import os, sys
SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')
print('Read %d lines' % len(L))

def method_end_idx(start):
    depth = 0
    for i in range(start, len(L)):
        line = L[i]
        for ch in line:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    return -1

def find_method_start(name):
    for i in range(len(L)):
        stripped = L[i].strip()
        if ('private void create' + name) in stripped or 'public void create' + name in stripped:
            return i
    raise Exception('Not found: ' + name)

print("=== Method boundaries ===")
g_names = ['createGeneralPane()', 'createDatabasePane()']
for name in g_names:
    start = find_method_start(name)
    end = method_end_idx(start)
    print("  %s: lines %d-%d (%d lines)" % (name.replace('create', ''), start+1, end, end-start))
