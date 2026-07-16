import re

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
lines = content.split('\n')

# Find all lines with single-quote string literals in Java context (outside comments)
for i, line in enumerate(lines):
    stripped = line.strip()
    if stripped.startswith('//'):
        continue  # comment line - skip
    # Look for showMessageDialog or setToolTipText with single-quoted args
    if "showMessageDialog" in line and "'" in line:
        print(f"LINE {i+1}: SHOW MSG: {stripped}")
    elif "setToolTip" in line and re.search(r"""setToolTip\(\s*'[^']*'""", line):
        print(f"LINE {i+1}: SETOLTIP: {stripped}")

# Also check LanguageManager.get lines with single quotes (these are also invalid)
for i, line in enumerate(lines):
    if "LanguageManager.get('" in line and not line.strip().startswith('//'):
        print(f"LINE {i+1}: LGM: {line.strip()}")
