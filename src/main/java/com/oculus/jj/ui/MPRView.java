package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * MPRView - Orthogonal MPR用のビュー
 */
public class MPRView extends JPanel {

    public enum Direction {
        AXIAL, SAGITTAL, CORONAL, THREE_D
    }

    private Direction direction;
    private MPRController controller;

    private BufferedImage image;

    // HOROS-2024.04.07 準拠: サムネイル（A, B, C）用のフラグ
    private String thumbLabel = "";
    private int thumbnailIndex = -1; // 0:A, 1:B, 2:C
    private boolean isThumbnail = false;
    private boolean needsUpdate = true; // 再描画フラグ

    public void triggerUpdate(boolean force) {
        if (force)
            this.needsUpdate = true;
        repaint();
    }

    public boolean getIsThumbnail() {
        return isThumbnail;
    }

    public void setThumbnailMode(String label, int index) {
        this.isThumbnail = true;
        this.thumbLabel = label;
        this.thumbnailIndex = index;
    }

    private Point lastMousePos;
    private double zoomScale = 1.0;
    private double panX = 0; // 0.0 - 1.0 (中心からのオフセット)
    private double panY = 0;

    // ヒットテスト用の状態変数 (Horos crossMove 相当)
    private enum DragMode {
        NONE, CENTER, HORIZONTAL, VERTICAL,
        CURVE_A, CURVE_B, CURVE_C // Curved-MPR 用のマーカードラッグ
    }

    private DragMode dragMode = DragMode.NONE;
    private static final int HIT_THRESHOLD = 8; // 感度を少し上げる

    public void refresh() {
        updateImage();
        repaint();
    }

    /**
     * 現在の画像を取得する（ヒストグラム計算用）
     */
    public BufferedImage getCurrentImage() {
        return image;
    }

    public double[][] getSamplingBasis_forMovement() {
        return getSamplingBasis();
    }

    public double[][] getSamplingBasis() {
        if (controller == null)
            return null;

        // HOROS-20240407準拠:
        // 回転操作中（クロスヘアを回している最中）に、見ているビュー自体の画像が回転すると非常に使いにくいため、
        // 各ビューの基底 (Display Basis) は LPS 標準座標系の基本ベクトルに投影・固定し、
        // 平面の「法線」が傾いても、画像の中身がぐるぐる回らないようにする（交差線だけが回るようにする）。
        switch (direction) {
            case AXIAL:
                // 法線 vA に対し、水平は X(1,0,0), 垂直は Y(0,1,0) 方向を優先して投影
                return computeStableBasis(controller.getVA(), new double[] { 1, 0, 0 }, new double[] { 0, 1, 0 });
            case SAGITTAL:
                // 法線 vS に対し、水平は Y(0,1,0), 垂直は Z(0,0,1) 方向を優先して投影
                return computeStableBasis(controller.getVS(), new double[] { 0, 1, 0 }, new double[] { 0, 0, 1 });
            case CORONAL:
                // 法線 vC に対し、水平は X(1,0,0), 垂直は Z(0,0,1) 方向を優先して投影
                return computeStableBasis(controller.getVC(), new double[] { 1, 0, 0 }, new double[] { 0, 0, 1 });
            default:
                return null;
        }
    }

    /**
     * 法線ベクトルに対し、指定された優先方向（preferredX/Y）に最も近い水平・垂直ベクトルを計算する。
     * これにより、平面が傾いても画像が「天地逆転」したり「回転」したりするのを防ぐ。
     */
    private double[][] computeStableBasis(double[] normal, double[] prefX, double[] prefY) {
        // Horizontal vector: vx = prefY x normal
        double vx_x = prefY[1] * normal[2] - prefY[2] * normal[1];
        double vx_y = prefY[2] * normal[0] - prefY[0] * normal[2];
        double vx_z = prefY[0] * normal[1] - prefY[1] * normal[0];
        double lenX = Math.sqrt(vx_x * vx_x + vx_y * vx_y + vx_z * vx_z);

        if (lenX < 0.1) {
            // 平行に近い場合は予備の prefX を使用
            vx_x = normal[1] * prefX[2] - normal[2] * prefX[1];
            vx_y = normal[2] * prefX[0] - normal[0] * prefX[2];
            vx_z = normal[0] * prefX[1] - normal[1] * prefX[0];
            lenX = Math.sqrt(vx_x * vx_x + vx_y * vx_y + vx_z * vx_z);
        }

        if (lenX < 0.01) {
            // それでもダメな場合は、適当な軸を選択
            vx_x = 1;
            vx_y = 0;
            vx_z = 0;
            lenX = 1;
        }

        double[] vx = { vx_x / lenX, vx_y / lenX, vx_z / lenX };
        // Vertical vector: vy = normal x vx
        double[] vy = {
                normal[1] * vx[2] - normal[2] * vx[1],
                normal[2] * vx[0] - normal[0] * vx[2],
                normal[0] * vx[1] - normal[1] * vx[0]
        };
        return new double[][] { normal, vx, vy };
    }

