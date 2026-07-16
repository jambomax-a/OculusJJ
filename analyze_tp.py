with open('src/main/java/com/oculus/jj/ui/ToolbarPanel.java', 'r', encoding='utf-8') as f: content = f.read()

# The file is a complete mess due to previous broken replacements.
# I will reconstruct it by keeping the start and implementing clean versions of the missing methods.

# Find where initializeUI ends
import re
match = re.search(r'        toolbar\.addSeparator\(\);', content) # Common separator near the end of UI setup

# Since it is too broken, let's look at what we REALLY need in ToolbarPanel:
# 1. Basic structure (package, imports, class decl)
# 2. initializeUI method with correct tool buttons
# 3. Help methods like toggleFusionMode etc.

# I will use a safer approach: read the file and strictly fix the syntactical debris at the end.
lines = content.splitlines()
clean_lines = []
inside_garbage = False

for line in lines:
    if '}}}' in line or '}}>' in line: # Heuristic for broken bits
        continue
    # Stop once we see the start of our appended garbage methods
    if 'private void toggleOrientationWidget() { }' in line:
        break
    clean_lines.append(line)

final_content = '\n'.join(clean_lines).strip()

# Ensure it ends with a closing brace for the class, and add clean method stubs
if not final_content.endswith('}'):
     # This is tricky because we might be inside a method. 
     # Let's try to find the last '}' that likely closes the class or a major method.
     pass

# Actually, let's just rebuild the core of ToolbarPanel based on what it should be.
# I have the imports and basic structure from previous outputs.

