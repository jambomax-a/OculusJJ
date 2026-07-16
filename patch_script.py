import os

file_path = r'src/main/java/com/oculus/jj/ui/ToolbarPanel.java'
if os.path.exists(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    new_lines = []
    inserted = False
    target = 'addToolButton(toolbar, "btn_select", "tt.btn_select", () -> DCMView.currentTool = DCMView.ToolMode.tPlain);'
    addition = '        addToolButton(toolbar, "btn_translate", "tt.btn_translate", () -> DCMView.currentTool = DCMView.ToolMode.tTranslate);\n'

    for line in lines:
        new_lines.append(line)
        if target in line and not inserted:
            new_lines.append(addition)
            inserted = True

    with open(file_path, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    print('Patch applied successfully.')
else:
    print('File not found.')
