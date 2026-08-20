package com.oculus.jj.ui;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import com.oculus.jj.utils.LanguageManager;

/**
 * Curve Palette Dialog
 * HOROS-20240407 [CPRVolume] 相当の機能を模倣
 */
public class CurvePaletteDialog extends JDialog {
    private static CurvePaletteDialog instance;
    private CPRController controller;

    private JSlider thicknessSlider;
    private JLabel thicknessLabel;
    private JCheckBox interpolateCheck;

    private CurvePaletteDialog(Frame owner, CPRController controller) {
        super(owner, "Curve Palette", false);
        this.controller = controller;
        setLayout(new BorderLayout());
        setAlwaysOnTop(true);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Thickness selection ---
        JPanel thicknessPanel = new JPanel(new BorderLayout());
        thicknessPanel.setBorder(BorderFactory.createTitledBorder(LanguageManager.get("curve.thickness", "Display Thickness (mm)")));

        thicknessSlider = new JSlider(0, 100, 0); // 0 to 10.0 mm
        thicknessLabel = new JLabel("0.0");
        thicknessLabel.setPreferredSize(new Dimension(50, 20));

        thicknessSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                float val = thicknessSlider.getValue() / 10.0f;
                thicknessLabel.setText(String.format("%.1f", val));
                // 厚みの適用 (将来的なマルチスラブ描画用)
            }
        });

        thicknessPanel.add(thicknessSlider, BorderLayout.CENTER);
        thicknessPanel.add(thicknessLabel, BorderLayout.EAST);
        mainPanel.add(thicknessPanel);

        // --- Other options ---
        JPanel optPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        interpolateCheck = new JCheckBox(LanguageManager.get("curve.interpolate", "Interpolate Points"), true);
        optPanel.add(interpolateCheck);
        mainPanel.add(optPanel);

        add(mainPanel, BorderLayout.CENTER);

        pack();
        if (owner != null) {
            setLocation(owner.getX() + owner.getWidth() - getWidth() - 20, owner.getY() + 100);
        }
    }

    public static void showPalette(Frame owner, CPRController controller) {
        if (instance == null) {
            instance = new CurvePaletteDialog(owner, controller);
        }
        instance.controller = controller;
        instance.setVisible(true);
    }

    public static void hidePalette() {
        if (instance != null) {
            instance.setVisible(false);
        }
    }
}
