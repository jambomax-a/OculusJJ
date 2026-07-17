package com.oculus.jj.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/**
 * NavigatorPanel - HOROS NavigatorWindowController / NavigatorView 相当。
 * DCMView のシリーズ全スライスをサムネイルで表示し、アクティブなスライス位置をハイライトする。
 */
public class NavigatorPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // HOROS-20240407準拠: NavigatorView.h thumbnailWidth / thumbnailHeight (68x75)
    private static final int THUMB_WIDTH = 68;
    private static final int THUMB_HEIGHT = 75;

    /** 全スライスのサムネイル画像 */
    private BufferedImage[] thumbImages;

    /** 現在のアクティブインデックス (-1 = 選択なし) */
    private int activeIndex = -1;

    public NavigatorPanel() {
        setOpaque(true);
        setBackground(new Color(35, 35, 35));
    }

    /** サムネイル画像とアクティブインデックスをセット（再描画） */
    public void setThumbnails(BufferedImage[] images, int activeIdx) {
        this.thumbImages = images;
        this.activeIndex = activeIdx;
        repaint();
    }

    /** アクティブインデックスだけを更新 */
    public void setActiveIndex(int activeIdx) {
        if (this.activeIndex == activeIdx) return;
        this.activeIndex = activeIdx;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (thumbImages == null || thumbImages.length == 0) {
            // 最小表示（ラベルのみ）
            g2.setColor(new Color(50, 50, 50));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(Color.GRAY.brighter());
            g2.setFont(g2.getFont().deriveFont(java.awt.Font.PLAIN, 10f));
            g2.drawString("Navigator", 6, 14);
            g2.dispose();
            return;
        }

        int w = getWidth();
        int h = getHeight();

        // ===== サムネイルを横に並べて描画 =====
        int totalThumbWidth = thumbImages.length * THUMB_WIDTH + (thumbImages.length - 1) * 3;
        int startX = (w - Math.min(totalThumbWidth, w)) / 2;
        if (startX < 0) startX = 0;

        for (int i = 0; i < thumbImages.length; i++) {
            BufferedImage img = thumbImages[i];
            float scaleX = THUMB_WIDTH / (float)(img != null ? img.getWidth() : 1);
            int scaledH = Math.max(1, (int)(img.getHeight() * scaleX));
            int drawY = h - scaledH;

            if (img != null) {
                g2.drawImage(img, startX + i * (THUMB_WIDTH + 3), drawY, THUMB_WIDTH, scaledH, this);
            } else {
                g2.setColor(new Color(40, 40, 40));
                g2.fillRect(startX + i * (THUMB_WIDTH + 3), drawY, THUMB_WIDTH, scaledH);
            }
        }

        // ===== アクティブスライスのハイライト =====
        if (activeIndex >= 0 && activeIndex < thumbImages.length) {
            int x = startX + activeIndex * (THUMB_WIDTH + 3);
            g2.setColor(new Color(10, 140, 220));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(x, h - THUMB_HEIGHT, THUMB_WIDTH, THUMB_HEIGHT);

            // アクティブラベル
            String label = "Slide " + (activeIndex + 1) + "/" + thumbImages.length;
            java.awt.FontMetrics fm = g2.getFontMetrics(g2.getFont().deriveFont(java.awt.Font.BOLD, 8f));
            int tw = fm.stringWidth(label);
            // Label background with rounded rect
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRoundRect(x + 2, h - THUMB_HEIGHT - 14, tw + 6, 13, 3, 3);
            g2.setColor(Color.WHITE);
            g2.setFont(g2.getFont().deriveFont(java.awt.Font.BOLD, 8f));
            g2.drawString(label, x + 5, h - THUMB_HEIGHT - 4);
        }

        // ===== ラベル（左上） =====
        if (thumbImages != null && thumbImages.length > 0) {
            g2.setColor(Color.GRAY.brighter());
            g2.setFont(g2.getFont().deriveFont(java.awt.Font.PLAIN, 10f));
            g2.drawString("Navigator", 4, 12);
        }

        g2.dispose();
    }

    /** DCMView に統合するためのヘルパー */
    public static NavigatorPanel createForDCMView() {
        return new NavigatorPanel();
    }
}