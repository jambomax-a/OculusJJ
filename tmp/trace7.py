path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

import re

def strip(text):
    out = []; i = 0; n = len(text)
    while i < n:
        c = text[i]
        if c == '/' and i+1 < n and text[i+1] == '/':
            end = text.find('\n', i+2); 
            if end == -1: out.append(' ' * (n-i)); break
            out.append(' ' * (end - i)); i = end + 1
        elif c == '/' and i+1 < n and text[i+1] == '*':
            j = text.find('*/', i+2); 
            if j == -1: out.append(' ' * (n-i)); break
            out.append(' ' * (j+2-i)); i = j+2
        elif c in ('"', "'"):
            q = c; k = i+1
            while k < n and text[k] != q: 
                if text[k]=='\\': k+=1
                k += 1
            out.append(' ' * (min(k,n)-i)); i = min(k,n)+1
        else:
            out.append(c); i += 1
    return ''.join(out)

lines_arr = content.split('\n')

# Find the exact spot where depth goes permanently +1 from line 724 level  
# Track brace count at indent-level relative to "addMouseListener" call start (line 724)
start_line_idx = 723  # 0-indexed, the addMouseListener line

# Collect all lines from 724 onward as part of this block  
bal = 0
for i in range(start_line_idx, len(lines_arr)):
    stripped = strip(lines_arr[i])
    opens = stripped.count('{')
    closes = stripped.count('}')
    old_bal = bal
    bal += (opens - closes)
    
    # Track first time depth drops to 0 again (meaning we found matching close)
    if old_bal == 0 and bal != 0:
        pass
    elif old_bal != 0 and bal == 0:
        print(f"Depth returned to 0 at line {i+1}: {lines_arr[i].rstrip()[:80]}")
        # Check next line - is it still balanced?
        break

if bal > 0:
    print(f"\nAt the end of file, depth from line 724 region is +{bal}")
else:
    print("\nDepth returns to 0 properly somewhere!")

# Now let me find the EXACT location of the issue by tracking depth at each indent level  
# The mousePressed method should start with a certain depth and match its closing brace
start_line_idx_2 = 723  # 0-indexed for addMouseListener line
depth_at_mouse_adapter_open = 1

stack_check = []
for i in range(start_line_idx, len(lines_arr)):
    stripped = strip(lines_arr[i])
    
    depth_now = depth_at_mouse_adapter_open + sum(1 if c == '{' else -1 if c == '}' and depth_at_mouse_adapter_open > 0 else 0 for c in stripped) 
    # Simpler: just use running count  
    pass

# Let me try a different approach - track braces per-line ignoring strings  
for i in range(start_line_idx, min(len(lines_arr), start_line_idx + 35)):
    stripped = strip(lines_arr[i])
    opens = stripped.count('{')
    closes = stripped.count('}')
    print(f"Line {i+1}: net={opens-closes} | O={opens}, C={closes}")

# Now find depth at every line  
depths_from_724 = []
bal = 0
for i in range(start_line_idx, len(lines_arr)):
    stripped = strip(lines_arr[i])
    opens = stripped.count('{')
    closes = stripped.count('}')
    bal += (opens - closes)
    depths_from_724.append(bal)

# Find where this should return to 0  
return_to_zero_at = None
for i, d in enumerate(depths_from_724):
    if d == 0: 
        return_to_zero_at = start_line_idx + i
        print(f"\nDepth returns to 0 at line {i+start_line_idx+1}: '{lines_arr[i+start_line_idx].strip()[:100]}'")
        break

if return_to_zero_at is None:
    print(f"Depth NEVER returns to 0 from line 724 onward!")

# Check what the file structure looks like around where depth should end
print("\n=== Around proposed close ===")
if return_to_zero_at:
    for j in range(max(0, return_to_zero_at - 3), min(len(lines_arr), return_to_zero_at + 5)):
        marker = " <<<" if (j+1) == return_to_zero_at else ""
        print(f"Line {j+1}: {lines_arr[j].rstrip()[:140]}{marker}")

