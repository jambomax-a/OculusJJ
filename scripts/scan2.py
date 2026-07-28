SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

# Show what's between createGeneralPane end and next method  
print("=== Between line 517 (new gen start) and 668 ===")
for i in range(515, min(670, len(L))):
    if not L[i].strip() or 'private void' in L[i] or i == 666:
        print("L%d:" % (i+1), repr(L[i][:80]))

print("---")
# Show Database area too  
for i in range(665, min(760, len(L))):
    if not L[i].strip() or 'private void' in L[i] or i == 743:
        print("L%d:" % (i+1), repr(L[i][:80]))
        
print("---")
# Find the next method after Database  
print("Looking for next method after line 668...")
for i in range(750, len(L)):
    if L[i].strip().startswith('private void'):
        print("L%d:" % (i+1), repr(L[i][:80]))
        break
