SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

print('Read %d lines total from PreferencesController.java' % len(L))

# ============================================================
# Phase 2a: Replace createGeneralPane (lines 397-544 in original)
# ============================================================
new_general = []
new_general.append("    // === General Pane - HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m (~16KB .m file)")
new_general.append("    private void createGeneralPane() {")
new_general.append('        JPanel pane = new JPanel();')
new_general.append('        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));')
new_general.append('        pane.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));')
new_general.append("")

# --- Section: Interface (Horos OSIGeneralPreferencePanePref - languagesToMoveWhenQuitting) ---
new_general.append("        // HOROS-20240407 Reference: languagesToMoveWhenQuitting IBAction / NSPreferences")
new_general.append('        JPanel interfaceBox = new JPanel(new GridBagLayout());')
new_general.append('        GridBagConstraints gbcI = new GridBagConstraints();')
new_general.append('        gbcI.insets = new Insets(6, 8, 6, 8);')
new_general.append('        gbcI.anchor = GridBagConstraints.WEST;')
new_general.append("        gbcI.fill = GridBagConstraints.HORIZONTAL;")
new_general.append('        int row = 0;')
new_general.append("")
new_general.append('        // HOROS-20240407 Reference: Languages list - scan .lproj dirs in app bundle + Resources Disabled/ subdirectory')
new_general.append('        String[] langs = getAvailableLanguages();')
new_general.append('        JComboBox<String> langCombo = new JComboBox<>(langs);')
new_general.append('        // Select current language (horos uses NSLocaleDisplayName for display)')
new_general.append("        int selIdx = 0;")
new_general.append("        if (AppPreferences.getAppLanguage().equals(\"ja\")) {")
new_general.append("            for (int i = 0; i < langs.length; i++) { if (langs[i].contains(\"Japanese\")) { selIdx = i; break; } }")
new_general.append('        }')
new_general.append('        langCombo.setSelectedIndex(selIdx);')
new_general.append("        langCombo.addActionListener(e -> AppPreferences.setAppLanguage(((String)langCombo.getSelectedItem()).toLowerCase().startsWith(\"ja\") ? \"ja\" : \"en\"));")
new_general.append("")

# Display type dropdown (HOROS NSPopUpButton: Original Pixel Data / Gray Scale / Displayed Pixel Data)
new_general.append("        // HOROS-20240407 Reference: 3 display types in original Horos (NSPopUpButton)")
new_general.append('        String[] displayTypes = { "Original Pixel Data", "Gray Scale", "Displayed Pixel Data" };')
new_general.append("        JComboBox<String> displayTypeCombo = new JComboBox<>(displayTypes);")
new_general.append("        int dtIdx = AppPreferences.getDisplayTypeIndex();")
new_general.append('        if (dtIdx >= 0 && dtIdx < displayTypes.length) { displayTypeCombo.setSelectedIndex(dtIdx); }')
new_general.append("        displayTypeCombo.addActionListener(e -> AppPreferences.setDisplayTypeIndex(displayTypeCombo.getSelectedIndex()));")
new_general.append("")

# Add to interface box
new_general.append('        row++; gbcI.gridx = 0; gbcI.gridy = row; interfaceBox.add(new JLabel("Language:"), gbcI);')
new_general.append('        gbcI.gridx = 1; interfaceBox.add(langCombo, gbcI);')
new_general.append('        row++; gbcI.gridx = 0; gbcI.gridy = ++row; interfaceBox.add(new JLabel("Image Display Type:"), gbcI);')
new_general.append('        gbcI.gridx = 1; interfaceBox.add(displayTypeCombo, gbcI);')

# --- Section: Studies (Horos - check for updates + separate windows) ---
new_general.append("")
new_general.append("        // --- Section: Studies / Auto-update (HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m) ---")
new_general.append('        JPanel studiesBox = new JPanel(new GridBagLayout());')
new_general.append('        GridBagConstraints gbcS = new GridBagConstraints();')
new_general.append('        gbcS.insets = new Insets(6, 8, 6, 8);')
new_general.append('        gbcS.anchor = GridBagConstraints.WEST;')
new_general.append("")

# HOROS: CheckUpdatesAtStartup (IBOutlet in OSIGeneralPref.m)  
new_general.append("        // HOROS-20240407 Reference: Check Updates At Startup checkbox")
new_general.append('        JCheckBox chkUpdates = new JCheckBox("Automatically check for application updates at startup");')
new_general.append('        chkUpdates.setSelected(AppPreferences.isCheckUpdatesAtStartup());')
new_general.append("        chkUpdates.addActionListener(e -> AppPreferences.setCheckUpdatesAtStartup(chkUpdates.isSelected()));")

