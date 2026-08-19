package com.oculus.jj.ui;

import com.oculus.jj.model.DicomPix;
import com.oculus.jj.utils.LanguageManager;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * NavigatorWindow — スライスナビゲータ（全フレームサムネイルストリップ）
 *
 * HOROS-20240407準拠: NavigatorWindowController.m + NavigatorView
 * (ViewerController.m:22787 - (IBAction) navigator:)
 *
 * 3Dデータシリーズの全スライスをサムネイルで横並びに表示し、
 * クリックでフレームをジャンプ。現在表示中のスライスをハイライト。
 * ビューアのインデックス変化をTimerでポーリングしてリアルタイム同期。
 * シングルトン相当: アクティブビューア毎に1つのウィンドウを再利用する。
 */
public class NavigatorWindow extends JDialog {

    private static final long serialVersionUID = 1L;

    /** サムネイルの描画幅（px） */
    private static final int THUMB_WIDTH = 96;
    private static final int THUMB_HEIGHT = 96;
    private static final int ROWS = 4;

    private final JPanel strip = new JPanel();
    private final Timer syncTimer;

    /** 現在表示中のビューアとインデックス（同期用） */
    private volatile DCMView boundView;
    private volatile int lastKnownIndex = -1;

    public NavigatorWindow() {
        super((java.awt.Frame) null);
        setTitle(LanguageManager.get("navigator.title", "Navigator"));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBackground(new Color(30, 30, 30));
        setResizable(false);

        strip.setLayout(new java.awt.GridLayout(ROWS, 0, 2, 2));
        strip.setOpaque(false);
        strip.setBackground(new Color(30, 30, 30));

        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(strip);
        scroll.setBackground(new Color(30, 30, 30));
        scroll.getViewport().setBackground(new Color(30, 30, 30));
        scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        add(scroll);

        // ビューアのフレーム変化に追随（HOROS: updateNavigator相当）
        syncTimer = new Timer(150, e -> syncHighlight());
        syncTimer.setRepeats(true);
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * ビューアをバインドし、サムネイルストリップを再構築して表示する。
     * HOROS: [[NavigatorWindowController navigatorWindowController] setViewer:viewer]
     */
    public void showForView(DCMView view) {
        List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || pixList.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("navigator.no_frames", "No images available for navigation."),
                    LanguageManager.get("navigator.title", "Navigator"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        this.boundView = view;
        this.lastKnownIndex = -1;

        SwingUtilities.invokeLater(() -> {
            strip.removeAll();
            for (int i = 0; i < pixList.size(); i++) {
                final int index = i;
                DicomPix pix = pixList.get(i);
                JButton btn = createThumbnailButton(index, pix);
                strip.add(btn);
            }
            strip.revalidate();
            strip.repaint();
            syncTimer.start();
            syncHighlight();
        });

        setAlwaysOnTop(true);
        if (!isVisible()) {
            setLocationRelativeTo(null);
        }
        setVisible(true);
        toFront();
    }

    /** サムネイルボタン生成（背景スレッド安全: 呼び出しはEDT） */
    private JButton createThumbnailButton(int index, DicomPix pix) {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(THUMB_WIDTH, THUMB_HEIGHT));
        btn.setMargin(new java.awt.Insets(0, 0, 0, 0));
        btn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70)));
        btn.setBackground(new Color(40, 40, 40));
        btn.setOpaque(true);
        btn.setToolTipText(LanguageManager.get("navigator.frame", "Slice") + " " + (index + 1));
        btn.setFocusable(false);
        btn.setRolloverEnabled(true);
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectIndex(index);
            }
        });
        // サムネイルは描画時に生成（遅延、EDT）
        btn.setIcon(new javax.swing.Icon() {
            private BufferedImage cached;

            @Override
            public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
                if (cached == null) {
                    cached = renderThumbnail(pix);
                }
                if (cached != null) {
                    g.drawImage(cached, x, y, c);
                } else {
                    g.setColor(new Color(50, 50, 50));
                    g.fillRect(x, y, getIconWidth(), getIconHeight());
                    g.setColor(Color.GRAY);
                    g.drawString(String.valueOf(index + 1), x + 6, y + getIconHeight() / 2);
                }
            }

            @Override
            public int getIconWidth() {
                return THUMB_WIDTH - 2;
            }

            @Override
            public int getIconHeight() {
                return THUMB_HEIGHT - 2;
            }
        });
        return btn;
    }

    /** DicomPixを小さなBufferedImageに変換（失敗時はnull） */
    private BufferedImage renderThumbnail(DicomPix pix) {
        try {
            BufferedImage img = pix.generateThumbnail(THUMB_WIDTH, THUMB_HEIGHT);
            if (img == null) {
                img = pix.getBufferedImageNoLoad();
            }
            if (img == null) {
                return null;
            }
            int w = img.getWidth(), h = img.getHeight();
            double scale = Math.min((double) (THUMB_WIDTH - 2) / w, (double) (THUMB_HEIGHT - 2) / h);
            int dw = Math.max(1, (int) (w * scale));
            int dh = Math.max(1, (int) (h * scale));
            BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img, 0, 0, dw, dh, null);
            g2.dispose();
            return out;
        } catch (Exception ex) {
            return null;
        }
    }

    /** サムネイルを選択 → ビューアのインデックスを設定（HOROS: navigatorViewのフレーム選択） */
    private void selectIndex(int index) {
        DCMView view = boundView;
        if (view == null) {
            return;
        }
        List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || index < 0 || index >= pixList.size()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            view.setIndex(index);
            view.repaint();
            syncHighlight();
        });
    }

    /** 現在インデックスのハイライト更新（Timerポーリング） */
    private void syncHighlight() {
        if (!isVisible() || boundView == null) {
            return;
        }
        int cur = boundView.getCurImage();
        if (cur == lastKnownIndex) {
            return;
        }
        lastKnownIndex = cur;
        int n = strip.getComponentCount();
        for (int i = 0; i < n; i++) {
            java.awt.Component c = strip.getComponent(i);
            if (c instanceof JButton b) {
                b.setForeground(i == cur ? new Color(0, 170, 255) : new Color(220, 220, 220));
                b.setBorder(BorderFactory.createLineBorder(
                        i == cur ? new Color(0, 170, 255) : new Color(70, 70, 70),
                        i == cur ? 2 : 1));
            }
        }
    }

    private volatile boolean disposed = false;

    /** DISPOSE_ON_CLOSE 後の再利用判定 */
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            syncTimer.stop();
            boundView = null;
            lastKnownIndex = -1;
        }
        super.setVisible(visible);
    }

    @Override
    public void dispose() {
        disposed = true;
        super.dispose();
    }
}
