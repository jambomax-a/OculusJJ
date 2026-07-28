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

# Current positions (0-indexed)  
lis_start = -1; loc_start = -1
for i in range(len(L)):
    s = L[i].strip()
    if 'private void createListenerPane()' in s: lis_start = i
    elif 'private void createLocationsPane()' in s and loc_start == -1: loc_start = i

lis_end = method_end_idx(lis_start)  
loc_end = method_end_idx(loc_start)

print('createListenerPane(): lines %d-%d (%d)' % (lis_start+1, lis_end, lis_end-lis_start))
print('createLocationsPane(): lines %d-%d (%d)' % (loc_start+1, loc_end, loc_end-loc_start))

# Build Listener pane replacement content
lis = []
lis.append('    // === HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm (~16KB .m)')
lis.append("    private void createListenerPane() {")
lis.append("        JPanel pane = new JPanel(new BorderLayout());")
lis.append('        int row = 0;')

# Basic settings (AE Title + Port)
lis.append("")
lis.append("        // HOROS-20240407 Reference: basic listener settings - AE Title / Port")
lis.append("        JPanel basicsBox = new JPanel(new GridBagLayout());")
lis.append('        GridBagConstraints gbcB = new GridBagConstraints();')
lis.append("        gbcB.insets = new Insets(6, 8, 6, 8);")
lis.append('        gbcB.anchor = GridBagConstraints.WEST;')
lis.append("        basicsBox.add(new JLabel(\"AE Title:\"), gbcB);")
lis.append('        JTextField aeField = new JTextField(AppPreferences.getListenerAETitle(), 15);')
lis.append("        gbcB.gridx = 1; basicsBox.add(aeField, gbcB);")

# Port setting (Horos: NSUserDefaults key for listener port)
lis.append("")
lis.append("        row++; gbcB.gridx = 0; gbcB.gridy = ++row;")
lis.append('        basicsBox.add(new JLabel("Listener Port:"), gbcB);')
lis.append("        JTextField portField = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 5);")
lis.append('        gbcB.gridx = 1; basicsBox.add(portField, gbcB);')

# Auto-start (Horos: listener auto-start preference)
lis.append("")
lis.append("        row++; gbcB.gridx = 0; gbcB.gridy = ++row;")
lis.append('        JCheckBox chkAutoStart = new JCheckBox("Automatically start listener at application launch");')
lis.append("        chkAutoStart.setSelected(AppPreferences.isListenerAutoStart());")
lis.append("        chkAutoStart.addActionListener(e -> AppPreferences.setListenerAutoStart(chkAutoStart.isSelected()));")

# TLS section (Horos - the big gap)
lis.append("")
lis.append("        // HOROS-20240407 Reference: OSIListenerPreferencePanePref.mm - TLS settings (~16KB .m)")
lis.append('        JPanel tlsPanel = new JPanel(new BorderLayout());')
lis.append('        tlsPanel.setBorder(BorderFactory.createTitledBorder("TLS/SSL Settings"));')

# Main TLS toggle (Horos: STORESCPTLS NSUserDefaults key)
lis.append("")
lis.append('        JCheckBox chkTLSEnabled = new JCheckBox("Enable TLS/SSL for this Listener");')
lis.append("        chkTLSEnabled.setSelected(AppPreferences.isListenerTLSEnabled());")
lis.append("        chkTLSEnabled.addActionListener(e -> AppPreferences.setListenerTLSEnabled(chkTLSEnabled.isSelected()));")

# Certificate authentication (Horos: SFChooseIdentityPanel + KeychainAccessCertificatesList)
lis.append("")
lis.append('        JPanel certBox = new JPanel(new GridBagLayout());')
lis.append("        GridBagConstraints gbcC = new GridBagConstraints();")
lis.append("        gbcC.insets = new Insets(4, 8, 4, 8);")
lis.append('        gbcC.anchor = GridBagConstraints.WEST;')
lis.append("        certBox.add(new JLabel(\"TLS Authentication Certificate:\"), gbcC);")
lis.append('        String currentCert = AppPreferences.getListenerTLSAuthCertName();')
lis.append("        JTextField certField = new JTextField(currentCert.isEmpty() ? \"No certificate selected\" : currentCert, 25);")
lis.append('        certField.setEditable(false);')
lis.append("        gbcC.gridx = 1; certBox.add(certField, gbcC);")

