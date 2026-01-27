package com.jj.dicomviewer.model;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DicomNodeManager - DICOMノードの設定を管理するクラス
 */
public class DicomNodeManager {
    private static final Logger logger = LoggerFactory.getLogger(DicomNodeManager.class);
    private static final String NODES_KEY = "DICOM_NODES";

    // シングルトン
    private static DicomNodeManager instance;
    private List<DicomNode> nodes;
    private final ObjectMapper mapper = new ObjectMapper();

    private DicomNodeManager() {
        loadNodes();
    }

    public static synchronized DicomNodeManager getInstance() {
        if (instance == null) {
            instance = new DicomNodeManager();
        }
        return instance;
    }

    public List<DicomNode> getNodes() {
        return new ArrayList<>(nodes);
    }

    public void addNode(DicomNode node) {
        nodes.add(node);
        saveNodes();
    }

    public void removeNode(DicomNode node) {
        nodes.remove(node);
        saveNodes();
    }

    public void saveNodes() {
        try {
            String json = mapper.writeValueAsString(nodes);
            Preferences prefs = Preferences.userNodeForPackage(DicomNodeManager.class);
            prefs.put(NODES_KEY, json);
            prefs.flush();
        } catch (Exception e) {
            logger.error("Failed to save DICOM nodes", e);
        }
    }

    private void loadNodes() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(DicomNodeManager.class);
            String json = prefs.get(NODES_KEY, "[]");
            nodes = mapper.readValue(json, new TypeReference<List<DicomNode>>() {
            });

            // デフォルトノードがなければ追加（デバッグ用）
            if (nodes.isEmpty()) {
                nodes.add(new DicomNode("Local PACS", "ANY-SCP", "127.0.0.1", 11112));
                saveNodes();
            }
        } catch (Exception e) {
            logger.error("Failed to load DICOM nodes", e);
            nodes = new ArrayList<>();
        }
    }

    /**
     * ローカルサーバーの設定（自分のAET, Port）を取得
     */
    public String getLocalAET() {
        Preferences prefs = Preferences.userNodeForPackage(DicomNodeManager.class);
        return prefs.get("LOCAL_AET", "HOROS_JAVA");
    }

    public int getLocalPort() {
        Preferences prefs = Preferences.userNodeForPackage(DicomNodeManager.class);
        return prefs.getInt("LOCAL_PORT", 11112);
    }

    public void setLocalConfig(String aet, int port) {
        Preferences prefs = Preferences.userNodeForPackage(DicomNodeManager.class);
        prefs.put("LOCAL_AET", aet);
        prefs.putInt("LOCAL_PORT", port);
    }
}
