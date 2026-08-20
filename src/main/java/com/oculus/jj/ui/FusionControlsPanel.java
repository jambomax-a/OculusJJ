package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import com.oculus.jj.config.AppPreferences;
import com.oculus.jj.model.DicomPix;
import com.oculus.jj.model.FusionImageProcessor;
import com.oculus.jj.utils.LanguageManager;

/**
 * FusionControlsPanel — PET/CT 融合コントロールパネル。
 * - 融合モードドロップダウン (COLOR / ALPHA / GRAY)
 * - ブレンド重みスライダー (0.0〜1.0)
 * - 適用ボタン
 */
public class FusionControlsPanel extends JPanel {

    private static final Color BG = new Color(40, 40, 40);
    private static final Color FG = Color.WHITE;

    private JComboBox<String> modeCombo;
    private JSlider weightSlider;
    private JLabel weightLabel;
    private JButton applyButton, clearButton;

    // Callback interface
    public interface FusionListener {
        void onModeChanged(FusionImageProcessor.FusionMode mode);
        void onWeightChanged(double weight);
        void onApply(DicomPix base, DicomPix overlay);
        void onClear();
    }
    private FusionListener listener;

    public FusionControlsPanel() {
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        initComponents();
    }

    private void initComponents() {
        // Mode selection
        JPanel modePanel = new JPanel(new BorderLayout(8, 0));
        modePanel.setBackground(BG);
        JLabel modeLabel = new JLabel(LanguageManager.get("fusion.mode", "Mode:"));
        modeLabel.setForeground(FG);
        modeLabel.setFont(modeLabel.getFont().deriveFont(12f));

        String[] modes = { "COLOR", "ALPHA", "GRAY" };
        modeCombo = new JComboBox<>(modes);
        modeCombo.setSelectedItem(AppPreferences.getFusionMode().name());
        modeCombo.setBackground(new Color(60, 60, 60));
        modeCombo.setForeground(FG);
        modeCombo.setFont(modeCombo.getFont().deriveFont(11f));

        modePanel.add(modeLabel, BorderLayout.WEST);
        modePanel.add(modeCombo, BorderLayout.CENTER);
        add(modePanel);

        // Weight slider
        JPanel weightPanel = new JPanel(new BorderLayout(8, 0));
        weightPanel.setBackground(BG);
        JLabel wLabel = new JLabel(LanguageManager.get("fusion.blend", "Blend:"));
        wLabel.setForeground(FG);
        wLabel.setFont(wLabel.getFont().deriveFont(12f));

        weightSlider = new JSlider(SwingConstants.HORIZONTAL, 0, 1000, 500);
        weightSlider.setMajorTickSpacing(250);
        weightSlider.setMinorTickSpacing(100);
        weightSlider.setPaintLabels(true);
        weightSlider.setPaintTicks(true);
        weightSlider.putClientProperty("Slider.paintThumbShadow", Boolean.FALSE);
        weightSlider.setPreferredSize(new Dimension(280, 30));

        // Custom renderer for slider labels (show as 0.0-1.0)


        weightLabel = new JLabel("50%");
        weightLabel.setForeground(FG);
        weightLabel.setFont(weightLabel.getFont().deriveFont(11f));
        weightLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        weightPanel.add(wLabel, BorderLayout.WEST);
        weightPanel.add(weightSlider, BorderLayout.CENTER);
        weightPanel.add(weightLabel, BorderLayout.EAST);
        add(weightPanel);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        btnPanel.setBackground(BG);

        applyButton = new JButton("\u25B6 " + LanguageManager.get("fusion.apply", "Apply"));
        applyButton.setFont(applyButton.getFont().deriveFont(12f));
        applyButton.setPreferredSize(new Dimension(90, 28));
        applyButton.setBackground(new Color(60, 140, 60));
        applyButton.setForeground(Color.WHITE);
        applyButton.setFocusPainted(false);

        clearButton = new JButton("\u2715 " + LanguageManager.get("fusion.clear", "Clear"));
        clearButton.setFont(clearButton.getFont().deriveFont(12f));
        clearButton.setPreferredSize(new Dimension(90, 28));
        clearButton.setBackground(new Color(180, 60, 60));
        clearButton.setForeground(Color.WHITE);
        clearButton.setFocusPainted(false);

        btnPanel.add(applyButton);
        btnPanel.add(clearButton);
        add(btnPanel);
    }

    // --- Slider weight update listener (using plain ActionListener) ---
    static {
        // No static init needed
    }

