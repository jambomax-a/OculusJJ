package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.Dialog;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * PACSQuerySettingsDialog - PACS Query設定ダイアログ
 */
public class PACSQuerySettingsDialog extends JDialog {

    private static final String PREF_PREFIX = "pacsQuery.";
    private static final Preferences prefs = Preferences.userNodeForPackage(PACSQuerySettingsDialog.class);

    private JTextField callerAEField;
    private JTextField calledAEField;
    private JSpinner portSpinner;
    private JComboBox<String> modalityCombo;
    private JButton applyButton, cancelButton;

    private static final String[] MODALITIES = { "ALL", "CT", "MR", "CR", "DX", "MG", "US", "XA", "RF", "PT", "NM", "SC", "DOC" };
    private static final String[] DAYS_LABELS = { "1 Day", "7 Days", "30 Days", "90 Days", "1 Year", "All" };

    public PACSQuerySettingsDialog(Frame owner) {
        super(owner, "PACS Query Settings", Dialog.ModalityType.MODELESS);
        initComponents();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(Color.WHITE);

        JPanel aePanel = createAEPanels();
        add(aePanel, BorderLayout.NORTH);

        JPanel filterPanel = createFilterPanels();
        add(filterPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        applyButton = makePrimaryButton("Apply");
        cancelButton = makeButton("Cancel");

        applyButton.addActionListener(e -> applySettings());
        cancelButton.addActionListener(e -> dispose());

        btnPanel.add(applyButton);
        btnPanel.add(cancelButton);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private JPanel createAEPanels() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Caller AE:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        callerAEField = new JTextField(prefs.get(PREF_PREFIX + "callerAE", "OculusJJ"));
        callerAEField.setColumns(20);
        panel.add(callerAEField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("Called AE:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        calledAEField = new JTextField(prefs.get(PREF_PREFIX + "calledAE", "ANY-SCP"));
        calledAEField.setColumns(20);
        panel.add(calledAEField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        portSpinner = new JSpinner(new SpinnerNumberModel(
                prefs.getInt(PREF_PREFIX + "port", 11112), 1, 65535, 1));
        ((JSpinner.DefaultEditor) portSpinner.getEditor()).getTextField().setColumns(6);
        panel.add(portSpinner, gbc);

        return panel;
    }

    private JPanel createFilterPanels() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Modality:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        modalityCombo = new JComboBox<>(MODALITIES);
        String savedMod = prefs.get(PREF_PREFIX + "modality", "ALL");
        for (int i = 0; i < MODALITIES.length; i++) {
            if (MODALITIES[i].equals(savedMod)) { modalityCombo.setSelectedIndex(i); break; }
        }
        panel.add(modalityCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("Date Range:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        gbc.insets = new Insets(4, 4, 4, 16);

        JComboBox<String> daysCombo = new JComboBox<>(DAYS_LABELS);
        String savedDays = prefs.get(PREF_PREFIX + "daysRange", "30 Days");
        for (int i = 0; i < DAYS_LABELS.length; i++) {
            if (DAYS_LABELS[i].equals(savedDays)) { daysCombo.setSelectedIndex(i); break; }
        }
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(daysCombo, gbc);

        return panel;
    }

    private JButton makePrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new java.awt.Dimension(90, 28));
        return btn;
    }

    private JButton makeButton(String text) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new java.awt.Dimension(75, 28));
        return btn;
    }

    public void applySettings() {
        prefs.put(PREF_PREFIX + "callerAE", callerAEField.getText().trim());
        prefs.put(PREF_PREFIX + "calledAE", calledAEField.getText().trim());
        prefs.putInt(PREF_PREFIX + "port", ((Number) portSpinner.getValue()).intValue());
        prefs.put(PREF_PREFIX + "modality", MODALITIES[modalityCombo.getSelectedIndex()]);
        JOptionPane.showMessageDialog(this, "PACS Query settings applied.", "Settings",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public String getCallerAE() { return callerAEField.getText().trim(); }
    public String getCalledAE() { return calledAEField.getText().trim(); }
    public int getPort() { return ((Number) portSpinner.getValue()).intValue(); }
    public String getModality() { return MODALITIES[modalityCombo.getSelectedIndex()]; }

    public static void showPACSQuerySettingsDialog(Frame owner) {
        new PACSQuerySettingsDialog(owner).setVisible(true);
    }
}
