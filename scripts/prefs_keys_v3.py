src = "src/main/java/com/oculus/jj/config/AppPreferences.java"
with open(src, encoding="utf-8") as f:
    lines = f.read().split("\n")

# Helper to find line index for a marker
def idx_of(marker):
    found = [i for i, l in enumerate(lines) if marker in l]
    return found[0] if found else -1

# Define all insertions with their target positions and content
inserts = []

# 1. After line 26 (KEY_CHECK_UPDATES=..., index 25) — General JP2K/compression keys
i1_pos = idx_of("KEY_CHECK_UPDATES") + 1  
ins1 = [
    '    // HOROS-20240407準拠: OSIGeneralPreferencePanePref.m - JP2KEngine=Kakadu or OpenJPEG',
    "    public static final String KEY_JP2K_ENGINE = \"JP2KEngine\";",
    "    public static final String KEY_COMPRESSION_SETTINGS_JSON = \"CompressionSettings\";",
    '    public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes"'
]

# 2. After line 34 (KEY_ERASE_DB_AT_STARTUP=..., index 33) - Database UID/date format
i2_pos = idx_of("KEY_ERASE_DB_AT_STARTUP") + 1
ins2 = [
    '    // HOROS-20240407準拠: OSIDatabasePreferencePanePref.m - UID生成/日付フォーマット',
    "    public static final String KEY_USE_PATIENT_NAME_FOR_UID = \"usePatientNameForUID\";",
    "    public static final String KEY_USE_PATIENT_BIRTH_DATE_FOR_UID = \"usePatientBirthDateForUID\";",
    '    public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = "AutoCleanDaysToKeep";',
    '    public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";',
    '    public static final String KEY_DB_BIRTH_DATE_FORMAT = "DBDateOfBirthFormat2";',
    "    public static final String KEY_USE_SERIES_DESCRIPTION = \"useSeriesDescription\";",
    '    public static final String KEY_SPLIT_MULTI_ECHO_MR = "splitMultiEchoMR"'
]

# 3. After line 58 (KEY_HOTKEYS=..., index 57) - Hanging/HOTKEYS/annotations etc.
i3_pos = idx_of('public static final String KEY_HOTKEYS') + 1
ins3 = [
    "",
    "    // HOROS-20240407準拠: annotation DICOM field map, hanging protocol body/keyword lists",
    '    public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";',
    '    public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = "hangingProtocolBodyRegions";',
    '    public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";',
    '    public static final String KEY_PET_SUV_NORMALIZE_SCANNER = "pet.suvNormalizeScanner";',
    '    public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList"',
    "",
    "    // HOROS-20240407準拠: OSIHotKeysPref.m - hotkey bindings JSON array",
    '    public static final String KEY_HOTKEY_BINDINGS_JSON = "hotkeyBindingsJSON"'
]

# 4. Before "// === Web Server" section (line ~108) - Listener TLS keys
i4_pos = idx_of("// === Web Server")
ins4 = [
    "",
    "    // HOROS-20240407準拠: OSIListenerPreferencePanePref.m - TLS認証/暗号化スイート等",
    '    public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
    '    public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";',
    '    public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
    '    public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";',
    '    public static final String KEY_LISTENER_CERT_VERIFY_LEVEL = "TLSCertificateVerification";',
    '    public static final String KEY_LISTENER_TLS_USE_SAME_AETITLE = "TLSUseSameAETitle";',
    '    public static final String KEY_LISTENER_TLS_STORESCP_AETITLE = "TLSStoreSCPAETITLEIsDefaultAET"'
]

# 5. After KEY_WEB_SERVER_PORT (line ~109) - WADO/User mgmt keys
i5_pos = idx_of("KEY_WEB_SERVER_PORT") + 1
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

# 6. After KEY_LISTENER_AUTO_START (line ~106) - TLS per-listener keys
i6_pos = idx_of("KEY_LISTENER_AUTO_START") + 1
ins6 = [
    '    // HOROS-20240407準拠: listener TLS per-listener',
    '    public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";',
    '    public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS"'
]

# Sort by position DESCENDING so earlier inserts don't shift later ones
all_ops = [(i1_pos, ins1), (i2_pos, ins2), (i3_pos, ins3), (i4_pos, ins4), (i5_pos, ins5), (i6_pos, ins6)]
# Filter out invalid positions and sort descending by position
valid_ops = [(p, k) for p, k in all_ops if 0 <= p <= len(lines)]
print(f"Insertion plan (positions to insert AFTER):")
for i, k in valid_ops:
    print(f"  pos={i} (line {i+1}): {len(k)} lines")

# Sort by position descending - insert largest first so smaller indices stay stable
valid_ops.sort(key=lambda x: x[0], reverse=True)
print("Applying in order:")
for p, k in valid_ops:
    for item in k:
        lines.insert(p, item)
    print(f"  inserted {len(k)} lines at pos={p}")

with open(src, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))

final = sum(1 for _ in open(src))
print(f"\nDone! AppPreferences.java: now has {final} lines (was 621)")
