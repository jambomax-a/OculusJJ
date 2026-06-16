package com.oculus.jj.repository;

import java.util.*;
import com.oculus.jj.model.DicomPathService;

/**
 * 複数のデータベース（ディレクトリ）間を動的に切り替えるためのコンテキストマネージャ。
 */
public class DatabaseContextManager {
    private static final DicomRepositoryManager repositoryManager = new DicomRepositoryManager();
    private static DicomPathService currentPathService;
    private static final DatabaseRegistry registry = new DatabaseRegistry();

    /**
     * 新しいベースパスに基づいてデータベースコンテキストを初期化または切り替えます。
     */
    public static void switchContext(String name) {
        String basePath = registry.getBasePath(name);
        if (basePath == null) {
            throw new IllegalArgumentException("Database with name '" + name + "' is not registered.");
        }
        // 新しいリポジトリをこの名前で登録
        repositoryManager.registerRepository(name, new LocalDicomRepository(new DicomPathService(basePath)));
        currentPathService = new DicomPathService(basePath);
        
        // 現在アクティブなソースとして設定
        repositoryManager.setCurrentSourceName(name);
    }

    /**
     * データベースを登録します。
     */
    public static void registerDatabase(String name, String basePath) {
        registry.registerBase(name, basePath);
    }

    public static DicomPathService getCurrentPathService() {
        return currentPathService;
    }

    public static DicomRepositoryManager getRepositoryManager() {
        return repositoryManager;
    }

    public static List<String> getAvailableDatabases() {
        return registry.getRegisteredBaseNames();
    }
}
