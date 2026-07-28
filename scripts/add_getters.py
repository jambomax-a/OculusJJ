src = "src/main/java/com/oculus/jj/config/AppPreferences.java"
with open(src, encoding="utf-8") as f:
    L = f.read().split("\n")

# Insert before the last closing brace of the class (second-to-last line)
insert_pos = len(L) - 1

getters_setters = [
    "",
    "/* ========== HOROS-20240407 Reference: Preference Pane Data Accessors ========== */",
    "",
    '    // -- JP2K Engine selection (HOROS: OSIGeneralPreferencePanePref.m)',
    'public static String getJP2KEngine() { return prefs.get(KEY_JP2K_ENGINE, "OpenJPEG"); }',
    'public static void setJP2KEngine(String engine) { prefs.put(KEY_JP2K_ENGINE, engine); }',
    "",
    '    // -- Compression settings (JSON array string, HOROS: compressionSettingsCopy)',
    'public static String getCompressionSettings() { return prefs.get(KEY_COMPRESSION_SETTINGS_JSON, ""); }',
    'public static void setCompressionSettings(String json) { prefs.put(KEY_COMPRESSION_SETTINGS_JSON, json); }',
    "",
    '    // -- Database UID generation / date format (HOROS: OSIDatabasePreferencePanePref.m)',
    'public static String getUsePatientNameForUID() { return prefs.get(KEY_USE_PATIENT_NAME_FOR_UID, ""); }',
    'public static void setUsePatientNameForUID(String method) { prefs.put(KEY_USE_PATIENT_NAME_FOR_UID, method); }',
    'public static String getAutoCleanDaysToKeep() { return prefs.get(KEY_AUTO_CLEAN_DAYS_TO_KEEP, "90"); }',
    'public static void setAutoCleanDaysToKeep(String days) { prefs.put(KEY_AUTO_CLEAN_DAYS_TO_KEEP, days); }',
    "",
    '    // -- Listener TLS settings (HOROS: OSIListenerPreferencePanePref.mm)',
    'public static boolean isListenerTLSEnabled() { return prefs.getBoolean(KEY_LISTENER_TLS_ENABLED, false); }',
    'public static void setListenerTLSEnabled(boolean enabled) { prefs.putBoolean(KEY_LISTENER_TLS_ENABLED, enabled); }',
    'public static String getListenerTLSAuthCertName() { return prefs.get(KEY_LISTENER_TLS_CERT_AUTH, ""); }',
    'public static void setListenerTLSAuthCertName(String certName) { prefs.put(KEY_LISTENER_TLS_CERT_AUTH, certName); }',
    "",
    '    // -- Web Server / Locations (HOROS: OSIWebSharingPreferencePanePref.mm)',
    'public static int getWadoPort() { return prefs.getInt(KEY_WADO_PORT, 8443); }',
    'public static void setWadoPort(int port) { prefs.putInt(KEY_WADO_PORT, port); }',
    "",
    '    // -- JSON arrays for complex settings (HOROS reference)',
    'public static String getUserManagementJSON() { return prefs.get(KEY_USERS_MANAGEMENT_JSON, "[]"); }',
    'public static void setUserManagementJSON(String json) { prefs.put(KEY_USERS_MANAGEMENT_JSON, json); }',
    'public static String getHangingProtocolBodyRegionsJSON() { return prefs.get(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, "[]"); }',
    'public static void setHangingProtocolBodyRegionsJSON(String json) { prefs.put(KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON, json); }',
    "",
    "/* ========== END HOROS-20240407 Data Accessors ==========",
]

# Insert all at insert_pos
new_L = L[:insert_pos] + getters_setters + [L[insert_pos]]  # preserve the closing brace
with open(src, "w", encoding="utf-8") as f:
    f.write("\n".join(new_L))

print(f"AppPreferences.java updated. Lines: {len(new_L)}")
