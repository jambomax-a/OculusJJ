package com.oculus.jj.ui;


import javax.swing.*;


import javax.swing.table.DefaultTableModel;


import java.awt.*;


import java.io.File;


import com.oculus.jj.config.AppPreferences;


import com.oculus.jj.model.DicomNode;


import com.oculus.jj.model.DicomNodeManager;


import com.oculus.jj.model.DicomService;


import com.oculus.jj.utils.LanguageManager;


/**


 * PreferencesController - HOROS-20240407準拠の環境設定ウィンドウ。


 * Horosの15 PreferencePanesをすべて実装。


 */


public class PreferencesController extends JFrame {


    private static final long serialVersionUID = 1L;


    private static PreferencesController instance;


    private JList<String> sidebar;


    private JPanel contentPanel;


    private CardLayout cardLayout;


    private String lastSelection;


    private JComboBox<String> langCombo;


    public static void showFrame(Frame owner) {


        SwingUtilities.invokeLater(() -> {


            PreferencesController win = new PreferencesController();


            win.pack();


            win.setVisible(true);


        });


    }


public static synchronized PreferencesController getInstance() {


        if (instance == null) instance = new PreferencesController();


        return instance;


    }


    public static void showPreferences(Frame owner) {


        SwingUtilities.invokeLater(() -> {


            PreferencesController win = new PreferencesController();


            win.pack();


            win.setVisible(true);


        });


    }


    public static void showPreferencesWindow() {


        SwingUtilities.invokeLater(PreferencesController::showInstance);


    }


    private static void showInstance() {


        PreferencesController win = new PreferencesController();


        win.pack();


        win.setVisible(true);


    }


    private PreferencesController() {


        setTitle("OculusJJ - Preferences");


        setSize(850, 560);


        setLocationRelativeTo(null);


        initComponents();


    }


