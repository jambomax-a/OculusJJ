package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;
import com.oculus.jj.model.DicomPix;
import com.oculus.jj.model.Measurement3D;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;
import org.lwjgl.opengl.GL;

/**
 * Viewer3DController - 3Dビューワー (MIP/VR)
 * HOROS-20240407準拠: VRController.h, VRController.m 相当
 */
public class Viewer3DController extends JFrame {

    private ViewerController viewer2D;
    private List<DicomPix> pixList;
    private short[][][] volume; // [z][y][x]
    private int dimX, dimY, dimZ;
    private double spacingZ = 1.0;

    private RenderingPanel renderingPanel;
    private GLViewer3D glViewer;
    private String mode = "MIP"; // "MIP", "VR", "MinIP", "Average", "ISO"
    private boolean shadingEnabled = true;

    // Window Level/Width
    private float windowLevel = 40;
    private float windowWidth = 400;

    // ISO Threshold for Surface Rendering
    private int isoThreshold = 300;
    private double resolution = 0.5;

    // 視点回転パラメータ
    private double rotateX = -Math.PI / 2; // デフォルトを正面(Coronal)向きに調整
    private double rotateY = 0;
    private double zoom = 1.0;

    private boolean isDragging = false;

    private enum Tool3D {
        ROTATE, ZOOM, PAN, WLWW, MEASURE, ROI_POINT, VR_ADJUST
    }

    private Tool3D currentTool = Tool3D.ROTATE;
    private List<com.oculus.jj.model.ROI> roiList = new ArrayList<>();
    private com.oculus.jj.model.ROI activeRoi = null;

    public void updateWindow(float dLevel, float dWidth) {
        this.windowLevel += dLevel;
        this.windowWidth += dWidth;
        if (this.windowWidth < 1)
            this.windowWidth = 1;
        repaint();
    }

    public Viewer3DController(ViewerController viewer2D, String mode) {
        super("3D Viewer - " + mode);
        this.viewer2D = viewer2D;
        setMode(mode);

        // initializeData(); // Don't call here, we will do it asynchronously
        initializeUI();

        // Asynchronously prepare data and launch GPU renderer
        startAsyncGPUInitialization();
    }

    private void initializeData() {
        // Stub for compatibility, actual work moved to startAsyncGPUInitialization
    }

    private void startAsyncGPUInitialization() {
        final JLabel loadingLabel = new JLabel("Preparing Volume Data (Converting to HU)...", JLabel.CENTER);
        loadingLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        this.add(loadingLabel, BorderLayout.CENTER);
        this.revalidate();

        new Thread(() -> {
            try {
                List<List<DicomPix>> allPix = viewer2D.getPixList();
                if (allPix == null || allPix.isEmpty())
                    return;
                this.pixList = new ArrayList<>(allPix.get(0));
                if (pixList == null || pixList.isEmpty())
                    return;

                DicomPix first = pixList.get(0);
                dimX = first.getWidth();
                dimY = first.getHeight();
                dimZ = pixList.size();
                spacingZ = (first.getSliceThickness() <= 0.1) ? 1.0 : first.getSliceThickness();

                volume = new short[dimZ][dimY][dimX];
                for (int z = 0; z < dimZ; z++) {
                    DicomPix pix = pixList.get(z);
                    short[] src = pix.getShortData();
                    if (src != null) {
                        float s = pix.getRescaleSlope();
                        float i = pix.getRescaleIntercept();
                        for (int y = 0; y < dimY; y++) {
                            for (int x = 0; x < dimX; x++) {
                                volume[z][y][x] = (short) (src[y * dimX + x] * s + i);
                            }
                        }
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    this.remove(loadingLabel);
                    setupGPURenderer();
                    this.revalidate();
                    this.repaint();
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    loadingLabel.setText("Error preparing data: " + e.getMessage());
                });
            }
        }).start();
    }

    private AWTGLCanvas canvas;
    private boolean isRendering = false;

    private void setMode(String newMode) {
        this.mode = newMode;
        if (glViewer != null) {
            if ("MIP".equals(newMode) || "MinIP".equals(newMode) || "Average".equals(newMode)) {
                glViewer.setRenderMode(0);
            } else if ("VR".equals(newMode)) {
                glViewer.setRenderMode(1);
            } else if ("ISO".equals(newMode)) {
                glViewer.setRenderMode(2);
            }
        }
        repaint();
    }

