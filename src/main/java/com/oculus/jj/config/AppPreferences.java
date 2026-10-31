package com.oculus.jj.config;

import java.util.prefs.Preferences;
import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * AppPreferences - アプリケーションの設定管理クラス。
 * HOROS-20240407のNSUserDefaults相当の機能を提供します。
 */
public class AppPreferences {

    private static final String NODE_NAME = "com.oculus.jj";
    private static final Preferences prefs = Preferences.userRoot().node(NODE_NAME);

    // === Basic preference keys ===
    public static final String KEY_3D_RESOLUTION = "3d.resolution";
    public static final String KEY_3D_SHADING = "3d.shading";
    public static final String KEY_ROI_COLOR = "roi.color.default";
    public static final String KEY_MEASURE_UNIT = "measure.unit";
    public static final String KEY_DICOM_FOLDER = "data.dicom.folder";
    public static final String KEY_DATABASE_FOLDER = "data.database.folder";

    // === General preference keys ===
    public static final String KEY_APP_LANGUAGE = "general.language";
    public static final String KEY_DISPLAY_TYPE = "general.displayType";
    public static final String KEY_CHECK_UPDATES = "general.checkUpdatesAtStartup";
    public static final String KEY_SEPARATE_WINDOW = "general.separateWindowPerStudy";

    // === CD/DVD preference keys ===
    public static final String KEY_CDDVD_EJECT_AFTER_COPY = "cddvd.ejectAfterCopy";
    public static final String KEY_CDDVD_AUTO_SELECT = "cddvd.autoSelectSource";
    public static final String KEY_CDDVD_AUTO_IMPORT = "cddvd.autoImportFromInsertedDiscs";
    public static final String KEY_CDDVD_PREFER_STD_JPEG = "cddvd.preferStdJPEG";
    public static final String KEY_CDDVD_SUPPLEMENTARY_FOLDER = "cddvd.supplementaryFolder";
    // === Burn preference keys ===
    public static final String KEY_BURN_CD_NAME = "burn.cdName";
    public static final String KEY_BURN_COMPRESSION_MODE = "burn.compressionMode";
    public static final String KEY_BURN_DESTINATION = "burn.destination";
    public static final String KEY_BURN_INCLUDE_WEASIS = "burn.includeWeasis";
    public static final String KEY_BURN_ENCRYPT_CD = "burn.encryptCD";

    // === Hanging Protocol preference keys ===
    public static final String KEY_HANGING_MODALITY = "hanging.defaultModality";
    public static final String KEY_HANGING_TILING = "hanging.defaultTiling";
    public static final String KEY_HANGING_WLWW_PRESET = "hanging.defaultWLWWPreset";

    // === Hot Keys preference keys (stored as JSON string for simplicity) ===
    public static final String KEY_HOTKEYS = "hotkeys.bindings";

    // === Viewers preference keys ===
    public static final String KEY_DEFAULT_CLUT = "viewers.defaultCLUT";
    public static final String KEY_DEFAULT_OPACITY_TABLE = "viewers.defaultOpacityTable";
    public static final String KEY_SYNC_SERIES_BETWEEN_STUDIES = "viewers.syncSeriesBetweenStudies";

    // === 3D Viewer preference keys ===
    public static final String KEY_3D_SHADING_ALGORITHM = "3d.shadingAlgorithm";
    public static final String KEY_3D_ROI_ALGORITHM = "3d.roiAlgorithm";
    public static final String KEY_3D_SPLINE_FOR_SCISORS = "3d.splineForScissorsROI";

    // === PET/SUV preference keys ===
    public static final String KEY_PET_DEFAULT_CLUT = "pet.defaultCLUT";
    public static final String KEY_PET_CLUT_BW_INVERSE = "pet.clutBwInverse";
    public static final String KEY_PET_DEFAULT_TRANSFER_FUNC = "pet.defaultTransferFunction";
    public static final String KEY_PET_APPLY_TO_NM = "pet.applyToNMModality";
    // === Fusion (PET/CT) preferences ===
    public static final String KEY_FUSION_MODE = "fusion.mode";
    public static final String KEY_FUSION_WEIGHT = "fusion.weight";

