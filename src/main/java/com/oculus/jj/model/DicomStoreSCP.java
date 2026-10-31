package com.oculus.jj.model;
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
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    /**
     * ファイル名にDICOM拡張子(.dcm/.ima)がない場合、.dcmを付与する。
     */
    private static String ensureDcmExtension(String filename) {
        if (filename == null || filename.isEmpty()) return filename;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".dcm") || lower.endsWith(".ima")) {
            return filename; // Already DICOM extension
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0) {
            // UID-based name like 1.2.392... has no extension -> append .dcm
            return filename + ".dcm";
        }
        String ext = filename.substring(lastDot).toLowerCase();
        if (!ext.equals(".dcm") && !ext.equals(".ima")) {
            // Non-DICOM extension (e.g., .0, .1) -> replace with .dcm
            return filename.substring(0, lastDot) + ".dcm";
        }
        return filename;
    }
    @Override
    protected void store(Association as, PresentationContext pc, Attributes rq,
            PDVInputStream data, Attributes rsp) throws IOException {
        String iuid = rq.getString(0x0008119A); // AffectedSOPInstanceUID
        String cuid = rq.getString(0x00081199); // AffectedSOPClassUID
        String tsuid = pc.getTransferSyntax();
        File storageDir = getStorageDir();
        String rawFilename = iuid != null ? iuid : UUID.randomUUID().toString();
        // UIDベースの名前には拡張子がないため、.dcm を付与（DICOMファイル保存に必要）
        String safeFilename = ensureDcmExtension(rawFilename);
        File file = new File(storageDir, safeFilename);
        if (file.exists() && !com.oculus.jj.config.AppPreferences.getBool("listener.replaceExisting", false)) {
            data.copyTo(OutputStream.nullOutputStream());
            rsp.setInt(0x00080160, VR.US, Status.Success);
            return;
        }
        File temporary = new File(storageDir, safeFilename + ".part-" + UUID.randomUUID());
        try {
            try (DicomOutputStream out = new DicomOutputStream(temporary)) {
                Attributes fmi = as.createFileMetaInformation(iuid, cuid, tsuid);
                if (fmi != null) {
                    out.writeFileMetaInformation(fmi);
                    data.copyTo(out);
                } else {
                    out.writeFileMetaInformation(new Attributes());
                    data.copyTo(out);
                }
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                DicomDatabase db = DicomDatabase.activeLocalDatabase();
                if (db != null) {
                    db.initiateImportFilesFromIncomingDirUnlessAlreadyImporting();
                }
            });
            rsp.setInt(0x00080160, VR.US, Status.Success); // Status
        } catch (Exception e) {
            try { Files.deleteIfExists(temporary.toPath()); } catch (Exception ignored) { }
            rsp.setInt(0x00080160, VR.US, Status.ProcessingFailure); // Status
        }
    }
}