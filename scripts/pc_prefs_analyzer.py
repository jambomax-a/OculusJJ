import os

SRC = "src/main/java/com/oculus/jj/ui/PreferencesController.java"
with open(SRC, encoding="utf-8") as f:
    L = f.read().split("\n")

print("Reading %d lines from PreferencesController.java" % len(L))

# Helper to find method by name
def find_method(start_from):
    for i in range(start_from, len(L)):
        t = L[i].strip()
        if "private void createGeneralPane()" in t or "private static final" in t[:50]:
            pass  # skip these for now
        if "createDatabasePane()" in t: return i  
    return -1

# Phase A: Replace createGeneralPane with comprehensive implementation
def find_method_bounds(method_name):
    """Find start and end (exclusive) line indices of a method by tracking braces."""
    start = -1
    for i in range(len(L)):
        if method_name in L[i]:
            # Make sure this is the right method (not partial match)
            stripped = L[i].strip()
            if method_name.replace("( ", "(").replace("(", "( ") in stripped or method_name + "()" in stripped:
                start = i
                break
    if start == -1: return None, None
    
    # Find end by brace counting  
    depth = 0
    for i in range(start, len(L)):
        for ch in L[i]:
            if ch == "{": depth += 1
            elif ch == "}": depth -= 1
            if depth == 0 and i > start: return start, i + 1  # exclusive end
    
    return start, -1

# Find method boundaries  
gen_start, gen_end = find_method_bounds("createGeneralPane()")
db_start, db_end = find_method_bounds("createDatabasePane()")

print("createGeneralPane(): lines %d-%d" % (gen_start+1, gen_end))
print("createDatabasePane(): lines %d-%d" % (db_start+1, db_end))

# Phase 2: Build replacement content for General pane - much more comprehensive
general_pane_content = [
    "    // === General Pane - HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m (16,282 bytes)",
    "    private void createGeneralPane() {",
    '        JPanel pane = new JPanel();',
    '        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));',
    '        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));',
    '',
    "        // --- Interface section (HOROS: OSIGeneralPreferencePanePref.m) ---",
    '        JPanel interfaceBox = new JPanel(new GridBagLayout());',
    '        GridBagConstraints gbc = new GridBagConstraints();',
    '        gbc.insets = new Insets(6, 6, 6, 6);',
    '        gbc.anchor = GridBagConstraints.WEST;',
    '        gbc.fill = GridBagConstraints.HORIZONTAL;',
    '        int row = 0;',
    '',
    "        // HOROS-20240407 Reference: languagesToMoveWhenQuitting - scan Resources Disabled dir for inactive langs",
    "        // Horos scans contentsOfDirectoryAtPath: resourcePath + .lproj extension, then Resources Disabled/ too",
    '        String[] langs = getAvailableLanguages();',
    '        JComboBox<String> langCombo = new JComboBox<>(langs);',
    "        String currentLang = AppPreferences.getAppLanguage().equals(\"ja\") ? \"Japanese (ja)\" : \"English (en)\";",
    "        // HOROS-20240407: setSelectedItem not available for JComboBox<String>, use selected index approach",
    "        int langIdx = 0;",
    "        for (int i = 0; i < langs.length; i++) { if (langs[i].contains(\"ja\")) { langIdx = i; break; } }",
    '        langCombo.setSelectedIndex(langIdx);',
    '',
    '        String[] displayTypes = {"Original Pixel Data", "Gray Scale", "Displayed Pixel Data"};',
    '        JComboBox<String> displayTypeCombo = new JComboBox<>(displayTypes);',
    '        int dtIdx = AppPreferences.getDisplayTypeIndex();',
    '        if (dtIdx >= 0 && dtIdx < displayTypes.length) displayTypeCombo.setSelectedIndex(dtIdx);',
    "        displayTypeCombo.addActionListener(e -> AppPreferences.setDisplayTypeIndex(displayTypeCombo.getSelectedIndex()));",
    '',
    '        row++; gbc.gridx = 0; gbc.gridy = row;',
    '        interfaceBox.add(new JLabel("Language:"), gbc);',
    '        gbc.gridx = 1; interfaceBox.add(langCombo, gbc);',
    '        row++; gbc.gridx = 0; gbc.gridy = row;',
    '        interfaceBox.add(new JLabel("Image Display Type:"), gbc);',
    '        gbc.gridx = 1; interfaceBox.add(displayTypeCombo, gbc);',
    '',
    "        // --- Auto-Update / Check for Updates (HOROS: OSIGeneralPref.m - CheckUpdatesAtStartup) ---",
    "        row++; gbc.gridx = 0; gbc.gridy = ++row;",
    '        JCheckBox chkUpdates = new JCheckBox("Automatically check for application updates at startup");',
    '        chkUpdates.setSelected(AppPreferences.isCheckUpdatesAtStartup());',
    '        chkUpdates.addActionListener(e -> AppPreferences.setCheckUpdatesAtStartup(chkUpdates.isSelected()));',
    '        interfaceBox.add(new JScrollPane(checkUpdates) { { setPreferredSize(new Dimension(0, 40)); } }, gbc);',
    '',
    "        // --- Languages list management (HOROS: willUnselect + applyLanguagesIfNeeded IBAction) ---",
    "        JPanel langManagePanel = new JPanel(new BorderLayout());",
    "        JLabel langLabel = new JLabel(\"Languages to move when quitting:\");",
    "        langManagePanel.add(langLabel, BorderLayout.NORTH);",
    "        JTable languagesTable = new JTable(3, 2);  // horos shows active/inactive list from .lproj dirs",
    "        Object[][] langData = {",
    '            {"English", "Active"},',
    '            {"Japanese", AppPreferences.getAppLanguage().equals("ja") ? "Active" : "Inactive"},',
    "        };",
    "        languagesTable.setModel(new javax.swing.table.DefaultTableModel(langData, new String[]{\"Language\", \"Status\"}) {");
