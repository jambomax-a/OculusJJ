$src = 'src/main/java/com/oculus/jj/config/AppPreferences.java'
$lines = [System.IO.File]::ReadAllLines($src, [System.Text.Encoding]::UTF8)

# === Phase 1: Add keys after line 26 (KEY_CHECK_UPDATES) - General pane JP2K + compression ===
$insert1 = @(
    '    // HOROS-20240407準拠: OSIGeneralPreferencePanePref.m — JP2KEngine="Kakadu"/"OpenJPEG"',
    '    public static final String KEY_JP2K_ENGINE = "JP2KEngine";',
    '    public static final String KEY_COMPRESSION_SETTINGS_JSON = "CompressionSettings";',
    '    public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes"'
)

# === Phase 2: Add keys after line 34 (KEY_ERASE_DB_AT_STARTUP) - Database pane UID/date format ===
$insert2 = @(
    '    // HOROS-20240407準拠: OSIDatabasePreferencePanePref.m — UID生成方式/日付フォーマット',
    '    public static final String KEY_USE_PATIENT_NAME_FOR_UID = "usePatientNameForUID";',
    '    public static final String KEY_USE_PATIENT_BIRTH_DATE_FOR_UID = "usePatientBirthDateForUID";',
    '    public static final String KEY_AUTO_CLEAN_DAYS_TO_KEEP = "AutoCleanDaysToKeep";',
    '    public static final String KEY_DB_DATE_FORMAT = "DBDateFormat2";',
    '    public static final String KEY_DB_BIRTH_DATE_FORMAT = "DBDateOfBirthFormat2";',
    '    public static final String KEY_USE_SERIES_DESCRIPTION = "useSeriesDescription";',
    '    public static final String KEY_SPLIT_MULTI_ECHO_MR = "splitMultiEchoMR"'
)

# === Phase 3: Add keys after line 58 (KEY_HOTKEYS) - Hanging/HOTKEYS/etc. ===
$insert3 = @(
    '',
    '    // HOROS-20240407準拠: annotation DICOM field map, hanging protocol body/keyword lists',
    '    public static final String KEY_ANNOTATION_DICOM_FIELD_MAP_JSON = "annotationDicomFieldMap";',
    '    public static final String KEY_HANGING_PROTOCOL_BODY_REGIONS_JSON = "hangingProtocolBodyRegions";',
    '    public static final String KEY_HANGING_KEYWORDS_JSON = "hangingKeywords";',
    '    public static final String KEY_PET_SUV_NORMALIZE_SCANNER = "pet.suvNormalizeScanner";',
    '    public static final String KEY_ONDEMAND_SOURCE_LIST_JSON = "pacsOnDemandSourceList"',
    '',
    '    // HOROS-20240407準拠: OSIHotKeysPref.m — ホットキーバインディング管理(JSON配列)',
    '    public static final String KEY_HOTKEY_BINDINGS_JSON = "hotkeyBindingsJSON"'
)

# === Phase 4: Add keys in Listener section (before "// === Web Server") - TLS settings ===
$idx4 = 0; while ($idx4 -lt $lines.Count -and $lines[$idx4] -notlike '*// === Web Server*') { $idx4++ }
$insert4 = @(
    '',
    '    // HOROS-20240407準拠: OSIListenerPreferencePanePref.m — TLS認証/暗号化スイート等',
    '    public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
    '    public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";',
    '    public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
    '    public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";',
    '    public static final String KEY_LISTENER_CERT_VERIFY_LEVEL = "TLSCertificateVerification";',
    '    public static final String KEY_LISTENER_TLS_USE_SAME_AETITLE = "TLSUseSameAETitle";',
    '    public static final String KEY_LISTENER_TLS_STORESCP_AETITLE = "TLSStoreSCPAETITLEIsDefaultAET";'
)

