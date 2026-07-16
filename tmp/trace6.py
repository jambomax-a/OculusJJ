path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

import re

def strip(text):
    """Remove strings and comments from Java source for brace counting."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == '/' and i+1 < n and text[i+1] == '/':
            end = text.find('\n', i+2)
            if end == -1: 
                out.append(' ' * (n - i)); break
            out.append(' ' * (end - i))
            i = end + 1
        elif c == '/' and i+1 < n and text[i+1] == '*':
            j = text.find('*/', i+2)
            if j == -1: 
                out.append(' ' * (n - i)); break
            out.append(' ' * (j + 2 - i))
            i = j + 2
        elif c in ('"', "'"):
            q = c; k = i+1
            while k < n and text[k] != q:
                if text[k] == '\\': 
                    k += 1
                k += 1
            out.append(' ' * (min(k, n) - i))
            i = min(k, n) + 1
        elif c == '\n' or c == ' ':
            out.append(c)
            i += 1
        else:
            out.append(' ') if c != '{' and c != '}' else out.append(c)
            i += 1
    return ''.join(out)

lines_arr = content.split('\n')

# Stack-based brace matching - proper nesting order
stack = []  # stack of (line_number) where { was opened  
unmatched_opens = []
unmatched_closes = []

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
                unmatched_closes.append((li + 1, ln[:60]))

# Any remaining in stack are truly unclosed
for ln in stack:
    print(f"UNMATCHED '{{' at line {ln}:")
    for j in range(max(0, ln-3), min(len(lines_arr), ln+2)):
        marker = " <<<" if (j+1) == ln else ""
        print(f"  Line {j+1}: {lines_arr[j].rstrip()[:140]}{marker}")

print(f"\nUnclosed braces count: {len(stack)}")
print(f"Unmatched close braces count: {len(unmatched_closes)}")
