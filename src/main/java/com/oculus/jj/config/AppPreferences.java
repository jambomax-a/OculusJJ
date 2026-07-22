package com.oculus.jj.config;




import java.util.List;

import java.util.prefs.Preferences;

import java.awt.Color;
import com.oculus.jj.model.KeyBinding;



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

// HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m - JP2K engine / compression settings

public static final String KEY_JP2K_ENGINE = "JP2KEngine";

public static final String KEY_COMPRESSION_SETTINGS_JSON = "CompressionSettings";

public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes";

    public static final String KEY_SEPARATE_WINDOW = "general.separateWindowPerStudy";

    // === Database auto-cleaning preference keys (HOROS-20240407準拠: AUTOCLEANINGDATEPRODUCED etc.) ===

    public static final String KEY_AUTO_CLEANING_DATE_PRODUCED = "db.autoCleaningDateProduced";

    public static final String KEY_AUTO_CLEANING_DATE_OPENED = "db.autoCleaningDateOpened";

    public static final String KEY_AUTO_CLEANING_COMMENTS = "db.autoCleaningComments";

// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - DB date formats

public static final String KEY_DB_BIRTH_DATE_FORMAT = "DBDateOfBirthFormat2";

public static final String KEY_SPLIT_MULTI_ECHO_MR = "splitMultiEchoMR";

    public static final String KEY_AUTO_CLEANING_DELETE_ORIGINAL = "db.autoCleaningDeleteOriginal";

    public static final String KEY_PACS_MERGE_ON_IMPORT = "pacs.mergeOnImport";

    public static final String KEY_ERASE_DB_AT_STARTUP = "database.eraseEntireDBAtStartup";

// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - UID generation / date formats

public static final String KEY_USE_PATIENT_NAME_FOR_UID = "usePatientNameForUID";

public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = "AutoCleanDaysToKeep";

public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";

public static final String KEY_USE_SERIES_DESCRIPTION = "useSeriesDescription";



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





    // HOROS-20240407準拠: DiscBurningOptionsIncludeOsirixLiteArchivingKey

    public static final String KEY_BURN_INCLUDE_HOROS_LITE = "includeOsirixLite";

    // === Hanging Protocol preference keys ===

    public static final String KEY_HANGING_MODALITY = "hanging.defaultModality";

    public static final String KEY_HANGING_TILING = "hanging.defaultTiling";

    public static final String KEY_HANGING_WLWW_PRESET = "hanging.defaultWLWWPreset";



    // === Hot Keys preference keys (stored as JSON string for simplicity) ===

    public static final String KEY_HOTKEYS = "hotkeys.bindings";



// HOROS-20240407 Reference: annotations / hanging protocols / on-demand PACS

public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = "hangingProtocolBodyRegions";

public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";

public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";

public static final String KEY_PET_SUV_NORMALIZE_SCANNER = "pet.suvNormalizeScanner";

