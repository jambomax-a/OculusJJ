package com.jj.dicomviewer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.jj.dicomviewer.model.DicomImage;
import com.jj.dicomviewer.model.DicomPix;

/**
 * ViewerController - 2Dビューワー
 * HOROS-20240407準拠: ViewerController.h, ViewerController.m
 *
 * シンプルな実装 - HOROS-20240407に忠実に移植
 */
public class ViewerController extends JFrame {

    // HOROS-20240407準拠: 背景での読み込みタスクを管理するための共有エグゼキュータ
    private static final ExecutorService backgroundLoader = Executors
            .newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

    // HOROS-20240407準拠: ViewerController.h 239-242行目
    private DCMView imageView;
    private List<List<DicomPix>> pixList = new ArrayList<>();
    private List<List<DicomImage>> fileList = new ArrayList<>();

    // HOROS-20240407準拠: ViewerController.h 296行目
    // NSMutableArray *seriesView;
    private int currentImageIndex = 0;

    // HOROS-20240407準拠: ViewerController.h slider
    private JSlider imageSlider;

    // HOROS-20240407準拠: ViewerController.h previewMatrixScrollView
    // サムネイル表示用のスクロールビュー
    private JScrollPane previewMatrixScrollView;
    private JPanel previewMatrix;

    // 現在表示しているシリーズ
    private com.jj.dicomviewer.model.DicomSeries currentSeries;

    // HOROS-20240407準拠: 折りたたまれたスタディの管理
    private final java.util.Set<com.jj.dicomviewer.model.DicomStudy> collapsedStudies = new java.util.HashSet<>();

    /**
     * スタディが折りたたまれているか確認
     */
    public boolean isStudyCollapsed(com.jj.dicomviewer.model.DicomStudy study) {
        return collapsedStudies.contains(study);
    }

    // HOROS-20240407準拠: 開いているViewerControllerのリスト
    private static final List<ViewerController> displayedViewers = Collections.synchronizedList(new ArrayList<>());

    /**
     * コンストラクタ
     * HOROS-20240407準拠: ViewerController.m 7620行目 -
     * initWithPix:withFiles:withVolume:
     */
    public ViewerController() {
        super("DICOM Viewer");

        // リストに追加
        displayedViewers.add(this);

        // UI初期化
        initializeUI();

        // HOROS-20240407準拠: ViewerController.m 7715行目
        // toolbarPanel = [[ToolbarPanelController alloc] initForViewer: self
        // withToolbar: toolbar];
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(ViewerController.class);
        boolean useToolbarPanel = prefs.getBoolean("USETOOLBARPANEL", true);
        if (useToolbarPanel) {
            toolbarPanel = new ToolbarPanel(this);
        }
    }

    /**
     * UIを初期化
     */
    private void initializeUI() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // HOROS-20240407準拠: imageSliderを作成 (最上部に配置)
        imageSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        imageSlider.setFocusable(false);
        imageSlider.setOpaque(false);
        imageSlider.setVisible(false);
        imageSlider.setToolTipText("Image Selection Slider");

        // スライダーの外観を調整（Horosに寄せて薄くする）
        imageSlider.setPreferredSize(new java.awt.Dimension(100, 15));
        imageSlider.setBackground(Color.BLACK); // 背景を黒にする（画像エリアに馴染ませる）

        imageSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                // ユーザーによる操作の場合のみ画像を切り替える
                if (imageSlider.getValueIsAdjusting()) {
                    int index = imageSlider.getValue();
                    if (index != currentImageIndex) {
                        currentImageIndex = index;
                        imageView.setIndex(currentImageIndex);
                    }
                }
            }
        });

        add(imageSlider, BorderLayout.NORTH);

        // HOROS-20240407準拠: imageViewを作成
        imageView = new DCMView();
        // 枠の描画はDCMView内部で行うため、Swingの枠はなし
        imageView.setBorder(null);
        add(imageView, BorderLayout.CENTER);

        // HOROS-20240407準拠: サムネイル表示用のpreviewMatrixを作成
        previewMatrix = new JPanel();
        previewMatrix.setLayout(new BoxLayout(previewMatrix, BoxLayout.Y_AXIS));
        previewMatrix.setBackground(new Color(230, 230, 230)); // より明るいグレー (Horosのデフォルトに近い)

        previewMatrixScrollView = new JScrollPane(previewMatrix);
        previewMatrixScrollView.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        previewMatrixScrollView.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // ウィンドウクローズ時の処理
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // HOROS-20240407準拠: ToolbarPanelを閉じる
                if (toolbarPanel != null) {
                    toolbarPanel.setVisible(false);
                    toolbarPanel.dispose();
                }

                // HOROS-20240407準拠: ThumbnailsListPanelからこのビューワーを削除
                ThumbnailsListPanel panel = ThumbnailsListPanel.getInstanceForScreenIndex(0);
                if (panel != null) {
                    panel.viewerWillClose(ViewerController.this);
                }

                displayedViewers.remove(ViewerController.this);

                // HOROS-20240407準拠: タイル配置を再計算
                java.util.prefs.Preferences prefs = java.util.prefs.Preferences
                        .userNodeForPackage(ViewerController.class);
                boolean autoTiling = prefs.getBoolean("AUTOTILING", true);
                if (autoTiling) {
                    tileWindows();
                }

                dispose();
            }

            @Override
            public void windowActivated(WindowEvent e) {
                // HOROS-20240407準拠: メインウィンドウ（アクティブなビューワー）のフォーカス管理
                windowDidBecomeMain();
                if (imageView != null) {
                    System.out.println(
                            "[DEBUG] ViewerController.windowActivated - setting frontMost=true for " + getTitle());
                    imageView.setFrontMost(true);
                    imageView.becomeFirstResponder();
                }
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                // Focus lost from the window.
                // In Horos, the red border stays if the focus moves to a toolbar, thumbnails
                // list, or another related window.
                Window opposite = e.getOppositeWindow();
                if (opposite != null) {
                    // If moving to another viewer or the browser, then deactivate frontMost.
                    // Also check for ThumbnailsListPanel and ToolbarPanel to maintain the focus
                    // effect.
                    if (opposite instanceof ViewerController || opposite instanceof BrowserController) {
                        if (imageView != null) {
                            imageView.setFrontMost(false);
                        }
                    } else if (opposite instanceof ThumbnailsListPanel || opposite instanceof ToolbarPanel) {
                        // Keep frontMost=true because we're still interacting with the viewer's tool
                        // windows.
                        if (imageView != null) {
                            imageView.setFrontMost(true);
                        }
                    }
                }
            }
        });
    }

    /**
     * 画像データで初期化
     * HOROS-20240407準拠: ViewerController.m 7654-7655行目
     */
    public void initWithPix(List<List<Object>> f, List<List<Object>> d, byte[] v) {
        // changeImageData相当の処理
        changeImageData(f, d, v, true);

        // ウィンドウを表示
        showWindow();

        // HOROS-20240407準拠: ViewerController.m 3460行目 - windowDidBecomeMain:
        // ウィンドウがメインになったときの処理を呼び出す
        windowDidBecomeMain();

        if (imageView != null) {
            System.out.println("[DEBUG] ViewerController.initWithPix - initial activation for " + getTitle());
            imageView.setFrontMost(true);
            imageView.becomeFirstResponder();
        }

        // HOROS-20240407準拠: 画像の非同期読み込みを開始
        startLoadImageThread();
    }

    /**
     * 画像の非同期読み込みを開始
     * HOROS-20240407準拠: ViewerController.m startLoadImageThread
     */
    public void startLoadImageThread() {
        backgroundLoader.submit(() -> {
            // 現在の画像を最優先でロード
            if (imageView != null && imageView.getCurDCM() != null) {
                imageView.getCurDCM().loadImage();
                SwingUtilities.invokeLater(() -> imageView.repaint());
            }

            // 残りの画像をロード
            synchronized (pixList) {
                for (List<DicomPix> row : pixList) {
                    for (DicomPix pix : row) {
                        if (pix != null) {
                            pix.loadImage();
                        }
                    }
                }
            }
            // 全て読み込み終わったら再描画（アノテーションなどの更新のため）
            SwingUtilities.invokeLater(() -> {
                if (imageView != null)
                    imageView.repaint();
            });
        });
    }

    /**
     * 画像データを変更
     * HOROS-20240407準拠: ViewerController.m changeImageData::::
     */
    public void changeImageData(List<List<Object>> f, List<List<Object>> d, byte[] v, boolean firstTime) {
        if (f == null || f.isEmpty() || d == null || d.isEmpty()) {
            return;
        }

        // pixListとfileListをクリア
        pixList.clear();
        fileList.clear();

        // データをコピー
        for (int i = 0; i < f.size(); i++) {
            List<Object> pixArray = f.get(i);
            List<Object> fileArray = d.get(i);

            List<DicomPix> pixRow = new ArrayList<>();
            List<DicomImage> fileRow = new ArrayList<>();

            for (int j = 0; j < pixArray.size(); j++) {
                if (pixArray.get(j) instanceof DicomPix) {
                    pixRow.add((DicomPix) pixArray.get(j));
                }
                if (fileArray.get(j) instanceof DicomImage) {
                    fileRow.add((DicomImage) fileArray.get(j));
                }
            }

            pixList.add(pixRow);
            fileList.add(fileRow);
        }

        // currentSeriesをセット (Horos準拠: 代表的なイメージから取得)
        if (!fileList.isEmpty() && !fileList.get(0).isEmpty()) {
            this.currentSeries = fileList.get(0).get(0).getSeries();
        }

        // 最初の画像を表示
        if (!pixList.isEmpty() && !pixList.get(0).isEmpty()) {
            currentImageIndex = 0;
            // HOROS-20240407準拠: DCMView.setPixels()を呼び出す
            List<Object> files = new ArrayList<>(fileList.get(0));
            imageView.setPixels(pixList.get(0), files, new ArrayList<>(), (short) currentImageIndex, 'i', true);
        }

        // HOROS-20240407準拠: スライダーの状態を更新
        adjustSlider();
    }

    /**
     * ウィンドウを表示
     * HOROS-20240407準拠: ViewerController.m 8755行目 - showWindowTransition
     */
    private void showWindow() {
        // HOROS-20240407準拠: AUTOTILING設定を確認
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(ViewerController.class);
        boolean autoTiling = prefs.getBoolean("AUTOTILING", true);

        if (autoTiling) {
            // ウィンドウが表示される前に配置を計算する
            // これにより、最大化状態で表示されてから縮小される現象を防ぐ
            tileWindows();
        } else {
            // 画面サイズを取得
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();

            // タスクバーを除いた領域を取得
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Insets insets = toolkit.getScreenInsets(gd.getDefaultConfiguration());

            boolean useToolbarPanel = prefs.getBoolean("USETOOLBARPANEL", true);
            boolean useFloatingThumbnails = prefs.getBoolean("UseFloatingThumbnailsList", true);

            int toolbarHeight = useToolbarPanel ? ToolbarPanel.exposedHeight() : 0;
            int thumbnailWidth = useFloatingThumbnails ? ThumbnailsListPanel.getFixedWidth() : 0;

            int x = screenBounds.x + insets.left + thumbnailWidth;
            int y = screenBounds.y + insets.top + toolbarHeight;
            int width = screenBounds.width - insets.left - insets.right - thumbnailWidth;
            int height = screenBounds.height - insets.top - insets.bottom - toolbarHeight;

            setBounds(x, y, width, height);
        }

        setVisible(true);

        // HOROS-20240407準拠: ビューワーウィンドウを最前面に表示
        toFront();
        requestFocus();
    }

    /**
     * サムネイルボタンがクリックされたときの処理
     * HOROS-20240407準拠: ViewerController.m 4413行目 - matrixPreviewPressed:
     */
    public void matrixPreviewPressed(ThumbnailButton button) {
        if (button == null) {
            return;
        }

        Object representedObject = button.getRepresentedObject();
        boolean rightClick = button.isRightClick();

        // HOROS-20240407準拠: スタディセルの場合、開閉を切り替える
        if (representedObject instanceof com.jj.dicomviewer.model.DicomStudy) {
            com.jj.dicomviewer.model.DicomStudy study = (com.jj.dicomviewer.model.DicomStudy) representedObject;
            if (collapsedStudies.contains(study)) {
                collapsedStudies.remove(study);
            } else {
                collapsedStudies.add(study);
            }
            // マトリクスを再構築
            buildMatrixPreview();
            return;
        }

        // HOROS-20240407準拠: ViewerController.m 4433行目
        loadSelectedSeries(representedObject, rightClick);
    }

    /**
     * 選択されたシリーズを読み込む
     * HOROS-20240407準拠: ViewerController.m 4272行目 - loadSelectedSeries:rightClick:
     */
    private void loadSelectedSeries(Object series, boolean rightClick) {
        if (series == null) {
            return;
        }

        // HOROS-20240407準拠: オブジェクトの種類を判定してSeriesを取り出す
        com.jj.dicomviewer.model.DicomSeries targetSeries = null;
        if (series instanceof com.jj.dicomviewer.model.DicomSeries) {
            targetSeries = (com.jj.dicomviewer.model.DicomSeries) series;
        } else if (series instanceof com.jj.dicomviewer.model.DicomStudy) {
            com.jj.dicomviewer.model.DicomStudy study = (com.jj.dicomviewer.model.DicomStudy) series;
            if (study.getSeries() != null && !study.getSeries().isEmpty()) {
                targetSeries = study.getSeries().iterator().next(); // 最初のシリーズを使用
            }
        }

        if (targetSeries == null) {
            return;
        }

        if (rightClick) {
            // HOROS-20240407準拠: 右クリック（またはTiling要求）の場合、新しいViewerControllerを開く
            BrowserController browser = BrowserController.currentBrowser();
            if (browser != null) {
                browser.openViewerForSeries(targetSeries);
            }
        } else {
            // HOROS-20240407準拠: 左クリックの場合、現在のビューワーの内容を差し替える (Reuse)
            loadSeries(targetSeries);
        }
    }

    /**
     * 開いている2DビューワーのリストALL
     * HOROS-20240407準拠: ViewerController.m 12138行目 - getDisplayed2DViewers
     */
    public static List<ViewerController> getDisplayed2DViewers() {
        synchronized (displayedViewers) {
            return new ArrayList<>(displayedViewers);
        }
    }

    /**
     * ウィンドウをタイル配置
     * HOROS-20240407準拠: AppController.m 4857行目 - tileWindows:
     */
    // HOROS-20240407準拠: タイル配置を一時的に無効化するためのフラグ
    private static boolean inhibitTiling = false;

    public static void setInhibitTiling(boolean b) {
        inhibitTiling = b;
    }

    public static void tileWindows() {
        if (inhibitTiling) {
            return;
        }
        List<ViewerController> viewers = getDisplayed2DViewers();
        if (viewers.isEmpty()) {
            return;
        }

        // スクリーン領域（タスクバーを除いた有効領域）を取得
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();
        java.awt.Insets insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gd.getDefaultConfiguration());

        // HOROS-20240407準拠: ToolbarPanelとThumbnailsListPanelのサイズを考慮してタイル配置領域を制限
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(ViewerController.class);
        boolean useToolbarPanel = prefs.getBoolean("USETOOLBARPANEL", true);
        boolean useFloatingThumbnails = prefs.getBoolean("UseFloatingThumbnailsList", true);

        int toolbarHeight = useToolbarPanel ? ToolbarPanel.exposedHeight() : 0;
        int thumbnailWidth = useFloatingThumbnails ? ThumbnailsListPanel.getFixedWidth() : 0;

        // タイル配置に使用可能な領域を計算
        int availX = screenBounds.x + insets.left + thumbnailWidth;
        int availY = screenBounds.y + insets.top + toolbarHeight;
        int availWidth = screenBounds.width - insets.left - insets.right - thumbnailWidth;
        int availHeight = screenBounds.height - insets.top - insets.bottom - toolbarHeight;

        // HOROS-20240407準拠: 画面比率を考慮したタイル配置ロジック
        // AppController.m 5153-5188行目
        int count = viewers.size();
        int cols = 1;
        int rows = 1;

        double screenRatio = (double) availWidth / availHeight;
        double landscapeRatio = screenRatio > 1.7 ? 2.0 : 1.5;

        // HOROS-20240407準拠: 奇数枚数（3枚, 5枚, 7枚...）の時の特徴的な配置
        // 最初のウィンドウを右端に縦長で配置し、残りを左側にグリッド状に配置
        if (count % 2 != 0 && count > 1) {
            rows = 2;
            cols = (count + 1) / 2;
        } else {
            // ウィンドウ数に合わせてグリッドを拡大
            while (rows * cols < count) {
                if ((double) cols / rows < landscapeRatio) {
                    cols++;
                } else {
                    rows++;
                }
            }

            // 垂直方向の余裕がある場合に調整
            if (rows * cols > count && rows * (cols - 1) >= count) {
                cols--;
            }
            if (rows * cols > count && (rows - 1) * cols >= count) {
                rows--;
            }
        }

        int cellWidth = availWidth / cols;
        int cellHeight = availHeight / rows;

        for (int i = 0; i < count; i++) {
            ViewerController v = viewers.get(i);

            if (count % 2 != 0 && count > 1) {
                if (i == 0) {
                    // HOROS-20240407準拠: 最初に選んだウィンドウを右端で縦長にスパン
                    v.setBounds(availX + (cols - 1) * cellWidth, availY, cellWidth, availHeight);
                } else {
                    // 残りのウィンドウは左側のグリッドに配置
                    int col = (i - 1) % (cols - 1);
                    int row = (i - 1) / (cols - 1);
                    v.setBounds(availX + col * cellWidth, availY + row * cellHeight, cellWidth, cellHeight);
                }
            } else {
                int row = i / cols;
                int col = i % cols;
                int x = availX + col * cellWidth;
                int y = availY + row * cellHeight;
                v.setBounds(x, y, cellWidth, cellHeight);
            }

            // HOROS-20240407準拠: ウィンドウサイズ確定後に画像をフィットさせる
            if (v.imageView != null) {
                v.imageView.scaleToFit();
            }

            // HOROS-20240407準拠: ウィンドウを前面に
            v.toFront();
        }

        // HOROS-20240407準拠: 全てのビューワーのサムネイルハイライトを更新
        refreshAllThumbnails();
    }

    /**
     * 全てのオープンされているビューワーのサムネイル表示を更新
     */
    public static void refreshAllThumbnails() {
        synchronized (displayedViewers) {
            for (ViewerController v : displayedViewers) {
                v.buildMatrixPreview();
            }
        }
    }

    /**
     * 2Dビューワーかどうかを返す
     * HOROS-20240407準拠: ViewerController.m is2DViewer
     */
    public boolean is2DViewer() {
        return true;
    }

    /**
     * 最前面の2Dビューワーを取得
     * HOROS-20240407準拠: ViewerController.m 12154行目 - frontMostDisplayed2DViewer
     */
    public static ViewerController frontMostDisplayed2DViewer() {
        synchronized (displayedViewers) {
            if (displayedViewers.isEmpty()) {
                return null;
            }
            // 簡易実装: リストの最後を最前面と見なす（実際はフォーカス状態をチェックすべき）
            for (int i = displayedViewers.size() - 1; i >= 0; i--) {
                ViewerController v = displayedViewers.get(i);
                if (v.isFocused()) {
                    return v;
                }
            }
            return displayedViewers.get(displayedViewers.size() - 1);
        }
    }

    /**
     * 全ての画像が読み込まれているか
     * HOROS-20240407準拠: ViewerController.h isEverythingLoaded
     */
    public boolean isEverythingLoaded() {
        // 簡易実装: 常にtrue（Javaでは遅延読み込みを制御していないため）
        return true;
    }

    /**
     * 指定されたシリーズを現在のビューワーに読み込む
     * HOROS-20240407準拠: BrowserController.m 12441-12448行目 (reuse of existing
     * viewer)
     */
    public void loadSeries(com.jj.dicomviewer.model.DicomSeries series) {
        if (series == null) {
            return;
        }
        this.currentSeries = series;

        BrowserController browser = BrowserController.currentBrowser();
        if (browser == null || browser.getDatabase() == null) {
            return;
        }

        // シリーズ内の画像をDBから取得（必要ならロード）
        browser.getDatabase().loadImagesForSeriesIfNeeded(series);
        java.util.Set<com.jj.dicomviewer.model.DicomImage> imageSet = series.getImages();

        if (imageSet == null || imageSet.isEmpty()) {
            return;
        }

        // インスタンス番号でソート
        List<com.jj.dicomviewer.model.DicomImage> loadList = new ArrayList<>(imageSet);
        loadList.sort((a, b) -> {
            int instanceA = a.getInstanceNumber() != null ? a.getInstanceNumber() : 0;
            int instanceB = b.getInstanceNumber() != null ? b.getInstanceNumber() : 0;
            if (instanceA != instanceB) {
                return Integer.compare(instanceA, instanceB);
            }
            return Integer.compare(a.getFrameID() != null ? a.getFrameID() : 0,
                    b.getFrameID() != null ? b.getFrameID() : 0);
        });

        // DCMPixと対応するオブジェクトのリストを作成
        List<com.jj.dicomviewer.model.DicomPix> viewerPix = new ArrayList<>();
        List<Object> correspondingObjects = new ArrayList<>();

        for (com.jj.dicomviewer.model.DicomImage img : loadList) {
            com.jj.dicomviewer.model.DicomPix dcmPix = img.getCachedDicomPix();
            if (dcmPix == null) {
                dcmPix = new com.jj.dicomviewer.model.DicomPix(
                        img,
                        img.getFrameID() != null ? img.getFrameID() : 0,
                        loadList.size());
                img.setCachedDicomPix(dcmPix);
            }
            viewerPix.add(dcmPix);
            correspondingObjects.add(img);
        }

        // 多次元リストにラップして反映
        List<List<Object>> pixList2D = new ArrayList<>();
        pixList2D.add(new ArrayList<>(viewerPix));
        List<List<Object>> fileList2D = new ArrayList<>();
        fileList2D.add(new ArrayList<>(correspondingObjects));

        changeImageData(pixList2D, fileList2D, null, false);

        // HOROS-20240407準拠: サムネイルを更新 (選択ハイライト等の反映のため)
        buildMatrixPreview();

        // HOROS-20240407準拠: サムネイルパネルの選択状態を更新
        ThumbnailsListPanel panel = ThumbnailsListPanel.getInstanceForScreenIndex(0);
        if (panel != null) {
            panel.setThumbnailsView(previewMatrixScrollView, this);
        }
    }

    /**
     * 前のシリーズへ移動
     * HOROS-20240407準拠: ViewerController.m 12461行目 - loadSeriesUp
     */
    public void loadSeriesUp() {
        loadSeriesRelative(-1);
    }

    /**
     * 次のシリーズへ移動
     * HOROS-20240407準拠: ViewerController.m 12465行目 - loadSeriesDown
     */
    public void loadSeriesDown() {
        loadSeriesRelative(1);
    }

    private void loadSeriesRelative(int delta) {
        if (currentSeries == null)
            return;
        com.jj.dicomviewer.model.DicomStudy study = currentSeries.getStudy();
        if (study == null)
            return;

        // DBから全シリーズ取得
        List<com.jj.dicomviewer.model.DicomSeries> seriesList = new ArrayList<>(study.getSeries());
        // ソート（SeriesNumber）
        seriesList.sort((a, b) -> {
            Integer an = a.getSeriesNumber();
            Integer bn = b.getSeriesNumber();
            if (an == null)
                return -1;
            if (bn == null)
                return 1;
            return an.compareTo(bn);
        });

        int currentIndex = -1;
        for (int i = 0; i < seriesList.size(); i++) {
            if (seriesList.get(i).getSeriesInstanceUID().equals(currentSeries.getSeriesInstanceUID())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1)
            return;

        int nextIndex = currentIndex + delta;
        if (nextIndex >= 0 && nextIndex < seriesList.size()) {
            loadSeries(seriesList.get(nextIndex));
        }
    }

    /**
     * スライダーを調整
     * HOROS-20240407準拠: ViewerController.m adjustSlider
     */
    public void adjustSlider() {
        if (imageView == null || pixList == null || pixList.isEmpty()) {
            if (imageSlider != null) {
                imageSlider.setVisible(false);
            }
            return;
        }

        // 現在表示中のシリーズの枚数を取得
        // pixList.get(0) は現在ロードされている単一のシリーズリストを想定
        int totalImages = 0;
        if (!pixList.isEmpty() && pixList.get(0) != null) {
            totalImages = pixList.get(0).size();
        }

        if (totalImages <= 1) {
            // HOROS-20240407準拠: 1枚の時も表示はするが、無効化する
            imageSlider.setVisible(true);
            imageSlider.setEnabled(false);
            imageSlider.setMinimum(0);
            imageSlider.setMaximum(0);
            imageSlider.setValue(0);
        } else {
            imageSlider.setVisible(true);
            imageSlider.setEnabled(true);
            // スライダーの範囲を更新 (0 to totalImages - 1)
            imageSlider.setMinimum(0);
            imageSlider.setMaximum(totalImages - 1);

            // 現在のインデックスをセット
            int curIndex = imageView.getCurImage();
            if (curIndex != imageSlider.getValue()) {
                imageSlider.setValue(curIndex);
            }

            imageSlider.setVisible(true);
        }

        // レイアウトを更新
        revalidate();
    }

    /**
     * ウィンドウを表示（トランジション付き）
     * HOROS-20240407準拠: ViewerController.m showWindowTransition
     */
    public void showWindowTransition() {
        showWindow();
    }

    /**
     * ウィンドウがメインになったときの処理
     * HOROS-20240407準拠: ViewerController.m 3460行目 - windowDidBecomeMain:
     */
    /**
     * 表示されているビューワーの数
     */
    public static int getDisplayedViewersCount() {
        return displayedViewers.size();
    }

    public void windowDidBecomeMain() {
        // 他のすべてのビューワーのfrontMostを解除
        synchronized (displayedViewers) {
            for (ViewerController v : displayedViewers) {
                if (v != this && v.imageView != null) {
                    v.imageView.setFrontMost(false);
                }
            }
        }

        // HOROS-20240407準拠: ViewerController.m 3399-3420行目
        // サムネイルウィンドウを表示
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(ViewerController.class);
        boolean useFloatingThumbnails = prefs.getBoolean("UseFloatingThumbnailsList", true);

        if (useFloatingThumbnails) {
            // HOROS-20240407準拠: サムネイルを構築
            buildMatrixPreview();

            // 画面に対応するThumbnailsListPanelを取得
            ThumbnailsListPanel panel = ThumbnailsListPanel.getInstanceForScreenIndex(0);
            if (panel != null) {
                // HOROS-20240407準拠: ViewerController.m 3412行目
                // [thumbnailsListPanel[ i] setThumbnailsView: previewMatrixScrollView viewer:
                // self];
                panel.setThumbnailsView(previewMatrixScrollView, this);
            }
        }

        // HOROS-20240407準拠: ViewerController.m 3388-3397行目
        // ツールバーウィンドウを表示
        boolean useToolbarPanel = prefs.getBoolean("USETOOLBARPANEL", true);
        if (useToolbarPanel && toolbarPanel != null) {
            toolbarPanel.setVisible(true);
            toolbarPanel.toFront();
        } else if (toolbarPanel != null) {
            toolbarPanel.setVisible(false);
        }

        // HOROS-20240407準拠: DCMViewのbecomeMainWindowを呼び出す
        if (imageView != null) {
            imageView.becomeMainWindow();
            imageView.becomeKeyWindow();
        }
    }

    /**
     * ツールバーパネル
     * HOROS-20240407準拠: ViewerController.h toolbarPanel
     */
    public ToolbarPanel toolbarPanel = null;

    /**
     * サムネイルマトリクスを構築
     * HOROS-20240407準拠: ViewerController.m 4903行目 - buildMatrixPreview:
     */
    public void buildMatrixPreview() {
        if (!isVisible()) {
            return;
        }

        // previewMatrixをクリア
        previewMatrix.removeAll();

        // HOROS-20240407準拠: 現在のシリーズからスタディを取得
        if (currentSeries == null) {
            // fileListから最初の画像を取得してシリーズを特定
            if (!fileList.isEmpty() && !fileList.get(0).isEmpty()) {
                DicomImage firstImage = fileList.get(0).get(0);
                if (firstImage != null) {
                    currentSeries = firstImage.getSeries();
                }
            }
        }

        if (currentSeries == null) {
            previewMatrix.revalidate();
            previewMatrix.repaint();
            return;
        }

        com.jj.dicomviewer.model.DicomDatabase db = com.jj.dicomviewer.model.DicomDatabase.defaultDatabase();

        // HOROS-20240407準拠: Viewerに含まれる全患者の全スタディをDBから取得して表示
        java.util.Set<String> patientUIDs = new java.util.HashSet<>();
        if (fileList != null) {
            for (java.util.List<com.jj.dicomviewer.model.DicomImage> list : fileList) {
                if (list != null && !list.isEmpty()) {
                    com.jj.dicomviewer.model.DicomImage firstImg = list.get(0);
                    if (firstImg != null && firstImg.getSeries() != null && firstImg.getSeries().getStudy() != null) {
                        String uid = firstImg.getSeries().getStudy().getPatientUID();
                        if (uid != null)
                            patientUIDs.add(uid);
                    }
                }
            }
        }

        // currentSeriesからも取得 (念のため)
        if (currentSeries != null && currentSeries.getStudy() != null) {
            String uid = currentSeries.getStudy().getPatientUID();
            if (uid != null)
                patientUIDs.add(uid);
        }

        java.util.Set<com.jj.dicomviewer.model.DicomStudy> studySet = new java.util.LinkedHashSet<>();
        for (String uid : patientUIDs) {
            studySet.addAll(db.getStudiesByPatientUID(uid));
        }

        java.util.List<com.jj.dicomviewer.model.DicomStudy> allStudies = new java.util.ArrayList<>(studySet);
        // スタディ日でソート（降順）
        allStudies.sort((a, b) -> {
            if (a.getDate() == null)
                return 1;
            if (b.getDate() == null)
                return -1;
            return b.getDate().compareTo(a.getDate());
        });

        System.out.println("buildMatrixPreview: 患者の全スタディ数 = " + allStudies.size());

        // HOROS-20240407準拠: 複数の患者が含まれているか判定
        boolean multiplePatients = false;
        if (!allStudies.isEmpty()) {
            String firstPatientUID = allStudies.get(0).getPatientUID();
            for (com.jj.dicomviewer.model.DicomStudy study : allStudies) {
                if (firstPatientUID != null && !firstPatientUID.equals(study.getPatientUID())) {
                    multiplePatients = true;
                    break;
                }
            }
        }

        ThumbnailButton targetButton = null;

        for (com.jj.dicomviewer.model.DicomStudy study : allStudies) {
            // シリーズ情報を事前に読み込んでシリーズ数を確定させる
            db.loadSeriesForStudyIfNeeded(study);
            int seriesCount = (study.getSeries() != null) ? study.getSeries().size() : 0;

            // スタディセルの作成
            ThumbnailButton studyButton = new ThumbnailButton(this);
            studyButton.setRepresentedObject(study);

            // スタディ情報をテキストに設定 (Horos準拠の複数行構成)
            StringBuilder studyInfo = new StringBuilder();
            studyInfo.append(allStudies.indexOf(study) + 1).append("\n"); // Index (空行なし)

            if (multiplePatients) {
                // HOROS-20240407準拠: 複数患者の場合はPatient Nameを1段目に表示
                studyInfo.append(study.getName() != null ? study.getName() : "Anonymous").append("\n");
                studyInfo.append(study.getStudyName() != null ? study.getStudyName() : "No Description").append("\n");
            } else {
                // 通常はStudy Description
                studyInfo.append(study.getStudyName() != null ? study.getStudyName() : "No Description").append("\n");
            }

            if (study.getDate() != null) {
                studyInfo.append(study.getDate().toLocalDate().toString()).append(" ")
                        .append(study.getDate().toLocalTime().toString()).append("\n");
            } else {
                studyInfo.append("No Date\n");
            }

            studyInfo.append(study.getModality() != null ? study.getModality() : "??")
                    .append(": ").append(seriesCount).append(seriesCount > 1 ? " series" : " series").append("\n");

            // Show Series / Hide series の表記 (Horos準拠)
            if (collapsedStudies.contains(study)) {
                studyInfo.append("Show Series");
            } else {
                studyInfo.append("Hide Series");
            }

            studyButton.setText(studyInfo.toString());

            // 自スタディか他スタディかで背景色を変える (Horos準拠)
            if (study.getStudyInstanceUID().equals(currentSeries.getStudy().getStudyInstanceUID())) {
                studyButton.setBackground(new Color(230, 230, 230)); // 標準に近い色
            } else {
                studyButton.setBackground(ThumbnailButton.DIFFERENT_STUDY_COLOR);
            }

            previewMatrix.add(studyButton);

            // スタディが折りたたまれていない場合、シリーズを表示
            if (!collapsedStudies.contains(study)) {
                db.loadSeriesForStudyIfNeeded(study);
                java.util.Set<com.jj.dicomviewer.model.DicomSeries> seriesSet = study.getSeries();

                if (seriesSet != null) {
                    // シリーズ番号でソート
                    java.util.List<com.jj.dicomviewer.model.DicomSeries> sortedSeries = new java.util.ArrayList<>(
                            seriesSet);
                    sortedSeries.sort((a, b) -> {
                        Integer aNum = a.getSeriesNumber();
                        Integer bNum = b.getSeriesNumber();
                        if (aNum == null)
                            return -1;
                        if (bNum == null)
                            return 1;
                        return aNum.compareTo(bNum);
                    });

                    for (com.jj.dicomviewer.model.DicomSeries series : sortedSeries) {
                        ThumbnailButton button = new ThumbnailButton(this);
                        button.setRepresentedObject(series);

                        // HOROS-20240407準拠: サムネイル画像を設定
                        byte[] thumbnailData = series.getThumbnail();
                        if (thumbnailData != null && thumbnailData.length > 0) {
                            try {
                                ImageIcon icon = new ImageIcon(thumbnailData);
                                button.setIcon(icon);
                            } catch (Exception e) {
                                // 読み込み失敗時は無視
                            }
                        } else {
                            // データベースにない場合、シリーズの最初の画像から動的に生成
                            java.util.Set<com.jj.dicomviewer.model.DicomImage> images = series.getImages();
                            if (images != null && !images.isEmpty()) {
                                com.jj.dicomviewer.model.DicomImage firstImage = images.iterator().next();
                                if (firstImage != null) {
                                    final ThumbnailButton btn = button;
                                    backgroundLoader.submit(() -> {
                                        try {
                                            com.jj.dicomviewer.model.DicomPix pix = new com.jj.dicomviewer.model.DicomPix(
                                                    firstImage);
                                            // 100x120のサムネイルを生成
                                            java.awt.image.BufferedImage thumb = pix.generateThumbnail(100, 120);
                                            if (thumb != null) {
                                                SwingUtilities.invokeLater(() -> {
                                                    btn.setIcon(new ImageIcon(thumb));
                                                    btn.repaint();
                                                });
                                            }
                                        } catch (Exception e) {
                                            // 生成失敗時は無視
                                        }
                                    });
                                }
                            }
                        }

                        // シリーズ情報をテキストに設定 (3行: 名前\n日時\n画像数)
                        // HOROS-20240407準拠: 1段目はSeries.name (seriesDescription)
                        StringBuilder seriesInfo = new StringBuilder();
                        String seriesName = (series.getName() != null && !series.getName().isEmpty()) ? series.getName()
                                : "Unnamed";
                        seriesInfo.append(seriesName);

                        seriesInfo.append("\n");
                        if (series.getDate() != null) {
                            seriesInfo.append(series.getDate().toLocalDate().toString());
                        } else {
                            seriesInfo.append("No Date");
                        }

                        seriesInfo.append("\n");
                        int count = series.getNumberOfImages() != null ? series.getNumberOfImages() : 0;
                        seriesInfo.append(count).append(" Images");

                        button.setText(seriesInfo.toString());

                        // HOROS-20240407準拠: 自スタディか他スタディかで背景色を変える
                        if (series.getStudy().getStudyInstanceUID()
                                .equals(currentSeries.getStudy().getStudyInstanceUID())) {
                            button.setBackground(new Color(230, 230, 230)); // 標準色
                        } else {
                            button.setBackground(ThumbnailButton.DIFFERENT_STUDY_COLOR); // 他スタディは暗く (0.55 gray)
                        }

                        // 現在のシリーズをハイライト (Horos準拠: 鮭色)
                        if (series.getSeriesInstanceUID().equals(currentSeries.getSeriesInstanceUID())) {
                            button.setBackground(ThumbnailButton.SELECTED_ITEM_COLOR);
                            targetButton = button;
                        } else {
                            // HOROS-20240407準拠: 他のビューワーで開かれているシリーズを黄色にハイライト
                            boolean isOpenInOtherViewer = false;
                            synchronized (displayedViewers) {
                                for (ViewerController v : displayedViewers) {
                                    if (v != ViewerController.this && v.currentSeries != null
                                            && v.currentSeries.getSeriesInstanceUID()
                                                    .equals(series.getSeriesInstanceUID())) {
                                        isOpenInOtherViewer = true;
                                        break;
                                    }
                                }
                            }
                            if (isOpenInOtherViewer) {
                                button.setBackground(ThumbnailButton.OPEN_ITEM_COLOR);
                            }
                        }

                        previewMatrix.add(button);
                    }
                }
            }
        }

        previewMatrix.revalidate();
        previewMatrix.repaint();

        // HOROS-20240407準拠: 選択されたシリーズまでスクロール
        if (targetButton != null) {
            final ThumbnailButton finalButton = targetButton;
            SwingUtilities.invokeLater(() -> {
                Rectangle rect = new Rectangle(0, 0, finalButton.getWidth(), finalButton.getHeight());
                finalButton.scrollRectToVisible(rect);
            });
        }
    }
}
