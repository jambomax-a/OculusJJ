package com.jj.dicomviewer.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import com.jj.dicomviewer.model.DicomPix;

/**
 * MPRView - Orthogonal MPR用のビュー
 * HOROS-20240407準拠: OrthogonalMPRView.h/m 相当
 */
public class MPRView extends JPanel {

    public enum Direction {
        AXIAL, SAGITTAL, CORONAL
    }

    private Direction direction;
    private MPRController controller;

    private BufferedImage image;
    private int sliceIndex = 0;

    // HOROS-20240407 [DCMView.h] 相当の表示パラメータ
    private Point lastMousePos;
    private double zoomScale = 1.0;
    private double panX = 0; // 0.0 - 1.0 (中心からのオフセット)
    private double panY = 0;

    public MPRView(MPRController controller, Direction direction) {
        this.controller = controller;
        this.direction = direction;
        setBackground(Color.BLACK);

        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePos = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePos == null)
                    return;

                int dx = e.getX() - lastMousePos.x;
                int dy = e.getY() - lastMousePos.y;

                // HOROS-20240407 [DCMView getTool:] 準拠の操作系（修飾キーによるツール切替）
                boolean isShift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;
                boolean isAlt = (e.getModifiersEx() & MouseEvent.ALT_DOWN_MASK) != 0;
                boolean isCmd = (e.getModifiersEx() & (MouseEvent.CTRL_DOWN_MASK | MouseEvent.META_DOWN_MASK)) != 0;
                boolean isRight = SwingUtilities.isRightMouseButton(e);
                boolean isMiddle = SwingUtilities.isMiddleMouseButton(e);

                if (isShift) {
                    // Shift + Drag: Zoom (Horos tZoom)
                    zoomScale *= (1.0 + dx / 100.0);
                    if (zoomScale < 0.1)
                        zoomScale = 0.1;
                    if (zoomScale > 20.0)
                        zoomScale = 20.0;
                    repaint();
                } else if (isCmd || isMiddle) {
                    // Command (Ctrl) + Drag or Middle Click: Pan (Horos tTranslate)
                    panX += (double) dx / getWidth() / zoomScale;
                    panY += (double) dy / getHeight() / zoomScale;
                    repaint();
                } else if (isAlt || isRight) {
                    // Alt + Drag or Right Click: WW/WL (Horos tWL)
                    float deltaW = dx * 4.0f;
                    float deltaL = dy * 4.0f;
                    controller.updateWindow(deltaL, deltaW);
                } else {
                    // Default Left Mouse: Crosshair move (OrthogonalMPR独自)
                    updateCrosshair(e.getX(), e.getY());
                }

