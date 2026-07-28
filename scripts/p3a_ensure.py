import os, sys

SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

print('Read %d lines from current file' % len(L))

def method_end_idx(start):
    depth = 0
    for i in range(start, len(L)):
        line = L[i]
        for ch in line:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    raise Exception('No match')

# Current positions (0-indexed, from earlier analysis)  
lis_start = -1; loc_start = -1
for i in range(len(L)):
    s = L[i].strip()
    if 'private void createListenerPane()' in s: lis_start = i
    elif 'private void createLocationsPane()' in s and loc_start == -1: loc_start = i

lis_end = method_end_idx(lis_start)  
loc_end = method_end_idx(loc_start)

print('createListenerPane(): lines %d-%d (%d)' % (lis_start+1, lis_end, lis_end-lis_start))
print('createLocationsPane(): lines %d-%d (%d)' % (loc_start+1, loc_end, loc_end-loc_start))
