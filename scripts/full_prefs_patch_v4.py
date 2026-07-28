import os

SRC = "src/main/java/com/oculus/jj/config/AppPreferences.java"
with open(SRC, encoding="utf-8") as f:
    L = f.read().split("\n")

print(f"Original lines: {len(L)}")

def find_in(orig):
    for i in range(len(L)):
        if orig in L[i]: return i
    raise Exception(f"Not found: {orig!r}")

# Find where saveAll() actually ends - look for the closing } that's at column 0 (or indented as part of class)
save_flush_idx = find_in("prefs.flush();")
# Move back to find "public static void saveAll()" method signature  
for i in range(save_flush_idx, 0, -1):
    if L[i].strip().startswith("public static void saveAll()"):
        save_all_start = i
        break

print(f"saveAll() at original index {save_all_start} (line {save_all_start+1})")

# Move forward from saveAll start to find where it ENDS - the last } before a blank line then class closing
end_scan = save_all_start + 1
while end_scan < len(L) and L[end_scan].strip():  # skip non-empty lines until we hit something meaningful
    if L[end_scan].strip() == "}":
        # Check if next significant content is the class closing brace  
        after = end_scan + 1
        while after < len(L) - 1 and not L[after].strip():  # blank lines between methods are allowed but unusual here
            after += 1
        # If next non-blank line starts with "}" or "//", this is likely the class-level closing brace 
        if after >= len(L): break  # end of file reached
        next_nonblank = L[after].strip()
        if next_nonblank == "}":
            save_all_end_idx = end_scan + 1  # insert AFTER this } (before blank/class-closing-brace)
            break
    end_scan += 1

print(f"saveAll() method ends at index {save_all_end_idx} (line {save_all_end_idx+1})")

# Phase 1: Insert keys at original indices in descending order  
key_ops = [
    (find_in("KEY_CHECK_UPDATES") + 1, ["// HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m - JP2K engine / compression settings",
        'public static final String KEY_JP2K_ENGINE = "JP2KEngine";',
        'public static final String KEY_COMPRESSION_SETTINGS_JSON = "CompressionSettings";',
        'public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes";']),

    (find_in("KEY_ERASE_DB_AT_STARTUP") + 1, ["// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - UID generation / date formats",
        'public static final String KEY_USE_PATIENT_NAME_FOR_UID = "usePatientNameForUID";',
        'public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = "AutoCleanDaysToKeep";',
        'public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";',
        'public static final String KEY_USE_SERIES_DESCRIPTION = "useSeriesDescription";']),

    (find_in('public static final String KEY_HOTKEYS') + 1, ["", "// HOROS-20240407 Reference: annotations / hanging protocols / on-demand PACS",
        'public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = "hangingProtocolBodyRegions";',
        'public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";',
        'public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";' ,
        'public static final String KEY_PET_SUV_NORMALIZE_SCANNER = "pet.suvNormalizeScanner";',
        'public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList";']),

    (find_in("// === Web Server"), ["", "// HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm - TLS settings",
        'public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
        'public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";' ,
        'public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
        'public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";']),

    (find_in("KEY_WEB_SERVER_PORT") + 1, ["", "// HOROS-20240407 Reference: OSILocationsPreferencePane / OSIWebSharingReferencePane",
        'public static final String KEY_WADO_ENABLED = "WADOEnabled";',
        'public static final String KEY_WADO_PORT = "wadomoduleportnumber";',
        'public static final String KEY_WADO_URL = "wadoURL";' ,  # Horos reference: wadomoduleportnumber for port  
        'public static final String KEY_WADO_TRANSFER_SYNTAX = "WADOTransferSyntaxes";' ,
        'public static final String KEY_WEB_PORTAL_URL = "WebPortalURL";',
        'public static final String KEY_CUSTOM_HTML_FILE_PATH = "customizedHTMLFilePaths";' ,
        'public static final String KEY_USERS_MANAGEMENT_JSON = "webUsersManagementJSON";']),

    (find_in("KEY_LISTENER_AUTO_START") + 1, ["// HOROS-20240407 Reference: listener TLS per-listener",
        'public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";' ,
        'public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS";']),

    (find_in("KEY_AUTO_CLEANING_COMMENTS") + 1, ["// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - DB date formats",
        'public static final String KEY_DB_BIRTH_DATE_FORMAT = "DBDateOfBirthFormat2";' ,
        'public static final String KEY_SPLIT_MULTI_ECHO_MR = "splitMultiEchoMR";']),
]

key_ops.sort(key=lambda x: x[0], reverse=True)  
for pos, keys in key_ops:
    for k in reversed(keys):  # reversed to maintain insertion order 
        L.insert(pos, k)
print(f"After Phase 1 (keys): {len(L)} lines")

# === Phase 2: Insert getter/setter methods as a block after saveAll() ends ===  
block = [
    "", "/* ========== HOROS-20240407 Reference Data Accessors (Preference Pane) ========== */", ""
]

# General pane (JP2K + compression)
block += ["// -- General pane (HOROS: OSIGeneralPreferencePanePref.m)", 
    'public static String getJP2KEngine() { return prefs.get(KEY_JP2K_ENGINE, "OpenJPEG"); }',
    'public static void setJP2KEngine(String e) { prefs.put(KEY_JP2K_ENGINE, e); }',
    'public static String getCompressionSettings() { return prefs.get(KEY_COMPRESSION_SETTINGS_JSON, ""); }',  
    'public static void setCompressionSettings(String j) { prefs.put(KEY_COMPRESSION_SETTINGS_JSON, j); }']

