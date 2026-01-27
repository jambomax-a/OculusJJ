package com.jj.dicomviewer.ui;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import com.jj.dicomviewer.model.DicomPix;

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

    // 現在の注視点 (volume coordinates)
    private int curX, curY, curZ;

    private float windowLevel = 40;
    private float windowWidth = 400;

    private float rescaleSlope = 1.0f;
    private float rescaleIntercept = 0.0f;
    private boolean isSigned = false;
    private boolean isInverse = false;

    public enum LayoutType {
        ORTHOGONAL,
        THREE_D_MPR
    }

    private LayoutType currentLayout = LayoutType.THREE_D_MPR;

    public MPRController(ViewerController viewer2D) {
        this(viewer2D, LayoutType.THREE_D_MPR);
    }

    public MPRController(ViewerController viewer2D, LayoutType layout) {
        super(layout == LayoutType.ORTHOGONAL ? "Orthogonal MPR" : "3D MPR");
        this.viewer2D = viewer2D;
        this.currentLayout = layout;

        initializeData();
        initializeUI();
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

        // HOROS-20240407準拠: Slice spacingの計算
        // Slice Thicknessよりも、実際のSlice間距離（Slice Locationの差）を優先する
        if (pixList.size() > 1) {
            double loc1 = pixList.get(0).getSliceLocation();
            double loc2 = pixList.get(1).getSliceLocation();
            spacingZ = Math.abs(loc2 - loc1);
        }
        if (spacingZ <= 0) {
            spacingZ = first.getSliceThickness();
        }
        if (spacingZ <= 0) {
            spacingZ = 1.0;
        }

        System.out.println("[DEBUG] MPR Spacing: X=" + spacingX + ", Y=" + spacingY + ", Z=" + spacingZ);

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

        System.out.println("[DEBUG] MPR Window: Level=" + windowLevel + ", Width=" + windowWidth);

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
                System.err.println("[ERROR] MPR Slice " + z + " failed. Reason: "
                        + (src == null ? "null data" : "size mismatch " + src.length + " vs " + (dimX * dimY)));
                if (z > 0) {
                    // 前のスライスをコピーして「空隙」を目立たなくする
                    for (int y = 0; y < dimY; y++) {
                        System.arraycopy(volume[z - 1][y], 0, volume[z][y], 0, dimX);
                    }
                }
            }
        }

        // 初期位置を中心にする
        curX = dimX / 2;
        curY = dimY / 2;
        curZ = dimZ / 2;
    }

    private void initializeUI() {
        // HOROS-20240407準拠: 3D MPRウィンドウは通常画面いっぱいに広がる
        setExtendedState(JFrame.MAXIMIZED_BOTH); // 画面いっぱいに表示
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // HOROS-20240407準拠: 独立したウィンドウとしてメニューを持たせる
        BrowserController browser = BrowserController.currentBrowser();
        if (browser != null && browser.getJMenuBar() != null) {
            javax.swing.JMenuBar menuBar = ViewerController.replicateMenuBar(browser.getJMenuBar());
            menuBar.setOpaque(true);
            menuBar.setBackground(new Color(240, 240, 240));
            setJMenuBar(menuBar);
        }

        setLayout(new BorderLayout());

        // HOROS-20240407準拠: MPR.xib 相当のツールバー
        JToolBar mprToolbar = new JToolBar();
        mprToolbar.setFloatable(false);
        mprToolbar.setBackground(new Color(240, 240, 240));

        // ツールボタン群 (MPR.xib 準拠)
        JButton levelsButton = new JButton("Levels");
        mprToolbar.add(levelsButton);
        mprToolbar.add(new JButton("Move"));
        mprToolbar.add(new JButton("Zoom"));
        mprToolbar.add(new JButton("Rotate"));
        mprToolbar.add(new JButton("Stack"));
        mprToolbar.addSeparator();

        // ROIツール
        mprToolbar.add(new JButton("Length"));
        mprToolbar.add(new JButton("Oval"));
        mprToolbar.add(new JButton("Angle"));
        mprToolbar.addSeparator();

        // 特殊ツール
        mprToolbar.add(new JButton("Cross"));

        mprToolbar.addSeparator();

        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> {
            curX = dimX / 2;
            curY = dimY / 2;
            curZ = dimZ / 2;
            updateAllViews();
        });
        mprToolbar.add(resetButton);

        mprToolbar.addSeparator();

        // レイアウト切り替え
        JButton orthogonalButton = new JButton("Orthogonal");
        orthogonalButton.setToolTipText("3 Vertical slices (Side-by-side)");
        orthogonalButton.addActionListener(e -> setLayoutOrthogonal());
        mprToolbar.add(orthogonalButton);

        JButton threeDMPRButton = new JButton("3D MPR");
        threeDMPRButton.setToolTipText("Left (Top/Bottom), Right (Full height)");
        threeDMPRButton.addActionListener(e -> setLayout3DMPR());
        mprToolbar.add(threeDMPRButton);

        mprToolbar.addSeparator();
        mprToolbar.add(new JButton("Thick Slab"));
        mprToolbar.add(new JButton("Export"));
        add(mprToolbar, BorderLayout.NORTH);

        // MPRビューの初期化
        axialView = new MPRView(this, MPRView.Direction.AXIAL);
        sagittalView = new MPRView(this, MPRView.Direction.SAGITTAL);
        coronalView = new MPRView(this, MPRView.Direction.CORONAL);

        // コンストラクタで指定されたレイアウトを設定
        if (currentLayout == LayoutType.ORTHOGONAL) {
            setLayoutOrthogonal();
        } else {
            setLayout3DMPR();
        }

        updateAllViews();

        // 強制的に初期描画を促す
        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }

    private JSplitPane mainSplit;

    /**
     * Orthogonal MPR レイアウト: 縦長3枚を横並びに配置
     */
    private void setLayoutOrthogonal() {
        if (mainSplit != null)
            remove(mainSplit);
        setTitle("Orthogonal MPR");

        JSplitPane split2 = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sagittalView, coronalView);
        split2.setDividerLocation(0.5); // 50%
        split2.setResizeWeight(0.5);

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, axialView, split2);
        mainSplit.setDividerLocation(0.33); // 33%
        mainSplit.setResizeWeight(0.33);

        add(mainSplit, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * 3D MPR レイアウト: 左側に上下2枚、右側に縦長1枚を配置
     * HOROS-20240407 MPR.xib 準拠
     */
    private void setLayout3DMPR() {
        if (mainSplit != null)
            remove(mainSplit);
        setTitle("3D MPR");

        // 左側スプリット (上下分割)
        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, axialView, sagittalView);
        leftSplit.setDividerLocation(400);
        leftSplit.setResizeWeight(0.5);

        // メインスプリット (左右分割)
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, coronalView);
        mainSplit.setDividerLocation(600);
        mainSplit.setResizeWeight(0.66);

        add(mainSplit, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void setLayoutVertical() {
        if (mainSplit != null)
            remove(mainSplit);

        JSplitPane split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sagittalView, coronalView);
        split2.setDividerLocation(300);
        split2.setResizeWeight(0.5);

        mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, axialView, split2);
        mainSplit.setDividerLocation(300);
        mainSplit.setResizeWeight(0.33);

        add(mainSplit, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    public void updatePosition(MPRView.Direction source, int x, int y) {
        switch (source) {
            case AXIAL:
                curX = clamp(x, 0, dimX - 1);
                curY = clamp(y, 0, dimY - 1);
                break;
            case SAGITTAL:
                // Sagittal view: Horizontal is Coronal(Y), Vertical is Axial(Z)
                curY = clamp(x, 0, dimY - 1);
                curZ = clamp(y, 0, dimZ - 1);
                break;
            case CORONAL:
                // Coronal view: Horizontal is Sagittal(X), Vertical is Axial(Z)
                curX = clamp(x, 0, dimX - 1);
                curZ = clamp(y, 0, dimZ - 1);
                break;
        }
        updateAllViews();
    }

    /**
     * マウスホイールなどによるスライス移動
     */
    public void updateSlicePosition(MPRView.Direction dir, int delta) {
        switch (dir) {
            case AXIAL:
                curZ = clamp(curZ + delta, 0, dimZ - 1);
                break;
            case SAGITTAL:
                curX = clamp(curX + delta, 0, dimX - 1);
                break;
            case CORONAL:
                curY = clamp(curY + delta, 0, dimY - 1);
                break;
        }
        updateAllViews();
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * Window Width / Level を更新
     * HOROS-20240407 [DCMView changeWLWW:] 準拠
     */
    public void updateWindow(float deltaL, float deltaW) {
        this.windowLevel += deltaL;
        this.windowWidth += deltaW;
        if (this.windowWidth < 1)
            this.windowWidth = 1;
        updateAllViews();
    }

    private void updateAllViews() {
        axialView.setSliceIndex(curZ);
        sagittalView.setSliceIndex(curX);
        coronalView.setSliceIndex(curY);
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
        }
        return 0;
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
        }
        return 1.0;
    }

    public short[] getSliceData(MPRView.Direction dir, int index) {
        int w = getDim(dir, 0);
        int h = getDim(dir, 1);
        short[] data = new short[w * h];

        switch (dir) {
            case AXIAL:
                for (int y = 0; y < h; y++) {
                    System.arraycopy(volume[index][y], 0, data, y * w, w);
                }
                break;
            case SAGITTAL:
                // Sagittal (YZ plane at X=index)
                for (int z = 0; z < h; z++) {
                    for (int y = 0; y < w; y++) {
                        data[z * w + y] = volume[z][y][index];
                    }
                }
                break;
            case CORONAL:
                // Coronal (XZ plane at Y=index)
                for (int z = 0; z < h; z++) {
                    for (int x = 0; x < w; x++) {
                        data[z * w + x] = volume[z][index][x];
                    }
                }
                break;
        }
        return data;
    }

    public int getCrosshairX(MPRView.Direction dir) {
        switch (dir) {
            case AXIAL:
                return curX;
            case SAGITTAL:
                return curY;
            case CORONAL:
                return curX;
        }
        return 0;
    }

    public int getCrosshairY(MPRView.Direction dir) {
        switch (dir) {
            case AXIAL:
                return curY;
            case SAGITTAL:
                return curZ;
            case CORONAL:
                return curZ;
        }
        return 0;
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

    public boolean getIsSigned() {
        return isSigned;
    }

    public boolean getIsInverse() {
        return isInverse;
    }

    public static void openMPR(ViewerController viewer) {
        openMPR(viewer, LayoutType.THREE_D_MPR);
    }

    public static void openOrthogonal(ViewerController viewer) {
        openMPR(viewer, LayoutType.ORTHOGONAL);
    }

    private static void openMPR(ViewerController viewer, LayoutType layout) {
        SwingUtilities.invokeLater(() -> {
            MPRController mpr = new MPRController(viewer, layout);
            mpr.setVisible(true);
        });
    }
}
