$src = 'src/main/java/com/oculus/jj/config/AppPreferences.java'
$lines = [System.IO.File]::ReadAllLines($src, [System.Text.Encoding]::UTF8)

# Convert to ArrayList for easy insertion
[System.Collections.ArrayList]$arr = @( $lines )

$insert1 = @(
    '    // HOROS-20240407準拠: OSIGeneralPreferencePanePref.m — JP2KEngine="Kakadu"/"OpenJPEG"',
    '    public static final String KEY_JP2K_ENGINE = "JP2KEngine";',
    '    public static final String KEY_COMPRESSION_SETTINGS_JSON = "CompressionSettings";',
    '    public static final String KEY_COMPRESSION_LOW_RES_SETTINGS_JSON = "CompressionSettingsLowRes"'
)

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

$insert4_idx = 0; while ($insert4_idx -lt $arr.Count -and $arr[$insert4_idx] -notlike '*// === Web Server*') { $insert4_idx++ }
$insert4 = @(
    '',
    '    // HOROS-20240407準拠: OSIListenerPreferencePanePref.m — TLS認証/暗号化スイート等',
    '    public static final String KEY_LISTENER_TLS_ENABLED = "STORESCPTLS";',
    '    public static final String KEY_LISTENER_TLS_CERT_AUTH = "TLSAuthenticationCertificate";',
    '    public static final String KEY_LISTENER_TLS_CIPHER_SUITE = "TLSSupportedCipherSuite";',
    '    public static final String KEY_LISTENER_DH_PARAM_FILE_URL = "TLSDHParameterFileURL";',
    '    public static final String KEY_LISTENER_CERT_VERIFY_LEVEL = "TLSCertificateVerification";',
    '    public static final String KEY_LISTENER_TLS_USE_SAME_AETITLE = "TLSUseSameAETitle";',
    '    public static final String KEY_LISTENER_TLS_STORESCP_AETITLE = "TLSStoreSCPAETITLEIsDefaultAET"'
)

$insert5_idx = 0; while ($insert5_idx -lt $arr.Count -and $arr[$insert5_idx] -notlike '*KEY_WEB_SERVER_PORT*') { $insert5_idx++ }
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

$insert6_idx = 0; while ($insert6_idx -lt $arr.Count -and $arr[$insert6_idx] -notlike '*KEY_LISTENER_AUTO_START*') { $insert6_idx++ }
$insert6 = @(
    '    // HOROS-20240407準拠: listener TLS per-listener',
    '    public static final String KEY_LISTENER_AUTO_ADD_CERTIFICATE = "autoAddCertificate";',
    '    public static final String KEY_LISTENER_PORT_TLS = "listener.portTLS"'
)

# Find positions and apply in reverse order (largest index first)
$pos1 = 25; # after KEY_CHECK_UPDATES at line 26 (0-indexed: 25)
$pos2 = 33; # after KEY_ERASE_DB_AT_STARTUP at line 34 (0-indexed: 33)  
$pos3 = 57; # after KEY_HOTKEYS at line 58 (0-indexed: 57)

Write-Output "Applying insertions in reverse order..."
Write-Output "insert1 at pos $($pos1): $($insert1.Count) lines"
Write-Output "insert2 at pos $($pos2): $($insert2.Count) lines"
Write-Output "insert3 at pos $($pos3): $($insert3.Count) lines"

# Apply in reverse order (largest index first to maintain correctness)
$ops = @(
    @{P=$pos1; K=$insert1},
    @{P=$pos2; K=$insert2},
    @{P=$pos3; K=$insert3}
) | Sort-Object -Property P -Descending

foreach ($op in $ops) {
    for ($j = 0; $j -lt $op.K.Count; $j++) {
        $arr.Insert($op.P + $j, $op.K[$j]) > $null
    }
}

# Find new positions after earlier inserts
$idx4 = 0; while ($idx4 -lt $arr.Count -and $arr[$idx4] -notlike '*// === Web Server*') { $idx4++ }
Write-Output "insert4 at pos $($idx4): $($insert4.Count) lines"

$ops2 = @(@{P=$idx4; K=$insert4}) | Sort-Object -Property P -Descending

foreach ($op in $ops2) {
    for ($j = 0; $j -lt $op.K.Count; $j++) {
        $arr.Insert($op.P + $j, $op.K[$j]) > $null
    }
}

$idx5 = 0; while ($idx5 -lt $arr.Count -and $arr[$idx5] -notlike '*KEY_WEB_SERVER_PORT*') { $idx5++ }
Write-Output "insert5 at pos $($idx5): $($insert5.Count) lines"
foreach ($j = 0; $j -lt $insert5.Count; $j++) {
    $arr.Insert($idx5 + $j, $insert5[$j]) > $null
}

$idx6 = 0; while ($idx6 -lt $arr.Count -and $arr[$idx6] -notlike '*KEY_LISTENER_AUTO_START*') { $idx6++ }
Write-Output "insert6 at pos $($idx6): $($insert6.Count) lines"
foreach ($j = 0; $j -lt $insert6.Count; $j++) {
    $arr.Insert($idx6 + $j, $insert6[$j]) > $null
}

# Convert back to string array and write
[string[]]$result = [string[]]::ConvertFromArrayList($arr) | ForEach-Object { $_ }
[System.IO.File]::WriteAllLines($src, $result)
$finalCount = (Get-Content $src).Count
Write-Output "DONE! AppPreferences.java: now has $($finalCount) lines"
