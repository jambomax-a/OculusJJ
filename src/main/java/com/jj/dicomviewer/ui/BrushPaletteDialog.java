package com.jj.dicomviewer.ui;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Brush Palette Dialog
 * HOROS-20240407準拠: PaletteBrush.m の機能を模倣
 */
public class BrushPaletteDialog extends JDialog {
    private static BrushPaletteDialog instance;

    private JRadioButton drawBtn;
    private JRadioButton eraseBtn;
    private JSlider sizeSlider;
    private JLabel sizeLabel;

    public enum BrushMode {
        DRAW, ERASE
    }

    private BrushPaletteDialog(Frame owner) {
        super(owner, "Brush Palette", false);
        setLayout(new BorderLayout());
        setAlwaysOnTop(true);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Mode selection ---
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.setBorder(BorderFactory.createTitledBorder("Mode"));

        drawBtn = new JRadioButton("Draw", true);
        eraseBtn = new JRadioButton("Erase");
        ButtonGroup group = new ButtonGroup();
        group.add(drawBtn);
        group.add(eraseBtn);

        modePanel.add(drawBtn);
        modePanel.add(eraseBtn);
        mainPanel.add(modePanel);

        // --- Size selection ---
        JPanel sizePanel = new JPanel(new BorderLayout());
        sizePanel.setBorder(BorderFactory.createTitledBorder("Brush Size"));

        // HOROS-20240407: Slider 1 to 17 (Radius)
        sizeSlider = new JSlider(1, 17, 3);
        sizeLabel = new JLabel("3");
        sizeLabel.setPreferredSize(new Dimension(50, 20));

        sizeSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                sizeLabel.setText(String.valueOf(sizeSlider.getValue()));
            }
        });

        sizePanel.add(sizeSlider, BorderLayout.CENTER);
        sizePanel.add(sizeLabel, BorderLayout.EAST);
        mainPanel.add(sizePanel);

        add(mainPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(owner);
    }

    public static void showPalette(Frame owner) {
        if (instance == null) {
            instance = new BrushPaletteDialog(owner);
        }
        instance.setVisible(true);
    }

    public static void hidePalette() {
        if (instance != null) {
            instance.setVisible(false);
        }
    }

    public static BrushMode getBrushMode() {
        if (instance == null)
            return BrushMode.DRAW;
        return instance.drawBtn.isSelected() ? BrushMode.DRAW : BrushMode.ERASE;
    }

    public static int getBrushSize() {
        if (instance == null)
            return 3;
        return instance.sizeSlider.getValue();
    }
}
