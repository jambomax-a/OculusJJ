package com.oculus.jj.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.io.DicomInputStream;

/** Reads referenced image paths from a DICOMDIR without scanning unrelated files. */
public final class DicomDirectoryUtils {
    private static final int DIRECTORY_RECORD_SEQUENCE = 0x00041220;
    private static final int REFERENCED_FILE_ID = 0x00041500;

    private DicomDirectoryUtils() {}

    public static List<Path> referencedFiles(Path directory) throws IOException {
        Path root = directory.toRealPath();
        Path dicomDir = findDicomDir(root);
        if (dicomDir == null) return List.of();

        Attributes dataset;
        try (DicomInputStream input = new DicomInputStream(dicomDir.toFile())) {
            input.readFileMetaInformation();
            dataset = input.readDataset();
        }
        if (dataset == null) return List.of();
        Set<Path> results = new LinkedHashSet<>();
        collect(dataset, root, results);
        return new ArrayList<>(results);
    }

    private static Path findDicomDir(Path root) throws IOException {
        try (java.util.stream.Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isRegularFile)
                    .filter(path -> "DICOMDIR".equalsIgnoreCase(path.getFileName().toString()))
                    .findFirst().orElse(null);
        }
    }

    private static void collect(Attributes record, Path root, Set<Path> results) {
        String[] fileIds = record.getStrings(REFERENCED_FILE_ID);
        if (fileIds != null && fileIds.length > 0) {
            List<String> components = new ArrayList<>();
            for (String id : fileIds) {
                if (id == null) continue;
                for (String component : id.split("[\\\\/]+")) {
                    if (!component.isBlank()) components.add(component);
                }
            }
            if (!components.isEmpty()) {
                Path candidate = root;
                for (String component : components) candidate = candidate.resolve(component);
                candidate = candidate.normalize();
                if (candidate.startsWith(root) && Files.isRegularFile(candidate)) results.add(candidate);
            }
        }

        Sequence children = record.getSequence(DIRECTORY_RECORD_SEQUENCE);
        if (children != null) {
            for (Attributes child : children) collect(child, root, results);
        }
    }
}
