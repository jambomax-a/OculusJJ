package com.oculus.jj.repository;

import java.util.*;

/**
 * 複数のデータソース間のリポジトリを管理し、適切なものをルーティングします。
 */
public class DicomRepositoryManager {
    // 名前(String)に基づいてリポジトリを管理 (例: "Local_Data", "Remote_PACS")
    private final Map<String, DicomRepository> repositories = new HashMap<>();
    private String currentSourceName = null;

    /**
     * 現在アクティブなソース名を設定します。
     */
    public void setCurrentSourceName(String name) {
        this.currentSourceName = name;
    }

    public String getCurrentSourceName() {
        return currentSourceName;
    }

    /**
     * 現在のソース名に基づいてリポジトリを取得します。
     */
    public DicomRepository getRepository() {
        if (currentSourceName != null && repositories.containsKey(currentSourceName)) {
            return repositories.get(currentSourceName);
        }
        return null;
    }

    /**
     * 特定の名前に基づいてリポジトリを取得します。
     */
    public DicomRepository getRepository(String name) {
        return repositories.get(name);
    }

    /**
     * 新しいリポジトリインスタンスを登録します。
     */
    public void registerRepository(String name, DicomRepository repository) {
        repositories.put(name, repository);
    }
}
