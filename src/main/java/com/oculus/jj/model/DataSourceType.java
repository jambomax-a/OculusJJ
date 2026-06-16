package com.oculus.jj.model;

/**
 * データのソースの種類を表す列挙型。
 */
public enum DataSourceType {
    /** ローカルのストレージ（ディスク、ネットワークドライブ等） */
    LOCAL,
    /** リモートサーバー（PACS, DICOM StoreSCU等） */
    REMOTE,
    /** クラウドストレージまたはその他の外部ソース */
    CLOUD,
    /** 未定義または解析中 */
    UNKNOWN;

    /**
     * 文字列表現を返す。
     */
    @Override
    public String toString() {
        switch (this) {
            case LOCAL: return "Local";
            case REMOTE: return "Remote";
            case CLOUD: return "Cloud";
            default: return "Unknown";
        }
    }
}
