const fs=require('fs');
const R='C:\\\\Users\\\\jam11\\\\OculusJJ';
const r=p=>fs.readFileSync(R+p,'utf8');
const w=(p,c)=>{fs.writeFileSync(R+p,c,'utf8');console.log('Wrote: '+p);};

function me(s,m){let si=s.indexOf(m);if(si<0)throw new Error('not found');let d=1,i=si+m.length;while(i<s.length&&d>0){if(s[i]==='{')d++;else if(s[i]==='}')d--;i++}return i-1;}
function ms(s,m){let i=s.indexOf(m);if(i<0)throw new Error('start not found');return i;}

let pc=r('src/main/java/com/oculus/jj/ui/PreferencesController.java');

// Step 1: Add imports after DicomNode import  
{const im='import com.oculus.jj.model.DicomNode;';if(pc.indexOf(im)>=0){
 let nl=pc.lastIndexOf('\n',pc.indexOf(im)+im.length);
 pc=pc.substring(0,nl)+'\nimport com.fasterxml.jackson.core.type.TypeReference;\nimport com.fasterxml.jackson.databind.ObjectMapper;' + pc.substring(nl);
}}

// Step 2: Add field  
{const cl='public class PreferencesController extends JDialog {';if(pc.indexOf(cl)>=0){
 let na=pc.indexOf('\n',pc.indexOf(cl)+cl.length);
 pc=pc.substring(0,na)+'\n\n    // Field for hot key table shared with helper methods\n    private JTable hkTable;\n'+pc.substring(na);
}}

// Step 3: Replace createHotKeysPane
{let s=ms(pc,'private void createHotKeysPane() {');let e=me(pc,'private void createHotKeysPane() {');
 let a=e;while(a<pc.length&&pc[a]==='\n')a++;
 const L=[
'private void createHotKeysPane() {',
'        JPanel pane = new JPanel(new BorderLayout());',
'',
'        // HOROS-20240407 Reference: Hot Key bindings with real data persistence',
'        JSectionBox hkBox = new JSectionBox("Keyboard Shortcuts");',
'        pane.add(hkBox, BorderLayout.NORTH);',
'',
'        String[] cols = {"Action", "Key Combination"};',
'        DefaultTableModel model = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int row, int col) { return false; } };',
'        hkTable = new JTable(model);',
'        hkTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);',
'        hkTable.setRowHeight(24);',
'',
'        // Load defaults from AppPreferences JSON on init',
'        loadHotKeysFromPrefs();',
'',
'        JPanel tablePanel = new JPanel(new BorderLayout());',
'        tablePanel.add(new JScrollPane(hkTable), BorderLayout.CENTER);',
'        pane.add(tablePanel, BorderLayout.CENTER);',
'',
'        // HOROS: HotKeyArrayController add/edit/remove buttons',
'        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));',
'',
'        JButton editBtn = new JButton("Edit Selected");',
'        editBtn.addActionListener(e -> {',
'            int r = hkTable.getSelectedRow();',
'            if (r >= 0) openKeyCaptureDialog(r, "Edit Key Binding", true);',
'        });',
'        bottomPanel.add(editBtn);',
'',
'        JButton removeBtn = new JButton("- Remove");',
'        removeBtn.addActionListener(e -> {',
'            int r = hkTable.getSelectedRow();',
'            if (r >= 0 && JOptionPane.showConfirmDialog(PreferencesController.this, "Remove this key binding?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {',
'                model.removeRow(r);',
'            }',
'        });',
'        bottomPanel.add(removeBtn);',
'',
'        JButton addBtn = new JButton("+ Add Key Binding");',
'        addBtn.addActionListener(e -> openKeyCaptureDialog(-1, "New Hotkey Binding", false));',
'        bottomPanel.add(addBtn);',
'',
'        pane.add(bottomPanel, BorderLayout.SOUTH);',
'',
'        // Auto-save on edit (HOROS: HotKeyArrayController save flow)',
'        hkTable.getModel().addTableModelListener(e -> {',
'            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {',
'                saveHotKeysToPrefs();',
'            }',
'        });',
'',
'        contentPanel.add(pane, "Hot Keys");',
'    }'];
 pc=pc.substring(0,s)+L.join('\n')+'\n'+pc.substring(a);}

console.log('Step 3 done');