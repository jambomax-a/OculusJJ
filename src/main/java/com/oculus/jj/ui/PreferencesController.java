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
    private final java.util.Map<String, ButtonGroup> preferenceRadioGroups = new java.util.HashMap<>();

    
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
        setTitle(LanguageManager.get("prefs.title", "OculusJJ - Preferences"));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        DefaultListModel<String> model = new DefaultListModel<>();
        // Horos basics section
        model.addElement("-- Basics --");
        model.addElement(LanguageManager.get("prefs.nav.general", "General"));
        model.addElement(LanguageManager.get("prefs.nav.database", "Database"));
        model.addElement(LanguageManager.get("prefs.nav.burning", "CD/DVD Burning"));
        model.addElement(LanguageManager.get("prefs.nav.hanging", "Hanging Protocols"));
        model.addElement(LanguageManager.get("prefs.nav.hotkeys", "Hot Keys"));
        model.addElement(LanguageManager.get("prefs.nav.viewers", "Viewers"));
        model.addElement(LanguageManager.get("prefs.nav.viewer3d", "3D Viewer"));
        model.addElement(LanguageManager.get("prefs.nav.petsuv", "PET/SUV"));
        // Horos sharing section
        model.addElement("-- Sharing --");
        model.addElement(LanguageManager.get("prefs.nav.annotations", "Annotations"));
        model.addElement(LanguageManager.get("prefs.nav.print", "DICOM Print"));
        model.addElement(LanguageManager.get("prefs.nav.listener", "Listener"));
        model.addElement(LanguageManager.get("prefs.nav.locations", "Locations"));
        model.addElement(LanguageManager.get("prefs.nav.routing", "Routing"));
        model.addElement(LanguageManager.get("prefs.nav.webserver", "Web Server"));
        model.addElement(LanguageManager.get("prefs.nav.ondemand", "On-Demand (PACS)"));

        sidebar = new JList<>(model);
        sidebar.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lastSelection = LanguageManager.get("prefs.nav.general", "General");
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
        JScrollPane contentScroll = new JScrollPane(contentPanel);
        contentScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(contentScroll, BorderLayout.CENTER);

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

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton(LanguageManager.get("prefs.apply", "Apply"));
        saveBtn.addActionListener(e -> {
            String selectedLang = langCombo.getSelectedItem().toString();
            String lang = selectedLang.contains("ja") ? "ja" : "en";
            AppPreferences.setAppLanguage(lang);
            // ConfigManager（config.properties）にも永続化し、次回起動時の言語設定と同期させる
            com.oculus.jj.config.ConfigManager.setProperty("ui.language", lang);
            com.oculus.jj.config.ConfigManager.saveConfig();
            AppPreferences.saveAll();
            java.util.Locale locale = lang.equals("ja") ? new java.util.Locale("ja") : java.util.Locale.ENGLISH;
            com.oculus.jj.ui.MenuResourceLoader.setLocale(locale);
            com.oculus.jj.utils.LanguageManager.getInstance().loadLanguage(selectedLang.contains("ja") ? "ja" : "en");
        });
        JButton closeBtn = new JButton(LanguageManager.get("prefs.close", "Close"));
        closeBtn.addActionListener(e -> dispose());
        bottomPanel.add(saveBtn);
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);
        pack();
        // 1920x1080 に収まるように最大サイズ制限と初期サイズ調整
        setMaximumSize(new Dimension(1200, 800));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(1100, (int)(screen.width * 0.85));
        int h = Math.min(750, (int)(screen.height * 0.80));
        setSize(w, h);
        setPreferredSize(new Dimension(w, h));
    }

    // === BASIC PANES ===

    private void createGeneralPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // --- Global ---
        JSectionBox globalBox = new JSectionBox("Global");
        JButton resetBtn = new JButton(LanguageManager.get("prefs.g.reset", "Reset Preferences"));
        JButton savePrefBtn = new JButton(LanguageManager.get("prefs.g.save", "Save Preferences..."));
        JButton loadPrefBtn = new JButton(LanguageManager.get("prefs.g.load", "Load Preferences..."));
        resetBtn.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this, LanguageManager.get("prefs.g.reset.confirm", "Reset all preferences to defaults?"),
                    LanguageManager.get("prefs.title", "Preferences"), JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                try {
                    AppPreferences.resetAll();
                    JOptionPane.showMessageDialog(this, LanguageManager.get("prefs.g.reset.done", "Preferences reset. Restart to apply."),
                            LanguageManager.get("prefs.title", "Preferences"), JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), LanguageManager.get("prefs.error", "Error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        savePrefBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Preferences XML", "xml"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    File file = fc.getSelectedFile();
                    if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) file = new File(file.getParentFile(), file.getName() + ".xml");
                    AppPreferences.saveAll();
                    AppPreferences.exportAll(file);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), LanguageManager.get("prefs.error", "Error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        loadPrefBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Preferences XML", "xml"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    AppPreferences.importAll(fc.getSelectedFile());
                    AppPreferences.saveAll();
                    JOptionPane.showMessageDialog(this, LanguageManager.get("prefs.g.load.done", "Loaded"),
                            LanguageManager.get("prefs.title", "Preferences"), JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), LanguageManager.get("prefs.error", "Error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        btnRow.add(resetBtn); btnRow.add(savePrefBtn); btnRow.add(loadPrefBtn);
        pane.add(globalBox);
        pane.add(btnRow);
        pane.add(Box.createVerticalStrut(6));

        JCheckBox adminChk = new JCheckBox(LanguageManager.get("prefs.g.admin_auth", "Require an 'Administrator' authentication to modify preferences settings"),
                AppPreferences.getBool("general.adminAuth", false));
        adminChk.addActionListener(e -> AppPreferences.setBool("general.adminAuth", adminChk.isSelected()));
        pane.add(adminChk);
        pane.add(Box.createVerticalStrut(4));

        JCheckBox syncChk = new JCheckBox(LanguageManager.get("prefs.g.sync_url", "Synchronize preferences at startup with this url:"),
                AppPreferences.getBool("general.syncAtStartup", false));
        syncChk.addActionListener(e -> AppPreferences.setBool("general.syncAtStartup", syncChk.isSelected()));
        JTextField syncUrl = new JTextField(AppPreferences.getStr("general.syncUrl", ""), 24);
        syncUrl.setEnabled(syncChk.isSelected());
        syncChk.addActionListener(e -> syncUrl.setEnabled(syncChk.isSelected()));
        syncUrl.addActionListener(e -> AppPreferences.setStr("general.syncUrl", syncUrl.getText().trim()));
        pane.add(syncChk);
        pane.add(syncUrl);
        pane.add(Box.createVerticalStrut(4));

        JCheckBox notifChk = new JCheckBox(LanguageManager.get("prefs.g.notifications", "Display Notifications"),
                AppPreferences.getBool("general.notifications", false));
        notifChk.addActionListener(e -> AppPreferences.setBool("general.notifications", notifChk.isSelected()));
        pane.add(notifChk);
        pane.add(Box.createVerticalStrut(12));

        // --- JPEG Compression ---
        JSectionBox jpegBox = new JSectionBox(LanguageManager.get("prefs.g.jpeg_section", "JPEG Compression"));
        pane.add(jpegBox);
        JPanel levelsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        levelsRow.add(new JLabel(LanguageManager.get("prefs.g.jpeg_levels", "Compression Levels:")));
        JButton configureBtn = new JButton(LanguageManager.get("prefs.g.jpeg_configure", "Configure..."));
        configureBtn.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "JPEG compression levels editor (TODO): " + AppPreferences.getStr("general.jpegLevels", ""),
                LanguageManager.get("prefs.g.jpeg_configure", "Configure..."), JOptionPane.INFORMATION_MESSAGE));
        levelsRow.add(configureBtn);
        pane.add(levelsRow);
        JCheckBox jpegColor = new JCheckBox(LanguageManager.get("prefs.g.jpeg_color_photometric",
                "For Color JPEG DICOMS, use JPEG photometric value, instead of DICOM photometric value"),
                AppPreferences.getBool("general.jpegColorPhotometric", true));
        jpegColor.addActionListener(e -> AppPreferences.setBool("general.jpegColorPhotometric", jpegColor.isSelected()));
        pane.add(jpegColor);
        pane.add(Box.createVerticalStrut(12));

        // --- Check version ---
        JSectionBox checkBox = new JSectionBox(LanguageManager.get("prefs.g.checkversion_section", "Check version"));
        pane.add(checkBox);
        JCheckBox pluginsChk = new JCheckBox(LanguageManager.get("prefs.g.plugins_update",
                "Automatically check for installed Plugins updates at startup time"),
                AppPreferences.getBool("general.pluginsUpdate", true));
        pluginsChk.addActionListener(e -> AppPreferences.setBool("general.pluginsUpdate", pluginsChk.isSelected()));
        pane.add(pluginsChk);
        JCheckBox appChk = new JCheckBox(LanguageManager.get("prefs.g.app_update",
                "Automatically check for application updates at startup time"),
                AppPreferences.isCheckUpdatesAtStartup());
        appChk.addActionListener(e -> AppPreferences.setCheckUpdatesAtStartup(appChk.isSelected()));
        pane.add(appChk);
        pane.add(Box.createVerticalStrut(12));

        // --- DICOM Editing ---
        JSectionBox editBox = new JSectionBox(LanguageManager.get("prefs.g.dicom_editing_section", "DICOM Editing"));
        pane.add(editBox);
        JCheckBox dicomEdit = new JCheckBox(LanguageManager.get("prefs.g.allow_dicom_editing",
                "Allow DICOM Editing. See Meta-Data Window."),
                AppPreferences.getBool("general.allowDicomEditing", false));
        dicomEdit.addActionListener(e -> AppPreferences.setBool("general.allowDicomEditing", dicomEdit.isSelected()));
        pane.add(dicomEdit);
        pane.add(Box.createVerticalStrut(12));

        // --- Languages ---
        JSectionBox langBox = new JSectionBox(LanguageManager.get("prefs.g.languages_section", "Languages"));
        pane.add(langBox);
        String[] langCols = {LanguageManager.get("prefs.g.lang_enabled", "Enabled"), LanguageManager.get("prefs.g.lang_name", "Language")};
        DefaultTableModel langModel = new DefaultTableModel(langCols, 2) {
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };
        langModel.addRow(new Object[]{true, "English"});
        langModel.addRow(new Object[]{true, "English"});
        JTable langTable = new JTable(langModel);
        langTable.setRowHeight(20);
        JPanel langTableWrap = new JPanel(new BorderLayout(0, 4));
        langTableWrap.add(new JScrollPane(langTable), BorderLayout.CENTER);
        JPanel langRight = new JPanel();
        langRight.setLayout(new BoxLayout(langRight, BoxLayout.Y_AXIS));
        JLabel noteLbl = new JLabel(LanguageManager.get("prefs.g.languages_note",
                "Restart to apply the changes. If multiple languages are enabled, the used language will be chosen according to OS settings."));
        noteLbl.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        langRight.add(noteLbl);
        langTableWrap.add(langRight, BorderLayout.EAST);
        pane.add(langTableWrap);
        pane.add(Box.createVerticalStrut(6));

        // 既存の「Language / Display Type / Separate Window / Check updates」を
        // 実機準拠のキーに統一しつつ維持（後方互換）
        JSectionBox legacy = new JSectionBox(LanguageManager.get("prefs.section.interface", "Interface (legacy)"));
        pane.add(legacy);
        String[] langs = {"English (en)", "Japanese (ja)"};
        langCombo = new JComboBox<>(langs);
        langCombo.setSelectedItem(com.oculus.jj.config.AppPreferences.getAppLanguage().equals("ja") ? "Japanese (ja)" : "English (en)");
        String[] displayTypes = {LanguageManager.get("prefs.display.original", "Original Pixel Data"),
                LanguageManager.get("prefs.display.grayscale", "Gray Scale"),
                LanguageManager.get("prefs.display.displayed", "Displayed Pixel Data")};
        JComboBox<String> displayTypeCombo = new JComboBox<>(displayTypes);
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.language", "Language:")), langCombo));
        pane.add(Box.createVerticalStrut(4));
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.image_display_type", "Image Display Type:")), displayTypeCombo));
        JCheckBox chkSeparateWin = new JCheckBox(LanguageManager.get("prefs.separate_window",
                "Open each selected study in a separate window"), AppPreferences.isSeparateWindowPerStudy());
        chkSeparateWin.addActionListener(e -> AppPreferences.setSeparateWindowPerStudy(chkSeparateWin.isSelected()));
        pane.add(chkSeparateWin);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.general", "General"));
    }

    private void createDatabasePane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel files = preferenceSection(LanguageManager.get("prefs.db.filemgmt", "File Management"));
        JPanel dbLocation = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        dbLocation.add(new JLabel(LanguageManager.get("prefs.db.location", "Default Database Location:")));
        ButtonGroup locationGroup = new ButtonGroup();
        JRadioButton defaultLocation = new JRadioButton(LanguageManager.get("prefs.db.default", "Default"),
                AppPreferences.getInt("database.locationMode", 0) == 0);
        JRadioButton customLocation = new JRadioButton(LanguageManager.get("prefs.db.custom", "Custom"),
                AppPreferences.getInt("database.locationMode", 0) == 1);
        locationGroup.add(defaultLocation);
        locationGroup.add(customLocation);
        defaultLocation.addActionListener(e -> AppPreferences.setInt("database.locationMode", 0));
        customLocation.addActionListener(e -> AppPreferences.setInt("database.locationMode", 1));
        dbLocation.add(defaultLocation);
        dbLocation.add(customLocation);
        JTextField dbPathField = new JTextField(AppPreferences.getDatabaseFolder(), 22);
        dbPathField.setEditable(false);
        JButton browseDb = new JButton(LanguageManager.get("prefs.db.select", "Select..."));
        browseDb.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(AppPreferences.getDatabaseFolder());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                AppPreferences.setDatabaseFolder(chooser.getSelectedFile().getPath());
                dbPathField.setText(AppPreferences.getDatabaseFolder());
                customLocation.setSelected(true);
                AppPreferences.setInt("database.locationMode", 1);
            }
        });
        dbLocation.add(dbPathField);
        dbLocation.add(browseDb);
        files.add(dbLocation);
        files.add(ck("database.dicomOnly", true, LanguageManager.get("prefs.db.dicom_only", "Import DICOM file format only (ignore JPEG, TIFF, LSM, XnAna...)")));
        files.add(ck("database.validateImport", false, LanguageManager.get("prefs.db.validate", "Validate files before importing files from CD/DVD or external drives (crash test)")));
        JPanel copyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        copyRow.add(new JLabel(LanguageManager.get("prefs.db.copy_if", "Copy files to Horos Data folder if:")));
        JComboBox<String> copyMode = preferenceCombo("database.copyMode", 2, new String[]{
                LanguageManager.get("prefs.db.copy_always", "Always"),
                LanguageManager.get("prefs.db.copy_not_hd", "If files aren't located on the HD"),
                LanguageManager.get("prefs.db.copy_ask", "Ask the user")});
        copyRow.add(copyMode);
        files.add(copyRow);
        files.add(ck("database.useDicomdirExport", false, LanguageManager.get("prefs.db.use_dicomdir", "Use DICOMDIR structure when exporting to filesystem")));
        JTextField dicomPath = new JTextField(AppPreferences.getDicomFolder(), 24);
        dicomPath.setEditable(false);
        JButton browseDicom = new JButton(LanguageManager.get("prefs.browse", "Browse..."));
        browseDicom.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(AppPreferences.getDicomFolder());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                AppPreferences.setDicomFolder(chooser.getSelectedFile().getPath());
                dicomPath.setText(AppPreferences.getDicomFolder());
            }
        });
        files.add(makeRow(new JLabel(LanguageManager.get("prefs.dicom_storage_path", "DICOM Files Storage Path:")), dicomPath));
        files.add(makeRow(new JLabel(""), browseDicom));
        files.add(ck("database.autoRefresh", true, LanguageManager.get("prefs.auto_refresh_db", "Automatically refresh database on startup")));
        pane.add(files);

        JPanel display = preferenceSection(LanguageManager.get("prefs.db.display", "Database Display"));
        display.add(preferenceTextRow("prefs.db.datetime", "Date & Time Format:", "database.dateTimeFormat", "y/MM/dd, H:mm"));
        display.add(preferenceTextRow("prefs.db.dateonly", "Date Only Format:", "database.dateFormat", "y/MM/dd"));
        JPanel fontAge = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        fontAge.add(new JLabel(LanguageManager.get("prefs.db.fontsize", "Font Size:")));
        fontAge.add(preferenceCombo("database.fontSize", 0, new String[]{"Regular", "Large", "Huge"}));
        fontAge.add(new JLabel(LanguageManager.get("prefs.db.age", "Patient's age:")));
        fontAge.add(preferenceCombo("database.patientAge", 0, new String[]{"Both, if different", "Patient", "Study"}));
        display.add(fontAge);
        display.add(ck("database.allStudies", true, LanguageManager.get("prefs.db.all_studies", "Always display all studies of each patient")));
        display.add(ck("database.capitalizeNames", true, LanguageManager.get("prefs.db.cap_names", "Display patient's names and descriptions with Capitalized Words")));
        display.add(ck("database.clearSearchOnAlbum", false, LanguageManager.get("prefs.db.clear_search", "Clear Search field (in the toolbar), when changing current album")));
        display.add(ck("database.historyDrawerHorizontal", false, LanguageManager.get("prefs.db.history_drawer", "Display the History Drawer horizontally")));
        pane.add(display);

        JPanel uid = preferenceSection(LanguageManager.get("prefs.db.patient_uid", "Patient Unique Identifier"));
        uid.add(new JLabel(LanguageManager.get("prefs.db.use_fields_uid", "Use following fields to generate Patient UID:")));
        uid.add(patientUIDPreference("database.uidPatientId", true, "prefs.db.uid_patientid", "Patient ID"));
        uid.add(patientUIDPreference("database.uidBirthDate", true, "prefs.db.uid_birthdate", "Patient BirthDate"));
        uid.add(patientUIDPreference("database.uidName", true, "prefs.db.uid_name", "Patient's Name"));
        JLabel uidNote = new JLabel("<html>" + LanguageManager.get("prefs.db.uid_note", "Changing these settings will recompute all database patientUIDs. It can require several minutes on large database.") + "</html>");
        uid.add(uidNote);
        pane.add(uid);

        JPanel comments = preferenceSection(LanguageManager.get("prefs.db.comments", "Comments & Status fields"));
        comments.add(ck("database.storeCommentsInDicom", true, LanguageManager.get("prefs.db.store_in_dicom", "Store them in DICOM fields")));
        comments.add(ck("database.loadCommentsFromDicom", true, LanguageManager.get("prefs.db.load_from_dicom", "Load them from DICOM files fields")));
        JPanel fieldRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        fieldRow.add(ck("database.fillDicomField", false, LanguageManager.get("prefs.db.dicom_field", "DICOM Field(s) to fill:")));
        fieldRow.add(preferenceCombo("database.dicomField", 0, new String[]{"Comments", "Referring Physician", "Study Description"}));
        fieldRow.add(new JLabel(LanguageManager.get("prefs.db.group", "Group:")));
        JTextField group = preferenceText("database.dicomGroup", "", 5);
        fieldRow.add(group);
        fieldRow.add(new JLabel(LanguageManager.get("prefs.db.element", "Element:")));
        fieldRow.add(preferenceText("database.dicomElement", "", 5));
        JButton regenerate = new JButton(LanguageManager.get("prefs.db.regenerate", "Regenerate"));
        regenerate.addActionListener(e -> JOptionPane.showMessageDialog(this, LanguageManager.get("prefs.db.uid_note", "This operation may take several minutes.")));
        fieldRow.add(regenerate);
        comments.add(fieldRow);
        JPanel level = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        level.add(new JLabel(LanguageManager.get("prefs.db.level", "Level:")));
        level.add(preferenceCombo("database.commentLevel", 0, new String[]{
                LanguageManager.get("prefs.db.level_study", "Study"), LanguageManager.get("prefs.db.level_series", "Series")}));
        comments.add(level);
        pane.add(comments);

        JPanel parsing = preferenceSection(LanguageManager.get("prefs.db.series_parsing", "Series Parsing"));
        parsing.add(ck("database.splitMultiEchoMR", true, LanguageManager.get("prefs.db.split_multiecho", "Split Multi-echo MR")));
        parsing.add(ck("database.split4dCardiacCT", true, LanguageManager.get("prefs.db.split_4dct", "Split 4D Cardiac CT")));
        parsing.add(ck("database.usOneSeriesPerFile", false, LanguageManager.get("prefs.db.us_one_series", "For US: one series per file")));
        JPanel crdr = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        crdr.add(new JLabel(LanguageManager.get("prefs.db.crdr", "For CR, DR, DX, CT and RF:")));
        ButtonGroup crdrGroup = new ButtonGroup();
        JRadioButton combine = new JRadioButton(LanguageManager.get("prefs.db.combine", "Combine"), AppPreferences.getInt("database.crdrMode", 1) == 0);
        JRadioButton split = new JRadioButton(LanguageManager.get("prefs.db.split", "Split"), AppPreferences.getInt("database.crdrMode", 1) == 1);
        crdrGroup.add(combine); crdrGroup.add(split);
        combine.addActionListener(e -> AppPreferences.setInt("database.crdrMode", 0));
        split.addActionListener(e -> AppPreferences.setInt("database.crdrMode", 1));
        crdr.add(combine); crdr.add(split);
        parsing.add(crdr);
        parsing.add(new JLabel(LanguageManager.get("prefs.db.localizer", "Gather localizer images with Series Description:")));
        parsing.add(preferenceText("database.localizerDescriptions", "localizer,socal,survey,locator,tracker", 42));
        parsing.add(makeRow(new JLabel(LanguageManager.get("prefs.db.sop_display", "List of SOP Class UIDs displayed:")),
                new JButton(LanguageManager.get("prefs.db.sop_display_btn", "Disp..."))));
        parsing.add(makeRow(new JLabel(LanguageManager.get("prefs.db.sop_store", "List of SOP Class UIDs stored:")),
                new JButton(LanguageManager.get("prefs.db.sop_store_btn", "Store..."))));
        pane.add(parsing);

        JPanel sorting = new JPanel(new GridLayout(1, 2, 8, 0));
        JPanel sortSeries = preferenceSection(LanguageManager.get("prefs.db.sort_series", "Sort Series by"));
        sortSeries.add(preferenceRadio("database.sortSeries", 0, LanguageManager.get("prefs.db.series_id", "Series ID")));
        sortSeries.add(preferenceRadio("database.sortSeries", 1, LanguageManager.get("prefs.db.series_acq_time", "Series Acquisition Time")));
        sortSeries.add(preferenceRadio("database.sortSeries", 2, LanguageManager.get("prefs.db.instance_number", "Instance Number")));
        JPanel sortImages = preferenceSection(LanguageManager.get("prefs.db.sort_images", "Sort Images by"));
        sortImages.add(preferenceRadio("database.sortImages", 0, LanguageManager.get("prefs.db.slice_asc", "Ascending Slice Location")));
        sortImages.add(preferenceRadio("database.sortImages", 1, LanguageManager.get("prefs.db.slice_desc", "Descending Slice Location")));
        sortImages.add(preferenceRadio("database.sortImages", 2, LanguageManager.get("prefs.db.date_asc", "Ascending Date")));
        sortImages.add(preferenceRadio("database.sortImages", 3, LanguageManager.get("prefs.db.date_desc", "Descending Date")));
        sorting.add(sortSeries); sorting.add(sortImages);
        pane.add(sorting);

        JPanel clean = preferenceSection(LanguageManager.get("prefs.db.autoclean", "Database Auto-Cleaning"));
        clean.add(ck("database.autoCleanEnabled", false, LanguageManager.get("prefs.db.delete_conditions", "Delete studies at these conditions:")));
        clean.add(new JLabel(LanguageManager.get("prefs.db.delete_note", "If all checked conditions are true. If a study is in an album, it will NOT be deleted.")));
        clean.add(preferenceCheckCombo("database.cleanAcqOlder", "prefs.db.acq_older", "Acquisition date is older than:", "database.cleanAcqAge", new String[]{"Three months", "Six months", "One year", "Two years", "Five years"}));
        clean.add(preferenceCheckCombo("database.cleanNotOpened", "prefs.db.not_opened", "Not opened during last:", "database.cleanNotOpenedAge", new String[]{"Three months", "Six months", "One year", "Two years", "Five years"}));
        JPanel commentRule = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        commentRule.add(ck("database.cleanCommentEnabled", false, LanguageManager.get("prefs.db.only_comments", "Only if 'comments' field:")));
        commentRule.add(preferenceCombo("database.cleanCommentMode", 0, new String[]{"contains", "doesn't contain"}));
        commentRule.add(preferenceText("database.cleanCommentText", "", 18));
        clean.add(commentRule);
        JPanel diskRule = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        diskRule.add(ck("database.cleanDiskSpaceEnabled", true, LanguageManager.get("prefs.db.hd_free", "If HD free space is less than:")));
        diskRule.add(preferenceCombo("database.cleanDiskSpace", 0, new String[]{"1 GB", "2 GB", "5 GB", "10 GB"}));
        diskRule.add(new JLabel(LanguageManager.get("prefs.db.delete_policy", "delete:")));
        diskRule.add(preferenceCombo("database.cleanPolicy", 1, new String[]{
                LanguageManager.get("prefs.db.lru_created", "Least recently created"),
                LanguageManager.get("prefs.db.lru_added", "Least recently added"),
                LanguageManager.get("prefs.db.lru_opened", "Least recently opened")}));
        clean.add(diskRule);
        clean.add(ck("database.deleteLinkedOriginal", false, LanguageManager.get("prefs.db.linked_files", "If image files are linked files, also delete the original file")));
        clean.add(ck("database.keepStudiesWithComments", true, LanguageManager.get("prefs.db.keep_comments", "Don't delete a study, if there are comments")));
        clean.add(ck("database.keepStudiesInAlbum", true, LanguageManager.get("prefs.db.keep_album", "Don't delete a study, if it is stored in an album")));
        clean.add(ck("database.deleteEntireAtStartup", false, LanguageManager.get("prefs.db.delete_entire", "Automatically delete the ENTIRE default database, at each startup")));
        pane.add(clean);

        JPanel reports = preferenceSection(LanguageManager.get("prefs.db.reports", "Reports"));
        JPanel reportFormat = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        reportFormat.add(new JLabel(LanguageManager.get("prefs.db.report_using", "Create reports using:")));
        reportFormat.add(preferenceCombo("database.reportFormat", 0, new String[]{"Pages (pages)", "PDF", "HTML"}));
        reports.add(reportFormat);
        reports.add(ck("database.createDicomPdfOnValidated", false, LanguageManager.get("prefs.db.pdf_report", "Automatically create a DICOM PDF file of the report, when status is changed to 'validated'")));
        pane.add(reports);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.database", "Database"));
    }

    private void createCDpvPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel readBox = preferenceSection(LanguageManager.get("prefs.cddvd.read", "Reading CD/DVDs"));
        readBox.add(new JLabel(LanguageManager.get("prefs.cddvd.insert", "When a disc is inserted:")));
        ButtonGroup insertGroup = new ButtonGroup();
        String[] insertLabels = {
            LanguageManager.get("prefs.cddvd.copy_all", "Copy all its contents into the database"),
            LanguageManager.get("prefs.cddvd.show_source", "Show disc content as a separate source"),
            LanguageManager.get("prefs.cddvd.ask_user", "Ask the user"),
            LanguageManager.get("prefs.cddvd.ignore", "Ignore it")};
        int insertMode = AppPreferences.getInt("cddvd.insertMode", 2);
        for (int i = 0; i < insertLabels.length; i++) {
            JRadioButton radio = new JRadioButton(insertLabels[i], i == insertMode);
            final int mode = i;
            radio.addActionListener(e -> AppPreferences.setInt("cddvd.insertMode", mode));
            insertGroup.add(radio);
            readBox.add(radio);
        }
        readBox.add(ck("cddvd.ejectAfterCopy", true, LanguageManager.get("prefs.eject_disk", "Eject the disk when done copying")));
        readBox.add(ck("cddvd.autoSelectSource", true, LanguageManager.get("prefs.auto_select_source", "Automatically select the source when ready")));
        readBox.add(new JLabel(LanguageManager.get("prefs.cddvd.determine", "Determine disc content by considering:")));
        ButtonGroup contentGroup = new ButtonGroup();
        JRadioButton scanMode = new JRadioButton(LanguageManager.get("prefs.cddvd.scan_mode", "All DICOM files available on the disk (Scan Mode), ignoring the DICOMDIR file (Slower)"),
            AppPreferences.getInt("cddvd.contentMode", 1) == 0);
        JRadioButton dicomdirMode = new JRadioButton(LanguageManager.get("prefs.cddvd.dicomdir_mode", "Files indexed in the DICOMDIR file (Faster)"),
            AppPreferences.getInt("cddvd.contentMode", 1) == 1);
        scanMode.addActionListener(e -> AppPreferences.setInt("cddvd.contentMode", 0));
        dicomdirMode.addActionListener(e -> AppPreferences.setInt("cddvd.contentMode", 1));
        contentGroup.add(scanMode);
        contentGroup.add(dicomdirMode);
        readBox.add(scanMode);
        readBox.add(dicomdirMode);
        readBox.add(ck("cddvd.switchToScanWhenEmpty", true, LanguageManager.get("prefs.cddvd.switch_scan", "If no DICOM files are found, switch to Scan Mode")));
        pane.add(readBox);
        pane.add(Box.createVerticalStrut(10));

        JSectionBox writeBox = new JSectionBox(LanguageManager.get("prefs.section.write_cd", "Writing CD/DVDs"));
        pane.add(writeBox);
        pane.add(ck("cddvd.preferStdJPEG", false, LanguageManager.get("prefs.prefer_std_jpeg", "Prefer standard JPEG instead of JPEG2000/JPEG-LS compression for DICOM files")));
        pane.add(Box.createVerticalStrut(4));

        JPanel suppPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox chkSuppFolder = new JCheckBox(LanguageManager.get("prefs.include_folder", "Include contents of this folder:"),
            !AppPreferences.getCDDVDSupplementaryFolder().isEmpty());
        JTextField suppField = new JTextField(AppPreferences.getCDDVDSupplementaryFolder(), 25);
        suppField.setEnabled(chkSuppFolder.isSelected());
        JButton browseBurnBtn = new JButton(LanguageManager.get("prefs.browse", "Browse..."));
        browseBurnBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {
                suppField.setText(chooser.getSelectedFile().getPath());
                AppPreferences.setCDDVDSupplementaryFolder(suppField.getText());
                chkSuppFolder.setSelected(true);
                suppField.setEnabled(true);
            }
        });
        chkSuppFolder.addActionListener(e -> {
            suppField.setEnabled(chkSuppFolder.isSelected());
            if (!chkSuppFolder.isSelected()) AppPreferences.setCDDVDSupplementaryFolder("");
        });
        suppField.addActionListener(e -> AppPreferences.setCDDVDSupplementaryFolder(suppField.getText()));
        suppPanel.add(chkSuppFolder);
        suppPanel.add(suppField);
        suppPanel.add(browseBurnBtn);
        pane.add(suppPanel);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.burning", "CD/DVD Burning"));
    }

    private void createHangingProtocolsPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        String[] modalities = {"CR", "CT", "DX", "ES", "MG", "MR", "NM", "OT", "PT", "RF", "SC", "US", "XA"};
        JComboBox<String> modalityCombo = new JComboBox<>(modalities);
        modalityCombo.setSelectedIndex(Math.max(0, Math.min(modalities.length - 1,
                AppPreferences.getHangingModalityIndex())));
        modalityCombo.addActionListener(e -> AppPreferences.setHangingModalityIndex(modalityCombo.getSelectedIndex()));
        JButton newButton = new JButton(LanguageManager.get("prefs.proto.new", "New"));
        JPanel header = new JPanel(new BorderLayout());
        header.add(makeRow(new JLabel(LanguageManager.get("prefs.modality", "Modality:")), modalityCombo), BorderLayout.WEST);
        header.add(newButton, BorderLayout.EAST);
        pane.add(header);

        String[] tilingOptions = {"1x1","2x1","1x2","2x2","3x1","1x3","2x3","3x2","4x1","1x4","3x3","2x4","4x2","4x3","5x1","1x5"};
        String[] wlwwPresets = {"Default WL & WW","Bone","Lung","Liver","Brain","Soft Tissue","Mediastinum"};
        String[] columns = {
                LanguageManager.get("prefs.proto.col_study_desc", "Study Description (0008,1030)"),
                LanguageManager.get("prefs.proto.col_win_tiling", "Windows Tiling"),
                LanguageManager.get("prefs.proto.col_img_tiling", "Image Tiling"),
                LanguageManager.get("prefs.proto.col_prev_study", "Previous Study"),
                LanguageManager.get("prefs.proto.col_sync", "Sync"),
                LanguageManager.get("prefs.proto.col_propagate", "Propagate"),
                LanguageManager.get("prefs.proto.col_wlww", "WL/WW")};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int column) {
                return column >= 3 && column <= 5 ? Boolean.class : String.class;
            }
        };
        String saved = AppPreferences.getStr("hanging.protocolRows", "");
        if (saved.isBlank()) {
            model.addRow(new Object[]{"Default", "1x1", "1x1", false, true, true, "Default WL & WW"});
        } else {
            for (String line : saved.split("\\n")) {
                String[] parts = line.split("\\t", -1);
                if (parts.length == 7) model.addRow(new Object[]{parts[0], parts[1], parts[2],
                        Boolean.parseBoolean(parts[3]), Boolean.parseBoolean(parts[4]),
                        Boolean.parseBoolean(parts[5]), parts[6]});
            }
            if (model.getRowCount() == 0) model.addRow(new Object[]{"Default", "1x1", "1x1", false, true, true, "Default WL & WW"});
        }
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(tilingOptions)));
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JComboBox<>(tilingOptions)));
        table.getColumnModel().getColumn(6).setCellEditor(new DefaultCellEditor(new JComboBox<>(wlwwPresets)));
        Runnable persist = () -> {
            StringBuilder rows = new StringBuilder();
            for (int r = 0; r < model.getRowCount(); r++) {
                if (r > 0) rows.append('\n');
                for (int c = 0; c < model.getColumnCount(); c++) {
                    if (c > 0) rows.append('\t');
                    rows.append(String.valueOf(model.getValueAt(r, c)).replace("\\t", " ").replace("\\n", " "));
                }
            }
            AppPreferences.setStr("hanging.protocolRows", rows.toString());
        };
        model.addTableModelListener(e -> persist.run());
        newButton.addActionListener(e -> {
            String description = JOptionPane.showInputDialog(this,
                    LanguageManager.get("prefs.proto.col_study_desc", "Study Description (0008,1030)"), "");
            if (description != null && !description.isBlank()) {
                model.addRow(new Object[]{description.trim(), "1x1", "1x1", false, true, true, "Default WL & WW"});
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("DELETE"), "deleteRow");
        table.getActionMap().put("deleteRow", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int row = table.getSelectedRow();
                if (row >= 0 && model.getRowCount() > 1) model.removeRow(table.convertRowIndexToModel(row));
            }
        });
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(760, 150));
        pane.add(tableScroll);

        JPanel windows = preferenceSection(LanguageManager.get("prefs.proto.windows", "Windows:"));
        windows.add(ck("hanging.repeatSeries", false, LanguageManager.get("prefs.proto.repeat_series", "If not enough series for number of windows, repeat series")));
        pane.add(windows);

        JPanel previous = preferenceSection(LanguageManager.get("prefs.proto.prev_study", "Previous Study:"));
        previous.add(ck("hanging.previousSameModality", false, LanguageManager.get("prefs.proto.same_modality", "Same Modality")));
        previous.add(ck("hanging.previousSameDescription", false, LanguageManager.get("prefs.proto.same_description", "Same Description")));
        previous.add(preferenceCombo("hanging.previousDisplayMode", 0, new String[]{
                LanguageManager.get("prefs.proto.display_last", "Display last study, if possible"),
                "Display the most recent study", "Do not display previous study"}));
        previous.add(preferenceCombo("hanging.previousSeriesMode", 0, new String[]{
                LanguageManager.get("prefs.proto.display_one_series", "Display 1 series per previous study"),
                "Display all series per previous study"}));
        pane.add(previous);

        JPanel order = preferenceSection(LanguageManager.get("prefs.proto.series_order", "Series Order:"));
        order.add(new JLabel(LanguageManager.get("prefs.proto.series_order_help", "Sort series according to their Series Description field, Use commas to separate terms.")));
        JTextArea orderText = new JTextArea(AppPreferences.getStr("hanging.seriesOrder", ""), 3, 60);
        orderText.setLineWrap(true);
        orderText.setWrapStyleWord(true);
        orderText.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void save() { AppPreferences.setStr("hanging.seriesOrder", orderText.getText()); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { save(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { save(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { save(); }
        });
        order.add(new JScrollPane(orderText));
        order.add(ck("hanging.ignoreCase", false, LanguageManager.get("prefs.proto.ignore_case", "Ignore case sensitivity")));
        pane.add(order);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.hanging", "Hanging Protocols"));
    }

    private void createHotKeysPane() {
        JPanel pane = new JPanel(new BorderLayout());
        String[] cols = {LanguageManager.get("prefs.hotkey.col_action", "Action"), LanguageManager.get("prefs.hotkey.col_key", "Key Combination")};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        Object[][] hotKeys = {
            {LanguageManager.get("prefs.hotkey.zoom_roi", "Zoom to ROI"),"Ctrl+Shift+Click"},{LanguageManager.get("prefs.hotkey.wlww", "Toggle WL/WW Mode"),"W"},
            {LanguageManager.get("prefs.hotkey.pan", "Pan Mode"),"P"},{LanguageManager.get("prefs.hotkey.roi", "Select/Edit ROI"),"S"},{LanguageManager.get("prefs.hotkey.freehand", "Freehand Measure"),"M"},
            {LanguageManager.get("prefs.hotkey.rect_roi", "Rectangular ROI"),"R"},{LanguageManager.get("prefs.hotkey.oval_roi", "Oval ROI"),"O"},{LanguageManager.get("prefs.hotkey.angle", "Angle Measurement"),"A"},
            {LanguageManager.get("prefs.hotkey.flip", "Flip Horizontal"),"F"},{LanguageManager.get("prefs.hotkey.rotate", "Rotate 90 CW"),"Shift+Right Click"},
            {LanguageManager.get("prefs.hotkey.zoom_io", "Zoom In/Out"),"Shift+Scroll"},{LanguageManager.get("prefs.hotkey.mip3d", "3D MIP Viewer"),"Ctrl+M"},
            {LanguageManager.get("prefs.hotkey.vr3d", "3D Volume Rendering"),"Ctrl+V"},{LanguageManager.get("prefs.hotkey.cpr", "Curved MPR"),"Ctrl+Y"}
        };
        for (Object[] r : hotKeys) model.addRow(r);

        pane.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(new JButton(LanguageManager.get("prefs.add_keybinding", "+ Add Key Binding")));
        bottomPanel.add(new JButton(LanguageManager.get("prefs.edit_selected", "Edit Selected")));
        bottomPanel.add(new JButton(LanguageManager.get("prefs.remove", "- Remove")));
        pane.add(bottomPanel, BorderLayout.SOUTH);
        contentPanel.add(pane, LanguageManager.get("prefs.nav.hotkeys", "Hot Keys"));
    }

    private void createViewersPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // --- TIFF, JPEG, Quicktime, DICOM Export ---
        pane.add(new JSectionBox(LanguageManager.get("prefs.v.export_section", "TIFF, JPEG, Quicktime, DICOM Export")));
        pane.add(ck("viewers.exportCrop", true, LanguageManager.get("prefs.v.export_crop", "Automatically crop the view to the image area")));
        pane.add(ck("viewers.exportTiled", true, LanguageManager.get("prefs.v.export_tiled", "If there are tiled views, export all tiled views, not only the selected view")));
        pane.add(ck("viewers.exportOpen", true, LanguageManager.get("prefs.v.export_open", "Automatically open the exported images after exporting with Preview or Quicktime Player")));
        pane.add(Box.createVerticalStrut(12));

        // --- Screens & Window size ---
        pane.add(new JSectionBox(LanguageManager.get("prefs.v.screens_section", "Screens & Window size")));
        pane.add(ck("viewers.reserveDbScreen", true, LanguageManager.get("prefs.v.reserve_db_screen", "Reserve the database screen for the database")));
        pane.add(ck("viewers.dbScreenLast", false, LanguageManager.get("prefs.v.use_db_screen_last", "Use database screen in the last")));
        pane.add(ck("viewers.groupSameStudy", false, LanguageManager.get("prefs.v.group_same_study", "Group series of same study on same screen")));
        pane.add(Box.createVerticalStrut(4));
        String[] winSizes = {
            LanguageManager.get("prefs.v.window_size_full", "Fullscreen"),
            LanguageManager.get("prefs.v.window_size_150", "At 150% of resolution"),
            LanguageManager.get("prefs.v.window_size_100", "At 100% of resolution"),
            LanguageManager.get("prefs.v.window_size_200", "At 200% of resolution")};
        ButtonGroup winGroup = new ButtonGroup();
        int winIdx = AppPreferences.getInt("viewers.windowSize", -1);
        for (int i = 0; i < winSizes.length; i++) {
            JRadioButton rb = new JRadioButton(winSizes[i], i == winIdx);
            final int idx = i;
            rb.addActionListener(e -> AppPreferences.setInt("viewers.windowSize", idx));
            winGroup.add(rb);
            pane.add(rb);
        }
        pane.add(Box.createVerticalStrut(12));

        // --- Tiling ---
        pane.add(new JSectionBox(LanguageManager.get("prefs.v.tiling_section", "Tiling:")));
        pane.add(ck("viewers.tilingAuto", true, LanguageManager.get("prefs.v.tiling_auto", "Automatically Tile Windows when closing or opening a new series")));
        pane.add(ck("viewers.tilingMagnetic", true, LanguageManager.get("prefs.v.tiling_magnetic", "Magnetic Windows for move & resize")));
        pane.add(ck("viewers.tilingStretch", true, LanguageManager.get("prefs.v.tiling_stretch", "Stretch last windows to fill the whole screen")));
        pane.add(ck("viewers.tilingRecentFirst", true, LanguageManager.get("prefs.v.tiling_recent_first", "Order windows from most recent to oldest studies")));
        pane.add(ck("viewers.tilingReversed", false, LanguageManager.get("prefs.v.tiling_reversed", "Reversed order")));
        pane.add(Box.createVerticalStrut(12));

        // --- Workspace State ---
        pane.add(new JSectionBox(LanguageManager.get("prefs.v.workspace_section", "Workspace State:")));
        pane.add(ck("viewers.workspaceSaveAuto", true, LanguageManager.get("prefs.v.workspace_save_auto", "Automatically save Workspace State when closing all viewers")));
        pane.add(ck("viewers.workspaceLoadAuto", true, LanguageManager.get("prefs.v.workspace_load_auto", "Automatically load Workspace State when opening a study")));
        pane.add(Box.createVerticalStrut(12));

        // --- Series List ---
        pane.add(new JSectionBox(LanguageManager.get("prefs.v.series_list_section", "Series List:")));
        pane.add(ck("viewers.seriesSingleList", true, LanguageManager.get("prefs.v.series_single_list", "Single Series List per screen")));
        pane.add(ck("viewers.seriesAutoShowhide", false, LanguageManager.get("prefs.v.series_auto_showhide", "Automatically showhide the list")));
        pane.add(Box.createVerticalStrut(12));

        // --- 12bit Display / Scale ---
        pane.add(new JSectionBox(LanguageManager.get("prefs.v._12bit_section", "12bit Display:")));
        pane.add(ck("viewers._12bitAuto", false, LanguageManager.get("prefs.v._12bit_auto", "Automatically enable 12 Bit Totoku Display support for CR, MQ, DX, DR, XA and RF")));
        pane.add(new JSectionBox(LanguageManager.get("prefs.v.scale_section", "Scale:")));
        pane.add(ck("viewers.scaleFit", true, LanguageManager.get("prefs.v.scale_fit", "Always 'scale to fit' when displaying or resizing windows")));
        pane.add(Box.createVerticalStrut(12));

        // --- Miscellaneous (2 columns) ---
        pane.add(new JSectionBox(LanguageManager.get("prefs.v.misc_section", "Miscellaneous")));
        JPanel miscLeft = new JPanel();
        miscLeft.setLayout(new BoxLayout(miscLeft, BoxLayout.Y_AXIS));
        miscLeft.add(ck("viewers.saveLeftTool", false, LanguageManager.get("prefs.v.save_left_tool", "Save left bottom mouse tool")));
        miscLeft.add(ck("viewers.reverseScroll", true, LanguageManager.get("prefs.v.reverse_scroll", "Reverse scroll wheel direction")));
        miscLeft.add(ck("viewers.loopSeries", false, LanguageManager.get("prefs.v.loop_series", "Loop in the series with mouse scroll wheel")));
        miscLeft.add(ck("viewers.zoomHorizontal", false, LanguageManager.get("prefs.v.zoom_horizontal", "Zoom with horizontal scrollwheel")));
        JPanel npRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        npRow.add(new JLabel(LanguageManager.get("prefs.v.no_propagate", "No 'Propagate settings in series' for:")));
        npRow.add(new JComboBox<>(new String[]{"Modalities", "All", "None"}));
        miscLeft.add(npRow);
        miscLeft.add(ck("viewers.nextPrevAll", true, LanguageManager.get("prefs.v.nextprev_all", "Apply Next/Previous Series button to all opened viewers")));
        JCheckBox useVoiLut = new JCheckBox(LanguageManager.get("prefs.v.use_voi_lut", "Use VOI LUT if available"),
            AppPreferences.isUseVOILUT());
        useVoiLut.addActionListener(e -> AppPreferences.setUseVOILUT(useVoiLut.isSelected()));
        miscLeft.add(useVoiLut);
        miscLeft.add(ck("viewers.splineRois", true, LanguageManager.get("prefs.v.spline_rois", "Spline Rendering for polygons and pencils ROIs")));
        miscLeft.add(ck("viewers.roiUnnamed", true, LanguageManager.get("prefs.v.roi_unnamed", "New ROIs are unnamed")));
        JPanel ssRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        ssRow.add(new JLabel(LanguageManager.get("prefs.v.scroll_series", "Scroll through image and series for:")));
        ssRow.add(new JComboBox<>(new String[]{"Modalities", "All", "None"}));
        miscLeft.add(ssRow);
        miscLeft.add(ck("viewers.keepClutBars", false, LanguageManager.get("prefs.v.keep_clut_bars", "Keep CLUT Bars when exporting Screen Captures")));
        miscLeft.add(ck("viewers.skewKurt", false, LanguageManager.get("prefs.v.skewness_kurtosis", "Compute Skewness and Kurtosis for ROI")));
        miscLeft.add(ck("viewers.foreRefUid", false, LanguageManager.get("prefs.v.forefrefuid", "Use FrameofReferenceUID for cross reference lines")));
        miscLeft.add(ck("viewers.zoomAroundMouse", true, LanguageManager.get("prefs.v.zoom_around_mouse", "Zoom centered around mouse position")));
        JPanel miscRight = new JPanel();
        miscRight.setLayout(new BoxLayout(miscRight, BoxLayout.Y_AXIS));
        miscRight.add(ck("viewers.rstructToBrush", false, LanguageManager.get("prefs.v.rstruct_to_brush", "Convert RSTRUCT ROIs to Brush ROIs")));
        miscRight.add(ck("viewers.noSwitchSeries", true, LanguageManager.get("prefs.v.no_switch_series", "Don't switch series if already displayed")));
        miscRight.add(ck("viewers.openPdfPreview", true, LanguageManager.get("prefs.v.open_pdf_preview", "Open PDF with Preview application")));
        miscRight.add(ck("viewers.noInterpZoom", true, LanguageManager.get("prefs.v.no_interp_zoom", "No interpolation for zoom")));
        miscRight.add(ck("viewers.hqZoom", true, LanguageManager.get("prefs.v.hq_zoom", "High Quality Zoom (Lanczos 5)")));
        miscRight.add(ck("viewers.magnifierLens", true, LanguageManager.get("prefs.v.magnifier_lens", "Show Magnifying Lens when using 'shift' key")));
        miscRight.add(ck("viewers.markRoiKey", true, LanguageManager.get("prefs.v.mark_roi_key", "Mark ROI Image as Key Image")));
        miscRight.add(ck("viewers.samePatientOnly", false, LanguageManager.get("prefs.v.same_patient_only", "Only display images of the same patient")));
        miscRight.add(ck("viewers.gl32bit", true, LanguageManager.get("prefs.v.gl_32bit", "Full 32-bit OpenGl pipeline")));
        miscRight.add(ck("viewers.selectWindowScroll", true, LanguageManager.get("prefs.v.select_window_scroll", "Select window when using scroll wheel")));
        miscRight.add(ck("viewers.fullscreen1x1", false, LanguageManager.get("prefs.v.fullscreen_1x1", "Switch to 1x1 Tiling in Fullscreen mode")));
        miscRight.add(ck("viewers.overflowLines", true, LanguageManager.get("prefs.v.overflow_lines", "Display Overflow Lines")));
        miscRight.add(ck("viewers.circleCenter", true, LanguageManager.get("prefs.v.circle_center", "Display the Center of Circle ROIs")));
        miscRight.add(ck("viewers.roiColorRotation", true, LanguageManager.get("prefs.v.roi_color_rotation", "ROI color rotation")));
        JPanel miscWrap = new JPanel(new BorderLayout());
        miscWrap.add(miscLeft, BorderLayout.WEST);
        miscWrap.add(miscRight, BorderLayout.CENTER);
        miscWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        pane.add(miscWrap);
        pane.add(Box.createVerticalStrut(12));

        // --- Photos Album ---
        JPanel albumRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        albumRow.add(new JLabel(LanguageManager.get("prefs.v.photos_album", "Photos Album:")));
        albumRow.add(new JTextField("OculusJJ", 20));
        pane.add(albumRow);
        pane.add(Box.createVerticalStrut(12));

        // --- Legacy (Default CLUT / Opacity Table / Sync series) ---
        JSectionBox legacy = new JSectionBox(LanguageManager.get("prefs.section.interface", "Legacy"));
        pane.add(legacy);
        String[] cluts = {"CT-Body","CT-Bone","CT-Lung","CT-Brain","Gray Scale","PET-Rainbow","PET-HotMetal"};
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.default_clut", "Default CLUT:")), new JComboBox<>(cluts)));
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.default_opacity_table", "Default Opacity Table:")),
                new JComboBox<>(new String[]{"Standard","PET Specific","NM Specific"})));
        JCheckBox chkSync = new JCheckBox(LanguageManager.get("prefs.sync_series", "Synchronize series between studies"),
                AppPreferences.isSyncSeriesBetweenStudies());
        chkSync.addActionListener(e -> AppPreferences.setSyncSeriesBetweenStudies(chkSync.isSelected()));
        pane.add(chkSync);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.viewers", "Viewers"));
    }

    /** HOROS 準拠のチェックボックス（AppPreferences flat-key 永続化） */
    private JCheckBox ck(String key, boolean def, String label) {
        JCheckBox cb = new JCheckBox(label, AppPreferences.getBool(key, def));
        cb.addActionListener(e -> AppPreferences.setBool(key, cb.isSelected()));
        return cb;
    }

    private JPanel preferenceSection(String title) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title), BorderFactory.createEmptyBorder(3, 5, 5, 5)));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        return section;
    }

    private JComboBox<String> preferenceCombo(String key, int defaultIndex, String[] values) {
        JComboBox<String> combo = new JComboBox<>(values);
        int selected = Math.max(0, Math.min(values.length - 1, AppPreferences.getInt(key, defaultIndex)));
        combo.setSelectedIndex(selected);
        combo.addActionListener(e -> AppPreferences.setInt(key, combo.getSelectedIndex()));
        return combo;
    }

    private JSpinner preferenceSpinner(String key, int defaultValue, int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                Math.max(min, Math.min(max, AppPreferences.getInt(key, defaultValue))), min, max, 1));
        spinner.addChangeListener(e -> AppPreferences.setInt(key, ((Number) spinner.getValue()).intValue()));
        return spinner;
    }

    private JTextField preferenceText(String key, String defaultValue, int columns) {
        JTextField field = new JTextField(AppPreferences.getStr(key, defaultValue), columns);
        field.addActionListener(e -> AppPreferences.setStr(key, field.getText()));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                AppPreferences.setStr(key, field.getText());
            }
        });
        return field;
    }

    private JPanel preferenceTextRow(String labelKey, String fallback, String prefKey, String defaultValue) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row.add(new JLabel(LanguageManager.get(labelKey, fallback)));
        JTextField field = preferenceText(prefKey, defaultValue, 18);
        row.add(field);
        JButton reset = new JButton(LanguageManager.get("prefs.db.reset", "Reset"));
        reset.addActionListener(e -> {
            field.setText(defaultValue);
            AppPreferences.setStr(prefKey, defaultValue);
        });
        row.add(reset);
        return row;
    }

    private JRadioButton preferenceRadio(String key, int value, String label) {
        JRadioButton radio = new JRadioButton(label, AppPreferences.getInt(key, 0) == value);
        ButtonGroup group = preferenceRadioGroups.computeIfAbsent(key, k -> new ButtonGroup());
        group.add(radio);
        radio.addActionListener(e -> AppPreferences.setInt(key, value));
        return radio;
    }

    private JPanel preferenceCheckCombo(String checkboxKey, String labelKey, String fallback,
            String comboKey, String[] values) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row.add(ck(checkboxKey, false, LanguageManager.get(labelKey, fallback)));
        row.add(preferenceCombo(comboKey, 0, values));
        return row;
    }

    private JCheckBox patientUIDPreference(String key, boolean defaultValue, String labelKey, String fallback) {
        JCheckBox checkbox = new JCheckBox(LanguageManager.get(labelKey, fallback), AppPreferences.getBool(key, defaultValue));
        checkbox.addActionListener(e -> {
            boolean previous = AppPreferences.getBool(key, defaultValue);
            boolean selected = checkbox.isSelected();
            boolean otherSelected = (!key.equals("database.uidPatientId") && AppPreferences.getBool("database.uidPatientId", true))
                    || (!key.equals("database.uidBirthDate") && AppPreferences.getBool("database.uidBirthDate", true))
                    || (!key.equals("database.uidName") && AppPreferences.getBool("database.uidName", true));
            if (!selected && !otherSelected) {
                checkbox.setSelected(true);
                return;
            }
            int answer = JOptionPane.showConfirmDialog(this,
                    LanguageManager.get("prefs.db.uid_confirm", "Recompute Patient UIDs for the entire database? This may take several minutes."),
                    LanguageManager.get("prefs.db.patient_uid", "Patient Unique Identifier"), JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                checkbox.setSelected(previous);
                return;
            }
            AppPreferences.setBool(key, selected);
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    com.oculus.jj.model.DicomDatabase.recomputePatientUIDsInContext(
                            com.oculus.jj.model.DicomDatabase.activeLocalDatabase());
                    return null;
                }
                @Override protected void done() {
                    JOptionPane.showMessageDialog(PreferencesController.this,
                            LanguageManager.get("prefs.db.uid_done", "Patient UIDs were recomputed."),
                            LanguageManager.get("prefs.db.patient_uid", "Patient Unique Identifier"), JOptionPane.INFORMATION_MESSAGE);
                }
            }.execute();
        });
        return checkbox;
    }

    private void create3DPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // --- Best Rendering Button ---
        JSectionBox renderBox = new JSectionBox("Best Rendering Button");
        pane.add(renderBox);
        JPanel ssRow = new JPanel(new BorderLayout(8, 2));
        ssRow.add(new JLabel(LanguageManager.get("prefs.3d.supersampling", "Ray cast : Super Sampling:  ")), BorderLayout.WEST);
        final JLabel ssValue = new JLabel(String.format("%.2f", AppPreferences.getDouble("3d.supersampling", 3.50)));
        ssValue.setHorizontalAlignment(SwingConstants.RIGHT);
        JSlider ssSlider = new JSlider(1, 10, (int)Math.round(AppPreferences.getDouble("3d.supersampling", 3.50) * 2));
        ssSlider.setMajorTickSpacing(3);
        ssSlider.setPaintTicks(true);
        ssSlider.setPaintLabels(false);
        java.util.Hashtable<Integer, JLabel> tickLabels = new java.util.Hashtable<>();
        tickLabels.put(2, new JLabel(LanguageManager.get("prefs.3d.good", "Good")));
        tickLabels.put(6, new JLabel(LanguageManager.get("prefs.3d.better", "Better")));
        tickLabels.put(10, new JLabel(LanguageManager.get("prefs.3d.best", "Best")));
        ssSlider.setLabelTable(tickLabels);
        ssSlider.setPaintLabels(true);
        ssSlider.addChangeListener(e -> {
            double v = ssSlider.getValue() / 2.0;
            AppPreferences.setDouble("3d.supersampling", v);
            // 後方互換: 既存 3d.resolution（0-1）にも反映
            AppPreferences.set3DResolution(Math.max(0.1, Math.min(1.0, (v - 1.0) / 9.0)));
            ssValue.setText(String.format("%.2f", v));
        });
        JPanel ssMid = new JPanel(new BorderLayout());
        ssMid.add(ssSlider, BorderLayout.CENTER);
        ssMid.add(ssValue, BorderLayout.EAST);
        ssRow.add(ssMid, BorderLayout.CENTER);
        ssRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pane.add(ssRow);
        pane.add(Box.createVerticalStrut(12));

        // --- Default View Size ---
        JSectionBox sizeBox = new JSectionBox(LanguageManager.get("prefs.3d.view_size_section", "Default View Size"));
        pane.add(sizeBox);
        String[] viewSizes = {
            LanguageManager.get("prefs.3d.view_square_full", "Square - Full Screen"),
            LanguageManager.get("prefs.3d.view_512", "512 x 512"),
            LanguageManager.get("prefs.3d.view_full", "Full Screen"),
            LanguageManager.get("prefs.3d.view_768", "768 x 768")};
        ButtonGroup viewGroup = new ButtonGroup();
        int viewIdx = AppPreferences.getInt("3d.viewSize", 0);
        for (int i = 0; i < viewSizes.length; i++) {
            JRadioButton rb = new JRadioButton(viewSizes[i], i == viewIdx);
            final int idx = i;
            rb.addActionListener(e -> AppPreferences.setInt("3d.viewSize", idx));
            viewGroup.add(rb);
            pane.add(rb);
        }
        pane.add(Box.createVerticalStrut(12));

        // --- Renderer ---
        JSectionBox rendererBox = new JSectionBox(LanguageManager.get("prefs.3d.renderer_section", "Renderer"));
        pane.add(rendererBox);
        JPanel engRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        engRow.add(new JLabel(LanguageManager.get("prefs.3d.engine", "Engine:")));
        ButtonGroup engGroup = new ButtonGroup();
        boolean engCpu = AppPreferences.getBool("3d.engineCpu", true);
        JRadioButton engCpuRb = new JRadioButton(LanguageManager.get("prefs.3d.engine_cpu", "CPU - Raycasting on the main processor"), engCpu);
        JRadioButton engGpuRb = new JRadioButton(LanguageManager.get("prefs.3d.engine_gpu", "GPU - Rendering on the graphic processor"), !engCpu);
        engCpuRb.addActionListener(e -> AppPreferences.setBool("3d.engineCpu", true));
        engGpuRb.addActionListener(e -> AppPreferences.setBool("3d.engineCpu", false));
        engGroup.add(engCpuRb); engGroup.add(engGpuRb);
        engRow.add(engCpuRb); engRow.add(engGpuRb);
        pane.add(engRow);
        JPanel shadeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        shadeRow.add(new JLabel(LanguageManager.get("prefs.3d.shading_default", "Default Shading value:")));
        ButtonGroup shadeGroup = new ButtonGroup();
        boolean shadeOn = AppPreferences.is3DShadingEnabled();
        JRadioButton shadeOnRb = new JRadioButton(LanguageManager.get("prefs.3d.shading_on", "On"), shadeOn);
        JRadioButton shadeOffRb = new JRadioButton(LanguageManager.get("prefs.3d.shading_off", "Off"), !shadeOn);
        shadeOnRb.addActionListener(e -> AppPreferences.set3DShadingEnabled(true));
        shadeOffRb.addActionListener(e -> AppPreferences.set3DShadingEnabled(false));
        shadeGroup.add(shadeOnRb); shadeGroup.add(shadeOffRb);
        shadeRow.add(shadeOnRb); shadeRow.add(shadeOffRb);
        pane.add(shadeRow);
        pane.add(Box.createVerticalStrut(12));

        // --- Misc ---
        JSectionBox miscBox = new JSectionBox(LanguageManager.get("prefs.3d.misc_section", "Misc"));
        pane.add(miscBox);
        pane.add(ck("3d.multiScreen", false, LanguageManager.get("prefs.3d.multi_screen",
                "If there are multiple screens, open the 3D window on a different screen of the 2D Viewer.")));
        pane.add(ck("3d.autoRotate", true, LanguageManager.get("prefs.3d.auto_rotate",
                "Auto-Rotate objects after 3 min. in Volume Rendering/MIP viewers.")));
        pane.add(ck("3d.splineForScissors", false, LanguageManager.get("prefs.3d.spline_scissors", "Use Spline for Scissors ROI")));
        JPanel roiAlgoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        roiAlgoRow.add(new JLabel(LanguageManager.get("prefs.3d.roi_algorithm", "For representing 3D ROIs, use this algorithm:")));
        ButtonGroup roiAlgoGroup = new ButtonGroup();
        int roiAlgo = AppPreferences.getInt("3d.roiAlgorithm", 0);
        JRadioButton isoRb = new JRadioButton(LanguageManager.get("prefs.3d.roi_algo_iso", "Iso Contour"), roiAlgo == 0);
        JRadioButton delaunayRb = new JRadioButton(LanguageManager.get("prefs.3d.roi_algo_delaunay", "Delaunay"), roiAlgo == 1);
        isoRb.addActionListener(e -> AppPreferences.setInt("3d.roiAlgorithm", 0));
        delaunayRb.addActionListener(e -> AppPreferences.setInt("3d.roiAlgorithm", 1));
        roiAlgoGroup.add(isoRb); roiAlgoGroup.add(delaunayRb);
        roiAlgoRow.add(isoRb); roiAlgoRow.add(delaunayRb);
        pane.add(roiAlgoRow);
        pane.add(Box.createVerticalStrut(12));

        // --- Legacy: Default ROI Color ---
        JSectionBox legacy = new JSectionBox(LanguageManager.get("prefs.section.roi3d", "3D ROI Display (legacy)"));
        pane.add(legacy);
        int rgb = AppPreferences.getROIColor().getRGB();
        String[] roiColors = {"Yellow","Red","Green","Blue","Cyan","Magenta"};
        JComboBox<String> roiColorCombo = new JComboBox<>(roiColors);
        if (rgb == Color.YELLOW.getRGB()) roiColorCombo.setSelectedIndex(0);
        else if (rgb == Color.RED.getRGB()) roiColorCombo.setSelectedIndex(1);
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.default_roi_color", "Default ROI Color:")), roiColorCombo));
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.shading_algorithm", "Shading Algorithm:")), new JComboBox<>(new String[]{"Phong","Gouraud","Metallic"})));

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.viewer3d", "3D Viewer"));
    }

    private void createPETPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        String[] cluts = {"CT-Body","CT-Bone","CT-Lung","CT-Brain","Gray Scale","PET-Rainbow","PET-HotMetal","PET-Heat"};

        // --- CLUT (Color Look-Up Tables) ---
        JSectionBox clutBox = new JSectionBox(LanguageManager.get("prefs.pet.clut_section", "CLUT (Color Look-Up Tables) :"));
        pane.add(clutBox);
        JPanel clut1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        clut1.add(new JRadioButton(LanguageManager.get("prefs.pet.use_bw_inverse", "Use \"B/W Inverse\" CLUT with this CLUT for blending:"),
                AppPreferences.getBool("pet.useBwInverse", false)));
        clut1.add(new JComboBox<>(cluts));
        pane.add(clut1);
        JPanel clut2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        clut2.add(new JRadioButton(LanguageManager.get("prefs.pet.use_this_clut", "Use this CLUT for PET, including for blending:"),
                !AppPreferences.getBool("pet.useBwInverse", false)));
        clut2.add(new JComboBox<>(cluts));
        pane.add(clut2);
        pane.add(ck("pet.applyToNM", false, LanguageManager.get("prefs.pet.apply_nm", "Apply this behavior also to NM modality")));
        pane.add(Box.createVerticalStrut(12));

        // --- Opacity Table (Transfer Function) ---
        JSectionBox opacityBox = new JSectionBox(LanguageManager.get("prefs.pet.opacity_section", "Opacity Table (Transfer Function)"));
        pane.add(opacityBox);
        JPanel opRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        opRow.add(new JCheckBox(LanguageManager.get("prefs.pet.apply_opacity", "Apply this Opacity Table when viewing a PET:"),
                AppPreferences.getBool("pet.applyOpacity", true)));
        opRow.add(new JComboBox<>(new String[]{"Log...","Standard","Linear","Custom"}));
        pane.add(opRow);
        pane.add(ck("pet.opacityApplyNM", false, LanguageManager.get("prefs.pet.apply_nm", "Apply this behavior also to NM modality")));
        pane.add(Box.createVerticalStrut(12));

        // --- Mouse Windowing mode ---
        JSectionBox wmBox = new JSectionBox(LanguageManager.get("prefs.pet.mouse_windowing_section", "Mouse Windowing mode:"));
        pane.add(wmBox);
        ButtonGroup wmGroup = new ButtonGroup();
        int wm = AppPreferences.getInt("pet.mouseWindowing", 1);
        JRadioButton wmClassic = new JRadioButton(LanguageManager.get("prefs.pet.wm_classic", "Classic (X: Window Width  Y: Window Level )"), wm == 0);
        wmClassic.addActionListener(e -> AppPreferences.setInt("pet.mouseWindowing", 0));
        wmGroup.add(wmClassic);
        JPanel wmFixedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JRadioButton wmFixed = new JRadioButton(LanguageManager.get("prefs.pet.wm_fixed_min", "Fixed Minimum (X: nothing  Y: maximum) with minimum at:"), wm == 1);
        wmFixed.addActionListener(e -> AppPreferences.setInt("pet.mouseWindowing", 1));
        wmGroup.add(wmFixed);
        wmFixedRow.add(wmFixed);
        JTextField wmMin = new JTextField(String.valueOf(AppPreferences.getInt("pet.wmFixedMin", 0)), 6);
        wmMin.addActionListener(e -> { try { AppPreferences.setInt("pet.wmFixedMin", Integer.parseInt(wmMin.getText().trim())); } catch (NumberFormatException ignored) {} });
        wmFixedRow.add(wmMin);
        JRadioButton wmMaxMin = new JRadioButton(LanguageManager.get("prefs.pet.wm_max_min", "Maximum - Minimum (X: Minimum Y: Maximum)"), wm == 2);
        wmMaxMin.addActionListener(e -> AppPreferences.setInt("pet.mouseWindowing", 2));
        wmGroup.add(wmMaxMin);
        pane.add(wmClassic);
        pane.add(wmFixedRow);
        pane.add(wmMaxMin);
        pane.add(ck("pet.wmApplyNM", false, LanguageManager.get("prefs.pet.apply_nm", "Apply this behavior also to NM modality")));
        pane.add(Box.createVerticalStrut(12));

        // --- Window Level & Width ---
        JSectionBox wlwwBox = new JSectionBox(LanguageManager.get("prefs.pet.wlww_section", "Window Level & Width"));
        pane.add(wlwwBox);
        ButtonGroup wlwwGroup = new ButtonGroup();
        int wlww = AppPreferences.getInt("pet.wlwwMode", 0);
        JRadioButton wlwwStored = new JRadioButton(LanguageManager.get("prefs.pet.wlww_stored", "Use stored WL & WW"), wlww == 0);
        wlwwStored.addActionListener(e -> AppPreferences.setInt("pet.wlwwMode", 0));
        wlwwGroup.add(wlwwStored);
        JPanel wlwwPct = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JRadioButton wlwwPctRb = new JRadioButton(LanguageManager.get("prefs.pet.wlww_pct", "Use fixed levels, in percentages:"), wlww == 1);
        wlwwPctRb.addActionListener(e -> AppPreferences.setInt("pet.wlwwMode", 1));
        wlwwGroup.add(wlwwPctRb);
        wlwwPct.add(wlwwPctRb);
        wlwwPct.add(new JLabel(LanguageManager.get("prefs.pet.wlww_from", "from:")));
        JTextField pctFrom = new JTextField(String.valueOf(AppPreferences.getInt("pet.wlwwPctFrom", 0)), 5);
        pctFrom.addActionListener(e -> AppPreferences.setInt("pet.wlwwPctFrom", parseIntSafe(pctFrom.getText(), 0)));
        wlwwPct.add(pctFrom);
        wlwwPct.add(new JLabel(LanguageManager.get("prefs.pet.wlww_to", "to:")));
        JTextField pctTo = new JTextField(String.valueOf(AppPreferences.getInt("pet.wlwwPctTo", 100)), 5);
        pctTo.addActionListener(e -> AppPreferences.setInt("pet.wlwwPctTo", parseIntSafe(pctTo.getText(), 100)));
        wlwwPct.add(pctTo);
        JPanel wlwwSuv = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JRadioButton wlwwSuvRb = new JRadioButton(LanguageManager.get("prefs.pet.wlww_suv", "Use fixed levels, in SUV:"), wlww == 2);
        wlwwSuvRb.addActionListener(e -> AppPreferences.setInt("pet.wlwwMode", 2));
        wlwwGroup.add(wlwwSuvRb);
        wlwwSuv.add(wlwwSuvRb);
        wlwwSuv.add(new JLabel(LanguageManager.get("prefs.pet.wlww_from", "from:")));
        JTextField suvFrom = new JTextField(String.valueOf(AppPreferences.getInt("pet.wlwwSuvFrom", 0)), 5);
        suvFrom.addActionListener(e -> AppPreferences.setInt("pet.wlwwSuvFrom", parseIntSafe(suvFrom.getText(), 0)));
        wlwwSuv.add(suvFrom);
        wlwwSuv.add(new JLabel(LanguageManager.get("prefs.pet.wlww_to", "to:")));
        JTextField suvTo = new JTextField(String.valueOf(AppPreferences.getInt("pet.wlwwSuvTo", 6)), 5);
        suvTo.addActionListener(e -> AppPreferences.setInt("pet.wlwwSuvTo", parseIntSafe(suvTo.getText(), 6)));
        wlwwSuv.add(suvTo);
        pane.add(wlwwStored);
        pane.add(wlwwPct);
        pane.add(wlwwSuv);
        pane.add(Box.createVerticalStrut(12));

        // --- Fusion ---
        JSectionBox fusionBox = new JSectionBox(LanguageManager.get("prefs.pet.fusion_section", "Fusion"));
        pane.add(fusionBox);
        pane.add(ck("pet.autoFuse", true, LanguageManager.get("prefs.pet.auto_fuse", "Automatically fuse PET and CT series for PET-CT study.")));
        JPanel fusionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        fusionRow.add(new JLabel(LanguageManager.get("prefs.pet.fusion_type", "Default fusion type:")));
        String[] fusionTypes = {"Linear", "Color CLUT", "Gray"};
        JComboBox<String> fusionType = new JComboBox<>(fusionTypes);
        com.oculus.jj.model.FusionImageProcessor.FusionMode savedFusionMode = AppPreferences.getFusionMode();
        fusionType.setSelectedIndex(savedFusionMode == com.oculus.jj.model.FusionImageProcessor.FusionMode.COLOR ? 1
            : savedFusionMode == com.oculus.jj.model.FusionImageProcessor.FusionMode.GRAY ? 2 : 0);
        fusionType.addActionListener(e -> {
            int selected = fusionType.getSelectedIndex();
            AppPreferences.setFusionMode(selected == 1
                ? com.oculus.jj.model.FusionImageProcessor.FusionMode.COLOR
                : selected == 2 ? com.oculus.jj.model.FusionImageProcessor.FusionMode.GRAY
                : com.oculus.jj.model.FusionImageProcessor.FusionMode.ALPHA);
        });
        fusionRow.add(fusionType);
        pane.add(fusionRow);
        pane.add(Box.createVerticalStrut(12));

        // --- SUV Computation ---
        JSectionBox suvBox = new JSectionBox(LanguageManager.get("prefs.pet.suv_section", "SUV Computation"));
        pane.add(suvBox);
        pane.add(ck("pet.suvAuto", false, LanguageManager.get("prefs.pet.suv_auto", "Automatically convert PET in SUV when loading, if possible.")));
        JPanel acqRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        acqRow.add(new JLabel(LanguageManager.get("prefs.pet.acq_time_param", "Use for Acquisition Time parameter:")));
        acqRow.add(new JComboBox<>(new String[]{"SeriesTime (0008,0031)","StudyTime (0008,0033)","InstanceTime (0008,0032)"}));
        pane.add(acqRow);
        JLabel doseNote = new JLabel(LanguageManager.get("prefs.pet.dose_note",
                "Dose is corrected by the half-life, in relation to the Acquisition Time and the injection Time. Corrected dose = Dose * (ln(2) / Half-life). Check with the PET modality manufacturer, which field should be used."));
        doseNote.setBorder(BorderFactory.createEmptyBorder(4, 12, 0, 0));
        pane.add(doseNote);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.petsuv", "PET/SUV"));
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private void createAnnotationsPane() {
        JPanel pane = new JPanel(new BorderLayout(6, 6));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        toolbar.add(new JLabel(LanguageManager.get("prefs.ann.modality", "Modality:")));
        JComboBox<String> modality = new JComboBox<>(new String[]{"Default", "CR", "CT", "DX", "MG", "MR", "NM", "PT", "US", "XA"});
        modality.setSelectedItem(AppPreferences.getStr("annotations.modality", "Default"));
        modality.addActionListener(e -> AppPreferences.setStr("annotations.modality", String.valueOf(modality.getSelectedItem())));
        toolbar.add(modality);
        JButton reset = new JButton(LanguageManager.get("prefs.ann.reset", "Reset"));
        JButton add = new JButton("+");
        JButton remove = new JButton("-");
        toolbar.add(reset); toolbar.add(add); toolbar.add(remove);
        JCheckBox orientation = ck("annotations.showOrientation", true, LanguageManager.get("prefs.ann.orientation", "Orientation"));
        toolbar.add(orientation);
        pane.add(toolbar, BorderLayout.NORTH);

        String[] zoneNames = {"topLeft", "topCenter", "topRight", "middleLeft", "middleCenter", "middleRight", "bottomLeft", "bottomCenter", "bottomRight"};
        JPanel canvas = new JPanel(new GridLayout(3, 3, 2, 2));
        canvas.setBackground(new Color(80, 80, 80));
        canvas.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        java.util.Map<String, JPanel> holders = new java.util.LinkedHashMap<>();
        for (String zone : zoneNames) {
            JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
            holder.setOpaque(false);
            holder.setBorder(BorderFactory.createDashedBorder(Color.GRAY));
            holders.put(zone, holder);
            canvas.add(holder);
        }
        java.util.Map<String, java.util.List<String>> defaults = new java.util.LinkedHashMap<>();
        defaults.put("topLeft", java.util.Arrays.asList("Image Size", "View Size", "WL / WW", "Mouse (px)", "Mouse (mm)"));
        defaults.put("topCenter", java.util.Arrays.asList("Orientation"));
        defaults.put("topRight", java.util.Arrays.asList("Patient ID", "Study Description", "Series Description", "Series ID"));
        defaults.put("middleLeft", java.util.Arrays.asList("Orientation"));
        defaults.put("middleRight", java.util.Arrays.asList("Orientation"));
        defaults.put("bottomLeft", java.util.Arrays.asList("Zoom & Angle", "Image Position", "Compression", "Thickness & Location"));
        defaults.put("bottomCenter", java.util.Arrays.asList("Orientation"));
        defaults.put("bottomRight", java.util.Arrays.asList("MRI", "Field Strength", "Image Acquisition Date"));
        String saved = AppPreferences.getStr("annotations.layout", "");
        if (!saved.isBlank()) {
            try {
                java.util.Map<String, java.util.List<String>> loaded = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(saved, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, java.util.List<String>>>() {});
                defaults.putAll(loaded);
            } catch (Exception ignored) { }
        }
        java.util.Map<String, java.util.List<String>> layout = defaults;
        Runnable saveLayout = () -> {
            java.util.Map<String, java.util.List<String>> current = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<String, JPanel> entry : holders.entrySet()) {
                java.util.List<String> values = new java.util.ArrayList<>();
                for (Component component : entry.getValue().getComponents())
                    if (component instanceof JLabel label) values.add(label.getText());
                current.put(entry.getKey(), values);
            }
            try { AppPreferences.setStr("annotations.layout", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(current)); }
            catch (Exception ignored) { }
        };
        class ChipHandler extends TransferHandler {
            private final Runnable save;
            ChipHandler(Runnable save) { this.save = save; }
            @Override protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
                return new java.awt.datatransfer.StringSelection(((JLabel)c).getText());
            }
            @Override public int getSourceActions(JComponent c) { return MOVE; }
            @Override public boolean canImport(TransferSupport support) {
                return support.isDrop() && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)
                        && support.getComponent() instanceof JPanel;
            }
            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    String text = (String)support.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
                    JPanel target = (JPanel)support.getComponent();
                    JLabel chip = new JLabel(text);
                    chip.setOpaque(true); chip.setForeground(Color.WHITE); chip.setBackground(new Color(235, 87, 30));
                    chip.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                    chip.setTransferHandler(this);
                    chip.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                        @Override public void mouseDragged(java.awt.event.MouseEvent e) { getTransferHandler().exportAsDrag(chip, e, MOVE); }
                    });
                    target.add(chip); target.revalidate(); target.repaint(); save.run();
                    return true;
                } catch (Exception ex) { return false; }
            }
            @Override protected void exportDone(JComponent source, java.awt.datatransfer.Transferable data, int action) {
                if (action == MOVE && source.getParent() != null) {
                    Container parent = source.getParent(); parent.remove(source); parent.revalidate(); parent.repaint(); save.run();
                }
            }
        }
        TransferHandler chipHandler = new ChipHandler(saveLayout);
        for (java.util.Map.Entry<String, JPanel> entry : holders.entrySet()) {
            JPanel holder = entry.getValue();
            holder.setTransferHandler(chipHandler);
            for (String labelText : layout.getOrDefault(entry.getKey(), java.util.Collections.emptyList())) {
                JLabel chip = new JLabel(labelText);
                chip.setOpaque(true); chip.setForeground(Color.WHITE); chip.setBackground(new Color(235, 87, 30));
                chip.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                chip.setTransferHandler(chipHandler);
                chip.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                    @Override public void mouseDragged(java.awt.event.MouseEvent e) { chipHandler.exportAsDrag(chip, e, TransferHandler.MOVE); }
                });
                holder.add(chip);
            }
        }
        JLabel centerHint = new JLabel(LanguageManager.get("prefs.ann.drag", "Drag Annotations into the placeholders"), SwingConstants.CENTER);
        centerHint.setForeground(Color.LIGHT_GRAY);
        holders.get("middleCenter").add(centerHint);
        reset.addActionListener(e -> {
            for (JPanel holder : holders.values()) holder.removeAll();
            defaults.forEach((zone, values) -> {
                if ("middleCenter".equals(zone)) return;
                for (String value : values) {
                    JLabel chip = new JLabel(value); chip.setOpaque(true); chip.setForeground(Color.WHITE);
                    chip.setBackground(new Color(235, 87, 30)); chip.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                    chip.setTransferHandler(chipHandler);
                    chip.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                        @Override public void mouseDragged(java.awt.event.MouseEvent e) { chipHandler.exportAsDrag(chip, e, TransferHandler.MOVE); }
                    });
                    holders.get(zone).add(chip);
                }
            });
            holders.get("middleCenter").add(centerHint);
            saveLayout.run(); canvas.revalidate(); canvas.repaint();
        });
        add.addActionListener(e -> {
            String value = JOptionPane.showInputDialog(this, LanguageManager.get("prefs.ann.title", "Selected Annotation Title:"));
            if (value != null && !value.isBlank()) {
                JLabel chip = new JLabel(value.trim()); chip.setOpaque(true); chip.setForeground(Color.WHITE);
                chip.setBackground(new Color(235, 87, 30)); chip.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                chip.setTransferHandler(chipHandler);
                chip.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                    @Override public void mouseDragged(java.awt.event.MouseEvent e) { chipHandler.exportAsDrag(chip, e, TransferHandler.MOVE); }
                });
                holders.get("topLeft").add(chip); saveLayout.run(); canvas.revalidate(); canvas.repaint();
            }
        });
        remove.addActionListener(e -> {
            for (JPanel holder : holders.values()) {
                Component[] components = holder.getComponents();
                for (Component component : components) if (component instanceof JLabel label && label.isOpaque()) {
                    holder.remove(component); saveLayout.run(); canvas.revalidate(); canvas.repaint(); return;
                }
            }
        });
        pane.add(canvas, BorderLayout.CENTER);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JPanel titleRow = new JPanel(new BorderLayout(5, 0));
        titleRow.add(new JLabel(LanguageManager.get("prefs.ann.title", "Selected Annotation Title:")), BorderLayout.WEST);
        JTextField title = preferenceText("annotations.selectedTitle", "", 35);
        titleRow.add(title, BorderLayout.CENTER);
        details.add(titleRow);
        details.add(new JLabel(LanguageManager.get("prefs.ann.content", "Content:")));
        JTextArea content = new JTextArea(AppPreferences.getStr("annotations.content", ""), 3, 50);
        content.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void save() { AppPreferences.setStr("annotations.content", content.getText()); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { save(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { save(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { save(); }
        });
        details.add(new JScrollPane(content));
        JPanel fields = new JPanel(new GridLayout(2, 2, 4, 4));
        fields.add(collapsibleField("prefs.ann.dicom_fields", "DICOM fields", new String[]{"Patient ID", "Study Description", "Series Description", "Series Number"}));
        fields.add(collapsibleField("prefs.ann.custom_dicom", "Custom DICOM field", new String[]{"Group", "Element", "Name"}));
        fields.add(collapsibleField("prefs.ann.database_fields", "Database fields", new String[]{"Study level", "Patient", "Study Date"}));
        fields.add(collapsibleField("prefs.ann.other", "Other infos", new String[]{"Image Size", "View Size", "WL / WW"}));
        details.add(fields);
        JPanel saveLoad = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton save = new JButton(LanguageManager.get("prefs.ann.save", "Save"));
        JButton load = new JButton(LanguageManager.get("prefs.ann.load", "Load"));
        save.addActionListener(e -> saveAnnotationsLayout(layout, holders));
        load.addActionListener(e -> loadAnnotationsLayout(canvas, holders, chipHandler));
        saveLoad.add(save); saveLoad.add(load); details.add(saveLoad);
        pane.add(details, BorderLayout.SOUTH);
        contentPanel.add(pane, LanguageManager.get("prefs.nav.annotations", "Annotations"));
    }

    private JPanel collapsibleField(String key, String fallback, String[] choices) {
        JPanel panel = new JPanel(new BorderLayout());
        JCheckBox toggle = new JCheckBox(LanguageManager.get(key, fallback));
        JComboBox<String> combo = new JComboBox<>(choices);
        combo.setEnabled(false);
        toggle.addActionListener(e -> combo.setEnabled(toggle.isSelected()));
        panel.add(toggle, BorderLayout.NORTH);
        panel.add(combo, BorderLayout.CENTER);
        return panel;
    }

    private void saveAnnotationsLayout(java.util.Map<String, java.util.List<String>> initial,
            java.util.Map<String, JPanel> holders) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.util.Map<String, java.util.List<String>> current = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, JPanel> entry : holders.entrySet()) {
            java.util.List<String> items = new java.util.ArrayList<>();
            for (Component c : entry.getValue().getComponents()) if (c instanceof JLabel label && label.isOpaque()) items.add(label.getText());
            current.put(entry.getKey(), items);
        }
        try { new com.fasterxml.jackson.databind.ObjectMapper().writeValue(chooser.getSelectedFile(), current); }
        catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
    }

    private void loadAnnotationsLayout(JPanel canvas, java.util.Map<String, JPanel> holders, TransferHandler handler) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            java.util.Map<String, java.util.List<String>> loaded = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    chooser.getSelectedFile(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, java.util.List<String>>>() {});
            for (JPanel holder : holders.values()) holder.removeAll();
            loaded.forEach((zone, values) -> {
                JPanel holder = holders.get(zone);
                if (holder == null) return;
                for (String value : values) {
                    JLabel chip = new JLabel(value); chip.setOpaque(true); chip.setForeground(Color.WHITE);
                    chip.setBackground(new Color(235, 87, 30)); chip.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                    chip.setTransferHandler(handler);
                    chip.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                        @Override public void mouseDragged(java.awt.event.MouseEvent e) { handler.exportAsDrag(chip, e, TransferHandler.MOVE); }
                    });
                    holder.add(chip);
                }
            });
            canvas.revalidate(); canvas.repaint();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
    }

    private void createDICOMPrintPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JCheckBox resize = ck("dicomPrint.autoResizeWindow", true,
            LanguageManager.get("prefs.print.resize", "Automatically resize window to a 'square size' before printing"));
        pane.add(resize);

        JPanel printerSection = preferenceSection(LanguageManager.get("prefs.print.printers", "DICOM Printers"));
        String[] printerColumns = {
            LanguageManager.get("prefs.print.printer", "Printer"), LanguageManager.get("prefs.print.host", "Host"),
            LanguageManager.get("prefs.print.port", "Port"), LanguageManager.get("prefs.print.aetitle", "AETitle"), "Default"};
        DefaultTableModel printerModel = new DefaultTableModel(printerColumns, 0) {
            @Override public Class<?> getColumnClass(int col) { return col == 4 ? Boolean.class : String.class; }
        };
        String printerData = AppPreferences.getStr("dicomPrint.printers", "");
        if (!printerData.isBlank()) {
            try {
            String[][] saved = new com.fasterxml.jackson.databind.ObjectMapper().readValue(printerData, String[][].class);
            for (String[] row : saved) if (row.length == 5)
                printerModel.addRow(new Object[]{row[0], row[1], row[2], row[3], Boolean.parseBoolean(row[4])});
            } catch (Exception ignored) { }
        }
        JTable printerTable = new JTable(printerModel);
        printerTable.setRowHeight(21);
        Runnable savePrinters = () -> {
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            for (int r = 0; r < printerModel.getRowCount(); r++) {
            rows.add(new String[]{String.valueOf(printerModel.getValueAt(r, 0)), String.valueOf(printerModel.getValueAt(r, 1)),
                String.valueOf(printerModel.getValueAt(r, 2)), String.valueOf(printerModel.getValueAt(r, 3)),
                String.valueOf(printerModel.getValueAt(r, 4))});
            }
            try { AppPreferences.setStr("dicomPrint.printers", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rows)); }
            catch (Exception ignored) { }
        };
        printerModel.addTableModelListener(e -> savePrinters.run());
        JScrollPane printerScroll = new JScrollPane(printerTable);
        printerScroll.setPreferredSize(new Dimension(700, 110));
        printerSection.add(printerScroll);
        JPanel printerButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton addPrinter = new JButton("+");
        addPrinter.addActionListener(e -> {
            JTextField name = new JTextField(12), host = new JTextField(12), port = new JTextField("104", 6), aet = new JTextField(10);
            JPanel fields = new JPanel(new GridLayout(4, 2, 4, 4));
            fields.add(new JLabel(LanguageManager.get("prefs.print.printer", "Printer"))); fields.add(name);
            fields.add(new JLabel(LanguageManager.get("prefs.print.host", "Host"))); fields.add(host);
            fields.add(new JLabel(LanguageManager.get("prefs.print.port", "Port"))); fields.add(port);
            fields.add(new JLabel(LanguageManager.get("prefs.print.aetitle", "AETitle"))); fields.add(aet);
            if (JOptionPane.showConfirmDialog(this, fields, "DICOM Printer", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
            printerModel.addRow(new Object[]{name.getText(), host.getText(), port.getText(), aet.getText(), printerModel.getRowCount() == 0});
        });
        JButton removePrinter = new JButton("-");
        removePrinter.addActionListener(e -> { int r = printerTable.getSelectedRow(); if (r >= 0) printerModel.removeRow(printerTable.convertRowIndexToModel(r)); });
        JButton setDefault = new JButton(LanguageManager.get("prefs.print.default", "set as default"));
        setDefault.addActionListener(e -> {
            int selected = printerTable.getSelectedRow();
            if (selected < 0) return;
            int modelRow = printerTable.convertRowIndexToModel(selected);
            for (int r = 0; r < printerModel.getRowCount(); r++) printerModel.setValueAt(r == modelRow, r, 4);
        });
        JButton saveAs = new JButton(LanguageManager.get("prefs.print.save", "Save as..."));
        saveAs.addActionListener(e -> savePrinterList(printerModel));
        JButton loadFrom = new JButton(LanguageManager.get("prefs.print.load", "Load from..."));
        loadFrom.addActionListener(e -> loadPrinterList(printerModel));
        printerButtons.add(addPrinter); printerButtons.add(removePrinter); printerButtons.add(setDefault);
        printerButtons.add(Box.createHorizontalGlue()); printerButtons.add(saveAs); printerButtons.add(loadFrom);
        printerSection.add(printerButtons);
        pane.add(printerSection);

        JPanel settings = new JPanel(new GridLayout(1, 3, 8, 0));
        JPanel film = preferenceSection(LanguageManager.get("prefs.print.settings", "Settings:"));
        film.add(makeRow(new JLabel(LanguageManager.get("prefs.print.film_orientation", "Film Orientation:")),
            preferenceCombo("dicomPrint.filmOrientationIndex", 1, new String[]{"Portrait", "Landscape"})));
        film.add(makeRow(new JLabel(LanguageManager.get("prefs.print.film_size", "Film Size:")),
            preferenceCombo("dicomPrint.filmSize", 0, new String[]{"8 IN X 10 IN", "10 IN X 12 IN", "10 IN X 14 IN", "14 IN X 17 IN"})));
        film.add(makeRow(new JLabel(LanguageManager.get("prefs.print.destination", "Film Destination:")),
            preferenceCombo("dicomPrint.filmDestination", 0, new String[]{"Processor", "Magazine", "Bin 1", "Bin 2"})));
        film.add(makeRow(new JLabel(LanguageManager.get("prefs.print.magnification", "Magnification Type:")),
            preferenceCombo("dicomPrint.magnification", 0, new String[]{"NONE", "CUBIC", "BILINEAR", "REPLICATE"})));
        film.add(makeRow(new JLabel(LanguageManager.get("prefs.print.trim", "Trim:")),
            preferenceCombo("dicomPrint.trim", 0, new String[]{"NO", "YES"})));
        film.add(makeRow(new JLabel(LanguageManager.get("prefs.print.configuration", "Configuration Information:")),
            preferenceText("dicomPrint.configuration", "No Selection", 14)));
        settings.add(film);

        JPanel layout = preferenceSection(LanguageManager.get("prefs.print.layout", "Layout:"));
        layout.add(makeRow(new JLabel(LanguageManager.get("prefs.print.format", "Format:")),
            preferenceCombo("dicomPrint.layoutFormat", 0, new String[]{"Standard 1:1", "Standard 2:1", "Standard 1:2", "Standard 2:2"})));
        layout.add(ck("dicomPrint.automaticLayout", false, LanguageManager.get("prefs.print.automatically", "Automatically")));
        layout.add(makeRow(new JLabel(LanguageManager.get("prefs.print.border_color", "Border Color:")),
            preferenceCombo("dicomPrint.borderColor", 0, new String[]{"BLACK", "WHITE"})));
        layout.add(makeRow(new JLabel(LanguageManager.get("prefs.print.empty_color", "Empty Image Color:")),
            preferenceCombo("dicomPrint.emptyColor", 0, new String[]{"BLACK", "WHITE"})));
        settings.add(layout);

        JPanel job = preferenceSection(LanguageManager.get("prefs.print.job", "Job:"));
        job.add(makeRow(new JLabel(LanguageManager.get("prefs.print.priority", "Priority:")),
            preferenceCombo("dicomPrint.priority", 0, new String[]{"HIGH", "MED", "LOW"})));
        job.add(makeRow(new JLabel(LanguageManager.get("prefs.print.medium", "Medium:")),
            preferenceCombo("dicomPrint.medium", 0, new String[]{"Blue Film", "Clear Film", "Paper"})));
        job.add(makeRow(new JLabel(LanguageManager.get("prefs.print.copies", "Copies:")),
            preferenceCombo("dicomPrint.copies", 0, new String[]{"No S", "1", "2", "3", "4", "5"})));
        settings.add(job);
        pane.add(settings);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.print", "DICOM Print"));
    }

        private void savePrinterList(DefaultTableModel model) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            rows.add(new String[]{String.valueOf(model.getValueAt(r, 0)), String.valueOf(model.getValueAt(r, 1)),
                String.valueOf(model.getValueAt(r, 2)), String.valueOf(model.getValueAt(r, 3)), String.valueOf(model.getValueAt(r, 4))});
        }
        try { new com.fasterxml.jackson.databind.ObjectMapper().writeValue(chooser.getSelectedFile(), rows); }
        catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
        }

        private void loadPrinterList(DefaultTableModel model) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String[][] rows = new com.fasterxml.jackson.databind.ObjectMapper().readValue(chooser.getSelectedFile(), String[][].class);
            model.setRowCount(0);
            for (String[] row : rows) if (row.length == 5)
            model.addRow(new Object[]{row[0], row[1], row[2], row[3], Boolean.parseBoolean(row[4])});
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
        }

    // === SHARING PANES ===

    private void createListenerPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel connection = preferenceSection(LanguageManager.get("prefs.lis.activate", "Activate DICOM listener when Horos is running"));
        JPanel aetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        aetRow.add(new JLabel(LanguageManager.get("prefs.lis.aetitle", "AETitle:")));
        JTextField aet = preferenceText("listener.aetitle", AppPreferences.getListenerAETitle(), 18);
        aetRow.add(aet);
        aetRow.add(ck("listener.useHostAsAetitle", false, LanguageManager.get("prefs.lis.use_host", "Use Host Name for AETitle")));
        connection.add(aetRow);
        JPanel portRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        portRow.add(new JLabel(LanguageManager.get("prefs.lis.port", "Port Number:")));
        JTextField port = new JTextField(String.valueOf(AppPreferences.getListenerPort()), 8);
        port.addActionListener(e -> saveIntField(port, "listener.port", 11112, 1, 131072));
        port.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { saveIntField(port, "listener.port", 11112, 1, 131072); }
        });
        portRow.add(port);
        portRow.add(new JLabel(LanguageManager.get("prefs.lis.port_range", "(between 1 and 131072)")));
        connection.add(portRow);
        JCheckBox listenerActive = new JCheckBox(LanguageManager.get("prefs.lis.activate", "Activate DICOM listener when Horos is running"),
                AppPreferences.isListenerAutoStart());
        listenerActive.addActionListener(e -> {
            boolean enabled = listenerActive.isSelected();
            AppPreferences.setListenerAutoStart(enabled);
            try {
                int listenerPort = Integer.parseInt(port.getText().trim());
                if (listenerPort < 1 || listenerPort > 65535) throw new NumberFormatException("Port must be 1-65535");
                AppPreferences.setListenerPort(listenerPort);
                AppPreferences.setListenerAETitle(aet.getText().trim());
                DicomNodeManager.getInstance().setLocalConfig(aet.getText().trim(), listenerPort);
                if (enabled) DicomService.getInstance().startListener(listenerPort);
                else DicomService.getInstance().stopListener();
            } catch (Exception ex) {
                listenerActive.setSelected(!enabled);
                AppPreferences.setListenerAutoStart(!enabled);
                JOptionPane.showMessageDialog(this, ex.getMessage(), LanguageManager.get("prefs.error", "Error"), JOptionPane.ERROR_MESSAGE);
            }
        });
        connection.add(listenerActive);
        connection.add(makeRow(new JLabel(LanguageManager.get("prefs.lis.address", "Address(es):")), preferenceText("listener.addresses", "", 25)));
        connection.add(makeRow(new JLabel(LanguageManager.get("prefs.lis.hostname", "Host Name:")), preferenceText("listener.hostName", "", 25)));
        connection.add(makeRow(new JLabel(LanguageManager.get("prefs.lis.preferred_syntax", "Preferred Syntax:")),
                preferenceCombo("listener.preferredSyntax", 0, new String[]{"Explicit Little Endian", "Implicit Little Endian", "Explicit Big Endian"})));
        connection.add(ck("listener.storeDestinationAetitle", false, LanguageManager.get("prefs.lis.store_dest", "Store Destination AETitle in PrivateInformationCreatorUID (0002,0100)")));
        connection.add(ck("listener.storeSourceAetitle", true, LanguageManager.get("prefs.lis.store_src", "Store Source AETitle in SourceApplicationEntityTitle (0002,0016)")));
        connection.add(ck("listener.activeUserOnly", false, LanguageManager.get("prefs.lis.user_session", "Activate the DICOM Listener only if this user session is active (user switching)")));
        JPanel timeouts = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        timeouts.add(new JLabel(LanguageManager.get("prefs.lis.timeout", "Time-out for DICOM communications:")));
        timeouts.add(preferenceText("listener.timeout", "40", 4));
        timeouts.add(new JLabel(LanguageManager.get("prefs.lis.seconds", "seconds")));
        timeouts.add(new JLabel(LanguageManager.get("prefs.lis.conn_timeout", "Connection time-out:")));
        timeouts.add(preferenceText("listener.connectionTimeout", "10", 4));
        timeouts.add(new JLabel(LanguageManager.get("prefs.lis.seconds", "seconds")));
        connection.add(timeouts);
        JPanel tls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        tls.add(ck("listener.tlsEnabled", false, LanguageManager.get("prefs.lis.tls", "Activate DICOM TLS Listener")));
        JButton tlsSettings = new JButton(LanguageManager.get("prefs.lis.tls_settings", "TLS Settings"));
        tlsSettings.addActionListener(e -> JOptionPane.showMessageDialog(this, "TLS certificate configuration is not available yet."));
        tls.add(tlsSettings);
        connection.add(tls);
        pane.add(connection);

        JPanel incoming = preferenceSection(LanguageManager.get("prefs.lis.incoming", "Incoming files"));
        JPanel checkNew = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        checkNew.add(new JLabel(LanguageManager.get("prefs.lis.check_new", "Check for new files every:")));
        checkNew.add(preferenceText("listener.checkInterval", "3", 4));
        checkNew.add(new JLabel(LanguageManager.get("prefs.lis.seconds", "seconds")));
        incoming.add(checkNew);
        ButtonGroup handlingGroup = new ButtonGroup();
        int handlingValue = AppPreferences.getInt("listener.fileHandling", 0);
        String[] handlingKeys = {"prefs.lis.dont_modify", "prefs.lis.decompress", "prefs.lis.compress"};
        String[] handlingFallbacks = {"Don't modify", "Decompress compressed images", "Compress non-compressed images with JPEG"};
        for (int i = 0; i < handlingKeys.length; i++) {
            final int mode = i;
            JRadioButton rb = new JRadioButton(LanguageManager.get(handlingKeys[i], handlingFallbacks[i]), handlingValue == i);
            rb.addActionListener(e -> AppPreferences.setInt("listener.fileHandling", mode));
            handlingGroup.add(rb);
            incoming.add(rb);
        }
        incoming.add(new JLabel(LanguageManager.get("prefs.lis.cant_open", "If Horos is not able to open a file received by the DICOM listener:")));
        ButtonGroup unreadableGroup = new ButtonGroup();
        int unreadableValue = AppPreferences.getInt("listener.unreadableAction", 0);
        JRadioButton deleteUnreadable = new JRadioButton(LanguageManager.get("prefs.lis.delete_it", "Delete it"), unreadableValue == 0);
        JRadioButton moveUnreadable = new JRadioButton(LanguageManager.get("prefs.lis.move_not_readable", "Move it to the NOT READABLE folder"), unreadableValue == 1);
        deleteUnreadable.addActionListener(e -> AppPreferences.setInt("listener.unreadableAction", 0));
        moveUnreadable.addActionListener(e -> AppPreferences.setInt("listener.unreadableAction", 1));
        unreadableGroup.add(deleteUnreadable); unreadableGroup.add(moveUnreadable);
        incoming.add(deleteUnreadable); incoming.add(moveUnreadable);
        incoming.add(ck("listener.replaceExisting", false, LanguageManager.get("prefs.lis.replace", "Replace existing files with the newly received files")));
        pane.add(incoming);

        JPanel other = preferenceSection(LanguageManager.get("prefs.lis.other", "Other options"));
        other.add(ck("listener.databaseSharing", false, LanguageManager.get("prefs.lis.share_db", "Activate Horos database sharing, and publish it using the name:")));
        other.add(preferenceText("listener.publishName", "OculusJJ", 20));
        other.add(ck("listener.publishPassword", false, LanguageManager.get("prefs.lis.publish_pw", "Publish it with a password")));
        other.add(ck("listener.bonjour", true, LanguageManager.get("prefs.lis.bonjour", "Publish my DICOM node over the network through the Bonjour protocol")));
        other.add(ck("listener.linkToDefaultDatabase", true, LanguageManager.get("prefs.lis.link_default", "Incoming and shared files are always linked to the default database")));
        other.add(ck("listener.generateLogs", false, LanguageManager.get("prefs.lis.logs", "Generate logs for DICOM Network events")));
        JPanel logsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        logsRow.add(new JLabel(LanguageManager.get("prefs.lis.keep_logs", "Keep logs for:")));
        logsRow.add(preferenceCombo("listener.logRetention", 0, new String[]{"1 week", "1 month", "1 year"}));
        other.add(logsRow);
        other.add(ck("listener.serverMode", false, LanguageManager.get("prefs.lis.server_mode", "Server mode (don't display errors messages, hide Database window at startup)")));
        other.add(ck("listener.cgetScp", true, LanguageManager.get("prefs.lis.cget", "Activate C-GET SCP support")));
        other.add(ck("listener.cfindScp", false, LanguageManager.get("prefs.lis.cfind", "Activate C-FIND SCP support")));
        JPanel xmlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        xmlRow.add(ck("listener.xmlRpcEnabled", false, LanguageManager.get("prefs.lis.url_xmlrpc", "Activate the URL support and HTTP XML-RPC Server on port:")));
        xmlRow.add(preferenceText("listener.xmlRpcPort", "8080", 6));
        other.add(xmlRow);
        other.add(ck("listener.searchOnDemandUrls", false, LanguageManager.get("prefs.lis.pacs_ondemand", "Also search on PACS On-Demand servers for URLs and XML-RPC orders")));
        other.add(ck("listener.closeViewersForXmlRpc", false, LanguageManager.get("prefs.lis.close_2d", "Automatically close all 2D Viewers before displaying studies with XML-RPC orders")));
        pane.add(other);
        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.listener", "Listener"));
    }

    private void saveIntField(JTextField field, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < min || value > max) throw new NumberFormatException();
            AppPreferences.setInt(key, value);
        } catch (NumberFormatException ex) {
            field.setText(String.valueOf(AppPreferences.getInt(key, fallback)));
        }
    }

    private void createLocationsPane() {
        JPanel locationsPane = new JPanel();
        locationsPane.setLayout(new BoxLayout(locationsPane, BoxLayout.Y_AXIS));
        locationsPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel nodes = preferenceSection(LanguageManager.get("prefs.loc.nodes_title", "DICOM Nodes for DICOM Query/Retrieve and DICOM Send"));
        nodes.add(new JLabel(LanguageManager.get("prefs.loc.delete_hint", "Press Delete key to remove a node")));
        String[] cols = {"✓", LanguageManager.get("prefs.node.address", "Address"), "AETitle",
                LanguageManager.get("prefs.node.port", "Port"), "Q/R", "Retrieve", "Send", "TLS",
                LanguageManager.get("prefs.node.name", "Name"), "Send Transfer Syntax"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public Class<?> getColumnClass(int col) { return col == 0 || col == 4 || col == 6 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int row, int col) { return col != 5; }
        };
        java.util.List<DicomNode> nodesList = DicomNodeManager.getInstance().getNodes();
        for (DicomNode node : nodesList) {
            model.addRow(new Object[]{true, node.getAddress(), node.getAeTitle(), String.valueOf(node.getPort()),
                    false, "C-MOVE", true, "No", node.getName(), "Explicit Little Endian"});
        }
        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(22);
        JScrollPane nodeScroll = new JScrollPane(table);
        nodeScroll.setPreferredSize(new Dimension(760, 150));
        nodes.add(nodeScroll);
        JPanel nodeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton allBtn = new JButton(LanguageManager.get("prefs.loc.all", "All"));
        allBtn.addActionListener(e -> { for (int r = 0; r < model.getRowCount(); r++) model.setValueAt(true, r, 0); });
        JButton noneBtn = new JButton(LanguageManager.get("prefs.loc.none", "None"));
        noneBtn.addActionListener(e -> { for (int r = 0; r < model.getRowCount(); r++) model.setValueAt(false, r, 0); });
        JButton addBtn = new JButton(LanguageManager.get("prefs.loc.add_node", "Add new node"));
        addBtn.addActionListener(e -> {
            DicomNode node = new DicomNode();
            showNodeEditor(node, () -> {
                model.addRow(new Object[]{true, node.getAddress(), node.getAeTitle(), String.valueOf(node.getPort()),
                        false, "C-MOVE", true, "No", node.getName(), "Explicit Little Endian"});
            });
        });
        JButton deleteBtn = new JButton(LanguageManager.get("prefs.node_delete", "Delete"));
        deleteBtn.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow >= 0) {
                int row = table.convertRowIndexToModel(viewRow);
                String address = String.valueOf(model.getValueAt(row, 1));
                String aet = String.valueOf(model.getValueAt(row, 2));
                DicomNodeManager.getInstance().getNodes().stream()
                        .filter(n -> n.getAddress().equals(address) && n.getAeTitle().equals(aet))
                        .findFirst().ifPresent(DicomNodeManager.getInstance()::removeNode);
                model.removeRow(row);
            }
        });
        JButton verifyBtn = new JButton(LanguageManager.get("prefs.loc.verify", "Verify"));
        verifyBtn.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) return;
            int row = table.convertRowIndexToModel(viewRow);
            DicomNode node = new DicomNode();
            node.setAddress(String.valueOf(model.getValueAt(row, 1)));
            node.setAeTitle(String.valueOf(model.getValueAt(row, 2)));
            try { node.setPort(Integer.parseInt(String.valueOf(model.getValueAt(row, 3)))); }
            catch (NumberFormatException ex) { return; }
            new Thread(() -> {
                try {
                    boolean ok = DicomService.getInstance().echo(node);
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            ok ? LanguageManager.get("prefs.echo_ok", "Echo Success!") : LanguageManager.get("prefs.echo_failed", "Echo Failed")));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            LanguageManager.get("prefs.error", "Error:") + " " + ex.getMessage()));
                }
            }).start();
        });
        JButton saveNodes = new JButton(LanguageManager.get("prefs.loc.save", "Save..."));
        saveNodes.addActionListener(e -> {
            try {
                JFileChooser chooser = new JFileChooser();
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    java.util.List<DicomNode> export = new java.util.ArrayList<>();
                    for (int r = 0; r < model.getRowCount(); r++) {
                        DicomNode n = new DicomNode(String.valueOf(model.getValueAt(r, 8)), String.valueOf(model.getValueAt(r, 2)),
                                String.valueOf(model.getValueAt(r, 1)), Integer.parseInt(String.valueOf(model.getValueAt(r, 3))));
                        export.add(n);
                    }
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValue(chooser.getSelectedFile(), export);
                }
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
        });
        JButton loadNodes = new JButton(LanguageManager.get("prefs.loc.load", "Load..."));
        loadNodes.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    DicomNode[] loaded = new com.fasterxml.jackson.databind.ObjectMapper().readValue(chooser.getSelectedFile(), DicomNode[].class);
                    for (DicomNode node : loaded) {
                        DicomNodeManager.getInstance().addNode(node);
                        model.addRow(new Object[]{true, node.getAddress(), node.getAeTitle(), String.valueOf(node.getPort()),
                                false, "C-MOVE", true, "No", node.getName(), "Explicit Little Endian"});
                    }
                } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
            }
        });
        nodeButtons.add(allBtn); nodeButtons.add(noneBtn); nodeButtons.add(saveNodes); nodeButtons.add(loadNodes);
        nodeButtons.add(verifyBtn); nodeButtons.add(addBtn); nodeButtons.add(deleteBtn);
        nodes.add(nodeButtons);
        nodes.add(preferenceTextRow("prefs.loc.sync_url", "Automatically sync the DICOM Nodes list from this URL:",
                "locations.nodesSyncUrl", "http://list.dicom.dcm/DICOMNodes.plist"));
        nodes.add(ck("locations.imageLevelRetrieval", false, LanguageManager.get("prefs.loc.image_level", "For C-GET and C-MOVE, try to retrieve images, at IMAGE level")));
        nodes.add(ck("locations.restartAutoQuery", false, LanguageManager.get("prefs.loc.restart", "Restart DICOM Auto Query & Retrieve settings, at launch")));
        nodes.add(ck("locations.searchBonjourNodes", true, LanguageManager.get("prefs.loc.bonjour", "Search for other DICOM Nodes through Bonjour protocol")));
        nodes.add(ck("locations.cfindStatusComments", true, LanguageManager.get("prefs.loc.cfind", "For C-FIND, support status and comments fields")));
        nodes.add(makeRow(new JLabel(LanguageManager.get("prefs.loc.encoding", "Text encoding:")),
                preferenceCombo("locations.textEncoding", 0, new String[]{"Western European: ISO_IR 100", "Japanese: ISO_IR 192", "Default"})));
        locationsPane.add(nodes);

        JPanel networkDb = preferenceSection(LanguageManager.get("prefs.loc.net_db_title", "Network - Horos Databases with Fixed Addresses (tcp/ip port 8780):"));
        DefaultTableModel networkModel = new DefaultTableModel(new String[]{"Address", LanguageManager.get("prefs.loc.description", "Description")}, 0);
        JTable networkTable = new JTable(networkModel);
        networkDb.add(new JScrollPane(networkTable));
        JPanel netControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton addServer = new JButton(LanguageManager.get("prefs.loc.add_server", "Add new server"));
        addServer.addActionListener(e -> {
            JTextField address = new JTextField(18), description = new JTextField(16);
            JPanel fields = new JPanel(new GridLayout(2, 2, 4, 4));
            fields.add(new JLabel("Address")); fields.add(address); fields.add(new JLabel("Description")); fields.add(description);
            if (JOptionPane.showConfirmDialog(this, fields, "Add server", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                networkModel.addRow(new Object[]{address.getText(), description.getText()});
        });
        JButton removeServer = new JButton(LanguageManager.get("prefs.node_delete", "Delete"));
        removeServer.addActionListener(e -> { int r = networkTable.getSelectedRow(); if (r >= 0) networkModel.removeRow(r); });
        netControls.add(addServer); netControls.add(removeServer);
        networkDb.add(netControls);
        networkDb.add(preferenceTextRow("prefs.loc.net_db_sync", "Automatically sync this list from this URL:",
                "locations.databaseSyncUrl", "http://list.dicom.dcm/OSirXDB.plist"));
        networkDb.add(ck("locations.disableBonjourDatabaseSearch", false,
                LanguageManager.get("prefs.loc.net_bonjour_off", "Turn Off Bonjour searching protocol - Do not search for Bonjour Horos Databases")));
        locationsPane.add(networkDb);

        JPanel localDb = preferenceSection(LanguageManager.get("prefs.loc.local_db_title", "Local - Horos Databases files / folder:"));
        DefaultTableModel localModel = new DefaultTableModel(new String[]{LanguageManager.get("prefs.loc.local_path", "Local Path"),
                LanguageManager.get("prefs.loc.description", "Description")}, 0);
        JTable localTable = new JTable(localModel);
        localDb.add(new JScrollPane(localTable));
        JButton addLocal = new JButton(LanguageManager.get("prefs.loc.add_local", "Add a local path"));
        addLocal.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                localModel.addRow(new Object[]{chooser.getSelectedFile().getAbsolutePath(), chooser.getSelectedFile().getName()});
        });
        localDb.add(addLocal);
        locationsPane.add(localDb);

        contentPanel.add(new JScrollPane(locationsPane), LanguageManager.get("prefs.nav.locations", "Locations"));
    }

    private void createRoutingPane() {
        JPanel pane = new JPanel(new BorderLayout(6, 6));
        pane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.add(ck("routing.active", false, LanguageManager.get("prefs.route.activate", "Activate AutoRouting")));
        options.add(ck("routing.showError", true, LanguageManager.get("prefs.route.error", "Display an error if an autorouting rule failed")));
        pane.add(options, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(new JLabel(LanguageManager.get("prefs.route.explain", "Horos will automatically send data added in the database according to the following routes:")), BorderLayout.NORTH);
        String[] columns = {LanguageManager.get("prefs.route.activated", "Activated"), LanguageManager.get("prefs.route.routes", "Routes"),
                LanguageManager.get("prefs.route.destination", "Destination"), LanguageManager.get("prefs.route.comments", "Comments")};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int col) { return col == 0 ? Boolean.class : String.class; }
        };
        String saved = AppPreferences.getStr("routing.rules", "");
        if (!saved.isBlank()) {
            try {
                String[][] rows = new com.fasterxml.jackson.databind.ObjectMapper().readValue(saved, String[][].class);
                for (String[] row : rows) if (row.length == 4)
                    model.addRow(new Object[]{Boolean.parseBoolean(row[0]), row[1], row[2], row[3]});
            } catch (Exception ignored) { }
        }
        JTable table = new JTable(model);
        Runnable persist = () -> {
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            for (int r = 0; r < model.getRowCount(); r++) rows.add(new String[]{
                    String.valueOf(model.getValueAt(r, 0)), String.valueOf(model.getValueAt(r, 1)),
                    String.valueOf(model.getValueAt(r, 2)), String.valueOf(model.getValueAt(r, 3))});
            try { AppPreferences.setStr("routing.rules", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rows)); }
            catch (Exception ignored) { }
        };
        model.addTableModelListener(e -> persist.run());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) editRoutingRow(table, model, table.convertRowIndexToModel(table.getSelectedRow()));
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("DELETE"), "deleteRoute");
        table.getActionMap().put("deleteRoute", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int row = table.getSelectedRow(); if (row >= 0) model.removeRow(table.convertRowIndexToModel(row));
            }
        });
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        JPanel hints = new JPanel(); hints.setLayout(new BoxLayout(hints, BoxLayout.Y_AXIS));
        hints.add(new JLabel(LanguageManager.get("prefs.route.edit_hint", "Double-Click on a route to edit filter parameters")));
        hints.add(new JLabel(LanguageManager.get("prefs.route.delete_hint", "Press Delete key to delete a route")));
        hints.add(new JLabel(LanguageManager.get("prefs.route.filter_help", "Filter: * or key=value; supported keys: modality, patientID, studyDescription, seriesDescription, accessionNumber, studyUID")));
        JButton addRoute = new JButton(LanguageManager.get("prefs.route.add", "Add new route"));
        addRoute.addActionListener(e -> {
            String route = JOptionPane.showInputDialog(this, LanguageManager.get("prefs.route.filter_help", "Filter: * or key=value; supported keys: modality, patientID, studyDescription, seriesDescription, accessionNumber, studyUID"));
            if (route == null || route.isBlank()) return;
            String destination = JOptionPane.showInputDialog(this, LanguageManager.get("prefs.route.destination", "Destination"));
            if (destination != null && !destination.isBlank()) model.addRow(new Object[]{true, route.trim(), destination.trim(), ""});
        });
        footer.add(hints, BorderLayout.WEST); footer.add(addRoute, BorderLayout.EAST);
        center.add(footer, BorderLayout.SOUTH);
        pane.add(center, BorderLayout.CENTER);
        contentPanel.add(pane, LanguageManager.get("prefs.nav.routing", "Routing"));
    }

    private void editRoutingRow(JTable table, DefaultTableModel model, int row) {
        JTextField route = new JTextField(String.valueOf(model.getValueAt(row, 1)), 24);
        JTextField destination = new JTextField(String.valueOf(model.getValueAt(row, 2)), 20);
        JTextField comments = new JTextField(String.valueOf(model.getValueAt(row, 3)), 20);
        JPanel fields = new JPanel(new GridLayout(3, 2, 4, 4));
        fields.add(new JLabel(LanguageManager.get("prefs.route.filter_help", "Routes / Filter"))); fields.add(route);
        fields.add(new JLabel(LanguageManager.get("prefs.route.destination", "Destination"))); fields.add(destination);
        fields.add(new JLabel(LanguageManager.get("prefs.route.comments", "Comments"))); fields.add(comments);
        if (JOptionPane.showConfirmDialog(this, fields, "Edit route", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            model.setValueAt(route.getText(), row, 1); model.setValueAt(destination.getText(), row, 2); model.setValueAt(comments.getText(), row, 3);
        }
    }

    private void createWebServerPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JCheckBox webServerActive = new JCheckBox(LanguageManager.get("prefs.web.activate", "Activate the built-in Web Server"),
                AppPreferences.getBool("webserver.active", false));
        webServerActive.addActionListener(e -> setWebServerActive(webServerActive.isSelected(), webServerActive));
        pane.add(webServerActive);
        JPanel address = preferenceSection("Web Server");
        address.add(makeRow(new JLabel(LanguageManager.get("prefs.web.public", "Public address:")), preferenceText("webserver.publicAddress", "", 25)));
        JTextField port = new JTextField(String.valueOf(AppPreferences.getWebServerPort()), 6);
        port.addActionListener(e -> saveIntField(port, "webserver.port", 3333, 1, 65535));
        port.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                saveIntField(port, "webserver.port", 3333, 1, 65535);
                if (webServerActive.isSelected()) setWebServerActive(true, webServerActive);
            }
        });
        address.add(makeRow(new JLabel(LanguageManager.get("prefs.web.local_port", "Local port:")), port));
        JPanel https = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        https.add(ck("webserver.httpsEnabled", false, LanguageManager.get("prefs.web.https", "Secure (HTTPS) connections with certificate:")));
        JButton cert = new JButton(LanguageManager.get("prefs.web.choose", "Choose..."));
        cert.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                AppPreferences.setStr("webserver.certificatePath", chooser.getSelectedFile().getAbsolutePath());
        });
        https.add(cert); address.add(https);
        pane.add(address);

        JPanel options = preferenceSection(LanguageManager.get("prefs.web.options", "Options"));
        options.add(ck("webserver.wadoEnabled", true, LanguageManager.get("prefs.wado.enable", "Activate WADO Server at /wado")));
        options.add(ck("webserver.wadoIdentifiedOnly", true, LanguageManager.get("prefs.web.wado_only", "Only accept WADO request from identified user")));
        options.add(ck("webserver.authorizeWeasisHoros", true, LanguageManager.get("prefs.web.authorize_viewers", "Authorize Weasis & Horos as web viewer")));
        options.add(ck("webserver.preferFlash", true, LanguageManager.get("prefs.web.prefer_flash", "Prefer Flash over QuickTime movies for series viewing")));
        options.add(makeRow(new JLabel(LanguageManager.get("prefs.web.max_frames", "Maximum number of frames for movies:")), preferenceText("webserver.maxMovieFrames", "400", 6)));
        options.add(makeRow(new JLabel(LanguageManager.get("prefs.web.min_width", "Minimum width for movies / still images:")), preferenceText("webserver.minWidth", "512", 6)));
        options.add(makeRow(new JLabel(LanguageManager.get("prefs.web.max_movie_width", "Maximum width for movies:")), preferenceText("webserver.maxMovieWidth", "880", 6)));
        options.add(makeRow(new JLabel(LanguageManager.get("prefs.web.max_still_width", "Maximum width for still images:")), preferenceText("webserver.maxStillWidth", "880", 6)));
        options.add(ck("webserver.generateLogEvents", false, LanguageManager.get("prefs.web.generate_logs", "Generate Log Events (see network menu)")));
        JPanel templateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        templateRow.add(ck("webserver.useTemplate", false, LanguageManager.get("prefs.web.template", "Use templates at:")));
        templateRow.add(preferenceText("webserver.templatePath", "", 30));
        JButton createTemplates = new JButton(LanguageManager.get("prefs.web.create_templates", "Create missing files"));
        createTemplates.addActionListener(e -> JOptionPane.showMessageDialog(this, "Template directory: " + AppPreferences.getStr("webserver.templatePath", "")));
        templateRow.add(createTemplates); options.add(templateRow);
        JPanel auth = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        auth.add(ck("webserver.requireAuth", false, LanguageManager.get("prefs.web.auth", "Require user authentication")));
        JButton editUsers = new JButton(LanguageManager.get("prefs.web.edit_users", "Edit users"));
        editUsers.addActionListener(e -> JOptionPane.showMessageDialog(this, "User management is not configured yet."));
        auth.add(editUsers); auth.add(preferenceText("webserver.authTimeoutMinutes", "30", 5));
        auth.add(new JLabel(LanguageManager.get("prefs.web.timeout", "minutes")));
        options.add(auth);
        options.add(ck("webserver.adminCanDelete", false, LanguageManager.get("prefs.web.admin_delete", "Admin users can delete studies or series")));
        options.add(ck("webserver.passwordResetByEmail", true, LanguageManager.get("prefs.web.restore_password", "Authorize users to restore password by email")));
        JPanel duration = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        duration.add(new JLabel(LanguageManager.get("prefs.web.temp_duration", "Duration for temporary user:")));
        duration.add(preferenceText("webserver.temporaryUserDays", "60", 5));
        duration.add(new JLabel(LanguageManager.get("prefs.web.days", "days"))); options.add(duration);
        pane.add(options);

        JPanel emails = preferenceSection(LanguageManager.get("prefs.web.emails", "Emails"));
        ButtonGroup emailGroup = new ButtonGroup();
        JRadioButton mailApp = new JRadioButton(LanguageManager.get("prefs.web.mail_app", "Mail.app"), AppPreferences.getInt("webserver.emailEngine", 0) == 0);
        JRadioButton smtp = new JRadioButton(LanguageManager.get("prefs.web.smtp", "Built-in SMTP"), AppPreferences.getInt("webserver.emailEngine", 0) == 1);
        mailApp.addActionListener(e -> AppPreferences.setInt("webserver.emailEngine", 0));
        smtp.addActionListener(e -> AppPreferences.setInt("webserver.emailEngine", 1));
        emailGroup.add(mailApp); emailGroup.add(smtp);
        emails.add(makeRow(new JLabel(LanguageManager.get("prefs.web.email_engine", "Email Engine:")), mailApp));
        emails.add(smtp);
        emails.add(makeRow(new JLabel(LanguageManager.get("prefs.web.email_address", "Email Address:")), preferenceText("webserver.emailAddress", "", 28)));
        JPanel notify = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        notify.add(ck("webserver.emailNotifications", false, LanguageManager.get("prefs.web.auto_notify", "Issue automatic notifications when adding studies, send them every:")));
        notify.add(preferenceText("webserver.notificationInterval", "1", 5));
        notify.add(new JLabel(LanguageManager.get("prefs.web.user", "minutes"))); emails.add(notify);
        pane.add(emails);
        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.webserver", "Web Server"));
    }

    private void setWebServerActive(boolean active, JCheckBox control) {
        AppPreferences.setBool("webserver.active", active);
        try {
            com.oculus.jj.model.WebServerService service = com.oculus.jj.model.WebServerService.getInstance();
            service.stop();
            if (active) service.start();
        } catch (Exception ex) {
            AppPreferences.setBool("webserver.active", false);
            control.setSelected(false);
            JOptionPane.showMessageDialog(this, ex.getMessage(), LanguageManager.get("prefs.error", "Error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createOnDemandPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        pane.add(ck("ondemand.active", false, LanguageManager.get("prefs.ond.activate", "Activate PACS On Demand")));
        JPanel nodes = preferenceSection(LanguageManager.get("prefs.ond.nodes", "DICOM Nodes for PACS On Demand matching and retrieving:"));
        nodes.add(new JLabel(LanguageManager.get("prefs.ond.locations_hint", "See Locations Preferences for editing these DICOM Node")));
        nodes.add(new JLabel(LanguageManager.get("prefs.ond.priority_hint", "Drag sources into the priority order for retrieving")));
        DefaultTableModel nodeModel = new DefaultTableModel(new String[]{"Use", "Name", "AETitle", "Address:Port"}, 0) {
            @Override public Class<?> getColumnClass(int col) { return col == 0 ? Boolean.class : String.class; }
        };
        for (DicomNode node : DicomNodeManager.getInstance().getNodes())
            nodeModel.addRow(new Object[]{true, node.getName(), node.getAeTitle(), node.getAddress() + ":" + node.getPort()});
        JTable nodeTable = new JTable(nodeModel);
        nodeTable.setRowHeight(21);
        JScrollPane nodeScroll = new JScrollPane(nodeTable); nodeScroll.setPreferredSize(new Dimension(700, 100));
        nodes.add(nodeScroll);
        JPanel orderButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        JButton moveUp = new JButton("↑"), moveDown = new JButton("↓");
        moveUp.addActionListener(e -> moveTableRow(nodeModel, nodeTable.getSelectedRow(), -1));
        moveDown.addActionListener(e -> moveTableRow(nodeModel, nodeTable.getSelectedRow(), 1));
        orderButtons.add(moveUp); orderButtons.add(moveDown); nodes.add(orderButtons);
        pane.add(nodes);
        pane.add(ck("ondemand.distantStudyIfMoreImages", true, LanguageManager.get("prefs.ond.distant", "If a distant study has more images than a local study, display the distant study")));
        pane.add(ck("ondemand.autoRetrievePartialLocal", true, LanguageManager.get("prefs.ond.auto_retrieve", "Automatically retrieve studies that have already some local images")));
        pane.add(ck("ondemand.useToolbarSearch", true, LanguageManager.get("prefs.ond.toolbar", "Use PACS On Demand, when using the toolbar search fields")));

        JPanel smart = preferenceSection(LanguageManager.get("prefs.ond.smart_search", "Activate search for following Smart Albums, with Q&R Date & Modality filters:"));
        DefaultTableModel smartModel = new DefaultTableModel(new String[]{LanguageManager.get("prefs.ond.enabled", ""),
                LanguageManager.get("prefs.ond.smart_album", "Smart Album Name"),
                LanguageManager.get("prefs.ond.auto_retrieve_col", "Auto-retrieve"), LanguageManager.get("prefs.ond.date", "Date"),
                LanguageManager.get("prefs.ond.modality", "Modality")}, 0) {
            @Override public Class<?> getColumnClass(int col) { return col == 0 || col == 2 ? Boolean.class : String.class; }
        };
        String[][] defaults = {{"true", "Just Acquired (last hour)", "false", "Last hour", ""},
                {"false", "Just Added (last hour)", "false", "Any date", ""},
                {"false", "Just Opened", "false", "Any date", ""}, {"true", "Today CR", "true", "Today", "CR"},
                {"true", "Today CT", "true", "Today", "CT"}, {"true", "Today MG", "true", "Today", "MG"}};
        String savedSmart = AppPreferences.getStr("ondemand.smartAlbums", "");
        try {
            String[][] rows = savedSmart.isBlank() ? defaults : new com.fasterxml.jackson.databind.ObjectMapper().readValue(savedSmart, String[][].class);
            for (String[] row : rows) {
                if (row.length == 5) {
                    smartModel.addRow(new Object[]{Boolean.parseBoolean(row[0]), row[1], Boolean.parseBoolean(row[2]), row[3], row[4]});
                } else if (row.length == 4) {
                    boolean enabled = row[0].startsWith("Today") || row[0].startsWith("Just Acquired");
                    smartModel.addRow(new Object[]{enabled, row[0], Boolean.parseBoolean(row[1]), row[2], row[3]});
                }
            }
        } catch (Exception ignored) { }
        JTable smartTable = new JTable(smartModel);
        smartTable.setRowHeight(21);
        smartTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"Last hour", "Any date", "Today", "Yesterday", "Last week"})));
        smartTable.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"", "CR", "CT", "MG", "MR", "PT", "US"})));
        smartModel.addTableModelListener(e -> {
            java.util.List<String[]> rows = new java.util.ArrayList<>();
            for (int r = 0; r < smartModel.getRowCount(); r++) rows.add(new String[]{String.valueOf(smartModel.getValueAt(r, 0)),
                    String.valueOf(smartModel.getValueAt(r, 1)), String.valueOf(smartModel.getValueAt(r, 2)),
                    String.valueOf(smartModel.getValueAt(r, 3)), String.valueOf(smartModel.getValueAt(r, 4))});
            try { AppPreferences.setStr("ondemand.smartAlbums", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rows)); }
            catch (Exception ignored) { }
        });
        JScrollPane smartScroll = new JScrollPane(smartTable); smartScroll.setPreferredSize(new Dimension(700, 150));
        smart.add(ck("ondemand.smartAlbumSearchEnabled", false, LanguageManager.get("prefs.ond.smart_search", "Activate search for following Smart Albums")));
        smart.add(smartScroll);
        smart.add(new JLabel(LanguageManager.get("prefs.ond.edit_hint", "Double-Click on a line to edit the Date & Modality Filters.")));
        pane.add(smart);
        JPanel maxRequests = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        maxRequests.add(new JLabel(LanguageManager.get("prefs.ond.max_requests", "Maximum number of background auto-retrieve requests per server:")));
        maxRequests.add(preferenceSpinner("ondemand.maxBackgroundRequests", 2, 1, 6));
        pane.add(maxRequests);

        JPanel webFunctions = preferenceSection(LanguageManager.get("prefs.ond.web_functions", "Activate PACS On Demand for following Web Server functions"));
        webFunctions.add(ck("ondemand.webComparativeStudies", false, LanguageManager.get("prefs.ond.comparative", "Comparative Studies, displayed at Study level")));
        webFunctions.add(ck("ondemand.webSearch", false, LanguageManager.get("prefs.ond.search", "Search function")));
        webFunctions.add(ck("ondemand.webAlbums", false, LanguageManager.get("prefs.ond.albums", "Albums")));
        pane.add(webFunctions);
        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.ondemand", "On-Demand (PACS)"));
    }

    private void moveTableRow(DefaultTableModel model, int row, int direction) {
        int target = row + direction;
        if (row < 0 || target < 0 || target >= model.getRowCount()) return;
        Object[] values = new Object[model.getColumnCount()];
        for (int c = 0; c < model.getColumnCount(); c++) values[c] = model.getValueAt(row, c);
        model.removeRow(row); model.insertRow(target, values);
    }

    // === HELPERS ===
    private JPanel makeRow(Component label, Component control) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.add(label); if(control != null) p.add(control);
        return p;
    }

    private void showNodeEditor(DicomNode node, Runnable onSave) {
        JDialog dialog = new JDialog(this, LanguageManager.get("prefs.dicom_node.title", "Add DICOM Node"), true);
        dialog.setSize(380, 260);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridLayout(6, 2, 5, 5));
        boolean isNew = (node.getName() == null || node.getName().isEmpty());

        JTextField nameF = new JTextField(node.getName()!=null?node.getName():"");
        JTextField aetF = new JTextField(node.getAeTitle()!=null?node.getAeTitle():"");
        JTextField addrF = new JTextField(node.getAddress()!=null?node.getAddress():"");
        JTextField portF = new JTextField(String.valueOf(node.getPort()>0?node.getPort():11112));

        dialog.add(new JLabel(LanguageManager.get("prefs.node.name", "Name:"))); dialog.add(nameF);
        dialog.add(new JLabel(LanguageManager.get("prefs.ae_title", "AETitle:"))); dialog.add(aetF);
        dialog.add(new JLabel(LanguageManager.get("prefs.node.address", "Address:"))); dialog.add(addrF);
        dialog.add(new JLabel(LanguageManager.get("prefs.node.port", "Port:"))); dialog.add(portF);

        JButton saveBtn = new JButton(isNew?LanguageManager.get("prefs.node_add", "Add"):LanguageManager.get("prefs.node_save", "Save"));
        saveBtn.addActionListener(e -> {
            try { node.setName(nameF.getText()); node.setAeTitle(aetF.getText());
                node.setAddress(addrF.getText()); node.setPort(Integer.parseInt(portF.getText()));
                if(isNew) DicomNodeManager.getInstance().addNode(node); else DicomNodeManager.getInstance().saveNodes();
                onSave.run(); dialog.dispose();
            } catch(Exception ex) { JOptionPane.showMessageDialog(dialog,LanguageManager.get("prefs.invalid_port", "Invalid port number.")); }
        });
        dialog.add(saveBtn);
        JButton cancelBtn = new JButton(LanguageManager.get("prefs.cancel", "Cancel"));
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.add(cancelBtn);
        dialog.setVisible(true);
    }

    private static class JSectionBox extends JPanel {
        public JSectionBox(String title) {
            setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }
    }
}

