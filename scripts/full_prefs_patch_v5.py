import os, copy

SRC = "src/main/java/com/oculus/jj/config/AppPreferences.java"
with open(SRC, encoding="utf-8") as f:
    content = f.read()

# Split but PRESERVE all original whitespace (newlines)
L = content.split("\n")  # This preserves trailing newlines from split
print(f"Original lines: {len(L)}")

def find_in(orig):
    for i, l in enumerate(L):
        if orig in l: return i
    raise Exception(f"Not found: {orig!r}")

# Phase 1: Find original insertion points and insert keys  
key_inserts = []  # list of (original_index_position, [lines_to_insert])

ci_check_updates = find_in("KEY_CHECK_UPDATES") + 1  # line ~27
key_inserts.append((ci_check_updates, [
    "// HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m - JP2K engine / compression settings",
    'public static final String KEY_JP2K_ENGINE = "JP2KEngine";',
    'public static final String KEY_COMPRESSION_SETTINGS_JSON = "CompressionSettings";',
    'public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes";'
]))

ci_erase_db = find_in("KEY_ERASE_DB_AT_STARTUP") + 1  # line ~35  
key_inserts.append((ci_erase_db, [
    "// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - UID generation / date formats",
    'public static final String KEY_USE_PATIENT_NAME_FOR_UID = "usePatientNameForUID";',
    'public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = "AutoCleanDaysToKeep";',
    'public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";',
    'public static final String KEY_USE_SERIES_DESCRIPTION = "useSeriesDescription";'
]))

ci_hotkeys = find_in('public static final String KEY_HOTKEYS') + 1
key_inserts.append((ci_hotkeys, [
    "", "// HOROS-20240407 Reference: annotations / hanging protocols / on-demand PACS",
    'public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = "hangingProtocolBodyRegions";',
    'public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";' ,
    'public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";' ,
    'public static final String KEY_PET_SUV_NORMALIZE_SCANNER = "pet.suvNormalizeScanner";',
    'public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList";'
]))

ci_web_section = find_in("// === Web Server")  # INSERT BEFORE this line (around ~108) 
key_inserts.append((ci_web_section, [
    "", "// HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm - TLS settings",
    'public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
    'public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";' ,
    'public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
    'public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";'
]))

ci_web_port = find_in("KEY_WEB_SERVER_PORT") + 1
key_inserts.append((ci_web_port, [
    "", "// HOROS-20240407 Reference: OSILocationsPreferencePane / OSIWebSharingReferencePane",
    'public static final String KEY_WADO_ENABLED = "WADOEnabled";',
    'public static final String KEY_WADO_PORT = "wadomoduleportnumber";',
    'public static final String KEY_WADO_URL = "wadoURL";' ,
    'public static final String KEY_WADO_TRANSFER_SYNTAX = "WADOTransferSyntaxes";' ,
    'public static final String KEY_WEB_PORTAL_URL = "WebPortalURL";',
    'public static final String KEY_CUSTOM_HTML_FILE_PATH = "customizedHTMLFilePaths";' ,
    'public static final String KEY_USERS_MANAGEMENT_JSON = "webUsersManagementJSON";'
]))

ci_listener_auto = find_in("KEY_LISTENER_AUTO_START") + 1  
key_inserts.append((ci_listener_auto, [
    "// HOROS-20240407 Reference: listener TLS per-listener",
    'public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";' ,
    'public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS";'
]))

ci_db_comments = find_in("KEY_AUTO_CLEANING_COMMENTS") + 1
key_inserts.append((ci_db_comments, [
    "// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - DB date formats",
    'public static final String KEY_DB_BIRTH_DATE_FORMAT = "DBDateOfBirthFormat2";' ,
    'public static final String KEY_SPLIT_MULTI_ECHO_MR = "splitMultiEchoMR";'
]))

# Sort by original position DESCENDING so earlier positions remain stable during insertion
key_inserts.sort(key=lambda x: x[0], reverse=True)  
for pos, keys in key_inserts:
    # Insert each line one at a time - maintain order by inserting reversed then the result is correct
    for k in reversed(keys):
        L.insert(pos, k)

print(f"After Phase 1 (keys): {len(L)} lines")

# Phase 2: Find where saveAll() ends and insert getter/setters there
# Scan forward from "public static void saveAll()" to find its closing } 
save_all_idx = find_in("public static void saveAll()")
print(f"Found saveAll() at index {save_all_idx} (line {save_all_idx + 1})")

# Walk forward until we hit a '}' followed by either class closing '}' or another method
brace_count = 0
i = save_all_idx
while i < len(L):
    stripped = L[i].strip()
    if stripped.startswith('{'): brace_count += 1
    elif stripped == '}': 
        brace_count -= 1
        if brace_count == 0:
            # This is the closing } of saveAll(). Insert AFTER this line.
            insert_pos = i + 1
            print(f"saveAll() ends at index {i} (line {i+1}), inserting after it")
            break
    i += 1

# Build getter/setter block
block = []
block.append("")
block.append("/* ========== HOROS-20240407 Reference Data Accessors (Preference Pane) ========== */")
block.append("")

