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

// ===== PATCH 1: AppPreferences.java =====
console.log('=== Patch 1 ===');
let c = read('src/main/java/com/oculus/jj/config/AppPreferences.java');

const OL = 'public static final String KEY_SYNC_SERIES_BETWEEN_STUDIES = "viewers.syncSeriesBetweenStudies";';
if (c.indexOf(OL) < 0) { console.log('ERROR: marker not found'); process.exit(1); }
c = c.replace(OL, 
  OL + '\n' +
  '    public static final String KEY_VIEWERS_SECONDARY_MONITOR_ENABLED = "viewers.secondaryMonitor.enabled";\n' +
  '    public static final String KEY_VIEWERS_SECONDARY_CONTENT_TYPE = "viewers.secondaryMonitor.contentType";\n' +
  '    public static final String KEY_VIEWERS_MAX_SIZE_INDEX = "viewers.maxSizeIndex"');

const GM = 'public static String getOnDemandSourceListJSON()';
if (c.indexOf(GM) >= 0 && c.indexOf('\n    // Viewers secondary monitor') < 0) {
  c = c.replace(GM, 
'\n    // Viewers secondary monitor\n' +
'    public static boolean isSecondaryMonitorEnabled() { return prefs.getBoolean(KEY_VIEWERS_SECONDARY_MONITOR_ENABLED, false); }\n' +
'    public static void setSecondaryMonitorEnabled(boolean v) { prefs.putBoolean(KEY_VIEWERS_SECONDARY_MONITOR_ENABLED, v); }\n' +
'\n' +
'    public static String getSecondaryContentType() { return prefs.get(KEY_VIEWERS_SECONDARY_CONTENT_TYPE, "None"); }\n' +
'    public static void setSecondaryContentType(String t) { prefs.put(KEY_VIEWERS_SECONDARY_CONTENT_TYPE, t); }\n' +
'\n' +
'    public static int getMaxSizeIndex() { return prefs.getInt(KEY_VIEWERS_MAX_SIZE_INDEX, 0); }\n' +
'    public static void setMaxSizeIndex(int idx) { prefs.putInt(KEY_VIEWERS_MAX_SIZE_INDEX, idx); }\n\n' + GM);
}

write('src/main/java/com/oculus/jj/config/AppPreferences.java', c);


// ===== PATCH 2: PreferencesController.java =====  
console.log('\n=== Patch 2 ===');
let pc = read('src/main/java/com/oculus/jj/ui/PreferencesController.java');

const impM = 'import com.oculus.jj.model.DicomNode;';
if (pc.indexOf(impM) >= 0) {
  pc = pc.replace(impM, 
    'import com.fasterxml.jackson.core.type.TypeReference;\nimport com.fasterxml.jackson.databind.ObjectMapper;\n' + impM);
}

const CLS = 'public class PreferencesController extends JDialog {';
pc = pc.replace(CLS, '\n\n    // Field for hot key table shared with helper methods\n    private JTable hkTable;\n' + CLS);


