package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.time.format.DateTimeFormatter;
import com.oculus.jj.model.DicomPix;
import com.oculus.jj.model.DicomStudy;

/**
 * MPRController - Orthogonal MPR実装
 * HOROS-20240407準拠: OrthogonalMPRController.h/m 相当
 */
public class MPRController extends JFrame {

    private ViewerController viewer2D;
    private List<DicomPix> pixList;

    private short[][][] volume; // [z][y][x]
    private int dimX, dimY, dimZ;
    private double spacingX, spacingY, spacingZ;

    private MPRView axialView;
    private MPRView sagittalView;
    private MPRView coronalView;
    private MPRView threeDView; // 3D表示用

    // --- HOROS-20240407 [3D MPR 拡張] ---
    // 現在の注視点 (volume coordinates / double for precision)
    private double curX, curY, curZ;

    // 各ビューのサンプリング中心点 (パン状態を保持。これを固定することで、クロスヘアを動かしても画像が移動しないようにする)
    private double volCX, volCY, volCZ;

    // 各ビューのサンプリング基底ベクトル (S=Sagittal Normal, C=Coronal Normal, A=Axial Normal)
    private double[] vS = { 1, 0, 0 }; // X-axis
    private double[] vC = { 0, 1, 0 }; // Y-axis
    private double[] vA = { 0, 0, 1 }; // Z-axis

    private double[] orientation = new double[9];
    private double originX, originY, originZ;

    // 3D回転パラメータ (3D表示用のみ)
    private double rotateX = Math.toRadians(-20);
    private double rotateY = Math.toRadians(20);

    private float windowLevel = 40;
    private float windowWidth = 400;

    // --- 厚み（Thick Slab）対応 ---
    public enum SlabMode {
        NONE, AVERAGE, MIP, MINIP
    }

    private double slabThickness = 1.0; // mm
    private SlabMode slabMode = SlabMode.MIP;

    public void setSlabThickness(double mm) {
        this.slabThickness = mm;
        updateAllViews(true);
    }

    public double getSlabThickness() {
        return slabThickness;
    }

    public void setSlabMode(SlabMode mode) {
        this.slabMode = mode;
        updateAllViews(true);
    }

    public SlabMode getSlabMode() {
        return slabMode;
    }

    private float rescaleSlope = 1.0f;
    private float rescaleIntercept = 0.0f;
    private boolean isSigned = false;
    private boolean isInverse = false;

    public enum LayoutType {
        ORTHOGONAL,
        THREE_D_MPR,
        CURVED_MPR
    }

    private LayoutType currentLayout = LayoutType.ORTHOGONAL;
    private MPRView.Direction highlightedView = MPRView.Direction.AXIAL;

    // --- Curved MPR 用のパス ---
    private List<double[]> curvePoints = new ArrayList<>();
    private double[] hoverCurvePoint = null; // マウスオーバー時の対応点
    private List<MPRView> thumbnailViews = new ArrayList<>(); // サムネイルViewを管理
    private double thumbInterval_mm = 35.2; // A-B, B-C 間の距離 (デフォルト 35.2mm)

    public void setThumbInterval(double mm) {
        this.thumbInterval_mm = mm;
        if (this.thumbInterval_mm < 0.1)
            this.thumbInterval_mm = 0.1;
        // 間隔が変わった時は、サムネイル画像を更新し、メインビューは再描画(repaint)のみ行う
        updateAllViews(false); // 全体の repaint をトリガー
        for (MPRView v : thumbnailViews) {
            v.triggerUpdate(true); // サムネイルのみ強制再生成
        }
    }

    private List<MPRView> getAllViews() {
        List<MPRView> views = new ArrayList<>();
        if (axialView != null)
            views.add(axialView);
        if (sagittalView != null)
            views.add(sagittalView);
        if (coronalView != null)
            views.add(coronalView);
        if (threeDView != null)
            views.add(threeDView);
        // サムネイルViewも管理されている場合はここに追加が必要ですが、
        // 現在は setLayoutCurvedMPR でローカル変数として生成されています。
        return views;
    }

    public double getThumbInterval() {
        return thumbInterval_mm;
    }

    /**
     * 現在の注視点 (curX, curY, curZ) が曲線上のどの位置（始点からの距離 mm）にあるかを計算する。
     * 最も近いセグメント上の点を探す。
     */
    public double getCurveDistanceAtFocus() {
        if (curvePoints.size() < 2)
            return 0;
        double minDistSq = Double.MAX_VALUE;
        double bestDist = 0;
        double currentDist = 0;

        for (int i = 0; i < curvePoints.size() - 1; i++) {
            double[] p1 = curvePoints.get(i);
            double[] p2 = curvePoints.get(i + 1);

            // 線分 p1-p2 と現在地 (curX, curY, curZ) の最短距離を求める
            double segmentLen = Math
                    .sqrt(Math.pow(p2[0] - p1[0], 2) + Math.pow(p2[1] - p1[1], 2) + Math.pow(p2[2] - p1[2], 2));
            if (segmentLen < 0.0001)
                continue;

            double t = ((curX - p1[0]) * (p2[0] - p1[0]) + (curY - p1[1]) * (p2[1] - p1[1])
                    + (curZ - p1[2]) * (p2[2] - p1[2])) / (segmentLen * segmentLen);
            t = Math.max(0, Math.min(1, t));

            double closestX = p1[0] + t * (p2[0] - p1[0]);
            double closestY = p1[1] + t * (p2[1] - p1[1]);
            double closestZ = p1[2] + t * (p2[2] - p1[2]);

            double distSq = Math.pow(curX - closestX, 2) + Math.pow(curY - closestY, 2) + Math.pow(curZ - closestZ, 2);
            if (distSq < minDistSq) {
                minDistSq = distSq;
                bestDist = currentDist + t * segmentLen;
            }
            currentDist += segmentLen;
        }
        return bestDist;
    }

