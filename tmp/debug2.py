import re

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'rb') as f:
    raw = f.read()
content = raw.decode('utf-8')

# Find all JOptionPane lines with single-quoted middle args in V1-V5 block  
v5_start_marker = '// V5:'
eof_marker = 'toolbar.add(btnSync);'
start_idx = content.index(v5_start_marker)
end_idx = content.index(eof_marker, start_idx) + len(eof_rng()) + 20 if False else -1
# Actually find real end by looking for toolbar.add(...) after V5 section
search_area = content[start_idx:]
lines_in_v5 = []
for i,line in enumerate(search_area.split('\n')):
    lines_in_v5.append((i, line))

print("V5 block contains:")
for _,l in lines_in_v5[:40]:
    print(f"  {repr(l)}")
