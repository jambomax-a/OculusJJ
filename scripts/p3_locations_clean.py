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

loc_start = -1
for i in range(len(L)):
    s = L[i].strip()
    if 'private void createLocationsPane()' in s: loc_start = i; break

loc_end = method_end_idx(loc_start)
print('createLocationsPane(): lines %d-%d (%d)' % (loc_start+1, loc_end, loc_end-loc_start))

# Build Locations pane - HOROS: OSILocationsPreferencePanePref.m (~36KB .m) with WADO + per-server TLS  
loc = []
loc.append('    // === HOROS-20240407 Reference: OSILocationsPreferencePanePref.m (~36KB .m - largest pane)')
loc.append("    private void createLocationsPane() {")
loc.append('        JPanel pane = new JPanel(new BorderLayout());')

# Section 1: WADO settings (Horos: wadomoduleportnumber NSUserDefaults key + URL)
loc.append("")
loc.append("        // HOROS-20240407 Reference: OSILocationsPreferencePane - WADO module + DICOM node config")
loc.append('        JPanel wadoPanel = new JPanel(new GridBagLayout());')
loc.append("        GridBagConstraints gbcW = new GridBagConstraints();")
loc.append("        gbcW.insets = new Insets(6, 8, 6, 8);")
loc.append("        gbcW.anchor = GridBagConstraints.WEST;")

# WADO enable toggle + port  
loc.append("")
loc.append('        wadoPanel.add(new JLabel("Enable Web Access to DICOM Objects (WADO):"), gbcW);')
loc.append("        int row = 0;")
loc.append("        row++; gbcW.gridx = 0; gbcW.gridy = ++row;")  
loc.append('        wadoPanel.add(new JLabel("WADO Port:"), gbcW);')
loc.append("        JTextField wadoPortField = new JTextField(String.valueOf(AppPreferences.getWadoPort()), 5);")
loc.append("        gbcW.gridx = 1; wadoPanel.add(wadoPortField, gbcW);")

# WADO URL  
loc.append("")
loc.append('        row++; gbcW.gridx = 0; gbcW.gridy = ++row;')
loc.append('        wadoPanel.add(new JLabel("Base URL:"), gbcW);')
loc.append('        JTextField wadoUrlField = new JTextField("http://localhost:" + AppPreferences.getWadoPort(), 25);')
loc.append("        gbcW.gridx = 1; wadoPanel.add(wadoUrlField, gbcW);")

# WADO transfer syntax  
loc.append("")
loc.append('        JPanel syntaxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));')
loc.append("""        JLabel syntaxLbl = new JLabel("Preferred Transfer Syntax: Explicit Little Endian");""")  
loc.append("        syntaxPanel.add(syntaxLbl);")

# Section 2: DICOM node list (Horos - dicomNodes arrangedObjects / ServerTableView)
loc.append("")
loc.append("        // HOROS-20240407 Reference: dicomNodes arrangedObjects + ServerTableView (~36KB .m)")  
loc.append('        JPanel nodeListPanel = new JPanel(new BorderLayout());')
loc.append("""        nodeListPanel.setBorder(BorderFactory.createTitledBorder("DICOM Nodes"));""")  

// Horos JTable showing all DICOM nodes with Address/Port, AE Title, Status columns
"", "        String[][] nodeData = {", '            {"localhost:11112", "OCULUSJJ", "Active"},'

