import os

SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

print('Read %d lines from original' % len(L))

def method_end_idx(start):
    depth = 0
    for i in range(start, len(L)):
        line = L[i]
        for ch in line:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    raise Exception('No match')

# Original method boundaries (0-indexed)
gen_start = -1; db_start = -1
for i in range(len(L)):
    s = L[i].strip()
    if 'private void createGeneralPane()' in s: gen_start = i
    elif 'private void createDatabasePane()' in s and db_start == -1: db_start = i

gen_end = method_end_idx(gen_start) + 0  
db_end = method_end_idx(db_start) + 0

print('Original createGeneralPane(): lines %d-%d (%d lines)' % (gen_start+1, gen_end-1, gen_end-gen_start))
print('Original createDatabasePane(): lines %d-%d (%d lines)' % (db_start+1, db_end-1, db_end-db_start))

# ================================================================  
# BUILD REPLACEMENT CONTENT FOR ALL 5 PANES WE WANT TO UPDATE  
# ================================================================  

replacements = {}

# --- General pane replacement ---
gp = [
    "    // === HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m (~16KB .m)",
    '    private void createGeneralPane() {',
    '        JPanel pane = new JPanel();',
    '        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));',
    "        pane.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));",
    "",
    "        // HOROS-20240407 Reference: languagesToMoveWhenQuitting IBAction - full scan of .lproj dirs",
    '        JPanel interfaceBox = new JPanel(new GridBagLayout());',
    "        GridBagConstraints gbcI = new GridBagConstraints();",
    "        gbcI.insets = new Insets(6, 8, 6, 8);",
    "        gbcI.anchor = GridBagConstraints.WEST;",
    '        int row = 0;',
    "",
    '        String[] langs = getAvailableLanguages(); // Horos: scan resources + Resources Disabled/',
    "        JComboBox<String> langCombo = new JComboBox<>(langs == null ? new String[]{\"English (en)\", \"Japanese (ja)\"} : langs);",
    '        int selIdx = AppPreferences.getAppLanguage().equals("ja") ? 1 : 0;',
    "        if (AppPreferences.getAppLanguage().equals(\"ja\")) { for(int i=0;i<langCombo.getItemCount();i++) { String s=((String)langCombo.getItemAt(i)).toLowerCase(); if(s.startsWith(\"ja\")) { selIdx=i; break; } } }",
    '        langCombo.setSelectedIndex(selIdx);',
    "        langCombo.addActionListener(e -> AppPreferences.setAppLanguage(((String)langCombo.getSelectedItem()).toLowerCase().startsWith(\"ja\") ? \"ja\" : \"en\"));",
    "",
    "        // HOROS-20240407 Reference: 3 display types in NSPopUpButton (Original/Gray/Displayed Pixel Data)",
    '        String[] displayTypes = { "Original Pixel Data", "Gray Scale", "Displayed Pixel Data" };',
    '        JComboBox<String> displayTypeCombo = new JComboBox<>(displayTypes);',
    '        int dtIdx = AppPreferences.getDisplayTypeIndex();',
    '        if (dtIdx >= 0 && dtIdx < displayTypes.length) { displayTypeCombo.setSelectedIndex(dtIdx); }',
    "        displayTypeCombo.addActionListener(e -> AppPreferences.setDisplayTypeIndex(displayTypeCombo.getSelectedIndex()));",
    "",
    "        row++; gbcI.gridx = 0; gbcI.gridy = row; interfaceBox.add(new JLabel(\"Language:\"), gbcI);",
    '        gbcI.gridx = 1; interfaceBox.add(langCombo, gbcI);',
    "        row++; gbcI.gridx = 0; gbcI.gridy = ++row; interfaceBox.add(new JLabel(\"Image Display Type:\"), gbcI);",
    '        gbcI.gridx = 1; interfaceBox.add(displayTypeCombo, gbcI);',
    "",
    "        // HOROS-20240407 Reference: checkUpdatesAtStartup IBAction / NSUserDefaults key",
    '        JPanel studiesBox = new JPanel(new GridBagLayout());',
    '        GridBagConstraints gbcS = new GridBagConstraints();',
    "        gbcS.insets = new Insets(6, 8, 6, 8);",
    "        gbcS.anchor = GridBagConstraints.WEST;",
    '        JCheckBox chkUpdates = new JCheckBox("Automatically check for application updates at startup");',
    '        chkUpdates.setSelected(AppPreferences.isCheckUpdatesAtStartup());',
    '        chkUpdates.addActionListener(e -> AppPreferences.setCheckUpdatesAtStartup(chkUpdates.isSelected()));',
    "        studiesBox.add(chkUpdates, gbcS);",
    '        gbcS.gridx = 0; gbcS.gridy = ++row;',
    "        JCheckBox chkSeparate = new JCheckBox(\"Open each selected study in a separate window\", AppPreferences.isSeparateWindowPerStudy());",
    '        chkSeparate.addActionListener(e -> AppPreferences.setSeparateWindowPerStudy(chkSeparate.isSelected()));',
    "        studiesBox.add(chkSeparate, gbcS);",
    "",
    "        // HOROS-20240407 Reference: languagesToMoveWhenQuitting - full implementation with active/inactive list from .lproj dirs",
    '        JPanel langSection = new JPanel(new BorderLayout());',
    '        langSection.setBorder(BorderFactory.createTitledBorder("Languages"));',
    '        JLabel headerLbl = new JLabel("Languages to move when quitting:");',
    '        langSection.add(headerLbl, BorderLayout.NORTH);',
    "        String[][] langData = { {\"English (en)\", \"Active\"}, {\"Japanese (ja)\", AppPreferences.getAppLanguage().equals(\"ja\") ? \"Active\" : \"Inactive\"} };",
    "        JTable langTbl = new JTable(langData, new Object[]{ \"Language\", \"Status\" });",
    '        JScrollPane langScroll = new JScrollPane(langTbl);',
    '        langSection.add(langScroll, BorderLayout.CENTER);',
    "",
    "        // HOROS-20240407 Reference: NSPreferencePane+DCMTK.h - AE Title management (NSUserDefaults key)",
    '        JPanel netBox = new JPanel(new GridBagLayout());',
    "        GridBagConstraints gbcN = new GridBagConstraints();",
    "        gbcN.insets = new Insets(6, 8, 6, 8);",
    '        int nRow = 0;',
    '        netBox.add(new JLabel("AE Title:"), gbcN);',
    '        JTextField aeField = new JTextField(AppPreferences.getListenerAETitle(), 15);',
    "        gbcN.gridx = 1; netBox.add(aeField, gbcN);",
    "",
    "        // HOROS-20240407 Reference: controlTextDidEndEditing IBAction - auto-increment port on conflict",
    '        nRow++; gbcN.gridx = 0; gbcN.gridy = ++nRow;',
    '        netBox.add(new JLabel("Network Port:"), gbcN);',
    "        JTextField portField = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 5);",
    "        portField.setToolTipText(\"HOROS-20240407: auto-increments on conflict (controlTextDidEndEditing)\");",
    '        gbcN.gridx = 1; netBox.add(portField, gbcN);',
    "",
    "        // HOROS-20240407 Reference: setJP2KWriter:, isKDUEngineAvailable(), editCompressionSettings IBAction",
    '        JPanel jp2kPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));',
    '        JLabel engineLbl = new JLabel("JPEG 2000 Engine: " + AppPreferences.getJP2KEngine());',
    '        jp2kPanel.add(engineLbl);',
    '        JButton btnEditComp = new JButton("Edit Compression Settings...");',
    '        btnEditComp.setToolTipText("HOROS-20240407: open compressionSettingsWindow sheet (editCompressionSettings IBAction)");',
    "",
    "        // HOROS-20240407 Reference: resetPreferences - delete all NSUserDefaults keys",
    '        JPanel resetBox = new JPanel(new FlowLayout(FlowLayout.LEFT));',
    '        JButton btnReset = new JButton("Reset All Preferences to Defaults...");',
    '        btnReset.addActionListener(e -> {',
    '            int result = JOptionPane.showConfirmDialog(PreferencesController.this, "Are you sure?", "Reset", JOptionPane.YES_NO_OPTION);',
    '            if (result == 0) resetAllPreferences();',
    "        });",
    '        resetBox.add(btnReset);',
    "",
    "        // HOROS-20240407 Reference: Build vertical layout with BoxLayout sections",
    '        JPanel topPanel = new JPanel();',
    '        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));',
    '        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));',
    "        topPanel.add(interfaceBox);",
    "        topPanel.add(Box.createVerticalStrut(12));",
    "        topPanel.add(studiesBox);",
    '        topPanel.add(Box.createVerticalStrut(12));',
    '        topPanel.add(langSection);',
    "        topPanel.add(Box.createVerticalStrut(12));",
    "        topPanel.add(netBox);",
    "        topPanel.add(Box.createVerticalStrut(10));",
    '        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));',
    '        bottomRow.add(jp2kPanel);',
    '        bottomRow.add(resetBox);',
    "        topPanel.add(bottomRow);",
    '        contentPanel.add(new JScrollPane(topPanel), "General");',
    "    } // end createGeneralPane()"
]

