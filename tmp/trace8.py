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

# Track running depth - this should be the "current nesting level" of any open brace 
bal_before = []  # depth BEFORE processing line  
depths_from_start = {}  # depth per line (using 0-indexed line numbers)
running = 0
for i, ln in enumerate(lines_arr):
    stripped = strip(ln)
    running += stripped.count('{') - stripped.count('}')
    depths_from_start[i] = running

# Check final depth  
print(f"Final depth after all {len(lines_arr)} lines: {depths_from_start[len(lines_arr)-1]}")

# Find ALL positions where depth goes to +1, 0 again, then back to +1 (unclosed block)
positions_with_depth_plus_one = []
for i in range(len(lines_arr)):
    d_after = depths_from_start.get(i, 0)  
    if d_after == 0:
        # Check next line depth 
        for j in range(i+1, min(len(lines_arr), i + 50)):
            if depths_from_start[j] > 0:
                positions_with_depth_plus_one.append((i, j))
                break

# Find where depth goes to -1 (this would be impossible unless we had unmatched closes)  
neg_positions = []
for i in range(len(lines_arr)):
    d_after = depths_from_start.get(i, 0)
    if d_after < 0:
        neg_positions.append((i, d_after))

if neg_positions[:5]:
    print(f"\nUnexpected negative depth positions:")
    for i, d in neg_positions[:5]:
        print(f"  Line {i+1}: depth={d} | '{lines_arr[i][:100]}'")

# The issue is the overall final depth is +1. Find the first line where running goes +1 from 0 and stays there  
pos_of_imbalance = None
for i in range(len(lines_arr) - 1):  # exclude last (final = +1 by definition)
    if depths_from_start[i] == 0:
        for j in range(i+1, len(lines_arr)):
            d_j = depths_from_start[j]
            if d_j > 0 and all(depths_from_start[k] >= d_j - 1 for k in range(j, min(len(lines_arr), j + 50))):  
                pos_of_imbalance = (i, j)
                break

# Simpler approach: find where depth first goes to +1 and never returns  
for i in range(27, len(lines_arr)):  # skip class declaration line 27 which is expected +1
    if depths_from_start.get(i-1, 0) == 0 and depths_from_start[i] > 0:
        print(f"Depth goes to positive at line {i+1}: '{lines_arr[i][:130]}'")
        # Check if it ever returns to 0 after this  
        returned_to_zero = False
        for k in range(i, len(lines_arr)):
            if depths_from_start[k] == 0: 
                returned_to_zero = True; break
        print(f"  Returns to 0 later? {returned_to_zero}")

# Also check the very last line depth  
print(f"\nDepth at file end (last line): {depths_from_start.get(len(lines_arr)-1, 'N/A')}")
