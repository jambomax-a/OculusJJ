import os

SRC = "src/main/java/com/oculus/jj/config/AppPreferences.java"

with open(SRC, encoding="utf-8") as f:
    L = f.read().split("\n")

orig = len(L)
print(f"Starting with {orig} lines")

def find_marker(orig_str):
    for i, l in enumerate(L):
        if orig_str in l: return i
    raise Exception(f"Not found: {orig_str!r}")

# --- Phase 1: Insert new KEY constants (descending position order) ---
insert_ops = []

# After KEY_CHECK_UPDATES (~index 25) - General JP2K/compression keys  
i_check = find_marker("KEY_CHECK_UPDATES") + 1
insert_ops.append((i_check, [
    "// HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m",
    'public static final String KEY_JP2K_ENGINE = "JP2KEngine";',
    'public static final String KEY_COMPRESSION_SETTINGS_JSON = "CompressionSettings";',
    'public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes";'
]))

# After KEY_ERASE_DB_AT_STARTUP (~index 33) - Database UID/date format  
i_erase = find_marker("KEY_ERASE_DB_AT_STARTUP") + 1
insert_ops.append((i_erase, [
    "// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m",
    'public static final String KEY_USE_PATIENT_NAME_FOR_UID = "usePatientNameForUID";',
    'public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = "AutoCleanDaysToKeep";',
    'public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";',
    'public static final String KEY_USE_SERIES_DESCRIPTION = "useSeriesDescription";'
]))

# After KEY_HOTKEYS (~index 57) - Other keys
i_hotkeys = find_marker('public static final String KEY_HOTKEYS') + 1
insert_ops.append((i_hotkeys, [
    "", "// HOROS-20240407 Reference: annotations/hanging/ondemand",
    'public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = "hangingProtocolBodyRegions";',
    'public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";' ,
    'public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";' ,
    'public static final String KEY_PET_SUV_NORMALIZE_SCANNER = "pet.suvNormalizeScanner";',
    'public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList";'
]))

# Before "// === Web Server" section (~index 108) - TLS keys
i_web = find_marker("// === Web Server")
insert_ops.append((i_web, [
    "", "// HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm (TLS)",
    'public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
    'public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";' ,
    'public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
    'public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";'
]))

# After KEY_WEB_SERVER_PORT (~index 109) - WADO/User mgmt keys  
i_webport = find_marker("KEY_WEB_SERVER_PORT") + 1
insert_ops.append((i_webport, [
    "", "// HOROS-20240407 Reference: OSILocationsPreferencePane / OSIWebSharingPreferencePane",
    'public static final String KEY_WADO_ENABLED = "WADOEnabled";',
    'public static final String KEY_WADO_PORT = "wadomoduleportnumber";',
    'public static final String KEY_WADO_URL = "wadoURL";' ,  # Horos uses wadomoduleportnumber for port
    'public static final String KEY_WADO_TRANSFER_SYNTAX = "WADOTransferSyntaxes";' ,
    'public static final String KEY_WEB_PORTAL_URL = "WebPortalURL";',
    'public static final String KEY_CUSTOM_HTML_FILE_PATH = "customizedHTMLFilePaths";' ,
    'public static final String KEY_USERS_MANAGEMENT_JSON = "webUsersManagementJSON";'
]))

# After KEY_LISTENER_AUTO_START (~index 106) - TLS per-listener  
i_listener_auto = find_marker("KEY_LISTENER_AUTO_START") + 1
insert_ops.append((i_listener_auto, [
    "// HOROS-20240407 Reference: listener TLS per-listener",
    'public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";' ,
    'public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS";'
]))

# DB date format and multi-echo MR settings at original index 34 (after KEY_ERASE_DB_AT_STARTUP)  
i_db_date = find_marker("KEY_AUTO_CLEANING_COMMENTS") + 1  # after autoCleaningComments line
insert_ops.append((i_db_date, [
    "// HOROS-20240407 Reference: DB date format / split multi echo MR",
    'public static final String KEY_DB_BIRTH_DATE_FORMAT = "DBDateOfBirthFormat2";' ,
    'public static final String KEY_SPLIT_MULTI_ECHO_MR = "splitMultiEchoMR";'
]))

# Sort descending by position so earlier indices stay valid
insert_ops.sort(key=lambda x: x[0], reverse=True)
for pos, keys in insert_ops:
    for k in reversed(keys):  # reversed to maintain insertion order
        L.insert(pos, k)
    
print(f"After key insertions: {len(L)} lines")

# --- Phase 2: Find where saveAll() ends and add getters/setters there ---  
save_all_end = find_marker("prefs.flush();") + 1  # after the flush call line
# Now insert get/set pairs AFTER save_all_end but BEFORE class closing brace (which is at len(L)-1)

def make_gs(name, getter_ret, key_ref, default_str=None):
    """Generate a pair of getter and setter for a String field."""
    if default_str:
        return [f'public static String get{name}() {{ return prefs.get({key_ref}, "{default_str}"); }}', 
                f'public static void set{name}(String v) {{ prefs.put({key_ref}, v); }}']
    else:
        return [f'public static String get{name}() {{ return prefs.get({key_ref}); }}', 
                f'public static void set{name}(String v) {{ prefs.put({key_ref}, v); }}']

def make_gs_int(name, key_ref, default_val):
    """Generate getter/setter for integer field."""  
    return [f'public static int get{name}() {{ try {{ return prefs.getInt({key_ref}, {default_val}); }} catch (Exception e) {{ return {default_val}; }} }}', 
            f'public static void set{name}(int v) {{ prefs.put({key_ref}, String.valueOf(v)); }}']