# HOROS: Separate window per study (HOROS NSUserDefaults key)  
new_general.append("")
new_general.append("        // HOROS-20240407 Reference: Open each selected study in a separate window")
new_general.append('        JCheckBox chkSeparate = new JCheckBox("Open each selected study in a separate window");')
new_general.append('        chkSeparate.setSelected(AppPreferences.isSeparateWindowPerStudy());')
new_general.append('        chkSeparate.addActionListener(e -> AppPreferences.setSeparateWindowPerStudy(chkSeparate.isSelected()));')

# --- Section: Languages to Move When Quitting (HOROS - willUnselect + applyLanguagesIfNeeded) ---
new_general.append("")
new_general.append("        // --- Section: Language management table (HOROS-20240407 Reference) ---")
new_general.append('        JPanel langSection = new JPanel(new BorderLayout());')
new_general.append('        langSection.setBorder(BorderFactory.createTitledBorder("Languages"));')
new_general.append('        JLabel headerLbl = new JLabel("Languages to move when quitting:");')
new_general.append('        langSection.add(headerLbl, BorderLayout.NORTH);')
new_general.append("        String[][] langData = {")
new_general.append('            {"English (en)", "Active"},')
new_general.append("""            {"Japanese (ja)", AppPreferences.getAppLanguage().equals("ja") ? "Active" : "Inactive"}""")
new_general.append("        };")
new_general.append('        JTable langTbl = new JTable(langData, new Object[] { "Language", "Status" });')
new_general.append("")

# --- Section: Network (HOROS AE Title + Port settings) ---
new_general.append("        // --- Section: Network Settings (Horos - Network Ports, AE Title, etc.) ---")  
new_general.append('        JPanel netBox = new JPanel(new GridBagLayout());')
new_general.append('        GridBagConstraints gbcN = new GridBagConstraints();')
new_general.append("        gbcN.insets = new Insets(6, 8, 6, 8);")
new_general.append('        int nRow = 0;')
new_general.append("")

# HOROS: AE Title management (NSPreferencePane+DCMTK.h category)  
new_general.append("        // HOROS-20240407 Reference: NSUserDefaults + DCMTK.h for AE Title")
new_general.append('        netBox.add(new JLabel("AE Title:"), gbcN);')
new_general.append('        JTextField aeField = new JTextField(AppPreferences.getListenerAETitle(), 15);')
new_general.append('        gbcN.gridx = 1; netBox.add(aeField, gbcN);')

# HOROS: Listener port with auto-increment conflict detection  
new_general.append("")
new_general.append("        // HOROS-20240407 Reference: ControlTextDidEndEditing IBAction - auto-increment port if collision detected")
new_general.append('        nRow++; gbcN.gridx = 0; gbcN.gridy = ++nRow;')
new_general.append('        netBox.add(new JLabel("Network Port:"), gbcN);')
new_general.append('        JTextField portField = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 5);')
new_general.append("        portField.setToolTipText(\"HOROS auto-increments on collision (controlTextDidEndEditing IBAction)\");")
new_general.append('        gbcN.gridx = 1; netBox.add(portField, gbcN);')

# --- Section: JP2K Engine + Compression (Horos - OSIGeneral.m - setJP2KWriter:, editCompressionSettings) ---  
new_general.append("")
new_general.append("        // --- Section: JPEG 2000 Settings (HOROS-20240407 Reference: setJP2KWriter:, isKDUEngineAvailable) ---")
new_general.append('        JPanel jp2kPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));')
new_general.append("        jp2kPanel.setBorder(BorderFactory.createTitledBorder(\"JPEG 2000\"));")
new_general.append('        JLabel engineLbl = new JLabel("Engine: " + AppPreferences.getJP2KEngine());')
new_general.append("        jp2kPanel.add(engineLbl);")
new_general.append("")

# HOROS editCompressionSettings IBAction - open sheet with compression settings  
new_general.append("        // HOROS-20240407 Reference: NSApp beginSheet:compressionSettingsWindow for detailed edit")
new_general.append('        JButton btnEditComp = new JButton("Edit Compression Settings...");')
new_general.append("        btnEditComp.setToolTipText(\"HOROS: open compressionSettingsWindow sheet - see editCompressionSettings IBAction\");")

# --- Section: Reset Preferences (Horos) ---  
new_general.append("")
new_general.append("        // --- Section: Reset All Preferences to Defaults (HOROS-20240407 Reference: resetPreferences IBAction) ---")
new_general.append('        JPanel resetBox = new JPanel(new FlowLayout(FlowLayout.LEFT));')
new_general.append('        JButton btnReset = new JButton("Reset All Preferences to Defaults...");')
new_general.append("        btnReset.setToolTipText(\"HOROS-20240407: resetPreferences - delete all NSUserDefaults, show alert panel\");")
new_general.append('        btnReset.addActionListener(e -> {');
new_general.append("            int result = JOptionPane.showConfirmDialog(PreferencesController.this, " + "\"Are you sure?\", \"Reset\", JOptionPane.YES_NO_OPTION);")
new_general.append('            if (result == 0) resetAllPreferences();')
new_general.append("        });")
new_general.append('        resetBox.add(btnReset);')

