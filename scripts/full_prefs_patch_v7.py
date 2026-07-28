import os

SRC = "src/main/java/com/oculus/jj/config/AppPreferences.java"
with open(SRC, encoding="utf-8") as f:
    L = f.read().split("\n")

print(f"Read {len(L)} lines from original file")

def find_in(orig):
    for i in range(len(L)):
        if orig in L[i]: return i
    raise Exception(f"Not found: {orig!r}")

# ===== Phase 1: Insert KEY constants (descending original positions) =====
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
      'public static final String KEY_WADO_URL = "wadoURL";' ,
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

key_ops.sort(key=lambda x: x[0], reverse=True)  
for pos, desc, keys in key_ops:
    for k in reversed(keys):
        L.insert(pos, k)
print(f"After Phase 1 (keys): {len(L)} lines")

# ===== Phase 2: Find the exact position AFTER saveAll's closing brace but BEFORE class closing brace =====
# In the modified file (after keys), find the pattern that indicates "class closing brace right after saveAll"  
# Look for: blank line or nothing between last '}' of a method and next '}' at column 0

# Scan backward from end to find the LAST pair where we have:
#   } <- closing of previous scope (method) 
#     [optional blank/comment]
#   } <- class closing brace  

# Find index where there's "}" alone on line then possibly a comment/blank, then "}"  
class_brace_pos = None
for i in range(len(L)-1, 0, -1):
    if L[i].strip() == "}":  # potential method close
        j = i + 1
        # Skip blank lines or comments between this } and next '}'
        while j < len(L) and not L[j].strip():  
            j += 1
        if j < len(L):
            prev = L[j-1].strip() if j > 0 else ""
            # Look for pattern: blank/comment then class brace or just a } followed by nothing useful
            next_content_start = j
            while next_content_start < len(L) and not L[next_content_start].strip():  
                next_content_start += 1
            if next_content_start == len(L):
                # Reached end - the line at i is the last '}' (class closing)
                class_brace_pos = i
                break

# Now find where to insert: I need to go BEFORE whatever comes before the blank-class-brace pattern  
# Let me just identify what's right BEFORE class_brace_pos in the modified file  
print(f"Class brace position identified as index {class_brace_pos}")

# The cleanest insertion strategy: look for a '}' that precedes the class closing. Insert after THAT '}'.
# Walk forward from beginning finding all single lines of "}"  
for i in range(len(L) - 1):
    if L[i].strip() == "}":
        # This could be saveAll's closing or another method
        # Check: is next non-blank line the class brace (which would make this '}' a candidate for insertion)?
        j = i + 1
        while j < len(L) and not L[j].strip():  
            j += 1
        if j >= len(L): break  # at end, skip 
        next_nonblank = L[j-1].strip() if j > 0 else ""
        # If we find a single '}' followed by a blank line then another '}', this is likely the right spot
        k = i + 1  
        while k < len(L) and not L[k].strip():  
            k += 1 
        if k < len(L) - 1:  # have more content after
            candidate_next = L[min(k, len(L)-1)]  # this is the class closing brace or comment
            print(f"Found potential insert point at i={i}, next non-blank line: {candidate_next.strip()!r}") 
            if k == len(L) - 1 and not L[k].strip():  
                break
        elif k == len(L):  # reached end with blanks only before
            print(f"End of file pattern at i={i}, next is blank then EOF")
            insert_point = i + 1  
            break

# If we didn't find above, use a simpler approach: 
if 'insert_point' not in dir():
    # Find the second-to-last '}' at column ~0 - that's likely saveAll close
    closing_braces = [i for i in range(len(L)) if L[i].strip() == "}"]  
    print(f"All single '}' positions: {closing_braces}")
    # Take the one whose next non-blank is the class closing brace or blank + class brace
    insert_point = -1
    for cb_idx in reversed(closing_braces):
        j = cb_idx + 1
        while j < len(L) and not L[j].strip():  
            j += 1
        if j >= len(L): break
        # Check if next meaningful line is a '}' (class closing brace) - then cb_idx's } is saveAll close
        next_content = L[min(j, len(L)-1)] if j < len(L) else ""
        if next_content.strip() == "}":  
            insert_point = cb_idx + 1
            print(f"Using saveAll closing brace at index {cb_idx}")
            break

# Insert getter/setter block at insert_point  
getset_block = [
    "", "/* ========== HOROS-20240407 Reference Data Accessors (Preference Pane) ========== */", ""
]

# General pane (HOROS: OSIGeneralPreferencePanePref.m - JP2K engine / compression settings) 
getset_block += ["// -- General pane (HOROS: OSIGeneralPreferencePanePref.m)", ""]
getset_block.append('public static String getJP2KEngine() { return prefs.get(KEY_JP2K_ENGINE, "OpenJPEG"); }')
getset_block.append('public static void setJP2KEngine(String e) { prefs.put(KEY_JP2K_ENGINE, e); }') 
getset_block.append('public static String getCompressionSettings() { return prefs.get(KEY_COMPRESSION_SETTINGS_JSON, ""); }')
getset_block.append('public static void setCompressionSettings(String j) { prefs.put(KEY_COMPRESSION_SETTINGS_JSON, j); }')

