import os, sys
SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

print('Read %d lines from original' % len(L))

def method_end(start):
    depth = 0
    for i in range(start, len(L)):
        for ch in L[i]:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    raise Exception('No match')

# Exact boundaries (0-indexed) from earlier analysis
bnds = {
    'General': (396, method_end(396)),
    'Database': (546, method_end(546)),
    'CD/DVD': (847, method_end(847)),
    'Hanging': (961, method_end(961)),
    'HotKeys': (1273, method_end(1273)),
    'Viewers': (1540, method_end(1540)),
    '3DViewer': (1614, method_end(1614)),
    'PET': (1671, method_end(1671)),
    'Annotations': (1734, method_end(1734)),
    'DICOMPrint': (1785, method_end(1785)),
    'Listener': (1830, method_end(1830)),
    'Locations': (1947, method_end(1947)),
    'Routing': (2232, method_end(2232)),
    'WebServer': (2403, method_end(2403)),
    'OnDemand': (2505, method_end(2505))
}
print("Found %d pane definitions" % len(bnds))
for k,(s,e) in sorted(bnds.items()): print("  %-12s: lines %d-%d (%d)" % (k, s+1, e, e-s))
