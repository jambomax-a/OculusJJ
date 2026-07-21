// ===== PATCH 2c/2d/2e: Method replacements (run after patcher-part1) =====
const fs = require('fs');
const path = require('path');
const ROOT = 'C:\\\\Users\\\\jam11\\\\OculusJJ';

function read(p) { return fs.readFileSync(path.join(ROOT, p), 'utf8'); }
function write(p, c) { fs.writeFileSync(path.join(ROOT, p), c, 'utf8'); console.log('wrote: ' + p); }

function findMethodEnd(src, marker) {
  const start = src.indexOf(marker);
  if (start < 0) throw new Error('not found: ' + marker.substring(0,30));
  let depth = 1;
  for (let i = start + marker.length; i < src.length && depth > 0; i++) {
    if (src[i] === '{') depth++;
    else if (src[i] === '}') depth--;
  }
  return start + marker.length + depth;
}

function findMethodStart(src, marker) {
  const idx = src.indexOf(marker);
  if (idx < 0) throw new Error('start not found: ' + marker.substring(0,30));
  return idx;
}

// Replace createHotKeysPane
let pc = read('src/main/java/com/oculus/jj/ui/PreferencesController.java');
{
  const sIdx = findMethodStart(pc, 'private void createHotKeysPane() {');
  const eIdx = findMethodEnd(pc, 'private void createHotKeysPane() {');
  let after = eIdx; while (after < pc.length && pc[after] === '\n') after++;
  
  pc = pc.substring(0, sIdx) + newMeth + '\n' + pc.substring(after);
}

console.log('2c done: hotKeys replaced');

function writeMethodReplacements(pc, newHotKeys, cdpvMarker, helpersCode, viewersStartMarker, newViewers) {
  // This won't work - let me use a different approach entirely
}

// Actually, the issue was the template literals in part1.js contained backticks or other JS syntax. Let me write everything cleanly using arrays:
const HOTKEYS_METHOD = [
"private void createHotKeysPane() {",
"        JPanel pane = new JPanel(new BorderLayout());",
"",
"        // HOROS-20240407 Reference: Hot Key bindings with real data persistence",
"        JSectionBox hkBox = new JSectionBox(\"Keyboard Shortcuts\");",
"        pane.add(hkBox, BorderLayout.NORTH);",
"",
"        String[] cols = {\"Action\", \"Key Combination\"};",
"        DefaultTableModel model = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int row, int col) { return false; } };",
"        hkTable = new JTable(model);",
"        hkTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);",
"        hkTable.setRowHeight(24);",
"",
"        // Load defaults from AppPreferences JSON on init",
"        loadHotKeysFromPrefs();",
"",
"        JPanel tablePanel = new JPanel(new BorderLayout());",
"        tablePanel.add(new JScrollPane(hkTable), BorderLayout.CENTER);",
"        pane.add(tablePanel, BorderLayout.CENTER);",
"",
"        // HOROS: HotKeyArrayController add/edit/remove buttons",
"        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));",
"",
"        JButton editBtn = new JButton(\"Edit Selected\");",
"        editBtn.addActionListener(e -> {",
"            int r = hkTable.getSelectedRow();",
"            if (r >= 0) openKeyCaptureDialog(r, \"Edit Key Binding\", true);",
"        });",
"        bottomPanel.add(editBtn);",
"",
"        JButton removeBtn = new JButton(\"- Remove\");",
"        removeBtn.addActionListener(e -> {",
"            int r = hkTable.getSelectedRow();",
"            if (r >= 0 && JOptionPane.showConfirmDialog(PreferencesController.this, \"Remove this key binding?\", \"Confirm\", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {",
"                model.removeRow(r);",
"            }",
"        });",
"        bottomPanel.add(removeBtn);",
"",
"        JButton addBtn = new JButton(\"+ Add Key Binding\");",
"        addBtn.addActionListener(e -> openKeyCaptureDialog(-1, \"New Hotkey Binding\", false));",
"        bottomPanel.add(addBtn);",
"",
"        pane.add(bottomPanel, BorderLayout.SOUTH);",
"",
"        // Auto-save on edit (HOROS: HotKeyArrayController save flow)",
"        hkTable.getModel().addTableModelListener(e -> {",
"            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {",
"                saveHotKeysToPrefs();",
"            }",
"        });",
"",
"        contentPanel.add(pane, \"Hot Keys\");",
"    }"
].join('\n');