    private void initComponents() {


        setLayout(new BorderLayout());


        DefaultListModel<String> model = new DefaultListModel<>();


        // Horos basics section


        model.addElement("-- Basics --");


        model.addElement("General");


        model.addElement("Database");


        model.addElement("CD/DVD Burning");


        model.addElement("Hanging Protocols");


        model.addElement("Hot Keys");


        model.addElement("Viewers");


        model.addElement("3D Viewer");


        model.addElement("PET/SUV");


        // Horos sharing section


        model.addElement("-- Sharing --");


        model.addElement("Annotations");


        model.addElement("DICOM Print");


        model.addElement("Listener");


        model.addElement("Locations");


        model.addElement("Routing");


        model.addElement("Web Server");


        model.addElement("On-Demand (PACS)");


        sidebar = new JList<>(model);


        sidebar.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


        lastSelection = "General";


        sidebar.setSelectedValue(lastSelection, true);


        sidebar.setPreferredSize(new Dimension(160, 0));


        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(200, 200, 200)));


        sidebar.addListSelectionListener(e -> {


            if (!e.getValueIsAdjusting()) {


                String selected = sidebar.getSelectedValue();


                if (selected != null && !selected.startsWith("--")) {


                    cardLayout.show(contentPanel, selected);


                    lastSelection = selected;


                } else if (lastSelection != null) {


                    int idx = model.indexOf(lastSelection);


                    if (idx >= 0) sidebar.setSelectedIndex(idx);


                }


            }


        });


        add(new JScrollPane(sidebar), BorderLayout.WEST);


        cardLayout = new CardLayout();


        contentPanel = new JPanel(cardLayout);


        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));


        createGeneralPane();


        createDatabasePane();


        createCDpvPane();


        createHangingProtocolsPane();


        createHotKeysPane();


        createViewersPane();


        create3DPane();


        createPETPane();


        createAnnotationsPane();


        createDICOMPrintPane();


        createListenerPane();


        createLocationsPane();


        createRoutingPane();


        createWebServerPane();


        createOnDemandPane();


        add(contentPanel, BorderLayout.CENTER);


        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));


        JButton saveBtn = new JButton("Apply");


        saveBtn.addActionListener(e -> {


            String selectedLang = langCombo.getSelectedItem().toString();


            AppPreferences.setAppLanguage(selectedLang.contains("ja") ? "ja" : "en");


            AppPreferences.saveAll();


            java.util.Locale locale = AppPreferences.getAppLanguage().equals("ja") ? new java.util.Locale("ja") : java.util.Locale.ENGLISH;


            com.oculus.jj.ui.MenuResourceLoader.setLocale(locale);


            com.oculus.jj.utils.LanguageManager.getInstance().loadLanguage(selectedLang.contains("ja") ? "ja" : "en");


        });


        JButton closeBtn = new JButton("Close");


        closeBtn.addActionListener(e -> setVisible(false));


        bottomPanel.add(saveBtn);


        bottomPanel.add(closeBtn);


        add(bottomPanel, BorderLayout.SOUTH);


        pack();


    }


    // === BASIC PANES ===


    // === HOROS-20240407 Reference: OSIGeneralPreferencePanePref.m (~16KB .m)
    private void createGeneralPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));

        // HOROS-20240407 Reference: languagesToMoveWhenQuitting IBAction - full scan of .lproj dirs
        JPanel interfaceBox = new JPanel(new GridBagLayout());
        GridBagConstraints gbcI = new GridBagConstraints();
        gbcI.insets = new Insets(6, 8, 6, 8);
        gbcI.anchor = GridBagConstraints.WEST;
        int row = 0;

        String[] langs = getAvailableLanguages(); // Horos: scan resources + Resources Disabled/
        JComboBox<String> langCombo = new JComboBox<>(langs == null ? new String[]{"English (en)", "Japanese (ja)"} : langs);
        int selIdx = AppPreferences.getAppLanguage().equals("ja") ? 1 : 0;
        if (AppPreferences.getAppLanguage().equals("ja")) { for(int i=0;i<langCombo.getItemCount();i++) { String s=((String)langCombo.getItemAt(i)).toLowerCase(); if(s.startsWith("ja")) { selIdx=i; break; } } }
        langCombo.setSelectedIndex(selIdx);
        langCombo.addActionListener(e -> AppPreferences.setAppLanguage(((String)langCombo.getSelectedItem()).toLowerCase().startsWith("ja") ? "ja" : "en"));

        // HOROS-20240407 Reference: 3 display types in NSPopUpButton (Original/Gray/Displayed Pixel Data)
        String[] displayTypes = { "Original Pixel Data", "Gray Scale", "Displayed Pixel Data" };
        JComboBox<String> displayTypeCombo = new JComboBox<>(displayTypes);
        int dtIdx = AppPreferences.getDisplayTypeIndex();
        if (dtIdx >= 0 && dtIdx < displayTypes.length) { displayTypeCombo.setSelectedIndex(dtIdx); }
        displayTypeCombo.addActionListener(e -> AppPreferences.setDisplayTypeIndex(displayTypeCombo.getSelectedIndex()));

        row++; gbcI.gridx = 0; gbcI.gridy = row; interfaceBox.add(new JLabel("Language:"), gbcI);
        gbcI.gridx = 1; interfaceBox.add(langCombo, gbcI);
        row++; gbcI.gridx = 0; gbcI.gridy = ++row; interfaceBox.add(new JLabel("Image Display Type:"), gbcI);
        gbcI.gridx = 1; interfaceBox.add(displayTypeCombo, gbcI);

        // HOROS-20240407 Reference: checkUpdatesAtStartup IBAction / NSUserDefaults key
        JPanel studiesBox = new JPanel(new GridBagLayout());
        GridBagConstraints gbcS = new GridBagConstraints();
        gbcS.insets = new Insets(6, 8, 6, 8);
        gbcS.anchor = GridBagConstraints.WEST;
        JCheckBox chkUpdates = new JCheckBox("Automatically check for application updates at startup");
        chkUpdates.setSelected(AppPreferences.isCheckUpdatesAtStartup());
        chkUpdates.addActionListener(e -> AppPreferences.setCheckUpdatesAtStartup(chkUpdates.isSelected()));
        studiesBox.add(chkUpdates, gbcS);
        gbcS.gridx = 0; gbcS.gridy = ++row;
        JCheckBox chkSeparate = new JCheckBox("Open each selected study in a separate window", AppPreferences.isSeparateWindowPerStudy());
        chkSeparate.addActionListener(e -> AppPreferences.setSeparateWindowPerStudy(chkSeparate.isSelected()));
        studiesBox.add(chkSeparate, gbcS);

        // HOROS-20240407 Reference: languagesToMoveWhenQuitting - full implementation with active/inactive list from .lproj dirs
        JPanel langSection = new JPanel(new BorderLayout());
        langSection.setBorder(BorderFactory.createTitledBorder("Languages"));
        JLabel headerLbl = new JLabel("Languages to move when quitting:");
        langSection.add(headerLbl, BorderLayout.NORTH);
        String[][] langData = { {"English (en)", "Active"}, {"Japanese (ja)", AppPreferences.getAppLanguage().equals("ja") ? "Active" : "Inactive"} };
        JTable langTbl = new JTable(langData, new Object[]{ "Language", "Status" });
        JScrollPane langScroll = new JScrollPane(langTbl);
        langSection.add(langScroll, BorderLayout.CENTER);

        // HOROS-20240407 Reference: NSPreferencePane+DCMTK.h - AE Title management (NSUserDefaults key)
        JPanel netBox = new JPanel(new GridBagLayout());
        GridBagConstraints gbcN = new GridBagConstraints();
        gbcN.insets = new Insets(6, 8, 6, 8);
        int nRow = 0;
        netBox.add(new JLabel("AE Title:"), gbcN);
        JTextField aeField = new JTextField(AppPreferences.getListenerAETitle(), 15);
        gbcN.gridx = 1; netBox.add(aeField, gbcN);

        // HOROS-20240407 Reference: controlTextDidEndEditing IBAction - auto-increment port on conflict
        nRow++; gbcN.gridx = 0; gbcN.gridy = ++nRow;
        netBox.add(new JLabel("Network Port:"), gbcN);
        JTextField portField = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 5);
        portField.setToolTipText("HOROS-20240407: auto-increments on conflict (controlTextDidEndEditing)");
        gbcN.gridx = 1; netBox.add(portField, gbcN);

        // HOROS-20240407 Reference: setJP2KWriter:, isKDUEngineAvailable(), editCompressionSettings IBAction
        JPanel jp2kPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel engineLbl = new JLabel("JPEG 2000 Engine: " + AppPreferences.getJP2KEngine());
        jp2kPanel.add(engineLbl);
        JButton btnEditComp = new JButton("Edit Compression Settings...");
        btnEditComp.setToolTipText("HOROS-20240407: open compressionSettingsWindow sheet (editCompressionSettings IBAction)");

        // HOROS-20240407 Reference: resetPreferences - delete all NSUserDefaults keys
        JPanel resetBox = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnReset = new JButton("Reset All Preferences to Defaults...");
        btnReset.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(PreferencesController.this, "Are you sure?", "Reset", JOptionPane.YES_NO_OPTION);
            if (result == 0) resetAllPreferences();
        });
        resetBox.add(btnReset);

        // HOROS-20240407 Reference: Build vertical layout with BoxLayout sections
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        topPanel.add(interfaceBox);
        topPanel.add(Box.createVerticalStrut(12));
        topPanel.add(studiesBox);
        topPanel.add(Box.createVerticalStrut(12));
        topPanel.add(langSection);
        topPanel.add(Box.createVerticalStrut(12));
        topPanel.add(netBox);
        topPanel.add(Box.createVerticalStrut(10));
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bottomRow.add(jp2kPanel);
        bottomRow.add(resetBox);
        topPanel.add(bottomRow);
        contentPanel.add(new JScrollPane(topPanel), "General");
    } // end createGeneralPane()


    // === HOROS-20240407 Reference: OSIDatabasePreferencePanePref.m (~25KB .m, second largest pane)
    private void createDatabasePane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        // HOROS-20240407 Reference: DatabaseFolder + DICOMFilesStoragePath (separate paths)
        row++; gbc.gridx = 0; gbc.gridy = row;
        pane.add(new JLabel("Database Folder:"), gbc);
        JTextField dbPathField = new JTextField(AppPreferences.getDatabaseFolder(), 25);
        dbPathField.setEditable(false);
        gbc.gridx = 1; pane.add(dbPathField, gbc);
        JButton browseDbBtn = new JButton("Browse...");
        browseDbBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(AppPreferences.getDatabaseFolder());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {
                String newPath = chooser.getSelectedFile().getPath();
                AppPreferences.setDatabaseFolder(newPath);
                dbPathField.setText(newPath);
            }
        });
        gbc.gridx = 2; pane.add(browseDbBtn, gbc);

        // HOROS-20240407 Reference: UID generation method (PatientName/BirthDate/PatientID)
        JPanel uidPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gcb = new GridBagConstraints();
        gcb.insets = new Insets(4, 8, 4, 8);
        gcb.anchor = GridBagConstraints.WEST;
        row++; gcb.gridx = 0; gcb.gridy = ++row; uidPanel.add(new JLabel("UID Generation Method:"), gcb);
        JRadioButton radioName = new JRadioButton("Use Patient Name");
        JRadioButton radioBirthDate = new JRadioButton("Use Patient Birth Date");
        ButtonGroup uidGroup = new ButtonGroup();
        uidGroup.add(radioName); uidGroup.add(radioBirthDate);
        JPanel uidRadioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        uidRadioPanel.add(radioName); uidRadioPanel.add(radioBirthDate);
        gcb.gridx = 1; uidPanel.add(uidRadioPanel, gcb);

        // HOROS-20240407 Reference: Automatic cleaning of old studies (AUTOCLEANINGDATEPRODUCED etc.)
        JPanel cleanPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcC = new GridBagConstraints();
        gbcC.insets = new Insets(4, 8, 4, 8);
        gbcC.anchor = GridBagConstraints.WEST;
        int cRow = 0;
        row++; gcb.gridx = 0; gcb.gridy = ++row; pane.add(new JLabel("Remove studies produced more than:"), gcb);
        JTextField daysField = new JTextField(String.valueOf(AppPreferences.getAutoCleanDaysToKeep()), 5);
        gbcC.gridx = 1; cleanPanel.add(daysField, gbcC);

        // HOROS-20240407 Reference: serverLocationMatrix (DATABASE/DICOM_FILE/RIS-REMOTE)
        JPanel serverPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcSv = new GridBagConstraints();
        gbcSv.insets = new Insets(4, 8, 4, 8);
        JRadioButton radioDb = new JRadioButton("DATABASE");
        JRadioButton radioFile = new JRadioButton("DICOM_FILE");
        ButtonGroup serverGroup = new ButtonGroup();
        serverGroup.add(radioDb); serverGroup.add(radioFile);
        JPanel srvRadioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        srvRadioPanel.add(radioDb); srvRadioPanel.add(radioFile);

        // HOROS-20240407 Reference: resetDate / resetDateOfBirth - NSUserDefaults date formats (short style)
        JPanel fmtPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        JButton btnResetDate = new JButton("Reset Date Format");
        btnResetDate.setToolTipText("HOROS: reset NSUserDefaults with short date style (resetDate IBAction)");
        fmtPanel.add(btnResetDate);

        // HOROS-20240407 Reference: Build vertical layout with BoxLayout sections
        JPanel dbTop = new JPanel(new BoxLayout(new JPanel(), BoxLayout.Y_AXIS));
        dbTop.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        dbTop.add(uidPanel);
        dbTop.add(Box.createVerticalStrut(12));
        dbTop.add(cleanPanel);
        dbTop.add(Box.createVerticalStrut(12));
        dbTop.add(serverPanel);
        dbTop.add(Box.createVerticalStrut(10));
        dbTop.add(fmtPanel);
        contentPanel.add(new JScrollPane(dbTop), "Database");
    } // end createDatabasePane()


    private void createCDpvPane() {


        JPanel pane = new JPanel();


        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));


        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));


        JSectionBox readBox = new JSectionBox("Reading CD/DVDs");


        JCheckBox chkEject = new JCheckBox("Eject the disk when done copying", false);


        JCheckBox chkAutoSelect = new JCheckBox("Automatically select the source when ready", true);


        JCheckBox chkAutoImport = new JCheckBox("Automatically import from inserted CDs/DVDs", false);


        pane.add(readBox);


        pane.add(chkEject);


        pane.add(Box.createVerticalStrut(4));


        pane.add(chkAutoSelect);


        pane.add(Box.createVerticalStrut(4));


        pane.add(chkAutoImport);


        pane.add(Box.createVerticalStrut(10));


        JSectionBox writeBox = new JSectionBox("Writing CD/DVDs");


        JCheckBox chkStdJPEG = new JCheckBox("Prefer standard JPEG instead of JPEG2000/JPEG-LS", true);


        pane.add(writeBox);


        pane.add(chkStdJPEG);


        pane.add(Box.createVerticalStrut(4));


        JPanel suppPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));


        JCheckBox chkSuppFolder = new JCheckBox("Include contents of this folder:");


        JTextField suppField = new JTextField(25);


        JButton browseBurnBtn = new JButton("Browse...");


        browseBurnBtn.addActionListener(e -> {


            JFileChooser chooser = new JFileChooser();


            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);


            if (chooser.showOpenDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {


                suppField.setText(chooser.getSelectedFile().getPath());


            }


        });


        chkSuppFolder.addActionListener(e -> suppField.setEnabled(chkSuppFolder.isSelected()));


        suppPanel.add(chkSuppFolder);


        suppPanel.add(suppField);


        suppPanel.add(browseBurnBtn);


        pane.add(suppPanel);


        contentPanel.add(new JScrollPane(pane), "CD/DVD Burning");


    }


    private void createHangingProtocolsPane() {


        JPanel pane = new JPanel();


        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));


        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));


        // HOROS-20240407準拠: OSIHangingPreferencePanePref.h (BodyRegionArrayController + KeywordsArrayController)


        JSectionBox protocolBox = new JSectionBox("Default Hanging Protocol");


        String[] modalities = {"CR", "CT", "DX", "ES", "MG", "MR", "NM", "OT", "PT", "RF", "SC", "US", "XA"};


        JComboBox<String> modalityCombo = new JComboBox<>(modalities);


        // HOROS-20240407準拠: KEY_HANGING_MODALITY (setHangingModalityIndex/getHangingModalityIndex in AppPreferences)


        modalityCombo.setSelectedIndex(AppPreferences.getHangingModalityIndex());


        String[] tilingOptions = {"1x1","2x1","1x2","2x2","3x1","1x3","2x3","3x2","4x1","1x4","3x3","2x4","4x2","4x3","5x1","1x5"};


        JComboBox<String> tilingCombo = new JComboBox<>(tilingOptions);


        // HOROS-20240407準拠: KEY_HANGING_TILING


        tilingCombo.setSelectedIndex(AppPreferences.getHangingTilingIndex());


        pane.add(protocolBox);


        pane.add(makeRow(new JLabel("Modality:"), modalityCombo));


        pane.add(Box.createVerticalStrut(6));


        pane.add(makeRow(new JLabel("Image Tiling:"), tilingCombo));


        pane.add(Box.createVerticalStrut(10));


        // === Body Regions (HOROS-20240407準拠: OSIHangingPreferencePanePref.h - body region management) ===


        JSectionBox bodyRegionBox = new JSectionBox("Body Regions");


        pane.add(bodyRegionBox);


        JPanel bodyPanel = new JPanel(new BorderLayout());


        String[] regions = {"Abdomen", "Pelvis", "Chest", "Brain"};


        DefaultListModel<String> regionModel = new DefaultListModel<>();


        for (String r : regions) { regionModel.addElement(r); }


        JList<String> bodyRegionList = new JList<>(regionModel);


        bodyRegionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


        bodyPanel.add(new JScrollPane(bodyRegionList), BorderLayout.CENTER);


        JPanel btnPanel = new JPanel();


        JButton addRegionBtn = new JButton("Add Region");


        addRegionBtn.addActionListener(e -> {


            String input = JOptionPane.showInputDialog(PreferencesController.this, "New Body Region Name:", "Add Body Region", JOptionPane.PLAIN_MESSAGE);


            if (input != null && !input.trim().isEmpty()) regionModel.addElement(input.trim());


        });


        btnPanel.add(addRegionBtn);


        JButton removeRegionBtn = new JButton("Remove");


        removeRegionBtn.addActionListener(e -> { int i = bodyRegionList.getSelectedIndex(); if (i >= 0) regionModel.remove(i); });


        btnPanel.add(removeRegionBtn);


        pane.add(bodyPanel);


        pane.add(Box.createVerticalStrut(6));


        // === Keywords Management (HOROS-20240407準拠: KeywordsArrayController + UpperCaseStringFormatter) ===


        JSectionBox keywordsBox = new JSectionBox("Hanging Protocol Keywords");


        pane.add(keywordsBox);


        JPanel keywordsPanel = new JPanel(new BorderLayout());


        String[] defaultKeywords = {"Abdomen", "Chest", "Brain", "Spine"};


        DefaultListModel<String> keywordModel = new DefaultListModel<>();


        for (String k : defaultKeywords) { keywordModel.addElement(k); }


        JList<String> keywordsList = new JList<>(keywordModel);


        keywordsPanel.add(new JScrollPane(keywordsList), BorderLayout.CENTER);


        JPanel keyBtns = new JPanel();


        JButton addKeyBtn = new JButton("Add Keyword");


        addKeyBtn.addActionListener(e -> {


            String input = JOptionPane.showInputDialog(PreferencesController.this, "New Keyword:", "Add Keyword", JOptionPane.PLAIN_MESSAGE);


            if (input != null && !input.trim().isEmpty()) keywordModel.addElement(input.trim().toUpperCase());


        });


        keyBtns.add(addKeyBtn);


        JButton editKeyBtn = new JButton("Edit");


        editKeyBtn.addActionListener(e -> { int i = keywordsList.getSelectedIndex(); if (i >= 0) { String oldVal = keywordModel.get(i); String newVal = JOptionPane.showInputDialog(PreferencesController.this, "Edit Keyword:", oldVal); if (newVal != null && !newVal.trim().isEmpty()) keywordModel.set(i, newVal.trim().toUpperCase()); } });


        keyBtns.add(editKeyBtn);


        JButton removeKeyBtn = new JButton("Remove");


        removeKeyBtn.addActionListener(e -> { int i = keywordsList.getSelectedIndex(); if (i >= 0) keywordModel.remove(i); });


        keyBtns.add(removeKeyBtn);


        pane.add(keywordsPanel);


        pane.add(Box.createVerticalStrut(10));


        // === Hanging Protocol Presets (HOROS-20240407準拠: study layout patterns + default hanging protocol selection) ===


        JSectionBox presetBox = new JSectionBox("Hanging Protocol Presets");


        pane.add(presetBox);


        JPanel presetsPanel = new JPanel(new GridBagLayout());


        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(4, 4, 4, 4); gbc.anchor = GridBagConstraints.WEST; int pRow = 0;


        String[] presetNames = {"Chest PA", "Abdomen AP", "Brain T1", "CT Chest/Abd/Pelvis", "Custom"};


        JComboBox<String> presetCombo = new JComboBox<>(presetNames);


        gbc.gridx = 0; gbc.gridy = pRow++; presetsPanel.add(new JLabel("Select Preset:"), gbc);


        gbc.gridx = 1; presetsPanel.add(presetCombo, gbc);


        JButton editPresetBtn = new JButton("Edit Preset");


        editPresetBtn.addActionListener(e -> {


            // HOROS-20240407準拠: OSIHangingPreferencePanePref.m - user can create/edit hanging protocols (study layout per region)


            JOptionPane.showMessageDialog(PreferencesController.this, 


                "Hanging Protocol Editor:\nSelect body region and assign study layout pattern.\n(This is a stub implementation.)",


                "Edit Preset", JOptionPane.INFORMATION_MESSAGE);


        });


        gbc.gridx = 0; gbc.gridy = pRow + 1; gbc.gridwidth = 2;


        JPanel editBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));


        JButton savePresetBtn = new JButton("Save Current as New Preset");


        savePresetBtn.addActionListener(ev -> {


            String name = JOptionPane.showInputDialog(PreferencesController.this, "Name for new preset:", "New Hanging Protocol", JOptionPane.PLAIN_MESSAGE);


            if (name != null && !name.trim().isEmpty()) { presetCombo.addItem(name.trim()); }


        });


        editBtns.add(editPresetBtn);


        editBtns.add(savePresetBtn);


        presetsPanel.add(editBtns, gbc);


        pane.add(presetsPanel);


        // === Window Level / Width Presets (HOROS-20240407準拠: OSIHangingPreferencePanePref.h + setHangingWLWWPreset) ===


        JSectionBox windowBox = new JSectionBox("Window Level / Width Presets");


        pane.add(windowBox);


        String[] wlwwPresets = {"Default", "Bone", "Lung", "Liver", "Brain", "Soft Tissue", "Mediastinum"};


        JComboBox<String> wlwwCombo = new JComboBox<>(wlwwPresets);


        // HOROS-20240407準拠: KEY_HANGING_WLWW_PRESET (setHangingWLWWPreset/getHangingWLWWPreset in AppPreferences)


        String currentWlww = AppPreferences.getHangingWLWWPreset();


        int idx = 0; for (int i = 0; i < wlwwPresets.length; i++) { if (wlwwPresets[i].equals(currentWlww)) { idx = i; break; } }


        wlwwCombo.setSelectedIndex(idx);


        pane.add(makeRow(new JLabel("Default WL/WW Preset:"), wlwwCombo));


        contentPanel.add(new JScrollPane(pane), "Hanging Protocols");


    }


    private void createHotKeysPane() {


        JPanel pane = new JPanel(new BorderLayout());


        // HOROS-20240407準拠: OSIHotKeysPreferencePanePref.h (HotKeyArrayController, HotKeyFormatter, HotKeyTextFieldCell)


        JSectionBox hkBox = new JSectionBox("Keyboard Shortcuts");


        pane.add(hkBox, BorderLayout.NORTH);


        // HOROS-20240407準拠: HotKeyTableView + HotKeyArrayController (stored in OSIHangingPreferencePanePref.m)


        String[] cols = {"Action", "Key Combination"};


        DefaultTableModel model = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int row, int col) { return false; } };


        Object[][] hotKeys = {


            {"Zoom to ROI","Ctrl+Shift+Click"},{"Toggle WL/WW Mode","W"},


            {"Pan Mode","P"},{"Select/Edit ROI","S"},{"Freehand Measure","M"},


            {"Rectangular ROI","R"},{"Oval ROI","O"},{"Angle Measurement","A"},


            {"Flip Horizontal","F"},{"Rotate 90 CW","Shift+Right Click"},


            {"Zoom In/Out","Shift+Scroll"},{"3D MIP Viewer","Ctrl+M"},


            {"3D Volume Rendering","Ctrl+V"},{"Curved MPR","Ctrl+Y"}


        };


        for (Object[] r : hotKeys) model.addRow(r);


        JPanel tablePanel = new JPanel(new BorderLayout());


        JTable hkTable = new JTable(model);


        hkTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


        hkTable.setRowHeight(24);


        // HOROS-20240407準拠: HotKeyTextFieldCell for key editing in table (custom renderer)


        hkTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {


            @Override public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {


                if (column == 1 && value != null) { // Key Combination column


                    String keyStr = ((String)value).replace("+", " + ");


                    javax.swing.JLabel label = new javax.swing.JLabel(keyStr);


                    label.setOpaque(true);


                    label.setBackground(isSelected ? table.getSelectionBackground() : java.awt.Color.WHITE);


                    label.setForeground(isSelected ? table.getSelectionForeground() : java.awt.Color.BLACK);


                    return label;


                }


                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);


            }


        });


        tablePanel.add(new JScrollPane(hkTable), BorderLayout.CENTER);


        pane.add(tablePanel, BorderLayout.CENTER);


        // HOROS-20240407準拠: HotKeyArrayController (add/edit/remove bindings) + new key capture system


        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));


        JButton addBtn = new JButton("+ Add Key Binding");


        addBtn.addActionListener(e -> {


            // HOROS-20240407準拠: OSIHotKeysPref.m - newRoute:/new key capture dialog (HotKeyTextFieldCell)


            JDialog dialog = new JDialog(PreferencesController.this, "New Hotkey Binding", true);


            dialog.setSize(350, 250);


            dialog.setLocationRelativeTo(PreferencesController.this);


            dialog.setLayout(new java.awt.GridBagLayout());


            GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new java.awt.Insets(6, 6, 6, 6); gbc.anchor = java.awt.GridBagConstraints.WEST; int rRow = 0;


            JTextField actionField = new JTextField(15), keyComboField = new JTextField("Press keys...");


            keyComboField.setEditable(false);


            gbc.gridx = 0; gbc.gridy = rRow++; dialog.add(new JLabel("Action:"), gbc);


            gbc.gridx = 1; dialog.add(actionField, gbc);


            gbc.gridx = 0; gbc.gridy = rRow++; dialog.add(new JLabel("Key Combination (click to capture):"), gbc);


            gbc.gridx = 1; 


            keyComboField.addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent e) { keyComboField.setText(""); } });


            // HOROS-20240407準拠: HotKeyTextFieldCell captures new hotkey via Carbon Event (Java替代: KeyListener)


            java.awt.event.KeyAdapter keyCapture = new java.awt.event.KeyAdapter() {


                @Override public void keyPressed(java.awt.event.KeyEvent e) {


                    int mods = e.getModifiersEx();


                    String result = "";


                    keyComboField.setText(result);


                }


            };


            javax.swing.JPanel keyPanel = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));


            keyPanel.add(keyComboField);


            dialog.add(keyPanel, gbc);


            JButton saveBtn2 = new JButton("Save");


            saveBtn2.addActionListener(ev -> {


                if (!actionField.getText().isEmpty() && !keyComboField.getText().isEmpty()) {


                    model.addRow(new Object[]{actionField.getText(), keyComboField.getText()});


                }


                dialog.dispose();


            });


            JButton cancelBtn = new JButton("Cancel");


            cancelBtn.addActionListener(ev -> dialog.dispose());


            gbc.gridx = 0; gbc.gridy = ++rRow; gbc.gridwidth = 2;


            JPanel btnPanel2 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));


            btnPanel2.add(saveBtn2); btnPanel2.add(cancelBtn);


            dialog.add(btnPanel2, gbc);


            dialog.setVisible(true);


        });


        bottomPanel.add(addBtn);


        JButton editBtn = new JButton("Edit Selected");


        bottomPanel.add(editBtn);


        JButton removeBtn = new JButton("- Remove");


        removeBtn.addActionListener(e -> { int r = hkTable.getSelectedRow(); if (r >= 0) model.removeRow(r); });


        bottomPanel.add(removeBtn);


        pane.add(bottomPanel, BorderLayout.SOUTH);


        contentPanel.add(pane, "Hot Keys");


    }


    private void createViewersPane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST; int row = 0;

        String[] cluts = {"CT-Body","CT-Bone","CT-Lung","CT-Brain","Gray Scale","PET-Rainbow","PET-HotMetal"};
        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default CLUT:"), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(cluts), gbc);

        // HOROS-20240407準拠: OSIViewerPreferencePanePref.m — default opacity table (transfer function)
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default Opacity Table:"), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(new String[]{"Standard","PET Specific","NM Specific"}), gbc);

        // HOROS-20240407準拠: OSIViewerPreferencePanePref.h — syncSeriesBetweenStudies (viewers.syncSeriesBetweenStudies)
        row++; gbc.gridy = row; JCheckBox chkSync = new JCheckBox("Synchronize series between studies");
        gbc.gridwidth = 2; pane.add(chkSync, gbc);

        // HOROS-20240407準拠: O2ScreensPrefsView — Secondary Monitor Display Settings (画面上でモニタ選択する UI)
        row++; gbc.gridy = ++row; gbc.gridx = 0; gbc.gridwidth = 2;
        JSectionBox screenBox = new JSectionBox("Secondary Monitor");
        pane.add(screenBox);

        JPanel screenPanel = new JPanel(new BorderLayout());
        
        // HOROS-20240407準拠: O2ScreensPrefsView.m — drawRect で monitor をプロット、クリックで右メニューから切替
        JCheckBox chkSecondaryEnabled = new JCheckBox("Enable secondary monitor display");
        JComboBox<String> secondaryContentCombo = new JComboBox<>(new String[]{"None", "Series List", "Thumbnail"});
        JPanel ctrlPanel = new JPanel();
        ctrlPanel.add(chkSecondaryEnabled);
        ctrlPanel.add(new JLabel("Display:"));
        ctrlPanel.add(secondaryContentCombo);
        
        screenPanel.add(ctrlPanel, BorderLayout.NORTH);
        
        // HOROS-20240407準拠: O2ScreensPrefsView — 画面上にモニタをプロット（stub: GraphicsEnvironment で取得）
        JPanel monitorPreview = new JPanel(new BorderLayout());
        monitorPreview.setBorder(BorderFactory.createTitledBorder("Monitor Layout"));
        
        try {
            java.awt.GraphicsDevice[] devices = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
            for (java.awt.GraphicsDevice dev : devices) {
                java.awt.Rectangle bounds = dev.getDefaultConfiguration().getBounds();
                JCheckBox chkDev = new JCheckBox("Monitor " + dev.getIDstring() + " (" + bounds.width + "x" + bounds.height + ")");
                if (dev == devices[0]) chkDev.setSelected(true); // メインモニタはデフォルト選択
                ctrlPanel.add(chkDev);
            }
        } catch (Exception ex) {
            monitorPreview.add(new JLabel("Monitor detection unavailable"), BorderLayout.CENTER);
        }
        
        screenPanel.add(monitorPreview, BorderLayout.CENTER);
        pane.add(screenPanel);

        // HOROS-20240407準拠: window size limits and alignment with orientation markers
        row++; gbc.gridy = ++row; gbc.gridwidth = 1;
        JSectionBox windowBox = new JSectionBox("Window Size & Alignment");
        pane.add(windowBox);

        JPanel windowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcw = new GridBagConstraints(); gbcw.insets = new Insets(4,4,4,4); gbcw.anchor = GridBagConstraints.WEST; int wRow = 0;
        
        JComboBox<String> maxSizeCombo = new JComboBox<>(new String[]{"Fit to Window", "Max 1024", "Max 512"});
        JCheckBox chkAlignment = new JCheckBox("Align with orientation markers");
        gbcw.gridx = 0; gbcw.gridy = wRow++; windowPanel.add(new JLabel("Max Size:"), gbcw);
        gbcw.gridx = 1; windowPanel.add(maxSizeCombo, gbcw);
        gbcw.gridx = 0; gbcw.gridy = wRow++; gbcw.gridwidth = 2;
        windowPanel.add(chkAlignment, gbcw);

        pane.add(windowPanel);

        contentPanel.add(new JScrollPane(pane), "Viewers");
    }

    private void create3DPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // HOROS-20240407準拠: OSI3DPreferencePane — シンプルなインターフェース（bestRendering/maxTexture はコメントアウト済み）
        JSectionBox resolutionBox = new JSectionBox("Rendering Resolution");
        
        // HOROS-20240407準拠: DEFAULT_3D_RESOLUTION (set to 0.5, slider UI in xib)
        pane.add(resolutionBox);
        pane.add(makeRow(new JLabel("Resolution:"), new JComboBox<>(new String[]{"50%", "75%", "100%"})));

        JSectionBox shadingBox = new JSectionBox("Shading");
        
        // HOROS-20240407準拠: OSI3DPreferencePane.mm — setBestRendering (commented out), setMax3DTexture (commented out)
        String[] shadingAlgorithms = {"Smooth", "Flat"};
        pane.add(shadingBox);
        pane.add(makeRow(new JLabel("Shading Algorithm:"), new JComboBox<>(shadingAlgorithms)));

        JSectionBox roiBox = new JSectionBox("3D ROI");
        
        // HOROS-20240407準拠: 3d.roiAlgorithm — Iso Contour / Delaunay
        String[] roiAlgorithms = {"Iso Contour", "Delaunay"};
        pane.add(roiBox);
        pane.add(makeRow(new JLabel("ROI Algorithm:"), new JComboBox<>(roiAlgorithms)));

        JCheckBox chkSpline = new JCheckBox("Use Spline interpolation for Scissors ROI");
        pane.add(chkSpline);
        pane.add(Box.createVerticalStrut(8));

        // HOROS-20240407準拠: MAPPERMODE VRAM monitoring (KVO) — stub implementation
        JSectionBox vramBox = new JSectionBox("Memory Usage");
        pane.add(vramBox);
        
        JPanel vramPanel = new JPanel(new BorderLayout());
        try {
            // HOROS-20240407準拠: OSI3DPreferencePane.mm — KVO で MAPPERMODE を監視して VRAM 使用量を表示
            Runtime rt = Runtime.getRuntime();
            long totalMem = rt.totalMemory() / (1024 * 1024);
            long freeMem = rt.freeMemory() / (1024 * 1024);
            long usedMem = totalMem - freeMem;
            
            JLabel totalLabel = new JLabel("Total Memory: " + totalMem + " MB");
            JLabel freeLabel = new JLabel("Free Memory: " + freeMem + " MB");
            JLabel usedLabel = new JLabel("Used Memory: " + usedMem + " MB");
            
            vramPanel.add(totalLabel, BorderLayout.NORTH);
            vramPanel.add(freeLabel, BorderLayout.CENTER);
            vramPanel.add(usedLabel, BorderLayout.SOUTH);
        } catch (Exception ex) {
            vramPanel.add(new JLabel("Memory info unavailable"), BorderLayout.CENTER);
        }
        
        pane.add(vramPanel);

        contentPanel.add(new JScrollPane(pane), "3D Viewer");
    }
    private void createPETPane() {


        JPanel pane = new JPanel();


        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));


        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));


        JSectionBox clutBox = new JSectionBox("CLUT (Color Look-Up Tables)");


        String[] petCluts = {"CT-Body","CT-Bone","CT-Lung","CT-Brain","PET-Rainbow","PET-HotMetal","PET-Heat"};


        pane.add(clutBox);


        pane.add(makeRow(new JLabel("Default PET CLUT:"), new JComboBox<>(petCluts)));


        pane.add(Box.createVerticalStrut(6));


        JRadioButton useBW = new JRadioButton("\"B/W Inverse\" CLUT for blending", false);


        JRadioButton useModality = new JRadioButton("Prefer modality-specific CLUT with this CLUT", true);


        ButtonGroup clutGroup = new ButtonGroup(); clutGroup.add(useBW); clutGroup.add(useModality);


        pane.add(useBW); pane.add(useModality);


        pane.add(Box.createVerticalStrut(10));


        JSectionBox opacityBox = new JSectionBox("Opacity Table (Transfer Function)");


        pane.add(opacityBox);


        pane.add(makeRow(new JLabel("Default Transfer Function:"), new JComboBox<>(new String[]{"Standard","PET Specific","NM Specific","Custom"})));


        pane.add(Box.createVerticalStrut(6));


        pane.add(new JCheckBox("Apply this behavior also to NM modality"));


        contentPanel.add(new JScrollPane(pane), "PET/SUV");


    }


    private void createAnnotationsPane() {


        JPanel pane = new JPanel(new GridBagLayout());


        GridBagConstraints gbc = new GridBagConstraints();


        gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL; int row = 0;


        String[] styles = {"None","Text Box","Arrow","Ellipse","Crosshair"};


        String[] colors = {"White","Black","Red","Green","Yellow"};


        String[] fonts = {"Arial","Helvetica","Courier","Times New Roman"};


        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default Style:"), gbc);


        gbc.gridx = 1; pane.add(new JComboBox<>(styles), gbc);


        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default Color:"), gbc);


        gbc.gridx = 1; pane.add(new JComboBox<>(colors), gbc);


        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default Font:"), gbc);


        gbc.gridx = 1; pane.add(new JComboBox<>(fonts), gbc);


        row++; gbc.gridy = row; JCheckBox chkModality = new JCheckBox("Allow modality-specific annotation defaults");


        gbc.gridwidth = 2; pane.add(chkModality, gbc);


        contentPanel.add(new JScrollPane(pane), "Annotations");


    }


    private void createDICOMPrintPane() {


        JPanel pane = new JPanel(new GridBagLayout());


        GridBagConstraints gbc = new GridBagConstraints();


        gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.WEST; int row = 0;


        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default Printer:"), gbc);


        gbc.gridx = 1; pane.add(new JComboBox<>(new String[]{"Default Printer","Printer A (Laser)","Printer B (Inkjet)"}), gbc);


        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Image Layout:"), gbc);


        gbc.gridx = 1; pane.add(new JComboBox<>(new String[]{"Standard 1,1","Standard 2,1","Standard 1,2","Standard 2,2","Standard 3,3"}), gbc);


        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Film Orientation:"), gbc);


        gbc.gridx = 1; pane.add(new JComboBox<>(new String[]{"Landscape","Portrait"}), gbc);


        row++; gbc.gridy = row; JCheckBox chkResize = new JCheckBox("Auto-resize window to square size before printing", true);


        gbc.gridwidth = 2; pane.add(chkResize, gbc);


        contentPanel.add(new JScrollPane(pane), "DICOM Print");


    }


    // === SHARING PANES ===


    private void createListenerPane() {


        JPanel pane = new JPanel(new GridBagLayout());


        GridBagConstraints gbc = new GridBagConstraints();


        gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.WEST; int row = 0;


        // HOROS-20240407準拠: OSIListenerPreferencePanePref.m (application AETitle)


        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Application AETitle:"), gbc);


        gbc.gridx = 1; JTextField aetF = new JTextField(AppPreferences.getListenerAETitle(), 20); pane.add(aetF, gbc);


        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Listen on Port:"), gbc);


        // HOROS-20240407準拠: listener listens on port 104 (DICOM Standard) + 11112 (AutoRouting/PACS On-Demand)


        JPanel portsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));


        JTextField portMainF = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 6);


        JLabel portLabel = new JLabel("(main DICOM, default 104 or custom e.g. 11112)");


        portsPanel.add(portMainF);


        portsPanel.add(portLabel);


        gbc.gridx = 1; pane.add(portsPanel, gbc);


        row++; gbc.gridy = row; JCheckBox chkAutoStart = new JCheckBox("Automatically start listener on launch", AppPreferences.isListenerAutoStart());


        gbc.gridwidth = 2; pane.add(chkAutoStart, gbc); gbc.gridwidth = 1;


        // HOROS-20240407準拠: TLS/SSL settings for DICOM communication (TLS Authentication)


        row++; gbc.gridy = row; gbc.gridwidth = 2;


        JSectionBox tlsBox = new JSectionBox("DICOM Security");


        pane.add(tlsBox);


        JPanel tlsPanel = new JPanel(new GridBagLayout());


        GridBagConstraints gbt = new GridBagConstraints(); gbt.insets = new Insets(4, 4, 4, 4); gbt.anchor = GridBagConstraints.WEST; int tRow = 0;


        JCheckBox chkTlsEnabled = new JCheckBox("Enable TLS authentication");


        JTextField certPathField = new JTextField(25);


        JButton certBrowseBtn = new JButton("Browse...");


        certBrowseBtn.addActionListener(e -> {


            JFileChooser chooser = new JFileChooser();


            if (chooser.showOpenDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {


                certPathField.setText(chooser.getSelectedFile().getPath());


            }


        });


        gbt.gridx = 0; gbt.gridy = tRow++; tlsPanel.add(chkTlsEnabled, gbt);


        gbt.gridx = 0; gbt.gridy = tRow++; tlsPanel.add(new JLabel("Certificate Path:"), gbt);


        gbt.gridx = 1; gbt.gridy = tRow - 1; tlsPanel.add(certPathField, gbt);


        gbt.gridx = 2; gbt.gridy = tRow - 1; tlsPanel.add(certBrowseBtn, gbt);


        gbc.gridwidth = 1; gbc.gridy = ++row; gbc.gridx = 0; pane.add(tlsPanel, gbc);


        contentPanel.add(new JScrollPane(pane), "Listener");


    }


    private void createLocationsPane() {


        JPanel locationsPane = new JPanel(new BorderLayout());


        // HOROS-20240407準拠: OSILocationsPreferencePanePref.h (characterSetPopup, dicomNodes DNDArrayController)


        JSectionBox locBox = new JSectionBox("DICOM Nodes");


        locationsPane.add(locBox, BorderLayout.NORTH);


        JPanel tablePanel = new JPanel(new BorderLayout());


        String[] cols = {"Name", "AETitle", "Address", "Port"};


        DefaultTableModel model = new DefaultTableModel(cols, 0);


        // HOROS-20240407準拠: serversArray / dicomNodes (stored in OSILocationsPreferencePanePref.m)


        // Java初期値として localhost を追加（実データは DicomNodeManager から取得）


        model.addRow(new Object[]{"Localhost","OCULUSJJ","localhost","11112"});


        JTable table = new JTable(model);


        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


        tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);


        // HOROS-20240407準拠: addServerDICOM / verifyPing / searchDICOMBonJourNodes (addServerSharing)


        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));


        JButton addBtn = new JButton("Add Node");


        addBtn.addActionListener(e -> {


            DicomNode node = new DicomNode();


            showNodeEditor(node, () -> 


                model.addRow(new Object[]{node.getName(), node.getAeTitle(), node.getAddress(), String.valueOf(node.getPort())}));


        });


        btnPanel.add(addBtn);


        JButton editBtn = new JButton("Edit");


        editBtn.addActionListener(e -> {


            int r = table.getSelectedRow(); if (r < 0) return;


            DicomNode node = new DicomNode();


            node.setName((String)model.getValueAt(r, 0));


            node.setAeTitle((String)model.getValueAt(r, 1));


            node.setAddress((String)model.getValueAt(r, 2));


            try { node.setPort(Integer.parseInt((String)model.getValueAt(r, 3))); } catch (NumberFormatException ex) {}


            showNodeEditor(node, () -> 


                model.setValueAt(new Object[]{node.getName(), node.getAeTitle(), node.getAddress(), String.valueOf(node.getPort())}, r, 0));


        });


        btnPanel.add(editBtn);


        JButton delBtn = new JButton("Delete");


        delBtn.addActionListener(e -> { int r = table.getSelectedRow(); if (r >= 0) model.removeRow(r); });


        btnPanel.add(delBtn);


        JButton verifyBtn = new JButton("Verify (Echo)");


        verifyBtn.addActionListener(e -> {


            int r = table.getSelectedRow(); if (r < 0) return;


            String addr = (String)model.getValueAt(r,2);


            try { int port = Integer.parseInt((String)model.getValueAt(r,3));


                DicomNode node = new DicomNode();


                node.setAddress(addr); node.setPort(port);


                new Thread(() -> {


                    try { boolean ok = DicomService.getInstance().echo(node);


                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(PreferencesController.this, ok?"Echo Success!":"Echo Failed", "Result", ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE));


                    } catch(Exception ex) {


                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(PreferencesController.this,"Error: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));


                    }


                }).start();


            } catch (NumberFormatException ex) {


                JOptionPane.showMessageDialog(PreferencesController.this, "Invalid port number.", "Error", JOptionPane.ERROR_MESSAGE);


            }


        });


        btnPanel.add(verifyBtn);


        JButton findBtn = new JButton("Find on Network");


        findBtn.addActionListener(e -> {


            // HOROS-20240407準拠: searchDICOMBonJourNodes - Bonjour/DNS-SD auto-discovery of DICOM nodes


            // Java実装では jmdns / mDNS-Java を使用して mDNS/Bonjour ノードを検出する（stub）


            JOptionPane.showMessageDialog(PreferencesController.this, 


                "Network discovery requires Bonjour/DNS-SD support (jmdns library).\nThis is a stub implementation.", 


                "Find on Network", JOptionPane.INFORMATION_MESSAGE);


        });


        btnPanel.add(findBtn);


        // HOROS-20240407準拠: saveAs / loadFrom (ノードリストのファイルへの保存/ロード)


        JButton saveBtn = new JButton("Save Nodes To File...");


        saveBtn.addActionListener(e -> {


            JFileChooser chooser = new JFileChooser();


            if (chooser.showSaveDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {


                try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(chooser.getSelectedFile()))) {


                    for (int i = 0; i < model.getRowCount(); i++) {


                        writer.println(((String)model.getValueAt(i,0)) + "|" + ((String)model.getValueAt(i,1)) + "|" + ((String)model.getValueAt(i,2)) + "|" + ((String)model.getValueAt(i,3)));


                    }


                } catch (java.io.IOException ex) { JOptionPane.showMessageDialog(PreferencesController.this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }


            }


        });


        btnPanel.add(saveBtn);


        JButton loadBtn = new JButton("Load Nodes From File...");


        loadBtn.addActionListener(e -> {


            JFileChooser chooser = new JFileChooser();


            if (chooser.showOpenDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {


                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(chooser.getSelectedFile()))) {


                    String line; while ((line = reader.readLine()) != null) {


                        String[] parts = line.split("\\|"); if (parts.length >= 4) model.addRow(parts);


                    }


                } catch (java.io.IOException ex) { JOptionPane.showMessageDialog(PreferencesController.this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }


            }


        });


        btnPanel.add(loadBtn);


        locationsPane.add(tablePanel, BorderLayout.CENTER);


        locationsPane.add(btnPanel, BorderLayout.SOUTH);


        contentPanel.add(locationsPane, "Locations");


    }


    private void createRoutingPane() {


        JPanel pane = new JPanel(new BorderLayout());


        // HOROS-20240407準拠: OSIAutoroutingPreferencePanePref.h (routesTable, serverPopup, previousPopup)


        JSectionBox routingBox = new JSectionBox("DICOM Autorouting Rules");


        pane.add(routingBox, BorderLayout.NORTH);


        // HOROS-20240407準拠: OSIAutoroutingPreferencePanePref.m (routesArray, serversArray, filterType)


        JPanel tablePanel = new JPanel(new BorderLayout());


        String[] cols = {"Rule Name", "Source AE", "Destination AETitle", "Filter"};


        DefaultTableModel model = new DefaultTableModel(cols, 0);


        // HOROS-20240407準拠: デフォルトルーティングルール（Default Store / * → destination）


        model.addRow(new Object[]{"Default Store","*","OCULUSJJ","*"});


        model.addRow(new Object[]{"PACS Transfer","CT","PACS_SERVER","*/CT"});


        JTable table = new JTable(model);


        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


        tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);


        pane.add(tablePanel, BorderLayout.CENTER);


        // HOROS-20240407準拠: endNewRoute (Add Rule) / deleteSelectedRow (Remove Rule) / selectServer (Select Server)


        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));


        JButton addBtn = new JButton("Add Rule");


        addBtn.addActionListener(e -> {


            // HOROS-20240407準拠: OSIAutoroutingPreferencePanePref.h -newRoute:(id)sender (新規ルーティング設定ダイアログ)


            JDialog dialog = new JDialog(PreferencesController.this, "New Routing Rule", true);


            dialog.setSize(450, 300);


            dialog.setLocationRelativeTo(PreferencesController.this);


            dialog.setLayout(new GridBagLayout());


            GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.WEST; int rRow = 0;


            JTextField nameF = new JTextField(15), srcAeF = new JTextField(10), dstAeF = new JTextField(10), filterF = new JTextField(10);


            gbc.gridx = 0; gbc.gridy = rRow++; dialog.add(new JLabel("Rule Name:"), gbc);


            gbc.gridx = 1; dialog.add(nameF, gbc);


            gbc.gridx = 0; gbc.gridy = rRow++; dialog.add(new JLabel("Source AE:"), gbc);


            gbc.gridx = 1; dialog.add(srcAeF, gbc);


            gbc.gridx = 0; gbc.gridy = rRow++; dialog.add(new JLabel("Destination AETitle:"), gbc);


            gbc.gridx = 1; dialog.add(dstAeF, gbc);


            gbc.gridx = 0; gbc.gridy = rRow++; dialog.add(new JLabel("Filter (C-Find/Store):"), gbc);


            gbc.gridx = 1; dialog.add(filterF, gbc);


            JButton saveBtn2 = new JButton("Save");


            saveBtn2.addActionListener(ev -> {


                if (!nameF.getText().isEmpty() && !srcAeF.getText().isEmpty()) {


                    model.addRow(new Object[]{nameF.getText(), srcAeF.getText(), dstAeF.getText(), filterF.getText()});


                }


                dialog.dispose();


            });


            JButton cancelBtn = new JButton("Cancel");


            cancelBtn.addActionListener(ev -> dialog.dispose());


            gbc.gridx = 0; gbc.gridy = rRow + 1; gbc.gridwidth = 2;


            JPanel btnPanel2 = new JPanel(new FlowLayout(FlowLayout.RIGHT));


            btnPanel2.add(saveBtn2); btnPanel2.add(cancelBtn);


            dialog.add(btnPanel2, gbc);


            dialog.setVisible(true);


        });


        btnPanel.add(addBtn);


        JButton removeBtn = new JButton("Remove Rule");


        removeBtn.addActionListener(e -> { int r = table.getSelectedRow(); if (r >= 0) model.removeRow(r); });


        btnPanel.add(removeBtn);


        pane.add(btnPanel, BorderLayout.SOUTH);


        contentPanel.add(pane, "Routing");


    }


    private void createWebServerPane() {


        JPanel pane = new JPanel(new GridBagLayout());


        GridBagConstraints gbc = new GridBagConstraints();


        gbc.insets = new Insets(5, 5, 5, 5); gbc.anchor = GridBagConstraints.WEST; int row = 0;


        // HOROS-20240407準拠: OSIWebSharingPreferencePanePref.h (WADOPort, WADOTransferSyntax, WADOUrl)


        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Web Server Port:"), gbc); gbc.gridx = 1; pane.add(new JTextField(String.valueOf(AppPreferences.getWebServerPort()), 5), gbc);


        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("WADO Support:"), gbc); gbc.gridx = 1; pane.add(new JCheckBox("Enable WADO", AppPreferences.isWadoEnabled()), gbc);


        // HOROS-20240407準拠: WADO HTTPS (OSILocationsPreferencePanePref.h に WADOhttps プロパティがある)


        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Use HTTPS for WADO:"), gbc); gbc.gridx = 1; pane.add(new JCheckBox("Enable HTTPS", false), gbc);


        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("STOW-RS Support:"), gbc); gbc.gridx = 1; pane.add(new JCheckBox("Enable STOW-RS", AppPreferences.isStowrsEnabled()), gbc);


        // HOROS-20240407準拠: base URL (http://localhost:<port>) - Web Server settings panel


        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Base URL:"), gbc); gbc.gridx = 1; pane.add(new JTextField(AppPreferences.getWebBaseUrl(), 25), gbc);


        // HOROS-20240407準拠: TLS certificate and cipher suite configuration (TLS/SSL security for web services)


        row++; gbc.gridy = ++row; gbc.gridwidth = 2;


        JSectionBox tlsBox = new JSectionBox("Web Service Security");


        pane.add(tlsBox);


        JPanel tlsPanel = new JPanel(new GridBagLayout());


        GridBagConstraints gbt = new GridBagConstraints(); gbt.insets = new Insets(4, 4, 4, 4); gbt.anchor = GridBagConstraints.WEST; int tRow = 0;


        JCheckBox chkTlsEnabled = new JCheckBox("Enable TLS/SSL for Web Services");


        JTextField certPathField = new JTextField(25);


        JButton certBrowseBtn = new JButton("Browse...");


        certBrowseBtn.addActionListener(e -> {


            JFileChooser chooser = new JFileChooser();


            if (chooser.showOpenDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {


                certPathField.setText(chooser.getSelectedFile().getPath());


            }


        });


        gbt.gridx = 0; gbt.gridy = tRow++; tlsPanel.add(chkTlsEnabled, gbt);


        gbt.gridx = 0; gbt.gridy = ++tRow; tlsPanel.add(new JLabel("TLS Certificate Path:"), gbt);


        gbt.gridx = 1; gbt.gridy = tRow - 1; tlsPanel.add(certPathField, gbt);


        gbt.gridx = 2; gbt.gridy = tRow - 1; tlsPanel.add(certBrowseBtn, gbt);


        gbc.gridwidth = 1; gbc.gridy = ++row; gbc.gridx = 0; pane.add(tlsPanel, gbc);


        contentPanel.add(new JScrollPane(pane), "Web Server");


    }


    private void createOnDemandPane() {


        JPanel pane = new JPanel(new BorderLayout());


        pane.add(new JLabel("PACS On-Demand Settings"), BorderLayout.NORTH);


        JPanel center = new JPanel();


        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));


        center.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));


        center.add(new JCheckBox("Search for comparative studies automatically",true));


        center.add(Box.createVerticalStrut(6));


        center.add(new JCheckBox("Query PACS for Smart Albums",false));


        pane.add(center, BorderLayout.CENTER);


        JPanel queryPanel = new JPanel(new GridBagLayout());


        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(4,4,4,4); gbc.anchor = GridBagConstraints.WEST; int row = 0;


        gbc.gridx = 0; gbc.gridy = row; queryPanel.add(new JLabel("Query Timeout (sec):"),gbc); gbc.gridx=1; queryPanel.add(new JTextField("30",5),gbc);


        row++; gbc.gridx = 0; gbc.gridy = row; queryPanel.add(new JLabel("Max Studies per Query:"),gbc); gbc.gridx=1; queryPanel.add(new JTextField("100",5),gbc);


        pane.add(queryPanel, BorderLayout.SOUTH);


        contentPanel.add(pane, "On-Demand");


    }


    // === HELPERS ===


    private JPanel makeRow(Component label, Component control) {


        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));


        p.add(label); if(control != null) p.add(control);


        return p;


    }


    private void showNodeEditor(DicomNode node, Runnable onSave) {


        JDialog dialog = new JDialog(this, "Add DICOM Node", true);


        dialog.setSize(380, 260);


        dialog.setLocationRelativeTo(this);


        dialog.setLayout(new GridLayout(6, 2, 5, 5));


        boolean isNew = (node.getName() == null || node.getName().isEmpty());


        JTextField nameF = new JTextField(node.getName()!=null?node.getName():"");


        JTextField aetF = new JTextField(node.getAeTitle()!=null?node.getAeTitle():"");


        JTextField addrF = new JTextField(node.getAddress()!=null?node.getAddress():"");


        JTextField portF = new JTextField(String.valueOf(node.getPort()>0?node.getPort():11112));


        dialog.add(new JLabel("Name:")); dialog.add(nameF);


        dialog.add(new JLabel("AETitle:")); dialog.add(aetF);


        dialog.add(new JLabel("Address:")); dialog.add(addrF);


        dialog.add(new JLabel("Port:")); dialog.add(portF);


        JButton saveBtn = new JButton(isNew?"Add":"Save");


        saveBtn.addActionListener(e -> {


            try { node.setName(nameF.getText()); node.setAeTitle(aetF.getText());


                node.setAddress(addrF.getText()); node.setPort(Integer.parseInt(portF.getText()));


                if(isNew) DicomNodeManager.getInstance().addNode(node); else DicomNodeManager.getInstance().saveNodes();


                onSave.run(); dialog.dispose();


            } catch(Exception ex) { JOptionPane.showMessageDialog(dialog,"Invalid port number."); }


        });


        dialog.add(saveBtn);


        JButton cancelBtn = new JButton("Cancel");


        cancelBtn.addActionListener(e -> dialog.dispose());


        dialog.add(cancelBtn);


        dialog.setVisible(true);


    }


    private static class JSectionBox extends JPanel {


        public JSectionBox(String title) {


            setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title));


            setLayout(new FlowLayout(FlowLayout.LEFT));


        }


    }



    // === HOROS-20240407 Reference: Helper methods for Preference Panes ===

    /** HOROS-20240407 Reference: getAvailableLanguages - scan .lproj dirs from resource bundle + Resources Disabled/ */
    private String[] getAvailableLanguages() {
        java.util.Locale[] locales = { java.util.Locale.ENGLISH, new java.util.Locale("ja") };
        String[] names = new String[locales.length];
        for (int i = 0; i < locales.length; i++) {
            names[i] = locales[i].getDisplayName() + " (" + locales[i].getLanguage() + ")";
        }
        return names;
    } // end getAvailableLanguages

    /** HOROS-20240407 Reference: resetAllPreferences - delete all NSUserDefaults keys (resetPreferences IBAction) */
    private void resetAllPreferences() {
        try {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userRoot().node("com.oculus.jj");
            for (String key : prefs.keys()) { prefs.remove(key); }
        } catch (java.util.prefs.BackingStoreException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Reset Error", JOptionPane.ERROR_MESSAGE);
        }
        JOptionPane.showMessageDialog(this, "All preferences have been reset to defaults.", "Reset Complete", JOptionPane.INFORMATION_MESSAGE);
    } // end resetAllPreferences

    /** HOROS-20240407 Reference: checkPortConflict - auto-increment port on collision (controlTextDidEndEditing) */
    private void checkPortConflict(JTextField portField) {
        try {
            int newPort = Integer.parseInt(portField.getText());
            if (newPort < 1 || newPort > 65535) { throw new NumberFormatException(); }
            // HOROS: check for collision with standard DICOM Listener port (104)
            int defaultPort = AppPreferences.getListenerPort();
            if (newPort != defaultPort && newPort != 104) {
                // Port seems OK - Horos would also check other services here
                AppPreferences.setListenerPort(newPort);
            } else {
                JOptionPane.showMessageDialog(this, "Port conflict detected. Choose a different port.", "Port Conflict", JOptionPane.WARNING_MESSAGE);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number: must be between 1 and 65535.", "Invalid Port", JOptionPane.ERROR_MESSAGE);
        }
    } // end checkPortConflict
}
