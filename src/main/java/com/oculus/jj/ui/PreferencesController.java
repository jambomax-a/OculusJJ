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
        saveBtn.addActionListener(e -> { AppPreferences.saveAll(); });
        JButton closeBtn = new JButton("Close");
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

        JSectionBox ifaceBox = new JSectionBox("Interface");
        String[] langs = {"English (en)", "Japanese (ja)"};
        JComboBox<String> langCombo = new JComboBox<>(langs);
        langCombo.setSelectedItem(com.oculus.jj.config.AppPreferences.getAppLanguage().equals("ja") ? "Japanese (ja)" : "English (en)");
        String[] displayTypes = {"Original Pixel Data", "Gray Scale", "Displayed Pixel Data"};
        JComboBox<String> displayTypeCombo = new JComboBox<>(displayTypes);

        pane.add(ifaceBox);
        pane.add(makeRow(new JLabel("Language:"), langCombo));
        pane.add(Box.createVerticalStrut(6));
        pane.add(makeRow(new JLabel("Image Display Type:"), displayTypeCombo));
        pane.add(Box.createVerticalStrut(10));

        JSectionBox autoBox = new JSectionBox("Studies");
        JCheckBox chkSeparateWin = new JCheckBox("Open each selected study in a separate window", true);
        pane.add(autoBox);
        pane.add(chkSeparateWin);
        pane.add(Box.createVerticalStrut(4));
        JCheckBox chkCheckUpdates = new JCheckBox("Automatically check for application updates at startup", false);
        pane.add(chkCheckUpdates);

        contentPanel.add(new JScrollPane(pane), "General");
    }

    private void createDatabasePane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        
        gbc.gridx = 0; gbc.gridy = row;
        pane.add(new JLabel("Database Folder:"), gbc);
        gbc.gridx = 1;
        JTextField dbPathField = new JTextField(AppPreferences.getDatabaseFolder(), 25);
        dbPathField.setEditable(false);
        pane.add(dbPathField, gbc);
        gbc.gridx = 2;
        JButton browseDbBtn = new JButton("Browse...");
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
        pane.add(new JLabel("DICOM Files Storage Path:"), gbc);
        gbc.gridx = 1;
        JTextField dicomPathField = new JTextField(AppPreferences.getDicomFolder(), 25);
        dicomPathField.setEditable(false);
        pane.add(dicomPathField, gbc);
        gbc.gridx = 2;
        JButton browseDicomBtn = new JButton("Browse...");
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
        JCheckBox chkAutoRefresh = new JCheckBox("Automatically refresh database on startup", true);
        pane.add(chkAutoRefresh, gbc);

        contentPanel.add(new JScrollPane(pane), "Database");
    }

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

        JSectionBox protocolBox = new JSectionBox("Default Hanging Protocol");
        String[] modalities = {"CR", "CT", "DX", "ES", "MG", "MR", "NM", "OT", "PT", "RF", "SC", "US", "XA"};
        JComboBox<String> modalityCombo = new JComboBox<>(modalities);
        modalityCombo.setSelectedIndex(1);
        String[] tilingOptions = {"1x1","2x1","1x2","2x2","3x1","1x3","2x3","3x2","4x1","1x4","3x3","2x4","4x2","4x3","5x1","1x5"};
        JComboBox<String> tilingCombo = new JComboBox<>(tilingOptions);
        tilingCombo.setSelectedIndex(3);

        pane.add(protocolBox);
        pane.add(makeRow(new JLabel("Modality:"), modalityCombo));
        pane.add(Box.createVerticalStrut(6));
        pane.add(makeRow(new JLabel("Image Tiling:"), tilingCombo));
        pane.add(Box.createVerticalStrut(10));

        JSectionBox windowBox = new JSectionBox("Window Level / Width Presets");
        String[] wlwwPresets = {"Default","Bone","Lung","Liver","Brain","Soft Tissue","Mediastinum"};
        pane.add(windowBox);
        pane.add(makeRow(new JLabel("Default WL/WW Preset:"), new JComboBox<>(wlwwPresets)));

        contentPanel.add(new JScrollPane(pane), "Hanging Protocols");
    }

    private void createHotKeysPane() {
        JPanel pane = new JPanel(new BorderLayout());
        String[] cols = {"Action", "Key Combination"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        Object[][] hotKeys = {
            {"Zoom to ROI","Ctrl+Shift+Click"},{"Toggle WL/WW Mode","W"},
            {"Pan Mode","P"},{"Select/Edit ROI","S"},{"Freehand Measure","M"},
            {"Rectangular ROI","R"},{"Oval ROI","O"},{"Angle Measurement","A"},
            {"Flip Horizontal","F"},{"Rotate 90 CW","Shift+Right Click"},
            {"Zoom In/Out","Shift+Scroll"},{"3D MIP Viewer","Ctrl+M"},
            {"3D Volume Rendering","Ctrl+V"},{"Curved MPR","Ctrl+Y"}
        };
        for (Object[] r : hotKeys) model.addRow(r);

        pane.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(new JButton("+ Add Key Binding"));
        bottomPanel.add(new JButton("Edit Selected"));
        bottomPanel.add(new JButton("- Remove"));
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

        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Default Opacity Table:"), gbc);
        gbc.gridx = 1; pane.add(new JComboBox<>(new String[]{"Standard","PET Specific","NM Specific"}), gbc);

        row++; gbc.gridy = row; JCheckBox chkSync = new JCheckBox("Synchronize series between studies");
        gbc.gridwidth = 2; pane.add(chkSync, gbc);

        contentPanel.add(new JScrollPane(pane), "Viewers");
    }

    private void create3DPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JSectionBox renderBox = new JSectionBox("Rendering");
        double res = AppPreferences.get3DResolution();
        int resIdx = (res < 0.3) ? 0 : (res < 0.6) ? 1 : 2;
        pane.add(renderBox);
        pane.add(makeRow(new JLabel("Rendering Resolution:"), 
            new JComboBox<>(new String[]{"Good","Better","Best"})));
        JPanel resPanel = makeRow(new JLabel(""), null);
        JSlider resSlider = new JSlider(1, 10, (int)(res * 10));
        resSlider.setMajorTickSpacing(1); resSlider.setPaintLabels(true); resSlider.setPaintTicks(true);
        resSlider.addChangeListener(e -> AppPreferences.set3DResolution(resSlider.getValue() / 10.0));
        pane.add(resPanel);
        pane.add(Box.createVerticalStrut(6));
        JCheckBox chkShading = new JCheckBox("Enable Shading by default", AppPreferences.is3DShadingEnabled());
        chkShading.addActionListener(e -> AppPreferences.set3DShadingEnabled(chkShading.isSelected()));
        pane.add(chkShading);
        pane.add(Box.createVerticalStrut(6));
        pane.add(makeRow(new JLabel("Shading Algorithm:"), new JComboBox<>(new String[]{"Phong","Gouraud","Metallic"})));
        pane.add(Box.createVerticalStrut(10));

        JSectionBox roiBox = new JSectionBox("3D ROI Display");
        int rgb = AppPreferences.getROIColor().getRGB();
        String[] roiColors = {"Yellow","Red","Green","Blue","Cyan","Magenta"};
        JComboBox<String> roiColorCombo = new JComboBox<>(roiColors);
        if (rgb == Color.YELLOW.getRGB()) roiColorCombo.setSelectedIndex(0);
        else if (rgb == Color.RED.getRGB()) roiColorCombo.setSelectedIndex(1);

        pane.add(roiBox);
        pane.add(makeRow(new JLabel("Default ROI Color:"), roiColorCombo));
        pane.add(Box.createVerticalStrut(4));
        pane.add(makeRow(new JLabel("Algorithm for 3D ROIs:"), new JComboBox<>(new String[]{"Iso Contour","Delaunay"})));
        pane.add(Box.createVerticalStrut(6));
        JCheckBox chkSpline = new JCheckBox("Use Spline interpolation for Scissors ROI", false);
        pane.add(chkSpline);

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
        
        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Application AETitle:"), gbc);
        gbc.gridx = 1; JTextField aetF = new JTextField("OCULUSJJ", 20); pane.add(aetF, gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Listen on Port:"), gbc);
        gbc.gridx = 1; pane.add(new JTextField("11112", 8), gbc);
        row++; gbc.gridy = row; pane.add(new JCheckBox("Automatically start listener on launch"), gbc);

        contentPanel.add(new JScrollPane(pane), "Listener");
    }

    private void createLocationsPane() {
        JPanel locationsPane = new JPanel(new BorderLayout());
        String[] cols = {"Name", "AETitle", "Address", "Port"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);
        model.addRow(new Object[]{"Localhost","OCULUSJJ","localhost","11112"});

        locationsPane.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        JButton addBtn = new JButton("Add");
        addBtn.addActionListener(e -> {
            DicomNode node = new DicomNode();
            showNodeEditor(node, () -> 
                model.addRow(new Object[]{node.getName(), node.getAeTitle(), node.getAddress(), String.valueOf(node.getPort())}));
        });
        btnPanel.add(addBtn);
        JButton delBtn = new JButton("Delete");
        delBtn.addActionListener(e -> { int r = table.getSelectedRow(); if (r >= 0) model.removeRow(r); });
        btnPanel.add(delBtn);
        JButton verifyBtn = new JButton("Verify");
        verifyBtn.addActionListener(e -> {
            int r = table.getSelectedRow(); if (r < 0) return;
            String addr = (String)model.getValueAt(r,2);
            int port = Integer.parseInt((String)model.getValueAt(r,3));
            DicomNode node = new DicomNode();
            node.setAddress(addr); node.setPort(port);
            new Thread(() -> {
                try { boolean ok = DicomService.getInstance().echo(node);
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(PreferencesController.this, ok?"Echo Success!":"Echo Failed"));
                } catch(Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(PreferencesController.this,"Error: "+ex.getMessage()));
                }
            }).start();
        });
        btnPanel.add(verifyBtn);
        locationsPane.add(btnPanel, BorderLayout.SOUTH);

        contentPanel.add(locationsPane, "Locations");
    }

    private void createRoutingPane() {
        JPanel pane = new JPanel(new BorderLayout());
        pane.add(new JLabel("DICOM Autorouting Rules"), BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new String[]{"Rule","Source AE","Destination","Filter"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        model.addRow(new Object[]{"Default Store","*","OCULUSJJ","*"});
        pane.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        JPanel controls = new JPanel();
        controls.add(new JButton("Add Rule")); controls.add(new JButton("Remove Rule"));
        pane.add(controls, BorderLayout.SOUTH);
        contentPanel.add(pane, "Routing");
    }

    private void createWebServerPane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); gbc.anchor = GridBagConstraints.WEST; int row = 0;
        
        gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Web Server Port:"), gbc); gbc.gridx = 1; pane.add(new JTextField("8080",5), gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("WADO Support:"), gbc); gbc.gridx = 1; pane.add(new JCheckBox("Enable WADO",true), gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("STOW-RS Support:"), gbc); gbc.gridx = 1; pane.add(new JCheckBox("Enable STOW-RS",false), gbc);
        row++; gbc.gridx = 0; gbc.gridy = row; pane.add(new JLabel("Base URL:"), gbc); gbc.gridx = 1; pane.add(new JTextField("http://localhost:8080",25), gbc);

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
}