# Database (HOROS: OSIDatabasePreferencePanePref.m - UID generation / auto clean / date format) 
getset_block += ["// -- Database preferences (HOROS-20240407 Reference)", ""]
getset_block.append('public static String getUsePatientNameForUID() { return prefs.get(KEY_USE_PATIENT_NAME_FOR_UID, ""); }')  
getset_block.append('public static void setUsePatientNameForUID(String m) { prefs.put(KEY_USE_PATIENT_NAME_FOR_UID, m); }')
getset_block.append('public static int getAutoCleanDaysToKeep() { try { return prefs.getInt(KEY_AUTO_CLEAN_DAYS_TO_KEEP, 90); } catch (Exception e) { return 90; } }') 
getset_block.append('public static void setAutoCleanDaysToKeep(int d) { prefs.put(KEY_AUTO_CLEAN_DAYS_TO_KEEP, String.valueOf(d)); }')

# Database: use series description / split multi echo MR  
getset_block += ["// -- DB use Series Description / Split Multi Echo MR (HOROS-20240407 Reference)", ""] 
getset_block.append('public static boolean isUseSeriesDescription() { return prefs.getBoolean(KEY_USE_SERIES_DESCRIPTION, false); }')
getset_block.append('public static void setUseSeriesDescription(boolean b) { prefs.putBoolean(KEY_USE_SERIES_DESCRIPTION, b); }')

# Listener TLS (HOROS: OSIListenerPreferencePanePref.mm - TLS auth / cipher suites) 
getset_block += ["// -- Listener TLS settings (HOROS-20240407 Reference)", ""]
getset_block.append('public static boolean isListenerTLSEnabled() { return prefs.getBoolean(KEY_LISTENER_TLS_ENABLED, false); }')  
getset_block.append('public static void setListenerTLSEnabled(boolean b) { prefs.putBoolean(KEY_LISTENER_TLS_ENABLED, b); }') 
getset_block.append('public static String getListenerTLSAuthCertName() { return prefs.get(KEY_LISTENER_TLS_CERT_AUTH, ""); }')
getset_block.append('public static void setListenerTLSAuthCertName(String n) { prefs.put(KEY_LISTENER_TLS_CERT_AUTH, n); }')

# Web Server / Locations (HOROS: OSILocationsPreferencePane / OSIWebSharingReferencePane - WADO/URL settings)  
getset_block += ["// -- WADO/WebServer settings (HOROS-20240407 Reference)", ""] 
getset_block.append('public static int getWadoPort() { try { return prefs.getInt(KEY_WADO_PORT, 8443); } catch (Exception e) { return 8443; } }')
getset_block.append('public static void setWadoPort(int p) { prefs.put(KEY_WADO_PORT, String.valueOf(p)); }')

# Web users management (HOROS: OSIWebSharingReferencePane - usersArrayController + WebPortalUser.h)  
getset_block += ["// -- Web users JSON (HOROS-20240407 Reference)", ""] 
getset_block.append('public static String getUserManagementJSON() { return prefs.get(KEY_USERS_MANAGEMENT_JSON, "[]"); }')
getset_block.append('public static void setUserManagementJSON(String j) { prefs.put(KEY_USERS_MANAGEMENT_JSON, j); }')

# Hanging protocols (HOROS: BodyRegionArrayController.m / KeywordsArrayController.m)  
getset_block += ["// -- Hanging protocols (HOROS-20240407 Reference)", ""] 
getset_block.append('public static String getHangingProtocolBodyRegionsJSON() { return prefs.get(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, "[]"); }')
getset_block.append('public static void setHangingProtocolBodyRegionsJSON(String j) { prefs.put(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, j); }')

# HotKeys (HOROS: OSIHotKeysPref.m - stored as JSON array string) 
getset_block += ["// -- Hot key bindings (HOROS-20240407 Reference)", ""]
getset_block.append('public static String getHotkeyBindingsJSON() { return prefs.get("hotkeyBindingsJSON", "[]"); }') 
getset_block.append('public static void setHotkeyBindingsJSON(String j) { prefs.put("hotkeyBindingsJSON", j); }')

# Annotations (HOROS: OSICustomImageAnnotations.m / CIALayoutController.m - 54KB + DICOM field map)  
getset_block += ["// -- Annotation DICOM field map (HOROS-20240407 Reference)", ""] 
getset_block.append('public static String getAnnotationDicomFieldMapJSON() { return prefs.get(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, "{}"); }')
getset_block.append('public static void setAnnotationDicomFieldMapJSON(String j) { prefs.put(KEY_ANNOTATION_DICOM_FIELD_MAP_JSON, j); }')

# PET/SUV (HOROS: OSIPETPreferencePane.m - SUV normalization scanner identifier)  
getset_block += ["// -- PET/SUV scanner normalize (HOROS-20240407 Reference)", ""] 
getset_block.append('public static String getPetsuvNormalizeScanner() { return prefs.get(KEY_PET_SUV_NORMALIZE_SCANNER, ""); }')
getset_block.append('public static void setPetsuvNormalizeScanner(String s) { prefs.put(KEY_PET_SUV_NORMALIZE_SCANNER, s); }')

# On-Demand PACS (HOROS: OSIPACSOnDemandPreferencePane.m - query source list for comparative studies / smart albums)  
getset_block += ["// -- On-demand PACS source list (HOROS-20240407 Reference)", ""] 
getset_block.append('public static String getOnDemandSourceListJSON() { return prefs.get(KEY_ONDEMAND_SOURCE_LIST_JSON, "[]"); }')
getset_block.append('public static void setOnDemandSourceListJSON(String j) { prefs.put(KEY_ONDEMAND_SOURCE_LIST_JSON, j); }')

# Insert at insert_point (after method closing brace but before class closing brace)  
for item in reversed(getset_block):  # reverse to maintain order when inserting sequentially
    L.insert(insert_point, item)

with open(SRC, "w", encoding="utf-8") as f:
    f.write("\n".join(L))

print(f"Final line count: {len(L)}")