pc = read('src/main/java/com/oculus/jj/ui/PreferencesController.java');
const sIdx1 = findMethodStart(pc, 'private void createHotKeysPane() {');
const eIdx1 = findMethodEnd(pc, 'private void createHotKeysPane() {');
let after = eIdx1; while (after < pc.length && pc[after] === '\n') after++;
pc = pc.substring(0, sIdx1) + HOTKEYS_METHOD + '\n' + pc.substring(after);

console.log('2c done: hotKeys replaced');

// 2d. Add helpers before createCDpvPane  
const CDpvStartMarker = '    private void createCDpvPane() {';
const cdpvIdx = findMethodStart(pc, CDpvStartMarker);
let beforeEnd = cdpvIdx; while (beforeEnd > 0 && pc[beforeEnd - 1] === '\n') beforeEnd--;

const HELPERS = [
"",
"// Hot Key bindings persistence helpers",
"",
'    @SuppressWarnings("unchecked")',
"    private List<Object[]> getHotKeysList() {",
"        try {",
'            String json = AppPreferences.getHotkeyBindingsJSON();',
"            if (json == null || json.isEmpty()) return null;",
"            ObjectMapper mapper = new ObjectMapper();",
"            List<Object[]> result = new java.util.ArrayList<>();",
"            var arr = mapper.readValue(json, Object[].class);",
"            if (arr != null) {",
"                for (Object item : arr) {",
'                    if (item instanceof Object[]) result.add((Object[]) item);',
"                }",
"            }",
"            return result;",
"        } catch (Exception ex) {",
'            System.err.println("Failed to parse hotkey JSON: " + ex.getMessage());',
"            return null;",
"        }",
"    }",
"",
"    private void loadHotKeysFromPrefs() {",
"        DefaultTableModel model = (DefaultTableModel) hkTable.getModel();",
"        List<Object[]> list = getHotKeysList();",
"        if (list == null || list.isEmpty()) {",
"            Object[][] defaults = {",
'                {"Zoom to ROI","Ctrl+Shift+Click"},{"Toggle WL/WW Mode","W"},',
'                {"Pan Mode","P"},{"Select/Edit ROI","S"},{"Freehand Measure","M"},',
'                {"Rectangular ROI","R"},{"Oval ROI","O"},{"Angle Measurement","A"},',
'                {"Flip Horizontal","F"},{"Rotate 90 CW","Shift+Right Click"},',
'                {"Zoom In/Out","Shift+Scroll"},{"3D MIP Viewer","Ctrl+M"},',
'                {"3D Volume Rendering","Ctrl+V"},{"Curved MPR","Ctrl+Y"}',
"            };",
"            for (Object[] r : defaults) model.addRow(r);",
"        } else {",
"            int row = 0;",
"            while (model.getRowCount() > 0) model.removeRow(0);",
"            for (Object[] r : list) {",
"                if (r.length >= 2) model.addRow(new Object[]{r[0], r[1]});",
"            }",
"        }",
"    }",
"",
"    private void saveHotKeysToPrefs() {",
"        DefaultTableModel model = (DefaultTableModel) hkTable.getModel();",
"        List<Object[]> result = new java.util.ArrayList<>();",
"        for (int i = 0; i < model.getRowCount(); i++) {",
"            Object act = model.getValueAt(i, 0);",
"            Object key = model.getValueAt(i, 1);",
"            if (act != null && key != null) result.add(new Object[]{act, key});",
"        }",
"        try {",
"            ObjectMapper mapper = new ObjectMapper();",
"            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);",
"            AppPreferences.setHotkeyBindingsJSON(json);",
"        } catch (Exception ex) {",
'            System.err.println("Failed to save hotkeys: " + ex.getMessage());',
"        }",
"    }",
"",
"    // Key capture dialog (HOROS-20240407 Reference: HotKeyTextFieldCell via Swing KeyListener)",
"    private void openKeyCaptureDialog(int editRow, String title, boolean isEdit) {",
"        JDialog dialog = new JDialog(PreferencesController.this, title, true);",
"        dialog.setSize(380, 260);",
"        dialog.setLocationRelativeTo(PreferencesController.this);",
"        dialog.setLayout(new GridBagLayout());",
'        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.WEST; int rRow = 0;',
"",
"        JTextField actionField = new JTextField(15);",
'        JButton captureBtn = new JButton("Press keys to capture...");',
'        final String[] capturedKeyCombo = {""};',
"",
"        // HOROS: HotKeyTextFieldCell captures via Swing KeyListener (replacement for Carbon Event)",
"        actionField.addKeyListener(new java.awt.event.KeyAdapter() {",
"            @Override public void keyPressed(java.awt.event.KeyEvent e) {",
"                int mods = e.getModifiersEx();",
"                String result = javax.swing.KeyboardShortcuts.format(mods, e.getKeyCode());",
'                captureBtn.setText(result.isEmpty() ? "Listening..." : result);',
"                capturedKeyCombo[0] = result;",
"            }",
"        });",
"",
"        if (editRow >= 0 && editRow < hkTable.getRowCount()) {",
"            String act = (String) hkTable.getValueAt(editRow, 0);",
"            String key = (String) hkTable.getValueAt(editRow, 1);",
'            actionField.setText(act != null ? act : "");',
'            capturedKeyCombo[0] = key != null ? key : "";',
"        }",
"",
'        gbc.gridx = 0; gbc.gridy = rRow++; dialog.add(new JLabel("Action:"), gbc);',
'        gbc.gridx = 1; gbc.gridwidth = 2; dialog.add(actionField, gbc);',
"",
'        gbc.gridx = 0; gbc.gridy = ++rRow; gbc.gridwidth = 1;',
'        dialog.add(new JLabel("Key Combination (click button to capture):"), gbc);',
"        JPanel keyPanel = new JPanel();",
"        keyPanel.add(captureBtn);",
'        gbc.gridx = 1; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;',
"        dialog.add(keyPanel, gbc);",
"",
'        JButton saveBtn = new JButton("Save");',
"        saveBtn.addActionListener(ev -> {",
'            String action = actionField.getText().trim();',
'            if (action.isEmpty() || capturedKeyCombo[0].isEmpty()) return;',
"            DefaultTableModel model = (DefaultTableModel) hkTable.getModel();",
'            int row = (isEdit && editRow >= 0) ? editRow : model.getRowCount();',
"            model.setValueAt(action, row, 0);",
"        });",
'        gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.WEST;',
'        dialog.add(saveBtn, new GridBagConstraints(0, ++rRow, 1, 1, 0, 0, GridBagConstraints.EAST, 0, new Insets(8, 6, 6, 6), 0, 0));',
"",
'        JButton cancelBtn = new JButton("Cancel");',
"        cancelBtn.addActionListener(ev -> dialog.dispose());",
'        gbc.gridx = 1; gbc.gridy = ++rRow; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;',
"        dialog.add(cancelBtn, gbc);",
"",
"        dialog.setVisible(true);",
"    }",
""
].join('\n');