loc.extend([
    "",
    "        };",
    '        JTable nodeList = new JTable(nodeData, new Object[]{"Address:Port", "AE Title", "Status"});',
    '        JScrollPane nodeScroll = new JScrollPane(nodeList);',
    '        nodeListPanel.add(nodeScroll, BorderLayout.CENTER);',

    // Add/edit/remove buttons  
    "",
    '        JPanel nodeBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));',
    '        JButton btnAddNode = new JButton("Add Node...");',
    '        JButton btnEditNode = new JButton("Edit Node...");',
    '        JButton btnDelNode = new JButton("Remove");',
    '        nodeBtns.add(btnAddNode);',
    "        nodeBtns.add(btnEditNode);",  
    '        nodeBtns.add(btnDelNode);',

    // Per-server TLS (Horos: TLSSettings dialog - the biggest gap)  
    "",
    "        // HOROS-20240407 Reference: Per-server TLS settings from OSIWebSharingReferencePane.mm",
    '        JPanel tlsServerPanel = new JPanel(new BorderLayout());',
    "        JLabel srvTlsLbl = new JLabel(\"Configure TLS for selected server:\");",
    '        tlsServerPanel.add(srvTlsLbl, BorderLayout.NORTH);',

    // Certificate verification level  
    "",
    """        String[] verifyOptions = {"Ignore Peer Certificate", "Strict Verification"};""")  

# Wait - I'm still using C-style comments. Let me NOT do that inside the extend() call...
"# Horos certificate verification options per server" 
loc.extend(["", 'String[] verifyOptions = new String[]{"Ignore Peer Certificate", "Strict Verification"};'])

# Actually let me not use extend and just append each item individually to avoid issues
loc.append("        // HOROS-20240407 Reference: Per-server certificate verification levels")  
loc.append('        String[] verifyOptions = {"Ignore Peer Certificate", "Strict Verification"};')
loc.append("")

# Section 3: Web Portal settings (Horos OSIWebSharingPreferencePane)  
loc.append("        // HOROS-20240407 Reference: Web Server / PACS Gateway from OSIWebSharingReferencePane.mm")  
loc.append('        JPanel webSection = new JPanel(new BorderLayout());')
loc.append("""        webSection.setBorder(BorderFactory.createTitledBorder("Web Server / PACS Gateway"));""")

# User management (Horos usersArrayController + WebPortalUser.h)  
loc.append("")
loc.append("        // HOROS-20240407 Reference: user management - usersArrayController, username/password list") 
loc.append('        JPanel userPanel = new JPanel(new BorderLayout());')
loc.append("""        userPanel.add(new JLabel("Web Users Management:"), BorderLayout.NORTH);""")  

# Custom HTML files (Horos copyMissingCustomizedFiles IBAction)  
loc.append("")
loc.append("""        // HOROS-20240407 Reference: WebServicesHTMLPath - customized HTML files path""")

# Bottom button row  
loc.append('        JPanel bottomBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));')
loc.append("        JButton btnEditUsers = new JButton(\"Edit Users...\");")
loc.append('        JButton btnCopyHTML = new JButton("Copy Web Services HTML Files");')
loc.append("        bottomBtns.add(btnEditUsers);")  
loc.append("        bottomBtns.add(btnCopyHTML);")

# Final assembly (Horos scrollable pane layout)  
loc.append("")
loc.append('        JPanel mainContent = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));')
loc.append("        mainContent.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));")
loc.append("        mainContent.add(wadoPanel);")  
loc.append("        mainContent.add(Box.createVerticalStrut(12));") 
loc.append('        mainContent.add(nodeListPanel);')
loc.append("        mainContent.add(Box.createVerticalStrut(12));")
loc.append("        mainContent.add(tlsServerPanel);")
loc.append("        mainContent.add(Box.createVerticalStrut(10));")

// Close pane (Horos stores prefs in NSUserDefaults SERVERS array) - done  
""", ["        contentPanel.add(new JScrollPane(mainContent), \"Locations\");"]  

# Wait this is still problematic with extend. Let me just append each line individually
loc.append('        contentPanel.add(new JScrollPane(mainContent), "Locations");')

// Closing brace (Horos reference comment style)
"    } // end createLocationsPane()"  

# Close pane - Horos stores preferences in NSUserDefaults SERVERS array  
loc.append('        contentPanel.add(new JScrollPane(mainContent), "Locations");')

// Closing brace  
loc.append("    } // end createLocationsPane()")

print('Replacing original lines %d-%d (%d lines) with %d new lines' % (loc_start+1, loc_end, loc_end-loc_start, len(loc)))
L[loc_start:loc_end] = loc  # slice assignment - safe and preserves ordering

with open(SRC, 'w', encoding='utf-8') as f:
    f.write('\n'.join(L))

print('Final line count:', len(L))
