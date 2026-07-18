package com.oculus.jj.ui;

import com.oculus.jj.model.DicomSeries;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StatusDisplayController - HOROS-20240407準拠の Status & Comments パネル
 * 
 * Patient Name, Series Description, Key Image Path を表示する UI コントローラ。
 * HOROS の StatusView と同等の機能を実現します。
 */
public class StatusDisplayController {

    private static final Logger logger = LoggerFactory.getLogger(StatusDisplayController.class);

    private DicomSeries selectedSeries;
    private JLabel patientNameLabel;
    private JLabel seriesDescriptionLabel;
    private JLabel keyImagePathLabel;
    private JPanel statusPanel;

    public StatusDisplayController() {
        initializeUI();
    }

    /** HOROS-20240407準拠: initialize the Status view */
    private void initializeUI() {
        statusPanel = new JPanel(new BorderLayout(5, 5));
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // Header section
        JLabel headerLabel = new JLabel("Status & Comments");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
        statusPanel.add(headerLabel, BorderLayout.NORTH);

        // Patient Name
        patientNameLabel = new JLabel("-");
        statusPanel.add(createRow("Patient:", patientNameLabel), BorderLayout.CENTER);

        // Series Description
        seriesDescriptionLabel = new JLabel("-");
        statusPanel.add(createRow("Series:", seriesDescriptionLabel), BorderLayout.SOUTH);

        // Key Image Path (placeholder)
        keyImagePathLabel = new JLabel("-");
        statusPanel.add(createRow("Key Image:", keyImagePathLabel), BorderLayout.PAGE_END);

        statusPanel.setPreferredSize(new Dimension(250, 80));
    }

    private JPanel createRow(String label, JLabel value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JLabel lbl = new JLabel(label + " ");
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 11f));
        row.add(lbl);
        row.add(value);
        return row;
    }

    /** HOROS-20240407準拠: display status for selected series */
    public void updateStatus(DicomSeries series) {
        if (series == null) return;
        this.selectedSeries = series;

        // HOROS: Patient Name を DICOM metadata から取得（未実装時は placeholder）
        String patientName = "N/A";
        try {
            // TODO: DicomRepository/SqliteService から metadata 取得後ここに入れる
            patientName = "Pending Metadata Implementation";
        } catch (Exception e) {
            logger.warn("Failed to get patient name: {}", e.getMessage());
        }

        // HOROS: Series Description を series の説明から取得
        String description = series.getSeriesDescription();
        
        // HOROS: Key Image Path は系列の代表画像パス
        String keyImagePath = "N/A";
        try {
            // TODO: DicomRepository から key image path 取得後ここに入れる
            if (selectedSeries != null) {
                keyImagePath = "(Key Image Not Implemented)";
            }
        } catch (Exception e) {
            logger.warn("Failed to get key image path: {}", e.getMessage());
        }

        patientNameLabel.setText(patientName);
        seriesDescriptionLabel.setText(description != null ? description : "N/A");
        keyImagePathLabel.setText(keyImagePath);
    }

    public JPanel getStatusPanel() {
        return statusPanel;
    }

    /** HOROS-20240407準拠: clear / reset */
    public void clearStatus() {
        patientNameLabel.setText("-");
        seriesDescriptionLabel.setText("-");
        keyImagePathLabel.setText("-");
    }

    // Getters for testing
    public String getPatientName() { return patientNameLabel.getText(); }
}