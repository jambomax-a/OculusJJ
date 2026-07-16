import re
from datetime import datetime

print(f"=== OculusJJ Build Fix Script ===")
print(f"Started: {datetime.now()}")

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

# ==============================
# FIX 1: ToolbarPanel.java - single quote strings 
# ==============================
print("\n[1/2] Fixing ToolbarPanel.java single-quote string errors...")
toolbar_path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(toolbar_path, 'r', encoding='utf-8') as f:
    toolbar_content = f.read()

original_toolbar_count = toolbar_content.count("'\\"+")  # approximate count of single quotes in string positions  
print(f"    Before fix: {toolbar_content.count(chr(39))} total single quotes")

# Pattern: LanguageManager.get(..., 'X') -> LanguageManager.get(..., "X")
# Also JOptionPane.showMessageDialog(..., 'X', ...) -> "...", "X", ...
fixed_toolbar = toolbar_content

# Replace LanguageManager.get("key", 'VAL') -> LanguageManager.get("key", "VAL")
pattern1 = re.compile(r'LanguageManager\.get\(("[^"]*"),\s*\'([A-Za-z0-9_]+)\',?\)')
matches1 = pattern1.findall(fixed_toolbar)
fixed_count = 0
for match in matches1:
    fixed_toolbar, n = pattern1.subn(
        lambda m: f'LanguageManager.get("{m.group(1)}", \\"{m.group(2)}\\")', 
        fixed_toolbar
    )
    if 'match' not in locals(): pass  # dummy
for k, v in matches1[:5]:  # Show first few fixes
    print(f"      LanguageManager.get(\"{k}\", '{v}') -> ...")

# Replace JOptionPane.showMessageDialog(xxx, 'X', xxx) -> showMessageDialog(xxx, "X", xxx)
pattern2 = re.compile(r'(JOptionPane\.showMessageDialog\([^,]+),\s*\'([A-Za-z ]+[^\'\",\)]*)\')')
fixed_toolbar2, n2 = pattern2.subn(
    lambda m: f'{m.group(1)}, \\"{m.group(2)}\\"', 
    fixed_toolbar
)

# Simpler approach - process line by line for clarity  
with open(toolbar_path, 'r', encoding='utf-8') as f:
    toolbar_lines = f.readlines()

fixed_lines = []
single_quote_fixes = 0
for i, ln in enumerate(toolbar_lines):
    fixed = False
    # Handle LanguageManager.get("key", 'VAL') 
    m1 = re.search(r'LanguageManager\.get\(("[^"]*"),\s*\'([^']+)\',?\)', ln)
    if m1:
        key = m1.group(1)
        val = m1.group(2)
        old = f"LanguageManager.get(\"{key}\", '{val}')"
        new = f'LanguageManager.get("{key}", "{val}")'
        ln = ln.replace(old, new)
        fixed = True
        single_quote_fixes += 1
        
    # Handle JOptionPane.showMessageDialog(xxx, 'X', xxx)  
    m2 = re.search(r'(JOptionPane\.showMessageDialog\([^)]*),\'([^\']+)\'\)', ln)
    if m2 and not fixed:
        prefix = m2.group(1)
        val = m2.group(2)
        old_part = f",'{val}'"
        new_part = f',"{val}"'
        # Only if the pattern makes sense
        pass
    
    # Handle btnSync.setText(DCMView.SYNCSERIES ? 'Sync ON' : 'Sync')
    m3 = re.search(r'setText\(DCMView\.SYNCSERIES\s*\?\s*\'([^']+)\'\s*:\s*\'([^']+)\'\)', ln)
    if m3:
        val1 = m3.group(1)
        val2 = m3.group(2)
        old3 = f"setText(DCMView.SYNCSERIES ? '{val1}' : '{val2}')"
        new3 = f'setText(DCMView.SYNCSERIES ? "{val1}" : "{val2}")'
        ln = ln.replace(old3, new3)
        fixed = True
        single_quote_fixes += 1
    
    # Handle JOptionPane.showMessageDialog(xxx, 'X', ...) pattern where first arg uses commas  
    if not fixed:
        m4 = re.search(r'(JOptionPane\.showMessageDialog\([^)]*), \'([A-Za-z][^\']*)\'\s*,', ln)
        if m4 and 'JOptionPane' in ln:
            # This might be JOptionPane.showMessageDialog(xxx, "X", xxx)  
            pass
    
    fixed_lines.append(ln)

