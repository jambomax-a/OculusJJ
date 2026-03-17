package com.jj.dicomviewer.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import com.jj.dicomviewer.model.DicomPix;

/**
 * CPRController - Curved Planar Reformation (Curved MPR) 実装
 * HOROS-20240407準拠: CPRController.h/m 相当
 */
public class CPRController extends JFrame {

    private ViewerController viewer2D;
    private List<DicomPix> pixList;

    public List<DicomPix> getPixList() {
        return pixList;
    }

    private short[][][] volume;
    private int dimX, dimY, dimZ;
    private double spacingX, spacingY, spacingZ;
    private double originX, originY, originZ;
    private float rescaleSlope = 1.0f;
    private float rescaleIntercept = 0.0f;
    private boolean isSigned = false;
    private boolean isInverse = false;

    private CPRView straightenedView;
    private CPRView stretchedView;
    private CPRTransverseView topTransView;
    private CPRTransverseView midTransView;
    private CPRTransverseView botTransView;

    private CPROrthogonalView axialView;
    private CPROrthogonalView sagittalView;
    private CPROrthogonalView coronalView;

    private JTabbedPane resultTabPane;
    private JSlider rotSlider;

    private List<com.jj.dicomviewer.model.ROI.Point3D> curveNodes = new ArrayList<>();
    private int selectedNodeIndex = 0;

    // --- Horos/Osirix 準拠: 3D中心点と回転状態の管理 ---
    private double crosshairX, crosshairY, crosshairZ; // インデックス空間での中心点

    // 各ビューのサンプリング基底ベクトル (S=Sagittal Normal, C=Coronal Normal, A=Axial Normal)
    // HOROS-20240407準拠: 初期値はDICOM Orientationに合わせる
    private double[] vS = { 1, 0, 0 };
    private double[] vC = { 0, 1, 0 };
    private double[] vA = { 0, 0, 1 };

    private double[] orientation = new double[9];

    public double[] getOrientation() {
        return orientation;
    }

    public double[] getVS() {
        return vS;
    }

    public double[] getVC() {
        return vC;
    }

    public double[] getVA() {
        return vA;
    }