# Database pane (UID generation / auto clean / date format - HOROS: OSIDatabasePreferencePanePref.m)
block += ["// -- Database preferences (HOROS-20240407 Reference)", 
    'public static String getUsePatientNameForUID() { return prefs.get(KEY_USE_PATIENT_NAME_FOR_UID, ""); }',
    'public static void setUsePatientNameForUID(String m) { prefs.put(KEY_USE_PATIENT_NAME_FOR_UID, m); }',  
    'public static int getAutoCleanDaysToKeep() { try { return prefs.getInt(KEY_AUTO_CLEAN_DAYS_TO_KEEP, 90); } catch (Exception e) { return 90; } }',
    'public static void setAutoCleanDaysToKeep(int d) { prefs.put(KEY_AUTO_CLEAN_DAYS_TO_KEEP, String.valueOf(d)); }']

# Database: Use series description / split multi-echo MR  
block += ["// -- DB use Series Description / Split Multi Echo MR (HOROS-20240407 Reference)",
    'public static boolean isUseSeriesDescription() { return prefs.getBoolean(KEY_USE_SERIES_DESCRIPTION, false); }',
    'public static void setUseSeriesDescription(boolean b) { prefs.putBoolean(KEY_USE_SERIES_DESCRIPTION, b); }']

# Listener TLS (HOROS: OSIListenerPreferencePanePref.mm)  
block += ["// -- Listener TLS settings (HOROS-20240407 Reference)", 
    'public static boolean isListenerTLSEnabled() { return prefs.getBoolean(KEY_LISTENER_TLS_ENABLED, false); }',
    'public static void setListenerTLSEnabled(boolean b) { prefs.putBoolean(KEY_LISTENER_TLS_ENABLED, b); }',
    'public static String getListenerTLSAuthCertName() { return prefs.get(KEY_LISTENER_TLS_CERT_AUTH, ""); }',  
    'public static void setListenerTLSAuthCertName(String n) { prefs.put(KEY_LISTENER_TLS_CERT_AUTH, n); }']

# Web Server / Locations (HOROS: OSILocationsPreferencePane / OSIWebSharingPreferencePane)
block += ["// -- WADO / WebServer settings (HOROS-20240407 Reference)", 
    'public static int getWadoPort() { try { return prefs.getInt(KEY_WADO_PORT, 8443); } catch (Exception e) { return 8443; } }',
    'public static void setWadoPort(int p) { prefs.put(KEY_WADO_PORT, String.valueOf(p)); }']

# Web Server users management (HOROS: OSIWebSharingPreferencePane - usersArrayController + WebPortalUser.h)
block += ["// -- Web users JSON (HOROS-20240407 Reference)",  
    'public static String getUserManagementJSON() { return prefs.get(KEY_USERS_MANAGEMENT_JSON, "[]"); }',
    'public static void setUserManagementJSON(String j) { prefs.put(KEY_USERS_MANAGEMENT_JSON, j); }']

# Hanging protocols / Hot Keys / Annotations / On-Demand (HOROS Reference)
block += ["// -- Other preferences (HOROS-20240407 Reference)",  
    'public static String getHangingProtocolBodyRegionsJSON() { return prefs.get(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, "[]"); }',
    'public static void setHangingProtocolBodyRegionsJSON(String j) { prefs.put(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, j); }']

# HotKeys (HOROS: OSIHotKeysPref.m - stored as JSON)  
block += ["// -- Hot key bindings (HOROS-20240407 Reference)",
    'public static String getHotkeyBindingsJSON() { return prefs.get("hotkeyBindingsJSON", "[]"); }',
    'public static void setHotkeyBindingsJSON(String j) { prefs.put("hotkeyBindingsJSON", j); }']

# Annotations (HOROS: OSICustomImageAnnotations.m / CIALayoutController.m)
block += ["// -- Annotation DICOM field map (HOROS-20240407 Reference)",  
    'public static String getAnnotationDicomFieldMapJSON() { return prefs.get(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, "{}"); }',
    'public static void setAnnotationDicomFieldMapJSON(String j) { prefs.put(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, j); }']

# PET/SUV (HOROS: OSIPETPreferencePane.m)  
block += ["// -- PET/SUV scanner normalize (HOROS-20240407 Reference)", 
    'public static String getPetsuvNormalizeScanner() { return prefs.get(KEY_PET_SUV_NORMALIZE_SCANNER, ""); }',
    'public static void setPetsuvNormalizeScanner(String s) { prefs.put(KEY_PET_SUV_NORMALIZE_SCANNER, s); }']

# On-Demand PACS (HOROS: OSIPACSOnDemandPreferencePane.m)
block += ["// -- On-demand PACS source list (HOROS-20240407 Reference)",  
    'public static String getOnDemandSourceListJSON() { return prefs.get(KEY_ONDEMAND_SOURCE_LIST_JSON, "[]"); }',
    'public static void setOnDemandSourceListJSON(String j) { prefs.put(KEY_ONDEMAND_SOURCE_LIST_JSON, j); }']

# Insert the block after saveAll's closing brace (before class closing brace)
for item in reversed(block):  # reverse to maintain insertion order 
    L.insert(save_all_end_idx, item)

with open(SRC, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print(f"Final line count: {len(L)}")
