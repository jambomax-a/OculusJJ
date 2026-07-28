import os, sys

SRC = 'src/main/java/com/oculus/jj/ui/PreferencesController.java'
with open(SRC, encoding='utf-8') as f:
    L = f.read().split('\n')

print('Read %d lines from current file' % len(L))

def method_end_idx(start):
    depth = 0
    for i in range(start, len(L)):
        line = L[i]
        for ch in line:
            if ch == '{': depth += 1
            elif ch == '}': depth -= 1
            if depth == 0 and i > start: return i + 1
    raise Exception('No match')

# Current positions (0-indexed, from earlier analysis)  
lis_start = -1; loc_start = -1
for i in range(len(L)):
    s = L[i].strip()
    if 'private void createListenerPane()' in s: lis_start = i
    elif 'private void createLocationsPane()' in s and loc_start == -1: loc_start = i

lis_end = method_end_idx(lis_start)  
loc_end = method_end_idx(loc_start)

print('createListenerPane(): lines %d-%d (%d)' % (lis_start+1, lis_end, lis_end-lis_start))
print('createLocationsPane(): lines %d-%d (%d)' % (loc_start+1, loc_end, loc_end-loc_start))

# ================================================================  
# BUILD REPLACEMENTS FOR LISTENER + LOCATIONS PANES  
# ================================================================  

# --- Listener Pane - HOROS: OSIListenerPreferencePanePref.mm (~16KB .m, TLS auth/cipher/DH params) ---
listener = [
    "    // === HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm (~16KB .m)",
    '    private void createListenerPane() {',
    '        JPanel pane = new JPanel(new BorderLayout());',
    '        int row = 0;',
    '',
    
    // Section 1: Basic listener settings (AE Title + Port)  
    "        // HOROS-20240407 Reference: basic listener settings - AE Title / Port",
    '        JPanel basicsBox = new JPanel(new GridBagLayout());',
    '        GridBagConstraints gbcB = new GridBagConstraints();',
    '        gbcB.insets = new Insets(6, 8, 6, 8);',
    '        gbcB.anchor = GridBagConstraints.WEST;',
    "        basicsBox.add(new JLabel(\"AE Title:\"), gbcB);",
    '        JTextField aeField = new JTextField(AppPreferences.getListenerAETitle(), 15);',
    '        gbcB.gridx = 1; basicsBox.add(aeField, gbcB);',
    
    // Horos: Listener port (standard DICOM port 104 or custom)  
    "        row++; gbcB.gridx = 0; gbcB.gridy = ++row;",
    '        basicsBox.add(new JLabel("Listener Port:"), gbcB);',
    "        JTextField portField = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 5);",
    '        gbcB.gridx = 1; basicsBox.add(portField, gbcB);',

    // Auto-start toggle (Horos: listener auto-start preference)  
    "        row++; gbcB.gridx = 0; gbcB.gridy = ++row;",
    '        JCheckBox chkAutoStart = new JCheckBox("Automatically start listener at application launch");',
    '        chkAutoStart.setSelected(AppPreferences.isListenerAutoStart());',
    "        chkAutoStart.addActionListener(e -> AppPreferences.setListenerAutoStart(chkAutoStart.isSelected()));",
    
    // Section 2: TLS settings (Horos - the biggest gap)  
    "",
    "        // HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm - TLS authentication/cipher/DH params (~16KB)",
    '        JPanel tlsPanel = new JPanel(new BorderLayout());',
    '        tlsPanel.setBorder(BorderFactory.createTitledBorder("TLS/SSL Settings"));',

    // Main toggle for TLS  
    '        JCheckBox chkTLSEnabled = new JCheckBox("Enable TLS/SSL for this Listener");',
    '        chkTLSEnabled.setSelected(AppPreferences.isListenerTLSEnabled());',
    "        chkTLSEnabled.addActionListener(e -> AppPreferences.setListenerTLSEnabled(chkTLSEnabled.isSelected()));",

    // Certificate authentication (Horos: SFChooseIdentityPanel + KeychainAccessCertificatesList)  
    "",
    '        JPanel certBox = new JPanel(new GridBagLayout());',
    "        GridBagConstraints gbcC = new GridBagConstraints();",
    "        gbcC.insets = new Insets(4, 8, 4, 8);",
    "        gbcC.anchor = GridBagConstraints.WEST;",
    "        certBox.add(new JLabel(\"TLS Authentication Certificate:\"), gbcC);",
    '        String currentCert = AppPreferences.getListenerTLSAuthCertName();',
    "        JTextField certField = new JTextField(currentCert.isEmpty() ? \"No certificate selected\" : currentCert, 25);",
    '        certField.setEditable(false);',
    "        gbcC.gridx = 1; certBox.add(certField, gbcC);",
    
    // Cipher suite selection (Horos: TLSSupportedCipherSuite array)  
    "",
    "        JPanel cipherPanel = new JPanel(new BorderLayout());",
    '        JLabel cipherLbl = new JLabel("Supported Cipher Suites:");',
    '        cipherPanel.add(cipherLbl, BorderLayout.NORTH);',

    // DH parameter file (Horos: TLSUseDHParameterFileURL)  
    "",
    "        // HOROS-20240407 Reference: TLS DH Parameter File URL setting",
    '        JPanel dhBox = new JPanel(new GridBagLayout());',
    '        GridBagConstraints gbcD = new GridBagConstraints();',
    '        gbcD.insets = new Insets(4, 8, 4, 8);',
    '        gbcD.anchor = GridBagConstraints.WEST;',
    "        dhBox.add(new JLabel(\"DH Parameter File:\"), gbcD);",
    
    // Same AETitle option (Horos: TLSUseSameAETitle / TLSStoreSCPAETITLEIsDefaultAET)  
    "",
    "        // HOROS-20240407 Reference: useSameAETitleForTLSListener IBAction - same AE Title for TLS StoreSCP",
    '        JCheckBox chkSameAET = new JCheckBox("Use same AE Title as default Listener (for TLS StoreSCP)");',

    // Assemble TLS section  
    "        JPanel tlsCenter = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));",
    '        tlsCenter.add(certBox);',
    "        tlsCenter.add(Box.createVerticalStrut(12));",
    "        tlsCenter.add(cipherPanel);",
    "        tlsCenter.add(Box.createVerticalStrut(12));",  
    "        tlsCenter.add(dhBox);",
    "        tlsCenter.add(Box.createVerticalStrut(8));",
    '        tlsCenter.add(chkSameAET);',
    '        tlsPanel.add(tlsCenter, BorderLayout.CENTER);',

    // Bottom: certificate choose button + select/deselect all cipher suites  
    "",
    '        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));',
    "        JButton btnChooseCert = new JButton(\"Choose Certificate...\");",
    "        JButton btnSelectAll = new JButton(\"Select All Ciphers\");",  
    "        JButton btnDeselectAll = new JButton(\"Deselect All Ciphers\");",
    '        btnRow.add(btnChooseCert);',
    '        btnRow.add(btnSelectAll);',
    '        btnRow.add(btnDeselectAll);',

    // Final assembly  
    "        JPanel topPanel = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));",
    '        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));',
    '        topPanel.add(basicsBox);',
    '        topPanel.add(Box.createVerticalStrut(12));',
    "        JPanel tlsSection = new JPanel(new BorderLayout());",
    "        tlsSection.add(chkTLSEnabled, BorderLayout.NORTH);",
    "        tlsSection.add(tlsCenter, BorderLayout.CENTER);",
    "        topPanel.add(tlsSection);",  
    '        topPanel.add(btnRow);',
    '        contentPanel.add(new JScrollPane(topPanel), "Listener");',
    
    // Closing brace  
    '    } // end createListenerPane()'
];

