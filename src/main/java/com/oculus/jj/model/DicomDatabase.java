package com.oculus.jj.model;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DicomDatabase - DICOMデータベース管理クラス
 * 
 * HOROS-20240407のDicomDatabaseをJavaに移植
 * Core Dataの代わりにSQLiteを使用
 */
public class DicomDatabase {

    private static final Logger logger = LoggerFactory.getLogger(DicomDatabase.class);

    // ========== 静的フィールド ==========
    public static final String CURRENT_DATABASE_VERSION = "1.0";
    public static final String OSIRIX_DATA_DIR_NAME = "OculusJJ Data";
    public static final String O2_SCREEN_CAPTURES_SERIES_NAME = "Screen Captures";

    // エンティティ名
    public static final String IMAGE_ENTITY_NAME = "Image";
    public static final String SERIES_ENTITY_NAME = "Series";
    public static final String STUDY_ENTITY_NAME = "Study";
    public static final String ALBUM_ENTITY_NAME = "Album";
    public static final String LOG_ENTRY_ENTITY_NAME = "LogEntry";

    private static DicomDatabase defaultDatabase = null;
    private static DicomDatabase activeLocalDatabase = null;

    // ========== インスタンスフィールド ==========
    private String baseDirPath; // "Horos Data"
    private String dataBaseDirPath; // DBFOLDER_LOCATIONに基づく
    private String sourcePath;
    private String name;

    private ReentrantLock processFilesLock;
    private ReentrantLock importFilesFromIncomingDirLock;

    private boolean isFileSystemFreeSizeLimitReached;
    private long timeOfLastIsFileSystemFreeSizeLimitReachedVerification;
    private long timeOfLastModification;

    private boolean isReadOnly;
    private boolean hasPotentiallySlowDataAccess;
    private boolean isLocal = true; // デフォルトはローカル

    // 圧縮/解凍
    private List<String> decompressQueue;
    private List<String> compressQueue;
    private Thread compressDecompressThread;

    // ルーティング
    private List<Object> routingSendQueues;
    private ReentrantLock routingLock;

    // クリーン
    private ReentrantLock cleanLock;

    private volatile boolean deallocating;

    // データファイルインデックス
    private int dataFileIndex;

    // スタディリスト（簡易実装）
    private List<DicomStudy> studies;

    // アルバムリスト
    private List<DicomAlbum> albums;

    // ファイルパスからImageへのマッピング（簡易実装）
    private java.util.Map<String, DicomImage> filePathToImageMap;

    // SQLite接続
    private Connection sqliteConnection;
    private String sqliteDatabasePath;

    // ========== コンストラクタ ==========

    /**
     * プライベートコンストラクタ
     * HOROS-20240407準拠: - (id)initWithPath:
     */
    private DicomDatabase(String baseDirPath, String name) {
        this.baseDirPath = baseDirPath;
        this.name = name;
        // HOROS-20240407準拠: DicomDatabase.mm 416-417行目
        // _dataBaseDirPath = [NSString stringWithContentsOfFile:[p
        // stringByAppendingPathComponent:@"DBFOLDER_LOCATION"]
        // encoding:NSUTF8StringEncoding error:NULL];
        // if (!_dataBaseDirPath) _dataBaseDirPath = p;
        File dbFolderLocationFile = new File(baseDirPath, "DBFOLDER_LOCATION");
        if (dbFolderLocationFile.exists() && dbFolderLocationFile.isFile()) {
            try {
                java.nio.file.Files.readAllLines(dbFolderLocationFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)
                        .stream()
                        .findFirst()
                        .ifPresent(path -> {
                            String trimmedPath = path.trim();
                            if (new File(trimmedPath).exists()) {
                                this.dataBaseDirPath = trimmedPath;
                                // System.out.println(">>> Database directory from DBFOLDER_LOCATION: " +
                                // trimmedPath);
                            } else {
                                this.dataBaseDirPath = baseDirPath;
                                // System.out.println(">>> DBFOLDER_LOCATION path not found: " + trimmedPath +
                                // ". Fallback: " + baseDirPath);
                            }
                        });
            } catch (Exception e) {
                this.dataBaseDirPath = baseDirPath;
                // System.out.println(">>> Error reading DBFOLDER_LOCATION. Fallback: " +
                // baseDirPath);
            }
        }
        if (this.dataBaseDirPath == null) {
            this.dataBaseDirPath = baseDirPath;
            // System.out.println(">>> Using baseDirPath as dataBaseDirPath: " +
            // baseDirPath);
        }
        this.processFilesLock = new ReentrantLock();
        this.importFilesFromIncomingDirLock = new ReentrantLock();
        this.routingLock = new ReentrantLock();
        this.cleanLock = new ReentrantLock();
        this.decompressQueue = new java.util.ArrayList<>();
        this.compressQueue = new java.util.ArrayList<>();
        this.routingSendQueues = new java.util.ArrayList<>();
        this.studies = new java.util.ArrayList<>();
        this.albums = new java.util.ArrayList<>();
        this.filePathToImageMap = new java.util.HashMap<>();

        // HOROS-20240407準拠: SQLiteデータベースを初期化
        initializeSQLiteDatabase();

        // HOROS-20240407準拠: データベースからデータを読み込む
        loadFromDatabase();

        String patientUidMigrationKey = patientUidMigrationKey(this.dataBaseDirPath);
        if (!com.oculus.jj.config.AppPreferences.getBool(patientUidMigrationKey, false)
                && recomputePatientUIDsInContext(this)) {
            com.oculus.jj.config.AppPreferences.setBool(patientUidMigrationKey, true);
        }

        // HOROS-20240407準拠: アルバムが空の場合はデフォルトアルバムを作成
        if (albums.isEmpty()) {
            createDefaultAlbums();
            save();
        }
        if (com.oculus.jj.config.AppPreferences.getBool("database.autoCleanEnabled", false)
                || com.oculus.jj.config.AppPreferences.getBool("database.deleteEntireAtStartup", false)) {
            runDatabaseAutoCleaning(true);
        }
    }

    // HOROS-20240407準拠: NSNotificationCenter相当のリスナー
    private final List<Runnable> databaseChangedListeners = new java.util.ArrayList<>();

    // ========== 静的メソッド ==========

    /**
     * データベース変更リスナーを追加
     */
    public void addDatabaseChangedListener(Runnable listener) {
        synchronized (databaseChangedListeners) {
            databaseChangedListeners.add(listener);
        }
    }

    /**
     * データベース変更リスナーを削除
     */
    public void removeDatabaseChangedListener(Runnable listener) {
        synchronized (databaseChangedListeners) {
            databaseChangedListeners.remove(listener);
        }
    }

    /**
     * データベース変更をリスナーに通知
     */
    protected void notifyDatabaseChanged() {
        synchronized (databaseChangedListeners) {
            for (Runnable listener : databaseChangedListeners) {
                javax.swing.SwingUtilities.invokeLater(listener);
            }
        }
    }

    /**
     * DicomDatabaseクラスを初期化
     */
    public static void initializeDicomDatabaseClass() {
    }

