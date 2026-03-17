package com.jj.dicomviewer.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Point2D;
import java.util.List;
import com.jj.dicomviewer.model.DicomPix;
import com.jj.dicomviewer.ui.DCMView.ToolMode;

/**
 * CPROrthogonalView - CPRウィンドウ内の直交断面表示
 * HOROS-20240407 [CPRMPRDCMView] 相当
 */
public class CPROrthogonalView extends JPanel {

    public enum Direction {
        AXIAL, SAGITTAL, CORONAL
    }

    private Direction direction;
    private CPRController controller;
    private BufferedImage image;

    private int mouseMode = 0; // 0: Pan, 1: Rotate, 2: MoveCenter
    private static final int HIT_TOLERANCE = 10;

    private double zoom = 1.0;
    private int panX = 0;
    private int panY = 0;

    private double[] projectAndNormalize(double[] v, double[] n) {
        double dot = v[0] * n[0] + v[1] * n[1] + v[2] * n[2];
        double[] res = { v[0] - dot * n[0], v[1] - dot * n[1], v[2] - dot * n[2] };
        double l = Math.sqrt(res[0] * res[0] + res[1] * res[1] + res[2] * res[2]);
        if (l < 1e-9)
            return v; // 平行な場合は元のベクトルを返す
        res[0] /= l;
        res[1] /= l;
        res[2] /= l;
        return res;
    }

    private double[][] getSamplingBasis() {
        double[] vS = controller.getVS();
        double[] vC = controller.getVC();
        double[] vA = controller.getVA();
        double[] vn, vr, vd;
        switch (direction) {
            case AXIAL:
                vn = vA;
                vr = projectAndNormalize(new double[] { 1, 0, 0 }, vn);
                vd = projectAndNormalize(new double[] { 0, 1, 0 }, vn);
                break;
            case SAGITTAL:
                vn = vS;
                vr = projectAndNormalize(new double[] { 0, 1, 0 }, vn);
                vd = projectAndNormalize(new double[] { 0, 0, -1 }, vn);
                break;
            case CORONAL:
                vn = vC;
                vr = projectAndNormalize(new double[] { 1, 0, 0 }, vn);
                vd = projectAndNormalize(new double[] { 0, 0, -1 }, vn);
                break;
            default:
                return null;
        }
        return new double[][] { vn, vr, vd };
    }

    public CPROrthogonalView(CPRController controller, Direction direction) {
        this.controller = controller;
        this.direction = direction;
        setBackground(Color.BLACK);

        MouseAdapter adapter = new MouseAdapter() {
            private int lastMouseX;
            private int lastMouseY;

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int current = controller.getSlice(direction);
                int next = current + e.getWheelRotation();
                int max = controller.getDim(direction, 2) - 1;
                if (next < 0)
                    next = 0;
                if (next > max)
                    next = max;
                controller.setSlice(direction, next);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();

                // --- Curved ROI Drawing (Path Redefinition) ---
                if (DCMView.currentTool == ToolMode.tCurvedROI) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        double[] worldPt = convertScreenToWorld(e.getX(), e.getY());
                        if (worldPt != null) {
                            if (e.getClickCount() == 2) {
                                // Double click to finish (already added points)
                                DCMView.currentTool = ToolMode.tPlain;
                            } else {
                                // Add a node
                                controller.addNode(
                                        new com.jj.dicomviewer.model.ROI.Point3D(worldPt[0], worldPt[1], worldPt[2]));
                                // Add a tracker node for mouseMoved
                                controller.addNode(
                                        new com.jj.dicomviewer.model.ROI.Point3D(worldPt[0], worldPt[1], worldPt[2]));
                            }
                            repaint();
                        }
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        // Right click to clear
                        controller.clearCurve();
                        repaint();
                    }
                    return;
                }

