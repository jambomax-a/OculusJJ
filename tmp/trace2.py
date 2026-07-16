path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Proper brace balance that handles strings and comments  
result = []
i = 0
while i < len(content):
    c = content[i]
    if c == '/' and i+1 < len(content) and content[i+1] == '/':
        end = content.find('\n', i)
        if end == -1: break
        result.append(('lc', i, 0))
        i = end + 1
    elif c == '/' and i+1 < len(content) and content[i+1] == '*':
        end = content.find('*/', i+2)
        if end == -1: break
        result.append(('bc', i, 0))
        i = end + 2
    elif c in ('"', "'"):
        q = c
        start = i
        j = i + 1
        while j < len(content):
            if content[j] == '\\': j += 2; continue
            if content[j] == q: break
            j += 1
        result.append(('s', start, 0))
        i = min(j+1, len(content))
    elif c == '{':
        result.append(('O', i, 1))
        i += 1
    elif c == '}':
        result.append(('C', i, -1 if depth > 0 else 0))
        i += 1
    else:
        i += 1

# Now find line numbers and track depth properly  
lines = content.split('\n')
def char_to_line(pos):
    return content[:pos].count('\n') + 1

print("=== Braces in lines 720-860 (with string/comment aware) ===")
for r in result:
    if r[0] not in ('O', 'C'): continue
    ln = char_to_line(r[1])
    if 720 <= ln <= 860:
        ch = content[r[1]]
        print(f"Line {ln}: brace='{ch}' at char {r[1]} | {lines[ln-1].rstrip()[:130] if ln <= len(lines) else ''}")

# Calculate depth per line
depth_per_line = {}
for r in result:
    if r[0] not in ('O', 'C'): continue
    ln = char_to_line(r[1])
    delta = r[2]  # 1 for O, -1 for C  
    if ln not in depth_per_line:
        depth_per_line[ln] = {'net': 0}
    depth_per_line[ln]['net'] += delta

# Find lines where net change is +N (more opens than closes) to identify unclosed  
for r in result:
    if r[0] == 'C' and r[2] == -1:
        ln = char_to_line(r[1])
        # Check if this close brace actually matches an open from earlier
        pass

# Let me use a simpler approach: track depth per line ignoring string content  
print("\n=== Simple depth tracking (line by line) ===")
lines_arr = content.split('\n')
depth = 0
for i, ln_text in enumerate(lines_arr):
    # Strip comments and strings quickly for approximate tracking
    text = ln_text.strip()
    if text.startswith('//') or text.startswith('*'): 
        continue
    
    opens = ln_text.count('{')
    closes = ln_text.count('}')
    
    old_depth = depth
    new_depth = depth + opens - closes
    # Only show lines that change depth for the region we care about  
    if 720 <= i+1 <= 865:
        print(f"Line {i+1}: d={old_depth}->{new_depth} (net={opens-closes}) | {ln_text[:130]}")
    depth = new_depth

print(f"\nFinal depth after line {len(lines_arr)}: {depth}")

