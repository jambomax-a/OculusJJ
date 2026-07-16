import re

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Show current state of those lines 
for i, line in enumerate(content.split('\n')):
    if "showMessageDialog" in line and "'" in line:
        print(f"BEFORE FIX LINE {i+1}: {line}")

# The pattern is: showMessageDialog(this, "msg", 'title', ENUM)
# Fix by replacing the single-quoted middle argument  
fixed = re.sub(
    r"""showMessageDialog\(this,\s*"[^"]+",\s*'([^']+)',\s*JOptionPane""",
    lambda m: f'showMessageDialog(this, "{m.group(0)[len("showMessageDialog(this"):-1]}"{m.group(1)}", JOptionPane', middle=8),
    content
)

# Simpler approach - just replace known patterns directly
replacements_map = [
    (r"""JOptionPane.showMessageDialog(this, "[^"]+", 'Select DB',"JOptionP""",
     r'JOptionPane.showMessageDialog(this, "...", "Select DB","JOptionP'),
]
# Even simpler: direct string replacement with correct context  
content = content.replace(
    """JOptionPane.showMessageDialog(this, "DB switch feature - to be implemented", 'Select DB', JOptionPane.INFORMATION_MESSAGE);""",
    """JOptionPane.showMessageDialog(this, "DB switch feature - to be implemented", "Select DB", JOptionPane.INFORMATION_MESSAGE);"""
)
content = content.replace(
    """JOptionPane.showMessageDialog(this, "Window tiling - to be implemented", 'Windows Tiling', JOptionPane.INFORMATION_MESSAGE);""",
    """JOptionPane.showMessageDialog(this, "Window tiling - to be implemented", "Windows Tiling", JOptionPane.INFORMATION_MESSAGE);"""
)
content = content.replace(
    """JOptionPane.showMessageDialog(this, "Series popup - to be implemented", 'Series Popup', JOptionPane.INFORMATION_MESSAGE);""",
    """JOptionPane.showMessageDialog(this, "Series popup - to be implemented", "Series Popup", JOptionPane.INFORMATION_MESSAGE);"""
)
content = content.replace(
    """JOptionPane.showMessageDialog(this, "Patient panel - to be implemented", 'Patient Panel', JOptionPane.INFORMATION_MESSAGE);""",
    """JOptionPane.showMessageDialog(this, "Patient panel - to be implemented", "Patient Panel", JOptionPane.INFORMATION_MESSAGE);"""
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

# Verify
errors = 0
for i, line in enumerate(content.split('\n')):
    if "showMessageDialog" in line and "'" in line:
        print(f"AFTER FIX ISSUE LINE {i+1}: {line}")
        errors += 1
if errors == 0:
    print("VERIFIED OK - no single-quote issues remain")
