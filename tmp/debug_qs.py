import re

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# V5 section boundaries - use original middle from before fix
v5_start_marker = '// V5:'
eof_marker = 'toolbar.add(btnSync);'
start_idx = content.index(v5_start_marker)
end_idx = content.index(eof_marker, start_idx) + len(eof_marker)
middle = content[start_idx:end_idx]

# Show what the problem strings actually look like (with their quotes)
problem_patterns = ["Windows Tiling", "Series Popup", "Patient Panel", "DB"]
for pat in problem_patterns:
    # find all occurrences of this pattern with surrounding chars
    for m in re.finditer(pat, middle):
        start_pos = max(0, m.start() - 5)
        end_pos = min(len(middle), m.end() + 2)
        context = repr(middle[start_pos:end_pos])
        if "Windows" == pat or "Series" == pat or "Patient" == pat:
            print(f"Found [{pat}]: {context}")

# Print the actual middle substring that should contain these
if "'Select DB'" in middle:
    print("SELECT DB still has single quotes")
else:
    # Search for 'DB' with different contexts  
    for m in re.finditer(r".{0,20}DB.{0,10}", middle):
        print(f"  context around DB: {repr(m.group())}")

print("---")
print("Middle length:", len(middle))
# Find lines with single-quoted JOptionPane args
for i, line in enumerate(middle.split('\n')):
    if "showMessageDialog" in line and "'" in line:
        print(f"LINE {i}: {line}")
