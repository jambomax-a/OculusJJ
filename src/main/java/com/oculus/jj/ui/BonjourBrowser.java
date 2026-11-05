package com.oculus.jj.ui;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import com.oculus.jj.model.DicomNode;

/**
 * BonjourBrowser - Bonjour共有データベースを検索・取得
 * 
 * HOROS-20240407のBonjourBrowserをJavaに移植
 * JmDNSライブラリを使用してBonjour/Zeroconfサービス発見を実装
 */
public class BonjourBrowser {
    
    private static BonjourBrowser currentBrowser = null;
    
    // JmDNSサービスブラウザとリゾルバ
    private JmDNS jmdns = null;
    private ServiceListener serviceListener = null;
    
    // 発見されたサービス情報（DicomNodeのリスト）
    private List<DicomNode> bonjourNodes = new ArrayList<>();
    
    // 固定IPサーバー、ローカルパス、DICOM宛先などの他のサービス情報
    private List<Map<String, Object>> services = new ArrayList<>();
    
    private BrowserController interfaceOsiriX;
    
    /**
     * 現在のブラウザを取得
     */
    public static BonjourBrowser currentBrowser() {
        return currentBrowser;
    }
    
    /**
     * コンストラクタ
     */
    public BonjourBrowser(BrowserController browserController) {
        currentBrowser = this;
        this.interfaceOsiriX = browserController;
        
        // JmDNSの初期化
        try {
            jmdns = JmDNS.create();
            
            // サービスリスナーを設定（DICOMサービス _dicom._tcp.local）
            // ServiceListenerはServiceEventを受け取る
            serviceListener = new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    ServiceInfo service = event.getInfo();
                    processBonjourService(service);
                }
                
                @Override
                public void serviceRemoved(ServiceEvent event) {
                    ServiceInfo service = event.getInfo();
                    removeBonjourService(service);
                }
                
                @Override
                public void serviceResolved(ServiceEvent event) {
                    // サービスが解決された場合も処理
                    ServiceInfo service = event.getInfo();
                    processBonjourService(service);
                }
            };
            
