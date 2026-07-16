import re
path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'rb') as f: content_bytes = f.read()

# Search for the byte pattern around Windows Tiling with single quote
import codecs
content = content_bytes.decode('utf-8')
for m in re.finditer(r".{0,5}Windows Tiling.{0,10}", content):
    start_hex = codecs.encode(content_bytes[m.start():m.start()+5], 'hex').decode()
    end_hex = codecs.encode(content_bytes[m.end()-2:m.end()+4], 'hex').decode()
    print(f"Match at {m.start()}: hex around match={start_hex}...{content[m.start():m.start()+30]}... hex after={end_hex}")

# Check all lines with single-quoted JOptionPane args
for i, line in enumerate(content.split('\n')):
    if "showMessageDialog" in line and "'" in line:
        # Show the raw bytes of that line segment around the quote
        q_idx = line.index("'Windows")
        start_byte = content_bytes[content.find(line):] + content_bytes[:0]  # this is wrong; let me use find
        idx2 = content.find(line) + q_idx
        print(f"\nLine {i+1} Windows: bytes around quote: {[hex(b) for b in content_bytes[idx2-5:idx2+3]]}")
