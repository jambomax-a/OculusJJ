const fs = require('fs');
let code = fs.readFileSync('src/main/java/com/oculus/jj/ui/PreferencesController.java', 'utf8');

// TLS Certificate Path section improvement - add Browse button
const oldBlock = [
  '        addGap(tlsPanel);',
  '        JTextField tfCertPath = new JTextField(AppPreferences.getListenerTLSAuthCertName(), 40);',
  '        tlsPanel.add(makeRow("TLS Certificate Path:", tfCertPath));',
  '',
  '        addGap(tlsPanel);',
  '        JComboBox<String> comboCipher = new JComboBox<>(new String[]{',
  '                "TLS_AES_256_GCM_SHA384", "TLS_RSA_WITH_AES_256_GCM_SHA384", "Default"});',
  '        tlsPanel.add(makeRow("Preferred Cipher Suite:", comboCipher));'
].join("\n");

const newBlock = [
  '        addGap(tlsPanel);',
  '        JPanel certPathRow = new JPanel(new BorderLayout(6, 0));',
  '        JTextField tfCertPath = new JTextField(AppPreferences.getListenerTLSAuthCertName(), 40);',
  '        JButton btnBrowseCert = new JButton("Browse...");',
  '        btnBrowseCert.addActionListener(e -> {',
  '            JFileChooser fc = new JFileChooser();',
  '            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Certificate Files", "crt", "pem", "p12"));',
  '            String currentPath = AppPreferences.getListenerTLSAuthCertName();',
  '            if (currentPath != null && !currentPath.isEmpty()) {',
  '                fc.setSelectedFile(new java.io.File(currentPath));',
  '            }',
  '            int result = fc.showOpenDialog(this);',
  '            if (result == JFileChooser.APPROVE_OPTION) {',
  '                tfCertPath.setText(fc.getSelectedFile().getAbsolutePath());',
  '                AppPreferences.setListenerTLSAuthCertName(fc.getSelectedFile().getAbsolutePath());',
  '            }',
  '        });',
  '        certPathRow.add(tfCertPath, BorderLayout.CENTER);',
  '        certPathRow.add(btnBrowseCert, BorderLayout.EAST);',
  '        tlsPanel.add(certPathRow);',
  '',
  '        addGap(tlsPanel);',
  '        JComboBox<String> comboCipher = new JComboBox<>(new String[]{',
  '                "TLS_AES_256_GCM_SHA384", "TLS_RSA_WITH_AES_256_GCM_SHA384", "Default"});',
  '        tlsPanel.add(makeRow("Preferred Cipher Suite:", comboCipher));'
].join("\n");

code = code.replace(oldBlock, newBlock);
fs.writeFileSync('src/main/java/com/oculus/jj/ui/PreferencesController.java', code);
console.log('Updated. Size:', fs.statSync('src/main/java/com/oculus/jj/ui/PreferencesController.java').size);