# === Phase 5: Add keys in Web Server section (after KEY_WEB_SERVER_PORT line) - WADO/User mgmt ===
$idx5 = 0; while ($idx5 -lt $lines.Count -and $lines[$idx5] -notlike '*KEY_WEB_SERVER_PORT*') { $idx5++ }
$insert5 = @(
    '',
    '    // HOROS-20240407準拠: OSILocationsPreferencePane / OSIWebSharingPreferencePane',
    '    public static final String KEY_WADO_ENABLED = "WADOEnabled";',
    '    public static final String KEY_WADO_PORT = "wadomoduleportnumber";',
    '    public static final String KEY_WADO_URL = "wadoURL";',
    '    public static final String KEY_WADO_TRANSFER_SYNTAX = "WADOTransferSyntaxes";',
    '    public static final String KEY_WADO_USERNAME = "WADOTokenUsername";',
    '    public static final String KEY_WADO_PASSWORD = "WADOTokenPassword";',
    '    public static final String KEY_WEB_PORTAL_URL = "WebPortalURL";',
    '    public static final String KEY_CUSTOM_HTML_FILE_PATH = "customizedHTMLFilePaths";',
    '    public static final String KEY_USERS_MANAGEMENT_JSON = "webUsersManagementJSON"'
)

# === Phase 6: Add keys in Listeners section (after KEY_LISTENER_AUTO_START) - TLS per-listener ===
$idx6 = 0; while ($idx6 -lt $lines.Count -and $lines[$idx6] -notlike '*KEY_LISTENER_AUTO_START*') { $idx6++ }
$insert6 = @(
    '    // HOROS-20240407準拠: listener TLS per-listener',
    '    public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";',
    '    public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS";'
)

# Apply insertions (reverse order of line numbers so indices don't shift)
# First find actual line positions
$pos26 = 0; while ($lines[$pos26] -notlike '*KEY_CHECK_UPDATES*') { $pos26++ }
Write-Output "Found KEY_CHECK_UPDATES at index $($pos26) (line $($pos26+1))"

$pos34 = 0; while ($lines[$pos34] -notlike '*KEY_ERASE_DB_AT_STARTUP*') { $pos34++ }
Write-Output "Found KEY_ERASE_DB_AT_STARTUP at index $($pos34) (line $($pos34+1))"

$pos58 = 0; while ($lines[$pos58] -notlike '*KEY_HOTKEYS *' -and $lines[$pos58] -notlike "*KEY_HOTKEYS*") { $pos58++ }
Write-Output "Found KEY_HOTKEYS at index $($pos58) (line $($pos58+1))"

$posWeb = 0; while ($idx4 -lt $lines.Count -and $lines[$idx4] -notlike '*// === Web Server*') { $idx4++ }
Write-Output "Found Web Server section at index $($idx4) (line $($idx4+1))"

$posWebPort = 0; while ($idx5 -lt $lines.Count -and $lines[$idx5] -notlike '*KEY_WEB_SERVER_PORT*') { $idx5++ }
Write-Output "Found KEY_WEB_SERVER_PORT at index $($idx5) (line $($idx5+1))"

$posListenerAuto = 0; while ($idx6 -lt $lines.Count -and $lines[$idx6] -notlike '*KEY_LISTENER_AUTO_START*') { $idx6++ }
Write-Output "Found KEY_LISTENER_AUTO_START at index $($idx6) (line $($idx6+1))"

# Now apply: process in reverse order of positions
$toApply = @(
    @{Pos=$pos26; Keys=$insert1},
    @{Pos=$pos34; Keys=$insert2},
    @{Pos=$pos58; Keys=$insert3},
    @{Pos=$idx4; Keys=$insert4},
    @{Pos=$idx5; Keys=$insert5},
    @{Pos=$idx6; Keys=$insert6}
)

# Sort by position descending (largest first) so earlier inserts don't shift later ones
$toApply = $toApply | Sort-Object -Property Pos -Descending

foreach ($item in $toApply) {
    Write-Output "Inserting $($item.Keys.Count) lines at index $($item.Pos)..."
    for ($k = 0; $k -lt $item.Keys.Count; $k++) {
        $lines = [array]::Insert($lines, $item.Pos + $k, $item.Keys[$k])
    }
}

[System.IO.File]::WriteAllLines($src, $lines)
$finalCount = (Get-Content $src).Count
Write-Output "AppPreferences.java: now has $finalCount lines (was 621)"