    /**
     * 指定されたビューの法線ベクトルを軸として、他の2つの基底ベクトルを回転させます。
     * これにより、3つのビューが常に直行しつつ、全てのビューで独立した回転が可能になります。
     */
    public void rotateMPR(CPROrthogonalView.Direction dir, double deltaDeg) {
        double rad = Math.toRadians(deltaDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        switch (dir) {
            case AXIAL:
                // Axial Normal (vA) を軸に、vS と vC を回転
                rotatePair(vS, vC, cos, sin);
                break;
            case SAGITTAL:
                // Sagittal Normal (vS) を軸に、vC と vA を回転
                rotatePair(vC, vA, cos, -sin); // 反転を修正
                break;
            case CORONAL:
                // Coronal Normal (vC) を軸に、vS と vA を回転
                rotatePair(vS, vA, cos, -sin); // 方向調整
                break;
        }
        updateViews();
    }

    private void rotatePair(double[] v1, double[] v2, double cos, double sin) {
        for (int i = 0; i < 3; i++) {
            double oldV1 = v1[i];
            double oldV2 = v2[i];
            v1[i] = oldV1 * cos + oldV2 * sin;
            v2[i] = -oldV1 * sin + oldV2 * cos;
        }
    }

    public double getAngle(CPROrthogonalView.Direction dir) {
        switch (dir) {
            case AXIAL:
                return Math.toDegrees(Math.atan2(vS[1], vS[0]));
            case SAGITTAL:
                return Math.toDegrees(Math.atan2(vC[2], vC[1]));
            case CORONAL:
                return Math.toDegrees(Math.atan2(vS[2], vS[0]));
            default:
                return 0;
        }
    }

    // 後方互換性維持
    public void setRotationAngle(double angle) {
        // 初期状態にリセットしてAxial回転を適用
        vS = new double[] { 1, 0, 0 };
        vC = new double[] { 0, 1, 0 };
        vA = new double[] { 0, 0, 1 };
        rotateMPR(CPROrthogonalView.Direction.AXIAL, angle);

        if (rotSlider != null && !rotSlider.getValueIsAdjusting()) {
            rotSlider.setValue((int) ((angle % 360 + 360) % 360));
        }
        updateViews();
    }

    public double getRotationAngle() {
        return getAngle(CPROrthogonalView.Direction.AXIAL);
    }

    public void setAngle(CPROrthogonalView.Direction dir, double angle) {
        // 個別角度のセットは rotateMPR を通じて行うべきだが、UIイベント用に互換メソッドとして残す
        // ここでは単純なセットは行わず、差分があれば回転させるなどの処理が必要だが、
        // 現状は handleMouse で直接 rotateMPR を呼ぶように修正する。
    }
    // ----------------------------------------------

    // ウィンドウレベル設定
    private float windowLevel = 40;
    private float windowWidth = 400;

    public void setWindowLevel(float wl) {
        this.windowLevel = wl;
    }

    public void setWindowWidth(float ww) {
        this.windowWidth = Math.max(1, ww);
    }

    public void setCurve(List<com.jj.dicomviewer.model.ROI.Point3D> nodes) {
        this.curveNodes = resamplePath(nodes, 1.0); // 1mm間隔でリサンプリング

        if (this.curveNodes != null && !this.curveNodes.isEmpty()) {
            this.selectedNodeIndex = this.curveNodes.size() / 2;
        }

        if (straightenedView != null)
            straightenedView.setPath(this.curveNodes);
        if (stretchedView != null)
            stretchedView.setPath(this.curveNodes);

        updateTransverseViews();
        updateOrthogonalViews();
        repaint();
    }

    private void updateTransverseViews() {
        if (curveNodes.size() < 2)
            return;
        topTransView.setData(curveNodes.get(0));
        midTransView.setData(curveNodes.get(selectedNodeIndex));
        botTransView.setData(curveNodes.get(curveNodes.size() - 1));
    }

    private void updateOrthogonalViews() {
        if (axialView != null)
            axialView.refresh();
        if (sagittalView != null)
            sagittalView.refresh();
        if (coronalView != null)
            coronalView.refresh();
    }

    public CPRController(ViewerController viewer2D) {
        super("3D Curved-MPR");
        this.viewer2D = viewer2D;

        initializeData();
        initializeUI();
    }

    private void initializeData() {
        List<DicomPix> sourcePix = viewer2D.getImageView().getDcmPixList();
        if (sourcePix == null || sourcePix.isEmpty())
            return;

        this.pixList = new ArrayList<>(sourcePix);
        this.pixList.sort((a, b) -> Double.compare(a.getSliceLocation(), b.getSliceLocation()));

        DicomPix first = pixList.get(0);
        if (first.getShortData() == null)
            first.loadImage();

        dimX = first.getWidth();
        dimY = first.getHeight();
        dimZ = pixList.size();
        spacingX = first.getPixelSpacingX();
        spacingY = first.getPixelSpacingY();

        double[] firstOrient = first.getOrientation();
        if (firstOrient != null && firstOrient.length >= 6) {
            System.arraycopy(firstOrient, 0, this.orientation, 0, 6);
            // Normal vector (orient[6..8]) は DicomPix.readHeader で計算されているはず
            if (firstOrient.length >= 9) {
                System.arraycopy(firstOrient, 6, this.orientation, 6, 3);
            } else {
                // Cross product
                this.orientation[6] = firstOrient[1] * firstOrient[5] - firstOrient[2] * firstOrient[4];
                this.orientation[7] = firstOrient[2] * firstOrient[3] - firstOrient[0] * firstOrient[5];
                this.orientation[8] = firstOrient[0] * firstOrient[4] - firstOrient[1] * firstOrient[3];
            }
        } else {
            this.orientation = new double[] { 1, 0, 0, 0, 1, 0, 0, 0, 1 };
        }

        // HOROS-20240407準拠: 初期基底をリセット
        this.vS = new double[] { 1, 0, 0 }; // World X
        this.vC = new double[] { 0, 1, 0 }; // World Y
        this.vA = new double[] { 0, 0, 1 }; // World Z

        originX = first.getOriginX();
        originY = first.getOriginY();
        originZ = first.getOriginZ();

        if (pixList.size() > 1) {
            DicomPix second = pixList.get(1);
            double dzx = second.getOriginX() - first.getOriginX();
            double dzy = second.getOriginY() - first.getOriginY();
            double dzz = second.getOriginZ() - first.getOriginZ();
            spacingZ = Math.sqrt(dzx * dzx + dzy * dzy + dzz * dzz);
            if (spacingZ <= 0)
                spacingZ = 1.0;
        } else {
            spacingZ = first.getSliceThickness();
            if (spacingZ <= 0)
                spacingZ = 1.0;
        }

        windowLevel = first.getWindowLevel();
        windowWidth = first.getWindowWidth();
        rescaleSlope = first.getRescaleSlope();
        rescaleIntercept = first.getRescaleIntercept();
        isSigned = first.getIsSigned();
        isInverse = first.getIsInverse();

        // HOROS-20240407準拠: WL/WWが0の場合はBest Fitを計算
        if (windowWidth <= 0 || windowLevel == 0) {
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            // センター付近のデータでサンプリング
            DicomPix centerPix = pixList.get(dimZ / 2);
            short[] data = centerPix.getShortData();
            if (data != null) {
                for (int i = 0; i < data.length; i += 100) {
                    float v = (isSigned ? (float) data[i] : (float) (data[i] & 0xFFFF)) * rescaleSlope
                            + rescaleIntercept;
                    if (v < min)
                        min = v;
                    if (v > max)
                        max = v;
                }
                // 標準的なCT範囲 (-1000 ~ 1000以上) の場合はSoft Tissue窓をデフォルトにする
                if (min < -800 && max > 200) {
                    windowWidth = 400;
                    windowLevel = 40;
                } else {
                    windowWidth = max - min;
                    if (windowWidth < 1)
                        windowWidth = 1000;
                    windowLevel = min + windowWidth / 2.0f;
                }
            }
        }

        if (pixList.size() > 0) {
            if (axialSlice == -1)
                axialSlice = dimZ / 2;
            if (sagittalSlice == -1)
                sagittalSlice = dimX / 2;
            if (coronalSlice == -1)
                coronalSlice = dimY / 2;

            // 3D中心点の初期化
            this.crosshairX = sagittalSlice;
            this.crosshairY = coronalSlice;
            this.crosshairZ = axialSlice;
        }

        // ボリュームデータの構築
        volume = new short[dimZ][dimY][dimX];
        for (int z = 0; z < dimZ; z++) {
            DicomPix pix = pixList.get(z);
            pix.setSliceIndex(z);
            short[] data = pix.getShortData();
            if (data == null) {
                pix.loadImage();
                data = pix.getShortData();
            }
            if (data != null) {
                for (int y = 0; y < dimY; y++) {
                    System.arraycopy(data, y * dimX, volume[z][y], 0, dimX);
                }
            }
        }
    }

    private void initializeUI() {
        setSize(1200, 900);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLayout(new BorderLayout());

        // 4ペインレイアウトの構築 (Horos CPR.xib / 添付画像 準拠)
        JPanel mainGrid = new JPanel(new GridLayout(2, 2, 2, 2));
        mainGrid.setBackground(Color.BLACK);

        axialView = new CPROrthogonalView(this, CPROrthogonalView.Direction.AXIAL);
        sagittalView = new CPROrthogonalView(this, CPROrthogonalView.Direction.SAGITTAL);
        coronalView = new CPROrthogonalView(this, CPROrthogonalView.Direction.CORONAL);
        straightenedView = new CPRView(this, true);

        // Transverse views の初期化
        topTransView = new CPRTransverseView(this, "Start");
        midTransView = new CPRTransverseView(this, "Middle");
        botTransView = new CPRTransverseView(this, "End");

        // --- CPR Cluster の構築 (右下ペイン) ---
        // Horos では大きな CPR 画像の右側に 3つの横断面が並ぶ
        JPanel cprCluster = new JPanel(new BorderLayout());
        cprCluster.setBackground(Color.BLACK);
        cprCluster.add(straightenedView, BorderLayout.CENTER);

        JPanel transSidePanel = new JPanel(new GridLayout(3, 1, 2, 2));
        transSidePanel.setBackground(Color.BLACK);
        transSidePanel.setPreferredSize(new Dimension(150, 0));
        transSidePanel.add(topTransView);
        transSidePanel.add(midTransView);
        transSidePanel.add(botTransView);
        cprCluster.add(transSidePanel, BorderLayout.EAST);

        // 添付画像通りの並び: 左上=Sagittal, 右上=Coronal, 左下=Axial, 右下=Cluster
        mainGrid.add(sagittalView);
        mainGrid.add(coronalView);
        mainGrid.add(axialView);
        mainGrid.add(cprCluster);

        add(mainGrid, BorderLayout.CENTER);

        // ツールバー (Horos風)
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        // --- Standard Tools (Tool Matrix 相当) ---
        JButton selectBtn = new JButton("Select");
        selectBtn.addActionListener(e -> DCMView.currentTool = DCMView.ToolMode.tPlain);
        toolbar.add(selectBtn);

        JButton wlBtn = new JButton("WL/WW");
        wlBtn.addActionListener(e -> DCMView.currentTool = DCMView.ToolMode.tWLWW);
        toolbar.add(wlBtn);

        JButton zoomBtn = new JButton("Zoom");
        zoomBtn.addActionListener(e -> DCMView.currentTool = DCMView.ToolMode.tZoom);
        toolbar.add(zoomBtn);

        JButton panBtn = new JButton("Pan");
        panBtn.addActionListener(e -> DCMView.currentTool = DCMView.ToolMode.tTranslate);
        toolbar.add(panBtn);

        toolbar.addSeparator();

        JButton curvedBtn = new JButton("Draw Path");
        curvedBtn.setToolTipText("Enable Path Drawing Tool (Click in 2D or 3D views)");
        curvedBtn.addActionListener(e -> DCMView.currentTool = DCMView.ToolMode.tCurvedROI);
        toolbar.add(curvedBtn);

        JButton resetBtn = new JButton("Clear Path");
        resetBtn.setToolTipText("Clear the current curved path");
        resetBtn.addActionListener(e -> clearCurve());
        toolbar.add(resetBtn);

        toolbar.addSeparator();

        // Reformation Type (Straightened / Stretched)
        JPanel typePanel = new JPanel();
        typePanel.setBorder(BorderFactory.createTitledBorder("Reformation Type"));
        JRadioButton stRadio = new JRadioButton("Straightened", true);
        JRadioButton strRadio = new JRadioButton("Stretched");
        ButtonGroup group = new ButtonGroup();
        group.add(stRadio);
        group.add(strRadio);
        stRadio.addActionListener(e -> straightenedView.setStraightened(true));
        strRadio.addActionListener(e -> straightenedView.setStraightened(false));
        typePanel.add(stRadio);
        typePanel.add(strRadio);
        toolbar.add(typePanel);

        // Rotation (Curved MPR Angle) - Toolbar に直接置くのが Horos 流
        toolbar.addSeparator();
        toolbar.add(new JLabel(" Angle: "));
        rotSlider = new JSlider(0, 360, 0);
        rotSlider.setPreferredSize(new Dimension(150, 40));
        rotSlider.addChangeListener(e -> {
            if (rotSlider.getValueIsAdjusting()) {
                setRotationAngle(rotSlider.getValue());
            }
        });
        toolbar.add(rotSlider);

        toolbar.addSeparator();
        JButton curveBtn = new JButton("Palette");
        curveBtn.addActionListener(e -> {
            CurvePaletteDialog.showPalette(this, this);
        });
        toolbar.add(curveBtn);

        add(toolbar, BorderLayout.NORTH);
    }

    private int axialSlice = -1;
    private int sagittalSlice = -1;
    private int coronalSlice = -1;

    public void addNode(com.jj.dicomviewer.model.ROI.Point3D node) {
        this.curveNodes.add(node);
        setCurve(new ArrayList<>(this.curveNodes));
    }

    public void clearCurve() {
        this.curveNodes.clear();
        setCurve(new ArrayList<>());
    }

    public void updateLastNode(com.jj.dicomviewer.model.ROI.Point3D node) {
        if (!curveNodes.isEmpty()) {
            curveNodes.set(curveNodes.size() - 1, node);
            setCurve(new ArrayList<>(this.curveNodes));
        }
    }

    public void updateSelectedNodeFromSlices() {
        if (curveNodes == null || curveNodes.isEmpty())
            return;

        double tx = originX + sagittalSlice * spacingX;
        double ty = originY + coronalSlice * spacingY;
        double tz = originZ + (dimZ - 1 - axialSlice) * spacingZ;

        double minDist = Double.MAX_VALUE;
        int minIdx = selectedNodeIndex;
        for (int i = 0; i < curveNodes.size(); i++) {
            com.jj.dicomviewer.model.ROI.Point3D p = curveNodes.get(i);
            double d = Math.pow(p.x - tx, 2) + Math.pow(p.y - ty, 2) + Math.pow(p.z - tz, 2);
            if (d < minDist) {
                minDist = d;
                minIdx = i;
            }
        }
        this.selectedNodeIndex = minIdx;
    }

    private void syncSelectedNodeToCrosshair() {
        if (curveNodes == null || curveNodes.isEmpty())
            return;

        double tx = originX + crosshairX * spacingX;
        double ty = originY + crosshairY * spacingY;
        double tz = originZ + (dimZ - 1 - crosshairZ) * spacingZ;

        double minDist = Double.MAX_VALUE;
        int minIdx = selectedNodeIndex;
        for (int i = 0; i < curveNodes.size(); i++) {
            com.jj.dicomviewer.model.ROI.Point3D p = curveNodes.get(i);
            double d = Math.pow(p.x - tx, 2) + Math.pow(p.y - ty, 2) + Math.pow(p.z - tz, 2);
            if (d < minDist) {
                minDist = d;
                minIdx = i;
            }
        }
        this.selectedNodeIndex = minIdx;
    }

    public void setSlice(CPROrthogonalView.Direction dir, int slice) {
        switch (dir) {
            case AXIAL:
                axialSlice = slice;
                crosshairZ = slice;
                break;
            case SAGITTAL:
                sagittalSlice = slice;
                crosshairX = slice;
                break;
            case CORONAL:
                coronalSlice = slice;
                crosshairY = slice;
                break;
        }
        syncSelectedNodeToCrosshair();
        updateViews();
    }

    public void setCrosshairCenter(double x, double y, double z) {
        this.crosshairX = Math.max(0, Math.min(dimX - 1, x));
        this.crosshairY = Math.max(0, Math.min(dimY - 1, y));
        this.crosshairZ = Math.max(0, Math.min(dimZ - 1, z));

        // 旧来の整数スライスも同期
        this.sagittalSlice = (int) Math.round(crosshairX);
        this.coronalSlice = (int) Math.round(crosshairY);
        this.axialSlice = (int) Math.round(crosshairZ);

        syncSelectedNodeToCrosshair();
        updateViews();
    }

    public double getCrosshairX() {
        return crosshairX;
    }

    public double getCrosshairY() {
        return crosshairY;
    }

    public double getCrosshairZ() {
        return crosshairZ;
    }

    public int getSlice(CPROrthogonalView.Direction dir) {
        switch (dir) {
            case AXIAL:
                return (int) Math.round(crosshairZ);
            case SAGITTAL:
                return (int) Math.round(crosshairX);
            case CORONAL:
                return (int) Math.round(crosshairY);
            default:
                return 0;
        }
    }

    public void updateViews() {
        if (straightenedView != null)
            straightenedView.refresh();
        if (stretchedView != null)
            stretchedView.refresh();
        if (axialView != null)
            axialView.refresh();
        if (sagittalView != null)
            sagittalView.refresh();
        if (coronalView != null)
            coronalView.refresh();
        updateTransverseViews();
        repaint();
    }

    /**
     * 曲線上の点における接線・法線・従法線フレーム（TNB）を計算
     */
    public com.jj.dicomviewer.model.ROI.Point3D[] getTNBFrame(int nodeIndex) {
        if (curveNodes.size() < 2)
            return null;

        com.jj.dicomviewer.model.ROI.Point3D next = curveNodes.get(Math.min(nodeIndex + 1, curveNodes.size() - 1));
        com.jj.dicomviewer.model.ROI.Point3D prev = curveNodes.get(Math.max(nodeIndex - 1, 0));

        // Tangent
        double tx = next.x - prev.x;
        double ty = next.y - prev.y;
        double tz = next.z - prev.z;
        double tmag = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tmag < 1e-6) {
            tx = 0;
            ty = 0;
            tz = 1;
        } else {
            tx /= tmag;
            ty /= tmag;
            tz /= tmag;
        }

        // Up (Standard Patient orientation: Superior)
        double ux = 0, uy = 0, uz = 1;
        if (Math.abs(tz) > 0.9) {
            ux = 0;
            uy = 1;
            uz = 0;
        }

        // Normal (Horizontal in slice) = Tangent x Up
        double nx = ty * uz - tz * uy;
        double ny = tz * ux - tx * uz;
        double nz = tx * uy - ty * ux;
        double nmag = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nmag;
        ny /= nmag;
        nz /= nmag;

        // Binormal (Vertical in slice) = Tangent x Normal
        double bx = ty * nz - tz * ny;
        double by = tz * nx - tx * nz;
        double bz = tx * ny - ty * nx;

        return new com.jj.dicomviewer.model.ROI.Point3D[] {
                new com.jj.dicomviewer.model.ROI.Point3D(tx, ty, tz),
                new com.jj.dicomviewer.model.ROI.Point3D(nx, ny, nz),
                new com.jj.dicomviewer.model.ROI.Point3D(bx, by, bz)
        };
    }

