package com.oculus.jj.ui;

import com.oculus.jj.model.ROI;
import com.oculus.jj.utils.LanguageManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * HOROS-20240407 compatible ROI Property Dialog
 */
public class ROIPropertyDialog extends JDialog {
    private ROI roi;
    private JComboBox<String> nameCombo;
    private JTextArea commentArea;
    private JSlider opacitySlider;
    private JSlider thicknessSlider;
    private JButton colorButton;
    private JCheckBox sameNameCheck;
    private Color selectedColor;

    public ROIPropertyDialog(Frame owner, ROI roi) {
        super(owner, "ROI Info", false);
        this.roi = roi;
        this.selectedColor = roi.getColor();

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel(LanguageManager.get("roi.name", "Name:")), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        String[] presets = { "Unnamed", "Tumor", "Nodule", "Cyst", "Calcification" };
        nameCombo = new JComboBox<>(presets);
        nameCombo.setEditable(true);
        nameCombo.setSelectedItem(roi.getName() != null && !roi.getName().isEmpty() ? roi.getName() : "Unnamed");
        nameCombo.addActionListener(e -> applyChanges());
        ((JTextField) nameCombo.getEditor().getEditorComponent()).getDocument()
                .addDocumentListener(new DocumentListener() {
                    public void insertUpdate(DocumentEvent e) {
                        applyChanges();
                    }

                    public void removeUpdate(DocumentEvent e) {
                        applyChanges();
                    }

                    public void changedUpdate(DocumentEvent e) {
                        applyChanges();
                    }
                });
        mainPanel.add(nameCombo, gbc);

        // Comments
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHEAST;
        mainPanel.add(new JLabel(LanguageManager.get("roi.comments", "Comments:")), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 0.5;
        gbc.fill = GridBagConstraints.BOTH;
        commentArea = new JTextArea(roi.getComments() != null ? roi.getComments() : "", 5, 20);
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        commentArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyChanges();
            }

            public void removeUpdate(DocumentEvent e) {
                applyChanges();
            }

            public void changedUpdate(DocumentEvent e) {
                applyChanges();
            }
        });
        mainPanel.add(new JScrollPane(commentArea), gbc);

        // Color & Checkbox
        gbc.gridy = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gbc.gridx = 0;
        mainPanel.add(new JLabel(LanguageManager.get("roi.color", "Color:")), gbc);
        gbc.gridx = 1;

        colorButton = new JButton(" ");
        colorButton.setPreferredSize(new Dimension(40, 25));
        colorButton.setBackground(selectedColor);
        colorButton.setOpaque(true);
        colorButton.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        colorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, LanguageManager.get("roi.choosecolor", "Choose ROI Color"), selectedColor);
            if (newColor != null) {
                selectedColor = newColor;
                colorButton.setBackground(selectedColor);
                applyChanges();
            }
        });
        colorRow.add(colorButton);
        colorRow.add(Box.createHorizontalStrut(20));
        sameNameCheck = new JCheckBox(LanguageManager.get("roi.all_samename", "All with same name"));
        colorRow.add(sameNameCheck);
        mainPanel.add(colorRow, gbc);

        // Thickness
        gbc.gridx = 0;
        gbc.gridy = 3;
        mainPanel.add(new JLabel(LanguageManager.get("roi.thickness", "Thickness:")), gbc);
        gbc.gridx = 1;
        thicknessSlider = new JSlider(1, 20,
                (int) (roi.getThickness() * (roi.getType() == DCMView.ToolMode.tBrush ? 1.0f : 2.0f)));
        thicknessSlider.addChangeListener(e -> applyChanges());
        mainPanel.add(thicknessSlider, gbc);

        // Opacity
        gbc.gridx = 0;
        gbc.gridy = 4;
        mainPanel.add(new JLabel(LanguageManager.get("roi.opacity", "Opacity:")), gbc);
        gbc.gridx = 1;
        opacitySlider = new JSlider(0, 100, (int) (roi.getOpacity() * 100));
        opacitySlider.addChangeListener(e -> applyChanges());
        mainPanel.add(opacitySlider, gbc);

        // Action Buttons Grid (matching screenshot)
        JPanel actionPanel = new JPanel(new GridLayout(3, 2, 8, 8));
        actionPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        actionPanel.add(new JButton(LanguageManager.get("roi.savefile", "Save as file")));
        actionPanel.add(new JButton(LanguageManager.get("roi.histogram", "Histogram")));
        actionPanel.add(new JButton(LanguageManager.get("roi.exportxml", "Export to XML")));
        JButton recalBtn = new JButton(LanguageManager.get("roi.recalibrate", "Recalibrate"));
        recalBtn.setEnabled(false);
        actionPanel.add(recalBtn);
        JButton xyBtn = new JButton(LanguageManager.get("roi.xyplot", "X-Y Plot"));
        xyBtn.setEnabled(false);
        actionPanel.add(xyBtn);
        // HOROS: Delete button is NOT in the property dialog. User uses
        // Delete/Backspace keys.

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        mainPanel.add(actionPanel, gbc);

        add(mainPanel, BorderLayout.CENTER);

        pack();
        setMinimumSize(new Dimension(380, 460));
        setLocationRelativeTo(getOwner());
    }

    private void applyChanges() {
        Object item = nameCombo.getEditor().getItem();
        String newName = item != null ? item.toString() : "";
        String oldName = roi.getName();

        roi.setName(newName);
        roi.setComments(commentArea.getText());
        roi.setColor(selectedColor);
        // Thickness logic
        if (roi.getType() == DCMView.ToolMode.tBrush) {
            roi.setThickness((float) thicknessSlider.getValue());
        } else if (roi.getType() == DCMView.ToolMode.tText) {
            // Text ROI: Map thickness to font size (12 is base, 2-40 range)
            roi.setThickness((float) thicknessSlider.getValue());
        } else {
            roi.setThickness(thicknessSlider.getValue() / 2.0f);
        }
        roi.setOpacity(opacitySlider.getValue() / 100.0f);

        // Sync ROIs with same name if checkbox is selected
        if (sameNameCheck.isSelected() && roi.getSourcePix() != null) {
            for (ROI r : roi.getSourcePix().getROIs()) {
                if (r.getName() != null && (r.getName().equals(newName) || r.getName().equals(oldName))) {
                    r.setColor(selectedColor);
                    r.setName(newName);
                    r.setThickness(roi.getThickness());
                    r.setOpacity(roi.getOpacity());
                }
            }
        }

        if (roi.getParentView() != null) {
            roi.getParentView().repaint();
        }
    }
}
