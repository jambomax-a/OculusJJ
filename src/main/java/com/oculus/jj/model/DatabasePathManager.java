package com.oculus.jj.model;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * DatabasePathManager - Handles directory structure for the DicomDatabase.
 * Ported from horos-20240407/Horos/Sources/DicomDatabase.h.
 */
public class DatabasePathManager {
    private final String baseDirPath;
    private final String dataBaseDirPath;

    public DatabasePathManager(String baseDirPath) {
        this.baseDirPath = baseDirPath;
        
        // Logic from DicomDatabase.mm / .cpp: 
        // check for DBFOLDER_LOCATION file and use its content as the database directory.
        String resolvedPath = baseDirPath;
        File dbFolderLocationFile = new File(baseDirPath, "DBFOLDER_LOCATION");
        if (dbFolderLocationFile.exists() && dbFolderLocationFile.isFile()) {
            try {
                String pathFromFiles;
                try (Stream<String> lines = Files.lines(dbFolderLocationFile.toPath())) {
                    pathFromFiles = lines.findFirst().orElse(baseDirPath);
                }
                if (new File(pathFromFiles).exists()) {
                    resolvedPath = pathFromFiles.trim();
                }
            } catch (Exception e) {
                // Fall back to baseDirPath on error
            }
        }
        this.dataBaseDirPath = resolvedPath;
    }

    public String getBaseDirPath() { return baseDirPath; }
    public String getDataBaseDirPath() { return dataBaseDirPath; }

    public String getDataDirPath() {
        return dataBaseDirPath + File.separator + "Data";
    }

    public String getIncomingDirPath() {
        return dataBaseDirPath + File.separator + "Incoming";
    }

    public String getErrorsDirPath() {
        return dataBaseDirPath + File.separator + "Errors";
    }

    public String getDecompressionDirPath() {
        return dataBaseDirPath + File.separator + "Decompression";
    }

    public String getToBeIndexedDirPath() {
        return dataBaseDirPath + File.separator + "ToBeIndexed";
    }

    public String getReportsDirPath() {
        return dataBaseDirPath + File.separator + "Reports";
    }

    public String getTempDirPath() {
        return dataBaseDirPath + File.separator + "Temp";
    }

    public String getDumpDirPath() {
        return dataBaseDirPath + File.separator + "Dump";
    }
    
    // Helper for listener-style access (C++: baseDirPathC, etc.)
    public String getBaseDirPathStr() { return baseDirPath; }
    public String getIncomingDirPathStr() { return getIncomingDirPath(); }
    public String getTempDirPathStr() { return getTempDirPath(); }
}
