const fs = require('fs');
let code = fs.readFileSync('src/main/java/com/oculus/jj/ui/PreferencesController.java', 'utf8');

// Replace closing section to add test connection button
const searchStr = "pane.add(tlsPanel);\n        return wrapVertically(pane);";
const replaceWith = [
  'pane.add(tlsPanel);',
  '',
  '        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));',
  '        JButton btnTestConnection = new JButton("Test Connection (C-ECHO)...");',
  '        btnTestConnection.addActionListener(e -> {',
  '            DicomNodeManager mngr = DicomNodeManager.getInstance();',
  '            List<DicomNode> nodes = mngr.getNodes();',
  '            if (nodes.isEmpty()) {',
  '                JOptionPane.showMessageDialog(this, "No DICOM nodes configured. Add a node in Locations pane first.");',
  '                return;',
  '            }',
  '            DicomNode node = nodes.get(0); // use first node for now',
  '            String message = "Test connection to: " + node.getName() + ", AE Title: " + node.getAeTitle();',
  '            int result = JOptionPane.showConfirmDialog(this,',
  '                    new JScrollPane(new JTextArea(message, 5, 40)),',
  '                    "C-ECHO Test", JOptionPane.OK_CANCEL_OPTION);',
  '        });',
  '        testRow.add(btnTestConnection);',
  '        pane.add(testRow);',
  '',
  '        return wrapVertically(pane);'
].join("\n");

if (code.includes(searchStr)) {
    code = code.replace(searchStr, replaceWith);
    fs.writeFileSync('src/main/java/com/oculus/jj/ui/PreferencesController.java', code);
    console.log("Updated. Size:", fs.statSync('src/main/java/com/oculus/jj/ui/PreferencesController.java').size);
} else {
    console.log("Search string not found");
}
