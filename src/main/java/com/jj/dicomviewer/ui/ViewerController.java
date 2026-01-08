package com.jj.dicomviewer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import com.jj.dicomviewer.model.DicomImage;
import com.jj.dicomviewer.model.DicomPix;
import com.jj.dicomviewer.model.DicomSeries;
import com.jj.dicomviewer.model.DicomStudy;

/**
 * ViewerController - ビューアコントローラー
 * 
 * HOROS-20240407のViewerControllerをJava Swingに移植
 * HOROS-20240407準拠: ViewerController.h, ViewerController.m
 */
public class ViewerController extends JFrame {

    // HOROS-20240407準拠: ViewerController.m get2DViewers
    // 開いているすべてのViewerControllerを追跡
    private static List<ViewerController> allViewers = new ArrayList<>();

    // HOROS-20240407準拠: ViewerController.m 4887-4900行目 - 色定義
    // + (NSColor*)_selectedItemColor { return [NSColor
    // colorWithCalibratedRed:252./255 green:177./255 blue:141./255 alpha:1]; }
    private static final Color SELECTED_ITEM_COLOR = new Color(252, 177, 141); // red
    // + (NSColor*)_fusionedItemColor { return [NSColor
    // colorWithCalibratedRed:195./255 green:249./255 blue:145./255 alpha:1]; }
    private static final Color FUSED_ITEM_COLOR = new Color(195, 249, 145); // green
    // + (NSColor*)_openItemColor { return [NSColor colorWithCalibratedRed:249./255
    // green:240./255 blue:140./255 alpha:1]; }
    private static final Color OPEN_ITEM_COLOR = new Color(249, 240, 140); // yellow
    // + (NSColor*)_differentStudyColor { return [NSColor
    // colorWithCalibratedRed:0.55 green:0.55 blue:0.55 alpha:1]; }
    private static final Color DIFFERENT_STUDY_COLOR = new Color(140, 140, 140); // gray

    // HOROS-20240407準拠: ViewerController.h 38行目
    // #define MAX4D 500
    private static final int MAX4D = 500;

    // HOROS-20240407準拠: ViewerController.m 全てのViewerControllerインスタンスを管理
    // + (NSMutableArray*) getDisplayed2DViewers
    private static final List<ViewerController> displayedViewers = new java.util.concurrent.CopyOnWriteArrayList<>();

    // HOROS-20240407準拠: ViewerController.h 279-283行目
    // NSMutableArray *fileList[ MAX4D];
    // NSMutableArray<DCMPix *> *pixList[MAX4D];
    // NSData *volumeData[ MAX4D];
    @SuppressWarnings("unchecked")
    private List<Object>[] fileList = new List[MAX4D];
    @SuppressWarnings("unchecked")
    private List<Object>[] pixList = new List[MAX4D];
    private byte[][] volumeData = new byte[MAX4D][];

    // HOROS-20240407準拠: ViewerController.h 284行目
    // short curMovieIndex, maxMovieIndex, orientationVector;
    private short curMovieIndex = 0;
    private short maxMovieIndex = 1;

    // HOROS-20240407準拠: ViewerController.h 306行目
    // BOOL windowWillClose;
    private boolean windowWillClose = false;

    // HOROS-20240407準拠: ViewerController.h 732行目
    // - (NSString*) studyInstanceUID;
    private String studyInstanceUID;

    /**
     * studyInstanceUIDを取得
     * HOROS-20240407準拠: ViewerController.h 732行目
     */
    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    /**
     * 現在のStudyを取得
     * HOROS-20240407準拠: ViewerController.m currentStudy
     */
    public DicomStudy getCurrentStudy() {
        if (fileList != null && fileList.length > 0 && fileList[curMovieIndex] != null
                && !fileList[curMovieIndex].isEmpty()) {
            Object firstObj = fileList[curMovieIndex].get(0);
            if (firstObj instanceof DicomImage) {
                DicomSeries series = ((DicomImage) firstObj).getSeries();
                if (series != null) {
                    return series.getStudy();
                }
            }
        }
        return null;
    }

    /**
     * 2Dビューワーかどうか
     * HOROS-20240407準拠: ViewerController.m 21659行目
     */
    public boolean is2DViewer() {
        // HOROS-20240407準拠: ViewerController.m 21661行目
        // return YES;
        return true;
    }

    /**
     * 表示されている全ての2D ViewerControllerを取得
     * HOROS-20240407準拠: ViewerController.m getDisplayed2DViewers
     */
    public static List<ViewerController> getDisplayed2DViewers() {
        // HOROS-20240407準拠: 2Dビューワーのみをフィルタリングしてリストを返す
        // CopyOnWriteArrayListなので、新しいリストを作成して返す
        List<ViewerController> result = new java.util.ArrayList<>();
        for (ViewerController viewer : displayedViewers) {
            if (viewer.is2DViewer() && viewer.isVisible()) {
                result.add(viewer);
            }
        }
        return result;
    }

