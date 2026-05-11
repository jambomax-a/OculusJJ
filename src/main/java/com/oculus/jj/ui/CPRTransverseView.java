package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * CPRTransverseView - 曲線に垂直な断面（横断面）を表示する小窓
 * HOROS-20240407 [CPRTransverseView] 相当
 */
public class CPRTransverseView extends JPanel {

    private CPRController controller;
    private String label;
    private BufferedImage image;
    private com.oculus.jj.model.ROI.Point3D centerPoint;

    public CPRTransverseView(CPRController controller, String label) {
        this.controller = controller;
        this.label = label;
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(150, 150));
    }

    public void setData(com.oculus.jj.model.ROI.Point3D point) {
        this.centerPoint = point;
        refresh();
    }

    public void refresh() {
        if (centerPoint == null)
            return;

        int size = 128;
        image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        float wl = controller.getWindowLevel();
        float ww = controller.getWindowWidth();
        double spacing = controller.getSpacingX();

        // 曲線上の点での TNB フレームを取得 (接線に垂直な面)
        int nodeIndex = 0;
        List<com.oculus.jj.model.ROI.Point3D> nodes = controller.getCurveNodes();
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) == centerPoint) {
                nodeIndex = i;
                break;
            }
        }
        com.oculus.jj.model.ROI.Point3D[] frame = controller.getTNBFrame(nodeIndex);
        if (frame == null)
            return;

        com.oculus.jj.model.ROI.Point3D normal = frame[1]; // Horizontal axis in slice
        com.oculus.jj.model.ROI.Point3D binormal = frame[2]; // Vertical axis in slice

        double rad = Math.toRadians(controller.getRotationAngle());
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx_loc = (x - size / 2.0) * spacing;
                double dy_loc = (y - size / 2.0) * spacing;

                // Rotate within the slice plane
                double dx = dx_loc * cos - dy_loc * sin;
                double dy = dx_loc * sin + dy_loc * cos;

                double sx = centerPoint.x + dx * normal.x + dy * binormal.x;
                double sy = centerPoint.y + dx * normal.y + dy * binormal.y;
                double sz = centerPoint.z + dx * normal.z + dy * binormal.z;

                double val = controller.getInterpolatedVoxelByPatient(sx, sy, sz);
                int gray = (int) ((val - (wl - ww / 2.0)) / ww * 255.0);
                if (gray < 0)
                    gray = 0;
                if (gray > 255)
                    gray = 255;
                image.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        }
        g.setColor(Color.YELLOW);
        g.drawString(label, 5, 15);
    }
}
