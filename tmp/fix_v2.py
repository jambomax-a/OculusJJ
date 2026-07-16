import re

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Direct string replacements for known problematic patterns
old_to_new = [
    ["JOptionPane.showMessageDialog(this, \"DB switch feature - to be implemented\", \'Select DB\', JOptionPane.INFORMATION_MESSAGE);",
     "JOptionPane.showMessageDialog(this, \"DB switch feature - to be implemented\", \"Select DB\", JOptionPane.INFORMATION_MESSAGE);"],
    ["JOptionPane.showMessageDialog(this, \"Window tiling - to be implemented\", \'Windows Tiling\', JOptionPane.INFORMATION_MESSAGE);",
     "JOptionPane.showMessageDialog(this, \"Window tiling - to be implemented\", \"Windows Tiling\", JOptionPane.INFORMATION_MESSAGE);"],
    ["JOptionPane.showMessageDialog(this, \"Series popup - to be implemented\", \'Series Popup\', JOptionPane.INFORMATION_MESSAGE);",
     "JOptionPane.showMessageDialog(this, \"Series popup - to be implemented\", \"Series Popup\", JOptionPane.INFORMATION_MESSAGE);"],
    ["JOptionPane.showMessageDialog(this, \"Patient panel - to be implemented\", \'Patient Panel\', JOptionPane.INFORMATION_MESSAGE);",
     "JOptionPane.showMessageDialog(this, \"Patient panel - to be implemented\", \"Patient Panel\", JOptionPane.INFORMATION_MESSAGE);"],
]

changes = 0
for old_s, new_s in old_to_new:
    if old_s in content:
        content = content.replace(old_s, new_s)
        changes += 1
        print(f"Fixed line containing: {old_s[:60]}...")
    else:
        # Show hex of first few chars to debug
        for pat in [\'Select DB\']:
            if pat in content:
                idx = content.index(pat)
                ctx = content[max(0,idx-20):idx+15]
                print(f"  Found {pat!r} context={repr(ctx)}")

print(f"Total changes made: {changes}")

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

# Final verification
remaining = []
for i, line in enumerate(content.split('\n')):
    if "showMessageDialog" in line and "\'" in line:
        remaining.append(i+1)
if remaining:
    print(f"REMAINING ISSUES on lines: {remaining}")
else:
    print("VERIFIED OK")