pc = pc.substring(0, beforeEnd) + HELPERS + pc.substring(beforeEnd);

console.log('2d done: helpers added');


// 2e. Replace createViewersPane  
const VIEW_START = 'private void createViewersPane() {';
{
  const sIdx = findMethodStart(pc, VIEW_START);
  const eIdx = findMethodEnd(pc, VIEW_START);
  let after = eIdx; while (after < pc.length && pc[after] === '\n') after++;

  const NEW_VIEWERS = [
    "private void createViewersPane() {",
    '        JPanel pane = new JPanel(new GridBagLayout());',
    "        GridBagConstraints gbc = new GridBagConstraints();",
    "        gbc.insets = new Insets(6, 6, 6, 6);",
    "        gbc.anchor = GridBagConstraints.WEST; int row = 0;",
    "",
    '        // HOROS-20240407 Reference: Default CLUT',
    '{"CT-Body","CT-Bone","CT-Lung","CT-Brain","Gray Scale","PET-Rainbow","PET-HotMetal"};',
    '        String[] cluts = {\"CT-Body\",\"CT-Bone\",\"CT-Lung\",\"CT-Brain\",\"Gray Scale\",\"PET-Rainbow\",\"PET-HotMetal\"};',
    '        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default CLUT:"), gbc);',
    "",
    "        JComboBox<String> cbCLUT = new JComboBox<>(cluts);",
"        String defaultClut = AppPreferences.getDefaultCLUT();",
"        if (defaultClut != null && !java.util.Arrays.asList(cluts).contains(defaultClut)) { cbCLUT.addItem(defaultClut); }",
"        int clutIdx = 0;",
"        for (int i = 0; i < cluts.length; i++) { if (cluts[i].equals(defaultClut)) { clutIdx = i; break; } }",
"        cbCLUT.setSelectedIndex(clutIdx);",
"        gbc.gridx = 1; pane.add(cbCLUT, gbc);",
"",
'        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default Opacity Table:"), gbc);',
'        JComboBox<String> cbOpacity = new JComboBox<>(new String[]{"Standard","PET Specific","NM Specific"});',
"        String defaultOpacity = AppPreferences.getDefaultOpacityTable();",
'        for (int i = 0; i < 3; i++) { if (new String[]{"Standard","PET Specific","NM Specific"}[i].equals(defaultOpacity)) { cbOpacity.setSelectedIndex(i); break; } }',
"        gbc.gridx = 1; pane.add(cbOpacity, gbc);",
"",
'        row++; gbc.gridy = ++row; JCheckBox chkSync = new JCheckBox("Synchronize series between studies");',
"        boolean syncDefault = AppPreferences.isSyncSeriesBetweenStudies();",
"        chkSync.setSelected(syncDefault);",
"        gbc.gridwidth = 2; pane.add(chkSync, gbc);",
"",
'        row++; gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;',
'        JSectionBox screenBox = new JSectionBox("Secondary Monitor");',
"        pane.add(screenBox);",
"",
"        JPanel screenPanel = new JPanel(new BorderLayout());",
'        JCheckBox chkSecondaryEnabled = new JCheckBox("Enable secondary monitor display");',
"        chkSecondaryEnabled.setSelected(AppPreferences.isSecondaryMonitorEnabled());",
"",
'        JComboBox<String> secondaryContentCombo = new JComboBox<>(new String[]{"None", "Series List", "Thumbnail"});',
"        String secType = AppPreferences.getSecondaryContentType();",
"        for (int i = 0; i < secondaryContentCombo.getItemCount(); i++) { if (secondaryContentCombo.getItemAt(i).equals(secType)) { secondaryContentCombo.setSelectedIndex(i); break; } }",
"",
"        JPanel ctrlPanel = new JPanel();",
"        ctrlPanel.add(chkSecondaryEnabled);",
'        ctrlPanel.add(new JLabel("Display:"));',
"        ctrlPanel.add(secondaryContentCombo);",
"        screenPanel.add(ctrlPanel, BorderLayout.NORTH);",
"",
"        // HOROS-20240407 Reference: Monitor layout (stub - GraphicsEnvironment)",
"        JPanel monitorPreview = new JPanel(new BorderLayout());",
'        monitorPreview.setBorder(BorderFactory.createTitledBorder("Monitor Layout"));',
"",
'        JSectionBox windowBox = new JSectionBox("Window Size & Alignment");',
"        pane.add(windowBox);",
"",
"        JPanel windowPanel = new JPanel(new GridBagLayout());",
"        GridBagConstraints gbcw = new GridBagConstraints(); gbcw.insets = new Insets(4,4,4,4); gbcw.anchor = GridBagConstraints.WEST; int wRow = 0;",
'        JComboBox<String> maxSizeCombo = new JComboBox<>(new String[]{"Fit to Window", "Max 1024", "Max 512"});',
"        int savedIdx = AppPreferences.getMaxSizeIndex();",
"        if (savedIdx >= 0 && savedIdx < maxSizeCombo.getItemCount()) { maxSizeCombo.setSelectedIndex(savedIdx); }",
"",
'        JCheckBox chkAlignment = new JCheckBox("Align with orientation markers");',
'        gbcw.gridx = 0; gbcw.gridy = wRow++; windowPanel.add(new JLabel("Max Size:"), gbcw);',
"        gbcw.gridx = 1; windowPanel.add(maxSizeCombo, gbcw);",
"        gbcw.gridx = 0; gbcw.gridy = ++wRow; gbcw.gridwidth = 2;",
"        windowPanel.add(chkAlignment, gbcw);",
"",
"        pane.add(windowPanel);",
"",
'        contentPanel.add(new JScrollPane(pane), "Viewers");',
"    }"
  ].join('\n');

  pc = pc.substring(0, sIdx) + NEW_VIEWERS + '\n' + pc.substring(after);
}

console.log('2e done: viewers replaced');

write('src/main/java/com/oculus/jj/ui/PreferencesController.java', pc);
console.log('\n=== ALL PATCHES COMPLETE ===');