    // === Annotations preference keys ===
    public static final String KEY_ANNOTATION_STYLE = "annotations.defaultStyle";
    public static final String KEY_ANNOTATION_COLOR = "annotations.defaultColor";
    public static final String KEY_ANNOTATION_FONT = "annotations.defaultFont";
    public static final String KEY_ANNOTATION_MODALITY_SPECIFIC = "annotations.modalitySpecificDefaults";

    // === DICOM Print preference keys ===
    public static final String KEY_DICOM_PRINT_PRINTER = "dicomPrint.defaultPrinter";
    public static final String KEY_DICOM_PRINT_LAYOUT = "dicomPrint.layout";
    public static final String KEY_DICOM_PRINT_ORIENTATION = "dicomPrint.filmOrientation";
    public static final String KEY_DICOM_PRINT_AUTO_RESIZE = "dicomPrint.autoResizeWindow";

    // === Listener preference keys ===
    public static final String KEY_LISTENER_AETITLE = "listener.aetitle";
    public static final String KEY_LISTENER_PORT = "listener.port";
    public static final String KEY_LISTENER_AUTO_START = "listener.autoStart";

    // === Viewer 2D preference keys ===
    // HOROS-20240407準拠: NSUserDefaults "UseVOILUT" (ViewerController.m useVOILUT:)
    public static final String KEY_USE_VOILUT = "viewer2d.useVOILUT";
    // HOROS-20240407準拠: SUV変換パラメータ (displaySUVWindow)
    public static final String KEY_SUV_PATIENTS_WEIGHT = "viewer2d.suv.patientsWeight";
    public static final String KEY_SUV_RADIONUCLIDE_TOTAL_DOSE = "viewer2d.suv.radionuclideTotalDose";
    public static final String KEY_SUV_RADIONUCLIDE_HALFLIFE = "viewer2d.suv.radionuclideHalfLife";

    // === Web Server preference keys ===
    public static final String KEY_WEB_SERVER_PORT = "webserver.port";
    public static final String KEY_WEB_WADO_ENABLED = "webserver.wadoEnabled";
    public static final String KEY_WEB_STOWRS_ENABLED = "webserver.stowrsEnabled";
    public static final String KEY_WEB_BASE_URL = "webserver.baseUrl";

    // === On-Demand / PACS preference keys ===
    public static final String KEY_ONDEMAND_SEARCH_COMPARATIVES = "ondemand.searchComparatives";
    public static final String KEY_ONDEMAND_QUERY_SMART_ALBUMS = "ondemand.querySmartAlbums";
    public static final String KEY_ONDEMAND_IMAGE_RETRIEVAL = "ondemand.imageRetrieval";
    public static final String KEY_PACS_QUERY_TIMEOUT = "pacs.queryTimeout";
    public static final String KEY_PACS_MAX_STUDIES = "pacs.maxStudiesPerQuery";

    // === Default values ===
    private static final double DEFAULT_3D_RESOLUTION = 0.5;
    private static final boolean DEFAULT_3D_SHADING = false;
    private static final int DEFAULT_ROI_COLOR = Color.YELLOW.getRGB();

    /** デフォルトのDICOMフォルダパス */
    // --- Fusion preferences ---
    public static com.oculus.jj.model.FusionImageProcessor.FusionMode getFusionMode() {
        String modeStr = prefs.get(KEY_FUSION_MODE, "ALPHA");
        try {
            return com.oculus.jj.model.FusionImageProcessor.FusionMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            return com.oculus.jj.model.FusionImageProcessor.FusionMode.ALPHA;
        }
    }

    public static void setFusionMode(com.oculus.jj.model.FusionImageProcessor.FusionMode mode) {
        prefs.put(KEY_FUSION_MODE, mode.name());
    }

    public static double getFusionWeight() {
        return prefs.getDouble(KEY_FUSION_WEIGHT, 0.5);
    }

