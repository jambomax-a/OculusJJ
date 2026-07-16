import re, sys
bak = 'C:/Users/jam11/OculusJJ/src/main/java/com/oculus/jj/ui/DCMView.java.bak'
with open(bak, encoding='utf-8', errors='replace') as f:
    text = f.read()

def strip(text):
    s = re.sub(r'"[^"]*"', '"S"', text)
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.DOTALL)
    s = re.sub(r'//.*$', '', s, flags=re.MULTILINE)
    return s

opens = sum(1 for c in strip(text) if c == '{')
closes = sum(1 for c in strip(text) if c == '}')
print('BAK brace balance: {} - {} = {}'.format(opens, closes, opens-closes))

running = 0
for i, line in enumerate(text.split('\n')):
    sline = re.sub(r'"[^"]*"', '"S"', line)
    sline = re.sub(r'/\*.*?\*/', '', sline, flags=re.DOTALL)
    for c in sline:
        if c == '{': running += 1
        elif c == '}': running -= 1
    if i >= 418 and i <= 450:
        print('{}: balance={} | {}'.format(i+1, running, line[:80]))