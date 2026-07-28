import os
src = "src/main/java/com/oculus/jj/config/AppPreferences.java"
with open(src, encoding="utf-8") as f:
    lines = f.read().split("\n")

# Keys to add after KEY_CHECK_UPDATES (line 26) — General pane JP2K + compression
ins1 = [
    "    // HOROS-20240407準拠: OSIGeneralPreferencePanePref.m — JP2KEngine/Kakadu or OpenJPEG",
    '    public static final String KEY_JP2K_ENGINE = "JP2KEngine";',
    '    public static final String KEY_COMPRESSION_SETTINGS_JSON = "CompressionSettings";',
    '    public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes"'
]

# Keys to add after KEY_ERASE_DB_AT_STARTUP (line 34) — Database pane UID/date format
ins2 = [
    "    // HOROS-20240407準拠: OSIDatabasePreferencePanePref.m — UID生成/日付フォーマット",
    '    public static final String KEY_USE_PATIENT_NAME_FOR_UID = "usePatientNameForUID";',
    '    public static final String KEY_USE_PATIENT_BIRTH_DATE_FOR_UID = "usePatientBirthDateForUID";',
    '    public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = "AutoCleanDaysToKeep";',
    '    public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";',
    '    public static final String KEY_DB_BIRTH_DATE_FORMAT = "DBDateOfBirthFormat2";',
    '    public static final String KEY_USE_SERIES_DESCRIPTION = "useSeriesDescription";',
    '    public static final String KEY_SPLIT_MULTI_ECHO_MR = "splitMultiEchoMR"'
]

# Keys after KEY_HOTKEYS (line 58) — Hanging/HOTKEYS/annotations/etc.
ins3 = [
    "",
    "    // HOROS-20240407準拠: annotation DICOM field map, hanging protocol body/keyword lists",
    '    public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";',
    '    public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = "hangingProtocolBodyRegions";',
    '    public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";',
    '    public static final String KEY_PET_SUV_NORMALIZE_SCANNER = "pet.suvNormalizeScanner";',
    '    public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList"',
    "",
    "    // HOROS-20240407準拠: OSIHotKeysPref.m — ホットキーバインディング管理(JSON配列)",
    '    public static final String KEY_HOTKEY_BINDINGS_JSON = "hotkeyBindingsJSON"'
]

# TLS keys before "// === Web Server" section (line 109)
idx4 = lines.index([l for l in lines if '// === Web Server' in l][0])
ins4 = [
    "",
    "    // HOROS-20240407準拠: OSIListenerPreferencePanePref.m — TLS認証/暗号化スイート等",
    '    public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
    '    public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";',
    '    public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
    '    public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";',
    '    public static final String KEY_LISTENER_CERT_VERIFY_LEVEL = "TLSCertificateVerification";',
    '    public static final String KEY_LISTENER_TLS_USE_SAME_AETITLE = "TLSUseSameAETitle";',
    '    public static final String KEY_LISTENER_TLS_STORESCP_AETITLE = "TLSStoreSCPAETITLEIsDefaultAET"'
]

# WADO/User mgmt keys after KEY_WEB_SERVER_PORT (line 110)
idx5 = lines.index([l for l in lines if "KEY_WEB_SERVER_PORT" in l][0])
ins5 = [
    "",
    "    // HOROS-20240407準拠: OSILocationsPreferencePane / OSIWebSharingPreferencePane",
    '    public static final String KEY_WADO_ENABLED = "WADOEnabled";',
    '    public static final String KEY_WADO_PORT = "wadomoduleportnumber";',
    '    public static final String KEY_WADO_URL = "wadoURL";',
    '    public static final String KEY_WADO_TRANSFER_SYNTAX = "WADOTransferSyntaxes";',
    '    public static final String KEY_WADO_USERNAME = "WADOTokenUsername";',
    '    public static final String KEY_WADO_PASSWORD = "WADOTokenPassword";',
    '    public static final String KEY_WEB_PORTAL_URL = "WebPortalURL";',
    '    public static final String KEY_CUSTOM_HTML_FILE_PATH = "customizedHTMLFilePaths";',
    '    public static final String KEY_USERS_MANAGEMENT_JSON = "webUsersManagementJSON"'
]

# TLS per-listener keys after KEY_LISTENER_AUTO_START (line 107)
idx6 = lines.index([l for l in lines if "KEY_LISTENER_AUTO_START" in l][0])
ins6 = [
    "    // HOROS-20240407準拠: listener TLS per-listener",
    '    public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";',
    '    public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS"'
]

# Apply in reverse order (largest index first to avoid shifting)
ops = [(idx5, ins5), (idx4, ins4), (idx6, ins6), (27, ins3)]  # positions: 5=109, 4=108, 6=106, 3=27

for pos, keys in ops:
    for k in keys:
        lines.insert(pos, k)

# Convert back to lines with newline and write
with open(src, "w", encoding="utf-8") as f:
    for line in lines:
        f.write(line + "\n")

final = sum(1 for _ in open(src))
print(f"AppPreferences.java updated: {final} lines (was 621)")