    public static void setFusionWeight(double weight) {
        prefs.putDouble(KEY_FUSION_WEIGHT, weight);
    }

    // --- ROI display options (HOROS-20240407: Display Info Only when ROI Selected / font size) ---
    /** HOROS準拠: 「選択中のROIのみ情報表示」の切替（デフォルト: 常に表示） */
    public static boolean isRoiInfoOnlyWhenSelected() {
        return prefs.getBoolean("roi.infoOnlyWhenSelected", false);
    }

    public static void setRoiInfoOnlyWhenSelected(boolean b) {
        prefs.putBoolean("roi.infoOnlyWhenSelected", b);
    }

    /** HOROS準拠: ROIフォントサイズの倍率（デフォルト1.0、increase/decreaseで±0.25） */
    public static double getRoiFontScale() {
        return prefs.getDouble("roi.fontScale", 1.0);
    }

    public static void setRoiFontScale(double scale) {
        prefs.putDouble("roi.fontScale", scale);
    }

    /** HOROS準拠: 新規ROIの既定名（デフォルト "ROI"） */
    public static String getRoiDefaultName() {
        return prefs.get("roi.defaultName", "ROI");
    }

    public static void setRoiDefaultName(String name) {
        prefs.put("roi.defaultName", name);
    }

    // --- Viewer 2D (Use VOI LUT) ---
    /** HOROS-20240407準拠: NSUserDefaults "UseVOILUT" (default: true) */
    public static boolean isUseVOILUT() {
        return prefs.getBoolean(KEY_USE_VOILUT, true);
    }

    public static void setUseVOILUT(boolean use) {
        prefs.putBoolean(KEY_USE_VOILUT, use);
    }

    // --- SUV conversion parameters (HOROS-20240407: displaySUVWindow) ---
    public static float getSUVPatientsWeight() {
        return (float) prefs.getDouble(KEY_SUV_PATIENTS_WEIGHT, 70.0);
    }

    public static void setSUVPatientsWeight(float w) {
        prefs.putDouble(KEY_SUV_PATIENTS_WEIGHT, w);
    }

    public static float getSUVRadionuclideTotalDose() {
        return (float) prefs.getDouble(KEY_SUV_RADIONUCLIDE_TOTAL_DOSE, 370.0);
    }

    public static void setSUVRadionuclideTotalDose(float d) {
        prefs.putDouble(KEY_SUV_RADIONUCLIDE_TOTAL_DOSE, d);
    }

    public static float getSUVRadionuclideHalfLife() {
        return (float) prefs.getDouble(KEY_SUV_RADIONUCLIDE_HALFLIFE, 110.0);
    }

    public static void setSUVRadionuclideHalfLife(float h) {
        prefs.putDouble(KEY_SUV_RADIONUCLIDE_HALFLIFE, h);
    }

    public static String getDefaultDicomFolder() {
        return ConfigManager.DEFAULT_DICOM_FOLDER;
    }

    /** デフォルトのデータベースフォルダパス */
    public static String getDefaultDatabaseFolder() {
        return ConfigManager.DEFAULT_DATABASE_FOLDER;
    }

    // === Getters / Setters ===

    // --- 3D Settings ---
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

    // --- General ---
    public static String getMeasureUnit() {
        return prefs.get(KEY_MEASURE_UNIT, "mm");
    }

    public static void setMeasureUnit(String unit) {
        prefs.put(KEY_MEASURE_UNIT, unit);
    }

    public static String getAppLanguage() {
        String lang = prefs.get(KEY_APP_LANGUAGE, "");
        if (lang.isEmpty()) {
            // 初回起動（未設定）: ConfigManager の既定値（config.properties / DEFAULT_LANGUAGE="ja"）に従う
            lang = ConfigManager.getLanguage();
        }
        return lang.contains("ja") ? "ja" : "en";
    }

    public static void setAppLanguage(String lang) {
        prefs.put(KEY_APP_LANGUAGE, lang);
    }

    public static int getDisplayTypeIndex() {
        return prefs.getInt(KEY_DISPLAY_TYPE, 0);
    }