// Replace createHotKeysPane  
{
  const sIdx = findMethodStart(pc, 'private void createHotKeysPane() {');
  const eIdx = findMethodEnd(pc, 'private void createHotKeysPane() {');
  let after = eIdx; while (after < pc.length && pc[after] === '\n') after++;
  
  const M = 
'private void createHotKeysPane() {\n' +
'        JPanel pane = new JPanel(new BorderLayout());\n' +
'\n' +
'        // HOROS-20240407 Reference: Hot Key bindings with real data persistence\n' +
'        JSectionBox hkBox = new JSectionBox("Keyboard Shortcuts");\n' +
'        pane.add(hkBox, BorderLayout.NORTH);\n' +
'\n' +
'        String[] cols = {"Action", "Key Combination"};\n' +
'        DefaultTableModel model = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int row, int col) { return false; } };\n' +
'        hkTable = new JTable(model);\n' +
'        hkTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);\n' +
'        hkTable.setRowHeight(24);\n' +
'\n' +
'        // Load defaults from AppPreferences JSON on init\n' +
'        loadHotKeysFromPrefs();\n' +
'\n' +
'        JPanel tablePanel = new JPanel(new BorderLayout());\n' +
'        tablePanel.add(new JScrollPane(hkTable), BorderLayout.CENTER);\n' +
'        pane.add(tablePanel, BorderLayout.CENTER);\n' +
'\n' +
'        // HOROS: HotKeyArrayController add/edit/remove buttons\n' +
'        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));\n' +
'\n' +
'        JButton editBtn = new JButton("Edit Selected");\n' +
'        editBtn.addActionListener(e -> {\n' +
'            int r = hkTable.getSelectedRow();\n' +
'            if (r >= 0) openKeyCaptureDialog(r, "Edit Key Binding", true);\n' +
'        });\n' +
'        bottomPanel.add(editBtn);\n' +
'\n' +
'        JButton removeBtn = new JButton("- Remove");\n' +
'        removeBtn.addActionListener(e -> {\n' +
'            int r = hkTable.getSelectedRow();\n' +
'            if (r >= 0 && JOptionPane.showConfirmDialog(PreferencesController.this, "Remove this key binding?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {\n' +
'                model.removeRow(r);\n' +
'            }\n' +
'        });\n' +
'        bottomPanel.add(removeBtn);\n' +
'\n' +
'        JButton addBtn = new JButton("+ Add Key Binding");\n' +
'        addBtn.addActionListener(e -> openKeyCaptureDialog(-1, "New Hotkey Binding", false));\n' +
'        bottomPanel.add(addBtn);\n' +
'\n' +
'        pane.add(bottomPanel, BorderLayout.SOUTH);\n' +
'\n' +
'        // Auto-save on edit (HOROS: HotKeyArrayController save flow)\n' +
'        hkTable.getModel().addTableModelListener(e -> {\n' +
'            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {\n' +
'                saveHotKeysToPrefs();\n' +
'            }\n' +
'        });\n' +
'\n' +
'        contentPanel.add(pane, "Hot Keys");\n' +
'    }';

  pc = pc.substring(0, sIdx) + M + '\n' + pc.substring(after);
}


