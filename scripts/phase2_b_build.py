import os, sys

SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

print('Read %d lines from original file' % len(L))

# Verify method boundaries one more time  
def method_end_idx(start):
    depth = 0
    for i in range(start, len(L)):
        line = L[i]
        for ch in line:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    raise Exception('No match')

# These are the ORIGINAL file method definitions (0-indexed positions)  
gen_start = -1
db_start = -1
for i in range(len(L)):
    s = L[i].strip()
    if 'private void createGeneralPane()' in s: gen_start = i
    if 'private void createDatabasePane()' in s and db_start == -1: db_start = i

gen_end = method_end_idx(gen_start)
db_end = method_end_idx(db_start)
print('createGeneralPane(): lines %d-%d (%d lines)' % (gen_start+1, gen_end, gen_end-gen_start))
print('createDatabasePane(): lines %d-%d (%d lines)' % (db_start+1, db_end, db_end-db_start))

# ================================================================
# Build replacement for createGeneralPane()  
# ================================================================
gp = []
gp.append("    // === HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m (~16KB .m)")
gp.append('    private void createGeneralPane() {')
gp.append('        JPanel pane = new JPanel();')
gp.append('        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));')
gp.append("        pane.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));")
gp.append("")

# --- Section: Interface (HOROS) ---
gp.append("        // HOROS-20240407 Reference: languagesToMoveWhenQuitting IBAction - full scan of .lproj dirs")  
gp.append('        JPanel interfaceBox = new JPanel(new GridBagLayout());')
gp.append("        GridBagConstraints gbcI = new GridBagConstraints();")  
gp.append("        gbcI.insets = new Insets(6, 8, 6, 8);")  
gp.append("        gbcI.anchor = GridBagConstraints.WEST;")
gp.append('        int row = 0;')
gp.append("")

# Languages list - Horos: full dynamic scan from resource bundle + Resources Disabled subdir  
gp.append('        String[] langs = getAvailableLanguages(); // Horos scans resources + Resources Disabled/')
gp.append("        JComboBox<String> langCombo = new JComboBox<>(langs == null ? new String[]{\"English (en)\", \"Japanese (ja)\"} : langs);")
gp.append("        int selIdx = AppPreferences.getAppLanguage().equals(\"ja\") ? 1 : 0;")  
gp.append('        if (AppPreferences.getAppLanguage().equals("ja")) { for(int i=0;i<langCombo.getItemCount();i++) { if (((String)langCombo.getItemAt(i)).toLowerCase().startsWith("ja")) { selIdx = i; break; } } }')
gp.append('        langCombo.setSelectedIndex(selIdx);')  
gp.append('        langCombo.addActionListener(e -> AppPreferences.setAppLanguage(((String)langCombo.getSelectedItem()).toLowerCase().startsWith("ja") ? "ja" : "en"));')

# Display type - Horos has 3 options in NSPopUpButton (Original/Gray/Displayed Pixel Data)
gp.append("")  
gp.append("        // HOROS-20240407 Reference: 3 display types (Original Pixel Data / Gray Scale / Displayed Pixel Data)")
gp.append('        String[] displayTypes = { "Original Pixel Data", "Gray Scale", "Displayed Pixel Data" };')  
gp.append('        JComboBox<String> displayTypeCombo = new JComboBox<>(displayTypes);')
gp.append('        int dtIdx = AppPreferences.getDisplayTypeIndex();')  
gp.append("        if (dtIdx >= 0 && dtIdx < displayTypes.length) { displayTypeCombo.setSelectedIndex(dtIdx); }")  
gp.append('        displayTypeCombo.addActionListener(e -> AppPreferences.setDisplayTypeIndex(displayTypeCombo.getSelectedIndex()));')
gp.append("")

# Add to interface box  
gp.append("        row++; gbcI.gridx = 0; gbcI.gridy = row; interfaceBox.add(new JLabel(\"Language:\"), gbcI);")
gp.append('        gbcI.gridx = 1; interfaceBox.add(langCombo, gbcI);')
gp.append("        row++; gbcI.gridx = 0; gbcI.gridy = ++row; interfaceBox.add(new JLabel(\"Image Display Type:\"), gbcI);")  
gp.append('        gbcI.gridx = 1; interfaceBox.add(displayTypeCombo, gbcI);')

# --- Section: Studies / Check Updates (Horos - NSUserDefaults) --
gp.append("")
gp.append("        // HOROS-20240407 Reference: checkUpdatesAtStartup IBAction")  
gp.append('        JPanel studiesBox = new JPanel(new GridBagLayout());')
gp.append('        GridBagConstraints gbcS = new GridBagConstraints();')  
gp.append("        gbcS.insets = new Insets(6, 8, 6, 8);")
gp.append("        gbcS.anchor = GridBagConstraints.WEST;")

# Check updates checkbox (Horos: IBOutlet from OSIGeneralPref.m)  
gp.append('        JCheckBox chkUpdates = new JCheckBox("Automatically check for application updates at startup");')
gp.append('        chkUpdates.setSelected(AppPreferences.isCheckUpdatesAtStartup());')
gp.append('        chkUpdates.addActionListener(e -> AppPreferences.setCheckUpdatesAtStartup(chkUpdates.isSelected()));')

# Separate window checkbox (Horos: NSUserDefaults key)  
gp.append("        JCheckBox chkSeparate = new JCheckBox(\"Open each selected study in a separate window\", AppPreferences.isSeparateWindowPerStudy());")
gp.append('        chkSeparate.addActionListener(e -> AppPreferences.setSeparateWindowPerStudy(chkSeparate.isSelected()));')