    public double getCurveDistanceTotal() {
        double total = 0;
        for (int i = 0; i < curvePoints.size() - 1; i++) {
            total += Math.sqrt(Math.pow(curvePoints.get(i + 1)[0] - curvePoints.get(i)[0], 2)
                    + Math.pow(curvePoints.get(i + 1)[1] - curvePoints.get(i)[1], 2)
                    + Math.pow(curvePoints.get(i + 1)[2] - curvePoints.get(i)[2], 2));
        }
        return total;
    }

    public void setHoverCurvePoint(double[] pt) {
        this.hoverCurvePoint = pt;
        updateAllViews();
    }

    public double[] getHoverCurvePoint() {
        return hoverCurvePoint;
    }

    public void addCurvePoint(double x, double y, double z) {
        curvePoints.add(new double[] { x, y, z });
        updateAllViews();
    }

    public void clearCurvePoints() {
        curvePoints.clear();
        updateAllViews();
    }

    public List<double[]> getCurvePoints() {
        return curvePoints;
    }

    public void setCurvePoints(List<double[]> points) {
        this.curvePoints = new ArrayList<>(points);
        updateAllViews(true);
    }

    public LayoutType getLayoutType() {
        return currentLayout;
    }

    public void setHighlightedView(MPRView.Direction dir) {
        this.highlightedView = dir;
        updateAllViews();
    }

    public MPRView.Direction getHighlightedView() {
        return highlightedView;
    }

