package com.oculus.jj.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import com.oculus.jj.model.DicomNode;
import com.oculus.jj.model.DicomNodeManager;
import com.oculus.jj.model.DicomService;

/**
 * PreferencesController - Horos-20240407準拠の環境設定ウィンドウ
 * 
 * 左側のリストでセクションを選択し、右側のパネルを切り替える構成(HorosのPreferencePanesを再現)
 */
public class PreferencesController extends JFrame {

    private static PreferencesController instance;
    private JList<String> sidebar;
    private JPanel contentPanel;
    private CardLayout cardLayout;

    // Panes
    private JPanel listenerPane;
    private JPanel locationsPane;
    private JPanel generalPane;

    public static synchronized PreferencesController getInstance() {
        if (instance == null) {
            instance = new PreferencesController();
        }
        return instance;
    }

    private PreferencesController() {
        setTitle("Preferences");
        setSize(800, 500);
        setLocationRelativeTo(null);
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // --- Sidebar (Left) ---
        // Horos-style Categorization
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("-- Basics --");
        model.addElement("General");
        model.addElement("Database");
        model.addElement("Viewers");
        model.addElement("-- Sharing --");
        model.addElement("Listener");
        model.addElement("Locations");
        model.addElement("Routing");
        model.addElement("Web Server");
        model.addElement("On-Demand");

        sidebar = new JList<>(model);
        sidebar.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sidebar.setSelectedIndex(1); // Select "General" by default
        sidebar.setPreferredSize(new Dimension(150, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY));

        // Disable selection of headers
        sidebar.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = sidebar.getSelectedValue();
                if (selected != null && selected.startsWith("--")) {
                    // Header selected, re-select previous or next valid item
                    sidebar.setSelectedIndex(
                            e.getFirstIndex() == sidebar.getSelectedIndex() ? e.getLastIndex() : e.getFirstIndex());
                } else if (selected != null) {
                    cardLayout.show(contentPanel, selected);
                }
            }
        });

        add(new JScrollPane(sidebar), BorderLayout.WEST);

        // --- Content Panel (Right) ---
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        createGeneralPane();
        createListenerPane();
        createLocationsPane();
        createRoutingPane();
        createWebServerPane();
        createOnDemandPane();

        // Placeholders for others
        contentPanel.add(new JLabel("Viewers Settings Placeholder"), "Viewers");

        add(contentPanel, BorderLayout.CENTER);

        // --- Bottom Buttons ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> setVisible(false));
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void createGeneralPane() {
        generalPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        generalPane.add(new JLabel("General Settings (Horos-20240407 Style)"));
        contentPanel.add(generalPane, "General");

        // Database Pane
        createDatabasePane();
    }

    private void createDatabasePane() {
        JPanel databasePane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // DICOM Folder
        gbc.gridx = 0;
        gbc.gridy = 0;
        databasePane.add(new JLabel("DICOM Files Storage Path:"), gbc);

        gbc.gridy = 1;
        JTextField dicomPathField = new JTextField(com.oculus.jj.config.AppPreferences.getDicomFolder());
        dicomPathField.setEditable(false);
        databasePane.add(dicomPathField, gbc);

        gbc.gridx = 1;
        JButton changeDicomBtn = new JButton("Change...");
        changeDicomBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(dicomPathField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                dicomPathField.setText(path);
                com.oculus.jj.config.AppPreferences.setDicomFolder(path);
                com.oculus.jj.config.AppPreferences.saveAll();
            }
        });
        databasePane.add(changeDicomBtn, gbc);

        // Database Folder
        gbc.gridx = 0;
        gbc.gridy = 2;
        databasePane.add(new JLabel("Database (Index) Path:"), gbc);

        gbc.gridy = 3;
        JTextField dbPathField = new JTextField(com.oculus.jj.config.AppPreferences.getDatabaseFolder());
        dbPathField.setEditable(false);
        databasePane.add(dbPathField, gbc);

        gbc.gridx = 1;
        JButton changeDbBtn = new JButton("Change...");
        changeDbBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(dbPathField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                dbPathField.setText(path);
                com.oculus.jj.config.AppPreferences.setDatabaseFolder(path);
                com.oculus.jj.config.AppPreferences.saveAll();
                JOptionPane.showMessageDialog(this, "Restart application to apply database path changes.");
            }
        });
        databasePane.add(changeDbBtn, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        databasePane.add(new JLabel("<html><i>Note: Moving the database path requires manual migration of existing data.</i></html>"), gbc);

        contentPanel.add(new JScrollPane(databasePane), "Database");
    }

    private void createListenerPane() {
        listenerPane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        listenerPane.add(new JLabel("AETitle:"), gbc);
        JTextField aetField = new JTextField(DicomNodeManager.getInstance().getLocalAET(), 15);
        gbc.gridx = 1;
        listenerPane.add(aetField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        listenerPane.add(new JLabel("Port:"), gbc);
        JTextField portField = new JTextField(String.valueOf(DicomNodeManager.getInstance().getLocalPort()), 10);
        gbc.gridx = 1;
        listenerPane.add(portField, gbc);

        JButton updateBtn = new JButton("Update & Restart Listener");
        updateBtn.addActionListener(e -> {
            try {
                String aet = aetField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                DicomNodeManager.getInstance().setLocalConfig(aet, port);
                DicomService.getInstance().stopListener();
                DicomService.getInstance().startListener(port);
                JOptionPane.showMessageDialog(this, "DICOM Listener restarted successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to restart listener: " + ex.getMessage());
            }
        });
        gbc.gridx = 1;
        gbc.gridy = 2;
        listenerPane.add(updateBtn, gbc);

        contentPanel.add(new JScrollPane(listenerPane), "Listener");
    }

    private void createLocationsPane() {
        locationsPane = new JPanel(new BorderLayout());

        String[] cols = { "Name", "AETitle", "Address", "Port" };
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);

        Runnable refreshTable = () -> {
            model.setRowCount(0);
            for (DicomNode node : DicomNodeManager.getInstance().getNodes()) {
                model.addRow(new Object[] { node.getName(), node.getAeTitle(), node.getAddress(), node.getPort() });
            }
        };
        refreshTable.run();

        locationsPane.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        JButton addBtn = new JButton("Add Node");
        JButton editBtn = new JButton("Edit");
        JButton delBtn = new JButton("Delete");
        JButton verifyBtn = new JButton("Verify (Echo)");

        addBtn.addActionListener(e -> {
            showNodeEditor(null, refreshTable);
        });

        editBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                showNodeEditor(DicomNodeManager.getInstance().getNodes().get(row), refreshTable);
            }
        });

        delBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                if (JOptionPane.showConfirmDialog(this, "Delete this node?") == JOptionPane.YES_OPTION) {
                    DicomNodeManager.getInstance().removeNode(DicomNodeManager.getInstance().getNodes().get(row));
                    refreshTable.run();
                }
            }
        });

        verifyBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                DicomNode node = DicomNodeManager.getInstance().getNodes().get(row);
                new Thread(() -> {
                    try {
                        boolean ok = DicomService.getInstance().echo(node);
                        SwingUtilities.invokeLater(
                                () -> JOptionPane.showMessageDialog(this, ok ? "Echo Success!" : "Echo Failed"));
                    } catch (Exception ex) {
                        SwingUtilities
                                .invokeLater(() -> JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()));
                    }
                }).start();
            }
        });

        btnPanel.add(addBtn);
        btnPanel.add(editBtn);
        btnPanel.add(delBtn);
        btnPanel.add(verifyBtn);
        locationsPane.add(btnPanel, BorderLayout.SOUTH);

        contentPanel.add(locationsPane, "Locations");
    }

    private void createRoutingPane() {
        JPanel pane = new JPanel(new BorderLayout());
        pane.add(new JLabel("DICOM Autorouting (Horos Routing Style)"), BorderLayout.NORTH);

        String[] cols = { "Destination", "Filter", "Status" };
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);
        pane.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.add(new JButton("Add Rule"));
        controls.add(new JButton("Remove Rule"));
        pane.add(controls, BorderLayout.SOUTH);

        contentPanel.add(pane, "Routing");
    }

    private void createWebServerPane() {
        JPanel pane = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        pane.add(new JLabel("Web Server Port:"), gbc);
        gbc.gridx = 1;
        pane.add(new JTextField("8080", 5), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        pane.add(new JLabel("WADO Support:"), gbc);
        gbc.gridx = 1;
        pane.add(new JCheckBox("Enable WADO", true), gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        pane.add(new JLabel("Base URL:"), gbc);
        gbc.gridx = 1;
        pane.add(new JTextField("http://localhost:8080", 20), gbc);

        contentPanel.add(new JScrollPane(pane), "Web Server");
    }

    private void createOnDemandPane() {
        JPanel pane = new JPanel(new BorderLayout());
        pane.add(new JLabel("PACS On-Demand (Smart Album/Comparative Search)"), BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(new JCheckBox("Search for comparative studies automatically", true));
        center.add(new JCheckBox("Query PACS for Smart Albums", false));

        pane.add(center, BorderLayout.CENTER);

        contentPanel.add(pane, "On-Demand");
    }

    private void showNodeEditor(DicomNode node, Runnable onSave) {
        JDialog dialog = new JDialog(this, node == null ? "Add DICOM Node" : "Edit DICOM Node", true);
        dialog.setSize(350, 250);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridLayout(6, 2, 5, 5));

        DicomNode target = (node != null) ? node : new DicomNode();
        boolean isNew = (node == null);

        dialog.add(new JLabel(" Name:"));
        JTextField nameF = new JTextField(target.getName());
        dialog.add(nameF);

        dialog.add(new JLabel(" AETitle:"));
        JTextField aetF = new JTextField(target.getAeTitle());
        dialog.add(aetF);

        dialog.add(new JLabel(" Address:"));
        JTextField addrF = new JTextField(target.getAddress());
        dialog.add(addrF);

        dialog.add(new JLabel(" Port:"));
        JTextField portF = new JTextField(String.valueOf(target.getPort() > 0 ? target.getPort() : 11112));
        dialog.add(portF);

        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> {
            try {
                target.setName(nameF.getText());
                target.setAeTitle(aetF.getText());
                target.setAddress(addrF.getText());
                target.setPort(Integer.parseInt(portF.getText()));
                if (isNew)
                    DicomNodeManager.getInstance().addNode(target);
                else
                    DicomNodeManager.getInstance().saveNodes();
                onSave.run();
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid port number.");
            }
        });
        dialog.add(saveBtn);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.add(cancelBtn);

        dialog.setVisible(true);
    }
}