    public double getVoxel(double x, double y, double z) {
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        int iz = (int) Math.round(z);

        if (ix < 0 || ix >= dimX || iy < 0 || iy >= dimY || iz < 0 || iz >= dimZ)
            return -2000; // Background (Air/Outside)

        short raw = volume[iz][iy][ix];
        double val = isSigned ? (double) raw : (double) (raw & 0xFFFF);
        double res = val * rescaleSlope + rescaleIntercept;
        if (isInverse)
            res = -res;
        return res;
    }

    public int getDim(CPROrthogonalView.Direction dir, int axis) {
        switch (dir) {
            case AXIAL:
                return (axis == 0) ? dimX : (axis == 1) ? dimY : dimZ;
            case SAGITTAL:
                return (axis == 0) ? dimY : (axis == 1) ? dimZ : dimX;
            case CORONAL:
                return (axis == 0) ? dimX : (axis == 1) ? dimZ : dimY;
        }
        return 0;
    }

    public int getDimX() {
        return dimX;
    }

    public int getDimY() {
        return dimY;
    }

    public int getDimZ() {
        return dimZ;
    }

    public double getSpacing(CPROrthogonalView.Direction dir, int axis) {
        switch (dir) {
            case AXIAL:
                return (axis == 0) ? spacingX : (axis == 1 ? spacingY : spacingZ);
            case SAGITTAL:
                return (axis == 0) ? spacingY : (axis == 1 ? spacingZ : spacingX);
            case CORONAL:
                return (axis == 0) ? spacingX : (axis == 1 ? spacingZ : spacingY);
        }
        return 1.0;
    }

