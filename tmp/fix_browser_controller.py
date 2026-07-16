import os, re

os.chdir(r'C:\Users\jam11\OculusJJ')
with open('src/main/java/com/oculus/jj/ui/BrowserController.java', 'r') as f:
    content = f.read()

# Replace hardcoded strings with AppPreferences constants
replacements = [
    ('prefs.getBoolean("AUTOTILING", true)', 'prefs.getBoolean(AppPreferences.KEY_AUTO_TILING, true)'),
    ('prefs.getBoolean("annotationFontBold", false)', 'prefs.getBoolean(AppPreferences.KEY_ANNOTATION_FONT_BOLD, false)'),
    ('prefs.putBoolean("annotationFontBold", !currentBold)', 'prefs.putBoolean(AppPreferences.KEY_ANNOTATION_FONT_BOLD, !currentBold)'),
    ('prefs.getInt("annotationFontSize", 12)', 'prefs.getInt(AppPreferences.KEY_ANNOTATION_FONT_SIZE, 12)'),
    ('prefs.putInt("annotationFontSize", newSize)', 'prefs.putInt(AppPreferences.KEY_ANNOTATION_FONT_SIZE, newSize)'),
]

for old, new in replacements:
    if old not in content:
        print(f'WARNING: "{old}" not found')
    else:
        count = content.count(old)
        content = content.replace(old, new)
        print(f'Replaced {count} occurrence(s): {old[:50]}...')

with open('src/main/java/com/oculus/jj/ui/BrowserController.java', 'w') as f:
    f.write(content)
print('OK: BrowserController.java updated with AppPreferences constants')
