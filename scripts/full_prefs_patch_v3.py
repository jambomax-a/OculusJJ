import os

SRC = "src/main/java/com/oculus/jj/config/AppPreferences.java"

with open(SRC, encoding="utf-8") as f:
    L = f.read().split("\n")

orig_count = len(L)  # should be 621 in original
print(f"Start with {orig_count} lines (original)")

def find_marker(orig_str):
    for i, l in enumerate(L):
        if orig_str in l: return i
    raise Exception(f"Not found: {orig_str!r}")

# ===== Phase 1: Insert KEY constants (in descending position order) =====
key_ops = [
    # Original index positions
    (find_marker("KEY_CHECK_UPDATES") + 1, "General pane JP2K/compression after KEY_CHECK_UPDATES", 
     ["// HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m - JP2K engine / compression settings",
      'public static final String KEY_JP2K_ENGINE = "JP2KEngine";',
      'public static final String KEY_COMPRESSION_SETTINGS_JSON = "CompressionSettings";',
      'public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes";']),
    
    (find_marker("KEY_ERASE_DB_AT_STARTUP") + 1, "Database UID/date format after KEY_ERASE_DB_AT_STARTUP", 
     ["// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - UID generation / date formats",
      'public static final String KEY_USE_PATIENT_NAME_FOR_UID = "usePatientNameForUID";',
      'public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = "AutoCleanDaysToKeep";',
      'public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";',
      'public static final String KEY_USE_SERIES_DESCRIPTION = "useSeriesDescription";']),
    
    (find_marker('public static final String KEY_HOTKEYS') + 1, "Other keys after KEY_HOTKEYS", 
     ["", "// HOROS-20240407 Reference: annotations / hanging protocols / on-demand PACS",
      'public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = "hangingProtocolBodyRegions";',
      'public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";' ,
      'public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";' ,
      'public static final String KEY_PET_SUV_NORMALIZE_SCANNER = "pet.suvNormalizeScanner";',
      'public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList";']),

    (find_marker("// === Web Server"), "Listener TLS keys before Web Server section", 
     ["", "// HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm - TLS settings",
      'public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
      'public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";' ,
      'public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
      'public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";']),

    (find_marker("KEY_WEB_SERVER_PORT") + 1, "WADO/User mgmt after KEY_WEB_SERVER_PORT", 
     ["", "// HOROS-20240407 Reference: OSILocationsPreferencePane / OSIWebSharingReferencePane",
      'public static final String KEY_WADO_ENABLED = "WADOEnabled";',
      'public static final String KEY_WADO_PORT = "wadomoduleportnumber";',
      'public static final String KEY_WADO_URL = "wadoURL";' ,  # Horos reference key
      'public static final String KEY_WADO_TRANSFER_SYNTAX = "WADOTransferSyntaxes";' ,
      'public static final String KEY_WEB_PORTAL_URL = "WebPortalURL";',
      'public static final String KEY_CUSTOM_HTML_FILE_PATH = "customizedHTMLFilePaths";' ,
      'public static final String KEY_USERS_MANAGEMENT_JSON = "webUsersManagementJSON";']),

    (find_marker("KEY_LISTENER_AUTO_START") + 1, "TLS per-listener after LISTENER_AUTO_START", 
     ["// HOROS-20240407 Reference: listener TLS per-listener",
      'public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";' ,
      'public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS";']),

    (find_marker("KEY_AUTO_CLEANING_COMMENTS") + 1, "DB date format/multi-echo after autoCleaningComments", 
     ["// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - DB date formats",
      'public static final String KEY_DB_BIRTH_DATE_FORMAT = "DBDateOfBirthFormat2";' ,
      'public static final String KEY_SPLIT_MULTI_ECHO_MR = "splitMultiEchoMR";']),
]

# Sort descending by position (original 0-indexed) so earlier inserts stay valid
key_ops.sort(key=lambda x: x[0], reverse=True)
for pos, desc, keys in key_ops:
    for k in reversed(keys):
        L.insert(pos, k)
print(f"After Phase 1 key insertions: {len(L)} lines")