    public static void setDisplayTypeIndex(int index) {
        prefs.putInt(KEY_DISPLAY_TYPE, index);
    }

    public static boolean isCheckUpdatesAtStartup() {
        return prefs.getBoolean(KEY_CHECK_UPDATES, false);
    }

    public static void setCheckUpdatesAtStartup(boolean checked) {
        prefs.putBoolean(KEY_CHECK_UPDATES, checked);
    }

    public static boolean isSeparateWindowPerStudy() {
        return prefs.getBoolean(KEY_SEPARATE_WINDOW, true);
    }

    public static void setSeparateWindowPerStudy(boolean separate) {
        prefs.putBoolean(KEY_SEPARATE_WINDOW, separate);
    }

    // --- CD/DVD ---
    public static boolean isCDDVDEjectAfterCopy() {
        return prefs.getBoolean(KEY_CDDVD_EJECT_AFTER_COPY, false);
    }

    public static void setCDDVDEjectAfterCopy(boolean eject) {
        prefs.putBoolean(KEY_CDDVD_EJECT_AFTER_COPY, eject);
    }

    public static boolean isCDDVDAutoSelectSource() {
        return prefs.getBoolean(KEY_CDDVD_AUTO_SELECT, true);
    }

    public static void setCDDVDAutoSelectSource(boolean auto) {
        prefs.putBoolean(KEY_CDDVD_AUTO_SELECT, auto);
    }

    public static boolean isCDDVDAutoImport() {
        return prefs.getBoolean(KEY_CDDVD_AUTO_IMPORT, false);
    }

    public static void setCDDVDAutoImport(boolean auto) {
        prefs.putBoolean(KEY_CDDVD_AUTO_IMPORT, auto);
    }

    public static boolean isCDDVDPrefStdJPEG() {
        return prefs.getBoolean(KEY_CDDVD_PREFER_STD_JPEG, true);
    }

    public static void setCDDVDPrefStdJPEG(boolean prefer) {
        prefs.putBoolean(KEY_CDDVD_PREFER_STD_JPEG, prefer);
    }

    public static String getCDDVDSupplementaryFolder() {
        return prefs.get(KEY_CDDVD_SUPPLEMENTARY_FOLDER, "");
    }

    public static void setCDDVDSupplementaryFolder(String folder) {
        prefs.put(KEY_CDDVD_SUPPLEMENTARY_FOLDER, folder);
    }

    // --- Burn ---
    public static String getBurnCDName() {
        return prefs.get(KEY_BURN_CD_NAME, "DICOMCD");
    }

    public static void setBurnCDName(String name) {
        prefs.put(KEY_BURN_CD_NAME, name);
    }

    public static int getBurnCompressionMode() {
        return prefs.getInt(KEY_BURN_COMPRESSION_MODE, 0);
    } // 0=no change

    public static void setBurnCompressionMode(int mode) {
        prefs.putInt(KEY_BURN_COMPRESSION_MODE, mode);
    }

    public static int getBurnDestination() {
        return prefs.getInt(KEY_BURN_DESTINATION, 0);
    } // 0=CD/DVD media

    public static void setBurnDestination(int dest) {
        prefs.putInt(KEY_BURN_DESTINATION, dest);
    }

    public static boolean getBurnIncludeWeasis() {
        return prefs.getBoolean(KEY_BURN_INCLUDE_WEASIS, false);
    }

    public static void setBurnIncludeWeasis(boolean include) {
        prefs.putBoolean(KEY_BURN_INCLUDE_WEASIS, include);
    }

    public static boolean getBurnEncryptCD() {
        return prefs.getBoolean(KEY_BURN_ENCRYPT_CD, false);
    }

    public static void setBurnEncryptCD(boolean encrypt) {
        prefs.putBoolean(KEY_BURN_ENCRYPT_CD, encrypt);
    }

    // --- Hanging Protocols ---
    public static int getHangingModalityIndex() {
        return prefs.getInt(KEY_HANGING_MODALITY, 1);
    } // CT

    public static void setHangingModalityIndex(int index) {
        prefs.putInt(KEY_HANGING_MODALITY, index);
    }

