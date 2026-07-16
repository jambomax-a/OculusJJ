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

path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

lines_arr = content.split('\n')

# Track depth from start of addMouseListener (line 724)  
# The MouseAdapter anonymous class opens with one { at line 724
start_idx = 723  # 0-indexed for line 724

bal = 0  # relative to just inside the MouseAdapter opening
print("=== Brace balance within mousePressed (lines 724-1065) ===")
for i in range(start_idx, min(len(lines_arr), 1065)):
    stripped = strip(lines_arr[i])
    opens = stripped.count('{')
    closes = stripped.count('}')
    old_bal = bal
    bal += (opens - closes)
    
    # Show only lines that change depth  
    if opens > 0 or closes > 0:
        print(f"{i+1}: {old_bal}->{bal} (+{opens}, -{closes}) | {lines_arr[i].rstrip()[:130]}")

print(f"\nFinal bal at end of region (line 1065): {bal}")

# What's the issue? Check line by line if bal ever goes to -1
for i in range(start_idx, min(len(lines_arr), 1065)):
    stripped = strip(lines_arr[i])
    opens = stripped.count('{')
    closes = stripped.count('}')
    # recalculate 
    running = sum(strip(lines_arr[j]).count('{') - strip(lines_arr[j]).count('}') for j in range(start_idx, i+1))
    if running == 0 and i < len(lines_arr) - 1:
        print(f"\n* Depth returns to 0 at line {i+1}: '{lines_arr[i][:80]}'")
