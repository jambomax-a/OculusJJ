import re

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

v5_start_marker = '// V5:'
eof_marker = 'toolbar.add(btnSync);'
start_idx = content.index(v5_start_marker)
end_idx = content.index(eof_marker, start_idx) + len(eof_marker)

before = content[:start_idx]
middle = content[start_idx:end_idx]
after = content[end_idx:]

# Replace known single-quoted strings with double-quoted equivalents in V1-V5 block only
replacements = [
    ["'Select DB'", '"Select DB"'],
    ["'Windows Tiling'", '"Windows Tiling"'],
    ["'Series Popup'", '"Series Popup"'],
    ["'Patient Panel'", '"Patient Panel"'],
]

for old, new in replacements:
    middle = middle.replace(old, new)

with open(path, 'w', encoding='utf-8') as f:
    f.write(before + middle + after)
print("OK")