    public static int getHangingTilingIndex() {
        return prefs.getInt(KEY_HANGING_TILING, 3);
    } // 2x2

    public static void setHangingTilingIndex(int index) {
        prefs.putInt(KEY_HANGING_TILING, index);
    }

    public static String getHangingWLWWPreset() {
        return prefs.get(KEY_HANGING_WLWW_PRESET, "Default");
    }

    public static void setHangingWLWWPreset(String preset) {
        prefs.put(KEY_HANGING_WLWW_PRESET, preset);
    }

    // --- Viewers ---
    public static String getDefaultCLUT() {
        return prefs.get(KEY_DEFAULT_CLUT, "CT-Body");
    }

    public static void setDefaultCLUT(String clut) {
        prefs.put(KEY_DEFAULT_CLUT, clut);
    }

    public static String getDefaultOpacityTable() {
        return prefs.get(KEY_DEFAULT_OPACITY_TABLE, "Standard");
    }

    public static void setDefaultOpacityTable(String table) {
        prefs.put(KEY_DEFAULT_OPACITY_TABLE, table);
    }

    public static boolean isSyncSeriesBetweenStudies() {
        return prefs.getBoolean(KEY_SYNC_SERIES_BETWEEN_STUDIES, true);
    }

    public static void setSyncSeriesBetweenStudies(boolean sync) {
        prefs.putBoolean(KEY_SYNC_SERIES_BETWEEN_STUDIES, sync);
    }

    // --- 3D Viewer ---
    public static int get3DShadingAlgorithmIndex() {
        return prefs.getInt(KEY_3D_SHADING_ALGORITHM, 0);
    }

    public static void set3DShadingAlgorithmIndex(int index) {
        prefs.putInt(KEY_3D_SHADING_ALGORITHM, index);
    }

    public static int get3DRoiAlgorithmIndex() {
        return prefs.getInt(KEY_3D_ROI_ALGORITHM, 0);
    } // Iso Contour

    public static void set3DRoiAlgorithmIndex(int index) {
        prefs.putInt(KEY_3D_ROI_ALGORITHM, index);
    }

    public static boolean is3DSplineForScissors() {
        return prefs.getBoolean(KEY_3D_SPLINE_FOR_SCISORS, false);
    }

    public static void set3DSplineForScissors(boolean spline) {
        prefs.putBoolean(KEY_3D_SPLINE_FOR_SCISORS, spline);
    }

    // --- PET/SUV ---
    public static String getPETDefaultCLUT() {
        return prefs.get(KEY_PET_DEFAULT_CLUT, "PET-Rainbow");
    }

    public static void setPETDefaultCLUT(String clut) {
        prefs.put(KEY_PET_DEFAULT_CLUT, clut);
    }

    public static boolean isPETCLUTBwInverse() {
        return prefs.getBoolean(KEY_PET_CLUT_BW_INVERSE, false);
    }

    public static void setPETCLUTBwInverse(boolean bw) {
        prefs.putBoolean(KEY_PET_CLUT_BW_INVERSE, bw);
    }

    public static String getPETDefaultTransferFunction() {
        return prefs.get(KEY_PET_DEFAULT_TRANSFER_FUNC, "Standard");
    }

    public static void setPETDefaultTransferFunction(String func) {
        prefs.put(KEY_PET_DEFAULT_TRANSFER_FUNC, func);
    }

    public static boolean isPETApplyToNM() {
        return prefs.getBoolean(KEY_PET_APPLY_TO_NM, false);
    }

    public static void setPETApplyToNM(boolean apply) {
        prefs.putBoolean(KEY_PET_APPLY_TO_NM, apply);
    }

    // --- Annotations ---
    public static String getAnnotationStyle() {
        return prefs.get(KEY_ANNOTATION_STYLE, "None");
    }

    public static void setAnnotationStyle(String style) {
        prefs.put(KEY_ANNOTATION_STYLE, style);
    }

    public static String getAnnotationColor() {
        return prefs.get(KEY_ANNOTATION_COLOR, "White");
    }

