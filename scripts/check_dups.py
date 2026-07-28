import os
SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

# Check for duplicate method names (any non-call occurrences)
for i in range(len(L)):
    s = L[i].strip()
    if 'private void createGeneralPane()' in s or 'private void createDatabasePane' in s or 'void createHanging' in s:
        print("L%d:" % (i+1), repr(s[:80]))