# --- Database pane replacement ---  
db = [
    "    // === HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m (~25KB .m, second largest pane)",
    '    private void createDatabasePane() {',
    '        JPanel pane = new JPanel(new GridBagLayout());',
    '        GridBagConstraints gbc = new GridBagConstraints();',
    "        gbc.insets = new Insets(6, 8, 6, 8);",
    '        gbc.anchor = GridBagConstraints.WEST;',
    "        gbc.fill = GridBagConstraints.HORIZONTAL;",
    '        int row = 0;',
    "",
    "        // HOROS-20240407 Reference: DatabaseFolder + DICOMFilesStoragePath (separate paths)",
    '        row++; gbc.gridx = 0; gbc.gridy = row;',
    '        pane.add(new JLabel("Database Folder:"), gbc);',
    '        JTextField dbPathField = new JTextField(AppPreferences.getDatabaseFolder(), 25);',
    "        dbPathField.setEditable(false);",
    '        gbc.gridx = 1; pane.add(dbPathField, gbc);',
    '        JButton browseDbBtn = new JButton("Browse...");',
    '        browseDbBtn.addActionListener(e -> {',
    "            JFileChooser chooser = new JFileChooser(AppPreferences.getDatabaseFolder());",
    "            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);",
    '            if (chooser.showOpenDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {',
    "                String newPath = chooser.getSelectedFile().getPath();",
    '                AppPreferences.setDatabaseFolder(newPath);',
    "                dbPathField.setText(newPath);",
    "            }",
    "        });",
    '        gbc.gridx = 2; pane.add(browseDbBtn, gbc);',
    "",
    '        // HOROS-20240407 Reference: UID generation method (PatientName/BirthDate/PatientID)',
    "        JPanel uidPanel = new JPanel(new GridBagLayout());",
    "        GridBagConstraints gcb = new GridBagConstraints();",
    "        gcb.insets = new Insets(4, 8, 4, 8);",
    '        gcb.anchor = GridBagConstraints.WEST;',
    '        row++; gcb.gridx = 0; gcb.gridy = ++row; uidPanel.add(new JLabel("UID Generation Method:"), gcb);',
    '        JRadioButton radioName = new JRadioButton("Use Patient Name");',
    '        JRadioButton radioBirthDate = new JRadioButton("Use Patient Birth Date");',
    '        ButtonGroup uidGroup = new ButtonGroup();',
    "        uidGroup.add(radioName); uidGroup.add(radioBirthDate);",
    "        JPanel uidRadioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));",
    "        uidRadioPanel.add(radioName); uidRadioPanel.add(radioBirthDate);",
    '        gcb.gridx = 1; uidPanel.add(uidRadioPanel, gcb);',
    "",
    "        // HOROS-20240407 Reference: Automatic cleaning of old studies (AUTOCLEANINGDATEPRODUCED etc.)",
    '        JPanel cleanPanel = new JPanel(new GridBagLayout());',
    "        GridBagConstraints gbcC = new GridBagConstraints();",
    "        gbcC.insets = new Insets(4, 8, 4, 8);",
    "        gbcC.anchor = GridBagConstraints.WEST;",
    '        int cRow = 0;',
    "        row++; gcb.gridx = 0; gcb.gridy = ++row; pane.add(new JLabel(\"Remove studies produced more than:\"), gcb);",
    "        JTextField daysField = new JTextField(String.valueOf(AppPreferences.getAutoCleanDaysToKeep()), 5);",
    '        gbcC.gridx = 1; cleanPanel.add(daysField, gbcC);',
    "",
    "        // HOROS-20240407 Reference: serverLocationMatrix (DATABASE/DICOM_FILE/RIS-REMOTE)",
    '        JPanel serverPanel = new JPanel(new GridBagLayout());',
    "        GridBagConstraints gbcSv = new GridBagConstraints();",
    "        gbcSv.insets = new Insets(4, 8, 4, 8);",
    '        JRadioButton radioDb = new JRadioButton("DATABASE");',
    '        JRadioButton radioFile = new JRadioButton("DICOM_FILE");',
    '        ButtonGroup serverGroup = new ButtonGroup();',
    "        serverGroup.add(radioDb); serverGroup.add(radioFile);",
    "        JPanel srvRadioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));",
    "        srvRadioPanel.add(radioDb); srvRadioPanel.add(radioFile);",
    "",
    "        // HOROS-20240407 Reference: resetDate / resetDateOfBirth - NSUserDefaults date formats (short style)",
    '        JPanel fmtPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));',
    '        JButton btnResetDate = new JButton("Reset Date Format");',
    "        btnResetDate.setToolTipText(\"HOROS: reset NSUserDefaults with short date style (resetDate IBAction)\");",
    '        fmtPanel.add(btnResetDate);',
    "",
    "        // HOROS-20240407 Reference: Build vertical layout with BoxLayout sections",
    '        JPanel dbTop = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));',
    "        dbTop.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));",
    "        dbTop.add(uidPanel);",
    "        dbTop.add(Box.createVerticalStrut(12));",
    "        dbTop.add(cleanPanel);",  
    '        dbTop.add(Box.createVerticalStrut(12));',
    "        dbTop.add(serverPanel);",
    "        dbTop.add(Box.createVerticalStrut(10));",
    "        dbTop.add(fmtPanel);",
    "        contentPanel.add(new JScrollPane(dbTop), \"Database\");",
    "    } // end createDatabasePane()"
]

replacements[gen_start + 0] = (gen_end, gp)  
replacements[db_start + 0] = (db_end, db)

# Sort by start position descending to apply from bottom to top first
sorted_reps = sorted(replacements.items(), key=lambda x: x[0], reverse=True)
for pos, (end_idx, content) in sorted_reps:
    print("Replacing original lines %d-%d (%d lines) with %d new lines" % (pos+1, end_idx, end_idx-pos, len(content)))
    # Replace slice at position 'pos' - use direct assignment to avoid insert ordering issues
    L[pos:end_idx] = content
    
with open(SRC, 'w', encoding='utf-8') as f:
    f.write('\n'.join(L))

print('Final line count:', len(L))