    public MPRController(ViewerController viewer2D, LayoutType layout) {
        super(layout == LayoutType.ORTHOGONAL ? "Orthogonal MPR" : "3D MPR");
        this.viewer2D = viewer2D;
        this.currentLayout = layout;

        // UIを先に構築して表示
        initializeUI();

        // 別スレッドで重いデータを読み込む
        setTitle("J-Horos MPR (Loading Volume...)");
        new Thread(() -> {
            try {
                initializeData();
                SwingUtilities.invokeLater(() -> {
                    setTitle("J-Horos MPR (v2025.02.09-STABLE)");
                    updateAllViews(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void initializeData() {
        // HOROS-20240407準拠: 全てのシリーズではなく、現在2Dビューワーが表示しているシリーズを正確に使用する
        // これを怠ると、スカウト画像（別シリーズ）が混入して画像が真っ黒になる原因になる
        List<DicomPix> sourcePix = viewer2D.getImageView().getDcmPixList();
        if (sourcePix == null || sourcePix.isEmpty()) {
            return;
        }

        // コピーを作成（元のリスト順序を壊さないため）
        this.pixList = new ArrayList<>(sourcePix);

        // HOROS-20240407準拠: スライスを物理的な位置(Slice Location)でソートする
        // 欠落している場合は originZ (ImagePositionPatient[2]) をフォールバックに使用する
        this.pixList.sort((a, b) -> Double.compare(a.getSliceLocation(), b.getSliceLocation()));

        // 【重要】サイズが異なるスライス（スカウト画像など）が含まれている場合、
        // 多数派のサイズを採用し、それ以外を除外する。サイズが混在すると配列コピーでエラーになり真っ黒になる。
        Map<String, Integer> dimCount = new HashMap<>();
        for (DicomPix p : pixList) {
            String key = p.getWidth() + "x" + p.getHeight();
            dimCount.put(key, dimCount.getOrDefault(key, 0) + 1);
        }

        String bestKey = "";
        int maxC = -1;
        for (String key : dimCount.keySet()) {
            if (dimCount.get(key) > maxC) {
                maxC = dimCount.get(key);
                bestKey = key;
            }
        }

        String finalBestKey = bestKey;
        pixList.removeIf(p -> !(p.getWidth() + "x" + p.getHeight()).equals(finalBestKey));

        if (pixList.isEmpty())
            return;

        DicomPix first = pixList.get(0);
        // HOROS-20240407準拠: データをコピーする前に、最初のスライスを確実にロードして
        // 正しいサイズやWL/WWを確定させる
        if (first.getShortData() == null) {
            first.loadImage();
        }

        dimX = first.getWidth();
        dimY = first.getHeight();
        dimZ = pixList.size();

        spacingX = first.getPixelSpacingX();
        spacingY = first.getPixelSpacingY();
        originX = first.getOriginX();
        originY = first.getOriginY();
        originZ = first.getOriginZ();
        double[] firstOrient = first.getOrientation();
        if (firstOrient != null && firstOrient.length >= 6) {
            System.arraycopy(firstOrient, 0, this.orientation, 0, 6);
            if (firstOrient.length >= 9) {
                System.arraycopy(firstOrient, 6, this.orientation, 6, 3);
            } else {
                this.orientation[6] = firstOrient[1] * firstOrient[5] - firstOrient[2] * firstOrient[4];
                this.orientation[7] = firstOrient[2] * firstOrient[3] - firstOrient[0] * firstOrient[5];
                this.orientation[8] = firstOrient[0] * firstOrient[4] - firstOrient[1] * firstOrient[3];
            }
        } else {
            this.orientation = new double[] { 1, 0, 0, 0, 1, 0, 0, 0, 1 };
        }

        // HOROS-20240407準拠: Slice spacingの計算
        if (pixList.size() > 1) {
            DicomPix second = pixList.get(1);
            double dx = second.getOriginX() - first.getOriginX();
            double dy = second.getOriginY() - first.getOriginY();
            double dz = second.getOriginZ() - first.getOriginZ();
            spacingZ = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        if (spacingZ <= 0) {
            spacingZ = first.getSliceThickness();
        }
        if (spacingZ <= 0) {
            spacingZ = 1.0;
        }

        // // System.out.println("[DEBUG] MPR Spacing: X=" + spacingX + ", Y=" + spacingY + ", Z=" + spacingZ);

        windowLevel = first.getWindowLevel();
        windowWidth = first.getWindowWidth();
        rescaleSlope = first.getRescaleSlope();
        rescaleIntercept = first.getRescaleIntercept();
        isSigned = first.getIsSigned();
        isInverse = first.getIsInverse();

        // HOROS-20240407準拠: もしWWが0なら、Best Fitを計算した後の値を再取得する
        if (windowWidth <= 0) {
            windowLevel = first.getWindowLevel();
            windowWidth = first.getWindowWidth();
            // それでも0ならデフォルト値
            if (windowWidth <= 0) {
                windowLevel = 127;
                windowWidth = 255;
            }
        }

        // // System.out.println("[DEBUG] MPR Window: Level=" + windowLevel + ", Width=" + windowWidth);

        volume = new short[dimZ][dimY][dimX];
        for (int z = 0; z < dimZ; z++) {
            DicomPix pix = pixList.get(z);
            // 【重要】各スライスがロードされていることを確認する。
            // これを怠ると、データがnullになり、ボリュームが空（真っ黒）になる。
            if (pix.getShortData() == null) {
                pix.loadImage();
            }
            short[] src = pix.getShortData();

            // HOROS-20240407準拠: データの整合性チェック
            if (src != null && src.length == dimX * dimY) {
                for (int y = 0; y < dimY; y++) {
                    System.arraycopy(src, y * dimX, volume[z][y], 0, dimX);
                }
            } else {
                // // System.err.println("[ERROR] MPR Slice " + z + " failed. Reason: "
// //                         + (src == null ? "null data" : "size mismatch " + src.length + " vs " + (dimX * dimY)));
                if (z > 0) {
                    // 前のスライスをコピーして「空隙」を目立たなくする
                    for (int y = 0; y < dimY; y++) {
                        System.arraycopy(volume[z - 1][y], 0, volume[z][y], 0, dimX);
                    }
                }
            }
        }

        // HOROS-20240407準拠: 基準となる世界座標系を定義
        this.vS = new double[] { orientation[0], orientation[1], orientation[2] };
        this.vC = new double[] { orientation[3], orientation[4], orientation[5] };
        this.vA = new double[] { orientation[6], orientation[7], orientation[8] };

        // 初期位置を中心にする（世界座標）
        double ix = dimX / 2.0;
        double iy = dimY / 2.0;
        double iz = dimZ / 2.0;

        // P = O + i*Sx*R + j*Sy*C + k*Sz*N
        curX = originX + ix * spacingX * orientation[0] + iy * spacingY * orientation[3]
                + iz * spacingZ * orientation[6];
        curY = originY + ix * spacingX * orientation[1] + iy * spacingY * orientation[4]
                + iz * spacingZ * orientation[7];
        curZ = originZ + ix * spacingX * orientation[2] + iy * spacingY * orientation[5]
                + iz * spacingZ * orientation[8];

        volCX = curX;
        volCY = curY;
        volCZ = curZ;
    }

    private JToolBar mprToolbar;
    private JPanel contentPane;

    private void initializeUI() {
        setTitle("J-Horos MPR (v2025.02.09-STABLE)");
        setSize(1280, 1024);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        setLayout(new BorderLayout());

        mprToolbar = createToolbar();
        add(mprToolbar, BorderLayout.NORTH);

        axialView = new MPRView(this, MPRView.Direction.AXIAL);
        sagittalView = new MPRView(this, MPRView.Direction.SAGITTAL);
        coronalView = new MPRView(this, MPRView.Direction.CORONAL);
        threeDView = new MPRView(this, MPRView.Direction.THREE_D);

        contentPane = new JPanel(new BorderLayout());
        add(contentPane, BorderLayout.CENTER);

        updateLayout();
    }

    private JToolBar createToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBackground(new Color(240, 240, 240));

        JButton btnOrth = new JButton("Orthogonal");
        btnOrth.addActionListener(e -> setLayoutOrthogonal());
        tb.add(btnOrth);

        JButton btn3D = new JButton("3D MPR");
        btn3D.addActionListener(e -> setLayout3DMPR());
        tb.add(btn3D);

        JButton btnCurv = new JButton("Curved MPR");
        btnCurv.addActionListener(e -> setLayoutCurvedMPR());
        tb.add(btnCurv);

        tb.addSeparator();
        JButton btnReset = new JButton("Reset");
        btnReset.addActionListener(e -> {
            curX = volCX;
            curY = volCY;
            curZ = volCZ;
            updateAllViews(true);
        });
        tb.add(btnReset);

        JButton btnClearCurve = new JButton("Clear Curve");
        btnClearCurve.addActionListener(e -> clearCurvePoints());
        tb.add(btnClearCurve);

        tb.addSeparator();
        tb.add(new JLabel(" Slab Mode: "));
        JComboBox<SlabMode> comboSlab = new JComboBox<>(SlabMode.values());
        comboSlab.setSelectedItem(slabMode);
        comboSlab.addActionListener(e -> {
            this.slabMode = (SlabMode) comboSlab.getSelectedItem();
            updateAllViews(true);
        });
        tb.add(comboSlab);

        tb.add(new JLabel(" Thickness: "));
        JSpinner spinThick = new JSpinner(new SpinnerNumberModel(slabThickness, 0.5, 200.0, 0.5));
        spinThick.setPreferredSize(new Dimension(80, 25));
        spinThick.addChangeListener(e -> {
            this.slabThickness = (Double) spinThick.getValue();
            updateAllViews(true);
        });
        tb.add(spinThick);
        tb.add(new JLabel(" mm "));

        return tb;
    }

    private void updateLayout() {
        if (currentLayout == LayoutType.ORTHOGONAL) {
            setLayoutOrthogonal();
        } else if (currentLayout == LayoutType.CURVED_MPR) {
            setLayoutCurvedMPR();
        } else {
            setLayout3DMPR();
        }
    }

    private void setLayoutOrthogonal() {
        this.currentLayout = LayoutType.ORTHOGONAL;
        setTitle("2D Orthogonal MPR");
        contentPane.removeAll();

        // 2D Orthogonal モード時は、標準的な断面(Axial/Sagittal/Coronal)にリセットする
        this.vA = new double[] { 0, 0, 1 };
        this.vS = new double[] { 1, 0, 0 };
        this.vC = new double[] { 0, 1, 0 };

        // 以前好評だった横3列のレイアウト (Axial | Coronal | Sagittal) を維持
        JSplitPane split2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, coronalView, sagittalView);
        split2.setDividerLocation(0.5);
        split2.setResizeWeight(0.5);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, axialView, split2);
        mainSplit.setDividerLocation(0.33);
        mainSplit.setResizeWeight(0.33);

        contentPane.add(mainSplit, BorderLayout.CENTER);
        contentPane.revalidate();
        contentPane.repaint();
        updateAllViews(true);
    }

    private void setLayout3DMPR() {
        this.currentLayout = LayoutType.THREE_D_MPR;
        setTitle("3D MPR (Horos Standard)");
        contentPane.removeAll();

        // HOROS-2024.04.07 準拠: スクショに基づいた 1+2 レイアウト
        // 左側に Sagittal(上) と Axial(下) を配置し、右側に Coronal(縦長) を配置する

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sagittalView, axialView);
        leftSplit.setDividerLocation(0.5);
        leftSplit.setResizeWeight(0.5);
        leftSplit.setBorder(null);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, coronalView);
        mainSplit.setDividerLocation(0.5);
        mainSplit.setResizeWeight(0.5);
        mainSplit.setBorder(null);

        contentPane.add(mainSplit, BorderLayout.CENTER);

        // 3D View (Volume) も必要ならメイン画面外か別窓で管理することが多いが、
        // ここでは一旦レイアウトに含まない（スクショにも出ていないため）。
        // 必要に応じて 2x2 に戻すことも可能。

        contentPane.revalidate();
        contentPane.repaint();
        updateAllViews(true);
    }

    private void setLayoutCurvedMPR() {
        this.currentLayout = LayoutType.CURVED_MPR;
        setTitle("3D Curved MPR (Horos Standard)");

        // HOROS-2024.04.07 準拠: 新しく開始するときは曲線をクリアする
        clearCurvePoints();
        thumbnailViews.clear();

        contentPane.removeAll();

        // HOROS-2024.04.07 準拠: 全体は 2x2 グリッド。
        // ただし、右下のパネルのみが「メインビュー + 右側に3つのサムネイル」という構成。

        // --- 右下パネルの構築 ---
        JPanel rightThumbPanel = new JPanel(new GridLayout(3, 1, 1, 1));
        rightThumbPanel.setBackground(Color.BLACK);
        rightThumbPanel.setPreferredSize(new java.awt.Dimension(120, 0));

        // HOROS-2024.04.07 準拠: A, B, C 用の横断像プレビュー (Bを中心に一定幅で移動する)
        MPRView thumbA = new MPRView(this, MPRView.Direction.AXIAL);
        thumbA.setThumbnailMode("A", 0);
        MPRView thumbB = new MPRView(this, MPRView.Direction.AXIAL);
        thumbB.setThumbnailMode("B", 1);
        MPRView thumbC = new MPRView(this, MPRView.Direction.AXIAL);
        thumbC.setThumbnailMode("C", 2);

        thumbnailViews.add(thumbA);
        thumbnailViews.add(thumbB);
        thumbnailViews.add(thumbC);

        rightThumbPanel.add(thumbA);
        rightThumbPanel.add(thumbB);
        rightThumbPanel.add(thumbC);

        JSplitPane bottomRightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, threeDView, rightThumbPanel);
        bottomRightSplit.setDividerLocation(0.8);
        bottomRightSplit.setResizeWeight(0.8);
        bottomRightSplit.setBorder(null);

        // --- 2x2 グリッド全体の構築 ---
        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sagittalView, coronalView);
        topSplit.setDividerLocation(0.5);
        topSplit.setResizeWeight(0.5);
        topSplit.setBorder(null);
        topSplit.setDividerSize(3);

        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, axialView, bottomRightSplit);
        bottomSplit.setDividerLocation(0.5);
        bottomSplit.setResizeWeight(0.5);
        bottomSplit.setBorder(null);
        bottomSplit.setDividerSize(3);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, bottomSplit);
        mainSplit.setDividerLocation(0.5);
        mainSplit.setResizeWeight(0.5);
        mainSplit.setBorder(null);
        mainSplit.setDividerSize(3);

