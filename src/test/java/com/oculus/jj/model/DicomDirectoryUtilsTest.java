package com.oculus.jj.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DicomDirectoryUtilsTest {

    @TempDir Path tempDirectory;

    @Test
    void readsOnlyExistingReferencedFilesAndRejectsDirectoryEscapes() throws Exception {
        Path imageDirectory = Files.createDirectories(tempDirectory.resolve("IMAGES"));
        Path referencedImage = Files.writeString(imageDirectory.resolve("IMG0001"), "image");
        Files.writeString(tempDirectory.resolve("unreferenced.txt"), "ignore");
        writeDicomDir(tempDirectory.resolve("DICOMDIR"), List.of("IMAGES", "IMG0001"), List.of("..", "outside.dcm"));

        List<Path> results = DicomDirectoryUtils.referencedFiles(tempDirectory);

        assertEquals(List.of(referencedImage), results);
    }

    @Test
    void returnsEmptyWhenDicomDirIsAbsent() throws Exception {
        assertTrue(DicomDirectoryUtils.referencedFiles(tempDirectory).isEmpty());
    }

    private void writeDicomDir(Path output, List<String> validId, List<String> invalidId) throws Exception {
        Attributes dataset = new Attributes();
        Sequence records = dataset.newSequence(0x00041220, 2);
        Attributes valid = new Attributes();
        valid.setString(0x00041500, VR.CS, validId.toArray(String[]::new));
        records.add(valid);
        Attributes escaped = new Attributes();
        escaped.setString(0x00041500, VR.CS, invalidId.toArray(String[]::new));
        records.add(escaped);

        Attributes fmi = new Attributes();
        fmi.setString(0x00020002, VR.UI, UID.MediaStorageDirectoryStorage);
        fmi.setString(0x00020003, VR.UI, "1.2.3.4.5");
        fmi.setString(0x00020010, VR.UI, UID.ExplicitVRLittleEndian);
        try (DicomOutputStream stream = new DicomOutputStream(output.toFile())) {
            stream.writeDataset(fmi, dataset);
            stream.finish();
        }
    }
}
