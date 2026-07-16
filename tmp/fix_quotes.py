import re

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

v1_start = content.find('// V1:')
v5_end_marker = '        toolbar.add(btnSync);'
idx = content.index(v5_end_marker, v1_start) + len(v5_end_marker)
before = content[:v1_start]
middle = content[v1_start:idx]

# Fix setToolTipText('text') -> setToolTipText("text")
middle = re.sub(r"setToolTipText\('([^']*)'\)", r'setToolTipText("\1")', middle)

# Fix JOptionPane.showMessageDialog(this, 'msg', 'title', ...)  
# Pattern: showMessageDialog(this, 'str', 'str'
def fix_show_msg(m):
    s = m.group(0)
    # Replace all single-quoted strings in this expression
    inner_parts = re.split(r'(showMessageDialog\s*\(\s*this,\s*)\')', s)
    result = ''
    for i, part in enumerate(inner_parts):
        if i % 2 == 1:  # the ' delimiter (single quotes only between commas or parens)
            result += '"' + ','  # closing single-quote was removed by split
        else:
            result += part
    return result

middle = re.sub(r"showMessageDialog\(\s*this,\s*'([^']*)',\s*'[^']*'\)", fix_show_msg, middle)

with open(path, 'w', encoding='utf-8') as f:
    f.write(before + middle + content[idx:])
print("OK: fixed quotes in V1-V5 block")

# Verify
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
bad_lines = []
for i,l in enumerate(lines):
    if "setToolTipText('" in l or "'Switch database" in l or "'Select DB'" in l:
        bad_lines.append((i+1, l.strip()))
if bad_lines:
    print("REMAINING ISSUES:")
    for ln, text in bad_lines[:5]:
        print(f"  {ln}: {text}")
else:
    print("VERIFIED OK")