    public double getOrigin(CPROrthogonalView.Direction dir, int axis) {
        switch (dir) {
            case AXIAL:
                return (axis == 0) ? originX : (axis == 1 ? originY : originZ);
            case SAGITTAL:
                return (axis == 0) ? originY : (axis == 1 ? originZ : originX);
            case CORONAL:
                return (axis == 0) ? originX : (axis == 1 ? originZ : originY);
        }
        return 0.0;
    }

    public double getOriginX() {
        return originX;
    }

    public double getOriginY() {
        return originY;
    }

    public double getOriginZ() {
        return originZ;
    }

    public float getRescaleSlope() {
        return rescaleSlope;
    }

    public float getRescaleIntercept() {
        return rescaleIntercept;
    }

    public boolean getIsSigned() {
        return isSigned;
    }

    public boolean getIsInverse() {
        return isInverse;
    }

    // トリリニア補間 (Horos CPRVolume.m 準拠)
    /**
     * ROIのノード（3D座標）から、一定間隔（1mmなど）でリサンプリングされたパスを生成します。
     */
    public java.util.List<com.jj.dicomviewer.model.ROI.Point3D> resamplePath(
            java.util.List<com.jj.dicomviewer.model.ROI.Point3D> nodes, double intervalMm) {
        if (nodes == null || nodes.size() < 2)
            return nodes;

        // HOROS-20240407準拠: 3D Catmull-Rom Spline による滑らかなパス生成
        java.util.List<com.jj.dicomviewer.model.ROI.Point3D> path = new java.util.ArrayList<>();

        // 端点の補完用制御点
        java.util.List<com.jj.dicomviewer.model.ROI.Point3D> pts = new java.util.ArrayList<>();
        pts.add(nodes.get(0)); // Dummy P-1
        pts.addAll(nodes);
        pts.add(nodes.get(nodes.size() - 1)); // Dummy P+1

        for (int i = 1; i < pts.size() - 2; i++) {
            com.jj.dicomviewer.model.ROI.Point3D p0 = pts.get(i - 1);
            com.jj.dicomviewer.model.ROI.Point3D p1 = pts.get(i);
            com.jj.dicomviewer.model.ROI.Point3D p2 = pts.get(i + 1);
            com.jj.dicomviewer.model.ROI.Point3D p3 = pts.get(i + 2);

            double dist = Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2) + Math.pow(p2.z - p1.z, 2));
            int steps = (int) Math.max(1, dist / intervalMm);

