import os, re
base = r'C:\Users\jam11\OculusJJ'
horos_src = os.path.join(base, 'horos-20240407', 'Horos')
content = open(os.path.join(horos_src, 'Sources', 'BrowserController.m'), 'r', encoding='utf-8', errors='ignore').read()
keys = {}
pat = re.compile(r"forKey:\s*@?\s*\"([A-Za-z_][A-Za-z0-9_]*)")
for m in pat.finditer(content):
    key = m.group(1)
    keys[key] = keys.get(key, 0) + 1
print('=== NSUserDefaults keys (BrowserController.m) ===')
sorted_keys = sorted(keys.items(), key=lambda x: -x[1])[:50]
for k, c in sorted_keys:
    print(f'  {k}: {c}')
