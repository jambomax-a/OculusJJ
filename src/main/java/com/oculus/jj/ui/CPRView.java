package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * CPRView - Curved MPRの表示ビュー
 * HOROS-20240407 [CPRStraightenedOperation / CPRStretchedOperation] 相当の簡易実装
 */
public class CPRView extends JPanel {

    private CPRController controller;
    private boolean isStraightened;
    private BufferedImage image;
    private List<com.oculus.jj.model.ROI.Point3D> curveNodes = new ArrayList<>();

    public void setStraightened(boolean straightened) {
        this.isStraightened = straightened;
        refresh();
    }

    public void setPath(List<com.oculus.jj.model.ROI.Point3D> nodes) {
        this.curveNodes = new ArrayList<>(nodes);
        refresh();
    }

    public CPRView(CPRController controller, boolean isStraightened) {
        this.controller = controller;
        this.isStraightened = isStraightened;
        setBackground(Color.BLACK);

        MouseAdapter adapter = new MouseAdapter() {
            private int lastX, lastY;

            @Override
            public void mousePressed(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();

                // クリックした位置のノードを選択
                int x = e.getX();
                if (x >= 0 && x < curveNodes.size()) {
                    controller.setSelectedNodeIndex(x);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    // WL/WW
                    int dx = e.getX() - lastX;
                    int dy = e.getY() - lastY;
                    controller.setWindowWidth(controller.getWindowWidth() + dx * 4);
                    controller.setWindowLevel(controller.getWindowLevel() + dy * 4);
                    controller.updateViews();
                } else {
                    // Rotation
                    int dx = e.getX() - lastX;
                    controller.setRotationAngle(controller.getRotationAngle() + dx * 0.5);
                }
                lastX = e.getX();
                lastY = e.getY();
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    public void refresh() {
        generateImage();
        repaint();
    }

    private void generateImage() {
        if (curveNodes.size() < 2)
            return;

        List<com.oculus.jj.model.ROI.Point3D> path = curveNodes;

        int width = path.size();
        int height = 512; // 断面の高さを大きくする (Horos準拠)
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        float wl = controller.getWindowLevel();
        float ww = controller.getWindowWidth();

        for (int x = 0; x < width; x++) {
            com.oculus.jj.model.ROI.Point3D p = path.get(x);

            // 接線ベクトル(方向)を計算
            double dx = 0, dy = 0, dz = 1; // デフォルトはZ方向
            if (x < width - 1) {
                com.oculus.jj.model.ROI.Point3D next = path.get(x + 1);
                dx = next.x - p.x;
                dy = next.y - p.y;
                dz = next.z - p.z;
            } else if (x > 0) {
                com.oculus.jj.model.ROI.Point3D prev = path.get(x - 1);
                dx = p.x - prev.x;
                dy = p.y - prev.y;
                dz = p.z - prev.z;
            }

            double mag = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (mag > 0) {
                dx /= mag;
                dy /= mag;
                dz /= mag;
            }

            // 垂直ベクトル (Up vector) を計算
            // 曲線が主にXY平面上にある場合はZ軸を垂直方向として扱う
            // 汎用的には 接線 T に対して T x (任意) x T などで求める
            double ux = 0, uy = 0, uz = 1;
            if (Math.abs(dz) > 0.9) { // 接線がほぼ垂直な場合
                ux = 0;
                uy = 1;
                uz = 0;
            }

            // 接線 T と 基準軸 A から 水平ベクトル H = T x A
            double hx = dy * uz - dz * uy;
            double hy = dz * ux - dx * uz;
            double hz = dx * uy - dy * ux;
            double hmag = Math.sqrt(hx * hx + hy * hy + hz * hz);
            if (hmag > 0) {
                hx /= hmag;
                hy /= hmag;
                hz /= hmag;
            }

            // 垂直ベクトル V = H x T (これが実際のサンプリング軸)
            double vx = hy * dz - hz * dy;
            double vy = hz * dx - hx * dz;
            double vz = hx * dy - hy * dx;
            double vmag = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (vmag > 0) {
                vx /= vmag;
                vy /= vmag;
                vz /= vmag;
            }

            // 回転を適用
            double rad = Math.toRadians(controller.getRotationAngle());
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            // V' = V * cos + H * sin
            double rVx = vx * cos + hx * sin;
            double rVy = vy * cos + hy * sin;
            double rVz = vz * cos + hz * sin;

            // 高さ方向にサンプリング
            for (int y = 0; y < height; y++) {
                double offset = (y - height / 2.0) * controller.getSpacingX(); // 横の間隔を使用
                double sampleX = p.x + offset * rVx;
                double sampleY = p.y + offset * rVy;
                double sampleZ = p.z + offset * rVz;

                double val = controller.getInterpolatedVoxelByPatient(sampleX, sampleY, sampleZ);

                // WL/WW適用
                int gray = (int) ((val - (wl - ww / 2.0)) / ww * 255.0);
                if (gray < 0)
                    gray = 0;
                if (gray > 255)
                    gray = 255;

                image.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            // フィット表示 (アスペクト比維持)
            int pw = getWidth();
            int ph = getHeight();
            int iw = image.getWidth();
            int ih = image.getHeight();
            double scale = Math.min((double) pw / iw, (double) ph / ih);
            int dw = (int) (iw * scale);
            int dh = (int) (ih * scale);
            int ox = (pw - dw) / 2;
            int oy = (ph - dh) / 2;
            g.drawImage(image, ox, oy, dw, dh, null);
        } else {
            g.setColor(Color.WHITE);
            g.drawString("No Curve Defined. Draw a Curved ROI in 2D Viewer first.", 20, 40);
        }

        // --- Overlays ---
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.drawString(isStraightened ? "Straightened CPR" : "Stretched CPR", 10, 20);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.drawString(String.format("Angle: %.1f", controller.getRotationAngle()), 10, 40);

        // 下部情報
        g2.drawString("Made In JJViewer", getWidth() - 110, getHeight() - 10);
    }
}