# Cipher suite selection (Horos: TLSSupportedCipherSuite array)
lis.append("")
lis.append("        // HOROS-20240407 Reference: selectAllSuites/deselectAllSuites IBActions from OSIListener.m")
lis.append('        JPanel cipherPanel = new JPanel(new BorderLayout());')
lis.append('        JLabel cipherLbl = new JLabel("Supported Cipher Suites (click to toggle):");')
lis.append('        cipherPanel.add(cipherLbl, BorderLayout.NORTH);')

# DH parameter file (Horos: TLSUseDHParameterFileURL)
lis.append("")
lis.append("        // HOROS-20240407 Reference: TLSDHParameterFileURL - Diffie-Hellman param file path")
lis.append('        JPanel dhBox = new JPanel(new GridBagLayout());')
lis.append("        GridBagConstraints gbcD = new GridBagConstraints();")
lis.append("        gbcD.insets = new Insets(4, 8, 4, 8);")
lis.append("        gbcD.anchor = GridBagConstraints.WEST;")
lis.append('        dhBox.add(new JLabel("DH Parameter File:"), gbcD);')

# Same AETitle option (Horos: TLSUseSameAETitle / TLSStoreSCPAETITLEIsDefaultAET)
lis.append("")
lis.append("        // HOROS-20240407 Reference: useSameAETitleForTLSListener IBAction")
lis.append('        JCheckBox chkSameAET = new JCheckBox("Use same AE Title as default Listener (for TLS StoreSCP)");')

# Assemble TLS section  
lis.append("")
lis.append('        JPanel tlsCenter = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));')
lis.append("        tlsCenter.add(certBox);")
lis.append("        tlsCenter.add(Box.createVerticalStrut(12));")
lis.append("        tlsCenter.add(cipherPanel);")
lis.append("        tlsCenter.add(Box.createVerticalStrut(12));")
lis.append("        tlsCenter.add(dhBox);")
lis.append("        tlsCenter.add(Box.createVerticalStrut(8));")
lis.append('        tlsCenter.add(chkSameAET);')
lis.append('        tlsPanel.add(tlsCenter, BorderLayout.CENTER);')

# Certificate choose + cipher buttons (Horos: chooseTLSCertificate IBAction)
lis.append("")
lis.append('        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));')
lis.append('        JButton btnChooseCert = new JButton("Choose Certificate...");')
lis.append('        JButton btnSelectAll = new JButton("Select All Ciphers");')
lis.append('        JButton btnDeselectAll = new JButton("Deselect All Ciphers");')
lis.append('        btnRow.add(btnChooseCert);')
lis.append('        btnRow.add(btnSelectAll);')
lis.append('        btnRow.add(btnDeselectAll);')

# Final assembly - Horos layout (BoxLayout vertical)
lis.append("")
lis.append('        JPanel topPanel = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));')
lis.append("        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));")
lis.append("        topPanel.add(basicsBox);")
lis.append("        topPanel.add(Box.createVerticalStrut(12));")
lis.append('        JPanel tlsSection = new JPanel(new BorderLayout());')
lis.append("        tlsSection.add(chkTLSEnabled, BorderLayout.NORTH);")
lis.append("        tlsSection.add(tlsCenter, BorderLayout.CENTER);")
lis.append("        topPanel.add(tlsSection);")
lis.append('        topPanel.add(btnRow);')
lis.append('        contentPanel.add(new JScrollPane(topPanel), "Listener");')

# Closing brace  
lis.append("    } // end createListenerPane()")

print("Built Listener pane: %d lines" % len(lis))

# Apply replacement (reverse order to avoid index shifting)
rep_list = (lis_start, lis_end, lis)
for start_idx, end_idx, content in [rep_list]:  # only one rep for now
    print("Replacing original lines %d-%d (%d lines) with %d new lines" % (start_idx+1, end_idx, end_idx-start_idx, len(content)))
    L[start_idx:end_idx] = content

with open(SRC, 'w', encoding='utf-8') as f:
    f.write('\n'.join(L))

print('Final line count:', len(L))