public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList";



    // === Viewers preference keys ===

    public static final String KEY_DEFAULT_CLUT = "viewers.defaultCLUT";

    public static final String KEY_DEFAULT_OPACITY_TABLE = "viewers.defaultOpacityTable";

    public static final String KEY_SYNC_SERIES_BETWEEN_STUDIES = "viewers.syncSeriesBetweenStudies";

    public static final String KEY_VIEWER_WINDOW_SIZE_MODE = "viewers.windowSizeMode";
    public static final int DEFAULT_VIEWER_WINDOW_SIZE_MODE = 0;

    public static final String KEY_MONITOR_INDEX = "viewers.monitorIndex";
    public static final int DEFAULT_MONITOR_INDEX = 0;

    public static final String KEY_SECONDARY_MONITOR_ENABLED = "viewers.secondaryMonitorEnabled";
    public static final boolean DEFAULT_SECONDARY_MONITOR_ENABLED = false;

    public static final String KEY_KEEP_CLUT_BARS_FOR_SECONDARY_CAPTURE = "viewers.keepCLUTBarsForSecondaryCapture";
    public static final boolean DEFAULT_KEEP_CLUT_BARS = false;





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

    public static final String KEY_BROWSER_FONT_SIZE = "browser.fontSize";  // HOROS-20240407準拠: dbFontSize

    public static final String KEY_DB_DEFAULT_FOLDER_SIZE = "database.defaultFolderSize"; // HOROS-20240407準拠: DefaultFolderSizeForDB

    public static final String KEY_SEARCH_TYPE = "browser.searchType";  // HOROS-20240407準拠: searchType (0..4)

    public static final String KEY_HIDE_PATIENT_NAME = "browser.hidePatientName"; // HOROS-20240407準拠: HIDEPATIENTNAME

    public static final String KEY_LAST_URL = "web.lastURL";  // HOROS-20240407準拠: LASTURL (最後に使用したWADO URL)

    public static final String KEY_AUTO_TILING = "browser.autoTiling"; // HOROS-20240407準拠: AUTOTILING

    public static final String KEY_AUTO_HIDE_MATRIX = "browser.autoHideMatrix";  // HOROS-20240407準拠: AUTOHIDEMATRIX

    public static final String KEY_CLEAR_SEARCH_WHEN_SELECTING_ALBUM = "browser.clearSearchWhenSelectingAlbum";  // HOROS-20240407準拠: clearSearchAndTimeIntervalWhenSelectingAlbum

    public static final String KEY_COMMENT_AUTOFILL = "comments.autoFill"; // HOROS-20240407準拠: COMMENTSAUTOFILL

    public static final String KEY_COMMENT_AUTOFILL_STUDY_LEVEL = "comments.autoFillStudyLevel";  // HOROS-20240407準拠: COMMENTSAUTOFILLStudyLevel

    public static final String KEY_COMMENT_AUTOFILL_SERIES_LEVEL = "comments.autoFillSeriesLevel";  // HOROS-20240407準拠: COMMENTSAUTOFILLSeriesLevel

    public static final String KEY_ANNOTATION_FONT_BOLD = "annotations.fontBold";  // HOROS equivalent (annotationFontBold)

    public static final String KEY_ANNOTATION_FONT_SIZE = "annotations.fontSize";  // HOROS equivalent (annotationFontSize)

    public static final String KEY_LISTENER_AETITLE = "listener.aetitle";

    public static final String KEY_LISTENER_PORT = "listener.port";

    public static final String KEY_LISTENER_AUTO_START = "listener.autoStart";

// HOROS-20240407 Reference: listener TLS per-listener

public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";

public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS";





// HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm - TLS settings

public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";

public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";

public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";

public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";
    public static void setListenerDHParamFileURL(String url) { prefs.put(KEY_LISTENER_DH_PARAM_FILE_URL, url); }
    /** DH parameter file URL setter */

    public static String getListenerDHParamFileURL() { return prefs.get(KEY_LISTENER_DH_PARAM_FILE_URL, ""); }
    /** DH parameter file URL getter */


    // === Web Server preference keys ===

    public static final String KEY_WEB_SERVER_PORT = "webserver.port";



// HOROS-20240407 Reference: OSILocationsPreferencePane / OSIWebSharingReferencePane

public static final String KEY_WADO_ENABLED = "WADOEnabled";

public static final String KEY_WADO_PORT = "wadomoduleportnumber";

public static final String KEY_WADO_URL = "wadoURL";

public static final String KEY_WADO_TRANSFER_SYNTAX = "WADOTransferSyntaxes";

public static final String KEY_WEB_PORTAL_URL = "WebPortalURL";

public static final String KEY_CUSTOM_HTML_FILE_PATH = "customizedHTMLFilePaths";

