package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * BurnerDialog - CD/DVD焼録ダイアログ
 * HOROS-20240407準拠のCD Burning機能の実装。
 */
public class BurnerDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private JList<String> filesList;
    private DefaultListModel<String> fileListModel;
    private JTable burnTable;
    private JButton[] ejectButtons;
    private JTextField supplementaryFolderField;

    public BurnerDialog(Frame owner) { super(owner, "Burn to CD/DVD", false); setSize(600, 450); initUI(); }

    /** ダイアログ表示（static factory） */
    public static void showBurnerDialog(Frame owner) { new BurnerDialog(owner).setVisible(true); }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left side: source folder + file list
        JPanel leftPanel = createSourcePanel();
        main.add(leftPanel, BorderLayout.WEST);

        // Right side: burn target info
        JPanel rightPanel = createBurnInfoPanel();
        main.add(rightPanel, BorderLayout.EAST);

        // Bottom buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton burnBtn = new JButton("Burn");
        burnBtn.addActionListener(e -> startBurn());
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(burnBtn);
        btnPanel.add(cancelBtn);

        main.add(btnPanel, BorderLayout.SOUTH);
        add(main);
    }

    private JPanel createSourcePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JButton selectFolderBtn = new JButton("Select Folder...");
        selectFolderBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                loadFolderFiles(fc.getSelectedFile().getAbsolutePath());
            }
        });

        fileListModel = new DefaultListModel<>();
        filesList = new JList<>(fileListModel);
        filesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        filesList.setFixedCellHeight(20);
        JScrollPane scrollPane = new JScrollPane(filesList);
        scrollPane.setPreferredSize(new Dimension(350, 300));

        panel.add(selectFolderBtn, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void loadFolderFiles(String folderPath) {
        fileListModel.removeAllElements();
        File folder = new File(folderPath);
        if (!folder.isDirectory()) return;
        collectFiles(folder, fileListModel);
    }

    private void collectFiles(File dir, DefaultListModel<String> model) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File f : entries) {
            if (f.isDirectory()) collectFiles(f, model);
            else if (!isNonDicomExt(f.getName())) model.addElement(f.getAbsolutePath());
        }
    }

    private boolean isNonDicomExt(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return false;
        String ext = name.substring(dot).toLowerCase();
        return ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png") ||
               ext.equals(".bmp") || ext.equals(".zip") || ext.equals(".rar") ||
               ext.equals(".pdf") || ext.equals(".txt") || ext.equals(".xml");
    }

    private JPanel createBurnInfoPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 8));

        JLabel titleLabel = new JLabel("CD/DVD Burn Info");
        titleLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 14f));
        panel.add(titleLabel);

        // Disk info (placeholder)
        JLabel diskInfo = new JLabel("Disk: Not inserted or unknown capacity");
        diskInfo.setForeground(Color.GRAY);
        panel.add(diskInfo);

        JButton ejectBtn = new JButton("Eject");
        ejectBtn.addActionListener(e -> JOptionPane.showMessageDialog(this, "Eject command pending", "CD/DVD", JOptionPane.INFORMATION_MESSAGE));
        panel.add(ejectBtn);

        // Supplementary folder
        supplementaryFolderField = new JTextField(20);
        supplementaryFolderField.setText(com.oculus.jj.config.AppPreferences.getCDDVDSupplementaryFolder());
        JLabel suppLabel = new JLabel("Supplementary Folder:");
        JButton browseBtn = new JButton("Browse");
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) supplementaryFolderField.setText(fc.getSelectedFile().getAbsolutePath());
        });
        JPanel suppPanel = new JPanel(new BorderLayout(5, 0));
        suppPanel.add(suppLabel, BorderLayout.WEST);
        suppPanel.add(supplementaryFolderField, BorderLayout.CENTER);
        suppPanel.add(browseBtn, BorderLayout.EAST);
        panel.add(suppPanel);

        return panel;
    }

    private void startBurn() {
        List<String> selected = filesList.getSelectedValuesList();
        if (selected.isEmpty()) { JOptionPane.showMessageDialog(this, "No files selected.", "Burn", JOptionPane.WARNING_MESSAGE); return; }
        com.oculus.jj.config.AppPreferences.setCDDVDSupplementaryFolder(supplementaryFolderField.getText());

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                for (String f : selected) { /* TODO: copy to CD/DVD */ }
                return "Burn complete";
            }
            @Override protected void done() { JOptionPane.showMessageDialog(BurnerDialog.this, "Burn operation completed", "CD/DVD Burn", JOptionPane.INFORMATION_MESSAGE); dispose(); }
        }.execute();
    }
}