# General: JP2K + compression  
block.extend(["// -- General pane - JP2K engine / compression settings", ""])
block.append('public static String getJP2KEngine() { return prefs.get(KEY_JP2K_ENGINE, "OpenJPEG"); }')
block.append('public static void setJP2KEngine(String e) { prefs.put(KEY_JP2K_ENGINE, e); }')
block.append('public static String getCompressionSettings() { return prefs.get(KEY_COMPRESSION_SETTINGS_JSON, ""); }')  
block.append('public static void setCompressionSettings(String j) { prefs.put(KEY_COMPRESSION_SETTINGS_JSON, j); }')

# Database: UID / auto clean / date format  
block.extend(["", "// -- Database preferences (HOROS-20240407 Reference)", ""])
block.append('public static String getUsePatientNameForUID() { return prefs.get(KEY_USE_PATIENT_NAME_FOR_UID, ""); }')
block.append('public static void setUsePatientNameForUID(String m) { prefs.put(KEY_USE_PATIENT_NAME_FOR_UID, m); }')
block.append('public static int getAutoCleanDaysToKeep() { try { return prefs.getInt(KEY_AUTO_CLEAN_DAYS_TO_KEEP, 90); } catch (Exception e) { return 90; } }')  
block.append('public static void setAutoCleanDaysToKeep(int d) { prefs.put(KEY_AUTO_CLEAN_DAYS_TO_KEEP, String.valueOf(d)); }')
block.append('public static boolean isUseSeriesDescription() { return prefs.getBoolean(KEY_USE_SERIES_DESCRIPTION, false); }')
block.append('public static void setUseSeriesDescription(boolean b) { prefs.putBoolean(KEY_USE_SERIES_DESCRIPTION, b); }')

# Listener TLS  
block.extend(["", "// -- Listener TLS settings (HOROS-20240407 Reference)", ""])
block.append('public static boolean isListenerTLSEnabled() { return prefs.getBoolean(KEY_LISTENER_TLS_ENABLED, false); }')
block.append('public static void setListenerTLSEnabled(boolean b) { prefs.putBoolean(KEY_LISTENER_TLS_ENABLED, b); }')  
block.append('public static String getListenerTLSAuthCertName() { return prefs.get(KEY_LISTENER_TLS_CERT_AUTH, ""); }')
block.append('public static void setListenerTLSAuthCertName(String n) { prefs.put(KEY_LISTENER_TLS_CERT_AUTH, n); }')

# Web Server / Locations
block.extend(["", "// -- WADO/WebServer settings (HOROS-20240407 Reference)", ""])  
block.append('public static int getWadoPort() { try { return prefs.getInt(KEY_WADO_PORT, 8443); } catch (Exception e) { return 8443; } }')
block.append('public static void setWadoPort(int p) { prefs.put(KEY_WADO_PORT, String.valueOf(p)); }')

# Web users  
block.extend(["", "// -- Web users JSON (HOROS-20240407 Reference: OSIWebSharingPreferencePane)", ""])
block.append('public static String getUserManagementJSON() { return prefs.get(KEY_USERS_MANAGEMENT_JSON, "[]"); }')
block.append('public static void setUserManagementJSON(String j) { prefs.put(KEY_USERS_MANAGEMENT_JSON, j); }')

# Hanging / HotKeys / Annotations / On-Demand  
block.extend(["", "// -- Other preferences (HOROS-20240407 Reference)", ""])
block.append('public static String getHangingProtocolBodyRegionsJSON() { return prefs.get(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, "[]"); }')
block.append('public static void setHangingProtocolBodyRegionsJSON(String j) { prefs.put(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, j); }')
block.append('public static String getHotkeyBindingsJSON() { return prefs.get("hotkeyBindingsJSON", "[]"); }')  
block.append('public static void setHotkeyBindingsJSON(String j) { prefs.put("hotkeyBindingsJSON", j); }')
block.append('public static String getAnnotationDicomFieldMapJSON() { return prefs.get(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, "{}"); }')
block.append('public static void setAnnotationDicomFieldMapJSON(String j) { prefs.put(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, j); }')

# PET/SUV / On-Demand PACS  
block.extend(["// -- PET/SUV (HOROS-20240407 Reference)", ""])
block.append('public static String getPetsuvNormalizeScanner() { return prefs.get(KEY_PET_SUV_NORMALIZE_SCANNER, ""); }')
block.append('public static void setPetsuvNormalizeScanner(String s) { prefs.put(KEY_PET_SUV_NORMALIZE_SCANNER, s); }')
block.extend(["", "// -- On-Demand PACS source list (HOROS-20240407 Reference)", ""]) 
block.append('public static String getOnDemandSourceListJSON() { return prefs.get(KEY_ONDEMAND_SOURCE_LIST_JSON, "[]"); }')
block.append('public static void setOnDemandSourceListJSON(String j) { prefs.put(KEY_ONDEMAND_SOURCE_LIST_JSON, j); }')

# Insert block AFTER the saveAll() closing brace (preserving original class closing brace)  
for item in reversed(block):  # reverse to maintain order when inserting one-by-one
    L.insert(insert_pos, item)

with open(SRC, "w", encoding="utf-8") as f:
    f.write("\n".join(L))

print(f"Final line count: {len(L)}")
