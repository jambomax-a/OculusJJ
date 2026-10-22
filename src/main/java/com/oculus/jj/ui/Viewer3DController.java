package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.ArrayList;
import com.oculus.jj.model.DicomPix;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;
import com.oculus.jj.utils.LanguageManager;

/**
 * Viewer3DController - 3Dビューワー (MIP/VR)
 * HOROS-20240407準拠: VRController.h, VRController.m 相当
 */
public class Viewer3DController extends JFrame {

    private ViewerController viewer2D;
    private List<DicomPix> pixList;
    private short[] volume; // [z * dimY * dimX + y * dimX + x] 1D統合配列
    private short[] volumeBackup; // 骨消去前のオリジナルデータバックアップ
    private boolean isBoneRemoved = false;
    private int dimX, dimY, dimZ;
    private double spacingX = 1.0; // mm/pixel (X)
    private double spacingY = 1.0; // mm/pixel (Y)
    private double spacingZ = 1.0; // mm/slice (Z)

    private JPanel mainContainer;
    private JToolBar vrToolbar;
    private GLViewer3D glViewer;
    private JLabel infoLabel;
    private CPURaycastRenderer cpuRenderer; // Phase 4: CPU fallback
    private String mode = "MIP"; // デフォルトをMIPにして可視性を優先

    // Window Level/Width (HOROS-20240407準拠: 3D用の初期表示は1:1マッピング)
    private float windowLevel = 1024;
    private float windowWidth = 4096;

    // ISO Threshold
    private int isoThreshold = 300;

    // Transfer Function (Phase 3)
    private TransferFunction currentTF = TransferFunction.createVRMusclesBones();

    // 視点回転パラメータ
    // rotateX=-π/2: 上方(+Y)からボリュームを俯瞰（CT体軸断面投影）
    // rotateY=0: 正面向き（-π/2 は画面の5%しか使わない縮退ビューになるため0に変更）
    private double rotateX = -Math.PI / 2;
    private double rotateY = Math.PI / 2;
    private double zoom = 1.0;

    // Clipping
    private float clipMinX = 0, clipMaxX = 1;
    private float clipMinY = 0, clipMaxY = 1;
    private float clipMinZ = 0, clipMaxZ = 1;

    private enum Tool3D {
        WLWW, PAN, ZOOM, ROTATE, CLIPPING, SCISSOR, MEASURE, POINT, ROI, SCISSOR_IN, SCISSOR_OUT, BONE_REMOVAL
    }

    private Tool3D currentTool = Tool3D.ROTATE;
    private List<com.oculus.jj.model.ROI> roiList = new ArrayList<>();
    private com.oculus.jj.model.ROI activeRoi = null;
    private com.oculus.jj.model.ROI draggedRoi = null;
    private int draggedNodeIndex = -1;
    private final List<JToggleButton> toolButtons = new ArrayList<>();
    private final List<Point> scissorPoints = new ArrayList<>();

    private JDialog clippingDialog;
    private boolean showCropBox = false;
    private boolean vrStereoActive = false;
    private JLabel loadingLabel;
    private String loadingStatus = "Preparing Volume Data (Converting to HU)...";
    private boolean gpuInitialized = false;
    private boolean gpuContextUnsupported = false;
    private int glConfigIndex = 0;
    private volatile boolean gpuRecoveryInProgress = false;
    private volatile boolean gpuActivationInProgress = false;
    private int gpuActivationToken = 0;
    private Point lastMousePos;
    private KeyEventDispatcher keyboardShortcutDispatcher;

    private void selectTool(Tool3D tool) {
        this.currentTool = tool;
        for (JToggleButton btn : toolButtons) {
            if (btn.getClientProperty("tool") == tool) {
                btn.setSelected(true);
                break;
            }
        }
        if (tool == Tool3D.CLIPPING) {
            showClippingDialog();
        }
        repaint();
    }

    private final MouseAdapter glMouseHandler = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            lastMousePos = e.getPoint();
            requestFocusInWindow();

            Tool3D tool = getEffectiveTool(e);

            // 1. Check if we hit any existing endpoint first
            if (tool != Tool3D.SCISSOR && tool != Tool3D.BONE_REMOVAL) {
                HitResult hit = findHitNode(e.getX(), e.getY());
                if (hit != null) {
                    draggedRoi = hit.roi;
                    draggedNodeIndex = hit.nodeIndex;
                    activeRoi = hit.roi;
                    repaint();
                    return;
                }
            }

            // 2. If no hit, check tool action
            if (tool == Tool3D.SCISSOR) {
                scissorPoints.clear();
                scissorPoints.add(e.getPoint());
                repaint();
                return;
            }

            if (tool == Tool3D.MEASURE) {
                com.oculus.jj.model.ROI.Point3D pt = getVoxelUnderMouse(e.getX(), e.getY());
                if (pt == null) {
                    pt = getVoxelAtDepth(e.getX(), e.getY(), 0.0);
                }
                if (pt != null) {
                    activeRoi = new com.oculus.jj.model.ROI(DCMView.ToolMode.tMesure, 1, 1, new Point2D.Float(0, 0));
                    activeRoi.setName("3D Length " + (roiList.size() + 1));
                    activeRoi.addPoint3D(pt);
                    activeRoi.addPoint3D(pt); // Add
                                              // second
                                              // point
                                              // initially
                                              // at
                                              // same
                                              // position
                    roiList.add(activeRoi);
                    draggedRoi = activeRoi;
                    draggedNodeIndex = 1; // Drag the second point
                    repaint();
                }
                return;
            }

            if (tool == Tool3D.POINT) {
                com.oculus.jj.model.ROI.Point3D pt = getVoxelUnderMouse(e.getX(), e.getY());
                if (pt == null) {
                    pt = getVoxelAtDepth(e.getX(), e.getY(), 0.0);
                }
                if (pt != null) {
                    com.oculus.jj.model.ROI pRoi = new com.oculus.jj.model.ROI(DCMView.ToolMode.t2DPoint, 1, 1,
                            new Point2D.Float(0, 0));
                    pRoi.setName("3D Point " + (roiList.size() + 1));
                    pRoi.addPoint3D(pt);
                    roiList.add(pRoi);
                    activeRoi = pRoi;
                    draggedRoi = pRoi;
                    draggedNodeIndex = 0; // Drag
                                          // the
                                          // point
                    repaint();
                }
                return;
            }

            if (tool == Tool3D.ROI) {
                com.oculus.jj.model.ROI.Point3D pt = getVoxelUnderMouse(e.getX(), e.getY());
                if (pt == null) {
                    pt = getVoxelAtDepth(e.getX(), e.getY(), 0.0);
                }
                if (pt != null) {
                    activeRoi = new com.oculus.jj.model.ROI(DCMView.ToolMode.tOval, 1, 1, new Point2D.Float(0, 0));
                    activeRoi.setName("3D Oval " + (roiList.size() + 1));
                    activeRoi.addPoint3D(pt);
                    activeRoi.addPoint3D(pt); // Add
                                              // second
                                              // point
                                              // initially
                                              // at
                                              // same
                                              // position
                    roiList.add(activeRoi);
                    draggedRoi = activeRoi;
                    draggedNodeIndex = 1; // Drag the second point
                    repaint();
                }
                return;
            }

            if (tool == Tool3D.BONE_REMOVAL) {
                com.oculus.jj.model.ROI.Point3D pt = getVoxelUnderMouse(e.getX(), e.getY());
                if (pt != null) {
                    removeBoneAtVoxel(pt);
                }
                return;
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (currentTool == Tool3D.SCISSOR) {
                if (scissorPoints.size() < 3) {
                    scissorPoints.clear();
                    repaint();
                } else if (glViewer != null) {
                    // ラッソ描画完了後、キーイベントを確実に受信できるようフォーカスをセット
                    glViewer.requestFocusInWindow();
                }
                return;
            }

            if (draggedRoi != null) {
                // If it's a newly created measurement or oval and they just clicked without
                // dragging, discard it
                if (draggedRoi == activeRoi && (activeRoi.getType() == DCMView.ToolMode.tMesure
                        || activeRoi.getType() == DCMView.ToolMode.tOval)) {
                    List<com.oculus.jj.model.ROI.Point3D> points = activeRoi.getPoints3D();
                    if (points.size() == 2) {
                        com.oculus.jj.model.ROI.Point3D p1 = points.get(0);
                        com.oculus.jj.model.ROI.Point3D p2 = points.get(1);
                        double sx = (pixList != null && !pixList.isEmpty()) ? pixList.get(0).getPixelSpacingX()
                                : spacingX;
                        double sy = (pixList != null && !pixList.isEmpty()) ? pixList.get(0).getPixelSpacingY()
                                : spacingY;
                        double dist = com.oculus.jj.model.Measurement3D.calculateDistance(p1.x, p1.y, p1.z, p2.x, p2.y,
                                p2.z, sx, sy, spacingZ);
                        // If distance/diagonal is less than 0.5 mm, discard
                        if (dist < 0.5) {
                            roiList.remove(activeRoi);
                            activeRoi = null;
                        }
                    }
                }
                draggedRoi = null;
                draggedNodeIndex = -1;
            }
            repaint();
        }

