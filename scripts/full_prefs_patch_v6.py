import os

SRC = "src/main/java/com/oculus/jj/config/AppPreferences.java"
with open(SRC, encoding="utf-8") as f:
    L = f.read().split("\n")

orig = len(L)  # should be 621 (or 622 if there's a trailing newline issue - let me check by re-splitting on the original content)
print(f"Read {len(L)} lines from file")
# Verify last char: if content ends with \n, split gives an extra empty element at end  
if L and not L[-1].strip():
    print("Last line is empty (trailing newline stripped by .split)")

def find_in(orig_str):
    for i in range(len(L)):
        if orig_str in L[i]: return i
    raise Exception(f"Not found: {orig_str!r}")

# ===== Phase 1: Add KEY constants at original indices, descending order =====  
key_ops = [
    (find_in("KEY_CHECK_UPDATES") + 1, "// HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m - JP2K engine / compression settings", 
     ['public static final String KEY_JP2K_ENGINE = "JP2KEngine";',
      'public static final String KEY_COMPRESSION_SETTINGS_JSON = "CompressionSettings";',
      'public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes";']),

    (find_in("KEY_ERASE_DB_AT_STARTUP") + 1, "// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - UID/date formats", 
     ['public static final String KEY_USE_PATIENT_NAME_FOR_UID = "usePatientNameForUID";',
      'public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = "AutoCleanDaysToKeep";',
      'public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";',
      'public static final String KEY_USE_SERIES_DESCRIPTION = "useSeriesDescription";']),

    (find_in('public static final String KEY_HOTKEYS') + 1, "// HOROS-20240407 Reference: annotations / hanging protocols / on-demand PACS", 
     ["",
      'public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = "hangingProtocolBodyRegions";',
      'public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";' ,
      'public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";' ,
      'public static final String KEY_PET_SUV_NORMALIZE_SCANNER = "pet.suvNormalizeScanner";',
      'public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList";']),

    (find_in("// === Web Server"), "// HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm - TLS settings", 
     ["",
      'public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
      'public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";' ,
      'public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
      'public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";']),

    (find_in("KEY_WEB_SERVER_PORT") + 1, "// HOROS-20240407 Reference: OSILocationsPreferencePane / OSIWebSharingReferencePane", 
     ["",
      'public static final String KEY_WADO_ENABLED = "WADOEnabled";',
      'public static final String KEY_WADO_PORT = "wadomoduleportnumber";',
      'public static final String KEY_WADO_URL = "wadoURL";' ,  # Horos reference: wadomoduleportnumber
      'public static final String KEY_WADO_TRANSFER_SYNTAX = "WADOTransferSyntaxes";' ,
      'public static final String KEY_WEB_PORTAL_URL = "WebPortalURL";',
      'public static final String KEY_CUSTOM_HTML_FILE_PATH = "customizedHTMLFilePaths";' ,
      'public static final String KEY_USERS_MANAGEMENT_JSON = "webUsersManagementJSON";']),

    (find_in("KEY_LISTENER_AUTO_START") + 1, "// HOROS-20240407 Reference: listener TLS per-listener", 
     ["// HOROS-20240407 Reference: listener TLS per-listener",
      'public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";' ,
      'public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS";']),

    (find_in("KEY_AUTO_CLEANING_COMMENTS") + 1, "// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - DB date formats", 
     ["// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - DB date formats",
      'public static final String KEY_DB_BIRTH_DATE_FORMAT = "DBDateOfBirthFormat2";' ,
      'public static final String KEY_SPLIT_MULTI_ECHO_MR = "splitMultiEchoMR";']),
]

key_ops.sort(key=lambda x: x[0], reverse=True)  # sort by original position DESCENDING
for pos, desc, keys in key_ops:
    for k in reversed(keys):
        L.insert(pos, k)
print(f"After Phase 1 (keys inserted at descending positions): {len(L)} lines")

# ===== Phase 2: Find class closing brace and insert getter/setters AFTER saveAll() method =====
# Find the FINAL '}' on its own line (column 0 indentation - class level), scanning BACKWARD from end of file
class_brace_idx = None
for i in range(len(L) - 1, -1, -1):
    if L[i].strip() == "}" and not L[i].startswith(" ") and not L[i].startswith("\t"):  
        # Pure '}' at column 0 (or with whitespace only) - likely class closing brace
        class_brace_idx = i
        break

print(f"Class closing brace found at index {class_brace_idx} (line {class_brace_idx + 1})")
assert class_brace_idx is not None, "Could not find class closing brace!"

# Now scan forward from beginning to find where the LAST '}' on its own line appears  
# before class_brace_idx - this should be the end of saveAll() method
# Actually simpler: find the last blank-line followed by class-brace sequence
last_blank_before_class = -1
for i in range(class_brace_idx, 0, -1):
    if not L[i].strip():  # blank line before class brace
        last_blank_before_class = i
    else:
        break

# SaveAll() ends at the closing '}' of its try block  
save_all_close = -1
for i in range(class_brace_idx - 1, 0, -1):
    if L[i].strip() == "}" and (i + 1 < len(L) and not L[i+1].strip()):
        # This '}' is followed by blank line - likely saveAll closing brace
        save_all_close = i
        break

# Insert AFTER saveAll's closing brace, before the class closing brace  
# That means insert at position save_all_close + 1 (= last_blank_before_class in most cases)
insert_target = last_blank_before_class
print(f"Inserting getter/setters at index {insert_target} (between blank line and class closing brace)")

# Build all getter/setter pairs to insert as ONE block  
getset_block = []
getset_block.append("") 
getset_block.append("/* ========== HOROS-20240407 Reference Data Accessors (Preference Pane) ========== */")
getset_block.append("")

