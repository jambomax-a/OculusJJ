package com.oculus.jj.model;

import java.io.File;
import org.dcm4che3.data.Tag;

/**
 * DataNodeIdentifier - データベースソースの識別子。
 * HOROS-20240407/Horos/Sources におけるデータソース（Local, Remote等）を区別するために使用します。
 */
public abstract class DataNodeIdentifier {
    protected String location;
    protected int port;
    protected String description;

    public DataNodeIdentifier(String location, int port, String description) {
        this.location = location;
        this.port = port;
        this.description = description;
    }

    public String getLocation() { return location; }
    public int getPort() { return port; }
    public String getDescription() { return description; }

    public abstract boolean isLocal();

    @Override
    public String toString() {
        return String.format("[%s] %s:%d (%s)", this.getClass().getSimpleName(), location, port, description);
    }
}

/**
 * デフォルトのローカルデータベース識別子。
 */
class DefaultLocalDatabaseNodeIdentifier extends DataNodeIdentifier {
    public DefaultLocalDatabaseNodeIdentifier() {
        super("Default Local", 0, "Default local database");
    }

    @Override
    public boolean isLocal() { return true; }
}

/**
 * ローカルファイルシステムのデータベース識別子。
 */
class LocalDatabaseNodeIdentifier extends DataNodeIdentifier {
    public LocalDatabaseNodeIdentifier(String path) {
        super(path, 0, "Local database");
    }

    @Override
    public boolean isLocal() { return true; }
}

/**
 * リモートデータベース（サーバー）の識別子。
 */
class RemoteDatabaseNodeIdentifier extends DataNodeIdentifier {
    public RemoteDatabaseNodeIdentifier(String location, int port, String description) {
        super(location, port, description);
    }

    @Override
    public boolean isLocal() { return false; }
}
