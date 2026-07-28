import os

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

loc_start = -1
for i in range(len(L)):
    s = L[i].strip()
    if 'private void createLocationsPane()' in s: loc_start = i; break

loc_end = method_end_idx(loc_start)
print('createLocationsPane(): lines %d-%d (%d)' % (loc_start+1, loc_end, loc_end-loc_start))

# Build Locations pane replacement content  
loc = []
loc.append('    // === HOROS-20240407 Reference: OSILocationsPreferencePanePref.m (~36KB .m - largest pane)')
loc.append("    private void createLocationsPane() {")
loc.append('        JPanel pane = new JPanel(new BorderLayout());')

# Section 1: WADO settings (Horos)  
loc.append("")
loc.append("        // HOROS-20240407 Reference: OSILocationsPreferencePane - WADO module + DICOM node config")
loc.append('        JPanel wadoPanel = new JPanel(new GridBagLayout());')
loc.append('        GridBagConstraints gbcW = new GridBagConstraints();')
loc.append("        gbcW.insets = new Insets(6, 8, 6, 8);")
loc.append('        gbcW.anchor = GridBagConstraints.WEST;')

# WADO enable toggle (Horos: wadomoduleportnumber NSUserDefaults key)
loc.append("")
loc.append("        wadoPanel.add(new JLabel(\"Enable Web Access to DICOM Objects (WADO):\"), gbcW);")

// Horos: WADO port + base URL + transfer syntax  
""", ["", "        int row = 0;", '        wadoPanel.add(new JLabel("WADO Port:"), gbcW);',
      "        JTextField wadoPortField = new JTextField(String.valueOf(AppPreferences.getWadoPort()), 5);"]

loc.extend([
    '        gbcW.gridx = 1; wadoPanel.add(wadoPortField, gbcW);',
    "",
    '        row++; gbcW.gridx = 0; gbcW.gridy = ++row;',
    "        wadoPanel.add(new JLabel(\"Base URL:\"), gbcW);",
    '        JTextField wadoUrlField = new JTextField("http://localhost:" + AppPreferences.getWadoPort(), 25);',
    "        gbcW.gridx = 1; wadoPanel.add(wadoUrlField, gbcW);",
    "",
    // Horos WADO transfer syntax  
    '        JPanel syntaxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));',
    "        JLabel syntaxLbl = new JLabel(\"Preferred Transfer Syntax: Explicit Little Endian\");",
    "        syntaxPanel.add(syntaxLbl);",
    "        gbcW.gridx = 0; gbcW.gridy = ++row;",
    '        gbcW.gridwidth = 2;',
    "        wadoPanel.add(syntaxPanel, gbcW);",

    // Section 2: DICOM node list (Horos - dicomNodes arrangedObjects / ServerTableView)  
    "",
    "        // HOROS-20240407 Reference: dicomNodes arrangedObjects + ServerTableView (~36KB .m)",
    '        JPanel nodeListPanel = new JPanel(new BorderLayout());',
    '        nodeListPanel.setBorder(BorderFactory.createTitledBorder("DICOM Nodes"));',

    // Horos JTable showing all DICOM nodes  
    "        String[][] nodeData = {",
    '            {"localhost:11112", "OCULUSJJ", "Active"},');

loc.extend([
    "            // Additional entries from DicomNodeManager can be added via BrowserController.addDicomNode()",
    "        };",
    '        JTable nodeList = new JTable(nodeData, new Object[]{"Address:Port", "AE Title", "Status"});',
    '        JScrollPane nodeScroll = new JScrollPane(nodeList);',
    '        nodeListPanel.add(nodeScroll, BorderLayout.CENTER);',

    // Add/edit/remove buttons (Horos)  
    "",
    '        JPanel nodeBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));',
    "        JButton btnAddNode = new JButton(\"Add Node...\");",  
    '        JButton btnEditNode = new JButton("Edit Node...");',
    "        JButton btnDelNode = new JButton(\"Remove\");",
    "        nodeBtns.add(btnAddNode); nodeBtns.add(btnEditNode); nodeBtns.add(btnDelNode);",

    // Section 3: TLS per-server (Horos - the biggest gap from OSILocations.m)  
    "",
    "        // HOROS-20240407 Reference: Per-server TLS settings (TLSSettings dialog)",
    '        JPanel tlsServerPanel = new JPanel(new BorderLayout());',
    '        JLabel srvTlsLbl = new JLabel("Configure TLS for selected server:");',
    '        tlsServerPanel.add(srvTlsLbl, BorderLayout.NORTH);',

    // Certificate verification level (Horos: TLSCertificateVerification enum)  
    "",
    "        // HOROS-20240407 Reference: Per-server certificate verification",
    '        String[] verifyOptions = {"Ignore Peer Certificate", "Strict Verification"};',

    // Section 4: Web Portal settings (Horos OSIWebSharingPreferencePane - URL + user mgmt)  
    "",
    '        JPanel webSection = new JPanel(new BorderLayout());',
    "        webSection.setBorder(BorderFactory.createTitledBorder(\"Web Server / PACS Gateway\"));",

    // Web portal URL / customized HTML path (Horos: defaultWebPortalAddress, WebServicesCustomHTMLPath)  
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

    // Final assembly - Horos uses scrollable area for whole pane  
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
]);

// Apply replacement (reverse order to avoid index shifting)
print("Replacing original lines %d-%d (%d lines)" % (loc_start+1, loc_end, loc_end-loc_start))
L[loc_start:loc_end] = loc

with open(SRC, 'w', encoding='utf-8') as f:
    f.write('\n'.join(L))

print('Final line count:', len(L))
