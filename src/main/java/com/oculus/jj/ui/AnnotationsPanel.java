package com.oculus.jj.ui;

import com.oculus.jj.model.DicomPix;
import com.oculus.jj.model.ROI;
import com.oculus.jj.utils.LanguageManager;
import com.oculus.jj.utils.UIStyle;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * AnnotationsPanel - アノテーション（ROI）管理パネル
 *
 * HOROS-20240407準拠: AnnotationsToolbarItemIdentifier = @"Annotations"
 * (ViewerController.m:182) - 現在のビューに表示されているアノテーション
 * （ROI/測定/角度/テキスト等）を一覧表示し、統計情報（面積・平均・SD・最小・最大）
 * を確認し、選択して削除できる。
 *
 * 単一インスタンス（シングルトン相当）として toggleAnnotationsPanel() から
 * 表示/非表示を切り替える。
 */
public class AnnotationsPanel extends JFrame {

    /** シングルトン相当のインスタンス */
    private static AnnotationsPanel instance = null;

    private DCMView sourceView = null;

    private JTable table;
    private DefaultTableModel tableModel;

    public AnnotationsPanel() {
        super(LanguageManager.get("annotations.title", "Annotations"));
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        setAlwaysOnTop(false);

        getContentPane().setBackground(UIStyle.BACKGROUND_COLOR);

        tableModel = new DefaultTableModel(
                new Object[]{
                        LanguageManager.get("annotations.col.type", "Type"),
                        LanguageManager.get("annotations.col.area", "Area"),
                        LanguageManager.get("annotations.col.mean", "Mean"),
                        LanguageManager.get("annotations.col.sd", "SD"),
                        LanguageManager.get("annotations.col.min", "Min"),
                        LanguageManager.get("annotations.col.max", "Max"),
                        LanguageManager.get("annotations.col.skew", "Skew"),
                        LanguageManager.get("annotations.col.kurt", "Kurt"),
                        LanguageManager.get("annotations.col.locked", "Locked")
                },
                0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(22);
        table.setFont(UIStyle.MAIN_FONT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(560, 360));
        add(scroll, BorderLayout.CENTER);

        // ===== ボタンバー =====
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton refreshBtn = new JButton(LanguageManager.get("annotations.refresh", "Refresh"));
        refreshBtn.setFont(UIStyle.MAIN_FONT);
        refreshBtn.addActionListener(e -> reload());

        JButton deleteBtn = new JButton(LanguageManager.get("annotations.delete", "Delete Selected"));
        deleteBtn.setFont(UIStyle.MAIN_FONT);
        deleteBtn.addActionListener(e -> deleteSelected());

        JButton closeBtn = new JButton(LanguageManager.get("annotations.close", "Close"));
        closeBtn.setFont(UIStyle.MAIN_FONT);
        closeBtn.addActionListener(e -> setVisible(false));

        buttonBar.add(deleteBtn);
        buttonBar.add(refreshBtn);
        buttonBar.add(closeBtn);
        add(buttonBar, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
            }
        });

        setSize(600, 440);
        setLocationRelativeTo(null);
    }

    /**
     * シングルトン相当のパネルを取得し、指定ビューに绑定して表示する。
     * 表示中なら非表示にする（toggle 動作）。
     */
    public static void toggle(Component owner, DCMView view) {
        if (instance == null) {
            instance = new AnnotationsPanel();
        }
        instance.sourceView = view;
        if (instance.isVisible()) {
            instance.setVisible(false);
        } else {
            instance.reload();
            instance.setVisible(true);
            instance.toFront();
        }
    }

    /** 現在のソースビューから ROI リストを再読み込みする */
    private void reload() {
        tableModel.setRowCount(0);
        if (sourceView == null) {
            return;
        }
        List<ROI> rois = sourceView.getCurRoiList();
        if (rois == null) {
            return;
        }
        for (ROI roi : rois) {
            if (roi == null) {
                continue;
            }
            boolean hasStats = roi.getType() != DCMView.ToolMode.tMesure
                    && roi.getType() != DCMView.ToolMode.tAngle
                    && roi.getType() != DCMView.ToolMode.tArrow
                    && roi.getType() != DCMView.ToolMode.tText;

            boolean showSkewKurt = com.oculus.jj.config.AppPreferences.getBool("viewers.skewKurt", false);
            double area = roi.getArea() / 100.0; // HOROS: formatArea(getArea() / 100.0)
            tableModel.addRow(new Object[]{
                    roi.getType() != null ? roi.getType().name() : "",
                    hasStats ? String.format("%.2f", area) : "-",
                    hasStats ? String.format("%.2f", roi.getMean()) : "-",
                    hasStats ? String.format("%.2f", roi.getStDev()) : "-",
                    hasStats ? String.format("%.1f", roi.getMin()) : "-",
                    hasStats ? String.format("%.1f", roi.getMax()) : "-",
                    (hasStats && showSkewKurt) ? String.format("%.3f", roi.getSkewness()) : "-",
                    (hasStats && showSkewKurt) ? String.format("%.2f", roi.getKurtosis()) : "-",
                    roi.isLocked() ? "✓" : ""
            });
        }
    }

    /** 選択中の ROI を削除する */
    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || sourceView == null) {
            return;
        }
        List<ROI> rois = sourceView.getCurRoiList();
        if (rois == null || row >= rois.size()) {
            return;
        }
        ROI roi = rois.get(row);
        DicomPix pix = sourceView.getCurDCM();
        if (pix != null) {
            pix.getROIs().remove(roi);
            sourceView.getCurRoiList(); // refresh
        }
        reload();
        sourceView.repaint();
    }

    /** 現在のソースビュー（表示用に保持） */
    public DCMView getSourceView() {
        return sourceView;
    }
}