                if (SwingUtilities.isLeftMouseButton(e)) {
                    determineMouseMode(e.getX(), e.getY());
                }
                handleMouse(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (DCMView.currentTool == ToolMode.tCurvedROI) {
                    double[] worldPt = convertScreenToWorld(e.getX(), e.getY());
                    if (worldPt != null) {
                        controller.updateLastNode(
                                new com.jj.dicomviewer.model.ROI.Point3D(worldPt[0], worldPt[1], worldPt[2]));
                        repaint();
                    }
                }
                updateCursor(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mouseMode = 0;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouse(e);
            }

            private void determineMouseMode(int x, int y) {
                Point center = getCrosshairScreenPos();
                if (center != null) {
                    // 1. 中心移動 (Move Center) の判定
                    if (center.distance(x, y) < HIT_TOLERANCE) {
                        mouseMode = 2; // Move
                        return;
                    }

                    // 2. 黄色のサンプリング面線 (Cut Line) の判定
                    if (isNearCutLine(x, y)) {
                        mouseMode = 3; // Rotate Curve Plane
                        return;
                    }

                    // 3. 十字線の判定 (Horos準拠：線の上で回転)
                    if (isNearCrosshairs(x, y)) {
                        mouseMode = 1; // Rotate MPR
                        return;
                    }
                }
                // 背景クリック: Pan (0)
                mouseMode = 0;
            }

            private boolean isNearCrosshairs(int x, int y) {
                Point c = getCrosshairScreenPos();
                if (c == null)
                    return false;

                double[][] basis = getSamplingBasis();
                double[] vn = basis[0], vx = basis[1], vy = basis[2];

                double[] vS = controller.getVS();
                double[] vC = controller.getVC();
                double[] vA = controller.getVA();

                switch (direction) {
                    case AXIAL:
                        return isNearProjectedLine(x, y, c, vx, vy, vn, vC) ||
                                isNearProjectedLine(x, y, c, vx, vy, vn, vS);
                    case SAGITTAL:
                        return isNearProjectedLine(x, y, c, vx, vy, vn, vA) ||
                                isNearProjectedLine(x, y, c, vx, vy, vn, vC);
                    case CORONAL:
                        return isNearProjectedLine(x, y, c, vx, vy, vn, vA) ||
                                isNearProjectedLine(x, y, c, vx, vy, vn, vS);
                }
                return false;
            }

            private boolean isNearProjectedLine(int px, int py, Point c, double[] vr, double[] vd, double[] n1,
                    double[] n2) {
                // 交差線の方向 L = n1 x n2
                double lx = n1[1] * n2[2] - n1[2] * n2[1];
                double ly = n1[2] * n2[0] - n1[0] * n2[2];
                double lz = n1[0] * n2[1] - n1[1] * n2[0];

                double dx = lx * vr[0] + ly * vr[1] + lz * vr[2];
                double dy = lx * vd[0] + ly * vd[1] + lz * vd[2];

                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < 1e-6)
                    return false;
                dx /= d;
                dy /= d;

                double dist = Math.abs(-(px - c.x) * dy + (py - c.y) * dx);
                return dist < HIT_TOLERANCE;
            }

            private boolean isNearCutLine(int x, int y) {
                Point center = getCrosshairScreenPos();
                if (center == null)
                    return false;

                int nodeIdx = controller.getSelectedNodeIndex();
                com.jj.dicomviewer.model.ROI.Point3D[] frame = controller.getTNBFrame(nodeIdx);
                if (frame == null)
                    return false;

                com.jj.dicomviewer.model.ROI.Point3D normal = frame[1];
                double[][] basis = getSamplingBasis();
                double[] vn = basis[0], vx = basis[1], vy = basis[2];

                return isNearProjectedLine(x, y, center, vx, vy, vn, new double[] { normal.x, normal.y, normal.z });
            }

            private double distanceToPoint(int x1, int y1, int x2, int y2) {
                return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
            }