# ===== Phase 2: Add getter/setter methods BEFORE final class closing brace =====
# Find the ORIGINAL file's structure - where does saveAll() end?

# Look for the line containing "prefs.flush()" (inside saveAll) and find its enclosing method
save_flush_idx = find_marker("prefs.flush();")
# Move backward to find opening of saveAll method  
for i in range(save_flush_idx, 0, -1):
    if L[i].strip().startswith("public static void saveAll()"):
        save_all_start = i
        break

print(f"saveAll() found at original line {save_all_start+1}")

# Move forward from end of file to find the LAST '}' (class closing brace)  
for i in range(len(L) - 1, -1, -1):
    if L[i].strip() == "}":
        final_brace_idx = i
        break

print(f"Final class closing brace at index {final_brace_idx}")

# The correct insertion point is AFTER saveAll()'s ending brace, BEFORE the class closing brace
# Look for where saveAll ends: find closing } of saveAll() method
save_all_end = None
for i in range(save_all_start + 1, final_brace_idx):
    if L[i].strip().startswith("}"):
        # This is likely the end of saveAll - verify by checking next non-empty lines are class-level
        after_i = min(i+1, len(L)-1)
        while after_i < len(L) and not L[after_i].strip():
            after_i += 1
        if after_i < len(L):
            # Check that next line is NOT a method (would mean we're inside another inner class)
            if not L[after_i].strip().startswith("public ") and L[after_i].strip() != "}":
                save_all_end = i + 1
        break

print(f"Saving ALL method ends at index {save_all_end}")

# Build all getter/setter pairs to insert as a block after the end of saveAll() 
getters_setters_block = [
    "", "/* ========== HOROS-20240407 Reference Data Accessors (Preference Pane) ========== */", ""
]

def g(name, ret, key_ref, default=None):
    if default is not None:
        return f"public static {ret} get{name}() {{ return prefs.get({key_ref}, {default}); }}"
    else:
        return f"public static {ret} get{name}() {{ return prefs.get({key_ref}); }}"

def g_set(name, key_ref):
    return f"public static void set{name}(String v) {{ prefs.put({key_ref}, v); }}"

getters_setters_block += ["", "// -- General pane (JP2K engine / compression settings)", ""]
getters_setters_block.append(g("JP2KEngine", "String", "KEY_JP2K_ENGINE", '"OpenJPEG"'))
getters_setters_block.append(g_set("JP2KEngine", "KEY_JP2K_ENGINE"))
getters_setters_block.append('public static String getCompressionSettings() { return prefs.get(KEY_COMPRESSION_SETTINGS_JSON, ""); }')
getters_setters_block.append('public static void setCompressionSettings(String j) { prefs.put(KEY_COMPRESSION_SETTINGS_JSON, j); }')

getters_setters_block += ["", "// -- Database (UID generation / auto clean / date format)", ""]
getters_setters_block.append(g("UsePatientNameForUID", "String", "KEY_USE_PATIENT_NAME_FOR_UID"))
getters_setters_block.append(g_set("UsePatientNameForUID", "KEY_USE_PATIENT_NAME_FOR_UID"))
# Auto clean days - Integer with try/catch  
getters_setters_block.append('public static int getAutoCleanDaysToKeep() { try { return prefs.getInt(KEY_AUTO_CLEAN_DAYS_TO_KEEP, 90); } catch (Exception e) { return 90; } }')
getters_setters_block.append('public static void setAutoCleanDaysToKeep(int d) { prefs.put(KEY_AUTO_CLEAN_DAYS_TO_KEEP, String.valueOf(d)); }')

# Use series description  
getters_setters_block += ["// -- Database use Series Description", 
    'public static boolean isUseSeriesDescription() { return prefs.getBoolean(KEY_USE_SERIES_DESCRIPTION, false); }',
    'public static void setUseSeriesDescription(boolean b) { prefs.putBoolean(KEY_USE_SERIES_DESCRIPTION, b); }']

