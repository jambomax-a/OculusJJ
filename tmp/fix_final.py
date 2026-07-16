import re

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Find V5 start - search for '// V5:' marker (only the first one)
v5_start_marker = '// V5:'
eof_marker = 'toolbar.add(btnSync);'
start_idx = content.index(v5_start_marker)
end_idx = content.index(eof_marker, start_idx) + len('toolbar.add(btnSync);')

before = content[:start_idx]
middle = content[start_idx:end_idx]
after = content[end_idx:]

# Show actual single-quoted strings that need fixing (only in V5 block area)
print("Lines to fix:")
for i, line in enumerate(middle.split('\n')):
    if "showMessageDialog" in line and "'" in line:
        print(f"  BEFORE FIX LINE {i}: {line}")

# The middle section is the SAME from v1_start (// V5:) to end of btnSync block  
# But wait - that's only ~628 chars. Need to reconsider boundaries.
# Let me find a broader range: between // V4 and toolbar.addSeparator() after Flip/Rotate
v4_marker = '// V4:'  # this is in the middle somewhere
flip_sep = '        toolbar.addSeparator();\n\n\n        // Flip / Rotate'

if start_idx > content.index(v4_marker):
    print(f"\nv5_start index {start_idx} > v4_marker position - they're in same area!")

# Try finding V1 section start and use that + end of btnSync block  
v1_marker = '// V1:'
global_v1 = content.index(v1_marker)
print(f"\nV1 at global pos: {global_v1}")

# Now fix from // V4 (which contains all four gap markers up to the end of btnSync)
# Find ALL the problematic single-quoted strings globally in middle section
target_ranges = []
for m in re.finditer(r"showMessageDialog\(\s*this,\s*\"[^\"]+\",\s*'([^']*)'", content):
    print(f"  GLOBAL MATCH at {m.start()}: title='{m.group(1)}'")

# Actually fix the strings globally - replace single-quote String literals with double in specific patterns
content_fixed = re.sub(r"""showMessageDialog\(\s*this,\s*'([^']+)', """, r'showMessageDialog(this, "\1", ', content)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content_fixed)
print("\nFIXED - wrote file")