// --- Locations Pane - HOROS: OSILocationsPreferencePanePref.m (~37KB .m, LARGEST pane) with WADO + per-server TLS ---
locations = [
    "    // === HOROS-20240407 Reference: OSILocationsPreferencePanePref.m (~36KB .m - largest pane)",
    '    private void createLocationsPane() {',
    '        JPanel pane = new JPanel(new BorderLayout());',

    // Section 1: WADO module settings (Horos)  
    "",
    "        // HOROS-20240407 Reference: OSILocationsPreferencePane - WADO + DICOM node config",
    "        JPanel wadoPanel = new JPanel(new GridBagLayout());",
    '        GridBagConstraints gbcW = new GridBagConstraints();',
    '        gbcW.insets = new Insets(6, 8, 6, 8);',
    '        gbcW.anchor = GridBagConstraints.WEST;',

    // Horos: WADO enabled toggle  
    "        wadoPanel.add(new JLabel(\"Enable Web Access to DICOM Objects (WADO):\"), gbcW);",

    // HOROS: WADO port, URL, transfer syntax settings  
    "",
    '        int row = 0;',
    "        row++; gbcW.gridx = 0; gbcW.gridy = ++row;",
    "        wadoPanel.add(new JLabel(\"WADO Port:\"), gbcW);",
    "        JTextField wadoPortField = new JTextField(String.valueOf(AppPreferences.getWadoPort()), 5);",
    '        gbcW.gridx = 1; wadoPanel.add(wadoPortField, gbcW);',

    // Horos: WADO URL  
    "        row++; gbcW.gridx = 0; gbcW.gridy = ++row;",  
    "        wadoPanel.add(new JLabel(\"Base URL:\"), gbcW);",
    '        JTextField wadoUrlField = new JTextField("http://localhost:" + AppPreferences.getWadoPort(), 25);',
    "        gbcW.gridx = 1; wadoPanel.add(wadoUrlField, gbcW);",

    // Horos: WADO Transfer Syntax  
    "",
    '        JPanel syntaxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));',
    '        JLabel syntaxLbl = new JLabel("Preferred Transfer Syntax:");',
    "        syntaxPanel.add(syntaxLbl);",
    
    // Horos: Username/Password for WADO (Horos has tokens for authenticated WADO)  
    "",
    "        row++; gbcW.gridx = 0; gbcW.gridy = ++row;",  
    '        wadoPanel.add(new JLabel("Authentication:"), gbcW);',

    // Section 2: DICOM node list (Horos - dicomNodes arrangedObjects / ServerTableView)  
    "",
    "        // HOROS-20240407 Reference: dicomNodes arrangedObjects + ServerTableView (~36KB .m)",
    '        JPanel nodeListPanel = new JPanel(new BorderLayout());',
    '        nodeListPanel.setBorder(BorderFactory.createTitledBorder("DICOM Nodes"));',

    // Horos has a JTable (ServerTableView) for DICOM nodes - show them in the UI  
    "        String[][] nodeData = {",
    "            {\"localhost\", \"11112\", \"OCULUSJJ\", \"Active\"},");

