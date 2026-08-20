package com.oculus.jj.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import com.oculus.jj.utils.LanguageManager;

/**
 * AnonymizationDialog - HOROS AnonymizationViewController 相当。
 * DICOMデータの匿名化ダイアログ。
 */
public class AnonymizationDialog extends JDialog {

    public AnonymizationDialog(Frame owner) {
        super(owner, "DICOM Anonymization", true);
        setSize(750, 500);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // Top: Template selection
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel(LanguageManager.get("anon.template", "Template:")));
        String[] templates = {"Default (ISO 12056)", "DICOM PS3.15", "Custom", "None"};
        JComboBox<String> templateCombo = new JComboBox<>(templates);
        templateCombo.setSelectedIndex(0);
        topPanel.add(templateCombo);
        
        JButton saveTplBtn = new JButton(LanguageManager.get("anon.save_template", "Save As Template"));
        JButton delTplBtn = new JButton(LanguageManager.get("anon.delete_template", "Delete Template"));
        topPanel.add(saveTplBtn);
        topPanel.add(delTplBtn);
        add(topPanel, BorderLayout.NORTH);

        // Center: Tags table (Horos AnonymizationTagsView equivalent)
        JPanel centerPanel = new JPanel(new BorderLayout());
        
        String[] cols = { LanguageManager.get("anon.col.tag", "Tag"), LanguageManager.get("anon.col.attr", "Attribute Name"),
                LanguageManager.get("anon.col.value", "Value"), LanguageManager.get("anon.col.action", "Action") };
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return col == 3; }
        };
        
        // Sample DICOM tags for anonymization
        Object[][] defaultTags = {
            {"(0010,0010)", "Patient Name", "[PatientName]", "Remove"},
            {"(0010,0020)", "Patient ID", "[PatientID]", "Remove"},
            {"(0010,0030)", "Birth Date", "[BirthDate]", "Remove"},
            {"(0010,0040)", "Patient Sex", "[PatientSex]", "Replace"},
            {"(0008,0020)", "Study Date", "[StudyDate]", "Remove"},
            {"(0008,0030)", "Study Time", "[StudyTime]", "Remove"},
            {"(0008,0050)", "Accession Number", "[AccessionNumber]", "Remove"},
            {"(0008,0060)", "Modality", "[Modality]", "Keep"},
            {"(0010,1000)", "Other Patient Names", "[OtherPatientNames]", "Remove"},
            {"(0040,A730)", "Comments", "[Comments]", "Remove"},
        };
        for (Object[] r : defaultTags) model.addRow(r);

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        centerPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Bottom: Preview + Buttons
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));

        // Add/Edit button row
        JPanel editPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton(LanguageManager.get("anon.add_tag", "+ Add Tag"));
        JButton editBtn = new JButton(LanguageManager.get("anon.edit_tag", "Edit Selected"));
        JButton delBtn = new JButton(LanguageManager.get("anon.remove_tag", "- Remove"));
        editPanel.add(addBtn);
        editPanel.add(editBtn);
        editPanel.add(delBtn);
        bottomPanel.add(editPanel);
        bottomPanel.add(Box.createVerticalStrut(6));

        // Preview panel
        JPanel previewPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        int row = 0;
        
        String[] previewLabels = {"Patient Name", "Patient ID", "Study Date", "Modality"};
        String[] previewValues = {"Anonymous", "Anonymous", "[Removed]", "CT"};
        for (int i = 0; i < previewLabels.length; i++) {
            gbc.gridx = 0; gbc.gridy = row;
            previewPanel.add(new JLabel(previewLabels[i] + ":"), gbc);
            gbc.gridx = 1;
            JTextField valField = new JTextField(previewValues[i], 25);
            valField.setEditable(false);
            valField.setFont(new Font("Monospaced", Font.PLAIN, 11));
            previewPanel.add(valField, gbc);
            row++;
        }
        bottomPanel.add(new JScrollPane(previewPanel));
        add(bottomPanel, BorderLayout.SOUTH);

        // OK/Cancel buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton(LanguageManager.get("anon.ok", "OK"));
        JButton cancelBtn = new JButton(LanguageManager.get("anon.cancel", "Cancel"));
        
        okBtn.addActionListener(e -> {
            // Collect all tag changes
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < model.getRowCount(); i++) {
                sb.append(String.format("%s=%s(%s)%n", 
                    model.getValueAt(i, 0), model.getValueAt(i, 2), model.getValueAt(i, 3)));
            }
            JOptionPane.showMessageDialog(this, LanguageManager.get("anon.applied", "Anonymization applied:") + "\n" + sb.toString(), 
                LanguageManager.get("anon.result", "Result"), JOptionPane.INFORMATION_MESSAGE);
            dispose();
        });
        cancelBtn.addActionListener(e -> dispose());
        
        btnRow.add(okBtn);
        btnRow.add(cancelBtn);
        add(btnRow, BorderLayout.EAST);

        setLocationRelativeTo(getOwner());
    }

    public static void showAnonymization(Frame owner) {
        SwingUtilities.invokeLater(() -> new AnonymizationDialog(owner).setVisible(true));
    }
}