    public MPRView(MPRController controller, Direction direction) {
        this.controller = controller;
        this.direction = direction;
        setBackground(Color.BLACK);

        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (direction != Direction.THREE_D) {
                        if (hitTest(e.getX(), e.getY()) == DragMode.CENTER) {
                            // HOROS-2024.04.07 準拠: ダブルクリックでクロスヘアをボリューム中心にリセット
                            controller.resetCrosshair();
                        }
                    } else {
                        // 3Dビューのダブルクリックでオリエンテーションをリセット
                        controller.resetOrientation();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // HOROS-20240407 準拠: クリックしたビューをハイライト（赤枠表示）対象にする
                controller.setHighlightedView(direction);

                lastMousePos = e.getPoint();

                // ダブルクリックでリセット (Horos準拠)
                if (e.getClickCount() == 2) {
                    if (direction == Direction.THREE_D) {
                        controller.resetOrientation();
                    } else {
                        controller.resetCrosshair();
                    }
                    return;
                }

                // Shift + Click で曲線ポイントを追加
                if (e.isShiftDown() && direction != Direction.THREE_D) {
                    // --- 修正: モードが CURVED_MPR の時だけ制御点を追加するように制約を加える ---
                    if (controller.getLayoutType() == MPRController.LayoutType.CURVED_MPR) {
                        jumpToClickPos(e.getX(), e.getY()); // 1. まずクリック位置にクロスヘアをジャンプ
                        controller.addCurvePoint(controller.getCurX(), controller.getCurY(), controller.getCurZ()); // 2.
                                                                                                                    // その座標を曲線に追加
                        controller.updateAllViews(true); // 3. 全ビュー（特にCurvedビュー）を更新
                        return;
                    }
                }

                if (direction != Direction.THREE_D) {
                    dragMode = hitTest(e.getX(), e.getY());
                    if (dragMode == DragMode.NONE) {
                        // 2D Orthogonal MPR において、背景クリックでも中心をジャンプさせる
                        jumpToClickPos(e.getX(), e.getY());
                        dragMode = DragMode.CENTER;
                    }
                }
                repaint(); // ← 追加: ハイライトの即時反映
            }

            private double[] screenToWorld(int px, int py) {
                double[][] basis = getSamplingBasis();
                double[] normal = basis[0];
                double[] vx = basis[1], vy = basis[2];

                // クリック位置（ピクセル）から正規化された画像座標への変換
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

                double normX = (px - sx) / (double) drawW - 0.5;
                double normY = (py - sy) / (double) drawH - 0.5;

                double dx_vox = normX * controller.getDim(direction, 0);
                double dy_vox = normY * controller.getDim(direction, 1);

                double spX = controller.getSpacing(direction, 0);
                double spY = controller.getSpacing(direction, 1);

                // --- 修正: 表示中のスライス面の中心座標を基準にする ---
                double dist = (controller.getCurX() - controller.getVolCX()) * normal[0]
                        + (controller.getCurY() - controller.getVolCY()) * normal[1]
                        + (controller.getCurZ() - controller.getVolCZ()) * normal[2];

                double cx = controller.getVolCX() + dist * normal[0];
                double cy = controller.getVolCY() + dist * normal[1];
                double cz = controller.getVolCZ() + dist * normal[2];

                double wx = cx + (dx_vox * spX) * vx[0] + (dy_vox * spY) * vy[0];
                double wy = cy + (dx_vox * spX) * vx[1] + (dy_vox * spY) * vy[1];
                double wz = cz + (dx_vox * spX) * vx[2] + (dy_vox * spY) * vy[2];
                return new double[] { wx, wy, wz };
            }

            private void jumpToClickPos(int px, int py) {
                double[] world = screenToWorld(px, py);
                controller.setFocusPosition(world[0], world[1], world[2]);
            }

            private DragMode hitTest(int px, int py) {
                Point screenPos = getImageScreenPos();
                if (screenPos == null)
                    return DragMode.NONE;

                // 中心点判定
                if (Math.abs(px - screenPos.x) <= HIT_THRESHOLD && Math.abs(py - screenPos.y) <= HIT_THRESHOLD) {
                    return DragMode.CENTER;
                }
                // 回転操作 (Horos: 軸の上でドラッグすると回転)
                if (Math.abs(px - screenPos.x) <= HIT_THRESHOLD) {
                    return DragMode.VERTICAL;
                }
                if (Math.abs(py - screenPos.y) <= HIT_THRESHOLD) {
                    return DragMode.HORIZONTAL;
                }
                return DragMode.NONE;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePos == null)
                    return;

                int dx = e.getX() - lastMousePos.x;
                int dy = e.getY() - lastMousePos.y;

                boolean isShift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;
                boolean isAlt = (e.getModifiersEx() & MouseEvent.ALT_DOWN_MASK) != 0;
                boolean isCmd = (e.getModifiersEx() & (MouseEvent.CTRL_DOWN_MASK | MouseEvent.META_DOWN_MASK)) != 0;
                boolean isRight = SwingUtilities.isRightMouseButton(e);
                boolean isMiddle = SwingUtilities.isMiddleMouseButton(e);

                if (isShift) {
                    zoomScale *= (1.0 + dx / 100.0);
                    repaint();
                } else if (isCmd || isMiddle) {
                    updatePanPos_rel(dx, dy);
                } else if (isAlt || isRight) {
                    controller.updateWindow(dy * 4.0f, dx * 4.0f);
                } else {
                    if (direction == Direction.THREE_D) {
                        if (controller.getLayoutType() == MPRController.LayoutType.CURVED_MPR && !isThumbnail) {
                            if (dragMode == DragMode.CURVE_B) {
                                double totalLen = controller.getCurveDistanceTotal();
                                double distB = (double) e.getX() / getWidth() * totalLen;
                                controller.setFocusByCurveDistance(distB);
                            } else if (dragMode == DragMode.CURVE_A || dragMode == DragMode.CURVE_C) {
                                double totalLen = controller.getCurveDistanceTotal();
                                double distFocus = controller.getCurveDistanceAtFocus();
                                double distDrag = (double) e.getX() / getWidth() * totalLen;
                                double newInterval = Math.abs(distDrag - distFocus);
                                controller.setThumbInterval(newInterval);
                            } else {
                                controller.updateRotation(dx, dy);
                            }
                        } else {
                            controller.updateRotation(dx, dy);
                        }
                    } else {
                        // HOROS-20240407準拠:
                        // 交差線の移動 (Orthogonal モードではヒットテストに関わらず移動を許可)
                        if (controller.getLayoutType() == MPRController.LayoutType.ORTHOGONAL) {
                            updateCrosshairPos_rel(dx, dy);
                        } else if (dragMode == DragMode.CENTER) {
                            updateCrosshairPos_rel(dx, dy);
                        } else if ((dragMode == DragMode.VERTICAL || dragMode == DragMode.HORIZONTAL)
                                && (controller.getLayoutType() == MPRController.LayoutType.THREE_D_MPR
                                        || controller.getLayoutType() == MPRController.LayoutType.CURVED_MPR)) {
                            // 3D MPR または Curved MPR の場合のみ回転を許可
                            Point screenPos = getImageScreenPos();
                            if (screenPos != null) {
                                double angle = Math.atan2(e.getY() - screenPos.y, e.getX() - screenPos.x);
                                double lastAngle = Math.atan2(lastMousePos.y - screenPos.y,
                                        lastMousePos.x - screenPos.x);
                                // 回転角の制限
                                double deltaAngle = angle - lastAngle;
                                if (deltaAngle > Math.PI)
                                    deltaAngle -= 2 * Math.PI;
                                if (deltaAngle < -Math.PI)
                                    deltaAngle += 2 * Math.PI;

                                controller.rotateMPR(direction, Math.toDegrees(deltaAngle));
                            }
                        }
                    }
                }
                lastMousePos = e.getPoint();
            }

            private void updateCrosshairPos_rel(int dx, int dy) {
                double imgRatio = controller.getAspectRatio(direction);
                double viewRatio = (double) getWidth() / getHeight();
                int drawW, drawH;
                if (imgRatio > viewRatio) {
                    drawW = (int) (getWidth() * zoomScale);
                    drawH = (int) (drawW / imgRatio);
                } else {
                    drawH = (int) (getHeight() * zoomScale);
                    drawW = (int) (drawH * imgRatio);
                }

                double scaleX = (double) controller.getDim(direction, 0) / drawW;
                double scaleY = (double) controller.getDim(direction, 1) / drawH;

                // HOROS-20240407 準拠: ドラッグモードに応じて移動方向を制限する
                // これにより、垂直線（Sagittal）だけを動かしたり、水平線（Coronal）だけを動かしたりしやすくなる。
                if (dragMode == DragMode.VERTICAL) {
                    controller.updatePosition_rel(direction, dx * scaleX, 0);
                } else if (dragMode == DragMode.HORIZONTAL) {
                    controller.updatePosition_rel(direction, 0, dy * scaleY);
                } else {
                    // CENTER または NONE の場合は両方動かす
                    controller.updatePosition_rel(direction, dx * scaleX, dy * scaleY);
                }
            }

