package com.oculus.jj.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
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
    private final ApplicationEntity ae = new ApplicationEntity();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private ScheduledExecutorService incomingImportScheduler;
    private boolean listenerStarted;

    private DicomService() {
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.addConnection(conn);
        device.setExecutor(executorService);

        ae.setAETitle(DicomNodeManager.getInstance().getLocalAET());

        // C-STORE SCPの登録
        String[] ts = {
                UID.ImplicitVRLittleEndian,
                UID.ExplicitVRLittleEndian,
                UID.JPEGBaseline8Bit,
                UID.JPEGLosslessSV1,
                UID.JPEGLSLossless,
                UID.JPEG2000Lossless,
                UID.RLELossless
        };
        ae.addTransferCapability(
                new TransferCapability(null, "*", TransferCapability.Role.SCP, ts));
        device.setDimseRQHandler(new DicomStoreSCP("*"));
    }

    public synchronized void startListener(int port) throws IOException, GeneralSecurityException {
        if (listenerStarted) return;
        ae.setAETitle(DicomNodeManager.getInstance().getLocalAET());
        conn.setPort(port);
        device.bindConnections();
        listenerStarted = true;
        int intervalSeconds = Math.max(1, Math.min(3600,
                com.oculus.jj.config.AppPreferences.getInt("listener.checkInterval", 3)));
        incomingImportScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "DICOM-Incoming-Poller");
            thread.setDaemon(true);
            return thread;
        });
        incomingImportScheduler.scheduleWithFixedDelay(() -> {
            DicomDatabase database = DicomDatabase.activeLocalDatabase();
            if (database == null) database = DicomDatabase.defaultDatabase();
            if (database != null) database.initiateImportFilesFromIncomingDirUnlessAlreadyImporting();
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        // // logger.info(
// //                 "DICOM Listener started on port {} (AET: {}). Ensure your PACS is configured to send to this AET, IP and Port.",
// //                 port, ae.getAETitle());
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
        // // logger.info("Starting C-ECHO to {}:{}", remoteNode.getAddress(), remoteNode.getPort());

        DicomNodeManager manager = DicomNodeManager.getInstance();
        ae.setAETitle(manager.getLocalAET());

        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        rq.addPresentationContext(new PresentationContext(1, UID.Verification, UID.ImplicitVRLittleEndian));

        Association association = ae.connect(new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
        try {
            association.cecho().next();
            return true;
        } finally {
            if (association.isReadyForDataTransfer()) {
                association.release();
            }
        }
    }

    /**
     * C-FIND 実行
     */
    public List<Attributes> find(DicomNode remoteNode, Attributes keys)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        // // logger.info("Starting C-FIND to {}:{}", remoteNode.getAddress(), remoteNode.getPort());
        final List<Attributes> results = new ArrayList<>();

        DicomNodeManager manager = DicomNodeManager.getInstance();
        ae.setAETitle(manager.getLocalAET());

        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        // Study Root Query/Retrieve Information Model - FIND
        rq.addPresentationContext(
                new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelFind, UID.ImplicitVRLittleEndian));

        Association association = ae.connect(new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
        try {
            DimseRSPHandler rspHandler = new DimseRSPHandler(association.nextMessageID()) {
                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                    int status = cmd.getInt(0x00080160, -1); // Status
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
        DicomNodeManager manager = DicomNodeManager.getInstance();
        ae.setAETitle(manager.getLocalAET());

        Attributes keys = new Attributes();
        keys.setString(0x00080052, VR.CS, "STUDY"); // QueryRetrieveLevel
        keys.setString(0x0020000D, VR.UI, studyInstanceUID); // StudyInstanceUID

        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        rq.addPresentationContext(
                new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelMove, UID.ImplicitVRLittleEndian));

        Association association = ae.connect(new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
        try {
            // HOROS-20240407準拠: 進捗を管理するためにカスタムDimseRSPHandlerを使用
            final com.oculus.jj.threads.ThreadsManager tm = com.oculus.jj.threads.ThreadsManager
                    .defaultManager();
            final Thread currentThread = Thread.currentThread();
            final int[] finalStatus = { -1 };

            DimseRSPHandler rspHandler = new DimseRSPHandler(association.nextMessageID()) {
                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                    int status = cmd.getInt(0x00080160, -1); // Status (C-MOVE)
                    finalStatus[0] = status;

                    // C-MOVEの重複操作数（完了、残数、失敗、警告）を取得
                    int completed = cmd.getInt(0x00081351, 0); // NumberOfCompletedSuboperations
                    int remaining = cmd.getInt(0x00081352, 0); // NumberOfRemainingSuboperations
                    int warning = cmd.getInt(0x00081353, 0); // NumberOfWarningSuboperations
                    int failed = cmd.getInt(0x00081354, 0); // NumberOfFailedSuboperations

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
                    manager.getLocalAET(), rspHandler);
            association.waitForOutstandingRSP();

            // ステータスチェック
            if (finalStatus[0] != 0 && !Status.isPending(finalStatus[0])) {
                throw new IOException("C-MOVE retrieve failed with status: 0x" + Integer.toHexString(finalStatus[0]));
            }
        } finally {
            if (association.isReadyForDataTransfer()) {
                association.release();
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

        DicomNodeManager manager = DicomNodeManager.getInstance();
        ae.setAETitle(manager.getLocalAET());
        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        rq.addPresentationContext(new PresentationContext(1, cuid, tsuid));
        Association association = ae.connect(
                new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
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
        DicomNodeManager manager = DicomNodeManager.getInstance();
        ae.setAETitle(manager.getLocalAET());

        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        rq.addPresentationContext(
                new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelGet, UID.ImplicitVRLittleEndian));

        Association association = ae.connect(new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
        try {
            DimseRSPHandler rspHandler = new DimseRSPHandler(association.nextMessageID()) {
                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                    int status = cmd.getInt(0x00080160, -1); // Status (C-GET)
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

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(urlStr).toURL().openConnection();
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

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(urlStr).toURL().openConnection();
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

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(urlStr).toURL().openConnection();
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
        keys.setString(0x00200011, VR.UI, seriesInstanceUID); // SeriesInstanceUID
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
