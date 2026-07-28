SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

for i in range(len(L)):
    if 'createGeneralPane' in L[i] or 'createDatabasePane' in L[i]:
        print("L%d:" % (i+1), repr(L[i][:100]))
        break  # just show first occurrence
