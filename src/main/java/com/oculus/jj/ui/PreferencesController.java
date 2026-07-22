package com.oculus.jj.ui;







import java.io.File;



import javax.swing.*;



import javax.swing.border.EmptyBorder;



import java.awt.*;



import com.oculus.jj.config.AppPreferences;



import com.oculus.jj.model.DicomNode;



import com.oculus.jj.model.DicomNodeManager;
import javax.swing.table.DefaultTableModel;
import com.oculus.jj.model.KeyBinding;
import java.util.List;







/**



 * PreferencesController - HOROS Preference Window equivalent (all 15 panes).



 */



public class PreferencesController extends JPanel {







    private static final String[] PANE_NAMES = {



            "General", "Database", "CD/DVD Burning", "Hanging Protocols",



            "Hot Keys", "Viewers", "3D Viewer", "PET/SUV", "Annotations",



            "DICOM Print", "Listener", "Web Server", "On-Demand PACS",



            "Locations", "Routing"



    };







    



    private static PreferencesController sInstance;







    public static void showPreferences(Frame owner) {



        if (sInstance == null) sInstance = new PreferencesController();



        SwingUtilities.invokeLater(() -> {



            JDialog dialog = new JDialog(owner, "OculusJJ Preferences", java.awt.Dialog.ModalityType.MODELESS);



            dialog.add(sInstance);



            dialog.setSize(900, 650);



            dialog.setLocationRelativeTo(owner);



            dialog.setVisible(true);



        });



    }



    public static PreferencesController getInstance() { return sInstance; }







    public static void showFrame(Component ownerComponent) {



        JDialog dialog = new JDialog(



                SwingUtilities.getWindowAncestor(ownerComponent),



                "OculusJJ Preferences", java.awt.Dialog.ModalityType.MODELESS);



        if (sInstance == null) sInstance = new PreferencesController();



        dialog.add(sInstance);



        dialog.setSize(900, 650);



        dialog.setLocationRelativeTo(ownerComponent);



        dialog.setVisible(true);



    }







private JTabbedPane tabbedPane;







    public PreferencesController() {



        setLayout(new BorderLayout(0, 8));



        setBorder(new EmptyBorder(8, 12, 4, 12));



        buildUI();



    }







    // --- Layout helpers (HOROS JSectionBox equivalent) -----------------------