    /**
     * 複数のViewerControllerウィンドウを自動的にタイル状に配置
     * HOROS-20240407準拠: AppController.m tileWindows: (4915-5549行目) displayViewers: (4518-4591行目)
     */
    public static void tileWindows() {
        List<ViewerController> viewers = getDisplayed2DViewers();
        if (viewers.isEmpty()) {
            return;
        }

        // HOROS-20240407準拠: 画面サイズを取得 (AppController.m usefullRectForScreen: 4836-4855行目)
        java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        java.awt.GraphicsDevice gd = ge.getDefaultScreenDevice();
        java.awt.Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();

        // タスクバーやメニューバーを考慮した実際の作業領域を取得
        java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
        java.awt.Insets insets = toolkit.getScreenInsets(gd.getDefaultConfiguration());

        int screenX = screenBounds.x + insets.left;
        int screenY = screenBounds.y + insets.top;
        int screenWidth = screenBounds.width - insets.left - insets.right;
        int screenHeight = screenBounds.height - insets.top - insets.bottom;

        int viewerCount = viewers.size();

        // HOROS-20240407準拠: AppController.m 5101-5156行目 - 画面の縦横比を判定
        boolean landscape = (screenWidth / (double) screenHeight > 1.0);
        float landscapeRatio = (screenWidth / (double) screenHeight > 1.7) ? 2.0f : 1.5f;
        float portraitRatio = (screenHeight / (double) screenWidth > 1.7) ? 0.49f : 0.9f;

        // HOROS-20240407準拠: AppController.m 5138-5150行目 - デフォルトの行列を設定
        int rows, columns;
        if (landscape) {
            // 横長スクリーン: 2列×1行から開始
            columns = 2;
            rows = 1;
        } else {
            // 縦長スクリーン: 1列×2行から開始
            columns = 1;
            rows = 2;
        }

        // HOROS-20240407準拠: AppController.m 5159-5203行目 - ウィンドウ数に応じて行列を調整
        if (viewerCount > (rows * columns)) {
            float ratioValue = landscape ? landscapeRatio : portraitRatio;

            // アスペクト比を維持しながらグリッドを拡大
            while (viewerCount > (rows * columns)) {
                float ratio = (float) columns / (float) rows;

                if (ratio > ratioValue) {
                    // 比率が大きすぎる → 行を増やす
                    rows++;
                } else {
                    // 比率が小さすぎる → 列を増やす
                    columns++;
                }
            }

            // HOROS-20240407準拠: 最適化 - 不要な行列を削除
            if (rows * columns > viewerCount && rows * (columns - 1) >= viewerCount) {
                columns--;
            }
            if (rows * columns > viewerCount && (rows - 1) * columns >= viewerCount) {
                rows--;
            }
        }

        System.out.println("ViewerController.tileWindows: tiling " + viewerCount + " windows in " + rows + "x" + columns + " grid (landscape=" + landscape + ")");
        System.out.println("ViewerController.tileWindows: screen=" + screenWidth + "x" + screenHeight);

        // HOROS-20240407準拠: AppController.m displayViewers: 4567-4578行目 - 端数を削除して隙間なく配置
        int temp;
        temp = screenWidth / columns;
        screenWidth = temp * columns;  // 端数を削除

        temp = screenHeight / rows;
        screenHeight = temp * rows;    // 端数を削除

        // ウィンドウのサイズを計算
        int windowWidth = screenWidth / columns;
        int windowHeight = screenHeight / rows;

        System.out.println("ViewerController.tileWindows: adjusted screen=" + screenWidth + "x" + screenHeight + ", window=" + windowWidth + "x" + windowHeight);

        // HOROS-20240407準拠: AppController.m displayViewers: 4563-4589行目 - 各ウィンドウを配置
        int index = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                if (index >= viewerCount) {
                    break;
                }

                ViewerController viewer = viewers.get(index);

                // HOROS-20240407準拠: 位置とサイズを計算
                int x = screenX + col * windowWidth;
                // Javaは上から下なので、rowをそのまま使用（macOSは下から上なので反転が必要）
                int y = screenY + row * windowHeight;

                // HOROS-20240407準拠: ViewerController.m setWindowFrame: 3054-3057行目 - 端数を四捨五入
                x = Math.round(x);
                y = Math.round(y);
                int width = Math.round(windowWidth);
                int height = Math.round(windowHeight);

                // ウィンドウを配置
                viewer.setBounds(x, y, width, height);
                viewer.setVisible(true);
                viewer.toFront();

                System.out.println("ViewerController.tileWindows: viewer[" + index + "] row=" + row + " col=" + col + " at (" + x + "," + y + ") size=" + width + "x" + height);

                index++;
            }
        }
    }

    // HOROS-20240407準拠: ViewerController.h 101行目
    // IBOutlet NSMatrix *previewMatrix;
    // IBOutlet NSScrollView *previewMatrixScrollView;
    private JPanel previewMatrix;
    private JScrollPane previewMatrixScrollView;

    // HOROS-20240407準拠: ViewerController.h 96行目
    // ToolbarPanelController *toolbarPanel;
    public ToolbarPanelController toolbarPanel;

    // HOROS-20240407準拠: ViewerController.m 3399-3420行目
    // ThumbnailsListPanelへの参照（ThumbnailsListPanelが管理）
    public ThumbnailsListPanel thumbnailsPanel;

    // HOROS-20240407準拠: ViewerController.h 119行目
    // DCMView *imageView;
    private DCMView imageView;

    // HOROS-20240407準拠: ViewerController.h 99行目
    // SeriesView *seriesView;
    private DCMView seriesView;

    // HOROS-20240407準拠: ViewerController.m 8760行目 - showWindowTransition
    // スライダーはビューアーの上部に配置
    private JSlider slider;

    /**
     * コンストラクタ
     * HOROS-20240407準拠: ViewerController.m 7620行目 -
     * initWithPix:withFiles:withVolume:
     */
    public ViewerController() {
        super("DICOM Viewer");

        // HOROS-20240407準拠: 開いているViewerControllerのリストに追加
        displayedViewers.add(this);

        // HOROS-20240407準拠: ViewerController.m 7637行目
        // self = [super initWithWindowNibName:@"Viewer"];
        initializeUI();
    }

    /**
     * UIを初期化
     * HOROS-20240407準拠: ViewerController.m 7620-7669行目
     */
    private void initializeUI() {
        // HOROS-20240407準拠: ViewerController.m 3399-3420行目
        // サムネイルは常に別ウィンドウとして扱う
        // previewMatrixはThumbnailsListPanelに移動される

        // HOROS-20240407準拠: ViewerController.m 5057行目
        // [previewMatrix setCellClass: [ThumbnailCell class]];
        // Java Swingでは、JPanel + BoxLayoutでNSMatrixを再現
        previewMatrix = new JPanel();
        previewMatrix.setLayout(new BoxLayout(previewMatrix, BoxLayout.Y_AXIS));
        previewMatrix.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewMatrix.setBorder(new EmptyBorder(0, 0, 0, 0));

        // HOROS-20240407準拠: NSMatrixのデフォルト背景色（中間グレー）
        // macOSのNSMatrixは中間的なグレーの背景を持つ
        // ハイライト色と区別できるように調整
        previewMatrix.setBackground(new Color(128, 128, 128)); // 中間グレー（ハイライトより暗い）
        previewMatrix.setOpaque(true);

        // HOROS-20240407準拠: ViewerController.m 4944行目
        // [previewMatrixScrollView setPostsBoundsChangedNotifications:YES];
        previewMatrixScrollView = new JScrollPane(previewMatrix);
        previewMatrixScrollView.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        previewMatrixScrollView.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        previewMatrixScrollView.setBorder(null);

        // HOROS-20240407準拠: ViewerController.m 8760行目 - showWindowTransition
        // スライダーはビューアーの上部に配置
        slider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        slider.setPreferredSize(new Dimension(200, 30));

        // HOROS-20240407準拠: スライダーの変更リスナーを追加
        slider.addChangeListener(e -> {
            if (!slider.getValueIsAdjusting()) {
                sliderAction();
            }
        });

        // HOROS-20240407準拠: ViewerController.m 7663行目
        // [seriesView setPixels:pixList[0] files:fileList[0] rois:roiList[0]
        // firstImage:0 level:'i' reset:YES];
        seriesView = new DCMView();
        imageView = seriesView; // 簡易実装

        // HOROS-20240407準拠: ViewerController.m 3399-3420行目
        // サムネイルは常に別ウィンドウとして扱う
        // previewMatrixScrollViewはThumbnailsListPanelに移動される

        // メインコンテンツパネル
        JPanel contentPane = new JPanel(new BorderLayout());

        // HOROS-20240407準拠: ViewerController.m 8760行目 - showWindowTransition
        // スライダーはビューアーの上部に配置
        contentPane.add(slider, BorderLayout.NORTH);
        contentPane.add(seriesView, BorderLayout.CENTER);

        setContentPane(contentPane);

        // HOROS-20240407準拠: ViewerController.m 3399-3420行目
        // ウィンドウクローズ時の処理
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // HOROS-20240407準拠: リストから削除
                displayedViewers.remove(ViewerController.this);
                processWindowEvent(e);
            }
        });
    }

    /**
     * 画像データで初期化
     * HOROS-20240407準拠: ViewerController.m 7620行目 -
     * initWithPix:withFiles:withVolume:
     * 
     * @param f List<List<Object>> - pixList[0]を含むリスト（f.get(0)がList<Object>）
     * @param d List<List<Object>> - fileList[0]を含むリスト（d.get(0)がList<Object>）
     * @param v byte[] - volumeData[0]
     */
    public void initWithPix(List<List<Object>> f, List<List<Object>> d, byte[] v) {
        // HOROS-20240407準拠: ViewerController.m 7654行目
        // [self viewerControllerInit];
        viewerControllerInit();

        // HOROS-20240407準拠: ViewerController.m 7655行目
        // [self changeImageData:f :d :v :YES];
        changeImageData(f, d, v, true);

        // HOROS-20240407準拠: ViewerController.m 7657行目
        // [self startLoadImageThread];
        startLoadImageThread();

        // HOROS-20240407準拠: ViewerController.m 7659行目
        // [self showWindowTransition];
        showWindowTransition();

        // HOROS-20240407準拠: ViewerController.m 7661行目
        // [self checkEverythingLoaded];
        checkEverythingLoaded();

        // HOROS-20240407準拠: ViewerController.m 7670行目
        if (isDataVolumicIn4D(true)) {
            computeIntervalAsync();
        }

        // HOROS-20240407準拠: ViewerController.m 7715行目
        // toolbarPanel = [[ToolbarPanelController alloc] initForViewer: self
        // withToolbar: toolbar];
        Preferences prefs = Preferences.userNodeForPackage(ViewerController.class);
        boolean useToolbarPanel = prefs.getBoolean("USETOOLBARPANEL", true);
        if (useToolbarPanel) {
            // JToolBarを作成（簡易実装）
            JToolBar jToolBar = new JToolBar();
            jToolBar.setFloatable(false);
            toolbarPanel = new ToolbarPanelController(this, jToolBar);
        }

        // HOROS-20240407準拠: ViewerController.m 4876-4884行目 - checkBuiltMatrixPreview
        // サムネイルマトリックスを構築（ウィンドウが表示された後に呼ばれる）
        // ここでは呼ばない（windowDidBecomeMainで呼ぶ）
    }

    /**
     * Check if everything is loaded.
     * HOROS-20240407準拠: ViewerController.m 8928行目 - checkEverythingLoaded
     */
    private void checkEverythingLoaded() {
        // TODO: Implement this method
    }

    /**
     * Check if data is volumic in 4D.
     * HOROS-20240407準拠: ViewerController.h 520行目 - isDataVolumicIn4D
     * 
     * @param check4D
     * @return
     */
    private boolean isDataVolumicIn4D(boolean check4D) {
        // HOROS-20240407準拠: ViewerController.m 7271行目 -
        // isDataVolumicIn4D:checkEverythingLoaded:tryToCorrect:
        // A simplified implementation.
        // TODO: A full implementation should check dimensions, orientation, and slice
        // spacing for all images.
        if (pixList[curMovieIndex] != null && !pixList[curMovieIndex].isEmpty()) {
            Object pix = pixList[curMovieIndex].get(0);
            if (pix instanceof DicomPix) {
                // HOROS-20240407準拠: ViewerController.m 7302行目 - [[pixList[ x] objectAtIndex: 1]
                // orientation: orientation];
                // DicomPix.getOrientation()はdouble[]を返す
                double[] orientation = ((DicomPix) pix).getOrientation();
                return orientation != null && orientation.length == 9;
            }
        }
        return false;
    }

    /**
     * Compute interval asynchronously.
     * HOROS-20240407準拠: ViewerController.m 8496行目 - computeIntervalAsync
     */
    private void computeIntervalAsync() {
        // HOROS-20240407準拠: ViewerController.m 8496行目 - computeIntervalAsync
        new Thread(() -> {
            computeInterval();
        }).start();
    }

    /**
     * Compute interval between slices.
     * HOROS-20240407準拠: ViewerController.m 8503行目 - computeInterval
     */
    private void computeInterval() {
        // TODO: Implement this method.
        // This method should sort slices and calculate the distance between them.
        System.out.println("ViewerController.computeInterval: would be computing slice interval here.");
    }

    /**
     * ビューアーコントローラーを初期化
     * HOROS-20240407準拠: ViewerController.m 20764行目 - viewerControllerInit
     */
    private void viewerControllerInit() {
        // HOROS-20240407準拠: ViewerController.m 20766行目
        // BOOL matrixVisible = [[NSUserDefaults standardUserDefaults] boolForKey:
        // @"SeriesListVisible"];
        Preferences prefs = Preferences.userNodeForPackage(ViewerController.class);
        boolean matrixVisible = prefs.getBoolean("SeriesListVisible", true);

        // HOROS-20240407準拠: ViewerController.m 20770行目
        // numberOf2DViewer++;
        // TODO: グローバルカウンターの実装

        // HOROS-20240407準拠: ViewerController.m 20797行目
        // roiLock = [[NSRecursiveLock alloc] init];
        // TODO: ROIロックの実装

        // HOROS-20240407準拠: ViewerController.m 20802行目
        // maxMovieIndex = 1;
        maxMovieIndex = 1;

        // HOROS-20240407準拠: ViewerController.m 20812行目
        // direction = 1;
        // TODO: directionの実装
    }

    /**
     * 画像データを変更
     * HOROS-20240407準拠: ViewerController.m
     * changeImageData:withFiles:withVolume:withReset:
     * 
     * @param f     List<List<Object>> - pixList[0]を含むリスト（f.get(0)がList<Object>）
     * @param d     List<List<Object>> - fileList[0]を含むリスト（d.get(0)がList<Object>）
     * @param v     byte[] - volumeData[0]
     * @param reset boolean - リセットフラグ
     */
    private void changeImageData(List<List<Object>> f, List<List<Object>> d, byte[] v, boolean reset) {
        // HOROS-20240407準拠: ViewerController.m
        // fileList[0] = d;
        // pixList[0] = f;
        // volumeData[0] = v;
        if (d != null && d.size() > 0 && f != null && f.size() > 0) {
            // HOROS-20240407準拠: fileList[0]はList<Object>（correspondingObjects）そのもの
            // dはList<List<Object>>なので、d.get(0)がList<Object>（correspondingObjects）
            @SuppressWarnings("unchecked")
            List<Object> correspondingObjects = (List<Object>) d.get(0);
            fileList[0] = correspondingObjects;

            // HOROS-20240407準拠: pixList[0]はList<Object>（viewerPix）そのもの
            // fはList<List<Object>>なので、f.get(0)がList<Object>（viewerPix）
            @SuppressWarnings("unchecked")
            List<Object> viewerPix = (List<Object>) f.get(0);
            pixList[0] = viewerPix;

            volumeData[0] = v;

            // HOROS-20240407準拠: ViewerController.m 7663行目
            // [seriesView setPixels:pixList[0] files:fileList[0] rois:roiList[0]
            // firstImage:0 level:'i' reset:YES];
            if (imageView != null && pixList[0] != null && fileList[0] != null) {
                // HOROS-20240407準拠: pixList[0]とfileList[0]をDCMViewに設定
                List<DicomPix> pixListForView = new ArrayList<>();
                for (Object pix : pixList[0]) {
                    if (pix instanceof DicomPix) {
                        pixListForView.add((DicomPix) pix);
                    }
                }

                List<Object> fileListForView = new ArrayList<>(fileList[0]);

                // HOROS-20240407準拠: DCMView.m 2176行目 -
                // setPixels:files:rois:firstImage:level:reset:
                imageView.setPixels(pixListForView, fileListForView, null, (short) 0, (char) 'i', reset);

                // HOROS-20240407準拠: 最初の画像を表示
                if (!pixListForView.isEmpty()) {
                    imageView.setIndex(0);
                    // HOROS-20240407準拠: 画像が読み込まれたことを確認
                    System.out.println("ViewerController.changeImageData: setIndex(0) called, curDCM: "
                            + (imageView.curDCM != null ? "not null" : "null"));

                    // HOROS-20240407準拠: 初回表示時はscaleToFitを呼び出す
                    // DCMView.m updatePresentationStateFromSeriesOnlyImageLevel:scale:offset:
                    // scale == firstTimeDisplay の場合、保存されたスケール値がなければscaleToFitを呼び出す
                    // Java実装ではupdatePresentationStateFromSeriesが未実装のため、明示的に呼び出す
                    if (reset) {
                        imageView.scaleToFit();
                        System.out.println("ViewerController.changeImageData: scaleToFit() called for initial display");
                    }
                } else {
                    System.out.println("ViewerController.changeImageData: pixListForView is empty, cannot set index");
                }

                System.out.println("ViewerController.changeImageData: setPixels called, pixList size: "
                        + pixListForView.size() + ", fileList size: " + fileListForView.size());

                // HOROS-20240407準拠: スライダーの最大値を設定してスライダーを更新
                adjustSlider();

                // HOROS-20240407準拠: 再描画を強制
                if (imageView != null) {
                    imageView.repaint();
                }

                // HOROS-20240407準拠: サムネイルのハイライトと自動スクロールを更新
                // changeImageDataが呼ばれた後、サムネイルを更新してハイライトとスクロールを同期
                javax.swing.SwingUtilities.invokeLater(() -> {
                    updatePreviewMatrix(true);
                });
            }

            // HOROS-20240407準拠: ViewerController.m 4946行目
            // DicomStudy *study = [curImage valueForKeyPath:@"series.study"];
            if (correspondingObjects != null && !correspondingObjects.isEmpty()) {
                Object curImage = correspondingObjects.get(0);
                if (curImage instanceof DicomImage) {
                    DicomImage image = (DicomImage) curImage;
                    DicomSeries series = image.getSeries();
                    if (series != null) {
                        DicomStudy study = series.getStudy();
                        if (study != null) {
                            studyInstanceUID = study.getStudyInstanceUID();
                        }
                    }
                }
            }
        }
    }

    /**
     * ウィンドウのサイズと位置を設定
     * HOROS-20240407準拠: ViewerController.m 8760行目 - showWindowTransition
     */
    public void showWindowTransition() {
        // HOROS-20240407準拠: ViewerController.m 8783行目
        // NSRect screenRect = [AppController usefullRectForScreen: screen];
        Rectangle screenRect = getUsefulRectForScreen();

        // HOROS-20240407準拠: ViewerController.m 8785行目
        // [[self window] setFrame:screenRect display:YES];
        setBounds(screenRect);

        // HOROS-20240407準拠: ViewerController.m 8787-8793行目
        // switch( [[NSUserDefaults standardUserDefaults] integerForKey:
        // @"WINDOWSIZEVIEWER"])
        Preferences prefs = Preferences.userNodeForPackage(ViewerController.class);
        int windowSizeViewer = prefs.getInt("WINDOWSIZEVIEWER", 0);
        switch (windowSizeViewer) {
            case 0:
                // [self setWindowFrame:screenRect showWindow: NO];
                setBounds(screenRect);
                break;
            case 1:
                // [imageView resizeWindowToScale: 1.0];
                // TODO: 実装
                break;
            case 2:
                // [imageView resizeWindowToScale: 1.5];
                // TODO: 実装
                break;
            case 3:
                // [imageView resizeWindowToScale: 2.0];
                // TODO: 実装
                break;
        }

        // HOROS-20240407準拠: ViewerController.m 8785行目
        // ウィンドウの最大サイズを設定（サムネイルウィンドウと重ならないように）
        setMaximumSize(new Dimension(screenRect.width, screenRect.height));
    }

    /**
     * 画面の有効領域を取得
     * HOROS-20240407準拠: AppController.m usefullRectForScreen:
     */
    private Rectangle getUsefulRectForScreen() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gd.getDefaultConfiguration());

        // HOROS-20240407準拠: ViewerController.m 3399-3420行目
        // ツールバーとサムネイルウィンドウのサイズを考慮
        int toolbarHeight = ToolbarPanelController.FIXED_HEIGHT;
        int thumbnailWidth = ThumbnailsListPanel.getFixedWidth();

        // HOROS-20240407準拠: ViewerController.m 8783行目
        // NSRect screenRect = [AppController usefullRectForScreen: screen];
        int x = screenBounds.x + screenInsets.left + thumbnailWidth;
        int y = screenBounds.y + screenInsets.top + toolbarHeight;
        int width = screenBounds.width - screenInsets.left - screenInsets.right - thumbnailWidth;
        int height = screenBounds.height - screenInsets.top - screenInsets.bottom - toolbarHeight;

        return new Rectangle(x, y, width, height);
    }

    /**
     * ウィンドウがメインになったときの処理
     * HOROS-20240407準拠: ViewerController.m 3460行目 - windowDidBecomeMain:
     */
    public void windowDidBecomeMain() {
        System.out.println("ViewerController.windowDidBecomeMain: called");
        // HOROS-20240407準拠: ViewerController.m 3399-3420行目
        // サムネイルウィンドウを表示
        Preferences prefs = Preferences.userNodeForPackage(ViewerController.class);
        boolean useFloatingThumbnailsList = prefs.getBoolean("UseFloatingThumbnailsList", true);
        boolean seriesListVisible = prefs.getBoolean("SeriesListVisible", true);

        System.out.println("ViewerController.windowDidBecomeMain: useFloatingThumbnailsList = "
                + useFloatingThumbnailsList + ", seriesListVisible = " + seriesListVisible);

        if (useFloatingThumbnailsList && seriesListVisible) {
            // HOROS-20240407準拠: ViewerController.m 3412行目
            // [thumbnailsListPanel[ i] setThumbnailsView: previewMatrixScrollView viewer:
            // self];
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] screens = ge.getScreenDevices();

            if (screens.length > 0) {
                GraphicsDevice defaultScreen = screens[0];
                int screenIndex = 0;
                for (int i = 0; i < screens.length; i++) {
                    if (screens[i] == defaultScreen) {
                        screenIndex = i;
                        break;
                    }
                }

                System.out.println("ViewerController.windowDidBecomeMain: screenIndex = " + screenIndex);
                thumbnailsPanel = ThumbnailsListPanel.getInstanceForScreenIndex(screenIndex);
                System.out.println("ViewerController.windowDidBecomeMain: thumbnailsPanel = " + thumbnailsPanel);
                if (thumbnailsPanel != null) {
                    System.out.println(
                            "ViewerController.windowDidBecomeMain: setting thumbnailsView, previewMatrixScrollView = "
                                    + previewMatrixScrollView);
                    thumbnailsPanel.setThumbnailsView(previewMatrixScrollView, this);
                    // HOROS-20240407準拠: applicationDidChangeScreenParametersは内部で呼ばれる
                    thumbnailsPanel.setVisible(true);
                    thumbnailsPanel.toFront();
                } else {
                    System.out.println("ViewerController.windowDidBecomeMain: thumbnailsPanel is null");
                }
            }
        }

        // HOROS-20240407準拠: ViewerController.m 3468行目
        // [self refreshToolbar];
        // TODO: refreshToolbarの実装

        // HOROS-20240407準拠: ViewerController.m 3469行目
        // [self updateNavigator];
        // TODO: updateNavigatorの実装

        // HOROS-20240407準拠: ViewerController.m 3470行目
        // [imageView setNeedsDisplay: YES];
        if (imageView != null) {
            imageView.repaint();
        }

        // HOROS-20240407準拠: ViewerController.m 4876-4884行目 - checkBuiltMatrixPreview
        // サムネイルマトリックスを構築
        // HOROS-20240407準拠: showSelected=trueで現在のSeriesをハイライト・スクロール
        System.out.println("ViewerController.windowDidBecomeMain: calling updatePreviewMatrix");
        if (isVisible()) {
            // HOROS-20240407準拠: SwingUtilities.invokeLaterで遅延実行し、
            // changeImageData()のupdatePreviewMatrix(true)の後に実行されるようにする
            javax.swing.SwingUtilities.invokeLater(() -> {
                updatePreviewMatrix(true);
            });
        } else {
            System.out.println(
                    "ViewerController.windowDidBecomeMain: window is not visible, skipping updatePreviewMatrix");
        }
    }

    /**
     * 画像読み込みスレッドを開始
     * HOROS-20240407準拠: ViewerController.m 8807行目 - startLoadImageThread
     */
    public void startLoadImageThread() {
        // HOROS-20240407準拠: ViewerController.m 8809行目
        // if( windowWillClose) return;
        if (windowWillClose) {
            return;
        }

        // HOROS-20240407準拠: ViewerController.m 8811行目
        // originalOrientation = -1;
        // TODO: originalOrientationの実装

        // HOROS-20240407準拠: ViewerController.m 8820-8825行目
        // 画像読み込み処理
        Thread loadingThread = new Thread(() -> {
            if (pixList[curMovieIndex] != null) {
                int count = 0;
                for (Object pix : pixList[curMovieIndex]) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    if (pix instanceof DicomPix) {
                        try {
                            ((DicomPix) pix).loadImage();
                            count++;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                System.out.println("ViewerController.startLoadImageThread: Loaded " + count + " images.");
            }
        });
        loadingThread.start();
    }

    /**
     * ウィンドウイベントを処理
     * HOROS-20240407準拠: ViewerController.m windowWillClose:
     */
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            // HOROS-20240407準拠: ViewerController.m windowWillClose:
            windowWillClose = true;

            // HOROS-20240407準拠: ViewerController.m 3399-3420行目
            // 関連ウィンドウを閉じる
            if (toolbarPanel != null) {
                toolbarPanel.viewerWillClose(this);
            }
            if (thumbnailsPanel != null) {
                thumbnailsPanel.viewerWillClose(this);
            }

            // HOROS-20240407準拠: 開いているViewerControllerのリストから削除
            synchronized (allViewers) {
                allViewers.remove(this);
            }

            // HOROS-20240407準拠: ViewerController.m windowWillClose:
            // ウィンドウを閉じる
            dispose();
        } else {
            super.processWindowEvent(e);
        }
    }

    /**
     * プレビューマトリックスを更新するための事前条件をチェックします。
     * 
     * @return 更新可能な場合は true
     */
    private boolean isReadyForUpdate() {
        if (!isVisible()) {
            System.out.println("ViewerController.updatePreviewMatrix: window is not visible");
            return false;
        }
        if (windowWillClose) {
            System.out.println("ViewerController.updatePreviewMatrix: windowWillClose is true");
            return false;
        }
        Preferences prefs = Preferences.userNodeForPackage(ViewerController.class);
        boolean seriesListVisible = prefs.getBoolean("SeriesListVisible", true);
        if (!seriesListVisible) {
            System.out.println("ViewerController.updatePreviewMatrix: SeriesListVisible is false");
            return false;
        }
        return true;
    }

    /**
     * プレビューマトリックスをクリアします。
     */
    private void clearPreviewMatrix() {
        previewMatrix.removeAll();
        previewMatrix.revalidate();
        previewMatrix.repaint();
    }

    /**
     * 現在の患者に関連するすべてのStudyをデータベースから読み込みます。
     * HOROS-20240407準拠: ViewerController.m 4960-4982行目
     * 
     * @return Studyのリスト。失敗した場合はnull。
     */
    private List<DicomStudy> loadPatientStudies() {
        if (fileList[0] == null || fileList[0].isEmpty() || !(fileList[0].get(0) instanceof DicomImage)) {
            System.out.println("ViewerController.loadPatientStudies: Initial image/fileList is not valid.");
            return null;
        }

        DicomImage image = (DicomImage) fileList[0].get(0);
        DicomSeries series = image.getSeries();
        if (series == null) {
            System.out.println("ViewerController.loadPatientStudies: Series is null.");
            return null;
        }
        DicomStudy study = series.getStudy();
        if (study == null) {
            System.out.println("ViewerController.loadPatientStudies: Study is null.");
            return null;
        }

        com.jj.dicomviewer.model.DicomDatabase db = com.jj.dicomviewer.model.DicomDatabase.defaultDatabase();
        String searchString = study.getPatientUID();
        List<DicomStudy> studiesArray;

        if (db == null) {
            studiesArray = new ArrayList<>();
            studiesArray.add(study);
        } else {
            if (searchString == null || searchString.isEmpty() || searchString.equals("0")) {
                searchString = study.getName();
                studiesArray = new ArrayList<>();
                studiesArray.add(study);
            } else {
                studiesArray = db.getStudiesByPatientUID(searchString);
            }
        }

        studiesArray.sort((a, b) -> {
            if (a.getDate() == null && b.getDate() == null)
                return 0;
            if (a.getDate() == null)
                return 1;
            if (b.getDate() == null)
                return -1;
            return b.getDate().compareTo(a.getDate());
        });

        return studiesArray;
    }

    /**
     * 現在のビューアで表示されているSeriesのリストを取得します。
     * HOROS-20240407準拠: ViewerController.m 4955-4958行目
     * 
     * @return DicomSeriesのリスト
     */
    private List<DicomSeries> getViewerSeries() {
        List<DicomSeries> viewerSeries = new ArrayList<>();
        for (int i = 0; i < maxMovieIndex; i++) {
            if (fileList[i] != null && !fileList[i].isEmpty()) {
                Object img = fileList[i].get(0);
                if (img instanceof DicomImage) {
                    DicomSeries s = ((DicomImage) img).getSeries();
                    if (s != null) {
                        viewerSeries.add(s);
                    }
                }
            }
        }
        return viewerSeries;
    }

    /**
     * スクロール位置を先頭にリセットします。
     * HOROS-20240407準拠: ViewerController.m 5486行目
     */
    private void resetScrollPosition() {
        previewMatrixScrollView.getViewport().setViewPosition(new java.awt.Point(0, 0));
    }

    /**
     * 現在表示されているSeriesのサムネイルまでスクロールします。
     * HOROS-20240407準拠: ViewerController.m 5477-5488行目
     */
    private void scrollAndHighlightSelectedSeries() {
        DicomSeries currentDisplayedSeries = getCurrentSeries();
        if (currentDisplayedSeries == null) {
            System.out.println(
                    "ViewerController.scrollAndHighlightSelectedSeries: currentDisplayedSeries is null, cannot scroll.");
            return;
        }

        Component[] components = previewMatrix.getComponents();
        int seriesIndex = -1;
        String currentUID = currentDisplayedSeries.getSeriesInstanceUID();

        // まず全てのSeriesのハイライトをクリア（Studyの色は維持）
        for (Component component : components) {
            if (component instanceof ThumbnailButton) {
                ThumbnailButton button = (ThumbnailButton) component;
                Object representedObj = button.getRepresentedObject();
                if (representedObj instanceof DicomSeries) {
                    // SELECTED_ITEM_COLOR（オレンジ）だけをクリアし、Studyの色などは維持
                    if (button.getHighlightColor() != null && button.getHighlightColor().equals(SELECTED_ITEM_COLOR)) {
                        button.setHighlightColor(null);
                    }
                }
            }
        }

        // 現在のSeriesをハイライト
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof ThumbnailButton) {
                ThumbnailButton button = (ThumbnailButton) components[i];
                Object representedObj = button.getRepresentedObject();
                if (representedObj instanceof DicomSeries) {
                    DicomSeries buttonSeries = (DicomSeries) representedObj;
                    String buttonUID = buttonSeries.getSeriesInstanceUID();
                    if (buttonUID != null && currentUID != null && buttonUID.equals(currentUID)) {
                        seriesIndex = i;
                        // 選択されたSeriesをハイライト
                        button.setHighlightColor(SELECTED_ITEM_COLOR);
                        break;
                    }
                }
            }
        }

        if (seriesIndex != -1) {
            Component targetComponent = components[seriesIndex];
            final java.awt.Rectangle rect = targetComponent.getBounds();
            javax.swing.SwingUtilities.invokeLater(() -> previewMatrix.scrollRectToVisible(rect));
        } else {
            System.out.println(
                    "ViewerController.scrollAndHighlightSelectedSeries: series index not found for UID: " + currentUID);
        }
    }

    /**
     * プレビューマトリックスを更新
     * HOROS-20240407準拠: ViewerController.m 4903行目 - buildMatrixPreview:
     *
     * @param showSelected true: 現在選択されているSeriesをハイライト・スクロール, false: スクロールをリセット
     */
    public void updatePreviewMatrix(boolean showSelected) {
        if (!isReadyForUpdate()) {
            return;
        }
        System.out.println("ViewerController.updatePreviewMatrix: starting update");
        try {
            List<DicomStudy> studiesArray = loadPatientStudies();
            if (studiesArray == null || studiesArray.isEmpty()) {
                clearPreviewMatrix();
                return;
            }

            DicomStudy currentStudy = ((DicomImage) fileList[0].get(0)).getSeries().getStudy();
            List<DicomSeries> displayedSeries = ViewerController.getDisplayedSeries();
            List<DicomSeries> viewerSeries = getViewerSeries();

            clearPreviewMatrix();

            com.jj.dicomviewer.model.DicomDatabase db = com.jj.dicomviewer.model.DicomDatabase.defaultDatabase();
            BrowserController browser = BrowserController.currentBrowser();

            for (int studyIdx = 0; studyIdx < studiesArray.size(); studyIdx++) {
                DicomStudy curStudy = studiesArray.get(studyIdx);

                // データベースからisHidden状態を読み込む
                if (db != null) {
                    try {
                        db.loadStudyHiddenState(curStudy);
                    } catch (Exception e) {
                        System.err
                                .println("ViewerController.updatePreviewMatrix: failed to load isHidden from database: "
                                        + e.getMessage());
                        curStudy.setHidden(false);
                    }
                } else {
                    curStudy.setHidden(false);
                }

                // Seriesリストを取得
                List<DicomSeries> seriesList;
                if (browser == null) {
                    seriesList = curStudy.allSeries();
                } else {
                    if (db != null)
                        db.loadSeriesForStudyIfNeeded(curStudy);
                    List<Object> children = browser.childrenArray(curStudy, false);
                    seriesList = new ArrayList<>();
                    for (Object child : children) {
                        if (child instanceof DicomSeries) {
                            seriesList.add((DicomSeries) child);
                        }
                    }
                }

                // Studyセルを作成して追加
                ThumbnailButton studyButton = createStudyCell(curStudy, currentStudy, studyIdx, studiesArray.size(),
                        seriesList.size());
                studyButton.addActionListener(e -> matrixPreviewSwitchHidden(curStudy));
                studyButton.setRepresentedObject(curStudy);
                previewMatrix.add(studyButton);

                // Seriesセルを作成して追加 (Studyが非表示でない場合)
                if (!curStudy.isHidden()) {
                    for (DicomSeries curSeries : seriesList) {
                        ThumbnailButton seriesButton = createSeriesCell(curSeries, curStudy, currentStudy, viewerSeries,
                                displayedSeries);
                        // ActionListenerはThumbnailButtonのコンストラクタで既に設定されている
                        seriesButton.setRepresentedObject(curSeries);
                        previewMatrix.add(seriesButton);
                    }
                }
            }

            previewMatrix.revalidate();
            previewMatrix.validate();
            previewMatrix.repaint();

            // 常に現在選択されているSeriesの背景色を更新（showSelectedの値に関わらず）
            DicomSeries currentSeries = getCurrentSeries();
            if (currentSeries != null) {
                updateSeriesHighlightColors(currentSeries);
            }

            if (showSelected) {
                scrollAndHighlightSelectedSeries();
            } else {
                resetScrollPosition();
            }

        } catch (Exception ex) {
            System.err.println("ViewerController.updatePreviewMatrix: error occurred");
            ex.printStackTrace();
            clearPreviewMatrix();
        }
    }

    /**
     * Studyセルを作成
     * HOROS-20240407準拠: ViewerController.m 5091-5242行目
     */
    private ThumbnailButton createStudyCell(DicomStudy study, DicomStudy currentStudy, int studyIndex, int totalStudies,
            int seriesCount) {
        ThumbnailButton button = new ThumbnailButton(this);

        // HOROS-20240407準拠: ViewerController.m 5123行目
        // NSString *name = [[curStudy valueForKey:@"studyName"]
        // stringByTruncatingToLength: 34];
        String name = study.getStudyName();
        if (name == null || name.isEmpty()) {
            name = "Unnamed";
        }
        if (name.length() > 34) {
            name = name.substring(0, 34);
        }

        // HOROS-20240407準拠: ViewerController.m 5125-5128行目
        // NSString *stateText;
        String stateText = "";
        if (study.getStateText() != null) {
            // TODO: BrowserController.statesArrayから取得
        }

        // HOROS-20240407準拠: ViewerController.m 5129-5133行目
        // NSString *comment = [curStudy valueForKey:@"comment"];
        String comment = study.getComment();
        if (comment == null) {
            comment = "";
        }
        if (comment.length() > 32) {
            comment = comment.substring(0, 32);
        }

        // HOROS-20240407準拠: ViewerController.m 5135-5137行目
        // NSString *modality = [curStudy valueForKey:@"modality"];
        String modality = study.getModality();
        if (modality == null || modality.isEmpty()) {
            modality = "OT:";
        }

        // HOROS-20240407準拠: ViewerController.m 5164-5167行目
        // if( [curStudy isHidden])
        // action = NSLocalizedString(@"Show Series", nil);
        // else
        // action = NSLocalizedString(@"Hide Series", nil);
        String action = study.isHidden() ? "Show Series" : "Hide Series";

        // HOROS-20240407準拠: ViewerController.m 5183-5192行目
        // NSMutableArray* components = [NSMutableArray array];
        // [components addObject: [NSString stringWithFormat: @" %d ", (int)
        // curStudyIndexAll+1]];
        List<String> components = new ArrayList<>();
        components.add(String.format(" %d ", studyIndex + 1));
        components.add("");
        if (name.length() > 0) {
            components.add(name);
        }
        if (study.getDate() != null) {
            // TODO: 日付フォーマット
            components.add(study.getDate().toString());
        }
        components.add(String.format("%s: %d %s", modality, seriesCount, seriesCount == 1 ? "series" : "series"));
        if (stateText.length() > 0) {
            components.add(stateText);
        }
        if (comment.length() > 0) {
            components.add(comment);
        }
        if (action.length() > 0) {
            components.add("\r" + action);
        }

        // HOROS-20240407準拠: ViewerController.m 5195行目
        // NSMutableAttributedString *finalString = [[[NSMutableAttributedString alloc]
        // initWithString: [components componentsJoinedByString:@"\r"]] autorelease];
        String text = String.join("\r", components);

        // HOROS-20240407準拠: ViewerController.m 5198-5215行目
        // 番号部分を太字・背景色付きで表示
        // HOROS-20240407準拠: ViewerController.m 5198行目 - viewerNumberFont
        // HOROS-20240407準拠: ViewerController.m 5213行目 - dbSmallMatrixFont
        Dimension cellSize = button.getCellSize();
        // HOROS-20240407準拠: テキストの幅を制限して右はみ出しを防止
        // フォントサイズを小さくして、日付を1段で表示できるようにする
        Preferences prefs = Preferences.userNodeForPackage(ViewerController.class);
        int dbFontSize = prefs.getInt("dbFontSize", 0);
        int fontSize = 6; // デフォルトフォントサイズ（1サイズ小さく）
        int numberFontSize = 7; // 番号部分のフォントサイズ（1サイズ小さく）
        switch (dbFontSize) {
            case -1:
                fontSize = 5;
                numberFontSize = 6;
                break;
            case 0:
                fontSize = 6;
                numberFontSize = 7;
                break;
            case 1:
                fontSize = 8;
                numberFontSize = 9;
                break;
        }

        // HOROS-20240407準拠: ViewerController.m 5217行目 - [finalString
        // setAlignment:NSCenterTextAlignment range: NSMakeRange( 0,
        // finalString.length)];
        // テキストを中央揃えで表示
        // HOROS-20240407準拠: テキストの幅をセルサイズに合わせる
        // HTMLのbodyに幅を設定せず、divで中央揃えを確実にする
        // IMPORTANT: background: transparent; を設定してボタンの背景色が透けて見えるようにする
        String html = "<html><head><style>body { margin: 0; padding: 0; background: transparent; }</style></head><body style='text-align: center; word-wrap: break-word; overflow: hidden; font-size: "
                + fontSize + "px;'>";
        html += "<div style='width: 100%; max-width: " + cellSize.width
                + "px; margin: 0 auto; text-align: center; background: transparent;'>";
        html += "<span style='background-color: " + getStudyColor(studyIndex) + "; font-weight: bold; font-size: "
                + numberFontSize + "px;'>" + String.format(" %d ", studyIndex + 1) + "</span>";
        html += "<br>";
        if (name.length() > 0) {
            html += name + "<br>";
        }
        if (study.getDate() != null) {
            // HOROS-20240407準拠: ViewerController.m 5188行目 - [[NSUserDefaults
            // dateTimeFormatter] stringFromDate:[curStudy date]]
            // LocalDateTime.toString()はISO 8601形式（2024-01-01T12:00:00）を返すので、「T」をスペースに置換
            String dateStr = study.getDate().toString().replace("T", " ");
            html += dateStr + "<br>";
        }
        html += String.format("%s: %d %s<br>", modality, seriesCount, seriesCount == 1 ? "series" : "series");
        if (stateText.length() > 0) {
            html += stateText + "<br>";
        }
        if (comment.length() > 0) {
            html += comment + "<br>";
        }
        if (action.length() > 0) {
            html += "<br>" + action;
        }
        html += "</div>";
        html += "</body></html>";

        // HOROS-20240407準拠: HTMLテキストのみを使用し、通常のテキストは設定しない
        button.setText(html);
        // HOROS-20240407準拠: ViewerController.m 5072行目 - [cell setImagePosition:
        // NSImageBelow];
        // Studyセルには画像がないので、テキストを中央に配置
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        // HOROS-20240407準拠: Studyセルの枠を非表示にする（Show/Hideを押すと消える問題を解決）
        button.setBorderPainted(false);
        button.setFocusPainted(false);

        // HOROS-20240407準拠: ViewerController.m 5106-5109行目
        // if( [[curStudy valueForKey: @"studyInstanceUID"] isEqualToString:
        // study.studyInstanceUID])
        // [cell setBackgroundColor: nil];
        // else
        // [cell setBackgroundColor: [[self class] _differentStudyColor]];
        // 現在のStudyと一致する場合は背景色なし、異なる場合は異なるStudyの色を設定
        if (study.getStudyInstanceUID() != null && currentStudy.getStudyInstanceUID() != null
                && study.getStudyInstanceUID().equals(currentStudy.getStudyInstanceUID())) {
            // 一致する場合：背景色なし
            button.setHighlightColor(null);
        } else {
            // 異なるStudyの場合：異なるStudyの色を設定（gray: 0.55, 0.55, 0.55）
            // HOROS-20240407準拠: ViewerController.m 4896行目 - _differentStudyColor
            button.setHighlightColor(DIFFERENT_STUDY_COLOR);
        }

        return button;
    }

    /**
     * Creates a placeholder thumbnail image.
     * 
     * @param text   The text to display on the thumbnail.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A BufferedImage to be used as a placeholder.
     */
    private java.awt.image.BufferedImage createPlaceholderThumbnail(String text, int width, int height) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = img.createGraphics();
        g2d.setColor(java.awt.Color.DARK_GRAY);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(java.awt.Color.WHITE);
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        java.awt.Font font = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
        g2d.setFont(font);
        java.awt.FontMetrics fm = g2d.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        int y = (fm.getAscent() + (height - (fm.getAscent() + fm.getDescent())) / 2);
        g2d.drawString(text, x, y);
        g2d.dispose();
        return img;
    }

    /**
     * Seriesセルを作成
     * HOROS-20240407準拠: ViewerController.m 5309-5441行目
     */
    private ThumbnailButton createSeriesCell(DicomSeries series, DicomStudy curStudy, DicomStudy currentStudy,
            List<DicomSeries> viewerSeries, List<DicomSeries> displayedSeries) {
        ThumbnailButton button = new ThumbnailButton(this);

        // HOROS-20240407準拠: ViewerController.m 5325行目
        // NSString *name = [curSeries valueForKey:@"name"];
        String name = series.getName();
        if (name == null) {
            name = "";
        }

        // HOROS-20240407準拠: ViewerController.m 5327-5331行目
        // if( [name length] > 18)
        if (name.length() > 18) {
            // [cell setFont:[NSFont boldSystemFontOfSize: [[BrowserController
            // currentBrowser] fontSize: @"viewerSmallCellFont"]]];
            // name = [name stringByTruncatingToLength: 34];
            if (name.length() > 34) {
                name = name.substring(0, 34);
            }
        }

        // HOROS-20240407準拠: ViewerController.m 5333-5370行目
        // 画像数の計算
        int count = series.getNumberOfImages() != null ? series.getNumberOfImages() : 0;
        String singleType = "Image";
        String pluralType = "Images";

        if (count == 1) {
            // TODO: フレーム数の確認
        } else if (count == 0) {
            // TODO: rawNoFilesの確認
        }

        // HOROS-20240407準拠: ViewerController.m 5370行目
        // [cell setTitle:[NSString stringWithFormat:@"%@\r%@\r%@", name,
        // [[NSUserDefaults dateTimeFormatter] stringFromDate: [curSeries
        // valueForKey:@"date"]], N2LocalizedSingularPluralCount(count, singleType,
        // pluralType)]];
        // HOROS-20240407準拠: 順序は name, date, count
        // ユーザー要望: Study Descriptionが最上段、真ん中が撮影日付、最下段がイメージ数
        // Study Descriptionを取得（Studyから）
        String studyDescription = "";
        if (curStudy != null) {
            studyDescription = curStudy.getStudyName();
            if (studyDescription == null || studyDescription.isEmpty()) {
                studyDescription = "Unnamed";
            }
        }

        // HOROS-20240407準拠: ViewerController.m 5072行目 - [cell setImagePosition:
        // NSImageBelow];
        // テキストは上、画像は下に表示
        // HOROS-20240407準拠: ViewerController.m 5321行目 - dbSmallMatrixFont
        // HTMLを使用して複数行テキストを表示
        Dimension cellSize = button.getCellSize();
        Preferences prefs = Preferences.userNodeForPackage(ViewerController.class);
        int dbFontSize = prefs.getInt("dbFontSize", 0);
        int fontSize = 6; // デフォルトフォントサイズ（1サイズ小さく）
        switch (dbFontSize) {
            case -1:
                fontSize = 5;
                break;
            case 0:
                fontSize = 6;
                break;
            case 1:
                fontSize = 8;
                break;
        }

        // ユーザー要望に合わせて順序を変更: Study Description, date, count
        // HOROS-20240407準拠: ViewerController.m 5370行目 - [cell setTitle:[NSString
        // stringWithFormat:@"%@\r%@\r%@", name, date, count]];
        // HOROS-20240407準拠: テキストを中央揃えで表示
        // HTMLのbodyに幅を設定せず、divで中央揃えを確実にする
        // IMPORTANT: background: transparent; を設定してボタンの背景色が透けて見えるようにする
        String html = "<html><head><style>body { margin: 0; padding: 0; background: transparent; }</style></head><body style='text-align: center; word-wrap: break-word; overflow: hidden; font-size: "
                + fontSize + "px;'>";
        html += "<div style='width: 100%; max-width: " + cellSize.width
                + "px; margin: 0 auto; text-align: center; background: transparent;'>";
        html += studyDescription + "<br>";
        if (series.getDate() != null) {
            // HOROS-20240407準拠: ViewerController.m 5370行目 - [[NSUserDefaults
            // dateTimeFormatter] stringFromDate: [curSeries valueForKey:@"date"]]
            // LocalDateTime.toString()はISO 8601形式（2024-01-01T12:00:00）を返すので、「T」をスペースに置換
            String dateStr = series.getDate().toString().replace("T", " ");
            html += dateStr + "<br>";
        }
        html += count + " " + (count == 1 ? singleType : pluralType);
        html += "</div>";
        html += "</body></html>";

        // HOROS-20240407準拠: HTMLテキストのみを使用し、通常のテキストは設定しない
        button.setText(html);
        // HOROS-20240407準拠: ViewerController.m 5072行目 - [cell setImagePosition:
        // NSImageBelow];
        // テキストは上、画像は下に表示
        button.setVerticalTextPosition(SwingConstants.TOP);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        // HOROS-20240407準拠: Seriesセルの枠を非表示にする
        button.setBorderPainted(false);
        button.setFocusPainted(false);

        // HOROS-20240407準拠: ViewerController.m 5372-5383行目
        // 背景色の設定
        // HOROS-20240407準拠: ViewerController.m 5315-5318行目
        // seriesInstanceUIDで比較（equalsメソッドが正しく実装されていない可能性があるため）
        boolean isInViewerSeries = false;
        String seriesUID = series.getSeriesInstanceUID();
        System.out.println(
                "ViewerController.createSeriesCell: checking series " + series.getName() + " (UID: " + seriesUID + ")");
        if (seriesUID != null) {
            for (DicomSeries vs : viewerSeries) {
                if (vs != null) {
                    String vsUID = vs.getSeriesInstanceUID();
                    if (vsUID != null && seriesUID.equals(vsUID)) {
                        isInViewerSeries = true;
                        System.out.println("ViewerController.createSeriesCell: series " + series.getName()
                                + " found in viewerSeries (matched UID: " + vsUID + ")");
                        break;
                    }
                }
            }
        }

        boolean isInDisplayedSeries = false;
        if (seriesUID != null) {
            for (DicomSeries ds : displayedSeries) {
                if (ds != null) {
                    String dsUID = ds.getSeriesInstanceUID();
                    if (dsUID != null && seriesUID.equals(dsUID)) {
                        isInDisplayedSeries = true;
                        System.out.println("ViewerController.createSeriesCell: series " + series.getName()
                                + " found in displayedSeries (matched UID: " + dsUID + ")");
                        break;
                    }
                }
            }
        }

        // HOROS-20240407準拠: ViewerController.m 5315-5382行目
        // 背景色の設定は2段階で行う
        // (1) まずStudyによって基本色を設定
        if (curStudy.getStudyInstanceUID() != null && currentStudy.getStudyInstanceUID() != null
                && curStudy.getStudyInstanceUID().equals(currentStudy.getStudyInstanceUID())) {
            // 現在のStudy：明るい背景色（デフォルト）
            button.setHighlightColor(null);
            System.out.println("ViewerController.createSeriesCell: series " + series.getName()
                    + " is in current study, setting light background");
        } else {
            // 異なるStudy：暗いグレー
            button.setHighlightColor(DIFFERENT_STUDY_COLOR);
            System.out.println("ViewerController.createSeriesCell: series " + series.getName()
                    + " is in different study, setting dark background");
        }

        // (2) 次にSeriesの状態によって上書き
        if (isInViewerSeries) {
            // Viewer内で選択されているSeries：初期段階では明るい背景色（デフォルト）
            // 後でupdateSeriesHighlightColorsで背景色が更新される
            button.setHighlightColor(null);
            System.out.println("ViewerController.createSeriesCell: series " + series.getName() + " is in viewerSeries");
        } else if (isInDisplayedSeries) {
            // Yellow - 開かれている（他のビューワーで開いている）
            button.setHighlightColor(OPEN_ITEM_COLOR);
            System.out.println("ViewerController.createSeriesCell: series " + series.getName()
                    + " is in displayedSeries, setting yellow background");
        }
        // isInViewerSeriesでもisInDisplayedSeriesでもない場合は、(1)で設定した色のまま

        // HOROS-20240407準拠: ViewerController.m 5385-5438行目
        // サムネイル画像の設定
        java.awt.image.BufferedImage thumbnailImage = null;
        int thumbnailHeight = cellSize.height - 40;
        int thumbnailWidth = cellSize.width - 4;
        if (thumbnailHeight < 20)
            thumbnailHeight = 20;

        try {
            // 1. DBからサムネイルを読み込む
            byte[] thumbnailData = series.getThumbnail();
            if (thumbnailData != null && thumbnailData.length > 0) {
                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(thumbnailData)) {
                    thumbnailImage = javax.imageio.ImageIO.read(bais);
                } catch (Exception e) {
                    System.err.println(
                            "Failed to read thumbnail data for series: " + series.getName() + ", " + e.getMessage());
                    thumbnailImage = null; // 失敗した場合は次に進む
                }
            }

            // 2. サムネイルがない場合、DicomPixから生成
            if (thumbnailImage == null) {
                List<DicomImage> images = series.sortedImages();
                if (images != null && !images.isEmpty()) {
                    DicomImage firstImage = images.get(0);
                    try {
                        com.jj.dicomviewer.model.DicomPix dcmPix = new com.jj.dicomviewer.model.DicomPix(
                                firstImage,
                                firstImage.getFrameID() != null ? firstImage.getFrameID() : 0,
                                1);
                        dcmPix.loadImage();
                        if (dcmPix.getBufferedImage() != null) {
                            thumbnailImage = dcmPix.generateThumbnail(thumbnailWidth, thumbnailHeight);
                        } else {
                            System.err.println("Failed to generate thumbnail, DicomPix buffer is null for series: "
                                    + series.getName());
                            thumbnailImage = createPlaceholderThumbnail("No Image", thumbnailWidth, thumbnailHeight);
                        }
                    } catch (Exception e) {
                        System.err.println(
                                "Failed to load DicomPix for thumbnail generation for series: " + series.getName());
                        e.printStackTrace();
                        thumbnailImage = createPlaceholderThumbnail("Load Error", thumbnailWidth, thumbnailHeight);
                    }
                } else {
                    thumbnailImage = createPlaceholderThumbnail("No Images", thumbnailWidth, thumbnailHeight);
                }
            }
        } catch (Exception e) {
            System.err.println("Unexpected error during thumbnail creation for series: " + series.getName());
            e.printStackTrace();
            thumbnailImage = createPlaceholderThumbnail("Error", thumbnailWidth, thumbnailHeight);
        }

        // 3. 必要に応じて画像を反転
        boolean displayUseInvertedPolarity = prefs.getBoolean("DisplayUseInvertedPolarity", false);
        if (displayUseInvertedPolarity && thumbnailImage != null) {
            java.awt.image.RescaleOp op = new java.awt.image.RescaleOp(-1.0f, 255f, null);
            thumbnailImage = op.filter(thumbnailImage, thumbnailImage);
        }

        // 4. スケーリングしてアイコンを設定
        if (thumbnailImage != null) {
            double scale = 1.0;
            switch (dbFontSize) {
                case -1:
                    scale = 0.6;
                    break;
                case 0:
                    scale = 1.0;
                    break;
                case 1:
                    scale = 1.3;
                    break;
            }

            if (scale != 1.0) {
                int newWidth = (int) (thumbnailImage.getWidth() * scale);
                int newHeight = (int) (thumbnailImage.getHeight() * scale);
                if (newWidth > 0 && newHeight > 0) {
                    java.awt.Image scaled = thumbnailImage.getScaledInstance(newWidth, newHeight,
                            java.awt.Image.SCALE_SMOOTH);
                    thumbnailImage = new java.awt.image.BufferedImage(newWidth, newHeight,
                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                    thumbnailImage.getGraphics().drawImage(scaled, 0, 0, null);
                }
            }
            button.setIcon(new javax.swing.ImageIcon(thumbnailImage));
        } else {
            // 最終的なフォールバック
            button.setIcon(new javax.swing.ImageIcon(
                    createPlaceholderThumbnail("Not Found", thumbnailWidth, thumbnailHeight)));
        }

        return button;
    }

    /**
     * サムネイルクリック時の処理
     * HOROS-20240407準拠: ViewerController.m 4413行目 - matrixPreviewPressed:
     */
    public void matrixPreviewPressed(ThumbnailButton sender) {
        // HOROS-20240407準拠: ViewerController.m 4415行目
        // ThumbnailCell *cell = [sender selectedCell];
        // JavaではsenderがThumbnailButton自体

        // HOROS-20240407準拠: ViewerController.m 4417-4424行目
        // [cell setLineBreakMode: NSLineBreakByWordWrapping];
        // [cell setFont:[NSFont boldSystemFontOfSize: [[BrowserController
        // currentBrowser] fontSize: @"dbSmallMatrixFont"]]];
        // [cell setImagePosition: NSImageBelow];
        // [cell setTransparent:NO];
        // [cell setEnabled:YES];
        // [cell setButtonType:NSMomentaryPushInButton];
        // [cell setBezelStyle:NSShadowlessSquareBezelStyle];
        // [cell setHighlightsBy:NSContentsCellMask];
        // [cell setImageScaling:NSImageScaleProportionallyDown];
        // [cell setBordered:YES];
        // JavaではThumbnailButtonの設定は既にされている

        // HOROS-20240407準拠: ViewerController.m 4426行目
        // id series = [[[sender selectedCell] representedObject] object];
        Object representedObj = sender.getRepresentedObject();
        DicomSeries series = null;
        if (representedObj instanceof DicomSeries) {
            series = (DicomSeries) representedObj;
        }

        // HOROS-20240407準拠: ViewerController.m 4428行目
        // [self loadSelectedSeries: series rightClick: cell.rightClick];
        loadSelectedSeries(series, sender.isRightClick());

        // HOROS-20240407準拠: クリックされたSeriesの背景色を更新
        if (series != null) {
            updateSeriesHighlightColors(series);
        }
    }

    /**
     * Studyの色を取得
     * HOROS-20240407準拠: ViewerController.m 5200-5205行目
     */
    private String getStudyColor(int studyIndex) {
        // HOROS-20240407準拠: ViewerController.studyColors
        // 簡易実装: 各Studyを区別する色
        Color[] colors = {
                SELECTED_ITEM_COLOR, // 赤系
                FUSED_ITEM_COLOR, // 緑系
                new Color(200, 200, 255), // 青系
                OPEN_ITEM_COLOR, // 黄系
                new Color(255, 200, 255), // マゼンタ系
                new Color(200, 255, 255) // シアン系
        };
        Color color = colors[studyIndex % colors.length];
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Seriesのハイライト色を更新
     * HOROS-20240407準拠: クリックされたSeriesをハイライト + Studyの背景色を動的に変更
     */
    private void updateSeriesHighlightColors(DicomSeries selectedSeries) {
        DicomStudy selectedStudy = selectedSeries.getStudy();

        // 全てのボタンを処理
        for (Component comp : previewMatrix.getComponents()) {
            if (comp instanceof ThumbnailButton) {
                ThumbnailButton button = (ThumbnailButton) comp;
                Object representedObj = button.getRepresentedObject();

                if (representedObj instanceof DicomStudy) {
                    // Studyボタンの背景色を更新
                    DicomStudy study = (DicomStudy) representedObj;
                    if (study.getStudyInstanceUID() != null && selectedStudy.getStudyInstanceUID() != null
                            && study.getStudyInstanceUID().equals(selectedStudy.getStudyInstanceUID())) {
                        // 選択されたStudyのみ明るい背景
                        button.setHighlightColor(null);
                    } else {
                        // それ以外は暗いグレーに統一（初期選択が残らないようにする）
                        button.setHighlightColor(DIFFERENT_STUDY_COLOR);
                    }
                } else if (representedObj instanceof DicomSeries) {
                    // Seriesボタンの背景色を更新
                    DicomSeries series = (DicomSeries) representedObj;
                    DicomStudy seriesStudy = series.getStudy();

                    // 選択されたSeriesの場合はオレンジで常にハイライト
                    if (series.getSeriesInstanceUID() != null && selectedSeries.getSeriesInstanceUID() != null
                            && series.getSeriesInstanceUID().equals(selectedSeries.getSeriesInstanceUID())) {
                        button.setHighlightColor(SELECTED_ITEM_COLOR);
                        continue;
                    }

                    // Study別の背景色を設定
                    if (seriesStudy.getStudyInstanceUID() != null && selectedStudy.getStudyInstanceUID() != null
                            && seriesStudy.getStudyInstanceUID().equals(selectedStudy.getStudyInstanceUID())) {
                        // 選択されたStudy内のSeries：明るい背景
                        button.setHighlightColor(null);
                    } else {
                        // それ以外は暗いグレー
                        button.setHighlightColor(DIFFERENT_STUDY_COLOR);
                    }
                }
            }
        }

        // 再描画
        previewMatrix.revalidate();
        previewMatrix.repaint();
    }

    /**
     * Studyの表示/非表示を切り替え
     * HOROS-20240407準拠: ViewerController.m 4845行目 - matrixPreviewSwitchHidden:
     */
    private void matrixPreviewSwitchHidden(DicomStudy study) {
        // HOROS-20240407準拠: ViewerController.m 4857行目
        // [curStudy setHidden: ![curStudy isHidden]];
        System.out.println("ViewerController.matrixPreviewSwitchHidden: study = " + study.getStudyInstanceUID()
                + ", isHidden = " + study.isHidden());
        study.setHidden(!study.isHidden());
        System.out
                .println("ViewerController.matrixPreviewSwitchHidden: after setHidden, isHidden = " + study.isHidden());

        // HOROS-20240407準拠: データベースに保存
        com.jj.dicomviewer.model.DicomDatabase db = com.jj.dicomviewer.model.DicomDatabase.defaultDatabase();
        if (db != null) {
            try {
                // HOROS-20240407準拠: Core Dataでは自動的に保存されるが、Javaでは明示的に保存する必要がある
                // updateStudyHiddenState()を呼び出してhidden状態を更新
                db.updateStudyHiddenState(study.getStudyInstanceUID(), study.isHidden());
                System.out.println("ViewerController.matrixPreviewSwitchHidden: saved to database");
            } catch (Exception e) {
                System.err.println(
                        "ViewerController.matrixPreviewSwitchHidden: failed to save to database: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // HOROS-20240407準拠: ViewerController.m 4859-4865行目
        // for( ViewerController *v in [ViewerController getDisplayed2DViewers])
        // {
        // if( [v.studyInstanceUID isEqualToString: self.studyInstanceUID)
        // [v buildMatrixPreview: NO];
        // else
        // [v buildMatrixPreview: YES];
        // }
        // HOROS-20240407準拠: ViewerController.m 4859-4865行目
        // 全てのViewerControllerのbuildMatrixPreview:を呼び出す
        // 簡易実装: 現在のViewerControllerのみ更新（SwingUtilities.invokeLaterでUIスレッドで実行）
        System.out.println("ViewerController.matrixPreviewSwitchHidden: calling updatePreviewMatrix(false)");
        javax.swing.SwingUtilities.invokeLater(() -> {
            updatePreviewMatrix(false);
        });

        // HOROS-20240407準拠: ViewerController.m 4867行目
        // [self syncThumbnails];
        syncThumbnails();
    }

    /**
     * 選択されたSeriesを読み込む
     * HOROS-20240407準拠: ViewerController.m 4272行目 - loadSelectedSeries:rightClick:
     */
    private void loadSelectedSeries(Object series, boolean rightClick) {
        // HOROS-20240407準拠: ViewerController.m 4274-4283行目
        // SeriesがDicomStudyの場合は、最初のSeriesを取得
        if (series instanceof DicomStudy) {
            DicomStudy s = (DicomStudy) series;
            List<DicomSeries> seriesArray = s.imageSeriesContainingPixels(true);
            if (seriesArray != null && !seriesArray.isEmpty()) {
                series = seriesArray.get(0);
            } else {
                return;
            }
        }

        if (!(series instanceof DicomSeries)) {
            return;
        }

        DicomSeries targetSeries = (DicomSeries) series;

        // HOROS-20240407準拠: ViewerController.m 4285-4291行目
        // 現在のviewerSeriesを取得（fileListから）
        List<DicomSeries> viewerSeries = new ArrayList<>();
        for (int i = 0; i < maxMovieIndex; i++) {
            if (fileList[i] != null && !fileList[i].isEmpty()) {
                Object img = fileList[i].get(0);
                if (img instanceof DicomImage) {
                    DicomSeries s = ((DicomImage) img).getSeries();
                    if (s != null) {
                        viewerSeries.add(s);
                    }
                }
            }
        }

        // HOROS-20240407準拠: ViewerController.m 4293-4323行目
        // rightClickまたはCommandキーの場合：新しいViewerControllerを作成
        // TODO: Commandキーの検出（現在はrightClickのみ対応）
        System.out.println("ViewerController.loadSelectedSeries: rightClick=" + rightClick + ", series=" + targetSeries.getName());
        if (rightClick) {
            // HOROS-20240407準拠: ViewerController.m 4301行目
            // ViewerController *newViewer = [[BrowserController currentBrowser] loadSeries
            // :series :nil :YES keyImagesOnly: displayOnlyKeyImages];
            // 右クリック時は新しいViewerControllerウィンドウを作成
            BrowserController browser = BrowserController.currentBrowser();
            if (browser != null) {
                System.out.println("ViewerController.loadSelectedSeries: creating new viewer for series (right-click): "
                        + targetSeries.getName());

                // HOROS-20240407準拠: BrowserController.loadSeriesを呼び出す
                // loadSeriesは内部でviewerDICOMIntを呼び出して新しいViewerControllerを作成する
                // 現在、loadSeriesメソッドがないため、viewerDICOMIntを直接呼び出す
                List<Object> selectedLines = new java.util.ArrayList<>();
                selectedLines.add(targetSeries);

                // viewerDICOMIntを呼び出して新しいViewerControllerを作成
                // 第1引数: movieViewer (false)
                // 第2引数: selectedLines (選択されたSeries)
                // 第3引数: viewer (null = 新しいウィンドウを作成)
                browser.openViewerForSeries(targetSeries);
            }
            return;
        }

        // HOROS-20240407準拠: ViewerController.m 4324-4379行目
        // それ以外の場合
        if (!viewerSeries.contains(targetSeries)) {
            // HOROS-20240407準拠: ViewerController.m 4326-4365行目
            // viewerSeriesに含まれていない場合：新しいSeriesを読み込む
            // HOROS-20240407準拠: ViewerController.m 4358行目
            // [[BrowserController currentBrowser] loadSeries :series :self :YES
            // keyImagesOnly: displayOnlyKeyImages];
            // 現在のViewerControllerにSeriesを追加
            List<DicomImage> images = targetSeries.sortedImages();
            if (images != null && !images.isEmpty()) {
                System.out
                        .println("ViewerController.loadSelectedSeries: loading new series: " + targetSeries.getName());

                // HOROS-20240407準拠: fileListとpixListにSeriesを追加
                if (maxMovieIndex < MAX4D) {
                    List<Object> newFileList = new ArrayList<>();
                    List<Object> newPixList = new ArrayList<>();

                    for (DicomImage image : images) {
                        newFileList.add(image);
                        // HOROS-20240407準拠: DicomPixを作成してpixListに追加
                        // DCMPix *dcmPix = [[DCMPix alloc] initWithPath:...];
                        try {
                            com.jj.dicomviewer.model.DicomPix pix = new com.jj.dicomviewer.model.DicomPix(
                                    image,
                                    image.getFrameID() != null ? image.getFrameID() : 0,
                                    1);
                            newPixList.add(pix);
                        } catch (Exception e) {
                            System.err.println("Failed to create DicomPix for image: " + image.getSopInstanceUID()
                                    + ", " + e.getMessage());
                            // エラーの場合でもnullを追加してインデックスを合わせる
                            newPixList.add(null);
                        }
                    }

                    fileList[maxMovieIndex] = newFileList;
                    pixList[maxMovieIndex] = newPixList;
                    maxMovieIndex++;

                    // HOROS-20240407準拠: 新しいSeriesを表示
                    setMovieIndex((short) (maxMovieIndex - 1));

                    // HOROS-20240407準拠: ViewerController.m 4360-4361行目
                    // [self showCurrentThumbnail:self];
                    // [self updateNavigator];
                    // HOROS-20240407準拠: showSelected=trueで自動スクロール
                    updatePreviewMatrix(true);
                }
            }
        } else if (!targetSeries.equals(getCurrentSeries())) {
            // HOROS-20240407準拠: ViewerController.m 4367-4376行目
            // viewerSeriesに含まれているが、現在のcurMovieIndexと異なる場合：setMovieIndexで切り替え
            int idx = viewerSeries.indexOf(targetSeries);
            if (idx >= 0 && idx < maxMovieIndex) {
                setMovieIndex((short) idx);
                propagateSettings();
                // HOROS-20240407準拠: Series切り替え後にハイライトを更新
                updatePreviewMatrix(true);
            }
        } else {
            // HOROS-20240407準拠: ViewerController.m 4378行目
            // 同じ場合：mouseMovedを呼ぶ
            // TODO: mouseMovedの実装
        }

        // HOROS-20240407準拠: ViewerController.m 4321行目
        // [self syncThumbnails];
        syncThumbnails();
    }

    /**
     * 現在表示されているSeriesを取得
     * HOROS-20240407準拠: ViewerController.m 4367行目
     */
    private DicomSeries getCurrentSeries() {
        if (fileList[curMovieIndex] != null && !fileList[curMovieIndex].isEmpty()) {
            Object img = fileList[curMovieIndex].get(0);
            if (img instanceof DicomImage) {
                return ((DicomImage) img).getSeries();
            }
        }
        return null;
    }

    /**
     * ムービーインデックスを設定
     * HOROS-20240407準拠: ViewerController.m setMovieIndex:
     */
    private void setMovieIndex(short index) {
        // HOROS-20240407準拠: ViewerController.m 17502-17512行目
        // シリーズインデックスのループ処理
        curMovieIndex = index;
        if (curMovieIndex < 0) curMovieIndex = (short) (maxMovieIndex - 1);
        if (curMovieIndex >= maxMovieIndex) curMovieIndex = 0;

        // HOROS-20240407準拠: 画像を更新
        if (imageView != null && pixList[curMovieIndex] != null && !pixList[curMovieIndex].isEmpty()
                && fileList[curMovieIndex] != null && !fileList[curMovieIndex].isEmpty()) {

            // HOROS-20240407準拠: DCMView.setPixels()を呼び出す
            List<DicomPix> pixListForView = new ArrayList<>();
            for (Object pix : pixList[curMovieIndex]) {
                if (pix instanceof DicomPix) {
                    pixListForView.add((DicomPix) pix);
                }
            }

            List<Object> fileListForView = new ArrayList<>(fileList[curMovieIndex]);

            // HOROS-20240407準拠: DCMView.m 2176行目 -
            // setPixels:files:rois:firstImage:level:reset:
            imageView.setPixels(pixListForView, fileListForView, null, (short) 0, (char) 0, false);

            // HOROS-20240407準拠: 最初の画像を表示
            if (!pixListForView.isEmpty()) {
                imageView.setIndex(0);

                // HOROS-20240407準拠: 異なるシリーズに切り替わったので、scaleToFitを呼び出す
                // updatePresentationStateFromSeriesが未実装のため、ここで明示的に呼び出す
                // 理想的には、updatePresentationStateFromSeriesで保存されたスケール値を復元すべき
                imageView.scaleToFit();
                System.out.println("ViewerController.setMovieIndex: scaleToFit() called for new series");
            }

            System.out.println("ViewerController.setMovieIndex: setting movie index to " + index + ", pixList size: "
                    + pixListForView.size());

            // HOROS-20240407準拠: サムネイルのハイライトと自動スクロールを更新
            updatePreviewMatrix(true);
        }
    }

    /**
     * 設定を伝播
     * HOROS-20240407準拠: ViewerController.m propagateSettings
     */
    private void propagateSettings() {
        // TODO: 実装
        System.out.println("ViewerController.propagateSettings: propagating settings");
    }

    /**
     * スライダーの値を現在の画像インデックスに同期
     * HOROS-20240407準拠: ViewerController.m adjustSlider
     */
    public void adjustSlider() {
        if (imageView == null || slider == null) {
            return;
        }

        // HOROS-20240407準拠: ViewerController.m adjustSlider
        // if( [imageView flippedData]) [slider setIntValue: [pixList[ curMovieIndex] count] - [imageView curImage] -1];
        // else [slider setIntValue:[imageView curImage]];

        // flippedData は現時点では未実装のため、常にfalseとして扱う
        int curImage = imageView.curImage;

        // スライダーの最大値を更新
        if (pixList[curMovieIndex] != null) {
            int maxValue = pixList[curMovieIndex].size() - 1;
            if (maxValue < 0) maxValue = 0;
            slider.setMaximum(maxValue);
        }

        // スライダーの値を更新
        slider.setValue(curImage);

        System.out.println("ViewerController.adjustSlider: slider value set to " + curImage);
    }

    /**
     * スライダーが変更されたときの処理
     * HOROS-20240407準拠: スライダーのアクション処理（明示的なメソッド名は不明だが、adjustSliderと対応）
     */
    private void sliderAction() {
        if (imageView == null || slider == null) {
            return;
        }

        int sliderValue = slider.getValue();

        // HOROS-20240407準拠: スライダーの値に応じて画像インデックスを変更
        // flippedData は現時点では未実装のため、常にfalseとして扱う

        System.out.println("ViewerController.sliderAction: setting image index to " + sliderValue);

        // 画像インデックスを変更
        imageView.setIndex(sliderValue);
    }

    /**
     * HOROS-20240407準拠: ViewerController.m getDisplayedSeries
     * 開いているすべてのViewerControllerから、現在表示されているSeriesを取得
     */
    private static List<DicomSeries> getDisplayedSeries() {
        // HOROS-20240407準拠: ViewerController.m 596-611行目
        List<ViewerController> displayedViewers = getDisplayed2DViewers();
        List<DicomSeries> seriesArray = new ArrayList<>();

        for (ViewerController win : displayedViewers) {
            DicomSeries seriesObj = win.getCurrentSeries();
            if (seriesObj != null) {
                // HOROS-20240407準拠: 重複を避けるため、seriesInstanceUIDで比較
                boolean found = false;
                String seriesUID = seriesObj.getSeriesInstanceUID();
                if (seriesUID != null) {
                    for (DicomSeries existing : seriesArray) {
                        if (existing != null && seriesUID.equals(existing.getSeriesInstanceUID())) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    seriesArray.add(seriesObj);
                    System.out.println("ViewerController.getDisplayedSeries: added series " + seriesObj.getName()
                            + " (UID: " + seriesUID + ")");
                }
            }
        }

        System.out.println("ViewerController.getDisplayedSeries: returning " + seriesArray.size() + " series");
        return seriesArray;
    }

    /**
     * 現在のサムネイルを表示
     * HOROS-20240407準拠: ViewerController.m 5560行目 - showCurrentThumbnail:
     */
    public void showCurrentThumbnail(Object sender) {
        // HOROS-20240407準拠: ViewerController.m 5562-5565行目
        // NSInteger index = [[[previewMatrix cells]
        // valueForKeyPath:@"representedObject.object"] indexOfObject: [[fileList[
        // curMovieIndex] objectAtIndex:0] valueForKey:@"series"]];
        // if( index != NSNotFound)
        // [previewMatrix scrollCellToVisibleAtRow: index column:0];
        scrollAndHighlightSelectedSeries();
    }

    /**
     * サムネイルを同期
     * HOROS-20240407準拠: ViewerController.m 4582行目 - syncThumbnails
     */
    public void syncThumbnails() {
        // HOROS-20240407準拠: ViewerController.m 4584行目
        // ViewBoundsDidChangeProtect = YES;
        // Javaでは同期処理なので不要

        // HOROS-20240407準拠: ViewerController.m 4586-4610行目
        for (ViewerController v : getDisplayed2DViewers()) {
            boolean same = false;

            // HOROS-20240407準拠: ViewerController.m 4589-4593行目
            // if( [[NSUserDefaults standardUserDefaults] boolForKey:
            // @"SyncSeriesListForAllComparativeStudies"])
            // same = [v.currentStudy.patientUID isEqualToString:
            // self.currentStudy.patientUID];
            // else
            // same = [[v studyInstanceUID] isEqualToString: [self studyInstanceUID]];
            Preferences prefs = Preferences.userNodeForPackage(ViewerController.class);
            boolean syncForAllComparativeStudies = prefs.getBoolean("SyncSeriesListForAllComparativeStudies", false);

            if (syncForAllComparativeStudies) {
                // 同じ患者のStudyを同期
                String thisPatientUID = getCurrentStudy() != null ? getCurrentStudy().getPatientUID() : null;
                String vPatientUID = v.getCurrentStudy() != null ? v.getCurrentStudy().getPatientUID() : null;
                same = thisPatientUID != null && vPatientUID != null && thisPatientUID.equals(vPatientUID);
            } else {
                // 同じStudyを同期
                String thisStudyUID = getStudyInstanceUID();
                String vStudyUID = v.getStudyInstanceUID();
                same = thisStudyUID != null && vStudyUID != null && thisStudyUID.equals(vStudyUID);
            }

            if (v != this && same) {
                // HOROS-20240407準拠: ViewerController.m 4595-4597行目
                // [[v.previewMatrixScrollView contentView] scrollToPoint:
                // [[v.previewMatrixScrollView contentView] constrainScrollPoint:
                // [[previewMatrix superview] bounds].origin]];
                // [v.previewMatrixScrollView reflectScrolledClipView:
                // [v.previewMatrixScrollView contentView]];
                java.awt.Point currentScrollPosition = previewMatrixScrollView.getViewport().getViewPosition();
                v.previewMatrixScrollView.getViewport().setViewPosition(currentScrollPosition);
                v.previewMatrixScrollView.revalidate();
            }
        }

        // HOROS-20240407準拠: ViewerController.m 4611行目
        // ViewBoundsDidChangeProtect = NO;
        // Javaでは同期処理なので不要
    }
}
