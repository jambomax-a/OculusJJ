#!/usr/bin/env python3
"""Add all missing HOROS Reference keys and getters/setters to AppPreferences.java."""
import os

SRC = "src/main/java/com/oculus/jj/config/AppPreferences.java"

with open(SRC, encoding="utf-8") as f:
    L = f.read().split("\n")

orig_count = len(L)
print(f"Original lines: {orig_count}")

# --- Phase 1: Find insertion points (original file indices) ---
def find_in(orig, start=0):
    for i in range(start, len(L)):
        if orig in L[i]:
            return i
    raise Exception(f"Not found in original file: {orig!r}")

# Original 0-indexed positions for markers
idx_check_updates = find_in("KEY_CHECK_UPDATES")     # ~25
idx_erase_db = find_in("KEY_ERASE_DB_AT_STARTUP")      # ~33
idx_hotkeys = find_in('public static final String KEY_HOTKEYS')  # ~57

# TLS keys: insert BEFORE "// === Web Server" section comment (around original index 108)
# But we need to put them AFTER Listener keys and before Web Server.
# The cleanest place is right after the listener section but before Web Server comment
idx_before_web = find_in("// === Web Server")

# WADO/User mgmt: after KEY_WEB_SERVER_PORT (around original index 109)
idx_after_webport = find_in("KEY_WEB_SERVER_PORT") + 1

# TLS per-listener: after KEY_LISTENER_AUTO_START (around original ~106)
idx_after_listener_auto = find_in("KEY_LISTENER_AUTO_START") + 1

# Sort descending positions, apply from largest to smallest
ops = [
    (idx_before_web, "before Web Server section"),
    (idx_check_updates + 1, "General pane (JP2K/compression) after KEY_CHECK_UPDATES"),
    (idx_erase_db + 1, "Database pane (UID/date format) after KEY_ERASE_DB_AT_STARTUP"),
]

ops_with_content = [
    # After KEY_CHECK_UPDATES (line ~26): General JP2K/compression keys
    (idx_check_updates + 1, [
        "// HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m - JP2K engine / compression settings",
        "public static final String KEY_JP2K_ENGINE = \"JP2KEngine\";",
        'public static final String KEY_COMPRESSION_SETTINGS_JSON = "CompressionSettings";',
        'public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes";'
    ]),
    
    # After KEY_ERASE_DB_AT_STARTUP (line ~34): Database UID/date format keys  
    (idx_erase_db + 1, [
        "// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - UID generation / date formats",
        'public static final String KEY_USE_PATIENT_NAME_FOR_UID = "usePatientNameForUID";',
        'public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = "AutoCleanDaysToKeep";',
        'public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";',
        'public static final String KEY_USE_SERIES_DESCRIPTION = "useSeriesDescription";'
    ]),

    # After KEY_HOTKEYS (line ~58): Annotations/Hanging/OnDemand keys
    (idx_hotkeys + 1, [
        "",
        "// HOROS-20240407 Reference: Hanging Protocol / Hot Keys / Annotations / On-Demand PACS",
        'public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = "hangingProtocolBodyRegions";',
        'public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";',
        'public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";',
        'public static final String KEY_PET_SUV_NORMALIZE_SCANNER = "pet.suvNormalizeScanner";',
        'public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList";'
    ]),

    # Before "// === Web Server" section: Listener TLS keys
    (idx_before_web, [
        "",
        "// HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm - TLS settings",
        'public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
        'public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";',
        'public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
        'public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";'
    ]),

    # After KEY_WEB_SERVER_PORT: WADO/User mgmt keys
    (idx_after_webport, [
        "",
        "// HOROS-20240407 Reference: OSILocationsPreferencePane / OSIWebSharingPreferencePane",
        'public static final String KEY_WADO_ENABLED = "WADOEnabled";',
        'public static final String KEY_WADO_PORT = "wadomoduleportnumber";',
        'public static final String KEY_WADO_URL = "wadoURL";' ,  # note: key is different from LASTURL
        'public static final String KEY_WADO_TRANSFER_SYNTAX = "WADOTransferSyntaxes";',
        'public static final String KEY_WEB_PORTAL_URL = "WebPortalURL";',
        'public static final String KEY_CUSTOM_HTML_FILE_PATH = "customizedHTMLFilePaths";' ,  # note: key differs from HOROS WebServicesCustomHTMLPath
        'public static final String KEY_USERS_MANAGEMENT_JSON = "webUsersManagementJSON";'  # user name/password management
    ]),

    # After KEY_LISTENER_AUTO_START: TLS per-listener keys
    (idx_after_listener_auto, [
        "// HOROS-20240407 Reference: listener TLS per listener",
        'public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";',
        'public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS";'
    ]),

    # Additional database keys (UID format) at end of auto-cleaning section  
    (idx_erase_db + 1, [
        "// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - UID date formats",
        'public static final String KEY_DB_BIRTH_DATE_FORMAT = "DBDateOfBirthFormat2";',
        'public static final String KEY_SPLIT_MULTI_ECHO_MR = "splitMultiEchoMR";'
    ]),
]