def make_gs_bool(name, key_ref, default_val):
    """Generate getter/setter for boolean field."""  
    return [f'public static boolean is{name}() {{ return prefs.getBoolean({key_ref}, {default_val}); }}', 
            f'public static void set{name}(boolean v) {{ prefs.putBoolean({key_ref}, v); }}']

# Build all getter/setter pairs to insert
getters_setters = []

getters_setters += ["", "/* ========== HOROS-20240407 Data Accessors (Preference Pane) ========== */", ""]

# General: JP2K engine  
getters_setters += ["// -- General pane (JP2K engine)", 'public static String getJP2KEngine() { return prefs.get(KEY_JP2K_ENGINE, "OpenJPEG"); }',
                    'public static void setJP2KEngine(String e) { prefs.put(KEY_JP2K_ENGINE, e); }']

# General: Compression settings (JSON string for simplicity)  
getters_setters += ["// -- Compression settings (JSON)", 'public static String getCompressionSettings() { return prefs.get(KEY_COMPRESSION_SETTINGS_JSON, ""); }',
                    'public static void setCompressionSettings(String j) { prefs.put(KEY_COMPRESSION_SETTINGS_JSON, j); }']

# Database: UID generation
getters_setters += ["// -- DB UID generation", 'public static String getUsePatientNameForUID() { return prefs.get(KEY_USE_PATIENT_NAME_FOR_UID, ""); }',
                    'public static void setUsePatientNameForUID(String m) { prefs.put(KEY_USE_PATIENT_NAME_FOR_UID, m); }']

# Database: Auto-clean days
getters_setters += ["// -- DB auto clean days", 'public static int getAutoCleanDaysToKeep() { try { return prefs.getInt(KEY_AUTO_CLEAN_DAYS_TO_KEEP, 90); } catch (Exception e) { return 90; } }',
                    'public static void setAutoCleanDaysToKeep(int d) { prefs.put(KEY_AUTO_CLEAN_DAYS_TO_KEEP, String.valueOf(d)); }']

# Database: Use series description (boolean)  
getters_setters += ["// -- DB use Series Description", 'public static boolean isUseSeriesDescription() { return prefs.getBoolean(KEY_USE_SERIES_DESCRIPTION, false); }',
                    'public static void setUseSeriesDescription(boolean b) { prefs.putBoolean(KEY_USE_SERIES_DESCRIPTION, b); }']

# Listener TLS: certificate auth  
getters_setters += ["// -- Listener TLS (HOROS-20240407 Reference)", 'public static boolean isListenerTLSEnabled() { return prefs.getBoolean(KEY_LISTENER_TLS_ENABLED, false); }',
                    'public static void setListenerTLSEnabled(boolean b) { prefs.putBoolean(KEY_LISTENER_TLS_ENABLED, b); }']

# Listener TLS: certificate name  
getters_setters += ["// -- Listener TLS auth cert", 'public static String getListenerTLSAuthCertName() { return prefs.get(KEY_LISTENER_TLS_CERT_AUTH, ""); }',
                    'public static void setListenerTLSAuthCertName(String n) { prefs.put(KEY_LISTENER_TLS_CERT_AUTH, n); }']

# Web Server: WADO port  
getters_setters += ["// -- Web/WADO settings (HOROS-20240407 Reference)", 'public static int getWadoPort() { try { return prefs.getInt(KEY_WADO_PORT, 8443); } catch (Exception e) { return 8443; } }',
                    'public static void setWadoPort(int p) { prefs.put(KEY_WADO_PORT, String.valueOf(p)); }']

# Web Server: User management  
getters_setters += ["// -- Web users JSON", 'public static String getUserManagementJSON() { return prefs.get(KEY_USERS_MANAGEMENT_JSON, "[]"); }',
                    'public static void setUserManagementJSON(String j) { prefs.put(KEY_USERS_MANAGEMENT_JSON, j); }']

# Hanging: body regions / keywords  
getters_setters += ["// -- Hanging protocols", 'public static String getHangingProtocolBodyRegionsJSON() { return prefs.get(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, "[]"); }',
                    'public static void setHangingProtocolBodyRegionsJSON(String j) { prefs.put(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, j); }']

# HotKeys  
getters_setters += ["// -- Hot key bindings", 'public static String getHotkeyBindingsJSON() { return prefs.get("hotkeyBindingsJSON", "[]"); }',
                    'public static void setHotkeyBindingsJSON(String j) { prefs.put("hotkeyBindingsJSON", j); }']

# Annotations  
getters_setters += ["// -- Annotation DICOM field map", 'public static String getAnnotationDicomFieldMapJSON() { return prefs.get(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, "{}"); }',
                    'public static void setAnnotationDicomFieldMapJSON(String j) { prefs.put(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, j); }']

# PET/SUV  
getters_setters += ["// -- PET/SUV scanner normalize", 'public static String getPetsuvNormalizeScanner() { return prefs.get(KEY_PET_SUV_NORMALIZE_SCANNER, ""); }',
                    'public static void setPetsuvNormalizeScanner(String s) { prefs.put(KEY_PET_SUV_NORMALIZE_SCANNER, s); }']

# On-Demand PACS  
getters_setters += ["// -- On-demand PACS source list", 'public static String getOnDemandSourceListJSON() { return prefs.get(KEY_ONDEMAND_SOURCE_LIST_JSON, "[]"); }',
                    'public static void setOnDemandSourceListJSON(String j) { prefs.put(KEY_ONDEMAND_SOURCE_LIST_JSON, j); }']

# Insert all at save_all_end position (after flush call line)
for g in reversed(getters_setters):
    L.insert(save_all_end, g)

with open(SRC, "w", encoding="utf-8") as f:
    f.write("\n".join(L))

print(f"Done! Final lines: {len(L)}")
