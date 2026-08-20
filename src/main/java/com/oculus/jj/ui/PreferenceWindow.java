package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import com.oculus.jj.config.AppPreferences;
import com.oculus.jj.utils.LanguageManager;

/**
 * PreferenceWindow - 環境設定ウィンドウ
 * HOROS-20240407のPreferencesWindowController相当の機能を提供。
 */
public class PreferenceWindow extends JDialog {

    public PreferenceWindow(Frame owner) {
        super(owner, LanguageManager.get("prefwin.title", "OculusJJ Preferences"), true);
        initializeUI();
    }

    private void initializeUI() {
        setSize(500, 400);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();

        // 3D Settings Tab
        tabbedPane.addTab("3D Viewer", create3DPane());

        // General Tab (Placeholder)
        tabbedPane.addTab("General", createGeneralPane());

        add(tabbedPane, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClose = new JButton(LanguageManager.get("prefwin.close", "Close"));
        btnClose.addActionListener(e -> {
            AppPreferences.saveAll();
            dispose();
        });
        buttonPanel.add(btnClose);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel create3DPane() {
        JPanel pane = new JPanel();
        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
        pane.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Resolution
        JPanel resPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resPanel.add(new JLabel(LanguageManager.get("prefwin.resolution", "3D Rendering Resolution (0.1 - 1.0): ")));
        JSpinner resSpinner = new JSpinner(new SpinnerNumberModel(AppPreferences.get3DResolution(), 0.1, 1.0, 0.1));
        resSpinner.addChangeListener(e -> AppPreferences.set3DResolution((double) resSpinner.getValue()));
        resPanel.add(resSpinner);
        pane.add(resPanel);

        // Shading
        JCheckBox chkShading = new JCheckBox(LanguageManager.get("prefwin.shading", "Enable Shading by default"), AppPreferences.is3DShadingEnabled());
        chkShading.addActionListener(e -> AppPreferences.set3DShadingEnabled(chkShading.isSelected()));
        pane.add(chkShading);

        // ROI Color
        JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        colorPanel.add(new JLabel(LanguageManager.get("prefwin.roicolor", "Default ROI Color: ")));
        JButton btnColor = new JButton(LanguageManager.get("prefwin.choosecolor", "Choose Color..."));
        btnColor.setBackground(AppPreferences.getROIColor());
        btnColor.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, LanguageManager.get("prefwin.selectroicolor", "Select ROI Color"), AppPreferences.getROIColor());
            if (c != null) {
                AppPreferences.setROIColor(c);
                btnColor.setBackground(c);
            }
        });
        colorPanel.add(btnColor);
        pane.add(colorPanel);

        return pane;
    }

    private JPanel createGeneralPane() {
        JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pane.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        pane.add(new JLabel(LanguageManager.get("prefwin.measureunit", "Measurement Unit: ")));
        String[] units = {"mm", "cm"};
        JComboBox<String> comboUnits = new JComboBox<>(units);
        comboUnits.setSelectedItem(AppPreferences.getMeasureUnit());
        comboUnits.addActionListener(e -> AppPreferences.setMeasureUnit((String) comboUnits.getSelectedItem()));
        pane.add(comboUnits);
        
        return pane;
    }

    public static void showPreferences(Frame owner) {
        SwingUtilities.invokeLater(() -> {
            PreferenceWindow win = new PreferenceWindow(owner);
            win.setVisible(true);
        });
    }
}
