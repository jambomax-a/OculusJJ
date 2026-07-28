SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

def method_end_idx(start):
    depth = 0
    for i in range(start, len(L)):
        line = L[i]
        # Skip string/char literals to avoid false brace counting
        in_str = False
        c1 = None
        for ch in line:
            if ch == "'" and not in_str:
                in_str = True; c1 = ch; continue
            elif in_str and ch == c1 and i > 0 and L[i-1] != '\\':
                # Actually simpler approach - just count braces outside of strings
                pass
            else:
                if not in_str:
                    if ch == '{': depth += 1
                    elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    return -1

# Find the ACTUAL method definitions (not the calls)  
defs = {}
for i in range(len(L)):
    s = L[i].strip()
    for mname in ['createGeneralPane()', 'createDatabasePane()']:
        if ('private void ' + mname) in s or ('public static' in s and mname in s):
            start = i
            end = method_end_idx(start)
            defs[mname.replace('()', '')] = (start, end)

print("Method definitions:")
for name, (s, e) in defs.items():
    print("  %s: lines %d-%d (%d lines)" % (name, s+1, e, e-s))