    /**
     * コンテキスト内の患者UIDを再計算
     */
    public static boolean recomputePatientUIDsInContext(Object context) {
        DicomDatabase db = context instanceof DicomDatabase ? (DicomDatabase) context : activeLocalDatabase();
        if (db == null) return false;
        synchronized (db.studies) {
            for (DicomStudy study : db.studies) {
                if (study == null) continue;
                Date birthDate = study.getDateOfBirth() == null ? null
                        : Date.from(study.getDateOfBirth().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
                study.setPatientUID(db.calculatePatientUID(study.getPatientName(), study.getPatientID(), birthDate));
            }
            boolean saved = db.save();
            if (saved) db.notifyDatabaseChanged();
            return saved;
        }
    }

    private static String patientUidMigrationKey(String path) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(path).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder("database.patientUIDSchema.v2.");
            for (int i = 0; i < 8; i++) key.append(String.format(java.util.Locale.ROOT, "%02x", digest[i]));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * デフォルトのベースディレクトリパスを取得
     */
    public static String defaultBaseDirPath() {
        return com.oculus.jj.config.AppPreferences.getDatabaseFolder();
    }

    /**
     * パスのベースディレクトリパスを取得
     */
    public static String baseDirPathForPath(String path) {
        return path;
    }

    /**
     * モードとパスからベースディレクトリパスを取得
     */
    public static String baseDirPathForMode(int mode, String path) {
        return path;
    }

    /**
     * すべてのデータベースを取得
     */
    public static List<DicomDatabase> allDatabases() {
        return new java.util.ArrayList<>();
    }

    /**
     * デフォルトデータベースを取得
     * HOROS-20240407準拠: NSUserDefaultsから新しいパスを取得しているため、キャッシュが古い場合はリセット
     */
    public static DicomDatabase defaultDatabase() {
        String currentBasePath = defaultBaseDirPath(); // HOROS-20240407準拠: 毎回最新のパスを取得
        if (defaultDatabase == null) {
            defaultDatabase = databaseAtPath(currentBasePath);
        } else if (!java.util.Objects.equals(defaultDatabase.getBaseDirPath(), currentBasePath)) {
            // HOROS-20240407準拠: パスが変更された場合は古いインスタンスを破棄して再作成
            logger.info("Resetting database cache - base path changed from {} to {}",
                    defaultDatabase.getBaseDirPath(), currentBasePath);
            defaultDatabase = null;
            activeLocalDatabase = null; // also clear this reference
            defaultDatabase = databaseAtPath(currentBasePath);
        }
        return defaultDatabase;
    }

    /**
     * パスのデータベースを取得
     */
    public static DicomDatabase databaseAtPath(String path) {
        return databaseAtPath(path, null);
    }

    /**
     * パスと名前でデータベースを取得
     */
    public static DicomDatabase databaseAtPath(String path, String name) {
        // 既存のデータベースを返すか、新規作成
        return new DicomDatabase(path, name != null ? name : "Database");
    }

    /**
     * 既存のデータベースをパスで取得
     */
    public static DicomDatabase existingDatabaseAtPath(String path) {
        return null;
    }

    /**
     * コンテキストのデータベースを取得（非推奨）
     */
    @Deprecated
    public static DicomDatabase databaseForContext(Object context) {
        return null;
    }

    /**
     * アクティブなローカルデータベースを取得
     * HOROS-20240407準拠: activeLocalDatabaseがnullの場合はdefaultDatabaseを返す
     */
    public static DicomDatabase activeLocalDatabase() {
        String currentBasePath = defaultBaseDirPath(); // HOROS-20240407準拠: 毎回最新のパスをチェック
        if (activeLocalDatabase == null) {
            activeLocalDatabase = defaultDatabase();
        } else if (!java.util.Objects.equals(activeLocalDatabase.getBaseDirPath(), currentBasePath)) {
            // HOROS-20240407準拠: パス不整合の場合はリセットして再作成
            logger.info("Resetting active database cache - base path changed from {} to {}",
                    activeLocalDatabase.getBaseDirPath(), currentBasePath);
            activeLocalDatabase = null;
            defaultDatabase = null;
            return defaultDatabase();
        }
        return activeLocalDatabase;
    }

    /**
     * データベースインスタンスをリセット（パス変更時に呼ぶ）
     * HOROS-20240407準拠: NSUserDefaults監視に類似した処理
     */
    public static void resetCachedDatabases() {
        logger.info("Resetting cached database instances");
        defaultDatabase = null;
        activeLocalDatabase = null;
    }

    public static void setActiveLocalDatabase(DicomDatabase database) {
        activeLocalDatabase = database;
    }
    // ========== プロパティ ==========

    /**
     * ベースディレクトリパスを取得
     */
    public String getBaseDirPath() {
        return baseDirPath;
    }

    /**
     * データベースディレクトリパスを取得
     */
    public String getDataBaseDirPath() {
        return dataBaseDirPath;
    }

    /**
     * 名前を取得
     */
    public String getName() {
        return name;
    }

    /**
     * 名前を設定
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Study Instance UIDでStudyを取得
     */
    public DicomStudy getStudyByUID(String studyUID) {
        synchronized (studies) {
            if (studies == null || studyUID == null) {
                return null;
            }
            for (DicomStudy s : studies) {
                if (studyUID.equals(s.getStudyInstanceUID())) {
                    return s;
                }
            }
        }
        return null;
    }

    public DicomImage findImageByUIDs(String studyUID, String seriesUID, String sopInstanceUID) {
        if (studyUID == null || seriesUID == null || sopInstanceUID == null) return null;
        DicomStudy study = getStudyByUID(studyUID);
        if (study == null || study.isDistant()) return null;
        DicomSeries series = study.getSeriesByUID(seriesUID);
        if (series == null) return null;
        loadImagesForSeriesIfNeeded(series);
        if (series.getImages() == null) return null;
        for (DicomImage image : series.getImages()) {
            if (image != null && sopInstanceUID.equals(image.getSopInstanceUID())) return image;
        }
        return null;
    }

    public boolean addRemoteStudy(DicomStudy remoteStudy) {
        if (remoteStudy == null || remoteStudy.getStudyInstanceUID() == null
                || remoteStudy.getStudyInstanceUID().isBlank() || remoteStudy.getSourceNodeName() == null
                || remoteStudy.getSourceNodeName().isBlank()) return false;
        synchronized (studies) {
            DicomStudy existing = getStudyByUID(remoteStudy.getStudyInstanceUID());
            if (existing != null) {
                return existing.isDistant()
                        && remoteStudy.getSourceNodeName().equals(existing.getSourceNodeName());
            }
            studies.add(remoteStudy);
        }
        if (!save(java.util.Collections.singletonList(remoteStudy))) {
            synchronized (studies) {
                studies.remove(remoteStudy);
            }
            return false;
        }
        notifyDatabaseChanged();
        return true;
    }

    /**
     * スタディリストを取得
     * HOROS-20240407準拠
     */
    public List<DicomStudy> getStudies() {
        return studies;
    }

    /**
     * ソースパスを取得
     */
    public String getSourcePath() {
        return sourcePath;
    }

    /**
     * ソースパスを設定
     */
    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    /**
     * 最終変更時刻を取得
     */
    public long getTimeOfLastModification() {
        return timeOfLastModification;
    }

    /**
     * 最終変更時刻を設定
     */
    public void setTimeOfLastModification(long time) {
        this.timeOfLastModification = time;
    }

    /**
     * 読み取り専用かどうか
     */
    public boolean isReadOnly() {
        return isReadOnly;
    }

    /**
     * 読み取り専用を設定
     */
    public void setReadOnly(boolean readOnly) {
        this.isReadOnly = readOnly;
    }

    /**
     * 潜在的に遅いデータアクセスがあるかどうか
     */
    public boolean hasPotentiallySlowDataAccess() {
        return hasPotentiallySlowDataAccess;
    }

    /**
     * 潜在的に遅いデータアクセスを設定
     */
    public void setHasPotentiallySlowDataAccess(boolean hasSlowAccess) {
        this.hasPotentiallySlowDataAccess = hasSlowAccess;
    }

    /**
     * ローカルデータベースかどうか
     */
    public boolean isLocal() {
        return isLocal;
    }

    /**
     * データノード識別子を取得
     */
    public Object dataNodeIdentifier() {
        return null;
    }

    // ========== SQLite関連メソッド ==========

    /**
     * SQLiteデータベースを初期化
     * HOROS-20240407準拠: DicomDatabase.mm 755-757行目 sqlFilePathForBasePath
     * return [basePath stringByAppendingPathComponent:SqlFileName];
     * SqlFileName = @"Database.sql"
     */
    private void initializeSQLiteDatabase() {
        try {
            // HOROS-20240407準拠: DicomDatabase.mm 410行目
            // NSString* sqlFilePath = [DicomDatabase sqlFilePathForBasePath:p];
            // sqlFilePathForBasePathはbasePath/Database.sqlを返す（755-757行目）
            // しかし、実際のファイルはdataBaseDirPath/DATABASE.noindex/Database.sqlにある
            // HOROS-20240407準拠: dataBaseDirPath/DATABASE.noindex/Database.sqlを使用
            File dbDir = new File(dataBaseDirPath, "DATABASE.noindex");
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }
            sqliteDatabasePath = new File(dbDir, "Database.sql").getAbsolutePath();
            // System.out.println(">>> Initializing SQLite Database (Read/Write) at: " +
            // sqliteDatabasePath);

            // SQLite接続を開く
            sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + sqliteDatabasePath);

            // WALモードを有効化（読込と書き込みを並行して行えるようにし、UIレスポンスを改善）
            try (Statement stmt = sqliteConnection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            }

            // テーブルを作成
            createTables();

            // マイグレーション
            migrateDatabase();
            migrateImageMetadata();
        } catch (SQLException e) {
            // System.err.println("!!! Failed to initialize SQLite database [" +
            // sqliteDatabasePath + "]: " + e.getMessage());
        }
    }

    /**
     * SQLiteテーブルを作成
     * HOROS-20240407準拠: Study, Series, Image, Albumエンティティ
     */
    private void createTables() throws SQLException {
        try (Statement stmt = sqliteConnection.createStatement()) {
            // Studyテーブル
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS Study (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            studyInstanceUID TEXT UNIQUE,
                            name TEXT,
                            patientID TEXT,
                            patientUID TEXT,
                            patientBirthDate TEXT,
                            patientSex TEXT,
                            studyName TEXT,
                            modality TEXT,
                            date TEXT,
                            dateAdded TEXT,
                            numberOfImages INTEGER DEFAULT 0,
                            comment TEXT,
                            accessionNumber TEXT,
                            referringPhysician TEXT,
                            performingPhysician TEXT,
                            institutionName TEXT,
                            studyID TEXT,
                            sourceNodeName TEXT,
                            hidden INTEGER DEFAULT 0
                        )
                    """);

            // Seriesテーブル
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS Series (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            seriesInstanceUID TEXT UNIQUE,
                            studyId INTEGER,
                            name TEXT,
                            seriesDescription TEXT,
                            modality TEXT,
                            date TEXT,
                            dateAdded TEXT,
                            numberOfImages INTEGER DEFAULT 0,
                            seriesNumber INTEGER,
                            seriesSOPClassUID TEXT,
                            windowLevel REAL,
                            windowWidth REAL,
                            FOREIGN KEY (studyId) REFERENCES Study(id)
                        )
                    """);

            // Imageテーブル
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS Image (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            sopInstanceUID TEXT UNIQUE,
                            seriesId INTEGER,
                            pathNumber INTEGER,
                            pathString TEXT,
                            completePath TEXT,
                            inDatabaseFolder INTEGER DEFAULT 1,
                            instanceNumber INTEGER,
                            numberOfFrames INTEGER DEFAULT 1,
                            frameID INTEGER,
                            date TEXT,
                            modality TEXT,
                            fileType TEXT,
                            height INTEGER,
                            width INTEGER,
                            sliceThickness REAL,
                            spacingBetweenSlices REAL,
                            pixelSpacingX REAL,
                            pixelSpacingY REAL,
                            sliceLocation REAL,
                           patientName TEXT,
                           studyDate TEXT,
                           seriesDate TEXT,
                           acquisitionDate TEXT,
                           manufacturerModelString TEXT,
                           deviceSerialNumber TEXT,
                           kvp REAL,
                           ma REAL,
                           tr REAL,
                           te REAL,
                           dynamicRange TEXT,
                           frequency TEXT,
                           FOREIGN KEY (seriesId) REFERENCES Series(id)
                       )
                    """);

            // Albumテーブル
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS Album (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT,
                            smartAlbum INTEGER DEFAULT 0,
                            predicate TEXT,
                            dateAdded TEXT
                        )
                    """);

            // Album-Study関連テーブル
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS AlbumStudy (
                            albumId INTEGER,
                            studyId INTEGER,
                            PRIMARY KEY (albumId, studyId),
                            FOREIGN KEY (albumId) REFERENCES Album(id),
                            FOREIGN KEY (studyId) REFERENCES Study(id)
                        )
                    """);

            // ROIテーブル
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS ROI (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            sopInstanceUID TEXT,
                            frame INTEGER,
                            type TEXT,
                            points TEXT,
                            name TEXT,
                            color INTEGER,
                            thickness REAL,
                            pixelSpacingX REAL,
                            pixelSpacingY REAL,
                            textBoxOffsetX REAL DEFAULT 0,
                            textBoxOffsetY REAL DEFAULT 0
                        )
                    """);

            // 旧バージョンからのマイグレーション用（カラムがない場合は追加）
            try {
                stmt.execute("ALTER TABLE ROI ADD COLUMN textBoxOffsetX REAL DEFAULT 0");
            } catch (SQLException e) {
                // カラムが既に存在する場合は無視
            }
            try {
                stmt.execute("ALTER TABLE ROI ADD COLUMN textBoxOffsetY REAL DEFAULT 0");
            } catch (SQLException e) {
                // カラムが既に存在する場合は無視
            }

            // インデックス追加
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_roi_sop ON ROI(sopInstanceUID)");

            // データベースマイグレーション: hiddenカラムを追加
            migrateDatabase();
            // 追加のメタデータカラム（sliceThickness等）を適用
            migrateImageMetadata();
        }
    }

    /**
     * データベースマイグレーション
     * 既存のデータベースに新しいカラムを追加
     */
    private void migrateDatabase() {
        try {
            // hiddenカラムが存在するか確認
            boolean hasHiddenColumn = false;
            try {
                try (ResultSet rs = sqliteConnection.getMetaData().getColumns(null, null, "Study", "hidden")) {
                    hasHiddenColumn = rs.next();
                }
            } catch (SQLException e) {
                // メタデータ取得に失敗した場合は、ALTER TABLEを試行
                hasHiddenColumn = false;
            }

            if (!hasHiddenColumn) {

                try (Statement stmt = sqliteConnection.createStatement()) {
                    stmt.execute("ALTER TABLE Study ADD COLUMN hidden INTEGER DEFAULT 0");

                } catch (SQLException e) {
                    // カラムが既に存在する場合は無視
                    if (e.getMessage() != null && e.getMessage().contains("duplicate column name")) {

                    } else {

                        // e.getMessage());
                    }
                }
            }

            boolean hasSourceNodeColumn = false;
            try (ResultSet rs = sqliteConnection.getMetaData().getColumns(null, null, "Study", "sourceNodeName")) {
                hasSourceNodeColumn = rs.next();
            }
            if (!hasSourceNodeColumn) {
                try (Statement stmt = sqliteConnection.createStatement()) {
                    stmt.execute("ALTER TABLE Study ADD COLUMN sourceNodeName TEXT");
                }
            }
        } catch (Exception e) {

        }
    }

    /**
     * Imageテーブルへのメタデータカラム追加（migration）
     */
    private void migrateImageMetadata() {
        try (Statement stmt = sqliteConnection.createStatement()) {
            // sliceThickness, spacingBetweenSlices, pixelSpacingX, pixelSpacingY の追加を試行
            String[] numericCols = { "sliceThickness", "spacingBetweenSlices", "pixelSpacingX", "pixelSpacingY", "kvp",
                    "ma", "tr", "te" };
            for (String col : numericCols) {
                try {
                    stmt.execute("ALTER TABLE Image ADD COLUMN " + col + " REAL DEFAULT 0");
                } catch (SQLException e) {
                    if (e.getMessage() != null && e.getMessage().contains("duplicate column name")) {

                    } else {

                        // e.getMessage());
                    }
                }
            }
            // dynamicRange, frequency の追加を試行
            String[] textCols = { "dynamicRange", "frequency" };
            for (String col : textCols) {
                try {
                    stmt.execute("ALTER TABLE Image ADD COLUMN " + col + " TEXT");
                } catch (SQLException e) {
                    if (e.getMessage() != null && e.getMessage().contains("duplicate column name")) {

                    } else {

                        // e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {

        }
    }

    /**
     * データベースからデータを読み込む
     * HOROS-20240407準拠: 起動時にDBからStudy-Series-Imageを読み込む
     */
    private void loadFromDatabase() {
        if (sqliteConnection == null) {
            // System.out.println(">>> SQLite connection is null, cannot load data");
            return;
        }

        try {
            // System.out.println(">>> Loading data from SQLite database (Read) at: " +
            // sqliteDatabasePath);

            // データベースファイルの存在確認
            File dbFile = new File(sqliteDatabasePath);
            if (!dbFile.exists()) {
                // System.out.println(">>> Database file does not exist: " +
                // sqliteDatabasePath);
                return;
            }

            // Studyを読み込む
            loadStudies();

            // HOROS-20240407準拠: アルバムを読み込む
            loadAlbums();

            // System.out.println(">>> Loaded " + studies.size() + " studies, " +
            // albums.size() + " albums from database");
        } catch (SQLException e) {
            // System.err.println("!!! SQLite Load Error [" + sqliteDatabasePath + "]: " +
            // e.getMessage());
        } catch (Exception e) {
            // System.err.println("!!! Unexpected Load Error [" + sqliteDatabasePath + "]: "
            // + e.getMessage());
        }
    }

    /**
     * アルバムをデータベースから読み込む
     * HOROS-20240407準拠: Albumテーブルからアルバムを読み込む
     */
    private void loadAlbums() throws SQLException {
        synchronized (albums) {
            albums.clear();

            // HOROS-20240407準拠: すべてのテーブルをリストアップして確認
            try {

                String listTablesSql = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name";
                try (Statement listStmt = sqliteConnection.createStatement();
                        ResultSet listRs = listStmt.executeQuery(listTablesSql)) {
                    java.util.List<String> tableNames = new java.util.ArrayList<>();
                    while (listRs.next()) {
                        String tableName = listRs.getString("name");
                        tableNames.add(tableName);
                    }

                }

                // テーブルの存在確認
                String checkTableSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='Album'";
                boolean albumTableExists = false;
                try (Statement checkStmt = sqliteConnection.createStatement();
                        ResultSet checkRs = checkStmt.executeQuery(checkTableSql)) {
                    albumTableExists = checkRs.next();
                }

                if (!albumTableExists) {

                    return;
                }

                // アルバムの数を確認
                String countSql = "SELECT COUNT(*) as count FROM Album";
                int albumCount = 0;
                try (Statement countStmt = sqliteConnection.createStatement();
                        ResultSet countRs = countStmt.executeQuery(countSql)) {
                    if (countRs.next()) {
                        albumCount = countRs.getInt("count");
                    }
                }

                String sql = "SELECT * FROM Album ORDER BY name";
                try (Statement stmt = sqliteConnection.createStatement();
                        ResultSet rs = stmt.executeQuery(sql)) {

                    int count = 0;
                    while (rs.next()) {
                        DicomAlbum album = new DicomAlbum();
                        int albumDbId = rs.getInt("id");
                        album.setIndex(albumDbId); // データベースIDをindexとして設定
                        album.setName(rs.getString("name"));

                        // HOROS-20240407準拠: smartAlbumはINTEGER（0または1）
                        int smartAlbumInt = rs.getInt("smartAlbum");
                        album.setSmartAlbum(smartAlbumInt != 0);

                        // HOROS-20240407準拠: predicateString（スマートアルバムの場合）
                        if (album.isSmartAlbum()) {
                            album.setPredicateString(rs.getString("predicate"));
                        }

                        // HOROS-20240407準拠: AlbumStudyテーブルからStudyを読み込む
                        loadAlbumStudies(album, albumDbId);

                        albums.add(album);
                        count++;

                    }

                }
            } catch (SQLException e) {

                // e);
                throw e;
            }
        }
    }

    /**
     * アルバムに属するStudyを読み込む
     * HOROS-20240407準拠: AlbumStudyテーブルからStudyを読み込む
     */
    private void loadAlbumStudies(DicomAlbum album, int albumDbId) throws SQLException {
        String sql = "SELECT studyId FROM AlbumStudy WHERE albumId = ?";
        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            pstmt.setInt(1, albumDbId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int studyDbId = rs.getInt("studyId");

                    // Studyを検索
                    DicomStudy study = findStudyByDbId(studyDbId);
                    if (study != null) {
                        album.addStudiesObject(study);
                    }
                }
            }
        }
    }

    /**
     * データベースIDでStudyを検索
     */
    private DicomStudy findStudyByDbId(int studyDbId) {
        synchronized (studies) {
            for (DicomStudy study : studies) {
                String id = study.getId();
                if (id != null && id.equals(String.valueOf(studyDbId))) {
                    return study;
                }
            }
        }
        return null;
    }

    /**
     * Studyを読み込む（遅延読み込み対応）
     * HOROS-20240407準拠: 起動時はStudyのみ読み込み、Series/Imageは必要時に読み込む
     */
    private void loadStudies() throws SQLException {
        synchronized (studies) {
            studies.clear();
        }

        // HOROS-20240407準拠: Studyテーブルからデータを読み込む
        String sql = "SELECT * FROM Study ORDER BY date DESC";
        try (Statement stmt = sqliteConnection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            int count = 0;
            while (rs.next()) {
                count++;
                DicomStudy study = new DicomStudy();
                int studyDbId = rs.getInt("id");
                String studyID_text = rs.getString("studyID");

                if (count <= 5) {

                    // studyDbId, studyID_text,
                    // // rs.getString("name"));
                }

                study.setInternalDbId(studyDbId); // DBのプライマリキーを保存
                study.setStudyInstanceUID(rs.getString("studyInstanceUID"));
                study.setName(rs.getString("name"));
                study.setPatientID(rs.getString("patientID"));
                study.setPatientUID(rs.getString("patientUID"));
                study.setModality(rs.getString("modality"));
                study.setNumberOfImages(rs.getInt("numberOfImages"));
                study.setComment(rs.getString("comment"));
                study.setAccessionNumber(rs.getString("accessionNumber"));
                study.setReferringPhysician(rs.getString("referringPhysician"));
                study.setPerformingPhysician(rs.getString("performingPhysician"));
                study.setInstitutionName(rs.getString("institutionName"));
                study.setStudyID(rs.getString("studyID"));
                try {
                    study.setSourceNodeName(rs.getString("sourceNodeName"));
                } catch (SQLException ignored) {
                    study.setSourceNodeName(null);
                }

                // HOROS-20240407準拠: studyNameを読み込む
                study.setStudyName(rs.getString("studyName"));

                // HOROS-20240407準拠: hiddenを読み込む
                try {
                    int hiddenValue = rs.getInt("hidden");
                    study.setHidden(hiddenValue != 0);
                } catch (SQLException e) {
                    // hiddenカラムが存在しない場合はデフォルト値（false）を使用
                    study.setHidden(false);
                }

                // HOROS-20240407準拠: dateOfBirthを読み込む（patientBirthDateから）
                String patientBirthDateStr = rs.getString("patientBirthDate");
                if (patientBirthDateStr != null && !patientBirthDateStr.isEmpty()) {
                    try {
                        study.setDateOfBirth(java.time.LocalDate.parse(patientBirthDateStr));
                    } catch (Exception e) {
                        // 日付パースエラーは無視
                    }
                }

                // HOROS-20240407準拠: idプロパティの値を設定
                // HOROS-20240407準拠: BrowserController.m 6560-6561行目では、[item
                // valueForKey:@"id"]を返している
                // HOROS-20240407準拠: ユーザー確認により、ID列にはStudyID（DICOMのStudyIDタグ）が表示されている
                // HOROS-20240407準拠: DicomStudy.h 66行目 - @property(nonatomic, retain) NSString*
                // id;
                // HOROS-20240407準拠: idプロパティにはStudyIDの値が設定されている
                String studyID = study.getStudyID();
                if (studyID != null && !studyID.isEmpty()) {
                    // HOROS-20240407準拠: StudyIDをidプロパティとして使用
                    study.setId(studyID);
                } else {
                    // StudyIDがない場合は、空文字列を設定
                    study.setId("");
                }

                // HOROS-20240407準拠: DBのIDを内部で保持（Series/Imageの読み込みに必要）
                // 注意: study.getId()はStudyID（DICOMタグ）を返すため、DBのIDは別途保持する必要がある
                // ただし、HOROS-20240407ではCore DataのobjectIDを使用しているため、Java実装では
                // studyDbIdを文字列としてidプロパティに設定するか、別の方法で保持する必要がある
                // 現在は、loadSeriesForStudyIfNeeded()でstudyInstanceUIDからstudyDbIdを取得する方法を使用

                String dateStr = rs.getString("date");
                if (dateStr != null && !dateStr.isEmpty()) {
                    try {
                        study.setDate(LocalDateTime.parse(dateStr));
                    } catch (Exception e) {
                        // 日付パースエラーは無視
                    }
                }

                // HOROS-20240407準拠: dateAddedフィールドを読み込む
                String dateAddedStr = rs.getString("dateAdded");
                if (dateAddedStr != null && !dateAddedStr.isEmpty()) {
                    try {
                        study.setDateAdded(LocalDateTime.parse(dateAddedStr));
                    } catch (Exception e) {
                        // 日付パースエラーは無視
                    }
                }

                // HOROS-20240407準拠: 遅延読み込み - Seriesは必要時に読み込む
                // loadSeriesForStudy(study, studyDbId);

                synchronized (studies) {
                    studies.add(study);
                }
            }

        } catch (SQLException e) {

            // e);
            throw e;
        }
    }

    /**
     * StudyのSeriesを遅延読み込み
     * HOROS-20240407準拠: Studyが選択されたときに呼び出される
     */
    public void loadSeriesForStudyIfNeeded(DicomStudy study) {
        if (study == null) {
            return;
        }
        if (study.getSeries() != null && !study.getSeries().isEmpty()) {
            return; // 既に読み込み済み
        }

        try {
            int studyDbId = study.getInternalDbId();
            if (studyDbId <= 0) {
                // internalDbIdが設定されていない場合はUIDから取得を試みる
                String studyInstanceUID = study.getStudyInstanceUID();
                if (studyInstanceUID != null && !studyInstanceUID.isEmpty()) {
                    studyDbId = getStudyDbId(studyInstanceUID);
                }
            }

            // study.getName(), studyDbId);
            if (studyDbId > 0) {
                loadSeriesForStudy(study, studyDbId);
            } else {

            }
        } catch (Exception e) {

            // study.getStudyInstanceUID(), e);
        }
    }

    /**
     * StudyのSeriesを読み込む
     */
    private void loadSeriesForStudy(DicomStudy study, int studyDbId) throws SQLException {
        String sql = "SELECT * FROM Series WHERE studyId = ? ORDER BY seriesNumber";

        // studyDbId);
        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            pstmt.setInt(1, studyDbId);
            try (ResultSet rs = pstmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    String seriesInstanceUID = rs.getString("seriesInstanceUID");

                    DicomSeries series = study.getSeriesByUID(seriesInstanceUID);
                    if (series == null) {
                        series = new DicomSeries();
                        series.setSeriesInstanceUID(seriesInstanceUID);
                    }

                    series.setName(rs.getString("name"));
                    series.setSeriesDescription(rs.getString("seriesDescription"));
                    series.setModality(rs.getString("modality"));
                    series.setNumberOfImages(rs.getInt("numberOfImages"));
                    series.setSeriesNumber(rs.getInt("seriesNumber"));
                    series.setSeriesSOPClassUID(rs.getString("seriesSOPClassUID"));
                    series.setWindowLevel((double) rs.getFloat("windowLevel"));
                    series.setWindowWidth((double) rs.getFloat("windowWidth"));
                    series.setStudy(study);

                    try {
                        series.setThumbnail(rs.getBytes("thumbnail"));
                    } catch (Exception e) {
                        // Ignore
                    }

                    String dateStr = rs.getString("date");
                    if (dateStr != null && !dateStr.isEmpty()) {
                        try {
                            series.setDate(LocalDateTime.parse(dateStr));
                        } catch (Exception e) {
                        }
                    }

                    String dateAddedStr = rs.getString("dateAdded");
                    if (dateAddedStr != null && !dateAddedStr.isEmpty()) {
                        try {
                            series.setDateAdded(LocalDateTime.parse(dateAddedStr));
                        } catch (Exception e) {
                        }
                    }

                    study.addSeriesObject(series);
                }

                // study.getName());
            }
        }
    }

    /**
     * SeriesのImageを遅延読み込み
     * HOROS-20240407準拠: Seriesが選択されたときに呼び出される
     */
    public void loadImagesForSeriesIfNeeded(DicomSeries series) {
        if (series == null || sqliteConnection == null || (series.getImages() != null && !series.getImages().isEmpty())) {
            return; // 既に読み込み済み
        }

        synchronized (sqliteConnection) {
            try {
                // SeriesのDBIDを取得（StudyのSeriesから検索）
                String sql = "SELECT id FROM Series WHERE seriesInstanceUID = ?";
                try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
                    pstmt.setString(1, series.getSeriesInstanceUID());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            int seriesDbId = rs.getInt("id");
                            loadImagesForSeries(series, seriesDbId);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to lazy-load images for series {}", series.getSeriesInstanceUID(), e);
            }
        }
    }

    /**
     * ROIを保存
     */
    public void saveROI(ROI roi, String sopInstanceUID, int frame) {
        if (roi == null || sopInstanceUID == null)
            return;

        if (roi.getId() != -1) {
            // 更新
            String sql = "UPDATE ROI SET type = ?, points = ?, name = ?, color = ?, thickness = ?, pixelSpacingX = ?, pixelSpacingY = ?, textBoxOffsetX = ?, textBoxOffsetY = ? WHERE id = ?";
            try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
                pstmt.setString(1, roi.getType().name());
                pstmt.setString(2, serializePoints(roi.getPoints()));
                pstmt.setString(3, roi.getName());
                pstmt.setInt(4, roi.getColor().getRGB());
                pstmt.setDouble(5, roi.getThickness());
                pstmt.setDouble(6, roi.getPixelSpacingX());
                pstmt.setDouble(7, roi.getPixelSpacingY());
                pstmt.setDouble(8, (double) roi.getTextBoxOffsetX());
                pstmt.setDouble(9, (double) roi.getTextBoxOffsetY());
                pstmt.setInt(10, roi.getId());
                pstmt.executeUpdate();
            } catch (SQLException e) {

            }
        } else {
            // 新規挿入
            String sql = "INSERT INTO ROI (sopInstanceUID, frame, type, points, name, color, thickness, pixelSpacingX, pixelSpacingY, textBoxOffsetX, textBoxOffsetY) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql,
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, sopInstanceUID);
                pstmt.setInt(2, frame);
                pstmt.setString(3, roi.getType().name());
                pstmt.setString(4, serializePoints(roi.getPoints()));
                pstmt.setString(5, roi.getName());
                pstmt.setInt(6, roi.getColor().getRGB());
                pstmt.setDouble(7, (double) roi.getThickness());
                pstmt.setDouble(8, roi.getPixelSpacingX());
                pstmt.setDouble(9, roi.getPixelSpacingY());
                pstmt.setDouble(10, (double) roi.getTextBoxOffsetX());
                pstmt.setDouble(11, (double) roi.getTextBoxOffsetY());
                pstmt.executeUpdate();

                // 生成されたIDを取得してセット
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        roi.setId(generatedKeys.getInt(1));
                    }
                }
            } catch (SQLException e) {

            }
        }
    }

    /**
     * 指定された画像のROIを削除
     */
    public void deleteROIs(String sopInstanceUID, int frame) {
        String sql = "DELETE FROM ROI WHERE sopInstanceUID = ? AND frame = ?";
        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            pstmt.setString(1, sopInstanceUID);
            pstmt.setInt(2, frame);
            pstmt.executeUpdate();
        } catch (SQLException e) {

        }
    }

    /**
     * ROIを削除 (現在、簡略化のため指定のSOPの全ROIを削除)
     */
    public void deleteROI(ROI roi) {
        if (roi == null || roi.getSOPInstanceUID() == null)
            return;
        // 本来はROI固有のIDで削除すべきだが、現在のスキーマに合わせてSOP UIDで削除
        // ROI自体にframe番号を保持させるか、呼び出し側で指定する必要がある
        deleteROIs(roi.getSOPInstanceUID(), 0); // とりあえずフレーム0
    }

    /**
     * 画像のすべてのROIを読み込む
     */
    public void loadROIsForImage(DicomImage image) {
        if (image == null || image.getSopInstanceUID() == null)
            return;

        String sql = "SELECT * FROM ROI WHERE sopInstanceUID = ?";
        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            pstmt.setString(1, image.getSopInstanceUID());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int frame = rs.getInt("frame");
                    String typeStr = rs.getString("type");
                    String pointsStr = rs.getString("points");
                    String name = rs.getString("name");
                    int colorRGB = rs.getInt("color");
                    double thickness = rs.getDouble("thickness");
                    double psX = rs.getDouble("pixelSpacingX");
                    double psY = rs.getDouble("pixelSpacingY");
                    float offX = rs.getFloat("textBoxOffsetX");
                    float offY = rs.getFloat("textBoxOffsetY");

                    ROI roi = new ROI();
                    roi.setId(id);
                    try {
                        roi.setType(com.oculus.jj.ui.DCMView.ToolMode.valueOf(typeStr));
                    } catch (Exception e) {
                        roi.setType(com.oculus.jj.ui.DCMView.ToolMode.tMesure);
                    }
                    roi.setPoints(deserializePoints(pointsStr));
                    roi.setName(name);
                    roi.setColor(new java.awt.Color(colorRGB));
                    roi.setThickness((float) thickness);
                    roi.setPixelSpacingX(psX);
                    roi.setPixelSpacingY(psY);
                    roi.setTextBoxOffset(offX, offY);
                    roi.setSOPInstanceUID(image.getSopInstanceUID());

                    image.addROI(frame, roi);
                }
            }
        } catch (SQLException e) {

        }
    }

    private String serializePoints(java.util.List<java.awt.geom.Point2D.Float> points) {
        StringBuilder sb = new StringBuilder();
        for (java.awt.geom.Point2D.Float p : points) {
            sb.append(p.x).append(",").append(p.y).append(";");
        }
        return sb.toString();
    }

    private java.util.List<java.awt.geom.Point2D.Float> deserializePoints(String s) {
        java.util.List<java.awt.geom.Point2D.Float> points = new java.util.ArrayList<>();
        if (s == null || s.isEmpty())
            return points;
        String[] pairs = s.split(";");
        for (String pair : pairs) {
            String[] coords = pair.split(",");
            if (coords.length == 2) {
                try {
                    points.add(
                            new java.awt.geom.Point2D.Float(Float.parseFloat(coords[0]), Float.parseFloat(coords[1])));
                } catch (NumberFormatException e) {
                    // Ignore malformed points
                }
            }
        }
        return points;
    }

    /**
     * SeriesのImageを読み込む
     */
    private void loadImagesForSeries(DicomSeries series, int seriesDbId) throws SQLException {
        String sql = "SELECT * FROM Image WHERE seriesId = ? ORDER BY instanceNumber";
        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            pstmt.setInt(1, seriesDbId);
            try (ResultSet rs = pstmt.executeQuery()) {
                java.util.Set<DicomImage> imageSet = new java.util.HashSet<>();

                while (rs.next()) {
                    String sopUID = rs.getString("sopInstanceUID");
                    int frameID = rs.getInt("frameID");

                    // HOROS-20240407準拠: 既存のImageオブジェクトがあれば再利用する
                    // これによりROIなどのインメモリ情報が保持される
                    DicomImage image = series.getImageBySOPUID(sopUID, frameID);

                    if (image == null) {
                        image = new DicomImage();
                        image.setSopInstanceUID(sopUID);
                        image.setFrameID(frameID);
                    }

                    // HOROS-20240407準拠: pathNumberとpathStringの両方を読み込む
                    try {
                        Integer pathNumber = rs.getObject("pathNumber", Integer.class);
                        if (pathNumber != null) {
                            image.setPathNumber(pathNumber);
                        }
                    } catch (SQLException e) {
                        // pathNumberカラムが存在しない場合は無視（既存DBの互換性）
                    }
                    image.setPathString(rs.getString("pathString"));
                    // HOROS-20240407準拠差分: DBに保存済みの完全パスを優先復元
                    // pathNumber/pathString だけでは再構築できないケースでサムネイル消失を防ぐ
                    try {
                        String dbCompletePath = rs.getString("completePath");
                        if (dbCompletePath != null && !dbCompletePath.isEmpty()) {
                            image.setCompletePathCache(dbCompletePath);
                        }
                    } catch (SQLException e) {
                        // completePathカラムが存在しない既存DBは無視（互換性維持）
                    }
                    // HOROS-20240407準拠: inDatabaseFolderも読み込む
                    try {
                        Boolean inDatabaseFolder = rs.getBoolean("inDatabaseFolder");
                        image.setInDatabaseFolder(inDatabaseFolder);
                    } catch (SQLException e) {
                        // inDatabaseFolderカラムが存在しない場合はデフォルト値（true）を使用
                        image.setInDatabaseFolder(true);
                    }
                    image.setInstanceNumber(rs.getInt("instanceNumber"));
                    image.setNumberOfFrames(rs.getInt("numberOfFrames"));
                    image.setModality(rs.getString("modality"));
                    image.setFileType(rs.getString("fileType"));
                    image.setHeight(rs.getInt("height"));
                    image.setWidth(rs.getInt("width"));
                    image.setSliceLocation((double) rs.getFloat("sliceLocation"));

                    // ROIを読み込む（新規作成時のみ、または既にロードされているか確認）
                    if (image.getROIs(frameID).isEmpty()) {
                        loadROIsForImage(image);
                    }

                    image.setSeries(series);

                    String dateStr = rs.getString("date");
                    if (dateStr != null && !dateStr.isEmpty()) {
                        try {
                            image.setDate(LocalDateTime.parse(dateStr));
                        } catch (Exception e) {
                            // 日付パースエラーは無視
                        }
                    }

                    imageSet.add(image);
                    series.addImagesObject(image);

                    // ファイルパスマッピングに追加
                    String completePath = image.getCompletePath();
                    if (completePath != null && !completePath.isEmpty()) {
                        synchronized (filePathToImageMap) {
                            filePathToImageMap.put(completePath, image);
                        }
                    }
                }

                series.setImages(imageSet);
            }
        }
    }

    /**
     * データベースに保存
     * HOROS-20240407準拠: - (BOOL)save:(NSError**)error
     * 最適化: バッチ処理でトランザクションを使用して高速化
     */
    public boolean save() {
        synchronized (studies) {
            return save(studies);
        }
    }

    private volatile String lastSaveError = "";

    /**
     * 指定されたStudyリストをデータベースに保存
     */
    public boolean save(java.util.Collection<DicomStudy> studiesToSave) {
        return save(studiesToSave, true);
    }

    private boolean save(java.util.Collection<DicomStudy> studiesToSave, boolean includeAlbums) {
        if (sqliteConnection == null) {
            lastSaveError = "sqliteConnection is null";
            return false;
        }

        synchronized (sqliteConnection) {
            lastSaveError = "";
            if (studiesToSave != null && !studiesToSave.isEmpty()) {
                try {
                    sqliteConnection.setAutoCommit(false);
                    for (DicomStudy study : studiesToSave) {
                        saveStudy(study);
                    }
                    sqliteConnection.commit();
                    sqliteConnection.setAutoCommit(true);
                } catch (SQLException e) {
                    lastSaveError = "saveStudy: " + e.getMessage();
                    logger.error("Failed to save studies to SQLite: {}", e.getMessage(), e);
                    try {
                        sqliteConnection.rollback();
                        sqliteConnection.setAutoCommit(true);
                    } catch (SQLException ignored) {
                    }
                    return false;
                }
            }
            return includeAlbums ? saveAlbumsToDatabase() : true;
        }
    }

    private boolean saveAlbumsToDatabase() {
        try {
            sqliteConnection.setAutoCommit(false);
            java.util.Set<Integer> retainedAlbumIds = new java.util.HashSet<>();
            synchronized (albums) {
                for (DicomAlbum album : albums) {
                    if (album.getName() == null || album.getName().trim().isEmpty()) continue;
                    Integer albumId = album.getIndex();
                    if (albumId == null) {
                        try (PreparedStatement find = sqliteConnection.prepareStatement(
                                "SELECT id FROM Album WHERE name = ?")) {
                            find.setString(1, album.getName());
                            try (ResultSet rs = find.executeQuery()) {
                                if (rs.next()) albumId = rs.getInt(1);
                            }
                        }
                    }

                    boolean smart = Boolean.TRUE.equals(album.isSmartAlbum());
                    if (albumId == null) {
                        try (PreparedStatement insert = sqliteConnection.prepareStatement(
                                "INSERT INTO Album (name, smartAlbum, predicate, dateAdded) VALUES (?, ?, ?, ?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                            insert.setString(1, album.getName());
                            insert.setInt(2, smart ? 1 : 0);
                            insert.setString(3, smart ? album.getPredicateString() : null);
                            insert.setString(4, LocalDateTime.now().toString());
                            insert.executeUpdate();
                            try (ResultSet keys = insert.getGeneratedKeys()) {
                                if (keys.next()) albumId = keys.getInt(1);
                            }
                        }
                    } else {
                        try (PreparedStatement update = sqliteConnection.prepareStatement(
                                "UPDATE Album SET name = ?, smartAlbum = ?, predicate = ? WHERE id = ?")) {
                            update.setString(1, album.getName());
                            update.setInt(2, smart ? 1 : 0);
                            update.setString(3, smart ? album.getPredicateString() : null);
                            update.setInt(4, albumId);
                            update.executeUpdate();
                        }
                    }
                    if (albumId == null) throw new SQLException("Could not assign album database ID");
                    album.setIndex(albumId);
                    retainedAlbumIds.add(albumId);

                    try (PreparedStatement delete = sqliteConnection.prepareStatement(
                            "DELETE FROM AlbumStudy WHERE albumId = ?")) {
                        delete.setInt(1, albumId);
                        delete.executeUpdate();
                    }
                    if (!smart) {
                        try (PreparedStatement link = sqliteConnection.prepareStatement(
                                "INSERT OR IGNORE INTO AlbumStudy (albumId, studyId) VALUES (?, ?)")) {
                            for (DicomStudy study : album.getStudies()) {
                                if (study.getStudyInstanceUID() == null) continue;
                                int studyId = getStudyDbId(study.getStudyInstanceUID());
                                if (studyId < 0) continue;
                                link.setInt(1, albumId);
                                link.setInt(2, studyId);
                                link.addBatch();
                            }
                            link.executeBatch();
                        }
                    }
                }
            }

            List<Integer> staleAlbumIds = new ArrayList<>();
            try (Statement query = sqliteConnection.createStatement();
                    ResultSet rs = query.executeQuery("SELECT id FROM Album")) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    if (!retainedAlbumIds.contains(id)) staleAlbumIds.add(id);
                }
            }
            try (PreparedStatement deleteLinks = sqliteConnection.prepareStatement(
                    "DELETE FROM AlbumStudy WHERE albumId = ?");
                    PreparedStatement deleteAlbum = sqliteConnection.prepareStatement(
                            "DELETE FROM Album WHERE id = ?")) {
                for (Integer id : staleAlbumIds) {
                    deleteLinks.setInt(1, id);
                    deleteLinks.addBatch();
                    deleteAlbum.setInt(1, id);
                    deleteAlbum.addBatch();
                }
                deleteLinks.executeBatch();
                deleteAlbum.executeBatch();
            }
            sqliteConnection.commit();
            sqliteConnection.setAutoCommit(true);
            return true;
        } catch (SQLException e) {
            lastSaveError = "saveAlbums: " + e.getMessage();
            logger.error("Failed to save albums to SQLite: {}", e.getMessage(), e);
            try {
                sqliteConnection.rollback();
                sqliteConnection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
            return false;
        }
    }

    /**
     * Studyを保存
     */
    private void saveStudy(DicomStudy study) throws SQLException {
        // HOROS-20240407準拠: dateAddedがnullの場合は現在の日時を設定
        if (study.getDateAdded() == null) {
            study.setDateAdded(java.time.LocalDateTime.now());
        }

        // System.out.println(">>> SQL Save Study (Write) [" + sqliteDatabasePath + "]
        // Study: " + study.getStudyInstanceUID());

        // 既存のIDを確認（外部キー整合性維持のため重要）
        int studyDbId = getStudyDbId(study.getStudyInstanceUID());

        // StudyをINSERT OR REPLACE (既存IDがある場合はそれを使用)
        String sql;
        if (studyDbId != -1) {
            sql = """
                        INSERT OR REPLACE INTO Study
                        (id, studyInstanceUID, name, patientID, patientUID, patientBirthDate, patientSex,
                         studyName, modality, date, dateAdded,
                         numberOfImages, comment, accessionNumber, referringPhysician,
                         performingPhysician, institutionName, studyID, sourceNodeName, hidden)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        } else {
            sql = """
                        INSERT OR REPLACE INTO Study
                        (studyInstanceUID, name, patientID, patientUID, patientBirthDate, patientSex,
                         studyName, modality, date, dateAdded,
                         numberOfImages, comment, accessionNumber, referringPhysician,
                         performingPhysician, institutionName, studyID, sourceNodeName, hidden)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        }

        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            int idx = 1;
            if (studyDbId != -1) {
                pstmt.setInt(idx++, studyDbId);
            }
            pstmt.setString(idx++, study.getStudyInstanceUID());
            pstmt.setString(idx++, study.getName());
            pstmt.setString(idx++, study.getPatientID());
            pstmt.setString(idx++, study.getPatientUID());
            pstmt.setString(idx++, study.getDateOfBirth() != null ? study.getDateOfBirth().toString() : null);
            pstmt.setString(idx++, study.getPatientSex());
            pstmt.setString(idx++, study.getStudyName());
            pstmt.setString(idx++, study.getModality());
            pstmt.setString(idx++, study.getDate() != null ? study.getDate().toString() : null);
            pstmt.setString(idx++, study.getDateAdded() != null ? study.getDateAdded().toString() : null);
            pstmt.setInt(idx++, study.getNumberOfImages() != null ? study.getNumberOfImages() : 0);
            pstmt.setString(idx++, study.getComment());
            pstmt.setString(idx++, study.getAccessionNumber());
            pstmt.setString(idx++, study.getReferringPhysician());
            pstmt.setString(idx++, study.getPerformingPhysician());
            pstmt.setString(idx++, study.getInstitutionName());
            pstmt.setString(idx++, study.getStudyID());
            pstmt.setString(idx++, study.getSourceNodeName());
            pstmt.setInt(idx++, study.isHidden() ? 1 : 0);

            pstmt.executeUpdate();

            // 新規作成された場合はIDを再取得
            if (studyDbId == -1) {
                studyDbId = getStudyDbId(study.getStudyInstanceUID());
            }

            // Seriesを保存
            java.util.Set<DicomSeries> seriesSet = study.getSeries();
            if (seriesSet != null) {
                for (DicomSeries series : seriesSet) {
                    saveSeries(series, studyDbId);
                }
            }
        }
    }

    /**
     * StudyのDBIDを取得
     */
    private int getStudyDbId(String studyInstanceUID) throws SQLException {
        String sql = "SELECT id FROM Study WHERE studyInstanceUID = ?";
        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            pstmt.setString(1, studyInstanceUID);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    /**
     * Seriesを保存
     */
    private void saveSeries(DicomSeries series, int studyDbId) throws SQLException {
        // HOROS-20240407準拠: dateAddedがnullの場合は現在の日時を設定（2080行目、2192行目）
        if (series.getDateAdded() == null) {
            series.setDateAdded(java.time.LocalDateTime.now());
        }

        // System.out.println(">>> SQL Save Series (Write) [" + sqliteDatabasePath + "]
        // Series: " + series.getSeriesInstanceUID());

        // 既存のIDを確認
        int seriesDbId = getSeriesDbId(series.getSeriesInstanceUID());

        String sql;
        if (seriesDbId != -1) {
            sql = """
                        INSERT OR REPLACE INTO Series
                        (id, seriesInstanceUID, studyId, name, seriesDescription, modality, date, dateAdded,
                         numberOfImages, seriesNumber, seriesSOPClassUID, windowLevel, windowWidth)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        } else {
            sql = """
                        INSERT OR REPLACE INTO Series
                        (seriesInstanceUID, studyId, name, seriesDescription, modality, date, dateAdded,
                         numberOfImages, seriesNumber, seriesSOPClassUID, windowLevel, windowWidth)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        }

        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            int idx = 1;
            if (seriesDbId != -1) {
                pstmt.setInt(idx++, seriesDbId);
            }
            pstmt.setString(idx++, series.getSeriesInstanceUID());
            pstmt.setInt(idx++, studyDbId);
            pstmt.setString(idx++, series.getName());
            pstmt.setString(idx++, series.getSeriesDescription());
            pstmt.setString(idx++, series.getModality());
            pstmt.setString(idx++, series.getDate() != null ? series.getDate().toString() : null);
            pstmt.setString(idx++, series.getDateAdded() != null ? series.getDateAdded().toString() : null);
            pstmt.setInt(idx++, series.getNumberOfImages() != null ? series.getNumberOfImages() : 0);
            pstmt.setInt(idx++, series.getSeriesNumber() != null ? series.getSeriesNumber() : 0);
            pstmt.setString(idx++, series.getSeriesSOPClassUID());
            pstmt.setFloat(idx++, series.getWindowLevel() != null ? series.getWindowLevel().floatValue() : 0);
            pstmt.setFloat(idx++, series.getWindowWidth() != null ? series.getWindowWidth().floatValue() : 0);

            pstmt.executeUpdate();

            // 新規作成された場合はIDを再取得
            if (seriesDbId == -1) {
                seriesDbId = getSeriesDbId(series.getSeriesInstanceUID());
            }

            // Imageを保存
            java.util.Set<DicomImage> imageSet = series.getImages();
            if (imageSet != null) {
                for (DicomImage image : imageSet) {
                    saveImage(image, seriesDbId);
                }
            }
        }
    }

    public int getSeriesDbId(String seriesInstanceUID) throws SQLException {
        String sql = "SELECT id FROM Series WHERE seriesInstanceUID = ?";
        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            pstmt.setString(1, seriesInstanceUID);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    /**
     * Imageを保存
     */
    public void saveImage(DicomImage image, int seriesDbId) throws SQLException {
        String sql = """
                    INSERT OR REPLACE INTO Image
                    (sopInstanceUID, seriesId, pathNumber, pathString, completePath, inDatabaseFolder, instanceNumber,
                     numberOfFrames, frameID, date, modality, fileType, height, width, sliceThickness, spacingBetweenSlices, pixelSpacingX, pixelSpacingY, sliceLocation, patientName, studyDate, seriesDate, acquisitionDate, manufacturerModelString, deviceSerialNumber, kvp, ma, tr, te, dynamicRange, frequency)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        // System.out.println(">>> SQL Save Image (Write) [" + sqliteDatabasePath + "]
        // SOP: " + image.getSopInstanceUID());

        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            pstmt.setString(1, image.getSopInstanceUID());
            pstmt.setInt(2, seriesDbId);
            // HOROS-20240407準拠: pathNumberとpathStringの両方を保存
            pstmt.setObject(3, image.getPathNumber());
            pstmt.setString(4, image.getPathString());
            pstmt.setString(5, image.getCompletePath());
            // HOROS-20240407準拠: inDatabaseFolderも保存
            pstmt.setBoolean(6, image.getInDatabaseFolder() != null && image.getInDatabaseFolder());
            pstmt.setInt(7, image.getInstanceNumber() != null ? image.getInstanceNumber() : 0);
            pstmt.setInt(8, image.getNumberOfFrames() != null ? image.getNumberOfFrames() : 1);
            pstmt.setInt(9, image.getFrameID() != null ? image.getFrameID() : 0);
            pstmt.setString(10, image.getDate() != null ? image.getDate().toString() : null);
            pstmt.setString(11, image.getModality());
            pstmt.setString(12, image.getFileType());
            pstmt.setInt(13, image.getHeight() != null ? image.getHeight() : 0);
            pstmt.setInt(14, image.getWidth() != null ? image.getWidth() : 0);
            pstmt.setDouble(15, image.getSliceThickness() != null ? image.getSliceThickness() : 0.0);
            pstmt.setDouble(16, image.getSpacingBetweenSlices() != null ? image.getSpacingBetweenSlices() : 0.0);
            pstmt.setDouble(17, image.getPixelSpacingX() != null ? image.getPixelSpacingX() : 0.0);
            pstmt.setDouble(18, image.getPixelSpacingY() != null ? image.getPixelSpacingY() : 0.0);
            pstmt.setDouble(19, image.getSliceLocation() != null ? image.getSliceLocation() : 0.0);
            pstmt.setString(20, image.getPatientName());
            pstmt.setString(21, image.getStudyDate() != null ? image.getStudyDate().toString() : null);
            pstmt.setString(22, image.getSeriesDate() != null ? image.getSeriesDate().toString() : null);
            pstmt.setString(23, image.getAcquisitionDate() != null ? image.getAcquisitionDate().toString() : null);
            pstmt.setString(24, image.getManufacturerModelString());
            pstmt.setString(25, image.getDeviceSerialNumber());
            pstmt.setDouble(26, image.getKvp() != null ? image.getKvp() : 0.0);
            pstmt.setDouble(27, image.getMa() != null ? image.getMa() : 0.0);
            pstmt.setDouble(28, image.getTr() != null ? image.getTr() : 0.0);
            pstmt.setDouble(29, image.getTe() != null ? image.getTe() : 0.0);
            pstmt.setString(30, image.getDynamicRange());
            pstmt.setString(31, image.getFrequency());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            // System.err.println("!!! SQL Write Error (Image) [" + sqliteDatabasePath + "]:
            // " + e.getMessage());
            throw e;
        }
    }

    /**
     * データベース接続を閉じる
     */
    public void close() {
        if (sqliteConnection != null) {
            try {
                // 保存してから閉じる
                save();
                sqliteConnection.close();

            } catch (SQLException e) {

            }
        }
    }

    // ========== パス関連メソッド ==========

    /**
     * モデルバージョンファイルパスを取得
     */
    public String modelVersionFilePath() {
        return baseDirPath + File.separator + "ModelVersion";
    }

    /**
     * ローディングファイルパスを取得
     */
    public String loadingFilePath() {
        return baseDirPath + File.separator + "Loading";
    }

    /**
     * データディレクトリパスを取得
     */
    public String dataDirPath() {
        return getDataDirPath();
    }

    /**
     * データディレクトリパスを取得
     * HOROS-20240407準拠: - (NSString*)dataDirPath (DicomDatabase.mm 763-765行目)
     * return [[self.dataBaseDirPath
     * stringByAppendingPathComponent:@"DATABASE.noindex"]
     * stringByResolvingSymlinksAndAliases];
     */
    public String getDataDirPath() {
        // HOROS-20240407準拠: dataBaseDirPath/DATABASE.noindex
        return dataBaseDirPath + File.separator + "DATABASE.noindex";
    }

    /**
     * 受信ディレクトリパスを取得
     */
    public String incomingDirPath() {
        return dataBaseDirPath + File.separator + "INCOMING";
    }

    /**
     * エラーディレクトリパスを取得
     */
    public String errorsDirPath() {
        return dataBaseDirPath + File.separator + "ERRORS";
    }

    /**
     * 解凍ディレクトリパスを取得
     */
    public String decompressionDirPath() {
        return dataBaseDirPath + File.separator + "DECOMPRESSION";
    }

    /**
     * インデックス待ちディレクトリパスを取得
     */
    public String toBeIndexedDirPath() {
        return dataBaseDirPath + File.separator + "TO_BE_INDEXED";
    }

    /**
     * レポートディレクトリパスを取得
     */
    public String reportsDirPath() {
        return dataBaseDirPath + File.separator + "REPORTS";
    }

    /**
     * 一時ディレクトリパスを取得
     */
    public String tempDirPath() {
        return dataBaseDirPath + File.separator + "TEMP";
    }

    /**
     * ダンプディレクトリパスを取得
     */
    public String dumpDirPath() {
        return dataBaseDirPath + File.separator + "DUMP";
    }

    /**
     * ページディレクトリパスを取得
     */
    public String pagesDirPath() {
        return dataBaseDirPath + File.separator + "PAGES";
    }

    /**
     * HTMLテンプレートディレクトリパスを取得
     */
    public String htmlTemplatesDirPath() {
        return dataBaseDirPath + File.separator + "HTML_TEMPLATES";
    }

    /**
     * ステートディレクトリパスを取得
     */
    public String statesDirPath() {
        return dataBaseDirPath + File.separator + "STATES";
    }

    /**
     * CLUTディレクトリパスを取得
     */
    public String clutsDirPath() {
        return dataBaseDirPath + File.separator + "CLUTS";
    }

    /**
     * プリセットディレクトリパスを取得
     */
    public String presetsDirPath() {
        return dataBaseDirPath + File.separator + "PRESETS";
    }

    /**
     * ベースディレクトリパス（C文字列）を取得
     */
    public String baseDirPathC() {
        return baseDirPath;
    }

    /**
     * 受信ディレクトリパス（C文字列）を取得
     */
    public String incomingDirPathC() {
        return incomingDirPath();
    }

    /**
     * 一時ディレクトリパス（C文字列）を取得
     */
    public String tempDirPathC() {
        return tempDirPath();
    }

    /**
     * データファイルインデックスを計算
     */
    public int computeDataFileIndex() {
        return ++dataFileIndex;
    }

    /**
     * 新しいデータファイルの一意パスを取得
     */
    public String uniquePathForNewDataFileWithExtension(String ext) {
        return dataDirPath() + File.separator + "file_" + computeDataFileIndex() + "." + ext;
    }

    // ========== アルバム関連メソッド ==========

    /**
     * デフォルトアルバムを追加
     */
    public void addDefaultAlbums() {
        String[][] defaults = {
                { "Today", "date >= $TODAY" },
                { "Yesterday", "date >= $YESTERDAY AND date < $TODAY" },
                { "Last Week", "date >= $LASTWEEK" }
        };
        synchronized (albums) {
            for (String[] descriptor : defaults) {
                boolean exists = false;
                for (DicomAlbum album : albums) {
                    if (descriptor[0].equalsIgnoreCase(album.getName())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) albums.add(new DicomAlbum(descriptor[0], descriptor[1]));
            }
        }
        if (!save()) throw new IllegalStateException("Failed to save default albums");
    }

    /**
     * アルバム配列を取得
     */
    public List<Object> albums() {
        List<DicomAlbum> sorted = getAlbums();
        sorted.sort(DicomAlbum::compareTo);
        return new ArrayList<>(sorted);
    }

    /**
     * スマートアルバムフィルターの述語を作成
     */
    public static Object predicateForSmartAlbumFilter(String string) {
        AlbumRuleEngine.RuleSet rules = AlbumRuleEngine.parsePredicate(string);
        return (java.util.function.Predicate<DicomStudy>) rules::evaluate;
    }

    /**
     * アルバムをパスに保存
     */
    public void saveAlbumsToPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Album archive path is empty");
        }
        if (!save()) {
            throw new IllegalStateException("Failed to save database before exporting albums");
        }

        List<Map<String, Object>> archive = new ArrayList<>();
        synchronized (albums) {
            for (DicomAlbum album : albums) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("name", album.getName());
                boolean smart = Boolean.TRUE.equals(album.isSmartAlbum());
                entry.put("smartAlbum", smart);
                if (smart) {
                    entry.put("predicateString", album.getPredicateString());
                } else {
                    List<String> studyUIDs = new ArrayList<>();
                    for (DicomStudy study : album.getStudies()) {
                        if (study.getStudyInstanceUID() != null) {
                            studyUIDs.add(study.getStudyInstanceUID());
                        }
                    }
                    entry.put("studies", studyUIDs);
                }
                archive.add(entry);
            }
        }

        java.nio.file.Path target = java.nio.file.Paths.get(path).toAbsolutePath();
        java.nio.file.Path parent = target.getParent();
        java.nio.file.Path temp = null;
        try {
            if (parent != null) java.nio.file.Files.createDirectories(parent);
            temp = java.nio.file.Files.createTempFile(parent, "albums-", ".tmp");
            javax.xml.stream.XMLStreamWriter xml = javax.xml.stream.XMLOutputFactory.newFactory()
                    .createXMLStreamWriter(java.nio.file.Files.newOutputStream(temp), "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("plist");
            xml.writeAttribute("version", "1.0");
            xml.writeStartElement("array");
            for (Map<String, Object> entry : archive) {
                xml.writeStartElement("dict");
                writePlistString(xml, "name", String.valueOf(entry.get("name")));
                xml.writeStartElement("key"); xml.writeCharacters("smartAlbum"); xml.writeEndElement();
                xml.writeEmptyElement(Boolean.TRUE.equals(entry.get("smartAlbum")) ? "true" : "false");
                if (Boolean.TRUE.equals(entry.get("smartAlbum"))) {
                    writePlistString(xml, "predicateString", (String) entry.get("predicateString"));
                } else {
                    xml.writeStartElement("key"); xml.writeCharacters("studies"); xml.writeEndElement();
                    xml.writeStartElement("array");
                    @SuppressWarnings("unchecked")
                    List<String> studyUIDs = (List<String>) entry.get("studies");
                    for (String uid : studyUIDs) {
                        xml.writeStartElement("dict");
                        writePlistString(xml, "studyInstanceUID", uid);
                        xml.writeEndElement();
                    }
                    xml.writeEndElement();
                }
                xml.writeEndElement();
            }
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
            try {
                java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to export albums to " + target, ex);
        } finally {
            if (temp != null) {
                try { java.nio.file.Files.deleteIfExists(temp); } catch (java.io.IOException ignored) { }
            }
        }
    }

    /**
     * アルバムをパスから読み込み
     */
    public void loadAlbumsFromPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Album archive path is empty");
        }
        java.io.File file = new java.io.File(path);
        if (!file.isFile()) {
            throw new IllegalArgumentException("Album archive does not exist: " + path);
        }

        List<Map<String, Object>> imported = new ArrayList<>();
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(file);
            com.fasterxml.jackson.databind.JsonNode entries = root.isArray() ? root : root.path("albums");
            if (entries.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : entries) {
                    if (!node.hasNonNull("name") || node.path("name").asText().trim().isEmpty()) continue;
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("name", node.path("name").asText().trim());
                    entry.put("smartAlbum", node.path("smartAlbum").asBoolean(false));
                    if (node.hasNonNull("predicateString")) {
                        entry.put("predicateString", node.path("predicateString").asText());
                    }
                    List<String> studyUIDs = new ArrayList<>();
                    if (node.path("studies").isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode study : node.path("studies")) {
                            String uid = study.isTextual() ? study.asText() : study.path("studyInstanceUID").asText();
                            if (!uid.isEmpty()) studyUIDs.add(uid);
                        }
                    }
                    entry.put("studies", studyUIDs);
                    imported.add(entry);
                }
            }
        } catch (Exception jsonException) {
            try {
                readPlistAlbums(file, imported);
            } catch (Exception plistException) {
                // Legacy OculusJJ archive: one album per line, optional name|predicate.
                try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(file.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int separator = line.indexOf('|');
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("name", (separator >= 0 ? line.substring(0, separator) : line).trim());
                        entry.put("smartAlbum", separator >= 0 && separator < line.length() - 1);
                        if (separator >= 0) entry.put("predicateString", line.substring(separator + 1).trim());
                        entry.put("studies", new ArrayList<String>());
                        if (!((String) entry.get("name")).isEmpty()) imported.add(entry);
                    }
                } catch (java.io.IOException ex) {
                    throw new IllegalArgumentException("Unsupported album archive: " + path, jsonException);
                }
            }
        }

        boolean changed = false;
        synchronized (albums) {
            for (Map<String, Object> entry : imported) {
                String name = (String) entry.get("name");
                DicomAlbum album = null;
                for (DicomAlbum existing : albums) {
                    if (existing.getName() != null && existing.getName().equalsIgnoreCase(name)) {
                        album = existing;
                        break;
                    }
                }
                boolean smart = Boolean.TRUE.equals(entry.get("smartAlbum"));
                if (album == null) {
                    album = smart
                            ? new DicomAlbum(name, (String) entry.get("predicateString"))
                            : new DicomAlbum(name);
                    albums.add(album);
                    changed = true;
                }
                if (!smart) {
                    @SuppressWarnings("unchecked")
                    List<String> studyUIDs = (List<String>) entry.get("studies");
                    for (String uid : studyUIDs) {
                        DicomStudy study = getStudyByUID(uid);
                        if (study != null && album.getStudies().add(study)) changed = true;
                    }
                }
            }
        }
        if (changed && !save()) {
            throw new IllegalStateException("Failed to save imported albums");
        }
    }

    private static void writePlistString(javax.xml.stream.XMLStreamWriter xml, String key, String value)
            throws javax.xml.stream.XMLStreamException {
        if (value == null) return;
        xml.writeStartElement("key"); xml.writeCharacters(key); xml.writeEndElement();
        xml.writeStartElement("string"); xml.writeCharacters(value); xml.writeEndElement();
    }

    private static void readPlistAlbums(java.io.File file, List<Map<String, Object>> imported) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
        org.w3c.dom.Document document = builder.parse(file);
        org.w3c.dom.NodeList arrays = document.getElementsByTagName("array");
        if (arrays.getLength() == 0) throw new IllegalArgumentException("No album array in plist");
        org.w3c.dom.Element rootArray = (org.w3c.dom.Element) arrays.item(0);
        for (org.w3c.dom.Node node = rootArray.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof org.w3c.dom.Element) || !"dict".equals(node.getNodeName())) continue;
            Map<String, Object> entry = new HashMap<>();
            List<String> studyUIDs = new ArrayList<>();
            org.w3c.dom.Node child = node.getFirstChild();
            while (child != null) {
                if (child instanceof org.w3c.dom.Element && "key".equals(child.getNodeName())) {
                    String key = child.getTextContent();
                    org.w3c.dom.Node value = child.getNextSibling();
                    while (value != null && !(value instanceof org.w3c.dom.Element)) value = value.getNextSibling();
                    if (value == null) break;
                    if ("name".equals(key) && "string".equals(value.getNodeName())) {
                        entry.put("name", value.getTextContent().trim());
                    } else if ("smartAlbum".equals(key)) {
                        entry.put("smartAlbum", "true".equals(value.getNodeName()));
                    } else if ("predicateString".equals(key) && "string".equals(value.getNodeName())) {
                        entry.put("predicateString", value.getTextContent());
                    } else if ("studies".equals(key) && "array".equals(value.getNodeName())) {
                        readPlistStudyUIDs((org.w3c.dom.Element) value, studyUIDs);
                    }
                    child = value.getNextSibling();
                } else {
                    child = child.getNextSibling();
                }
            }
            if (entry.get("name") instanceof String && !((String) entry.get("name")).isEmpty()) {
                entry.putIfAbsent("smartAlbum", false);
                entry.put("studies", studyUIDs);
                imported.add(entry);
            }
        }
        if (imported.isEmpty()) throw new IllegalArgumentException("No albums in plist");
    }

    private static void readPlistStudyUIDs(org.w3c.dom.Element array, List<String> studyUIDs) {
        for (org.w3c.dom.Node node = array.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof org.w3c.dom.Element)) continue;
            if ("string".equals(node.getNodeName())) {
                studyUIDs.add(node.getTextContent());
            } else if ("dict".equals(node.getNodeName())) {
                org.w3c.dom.Node child = node.getFirstChild();
                while (child != null) {
                    if (child instanceof org.w3c.dom.Element && "key".equals(child.getNodeName())
                            && "studyInstanceUID".equals(child.getTextContent())) {
                        org.w3c.dom.Node value = child.getNextSibling();
                        while (value != null && !(value instanceof org.w3c.dom.Element)) value = value.getNextSibling();
                        if (value != null && "string".equals(value.getNodeName())) studyUIDs.add(value.getTextContent());
                        break;
                    }
                    child = child.getNextSibling();
                }
            }
        }
    }

    /**
     * スタディをアルバムに追加
     */
    public void addStudies(List<Object> dicomStudies, Object dicomAlbum) {
        if (dicomStudies == null || dicomStudies.isEmpty() || !(dicomAlbum instanceof DicomAlbum)) return;
        DicomAlbum target = (DicomAlbum) dicomAlbum;
        List<DicomStudy> selectedStudies = new ArrayList<>();
        for (Object item : dicomStudies) {
            DicomStudy study = null;
            if (item instanceof DicomStudy) {
                study = (DicomStudy) item;
            } else if (item instanceof DicomSeries) {
                study = ((DicomSeries) item).getStudy();
            } else if (item instanceof DicomImage) {
                DicomSeries series = ((DicomImage) item).getSeries();
                if (series != null) study = series.getStudy();
            }
            if (study != null && !selectedStudies.contains(study)) selectedStudies.add(study);
        }
        if (selectedStudies.isEmpty()) return;

        boolean albumExists;
        synchronized (albums) {
            albumExists = albums.contains(target);
            if (!albumExists) {
                for (DicomAlbum album : albums) {
                    if (album.getName() != null && album.getName().equals(target.getName())) {
                        target = album;
                        albumExists = true;
                        break;
                    }
                }
            }
            if (!albumExists) return;
            for (DicomStudy study : selectedStudies) target.addStudiesObject(study);
        }
        if (!save(selectedStudies) || !save()) {
            throw new IllegalStateException("Failed to save album Study membership");
        }
    }

    /**
     * Studyを削除
     * HOROS-20240407準拠
     */
    public boolean deleteStudy(DicomStudy study) {
        if (study == null || sqliteConnection == null) {
            return false;
        }
        try {
            sqliteConnection.setAutoCommit(false);
            try {
                int studyDbId = getStudyDbId(study.getStudyInstanceUID());
                if (studyDbId > 0) {
                    try (PreparedStatement pstmt = sqliteConnection.prepareStatement(
                            "DELETE FROM AlbumStudy WHERE studyId = ?")) {
                        pstmt.setInt(1, studyDbId);
                        pstmt.executeUpdate();
                    }
                    try (PreparedStatement pstmt = sqliteConnection.prepareStatement(
                            "DELETE FROM Image WHERE seriesId IN (SELECT id FROM Series WHERE studyId = ?)")) {
                        pstmt.setInt(1, studyDbId);
                        pstmt.executeUpdate();
                    }
                    try (PreparedStatement pstmt = sqliteConnection.prepareStatement("DELETE FROM Series WHERE studyId = ?")) {
                        pstmt.setInt(1, studyDbId);
                        pstmt.executeUpdate();
                    }
                    try (PreparedStatement pstmt = sqliteConnection.prepareStatement("DELETE FROM Study WHERE id = ?")) {
                        pstmt.setInt(1, studyDbId);
                        pstmt.executeUpdate();
                    }
                }
                sqliteConnection.commit();
                synchronized (studies) {
                    studies.remove(study);
                }
                synchronized (albums) {
                    for (DicomAlbum album : albums) album.removeStudiesObject(study);
                }
                return true;
            } catch (SQLException ex) {
                sqliteConnection.rollback();
                throw ex;
            } finally {
                sqliteConnection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.warn("Failed to delete study {}", study.getStudyInstanceUID(), e);
            return false;
        }
    }

    public int runDatabaseAutoCleaning(boolean startup) {
        if (startup && !baseDirPath.equals(defaultBaseDirPath())) return 0;
        boolean deleteEntire = startup && com.oculus.jj.config.AppPreferences.getBool("database.deleteEntireAtStartup", false);
        if (!deleteEntire && !com.oculus.jj.config.AppPreferences.getBool("database.autoCleanEnabled", false)) return 0;

        boolean filterByAcquisition = com.oculus.jj.config.AppPreferences.getBool("database.cleanAcqOlder", false);
        boolean filterByLastOpened = com.oculus.jj.config.AppPreferences.getBool("database.cleanNotOpened", false);
        boolean filterByComments = com.oculus.jj.config.AppPreferences.getBool("database.cleanCommentEnabled", false);
        boolean filterByDiskSpace = com.oculus.jj.config.AppPreferences.getBool("database.cleanDiskSpaceEnabled", true);
        if (!deleteEntire && !filterByAcquisition && !filterByLastOpened && !filterByComments && !filterByDiskSpace) return 0;

        long minimumFreeBytes = diskSpaceThresholdBytes();
        if (!deleteEntire && filterByDiskSpace && getUsableSpaceBytes() >= minimumFreeBytes) return 0;

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime acquisitionCutoff = now.minus(agePeriod("database.cleanAcqAge"));
        java.time.LocalDateTime lastOpenedCutoff = now.minus(agePeriod("database.cleanNotOpenedAge"));
        List<DicomStudy> candidates = new ArrayList<>();
        synchronized (studies) {
            for (DicomStudy study : studies) {
                if (study == null || study.isDistant()) continue;
                if (!deleteEntire && !studyMatchesAutoClean(study, filterByAcquisition, acquisitionCutoff,
                        filterByLastOpened, lastOpenedCutoff, filterByComments)) continue;
                if (!deleteEntire && com.oculus.jj.config.AppPreferences.getBool("database.keepStudiesWithComments", true)
                        && hasStudyComments(study)) continue;
                if (!deleteEntire && com.oculus.jj.config.AppPreferences.getBool("database.keepStudiesInAlbum", true)
                        && belongsToRegularAlbum(study)) continue;
                candidates.add(study);
            }
        }
        candidates.sort(autoCleanComparator(com.oculus.jj.config.AppPreferences.getInt("database.cleanPolicy", 1)));

        int deleted = 0;
        boolean deleteLinkedOriginal = com.oculus.jj.config.AppPreferences.getBool("database.deleteLinkedOriginal", false);
        for (DicomStudy study : candidates) {
            if (!deleteEntire && filterByDiskSpace && getUsableSpaceBytes() >= minimumFreeBytes) break;
            List<java.nio.file.Path> filesToDelete = filesToDeleteForStudy(study, deleteLinkedOriginal);
            if (!deleteStudy(study)) continue;
            deleted++;
            for (java.nio.file.Path path : filesToDelete) {
                try { java.nio.file.Files.deleteIfExists(path); }
                catch (Exception ex) { logger.warn("Could not remove cleaned DICOM file {}", path, ex); }
            }
        }
        if (deleted > 0) notifyDatabaseChanged();
        return deleted;
    }

    private boolean studyMatchesAutoClean(DicomStudy study, boolean byAcquisition,
            java.time.LocalDateTime acquisitionCutoff, boolean byLastOpened,
            java.time.LocalDateTime lastOpenedCutoff, boolean byComments) {
        if (byAcquisition && (study.getDate() == null || !study.getDate().isBefore(acquisitionCutoff))) return false;
        if (byLastOpened && (study.getDateOpened() != null && !study.getDateOpened().isBefore(lastOpenedCutoff))) return false;
        if (byComments) {
            String comments = String.join(" ", safeComment(study.getComment()), safeComment(study.getComment2()),
                    safeComment(study.getComment3()), safeComment(study.getComment4()));
            String filter = com.oculus.jj.config.AppPreferences.getStr("database.cleanCommentText", "").trim();
            if (filter.isEmpty()) return false;
            boolean contains = comments.toLowerCase(java.util.Locale.ROOT).contains(filter.toLowerCase(java.util.Locale.ROOT));
            boolean shouldContain = com.oculus.jj.config.AppPreferences.getInt("database.cleanCommentMode", 0) == 0;
            if (contains != shouldContain) return false;
        }
        return true;
    }

    private static String safeComment(String value) { return value == null ? "" : value; }

    private boolean hasStudyComments(DicomStudy study) {
        return !safeComment(study.getComment()).isBlank() || !safeComment(study.getComment2()).isBlank()
                || !safeComment(study.getComment3()).isBlank() || !safeComment(study.getComment4()).isBlank();
    }

    private boolean belongsToRegularAlbum(DicomStudy study) {
        for (DicomAlbum album : getAlbums()) {
            if (album != null && !Boolean.TRUE.equals(album.isSmartAlbum())
                    && album.getStudies() != null && album.getStudies().contains(study)) return true;
        }
        return false;
    }

    private java.time.Period agePeriod(String key) {
        return switch (Math.max(0, Math.min(4, com.oculus.jj.config.AppPreferences.getInt(key, 0)))) {
            case 1 -> java.time.Period.ofMonths(6);
            case 2 -> java.time.Period.ofYears(1);
            case 3 -> java.time.Period.ofYears(2);
            case 4 -> java.time.Period.ofYears(5);
            default -> java.time.Period.ofMonths(3);
        };
    }

    private long diskSpaceThresholdBytes() {
        int index = Math.max(0, Math.min(3, com.oculus.jj.config.AppPreferences.getInt("database.cleanDiskSpace", 0)));
        long gigabytes = switch (index) { case 1 -> 2; case 2 -> 5; case 3 -> 10; default -> 1; };
        return gigabytes * 1024L * 1024L * 1024L;
    }

    private long getUsableSpaceBytes() {
        return new File(getDataDirPath()).getUsableSpace();
    }

    private java.util.Comparator<DicomStudy> autoCleanComparator(int policy) {
        return java.util.Comparator.comparing(study -> switch (policy) {
            case 0 -> study.getDate();
            case 2 -> study.getDateOpened();
            default -> study.getDateAdded();
        }, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()));
    }

    private List<java.nio.file.Path> filesToDeleteForStudy(DicomStudy study, boolean deleteLinkedOriginal) {
        List<java.nio.file.Path> files = new ArrayList<>();
        java.nio.file.Path managedRoot = java.nio.file.Path.of(getDataDirPath()).toAbsolutePath().normalize();
        if (study.getSeries() == null) return files;
        for (DicomSeries series : study.getSeries()) {
            if (series == null) continue;
            loadImagesForSeriesIfNeeded(series);
            if (series.getImages() == null) continue;
            for (DicomImage image : series.getImages()) {
                if (image == null || image.completePath() == null) continue;
                try {
                    java.nio.file.Path path = java.nio.file.Path.of(image.completePath()).toRealPath();
                    boolean managed = Boolean.TRUE.equals(image.getInDatabaseFolder()) && path.startsWith(managedRoot);
                    if (managed || deleteLinkedOriginal && !managed) files.add(path);
                } catch (Exception ignored) { }
            }
        }
        return files;
    }

    public boolean mergeStudiesDatabaseOnly(DicomStudy destination, List<DicomStudy> selectedStudies) {
        if (destination == null || selectedStudies == null || selectedStudies.size() < 2
                || sqliteConnection == null || destination.isDistant()) return false;
        List<DicomStudy> sources = new ArrayList<>();
        for (DicomStudy study : selectedStudies) {
            if (study == null || study == destination || study.isDistant()) continue;
            if (!sources.contains(study)) sources.add(study);
        }
        if (sources.isEmpty()) return false;

        loadSeriesForStudyIfNeeded(destination);
        for (DicomStudy source : sources) loadSeriesForStudyIfNeeded(source);

        try {
            int destinationId = getStudyDbId(destination.getStudyInstanceUID());
            if (destinationId <= 0) return false;
            Map<DicomStudy, Integer> sourceIds = new HashMap<>();
            for (DicomStudy source : sources) {
                int sourceId = getStudyDbId(source.getStudyInstanceUID());
                if (sourceId <= 0) return false;
                sourceIds.put(source, sourceId);
            }

            sqliteConnection.setAutoCommit(false);
            try {
                for (Integer sourceId : sourceIds.values()) {
                    try (PreparedStatement mergeAlbums = sqliteConnection.prepareStatement(
                            "INSERT OR IGNORE INTO AlbumStudy (albumId, studyId) SELECT albumId, ? FROM AlbumStudy WHERE studyId = ?")) {
                        mergeAlbums.setInt(1, destinationId);
                        mergeAlbums.setInt(2, sourceId);
                        mergeAlbums.executeUpdate();
                    }
                    try (PreparedStatement removeSourceAlbums = sqliteConnection.prepareStatement(
                            "DELETE FROM AlbumStudy WHERE studyId = ?")) {
                        removeSourceAlbums.setInt(1, sourceId);
                        removeSourceAlbums.executeUpdate();
                    }
                    try (PreparedStatement reparent = sqliteConnection.prepareStatement(
                            "UPDATE Series SET studyId = ? WHERE studyId = ?")) {
                        reparent.setInt(1, destinationId);
                        reparent.setInt(2, sourceId);
                        reparent.executeUpdate();
                    }
                    try (PreparedStatement delete = sqliteConnection.prepareStatement("DELETE FROM Study WHERE id = ?")) {
                        delete.setInt(1, sourceId);
                        delete.executeUpdate();
                    }
                }
                try (PreparedStatement updateCount = sqliteConnection.prepareStatement(
                        "UPDATE Study SET numberOfImages = COALESCE((SELECT SUM(numberOfImages) FROM Series WHERE studyId = ?), 0) WHERE id = ?")) {
                    updateCount.setInt(1, destinationId);
                    updateCount.setInt(2, destinationId);
                    updateCount.executeUpdate();
                }
                sqliteConnection.commit();
            } catch (SQLException ex) {
                sqliteConnection.rollback();
                throw ex;
            } finally {
                sqliteConnection.setAutoCommit(true);
            }

            synchronized (studies) {
                for (DicomStudy source : sources) {
                    if (source.getSeries() != null) {
                        for (DicomSeries series : new ArrayList<>(source.getSeries())) {
                            source.removeSeriesObject(series);
                            destination.addSeriesObject(series);
                            series.setStudy(destination);
                        }
                    }
                    studies.remove(source);
                }
                int totalImages = 0;
                if (destination.getSeries() != null) {
                    for (DicomSeries series : destination.getSeries()) {
                        totalImages += series.getNumberOfImages() != null ? series.getNumberOfImages() : 0;
                    }
                }
                destination.setNumberOfImages(totalImages);
            }
            synchronized (albums) {
                for (DicomAlbum album : albums) {
                    for (DicomStudy source : sources) {
                        if (album.getStudies().remove(source)) album.addStudiesObject(destination);
                    }
                }
            }
            notifyDatabaseChanged();
            return true;
        } catch (SQLException e) {
            logger.warn("Failed to merge studies into {}", destination.getStudyInstanceUID(), e);
            return false;
        }
    }

    /**
     * Seriesを削除
     * HOROS-20240407準拠
     */
    public boolean deleteSeries(DicomSeries series) {
        if (series == null || sqliteConnection == null) {
            return false;
        }

        try {
            sqliteConnection.setAutoCommit(false);
            // まずSeriesのIDを取得
            String selectSql = "SELECT id FROM Series WHERE seriesInstanceUID = ?";
            int seriesDbId = -1;
            try (PreparedStatement pstmt = sqliteConnection.prepareStatement(selectSql)) {
                pstmt.setString(1, series.getSeriesInstanceUID());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    seriesDbId = rs.getInt("id");
                }
            }

            if (seriesDbId > 0) {
                // Imageを削除
                String deleteImagesSql = "DELETE FROM Image WHERE seriesId = ?";
                try (PreparedStatement pstmt = sqliteConnection.prepareStatement(deleteImagesSql)) {
                    pstmt.setInt(1, seriesDbId);
                    pstmt.executeUpdate();
                }

                // Seriesを削除
                String deleteSeriesSql = "DELETE FROM Series WHERE id = ?";
                try (PreparedStatement pstmt = sqliteConnection.prepareStatement(deleteSeriesSql)) {
                    pstmt.setInt(1, seriesDbId);
                    pstmt.executeUpdate();
                }
            }

            sqliteConnection.commit();
            DicomStudy study = series.getStudy();
            if (study != null && study.getSeries() != null) {
                study.getSeries().remove(series);
            }
            return true;
        } catch (SQLException e) {
            try {
                sqliteConnection.rollback();
            } catch (SQLException ignored) {
            }
            logger.warn("Failed to delete series {}", series.getSeriesInstanceUID(), e);
            return false;
        } finally {
            try {
                sqliteConnection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    // ========== ファイル追加メソッド ==========

    /**
     * パスのファイルを追加
     */
    public List<Object> addFilesAtPaths(List<String> paths) {
        return addFilesAtPaths(paths, true);
    }

    /**
     * パスのファイルを追加（通知オプション付き）
     */
    public List<Object> addFilesAtPaths(List<String> paths, boolean postNotifications) {
        return addFilesAtPaths(paths, postNotifications, false, false);
    }

    /**
     * パスのファイルを追加（全オプション）
     */
    public List<Object> addFilesAtPaths(List<String> paths, boolean postNotifications,
            boolean dicomOnly, boolean rereadExistingItems) {
        return addFilesAtPaths(paths, postNotifications, dicomOnly, rereadExistingItems, false);
    }

    /**
     * パスのファイルを追加（OsiriX生成フラグ付き）
     */
    public List<Object> addFilesAtPaths(List<String> paths, boolean postNotifications,
            boolean dicomOnly, boolean rereadExistingItems,
            boolean generatedByOsiriX) {
        return addFilesAtPaths(paths, postNotifications, dicomOnly, rereadExistingItems,
                generatedByOsiriX, true);
    }

    /**
     * パスのファイルを追加（返却配列オプション付き）
     */
    public List<Object> addFilesAtPaths(List<String> paths, boolean postNotifications,
            boolean dicomOnly, boolean rereadExistingItems,
            boolean generatedByOsiriX, boolean returnArray) {
        return addFilesAtPaths(paths, postNotifications, dicomOnly, rereadExistingItems,
                generatedByOsiriX, false, returnArray);
    }

    /**
     * パスのファイルを追加（インポートファイルフラグ付き）
     */
    public List<Object> addFilesAtPaths(List<String> paths, boolean postNotifications,
            boolean dicomOnly, boolean rereadExistingItems,
            boolean generatedByOsiriX, boolean importedFiles,
            boolean returnArray) {
        // HOROS-20240407準拠: NSThread* thread = [NSThread currentThread]; (1502行目)
        Thread thread = Thread.currentThread();
        com.oculus.jj.threads.ThreadsManager tm = com.oculus.jj.threads.ThreadsManager.defaultManager();

        // HOROS-20240407準拠: ファイルを追加してスタディを作成
        List<Object> addedObjects = new java.util.ArrayList<>();

        if (paths == null || paths.isEmpty()) {
            return addedObjects;
        }

        // HOROS-20240407準拠: [thread enterOperation]; thread.status = [NSString
        // stringWithFormat:NSLocalizedString(@"Scanning %@", nil),
        // N2LocalizedSingularPluralCount(paths.count, NSLocalizedString(@"file", nil),
        // NSLocalizedString(@"files", nil))]; (1526-1527行目)
        String scanningStatus = paths.size() == 1 ? "Scanning 1 file" : "Scanning " + paths.size() + " files";
        tm.setThreadStatus(thread, scanningStatus);
        tm.setThreadProgress(thread, -1.0); // indeterminateモード

        // HOROS-20240407準拠: ファイルパスを展開（ディレクトリの場合は再帰的にファイルを取得）
        List<String> expandedPaths = new java.util.ArrayList<>();
        for (String path : paths) {
            java.io.File file = new java.io.File(path);
            if (file.isDirectory()) {
                collectFilesRecursively(file, expandedPaths);
            } else if (file.isFile()) {
                expandedPaths.add(path);
            }
        }

        // HOROS-20240407準拠: ディレクトリを作成（1539-1543行目）
        String dataDirPath = this.dataBaseDirPath;
        String reportsDirPath = this.dataBaseDirPath + File.separator + "REPORTS";
        String errorsDirPath = this.dataBaseDirPath + File.separator + "ERRORS";

        java.io.File dataDir = new java.io.File(dataDirPath);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        java.io.File reportsDir = new java.io.File(reportsDirPath);
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }
        java.io.File errorsDir = new java.io.File(errorsDirPath);
        if (!errorsDir.exists()) {
            errorsDir.mkdirs();
        }

        // HOROS-20240407準拠: DICOMファイルをスキャンしてdicomFilesArrayを構築（1537-1641行目）
        List<Map<String, Object>> dicomFilesArray = new java.util.ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < expandedPaths.size(); i++) {
            String path = expandedPaths.get(i);

            // HOROS-20240407準拠: 進捗更新（1555-1558行目）
            long currentTime = System.currentTimeMillis();
            if (currentTime - startTime > 500 || i == expandedPaths.size() - 1) {
                tm.setThreadProgress(thread, 1.0 * i / expandedPaths.size());
                startTime = currentTime;
            }

            try {
                java.io.File file = new java.io.File(path);
                if (!file.exists() || !file.isFile()) {
                    continue;
                }

                // HOROS-20240407準拠: DicomFileを作成してdicomElementsを取得（1567-1580行目）
                Map<String, Object> curDict = extractDicomElements(file);

                if (curDict != null) {
                    // HOROS-20240407準拠: dicomOnlyチェック（1581-1585行目）
                    if (dicomOnly) {
                        String fileType = (String) curDict.get("fileType");
                        if (fileType == null || !fileType.startsWith("DICOM")) {
                            curDict = null;
                        }
                    }

                    if (curDict != null) {
                        dicomFilesArray.add(curDict);
                    } else {
                        // HOROS-20240407準拠: 読み取り不能なファイルの処理（1593-1609行目）
                        String absolutePath = file.getAbsolutePath();
                        if (dataDirPath != null && absolutePath.startsWith(dataDirPath)) {

                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to extract DICOM elements from {}", path, e);
            }

            // HOROS-20240407準拠: キャンセルチェック（1624-1640行目）
            if (Thread.currentThread().isInterrupted()) {
                dicomFilesArray.clear();
                break;
            }
        }

        // HOROS-20240407準拠: [thread enterOperationIgnoringLowerLevels]; thread.status =
        // [NSString stringWithFormat:NSLocalizedString(@"Adding %@", nil),
        // N2LocalizedSingularPluralCount(dicomFilesArray.count,
        // NSLocalizedString(@"file", nil), NSLocalizedString(@"files", nil))];
        // (1643-1644行目)
        String addingStatus = dicomFilesArray.size() == 1 ? "Adding 1 file"
                : "Adding " + dicomFilesArray.size() + " files";
        tm.setThreadStatus(thread, addingStatus);

        // HOROS-20240407準拠: addFilesDescribedInDictionariesを呼び出す（1648-1653行目）
        // HOROS-20240407準拠: List<Map>をList<Object>に変換
        List<Object> dicomFilesArrayAsObject = new java.util.ArrayList<>();
        for (Map<String, Object> dict : dicomFilesArray) {
            dicomFilesArrayAsObject.add(dict);
        }
        List<Object> objectIDs = addFilesDescribedInDictionaries(
                dicomFilesArrayAsObject,
                postNotifications,
                rereadExistingItems,
                generatedByOsiriX,
                importedFiles,
                returnArray);

        // HOROS-20240407準拠: 返却配列に追加（1668-1676行目）
        if (returnArray) {
            if (objectIDs != null) {
                addedObjects.addAll(objectIDs);
            }
        }

        return addedObjects;
    }

    /**
     * 辞書で記述されたファイルを追加（インポートファイルフラグ付き）
     * HOROS-20240407準拠: - (NSArray*)addFilesDescribedInDictionaries:...
     * (1755-4271行目)
     */
    public List<Object> addFilesDescribedInDictionaries(List<Object> dicomFilesArray,
            boolean postNotifications,
            boolean rereadExistingItems,
            boolean generatedByOsiriX,
            boolean importedFiles,
            boolean returnArray) {
        // HOROS-20240407準拠: NSThread* thread = [NSThread currentThread]; (1761行目)
        Thread thread = Thread.currentThread();
        com.oculus.jj.threads.ThreadsManager tm = com.oculus.jj.threads.ThreadsManager.defaultManager();

        // HOROS-20240407準拠: thread.status = [NSString
        // stringWithFormat:NSLocalizedString(@"Adding %@", nil),
        // N2LocalizedSingularPluralCount(dicomFilesArray.count,
        // NSLocalizedString(@"file", nil), NSLocalizedString(@"files", nil))]; (1762行目)
        String addingStatus = dicomFilesArray.size() == 1 ? "Adding 1 file"
                : "Adding " + dicomFilesArray.size() + " files";
        tm.setThreadStatus(thread, addingStatus);

        // HOROS-20240407準拠: NSMutableArray* newStudies = [NSMutableArray array];
        // (1764行目)
        List<DicomStudy> newStudies = new java.util.ArrayList<>();

        // HOROS-20240407準拠: NSMutableArray* addedImageObjects = nil; (1766行目)
        List<Object> addedImageObjects = null;
        if (returnArray) {
            addedImageObjects = new java.util.ArrayList<>();
        }

        // HOROS-20240407準拠: NSDate* today = [NSDate date]; (1801行目)
        LocalDateTime today = LocalDateTime.now();

        // HOROS-20240407準拠: NSDate *defaultDate = [NSCalendarDate dateWithYear:1901
        // month:1 day:1 hour:0 minute:0 second:0 timeZone:nil]; (1792行目)
        LocalDateTime defaultDate = LocalDateTime.of(1901, 1, 1, 0, 0, 0);

        // HOROS-20240407準拠: NSString* dataDirPath = self.dataDirPath; (1802行目)
        String dataDirPath = this.dataDirPath();

        // HOROS-20240407準拠: NSMutableArray* studiesArray = [[self
        // objectsForEntity:self.studyEntity] mutableCopy]; (1789行目)
        List<DicomStudy> studiesArray = new java.util.ArrayList<>();
        synchronized (studies) {
            studiesArray.addAll(studies);
        }

        // HOROS-20240407準拠: NSMutableArray *studiesArrayStudyInstanceUID =
        // [[studiesArray valueForKey:@"studyInstanceUID"] mutableCopy]; (1797行目)
        List<String> studiesArrayStudyInstanceUID = new java.util.ArrayList<>();
        for (DicomStudy s : studiesArray) {
            studiesArrayStudyInstanceUID.add(s.getStudyInstanceUID());
        }

        DicomStudy study = null;
        DicomSeries series = null;
        DicomImage image = null;
        String curPatientUID = null;
        String curStudyID = null;
        String curSerieID = null;
        boolean newObject = false;
        boolean newStudy = false;

        // HOROS-20240407準拠: インポートされたStudyを記録
        java.util.Set<DicomStudy> touchedStudies = new java.util.HashSet<>();

        // HOROS-20240407準拠: for (NSInteger i = 0; i < dicomFilesArray.count; ++i)
        // (1815行目)
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < dicomFilesArray.size(); i++) {
            // HOROS-20240407準拠: 進捗更新（1817-1820行目）
            long currentTime = System.currentTimeMillis();
            if (currentTime - startTime > 500 || i == dicomFilesArray.size() - 1) {
                tm.setThreadProgress(thread, 1.0 * i / dicomFilesArray.size());
                startTime = currentTime;
            }

            try {
                // HOROS-20240407準拠: NSMutableDictionary *curDict = [dicomFilesArray
                // objectAtIndex:i]; (1826行目)
                @SuppressWarnings("unchecked")
                Map<String, Object> curDict = (Map<String, Object>) dicomFilesArray.get(i);

                // HOROS-20240407準拠: newFile = [curDict objectForKey:@"filePath"]; (1829行目)
                String newFile = (String) curDict.get("filePath");

                // HOROS-20240407準拠: BOOL inParseExistingObject = rereadExistingItems; (1832行目)
                boolean inParseExistingObject = rereadExistingItems;

                // HOROS-20240407準拠: NSString *SOPClassUID = [curDict
                // objectForKey:@"SOPClassUID"]; (1834行目)
                String sopClassUID = (String) curDict.get("SOPClassUID");

                // HOROS-20240407準拠: 辞書からStudy/Series/Imageを作成（1902-4271行目）
                // まず、Studyを検索または作成
                String studyID = (String) curDict.get("studyID");
                String patientUID = (String) curDict.get("patientUID");

                // HOROS-20240407準拠: Study検索（1904-1969行目）
                if (studyID != null && studyID.equals(curStudyID) &&
                        patientUID != null && patientUID.equals(curPatientUID)) {
                    // 同じStudyの続き
                } else {
                    // 新しいStudyを検索または作成
                    study = null;
                    curSerieID = null;
                    newObject = false;

                    // HOROS-20240407準拠: NSInteger index = [studiesArrayStudyInstanceUID
                    // indexOfObject:[curDict objectForKey: @"studyID"]]; (1918行目)
                    int index = studiesArrayStudyInstanceUID.indexOf(studyID);

                    if (index >= 0) {
                        // HOROS-20240407準拠: 既存のStudyを検索（1922-1968行目）
                        DicomStudy tstudy = studiesArray.get(index);

                        if (tstudy.getPatientUID() == null) {
                            tstudy.setPatientUID(patientUID);
                        }
                        // studyInstanceUIDはDB上でUNIQUEのため、UID一致時は同一Studyとして統合する
                        study = tstudy;
                    }

                    // HOROS-20240407準拠: 新しいStudyを作成（1971-1992行目）
                    if (study == null) {
                        study = new DicomStudy();
                        newObject = true;
                        newStudy = true;

                        study.setDateAdded(today);
                        newStudies.add(study);
                        studiesArray.add(study);
                        if (studyID != null) {
                            studiesArrayStudyInstanceUID.add(studyID);
                        } else {

                            studiesArrayStudyInstanceUID.add("noStudyID");
                        }

                        curSerieID = null;
                        synchronized (studies) {
                            studies.add(study);
                        }
                    }

                    // touchedStudiesに追加
                    touchedStudies.add(study);

                    // HOROS-20240407準拠: Studyのフィールドを設定（1996-2045行目）
                    if (newObject || inParseExistingObject) {
                        // HOROS-20240407準拠: DicomDatabase.mm 1996-2004行目
                        study.setStudyInstanceUID(studyID);
                        study.setAccessionNumber((String) curDict.get("accessionNumber"));
                        // HOROS-20240407準拠: 1998行目 - study.modality = study.modalities;
                        study.setModality(study.modalities());
                        study.setDateOfBirth(null); // 後で設定
                        Object patientBirthDateObj = curDict.get("patientBirthDate");
                        if (patientBirthDateObj != null) {
                            if (patientBirthDateObj instanceof Date) {
                                Date patientBirthDate = (Date) patientBirthDateObj;
                                study.setDateOfBirth(patientBirthDate.toInstant()
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate());
                            } else if (patientBirthDateObj instanceof String) {
                                try {
                                    study.setDateOfBirth(java.time.LocalDate.parse((String) patientBirthDateObj));
                                } catch (Exception e) {
                                    // パースエラーは無視
                                }
                            }
                        }
                        study.setPatientSex((String) curDict.get("patientSex"));
                        study.setPatientID((String) curDict.get("patientID"));
                        study.setName((String) curDict.get("patientName"));
                        study.setPatientUID(patientUID);
                        String studyNumber = (String) curDict.get("studyNumber");
                        study.setStudyID(studyNumber);
                        // HOROS-20240407準拠: idプロパティにStudyIDを設定
                        // HOROS-20240407準拠: BrowserController.m 6560-6561行目では、[item
                        // valueForKey:@"id"]を返している
                        // HOROS-20240407準拠: idプロパティにはStudyID（DICOMのStudyIDタグ）の値が設定されている
                        if (studyNumber != null && !studyNumber.isEmpty()) {
                            study.setId(studyNumber);
                        } else {
                            study.setId("");
                        }

                        // HOROS-20240407準拠: 2006-2016行目 - SR/PDFの特別処理
                        // sopClassUIDは既に1680行目で定義されているため、ここでは使用するだけ
                        boolean isStructuredReport = false;
                        boolean isPDF = false;

                        if ((isStructuredReport || isPDF) && inParseExistingObject) {
                            String studyDescription = (String) curDict.get("studyDescription");
                            if (studyDescription != null && !studyDescription.isEmpty()
                                    && !studyDescription.equals("unnamed")) {
                                study.setStudyName(studyDescription);
                            }
                            String referringPhysician = (String) curDict.get("referringPhysiciansName");
                            if (referringPhysician != null && !referringPhysician.isEmpty()) {
                                study.setReferringPhysician(referringPhysician);
                            }
                            String performingPhysician = (String) curDict.get("performingPhysiciansName");
                            if (performingPhysician != null && !performingPhysician.isEmpty()) {
                                study.setPerformingPhysician(performingPhysician);
                            }
                            String institutionName = (String) curDict.get("institutionName");
                            if (institutionName != null && !institutionName.isEmpty()) {
                                study.setInstitutionName(institutionName);
                            }
                        } else {
                            // HOROS-20240407準拠: 2019-2023行目
                            study.setStudyName((String) curDict.get("studyDescription"));
                            study.setReferringPhysician((String) curDict.get("referringPhysiciansName"));
                            study.setPerformingPhysician((String) curDict.get("performingPhysiciansName"));
                            study.setInstitutionName((String) curDict.get("institutionName"));
                        }

                        // HOROS-20240407準拠: 2025-2026行目
                        String studyName = study.getStudyName();
                        if (studyName == null || studyName.isEmpty() || studyName.equals("unnamed")) {
                            study.setStudyName((String) curDict.get("seriesDescription"));
                        }
                    } else {
                        // HOROS-20240407準拠: 既存のStudyの場合（2035-2045行目）
                        // modalityが"SR"または"OT"の場合は更新（2037-2038行目）
                        String modality = study.getModality();
                        if ("SR".equals(modality) || "OT".equals(modality)) {
                            String newModality = (String) curDict.get("modality");
                            if (newModality != null) {
                                study.setModality(newModality);
                            }
                        }

                        // HOROS-20240407準拠: 2040-2044行目
                        String currentStudyName = study.getStudyName();
                        if (currentStudyName == null || currentStudyName.isEmpty()
                                || currentStudyName.equals("unnamed")) {
                            String studyDescription = (String) curDict.get("studyDescription");
                            if (studyDescription != null) {
                                study.setStudyName(studyDescription);
                            }
                        }
                        if (study.getStudyName() == null || study.getStudyName().isEmpty()
                                || study.getStudyName().equals("unnamed")) {
                            String seriesDescription = (String) curDict.get("seriesDescription");
                            if (seriesDescription != null) {
                                study.setStudyName(seriesDescription);
                            }
                        }
                    }

                    // HOROS-20240407準拠: 2047-2051行目 - studyDateを設定
                    Date studyDate = (Date) curDict.get("studyDate");
                    if (studyDate != null) {
                        LocalDateTime studyDateLocal = LocalDateTime.ofInstant(studyDate.toInstant(),
                                ZoneId.systemDefault());
                        if (studyDateLocal.isAfter(defaultDate)) {
                            if (study.getDate() == null || study.getDate().isBefore(studyDateLocal)) {
                                study.setDate(studyDateLocal);
                            }
                    } else {
                        // HOROS-20240407??: StudyDate???????????????????????
                        if (study.getDate() == null) {
                            study.setDate(today);
                        }
                        }
                    }

                    curStudyID = studyID;
                    curPatientUID = patientUID;

                    // HOROS-20240407準拠: studiesに追加
                    synchronized (studies) {
                        if (!studies.contains(study)) {
                            studies.add(study);
                        }
                    }
                }

                // HOROS-20240407準拠: Seriesを検索または作成（2059-2110行目）
                String seriesID = (String) curDict.get("seriesID");
                if (seriesID == null) {
                    seriesID = (String) curDict.get("seriesDICOMUID");
                }

                if (seriesID == null || !seriesID.equals(curSerieID)) {
                    // 新しいSeriesを検索または作成
                    series = null;
                    java.util.Set<DicomSeries> seriesSet = study.getSeries();
                    if (seriesSet != null) {
                        for (DicomSeries s : seriesSet) {
                            if (seriesID != null && seriesID.equals(s.getSeriesInstanceUID())) {
                                series = s;
                                break;
                            }
                        }
                    }

                    if (series == null) {
                        // HOROS-20240407準拠: 新しいSeriesを作成（2079-2107行目）
                        series = new DicomSeries();
                        series.setDateAdded(today);
                        newObject = true;
                    } else {
                        loadImagesForSeriesIfNeeded(series);
                        newObject = false;
                    }

                    if (newObject || inParseExistingObject) {
                        // HOROS-20240407準拠: DicomDatabase.mm 2092-2100行目
                        if (curDict.get("seriesDICOMUID") != null) {
                            series.setSeriesDICOMUID((String) curDict.get("seriesDICOMUID"));
                        }
                        if (curDict.get("SOPClassUID") != null) {
                            series.setSeriesSOPClassUID((String) curDict.get("SOPClassUID"));
                        }
                        series.setSeriesInstanceUID(seriesID);
                        series.setName((String) curDict.get("seriesDescription"));
                        series.setModality((String) curDict.get("modality"));
                        Object seriesNumberObj = curDict.get("seriesNumber");
                        if (seriesNumberObj != null) {
                            if (seriesNumberObj instanceof Number) {
                                series.setSeriesNumber(((Number) seriesNumberObj).intValue());
                            }
                        }
                        series.setSeriesDescription((String) curDict.get("protocolName"));
                        series.setStudy(study);

                        Date seriesDate = (Date) curDict.get("studyDate");
                        if (seriesDate != null) {
                            series.setDate(LocalDateTime.ofInstant(seriesDate.toInstant(), ZoneId.systemDefault()));
                        }

                        // HOROS-20240407準拠: 2104-2106行目 - Studyのモダリティを更新
                        // Studyのモダリティが"OT"または"SC"で、Seriesのモダリティが"OT"または"SC"でない場合は更新
                        String studyModality = study.getModality();
                        String seriesModality = (String) curDict.get("modality");
                        if (("OT".equals(studyModality) || "SC".equals(studyModality)) &&
                                seriesModality != null && !"OT".equals(seriesModality)
                                && !"SC".equals(seriesModality)) {
                            study.setModality(seriesModality);
                        }
                    }

                    curSerieID = seriesID;

                    // HOROS-20240407準拠: StudyにSeriesを追加
                    java.util.Set<DicomSeries> studySeriesSet = study.getSeries();
                    if (studySeriesSet == null) {
                        studySeriesSet = new java.util.HashSet<>();
                        study.setSeries(studySeriesSet);
                    }
                    if (!studySeriesSet.contains(series)) {
                        studySeriesSet.add(series);
                        study.addSeriesObject(series);
                    }
                }

                // HOROS-20240407準拠: Imageを検索または作成（2112-2271行目）
                String sopUID = (String) curDict.get("SOPUID");
                int numberOfFrames = ((Number) curDict.getOrDefault("numberOfFrames", 1)).intValue();
                if (numberOfFrames == 0) {
                    numberOfFrames = 1;
                }

                for (int f = 0; f < numberOfFrames; f++) {
                    image = null;

                    // HOROS-20240407準拠: 既存のImageを検索（2132-2140行目）
                    java.util.Set<DicomImage> imageSet = series.getImages();
                    if (imageSet != null) {
                        for (DicomImage img : imageSet) {
                            if (sopUID != null && sopUID.equals(img.getSopInstanceUID()) &&
                                    img.getFrameID() != null && img.getFrameID() == f) {
                                image = img;
                                break;
                            }
                        }
                    }

                    if (image == null) {
                        // HOROS-20240407準拠: 新しいImageを作成（2169-2271行目）
                        image = new DicomImage();
                        newObject = true;
                    } else {
                        newObject = false;
                    }

                    if (newObject || inParseExistingObject) {
                        // HOROS-20240407準拠: DicomDatabase.mm 2198-2248行目
                        image.setFrameID(f);

                        // HOROS-20240407準拠: 2200-2216行目 -
                        // instanceNumberの設定（combineProjectionSeriesの処理は省略）
                        Object imageIDObj = curDict.get("imageID");
                        if (imageIDObj != null) {
                            if (imageIDObj instanceof Number) {
                                int instanceNumber = ((Number) imageIDObj).intValue();
                                image.setInstanceNumber(instanceNumber + f);
                            }
                        } else {
                            image.setInstanceNumber(0);
                        }

                        // HOROS-20240407準拠: 2218-2219行目 - pathの設定
                        // HOROS-20240407準拠: if (local) [image setValue: [newFile lastPathComponent]
                        // forKey:@"path"];
                        // HOROS-20240407準拠: else [image setValue:newFile forKey:@"path"];
                        // HOROS-20240407準拠: localの判定（ファイルがDBフォルダ内かどうか）
                        // HOROS-20240407準拠: DicomDatabase.mm 2115-2117行目
                        // HOROS-20240407準拠: BOOL local = NO; if (dataDirPath && [newFile
                        // hasPrefix:dataDirPath]) local = YES;
                        boolean local = false;
                        String dataDirPathValue = this.dataDirPath();
                        if (dataDirPathValue != null && !dataDirPathValue.isEmpty() && newFile != null) {
                            // HOROS-20240407準拠: hasPrefix:は文字列の先頭一致判定
                            local = newFile.startsWith(dataDirPathValue);
                        }
                        if (local) {
                            // HOROS-20240407準拠: setPath:メソッドを使用（pathNumber/pathStringの自動設定）
                            image.setPath(new java.io.File(newFile).getName());
                        } else {
                            // HOROS-20240407準拠: 絶対パスの場合はpathStringに設定
                            image.setPath(newFile);
                        }
                        // HOROS-20240407準拠: 2221行目 - inDatabaseFolderの設定
                        image.setInDatabaseFolder(local);

                        // HOROS-20240407準拠: 2223行目 - dateの設定
                        Date imageDate = (Date) curDict.get("studyDate");
                        if (imageDate != null) {
                            image.setDate(LocalDateTime.ofInstant(imageDate.toInstant(), ZoneId.systemDefault()));
                        }

                        // HOROS-20240407準拠: 2225行目 - sopInstanceUIDの設定
                        image.setSopInstanceUID(sopUID);

                        // HOROS-20240407準拠: 2227-2230行目 - sliceLocationの設定
                        Object sliceLocationObj = curDict.get("sliceLocation");
                        if (sliceLocationObj != null) {
                            if (sliceLocationObj instanceof Number) {
                                image.setSliceLocation(((Number) sliceLocationObj).doubleValue());
                            }
                        } else {
                            image.setSliceLocation(0.0);
                        }

                        // HOROS-20240407準拠: 2232-2233行目 - commentの設定（imageCommentPerFrameの処理は省略）

                        // HOROS-20240407準拠: 2235行目 - extensionの設定
                        String extension = new java.io.File(newFile).getName();
                        int lastDot = extension.lastIndexOf('.');
                        if (lastDot >= 0 && lastDot < extension.length() - 1) {
                            extension = extension.substring(lastDot + 1).toLowerCase();
                        } else {
                            extension = "dcm";
                        }
                        image.setExtension(extension);

                        // HOROS-20240407準拠: 2236行目 - fileTypeの設定
                        image.setFileType((String) curDict.get("fileType"));

                        // HOROS-20240407準拠: 2238-2239行目 - height, widthの設定
                        Object heightObj = curDict.get("height");
                        if (heightObj != null && heightObj instanceof Number) {
                            image.setHeight(((Number) heightObj).intValue());
                        } else {
                            image.setHeight(0);
                        }
                        Object widthObj = curDict.get("width");
                        if (widthObj != null && widthObj instanceof Number) {
                            image.setWidth(((Number) widthObj).intValue());
                        } else {
                            image.setWidth(0);
                        }

                        // HOROS-20240407準拠: 2240-2241行目 - numberOfFrames, numberOfSeriesの設定
                        Object numberOfFramesObj = curDict.get("numberOfFrames");
                        if (numberOfFramesObj != null && numberOfFramesObj instanceof Number) {
                            image.setNumberOfFrames(((Number) numberOfFramesObj).intValue());
                        } else {
                            image.setNumberOfFrames(1);
                        }
                        Object numberOfSeriesObj = curDict.get("numberOfSeries");
                        if (numberOfSeriesObj != null && numberOfSeriesObj instanceof Number) {
                            image.setNumberOfSeries(((Number) numberOfSeriesObj).intValue());
                        } else {
                            image.setNumberOfSeries(1);
                        }

                        // HOROS-20240407準拠: 2243行目 - thumbnailの設定
                        Object thumbnailObj = curDict.get("NSImageThumbnail");
                        if (thumbnailObj != null && thumbnailObj instanceof java.awt.Image) {
                            image.setThumbnail((java.awt.Image) thumbnailObj);
                        }

                        // HOROS-20240407準拠: 2245-2248行目 - importedFileの設定
                        if (importedFiles) {
                            image.setImportedFile(true);
                        } else {
                            image.setImportedFile(null);
                        }

                        image.setSeries(series);

                        // HOROS-20240407準拠: 2260-2261行目 - Imageのモダリティを設定し、Studyのモダリティを更新
                        // [image setValue: [curDict objectForKey: @"modality"] forKey:@"modality"];
                        image.setModality((String) curDict.get("modality"));
                        // [study setValue:[study valueForKey:@"modalities"] forKey:@"modality"];
                        study.setModality(study.modalities());
                    }

                    // HOROS-20240407準拠: SeriesにImageを追加
                    java.util.Set<DicomImage> currentImageSet = series.getImages();
                    if (currentImageSet == null) {
                        currentImageSet = new java.util.HashSet<>();
                        series.setImages(currentImageSet);
                    }
                    if (!currentImageSet.contains(image)) {
                        currentImageSet.add(image);
                        series.addImagesObject(image);
                    }

                    // HOROS-20240407準拠: ファイルパスマッピングに追加
                    synchronized (filePathToImageMap) {
                        filePathToImageMap.put(newFile, image);
                    }

                    // HOROS-20240407準拠: returnArrayがtrueの場合は追加されたImageを返す
                    if (returnArray && addedImageObjects != null) {
                        addedImageObjects.add(image);
                    }
                }

                // HOROS-20240407準拠: Seriesの画像数を更新
                java.util.Set<DicomImage> finalImageSetForCount = series.getImages();
                if (finalImageSetForCount != null) {
                    series.setNumberOfImages(finalImageSetForCount.size());
                } else {
                    series.setNumberOfImages(0);
                }

                // HOROS-20240407準拠: Studyの画像数を更新
                updateStudyNumberOfImages(study);

            } catch (Exception e) {
                logger.warn("Failed to extract DICOM elements", e);
            }
        }

        // HOROS-20240407準拠: データベースに保存（ファイル追加時はAlbum再保存は不要）
        boolean saved = save(touchedStudies, false);
        lastImportDiagnostic = String.format("extracted=%d/%d, saved=%s%s",
                dicomFilesArray.size(), dicomFilesArray.size(), saved,
                (!saved && lastSaveError != null && !lastSaveError.isEmpty()) ? " (" + lastSaveError + ")" : "");

        // HOROS-20240407準拠: データベース変更を通知
        notifyDatabaseChanged();

        // HOROS-20240407準拠: 返却配列を返す
        if (returnArray) {
            return addedImageObjects != null ? addedImageObjects : new java.util.ArrayList<>();
        } else {
            return new java.util.ArrayList<>();
        }
    }

    /**
     * 全てのスタディを取得
     * HOROS-20240407準拠
     */
    public List<DicomStudy> getAllStudies() {
        synchronized (studies) {
            return new java.util.ArrayList<>(studies);
        }
    }

    /**
     * アルバムリストを取得
     * HOROS-20240407準拠: - (NSArray*) albums
     */
    public List<DicomAlbum> getAlbums() {
        synchronized (albums) {
            return new java.util.ArrayList<>(albums);
        }
    }

    /**
     * アルバムを追加
     * HOROS-20240407準拠
     */
    public void addAlbum(DicomAlbum album) {
        if (album != null) {
            synchronized (albums) {
                albums.add(album);
            }
        }
    }

    /**
     * アルバムを削除
     * HOROS-20240407準拠
     */
    public void removeAlbum(DicomAlbum album) {
        if (album != null) {
            synchronized (albums) {
                albums.remove(album);
            }
        }
    }

    /**
     * デフォルトアルバムを作成
     * HOROS-20240407準拠
     */
    private void createDefaultAlbums() {
        // HOROS-20240407準拠: 最初のアルバムは「Database」（全Study）
        // これはalbumsリストには含めず、albumTable.selectedRow == 0で判定

        // HOROS-20240407準拠: デフォルトのスマートアルバムを作成
        albums.add(new DicomAlbum("Today", "date >= $TODAY"));
        albums.add(new DicomAlbum("Yesterday", "date >= $YESTERDAY AND date < $TODAY"));
        albums.add(new DicomAlbum("Last Week", "date >= $LASTWEEK"));
    }

    /**
     * DICOMファイルからStudyInstanceUIDを抽出（簡易実装）
     * HOROS-20240407準拠: DicomFile.dicomElementsから取得
     */
    /**
     * DICOMファイルからメタデータを抽出
     * HOROS-20240407準拠: DicomFile.dicomElementsから取得
     */
    private Attributes readDicomAttributes(java.io.File file) {
        try (DicomInputStream dis = new DicomInputStream(file)) {
            dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
            try {
                dis.readFileMetaInformation();
            } catch (Exception e) {
                try (DicomInputStream dis2 = new DicomInputStream(file)) {
                    dis2.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
                    return dis2.readDataset();
                }
            }
            return dis.readDataset();
        } catch (Exception e) {
            logger.warn("Failed to read DICOM attributes from {}: {}", file.getName(), e.getMessage());
            return null;
        }
    }

    private String extractStudyInstanceUID(java.io.File file) {
        Attributes attrs = readDicomAttributes(file);
        if (attrs != null) {
            return attrs.getString(0x0020000D); // StudyInstanceUID
        }
        return null;
    }

    private String extractSeriesInstanceUID(java.io.File file) {
        Attributes attrs = readDicomAttributes(file);
        if (attrs != null) {
            return attrs.getString(0x0020000E); // SeriesInstanceUID
        }
        return null;
    }

    private String extractSOPInstanceUID(java.io.File file) {
        Attributes attrs = readDicomAttributes(file);
        if (attrs != null) {
            return attrs.getString(0x00080018); // SOPInstanceUID
        }
        return null;
    }

    /**
     * DICOMファイルから辞書（Map）を作成
     * HOROS-20240407準拠: DicomFile.dicomElements (1686-1742行目のコメント参照)
     * 辞書のキーはHOROS-20240407のDicomDatabase.mm 1686-1742行目に記載されている
     */
    private Map<String, Object> extractDicomElements(java.io.File file) {
        Attributes attrs = readDicomAttributes(file);
        if (attrs == null) {
            return null;
        }

        Map<String, Object> dict = new HashMap<>();

        // HOROS-20240407準拠: filePath (1689行目)
        dict.put("filePath", file.getAbsolutePath());

        // HOROS-20240407準拠: fileType (1693行目)
        // HOROS-20240407準拠: Transfer Syntax UIDを確認してMPEG2を識別
        // DicomFileDCMTKCategory.mm 326行目、DicomDatabase+Scan.mm 236行目
        String fileType = "DICOM";
        String transferSyntaxUID = attrs.getString(0x00020010); // TransferSyntaxUID
        if (transferSyntaxUID != null && transferSyntaxUID.equals("1.2.840.10008.1.2.4.100")) {
            // HOROS-20240407準拠: MPEG2 Main Profile at Main Level
            fileType = "DICOMMPEG2";
        }
        dict.put("fileType", fileType);
        dict.put("hasDICOM", true);

        // HOROS-20240407準拠: SOPClassUID (1690行目)
        String sopClassUID = attrs.getString(0x00080016); // SOPClassUID
        if (sopClassUID != null) {
            dict.put("SOPClassUID", sopClassUID);
        }

        // HOROS-20240407準拠: studyID (1694行目) = StudyInstanceUID
        String studyInstanceUID = attrs.getString(0x0020000D); // StudyInstanceUID
        if (studyInstanceUID != null) {
            dict.put("studyID", studyInstanceUID);
        }

        // HOROS-20240407準拠: patientUID (1695行目)
        // HOROS-20240407準拠: patientUIDはpatientName, patientID, patientBirthDateから計算される
        String patientName = attrs.getString(0x00100010); // PatientName
        String patientID = attrs.getString(0x00100020); // PatientID
        Date patientBirthDate = attrs.getDate(0x00100030); // PatientBirthDate
        String patientUID = calculatePatientUID(patientName, patientID, patientBirthDate);
        if (patientUID != null) {
            dict.put("patientUID", patientUID);
        }

        // HOROS-20240407準拠: patientName (1701行目)
        if (patientName != null) {
            dict.put("patientName", patientName);
        }

        // HOROS-20240407準拠: patientID (1702行目)
        if (patientID != null) {
            dict.put("patientID", patientID);
        }

        // HOROS-20240407準拠: patientBirthDate (1699行目)
        if (patientBirthDate != null) {
            dict.put("patientBirthDate", patientBirthDate);
        }

        // HOROS-20240407準拠: patientSex (1700行目)
        String patientSex = attrs.getString(0x00100040); // PatientSex
        if (patientSex != null) {
            dict.put("patientSex", patientSex);
        }

        // HOROS-20240407準拠: studyDescription (1704行目)
        String studyDescription = attrs.getString(0x00081030); // StudyDescription
        if (studyDescription == null || studyDescription.trim().isEmpty()) {
            org.dcm4che3.data.Sequence procedureCodeSeq = attrs.getSequence(0x00081032); // ProcedureCodeSequence
            if (procedureCodeSeq != null && !procedureCodeSeq.isEmpty()) {
                Attributes firstItem = procedureCodeSeq.get(0);
                if (firstItem != null) {
                    studyDescription = firstItem.getString(0x00080104); // CodeMeaning
                }
            }
        }
        if (studyDescription == null || studyDescription.trim().isEmpty()) {
            studyDescription = "unnamed";
        }
        dict.put("studyDescription", studyDescription);

        // HOROS-20240407準拠: studyDate (1710行目)
        Date studyDate = attrs.getDate(0x00080020); // StudyDate
        if (studyDate != null) {
            dict.put("studyDate", studyDate);
        }

        // HOROS-20240407準拠: accessionNumber (1698行目)
        String accessionNumber = attrs.getString(0x00080050); // AccessionNumber
        if (accessionNumber != null) {
            dict.put("accessionNumber", accessionNumber);
        }

        // HOROS-20240407準拠: referringPhysiciansName (1705行目)
        String referringPhysician = attrs.getString(0x00080090); // ReferringPhysicianName
        if (referringPhysician != null) {
            dict.put("referringPhysiciansName", referringPhysician);
        }

        // HOROS-20240407準拠: performingPhysiciansName (1706行目)
        String performingPhysician = attrs.getString(0x00081050); // PerformingPhysicianName
        if (performingPhysician != null) {
            dict.put("performingPhysiciansName", performingPhysician);
        }

        // HOROS-20240407準拠: institutionName (1707行目)
        String institutionName = attrs.getString(0x00080080); // InstitutionName
        if (institutionName != null) {
            dict.put("institutionName", institutionName);
        }

        // HOROS-20240407準拠: studyNumber (1703行目)
        String studyNumber = attrs.getString(0x00200010); // StudyID
        if (studyNumber != null) {
            dict.put("studyNumber", studyNumber);
        }

        // HOROS-20240407準拠: seriesID (1714行目) = SeriesInstanceUID
        String seriesInstanceUID = attrs.getString(0x0020000E); // SeriesInstanceUID
        if (seriesInstanceUID != null) {
            dict.put("seriesID", seriesInstanceUID);
            dict.put("seriesDICOMUID", seriesInstanceUID);
        }

        // HOROS-20240407準拠: seriesDescription (1715行目)
        String seriesDescription = attrs.getString(0x0008103E); // SeriesDescription
        if (seriesDescription == null || seriesDescription.trim().isEmpty()) {
            seriesDescription = attrs.getString(0x00400254); // PerformedProcedureStepDescription
        }
        if (seriesDescription == null || seriesDescription.trim().isEmpty()) {
            seriesDescription = attrs.getString(0x00181400); // AcquisitionDeviceProcessingDescription
        }
        if (seriesDescription == null || seriesDescription.trim().isEmpty()) {
            // HOROS-20240407準拠: seriesDescriptionがない場合はstudyDescriptionを使用
            seriesDescription = (String) dict.get("studyDescription");
        }
        if (seriesDescription == null || seriesDescription.trim().isEmpty()) {
            seriesDescription = "unnamed";
        }
        dict.put("seriesDescription", seriesDescription);

        // HOROS-20240407準拠: seriesNumber (1716行目)
        int seriesNumber = attrs.getInt(0x00200011, 0); // SeriesNumber
        dict.put("seriesNumber", seriesNumber);

        // HOROS-20240407準拠: modality (1696行目)
        String modality = attrs.getString(0x00080060); // Modality
        if (modality != null) {
            dict.put("modality", modality);
        }

        // HOROS-20240407準拠: protocolName (1718行目)
        String protocolName = attrs.getString(0x00181030); // ProtocolName
        if (protocolName != null) {
            dict.put("protocolName", protocolName);
        }

        // HOROS-20240407準拠: numberOfFrames (1720行目)
        int numberOfFrames = attrs.getInt(0x00280008, 1); // NumberOfFrames
        dict.put("numberOfFrames", numberOfFrames);

        // HOROS-20240407準拠: SOPUID (1721行目) = SOPInstanceUID
        String sopInstanceUID = attrs.getString(0x00080018); // SOPInstanceUID
        if (sopInstanceUID != null) {
            dict.put("SOPUID", sopInstanceUID);
        }

        // HOROS-20240407準拠: imageID (1722行目) = InstanceNumber
        int instanceNumber = attrs.getInt(0x00200013, 0); // InstanceNumber
        dict.put("imageID", instanceNumber);

        // HOROS-20240407準拠: sliceLocation (1724行目)
        double sliceLocation = attrs.getDouble(0x00201041, 0.0); // SliceLocation
        dict.put("sliceLocation", sliceLocation);

        // HOROS-20240407準拠: height (1726行目)
        int height = attrs.getInt(0x00280010, 0); // Rows
        dict.put("height", height);

        // HOROS-20240407準拠: width (1727行目)
        int width = attrs.getInt(0x00280011, 0); // Columns
        dict.put("width", width);

        // HOROS-20240407準拠: numberOfSeries (1728行目)
        // 通常は1だが、複数Seriesを含むファイルの場合は設定される
        dict.put("numberOfSeries", 1);

        return dict;
    }

    /**
     * PatientUIDを計算
     * HOROS-20240407準拠: DicomFile.patientUID (patientName, patientID,
     * patientBirthDateから計算)
     */
    private String calculatePatientUID(String patientName, String patientID, Date patientBirthDate) {
        return createPatientUID(patientName, patientID, patientBirthDate,
            com.oculus.jj.config.AppPreferences.getBool("database.uidPatientId", true),
            com.oculus.jj.config.AppPreferences.getBool("database.uidBirthDate", true),
            com.oculus.jj.config.AppPreferences.getBool("database.uidName", true));
        }

        static String createPatientUID(String patientName, String patientID, Date patientBirthDate,
            boolean useId, boolean useBirthDate, boolean useName) {
        java.util.List<String> components = new java.util.ArrayList<>();
        if (useId) components.add("ID=" + normalizeUIDComponent(patientID));
        if (useBirthDate) {
            String birth = patientBirthDate == null ? "" : new java.text.SimpleDateFormat("yyyyMMdd").format(patientBirthDate);
            components.add("DOB=" + birth);
        }
        if (useName) components.add("NAME=" + normalizeUIDComponent(patientName));
        if (components.isEmpty() || components.stream().allMatch(s -> s.endsWith("="))) return "PATIENT_UNKNOWN";

        if (useId && !useBirthDate && !useName && patientID != null && !patientID.trim().isEmpty()) {
            return "PATIENT_" + patientID.trim();
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(String.join("|", components).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder uid = new StringBuilder("PATIENT_");
            for (int i = 0; i < 16; i++) uid.append(String.format(java.util.Locale.ROOT, "%02x", digest[i]));
            return uid.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String normalizeUIDComponent(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * DICOMファイルから全メタデータを抽出してStudy/Series/Imageに設定
     * HOROS-20240407準拠
     * 
     * @deprecated extractDicomElementsを使用してください
     */
    @Deprecated
    private DicomMetadata extractAllMetadata(java.io.File file) {
        Attributes attrs = readDicomAttributes(file);
        if (attrs == null) {
            return null;
        }

        DicomMetadata meta = new DicomMetadata();

        // Study level
        meta.studyInstanceUID = attrs.getString(0x0020000D); // StudyInstanceUID
        meta.patientName = attrs.getString(0x00100010); // PatientName
        meta.patientID = attrs.getString(0x00100020); // PatientID

        // HOROS-20240407準拠: DicomFileDCMTKCategory.mm 517-531行目を完全に写経
        // Study Description
        // if (dataset->findAndGetString(DCM_StudyDescription, string, OFFalse).good()
        // && string != NULL)
        // study = [[DicomFile stringWithBytes: (char*) string encodings:encoding]
        // retain];
        // else
        // {
        // DcmItem *item = NULL;
        // if (dataset->findAndGetSequenceItem(DCM_ProcedureCodeSequence, item).good())
        // {
        // if( item->findAndGetString(DCM_CodeMeaning, string, OFFalse).good() && string
        // != NULL)
        // study = [[DicomFile stringWithBytes: (char*) string encodings:encoding]
        // retain];
        // }
        // }
        // if( !study)
        // study = [[NSString alloc] initWithString: @"unnamed"];
        String studyDescription = attrs.getString(0x00081030); // StudyDescription
        // HOROS-20240407準拠: ProcedureCodeSequence内のCodeMeaningをフォールバックとして使用
        // DICOMタグ(0008,1032) ProcedureCodeSequence
        // DICOMタグ(0008,0104) CodeMeaning
        if (studyDescription == null || studyDescription.trim().isEmpty()) {
            org.dcm4che3.data.Sequence procedureCodeSeq = attrs.getSequence(0x00081032); // ProcedureCodeSequence
            if (procedureCodeSeq != null && !procedureCodeSeq.isEmpty()) {
                Attributes firstItem = procedureCodeSeq.get(0);
                if (firstItem != null) {
                    studyDescription = firstItem.getString(0x00080104); // CodeMeaning
                }
            }
        }
        // HOROS-20240407準拠: if( !study) study = [[NSString alloc] initWithString:
        // @"unnamed"];
        if (studyDescription == null || studyDescription.trim().isEmpty()) {
            studyDescription = "unnamed";
        }
        meta.studyDescription = studyDescription;
        // HOROS-20240407準拠: StudyDateとStudyTimeを組み合わせる
        meta.studyDate = attrs.getDate(0x00080020); // StudyDate
        meta.accessionNumber = attrs.getString(0x00080050); // AccessionNumber
        meta.referringPhysician = attrs.getString(0x00080090); // ReferringPhysicianName
        meta.institutionName = attrs.getString(0x00080080); // InstitutionName

        // Series level
        meta.seriesInstanceUID = attrs.getString(0x0020000E); // SeriesInstanceUID
        // HOROS-20240407準拠: DicomFileDCMTKCategory.mm 594-604行目
        // Series Description
        // if (dataset->findAndGetString(DCM_SeriesDescription, string, OFFalse).good()
        // && string != NULL)
        // serie = [[DicomFile stringWithBytes: (char*) string encodings:encoding]
        // retain];
        // else if (dataset->findAndGetString(DCM_PerformedProcedureStepDescription,
        // string, OFFalse).good() && string != NULL)
        // serie = [[DicomFile stringWithBytes: (char*) string encodings:encoding]
        // retain];
        // else if
        // (dataset->findAndGetString(DCM_AcquisitionDeviceProcessingDescription,
        // string, OFFalse).good() && string != NULL)
        // serie = [[DicomFile stringWithBytes: (char*) string encodings:encoding]
        // retain];
        // else if( serie == nil)
        // serie = [[NSString alloc] initWithString: @"unnamed"];
        // HOROS-20240407準拠: DicomFileDCMTKCategory.mm 595-602行目を完全に写経
        // if (dataset->findAndGetString(DCM_SeriesDescription, string, OFFalse).good()
        // && string != NULL)
        // serie = [[DicomFile stringWithBytes: (char*) string encodings:encoding]
        // retain];
        // else if (dataset->findAndGetString(DCM_PerformedProcedureStepDescription,
        // string, OFFalse).good() && string != NULL)
        // serie = [[DicomFile stringWithBytes: (char*) string encodings:encoding]
        // retain];
        // else if
        // (dataset->findAndGetString(DCM_AcquisitionDeviceProcessingDescription,
        // string, OFFalse).good() && string != NULL)
        // serie = [[DicomFile stringWithBytes: (char*) string encodings:encoding]
        // retain];
        // else if( serie == nil)
        // serie = [[NSString alloc] initWithString: @"unnamed"];
        String seriesDescription = attrs.getString(0x0008103E); // SeriesDescription
        // HOROS-20240407準拠: else if
        // (dataset->findAndGetString(DCM_PerformedProcedureStepDescription, string,
        // OFFalse).good() && string != NULL)
        // DICOMタグ(0040,0254) PerformedProcedureStepDescription
        if (seriesDescription == null || seriesDescription.trim().isEmpty()) {
            seriesDescription = attrs.getString(0x00400254);
        }
        // HOROS-20240407準拠: else if
        // (dataset->findAndGetString(DCM_AcquisitionDeviceProcessingDescription,
        // string, OFFalse).good() && string != NULL)
        // DICOMタグ(0018,1400) AcquisitionDeviceProcessingDescription
        if (seriesDescription == null || seriesDescription.trim().isEmpty()) {
            seriesDescription = attrs.getString(0x00181400);
        }
        // HOROS-20240407準拠: else if( serie == nil) serie = [[NSString alloc]
        // initWithString: @"unnamed"];
        if (seriesDescription == null || seriesDescription.trim().isEmpty()) {
            seriesDescription = "unnamed";
        }
        meta.seriesDescription = seriesDescription;
        meta.seriesNumber = attrs.getInt(0x00200011, 0); // SeriesNumber
        meta.modality = attrs.getString(0x00080060); // Modality
        // HOROS-20240407準拠: SeriesDateとSeriesTimeを組み合わせる
        meta.seriesDate = attrs.getDate(0x00080021); // SeriesDate
        if (meta.seriesDate == null) {
            meta.seriesDate = attrs.getDate(0x00080020); // StudyDate
        }

        // Image level
        meta.sopInstanceUID = attrs.getString(0x00080018); // SOPInstanceUID
        meta.instanceNumber = attrs.getInt(0x00200013, 0); // InstanceNumber
        meta.sliceLocation = attrs.getDouble(0x00201041, 0.0); // SliceLocation
        meta.numberOfFrames = attrs.getInt(0x00280008, 1); // NumberOfFrames

        // Window level/width
        meta.windowCenter = attrs.getDouble(0x00281050, 0.0); // WindowCenter
        meta.windowWidth = attrs.getDouble(0x00281051, 0.0); // WindowWidth

        return meta;
    }

    /**
     * ディレクトリ内のファイルを再帰的に収集
     * HOROS-20240407準拠
     */
    private void collectFilesRecursively(java.io.File directory, List<String> files) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }

        java.io.File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        for (java.io.File child : children) {
            String name = child.getName();
            // 隠しファイル・フォルダをスキップ
            if (name.startsWith(".")) {
                continue;
            }

            if (child.isDirectory()) {
                collectFilesRecursively(child, files);
            } else if (child.isFile()) {
                files.add(child.getAbsolutePath());
            }
        }
    }

    /**
     * DICOMメタデータを保持する内部クラス
     */
    private static class DicomMetadata {
        // Study
        String studyInstanceUID;
        String patientName;
        String patientID;
        String studyDescription;
        Date studyDate;
        String accessionNumber;
        String referringPhysician;
        String institutionName;

        // Series
        String seriesInstanceUID;
        String seriesDescription;
        int seriesNumber;
        String modality;
        Date seriesDate;

        // Image
        String sopInstanceUID;
        int instanceNumber;
        double sliceLocation;
        int numberOfFrames;
        double windowCenter;
        double windowWidth;
    }

    /**
     * Studyの画像数を更新
     * HOROS-20240407準拠: Studyの全てのSeriesの画像数を合計
     */
    private void updateStudyNumberOfImages(DicomStudy study) {
        if (study == null) {
            return;
        }

        int totalImages = 0;
        java.util.Set<DicomSeries> seriesSet = study.getSeries();
        if (seriesSet != null) {
            try {
                for (DicomSeries series : seriesSet) {
                    Integer numberOfImages = series.getNumberOfImages();
                    if (numberOfImages != null) {
                        totalImages += numberOfImages;
                    }
                }
            } catch (java.util.ConcurrentModificationException e) {
                // マルチスレッド環境での競合は無視（ログを抑制）
                // コピーを作成して再試行
                try {
                    for (DicomSeries series : new java.util.ArrayList<>(seriesSet)) {
                        Integer numberOfImages = series.getNumberOfImages();
                        if (numberOfImages != null) {
                            totalImages += numberOfImages;
                        }
                    }
                } catch (Exception e2) {
                    // 再試行も失敗した場合は無視
                }
            }
        }

        study.setNumberOfImages(totalImages);
    }

    /**
     * 辞書で記述されたファイルを追加
     */
    public List<Object> addFilesDescribedInDictionaries(List<Object> dicomFilesArray,
            boolean postNotifications,
            boolean rereadExistingItems,
            boolean generatedByOsiriX) {
        return addFilesDescribedInDictionaries(dicomFilesArray, postNotifications,
                rereadExistingItems, generatedByOsiriX, true);
    }

    /**
     * 辞書で記述されたファイルを追加（返却配列オプション付き）
     */
    public List<Object> addFilesDescribedInDictionaries(List<Object> dicomFilesArray,
            boolean postNotifications,
            boolean rereadExistingItems,
            boolean generatedByOsiriX,
            boolean returnArray) {
        return addFilesDescribedInDictionaries(dicomFilesArray, postNotifications,
                rereadExistingItems, generatedByOsiriX,
                false, returnArray);
    }

    // ========== 受信関連メソッド ==========

    /**
     * ファイルシステムの空き容量制限に達したかどうか
     */
    public boolean isFileSystemFreeSizeLimitReached() {
        return isFileSystemFreeSizeLimitReached;
    }

    /**
     * インポートするファイルがあるかどうか
     */
    public boolean hasFilesToImport() {
        return false;
    }

    /**
     * 受信ディレクトリからファイルをインポート
     */
    public int importFilesFromIncomingDir() {
        return importFilesFromIncomingDir(null);
    }

    /**
     * 受信ディレクトリからファイルをインポート（GUI表示オプション付き）
     */
    public int importFilesFromIncomingDir(Boolean showGUI) {
        return importFilesFromIncomingDir(showGUI, 0);
    }

    private volatile String lastImportDiagnostic = "";

    public String getLastImportDiagnostic() {
        return lastImportDiagnostic;
    }

    /**
     * 受信ディレクトリからファイルをインポート（リスナー圧縮設定付き）
     */
    public int importFilesFromIncomingDir(Boolean showGUI, int listenerCompressionSettings) {
        importFilesFromIncomingDirLock.lock();
        try {
            return importFilesFromIncomingDirLocked(showGUI, listenerCompressionSettings);
        } finally {
            importFilesFromIncomingDirLock.unlock();
        }
    }

    private int importFilesFromIncomingDirLocked(Boolean showGUI, int listenerCompressionSettings) {
        String incomingPath = incomingDirPath();
        File incomingDir = new File(incomingPath);
        if (!incomingDir.exists() || !incomingDir.isDirectory()) {
            return 0;
        }

        File[] files = incomingDir.listFiles();
        if (files == null || files.length == 0) {
            return 0;
        }

        List<String> paths = new ArrayList<>();
        String dataDir = getDataDirPath();
        File dataDirFile = new File(dataDir);
        if (!dataDirFile.exists()) {
            dataDirFile.mkdirs();
        }

        for (File f : files) {
            if (f.isFile() && !f.getName().startsWith(".") && !f.getName().contains(".part-")) {
                // HOROS-20240407準拠: インポートファイルをデータベースフォルダに移動

                String originalName = f.getName();

                // UIDベースの名前には拡張子がないため、.dcm を付与（DicomStoreSCP準拠）

                String originalLower = originalName.toLowerCase();
                String destName;
                if (originalLower.endsWith(".dcm") || originalLower.endsWith(".ima")) {
                    destName = originalName;
                } else {
                    int lastDot = originalName.lastIndexOf('.');
                    if (lastDot < 0) {
                        destName = originalName + ".dcm";
                    } else {
                        String ext = originalName.substring(lastDot).toLowerCase();
                        if (!ext.equals(".dcm") && !ext.equals(".ima")) {
                            destName = originalName.substring(0, lastDot) + ".dcm";
                        } else {
                            destName = originalName;
                        }
                    }
                }

                File destFile = new File(dataDir, destName);

                try {
                    java.nio.file.Files.move(f.toPath(), destFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    paths.add(destFile.getAbsolutePath());
                } catch (Exception e) {

                    // f.getAbsolutePath(), e);
                    // 移動に失敗した場合は元のパスで試行
                    paths.add(f.getAbsolutePath());
                }
            }
        }

        if (paths.isEmpty()) {
            return 0;
        }

        // HOROS-20240407準拠: addFilesAtPathsを呼び出す
        // postNotifications:true, dicomOnly:true (or false?),
        // rereadExistingItems:false, generatedByOsiriX:false, importedFiles:true,
        // returnArray:false
        addFilesAtPaths(paths, true, true, false, false, true, false);

        return paths.size();
    }

    /**
     * 圧縮スレッドを待機
     */
    public boolean waitForCompressThread() {
        return true;
    }

    /**
     * 既にインポート中でない限り、受信ディレクトリからのファイルインポートを開始
     */
    public void initiateImportFilesFromIncomingDirUnlessAlreadyImporting() {
        if (importFilesFromIncomingDirLock.isLocked()) {
            return;
        }

        Thread thread = new Thread(this::importFilesFromIncomingDirThread, "ImportFromIncoming");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 受信ディレクトリからファイルをインポート（スレッド）
     */
    public void importFilesFromIncomingDirThread() {
        if (!importFilesFromIncomingDirLock.tryLock()) {
            return;
        }
        try {
            importFilesFromIncomingDir(false, 0);
        } finally {
            importFilesFromIncomingDirLock.unlock();
        }
    }

    /**
     * ユーザー設定と同期して受信ディレクトリからのファイルインポートタイマーを設定（非推奨）
     */
    @Deprecated
    public static void syncImportFilesFromIncomingDirTimerWithUserDefaults() {
    }

    // ========== 圧縮/解凍メソッド ==========

    /**
     * パスのファイルを圧縮
     */
    public boolean compressFilesAtPaths(List<String> paths) {
        return compressFilesAtPaths(paths, null);
    }

    /**
     * パスのファイルを圧縮（出力先ディレクトリ指定）
     */
    public boolean compressFilesAtPaths(List<String> paths, String destDir) {
        return false;
    }

    /**
     * パスのファイルを解凍
     */
    public boolean decompressFilesAtPaths(List<String> paths) {
        return decompressFilesAtPaths(paths, null);
    }

    /**
     * パスのファイルを解凍（出力先ディレクトリ指定）
     */
    public boolean decompressFilesAtPaths(List<String> paths, String destDir) {
        return false;
    }

    /**
     * パスのファイルの圧縮を開始
     */
    public void initiateCompressFilesAtPaths(List<String> paths) {
        initiateCompressFilesAtPaths(paths, null);
    }

    /**
     * パスのファイルの圧縮を開始（出力先ディレクトリ指定）
     */
    public void initiateCompressFilesAtPaths(List<String> paths, String destDir) {
    }

    /**
     * パスのファイルの解凍を開始
     */
    public void initiateDecompressFilesAtPaths(List<String> paths) {
        initiateDecompressFilesAtPaths(paths, null);
    }

    /**
     * パスのファイルの解凍を開始（出力先ディレクトリ指定）
     */
    public void initiateDecompressFilesAtPaths(List<String> paths, String destDir) {
    }

    /**
     * パスのファイルを処理（モード指定）
     */
    public void processFilesAtPaths(List<String> paths, String destDir, int mode) {
    }

    // ========== その他のメソッド ==========

    /**
     * 再構築が許可されているかどうか
     */
    public boolean rebuildAllowed() {
        return true;
    }

    /**
     * 再構築
     */
    public void rebuild() {
        rebuild(false);
    }

    /**
     * 再構築（完全再構築オプション付き）
     */
    public void rebuild(boolean complete) {
    }

    /**
     * スタディの既存レポートをチェック
     */
    public void checkForExistingReportForStudy(Object study) {
    }

    /**
     * DICOMSRとのレポート一貫性をチェック
     */
    public void checkReportsConsistencyWithDICOMSR() {
    }

    /**
     * SQLファイルを再構築
     */
    public void rebuildSqlFile() {
    }

    /**
     * HTMLテンプレートをチェック
     */
    public void checkForHtmlTemplates() {
    }

    /**
     * オートルーティングが許可されているかどうか
     */
    public boolean allowAutoroutingWithPostNotifications(boolean postNotifications,
            boolean rereadExistingItems) {
        return true;
    }

    /**
     * ルーティングルールを画像に適用するアラート
     */
    public void alertToApplyRoutingRules(List<Object> routingRules, List<Object> images) {
    }

    /**
     * ファイルをコピー（スレッド）
     * HOROS-20240407準拠: - (void)copyFilesThread:(NSDictionary*)dict (2610-2848行目)
     */
    public void copyFilesThread(java.util.Map<String, Object> dict) {

        // HOROS-20240407準拠: @autoreleasepool (2612行目)
        try {
            // HOROS-20240407準拠: NSOperationQueue* queue = [[[NSOperationQueue alloc] init]
            // autorelease]; [queue setMaxConcurrentOperationCount:1]; (2614-2615行目)
            java.util.concurrent.ExecutorService queue = java.util.concurrent.Executors.newSingleThreadExecutor();

            // HOROS-20240407準拠: BOOL onlyDICOM = [[dict objectForKey: @"onlyDICOM"]
            // boolValue], copyFiles = [[dict objectForKey: @"copyFiles"] boolValue];
            // (2617行目)
            Boolean onlyDICOM = dict.containsKey("onlyDICOM") ? (Boolean) dict.get("onlyDICOM") : true;
            Boolean copyFiles = dict.containsKey("copyFiles") ? (Boolean) dict.get("copyFiles") : false;

            // copyFiles);

            // HOROS-20240407準拠: __block BOOL studySelected = NO; (2618行目)
            java.util.concurrent.atomic.AtomicBoolean studySelected = new java.util.concurrent.atomic.AtomicBoolean(
                    false);

            // HOROS-20240407準拠: copyFilesThreadを呼び出したスレッド（メインのインポートスレッド）を取得
            // このスレッドは既にThreadsManagerに登録されている（BrowserController.copyFilesIntoDatabaseIfNeededで登録済み）
            Thread mainImportThread = Thread.currentThread();
            com.oculus.jj.threads.ThreadsManager tm = com.oculus.jj.threads.ThreadsManager.defaultManager();

            // HOROS-20240407準拠: メインのインポートスレッドの初期ステータスを設定
            // 名前は既に「Indexing
            // files...」に設定されている（BrowserController.copyFilesIntoDatabaseIfNeededで設定済み）
            tm.setThreadStatus(mainImportThread, "Indexing files...");
            tm.setThreadProgress(mainImportThread, -1.0); // indeterminateモード

            // HOROS-20240407準拠: NSArray *filesInput = [[dict objectForKey: @"filesInput"]
            // sortedArrayUsingSelector:@selector(compare:)]; (2619行目)
            @SuppressWarnings("unchecked")
            List<String> filesInput = (List<String>) dict.get("filesInput");
            if (filesInput == null || filesInput.isEmpty()) {

                return;
            }

            List<String> sortedFiles = new java.util.ArrayList<>(filesInput);
            sortedFiles.sort(String::compareTo);

            // HOROS-20240407準拠: for( int i = 0; i < [filesInput count];) (2621行目)
            int i = 0;
            while (i < sortedFiles.size()) {
                // HOROS-20240407準拠: if ([[NSThread currentThread] isCancelled]) break; (2623行目)
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                // HOROS-20240407準拠: @autoreleasepool (2625行目)
                try {
                    // HOROS-20240407準拠: @try (2627行目)
                    try {
                        // HOROS-20240407準拠: NSMutableArray *copiedFiles = [NSMutableArray array];
                        // (2629行目)
                        List<String> copiedFiles = new java.util.ArrayList<>();

                        // HOROS-20240407準拠: NSTimeInterval lastGUIUpdate = 0; NSTimeInterval
                        // twentySeconds = [NSDate timeIntervalSinceReferenceDate] + 5; (2630-2631行目)
                        long lastGUIUpdate = 0;
                        long fiveSeconds = System.currentTimeMillis() + 5000; // actually fiveSeconds

                        // HOROS-20240407準拠: for( ; i < [filesInput count] && twentySeconds > [NSDate
                        // timeIntervalSinceReferenceDate]; i++) (2633行目)
                        for (; i < sortedFiles.size() && System.currentTimeMillis() < fiveSeconds; i++) {
                            // HOROS-20240407準拠: if ([[NSThread currentThread] isCancelled]) break; (2635行目)
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            // HOROS-20240407準拠: if( [NSDate timeIntervalSinceReferenceDate] - lastGUIUpdate
                            // > 1) (2637行目)
                            long now = System.currentTimeMillis();
                            if (now - lastGUIUpdate > 1000) {
                                lastGUIUpdate = now;

                                // HOROS-20240407準拠: [NSThread currentThread].status =
                                // N2LocalizedSingularPluralCount((long)filesInput.count-i,
                                // NSLocalizedString(@"file left", nil), NSLocalizedString(@"files left", nil));
                                // (2641行目)
                                // HOROS-20240407準拠: [NSThread currentThread].progress =
                                // float(i)/filesInput.count; (2642行目)
                                // HOROS-20240407準拠: メインのインポートスレッドのステータスと進捗を更新
                                long remaining = sortedFiles.size() - i;
                                String statusText = remaining == 1 ? "file left" : "files left";
                                tm.setThreadStatus(mainImportThread, remaining + " " + statusText);
                                tm.setThreadProgress(mainImportThread, (double) i / sortedFiles.size());
                            }

                            // HOROS-20240407準拠: NSString *srcPath = [filesInput objectAtIndex: i], *dstPath
                            // = nil; (2645行目)
                            String srcPath = sortedFiles.get(i);

                            // HOROS-20240407準拠: if( copyFiles) (2647行目)
                            if (copyFiles) {
                                java.io.File file = new java.io.File(srcPath);
                                if (file.exists() && file.isFile()) {
                                    try {
                                        copiedFiles.add(copyImportedFile(srcPath));
                                    } catch (Exception ex) {
                                        logger.warn("Failed to copy imported file into database: {}", srcPath, ex);
                                    }
                                }
                            } else {
                                // HOROS-20240407準拠: copyFiles=falseの場合 (2703-2730行目)
                                java.io.File file = new java.io.File(srcPath);
                                if (file.exists() && file.isFile()) {
                                    Boolean mountedVolume = dict.containsKey("mountedVolume")
                                            ? (Boolean) dict.get("mountedVolume")
                                            : false;

                                    // HOROS-20240407準拠: if( [[dict objectForKey: @"mountedVolume"] boolValue])
                                    // (2707行目)
                                    if (mountedVolume) {
                                        // HOROS-20240407準拠: Pre-load for CD/DVD in cache (2709-2719行目)
                                        copiedFiles.add(srcPath);
                                    } else {
                                        // HOROS-20240407準拠: 通常の場合はそのまま追加 (2721-2729行目)
                                        // HOROS-20240407準拠: if( [[NSUserDefaults standardUserDefaults] boolForKey:
                                        // @"validateFilesBeforeImporting"] && [[dict objectForKey: @"mountedVolume"]
                                        // boolValue] == NO) (2723行目)
                                        // HOROS-20240407準拠: [copiedFiles addObject: srcPath]; (2728行目)
                                        copiedFiles.add(srcPath);
                                    }
                                }
                            }

                            // HOROS-20240407準拠: if ([NSThread currentThread].isCancelled) break; (2733行目)
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }
                        }

                        // HOROS-20240407準拠: [queue addOperationWithBlock:^{ (2737行目)
                        final List<String> finalCopiedFiles = copiedFiles;

                        // finalCopiedFiles.size());

                        queue.submit(() -> {
                            Thread workerThread = null;
                            try {

                                // // finalCopiedFiles.size());
                                // HOROS-20240407準拠: NSThread* thread = [NSThread currentThread]; thread.name =
                                // NSLocalizedString(@"Adding files...", nil); [[ThreadsManager defaultManager]
                                // addThreadAndStart:thread]; (2738-2740行目)
                                workerThread = Thread.currentThread();
                                workerThread.setName("Adding files...");
                                tm.addThreadAndStart(workerThread);

                                // HOROS-20240407準拠: BOOL succeed = YES; (2742行目)
                                boolean succeed = true;

                                // HOROS-20240407準拠: #ifndef OSIRIX_LIGHT thread.status =
                                // NSLocalizedString(@"Validating the files...", nil); if( [[NSUserDefaults
                                // standardUserDefaults] boolForKey: @"validateFilesBeforeImporting"] && [[dict
                                // objectForKey: @"mountedVolume"] boolValue] == NO) succeed = [DicomDatabase
                                // testFiles: copiedFiles]; #endif (2744-2747行目)

                                // HOROS-20240407準拠: NSArray *objects = nil; (2750行目)
                                // objects変数は後でfinalObjectsとして使用されるため、ここでは宣言しない

                                // HOROS-20240407準拠: if( succeed) (2752行目)
                                if (succeed) {
                                    // HOROS-20240407準拠: thread.status = NSLocalizedString(@"Indexing the files...",
                                    // nil); (2754行目)
                                    tm.setThreadStatus(workerThread, "Indexing the files...");

                                    // HOROS-20240407準拠: DicomDatabase *idatabase = self.isMainDatabase?
                                    // self.independentDatabase : [self.mainDatabase independentDatabase]; (2756行目)
                                    DicomDatabase idatabase = this;

                                    // HOROS-20240407準拠: objects = [idatabase addFilesAtPaths:copiedFiles
                                    // postNotifications:YES dicomOnly:onlyDICOM rereadExistingItems:YES
                                    // generatedByOsiriX:NO importedFiles:YES returnArray:YES]; (2758行目)

                                    // // finalCopiedFiles.size());
                                    final List<Object> finalObjects = idatabase.addFilesAtPaths(finalCopiedFiles, true,
                                            onlyDICOM, true, false, true, true);
                                    if (finalObjects != null && !finalObjects.isEmpty()) {
                                        routeImportedImages(finalObjects);
                                    }

                                    // // finalObjects != null ? finalObjects.size() : 0);

                                    // HOROS-20240407準拠: DicomDatabase* mdatabase = self.isMainDatabase? self :
                                    // self.mainDatabase; if( [[BrowserController currentBrowser] database] ==
                                    // mdatabase && [[dict objectForKey:@"addToAlbum"] boolValue]) (2760-2761行目)

                                    // HOROS-20240407準拠: if ([objects count]) (2793行目)
                                    if (finalObjects != null && !finalObjects.isEmpty()) {
                                        // HOROS-20240407準拠: @try (2795行目)
                                        try {
                                            // HOROS-20240407準拠: BrowserController* bc = [BrowserController
                                            // currentBrowser]; (2797行目)
                                            // HOROS-20240407準拠: UIを更新するため、BrowserControllerの参照を取得
                                            javax.swing.SwingUtilities.invokeLater(() -> {
                                                try {
                                                    // HOROS-20240407準拠: currentBrowserを使用してUIを更新
                                                    com.oculus.jj.ui.BrowserController bc = com.oculus.jj.ui.BrowserController
                                                            .getCurrentBrowser();
                                                    if (bc != null) {
                                                        // HOROS-20240407準拠: DBリストとマトリックスを更新
                                                        bc.outlineViewRefresh();
                                                        bc.refreshMatrix(bc);

                                                        // HOROS-20240407準拠: if( studySelected == NO) { studySelected =
                                                        // YES; if ([[dict objectForKey:@"selectStudy"] boolValue]) [bc
                                                        // performSelectorOnMainThread:@selector(selectStudyWithObjectID:)
                                                        // withObject: [objects objectAtIndex:0] waitUntilDone:NO]; }
                                                        // (2801-2805行目)
                                                        if (!studySelected.getAndSet(true)) {
                                                            Boolean selectStudy = dict.containsKey("selectStudy")
                                                                    ? (Boolean) dict.get("selectStudy")
                                                                    : false;
                                                            if (selectStudy && !finalObjects.isEmpty()) {
                                                                // Study選択処理は必要に応じて実装
                                                            }
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    // HOROS-20240407準拠: @catch (NSException *e) {
                                                    // N2LogExceptionWithStackTrace(e); } (2808-2809行目)

                                                }
                                            });
                                        } catch (Exception e) {
                                            // HOROS-20240407準拠: @catch (NSException* e) {
                                            // N2LogExceptionWithStackTrace(e); } (2812-2813行目)

                                        }
                                    } else {
                                        // オブジェクトが空の場合でもUIを更新（エラーハンドリングのため）
                                        javax.swing.SwingUtilities.invokeLater(() -> {
                                            try {
                                                com.oculus.jj.ui.BrowserController bc = com.oculus.jj.ui.BrowserController
                                                        .getCurrentBrowser();
                                                if (bc != null) {
                                                    bc.outlineViewRefresh();
                                                }
                                            } catch (Exception e) {

                                            }
                                        });
                                    }
                                } else if (copyFiles) {
                                    // HOROS-20240407準拠: else if( copyFiles) { for( NSString * f in copiedFiles)
                                    // [[NSFileManager defaultManager]removeItemAtPath: f error: nil]; }
                                    // (2787-2790行目)
                                    for (String f : finalCopiedFiles) {
                                        java.io.File file = new java.io.File(f);
                                        if (file.exists()) {
                                            file.delete();
                                        }
                                    }
                                }

                                // HOROS-20240407準拠: [[ThreadsManager defaultManager] removeThread:thread];
                                // (2817行目)
                                // HOROS-20240407準拠: NSOperationQueue threads don't finish after ablock
                                // execution, they're recycled
                                // HOROS-20240407準拠: ExecutorServiceのスレッドも再利用されるため、各バッチの最後でremoveThreadを呼ぶ
                            } catch (Exception e) {
                                // ConcurrentModificationException、ArrayIndexOutOfBoundsException、NullPointerExceptionは抑制
                                if (!(e instanceof java.util.ConcurrentModificationException) &&
                                        !(e instanceof ArrayIndexOutOfBoundsException) &&
                                        !(e instanceof NullPointerException)) {

                                }
                            } finally {
                                // HOROS-20240407準拠: 例外が発生しても必ずremoveThreadを呼ぶ（重複登録を防ぐため）
                                if (workerThread != null) {
                                    try {
                                        tm.removeThread(workerThread);
                                    } catch (Exception e) {

                                    }
                                }
                            }
                        });

                        // HOROS-20240407準拠: if( [NSThread currentThread].isCancelled) break; (2820行目)
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }
                    } catch (Exception e) {
                        // HOROS-20240407準拠: @catch (NSException * e) { N2LogExceptionWithStackTrace(e);
                        // } (2823-2825行目)

                    }
                } catch (Exception e) {

                }
            }

            // HOROS-20240407準拠: if (queue.operationCount) { [NSThread currentThread].status
            // = NSLocalizedString(@"Waiting for subtasks to complete...", nil); while
            // (queue.operationCount) { [NSThread sleepForTimeInterval:0.05]; if( [[NSThread
            // currentThread] isCancelled]) [queue cancelAllOperations]; } } (2830-2839行目)
            // HOROS-20240407準拠: メインのインポートスレッドのステータスを「Waiting for subtasks to
            // complete...」に更新
            // 注意: ExecutorServiceにはoperationCountがないため、shutdown()前にステータスを更新
            tm.setThreadStatus(mainImportThread, "Waiting for subtasks to complete...");
            queue.shutdown();
            // HOROS-20240407準拠: メインのインポートスレッドのステータスを「Waiting for subtasks to
            // complete...」に更新
            tm.setThreadStatus(mainImportThread, "Waiting for subtasks to complete...");
            try {
                while (!queue.awaitTermination(50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    if (Thread.currentThread().isInterrupted()) {
                        queue.shutdownNow();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                queue.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // HOROS-20240407準拠: if( [[dict objectForKey: @"ejectCDDVD"] boolValue] == YES
            // && copyFiles == YES) { if( [[NSUserDefaults standardUserDefaults] boolForKey:
            // @"EJECTCDDVD"]) [[NSWorkspace sharedWorkspace] unmountAndEjectDeviceAtPath:
            // [filesInput objectAtIndex:0]]; } (2842-2845行目)

        } catch (Exception e) {
            // エラーは無視するか、適切にログ出力を行う（コンソールへの直接出力は避ける）
        }
    }

    private String copyImportedFile(String sourcePath) throws java.io.IOException {
        java.nio.file.Path source = java.nio.file.Path.of(sourcePath).toRealPath();
        java.nio.file.Path databaseRoot = java.nio.file.Path.of(dataDirPath()).toAbsolutePath().normalize();
        if (source.startsWith(databaseRoot)) return source.toString();

        String parent = source.getParent() == null ? "root" : source.getParent().toString();
        byte[] digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(parent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new java.io.IOException("SHA-256 is unavailable", e);
        }
        StringBuilder folderKey = new StringBuilder();
        for (int i = 0; i < 8; i++) folderKey.append(String.format(java.util.Locale.ROOT, "%02x", digest[i]));
        java.nio.file.Path destinationDirectory = databaseRoot.resolve("Imported").resolve(folderKey.toString());
        java.nio.file.Files.createDirectories(destinationDirectory);

        String fileName = source.getFileName().toString();
        String stem = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            stem = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }
        java.nio.file.Path destination = destinationDirectory.resolve(fileName);
        int suffix = 1;
        while (java.nio.file.Files.exists(destination)) {
            if (java.nio.file.Files.isSameFile(source, destination)) return destination.toString();
            destination = destinationDirectory.resolve(stem + "-" + suffix++ + extension);
        }
        java.nio.file.Files.copy(source, destination, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        return destination.toString();
    }

    private void routeImportedImages(List<Object> importedObjects) {
        if (!com.oculus.jj.config.AppPreferences.getBool("routing.active", false)) return;
        String rulesJson = com.oculus.jj.config.AppPreferences.getStr("routing.rules", "");
        if (rulesJson.isBlank()) return;

        String[][] rules;
        try {
            rules = new com.fasterxml.jackson.databind.ObjectMapper().readValue(rulesJson, String[][].class);
        } catch (Exception ex) {
            logger.warn("Cannot parse automatic routing rules", ex);
            return;
        }
        List<DicomNode> nodes = DicomNodeManager.getInstance().getNodes();
        List<String> failures = new ArrayList<>();
        for (Object object : importedObjects) {
            if (!(object instanceof DicomImage image)) continue;
            String path = image.completePath();
            if (path == null || path.isBlank()) continue;
            for (String[] rule : rules) {
                if (rule.length < 4 || !Boolean.parseBoolean(rule[0]) || !matchesRoutingFilter(rule[1], image)) continue;
                DicomNode destination = nodes.stream().filter(node -> routingDestinationMatches(rule[2], node))
                        .findFirst().orElse(null);
                if (destination == null) {
                    failures.add("No DICOM node matches routing destination: " + rule[2]);
                    continue;
                }
                try {
                    com.oculus.jj.model.DicomService.getInstance().store(destination, new File(path));
                } catch (Exception ex) {
                    failures.add(destination.getName() + ": " + ex.getMessage());
                }
            }
        }
        if (!failures.isEmpty() && com.oculus.jj.config.AppPreferences.getBool("routing.showError", true)) {
            String message = String.join("\n", failures);
            javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(null, message,
                    "AutoRouting", javax.swing.JOptionPane.ERROR_MESSAGE));
        }
    }

    private boolean routingDestinationMatches(String target, DicomNode node) {
        if (target == null || node == null) return false;
        String normalized = target.trim();
        return normalized.equalsIgnoreCase(node.getName()) || normalized.equalsIgnoreCase(node.getAeTitle())
                || normalized.equalsIgnoreCase(node.getAddress() + ":" + node.getPort());
    }

    private boolean matchesRoutingFilter(String filter, DicomImage image) {
        if (filter == null || filter.isBlank() || "*".equals(filter.trim())) return true;
        for (String condition : filter.split("[;,&]")) {
            String[] pair = condition.trim().split("=", 2);
            if (pair.length != 2) return false;
            String actual = switch (pair[0].trim().toLowerCase(java.util.Locale.ROOT)) {
                case "modality" -> image.getModality();
                case "patientid" -> image.getPatientID();
                case "studydescription" -> image.getStudyDescription();
                case "seriesdescription" -> image.getSeriesDescription();
                case "accessionnumber" -> image.getAccessionNumber();
                case "studyuid" -> image.getStudyInstanceUID();
                default -> null;
            };
            if (actual == null || !routingValueMatches(actual, pair[1].trim())) return false;
        }
        return true;
    }

    static boolean routingValueMatches(String actual, String expected) {
        String value = actual.toLowerCase(java.util.Locale.ROOT);
        String pattern = expected.toLowerCase(java.util.Locale.ROOT);
        if (!pattern.contains("*")) return value.equals(pattern);
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char character = pattern.charAt(i);
            regex.append(character == '*' ? ".*" : java.util.regex.Pattern.quote(String.valueOf(character)));
        }
        regex.append('$');
        return value.matches(regex.toString());
    }

    // ========== エンティティ関連メソッド ==========

    /**
     * 画像エンティティを取得
     */
    public Object imageEntity() {
        return null;
    }

    /**
     * シリーズエンティティを取得
     */
    public Object seriesEntity() {
        return null;
    }

    /**
     * スタディエンティティを取得
     */
    public Object studyEntity() {
        return null;
    }

    /**
     * アルバムエンティティを取得
     */
    public Object albumEntity() {
        return null;
    }

    /**
     * ログエントリエンティティを取得
     */
    public Object logEntryEntity() {
        return null;
    }

    /**
     * マネージドオブジェクトコンテキストを取得
     */
    public Object managedObjectContext() {
        return null;
    }

    /**
     * patientUIDでStudyを検索
     * HOROS-20240407準拠: [idatabase objectsForEntity: idatabase.studyEntity
     * predicate: [NSPredicate predicateWithFormat: @"(patientUID BEGINSWITH[cd]
     * %@)", studySelected.patientUID]]
     */
    public List<DicomStudy> getStudiesByPatientUID(String patientUID) {
        List<DicomStudy> result = new ArrayList<>();
        if (patientUID == null || patientUID.isEmpty() || sqliteConnection == null) {
            return result;
        }

        // HOROS-20240407準拠: patientUID BEGINSWITH[cd]（大文字小文字を区別しない前方一致）
        String sql = "SELECT * FROM Study WHERE UPPER(patientUID) LIKE UPPER(?) ORDER BY date DESC";
        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(sql)) {
            pstmt.setString(1, patientUID + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    DicomStudy study = new DicomStudy();
                    int studyDbId = rs.getInt("id");
                    study.setStudyInstanceUID(rs.getString("studyInstanceUID"));
                    study.setName(rs.getString("name"));
                    study.setPatientID(rs.getString("patientID"));
                    study.setPatientUID(rs.getString("patientUID"));
                    study.setModality(rs.getString("modality"));
                    study.setNumberOfImages(rs.getInt("numberOfImages"));
                    study.setComment(rs.getString("comment"));
                    study.setAccessionNumber(rs.getString("accessionNumber"));
                    study.setReferringPhysician(rs.getString("referringPhysician"));
                    study.setPerformingPhysician(rs.getString("performingPhysician"));
                    study.setInstitutionName(rs.getString("institutionName"));
                    study.setStudyID(rs.getString("studyID"));
                    study.setStudyName(rs.getString("studyName"));

                    // HOROS-20240407準拠: hiddenを読み込む
                    try {
                        int hiddenValue = rs.getInt("hidden");
                        study.setHidden(hiddenValue != 0);
                    } catch (SQLException e) {
                        // hiddenカラムが存在しない場合はデフォルト値（false）を使用
                        study.setHidden(false);
                    }

                    // DBのIDを保存（遅延読み込み用）
                    study.setId(String.valueOf(studyDbId));

                    String dateStr = rs.getString("date");
                    if (dateStr != null && !dateStr.isEmpty()) {
                        try {
                            study.setDate(LocalDateTime.parse(dateStr));
                        } catch (Exception e) {
                            // 日付パースエラーは無視
                        }
                    }

                    result.add(study);
                }
            }
        } catch (SQLException e) {

        }
        return result;
    }

    /**
     * マネージドオブジェクトモデルを取得
     */
    public Object managedObjectModel() {
        return null;
    }

    /**
     * データベースを保存
     */
    public boolean save(Object error) {
        return true;
    }

    /**
     * Studyのhidden状態を更新
     * HOROS-20240407準拠: matrixPreviewSwitchHiddenで使用
     */
    public void updateStudyHiddenState(String studyInstanceUID, boolean hidden) throws SQLException {
        if (sqliteConnection == null || studyInstanceUID == null) {
            return;
        }

        String updateSql = "UPDATE Study SET hidden = ? WHERE studyInstanceUID = ?";
        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(updateSql)) {
            pstmt.setInt(1, hidden ? 1 : 0);
            pstmt.setString(2, studyInstanceUID);
            pstmt.executeUpdate();
        }
    }

    /**
     * Studyのhidden状態を読み込む
     * HOROS-20240407準拠: updatePreviewMatrixで使用
     */
    public void loadStudyHiddenState(DicomStudy study) throws SQLException {
        if (sqliteConnection == null || study == null || study.getStudyInstanceUID() == null) {
            return;
        }

        // hiddenカラムが存在するか確認
        boolean hasHiddenColumn = false;
        try {
            try (ResultSet rs = sqliteConnection.getMetaData().getColumns(null, null, "Study", "hidden")) {
                hasHiddenColumn = rs.next();
            }
        } catch (SQLException e) {
            // メタデータ取得に失敗した場合は、SELECTを試行してエラーで判断
            hasHiddenColumn = false;
        }

        if (!hasHiddenColumn) {
            // hiddenカラムが存在しない場合は、デフォルト値（false）を設定
            study.setHidden(false);
            return;
        }

        String selectSql = "SELECT hidden FROM Study WHERE studyInstanceUID = ?";
        try (PreparedStatement pstmt = sqliteConnection.prepareStatement(selectSql)) {
            pstmt.setString(1, study.getStudyInstanceUID());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int hiddenValue = rs.getInt("hidden");
                    study.setHidden(hiddenValue != 0);
                } else {
                    // レコードが存在しない場合は、デフォルト値（false）を設定
                    study.setHidden(false);
                }
            }
        }
    }

    /**
     * ロック
     */
    public void lock() {
        processFilesLock.lock();
    }

    /**
     * アンロック
     */
    public void unlock() {
        processFilesLock.unlock();
    }
}