            private void updatePanPos_rel(int dx, int dy) {
                double imgRatio = controller.getAspectRatio(direction);
                double viewRatio = (double) getWidth() / getHeight();
                int drawW, drawH;
                if (imgRatio > viewRatio) {
                    drawW = (int) (getWidth() * zoomScale);
                    drawH = (int) (drawW / imgRatio);
                } else {
                    drawH = (int) (getHeight() * zoomScale);
                    drawW = (int) (drawH * imgRatio);
                }

                double scaleX = (double) controller.getDim(direction, 0) / drawW;
                double scaleY = (double) controller.getDim(direction, 1) / drawH;

                // HOROS-20240407 準拠: パン操作の方向を直感的に (dx, dy そのまま渡す)
                controller.updatePan_rel(direction, dx * scaleX, dy * scaleY);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (direction == Direction.THREE_D && controller.getLayoutType() == MPRController.LayoutType.CURVED_MPR
                        && !isThumbnail) {
                    int px = e.getX();
                    int w = getWidth();
                    double totalLen = controller.getCurveDistanceTotal();
                    double distFocus = controller.getCurveDistanceAtFocus();
                    double interval = controller.getThumbInterval();

                    // A, B, C の位置（ピクセル）を計算
                    int bx = (int) ((distFocus / totalLen) * w);
                    int ax = (int) (((distFocus - interval) / totalLen) * w);
                    int cx = (int) (((distFocus + interval) / totalLen) * w);

                    if (Math.abs(px - bx) < HIT_THRESHOLD) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        dragMode = DragMode.CURVE_B;
                    } else if (Math.abs(px - ax) < HIT_THRESHOLD) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
                        dragMode = DragMode.CURVE_A;
                    } else if (Math.abs(px - cx) < HIT_THRESHOLD) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                        dragMode = DragMode.CURVE_C;
                    } else {
                        setCursor(Cursor.getDefaultCursor());
                        dragMode = DragMode.NONE;

                        // ヒットしなければ従来通りのホバー処理
                        double ratio = (double) px / w;
                        java.util.List<double[]> pts = controller.getCurvePoints();
                        if (pts.size() >= 2) {
                            double targetDist = ratio * totalLen;
                            double currentDist = 0;
                            double[] hoverPos = pts.get(0);
                            for (int i = 0; i < pts.size() - 1; i++) {
                                double d = Math.sqrt(Math.pow(pts.get(i + 1)[0] - pts.get(i)[0], 2)
                                        + Math.pow(pts.get(i + 1)[1] - pts.get(i)[1], 2)
                                        + Math.pow(pts.get(i + 1)[2] - pts.get(i)[2], 2));
                                if (currentDist + d >= targetDist || i == pts.size() - 2) {
                                    double t = (targetDist - currentDist) / d;
                                    hoverPos = new double[] {
                                            pts.get(i)[0] + (pts.get(i + 1)[0] - pts.get(i)[0]) * t,
                                            pts.get(i)[1] + (pts.get(i + 1)[1] - pts.get(i)[1]) * t,
                                            pts.get(i)[2] + (pts.get(i + 1)[2] - pts.get(i)[2]) * t
                                    };
                                    break;
                                }
                                currentDist += d;
                            }
                            controller.setHoverCurvePoint(hoverPos);
                        }
                    }
                }
            }
        };

        addMouseListener(adapter);
        addMouseMotionListener(adapter);

        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if ((e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0) {
                    zoomScale *= (1.0 - e.getPreciseWheelRotation() * 0.1);
                    repaint();
                } else {
                    controller.updateSlicePosition(direction, e.getWheelRotation());
                }
            }
        });
    }

    private Point getImageScreenPos() {
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
        // Panパラメータ(panX, panY)は今回は使用せず、volCX系で制御する想定だが、
        // もし残すならここでオフセットを加算する
        sx = (int) ((getWidth() - drawW) / 2 + panX * getWidth());
        sy = (int) ((getHeight() - drawH) / 2 + panY * getHeight());

        double scaleX = (double) drawW / controller.getDim(direction, 0);
        double scaleY = (double) drawH / controller.getDim(direction, 1);

        // 各ビューのサンプリング基底を取得
        double[][] basis = getSamplingBasis();
        double[] vx = basis[1], vy = basis[2];

        // サンプリング中心(volCX)から見たクロスヘア(curX)の相対位置を計算 (mm)
        double dx_mm = (controller.getvX() - controller.getVolCX()) * vx[0]
                + (controller.getvY() - controller.getVolCY()) * vx[1]
                + (controller.getvZ() - controller.getVolCZ()) * vx[2];
        double dy_mm = (controller.getvX() - controller.getVolCX()) * vy[0]
                + (controller.getvY() - controller.getVolCY()) * vy[1]
                + (controller.getvZ() - controller.getVolCZ()) * vy[2];

        // mm からピクセル座標へ変換 (mm / spacing = voxels)
        double dx_vox = dx_mm / controller.getSpacing(direction, 0);
        double dy_vox = dy_mm / controller.getSpacing(direction, 1);

        // 画面中央 + 相対位置 (px)
        int screenX = sx + (int) ((controller.getDim(direction, 0) / 2.0 + dx_vox) * scaleX);
        int screenY = sy + (int) ((controller.getDim(direction, 1) / 2.0 + dy_vox) * scaleY);
        return new Point(screenX, screenY);
    }

    private void updateImage() {
        if (isThumbnail) {
            updateThumbnailImage();
        } else if (direction == Direction.THREE_D) {
            if (controller.getLayoutType() == MPRController.LayoutType.CURVED_MPR) {
                updateCurvedImage();
            } else {
                update3DImage();
            }
        } else {
            update2DImage();
        }
    }

    private void updateThumbnailImage() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            w = 256;
            h = 256;
        }
        if (image == null || image.getWidth() != w || image.getHeight() != h) {
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }

        java.util.List<double[]> pts = controller.getCurvePoints();
        if (pts.size() < 2) {
            // 曲線の作成前でも黒く塗りつぶしてラベルだけ表示する
            Graphics2D g = image.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
            g.dispose();
            return;
        }

        // 総延長の計算
        double totalLen = controller.getCurveDistanceTotal();

        // B(Focus)地点の距離を取得
        double distFocus = controller.getCurveDistanceAtFocus();
        double interval = controller.getThumbInterval();
        double targetDist = distFocus; // Default B

        // thumbnailIndex(0=A, 1=B, 2=C)に応じて距離をオフセット
        if (thumbnailIndex == 0)
            targetDist = distFocus - interval;
        else if (thumbnailIndex == 2)
            targetDist = distFocus + interval;

        // 範囲外チェック
        if (targetDist < 0)
            targetDist = 0;
        if (targetDist > totalLen)
            targetDist = totalLen;

        double currentDist = 0;
        double[] pos = pts.get(0);
        double[] tangent = { 0, 0, 1 };

        for (int i = 0; i < pts.size() - 1; i++) {
            double[] p1 = pts.get(i), p2 = pts.get(i + 1);
            double d = Math.sqrt(Math.pow(p2[0] - p1[0], 2) + Math.pow(p2[1] - p1[1], 2) + Math.pow(p2[2] - p1[2], 2));
            if (currentDist + d >= targetDist || i == pts.size() - 2) {
                double t = (targetDist - currentDist) / (d < 0.0001 ? 1 : d);
                pos = new double[] { p1[0] + (p2[0] - p1[0]) * t, p1[1] + (p2[1] - p1[1]) * t,
                        p1[2] + (p2[2] - p1[2]) * t };
                tangent = new double[] { (p2[0] - p1[0]) / (d < 0.0001 ? 1 : d),
                        (p2[1] - p1[1]) / (d < 0.0001 ? 1 : d), (p2[2] - p1[2]) / (d < 0.0001 ? 1 : d) };
                break;
            }
            currentDist += d;
        }

        // 接線に垂直な断面 (Transverse) をサンプリング
        double[] vUp = controller.getVA();
        double[] vSide = {
                tangent[1] * vUp[2] - tangent[2] * vUp[1],
                tangent[2] * vUp[0] - tangent[0] * vUp[2],
                tangent[0] * vUp[1] - tangent[1] * vUp[0]
        };
        double[] vOrtho = {
                vSide[1] * tangent[2] - vSide[2] * tangent[1],
                vSide[2] * tangent[0] - vSide[0] * tangent[2],
                vSide[0] * tangent[1] - vSide[1] * tangent[0]
        };

        // 正規化
        double lenS = Math.sqrt(vSide[0] * vSide[0] + vSide[1] * vSide[1] + vSide[2] * vSide[2]);
        if (lenS > 0) {
            vSide[0] /= lenS;
            vSide[1] /= lenS;
            vSide[2] /= lenS;
        }
        double lenO = Math.sqrt(vOrtho[0] * vOrtho[0] + vOrtho[1] * vOrtho[1] + vOrtho[2] * vOrtho[2]);
        if (lenO > 0) {
            vOrtho[0] /= lenO;
            vOrtho[1] /= lenO;
            vOrtho[2] /= lenO;
        }

        float wl = controller.getWindowLevel(), ww = controller.getWindowWidth();
        float factor = 255.0f / (ww < 1 ? 1 : ww), low = wl - (ww / 2f);
        int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();

        double mm_range = 100.0; // 10cm x 10cm
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double ox = ((x / (double) w) - 0.5) * mm_range;
                double oy = ((y / (double) h) - 0.5) * mm_range;
                double vx = pos[0] + vSide[0] * ox + vOrtho[0] * oy;
                double vy = pos[1] + vSide[1] * ox + vOrtho[1] * oy;
                double vz = pos[2] + vSide[2] * ox + vOrtho[2] * oy;

                double val = controller.getInterpolatedVoxel(vx, vy, vz);
                int g = (int) ((val - low) * factor);
                if (g < 0)
                    g = 0;
                else if (g > 255)
                    g = 255;
                pixels[y * w + x] = (g << 16) | (g << 8) | g;
            }
        }
    }

    private void updateCurvedImage() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            w = 512;
            h = 512; // Fallback
        }

        if (image == null || image.getWidth() != w || image.getHeight() != h) {
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }

        java.util.List<double[]> pts = controller.getCurvePoints();
        if (pts.size() < 2) {
            // 背景を黒くし、ガイドメッセージをより詳細に表示
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(255, 200, 0)); // 明いオレンジ
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            String msg1 = com.oculus.jj.utils.LanguageManager.get("mpr.mode.3dCurved");
            String msg2 = com.oculus.jj.utils.LanguageManager.get("mpr.hint.curved");
            FontMetrics fm = g.getFontMetrics();
            g.drawString(msg1, w / 2 - fm.stringWidth(msg1) / 2, h / 2 - 20);
            g.drawString(msg2, w / 2 - fm.stringWidth(msg2) / 2, h / 2 + 20);
            g.dispose();
            return;
        }

        // 曲線に沿ったサンプリング (Stretched CPR)
        double totalLen = 0;
        double[] segmentLens = new double[pts.size() - 1];
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] p1 = pts.get(i), p2 = pts.get(i + 1);
            double d = Math.sqrt(Math.pow(p2[0] - p1[0], 2) + Math.pow(p2[1] - p1[1], 2) + Math.pow(p2[2] - p1[2], 2));
            segmentLens[i] = d;
            totalLen += d;
        }

        float wl = controller.getWindowLevel(), ww = controller.getWindowWidth();
        float factor = 255.0f / (ww < 1 ? 1 : ww), low = wl - (ww / 2f);
        int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();

        // 垂直方向は Axial Normal (vA) 方向にサンプリング (-100mm to +100mm)
        // 改善: vUpを現在の曲線の接線と垂直なベクトルとして計算する（より正確な断面）
        double[] vUp = controller.getVA();
        double vRange_mm = 200.0;

        for (int x = 0; x < w; x++) {
            // 曲線上の位置を特定
            double targetDist = (x / (double) w) * totalLen;
            double currentDist = 0;
            double[] pos = pts.get(0);
            double[] tangent = { 0, 0, 1 }; // デフォルト

            for (int i = 0; i < segmentLens.length; i++) {
                if (currentDist + segmentLens[i] >= targetDist || i == segmentLens.length - 1) {
                    double t = (targetDist - currentDist) / segmentLens[i];
                    double[] p1 = pts.get(i), p2 = pts.get(i + 1);
                    pos = new double[] {
                            p1[0] + (p2[0] - p1[0]) * t,
                            p1[1] + (p2[1] - p1[1]) * t,
                            p1[2] + (p2[2] - p1[2]) * t
                    };
                    tangent = new double[] {
                            (p2[0] - p1[0]) / segmentLens[i],
                            (p2[1] - p1[1]) / segmentLens[i],
                            (p2[2] - p1[2]) / segmentLens[i]
                    };
                    break;
                }
                currentDist += segmentLens[i];
            }

            // 接線とvAから垂直ベクトルを再計算 (外積)
            double[] vSide = {
                    tangent[1] * vUp[2] - tangent[2] * vUp[1],
                    tangent[2] * vUp[0] - tangent[0] * vUp[2],
                    tangent[0] * vUp[1] - tangent[1] * vUp[0]
            };
            // 垂直ベクトル (Normal to tangent and side)
            double[] vOrtho = {
                    vSide[1] * tangent[2] - vSide[2] * tangent[1],
                    vSide[2] * tangent[0] - vSide[0] * tangent[2],
                    vSide[0] * tangent[1] - vSide[1] * tangent[0]
            };
            double lenO = Math.sqrt(vOrtho[0] * vOrtho[0] + vOrtho[1] * vOrtho[1] + vOrtho[2] * vOrtho[2]);
            if (lenO > 0.001) {
                vOrtho[0] /= lenO;
                vOrtho[1] /= lenO;
                vOrtho[2] /= lenO;
            } else {
                vOrtho = vUp;
            }

            for (int y = 0; y < h; y++) {
                double offset = ((y / (double) h) - 0.5) * vRange_mm;
                double vx = pos[0] + vOrtho[0] * offset;
                double vy = pos[1] + vOrtho[1] * offset;
                double vz = pos[2] + vOrtho[2] * offset;

                double val = controller.getInterpolatedVoxel(vx, vy, vz);
                int g = (int) ((val - low) * factor);
                if (g < 0)
                    g = 0;
                else if (g > 255)
                    g = 255;
                pixels[y * w + x] = (g << 16) | (g << 8) | g;
            }
        }
    }

    private void update2DImage() {
        int w = controller.getDim(direction, 0);
        int h = controller.getDim(direction, 1);
        if (w <= 0 || h <= 0)
            return;

        if (image == null || image.getWidth() != w || image.getHeight() != h) {
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }

        double[][] basis = getSamplingBasis();
        double[] normal = basis[0], vx = basis[1], vy = basis[2];
        double volCX = controller.getVolCX(), volCY = controller.getVolCY(), volCZ = controller.getVolCZ();
        double curX = controller.getvX(), curY = controller.getvY(), curZ = controller.getvZ();

        double dist = (curX - volCX) * normal[0] + (curY - volCY) * normal[1] + (curZ - volCZ) * normal[2];
        double cx = volCX + dist * normal[0], cy = volCY + dist * normal[1], cz = volCZ + dist * normal[2];

        float wl = controller.getWindowLevel(), ww = controller.getWindowWidth();
        float factor = 255.0f / (ww < 1 ? 1 : ww), low = wl - (ww / 2f);
        int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();

        double startX = cx - (w / 2.0) * controller.getSpacing(direction, 0) * vx[0]
                - (h / 2.0) * controller.getSpacing(direction, 1) * vy[0];
        double startY = cy - (w / 2.0) * controller.getSpacing(direction, 0) * vx[1]
                - (h / 2.0) * controller.getSpacing(direction, 1) * vy[1];
        double startZ = cz - (w / 2.0) * controller.getSpacing(direction, 0) * vx[2]
                - (h / 2.0) * controller.getSpacing(direction, 1) * vy[2];

        double slabT = controller.getSlabThickness();
        MPRController.SlabMode slabM = controller.getSlabMode();
        boolean isSlab = (slabT > 0.1 && slabM != MPRController.SlabMode.NONE);

        // Slabサンプリング用パラメータ
        double samplingStep = 0.5; // mm
        int steps = isSlab ? (int) Math.max(1, Math.ceil(slabT / samplingStep)) : 1;
        double actualStep = (isSlab && steps > 1) ? (slabT / (steps - 1)) : 0;
        double startOffset = -slabT / 2.0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double vpx = startX + x * controller.getSpacing(direction, 0) * vx[0]
                        + y * controller.getSpacing(direction, 1) * vy[0];
                double vpy = startY + x * controller.getSpacing(direction, 0) * vx[1]
                        + y * controller.getSpacing(direction, 1) * vy[1];
                double vpz = startZ + x * controller.getSpacing(direction, 0) * vx[2]
                        + y * controller.getSpacing(direction, 1) * vy[2];

                double val;
                if (!isSlab) {
                    val = controller.getInterpolatedVoxel(vpx, vpy, vpz);
                } else {
                    if (slabM == MPRController.SlabMode.MIP) {
                        val = -3024; // 最小のHU値想定
                        for (int i = 0; i < steps; i++) {
                            double offset = startOffset + i * actualStep;
                            double v = controller.getInterpolatedVoxel(vpx + offset * normal[0],
                                    vpy + offset * normal[1], vpz + offset * normal[2]);
                            if (v > val)
                                val = v;
                        }
                    } else if (slabM == MPRController.SlabMode.MINIP) {
                        val = 3024; // 最大のHU値想定
                        for (int i = 0; i < steps; i++) {
                            double offset = startOffset + i * actualStep;
                            double v = controller.getInterpolatedVoxel(vpx + offset * normal[0],
                                    vpy + offset * normal[1], vpz + offset * normal[2]);
                            if (v < val)
                                val = v;
                        }
                    } else if (slabM == MPRController.SlabMode.AVERAGE) {
                        double sum = 0;
                        for (int i = 0; i < steps; i++) {
                            double offset = startOffset + i * actualStep;
                            sum += controller.getInterpolatedVoxel(vpx + offset * normal[0],
                                    vpy + offset * normal[1], vpz + offset * normal[2]);
                        }
                        val = sum / steps;
                    } else {
                        val = controller.getInterpolatedVoxel(vpx, vpy, vpz);
                    }
                }

                int g = (int) ((val - low) * factor);
                if (g < 0)
                    g = 0;
                else if (g > 255)
                    g = 255;
                pixels[y * w + x] = (g << 16) | (g << 8) | g;
            }
        }
    }

    private double lastRotateX = Double.NaN, lastRotateY = Double.NaN;
    private float lastWL = Float.NaN, lastWW = Float.NaN;

    private void update3DImage() {
        int w = 256;
        int h = 256;
        double rotateY = controller.getRotationY();
        double rotateX = controller.getRotationX();
        float wl = controller.getWindowLevel();
        float ww = controller.getWindowWidth();

        if (image == null || image.getWidth() != w || image.getHeight() != h) {
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        } else if (Math.abs(rotateX - lastRotateX) < 1e-4 && Math.abs(rotateY - lastRotateY) < 1e-4 &&
                Math.abs(wl - lastWL) < 0.1f && Math.abs(ww - lastWW) < 0.1f) {
            return;
        }

        lastRotateX = rotateX;
        lastRotateY = rotateY;
        lastWL = wl;
        lastWW = ww;

        short[][][] volume = controller.getVolume();
        if (volume == null)
            return;
        int dimZ = volume.length, dimY = volume[0].length, dimX = volume[0][0].length;

        double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
        double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);
        double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;

        float low = wl - (ww / 2f), factor = 255.0f / (ww < 1 ? 1 : ww);
        float slope = controller.getRescaleSlope(), intercept = controller.getRescaleIntercept();
        int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
        java.util.Arrays.fill(pixels, 0);

        for (int sy = 0; sy < h; sy += 2) {
            for (int sx = 0; sx < w; sx += 2) {
                double vx = (sx - w / 2.0) * (dimX / (double) w);
                double vy = (sy - h / 2.0) * (dimY / (double) h);
                float maxVal = -10000f;
                for (double vz = -cz; vz < cz; vz += 2.5) {
                    double tx = vx * cosY + vz * sinY;
                    double tz = -vx * sinY + vz * cosY;
                    double ty = vy * cosX - tz * sinX;
                    double fz = vy * sinX + tz * cosX;
                    int ix = (int) (tx + cx), iy = (int) (ty + cy), iz = (int) (fz + cz);
                    if (ix >= 0 && ix < dimX && iy >= 0 && iy < dimY && iz >= 0 && iz < dimZ) {
                        float v = (float) volume[iz][iy][ix] * slope + intercept;
                        if (v > maxVal)
                            maxVal = v;
                    }
                }
                int g = (int) ((maxVal - low) * factor);
                if (g < 0)
                    g = 0;
                else if (g > 255)
                    g = 255;
                int rgb = (g << 16) | (g << 8) | g;
                if (sy * w + sx < pixels.length) {
                    pixels[sy * w + sx] = rgb;
                    if (sx + 1 < w)
                        pixels[sy * w + sx + 1] = rgb;
                    if (sy + 1 < h) {
                        pixels[(sy + 1) * w + sx] = rgb;
                        if (sx + 1 < w)
                            pixels[(sy + 1) * w + sx + 1] = rgb;
                    }
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (controller == null)
            return;

        Graphics2D g2 = (Graphics2D) g;

        // 背景
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // 画像の更新（変更があった場合のみ実行）
        if (needsUpdate || image == null) {
            updateImage();
            needsUpdate = false;
        }

        // 描画
        if (image != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // CPR出力の場合、画像はすでにコンポーネントサイズ(等倍)で生成されている前提。
            // それ以外はアスペクト比を考慮。
            double imgRatio;
            if (direction == Direction.THREE_D && controller.getLayoutType() == MPRController.LayoutType.CURVED_MPR
                    && !isThumbnail) {
                imgRatio = (double) image.getWidth() / image.getHeight();
            } else {
                imgRatio = (direction == Direction.THREE_D) ? 1.0 : controller.getAspectRatio(direction);
            }

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

            g2.drawImage(image, sx, sy, drawW, drawH, null);

            // クロスヘアと曲線
            if (direction != Direction.THREE_D) {
                drawCrosshairs(g2, sx, sy, drawW, drawH);
                if (controller.getLayoutType() == MPRController.LayoutType.CURVED_MPR) {
                    drawCurve(g2, sx, sy, drawW, drawH);
                }
                drawRulers(g2, sx, sy, drawW, drawH);
            } else if (controller.getLayoutType() == MPRController.LayoutType.CURVED_MPR) {
                // Curved MPR の 3D タイルでもセンターラインを表示
                drawCurveOnCurvedView(g2, sx, sy, drawW, drawH);
            }

            // 測定結果を描画 (HOROS-20240407準拠: ROIManager相当のMeasurementHistory)
            drawMeasurements(g2, sx, sy, drawW, drawH);
        }

        // アノテーション (Horos準拠)
        drawHorosAnnotations(g2);

        // 方位マークは常時表示
        if (direction != Direction.THREE_D) {
            drawOrientationMarkers(g2);
        }
    }

    private void drawRulers(Graphics2D g2, int sx, int sy, int drawW, int drawH) {
        if (controller == null)
            return;

        // HOROS-20240407 準拠: 10cm (100mm) のスケールバーを左側中央と下側中央に表示
        // 色は交差線の縦方向の色
        Color rulerColor = controller.getCrosshairColor(direction, true);
        g2.setColor(rulerColor);
        g2.setStroke(new BasicStroke(1.2f));

        double spX = controller.getSpacing(direction, 0);
        double spY = controller.getSpacing(direction, 1);
        double scaleX = (double) drawW / controller.getDim(direction, 0);
        double scaleY = (double) drawH / controller.getDim(direction, 1);

        double pixelsPerMmX = scaleX / spX;
        double pixelsPerMmY = scaleY / spY;

        int barLenMm = 100; // 10cm
        int barLenPxX = (int) (barLenMm * pixelsPerMmX);
        int barLenPxY = (int) (barLenMm * pixelsPerMmY);

        // 下側中央 (水平スケール)
        int bx = getWidth() / 2 - barLenPxX / 2;
        int by = getHeight() - 45; // 下方位マークの少し上
        g2.drawLine(bx, by, bx + barLenPxX, by);
        for (int i = 0; i <= 10; i++) {
            int tx = bx + (int) (i * 10 * pixelsPerMmX);
            int th = (i % 5 == 0) ? 8 : 4;
            g2.drawLine(tx, by, tx, by - th);
        }

        // 左側中央 (垂直スケール)
        int lx = 45; // 左方位マークの少し内側
        int ly = getHeight() / 2 - barLenPxY / 2;
        g2.drawLine(lx, ly, lx, ly + barLenPxY);
        for (int i = 0; i <= 10; i++) {
            int ty = ly + (int) (i * 10 * pixelsPerMmY);
            int tw = (i % 5 == 0) ? 8 : 4;
            g2.drawLine(lx, ty, lx + tw, ty);
        }
    }

    /**
     * 測定結果を描画する (HOROS-20240407準拠: ROIManager相当のMeasurementHistoryから描画)
     * 
     * @param g2      Graphics2D
     * @param sx      画像描画左上X
     * @param sy      画像描画左上Y
     * @param drawW   画像幅
     * @param drawH   画像高さ
     */
    private void drawMeasurements(Graphics2D g2, int sx, int sy, int drawW, int drawH) {
        if (controller == null || image == null)
            return;

        // サムネイルの場合は測定結果を表示しない
        if (isThumbnail)
            return;

        double[][] basis = getSamplingBasis();
        if (basis == null || basis.length < 3)
            return;

        double[] vx = basis[1], vy = basis[2];
        double volCX = controller.getVolCX(), volCY = controller.getVolCY(), volCZ = controller.getVolCZ();
        double scaleX = (double) drawW / controller.getDim(direction, 0);
        double scaleY = (double) drawH / controller.getDim(direction, 1);

        // 距離測定結果を描画
        java.util.List<com.oculus.jj.model.Measurement3D.DistanceResult> distances = 
            controller.getDistanceMeasurements();
        if (distances != null) {
            int colorIndex = 0;
            Color[] measurementColors = { Color.GREEN, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.WHITE };
            
            for (int i = 0; i < distances.size(); i++) {
                com.oculus.jj.model.Measurement3D.DistanceResult result = distances.get(i);
                if (result == null) continue;

                // 色をローテーション
                g2.setColor(measurementColors[i % measurementColors.length]);
                g2.setStroke(new BasicStroke(2.0f));

                // 始点と終点を画面座標に変換して描画
                com.oculus.jj.model.Measurement3D.Point3D start = result.startPoint;
                com.oculus.jj.model.Measurement3D.Point3D end = result.endPoint;

                int startX, startY, endX, endY;
                
                // 始点を画面座標に変換
                double startDX = start.x - volCX;
                double startDY = start.y - volCY;
                double startDZ = start.z - volCZ;
                double startScreenX = startDX * vx[0] + startDY * vx[1] + startDZ * vx[2];
                double startScreenY = startDX * vy[0] + startDY * vy[1] + startDZ * vy[2];
                
                startX = sx + (int) ((controller.getDim(direction, 0) / 2.0 + startScreenX / controller.getSpacing(direction, 0)) * scaleX);
                startY = sy + (int) ((controller.getDim(direction, 1) / 2.0 + startScreenY / controller.getSpacing(direction, 1)) * scaleY);

                // 終点を画面座標に変換
                double endDX = end.x - volCX;
                double endDY = end.y - volCY;
                double endDZ = end.z - volCZ;
                double endScreenX = endDX * vx[0] + endDY * vx[1] + endDZ * vx[2];
                double endScreenY = endDX * vy[0] + endDY * vy[1] + endDZ * vy[2];
                
                endX = sx + (int) ((controller.getDim(direction, 0) / 2.0 + endScreenX / controller.getSpacing(direction, 0)) * scaleX);
                endY = sy + (int) ((controller.getDim(direction, 1) / 2.0 + endScreenY / controller.getSpacing(direction, 1)) * scaleY);

                // 測定ラインを描画
                g2.drawLine(startX, startY, endX, endY);

                // 始点と終点を丸でマーク
                g2.fillOval(startX - 4, startY - 4, 8, 8);
                g2.drawOval(startX - 4, startY - 4, 8, 8);
                g2.fillOval(endX - 4, endY - 4, 8, 8);
                g2.drawOval(endX - 4, endY - 4, 8, 8);

                // 距離値を描画（中央に）
                int midX = (startX + endX) / 2;
                int midY = (startY + endY) / 2;
                
                // 背景を少し暗くして文字を見やすくする
                String distanceText = String.format("%.1f mm", result.distanceMM);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(distanceText);
                g2.setColor(new Color(0, 0, 0, 128));
                g2.fillRect(midX - textWidth / 2 - 4, midY - fm.getHeight() / 2 - 2, textWidth + 8, fm.getHeight() + 4);
                
                g2.setColor(Color.YELLOW);
                g2.drawString(distanceText, midX - fm.stringWidth(distanceText) / 2, midY + fm.getAscent() / 2);

                colorIndex++;
            }
        }

        // 角度測定結果を描画
        java.util.List<com.oculus.jj.model.Measurement3D.AngleResult> angles = 
            controller.getAngleMeasurements();
        if (angles != null) {
            int colorIndex = 0;
            Color[] angleColors = { new Color(255, 165, 0), new Color(173, 216, 230), 
                                    new Color(255, 192, 203), new Color(144, 238, 144) };
            
            for (int i = 0; i < angles.size(); i++) {
                com.oculus.jj.model.Measurement3D.AngleResult result = angles.get(i);
                if (result == null) continue;

                g2.setColor(angleColors[i % angleColors.length]);
                g2.setStroke(new BasicStroke(2.0f));

                // 頂点
                com.oculus.jj.model.Measurement3D.Point3D vertex = result.vertex;
                double vdx = vertex.x - volCX;
                double vdy = vertex.y - volCY;
                double vdZ = vertex.z - volCZ;
                double vScreenX = vdx * vx[0] + vdy * vx[1] + vdZ * vx[2];
                double vScreenY = vdx * vy[0] + vdy * vy[1] + vdZ * vy[2];
                
                int vertexX = sx + (int) ((controller.getDim(direction, 0) / 2.0 + vScreenX / controller.getSpacing(direction, 0)) * scaleX);
                int vertexY = sy + (int) ((controller.getDim(direction, 1) / 2.0 + vScreenY / controller.getSpacing(direction, 1)) * scaleY);

                // 頂点を三角でマーク
                java.awt.Polygon triangle = new java.awt.Polygon();
                triangle.addPoint(vertexX, vertexY - 6);
                triangle.addPoint(vertexX - 5, vertexY + 4);
                triangle.addPoint(vertexX + 5, vertexY + 4);
                g2.fillPolygon(triangle);
                g2.drawPolygon(triangle);

                // 脚1を描画
                com.oculus.jj.model.Measurement3D.Point3D p1 = result.p1;
                double p1dx = p1.x - volCX;
                double p1dy = p1.y - volCY;
                double p1dz = p1.z - volCZ;
                double p1ScreenX = p1dx * vx[0] + p1dy * vx[1] + p1dz * vx[2];
                double p1ScreenY = p1dx * vy[0] + p1dy * vy[1] + p1dz * vy[2];
                
                int p1X = sx + (int) ((controller.getDim(direction, 0) / 2.0 + p1ScreenX / controller.getSpacing(direction, 0)) * scaleX);
                int p1Y = sy + (int) ((controller.getDim(direction, 1) / 2.0 + p1ScreenY / controller.getSpacing(direction, 1)) * scaleY);
                
                g2.drawLine(vertexX, vertexY, p1X, p1Y);
                g2.fillOval(p1X - 3, p1Y - 3, 6, 6);

                // 脚2を描画
                com.oculus.jj.model.Measurement3D.Point3D p2 = result.p2;
                double p2dx = p2.x - volCX;
                double p2dy = p2.y - volCY;
                double p2dz = p2.z - volCZ;
                double p2ScreenX = p2dx * vx[0] + p2dy * vx[1] + p2dz * vx[2];
                double p2ScreenY = p2dx * vy[0] + p2dy * vy[1] + p2dz * vy[2];
                
                int p2X = sx + (int) ((controller.getDim(direction, 0) / 2.0 + p2ScreenX / controller.getSpacing(direction, 0)) * scaleX);
                int p2Y = sy + (int) ((controller.getDim(direction, 1) / 2.0 + p2ScreenY / controller.getSpacing(direction, 1)) * scaleY);
                
                g2.drawLine(vertexX, vertexY, p2X, p2Y);
                g2.fillOval(p2X - 3, p2Y - 3, 6, 6);

                // 角度値を描画（頂点の近く）
                String angleText = String.format("%.1f°", result.angleDegrees);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(angleText);
                
                int labelX = vertexX;
                int labelY = vertexY - 15;
                if (labelY < sy + 10) labelY = sy + 10;
                
                g2.setColor(new Color(0, 0, 0, 128));
                g2.fillRect(labelX - textWidth / 2 - 4, labelY - fm.getHeight() / 2 - 2, textWidth + 8, fm.getHeight() + 4);
                
                g2.setColor(Color.ORANGE);
                g2.drawString(angleText, labelX - fm.stringWidth(angleText) / 2, labelY + fm.getAscent() / 2);

                colorIndex++;
            }
        }
    }

    private void drawCurve(Graphics2D g2, int sx, int sy, int drawW, int drawH) {
        // --- 修正: 曲線(Curve)の描画を CURVED_MPR モード時のみに限定 ---
        if (controller.getLayoutType() != MPRController.LayoutType.CURVED_MPR) {
            return;
        }

        java.util.List<double[]> pts = controller.getCurvePoints();
        if (pts.isEmpty())
            return;

        double[][] basis = getSamplingBasis();
        double[] vx = basis[1], vy = basis[2];
        double volCX = controller.getVolCX(), volCY = controller.getVolCY(), volCZ = controller.getVolCZ();
        double scaleX = (double) drawW / controller.getDim(direction, 0);
        double scaleY = (double) drawH / controller.getDim(direction, 1);

        g2.setColor(Color.YELLOW);
        g2.setStroke(new BasicStroke(2.0f));

        // 制御点を描画
        for (double[] p : pts) {
            double dx = (p[0] - volCX), dy = (p[1] - volCY), dz = (p[2] - volCZ);
            double px = dx * vx[0] + dy * vx[1] + dz * vx[2];
            double py = dx * vy[0] + dy * vy[1] + dz * vy[2];

            int screenX = sx + (int) ((controller.getDim(direction, 0) / 2.0 + px / controller.getSpacing(direction, 0))
                    * scaleX);
            int screenY = sy + (int) ((controller.getDim(direction, 1) / 2.0 + py / controller.getSpacing(direction, 1))
                    * scaleY);

            // 制御点を少し大きめの丸で描画
            g2.fillOval(screenX - 4, screenY - 4, 8, 8);
        }

        // 線を描画 (点数が多い場合は補完した方が良いが、まずは単純な折れ線で描画を確実にする)
        if (pts.size() >= 2) {
            Path2D.Double path = new Path2D.Double();
            boolean first = true;
            for (double[] p : pts) {
                double dx = (p[0] - volCX), dy = (p[1] - volCY), dz = (p[2] - volCZ);
                double px = dx * vx[0] + dy * vx[1] + dz * vx[2];
                double py = dx * vy[0] + dy * vy[1] + dz * vy[2];

                int screenX = sx
                        + (int) ((controller.getDim(direction, 0) / 2.0 + px / controller.getSpacing(direction, 0))
                                * scaleX);
                int screenY = sy
                        + (int) ((controller.getDim(direction, 1) / 2.0 + py / controller.getSpacing(direction, 1))
                                * scaleY);

                if (first) {
                    path.moveTo(screenX, screenY);
                    first = false;
                } else {
                    path.lineTo(screenX, screenY);
                }
            }
            g2.draw(path);

            // --- 追加: マウスオーバー時の緑点 (Hover highlight) ---
            double[] hoverPt = controller.getHoverCurvePoint();
            if (hoverPt != null && direction != Direction.THREE_D) {
                double hdx = (hoverPt[0] - volCX), hdy = (hoverPt[1] - volCY), hdz = (hoverPt[2] - volCZ);
                double hpx = hdx * vx[0] + hdy * vx[1] + hdz * vx[2];
                double hpy = hdx * vy[0] + hdy * vy[1] + hdz * vy[2];
                double hpz = hdx * basis[0][0] + hdy * basis[0][1] + hdz * basis[0][2];

                // 現在のスライスに近い場合のみ描画
                if (Math.abs(hpz) < 2.0) {
                    int hx = sx
                            + (int) ((controller.getDim(direction, 0) / 2.0 + hpx / controller.getSpacing(direction, 0))
                                    * scaleX);
                    int hy = sy
                            + (int) ((controller.getDim(direction, 1) / 2.0 + hpy / controller.getSpacing(direction, 1))
                                    * scaleY);
                    g2.setColor(Color.GREEN);
                    g2.fillOval(hx - 5, hy - 5, 10, 10);
                    g2.setColor(Color.WHITE);
                    g2.drawOval(hx - 5, hy - 5, 10, 10);
                }
            }
        }
    }

    private void drawCurveOnCurvedView(Graphics2D g2, int sx, int sy, int drawW, int drawH) {
        java.util.List<double[]> pts = controller.getCurvePoints();
        if (pts.size() < 2)
            return;

        // 総延長の計算 (px位置の計算用)
        double totalLen = 0;
        double[] segmentLens = new double[pts.size() - 1];
        for (int i = 0; i < pts.size() - 1; i++) {
            double d = Math.sqrt(Math.pow(pts.get(i + 1)[0] - pts.get(i)[0], 2)
                    + Math.pow(pts.get(i + 1)[1] - pts.get(i)[1], 2) + Math.pow(pts.get(i + 1)[2] - pts.get(i)[2], 2));
            segmentLens[i] = d;
            totalLen += d;
        }

        // 点線で中心線を表示
        g2.setColor(Color.YELLOW);
        g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 5 }, 0));
        int cy = sy + (int) (drawH * 0.5);
        g2.drawLine(sx, cy, sx + drawW, cy);

        // HOROS-2024.04.07 準拠: A, B, C の３本の垂直線を表示 (Bを中心に一定幅で移動する)
        g2.setStroke(new BasicStroke(1.0f));
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));

        double distFocus = controller.getCurveDistanceAtFocus();
        double interval = controller.getThumbInterval();
        double[] dists = { distFocus - interval, distFocus, distFocus + interval };
        String[] labels = { "A", "B", "C" };

        for (int i = 0; i < labels.length; i++) {
            double d = dists[i];
            if (d < 0)
                d = 0;
            if (d > totalLen)
                d = totalLen;

            int px = sx + (int) ((d / totalLen) * drawW);
            g2.setColor(Color.YELLOW);
            g2.drawLine(px, sy, px, sy + drawH);
            g2.drawString(labels[i], px + 4, sy + 20);
        }

        // --- 追加: 制御点を中心線上に描画 ---
        g2.setColor(new Color(255, 200, 0));
        double curLen = 0;
        for (int i = 0; i < pts.size(); i++) {
            int px = sx + (int) ((curLen / totalLen) * drawW);
            g2.fillOval(px - 3, cy - 3, 6, 6);
            g2.drawOval(px - 4, cy - 4, 8, 8);
            if (i < pts.size() - 1)
                curLen += segmentLens[i];
        }
    }

    private void drawHorosAnnotations(Graphics2D g2) {
        if (g2 == null || controller == null)
            return;

        int w = getWidth(), h = getHeight();
        boolean highlighted = (controller.getHighlightedView() == direction);
        MPRController.LayoutType layout = controller.getLayoutType();

        // 1. 枠線の描画 (Horos Style)
        Color borderColor = highlighted ? Color.RED
                : (layout == MPRController.LayoutType.ORTHOGONAL ? Color.YELLOW
                        : (layout == MPRController.LayoutType.CURVED_MPR ? Color.YELLOW : Color.CYAN));
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(highlighted ? 3.0f : 1.0f));
        g2.drawRect(1, 1, w - 3, h - 3);

        // HOROS-2024.04.07 準拠
        // サムネイルのアノテーションは draw3DAnnotations 内で制御

        // 2. モードに応じたアノテーションの分岐
        if (layout == MPRController.LayoutType.ORTHOGONAL) {
            draw2DAnnotations(g2, highlighted);
        } else {
            draw3DAnnotations(g2, highlighted, layout);
        }
    }

    private void draw2DAnnotations(Graphics2D g2, boolean highlighted) {
        // 2D Orthogonal モードではアクティブな（赤枠の）ビューのみに詳細情報を表示
        if (!highlighted)
            return;

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(Color.WHITE);
        int lx = 10, ly = 25;

        // HOROS-2024.04.07 準拠: 文字列をローカライズ
        String[] labels = {
                com.oculus.jj.utils.LanguageManager.get("mpr.label.sagittal"),
                com.oculus.jj.utils.LanguageManager.get("mpr.label.coronal"),
                com.oculus.jj.utils.LanguageManager.get("mpr.label.axial")
        };
        String labelStr = (direction == Direction.THREE_D) ? "3D" : labels[direction.ordinal()];

        g2.drawString(labelStr, lx, ly);
        ly += 16;
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.drawString(String.format(com.oculus.jj.utils.LanguageManager.get("mpr.info.wlww"),
                controller.getWindowLevel(), controller.getWindowWidth()), lx, ly);
        ly += 14;
        Point lp = getLocalPixelIndex();
        g2.drawString(String.format(
                com.oculus.jj.utils.LanguageManager.get("mpr.info.pos_vox") + " "
                        + com.oculus.jj.utils.LanguageManager.get("mpr.info.voxelValue"),
                lp.x, lp.y, controller.getVoxelValueAtFocus()), lx, ly);
        ly += 14;
        g2.drawString(String.format(com.oculus.jj.utils.LanguageManager.get("mpr.info.pos_mm"),
                controller.getCurX(), controller.getCurY(), controller.getCurZ()), lx, ly);
    }

    private void draw3DAnnotations(Graphics2D g2, boolean highlighted, MPRController.LayoutType layout) {
        if (g2 == null)
            return;

        int w = getWidth(), h = getHeight();
        if (isThumbnail) {
            // HOROS-2024.04.07 準拠: サムネイルは情報を最小限にする（画像が見えなくなるのを防ぐ）
            g2.setFont(new Font("SansSerif", Font.BOLD, 18));
            g2.setColor(Color.YELLOW);
            g2.drawString(thumbLabel, 12, 28);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(new Color(255, 255, 255, 180));
            double distFocus = controller.getCurveDistanceAtFocus();
            double interval = controller.getThumbInterval();
            double targetDist = distFocus;
            if (thumbnailIndex == 0)
                targetDist = distFocus - interval;
            else if (thumbnailIndex == 2)
                targetDist = distFocus + interval;

            g2.drawString(String.format("%.1f mm", targetDist), 12, h - 12);
            return;
        }

        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // --- A. タイトル (最上部中央) ---
            if (direction == Direction.THREE_D) {
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                g2.setColor(Color.CYAN);
                String title = (layout == MPRController.LayoutType.CURVED_MPR)
                        ? com.oculus.jj.utils.LanguageManager.get("mpr.title.cpr")
                        : com.oculus.jj.utils.LanguageManager.get("mpr.title.3d");
                g2.drawString(title, w / 2 - g2.getFontMetrics().stringWidth(title) / 2, 25);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            }

            // --- B. テキストアノテーション (4隅) ---
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(Color.WHITE);
            int lx = 15, ly = (direction == Direction.THREE_D) ? 45 : 25;

            if (controller != null && !isThumbnail) {
                g2.drawString(String.format(com.oculus.jj.utils.LanguageManager.get("mpr.info.imageSize"),
                        controller.getDimOriginalX(), controller.getDimOriginalY()), lx, ly);
                ly += 15;
                g2.drawString(String.format(com.oculus.jj.utils.LanguageManager.get("mpr.info.viewSize"),
                        w, h), lx, ly);
                ly += 15;
                g2.drawString(String.format(com.oculus.jj.utils.LanguageManager.get("mpr.info.wlww"),
                        controller.getWindowLevel(), controller.getWindowWidth()), lx, ly);
                ly += 15;

                Point lp = (direction == Direction.THREE_D) ? new Point(0, 0) : getLocalPixelIndex();
                g2.drawString(String.format(
                        com.oculus.jj.utils.LanguageManager.get("mpr.info.pos_vox") + " "
                                + com.oculus.jj.utils.LanguageManager.get("mpr.info.voxelValue"),
                        lp.x, lp.y, controller.getVoxelValueAtFocus()), lx, ly);
                ly += 15;
                g2.drawString(String.format(com.oculus.jj.utils.LanguageManager.get("mpr.info.pos_mm")
                        .replace(" Z: %.2f mm", ""), // 2DビューではZを隠す（オプション）
                        controller.getCurX(), controller.getCurY()), lx, ly);
                ly += 15;

                // --- 追加: Slab 情報 (Horos準拠) ---
                if (controller.getSlabMode() != MPRController.SlabMode.NONE) {
                    g2.setColor(new Color(255, 200, 0)); // 黄色
                    String slabInfo = String.format(com.oculus.jj.utils.LanguageManager.get("mpr.info.slabMode"),
                            controller.getSlabMode().name());
                    g2.drawString(slabInfo, lx, ly);
                    ly += 15;
                    String thickInfo = String.format(com.oculus.jj.utils.LanguageManager.get("mpr.info.thickness"),
                            controller.getSlabThickness());
                    g2.drawString(thickInfo, lx, ly);
                    ly += 15;
                    g2.setColor(Color.WHITE);
                }

                // Curved MPR の場合は距離を表示 (Horos風)
                if (direction == Direction.THREE_D && layout == MPRController.LayoutType.CURVED_MPR) {
                    java.util.List<double[]> pts = controller.getCurvePoints();
                    if (pts.size() >= 2) {
                        double totalD = 0;
                        for (int i = 0; i < pts.size() - 1; i++) {
                            totalD += Math.sqrt(Math.pow(pts.get(i + 1)[0] - pts.get(i)[0], 2)
                                    + Math.pow(pts.get(i + 1)[1] - pts.get(i)[1], 2)
                                    + Math.pow(pts.get(i + 1)[2] - pts.get(i)[2], 2));
                        }
                        g2.drawString(String.format(com.oculus.jj.utils.LanguageManager.get("mpr.info.length"), totalD),
                                lx, ly);
                    }
                }
            }

            // 右上: 患者情報 (Horos形式)
            if (controller != null && !isThumbnail) {
                ly = 25;
                String pHeader = controller.getPatientHeader();
                g2.drawString(pHeader, w - g2.getFontMetrics().stringWidth(pHeader) - 15, ly);
                ly += 15;
                String pName = controller.getPatientName();
                g2.drawString(pName, w - g2.getFontMetrics().stringWidth(pName) - 15, ly);
                ly += 15;
                String sDesc = controller.getSeriesInfo();
                g2.drawString(sDesc, w - g2.getFontMetrics().stringWidth(sDesc) - 15, ly);
            }

            // 右下: 日時・Made in Horos
            if (controller != null && !isThumbnail) {
                ly = h - 35;
                String dtInfo = controller.getStudyDate();
                g2.drawString(dtInfo, w - g2.getFontMetrics().stringWidth(dtInfo) - 15, ly);
                ly += 16;
                String madeIn = "Made In Horos";
                g2.drawString(madeIn, w - g2.getFontMetrics().stringWidth(madeIn) - 15, ly);
            }

            // サムネイル固有情報 (A, B, C / 距離)
            if (isThumbnail) {
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                g2.setColor(Color.YELLOW);
                g2.drawString(thumbLabel, 12, 25);

                double dFocus = controller.getCurveDistanceAtFocus();
                double interval = controller.getThumbInterval();
                double targetDist = dFocus;
                if (thumbnailIndex == 0)
                    targetDist = dFocus - interval;
                else if (thumbnailIndex == 2)
                    targetDist = dFocus + interval;

                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.drawString(String.format(com.oculus.jj.utils.LanguageManager.get("mpr.info.distance"), targetDist),
                        12, h - 12);
            }
        } catch (Exception ex) {
            // ignore
        }
    }

    private void drawOrientationMarkers(Graphics2D g2) {
        if (direction == Direction.THREE_D)
            return;

        double[][] basis = getSamplingBasis();
        double[] vx = basis[1], vy = basis[2];

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();

        // vx が +X なら、画面の右端は "L" (Left)、左端は "R" (Right)
        String rightLabel = getOrientationLabel(vx);
        String leftLabel = getOrientationLabel(scaleVector(vx, -1));
        String bottomLabel = getOrientationLabel(vy);
        String topLabel = getOrientationLabel(scaleVector(vy, -1));

        // Top
        g2.drawString(topLabel, getWidth() / 2 - fm.stringWidth(topLabel) / 2, 25);
        // Bottom
        g2.drawString(bottomLabel, getWidth() / 2 - fm.stringWidth(bottomLabel) / 2, getHeight() - 15);
        // Left
        g2.drawString(leftLabel, 15, getHeight() / 2 + fm.getAscent() / 2);
        // Right
        g2.drawString(rightLabel, getWidth() - fm.stringWidth(rightLabel) - 15, getHeight() / 2 + fm.getAscent() / 2);
    }

    private double[] scaleVector(double[] v, double s) {
        return new double[] { v[0] * s, v[1] * s, v[2] * s };
    }

    private Point getLocalPixelIndex() {
        if (controller == null)
            return new Point(0, 0);

        double[][] basis = getSamplingBasis();
        double[] vx = basis[1], vy = basis[2];

        double dx = controller.getCurX() - controller.getVolCX();
        double dy = controller.getCurY() - controller.getVolCY();
        double dz = controller.getCurZ() - controller.getVolCZ();

        double u_mm = dx * vx[0] + dy * vx[1] + dz * vx[2];
        double v_mm = dx * vy[0] + dy * vy[1] + dz * vy[2];

        double spX = controller.getSpacing(direction, 0);
        double spY = controller.getSpacing(direction, 1);

        int px = (int) Math.round(controller.getDim(direction, 0) / 2.0 + u_mm / spX);
        int py = (int) Math.round(controller.getDim(direction, 1) / 2.0 + v_mm / spY);

        return new Point(px, py);
    }

    private String getOrientationLabel(double[] v) {
        // DICOM LPS 世界座標系
        // X: +Left (Right -> Left)
        // Y: +Posterior (Anterior -> Posterior)
        // Z: +Superior (Inferior -> Superior)
        double ax = Math.abs(v[0]), ay = Math.abs(v[1]), az = Math.abs(v[2]);
        if (ax > ay && ax > az)
            return (v[0] > 0) ? com.oculus.jj.utils.LanguageManager.get("mpr.label.left")
                    : com.oculus.jj.utils.LanguageManager.get("mpr.label.right");
        if (ay > ax && ay > az)
            return (v[1] > 0) ? com.oculus.jj.utils.LanguageManager.get("mpr.label.posterior")
                    : com.oculus.jj.utils.LanguageManager.get("mpr.label.anterior");
        return (v[2] > 0) ? com.oculus.jj.utils.LanguageManager.get("mpr.label.superior")
                : com.oculus.jj.utils.LanguageManager.get("mpr.label.inferior");
    }

    private void drawCrosshairs(Graphics2D g2, int sx, int sy, int drawW, int drawH) {
        Point c = getImageScreenPos();
        if (c == null)
            return;

        double[][] basis = getSamplingBasis();
        double[] vn = basis[0], vx = basis[1], vy = basis[2];

        double[] vS = controller.getVS();
        double[] vC = controller.getVC();
        double[] vA = controller.getVA();

        g2.setStroke(new BasicStroke(1.0f));

        // 2D Orthogonal MPR の場合は中央を抜くが、3D MPR 系の場合は交点を描画する
        MPRController.LayoutType layout = controller.getLayoutType();
        boolean hasGap = (layout == MPRController.LayoutType.ORTHOGONAL);

        switch (direction) {
            case AXIAL:
                drawProjectedLine(g2, c, vx, vy, vn, vC, controller.getCrosshairColor(direction, false), drawW, drawH,
                        sx, sy, hasGap); // Horizontal (Coronal)
                drawProjectedLine(g2, c, vx, vy, vn, vS, controller.getCrosshairColor(direction, true), drawW, drawH,
                        sx, sy, hasGap); // Vertical (Sagittal)
                break;
            case SAGITTAL:
                drawProjectedLine(g2, c, vx, vy, vn, vA, controller.getCrosshairColor(direction, false), drawW, drawH,
                        sx, sy, hasGap); // Horizontal (Axial)
                drawProjectedLine(g2, c, vx, vy, vn, vC, controller.getCrosshairColor(direction, true), drawW, drawH,
                        sx, sy, hasGap); // Vertical (Coronal)
                break;
            case CORONAL:
                drawProjectedLine(g2, c, vx, vy, vn, vA, controller.getCrosshairColor(direction, false), drawW, drawH,
                        sx, sy, hasGap); // Horizontal (Axial)
                drawProjectedLine(g2, c, vx, vy, vn, vS, controller.getCrosshairColor(direction, true), drawW, drawH,
                        sx, sy, hasGap); // Vertical (Sagittal)
                break;
            default:
                break;
        }

        // 3D MPR の場合は交点ドットを赤色で描画 (Horos 3D準拠)
        if (!hasGap) {
            g2.setColor(Color.RED);
            g2.fillOval(c.x - 2, c.y - 2, 5, 5);
            g2.setColor(Color.BLACK);
            g2.drawOval(c.x - 2, c.y - 2, 5, 5);
        }
    }

    private void drawProjectedLine(Graphics2D g2, Point c, double[] vr, double[] vd, double[] n1, double[] n2,
            Color color, int drawW, int drawH, int sx, int sy, boolean hasGap) {
        // 交差線の方向 L = n1 x n2
        double lx = n1[1] * n2[2] - n1[2] * n2[1];
        double ly = n1[2] * n2[0] - n1[0] * n2[2];
        double lz = n1[0] * n2[1] - n1[1] * n2[0];

        // スクリーン上での方向
        double dx = lx * vr[0] + ly * vr[1] + lz * vr[2];
        double dy = lx * vd[0] + ly * vd[1] + lz * vd[2];

        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < 1e-6)
            return;
        dx /= d;
        dy /= d;

        g2.setColor(color);
        double len = Math.max(drawW, drawH) * 2;

        if (hasGap) {
            // HOROS-20240407準拠: 真ん中を丸く抜くためのギャップ（半径24ピクセル）
            int radius = 24;
            g2.drawLine((int) (c.x - dx * len), (int) (c.y - dy * len), (int) (c.x - dx * radius),
                    (int) (c.y - dy * radius));
            g2.drawLine((int) (c.x + dx * radius), (int) (c.y + dy * radius), (int) (c.x + dx * len),
                    (int) (c.y + dy * len));
        } else {
            // ギャップなしで1本の線を描画
            g2.drawLine((int) (c.x - dx * len), (int) (c.y - dy * len), (int) (c.x + dx * len), (int) (c.y + dy * len));
        }
    }
}
