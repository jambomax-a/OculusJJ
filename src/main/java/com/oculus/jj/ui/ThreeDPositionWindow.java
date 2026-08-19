package com.oculus.jj.ui;

import com.oculus.jj.model.DicomPix;
import com.oculus.jj.utils.LanguageManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.util.List;

/**
 * ThreeDPositionWindow — 3D Position パネル（現在位置表示）
 *
 * HOROS-20240407準拠: ThreeDPositionController
 * (ViewerController.m:22812 - (IBAction) threeDPanel:)
 *
 * 現在表示中のスライスのインデックス・総スライス数・モダリティ・
 * 画像サイズをリアルタイム表示する小さな常時最前面ウィンドウ。
 */
public class ThreeDPositionWindow extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JLabel statusLabel = new JLabel();
    private final JLabel detailLabel = new JLabel();
    private final Timer syncTimer;
    private volatile DCMView boundView;
    private volatile String lastText = "";
    private volatile String lastDetail = "";

    public ThreeDPositionWindow() {
        super((java.awt.Frame) null);
        setTitle(LanguageManager.get("position3d.title", "3D Position"));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);
        setBackground(new Color(30, 30, 30));
        getRootPane().setOpaque(true);

        statusLabel.setFont(statusLabel.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        statusLabel.setForeground(new Color(0, 170, 255));
        detailLabel.setForeground(new Color(200, 200, 200));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setBackground(new Color(30, 30, 30));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        content.add(statusLabel);
        content.add(Box.createVerticalStrut(6));
        content.add(detailLabel);
        add(content);

        syncTimer = new Timer(200, e -> update());
        syncTimer.setRepeats(true);

        pack();
        setLocationRelativeTo(null);
    }

    public void showForView(DCMView view) {
        this.boundView = view;
        this.lastText = "";
        if (!isVisible()) {
            setLocationRelativeTo(null);
        }
        syncTimer.start();
        update();
        setVisible(true);
        toFront();
    }

    /** 現在位置情報を更新 */
    private void update() {
        if (!isVisible() || boundView == null) {
            return;
        }
        List<DicomPix> pixList = boundView.getDcmPixList();
        int total = (pixList == null) ? 0 : pixList.size();
        int cur = boundView.getCurImage();
        String modality = "";
        int w = 0, h = 0;
        DicomPix pix = (pixList != null && cur >= 0 && cur < pixList.size()) ? pixList.get(cur) : null;
        if (pix != null) {
            String m = pix.getModality();
            modality = (m == null) ? "" : m;
            w = pix.getWidth();
            h = pix.getHeight();
        }
        String status = LanguageManager.get("position3d.frame", "Slice") + " "
                + (cur + 1) + " / " + total;
        String detail = (modality.isEmpty() ? "" : modality + "  ")
                + (w > 0 ? (w + " x " + h) : "");
        if (status.equals(lastText) && detail.equals(lastDetail)) {
            return;
        }
        lastText = status;
        lastDetail = detail;
        statusLabel.setText(status);
        detailLabel.setText(detail);
    }

    private volatile boolean disposed = false;

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            syncTimer.stop();
            boundView = null;
        }
        super.setVisible(visible);
    }

    @Override
    public void dispose() {
        disposed = true;
        super.dispose();
    }
}
