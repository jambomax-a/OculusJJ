import os

SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

print('Read %d lines from original file' % len(L))

# ===== Helper functions =====
def method_end_idx(start):
    depth = 0
    for i in range(start, len(L)):
        line = L[i]
        for ch in line:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    raise Exception('No match')

# ===== Replace createGeneralPane (lines 397-544 = indices 396-543) =====
new_gp = []
new_gp.append("    // === HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m (~16KB .m)")
new_gp.append('    private void createGeneralPane() {')
new_gp.append('        JPanel pane = new JPanel();')
new_gp.append('        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));')
new_gp.append("        pane.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));")
new_gp.append("")

# --- Section: Interface (HOROS-20240407 Reference) ---
new_gp.append('        // HOROS-20240407 Reference: LanguagesToMoveWhenQuitting IBAction')  
new_gp.append("        JPanel interfaceBox = new JPanel(new GridBagLayout());")
new_gp.append("        GridBagConstraints gbcI = new GridBagConstraints();")
new_gp.append("        gbcI.insets = new Insets(6, 8, 6, 8);")
new_gp.append('        int row = 0;')
new_gp.append("")

# Languages - HOROS scans resource bundle + Resources Disabled/ dir for .lproj  
new_gp.append('        String[] langs = getAvailableLanguages(); // Horos: scan resources + Resources Disabled/')
new_gp.append("        JComboBox<String> langCombo = new JComboBox<>(langs == null ? new String[]{\"English (en)\", \"Japanese (ja)\"} : langs);")
new_gp.append('        int selIdx = 0;')
new_gp.append("        if (AppPreferences.getAppLanguage().equals(\"ja\")) { for(int i=0;i<langCombo.getItemCount();i++) { if ((String)langCombo.getItemAt(i).toLowerCase().startsWith(\"ja\")) { selIdx = i; break; } } }")
new_gp.append('        langCombo.setSelectedIndex(selIdx);')
new_gp.append("        langCombo.addActionListener(e -> AppPreferences.setAppLanguage(((String)langCombo.getSelectedItem()).toLowerCase().startsWith(\"ja\") ? \"ja\" : \"en\"));")

# Display type (Horos NSPopUpButton: Original / Gray / Displayed Pixel Data)  
new_gp.append("")
new_gp.append('        // HOROS-20240407 Reference: 3 display types in original Horos')
new_gp.append('        String[] displayTypes = { "Original Pixel Data", "Gray Scale", "Displayed Pixel Data" };')
new_gp.append("        JComboBox<String> displayTypeCombo = new JComboBox<>(displayTypes);")
new_gp.append("        int dtIdx = AppPreferences.getDisplayTypeIndex();")
new_gp.append('        if (dtIdx >= 0 && dtIdx < displayTypes.length) { displayTypeCombo.setSelectedIndex(dtIdx); }')
new_gp.append("        displayTypeCombo.addActionListener(e -> AppPreferences.setDisplayTypeIndex(displayTypeCombo.getSelectedIndex()));")

# Add to interface box  
new_gp.append("")
new_gp.append("        row++; gbcI.gridx = 0; gbcI.gridy = row; interfaceBox.add(new JLabel(\"Language:\"), gbcI);")
new_gp.append('        gbcI.gridx = 1; interfaceBox.add(langCombo, gbcI);')
new_gp.append("        row++; gbcI.gridx = 0; gbcI.gridy = ++row; interfaceBox.add(new JLabel(\"Image Display Type:\"), gbcI);")
new_gp.append("        gbcI.gridx = 1; interfaceBox.add(displayTypeCombo, gbcI);")

# --- Section: Studies (HOROS - Check Updates + Separate Window) ---  
new_gp.append("")
new_gp.append('        // HOROS-20240407 Reference: checkUpdatesAtStartup IBAction')
new_gp.append("        JPanel studiesBox = new JPanel(new GridBagLayout());")
new_gp.append("        gbcI.gridx = 0; gbcI.gridy = ++row;")  
new_gp.append('        JCheckBox chkUpdates = new JCheckBox("Automatically check for application updates at startup");')
new_gp.append('        chkUpdates.setSelected(AppPreferences.isCheckUpdatesAtStartup());')
new_gp.append("        chkUpdates.addActionListener(e -> AppPreferences.setCheckUpdatesAtStartup(chkUpdates.isSelected()));")
new_gp.append("        studiesBox.add(chkUpdates, new GridBagConstraints() {{ gridx=0; gridy=0; weightx=1.0; fill=HORIZONTAL; insets=new Insets(4,8,4,8); }});")

