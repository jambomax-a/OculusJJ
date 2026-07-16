import re
with open('C:/Users/jam11/OculusJJ/src/main/java/com/oculus/jj/ui/DCMView.java', encoding='utf-8') as f:
    lines = f.read().splitlines()

# Look at mousePressed area around line 1026 more carefully - the else block
# This is likely a duplicate of code from outside (mouseDragged?) that was pasted into mousePressed
print('=== Lines 1024-1055 in current file ===')
for i in range(1023, min(1055, len(lines))):
    print(str(i+1) + ': ' + lines[i][:90])

# Now check if these lines have undefined vars (pts from previous section)
# The mousePressed block is supposed to handle: tCross, tRepulsor, creation of new ROI
# Lines 1030-1045 are garbage: they reference pts/convertScreenToImage/e.getPoint() which 
# don't exist in mousePressed context (mousePressed uses 'e' for MouseEvent)