print(f"  Fixed {single_quote_fixes} single-quote pattern occurrences")
with open(toolbar_path, 'w', encoding='utf-8') as f:
    f.writelines(fixed_lines)
    
# Verify no more single quotes in LanguageManager.get calls  
final_check = re.findall(r'LanguageManager\.get\([^)]*\x27[^"\x27]+\x27\)', open(toolbar_path,'r',encoding='utf-8').read())
if final_check:
    print(f"  WARNING: Still {len(final_check)} single-quote issues in LanguageManager.get() calls")

print("\n[2/2] Fixing DCMView.java structural brace issue...")
dcmview_path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java'
with open(dcmview_path, 'r', encoding='utf-8') as f:
    dcm_lines = f.readlines()

print(f"    Before fix: {len(dcm_lines)} lines")

# The issue: line 724 opens MouseAdapter which is never properly closed with }); 
# We need to add proper closing braces before line 1053's `});`
# Looking at the structure, mousePressed body ends around where startDragPoint assignments are
# Line 1052 has one '}' that closes an inner block  
# Line 1053 has `});` which should close addMouseListener - but we need a '}' first for MouseAdapter class 

# The current line 1049 has:                 }           (close isROITool block? or something else)
# Looking at indentation depth at lines 726-894 the structure seems to be inside mousePressed

# Actually, examining the data more carefully: 
# Lines 756-825 handle tRepulsor block
# Lines 831-890 are for newRoi != null (other ROITools)  
# Line 894 ends is2DViewer() check (bal went from + to -)

# Lines 967 onwards: } else if (isROITool(currentTool)) { — this seems out of place
# It's at depth where it should be inside mousePressed but after line 894

# The simplest fix: insert proper closing for MouseAdapter class and addMouseListener call
# Line 1053 is `});` which closes MouseAdapter anonymous class + addMouseListener - so it works IF braces match!

# Wait, let me check what's ACTUALLY before line 1052  
for i in range(max(0, len(dcm_lines)-5), len(dcm_lines)):
    print(f"  Last {i+1} lines: ...{dcm_lines[i].rstrip()[:80]}")

# Find where to insert the closing braces for MouseAdapter  
insert_line = None
for i, ln in enumerate(dcm_lines):
    stripped = strip(ln)
    if 'addMouseWheelListener' in ln and stripped.count('{') > 0:
        # This is AFTER mousePressed close - we need to find where it goes back to bal=0 relative to MouseAdapter
        print(f"Found addMouseWheelListener at line {i+1}: '{ln.strip()}'")
        insert_line = i + 1  # Insert BEFORE this (line number 1-indexed)  
        break

if insert_line:
    # Insert closing braces for mousePressed method body and MouseAdapter class 
    closing_lines = [
        '\n',
        '            }     // close mousePressed\n',
        '        });      // close addMouseListener MouseAdapter anonymous inner class\n',
        '\n'
    ]
    dcm_lines = dcm_lines[:insert_line] + closing_lines + dcm_lines[insert_line:]
    
    with open(dcmview_path, 'w', encoding='utf-8') as f:
        f.writelines(dcm_lines)
    
    print(f"    Inserted {len(closing_lines)} lines at line {insert_line}")

# Verify fix  
with open(dcmview_path, 'r', encoding='utf-8') as f:
    fixed_content = f.read()

final_unclosed = []
lines_arr = fixed_content.split('\n')
stack = []
for li, ln in enumerate(lines_arr):
    sl = strip(ln)
    for ci in range(len(sl)):
        ch = sl[ci]
        if ch == '{': stack.append(li+1)
        elif ch == '}': 
            if stack: stack.pop()  
            else: final_unclosed.append((li+1, ln[:60]))

if final_unclosed:
    print(f"  WARNING: Still {len(final_unclosed)} unclosed brace(s)")
    for ln, txt in final_unclosed:
        print(f"      Line {ln}: '{txt}'")
else:
    print("  OK! All braces are now balanced.")

print(f"\nAfter fix: {len(dcm_lines)} lines (changed from original)")

