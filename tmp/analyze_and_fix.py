import re, sys

def strip(text):
    """Strip Java strings and comments."""
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

def find_unclosed_braces(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    lines_arr = content.split('\n')
    stack = []
    
    for li, ln in enumerate(lines_arr):
        stripped_line = strip(ln)
        
        for ci in range(len(stripped_line)):
            ch = stripped_line[ci]
            if ch == '{':
                stack.append(li+1)  # line number (1-indexed)
            elif ch == '}':
                if stack:
                    opened_at_line = stack.pop()  
                else:
                    print(f"Unmatched close '}}' at line {li + 1}")

    unmatched_opens = list(stack)
    
    # Find which one was opened but never closed by checking depth transitions
    running = 0
    for i in range(len(lines_arr)):
        stripped_line = strip(lines_arr[i])
        opens = stripped_line.count('{')
        closes = stripped_line.count('}')
        running += (opens - closes)
    
    return unmatched_opens

def fix_dcmview(filepath):
    """Fix the unclosed brace in DCMView.java around addMouseListener MouseAdapter."""
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # The issue: line 724 adds an anonymous MouseAdapter that is never closed with }); 
    # Lines 1030-1049 should be inside mousePressed method body but have wrong indentation
    # We need to add closing } for mousePressed and );) for addMouseListener before the wheel listener
    
    # Find where mousePressed should end - look for "startDragPoint = null" pattern 
    insert_point = None
    for i, ln in enumerate(lines):
        if 'startDragPoint = null;' in ln.strip():
            insert_point = i + 1  # line number (1-indexed)
            break
    
    if not insert_point:
        print("Could not find startDragPoint marker")
        return False
    
    # Look at lines around insertion point to understand current structure
    print(f"Found startDragPoint at line {insert_point}")
    
    # Check what's before and after  
    for i in range(max(0, insert_point-3), min(len(lines), insert_point+6)):
        marker = "" if (i+1) != insert_point else " <<< INSERT CLOSING BRACES HERE"
        print(f"  Line {i+1}: [{len(lines[i])}] {lines[i].rstrip()} {marker}")
    
    # Check next lines for addMouseWheelListener which follows mousePressed close  
    for i in range(insert_point, min(len(lines), insert_point+20)):
        print(f"  Line {i+1}: [{len(lines[i])}] {lines[i].rstrip()}")
    
    return True

# Run fixes
print("=== DCMView.java unclosed braces ===")
for fp in [r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java']:
    result = find_unclosed_braces(fp)
    if result:
        print(f"Unclosed opens at lines: {result}")

print("\n=== Attempting DCMView fix analysis ===")
fix_dcmview(r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java')