// Add helpers before createCDpvPane  
{
  const cdpvStart = '    private void createCDpvPane() {';
  const cdpvIdx = findMethodStart(pc, cdpvStart);
  let beforeEnd = cdpvIdx; while (beforeEnd > 0 && pc[beforeEnd - 1] === '\n') beforeEnd--;

  const H = 
'\n// Hot Key bindings persistence helpers\n' +
'\n    @SuppressWarnings("unchecked")\n' +
'    private List<Object[]> getHotKeysList() {\n' +
'        try {\n' +
'            String json = AppPreferences.getHotkeyBindingsJSON();\n' +
'            if (json == null || json.isEmpty()) return null;\n' +
'            ObjectMapper mapper = new ObjectMapper();\n' +
'            List<Object[]> result = new java.util.ArrayList<>();\n' +
'            var arr = mapper.readValue(json, Object[].class);\n' +
'            if (arr != null) {\n' +
'                for (Object item : arr) {\n' +
'                    if (item instanceof Object[]) result.add((Object[]) item);\n' +
'                }\n' +
'            }\n' +
'            return result;\n' +
'        } catch (Exception ex) {\n' +
'            System.err.println("Failed to parse hotkey JSON: " + ex.getMessage());\n' +
'            return null;\n' +
'        }\n' +
'    }\n\n' +

'    private void loadHotKeysFromPrefs() {\n' +
'        DefaultTableModel model = (DefaultTableModel) hkTable.getModel();\n' +
'        List<Object[]> list = getHotKeysList();\n' +
'        if (list == null || list.isEmpty()) {\n' +
'            Object[][] defaults = {\n' +
'                {"Zoom to ROI","Ctrl+Shift+Click"},{"Toggle WL/WW Mode","W"},\n' +
'                {"Pan Mode","P"},{"Select/Edit ROI","S"},{"Freehand Measure","M"},\n' +
'                {"Rectangular ROI","R"},{"Oval ROI","O"},{"Angle Measurement","A"},\n' +
'                {"Flip Horizontal","F"},{"Rotate 90 CW","Shift+Right Click"},\n' +
'                {"Zoom In/Out","Shift+Scroll"},{"3D MIP Viewer","Ctrl+M"},\n' +
'                {"3D Volume Rendering","Ctrl+V"},{"Curved MPR","Ctrl+Y"}\n' +
'            };\n' +
'            for (Object[] r : defaults) model.addRow(r);\n' +
'        } else {\n' +
'            int row = 0;\n' +
'            while (model.getRowCount() > 0) model.removeRow(0);\n' +
'            for (Object[] r : list) {\n' +
'                if (r.length >= 2) model.addRow(new Object[]{r[0], r[1]});\n' +
'            }\n' +
'        }\n' +
'    }\n\n' +

'    private void saveHotKeysToPrefs() {\n' +
'        DefaultTableModel model = (DefaultTableModel) hkTable.getModel();\n' +
'        List<Object[]> result = new java.util.ArrayList<>();\n' +
'        for (int i = 0; i < model.getRowCount(); i++) {\n' +
'            Object act = model.getValueAt(i, 0);\n' +
'            Object key = model.getValueAt(i, 1);\n' +
'            if (act != null && key != null) result.add(new Object[]{act, key});\n' +
'        }\n' +
'        try {\n' +
'            ObjectMapper mapper = new ObjectMapper();\n' +
'            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);\n' +
'            AppPreferences.setHotkeyBindingsJSON(json);\n' +
'        } catch (Exception ex) {\n' +
'            System.err.println("Failed to save hotkeys: " + ex.getMessage());\n' +
'        }\n' +
'    }\n\n' +

'    // Key capture dialog (HOROS-20240407 Reference: HotKeyTextFieldCell via Swing KeyListener)\n' +
'    private void openKeyCaptureDialog(int editRow, String title, boolean isEdit) {\n' +
'        JDialog dialog = new JDialog(PreferencesController.this, title, true);\n' +
'        dialog.setSize(380, 260);\n' +
'        dialog.setLocationRelativeTo(PreferencesController.this);\n' +
'        dialog.setLayout(new GridBagLayout());\n' +
'        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.WEST; int rRow = 0;\n\n' +
'        JTextField actionField = new JTextField(15);\n' +
'        JButton captureBtn = new JButton("Press keys to capture...");\n' +
'        final String[] capturedKeyCombo = {""};\n\n' +

'        // HOROS: HotKeyTextFieldCell captures via Swing KeyListener (replacement for Carbon Event)\n' +
'        actionField.addKeyListener(new java.awt.event.KeyAdapter() {\n' +
'            @Override public void keyPressed(java.awt.event.KeyEvent e) {\n' +
'                int mods = e.getModifiersEx();\n' +
'                String result = javax.swing.KeyboardShortcuts.format(mods, e.getKeyCode());\n' +
'                captureBtn.setText(result.isEmpty() ? "Listening..." : result);\n' +
'                capturedKeyCombo[0] = result;\n' +
'            }\n' +
'        });\n\n' +

'        if (editRow >= 0 && editRow < hkTable.getRowCount()) {\n' +
'            String act = (String) hkTable.getValueAt(editRow, 0);\n' +
'            String key = (String) hkTable.getValueAt(editRow, 1);\n' +
'            actionField.setText(act != null ? act : "");\n' +
'            capturedKeyCombo[0] = key != null ? key : "";\n' +
'        }\n\n' +

'        gbc.gridx = 0; gbc.gridy = rRow++; dialog.add(new JLabel("Action:"), gbc);\n' +
'        gbc.gridx = 1; gbc.gridwidth = 2; dialog.add(actionField, gbc);\n\n' +

'        gbc.gridx = 0; gbc.gridy = ++rRow; gbc.gridwidth = 1;\n' +
'        dialog.add(new JLabel("Key Combination (click button to capture):"), gbc);\n' +
'        JPanel keyPanel = new JPanel();\n' +
'        keyPanel.add(captureBtn);\n' +
'        gbc.gridx = 1; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;\n' +
'        dialog.add(keyPanel, gbc);\n\n' +

'        JButton saveBtn = new JButton("Save");\n' +
'        saveBtn.addActionListener(ev -> {\n' +
'            String action = actionField.getText().trim();\n' +
'            if (action.isEmpty() || capturedKeyCombo[0].isEmpty()) return;\n' +
'            DefaultTableModel model = (DefaultTableModel) hkTable.getModel();\n' +
'            int row = (isEdit && editRow >= 0) ? editRow : model.getRowCount();\n' +
'            model.setValueAt(action, row, 0);\n' +
'        });\n' +
'        gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.WEST;\n' +
'        dialog.add(saveBtn, new GridBagConstraints(0, ++rRow, 1, 1, 0, 0, GridBagConstraints.EAST, 0, new Insets(8, 6, 6, 6), 0, 0));\n\n' +

'        JButton cancelBtn = new JButton("Cancel");\n' +
'        cancelBtn.addActionListener(ev -> dialog.dispose());\n' +
'        gbc.gridx = 1; gbc.gridy = ++rRow; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;\n' +
'        dialog.add(cancelBtn, gbc);\n\n' +

'        dialog.setVisible(true);\n' +
'    }';

  pc = pc.substring(0, beforeEnd) + H + pc.substring(beforeEnd);
}


