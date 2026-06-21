package com.oculus.jj.model;

import java.awt.*;
import com.oculus.jj.ui.DCMView;
import java.util.ArrayList;
import java.util.List;

/**
 * ShutterTool - 画像の特定領域を黒く塗りつぶすシャッターツール
 * クリック＆ドラッグで矩形シャッターを描画、複数シャッター対応
 */
public class ShutterTool {

    /** シャッター領域を表すクラス */
    public static class ShutterRegion {
        public int x, y, width, height;
        public Color color = Color.BLACK;
        public boolean visible = true;
        private String label;

        public ShutterRegion(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.width = w; this.height = h;
        }

        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        public boolean intersects(int x2, int y2, int w2, int h2) {
            return this.x < x2 + w2 && this.x + this.width > x2
                && this.y < y2 + h2 && this.y + this.height > y2;
        }

        public String getLabel() { return label != null ? label : ("Shutter"); }
    }

    private List<ShutterRegion> shutters = new ArrayList<>();
    private ShutterRegion pendingRegion = null; // ドラッグ中のシャッター
    private int startX = 0, startY = 0; // マウスダウン位置
    private boolean active = false;
    private DCMView ownerView;

    public ShutterTool(DCMView view) {
        this.ownerView = view;
    }

    /** マウスダウンイベント */
    public void mousePressed(int x, int y) {
        if (!active) return;
        startX = x; startY = y;
        pendingRegion = new ShutterRegion(x, y, 0, 0);
    }

    /** マウスドラッグイベント */
    public void mouseDragged(int x, int y) {
        if (pendingRegion == null) return;
        pendingRegion.x = Math.min(startX, x);
        pendingRegion.y = Math.min(startY, y);
        pendingRegion.width = Math.abs(x - startX);
        pendingRegion.height = Math.abs(y - startY);

        // オブザーバーにリペイントを依頼
        if (ownerView != null) ownerView.repaint();
    }

    /** マウスアップイベント */
    public void mouseReleased(int x, int y) {
        if (pendingRegion == null || pendingRegion.width < 2 && pendingRegion.height < 2) {
            pendingRegion = null;
            return;
        }

        // width/height を正の数に
        pendingRegion.width = Math.max(pendingRegion.width, 1);
        pendingRegion.height = Math.max(pendingRegion.height, 1);
        shutters.add(pendingRegion);
        pendingRegion = null;

        if (ownerView != null) ownerView.repaint();
    }

    /** シャッター描画 */
    public void paintOverlay(Graphics2D g2d) {
        for (ShutterRegion s : shutters) {
            if (!s.visible) continue;
            Color origColor = g2d.getColor();
            g2d.setColor(s.color);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
            g2d.fillRect(s.x, s.y, s.width, s.height);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g2d.setColor(origColor);
        }

        // ドラッグ中のシャッター（半透明でプレビュー）
        if (pendingRegion != null) {
            g2d.setColor(new Color(0, 0, 0, 128));
            g2d.fillRect(pendingRegion.x, pendingRegion.y,
                    pendingRegion.width, pendingRegion.height);
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(1.0f));
            g2d.drawRect(pendingRegion.x, pendingRegion.y,
                    pendingRegion.width, pendingRegion.height);
        }
    }

    /** シャッター数を返す */
    public int getShutterCount() { return shutters.size(); }

    /** 指定インデックスのシャッターをトグル（表示/非表示） */
    public void toggleShutter(int index) {
        if (index >= 0 && index < shutters.size()) {
            shutters.get(index).visible = !shutters.get(index).visible;
        }
    }

    /** 全シャッターを削除 */
    public void clearAll() {
        shutters.clear();
        pendingRegion = null;
        if (ownerView != null) ownerView.repaint();
    }

    // --- Properties for serialization / restoration ---
    public List<ShutterRegion> getShutters() { return new ArrayList<>(shutters); }
    public void setShutters(List<ShutterRegion> list) { shutters = new ArrayList<>(list); }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { active = a; }

    /** Owner view 参照を設定 */
    public void setOwnerView(DCMView view) { this.ownerView = view; }
}