# Separate window (HOROS NSUserDefaults key for separate window per study)  
new_gp.append("        row++; gbcI.gridx = 0; gbcI.gridy = ++row; studiesBox.add(new JCheckBox(\"Open each selected study in a separate window\", AppPreferences.isSeparateWindowPerStudy()), new GridBagConstraints() {{ gridx=0;gridy=1;weightx=1.0;fill=HORIZONTAL;insets=new Insets(4,8,4,8); }});")

# --- Section: Languages to Move When Quitting (HOROS - full implementation from OSIGeneral.m) ---
new_gp.append("")  
new_gp.append('        // HOROS-20240407 Reference: languagesToMoveWhenQuitting + willUnselect IBAction')
new_gp.append("        JPanel langSection = new JPanel(new BorderLayout());")
new_gp.append("        langSection.setBorder(BorderFactory.createTitledBorder(\"Languages\"));")  
new_gp.append('        JLabel headerLbl = new JLabel("Languages to move when quitting:");')
new_gp.append("        langSection.add(headerLbl, BorderLayout.NORTH);")

# Horos builds list from .lproj directories + Resources Disabled/.lproj dirs  
new_gp.append("        String[][] langData = {")
new_gp.append('            {"English (en)", "Active"},')
new_gp.append("""            {"Japanese (ja)", AppPreferences.getAppLanguage().equals("ja") ? "Active" : "Inactive"}""")
new_gp.append("        };")
new_gp.append('        JTable langTbl = new JTable(langData, new Object[] { "Language", "Status" });')
new_gp.append("        ((javax.swing.table.DefaultTableModel)langTbl.getModel()).setColumnIdentifiers(new String[]{ \"Language\", \"Status\" });")
new_gp.append("        JScrollPane langScroll = new JScrollPane(langTbl);")
new_gp.append('        langSection.add(langScroll, BorderLayout.CENTER);')

# --- Section: Network (AE Title / Port - HOROS - NSPreferencePane+DCMTK.h) ---
new_gp.append("")  
new_gp.append("        // HOROS-20240407 Reference: AE Title + Network Port (NSUserDefaults key)")
new_gp.append('        JPanel netBox = new JPanel(new GridBagLayout());')
new_gp.append("        GridBagConstraints gbcN = new GridBagConstraints();")
new_gp.append("        gbcN.insets = new Insets(6, 8, 6, 8);")
new_gp.append('        int nRow = 0;')

# Horos has: AE Title management (stored in NSUserDefaults + DCMTK category)
new_gp.append("        netBox.add(new JLabel(\"AE Title:\"), gbcN);")  
new_gp.append("        JTextField aeField = new JTextField(AppPreferences.getListenerAETitle(), 15);")  
new_gp.append("        gbcN.gridx = 1; netBox.add(aeField, gbcN);")

# Horos: Listener port with auto-increment on conflict (controlTextDidEndEditing IBAction)
new_gp.append("")  
new_gp.append("        // HOROS-20240407 Reference: controlTextDidEndEditing - auto increment if collision")
new_gp.append("        nRow++; gbcN.gridx = 0; gbcN.gridy = ++nRow; netBox.add(new JLabel(\"Network Port:\"), gbcN);")  
new_gp.append("        JTextField portField = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 5);")
new_gp.append("""        portField.setToolTipText("HOROS auto-increments on collision (controlTextDidEndEditing)");""")
new_gp.append('        gbcN.gridx = 1; netBox.add(portField, gbcN);')

# --- Section: JP2K Engine + Compression Settings (Horos - OSIGeneral.m ~16KB) ---  
new_gp.append("")  
new_gp.append("        // HOROS-20240407 Reference: setJP2KWriter:, isKDUEngineAvailable(), editCompressionSettings IBAction")
new_gp.append('        JPanel jp2kPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));')
new_gp.append('        JLabel engineLbl = new JLabel("JPEG 2000 Engine: " + AppPreferences.getJP2KEngine());')
new_gp.append('        jp2kPanel.add(engineLbl);')  
new_gp.append("        JButton btnEditComp = new JButton(\"Edit Compression Settings...\");")
new_gp.append("""        btnEditComp.setToolTipText("HOROS-20240407: open compressionSettingsWindow sheet (editCompressionSettings IBAction)");""")

