path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

import re
def clean_text(text):
    # Remove line comments first, then block comments (non-greedy)
    text2 = re.sub(r'//.*$', '', text, flags=re.MULTILINE)
    text3 = re.sub(r'/\*.*?\*/', ' ', text2, count=99, flags=re.DOTALL)
    # Remove string literals  
    text4 = re.sub(r'"(?:[^"\\]|\\.)*"', '" "', text3, count=999)
    return text4

lines_arr = content.split('\n')
stack = []  # list of line numbers where { was opened
matched = set()  # (open_line, close_line) pairs
unmatched_opens = []  # for unmatched opens

for i, ln in enumerate(lines_arr):
    cleaned = clean_text(ln)
    
    # Find all braces in this line (left to right order matters)
    pos = 0
    while pos < len(cleaned):
        ch = cleaned[pos]
        if ch == '{':
            stack.append((i+1, None))  # line_num, corresponding close_line
        elif ch == '}':
            if stack:
                open_ln = stack[-1][0]
                stack[-1] = (open_ln, i+1)  # mark as matched  
                pos += 1
                continue
            else:
                unmatched_opens.append((i+1, ln[:80]))
        elif ch in ('"', "'"):
            q = ch
            pos += 1
            while pos < len(cleaned):
                if cleaned[pos] == '\\':
                    pos += 2; continue
                if cleaned[pos] == q: break
                pos += 1
        else:
            pass
        pos += 1

# Any remaining in stack are unmatched opens
unmatched_count = 0
for item in stack:
    open_ln = item[0]
    close_ln = item[1] if len(item) > 1 and item[1] is not None else "never"
    print(f"UNMATCHED OPEN at line {open_ln}: (depth={lines_arr[open_ln-1].find('{')}) | {lines_arr[open_ln-1][:130]}")
    unmatched_count += 1

print(f"\nTotal unmatched open braces: {unmatched_count}")
for ln, txt in unmatched_opens[:5]:
    print(f"UNMATCHED CLOSE at line {ln}: {txt}")