getters_setters_block += ["", "// -- Listener TLS (HOROS-20240407 Reference)", ""]  
getters_setters_block.append('public static boolean isListenerTLSEnabled() { return prefs.getBoolean(KEY_LISTENER_TLS_ENABLED, false); }')
getters_setters_block.append('public static void setListenerTLSEnabled(boolean b) { prefs.putBoolean(KEY_LISTENER_TLS_ENABLED, b); }')

# TLS certificate auth name
getters_setters_block += ["", "// -- Listener TLS authentication cert (HOROS-20240407 Reference)", ""]
getters_setters_block.append(g("ListenerTLSAuthCertName", "String", "KEY_LISTENER_TLS_CERT_AUTH"))
getters_setters_block.append(g_set("ListenerTLSAuthCertName", "KEY_LISTENER_TLS_CERT_AUTH"))

getters_setters_block += ["", "// -- WADO/WebServer (HOROS-20240407 Reference)", ""]
# WADO port - Integer  
getters_setters_block.append('public static int getWadoPort() { try { return prefs.getInt(KEY_WADO_PORT, 8443); } catch (Exception e) { return 8443; } }')
getters_setters_block.append('public static void setWadoPort(int p) { prefs.put(KEY_WADO_PORT, String.valueOf(p)); }')

# User management JSON  
getters_setters_block += ["// -- Web users management (OSIWebSharingPreferencePane)", ""] 
getters_setters_block.append(g("UserManagementJSON", "String", "KEY_USERS_MANAGEMENT_JSON", '"[]"'))
getters_setters_block.append(g_set("UserManagementJSON", "KEY_USERS_MANAGEMENT_JSON"))

# Hanging protocol body regions / keywords
getters_setters_block += ["", "// -- Hanging protocols (BodyRegionArrayController / KeywordsArrayController)", ""]
getters_setters_block.append('public static String getHangingProtocolBodyRegionsJSON() { return prefs.get(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, "[]"); }')
getters_setters_block.append('public static void setHangingProtocolBodyRegionsJSON(String j) { prefs.put(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, j); }')

# HotKeys  
getters_setters_block += ["// -- Hot key bindings (OSIHotKeysPref.m)", ""]
getters_setters_block.append('public static String getHotkeyBindingsJSON() { return prefs.get("hotkeyBindingsJSON", "[]"); }')
getters_setters_block.append('public static void setHotkeyBindingsJSON(String j) { prefs.put("hotkeyBindingsJSON", j); }')

# Annotations DICOM field map  
getters_setters_block += ["// -- Annotation DICOM field map (OSICustomImageAnnotations)", ""] 
getters_setters_block.append(g("AnnotationDicomFieldMapJSON", "String", "KEY_ANNOTATION_DICOM_FIELD_MAP_JSON", '"{}"'))
getters_setters_block.append(g_set("AnnotationDicomFieldMapJSON", "KEY_ANNOTATION_DICOM_FIELD_MAP_JSON"))

# PET/SUV  
getters_setters_block += ["// -- PET/SUV scanner normalize (OSIPETPreferencePane)", ""]
getters_setters_block.append(g("PetsuvNormalizeScanner", "String", "KEY_PET_SUV_NORMALIZE_SCANNER"))
getters_setters_block.append(g_set("PetsuvNormalizeScanner", "KEY_PET_SUV_NORMALIZE_SCANNER"))

# On-Demand PACS source list  
getters_setters_block += ["// -- On-demand PACS source list (OSIPACSOnDemandPreferencePane)", ""]
getters_setters_block.append(g("OnDemandSourceListJSON", "String", "KEY_ONDEMAND_SOURCE_LIST_JSON", '"[]"')) 
getters_setters_block.append(g_set("OnDemandSourceListJSON", "KEY_ONDEMAND_SOURCE_LIST_JSON"))

# Insert the entire block after saveAll() ends
for g in reversed(getters_setters_block):  # reverse to maintain order when inserting sequentially
    L.insert(save_all_end, g)

with open(SRC, "w", encoding="utf-8") as f:
    f.write("\n".join(L))

print(f"Final line count: {len(L)}")
