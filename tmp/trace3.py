path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\DCMView.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

lines_arr = content.split('\n')

# Strip comments and strings for accurate brace counting per line
import re
def clean_text(text):
    # Remove single-line comments
    text2 = re.sub(r'//.*$', '', text)
    # Remove block comments (simplified, assumes they don't span across lines in this file)
    text3 = re.sub(r'/\*.*?\*/', '', text2, flags=re.DOTALL)
    # Remove string literals (double quotes with escape handling - simplified)
    text4 = re.sub(r'"(?:[^"\\]|\\.)*"', '""', text3)
    # Single-quoted char literals we can't simply remove without affecting meaning
    return text4

# Calculate depth per line properly
depth = 0
line_depths = []
for i, ln in enumerate(lines_arr):
    cleaned = clean_text(ln)
    opens = cleaned.count('{')
    closes = cleaned.count('}')
    old_d = depth
    new_d = depth + opens - closes
    line_depths.append((i+1, old_d, new_d))
    if i < len(lines_arr) - 1:
        # Depth should be stable until next open/close
        pass
    depth = new_d

# Find where the file becomes permanently +1 (one extra open somewhere in earlier lines)
# Check: is there a line where depth goes from 0 to +1 and never comes back?
print("=== Full depth progression (showing first/last +/- transitions) ===")
prev_positive = False
first_pos = None
for ln, d_old, d_new in line_depths:
    if not prev_positive and d_new > 0:
        first_pos = ln
        print(f"Line {ln}: depth becomes +1 (first time): '{lines_arr[ln-1][:120]}'")
    elif prev_positive and d_new == 0:
        print(f"Line {ln}: depth returns to 0 from positive region ending at line {ln-1}")
    prev_positive = (d_new > 0 or d_old > 0)

print(f"\nFinal depth of file: {depth}")

# Now check if final depth is permanently +1 by verifying last line
if depth == 1:
    print("\nThe +1 imbalance persists to the very end.")
    # Find where it started (first transition from 0->+1 that wasn't balanced)
    bal = 0
    for ln, d_old, d_new in line_depths:
        if d_old == 0 and d_new > 0 and not prev_positive_after_zero:
            print(f"First time going +1 at line {ln}: {lines_arr[ln-1][:120]}")
            break

# Let me try a different approach: find unmatched open braces by proper pairing
print("\n=== Finding unclosed brace via stack ===")
stack = []  # (line_num, depth_when_opened)
for i, ln in enumerate(lines_arr):
    cleaned = clean_text(ln)
    
    # Process opens first, then closes to pair them properly  
    for ch_pos, ch in enumerate(cleaned):
        if ch == '{':
            stack.append((i+1))  # line where this { was found
        elif ch == '}':
            if stack:
                opened_at = stack.pop()
            else:
                print(f"UNMATCHED CLOSE at line {i+1}: {ln[:80]}")

if stack:
    for ln in stack[-5:]:  # Show last few unmatched opens  
        print(f"UNCLOSED OPEN at line {ln}: {lines_arr[ln-1][:120]}")
else:
    print("All braces are matched!")

print(f"\nTotal unmatched open braces: {len(stack)}")