    public static void setAnnotationColor(String color) {
        prefs.put(KEY_ANNOTATION_COLOR, color);
    }

    public static String getAnnotationFont() {
        return prefs.get(KEY_ANNOTATION_FONT, "Arial");
    }

    public static void setAnnotationFont(String font) {
        prefs.put(KEY_ANNOTATION_FONT, font);
    }

    public static boolean isAnnotationModalitySpecific() {
        return prefs.getBoolean(KEY_ANNOTATION_MODALITY_SPECIFIC, false);
    }

    public static void setAnnotationModalitySpecific(boolean modality) {
        prefs.putBoolean(KEY_ANNOTATION_MODALITY_SPECIFIC, modality);
    }

    // --- DICOM Print ---
    public static String getDICOMPrintPrinter() {
        return prefs.get(KEY_DICOM_PRINT_PRINTER, "Default Printer");
    }

    public static void setDICOMPrintPrinter(String printer) {
        prefs.put(KEY_DICOM_PRINT_PRINTER, printer);
    }

    public static String getDICOMPrintLayout() {
        return prefs.get(KEY_DICOM_PRINT_LAYOUT, "Standard 2,1");
    }

    public static void setDICOMPrintLayout(String layout) {
        prefs.put(KEY_DICOM_PRINT_LAYOUT, layout);
    }

    public static String getDICOMPrintOrientation() {
        return prefs.get(KEY_DICOM_PRINT_ORIENTATION, "Landscape");
    }

    public static void setDICOMPrintOrientation(String orientation) {
        prefs.put(KEY_DICOM_PRINT_ORIENTATION, orientation);
    }

    public static boolean isDICOMPrintAutoResize() {
        return prefs.getBoolean(KEY_DICOM_PRINT_AUTO_RESIZE, true);
    }

    public static void setDICOMPrintAutoResize(boolean resize) {
        prefs.putBoolean(KEY_DICOM_PRINT_AUTO_RESIZE, resize);
    }

    // --- Listener ---
    public static String getListenerAETitle() {
        return prefs.get(KEY_LISTENER_AETITLE, "OCULUSJJ");
    }

    public static void setListenerAETitle(String aet) {
        prefs.put(KEY_LISTENER_AETITLE, aet);
    }

    public static int getListenerPort() {
        return prefs.getInt(KEY_LISTENER_PORT, 11112);
    }

    public static void setListenerPort(int port) {
        prefs.putInt(KEY_LISTENER_PORT, port);
    }

    public static boolean isListenerAutoStart() {
        return prefs.getBoolean(KEY_LISTENER_AUTO_START, true);
    }

    public static void setListenerAutoStart(boolean auto) {
        prefs.putBoolean(KEY_LISTENER_AUTO_START, auto);
    }

    // --- Web Server ---
    public static int getWebServerPort() {
        return prefs.getInt(KEY_WEB_SERVER_PORT, 3333);
    }

    public static void setWebServerPort(int port) {
        prefs.putInt(KEY_WEB_SERVER_PORT, port);
    }

    public static boolean isWADOEnabled() {
        return prefs.getBoolean(KEY_WEB_WADO_ENABLED, true);
    }

    public static void setWADOEnabled(boolean wado) {
        prefs.putBoolean(KEY_WEB_WADO_ENABLED, wado);
    }

    public static boolean isSTOWRSEnabled() {
        return prefs.getBoolean(KEY_WEB_STOWRS_ENABLED, false);
    }

    public static void setSTOWRSEnabled(boolean stowrs) {
        prefs.putBoolean(KEY_WEB_STOWRS_ENABLED, stowrs);
    }

    public static String getWebBaseUrl() {
        return prefs.get(KEY_WEB_BASE_URL, "http://localhost:8080");
    }

    public static void setWebBaseUrl(String url) {
        prefs.put(KEY_WEB_BASE_URL, url);
    }

    // --- On-Demand / PACS ---
    public static boolean isOnDemandSearchComparatives() {
        return prefs.getBoolean(KEY_ONDEMAND_SEARCH_COMPARATIVES, true);
    }

