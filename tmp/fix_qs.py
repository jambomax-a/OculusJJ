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

# Fix all setToolTipText(' -> setToolTipText("  and closing ') -> "
import re as r2
middle = r2.sub(r"setToolTipText\('([^']+)'", lambda m: 'setToolTipText("' + m.group(1) + '"', middle)
middle = r2.sub(r'showMessageDialog\(([^,]+),\s*' + "'" + r'([A-Za-z\s\-\/&%]+)\'' + r"\)", 
    lambda m: 'showMessageDialog(' + m.group(1).strip() + ', "' + m.group(2) + '"', middle)

with open(path, 'w', encoding='utf-8') as f:
    f.write(before + middle + after)
print("OK")
