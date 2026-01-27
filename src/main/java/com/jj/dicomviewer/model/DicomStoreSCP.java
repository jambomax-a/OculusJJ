package com.jj.dicomviewer.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.util.SafeClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * DicomStoreSCP - DICOM C-STORE SCP (Listener)
 */
public class DicomStoreSCP extends BasicCStoreSCP {

    private static final Logger logger = LoggerFactory.getLogger(DicomStoreSCP.class);

    public DicomStoreSCP(String... sopClasses) {
        super(sopClasses);
    }

    private File getStorageDir() {
        DicomDatabase db = DicomDatabase.activeLocalDatabase();
        if (db != null) {
            File dir = new File(db.incomingDirPath());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return dir;
        }
        return new File("Incoming"); // Fallback
    }

    @Override
    protected void store(Association as, PresentationContext pc, Attributes rq,
            PDVInputStream data, Attributes rsp) throws IOException {
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String tsuid = pc.getTransferSyntax();

        File storageDir = getStorageDir();
        File file = new File(storageDir, iuid != null ? iuid : UUID.randomUUID().toString());
        logger.info("Receiving DICOM file: {}", file.getAbsolutePath());

        try {
            try (DicomOutputStream out = new DicomOutputStream(file)) {
                // File Meta Information
                Attributes fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
                out.writeFileMetaInformation(fmi);
                data.copyTo(out);
            }

            // HOROS-20240407準拠: DicomDatabaseのimportFilesFromIncomingDirを呼び出す
            javax.swing.SwingUtilities.invokeLater(() -> {
                DicomDatabase db = DicomDatabase.activeLocalDatabase();
                if (db != null) {
                    db.initiateImportFilesFromIncomingDirUnlessAlreadyImporting();
                }
            });

            rsp.setInt(Tag.Status, VR.US, Status.Success);
        } catch (Exception e) {
            logger.error("Error storing DICOM file", e);
            rsp.setInt(Tag.Status, VR.US, Status.ProcessingFailure);
        }
    }
}