            for (int k = 0; k < steps; k++) {
                double t = (double) k / steps;
                double t2 = t * t;
                double t3 = t2 * t;

                double x = 0.5 * ((2 * p1.x) + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2
                        + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
                double y = 0.5 * ((2 * p1.y) + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2
                        + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
                double z = 0.5 * ((2 * p1.z) + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2
                        + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);

                path.add(new com.jj.dicomviewer.model.ROI.Point3D(x, y, z));
            }
        }
        // 最後の一点を追加
        path.add(nodes.get(nodes.size() - 1));

        return path;
    }

    public double getInterpolatedVoxel(double x, double y, double z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        if (x0 < 0 || x1 >= dimX || y0 < 0 || y1 >= dimY || z0 < 0 || z1 >= dimZ)
            return -2000;

        double dx = x - x0;
        double dy = y - y0;
        double dz = z - z0;

        double v000 = getVoxelValue(x0, y0, z0);
        double v100 = getVoxelValue(x1, y0, z0);
        double v010 = getVoxelValue(x0, y1, z0);
        double v110 = getVoxelValue(x1, y1, z0);
        double v001 = getVoxelValue(x0, y0, z1);
        double v101 = getVoxelValue(x1, y0, z1);
        double v011 = getVoxelValue(x0, y1, z1);
        double v111 = getVoxelValue(x1, y1, z1);

        double v00 = v000 * (1 - dx) + v100 * dx;
        double v10 = v010 * (1 - dx) + v110 * dx;
        double v01 = v001 * (1 - dx) + v101 * dx;
        double v11 = v011 * (1 - dx) + v111 * dx;

        double v0 = v00 * (1 - dy) + v10 * dy;
        double v1 = v01 * (1 - dy) + v11 * dy;

        double result = v0 * (1 - dz) + v1 * dz;

        // Rescale Slope/Intercept を適用して HU に変換
        double res = result * rescaleSlope + rescaleIntercept;

        // MONOCHROME1 の場合は反転（Horos準拠）
        if (isInverse) {
            res = -res;
        }

        return res;
    }

