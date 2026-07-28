import os
SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

def method_end_idx(start):
    depth = 0
    for i in range(start, len(L)):
        line = L[i]
        for ch in line:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    raise Exception('No match')

pane_names = [
    ('General', 'createGeneralPane'),
    ('Database', 'createDatabasePane'),
    ('CD/DVD', 'createCDpvPane'),
    ('Hanging', 'createHangingProtocolsPane'),
    ('HotKeys', 'createHotKeysPane'),
    ('Viewers', 'createViewersPane'),
    ('3DViewer', 'create3DPane'),
    ('PET', 'createPETPane'),
    ('Annotations', 'createAnnotationsPane'),
    ('DICOMPrint', 'createDICOMPrintPane'),
    ('Listener', 'createListenerPane'),
    ('Locations', 'createLocationsPane'),
    ('Routing', 'createRoutingPane'),
    ('WebServer', 'createWebServerPane'),
    ('OnDemand', 'createOnDemandPane')
]

for pname, cnam in pane_names:
    for i in range(len(L)):
        if 'private void ' + cnam + '(' in L[i]:
            start = i
            end = method_end_idx(start)
            print('%-12s: lines %d-%d (%d)' % (pname, start+1, end, end-start))
            break

print('Total:', len(L), 'lines')