    public static void setOnDemandSearchComparatives(boolean search) {
        prefs.putBoolean(KEY_ONDEMAND_SEARCH_COMPARATIVES, search);
    }

    public static boolean isOnDemandQuerySmartAlbums() {
        return prefs.getBoolean(KEY_ONDEMAND_QUERY_SMART_ALBUMS, false);
    }

    public static void setOnDemandQuerySmartAlbums(boolean query) {
        prefs.putBoolean(KEY_ONDEMAND_QUERY_SMART_ALBUMS, query);
    }

    public static boolean isOnDemandImageRetrieval() {
        return prefs.getBoolean(KEY_ONDEMAND_IMAGE_RETRIEVAL, true);
    }

    public static void setOnDemandImageRetrieval(boolean retrieval) {
        prefs.putBoolean(KEY_ONDEMAND_IMAGE_RETRIEVAL, retrieval);
    }

    public static int getPacsQueryTimeout() {
        return prefs.getInt(KEY_PACS_QUERY_TIMEOUT, 30);
    }

    public static void setPacsQueryTimeout(int timeout) {
        prefs.putInt(KEY_PACS_QUERY_TIMEOUT, timeout);
    }

    public static int getPacsMaxStudiesPerQuery() {
        return prefs.getInt(KEY_PACS_MAX_STUDIES, 100);
    }

    public static void setPacsMaxStudiesPerQuery(int max) {
        prefs.putInt(KEY_PACS_MAX_STUDIES, max);
    }

    // --- Misc ---
    public static String getDicomFolder() {
        return prefs.get(KEY_DICOM_FOLDER, getDefaultDicomFolder());
    }

    public static void setDicomFolder(String path) {
        prefs.put(KEY_DICOM_FOLDER, path);
    }

    public static String getDatabaseFolder() {
        return prefs.get(KEY_DATABASE_FOLDER, getDefaultDatabaseFolder());
    }

    public static void setDatabaseFolder(String path) {
        String oldPath = prefs.get(KEY_DATABASE_FOLDER, getDefaultDatabaseFolder());
        if (!path.equals(oldPath)) {
            com.oculus.jj.model.DicomDatabase.resetCachedDatabases();
        }
        prefs.put(KEY_DATABASE_FOLDER, path);
    }

    public static void saveAll() {
        try {
            prefs.flush();
        } catch (Exception e) {
            // エラーは無視するか、適切にログ出力を行う（コンソールへの直接出力は避ける）
        }
    }

    public static void resetAll() throws java.util.prefs.BackingStoreException {
        prefs.clear();
        prefs.flush();
    }

    public static void exportAll(File file) throws java.io.IOException, java.util.prefs.BackingStoreException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            prefs.exportSubtree(out);
        }
    }

    public static void importAll(File file) throws java.io.IOException, java.util.prefs.InvalidPreferencesFormatException {
        try (FileInputStream in = new FileInputStream(file)) {
            Preferences.importPreferences(in);
        }
    }

    // ============================================================
    // Generic flat-key helpers (HOROS NSUserDefaults 相当).
    // 大量の Preference ペイン項目を個別に定数/getter/setter として
    // 定義する代わりに、キー文字列を直接渡す薄いラッパー。
    // ============================================================
    public static boolean getBool(String key, boolean def) {
        return prefs.getBoolean(key, def);
    }

    public static void setBool(String key, boolean v) {
        prefs.putBoolean(key, v);
    }

    public static int getInt(String key, int def) {
        return prefs.getInt(key, def);
    }

    public static void setInt(String key, int v) {
        prefs.putInt(key, v);
    }

    public static double getDouble(String key, double def) {
        String s = prefs.get(key, null);
        if (s == null) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    public static void setDouble(String key, double v) {
        prefs.put(key, String.valueOf(v));
    }

    public static String getStr(String key, String def) {
        return prefs.get(key, def == null ? "" : def);
    }

    public static void setStr(String key, String v) {
        prefs.put(key, v == null ? "" : v);
    }
}
