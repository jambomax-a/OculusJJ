package com.oculus.jj.config;

import java.util.prefs.Preferences;
import java.awt.Color;

/**
 * AppPreferences - アプリケーションの設定管理クラス
 * HOROS-20240407のNSUserDefaults相当の機能を提供します。
 */
public class AppPreferences {

    private static final String NODE_NAME = "com.oculus.jj";
    private static final Preferences prefs = Preferences.userRoot().node(NODE_NAME);

    // キー名の定義
    public static final String KEY_3D_RESOLUTION = "3d.resolution";
    public static final String KEY_3D_SHADING = "3d.shading";
    public static final String KEY_ROI_COLOR = "roi.color.default";
    public static final String KEY_MEASURE_UNIT = "measure.unit"; // "mm" or "cm"
    public static final String KEY_DICOM_FOLDER = "data.dicom.folder";
    public static final String KEY_DATABASE_FOLDER = "data.database.folder";

    // デフォルト値
    private static final double DEFAULT_3D_RESOLUTION = 0.5;
    private static final boolean DEFAULT_3D_SHADING = true;
    private static final int DEFAULT_ROI_COLOR = Color.YELLOW.getRGB();

    public static String getDefaultDicomFolder() {
        return System.getProperty("user.home") + File.separator + "OculusJJ" + File.separator + "DICOM";
    }

    public static String getDefaultDatabaseFolder() {
        return System.getProperty("user.home") + File.separator + "OculusJJ" + File.separator + "Database";
    }

    public static double get3DResolution() {
        return prefs.getDouble(KEY_3D_RESOLUTION, DEFAULT_3D_RESOLUTION);
    }

    public static void set3DResolution(double res) {
        prefs.putDouble(KEY_3D_RESOLUTION, res);
    }

    public static boolean is3DShadingEnabled() {
        return prefs.getBoolean(KEY_3D_SHADING, DEFAULT_3D_SHADING);
    }

    public static void set3DShadingEnabled(boolean enabled) {
        prefs.putBoolean(KEY_3D_SHADING, enabled);
    }

    public static Color getROIColor() {
        int rgb = prefs.getInt(KEY_ROI_COLOR, DEFAULT_ROI_COLOR);
        return new Color(rgb);
    }

    public static void setROIColor(Color color) {
        prefs.putInt(KEY_ROI_COLOR, color.getRGB());
    }

    public static String getMeasureUnit() {
        return prefs.get(KEY_MEASURE_UNIT, "mm");
    }

    public static void setMeasureUnit(String unit) {
        prefs.put(KEY_MEASURE_UNIT, unit);
    }
    
    /**
     * すべての設定を強制的に保存
     */
    public static void saveAll() {
        try {
            prefs.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
