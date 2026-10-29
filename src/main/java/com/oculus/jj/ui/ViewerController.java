package com.oculus.jj.ui;

import com.oculus.jj.utils.UIStyle;
import com.oculus.jj.utils.LanguageManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.oculus.jj.model.DicomImage;
import com.oculus.jj.model.DicomPix;

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

    // HOROS-20240407準拠: サムネイル生成用の制限されたスレッドプール
    private static final ExecutorService thumbnailLoader = Executors.newFixedThreadPool(2);

    static {
        // Global key interceptor for Horos shortcuts (H, V, Tab, Arrows, etc.)
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new java.awt.KeyEventDispatcher() {
                    @Override
                    public boolean dispatchKeyEvent(java.awt.event.KeyEvent e) {
                        if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                            int keyCode = e.getKeyCode();
                            if (keyCode == java.awt.event.KeyEvent.VK_H || keyCode == java.awt.event.KeyEvent.VK_V
                                    || keyCode == java.awt.event.KeyEvent.VK_TAB
                                    || keyCode == java.awt.event.KeyEvent.VK_LEFT
                                    || keyCode == java.awt.event.KeyEvent.VK_RIGHT
                                    || keyCode == java.awt.event.KeyEvent.VK_UP
                                    || keyCode == java.awt.event.KeyEvent.VK_DOWN
                                    || keyCode == java.awt.event.KeyEvent.VK_PAGE_UP
                                    || keyCode == java.awt.event.KeyEvent.VK_PAGE_DOWN
                                    || keyCode == java.awt.event.KeyEvent.VK_HOME
                                    || keyCode == java.awt.event.KeyEvent.VK_END
                                    || (keyCode >= java.awt.event.KeyEvent.VK_1
                                            && keyCode <= java.awt.event.KeyEvent.VK_5)
                                    || keyCode == java.awt.event.KeyEvent.VK_W
                                    || keyCode == java.awt.event.KeyEvent.VK_Z
                                    || keyCode == java.awt.event.KeyEvent.VK_P
                                    || keyCode == java.awt.event.KeyEvent.VK_M
                                    || keyCode == java.awt.event.KeyEvent.VK_DELETE
                                    || keyCode == java.awt.event.KeyEvent.VK_BACK_SPACE) {

                                // Find the active ViewerController
                                ViewerController vc = null;
                                Window activeWindow = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                        .getActiveWindow();

                                // 3Dビューワーがアクティブな場合は、2D側のグローバルショートカットを無効化する
                                if (activeWindow instanceof Viewer3DController) {
                                    return false;
                                }

                                // Debug log to see what window is active
                                // if (activeWindow != null)

                                if (activeWindow instanceof ViewerController) {
                                    vc = (ViewerController) activeWindow;
                                } else if (activeWindow instanceof ToolbarPanel) {
                                    vc = ((ToolbarPanel) activeWindow).getViewer();
                                }

                                if (vc == null) {
                                    // Robust search
                                    for (ViewerController v : getDisplayedViewers()) {
                                        if (v.isActive() || v.isFocused()) {
                                            vc = v;
                                            break;
                                        }
                                    }
                                }

                                // Final fallback: use the first displayed viewer if there's only one
                                if (vc == null) {
                                    List<ViewerController> viewers = getDisplayedViewers();
                                    if (viewers.size() == 1) {
                                        vc = viewers.get(0);
                                    }
                                }

                                if (vc != null) {
                                    DCMView view = vc.getImageView();
                                    if (view != null) {
                                        //
                                        if (keyCode == java.awt.event.KeyEvent.VK_H) {
                                            // Horos: 'H' is Horizontal Flip
                                            view.toggleXFlipped();
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_V) {
                                            // Horos: 'V' is Vertical Flip
                                            view.toggleYFlipped();
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_TAB) {
                                            // Horos: 'Tab' cycles annotations
                                            int currentIdx = view.getAnnotationType();
                                            int nextIdx = currentIdx + 1;
                                            if (nextIdx > 3)
                                                nextIdx = 0;
                                            view.setAnnotationType(nextIdx);
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_LEFT) {
                                            view.setIndex(view.getCurImage() - 1);
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_RIGHT) {
                                            view.setIndex(view.getCurImage() + 1);
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_UP) {
                                            view.setScaleValue(view.getScaleValue() + 0.02f);
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_DOWN) {
                                            view.setScaleValue(view.getScaleValue() - 0.02f);
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_PAGE_UP) {
                                            view.setIndex(view.getCurImage() - 10);
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_PAGE_DOWN) {
                                            view.setIndex(view.getCurImage() + 10);
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_HOME) {
                                            view.setIndex(0);
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_END) {
                                            if (view.getDcmPixList() != null) {
                                                view.setIndex(view.getDcmPixList().size() - 1);
                                            }
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_1) {
                                            // Horos: '1' is Brain (WL: 40, WW: 80)
                                            if (view.getCurDCM() != null) {
                                                view.getCurDCM().setWindowLevelWidth(40, 80);
                                                view.sendSyncMessageWLWW(40, 80);
                                                view.repaint();
                                            }
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_2) {
                                            // Horos: '2' is Bone (WL: 300, WW: 1500)
                                            if (view.getCurDCM() != null) {
                                                view.getCurDCM().setWindowLevelWidth(300, 1500);
                                                view.sendSyncMessageWLWW(300, 1500);
                                                view.repaint();
                                            }
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_3) {
                                            // Horos: '3' is Lung (WL: -600, WW: 1500)
                                            if (view.getCurDCM() != null) {
                                                view.getCurDCM().setWindowLevelWidth(-600, 1500);
                                                view.sendSyncMessageWLWW(-600, 1500);
                                                view.repaint();
                                            }
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_4) {
                                            // Horos: '4' is Soft Tissue (WL: 40, WW: 400)
                                            if (view.getCurDCM() != null) {
                                                view.getCurDCM().setWindowLevelWidth(40, 400);
                                                view.sendSyncMessageWLWW(40, 400);
                                                view.repaint();
                                            }
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_5) {
                                            // Horos: '5' is Reset
                                            if (view.getCurDCM() != null) {
                                                view.getCurDCM().resetWLWW();
                                                view.sendSyncMessageWLWW(view.getCurDCM().getWindowLevel(),
                                                        view.getCurDCM().getWindowWidth());
                                                view.repaint();
                                            }
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_W) {
                                            DCMView.currentTool = DCMView.ToolMode.tWLWW;
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_Z) {
                                            DCMView.currentTool = DCMView.ToolMode.tZoom;
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_P) {
                                            DCMView.currentTool = DCMView.ToolMode.tTranslate;
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_M) {
                                            DCMView.currentTool = DCMView.ToolMode.tMesure;
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_O) {
                                            DCMView.currentTool = DCMView.ToolMode.tOval;
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_R) {
                                            DCMView.currentTool = DCMView.ToolMode.tROI;
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_A) {
                                            DCMView.currentTool = DCMView.ToolMode.tAngle;
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_S) {
                                            DCMView.currentTool = DCMView.ToolMode.tPlain;
                                        } else if (keyCode == java.awt.event.KeyEvent.VK_DELETE
                                                || keyCode == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                                            // 選択中のROIを削除
                                            if (view.selectedRoi != null) {
                                                com.oculus.jj.model.ROI toRemove = view.selectedRoi;
                                                view.selectedRoi = null;
                                                if (view.getCurDCM() != null) {
                                                    view.getCurDCM().removeROI(toRemove);
                                                    // 表示リストを更新
                                                    view.curRoiList = view.getCurDCM().getROIs();

                                                    // データベースからも削除
                                                    com.oculus.jj.model.DicomDatabase db = com.oculus.jj.model.DicomDatabase
                                                            .activeLocalDatabase();
                                                    if (db != null) {
                                                        db.deleteROI(toRemove);
                                                    }
                                                    view.repaint();
                                                }
                                            }
                                        }
                                        return true; // Consume event
                                    }
                                }
                            }
                        }
                        return false;
                    }
                });
    }

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
    private com.oculus.jj.model.DicomSeries currentSeries;

    // HOROS-20240407準拠: 折りたたまれたスタディの管理
    private final java.util.Set<com.oculus.jj.model.DicomStudy> collapsedStudies = new java.util.HashSet<>();

    // マトリックス構築中フラグ
    private boolean isBuildingMatrix = false;

    /**
     * スタディが折りたたまれているか確認
     */
    public boolean isStudyCollapsed(com.oculus.jj.model.DicomStudy study) {
        return collapsedStudies.contains(study);
    }

    // HOROS-20240407準拠: 開いているViewerControllerのリスト
    private static final List<ViewerController> displayedViewers = Collections.synchronizedList(new ArrayList<>());

    public static List<ViewerController> getDisplayedViewers() {
        return new ArrayList<>(displayedViewers);
    }

    public DCMView getImageView() {
        return imageView;
    }

    /**
     * 現在表示中のDicomImageを取得します。
     */
    public DicomImage getCurrentImage() {
        synchronized (fileList) {
            if (!fileList.isEmpty() && !fileList.get(0).isEmpty()) {
                int index = (imageView != null) ? imageView.getCurImage() : currentImageIndex;
                if (index >= 0 && index < fileList.get(0).size()) {
                    return fileList.get(0).get(index);
                }
            }
        }
        return null;
    }

    public List<List<DicomPix>> getPixList() {
        return pixList;
    }

    /**
     * コンストラクタ
     * HOROS-20240407準拠: ViewerController.m 7620行目 -
     * initWithPix:withFiles:withVolume:
     */
    public ViewerController() {
        super(LanguageManager.get("viewer.title", "DICOM Viewer"));

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

        // HOROS-20240407準拠: 2D Viewerもメインメニューを表示する（Windowsでのアクセシビリティのため）
        // ユーザー要求により削除: ２Dビューワーパネルのメニューはいらない
        /*
         * BrowserController browser = BrowserController.currentBrowser();
         * if (browser != null && browser.getJMenuBar() != null) {
         * javax.swing.JMenuBar menuBar = replicateMenuBar(browser.getJMenuBar());
         * // Windowsでの視認性向上のための設定
         * menuBar.setOpaque(true);
         * menuBar.setBackground(new Color(240, 240, 240));
         * setJMenuBar(menuBar);
         * }
         */

        setLayout(new BorderLayout());

        // HOROS-20240407準拠: imageSliderを作成 (最上部に配置)
        imageSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        imageSlider.setFocusable(false);
        imageSlider.setOpaque(false);
        imageSlider.setVisible(false);
        imageSlider.setToolTipText(LanguageManager.get("viewer.slider", "Image Selection Slider"));

        // スライダーの外観を調整（Horosに寄せて薄くする）
        imageSlider.setPreferredSize(new java.awt.Dimension(100, 15));
        imageSlider.setBackground(Color.BLACK); // 背景を黒にする（画像エリアに馴染ませる）

        imageSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                // スライダーの値が現在のインデックスと異なる場合に更新
                int index = imageSlider.getValue();
                if (index != currentImageIndex) {
                    currentImageIndex = index;
                    // HOROS-20240407準拠: 操作したビューをアクティブにする
                    if (imageView != null) {
                        imageView.becomeFirstResponder();
                        imageView.setIndex(currentImageIndex);
                    }

                    // 交差線を描画するために、他のビューアーも再描画を促す
                    for (ViewerController vc : ViewerController.getDisplayedViewers()) {
                        if (vc != ViewerController.this && vc.getImageView() != null) {
                            vc.getImageView().repaint();
                        }
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
                SwingUtilities.invokeLater(() -> {
                    imageView.repaint();
                });
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
        if (representedObject instanceof com.oculus.jj.model.DicomStudy) {
            com.oculus.jj.model.DicomStudy study = (com.oculus.jj.model.DicomStudy) representedObject;
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
        com.oculus.jj.model.DicomSeries targetSeries = null;
        if (series instanceof com.oculus.jj.model.DicomSeries) {
            targetSeries = (com.oculus.jj.model.DicomSeries) series;
        } else if (series instanceof com.oculus.jj.model.DicomStudy) {
            com.oculus.jj.model.DicomStudy study = (com.oculus.jj.model.DicomStudy) series;
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

        // HOROS-20240407準拠: ウィンドウを左上から順にソート (AppController.m 4913-4958行目相当)
        // Javaでは画面上の位置(Y, X)でソートする
        viewers.sort((v1, v2) -> {
            int y1 = v1.getY();
            int y2 = v2.getY();
            if (Math.abs(y1 - y2) > 50) { // 同じ行とみなすしきい値
                return Integer.compare(y1, y2);
            }
            return Integer.compare(v1.getX(), v2.getX());
        });

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
        // AppController.m 5110-5188行目
        int viewerCount = viewers.size();

        boolean landscape = (availWidth > availHeight);

        // HOROS-20240407準拠: 比率の計算 (1.5 または 2.0)
        float landscapeRatio = 1.5f;
        if ((float) screenBounds.width / screenBounds.height > 1.7f)
            landscapeRatio = 2.0f;

        float portraitRatio = 0.9f;
        if ((float) screenBounds.height / screenBounds.width > 1.7f)
            portraitRatio = 0.49f;

        int rows = 1;
        int cols = 1;

        // HOROS-20240407準拠: 2枚以上の場合は初期値を2x1または1x2にする
        if (viewerCount > 1) {
            if (landscape) {
                cols = 2;
                rows = 1;

                // HOROS-20240407特例: 3枚の場合は2x2グリッドに強制して、
                // 後続のStrechWindowsロジックで2+1(縦)を実現する
                if (viewerCount == 3) {
                    rows = 2;
                }
            } else {
                cols = 1;
                rows = 2;

                // 縦画面で3枚の場合は1x3ではなく2x2のほうがHorosらしい配置になる
                if (viewerCount == 3) {
                    cols = 2;
                }
            }
        }

        // ウィンドウ数が多い場合にグリッドを拡張
        if (viewerCount > (rows * cols)) {
            // HOROS-20240407準拠: 比率の計算 (1.5 または 2.0)
            // 9枚表示で3x3を優先するため、ratioValueを少し厳しめ(1.5以下)に調整
            float ratioValue = landscape ? landscapeRatio : portraitRatio;
            if (viewerCount == 9) {
                ratioValue = 1.4f; // 9枚の場合は3x3(ratio=1.0)になりやすくする
            }

            while (viewerCount > (rows * cols)) {
                float actualRatio = (float) cols / (float) rows;
                if (actualRatio > ratioValue) {
                    rows++;
                } else {
                    cols++;
                }
            }

            // HOROS-20240407準拠: 補正処理 (AppController.m 5198-5202行目)
            while (rows * cols > viewerCount) {
                if (rows * (cols - 1) >= viewerCount) {
                    cols--;
                } else if ((rows - 1) * cols >= viewerCount) {
                    rows--;
                } else {
                    break;
                }
            }
        }

        // グリッド配置
        int cellWidth = availWidth / cols;
        int cellHeight = availHeight / rows;

        for (int i = 0; i < viewerCount; i++) {
            ViewerController v = viewers.get(i);
            int row = i / cols;
            int col = i % cols;

            int x = availX + col * cellWidth;
            int y = availY + row * cellHeight;
            int w = cellWidth;
            int h = cellHeight;

            // HOROS-20240407準拠: StrechWindowsロジック (AppController.m 4582行目相当)
            // 下にウィンドウがない場合、垂直方向に引き伸ばして隙間を埋める
            // これにより、3枚表示(2x2グリッド)などで1枚が縦長になる挙動を再現
            if (i + cols >= viewerCount) {
                h = availHeight - (y - availY);
            }

            v.setBounds(x, y, w, h);

            // HOROS-20240407準拠: ウィンドウサイズ確定後に画像をフィットさせる
            // Swingのレイアウト更新を待つためにinvokeLaterを使用
            final ViewerController finalV = v;
            SwingUtilities.invokeLater(() -> {
                if (finalV.imageView != null) {
                    finalV.imageView.scaleToFit();
                    finalV.imageView.repaint();
                }
            });
            v.toFront();
        }

        // HOROS-20240407準拠: 全てのビューワーのサムネイルハイライトを更新
        refreshAllThumbnails();
    }

    /**
     * 全てのオープンされているビューワーのサムネイル表示を更新
     */
    public static void refreshAllThumbnails() {
        // HOROS-20240407準拠: 全てのビューワーを更新すると重いため、最前面のビューワーのみ更新する
        // サムネイルリストパネルは共有されているため、これで十分
        ViewerController front = frontMostDisplayed2DViewer();
        if (front != null) {
            front.buildMatrixPreview();
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
    public void loadSeries(com.oculus.jj.model.DicomSeries series) {
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
        java.util.Set<com.oculus.jj.model.DicomImage> imageSet = series.getImages();

        if (imageSet == null || imageSet.isEmpty()) {
            return;
        }

        // インスタンス番号でソート
        List<com.oculus.jj.model.DicomImage> loadList = new ArrayList<>(imageSet);
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
        List<com.oculus.jj.model.DicomPix> viewerPix = new ArrayList<>();
        List<Object> correspondingObjects = new ArrayList<>();

        for (com.oculus.jj.model.DicomImage img : loadList) {
            // HOROS-20240407準拠: 各フレーム/画像に対して独立したDicomPixインスタンスを作成する
            // (ROIリストなどを独立させるため、DicomImageでのキャッシュは最小限に留めるか、
            // フレームごとに独立させる必要がある)
            com.oculus.jj.model.DicomPix dcmPix = new com.oculus.jj.model.DicomPix(
                    img,
                    img.getFrameID() != null ? img.getFrameID() : 0,
                    loadList.size());

            viewerPix.add(dcmPix);
            correspondingObjects.add(img);
        }

        // 多次元リストにラップして反映
        List<List<Object>> pixList2D = new ArrayList<>();
        pixList2D.add(new ArrayList<>(viewerPix));
        List<List<Object>> fileList2D = new ArrayList<>();
        fileList2D.add(new ArrayList<>(correspondingObjects));

        changeImageData(pixList2D, fileList2D, null, false);

        // HOROS-20240407準拠: ImageViewにフォーカスを設定
        // (ViewDidBecomeFirstResponderNotification相当)
        if (imageView != null) {
            imageView.becomeFirstResponder();
        }

        // HOROS-20240407準拠: 画像の非同期読み込みを開始
        startLoadImageThread();

        // 描画を強制更新とFITの実行
        // HOROS-20240407準拠: シリーズ切り替え時も確実にフィットさせる
        SwingUtilities.invokeLater(() -> {
            if (imageView != null) {
                imageView.scaleToFit();
                imageView.revalidate();
                imageView.repaint();
            }
        });

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

    public void setSeriesListVisible(boolean visible) {
        java.util.prefs.Preferences.userNodeForPackage(ThumbnailsListPanel.class)
                .putBoolean("SeriesListVisible", visible);
        ThumbnailsListPanel panel = ThumbnailsListPanel.getInstanceForScreenIndex(0);
        if (panel != null) {
            panel.setThumbnailsView(visible ? previewMatrixScrollView : null, this);
            if (visible) panel.setVisible(true);
        }
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
        com.oculus.jj.model.DicomStudy study = currentSeries.getStudy();
        if (study == null)
            return;

        // DBから全シリーズ取得
        List<com.oculus.jj.model.DicomSeries> seriesList = new ArrayList<>(study.getSeries());
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
        int totalImages = 0;
        if (!pixList.isEmpty() && pixList.get(0) != null) {
            totalImages = pixList.get(0).size();
        }

        int currentImageViewIndex = imageView.getCurImage();

        if (totalImages <= 1) {
            // HOROS-20240407準拠: 1枚の時も表示はするが、無効化する
            imageSlider.setVisible(true);
            imageSlider.setEnabled(false);
            imageSlider.setMinimum(0);
            imageSlider.setMaximum(0);
            imageSlider.setValue(0);
            currentImageIndex = 0;
        } else {
            imageSlider.setVisible(true);
            imageSlider.setEnabled(true);
            // スライダーの範囲を更新 (0 to totalImages - 1)
            imageSlider.setMinimum(0);
            imageSlider.setMaximum(totalImages - 1);

            // 現在のインデックスをセット
            if (currentImageViewIndex != imageSlider.getValue()) {
                imageSlider.setValue(currentImageViewIndex);
            }
            // 内部インデックスも確実に同期
            this.currentImageIndex = currentImageViewIndex;
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
        if (isBuildingMatrix) {
            return;
        }
        isBuildingMatrix = true;

        // 現在のシリーズを保持
        final com.oculus.jj.model.DicomSeries referenceSeries;
        if (currentSeries == null) {
            if (!fileList.isEmpty() && !fileList.get(0).isEmpty()) {
                referenceSeries = fileList.get(0).get(0).getSeries();
            } else {
                referenceSeries = null;
            }
        } else {
            referenceSeries = currentSeries;
        }

        if (referenceSeries == null) {
            isBuildingMatrix = false;
            return;
        }

        backgroundLoader.submit(() -> {
            try {
                com.oculus.jj.model.DicomDatabase db = com.oculus.jj.model.DicomDatabase.defaultDatabase();
                java.util.Set<String> patientUIDs = new java.util.HashSet<>();
                synchronized (fileList) {
                    for (java.util.List<com.oculus.jj.model.DicomImage> list : fileList) {
                        if (list != null && !list.isEmpty()) {
                            com.oculus.jj.model.DicomImage firstImg = list.get(0);
                            if (firstImg != null && firstImg.getSeries() != null
                                    && firstImg.getSeries().getStudy() != null) {
                                String uid = firstImg.getSeries().getStudy().getPatientUID();
                                if (uid != null)
                                    patientUIDs.add(uid);
                            }
                        }
                    }
                }
                if (referenceSeries.getStudy() != null) {
                    String uid = referenceSeries.getStudy().getPatientUID();
                    if (uid != null)
                        patientUIDs.add(uid);
                }

                java.util.Set<com.oculus.jj.model.DicomStudy> studySet = new java.util.LinkedHashSet<>();
                for (String uid : patientUIDs) {
                    studySet.addAll(db.getStudiesByPatientUID(uid));
                }

                java.util.List<com.oculus.jj.model.DicomStudy> allStudiesList = new java.util.ArrayList<>(
                        studySet);
                allStudiesList.sort((a, b) -> {
                    if (a.getDate() == null)
                        return 1;
                    if (b.getDate() == null)
                        return -1;
                    return b.getDate().compareTo(a.getDate());
                });

                for (com.oculus.jj.model.DicomStudy study : allStudiesList) {
                    db.loadSeriesForStudyIfNeeded(study);
                }

                SwingUtilities.invokeLater(() -> {
                    try {
                        updateMatrixUI(allStudiesList, referenceSeries);
                    } finally {
                        isBuildingMatrix = false;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                isBuildingMatrix = false;
            }
        });
    }

    private void updateMatrixUI(java.util.List<com.oculus.jj.model.DicomStudy> allStudies,
            com.oculus.jj.model.DicomSeries referenceSeries) {
        previewMatrix.removeAll();

        boolean multiplePatients = false;
        if (!allStudies.isEmpty()) {
            String firstPatientUID = allStudies.get(0).getPatientUID();
            for (com.oculus.jj.model.DicomStudy study : allStudies) {
                if (firstPatientUID != null && !firstPatientUID.equals(study.getPatientUID())) {
                    multiplePatients = true;
                    break;
                }
            }
        }

        final int MAX_DISPLAY_SERIES = 250;
        int displayedSeriesCount = 0;
        boolean limitationReached = false;
        ThumbnailButton targetButton = null;
        com.oculus.jj.model.DicomDatabase db = com.oculus.jj.model.DicomDatabase.defaultDatabase();

        for (com.oculus.jj.model.DicomStudy study : allStudies) {
            if (displayedSeriesCount >= MAX_DISPLAY_SERIES) {
                limitationReached = true;
                break;
            }

            int seriesCount = (study.getSeries() != null) ? study.getSeries().size() : 0;
            ThumbnailButton studyButton = new ThumbnailButton(this);
            studyButton.setRepresentedObject(study);

            StringBuilder studyInfo = new StringBuilder();
            studyInfo.append(allStudies.indexOf(study) + 1).append("\n");
            if (multiplePatients) {
                studyInfo.append(study.getName() != null ? study.getName() : LanguageManager.get("viewer.anonymous", "Anonymous")).append("\n");
                studyInfo.append(study.getStudyName() != null ? study.getStudyName() : LanguageManager.get("viewer.nodescription", "No Description")).append("\n");
            } else {
                studyInfo.append(study.getStudyName() != null ? study.getStudyName() : LanguageManager.get("viewer.nodescription", "No Description")).append("\n");
            }

            if (study.getDate() != null) {
                studyInfo.append(study.getDate().toLocalDate().toString()).append(" ")
                        .append(study.getDate().toLocalTime().toString()).append("\n");
            } else {
                studyInfo.append(LanguageManager.get("viewer.nodate", "No Date") + "\n");
            }

            studyInfo.append(study.getModality() != null ? study.getModality() : "??")
                    .append(": ").append(seriesCount).append(" ").append(LanguageManager.get("viewer.series", "series")).append("\n");

            if (collapsedStudies.contains(study)) {
                studyInfo.append(LanguageManager.get("viewer.showseries", "Show Series"));
            } else {
                studyInfo.append(LanguageManager.get("viewer.hideseries", "Hide Series"));
            }

            studyButton.setText(studyInfo.toString());

            if (study.getStudyInstanceUID().equals(referenceSeries.getStudy().getStudyInstanceUID())) {
                studyButton.setBackground(new Color(230, 230, 230));
            } else {
                studyButton.setBackground(ThumbnailButton.DIFFERENT_STUDY_COLOR);
            }

            previewMatrix.add(studyButton);

            if (!collapsedStudies.contains(study)) {
                java.util.Set<com.oculus.jj.model.DicomSeries> seriesSet = study.getSeries();
                if (seriesSet != null) {
                    java.util.List<com.oculus.jj.model.DicomSeries> sortedSeries = new java.util.ArrayList<>(
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

                    for (com.oculus.jj.model.DicomSeries series : sortedSeries) {
                        ThumbnailButton button = new ThumbnailButton(this);
                        button.setRepresentedObject(series);

                        byte[] thumbnailData = series.getThumbnail();
                        if (thumbnailData != null && thumbnailData.length > 0) {
                            try {
                                button.setIcon(new ImageIcon(thumbnailData));
                            } catch (Exception e) {
                            }
                        } else {
                            int imageCount = series.getNumberOfImages() != null ? series.getNumberOfImages() : 0;
                            if (imageCount > 0) {
                                final ThumbnailButton btn = button;
                                final com.oculus.jj.model.DicomSeries finalSeries = series;
                                thumbnailLoader.submit(() -> {
                                    try {
                                        db.loadImagesForSeriesIfNeeded(finalSeries);
                                        java.util.Set<com.oculus.jj.model.DicomImage> images = finalSeries
                                                .getImages();
                                        if (images != null && !images.isEmpty()) {
                                            com.oculus.jj.model.DicomPix pix = new com.oculus.jj.model.DicomPix(
                                                    images.iterator().next());
                                            java.awt.image.BufferedImage thumb = pix.generateThumbnail(100, 120);
                                            if (thumb != null) {
                                                SwingUtilities.invokeLater(() -> {
                                                    btn.setIcon(new ImageIcon(thumb));
                                                    btn.repaint();
                                                });
                                            }
                                        }
                                    } catch (Exception e) {
                                    }
                                });
                            }
                        }

                        StringBuilder seriesInfo = new StringBuilder();
                        seriesInfo.append((series.getName() != null && !series.getName().isEmpty()) ? series.getName()
                                : LanguageManager.get("viewer.unnamed", "Unnamed")).append("\n");
                        if (series.getDate() != null) {
                            seriesInfo.append(series.getDate().toLocalDate().toString());
                        } else {
                            seriesInfo.append(LanguageManager.get("viewer.nodate", "No Date"));
                        }
                        seriesInfo.append("\n");
                        seriesInfo.append(series.getNumberOfImages() != null ? series.getNumberOfImages() : 0)
                                .append(" ").append(LanguageManager.get("viewer.images", "Images"));

                        button.setText(seriesInfo.toString());

                        if (series.getStudy().getStudyInstanceUID()
                                .equals(referenceSeries.getStudy().getStudyInstanceUID())) {
                            button.setBackground(new Color(230, 230, 230));
                        } else {
                            button.setBackground(ThumbnailButton.DIFFERENT_STUDY_COLOR);
                        }

                        if (series.getSeriesInstanceUID().equals(referenceSeries.getSeriesInstanceUID())) {
                            button.setBackground(ThumbnailButton.SELECTED_ITEM_COLOR);
                            targetButton = button;
                        } else {
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
                        displayedSeriesCount++;
                        if (displayedSeriesCount >= MAX_DISPLAY_SERIES) {
                            limitationReached = true;
                            break;
                        }
                    }
                    if (limitationReached)
                        break;
                }
            }
        }

        if (limitationReached) {
            JLabel limitLabel = new JLabel(LanguageManager.get("viewer.limited", "... and more (Limited for performance)"));
            limitLabel.setForeground(Color.RED);
            limitLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            previewMatrix.add(limitLabel);
        }

        previewMatrix.revalidate();
        previewMatrix.repaint();

        if (targetButton != null) {
            final ThumbnailButton finalButton = targetButton;
            SwingUtilities.invokeLater(() -> {
                Rectangle rect = new Rectangle(0, 0, finalButton.getWidth(), finalButton.getHeight());
                finalButton.scrollRectToVisible(rect);
            });
        }
    }

    /**
     * メニューバーの内容を複製する
     * SwingのJMenuBar/JMenuItemは1つの場所にしか所属できないため、新しいインスタンスを作成する必要がある
     */
    public static javax.swing.JMenuBar replicateMenuBar(javax.swing.JMenuBar original) {
        javax.swing.JMenuBar copy = new javax.swing.JMenuBar();
        for (int i = 0; i < original.getMenuCount(); i++) {
            javax.swing.JMenu menu = original.getMenu(i);
            if (menu != null) {
                copy.add(replicateMenu(menu));
            }
        }
        return copy;
    }

    private static javax.swing.JMenu replicateMenu(javax.swing.JMenu original) {
        javax.swing.JMenu copy = new javax.swing.JMenu(original.getText());
        copy.setForeground(Color.BLACK);
        for (int i = 0; i < original.getItemCount(); i++) {
            java.awt.Component item = original.getMenuComponent(i);
            if (item instanceof javax.swing.JSeparator) {
                copy.addSeparator();
            } else if (item instanceof javax.swing.JMenuItem) {
                javax.swing.JMenuItem originalItem = (javax.swing.JMenuItem) item;
                if (originalItem instanceof javax.swing.JMenu) {
                    copy.add(replicateMenu((javax.swing.JMenu) originalItem));
                } else {
                    javax.swing.JMenuItem itemCopy = new javax.swing.JMenuItem(originalItem.getText());
                    itemCopy.setForeground(UIStyle.TEXT_COLOR);
                    itemCopy.setAccelerator(originalItem.getAccelerator());
                    // 元のリスナーをコピー
                    for (java.awt.event.ActionListener al : originalItem.getActionListeners()) {
                        itemCopy.addActionListener(al);
                    }
                    copy.add(itemCopy);
                }
            }
        }
        return copy;
    }

    /** Tile all 3D viewer windows in a grid layout */
    public static void tile3DWindows() {
        java.util.List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        int count = viewers.size();
        if (count == 0)
            return;
        // Use square-ish grid
        int cols = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / cols);
        java.awt.Dimension cellSize = new java.awt.Dimension(800, 600);
        for (int i = 0; i < count; i++) {
            ViewerController vc = viewers.get(i);
            int row = i / cols;
            int col = i % cols;
            java.awt.Rectangle bounds = new java.awt.Rectangle(col * cellSize.width, row * cellSize.height,
                    cellSize.width, cellSize.height);
            vc.setBounds(bounds);
        }
    }
}
