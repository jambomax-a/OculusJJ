package com.oculus.jj.config;

import java.io.*;
import java.util.Properties;

/**
 * 設定情報を外部ファイルから読み書きするマネージャークラス。
 * デフォルト値とユーザーカスタマイズ値を管理します。
 */
public class ConfigManager {
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties props = new Properties();

    // シングルトンインスタンス
    private static final ConfigManager instance = new ConfigManager();

    /**
     * シングルトンインスタンスを取得する。
     */
    public static ConfigManager getInstance() {
        return instance;
    }

    // デフォルト値
    public static final int DEFAULT_WINDOW_SIZE_WIDTH = 1280;
    public static final int DEFAULT_WINDOW_SIZE_HEIGHT = 1024;
    public static final double DEFAULT_SLAB_THICKNESS = 1.0;
    public static final double DEFAULT_SLICE_SPACING_Z = 1.0;

    // MPR関連デフォルト値
    public static final String DEFAULT_MPR_LAYOUT = "ORTHOGONAL";
    public static final SlabMode DEFAULT_SLAB_MODE = SlabMode.MIP;

    // 一般設定デフォルト値
    public static final String DEFAULT_DICOM_FOLDER = System.getProperty("user.home") + "/OculusJJ/DICOM";
    public static final String DEFAULT_DATABASE_FOLDER = System.getProperty("user.home") + "/OculusJJ/Database";
    public static final String DEFAULT_LANGUAGE = "ja";
    public static final String DEFAULT_THEME = "system";

    // ネットワーク設定デフォルト値
    public static final String DEFAULT_LOCAL_AE_TITLE = "JJDICOM_VIEWER";
    public static final int DEFAULT_LOCAL_PORT = 104;
    public static final int DEFAULT_TIMEOUT_MS = 30000;

    // パフォーマンス設定デフォルト値
    public static final int DEFAULT_LOD_THRESHOLD = 1024;
    public static final int DEFAULT_CACHE_SIZE_MB = 2048;

    static {
        loadConfig();
    }

    /**
     * 設定ファイルを読み込む。存在しない場合はデフォルト値を使用。
     */
    private static void loadConfig() {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            // ファイルがない場合はデフォルト値を使用
            System.out.println("Config file not found, using default values.");
        }
    }

    /**
     * 設定を保存する。
     */
    public static void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "OculusJJ Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 文字列設定を取得する。
     */
    public static String getString(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    /**
     * 整数値設定を取得する。
     */
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 浮動小数点数設定を取得する。
     */
    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * ブーリアン設定を取得する。
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = props.getProperty(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

   /**
     * 設定を設定する（メモリ上のみ）。
     */
    public static void setProperty(String key, String value) {
        props.setProperty(key, value);
    }

    // ==================== ヘルパーメソッド ====================

    /**
     * DICOMフォルダパスを取得する。
     */
    public static String getDicomFolder() {
        return getString("data.dicom.folder", DEFAULT_DICOM_FOLDER);
    }

    /**
     * データベースフォルダパスを取得する。
     */
    public static String getDatabaseFolder() {
        return getString("data.database.folder", DEFAULT_DATABASE_FOLDER);
    }

    /**
     * 表示言語を取得する。
     */
    public static String getLanguage() {
        return getString("ui.language", DEFAULT_LANGUAGE);
    }

    /**
     * UIテーマを取得する。
     */
    public static String getTheme() {
        return getString("ui.theme", DEFAULT_THEME);
    }

    /**
     * ウィンドウ起動時に最大化するかどうかを取得する。
     */
    public static boolean isWindowMaximized() {
        return getBoolean("window.maximized", false);
    }

    /**
     * デフォルトウィンドウレベルを取得する。
     */
    public static int getDefaultWindowLevel() {
        return getInt("image.default.window.level", 0);
    }

    /**
     * デフォルトウィンドウ幅を取得する。
     */
    public static int getDefaultWindowWidth() {
        return getInt("image.default.window.width", 0);
    }

    /**
     * 補完アルゴリズムを取得する。
     */
    public static String getInterpolationAlgorithm() {
        return getString("image.interpolation", "bilinear");
    }

    /**
     * ローカルAEタイトルを取得する。
     */
    public static String getLocalAETitle() {
        return getString("net.local.ae.title", DEFAULT_LOCAL_AE_TITLE);
    }

    /**
     * ローカルポートを取得する。
     */
    public static int getLocalPort() {
        return getInt("net.local.port", DEFAULT_LOCAL_PORT);
    }

    /**
     * 通信タイムアウト（ミリ秒）を取得する。
     */
    public static int getTimeoutMs() {
        return getInt("net.timeout.ms", DEFAULT_TIMEOUT_MS);
    }

    /**
     * LOD閾値を取得する。
     */
    public static int getLodThreshold() {
        return getInt("perf.lod.threshold", DEFAULT_LOD_THRESHOLD);
    }

    /**
     * キャッシュサイズ（MB）を取得する。
     */
    public static int getCacheSizeMB() {
        return getInt("perf.cache.size.mb", DEFAULT_CACHE_SIZE_MB);
    }

    /**
     * 最大並列スレッド数を取得する。
     */
    public static int getMaxThreads() {
        return getInt("perf.threads.max", Runtime.getRuntime().availableProcessors());
    }

    /**
     * スラブモードの列挙型。
     */
    public enum SlabMode {
        NONE, AVERAGE, MIP, MINIP
    }

    /**
     * 設定をリセットしてデフォルト値に戻す。
     */
    public static void resetToDefaults() {
        props.clear();
        saveConfig();
    }
}