        contentPane.add(mainSplit, BorderLayout.CENTER);

        // HOROS-2024.04.07 準拠: 強制的に 50:50 に初期化する
        SwingUtilities.invokeLater(() -> {
            topSplit.setDividerLocation(0.5);
            bottomSplit.setDividerLocation(0.5);
            mainSplit.setDividerLocation(0.5);
        });

        contentPane.revalidate();
        contentPane.repaint();
        updateAllViews(true);
    }

    /**
     * クロスヘア（注視点）の移動
     * 2D Orthogonal MPR の場合、画像は固定したまま、クロスヘアのみを動かす。
     * 3D MPR / Curved MPR の場合は画像もその位置に追従（パン）させる。
     */
    public void updatePosition_rel(MPRView.Direction source, double dx, double dy) {
        if (source == MPRView.Direction.THREE_D)
            return;

        MPRView view = null;
        switch (source) {
            case AXIAL:
                view = axialView;
                break;
            case SAGITTAL:
                view = sagittalView;
                break;
            case CORONAL:
                view = coronalView;
                break;
            default:
                break;
        }
        if (view == null)
            return;

        double[][] basis = view.getSamplingBasis_forMovement();
        double[] vx = basis[1], vy = basis[2];

        double spX = getSpacing(source, 0);
        double spY = getSpacing(source, 1);

        if (currentLayout == LayoutType.ORTHOGONAL) {
            // 2D Orthogonal MPR: 画像は固定し、クロスヘア（注視点）をドラッグ方向に動かす
            curX += (dx * spX) * vx[0] + (dy * spY) * vy[0];
            curY += (dx * spX) * vx[1] + (dy * spY) * vy[1];
            curZ += (dx * spX) * vx[2] + (dy * spY) * vy[2];
        } else {
            // 3D MPR / Curved MPR: 画像自体をドラッグ方向に動かす（パン操作）
            // 画像を右に動かすには、サンプリング中心を「左（マイナス）」に移動させる必要がある
            curX -= (dx * spX) * vx[0] + (dy * spY) * vy[0];
            curY -= (dx * spX) * vx[1] + (dy * spY) * vy[1];
            curZ -= (dx * spX) * vx[2] + (dy * spY) * vy[2];
            volCX = curX;
            volCY = curY;
            volCZ = curZ;
        }

        updateAllViews(true);
    }

    /**
     * 指定された世界座標を新しい注視点（クロスヘア位置）に設定します。
     * レイアウトが ORTHOGONAL の場合、画像自体の表示位置（volC）は変更せず、
     * 3D/Curved MPR の場合は画像もその位置に追従（パン）させます。
     */
    public void setFocusPosition(double x, double y, double z) {
        this.curX = x;
        this.curY = y;
        this.curZ = z;
        if (currentLayout == LayoutType.THREE_D_MPR || currentLayout == LayoutType.CURVED_MPR) {
            this.volCX = x;
            this.volCY = y;
            this.volCZ = z;
        }

        if (currentLayout == LayoutType.CURVED_MPR) {
            // Curved MPR では、中心線の変更がない限りメイン画像は再生成不要。
            // ただしサムネイルは断面が変わるので再生成が必要。
            updateAllViews(false);
            for (MPRView v : thumbnailViews) {
                v.triggerUpdate(true);
            }
        } else {
            updateAllViews(true);
        }
    }

    /**
     * 画像のパン操作（画像とクロスヘアを同時に移動）
     */
    public void updatePan_rel(MPRView.Direction source, double dx, double dy) {
        if (source == MPRView.Direction.THREE_D)
            return;

        MPRView view = null;
        switch (source) {
            case AXIAL:
                view = axialView;
                break;
            case SAGITTAL:
                view = sagittalView;
                break;
            case CORONAL:
                view = coronalView;
                break;
            default:
                break;
        }
        if (view == null)
            return;

        double[][] basis = view.getSamplingBasis_forMovement();
        double[] vx = basis[1], vy = basis[2];

        double spX = getSpacing(source, 0);
        double spY = getSpacing(source, 1);

        // HOROS-20240407 準拠: パン操作（画像全体の移動）
        // マウスを右にドラッグした際に画像を右へ動かすため、サンプリング中心を「左（マイナス）」へずらす。
        volCX -= (dx * spX) * vx[0] + (dy * spY) * vy[0];
        volCY -= (dx * spX) * vx[1] + (dy * spY) * vy[1];
        volCZ -= (dx * spX) * vx[2] + (dy * spY) * vy[2];

        curX -= (dx * spX) * vx[0] + (dy * spY) * vy[0];
        curY -= (dx * spX) * vx[1] + (dy * spY) * vy[1];
        curZ -= (dx * spX) * vx[2] + (dy * spY) * vy[2];

        updateAllViews();
    }

    /**
     * マウスホイールなどによるスライス移動
     */
    public void updateSlicePosition(MPRView.Direction dir, double delta) {
        // 現在のビューの法線ベクトル (n) 方向に移動
        double[] n;
        switch (dir) {
            case AXIAL:
                n = vA;
                break;
            case SAGITTAL:
                n = vS;
                break;
            case CORONAL:
                n = vC;
                break;
            default:
                return;
        }

        // 断面の中心と注視点の両方を法線方向に移動
        volCX += delta * n[0];
        volCY += delta * n[1];
        volCZ += delta * n[2];

        curX += delta * n[0];
        curY += delta * n[1];
        curZ += delta * n[2];

        updateAllViews();
    }

    /**
     * 注視点（中心点）を特定の位置へジャンプさせる
     */
    public void setCenterPosition(double x, double y, double z) {
        this.curX = x;
        this.curY = y;
        this.curZ = z;
        updateAllViews();
    }

    public void rotateMPR(MPRView.Direction dir, double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double[] n;
        switch (dir) {
            case AXIAL:
                n = vA;
                break;
            case SAGITTAL:
                n = vS;
                break;
            case CORONAL:
                n = vC;
                break;
            default:
                return;
        }

        if (n == vA) {
            rotateVector(vS, n, cos, sin);
            rotateVector(vC, n, cos, sin);
        } else if (n == vS) {
            rotateVector(vC, n, cos, sin);
            rotateVector(vA, n, cos, sin);
        } else if (n == vC) {
            rotateVector(vS, n, cos, sin);
            rotateVector(vA, n, cos, sin);
        }
        updateAllViews();
    }

    private void rotateVector(double[] v, double[] n, double cos, double sin) {
        double nxv_x = n[1] * v[2] - n[2] * v[1];
        double nxv_y = n[2] * v[0] - n[0] * v[2];
        double nxv_z = n[0] * v[1] - n[1] * v[0];
        double dot = n[0] * v[0] + n[1] * v[1] + n[2] * v[2];
        v[0] = v[0] * cos + nxv_x * sin + n[0] * dot * (1 - cos);
        v[1] = v[1] * cos + nxv_y * sin + n[1] * dot * (1 - cos);
        v[2] = v[2] * cos + nxv_z * sin + n[2] * dot * (1 - cos);
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= l;
        v[1] /= l;
        v[2] /= l;
    }

    public double getInterpolatedVoxel(double px, double py, double pz) {
        // HOROS-20240407準拠: 世界座標(mm)からインデックス座標への変換 (Orientationを考慮)
        double dx = px - originX;
        double dy = py - originY;
        double dz = pz - originZ;

        // I = M^T * D / Spacing
        double ix = (dx * orientation[0] + dy * orientation[1] + dz * orientation[2]) / spacingX;
        double iy = (dx * orientation[3] + dy * orientation[4] + dz * orientation[5]) / spacingY;
        double iz = (dx * orientation[6] + dy * orientation[7] + dz * orientation[8]) / spacingZ;

        return getInterpolatedVoxelByIndex(ix, iy, iz);
    }

    private double getInterpolatedVoxelByIndex(double x, double y, double z) {
        if (this.volume == null) return 0;
        
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double val;

        if (x0 < 0 || x0 >= dimX - 1 || y0 < 0 || y0 >= dimY - 1 || z0 < 0 || z0 >= dimZ - 1) {
            if (x0 >= 0 && x0 < dimX && y0 >= 0 && y0 < dimY && z0 >= 0 && z0 < dimZ) {
                val = (isSigned ? (float) volume[z0][y0][x0] : (float) (volume[z0][y0][x0] & 0xFFFF));
            } else {
                return -2000;
            }
        } else {
            double fx = x - x0;
            double fy = y - y0;
            double fz = z - z0;

            // パフォーマンス最適化: 2D Orthogonal MPR など、座標がほぼ整数の場合は補間をスキップする
            if (fx < 0.001 && fy < 0.001 && fz < 0.001) {
                val = (isSigned ? volume[z0][y0][x0] : (volume[z0][y0][x0] & 0xFFFF));
            } else {
                double v000 = (isSigned ? volume[z0][y0][x0] : (volume[z0][y0][x0] & 0xFFFF));
                double v100 = (isSigned ? volume[z0][y0][x0 + 1] : (volume[z0][y0][x0 + 1] & 0xFFFF));
                double v010 = (isSigned ? volume[z0][y0 + 1][x0] : (volume[z0][y0 + 1][x0] & 0xFFFF));
                double v110 = (isSigned ? volume[z0][y0 + 1][x0 + 1] : (volume[z0][y0 + 1][x0 + 1] & 0xFFFF));
                double v001 = (isSigned ? volume[z0 + 1][y0][x0] : (volume[z0 + 1][y0][x0] & 0xFFFF));
                double v101 = (isSigned ? volume[z0 + 1][y0][x0 + 1] : (volume[z0 + 1][y0][x0 + 1] & 0xFFFF));
                double v011 = (isSigned ? volume[z0 + 1][y0 + 1][x0] : (volume[z0 + 1][y0 + 1][x0] & 0xFFFF));
                double v111 = (isSigned ? volume[z0 + 1][y0 + 1][x0 + 1] : (volume[z0 + 1][y0 + 1][x0 + 1] & 0xFFFF));

                double v00 = v000 * (1 - fx) + v100 * fx;
                double v01 = v001 * (1 - fx) + v101 * fx;
                double v10 = v010 * (1 - fx) + v110 * fx;
                double v11 = v011 * (1 - fx) + v111 * fx;
                double vz0 = v00 * (1 - fy) + v10 * fy;
                double vz1 = v01 * (1 - fy) + v11 * fy;
                val = vz0 * (1 - fz) + vz1 * fz;
            }
        }
        // HOROS-20240407準拠: 物理的な値（HU等）に変換するためにRescale Slope/Interceptを適用する
        return val * rescaleSlope + rescaleIntercept;
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

    public double getCurX() {
        return curX;
    }

    public double getCurY() {
        return curY;
    }

    public double getCurZ() {
        return curZ;
    }

    public String getPatientName() {
        if (pixList != null && !pixList.isEmpty()) {
            DicomPix first = pixList.get(0);
            if (first.getImageObj() != null && first.getImageObj().getSeries() != null
                    && first.getImageObj().getSeries().getStudy() != null) {
                return first.getImageObj().getSeries().getStudy().getName();
            }
        }
        return "Unknown Patient";
    }

    public String getPatientAgeSex() {
        if (pixList != null && !pixList.isEmpty()) {
            DicomPix first = pixList.get(0);
            if (first.getImageObj() != null && first.getImageObj().getSeries() != null
                    && first.getImageObj().getSeries().getStudy() != null) {
                DicomStudy study = first.getImageObj().getSeries().getStudy();
                String age = DicomStudy.yearOldFromDateOfBirth(study.getDateOfBirth());
                String sex = study.getPatientSex() != null ? study.getPatientSex() : "";
                return String.format("%s, %s", age, sex);
            }
        }
        return "";
    }

    public String getPatientID() {
        if (pixList != null && !pixList.isEmpty()) {
            DicomPix first = pixList.get(0);
            if (first.getImageObj() != null && first.getImageObj().getSeries() != null
                    && first.getImageObj().getSeries().getStudy() != null) {
                return first.getImageObj().getSeries().getStudy().getPatientID();
            }
        }
        return "000000";
    }

    public String getStudyInfo() {
        if (pixList != null && !pixList.isEmpty()) {
            DicomPix first = pixList.get(0);
            if (first.getImageObj() != null && first.getImageObj().getSeries() != null
                    && first.getImageObj().getSeries().getStudy() != null) {
                return first.getImageObj().getSeries().getStudy().getStudyName();
            }
        }
        return "Unknown Study";
    }

    public String getSeriesInfo() {
        if (pixList != null && !pixList.isEmpty()) {
            DicomPix first = pixList.get(0);
            if (first.getImageObj() != null && first.getImageObj().getSeries() != null) {
                String desc = first.getImageObj().getSeries().getSeriesDescription();
                return desc != null ? desc : "Unknown Series";
            }
        }
        return "Unknown Series";
    }

    public String getDateTimeInfo() {
        if (pixList != null && !pixList.isEmpty()) {
            DicomPix first = pixList.get(0);
            if (first.getImageObj() != null && first.getImageObj().getDate() != null) {
                java.time.LocalDateTime dt = first.getImageObj().getDate();
                return dt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
            }
        }
        return "0000/00/00, 00:00:00";
    }

    public int getDimOriginalX() {
        return dimX;
    }

    public int getDimOriginalY() {
        return dimY;
    }

    public int getDimOriginalZ() {
        return dimZ;
    }

    public double getVoxelValueAtFocus() {
        return getInterpolatedVoxel(curX, curY, curZ);
    }

    public void updateWindow(float deltaL, float deltaW) {
        this.windowLevel += deltaL;
        this.windowWidth += deltaW;
        if (this.windowWidth < 1)
            this.windowWidth = 1;

        // HOROS-20240407準拠: 3D MPRのWL/WWを親の2Dビューワーと同期させる
        if (viewer2D != null && viewer2D.getImageView() != null) {
            DCMView dcmView = viewer2D.getImageView();
            if (dcmView.getCurDCM() != null) {
                dcmView.getCurDCM().setWindowLevelWidth(this.windowLevel, this.windowWidth);
                dcmView.sendSyncMessageWLWW(this.windowLevel, this.windowWidth);
                dcmView.repaint();
            }
        }

        updateAllViews();
    }

    public void updateAllViews() {
        updateAllViews(true);
    }

    public void updateAllViews(boolean force) {
        if (axialView != null)
            axialView.triggerUpdate(force);
        if (sagittalView != null)
            sagittalView.triggerUpdate(force);
        if (coronalView != null)
            coronalView.triggerUpdate(force);
        if (threeDView != null)
            threeDView.triggerUpdate(force);
        for (MPRView v : thumbnailViews) {
            v.triggerUpdate(force);
        }
    }

    public double getCrosshairX_d(MPRView.Direction dir) {
        return getDim(dir, 0) / 2.0;
    }

    public double getCrosshairY_d(MPRView.Direction dir) {
        return getDim(dir, 1) / 2.0;
    }

    public double getvX() {
        return curX;
    }

    public double getvY() {
        return curY;
    }

    public double getvZ() {
        return curZ;
    }

    /**
     * 現在の注視点(World)をインデックス空間の座標に変換して返します。
     * HOROS-20240407準拠
     */
    public int getIndexX() {
        double dx = curX - originX, dy = curY - originY, dz = curZ - originZ;
        return (int) Math.round((dx * orientation[0] + dy * orientation[1] + dz * orientation[2]) / spacingX);
    }

    public int getIndexY() {
        double dx = curX - originX, dy = curY - originY, dz = curZ - originZ;
        return (int) Math.round((dx * orientation[3] + dy * orientation[4] + dz * orientation[5]) / spacingY);
    }

    public int getIndexZ() {
        double dx = curX - originX, dy = curY - originY, dz = curZ - originZ;
        return (int) Math.round((dx * orientation[6] + dy * orientation[7] + dz * orientation[8]) / spacingZ);
    }

    public double getVolCX() {
        return volCX;
    }

    public double getVolCY() {
        return volCY;
    }

    public double getVolCZ() {
        return volCZ;
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

    // --- Data Accessors for MPRView ---

    public int getDim(MPRView.Direction dir, int axis) {
        switch (dir) {
            case AXIAL:
                return (axis == 0) ? dimX : dimY;
            case SAGITTAL:
                return (axis == 0) ? dimY : dimZ;
            case CORONAL:
                return (axis == 0) ? dimX : dimZ;
            case THREE_D:
                return 256; // 3Dプレビュー用固定サイズ
        }
        return 0;
    }

    public double getSpacing(MPRView.Direction dir, int axis) {
        switch (dir) {
            case AXIAL:
                return (axis == 0) ? spacingX : spacingY;
            case SAGITTAL:
                return (axis == 0) ? spacingY : spacingZ;
            case CORONAL:
                return (axis == 0) ? spacingX : spacingZ;
            default:
                return 1.0;
        }
    }

    /**
     * 指定された断面の物理的なアスペクト比 (Width / Height) を取得
     * HOROS-20240407準拠
     */
    public double getAspectRatio(MPRView.Direction dir) {
        double px = (spacingX > 0) ? spacingX : 1.0;
        double py = (spacingY > 0) ? spacingY : 1.0;
        double pz = (spacingZ > 0) ? spacingZ : 1.0;

        switch (dir) {
            case AXIAL:
                return (dimX * px) / (dimY * py);
            case SAGITTAL:
                return (dimY * py) / (dimZ * pz);
            case CORONAL:
                return (dimX * px) / (dimZ * pz);
            case THREE_D:
                return 1.0;
        }
        return 1.0;
    }

    /**
     * 各スライス面に対応する交差線の色を取得
     * 2D Orthogonal MPR (LayoutType.ORTHOGONAL) の場合は緑系、
     * 3D MPR (LayoutType.THREE_D_MPR) の場合は各面固有の色（Purple/Blue/Orange）を使用
     */
    public Color getCrosshairColor(MPRView.Direction dir, boolean isVertical) {
        // Orthogonalモードの場合は緑一色
        if (currentLayout == LayoutType.ORTHOGONAL) {
            return Color.GREEN;
        }

        // 3D MPR / Curved MPR 共通の色定義
        final Color AXIAL_COLOR = new Color(255, 0, 255); // Purple (Magenta)
        final Color CORONAL_COLOR = new Color(0, 160, 255); // Blue
        final Color SAGITTAL_COLOR = new Color(255, 128, 0); // Orange

        switch (dir) {
            case AXIAL:
                // Axialビューで見えるのは: 垂直=Sagittal面, 水平=Coronal面
                return isVertical ? SAGITTAL_COLOR : CORONAL_COLOR;
            case SAGITTAL:
                // Sagittalビューで見えるのは: 垂直=Coronal面, 水平=Axial面
                return isVertical ? CORONAL_COLOR : AXIAL_COLOR;
            case CORONAL:
                // Coronalビューで見えるのは: 垂直=Sagittal面, 水平=Axial面
                return isVertical ? SAGITTAL_COLOR : AXIAL_COLOR;
            default:
                return Color.GREEN;
        }
    }

    /**
     * ハイライト（アクティブビュー）の枠色を取得
     */
    public Color getHighlightedViewColor(MPRView.Direction dir) {
        if (currentLayout == LayoutType.ORTHOGONAL) {
            return Color.RED;
        }
        switch (dir) {
            case AXIAL:
                return new Color(255, 0, 255); // Purple
            case CORONAL:
                return new Color(0, 160, 255); // Blue
            case SAGITTAL:
                return new Color(255, 128, 0); // Orange
            case THREE_D:
                return Color.WHITE; // 3D View
            default:
                return Color.RED;
        }
    }

    public String getPatientHeader() {
        if (pixList != null && !pixList.isEmpty()) {
            DicomPix first = pixList.get(0);
            if (first.getImageObj() != null && first.getImageObj().getSeries() != null
                    && first.getImageObj().getSeries().getStudy() != null) {
                DicomStudy study = first.getImageObj().getSeries().getStudy();
                String age = DicomStudy.yearOldFromDateOfBirth(study.getDateOfBirth());
                return String.format("%s ( %s )", study.getPatientID(), age);
            }
        }
        return "99999830 ( 12 y )";
    }

    public String getSeriesNumber() {
        if (pixList != null && !pixList.isEmpty()) {
            DicomPix first = pixList.get(0);
            if (first.getImageObj() != null && first.getImageObj().getSeries() != null) {
                Integer sn = first.getImageObj().getSeries().getSeriesNumber();
                return sn != null ? sn.toString() : "201";
            }
        }
        return "201";
    }

    public String getStudyDate() {
        if (pixList != null && !pixList.isEmpty()) {
            DicomPix first = pixList.get(0);
            if (first.getImageObj() != null && first.getImageObj().getSeries() != null
                    && first.getImageObj().getSeries().getStudy() != null) {
                java.time.LocalDateTime ldt = first.getImageObj().getSeries().getStudy().getDate();
                if (ldt != null) {
                    return String.format("%04d/%02d/%02d, %02d:%02d:%02d",
                            ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(),
                            ldt.getHour(), ldt.getMinute(), ldt.getSecond());
                }
            }
        }
        return "2025/02/08, 15:05:04";
    }

    public float getWindowLevel() {
        return windowLevel;
    }

    public float getWindowWidth() {
        return windowWidth;
    }

    public float getRescaleSlope() {
        return rescaleSlope;
    }

    public float getRescaleIntercept() {
        return rescaleIntercept;
    }

    public boolean getIsInverse() {
        return isInverse;
    }

    public boolean getIsSigned() {
        return isSigned;
    }

    // I'll add the new methods at the end of the class.

    public short[][][] getVolume() {
        return volume;
    }

    public double getRotationX() {
        return rotateX;
    }

    public double getRotationY() {
        return rotateY;
    }

    public void updateRotation(double dx, double dy) {
        this.rotateY += dx * (Math.PI / 360.0);
        this.rotateX += dy * (Math.PI / 360.0);
        updateAllViews(true);
    }

    public static void openMPR(ViewerController viewer) {
        openMPR(viewer, LayoutType.THREE_D_MPR);
    }

    public static void openOrthogonal(ViewerController viewer) {
        openMPR(viewer, LayoutType.ORTHOGONAL);
    }

    /**
     * 曲線上の始点からの距離(mm)を指定して、注視点を移動する
     */
    public void setFocusByCurveDistance(double dist) {
        if (curvePoints.size() < 2)
            return;
        double currentDist = 0;
        for (int i = 0; i < curvePoints.size() - 1; i++) {
            double[] p1 = curvePoints.get(i), p2 = curvePoints.get(i + 1);
            double d = Math.sqrt(Math.pow(p2[0] - p1[0], 2) + Math.pow(p2[1] - p1[1], 2) + Math.pow(p2[2] - p1[2], 2));
            if (currentDist + d >= dist || i == curvePoints.size() - 2) {
                double t = (dist - currentDist) / d;
                t = Math.max(0, Math.min(1, t));
                double nx = p1[0] + (p2[0] - p1[0]) * t;
                double ny = p1[1] + (p2[1] - p1[1]) * t;
                double nz = p1[2] + (p2[2] - p1[2]) * t;
                setFocusPosition(nx, ny, nz);
                return;
            }
            currentDist += d;
        }
    }

    private static void openMPR(ViewerController viewer, LayoutType layout) {
        if (viewer == null || viewer.getImageView() == null || viewer.getImageView().getDcmPixList() == null
                || viewer.getImageView().getDcmPixList().isEmpty()) {
            JOptionPane.showMessageDialog(null, "No images found for MPR reconstruction.", "3D MPR Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                MPRController mpr = new MPRController(viewer, layout);
                mpr.setVisible(true);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, "Failed to start 3D MPR: " + ex.getMessage(), "3D MPR Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