        private Tool3D getEffectiveTool(MouseEvent e) {
            boolean isRight = SwingUtilities.isRightMouseButton(e);
            boolean isMiddle = SwingUtilities.isMiddleMouseButton(e)
                    || (e.getModifiersEx() & MouseEvent.BUTTON2_DOWN_MASK) != 0;
            boolean isShift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;
            boolean isCtrl = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;

            // 1. Modifier key combinations (highest priority, matches standard Horos DICOM
            // operations)
            if (isMiddle) {
                return Tool3D.PAN; // Middle drag -> Pan
            }
            if (isRight) {
                return Tool3D.WLWW; // Right drag -> WLWW (Adjust window level/width)
            }
            if (isShift || isCtrl) {
                return Tool3D.ZOOM; // Shift/Ctrl + Left drag -> Zoom
            }

            // 2. Local 3D tool (always prioritized for left click when no modifier is
            // pressed)
            return currentTool;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (lastMousePos == null) {
                return;
            }

            int dx = e.getX() - lastMousePos.x;
            int dy = e.getY() - lastMousePos.y;

            if (draggedRoi != null && draggedNodeIndex != -1) {
                com.oculus.jj.model.ROI.Point3D pt = getVoxelUnderMouse(e.getX(), e.getY());
                if (pt == null) {
                    double depth = getRoiStartDepth(draggedRoi);
                    pt = getVoxelAtDepth(e.getX(), e.getY(), depth);
                }
                if (pt != null) {
                    draggedRoi.updatePoint3D(draggedNodeIndex, pt);
                }
                lastMousePos = e.getPoint();
                updateInfoLabel(e.getX(), e.getY());
                repaint();
                return;
            }

            Tool3D tool = getEffectiveTool(e);

            if (tool == Tool3D.SCISSOR) {
                Point p = e.getPoint();
                if (!scissorPoints.isEmpty()) {
                    Point last = scissorPoints.get(scissorPoints.size() - 1);
                    if (p.distance(last) > 3) {
                        scissorPoints.add(p);
                    }
                } else {
                    scissorPoints.add(p);
                }
                lastMousePos = e.getPoint();
                updateInfoLabel(e.getX(), e.getY());
                repaint();
                return;
            }

            if (tool == Tool3D.WLWW) {
                updateWindow(dy * 2.0f, dx * 2.0f);
            } else if (tool == Tool3D.ZOOM) {
                zoom *= (1.0 - dy / 200.0);
                if (zoom < 0.1)
                    zoom = 0.1;
                if (glViewer != null) {
                    glViewer.setZoom((float) zoom);
                }
            } else if (tool == Tool3D.PAN) {
                // Pure view-space flat translation (world X & Y since translation is left of
                // rotation)
                float sens = 0.002f / (float) zoom;
                float dOffsetX = dx * sens;
                float dOffsetY = -dy * sens;
                float dOffsetZ = 0.0f;

                if (glViewer != null) {
                    glViewer.addOffset(dOffsetX, dOffsetY, dOffsetZ);
                }
            } else if (tool == Tool3D.ROTATE) {
                rotateY += dx * 0.01;
                rotateX += dy * 0.01;
                if (glViewer != null) {
                    glViewer.setRotation((float) rotateX, (float) rotateY);
                }
            }

            lastMousePos = e.getPoint();
            updateInfoLabel(e.getX(), e.getY());
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            updateInfoLabel(e.getX(), e.getY());
        }
    };

    private final MouseWheelListener glMouseWheelHandler = e -> {
        zoom *= (1.0 - e.getWheelRotation() * 0.1);
        if (zoom < 0.1)
            zoom = 0.1;
        if (glViewer != null) {
            glViewer.setZoom((float) zoom);
        }
        updateInfoLabel(-1, -1);
        repaint();
    };

    public void updateWindow(float dLevel, float dWidth) {
        this.windowLevel += dLevel;
        this.windowWidth += dWidth;
        if (this.windowWidth < 1)
            this.windowWidth = 1;
        if (glViewer != null)
            glViewer.setWindowLevel(windowLevel, windowWidth);
        repaint();
    }

    public Viewer3DController(ViewerController viewer2D, String mode) {
        super("3D Viewer - " + mode);
        this.viewer2D = viewer2D;
        setMode(mode);

        // In 3D Viewer, the default active tool is 3D Rotate (so Left Drag rotates).
        DCMView.currentTool = DCMView.ToolMode.t3DRotate;

        // initializeData(); // Don't call here, we will do it asynchronously
        initializeUI();

        // ウィンドウクローズ時にメモリを解放 (HOROS-20240407準拠: リソース管理を徹底)
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });

        // Asynchronously prepare data and launch GPU renderer
        startAsyncGPUInitialization();
    }

    private void cleanup() {
        // System.out.println("Cleaning up 3D Viewer resources...");
        stopRenderLoop();
        if (keyboardShortcutDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyboardShortcutDispatcher);
            keyboardShortcutDispatcher = null;
        }
        volume = null;
        pixList = null;
        if (glViewer != null) {
            glViewer.setVolumeData(null, null, 0, 0, 0);
            glViewer = null;
        }
        if (cpuRenderer != null) {
            cpuRenderer.setVolumeData(null, 0, 0, 0);
            cpuRenderer = null;
        }
        System.gc();
    }

    private void activateGPUMode() {
        if (gpuContextUnsupported) {
            // System.err.println("DEBUG: GPU mode unavailable for all GL configs. Staying
            // in CPU mode.");
            if (glViewer != null) {
                glViewer.setEngine(GLViewer3D.ENGINE_CPU);
                glViewer.requestCpuRender();
            }
            showView("VIEW");
            return;
        }

        final int activationToken = ++gpuActivationToken;
        gpuActivationInProgress = true;
        startGpuActivationWatchdog(activationToken);

        if (!gpuInitialized) {
            gpuInitialized = true;
            new Thread(() -> {
                try {
                    SwingUtilities.invokeLater(() -> {
                        // glViewer を先に隠してから loadingStatus をセット。
                        // 逆順だと可視状態の glViewer.repaint() が paintOverlay 経由で
                        // GDI レイヤーにテキストを書き込み DWM 残留の原因になる。
                        showView("LOADING");
                        setLoadingStatus("Computing GPU Gradients (Safety Mode)...");
                    });

                    int zStep = (dimZ > 512) ? 2 : 1;
                    int tz = dimZ / zStep;
                    byte[] grad = computeGradients(zStep, tz);
                    // System.out.println("DEBUG: Gradients computed (" + grad.length + " bytes).");

                    SwingUtilities.invokeLater(() -> loadingLabel
                            .setText("Uploading Volume to GPU (Step 1/2)... Please wait 2-3 seconds..."));
                    // System.out.println("DEBUG: Starting glViewer.setVolumeData...");
                    glViewer.setVolumeData(volume, grad, dimX, dimY, dimZ);
                    // System.out.println("DEBUG: glViewer.setVolumeData finished.");

                    // P5 VRAM最適化 (HOROS準拠): VRAMサイズ検出 + 低VRAM/超過時の1回警告
                    // 参照: HOROS VTKView.mm:182 / DefaultsOsiriX.m:206 (検出), VRView.mm:1020,6310 (警告)
                    try {
                        long volBytes = (volume == null) ? 0L : (long) volume.length * 2L;
                        if (grad != null) {
                            volBytes += grad.length;
                        }
                        com.oculus.jj.util.GpuVramInfo.warnIfNecessary(
                                com.oculus.jj.util.GpuVramInfo.detect(), volBytes, this);
                    } catch (Throwable ignored) {
                    }

                    Thread.sleep(400); // EDTに "Uploading Volume..." 表示を描画させる時間を確保

                    SwingUtilities
                            .invokeLater(() -> setLoadingStatus("Completing GPU Initialization (Step 2/2)..."));
                    glViewer.setTransferFunction(currentTF);
                    glViewer.setWindowLevel(windowLevel, windowWidth);
                    glViewer.setClipping(clipMinX, clipMaxX, clipMinY, clipMaxY, clipMinZ, clipMaxZ);
                    glViewer.setVoxelSpacing((float) spacingX, (float) spacingY, (float) spacingZ);
                    // System.out.println("DEBUG activateGPU EDT: spacing=(" + spacingX + "," +
                    // spacingY + "," + spacingZ + ") dim=(" + dimX + "," + dimY + "," + dimZ +
                    // ")");
                    glViewer.setRotation((float) rotateX, (float) rotateY);
                    glViewer.setZoom((float) zoom);
                    // System.out.println("DEBUG: GPU parameters set.");

                    Thread.sleep(400); // EDTに "Completing GPU..." 表示を描画させる時間を確保

                    SwingUtilities.invokeLater(() -> {
                        try {
                            glViewer.setEngine(GLViewer3D.ENGINE_GPU);
                            this.canvas = glViewer; // 描画ループ対象をセット
                            setMode(this.mode);
                            // System.out.println("DEBUG: activateGPUMode EDT: about to showView(VIEW)");
                            showView("VIEW");
                            startRenderLoop();
                            // System.out.println("3D Rendering Ready. Mode: GPU");
                        } catch (Throwable t) {
                            // System.err.println("DEBUG: EXCEPTION in activateGPUMode EDT block:");
                            t.printStackTrace();
                            showView("VIEW");
                            startRenderLoop();
                        } finally {
                            gpuActivationInProgress = false;
                        }
                    });
                } catch (Throwable t) {
                    // System.err.println("DEBUG: Error in activateGPUMode thread:");
                    t.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        gpuActivationInProgress = false;
                        setLoadingStatus("GPU Error: " + t.getMessage());
                    });
                }
            }).start();
        } else {
            glViewer.setRotation((float) rotateX, (float) rotateY);
            glViewer.setZoom((float) zoom);
            setMode(this.mode);
            glViewer.setEngine(GLViewer3D.ENGINE_GPU);
            this.canvas = glViewer;
            showView("VIEW");
            startRenderLoop();
            gpuActivationInProgress = false;
        }
    }

    private void startGpuActivationWatchdog(int token) {
        Timer watchdog = new Timer(12000, e -> {
            if (token != gpuActivationToken || !gpuActivationInProgress) {
                ((Timer) e.getSource()).stop();
                return;
            }
            // System.err.println("DEBUG: GPU activation timeout at step 2/2. Triggering GL
            // fallback retry.");
            gpuActivationInProgress = false;
            ((Timer) e.getSource()).stop();
            SwingUtilities.invokeLater(this::handleGpuContextFailure);
        });
        watchdog.setRepeats(false);
        watchdog.start();
    }

    private void startAsyncGPUInitialization() {
        setLoadingStatus("Preparing Volume Data (Converting to HU)...");
        showView("LOADING");

        new Thread(() -> {
            try {
                // System.out.println("DEBUG: startAsyncGPUInitialization thread started.");
                List<List<DicomPix>> allPix = viewer2D.getPixList();
                if (allPix == null || allPix.isEmpty()) {
                    // System.err.println("DEBUG: allPix is NULL or empty!");
                    return;
                }
                this.pixList = new ArrayList<>(allPix.get(0));
                // System.out.println("DEBUG: pixList size = " + pixList.size());
                if (pixList == null || pixList.isEmpty())
                    return;

                DicomPix first = pixList.get(0);
                dimX = first.getWidth();
                dimY = first.getHeight();
                dimZ = pixList.size();
                spacingZ = (first.getSliceThickness() <= 0.1) ? 1.0 : first.getSliceThickness();

                // ---- Fix 1: DICOMスライスを空間順にソート (HOROS-20240407準拠) ----
                // 降順ソート: SliceLocation大(=頭側)が先頭→画面上に表示される
                pixList.sort((a, b) -> Double.compare(b.getSliceLocation(), a.getSliceLocation()));

                // ---- Fix 2: ボクセルの物理サイズを取得 (アスペクト比正規化) ----
                // PixelSpacing: DICOM Row/Column spacing [mm]
                // SliceThickness/SpacingBetweenSlices: Z方向[mm]
                spacingX = (first.getPixelSpacingX() > 0) ? first.getPixelSpacingX() : 1.0;
                spacingY = (first.getPixelSpacingY() > 0) ? first.getPixelSpacingY() : 1.0;
                // Z spacing = SliceThickness (またはImagePositionPatientのZ差分で補完)
                if (pixList.size() > 1) {
                    double z0 = pixList.get(0).getSliceLocation();
                    double z1 = pixList.get(1).getSliceLocation();
                    double dzAbs = Math.abs(z1 - z0);
                    spacingZ = (dzAbs > 0.01) ? dzAbs
                            : ((first.getSliceThickness() > 0.1) ? first.getSliceThickness() : 1.0);
                } else {
                    spacingZ = (first.getSliceThickness() > 0.1) ? first.getSliceThickness() : 1.0;
                }

                volume = new short[dimZ * dimY * dimX];
                // System.out.println("DEBUG: loadSlices started. dimZ=" + dimZ + ", dimY=" +
                // dimY + ", dimX=" + dimX);
                // System.out.println("Starting HU conversion (Sequential/Safe Mode) for " +
                // dimZ + " slices...");

                for (int z = 0; z < dimZ; z++) {
                    final int currentZ = z;
                    // 1枚ごとに進捗表示 (フリーズ確認用)
                    SwingUtilities.invokeLater(() -> {
                        setLoadingStatus(String.format("Converting to HU... %d%% (%d / %d)",
                                (currentZ * 100 / dimZ), currentZ, dimZ));
                    });

                    try {
                        DicomPix pix = pixList.get(z);
                        if (pix != null) {
                            short[] src = pix.getShortData();
                            if (src != null) {
                                float s = pix.getRescaleSlope();
                                float i = pix.getRescaleIntercept();
                                int w = pix.getWidth();
                                int h = pix.getHeight();
                                if (w == dimX && h == dimY) {
                                    int zOffset = z * dimY * dimX;
                                    for (int y = 0; y < dimY; y++) {
                                        int yOffset = y * dimX;
                                        for (int x = 0; x < dimX; x++) {
                                            volume[zOffset + yOffset + x] = (short) (src[yOffset + x] * s + i);
                                        }
                                    }
                                }
                            }
                        }
                        // pixList.set(z, null); // HOROS-20240407互換のため、2D連動用に参照を保持する

                        // 50枚ごとにコンソールに進捗を出す
                        if (z % 50 == 0) {
                            // System.out.println("DEBUG: HU Converting... " + z + "/" + dimZ);
                        }
                        // 100枚ごとにGCを強制
                        if (z % 100 == 0) {
                            System.gc();
                        }
                    } catch (Throwable t) {
                        // System.err.println("Error at slice " + z + ": " + t.getMessage());
                    }
                }

                // System.out.println("HU conversion finished. Finalizing...");
                // データの詳細統計チェック（デバッグログ出力のため）
                /*
                 * long nonZeroCount = 0;
                 * int maxV = -3000, minV = 3000;
                 * long sumV = 0;
                 * for (int i = 0; i < Math.min(volume.length, 1000000); i++) {
                 * short val = volume[i];
                 * if (val != 0)
                 * nonZeroCount++;
                 * if (val > maxV)
                 * maxV = val;
                 * if (val < minV)
                 * minV = val;
                 * sumV += val;
                 * }
                 * // System.out.println("DEBUG: Volume Stats (first 1M samples):");
                 * // System.out.println("  Non-zero: " + nonZeroCount);
                 * // System.out.println("  Max: " + maxV + ", Min: " + minV + ", Avg: " + (sumV
                 * / 1000000));
                 */
                pixList = null;

                // 3Dレンダリング用ウィンドウは広範囲を維持する。
                // DICOMの2D表示用窓(例: 軟部組織 WL=50/WW=300)は骨(HU>200)を完全除外するため
                // VRモードで真っ黒になる。wl=300/ww=2000を使用して骨・軟組織を両方表示する。
                // DICOM 2D window info suppressed. 3D uses separate WL/WW.
                // 3D用デフォルト窓: WL=1024 WW=4096 (1:1マッピング)
                windowLevel = 1024;
                windowWidth = 4096;

                SwingUtilities.invokeLater(() -> {
                    setLoadingStatus("Initializing Renderers...");
                    setupGPURenderer();
                });
            } catch (Throwable t) {
                t.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    setLoadingStatus("Error: " + t.getMessage());
                });
            }
        }).start();
    }

    private AWTGLCanvas canvas;
    private boolean isRendering = false;
    private Timer renderTimer;

    private void startRenderLoop() {
        stopRenderLoop();
        isRendering = true;
        final int[] consecutiveErrors = { 0 };
        renderTimer = new Timer(16, e -> {
            if (!isRendering || canvas == null) {
                stopRenderLoop();
                return;
            }
            try {
                if (canvas.isDisplayable() && canvas.getWidth() > 0 && canvas.getHeight() > 0)
                    canvas.repaint();
                consecutiveErrors[0] = 0; // 成功したらリセット
            } catch (Throwable ex) {
                if (isPixelFormatError(ex)) {
                    // System.err.println("DEBUG: OpenGL pixel format error detected. Trying next GL
                    // config.");
                    ex.printStackTrace();
                    gpuActivationInProgress = false;
                    SwingUtilities.invokeLater(this::handleGpuContextFailure);
                    return;
                }
                consecutiveErrors[0]++;
                // System.err.println("DEBUG: Render error #" + consecutiveErrors[0] + ": " +
                // ex);
                ex.printStackTrace();
                if (consecutiveErrors[0] >= 10) {
                    // 連続10回失敗した場合のみ停止 (一時的なエラーで永久停止しないよう)
                    stopRenderLoop();
                    // System.err.println("DEBUG: Render loop stopped after 10 consecutive
                    // errors.");
                }
            }
        });
        renderTimer.setRepeats(true);
        // 最初のタイマー実行を 200ms 遅延させて、EDTがカード表示切替と
        // OSウィンドウ側のHWNDピア可視化を完全に終える時間を十分に確保する（EDT飢餓状態の防止）
        renderTimer.setInitialDelay(200);
        renderTimer.start();
    }

    private boolean isPixelFormatError(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains("Failed to set pixel format")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void stopRenderLoop() {
        isRendering = false;
        if (renderTimer != null) {
            renderTimer.stop();
            renderTimer = null;
        }
    }

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
        // System.out.println("DEBUG: setupGPURenderer called. engine=" + (isGpuEngine()
        // ? "GPU" : "CPU"));
        new Thread(() -> {
            try {
                // System.out.println("DEBUG: setupGPURenderer thread started.");
                // 先にCPURendererを完全に作ってからEDTに渡すことで排他やNPEを回避
                CPURaycastRenderer tempCpu = new CPURaycastRenderer();
                tempCpu.setVolumeData(volume, dimX, dimY, dimZ);
                tempCpu.setVoxelSpacing((float) spacingX, (float) spacingY, (float) spacingZ);
                tempCpu.setTransferFunction(currentTF);
                tempCpu.setClipping(clipMinX, clipMaxX, clipMinY, clipMaxY, clipMinZ, clipMaxZ);
                tempCpu.setRotation((float) rotateX, (float) rotateY);
                tempCpu.setZoom((float) zoom);

                // バックグラウンドスレッドにてCPU用勾配(Gradient)を事前計算
                tempCpu.computeGradients();

                SwingUtilities.invokeLater(() -> {
                    try {
                        // GLViewer3D は initializeUI() 時点(HU変換前)に volume=null で生成されているため、
                        // setCpuRenderer() の内部で renderer.setVolumeData(null,0,0,0) が呼ばれて
                        // tempCpu のデータが消えてしまう。先に volume を設定して防ぐ。
                        glViewer.setVolumeData(volume, null, dimX, dimY, dimZ);
                        glViewer.setVoxelSpacing((float) spacingX, (float) spacingY, (float) spacingZ);
                        cpuRenderer = tempCpu;
                        glViewer.setCpuRenderer(cpuRenderer);
                        glViewer.setWindowLevel(windowLevel, windowWidth);
                        setMode(this.mode);
                        glViewer.setEngine(GLViewer3D.ENGINE_CPU);
                        this.canvas = glViewer; // 描画ループの対象セット
                        // System.out.println("DEBUG: setupGPURenderer EDT: about to showView(VIEW)");
                        showView("VIEW");
                        startRenderLoop(); // 定期再描画を回す
                        // System.out.println("3D Rendering Ready. Mode: CPU");
                    } catch (Throwable t) {
                        // System.err.println("DEBUG: EXCEPTION in setupGPURenderer EDT block:");
                        t.printStackTrace();
                        showView("VIEW"); // 例外でも VIEW に切り替えてブラックスクリーンを表示
                        startRenderLoop();
                    }
                });

                // System.out.println("DEBUG: CPU renderer initialized.");
            } catch (Throwable t) {
                // System.err.println("DEBUG: Error in setupGPURenderer thread:");
                t.printStackTrace();
                SwingUtilities.invokeLater(() -> setLoadingStatus("Initialization Error: " + t.getMessage()));
            }
        }).start();
    }

    private void showView(String viewName) {
        if (mainContainer != null && mainContainer.getLayout() instanceof CardLayout) {
            ((CardLayout) mainContainer.getLayout()).show(mainContainer, viewName);
        }
        if ("VIEW".equals(viewName)) {
            setLoadingStatus(null);
        }
        if (glViewer != null) {
            glViewer.setVisible(true);
            try {
                mainContainer.setComponentZOrder(glViewer, 0);
            } catch (Throwable t) {
                // ignore Z-order mixing exceptions
            }
            glViewer.requestFocus();
        }
        if (mainContainer != null) {
            mainContainer.revalidate();
            mainContainer.repaint();
        }
        if (glViewer != null) {
            glViewer.revalidate();
            glViewer.repaint();
        }
        // System.out.println("DEBUG: showView(" + viewName + ") done."
        // + " glViewer.isShowing=" + (glViewer != null ? glViewer.isShowing() : "null")
        // + " glViewer.isVisible=" + (glViewer != null ? glViewer.isVisible() :
        // "null"));
    }

    private byte[] computeGradients(int zStep, int tz) {
        byte[] g = new byte[dimX * dimY * tz * 3];
        java.util.stream.IntStream.range(0, tz).parallel().forEach(zi -> {
            int z = zi * zStep;
            int layerOffset = zi * dimY * dimX * 3;
            for (int y = 0; y < dimY; y++) {
                int yOffset = y * dimX * 3;
                for (int x = 0; x < dimX; x++) {
                    int idx = layerOffset + yOffset + x * 3;
                    float dx = (sv(x + 1, y, z) - sv(x - 1, y, z)) * 0.5f;
                    float dy = (sv(x, y + 1, z) - sv(x, y - 1, z)) * 0.5f;
                    float dz = (sv(x, y, z + 1) - sv(x, y, z - 1)) * 0.5f;
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len < 1e-6f)
                        len = 1;
                    g[idx] = (byte) ((dx / len * 0.5f + 0.5f) * 255);
                    g[idx + 1] = (byte) ((dy / len * 0.5f + 0.5f) * 255);
                    g[idx + 2] = (byte) ((dz / len * 0.5f + 0.5f) * 255);
                }
            }
        });
        return g;
    }

    private float sv(int x, int y, int z) {
        if (x < 0)
            x = 0;
        if (x >= dimX)
            x = dimX - 1;
        if (y < 0)
            y = 0;
        if (y >= dimY)
            y = dimY - 1;
        if (z < 0)
            z = 0;
        if (z >= dimZ)
            z = dimZ - 1;
        // 符号付きshort (HU値) をそのままfloatに変換。& 0xFFFF は厳禁:
        // 負のHU (例: 空気=-1024) を unsigned 64512 に化かし、勾配方向が逆転する。
        return (float) (volume[z * dimY * dimX + y * dimX + x]);
    }

    private void initializeUI() {
        // HOROS-20240407準拠: 3D Viewerも大画面または全画面で表示
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopRenderLoop();
            }
        });

        // HOROS-20240407準拠: 3D Viewerも独立したメニューバーを持つ（Windows環境）
        BrowserController browser = BrowserController.currentBrowser();
        if (browser != null && browser.getJMenuBar() != null) {
            JMenuBar replicated = ViewerController.replicateMenuBar(browser.getJMenuBar());
            // 3D固有のメニューを追加
            JMenu modeMenu = new JMenu("3D Mode");
            modeMenu.setForeground(Color.BLACK);

            JMenuItem mipItem = new JMenuItem(LanguageManager.get("v3d.mip", "MIP"));
            mipItem.addActionListener(e -> {
                setMode("MIP");
                repaint();
            });
            modeMenu.add(mipItem);

            JMenuItem vrItem = new JMenuItem(LanguageManager.get("v3d.vr", "Volume Rendering"));
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
        // ---- HOROS-20240407 準拠 ツールバー再構成 ----
        vrToolbar = new JToolBar();
        vrToolbar.setFloatable(false);
        vrToolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));

        // 1. Mouse button function
        JPanel contentMouse = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        JPanel groupMouse = createToolbarGroup(com.oculus.jj.utils.LanguageManager.get("vr.group.mouse"), contentMouse);
        ButtonGroup bgTools = new ButtonGroup();

        // 左クリック時の動作を切り替えるボタンのみに絞る
        String[] toolLabels = { "↻ Rotate", "🟢 Point", "⭕ Oval", "📏 Meas", "✂️ Scissor", "💀 Bone" };
        Tool3D[] toolValues = { Tool3D.ROTATE, Tool3D.POINT, Tool3D.ROI, Tool3D.MEASURE, Tool3D.SCISSOR,
                Tool3D.BONE_REMOVAL };

        for (int i = 0; i < toolLabels.length; i++) {
            final Tool3D t = toolValues[i];
            JToggleButton btn = new JToggleButton(toolLabels[i]);
            btn.putClientProperty("tool", t);
            btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            btn.setMargin(new Insets(2, 4, 2, 4));
            btn.setFocusable(false);
            btn.addActionListener(e -> {
                selectTool(t);
            });
            bgTools.add(btn);
            contentMouse.add(btn);
            toolButtons.add(btn);
            if (t == Tool3D.ROTATE) {
                btn.setSelected(true);
            }
        }

        vrToolbar.add(groupMouse);

        // 2. WL/WW & CLUT & Opacity
        JPanel contentWLWW = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JPanel groupWLWW = createToolbarGroup(com.oculus.jj.utils.LanguageManager.get("vr.group.wlww"), contentWLWW);

        JPanel gridLeft = new JPanel(new GridLayout(3, 2, 2, 2));
        gridLeft.setOpaque(false);

        JLabel lblWL = new JLabel(LanguageManager.get("v3d.wlww", "WL/WW:"), JLabel.RIGHT);
        lblWL.setFont(new Font("SansSerif", Font.PLAIN, 10));
        JComboBox<String> cbWL = new JComboBox<>(new String[] {
                LanguageManager.get("vr.wlww.other"),
                "Default WL & WW",
                "Full dynamic",
                "----------------",
                "1 - CT - Abdomen",
                "2 - CT - Bone",
                "3 - CT - Brain",
                "4 - CT - Pulmonary",
                "5 - VR - Endoscopy",
                "----------------",
                "Add Current WL/WW",
                "Set WL/WW Manually"
        });
        cbWL.setFont(new Font("SansSerif", Font.PLAIN, 10));
        cbWL.setFocusable(false);
        cbWL.setSelectedItem("Other");
        cbWL.addActionListener(e -> {
            String item = (String) cbWL.getSelectedItem();
            if (item == null || item.startsWith("-"))
                return;

            if ("Default WL & WW".equals(item)) {
                windowLevel = 50;
                windowWidth = 350;
            } else if ("Full dynamic".equals(item)) {
                windowLevel = 1024;
                windowWidth = 4096;
            } else if (item.contains("Abdomen")) {
                windowLevel = 60;
                windowWidth = 400;
            } else if (item.contains("Bone")) {
                windowLevel = 300;
                windowWidth = 1500;
            } else if (item.contains("Brain")) {
                windowLevel = 40;
                windowWidth = 80;
            } else if (item.contains("Pulmonary")) {
                windowLevel = -600;
                windowWidth = 1500;
            } else if (item.contains(LanguageManager.get("vr.perspective.endoscopy"))) {
                windowLevel = -200;
                windowWidth = 1000;
            }
            if (glViewer != null) {
                glViewer.setWindowLevel(windowLevel, windowWidth);
            }
            repaint();
        });
        gridLeft.add(lblWL);
        gridLeft.add(cbWL);

        JLabel lblCLUT = new JLabel(LanguageManager.get("v3d.clut", "CLUT:"), JLabel.RIGHT);
        lblCLUT.setFont(new Font("SansSerif", Font.PLAIN, 10));
        JComboBox<String> cbCLUT = new JComboBox<>(new String[] {
                "No CLUT",
                "----------------",
                "B/W Inverse",
                "BlackBody",
                "Endoscopy",
                "Flow",
                "French",
                "GEcolor",
                "GrayRainbow",
                "HotGreen",
                "HotIron",
                "HotMetal",
                "Hue1",
                "Hue2",
                "ired",
                "Jet",
                "NIH",
                "Perfusion",
                "PET",
                "PPU Inferno",
                "PPU Magma",
                "PPU Plasma",
                "PPU Viridis",
                "Rainbow",
                "Rainbow2",
                "Rainbow3",
                "Ratio",
                "Spectrum",
                "Stern",
                "UCLA",
                "Unnamed",
                "VR Bones",
                "VR Muscles-Bones",
                "VR Red Vessels",
                "----------------",
                "8-bit CLUT Editor",
                "16-bit CLUT Editor"
        });
        cbCLUT.setFont(new Font("SansSerif", Font.PLAIN, 10));
        cbCLUT.setFocusable(false);
        cbCLUT.setSelectedItem("VR Muscles-Bones");
        cbCLUT.addActionListener(e -> {
            String clut = (String) cbCLUT.getSelectedItem();
            if (clut == null || clut.startsWith("-"))
                return;

            if (currentTF == null) {
                currentTF = TransferFunction.createVRMusclesBones();
            }
            currentTF.setColorType(clut);

            if (glViewer != null) {
                glViewer.setTransferFunction(currentTF);
            }
            repaint();
        });
        gridLeft.add(lblCLUT);
        gridLeft.add(cbCLUT);

        JLabel lblOpacity = new JLabel(LanguageManager.get("v3d.opacity", "Opacity:"), JLabel.RIGHT);
        lblOpacity.setFont(new Font("SansSerif", Font.PLAIN, 10));
        JComboBox<String> cbOpacity = new JComboBox<>(new String[] {
                "Linear Table",
                "Logarithmic Inverse Table",
                "Logarithmic Table",
                "Smooth Table",
                "Inverse Linear Table",
                "Inverse Logarithmic Table",
                "----------------",
                "Add an Opacity Table"
        });
        cbOpacity.setFont(new Font("SansSerif", Font.PLAIN, 10));
        cbOpacity.setFocusable(false);
        cbOpacity.setSelectedItem("Linear Table");
        cbOpacity.addActionListener(e -> {
            String op = (String) cbOpacity.getSelectedItem();
            if (op == null || op.startsWith("-"))
                return;

            if (currentTF == null) {
                currentTF = TransferFunction.createVRMusclesBones();
            }
            currentTF.setOpacityType(op);

            if (glViewer != null) {
                glViewer.setTransferFunction(currentTF);
            }
            repaint();
        });
        gridLeft.add(lblOpacity);
        gridLeft.add(cbOpacity);

        contentWLWW.add(gridLeft);

        JPanel pnlMid = new JPanel(new GridLayout(2, 1, 2, 2));
        pnlMid.setOpaque(false);
        JButton btn8 = new JButton(com.oculus.jj.utils.LanguageManager.get("vr.8bit"));
        btn8.setFont(new Font("SansSerif", Font.BOLD, 9));
        btn8.setMargin(new Insets(1, 4, 1, 4));
        btn8.setFocusable(false);
        JButton btn16 = new JButton(com.oculus.jj.utils.LanguageManager.get("vr.16bit"));
        btn16.setFont(new Font("SansSerif", Font.BOLD, 9));
        btn16.setMargin(new Insets(1, 4, 1, 4));
        btn16.setFocusable(false);
        pnlMid.add(btn8);
        pnlMid.add(btn16);
        contentWLWW.add(pnlMid);

        JButton btnPresets = new JButton(com.oculus.jj.utils.LanguageManager.get("vr.presets"));
        btnPresets.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnPresets.setMargin(new Insets(4, 4, 4, 4));
        btnPresets.setFocusable(false);
        btnPresets.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem itemMB = new JMenuItem("VR Muscle & Bone (Creamy)");
            itemMB.addActionListener(ev -> {
                currentTF = TransferFunction.createVRMusclesBones();
                cbCLUT.setSelectedItem("VR Muscles-Bones");
                cbOpacity.setSelectedItem("Linear Table");
                if (glViewer != null)
                    glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemMB);

            JMenuItem itemBone = new JMenuItem("Bone Only");
            itemBone.addActionListener(ev -> {
                currentTF = TransferFunction.createBone();
                cbCLUT.setSelectedItem("VR Bones");
                cbOpacity.setSelectedItem("Linear Table");
                if (glViewer != null)
                    glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemBone);

            JMenuItem itemRed = new JMenuItem("VR Red Vessels");
            itemRed.addActionListener(ev -> {
                currentTF.setColorType("VR Red Vessels");
                cbCLUT.setSelectedItem("VR Red Vessels");
                cbOpacity.setSelectedItem("Logarithmic Table");
                if (glViewer != null)
                    glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemRed);

            JMenuItem itemSoft = new JMenuItem("Soft Tissue (Hot Iron)");
            itemSoft.addActionListener(ev -> {
                currentTF = TransferFunction.createSoftTissue();
                cbCLUT.setSelectedItem("HotIron");
                cbOpacity.setSelectedItem("Linear Table");
                if (glViewer != null)
                    glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemSoft);

            JMenuItem itemLung = new JMenuItem("Lung (High Contrast)");
            itemLung.addActionListener(ev -> {
                currentTF = TransferFunction.createLung();
                cbCLUT.setSelectedItem("No CLUT");
                cbOpacity.setSelectedItem("Linear Table");
                if (glViewer != null)
                    glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemLung);

            JMenuItem itemVasc = new JMenuItem("Vascular / Angio");
            itemVasc.addActionListener(ev -> {
                currentTF = TransferFunction.createVascular();
                cbCLUT.setSelectedItem("Rainbow");
                if (glViewer != null)
                    glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemVasc);

            menu.show(btnPresets, 0, btnPresets.getHeight());
        });
        contentWLWW.add(btnPresets);
        vrToolbar.add(groupWLWW);

        // 3. Level of Detail & Engine & Crop & Orientation
        JPanel contentLOD = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JPanel groupLOD = createToolbarGroup(com.oculus.jj.utils.LanguageManager.get("vr.group.lod"), contentLOD);

        JPanel pnlSlider = new JPanel(new BorderLayout());
        pnlSlider.setOpaque(false);
        JLabel lblSliderName = new JLabel(LanguageManager.get("vr.lod.resolution", "LOD / Resolution"), JLabel.CENTER);
        lblSliderName.setFont(new Font("SansSerif", Font.BOLD, 9));
        pnlSlider.add(lblSliderName, BorderLayout.NORTH);

        JSlider sliderLOD = new JSlider(0, 100, 25);
        sliderLOD.setPreferredSize(new Dimension(100, 20));
        sliderLOD.setOpaque(false);
        sliderLOD.setFocusable(false);
        sliderLOD.addChangeListener(e -> {
            float factor = sliderLOD.getValue() / 100.0f;
            if (glViewer != null) {
                glViewer.setLodFactor(factor);
            }
        });
        pnlSlider.add(sliderLOD, BorderLayout.CENTER);

        JPanel pnlSliderLabels = new JPanel(new BorderLayout());
        pnlSliderLabels.setOpaque(false);
        JLabel lblFine = new JLabel(LanguageManager.get("vr.fine", "Fine"), JLabel.LEFT);
        lblFine.setFont(new Font("SansSerif", Font.PLAIN, 8));
        JLabel lblCoarse = new JLabel(LanguageManager.get("vr.coarse", "Coarse"), JLabel.RIGHT);
        lblCoarse.setFont(new Font("SansSerif", Font.PLAIN, 8));
        pnlSliderLabels.add(lblFine, BorderLayout.WEST);
        pnlSliderLabels.add(lblCoarse, BorderLayout.EAST);
        pnlSlider.add(pnlSliderLabels, BorderLayout.SOUTH);
        contentLOD.add(pnlSlider);

        JButton btnBest = new JButton(com.oculus.jj.utils.LanguageManager.get("vr.bestRendering"));
        btnBest.setFont(new Font("SansSerif", Font.BOLD, 10));
        btnBest.setMargin(new Insets(4, 4, 4, 4));
        btnBest.setFocusable(false);
        btnBest.addActionListener(e -> {
            sliderLOD.setValue(0);
            if (glViewer != null) {
                glViewer.setLodFactor(0.0f);
            }
        });
        contentLOD.add(btnBest);

        JPanel pnlEngine = new JPanel(new GridLayout(2, 1));
        pnlEngine.setOpaque(false);
        JRadioButton rbCPU = new JRadioButton("CPU", true);
        rbCPU.setFont(new Font("SansSerif", Font.PLAIN, 9));
        rbCPU.setOpaque(false);
        rbCPU.setFocusable(false);
        JRadioButton rbGPU = new JRadioButton("GPU", false);
        rbGPU.setFont(new Font("SansSerif", Font.PLAIN, 9));
        rbGPU.setOpaque(false);
        rbGPU.setFocusable(false);
        ButtonGroup bgEngine = new ButtonGroup();
        bgEngine.add(rbCPU);
        bgEngine.add(rbGPU);
        rbCPU.addActionListener(e -> {
            stopRenderLoop();
            canvas = null;
            if (glViewer != null) {
                glViewer.setEngine(GLViewer3D.ENGINE_CPU);
                glViewer.requestCpuRender();
            }
            showView("VIEW");
            repaint();
        });
        rbGPU.addActionListener(e -> {
            activateGPUMode();
            if (glViewer != null) {
                float factor = sliderLOD.getValue() / 100.0f;
                glViewer.setLodFactor(factor);
            }
        });
        pnlEngine.add(rbCPU);
        pnlEngine.add(rbGPU);
        contentLOD.add(pnlEngine);

        JToggleButton tglCrop = new JToggleButton(com.oculus.jj.utils.LanguageManager.get("vr.cropBox"));
        tglCrop.setFont(new Font("SansSerif", Font.PLAIN, 10));
        tglCrop.setMargin(new Insets(4, 4, 4, 4));
        tglCrop.setFocusable(false);
        tglCrop.addActionListener(e -> {
            this.showCropBox = tglCrop.isSelected();
            repaint();
        });
        contentLOD.add(tglCrop);

        JButton btnOrient = new JButton(com.oculus.jj.utils.LanguageManager.get("vr.orientation"));
        btnOrient.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnOrient.setMargin(new Insets(4, 4, 4, 4));
        btnOrient.setFocusable(false);
        btnOrient.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem itemLat = new JMenuItem("Lateral (Left-Right)");
            itemLat.addActionListener(ev -> {
                if (glViewer != null) {
                    glViewer.setRotation(-1.5707963f, 1.5707963f);
                }
                repaint();
            });
            menu.add(itemLat);

            JMenuItem itemAnt = new JMenuItem("Anterior-Posterior");
            itemAnt.addActionListener(ev -> {
                if (glViewer != null) {
                    glViewer.setRotation(-1.5707963f, 0.0f);
                }
                repaint();
            });
            menu.add(itemAnt);

            JMenuItem itemSup = new JMenuItem("Superior-Inferior");
            itemSup.addActionListener(ev -> {
                if (glViewer != null) {
                    glViewer.setRotation(0.0f, 0.0f);
                }
                repaint();
            });
            menu.add(itemSup);

            menu.show(btnOrient, 0, btnOrient.getHeight());
        });
        contentLOD.add(btnOrient);
        vrToolbar.add(groupLOD);

        // 4. Shading
        JPanel contentShading = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JPanel groupShading = createToolbarGroup(com.oculus.jj.utils.LanguageManager.get("vr.group.shading"),
                contentShading);

        JCheckBox chkShading = new JCheckBox(com.oculus.jj.utils.LanguageManager.get("vr.shading"), true);
        chkShading.setFont(new Font("SansSerif", Font.PLAIN, 10));
        chkShading.setOpaque(false);
        chkShading.setFocusable(false);

        JLabel lblParams = new JLabel("<html>Ambient: 0.20<br>Diffuse: 0.90<br>Specular: 0.30-15.0</html>");
        lblParams.setFont(new Font("SansSerif", Font.PLAIN, 8));
        lblParams.setForeground(Color.GRAY);

        chkShading.addActionListener(e -> {
            boolean isShaded = chkShading.isSelected();
            if (glViewer != null) {
                glViewer.setUseShading(isShaded);
            }
            lblParams.setEnabled(isShaded);
            repaint();
        });
        contentShading.add(chkShading);

        JButton btnEditShading = new JButton(com.oculus.jj.utils.LanguageManager.get("vr.edit"));
        btnEditShading.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnEditShading.setMargin(new Insets(2, 4, 2, 4));
        btnEditShading.setFocusable(false);
        btnEditShading.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Ambient: 0.20\nDiffuse: 0.90\nSpecular: 0.30\nShininess: 15.0",
                    LanguageManager.get("vr.shading_params_title", "Shading Parameters"), JOptionPane.INFORMATION_MESSAGE);
        });
        contentShading.add(btnEditShading);
        contentShading.add(lblParams);
        vrToolbar.add(groupShading);
        // HOROS-20240407準拠: Stereo mode toggle
        JPanel contentStereo = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        JCheckBox stereoCheck = new JCheckBox(com.oculus.jj.utils.LanguageManager.get("vr.stereo"));
        stereoCheck.setFont(new Font("SansSerif", Font.PLAIN, 10));
        stereoCheck.setFocusable(false);
        stereoCheck.addActionListener(e -> {
            // HOROS-20240407準拠: Stereo mode toggle - enable stereoscopic rendering
            vrStereoActive = stereoCheck.isSelected();
            if (glViewer != null) {
                glViewer.setStereoMode(stereoCheck.isSelected() ? GLViewer3D.STEREO_BUFFERED : GLViewer3D.STEREO_OFF);
            }
            repaint();
        });
        contentStereo.add(stereoCheck);
        JPanel groupStereo = createToolbarGroup(com.oculus.jj.utils.LanguageManager.get("vr.group.stereo"),
                contentStereo);
        vrToolbar.add(groupStereo);

        // 5. Perspective & Filters
        JPanel contentPersp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JPanel groupPersp = createToolbarGroup(com.oculus.jj.utils.LanguageManager.get("vr.group.persp"), contentPersp);

        JPanel pnlPerspRadios = new JPanel(new GridLayout(3, 1));
        pnlPerspRadios.setOpaque(false);
        JRadioButton rbParallel = new JRadioButton(LanguageManager.get("vr.perspective.parallel"), true);
        rbParallel.setFont(new Font("SansSerif", Font.PLAIN, 9));
        rbParallel.setOpaque(false);
        rbParallel.setFocusable(false);
        JRadioButton rbPersp = new JRadioButton(LanguageManager.get("vr.perspective.perspective"), false);
        rbPersp.setFont(new Font("SansSerif", Font.PLAIN, 9));
        rbPersp.setOpaque(false);
        rbPersp.setFocusable(false);
        JRadioButton rbEndo = new JRadioButton(LanguageManager.get("v3d.endoscopy", "Endoscopy"), false);
        rbEndo.setFont(new Font("SansSerif", Font.PLAIN, 9));
        rbEndo.setOpaque(false);
        rbEndo.setFocusable(false);

        ButtonGroup bgPersp = new ButtonGroup();
        bgPersp.add(rbParallel);
        bgPersp.add(rbPersp);
        bgPersp.add(rbEndo);

        ActionListener alPersp = ev -> {
            boolean isP = rbPersp.isSelected() || rbEndo.isSelected();
            if (glViewer != null) {
                glViewer.setPerspective(isP);
            }
            repaint();
        };
        rbParallel.addActionListener(alPersp);
        rbPersp.addActionListener(alPersp);
        rbEndo.addActionListener(alPersp);

        pnlPerspRadios.add(rbParallel);
        pnlPerspRadios.add(rbPersp);
        pnlPerspRadios.add(rbEndo);
        contentPersp.add(pnlPerspRadios);

        JPanel pnlFilter = new JPanel(new BorderLayout());
        pnlFilter.setOpaque(false);
        JLabel lblFilter = new JLabel(LanguageManager.get("vr.filter.label"), JLabel.LEFT);
        lblFilter.setFont(new Font("SansSerif", Font.BOLD, 8));
        pnlFilter.add(lblFilter, BorderLayout.NORTH);

        JComboBox<String> cbFilter = new JComboBox<>(new String[] {
                LanguageManager.get("vr.filter.preset"),
                LanguageManager.get("vr.filter.smooth5"),
                LanguageManager.get("vr.filter.blur3"),
                LanguageManager.get("vr.filter.blur5"),
                "Bone Filter 3x3",
                "Edge 3x3",
                "Emboss",
                "Emboss diagonal",
                "Emboss heavy",
                "Emboss north",
                "Emboss west",
                "Excessive edges",
                "Gaussian blur",
                "Hat",
                "Highpass 5x5",
                "Inverted blur",
                "Laplacian",
                "Laplacian 4",
                "Laplacian 8",
                "Lowpass",
                "Negative blur",
                "Sharpen 3x3",
                "Sharpen 5x5"
        });
        cbFilter.setFont(new Font("SansSerif", Font.PLAIN, 9));
        cbFilter.setFocusable(false);
        cbFilter.addActionListener(e -> {
            String selected = (String) cbFilter.getSelectedItem();
            applySelectedFilter(selected);
        });
        pnlFilter.add(cbFilter, BorderLayout.CENTER);
        contentPersp.add(pnlFilter);

        // 6. Export & Tools (Originally hidden behind double-arrow in macOS Horos)
        JPanel contentTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        JPanel groupTools = createToolbarGroup(com.oculus.jj.utils.LanguageManager.get("vr.group.tools"), contentTools);

        JButton btnClipping = new JButton(com.oculus.jj.utils.LanguageManager.get("vr.clipping"));
        btnClipping.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnClipping.setMargin(new Insets(2, 4, 2, 4));
        btnClipping.setFocusable(false);
        btnClipping.addActionListener(e -> showClippingDialog());
        contentTools.add(btnClipping);
        // HOROS-20240407準拠: Fusion/Blending toggle (Horos 2DBlending)
        JButton btnFusion = new JButton(com.oculus.jj.utils.LanguageManager.get("vr.fusion"));
        btnFusion.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnFusion.setMargin(new Insets(2, 4, 2, 4));
        btnFusion.setFocusable(false);
        final boolean[] fusionOn = { false };
        btnFusion.addActionListener(e -> {
            fusionOn[0] = !fusionOn[0];
            // fusion mode - GLViewer3D.setFusionMode() not yet implemented
        });
        contentTools.add(btnFusion);

        // HOROS-20240407準拠: ROI Manager (ROIManager.pdf)
        JButton btnROIManager = new JButton(com.oculus.jj.utils.LanguageManager.get("vr.roiManager"));
        btnROIManager.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnROIManager.setMargin(new Insets(2, 4, 2, 4));
        btnROIManager.setFocusable(false);
        btnROIManager.addActionListener(e -> {
            // HOROS-20240407準拠: [roiGetManager:] - ROI Manager ウィンドウ表示
            // System.out.println("[VR] Opening ROI Manager...");
            // TODO: Show ROI management dialog linked to current series ROIs
        });
        contentTools.add(btnROIManager);

        JButton btnReset = new JButton(com.oculus.jj.utils.LanguageManager.get("button.reset"));
        btnReset.setFont(new Font("SansSerif", Font.BOLD, 10));
        btnReset.setMargin(new Insets(2, 4, 2, 4));
        btnReset.setFocusable(false);
        btnReset.addActionListener(e -> {
            rotateX = -Math.PI / 2;
            rotateY = Math.PI / 2;
            zoom = 1.0;
            if (glViewer != null) {
                glViewer.setZoom((float) zoom);
                glViewer.setRotation((float) rotateX, (float) rotateY);
                glViewer.setOffset(0, 0, 0);
            }
            if (cpuRenderer != null) {
                cpuRenderer.setOffset(0, 0, 0);
            }
            windowLevel = 1024;
            windowWidth = 4096;
            if (glViewer != null) {
                glViewer.setWindowLevel(windowLevel, windowWidth);
            }
            updateInfoLabel(-1, -1);
            repaint();
        });
        contentTools.add(btnReset);

        String[] extraTools = { LanguageManager.get("vr.extra.movie", "Movie Export"), LanguageManager.get("vr.extra.orient", "Orientations") };
        for (String label : extraTools) {
            JButton btn = new JButton(label);
            btn.setFont(new Font("SansSerif", Font.PLAIN, 10));
            btn.setMargin(new Insets(2, 4, 2, 4));
            btn.setFocusable(false);
            contentTools.add(btn);
        }

        JButton btnRevert = new JButton(com.oculus.jj.utils.LanguageManager.get("button.revert"));
        btnRevert.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnRevert.setMargin(new Insets(2, 4, 2, 4));
        btnRevert.setFocusable(false);
        btnRevert.addActionListener(e -> {
            if (volumeBackup != null && volume != null) {
                System.arraycopy(volumeBackup, 0, volume, 0, volume.length);
                if (glViewer != null) {
                    glViewer.setVolumeData(volume, null, dimX, dimY, dimZ);
                    glViewer.triggerVolumeReupload();
                }
                if (cpuRenderer != null) {
                    cpuRenderer.setVolumeData(volume, dimX, dimY, dimZ);
                }
                repaint();
            }
        });
        contentTools.add(btnRevert);

        String[] extraTools2 = {
                com.oculus.jj.utils.LanguageManager.get("vr.extra.dicomFile"),
                com.oculus.jj.utils.LanguageManager.get("vr.extra.flyThru")
        };
        for (String label : extraTools2) {
            JButton btn = new JButton(label);
            btn.setFont(new Font("SansSerif", Font.PLAIN, 10));
            btn.setMargin(new Insets(2, 4, 2, 4));
            btn.setFocusable(false);
            contentTools.add(btn);
        }
        vrToolbar.add(groupPersp);
        vrToolbar.add(groupTools);

        setLayout(new BorderLayout());
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(vrToolbar, BorderLayout.NORTH);

        infoLabel = new JLabel("View Size: 512 x 512", JLabel.LEFT);
        infoLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        infoLabel.setOpaque(true);
        infoLabel.setBackground(Color.BLACK);
        infoLabel.setForeground(Color.WHITE);
        infoLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        topPanel.add(infoLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        glViewer = createGLViewer(buildGLData(glConfigIndex));

        this.loadingLabel = new JLabel(LanguageManager.get("vr.preparing", "Preparing Volume Data (Converting to HU)..."), JLabel.CENTER);
        loadingLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        loadingLabel.setForeground(Color.WHITE);

        // CardLayout: Swing and Heavyweight AWTGLCanvas conflict on Windows.
        // We use BorderLayout to keep glViewer visible at all times, preventing native
        // HWND destruction,
        // and draw the loading screen as a high-performance Java2D overlay.
        JPanel loadingPanel = new JPanel(new BorderLayout());
        loadingPanel.setBackground(Color.BLACK);
        loadingPanel.add(loadingLabel, BorderLayout.CENTER);

        mainContainer = new JPanel(new BorderLayout());
        mainContainer.setOpaque(false);
        mainContainer.add(glViewer, BorderLayout.CENTER);

        add(mainContainer, BorderLayout.CENTER);

        showView("LOADING"); // 初期表示: データロード中

        // JVM レベルでキー入力を確実に補足するグローバルキーディスパッチャー
        // LWJGL の重量コンポーネント (AWTGLCanvas) がフォーカスを持つ場合でも、KeyboardFocusManager を通じて
        // ショートカット (WL/WW, Rotate, Scissor, Dispose) が確実に動作するようにする。
        keyboardShortcutDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED)
                return false;

            Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            // 自分のウィンドウまたはその子ウィンドウ（ダイアログ等）がアクティブでない場合は無視
            if (activeWindow != this && (activeWindow == null || activeWindow.getOwner() != this))
                return false;

            int keyCode = e.getKeyCode();
            switch (keyCode) {
                case KeyEvent.VK_W:
                    selectTool(Tool3D.WLWW);
                    return true;
                case KeyEvent.VK_S:
                    selectTool(Tool3D.ROTATE);
                    return true;
                case KeyEvent.VK_Z:
                    selectTool(Tool3D.ZOOM);
                    return true;
                case KeyEvent.VK_M:
                    selectTool(Tool3D.MEASURE);
                    activeRoi = null;
                    repaint();
                    return true;
                case KeyEvent.VK_O:
                    selectTool(Tool3D.ROI);
                    activeRoi = null;
                    repaint();
                    return true;
                case KeyEvent.VK_C:
                    selectTool(Tool3D.SCISSOR);
                    activeRoi = null;
                    repaint();
                    return true;
                case KeyEvent.VK_P:
                    selectTool(Tool3D.PAN);
                    return true;
                case KeyEvent.VK_ESCAPE:
                    if (currentTool == Tool3D.SCISSOR) {
                        scissorPoints.clear();
                        repaint();
                        return true;
                    } else {
                        dispose();
                        return true;
                    }
                case KeyEvent.VK_DELETE:
                case KeyEvent.VK_BACK_SPACE:
                    if (currentTool == Tool3D.SCISSOR) {
                        if (scissorPoints.size() >= 3) {
                            performScissorCut(true); // Clear Inside
                            return true;
                        } else if (!scissorPoints.isEmpty()) {
                            scissorPoints.clear();
                            repaint();
                            return true;
                        }
                    } else if (activeRoi != null) {
                        roiList.remove(activeRoi);
                        activeRoi = null;
                        repaint();
                        return true;
                    }
                    break;
                case KeyEvent.VK_ENTER:
                    if (currentTool == Tool3D.SCISSOR && scissorPoints.size() >= 3) {
                        performScissorCut(false); // Keep Inside
                        return true;
                    }
                    break;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyboardShortcutDispatcher);

        // ダイアログが閉じられたらクリーンアップ
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent we) {
                // クリーンアップ
            }
        });
    }

    private JPanel makeClipSlider(String label, int init, java.util.function.Consumer<Integer> cb) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(11f));
        p.add(lbl, BorderLayout.NORTH);
        JSlider s = new JSlider(0, 100, init);
        s.setOpaque(false);
        s.addChangeListener(e -> {
            cb.accept(s.getValue());
            repaint();
        });
        p.add(s, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(230, 48));
        return p;
    }

    private JPanel createToolbarGroup(String title, JPanel content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        content.setOpaque(false);
        p.add(content, BorderLayout.CENTER);
        JLabel lbl = new JLabel(title, JLabel.CENTER);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lbl.setForeground(Color.GRAY);
        p.add(lbl, BorderLayout.SOUTH);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY));
        return p;
    }

    private void showClippingDialog() {
        if (clippingDialog == null) {
            clippingDialog = new JDialog(this, LanguageManager.get("vr.clipping_title", "3D Clipping"), false);
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            content.setBackground(new Color(240, 240, 240));

            content.add(makeClipSlider(LanguageManager.get("vr.clip.xmin", "X-Min (%)"), (int) (clipMinX * 100), v -> {
                clipMinX = v / 100f;
                applyClipping();
            }));
            content.add(makeClipSlider(LanguageManager.get("vr.clip.xmax", "X-Max (%)"), (int) (clipMaxX * 100), v -> {
                clipMaxX = v / 100f;
                applyClipping();
            }));
            content.add(Box.createVerticalStrut(10));
            content.add(makeClipSlider(LanguageManager.get("vr.clip.ymin", "Y-Min (%)"), (int) (clipMinY * 100), v -> {
                clipMinY = v / 100f;
                applyClipping();
            }));
            content.add(makeClipSlider(LanguageManager.get("vr.clip.ymax", "Y-Max (%)"), (int) (clipMaxY * 100), v -> {
                clipMaxY = v / 100f;
                applyClipping();
            }));
            content.add(Box.createVerticalStrut(10));
            content.add(makeClipSlider(LanguageManager.get("vr.clip.zmin", "Z-Min (%)"), (int) (clipMinZ * 100), v -> {
                clipMinZ = v / 100f;
                applyClipping();
            }));
            content.add(makeClipSlider(LanguageManager.get("vr.clip.zmax", "Z-Max (%)"), (int) (clipMaxZ * 100), v -> {
                clipMaxZ = v / 100f;
                applyClipping();
            }));

            clippingDialog.add(content);
            clippingDialog.pack();
            clippingDialog.setLocationRelativeTo(this);
        }
        clippingDialog.setVisible(!clippingDialog.isVisible());
    }

    private void applyClipping() {
        if (glViewer != null)
            glViewer.setClipping(clipMinX, clipMaxX, clipMinY, clipMaxY, clipMinZ, clipMaxZ);
        if (cpuRenderer != null)
            cpuRenderer.setClipping(clipMinX, clipMaxX, clipMinY, clipMaxY, clipMinZ, clipMaxZ);
    }

    private void setLoadingStatus(String status) {
        this.loadingStatus = status;
        if (loadingLabel != null) {
            loadingLabel.setText(status != null
                    ? "<html><center>" + status + "</center></html>"
                    : "");
        }
        if (glViewer != null) {
            glViewer.markOverlayDirty();
            glViewer.repaint();
        }
    }

    private void drawOverlay(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (loadingStatus != null && !loadingStatus.isEmpty()) {
            int w = glViewer != null ? glViewer.getWidth() : 0;
            int h = glViewer != null ? glViewer.getHeight() : 0;
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, w, h);

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
            FontMetrics fm = g2d.getFontMetrics();
            String cleanStatus = loadingStatus.replaceAll("<[^>]*>", ""); // strip HTML tags
            int lw = fm.stringWidth(cleanStatus);
            g2d.drawString(cleanStatus, w / 2 - lw / 2, h / 2 + fm.getAscent() / 2);
            return;
        }

        if (showCropBox)
            drawCropBox(g2d);
        drawMeasurements(g2d);
        drawOrientationOverlay(g2d);

        // Draw Scissors instructions when drawing a lasso
        if (currentTool == Tool3D.SCISSOR && !scissorPoints.isEmpty()) {
            int w = glViewer != null ? glViewer.getWidth() : 0;
            int h = glViewer != null ? glViewer.getHeight() : 0;
            String text = LanguageManager.get("vr.scissors_hint", "[Scissors] Delete: Clear Inside | Enter: Keep Inside (Clear Outside) | Esc: Cancel");
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fm = g2d.getFontMetrics();
            int tw = fm.stringWidth(text);
            int tx = (w - tw) / 2;
            int ty = h - 30;

            // Draw semi-transparent background bubble
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRoundRect(tx - 10, ty - fm.getAscent() - 6, tw + 20, fm.getHeight() + 12, 10, 10);

            // Draw border (yellow-green to match lasso color)
            g2d.setColor(new Color(173, 255, 47));
            g2d.drawRoundRect(tx - 10, ty - fm.getAscent() - 6, tw + 20, fm.getHeight() + 12, 10, 10);

            // Draw text
            g2d.setColor(Color.WHITE);
            g2d.drawString(text, tx, ty);
        }
    }

    /** HOROS準拠: 方位ラベル(S/I/A/P/L/R)・Rボックス・WL情報を描画 */
    private void drawOrientationOverlay(Graphics2D g) {
        int w = glViewer != null ? glViewer.getWidth() : 0;
        int h = glViewer != null ? glViewer.getHeight() : 0;

        double rx = rotateX, ry = rotateY;
        String[] anatomicalLabels = computeOrientationLabels(rx, ry);
        String labelTop = anatomicalLabels[0];
        String labelBottom = anatomicalLabels[1];
        String labelLeft = anatomicalLabels[2];
        String labelRight = anatomicalLabels[3];

        Font infoFont = new Font("Monospaced", Font.PLAIN, 12);
        g.setFont(infoFont);
        g.setColor(Color.WHITE);
        FontMetrics ifm = g.getFontMetrics(infoFont);
        String[] coordLines = {
                String.format("S-I  %.1f", -rotateX),
                String.format("L-R  %.1f", rotateY),
                String.format("ROI T %.1f", zoom)
        };
        int margin = 12;
        int cy2 = h - margin - ifm.getHeight() * (coordLines.length - 1);
        for (String line : coordLines) {
            int cw = ifm.stringWidth(line);
            g.drawString(line, w - cw - margin, cy2);
            cy2 += ifm.getHeight();
        }

        Font oriFont = new Font("SansSerif", Font.BOLD, 16);
        g.setFont(oriFont);
        FontMetrics fm = g.getFontMetrics(oriFont);

        g.setColor(Color.BLACK);
        int lw = fm.stringWidth(labelTop);
        g.drawString(labelTop, w / 2 - lw / 2 + 1, margin + fm.getAscent() + 1);
        lw = fm.stringWidth(labelBottom);
        g.drawString(labelBottom, w / 2 - lw / 2 + 1, h - margin + 1);
        g.drawString(labelLeft, margin + 1, h / 2 + fm.getAscent() / 2 + 1);
        lw = fm.stringWidth(labelRight);
        g.drawString(labelRight, w - margin - lw + 1, h / 2 + fm.getAscent() / 2 + 1);

        g.setColor(getOrientationColor(labelTop));
        lw = fm.stringWidth(labelTop);
        g.drawString(labelTop, w / 2 - lw / 2, margin + fm.getAscent());

        g.setColor(getOrientationColor(labelBottom));
        lw = fm.stringWidth(labelBottom);
        g.drawString(labelBottom, w / 2 - lw / 2, h - margin);

        g.setColor(getOrientationColor(labelLeft));
        g.drawString(labelLeft, margin, h / 2 + fm.getAscent() / 2);

        g.setColor(getOrientationColor(labelRight));
        lw = fm.stringWidth(labelRight);
        g.drawString(labelRight, w - margin - lw, h / 2 + fm.getAscent() / 2);

        g.drawString(String.format("WL: %.0f  WW: %.0f", windowLevel, windowWidth), 10, 20);
        g.drawString(String.format("Mode: %s | %s", mode, isGpuEngine() ? "GPU" : "CPU"), 10, 36);
        g.drawString(String.format("Zoom: %.2fx", zoom), 10, 52);
    }

    private boolean isGpuEngine() {
        return glViewer != null && glViewer.getEngine() == GLViewer3D.ENGINE_GPU;
    }

    private GLData buildGLData(int index) {
        GLData data = new GLData();
        data.samples = 0; // ドライバー互換性を最大化
        data.doubleBuffer = true;

        if (index == 0) {
            data.majorVersion = 3;
            data.minorVersion = 3;
            data.profile = GLData.Profile.CORE;
        } else if (index == 1) {
            data.majorVersion = 3;
            data.minorVersion = 2;
            data.profile = GLData.Profile.COMPATIBILITY;
        } else if (index == 2) {
            data.majorVersion = 3;
            data.minorVersion = 0;
        } else {
            data.majorVersion = 2;
            data.minorVersion = 1;
        }
        return data;
    }

    private GLViewer3D createGLViewer(GLData glData) {
        GLViewer3D viewer = new GLViewer3D(glData, volume, dimX, dimY, dimZ);
        viewer.setBackground(Color.BLACK); // AWTの灰色消去バグを黒色で無効化
        viewer.addMouseListener(glMouseHandler);
        viewer.addMouseMotionListener(glMouseHandler);
        viewer.addMouseWheelListener(glMouseWheelHandler);
        viewer.setOverlayPainter(this::drawOverlay);
        viewer.setGpuContextFailureHandler(() -> SwingUtilities.invokeLater(this::handleGpuContextFailure));
        // CardLayout でカードが表示されたとき (componentShown) に即座に render() を呼ぶ。
        viewer.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                // System.out.println("DEBUG: glViewer shown, size=" + viewer.getWidth() + "x" +
                // viewer.getHeight());
                if (canvas == viewer) {
                    try {
                        viewer.render();
                    } catch (Throwable t) {
                        /* suppress first-frame errors */
                    }
                }
            }
        });
        return viewer;
    }

    private void recreateGLViewerForRetry() {
        GLViewer3D oldViewer = glViewer;
        GLViewer3D newViewer = createGLViewer(buildGLData(glConfigIndex));

        if (cpuRenderer != null) {
            newViewer.setCpuRenderer(cpuRenderer);
        }
        if (volume != null && dimX > 0 && dimY > 0 && dimZ > 0) {
            newViewer.setVolumeData(volume, null, dimX, dimY, dimZ);
        }
        newViewer.setTransferFunction(currentTF);
        newViewer.setWindowLevel(windowLevel, windowWidth);
        newViewer.setClipping(clipMinX, clipMaxX, clipMinY, clipMaxY, clipMinZ, clipMaxZ);
        newViewer.setVoxelSpacing((float) spacingX, (float) spacingY, (float) spacingZ);
        newViewer.setRotation((float) rotateX, (float) rotateY);
        newViewer.setZoom((float) zoom);

        if (mainContainer != null && oldViewer != null) {
            mainContainer.remove(oldViewer);
            mainContainer.add(newViewer, "VIEW");
            mainContainer.revalidate();
            mainContainer.repaint();
        }

        glViewer = newViewer;
        setMode(this.mode);
        canvas = null;
    }

    private void handleGpuContextFailure() {
        if (gpuRecoveryInProgress) {
            return;
        }
        gpuRecoveryInProgress = true;
        try {
            gpuActivationInProgress = false;
            stopRenderLoop();
            canvas = null;

            if (glConfigIndex < 3) {
                glConfigIndex++;
                gpuInitialized = false;
                gpuContextUnsupported = false;
                // System.err.println("DEBUG: Retrying GPU initialization with GL config index="
                // + glConfigIndex);
                if (loadingLabel != null) {
                    setLoadingStatus("Retrying GPU context with compatible GL profile...");
                }
                showView("LOADING");
                recreateGLViewerForRetry();
                activateGPUMode();
            } else {
                gpuContextUnsupported = true;
                // System.err.println("DEBUG: All GL configs failed. GPU mode unavailable.");
                if (glViewer != null) {
                    glViewer.setEngine(GLViewer3D.ENGINE_CPU);
                    glViewer.requestCpuRender();
                }
                showView("VIEW");
            }
        } finally {
            gpuRecoveryInProgress = false;
        }
    }

    private void drawCropBox(Graphics2D g) {
        int w = glViewer != null ? glViewer.getWidth() : 0;
        int h = glViewer != null ? glViewer.getHeight() : 0;
        double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
        double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);
        double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;
        double physX = dimX * spacingX;
        double physY = dimY * spacingY;
        double physZ = dimZ * spacingZ;
        double maxPhys = Math.max(physX, Math.max(physY, physZ));
        double baseScale = Math.min(w, h) / maxPhys * 0.95;
        double currentScale = baseScale * zoom;

        double[][] corners = {
                { clipMinX * dimX, clipMinY * dimY, clipMinZ * dimZ },
                { clipMaxX * dimX, clipMinY * dimY, clipMinZ * dimZ },
                { clipMaxX * dimX, clipMaxY * dimY, clipMinZ * dimZ },
                { clipMinX * dimX, clipMaxY * dimY, clipMinZ * dimZ },
                { clipMinX * dimX, clipMinY * dimY, clipMaxZ * dimZ },
                { clipMaxX * dimX, clipMinY * dimY, clipMaxZ * dimZ },
                { clipMaxX * dimX, clipMaxY * dimY, clipMaxZ * dimZ },
                { clipMinX * dimX, clipMaxY * dimY, clipMaxZ * dimZ }
        };

        Point[] pts = new Point[8];
        for (int i = 0; i < 8; i++) {
            double tx = corners[i][0] - cx;
            double ty = corners[i][1] - cy;
            double tz = corners[i][2] - cz;
            double x1 = tx * cosY + tz * sinY;
            double z1 = -tx * sinY + tz * cosY;
            double x2 = x1;
            double y2 = ty * cosX - z1 * sinX;
            pts[i] = new Point((int) (w / 2.0 + x2 * currentScale), (int) (h / 2.0 + y2 * currentScale));
        }

        g.setColor(Color.GREEN);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(pts[0].x, pts[0].y, pts[1].x, pts[1].y);
        g.drawLine(pts[1].x, pts[1].y, pts[2].x, pts[2].y);
        g.drawLine(pts[2].x, pts[2].y, pts[3].x, pts[3].y);
        g.drawLine(pts[3].x, pts[3].y, pts[0].x, pts[0].y);
        g.drawLine(pts[4].x, pts[4].y, pts[5].x, pts[5].y);
        g.drawLine(pts[5].x, pts[5].y, pts[6].x, pts[6].y);
        g.drawLine(pts[6].x, pts[6].y, pts[7].x, pts[7].y);
        g.drawLine(pts[7].x, pts[7].y, pts[4].x, pts[4].y);
        g.drawLine(pts[0].x, pts[0].y, pts[4].x, pts[4].y);
        g.drawLine(pts[1].x, pts[1].y, pts[5].x, pts[5].y);
        g.drawLine(pts[2].x, pts[2].y, pts[6].x, pts[6].y);
        g.drawLine(pts[3].x, pts[3].y, pts[7].x, pts[7].y);
        for (Point p : pts) {
            g.fillRect(p.x - 3, p.y - 3, 6, 6);
        }
    }

    private String[] computeOrientationLabels(double rx, double ry) {
        // Mathematically correct transformation of camera screen-space UP and RIGHT
        // vectors back to volume space
        // for the model rotation matrix: R = rotateY(ry) * rotateX(rx)

        // Camera UP vector in volume space: U_vol = (0, cos(rx), -sin(rx))
        double ux_vol = 0;
        double uy_vol = Math.cos(rx);
        double uz_vol = -Math.sin(rx);
        String top = dominantAxis(ux_vol, uy_vol, uz_vol);
        String bottom = opposite(top);

        // Camera RIGHT vector in volume space: R_vol = (cos(ry), -sin(ry) * sin(rx),
        // sin(ry) * cos(rx))
        double rx_vol = Math.cos(ry);
        double ry_vol = -Math.sin(ry) * Math.sin(rx);
        double rz_vol = Math.sin(ry) * Math.cos(rx);
        String right2 = dominantAxis(rx_vol, ry_vol, rz_vol);
        String left2 = opposite(right2);

        return new String[] { top, bottom, left2, right2 };
    }

    private Color getOrientationColor(String label) {
        if (label == null)
            return Color.WHITE;
        if (label.contains("S") || label.contains("I"))
            return Color.YELLOW;
        if (label.contains("A") || label.contains("P"))
            return Color.GREEN;
        if (label.contains("L") || label.contains("R"))
            return new Color(100, 150, 255);
        return Color.WHITE;
    }

    private String dominantAxis(double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (az >= ax && az >= ay)
            return z >= 0 ? "S" : "I";
        if (ax >= ay)
            return x >= 0 ? "R" : "L";
        return y >= 0 ? "P" : "A";
    }

    private String opposite(String s) {
        switch (s) {
            case "S":
                return "I";
            case "I":
                return "S";
            case "R":
                return "L";
            case "L":
                return "R";
            case "A":
                return "P";
            default:
                return "A";
        }
    }

    private com.oculus.jj.model.ROI.Point3D getVoxelUnderMouse(int sx, int sy) {
        int w = glViewer != null ? glViewer.getWidth() : 0;
        int h = glViewer != null ? glViewer.getHeight() : 0;
        if (w <= 0 || h <= 0)
            return null;

        double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
        double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);
        double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;
        double physX = dimX * spacingX;
        double physY = dimY * spacingY;
        double physZ = dimZ * spacingZ;
        double maxPhys = Math.max(physX, Math.max(physY, physZ));
        double baseScale = Math.min(w, h) / maxPhys * 0.95;
        double currentScale = baseScale * zoom;

        // View-space panning offsets in physical mm
        double ox = glViewer != null ? glViewer.getOffsetX() * maxPhys : 0.0;
        double oy = glViewer != null ? glViewer.getOffsetY() * maxPhys : 0.0;
        double oz = glViewer != null ? glViewer.getOffsetZ() * maxPhys : 0.0;

        double vx = (sx - w / 2.0) / currentScale - ox;
        double vy = (sy - h / 2.0) / currentScale - oy;
        double zLimit = Math.max(dimX, Math.max(dimY, dimZ)) * 1.2;

        double rayStep = 1.0;

        // If we are performing bone removal, search specifically for the first voxel >=
        // 250 HU along the ray
        if (currentTool == Tool3D.BONE_REMOVAL) {
            for (double vz = -zLimit; vz < zLimit; vz += rayStep) {
                double vz_offset = vz - oz;
                double tx = vx * cosY + vz_offset * sinY;
                double tz0 = -vx * sinY + vz_offset * cosY;
                double ty = vy * cosX - tz0 * sinX;
                double tz = vy * sinX + tz0 * cosX;
                int ix = (int) (tx + cx), iy = (int) (ty + cy), iz = (int) (tz + cz);
                if (ix >= 0 && ix < dimX && iy >= 0 && iy < dimY && iz >= 0 && iz < dimZ) {
                    int val = volume[iz * dimY * dimX + iy * dimX + ix];
                    if (val >= 250) {
                        return new com.oculus.jj.model.ROI.Point3D(ix, iy, iz);
                    }
                }
            }
            return null;
        }

        for (double vz = -zLimit; vz < zLimit; vz += rayStep) {
            double vz_offset = vz - oz;
            double tx = vx * cosY + vz_offset * sinY;
            double tz0 = -vx * sinY + vz_offset * cosY;
            double ty = vy * cosX - tz0 * sinX;
            double tz = vy * sinX + tz0 * cosX;
            int ix = (int) (tx + cx), iy = (int) (ty + cy), iz = (int) (tz + cz);
            if (ix >= 0 && ix < dimX && iy >= 0 && iy < dimY && iz >= 0 && iz < dimZ) {
                int val = volume[iz * dimY * dimX + iy * dimX + ix];
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
                    if (val > (windowLevel - windowWidth / 4.0))
                        return new com.oculus.jj.model.ROI.Point3D(ix, iy, iz);
                }
            }
        }
        return null;
    }

    private void drawMeasurements(Graphics2D g) {
        int w = glViewer != null ? glViewer.getWidth() : 0;
        int h = glViewer != null ? glViewer.getHeight() : 0;
        if (w <= 0 || h <= 0)
            return;

        double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
        double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);

        double physX = dimX * spacingX;
        double physY = dimY * spacingY;
        double physZ = dimZ * spacingZ;
        double maxPhys = Math.max(physX, Math.max(physY, physZ));

        // GLViewer3D の投影ロジックと一致させる
        double currentScale = Math.min(w, h) * zoom;
        double ox = glViewer != null ? glViewer.getOffsetX() : 0.0;
        double oy = glViewer != null ? glViewer.getOffsetY() : 0.0;

        double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;

        java.awt.Font originalFont = g.getFont();
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));

        if (!roiList.isEmpty()) {
            for (com.oculus.jj.model.ROI roi : roiList) {
                List<com.oculus.jj.model.ROI.Point3D> points = roi.getPoints3D();
                if (points.isEmpty())
                    continue;

                Point prevP = null;
                com.oculus.jj.model.ROI.Point3D prevV = null;

                Color roiColor = (roi == activeRoi || roi == draggedRoi) ? Color.CYAN
                        : com.oculus.jj.config.AppPreferences.getROIColor();

                // First pass: Draw shadows and lines
                if (points.size() >= 1) {
                    Point[] screenPts = new Point[points.size()];
                    for (int i = 0; i < points.size(); i++) {
                        com.oculus.jj.model.ROI.Point3D vpt = points.get(i);

                        // 1. Normalize to Unit Cube with Spacing
                        double px_local = (vpt.x - cx) * spacingX / maxPhys;
                        double py_local = (vpt.y - cy) * spacingY / maxPhys;
                        double pz_local = (vpt.z - cz) * spacingZ / maxPhys;

                        // 2. RX Rotate
                        double ry_rx = py_local * cosX - pz_local * sinX;
                        double rz_rx = py_local * sinX + pz_local * cosX;

                        // 3. RY Rotate
                        double rx_ry = px_local * cosY + rz_rx * sinY;
                        double ry_ry = ry_rx;

                        // 4. Project (Applying Offset and Screen Scale)
                        int sx = (int) Math.round((rx_ry + ox) * currentScale + w / 2.0);
                        int sy = (int) Math.round(h / 2.0 - (ry_ry + oy) * currentScale);
                        screenPts[i] = new Point(sx, sy);
                    }

                    if (roi.getType() == DCMView.ToolMode.tMesure && points.size() >= 2) {
                        // Draw shadow line
                        g.setColor(Color.BLACK);
                        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        for (int i = 1; i < screenPts.length; i++) {
                            g.drawLine(screenPts[i - 1].x, screenPts[i - 1].y, screenPts[i].x, screenPts[i].y);
                        }
                        // Draw colored measurement line
                        g.setColor(roiColor);
                        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        for (int i = 1; i < screenPts.length; i++) {
                            g.drawLine(screenPts[i - 1].x, screenPts[i - 1].y, screenPts[i].x, screenPts[i].y);
                        }
                    }
                } else if (roi.getType() == DCMView.ToolMode.tOval && points.size() >= 2) {
                    com.oculus.jj.model.ROI.Point3D p1 = points.get(0);
                    com.oculus.jj.model.ROI.Point3D p2 = points.get(1);

                    double tx1 = p1.x - cx;
                    double ty1 = p1.y - cy;
                    double tz1 = p1.z - cz;
                    double ry_view1 = ty1 * cosX + tz1 * sinX;
                    double tz0_1 = tz1 * cosX - ty1 * sinX;
                    double rx_view1 = tx1 * cosY - tz0_1 * sinY;
                    int sx1 = (int) Math.round((rx_view1 + ox) * currentScale + w / 2.0);
                    int sy1 = (int) Math.round((ry_view1 + oy) * currentScale + h / 2.0);

                    double tx2 = p2.x - cx;
                    double ty2 = p2.y - cy;
                    double tz2 = p2.z - cz;
                    double ry_view2 = ty2 * cosX + tz2 * sinX;
                    double tz0_2 = tz2 * cosX - ty2 * sinX;
                    double rx_view2 = tx2 * cosY - tz0_2 * sinY;
                    int sx2 = (int) Math.round((rx_view2 + ox) * currentScale + w / 2.0);
                    int sy2 = (int) Math.round((ry_view2 + oy) * currentScale + h / 2.0);

                    int minX = Math.min(sx1, sx2);
                    int minY = Math.min(sy1, sy2);
                    int width = Math.abs(sx1 - sx2);
                    int height = Math.abs(sy1 - sy2);

                    if (width > 0 && height > 0) {
                        // Draw shadow oval (thick black line behind)
                        g.setColor(Color.BLACK);
                        g.setStroke(new BasicStroke(4f));
                        g.drawOval(minX, minY, width, height);

                        // Draw colored oval
                        g.setColor(roiColor);
                        g.setStroke(new BasicStroke(2f));
                        g.drawOval(minX, minY, width, height);

                        // Calculate physical area in cm²
                        double rx_mm = (width / 2.0) / currentScale;
                        double ry_mm = (height / 2.0) / currentScale;
                        double area_cm2 = (Math.PI * rx_mm * ry_mm) / 100.0;

                        // Compute voxel stats inside the projected ellipse at the depth of the first
                        // point
                        double depth = getRoiStartDepth(roi);
                        double xc = (sx1 + sx2) / 2.0;
                        double yc = (sy1 + sy2) / 2.0;
                        double a = width / 2.0;
                        double b = height / 2.0;

                        int count = 0;
                        double sum = 0;
                        double minVal = Double.MAX_VALUE;
                        double maxVal = -Double.MAX_VALUE;
                        java.util.List<Integer> sampledVals = new java.util.ArrayList<>();

                        // Only sample pixels in a grid inside the screen-space bounding box
                        for (int y = minY; y <= minY + height; y++) {
                            for (int x = minX; x <= minX + width; x++) {
                                double term1 = Math.pow(x - xc, 2) / Math.pow(a, 2);
                                double term2 = Math.pow(y - yc, 2) / Math.pow(b, 2);
                                if (term1 + term2 <= 1.0) {
                                    com.oculus.jj.model.ROI.Point3D vpt = getVoxelAtDepth(x, y, depth);
                                    if (vpt != null && volume != null) {
                                        int ix = (int) vpt.x;
                                        int iy = (int) vpt.y;
                                        int iz = (int) vpt.z;
                                        if (ix >= 0 && ix < dimX && iy >= 0 && iy < dimY && iz >= 0 && iz < dimZ) {
                                            int val = volume[iz * dimY * dimX + iy * dimX + ix];
                                            sum += val;
                                            if (val < minVal)
                                                minVal = val;
                                            if (val > maxVal)
                                                maxVal = val;
                                            sampledVals.add(val);
                                            count++;
                                        }
                                    }
                                }
                            }
                        }

                        double mean = count > 0 ? sum / count : 0.0;
                        double varianceSum = 0;
                        for (int val : sampledVals) {
                            varianceSum += Math.pow(val - mean, 2);
                        }
                        double sd = count > 1 ? Math.sqrt(varianceSum / (count - 1)) : 0.0;

                        // Create label string
                        String label;
                        if (count > 0) {
                            label = String.format("Area: %.2f cm²\nMean: %.1f HU\nSD: %.1f HU\nMin: %.0f, Max: %.0f",
                                    area_cm2, mean, sd, minVal, maxVal);
                        } else {
                            label = String.format("Area: %.2f cm²", area_cm2);
                        }

                        // Display multiline label with semi-transparent background
                        FontMetrics fm = g.getFontMetrics();
                        String[] lines = label.split("\n");
                        int maxTw = 0;
                        for (String line : lines) {
                            int tw = fm.stringWidth(line);
                            if (tw > maxTw)
                                maxTw = tw;
                        }
                        int th = fm.getHeight();
                        int padX = 8;
                        int padY = 6;
                        int lineSpacing = 2;

                        int bx = minX + width / 2 - maxTw / 2 - padX;
                        int by = minY + height + 10;
                        int bw = maxTw + padX * 2;
                        int bh = lines.length * th + (lines.length - 1) * lineSpacing + padY * 2;

                        g.setColor(new Color(0, 0, 0, 160));
                        g.fillRoundRect(bx, by, bw, bh, 8, 8);
                        g.setColor(new Color(255, 255, 255, 60));
                        g.setStroke(new BasicStroke(1f));
                        g.drawRoundRect(bx, by, bw, bh, 8, 8);

                        g.setColor(Color.WHITE);
                        for (int j = 0; j < lines.length; j++) {
                            g.drawString(lines[j], bx + padX,
                                    by + padY + (j + 1) * th + j * lineSpacing - fm.getDescent() - 1);
                        }
                    }
                }

                // Second pass: Draw nodes and labels
                for (int i = 0; i < points.size(); i++) {
                    if (roi.getType() == DCMView.ToolMode.tOval && roi != activeRoi) {
                        continue;
                    }
                    com.oculus.jj.model.ROI.Point3D vpt = points.get(i);
                    double tx = vpt.x - cx;
                    double ty = vpt.y - cy;
                    double tz = vpt.z - cz;
                    double ry_view = ty * cosX + tz * sinX;
                    double tz0 = tz * cosX - ty * sinX;
                    double rx_view = tx * cosY - tz0 * sinY;

                    int sx = (int) Math.round((rx_view + ox) * currentScale + w / 2.0);
                    int sy = (int) Math.round((ry_view + oy) * currentScale + h / 2.0);

                    if (roi.getType() == DCMView.ToolMode.t2DPoint) {
                        // Marker for 3D Point ROI
                        g.setColor(Color.BLACK);
                        g.drawOval(sx - 6, sy - 6, 12, 12);
                        g.setColor(roiColor);
                        g.drawOval(sx - 5, sy - 5, 10, 10);
                        g.fillOval(sx - 2, sy - 2, 4, 4);
                    } else {
                        // Double ring marker for measurement nodes (Horos-style)
                        g.setColor(Color.BLACK);
                        g.drawOval(sx - 4, sy - 4, 8, 8);
                        g.setColor(roiColor);
                        g.fillOval(sx - 3, sy - 3, 6, 6);
                        g.setColor(Color.WHITE);
                        g.drawOval(sx - 2, sy - 2, 4, 4);
                    }

                    if (prevP != null && roi.getType() == DCMView.ToolMode.tMesure) {
                        // Calculate distance
                        double spacing_x = (pixList != null && !pixList.isEmpty()) ? pixList.get(0).getPixelSpacingX()
                                : spacingX;
                        double spacing_y = (pixList != null && !pixList.isEmpty()) ? pixList.get(0).getPixelSpacingY()
                                : spacingY;
                        double dist = com.oculus.jj.model.Measurement3D.calculateDistance(
                                prevV.x, prevV.y, prevV.z,
                                vpt.x, vpt.y, vpt.z,
                                spacing_x,
                                spacing_y,
                                spacingZ);

                        String unit = com.oculus.jj.config.AppPreferences.getMeasureUnit();
                        if ("cm".equals(unit))
                            dist /= 10.0;

                        String label = String.format("%.2f %s", dist, unit);

                        // Compute label position
                        int mx = (prevP.x + sx) / 2;
                        int my = (prevP.y + sy) / 2;

                        FontMetrics fm = g.getFontMetrics();
                        int tw = fm.stringWidth(label);
                        int th = fm.getHeight();
                        int padX = 6;
                        int padY = 4;
                        int bx = mx - tw / 2 - padX;
                        int by = my - th / 2 - padY;
                        int bw = tw + padX * 2;
                        int bh = th + padY * 2;

                        // Rounded translucent background box
                        g.setColor(new Color(0, 0, 0, 160));
                        g.fillRoundRect(bx, by, bw, bh, 8, 8);
                        g.setColor(new Color(255, 255, 255, 60));
                        g.setStroke(new BasicStroke(1f));
                        g.drawRoundRect(bx, by, bw, bh, 8, 8);

                        // Contrast text
                        g.setColor(Color.WHITE);
                        g.drawString(label, mx - tw / 2, my + th / 2 - fm.getDescent() - 1);
                    }

                    prevP = new Point(sx, sy);
                    prevV = vpt;
                }
            }
        }

        // Draw Scissor path if active and has points
        if (currentTool == Tool3D.SCISSOR && !scissorPoints.isEmpty()) {
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < scissorPoints.size(); i++) {
                Point p1 = scissorPoints.get(i - 1);
                Point p2 = scissorPoints.get(i);
                g.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
            if (scissorPoints.size() >= 3) {
                // Draw closing line
                Point pStart = scissorPoints.get(0);
                Point pEnd = scissorPoints.get(scissorPoints.size() - 1);
                g.drawLine(pEnd.x, pEnd.y, pStart.x, pStart.y);
            }

            g.setColor(new Color(173, 255, 47)); // HOROS-style yellow-green lasso
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < scissorPoints.size(); i++) {
                Point p1 = scissorPoints.get(i - 1);
                Point p2 = scissorPoints.get(i);
                g.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
            if (scissorPoints.size() >= 3) {
                Point pStart = scissorPoints.get(0);
                Point pEnd = scissorPoints.get(scissorPoints.size() - 1);
                g.drawLine(pEnd.x, pEnd.y, pStart.x, pStart.y);
            }

            // Draw starting point handles or nodes
            for (Point p : scissorPoints) {
                g.setColor(Color.BLACK);
                g.fillOval(p.x - 2, p.y - 2, 4, 4);
                g.setColor(Color.WHITE);
                g.fillOval(p.x - 1, p.y - 1, 2, 2);
            }
        }

        g.setFont(originalFont);
    }

    private static class HitResult {
        com.oculus.jj.model.ROI roi;
        int nodeIndex;

        HitResult(com.oculus.jj.model.ROI roi, int nodeIndex) {
            this.roi = roi;
            this.nodeIndex = nodeIndex;
        }
    }

    private HitResult findHitNode(int mouseX, int mouseY) {
        if (roiList.isEmpty())
            return null;

        int w = glViewer != null ? glViewer.getWidth() : 0;
        int h = glViewer != null ? glViewer.getHeight() : 0;
        if (w <= 0 || h <= 0)
            return null;

        double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
        double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);
        double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;
        double physX = dimX * spacingX;
        double physY = dimY * spacingY;
        double physZ = dimZ * spacingZ;
        double maxPhys = Math.max(physX, Math.max(physY, physZ));
        double baseScale = Math.min(w, h) / maxPhys * 0.95;
        double currentScale = baseScale * zoom;

        double ox = glViewer != null ? glViewer.getOffsetX() * maxPhys : 0.0;
        double oy = glViewer != null ? glViewer.getOffsetY() * maxPhys : 0.0;

        double hitTolerance = 8.0; // pixels

        for (com.oculus.jj.model.ROI roi : roiList) {
            List<com.oculus.jj.model.ROI.Point3D> points = roi.getPoints3D();
            if (points == null)
                continue;

            for (int i = 0; i < points.size(); i++) {
                com.oculus.jj.model.ROI.Point3D vpt = points.get(i);
                double tx = vpt.x - cx;
                double ty = vpt.y - cy;
                double tz = vpt.z - cz;
                double ry_view = ty * cosX + tz * sinX;
                double tz0 = tz * cosX - ty * sinX;
                double rx_view = tx * cosY - tz0 * sinY;

                int sx = (int) Math.round((rx_view + ox) * currentScale + w / 2.0);
                int sy = (int) Math.round((ry_view + oy) * currentScale + h / 2.0);

                double dist = Math.hypot(mouseX - sx, mouseY - sy);
                if (dist <= hitTolerance) {
                    return new HitResult(roi, i);
                }
            }
        }
        return null;
    }

    private double getRoiStartDepth(com.oculus.jj.model.ROI roi) {
        List<com.oculus.jj.model.ROI.Point3D> points = roi.getPoints3D();
        if (points == null || points.isEmpty())
            return 0.0;
        com.oculus.jj.model.ROI.Point3D vpt = points.get(0);

        double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
        double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);
        double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;

        double tx = vpt.x - cx;
        double ty = vpt.y - cy;
        double tz = vpt.z - cz;
        double tz0 = tz * cosX - ty * sinX;
        double rz_view = tz0 * cosY + tx * sinY;
        return rz_view;
    }

    private com.oculus.jj.model.ROI.Point3D getVoxelAtDepth(int sx, int sy, double vz_offset) {
        int w = glViewer != null ? glViewer.getWidth() : 0;
        int h = glViewer != null ? glViewer.getHeight() : 0;
        if (w <= 0 || h <= 0)
            return null;

        double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
        double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);
        double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;
        double physX = dimX * spacingX;
        double physY = dimY * spacingY;
        double physZ = dimZ * spacingZ;
        double maxPhys = Math.max(physX, Math.max(physY, physZ));
        double baseScale = Math.min(w, h) / maxPhys * 0.95;
        double currentScale = baseScale * zoom;

        double ox = glViewer != null ? glViewer.getOffsetX() * maxPhys : 0.0;
        double oy = glViewer != null ? glViewer.getOffsetY() * maxPhys : 0.0;

        double vx = (sx - w / 2.0) / currentScale - ox;
        double vy = (sy - h / 2.0) / currentScale - oy;

        double tx = vx * cosY + vz_offset * sinY;
        double tz0 = -vx * sinY + vz_offset * cosY;
        double ty = vy * cosX - tz0 * sinX;
        double tz = vy * sinX + tz0 * cosX;

        int ix = (int) Math.round(tx + cx);
        int iy = (int) Math.round(ty + cy);
        int iz = (int) Math.round(tz + cz);

        if (ix < 0)
            ix = 0;
        if (ix >= dimX)
            ix = dimX - 1;
        if (iy < 0)
            iy = 0;
        if (iy >= dimY)
            iy = dimY - 1;
        if (iz < 0)
            iz = 0;
        if (iz >= dimZ)
            iz = dimZ - 1;

        return new com.oculus.jj.model.ROI.Point3D(ix, iy, iz);
    }

    private float getOpacity(int val, float low, float factor) {
        float alpha = (val - low) * factor / 255.0f;
        if (alpha < 0)
            alpha = 0;
        if (alpha > 1.0f)
            alpha = 1.0f;
        return (float) Math.pow(alpha, 2.0);
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
        panel.add(new JLabel(LanguageManager.get("vr.iso.threshold", "Surface 1 Threshold (HU):")));
        JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(300, -1024, 3072, 50));
        panel.add(thresholdSpinner);

        panel.add(new JLabel(LanguageManager.get("vr.iso.resolution", "Resolution (0.1-1.0):")));
        JSpinner resSpinner = new JSpinner(new SpinnerNumberModel(0.5, 0.1, 1.0, 0.1));
        panel.add(resSpinner);

        panel.add(new JLabel(LanguageManager.get("vr.iso.smoothing", "Smoothing:")));
        JCheckBox smoothCheck = new JCheckBox("", true);
        panel.add(smoothCheck);

        int result = JOptionPane.showConfirmDialog(null, panel, LanguageManager.get("vr.iso.title", "3D Surface Rendering Settings"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            SwingUtilities.invokeLater(() -> {
                Viewer3DController v3d = new Viewer3DController(viewer, "ISO");
                v3d.isoThreshold = (int) thresholdSpinner.getValue();
                v3d.setVisible(true);
            });
        }
    }

    public void updateInfoLabel(int mouseX, int mouseY) {
        if (infoLabel == null)
            return;
        int w = glViewer != null ? glViewer.getWidth() : 0;
        int h = glViewer != null ? glViewer.getHeight() : 0;
        boolean isPersp = glViewer != null && glViewer.isPerspective();

        double physX = dimX * spacingX;
        double physY = dimY * spacingY;
        double physZ = dimZ * spacingZ;
        double maxPhys = Math.max(physX, Math.max(physY, physZ));
        if (maxPhys <= 0.0001)
            maxPhys = 1.0;
        double baseScale = Math.min(w, h) / maxPhys * 0.95;
        double currentScale = baseScale * zoom;
        if (currentScale <= 0.0001)
            currentScale = 0.0001;
        double parallelScale = (h / 2.0) / currentScale;
        double spX = spacingX > 0.0001 ? spacingX : 1.0;
        double scaleFactor = 100.0 / (spX * parallelScale);

        com.oculus.jj.model.ROI.Point3D voxel = (mouseX >= 0 && mouseY >= 0) ? getVoxelUnderMouse(mouseX, mouseY)
                : null;
        if (voxel != null && volume != null) {
            int ix = (int) voxel.x;
            int iy = (int) voxel.y;
            int iz = (int) voxel.z;

            int val = volume[iz * dimY * dimX + iy * dimX + ix];

            double px = (ix - dimX / 2.0) * spacingX;
            double py = (iy - dimY / 2.0) * spacingY;
            double pz = (iz - dimZ / 2.0) * spacingZ;

            String label;
            if (isPersp) {
                label = String.format(
                        "View Size: %d x %d   Pixel: %d HU   X: %d px  Y: %d px   X: %.1f mm  Y: %.1f mm  Z: %.1f mm",
                        w, h, val, ix, iy, px, py, pz);
            } else {
                label = String.format(
                        "View Size: %d x %d   Pixel: %d HU   X: %d px  Y: %d px   X: %.1f mm  Y: %.1f mm  Z: %.1f mm   SCALE: %.3f %%",
                        w, h, val, ix, iy, px, py, pz, scaleFactor);
            }
            infoLabel.setText(label);
        } else {
            String label;
            if (isPersp) {
                label = String.format("View Size: %d x %d", w, h);
            } else {
                label = String.format("View Size: %d x %d   SCALE: %.3f %%", w, h, scaleFactor);
            }
            infoLabel.setText(label);
        }
    }

    private void removeBoneAtVoxel(com.oculus.jj.model.ROI.Point3D pt) {
        if (volume == null)
            return;

        int sx = (int) pt.x;
        int sy = (int) pt.y;
        int sz = (int) pt.z;
        if (sx < 0 || sx >= dimX || sy < 0 || sy >= dimY || sz < 0 || sz >= dimZ)
            return;

        int seedIdx = sz * dimY * dimX + sy * dimX + sx;
        short seedVal = volume[seedIdx];
        if (seedVal < 250)
            return; // 骨ではない（閾値250 HU）

        if (volumeBackup == null) {
            volumeBackup = new short[volume.length];
            System.arraycopy(volume, 0, volumeBackup, 0, volume.length);
        }

        // Fast array-based primitive queue for 3D Flood Fill (Region Growing)
        int[] q = new int[dimX * dimY * dimZ];
        boolean[] visited = new boolean[dimX * dimY * dimZ];
        int head = 0;
        int tail = 0;

        q[tail++] = seedIdx;
        visited[seedIdx] = true;

        int[] dx = { 1, -1, 0, 0, 0, 0 };
        int[] dy = { 0, 0, 1, -1, 0, 0 };
        int[] dz = { 0, 0, 0, 0, 1, -1 };

        int count = 0;
        while (head < tail) {
            int idx = q[head++];
            int z = idx / (dimY * dimX);
            int rem = idx % (dimY * dimX);
            int y = rem / dimX;
            int x = rem % dimX;

            volume[idx] = -1024; // 空気に置換して消去
            update2DData(z, y, x, (short) -1024); // 2D連動
            count++;

            for (int i = 0; i < 6; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                int nz = z + dz[i];

                if (nx >= 0 && nx < dimX && ny >= 0 && ny < dimY && nz >= 0 && nz < dimZ) {
                    int nIdx = nz * dimY * dimX + ny * dimX + nx;
                    if (!visited[nIdx] && volumeBackup[nIdx] >= 250) {
                        visited[nIdx] = true;
                        q[tail++] = nIdx;
                    }
                }
            }
        }

        // System.out.println("Bone Removal: Removed " + count + " connected bone
        // voxels.");

        if (glViewer != null) {
            glViewer.setVolumeData(volume, null, dimX, dimY, dimZ);
            glViewer.triggerVolumeReupload();
        }
        if (cpuRenderer != null) {
            cpuRenderer.setVolumeData(volume, dimX, dimY, dimZ);
        }

        // HOROS-20240407連動: 2Dビューも更新
        if (viewer2D != null && viewer2D.getImageView() != null) {
            viewer2D.getImageView().repaint();
        }

        repaint();
    }

    private void applySelectedFilter(String filterName) {
        if (filterName == null || filterName.startsWith("Apply a filter")) {
            applyConvolution2D(filterName, null, 0);
            return;
        }

        float[] kernel = null;
        int size = 0;

        switch (filterName) {
            case "Basic Smooth 5x5":
                size = 5;
                kernel = new float[] {
                        1, 1, 1, 1, 1,
                        1, 4, 4, 4, 1,
                        1, 4, 12, 4, 1,
                        1, 4, 4, 4, 1,
                        1, 1, 1, 1, 1
                };
                break;
            case "Blur 3x3":
                size = 3;
                kernel = new float[] {
                        1, 2, 1,
                        2, 4, 2,
                        1, 2, 1
                };
                break;
            case "Blur 5x5":
                size = 5;
                kernel = new float[] {
                        1, 1, 2, 1, 1,
                        1, 2, 3, 2, 1,
                        2, 3, 4, 3, 2,
                        1, 2, 3, 2, 1,
                        1, 1, 2, 1, 1
                };
                break;
            case "Bone Filter 3x3":
                size = 3;
                kernel = new float[] {
                        -1, -1, -1,
                        -1, 9, -1,
                        -1, -1, -1
                };
                break;
            case "Edge 3x3":
                size = 3;
                kernel = new float[] {
                        1, -2, 1,
                        -2, 4, -2,
                        1, -2, 1
                };
                break;
            case "Emboss":
                size = 3;
                kernel = new float[] {
                        -1, 0, 0,
                        0, 0, 0,
                        0, 0, 1
                };
                break;
            case "Emboss diagonal":
                size = 3;
                kernel = new float[] {
                        0, 1, 0,
                        -1, 0, 1,
                        0, -1, 0
                };
                break;
            case "Emboss heavy":
                size = 3;
                kernel = new float[] {
                        -1, -1, 0,
                        -1, 0, 1,
                        0, 1, 1
                };
                break;
            case "Emboss north":
                size = 3;
                kernel = new float[] {
                        1, 2, 1,
                        0, 0, 0,
                        -1, -2, -1
                };
                break;
            case "Emboss west":
                size = 3;
                kernel = new float[] {
                        1, 0, -1,
                        2, 0, -2,
                        1, 0, -1
                };
                break;
            case "Excessive edges":
                size = 3;
                kernel = new float[] {
                        1, 1, 1,
                        1, -7, 1,
                        1, 1, 1
                };
                break;
            case "Gaussian blur":
                size = 5;
                kernel = new float[] {
                        1, 1, 2, 1, 1,
                        1, 2, 4, 2, 1,
                        2, 4, 8, 4, 2,
                        1, 2, 4, 2, 1,
                        1, 1, 2, 1, 1
                };
                break;
            case "Hat":
                size = 5;
                kernel = new float[] {
                        0, 0, -1, 0, 0,
                        0, -1, -2, -1, 0,
                        -1, -2, 16, -2, -1,
                        0, -1, -2, -1, 0,
                        0, 0, -1, 0, 0
                };
                break;
            case "Highpass 5x5":
                size = 5;
                kernel = new float[] {
                        0, -1, -1, -1, 0,
                        -1, 2, -4, 2, -1,
                        -1, -4, 13, -4, -1,
                        -1, 2, -4, 2, -1,
                        0, -1, -1, -1, 0
                };
                break;
            case "Inverted blur":
                size = 5;
                kernel = new float[] {
                        3, 3, 2, 3, 3,
                        3, 2, 1, 2, 3,
                        2, 1, 0, 1, 2,
                        3, 2, 1, 2, 3,
                        3, 3, 2, 3, 3
                };
                break;
            case "Laplacian":
                size = 5;
                kernel = new float[] {
                        -1, -1, -1, -1, -1,
                        -1, -1, -1, -1, -1,
                        -1, -1, 24, -1, -1,
                        -1, -1, -1, -1, -1,
                        -1, -1, -1, -1, -1
                };
                break;
            case "Laplacian 4":
                size = 3;
                kernel = new float[] {
                        0, -1, 0,
                        -1, 4, -1,
                        0, -1, 0
                };
                break;
            case "Laplacian 8":
                size = 3;
                kernel = new float[] {
                        -1, -1, -1,
                        -1, 8, -1,
                        -1, -1, -1
                };
                break;
            case "Lowpass":
                size = 3;
                kernel = new float[] {
                        1, 1, 1,
                        1, 1, 1,
                        1, 1, 1
                };
                break;
            case "Negative blur":
                size = 5;
                kernel = new float[] {
                        0, 0, -1, 0, 0,
                        0, -1, -2, -1, 0,
                        -1, -2, -3, -2, -1,
                        0, -1, -2, -1, 0,
                        0, 0, -1, 0, 0
                };
                break;
            case "Sharpen 3x3":
                size = 3;
                kernel = new float[] {
                        -1, 0, -1,
                        0, 7, 0,
                        -1, 0, -1
                };
                break;
            case "Sharpen 5x5":
                size = 5;
                kernel = new float[] {
                        -1, -1, -1, -1, -1,
                        -1, 2, 2, 2, -1,
                        -1, 2, 8, 2, -1,
                        -1, 2, 2, 2, -1,
                        -1, -1, -1, -1, -1
                };
                break;
        }

        if (kernel != null) {
            applyConvolution2D(filterName, kernel, size);
        }
    }

    private void applyConvolution2D(String name, float[] kernel, int size) {
        if (volume == null)
            return;

        if (volumeBackup == null) {
            volumeBackup = new short[volume.length];
            System.arraycopy(volume, 0, volumeBackup, 0, volume.length);
        }

        if (name == null || name.startsWith("Apply a filter")) {
            System.arraycopy(volumeBackup, 0, volume, 0, volume.length);
            if (glViewer != null) {
                glViewer.setVolumeData(volume, null, dimX, dimY, dimZ);
                glViewer.triggerVolumeReupload();
            }
            if (cpuRenderer != null) {
                cpuRenderer.setVolumeData(volume, dimX, dimY, dimZ);
            }
            repaint();
            return;
        }

        // Compute normalization factor (sum of elements)
        float norm = 0;
        for (float v : kernel) {
            norm += v;
        }
        if (norm != 0) {
            for (int i = 0; i < kernel.length; i++) {
                kernel[i] /= norm;
            }
        }

        int half = size / 2;
        short[] src = volumeBackup; // Always filter starting from clean backup data
        short[] dest = volume;

        // Apply 2D convolution slice-by-slice along the Z-axis (axial planes)
        for (int z = 0; z < dimZ; z++) {
            int sliceOffset = z * dimY * dimX;
            for (int y = 0; y < dimY; y++) {
                for (int x = 0; x < dimX; x++) {
                    float sum = 0;
                    for (int ky = 0; ky < size; ky++) {
                        int py = y + ky - half;
                        if (py < 0)
                            py = 0;
                        else if (py >= dimY)
                            py = dimY - 1;

                        int pyOffset = sliceOffset + py * dimX;
                        int kRowOffset = ky * size;

                        for (int kx = 0; kx < size; kx++) {
                            int px = x + kx - half;
                            if (px < 0)
                                px = 0;
                            else if (px >= dimX)
                                px = dimX - 1;

                            float kVal = kernel[kRowOffset + kx];
                            sum += src[pyOffset + px] * kVal;
                        }
                    }
                    dest[sliceOffset + y * dimX + x] = (short) Math.round(sum);
                }
            }
        }

        if (glViewer != null) {
            glViewer.setVolumeData(volume, null, dimX, dimY, dimZ);
            glViewer.triggerVolumeReupload();
        }
        if (cpuRenderer != null) {
            cpuRenderer.setVolumeData(volume, dimX, dimY, dimZ);
        }
        repaint();
    }

    private void update2DData(int z, int y, int x, short huValue) {
        if (pixList != null && z >= 0 && z < pixList.size()) {
            DicomPix pix = pixList.get(z);
            if (pix != null) {
                short[] rawData = pix.getShortData();
                if (rawData != null) {
                    int offset = y * dimX + x;
                    if (offset >= 0 && offset < rawData.length) {
                        float s = pix.getRescaleSlope();
                        float inter = pix.getRescaleIntercept();
                        // (PIXEL * Slope) + Intercept = HU => PIXEL = (HU - Intercept) / Slope
                        rawData[offset] = (short) Math.round((huValue - inter) / s);
                    }
                }
            }
        }
    }

    private void performScissorCut(boolean clearInside) {
        int w = glViewer != null ? glViewer.getWidth() : 0;
        int h = glViewer != null ? glViewer.getHeight() : 0;
        if (w <= 0 || h <= 0 || scissorPoints.size() < 3 || volume == null) {
            scissorPoints.clear();
            repaint();
            return;
        }

        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

        try {
            // 1. Create closed lasso path
            java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
            Point p0 = scissorPoints.get(0);
            path.moveTo(p0.x, p0.y);
            for (int i = 1; i < scissorPoints.size(); i++) {
                Point p = scissorPoints.get(i);
                path.lineTo(p.x, p.y);
            }
            path.closePath();

            // 2. Pre-generate screen space mask for speed!
            boolean[] mask = new boolean[w * h];
            java.awt.geom.Rectangle2D bounds = path.getBounds2D();
            int minX = Math.max(0, (int) Math.floor(bounds.getMinX()));
            int maxX = Math.min(w - 1, (int) Math.ceil(bounds.getMaxX()));
            int minY = Math.max(0, (int) Math.floor(bounds.getMinY()));
            int maxY = Math.min(h - 1, (int) Math.ceil(bounds.getMaxY()));

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (path.contains(x, y)) {
                        mask[y * w + x] = true;
                    }
                }
            }

            // Ensure volumeBackup exists before editing
            if (volumeBackup == null) {
                volumeBackup = new short[volume.length];
                System.arraycopy(volume, 0, volumeBackup, 0, volume.length);
            }

            double sinX = Math.sin(rotateX), cosX = Math.cos(rotateX);
            double sinY = Math.sin(rotateY), cosY = Math.cos(rotateY);

            double physX = dimX * spacingX;
            double physY = dimY * spacingY;
            double physZ = dimZ * spacingZ;
            double maxPhys = Math.max(physX, Math.max(physY, physZ));

            // GPU の描画ロジックと数学的に完全に一致させる
            double currentScale = Math.min(w, h) * zoom;
            double ox = glViewer != null ? glViewer.getOffsetX() : 0.0;
            double oy = glViewer != null ? glViewer.getOffsetY() : 0.0;

            double cx = dimX / 2.0, cy = dimY / 2.0, cz = dimZ / 2.0;
            double w_half = w / 2.0;
            double h_half = h / 2.0;

            // Perform the cut
            for (int iz = 0; iz < dimZ; iz++) {
                double pz_local = (iz - cz) * spacingZ / maxPhys;
                int iz_offset = iz * dimY * dimX;

                for (int iy = 0; iy < dimY; iy++) {
                    double py_local = (iy - cy) * spacingY / maxPhys;
                    int iy_offset = iz_offset + iy * dimX;

                    // GPU と同じ回転行列の適用 (RX -> RY)
                    double ry_rx = py_local * cosX - pz_local * sinX;
                    double rz_rx = py_local * sinX + pz_local * cosX;

                    for (int ix = 0; ix < dimX; ix++) {
                        double px_local = (ix - cx) * spacingX / maxPhys;

                        double rx_ry = px_local * cosY + rz_rx * sinY;
                        double ry_ry = ry_rx;

                        // OpenGL(Y+上が正) と AWT Screen(Y+下が正) を反転させて同期
                        double sx = (rx_ry + ox) * currentScale + w_half;
                        double sy = h_half - (ry_ry + oy) * currentScale;

                        int sx_int = (int) Math.round(sx);
                        int sy_int = (int) Math.round(sy);

                        boolean inside = false;
                        if (sy_int >= 0 && sy_int < h && sx_int >= 0 && sx_int < w) {
                            inside = mask[sy_int * w + sx_int];
                        }

                        if (clearInside) {
                            if (inside) {
                                volume[iy_offset + ix] = -1024;
                                update2DData(iz, iy, ix, (short) -1024);
                            }
                        } else {
                            if (!inside) {
                                volume[iy_offset + ix] = -1024;
                                update2DData(iz, iy, ix, (short) -1024);
                            }
                        }
                    }
                }
            }

            // 3. Update Renderers
            if (glViewer != null) {
                glViewer.setVolumeData(volume, null, dimX, dimY, dimZ);
                glViewer.triggerVolumeReupload();
            }
            if (cpuRenderer != null) {
                cpuRenderer.setVolumeData(volume, dimX, dimY, dimZ);
            }

            // 4. Update 2D Viewer (HOROS-20240407連動)
            if (viewer2D != null && viewer2D.getImageView() != null) {
                viewer2D.getImageView().repaint();
            }
        } finally {
            setCursor(java.awt.Cursor.getDefaultCursor());
            scissorPoints.clear();
            repaint();
        }
    }

    @Override
    public void repaint() {
        super.repaint();
        if (glViewer != null) {
            glViewer.markOverlayDirty();
            glViewer.repaint();
        }
    }

    /** HOROS-20240407準拠: Endoscopy Viewer (未実装プレースホルダ) */
    public static void openEndoscopy(ViewerController viewer) {
        // System.out.println("[VR] Endoscopy Viewer - not yet implemented");
    }

    /** HOROS-20240407準拠: Structured Report Viewer (未実装プレースホルダ) */
    public static void openSR(ViewerController viewer) {
        // System.out.println("[VR] SR Viewer - not yet implemented");
    }
}