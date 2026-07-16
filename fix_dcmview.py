import re
bak = r'C:/Users/jam11/OculusJJ/src/main/java/com/oculus/jj/ui/DCMView.java.bak'
with open(bak, encoding='utf-8', errors='replace') as f:
    lines = f.read().splitlines()

# Remove garbage blocks (0-indexed)
clean_lines = []
for i in range(len(lines)):
    if 425 <= i <= 437:  # BAK 1-indexed: 426-438, skip
        continue
    if 499 <= i <= 516:  # BAK 1-indexed: 500-517, skip
        continue
    clean_lines.append(lines[i])

def strip(t):
    t2 = re.sub(r'"[^"]*"', '"S"', t)
    t2 = re.sub(r'/\*.*?\*/', '', t2, flags=re.DOTALL)
    t2 = re.sub(r'//.*$', '', t2, flags=re.MULTILINE)
    return t2

clean_text = '\n'.join(clean_lines)
ob = sum(1 for c in strip(clean_text) if c == '{')
cb = sum(1 for c in strip(clean_text) if c == '}')
print('Clean brace balance: {} - {} = {}'.format(ob, cb, ob-cb))

out = r'C:/Users/jam11/OculusJJ/src/main/java/com/oculus/jj/ui/DCMView.java'
with open(out, 'w', encoding='utf-8') as f:
    f.write(clean_text)
print('Wrote {} lines to {}'.format(len(clean_lines), out))