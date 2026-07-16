path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

import re

# Simple but effective: strip strings (handle escapes), line comments, block comments  
def strip(text):
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == '/' and i+1 < n and text[i+1] == '/':
            end = text.find('\n', i+2)
            if end == -1: return ''.join(out); break
            out.append(' ' * (end - i)); i = end + 1
        elif c == '/' and i+1 < n and text[i+1] == '*':
            j = text.find('*/', i+2)
            if j == -1: return ''.join(out); break
            out.append(' ' * (j + 2 - i)); i = j + 2
        elif c in ('"', "'"):
            q = c; k = i+1
            while k < n and text[k] != q:
                if text[k] == '\\': k += 1
                k += 1
            out.append(' ' * (min(k, n) - i))
            i = min(k, n) + 1
        else:
            out.append(c); i += 1
    return ''.join(out)

lines_arr = content.split('\n')

# Stack-based brace matching with depth tracking  
stack = []  # list of (line_num, char_index_in_line where { starts)
unmatched_closes = []

depth_at_line_open = {}

for li, ln in enumerate(lines_arr):
    stripped = strip(ln)
    
    for ci, ch in enumerate(stripped):
        if ch == '{':
            stack.append((li+1))  # line where { was found  
        elif ch == '}':
            if stack:
                opened_at_line = stack.pop()
                depth_at_line_open[opened_at_line] = 'matched'
            else:
                unmatched_closes.append(li + 1)

# Check what's left in stack (unmatched opens) - these are the real issue  
unclosed_lines = list(stack)
print(f"Unclosed open braces at lines: {unclosed_lines}")

if unclosed_lines:
    for ln in unclosed_lines[-5:]:
        print(f"  Unclosed '{{' at line {ln}: '{lines_arr[ln-1][:140]}'")

# Also find first unmatched close if any
if unmatched_closes[:3]:
    print(f"\nFirst few unmatched '}}': lines {unmatched_closes[:3]}")
