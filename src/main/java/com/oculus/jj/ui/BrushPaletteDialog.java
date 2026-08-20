package com.oculus.jj.ui;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import com.oculus.jj.utils.LanguageManager;

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
        modePanel.setBorder(BorderFactory.createTitledBorder(LanguageManager.get("brush.mode", "Mode")));

        drawBtn = new JRadioButton(LanguageManager.get("brush.draw", "Draw"), true);
        eraseBtn = new JRadioButton(LanguageManager.get("brush.erase", "Erase"));
        ButtonGroup group = new ButtonGroup();
        group.add(drawBtn);
        group.add(eraseBtn);

        modePanel.add(drawBtn);
        modePanel.add(eraseBtn);
        mainPanel.add(modePanel);

        // --- Size selection ---
        JPanel sizePanel = new JPanel(new BorderLayout());
        sizePanel.setBorder(BorderFactory.createTitledBorder(LanguageManager.get("brush.size", "Brush Size")));

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

        // --- P4: Morphological ops (Dilate / Erode) on selected Brush ROI ---
        JPanel morphPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        morphPanel.setBorder(BorderFactory.createTitledBorder(
                LanguageManager.get("brush.morph.strength", "Brush Morph")));
        JButton dilateBtn = new JButton(LanguageManager.get("brush.dilate", "Dilate"));
        JButton erodeBtn = new JButton(LanguageManager.get("brush.erode", "Erode"));
        dilateBtn.addActionListener(e -> applyMorph(true));
        erodeBtn.addActionListener(e -> applyMorph(false));
        morphPanel.add(dilateBtn);
        morphPanel.add(erodeBtn);
        mainPanel.add(morphPanel);

        add(mainPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * 選択中の Brush ROI に対して Dilate/Erode を適用。
     * Brush は太さで描画されるため、太さの増減で実現する。
     */
    private void applyMorph(boolean dilate) {
        BrowserController bc = BrowserController.currentBrowser();
        com.oculus.jj.ui.DCMView view = (bc != null) ? bc.getActiveDCMViewForEdit() : null;
        if (view == null) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("brush.morph.no.roi", "Select a Brush ROI first."),
                    LanguageManager.get("brush.title", "Brush"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean applied = view.applyBrushMorph(dilate, 1);
        if (!applied) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("brush.morph.no.roi", "Select a Brush ROI first."),
                    LanguageManager.get("brush.title", "Brush"), JOptionPane.WARNING_MESSAGE);
        }
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
