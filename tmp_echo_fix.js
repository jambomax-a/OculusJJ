const fs = require('fs');
let code = fs.readFileSync('src/main/java/com/oculus/jj/ui/PreferencesController.java', 'utf8');

// Add test connection button before the closing brace of createListenerPane
const oldBlock = [
  '        pane.add(tlsPanel);',
  '        return wrapVertically(pane);'
].join("\n");

const newBlock = [
  '        pane.add(tlsPanel);',
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
  '            JOptionPane.showConfirmDialog(this,',
  '                    "Test connection to: " + node.getName() + "\\nAE: " + node.getAeTitle() + "@' + node.getAddress() + ':' + node.getPort() +"",',
  '                    "C-ECHO Test", JOptionPane.OK_CANCEL_OPTION);',
  '            // TODO: implement actual C-ECHO via DicomCommunicationUtils.testConnection(node)',
  '        });',
  '        testRow.add(btnTestConnection);',
  '        pane.add(testRow);',
  '',
  '        return wrapVertically(pane);'
].join("\n");

code = code.replace(oldBlock, newBlock);
fs.writeFileSync('src/main/java/com/oculus/jj/ui/PreferencesController.java', code);
console.log('Updated. Size:', fs.statSync('src/main/java/com/oculus/jj/ui/PreferencesController.java').size);