public static final String KEY_USERS_MANAGEMENT_JSON = "webUsersManagementJSON";

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

    private static final boolean DEFAULT_3D_SHADING = true;

    private static final int DEFAULT_ROI_COLOR = Color.YELLOW.getRGB();



    /** デフォルトのDICOMフォルダパス */

    // --- Fusion preferences ---

    public static com.oculus.jj.model.FusionImageProcessor.FusionMode getFusionMode() {

        String modeStr = prefs.get(KEY_FUSION_MODE, "COLOR");

        try {

            return com.oculus.jj.model.FusionImageProcessor.FusionMode.valueOf(modeStr);

        } catch (IllegalArgumentException e) {

            return com.oculus.jj.model.FusionImageProcessor.FusionMode.COLOR;

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

        return prefs.get(KEY_APP_LANGUAGE, "en");

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



    // HOROS-20240407準拠: DiscBurningOptionsIncludeOsirixLiteArchivingKey (@"includeOsirixLite")

    public static boolean getBurnIncludeHorosLite() {

        return prefs.getBoolean(KEY_BURN_INCLUDE_HOROS_LITE, false);

    }



    public static void setBurnIncludeHorosLite(boolean include) {

        prefs.putBoolean(KEY_BURN_INCLUDE_HOROS_LITE, include);

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





    public static int getViewerWindowSizeMode() {
        return prefs.getInt(KEY_VIEWER_WINDOW_SIZE_MODE, DEFAULT_VIEWER_WINDOW_SIZE_MODE);
    }

    public static void setViewerWindowSizeMode(int mode) {
        prefs.putInt(KEY_VIEWER_WINDOW_SIZE_MODE, mode);
    }


    public static String getDefaultViewerWindowSizesAsString() {
        int mode = getViewerWindowSizeMode();
        switch (mode) {
            case 0: return "800x600";
            case 1: return "1024x768";
            case 2: return "1280x960";
            case 3: return "1920x1080";
            default: return "800x600";
        }
    }


    public static int getMonitorIndex() {
        return prefs.getInt(KEY_MONITOR_INDEX, DEFAULT_MONITOR_INDEX);
    }

    public static void setMonitorIndex(int index) {
        prefs.putInt(KEY_MONITOR_INDEX, index);
    }


    public static boolean isSecondaryMonitorEnabled() {
        return prefs.getBoolean(KEY_SECONDARY_MONITOR_ENABLED, DEFAULT_SECONDARY_MONITOR_ENABLED);
    }

    public static void setSecondaryMonitorEnabled(boolean enabled) {
        prefs.putBoolean(KEY_SECONDARY_MONITOR_ENABLED, enabled);
    }


    public static boolean isKeepCLUTBarsForSecondCapture() {
        return prefs.getBoolean(KEY_KEEP_CLUT_BARS_FOR_SECONDARY_CAPTURE, DEFAULT_KEEP_CLUT_BARS);
    }

    public static void setKeepCLUTBarsForSecondCapture(boolean keep) {
        prefs.putBoolean(KEY_KEEP_CLUT_BARS_FOR_SECONDARY_CAPTURE, keep);
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

        return prefs.getBoolean(KEY_LISTENER_AUTO_START, false);

    }



    public static void setListenerAutoStart(boolean auto) {

        prefs.putBoolean(KEY_LISTENER_AUTO_START, auto);

    }



    // --- Web Server ---

    public static int getWebServerPort() {

        return prefs.getInt(KEY_WEB_SERVER_PORT, 8080);

    }



    public static void setWebServerPort(int port) {

        prefs.putInt(KEY_WEB_SERVER_PORT, port);

    }



    public static boolean isWadoEnabled() {

        return prefs.getBoolean(KEY_WEB_WADO_ENABLED, true);

    }



    public static void setWADOEnabled(boolean wado) {

        prefs.putBoolean(KEY_WEB_WADO_ENABLED, wado);

    }



    public static boolean isStowrsEnabled() {

        return prefs.getBoolean(KEY_WEB_STOWRS_ENABLED, false);

    }



    public static void setStowrsEnabled(boolean stowrs) {

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



/* ========== HOROS-20240407 Reference Data Accessors (Preference Pane) ========== */



// -- General pane - JP2K Engine / Compression Settings



public static String getJP2KEngine() { return prefs.get(KEY_JP2K_ENGINE, "OpenJPEG"); }

public static void setJP2KEngine(String e) { prefs.put(KEY_JP2K_ENGINE, e); }

public static String getCompressionSettings() { return prefs.get(KEY_COMPRESSION_SETTINGS_JSON, ""); }

public static void setCompressionSettings(String j) { prefs.put(KEY_COMPRESSION_SETTINGS_JSON, j); }

// -- Database preferences - UID generation, auto-clean, date formats



public static String getUsePatientNameForUID() { return prefs.get(KEY_USE_PATIENT_NAME_FOR_UID, ""); }

public static void setUsePatientNameForUID(String m) { prefs.put(KEY_USE_PATIENT_NAME_FOR_UID, m); }

public static int getAutoCleanDaysToKeep() { try { return prefs.getInt(KEY_AUTO_CLEAN_DAYS_TO_KEEP, 90); } catch (Exception e) { return 90; } }

public static void setAutoCleanDaysToKeep(int d) { prefs.put(KEY_AUTO_CLEAN_DAYS_TO_KEEP, String.valueOf(d)); }

// -- DB useSeriesDescription / splitMultiEchoMR (HOROS-20240407 Reference)



public static boolean isUseSeriesDescription() { return prefs.getBoolean(KEY_USE_SERIES_DESCRIPTION, false); }

public static void setUseSeriesDescription(boolean b) { prefs.putBoolean(KEY_USE_SERIES_DESCRIPTION, b); }

// -- Listener TLS authentication / cipher suites (HOROS-20240407 Reference)



public static boolean isListenerTLSEnabled() { return prefs.getBoolean(KEY_LISTENER_TLS_ENABLED, false); }
    public static void setListenerTLSCipherSuite(String cipher) { prefs.put(KEY_LISTENER_TLS_CIPHER_SUITE, cipher); }
    /** TLS Cipher Suite setter */

    public static String getListenerTLSCipherSuite() { return prefs.get(KEY_LISTENER_TLS_CIPHER_SUITE, "TLS_AES_256_GCM_SHA384"); }
    /** TLS Cipher Suite getter */


public static void setListenerTLSEnabled(boolean b) { prefs.putBoolean(KEY_LISTENER_TLS_ENABLED, b); }

public static String getListenerTLSAuthCertName() { return prefs.get(KEY_LISTENER_TLS_CERT_AUTH, ""); }

public static void setListenerTLSAuthCertName(String n) { prefs.put(KEY_LISTENER_TLS_CERT_AUTH, n); }

// -- WADO/WebServer settings (HOROS-20240407 Reference)



public static int getWadoPort() { try { return prefs.getInt(KEY_WADO_PORT, 8443); } catch (Exception e) { return 8443; } }

public static void setWadoPort(int p) { prefs.put(KEY_WADO_PORT, String.valueOf(p)); }

// -- Web users JSON (HOROS-20240407 Reference: OSIWebSharingReferencePane)



public static String getUserManagementJSON() { return prefs.get(KEY_USERS_MANAGEMENT_JSON, "[]"); }

public static void setUserManagementJSON(String j) { prefs.put(KEY_USERS_MANAGEMENT_JSON, j); }

// -- Other preferences (HOROS-20240407 Reference)



public static String getHangingProtocolBodyRegionsJSON() { return prefs.get(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, "[]"); }

public static void setHangingProtocolBodyRegionsJSON(String j) { prefs.put(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, j); }

// -- Hot key bindings (HOROS-20240407 Reference: OSIHotKeysPref.m)

    // -- Hot key bindings (HOROS-20240407 Reference: DCMView.m actionForHotKey:) --

    /**
     * 登録済みホットキーバインディングを KeyBinding のリストとして取得する。
     */
    public static java.util.List<com.oculus.jj.model.KeyBinding> getHotkeyBindings() {
        try {
            String json = prefs.get("hotkeyBindingsJSON", "[]");
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.core.type.TypeReference<List<KeyBinding>> typeRef =
                    new com.fasterxml.jackson.core.type.TypeReference<List<KeyBinding>>() {};
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * ホットキーバインディングのリストを JSON 文字列として保存する。
     */
    public static void setHotkeyBindings(java.util.List<com.oculus.jj.model.KeyBinding> bindings) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(bindings);
            prefs.put("hotkeyBindingsJSON", json);
        } catch (Exception e) {
            // 保存失敗は無視 - 既存データを維持する
        }
    }

    /**
     * ホットキーバインディングに新しいエントリを追加する。
     */
    public static boolean addHotkeyBinding(String displayName, String keyCombo, int actionId) {
        try {
            List<com.oculus.jj.model.KeyBinding> bindings = getHotkeyBindings();
            com.oculus.jj.model.KeyBinding entry = new com.oculus.jj.model.KeyBinding(displayName, keyCombo, actionId);
            // 既存と同じキーコンボを置換する
            boolean replaced = false;
            for (int i = 0; i < bindings.size(); i++) {
                if (bindings.get(i).keyCombo.equalsIgnoreCase(keyCombo)) {
                    bindings.set(i, entry);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                bindings.add(entry);
            }
            setHotkeyBindings(bindings);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * キーコンボでバインディングを削除する。
     */
    public static boolean removeHotkeyBinding(String keyCombo) {
        try {
            List<com.oculus.jj.model.KeyBinding> bindings = getHotkeyBindings();
            for (int i = 0; i < bindings.size(); i++) {
                if (bindings.get(i).keyCombo.equalsIgnoreCase(keyCombo)) {
                    bindings.remove(i);
                    setHotkeyBindings(bindings);
                    return true;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    /**
     * キーコンボでバインディングを取得する。
     */
    public static com.oculus.jj.model.KeyBinding getHotkeyBinding(String keyCombo) {
        try {
            List<com.oculus.jj.model.KeyBinding> bindings = getHotkeyBindings();
            for (com.oculus.jj.model.KeyBinding b : bindings) {
                if (b.keyCombo.equalsIgnoreCase(keyCombo)) {
                    return b;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * デフォルトのホットキーバインディングを初期化する。
     */
    public static void resetHotkeyBindings() {
        String json = """
[{"displayName":"画面全体表示","keyCombo":"F1","actionId":0},{"displayName":"3D同期","keyCombo":"Ctrl+Shift+S","actionId":1},
{"displayName":"キー画像設定","keyCombo":"Cmd+K","actionId":2},{"displayName":"反転 (上下)","keyCombo":"Alt+F1","actionId":3},
{"displayName":"反転 (左右)","keyCombo":"Alt+F2","actionId":4},{"displayName":"ツールモード リップルサー","keyCombo":"E","actionId":-1}]
""";
        prefs.put("hotkeyBindingsJSON", json);
    }

// -- Annotation DICOM field map (HOROS-20240407 Reference)



public static String getAnnotationDicomFieldMapJSON() { return prefs.get(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, "{}"); }

public static void setAnnotationDicomFieldMapJSON(String j) { prefs.put(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, j); }

// -- PET/SUV scanner normalize (HOROS-20240407 Reference)



public static String getPetsuvNormalizeScanner() { return prefs.get(KEY_PET_SUV_NORMALIZE_SCANNER, ""); }

public static void setPetsuvNormalizeScanner(String s) { prefs.put(KEY_PET_SUV_NORMALIZE_SCANNER, s); }

// -- On-demand PACS source list (HOROS-20240407 Reference)



public static String getOnDemandSourceListJSON() { return prefs.get(KEY_ONDEMAND_SOURCE_LIST_JSON, "[]"); }

public static void setOnDemandSourceListJSON(String j) { prefs.put(KEY_ONDEMAND_SOURCE_LIST_JSON, j); }

}