import os

SRC = "src/main/java/com/oculus/jj/config/AppPreferences.java"
with open(SRC, encoding="utf-8") as f:
    L = f.read().split("\n")

print("Read %d lines from original file" % len(L))

def find_in(orig):
    for i in range(len(L)):
        if orig in L[i]: return i
    raise Exception("Not found: " + repr(orig))

# Phase 1: Insert KEY constants at original indices, descending order  
key_ops = [
    (find_in("KEY_CHECK_UPDATES") + 1, ["// HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m - JP2K engine / compression settings",
        "public static final String KEY_JP2K_ENGINE = \"JP2KEngine\";",
        "public static final String KEY_COMPRESSION_SETTINGS_JSON = \"CompressionSettings\";",
        'public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes";']),

    (find_in("KEY_ERASE_DB_AT_STARTUP") + 1, ["// HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m - UID generation / date formats",
        'public static final String KEY_USE_PATIENT_NAME_FOR_UID = "usePatientNameForUID";',
        "public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = \"AutoCleanDaysToKeep\";",
        'public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";',
        'public static final String KEY_USE_SERIES_DESCRIPTION = "useSeriesDescription";']),

    (find_in("public static final String KEY_HOTKEYS") + 1, ["", "// HOROS-20240407 Reference: annotations / hanging protocols / on-demand PACS",
        "public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = \"hangingProtocolBodyRegions\";",
        'public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";' ,
        'public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";' ,
        "public static final String KEY_PET_SUV_NORMALIZE_SCANNER = \"pet.suvNormalizeScanner\";",
        'public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList";']),

    (find_in("// === Web Server"), ["", "// HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm - TLS settings",
        'public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
        'public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";' ,
        'public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
        'public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";']),

    (find_in("KEY_WEB_SERVER_PORT") + 1, ["", "// HOROS-20240407 Reference: OSILocationsPreferencePane / OSIWebSharingReferencePane",
        'public static final String KEY_WADO_ENABLED = "WADOEnabled";',
        'public static final String KEY_WADO_PORT = "wadomoduleportnumber";',
        'public static final String KEY_WADO_URL = "wadoURL";' ,  # Horos reference key
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

key_ops.sort(key=lambda x: x[0], reverse=True)  # descending order so earlier indices stay stable  
for pos, keys in key_ops:
    for k in reversed(keys):
        L.insert(pos, k)

print("After Phase 1 (keys inserted): %d lines" % len(L))

# Phase 2: Find where to insert getters/setters - after saveAll() ends, before class closing brace
# Strategy: find the LAST '}' at column ~0 that is followed by either another '}' (class close) or blank + '}'
# This identifies where one method's scope closes right before the class itself.

last_method_close = None
for i in range(len(L) - 2, -1, -1):
    if L[i].strip() == "}":
        # Check: is this a single-line } followed by another '}' at column ~0 (or blank + '}')?
        j = i + 1  
        while j < len(L) and not L[j].strip():  # skip blanks
            j += 1
        if j < len(L):
            next_content = L[j].strip()
            if next_content == "}":
                # Found: method close } followed by class closing brace (possibly with blank lines between)
                last_method_close = i  
                print("Found potential insert point - method closes at index %d, class brace at %d" % (i, j))
                break

# If we found a candidate, the insert position is right after it
if last_method_close:
    insert_pos = last_method_close + 1
else:
    print("Warning: no clean method-close pattern found - will use fallback")
    # Fallback: just find any '}' followed by another '}' with optional blank lines in between  
    for i in range(len(L) - 2, -1, -1):
        if L[i].strip() == "}":
            j = i + 1
            while j < len(L) and not L[j].strip():
                j += 1
            if j < len(L) and L[j].strip() == "}":
                insert_pos = i + 1
                break

print("Inserting getter/setters at position %d" % insert_pos)  

# Build the complete getter/setter block to insert  
block = []
block.append("") 
block.append("/* ========== HOROS-20240407 Reference Data Accessors (Preference Pane) ========== */")
block.append("")

# General pane: JP2K engine + compression settings (HOROS: OSIGeneralPreferencePanePref.m ~16KB .m file)  
block += ["// -- General pane - JP2K Engine / Compression Settings", ""]
block.append('public static String getJP2KEngine() { return prefs.get(KEY_JP2K_ENGINE, "OpenJPEG"); }')
block.append('public static void setJP2KEngine(String e) { prefs.put(KEY_JP2K_ENGINE, e); }')
block.append('public static String getCompressionSettings() { return prefs.get(KEY_COMPRESSION_SETTINGS_JSON, ""); }')  
block.append('public static void setCompressionSettings(String j) { prefs.put(KEY_COMPRESSION_SETTINGS_JSON, j); }')

# Database pane: UID generation / auto clean days / date format (HOROS: OSIDatabasePreferencePanePref.m ~24KB .m file) 
block += ["// -- Database preferences - UID generation, auto-clean, date formats", ""]  
block.append('public static String getUsePatientNameForUID() { return prefs.get(KEY_USE_PATIENT_NAME_FOR_UID, ""); }')
block.append('public static void setUsePatientNameForUID(String m) { prefs.put(KEY_USE_PATIENT_NAME_FOR_UID, m); }') 
block.append('public static int getAutoCleanDaysToKeep() { try { return prefs.getInt(KEY_AUTO_CLEAN_DAYS_TO_KEEP, 90); } catch (Exception e) { return 90; } }')
block.append('public static void setAutoCleanDaysToKeep(int d) { prefs.put(KEY_AUTO_CLEAN_DAYS_TO_KEEP, String.valueOf(d)); }')

# Database: Use series description / Split multi-echo MR  
block += ["// -- DB useSeriesDescription / splitMultiEchoMR (HOROS-20240407 Reference)", ""]
block.append('public static boolean isUseSeriesDescription() { return prefs.getBoolean(KEY_USE_SERIES_DESCRIPTION, false); }')
block.append('public static void setUseSeriesDescription(boolean b) { prefs.putBoolean(KEY_USE_SERIES_DESCRIPTION, b); }')

# Listener TLS settings (HOROS: OSIListenerPreferencePanePref.mm ~16KB .m file - second largest pane)  
block += ["// -- Listener TLS authentication / cipher suites (HOROS-20240407 Reference)", ""]
block.append('public static boolean isListenerTLSEnabled() { return prefs.getBoolean(KEY_LISTENER_TLS_ENABLED, false); }') 
block.append('public static void setListenerTLSEnabled(boolean b) { prefs.putBoolean(KEY_LISTENER_TLS_ENABLED, b); }')
block.append('public static String getListenerTLSAuthCertName() { return prefs.get(KEY_LISTENER_TLS_CERT_AUTH, ""); }')  
block.append('public static void setListenerTLSAuthCertName(String n) { prefs.put(KEY_LISTENER_TLS_CERT_AUTH, n); }')

# Web Server / Locations: WADO port + URL (HOROS: OSILocationsPreferencePane ~36KB .m file - LARGEST pane!)
block += ["// -- WADO/WebServer settings (HOROS-20240407 Reference)", ""] 
block.append('public static int getWadoPort() { try { return prefs.getInt(KEY_WADO_PORT, 8443); } catch (Exception e) { return 8443; } }')
block.append('public static void setWadoPort(int p) { prefs.put(KEY_WADO_PORT, String.valueOf(p)); }')

# Web users management (HOROS: OSIWebSharingPreferencePane - uses usersArrayController + WebPortalUser.h)  
block += ["// -- Web users JSON (HOROS-20240407 Reference: OSIWebSharingReferencePane)", ""] 
block.append('public static String getUserManagementJSON() { return prefs.get(KEY_USERS_MANAGEMENT_JSON, "[]"); }')
block.append('public static void setUserManagementJSON(String j) { prefs.put(KEY_USERS_MANAGEMENT_JSON, j); }')

# Hanging protocols / Hot Keys / Annotations / On-Demand PACS (HOROS Reference - smaller panes)  
block += ["// -- Other preferences (HOROS-20240407 Reference)", ""]
block.append('public static String getHangingProtocolBodyRegionsJSON() { return prefs.get(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, "[]"); }') 
block.append('public static void setHangingProtocolBodyRegionsJSON(String j) { prefs.put(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, j); }')

# HotKeys JSON bindings  
block += ["// -- Hot key bindings (HOROS-20240407 Reference: OSIHotKeysPref.m)", ""] 
block.append('public static String getHotkeyBindingsJSON() { return prefs.get("hotkeyBindingsJSON", "[]"); }')
block.append('public static void setHotkeyBindingsJSON(String j) { prefs.put("hotkeyBindingsJSON", j); }')

# Annotations DICOM field map (HOROS: OSICustomImageAnnotations.m / CIALayoutController.m - 54KB .m file!)  
block += ["// -- Annotation DICOM field map (HOROS-20240407 Reference)", ""]
block.append('public static String getAnnotationDicomFieldMapJSON() { return prefs.get(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, "{}"); }') 
block.append('public static void setAnnotationDicomFieldMapJSON(String j) { prefs.put(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, j); }')

# PET/SUV normalization scanner identifier (HOROS: OSIPETPreferencePane.m ~5.5KB .m file)  
block += ["// -- PET/SUV scanner normalize (HOROS-20240407 Reference)", ""]
block.append('public static String getPetsuvNormalizeScanner() { return prefs.get(KEY_PET_SUV_NORMALIZE_SCANNER, ""); }') 
block.append('public static void setPetsuvNormalizeScanner(String s) { prefs.put(KEY_PET_SUV_NORMALIZE_SCANNER, s); }')

# On-Demand PACS source list (HOROS: OSIPACSOnDemandPreferencePane.m ~19KB .m file - large pane!)
block += ["// -- On-demand PACS source list (HOROS-20240407 Reference)", ""]  
block.append('public static String getOnDemandSourceListJSON() { return prefs.get(KEY_ONDEMAND_SOURCE_LIST_JSON, "[]"); }') 
block.append('public static void setOnDemandSourceListJSON(String j) { prefs.put(KEY_ONDEMAND_SOURCE_LIST_JSON, j); }')

# Insert block - each line one at a time in reverse order to maintain forward insertion sequence
for item in reversed(block):
    L.insert(insert_pos, item)

with open(SRC, "w", encoding="utf-8") as f:
    f.write("\n".join(L))

print("Final line count: %d" % len(L))