# Sort by position descending (largest first) - ensures earlier indices stay valid during insertion  
ops_with_content.sort(key=lambda x: x[0], reverse=True)
print("Insertion plan (descending positions):")
for pos, desc in ops_with_content:
    print(f"  Position {pos} (L{pos+1}): {len(desc)} lines")

# Apply all insertions  
for pos, keys in ops_with_content:
    for k in reversed(keys):
        L.insert(pos, k)
    # Verify no shift of earlier indices (since we're inserting at largest first)
    
print(f"\nAfter key insertion: {len(L)} lines")

# --- Phase 2: Add getters/setters before final class closing brace ---
# Find the line index of the final '}' which closes the class
final_brace = None
for i in range(len(L) - 1, -1, -1):
    if L[i].strip() == '}':
        final_brace = i
        break

if final_brace is not None:
    print(f"Found closing brace at original position {final_brace}")
else:
    raise Exception("Could not find class closing brace")

# Insert getters/setters just before the closing brace (insert_pos means: insert AFTER this line)
get_inserts = [
    "", "/* ========== HOROS-20240407 Reference Data Accessors ========== */", ""
]

def add_getset(pos, lines_to_insert):
    for k in reversed(lines_to_insert):
        L.insert(pos, k)

# General: JP2K + compression settings
add_getset(final_brace - 1, [
    '    // HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m',
    'public static String getJP2KEngine() { return prefs.get(KEY_JP2K_ENGINE, "OpenJPEG"); }'
])

add_getset(final_brace - 1, [
    'public static void setJP2KEngine(String engine) { prefs.put(KEY_JP2K_ENGINE, engine); }'
])

# General: Compression settings (JSON string for simplicity)
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: compressionSettingsCopy in OSIGeneral.m',
    'public static String getCompressionSettings() { return prefs.get(KEY_COMPRESSION_SETTINGS_JSON, ""); }'
])

add_getset(final_brace - 1, [
    'public static void setCompressionSettings(String json) { prefs.put(KEY_COMPRESSION_SETTINGS_JSON, json); }'
])

# Database: UID generation (string key like "PATIENTNAME" / "PATIENTBIRTHDATE") 
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - newUsePatientNameForUID etc.',
    'public static String getUsePatientNameForUID() { return prefs.get(KEY_USE_PATIENT_NAME_FOR_UID, ""); }'
])

add_getset(final_brace - 1, [
    'public static void setUsePatientNameForUID(String method) { prefs.put(KEY_USE_PATIENT_NAME_FOR_UID, method); }'
])

# Database: Auto-clean days to keep (Integer)  
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: AutoCleanDaysToKeep in OSIDatabase.m',
    'public static int getAutoCleanDaysToKeep() { try { return prefs.getInt(KEY_AUTO_CLEAN_DAYS_TO_KEEP, 90); } catch (NumberFormatException e) { return 90; } }'
])

add_getset(final_brace - 1, [
    'public static void setAutoCleanDaysToKeep(int days) { prefs.putInt(KEY_AUTO_CLEAN_DAYS_TO_KEEP, String.valueOf(days)); }'
])

# Listener TLS
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm',
    'public static boolean isListenerTLSEnabled() { return prefs.getBoolean(KEY_LISTENER_TLS_ENABLED, false); }'
])