general_pane_content.append("            @Override public boolean isCellEditable(int r, int c) { return c == 1; } // only status editable in Horos");

general_pane_content.extend([
    "        });",
    '        JScrollPane langScroll = new JScrollPane(languagesTable);',
    '        langManagePanel.add(langScroll, BorderLayout.CENTER);',
    '',
    "        // HOROS-20240407 Reference: editCompressionSettings IBAction (open compressionSettingsWindow sheet)",
    "        JPanel compPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));",
    '        JButton btnEditComp = new JButton("Edit Compression Settings...");',
    "        btnEditComp.addActionListener(e -> showCompressionEditor());  // stub for Phase 3+",
    '        JButton btnResetComp = new JButton("Reset Compression Settings to Defaults");',
    '        btnResetComp.addActionListener(e -> { if (AppPreferences.getCompressionSettings().isEmpty() || AppPreferences.getCompressionSettings().equals("")) { JOptionPane.showMessageDialog(PreferencesController.this, "No custom compression settings yet."); } });',
    '',
    "        // HOROS-20240407 Reference: AE Title / Network Port / Password Protection",
    '        JPanel networkBox = new JPanel(new GridBagLayout());',
    '        gbc = new GridBagConstraints();',
    '        gbc.insets = new Insets(6, 6, 6, 6);',
    "        gbc.gridx = 0; gbc.gridy = 0;",
    "        networkBox.add(new JLabel(\"AE Title:\"), gbc);",
    '        JTextField aeField = new JTextField(AppPreferences.getListenerAETitle(), 15);',
    '        gbc.gridx = 1; networkBox.add(aeField, gbc);',
    '',
    "        // HOROS-20240407 Reference: listener port with auto-increment (controlTextDidEndEditing IBAction)",
    '        row++; gbc.gridx = 0; gbc.gridy = ++row;',
    "        networkBox.add(new JLabel(\"Network Port:\"), gbc);",
    '        JTextField portField = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 5);',
    '        portField.addFocusListener(new java.awt.event.FocusAdapter() {',
    "            public void focusLost(java.awt.event.FocusEvent e) { checkPortConflict(portField); }",
    '        });',
    '        gbc.gridx = 1; networkBox.add(portField, gbc);',
    '',
    "        // HOROS-20240407 Reference: passwordForWindowProtectionInterval (window lock)",
    '        JPanel pwPanel = new JPanel(new GridBagLayout());',
    '        JCheckBox chkPasswordLock = new JCheckBox("Automatically lock window after inactivity");',
    "        // HOROS stores interval in NSUserDefaults - we'll add field when needed",
    '        pwPanel.add(chkPasswordLock, gbc);',
    '',
    "        JPanel resetBox = new JPanel(new FlowLayout(FlowLayout.LEFT));",
    '        JButton btnReset = new JButton("Reset All Preferences to Defaults...");',
    "        btnReset.addActionListener(e -> { int r = JOptionPane.showConfirmDialog(this, \"Are you sure?\", \"Reset\", JOptionPane.YES_NO_OPTION); if (r == 0) resetAllPreferences(); });",
    '        resetBox.add(btnReset);',
    '',
    // --- Put everything together in a scroll pane ---
    "        JPanel topPanel = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));",
    "        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));",
    "        topPanel.add(interfaceBox);",
    "        topPanel.add(langManagePanel);",
    "        topPanel.add(compPanel);",
    "        topPanel.add(networkBox);",
    "        topPanel.add(pwPanel);",
    "        topPanel.add(Box.createVerticalStrut(10));",
    "        topPanel.add(resetBox);",
    '        contentPanel.add(new JScrollPane(topPanel), "General");',
    "    }"
]);

print("Built General pane replacement: %d lines" % len(general_pane_content))
with open('scripts/gen_debug.txt', 'w') as f:
    for line in general_pane_content:
        f.write(line + '\n')
print("Written to scripts/gen_debug.txt")
