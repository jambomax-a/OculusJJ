package com.jj.dicomviewer.model;

import java.io.Serializable;

/**
 * DicomNode - リモートDICOMノード（PACSサーバ等）を表すクラス
 * 
 * HOROS-20240407のDicomNodeをJavaに移植
 */
public class DicomNode implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String aeTitle;
    private String address;
    private int port;
    private String description;

    // 検索・取得の設定
    private boolean sendCGet = false;
    private boolean sendCPack = false; // パッキング（複数画像をまとめて送信）
    private boolean useWADO = false;
    private String wadoURL;

    public DicomNode() {
    }

    public DicomNode(String name, String aeTitle, String address, int port) {
        this.name = name;
        this.aeTitle = aeTitle;
        this.address = address;
        this.port = port;
    }

    // Getter / Setter
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAeTitle() {
        return aeTitle;
    }

    public void setAeTitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSendCGet() {
        return sendCGet;
    }

    public void setSendCGet(boolean sendCGet) {
        this.sendCGet = sendCGet;
    }

    public boolean isSendCPack() {
        return sendCPack;
    }

    public void setSendCPack(boolean sendCPack) {
        this.sendCPack = sendCPack;
    }

    public boolean isUseWADO() {
        return useWADO;
    }

    public void setUseWADO(boolean useWADO) {
        this.useWADO = useWADO;
    }

    public String getWadoURL() {
        return wadoURL;
    }

    public void setWadoURL(String wadoURL) {
        this.wadoURL = wadoURL;
    }

    @Override
    public String toString() {
        return name + " (" + aeTitle + "@" + address + ":" + port + ")";
    }
}