# --- Section: Reset Preferences (Horos - resetPreferences IBAction) ---  
new_gp.append("")
new_gp.append('        // HOROS-20240407 Reference: resetPreferences - delete all NSUserDefaults keys')
new_gp.append('        JPanel resetBox = new JPanel(new FlowLayout(FlowLayout.LEFT));')
new_gp.append("        JButton btnReset = new JButton(\"Reset All Preferences to Defaults...\");")  
new_gp.append("""        btnReset.setToolTipText("HOROS-20240407: resetPreferences - delete all prefs, show alert");""") 
new_gp.append('        btnReset.addActionListener(e -> {');
new_gp.append("            int result = JOptionPane.showConfirmDialog(PreferencesController.this, " + "\"Are you sure you want to reset ALL preferences? This cannot be undone.\", \"Reset Preferences\", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);")
new_gp.append("            if (result == JOptionPane.YES_OPTION) {")  
new_gp.append('                java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userRoot().node("com.oculus.jj");')
new_gp.append('                for (String k : prefs.keys()) { prefs.remove(k); }')
new_gp.append("""                JOptionPane.showMessageDialog(PreferencesController.this, "All preferences have been reset to defaults.", "Reset Complete", JOptionPane.INFORMATION_MESSAGE);""")  
new_gp.append("            }")
new_gp.append("        });")
new_gp.append('        resetBox.add(btnReset);')

# --- Assemble (Horos layout: BoxLayout vertical) ---  
new_gp.append("")
new_gp.append('        JPanel topPanel = new JPanel();')
new_gp.append('        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));')
new_gp.append("        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));")
new_gp.append("        topPanel.add(interfaceBox);")
new_gp.append("        topPanel.add(Box.createVerticalStrut(12));")
new_gp.append("        topPanel.add(studiesBox);")  
new_gp.append("        topPanel.add(Box.createVerticalStrut(12));")
new_gp.append("        topPanel.add(langSection);")
new_gp.append("        topPanel.add(Box.createVerticalStrut(12));")  
new_gp.append("        topPanel.add(netBox);")
new_gp.append("        topPanel.add(Box.createVerticalStrut(10));")
new_gp.append('        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));')
new_gp.append('        bottomRow.add(jp2kPanel);')
new_gp.append('        bottomRow.add(resetBox);')
new_gp.append("        topPanel.add(bottomRow);")  
new_gp.append('        contentPanel.add(new JScrollPane(topPanel), "General");')

# Add closing brace - note: the closing } goes at index 396+0 (start position in new file)  
gp_close = ["    } // end createGeneralPane()"]
new_gp.extend(gp_close)
print("Built General pane: %d lines" % len(new_gp))

# ===== Replace createDatabasePane (lines 547-845 = indices 546-844 in original) =====
new_db = []
new_db.append("    // === HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m (~25KB .m, second largest pane)")
new_db.append('    private void createDatabasePane() {')
new_db.append("        JPanel pane = new JPanel(new GridBagLayout());")
new_db.append("        GridBagConstraints gbc = new GridBagConstraints();")  
new_db.append("        gbc.insets = new Insets(6, 8, 6, 8);")
new_db.append('        gbc.anchor = GridBagConstraints.WEST;')
new_db.append("        gbc.fill = GridBagConstraints.HORIZONTAL;")
new_db.append('        int row = 0;')
new_db.append("")

# --- Section: Folder paths (Horos - DatabaseFolder + DICOMFilesStoragePath) ---  
new_db.append('        // HOROS-20240407 Reference: DBLocation + Storage Path settings')
new_db.append("        row++; gbc.gridx = 0; gbc.gridy = row++;")
new_db.append("""        pane.add(new JLabel("Database Folder:"), gbc);""")  
new_db.append('        JTextField dbPathField = new JTextField(AppPreferences.getDatabaseFolder(), 25);')
new_db.append("        dbPathField.setEditable(false);")
new_db.append("        gbc.gridx = 1; pane.add(dbPathField, gbc);")  
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
new_db.append("        gbc.gridx = 2; pane.add(browseDbBtn, gbc);")

# --- Section: UID Generation Method (Horos - newUsePatientNameForUID etc.) ---
new_db.append("")
new_db.append('        // HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m newUsePatientNameForUID etc.')  
new_db.append("        JPanel uidPanel = new JPanel(new GridBagLayout());")
new_db.append("        GridBagConstraints gcb = new GridBagConstraints();")
new_db.append("        gcb.insets = new Insets(4, 8, 4, 8);")
new_db.append('        gcb.anchor = GridBagConstraints.WEST;')

# Horos has radio buttons for UID generation method: PatientName/BirthDate  
new_db.append('        row++; gcb.gridx = 0; gcb.gridy = ++row; uidPanel.add(new JLabel("UID Generation Method:"), gcb);')
new_db.append("""        JRadioButton radioName = new JRadioButton(\"Use Patient Name\");""")  
new_db.append("""        JRadioButton radioBirthDate = new JRadioButton(\"Use Patient Birth Date\");""")
new_db.append('        ButtonGroup uidGroup = new ButtonGroup();')
new_db.append("        uidGroup.add(radioName); uidGroup.add(radioBirthDate);")
new_db.append("        JPanel uidRadioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));")
new_db.append("        uidRadioPanel.add(radioName);")
new_db.append("        uidRadioPanel.add(radioBirthDate);")  
new_db.append('        gcb.gridx = 1; uidPanel.add(uidRadioPanel, gcb);')