    private JPanel sectionBox(String title) {



        JPanel box = new JPanel();



        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));



        box.setBorder(BorderFactory.createTitledBorder(



                BorderFactory.createEtchedBorder(),



                "<html><b>" + title + "</b></html>",



                javax.swing.border.TitledBorder.LEFT,



                javax.swing.border.TitledBorder.TOP));



        box.setAlignmentX(Component.LEFT_ALIGNMENT);



        return box;



    }







    private JPanel makeRow(String label, JComponent control) {



        JPanel row = new JPanel(new BorderLayout(6, 0));



        JLabel lbl = new JLabel(label);



        lbl.setPreferredSize(new Dimension(180, -1));



        lbl.setAlignmentY(Component.TOP_ALIGNMENT);



        control.setAlignmentX(Component.LEFT_ALIGNMENT);



        row.add(lbl, BorderLayout.WEST);



        row.add(control, BorderLayout.CENTER);



        return row;



    }







    private JPanel makeToggleRow(String label, JCheckBox cb) {



        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));



        cb.setOpaque(false);



        if (label.length() > 0) row.add(new JLabel(label + " ", SwingConstants.RIGHT));



        row.add(cb);



        return row;



    }







    private void addGap(JPanel container) {



        container.add(Box.createVerticalStrut(8));



    }







    private JScrollPane wrapVertically(JPanel inner) {



        JScrollPane scroll = new JScrollPane(inner,



                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,



                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);



        inner.setMinimumSize(new Dimension(520, 600));



        return scroll;



    }







    private void addSeparator(JPanel container) {



        addGap(container);



        container.add(new JSeparator(SwingConstants.HORIZONTAL));



        addGap(container);



    }







    // ======================== Build Main UI ================================



    public JTabbedPane buildUI() {



        tabbedPane = new JTabbedPane();



        for (String name : PANE_NAMES) {

            String methodName = "create" + toCamel(name) + "Pane";

            try {

                JComponent pane = ((JComponent) getClass().getDeclaredMethod(methodName).invoke(this));

                tabbedPane.addTab(name, null, pane, name);

            } catch (Exception e) {

                tabbedPane.addTab(name, null, new JPanel(new FlowLayout(FlowLayout.LEFT)), name);

            }



        }



        add(tabbedPane, BorderLayout.CENTER);







        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));



        JButton btnSave = new JButton("Apply");



        btnSave.addActionListener(e -> AppPreferences.saveAll());



        JButton btnCancel = new JButton("Cancel");



        btnCancel.addActionListener(e -> tabbedPane.setSelectedIndex(0));



        JButton btnClose = new JButton("Close");



        btnClose.addActionListener(e -> SwingUtilities.getWindowAncestor(this).dispose());



        btnPanel.add(btnSave);



        btnPanel.add(btnCancel);



        btnPanel.add(btnClose);



        add(btnPanel, BorderLayout.SOUTH);



        return tabbedPane;



    }







    private String toCamel(String name) {



        StringBuilder sb = new StringBuilder();



        for (String part : name.split("[ /]")) {



            if (part.isEmpty()) continue;



            sb.append(part.substring(0, 1).toUpperCase());



            if (part.length() > 1) sb.append(part.substring(1));



        }



        return sb.toString();



    }











    // ===== PANE 1: General (HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m) =====



    private JComponent createGeneralPane() {



        JTabbedPane sub = new JTabbedPane();



        sub.addTab("General", buildGeneralBasic());



        sub.addTab("Network", buildGeneralNetwork());



        sub.addTab("Other", buildGeneralOther());



        sub.setAlignmentX(Component.LEFT_ALIGNMENT);



        JPanel wrapper = new JPanel(new BorderLayout());



        wrapper.add(sub, BorderLayout.CENTER);



        return (JComponent) wrapper;



    }







    private JPanel buildGeneralBasic() {



        JPanel pane = sectionBox("Preferences");



        String[] languages = {"English", "Japanese"};



        JComboBox<String> comboLang = new JComboBox<>(languages);



        String current = AppPreferences.getAppLanguage();



        for (int i = 0; i < languages.length; i++) {



            if (languages[i].equalsIgnoreCase(current)) comboLang.setSelectedIndex(i);



        }



        pane.add(makeRow("Application Language:", comboLang));







        addGap(pane);



        JCheckBox cbUpdates = new JCheckBox("Check for updates at startup", AppPreferences.isCheckUpdatesAtStartup());



        cbUpdates.addActionListener(e -> AppPreferences.setCheckUpdatesAtStartup(cbUpdates.isSelected()));



        pane.add(makeToggleRow("", cbUpdates));







        JComboBox<String> comboUnit = new JComboBox<>(new String[]{"mm", "cm"});



        int idx = "cm".equals(AppPreferences.getMeasureUnit()) ? 1 : 0;



        comboUnit.setSelectedIndex(idx);



        pane.add(makeRow("Measurement Unit:", comboUnit));







        addGap(pane);



        JComboBox<String> comboDisplay = new JComboBox<>(new String[]{"Default", "DICOM Label", "Custom"});



        comboDisplay.setSelectedIndex(AppPreferences.getDisplayTypeIndex());



        pane.add(makeRow("Label Display:", comboDisplay));







        addGap(pane);



        JComboBox<String> comboSeparate = new JComboBox<>(new String[]{"No", "Yes"});



        comboSeparate.setSelectedIndex(AppPreferences.isSeparateWindowPerStudy() ? 1 : 0);



        pane.add(makeRow("Open each Study in a Separate Window:", comboSeparate));







        return pane;



    }







    private JPanel buildGeneralNetwork() {



        JPanel pane = sectionBox("AE Title / Transfer Syntaxes");



        JTextField tfAET = new JTextField(AppPreferences.getListenerAETitle(), 30);



        pane.add(makeRow("My AE Title:", tfAET));



        tfAET.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {



            public void insertUpdate(javax.swing.event.DocumentEvent e) { AppPreferences.setListenerAETitle(tfAET.getText()); }



            public void removeUpdate(javax.swing.event.DocumentEvent e) { AppPreferences.setListenerAETitle(tfAET.getText()); }



            public void changedUpdate(javax.swing.event.DocumentEvent e) { AppPreferences.setListenerAETitle(tfAET.getText()); }



        });







        addGap(pane);



        JComboBox<String> comboJP2K = new JComboBox<>(new String[]{"OpenJPEG", "Kakadu (if available)"});



        pane.add(makeRow("JPEG 2000 Engine:", comboJP2K));







        addGap(pane);



        JButton btnEditCompression = new JButton("Edit Compression Settings...");



        btnEditCompression.addActionListener(e -> JOptionPane.showMessageDialog(this,



                "Compression settings editor (future implementation)", "Horos Reference", JOptionPane.INFORMATION_MESSAGE));



        pane.add(btnEditCompression);







        JPanel autoSavePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));



        JCheckBox cbAuto = new JCheckBox("Automatically save to database after receiving a DICOM file", true);



        autoSavePanel.add(cbAuto);



        pane.add(autoSavePanel);







        addGap(pane);



        JButton btnReset = new JButton("Reset All Preferences...");



        btnReset.addActionListener(e -> {



            int r = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this),



                    "Are you sure you want to reset all preferences?", "Reset",



                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);



            if (r == JOptionPane.YES_OPTION) AppPreferences.saveAll();



        });



        pane.add(btnReset);







        return pane;



    }







    private JPanel buildGeneralOther() {



        JPanel pane = sectionBox("Miscellaneous");



        JCheckBox cbAnnotations = new JCheckBox("Enable Annotations by default", false);



        pane.add(makeToggleRow("", cbAnnotations));







        addGap(pane);



        JButton btnAnnotationStyle = new JButton("Edit Annotation Style...");



        btnAnnotationStyle.addActionListener(e -> JOptionPane.showMessageDialog(this,



                "Annotation style editor (future)", "Horos Reference", JOptionPane.INFORMATION_MESSAGE));



        pane.add(btnAnnotationStyle);







        JPanel mousePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));



        JComboBox<String> comboMouse1 = new JComboBox<>(new String[]{"Zoom", "Pan", "Measure"});



        comboMouse1.setSelectedIndex(0);



        mousePanel.add(new JLabel("Mouse Button 1:"));



        mousePanel.add(comboMouse1);



        pane.add(mousePanel);







        JPanel sizeLimitRow = makeRow("Max Window Size:", new JSpinner(new SpinnerNumberModel(800, 256, 4096, 64)));



        addGap(pane);



        pane.add(sizeLimitRow);







        JButton btnAlignment = new JButton("Alignment with Orientation Markers...");



        addGap(pane);



        pane.add(btnAlignment);







        return pane;



    }







    // ===== PANE 2: Database (HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m) =====



    private JComponent createDatabasePane() {



        JPanel pane = sectionBox("Patient identifiers");



        String[] uidOptions = {"Use patient name", "Use date of birth"};



        JComboBox<String> comboUID = new JComboBox<>(uidOptions);



        // HOROS-20240407 Reference: usePatientNameForUID preference key



        String currentUidMethod = AppPreferences.getUsePatientNameForUID();



        int idx = ("usePatientName".equals(currentUidMethod) || "yes".equalsIgnoreCase(currentUidMethod)) ? 0 : 1;



        comboUID.setSelectedIndex(idx);



        pane.add(makeRow("Identifier for the UID:", comboUID));







        addGap(pane);



        JPanel formatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));



        JSpinner spinnerDOB = new JSpinner(new SpinnerNumberModel(2, 0, 10, 1));



        formatPanel.add(new JLabel("Format for date of birth:"));



        formatPanel.add(spinnerDOB);



        pane.add(formatPanel);







        addGap(pane);



        JPanel cleanupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));



        JCheckBox cbCleanup = new JCheckBox("Automatically remove images older than:", true);



        JTextField tfDays = new JTextField(5);



        tfDays.setText(String.valueOf(AppPreferences.isUseSeriesDescription() ? 90 : 30));



        cleanupPanel.add(cbCleanup);



        cleanupPanel.add(tfDays);



        cleanupPanel.add(new JLabel("days"));



        pane.add(cleanupPanel);







        addGap(pane);



        JCheckBox cbSeriesDesc = new JCheckBox("Use Series Description as identifier when importing from PACS", AppPreferences.isUseSeriesDescription());



        pane.add(makeToggleRow("", cbSeriesDesc));







        addSeparator(pane);



        JPanel eraseBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));



        JButton btnEraseAll = new JButton("Erase Entire Database");



        btnEraseAll.addActionListener(e -> {



            int r = JOptionPane.showConfirmDialog(this, "Erase entire database?",



                    "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);



            if (r == JOptionPane.YES_OPTION) AppPreferences.saveAll();



        });



        eraseBtns.add(btnEraseAll);







        JButton btnBackup = new JButton("Back Up Database");



        btnBackup.addActionListener(e -> { /* Future: backup dialog */ });



        eraseBtns.add(btnBackup);



        pane.add(eraseBtns);







        return wrapVertically(pane);



    }











    // ===== PANE 3: CD/DVD Burning (HOROS-20240407 Reference: OSICDPreferencePanePref.m) =====



    private JComponent createCDDVDBurningPane() {



        JPanel pane = sectionBox("General");



        JCheckBox cbEject = new JCheckBox(



                "Automatically eject a disk after copying to it", AppPreferences.isCDDVDEjectAfterCopy());



        cbEject.addActionListener(e -> AppPreferences.setCDDVDEjectAfterCopy(cbEject.isSelected()));



        pane.add(makeToggleRow("", cbEject));







        JPanel autoSelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));



        JCheckBox cbAutoSelect = new JCheckBox("Automatically select source:", true);



        JComboBox<String> comboSource = new JComboBox<>(new String[]{"Local Hard Drive", "PACS"});



        autoSelPanel.add(cbAutoSelect);



        autoSelPanel.add(comboSource);



        pane.add(autoSelPanel);







        JPanel importPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));



        JCheckBox cbImport = new JCheckBox("Automatically import from inserted discs:", true);



        JComboBox<String> comboModality = new JComboBox<>(new String[]{"All Modalities", "X-Ray", "CT", "MRI", "PET"});



        importPanel.add(cbImport);



        importPanel.add(comboModality);



        pane.add(importPanel);







        JCheckBox cbPreferJPEG = new JCheckBox("Use standard JPEG rather than DICOM Lossless when saving on disk",



                AppPreferences.isCDDVDPrefStdJPEG());



        cbPreferJPEG.addActionListener(e -> AppPreferences.setCDDVDPrefStdJPEG(cbPreferJPEG.isSelected()));



        addGap(pane);



        pane.add(makeToggleRow("", cbPreferJPEG));







        JButton btnSelectFolder = new JButton("Select folder containing supplementary DICOM files...");



        btnSelectFolder.addActionListener(e -> {



            JFileChooser fc = new JFileChooser(AppPreferences.getCDDVDSupplementaryFolder());



            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {



                AppPreferences.setCDDVDSupplementaryFolder(fc.getSelectedFile().getAbsolutePath());



            }



        });



        pane.add(btnSelectFolder);







        addSeparator(pane);



        JPanel burnPanel = sectionBox("Burning Options");



        JButton btnCompression = new JButton("Change compression mode...");



        burnPanel.add(makeRow("Name of the DICOM disk:", new JTextField(AppPreferences.getBurnCDName(), 20)));



        addGap(burnPanel);



        burnPanel.add(btnCompression);







        pane.add(burnPanel);



        return wrapVertically(pane);



    }











    // ===== PANE 4: Hanging Protocols (HOROS-20240407 Reference: OSIHangingPreferencePanePref.m) =====



    private JComponent createHangingProtocolsPane() {



        JPanel pane = sectionBox("Default Values");



        String[] modalities = {"All Modalities", "CT", "MR", "CR", "DX", "PT"};



        JComboBox<String> comboModality = new JComboBox<>(modalities);



        int modIdx = Math.min(AppPreferences.getHangingModalityIndex(), modalities.length - 1);



        comboModality.setSelectedIndex(modIdx);



        pane.add(makeRow("Default Modality:", comboModality));







        addGap(pane);



        String[] tilings = {"1x1", "2x1", "3x1", "2x2", "4x2"};



        JComboBox<String> comboTiling = new JComboBox<>(tilings);



        int tIdx = Math.min(AppPreferences.getHangingTilingIndex(), tilings.length - 1);



        comboTiling.setSelectedIndex(tIdx);



        pane.add(makeRow("Default Tiling:", comboTiling));







        addGap(pane);



        String[] presets = {"Default", "Bone Window", "Brain Window", "Lung Window"};



        JComboBox<String> comboWLWW = new JComboBox<>(presets);



        comboWLWW.setSelectedItem(AppPreferences.getHangingWLWWPreset());



        pane.add(makeRow("Window/Level Preset:", comboWLWW));







        addSeparator(pane);



        JPanel bodyRegionPanel = sectionBox("Body Regions (JSON)");



        JTextField tfRegions = new JTextField(AppPreferences.getHangingProtocolBodyRegionsJSON(), 50);



        bodyRegionPanel.add(tfRegions);



        pane.add(bodyRegionPanel);







        return wrapVertically(pane);



    }











    // ===== PANE 5: Hot Keys (HOROS-20240407 Reference: OSIHotKeysPref.m) =====



    private JComponent createHotKeysPane() {
        JPanel pane = sectionBox("Hot Key Bindings");

        // -- Table for displaying bindings (HOROS-20240407 Reference: DCMView.m actionForHotKey:) --
        String[] columns = {"キー操作", "アクション名"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 1) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };

        List<KeyBinding> bindings = AppPreferences.getHotkeyBindings();
        for (KeyBinding b : bindings) {
            Object[] row = { b.keyCombo, b.displayName };
            tableModel.addRow(row);
        }

        JTable bindingTable = new JTable(tableModel);
        pane.add(new JScrollPane(bindingTable), BorderLayout.CENTER);

        // -- Button area below the table (HOROS-20240407準拠: Add / Edit / Remove pattern) --
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        JButton btnAdd = new JButton("Hot Key Add...");
        JButton btnEdit = new JButton("Hot Key Edit...");
        JButton btnRemove = new JButton("Hot Key Delete");
        btnAdd.addActionListener(e -> startKeyCapture(this, bindingTable, tableModel, "New Binding"));
        btnEdit.addActionListener(e -> {
            if (bindingTable.getSelectedRow() >= 0) {
                startKeyCapture(this, bindingTable, tableModel, "Editing: row " + (bindingTable.getSelectedRow() + 1));
            } else {
                JOptionPane.showMessageDialog(this, "Please select a key binding first.");
            }
        });
        btnRemove.addActionListener(e -> {
            int selected = bindingTable.getSelectedRow();
            if (selected >= 0) {
                KeyBinding b = AppPreferences.getHotkeyBindings().get(selected);
                String combo = b.keyCombo;
                if (AppPreferences.removeHotkeyBinding(combo)) {
                    tableModel.removeRow(selected);
                    bindingTable.repaint();
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to delete.");
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a key binding first.");
            }
        });

        btnRow.add(btnAdd);
        btnRow.add(btnEdit);
        btnRow.add(btnRemove);
        pane.add(btnRow, BorderLayout.SOUTH);

        return wrapVertically(pane);
    }

    /** Key capture mode - 押されたキーをホットキーバインディングとして記録する */
    private void startKeyCapture(final JComponent parent, final JTable bindingTable,
            final DefaultTableModel tableModel, String title) {
        final JPanel capturePanel = new JPanel(new BorderLayout(8, 0));
        JLabel label = new JLabel("Press the key combination you want to assign. Press Esc to cancel.",
                SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(13f));
        JTextField displayField = new JTextField("Press keys...");
        displayField.setEditable(false);
        capturePanel.add(label, BorderLayout.NORTH);
        capturePanel.add(displayField, BorderLayout.SOUTH);

        JPanel wrapper = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.insets = new Insets(4, 4, 4, 4);
        wrapper.add(capturePanel, gbc);

        int result = JOptionPane.showConfirmDialog(parent, wrapper, "Key Capture - " + title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.CANCEL_OPTION || displayField.getText().equals("Press keys...")) {
            return;
        }

        String combo = displayField.getText();
        // Add to the AppPreferences and update table
        List<KeyBinding> bindings = AppPreferences.getHotkeyBindings();
        KeyBinding newEntry = new KeyBinding("New Binding", combo, -1);
        boolean replaced = false;
        for (int i = 0; i < bindings.size(); i++) {
            if (bindings.get(i).keyCombo.equalsIgnoreCase(combo)) {
                // Replace existing binding
                if (!bindings.get(i).displayName.isEmpty()) {
                    newEntry.displayName = bindings.get(i).displayName;
                }
                bindings.set(i, newEntry);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            bindings.add(newEntry);
        }
        AppPreferences.setHotkeyBindings(bindings);

        // Update the table display
        DefaultTableModel tm2 = (DefaultTableModel) bindingTable.getModel();
        int selectedRow = bindingTable.getSelectedRow();
        if (selectedRow < 0 && !replaced && tm2.getRowCount() > 0) {
            selectedRow = tm2.getRowCount() - 1;
        }
        // Insert at the position that was edited or append
        boolean inserted = false;
        int targetIdx = replaced ? bindingTable.getSelectedRow() : tm2.getRowCount();
        for (int i = 0; i < bindings.size(); i++) {
            if (bindings.get(i).keyCombo.equalsIgnoreCase(combo)) {
                Object[] row = { bindings.get(i).keyCombo, bindings.get(i).displayName };
                tm2.insertRow(Math.min(targetIdx, i), row);
                inserted = true;
                break;
            }
        }
        // Rebuild table from AppPreferences to keep it consistent
        tm2.setRowCount(0);
        for (KeyBinding b : bindings) {
            tm2.addRow(new Object[] { b.keyCombo, b.displayName });
        }
    }


// ===== PANE 6: Viewers (HOROS-20240407 Reference: OSIViewerPreferencePanePref.m) =====






    private JComponent createViewersPane() {
        JPanel pane = sectionBox("Default Viewer Settings");

        // -- Default CLUT (HOROS-20240407 Reference: OSIViewerPreferencePanePref.m - CLUT selection) --
        String[] cluts = {"CT-Body", "PET-Rainbow", "Gray Scale", "Brain", "Bone"};
        JComboBox<String> comboCLUT = new JComboBox<>(cluts);
        comboCLUT.setSelectedItem(AppPreferences.getDefaultCLUT());
        pane.add(makeRow("Default CLUT:", comboCLUT));

        addGap(pane);

        // -- Default Opacity Table (HOROS-20240407 Reference: OSIViewerPreferencePanePref.m - Opacity) --
        String[] opacityTables = {"Standard", "Custom 1", "Custom 2"};
        JComboBox<String> comboOpacity = new JComboBox<>(opacityTables);
        comboOpacity.setSelectedItem(AppPreferences.getDefaultOpacityTable());
        pane.add(makeRow("Default Opacity Table:", comboOpacity));

        addGap(pane);

        // -- Synchronize Series Between Studies (HOROS-20240407 Reference: OSIViewerPreferencePanePref.m) --
        JCheckBox cbSync = new JCheckBox(
                "Synchronize Series Between Studies", AppPreferences.isSyncSeriesBetweenStudies());
        pane.add(makeToggleRow("", cbSync));

        addSeparator(pane);

        // ===== Secondary Monitor Section (HOROS-20240407 Reference: OSIViewerPreferencePanePref.m / MULTISCREENS) --
        JPanel secondaryPanel = sectionBox("Secondary Monitor");

        JCheckBox cbMonitorEnabled = new JCheckBox("Enable Secondary Monitor", AppPreferences.isSecondaryMonitorEnabled());
        secondaryPanel.add(makeToggleRow("", cbMonitorEnabled));
        pane.add(secondaryPanel);

        // Monitor selection dropdown (HOROS-20240407 Reference: ViewerController.m 8762-8781)
        JPanel monitorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JLabel lblMonitorIdx = new JLabel("Monitor Index:");
        JSpinner spinnerMonitor = new JSpinner(new SpinnerNumberModel(AppPreferences.getMonitorIndex(), 0, 16, 1));
        monitorPanel.add(lblMonitorIdx);
        monitorPanel.add(spinnerMonitor);
        pane.add(monitorPanel);

        // Keep CLUT bars for second capture (HOROS-20240407 Reference: ViewerController.m 18879/18881)
        JCheckBox cbKeepCLUT = new JCheckBox(
                "Keep CLUT Bars on Second Capture", AppPreferences.isKeepCLUTBarsForSecondCapture());
        pane.add(makeToggleRow("", cbKeepCLUT));

        addSeparator(pane);

        // -- Window Size Mode (HOROS-20240407 Reference: ViewerController.m 8787/8791 - WINDOWSIZEVIEWER) --
        JPanel windowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JLabel lblWinSize = new JLabel("Default Window Size:");
        String[] sizePresets = {"800x600", "1024x768", "1280x960", "1920x1080"};
        JComboBox<String> comboWinSize = new JComboBox<>(sizePresets);
        int currentMode = AppPreferences.getViewerWindowSizeMode();
        if (currentMode >= 0 && currentMode < sizePresets.length) {
            comboWinSize.setSelectedIndex(currentMode);
        }
        windowPanel.add(lblWinSize);
        windowPanel.add(comboWinSize);
        pane.add(windowPanel);

        addGap(pane);

        // -- Default Window Size (W x H) manual override - tied to AppPreferences via the Spinner --
        JPanel sizeManual = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JLabel lblSizeX = new JLabel("Width:");
        JSpinner spinnerWidth = new JSpinner(new SpinnerNumberModel(800, 256, 4096, 32));
        sizeManual.add(lblSizeX);
        sizeManual.add(spinnerWidth);

        JLabel lblSizeY = new JLabel("Height:");
        JSpinner spinnerHeight = new JSpinner(new SpinnerNumberModel(600, 256, 4096, 32));
        sizeManual.add(lblSizeY);
        sizeManual.add(spinnerHeight);
        pane.add(sizeManual);

        addSeparator(pane);

        // -- Default Alignment (HOROS-20240407 Reference: OSIViewerPreferencePanePref.m) --
        JComboBox<String> comboAlignment = new JComboBox<>(new String[]{"Left-Aligned", "Right-Aligned"});
        comboAlignment.setSelectedIndex(AppPreferences.getViewerWindowSizeMode() == 1 ? 1 : 0);
        pane.add(makeRow("Default Alignment:", comboAlignment));

        return wrapVertically(pane);
    }

    // ===== PANE 7: 3D Viewer (HOROS-20240407 Reference: OSI3DPreferencePanePref.m) =====



    private JComponent create3DViewerPane() {



        JPanel pane = sectionBox("3D Rendering");



        String[] shadingAlgorithms = {"Surface Shading", "Volume Rendering", "Maximum Intensity Projection"};



        JComboBox<String> comboShading = new JComboBox<>(shadingAlgorithms);



        comboShading.setSelectedIndex(AppPreferences.get3DShadingAlgorithmIndex());



        pane.add(makeRow("Default Shading Algorithm:", comboShading));







        addGap(pane);



        String[] roiAlgorithms = {"Isosurface", "Raycasting"};



        JComboBox<String> comboROI = new JComboBox<>(roiAlgorithms);



        comboROI.setSelectedIndex(AppPreferences.get3DRoiAlgorithmIndex());



        pane.add(makeRow("Default ROI Algorithm:", comboROI));







        addGap(pane);



        JCheckBox cbSpline = new JCheckBox("Use spline interpolation for Scissors ROI",



                AppPreferences.is3DSplineForScissors());



        pane.add(makeToggleRow("", cbSpline));







        addGap(pane);



        JPanel resolutionPanel = makeRow(



                "Default 3D Resolution (0.1 - 1.0):",



                new JSpinner(new SpinnerNumberModel(AppPreferences.get3DResolution(), 0.1, 1.0, 0.1)));



        pane.add(resolutionPanel);







        return wrapVertically(pane);



    }











    // ===== PANE 8: PET/SUV (HOROS-20240407 Reference: OSIPETPreferencePane.m) =====



    private JComponent createPETSVUPane() {



        JPanel pane = sectionBox("PET/SUV Normalization");



        String[] scanners = {"", "Biograph mCT 64-slice", "Biograph mMR", "Discovery PET/CT 690"};



        JComboBox<String> comboScanner = new JComboBox<>(scanners);



        String currentScanner = AppPreferences.getPetsuvNormalizeScanner();



        if (currentScanner.isEmpty()) {



            comboScanner.setSelectedIndex(0);



        } else {



            for (int i = 1; i < scanners.length; i++) {



                if (scanners[i].equals(currentScanner)) comboScanner.setSelectedIndex(i);



            }



        }



        pane.add(makeRow("Select Scanner:", comboScanner));







        addGap(pane);



        JPanel clutPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));



        JComboBox<String> comboPETCLUT = new JComboBox<>(new String[]{"PET-Rainbow", "Gray Scale", "Hue Shift"});



        comboPETCLUT.setSelectedItem(AppPreferences.getPETDefaultCLUT());



        clutPanel.add(new JLabel("Default PET CLUT:"));



        clutPanel.add(comboPETCLUT);



        pane.add(clutPanel);







        addGap(pane);



        JCheckBox cbBWInv = new JCheckBox("Invert Black-White CLUT", AppPreferences.isPETCLUTBwInverse());



        pane.add(makeToggleRow("", cbBWInv));







        addSeparator(pane);



        JPanel transferPanel = sectionBox("Default Transfer Function");



        String[] funcs = {"Standard", "Low Contrast"};



        JComboBox<String> comboFunc = new JComboBox<>(funcs);



        comboFunc.setSelectedItem(AppPreferences.getPETDefaultTransferFunction());



        transferPanel.add(makeRow("Transfer Function:", comboFunc));







        addGap(pane);



        JCheckBox cbNMModality = new JCheckBox("Apply to NM Modality", AppPreferences.isPETApplyToNM());



        pane.add(makeToggleRow("", cbNMModality));







        return wrapVertically(pane);



    }











    // ===== PANE 9: Annotations (HOROS-20240407 Reference: OSICustomImageAnnotations.m) =====



    private JComponent createAnnotationsPane() {



        JPanel pane = sectionBox("Default Annotation Settings");



        String[] styles = {"None", "Standard", "Custom A", "Custom B"};



        JComboBox<String> comboStyle = new JComboBox<>(styles);



        comboStyle.setSelectedItem(AppPreferences.getAnnotationStyle());



        pane.add(makeRow("Default Annotation Style:", comboStyle));







        addGap(pane);



        String[] colors = {"White", "Black", "Red", "Yellow"};



        JComboBox<String> comboColor = new JComboBox<>(colors);



        comboColor.setSelectedItem(AppPreferences.getAnnotationColor());



        pane.add(makeRow("Default Annotation Color:", comboColor));







        addGap(pane);



        String[] fonts = {"Arial", "Times New Roman", "Courier New"};



        JComboBox<String> comboFont = new JComboBox<>(fonts);



        comboFont.setSelectedItem(AppPreferences.getAnnotationFont());



        pane.add(makeRow("Default Annotation Font:", comboFont));







        addGap(pane);



        JCheckBox cbModalitySpecific = new JCheckBox(



                "Use modality-specific default annotation values",



                AppPreferences.isAnnotationModalitySpecific());



        pane.add(makeToggleRow("", cbModalitySpecific));







        return wrapVertically(pane);



    }







    // ===== PANE 10: DICOM Print (HOROS-20240407 Reference: AYDicomPrintPreferencePane.m) =====



    private JComponent createDICOMPrintPane() {



        JPanel pane = sectionBox("Print Layout");



        JComboBox<String> comboPrinter = new JComboBox<>(new String[]{"Default Printer", "None"});



        comboPrinter.setSelectedItem(AppPreferences.getDICOMPrintPrinter());



        pane.add(makeRow("Printer:", comboPrinter));







        addGap(pane);



        JComboBox<String> comboLayout = new JComboBox<>(



                new String[]{"Standard 2,1", "Large", "Small", "4-up", "9-up"});



        comboLayout.setSelectedItem(AppPreferences.getDICOMPrintLayout());



        pane.add(makeRow("Film Layout:", comboLayout));







        addGap(pane);



        JComboBox<String> comboOrientation = new JComboBox<>(new String[]{"Landscape", "Portrait"});



        comboOrientation.setSelectedItem(AppPreferences.getDICOMPrintOrientation());



        pane.add(makeRow("Film Orientation:", comboOrientation));







        addGap(pane);



        JCheckBox cbResize = new JCheckBox("Auto Resize Window to Fit Film", AppPreferences.isDICOMPrintAutoResize());



        pane.add(makeToggleRow("", cbResize));







        return wrapVertically(pane);



    }











    // ===== PANE 11: Listener (HOROS-20240407 Reference: OSIListenerPreferencePane.m) =====



    private JComponent createListenerPane() {



        JPanel pane = sectionBox("DICOM Listener Configuration");



        JTextField tfAET = new JTextField(AppPreferences.getListenerAETitle(), 30);



        pane.add(makeRow("AE Title:", tfAET));



        tfAET.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {



            public void insertUpdate(javax.swing.event.DocumentEvent e) { AppPreferences.setListenerAETitle(tfAET.getText()); }



            public void removeUpdate(javax.swing.event.DocumentEvent e) { AppPreferences.setListenerAETitle(tfAET.getText()); }



            public void changedUpdate(javax.swing.event.DocumentEvent e) { AppPreferences.setListenerAETitle(tfAET.getText()); }



        });







        addGap(pane);



        JTextField tfPort = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 10);



        pane.add(makeRow("TCP Port:", tfPort));



        tfPort.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {



            public void insertUpdate(javax.swing.event.DocumentEvent e) { try { AppPreferences.setListenerPort(Integer.parseInt(tfPort.getText())); } catch (Exception ex) {} }



            public void removeUpdate(javax.swing.event.DocumentEvent e) { /* ignore */ }



            public void changedUpdate(javax.swing.event.DocumentEvent e) { try { AppPreferences.setListenerPort(Integer.parseInt(tfPort.getText())); } catch (Exception ex) {} }



        });







        addGap(pane);



        JCheckBox cbAutoStart = new JCheckBox("Automatically start listener at application startup",



                AppPreferences.isListenerAutoStart());



        pane.add(makeToggleRow("", cbAutoStart));







        addSeparator(pane);



        JPanel tlsPanel = sectionBox("TLS / SSL Configuration");



        JCheckBox cbTLSEnabled = new JCheckBox("Enable TLS/SSL for DICOM communication",



                AppPreferences.isListenerTLSEnabled());



        tlsPanel.add(makeToggleRow("", cbTLSEnabled));







        addGap(tlsPanel);



        JTextField tfCertPath = new JTextField(AppPreferences.getListenerTLSAuthCertName(), 40);



        tlsPanel.add(makeRow("TLS Certificate Path:", tfCertPath));







        addGap(tlsPanel);



        JComboBox<String> comboCipher = new JComboBox<>(new String[]{



                "TLS_AES_256_GCM_SHA384", "TLS_RSA_WITH_AES_256_GCM_SHA384", "Default"});



        tlsPanel.add(makeRow("Preferred Cipher Suite:", comboCipher));







        pane.add(tlsPanel);







        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));



        JButton btnTestConnection = new JButton("Test Connection (C-ECHO)...");



        btnTestConnection.addActionListener(e -> {



            DicomNodeManager mngr = DicomNodeManager.getInstance();



            java.util.List<DicomNode> nodes = mngr.getNodes();



            if (nodes.isEmpty()) {



                JOptionPane.showMessageDialog(this, "No DICOM nodes configured. Add a node in Locations pane first.");



                return;



            }



            DicomNode node = nodes.get(0); // use first node for now



            String message = "Test connection to: " + node.getName() + ", AE Title: " + node.getAeTitle();



            int result = JOptionPane.showConfirmDialog(this,



                    new JScrollPane(new JTextArea(message, 5, 40)),



                    "C-ECHO Test", JOptionPane.OK_CANCEL_OPTION);



        });



        testRow.add(btnTestConnection);



        pane.add(testRow);



        return wrapVertically(pane);



    }











    // ===== PANE 12: Web Server / WADO (HOROS-20240407 Reference: OSIWebSharingReferencePane) =====



    private JComponent createWebServerPane() {



        JPanel pane = sectionBox("WADO / Web Sharing");



        JCheckBox cbWado = new JCheckBox("Enable WADO module", AppPreferences.isWadoEnabled());



        pane.add(makeToggleRow("", cbWado));







        addGap(pane);



        JTextField tfPort = new JTextField(String.valueOf(AppPreferences.getWebServerPort()), 10);



        pane.add(makeRow("Web Server Port:", tfPort));



        tfPort.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {



            public void insertUpdate(javax.swing.event.DocumentEvent e) { try { AppPreferences.setWebServerPort(Integer.parseInt(tfPort.getText())); } catch (Exception ex) {} }



            public void removeUpdate(javax.swing.event.DocumentEvent e) { /* ignore */ }



            public void changedUpdate(javax.swing.event.DocumentEvent e) { try { AppPreferences.setWebServerPort(Integer.parseInt(tfPort.getText())); } catch (Exception ex) {} }



        });







        addGap(pane);



        JTextField tfBaseUrl = new JTextField(AppPreferences.getWebBaseUrl(), 50);



        pane.add(makeRow("Base URL:", tfBaseUrl));







        addSeparator(pane);



        JPanel stowrsPanel = sectionBox("DICOM Web Services (STOW-RS)");



        JCheckBox cbStowrs = new JCheckBox("Enable STOW-RS", AppPreferences.isStowrsEnabled());



        stowrsPanel.add(makeToggleRow("", cbStowrs));







        addGap(pane);



        JButton btnUsers = new JButton("User Management (JSON)...");



        btnUsers.addActionListener(e -> {



            JTextArea ta = new JTextArea(AppPreferences.getUserManagementJSON(), 15, 40);



            JScrollPane sp = new JScrollPane(ta);



            int r = JOptionPane.showConfirmDialog(this, sp, "Edit Users", JOptionPane.OK_CANCEL_OPTION);



            if (r == JOptionPane.OK_OPTION) AppPreferences.setUserManagementJSON(ta.getText());



        });



        pane.add(btnUsers);







        return wrapVertically(pane);



    }











    // ===== PANE 13: On-Demand PACS Queries (HOROS-20240407 Reference: OSIPACSOnDemandPreferencePane.m) =====



    private JComponent createOnDemandPACSPane() {



        JPanel pane = sectionBox("On-Demand Query Settings");







        JCheckBox cbComparatives = new JCheckBox("Search comparatives on demand", AppPreferences.isOnDemandSearchComparatives());



        pane.add(makeToggleRow("", cbComparatives));







        addGap(pane);



        JCheckBox cbSmartAlbums = new JCheckBox("Query smart albums for additional studies", AppPreferences.isOnDemandQuerySmartAlbums());



        pane.add(makeToggleRow("", cbSmartAlbums));







        addGap(pane);



        JCheckBox cbImageRetrieval = new JCheckBox("Retrieve images on demand (rather than just headers)", AppPreferences.isOnDemandImageRetrieval());



        pane.add(makeToggleRow("", cbImageRetrieval));







        addSeparator(pane);



        JPanel timeoutPanel = makeRow("Query Timeout (seconds):",



                new JSpinner(new SpinnerNumberModel(AppPreferences.getPacsQueryTimeout(), 1, 300, 1)));



        pane.add(timeoutPanel);







        addGap(pane);



        JPanel maxStudies = makeRow("Max Studies Per Query:",



                new JSpinner(new SpinnerNumberModel(AppPreferences.getPacsMaxStudiesPerQuery(), 1, 1000, 10)));



        pane.add(maxStudies);







        return wrapVertically(pane);



    }











    // ===== PANE 14: Locations (DICOM Nodes) (HOROS-20240407 Reference: OSILocationsPreferencePanePref.m) =====



    private JComponent createLocationsPane() {



        JPanel pane = sectionBox("DICOM Node Manager");



        String[] aetCol = {"Name", "AE Title"};



        javax.swing.table.DefaultTableModel dm = new javax.swing.table.DefaultTableModel(aetCol, 0); JTable table = new JTable(dm);



        for (DicomNode n : DicomNodeManager.getInstance().getNodes()) {



            ((javax.swing.table.DefaultTableModel)table.getModel()).addRow(



                    new Object[]{n.getName(), n.getAeTitle()});



        }



        pane.add(new JScrollPane(table));







        addGap(pane);



        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));



        JButton btnAdd = new JButton("Add Node...");



        JButton btnEdit = new JButton("Edit Selected");



        JButton btnRemove = new JButton("Remove Selected Node");







        btnAdd.addActionListener(e -> JOptionPane.showMessageDialog(this, "Node editor (future)"));



        btnEdit.addActionListener(e -> { int row = table.getSelectedRow(); if (row >= 0) { DicomNode n = DicomNodeManager.getInstance().getNodes().get(row); /* edit dialog */ } });



        btnRemove.addActionListener(e -> { int row = table.getSelectedRow(); if (row >= 0) { DicomNode n = DicomNodeManager.getInstance().getNodes().get(row); DicomNodeManager.getInstance().removeNode(n); ((javax.swing.table.DefaultTableModel)table.getModel()).removeRow(row); } });







        btnPanel.add(btnAdd);



        btnPanel.add(btnEdit);



        btnPanel.add(btnRemove);



        pane.add(btnPanel);







        JButton btnVerify = new JButton("Test Connection (C-ECHO)...");



        btnVerify.addActionListener(e -> { int row = table.getSelectedRow(); if (row < 0) { JOptionPane.showMessageDialog(this, "Select a node first."); return; } /* C-ECHO stub */ JOptionPane.showMessageDialog(this, "Connection test stub"); });



        pane.add(btnVerify);







        return wrapVertically(pane);



    }











    // ===== PANE 15: Routing / Autorouting (HOROS-20240407 Reference: OSIAutoroutingPreferencePanePref.m) =====



    private JComponent createRoutingPane() {



        JPanel pane = sectionBox("Auto-routing Rules");



        JTextArea rulesArea = new JTextArea(10, 60);



        pane.add(new JScrollPane(rulesArea));







        addGap(pane);



        JButton btnAddRule = new JButton("Add Rule...");



        JButton btnEdit = new JButton("Edit Selected Rule");



        JButton btnDelete = new JButton("Delete Rule");



        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));



        btnPanel.add(btnAddRule);



        btnPanel.add(btnEdit);



        btnPanel.add(btnDelete);



        pane.add(btnPanel);







        addSeparator(pane);



        JButton btnTestRouting = new JButton("Test Routing Configuration");



        btnTestRouting.addActionListener(e -> JOptionPane.showMessageDialog(this,



                "Routing test (future implementation)", "Horos Reference", JOptionPane.INFORMATION_MESSAGE));



        pane.add(btnTestRouting);







        return wrapVertically(pane);



    }



}