    private double getVoxelValue(int x, int y, int z) {
        short raw = volume[z][y][x];
        if (isSigned) {
            return (double) raw;
        } else {
            return (double) (raw & 0xFFFF);
        }
    }

    /**
     * 患者座標(mm)からボリュームの画素値をトリリニア補間して取得
     */
    public double getInterpolatedVoxelByPatient(double px, double py, double pz) {
        // HOROS-20240407準拠: 物理座標からインデックスへの変換 (Orientationを考慮)
        double dx = px - originX;
        double dy = py - originY;
        double dz = pz - originZ;

        // I = M^T * D / Spacing
        double ix = (dx * orientation[0] + dy * orientation[1] + dz * orientation[2]) / spacingX;
        double iy = (dx * orientation[3] + dy * orientation[4] + dz * orientation[5]) / spacingY;
        double iz = (dx * orientation[6] + dy * orientation[7] + dz * orientation[8]) / spacingZ;

        return getInterpolatedVoxel(ix, iy, iz);
    }

    public List<com.jj.dicomviewer.model.ROI.Point3D> getCurveNodes() {
        return curveNodes;
    }

    public int getSelectedNodeIndex() {
        return selectedNodeIndex;
    }

    public void setSelectedNodeIndex(int index) {
        if (curveNodes == null || curveNodes.isEmpty())
            return;
        if (index < 0)
            index = 0;
        if (index >= curveNodes.size())
            index = curveNodes.size() - 1;
        this.selectedNodeIndex = index;

        // クロスヘアを選択ノードに同期 (Horos準拠)
        com.jj.dicomviewer.model.ROI.Point3D p = curveNodes.get(index);
        axialSlice = (int) Math.round((p.z - originZ) / spacingZ);
        sagittalSlice = (int) Math.round((p.x - originX) / spacingX);
        coronalSlice = (int) Math.round((p.y - originY) / spacingY);

        updateViews();
    }

    public com.jj.dicomviewer.model.ROI.Point3D getSelectedNode() {
        if (curveNodes == null || curveNodes.isEmpty())
            return null;
        return curveNodes.get(selectedNodeIndex);
    }

    public float getWindowLevel() {
        return windowLevel;
    }

    public float getWindowWidth() {
        return windowWidth;
    }

    public double getSpacingX() {
        return spacingX;
    }

    public double getSpacingY() {
        return spacingY;
    }

    public double getSpacingZ() {
        return spacingZ;
    }

    public static void openCurved(ViewerController viewer) {
        SwingUtilities.invokeLater(() -> {
            CPRController cpr = new CPRController(viewer);
            cpr.setVisible(true);
        });
    }
}