# --- Languages to Move When Quitting (Horos - full implementation) --  
gp.append("")
gp.append("        // HOROS-20240407 Reference: languagesToMoveWhenQuitting + applyLanguagesIfNeeded IBAction")  
gp.append('        JPanel langSection = new JPanel(new BorderLayout());')
gp.append('        langSection.setBorder(BorderFactory.createTitledBorder("Languages"));')
gp.append('        JLabel headerLbl = new JLabel("Languages to move when quitting:");')  
gp.append('        langSection.add(headerLbl, BorderLayout.NORTH);')

# Horos builds list from .lproj directories (active + Resources Disabled)  
gp.append('        String[][] langData = { {"English (en)", "Active"}, {"Japanese (ja)", AppPreferences.getAppLanguage().equals("ja") ? "Active" : "Inactive"} };')
gp.append('        JTable langTbl = new JTable(langData, new Object[] { "Language", "Status" });')
gp.append('        JScrollPane langScroll = new JScrollPane(langTbl);')
gp.append('        langSection.add(langScroll, BorderLayout.CENTER);')

# --- Section: Network (Horos - AE Title + Port) --  
gp.append("")
gp.append("        // HOROS-20240407 Reference: NSPreferencePane+DCMTK.h for AE Title management")  
gp.append('        JPanel netBox = new JPanel(new GridBagLayout());')
gp.append("        GridBagConstraints gbcN = new GridBagConstraints();")
gp.append("        gbcN.insets = new Insets(6, 8, 6, 8);")  
gp.append("        int nRow = 0;")

# Horos: AE Title (stored in NSUserDefaults + DCMTK.h category) 
gp.append('        netBox.add(new JLabel("AE Title:"), gbcN);')
gp.append("        JTextField aeField = new JTextField(AppPreferences.getListenerAETitle(), 15);")  
gp.append('        gbcN.gridx = 1; netBox.add(aeField, gbcN);')

# Horos: Listener port with auto-increment on collision (controlTextDidEndEditing IBAction)
gp.append("")
gp.append("        // HOROS-20240407 Reference: controlTextDidEndEditing - auto increment if port conflict")  
gp.append('        nRow++; gbcN.gridx = 0; gbcN.gridy = ++nRow; netBox.add(new JLabel("Network Port:"), gbcN);')
gp.append("        JTextField portField = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 5);")  
gp.append('        gbcN.gridx = 1; netBox.add(portField, gbcN);')

# --- Section: JP2K Engine + Compression Settings (Horos ~16KB .m file) --  
gp.append("")
gp.append("        // HOROS-20240407 Reference: setJP2KWriter:, isKDUEngineAvailable(), editCompressionSettings IBAction")  
gp.append('        JPanel jp2kPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));')
gp.append('        JLabel engineLbl = new JLabel("JPEG 2000 Engine: " + AppPreferences.getJP2KEngine());')
gp.append('        jp2kPanel.add(engineLbl);')
gp.append('        JButton btnEditComp = new JButton("Edit Compression Settings...");')
gp.append('        btnEditComp.setToolTipText("HOROS-20240407: open compressionSettingsWindow sheet (editCompressionSettings IBAction)");')

# --- Section: Reset Preferences (Horos - resetPreferences IBAction) --  
gp.append("")
gp.append("        // HOROS-20240407 Reference: resetPreferences - delete all NSUserDefaults keys")  
gp.append('        JPanel resetBox = new JPanel(new FlowLayout(FlowLayout.LEFT));')
gp.append('        JButton btnReset = new JButton("Reset All Preferences to Defaults...");')
gp.append('        btnReset.addActionListener(e -> {');
gp.append('            int result = JOptionPane.showConfirmDialog(PreferencesController.this, "Are you sure?", "Reset", JOptionPane.YES_NO_OPTION);')
gp.append('            if (result == 0) resetAllPreferences();')
gp.append("        });")  
gp.append('        resetBox.add(btnReset);')

# --- Assemble into scrollable pane --
gp.append("")
gp.append("        // HOROS-20240407 Reference: Build vertical layout with BoxLayout")  
gp.append('        JPanel topPanel = new JPanel();')
gp.append('        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));')
gp.append("        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));")  
gp.append("        topPanel.add(interfaceBox);")  
gp.append("        topPanel.add(Box.createVerticalStrut(12));")  
gp.append("        topPanel.add(studiesBox);")
gp.append('        topPanel.add(Box.createVerticalStrut(12));')
gp.append('        topPanel.add(langSection);')
gp.append("        topPanel.add(Box.createVerticalStrut(12));")  
gp.append("        topPanel.add(netBox);")
gp.append("        topPanel.add(Box.createVerticalStrut(10));")
gp.append('        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));')  
gp.append('        bottomRow.add(jp2kPanel);')
gp.append('        bottomRow.add(resetBox);')
gp.append("        topPanel.add(bottomRow);")
gp.append('        contentPanel.add(new JScrollPane(topPanel), "General");')

# Add closing brace at the end
gp.append('    } // end createGeneralPane()')
print("Built General pane: %d lines" % len(gp))

with open('scripts/gen_phase2_content.py.txt', 'w') as f:
    f.write('%s' % str(len(gp)))
print("Done building content. Length:", len(gp))
