import os, re

os.chdir(r'C:\Users\jam11\OculusJJ')
path = r'src\main\java\com\oculus\jj\ui\ThumbnailsListPanel.java'

with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old_func = '''    // HOROS-20240407準拠: ThumbnailsListPanel.m 53行目 - fixedWidth
    public static int getFixedWidth() {
        float w = ThumbnailButton.getThumbnailCellWidth();
        w += 20; // Javaのスクロールバー分を考慮して+10から+20に増やす
        return (int) w;
    }'''

new_func = '''    // HOROS-20240407準拠: ThumbnailsListPanel.m 53行目 - fixedWidth / Horos ThumbnailsList.xib equivalent 327px
    public static int getFixedWidth() {
        return 327; // HOROS ThumbnailsList.xib width=327 equivalent (Horos準拠)
    }'''

if old_func in content:
    content = content.replace(old_func, new_func)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("OK: getFixedWidth() fixed to 327 (Horos ThumbnailsList.xib equivalent)")
else:
    print("ERROR: old function not found")
