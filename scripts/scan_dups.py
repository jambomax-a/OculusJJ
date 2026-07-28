import os

SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

# Find all occurrences of "createGeneralPane" or "createDatabasePane"  
for i in range(len(L)):
    if 'createGeneralPane' in L[i] or 'createDatabasePane' in L[i]:
        print("L%d:" % (i+1), repr(L[i].strip()[:80]))

print('---')
# Show lines 516-520  
for i in range(514, min(522, len(L))):
    print("L%d: |%s|" % (i+1, L[i][:70]))

print('---DATABASE---')
for i in range(len(L)):
    if 'createDatabasePane' in L[i]:
        print("L%d:" % (i+1), repr(L[i].strip()[:80]))
        
# Show lines 745-752  
for i in range(743, min(755, len(L))):
    print("L%d: |%s|" % (i+1, L[i][:70]))