# General: JP2K + compression settings
getset_block.extend(["", "// -- General pane - JP2K engine / compression settings (HOROS: OSIGeneralPreferencePanePref.m)", ""])  
getset_block.append('public static String getJP2KEngine() { return prefs.get(KEY_JP2K_ENGINE, "OpenJPEG"); }')
getset_block.append('public static void setJP2KEngine(String e) { prefs.put(KEY_JP2K_ENGINE, e); }')
getset_block.append('public static String getCompressionSettings() { return prefs.get(KEY_COMPRESSION_SETTINGS_JSON, ""); }')  
getset_block.append('public static void setCompressionSettings(String j) { prefs.put(KEY_COMPRESSION_SETTINGS_JSON, j); }')

# Database: UID / auto clean / date format
getset_block.extend(["", "// -- Database preferences (HOROS-20240407 Reference)", ""]) 
getset_block.append('public static String getUsePatientNameForUID() { return prefs.get(KEY_USE_PATIENT_NAME_FOR_UID, ""); }')
getset_block.append('public static void setUsePatientNameForUID(String m) { prefs.put(KEY_USE_PATIENT_NAME_FOR_UID, m); }') 
getset_block.append('public static int getAutoCleanDaysToKeep() { try { return prefs.getInt(KEY_AUTO_CLEAN_DAYS_TO_KEEP, 90); } catch (Exception e) { return 90; } }')
getset_block.append('public static void setAutoCleanDaysToKeep(int d) { prefs.put(KEY_AUTO_CLEAN_DAYS_TO_KEEP, String.valueOf(d)); }')

# Database: Use Series Description / Split Multi Echo MR  
getset_block.extend(["// -- DB use Series Description / Split Multi Echo MR (HOROS-20240407 Reference)", ""])
getset_block.append('public static boolean isUseSeriesDescription() { return prefs.getBoolean(KEY_USE_SERIES_DESCRIPTION, false); }')
getset_block.append('public static void setUseSeriesDescription(boolean b) { prefs.putBoolean(KEY_USE_SERIES_DESCRIPTION, b); }')

# Listener TLS  
getset_block.extend(["", "// -- Listener TLS settings (HOROS-20240407 Reference)", ""]) 
getset_block.append('public static boolean isListenerTLSEnabled() { return prefs.getBoolean(KEY_LISTENER_TLS_ENABLED, false); }')
getset_block.append('public static void setListenerTLSEnabled(boolean b) { prefs.putBoolean(KEY_LISTENER_TLS_ENABLED, b); }') 
getset_block.append('public static String getListenerTLSAuthCertName() { return prefs.get(KEY_LISTENER_TLS_CERT_AUTH, ""); }')
getset_block.append('public static void setListenerTLSAuthCertName(String n) { prefs.put(KEY_LISTENER_TLS_CERT_AUTH, n); }')

# Web Server / Locations: WADO  
getset_block.extend(["", "// -- WADO/WebServer settings (HOROS-20240407 Reference)", ""])
getset_block.append('public static int getWadoPort() { try { return prefs.getInt(KEY_WADO_PORT, 8443); } catch (Exception e) { return 8443; } }') 
getset_block.append('public static void setWadoPort(int p) { prefs.put(KEY_WADO_PORT, String.valueOf(p)); }')

# Web users management
getset_block.extend(["", "// -- Web users JSON (HOROS-20240407 Reference: OSIWebSharingReferencePane)", ""])  
getset_block.append('public static String getUserManagementJSON() { return prefs.get(KEY_USERS_MANAGEMENT_JSON, "[]"); }')
getset_block.append('public static void setUserManagementJSON(String j) { prefs.put(KEY_USERS_MANAGEMENT_JSON, j); }')

# Hanging / HotKeys / Annotations
getset_block.extend(["", "// -- Other preferences (HOROS-20240407 Reference)", ""])
getset_block.append('public static String getHangingProtocolBodyRegionsJSON() { return prefs.get(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, "[]"); }') 
getset_block.append('public static void setHangingProtocolBodyRegionsJSON(String j) { prefs.put(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, j); }')
getset_block.append('public static String getHotkeyBindingsJSON() { return prefs.get("hotkeyBindingsJSON", "[]"); }') 
getset_block.append('public static void setHotkeyBindingsJSON(String j) { prefs.put("hotkeyBindingsJSON", j); }')
getset_block.append('public static String getAnnotationDicomFieldMapJSON() { return prefs.get(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, "{}"); }')  
getset_block.append('public static void setAnnotationDicomFieldMapJSON(String j) { prefs.put(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, j); }')

# PET/SUV / On-Demand PACS
getset_block.extend(["// -- PET/SUV scanner normalize (HOROS-20240407 Reference)", ""]) 
getset_block.append('public static String getPetsuvNormalizeScanner() { return prefs.get(KEY_PET_SUV_NORMALIZE_SCANNER, ""); }')
getset_block.append('public static void setPetsuvNormalizeScanner(String s) { prefs.put(KEY_PET_SUV_NORMALIZE_SCANNER, s); }') 
getset_block.extend(["", "// -- On-Demand PACS source list (HOROS-20240407 Reference)", ""])
getset_block.append('public static String getOnDemandSourceListJSON() { return prefs.get(KEY_ONDEMAND_SOURCE_LIST_JSON, "[]"); }') 
getset_block.append('public static void setOnDemandSourceListJSON(String j) { prefs.put(KEY_ONDEMAND_SOURCE_LIST_JSON, j); }')

# Insert the block at insert_target (after saveAll's closing brace / blank line before class closing brace)
for item in reversed(getset_block):  # reverse to maintain order  
    L.insert(insert_target, item)

with open(SRC, "w", encoding="utf-8") as f:
    f.write("\n".join(L))

print(f"Final line count: {len(L)}")
