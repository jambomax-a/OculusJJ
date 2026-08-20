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
        setTitle(LanguageManager.get("prefs.title", "OculusJJ - Preferences"));
        setSize(850, 560);
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
        JButton saveBtn = new JButton(LanguageManager.get("prefs.apply", "Apply"));
        saveBtn.addActionListener(e -> {
            String selectedLang = langCombo.getSelectedItem().toString();
            AppPreferences.setAppLanguage(selectedLang.contains("ja") ? "ja" : "en");
            AppPreferences.saveAll();
            java.util.Locale locale = AppPreferences.getAppLanguage().equals("ja") ? new java.util.Locale("ja") : java.util.Locale.ENGLISH;
            com.oculus.jj.ui.MenuResourceLoader.setLocale(locale);
            com.oculus.jj.utils.LanguageManager.getInstance().loadLanguage(selectedLang.contains("ja") ? "ja" : "en");
        });
        JButton closeBtn = new JButton(LanguageManager.get("prefs.close", "Close"));
        closeBtn.addActionListener(e -> setVisible(false));
        bottomPanel.add(saveBtn);
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);
        pack();
    }

    // === BASIC PANES ===

    private void createGeneralPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JSectionBox ifaceBox = new JSectionBox(LanguageManager.get("prefs.section.interface", "Interface"));
        String[] langs = {"English (en)", "Japanese (ja)"};
        langCombo = new JComboBox<>(langs);
        langCombo.setSelectedItem(com.oculus.jj.config.AppPreferences.getAppLanguage().equals("ja") ? "Japanese (ja)" : "English (en)");
        String[] displayTypes = {LanguageManager.get("prefs.display.original", "Original Pixel Data"), LanguageManager.get("prefs.display.grayscale", "Gray Scale"), LanguageManager.get("prefs.display.displayed", "Displayed Pixel Data")};
        JComboBox<String> displayTypeCombo = new JComboBox<>(displayTypes);

        pane.add(ifaceBox);
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.language", "Language:")), langCombo));
        pane.add(Box.createVerticalStrut(6));
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.image_display_type", "Image Display Type:")), displayTypeCombo));
        pane.add(Box.createVerticalStrut(10));

        JSectionBox autoBox = new JSectionBox(LanguageManager.get("prefs.section.studies", "Studies"));
        JCheckBox chkSeparateWin = new JCheckBox(LanguageManager.get("prefs.separate_window", "Open each selected study in a separate window"), true);
        pane.add(autoBox);
        pane.add(chkSeparateWin);
        pane.add(Box.createVerticalStrut(4));
        JCheckBox chkCheckUpdates = new JCheckBox(LanguageManager.get("prefs.check_updates", "Automatically check for application updates at startup"), false);
        pane.add(chkCheckUpdates);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.general", "General"));
    }

    private void createDatabasePane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        
        gbc.gridx = 0; gbc.gridy = row;
        pane.add(new JLabel(LanguageManager.get("prefs.database_folder", "Database Folder:")), gbc);
        gbc.gridx = 1;
        JTextField dbPathField = new JTextField(AppPreferences.getDatabaseFolder(), 25);
        dbPathField.setEditable(false);
        pane.add(dbPathField, gbc);
        gbc.gridx = 2;
        JButton browseDbBtn = new JButton(LanguageManager.get("prefs.browse", "Browse..."));
        browseDbBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(AppPreferences.getDatabaseFolder());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {
                AppPreferences.setDatabaseFolder(chooser.getSelectedFile().getPath());
                dbPathField.setText(AppPreferences.getDatabaseFolder());
            }
        });
        pane.add(browseDbBtn, gbc);

        row++; gbc.gridy = row; gbc.gridx = 0;
        pane.add(new JLabel(LanguageManager.get("prefs.dicom_storage_path", "DICOM Files Storage Path:")), gbc);
        gbc.gridx = 1;
        JTextField dicomPathField = new JTextField(AppPreferences.getDicomFolder(), 25);
        dicomPathField.setEditable(false);
        pane.add(dicomPathField, gbc);
        gbc.gridx = 2;
        JButton browseDicomBtn = new JButton(LanguageManager.get("prefs.browse", "Browse..."));
        browseDicomBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(AppPreferences.getDicomFolder());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(PreferencesController.this) == JFileChooser.APPROVE_OPTION) {
                AppPreferences.setDicomFolder(chooser.getSelectedFile().getPath());
                dicomPathField.setText(AppPreferences.getDicomFolder());
            }
        });
        pane.add(browseDicomBtn, gbc);

        row++; gbc.gridy = row; gbc.gridx = 0; gbc.gridwidth = 3;
        JCheckBox chkAutoRefresh = new JCheckBox(LanguageManager.get("prefs.auto_refresh_db", "Automatically refresh database on startup"), true);
        pane.add(chkAutoRefresh, gbc);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.database", "Database"));
    }

    private void createCDpvPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JSectionBox readBox = new JSectionBox(LanguageManager.get("prefs.section.read_cd", "Reading CD/DVDs"));
        JCheckBox chkEject = new JCheckBox(LanguageManager.get("prefs.eject_disk", "Eject the disk when done copying"), false);
        JCheckBox chkAutoSelect = new JCheckBox(LanguageManager.get("prefs.auto_select_source", "Automatically select the source when ready"), true);
        JCheckBox chkAutoImport = new JCheckBox(LanguageManager.get("prefs.auto_import_cd", "Automatically import from inserted CDs/DVDs"), false);
        
        pane.add(readBox);
        pane.add(chkEject);
        pane.add(Box.createVerticalStrut(4));
        pane.add(chkAutoSelect);
        pane.add(Box.createVerticalStrut(4));
        pane.add(chkAutoImport);
        pane.add(Box.createVerticalStrut(10));

        JSectionBox writeBox = new JSectionBox(LanguageManager.get("prefs.section.write_cd", "Writing CD/DVDs"));
        JCheckBox chkStdJPEG = new JCheckBox(LanguageManager.get("prefs.prefer_std_jpeg", "Prefer standard JPEG instead of JPEG2000/JPEG-LS"), true);
        pane.add(writeBox);
        pane.add(chkStdJPEG);
        pane.add(Box.createVerticalStrut(4));

        JPanel suppPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox chkSuppFolder = new JCheckBox(LanguageManager.get("prefs.include_folder", "Include contents of this folder:"));
        JTextField suppField = new JTextField(25);
        JButton browseBurnBtn = new JButton(LanguageManager.get("prefs.browse", "Browse..."));
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

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.burning", "CD/DVD Burning"));
    }

    private void createHangingProtocolsPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JSectionBox protocolBox = new JSectionBox(LanguageManager.get("prefs.section.hanging", "Default Hanging Protocol"));
        String[] modalities = {"CR", "CT", "DX", "ES", "MG", "MR", "NM", "OT", "PT", "RF", "SC", "US", "XA"};
        JComboBox<String> modalityCombo = new JComboBox<>(modalities);
        modalityCombo.setSelectedIndex(1);
        String[] tilingOptions = {"1x1","2x1","1x2","2x2","3x1","1x3","2x3","3x2","4x1","1x4","3x3","2x4","4x2","4x3","5x1","1x5"};
        JComboBox<String> tilingCombo = new JComboBox<>(tilingOptions);
        tilingCombo.setSelectedIndex(3);

        pane.add(protocolBox);
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.modality", "Modality:")), modalityCombo));
        pane.add(Box.createVerticalStrut(6));
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.image_tiling", "Image Tiling:")), tilingCombo));
        pane.add(Box.createVerticalStrut(10));

        JSectionBox windowBox = new JSectionBox(LanguageManager.get("prefs.section.wlww", "Window Level / Width Presets"));
        String[] wlwwPresets = {"Default","Bone","Lung","Liver","Brain","Soft Tissue","Mediastinum"};
        pane.add(windowBox);
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.wlww_preset", "Default WL/WW Preset:")), new JComboBox<>(wlwwPresets)));

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
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST; int row = 0;

        String[] cluts = {"CT-Body","CT-Bone","CT-Lung","CT-Brain","Gray Scale","PET-Rainbow","PET-HotMetal"};
        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.default_clut", "Default CLUT:")), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(cluts), gbc);

        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.default_opacity_table", "Default Opacity Table:")), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(new String[]{"Standard","PET Specific","NM Specific"}), gbc);

        row++; gbc.gridy = row; JCheckBox chkSync = new JCheckBox(LanguageManager.get("prefs.sync_series", "Synchronize series between studies"));
        gbc.gridwidth = 2; pane.add(chkSync, gbc);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.viewers", "Viewers"));
    }

    private void create3DPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JSectionBox renderBox = new JSectionBox(LanguageManager.get("prefs.section.rendering", "Rendering"));
        double res = AppPreferences.get3DResolution();
        int resIdx = (res < 0.3) ? 0 : (res < 0.6) ? 1 : 2;
        pane.add(renderBox);
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.rendering_resolution", "Rendering Resolution:")), 
            new JComboBox<>(new String[]{LanguageManager.get("prefs.res.good", "Good"), LanguageManager.get("prefs.res.better", "Better"), LanguageManager.get("prefs.res.best", "Best")})));
        JPanel resPanel = makeRow(new JLabel(""), null);
        JSlider resSlider = new JSlider(1, 10, (int)(res * 10));
        resSlider.setMajorTickSpacing(1); resSlider.setPaintLabels(true); resSlider.setPaintTicks(true);
        resSlider.addChangeListener(e -> AppPreferences.set3DResolution(resSlider.getValue() / 10.0));
        pane.add(resPanel);
        pane.add(Box.createVerticalStrut(6));
        JCheckBox chkShading = new JCheckBox(LanguageManager.get("prefs.enable_shading", "Enable Shading by default"), AppPreferences.is3DShadingEnabled());
        chkShading.addActionListener(e -> AppPreferences.set3DShadingEnabled(chkShading.isSelected()));
        pane.add(chkShading);
        pane.add(Box.createVerticalStrut(6));
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.shading_algorithm", "Shading Algorithm:")), new JComboBox<>(new String[]{"Phong","Gouraud","Metallic"})));
        pane.add(Box.createVerticalStrut(10));

        JSectionBox roiBox = new JSectionBox(LanguageManager.get("prefs.section.roi3d", "3D ROI Display"));
        int rgb = AppPreferences.getROIColor().getRGB();
        String[] roiColors = {"Yellow","Red","Green","Blue","Cyan","Magenta"};
        JComboBox<String> roiColorCombo = new JComboBox<>(roiColors);
        if (rgb == Color.YELLOW.getRGB()) roiColorCombo.setSelectedIndex(0);
        else if (rgb == Color.RED.getRGB()) roiColorCombo.setSelectedIndex(1);

        pane.add(roiBox);
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.default_roi_color", "Default ROI Color:")), roiColorCombo));
        pane.add(Box.createVerticalStrut(4));
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.roi_3d_algorithm", "Algorithm for 3D ROIs:")), new JComboBox<>(new String[]{"Iso Contour","Delaunay"})));
        pane.add(Box.createVerticalStrut(6));
        JCheckBox chkSpline = new JCheckBox(LanguageManager.get("prefs.spline_scissors", "Use Spline interpolation for Scissors ROI"), false);
        pane.add(chkSpline);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.viewer3d", "3D Viewer"));
    }

    private void createPETPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JSectionBox clutBox = new JSectionBox(LanguageManager.get("prefs.section.clut", "CLUT (Color Look-Up Tables)"));
        String[] petCluts = {"CT-Body","CT-Bone","CT-Lung","CT-Brain","PET-Rainbow","PET-HotMetal","PET-Heat"};
        pane.add(clutBox);
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.default_pet_clut", "Default PET CLUT:")), new JComboBox<>(petCluts)));
        pane.add(Box.createVerticalStrut(6));
        JRadioButton useBW = new JRadioButton(LanguageManager.get("prefs.bw_inverse_clut", "\"B/W Inverse\" CLUT for blending"), false);
        JRadioButton useModality = new JRadioButton(LanguageManager.get("prefs.prefer_modality_clut", "Prefer modality-specific CLUT with this CLUT"), true);
        ButtonGroup clutGroup = new ButtonGroup(); clutGroup.add(useBW); clutGroup.add(useModality);
        pane.add(useBW); pane.add(useModality);
        pane.add(Box.createVerticalStrut(10));

        JSectionBox opacityBox = new JSectionBox(LanguageManager.get("prefs.section.tf", "Opacity Table (Transfer Function)"));
        pane.add(opacityBox);
        pane.add(makeRow(new JLabel(LanguageManager.get("prefs.default_tf", "Default Transfer Function:")), new JComboBox<>(new String[]{"Standard","PET Specific","NM Specific","Custom"})));
        pane.add(Box.createVerticalStrut(6));
        pane.add(new JCheckBox(LanguageManager.get("prefs.apply_to_nm", "Apply this behavior also to NM modality")));

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.petsuv", "PET/SUV"));
    }

    private void createAnnotationsPane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL; int row = 0;
        String[] styles = {"None","Text Box","Arrow","Ellipse","Crosshair"};
        String[] colors = {"White","Black","Red","Green","Yellow"};
        String[] fonts = {"Arial","Helvetica","Courier","Times New Roman"};

        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.annotation.default_style", "Default Style:")), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(styles), gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.annotation.default_color", "Default Color:")), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(colors), gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.annotation.default_font", "Default Font:")), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(fonts), gbc);
        row++; gbc.gridy = row; JCheckBox chkModality = new JCheckBox(LanguageManager.get("prefs.annotation.modality_defaults", "Allow modality-specific annotation defaults"));
        gbc.gridwidth = 2; pane.add(chkModality, gbc);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.annotations", "Annotations"));
    }

    private void createDICOMPrintPane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.WEST; int row = 0;
        
        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.default_printer", "Default Printer:")), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(new String[]{"Default Printer","Printer A (Laser)","Printer B (Inkjet)"}), gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.image_layout", "Image Layout:")), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(new String[]{"Standard 1,1","Standard 2,1","Standard 1,2","Standard 2,2","Standard 3,3"}), gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.film_orientation", "Film Orientation:")), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(new String[]{LanguageManager.get("prefs.orientation.landscape", "Landscape"), LanguageManager.get("prefs.orientation.portrait", "Portrait")}), gbc);
        row++; gbc.gridy = row; JCheckBox chkResize = new JCheckBox(LanguageManager.get("prefs.auto_resize_print", "Auto-resize window to square size before printing"), true);
        gbc.gridwidth = 2; pane.add(chkResize, gbc);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.print", "DICOM Print"));
    }

    // === SHARING PANES ===

    private void createListenerPane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.WEST; int row = 0;
        
        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.app_aetitle", "Application AETitle:")), gbc);
        gbc.gridx = 1; JTextField aetF = new JTextField("OCULUSJJ", 20); pane.add(aetF, gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.listen_port", "Listen on Port:")), gbc);
        gbc.gridx = 1; pane.add(new JTextField("11112", 8), gbc);
        row++; gbc.gridy = row; pane.add(new JCheckBox(LanguageManager.get("prefs.auto_start_listener", "Automatically start listener on launch")), gbc);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.listener", "Listener"));
    }

    private void createLocationsPane() {
        JPanel locationsPane = new JPanel(new BorderLayout());
        String[] cols = {LanguageManager.get("prefs.node.name", "Name"), "AETitle", LanguageManager.get("prefs.node.address", "Address"), LanguageManager.get("prefs.node.port", "Port")};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);
        model.addRow(new Object[]{"Localhost","OCULUSJJ","localhost","11112"});

        locationsPane.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        JButton addBtn = new JButton(LanguageManager.get("prefs.node_add", "Add"));
        addBtn.addActionListener(e -> {
            DicomNode node = new DicomNode();
            showNodeEditor(node, () -> 
                model.addRow(new Object[]{node.getName(), node.getAeTitle(), node.getAddress(), String.valueOf(node.getPort())}));
        });
        btnPanel.add(addBtn);
        JButton delBtn = new JButton(LanguageManager.get("prefs.node_delete", "Delete"));
        delBtn.addActionListener(e -> { int r = table.getSelectedRow(); if (r >= 0) model.removeRow(r); });
        btnPanel.add(delBtn);
        JButton verifyBtn = new JButton(LanguageManager.get("prefs.node_verify", "Verify"));
        verifyBtn.addActionListener(e -> {
            int r = table.getSelectedRow(); if (r < 0) return;
            String addr = (String)model.getValueAt(r,2);
            int port = Integer.parseInt((String)model.getValueAt(r,3));
            DicomNode node = new DicomNode();
            node.setAddress(addr); node.setPort(port);
            new Thread(() -> {
                try { boolean ok = DicomService.getInstance().echo(node);
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(PreferencesController.this, ok?LanguageManager.get("prefs.echo_ok", "Echo Success!"):LanguageManager.get("prefs.echo_failed", "Echo Failed")));
                } catch(Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(PreferencesController.this,LanguageManager.get("prefs.error", "Error:")+" "+ex.getMessage()));
                }
            }).start();
        });
        btnPanel.add(verifyBtn);
        locationsPane.add(btnPanel, BorderLayout.SOUTH);

        contentPanel.add(locationsPane, LanguageManager.get("prefs.nav.locations", "Locations"));
    }

    private void createRoutingPane() {
        JPanel pane = new JPanel(new BorderLayout());
        pane.add(new JLabel(LanguageManager.get("prefs.routing_title", "DICOM Autorouting Rules")), BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new String[]{LanguageManager.get("prefs.routing.rule", "Rule"), LanguageManager.get("prefs.routing.source", "Source AE"), LanguageManager.get("prefs.routing.destination", "Destination"), LanguageManager.get("prefs.routing.filter", "Filter")}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        model.addRow(new Object[]{"Default Store","*","OCULUSJJ","*"});
        pane.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        JPanel controls = new JPanel();
        controls.add(new JButton(LanguageManager.get("prefs.routing.add_rule", "Add Rule"))); controls.add(new JButton(LanguageManager.get("prefs.routing.remove_rule", "Remove Rule")));
        pane.add(controls, BorderLayout.SOUTH);
        contentPanel.add(pane, LanguageManager.get("prefs.nav.routing", "Routing"));
    }

    private void createWebServerPane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); gbc.anchor = GridBagConstraints.WEST; int row = 0;
        
        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.webserver_port", "Web Server Port:")), gbc); gbc.gridx = 1; pane.add(new JTextField("8080",5), gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.wado", "WADO Support:")), gbc); gbc.gridx = 1; pane.add(new JCheckBox(LanguageManager.get("prefs.wado.enable", "Enable WADO"),true), gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.stow_rs", "STOW-RS Support:")), gbc); gbc.gridx = 1; pane.add(new JCheckBox(LanguageManager.get("prefs.stow_rs.enable", "Enable STOW-RS"),false), gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel(LanguageManager.get("prefs.base_url", "Base URL:")), gbc); gbc.gridx = 1; pane.add(new JTextField("http://localhost:8080",25), gbc);

        contentPanel.add(new JScrollPane(pane), LanguageManager.get("prefs.nav.webserver", "Web Server"));
    }

    private void createOnDemandPane() {
        JPanel pane = new JPanel(new BorderLayout());
        pane.add(new JLabel(LanguageManager.get("prefs.ondemand_title", "PACS On-Demand Settings")), BorderLayout.NORTH);
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        center.add(new JCheckBox(LanguageManager.get("prefs.ondemand.search_comparative", "Search for comparative studies automatically"),true));
        center.add(Box.createVerticalStrut(6));
        center.add(new JCheckBox(LanguageManager.get("prefs.ondemand.smart_albums", "Query PACS for Smart Albums"),false));
        pane.add(center, BorderLayout.CENTER);

        JPanel queryPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(4,4,4,4); gbc.anchor = GridBagConstraints.WEST; int row = 0;
        gbc.gridx = 0; gbc.gridy = row; queryPanel.add(new JLabel(LanguageManager.get("prefs.ondemand.timeout", "Query Timeout (sec):")),gbc); gbc.gridx=1; queryPanel.add(new JTextField("30",5),gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; queryPanel.add(new JLabel(LanguageManager.get("prefs.ondemand.max_studies", "Max Studies per Query:")),gbc); gbc.gridx=1; queryPanel.add(new JTextField("100",5),gbc);
        pane.add(queryPanel, BorderLayout.SOUTH);

        contentPanel.add(pane, LanguageManager.get("prefs.nav.ondemand", "On-Demand (PACS)"));
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
            setLayout(new FlowLayout(FlowLayout.LEFT));
        }
    }
}

