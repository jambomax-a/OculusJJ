import re

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Find V5 section boundaries
v5_start_marker = '// V5:'
eof_marker = 'toolbar.add(btnSync);'
start = content.index(v5_start_marker)
end = content.index(eof_marker, start) + len(eof_marker)

before = content[:start]
middle = content[start:end]
after = content[end:]

# Fix remaining single-quoted String literals in the V1-V5 block
# Pattern: JOptionPane.showMessageDialog( this, "double-quoted", 'single-quoted', ENUM )
fixed_middle = re.sub(
    r"showMessageDialog\(\s*this,\s*\"[^\"]+\",\s*'([^']*)'\s*,",
    lambda m: f'showMessageDialog(this, "{m.group(0)[middle_len_before]}"{m.group(1)}",', middle=50) if False else None for _ in [None] or []

# Simpler: just replace known patterns directly  
replacements = [
    (r'\x27Select DB\x27', '"Select DB"'),
    (r"\x27Windows Tiling\x27", '"Windows Tiling"'),
    (r"\x27Series Popup\x27", '"Series Popup"'),
    (r"\x27Patient Panel\x27", '"Patient Panel"'),
    ("\\'Sync ON\\'", "'Sync ON'"),  # leave this one as-is (it's a ternary)
]

for pat, rep in replacements:
    if pat != replacements[-1][0]:  # skip the last one
        middle = re.sub(pat, rep, middle)

# Actually simpler approach - just do direct string replaces for known strings  
middle = middle.replace("'Select DB'", '"Select DB"')
middle = middle.replace("'Windows Tiling'", '"Windows Tiling"')
middle = middle.replace("'Series Popup'", '"Series Popup"')
middle = middle.replace("'Patient Panel'", '"Patient Panel"')

with open(path, 'w', encoding='utf-8') as f:
    f.write(before + middle + after)
print("OK")