# --- Section: Auto-Cleaning (Horos - AUTOCLEANINGDATEPRODUCED/OCTOBER/COMMENTS) ---
new_db.append("")
new_db.append("        // HOROS-20240407 Reference: Automatic cleaning of old studies")
new_db.append('        JPanel cleanPanel = new JPanel(new GridBagLayout());')  
new_db.append("        GridBagConstraints gbcC = new GridBagConstraints();")
new_db.append("        gbcC.insets = new Insets(4, 8, 4, 8);")
new_db.append("        gbcC.anchor = GridBagConstraints.WEST;")
new_db.append('        int cRow = 0;')

# Horos has multiple auto-clean date types  
new_db.append("""        row++; gcb.gridx = 0; gcb.gridy = ++row; pane.add(new JLabel(\"Remove studies produced more than:\"), gcb);""")
new_db.append("        JTextField daysField = new JTextField(AppPreferences.getAutoCleanDaysToKeep(), 5);")  
new_db.append('        gbcC.gridx = 1; cleanPanel.add(daysField, gbcC);')

# Server location (DATABASE/DICOM_FILE/RIS-REMOTE) - Horos has a matrix with cellTag selection  
new_db.append("")
new_db.append("        // HOROS-20240407 Reference: serverLocationMatrix (selectCellWithTag)")
new_db.append('        JPanel serverPanel = new JPanel(new GridBagLayout());')
new_db.append("        GridBagConstraints gbcSv = new GridBagConstraints();")
new_db.append("        gbcSv.insets = new Insets(4, 8, 4, 8);")  
new_db.append('        JRadioButton radioDb = new JRadioButton("DATABASE");')
new_db.append('        JRadioButton radioFile = new JRadioButton("DICOM_FILE");')
new_db.append('        ButtonGroup serverGroup = new ButtonGroup();')
new_db.append("        serverGroup.add(radioDb); serverGroup.add(radioFile);")  
new_db.append('        JPanel srvRadioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));')  
new_db.append("        srvRadioPanel.add(radioDb);")
new_db.append("        srvRadioPanel.add(radioFile);")

# --- Section: Date Formats (Horos - resetDate / resetDateOfBirth IBActions) ---  
new_db.append("")
new_db.append("        // HOROS-20240407 Reference: resetDate + resetDateOfBirth - set NSUserDefaults date formats")
new_db.append('        JPanel fmtPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));')
new_db.append("""        JButton btnResetDate = new JButton(\"Reset Date Format\");""")
new_db.append("        btnResetDate.setToolTipText(\"HOROS: set NSUserDefaults with short style dateFormat for DB display (resetDate IBAction)\");")
new_db.append('        fmtPanel.add(btnResetDate);')

# --- Assemble into scrollable pane ---  
new_db.append("")
new_db.append("        // HOROS-20240407 Reference: Build vertical layout with sections")
new_db.append("        JPanel dbTop = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));")  
new_db.append("        dbTop.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));")  
new_db.append("        dbTop.add(uidPanel);")
new_db.append("        dbTop.add(Box.createVerticalStrut(12));")  
new_db.append("        dbTop.add(cleanPanel);")
new_db.append("        dbTop.add(Box.createVerticalStrut(12));")
new_db.append("        dbTop.add(serverPanel);")  
new_db.append("        dbTop.add(Box.createVerticalStrut(10));")  
new_db.append("        dbTop.add(fmtPanel);")
new_db.append('        contentPanel.add(new JScrollPane(dbTop), "Database");')

# Add closing brace
db_close = ["    } // end createDatabasePane()"]
new_db.extend(db_close)
print("Built Database pane: %d lines" % len(new_db))

# ===== Apply replacements in reverse order (largest index first) =====  
rep_general = (396, 544, new_gp)   # indices in ORIGINAL file
rep_database = (546, 845, new_db)

all_reps = [rep_database, rep_general]  # descending by start index
for start_idx, end_idx, content in all_reps:
    print("Replacing original lines %d-%d with %d new lines" % (start_idx+1, end_idx, len(content)))
    for item in reversed(content):
        L.insert(start_idx, item)

with open(SRC, 'w', encoding='utf-8') as f:
    f.write('\n'.join(L))

print("Final line count:", len(L))
