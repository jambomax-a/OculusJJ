package com.jj.dicomviewer.model;

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

    private final Device device = new Device("Horos");
    private final Connection conn = new Connection();
    private final ApplicationEntity ae = new ApplicationEntity();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

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

    public void startListener(int port) throws IOException, GeneralSecurityException {
        ae.setAETitle(DicomNodeManager.getInstance().getLocalAET());
        conn.setPort(port);
        device.bindConnections();
        logger.info(
                "DICOM Listener started on port {} (AET: {}). Ensure your PACS is configured to send to this AET, IP and Port.",
                port, ae.getAETitle());
    }

    public void stopListener() {
        device.unbindConnections();
        logger.info("DICOM Listener stopped");
    }

    /**
     * C-ECHO 実行
     */
    public boolean echo(DicomNode remoteNode)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        logger.info("Starting C-ECHO to {}:{}", remoteNode.getAddress(), remoteNode.getPort());

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
        logger.info("Starting C-FIND to {}:{}", remoteNode.getAddress(), remoteNode.getPort());
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
                    int status = cmd.getInt(Tag.Status, -1);
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
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);

        AAssociateRQ rq = new AAssociateRQ();
        rq.setCalledAET(remoteNode.getAeTitle());
        rq.addPresentationContext(
                new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelMove, UID.ImplicitVRLittleEndian));

        Association association = ae.connect(new Connection(null, remoteNode.getAddress(), remoteNode.getPort()), rq);
        try {
            // HOROS-20240407準拠: 進捗を管理するためにカスタムDimseRSPHandlerを使用
            final com.jj.dicomviewer.threads.ThreadsManager tm = com.jj.dicomviewer.threads.ThreadsManager
                    .defaultManager();
            final Thread currentThread = Thread.currentThread();
            final int[] finalStatus = { -1 };

            DimseRSPHandler rspHandler = new DimseRSPHandler(association.nextMessageID()) {
                @Override
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                    int status = cmd.getInt(Tag.Status, -1);
                    finalStatus[0] = status;

                    // C-MOVEの重複操作数（完了、残数、失敗、警告）を取得
                    int completed = cmd.getInt(Tag.NumberOfCompletedSuboperations, 0);
                    int remaining = cmd.getInt(Tag.NumberOfRemainingSuboperations, 0);
                    int warning = cmd.getInt(Tag.NumberOfWarningSuboperations, 0);
                    int failed = cmd.getInt(Tag.NumberOfFailedSuboperations, 0);

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
                        logger.error(errorMsg);
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
}