    private void setupGPURenderer() {
        try {
            if (glViewer == null) {
                glViewer = new GLViewer3D();
                setMode(this.mode); // Apply initial mode

                GLData data = new GLData();
                data.api = GLData.API.GL;
                data.majorVersion = 3;
                data.minorVersion = 3;
                data.profile = GLData.Profile.CORE;
                data.samples = 0;

                canvas = new AWTGLCanvas(data) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void initGL() {
                        GL.createCapabilities();
                        glViewer.initGL();
                    }

                    @Override
                    public void paintGL() {
                        glViewer.paintGL(getWidth(), getHeight());
                        swapBuffers();
                    }
                };

                if (renderingPanel != null) {
                    remove(renderingPanel);
                }
                for (Component c : getContentPane().getComponents()) {
                    if (c instanceof JLabel) {
                        remove(c);
                    }
                }

                glViewer.setVolumeData(volume, dimX, dimY, dimZ, pixList);

                java.awt.event.MouseAdapter syncMouse = new java.awt.event.MouseAdapter() {
                    private Point lastPos;

                    @Override
                    public void mousePressed(MouseEvent e) {
                        lastPos = e.getPoint();
                    }

                    @Override
                    public void mouseDragged(MouseEvent e) {
                        if (lastPos != null) {
                            int dx = e.getX() - lastPos.x;
                            int dy = e.getY() - lastPos.y;
                            if (SwingUtilities.isRightMouseButton(e) || currentTool == Tool3D.ZOOM) {
                                zoom *= (1.0 - dy / 200.0);
                                if (zoom < 0.1)
                                    zoom = 0.1;
                                                        } else if (SwingUtilities.isMiddleMouseButton(e)) {
                                boolean isShift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;
                                if (isShift) {
                                    windowLevel += dy * 2.0f;
                                    windowWidth += dx * 2.0f;
                                    if (windowWidth < 1) windowWidth = 1;
                                } else {
                                    float sensitivity = 0.001f;
                                    glViewer.addOffset((float)(lastPos.x - e.getX()) * sensitivity, (float)(lastPos.y - e.getY()) * sensitivity, 0);
                                }
                                repaint();
                            } else {
                                rotateY += dx * 0.01;
                                rotateX += dy * 0.01;
                            }
                            glViewer.setRotation((float) rotateX, (float) rotateY);
                            glViewer.setZoom((float) zoom);
                            glViewer.setWindowLevel(windowLevel, windowWidth);
                            lastPos = e.getPoint();
                        }
                    }

                    @Override
                    public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                        zoom *= (1.0 - e.getWheelRotation() * 0.1);
                        if (zoom < 0.1)
                            zoom = 0.1;
                        glViewer.setZoom((float) zoom);
                    }
                };
                canvas.addMouseListener(syncMouse);
                canvas.addMouseMotionListener(syncMouse);
                canvas.addMouseWheelListener(syncMouse);

                glViewer.setRotation((float) rotateX, (float) rotateY);
                glViewer.setZoom((float) zoom);
                glViewer.setWindowLevel(windowLevel, windowWidth);

                this.add(canvas, BorderLayout.CENTER);
                this.revalidate();
                this.repaint();

                isRendering = true;
                Thread renderThread = new Thread(() -> {
                    while (isRendering) {
                        canvas.render();
                        try {
                            Thread.sleep(16);
                        } catch (Exception e) {
                        }
                    }
                });
                renderThread.setDaemon(true);
                renderThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.add(new JLabel("GPU Acceleration failed: " + e.getMessage()), BorderLayout.CENTER);
            this.revalidate();
            this.repaint();
        }
    }

    private void initializeUI() {
        // HOROS-20240407準拠: 3D Viewerも大画面または全画面で表示
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // HOROS-20240407準拠: 3D Viewerも独立したメニューバーを持つ（Windows環境）
        BrowserController browser = BrowserController.currentBrowser();
        if (browser != null && browser.getJMenuBar() != null) {
            JMenuBar replicated = ViewerController.replicateMenuBar(browser.getJMenuBar());
            // 3D固有のメニューを追加
            JMenu modeMenu = new JMenu("3D Mode");
            modeMenu.setForeground(Color.BLACK);

            JMenuItem mipItem = new JMenuItem("MIP");
            mipItem.addActionListener(e -> {
                setMode("MIP");
                repaint();
            });
            modeMenu.add(mipItem);

            JMenuItem vrItem = new JMenuItem("Volume Rendering");
            vrItem.addActionListener(e -> {
                setMode("VR");
                repaint();
            });
            modeMenu.add(vrItem);

            replicated.add(modeMenu); // 最後に配置
            setJMenuBar(replicated);
        }

        setLayout(new BorderLayout());

        // HOROS-20240407準拠: VR.xib 相当のツールバー
        JToolBar vrToolbar = new JToolBar();
        vrToolbar.setFloatable(false);
        vrToolbar.setBackground(new Color(240, 240, 240));

        JButton btnMIP = new JButton("MIP");
        btnMIP.addActionListener(e -> {
            setMode("MIP");
            setTitle("3D Viewer - MIP");
            repaint();
        });
        vrToolbar.add(btnMIP);

        JButton btnMinIP = new JButton("MinIP");
        btnMinIP.addActionListener(e -> {
            setMode("MinIP");
            setTitle("3D Viewer - MinIP");
            repaint();
        });
        vrToolbar.add(btnMinIP);

        JButton btnAvg = new JButton("Average");
        btnAvg.addActionListener(e -> {
            setMode("Average");
            setTitle("3D Viewer - Average");
            repaint();
        });
        vrToolbar.add(btnAvg);

        JButton btnVR = new JButton("Volume Rendering");
        btnVR.addActionListener(e -> {
            setMode("VR");
            setTitle("3D Viewer - VR");
            repaint();
        });
        vrToolbar.add(btnVR);

        JButton btnISO = new JButton("3D Surface");
        btnISO.addActionListener(e -> {
            setMode("ISO");
            setTitle("3D Viewer - Surface Rendering");
            repaint();
        });
        vrToolbar.add(btnISO);

        vrToolbar.addSeparator();

        vrToolbar.add(new JLabel(" ISO: "));
        JSpinner isoSpinner = new JSpinner(new SpinnerNumberModel(300, -1024, 3072, 50));
        isoSpinner.addChangeListener(e -> {
            this.isoThreshold = (int) isoSpinner.getValue();
            repaint();
        });
        vrToolbar.add(isoSpinner);

        vrToolbar.addSeparator();

        JToggleButton tglShading = new JToggleButton("Shading", true);
        tglShading.addActionListener(e -> {
            this.shadingEnabled = tglShading.isSelected();
            repaint();
        });
        vrToolbar.add(tglShading);

        vrToolbar.addSeparator();

        JButton btnReset = new JButton("Reset");
        btnReset.addActionListener(e -> {
            rotateX = -Math.PI / 2;
            rotateY = 0;
            zoom = 1.0;
            windowLevel = 40;
            windowWidth = 400;
            repaint();
        });
        vrToolbar.add(btnReset);

        vrToolbar.addSeparator();

        JToggleButton tglMeasure = new JToggleButton("Measure (Line)");
        tglMeasure.addActionListener(e -> {
            if (tglMeasure.isSelected()) {
                this.currentTool = Tool3D.MEASURE;
                activeRoi = new com.oculus.jj.model.ROI(DCMView.ToolMode.tMesure, 1, 1, new Point2D.Float(0, 0));
                activeRoi.setName("3D Measurement " + (roiList.size() + 1));
            } else {
                this.currentTool = Tool3D.ROTATE;
                activeRoi = null;
            }
            repaint();
        });
        vrToolbar.add(tglMeasure);

        JToggleButton tglPoint = new JToggleButton("Point");
        tglPoint.addActionListener(e -> {
            if (tglPoint.isSelected()) {
                this.currentTool = Tool3D.ROI_POINT;
            } else {
                this.currentTool = Tool3D.ROTATE;
            }
            repaint();
        });
        vrToolbar.add(tglPoint);

        JButton btnClearMeasure = new JButton("Clear ROIs");
        btnClearMeasure.addActionListener(e -> {
            roiList.clear();
            activeRoi = null;
            repaint();
        });
        vrToolbar.add(btnClearMeasure);

        vrToolbar.addSeparator();

        JToggleButton tglVRAdjust = new JToggleButton("VR Adjust");
        tglVRAdjust.addActionListener(e -> {
            if (tglVRAdjust.isSelected()) {
                this.currentTool = Tool3D.VR_ADJUST;
            } else {
                this.currentTool = Tool3D.ROTATE;
            }
        });
        vrToolbar.add(tglVRAdjust);

        vrToolbar.addSeparator();
        vrToolbar.add(new JLabel("Presets: "));
        String[] presets = { "Bone", "Soft Tissue", "Lung" };
        JComboBox<String> comboPresets = new JComboBox<>(presets);
        comboPresets.addActionListener(e -> {
            String p = (String) comboPresets.getSelectedItem();
            if ("Bone".equals(p)) {
                windowLevel = 300;
                windowWidth = 1500;
            } else if ("Soft Tissue".equals(p)) {
                windowLevel = 40;
                windowWidth = 400;
            } else if ("Lung".equals(p)) {
                windowLevel = -600;
                windowWidth = 1500;
            }
            repaint();
        });
        vrToolbar.add(comboPresets);

        add(vrToolbar, BorderLayout.NORTH);

        renderingPanel = new RenderingPanel();
        add(renderingPanel, BorderLayout.CENTER);

        // キーボードショートカット (Horos準拠: ESCで閉じる, 各ツール切替)
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE:
                        dispose();
                        break;
                    case KeyEvent.VK_W:
                        currentTool = Tool3D.VR_ADJUST;
                        break;
                    case KeyEvent.VK_S:
                        currentTool = Tool3D.ROTATE;
                        break;
                    case KeyEvent.VK_Z:
                        currentTool = Tool3D.ZOOM;
                        break;
                    case KeyEvent.VK_M:
                        currentTool = Tool3D.MEASURE;
                        activeRoi = new com.oculus.jj.model.ROI(DCMView.ToolMode.tMesure, 1, 1,
                                new Point2D.Float(0, 0));
                        activeRoi.setName("3D Measurement " + (roiList.size() + 1));
                        break;
                    case KeyEvent.VK_P:
                        currentTool = Tool3D.PAN;
                        break;
                }
                repaint();
            }
        });
    }

    private class RenderingPanel extends JPanel {
        private Point lastMousePos;
        private BufferedImage image;

        public RenderingPanel() {
            setBackground(Color.BLACK);

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMousePos = e.getPoint();

                    if (currentTool == Tool3D.MEASURE) {
                        com.oculus.jj.model.ROI.Point3D pt = getVoxelUnderMouse(e.getX(), e.getY());
                        if (pt != null) {
                            if (activeRoi == null || activeRoi.getPoints3D().size() >= 2) {
                                activeRoi = new com.oculus.jj.model.ROI(DCMView.ToolMode.tMesure, 1, 1,
                                        new Point2D.Float(0, 0));
                                activeRoi.setName("3D Length " + (roiList.size() + 1));
                                roiList.add(activeRoi);
                            }
                            activeRoi.addPoint3D(pt);
                            repaint();
                        }
                        return;
                    } else if (currentTool == Tool3D.ROI_POINT) {
                        com.oculus.jj.model.ROI.Point3D pt = getVoxelUnderMouse(e.getX(), e.getY());
                        if (pt != null) {
                            com.oculus.jj.model.ROI pRoi = new com.oculus.jj.model.ROI(DCMView.ToolMode.t2DPoint, 1, 1,
                                    new Point2D.Float(0, 0));
                            pRoi.setName("3D Point " + (roiList.size() + 1));
                            pRoi.addPoint3D(pt);
                            roiList.add(pRoi);
                            repaint();
                        }
                        return;
                    }

                    isDragging = true;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    isDragging = false;
                    repaint(); // 高画質で再描画
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (lastMousePos != null) {
                        int dx = e.getX() - lastMousePos.x;
                        int dy = e.getY() - lastMousePos.y;

                        boolean isRight = SwingUtilities.isRightMouseButton(e);
                        boolean isShift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;

                        if (currentTool == Tool3D.VR_ADJUST) {
                            // VR Adjustment: Up/Down for Level, Left/Right for Width
                            updateWindow(dy * 2.0f, dx * 2.0f);
                        } else if (isRight || isShift || currentTool == Tool3D.ZOOM) {
                            // Zoom
                            zoom *= (1.0 - dy / 200.0);
                            if (zoom < 0.1)
                                zoom = 0.1;
                        } else if (SwingUtilities.isMiddleMouseButton(e) || currentTool == Tool3D.PAN) {
                            // Pan (Currently PAN logic is just changing WLWW, let's fix it to actual PAN)
                            // Note: In 3D Viewer, PAN usually moves the view center.
                            // For simplicity, we use middle button for WLWW as per previous implementation.
                            updateWindow(dy * 2.0f, dx * 2.0f);
                        } else if (currentTool == Tool3D.ROTATE || !isRight) {
                            // Rotate
                            rotateY += dx * 0.01;
                            rotateX += dy * 0.01;
                        }

                        lastMousePos = e.getPoint();
                        repaint();
                    }
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);

            // マウスホイールでのズーム
            addMouseWheelListener(e -> {
                zoom *= (1.0 - e.getWheelRotation() * 0.1);
                if (zoom < 0.1)
                    zoom = 0.1;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (volume == null)
                return;

            Graphics2D g2d = (Graphics2D) g;
            render3D(g2d);
            drawMeasurements(g2d);
        }

        private com.oculus.jj.model.ROI.Point3D getVoxelUnderMouse(int sx, int sy) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0)
                return null;

            double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
            double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);

            double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;
            double baseScale = Math.min(w / (double) dimX, h / (double) dimY) * 0.8;
            double currentScale = baseScale * zoom;

            double vx = (sx - w / 2.0) / currentScale;
            double vy = (sy - h / 2.0) / currentScale;

            double zLimit = Math.max(dimX, Math.max(dimY, dimZ)) * 1.2;

            // レイの方向に沿って、不透明度が一定値を超えるかISO閾値を超える場所を探索
            double rayStep = 1.0;
            for (double vz = -zLimit; vz < zLimit; vz += rayStep) {
                double tx = vx * cosY + vz * sinY;
                double tz0 = -vx * sinY + vz * cosY;
                double ty = vy * cosX - tz0 * sinX;
                double tz = vy * sinX + tz0 * cosX;

                int ix = (int) (tx + cx), iy = (int) (ty + cy), iz = (int) (tz + cz);

                if (ix >= 0 && ix < dimX && iy >= 0 && iy < dimY && iz >= 0 && iz < dimZ) {
                    int val = volume[iz][iy][ix];

                    // モードに応じてピック条件を変える
                    if ("ISO".equals(mode)) {
                        if (val >= isoThreshold)
                            return new com.oculus.jj.model.ROI.Point3D(ix, iy, iz);
                    } else if ("VR".equals(mode)) {
                        float wl = windowLevel, ww = windowWidth;
                        float low = wl - ww / 2.0f;
                        float factor = 255.0f / (ww < 1 ? 1 : ww);
                        if (getOpacity(val, low, factor) > 0.3)
                            return new com.oculus.jj.model.ROI.Point3D(ix, iy, iz);
                    } else {
                        // MIP等の場合は最大値の場所を特定するのが難しいが、簡易的に一定以上の値でヒットとする
                        if (val > (windowLevel - windowWidth / 4.0))
                            return new com.oculus.jj.model.ROI.Point3D(ix, iy, iz);
                    }
                }
            }
            return null;
        }

        private void drawMeasurements(Graphics2D g) {
            if (roiList.isEmpty())
                return;

            int w = getWidth();
            int h = getHeight();
            double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
            double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);
            double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;
            double baseScale = Math.min(w / (double) dimX, h / (double) dimY) * 0.8;
            double currentScale = baseScale * zoom;

            g.setColor(com.oculus.jj.config.AppPreferences.getROIColor());
            g.setStroke(new BasicStroke(2));

            for (com.oculus.jj.model.ROI roi : roiList) {
                List<com.oculus.jj.model.ROI.Point3D> points = roi.getPoints3D();
                if (points.isEmpty())
                    continue;

                Point prevP = null;
                com.oculus.jj.model.ROI.Point3D prevV = null;

                for (com.oculus.jj.model.ROI.Point3D vpt : points) {
                    // ボクセル座標 -> 3D回転座標 -> 2D表示座標
                    double tx = vpt.x - cx;
                    double ty = vpt.y - cy;
                    double tz = vpt.z - cz;

                    double ry_view = ty * cosX + tz * sinX;
                    double tz0 = tz * cosX - ty * sinX;
                    double rx_view = tx * cosY - tz0 * sinY;

                    int sx = (int) Math.round(rx_view * currentScale + w / 2.0);
                    int sy = (int) Math.round(ry_view * currentScale + h / 2.0);

                    if (roi.getType() == DCMView.ToolMode.t2DPoint) {
                        g.drawOval(sx - 5, sy - 5, 10, 10);
                        g.fillOval(sx - 2, sy - 2, 4, 4);
                    } else {
                        g.fillOval(sx - 3, sy - 3, 6, 6);
                    }

                    if (prevP != null && roi.getType() == DCMView.ToolMode.tMesure) {
                        g.drawLine(prevP.x, prevP.y, sx, sy);

                        // 距離計算 (mm)
                        double dist = com.oculus.jj.model.Measurement3D.calculateDistance(
                                prevV.x, prevV.y, prevV.z,
                                vpt.x, vpt.y, vpt.z,
                                pixList.get(0).getPixelSpacingX(),
                                pixList.get(0).getPixelSpacingY(),
                                spacingZ);

                        String unit = com.oculus.jj.config.AppPreferences.getMeasureUnit();
                        if ("cm".equals(unit))
                            dist /= 10.0;

                        String label = String.format("%.2f %s", dist, unit);
                        g.drawString(label, (prevP.x + sx) / 2 + 5, (prevP.y + sy) / 2 - 5);
                    }

                    prevP = new Point(sx, sy);
                    prevV = vpt;
                }
            }
        }

        private float getOpacity(int val, float low, float factor) {
            float alpha = (val - low) * factor / 255.0f;
            if (alpha < 0)
                alpha = 0;
            if (alpha > 1.0f)
                alpha = 1.0f;
            // 非線形な不透明度 (Horosのトランスファーファンクション風)
            return (float) Math.pow(alpha, 2.0);
        }

        private double[] getGradient(int x, int y, int z) {
            double dx = (getVal(x + 1, y, z) - getVal(x - 1, y, z)) / 2.0;
            double dy = (getVal(x, y + 1, z) - getVal(x, y - 1, z)) / 2.0;
            double dz = (getVal(x, y, z + 1) - getVal(x, y, z - 1)) / 2.0;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-4)
                return new double[] { 0, 0, 0 };
            return new double[] { dx / len, dy / len, dz / len };
        }

        private int getVal(int x, int y, int z) {
            if (x < 0 || x >= dimX || y < 0 || y >= dimY || z < 0 || z >= dimZ)
                return -3024;
            return volume[z][y][x];
        }

        /**
         * 三線形補間 (Trilinear Interpolation) でボクセル値を取得
         * 画質を滑らかにするためのコアロジック
         */
        private double getValTrilinear(double x, double y, double z) {
            int x0 = (int) Math.floor(x);
            int y0 = (int) Math.floor(y);
            int z0 = (int) Math.floor(z);
            int x1 = x0 + 1;
            int y1 = y0 + 1;
            int z1 = z0 + 1;

            if (x0 < 0 || x1 >= dimX || y0 < 0 || y1 >= dimY || z0 < 0 || z1 >= dimZ) {
                return getVal((int) x, (int) y, (int) z);
            }

            double dx = x - x0;
            double dy = y - y0;
            double dz = z - z0;

            double v000 = volume[z0][y0][x0];
            double v100 = volume[z0][y0][x1];
            double v010 = volume[z0][y1][x0];
            double v110 = volume[z0][y1][x1];
            double v001 = volume[z1][y0][x0];
            double v101 = volume[z1][y0][x1];
            double v011 = volume[z1][y1][x0];
            double v111 = volume[z1][y1][x1];

            double v00 = v000 * (1 - dx) + v100 * dx;
            double v10 = v010 * (1 - dx) + v110 * dx;
            double v01 = v001 * (1 - dx) + v101 * dx;
            double v11 = v011 * (1 - dx) + v111 * dx;

            double v0 = v00 * (1 - dy) + v10 * dy;
            double v1 = v01 * (1 - dy) + v11 * dy;

            return v0 * (1 - dz) + v1 * dz;
        }

        private void render3D(Graphics2D g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0)
                return;

            if (image == null || image.getWidth() != w || image.getHeight() != h) {
                image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            }

            int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
            java.util.Arrays.fill(pixels, 0);

            // 回転行列の事前計算
            double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
            double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);

            double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;
            double baseScale = Math.min(w / (double) dimX, h / (double) dimY) * 0.8;
            double currentScale = baseScale * zoom;

            float wl = windowLevel, ww = windowWidth;
            float low = wl - ww / 2.0f;
            float factor = 255.0f / (ww < 1 ? 1 : ww);

            boolean isVR = "VR".equals(mode);
            boolean isMIP = "MIP".equals(mode);
            boolean isMinIP = "MinIP".equals(mode);
            boolean isAvg = "Average".equals(mode);
            boolean isISO = "ISO".equals(mode); // Surface Rendering

            // 画質を大幅に向上させる（ユーザー様のハイスペックCPU向けに最適化）
            // isDragging（回転中）のスキップ幅を3から2（または1）に減らし、粗さを解消
            int rawStep = isDragging ? 1 : 1;
            final int currentStep = Math.max(1, rawStep);
            // レイが進む間隔（rayStep）も細かくし、3Dのディテールをくっきりさせる
            final double rayStep = isDragging ? 1.0 : 0.4;

            IntStream.range(0, (h + currentStep - 1) / currentStep).parallel().forEach(yi -> {
                int sy = yi * currentStep;
                for (int sx = 0; sx < w; sx += currentStep) {
                    double vx = (sx - w / 2.0) / currentScale;
                    double vy = (sy - h / 2.0) / currentScale;

                    float accR = 0, accG = 0, accB = 0, accA = 0;
                    int maxVal = -3024;
                    int minVal = 3024;
                    long sumVal = 0;
                    int countVal = 0;

                    double zLimit = Math.max(dimX, Math.max(dimY, dimZ)) * 1.2;
                    for (double vz = -zLimit; vz < zLimit; vz += rayStep) {
                        // Ray -> Voxel coordinates
                        double tx = vx * cosY + vz * sinY;
                        double tz0 = -vx * sinY + vz * cosY;
                        double ty = vy * cosX - tz0 * sinX;
                        double tz = vy * sinX + tz0 * cosX;

                        if (tx + cx >= 0 && tx + cx < dimX && ty + cy >= 0 && ty + cy < dimY && tz + cz >= 0
                                && tz + cz < dimZ) {
                            // 補間計算用の実数座標
                            double curX = tx + cx;
                            double curY = ty + cy;
                            double curZ = tz + cz;

                            // 陰影計算（getGradient）用の整数座標
                            int ix = (int) curX;
                            int iy = (int) curY;
                            int iz = (int) curZ;

                            // 画質重視：ドラッグ中も常に補間計算を行う
                            int val = (int) getValTrilinear(curX, curY, curZ);
                            if (isISO) {
                                if (val >= isoThreshold) {
                                    float r = (isoThreshold >= 200) ? 1.0f : 0.9f;
                                    float gVal = (isoThreshold >= 200) ? 0.9f : 0.7f;
                                    float b = (isoThreshold >= 200) ? 0.8f : 0.6f;

                                    float shade = 1.0f;
                                    double[] grad = getGradient(ix, iy, iz);
                                    double dot = grad[0] * sinY * cosX + grad[1] * sinX + grad[2] * cosY * cosX;
                                    shade = (float) Math.max(0.2, Math.abs(dot));

                                    accR = r * shade;
                                    accG = gVal * shade;
                                    accB = b * shade;
                                    accA = 1.0f;
                                    break;
                                }
                            } else if (isVR) {
                                float alpha = getOpacity(val, low, factor);
                                if (alpha > 0.01f) {
                                    float weight = alpha * (1.0f - accA);

                                    // 組織別カラーマッピング (Horosプリセットを模倣)
                                    // WindowLevel/Widthによって組織の強調度合いが変わるように調整
                                    float r = 0, g_val = 0, b = 0;
                                    float relVal = val - low; // 0 at low threshold, ww at full opacity

                                    if (val > 400 + (wl - 300)) { // Bone
                                        r = 1.0f;
                                        g_val = 0.9f;
                                        b = 0.8f;
                                    } else if (val > 150 + (wl - 100)) { // Contrast/Muscle
                                        r = 0.9f;
                                        g_val = 0.6f;
                                        b = 0.4f;
                                    } else if (val > 20 + (wl - 40)) { // Soft Tissue
                                        r = 0.7f;
                                        g_val = 0.4f;
                                        b = 0.3f;
                                    } else if (val > -100 + (wl - 0)) { // Fat
                                        r = 0.4f;
                                        g_val = 0.3f;
                                        b = 0.2f;
                                    } else { // Air/Lung
                                        r = 0.1f;
                                        g_val = 0.1f;
                                        b = 0.2f;
                                    }

                                    // Shading (Lambertian)
                                    float shade = 1.0f;
                                    if (shadingEnabled) {
                                        double[] grad = getGradient(ix, iy, iz);
                                        // カメラ方向への法線の向きを計算
                                        double dot = grad[0] * sinY * cosX + grad[1] * sinX + grad[2] * cosY * cosX;
                                        shade = (float) Math.max(0.2, Math.abs(dot));
                                    }

                                    accR += weight * r * shade;
                                    accG += weight * g_val * shade;
                                    accB += weight * b * shade;
                                    accA += weight;
                                    if (accA >= 0.95f)
                                        break;
                                }
                            } else if (isMIP) {
                                if (val > maxVal)
                                    maxVal = val;
                            } else if (isMinIP) {
                                if (val < minVal)
                                    minVal = val;
                            } else if (isAvg) {
                                sumVal += val;
                                countVal++;
                            }
                        }
                    }

                    int rgb = 0;
                    if (isVR || isISO) {
                        rgb = ((int) (Math.min(1f, accR) * 255) << 16) |
                                ((int) (Math.min(1f, accG) * 255) << 8) |
                                ((int) (Math.min(1f, accB) * 255));
                    } else if (isMIP && maxVal > -3024) {
                        int c = (int) ((maxVal - low) * factor);
                        c = Math.max(0, Math.min(255, c));
                        rgb = (c << 16) | (c << 8) | c;
                    } else if (isMinIP && minVal < 3024) {
                        int c = (int) ((minVal - low) * factor);
                        c = Math.max(0, Math.min(255, c));
                        rgb = (c << 16) | (c << 8) | c;
                    } else if (isAvg && countVal > 0) {
                        int avg = (int) (sumVal / countVal);
                        int c = (int) ((avg - low) * factor);
                        c = Math.max(0, Math.min(255, c));
                        rgb = (c << 16) | (c << 8) | c;
                    }

                    if (rgb != 0) {
                        for (int dy = 0; dy < currentStep && (sy + dy) < h; dy++) {
                            int offset = (sy + dy) * w;
                            for (int dx = 0; dx < currentStep && (sx + dx) < w; dx++) {
                                pixels[offset + (sx + dx)] = rgb;
                            }
                        }
                    }
                }
            });
            g.drawImage(image, 0, 0, null);

            g.setColor(Color.WHITE);
            g.drawString(String.format("Mode: %s | Tool: %s", mode, currentTool), 20, 30);
            g.drawString(String.format("WL: %.0f WW: %.0f", windowLevel, windowWidth), 20, 50);
            g.drawString(String.format("Zoom: %.2f", zoom), 20, 70);
            g.drawString("Rotate: Left Drag | VR Adjust: Tool/Middle Drag | Zoom: Right Drag/Wheel", 20, h - 20);
        }
    }

    public static void openMIP(ViewerController viewer) {
        SwingUtilities.invokeLater(() -> {
            Viewer3DController v3d = new Viewer3DController(viewer, "MIP");
            v3d.setVisible(true);
        });
    }

    public static void openVR(ViewerController viewer) {
        SwingUtilities.invokeLater(() -> {
            Viewer3DController v3d = new Viewer3DController(viewer, "VR");
            v3d.setVisible(true);
        });
    }

    public static void openISO(ViewerController viewer) {
        // HOROS-20240407準拠: Surface Renderingの開始前に設定ダイアログを表示
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.add(new JLabel("Surface 1 Threshold (HU):"));
        JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(300, -1024, 3072, 50));
        panel.add(thresholdSpinner);

        panel.add(new JLabel("Resolution (0.1-1.0):"));
        JSpinner resSpinner = new JSpinner(new SpinnerNumberModel(0.5, 0.1, 1.0, 0.1));
        panel.add(resSpinner);

        panel.add(new JLabel("Smoothing:"));
        JCheckBox smoothCheck = new JCheckBox("", true);
        panel.add(smoothCheck);

        int result = JOptionPane.showConfirmDialog(null, panel, "3D Surface Rendering Settings",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            SwingUtilities.invokeLater(() -> {
                Viewer3DController v3d = new Viewer3DController(viewer, "ISO");
                v3d.isoThreshold = (int) thresholdSpinner.getValue();
                v3d.resolution = (double) resSpinner.getValue();
                v3d.setVisible(true);
            });
        }
    }
}
