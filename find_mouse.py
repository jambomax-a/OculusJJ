import re
bak_path = 'C:/Users/jam11/OculusJJ/src/main/java/com/oculus/jj/ui/DCMView.java.bak'
with open(bak_path, encoding='utf-8') as f:
    bak = f.read().splitlines()

def strip(t):
    t2 = re.sub(r'" + '"' + r"[^" + '"' + "]*" + '"' + r'", "' + '"' + 'S' + '"', t)
    t2 = re.sub(r"/\*.*?\*/", "", t2, flags=re.DOTALL)
    t2 = re.sub(r'//.*$', '', t2, flags=re.MULTILINE)
    return t2

# Find where mousePressed (inside addMouseListener) closes - 
# The MouseAdapter anonymous class starts with { after 'new MouseAdapter()' at line 756 in BAK
# Then the @Override + mousePressed { is another level. 
# So when we see )} followed by context that makes sense...

# Simple approach: find where addMouseListener closes - it's an anonymous class inside a method call
running = 0
mouse_adapter_open_line = None
for i, line in enumerate(bak):
    sline = strip(line)
    for c in sline:
        if c == '{': 
            running += 1
        elif c == '}': 
            running -= 1
    # Find addMouseListener call
    if 'addMouseListener' in line and i >= 750 and i < 800:
        mouse_adapter_open_line = i + 2  # The { is on this line
        print('Found addMouseListener at line {}'.format(i+1))

# Now count from that { to find where it closes
# We need to go through lines after the open and find )} pattern