// Replace createViewersPane  
{
  const sIdx = findMethodStart(pc, 'private void createViewersPane() {');
  const eIdx = findMethodEnd(pc, 'private void createViewersPane() {');
  let after = eIdx; while (after < pc.length && pc[after] === '\n') after++;

  const V = 
'private void createViewersPane() {\n' +
'        JPanel pane = new JPanel(new GridBagLayout());\n' +
'        GridBagConstraints gbc = new GridBagConstraints();\n' +
'        gbc.insets = new Insets(6, 6, 6, 6);\n' +
'        gbc.anchor = GridBagConstraints.WEST; int row = 0;\n\n' +

'        // HOROS-20240407 Reference: Default CLUT\n' +
'        String[] cluts = {"CT-Body","CT-Bone","CT-Lung","CT-Brain","Gray Scale","PET-Rainbow","PET-HotMetal"};\n' +
'        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default CLUT:"), gbc);\n\n' +

'        JComboBox<String> cbCLUT = new JComboBox<>(cluts);\n' +
'        String defaultClut = AppPreferences.getDefaultCLUT();\n' +
'        if (defaultClut != null && !java.util.Arrays.asList(cluts).contains(defaultClut)) { cbCLUT.addItem(defaultClut); }\n' +
'        int clutIdx = 0;\n' +
'        for (int i = 0; i < cluts.length; i++) { if (cluts[i].equals(defaultClut)) { clutIdx = i; break; } }\n' +
'        cbCLUT.setSelectedIndex(clutIdx);\n' +
'        gbc.gridx = 1; pane.add(cbCLUT, gbc);\n\n' +

'        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default Opacity Table:"), gbc);\n' +
'        JComboBox<String> cbOpacity = new JComboBox<>(new String[]{"Standard","PET Specific","NM Specific"});\n' +
'        String defaultOpacity = AppPreferences.getDefaultOpacityTable();\n' +
'        for (int i = 0; i < 3; i++) { if (new String[]{"Standard","PET Specific","NM Specific"}[i].equals(defaultOpacity)) { cbOpacity.setSelectedIndex(i); break; } }\n' +
'        gbc.gridx = 1; pane.add(cbOpacity, gbc);\n\n' +

'        row++; gbc.gridy = ++row; JCheckBox chkSync = new JCheckBox("Synchronize series between studies");\n' +
'        boolean syncDefault = AppPreferences.isSyncSeriesBetweenStudies();\n' +
'        chkSync.setSelected(syncDefault);\n' +
'        gbc.gridwidth = 2; pane.add(chkSync, gbc);\n\n' +

'        row++; gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;\n' +
'        JSectionBox screenBox = new JSectionBox("Secondary Monitor");\n' +
'        pane.add(screenBox);\n\n' +

'        JPanel screenPanel = new JPanel(new BorderLayout());\n' +
'        JCheckBox chkSecondaryEnabled = new JCheckBox("Enable secondary monitor display");\n' +
'        chkSecondaryEnabled.setSelected(AppPreferences.isSecondaryMonitorEnabled());\n\n' +

'        JComboBox<String> secondaryContentCombo = new JComboBox<>(new String[]{"None", "Series List", "Thumbnail"});\n' +
'        String secType = AppPreferences.getSecondaryContentType();\n' +
'        for (int i = 0; i < secondaryContentCombo.getItemCount(); i++) { if (secondaryContentCombo.getItemAt(i).equals(secType)) { secondaryContentCombo.setSelectedIndex(i); break; } }\n\n' +

'        JPanel ctrlPanel = new JPanel();\n' +
'        ctrlPanel.add(chkSecondaryEnabled);\n' +
'        ctrlPanel.add(new JLabel("Display:"));\n' +
'        ctrlPanel.add(secondaryContentCombo);\n' +
'        screenPanel.add(ctrlPanel, BorderLayout.NORTH);\n\n' +

'        // HOROS-20240407 Reference: Monitor layout (stub - GraphicsEnvironment)\n' +
'        JPanel monitorPreview = new JPanel(new BorderLayout());\n' +
'        monitorPreview.setBorder(BorderFactory.createTitledBorder("Monitor Layout"));\n\n' +

'        JSectionBox windowBox = new JSectionBox("Window Size & Alignment");\n' +
'        pane.add(windowBox);\n\n' +

'        JPanel windowPanel = new JPanel(new GridBagLayout());\n' +
'        GridBagConstraints gbcw = new GridBagConstraints(); gbcw.insets = new Insets(4,4,4,4); gbcw.anchor = GridBagConstraints.WEST; int wRow = 0;\n' +
'        JComboBox<String> maxSizeCombo = new JComboBox<>(new String[]{"Fit to Window", "Max 1024", "Max 512"});\n' +
'        int savedIdx = AppPreferences.getMaxSizeIndex();\n' +
'        if (savedIdx >= 0 && savedIdx < maxSizeCombo.getItemCount()) { maxSizeCombo.setSelectedIndex(savedIdx); }\n\n' +

'        JCheckBox chkAlignment = new JCheckBox("Align with orientation markers");\n' +
'        gbcw.gridx = 0; gbcw.gridy = wRow++; windowPanel.add(new JLabel("Max Size:"), gbcw);\n' +
'        gbcw.gridx = 1; windowPanel.add(maxSizeCombo, gbcw);\n' +
'        gbcw.gridx = 0; gbcw.gridy = ++wRow; gbcw.gridwidth = 2;\n' +
'        windowPanel.add(chkAlignment, gbcw);\n\n' +

'        pane.add(windowPanel);\n\n' +

'        contentPanel.add(new JScrollPane(pane), "Viewers");\n' +
'    }';

  pc = pc.substring(0, sIdx) + V + '\n' + pc.substring(after);
}


write('src/main/java/com/oculus/jj/ui/PreferencesController.java', pc);
console.log('\n=== ALL PATCHES COMPLETE ===');