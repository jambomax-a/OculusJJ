path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Full trace from top to find where balance goes to +1 and stays there
bal = 0
first_plus_one_at = None
for i in range(len(lines)):
    line = lines[i]
    opens = line.count('{')
    closes = line.count('}')
    new_bal = bal + opens - closes
    if new_bal > bal:
        pass
    elif opens == 0 and closes > 0:
        pass
    
    # Track when bal first becomes positive  
    if first_plus_one_at is None and new_bal > 0 and (opens > 0 or closes > 0):
        first_plus_one_at = i + 1
        
    bal = new_bal

print(f"First time balance became +1: line {first_plus_one_at}")
print(f"\nFinal overall balance: {bal}")
print(f"\n=== Checking structure around mousePressed (724) ===")
# Find where the issue is by checking specific suspicious spots
for check_start in [806, 820, 830, 850, 900]:
    bal2 = 0
    for i in range(check_start-1, min(len(lines), check_start + 230)):
        line = lines[i]
        bal2 += line.count('{') - line.count('}')
    print(f"Bal at [{check_start}-end]: {bal2}")

# Also look for specific patterns that suggest unclosed braces
print("\n=== Looking for unmatched braces ===")
for i in range(700, min(len(lines), 1100)):
    line = lines[i]
    if 'if (curRoiList != null' in line:
        print(f"{i+1}: has '{line.rstrip()[:80]}'")
    if '} else {' in line and i > 820 and i < 970:
        # Check indentation to find which } belongs where  
        indent = len(line) - len(line.lstrip())
        print(f"{i+1} (indent={indent}): '{line.rstrip()[:100]}'")

