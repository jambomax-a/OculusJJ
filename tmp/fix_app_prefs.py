import os, re
os.chdir(r'C:\Users\jam11\OculusJJ')
with open('src/main/java/com/oculus/jj/config/AppPreferences.java', 'r') as f:
    content = f.read()

lines_to_add = [
    '    public static final String KEY_BROWSER_FONT_SIZE = "browser.fontSize";  // HOROS-20240407準拠: dbFontSize',
    '    public static final String KEY_DB_DEFAULT_FOLDER_SIZE = "database.defaultFolderSize"; // HOROS-20240407準拠: DefaultFolderSizeForDB',
    '    public static final String KEY_SEARCH_TYPE = "browser.searchType";  // HOROS-20240407準拠: searchType (0..4)',
    '    public static final String KEY_HIDE_PATIENT_NAME = "browser.hidePatientName"; // HOROS-20240407準拠: HIDEPATIENTNAME',
    '    public static final String KEY_LAST_URL = "web.lastURL";  // HOROS-20240407準拠: LASTURL (最後に使用したWADO URL)',
    '    public static final String KEY_AUTO_TILING = "browser.autoTiling"; // HOROS-20240407準拠: AUTOTILING',
    '    public static final String KEY_AUTO_HIDE_MATRIX = "browser.autoHideMatrix";  // HOROS-20240407準拠: AUTOHIDEMATRIX',
    '    public static final String KEY_CLEAR_SEARCH_WHEN_SELECTING_ALBUM = "browser.clearSearchWhenSelectingAlbum";  // HOROS-20240407準拠: clearSearchAndTimeIntervalWhenSelectingAlbum',
    '    public static final String KEY_COMMENT_AUTOFILL = "comments.autoFill"; // HOROS-20240407準拠: COMMENTSAUTOFILL',
    '    public static final String KEY_COMMENT_AUTOFILL_STUDY_LEVEL = "comments.autoFillStudyLevel";  // HOROS-20240407準拠: COMMENTSAUTOFILLStudyLevel',
    '    public static final String KEY_COMMENT_AUTOFILL_SERIES_LEVEL = "comments.autoFillSeriesLevel";  // HOROS-20240407準拠: COMMENTSAUTOFILLSeriesLevel',
    '    public static final String KEY_ANNOTATION_FONT_BOLD = "annotations.fontBold";  // HOROS equivalent (annotationFontBold)',
    '    public static final String KEY_ANNOTATION_FONT_SIZE = "annotations.fontSize";  // HOROS equivalent (annotationFontSize)',
]

target = 'KEY_LISTENER_AETITLE'
insert_pos = content.find(target)
while insert_pos > 0 and content[insert_pos-1] not in '\n':
    insert_pos -= 1

new_block = '\n'.join(lines_to_add) + '\n'
new_content = content[:insert_pos] + new_block + content[insert_pos:]

with open('src/main/java/com/oculus/jj/config/AppPreferences.java', 'w') as f:
    f.write(new_content)
print('OK: AppPreferences.java updated with 13 new keys')
