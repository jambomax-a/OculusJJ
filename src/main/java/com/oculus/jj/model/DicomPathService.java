package com.oculus.jj.model;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * DicomPathService - DICOMデータベースのディレクトリパスを管理するクラス。
 * 元ソース: horos-20240407/Horos/Sources/DicomDatabase.h より移植
 */
public class DicomPathService {
    private final String baseDirPath; // "Horos Data"
    private String dataBaseDirPath;

    public DicomPathService(String baseDirPath) {
        this.baseDirPath = baseDirPath;
        
        // 元ソース: DicomDatabase.mm / .cpp より移植
        // DBFOLDER_LOCATION を検索し、その内容をプライマリなデータベースディレクトリとして使用する。
        File dbFolderLocationFile = new File(baseDirPath, "DBFOLDER_LOCATION");
        if (dbFolderLocationFile.exists() && dbFolderLocationFile.isFile()) {
            try {
                String pathFromFiles = Files.lines(dbFolderLocationFile.toPath())
                        .filter(line -> !line.trim().isEmpty())
                        .findFirst()
                        .orElse(baseDirPath);
                
                if (new File(pathFromFiles).exists()) {
                    this.dataBaseDirPath = pathFromFiles.trim();
                    return;
                }
            } catch (Exception e) {
                // エラー時は baseDirPath をフォールバックとして使用
            }
        }
        this.dataBaseDirPath = baseDirPath;
    }

    public String getBasePath() { return baseDirPath; }
    public String getDataBasePath() { return dataBaseDirPath; }

    public String getDataDir() { return dataBaseDirPath + File.separator + "Data"; }
    public String getIncomingDir() { return dataBaseDirPath + File.separator + "Incoming"; }
    public String getErrorsDir() { return dataBaseDirPath + File.separator + "Errors"; }
    public String getDecompressionDir() { return dataBaseDirPath + File.separator + "Decompression"; }
    public String getToBeIndexedDir() { return dataBaseDirPath + File.separator + "ToBeIndexed"; }
    public String getReportsDir() { return dataBaseDirPath + File.separator + "Reports"; }
    public String getTempDir() { return dataBaseDirPath + File.separator + "Temp"; }
    public String getDumpDir() { return dataBaseDirPath + File.separator + "Dump"; }

    /**
     * レガシーなリスナーコンポーネントで使用されるCスタイルパス用のヘルパーメソッド。
     */
    public String getBaseDirPathStr() { return baseDirPath; }
    public String getIncomingDirPathStr() { return getIncomingDir(); }
    public String getTempDirPathStr() { return getTempDir(); }

    @Override
    public String toString() {
        return "DicomPathService{base=" + baseDirPath + ", db=" + dataBaseDirPath + "}";
    }
}