                lastMousePos = e.getPoint();
            }

            private void updateCrosshair(int px, int py) {
                // paintComponentと同じ計算で描画領域を求める
                double imgRatio = controller.getAspectRatio(direction);
                double viewRatio = (double) getWidth() / getHeight();
                int drawW, drawH, sx, sy;

                if (imgRatio > viewRatio) {
                    drawW = (int) (getWidth() * zoomScale);
                    drawH = (int) (drawW / imgRatio);
                } else {
                    drawH = (int) (getHeight() * zoomScale);
                    drawW = (int) (drawH * imgRatio);
                }
                sx = (int) ((getWidth() - drawW) / 2 + panX * getWidth());
                sy = (int) ((getHeight() - drawH) / 2 + panY * getHeight());

                // 表示座標からボリューム座標に変換
                double scaleX = (double) controller.getDim(direction, 0) / drawW;
                double scaleY = (double) controller.getDim(direction, 1) / drawH;

                int volX = (int) ((px - sx) * scaleX);
                int volY = (int) ((py - sy) * scaleY);

                controller.updatePosition(direction, volX, volY);
            }
        };

        addMouseListener(adapter);
        addMouseMotionListener(adapter);

        // HOROS-20240407準拠: マウスホイールによるスライス移動
        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if ((e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0) {
                    // Ctrl + Wheel: Zoom
                    double delta = e.getPreciseWheelRotation();
                    zoomScale *= (1.0 - delta * 0.1);
                    if (zoomScale < 0.1)
                        zoomScale = 0.1;
                    if (zoomScale > 20.0)
                        zoomScale = 20.0;
                    repaint();
                } else {
                    int rotation = e.getWheelRotation();
                    // ホイールの回転方向に応じて位置を更新
                    controller.updateSlicePosition(direction, rotation);
                }
            }
        });
    }

    public void setSliceIndex(int index) {
        this.sliceIndex = index;
        updateImage();
        repaint();
    }

    private void updateImage() {
        int w = controller.getDim(direction, 0);
        int h = controller.getDim(direction, 1);
        if (w <= 0 || h <= 0)
            return;

        if (image == null || image.getWidth() != w || image.getHeight() != h) {
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }

        short[] data = controller.getSliceData(direction, sliceIndex);
        if (data == null || data.length == 0)
            return;

        float wl = controller.getWindowLevel();
        float ww = controller.getWindowWidth();
        float slope = controller.getRescaleSlope();
        float intercept = controller.getRescaleIntercept();
        boolean isSigned = controller.getIsSigned();
        boolean isInverse = controller.getIsInverse();

        // HOROS-20240407準拠の高速LUT適用
        // 16bit入力を0-255にマッピング
        byte[] lut = new byte[65536];
        float low = wl - (ww / 2f);
        float actualWW = (ww < 1.0f) ? 1.0f : ww;
        float factor = 255.0f / actualWW;

        for (int i = 0; i < 65536; i++) {
            // DICOMのピクセル値に応じたRescale Slope/Interceptを考慮
            // HOROS-20240407準拠: 符号なし16bitとしてキャストしてから計算
            float rawValue = isSigned ? (float) ((short) i) : (float) (i & 0xFFFF);
            float v = rawValue * slope + intercept;

            // MONOCHROME1の場合、データ(v)を反転させる
            if (isInverse) {
                v = -v;
            }

            int p = (int) ((v - low) * factor);
            if (p < 0)
                p = 0;
            if (p > 255)
                p = 255;

            lut[i] = (byte) p;
        }

        int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < data.length; i++) {
            int rawVal = data[i] & 0xFFFF;
            int gray = lut[rawVal] & 0xFF;
            pixels[i] = (gray << 16) | (gray << 8) | gray;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null)
            return;

        Graphics2D g2 = (Graphics2D) g;
        // HOROS-20240407 [DCMView drawRect:] 準拠:
        // 拡大時の画像品質を確保するため Bicubic 補間を使用
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 背景（黒）
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // アスペクト比を維持して描画 (物理的な比率を使用)
        double imgRatio = controller.getAspectRatio(direction);
        double viewRatio = (double) getWidth() / getHeight();
        int drawW, drawH, sx, sy;

        // HOROS-20240407 [DCMView drawRect:] 準拠
        // ズームとパンを適用してサイズと開始位置を計算
        if (imgRatio > viewRatio) {
            drawW = (int) (getWidth() * zoomScale);
            drawH = (int) (drawW / imgRatio);
        } else {
            drawH = (int) (getHeight() * zoomScale);
            drawW = (int) (drawH * imgRatio);
        }

        // パンはビュー領域に対する比率でオフセット
        sx = (int) ((getWidth() - drawW) / 2 + panX * getWidth());
        sy = (int) ((getHeight() - drawH) / 2 + panY * getHeight());

        g2.drawImage(image, sx, sy, drawW, drawH, null);

        // クロスヘアの描画
        drawCrosshairs(g2, sx, sy, drawW, drawH);

        // 情報の描画 (Horosのスタイル)
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        String title = "";
        switch (direction) {
            case AXIAL:
                title = "AXIAL";
                break;
            case SAGITTAL:
                title = "SAGITTAL";
                break;
            case CORONAL:
                title = "CORONAL";
                break;
        }
        g2.drawString(title, 10, 20);
        g2.drawString("Slice: " + (sliceIndex + 1), 10, getHeight() - 10);

        // HOROS-20240407準拠: 右下にWW/WLを表示
        String wwl = String.format("WL: %.0f WW: %.0f", controller.getWindowLevel(), controller.getWindowWidth());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(wwl, getWidth() - fm.stringWidth(wwl) - 10, getHeight() - 10);

        // ズーム倍率を表示 (1.0x 以外の場合)
        if (Math.abs(zoomScale - 1.0) > 0.01) {
            String zoomStr = String.format("Zoom: %.2fx", zoomScale);
            g2.drawString(zoomStr, getWidth() - fm.stringWidth(zoomStr) - 10, 20);
        }
    }

    private void drawCrosshairs(Graphics2D g2, int sx, int sy, int drawW, int drawH) {
        int posX = controller.getCrosshairX(direction);
        int posY = controller.getCrosshairY(direction);

        double scaleX = (double) drawW / controller.getDim(direction, 0);
        double scaleY = (double) drawH / controller.getDim(direction, 1);

        int screenX = sx + (int) (posX * scaleX);
        int screenY = sy + (int) (posY * scaleY);

        // HOROS-20240407準拠: 交差線は緑色 (0, 255, 0)
        g2.setColor(new Color(0, 255, 0, 180));
        g2.setStroke(new BasicStroke(1.0f));

        // 中心付近を空白にする描画 (隙間: GAPピクセル)
        int GAP = 15;

        // 垂直線 (上下)
        if (screenY - GAP > sy) {
            g2.drawLine(screenX, sy, screenX, screenY - GAP);
        }
        if (screenY + GAP < sy + drawH) {
            g2.drawLine(screenX, screenY + GAP, screenX, sy + drawH);
        }

        // 水平線 (左右)
        if (screenX - GAP > sx) {
            g2.drawLine(sx, screenY, screenX - GAP, screenY);
        }
        if (screenX + GAP < sx + drawW) {
            g2.drawLine(screenX + GAP, screenY, sx + drawW, screenY);
        }
    }
}