            // DICOMサービスのブラウジングを開始
            jmdns.addServiceListener("_dicom._tcp.local", serviceListener);
            
        } catch (IOException e) {
            System.err.println("BonjourBrowser: JmDNS初期化に失敗: " + e.getMessage());
        }
        
        // 既存の固定リストを構築
        buildFixedIPList();
        buildLocalPathsList();
        buildDICOMDestinationsList();
        arrangeServices();
    }
    
    /**
     * サービス配列を取得（HOROS互換）
     */
    public List<Map<String, Object>> services() {
        return services;
    }
    
    /**
     * 指定された行インデックスのサービスを取得
     * HOROS準拠: row > 0 の場合、Bonjourサービスを返す
     * 
     * @param row 行インデックス
     * @return サービス情報（Map）、またはnull
     */
    public Object getServiceAtRow(int row) {
        if (row > 0 && row <= services.size()) {
            return services.get(row - 1);
        }
        return null;
    }
    
    /**
     * Bonjourで発見されたDICOMノードを取得
     */
    public List<DicomNode> getBonjourNodes() {
        return Collections.unmodifiableList(bonjourNodes);
    }
    
    /**
     * Bonjourサービスを処理（追加または更新）
     */
    private void processBonjourService(ServiceInfo service) {
        if (service == null) {
            return;
        }
        
        String name = service.getName();
        String domain = service.getDomain();
        String type = service.getType();
        
        // サービスアドレスとポートを取得
        String address = null;
        int port = service.getPort();
        
        // 収集されたIPアドレスから最初のものを使用
        String[] addresses = service.getHostAddresses();
        if (addresses != null && addresses.length > 0) {
            address = addresses[0];
        } else {
            // IPアドレスがない場合はリゾルブを試みる
            try {
                address = InetAddress.getByName(service.getQualifiedName()).getHostAddress();
            } catch (UnknownHostException e) {
                System.err.println("BonjourBrowser: アドレス解決に失敗: " + service.getQualifiedName());
                return;
            }
        }
        
        // テキストレコードからプロパティを抽出
        String aeTitle = service.getPropertyString("AE_TITLE");
        if (aeTitle == null || aeTitle.isEmpty()) {
            aeTitle = service.getPropertyString("aeTitle");
        }
        if (aeTitle == null || aeTitle.isEmpty()) {
            aeTitle = name;
        }
        
        String description = service.getPropertyString("DESCRIPTION");
        if (description == null || description.isEmpty()) {
            description = service.getPropertyString("description");
        }
        
        // 既存のノードを検索（重複防止）
        DicomNode existingNode = findBonjourNodeByAddress(address, port);
        if (existingNode != null) {
            // 既存ノードを更新
            existingNode.setName(name);
            existingNode.setAeTitle(aeTitle);
            existingNode.setDescription(description);
            System.out.println("BonjourBrowser: サービス更新 - " + existingNode.toString());
        } else {
            // 新しいノードを作成して追加
            DicomNode node = new DicomNode(name, aeTitle, address, port);
            node.setDescription(description);
            bonjourNodes.add(node);
            System.out.println("BonjourBrowser: サービス発見 - " + node.toString());
        }
    }
    
    /**
     * Bonjourサービスを削除
     */
    private void removeBonjourService(ServiceInfo service) {
        if (service == null) {
            return;
        }
        
        String address = null;
        int port = service.getPort();
        
        String[] addresses = service.getHostAddresses();
        if (addresses != null && addresses.length > 0) {
            address = addresses[0];
        }
        
        if (address != null) {
            DicomNode nodeToRemove = findBonjourNodeByAddress(address, port);
            if (nodeToRemove != null) {
                bonjourNodes.remove(nodeToRemove);
                System.out.println("BonjourBrowser: サービス削除 - " + nodeToRemove.toString());
            }
        }
    }
    
    /**
     * アドレスとポートでBonjourノードを検索
     */
    private DicomNode findBonjourNodeByAddress(String address, int port) {
        for (DicomNode node : bonjourNodes) {
            if (address.equals(node.getAddress()) && port == node.getPort()) {
                return node;
            }
        }
        return null;
    }
    
    /**
     * BonjourノードをServicesリストに追加（UI表示用）
     */
    public void addBonjourNodesToServices() {
        // 既存のbonjourエントリを削除して再構築
        services.removeIf(s -> "bonjour".equals(s.get("type")));
        
        for (DicomNode node : bonjourNodes) {
            Map<String, Object> service = new HashMap<>();
            service.put("type", "bonjour");
            service.put("name", node.getName());
            service.put("aeTitle", node.getAeTitle());
            service.put("address", node.getAddress());
            service.put("port", node.getPort());
            service.put("description", node.getDescription());
            service.put("dicomNode", node);
            services.add(service);
        }
    }
    
    /**
     * 固定IPリストを構築
     */
    public void buildFixedIPList() {
        // OSIRIXSERVERSから固定IPサーバーを取得してservicesに追加
        // 現在は空リストとして初期化
        services.removeIf(s -> "fixedIP".equals(s.get("type")));
    }
    
    /**
     * ローカルパスリストを構築
     */
    public void buildLocalPathsList() {
        // localDatabasePathsからローカルパスを取得してservicesに追加
        services.removeIf(s -> "localPath".equals(s.get("type")));
    }
    
    /**
     * DICOM宛先リストを構築
     */
    public void buildDICOMDestinationsList() {
        // DCMNetServiceDelegateからDICOMサーバーリストを取得してservicesに追加
        services.removeIf(s -> "dicomDestination".equals(s.get("type")));
    }
    
    /**
     * サービスを整理（localPath, fixedIP, bonjour, dicomDestinationの順）
     */
    public void arrangeServices() {
        // Bonjourノードをservicesに追加
        addBonjourNodesToServices();
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        // localPathを追加
        for (Map<String, Object> service : services) {
            if ("localPath".equals(service.get("type"))) {
                result.add(service);
            }
        }
        
        // fixedIPを追加
        for (Map<String, Object> service : services) {
            if ("fixedIP".equals(service.get("type"))) {
                result.add(service);
            }
        }
        
        // bonjourを追加
        for (Map<String, Object> service : services) {
            if ("bonjour".equals(service.get("type"))) {
                result.add(service);
            }
        }
        
        // dicomDestinationを追加
        for (Map<String, Object> service : services) {
            if ("dicomDestination".equals(service.get("type"))) {
                result.add(service);
            }
        }
        
        services.clear();
        services.addAll(result);
    }
    
    /**
     * 固定リストを更新（設定変更通知時に呼ばれる）
     */
    public void updateFixedList() {
        buildFixedIPList();
        buildLocalPathsList();
        buildDICOMDestinationsList();
        arrangeServices();
    }
    
    /**
     * Bonjourサービス発見を開始/再開
     */
    public void startBonjourSearch() {
        if (jmdns != null && serviceListener != null) {
            // すでに有効な場合は無視
            System.out.println("BonjourBrowser: すでにBonjour検索を実行中");
            return;
        }
        
        try {
            if (jmdns == null) {
                jmdns = JmDNS.create();
            }
            
            if (serviceListener == null) {
                serviceListener = new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent event) {
                        ServiceInfo service = event.getInfo();
                        processBonjourService(service);
                    }
                    
                    @Override
                    public void serviceRemoved(ServiceEvent event) {
                        ServiceInfo service = event.getInfo();
                        removeBonjourService(service);
                    }
                    
                    @Override
                    public void serviceResolved(ServiceEvent event) {
                        ServiceInfo service = event.getInfo();
                        processBonjourService(service);
                    }
                };
            }
            
            jmdns.addServiceListener("_dicom._tcp.local", serviceListener);
            System.out.println("BonjourBrowser: Bonjourサービス検索を開始");
            
        } catch (IOException e) {
            System.err.println("BonjourBrowser: Bonjour検索の開始に失敗: " + e.getMessage());
        }
    }
    
    /**
     * Bonjourサービス発見を停止
     */
    public void stopBonjourSearch() {
        try {
            if (jmdns != null) {
                jmdns.close();
                jmdns = null;
            }
            if (serviceListener != null) {
                serviceListener = null;
            }
            bonjourNodes.clear();
            services.removeIf(s -> "bonjour".equals(s.get("type")));
            System.out.println("BonjourBrowser: Bonjourサービス検索を停止");
        } catch (IOException e) {
            System.err.println("BonjourBrowser: Bonjour検索の停止に失敗: " + e.getMessage());
        }
    }
    
    /**
     * Bonjourブラウザをリセット（再初期化）
     */
    public void reset() {
        stopBonjourSearch();
        bonjourNodes.clear();
        services.removeIf(s -> "bonjour".equals(s.get("type")));
        startBonjourSearch();
    }
    
    /**
     * リソースを解放
     */
    public void shutdown() {
        stopBonjourSearch();
        currentBrowser = null;
    }
    
    /**
     * キー値の変更を監視（設定変更時の更新）
     */
    public void observeValueForKeyPath(String keyPath, Object object, Map<String, Object> change, Object context) {
        if (keyPath != null && keyPath.equals("values.SERVERS")) {
            updateFixedList();
        }
    }
}

