package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * NavigatorWindow - HOROS NavigatorWindowController 相当。
 * DCMView のミニマップ/ナビゲータパネル。
 */
public class NavigatorPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private ImageIcon thumbnailImage;
    private Rectangle visibleRect;
    private boolean showNavigator = false;

    public NavigatorPanel() {
        setOpaque(true);
        setBackground(new Color(30, 30, 30));
        setVisible(false);
        
        // Hover to expand
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                showNavigator = true;
                repaint();
            }
        });

        setPreferredSize(new Dimension(150, 120));
    }

    /** ナビゲータの表示/非表示 */
    public void toggle() {
        showNavigator = !showNavigator;
        setVisible(showNavigator);
        repaint();
    }

    /** サムネイル画像と可視領域をセット */
    public void setThumbnail(ImageIcon thumbnail, Rectangle visible) {
        this.thumbnailImage = thumbnail;
        this.visibleRect = visible;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!showNavigator || thumbnailImage == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Mini-map background
        g2.setColor(new Color(40, 40, 40));
        g2.fillRect(0, 0, w, h);

        // Draw thumbnail scaled to panel size
        int imgW = thumbnailImage.getIconWidth();
        int imgH = thumbnailImage.getIconHeight();
        float scale = (float) Math.min((w - 4) / (double) imgW, (h - 4) / (double) imgH);
        
        int drawW = (int)(imgW * scale);
        int drawH = (int)(imgH * scale);
        int xOff = (w - drawW) / 2;
        int yOff = (h - drawH) / 2;
        
        g2.drawImage(thumbnailImage.getImage(), xOff, yOff, drawW, drawH, this);

        // Draw visible rectangle overlay
        if (visibleRect != null) {
            double vrScaleX = drawW / (double) imgW;
            double vrScaleY = drawH / (double) imgH;
            
            int vrX = xOff + (int)(visibleRect.getX() * vrScaleX);
            int vrY = yOff + (int)(visibleRect.getY() * vrScaleY);
            int vrW = (int)(visibleRect.getWidth() * vrScaleX);
            int vrH = (int)(visibleRect.getHeight() * vrScaleY);

            g2.setColor(new Color(0, 180, 255, 120));
            g2.drawRect(vrX, vrY, vrW, vrH);
            
            // Make the border more visible
            g2.setColor(Color.CYAN);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(vrX + 1, vrY + 1, vrW - 2, vrH - 2);
        }

        // Label
        g2.setColor(new Color(200, 200, 200, 180));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2.drawString("Navigator", 4, 12);

        g2.dispose();
    }

    /** DCMView に統合するためのヘルパー */
    public static NavigatorPanel createForDCMView() {
        return new NavigatorPanel();
    }
}