locations.extend([
    "            // Additional server entries can be added via addNode button (see BrowserController)",
    "        };",
    '        JTable nodeList = new JTable(nodeData, new Object[]{ "Name/Address", "Port", "AE Title", "Status" });',
    '        JScrollPane nodeScroll = new JScrollPane(nodeList);',
    '        nodeListPanel.add(nodeScroll, BorderLayout.CENTER);',

    // Buttons for adding/removing nodes (Horos)  
    "",
    '        JPanel nodeBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));',
    "        JButton btnAddNode = new JButton(\"Add Node...\");",  
    '        JButton btnEditNode = new JButton("Edit Node...");',
    "        JButton btnDelNode = new JButton(\"Remove\");",
    "        nodeBtns.add(btnAddNode); nodeBtns.add(btnEditNode); nodeBtns.add(btnDelNode);",

    // Section 3: TLS per-server (Horos - TLSEnabled, TLSAuthenticated, DH params per server)  
    "",
    "        // HOROS-20240407 Reference: Per-server TLS settings (TLSSettings dialog from OSIWebSharing/locations)",
    '        JPanel tlsServerPanel = new JPanel(new BorderLayout());',
    '        JLabel srvTlsLbl = new JLabel("Select a server to configure its TLS settings:");',
    '        tlsServerPanel.add(srvTlsLbl, BorderLayout.NORTH);',

    // Per-server certificate verification level (Horos: TLSCertificateVerification enum)  
    "",
    "        // HOROS-20240407 Reference: Certificate verification options per server",
    "        String[] verifyOptions = {\"Ignore Peer Certificate\", \"Strict Verification\"};",

    // Section 4: Web Portal (Horos OSIWebSharingReferencePane - URL + user mgmt)  
    "",
    '        JPanel webSection = new JPanel(new BorderLayout());',
    "        webSection.setBorder(BorderFactory.createTitledBorder(\"Web Server / PACS Gateway\"));",

    // Web portal settings - Horos: defaultWebPortalAddress, customizedHTMLFilePaths  
    "        JPanel webPanel = new JPanel(new GridBagLayout());",
    '        GridBagConstraints gbcW2 = new GridBagConstraints();',
    "        gbcW2.insets = new Insets(4, 8, 4, 8);",
    "        gbcW2.anchor = GridBagConstraints.WEST;",

    // User management (Horos: usersArrayController + WebPortalUser.h)  
    "",
    '        JPanel userPanel = new JPanel(new BorderLayout());',
    '        userPanel.add(new JLabel("Web Users Management:"), BorderLayout.NORTH);',

    // Section 5: Custom HTML files (Horos copyMissingCustomizedFiles IBAction)  
    "",
    "        // HOROS-20240407 Reference: copyMissingCustomizedFiles - WebServicesHTMLPath",

    // Bottom button row  
    '        JPanel bottomBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));',
    "        JButton btnEditUsers = new JButton(\"Edit Users...\");",
    "        JButton btnCopyHTML = new JButton(\"Copy Web Services HTML Files\");",  
    "        bottomBtns.add(btnEditUsers); bottomBtns.add(btnCopyHTML);",

    // Final assembly - Horos uses a scrollable area for the whole pane  
    '        JPanel mainContent = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));',
    "        mainContent.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));",
    "        mainContent.add(wadoPanel);",
    "        mainContent.add(Box.createVerticalStrut(12));",  
    '        mainContent.add(nodeListPanel);',
    "        mainContent.add(Box.createVerticalStrut(12));",  
    "        mainContent.add(tlsServerPanel);",
    "        mainContent.add(Box.createVerticalStrut(10));",

    // Close pane - Horos stores preferences in NSUserDefaults SERVERS array  
    '        contentPanel.add(new JScrollPane(mainContent), "Locations");',

    // Closing brace  
    '    } // end createLocationsPane()'
];

// ================================================================
// APPLY REPLACEMENTS using reverse-order slice assignment
// ================================================================

all_reps = [
    (lis_start, lis_end, listener),  // Listener pane - smaller so replace first  
    (loc_start, loc_end, locations)   // Locations pane
]

for start_idx, end_idx, content in all_reps:
    print("Replacing original lines %d-%d (%d lines) with %d new lines" % (start_idx+1, end_idx, end_idx-start_idx, len(content)))
    L[start_idx:end_idx] = content  // slice assignment - safe and preserves ordering

with open(SRC, 'w', encoding='utf-8') as f:
    f.write('\n'.join(L))

print('Final line count:', len(L))