    /** Set up the slider change listener (call once after construction) */
    public void attachSliderListener() {
        final int[] lastVal = {-1}; // box to capture in lambda
        weightSlider.addChangeListener(e -> {
            int val = weightSlider.getValue();
            if (val != lastVal[0]) {
                lastVal[0] = val;
                double w = val / 1000.0;
                weightLabel.setText(val + "%");
                if (listener != null) listener.onWeightChanged(w);
            }
        });
    }

    /** Set up the mode combo change listener */
    public void attachModeListener() {
        final String[] lastMode = {""};
        modeCombo.addActionListener(e -> {
            int idx = modeCombo.getSelectedIndex();
            if (idx >= 0 && idx < FusionImageProcessor.FusionMode.values().length) {
                String name = FusionImageProcessor.FusionMode.values()[idx].name();
                if (!name.equals(lastMode[0])) {
                    lastMode[0] = name;
                    AppPreferences.setFusionMode(FusionImageProcessor.FusionMode.valueOf(name));
                    if (listener != null) listener.onModeChanged(FusionImageProcessor.FusionMode.valueOf(name));
                }
            }
        });
    }

    /** Set up the Apply button listener */
    public void attachApplyListener() {
        applyButton.addActionListener(e -> {
            if (listener != null) {
                int idx = modeCombo.getSelectedIndex();
                FusionImageProcessor.FusionMode mode = FusionImageProcessor.FusionMode.values()[idx];
                double weight = weightSlider.getValue() / 1000.0;
                listener.onApply(null, null); // caller should fetch base/overlay themselves
            }
        });
    }

    public void attachClearListener() {
        clearButton.addActionListener(e -> {
            if (listener != null) listener.onClear();
        });
    }

    public FusionImageProcessor.FusionMode getSelectedMode() {
        int idx = modeCombo.getSelectedIndex();
        if (idx < 0 || idx >= FusionImageProcessor.FusionMode.values().length) return FusionImageProcessor.FusionMode.COLOR;
        return FusionImageProcessor.FusionMode.values()[idx];
    }

    public double getSelectedWeight() {
        return weightSlider.getValue() / 1000.0;
    }

    public void setFusionListener(FusionListener l) { this.listener = l; }

    /** Convenience: show as a modeless dialog */
    public static JDialog createAndShow(JFrame owner, DCMView baseView, DicomPix overlay) {
        if (baseView == null || overlay == null) return null;
        
        final FusionControlsPanel panel = new FusionControlsPanel();
        // Set initial values from preferences
        FusionImageProcessor.FusionMode currentMode = AppPreferences.getFusionMode();
        int modeIdx = ArrayUtil.indexOf(FusionImageProcessor.FusionMode.values(), currentMode);
        if (modeIdx >= 0) panel.modeCombo.setSelectedIndex(modeIdx);
        
        double currentWeight = AppPreferences.getFusionWeight();
        panel.weightSlider.setValue((int)(currentWeight * 1000));

        panel.attachSliderListener();
        panel.attachModeListener();
        panel.attachApplyListener();
        panel.attachClearListener();

        // Wire callbacks
        panel.setFusionListener(new FusionControlsPanel.FusionListener() {
            public void onModeChanged(FusionImageProcessor.FusionMode mode) {
                baseView.getDcmPixList().get(baseView.getCurImage()).setFusionMode(mode);
            }
            public void onWeightChanged(double weight) {
                baseView.getDcmPixList().get(baseView.getCurImage()).setFusionWeight(weight);
            }
            public void onApply(DicomPix base, DicomPix ovrlay) {
                double weight = panel.getSelectedWeight();
                FusionImageProcessor.FusionMode mode = panel.getSelectedMode();
                DicomPix currentBase = baseView.getDcmPixList().get(baseView.getCurImage());
                currentBase.setFusionOverlay(overlay, weight, mode);
                AppPreferences.setFusionMode(mode);
                AppPreferences.setFusionWeight(weight);
                if (owner instanceof java.awt.Window) { ((java.awt.Window)owner).repaint(); }
                baseView.repaint();
            }
            public void onClear() {
                baseView.getDcmPixList().get(baseView.getCurImage()).clearFusion();
                baseView.repaint();
            }
        });

        JDialog dlg = new JDialog((java.awt.Frame)owner, LanguageManager.get("fusion.title", "Fusion Controls"), java.awt.Dialog.ModalityType.MODELESS);
        dlg.add(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);

        dlg.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                // Fusion state is preserved on basePix even after dialog closes
            }
        });
        return dlg;
    }
}

/** Simple array utility */
class ArrayUtil {
    static int indexOf(Object[] arr, Object target) {
        if (arr == null || target == null) return -1;
        for (int i = 0; i < arr.length; i++) {
            if (target.equals(arr[i])) return i;
        }
        return -1;
    }
}