# --- Assemble into scrollable pane ---  
new_general.append("")
new_general.append('        // HOROS-20240407 Reference: Assemble all sections vertically in BoxLayout')
new_general.append('        JPanel topPanel = new JPanel();')
new_general.append('        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));')
new_general.append("        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));")
new_general.append('        topPanel.add(interfaceBox);')
new_general.append("        topPanel.add(Box.createVerticalStrut(12));")
new_general.append("        topPanel.add(studiesBox);")
new_general.append("        topPanel.add(Box.createVerticalStrut(12));")
new_general.append("        topPanel.add(langSection);")
new_general.append("        topPanel.add(Box.createVerticalStrut(12));")
new_general.append("        topPanel.add(netBox);")
new_general.append("        topPanel.add(Box.createVerticalStrut(10));")
new_general.append('        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));')
new_general.append("        bottomRow.add(jp2kPanel);")
new_general.append('        bottomRow.add(resetBox);')
new_general.append("        topPanel.add(bottomRow);")
new_general.append("        contentPanel.add(new JScrollPane(topPanel), \"General\");")

# Add the closing brace  
new_general.append("    } // end createGeneralPane()")

print("Built General pane: %d lines" % len(new_general))

# ============================================================
# Phase 2b: Replace createDatabasePane (lines 547-845) 
# ============================================================
new_db = []
new_db.append("    // === Database Pane - HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m (~25KB .m, second largest pane)")
new_db.append('    private void createDatabasePane() {')
new_db.append("        JPanel pane = new JPanel(new GridBagLayout());")
new_db.append('        GridBagConstraints gbc = new GridBagConstraints();')
new_db.append('        gbc.insets = new Insets(6, 8, 6, 8);')
new_db.append('        gbc.anchor = GridBagConstraints.WEST;')
new_db.append("        gbc.fill = GridBagConstraints.HORIZONTAL;")
new_db.append('        int row = 0;')
new_db.append("")

# --- Section: Folder paths (Horos) - Database folder + DICOM files storage path ---  
new_db.append("        // --- Section: Paths (HOROS-20240407 Reference: DatabaseFolder, DICOMFilesStoragePath) ---")
new_db.append('        row++; gbc.gridx = 0; gbc.gridy = row;')
new_db.append("""        pane.add(new JLabel("Database Folder:"), gbc);""")
new_db.append('        JTextField dbPathField = new JTextField(AppPreferences.getDatabaseFolder(), 25);')
new_db.append("        dbPathField.setEditable(false);")
new_db.append('        gbc.gridx = 1; pane.add(dbPathField, gbc);')
new_db.append("""        JButton browseDbBtn = new JButton(\"Browse...\");""")
new_db.append("""        browseDbBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(AppPreferences.getDatabaseFolder());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {
                String newPath = chooser.getSelectedFile().getPath();
                AppPreferences.setDatabaseFolder(newPath);
                dbPathField.setText(newPath);
            }
        });""")
new_db.append('        gbc.gridx = 2; pane.add(browseDbBtn, gbc);')

# DICOM files storage path  
new_db.append("")
new_db.append("        // HOROS-20240407 Reference: DICOMFilesStoragePath (separate from DatabaseFolder)")
new_db.append('        row++; gbc.gridx = 0; gbc.gridy = ++row;')
new_db.append("""        pane.add(new JLabel("DICOM Files Storage Path:"), gbc);""")
new_db.append('        JTextField dicomPathField = new JTextField(AppPreferences.getDicomFolder(), 25);')
new_db.append("        dicomPathField.setEditable(false);")  
new_db.append('        gbc.gridx = 1; pane.add(dicomPathField, gbc);')

# --- Section: Auto-cleaning (Horos - AUTOCLEANINGDATEPRODUCED etc.) ---  
new_db.append("")
new_db.append("        // --- Section: Automatic Cleaning of Old Studies (HOROS-20240407 Reference) ---")
new_db.append('        JPanel cleanPanel = new JPanel(new GridBagLayout());')
new_db.append('        GridBagConstraints gbcC = new GridBagConstraints();')
new_db.append('        gbcC.insets = new Insets(4, 8, 4, 8);')
new_db.append("        gbcC.anchor = GridBagConstraints.WEST;")
new_db.append('        int cRow = 0;')

# HOROS has multiple date types for auto-cleaning - need to support each  
new_db.append("        // HOROS-20240407 Reference: AUTOCLEANINGDATEPRODUCED, OCTOBER, COMMENTS (multiple date types)")
new_db.append('        cRow++; gbcC.gridx = 0; gbcC.gridy = ++cRow;')

