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

    private JDialog clippingDialog;
    private boolean showCropBox = false;
    private JLabel loadingLabel;
    private String loadingStatus = "Preparing Volume Data (Converting to HU)...";
    private boolean gpuInitialized = false;
    private boolean gpuContextUnsupported = false;
    private int glConfigIndex = 0;
    private volatile boolean gpuRecoveryInProgress = false;
    private volatile boolean gpuActivationInProgress = false;
    private int gpuActivationToken = 0;
    private Point lastMousePos;

    private final MouseAdapter glMouseHandler = new MouseAdapter() {
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
            }

            if (currentTool == Tool3D.POINT) {
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

            if (currentTool == Tool3D.BONE_REMOVAL) {
                com.oculus.jj.model.ROI.Point3D pt = getVoxelUnderMouse(e.getX(), e.getY());
                if (pt != null) {
                    removeBoneAtVoxel(pt);
                }
                return;
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            repaint();
        }

        private Tool3D getEffectiveTool(MouseEvent e) {
            boolean isRight = SwingUtilities.isRightMouseButton(e);
            boolean isMiddle = SwingUtilities.isMiddleMouseButton(e) || (e.getModifiersEx() & MouseEvent.BUTTON2_DOWN_MASK) != 0;
            boolean isShift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;
            boolean isCtrl = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;

            // 1. Modifier key combinations (highest priority, matches standard Horos DICOM operations)
            if (isMiddle) {
                return Tool3D.PAN;  // Middle drag -> Pan
            }
            if (isRight) {
                return Tool3D.WLWW; // Right drag -> WLWW (Adjust window level/width)
            }
            if (isShift || isCtrl) {
                return Tool3D.ZOOM; // Shift/Ctrl + Left drag -> Zoom
            }

            // 2. Map based on DCMView.currentTool (active main toolbar button)
            if (DCMView.currentTool != null) {
                switch (DCMView.currentTool) {
                    case tTranslate:
                        return Tool3D.PAN;
                    case tZoom:
                        return Tool3D.ZOOM;
                    case tWLWW:
                        return Tool3D.WLWW;
                    case t3DRotate:
                    case tRotate:
                    case t3DRotateTool:
                    case t3DRotateTool2:
                    case t3DRotateTool3:
                    case t3DRotateTool4:
                        return Tool3D.ROTATE;
                    case tMesure:
                        return Tool3D.MEASURE;
                    case t2DPoint:
                    case t3Dpoint:
                    case t3DPointTool:
                    case t3DPointTool2:
                    case t3DPointTool3:
                    case t3DPointTool4:
                        return Tool3D.POINT;
                }
            }

            // 3. Fallback to local shortcut/currentTool
            return currentTool;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (lastMousePos == null) {
                return;
            }

            int dx = e.getX() - lastMousePos.x;
            int dy = e.getY() - lastMousePos.y;

            Tool3D tool = getEffectiveTool(e);

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
                // Pure view-space flat translation (world X & Y since translation is left of rotation)
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
        System.out.println("Cleaning up 3D Viewer resources...");
        stopRenderLoop();
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
            System.err.println("DEBUG: GPU mode unavailable for all GL configs. Staying in CPU mode.");
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
                    System.out.println("DEBUG: Gradients computed (" + grad.length + " bytes).");

                    SwingUtilities.invokeLater(() -> loadingLabel
                            .setText("Uploading Volume to GPU (Step 1/2)... Please wait 2-3 seconds..."));
                    System.out.println("DEBUG: Starting glViewer.setVolumeData...");
                    glViewer.setVolumeData(volume, grad, dimX, dimY, dimZ);
                    System.out.println("DEBUG: glViewer.setVolumeData finished.");

                    Thread.sleep(400); // EDTに "Uploading Volume..." 表示を描画させる時間を確保

                    SwingUtilities
                            .invokeLater(() -> setLoadingStatus("Completing GPU Initialization (Step 2/2)..."));
                    glViewer.setTransferFunction(currentTF);
                    glViewer.setWindowLevel(windowLevel, windowWidth);
                    glViewer.setClipping(clipMinX, clipMaxX, clipMinY, clipMaxY, clipMinZ, clipMaxZ);
                    glViewer.setVoxelSpacing((float) spacingX, (float) spacingY, (float) spacingZ);
                    glViewer.setRotation((float) rotateX, (float) rotateY);
                    glViewer.setZoom((float) zoom);
                    System.out.println("DEBUG: GPU parameters set.");

                    Thread.sleep(400); // EDTに "Completing GPU..." 表示を描画させる時間を確保

                    SwingUtilities.invokeLater(() -> {
                        try {
                            glViewer.setEngine(GLViewer3D.ENGINE_GPU);
                            this.canvas = glViewer; // 描画ループ対象をセット
                            setMode(this.mode);
                            System.out.println("DEBUG: activateGPUMode EDT: about to showView(VIEW)");
                            showView("VIEW");
                            startRenderLoop();
                            System.out.println("3D Rendering Ready. Mode: GPU");
                        } catch (Throwable t) {
                            System.err.println("DEBUG: EXCEPTION in activateGPUMode EDT block:");
                            t.printStackTrace();
                            showView("VIEW");
                            startRenderLoop();
                        } finally {
                            gpuActivationInProgress = false;
                        }
                    });
                } catch (Throwable t) {
                    System.err.println("DEBUG: Error in activateGPUMode thread:");
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
            System.err.println("DEBUG: GPU activation timeout at step 2/2. Triggering GL fallback retry.");
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
                System.out.println("DEBUG: startAsyncGPUInitialization thread started.");
                List<List<DicomPix>> allPix = viewer2D.getPixList();
                if (allPix == null || allPix.isEmpty()) {
                    System.err.println("DEBUG: allPix is NULL or empty!");
                    return;
                }
                this.pixList = new ArrayList<>(allPix.get(0));
                System.out.println("DEBUG: pixList size = " + pixList.size());
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
                System.out.println("DEBUG: loadSlices started. dimZ=" + dimZ + ", dimY=" + dimY + ", dimX=" + dimX);
                System.out.println("Starting HU conversion (Sequential/Safe Mode) for " + dimZ + " slices...");

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
                        // コピー済みスライスを即座に解放
                        pixList.set(z, null);

                        // 50枚ごとにコンソールに進捗を出す
                        if (z % 50 == 0) {
                            System.out.println("DEBUG: HU Converting... " + z + "/" + dimZ);
                        }
                        // 100枚ごとにGCを強制
                        if (z % 100 == 0) {
                            System.gc();
                        }
                    } catch (Throwable t) {
                        System.err.println("Error at slice " + z + ": " + t.getMessage());
                    }
                }

                System.out.println("HU conversion finished. Finalizing...");
                // データの詳細統計チェック
                long nonZeroCount = 0;
                int maxV = -3000, minV = 3000;
                long sumV = 0;
                for (int i = 0; i < Math.min(volume.length, 1000000); i++) {
                    short val = volume[i];
                    if (val != 0)
                        nonZeroCount++;
                    if (val > maxV)
                        maxV = val;
                    if (val < minV)
                        minV = val;
                    sumV += val;
                }
                System.out.println("DEBUG: Volume Stats (first 1M samples):");
                System.out.println("  Non-zero: " + nonZeroCount);
                System.out.println("  Max: " + maxV + ", Min: " + minV + ", Avg: " + (sumV / 1000000));
                pixList = null;

                // 3Dレンダリング用ウィンドウは広範囲を維持する。
                // DICOMの2D表示用窓(例: 軟部組織 WL=50/WW=300)は骨(HU>200)を完全除外するため
                // VRモードで真っ黒になる。wl=300/ww=2000を使用して骨・軟組織を両方表示する。
                if (viewer2D != null && viewer2D.getImageView() != null
                        && viewer2D.getImageView().getCurDCM() != null) {
                    float dicom2DWL = viewer2D.getImageView().getCurDCM().getWindowLevel();
                    float dicom2DWW = viewer2D.getImageView().getCurDCM().getWindowWidth();
                    System.out
                            .println("DEBUG: DICOM 2D window (not used for 3D): WL=" + dicom2DWL + " WW=" + dicom2DWW);
                } else if (first != null) {
                    System.out.println("DEBUG: DICOM first-slice window (not used for 3D): WL="
                            + first.getWindowLevel() + " WW=" + first.getWindowWidth());
                }
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
                    System.err.println("DEBUG: OpenGL pixel format error detected. Trying next GL config.");
                    ex.printStackTrace();
                    gpuActivationInProgress = false;
                    SwingUtilities.invokeLater(this::handleGpuContextFailure);
                    return;
                }
                consecutiveErrors[0]++;
                System.err.println("DEBUG: Render error #" + consecutiveErrors[0] + ": " + ex);
                ex.printStackTrace();
                if (consecutiveErrors[0] >= 10) {
                    // 連続10回失敗した場合のみ停止 (一時的なエラーで永久停止しないよう)
                    stopRenderLoop();
                    System.err.println("DEBUG: Render loop stopped after 10 consecutive errors.");
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
        System.out.println("DEBUG: setupGPURenderer called. engine=" + (isGpuEngine() ? "GPU" : "CPU"));
        new Thread(() -> {
            try {
                System.out.println("DEBUG: setupGPURenderer thread started.");
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
                        System.out.println("DEBUG: setupGPURenderer EDT: about to showView(VIEW)");
                        showView("VIEW");
                        startRenderLoop(); // 定期再描画を回す
                        System.out.println("3D Rendering Ready. Mode: CPU");
                    } catch (Throwable t) {
                        System.err.println("DEBUG: EXCEPTION in setupGPURenderer EDT block:");
                        t.printStackTrace();
                        showView("VIEW"); // 例外でも VIEW に切り替えてブラックスクリーンを表示
                        startRenderLoop();
                    }
                });

                System.out.println("DEBUG: CPU renderer initialized.");
            } catch (Throwable t) {
                System.err.println("DEBUG: Error in setupGPURenderer thread:");
                t.printStackTrace();
                SwingUtilities.invokeLater(() -> setLoadingStatus("Initialization Error: " + t.getMessage()));
            }
        }).start();
    }

    private void showView(String viewName) {
        System.out.println("DEBUG: showView(" + viewName + ") called. layout="
                + (mainContainer != null ? mainContainer.getLayout().getClass().getSimpleName() : "null")
                + " glViewer.isShowing=" + (glViewer != null ? glViewer.isShowing() : "null")
                + " glViewer.isVisible=" + (glViewer != null ? glViewer.isVisible() : "null"));
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
        System.out.println("DEBUG: showView(" + viewName + ") done."
                + " glViewer.isShowing=" + (glViewer != null ? glViewer.isShowing() : "null")
                + " glViewer.isVisible=" + (glViewer != null ? glViewer.isVisible() : "null"));
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
        // ---- HOROS-20240407 準拠 ツールバー再構成 ----
        vrToolbar = new JToolBar();
        vrToolbar.setFloatable(false);
        vrToolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));

        // 1. Mouse button function
        JPanel contentMouse = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        JPanel groupMouse = createToolbarGroup("Mouse button function", contentMouse);
        ButtonGroup bgTools = new ButtonGroup();
        
        // 左クリック時の動作を切り替えるボタンのみに絞る
        String[] toolLabels = { "↻ Rotate", "🟢 Point", "⭕ Oval", "📏 Meas", "✂️ Scissor", "💀 Bone" };
        Tool3D[] toolValues = { Tool3D.ROTATE, Tool3D.POINT, Tool3D.ROI, Tool3D.MEASURE, Tool3D.SCISSOR, Tool3D.BONE_REMOVAL };
        
        for (int i = 0; i < toolLabels.length; i++) {
            final Tool3D t = toolValues[i];
            JToggleButton btn = new JToggleButton(toolLabels[i]);
            btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            btn.setMargin(new Insets(2, 4, 2, 4));
            btn.setFocusable(false);
            btn.addActionListener(e -> {
                this.currentTool = t;
                if (t == Tool3D.CLIPPING) {
                    showClippingDialog();
                }
                repaint();
            });
            bgTools.add(btn);
            contentMouse.add(btn);
            if (t == Tool3D.ROTATE) {
                btn.setSelected(true);
            }
        }
        
        vrToolbar.add(groupMouse);

        // 2. WL/WW & CLUT & Opacity
        JPanel contentWLWW = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JPanel groupWLWW = createToolbarGroup("WL/WW & CLUT & Opacity", contentWLWW);
        
        JPanel gridLeft = new JPanel(new GridLayout(3, 2, 2, 2));
        gridLeft.setOpaque(false);
        
        JLabel lblWL = new JLabel("WL/WW:", JLabel.RIGHT);
        lblWL.setFont(new Font("SansSerif", Font.PLAIN, 10));
        JComboBox<String> cbWL = new JComboBox<>(new String[] { "Other", "Default", "Bone", "Soft Tissue", "Lung", "Vascular" });
        cbWL.setFont(new Font("SansSerif", Font.PLAIN, 10));
        cbWL.setFocusable(false);
        cbWL.setSelectedItem("Default");
        cbWL.addActionListener(e -> {
            String item = (String) cbWL.getSelectedItem();
            if ("Default".equals(item)) {
                windowLevel = 1024;
                windowWidth = 4096;
            } else if ("Bone".equals(item)) {
                windowLevel = 300;
                windowWidth = 1500;
            } else if ("Soft Tissue".equals(item)) {
                windowLevel = 40;
                windowWidth = 400;
            } else if ("Lung".equals(item)) {
                windowLevel = -500;
                windowWidth = 1500;
            } else if ("Vascular".equals(item)) {
                windowLevel = 200;
                windowWidth = 1000;
            }
            if (glViewer != null) {
                glViewer.setWindowLevel(windowLevel, windowWidth);
            }
            repaint();
        });
        gridLeft.add(lblWL);
        gridLeft.add(cbWL);
        
        JLabel lblCLUT = new JLabel("CLUT:", JLabel.RIGHT);
        lblCLUT.setFont(new Font("SansSerif", Font.PLAIN, 10));
        JComboBox<String> cbCLUT = new JComboBox<>(new String[] { "No CLUT", "Hot Iron", "Rainbow", "VR Muscles & Bones" });
        cbCLUT.setFont(new Font("SansSerif", Font.PLAIN, 10));
        cbCLUT.setFocusable(false);
        cbCLUT.setSelectedItem("VR Muscles & Bones");
        cbCLUT.addActionListener(e -> {
            String clut = (String) cbCLUT.getSelectedItem();
            if ("VR Muscles & Bones".equals(clut)) {
                currentTF = TransferFunction.createVRMusclesBones();
            } else if ("Hot Iron".equals(clut)) {
                currentTF = TransferFunction.createSoftTissue();
            } else if ("Rainbow".equals(clut)) {
                currentTF = TransferFunction.createVascular();
            } else {
                currentTF = TransferFunction.createBone();
            }
            if (glViewer != null) {
                glViewer.setTransferFunction(currentTF);
            }
            repaint();
        });
        gridLeft.add(lblCLUT);
        gridLeft.add(cbCLUT);
        
        JLabel lblOpacity = new JLabel("Opacity:", JLabel.RIGHT);
        lblOpacity.setFont(new Font("SansSerif", Font.PLAIN, 10));
        JComboBox<String> cbOpacity = new JComboBox<>(new String[] { "Linear", "Logarithmic" });
        cbOpacity.setFont(new Font("SansSerif", Font.PLAIN, 10));
        cbOpacity.setFocusable(false);
        gridLeft.add(lblOpacity);
        gridLeft.add(cbOpacity);
        
        contentWLWW.add(gridLeft);
        
        JPanel pnlMid = new JPanel(new GridLayout(2, 1, 2, 2));
        pnlMid.setOpaque(false);
        JButton btn8 = new JButton("8-bit");
        btn8.setFont(new Font("SansSerif", Font.BOLD, 9));
        btn8.setMargin(new Insets(1, 4, 1, 4));
        btn8.setFocusable(false);
        JButton btn16 = new JButton("16-bit");
        btn16.setFont(new Font("SansSerif", Font.BOLD, 9));
        btn16.setMargin(new Insets(1, 4, 1, 4));
        btn16.setFocusable(false);
        pnlMid.add(btn8);
        pnlMid.add(btn16);
        contentWLWW.add(pnlMid);
        
        JButton btnPresets = new JButton("🎛️ Presets");
        btnPresets.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnPresets.setMargin(new Insets(4, 4, 4, 4));
        btnPresets.setFocusable(false);
        btnPresets.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            
            JMenuItem itemMB = new JMenuItem("VR Muscle & Bone (Creamy)");
            itemMB.addActionListener(ev -> {
                currentTF = TransferFunction.createVRMusclesBones();
                cbCLUT.setSelectedItem("VR Muscles & Bones");
                if (glViewer != null) glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemMB);
            
            JMenuItem itemBone = new JMenuItem("Bone Only");
            itemBone.addActionListener(ev -> {
                currentTF = TransferFunction.createBone();
                cbCLUT.setSelectedItem("No CLUT");
                if (glViewer != null) glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemBone);
            
            JMenuItem itemSoft = new JMenuItem("Soft Tissue (Hot Iron)");
            itemSoft.addActionListener(ev -> {
                currentTF = TransferFunction.createSoftTissue();
                cbCLUT.setSelectedItem("Hot Iron");
                if (glViewer != null) glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemSoft);
            
            JMenuItem itemLung = new JMenuItem("Lung (High Contrast)");
            itemLung.addActionListener(ev -> {
                currentTF = TransferFunction.createLung();
                cbCLUT.setSelectedItem("No CLUT");
                if (glViewer != null) glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemLung);
            
            JMenuItem itemVasc = new JMenuItem("Vascular / Angio");
            itemVasc.addActionListener(ev -> {
                currentTF = TransferFunction.createVascular();
                cbCLUT.setSelectedItem("Rainbow");
                if (glViewer != null) glViewer.setTransferFunction(currentTF);
                repaint();
            });
            menu.add(itemVasc);
            
            menu.show(btnPresets, 0, btnPresets.getHeight());
        });
        contentWLWW.add(btnPresets);
        vrToolbar.add(groupWLWW);

        // 3. Level of Detail & Engine & Crop & Orientation
        JPanel contentLOD = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JPanel groupLOD = createToolbarGroup("Level of Detail & Orientation", contentLOD);
        
        JPanel pnlSlider = new JPanel(new BorderLayout());
        pnlSlider.setOpaque(false);
        JLabel lblSliderName = new JLabel("LOD / Resolution", JLabel.CENTER);
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
        JLabel lblFine = new JLabel("Fine", JLabel.LEFT);
        lblFine.setFont(new Font("SansSerif", Font.PLAIN, 8));
        JLabel lblCoarse = new JLabel("Coarse", JLabel.RIGHT);
        lblCoarse.setFont(new Font("SansSerif", Font.PLAIN, 8));
        pnlSliderLabels.add(lblFine, BorderLayout.WEST);
        pnlSliderLabels.add(lblCoarse, BorderLayout.EAST);
        pnlSlider.add(pnlSliderLabels, BorderLayout.SOUTH);
        contentLOD.add(pnlSlider);
        
        JButton btnBest = new JButton("✨ Best");
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
        
        JToggleButton tglCrop = new JToggleButton("✂ Crop Box");
        tglCrop.setFont(new Font("SansSerif", Font.PLAIN, 10));
        tglCrop.setMargin(new Insets(4, 4, 4, 4));
        tglCrop.setFocusable(false);
        tglCrop.addActionListener(e -> {
            this.showCropBox = tglCrop.isSelected();
            repaint();
        });
        contentLOD.add(tglCrop);
        
        JButton btnOrient = new JButton("🧊 Orient");
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
        JPanel groupShading = createToolbarGroup("Shading", contentShading);
        
        JCheckBox chkShading = new JCheckBox("Shading", true);
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
        
        JButton btnEditShading = new JButton("Edit");
        btnEditShading.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnEditShading.setMargin(new Insets(2, 4, 2, 4));
        btnEditShading.setFocusable(false);
        btnEditShading.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Ambient: 0.20\nDiffuse: 0.90\nSpecular: 0.30\nShininess: 15.0", "Shading Parameters", JOptionPane.INFORMATION_MESSAGE);
        });
        contentShading.add(btnEditShading);
        contentShading.add(lblParams);
        vrToolbar.add(groupShading);

        // 5. Perspective & Filters
        JPanel contentPersp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JPanel groupPersp = createToolbarGroup("Perspective & Filters", contentPersp);
        
        JPanel pnlPerspRadios = new JPanel(new GridLayout(3, 1));
        pnlPerspRadios.setOpaque(false);
        JRadioButton rbParallel = new JRadioButton("Parallel", true);
        rbParallel.setFont(new Font("SansSerif", Font.PLAIN, 9));
        rbParallel.setOpaque(false);
        rbParallel.setFocusable(false);
        JRadioButton rbPersp = new JRadioButton("Perspective", false);
        rbPersp.setFont(new Font("SansSerif", Font.PLAIN, 9));
        rbPersp.setOpaque(false);
        rbPersp.setFocusable(false);
        JRadioButton rbEndo = new JRadioButton("Endoscopy", false);
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
        JLabel lblFilter = new JLabel("Convolution Filter:", JLabel.LEFT);
        lblFilter.setFont(new Font("SansSerif", Font.BOLD, 8));
        pnlFilter.add(lblFilter, BorderLayout.NORTH);
        
        JComboBox<String> cbFilter = new JComboBox<>(new String[] {
            "Apply a filter",
            "Basic Smooth 5x5",
            "Blur 3x3",
            "Blur 5x5",
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
        JPanel groupTools = createToolbarGroup("Export & Tools", contentTools);
        
        JButton btnClipping = new JButton("Clipping");
        btnClipping.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnClipping.setMargin(new Insets(2, 4, 2, 4));
        btnClipping.setFocusable(false);
        btnClipping.addActionListener(e -> showClippingDialog());
        contentTools.add(btnClipping);
        
        JButton btnReset = new JButton("Reset");
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
        
        String[] extraTools = {"Movie Export", "Orientations"};
        for(String label : extraTools) {
            JButton btn = new JButton(label);
            btn.setFont(new Font("SansSerif", Font.PLAIN, 10));
            btn.setMargin(new Insets(2, 4, 2, 4));
            btn.setFocusable(false);
            contentTools.add(btn);
        }
        
        JButton btnRevert = new JButton("Revert");
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
        
        String[] extraTools2 = {"DICOM File", "Fly Thru"};
        for(String label : extraTools2) {
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

        this.loadingLabel = new JLabel("Preparing Volume Data (Converting to HU)...", JLabel.CENTER);
        loadingLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        loadingLabel.setForeground(Color.WHITE);

        // CardLayout: Swing and Heavyweight AWTGLCanvas conflict on Windows.
        // We use BorderLayout to keep glViewer visible at all times, preventing native HWND destruction,
        // and draw the loading screen as a high-performance Java2D overlay.
        JPanel loadingPanel = new JPanel(new BorderLayout());
        loadingPanel.setBackground(Color.BLACK);
        loadingPanel.add(loadingLabel, BorderLayout.CENTER);

        mainContainer = new JPanel(new BorderLayout());
        mainContainer.setOpaque(false);
        mainContainer.add(glViewer, BorderLayout.CENTER);

        add(mainContainer, BorderLayout.CENTER);

        showView("LOADING"); // 初期表示: データロード中

        // キーボードショートカット (Horos準拠: ESCで閉じる, 各ツール切替)
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE:
                        dispose();
                        break;
                    case KeyEvent.VK_W:
                        currentTool = Tool3D.WLWW;
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
            clippingDialog = new JDialog(this, "3D Clipping", false);
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            content.setBackground(new Color(240, 240, 240));

            content.add(makeClipSlider("X-Min (%)", (int) (clipMinX * 100), v -> {
                clipMinX = v / 100f;
                applyClipping();
            }));
            content.add(makeClipSlider("X-Max (%)", (int) (clipMaxX * 100), v -> {
                clipMaxX = v / 100f;
                applyClipping();
            }));
            content.add(Box.createVerticalStrut(10));
            content.add(makeClipSlider("Y-Min (%)", (int) (clipMinY * 100), v -> {
                clipMinY = v / 100f;
                applyClipping();
            }));
            content.add(makeClipSlider("Y-Max (%)", (int) (clipMaxY * 100), v -> {
                clipMaxY = v / 100f;
                applyClipping();
            }));
            content.add(Box.createVerticalStrut(10));
            content.add(makeClipSlider("Z-Min (%)", (int) (clipMinZ * 100), v -> {
                clipMinZ = v / 100f;
                applyClipping();
            }));
            content.add(makeClipSlider("Z-Max (%)", (int) (clipMaxZ * 100), v -> {
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
                System.out.println("DEBUG: glViewer shown, size=" + viewer.getWidth() + "x" + viewer.getHeight());
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
                System.err.println("DEBUG: Retrying GPU initialization with GL config index=" + glConfigIndex);
                if (loadingLabel != null) {
                    setLoadingStatus("Retrying GPU context with compatible GL profile...");
                }
                showView("LOADING");
                recreateGLViewerForRetry();
                activateGPUMode();
            } else {
                gpuContextUnsupported = true;
                System.err.println("DEBUG: All GL configs failed. GPU mode unavailable.");
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
        // Mathematically correct transformation of camera screen-space UP and RIGHT vectors back to volume space
        // for the model rotation matrix: R = rotateY(ry) * rotateX(rx)

        // Camera UP vector in volume space: U_vol = (0, cos(rx), -sin(rx))
        double ux_vol = 0;
        double uy_vol = Math.cos(rx);
        double uz_vol = -Math.sin(rx);
        String top = dominantAxis(ux_vol, uy_vol, uz_vol);
        String bottom = opposite(top);

        // Camera RIGHT vector in volume space: R_vol = (cos(ry), -sin(ry) * sin(rx), sin(ry) * cos(rx))
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

        // If we are performing bone removal, search specifically for the first voxel >= 250 HU along the ray
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
        if (roiList.isEmpty())
            return;

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

        double ox = glViewer != null ? glViewer.getOffsetX() * maxPhys : 0.0;
        double oy = glViewer != null ? glViewer.getOffsetY() * maxPhys : 0.0;

        g.setColor(com.oculus.jj.config.AppPreferences.getROIColor());
        g.setStroke(new BasicStroke(2));

        for (com.oculus.jj.model.ROI roi : roiList) {
            List<com.oculus.jj.model.ROI.Point3D> points = roi.getPoints3D();
            if (points.isEmpty())
                continue;

            Point prevP = null;
            com.oculus.jj.model.ROI.Point3D prevV = null;

            for (com.oculus.jj.model.ROI.Point3D vpt : points) {
                double tx = vpt.x - cx;
                double ty = vpt.y - cy;
                double tz = vpt.z - cz;
                double ry_view = ty * cosX + tz * sinX;
                double tz0 = tz * cosX - ty * sinX;
                double rx_view = tx * cosY - tz0 * sinY;

                int sx = (int) Math.round((rx_view + ox) * currentScale + w / 2.0);
                int sy = (int) Math.round((ry_view + oy) * currentScale + h / 2.0);

                if (roi.getType() == DCMView.ToolMode.t2DPoint) {
                    g.drawOval(sx - 5, sy - 5, 10, 10);
                    g.fillOval(sx - 2, sy - 2, 4, 4);
                } else {
                    g.fillOval(sx - 3, sy - 3, 6, 6);
                }

                if (prevP != null && roi.getType() == DCMView.ToolMode.tMesure) {
                    g.drawLine(prevP.x, prevP.y, sx, sy);
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
                v3d.setVisible(true);
            });
        }
    }

    public void updateInfoLabel(int mouseX, int mouseY) {
        if (infoLabel == null) return;
        int w = glViewer != null ? glViewer.getWidth() : 0;
        int h = glViewer != null ? glViewer.getHeight() : 0;
        boolean isPersp = glViewer != null && glViewer.isPerspective();
        
        double physX = dimX * spacingX;
        double physY = dimY * spacingY;
        double physZ = dimZ * spacingZ;
        double maxPhys = Math.max(physX, Math.max(physY, physZ));
        if (maxPhys <= 0.0001) maxPhys = 1.0;
        double baseScale = Math.min(w, h) / maxPhys * 0.95;
        double currentScale = baseScale * zoom;
        if (currentScale <= 0.0001) currentScale = 0.0001;
        double parallelScale = (h / 2.0) / currentScale;
        double spX = spacingX > 0.0001 ? spacingX : 1.0;
        double scaleFactor = 100.0 / (spX * parallelScale);
        
        com.oculus.jj.model.ROI.Point3D voxel = (mouseX >= 0 && mouseY >= 0) ? getVoxelUnderMouse(mouseX, mouseY) : null;
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
                label = String.format("View Size: %d x %d   Pixel: %d HU   X: %d px  Y: %d px   X: %.1f mm  Y: %.1f mm",
                        w, h, val, ix, iy, px, py);
            } else {
                label = String.format("View Size: %d x %d   Pixel: %d HU   X: %d px  Y: %d px   X: %.1f mm  Y: %.1f mm   SCALE: %.3f %%",
                        w, h, val, ix, iy, px, py, scaleFactor);
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
        if (volume == null) return;
        
        int sx = (int) pt.x;
        int sy = (int) pt.y;
        int sz = (int) pt.z;
        if (sx < 0 || sx >= dimX || sy < 0 || sy >= dimY || sz < 0 || sz >= dimZ) return;
        
        int seedIdx = sz * dimY * dimX + sy * dimX + sx;
        short seedVal = volume[seedIdx];
        if (seedVal < 250) return; // 骨ではない（閾値250 HU）
        
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
        
        int[] dx = {1, -1, 0, 0, 0, 0};
        int[] dy = {0, 0, 1, -1, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};
        
        int count = 0;
        while(head < tail) {
            int idx = q[head++];
            volume[idx] = -1024; // 空気に置換して消去
            count++;
            
            int z = idx / (dimY * dimX);
            int rem = idx % (dimY * dimX);
            int y = rem / dimX;
            int x = rem % dimX;
            
            for(int i = 0; i < 6; i++) {
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
        
        System.out.println("Bone Removal: Removed " + count + " connected bone voxels.");
        
        if (glViewer != null) {
            glViewer.setVolumeData(volume, null, dimX, dimY, dimZ);
            glViewer.triggerVolumeReupload();
        }
        if (cpuRenderer != null) {
            cpuRenderer.setVolumeData(volume, dimX, dimY, dimZ);
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
                    -1,  9, -1,
                    -1, -1, -1
                };
                break;
            case "Edge 3x3":
                size = 3;
                kernel = new float[] {
                    1, -2,  1,
                    -2,  4, -2,
                    1, -2,  1
                };
                break;
            case "Emboss":
                size = 3;
                kernel = new float[] {
                    -1,  0,  0,
                    0,  0,  0,
                    0,  0,  1
                };
                break;
            case "Emboss diagonal":
                size = 3;
                kernel = new float[] {
                    0,  1,  0,
                    -1,  0,  1,
                    0, -1,  0
                };
                break;
            case "Emboss heavy":
                size = 3;
                kernel = new float[] {
                    -1, -1,  0,
                    -1,  0,  1,
                    0,  1,  1
                };
                break;
            case "Emboss north":
                size = 3;
                kernel = new float[] {
                    1,  2,  1,
                    0,  0,  0,
                    -1, -2, -1
                };
                break;
            case "Emboss west":
                size = 3;
                kernel = new float[] {
                    1,  0, -1,
                    2,  0, -2,
                    1,  0, -1
                };
                break;
            case "Excessive edges":
                size = 3;
                kernel = new float[] {
                    1,  1,  1,
                    1, -7,  1,
                    1,  1,  1
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
                    0,  0, -1,  0,  0,
                    0, -1, -2, -1,  0,
                    -1, -2, 16, -2, -1,
                    0, -1, -2, -1,  0,
                    0,  0, -1,  0,  0
                };
                break;
            case "Highpass 5x5":
                size = 5;
                kernel = new float[] {
                    0, -1, -1, -1,  0,
                    -1,  2, -4,  2, -1,
                    -1, -4, 13, -4, -1,
                    -1,  2, -4,  2, -1,
                    0, -1, -1, -1,  0
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
                    0, -1,  0,
                    -1,  4, -1,
                    0, -1,  0
                };
                break;
            case "Laplacian 8":
                size = 3;
                kernel = new float[] {
                    -1, -1, -1,
                    -1,  8, -1,
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
                    0,  0, -1,  0,  0,
                    0, -1, -2, -1,  0,
                    -1, -2, -3, -2, -1,
                    0, -1, -2, -1,  0,
                    0,  0, -1,  0,  0
                };
                break;
            case "Sharpen 3x3":
                size = 3;
                kernel = new float[] {
                    -1,  0, -1,
                    0,  7,  0,
                    -1,  0, -1
                };
                break;
            case "Sharpen 5x5":
                size = 5;
                kernel = new float[] {
                    -1, -1, -1, -1, -1,
                    -1,  2,  2,  2, -1,
                    -1,  2,  8,  2, -1,
                    -1,  2,  2,  2, -1,
                    -1, -1, -1, -1, -1
                };
                break;
        }
        
        if (kernel != null) {
            applyConvolution2D(filterName, kernel, size);
        }
    }

    private void applyConvolution2D(String name, float[] kernel, int size) {
        if (volume == null) return;
        
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
                        if (py < 0) py = 0;
                        else if (py >= dimY) py = dimY - 1;
                        
                        int pyOffset = sliceOffset + py * dimX;
                        int kRowOffset = ky * size;
                        
                        for (int kx = 0; kx < size; kx++) {
                            int px = x + kx - half;
                            if (px < 0) px = 0;
                            else if (px >= dimX) px = dimX - 1;
                            
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
}
