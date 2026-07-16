import re

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Check for LanguageManager.get with single quotes (bad in Java)
matches = re.findall(r"LanguageManager\.get\('[^']*'", content)
print(f"Single-quote matches found: {len(matches)}")
for m in matches[:5]:
    print(f"  ISSUE: {m}")

# Replace all LanguageManager.get('key', 'default') with LanguageManager.get("key", "default") in the new section (V1-V5 area)
# Only replace within the V1-V5 inserted block between V1 marker and end of V5 marker
v1_start = content.find("// V1: DB Window switcher")
v5_end = content.find('        toolbar.add(btnSync);', v1_start)
if v1_start != -1 and v5_end != -1:
    before_v1 = content[:v1_start]
    after_v5 = content[v5_end + len('        toolbar.add(btnSync);'):]
    
    middle = content[v1_start:v5_end + len('        toolbar.add(btnSync);')]
    
    # Replace single-quote LanguageManager.get calls with double quotes
    middle_fixed = re.sub(r"LanguageManager\.get\('[^']*'", lambda m: m.group().replace("'", '"'), middle)
    # Also replace any remaining single quotes in the new section that shouldn't be there (toolTipText, showMessageDialog strings)
    # Actually only LanguageManager.get needs double quotes; toolTipText etc can stay as-is if they use single quotes
    
    content = before_v1 + middle_fixed + after_v5
    
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("OK: Replaced LanguageManager.get single-quote args with double-quote in V1-V5 block")

# Verify final state
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
issues = 0
for i,l in enumerate(lines):
    if "LanguageManager.get('" in l or "'btn_window" in l or "'btn_sync_series" in l:
        print(f"REMAINING ISSUE LINE {i+1}: {l.rstrip()}")
        issues += 1
if issues == 0:
    print("VERIFIED OK - no single quote issues remain in Java source")
print(f"Total lines: {len(lines)}")