            private double distanceToLine(int px, int py, int x1, int y1, int x2, int y2) {
                double l2 = Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2);
                if (l2 == 0)
                    return distanceToPoint(px, py, x1, y1);
                double t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2;
                t = Math.max(0, Math.min(1, t));
                return distanceToPoint(px, py, (int) (x1 + t * (x2 - x1)), (int) (y1 + t * (y2 - y1)));
            }

            private double[] convertScreenToWorld(int sx, int sy) {
                int pw = getWidth();
                int ph = getHeight();
                int iw = controller.getDim(direction, 0);
                int ih = controller.getDim(direction, 1);

                double scale = Math.min((double) pw / iw, (double) ph / ih);
                int ox = (pw - (int) (iw * scale)) / 2;
                int oy = (ph - (int) (ih * scale)) / 2;

                double ix = (sx - (ox + panX)) / (scale * zoom);
                double iy = (sy - (oy + panY)) / (scale * zoom);

                double cx = controller.getCrosshairX();
                double cy = controller.getCrosshairY();
                double cz = controller.getCrosshairZ();

                double startX = 0, startY = 0;
                switch (direction) {
                    case AXIAL:
                        startX = cx;
                        startY = cy;
                        break;
                    case SAGITTAL:
                        startX = cy;
                        startY = (controller.getDimZ() - 1) - cz;
                        break;
                    case CORONAL:
                        startX = cx;
                        startY = (controller.getDimZ() - 1) - cz;
                        break;
                }

                double[][] basis = getSamplingBasis();
                double[] vx_basis = basis[1], vy_basis = basis[2];

                double dx = ix - startX;
                double dy = iy - startY;

                double px = cx + dx * vx_basis[0] + dy * vy_basis[0];
                double py = cy + dx * vx_basis[1] + dy * vy_basis[1];
                double pz = cz + dx * vx_basis[2] + dy * vy_basis[2];

                // HOROS-20240407準拠: インデックス空間から世界座標(mm)への変換 (Orientationを考慮)
                double[] orient = controller.getOrientation();
                double wx = controller.getOriginX() + px * controller.getSpacingX() * orient[0]
                        + py * controller.getSpacingY() * orient[3] + pz * controller.getSpacingZ() * orient[6];
                double wy = controller.getOriginY() + px * controller.getSpacingX() * orient[1]
                        + py * controller.getSpacingY() * orient[4] + pz * controller.getSpacingZ() * orient[7];
                double wz = controller.getOriginZ() + px * controller.getSpacingX() * orient[2]
                        + py * controller.getSpacingY() * orient[5] + pz * controller.getSpacingZ() * orient[8];

                return new double[] { wx, wy, wz };
            }

            private void updateCursor(int x, int y) {
                if (DCMView.currentTool == ToolMode.tCurvedROI) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    return;
                }
                Point center = getCrosshairScreenPos();
                if (center != null) {
                    if (center.distance(x, y) < HIT_TOLERANCE) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                        return;
                    }
                    if (isNearCutLine(x, y) || isNearCrosshairs(x, y)) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        return;
                    }
                }
                // 背景：画像移動（Pan）
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            private Point getCrosshairScreenPos() {
                int pw = getWidth();
                int ph = getHeight();
                int iw = controller.getDim(direction, 0);
                int ih = controller.getDim(direction, 1);
                double scale = Math.min((double) pw / iw, (double) ph / ih);
                int ox = (pw - (int) (iw * scale)) / 2;
                int oy = (ph - (int) (ih * scale)) / 2;
                return CPROrthogonalView.this.getCrosshairScreenPos(ox, oy, scale);
            }

            private void handleMouse(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    // Right drag: Zoom (Horos parity as requested)
                    int dy = e.getY() - lastMouseY;
                    zoom *= Math.pow(1.01, -dy);
                    if (zoom < 0.4)
                        zoom = 0.4;
                    if (zoom > 10.0)
                        zoom = 10.0;
                    repaint();
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    return;
                }

                // 2. パンニング (中ボタン または Ctrl+左ドラッグ)
                boolean isPan = SwingUtilities.isMiddleMouseButton(e) ||
                        (SwingUtilities.isLeftMouseButton(e) && e.isControlDown());

                if (isPan || (SwingUtilities.isLeftMouseButton(e) && mouseMode == 0)) {
                    panX += (e.getX() - lastMouseX);
                    panY += (e.getY() - lastMouseY);
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                    return;
                }

                // 3. 中心移動 (左ドラッグ かつ mouseMode == 2)
                if (SwingUtilities.isLeftMouseButton(e) && mouseMode == 2) {
                    double angleRad = Math.toRadians(controller.getRotationAngle());
                    double cos = Math.cos(angleRad);
                    double sin = Math.sin(angleRad);

                    int pw = getWidth();
                    int ph = getHeight();
                    int iw = controller.getDim(direction, 0);
                    int ih = controller.getDim(direction, 1);
                    double scale = Math.min((double) pw / iw, (double) ph / ih);

                    // マウスのピクセル移動量
                    double dpx = (e.getX() - lastMouseX) / (scale * zoom);
                    double dpy = (e.getY() - lastMouseY) / (scale * zoom);

                    double vx = 0, vy = 0, vz = 0;

                    // ビューごとの基底ベクトルに基づいた移動量の変換
                    switch (direction) {
                        case AXIAL:
                            // Axial: X軸(1,0,0), Y軸(0,1,0)
                            vx = dpx;
                            vy = dpy;
                            break;
                        case SAGITTAL:
                            // Sagittal: 横軸は (-sin, cos, 0), 縦軸は (0, 0, -1)
                            vx = dpx * (-sin);
                            vy = dpx * cos;
                            vz = -dpy;
                            break;
                        case CORONAL:
                            // Coronal: 横軸は (cos, sin, 0), 縦軸は (0, 0, -1)
                            vx = dpx * cos;
                            vy = dpx * sin;
                            vz = -dpy;
                            break;
                    }

                    controller.setCrosshairCenter(
                            controller.getCrosshairX() + vx,
                            controller.getCrosshairY() + vy,
                            controller.getCrosshairZ() + vz);

                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                    return;
                }

                // 4. 回転 (左ドラッグ かつ mouseMode == 1)
                if (SwingUtilities.isLeftMouseButton(e) && mouseMode == 1) {
                    Point center = getCrosshairScreenPos();
                    if (center != null) {
                        double angle = Math.atan2(e.getY() - center.y, e.getX() - center.x);
                        double lastAngle = Math.atan2(lastMouseY - center.y, lastMouseX - center.x);
                        double delta = Math.toDegrees(angle - lastAngle);

                        // 基底ベクトルを回転させる (これにより全ビューが同期して画像遷移する)
                        controller.rotateMPR(direction, delta);

                        lastMouseX = e.getX();
                        lastMouseY = e.getY();
                        repaint();
                    }
                }
                // 5. 曲線の回転 (左ドラッグ かつ mouseMode == 3)
                if (SwingUtilities.isLeftMouseButton(e) && mouseMode == 3) {
                    Point center = getCrosshairScreenPos();
                    if (center != null) {
                        double angle = Math.atan2(e.getY() - center.y, e.getX() - center.x);
                        double lastAngle = Math.atan2(lastMouseY - center.y, lastMouseX - center.x);
                        double delta = Math.toDegrees(angle - lastAngle);

                        // 曲線のサンプリング角度を更新
                        controller.setRotationAngle(controller.getRotationAngle() + delta);

                        lastMouseX = e.getX();
                        lastMouseY = e.getY();
                        repaint();
                    }
                }
            }
        };
        addMouseWheelListener(adapter);
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    public void refresh() {
        generateImage();
        repaint();
    }

    private void generateImage() {
        int w = controller.getDim(direction, 0);
        int h = controller.getDim(direction, 1);
        if (w <= 0 || h <= 0)
            return;

        image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        float wl = controller.getWindowLevel();
        float ww = controller.getWindowWidth();

        double cx = controller.getCrosshairX();
        double cy = controller.getCrosshairY();
        double cz = controller.getCrosshairZ();

        double[][] basis = getSamplingBasis();
        double[] vx = basis[1], vy = basis[2];

        double startX = 0, startY = 0;
        switch (direction) {
            case AXIAL:
                startX = cx;
                startY = cy;
                break;
            case SAGITTAL:
                startX = cy;
                startY = (controller.getDimZ() - 1) - cz;
                break;
            case CORONAL:
                startX = cx;
                startY = (controller.getDimZ() - 1) - cz;
                break;
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - startX;
                double dy = y - startY;
                double px = cx + dx * vx[0] + dy * vy[0];
                double py = cy + dx * vx[1] + dy * vy[1];
                double pz = cz + dx * vx[2] + dy * vy[2];

                double val = controller.getInterpolatedVoxel(px, py, pz);
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
        // refresh() で生成済みのため、ここでは呼び出さない (パフォーマンス向上のため)
        if (image == null)
            return;

        Graphics2D g2 = (Graphics2D) g;
        int pw = getWidth();
        int ph = getHeight();
        int iw = image.getWidth();
        int ih = image.getHeight();

        double scale = Math.min((double) pw / iw, (double) ph / ih);
        int dw = (int) (iw * scale);
        int dh = (int) (ih * scale);
        int ox = (pw - dw) / 2;
        int oy = (ph - dh) / 2;

        g2.drawImage(image, ox + panX, oy + panY, (int) (dw * zoom), (int) (dh * zoom), null);

        // --- クロスヘア表示 (線が回転し、画像が遷移するHoros準拠) ---
        g2.setStroke(new BasicStroke(1.5f));
        Color axis1Color = new Color(255, 171, 0); // Axial (Orange)
        Color axis2Color = new Color(153, 0, 255); // Coronal (Purple)
        Color axis3Color = new Color(0, 128, 255); // Sagittal (Blue)

        Point center = getCrosshairScreenPos(ox, oy, scale);
        int lineLen = Math.max(pw, ph);

        double[][] basis = getSamplingBasis();
        double[] vn = basis[0], vx = basis[1], vy = basis[2];

        // 各ビューの法線
        double[] vS = controller.getVS();
        double[] vC = controller.getVC();
        double[] vA = controller.getVA();

        switch (direction) {
            case AXIAL:
                g2.setColor(axis2Color); // Coronal (Purple)
                drawProjectedLine(g2, center, vx, vy, vn, vC, lineLen);
                g2.setColor(axis3Color); // Sagittal (Blue)
                drawProjectedLine(g2, center, vx, vy, vn, vS, lineLen);
                break;
            case SAGITTAL:
                g2.setColor(axis1Color); // Axial (Orange)
                drawProjectedLine(g2, center, vx, vy, vn, vA, lineLen);
                g2.setColor(axis2Color); // Coronal (Purple)
                drawProjectedLine(g2, center, vx, vy, vn, vC, lineLen);
                break;
            case CORONAL:
                g2.setColor(axis1Color); // Axial (Orange)
                drawProjectedLine(g2, center, vx, vy, vn, vA, lineLen);
                g2.setColor(axis3Color); // Sagittal (Blue)
                drawProjectedLine(g2, center, vx, vy, vn, vS, lineLen);
                break;
        }

        // Selected Node と CPR Cut Line の描画
        drawCurve(g2, ox, oy, scale);
        drawROIs(g2, ox, oy, scale);
        drawCutLine(g2, ox, oy, scale);
        drawCenterDot(g2, ox, oy, scale);
    }

    private void drawProjectedLine(Graphics2D g2, Point c, double[] vr, double[] vd, double[] n1, double[] n2,
            int len) {
        // 交差線の方向 L = n1 x n2
        double lx = n1[1] * n2[2] - n1[2] * n2[1];
        double ly = n1[2] * n2[0] - n1[0] * n2[2];
        double lz = n1[0] * n2[1] - n1[1] * n2[0];

        // L を画面の右ベクトル vr と下ベクトル vd に投影
        double dx = lx * vr[0] + ly * vr[1] + lz * vr[2];
        double dy = lx * vd[0] + ly * vd[1] + lz * vd[2];

        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < 1e-6)
            return;
        dx = (dx / d) * len;
        dy = (dy / d) * len;
        g2.drawLine((int) (c.x - dx), (int) (c.y - dy), (int) (c.x + dx), (int) (c.y + dy));
    }

    private void drawCenterDot(Graphics2D g2, int ox, int oy, double scale) {
        Point c = getCrosshairScreenPos(ox, oy, scale);
        if (c == null)
            return;

        g2.setColor(Color.WHITE);
        g2.fillOval(c.x - 3, c.y - 3, 6, 6);
        g2.setColor(Color.BLACK);
        g2.drawOval(c.x - 3, c.y - 3, 6, 6);
    }

    private void drawROIs(Graphics2D g2, int ox, int oy, double scale) {
        List<DicomPix> pixList = controller.getPixList();
        if (pixList == null)
            return;

        double cx = controller.getCrosshairX();
        double cy = controller.getCrosshairY();
        double cz = controller.getCrosshairZ();

        double[][] basis = getSamplingBasis();
        double[] vn = basis[0], vx = basis[1], vy = basis[2];

        Point center = getCrosshairScreenPos(ox, oy, scale);

        for (int k = 0; k < pixList.size(); k++) {
            DicomPix pix = pixList.get(k);
            if (pix == null)
                continue;
            List<com.jj.dicomviewer.model.ROI> rois = pix.getROIs();
            if (rois == null)
                continue;

            for (com.jj.dicomviewer.model.ROI roi : rois) {
                // Curved ROIそのものはdrawCurveで個別に描画されるため、ここではスキップ
                if (roi.getType() == ToolMode.tCurvedROI)
                    continue;

                List<Point2D.Float> points = roi.getPoints();
                if (points == null || points.isEmpty())
                    continue;

                Color roiColor = roi.getColor();
                g2.setStroke(new BasicStroke(roi.getThickness()));

                for (int i = 0; i < (roi.isClosed() ? points.size() : points.size() - 1); i++) {
                    Point2D.Float p1 = points.get(i);
                    Point2D.Float p2 = points.get((i + 1) % points.size());

                    // ボクセル座標 (x, y, k)
                    double v1x = p1.x;
                    double v1y = p1.y;
                    double v1z = k;

                    double v2x = p2.x;
                    double v2y = p2.y;
                    double v2z = k;

                    // 断面からの距離を計算（ボクセル単位）
                    double d1 = (v1x - cx) * vn[0] + (v1y - cy) * vn[1] + (v1z - cz) * vn[2];
                    double d2 = (v2x - cx) * vn[0] + (v2y - cy) * vn[1] + (v2z - cz) * vn[2];

                    double avgDist = Math.abs((d1 + d2) / 2.0);
                    float alpha = 1.0f;
                    if (avgDist <= 0.5) {
                        alpha = 1.0f;
                    } else if (avgDist >= 1.0) {
                        alpha = 0.2f;
                    } else {
                        alpha = (float) (avgDist * -1.6 + 1.8);
                    }

                    if (alpha < 0.05f)
                        continue;

                    g2.setColor(
                            new Color(roiColor.getRed() / 255f, roiColor.getGreen() / 255f, roiColor.getBlue() / 255f,
                                    alpha));

                    double dx1 = (v1x - cx) * vx[0] + (v1y - cy) * vx[1] + (v1z - cz) * vx[2];
                    double dy1 = (v1x - cx) * vy[0] + (v1y - cy) * vy[1] + (v1z - cz) * vy[2];
                    double dx2 = (v2x - cx) * vx[0] + (v2y - cy) * vx[1] + (v2z - cz) * vx[2];
                    double dy2 = (v2x - cx) * vy[0] + (v2y - cy) * vy[1] + (v2z - cz) * vy[2];

                    g2.drawLine((int) (center.x + dx1 * scale * zoom), (int) (center.y + dy1 * scale * zoom),
                            (int) (center.x + dx2 * scale * zoom), (int) (center.y + dy2 * scale * zoom));
                }
            }
        }
    }

    private Point getCrosshairScreenPos(int ox, int oy, double scale) {
        double cx = controller.getCrosshairX();
        double cy = controller.getCrosshairY();
        double cz = controller.getCrosshairZ();
        double imgX = 0, imgY = 0;

        switch (direction) {
            case AXIAL:
                imgX = cx;
                imgY = cy;
                break;
            case SAGITTAL:
                imgX = cy;
                imgY = (controller.getDimZ() - 1) - cz;
                break;
            case CORONAL:
                imgX = cx;
                imgY = (controller.getDimZ() - 1) - cz;
                break;
        }
        return new Point((int) (ox + imgX * scale * zoom + panX), (int) (oy + imgY * scale * zoom + panY));
    }

    private void drawCurve(Graphics2D g2, int ox, int oy, double scale) {
        List<com.jj.dicomviewer.model.ROI.Point3D> curveNodes = controller.getCurveNodes();
        if (curveNodes == null || curveNodes.isEmpty())
            return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // HOROS-20240407 parity: thickness 3.0
        g2.setStroke(new BasicStroke(3.0f));

        double cx = controller.getCrosshairX();
        double cy = controller.getCrosshairY();
        double cz = controller.getCrosshairZ();

        double[][] basis = getSamplingBasis();
        double[] vn = basis[0], vx = basis[1], vy = basis[2];

        Point center = getCrosshairScreenPos(ox, oy, scale);

        Color baseColor = Color.YELLOW;

        for (int i = 0; i < curveNodes.size() - 1; i++) {
            com.jj.dicomviewer.model.ROI.Point3D p1 = curveNodes.get(i);
            com.jj.dicomviewer.model.ROI.Point3D p2 = curveNodes.get(i + 1);

            double[] idx1 = { (p1.x - controller.getOriginX()) / controller.getSpacingX(),
                    (p1.y - controller.getOriginY()) / controller.getSpacingY(),
                    (p1.z - controller.getOriginZ()) / controller.getSpacingZ() };
            double[] idx2 = { (p2.x - controller.getOriginX()) / controller.getSpacingX(),
                    (p2.y - controller.getOriginY()) / controller.getSpacingY(),
                    (p2.z - controller.getOriginZ()) / controller.getSpacingZ() };

            double dist1 = (idx1[0] - cx) * vn[0] + (idx1[1] - cy) * vn[1] + (idx1[2] - cz) * vn[2];
            double dist2 = (idx2[0] - cx) * vn[0] + (idx2[1] - cy) * vn[1] + (idx2[2] - cz) * vn[2];

            // HOROS-20240407 parity: fade logic
            double avgDist = Math.abs((dist1 + dist2) / 2.0);
            float alpha = 1.0f;
            if (avgDist <= 0.5) {
                alpha = 1.0f;
            } else if (avgDist >= 1.0) {
                alpha = 0.2f;
            } else {
                alpha = (float) (avgDist * -1.6 + 1.8);
            }

            if (alpha < 0.05f)
                continue;

            g2.setColor(new Color(baseColor.getRed() / 255f, baseColor.getGreen() / 255f, baseColor.getBlue() / 255f,
                    alpha));

            double dx1 = (idx1[0] - cx) * vx[0] + (idx1[1] - cy) * vx[1] + (idx1[2] - cz) * vx[2];
            double dy1 = (idx1[0] - cx) * vy[0] + (idx1[1] - cy) * vy[1] + (idx1[2] - cz) * vy[2];
            double dx2 = (idx2[0] - cx) * vx[0] + (idx2[1] - cy) * vx[1] + (idx2[2] - cz) * vx[2];
            double dy2 = (idx2[0] - cx) * vy[0] + (idx2[1] - cy) * vy[1] + (idx2[2] - cz) * vy[2];

            g2.drawLine((int) (center.x + dx1 * scale * zoom), (int) (center.y + dy1 * scale * zoom),
                    (int) (center.x + dx2 * scale * zoom), (int) (center.y + dy2 * scale * zoom));
        }

        // HOROS-20240407 parity: draw nodes
        for (com.jj.dicomviewer.model.ROI.Point3D p : curveNodes) {
            double[] idx = { (p.x - controller.getOriginX()) / controller.getSpacingX(),
                    (p.y - controller.getOriginY()) / controller.getSpacingY(),
                    (p.z - controller.getOriginZ()) / controller.getSpacingZ() };
            double dist = (idx[0] - cx) * vn[0] + (idx[1] - cy) * vn[1] + (idx[2] - cz) * vn[2];

            if (Math.abs(dist) < 1.0) {
                double dx = (idx[0] - cx) * vx[0] + (idx[1] - cy) * vx[1] + (idx[2] - cz) * vx[2];
                double dy = (idx[0] - cx) * vy[0] + (idx[1] - cy) * vy[1] + (idx[2] - cz) * vy[2];

                int px = (int) (center.x + dx * scale * zoom);
                int py = (int) (center.y + dy * scale * zoom);

                g2.setColor(Color.RED);
                g2.fillOval(px - 3, py - 3, 6, 6);
            }
        }
    }

    private void drawCutLine(Graphics2D g2, int ox, int oy, double scale) {
        com.jj.dicomviewer.model.ROI.Point3D p = controller.getSelectedNode();
        if (p == null)
            return;

        Point center = getCrosshairScreenPos(ox, oy, scale * zoom);
        int nodeIdx = controller.getSelectedNodeIndex();
        com.jj.dicomviewer.model.ROI.Point3D[] frame = controller.getTNBFrame(nodeIdx);
        if (frame == null)
            return;

        com.jj.dicomviewer.model.ROI.Point3D normal = frame[1];
        double[][] basis = getSamplingBasis();
        double[] vx = basis[1], vy = basis[2];

        double sx_vec = normal.x * vx[0] + normal.y * vx[1] + normal.z * vx[2];
        double sy_vec = normal.x * vy[0] + normal.y * vy[1] + normal.z * vy[2];

        int len = (int) (50 * zoom);
        int x1 = center.x - (int) (sx_vec * len);
        int y1 = center.y - (int) (sy_vec * len);
        int x2 = center.x + (int) (sx_vec * len);
        int y2 = center.y + (int) (sy_vec * len);

        g2.setColor(Color.YELLOW);
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 5, 5 }, 0));
        g2.drawLine(x1, y1, x2, y2);
    }
}
