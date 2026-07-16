import re
with open('C:/Users/jam11/OculusJJ/src/main/java/com/oculus/jj/ui/DCMView.java', encoding='utf-8') as f:
    lines = f.read().splitlines()

def strip(t):
    t2 = re.sub(r'\"[^\"]*\"', '\"S\"', t)
    t2 = re.sub(r'/\*.*?\*/', '', t2, flags=re.DOTALL)
    t2 = re.sub(r'//.*$', '', t2, flags=re.MULTILINE)
    return t2

running = 0
for i, line in enumerate(lines):
    sline = strip(line)
    for c in sline:
        if c == '{': 
            running += 1
        elif c == '}': 
            running -= 1

print('Final brace balance: {}'.format(running))

# Now track where opens are not closed
running2 = 0
unclosed = []
for i, line in enumerate(lines):
    sline = strip(line)
    for c in sline:
        if c == '{': 
            running2 += 1
            unclosed.append((i+1))
        elif c == '}': 
            if running2 > 0 and unclosed:
                unclosed.pop()
            running2 -= 1

if unclosed:
    print('Unclosed braces at lines:')
    for l in unclosed:
        print('{}: {}'.format(l, lines[l-1][:80]))
else:
    print('All braces are properly closed.')