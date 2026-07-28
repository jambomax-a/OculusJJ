import os

SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

print('Read %d lines from original PreferencesController.java' % len(L))

# Method definitions are at specific line numbers in the ORIGINAL file (1-indexed)
# Let me find them precisely for all 15 panes
def method_end(start):
    depth = 0
    for i in range(start, len(L)):
        for ch in L[i]:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    raise Exception('No match')

def find_def(name):
    # Find method DEFINITION (has 'private void' or 'public static' before it)
    for i in range(len(L)):
        if name in L[i]:
            # Verify this is a definition by checking nearby context  
            prev = L[i-1].strip() if i > 0 else ''
            curr = L[i].strip()
            # A method def would have 'private void' or similar before the name  
            if any(w in curr.lower().replace('_', '') for w in ['privatemethod','privatevoid']): 
                return i
    # Fallback: just find first occurrence with parentheses
    raise Exception('Not found: ' + name)

# Search all 15 pane names as DEFINITIONS (look for "createXxxPane()" preceded by access modifier)  
pane_names = [
    ('General', 'createGeneralPane'),
    ('Database', 'createDatabasePane'),
    ('CD/DVD Burning', 'createCDpvPane'), 
    ('Hanging Protocols', 'createHangingProtocolsPane'),
    ('Hot Keys', 'createHotKeysPane'),
    ('Viewers', 'createViewersPane'),
    ('3D Viewer', 'create3DPane'),
    ('PET/SUV', 'createPETPane'),
    ('Annotations', 'createAnnotationsPane'),
    ('DICOM Print', 'createDICOMPrintPane'),
    ('Listener', 'createListenerPane'),
    ('Locations', 'createLocationsPane'),
    ('Routing', 'createRoutingPane'),
    ('Web Server', 'createWebServerPane'),
    ('On-Demand PACS', 'createOnDemandPane')
]

defs = {}
for pname, cnam in pane_names:
    # Find all occurrences first  
    positions = []
    for i in range(len(L)):
        if cnam + '(' in L[i]:
            positions.append(i)
    
    # Now find which one is a method DEFINITION (not just a call)
    for pos in positions:
        line = L[pos].strip()
        # Method def pattern: [access modifier] void createXxxPane( ... ) { or ;
        if 'private' in line.lower() and 'void' in line.lower():
            defs[pname] = (pos, method_end(pos))
            print("%-15s: lines %d-%d (%d lines)" % (pname, pos+1, method_end(pos), method_end(pos) - pos))
            break

# Also check for the class structure after last pane definition
last_pane = max(defs.values(), key=lambda x: x[0])[1]  # end of last pane  
print("\nAfter all panes defined (line %d+):" % last_pane)
for i in range(last_pane - 3, min(last_pane + 35, len(L))):
    if L[i].strip() and not all(c.isspace() for c in L[i]):
        print("L%d:" % (i+1), repr(L[i][:80]))