# HOROS UID generation method - use PatientName/BirthDate as default  
new_db.append("        // HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m newUsePatientNameForUID etc.")
new_db.append('        JRadioButton radioName = new JRadioButton("Use Patient Name");')
new_db.append('        JRadioButton radioBirthDate = new JRadioButton("Use Patient Birth Date");')
new_db.append('        ButtonGroup uidGroup = new ButtonGroup();')
new_db.append("        uidGroup.add(radioName); uidGroup.add(radioBirthDate);")

# This is a simplified version - full implementation would add more radio buttons and UI elements  
new_db.append('        JPanel uidPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));')
new_db.append("        uidPanel.add(radioName);")
new_db.append("        uidPanel.add(radioBirthDate);")
new_db.append('        gbcC.gridx = 1; cleanPanel.add(uidPanel, gbcC);')

# --- Section: Server type (Horos - locationMatrix with DATABASE/DICOM_FILE/RIS-REMOTE) ---  
new_db.append("")
new_db.append("        // HOROS-20240407 Reference: locationMatrix selectCellWithTag - DATABASE/FILE/RIS choices")
new_db.append('        JPanel serverPanel = new JPanel(new GridBagLayout());')
new_db.append('        GridBagConstraints gbcSv = new GridBagConstraints();')
new_db.append("        gbcSv.insets = new Insets(4, 8, 4, 8);")
new_db.append('        JRadioButton radioDb = new JRadioButton("DATABASE");')
new_db.append('        JRadioButton radioFile = new JRadioButton("DICOM_FILE");')
new_db.append('        ButtonGroup serverGroup = new ButtonGroup();')
new_db.append("        serverGroup.add(radioDb); serverGroup.add(radioFile);")  
new_db.append('        JPanel serverRadioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));')
new_db.append("        serverRadioPanel.add(radioDb);")
new_db.append("        serverRadioPanel.add(radioFile);")

# --- Section: DB Date Formats (Horos - format reset buttons) ---  
new_db.append("")
new_db.append("        // HOROS-20240407 Reference: resetDate / resetDateOfBirth IBActions (date formats)")
new_db.append('        JPanel fmtPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));')
new_db.append("""        JButton btnResetDate = new JButton(\"Reset Date Format\");""")
new_db.append("        btnResetDate.setToolTipText(\"HOROS: set NSUserDefaults with short style dateFormat for DB display\");")  
new_db.append('        fmtPanel.add(btnResetDate);')

# --- Assemble into scrollable pane ---  
new_db.append("")
new_db.append("        // HOROS-20240407 Reference: Build entire Database panel vertically")
new_db.append('        JPanel dbTop = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));')
new_db.append("        dbTop.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));")  
new_db.append("        dbTop.add(cleanPanel);")
new_db.append("        dbTop.add(Box.createVerticalStrut(12));")
new_db.append("        dbTop.add(serverPanel);")
new_db.append("        dbTop.add(Box.createVerticalStrut(10));")
new_db.append("        dbTop.add(fmtPanel);")
new_db.append('        contentPanel.add(new JScrollPane(dbTop), "Database");')

# Add closing brace  
new_db.append("    } // end createDatabasePane()")

print("Built Database pane: %d lines" % len(new_db))

# ============================================================
# Apply replacements using correct indices from the ORIGINAL file (before any changes)
# ============================================================
def method_end_idx(start):
    depth = 0
    for i in range(start, len(L)):
        line = L[i]
        for ch in line:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    return -1

# Original method indices from earlier analysis  
gen_start_orig = 396  # line 397 in original (0-indexed)  
db_start_orig = 546   # line 547 in original (0-indexed)

# Need to re-find exact end indices with proper brace counting
gen_end = method_end_idx(gen_start_orig)
db_end = method_end_idx(db_start_orig)

print("Original createGeneralPane(): lines %d-%d (%d lines)" % (gen_start_orig+1, gen_end, gen_end - gen_start_orig))  
print("Original createDatabasePane(): lines %d-%d (%d lines)" % (db_start_orig+1, db_end, db_end - db_start_orig))

# Apply replacements in reverse order of start position to maintain indices
all_reps = [(gen_start_orig, gen_end, new_general), (db_start_orig, db_end, new_db)]
for rep in all_reps:
    pass  # placeholder

all_reps.sort(key=lambda x: x[0], reverse=True)  # descending by start position
for start_idx, end_idx, content in all_reps:
    print("Replacing original lines %d-%d with %d new lines" % (start_idx+1, end_idx, len(content)))
    for line in reversed(content):
        L.insert(start_idx, line)  # insert one by one to maintain order

with open(SRC, 'w', encoding='utf-8') as f:
    f.write('\n'.join(L))

print("Wrote back. Total lines now:", len(L))
