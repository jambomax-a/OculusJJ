package com.oculus.jj.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DicomService - DICOMネットワーク通信（C-FIND, C-MOVE等）を担当するクラス
 */
public class DicomService {
    private static final Logger logger = LoggerFactory.getLogger(DicomService.class);

    private static DicomService instance;

    public static synchronized DicomService getInstance() {
        if (instance == null) {
            instance = new DicomService();
        }
        return instance;
    }

    private final Device device = new Device("OculusJJ");
    private final Connection conn = new Connection();
    private final Connection scuConn = new Connection();
    private final ApplicationEntity ae = new ApplicationEntity();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DICOM-Device-Scheduler");
        t.setDaemon(true);
        return t;
    });
    private ScheduledExecutorService incomingImportScheduler;
    private boolean listenerStarted;
    private volatile String lastListenerError;
    private volatile String lastStoreError;

    private DicomService() {
        String initialAet = DicomNodeManager.getInstance().getLocalAET();
        if (initialAet == null || initialAet.isBlank()) {
            initialAet = "OCULUSJJ";
        }
        ae.setAETitle(initialAet);
        ae.setAssociationInitiator(true);
        ae.setAssociationAcceptor(true);

        device.addConnection(conn);
        device.addConnection(scuConn);
        device.addApplicationEntity(ae);
        ae.addConnection(conn);
        ae.addConnection(scuConn);
        device.setExecutor(executorService);
        device.setScheduledExecutor(scheduledExecutor);

        // C-ECHO / C-STORE SCPの登録（任意のSOP Class / Transfer Syntaxを受信保存可能にする）
        ae.addTransferCapability(
                new TransferCapability(null, UID.Verification, TransferCapability.Role.SCP, "*"));
        ae.addTransferCapability(
                new TransferCapability(null, "*", TransferCapability.Role.SCP, "*"));
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        serviceRegistry.addDicomService(new BasicCEchoSCP());
        serviceRegistry.addDicomService(new DicomStoreSCP("*"));
        device.setDimseRQHandler(serviceRegistry);
    }

    public boolean isListenerStarted() {
        return listenerStarted;
    }

    public String getLastListenerError() {
        return lastListenerError;
    }

    public void setLastListenerError(String err) {
        this.lastListenerError = err;
    }

    public void setLastStoreError(String err) {
        this.lastStoreError = err;
    }

    private synchronized String ensureLocalAET() {
        String targetAet = DicomNodeManager.getInstance().getLocalAET();
        if (targetAet == null || targetAet.isBlank()) {
            targetAet = "OCULUSJJ";
        }
        if (!targetAet.equals(ae.getAETitle()) || ae.getDevice() == null) {
            if (ae.getDevice() != null) {
                device.removeApplicationEntity(ae);
            }
            ae.setAETitle(targetAet);
            device.addApplicationEntity(ae);
        }
        return targetAet;
    }

    public synchronized void startListener(int port) throws IOException, GeneralSecurityException {
        if (listenerStarted) {
            ensureLocalAET();
            if (conn.getPort() == port) {
                return;
            }
            stopListener();
        }
        ensureLocalAET();
        conn.setPort(port);
        device.bindConnections();
        listenerStarted = true;
        lastListenerError = null;
        int intervalSeconds = Math.max(1, Math.min(3600,
                com.oculus.jj.config.AppPreferences.getInt("listener.checkInterval", 3)));
        incomingImportScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "DICOM-Incoming-Poller");
            thread.setDaemon(true);
            return thread;
        });
        incomingImportScheduler.scheduleWithFixedDelay(() -> {
            DicomDatabase database = DicomDatabase.activeLocalDatabase();
            if (database == null)
                database = DicomDatabase.defaultDatabase();
            if (database != null)
                database.initiateImportFilesFromIncomingDirUnlessAlreadyImporting();
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        // // logger.info(
        // // "DICOM Listener started on port {} (AET: {}). Ensure your PACS is
        // configured to send to this AET, IP and Port.",
        // // port, ae.getAETitle());
    }

    public synchronized void stopListener() {
        if (incomingImportScheduler != null) {
            incomingImportScheduler.shutdownNow();
            incomingImportScheduler = null;
        }
        if (listenerStarted) {
            device.unbindConnections();
            listenerStarted = false;
        }
        // // logger.info("DICOM Listener stopped");
    }

    /** Abort active inbound associations addressed to the local AE. */
    public int abortIncomingAssociations() {
        String localAET = DicomNodeManager.getInstance().getLocalAET();
        int aborted = 0;
        for (Association association : device.listOpenAssociations()) {
            if (localAET.equals(association.getCalledAET())) {
                association.abort();
                aborted++;
            }
        }
        return aborted;
    }

    /**
     * C-ECHO 実行
     */
    public boolean echo(DicomNode remoteNode)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        // // logger.info("Starting C-ECHO to {}:{}", remoteNode.getAddress(),
        // remoteNode.getPort());

        ensureLocalAET();

        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        rq.addPresentationContext(new PresentationContext(1, UID.Verification, UID.ImplicitVRLittleEndian));

        Association association = ae.connect(scuConn, new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
        try {
            association.cecho().next();
            return true;
        } finally {
            if (association.isReadyForDataTransfer()) {
                association.release();
                association.waitForSocketClose();
            }
        }
    }

    /**
     * C-FIND 実行
     */
    public List<Attributes> find(DicomNode remoteNode, Attributes keys)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        // // logger.info("Starting C-FIND to {}:{}", remoteNode.getAddress(),
        // remoteNode.getPort());
        final List<Attributes> results = new ArrayList<>();

        ensureLocalAET();

        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        // Study Root Query/Retrieve Information Model - FIND
        rq.addPresentationContext(
                new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelFind, UID.ImplicitVRLittleEndian));

        Association association = ae.connect(scuConn, new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
        try {
            DimseRSPHandler rspHandler = new DimseRSPHandler(association.nextMessageID()) {
                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                    int status = cmd.getInt(0x00000900, -1); // Status
                    if (status == 0xFF00 || status == 0xFF01) { // Pending
                        if (data != null) {
                            results.add(new Attributes(data));
                        }
                    }
                }
            };
            association.cfind(UID.StudyRootQueryRetrieveInformationModelFind, Priority.NORMAL, keys, null, rspHandler);
            association.waitForOutstandingRSP();
        } finally {
            if (association.isReadyForDataTransfer()) {
                association.release();
                association.waitForSocketClose();
            }
        }

        return results;
    }

    /**
     * C-MOVE 実行
     */
    public void move(DicomNode remoteNode, String studyInstanceUID)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        if (studyInstanceUID != null) {
            studyInstanceUID = studyInstanceUID.trim();
        }
        String localAet = ensureLocalAET();

        Attributes keys = new Attributes();
        keys.setString(0x00080052, VR.CS, "STUDY"); // QueryRetrieveLevel
        keys.setString(0x0020000D, VR.UI, studyInstanceUID); // StudyInstanceUID

        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        rq.addPresentationContext(
                new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelMove, UID.ImplicitVRLittleEndian));

        Association association = ae.connect(scuConn, new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
        try {
            // HOROS-20240407準拠: 進捗を管理するためにカスタムDimseRSPHandlerを使用
            final com.oculus.jj.threads.ThreadsManager tm = com.oculus.jj.threads.ThreadsManager
                    .defaultManager();
            final Thread currentThread = Thread.currentThread();
            final int[] finalStatus = { -1 };
            final String[] errorComment = { null };
            final int[] counts = { 0, 0, 0, 0 }; // completed, remaining, warning, failed
            lastStoreError = null;

            DimseRSPHandler rspHandler = new DimseRSPHandler(association.nextMessageID()) {
                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                    int status = cmd.getInt(0x00000900, -1); // Status (C-MOVE)
                    finalStatus[0] = status;
                    String comment = cmd.getString(0x00000902); // ErrorComment
                    if (comment != null && !comment.isEmpty()) {
                        errorComment[0] = comment;
                    }

                    // C-MOVEの重複操作数（完了、残数、失敗、警告）を取得
                    int completed = cmd.getInt(0x00001021, 0); // NumberOfCompletedSuboperations
                    int remaining = cmd.getInt(0x00001020, 0); // NumberOfRemainingSuboperations
                    int warning = cmd.getInt(0x00001023, 0); // NumberOfWarningSuboperations
                    int failed = cmd.getInt(0x00001022, 0); // NumberOfFailedSuboperations
                    counts[0] = completed;
                    counts[1] = remaining;
                    counts[2] = warning;
                    counts[3] = failed;

                    int total = completed + remaining + warning + failed;
                    if (total > 0) {
                        double progress = (double) completed / total;
                        tm.setThreadProgress(currentThread, progress);
                        tm.setThreadStatus(currentThread, String.format("Retrieving image %d/%d...", completed, total));
                    }

                    if (Status.isPending(status)) {
                        // 継続中
                    } else if (status == Status.Success) {
                        tm.setThreadProgress(currentThread, 1.0);
                        tm.setThreadStatus(currentThread, "Retrieve Successful");
                    } else {
                        String statusHex = Integer.toHexString(status);
                        String errorMsg = "C-MOVE failed with status: 0x" + statusHex
                                + ". This often means the PACS (Orthanc) cannot connect back to HOROS_JAVA. Check AET/IP/Port mapping in Orthanc.";
                        // // logger.error(errorMsg);
                        tm.setThreadStatus(currentThread, "Retrieve Failed (Status: 0x" + statusHex + ")");
                    }
                }
            };

            // C-MOVEの送信。宛先(Move Destination)は自分のAET
            association.cmove(UID.StudyRootQueryRetrieveInformationModelMove, Priority.NORMAL, keys, null,
                    localAet, rspHandler);
            association.waitForOutstandingRSP();

            // ステータスチェック
            if (finalStatus[0] != 0 && !Status.isPending(finalStatus[0])) {
                StringBuilder msg = new StringBuilder("C-MOVE retrieve failed with status: 0x")
                        .append(Integer.toHexString(finalStatus[0]));
                if (errorComment[0] != null) {
                    msg.append(" (").append(errorComment[0]).append(")");
                }
                msg.append(" [MoveDestination=").append(localAet)
                   .append(", ListenerRunning=").append(listenerStarted)
                   .append(", Port=").append(conn.getPort())
                   .append(", ok=").append(counts[0])
                   .append(", fail=").append(counts[3])
                   .append(", warn=").append(counts[2]).append("]");
                if (!listenerStarted && lastListenerError != null) {
                    msg.append(" ListenerError=").append(lastListenerError);
                }
                if (lastStoreError != null) {
                    msg.append(" StoreError=").append(lastStoreError);
                }
                throw new IOException(msg.toString());
            }
        } finally {
            if (association.isReadyForDataTransfer()) {
                association.release();
                association.waitForSocketClose();
            }
        }
    }

    /**
     * C-STORE SCU: 元の DICOM ファイルをリモートノードへ送信する。
     */
    public void store(DicomNode remoteNode, java.io.File file)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        if (remoteNode == null || file == null || !file.isFile()) {
            throw new IOException("DICOM file or destination node is unavailable");
        }

        Attributes fileMeta;
        Attributes dataset;
        try (org.dcm4che3.io.DicomInputStream in = new org.dcm4che3.io.DicomInputStream(file)) {
            fileMeta = in.readFileMetaInformation();
            dataset = in.readDataset();
        }
        String cuid = fileMeta != null ? fileMeta.getString(Tag.MediaStorageSOPClassUID)
                : dataset.getString(Tag.SOPClassUID);
        String iuid = fileMeta != null ? fileMeta.getString(Tag.MediaStorageSOPInstanceUID)
                : dataset.getString(Tag.SOPInstanceUID);
        String tsuid = fileMeta != null ? fileMeta.getString(Tag.TransferSyntaxUID)
                : UID.ExplicitVRLittleEndian;
        if (cuid == null || iuid == null) {
            throw new IOException("DICOM file is missing SOP Class or Instance UID: " + file.getName());
        }
        if (tsuid == null || tsuid.isEmpty()) {
            tsuid = UID.ExplicitVRLittleEndian;
        }

        ensureLocalAET();
        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        rq.addPresentationContext(new PresentationContext(1, cuid, tsuid));
        Association association = ae.connect(
                scuConn, new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
        try {
            org.dcm4che3.net.DimseRSP rsp = association.cstore(cuid, iuid, Priority.NORMAL,
                    org.dcm4che3.net.DataWriterAdapter.forAttributes(dataset), tsuid);
            rsp.next();
            int status = rsp.getCommand().getInt(Tag.Status, -1);
            if (status != org.dcm4che3.net.Status.Success) {
                throw new IOException("C-STORE failed with status 0x" + Integer.toHexString(status)
                        + " for " + file.getName());
            }
        } finally {
            if (association.isReadyForDataTransfer()) {
                association.release();
            }
        }
    }

    /**
     * C-GET 実行（HOROS-20240407準拠: C-MOVEの代替）
     */
    public void getC(DicomNode remoteNode, Attributes keys)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        ensureLocalAET();

        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        rq.addPresentationContext(
                new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelGet, UID.ImplicitVRLittleEndian));

        Association association = ae.connect(scuConn, new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
        try {
            DimseRSPHandler rspHandler = new DimseRSPHandler(association.nextMessageID()) {
                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                    int status = cmd.getInt(0x00000900, -1); // Status (C-GET)
                    if (Status.isPending(status)) {
                        // 継続中
                    } else if (status == Status.Success) {
                        logger.info("C-GET retrieve successful");
                    } else {
                        logger.error("C-GET failed with status: 0x" + Integer.toHexString(status));
                    }
                }
            };

            association.cget(UID.StudyRootQueryRetrieveInformationModelGet, Priority.NORMAL, keys, null, rspHandler);
            association.waitForOutstandingRSP();
        } finally {
            if (association.isReadyForDataTransfer()) {
                association.release();
            }
        }
    }

    /**
     * C-STORE SCP: 受信したDICOMデータをファイルとして保存する（非同期）
     */
    public void startCStoreSCP(int port, java.io.File saveDir) throws IOException, GeneralSecurityException {
        conn.setPort(port);

        // DicomStoreSCPを登録（既存のコンストラクタを使用）
        device.setDimseRQHandler(new DicomStoreSCP(saveDir.getAbsolutePath()));
        device.bindConnections();
        logger.info("C-STORE SCP started on port {} (saving to: {})", port, saveDir.getAbsolutePath());
    }

    /**
     * WADO-RS: DICOM画像をHTTP GETで取得する（REST API）
     */
    public static byte[] getWadoRsImage(String baseUrl, String studyUID, String seriesUID, String sopInstanceUID)
            throws IOException {
        String urlStr = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        urlStr += "rs/studies/" + studyUID + "/series/" + seriesUID + "/instances/" + sopInstanceUID;

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(urlStr).toURL()
                .openConnection();
        conn.setRequestMethod("GET");

        // AETitleをContent-Typeに設定（WADO-RS仕様）
        String aet = DicomNodeManager.getInstance().getLocalAET();
        conn.setRequestProperty("Accept", "application/dicom");

        int responseCode = conn.getResponseCode();
        if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
            throw new IOException("WADO-RS request failed with status: " + responseCode);
        }

        byte[] data = conn.getInputStream().readAllBytes();
        conn.disconnect();
        return data;
    }

    /**
     * WADO-RS: DICOMメタデータをHTTP GETで取得する（JSON形式）
     */
    public static String getWadoRsMetadata(String baseUrl, String studyUID, String seriesUID, String sopInstanceUID)
            throws IOException {
        String urlStr = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        urlStr += "rs/studies/" + studyUID + "/series/" + seriesUID + "/instances/" + sopInstanceUID;

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(urlStr).toURL()
                .openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        int responseCode = conn.getResponseCode();
        if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
            throw new IOException("WADO-RS metadata request failed with status: " + responseCode);
        }

        String json = new String(conn.getInputStream().readAllBytes(), "UTF-8");
        conn.disconnect();
        return json;
    }

    /**
     * STOW-RS: DICOM画像をHTTP POSTで送信する（REST API）
     */
    public static void postStowRsImage(String baseUrl, byte[] dicomData) throws IOException {
        String urlStr = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        urlStr += "stow-rs/metadata";

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(urlStr).toURL()
                .openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/dicom");

        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(dicomData);
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != java.net.HttpURLConnection.HTTP_OK &&
                responseCode != java.net.HttpURLConnection.HTTP_CREATED) {
            throw new IOException("STOW-RS POST failed with status: " + responseCode);
        }
        conn.disconnect();
    }

    /**
     * DICOM Print Management (Basic Grayscale Print Management Meta SOP Class)
     * ジョブ送信。
     *
     * <p>
     * DICOM PS3.4 Annex H 準拠シーケンス:
     * <ol>
     * <li>N-CREATE BasicFilmSession (2000,0010 NumberOfCopies / PrintPriority /
     * MediumType / FilmDestination)</li>
     * <li>フィルムごとに N-CREATE BasicFilmBox (ImageDisplayFormat / FilmOrientation /
     * FilmSizeID / MagnificationType / BorderDensity / EmptyImageDensity / Trim /
     * ConfigurationInformation / ReferencedFilmSessionSequence)</li>
     * <li>各スロットに N-SET BasicGrayscaleImageBox (ImageBoxPosition +
     * BasicGrayscaleImageSequence)</li>
     * <li>N-ACTION BasicFilmBox (Action Type ID = 1: Print)</li>
     * <li>N-DELETE BasicFilmBox / BasicFilmSession</li>
     * </ol>
     */
    public void printDICOMImages(DicomNode printer, java.util.List<java.awt.image.BufferedImage> pages)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        if (printer == null) {
            throw new IOException("No DICOM printer configured");
        }
        if (pages == null || pages.isEmpty()) {
            throw new IOException("No images to print");
        }

        Attributes filmSessionAttrs = buildFilmSessionAttributes();
        int[] grid = resolveLayoutGrid(pages.size());
        int cols = grid[0];
        int rows = grid[1];
        int slotsPerFilm = Math.max(1, cols * rows);

        ensureLocalAET();

        String metaUID = UID.BasicGrayscalePrintManagementMeta;
        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(printer.getAeTitle());
        rq.addPresentationContext(new PresentationContext(1, metaUID,
                UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian));

        Association association = ae.connect(scuConn, new Connection(null, printer.getAddress(), printer.getPort()), rq);
        try {
            String filmSessionIUID = org.dcm4che3.util.UIDUtils.createUID();
            DimseRSP sessionRsp = association.ncreate(metaUID, UID.BasicFilmSession, filmSessionIUID, filmSessionAttrs,
                    null);
            sessionRsp.next();
            checkDimseStatus(sessionRsp.getCommand(), "N-CREATE BasicFilmSession");
            String returnedSessionIUID = sessionRsp.getCommand().getString(Tag.AffectedSOPInstanceUID);
            if (returnedSessionIUID != null && !returnedSessionIUID.isBlank()) {
                filmSessionIUID = returnedSessionIUID;
            }

            for (int offset = 0; offset < pages.size(); offset += slotsPerFilm) {
                Attributes filmBoxAttrs = buildFilmBoxAttributes(filmSessionIUID, cols, rows);
                String filmBoxIUID = org.dcm4che3.util.UIDUtils.createUID();
                DimseRSP boxRsp = association.ncreate(metaUID, UID.BasicFilmBox, filmBoxIUID, filmBoxAttrs, null);
                boxRsp.next();
                checkDimseStatus(boxRsp.getCommand(), "N-CREATE BasicFilmBox");
                String returnedBoxIUID = boxRsp.getCommand().getString(Tag.AffectedSOPInstanceUID);
                if (returnedBoxIUID != null && !returnedBoxIUID.isBlank()) {
                    filmBoxIUID = returnedBoxIUID;
                }

                org.dcm4che3.data.Sequence refImageBoxes = boxRsp.getDataset() != null
                        ? boxRsp.getDataset().getSequence(Tag.ReferencedImageBoxSequence)
                        : null;

                int limit = Math.min(slotsPerFilm, pages.size() - offset);
                for (int slot = 0; slot < limit; slot++) {
                    java.awt.image.BufferedImage page = pages.get(offset + slot);
                    if (page == null)
                        continue;
                    String imageBoxIUID = (refImageBoxes != null && slot < refImageBoxes.size())
                            ? refImageBoxes.get(slot).getString(Tag.ReferencedSOPInstanceUID)
                            : null;
                    if (imageBoxIUID == null || imageBoxIUID.isBlank()) {
                        imageBoxIUID = org.dcm4che3.util.UIDUtils.createUID();
                    }
                    Attributes imageBoxAttrs = buildGrayscaleImageBoxAttributes(page, slot + 1);
                    DimseRSP setRsp = association.nset(metaUID, UID.BasicGrayscaleImageBox, imageBoxIUID, imageBoxAttrs,
                            null);
                    setRsp.next();
                    checkDimseStatus(setRsp.getCommand(), "N-SET BasicGrayscaleImageBox");
                }

                DimseRSP actionRsp = association.naction(metaUID, UID.BasicFilmBox, filmBoxIUID, 1, null, null);
                actionRsp.next();
                checkDimseStatus(actionRsp.getCommand(), "N-ACTION BasicFilmBox Print");

                DimseRSP delBoxRsp = association.ndelete(metaUID, UID.BasicFilmBox, filmBoxIUID);
                delBoxRsp.next();
            }

            DimseRSP delSessionRsp = association.ndelete(metaUID, UID.BasicFilmSession, filmSessionIUID);
            delSessionRsp.next();
        } finally {
            if (association.isReadyForDataTransfer()) {
                association.release();
            }
        }
    }

    public static Attributes buildFilmSessionAttributes() {
        String[] priorities = { "HIGH", "MED", "LOW" };
        String[] media = { "BLUE FILM", "CLEAR FILM", "PAPER" };
        String[] destinations = { "PROCESSOR", "MAGAZINE", "BIN_1", "BIN_2" };
        int priorityIdx = Math.max(0, Math.min(priorities.length - 1,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.priority", 0)));
        int mediumIdx = Math.max(0, Math.min(media.length - 1,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.medium", 0)));
        int destIdx = Math.max(0, Math.min(destinations.length - 1,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.filmDestination", 0)));
        int copiesIdx = Math.max(0, Math.min(5,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.copies", 0)));
        int copies = copiesIdx <= 0 ? 1 : copiesIdx;

        Attributes attrs = new Attributes();
        attrs.setString(Tag.NumberOfCopies, VR.IS, Integer.toString(copies));
        attrs.setString(Tag.PrintPriority, VR.CS, priorities[priorityIdx]);
        attrs.setString(Tag.MediumType, VR.CS, media[mediumIdx]);
        attrs.setString(Tag.FilmDestination, VR.CS, destinations[destIdx]);
        return attrs;
    }

    public static Attributes buildFilmBoxAttributes(String filmSessionIUID, int cols, int rows) {
        String[] orientations = { "PORTRAIT", "LANDSCAPE" };
        String[] sizes = { "8INX10IN", "10INX12IN", "10INX14IN", "14INX17IN" };
        String[] magnifications = { "NONE", "CUBIC", "BILINEAR", "REPLICATE" };
        String[] trims = { "NO", "YES" };
        String[] colors = { "BLACK", "WHITE" };

        int orientIdx = Math.max(0, Math.min(orientations.length - 1,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.filmOrientationIndex", 1)));
        int sizeIdx = Math.max(0, Math.min(sizes.length - 1,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.filmSize", 0)));
        int magIdx = Math.max(0, Math.min(magnifications.length - 1,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.magnification", 0)));
        int trimIdx = Math.max(0, Math.min(trims.length - 1,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.trim", 0)));
        int borderIdx = Math.max(0, Math.min(colors.length - 1,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.borderColor", 0)));
        int emptyIdx = Math.max(0, Math.min(colors.length - 1,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.emptyColor", 0)));
        String configInfo = com.oculus.jj.config.AppPreferences.getStr("dicomPrint.configuration", "No Selection");

        Attributes attrs = new Attributes();
        attrs.setString(Tag.ImageDisplayFormat, VR.ST, "STANDARD\\" + Math.max(1, cols) + "," + Math.max(1, rows));
        attrs.setString(Tag.FilmOrientation, VR.CS, orientations[orientIdx]);
        attrs.setString(Tag.FilmSizeID, VR.CS, sizes[sizeIdx]);
        attrs.setString(Tag.MagnificationType, VR.CS, magnifications[magIdx]);
        attrs.setString(Tag.BorderDensity, VR.CS, colors[borderIdx]);
        attrs.setString(Tag.EmptyImageDensity, VR.CS, colors[emptyIdx]);
        attrs.setString(Tag.Trim, VR.CS, trims[trimIdx]);
        if (configInfo != null && !configInfo.isBlank() && !"No Selection".equalsIgnoreCase(configInfo.trim())) {
            attrs.setString(Tag.ConfigurationInformation, VR.ST, configInfo.trim());
        }
        Attributes sessionRef = new Attributes();
        sessionRef.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.BasicFilmSession);
        sessionRef.setString(Tag.ReferencedSOPInstanceUID, VR.UI, filmSessionIUID);
        attrs.ensureSequence(Tag.ReferencedFilmSessionSequence, 1).add(sessionRef);
        return attrs;
    }

    public static Attributes buildGrayscaleImageBoxAttributes(java.awt.image.BufferedImage image, int position) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] pixels = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                pixels[y * width + x] = (byte) ((r * 77 + g * 150 + b * 29) >> 8);
            }
        }
        Attributes imageBox = new Attributes();
        imageBox.setInt(Tag.ImageBoxPosition, VR.US, Math.max(1, position));
        Attributes pixelItem = new Attributes();
        pixelItem.setInt(Tag.SamplesPerPixel, VR.US, 1);
        pixelItem.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        pixelItem.setInt(Tag.Rows, VR.US, height);
        pixelItem.setInt(Tag.Columns, VR.US, width);
        pixelItem.setInt(Tag.PixelAspectRatio, VR.IS, 1, 1);
        pixelItem.setInt(Tag.BitsAllocated, VR.US, 8);
        pixelItem.setInt(Tag.BitsStored, VR.US, 8);
        pixelItem.setInt(Tag.HighBit, VR.US, 7);
        pixelItem.setInt(Tag.PixelRepresentation, VR.US, 0);
        pixelItem.setBytes(Tag.PixelData, VR.OB, pixels);
        imageBox.ensureSequence(Tag.BasicGrayscaleImageSequence, 1).add(pixelItem);
        return imageBox;
    }

    public static int[] resolveLayoutGrid(int imageCount) {
        if (com.oculus.jj.config.AppPreferences.getBool("dicomPrint.automaticLayout", false)) {
            if (imageCount <= 1)
                return new int[] { 1, 1 };
            if (imageCount == 2)
                return new int[] { 2, 1 };
            return new int[] { 2, 2 };
        }
        int fmtIdx = Math.max(0, Math.min(3,
                com.oculus.jj.config.AppPreferences.getInt("dicomPrint.layoutFormat", 0)));
        return switch (fmtIdx) {
            case 1 -> new int[] { 2, 1 };
            case 2 -> new int[] { 1, 2 };
            case 3 -> new int[] { 2, 2 };
            default -> new int[] { 1, 1 };
        };
    }

    public static DicomNode resolveConfiguredDicomPrinter() {
        String json = com.oculus.jj.config.AppPreferences.getStr("dicomPrint.printers", "");
        if (json == null || json.isBlank())
            return null;
        try {
            String[][] rows = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, String[][].class);
            String[] chosen = null;
            for (String[] row : rows) {
                if (row == null || row.length < 4)
                    continue;
                if (chosen == null)
                    chosen = row;
                if (row.length >= 5 && Boolean.parseBoolean(row[4])) {
                    chosen = row;
                    break;
                }
            }
            if (chosen == null)
                return null;
            String host = chosen[1] == null ? "" : chosen[1].trim();
            String aet = chosen[3] == null ? "" : chosen[3].trim();
            int port = Integer.parseInt(chosen[2] == null ? "104" : chosen[2].trim());
            if (host.isEmpty() || aet.isEmpty() || port <= 0 || port > 65535)
                return null;
            return new DicomNode(chosen[0], aet, host, port);
        } catch (Exception ex) {
            return null;
        }
    }

    private static void checkDimseStatus(Attributes cmd, String operation) throws IOException {
        int status = cmd == null ? -1 : cmd.getInt(Tag.Status, -1);
        if (status != Status.Success && !Status.isPending(status) && (status & 0xF000) != 0xB000) {
            throw new IOException(operation + " failed with status: 0x" + Integer.toHexString(status));
        }
    }

    /**
     * WADO: 後方互換性のためのURL生成（既存コード用）
     */
    public static String getWadoUrl(String baseUrl, String studyUID, String seriesUID, String sopInstanceUID) {
        String urlStr = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        urlStr += "wado?RequestType=WADO&StudyUID=" + studyUID
                + "&SeriesUID=" + seriesUID
                + "&SOPInstanceUID=" + sopInstanceUID
                + "&contentType=application/dicom";
        return urlStr;
    }

    /**
     * SeriesレベルのC-FINDクエリキーを作成する
     */
    public static Attributes createSeriesQueryKeys(String studyInstanceUID) {
        Attributes keys = new Attributes();
        keys.setString(0x00080052, VR.CS, "SERIES"); // QueryRetrieveLevel
        keys.setString(0x0020000D, VR.UI, studyInstanceUID); // StudyInstanceUID
        return keys;
    }

    /**
     * InstanceレベルのC-FINDクエリキーを作成する
     */
    public static Attributes createInstanceQueryKeys(String seriesInstanceUID) {
        Attributes keys = new Attributes();
        keys.setString(0x00080052, VR.CS, "IMAGE"); // QueryRetrieveLevel
        keys.setString(0x0020000E, VR.UI, seriesInstanceUID); // SeriesInstanceUID
        return keys;
    }

    /**
     * C-FINDでSeriesレベルの検索を行う
     */
    public List<Attributes> findSeries(DicomNode remoteNode, String studyInstanceUID)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        Attributes keys = createSeriesQueryKeys(studyInstanceUID);
        return find(remoteNode, keys);
    }

    /**
     * C-FINDでInstanceレベルの検索を行う
     */
    public List<Attributes> findInstances(DicomNode remoteNode, String seriesInstanceUID)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        Attributes keys = createInstanceQueryKeys(seriesInstanceUID);
        return find(remoteNode, keys);
    }

    /**
     * 利用可能なTransfer Syntaxの一覧を取得する
     */
    public static String[] getSupportedTransferSyntaxes() {
        return new String[] {
                UID.ImplicitVRLittleEndian,
                UID.ExplicitVRLittleEndian,
                UID.JPEGBaseline8Bit,
                UID.JPEGLosslessSV1,
                UID.JPEGLSLossless,
                UID.JPEG2000Lossless,
                UID.RLELossless
        };
    }

    /**
     * DICOM UIDを生成する（RFC 4122 UUID v4準拠）
     */
    public static String generateUID() {
        // DICOM UIDプレフィックス: 1.2.840.113619.x.y.z
        // ここでは簡易的にUUIDを使用
        return "1.2.840.113619." + java.util.UUID.randomUUID().toString().replace("-", ".");
    }

    /**
     * DICOMバージョン情報を取得する
     */
    public static String getDicomVersion() {
        return "2025a"; // dcm4che3標準
    }
}