add_getset(final_brace - 1, [
    'public static void setListenerTLSEnabled(boolean enabled) { prefs.putBoolean(KEY_LISTENER_TLS_ENABLED, enabled); }'
])

# Locations: WADO port  
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: OSILocationsPreferencePane / OSIWebSharingPreferencePane',
    'public static int getWadoPort() { try { return prefs.getInt(KEY_WADO_PORT, 8443); } catch (NumberFormatException e) { return 8443; } }'
])

add_getset(final_brace - 1, [
    'public static void setWadoPort(int port) { prefs.putInt(KEY_WADO_PORT, String.valueOf(port)); }'
])

# Web Server: User management JSON (for user name/password list from OSIWebSharing.m usersArrayController)  
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: OSILocationsPreferencePane / OSIWebSharingReference',
    'public static String getUserManagementJSON() { return prefs.get(KEY_USERS_MANAGEMENT_JSON, "[]"); }'
])

add_getset(final_brace - 1, [
    'public static void setUserManagementJSON(String json) { prefs.put(KEY_USERS_MANAGEMENT_JSON, json); }'
])

# Hanging protocol body regions / keywords JSON arrays  
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: BodyRegionArrayController.m / KeywordsArrayController.m',
    'public static String getHangingProtocolBodyRegionsJSON() { return prefs.get(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, "[]"); }'
])

add_getset(final_brace - 1, [
    'public static void setHangingProtocolBodyRegionsJSON(String json) { prefs.put(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, json); }'
])

# HotKeys JSON bindings array
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: OSIHotKeysPref.m (stored as JSON string)',
    'public static String getHotkeyBindingsJSON() { return prefs.get("hotkeyBindingsJSON", "[]"); }'
])

add_getset(final_brace - 1, [
    'public static void setHotkeyBindingsJSON(String json) { prefs.put("hotkeyBindingsJSON", json); }'
])

# Annotations DICOM field map JSON  
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: OSICustomImageAnnotations.m / CIALayoutController.m',
    'public static String getAnnotationDicomFieldMapJSON() { return prefs.get(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, "{}"); }'
])

add_getset(final_brace - 1, [
    'public static void setAnnotationDicomFieldMapJSON(String json) { prefs.put(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, json); }'
])

# PET/SUV normalization scanner identifier  
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: OSIPETPreferencePane',
    'public static String getPetsuvNormalizeScanner() { return prefs.get(KEY_PET_SUV_NORMALIZE_SCANNER, ""); }'
])

add_getset(final_brace - 1, [
    'public static void setPetsuvNormalizeScanner(String scanner) { prefs.put(KEY_PET_SUV_NORMALIZE_SCANNER, scanner); }'
])

# On-Demand PACS source list JSON  
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: OSIPACSOnDemandPreferencePane',
    'public static String getOnDemandSourceListJSON() { return prefs.get(KEY_ONDEMAND_SOURCE_LIST_JSON, "[]"); }'
])

add_getset(final_brace - 1, [
    'public static void setOnDemandSourceListJSON(String json) { prefs.put(KEY_ONDEMAND_SOURCE_LIST_JSON, json); }'
])

# Database: Use series description toggle (boolean)  
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m',
    'public static boolean isUseSeriesDescription() { return prefs.getBoolean(KEY_USE_SERIES_DESCRIPTION, false); }'
])

add_getset(final_brace - 1, [
    'public static void setUseSeriesDescription(boolean use) { prefs.putBoolean(KEY_USE_SERIES_DESCRIPTION, use); }'
])

# Listener TLS: certificate auth name getter/setter  
add_getset(final_brace - 1, [
    '// HOROS-20240407 Reference: TLSAuthenticationCertificate in OSIListener.m',
    'public static String getListenerTLSAuthCertName() { return prefs.get(KEY_LISTENER_TLS_CERT_AUTH, ""); }'
])

add_getset(final_brace - 1, [
    'public static void setListenerTLSAuthCertName(String name) { prefs.put(KEY_LISTENER_TLS_CERT_AUTH, name); }'
])

with open(SRC, "w", encoding="utf-8") as f:
    f.write("\n".join(L))

print(f"Final line count: {len(L)}")
print("Done!")
