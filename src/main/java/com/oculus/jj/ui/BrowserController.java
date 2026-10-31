package com.oculus.jj.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import com.oculus.jj.model.DicomSeries;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.JPopupMenu;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.JButton;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;

import com.oculus.jj.model.DicomDatabase;
import com.oculus.jj.model.DicomPix;
import com.oculus.jj.threads.ThreadsManager;
import com.oculus.jj.utils.LanguageManager;

/**
 * BrowserController - ブラウザウィンドウコントローラー
 * 
 * HOROS-20240407のBrowserControllerをJava Swingに移植
 */
public class BrowserController extends JFrame {

    // HOROS-20240407準拠: static BrowserController *browserWindow = nil; (170行目)
    private static BrowserController browserWindow = null;

    /**
     * HOROS-20240407準拠: BrowserController.h 327行目 - + (BrowserController*)
     * currentBrowser;
     * BrowserController.m 521行目 - + (BrowserController*) currentBrowser { return
     * browserWindow; }
     */
    public static BrowserController currentBrowser() {
        return browserWindow;
    }

    // HOROS-20240407準拠: DicomDatabase* _database; (80行目)
    private DicomDatabase database;

    // HOROS-20240407準拠: BrowserMatrix *oMatrix; (BrowserController.h)
    private BrowserMatrix oMatrix;

    // HOROS-20240407準拠: MyOutlineView *databaseOutline; (BrowserController.h)
    private DatabaseOutlineView databaseOutline;

    // HOROS-20240407準拠: BrowserActivityHelper *_activityHelper;
    // (BrowserController+Activity.h)
    private BrowserActivityHelper activityHelper;

    // ========== UI関連フィールド（HOROS-20240407準拠） ==========
    // HOROS-20240407準拠: BrowserController.h から主要なフィールドを復元

    // HOROS-20240407準拠: NSMutableArray *previewPix, *previewPixThumbnails; (93行目)
    private List<com.oculus.jj.model.DicomPix> previewPix;
    private List<javax.swing.ImageIcon> previewPixThumbnails;

    // HOROS-20240407準拠: long loadPreviewIndex, previousNoOfFiles; (106行目)
    private long loadPreviewIndex = 0;
    private long previousNoOfFiles = 0;

    // HOROS-20240407準拠: NSManagedObject *previousItem; (107行目)
    private Object previousItem;

    // HOROS-20240407準拠: BOOL setDCMDone, dontUpdatePreviewPane; (114行目)
    private boolean setDCMDone = false;
    private boolean dontUpdatePreviewPane = false;

    // HOROS-20240407準拠: NSArray *matrixViewArray; (125行目)
    private List<Object> matrixViewArray;

    // HOROS-20240407準拠: IBOutlet NSSlider *animationSlider; (142行目)
    private javax.swing.JSlider animationSlider;

    // HOROS-20240407準拠: IBOutlet NSButton *animationCheck; (143行目)
    private javax.swing.JCheckBox animationCheck;

    // HOROS-20240407準拠: アニメーションタイマー
    private javax.swing.Timer animationTimer;

    // P-G: プレビューパネルの 3D/4D モードボタン（HOROS MainMenu.xib 3193-3202行目相当）
    private JRadioButton preview3DButton;
    private JRadioButton preview4DButton;

    // HOROS-20240407準拠: IBOutlet PreviewView *imageView; (146行目)
    private PreviewView imageView;

    // HOROS-20240407準拠: IBOutlet NSTableView *albumTable; (133行目)
    private AlbumTableView albumTable;
    private volatile int allStudiesAlbumCount;

    // HOROS-20240407準拠: IBOutlet NSTableView *_sourcesTableView; (138行目)
    private javax.swing.JTable sourcesTableView;

    // HOROS-20240407準拠: IBOutlet NSTableView *comparativeTable; (270行目)
    private javax.swing.JTable comparativeTable;

    // HOROS-20240407準拠: NSMenu *columnsMenu; (BrowserController.h 131行目)
    private javax.swing.JPopupMenu columnsMenu;

    // HOROS-20240407準拠: IBOutlet NSSearchField *searchField; (180行目)
    private javax.swing.JTextField searchField;

    // HOROS-20240407準拠: IBOutlet NSTextField *databaseDescription;
    // (BrowserController.h 129行目)
    private javax.swing.JTextField databaseDescription;

    // HOROS-20240407準拠: IBOutlet NSSplitView *splitAlbums; (BrowserController.h
    // 111行目)
    private JSplitPane splitAlbums;

    // HOROS-20240407準拠: splitSourcesActivity（SourcesとActivityを分割するスプリッター）
    private JSplitPane splitSourcesActivity;

    // HOROS-20240407準拠: IBOutlet NSSplitView *splitComparative;
    // (BrowserController.h 111行目)
    private JSplitPane splitComparative;

    // HOROS-20240407準拠: bottomPreviewSplit（サムネイルとプレビューを分割するスプリッター）
    private JSplitPane bottomPreviewSplit;

    // サムネイルスクロールビューの垂直スクロールバー（ディバイダーの右側に固定配置するため）
    private javax.swing.JScrollBar thumbnailsVerticalScrollBar;

    // 手動スクロールバーの調整中フラグ（無限ループを防ぐため）
    private boolean isAdjustingThumbnailsScrollBar = false;

    // サムネイルスクロールビュー（previewMatrixScrollViewFrameDidChangeで直接参照するため）
    private javax.swing.JScrollPane thumbnailsScrollView;

    // HOROS-20240407準拠: BOOL loadingIsOver = NO; (BrowserController.m 174行目)
    private boolean loadingIsOver = false;

    // HOROS-20240407準拠: BrowserToolbar フィールド（initializeUIComponents()外側で宣言）
    private BrowserToolbar browserToolbar = null;

    // HOROS-20240407準拠: ツールバー用 JScrollPane
    private JScrollPane toolbarScroll = null;

    // HOROS-20240407準拠: BOOL DatabaseIsEdited; (201行目)
    private boolean DatabaseIsEdited = false;

    // --- Annotation Undo/Redo management
    private com.oculus.jj.util.AnnotationUndoManager annotationUndoManager;

    // --- Annotation font preference keys
    private static final String PREF_ANNOT_FONT_SIZE = "annotationFontSize";
    private static final String PREF_ANNOT_FONT_BOLD = "annotationFontBold";
    private static final int DEFAULT_ANNOT_FONT_SIZE = 12;

    /** Initialize annotation undo manager */
    private void initAnnotationUndo() {
        if (annotationUndoManager == null) {
            DCMView active = getActiveDCMViewForEdit();
            annotationUndoManager = new com.oculus.jj.util.AnnotationUndoManager(active);
        }
    }

    // --- ビュータイプ追跡（HOROS-20240407準拠: macOS上部メニューバーのグレーアウトロジック） ---
    private MainMenuBar.ViewType currentViewType = MainMenuBar.ViewType.STANDARD_2D;
    private javax.swing.JMenu viewer3DMenu; // 3D Viewerメニューへの参照（updateForViewType用）
    private javax.swing.JMenu convolutionMenu; // Convolution Filtersメニューへの参照（rebuild用）

    // --- HOROS-20240407 Subtraction state ---
    // subCtrlOnOff / subSharpen / subCtrlNewMask / subCtrlOffset / subCtrlSliders / subSumSlider
    // 参照: HOROS ViewerController.m:9827 subCtrlOnOff:, 9875 subCtrlNewMask:,
    //       9910 subCtrlOffset:, 10136 subCtrlSliders:, 10173 subSumSlider:, 10211 subSharpen:
    private javax.swing.JCheckBoxMenuItem subCtrlOnOffItem; // Subtract (toggle)
    private javax.swing.JCheckBoxMenuItem subSharpenItem; // Sharpen (toggle)
    private int subCtrlMaskIndex = -1; // HOROS: subCtrlMaskID (0-based)
    private int subCtrlOffsetX = 0; // HOROS: subCtrlOffset.x
    private int subCtrlOffsetY = 0; // HOROS: subCtrlOffset.y
    private int subCtrlSum = 1; // HOROS: subCtrlSum (1..10)
    private boolean subCtrlSharpenOn = false;

    /**
     * HOROS-20240407準拠: ビュータイプに基づいてメニュー項目をグレーアウト/非表示にする
     */
    public void updateForViewType(MainMenuBar.ViewType viewType) {
        this.currentViewType = viewType;

        switch (viewType) {
            case STANDARD_2D:
                // 標準2Dビュー: 全メニュー有効
                if (viewer3DMenu != null) {
                    viewer3DMenu.setEnabled(true);
                }
                break;

            case MPR_ORTHOGONAL:
            case MPR_CURVED:
                // MPRモード: 3D Viewerメニューは表示但し一部無効化
                if (viewer3DMenu != null) {
                    viewer3DMenu.setEnabled(true);
                }
                break;

            case VIEWER_3D:
                // 3Dビューワー: ブラウザウィンドウの3D Viewerメニューをグレーアウト（重複防止）
                if (viewer3DMenu != null) {
                    viewer3DMenu.setEnabled(false);
                    // HOROS-20240407準拠: 各メニュー項目を個別に無効化（JMenu.setEnabledだけでは不十分な場合の対策）
                    for (int i = 0; i < viewer3DMenu.getMenuComponentCount(); i++) {
                        java.awt.Component comp = viewer3DMenu.getMenuComponent(i);
                        if (comp instanceof javax.swing.JMenuItem menuItem) {
                            menuItem.setEnabled(false);
                        }
                    }
                }
                break;
        }

        // MainMenuBarにも通知（MainMenuBarを使用している場合）
        if (getJMenuBar() instanceof MainMenuBar mainMenuBar) {
            mainMenuBar.updateForViewType(viewType);
        }
        if (getJMenuBar() != null) {
            validateMenuBarForWindow(getJMenuBar(), this);
        }
    }

    /**
     * 現在のビュータイプを取得する
     */
    public MainMenuBar.ViewType getCurrentViewType() {
        return currentViewType;
    }

    // HOROS-20240407準拠: NSThread *matrixLoadIconsThread; (262行目)
    private Thread matrixLoadIconsThread;

    // HOROS-20240407準拠: NSTimeInterval _timeIntervalOfLastLoadIconsDisplayIcons;
    // (BrowserController.h 261行目)
    private long timeIntervalOfLastLoadIconsDisplayIcons = 0;

    // HOROS-20240407準拠: BOOL withReset; (プレビューリセットフラグ)
    private boolean withReset = false;

    // HOROS-20240407準拠: NSString *comparativePatientUID; (267行目)
    private String comparativePatientUID;

    // HOROS-20240407準拠: NSArray *comparativeStudies; (269行目)
    private List<Object> comparativeStudies;

    // HOROS-20240407準拠: BOOL _computingNumberOfStudiesForAlbums; (253行目)
    private boolean _computingNumberOfStudiesForAlbums = false;

    // 無限ループ防止フラグ
    private boolean isRefreshingOutline = false;
    private boolean isRefreshingAlbums = false;
    private boolean isUpdatingAlbumTable = false;
    private boolean refreshAlbumsPending = false;

    // ディバイダー位置の復元が完了したかどうか（一度だけ実行するため）
    private boolean dividerLocationsRestored = false;

    // UI初期化が完了したかどうか（previewMatrixScrollViewFrameDidChangeを呼び出す前に確認）
    private boolean uiInitialized = false;

    // ディバイダー位置の調整中かどうか（無限ループ防止）
    private boolean isAdjustingDivider = false;

    // HOROS-20240407準拠: dbObjectSelection enum (BrowserController.m)
    // oAny, oMiddle, oFirstForFirst は画像選択方法を指定する定数
    private static final int oAny = 0;
    private static final int oMiddle = 1;
    private static final int oFirstForFirst = 2;

    // HOROS-20240407準拠: NSImage *notFoundImage; (230行目)
    private javax.swing.ImageIcon notFoundImage;

    private javax.swing.ImageIcon createNotFoundImage() {
        // HOROS-20240407準拠: 70x70のグレーの矩形をデフォルトとする
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(70, 70,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = img.createGraphics();
        g2d.setColor(java.awt.Color.DARK_GRAY);
        g2d.fillRect(0, 0, 70, 70);
        g2d.setColor(java.awt.Color.LIGHT_GRAY);
        g2d.drawRect(5, 5, 60, 60);
        g2d.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10));
        g2d.drawString("NOT FOUND", 5, 40);
        g2d.dispose();
        return new javax.swing.ImageIcon(img);
    }

    /**
     * コンストラクタ
     * HOROS-20240407準拠: - (id)init (BrowserController.m)
     */
    public BrowserController() {
        super("OculusJJ");

        // HOROS-20240407準拠: browserWindow = self; (BrowserController.m)
        browserWindow = this;

        // HOROS-20240407準拠: _database = [DicomDatabase activeLocalDatabase];
        // (BrowserController.m)
        this.database = DicomDatabase.activeLocalDatabase();

        // HOROS-20240407準拠: フィールドの初期化
        this.previewPix = Collections.synchronizedList(new ArrayList<>());
        this.previewPixThumbnails = Collections.synchronizedList(new ArrayList<>());
        this.matrixViewArray = Collections.synchronizedList(new ArrayList<>());
        this.comparativeStudies = Collections.synchronizedList(new ArrayList<>());
        this.notFoundImage = createNotFoundImage();

        // UI初期化
        initializeUIComponents();

        // HOROS-20240407準拠: データベース通知の監視（OsirixDisplayDatabaseChangedNotification相当）
        if (this.database != null) {
            this.database.addDatabaseChangedListener(() -> {
                refreshDatabase(this);
            });
        }
        SwingUtilities.invokeLater(() -> QueryController.resumeSavedAutoQuery(this));
    }

    /**
     * UIコンポーネントの初期化
     * HOROS-20240407準拠: - (void)awakeFromNib (BrowserController.m 14164行目)
     * 
     * HOROS-20240407のコード構造（BrowserController.h/mから確認）:
     * - splitViewHorz (水平): 左サイドバー | 右メインパネル
     * - 左: splitAlbums (垂直): アルバムテーブル | データベースアウトライン（Sources）
     * - 右: splitViewVert (垂直): matrixView | imageView
     * - matrixView: BrowserMatrix（oMatrix）を含むNSView
     * - imageView: PreviewView（_bottomSplitを含む）
     * - _bottomSplit (水平): thumbnailsScrollView | 画像プレビュー
     * 
     * HOROS-20240407準拠: BrowserController.m 10536行目
     * splitViewVert subviews[0] = matrixView
     * splitViewVert subviews[1] = imageView
     * 
     * HOROS-20240407準拠: BrowserController.m 10540-10541行目
     * _bottomSplit subviews[0] = thumbnailsScrollView
     * _bottomSplit subviews[1] = 画像プレビュー
     */
    private void initializeUIComponents() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // HOROS-20240407準拠: BrowserController.m 14183-14188行目
        // ウィンドウ位置・サイズを復元
        restoreWindowFrame();

        // HOROS-20240407準拠: MainMenu.xib 4851行目 - scroller key="verticalScroller"
        // width="16"
        javax.swing.UIManager.put("ScrollBar.width", 16);
        javax.swing.UIManager.put("ScrollBar.minimumThumbSize", new java.awt.Dimension(16, 16));

        // HOROS-20240407準拠: MainMenu.xibからメニューバーを実装
        initializeMenuBar();
        // HOROS-20240407準拠: BrowserController.m toolbarDefaultItemIdentifiers
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        // HOROS-20240407準拠: JToolBar を横スクロール可能な JScrollPane で囲む（Swing で JToolBar が横に伸びるよう明示）
        browserToolbar = new BrowserToolbar();
        // HOROS-20240407準拠: NSPopUpButton selection changed → outlineViewRefresh (setTimeIntervalFrom:)
        browserToolbar.setOnTimeIntervalChangeListener(selectedIdx -> {
            timeIntervalType = selectedIdx;
            SwingUtilities.invokeLater(this::outlineViewRefresh);
        });
        toolbarScroll = new JScrollPane(browserToolbar);
        toolbarScroll.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        // Swing: setViewPortSetViewport(browserToolbar) を contentPane.add 後に遅延して呼出 (setViewPortSetViewport は初期化順序で問題になる場合がある)

        // ========== 左側サイドバー（垂直スプリッター） ==========
        // HOROS-20240407準拠: NSPopUpButton selection changed listener (setModalityFilter: equivalent)
        browserToolbar.setOnModalityFilterChangeListener(mod -> {
            outlineViewRefresh();
        });
        // HOROS-20240407準拠: splitAlbums - Albums | Sources | Activity
        // HOROS-20240407準拠: BrowserController.h 111行目 IBOutlet NSSplitView
        // *splitAlbums;
        // HOROS-20240407準拠: MainMenu.xib 4078行目 -
        // splitAlbumsは垂直分割でAlbums、Sources、Activityを含む
        // Java SwingのJSplitPaneは2分割しかできないため、入れ子構造にする必要がある
        splitAlbums = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitAlbums.setName("splitAlbums"); // 検索用に名前を設定

        // Albums（上）
        // HOROS-20240407準拠: BrowserController.h 133行目 IBOutlet NSTableView *albumTable;
        // HOROS-20240407準拠: MainMenu.xib 4082行目 - box id="11696" (Albums)
        // HOROS-20240407準拠: MainMenu.xib 4104行目 - tableHeaderCell title="Albums"
        // HOROS-20240407準拠: AlbumTableViewを使用
        albumTable = new AlbumTableView(this);
        // HOROS-20240407準拠: 2列モデル（アルバム名、スタディ数）を設定
        String[] albumColumns = { LanguageManager.get("browser.albumName"), LanguageManager.get("browser.studyCount") };
        javax.swing.table.DefaultTableModel albumModel = new javax.swing.table.DefaultTableModel(albumColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        albumTable.setModel(albumModel);
        // HOROS-20240407準拠: 選択変更リスナーはAlbumTableView内で処理されるため、ここでは追加しない
        // ただし、isUpdatingAlbumTableフラグを考慮する必要があるため、AlbumTableViewを修正
        // HOROS-20240407準拠: MainMenu.xib 4104行目 - tableHeaderCell title="Albums"
        albumTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane albumScroll = new JScrollPane(albumTable);
        // HOROS-20240407準拠: MainMenu.xib 4082行目 - box title="Albums"
        // titlePosition="noTitle"
        // Java SwingではTitledBorderで見出しを表示
        albumScroll.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createEmptyBorder(),
                "Albums",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP));
        splitAlbums.setTopComponent(albumScroll);

        // SourcesとActivityを垂直に分割（下部）
        // HOROS-20240407準拠: MainMenu.xib 4196行目 - box id="11697" (Sources)
        // HOROS-20240407準拠: MainMenu.xib 4225行目 - tableView id="14153" (Activity)
        splitSourcesActivity = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitSourcesActivity.setName("splitSourcesActivity"); // 検索用に名前を設定

        // Sources（中）- データベースリスト
        // HOROS-20240407準拠: BrowserController.h 138行目 IBOutlet NSTableView
        // *_sourcesTableView;
        // HOROS-20240407準拠: MainMenu.xib 4156行目 - tableView id="11693"
        // HOROS-20240407準拠: MainMenu.xib 4163行目 - tableColumn identifier="Source"
        // title="Sources"
        // HOROS-20240407準拠: MainMenu.xib 4176行目 - binding
        // keyPath="arrangedObjects.description"
        String[] sourcesColumns = { "Sources" };
        javax.swing.table.DefaultTableModel sourcesModel = new javax.swing.table.DefaultTableModel(sourcesColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        // HOROS-20240407準拠: 初期データとして"Documents DB"を追加
        sourcesModel.addRow(new Object[] { "Documents DB" });
        sourcesTableView = new javax.swing.JTable(sourcesModel);
        sourcesTableView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // HOROS-20240407準拠: MainMenu.xib 4156行目 - columnReordering="NO"
        // columnResizing="NO"
        sourcesTableView.getTableHeader().setReorderingAllowed(false);
        sourcesTableView.setColumnSelectionAllowed(false);
        sourcesTableView.setRowSelectionAllowed(true);
        // HOROS-20240407準拠: MainMenu.xib 4163行目 - 列識別子を設定
        sourcesTableView.getColumnModel().getColumn(0).setIdentifier("Source");
        // HOROS-20240407準拠: 選択変更リスナー
        sourcesTableView.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                sourceSelectionChanged();
            }
        });
        JScrollPane sourcesScroll = new JScrollPane(sourcesTableView);
        // HOROS-20240407準拠: MainMenu.xib 4142行目 - box title=noTitle
        // titlePosition="noTitle"

        sourcesScroll.setBorder(null);

        splitSourcesActivity.setTopComponent(sourcesScroll);
        // Activity（下）
        // HOROS-20240407準拠: BrowserController+Activity.h
        // HOROS-20240407準拠: BrowserController+Activity.mm 64-72行目
        // HOROS-20240407準拠: MainMenu.xib 4225行目 - tableView id="14153"
        activityHelper = new BrowserActivityHelper(this);

        // HOROS-20240407準拠: BrowserController+Activity.mm 67-68行目
        // [_activityTableView setDelegate: _activityHelper];
        // [_activityTableView setDataSource: _activityHelper];
        this.activityTableView = new javax.swing.JTable(activityHelper);
        // HOROS-20240407準拠: BrowserController.m 338行目 - Regular mode: 38
        this.activityTableView.setRowHeight(38);
        // HOROS-20240407準拠: MainMenu.xib 4225行目 - headerView="14179" (Activityヘッダーあり)
        // HOROS-20240407準拠: MainMenu.xib 4233行目 - tableHeaderCell title="Activity"
        // Activityテーブルにはヘッダーがあり、"Activity"というタイトルが表示される
        this.activityTableView.getTableHeader().setReorderingAllowed(false);
        this.activityTableView.setShowGrid(false);
        this.activityTableView.setIntercellSpacing(new java.awt.Dimension(0, 0));

        // HOROS-20240407準拠: ThreadCellRendererを使用してThreadCellを表示
        this.activityTableView.setDefaultRenderer(Object.class,
                new com.oculus.jj.ui.ThreadCellRenderer(activityHelper));

        // HOROS-20240407準拠: Activityパネルにテーブルを追加
        // HOROS-20240407準拠: MainMenu.xib 4211行目 - box id="14167" title="Threads"
        // titlePosition="noTitle"
        // HOROS-20240407準拠: MainMenu.xib 4233行目 - tableHeaderCell title="Activity"
        // Activityテーブルのヘッダーに"Activity"が表示されるため、TitledBorderは不要
        JScrollPane activityScroll = new JScrollPane(this.activityTableView);
        activityScroll.setBorder(null);
        splitSourcesActivity.setBottomComponent(activityScroll);
        // HOROS-20240407準拠: MainMenu.xib 4143行目 - Sources box height="381"
        // Sourcesパネルの高さを3/4に設定
        splitSourcesActivity.setResizeWeight(0.75); // Sourcesが75%（3/4）

        // splitAlbumsの下部にSourcesとActivityを含むスプリッターを設定
        splitAlbums.setBottomComponent(splitSourcesActivity);
        // HOROS-20240407準拠: MainMenu.xib 4083行目 - Albums box height="198"
        // HOROS-20240407準拠: Albums: 198px, Sources+Activity: 489px, 合計: 687px
        // Albumsの割合: 198/687 ≈ 0.29
        splitAlbums.setResizeWeight(0.29); // Albumsが29%（HOROS-20240407準拠）

        // Activityの最小高さを設定（見えるようにするため）
        activityScroll.setMinimumSize(new java.awt.Dimension(0, 108));

        // HOROS-20240407準拠: awakeActivityを呼び出す（BrowserController.m 14432行目）
        // ただし、activityHelperは既に初期化済みなので、ここではactivityTableViewの設定のみ行う
        // 実際のawakeActivity相当の処理はBrowserActivityHelperのコンストラクタで行われる

        // ========== 右側メインパネル ==========
        // HOROS-20240407準拠: MainMenu.xib 4281行目 - splitComparative (id="14729")
        // 垂直スプリッター
        // HOROS-20240407準拠: BrowserController.h 111行目 IBOutlet NSSplitView
        // *splitComparative;
        splitComparative = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // 上部テーブルエリア（水平スプリッター）: databaseOutline | comparativeScrollView
        // HOROS-20240407準拠: MainMenu.xib 4284-4714行目
        JSplitPane topTablesSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        // 患者情報テーブル（左）- databaseOutline
        // HOROS-20240407準拠: BrowserController.h 130行目 IBOutlet MyOutlineView
        // *databaseOutline;
        // HOROS-20240407準拠: MainMenu.xib 4292行目 - outlineView id="966"
        databaseOutline = new DatabaseOutlineView(this);
        JScrollPane patientTableScroll = new JScrollPane(databaseOutline);
        topTablesSplit.setLeftComponent(patientTableScroll);

        // 履歴パネル（右）- comparativeTable
        // HOROS-20240407準拠: BrowserController.h 270行目 IBOutlet NSTableView
        // *comparativeTable;
        // HOROS-20240407準拠: MainMenu.xib 4666-4714行目 - comparativeScrollView id="14720"
        // width="205"
        // HOROS-20240407準拠: BrowserController.m 11369-11393行目 -
        // ComparativeCellを使用して2行表示
        String[] comparativeColumns = { "History" };
        javax.swing.table.DefaultTableModel comparativeModel = new javax.swing.table.DefaultTableModel(
                comparativeColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        comparativeTable = new javax.swing.JTable(comparativeModel);
        comparativeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        comparativeTable.getTableHeader().setReorderingAllowed(false);
        // HOROS-20240407準拠: 行の高さを設定（2行表示のため）
        // HOROS-20240407準拠: BrowserController.m 322-353行目 - setRowHeight:
        // 13/16/21/24/29/43
        // 通常モード（mode == 0）: gHorizontalHistoryの場合は16、そうでない場合は29
        // 罫線に重ならないように、行の高さを適切に設定（ユーザー要求：下が余っているので小さく）
        comparativeTable.setRowHeight(30); // 2行表示に適した高さ（下の余白を減らす）

        // HOROS-20240407準拠: カスタムセルレンダラーを設定（2行表示：スタディ名/モダリティ、日付/画像数）
        // HOROS-20240407準拠: BrowserController.m 11369-11393行目 -
        // ComparativeCellを使用して2行表示

        // HOROS-20240407準拠: ComparativeCell.mm 185-254行目 - カスタム描画を使用
        // HOROS-20240407準拠: 右寄せのテキストを先に描画し、その幅を取得して左側のテキストの幅を調整
        comparativeTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(
                    javax.swing.JTable table, Object value, boolean isSelected, boolean hasFocus,
                    int row, int column) {
                // HOROS-20240407準拠: ComparativeCell.mm 98-105行目 - カスタム描画を使用
                final javax.swing.JTable finalTable = table;
                final boolean finalRowSelected = isSelected || table.isRowSelected(row);

                // カスタムJPanelクラスを定義
                class ComparativeCellPanel extends javax.swing.JPanel {
                    private com.oculus.jj.model.DicomStudy study;
                    private boolean selected;
                    private java.awt.Font customFont;
                    private javax.swing.JTable tableRef;

                    public void setStudy(com.oculus.jj.model.DicomStudy s) {
                        this.study = s;
                        // HOROS-20240407準拠: BrowserController.m 11369-11401行目 - willDisplayCell
                        // HISTORYパネル（comparativeTable）のセルには画像を設定しない
                        // テキスト情報のみを設定（leftTextFirstLine, rightTextFirstLine, leftTextSecondLine,
                        // rightTextSecondLine）
                    }

                    public void setSelected(boolean sel) {
                        this.selected = sel;
                    }

                    public void setCustomFont(java.awt.Font f) {
                        this.customFont = f;
                    }

                    public void setTable(javax.swing.JTable t) {
                        this.tableRef = t;
                    }

                    @Override
                    protected void paintComponent(java.awt.Graphics g) {
                        super.paintComponent(g);

                        if (study == null || tableRef == null)
                            return;

                        java.awt.Graphics2D g2d = (java.awt.Graphics2D) g.create();
                        g2d.setFont(customFont != null ? customFont : getFont());

                        // HOROS-20240407準拠: ComparativeCell.mm 100-102行目 - 罫線を描画
                        g2d.setColor(new java.awt.Color(0.666f, 0.666f, 0.666f, 0.333f));
                        g2d.setStroke(new java.awt.BasicStroke(1.0f));
                        int lineY = getHeight() - 1;
                        g2d.drawLine(0, lineY, getWidth(), lineY);

                        // HOROS-20240407準拠: ComparativeCell.mm 56行目 - setImagePosition:NSImageLeft
                        // 注意: HOROS-20240407では、HISTORYパネル（comparativeTable）にサムネイル画像を表示しない
                        // BrowserController.m 11369-11401行目のwillDisplayCellでは、画像を設定していない
                        // そのため、サムネイル画像は描画しない（HOROS-20240407準拠）
                        int textStartX = 0;

                        // HOROS-20240407準拠: スタディ名、モダリティ、日付、画像数を取得
                        String studyName = study.getStudyName();
                        boolean isUnnamed = false;
                        if (studyName == null || studyName.isEmpty() || "unnamed".equalsIgnoreCase(studyName)) {
                            studyName = "Unnamed";
                            isUnnamed = true;
                        }
                        String modality = study.getModality();
                        if (modality == null || modality.isEmpty()) {
                            modality = "";
                        }
                        java.time.LocalDateTime date = study.getDate();
                        String dateStr = "";
                        if (date != null) {
                            try {
                                dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
                            } catch (Exception e) {
                                dateStr = date.toString();
                            }
                        }
                        Integer numberOfImages = study.getNumberOfImages();
                        String imageCountStr = "";
                        if (numberOfImages != null) {
                            int count = Math.abs(numberOfImages.intValue());
                            imageCountStr = count + (count == 1 ? " image" : " images");
                        }

                        // HOROS-20240407準拠: 色を設定
                        java.awt.Color fgColor = selected ? tableRef.getSelectionForeground()
                                : tableRef.getForeground();
                        java.awt.Color studyNameColor = fgColor;
                        if (isUnnamed && !selected) {
                            java.awt.Color originalColor = tableRef.getForeground();
                            java.awt.Color grayColor = new java.awt.Color(128, 128, 128);
                            studyNameColor = new java.awt.Color(
                                    (int) (originalColor.getRed() * 0.6 + grayColor.getRed() * 0.4),
                                    (int) (originalColor.getGreen() * 0.6 + grayColor.getGreen() * 0.4),
                                    (int) (originalColor.getBlue() * 0.6 + grayColor.getBlue() * 0.4));
                        }
                        java.awt.Color dateColor = selected ? fgColor : new java.awt.Color(102, 102, 102);

                        // HOROS-20240407準拠: ComparativeCell.mm 185-254行目
                        // 1行目：rightTextFirstLine（右寄せ、モダリティ）を先に描画
                        // 右端が切れないように、列幅を考慮して描画位置を計算
                        // テーブルの列幅を取得（列幅が設定されていない場合はgetWidth()を使用）
                        int cellWidth = getWidth();
                        if (tableRef != null && tableRef.getColumnModel().getColumnCount() > 0) {
                            int columnWidth = tableRef.getColumnModel().getColumn(0).getWidth();
                            if (columnWidth > 0) {
                                cellWidth = columnWidth;
                            }
                        }
                        // 垂直スクロールバーが表示されている場合、その幅を考慮して描画領域を調整
                        // ただし、セル自体の幅は列幅に合わせられているため、getWidth()を使用
                        // 実際の描画可能な幅は、セルの幅（列幅）を使用
                        // 右端に3ピクセルのマージンを設けて、スクロールバーに被さることを防ぐ
                        java.awt.Rectangle frame = new java.awt.Rectangle(textStartX, 0, cellWidth - textStartX,
                                getHeight());
                        final int spacer = 2; // HOROS-20240407準拠: ComparativeCell.mm 112行目
                        final int lineSpace = 12; // HOROS-20240407準拠: BrowserController.m 368行目 - comparativeLineSpace
                        final int rightPadding = 3; // 右端のパディング（スクロールバーに被さることを防ぐため）

                        // 1行目：右寄せのテキスト（モダリティ）を先に描画
                        if (modality != null && !modality.isEmpty()) {
                            java.awt.FontMetrics modalityFm = g2d.getFontMetrics();
                            int modalityWidth = modalityFm.stringWidth(modality);
                            g2d.setColor(fgColor);
                            // 右端が切れないように、列幅から直接計算（列幅は既にスクロールバーを考慮済み）
                            g2d.drawString(modality, cellWidth - modalityWidth - rightPadding,
                                    frame.y + 1 + modalityFm.getAscent());

                            // 左側のテキスト用のフレーム幅を調整
                            frame.width -= modalityWidth + spacer + rightPadding;
                        }

                        // 1行目：左寄せのテキスト（スタディ名）を描画
                        if (studyName != null && !studyName.isEmpty()) {
                            java.awt.FontMetrics nameFm = g2d.getFontMetrics();
                            g2d.setColor(studyNameColor);
                            String displayName = studyName;
                            int nameWidth = nameFm.stringWidth(displayName);
                            if (nameWidth > frame.width) {
                                // 省略表示
                                displayName = nameFm.stringWidth("...") > frame.width ? ""
                                        : nameFm.stringWidth(
                                                displayName.substring(0, Math.max(0, displayName.length() - 3))
                                                        + "...") <= frame.width
                                                                ? displayName.substring(0,
                                                                        Math.max(0, displayName.length() - 3)) + "..."
                                                                : displayName;
                            }
                            g2d.drawString(displayName, frame.x, frame.y + 1 + nameFm.getAscent());
                        }

                        // 2行目：右寄せのテキスト（画像数）を先に描画
                        frame = new java.awt.Rectangle(textStartX, 0, cellWidth - textStartX, getHeight());
                        if (imageCountStr != null && !imageCountStr.isEmpty()) {
                            java.awt.Font smallFont = g2d.getFont().deriveFont(g2d.getFont().getSize() * 0.85f);
                            java.awt.FontMetrics imageFm = g2d.getFontMetrics(smallFont);
                            g2d.setFont(smallFont);
                            int imageWidth = imageFm.stringWidth(imageCountStr);
                            g2d.setColor(dateColor);
                            // 右端が切れないように、列幅から直接計算（列幅は既にスクロールバーを考慮済み）
                            g2d.drawString(imageCountStr, cellWidth - imageWidth - rightPadding,
                                    frame.y + lineSpace + 1 + imageFm.getAscent());

                            // 左側のテキスト用のフレーム幅を調整
                            frame.width -= imageWidth + spacer + rightPadding;
                        }

                        // 2行目：左寄せのテキスト（日付）を描画
                        if (dateStr != null && !dateStr.isEmpty()) {
                            java.awt.Font smallFont = g2d.getFont().deriveFont(g2d.getFont().getSize() * 0.85f);
                            java.awt.FontMetrics dateFm = g2d.getFontMetrics(smallFont);
                            g2d.setFont(smallFont);
                            g2d.setColor(dateColor);
                            g2d.drawString(dateStr, frame.x, frame.y + lineSpace + 1 + dateFm.getAscent());
                        }

                        g2d.dispose();
                    }
                }

                ComparativeCellPanel panel = new ComparativeCellPanel();
                panel.setOpaque(true);

                if (finalRowSelected) {
                    panel.setBackground(finalTable.getSelectionBackground());
                } else {
                    panel.setBackground(finalTable.getBackground());
                }

                // HOROS-20240407準拠: BrowserController.m 11373-11393行目
                com.oculus.jj.model.DicomStudy dicomStudy = null;
                if (value instanceof com.oculus.jj.model.DicomStudy) {
                    dicomStudy = (com.oculus.jj.model.DicomStudy) value;
                } else if (comparativeStudies != null && row >= 0 && row < comparativeStudies.size()) {
                    Object study = comparativeStudies.get(row);
                    if (study instanceof com.oculus.jj.model.DicomStudy) {
                        dicomStudy = (com.oculus.jj.model.DicomStudy) study;
                    }
                }

                if (dicomStudy != null) {
                    // HOROS-20240407準拠: ローカルスタディは太字（BrowserController.m 11383行目）
                    // HOROS-20240407準拠: フォントサイズを2ポイント小さくする（ユーザー要求）
                    java.awt.Font originalFont = finalTable.getFont();
                    java.awt.Font smallerFont = originalFont.deriveFont(java.awt.Font.BOLD,
                            Math.max(9, originalFont.getSize() - 2));
                    panel.setFont(smallerFont);

                    // パネルにデータを設定
                    panel.setStudy(dicomStudy);
                    panel.setSelected(finalRowSelected);
                    panel.setCustomFont(smallerFont);
                    panel.setTable(finalTable);
                }

                return panel;
            }
        });
        // HOROS-20240407準拠: HISTORYパネルの選択変更イベントを処理
        // HOROS-20240407準拠: BrowserController.m 11828-11901行目 -
        // tableViewSelectionDidChange:
        comparativeTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = comparativeTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < comparativeStudies.size()) {
                    Object study = comparativeStudies.get(selectedRow);
                    if (study instanceof com.oculus.jj.model.DicomStudy) {
                        // HOROS-20240407準拠: DBリストで同じstudyInstanceUIDを持つスタディを選択
                        String studyInstanceUID = ((com.oculus.jj.model.DicomStudy) study).getStudyInstanceUID();
                        if (studyInstanceUID != null) {
                            // HOROS-20240407準拠: databaseOutlineで同じstudyInstanceUIDを持つスタディを検索して選択
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    // HOROS-20240407準拠: databaseOutlineで同じstudyInstanceUIDを持つスタディを検索して選択
                                    com.oculus.jj.model.DicomStudy studyToSelect = null;
                                    List<Object> outlineArray = getOutlineViewArray();
                                    if (outlineArray != null) {
                                        for (Object item : outlineArray) {
                                            if (item instanceof com.oculus.jj.model.DicomStudy) {
                                                com.oculus.jj.model.DicomStudy s = (com.oculus.jj.model.DicomStudy) item;
                                                if (studyInstanceUID.equals(s.getStudyInstanceUID())) {
                                                    studyToSelect = s;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    if (studyToSelect != null) {
                                        // HOROS-20240407準拠: スタディを選択
                                        if (databaseOutline.selectStudy(studyToSelect)) {
                                            // HOROS-20240407準拠: 選択変更後にoutlineViewSelectionDidChangeを呼び出す
                                            outlineViewSelectionDidChange();
                                        }
                                    }
                                } catch (Exception ex) {
                                    // エラーは無視
                                }
                            });
                        }
                    }
                }
            }
        });

        // HOROS-20240407準拠: HISTORYパネルのダブルクリックイベントを処理
        // HOROS-20240407準拠: BrowserController.m 14396行目 - [comparativeTable
        // setDoubleAction: @selector(doubleClickComparativeStudy:)];
        comparativeTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = comparativeTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < comparativeStudies.size()) {
                        Object study = comparativeStudies.get(row);
                        if (study instanceof com.oculus.jj.model.DicomStudy) {
                            // HOROS-20240407準拠: ダブルクリックでビューワーを開く
                            // HOROS-20240407準拠: BrowserController.m 11810-11827行目 -
                            // doubleClickComparativeStudy:
                            // HOROS-20240407準拠: displayStudy:object:command:を呼び出す
                            com.oculus.jj.model.DicomStudy dicomStudy = (com.oculus.jj.model.DicomStudy) study;
                            // HOROS-20240407準拠: まずスタディを選択してからビューワーを開く
                            if (databaseOutline.selectStudy(dicomStudy)) {
                                outlineViewSelectionDidChange();
                                // HOROS-20240407準拠: ビューワーを開く（matrixPressedと同様の処理）
                                matrixPressed(null);
                            }
                        }
                    }
                }
            }
        });

        JScrollPane comparativeScroll = new JScrollPane(comparativeTable);
        // HOROS-20240407準拠: 履歴パネルの幅を160ピクセルに固定（ユーザー要求：180ピクセルからさらに縮小、水平スクロールバーが出ないように）
        int historyPanelWidth = 160;
        comparativeScroll.setPreferredSize(new java.awt.Dimension(historyPanelWidth, 0));
        comparativeScroll.setMinimumSize(new java.awt.Dimension(historyPanelWidth, 0));
        comparativeScroll.setMaximumSize(new java.awt.Dimension(historyPanelWidth, Integer.MAX_VALUE));

        // 水平スクロールバーを無効にする（ユーザー要求：水平スクロールバーが出ないように）
        comparativeScroll.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // HOROS-20240407準拠: テーブルのセル間隔を0に設定して余白を減らす
        comparativeTable.setIntercellSpacing(new java.awt.Dimension(0, 0));

        // HOROS-20240407準拠: テーブルの列幅をパネル幅に完全に合わせる
        // スクロールバーが表示されない場合の余白を防ぐため、列幅をパネル幅に完全に合わせる
        comparativeTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);

        // 列幅を動的に調整する関数（水平スクロールバーが出ないように、列幅をパネル幅に完全に合わせる）
        // HOROS-20240407準拠: スクロールバーが表示されていてもいなくても、常にスクロールバー分のスペースを確保
        java.util.function.Consumer<Void> adjustColumnWidth = (Void v) -> {
            if (comparativeTable.getColumnModel().getColumnCount() > 0) {
                javax.swing.JScrollBar verticalScrollBar = comparativeScroll.getVerticalScrollBar();
                int availableWidth = historyPanelWidth;
                // スクロールバーが表示されているかどうかに関わらず、常にスクロールバーの幅を引く
                // HOROS-20240407準拠: スクロールバー分のスペースを常に確保することで、右端の文字が切れないようにする
                if (verticalScrollBar != null) {
                    // スクロールバーの幅を取得（表示されていない場合でも、preferredSizeから取得）
                    int scrollBarWidth = verticalScrollBar.isVisible() ? verticalScrollBar.getWidth()
                            : verticalScrollBar.getPreferredSize().width;
                    availableWidth -= scrollBarWidth;
                }
                // 水平スクロールバーが出ないように、列幅を利用可能な幅に完全に合わせる
                // テーブルの幅と列幅の合計が一致するようにする
                int columnWidth = availableWidth;
                comparativeTable.getColumnModel().getColumn(0).setPreferredWidth(columnWidth);
                comparativeTable.getColumnModel().getColumn(0).setMinWidth(columnWidth);
                comparativeTable.getColumnModel().getColumn(0).setMaxWidth(columnWidth);
                // テーブル自体の幅も設定（列幅の合計と一致させる）
                comparativeTable.setPreferredScrollableViewportSize(new java.awt.Dimension(columnWidth, 0));
                // テーブルのサイズを強制的に更新
                comparativeTable.setSize(columnWidth, comparativeTable.getHeight());
                // テーブルを再描画して、列幅の変更を反映
                comparativeTable.revalidate();
                comparativeTable.repaint();
            }
        };

        // スクロールバーの表示状態に応じて列幅を動的に調整するリスナーを追加
        javax.swing.JScrollBar verticalScrollBar = comparativeScroll.getVerticalScrollBar();
        if (verticalScrollBar != null) {
            verticalScrollBar.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentShown(java.awt.event.ComponentEvent e) {
                    adjustColumnWidth.accept(null);
                }

                @Override
                public void componentHidden(java.awt.event.ComponentEvent e) {
                    adjustColumnWidth.accept(null);
                }
            });
        }

        // 初期状態：列幅をパネル幅に完全に合わせる（スクロールバーが表示されない場合を想定）
        // コンポーネントが表示された後に、スクロールバーの状態を確認して列幅を調整
        SwingUtilities.invokeLater(() -> {
            adjustColumnWidth.accept(null);
        });

        topTablesSplit.setRightComponent(comparativeScroll);
        topTablesSplit.setResizeWeight(1.0); // 患者情報テーブルが可変、履歴パネルが固定
        // HOROS-20240407準拠: 履歴パネルの幅を固定（ディバイダーを非表示にする）
        topTablesSplit.setDividerSize(0); // デバイダーを非表示（動かせない仕様のため）
        SwingUtilities.invokeLater(() -> {
            int totalWidth = topTablesSplit.getWidth();
            if (totalWidth > 0) {
                topTablesSplit.setDividerLocation(totalWidth - historyPanelWidth);
            }
        });

        splitComparative.setTopComponent(topTablesSplit);

        // 下部プレビューエリア（水平スプリッター）: サムネイル（左） | プレビュー（右）
        // HOROS-20240407準拠: MainMenu.xib 4732行目 - splitView id="14624" (Matrix Split)
        // 水平スプリッター
        bottomPreviewSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        // HOROS-20240407準拠: _bottomSplitは標準のNSSplitViewとして実装されている
        // HOROS-20240407準拠: カスタムマウスイベント処理はない - NSSplitViewの標準機能を使用
        // HOROS-20240407準拠: BrowserController.m 10509-10513行目、10585-10586行目
        // Java Swingでは標準のJSplitPaneの機能を使用（カスタムマウスイベント処理を削除）
        // HOROS-20240407準拠: ディバイダーのサイズを2pxに設定（一番右まで来ても制御できるように）
        bottomPreviewSplit.setDividerSize(2);
        bottomPreviewSplit.setBorder(null);
        bottomPreviewSplit.setOpaque(false);

        // HOROS-20240407準拠: ディバイダーを非表示にするため、カスタムUIで描画を無効化
        // HOROS-20240407準拠: ディバイダーのサイズを2pxに設定（一番右まで来ても制御できるように）
        bottomPreviewSplit.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(java.awt.Graphics g) {
                        // 描画しない（完全に非表示）
                    }

                    @Override
                    public java.awt.Dimension getPreferredSize() {
                        // サイズは2px（ドラッグ可能にするため、一番右まで来ても制御できるように）
                        return new java.awt.Dimension(2, 1);
                    }

                    @Override
                    public void setBounds(int x, int y, int width, int height) {
                        // ディバイダーの位置を設定（幅は2px、高さは親の高さ）
                        super.setBounds(x, y, 2, height);
                    }
                };
            }
        });

        // サムネイル（左）- oMatrix
        // HOROS-20240407準拠: MainMenu.xib 4752行目 - scrollView id="12410"
        // (thumbnailsScrollView)
        // HOROS-20240407準拠: MainMenu.xib 4759行目 - matrix id="12411" (oMatrix)
        // HOROS-20240407準拠: BrowserController.h 132行目 IBOutlet BrowserMatrix *oMatrix;
        // HOROS-20240407準拠: BrowserController.h 204行目 IBOutlet NSScrollView
        // *thumbnailsScrollView;
        oMatrix = new BrowserMatrix(this);

        // HOROS-20240407準拠: MainMenu.xib準拠
        // マトリックスを直接JScrollPaneに配置
        // スクロールバーの幅（40px）分だけ表示領域を小さくすることで、スクロールバーがセルに被らないようにする
        // JScrollPaneの作成をtry-catchで囲む（初期化中にgetViewport()が失敗する可能性があるため）
        try {
            thumbnailsScrollView = new JScrollPane(oMatrix);
            // HOROS-20240407準拠: BrowserController.m 14239-14240行目
            // [thumbnailsScrollView setDrawsBackground:NO];
            // [[thumbnailsScrollView contentView] setDrawsBackground:NO];
            thumbnailsScrollView.setBackground(null);
            // JViewportの設定はcomponentShownイベントで実行する（初期化中にgetViewport()が失敗する可能性があるため）
            // HOROS-20240407準拠: 水平スクロールバーは不要（セルサイズに合わせてディバイダー位置を調整するため）
            thumbnailsScrollView.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            // 垂直スクロールバーを常に表示（左側のサムネイルパネル内に表示）
            // スクロールが不要なときは無効化、必要なときだけ有効化
            thumbnailsScrollView.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            // bottomPreviewSplitにthumbnailsScrollViewを設定
            // SwingUtilities.invokeLaterで遅延実行することで、JScrollPaneが完全に初期化されるまで待機
            SwingUtilities.invokeLater(() -> {
                try {
                    if (bottomPreviewSplit != null && thumbnailsScrollView != null) {
                        bottomPreviewSplit.setLeftComponent(thumbnailsScrollView);
                    }
                } catch (Exception ex) {
                    // 初期化中にsetLeftComponent()が失敗する可能性があるため、例外をキャッチ
                    // 後で再試行する
                }
            });
        } catch (Exception ex) {
            // JScrollPaneの作成中にエラーが発生した場合、後で再試行する
            thumbnailsScrollView = null;
        }

        // HOROS-20240407準拠:
        // bottomPreviewSplitのディバイダー位置変更時にpreviewMatrixScrollViewFrameDidChangeを呼び出す
        // componentShownイベントが発生した後にリスナーを追加するため、ここでは追加しない
        // componentShownイベントで追加する

        // プレビュー（右）- imageView
        // HOROS-20240407準拠: MainMenu.xib 4864行目 - id="1166"
        // HOROS-20240407準拠: BrowserController.h 146行目 IBOutlet PreviewView *imageView;
        imageView = new PreviewView();
        // HOROS-20240407準拠: BrowserController.m 6490行目、12635行目
        // annotationType = [[NSUserDefaults standardUserDefaults]
        // integerForKey:@"ANNOTATIONS"];
        // HOROS-20240407準拠: デフォルト値はannotBase（定規とアノテーションを表示）
        int annotationType = java.util.prefs.Preferences.userNodeForPackage(BrowserController.class)
                .getInt("ANNOTATIONS", DCMView.annotBase);
        imageView.setAnnotationType(annotationType);
        // HOROS-20240407準拠: BrowserController.m 14250行目 - [imageView
        // setTheMatrix:oMatrix];
        imageView.setTheMatrix(oMatrix);
        imageView.setBrowserController(this);

        // HOROS-20240407準拠: 左右の矢印キーで画像を遷移できるようにKeyListenerを設定
        if (oMatrix != null) {
            oMatrix.setupKeyListener();
        }

        // プレビュービューにもマウススクロール処理を追加（PreviewViewのscrollWheel delegationを使用するため不要なリスナーを削除）
        // プレビューエリア（右側のスクロールバーは非表示）
        // 手動スクロールバーは非表示にするが、後で使用する可能性があるため保持
        thumbnailsVerticalScrollBar = new javax.swing.JScrollBar(javax.swing.JScrollBar.VERTICAL);
        thumbnailsVerticalScrollBar.setEnabled(false); // 初期状態では無効化
        thumbnailsVerticalScrollBar.setVisible(false); // 非表示

        // 手動スクロールバーのイベントリスナーを追加（JScrollPaneのビューポートと同期）
        // 現在は非表示だが、将来使用する可能性があるため保持
        thumbnailsVerticalScrollBar.addAdjustmentListener(e -> {
            if (thumbnailsScrollView != null && !isAdjustingThumbnailsScrollBar) {
                isAdjustingThumbnailsScrollBar = true;
                try {
                    javax.swing.JViewport viewport = thumbnailsScrollView.getViewport();
                    if (viewport != null) {
                        java.awt.Point currentPosition = viewport.getViewPosition();
                        int newY = e.getValue();
                        if (currentPosition.y != newY) {
                            viewport.setViewPosition(new java.awt.Point(currentPosition.x, newY));
                        }
                    }
                } finally {
                    isAdjustingThumbnailsScrollBar = false;
                }
            }
        });

        // プレビューパネル（スクロールバーは非表示のため、imageViewのみ配置）
        javax.swing.JPanel previewPanel = new javax.swing.JPanel(new java.awt.BorderLayout());
        previewPanel.setOpaque(false);
        previewPanel.add(imageView, java.awt.BorderLayout.CENTER);

        bottomPreviewSplit.setRightComponent(previewPanel);
        bottomPreviewSplit.setResizeWeight(0.3); // サムネイルが30%

        // ステータスバー: 左側（databaseDescription） | 右側（animationSlider + Playボタン）
        // HOROS-20240407準拠: MainMenu.xib 4895行目 - splitView id="14352" (_bottomSplit)
        // ただし、デバイダーは不要なため、JSplitPaneではなくBorderLayoutで配置
        // HOROS-20240407準拠: BrowserController.h 144行目 IBOutlet NSSplitView*
        // _bottomSplit;
        JPanel bottomSplit = new JPanel(new BorderLayout());

        // 左パネル: databaseDescriptionのみ（ボタンは存在しない）
        // HOROS-20240407準拠: MainMenu.xib 4899行目 - id="et0-HG-KxJ"
        // width="431"
        // HOROS-20240407準拠: MainMenu.xib 4914行目 - textField id="14357"
        // (databaseDescription)
        // HOROS-20240407準拠: BrowserController.h 129行目 IBOutlet NSTextField
        // *databaseDescription;
        databaseDescription = new JTextField(
                "Local Database: Documents DB / No album selected / Result = 0 studies (0 images)");
        databaseDescription.setEditable(false);
        databaseDescription.setBorder(null);
        databaseDescription.setHorizontalAlignment(JTextField.LEFT);
        bottomSplit.add(databaseDescription, BorderLayout.WEST);

        // 右パネル: animationSlider + Playボタン
        // HOROS-20240407準拠: MainMenu.xib 4925行目 - id="14354" width="611"
        // HOROS-20240407準拠: MainMenu.xib 4929行目 - slider id="1293" (animationSlider)
        // HOROS-20240407準拠: MainMenu.xib 4939行目 - button id="1294" (Play)
        JPanel rightStatusPanel = new JPanel(null);
        rightStatusPanel.setPreferredSize(new java.awt.Dimension(611, 21));
        rightStatusPanel.setMinimumSize(new java.awt.Dimension(611, 21));
        rightStatusPanel.setMaximumSize(new java.awt.Dimension(611, 21));
        rightStatusPanel.setLayout(null);

        // Playチェックボックス（右端に配置、幅を1.5倍に）
        // 元の幅40px → 60px（1.5倍）
        // HOROS-20240407準拠: BrowserController.h 143行目 IBOutlet NSButton
        // *animationCheck;
        // HOROS-20240407準拠: MainMenu.xib 4942行目 - controlSize="mini"
        // font="metaFont="miniSystem"
        // HOROS-20240407準拠: IBOutlet NSButton *animationCheck; (BrowserController.h
        // 143行目)
        animationCheck = new javax.swing.JCheckBox(LanguageManager.get("browser.play", "Play"));
        java.awt.Font miniFont = new java.awt.Font(animationCheck.getFont().getName(),
                java.awt.Font.PLAIN, Math.max(9, animationCheck.getFont().getSize() - 2));
        animationCheck.setFont(miniFont);
        // 右端に配置、幅60px（1.5倍）、高さ20px（そのまま）
        animationCheck.setBounds(611 - 60, 2, 60, 20);
        // HOROS-20240407準拠: BrowserController.m 14343行目 - 状態を復元
        boolean autoPlayAnimation = java.util.prefs.Preferences.userNodeForPackage(BrowserController.class)
                .getBoolean("AutoPlayAnimation", false);
        animationCheck.setSelected(autoPlayAnimation);
        // HOROS-20240407準拠: チェックボックスのアクション（状態変更時に保存）
        animationCheck.addActionListener(e -> {
            // HOROS-20240407準拠: BrowserController.m 14742行目 - 状態を保存
            java.util.prefs.Preferences.userNodeForPackage(BrowserController.class).putBoolean("AutoPlayAnimation",
                    animationCheck.isSelected());
        });
        rightStatusPanel.add(animationCheck);

        // animationSlider（Playチェックボックスのすぐ左隣に配置）
        // HOROS-20240407準拠: MainMenu.xib 4930行目 - rect width="294" height="16"
        // HOROS-20240407準拠: BrowserController.h 142行目 IBOutlet NSSlider
        // *animationSlider;
        animationSlider = new JSlider(JSlider.HORIZONTAL, 0, 0, 0);
        animationSlider.setEnabled(false);
        // Playチェックボックスの左隣に配置（幅294pxのまま）
        // HOROS-20240407準拠: MainMenu.xib 3193-3213行目 - 3D/4D ラジオボタン + "Open all 4D Series" ボタン
        // 配置: [ 3D ] [ 4D ] [ Open all 4D Series ] [ animationSlider 200px ] [ Play ]
        final javax.swing.ButtonGroup modeGroup = new javax.swing.ButtonGroup();
        preview3DButton = new JRadioButton("3D");
        preview4DButton = new JRadioButton("4D");
        java.awt.Font tinyFont = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 9);
        preview3DButton.setFont(tinyFont);
        preview4DButton.setFont(tinyFont);
        preview3DButton.setOpaque(false);
        preview4DButton.setOpaque(false);
        preview3DButton.setFocusPainted(false);
        preview4DButton.setFocusPainted(false);
        modeGroup.add(preview3DButton);
        modeGroup.add(preview4DButton);
        boolean want4D = java.util.prefs.Preferences.userNodeForPackage(BrowserController.class)
                .getBoolean("Preview4DMode", false);
        (want4D ? preview4DButton : preview3DButton).setSelected(true);
        preview4DButton.addActionListener(e -> {
            java.util.prefs.Preferences.userNodeForPackage(BrowserController.class).putBoolean("Preview4DMode", true);
        });
        preview3DButton.addActionListener(e -> {
            java.util.prefs.Preferences.userNodeForPackage(BrowserController.class).putBoolean("Preview4DMode", false);
        });

        javax.swing.JButton open4DButton = new javax.swing.JButton(LanguageManager.get("menu_4d_open_all", "Open all 4D Series"));
        open4DButton.setFont(tinyFont);
        open4DButton.setOpaque(false);
        open4DButton.setBorderPainted(false);
        open4DButton.setFocusPainted(false);
        open4DButton.addActionListener(e -> movieViewerDICOM(null));

        // animationSliderは幅を縮め、3D/4Dボタン群の右に収める（200px）
        animationSlider.setBounds(611 - 60 - 200, 2, 200, 16);
        animationSlider.addChangeListener(e -> {
            // HOROS-20240407準拠: スライダーの値が変更されたときにpreviewSliderActionを呼び出す
            // getValueIsAdjusting()のチェックを削除して、常にpreviewSliderActionを呼び出す
            previewSliderAction(animationSlider);
        });
        rightStatusPanel.add(animationSlider);

        // ボタン群を animationSlider の左に配置（null layout で手動バインド）
        // 配置 (右パネル幅611): 3D[151,30] 4D[185,30] Open-all-4D[219,128] slider[351,200] Play[551,60]
        preview3DButton.setBounds(151, 2, 30, 18);
        preview4DButton.setBounds(185, 2, 30, 18);
        open4DButton.setBounds(219, 2, 128, 18);
        rightStatusPanel.add(preview3DButton);
        rightStatusPanel.add(preview4DButton);
        rightStatusPanel.add(open4DButton);

        // HOROS-20240407準拠: BrowserController.m 13889行目
        // [NSTimer scheduledTimerWithTimeInterval: 0.15 target:self
        // selector:@selector(previewPerformAnimation:) userInfo:self repeats:YES];
        // Java Swingではjavax.swing.Timerを使用
        animationTimer = new javax.swing.Timer(150, e -> {
            previewPerformAnimation();
        });
        animationTimer.setRepeats(true);
        animationTimer.start();

        // HOROS-20240407準拠: BrowserController.m 14838-14839行目
        // else if(c == ' ')
        // [animationCheck setState: ![animationCheck state]];
        // スペースキーでPlayチェックボックスの状態をトグル
        getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0),
                "togglePlayAnimation");
        getRootPane().getActionMap().put("togglePlayAnimation", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (animationCheck != null) {
                    animationCheck.setSelected(!animationCheck.isSelected());
                    // 状態を保存
                    java.util.prefs.Preferences.userNodeForPackage(BrowserController.class)
                            .putBoolean("AutoPlayAnimation", animationCheck.isSelected());
                }
            }
        });

        bottomSplit.add(rightStatusPanel, BorderLayout.EAST);

        // bottomPreviewSplitのみをsplitComparativeの下部に配置（ステータスバーは後で追加）
        // 右側の垂直スプリットの位置を上から約50%の位置に設定（上部50%、下部50%）
        splitComparative.setBottomComponent(bottomPreviewSplit);
        splitComparative.setResizeWeight(0.5); // 上部テーブルエリアが50%、下部プレビューエリアが50%

        // ========== メインウィンドウ（水平スプリッター） ==========
        // HOROS-20240407準拠: splitViewHorz - 左サイドバー | 右メインパネル
        // HOROS-20240407準拠: BrowserController.h 111行目 IBOutlet NSSplitView
        // *splitViewHorz;
        // HOROS-20240407準拠: MainMenu.xib 12422行目 - splitView id="12422"
        JSplitPane splitViewHorz = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        // HOROS-20240407準拠: splitAlbumsは左サイドバー全体（Albums、Sources、Activityを含む）
        splitViewHorz.setLeftComponent(splitAlbums);
        splitViewHorz.setRightComponent(splitComparative);
        splitViewHorz.setResizeWeight(0.2); // 左サイドバーが20%

        // ステータスバーをウィンドウ全体の最下部に配置
        // HOROS-20240407準拠: MainMenu.xib 4895行目 - splitView id="14352" (_bottomSplit)
        // 水平スプリッター
        // HOROS-20240407準拠: ステータスバーはsplitViewHorzの下に配置（ウィンドウ全体の最下部）
        JPanel mainContentPanel = new JPanel(new BorderLayout());
        mainContentPanel.add(splitViewHorz, BorderLayout.CENTER);
        mainContentPanel.add(bottomSplit, BorderLayout.SOUTH);

        // HOROS-20240407準拠: ツールバーはウィンドウ上部（NORTH）に配置
        contentPane.add(toolbarScroll, BorderLayout.NORTH);

        contentPane.add(mainContentPanel, BorderLayout.CENTER);

        // HOROS-20240407準拠: 検索フィールド
        // HOROS-20240407準拠: BrowserController.h 180行目 IBOutlet NSSearchField
        // *searchField;
        searchField = browserToolbar != null ? browserToolbar.getSearchField() : null;
        if (searchField != null) {
            searchField.setToolTipText(LanguageManager.get("browser.searchHint"));
            searchField.addActionListener(e -> {
                // HOROS-20240407準拠: 検索を実行してアウトラインビューを更新
                outlineViewRefresh();
                if (com.oculus.jj.config.AppPreferences.getBool("ondemand.active", false)
                        && com.oculus.jj.config.AppPreferences.getBool("ondemand.useToolbarSearch", true)
                        && !searchField.getText().trim().isEmpty()) {
                    int searchType = browserToolbar.getSearchTypeCombo() == null
                            ? 0 : browserToolbar.getSearchTypeCombo().getSelectedIndex();
                    QueryController.getInstance().setToolbarSearchAndQuery(searchField.getText().trim(), searchType);
                }
            });

            // HOROS-20240407準拠: NSSearchFieldのリアルタイム検索（ディレイ付き）を再現
            // 入力が停止してから250ms後に検索を実行するデバウンスタイマー
            javax.swing.Timer searchDelayTimer = new javax.swing.Timer(250, ev -> {
                outlineViewRefresh();
            });
            searchDelayTimer.setRepeats(false);

            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                private void triggerSearch() {
                    searchDelayTimer.restart();
                }
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    triggerSearch();
                }
                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    triggerSearch();
                }
                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    triggerSearch();
                }
            });
        }

        // HOROS-20240407準拠: ドラッグ&ドロップ対応
        setupDragAndDrop();

        // HOROS-20240407準拠: awakeActivityを呼び出す（BrowserController.m 14432行目）
        // BrowserController+Activity.mm
        // 64-72行目で_activityTableViewのdelegateとdataSourceを設定
        // ただし、JavaではactivityHelperのコンストラクタで既に設定されているため、ここでは不要
        // 念のため、activityTableViewが正しく設定されていることを確認
        if (this.activityTableView != null && activityHelper != null) {
            // activityTableViewは既にactivityHelperをTableModelとして設定済み
            // BrowserActivityHelperがThreadsManagerを監視して自動的に更新される
        }

        // アルバムテーブルのデータを更新
        updateAlbumTable();

        // HOROS-20240407準拠: BrowserController.m 14312-14335行目
        // データベースアウトラインの初期状態を設定し、列状態とソート状態を復元
        // HOROS-20240407準拠: [databaseOutline setInitialState] (14312行目)
        // HOROS-20240407準拠: restoreColumnState (14315-14316行目)
        // HOROS-20240407準拠: databaseSortDescriptorの復元 (14318-14333行目)
        // databaseOutlineが完全に初期化された後に復元するため、SwingUtilities.invokeLaterで遅延させる
        SwingUtilities.invokeLater(() -> {
            // HOROS-20240407準拠: BrowserController.m 14315-14316行目
            // カラム状態を復元
            restoreDatabaseColumnState();

            // HOROS-20240407準拠: BrowserController.m 14318-14333行目
            // ソート状態を復元（列状態の復元後に実行）
            restoreSortState();

            // HOROS-20240407準拠: BrowserController.m 14337-14338行目
            // 起動時に最初の行（インデックス0）を選択
            // [databaseOutline selectRowIndexes: [NSIndexSet indexSetWithIndex: 0]
            // byExtendingSelection:NO];
            // [databaseOutline scrollRowToVisible: 0];
            // HOROS-20240407準拠:
            // selectRowIndexesを呼び出すと自動的にNSOutlineViewSelectionDidChangeNotificationが送信され、
            // outlineViewSelectionDidChangeが呼び出される
            // ただし、loadingIsOverがtrueになる前に選択処理が実行されると、outlineViewSelectionDidChangeが早期リターンするため、
            // loadingIsOverがtrueになった後に選択処理を実行する必要がある
            // そのため、ここでは選択処理を行わず、loadingIsOverがtrueになった後に実行する
            if (databaseOutline != null && databaseOutline.getRowCount() > 0) {
                javax.swing.tree.TreePath path = databaseOutline.getPathForRow(0);
                if (path != null) {
                    databaseOutline.getTreeSelectionModel().setSelectionPath(path);
                    databaseOutline.scrollPathToVisible(path);
                }
            }

            // HOROS-20240407準拠: BrowserController.m 14339行目
            // 列メニューを構築
            buildColumnsMenu();

            // HOROS-20240407準拠: 列の表示/非表示状態を復元（COLUMNSDATABASEから）
            refreshColumns();

        });

        // ディバイダー位置変更を監視（保存用）
        // HOROS-20240407準拠: NSSplitViewのautosaveNameにより自動保存されるが、Java Swingでは手動実装が必要
        // 注意: 復元処理が完了するまで保存を無効化するため、componentShownで設定
        // ディバイダー位置を復元（ウィンドウが表示された後に一度だけ実行）
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                // UI初期化が完了したことをマーク
                uiInitialized = true;

                // JViewportの設定を実行（初期化中に失敗した可能性があるため、componentShownイベントで再設定）
                if (thumbnailsScrollView != null) {
                    try {
                        javax.swing.JViewport viewport = thumbnailsScrollView.getViewport();
                        if (viewport != null) {
                            viewport.setBackground(null);
                            // HOROS-20240407準拠: JViewportのサイズ制約を無効化
                            // マトリックスのサイズがスクロールビューのサイズより広くなることを許可
                            // MainMenu.xib準拠: マトリックスの幅（424px）がスクロールビューの幅（384px）より40px広い
                            viewport.setScrollMode(javax.swing.JViewport.BLIT_SCROLL_MODE);
                        }
                    } catch (NullPointerException ex) {
                        // 初期化中にgetViewport()が失敗する可能性があるため、例外をキャッチ
                        // 後で再試行する
                    }
                }

                // フレーム変更通知を監視（componentShownイベントが発生した後にリスナーを追加）
                if (thumbnailsScrollView != null) {
                    thumbnailsScrollView.addComponentListener(new java.awt.event.ComponentAdapter() {
                        @Override
                        public void componentResized(java.awt.event.ComponentEvent e) {
                            // 初期化が完了していない場合は処理をスキップ
                            if (!uiInitialized) {
                                return;
                            }
                            if (thumbnailsScrollView == null || !thumbnailsScrollView.isDisplayable()) {
                                return;
                            }
                            if (bottomPreviewSplit == null || !bottomPreviewSplit.isDisplayable()) {
                                return;
                            }
                            previewMatrixScrollViewFrameDidChange();
                        }
                    });

                    // HOROS-20240407準拠: スクロールビュー上のマウスホイールでプレビューを切り替える
                    thumbnailsScrollView.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
                        @Override
                        public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                            previewScrollWheel(e.getWheelRotation());
                        }
                    });

                    // ビューポートの変更を監視して、手動スクロールバーの値を更新
                    javax.swing.JViewport viewport = thumbnailsScrollView.getViewport();
                    if (viewport != null && thumbnailsVerticalScrollBar != null) {
                        viewport.addChangeListener(changeEvent -> {
                            if (thumbnailsVerticalScrollBar != null && !isAdjustingThumbnailsScrollBar) {
                                java.awt.Point viewPosition = viewport.getViewPosition();
                                int currentValue = thumbnailsVerticalScrollBar.getValue();
                                if (currentValue != viewPosition.y) {
                                    isAdjustingThumbnailsScrollBar = true;
                                    try {
                                        thumbnailsVerticalScrollBar.setValue(viewPosition.y);
                                    } finally {
                                        isAdjustingThumbnailsScrollBar = false;
                                    }
                                }
                            }
                        });
                    }
                }

                // HOROS-20240407準拠:
                // bottomPreviewSplitのディバイダー位置変更時にpreviewMatrixScrollViewFrameDidChangeを呼び出す
                // HOROS-20240407準拠: ディバイダー位置変更の制約処理を防ぐためのフラグ
                // setDividerLocationを呼び出す際に無限ループを防ぐ
                if (bottomPreviewSplit != null) {
                    bottomPreviewSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
                        // 制約処理中はスキップ（無限ループ防止）
                        if (isAdjustingDivider) {
                            return;
                        }

                        if (!uiInitialized) {
                            return;
                        }

                        if (thumbnailsScrollView == null || !thumbnailsScrollView.isDisplayable()) {
                            return;
                        }

                        // HOROS-20240407準拠: splitView:constrainSplitPosition:ofSubviewAt:
                        // (BrowserController.m 10194-10224行目)
                        // ディバイダーの位置をセルサイズに合わせて調整し、セルが隠れないようにする
                        // HOROS-20240407準拠: 常にセル幅に合わせて制約を適用（常にセル幅分しか動かない）
                        if (oMatrix != null) {
                            java.awt.Dimension cellSize = oMatrix.getCellSize();
                            if (cellSize != null && cellSize.width > 0) {
                                java.awt.Dimension intercellSpacingDim = oMatrix.getIntercellSpacing();
                                int intercellSpacing = intercellSpacingDim != null ? intercellSpacingDim.width : 0;
                                int rcs = cellSize.width + intercellSpacing;

                                if (rcs > 0) {
                                    int currentLocation = bottomPreviewSplit.getDividerLocation();

                                    // HOROS-20240407準拠: hcells =
                                    // MAX(roundf((proposedPosition+oMatrix.intercellSpacing.width)/rcs), 1)
                                    // (10217行目)
                                    int hcells = Math
                                            .max(Math.round((float) (currentLocation + intercellSpacing) / rcs), 1);
                                    // HOROS-20240407準拠: proposedPosition =
                                    // rcs*hcells-oMatrix.intercellSpacing.width (10218行目)
                                    int constrainedLocation = rcs * hcells - intercellSpacing;

                                    // スクロールバーの幅を考慮（別コンポーネントとして固定配置されるため）
                                    int scrollbarWidth = 0;
                                    if (thumbnailsVerticalScrollBar != null) {
                                        scrollbarWidth = thumbnailsVerticalScrollBar.getPreferredSize().width;
                                    }
                                    if (scrollbarWidth == 0) {
                                        Object scrollBarWidthObj = javax.swing.UIManager.get("ScrollBar.width");
                                        if (scrollBarWidthObj instanceof Integer) {
                                            scrollbarWidth = (Integer) scrollBarWidthObj;
                                        } else {
                                            scrollbarWidth = 16; // デフォルト値
                                        }
                                    }
                                    constrainedLocation += scrollbarWidth;

                                    // HOROS-20240407準拠: constrainMaxCoordinate (BrowserController.m 10657-10658行目)
                                    // 最大位置を制限（プレビュー画面があるので右側に制限）
                                    int maxLocation = bottomPreviewSplit.getWidth() - 200; // HOROS-20240407準拠: width -
                                                                                           // 200
                                    constrainedLocation = Math.min(constrainedLocation, maxLocation);

                                    // HOROS-20240407準拠: 常にセル幅に合わせて制約を適用（位置が変更された場合のみ調整）
                                    if (Math.abs(constrainedLocation - currentLocation) > 1) {
                                        isAdjustingDivider = true;
                                        try {
                                            bottomPreviewSplit.setDividerLocation(constrainedLocation);
                                        } finally {
                                            isAdjustingDivider = false;
                                        }
                                        // 位置を調整した後、previewMatrixScrollViewFrameDidChangeを呼び出してセルを並べ替え
                                        // HOROS-20240407準拠: ディバイダー位置変更後、セルの行・列を再計算
                                        SwingUtilities.invokeLater(() -> {
                                            if (uiInitialized) {
                                                previewMatrixScrollViewFrameDidChange();
                                            }
                                        });
                                        return;
                                    }
                                }
                            }
                        }

                        // HOROS-20240407準拠: constrainMaxCoordinate (BrowserController.m 10657-10658行目)
                        // 最大位置を制限（プレビュー画面があるので右側に制限）
                        int currentLocation = bottomPreviewSplit.getDividerLocation();
                        int maxLocation = bottomPreviewSplit.getWidth() - 200; // HOROS-20240407準拠: width - 200
                        if (currentLocation > maxLocation) {
                            isAdjustingDivider = true;
                            try {
                                bottomPreviewSplit.setDividerLocation(maxLocation);
                            } finally {
                                isAdjustingDivider = false;
                            }
                        }

                        // 位置を調整しなかった場合、通常通りpreviewMatrixScrollViewFrameDidChangeを呼び出してセルを並べ替え
                        // HOROS-20240407準拠: ディバイダー位置変更後、セルの行・列を再計算
                        if (uiInitialized) {
                            previewMatrixScrollViewFrameDidChange();
                        }
                    });
                }

                // HOROS-20240407準拠: BrowserController.m 14233行目
                // [self previewMatrixScrollViewFrameDidChange:nil];
                // コンポーネントが表示された後に一度だけ呼び出す
                SwingUtilities.invokeLater(() -> {
                    if (uiInitialized) {
                        previewMatrixScrollViewFrameDidChange();
                    }
                });

                if (!dividerLocationsRestored) {
                    // コンポーネントのサイズが確定してから復元するため、SwingUtilities.invokeLaterで遅延
                    SwingUtilities.invokeLater(() -> {
                        SwingUtilities.invokeLater(() -> {
                            restoreDividerLocations();
                            dividerLocationsRestored = true;

                            // 復元が完了した後に、ディバイダー位置変更を監視して保存
                            if (splitAlbums != null) {
                                splitAlbums.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
                                    saveDividerLocations();
                                });
                            }
                            if (splitSourcesActivity != null) {
                                splitSourcesActivity.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
                                        evt -> {
                                            saveDividerLocations();
                                        });
                            }
                            if (splitComparative != null) {
                                splitComparative.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
                                        evt -> {
                                            saveDividerLocations();
                                        });
                            }

                            // HOROS-20240407準拠: BrowserController.m 14428行目
                            // loadingIsOver = YES;
                            // すべての初期化と復元が完了した後に設定
                            loadingIsOver = true;

                            // HOROS-20240407準拠: BrowserController.m 14430行目
                            // [self outlineViewRefresh];
                            // outlineViewRefreshが呼び出されると、outlineViewSelectionDidChangeが呼び出される
                            // ただし、起動時に最初の行を選択する処理は、loadingIsOverがtrueになった後に実行する必要がある
                            // HOROS-20240407準拠: BrowserController.m 14337-14338行目
                            // [databaseOutline selectRowIndexes: [NSIndexSet indexSetWithIndex: 0]
                            // byExtendingSelection:NO];
                            // [databaseOutline scrollRowToVisible: 0];
                            // HOROS-20240407準拠:
                            // selectRowIndexesを呼び出すと自動的にNSOutlineViewSelectionDidChangeNotificationが送信され、
                            // outlineViewSelectionDidChangeが呼び出される
                            SwingUtilities.invokeLater(() -> {
                                if (databaseOutline != null && databaseOutline.getRowCount() > 0) {
                                    javax.swing.tree.TreePath path = databaseOutline.getPathForRow(0);
                                    if (path != null) {
                                        // HOROS-20240407準拠:
                                        // setSelectionPathを呼び出すと、TreeSelectionListenerのvalueChangedが呼び出され、
                                        // databasePressedが呼び出され、それがoutlineViewSelectionDidChangeを呼び出す
                                        databaseOutline.getTreeSelectionModel().setSelectionPath(path);
                                        databaseOutline.scrollPathToVisible(path);
                                        // HOROS-20240407準拠:
                                        // この時点でloadingIsOverはtrueなので、outlineViewSelectionDidChangeが正常に実行される
                                        // databasePressedが自動的にoutlineViewSelectionDidChangeを呼び出すため、ここでは明示的に呼び出す必要はない
                                    }
                                }
                            });
                        });
                    });
                }
            }
        });

        // HOROS-20240407準拠: BrowserController.m 14682行目
        // ウィンドウが閉じられる時に位置・サイズを保存
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveWindowFrame();
                saveDatabaseColumnState();
                saveSortState();
                saveDividerLocations();
            }
        });
    }

    /**
     * ウィンドウ位置・サイズを復元
     * HOROS-20240407準拠: BrowserController.m 14183-14188行目
     * r = NSRectFromString( [[NSUserDefaults standardUserDefaults]
     * stringForKey: @"DBWindowFrame"]);
     * if( NSIsEmptyRect( r)) // No position for the window -> fullscreen
     * [[self window] zoom: self];
     * else
     * [self.window setFrame: r display: YES];
     */
    private void restoreWindowFrame() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);
            String frameStr = prefs.get("DBWindowFrame", null);

            if (frameStr != null && !frameStr.isEmpty()) {
                // 形式: "x,y,width,height"
                String[] parts = frameStr.split(",");
                if (parts.length == 4) {
                    try {
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        int width = Integer.parseInt(parts[2]);
                        int height = Integer.parseInt(parts[3]);

                        // 画面内に収まるかチェック
                        java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
                        java.awt.GraphicsDevice[] screens = ge.getScreenDevices();
                        boolean isValidPosition = false;

                        for (java.awt.GraphicsDevice screen : screens) {
                            java.awt.Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();
                            if (x >= screenBounds.x && y >= screenBounds.y &&
                                    x + width <= screenBounds.x + screenBounds.width &&
                                    y + height <= screenBounds.y + screenBounds.height) {
                                isValidPosition = true;
                                break;
                            }
                        }

                        if (isValidPosition) {
                            setBounds(x, y, width, height);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        // 無効な形式の場合はデフォルトサイズを使用
                    }
                }
            }
        } catch (Exception e) {
            // エラーが発生した場合はデフォルトサイズを使用
        }

        // デフォルトサイズ（画面中央に配置）
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // HOROS-20240407準拠: BrowserController.m 14233行目
        // [self previewMatrixScrollViewFrameDidChange:nil];
        // UI初期化が完了した後に一度だけ呼び出す
        // ただし、コンポーネントが完全に初期化されるまで待つため、componentShownイベントで呼び出す
        // uiInitializedフラグは、componentShownイベントで設定する
    }

    /**
     * ウィンドウ位置・サイズを保存
     * HOROS-20240407準拠: BrowserController.m 14682行目
     * [[NSUserDefaults standardUserDefaults] setObject: NSStringFromRect(
     * self.window.frame) forKey: @"DBWindowFrame"];
     */
    private void saveWindowFrame() {
        try {
            java.awt.Rectangle bounds = getBounds();
            // 形式: "x,y,width,height"
            String frameStr = bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height;

            Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);
            prefs.put("DBWindowFrame", frameStr);
            // HOROS-20240407準拠: BrowserController.m 14742行目
            // [[NSUserDefaults standardUserDefaults] setBool: [animationCheck state]
            // forKey: @"AutoPlayAnimation"];
            if (animationCheck != null) {
                prefs.putBoolean("AutoPlayAnimation", animationCheck.isSelected());
            }
            prefs.flush();
        } catch (Exception e) {
            // エラーが発生した場合は保存をスキップ
        }
    }

    /**
     * ディバイダー位置を保存
     * HOROS-20240407準拠 - NSSplitViewのautosaveName
     */
    private void saveDividerLocations() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);

            // splitAlbumsとsplitSourcesActivityのディバイダー位置を保存（比率で保存）
            if (splitAlbums != null && splitAlbums.getHeight() > 0) {
                int albumsDividerLocation = splitAlbums.getDividerLocation();
                double albumsRatio = (double) albumsDividerLocation / splitAlbums.getHeight();
                prefs.putDouble("AlbumsDividerRatio", albumsRatio);
            }

            if (splitSourcesActivity != null && splitSourcesActivity.getHeight() > 0) {
                int sourcesActivityDividerLocation = splitSourcesActivity.getDividerLocation();
                double sourcesActivityRatio = (double) sourcesActivityDividerLocation
                        / splitSourcesActivity.getHeight();
                prefs.putDouble("SourcesActivityDividerRatio", sourcesActivityRatio);
            }

            // splitComparativeのディバイダー位置も保存（比率で保存）
            if (splitComparative != null && splitComparative.getHeight() > 0) {
                int comparativeDividerLocation = splitComparative.getDividerLocation();
                double comparativeRatio = (double) comparativeDividerLocation / splitComparative.getHeight();
                prefs.putDouble("ComparativeDividerRatio", comparativeRatio);
            }

            // bottomPreviewSplitのディバイダー位置も保存（比率で保存）
            // HOROS-20240407準拠: NSSplitViewのautosaveNameにより自動保存されるが、Java Swingでは手動実装が必要
            if (bottomPreviewSplit != null && bottomPreviewSplit.getWidth() > 0) {
                int bottomPreviewDividerLocation = bottomPreviewSplit.getDividerLocation();
                double bottomPreviewRatio = (double) bottomPreviewDividerLocation / bottomPreviewSplit.getWidth();
                prefs.putDouble("BottomPreviewDividerRatio", bottomPreviewRatio);
            }

            prefs.flush();
        } catch (Exception e) {
            // エラーが発生した場合は保存をスキップ
        }
    }

    /**
     * ディバイダー位置を復元
     * HOROS-20240407準拠 - NSSplitViewのautosaveName
     */
    private void restoreDividerLocations() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);

            // 復元中はPropertyChangeListenerを無効化（無限ループ防止）
            isAdjustingDivider = true;
            try {
                // splitAlbumsとsplitSourcesActivityのディバイダー位置を復元（比率で復元）
                if (splitAlbums != null && splitAlbums.getHeight() > 0) {
                    double savedRatio = prefs.getDouble("AlbumsDividerRatio", -1.0);
                    if (savedRatio > 0.0 && savedRatio < 1.0) {
                        // 保存された比率で復元
                        splitAlbums.setDividerLocation(savedRatio);
                    } else {
                        // デフォルト位置: Albumsが198px（比率で計算）
                        // ただし、高さが確定していない場合は後で再設定
                        double albumsRatio = 198.0 / 687.0;
                        splitAlbums.setDividerLocation(albumsRatio);
                    }
                }

                if (splitSourcesActivity != null && splitSourcesActivity.getHeight() > 0) {
                    double savedRatio = prefs.getDouble("SourcesActivityDividerRatio", -1.0);
                    if (savedRatio > 0.0 && savedRatio < 1.0) {
                        // 保存された比率で復元
                        splitSourcesActivity.setDividerLocation(savedRatio);
                    } else {
                        // デフォルト位置: Sourcesが1/3、Activityが2/3
                        splitSourcesActivity.setDividerLocation(0.33);
                    }
                }

                // splitComparativeのディバイダー位置も復元（比率で復元）
                if (splitComparative != null && splitComparative.getHeight() > 0) {
                    double savedRatio = prefs.getDouble("ComparativeDividerRatio", -1.0);
                    if (savedRatio > 0.0 && savedRatio < 1.0) {
                        // 保存された比率で復元
                        splitComparative.setDividerLocation(savedRatio);
                    } else {
                        // デフォルト位置: 上から50%
                        splitComparative.setDividerLocation(0.5);
                    }
                }

                // bottomPreviewSplitのディバイダー位置を復元（比率で復元）
                // HOROS-20240407準拠: NSSplitViewのautosaveNameにより自動復元されるが、Java Swingでは手動実装が必要
                if (bottomPreviewSplit != null && bottomPreviewSplit.getWidth() > 0) {
                    double savedRatio = prefs.getDouble("BottomPreviewDividerRatio", -1.0);
                    if (savedRatio > 0.0 && savedRatio < 1.0) {
                        // 保存された比率で復元
                        bottomPreviewSplit.setDividerLocation(savedRatio);
                    } else {
                        // デフォルト位置: 左から30%（サムネイルが30%）
                        bottomPreviewSplit.setDividerLocation(0.3);
                    }
                }
            } finally {
                // 復元完了後、PropertyChangeListenerを再有効化
                isAdjustingDivider = false;
            }
        } catch (Exception e) {
            // エラーが発生した場合は復元をスキップ
            isAdjustingDivider = false;
        }
    }

    /**
     * ドラッグ&ドロップ機能を設定
     * HOROS-20240407準拠:
     * MyOutlineViewのdraggingEntered/draggingUpdated/performDragOperation
     */
    private void setupDragAndDrop() {
        // ウィンドウ全体にドロップターゲットを設定
        new DropTarget(this, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = dtde.getTransferable();

                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

                        // ファイルパスのリストを作成
                        List<String> filePaths = new ArrayList<>();
                        for (File file : files) {
                            if (file.isFile()) {
                                filePaths.add(file.getAbsolutePath());
                            } else if (file.isDirectory()) {
                                // ディレクトリの場合は再帰的にファイルを収集
                                collectDicomFiles(file, filePaths);
                            }
                        }

                        if (!filePaths.isEmpty()) {
                            // HOROS-20240407準拠: BrowserController.m 1472行目
                            // [self.database addFilesAtPaths:localFiles]
                            importFiles(filePaths);
                        }

                        dtde.dropComplete(true);
                    } else {
                        dtde.rejectDrop();
                    }
                } catch (UnsupportedFlavorException | IOException e) {
                    dtde.rejectDrop();
                }
            }
        });
    }

    /**
     * ディレクトリからDICOMファイルを再帰的に収集
     */
    private void collectDicomFiles(File directory, List<String> filePaths) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    // DICOMファイルの拡張子をチェック（.dcm, .dicom, 拡張子なしなど）
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".dcm") || name.endsWith(".dicom") ||
                            name.endsWith(".ima") || name.endsWith(".img") ||
                            !name.contains(".")) {
                        filePaths.add(file.getAbsolutePath());
                    }
                } else if (file.isDirectory()) {
                    collectDicomFiles(file, filePaths);
                }
            }
        }
    }

    /**
     * ファイルとフォルダをデータベースに追加
     * HOROS-20240407準拠: - (void) addFilesAndFolderToDatabase:(NSArray*) filenames
     * (864行目)
     * 
     * このメソッドはファイルを収集してから、copyFilesIntoDatabaseIfNeededを呼び出す
     * HOROS-20240407準拠: BrowserController.m 864-998行目
     */
    public void addFilesAndFolderToDatabase(List<String> filenames) {
        if (database == null || filenames == null || filenames.isEmpty()) {
            return;
        }

        // HOROS-20240407準拠: BrowserController.m 870行目
        // NSMutableArray *filesArray = [[[NSMutableArray alloc] initWithCapacity:0]
        // autorelease];
        List<String> filesArray = new ArrayList<>();

        // HOROS-20240407準拠: BrowserController.m 872-995行目
        for (String filename : filenames) {
            try {
                java.io.File file = new java.io.File(filename);
                if (!file.exists()) {
                    continue;
                }

                // HOROS-20240407準拠: BrowserController.m 878行目
                // if( [[filename lastPathComponent] characterAtIndex: 0] != '.')
                String lastComponent = file.getName();
                if (lastComponent.isEmpty() || lastComponent.charAt(0) == '.') {
                    continue;
                }

                if (file.isDirectory()) {
                    // HOROS-20240407準拠: BrowserController.m 880-950行目
                    // ディレクトリの場合は再帰的にファイルを列挙
                    // ただし、pagesやapp拡張子のディレクトリはスキップ
                    String extension = getPathExtension(filename);
                    if ("pages".equals(extension) || "app".equals(extension)) {
                        continue;
                    }

                    if (com.oculus.jj.config.AppPreferences.getInt("cddvd.contentMode", 1) == 1) {
                        List<java.nio.file.Path> indexedFiles = List.of();
                        try {
                            indexedFiles = com.oculus.jj.model.DicomDirectoryUtils.referencedFiles(file.toPath());
                        } catch (Exception ex) {
                            // Fall through to Scan Mode only when the preference allows it.
                        }
                        if (!indexedFiles.isEmpty()) {
                            for (java.nio.file.Path indexedFile : indexedFiles) filesArray.add(indexedFile.toString());
                            continue;
                        }
                        if (!com.oculus.jj.config.AppPreferences.getBool("cddvd.switchToScanWhenEmpty", true)) continue;
                    }

                    // HOROS-20240407準拠: BrowserController.m 886行目
                    // NSDirectoryEnumerator *enumer = [[NSFileManager defaultManager]
                    // enumeratorAtPath: filename];
                    String folderSkip = null;
                    try {
                        java.nio.file.Files.walk(java.nio.file.Paths.get(filename))
                                .filter(p -> {
                                    try {
                                        return java.nio.file.Files.isRegularFile(p);
                                    } catch (Exception e) {
                                        return false;
                                    }
                                })
                                .forEach(p -> {
                                    try {
                                        String itemPath = p.toString();
                                        String pathname = java.nio.file.Paths.get(filename).relativize(p).toString();

                                        // HOROS-20240407準拠: BrowserController.m 901-903行目
                                        // folderSkipの処理（簡略化）

                                        // HOROS-20240407準拠: BrowserController.m 909行目
                                        String itemLastComponent = p.getFileName().toString();
                                        if (itemLastComponent.isEmpty() || itemLastComponent.charAt(0) == '.') {
                                            return;
                                        }

                                        // HOROS-20240407準拠: BrowserController.m 911-935行目
                                        // 特殊なファイルタイプの処理
                                        String itemExtension = getPathExtension(itemPath);
                                        if ("dcmURLs".equals(itemExtension)) {
                                            // HOROS-20240407準拠: BrowserController.m 913-917行目
                                        } else if ("zip".equals(itemExtension) || "osirixzip".equals(itemExtension)) {
                                            // HOROS-20240407準拠: BrowserController.m 919-930行目
                                        } else if ("DICOMDIR".equalsIgnoreCase(itemLastComponent)
                                                || "DICOMDIR.".equalsIgnoreCase(itemLastComponent)) {
                                            // HOROS-20240407準拠: BrowserController.m 932-933行目
                                        } else {
                                            // HOROS-20240407準拠: BrowserController.m 935行目
                                            filesArray.add(itemPath);
                                        }
                                    } catch (Exception e) {
                                        // HOROS-20240407準拠: BrowserController.m 944-947行目
                                        // エラーが発生したファイルはスキップ
                                    }
                                });
                    } catch (Exception e) {
                        // エラーが発生したディレクトリはスキップ
                    }
                } else {
                    // HOROS-20240407準拠: BrowserController.m 952-984行目
                    // ファイルの場合
                    String extension = getPathExtension(filename);
                    if ("xml".equals(extension)) {
                        // HOROS-20240407準拠: BrowserController.m 954-956行目
                    } else if ("dcmURLs".equals(extension)) {
                        // HOROS-20240407準拠: BrowserController.m 958-964行目
                    } else if ("zip".equals(extension) || "osirixzip".equals(extension)) {
                        // HOROS-20240407準拠: BrowserController.m 966-977行目
                    } else if ("DICOMDIR".equalsIgnoreCase(lastComponent)
                            || "DICOMDIR.".equalsIgnoreCase(lastComponent)) {
                        // HOROS-20240407準拠: BrowserController.m 979-980行目
                    } else if ("app".equals(extension)) {
                        // HOROS-20240407準拠: BrowserController.m 981-983行目
                        // appファイルはスキップ
                    } else {
                        // HOROS-20240407準拠: BrowserController.m 984行目
                        filesArray.add(filename);
                    }
                }
            } catch (Exception e) {
                // HOROS-20240407準拠: BrowserController.m 989-992行目
                // エラーが発生したファイルはスキップ
            }
        }

        // HOROS-20240407準拠: BrowserController.m 997行目
        // [self copyFilesIntoDatabaseIfNeeded: filesArray options: [NSDictionary
        // dictionaryWithObjectsAndKeys: ...]]
        if (!filesArray.isEmpty()) {
            Map<String, Object> options = new HashMap<>();
            // HOROS-20240407準拠: BrowserController.m 997行目
            // [[NSUserDefaults standardUserDefaults] objectForKey: @"onlyDICOM"]
            options.put("onlyDICOM", com.oculus.jj.config.AppPreferences.getBool("database.dicomOnly", true));
            options.put("async", true); // HOROS-20240407準拠: [NSNumber numberWithBool: YES], @"async"
            options.put("addToAlbum", true); // HOROS-20240407準拠: [NSNumber numberWithBool: YES], @"addToAlbum"
            options.put("selectStudy", true); // HOROS-20240407準拠: [NSNumber numberWithBool: YES], @"selectStudy"
            copyFilesIntoDatabaseIfNeeded(filesArray, options);
        }
    }

    /**
     * パスの拡張子を取得
     * HOROS-20240407準拠: [NSString pathExtension]
     */
    private String getPathExtension(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int lastDot = path.lastIndexOf('.');
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastDot > lastSep && lastDot < path.length() - 1) {
            return path.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * ファイルをインポート
     * HOROS-20240407準拠: addFilesAndFolderToDatabaseを呼び出す
     * 
     * 注意: このメソッドは後方互換性のために残されている
     * 新しいコードではaddFilesAndFolderToDatabaseを使用すること
     */
    public void importFiles(List<String> filePaths) {
        addFilesAndFolderToDatabase(filePaths);
    }

    /**
     * ファイル選択ダイアログからインポート
     * HOROS-20240407準拠: メニューからのインポート機能
     */
    public void importFilesFromDialog() {
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setDialogTitle(LanguageManager.get("browser.importDicomTitle"));

        int result = fileChooser.showOpenDialog(this);
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            List<String> filePaths = new ArrayList<>();

            for (File file : selectedFiles) {
                if (file.isFile()) {
                    filePaths.add(file.getAbsolutePath());
                } else if (file.isDirectory()) {
                    collectDicomFiles(file, filePaths);
                }
            }

            if (!filePaths.isEmpty()) {
                importFiles(filePaths);
            }
        }

        // HOROS-20240407準拠: BrowserController.m 14316行目
        // カラム状態を復元
        restoreDatabaseColumnState();

        // HOROS-20240407準拠: ソート状態を復元
        // HOROS-20240407準拠: restoreColumnStateの後にrestoreSortStateを呼び出す（14315-14333行目）
        // 列の状態が復元された後にソート状態を復元するため、SwingUtilities.invokeLaterで遅延させる
        SwingUtilities.invokeLater(() -> {
            restoreSortState();
        });

        // HOROS-20240407準拠: 列ヘッダーのクリック処理はNSOutlineViewが自動的に処理
        // Java Swingでは、JXTreeTableが自動的にヘッダークリックを処理するため、
        // カスタムのMouseListenerは不要（HOROS-20240407準拠）

        // HOROS-20240407準拠: BrowserController.m 14738行目
        // ウィンドウが閉じられる時にカラム状態とソート状態を保存
        // 注意: 既にaddWindowListenerが呼ばれている場合は、重複を避ける
        // HOROS-20240407準拠: windowWillClose:で保存（14733-14738行目）
    }

    /**
     * データベースアウトラインのカラム状態を復元
     * HOROS-20240407準拠: BrowserController.m 14316行目
     * [databaseOutline restoreColumnState: [[NSUserDefaults standardUserDefaults]
     * objectForKey: @"databaseColumns2"]]
     */
    private void restoreDatabaseColumnState() {
        if (databaseOutline == null) {
            return;
        }

        try {
            Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);
            String columnStateJson = prefs.get("databaseColumns2", null);

            if (columnStateJson != null && !columnStateJson.isEmpty()) {
                // JSON文字列をパースしてList<Map<String, Object>>に変換
                // 簡易実装: JSONライブラリを使用せず、カンマ区切りで保存
                // より堅牢な実装にはJSONライブラリ（Jackson、Gsonなど）を使用することを推奨
                List<Map<String, Object>> state = parseColumnState(columnStateJson);
                if (state != null && !state.isEmpty()) {
                    databaseOutline.restoreColumnState(state);
                }
            }
        } catch (Exception e) {
            // エラーが発生した場合はデフォルトのカラム幅を使用
            // // // logger.error("Failed to restore column state", e);
        }
    }

    /**
     * データベースアウトラインのカラム状態を保存
     * HOROS-20240407準拠: BrowserController.m 14738行目
     * [[NSUserDefaults standardUserDefaults] setObject:[databaseOutline
     * columnState] forKey: @"databaseColumns2"]
     */
    private void saveDatabaseColumnState() {
        if (databaseOutline == null) {
            return;
        }

        try {
            List<Map<String, Object>> state = databaseOutline.getColumnState();
            if (state != null && !state.isEmpty()) {
                // List<Map<String, Object>>をJSON文字列に変換
                // 簡易実装: JSONライブラリを使用せず、カンマ区切りで保存
                // より堅牢な実装にはJSONライブラリ（Jackson、Gsonなど）を使用することを推奨
                String columnStateJson = serializeColumnState(state);
                Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);
                prefs.put("databaseColumns2", columnStateJson);
                prefs.flush();
            }
        } catch (Exception e) {
            // エラーが発生した場合は保存をスキップ
            // // // logger.error("Failed to save column state", e);
        }
    }

    /**
     * カラム状態をシリアライズ（簡易実装）
     * 形式: "Identifier1:Width1:Index1,Identifier2:Width2:Index2,..."
     * HOROS-20240407準拠: 列の位置（Index）も保存する必要がある
     */
    private String serializeColumnState(List<Map<String, Object>> state) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < state.size(); i++) {
            Map<String, Object> columnInfo = state.get(i);
            String identifier = (String) columnInfo.get("Identifier");
            Object widthObj = columnInfo.get("Width");
            Object indexObj = columnInfo.get("Index");
            int width = widthObj instanceof Number ? ((Number) widthObj).intValue() : 0;
            int index = indexObj instanceof Number ? ((Number) indexObj).intValue() : -1;

            if (identifier != null) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(identifier).append(":").append(width).append(":").append(index);
            }
        }
        return sb.toString();
    }

    /**
     * カラム状態をパース（簡易実装）
     * 形式: "Identifier1:Width1:Index1,Identifier2:Width2:Index2,..."
     * HOROS-20240407準拠: 列の位置（Index）も復元する必要がある
     * 後方互換性: 古い形式（"Identifier1:Width1"）もサポート
     */
    private List<Map<String, Object>> parseColumnState(String json) {
        List<Map<String, Object>> state = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return state;
        }

        String[] parts = json.split(",");
        for (String part : parts) {
            String[] keyValue = part.split(":");
            if (keyValue.length >= 2) {
                try {
                    Map<String, Object> columnInfo = new HashMap<>();
                    columnInfo.put("Identifier", keyValue[0]);
                    columnInfo.put("Width", Integer.parseInt(keyValue[1]));
                    // Indexが存在する場合は復元、存在しない場合は-1（後方互換性）
                    if (keyValue.length >= 3) {
                        columnInfo.put("Index", Integer.parseInt(keyValue[2]));
                    } else {
                        columnInfo.put("Index", -1); // 古い形式の場合は-1
                    }
                    state.add(columnInfo);
                } catch (NumberFormatException e) {
                    // 無効な数値はスキップ
                }
            }
        }
        return state;
    }

    /**
     * アルバムテーブルのデータを更新
     */
    private void updateAlbumTable() {
        if (albumTable == null || database == null) {

            return;
        }

        // 無限ループ防止
        if (isUpdatingAlbumTable)
            return;
        isUpdatingAlbumTable = true;

        try {
            javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) albumTable.getModel();
            model.setRowCount(0);

            List<com.oculus.jj.model.DicomAlbum> albums = getAlbumArray();

            for (com.oculus.jj.model.DicomAlbum album : albums) {
                Object[] row = {
                        album.getName() != null ? album.getName() : "",
                        album.getNumberOfStudies()
                };
                model.addRow(row);

            }

            // HOROS-20240407準拠: 最初のアルバム（"Database"）を自動選択
            if (!albums.isEmpty()) {
                // フラグにより選択変更イベントが発火してもrefreshDatabaseがスキップされる
                albumTable.setRowSelectionInterval(0, 0);

                // HOROS-20240407準拠: 選択されたアルバムに基づいてアウトラインビューを更新
                // ただし、無限ループを防ぐため直接呼び出す
                if (!isRefreshingOutline) {

                    SwingUtilities.invokeLater(() -> {
                        outlineViewRefresh();
                    });
                } else {
                    System.out
                            .println("[DEBUG] updateAlbumTable() - skipping outlineViewRefresh() (already refreshing)");
                }
            } else {

            }
        } finally {
            isUpdatingAlbumTable = false;
        }
    }

    /**
     * HOROS-20240407準拠: + (BrowserController*) currentBrowser (521行目)
     */
    public static BrowserController getCurrentBrowser() {
        return browserWindow;
    }

    /**
     * データベースを取得
     * HOROS-20240407準拠: - (DicomDatabase*)database (BrowserController.h)
     */
    public DicomDatabase getDatabase() {
        return database;
    }

    /**
     * ファイルをデータベースにコピー
     * HOROS-20240407準拠: - (void) copyFilesIntoDatabaseIfNeeded: (NSMutableArray*)
     * filesInput options: (NSDictionary*) options (2191行目)
     */
    public void copyFilesIntoDatabaseIfNeeded(List<String> filesInput, Map<String, Object> options) {
        if (database == null || !database.isLocal()) {
            return;
        }
        if (filesInput == null || filesInput.isEmpty()) {
            return;
        }

        // HOROS-20240407準拠: NSMutableArray *newFilesToCopyList = [NSMutableArray
        // arrayWithCapacity: [filesInput count]]; (2208行目)
        List<String> newFilesToCopyList = new ArrayList<>();
        String inPath = database.getDataDirPath();

        // HOROS-20240407準拠: for( NSString *file in filesInput) (2212行目)
        for (String file : filesInput) {
            // HOROS-20240407準拠: if( [[file commonPrefixWithString: INpath options:
            // NSLiteralSearch] isEqualToString:INpath] == NO) (2214行目)
            if (!file.startsWith(inPath)) {
                newFilesToCopyList.add(file);
            }
        }

        int copyMode = com.oculus.jj.config.AppPreferences.getInt("database.copyMode", 2);
        boolean copyFiles = !newFilesToCopyList.isEmpty() && shouldCopyImportedFiles(newFilesToCopyList, inPath, copyMode);

        // HOROS-20240407準拠: if( [[options objectForKey: @"async"] boolValue]) (2282行目)
        boolean async = options != null && Boolean.TRUE.equals(options.get("async"));

        if (async) {
            // HOROS-20240407準拠: NSMutableDictionary *dict = [NSMutableDictionary
            // dictionaryWithObjectsAndKeys: filesInput, @"filesInput", ...]; (2284行目)
            Map<String, Object> dict = new HashMap<>();
            if (options != null) {
                dict.putAll(options);
            }
            dict.put("filesInput", filesInput); // HOROS-20240407準拠: filesInputをそのまま使用（newFilesToCopyListではなく）
            dict.put("copyFiles", copyFiles);
            dict.put("onlyDICOM", com.oculus.jj.config.AppPreferences.getBool("database.dicomOnly", true));

            // HOROS-20240407準拠: NSThread *t = [[[NSThread alloc]
            // initWithTarget:_database.independentDatabase
            // selector:@selector(copyFilesThread:) object: dict] autorelease]; (2289行目)
            // HOROS-20240407準拠: BrowserController.m 2288-2291行目
            // メインスレッドでない場合はindependentDatabaseを使用、メインスレッドの場合はdatabaseを使用
            Thread thread = new Thread(() -> {
                database.copyFilesThread(dict);
            });

            // HOROS-20240407準拠: t.name = NSLocalizedString( @"Copying and indexing
            // files...", nil); (2294行目)
            if (copyFiles) {
                if (options != null && Boolean.TRUE.equals(options.get("mountedVolume"))) {
                    thread.setName("Copying and indexing files from CD/DVD...");
                } else {
                    thread.setName("Copying and indexing files...");
                }
            } else {
                if (options != null && Boolean.TRUE.equals(options.get("mountedVolume"))) {
                    thread.setName("Indexing files from CD/DVD...");
                } else {
                    thread.setName("Indexing files...");
                }
            }

            // HOROS-20240407準拠: t.status = N2LocalizedSingularPluralCount( [filesInput
            // count], NSLocalizedString(@"file", nil), NSLocalizedString(@"files", nil));
            // (2295行目)
            // HOROS-20240407準拠: t.supportsCancel = YES; (2296行目)
            // HOROS-20240407準拠: [[ThreadsManager defaultManager] addThreadAndStart: t];
            // (2297行目)
            ThreadsManager.defaultManager().addThreadAndStart(thread);
        } else {
            // HOROS-20240407準拠: Wait *splash = [[Wait alloc] initWithString:
            // NSLocalizedString(@"Copying into Database...", nil)]; (2301行目)
            // 同期処理の場合は、Waitスプラッシュを表示してから処理を実行
            // 現在は非同期処理のみをサポート
            // 同期処理が必要な場合は、async=falseを設定して呼び出す
        }
    }

    private boolean shouldCopyImportedFiles(List<String> files, String databasePath, int copyMode) {
        if (copyMode == 0) return true;
        if (copyMode == 2) {
            final int[] answer = {JOptionPane.NO_OPTION};
            Runnable prompt = () -> answer[0] = JOptionPane.showConfirmDialog(this,
                    LanguageManager.get("prefs.db.copy_ask_prompt", "Copy imported files into the database folder?"),
                    LanguageManager.get("prefs.db.filemgmt", "File Management"), JOptionPane.YES_NO_OPTION);
            if (SwingUtilities.isEventDispatchThread()) prompt.run();
            else {
                try { SwingUtilities.invokeAndWait(prompt); }
                catch (Exception ex) { return false; }
            }
            return answer[0] == JOptionPane.YES_OPTION;
        }

        try {
            java.nio.file.FileStore databaseStore = java.nio.file.Files.getFileStore(java.nio.file.Path.of(databasePath));
            for (String file : files) {
                java.nio.file.FileStore sourceStore = java.nio.file.Files.getFileStore(java.nio.file.Path.of(file));
                if (!databaseStore.equals(sourceStore)) return true;
            }
            return false;
        } catch (Exception ex) {
            return true;
        }
    }

    // HOROS-20240407準拠: NSArray *outlineViewArray, *originalOutlineViewArray;
    // (124行目)
    private List<Object> outlineViewArray = new ArrayList<>();
    /** HOROS-20240407準拠: int timeIntervalType (NSPopUpButton tag, default=0=None) */
    private int timeIntervalType = 0;

    /**
     * アウトラインビューを更新
     * HOROS-20240407準拠: - (void)outlineViewRefresh (BrowserController.m)
     */
    /**
     * アウトラインビューを更新
     * HOROS-20240407準拠: - (void)outlineViewRefresh (BrowserController.m)
     */
    public void outlineViewRefresh() {
        // 無限ループ防止
        if (isRefreshingOutline)
            return;
        isRefreshingOutline = true;

        synchronized (outlineViewArray) {
            try {
                if (database == null || albumTable == null) {
                    return;
                }

                List<com.oculus.jj.model.DicomAlbum> albumArray = getAlbumArray();
                int selectedRow = albumTable.getSelectedRow();

                outlineViewArray.clear();

                if (selectedRow >= 0 && selectedRow < albumArray.size()) {
                    com.oculus.jj.model.DicomAlbum selectedAlbum = albumArray.get(selectedRow);

                    if (selectedAlbum != null) {
                        // HOROS-20240407準拠: "Database"アルバムの場合は全スタディを表示
                        if ("Database".equals(selectedAlbum.getName())) {
                            // HOROS-20240407準拠: Databaseアルバムは全スタディを含む
                            try {
                                List<com.oculus.jj.model.DicomStudy> allStudies = database.getAllStudies();
                                if (allStudies != null && !allStudies.isEmpty()) {
                                    outlineViewArray.addAll(allStudies);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else if (Boolean.TRUE.equals(selectedAlbum.isSmartAlbum())) {
                            List<com.oculus.jj.model.DicomStudy> matching = selectedAlbum.evaluate(database.getAllStudies());
                            if (matching != null) outlineViewArray.addAll(matching);
                        } else if (selectedAlbum.getStudies() != null) {
                            // HOROS-20240407準拠: 通常のアルバムの場合はアルバムのスタディを表示
                            outlineViewArray.addAll(selectedAlbum.getStudies());
                        }
                    }
                }

            // HOROS-20240407準拠: 検索キーワードと検索項目によるフィルタリング
            synchronized (outlineViewArray) {
                String searchText = (searchField != null) ? searchField.getText().trim() : "";
                if (!searchText.isEmpty()) {
                    int selectedSearchType = 0;
                    if (browserToolbar != null && browserToolbar.getSearchTypeCombo() != null) {
                        selectedSearchType = browserToolbar.getSearchTypeCombo().getSelectedIndex();
                    }
                    String finalSearchText = searchText.toLowerCase();
                    List<Object> filteredList = new java.util.ArrayList<>();
                    java.util.function.BiFunction<String, String, Boolean> contains = (val, query) -> 
                        val != null && val.toLowerCase().contains(query);

                    for (Object item : outlineViewArray) {
                        if (item instanceof com.oculus.jj.model.DicomStudy) {
                            com.oculus.jj.model.DicomStudy study = (com.oculus.jj.model.DicomStudy) item;
                            boolean match = false;
                            switch (selectedSearchType) {
                                case 0: // All Fields
                                    match = contains.apply(study.getPatientName(), finalSearchText)
                                         || contains.apply(study.getPatientID(), finalSearchText)
                                         || contains.apply(study.getId(), finalSearchText)
                                         || contains.apply(study.getComment(), finalSearchText)
                                         || contains.apply(study.getComment2(), finalSearchText)
                                         || contains.apply(study.getComment3(), finalSearchText)
                                         || contains.apply(study.getComment4(), finalSearchText)
                                         || contains.apply(study.getStudyName(), finalSearchText)
                                         || contains.apply(study.getModality(), finalSearchText)
                                         || contains.apply(study.getAccessionNumber(), finalSearchText)
                                         || contains.apply(study.getPerformingPhysician(), finalSearchText)
                                         || contains.apply(study.getReferringPhysician(), finalSearchText)
                                         || contains.apply(study.getInstitutionName(), finalSearchText);
                                    break;
                                case 1: // Patient Name
                                    match = contains.apply(study.getPatientName(), finalSearchText);
                                    break;
                                case 2: // Study Date
                                    if (study.getDate() != null) {
                                        match = study.getDate().toString().contains(finalSearchText);
                                    }
                                    break;
                                case 3: // Accession Number
                                    match = contains.apply(study.getAccessionNumber(), finalSearchText);
                                    break;
                                case 4: // Modality
                                    match = contains.apply(study.getModality(), finalSearchText);
                                    break;
                                case 5: // Series Description (Study Name + Series Description)
                                    match = contains.apply(study.getStudyName(), finalSearchText);
                                    if (!match && study.getSeries() != null) {
                                        for (Object sObj : study.getSeries()) {
                                            if (sObj instanceof com.oculus.jj.model.DicomSeries) {
                                                com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) sObj;
                                                if (contains.apply(series.getSeriesDescription(), finalSearchText)) {
                                                    match = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    break;
                                case 6: // Institution
                                    match = contains.apply(study.getInstitutionName(), finalSearchText);
                                    break;
                                case 7: // Other (Comments)
                                    match = contains.apply(study.getComment(), finalSearchText)
                                         || contains.apply(study.getComment2(), finalSearchText)
                                         || contains.apply(study.getComment3(), finalSearchText)
                                         || contains.apply(study.getComment4(), finalSearchText);
                                    break;
                            }
                            if (match) {
                                filteredList.add(study);
                            }
                        } else {
                            filteredList.add(item);
                        }
                    }
                    outlineViewArray.clear();
                    outlineViewArray.addAll(filteredList);
                }
            }

            // HOROS-20240407準拠: Modality Filter (15261行目辺り NSPredicate modality CONTAINS)
            if (browserToolbar != null && browserToolbar.getModalityCombo() != null) {
                String selectedModality = (String) browserToolbar.getModalityCombo().getSelectedItem();
                // index 0 = All modalities (localized label, do not string-compare)
                if (selectedModality != null && !"".equals(selectedModality) && browserToolbar.getModalityCombo().getSelectedIndex() != 0) {
                    final String modCode = selectedModality;
                    synchronized (outlineViewArray) {
                        java.util.List<Object> modalityFiltered = new java.util.ArrayList<>();
                        for (Object item : outlineViewArray) {
                            if (item instanceof com.oculus.jj.model.DicomStudy 
                                && ((com.oculus.jj.model.DicomStudy) item).getModality() != null 
                                && ((com.oculus.jj.model.DicomStudy) item).getModality().contains(modCode)) {
                                modalityFiltered.add(item);
                            } else if (!(item instanceof com.oculus.jj.model.DicomStudy)) {
                                modalityFiltered.add(item); // DicomSeries etc. はそのまま
                            }
                        }
                        outlineViewArray.clear();
                        outlineViewArray.addAll(modalityFiltered);
                    }
                }
            }


            // HOROS-20240407準拠: Time Interval Filter (outlineViewRefresh equivalent)
            if (timeIntervalType != 0) {
                long nowMs = System.currentTimeMillis();
                long startMs = Long.MIN_VALUE;
                switch (timeIntervalType) {
                    case 1: startMs = nowMs - 3600_000L; break;               // 1 hour (index 1)
                    case 2: startMs = nowMs - 21_600_000L; break;            // 6 hours (index 2)
                    case 3: startMs = nowMs - 43_200_000L; break;            // 12 hours (index 3)
                    case 4: startMs = nowMs - 86_400_000L; break;            // 24 hours (index 4)
                    case 5: startMs = nowMs - 172_800_000L; break;           // 48 hours (index 5)
                    case 6: {                                                   // Today (start of day, index 6)
                        java.time.LocalDateTime now = java.time.LocalDateTime.now();
                        startMs = now.toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                        break; }
                    case 7: startMs = nowMs - 604_800_000L; break;           // 7 days (One Week, index 7)
                    case 8: startMs = nowMs - 2_592_000_000L; break;         // 30 days (One Month, index 8)
                }

                synchronized (outlineViewArray) {
                    java.util.List<Object> timeFiltered = new java.util.ArrayList<>();
                    for (Object item : outlineViewArray) {
                        if (!(item instanceof com.oculus.jj.model.DicomStudy)) {
                            timeFiltered.add(item);
                        } else {
                            com.oculus.jj.model.DicomStudy study = (com.oculus.jj.model.DicomStudy) item;
                            if (study.getDate() != null) {
                                java.time.LocalDateTime studyDate = study.getDate();
                                long studyMs = studyDate.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                                if (studyMs >= startMs) {
                                    timeFiltered.add(item);
                                }
                            }
                        }
                    }
                    outlineViewArray.clear();
                    outlineViewArray.addAll(timeFiltered);
                }
            }

            // HOROS-20240407準拠: ソート処理 (BrowserController.m 3193-3265行目)
            // HOROS-20240407準拠: NSSortDescriptor * sortdate = [[[NSSortDescriptor alloc]
            // initWithKey: @"date" ascending:NO] autorelease];
            // HOROS-20240407準拠: デフォルトはname（昇順、caseInsensitiveCompare）とdate（降順）
            synchronized (outlineViewArray) {
                if (!outlineViewArray.isEmpty()) {
                    // HOROS-20240407準拠: ソート記述子を決定
                    String primarySortColumn = sortColumn;
                    boolean primaryAscending = sortAscending;

                    // HOROS-20240407準拠: ソート列が未設定または空の場合はデフォルト（name昇順、date降順）
                    if (primarySortColumn == null || primarySortColumn.isEmpty()) {
                        primarySortColumn = "name";
                        primaryAscending = true;
                    }

                    // HOROS-20240407準拠: ソート実行
                    java.util.Comparator<Object> comparator = createSortComparator(primarySortColumn, primaryAscending);
                    outlineViewArray.sort(comparator);

                    // HOROS-20240407準拠: name以外の列でソートした場合は、dateで二次ソート（降順）
                    if (!"name".equals(primarySortColumn)) {
                        java.util.Comparator<Object> dateComparator = createSortComparator("date", false);
                        outlineViewArray.sort(comparator.thenComparing(dateComparator));
                    } else {
                        // HOROS-20240407準拠: nameでソートした場合は、dateで二次ソート（降順）
                        java.util.Comparator<Object> dateComparator = createSortComparator("date", false);
                        outlineViewArray.sort(comparator.thenComparing(dateComparator));
                    }
                }
            }

            // HOROS-20240407準拠: アウトラインビューを更新
            if (databaseOutline != null) {
                SwingUtilities.invokeLater(() -> {
                    // HOROS-20240407準拠: ソートインジケーターを更新
                    // HOROS-20240407準拠: デフォルトはname昇順（BrowserController.m 3198行目）
                    // HOROS-20240407準拠: 列が非表示（hidden）でない限り、可視領域外でもソートインジケーターを表示
                    String currentSortCol = (sortColumn != null && !sortColumn.isEmpty()) ? sortColumn : "name";
                    boolean currentSortAsc = (sortColumn != null && !sortColumn.isEmpty()) ? sortAscending : true;
                    // HOROS-20240407準拠: 列が非表示（hidden）でない限り、常にソートインジケーターを表示
                    if (databaseOutline.isColumnWithIdentifierVisible(currentSortCol)) {
                        databaseOutline.updateSortIndicator(currentSortCol, currentSortAsc);
                    }

                    // HOROS-20240407準拠: [databaseOutline reloadData]
                    if (databaseOutline.getTreeTableModel() != null) {
                        org.jdesktop.swingx.treetable.TreeTableModel model = databaseOutline.getTreeTableModel();
                        if (model instanceof DatabaseOutlineView.DatabaseOutlineTreeTableModel) {
                            DatabaseOutlineView.DatabaseOutlineTreeTableModel treeTableModel = (DatabaseOutlineView.DatabaseOutlineTreeTableModel) model;
                            // HOROS-20240407準拠: TreeModelSupportを使って構造変更を通知

                            treeTableModel.fireTreeStructureChanged();

                        }
                    }

                    databaseOutline.revalidate();
                    databaseOutline.repaint();

                });
            }
        } finally {
            isRefreshingOutline = false;
        }
      }
    }

    /**
     * マトリックスを更新
     * HOROS-20240407準拠: - (void)refreshMatrix: (id) sender (5232行目)
     * 完全実装：前回アイテムをクリアして強制的にマトリックスを更新
     */
    public void refreshMatrix(Object sender) {
        // HOROS-20240407準拠: [previousItem release]; previousItem = nil;
        previousItem = null; // これによりマトリックスの更新が強制される

        boolean firstResponderMatrix = false;

        // HOROS-20240407準拠: if( [[self window] firstResponder] == oMatrix && ...)
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == oMatrix && focusOwner != searchField) {
            // HOROS-20240407準拠: [[self window] makeFirstResponder: databaseOutline];
            databaseOutline.requestFocus();
            firstResponderMatrix = true;
        }

        // HOROS-20240407準拠: [[NSNotificationCenter defaultCenter] postNotificationName:
        // NSOutlineViewSelectionDidChangeNotification ...]
        // アウトライン選択変更通知を送信（これによりoutlineViewSelectionDidChangeが呼ばれる）
        outlineViewSelectionDidChange();

        // HOROS-20240407準拠: [imageView display];
        if (imageView != null) {
            imageView.repaint();
        }

        // HOROS-20240407準拠: if( firstResponderMatrix && ...)
        if (firstResponderMatrix && focusOwner != searchField) {
            oMatrix.requestFocus();
        }
    }

    /**
     * アウトライン選択変更通知
     * HOROS-20240407準拠: - (void)outlineViewSelectionDidChange:(NSNotification
     * *)aNotification (4973行目)
     * 完全実装：選択変更時のマトリックス更新、プレビュー更新、比較スタディの検索
     */
    public void outlineViewSelectionDidChange() {

        // HOROS-20240407準拠: if( [NSThread isMainThread] == NO)
        if (!SwingUtilities.isEventDispatchThread()) {

            SwingUtilities.invokeLater(this::outlineViewSelectionDidChange);
            return;
        }

        // HOROS-20240407準拠: if( loadingIsOver == NO) return;

        if (!loadingIsOver) {

            return;
        }

        try {
            // HOROS-20240407準拠: キャッシュをクリア
            // cachedFilesForDatabaseOutlineSelectionSelectedFiles = nil;

            Object item = databaseOutline.getSelectedItem();

            boolean refreshMatrix = true;
            int nowFiles = 0;
            if (item != null) {
                nowFiles = getItemFileCount(item);
            }

            // HOROS-20240407準拠: if( item == previousItem || ...)
            if (item == previousItem || (previousItem != null && item != null && previousItem.equals(item))) {
                if (nowFiles == previousNoOfFiles) {
                    refreshMatrix = false;
                }
            } else {
                DatabaseIsEdited = false;
            }

            previousNoOfFiles = nowFiles;

            if (refreshMatrix && item != null) {
                // HOROS-20240407準拠: データベースロック
                synchronized (database) {
                    // HOROS-20240407準拠: DicomStudyが選択された場合、Seriesを遅延読み込み
                    if (item instanceof com.oculus.jj.model.DicomStudy) {
                        com.oculus.jj.model.DicomStudy study = (com.oculus.jj.model.DicomStudy) item;
                        database.loadSeriesForStudyIfNeeded(study);
                    } else if (item instanceof com.oculus.jj.model.DicomSeries) {
                        // HOROS-20240407準拠: DicomSeriesが選択された場合、Imageを遅延読み込み
                        com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) item;
                        database.loadImagesForSeriesIfNeeded(series);
                    }

                    if (animationSlider != null) {
                        animationSlider.setEnabled(false);
                        animationSlider.setMaximum(0);
                        animationSlider.setValue(0);
                    }

                    // HOROS-20240407準拠: BrowserController.m 9403行目 - setDCMDone = NO;
                    // 新しいスタディが選択されたときにプレビューを更新するために、setDCMDoneをfalseにリセット
                    setDCMDone = false;

                    // HOROS-20240407準拠: matrixViewArray = [[self childrenArray: item] retain];
                    // HOROS-20240407準拠: childrenArray:item は childrenArray:item onlyImages:YES
                    // を呼び出す
                    matrixViewArray = new ArrayList<>(childrenArray(item, true));

                    // HOROS-20240407準拠: BrowserController.m 5058-5074行目
                    // item == previousItem でない場合、最初のセルを選択
                    if (item != previousItem && (previousItem == null || !item.equals(previousItem))) {
                        // HOROS-20240407準拠: [oMatrix selectCellWithTag: 0]; (5074行目)
                        if (oMatrix != null) {
                            oMatrix.selectCellWithTag(0);
                        }
                    }

                    // HOROS-20240407準拠: [self matrixInit: matrixViewArray.count];
                    matrixInit(matrixViewArray.size());

                    // HOROS-20240407準拠: files = [self imagesArray: item
                    // preferredObject:oFirstForFirst]; (5078行目)
                    List<Object> files = imagesArray(item, oFirstForFirst);

                    // HOROS-20240407準拠: サムネイル配列を初期化 (5081-5083行目)
                    synchronized (this) {
                        // HOROS-20240407準拠: 既存のスレッドとの競合を防ぐため、新しいリストを作成
                        // これにより、古い読み込みスレッドが旧リストの参照(context)と比較して更新を停止する
                        previewPixThumbnails = Collections.synchronizedList(new ArrayList<>());
                        for (int i = 0; i < files.size(); i++) {
                            previewPixThumbnails.add(notFoundImage);
                        }
                        // HOROS-20240407準拠:
                        // previewPixも同様に初期化（HOROS-20240407では明示的に初期化されていないが、matrixLoadIconsで使用される）
                        previewPix = Collections.synchronizedList(new ArrayList<>());
                        for (int i = 0; i < files.size(); i++) {
                            previewPix.add(null);
                        }
                    }

                    // HOROS-20240407準拠: アイコン読み込みスレッドを開始
                    // HOROS-20240407準拠: BrowserController.m 5105-5158行目
                    startMatrixLoadIconsThread(files, item instanceof com.oculus.jj.model.DicomImage);
                }
            }

            if (previousItem != item) {
                previousItem = item;

                // HOROS-20240407準拠: BrowserController.m 6739-6756行目
                // previousItemが設定された後、DBリストの背景色を更新するため、databaseOutlineを再描画
                // HOROS-20240407準拠: willDisplayCellでpreviousItemと比較するため、
                // previousItemが設定されていれば自動的に背景色が変更される
                SwingUtilities.invokeLater(() -> {
                    if (databaseOutline != null) {
                        databaseOutline.repaint();
                    }
                });

                // HOROS-20240407準拠: COMPARATIVE STUDIES
                Object studySelected = getStudyFromItem(item);
                if (studySelected != null) {
                    String patientUID = getPatientUID(studySelected);
                    if (patientUID != null && !patientUID.equals(comparativePatientUID)) {
                        comparativePatientUID = patientUID;
                        comparativeStudies = null;
                        if (comparativeTable != null) {
                            // HOROS-20240407準拠: comparativeTableをクリア
                            SwingUtilities.invokeLater(() -> {
                                javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) comparativeTable
                                        .getModel();
                                model.setRowCount(0);
                            });
                        }

                        // HOROS-20240407準拠: [NSThread detachNewThreadSelector:
                        // @selector(searchForComparativeStudies:) ...]

                        Thread thread = new Thread(() -> searchForComparativeStudies(studySelected));
                        thread.setName("Search for comparative studies");
                        ThreadsManager.defaultManager().addThreadAndStart(thread);
                    } else if (comparativeStudies != null && !comparativeStudies.isEmpty()) {
                        // HOROS-20240407準拠: BrowserController.m 5184-5192行目
                        // 履歴パネル（comparativeTable）の行選択を同期
                        String studyInstanceUID = getStudyInstanceUID(studySelected);
                        if (studyInstanceUID != null) {
                            // HOROS-20240407準拠: comparativeStudiesから同じstudyInstanceUIDを持つスタディのインデックスを検索
                            int index = findStudyIndexInComparativeStudies(studyInstanceUID);
                            if (index >= 0 && comparativeTable != null) {
                                // HOROS-20240407準拠: [comparativeTable selectRowIndexes: [NSIndexSet
                                // indexSetWithIndex: index] byExtendingSelection: NO];
                                SwingUtilities.invokeLater(() -> {
                                    if (index < comparativeTable.getRowCount()) {
                                        comparativeTable.setRowSelectionInterval(index, index);
                                        comparativeTable
                                                .scrollRectToVisible(comparativeTable.getCellRect(index, 0, true));
                                    }
                                });
                            } else if (comparativeTable != null) {
                                // HOROS-20240407準拠: 見つからない場合は最初の行を選択
                                SwingUtilities.invokeLater(() -> {
                                    if (comparativeTable.getRowCount() > 0) {
                                        comparativeTable.setRowSelectionInterval(0, 0);
                                    }
                                });
                            }
                        }
                    }
                } else {
                    // HOROS-20240407準拠: BrowserController.m 5202-5216行目
                    // アイテムが選択されていない場合は履歴パネルをクリア
                    comparativePatientUID = null;
                    comparativeStudies = null;
                    if (comparativeTable != null) {
                        SwingUtilities.invokeLater(() -> {
                            // HOROS-20240407準拠: [comparativeTable reloadData];
                            javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) comparativeTable
                                    .getModel();
                            model.setRowCount(0);
                        });
                    }
                }
            }

            resetROIsAndKeysButton();

        } catch (Exception e) {
            // HOROS-20240407準拠: @catch (NSException * e) { N2LogExceptionWithStackTrace(e);
            // }
            // e.printStackTrace(); // デバッグ用（コメントアウト）
        }
    }

    /**
     * データベースアウトラインを選択
     * HOROS-20240407準拠: - (void) selectDatabaseOutline (5227行目)
     */
    public void selectDatabaseOutline() {
        databaseOutline.requestFocus();
    }

    /**
     * データベースが押されたときの処理
     * HOROS-20240407準拠: - (IBAction)databasePressed: (id) sender
     * (BrowserController.m 7899-7902行目)
     * HOROS-20240407準拠: [self resetROIsAndKeysButton];
     */
    public void databasePressed(DatabaseOutlineView sender) {

        // HOROS-20240407準拠: BrowserController.m 7901行目
        resetROIsAndKeysButton();

        // HOROS-20240407準拠: NSOutlineViewSelectionDidChangeNotificationが通知として送信される
        // Java Swingでは、選択変更時にoutlineViewSelectionDidChangeを呼び出す必要がある
        outlineViewSelectionDidChange();
    }

    /**
     * データベースがダブルクリックされたときの処理
     * HOROS-20240407準拠: - (IBAction)databaseDoublePressed: (id) sender
     * (BrowserController.m)
     */
    public void databaseDoublePressed(DatabaseOutlineView sender) {
        // HOROS-20240407準拠: BrowserController.m 7906行目
        // if( [sender clickedRow] != -1)
        int clickedRow = sender.getSelectedRow();
        if (clickedRow != -1) {
            // HOROS-20240407準拠: BrowserController.m 7908-7912行目
            // id item;
            // if ([databaseOutline clickedRow] != -1)
            // item = [databaseOutline itemAtRow:[databaseOutline clickedRow]];
            // else
            // item = [databaseOutline itemAtRow:[databaseOutline selectedRow]];
            Object item = sender.getSelectedItem();

            if (item != null) {
                // HOROS-20240407準拠: BrowserController.m 7914行目
                // if ([[item numberOfImages] intValue] != 0)
                int numberOfImages = getItemFileCount(item);
                if (numberOfImages != 0) {
                    // HOROS-20240407準拠: BrowserController.m 7916-7930行目
                    // OSIRIX_LIGHTのチェックはスキップ（Java実装では不要）
                    // if( [item isDistant])
                    // {
                    // // Check to see if already in retrieving mode, if not download it
                    // [self retrieveComparativeStudy: study select: YES open: NO];
                    // }
                    // else
                    // {
                    // [self databaseOpenStudy: item];
                    // }
                    databaseOpenStudy(item);
                } else {
                    // HOROS-20240407準拠: BrowserController.m 7933-7937行目
                    // #ifndef OSIRIX_LIGHT
                    // [self querySelectedStudy:self];
                    // #endif
                }
            }
        }
    }

    /**
     * アウトラインビューの配列を取得
     * HOROS-20240407準拠: - (NSArray*)outlineViewArray (BrowserController.m)
     */
    public List<Object> getOutlineViewArray() {
        // HOROS-20240407準拠: outlineViewArrayを返す
        synchronized (outlineViewArray) {
            return new ArrayList<>(outlineViewArray);
        }
    }

    /**
     * プレビューのスクロールホイール処理
     * HOROS-20240407準拠: - (void)previewScrollWheel: (float) deltaY
     * (BrowserController.m)
     */
    public void previewScrollWheel(int wheelRotation) {
        if (wheelRotation == 0)
            return;

        // HOROS-20240407準拠: スクロールホイールの反転設定を確認
        boolean reverseScrollWheel = java.util.prefs.Preferences.userRoot().node("com.oculus.jj")
                .getBoolean("Scroll Wheel Reversed", false);
        int change = reverseScrollWheel ? -1 : 1;
        change *= wheelRotation;

        if (animationSlider != null && animationSlider.isEnabled()) {
            int maxValue = animationSlider.getMaximum();
            if (maxValue > 0) {
                int pos = animationSlider.getValue() + change;

                if (pos > maxValue)
                    pos = 0;
                if (pos < 0)
                    pos = maxValue;

                animationSlider.setValue(pos);
                // previewSliderActionはChangeListenerによって自動的に呼び出される
            }
        }
    }

    /**
     * マトリックスを取得
     * HOROS-20240407準拠: - (BrowserMatrix*)oMatrix (BrowserController.h)
     */
    public BrowserMatrix getMatrix() {
        return oMatrix;
    }

    /**
     * 子要素の配列を取得
     * HOROS-20240407準拠: - (NSArray*)childrenArray: (id) parent recursive: (BOOL)
     * recursive (BrowserController.m)
     */
    /**
     * 子要素の配列を取得
     * HOROS-20240407準拠: - (NSArray*) childrenArray: (id) item onlyImages:(BOOL)
     * onlyImages (3620行目)
     * HOROS-20240407準拠: - (NSArray*) childrenArray: (id) item (3693行目) -
     * onlyImages:YESで呼び出す
     * 
     * @param parent     親要素
     * @param onlyImages 画像のみかどうか（true: imageSeries, false: allSeries）
     * @return 子要素のリスト
     */
    public List<Object> childrenArray(Object parent, boolean onlyImages) {
        // HOROS-20240407準拠: if( [item isDeleted] || item == nil) return [NSArray
        // array];
        if (parent == null) {
            return getOutlineViewArray();
        }

        // HOROS-20240407準拠: if ([[item valueForKey:@"type"] isEqualToString:@"Series"])
        if (parent instanceof com.oculus.jj.model.DicomSeries) {
            com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) parent;
            try {
                // HOROS-20240407準拠: データをロード
                if (database != null) {
                    database.loadImagesForSeriesIfNeeded(series);
                }
                // HOROS-20240407準拠: NSArray *sortedArray = [item sortedImages];
                return new ArrayList<>(series.sortedImages());
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        // HOROS-20240407準拠: if ([[item valueForKey:@"type"] isEqualToString:@"Study"])
        if (parent instanceof com.oculus.jj.model.DicomStudy) {
            com.oculus.jj.model.DicomStudy study = (com.oculus.jj.model.DicomStudy) parent;
            try {
                // HOROS-20240407準拠: if( onlyImages) sortedArray = [item
                // valueForKey:@"imageSeries"];
                // HOROS-20240407準拠: else sortedArray = [item valueForKey:@"allSeries"];
                if (onlyImages) {
                    // HOROS-20240407準拠: imageSeriesの実装（画像を含むシリーズのみ）
                    List<com.oculus.jj.model.DicomSeries> imageSeries = study.imageSeries();
                    return new ArrayList<>(imageSeries);
                } else {
                    // HOROS-20240407準拠: allSeriesの実装（すべてのシリーズ）
                    // HOROS-20240407準拠: sortedArray = [item valueForKey:@"allSeries"]; (3662行目)
                    List<com.oculus.jj.model.DicomSeries> allSeries = study.allSeries();

                    // HOROS-20240407準拠: Put the ROI, Comments, Reports, ... at the end of the array
                    // (3664-3677行目)
                    // StructuredReportを最後に移動
                    java.util.List<com.oculus.jj.model.DicomSeries> resortedArray = new java.util.ArrayList<>(
                            allSeries);
                    java.util.List<com.oculus.jj.model.DicomSeries> SRArray = new java.util.ArrayList<>();

                    for (int i = 0; i < resortedArray.size(); i++) {
                        com.oculus.jj.model.DicomSeries s = resortedArray.get(i);
                        // HOROS-20240407準拠: [DCMAbstractSyntaxUID isStructuredReport: [[resortedArray
                        // objectAtIndex: i] valueForKey:@"seriesSOPClassUID"]] (3670行目)
                        if (com.oculus.jj.model.DicomStudy.isStructuredReport(s.getSeriesSOPClassUID())) {
                            SRArray.add(s);
                        }
                    }

                    resortedArray.removeAll(SRArray);
                    resortedArray.addAll(SRArray);

                    return new ArrayList<>(resortedArray);
                }
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }

    /**
     * 画像配列を取得
     * HOROS-20240407準拠: - (NSArray*) imagesArray: (id) item preferredObject: (int)
     * preferredObject onlyImages:(BOOL) onlyImages (3698行目)
     * HOROS-20240407準拠: Studyが選択された場合、各Seriesから1つの画像を選択して返す（全画像を返さない）
     */
    private List<Object> imagesArray(Object item, int preferredObject) {
        return imagesArray(item, preferredObject, true);
    }

    /**
     * 画像配列を取得（onlyImagesパラメータ付き）
     * HOROS-20240407準拠: - (NSArray*) imagesArray: (id) item preferredObject: (int)
     * preferredObject onlyImages:(BOOL) onlyImages (3698行目)
     */
    private List<Object> imagesArray(Object item, int preferredObject, boolean onlyImages) {
        // HOROS-20240407準拠: NSArray *childrenArray = [self childrenArray: item
        // onlyImages:onlyImages]; (3700行目)
        List<Object> childrenArray = childrenArray(item, onlyImages);

        if (childrenArray == null || childrenArray.isEmpty()) {
            return new ArrayList<>();
        }

        List<Object> imagesPathArray = new ArrayList<>();

        try {
            // HOROS-20240407準拠: if ([[item valueForKey:@"type"] isEqualToString:@"Series"])
            // (3710行目)
            if (item instanceof com.oculus.jj.model.DicomSeries) {
                // HOROS-20240407準拠: imagesPathArray = [NSMutableArray arrayWithArray:
                // childrenArray]; (3712行目)
                imagesPathArray.addAll(childrenArray);
            }
            // HOROS-20240407準拠: else if ([[item valueForKey:@"type"]
            // isEqualToString:@"Study"]) (3714行目)
            else if (item instanceof com.oculus.jj.model.DicomStudy) {
                // HOROS-20240407準拠: imagesPathArray = [NSMutableArray arrayWithCapacity:
                // [childrenArray count]]; (3716行目)
                imagesPathArray = new ArrayList<>(childrenArray.size());

                // HOROS-20240407準拠: BOOL first = YES; (3718行目)
                boolean first = true;
                int currentPreferredObject = preferredObject;

                // HOROS-20240407準拠: for( id i in childrenArray) (3720行目)
                for (Object i : childrenArray) {
                    if (database != null && i instanceof com.oculus.jj.model.DicomSeries) {
                        database.loadImagesForSeriesIfNeeded((com.oculus.jj.model.DicomSeries) i);
                    }

                    // HOROS-20240407準拠: int whichObject = preferredObject; (3722行目)
                    int whichObject = currentPreferredObject;

                    // HOROS-20240407準拠: if( preferredObject == oFirstForFirst) (3724行目)
                    if (currentPreferredObject == oFirstForFirst) {
                        // HOROS-20240407準拠: if( first == NO) preferredObject = oAny; (3726行目)
                        if (!first) {
                            currentPreferredObject = oAny;
                            whichObject = oAny;
                        }
                    }

                    first = false;

                    // HOROS-20240407準拠: if( preferredObject != oMiddle) (3731行目)
                    if (whichObject != oMiddle) {
                        // HOROS-20240407準拠: if( [i primitiveValueForKey:@"thumbnail"] == nil) (3733行目)
                        // サムネイルがない場合はoMiddleを使用
                        if (i instanceof com.oculus.jj.model.DicomSeries) {
                            com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) i;
                            // HOROS-20240407準拠: primitiveValueForKey:@"thumbnail"の実装
                            // Seriesのサムネイルが存在しない場合はoMiddleを使用
                            byte[] thumbnail = series.getThumbnail();
                            if (thumbnail == null || thumbnail.length == 0) {
                                whichObject = oMiddle;
                            }
                        }
                    }

                    // HOROS-20240407準拠: switch( whichObject) (3737行目)
                    switch (whichObject) {
                        case oAny:
                            // HOROS-20240407準拠: NSManagedObject *obj = [[i valueForKey:@"images"]
                            // anyObject]; (3741行目)
                            if (i instanceof com.oculus.jj.model.DicomSeries) {
                                com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) i;
                                // HOROS-20240407準拠: anyObjectは任意の1つの画像を返すが、
                                // 順序を保証するためにsortedImages()を使用
                                List<com.oculus.jj.model.DicomImage> sortedImages = series.sortedImages();
                                if (sortedImages != null && !sortedImages.isEmpty()) {
                                    // HOROS-20240407準拠: anyObject（任意の1つの画像を選択）
                                    // sortedImagesの最初の画像を選択（順序が保証される）
                                    com.oculus.jj.model.DicomImage selectedImage = sortedImages.get(0);
                                    // デバッグ: 選択された画像が正しいSeriesに属しているか確認
                                    if (selectedImage.getSeries() != series) {

                                    }
                                    imagesPathArray.add(selectedImage);
                                }
                            }
                            break;

                        case oMiddle:
                            // HOROS-20240407準拠: NSArray *seriesArray = [self childrenArray: i
                            // onlyImages:onlyImages]; (3748行目)
                            if (i instanceof com.oculus.jj.model.DicomSeries) {
                                com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) i;
                                List<Object> seriesArray = childrenArray(series, onlyImages);

                                // HOROS-20240407準拠: Get the middle image of the series (3750行目)
                                if (seriesArray != null && !seriesArray.isEmpty()) {
                                    int index;
                                    if (seriesArray.size() > 1) {
                                        // HOROS-20240407準拠: [seriesArray objectAtIndex: -1 + [seriesArray count]/2]
                                        // (3754行目)
                                        index = -1 + seriesArray.size() / 2;
                                    } else {
                                        // HOROS-20240407準拠: [seriesArray objectAtIndex: [seriesArray count]/2] (3756行目)
                                        index = seriesArray.size() / 2;
                                    }
                                    if (index >= 0 && index < seriesArray.size()) {
                                        Object selectedImage = seriesArray.get(index);
                                        // デバッグ: 選択された画像が正しいSeriesに属しているか確認
                                        if (selectedImage instanceof com.oculus.jj.model.DicomImage) {
                                            com.oculus.jj.model.DicomImage img = (com.oculus.jj.model.DicomImage) selectedImage;
                                            if (img.getSeries() != series) {

                                            }
                                        }
                                        imagesPathArray.add(selectedImage);
                                    }
                                }
                            }
                            break;

                        case oFirstForFirst:
                            // HOROS-20240407準拠: NSArray *seriesArray = [self childrenArray: i
                            // onlyImages:onlyImages]; (3764行目)
                            if (i instanceof com.oculus.jj.model.DicomSeries) {
                                com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) i;
                                List<Object> seriesArray = childrenArray(series, onlyImages);

                                // HOROS-20240407準拠: Get the first image of the series (3767行目)
                                if (seriesArray != null && !seriesArray.isEmpty()) {
                                    // HOROS-20240407準拠: [seriesArray objectAtIndex: 0] (3768行目)
                                    Object selectedImage = seriesArray.get(0);
                                    // デバッグ: 選択された画像が正しいSeriesに属しているか確認
                                    if (selectedImage instanceof com.oculus.jj.model.DicomImage) {
                                        com.oculus.jj.model.DicomImage img = (com.oculus.jj.model.DicomImage) selectedImage;
                                        if (img.getSeries() != series) {

                                        }
                                    }
                                    imagesPathArray.add(selectedImage);
                                }
                            }
                            break;
                    }
                }
            }
        } catch (Exception e) {
            // HOROS-20240407準拠: @catch (NSException *e) { N2LogExceptionWithStackTrace(e);
            // } (3776行目)
            e.printStackTrace();
        }

        // HOROS-20240407準拠: return imagesPathArray; (3783行目)
        return imagesPathArray;
    }

    // HOROS-20240407準拠: BrowserController.h 255行目 IBOutlet NSTableView*
    // _activityTableView;
    private javax.swing.JTable activityTableView;

    /**
     * Activityテーブルビューを取得
     * HOROS-20240407準拠: - (NSTableView*)_activityTableView
     * (BrowserController+Activity.mm 82行目)
     */
    public javax.swing.JTable getActivityTableView() {
        return activityTableView;
    }

    // HOROS-20240407準拠: ソート関連のフィールド
    // HOROS-20240407準拠: ソート状態は保存/復元される（NSUserDefaults相当）
    private String sortColumn = null;
    private boolean sortAscending = true;

    /**
     * ソート列を取得
     * HOROS-20240407準拠: - (NSString*)sortColumn (BrowserController.m)
     */
    public String getSortColumn() {
        return sortColumn;
    }

    /**
     * ソートが昇順かどうかを取得
     * HOROS-20240407準拠: ソート状態の取得
     */
    public boolean isSortAscending() {
        return sortAscending;
    }

    /**
     * ソート順を取得
     * HOROS-20240407準拠: - (BOOL)sortAscending (BrowserController.m)
     */
    public boolean getSortAscending() {
        return sortAscending;
    }

    /**
     * ソート列を設定
     * HOROS-20240407準拠: - (void)setSortColumn: (NSString*) column ascending: (BOOL)
     * ascending (BrowserController.m)
     * HOROS-20240407準拠: outlineView:sortDescriptorsDidChange: (6665行目) -
     * ソート変更時にoutlineViewRefreshを呼び出す
     */
    public void setSortColumn(String column, boolean ascending) {
        this.sortColumn = column;
        this.sortAscending = ascending;

        // HOROS-20240407準拠: ソート状態の保存はwindowWillClose:で行う（14733-14736行目）
        // setSortColumn内では保存しない（HOROS-20240407準拠）

        // HOROS-20240407準拠: ソートインジケーターを更新
        if (databaseOutline != null) {
            SwingUtilities.invokeLater(() -> {
                databaseOutline.updateSortIndicator(column, ascending);
            });
        }

        // HOROS-20240407準拠: ソート変更時にoutlineViewRefreshを呼び出す
        outlineViewRefresh();

        // HOROS-20240407準拠: name以外の列でソートした場合は最初の行を選択 (6669-6670行目)
        if (column != null && !"name".equals(column) && databaseOutline != null) {
            SwingUtilities.invokeLater(() -> {
                if (databaseOutline.getRowCount() > 0) {
                    databaseOutline.setRowSelectionInterval(0, 0);
                    databaseOutline.scrollRowToVisible(0);
                }
            });
        }
    }

    /**
     * ソート状態を保存
     * HOROS-20240407準拠: BrowserController.m 14733-14736行目
     * HOROS-20240407準拠: if( [[databaseOutline sortDescriptors] count] >= 1)
     * HOROS-20240407準拠: NSDictionary *sort = [NSDictionary
     * dictionaryWithObjectsAndKeys: [NSNumber numberWithBool:[[[databaseOutline
     * sortDescriptors] objectAtIndex: 0] ascending]], @"order", [[[databaseOutline
     * sortDescriptors] objectAtIndex: 0] key], @"key", nil];
     * HOROS-20240407準拠: [[NSUserDefaults standardUserDefaults] setObject:sort
     * forKey: @"databaseSortDescriptor"];
     * HOROS-20240407準拠: NSDictionary形式で保存（keyとorderを含む）
     */
    private void saveSortState() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);
            // HOROS-20240407準拠: if( [[databaseOutline sortDescriptors] count] >= 1)
            // HOROS-20240407準拠: 現在のソート状態（sortColumnとsortAscending）を保存
            if (sortColumn != null && !sortColumn.isEmpty()) {
                // HOROS-20240407準拠: NSDictionary形式で保存（keyとorderを含む）
                // 簡易実装: "key:order"形式で保存（より堅牢な実装にはJSONライブラリを使用）
                String sortDescriptor = sortColumn + ":" + (sortAscending ? "true" : "false");
                prefs.put("databaseSortDescriptor", sortDescriptor);
            } else {
                // ソート状態が未設定の場合は削除
                prefs.remove("databaseSortDescriptor");
            }
            try {
                prefs.flush();
            } catch (java.util.prefs.BackingStoreException e) {
                // エラーが発生した場合は保存をスキップ
            }
        } catch (Exception e) {
            // エラーが発生した場合は保存をスキップ
        }
    }

    /**
     * ソート状態を復元
     * HOROS-20240407準拠: BrowserController.m 14318-14333行目
     * HOROS-20240407準拠: if( [[NSUserDefaults standardUserDefaults]
     * objectForKey: @"databaseSortDescriptor"])
     * HOROS-20240407準拠: NSDictionary形式から復元（keyとorderを含む）
     * HOROS-20240407準拠: [databaseOutline setSortDescriptors:...]でソート状態を設定
     * HOROS-20240407準拠: 列が表示されている場合のみソート状態を復元（14322-14329行目）
     */
    private void restoreSortState() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);
            String sortDescriptor = prefs.get("databaseSortDescriptor", null);
            if (sortDescriptor != null && !sortDescriptor.isEmpty()) {
                // HOROS-20240407準拠: NSDictionary形式から復元（keyとorderを含む）
                // 簡易実装: "key:order"形式から復元（より堅牢な実装にはJSONライブラリを使用）
                String[] parts = sortDescriptor.split(":", 2);
                if (parts.length == 2) {
                    String savedColumn = parts[0];
                    boolean savedAscending = "true".equals(parts[1]);

                    // HOROS-20240407準拠: BrowserController.m 14322-14329行目
                    // if( [databaseOutline isColumnWithIdentifierVisible: [sort
                    // objectForKey:@"key"]])
                    // 列が表示されている場合のみソート状態を復元
                    SwingUtilities.invokeLater(() -> {
                        if (databaseOutline != null && databaseOutline.isColumnWithIdentifierVisible(savedColumn)) {
                            // HOROS-20240407準拠: 列が表示されている場合は、そのソート記述子を設定
                            // HOROS-20240407準拠: setSortColumnを呼び出してソート状態を設定（outlineViewRefreshも呼ばれる）
                            setSortColumn(savedColumn, savedAscending);
                        } else {
                            // HOROS-20240407準拠: 列が表示されていない場合は、デフォルト（name昇順）を設定
                            // HOROS-20240407準拠: [databaseOutline setSortDescriptors:[NSArray
                            // arrayWithObject: [[[NSSortDescriptor alloc] initWithKey:@"name" ascending:YES
                            // selector:@selector(caseInsensitiveCompare:)] autorelease]]];
                            setSortColumn("name", true);
                        }
                    });
                } else {
                    // 無効な形式の場合はデフォルトを設定
                    SwingUtilities.invokeLater(() -> {
                        setSortColumn("name", true);
                    });
                }
            } else {
                // HOROS-20240407準拠: else [databaseOutline setSortDescriptors:[NSArray
                // arrayWithObject: [[[NSSortDescriptor alloc] initWithKey:@"name" ascending:YES
                // selector:@selector(caseInsensitiveCompare:)] autorelease]]];
                // 保存されたソート状態がない場合はデフォルト（name昇順）を設定
                // HOROS-20240407準拠: setSortColumnを呼び出してソート状態を設定（outlineViewRefreshも呼ばれる）
                SwingUtilities.invokeLater(() -> {
                    setSortColumn("name", true);
                });
            }
        } catch (Exception e) {
            // エラーが発生した場合はデフォルトのソート状態を使用
            this.sortColumn = "name";
            this.sortAscending = true;
        }
    }

    /**
     * マトリックスが押されたときの処理
     * HOROS-20240407準拠: - (IBAction)matrixPressed: (id) sender (9298行目)
     * 完全実装：セル選択時の処理、アニメーションスライダーの制御、プレビュー更新
     */
    public void matrixPressed(BrowserMatrix sender) {

        // HOROS-20240407準拠: id theCell = [sender selectedCell];
        javax.swing.JButton selectedCell = sender.selectedCell();

        int index = -1;

        // HOROS-20240407準拠: [self.window makeFirstResponder: oMatrix];
        oMatrix.requestFocus();

        if (selectedCell != null) {
            // HOROS-20240407準拠: if( [theCell tag] >= 0)
            Object tagObj = selectedCell.getClientProperty("tag");
            if (tagObj instanceof Integer) {
                index = (Integer) tagObj;

            } else {

            }
        } else {

        }

        if (index >= 0) {
            // HOROS-20240407準拠: NSManagedObject *dcmFile = [databaseOutline
            // itemAtRow:[databaseOutline selectedRow]];
            Object dcmFile = databaseOutline.getSelectedItem();

            if (dcmFile != null) {
                // HOROS-20240407準拠: BrowserController.m 9329行目
                // if( [[dcmFile valueForKey:@"type"] isEqualToString: @"Series"] && [[[dcmFile
                // valueForKey:@"images"] allObjects] count] > 1)
                String type = getItemType(dcmFile);

                if ("Series".equals(type)) {
                    int imageCount = getItemImageCount(dcmFile);

                    if (imageCount > 1) {
                        // HOROS-20240407準拠: BrowserController.m 9331行目 - [animationSlider setIntValue:
                        // [theCell tag]];
                        if (animationSlider != null) {
                            animationSlider.setValue(index);
                        }
                        // HOROS-20240407準拠: BrowserController.m 9332行目 - [self previewSliderAction:
                        // nil];

                        previewSliderAction(null);
                        return;
                    } else {
                        System.out
                                .println("[DEBUG] matrixPressed() - imageCount <= 1, not calling previewSliderAction");
                    }
                } else if ("Study".equals(type)) {
                    // HOROS-20240407準拠: BrowserController.m 9329行目 - Studyタイプの場合
                    // HOROS-20240407準拠: BrowserController.m 9375行目 - [self initAnimationSlider];
                    // initAnimationSlider内でpreviewSliderActionが呼び出される（BrowserController.m 5033行目）
                    // しかし、Studyタイプの場合、previewSliderActionを直接呼び出す方が確実
                    if (animationSlider != null && index >= 0) {
                        animationSlider.setValue(index);
                    }

                    previewSliderAction(null);
                    return;
                } else {

                }
            }
        }

        // HOROS-20240407準拠: [animationSlider setEnabled:NO];
        if (animationSlider != null) {
            animationSlider.setEnabled(false);
            animationSlider.setMaximum(0);
            animationSlider.setValue(0);
        }

        if (index >= 0) {
            // HOROS-20240407準拠: BrowserController.m 9348行目
            // NSManagedObject *dcmFile = [databaseOutline itemAtRow:[databaseOutline
            // selectedRow]];
            Object dcmFile = databaseOutline.getSelectedItem();

            if (dcmFile != null) {
                String type = getItemType(dcmFile);

                // HOROS-20240407準拠: BrowserController.m 9369-9373行目
                // if( [[dcmFile valueForKey:@"type"] isEqualToString: @"Study"] == NO)
                // {
                // index = [theCell tag];
                // [imageView setIndex: index];
                // }
                if (!"Study".equals(type)) {
                    // HOROS-20240407準拠: BrowserController.m 9369-9373行目
                    // if( [[dcmFile valueForKey:@"type"] isEqualToString: @"Study"] == NO)
                    // {
                    // index = [theCell tag];
                    // [imageView setIndex: index];
                    // }
                    // HOROS-20240407準拠: BrowserController.m 9372行目 - [imageView setIndex: index];
                    // HOROS-20240407準拠: setIndexはdcmPixListから画像を取得するため、
                    // setPixelsでpreviewPixがdcmPixListに設定されている必要がある
                    // HOROS-20240407準拠: matrixDisplayIconsでsetPixelsが呼び出されているため、
                    // ここではsetIndexを呼び出すだけでよい
                    if (imageView != null) {

                        synchronized (previewPixThumbnails) {
                            // HOROS-20240407準拠: previewPixが正しく設定されているか確認
                            if (previewPix == null || previewPix.isEmpty() ||
                                    index >= previewPix.size() || previewPix.get(index) == null) {

                                // previewPixが正しく設定されていない場合、setPixelsを呼び出す
                                List<Object> files = imagesArray(dcmFile, oAny);
                                if (!files.isEmpty()) {
                                    // previewPixを初期化
                                    if (previewPix == null || previewPix.isEmpty()) {
                                        previewPix = Collections.synchronizedList(new ArrayList<>());
                                        for (int j = 0; j < files.size(); j++) {
                                            previewPix.add(null);
                                        }
                                    }
                                    // setPixelsを呼び出してdcmPixListを設定
                                    imageView.setPixels(previewPix, files, null, (short) index, 'i', true);
                                    imageView.setStringID("previewDatabase");
                                }
                            } else {

                            }
                            // HOROS-20240407準拠: BrowserController.m 9372行目 - [imageView setIndex: index];
                            imageView.setIndex(index);
                        }
                    } else {

                    }
                } else {
                    // HOROS-20240407準拠: BrowserController.m 9369-9373行目
                    // if( [[dcmFile valueForKey:@"type"] isEqualToString: @"Study"] == NO)
                    // {
                    // index = [theCell tag];
                    // [imageView setIndex: index];
                    // }
                    // スタディレベルの場合はsetIndexを直接呼び出さない（BrowserController.m 9369行目）
                    // initAnimationSlider内でpreviewSliderActionが呼び出される（BrowserController.m 5033行目）
                    System.out
                            .println("[DEBUG] matrixPressed() - type is Study, will be handled by initAnimationSlider");
                }
                // HOROS-20240407準拠: BrowserController.m 9375行目 - [self initAnimationSlider];
                // initAnimationSlider内でpreviewSliderActionが呼び出される（BrowserController.m 5033行目）
                initAnimationSlider();
            } else {

            }
        } else {

        }

        // HOROS-20240407準拠: [self resetROIsAndKeysButton];
        resetROIsAndKeysButton();
    }

    /**
     * マトリックスがダブルクリックされたときの処理
     * HOROS-20240407準拠: - (IBAction) matrixDoublePressed:(id)sender (9381行目)
     *
     * HOROS-20240407準拠: DCMView.m 4319-4322行目
     * - PreviewView（imageView）でのダブルクリック時にも呼び出される
     * - if( clickCount == 2 && [self window] == [[BrowserController currentBrowser]
     * window])
     * - {
     * - [[BrowserController currentBrowser] matrixDoublePressed:nil];
     * - }
     */
    public void matrixDoublePressed(BrowserMatrix sender) {
        // HOROS-20240407準拠: BrowserController.m 9383行目
        // id theCell = [oMatrix selectedCell];
        // HOROS-20240407準拠: senderがnullの場合（PreviewViewからの呼び出し）はoMatrixから選択されたセルを取得
        javax.swing.JButton theCell = (sender != null) ? sender.selectedCell()
                : (oMatrix != null ? oMatrix.selectedCell() : null);

        // HOROS-20240407準拠: BrowserController.m 9385-9387行目
        // if( [theCell tag] >= 0)
        // {
        // [self viewerDICOM: [[oMatrix menu] itemAtIndex:0]];
        // }
        if (theCell != null) {
            Object tagObj = theCell.getClientProperty("tag");
            if (tagObj instanceof Integer && (Integer) tagObj >= 0) {
                // HOROS-20240407準拠: viewerDICOMを呼び出す（menuのitemAtIndex:0はnullで呼び出す）
                viewerDICOM(null);
            }
        }
    }

    /**
     * アルバムを追加
     * HOROS-20240407準拠: - (void)addAlbum: (id) sender (BrowserController.m)
     */
    public void addAlbum(Object sender) {
        DicomDatabase db = DicomDatabase.activeLocalDatabase();
        if (db == null) {
            infoFmt(LanguageManager.get("db.album.no_db", "No active database."),
                    LanguageManager.get("db.album.add.title", "Add Album"));
            return;
        }
        String name = JOptionPane.showInputDialog(this,
                LanguageManager.get("db.album.name", "Album name"), "New Album");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        db.addAlbum(new com.oculus.jj.model.DicomAlbum(name.trim()));
        db.save();
        refreshAlbums();
        infoFmt(name.trim(), LanguageManager.get("db.album.add.done", "Album added"));
    }

    /**
     * スマートアルバムを追加
     * HOROS-20240407準拠: - (void)addSmartAlbum: (id) sender (BrowserController.m)
     */
    public void addSmartAlbum(Object sender) {
        DicomDatabase db = DicomDatabase.activeLocalDatabase();
        if (db == null) {
            infoFmt(LanguageManager.get("db.album.no_db", "No active database."),
                    LanguageManager.get("db.album.smart.title", "Add Smart Album"));
            return;
        }
        String name = JOptionPane.showInputDialog(this,
                LanguageManager.get("db.album.name", "Album name"), "Smart Album");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        String predicate = JOptionPane.showInputDialog(this,
                LanguageManager.get("db.album.predicate", "Filter predicate"), "modality == 'CT'");
        if (predicate == null) predicate = "";
        db.addAlbum(new com.oculus.jj.model.DicomAlbum(name.trim(), predicate));
        db.save();
        refreshAlbums();
        infoFmt(name.trim(), LanguageManager.get("db.album.smart.done", "Smart album added"));
    }

    public void addSelectedStudiesToAlbum(Object sender) {
        DicomDatabase db = DicomDatabase.activeLocalDatabase();
        List<Object> selected = databaseOutline != null ? databaseOutline.getSelectedItems()
                : java.util.Collections.emptyList();
        if (db == null || selected.isEmpty()) {
            infoFmt(LanguageManager.get("db.album.add_studies.none", "Select one or more studies or series first."),
                    LanguageManager.get("db.album.add_studies.title", "Add Studies to Album"));
            return;
        }
        List<com.oculus.jj.model.DicomAlbum> candidates = new ArrayList<>();
        for (com.oculus.jj.model.DicomAlbum album : db.getAlbums()) {
            if (album != null && !Boolean.TRUE.equals(album.isSmartAlbum())
                    && album.getName() != null && !"Database".equals(album.getName())) {
                candidates.add(album);
            }
        }
        if (candidates.isEmpty()) {
            infoFmt(LanguageManager.get("db.album.add_studies.no_albums", "Create a regular album first."),
                    LanguageManager.get("db.album.add_studies.title", "Add Studies to Album"));
            return;
        }
        Object target = JOptionPane.showInputDialog(this,
                LanguageManager.get("db.album.add_studies.select", "Choose an album:"),
                LanguageManager.get("db.album.add_studies.title", "Add Studies to Album"),
                JOptionPane.QUESTION_MESSAGE, null, candidates.toArray(), candidates.get(0));
        if (!(target instanceof com.oculus.jj.model.DicomAlbum)) return;
        try {
            db.addStudies(selected, target);
            refreshAlbums();
            infoFmt(String.valueOf(selected.size()),
                    LanguageManager.get("db.album.add_studies.done", "Selected items added to album."));
        } catch (Exception ex) {
            infoFmt(String.valueOf(ex.getMessage()),
                    LanguageManager.get("db.album.add_studies.fail", "Failed to add studies to album."));
        }
    }

    /**
     * アルバムを削除
     * HOROS-20240407準拠: - (void)removeAlbum: (id) album (BrowserController.m)
     */
    public void removeAlbum(Object album) {
        DicomDatabase db = DicomDatabase.activeLocalDatabase();
        if (db == null || album == null) {
            infoFmt(LanguageManager.get("db.album.no_db", "No active database."),
                    LanguageManager.get("db.album.remove.title", "Delete Album"));
            return;
        }
        com.oculus.jj.model.DicomAlbum target = (album instanceof com.oculus.jj.model.DicomAlbum)
                ? (com.oculus.jj.model.DicomAlbum) album : null;
        if (target == null) {
            infoFmt(LanguageManager.get("db.album.remove.invalid", "Invalid album reference"),
                    LanguageManager.get("db.album.remove.title", "Delete Album"));
            return;
        }
        if (db.getAlbums().contains(target)) {
            db.removeAlbum(target);
            db.save();
            refreshAlbums();
            infoFmt(target.getName(), LanguageManager.get("db.album.remove.done", "Album deleted"));
        }
    }

    // ========== スレッド関連メソッド ==========
    // HOROS-20240407準拠: BrowserController.m のスレッド関連メソッドを復元

    /**
     * ファイルコピースレッド
     * HOROS-20240407準拠: - (void) copyFilesThread: (NSDictionary*) dict (2105行目)
     * データベースに委譲
     */
    public void copyFilesThread(Map<String, Object> dict) {
        if (database != null) {
            // HOROS-20240407準拠: [self.database performSelector:@selector(copyFilesThread:)
            // withObject:dict];
            database.copyFilesThread(dict);
        }
    }

    /**
     * 自動コメント再生成スレッド
     * HOROS-20240407準拠: - (void) regenerateAutoCommentsThread: (NSDictionary*)
     * arrays (1108行目)
     */
    public void regenerateAutoCommentsThread(Map<String, Object> arrays) {
        Thread thread = new Thread(() -> {
            try {
                // HOROS-20240407準拠: NSManagedObjectContext *context =
                // self.database.independentContext;

                @SuppressWarnings("unchecked")
                List<Object> studiesArray = (List<Object>) arrays.get("studyArrayIDs");
                @SuppressWarnings("unchecked")
                List<Object> seriesArray = (List<Object>) arrays.get("seriesArrayIDs");

                // HOROS-20240407準拠: NSString *commentField = [[NSUserDefaults
                // standardUserDefaults] stringForKey: @"commentFieldForAutoFill"];
                String commentField = "comment";

                // HOROS-20240407準拠: BOOL studyLevel = [[NSUserDefaults standardUserDefaults]
                // boolForKey: @"COMMENTSAUTOFILLStudyLevel"];
                boolean studyLevel = false;

                // HOROS-20240407準拠: BOOL seriesLevel = [[NSUserDefaults standardUserDefaults]
                // boolForKey: @"COMMENTSAUTOFILLSeriesLevel"];
                boolean seriesLevel = false;

                // HOROS-20240407準拠: BOOL commentsAutoFill = [[NSUserDefaults
                // standardUserDefaults] boolForKey: @"COMMENTSAUTOFILL"];
                boolean commentsAutoFill = false;

                if (studiesArray != null) {
                    int x = 0;
                    for (Object studyID : studiesArray) {
                        // HOROS-20240407準拠: DicomStudy *s = (DicomStudy*) [context objectWithID:
                        // studyID];

                        // HOROS-20240407準拠: float p = (float) (x++) / (float) studiesArray.count;
                        double progress = (double) (x++) / studiesArray.size();
                        ThreadsManager.defaultManager().setThreadProgress(Thread.currentThread(), progress);

                        // HOROS-20240407準拠: if( [[NSThread currentThread] isCancelled])
                        if (ThreadsManager.defaultManager().isCancelled(Thread.currentThread())) {
                            break;
                        }
                    }
                }

                if (seriesArray != null) {
                    int i = 0;
                    for (Object seriesID : seriesArray) {
                        // HOROS-20240407準拠: DicomSeries *series = (DicomSeries*) [context objectWithID:
                        // seriesID];

                        // HOROS-20240407準拠: float p = (float) (i++) / (float) seriesArray.count;
                        double progress = (double) (i++) / seriesArray.size();
                        ThreadsManager.defaultManager().setThreadProgress(Thread.currentThread(), progress);

                        // HOROS-20240407準拠: if( [[NSThread currentThread] isCancelled])
                        if (ThreadsManager.defaultManager().isCancelled(Thread.currentThread())) {
                            break;
                        }
                    }
                }

                // HOROS-20240407準拠: [self performSelectorOnMainThread: @selector(
                // outlineViewRefresh) withObject: nil waitUntilDone: NO];
                SwingUtilities.invokeLater(() -> outlineViewRefresh());

            } catch (Exception e) {
                // HOROS-20240407準拠: @catch (NSException * e) { N2LogExceptionWithStackTrace(e);
                // }
                // e.printStackTrace(); // デバッグ用（コメントアウト）
            }
        });

        thread.setName("Regenerating auto comments...");
        ThreadsManager.defaultManager().addThreadAndStart(thread);
    }

    /**
     * 自動コメント再生成
     * HOROS-20240407準拠: - (IBAction) regenerateAutoComments:(id) sender (1264行目)
     */
    public void regenerateAutoComments(Object sender) {
        // HOROS-20240407準拠: NSRunInformationalAlertPanel

        // 選択されたスタディとシリーズを取得
        List<Object> studiesArray = new ArrayList<>();
        List<Object> seriesArray = new ArrayList<>();

        Map<String, Object> arrays = new HashMap<>();
        arrays.put("studyArrayIDs", studiesArray);
        arrays.put("seriesArrayIDs", seriesArray);

        regenerateAutoCommentsThread(arrays);
    }

    /**
     * 非同期WADOダウンロード
     * HOROS-20240407準拠: - (void) asyncWADODownload:(NSString*) filename (824行目)
     */
    public void asyncWADODownload(String filename) {
        Thread thread = new Thread(() -> {
            try {
                List<java.net.URL> urlToDownloads = new ArrayList<>();

                // HOROS-20240407準拠: NSArray *urlsR = [[NSString
                // stringWithContentsOfFile:filename usedEncoding:NULL error:NULL]
                // componentsSeparatedByString: @"\r"];
                // HOROS-20240407準拠: NSArray *urlsN = [[NSString
                // stringWithContentsOfFile:filename usedEncoding:NULL error:NULL]
                // componentsSeparatedByString: @"\n"];
                java.nio.file.Path path = java.nio.file.Paths.get(filename);
                String content = new String(java.nio.file.Files.readAllBytes(path));

                String[] urlsR = content.split("\r");
                String[] urlsN = content.split("\n");

                String[] urls = urlsR.length >= urlsN.length ? urlsR : urlsN;

                for (String url : urls) {
                    if (url != null && !url.trim().isEmpty()) {
                        try {
                            urlToDownloads.add(new java.net.URL(url.trim()));
                        } catch (java.net.MalformedURLException e) {
                            // 無効なURLはスキップ
                        }
                    }
                }

                // HOROS-20240407準拠: WADODownload *downloader = [[[WADODownload alloc] init]
                // autorelease];

                // HOROS-20240407準拠: [[NSFileManager defaultManager] removeItemAtPath: filename
                // error: nil];
                java.nio.file.Files.deleteIfExists(path);

            } catch (Exception e) {
                // HOROS-20240407準拠: @catch (NSException * e) { N2LogExceptionWithStackTrace(e);
                // }
                // e.printStackTrace(); // デバッグ用（コメントアウト）
            }
        });

        thread.setName("WADO Download");
        ThreadsManager.defaultManager().addThreadAndStart(thread);
    }

    /**
     * 画像処理スレッド（vImage相当）
     * HOROS-20240407準拠: - (void) vImageThread: (NSDictionary*) d (582行目)
     * 注意: vImageはmacOS固有のため、Javaでは別の実装が必要
     */
    public void vImageThread(Map<String, Object> d) {
        // HOROS-20240407準拠: vImage_Buffer src = *(vImage_Buffer*) [[d objectForKey:
        // @"src"] pointerValue];
        // HOROS-20240407準拠: vImage_Buffer dst = *(vImage_Buffer*) [[d objectForKey:
        // @"dst"] pointerValue];

        String what = (String) d.get("what");

        if ("FTo16U".equals(what)) {
            // HOROS-20240407準拠: vImageConvert_FTo16U(&src, &dst, offset, scale,
            // kvImageDoNotTile);
        } else if ("16UToF".equals(what)) {
            // HOROS-20240407準拠: vImageConvert_16UToF(&src, &dst, offset, scale,
            // kvImageDoNotTile);
        } else {
            // HOROS-20240407準拠: 不明なvImageThreadの場合は何もしない
            // //
            // デバッグ用（コメントアウト）
        }
    }

    /**
     * マルチスレッド画像変換
     * HOROS-20240407準拠: + (void) multiThreadedImageConvert: (NSString*) what
     * :(vImage_Buffer*) src :(vImage_Buffer *) dst :(float) offset :(float) scale
     * (625行目)
     */
    public static void multiThreadedImageConvert(String what, Object src, Object dst, float offset, float scale) {
        // HOROS-20240407準拠: int mpprocessors = [[NSProcessInfo processInfo]
        // processorCount];
        int mpprocessors = Runtime.getRuntime().availableProcessors();

        // HOROS-20240407準拠: static NSConditionLock *threadLock = nil;

        // HOROS-20240407準拠: for( int i = 0; i < mpprocessors; i++)
        // HOROS-20240407準拠: [NSThread detachNewThreadSelector: @selector(vImageThread:)
        // toTarget: browserWindow withObject: d];
    }

    // ========== ヘルパーメソッド ==========
    // HOROS-20240407準拠: UI関連のヘルパーメソッド

    /**
     * アイテムのタイプを取得
     */
    private String getItemType(Object item) {
        if (item == null)
            return null;
        if (item instanceof com.oculus.jj.model.DicomStudy)
            return "Study";
        if (item instanceof com.oculus.jj.model.DicomSeries)
            return "Series";
        if (item instanceof com.oculus.jj.model.DicomImage)
            return "Image";
        return null;
    }

    /**
     * アイテムのファイル数を取得
     */
    private int getItemFileCount(Object item) {
        if (item instanceof com.oculus.jj.model.DicomStudy) {
            return ((com.oculus.jj.model.DicomStudy) item).getNumberOfImages() != null
                    ? ((com.oculus.jj.model.DicomStudy) item).getNumberOfImages()
                    : 0;
        }
        if (item instanceof com.oculus.jj.model.DicomSeries) {
            return ((com.oculus.jj.model.DicomSeries) item).getNumberOfImages() != null
                    ? ((com.oculus.jj.model.DicomSeries) item).getNumberOfImages()
                    : 0;
        }
        return 0;
    }

    /**
     * アイテムの画像数を取得
     */
    private int getItemImageCount(Object item) {
        if (item instanceof com.oculus.jj.model.DicomSeries) {
            return ((com.oculus.jj.model.DicomSeries) item).getImages() != null
                    ? ((com.oculus.jj.model.DicomSeries) item).getImages().size()
                    : 0;
        }
        return 0;
    }

    /**
     * アイテムからスタディを取得
     */
    private Object getStudyFromItem(Object item) {
        if (item instanceof com.oculus.jj.model.DicomStudy) {
            return item;
        }
        if (item instanceof com.oculus.jj.model.DicomSeries) {
            return ((com.oculus.jj.model.DicomSeries) item).getStudy();
        }
        if (item instanceof com.oculus.jj.model.DicomImage) {
            return ((com.oculus.jj.model.DicomImage) item).getSeries() != null
                    ? ((com.oculus.jj.model.DicomImage) item).getSeries().getStudy()
                    : null;
        }
        return null;
    }

    /**
     * 患者UIDを取得
     */
    private String getPatientUID(Object study) {
        if (study instanceof com.oculus.jj.model.DicomStudy) {
            return ((com.oculus.jj.model.DicomStudy) study).getPatientUID();
        }
        return null;
    }

    /**
     * スタディのstudyInstanceUIDを取得
     * HOROS-20240407準拠: BrowserController.m 5184-5192行目
     */
    private String getStudyInstanceUID(Object study) {
        if (study instanceof com.oculus.jj.model.DicomStudy) {
            return ((com.oculus.jj.model.DicomStudy) study).getStudyInstanceUID();
        }
        return null;
    }

    /**
     * comparativePatientUIDを取得
     * HOROS-20240407準拠: BrowserController.m 497行目 - @synthesize
     * comparativePatientUID
     * DatabaseOutlineViewのセルレンダラーから使用される
     */
    public String getComparativePatientUID() {
        return comparativePatientUID;
    }

    /**
     * 選択された行の患者UIDを取得
     * HOROS-20240407準拠: BrowserController.m 6739-6756行目
     * willDisplayCellでpreviousItemの患者UIDと比較するため
     */
    public String getSelectedPatientUID() {
        if (previousItem == null) {
            return null;
        }
        Object study = getStudyFromItem(previousItem);
        if (study != null) {
            return getPatientUID(study);
        }
        return null;
    }

    /**
     * previousItemを取得
     * HOROS-20240407準拠: BrowserController.m 6745行目 - [[previousItem
     * valueForKey: @"type"] isEqualToString:@"Study"]
     * willDisplayCellでpreviousItemと比較するため
     */
    public Object getPreviousItem() {
        return previousItem;
    }

    /**
     * comparativeStudiesから同じstudyInstanceUIDを持つスタディのインデックスを検索
     * HOROS-20240407準拠: BrowserController.m 5186行目
     * HOROS-20240407準拠: NSUInteger index = [[self.comparativeStudies
     * valueForKey: @"studyInstanceUID"] indexOfObject: [studySelected
     * valueForKey: @"studyInstanceUID"]];
     */
    private int findStudyIndexInComparativeStudies(String studyInstanceUID) {
        if (comparativeStudies == null || studyInstanceUID == null) {
            return -1;
        }

        for (int i = 0; i < comparativeStudies.size(); i++) {
            Object study = comparativeStudies.get(i);
            String uid = getStudyInstanceUID(study);
            if (studyInstanceUID.equals(uid)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * ソート用Comparatorを作成
     * HOROS-20240407準拠: NSSortDescriptorの動作を再現
     * HOROS-20240407準拠: BrowserController.m 3193-3199行目
     */
    private java.util.Comparator<Object> createSortComparator(String column, boolean ascending) {
        return (o1, o2) -> {
            if (!(o1 instanceof com.oculus.jj.model.DicomStudy) ||
                    !(o2 instanceof com.oculus.jj.model.DicomStudy)) {
                return 0;
            }

            com.oculus.jj.model.DicomStudy study1 = (com.oculus.jj.model.DicomStudy) o1;
            com.oculus.jj.model.DicomStudy study2 = (com.oculus.jj.model.DicomStudy) o2;

            int result = 0;

            try {
                switch (column) {
                    case "name":
                        // HOROS-20240407準拠: caseInsensitiveCompare相当
                        String name1 = study1.getName() != null ? study1.getName() : "";
                        String name2 = study2.getName() != null ? study2.getName() : "";
                        result = name1.compareToIgnoreCase(name2);
                        break;
                    case "date":
                        // HOROS-20240407準拠: date降順がデフォルト
                        java.time.LocalDateTime date1 = study1.getDate();
                        java.time.LocalDateTime date2 = study2.getDate();
                        if (date1 == null && date2 == null) {
                            result = 0;
                        } else if (date1 == null) {
                            result = 1; // nullは後ろに
                        } else if (date2 == null) {
                            result = -1; // nullは後ろに
                        } else {
                            result = date1.compareTo(date2);
                        }
                        break;
                    case "patientID":
                        String pid1 = study1.getPatientID() != null ? study1.getPatientID() : "";
                        String pid2 = study2.getPatientID() != null ? study2.getPatientID() : "";
                        result = pid1.compareToIgnoreCase(pid2);
                        break;
                    case "accessionNumber":
                        String acc1 = study1.getAccessionNumber() != null ? study1.getAccessionNumber() : "";
                        String acc2 = study2.getAccessionNumber() != null ? study2.getAccessionNumber() : "";
                        result = acc1.compareToIgnoreCase(acc2);
                        break;
                    case "studyName":
                        String sn1 = study1.getStudyName() != null ? study1.getStudyName() : "";
                        String sn2 = study2.getStudyName() != null ? study2.getStudyName() : "";
                        result = sn1.compareToIgnoreCase(sn2);
                        break;
                    case "modality":
                        String mod1 = study1.getModality() != null ? study1.getModality() : "";
                        String mod2 = study2.getModality() != null ? study2.getModality() : "";
                        result = mod1.compareToIgnoreCase(mod2);
                        break;
                    case "dateAdded":
                        java.time.LocalDateTime da1 = study1.getDateAdded();
                        java.time.LocalDateTime da2 = study2.getDateAdded();
                        if (da1 == null && da2 == null) {
                            result = 0;
                        } else if (da1 == null) {
                            result = 1;
                        } else if (da2 == null) {
                            result = -1;
                        } else {
                            result = da1.compareTo(da2);
                        }
                        break;
                    case "dateOpened":
                        java.time.LocalDateTime do1 = study1.getDateOpened();
                        java.time.LocalDateTime do2 = study2.getDateOpened();
                        if (do1 == null && do2 == null) {
                            result = 0;
                        } else if (do1 == null) {
                            result = 1;
                        } else if (do2 == null) {
                            result = -1;
                        } else {
                            result = do1.compareTo(do2);
                        }
                        break;
                    default:
                        // その他の列は文字列として比較
                        String val1 = getStudyValueAsString(study1, column);
                        String val2 = getStudyValueAsString(study2, column);
                        result = val1.compareToIgnoreCase(val2);
                        break;
                }
            } catch (Exception e) {
                // エラーが発生した場合は0を返す（順序を変更しない）
                result = 0;
            }

            return ascending ? result : -result;
        };
    }

    /**
     * スタディの値を文字列として取得（ソート用）
     */
    private String getStudyValueAsString(com.oculus.jj.model.DicomStudy study, String column) {
        try {
            switch (column) {
                case "id":
                    return study.getId() != null ? study.getId() : "";
                case "comment":
                    return study.getComment() != null ? study.getComment() : "";
                case "stateText":
                    return study.getStateText() != null ? study.getStateText().toString() : "";
                case "noFiles":
                    return study.getNumberOfImages() != null ? study.getNumberOfImages().toString() : "0";
                case "noSeries":
                    return study.getSeries() != null ? String.valueOf(study.getSeries().size()) : "0";
                case "yearOld":
                    return "";
                case "referringPhysician":
                    return study.getReferringPhysician() != null ? study.getReferringPhysician() : "";
                case "performingPhysician":
                    return study.getPerformingPhysician() != null ? study.getPerformingPhysician() : "";
                case "institutionName":
                    return study.getInstitutionName() != null ? study.getInstitutionName() : "";
                case "dateOfBirth":
                    return study.getDateOfBirth() != null ? study.getDateOfBirth().toString() : "";
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * マトリックス初期化
     * HOROS-20240407準拠: - (void) matrixInit:(long) noOfImages (9391行目)
     */
    private void matrixInit(long noOfImages) {
        // HOROS-20240407準拠: インスタンスを新しくして古いスレッドからの書き込みを防止する
        previewPix = Collections.synchronizedList(new ArrayList<>());
        previewPixThumbnails = Collections.synchronizedList(new ArrayList<>());

        synchronized (this) {
            setDCMDone = false;
            loadPreviewIndex = 0;

            // HOROS-20240407準拠: [self previewMatrixScrollViewFrameDidChange: nil] (9406行目)
            // oMatrixがまだ親に追加されていない可能性があるため、SwingUtilities.invokeLaterで遅延実行
            SwingUtilities.invokeLater(() -> {
                // 初期化が完了していない場合は処理をスキップ
                if (!uiInitialized) {
                    return;
                }
                if (thumbnailsScrollView == null || !thumbnailsScrollView.isDisplayable()) {
                    return;
                }
                if (bottomPreviewSplit == null || !bottomPreviewSplit.isDisplayable()) {
                    return;
                }
                previewMatrixScrollViewFrameDidChange();
            });

            if (oMatrix != null) {
                int rows = oMatrix.getRows();
                int columns = oMatrix.getColumns();
                if (columns < 1)
                    columns = 1;

                for (long i = 0; i < rows * columns; i++) {
                    int row = (int) (i / columns);
                    int col = (int) (i % columns);
                    javax.swing.JButton cell = oMatrix.cellAtRowColumn(row, col);
                    if (cell != null) {
                        cell.putClientProperty("tag", (int) i);
                        // HOROS-20240407準拠: [cell setTransparent:(i>=noOfImages)] (9415行目)
                        cell.setOpaque(i < noOfImages);
                        // HOROS-20240407準拠: [cell setEnabled:NO] (9416行目)
                        cell.setEnabled(false);
                        // HOROS-20240407準拠: [cell setFont:[NSFont systemFontOfSize: [self fontSize:
                        // @"dbMatrixFont"]]] (9417行目)
                        // HOROS-20240407準拠: [cell setImagePosition: NSImageBelow] (9418行目)
                        cell.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
                        cell.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
                        // HOROS-20240407準拠: cell.title = NSLocalizedString(@"loading...", nil) (9419行目)
                        cell.setText(i < noOfImages ? "loading..." : "");
                        cell.setIcon(null);
                        // HOROS-20240407準拠: cell.bezelStyle = NSShadowlessSquareBezelStyle (9421行目)
                        // Java SwingではJButtonのデフォルトスタイルを使用
                    }
                }
            }

            // HOROS-20240407準拠: [imageView setPixels:nil files:nil rois:nil firstImage:0
            // level:0 reset:YES] (9424行目)
            // 初期起動時はプレビュー画面をクリア（真っ黒にする）
            if (imageView != null) {
                imageView.setPixels(null, null, null, (short) 0, (char) 0, true);
            }
        }
    }

    /**
     * サムネイルスクロールビューのフレーム変更通知
     * HOROS-20240407準拠: -
     * (void)previewMatrixScrollViewFrameDidChange:(NSNotification*)note (10331行目)
     */
    private void previewMatrixScrollViewFrameDidChange() {
        // HOROS-20240407準拠: BrowserController.m 10331-10373行目
        // HOROS-20240407準拠: if( matrixViewArray.count == 0) return; (10333行目)
        if (matrixViewArray == null || matrixViewArray.isEmpty()) {
            return;
        }

        // UI初期化が完了していない場合は処理をスキップ
        // componentShownイベントが発生するまで、uiInitializedはfalseのまま
        if (!uiInitialized) {
            return;
        }

        if (oMatrix == null) {
            return;
        }

        // thumbnailsScrollViewが初期化されていない場合は処理をスキップ
        if (thumbnailsScrollView == null) {
            return;
        }

        // bottomPreviewSplitが初期化されていない場合は処理をスキップ
        if (bottomPreviewSplit == null) {
            return;
        }

        // thumbnailsScrollViewから直接JViewportを取得
        // HOROS-20240407準拠: thumbnailsScrollViewから直接JViewportを取得
        // ただし、thumbnailsScrollViewが完全に初期化されていない場合は処理をスキップ
        if (!thumbnailsScrollView.isDisplayable() || !thumbnailsScrollView.isShowing()) {
            return;
        }

        // JViewportを取得（初期化中に失敗する可能性があるため、NullPointerExceptionをキャッチ）
        // JScrollPaneの内部実装で、getViewport()が内部でcomp.parentを参照するため、
        // 初期化中にNullPointerExceptionが発生する可能性がある
        javax.swing.JViewport viewport = null;
        try {
            viewport = thumbnailsScrollView.getViewport();
        } catch (NullPointerException e) {
            // JViewportの内部でcompがnullの場合にエラーが発生する可能性があるため、例外をキャッチ
            // 初期化が完了していない場合は処理をスキップ
            return;
        }
        if (viewport == null) {
            return;
        }

        // HOROS-20240407準拠: NSInteger selectedCellTag = [oMatrix.selectedCell tag];
        // (10336行目)
        javax.swing.JButton selectedCell = oMatrix.selectedCell();
        int selectedCellTag = -1;
        if (selectedCell != null) {
            Object tagObj = selectedCell.getClientProperty("tag");
            if (tagObj instanceof Integer) {
                selectedCellTag = (Integer) tagObj;
            }
        }

        // セルサイズと間隔を取得
        java.awt.Dimension cellSize = oMatrix.getCellSize();
        if (cellSize == null) {
            cellSize = new java.awt.Dimension(105, 113); // デフォルトサイズ
        }

        java.awt.Dimension intercellSpacingDim = oMatrix.getIntercellSpacing();
        int intercellSpacing = intercellSpacingDim != null ? intercellSpacingDim.width : 0;
        int rcs = cellSize.width + intercellSpacing;

        if (rcs > 0) {
            // スクロールビューの幅を取得
            // HOROS-20240407準拠: BrowserController.m 10340行目
            // NSSize size = thumbnailsScrollView.bounds.size;
            // thumbnailsScrollViewから直接JViewportを取得（oMatrix.getParent()を呼び出す必要がない）
            if (thumbnailsScrollView != null && viewport != null) {
                // HOROS-20240407準拠: JViewportのサイズを使用
                // スクロールバーは別コンポーネントとしてディバイダーの右側に固定配置されるため、
                // ここではスクロールバーの幅を考慮する必要はない
                java.awt.Dimension size = viewport.getSize();
                if (size == null || size.width <= 0) {
                    return;
                }
                int width = size.width;

                // HOROS-20240407準拠: size.width += oMatrix.intercellSpacing.width; (10341行目)
                // BrowserController.m 10340-10345行目
                width += intercellSpacing;

                // HOROS-20240407準拠: NSInteger hcells = (NSInteger)roundf(size.width/rcs);
                // (10345行目)
                int hcells = Math.round((float) width / rcs);

                if (hcells > 0) {
                    int vcells = (int) Math.ceil((double) matrixViewArray.size() / hcells);

                    if (vcells < 1) {
                        vcells = 1;
                    }

                    if (vcells > 0 && hcells > 0) {
                        oMatrix.renewRows(vcells, hcells);

                        // 追加セルを透明・無効化
                        synchronized (previewPixThumbnails) {
                            int previewPixCount = previewPix != null ? previewPix.size() : 0;
                            for (int i = previewPixCount; i < hcells * vcells; i++) {
                                int row = i / hcells;
                                int col = i % hcells;
                                javax.swing.JButton cell = oMatrix.cellAtRowColumn(row, col);
                                if (cell != null) {
                                    cell.setOpaque(false);
                                    cell.setEnabled(false);
                                }
                            }
                        }

                        // HOROS-20240407準拠: [oMatrix sizeToCells]; (10368行目)
                        // マトリックスのサイズをセルに合わせて調整（引数なし）
                        // NSMatrixのsizeToCellsはセルのサイズと間隔に基づいてマトリックスのサイズを計算する
                        // スクロールバーの幅は自動的に考慮される（MainMenu.xib: matrix幅424px vs scrollView幅384px = 40px差）
                        oMatrix.sizeToCells();

                        // マトリックスのサイズを取得
                        java.awt.Dimension matrixSize = oMatrix.getPreferredSize();
                        java.awt.Dimension viewportSize = viewport.getSize();
                        if (viewportSize == null) {
                            return;
                        }

                        // マトリックスの高さとビューポートの高さを比較して、スクロールが必要かどうかを判定
                        // HOROS-20240407準拠: 垂直スクロールバーは常に表示されるが、必要に応じて有効/無効化される
                        if (thumbnailsVerticalScrollBar != null && !isAdjustingThumbnailsScrollBar) {
                            boolean needsScroll = matrixSize.height > viewportSize.height;
                            thumbnailsVerticalScrollBar.setEnabled(needsScroll);

                            // スクロールバーが有効な場合、スクロール範囲を設定
                            if (needsScroll) {
                                thumbnailsVerticalScrollBar.setMinimum(0);
                                thumbnailsVerticalScrollBar.setMaximum((int) matrixSize.height);
                                thumbnailsVerticalScrollBar.setVisibleAmount((int) viewportSize.height);
                                // 現在のビューポート位置を取得して設定
                                if (viewport instanceof javax.swing.JViewport) {
                                    java.awt.Point viewPosition = ((javax.swing.JViewport) viewport).getViewPosition();
                                    isAdjustingThumbnailsScrollBar = true;
                                    try {
                                        thumbnailsVerticalScrollBar.setValue(viewPosition.y);
                                    } finally {
                                        isAdjustingThumbnailsScrollBar = false;
                                    }
                                }
                            } else {
                                isAdjustingThumbnailsScrollBar = true;
                                try {
                                    thumbnailsVerticalScrollBar.setValue(0);
                                } finally {
                                    isAdjustingThumbnailsScrollBar = false;
                                }
                            }
                        }

                        // マトリックスのサイズを強制的に適用
                        oMatrix.revalidate();
                        oMatrix.repaint();

                        // HOROS-20240407準拠: [oMatrix selectCellWithTag:selectedCellTag]; (10369行目)
                        if (selectedCellTag >= 0) {
                            oMatrix.selectCellWithTag(selectedCellTag);
                        }

                        // Java Swing実装上の制約: renewRowsでセルが再生成されると内容が失われるため、
                        // セルの内容を再設定するためにmatrixDisplayIconsを呼び出す必要がある
                        // HOROS-20240407ではNSMatrixのrenewRowsが既存セルを再利用するが、
                        // Java SwingのJButtonでは再生成されるため、この処理が必要
                        // renewRowsでセルが再生成されたため、loadPreviewIndexを0にリセットして
                        // すべてのセルを再表示する
                        synchronized (previewPixThumbnails) {
                            loadPreviewIndex = 0;
                        }
                        SwingUtilities.invokeLater(() -> {
                            matrixDisplayIcons();
                        });
                    }
                }
            }
        }
    }

    /**
     * サムネイルアイコンの更新
     * HOROS-20240407準拠: - (void) matrixNewIcon:(long) index
     * :(NSManagedObject*)curFile (9428行目)
     * 
     * @param index   インデックス
     * @param curFile 現在のファイル（DicomSeriesまたはDicomImage）
     */
    public void matrixNewIcon(long index, Object curFile) {
        // HOROS-20240407準拠: BrowserController.m 9428-9577行目
        long i = index;

        if (curFile == null) {
            if (oMatrix != null) {
                oMatrix.repaint();
            }
            return;
        }

        javax.swing.ImageIcon img = null;
        synchronized (previewPixThumbnails) {
            // HOROS-20240407準拠: if( i >= [previewPix count]) return; (9443行目)
            if (previewPix != null && i >= previewPix.size()) {
                return;
            }
            // HOROS-20240407準拠: if( i >= [previewPixThumbnails count]) return; (9444行目)
            if (i >= previewPixThumbnails.size()) {
                return;
            }

            // HOROS-20240407準拠: img = [[previewPixThumbnails objectAtIndex: i] retain];
            // (9446行目)
            img = previewPixThumbnails.get((int) i);
            // HOROS-20240407準拠: if( img == nil) NSLog( @"Error: [previewPixThumbnails
            // objectAtIndex: i] == nil"); (9447行目)
            // HOROS-20240407準拠: imgがnilでも処理を続行するが、HOROS-20240407ではnotFoundImageが設定されているはず
            // 念のため、imgがnullの場合はnotFoundImageを使用（HOROS-20240407準拠:
            // 5083行目でnotFoundImageが追加されている）
            if (img == null) {
                img = notFoundImage;
            }
        }

        try {
            String modality = null;
            String seriesSOPClassUID = null;
            String fileType = null;
            String name = null;

            // HOROS-20240407準拠: ファイルタイプに応じてmodality等を取得
            if (curFile instanceof com.oculus.jj.model.DicomImage) {
                com.oculus.jj.model.DicomImage image = (com.oculus.jj.model.DicomImage) curFile;
                modality = image.getModality();
                if (image.getSeries() != null) {
                    seriesSOPClassUID = image.getSeries().getSeriesSOPClassUID();
                }
                fileType = image.getFileType();
                // HOROS-20240407準拠: DicomImageにはnameがないため、series nameを使用
                if (image.getSeries() != null) {
                    name = image.getSeries().getName();
                }
            } else if (curFile instanceof com.oculus.jj.model.DicomSeries) {
                com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) curFile;
                seriesSOPClassUID = series.getSeriesSOPClassUID();
                if (series.getImages() != null && !series.getImages().isEmpty()) {
                    // HOROS-20240407準拠: Setから最初の要素を取得
                    java.util.Iterator<com.oculus.jj.model.DicomImage> it = series.getImages().iterator();
                    if (it.hasNext()) {
                        com.oculus.jj.model.DicomImage firstImage = it.next();
                        modality = firstImage.getModality();
                        fileType = firstImage.getFileType();
                    }
                }
                name = series.getName();
            }

            // HOROS-20240407準拠: if( img || [modality hasPrefix: @"RT"]) (9482行目)
            // HOROS-20240407準拠: imgがnilでもRTモダリティの場合は処理を続行
            if (img != null || (modality != null && modality.startsWith("RT"))) {
                if (oMatrix != null) {
                    int rows = oMatrix.getRows();
                    int cols = oMatrix.getColumns();
                    if (cols < 1) {
                        cols = 1;
                    }

                    int row = (int) (i / cols);
                    int col = (int) (i % cols);
                    javax.swing.JButton cell = oMatrix.cellAtRowColumn(row, col);

                    if (cell != null) {
                        // HOROS-20240407準拠: セルの設定
                        // HOROS-20240407準拠: [cell setLineBreakMode: NSLineBreakByCharWrapping];
                        // HOROS-20240407準拠: [cell setFont:[NSFont systemFontOfSize: [self fontSize:
                        // @"dbMatrixFont"]]];
                        // HOROS-20240407準拠: [cell setRepresentedObject: [curFile objectID]];
                        // HOROS-20240407準拠: [cell setImagePosition: NSImageBelow];
                        // HOROS-20240407準拠: [cell setTransparent:NO];
                        // HOROS-20240407準拠: [cell setEnabled:YES];
                        // HOROS-20240407準拠: [cell setButtonType:NSPushOnPushOffButton];
                        // HOROS-20240407準拠: [cell setBezelStyle:NSShadowlessSquareBezelStyle];
                        // HOROS-20240407準拠: [cell setImageScaling:NSImageScaleProportionallyDown];
                        // HOROS-20240407準拠: [cell setBordered:YES]; (9501行目)
                        // HOROS-20240407準拠: [cell setTransparent:NO]; (9493行目)
                        cell.setOpaque(true);
                        cell.setContentAreaFilled(true);
                        cell.setBorderPainted(true);
                        cell.setEnabled(true);

                        // HOROS-20240407準拠: 画像のスケーリング（HOROS-20240407準拠: BrowserController.m
                        // 9594-9608行目）
                        // HOROS-20240407準拠: switch( [[NSUserDefaults standardUserDefaults]
                        // integerForKey: @"dbFontSize"])
                        // case -1: [cell setImage: [img imageByScalingProportionallyUsingNSImage:
                        // 0.6]];
                        // case 0: [cell setImage: img];
                        // case 1: [cell setImage: [img imageByScalingProportionallyUsingNSImage: 1.3]];
                        javax.swing.ImageIcon scaledImg = img;
                        int dbFontSize = 0;
                        if (img != null) {
                            if (dbFontSize == -1) {
                                // HOROS-20240407準拠: 0.6倍にスケーリング
                                scaledImg = scaleImageIcon(img, 0.6);
                            } else if (dbFontSize == 1) {
                                // HOROS-20240407準拠: 1.3倍にスケーリング
                                scaledImg = scaleImageIcon(img, 1.3);
                            }
                        }
                        // HOROS-20240407準拠: imgがnullの場合はnotFoundImageを使用（HOROS-20240407準拠:
                        // 5083行目でnotFoundImageが追加されている）
                        if (scaledImg == null) {
                            scaledImg = notFoundImage;
                        }
                        // HOROS-20240407準拠: セルタイトルの設定（HOROS-20240407準拠: BrowserController.m
                        // 9513-9577行目）

                        // HOROS-20240407準拠: NSString *name = [curFile valueForKey:@"name"]; (9513行目)
                        // HOROS-20240407準拠: if( name == nil) name = @""; (9515-9516行目)
                        if (name == null) {
                            name = "";
                        }

                        // HOROS-20240407準拠: if( name.length > 18) (9518行目)
                        if (name.length() > 18) {
                            // HOROS-20240407準拠: [cell setFont:[NSFont systemFontOfSize: [self fontSize:
                            // @"dbSmallMatrixFont"]]]; (9520行目)
                            // HOROS-20240407準拠: name = [name stringByTruncatingToLength: 36]; // 2 lines
                            // (9521行目)
                            name = name.substring(0, Math.min(36, name.length())); // 2行分
                        }

                        // HOROS-20240407準拠: if( name.length == 0) name = modality; (9524-9525行目)
                        if (name.length() == 0 && modality != null) {
                            name = modality;
                        }

                        String title = "";
                        // HOROS-20240407準拠: if ( [modality hasPrefix: @"RT"]) (9527行目)
                        if (modality != null && modality.startsWith("RT")) {
                            // HOROS-20240407準拠: [cell setTitle: [NSString stringWithFormat: @"%@\r%@",
                            // name, modality]]; (9529行目)
                            // Java Swingでは\nを使用（\rは改行として認識されない）
                            title = name + "\n" + modality;
                        } else if ("DICOMMPEG2".equals(fileType)) {
                            // HOROS-20240407準拠: MPEG-2シリーズ (9531-9535行目)
                            int count = 0;
                            if (curFile instanceof com.oculus.jj.model.DicomSeries) {
                                com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) curFile;
                                count = series.getImages() != null ? series.getImages().size() : 0;
                            }
                            // Java Swingでは\nを使用
                            title = String.format("MPEG-2 Series\n%s\n%d Images", name, count);
                        } else if (curFile instanceof com.oculus.jj.model.DicomSeries) {
                            // HOROS-20240407準拠: Seriesレベルのタイトル (9537-9571行目)
                            com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) curFile;
                            // HOROS-20240407準拠: int count = [[curFile valueForKey:@"noFiles"] intValue];
                            // (9539行目)
                            // HOROS-20240407準拠: noFilesはnumberOfImagesを返す (DicomSeries.m 578-580行目)
                            int count = series.getNumberOfImages() != null ? series.getNumberOfImages() : 0;

                            // HOROS-20240407準拠: シリーズタイプの判定 (9540-9569行目)
                            String singleType = "Image";
                            String pluralType = "Images";

                            if (seriesSOPClassUID != null && (seriesSOPClassUID.contains("StructuredReport")
                                    || seriesSOPClassUID.contains("PDF"))) {
                                // HOROS-20240407準拠: StructuredReportまたはPDFの場合 (9542-9548行目)
                                if (count <= 1 && series.getImages() != null && !series.getImages().isEmpty()) {
                                    java.util.Iterator<com.oculus.jj.model.DicomImage> it = series.getImages()
                                            .iterator();
                                    if (it.hasNext()) {
                                        com.oculus.jj.model.DicomImage firstImage = it.next();
                                        if (firstImage.getNumberOfFrames() != null
                                                && firstImage.getNumberOfFrames() >= 1) {
                                            count = firstImage.getNumberOfFrames();
                                        }
                                    }
                                }
                                singleType = "Page";
                                pluralType = "Pages";
                            } else if (count == 1 && series.getImages() != null && !series.getImages().isEmpty()) {
                                // HOROS-20240407準拠: count == 1 かつ numberOfFrames > 1 の場合 (9550-9555行目)
                                java.util.Iterator<com.oculus.jj.model.DicomImage> it = series.getImages()
                                        .iterator();
                                if (it.hasNext()) {
                                    com.oculus.jj.model.DicomImage firstImage = it.next();
                                    if (firstImage.getNumberOfFrames() != null && firstImage.getNumberOfFrames() > 1) {
                                        count = firstImage.getNumberOfFrames();
                                        singleType = "Frame";
                                        pluralType = "Frames";
                                    }
                                }
                            } else if (count == 0) {
                                // HOROS-20240407準拠: count == 0 の場合 (9556-9564行目)
                                // HOROS-20240407準拠: count = [[curFile valueForKey: @"rawNoFiles"] intValue];
                                // (9558行目)
                                if (series.getImages() != null && !series.getImages().isEmpty()) {
                                    java.util.Iterator<com.oculus.jj.model.DicomImage> it = series.getImages()
                                            .iterator();
                                    if (it.hasNext()) {
                                        com.oculus.jj.model.DicomImage firstImage = it.next();
                                        if (firstImage.getNumberOfFrames() != null
                                                && firstImage.getNumberOfFrames() >= 1) {
                                            count = firstImage.getNumberOfFrames();
                                        }
                                    }
                                }
                                singleType = "Object";
                                pluralType = "Objects";
                            }

                            // HOROS-20240407準拠: [cell setTitle:[NSString stringWithFormat: @"%@\r%@", name,
                            // N2LocalizedSingularPluralCount(count, singleType, pluralType)]]; (9571行目)
                            // HOROS-20240407準拠: \rで改行（2行表示）
                            // HOROS-20240407準拠: N2LocalizedSingularPluralCountは数値をローカライズして表示 (N2Stuff.h
                            // 45行目)
                            // HOROS-20240407準拠: N2LocalizedSingularPluralCount(c, s, p) = [NSString
                            // stringWithFormat:@"%@ %@", [NSNumberFormatter
                            // localizedStringFromNumber:[NSNumber numberWithInteger:(NSInteger)c]
                            // numberStyle:NSNumberFormatterDecimalStyle], (c == 1? s : p)]
                            // Java Swingでは\nを使用（\rは改行として認識されない）
                            String countStr = n2LocalizedSingularPluralCount(count, singleType, pluralType);
                            title = name + "\n" + countStr;
                        } else if (curFile instanceof com.oculus.jj.model.DicomImage) {
                            // HOROS-20240407準拠: Imageレベルのタイトル (9573-9580行目)
                            if (seriesSOPClassUID != null && (seriesSOPClassUID.contains("StructuredReport")
                                    || seriesSOPClassUID.contains("PDF"))) {
                                // HOROS-20240407準拠: [cell setTitle:[NSString
                                // stringWithFormat:NSLocalizedString(@"Page %d", nil), i+1]]; (9576行目)
                                title = String.format("Page %d", (int) i + 1);
                            } else {
                                // HOROS-20240407準拠: [cell setTitle:[NSString
                                // stringWithFormat:NSLocalizedString(@"Image %d", nil), i+1]]; (9580行目)
                                title = String.format("Image %d", (int) i + 1);
                            }
                        }

                        // HOROS-20240407準拠: [cell setFont:[NSFont systemFontOfSize: [self fontSize:
                        // @"dbMatrixFont"]]]; (9488行目)
                        // HOROS-20240407準拠: dbMatrixFont = 8 (Small), 9 (Regular), 13 (Large)
                        // (BrowserController.m 389-475行目)
                        // HOROS-20240407準拠: フォントサイズを設定してテキストが2行表示できるようにする
                        // HOROS-20240407準拠: dbFontSizeは既に定義されている（3624行目）ので、それを使用
                        int fontSize = 10; // HOROS-20240407準拠: Regular mode = 9 → 1つ大きくして10
                        if (dbFontSize == -1) {
                            fontSize = 9; // HOROS-20240407準拠: Small mode = 8 → 1つ大きくして9
                        } else if (dbFontSize == 1) {
                            fontSize = 14; // HOROS-20240407準拠: Large mode = 13 → 1つ大きくして14
                        }
                        // HOROS-20240407準拠: name.length > 18 の場合は dbSmallMatrixFont を使用 (9520行目)
                        if (name != null && name.length() > 18) {
                            fontSize = (dbFontSize == -1) ? 8 : (dbFontSize == 1) ? 9 : 9; // HOROS-20240407準拠:
                                                                                           // dbSmallMatrixFont → 1つ大きく
                        }
                        cell.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, fontSize));

                        // HOROS-20240407準拠: [cell setImagePosition: NSImageBelow]; (9492行目)
                        // HOROS-20240407準拠: 順序: setImagePosition → setTitle → setImage (9492,
                        // 9529/9571, 9601行目)
                        // NSImageBelow = 画像を下に、テキストを上に配置
                        // Java Swingでは、setVerticalTextPosition(TOP)でテキストを上に配置し、アイコンを下に配置
                        cell.setVerticalTextPosition(javax.swing.SwingConstants.TOP);
                        cell.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
                        cell.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                        cell.setIconTextGap(4);

                        // HOROS-20240407準拠: [cell setTitle: ...]; (9529/9571行目)
                        // HOROS-20240407準拠: テキストを先に設定（setImagePositionの後、setImageの前）
                        // HOROS-20240407準拠: 2行表示（\rで改行、Java SwingではHTMLタグを使用して2行表示）
                        // HOROS-20240407準拠: Java SwingのJButtonで複数行テキストを表示するにはHTMLタグを使用
                        if (title.contains("\n")) {
                            // 2行表示の場合、HTMLタグを使用
                            String htmlTitle = "<html><center>" + title.replace("\n", "<br>") + "</center></html>";
                            cell.setText(htmlTitle);
                        } else {
                            cell.setText(title);
                        }

                        // HOROS-20240407準拠: [cell setImage: img]; (9601行目)
                        // HOROS-20240407準拠: アイコンを最後に設定

                        cell.setIcon(scaledImg);

                        // HOROS-20240407準拠: [cell setEnabled:YES]; (9494行目)
                        cell.setEnabled(true);

                        cell.putClientProperty("file", curFile);
                        cell.putClientProperty("tag", (int) i);

                        // Weasis実装参考: セルの表示を更新
                        // Java Swingでは、アイコンとテキストを設定した後、明示的に再検証と再描画が必要
                        // renewRowsで作成されたセルの初期設定を上書きするため、明示的に設定
                        cell.invalidate();
                        cell.revalidate();
                        cell.repaint();

                        // HOROS-20240407準拠: マトリックスの表示を更新
                        if (oMatrix != null) {
                            oMatrix.invalidate();
                            oMatrix.revalidate();
                            oMatrix.repaint();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // HOROS-20240407準拠: エラーが発生した場合はスキップ
            // デバッグログは削除（HOROS-20240407準拠）
        }
    }

    /**
     * 単数形/複数形をローカライズして表示
     * HOROS-20240407準拠: N2LocalizedSingularPluralCount (N2Stuff.h 45行目)
     * HOROS-20240407準拠: #define N2LocalizedSingularPluralCount(c, s, p) [NSString
     * stringWithFormat:@"%@ %@", [NSNumberFormatter
     * localizedStringFromNumber:[NSNumber numberWithInteger:(NSInteger)c]
     * numberStyle:NSNumberFormatterDecimalStyle], (c == 1? s : p)]
     * 
     * @param count      数値
     * @param singleType 単数形（例: "Image"）
     * @param pluralType 複数形（例: "Images"）
     * @return ローカライズされた文字列（例: "1 Image" または "3 Images"）
     */
    private String n2LocalizedSingularPluralCount(int count, String singleType, String pluralType) {
        // HOROS-20240407準拠: 数値をローカライズして表示
        java.text.NumberFormat formatter = java.text.NumberFormat.getIntegerInstance();
        String countStr = formatter.format(count);

        // HOROS-20240407準拠: count == 1 の場合は単数形、それ以外は複数形
        String type = (count == 1) ? singleType : pluralType;

        // HOROS-20240407準拠: "%@ %@" の形式で結合
        return countStr + " " + type;
    }

    /**
     * 画像アイコンをスケーリング
     * HOROS-20240407準拠: [img imageByScalingProportionallyUsingNSImage: scale]
     * 
     * @param icon  元のアイコン
     * @param scale スケール（0.6, 1.0, 1.3など）
     * @return スケーリングされたアイコン
     */
    private javax.swing.ImageIcon scaleImageIcon(javax.swing.ImageIcon icon, double scale) {
        if (icon == null) {
            return null;
        }

        java.awt.Image originalImage = icon.getImage();
        int newWidth = (int) (originalImage.getWidth(null) * scale);
        int newHeight = (int) (originalImage.getHeight(null) * scale);

        if (newWidth <= 0 || newHeight <= 0) {
            return icon;
        }

        // HOROS-20240407準拠: 比例スケーリング
        java.awt.Image scaledImage = originalImage.getScaledInstance(
                newWidth,
                newHeight,
                java.awt.Image.SCALE_SMOOTH);

        return new javax.swing.ImageIcon(scaledImage);
    }

    /**
     * サムネイル読み込みスレッドを開始
     * HOROS-20240407準拠: BrowserController.m 5105-5158行目
     * 
     * @param files      画像ファイルのリスト
     * @param imageLevel イメージレベルかどうか
     */
    private void startMatrixLoadIconsThread(List<Object> files, boolean imageLevel) {

        // HOROS-20240407準拠: 既存のスレッドをキャンセル
        synchronized (previewPixThumbnails) {
            if (matrixLoadIconsThread != null && matrixLoadIconsThread.isAlive()) {
                matrixLoadIconsThread.interrupt();
            }

            // HOROS-20240407準拠: スレッドで実行するデータを準備
            // シリーズレベルの場合、サムネイルが5個未満ならメインスレッドで実行
            boolean separateThread = true;
            if (!imageLevel) {
                int thumbnailsToGenerate = 0;
                for (Object file : files) {
                    if (file instanceof com.oculus.jj.model.DicomSeries) {
                        com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) file;
                        if (series.getThumbnail() == null) {
                            thumbnailsToGenerate++;
                        }
                    }
                }

                if (thumbnailsToGenerate < 5) {
                    separateThread = false;
                }
            }

            if (separateThread) {
                // HOROS-20240407準拠: 別スレッドで実行
                final List<Object> filesCopy = new ArrayList<>(files);
                final boolean imageLevelCopy = imageLevel;

                matrixLoadIconsThread = new Thread(() -> {
                    try {
                        matrixLoadIcons(filesCopy, imageLevelCopy);
                    } catch (Exception e) {
                        // HOROS-20240407準拠: デバッグログは削除
                    }
                }, "matrixLoadIcons");
                matrixLoadIconsThread.start();
            } else {
                // HOROS-20240407準拠: メインスレッドで実行
                matrixLoadIcons(files, imageLevel);
                SwingUtilities.invokeLater(() -> matrixDisplayIcons());
            }
        }
    }

    /**
     * サムネイルを読み込む
     * HOROS-20240407準拠: - (void)matrixLoadIcons: (NSDictionary*)dict (10021行目)
     * 
     * @param files      画像ファイルのリスト
     * @param imageLevel イメージレベルかどうか
     */
    private void matrixLoadIcons(List<Object> files, boolean imageLevel) {

        // HOROS-20240407準拠: BrowserController.m 10021-10170行目
        try {
            List<javax.swing.ImageIcon> tempPreviewPixThumbnails = null;
            List<com.oculus.jj.model.DicomPix> tempPreviewPix = null;

            synchronized (previewPixThumbnails) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                // HOROS-20240407準拠: tempPreviewPixThumbnails = [[previewPixThumbnails
                // mutableCopy] autorelease]; (10046行目)
                // HOROS-20240407準拠: tempPreviewPix = [[previewPix mutableCopy] autorelease];
                // (10047行目)
                // HOROS-20240407準拠: previewPixThumbnailsには既にnotFoundImageが追加されている（5083行目）
                tempPreviewPixThumbnails = new ArrayList<>(previewPixThumbnails);
                if (previewPix != null) {
                    tempPreviewPix = new ArrayList<>(previewPix);
                } else {
                    tempPreviewPix = new ArrayList<>();
                }

                // HOROS-20240407準拠: files.size()分のサイズを確保（notFoundImageで埋める）
                while (tempPreviewPixThumbnails.size() < files.size()) {
                    tempPreviewPixThumbnails.add(notFoundImage);
                }
                while (tempPreviewPix.size() < files.size()) {
                    tempPreviewPix.add(null);
                }
            }

            // HOROS-20240407準拠: NSTimeInterval now = [NSDate
            // timeIntervalSinceReferenceDate];
            // HOROS-20240407準拠: if (now-_timeIntervalOfLastLoadIconsDisplayIcons > 0.5)
            // インスタンス変数を使用（HOROS-20240407準拠: BrowserController.h 261行目）

            for (int i = 0; i < files.size(); i++) {
                try {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    // HOROS-20240407準拠: if( i != 0)
                    if (i != 0) {
                        // HOROS-20240407準拠: // only do it on a delayed basis
                        // HOROS-20240407準拠: NSTimeInterval now = [NSDate
                        // timeIntervalSinceReferenceDate];
                        long now = System.currentTimeMillis();
                        // HOROS-20240407準拠: if (now-_timeIntervalOfLastLoadIconsDisplayIcons > 0.5)
                        if (now - timeIntervalOfLastLoadIconsDisplayIcons > 500) {
                            // HOROS-20240407準拠: _timeIntervalOfLastLoadIconsDisplayIcons = now;
                            timeIntervalOfLastLoadIconsDisplayIcons = now;

                            // 常に現在のリストに直接書き込む（選択変更時に新しいリストが作成されるため）
                            synchronized (previewPixThumbnails) {
                                for (int k = 0; k < tempPreviewPixThumbnails.size(); k++) {
                                    if (k < previewPixThumbnails.size()) {
                                        previewPixThumbnails.set(k, tempPreviewPixThumbnails.get(k));
                                    }
                                }
                            }

                            synchronized (previewPix) {
                                for (int k = 0; k < tempPreviewPix.size(); k++) {
                                    if (k < previewPix.size()) {
                                        previewPix.set(k, tempPreviewPix.get(k));
                                    }
                                }
                            }

                            if (!Thread.currentThread().isInterrupted()) {
                                SwingUtilities.invokeLater(() -> {
                                    matrixDisplayIcons();
                                });
                            }
                        }
                    }

                    // HOROS-20240407準拠: DicomImage* image = [idatabase objectWithID:[objectIDs
                    // objectAtIndex:i]];
                    // HOROS-20240407準拠: if (!image) break; // the objects don't exist anymore, the
                    // selection has very likely changed after this call
                    Object fileObj = files.get(i);
                    if (fileObj == null) {

                        continue;
                    }

                    // HOROS-20240407準拠: サムネイルを生成
                    javax.swing.ImageIcon thumbnail = null;
                    com.oculus.jj.model.DicomPix pix = null;

                    if (fileObj instanceof com.oculus.jj.model.DicomImage) {
                        com.oculus.jj.model.DicomImage image = (com.oculus.jj.model.DicomImage) fileObj;

                        // HOROS-20240407準拠: int frame = 0;
                        // HOROS-20240407準拠: if (image.numberOfFrames.intValue > 1) frame =
                        // image.numberOfFrames.intValue/2;
                        // HOROS-20240407準拠: if (image.frameID) frame = image.frameID.intValue;
                        int frame = 0;
                        if (image.getNumberOfFrames() != null && image.getNumberOfFrames() > 1) {
                            frame = image.getNumberOfFrames() / 2;
                        }
                        if (image.getFrameID() != null) {
                            frame = image.getFrameID();
                        }

                        // HOROS-20240407準拠: DCMPix* dcmPix = [self
                        // getDCMPixFromViewerIfAvailable:image.completePath frameNumber: frame];
                        // HOROS-20240407準拠: if (dcmPix == nil) dcmPix = [[[DCMPix alloc]
                        // initWithPath:image.completePath :0 :1 :nil :frame :0 isBonjour:![idatabase
                        // isLocal] imageObj: image] autorelease];
                        String completePath = image.getCompletePath();
                        String pathValue = image.path();

                        if (completePath == null || completePath.isEmpty()) {

                            // HOROS-20240407準拠: エラー時はnotFoundImageを使用
                            while (tempPreviewPixThumbnails.size() <= i) {
                                tempPreviewPixThumbnails.add(null);
                            }
                            tempPreviewPixThumbnails.set(i, notFoundImage);
                            while (tempPreviewPix.size() <= i) {
                                tempPreviewPix.add(null);
                            }
                            tempPreviewPix.set(i, null);
                            continue;
                        }
                        // ファイルの存在確認
                        java.io.File file = new java.io.File(completePath);
                        if (!file.exists()) {

                            // もし相対パスなら、ベースディレクトリからの相対とみなして再試行
                            if (database != null) {
                                java.io.File retryFile = new java.io.File(database.getDataBaseDirPath(), completePath);
                                if (retryFile.exists()) {

                                    completePath = retryFile.getAbsolutePath();
                                }
                            }
                        } else {

                        }
                        if (completePath != null && !completePath.isEmpty()) {
                            try {
                                pix = new com.oculus.jj.model.DicomPix(
                                        completePath,
                                        0, // imageIndex
                                        1, // numberOfImages
                                        frame, // frameNumber
                                        "0", // seriesId (String)
                                        false,
                                        image // imageObj
                                );
                            } catch (Exception e) {
                                // HOROS-20240407準拠: エラー時はnotFoundImageを使用
                                // JPEG圧縮画像（jpeg-cv）の場合はエラーログを抑制
                                if (e.getMessage() != null && e.getMessage().contains("jpeg-cv")) {
                                    // デバッグログは出力しない（JPEG圧縮画像はdcm4che-imageio-opencvが必要）
                                } else {
                                    System.err
                                            .println("[ERROR] matrixLoadIcons() - failed to create DicomPix for image "
                                                    + i + ", completePath: " + completePath);
                                    e.printStackTrace();
                                }
                                pix = null;
                            }
                        }

                        // HOROS-20240407準拠: if (!imageLevel)
                        if (!imageLevel) {
                            // HOROS-20240407準拠: NSData* dbThmb = image.series.thumbnail;
                            // HOROS-20240407準拠: if (dbThmb)
                            if (image.getSeries() != null) {
                                byte[] dbThmb = image.getSeries().getThumbnail();
                                if (dbThmb != null) {
                                    // HOROS-20240407準拠: NSImageRep* rep = [[[NSBitmapImageRep alloc]
                                    // initWithData:dbThmb] autorelease];
                                    // HOROS-20240407準拠: NSImage* dbIma = [[[NSImage alloc] initWithSize:[rep size]]
                                    // autorelease];
                                    // HOROS-20240407準拠: [dbIma addRepresentation:rep];
                                    try {
                                        java.awt.Image img = javax.imageio.ImageIO
                                                .read(new java.io.ByteArrayInputStream(dbThmb));
                                        thumbnail = new javax.swing.ImageIcon(img);

                                        // HOROS-20240407準拠: DCMPix *pix = (dcmPix? dcmPix : [[[DCMPix alloc]
                                        // myinitEmpty] autorelease]);
                                        if (pix == null) {
                                            pix = null; // 後で実装
                                        }

                                        // HOROS-20240407準拠: [tempPreviewPixThumbnails replaceObjectAtIndex: i
                                        // withObject: dbIma];
                                        // HOROS-20240407準拠: [tempPreviewPix addObject: pix];
                                        // HOROS-20240407準拠: continue;
                                        // 配列に追加（後で処理）
                                        while (tempPreviewPixThumbnails.size() <= i) {
                                            tempPreviewPixThumbnails.add(null);
                                        }
                                        tempPreviewPixThumbnails.set(i, thumbnail);

                                        while (tempPreviewPix.size() <= i) {
                                            tempPreviewPix.add(null);
                                        }
                                        tempPreviewPix.set(i, pix);

                                        continue; // HOROS-20240407準拠: continue
                                    } catch (Exception e) {
                                        // HOROS-20240407準拠: デバッグログは削除
                                    }
                                }
                            }
                        }

                        // HOROS-20240407準拠: if (dcmPix)
                        if (pix != null) {
                            // HOROS-20240407準拠: if ([DCMAbstractSyntaxUID
                            // isStructuredReport:image.series.seriesSOPClassUID] || [DCMAbstractSyntaxUID
                            // isPDF:image.series.seriesSOPClassUID])
                            String seriesSOPClassUID = null;
                            if (image.getSeries() != null) {
                                seriesSOPClassUID = image.getSeries().getSeriesSOPClassUID();
                            }

                            if (seriesSOPClassUID != null && (seriesSOPClassUID.contains("StructuredReport")
                                    || seriesSOPClassUID.contains("PDF"))) {
                                // HOROS-20240407準拠: NSImage *icon = [[NSWorkspace sharedWorkspace]
                                // iconForFileType: @"txt"];
                                // HOROS-20240407準拠: NSImage *thumbnail = [[[NSImage alloc] initWithSize:
                                // NSMakeSize( THUMBNAILSIZE, THUMBNAILSIZE)] autorelease];
                                // HOROS-20240407準拠: [thumbnail lockFocus];
                                // HOROS-20240407準拠: [icon drawInRect: NSMakeRect( 0, 0, THUMBNAILSIZE,
                                // THUMBNAILSIZE) fromRect: [icon alignmentRect] operation: NSCompositeCopy
                                // fraction: 1.0];
                                // HOROS-20240407準拠: [thumbnail unlockFocus];
                                thumbnail = notFoundImage;

                                // HOROS-20240407準拠: [tempPreviewPixThumbnails replaceObjectAtIndex: i
                                // withObject: thumbnail];
                                // HOROS-20240407準拠: [tempPreviewPix addObject: dcmPix];
                                while (tempPreviewPixThumbnails.size() <= i) {
                                    tempPreviewPixThumbnails.add(null);
                                }
                                tempPreviewPixThumbnails.set(i, thumbnail);

                                while (tempPreviewPix.size() <= i) {
                                    tempPreviewPix.add(null);
                                }
                                tempPreviewPix.set(i, pix);

                                continue; // HOROS-20240407準拠: continue
                            } else {
                                // HOROS-20240407準拠: NSImage* thumbnail = [dcmPix
                                // generateThumbnailImageWithWW:image.series.windowWidth.floatValue
                                // WL:image.series.windowLevel.floatValue];
                                float ww = 0.0f;
                                float wl = 0.0f;
                                if (image.getSeries() != null) {
                                    if (image.getSeries().getWindowWidth() != null) {
                                        ww = image.getSeries().getWindowWidth().floatValue();
                                    }
                                    if (image.getSeries().getWindowLevel() != null) {
                                        wl = image.getSeries().getWindowLevel().floatValue();
                                    }
                                }

                                // HOROS-20240407準拠: Window Level/Widthを設定
                                // DBの値が0、または不正な場合はヘッダーの値（DicomPix内のsavedWL/savedWW）を使用する
                                if (ww > 0) {
                                    pix.setWindowLevelWidth(wl, ww);
                                } else {
                                    // wwが0の場合はヘッダーのデフォルト値を使用
                                    pix.setWindowLevelWidth(pix.getSavedWL(), pix.getSavedWW());
                                }

                                // HOROS-20240407準拠: サムネイルを生成

                                // HOROS-20240407準拠: THUMBNAILSIZE = 70 (DicomSeries.h 42行目)
                                // HOROS-20240407準拠: サムネイルは70x70で生成される
                                java.awt.image.BufferedImage thumbBuff = pix.generateThumbnail(70, 70); // HOROS-20240407準拠:
                                                                                                        // THUMBNAILSIZE
                                if (thumbBuff != null) {
                                    thumbnail = new javax.swing.ImageIcon(thumbBuff);

                                } else {

                                    thumbnail = notFoundImage;
                                }

                                // HOROS-20240407準拠: [dcmPix revert:NO]; // <- Kill the raw data
                                pix.revert(false);

                                // HOROS-20240407準拠: if (thumbnail == nil || dcmPix.notAbleToLoadImage == YES)
                                // thumbnail = notFoundImage;

                                // HOROS-20240407準拠: [tempPreviewPixThumbnails replaceObjectAtIndex: i
                                // withObject: thumbnail];
                                // HOROS-20240407準拠: [tempPreviewPix addObject: dcmPix];
                                while (tempPreviewPixThumbnails.size() <= i) {
                                    tempPreviewPixThumbnails.add(null);
                                }
                                tempPreviewPixThumbnails.set(i, thumbnail);

                                while (tempPreviewPix.size() <= i) {
                                    tempPreviewPix.add(null);
                                }
                                tempPreviewPix.set(i, pix);

                                continue; // HOROS-20240407準拠: continue
                            }
                        }
                    } else if (fileObj instanceof com.oculus.jj.model.DicomSeries) {
                        // HOROS-20240407準拠: シリーズレベルの処理
                        com.oculus.jj.model.DicomSeries series = (com.oculus.jj.model.DicomSeries) fileObj;

                        // HOROS-20240407準拠: DBに保存されたサムネイルを使用
                        byte[] dbThmb = series.getThumbnail();
                        if (dbThmb != null) {
                            try {
                                java.awt.Image img = javax.imageio.ImageIO
                                        .read(new java.io.ByteArrayInputStream(dbThmb));
                                thumbnail = new javax.swing.ImageIcon(img);
                            } catch (Exception e) {
                                // HOROS-20240407準拠: デバッグログは削除
                            }
                        }

                        // HOROS-20240407準拠: サムネイルがなければ生成
                        if (thumbnail == null) {
                            // 現在はプレースホルダー
                            thumbnail = notFoundImage;
                        }

                        // HOROS-20240407準拠: 配列に追加
                        while (tempPreviewPixThumbnails.size() <= i) {
                            tempPreviewPixThumbnails.add(null);
                        }
                        tempPreviewPixThumbnails.set(i, thumbnail);

                        while (tempPreviewPix.size() <= i) {
                            tempPreviewPix.add(null);
                        }
                        tempPreviewPix.set(i, pix);
                    }
                }
                // HOROS-20240407準拠: @catch (NSException* e)
                catch (Exception e) {
                    // HOROS-20240407準拠: N2LogExceptionWithStackTrace(e);
                    // デバッグログは削除（HOROS-20240407準拠）
                }
                // HOROS-20240407準拠: // successful iterations don't execute this (they continue
                // to the next iteration), this is in case no image has been provided by this
                // iteration (exception, no file, ...)
                // HOROS-20240407準拠: [tempPreviewPixThumbnails replaceObjectAtIndex: i
                // withObject: notFoundImage]; (10146行目)
                // HOROS-20240407準拠: [tempPreviewPix addObject: [[[DCMPix alloc] myinitEmpty]
                // autorelease]]; (10147行目)
                // エラー時はnotFoundImageを使用（HOROS-20240407準拠）
                // HOROS-20240407準拠: replaceObjectAtIndexで置き換える（既にnotFoundImageが入っているはず）
                while (tempPreviewPixThumbnails.size() <= i) {
                    tempPreviewPixThumbnails.add(notFoundImage);
                }
                tempPreviewPixThumbnails.set(i, notFoundImage);
                while (tempPreviewPix.size() <= i) {
                    tempPreviewPix.add(null);
                }
                if (tempPreviewPix.get(i) == null) {
                    tempPreviewPix.set(i, null); // 後で実装
                }
            }

            // HOROS-20240407準拠: @synchronized( previewPixThumbnails)
            if (!Thread.currentThread().isInterrupted()) {

                synchronized (previewPixThumbnails) {
                    for (int k = 0; k < tempPreviewPixThumbnails.size(); k++) {
                        if (k < previewPixThumbnails.size()) {
                            previewPixThumbnails.set(k, tempPreviewPixThumbnails.get(k));
                        }
                    }
                }

                if (previewPix != null) {
                    synchronized (previewPix) {
                        for (int k = 0; k < tempPreviewPix.size(); k++) {
                            if (k < previewPix.size()) {
                                previewPix.set(k, tempPreviewPix.get(k));
                            }
                        }
                    }
                }

                if (!SwingUtilities.isEventDispatchThread()) {
                    SwingUtilities.invokeLater(() -> {
                        matrixDisplayIcons();
                    });
                } else {
                    matrixDisplayIcons();
                }
            }
        }
        // HOROS-20240407準拠: @catch (NSException* e)
        catch (

        Exception e) {
            // HOROS-20240407準拠: N2LogExceptionWithStackTrace(e);
            // デバッグログは削除（HOROS-20240407準拠）
        }
        // HOROS-20240407準拠: [pool release];
        // Javaでは不要（GCが自動的に処理）
    }

    /**
     * サムネイルを表示する
     * HOROS-20240407準拠: - (void)matrixDisplayIcons:(id) sender (9719行目)
     */
    private void matrixDisplayIcons() {

        // HOROS-20240407準拠: BrowserController.m 9719-9769行目
        if (database == null) {

            return;
        }

        try {
            synchronized (previewPixThumbnails) {
                // HOROS-20240407準拠: if ([previewPix count] && loadPreviewIndex < [previewPix
                // count])

                // HOROS-20240407準拠: if ([previewPix count] && loadPreviewIndex < [previewPix
                // count]) (9729行目)
                // HOROS-20240407準拠: previewPixが空の場合は何もしない
                // 改修: loadPreviewIndexによるスキップを中断し、未読み込みセルがあれば常にチェックするように変更
                if (previewPix != null && !previewPix.isEmpty()) {

                    long i;
                    boolean firstProcess = (loadPreviewIndex == 0);
                    // HOROS-20240407準拠: for( i = 0; i < [previewPix count]; i++) (9732行目)
                    for (i = 0; i < previewPix.size(); i++) {
                        if (oMatrix != null) {
                            int cols = oMatrix.getColumns();
                            if (cols < 1) {
                                cols = 1;
                            }

                            int row = (int) (i / cols);
                            int col = (int) (i % cols);
                            javax.swing.JButton cell = oMatrix.cellAtRowColumn(row, col);

                            // HOROS-20240407準拠: if( [cell isEnabled] == NO) (9737行目)
                            // 改修: すべてのセル（無効なセルを含む）にサムネイルが表示されるように変更
                            if (cell != null) {
                                // HOROS-20240407準拠: if( i < [previewPix count]) (9739行目)
                                // HOROS-20240407準拠: if( [previewPix objectAtIndex: i] != nil) (9741行目)
                                Object pix = previewPix.get((int) i);
                                Object curItem = i < matrixViewArray.size() ? matrixViewArray.get((int) i) : null;

                                // HOROS-20240407準拠: if( i < [matrixViewArray count]) (9743行目)
                                // Seriesレベルの場合、pixがnullでもサムネイルがあれば表示する
                                // かつ、サムネイルがnotFoundImageでない場合、またはpixが読み込まれている場合にロードしたとみなす
                                javax.swing.ImageIcon thumb = (i < previewPixThumbnails.size())
                                        ? previewPixThumbnails.get((int) i)
                                        : null;

                                if (curItem != null && (pix != null
                                        || (curItem instanceof com.oculus.jj.model.DicomSeries
                                                && thumb != null))) {
                                    // HOROS-20240407準拠: [self matrixNewIcon:i :[matrixViewArray objectAtIndex: i]];
                                    // (9745行目)

                                    matrixNewIcon(i, curItem);
                                }
                            }
                        }
                    }

                    // HOROS-20240407準拠: 選択されていない場合は最初のセルを選択
                    if (oMatrix != null) {
                        javax.swing.JButton selectedCell = oMatrix.selectedCell();
                        if (selectedCell == null) {
                            if (!matrixViewArray.isEmpty()) {
                                oMatrix.selectCellWithTag(0);
                            }
                        }
                    }

                    // 最初のサムネイルが読み込まれたらアニメーションスライダーを初期化
                    if (firstProcess) {
                        initAnimationSlider();
                    }

                    loadPreviewIndex = i;
                }

                // HOROS-20240407準拠: BrowserController.m 9610-9625行目
                // setDCMDone == NO の場合、imageViewにsetPixelsを呼び出す
                // HOROS-20240407準拠: この処理はループの外で実行される（loadPreviewIndex == iの後）
                if (!setDCMDone) {
                    Object aFile = databaseOutline.getSelectedItem();

                    if (aFile != null && imageView != null) {
                        synchronized (previewPixThumbnails) {
                            // HOROS-20240407準拠: BrowserController.m 9619行目
                            // [imageView setPixels:previewPix files:[self imagesArray: aFile
                            // preferredObject: oAny] rois:nil firstImage:[[oMatrix selectedCell] tag]
                            // level:'i' reset:YES];
                            javax.swing.JButton selectedCell = oMatrix != null ? oMatrix.selectedCell() : null;
                            int firstImage = 0;
                            if (selectedCell != null) {
                                Object tagObj = selectedCell.getClientProperty("tag");
                                firstImage = tagObj instanceof Integer ? (Integer) tagObj : 0;
                            }

                            List<Object> files = imagesArray(aFile, oAny);
                            // HOROS-20240407準拠: BrowserController.m 9619行目
                            // previewPixが空でないことを確認していない
                            // そのため、previewPixが空の場合は、空のリストを渡す
                            if (!files.isEmpty()) {
                                // HOROS-20240407準拠: previewPixが空の場合は、空のリストを補完
                                synchronized (previewPix) {
                                    if (previewPix == null) {
                                        previewPix = Collections.synchronizedList(new ArrayList<>());
                                    }
                                    if (previewPix.isEmpty()) {
                                        for (int j = 0; j < files.size(); j++) {
                                            previewPix.add(null);
                                        }
                                    }
                                }
                                imageView.setPixels(previewPix, files, null, (short) firstImage, 'i', true);

                                // HOROS-20240407準拠: BrowserController.m 9622行目
                                // [imageView setStringID:@"previewDatabase"];
                                imageView.setStringID("previewDatabase");

                                setDCMDone = true;
                            }
                        }
                    } else if (imageView != null) {
                        // 改修: すでにsetPixels呼び出し済みの表示中DCMViewも再描画する
                        // これにより、バックグラウンドでの画像読み込み進捗がプレビューに反映される
                        imageView.repaint();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * アニメーションスライダー初期化
     * HOROS-20240407準拠: - (void) initAnimationSlider (8861行目)
     */
    private void initAnimationSlider() {
        if (animationSlider == null) {
            animationSlider = new javax.swing.JSlider();
        }

        boolean animate = false;
        long noOfImages = 0;

        javax.swing.JButton cell = oMatrix != null ? oMatrix.selectedCell() : null;

        if (cell != null) {
            Object tagObj = cell.getClientProperty("tag");
            int tag = tagObj instanceof Integer ? (Integer) tagObj : -1;

            if (tag >= 0 && matrixViewArray != null && tag < matrixViewArray.size()) {
                Object aFile = databaseOutline.getSelectedItem();

                if (aFile != null) {
                    String type = getItemType(aFile);

                    if ("Series".equals(type)) {
                        int imageCount = getItemImageCount(aFile);
                        if (imageCount > 1) {
                            noOfImages = imageCount;
                            animate = true;
                        }
                    } else if ("Study".equals(type)) {
                        // HOROS-20240407準拠: Studyが選択されていても、マトリックスで特定のシリーズが選択されているなら
                        // そのシリーズの枚数を確認する
                        Object selectedSeries = (matrixViewArray != null && tag >= 0 && tag < matrixViewArray.size())
                                ? matrixViewArray.get(tag)
                                : null;

                        if (selectedSeries != null) {
                            int imageCount = getItemImageCount(selectedSeries);
                            if (imageCount > 1) {
                                noOfImages = imageCount;
                                animate = true;
                            } else if (previewPix != null && previewPix.size() > 1) {
                                // HOROS-20240407準拠: 選択したシリーズが1枚のみなら、シリーズ間ナビゲーションに切り替える
                                // BrowserController.m 8894-8899行目相当
                                noOfImages = previewPix.size();
                                animate = true;
                            } else {
                                // 1枚のみの場合はアニメーション不可
                                animate = false;
                            }
                        } else if (previewPix != null && previewPix.size() > 1) {
                            // 何も選択されていない場合はシリーズ間のナビゲーションとして機能
                            noOfImages = previewPix.size();
                            animate = true;
                        }
                    }
                }
            }
        }

        // HOROS-20240407準拠: animateがNOの場合はスライダーを無効化し、YESの場合は有効化する
        if (!animate) {
            if (animationSlider != null) {
                animationSlider.setEnabled(false);
                animationSlider.setMaximum(0);
                animationSlider.setValue(0);
            }
        } else if (animationSlider != null) {
            // HOROS-20240407準拠: animateがtrueの場合、animationSliderを有効化
            if (!animationSlider.isEnabled()) {
                animationSlider.setEnabled(true);
            }
            animationSlider.setMaximum((int) (noOfImages - 1));
            // HOROS-20240407準拠: 現在選択されているサムネイルのインデックスを使用
            if (cell != null) {
                Object tagObj = cell.getClientProperty("tag");
                int currentTag = tagObj instanceof Integer ? (Integer) tagObj : -1;
                if (currentTag >= 0 && currentTag < noOfImages) {
                    animationSlider.setValue(currentTag);
                } else {
                    animationSlider.setValue(0);
                }
            } else {
                animationSlider.setValue(0);
            }
        }

        withReset = true;
        previewSliderAction(animationSlider);
        withReset = false;
    }

    /**
     * プレビュースライダーアクション
     * HOROS-20240407準拠: - (void) previewSliderAction:(id) sender (9049行目)
     * 注意: このメソッドは200行以上の複雑なロジックを含む
     */
    private void previewSliderAction(Object sender) {

        // HOROS-20240407準拠: BrowserController.m 9049-9218行目

        if (dontUpdatePreviewPane) {

            return;
        }

        javax.swing.JButton cell = oMatrix != null ? oMatrix.selectedCell() : null;

        Object aFile = databaseOutline.getSelectedItem();

        if (aFile == null) {

            return;
        }

        String type = getItemType(aFile);

        // HOROS-20240407準拠: Studyタイプの場合、cellがnullでも処理を続行
        // Seriesタイプの場合、cellがnullまたはdisabledの場合は早期リターン
        if (!"Study".equals(type)) {
            if (cell == null || !cell.isEnabled()) {

                return;
            }
        }

        int index = -1;
        if (cell != null) {
            Object tagObj = cell.getClientProperty("tag");
            index = tagObj instanceof Integer ? (Integer) tagObj : -1;
        } else if ("Study".equals(type) && animationSlider != null) {
            // HOROS-20240407準拠: Studyタイプでcellがnullの場合、animationSliderの値を使用
            index = animationSlider.getValue();
        }

        // HOROS-20240407準拠: BrowserController.m 9095-9145行目 - Studyタイプの処理
        if ("Study".equals(type) && cell == null) {
            // HOROS-20240407準拠: Studyタイプでセルが選択されていない場合、previewPixのサイズを使用
            if (previewPix != null && !previewPix.isEmpty()) {
                // HOROS-20240407準拠: animationSliderの値を使用（senderが存在する場合、またはアニメーション時）
                if (animationSlider != null
                        && (sender != null || (animationCheck != null && animationCheck.isSelected()))) {
                    index = animationSlider.getValue();
                }
                // indexが範囲外の場合は調整
                if (index < 0)
                    index = 0;
                if (index >= previewPix.size())
                    index = previewPix.size() - 1;

                // HOROS-20240407準拠: BrowserController.m 9104-9105行目
                // if( sender) [oMatrix selectCellWithTag: [animationSlider intValue]];
                // senderが存在する場合（スライドバー操作時）はサムネイルを選択
                if (sender != null && oMatrix != null) {
                    oMatrix.selectCellWithTag(index);
                }

                if (imageView != null && index >= 0 && index < previewPix.size()) {
                    // HOROS-20240407準拠: BrowserController.m 9144-9145行目
                    // [imageView setIndex:[cell tag]];
                    imageView.setIndex(index);
                }
            } else {

            }
            return; // Studyタイプでセル未選択の場合はここで終了
        }

        // Seriesタイプの場合の処理
        if ("Series".equals(type)) {
            if (index < 0 || matrixViewArray == null || index >= matrixViewArray.size()) {

                return;
            }
            int imageCount = getItemImageCount(aFile);

            if (imageCount > 1 && animationSlider != null) {
                int sliderValue = animationSlider.getValue();

                // HOROS-20240407準拠: BrowserController.m 9104-9105行目
                // if( sender) [oMatrix selectCellWithTag: [animationSlider intValue]];
                // HOROS-20240407準拠: スライダー操作時やアニメーション時は、サムネイルを選択
                if (oMatrix != null && (sender != null || (animationCheck != null && animationCheck.isSelected()))) {
                    oMatrix.selectCellWithTag(sliderValue);
                }
                if (imageView != null && sliderValue < imageCount) {
                    System.out
                            .println("[DEBUG] previewSliderAction() - calling imageView.setIndex(" + sliderValue + ")");
                    imageView.setIndex(sliderValue);
                } else {
                    System.out
                            .println("[DEBUG] previewSliderAction() - imageView is null or sliderValue >= imageCount");
                }
            } else {
                // HOROS-20240407準拠: 1枚のみの場合でもマトリックスで選択されているインデックスを表示
                if (imageView != null && index >= 0 && index < matrixViewArray.size()) {

                    imageView.setIndex(index);
                }
            }
        } else if ("Study".equals(type)) {
            // HOROS-20240407準拠: BrowserController.m 9099-9100行目 - スタディレベルの処理
            int cellTag = index;
            if (cell != null) {
                Object cellTagObj = cell.getClientProperty("tag");
                if (cellTagObj instanceof Integer) {
                    cellTag = (Integer) cellTagObj;
                }
            }

            // 画像内でのインデックスか、シリーズ間のインデックスかを判別
            int imageIndex = 0;
            boolean isSeriesNavigationMode = false;
            if (animationSlider != null && previewPix != null
                    && animationSlider.getMaximum() == previewPix.size() - 1) {
                isSeriesNavigationMode = true;
            }

            // HOROS-20240407準拠: senderが存在する場合（スライダー操作時）またはアニメーション時は、animationSliderの値を使用
            if (animationSlider != null
                    && (sender != null || (animationCheck != null && animationCheck.isSelected()))) {
                int sliderValue = animationSlider.getValue();
                if (isSeriesNavigationMode) {
                    // シリーズ間ナビゲーションモード: スライダーの値はシリーズのインデックス
                    cellTag = sliderValue;
                    imageIndex = 0; // シリーズ切り替え時は常に最初の画像を表示

                    // マトリックスの選択を更新
                    if (oMatrix != null && cellTag >= 0 && cellTag < previewPix.size()) {
                        oMatrix.selectCellWithTag(cellTag);
                    }
                } else {
                    // 画像ナビゲーションモード: スライダーの値は現在のシリーズ内での画像インデックス
                    imageIndex = sliderValue;
                }
            } else {
                // スライダー操作でない（マトリックス直接クリックなど）場合は、常にシリーズの最初の画像を表示
                imageIndex = 0;
            }

            // HOROS-20240407準拠: BrowserController.m 9100行目 - images = [self imagesArray:
            // [matrixViewArray objectAtIndex: [cell tag]]];
            if (matrixViewArray != null && cellTag >= 0 && cellTag < matrixViewArray.size()) {
                Object seriesObj = matrixViewArray.get(cellTag);
                List<Object> images = imagesArray(seriesObj, oAny);

                if (!images.isEmpty() && imageView != null) {
                    // インデックスが範囲内であることを確認
                    if (imageIndex >= 0 && imageIndex < images.size()) {
                        // HOROS-20240407準拠: プレビュー画面が現在のシリーズの全画像を保持しているか確認
                        List<Object> currentFiles = imageView.getDcmFilesList();
                        if (currentFiles == null || currentFiles.size() != images.size()
                                || !currentFiles.equals(images)) {

                            List<com.oculus.jj.model.DicomPix> seriesPixList = new ArrayList<>();
                            for (int k = 0; k < images.size(); k++) {
                                seriesPixList.add(null);
                            }
                            imageView.setPixels(seriesPixList, images, null, (short) imageIndex, 'i', false);
                        }

                        // 画像を表示

                        if (withReset) {
                            imageView.setIndexWithReset((short) imageIndex, true);
                        } else {
                            imageView.setIndex(imageIndex);
                        }
                    }
                } else {

                }
            }
        }
    }

    /**
     * 選択されたアイテムを取得
     * HOROS-20240407準拠: アノテーション表示用
     */
    public Object getSelectedItem() {
        if (databaseOutline != null) {
            return databaseOutline.getSelectedItem();
        }
        return null;
    }

    /**
     * データベース選択項目を取得
     * HOROS-20240407準拠: - (NSArray *) databaseSelection (20654行目)
     */
    private List<Object> databaseSelection() {
        // HOROS-20240407準拠: BrowserController.m 20654-20667行目
        List<Object> selectedItems = new ArrayList<>();

        if (databaseOutline != null) {
            int[] selectedRows = databaseOutline.getSelectedRows();
            for (int index : selectedRows) {
                javax.swing.tree.TreePath path = databaseOutline.getPathForRow(index);
                if (path != null) {
                    Object item = path.getLastPathComponent();
                    if (item != null) {
                        selectedItems.add(item);
                    }
                }
            }
        }

        return selectedItems;
    }

    /**
     * データベーススタディを開く
     * HOROS-20240407準拠: - (void) databaseOpenStudy: (NSManagedObject*) item
     * (7505行目)
     */
    private void databaseOpenStudy(Object item) {
        if (item == null) {
            return;
        }

        // HOROS-20240407準拠: BrowserController.m 7517-7524行目
        // NSArray *cells = [oMatrix selectedCells];
        // if( [cells count] > 1)
        // {
        // for( NSCell *c in oMatrix.cells)
        // [c setHighlighted: NO];
        // [oMatrix selectCell: [cells objectAtIndex: 0]];
        // }
        if (oMatrix != null) {
            List<javax.swing.JButton> selectedCells = oMatrix.selectedCells();
            if (selectedCells.size() > 1) {
                // 現在は基本的な実装のみ
                if (!selectedCells.isEmpty()) {
                    oMatrix.selectCell(selectedCells.get(0));
                }
            }
        }

        // HOROS-20240407準拠: BrowserController.m 7526-7534行目
        // if ([[item valueForKey:@"type"] isEqualToString:@"Series"])
        // {
        // if( [self isUsingExternalViewer: item] == NO)
        // {
        // // DICOM & others
        // [self viewerDICOMInt :NO dcmFile: [NSArray arrayWithObject:item] viewer:nil];
        // }
        // }
        String itemType = getItemType(item);
        if ("Series".equals(itemType)) {
            // if (!isUsingExternalViewer(item)) {
            viewerDICOMInt(false, java.util.Arrays.asList(item), null);
            // }
        } else {
            // HOROS-20240407準拠: BrowserController.m 7535行目以降
            // STUDY - Hanging Protocols - Windows State
            // 現在は基本的な実装のみ
            viewerDICOMInt(false, java.util.Arrays.asList(item), null);
        }
    }

    /**
     * Seriesに対して新しいViewerControllerを開く
     * HOROS-20240407準拠: ViewerController.m 4301行目
     * ViewerController *newViewer = [[BrowserController currentBrowser] loadSeries
     * :series :nil :YES keyImagesOnly: displayOnlyKeyImages];
     */
    public void openViewerForSeries(DicomSeries series) {
        if (series == null) {
            return;
        }

        // HOROS-20240407準拠: データベースから画像をロード
        getDatabase().loadImagesForSeriesIfNeeded(series);
        java.util.Set<com.oculus.jj.model.DicomImage> imageSet = series.getImages();
        if (imageSet == null || imageSet.isEmpty()) {
            return;
        }

        // 画像をリストにしてソート (InstanceNumber, FrameID順)
        List<Object> loadList = new java.util.ArrayList<>(imageSet);
        loadList.sort((a, b) -> {
            com.oculus.jj.model.DicomImage imgA = (com.oculus.jj.model.DicomImage) a;
            com.oculus.jj.model.DicomImage imgB = (com.oculus.jj.model.DicomImage) b;
            int instA = imgA.getInstanceNumber() != null ? imgA.getInstanceNumber() : 0;
            int instB = imgB.getInstanceNumber() != null ? imgB.getInstanceNumber() : 0;
            if (instA != instB) {
                return Integer.compare(instA, instB);
            }
            return Integer.compare(imgA.getFrameID() != null ? imgA.getFrameID() : 0,
                    imgB.getFrameID() != null ? imgB.getFrameID() : 0);
        });

        // openViewerFromImagesを直接呼び出してビューワーを開く (Browserの選択状態に依存しない)
        List<List<Object>> toOpenArray = new java.util.ArrayList<>();
        toOpenArray.add(loadList);

        openViewerFromImages(toOpenArray, false, null, false);
        autoFuseVisiblePETCT();

        // HOROS-20240407準拠: AUTOTILING処理
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(ViewerController.class);
        boolean autoTiling = prefs.getBoolean("AUTOTILING", true);
        if (autoTiling) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                ViewerController.tileWindows();
            });
        }
    }

    // ========== P-G: 4D シリーズ（Movie / Reparse / Select All 4D） ==========
    // HOROS-20240407準拠: BrowserController.m 12576/12581/13530行目

    /**
     * シリーズが4D（アニメーション可能な複数3D体積）かどうかを判定
     * HOROS-20240407準拠: - (BOOL) isDataVolumicIn4D:checkEverythingLoaded: (7271行目)
     * <p>
     * HOROSの判定の中核（簡略化版）:
     * <ol>
     * <li>同一width/heightのフレームが4枚以上連続する → 4D 3D体積</li>
     * <li>複数体積がseries内にある → 4Dシシリーズ</li>
     * </ol>
     * 実装: 連続width/height groupを収集し、最大 group size が 4 以上でtrue.
     */
    public boolean isSeries4D(com.oculus.jj.model.DicomSeries series) {
        if (series == null) return false;
        try {
            List<com.oculus.jj.model.DicomImage> imgs = series.sortedImages();
            if (imgs == null || imgs.size() < 4) return false;
            int bestRun = 0, run = 1;
            int prevW = -1, prevH = -1;
            for (int i = 0; i < imgs.size(); i++) {
                com.oculus.jj.model.DicomImage img = imgs.get(i);
                Integer wi = img.getWidth();
                Integer hi = img.getHeight();
                int w = (wi != null) ? wi : -1;
                int h = (hi != null) ? hi : -1;
                if (i > 0 && w == prevW && h == prevH) {
                    run++;
                } else {
                    run = 1;
                }
                if (run > bestRun) bestRun = run;
                prevW = w; prevH = h;
            }
            return bestRun >= 4;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 選択されたイメージを4Dアニメーションとしてビューワーで開く
     * HOROS-20240407準拠: - (void) MovieViewerDICOM: (id) sender (13530行目)
     * [self viewerDICOMInt:YES dcmFile: selectedItems viewer:nil]
     */
    public void movieViewerDICOM(Object sender) {
        List<Object> selected = databaseSelection();
        if (selected.isEmpty()) {
            infoFmt(LanguageManager.get("4d.open.none", "No series selected."),
                    LanguageManager.get("4d.open.title", "Open in 4D"));
            return;
        }
        try {
            dontUpdatePreviewPane = true;
            viewerDICOMInt(true, selected, null);
            dontUpdatePreviewPane = false;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 4Dシリーズとして再解析（連続同じwidth/heightの複数3D体積を4Dシリーズとして扱う）
     * HOROS-20240407準拠: - (IBAction) reparseIn4D: (id) sender (12576行目)
     * HOROSでは [NSApp stopModalWithCode: 11] を返し、open dialogが4D解析モードで続行される。
     * Java版: 選択行を4Dとして開く（movieViewerDICOMに委譲）。
     */
    public void reparseIn4D(Object sender) {
        List<Object> selected = databaseSelection();
        if (selected.isEmpty()) {
            infoFmt(LanguageManager.get("4d.reparse.none", "No series selected."),
                    LanguageManager.get("4d.reparse.title", "Reparse in 4D"));
            return;
        }
        // HOROSの挙動: 選択中の行を4Dシリーズとして再解析して開く
        // OculusJJでは、同じwidth/heightの連続グループを検出し、movieViewerDICOMで開く
        boolean any4D = false;
        for (Object o : selected) {
            if (o instanceof com.oculus.jj.model.DicomSeries) {
                if (isSeries4D((com.oculus.jj.model.DicomSeries) o)) {
                    any4D = true;
                    break;
                }
            }
        }
        if (any4D) {
            movieViewerDICOM(sender);
        } else {
            infoFmt(LanguageManager.get("4d.reparse.not_4d",
                    "The selected series does not look like a 4D series (needs 4+ consecutive frames with identical width/height)."),
                    LanguageManager.get("4d.reparse.title", "Reparse in 4D"));
        }
    }

    /**
     * 全4Dシリーズを選択する
     * HOROS-20240407準拠: - (IBAction) selectAll4DSeries: (id) sender (12581行目)
     * HOROSでは [subOpenMatrix4D isEnabled] 時 [NSApp stopModalWithCode: 7]
     * Java版: アウトライン内で 4D 判定が成立するシリーズ行をすべて選択
     */
    public void selectAll4DSeries(Object sender) {
        if (databaseOutline == null) {
            infoFmt(LanguageManager.get("4d.select.none", "No outline view to select from."),
                    LanguageManager.get("4d.select.title", "Select all 4D Series"));
            return;
        }
        List<Integer> targetRows = new ArrayList<>();
        int rowCount = databaseOutline.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            try {
                javax.swing.tree.TreePath path = databaseOutline.getPathForRow(i);
                if (path == null) continue;
                Object last = path.getLastPathComponent();
                if (last instanceof com.oculus.jj.model.DicomSeries) {
                    if (isSeries4D((com.oculus.jj.model.DicomSeries) last)) {
                        targetRows.add(i);
                    }
                }
            } catch (Exception e) { /* continue */ }
        }
        if (targetRows.isEmpty()) {
            infoFmt(LanguageManager.get("4d.select.none_found",
                    "No 4D series found in the current outline."),
                    LanguageManager.get("4d.select.title", "Select all 4D Series"));
            return;
        }
        // 複数行選択を有効にして、該当行すべてを選択
        javax.swing.ListSelectionModel selModel = databaseOutline.getSelectionModel();
        try {
            selModel.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            selModel.clearSelection();
            for (Integer r : targetRows) {
                selModel.addSelectionInterval(r, r);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // 元の選択モードに戻す
            selModel.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        }
        String doneMsg = LanguageManager.get("4d.select.done", "Selected %d 4D series.");
        doneMsg = doneMsg.replace("%d", String.valueOf(targetRows.size()));
        infoFmt(doneMsg, LanguageManager.get("4d.select.title", "Select all 4D Series"));
    }

    /**
     * databaseOutline に4Dメニュー項目を追加（Open in 4D / Reparse in 4D / Select all 4D Series）
     * HOROS-20240407準拠: initContextualMenus (10850行目) の "Open Images in 4D" / "Open Reparsed series"
     */
    public void add4DMenuToOutline() {
        if (databaseOutline == null) return;
        javax.swing.JPopupMenu popup = databaseOutline.getComponentPopupMenu();
        if (popup == null) {
            popup = new javax.swing.JPopupMenu();
            databaseOutline.setComponentPopupMenu(popup);
        } else if (popup.getComponentCount() > 0) {
            // 既に追加済みならスキップ（キーで確認）
            for (int i = 0; i < popup.getComponentCount(); i++) {
                if (popup.getComponent(i) instanceof javax.swing.JMenuItem
                        && "menu_4d_open".equals(((javax.swing.JMenuItem) popup.getComponent(i)).getClientProperty("key"))) {
                    return;
                }
            }
        }
        javax.swing.JMenuItem open4D = new javax.swing.JMenuItem(LanguageManager.get("menu_4d_open", "Open in 4D"));
        open4D.putClientProperty("key", "menu_4d_open");
        open4D.addActionListener(e -> movieViewerDICOM(null));
        javax.swing.JMenuItem reparse4D = new javax.swing.JMenuItem(LanguageManager.get("menu_4d_reparse", "Reparse in 4D..."));
        reparse4D.putClientProperty("key", "menu_4d_reparse");
        reparse4D.addActionListener(e -> reparseIn4D(null));
        javax.swing.JMenuItem selectAll4D = new javax.swing.JMenuItem(LanguageManager.get("menu_4d_select_all", "Select all 4D Series"));
        selectAll4D.putClientProperty("key", "menu_4d_select_all");
        selectAll4D.addActionListener(e -> selectAll4DSeries(null));
        popup.addSeparator();
        popup.add(open4D);
        popup.add(reparse4D);
        popup.add(selectAll4D);
    }

    /**
     * ビューワーを開く
     * HOROS-20240407準拠: - (void) viewerDICOM: (id)sender (13372行目)
     */
    private void viewerDICOM(Object sender) {
        // HOROS-20240407準拠: BrowserController.m 13374-13382行目
        // if ([[[NSApplication sharedApplication] currentEvent] modifierFlags] &
        // NSShiftKeyMask)
        // [self viewerDICOMMergeSelection: sender];
        // else
        // {
        // if( [[self window] firstResponder] == databaseOutline)
        // [self newViewerDICOM: nil];
        // else
        // [self newViewerDICOM: sender];
        // }
        // if (isShiftKeyPressed()) {
        // viewerDICOMMergeSelection(sender);
        // return;
        // }

        // HOROS-20240407準拠: firstResponderのチェック
        java.awt.Component focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == databaseOutline) {
            newViewerDICOM(null);
        } else {
            newViewerDICOM(sender);
        }
    }

    /**
     * 新しいビューワーを開く
     * HOROS-20240407準拠: - (void)newViewerDICOM: (id)sender (13388行目)
     */
    private void newViewerDICOM(Object sender) {
        if (database == null) {
            return;
        }

        // HOROS-20240407準拠: BrowserController.m 13392行目
        // NSManagedObject *item = [databaseOutline itemAtRow: [databaseOutline
        // selectedRow]];
        Object item = databaseOutline != null ? databaseOutline.getSelectedItem() : null;

        try {
            // HOROS-20240407準拠: BrowserController.m 13396-13413行目
            // if (sender == Nil &&
            // [[oMatrix selectedCells] count] == 1 &&
            // [[item valueForKey:@"type"] isEqualToString:@"Study"])
            // {
            // NSArray *array = [self databaseSelection];
            // // ... 複数のスタディを開く処理
            // }
            if (sender == null && oMatrix != null && item != null) {
                List<javax.swing.JButton> selectedCells = oMatrix.selectedCells();
                if (selectedCells.size() == 1 && "Study".equals(getItemType(item))) {
                    List<Object> array = databaseSelection();
                    for (Object obj : array) {
                        databaseOpenStudy(obj);
                    }
                    return;
                }
            }

            // HOROS-20240407準拠: BrowserController.m 13414-13424行目
            // else
            // {
            // if( [matrixViewArray count] > [[oMatrix selectedCell] tag] && [self
            // isUsingExternalViewer: [matrixViewArray objectAtIndex: [[oMatrix
            // selectedCell] tag]]] == NO)
            // {
            // dontUpdatePreviewPane = YES;
            // [self viewerDICOMInt: NO dcmFile: [self databaseSelection] viewer: nil];
            // dontUpdatePreviewPane = NO;
            // [self previewSliderAction: nil];
            // }
            // }
            if (oMatrix != null && matrixViewArray != null) {
                javax.swing.JButton selectedCell = oMatrix.selectedCell();
                if (selectedCell != null) {
                    Object tagObj = selectedCell.getClientProperty("tag");
                    if (tagObj instanceof Integer) {
                        int cellTag = (Integer) tagObj;
                        if (cellTag >= 0 && cellTag < matrixViewArray.size()) {
                            // if (!isUsingExternalViewer(matrixViewArray.get(cellTag))) {
                            dontUpdatePreviewPane = true;
                            viewerDICOMInt(false, databaseSelection(), null);
                            dontUpdatePreviewPane = false;
                            previewSliderAction(null);
                            // }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ビューワーを開く（内部処理）
     * HOROS-20240407準拠: - (void) viewerDICOMInt:(BOOL) movieViewer dcmFile:(NSArray
     * *)selectedLines viewer:(ViewerController*) viewer tileWindows: (BOOL)
     * tileWindows protocol: (NSDictionary*) protocol (13205行目)
     */
    private void viewerDICOMInt(boolean movieViewer, List<Object> selectedLines, ViewerController viewer) {
        viewerDICOMInt(movieViewer, selectedLines, viewer, true, null);
    }

    /**
     * ビューワーを開く（内部処理）
     * HOROS-20240407準拠: - (void) viewerDICOMInt:(BOOL) movieViewer dcmFile:(NSArray
     * *)selectedLines viewer:(ViewerController*) viewer tileWindows: (BOOL)
     * tileWindows protocol: (NSDictionary*) protocol (13205行目)
     */
    private void viewerDICOMInt(boolean movieViewer, List<Object> selectedLines, ViewerController viewer,
            boolean tileWindows, Object protocol) {
        // HOROS-20240407準拠: BrowserController.m 13207行目
        // if( [selectedLines count] == 0) return;
        if (selectedLines == null || selectedLines.isEmpty()) {
            return;
        }

        if (database == null) {
            return;
        }

        try {
            // HOROS-20240407準拠: BrowserController.m 13213行目
            // NSManagedObject *selectedLine = [selectedLines objectAtIndex: 0];
            Object selectedLine = selectedLines.get(0);

            // HOROS-20240407準拠: BrowserController.m 13218-13223行目
            // NSArray *cells = [oMatrix selectedCells];
            // if( [cells count] == 0 && [[oMatrix cells] count] > 0)
            // {
            // cells = [NSArray arrayWithObject: [[oMatrix cells] objectAtIndex: 0]];
            // }
            List<javax.swing.JButton> cells = oMatrix != null ? oMatrix.selectedCells() : new ArrayList<>();
            if (cells.isEmpty() && oMatrix != null) {
                javax.swing.JButton firstCell = oMatrix.selectedCell();
                if (firstCell != null) {
                    cells = java.util.Arrays.asList(firstCell);
                }
            }

            // HOROS-20240407準拠: BrowserController.m 13225-13228行目
            // if( [[selectedLine valueForKey:@"type"] isEqualToString: @"Series"])
            // [[AppController sharedAppController] addStudyToRecentStudiesMenu:
            // [[selectedLine valueForKey: @"study"] objectID]];
            // else
            // [[AppController sharedAppController] addStudyToRecentStudiesMenu:
            // selectedLine.objectID];

            // HOROS-20240407準拠: BrowserController.m 13234-13252行目
            // 選択された画像のみを開く処理
            String selectedLineType = getItemType(selectedLine);
            if (cells.size() > 1 && "Series".equals(selectedLineType)) {
                List<Object> curList = childrenArray(selectedLine, false);
                List<Object> selectedFilesList = new ArrayList<>();

                for (javax.swing.JButton c : cells) {
                    Object tagObj = c.getClientProperty("tag");
                    if (tagObj instanceof Integer) {
                        int tag = (Integer) tagObj;
                        if (tag >= 0 && tag < curList.size()) {
                            selectedFilesList.add(curList.get(tag));
                        }
                    }
                }

                openViewerFromImages(java.util.Arrays.asList(selectedFilesList), movieViewer, viewer, false);
            } else {
                // HOROS-20240407準拠: BrowserController.m 13254-13319行目
                // シリーズを開く処理
                List<List<Object>> toOpenArray = new ArrayList<>();

                if (cells.size() == 1 && selectedLines.size() > 1) {
                    // 1つのサムネイルが選択され、複数の行が選択されている場合
                    for (Object curFile : selectedLines) {
                        List<Object> loadList = null;

                        if ("Study".equals(getItemType(curFile))) {
                            // 現在は基本的な実装のみ
                        } else if ("Series".equals(getItemType(curFile))) {
                            loadList = childrenArray(curFile, true);
                        }

                        if (loadList != null && !loadList.isEmpty()) {
                            toOpenArray.add(loadList);
                        }
                    }
                } else {
                    // 複数のサムネイルが選択されている場合
                    for (javax.swing.JButton cell : cells) {
                        Object tagObj = cell.getClientProperty("tag");
                        if (tagObj instanceof Integer) {
                            int tag = (Integer) tagObj;
                            if (matrixViewArray != null && tag >= 0 && tag < matrixViewArray.size()) {
                                Object curFile = matrixViewArray.get(tag);
                                List<Object> loadList = null;

                                if ("Image".equals(getItemType(curFile))) {
                                    loadList = childrenArray(selectedLine, true);
                                } else if ("Series".equals(getItemType(curFile))) {
                                    loadList = childrenArray(curFile, true);
                                }

                                if (loadList != null && !loadList.isEmpty()) {
                                    toOpenArray.add(loadList);
                                }
                            }
                        }
                    }
                }

                processOpenViewerDICOMFromArray(toOpenArray, movieViewer, viewer);
            }

            // HOROS-20240407準拠: BrowserController.m 13222-13346行目
            // tileWindowsの処理
            if (tileWindows) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ビューワーを開く（配列から）
     * HOROS-20240407準拠: - (void) processOpenViewerDICOMFromArray:(NSArray*)
     * toOpenArray movie:(BOOL) movieViewer viewer: (ViewerController*) viewer
     * (12587行目)
     */
    private void processOpenViewerDICOMFromArray(List<List<Object>> toOpenArray, boolean movieViewer,
            ViewerController viewer) {
        // HOROS-20240407準拠: BrowserController.m 12587行目以降
        for (List<Object> loadList : toOpenArray) {
            openViewerFromImages(java.util.Arrays.asList(loadList), movieViewer, viewer, false);
        }
        autoFuseVisiblePETCT();
    }

    private void autoFuseVisiblePETCT() {
        if (!com.oculus.jj.config.AppPreferences.getBool("pet.autoFuse", true)) return;
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            javax.swing.SwingUtilities.invokeLater(this::autoFuseVisiblePETCT);
            return;
        }

        List<ViewerController> viewers = new ArrayList<>(ViewerController.getDisplayed2DViewers());
        java.util.List<DicomPix[]> pairs = new java.util.ArrayList<>();
        for (ViewerController controller : viewers) {
            DCMView view = controller.getImageView();
            if (view == null) continue;
            List<DicomPix> pixels = view.getDcmPixList();
            if (pixels == null) continue;
            for (DicomPix ct : pixels) {
                if (ct == null || !isCTModality(ct.getModality()) || ct.hasFusion()) continue;
                DicomPix pet = findPetOrSpectFor(ct, viewers);
                if (pet != null) pairs.add(new DicomPix[]{ct, pet});
            }
        }
        if (pairs.isEmpty()) return;

        com.oculus.jj.model.FusionImageProcessor.FusionMode mode =
                com.oculus.jj.config.AppPreferences.getFusionMode();
        double weight = com.oculus.jj.config.AppPreferences.getFusionWeight();
        new javax.swing.SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                for (DicomPix[] pair : pairs) {
                    DicomPix ct = pair[0];
                    DicomPix pet = pair[1];
                    if (ct.getBufferedImage() == null || pet.getBufferedImage() == null) continue;
                    ct.setFusionOverlay(pet, weight, mode);
                }
                return null;
            }

            @Override protected void done() {
                for (ViewerController controller : viewers) {
                    DCMView view = controller.getImageView();
                    if (view != null) view.repaint();
                }
            }
        }.execute();
    }

    /**
     * ビューワーを開く（画像から）
     * HOROS-20240407準拠: - (ViewerController*) openViewerFromImages:(NSArray*)
     * toOpenArray movie:(BOOL) movieViewer viewer:(ViewerController*) viewer
     * keyImagesOnly:(BOOL) keyImages tryToFlipData:(BOOL) tryToFlipData (12005行目)
     */
    private ViewerController openViewerFromImages(List<List<Object>> toOpenArray, boolean movieViewer,
            ViewerController viewer, boolean keyImages) {
        return openViewerFromImages(toOpenArray, movieViewer, viewer, keyImages, false);
    }

    /**
     * ビューワーを開く（画像から）
     * HOROS-20240407準拠: - (ViewerController*) openViewerFromImages:(NSArray*)
     * toOpenArray movie:(BOOL) movieViewer viewer:(ViewerController*) viewer
     * keyImagesOnly:(BOOL) keyImages tryToFlipData:(BOOL) tryToFlipData (12005行目)
     */
    private ViewerController openViewerFromImages(List<List<Object>> toOpenArray, boolean movieViewer,
            ViewerController viewer, boolean keyImages, boolean tryToFlipData) {
        // HOROS-20240407準拠: BrowserController.m 12007行目
        // unsigned long *memBlockSize = calloc( [toOpenArray count], sizeof (unsigned
        // long));
        if (toOpenArray == null || toOpenArray.isEmpty()) {
            return null;
        }

        // HOROS-20240407準拠: BrowserController.m 12028-12060行目
        // (1) keyImagesの処理
        if (keyImages) {
            List<List<Object>> keyImagesToOpenArray = new ArrayList<>();
            try {
                for (List<Object> loadList : toOpenArray) {
                    List<Object> keyImagesArray = new ArrayList<>();
                    for (Object image : loadList) {
                        if (image instanceof com.oculus.jj.model.DicomImage) {
                            // com.oculus.jj.model.DicomImage dicomImage =
                            // (com.oculus.jj.model.DicomImage) image;
                            // if (dicomImage.isKeyImage()) {
                            // keyImagesArray.add(image);
                            // }
                        }
                    }
                    if (!keyImagesArray.isEmpty()) {
                        keyImagesToOpenArray.add(keyImagesArray);
                    }
                }
                if (!keyImagesToOpenArray.isEmpty()) {
                    toOpenArray = keyImagesToOpenArray;
                } else {
                    // 現在はすべての画像を開く
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // HOROS-20240407準拠: BrowserController.m 12062-12072行目
        // AUTOHIDEMATRIXの処理（スキップ）
        // openSubSeriesの処理（スキップ）

        // HOROS-20240407準拠: BrowserController.m 12074-12084行目
        // 画像の検証
        for (List<Object> r : toOpenArray) {
            if (!r.isEmpty()) {
                Object lastObj = r.get(r.size() - 1);
                if (!(lastObj instanceof com.oculus.jj.model.DicomImage)) {
                    return null;
                }
            }
        }

        // HOROS-20240407準拠: BrowserController.m 12228-12461行目
        // 画像の読み込みとViewerControllerの作成
        ViewerController createdViewer = viewer;

        // HOROS-20240407準拠: 大量にウィンドウを開く際のパフォーマンス向上のため、タイル配置を一時的に抑制
        ViewerController.setInhibitTiling(true);

        try {
            for (List<Object> loadList : toOpenArray) {
                if (loadList == null || loadList.isEmpty()) {
                    continue;
                }

                // HOROS-20240407準拠: BrowserController.m 12336-12339行目
                // dateOpenedの更新
                Object firstFile = loadList.get(0);
                if (firstFile instanceof com.oculus.jj.model.DicomImage) {
                    // com.oculus.jj.model.DicomImage dicomImage =
                    // (com.oculus.jj.model.DicomImage) firstFile;
                    // dicomImage.getSeries().setDateOpened(new Date());
                    // dicomImage.getSeries().getStudy().setDateOpened(new Date());
                }

                // HOROS-20240407準拠: BrowserController.m 12341-12386行目
                // DCMPixの作成（簡易実装）
                List<com.oculus.jj.model.DicomPix> viewerPix = new ArrayList<>();
                List<Object> correspondingObjects = new ArrayList<>();

                for (Object curFile : loadList) {
                    if (curFile instanceof com.oculus.jj.model.DicomImage) {
                        com.oculus.jj.model.DicomImage dicomImage = (com.oculus.jj.model.DicomImage) curFile;
                        try {
                            // HOROS-20240407準拠: DCMPixの作成 (キャッシュがあれば再利用)
                            com.oculus.jj.model.DicomPix dcmPix = dicomImage.getCachedDicomPix();
                            if (dcmPix == null) {
                                dcmPix = new com.oculus.jj.model.DicomPix(
                                        dicomImage,
                                        dicomImage.getFrameID() != null ? dicomImage.getFrameID() : 0,
                                        loadList.size());
                                dicomImage.setCachedDicomPix(dcmPix);
                            }
                            viewerPix.add(dcmPix);
                            correspondingObjects.add(curFile);
                        } catch (Exception e) {

                            e.printStackTrace();
                        }
                    }
                }

                if (viewerPix.isEmpty()) {
                    continue;
                }

                // HOROS-20240407準拠: BrowserController.m 12409-12461行目
                // ViewerControllerの作成または再利用
                if (movieViewer) {
                    if (createdViewer == null) {
                        if (viewer != null) {
                            createdViewer = viewer;
                        } else {
                            createdViewer = new ViewerController();
                        }
                    } else {
                    }
                } else {
                    if (viewer != null) {
                        // HOROS-20240407準拠: BrowserController.m 12441-12448行目
                        // reuse of existing viewer
                        // viewer.changeImageData(viewerPix, correspondingObjects, null, false);
                        // viewer.startLoadImageThread();
                        createdViewer = viewer;
                    } else {
                        // HOROS-20240407準拠: BrowserController.m 12450-12459行目
                        // creation of new viewer
                        // createdViewer = [[ViewerController alloc] initWithPix:viewerPix[0] withFiles:
                        // [NSMutableArray arrayWithArray: correspondingObjects] withVolume:volumeData];
                        // [createdViewer showWindowTransition];
                        // [createdViewer startLoadImageThread];
                        createdViewer = new ViewerController();
                        // HOROS-20240407準拠: ViewerController.m 7620行目 -
                        // initWithPix:withFiles:withVolume:
                        // viewerPix[0]とcorrespondingObjectsをList<List<Object>>にラップ
                        List<List<Object>> viewerPixList = new ArrayList<>();
                        viewerPixList.add(new ArrayList<>(viewerPix));
                        List<List<Object>> correspondingObjectsList = new ArrayList<>();
                        correspondingObjectsList.add(new ArrayList<>(correspondingObjects));
                        createdViewer.initWithPix(viewerPixList, correspondingObjectsList, null);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            // HOROS-20240407準拠: タイル配置を再開し、一括で配置を実行
            ViewerController.setInhibitTiling(false);
            ViewerController.tileWindows();
        }

        return createdViewer;
    }

    /**
     * プレビューアニメーション実行
     * HOROS-20240407準拠: - (void)previewPerformAnimation: (id)sender (9239行目)
     */
    private void previewPerformAnimation() {
        // HOROS-20240407準拠: BrowserController.m 9245-9251行目
        // if( [[AppController sharedAppController] isSessionInactive] ||
        // waitForRunningProcess)
        // return;
        // if( _database == nil) return;
        // if( animationCheck.state == NSOffState) return;
        if (database == null) {
            return;
        }
        if (animationCheck == null || !animationCheck.isSelected()) {
            return;
        }

        // HOROS-20240407準拠: BrowserController.m 9253-9254行目
        // if( self.window.isKeyWindow == NO) return;
        // if( animationSlider.isEnabled == NO) return;
        if (!isActive()) {
            return;
        }
        if (animationSlider == null || !animationSlider.isEnabled()) {
            return;
        }

        // HOROS-20240407準拠: BrowserController.m 9256-9261行目
        // int pos = animationSlider.intValue;
        // pos++;
        // if( pos > animationSlider.maxValue) pos = 0;
        // [animationSlider setIntValue: pos];
        // [self previewSliderAction: nil];
        int pos = animationSlider.getValue();
        pos++;
        int maxValue = animationSlider.getMaximum();
        if (pos > maxValue) {
            pos = 0;
        }
        animationSlider.setValue(pos);
        previewSliderAction(null);
    }

    /**
     * 比較スタディを検索
     * HOROS-20240407準拠: - (void) searchForComparativeStudies: (id) studySelectedID
     * (4709行目)
     * HOROS-20240407準拠: BrowserController.m 4709-4716行目
     */
    private void searchForComparativeStudies(Object studySelectedID) {

        if (database == null || studySelectedID == null) {

            return;
        }

        try {
            // HOROS-20240407準拠: subSearchForComparativeStudiesを呼び出す
            List<Object> studies = subSearchForComparativeStudies(studySelectedID);

            // HOROS-20240407準拠: refreshComparativeStudiesをメインスレッドで呼び出す
            SwingUtilities.invokeLater(() -> {

                refreshComparativeStudies(studies);
            });
        } catch (Exception e) {

            e.printStackTrace();
            // エラーが発生した場合は空のリストを設定
            SwingUtilities.invokeLater(() -> {
                refreshComparativeStudies(new ArrayList<>());
            });
        }
    }

    /**
     * 比較スタディをサブ検索
     * HOROS-20240407準拠: - (NSArray*) subSearchForComparativeStudies: (id)
     * studySelectedID
     * (4536行目)
     */
    private List<Object> subSearchForComparativeStudies(Object studySelectedID) {

        List<Object> result = new ArrayList<>();

        if (database == null || studySelectedID == null) {

            return result;
        }

        try {
            // HOROS-20240407準拠: studySelectedIDからスタディを取得
            if (!(studySelectedID instanceof com.oculus.jj.model.DicomStudy)) {

                return result;
            }

            com.oculus.jj.model.DicomStudy studySelected = (com.oculus.jj.model.DicomStudy) studySelectedID;

            // HOROS-20240407準拠: 患者UIDを取得
            String patientUID = getPatientUID(studySelected);

            if (patientUID == null || patientUID.isEmpty()) {

                return result;
            }

            // HOROS-20240407準拠: 同じ患者UIDを持つスタディを検索
            // HOROS-20240407準拠: BrowserController.m 4569行目 - ローカルデータベースから検索
            List<com.oculus.jj.model.DicomStudy> allStudies = database.getAllStudies();

            if (allStudies == null || allStudies.isEmpty()) {

                return result;
            }

            for (com.oculus.jj.model.DicomStudy study : allStudies) {
                String studyPatientUID = study.getPatientUID();
                if (studyPatientUID != null && studyPatientUID.equalsIgnoreCase(patientUID)) {
                    result.add(study);
                }
            }

            // HOROS-20240407準拠: 日付でソート（降順）
            result.sort((a, b) -> {
                if (a instanceof com.oculus.jj.model.DicomStudy &&
                        b instanceof com.oculus.jj.model.DicomStudy) {
                    com.oculus.jj.model.DicomStudy studyA = (com.oculus.jj.model.DicomStudy) a;
                    com.oculus.jj.model.DicomStudy studyB = (com.oculus.jj.model.DicomStudy) b;
                    java.time.LocalDateTime dateA = studyA.getDate();
                    java.time.LocalDateTime dateB = studyB.getDate();
                    if (dateA == null && dateB == null) {
                        return 0;
                    } else if (dateA == null) {
                        return 1;
                    } else if (dateB == null) {
                        return -1;
                    } else {
                        return dateB.compareTo(dateA); // 降順
                    }
                }
                return 0;
            });
        } catch (Exception e) {
            // エラーが発生した場合は空のリストを返す
            // デバッグ用: エラーをログ出力（本番環境では削除）
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 比較スタディを更新
     * HOROS-20240407準拠: - (void) refreshComparativeStudies: (NSArray*) newStudies
     * (4747行目)
     */
    private void refreshComparativeStudies(List<Object> newStudies) {

        if (comparativeTable == null) {

            return;
        }

        try {
            // HOROS-20240407準拠: comparativeStudiesを設定
            comparativeStudies = newStudies != null ? new ArrayList<>(newStudies) : new ArrayList<>();

            // HOROS-20240407準拠: comparativeTableのモデルを更新
            javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) comparativeTable
                    .getModel();

            // モデルをクリア
            int rowCount = model.getRowCount();

            if (rowCount > 0) {
                model.setRowCount(0);
            }

            // HOROS-20240407準拠: 各スタディをテーブルに追加
            // HOROS-20240407準拠: BrowserController.m 11369-11393行目
            // セルレンダラーで表示内容を制御するため、ここではstudyオブジェクトを直接追加
            for (Object study : comparativeStudies) {
                if (study instanceof com.oculus.jj.model.DicomStudy) {
                    // HOROS-20240407準拠: セルレンダラーがスタディ情報を表示するため、studyオブジェクトを直接追加
                    // セルレンダラーのvalueパラメータから直接DicomStudyを取得できるようにする
                    model.addRow(new Object[] { study });

                }
            }

            // HOROS-20240407準拠: [comparativeTable reloadData]相当の処理
            // モデル変更を通知
            model.fireTableDataChanged();

            // HOROS-20240407準拠: テーブルを再描画（サムネイルも更新される）
            comparativeTable.revalidate();
            comparativeTable.repaint();

            // HOROS-20240407準拠: HISTORYパネルのサムネイルを更新
            // DBリストで選択されているスタディと同じ患者UIDを持つスタディのサムネイルを表示
            SwingUtilities.invokeLater(() -> {
                comparativeTable.repaint();
            });

            // HOROS-20240407準拠: 現在選択されているスタディと同じstudyInstanceUIDを持つ行を選択
            Object item = databaseOutline.getSelectedItem();
            if (item != null) {
                Object studySelected = getStudyFromItem(item);
                if (studySelected != null) {
                    String studyInstanceUID = getStudyInstanceUID(studySelected);
                    if (studyInstanceUID != null) {
                        int index = findStudyIndexInComparativeStudies(studyInstanceUID);
                        if (index >= 0 && index < comparativeTable.getRowCount()) {
                            comparativeTable.setRowSelectionInterval(index, index);
                            comparativeTable.scrollRectToVisible(
                                    comparativeTable.getCellRect(index, 0, true));
                            // HOROS-20240407準拠: 選択行の背景色を確実に表示するため、再描画
                            // SwingUtilities.invokeLaterで遅延して再描画することで、
                            // セルレンダラーが正しく呼ばれるようにする
                            SwingUtilities.invokeLater(() -> {
                                comparativeTable.repaint();
                            });
                        } else if (comparativeTable.getRowCount() > 0) {
                            // 見つからない場合は最初の行を選択
                            comparativeTable.setRowSelectionInterval(0, 0);
                            // HOROS-20240407準拠: 選択行の背景色を確実に表示するため、再描画
                            SwingUtilities.invokeLater(() -> {
                                comparativeTable.repaint();
                            });
                        }
                    }

                    // HOROS-20240407準拠: BrowserController.m 6739-6756行目
                    // 選択された行（previousItem）と同じ患者UIDを持つDBリスト行の背景色を変更するため、
                    // databaseOutlineを再描画
                    // HOROS-20240407準拠: willDisplayCellでpreviousItemと比較するため、
                    // previousItemが設定されていれば自動的に背景色が変更される
                    // HOROS-20240407準拠: databaseOutlineを再描画してセルレンダラーを呼ぶ
                    SwingUtilities.invokeLater(() -> {
                        if (databaseOutline != null) {
                            databaseOutline.repaint();
                        }
                    });
                }
            }
        } catch (Exception e) {
            // エラーが発生した場合はスキップ
            // デバッグ用: エラーをログ出力（本番環境では削除）
            e.printStackTrace();
        }
    }

    /**
     * ROIとキー画像ボタンをリセット
     * HOROS-20240407準拠: - (void) resetROIsAndKeysButton (4170行目)
     */
    private void resetROIsAndKeysButton() {
        // HOROS-20240407準拠: ROIとキー画像ボタンのリセット実装
    }

    /**
     * アルバムを更新
     * HOROS-20240407準拠: - (void)refreshAlbums (3571行目)
     */
    public void refreshAlbums() {
        // 無限ループ防止
        if (isRefreshingAlbums)
            return;
        isRefreshingAlbums = true;

        try {
            if (database != null) {
                if (_computingNumberOfStudiesForAlbums) {
                    refreshAlbumsPending = true;
                } else {
                    // アルバムテーブルを更新
                    SwingUtilities.invokeLater(this::updateAlbumTable);

                    Thread thread = new Thread(this::_computeNumberOfStudiesForAlbumsThread);
                    thread.setName("Compute Albums...");
                    ThreadsManager.defaultManager().addThreadAndStart(thread);
                }
            }
        } finally {
            isRefreshingAlbums = false;
        }
    }

    /**
     * アルバム更新を遅延
     * HOROS-20240407準拠: - (void)delayedRefreshAlbums (3565行目)
     */
    private void _computeNumberOfStudiesForAlbumsThread() {
        // HOROS-20240407準拠: すでに計算中の場合は何もしない（refreshAlbumsでチェック済みだが念のため）
        _computingNumberOfStudiesForAlbums = true;

        Thread currentThread = Thread.currentThread();
        currentThread.setName("Compute Albums...");
        ThreadsManager.defaultManager().addThreadAndStart(currentThread);

        try {
            if (database != null) {
                List<com.oculus.jj.model.DicomStudy> allStudies = database.getAllStudies();
                allStudiesAlbumCount = allStudies.size();
                for (com.oculus.jj.model.DicomAlbum album : database.getAlbums()) {
                    int count = Boolean.TRUE.equals(album.isSmartAlbum())
                            ? album.evaluate(allStudies).size()
                            : album.getStudies().size();
                    album.setNumberOfStudies(count);
                }
            }

        } catch (Exception e) {
            // エラーは無視
        } finally {
            _computingNumberOfStudiesForAlbums = false;

            // アルバムテーブルを最終更新して計算値を反映
            SwingUtilities.invokeLater(this::updateAlbumTable);

            // 待機中の更新があれば実行
            if (refreshAlbumsPending) {
                refreshAlbumsPending = false;
                SwingUtilities.invokeLater(this::refreshAlbums);
            }
        }
    }

    /**
     * アルバム配列を取得
     * HOROS-20240407準拠: - (NSArray*) albumArray (11276行目)
     * 最初の要素は常に"Database"アルバム（全スタディを表示）
     */
    private List<com.oculus.jj.model.DicomAlbum> getAlbumArray() {
        List<com.oculus.jj.model.DicomAlbum> result = new ArrayList<>();

        if (database == null) {
            return result;
        }

        // HOROS-20240407準拠: 最初の要素は"Database"アルバム
        // [[NSArray arrayWithObject:[NSDictionary dictionaryWithObject:
        // NSLocalizedString(@"Database", nil) forKey:@"name"]]
        // arrayByAddingObjectsFromArray:[self albumsInDatabase]]
        com.oculus.jj.model.DicomAlbum databaseAlbum = new com.oculus.jj.model.DicomAlbum("Database");
        databaseAlbum.setSmartAlbum(false);
        databaseAlbum.setNumberOfStudies(allStudiesAlbumCount);
        result.add(databaseAlbum);

        // HOROS-20240407準拠: その後にデータベースのアルバムを追加
        result.addAll(database.getAlbums());

        return result;
    }

    /**
     * アルバム名でアルバムを選択
     * HOROS-20240407準拠: - (void) selectAlbumWithName: (NSString*) name (2863行目)
     */
    public void selectAlbumWithName(String name) {
        if (albumTable == null || database == null)
            return;

        List<com.oculus.jj.model.DicomAlbum> albumArray = getAlbumArray();
        for (int i = 0; i < albumArray.size(); i++) {
            com.oculus.jj.model.DicomAlbum album = albumArray.get(i);
            if (name.equals(album.getName())) {
                // HOROS-20240407準拠: [albumTable selectRowIndexes:[NSIndexSet
                // indexSetWithIndex:...] byExtendingSelection:NO];
                albumTable.setRowSelectionInterval(i, i);
                albumTable.scrollRectToVisible(albumTable.getCellRect(i, 0, true));
                break;
            }
        }
    }

    /**
     * データベースを更新
     * HOROS-20240407準拠: - (void)refreshDatabase: (id)sender (3585行目)
     */
    public void refreshDatabase(Object sender) {
        // 無限ループ防止: 既に更新中の場合はスキップ
        if (isRefreshingOutline || isRefreshingAlbums || isUpdatingAlbumTable)
            return;

        // HOROS-20240407準拠: if( [[AppController sharedAppController] isSessionInactive]
        // || waitForRunningProcess) return;

        // HOROS-20240407準拠: if( _database == nil) return;
        if (database == null)
            return;

        // HOROS-20240407準拠: if( DatabaseIsEdited) return;
        if (DatabaseIsEdited)
            return;

        // HOROS-20240407準拠: if( [databaseOutline editedRow] != -1) return;

        List<com.oculus.jj.model.DicomAlbum> albumArray = getAlbumArray();

        // HOROS-20240407準拠: if( albumTable.selectedRow >= [albumArray count]) return;
        int selectedRow = albumTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= albumArray.size())
            return;

        // HOROS-20240407準拠: if( [[[albumArray objectAtIndex: albumTable.selectedRow]
        // valueForKey:@"smartAlbum"] boolValue] == YES)
        com.oculus.jj.model.DicomAlbum selectedAlbum = albumArray.get(selectedRow);
        if (selectedAlbum != null && Boolean.TRUE.equals(selectedAlbum.isSmartAlbum())) {
            try {
                // HOROS-20240407準拠: [self outlineViewRefresh];
                outlineViewRefresh();
                // HOROS-20240407準拠: [self refreshAlbums];
                refreshAlbums();
            } catch (Exception e) {
                // HOROS-20240407準拠: @catch (NSException * e) { N2LogExceptionWithStackTrace(e);
                // }
                // e.printStackTrace(); // デバッグ用（コメントアウト）
            }
        } else {
            // HOROS-20240407準拠: 通常のアルバムの場合
            // [self refreshAlbums];
            refreshAlbums();
            // HOROS-20240407準拠: [databaseOutline reloadData];
            if (databaseOutline != null) {
                SwingUtilities.invokeLater(() -> {
                    outlineViewRefresh(); // outlineViewArrayを更新
                    // HOROS-20240407準拠: reloadData相当の処理
                    if (databaseOutline.getTreeTableModel() != null) {
                        org.jdesktop.swingx.treetable.TreeTableModel model = databaseOutline.getTreeTableModel();
                        if (model instanceof DatabaseOutlineView.DatabaseOutlineTreeTableModel) {
                            DatabaseOutlineView.DatabaseOutlineTreeTableModel treeTableModel = (DatabaseOutlineView.DatabaseOutlineTreeTableModel) model;
                            treeTableModel.fireTreeStructureChanged();
                        }
                    }
                    databaseOutline.repaint();
                });
            }
            // HOROS-20240407準拠: [comparativeTable reloadData];
            if (comparativeTable != null && comparativeStudies != null) {
                SwingUtilities.invokeLater(() -> {
                    refreshComparativeStudies(comparativeStudies);
                });
            }
        }
    }

    /**
     * メニューバーの初期化
     * HOROS-20240407準拠: MainMenu.xibからメニュー構造を実装
     * HOROS-20240407準拠: MainMenu.xib 69-397行目（Fileメニュー）
     */
    private void initializeMenuBar() {
        String langCode = com.oculus.jj.config.AppPreferences.getAppLanguage().equals("ja") ? "ja" : "en";
        java.util.Locale locale = new java.util.Locale(langCode);
        MenuResourceLoader.initialize(locale);
        javax.swing.JMenuBar menuBar = new javax.swing.JMenuBar();

        // ========== Fileメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib 69行目 <menuItem title="File" id="848">
        javax.swing.JMenu fileMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_file"));

        // Show Database Window
        // HOROS-20240407準拠: MainMenu.xib 72行目
        javax.swing.JMenuItem showDatabaseItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_show_db", "Show Database Window"));
        showDatabaseItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, 0));
        showDatabaseItem.addActionListener(e -> {
            handleShowDatabaseWindow(e);
        });
        fileMenu.add(showDatabaseItem);

        // Toggle Albums & Sources drawer
        // HOROS-20240407準拠: MainMenu.xib 77行目
        javax.swing.JMenuItem drawerToggleItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_toggle_drawer", "Toggle Albums & Sources drawer"));
        drawerToggleItem.addActionListener(e -> drawerToggle(null));
        fileMenu.add(drawerToggleItem);

        // Toggle History drawer
        // HOROS-20240407準拠: MainMenu.xib 83行目
        javax.swing.JMenuItem comparativeToggleItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_history"));
        comparativeToggleItem.addActionListener(e -> comparativeToggle(null));
        fileMenu.add(comparativeToggleItem);

        fileMenu.addSeparator();

        // Preferences...
        // HOROS-20240407準拠: Preferences window access
        javax.swing.JMenuItem prefMenuItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_file_preferences"));
        prefMenuItem.addActionListener(e -> PreferencesController.showFrame(this));
        fileMenu.add(prefMenuItem);

        fileMenu.addSeparator();

        // New Database Folder...
        // HOROS-20240407準拠: MainMenu.xib 92行目
        javax.swing.JMenuItem newDatabaseFolderItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_new_db_folder", "New Database Folder..."));
        newDatabaseFolderItem.addActionListener(e -> createDatabaseFolder(null));
        fileMenu.add(newDatabaseFolderItem);

        // Open Database Folder...
        // HOROS-20240407準拠: MainMenu.xib 97行目
        javax.swing.JMenuItem openDatabaseFolderItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_open_db_folder", "Open Database Folder..."));
        openDatabaseFolderItem.addActionListener(e -> openDatabase(null));
        fileMenu.add(openDatabaseFolderItem);

        fileMenu.addSeparator();

        // Albums サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 105行目
        javax.swing.JMenu albumsMenu = new javax.swing.JMenu(MenuResourceLoader.getString("file_albums"));

        // Add an album...
        // HOROS-20240407準拠: MainMenu.xib 109行目
        javax.swing.JMenuItem addAlbumItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_add_album", "Add an album..."));
        addAlbumItem.addActionListener(e -> addAlbum(null));
        albumsMenu.add(addAlbumItem);

        // Add a smart album...
        // HOROS-20240407準拠: MainMenu.xib 115行目
        javax.swing.JMenuItem addSmartAlbumItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_add_smart_album", "Add a smart album..."));
        addSmartAlbumItem.addActionListener(e -> addSmartAlbum(null));
        albumsMenu.add(addSmartAlbumItem);

        javax.swing.JMenuItem addSelectedStudiesItem = new javax.swing.JMenuItem(
            LanguageManager.get("menu_file_add_selected_to_album", "Add selected studies to album..."));
        addSelectedStudiesItem.addActionListener(e -> addSelectedStudiesToAlbum(null));
        albumsMenu.add(addSelectedStudiesItem);

        // Delete selected album
        // HOROS-20240407準拠: MainMenu.xib 121行目
        javax.swing.JMenuItem deleteAlbumItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_delete_album", "Delete selected album"));
        deleteAlbumItem.addActionListener(e -> {
            int selectedRow = albumTable.getSelectedRow();
            List<com.oculus.jj.model.DicomAlbum> albums = getAlbumArray();
            if (selectedRow >= 0 && selectedRow < albums.size()) {
                com.oculus.jj.model.DicomAlbum selectedAlbum = albums.get(selectedRow);
                if (selectedAlbum == null || "Database".equals(selectedAlbum.getName())) return;
                int answer = JOptionPane.showConfirmDialog(BrowserController.this,
                        String.format(LanguageManager.get("db.album.remove.confirm",
                                "Delete album '%s'?"), selectedAlbum.getName()),
                        LanguageManager.get("db.album.remove.title", "Delete Album"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (answer == JOptionPane.YES_OPTION) removeAlbum(selectedAlbum);
            }
        });
        albumsMenu.add(deleteAlbumItem);

        albumsMenu.addSeparator();

        // Save albums...
        // HOROS-20240407準拠: MainMenu.xib 128行目
        javax.swing.JMenuItem saveAlbumsItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_save_albums", "Save albums..."));
        saveAlbumsItem.addActionListener(e -> saveAlbums(null));
        albumsMenu.add(saveAlbumsItem);

        // Import albums...
        // HOROS-20240407準拠: MainMenu.xib 134行目
        javax.swing.JMenuItem importAlbumsItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_import_albums", "Import albums..."));
        importAlbumsItem.addActionListener(e -> addAlbums(null));
        albumsMenu.add(importAlbumsItem);

        albumsMenu.addSeparator();

        // Create default albums...
        // HOROS-20240407準拠: MainMenu.xib 141行目 / BrowserController.m:1379 defaultAlbums:
        // [self.database addDefaultAlbums] → albumsInDatabase → refreshAlbums
        javax.swing.JMenuItem defaultAlbumsItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_default_albums", "Create default albums..."));
        defaultAlbumsItem.addActionListener(e -> createDefaultAlbums());
        albumsMenu.add(defaultAlbumsItem);

        fileMenu.add(albumsMenu);

        fileMenu.addSeparator();

        // Report サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 153行目
        javax.swing.JMenu reportMenu = new javax.swing.JMenu(MenuResourceLoader.getString("file_report"));

        // Open report
        // HOROS-20240407準拠: MainMenu.xib 156行目
        javax.swing.JMenuItem openReportItem = new javax.swing.JMenuItem(LanguageManager.get("menu_report_open", "Open report"));
        openReportItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, 0));
        openReportItem.addActionListener(e -> generateReport(null));
        reportMenu.add(openReportItem);

        // Delete report
        // HOROS-20240407準拠: MainMenu.xib 161行目
        javax.swing.JMenuItem deleteReportItem = new javax.swing.JMenuItem(LanguageManager.get("menu_report_delete", "Delete report"));
        deleteReportItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, 0));
        deleteReportItem.addActionListener(e -> deleteReport(null));
        reportMenu.add(deleteReportItem);

        reportMenu.addSeparator();

        // Convert to PDF...
        // HOROS-20240407準拠: MainMenu.xib 167行目
        javax.swing.JMenuItem convertToPDFItem = new javax.swing.JMenuItem(LanguageManager.get("menu_report_to_pdf", "Convert to PDF..."));
        convertToPDFItem.addActionListener(e -> convertReportToPDF(null));
        reportMenu.add(convertToPDFItem);

        // Convert to DICOM PDF
        // HOROS-20240407準拠: MainMenu.xib 173行目
        javax.swing.JMenuItem convertToDICOMSRItem = new javax.swing.JMenuItem(LanguageManager.get("menu_report_to_dicom_pdf", "Convert to DICOM PDF"));
        convertToDICOMSRItem.addActionListener(e -> convertReportToDICOMSR(null));
        reportMenu.add(convertToDICOMSRItem);

        fileMenu.add(reportMenu);

        fileMenu.addSeparator();

        // Import サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 185行目
        javax.swing.JMenu importMenu = new javax.swing.JMenu(MenuResourceLoader.getString("file_import"));

        // Import Files...
        // HOROS-20240407準拠: MainMenu.xib 188行目
        javax.swing.JMenuItem importFilesItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_import_files"));
        importFilesItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, 0));
        importFilesItem.addActionListener(e -> importFilesFromDialog());
        importMenu.add(importFilesItem);

        // Import Image from URL...
        // HOROS-20240407準拠: MainMenu.xib 198行目
        javax.swing.JMenuItem importURLItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_import_image_url"));
        importURLItem.addActionListener(e -> {
            importImageFromURL();
        });
        importMenu.add(importURLItem);

        // Import Raw Data...
        // HOROS-20240407準拠: MainMenu.xib 209行目
        javax.swing.JMenuItem importRawDataItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_import_raw_data"));
        importRawDataItem.addActionListener(e -> importRawData(null));
        importMenu.add(importRawDataItem);

        fileMenu.add(importMenu);

        // Export サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 222行目
        javax.swing.JMenu exportMenu = new javax.swing.JMenu(MenuResourceLoader.getString("file_export"));

        // Export to DICOM Network Node
        // HOROS-20240407準拠: MainMenu.xib 225行目
        javax.swing.JMenuItem export2PACSItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_network_export_pacs"));
        export2PACSItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, 0));
        export2PACSItem.addActionListener(e -> {
            exportToPACS();
        });
        exportMenu.add(export2PACSItem);

        // Export to Movie
        // HOROS-20240407準拠: MainMenu.xib 230行目
        javax.swing.JMenuItem exportMovieItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_export_movie"));
        exportMovieItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, 0));
        exportMovieItem.addActionListener(e -> {
            MoviePlaybackPanel.showMoviePanel(BrowserController.this, getActiveDCMViewForEdit());
        });
        exportMenu.add(exportMovieItem);

        // Export to JPEG
        // HOROS-20240407準拠: MainMenu.xib 235行目
        javax.swing.JMenuItem exportJPEGItem = new javax.swing.JMenuItem(LanguageManager.get("menu_export_jpeg", "Export to JPEG"));
        exportJPEGItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, 0));
        exportJPEGItem.addActionListener(e -> {
            exportToJPEG();
        });
        exportMenu.add(exportJPEGItem);

        // Export to Raw
        // HOROS-20240407準拠: MainMenu.xib 240行目
        javax.swing.JMenuItem exportRAWItem = new javax.swing.JMenuItem(LanguageManager.get("menu_export_raw", "Export to Raw"));
        exportRAWItem.addActionListener(e -> {
            showExportRawDialog();
        });
        exportMenu.add(exportRAWItem);

        // Export to TIFF
        // HOROS-20240407準拠: MainMenu.xib 245行目
        javax.swing.JMenuItem exportTIFFItem = new javax.swing.JMenuItem(LanguageManager.get("menu_export_tiff", "Export to TIFF"));
        exportTIFFItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        exportTIFFItem.addActionListener(e -> {
            exportToTIFF();
        });
        exportMenu.add(exportTIFFItem);

        // Export to DICOM file(s)
        // HOROS-20240407準拠: MainMenu.xib 251行目
        javax.swing.JMenuItem exportDICOMFileItem = new javax.swing.JMenuItem(LanguageManager.get("menu_export_dicom_file", "Export to DICOM file(s)"));
        exportDICOMFileItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, 0));
        exportDICOMFileItem.addActionListener(e -> {
            exportToDICOMFile();
        });
        exportMenu.add(exportDICOMFileItem);

        // Export to Email
        // HOROS-20240407準拠: MainMenu.xib 256行目
        javax.swing.JMenuItem exportEmailItem = new javax.swing.JMenuItem(LanguageManager.get("menu_export_email", "Export to Email"));
        exportEmailItem.addActionListener(e -> sendMail(null));
        exportMenu.add(exportEmailItem);

        // Export to Photos
        // HOROS-20240407準拠: MainMenu.xib 261行目
        javax.swing.JMenuItem exportPhotosItem = new javax.swing.JMenuItem(LanguageManager.get("menu_export_photos", "Export to Photos"));
        exportPhotosItem.addActionListener(e -> {
            exportToPhotos();
        });
        exportMenu.add(exportPhotosItem);

        // Export Displayed Database List as...
        // HOROS-20240407準拠: MainMenu.xib 266行目
        javax.swing.JMenuItem saveDBListItemItem = new javax.swing.JMenuItem(LanguageManager.get("menu_export_db_list", "Export Displayed Database List as..."));
        saveDBListItemItem.addActionListener(e -> saveDBListAs(null));
        exportMenu.add(saveDBListItemItem);

        fileMenu.add(exportMenu);

        fileMenu.addSeparator();

        // Burn...
        // HOROS-20240407準拠: MainMenu.xib 277行目
        javax.swing.JMenuItem burnItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("file_burn"));
        burnItem.addActionListener(e -> {
            BurnerDialog.showBurnerDialog(BrowserController.this);
        });
        fileMenu.add(burnItem);

        // Anonymize...
        // HOROS-20240407準拠: MainMenu.xib 282行目
        javax.swing.JMenuItem anonymizeItem = new javax.swing.JMenuItem(LanguageManager.get("menu_file_anonymize", "Anonymize..."));
        anonymizeItem.addActionListener(e -> anonymizeDICOM(null));
        fileMenu.add(anonymizeItem);

        // Copy Linked Files to Database Folder
        // HOROS-20240407準拠: MainMenu.xib 287行目
        javax.swing.JMenuItem copyToDBFolderItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_copy_to_db_folder"));
        copyToDBFolderItem.addActionListener(e -> copyToDBFolder(null));
        fileMenu.add(copyToDBFolderItem);

        // Search
        // HOROS-20240407準拠: MainMenu.xib 292行目
        javax.swing.JMenuItem searchItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("file_search"));
        searchItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        searchItem.addActionListener(e -> searchField(null));
        fileMenu.add(searchItem);

        // Merge Selected Studies
        // HOROS-20240407準拠: MainMenu.xib 298行目
        javax.swing.JMenuItem mergeStudiesItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_merge_studies"));
        mergeStudiesItem.addActionListener(e -> mergeStudies(null));
        fileMenu.add(mergeStudiesItem);

        // Delete Selected Exam
        // HOROS-20240407準拠: MainMenu.xib 303行目
        javax.swing.JMenuItem deleteExamItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_delete_exam"));
        deleteExamItem.addActionListener(e -> delItem(null));
        fileMenu.add(deleteExamItem);

        // Meta-Data...
        // HOROS-20240407準拠: MainMenu.xib 308行目
        javax.swing.JMenuItem metaDataItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("file_meta_data"));
        metaDataItem.addActionListener(e -> viewXML(null));
        fileMenu.add(metaDataItem);

        fileMenu.addSeparator();

        // Compress selected DICOM files
        // HOROS-20240407準拠: MainMenu.xib 316行目
        javax.swing.JMenuItem compressFilesItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_compress"));
        compressFilesItem.addActionListener(e -> compressSelectedFiles(null));
        fileMenu.add(compressFilesItem);

        // Decompress selected DICOM files
        // HOROS-20240407準拠: MainMenu.xib 321行目
        javax.swing.JMenuItem decompressFilesItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_decompress"));
        decompressFilesItem.addActionListener(e -> decompressSelectedFiles(null));
        fileMenu.add(decompressFilesItem);

        fileMenu.addSeparator();

        // Rebuild Entire Database...
        // HOROS-20240407準拠: MainMenu.xib 329行目
        javax.swing.JMenuItem rebuildDatabaseItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_rebuild_db"));
        rebuildDatabaseItem.addActionListener(e -> ReBuildDatabaseSheet(null));
        fileMenu.add(rebuildDatabaseItem);

        // Rebuild SQL Index File...
        // HOROS-20240407準拠: MainMenu.xib 347行目 / BrowserController.m:2530 rebuildSQLFile:
        javax.swing.JMenuItem rebuildSQLItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_rebuild_sql"));
        rebuildSQLItem.addActionListener(e -> rebuildSQLIndexFile());
        fileMenu.add(rebuildSQLItem);

        // Rebuild Selected Thumbnails
        // HOROS-20240407準拠: MainMenu.xib 365行目
        javax.swing.JMenuItem rebuildThumbnailsItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_rebuild_thumbnails"));
        rebuildThumbnailsItem.addActionListener(e -> rebuildThumbnails(null));
        fileMenu.add(rebuildThumbnailsItem);

        fileMenu.addSeparator();

        // Close Window
        // HOROS-20240407準拠: MainMenu.xib 373行目
        javax.swing.JMenuItem closeWindowItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_close_window"));
        closeWindowItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, 0));
        closeWindowItem.addActionListener(e -> closeMenuOwnerWindow(e));
        fileMenu.add(closeWindowItem);

        fileMenu.addSeparator();

        // Page Setup...
        // HOROS-20240407準拠: MainMenu.xib 381行目
        javax.swing.JMenuItem pageSetupItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_file_page_setup"));
        pageSetupItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        pageSetupItem.addActionListener(e -> showPageSetup());
        fileMenu.add(pageSetupItem);

        // Print...
        // HOROS-20240407準拠: MainMenu.xib 386行目
        javax.swing.JMenuItem printItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_file_print"));
        printItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, 0));
        printItem.addActionListener(e -> showPrintDialog());
        fileMenu.add(printItem);

        // DICOM Print...
        // HOROS-20240407準拠: MainMenu.xib 391行目
        javax.swing.JMenuItem dicomPrintItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_file_dicom_print"));
        dicomPrintItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.ALT_DOWN_MASK));
        dicomPrintItem.addActionListener(e -> printDICOM(dicomPrintItem));
        fileMenu.add(dicomPrintItem);

        menuBar.add(fileMenu);

        // ========== Networkメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib 400行目
        javax.swing.JMenu networkMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_network"));

        // Export to DICOM Network Node
        // HOROS-20240407準拠: MainMenu.xib 404行目
        javax.swing.JMenuItem networkExport2PACSItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_network_export_pacs"));
        networkExport2PACSItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, 0));
        networkExport2PACSItem.addActionListener(e -> {
            exportToPACS();
        });
        networkMenu.add(networkExport2PACSItem);

        networkMenu.addSeparator();

        // Query / Retrieve Window...
        // HOROS-20240407準拠: MainMenu.xib 412行目
        javax.swing.JMenuItem queryDICOMItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_network_query_retrieve"));
        queryDICOMItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        queryDICOMItem.addActionListener(e -> {
            QueryController.getInstance().refreshNodes();
            QueryController.getInstance().setVisible(true);
        });
        networkMenu.add(queryDICOMItem);

        // Query Selected Patient from Q&R Window...
        // HOROS-20240407準拠: MainMenu.xib 418行目
        javax.swing.JMenuItem querySelectedStudyItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_network_query_selected", "Query Selected Patient from Q&R Window..."));
        querySelectedStudyItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R,
                java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        querySelectedStudyItem.addActionListener(e -> querySelectedStudy(null));
        networkMenu.add(querySelectedStudyItem);

        networkMenu.addSeparator();

        // Auto Query / Retrieve Window...
        // HOROS-20240407準拠: MainMenu.xib 427行目
        javax.swing.JMenuItem autoQueryItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_network_auto_query"));
        autoQueryItem.addActionListener(e -> {
            QueryController query = QueryController.getInstance();
            query.refreshNodes();
            query.configureAutoQuery(BrowserController.this);
        });
        networkMenu.add(autoQueryItem);

        // Auto Query / Retrieve Refresh
        // HOROS-20240407準拠: MainMenu.xib 433行目
        javax.swing.JMenuItem autoQueryRefreshItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_network_auto_query_refresh"));
        autoQueryRefreshItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, 0));
        autoQueryRefreshItem.addActionListener(e -> QueryController.getInstance().refreshResults(BrowserController.this));
        networkMenu.add(autoQueryRefreshItem);

        networkMenu.addSeparator();

        // Retrieve selected PACS On-Demand studies
        // HOROS-20240407準拠: MainMenu.xib 441行目
        javax.swing.JMenuItem retrievePODItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_network_retrieve_pod"));
        retrievePODItem.addActionListener(e -> retrieveSelectedPODStudies(null));
        networkMenu.add(retrievePODItem);

        // Refresh PACS On-Demand results
        // HOROS-20240407準拠: MainMenu.xib 447行目
        javax.swing.JMenuItem refreshPODItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_network_refresh_pod"));
        refreshPODItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, 0));
        refreshPODItem.addActionListener(e -> refreshPACSOnDemandResults(null));
        networkMenu.add(refreshPODItem);

        networkMenu.addSeparator();

        // Abort Incoming DICOM Processes (Store-SCU)
        // HOROS-20240407準拠: MainMenu.xib 455行目
        javax.swing.JMenuItem abortStoreSCUItem = new javax.swing.JMenuItem(
                LanguageManager.get("net_abort_store_scu", "Abort Incoming DICOM Processes (Store-SCU)"));
        abortStoreSCUItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A,
                java.awt.event.InputEvent.ALT_DOWN_MASK));
        abortStoreSCUItem.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(BrowserController.this,
                LanguageManager.get("pacs.abort.confirm",
                    "Abort active incoming DICOM transfers? These transfers cannot be resumed."),
                LanguageManager.get("net_title", "Network"), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
            int aborted = com.oculus.jj.model.DicomService.getInstance().abortIncomingAssociations();
            infoFmt(String.format(LanguageManager.get("pacs.abort.done", "Aborted %d incoming association(s)."), aborted),
                LanguageManager.get("net_title", "Network"));
        });
        networkMenu.add(abortStoreSCUItem);

        networkMenu.addSeparator();

        // Network Logs
        // HOROS-20240407準拠: MainMenu.xib 464行目
        javax.swing.JMenuItem networkLogsItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_network_logs"));
        networkLogsItem.addActionListener(e -> showLogWindow(null));
        networkMenu.add(networkLogsItem);

        networkMenu.addSeparator();

        // Preferences...
        // HOROS-20240407準拠: AppController.m (showPreferencePanel)
        javax.swing.JMenuItem preferencesItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_file_preferences"));
        preferencesItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_COMMA,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        preferencesItem.addActionListener(e -> {
            PreferencesController.getInstance().setVisible(true);
        });
        networkMenu.add(preferencesItem);
        // PACS Query Settings
        javax.swing.JMenuItem pacsQuerySettingsItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_pacs_settings"));
        pacsQuerySettingsItem.addActionListener(e -> {
            com.oculus.jj.ui.PACSQuerySettingsDialog.showPACSQuerySettingsDialog(BrowserController.this);
        });
        networkMenu.add(pacsQuerySettingsItem);

        networkMenu.addSeparator();

        menuBar.add(networkMenu);

        // ========== Editメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib 472行目
        javax.swing.JMenu editMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_edit"));

        // Undo
        // HOROS-20240407準拠: MainMenu.xib 475行目
        javax.swing.JMenuItem undoItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_edit_undo"));
        undoItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, 0));
        undoItem.addActionListener(e -> {
            DCMView view = getActiveDCMViewForEdit();
            if (view == null)
                return;
            initAnnotationUndo();
            annotationUndoManager.setTarget(view);
            java.util.List<com.oculus.jj.model.ROI> savedRois = new java.util.ArrayList<>();
            if (view.curRoiList != null) {
                for (com.oculus.jj.model.ROI r : view.curRoiList) {
                    if (r.getPoints() != null && !r.getPoints().isEmpty())
                        savedRois.add(r);
                }
            }
            annotationUndoManager.pushSnapshot(savedRois, new java.util.ArrayList<>());
            if (annotationUndoManager.canUndo()) {
                com.oculus.jj.model.ROI restored = annotationUndoManager.undo();
                if (restored != null)
                    view.repaint();
            }
        });

        // Redo
        // HOROS-20240407準拠: MainMenu.xib 480行目
        javax.swing.JMenuItem redoItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_edit_redo"));
        redoItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        redoItem.addActionListener(e -> {
            DCMView view = getActiveDCMViewForEdit();
            if (view == null)
                return;
            initAnnotationUndo();
            annotationUndoManager.setTarget(view);
            if (annotationUndoManager.canRedo()) {
                com.oculus.jj.model.ROI restored = annotationUndoManager.redo();
                if (restored != null)
                    view.repaint();
            }
        });

        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();

        // Cut
        // HOROS-20240407準拠: MainMenu.xib 488行目
        javax.swing.JMenuItem cutItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_edit_cut"));
        cutItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, 0));
        editMenu.add(cutItem);

        // Copy
        // HOROS-20240407準拠: MainMenu.xib 493行目
        javax.swing.JMenuItem copyItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_edit_copy"));
        copyItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, 0));
        copyItem.addActionListener(e -> copy(null));
        editMenu.add(copyItem);

        // Paste
        // HOROS-20240407準拠: MainMenu.xib 498行目
        javax.swing.JMenuItem pasteItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_edit_paste"));
        pasteItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, 0));
        pasteItem.addActionListener(e -> paste(null));
        editMenu.add(pasteItem);

        // Select All
        // HOROS-20240407準拠: MainMenu.xib 503行目
        javax.swing.JMenuItem selectAllItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_edit_select_all"));
        selectAllItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, 0));
        selectAllItem.addActionListener(e -> {
            DCMView view = getActiveDCMViewForEdit();
            if (view != null && view.curRoiList != null) {
                for (int i = 0; i < view.curRoiList.size(); i++) {
                    view.selectedRoi = view.curRoiList.get(i);
                }
                view.repaint();
            }
        });

        editMenu.add(selectAllItem);

        menuBar.add(editMenu);

        // ========== Formatメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib 511行目
        javax.swing.JMenu formatMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_format"));

        // Customize Toolbar...
        // HOROS-20240407準拠: MainMenu.xib 514行目
        javax.swing.JMenuItem customizeToolbarItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_format_customize_toolbar"));
        customizeToolbarItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(BrowserController.this, LanguageManager.get("fmt_customize_pending", "Customize Toolbar - not yet implemented"), LanguageManager.get("fmt_title", "Format"),
                    JOptionPane.INFORMATION_MESSAGE);
        });
        formatMenu.add(customizeToolbarItem);

        // Fullscreen
        // HOROS-20240407準拠: MainMenu.xib 519行目
        javax.swing.JMenuItem fullscreenItem = new javax.swing.JMenuItem(LanguageManager.get("fmt_fullscreen", "Fullscreen"));
        fullscreenItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, 0));
        fullscreenItem.addActionListener(e -> {
            JFrame mainFrame = resolveMenuOwnerFrame(e);
            if (mainFrame.getExtendedState() == JFrame.MAXIMIZED_BOTH) {
                mainFrame.setExtendedState(JFrame.NORMAL);
            } else {
                mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
        });
        formatMenu.add(fullscreenItem);

        formatMenu.addSeparator();

        // Font サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 527行目
        javax.swing.JMenu fontMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_font"));

        // Show Fonts
        // HOROS-20240407準拠: MainMenu.xib 530行目
        javax.swing.JMenuItem showFontsItem = new javax.swing.JMenuItem(LanguageManager.get("fmt_show_fonts", "Show Fonts"));
        showFontsItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(BrowserController.this,
                    LanguageManager.get("font_prefs_msg", "Font settings are managed in Preferences > Annotations"), LanguageManager.get("font_title", "Font"), JOptionPane.INFORMATION_MESSAGE);
        });
        fontMenu.add(showFontsItem);

        // Bold
        // HOROS-20240407準拠: MainMenu.xib 536行目
        javax.swing.JMenuItem boldItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_font_bold"));
        boldItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B, 0));
        boldItem.addActionListener(e -> {
            DCMView view = getActiveDCMViewForEdit();
            if (view == null) {
                JOptionPane.showMessageDialog(BrowserController.this, LanguageManager.get("font_need_viewer", "Open a viewer to use Bold"), LanguageManager.get("font_title", "Font"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(BrowserController.class);
            boolean currentBold = prefs.getBoolean("annotationFontBold", false);
            prefs.putBoolean("annotationFontBold", !currentBold);
            try {
                prefs.flush();
            } catch (Exception ex) {
            }
        });
        fontMenu.add(boldItem);
        // HOROS-20240407準拠: MainMenu.xib 541行目
        javax.swing.JMenuItem italicItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_font_italic"));
        italicItem.addActionListener(e -> {
            DCMView view = getActiveDCMViewForEdit();
            if (view != null) {
                JOptionPane.showMessageDialog(BrowserController.this, LanguageManager.get("font_italic_pending", "Italic annotation text - implementation pending"),
                        LanguageManager.get("font_title", "Font"), JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(BrowserController.this, LanguageManager.get("font_need_viewer_italic", "Open a viewer to use Italic"), LanguageManager.get("font_title", "Font"),
                        JOptionPane.WARNING_MESSAGE);
            }
        });
        fontMenu.add(italicItem);

        fontMenu.addSeparator();

        javax.swing.JMenuItem biggerItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_font_bigger"));
        biggerItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PLUS, 0));
        // Bigger
        biggerItem.addActionListener(e -> {
            DCMView view = getActiveDCMViewForEdit();
            if (view == null) {
                JOptionPane.showMessageDialog(BrowserController.this, LanguageManager.get("font_need_viewer_bigger", "Open a viewer to use Bigger"), LanguageManager.get("font_title", "Font"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(BrowserController.class);
            int currentSize = prefs.getInt("annotationFontSize", 12);
            int newSize = Math.min(currentSize + 2, 48);
            prefs.putInt("annotationFontSize", newSize);
            try {
                prefs.flush();
            } catch (Exception ex) {
            }
        });
        fontMenu.add(biggerItem);
        javax.swing.JMenuItem smallerItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_font_smaller"));
        smallerItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, 0));
        // Smaller
        // HOROS-20240407準拠: MainMenu.xib 555行目
        smallerItem.addActionListener(e -> {
            DCMView view = getActiveDCMViewForEdit();
            if (view == null) {
                JOptionPane.showMessageDialog(BrowserController.this, LanguageManager.get("font_need_viewer_smaller", "Open a viewer to use Smaller"), LanguageManager.get("font_title", "Font"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(BrowserController.class);
            int currentSize = prefs.getInt("annotationFontSize", 12);
            int newSize = Math.max(currentSize - 2, 6);
            prefs.putInt("annotationFontSize", newSize);
            try {
                prefs.flush();
            } catch (Exception ex) {
            }
        });
        fontMenu.add(smallerItem);
        fontMenu.addSeparator();

        // Show Colors
        // HOROS-20240407準拠: MainMenu.xib 563行目
        javax.swing.JMenuItem showColorsItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_show_colors"));
        showColorsItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        showColorsItem.addActionListener(e -> {
            DCMView view = getActiveDCMViewForEdit();
            if (view != null) {
                JOptionPane.showMessageDialog(BrowserController.this,
                        LanguageManager.get("colors_in_view_menu", "Color/LUT window level settings are in the View menu"), LanguageManager.get("colors_title", "Colors"),
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(BrowserController.this, LanguageManager.get("colors_need_viewer", "Open a viewer to use Show Colors"), LanguageManager.get("colors_title", "Colors"),
                        JOptionPane.WARNING_MESSAGE);
            }
        });
        fontMenu.add(showColorsItem);

        formatMenu.add(fontMenu);

        menuBar.add(formatMenu);

        // ========== 2D Viewerメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib 574行目
        javax.swing.JMenu viewer2DMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_viewer_2d"));

        // Next Series
        // HOROS-20240407準拠: MainMenu.xib 577行目
        javax.swing.JMenuItem nextSeriesItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_next_series"));
        nextSeriesItem.addActionListener(e -> loadSerie(null));
        viewer2DMenu.add(nextSeriesItem);

        // Previous Series
        // HOROS-20240407準拠: MainMenu.xib 582行目
        javax.swing.JMenuItem previousSeriesItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_previous_series"));
        previousSeriesItem.addActionListener(e -> loadSerie(null));
        viewer2DMenu.add(previousSeriesItem);

        // Next Patient
        // HOROS-20240407準拠: MainMenu.xib 587行目
        javax.swing.JMenuItem nextPatientItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_next_patient"));
        nextPatientItem.addActionListener(e -> loadPatient(null));
        viewer2DMenu.add(nextPatientItem);

        // Previous Patient
        // HOROS-20240407準拠: MainMenu.xib 593行目
        javax.swing.JMenuItem previousPatientItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_previous_patient"));
        previousPatientItem.addActionListener(e -> loadPatient(null));
        viewer2DMenu.add(previousPatientItem);

        viewer2DMenu.addSeparator();

        // Convert between BW / RGB サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 607行目
        javax.swing.JMenu convertBWRGBMenu = new javax.swing.JMenu(
                MenuResourceLoader.getString("menu_color_convert_bw_rgb"));

        javax.swing.JMenuItem redToBWItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_color_red_to_bw"));
        redToBWItem.addActionListener(e -> ConvertToBWMenu(0));
        convertBWRGBMenu.add(redToBWItem);

        javax.swing.JMenuItem greenToBWItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_color_green_to_bw"));
        greenToBWItem.addActionListener(e -> ConvertToBWMenu(1));
        convertBWRGBMenu.add(greenToBWItem);

        javax.swing.JMenuItem blueToBWItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_color_blue_to_bw"));
        blueToBWItem.addActionListener(e -> ConvertToBWMenu(2));
        convertBWRGBMenu.add(blueToBWItem);

        javax.swing.JMenuItem rgbToBWItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_color_rgb_to_bw"));
        rgbToBWItem.addActionListener(e -> ConvertToBWMenu(3));
        convertBWRGBMenu.add(rgbToBWItem);

        convertBWRGBMenu.addSeparator();

        javax.swing.JMenuItem bwToRedItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_color_bw_to_red"));
        bwToRedItem.addActionListener(e -> ConvertToRGBMenu(0));
        convertBWRGBMenu.add(bwToRedItem);

        javax.swing.JMenuItem bwToGreenItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_color_bw_to_green"));
        bwToGreenItem.addActionListener(e -> ConvertToRGBMenu(1));
        convertBWRGBMenu.add(bwToGreenItem);

        javax.swing.JMenuItem bwToBlueItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_color_bw_to_blue"));
        bwToBlueItem.addActionListener(e -> ConvertToRGBMenu(2));
        convertBWRGBMenu.add(bwToBlueItem);

        javax.swing.JMenuItem bwToRGBItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_color_bw_to_rgb"));
        bwToRGBItem.addActionListener(e -> ConvertToRGBMenu(3));
        convertBWRGBMenu.add(bwToRGBItem);

        viewer2DMenu.add(convertBWRGBMenu);

        // Apply Window Protocol
        // HOROS-20240407準拠: - (IBAction) applyWindowProtocol
        javax.swing.JMenuItem applyWindowProtocolItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_apply_window_protocol"));
        applyWindowProtocolItem.addActionListener(e -> applyWindowProtocol(null));
        viewer2DMenu.add(applyWindowProtocolItem);

        // Apply CLUT
        // HOROS-20240407準拠: - (IBAction) applyCLUT (2D Viewer)
        javax.swing.JMenu clutMenu = new javax.swing.JMenu(
            LanguageManager.get("menu_2d_color_lut", "Color Look Up Table"));
        for (String preset : DicomPix.CLUT_PRESETS) {
            final String name = preset;
            javax.swing.JMenuItem it = new javax.swing.JMenuItem(name);
            it.addActionListener(e -> applyCLUT(name));
            clutMenu.add(it);
        }
        clutMenu.addSeparator();
        javax.swing.JMenuItem noClutItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("clut.none"));
        noClutItem.addActionListener(e -> applyCLUT(null));
        clutMenu.add(noClutItem);
        viewer2DMenu.add(clutMenu);

        // Apply Opacity
        // HOROS-20240407準拠: - (IBAction) applyOpacity (2D Viewer)
        javax.swing.JMenu opacityMenu = new javax.swing.JMenu(
            LanguageManager.get("menu_2d_opacity", "Opacity"));
        int[] opacityPercents = { 100, 75, 50, 25 };
        for (int p : opacityPercents) {
            final int pp = p;
            javax.swing.JMenuItem it = new javax.swing.JMenuItem(p + "%");
            it.addActionListener(e -> applyOpacity(pp));
            opacityMenu.add(it);
        }
        viewer2DMenu.add(opacityMenu);

        // Convolution Filters
        // HOROS-20240407準拠: AppController.m (IBAction) ApplyConv: / AddConv: / UpdateConvolutionMenu:
        convolutionMenu = new javax.swing.JMenu(
                MenuResourceLoader.getString("menu_viewer_2d_convolution_filters"));
        viewer2DMenu.add(convolutionMenu);
        rebuildConvolutionMenu();

        // Resample (HOROS-20240407準拠: ViewerController.m resampleDataFromPixArray: - blending sheet tag 11)
        javax.swing.JMenuItem resampleItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_resample"));
        resampleItem.addActionListener(e -> showResampleDialog());
        viewer2DMenu.add(resampleItem);

        // 12-bit Display (HOROS-20240407準拠: ViewerController.m:22770-22775 enable12Bit:)
        // [imageView setIsLUT12Bit:(tag==0)]; [imageView updateImage];
        // 備考: HOROS 標準ビルドでは canDisplay12Bit=NO（AppController.m:129）のため、
        // 本フラグは state 保持のみ（dormant）。DCMPix.m:10060-10067 参照。
        javax.swing.JCheckBoxMenuItem item12Bit = new javax.swing.JCheckBoxMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_12bit"));
        item12Bit.addActionListener(e -> {
            DCMView view = getActiveDCMViewForEdit();
            if (view == null) return;
            view.setIsLUT12Bit(item12Bit.isSelected());
            view.repaint();
        });
        viewer2DMenu.add(item12Bit);

        // Workspace State
        // HOROS-20240407準拠: Save/Load Workspace State
        javax.swing.JMenu workspaceMenu = new javax.swing.JMenu(
                MenuResourceLoader.getString("menu_workspace_state"));
        javax.swing.JMenuItem saveWorkspaceItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_workspace_save"));
        saveWorkspaceItem.addActionListener(e -> saveWorkspaceState(null));
        workspaceMenu.add(saveWorkspaceItem);
        javax.swing.JMenuItem loadWorkspaceItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_workspace_load"));
        loadWorkspaceItem.addActionListener(e -> loadWorkspaceState(null));
        workspaceMenu.add(loadWorkspaceItem);
        javax.swing.JMenuItem resetWorkspaceItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_workspace_reset"));
        resetWorkspaceItem.addActionListener(e -> resetWorkspaceState(null));
        workspaceMenu.add(resetWorkspaceItem);
        viewer2DMenu.add(workspaceMenu);

        viewer2DMenu.addSeparator();

        // Reset Image View
        // HOROS-20240407準拠: MainMenu.xib 659行目
        javax.swing.JMenuItem resetImageViewItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_reset_view"));
        resetImageViewItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, 0));
        resetImageViewItem.addActionListener(e -> resetImage(null));
        viewer2DMenu.add(resetImageViewItem);

        // Revert series
        // HOROS-20240407準拠: MainMenu.xib 664行目
        javax.swing.JMenuItem revertSeriesItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_revert"));
        revertSeriesItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        revertSeriesItem.addActionListener(e -> revertSeries(null));
        viewer2DMenu.add(revertSeriesItem);

        // Scale サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 669行目
        javax.swing.JMenu scaleMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_scale"));

        javax.swing.JMenuItem noRescaleItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_scale_no_rescale"));
        noRescaleItem.addActionListener(e -> actualSize(null));
        scaleMenu.add(noRescaleItem);

        javax.swing.JMenuItem actualSizeItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_view_actual_size"));
        actualSizeItem.addActionListener(e -> realSize(null));
        scaleMenu.add(actualSizeItem);

        javax.swing.JMenuItem scaleToFitItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_scale_scale_to_fit"));
        scaleToFitItem.addActionListener(e -> scaleToFit(null));
        scaleMenu.add(scaleToFitItem);

        viewer2DMenu.add(scaleMenu);

        // Sort By サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 694行目
        javax.swing.JMenu sortByMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_sort_by"));

        javax.swing.JMenuItem instanceAscItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_sort_by_instance_asc"));
        instanceAscItem.addActionListener(e -> sortSeriesByValue(null));
        sortByMenu.add(instanceAscItem);

        javax.swing.JMenuItem instanceDescItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_sort_by_instance_desc"));
        instanceDescItem.addActionListener(e -> sortSeriesByValue(null));
        sortByMenu.add(instanceDescItem);

        sortByMenu.addSeparator();

        javax.swing.JMenuItem sliceLocAscItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_sort_by_slice_loc_asc"));
        sliceLocAscItem.addActionListener(e -> sortSeriesByValue(null));
        sortByMenu.add(sliceLocAscItem);

        javax.swing.JMenuItem sliceLocDescItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_sort_by_slice_loc_desc"));
        sliceLocDescItem.addActionListener(e -> sortSeriesByValue(null));
        sortByMenu.add(sliceLocDescItem);

        viewer2DMenu.add(sortByMenu);

        // Orientation サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 726行目
        javax.swing.JMenu orientationMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_orientation"));

        javax.swing.JMenuItem flipHorizontalItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_flip_h"));
        flipHorizontalItem.addActionListener(e -> flipHorizontal(null));
        orientationMenu.add(flipHorizontalItem);

        javax.swing.JMenuItem flipVerticalItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_flip_v"));
        flipVerticalItem.addActionListener(e -> flipVertical(null));
        orientationMenu.add(flipVerticalItem);

        orientationMenu.addSeparator();

        javax.swing.JMenuItem rotate0Item = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_rotate0"));
        rotate0Item.addActionListener(e -> rotate0(null));
        orientationMenu.add(rotate0Item);

        javax.swing.JMenuItem rotate90Item = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_rotate90"));
        rotate90Item.addActionListener(e -> rotate90(null));
        orientationMenu.add(rotate90Item);

        javax.swing.JMenuItem rotate180Item = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_rotate180"));
        rotate180Item.addActionListener(e -> rotate180(null));
        orientationMenu.add(rotate180Item);

        viewer2DMenu.add(orientationMenu);

        // Calibrate Resolution
        // HOROS-20240407準拠: MainMenu.xib 760行目
        javax.swing.JMenuItem calibrateItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_calibrate"));
        calibrateItem.addActionListener(e -> calibrate(null));
        viewer2DMenu.add(calibrateItem);

        // 3D Position Panel
        // HOROS-20240407準拠: MainMenu.xib 765行目
        javax.swing.JMenuItem threeDPanelItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_three_d_panel"));
        threeDPanelItem.addActionListener(e -> threeDPanel(null));
        viewer2DMenu.add(threeDPanelItem);

        // Navigator Panel
        // HOROS-20240407準拠: MainMenu.xib 770行目
        javax.swing.JMenuItem navigatorItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_navigator"));
        navigatorItem.addActionListener(e -> navigator(null));
        viewer2DMenu.add(navigatorItem);

        // Flip Series
        // HOROS-20240407準拠: MainMenu.xib 775行目
        javax.swing.JMenuItem flipSeriesItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_flip_series"));
        flipSeriesItem.addActionListener(e -> flipDataSeries(null));
        viewer2DMenu.add(flipSeriesItem);

        // Use VOI LUT
        // HOROS-20240407準拠: MainMenu.xib 780行目
        useVOILUTItem = new javax.swing.JCheckBoxMenuItem("Use VOI LUT");
        useVOILUTItem.setSelected(com.oculus.jj.config.AppPreferences.isUseVOILUT());
        useVOILUTItem.addActionListener(e -> useVOILUT(null));
        viewer2DMenu.add(useVOILUTItem);

        // Display DICOM Overlays
        // HOROS-20240407準拠: MainMenu.xib 785行目
        javax.swing.JCheckBoxMenuItem displayOverlaysItem = new javax.swing.JCheckBoxMenuItem("Display DICOM Overlays");
        displayOverlaysItem.setSelected(true);
        displayOverlaysItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O,
                java.awt.event.InputEvent.ALT_DOWN_MASK));
        viewer2DMenu.add(displayOverlaysItem);

        // DICOM Meta-Data
        // HOROS-20240407準拠: MainMenu.xib 795行目
        javax.swing.JMenuItem dicomMetaDataItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_meta_data"));
        dicomMetaDataItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        dicomMetaDataItem.addActionListener(e -> viewXML(null));
        viewer2DMenu.add(dicomMetaDataItem);

        // Convert from/to SUV
        // HOROS-20240407準拠: MainMenu.xib 801行目
        javax.swing.JMenuItem convertSUVItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_convert_suv"));
        convertSUVItem.addActionListener(e -> displaySUV(null));
        viewer2DMenu.add(convertSUVItem);

        // Fuse/De-Fuse PET/SPECT - CT
        // HOROS-20240407準拠: MainMenu.xib 807行目
        javax.swing.JMenuItem fuseItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_viewer_2d_fuse"));
        fuseItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, 0));
        fuseItem.addActionListener(e -> blendWindows(null));
        viewer2DMenu.add(fuseItem);

        // Flatten Fused Image
        // HOROS-20240407準拠: MainMenu.xib 812行目
        javax.swing.JMenuItem flattenFusedItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_flatten"));
        flattenFusedItem.addActionListener(e -> mergeFusedImages(null));
        viewer2DMenu.add(flattenFusedItem);

        viewer2DMenu.addSeparator();

        // ========== Subtractionメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib 951行目 (Subtraction サブメニュー全体)
        // subCtrlOnOff / subCtrlNewMask / subSharpen / subCtrlOffset(8方向) /
        // subCtrlSliders(Contrast・Brightness) / subSumSlider
        javax.swing.JMenu subtractionMenu = new javax.swing.JMenu(LanguageManager.get("menu_subtraction", "Subtraction"));

        // Subtract (toggle)
        subCtrlOnOffItem = new javax.swing.JCheckBoxMenuItem(LanguageManager.get("menu_subtraction_subtract", "Subtract"));
        subCtrlOnOffItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SLASH,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        subCtrlOnOffItem.addActionListener(e -> subCtrlOnOff(0));
        subtractionMenu.add(subCtrlOnOffItem);

        // New Mask
        javax.swing.JMenuItem newMaskItem = new javax.swing.JMenuItem(LanguageManager.get("menu_subtraction_new_mask", "New Mask"));
        newMaskItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ASTERISK,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        newMaskItem.addActionListener(e -> subCtrlNewMask(null));
        subtractionMenu.add(newMaskItem);

        // Sharpen (toggle)
        subSharpenItem = new javax.swing.JCheckBoxMenuItem(LanguageManager.get("menu_subtraction_sharpen", "Sharpen"));
        subSharpenItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        subSharpenItem.addActionListener(e -> subSharpen(30));
        subtractionMenu.add(subSharpenItem);

        subtractionMenu.addSeparator();

        // Pixel Shift 8方向 (HOROS: subCtrlOffset: tag 1..9)
        String[] shiftLabels = {
                LanguageManager.get("menu_subtraction_shift_ne", "Pixel Shift NE"),
                LanguageManager.get("menu_subtraction_shift_n", "Pixel Shift N"),
                LanguageManager.get("menu_subtraction_shift_nw", "Pixel Shift NW"),
                LanguageManager.get("menu_subtraction_shift_w", "Pixel Shift W"),
                LanguageManager.get("menu_subtraction_shift_none", "No Pixel Shift"),
                LanguageManager.get("menu_subtraction_shift_e", "Pixel Shift E"),
                LanguageManager.get("menu_subtraction_shift_sw", "Pixel Shift SW"),
                LanguageManager.get("menu_subtraction_shift_s", "Pixel Shift S"),
                LanguageManager.get("menu_subtraction_shift_se", "Pixel Shift SE"),
        };
        int[] shiftTags = { 7, 8, 9, 4, 5, 6, 1, 2, 3 };
        String[] shiftKeys = { "9", "8", "7", "4", "5", "6", "1", "2", "3" };
        for (int i = 0; i < shiftLabels.length; i++) {
            javax.swing.JMenuItem si = new javax.swing.JMenuItem(shiftLabels[i]);
            si.setAccelerator(javax.swing.KeyStroke.getKeyStroke(shiftKeys[i].charAt(0),
                    java.awt.event.InputEvent.CTRL_DOWN_MASK));
            final int tag = shiftTags[i]; si.addActionListener(e -> subCtrlOffset(tag));
            subtractionMenu.add(si);
        }

        subtractionMenu.addSeparator();

        // Contrast / Brightness (HOROS: subCtrlSliders: tag 34-39)
        String[] sliderLabels = {
                LanguageManager.get("menu_subtraction_contrast_down", "Contrast Down"),
                LanguageManager.get("menu_subtraction_contrast_default", "Contrast Default"),
                LanguageManager.get("menu_subtraction_contrast_up", "Contrast Up"),
                LanguageManager.get("menu_subtraction_brightness_down", "Brightness Down"),
                LanguageManager.get("menu_subtraction_brightness_default", "Brightness Default"),
                LanguageManager.get("menu_subtraction_brightness_up", "Brightness Up"),
        };
        int[] sliderTags = { 37, 38, 39, 34, 35, 36 };
        String[] sliderKeys = { "7", "8", "9", "4", "5", "6" };
        for (int i = 0; i < sliderLabels.length; i++) {
            javax.swing.JMenuItem si = new javax.swing.JMenuItem(sliderLabels[i]);
            si.setAccelerator(javax.swing.KeyStroke.getKeyStroke(sliderKeys[i].charAt(0),
                    java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.META_DOWN_MASK));
            final int tag = sliderTags[i]; si.addActionListener(e -> subCtrlSliders(tag));
            subtractionMenu.add(si);
        }

        // Sum (HOROS: subSumSlider: tag 31-33)
        String[] sumLabels = {
                LanguageManager.get("menu_subtraction_sum_less", "Sum Less"),
                LanguageManager.get("menu_subtraction_sum_none", "No Sum"),
                LanguageManager.get("menu_subtraction_sum_more", "Sum More"),
        };
        int[] sumTags = { 31, 32, 33 };
        String[] sumKeys = { "1", "2", "3" };
        for (int i = 0; i < sumLabels.length; i++) {
            javax.swing.JMenuItem si = new javax.swing.JMenuItem(sumLabels[i]);
            si.setAccelerator(javax.swing.KeyStroke.getKeyStroke(sumKeys[i].charAt(0),
                    java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.META_DOWN_MASK));
            final int tag = sumTags[i]; si.addActionListener(e -> subSumSlider(tag));
            subtractionMenu.add(si);
        }

        viewer2DMenu.add(subtractionMenu);

        viewer2DMenu.addSeparator();

        // Propagate Settings Between Series
        // HOROS-20240407準拠: MainMenu.xib 821行目
        javax.swing.JCheckBoxMenuItem propagateSettingsItem = new javax.swing.JCheckBoxMenuItem(
                "Propagate Settings Between Series");
        propagateSettingsItem.setSelected(DCMView.propagateSettings);
        propagateSettingsItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, 0));
        // 有効/無効の切り替え + 有効時オンクリックで即座に伝播
        propagateSettingsItem.addActionListener(e -> {
            boolean was = DCMView.propagateSettings;
            DCMView.propagateSettings = propagateSettingsItem.isSelected();
            if (DCMView.propagateSettings) {
                copySettingsToOthers(null);
            } else {
                // アクセラレータ押下等で再度ONになった場合は状態を元に戻す (再発火ループ防止)
                if (was) {
                    propagateSettingsItem.setSelected(true);
                    DCMView.propagateSettings = true;
                } else {
                    for (ViewerController vc : ViewerController.getDisplayed2DViewers()) {
                        if (vc.getImageView() != null) vc.getImageView().repaint();
                    }
                }
            }
        });
        viewer2DMenu.add(propagateSettingsItem);

        // Don't Propagate WL&WW Between Series
        // HOROS-20240407準拠: MainMenu.xib 831行目
        javax.swing.JCheckBoxMenuItem dontPropagateWLWWItem = new javax.swing.JCheckBoxMenuItem(
                "Don't Propagate WL&WW Between Series");
        dontPropagateWLWWItem.setSelected(false);
        dontPropagateWLWWItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G,
                java.awt.event.InputEvent.ALT_DOWN_MASK));
        viewer2DMenu.add(dontPropagateWLWWItem);

        // Propagate Settings in Current Series
        // HOROS-20240407準拠: MainMenu.xib 838行目
        javax.swing.JMenuItem propagateInSeriesItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_2d_propagate"));
        propagateInSeriesItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        propagateInSeriesItem.addActionListener(e -> switchCopySettingsInSeries(null));
        viewer2DMenu.add(propagateInSeriesItem);

        // Sync Series (Same Study) サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 853行目
        javax.swing.JMenu syncSeriesMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_sync_series"));

        javax.swing.JMenuItem syncOffItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_sync_off"));
        syncOffItem.addActionListener(e -> {
            DCMView.syncro = DCMView.syncroOFF;
            NotificationCenter.defaultCenter().postNotification(NotificationCenter.OsirixUpdateViewNotification, null);
        });
        syncSeriesMenu.add(syncOffItem);

        javax.swing.JMenuItem syncSlicePosItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_sync_slice_pos_abs"));
        syncSlicePosItem.addActionListener(e -> {
            DCMView.syncro = DCMView.syncroLOC;
            NotificationCenter.defaultCenter().postNotification(NotificationCenter.OsirixUpdateViewNotification, null);
        });
        syncSeriesMenu.add(syncSlicePosItem);

        syncSeriesMenu.addSeparator();

        javax.swing.JMenuItem syncSliceIDAbsItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_sync_slice_id_abs"));
        syncSliceIDAbsItem.addActionListener(e -> {
            DCMView.syncro = DCMView.syncroABS;
            NotificationCenter.defaultCenter().postNotification(NotificationCenter.OsirixUpdateViewNotification, null);
        });
        syncSeriesMenu.add(syncSliceIDAbsItem);

        javax.swing.JMenuItem syncSliceIDRatioItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_sync_slice_id_ratio"));
        syncSliceIDRatioItem.addActionListener(e -> {
            DCMView.syncro = DCMView.syncroRatio;
            NotificationCenter.defaultCenter().postNotification(NotificationCenter.OsirixUpdateViewNotification, null);
        });
        syncSeriesMenu.add(syncSliceIDRatioItem);

        javax.swing.JMenuItem syncSliceIDRelItem = new javax.swing.JMenuItem("Slice ID - Relative");
        syncSliceIDRelItem.addActionListener(e -> {
            DCMView.syncro = DCMView.syncroREL;
            NotificationCenter.defaultCenter().postNotification(NotificationCenter.OsirixUpdateViewNotification, null);
        });
        syncSeriesMenu.add(syncSliceIDRelItem);

        syncSeriesMenu.addSeparator();

        javax.swing.JCheckBoxMenuItem limitSameStudyItem = new javax.swing.JCheckBoxMenuItem("Limit to Same Study");
        limitSameStudyItem.addActionListener(e -> alwaysSyncMenu(null));
        syncSeriesMenu.add(limitSameStudyItem);

        viewer2DMenu.add(syncSeriesMenu);

        // Sync Series (Different Studies) at Current Position
        // HOROS-20240407準拠: MainMenu.xib 899行目
        javax.swing.JMenuItem syncDifferentItem = new javax.swing.JMenuItem(
                "Sync Series (Different Studies) at Current Position");
        syncDifferentItem.addActionListener(e -> SyncSeries(null));
        viewer2DMenu.add(syncDifferentItem);

        viewer2DMenu.addSeparator();

        // Key Image
        // HOROS-20240407準拠: MainMenu.xib 907行目
        javax.swing.JMenuItem keyImageItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("file_key_image"));
        keyImageItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K, 0));
        keyImageItem.addActionListener(e -> setKeyImage(0));
        viewer2DMenu.add(keyImageItem);

        // Mark All Images as Key Images
        // HOROS-20240407準拠: MainMenu.xib 912行目
        javax.swing.JMenuItem markAllKeyItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_mark_all_key"));
        markAllKeyItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K,
                java.awt.event.InputEvent.ALT_DOWN_MASK));
        markAllKeyItem.addActionListener(e -> setAllKeyImages(null));
        viewer2DMenu.add(markAllKeyItem);

        // Unmark All Images as Key Images
        // HOROS-20240407準拠: MainMenu.xib 918行目
        javax.swing.JMenuItem unmarkAllKeyItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_unmark_all_key"));
        unmarkAllKeyItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        unmarkAllKeyItem.addActionListener(e -> setAllNonKeyImages(null));
        viewer2DMenu.add(unmarkAllKeyItem);

        // Mark All ROIs Images as Key Images
        // HOROS-20240407準拠: MainMenu.xib 924行目
        javax.swing.JMenuItem markROIsKeyItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_mark_rois_key"));
        markROIsKeyItem.addActionListener(e -> setROIsImagesKeyImages(null));
        viewer2DMenu.add(markROIsKeyItem);

        // Save as DICOM SC and mark as Key Image
        // HOROS-20240407準拠: MainMenu.xib 930行目
        javax.swing.JMenuItem saveDICOMSCItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_save_dicom_sc"));
        saveDICOMSCItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K,
                java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        saveDICOMSCItem.addActionListener(e -> captureAndSetKeyImage(null));
        viewer2DMenu.add(saveDICOMSCItem);

        // Export Key Images as DICOM SC Series
        // HOROS-20240407準拠: BrowserController.m:17815 exportROIAndKeyImagesAsDICOMSeries:
        javax.swing.JMenuItem exportKeySCItem = new javax.swing.JMenuItem(
                LanguageManager.get("export.sc.menu", "Export Key Images as DICOM SC Series"));
        exportKeySCItem.addActionListener(e -> exportKeyImagesAsDICOM(null));
        viewer2DMenu.add(exportKeySCItem);

        // Find Next Key Image
        // HOROS-20240407準拠: MainMenu.xib 936行目
        javax.swing.JMenuItem findNextKeyItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_find_next_key"));
        findNextKeyItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K,
                java.awt.event.InputEvent.CTRL_DOWN_MASK));
        findNextKeyItem.addActionListener(e -> findNextPreviousKeyImage(1));
        viewer2DMenu.add(findNextKeyItem);

        // Find Previous Key Image
        // HOROS-20240407準拠: MainMenu.xib 942行目
        javax.swing.JMenuItem findPrevKeyItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("file_find_prev_key"));
        findPrevKeyItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K,
                java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        findPrevKeyItem.addActionListener(e -> findNextPreviousKeyImage(-1));
        viewer2DMenu.add(findPrevKeyItem);

        javax.swing.JCheckBoxMenuItem seriesListItem = new javax.swing.JCheckBoxMenuItem(
            LanguageManager.get("menu_2d_series_list", "Series List"),
            java.util.prefs.Preferences.userNodeForPackage(ThumbnailsListPanel.class)
                .getBoolean("SeriesListVisible", true));
        seriesListItem.addActionListener(e -> {
            List<ViewerController> viewers = ViewerController.getDisplayedViewers();
            if (!viewers.isEmpty()) viewers.get(viewers.size() - 1).setSeriesListVisible(seriesListItem.isSelected());
        });

        javax.swing.JMenu annotationsMenu = new javax.swing.JMenu(
            LanguageManager.get("menu_2d_annotations", "Annotations"));
        javax.swing.JMenuItem openAnnotationsItem = new javax.swing.JMenuItem(
            LanguageManager.get("menu_2d_annotations_open", "Open ROI Annotations Panel"));
        openAnnotationsItem.addActionListener(e -> AnnotationsPanel.toggle(this, getActiveDCMViewForEdit()));
        annotationsMenu.add(openAnnotationsItem);

        javax.swing.JMenu windowWidthLevelMenu = new javax.swing.JMenu(
            LanguageManager.get("menu_2d_window_width_level", "Window Width & Level"));
        windowWidthLevelMenu.add(applyWindowProtocolItem);
        javax.swing.JMenuItem resetWLWWItem = new javax.swing.JMenuItem(
            LanguageManager.get("menu_2d_reset_wlww", "Reset Window Width & Level"));
        resetWLWWItem.addActionListener(e -> {
            DCMView view = getActiveDCMViewForEdit();
            if (view != null && view.getCurDCM() != null) {
            view.getCurDCM().resetWLWW();
            view.repaint();
            }
        });
        windowWidthLevelMenu.add(resetWLWWItem);

        javax.swing.JMenu clutBarMenu = new javax.swing.JMenu(
            LanguageManager.get("menu_2d_clut_bar", "Color Look Up Table Bar"));
        javax.swing.JMenuItem clutBarUnavailableItem = new javax.swing.JMenuItem(
            LanguageManager.get("menu_2d_clut_bar_unavailable", "Color bar display is not available yet"));
        clutBarUnavailableItem.setEnabled(false);
        clutBarMenu.add(clutBarUnavailableItem);

        javax.swing.JCheckBoxMenuItem zoomParallelItem = new javax.swing.JCheckBoxMenuItem(
            LanguageManager.get("menu_2d_propagate_zoom_parallel",
                "Propagate Zoom Level Only if Planes are Parallel"));
        zoomParallelItem.setEnabled(false);

        // HOROS 2D Viewer order from MainMenu.xib; keep port-specific tools after the reference items.
        viewer2DMenu.removeAll();
        viewer2DMenu.add(nextSeriesItem);
        viewer2DMenu.add(previousSeriesItem);
        viewer2DMenu.add(nextPatientItem);
        viewer2DMenu.add(previousPatientItem);
        viewer2DMenu.addSeparator();
        viewer2DMenu.add(seriesListItem);
        viewer2DMenu.addSeparator();
        viewer2DMenu.add(convertBWRGBMenu);
        viewer2DMenu.add(resetImageViewItem);
        viewer2DMenu.add(revertSeriesItem);
        viewer2DMenu.add(scaleMenu);
        viewer2DMenu.add(sortByMenu);
        viewer2DMenu.add(orientationMenu);
        viewer2DMenu.add(calibrateItem);
        viewer2DMenu.add(threeDPanelItem);
        viewer2DMenu.add(navigatorItem);
        viewer2DMenu.add(flipSeriesItem);
        viewer2DMenu.add(useVOILUTItem);
        viewer2DMenu.add(displayOverlaysItem);
        viewer2DMenu.add(dicomMetaDataItem);
        viewer2DMenu.add(convertSUVItem);
        viewer2DMenu.add(fuseItem);
        viewer2DMenu.add(flattenFusedItem);
        viewer2DMenu.addSeparator();
        viewer2DMenu.add(propagateSettingsItem);
        viewer2DMenu.add(dontPropagateWLWWItem);
        viewer2DMenu.add(propagateInSeriesItem);
        viewer2DMenu.add(zoomParallelItem);
        viewer2DMenu.add(syncSeriesMenu);
        viewer2DMenu.add(syncDifferentItem);
        viewer2DMenu.addSeparator();
        viewer2DMenu.add(keyImageItem);
        viewer2DMenu.add(markAllKeyItem);
        viewer2DMenu.add(unmarkAllKeyItem);
        viewer2DMenu.add(markROIsKeyItem);
        viewer2DMenu.add(saveDICOMSCItem);
        viewer2DMenu.add(findNextKeyItem);
        viewer2DMenu.add(findPrevKeyItem);
        viewer2DMenu.addSeparator();
        viewer2DMenu.add(subtractionMenu);
        viewer2DMenu.add(annotationsMenu);
        viewer2DMenu.add(windowWidthLevelMenu);
        viewer2DMenu.add(clutMenu);
        viewer2DMenu.add(clutBarMenu);
        viewer2DMenu.add(opacityMenu);
        viewer2DMenu.add(convolutionMenu);
        viewer2DMenu.addSeparator();
        viewer2DMenu.add(resampleItem);
        viewer2DMenu.add(item12Bit);
        viewer2DMenu.add(workspaceMenu);

        // 注: 2D Viewerメニューには他にも多くの項目がありますが、主要な項目のみ実装しています
        // 残りの項目（Subtraction、Annotations、Window Width & Level、Color Look Up Table、Image
        // Tiling、Windows Tiling、Save/Load Workspace Stateなど）は後で実装

        menuBar.add(viewer2DMenu);

        // ========== 3D Viewerメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib の 3D Viewer メニュー構成に完全に準拠
        // グループ: MPR系 / 描画系(MIP・VR・SR・Endo) / 操作(Reset・Revert) /
        //          方向ビュー / FlyThru / Curved Path / Scissor / 3Dプリセット
        // 各グループ間はセパレータで区切り、項目の並び・位置を統一する
        viewer3DMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_viewer_3d"));

        // --- Group 1: MPR系 ---
        // HOROS-20240407準拠: MainMenu.xib 1374行目
        javax.swing.JMenuItem mprViewerItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_3d_mpr"));
        mprViewerItem.addActionListener(e -> mprViewer(null));
        viewer3DMenu.add(mprViewerItem);

        // HOROS-20240407準拠: MainMenu.xib 1379行目
        javax.swing.JMenuItem cprViewerItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_3d_curved_mpr"));
        cprViewerItem.addActionListener(e -> cprViewer(null));
        viewer3DMenu.add(cprViewerItem);

        // 2D Orthogonal MPR
        // HOROS-20240407準拠: MainMenu.xib 1384行目
        javax.swing.JMenuItem orthogonalMPRItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_3d_orthogonal_mpr"));
        orthogonalMPRItem.addActionListener(e -> orthogonalMPRViewer(null));
        viewer3DMenu.add(orthogonalMPRItem);

        viewer3DMenu.addSeparator();

        // --- Group 2: 3D描画系ビューア ---
        javax.swing.JMenuItem mipViewerItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_mip", "3D MIP"));
        mipViewerItem.addActionListener(e -> MIPViewer(null));
        viewer3DMenu.add(mipViewerItem);

        javax.swing.JMenuItem vrViewerItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_volume_rendering", "3D Volume Rendering"));
        vrViewerItem.addActionListener(e -> VRViewer(null));
        viewer3DMenu.add(vrViewerItem);

        // HOROS-20240407準拠: 3D Surface Rendering (MainMenu.xib 1402行目)
        javax.swing.JMenuItem surfaceRenderingItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_3d_surface_rendering"));
        surfaceRenderingItem.addActionListener(e -> SRViewer(null));
        viewer3DMenu.add(surfaceRenderingItem);

        // 3D Endoscopy
        javax.swing.JMenuItem endoscopyItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_endoscopy", "3D Endoscopy"));
        endoscopyItem.addActionListener(e -> endoscopyViewer(null));
        viewer3DMenu.add(endoscopyItem);

        viewer3DMenu.addSeparator();

        // --- Group 3: 操作 (Reset / Revert) ---
        // HOROS-20240407準拠: MainMenu.xib 1415行目
        javax.swing.JMenuItem reset3DItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_3d_reset_view"));
        reset3DItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, 0));
        reset3DItem.addActionListener(e -> resetImage(null));
        viewer3DMenu.add(reset3DItem);

        // HOROS-20240407準拠: MainMenu.xib 1420行目
        javax.swing.JMenuItem revert3DItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_viewer_3d_revert_series"));
        revert3DItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        revert3DItem.addActionListener(e -> revertSeries(null));
        viewer3DMenu.add(revert3DItem);

        viewer3DMenu.addSeparator();

        // --- Group 4: 方向ビュー ---
        // HOROS-20240407準拠: coView:/saView:/saViewOpposite:/axView:
        javax.swing.JMenuItem coronalItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_coronal", "Coronal View"));
        coronalItem.addActionListener(e -> openOrientationView(Viewer3DController.OrientationView.CORONAL));
        viewer3DMenu.add(coronalItem);

        javax.swing.JMenuItem leftSagittalItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_left_sagittal", "Left Sagittal View"));
        leftSagittalItem.addActionListener(e -> openOrientationView(Viewer3DController.OrientationView.LEFT_SAGITTAL));
        viewer3DMenu.add(leftSagittalItem);

        javax.swing.JMenuItem rightSagittalItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_right_sagittal", "Right Sagittal View"));
        rightSagittalItem.addActionListener(e -> openOrientationView(Viewer3DController.OrientationView.RIGHT_SAGITTAL));
        viewer3DMenu.add(rightSagittalItem);

        javax.swing.JMenuItem axialItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_axial", "Axial View"));
        axialItem.addActionListener(e -> openOrientationView(Viewer3DController.OrientationView.AXIAL));
        viewer3DMenu.add(axialItem);

        viewer3DMenu.addSeparator();

        // --- Group 5: FlyThru (HOROS: flyThruButtonMenu:) ---
        javax.swing.JMenuItem flyThruAddItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_flythru_add", "Add FlyThru Point"));
        flyThruAddItem.addActionListener(e -> flyThruButtonMenu(0));
        viewer3DMenu.add(flyThruAddItem);

        javax.swing.JMenuItem flyThruRemoveItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_flythru_remove", "Remove FlyThru Point"));
        flyThruRemoveItem.addActionListener(e -> flyThruButtonMenu(1));
        viewer3DMenu.add(flyThruRemoveItem);

        javax.swing.JMenuItem flyThruResetItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_flythru_reset", "Reset FlyThru Path"));
        flyThruResetItem.addActionListener(e -> flyThruButtonMenu(2));
        viewer3DMenu.add(flyThruResetItem);

        viewer3DMenu.addSeparator();

        // --- Group 6: 3D Curved Path サブメニュー ---
        javax.swing.JMenu curvedPathMenu = new javax.swing.JMenu(
                LanguageManager.get("menu_viewer_3d_curved_path", "3D Curved Path"));
        javax.swing.JMenuItem curvedPathLoad = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_curved_path_load", "Load from file..."));
        curvedPathLoad.addActionListener(e -> loadBezierPath(null));
        curvedPathMenu.add(curvedPathLoad);
        javax.swing.JMenuItem curvedPathSave = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_curved_path_save", "Save to file..."));
        curvedPathSave.addActionListener(e -> saveBezierPath(null));
        curvedPathMenu.add(curvedPathSave);
        viewer3DMenu.add(curvedPathMenu);

        viewer3DMenu.addSeparator();

        // --- Group 7: Scissor Editing サブメニュー ---
        javax.swing.JMenu scissorMenu = new javax.swing.JMenu(
                LanguageManager.get("menu_viewer_3d_scissor", "Scissor Editing"));
        javax.swing.JMenuItem scissorSave = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_scissor_save", "Save"));
        scissorSave.addActionListener(e -> scissorStateButtons(0));
        scissorMenu.add(scissorSave);
        javax.swing.JMenuItem scissorLoad = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_scissor_load", "Load"));
        scissorLoad.addActionListener(e -> scissorStateButtons(1));
        scissorMenu.add(scissorLoad);
        javax.swing.JMenuItem scissorDelete = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_scissor_delete", "Delete"));
        scissorDelete.addActionListener(e -> scissorStateButtons(2));
        scissorMenu.add(scissorDelete);
        viewer3DMenu.add(scissorMenu);

        viewer3DMenu.addSeparator();

        // --- Group 8: 3Dプリセット (HOROS: load3DSettings:/save3DSettings:) ---
        javax.swing.JMenuItem presetSelectItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_preset_select", "Select 3D Preset..."));
        presetSelectItem.addActionListener(e -> load3DSettings(null));
        viewer3DMenu.add(presetSelectItem);

        javax.swing.JMenuItem presetSaveItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_viewer_3d_preset_save", "Save Current State as 3D Preset..."));
        presetSaveItem.addActionListener(e -> save3DSettings(null));
        viewer3DMenu.add(presetSaveItem);

        menuBar.add(viewer3DMenu);

        // ========== ROIメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib 1532行目
        javax.swing.JMenu roiMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_tools_roi"));

        // Import ROI(s)...
        // HOROS-20240407準拠: MainMenu.xib 1535行目
        javax.swing.JMenuItem importROIItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_roi_import"));
        importROIItem.addActionListener(e -> roiLoadFromFiles(null));
        roiMenu.add(importROIItem);

        roiMenu.addSeparator();

        // Save Selected ROI(s)...
        // HOROS-20240407準拠: MainMenu.xib 1543行目
        javax.swing.JMenuItem saveSelectedROIItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_roi_save_selected"));
        saveSelectedROIItem.addActionListener(e -> roiSaveSelected(null));
        roiMenu.add(saveSelectedROIItem);

        // Save All ROIs of this Series...
        // HOROS-20240407準拠: MainMenu.xib 1548行目
        javax.swing.JMenuItem saveSeriesROIItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_roi_save_all"));
        saveSeriesROIItem.addActionListener(e -> roiSaveSeries(null));
        roiMenu.add(saveSeriesROIItem);

        roiMenu.addSeparator();

        // Delete All ROIs in this Series
        // HOROS-20240407準拠: MainMenu.xib 1556行目
        javax.swing.JMenuItem deleteAllROIItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_roi_delete_all"));
        deleteAllROIItem.addActionListener(e -> roiDeleteAll(null));
        roiMenu.add(deleteAllROIItem);

        // Delete All ROIs with Same Name as Selected ROI
        // HOROS-20240407準拠: MainMenu.xib 1564行目
        javax.swing.JMenuItem deleteSameNameROIItem = new javax.swing.JMenuItem(
                "Delete All ROIs with Same Name as Selected ROI");
        deleteSameNameROIItem.addActionListener(e -> roiDeleteAllROIsWithSameName(null));
        roiMenu.add(deleteSameNameROIItem);

        roiMenu.addSeparator();

        // Select All ROIs in this Series
        // HOROS-20240407準拠: MainMenu.xib 1573行目
        javax.swing.JMenuItem selectAllROIItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_roi_select_all"));
        selectAllROIItem.addActionListener(e -> roiSelectDeselectAll(null));
        roiMenu.add(selectAllROIItem);

        // Deselect All ROIs in this Series
        // HOROS-20240407準拠: MainMenu.xib 1578行目
        javax.swing.JMenuItem deselectAllROIItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_roi_deselect_all"));
        deselectAllROIItem.addActionListener(e -> roiSelectDeselectAll("deselect"));
        roiMenu.add(deselectAllROIItem);

        roiMenu.addSeparator();

        // ROI Manager...
        // HOROS-20240407準拠: MainMenu.xib 1586行目
        javax.swing.JMenuItem roiManagerItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_roi_manager"));
        roiManagerItem.addActionListener(e -> roiGetManager(null));
        roiMenu.add(roiManagerItem);

        // ROI Info...
        // HOROS-20240407準拠: MainMenu.xib 1591行目
        javax.swing.JMenuItem roiInfoItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("menu_roi_info"));
        roiInfoItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I, 0));
        roiInfoItem.addActionListener(e -> roiGetInfo(null));
        roiMenu.add(roiInfoItem);

        // ROI Rename...
        // HOROS-20240407準拠: MainMenu.xib 1596行目
        javax.swing.JMenuItem roiRenameItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_roi_rename"));
        roiRenameItem.addActionListener(e -> roiRename(null));
        roiMenu.add(roiRenameItem);

        // Set Default ROI Name...
        // HOROS-20240407準拠: MainMenu.xib 1601行目
        javax.swing.JMenuItem roiDefaultsItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_roi_defaults"));
        roiDefaultsItem.addActionListener(e -> roiDefaults(null));
        roiMenu.add(roiDefaultsItem);

        roiMenu.addSeparator();

        // --- Display options (HOROS: updateViews:) ---
        javax.swing.JCheckBoxMenuItem roiInfoOnlyItem = new javax.swing.JCheckBoxMenuItem(
                LanguageManager.get("menu_roi_display_info_only", "Display Info Only when ROI Selected"));
        roiInfoOnlyItem.setSelected(com.oculus.jj.config.AppPreferences.isRoiInfoOnlyWhenSelected());
        roiInfoOnlyItem.addActionListener(e -> updateViews(0));
        roiMenu.add(roiInfoOnlyItem);

        javax.swing.JCheckBoxMenuItem roiMouseOverItem = new javax.swing.JCheckBoxMenuItem(
                LanguageManager.get("menu_roi_display_mouseover", "Display Info when mouse is over"));
        roiMouseOverItem.addActionListener(e -> updateViews(1));
        roiMenu.add(roiMouseOverItem);

        javax.swing.JCheckBoxMenuItem roiNameOnlyItem = new javax.swing.JCheckBoxMenuItem(
                LanguageManager.get("menu_roi_display_name_only", "Display Name Only"));
        roiNameOnlyItem.addActionListener(e -> updateViews(2));
        roiMenu.add(roiNameOnlyItem);

        javax.swing.JCheckBoxMenuItem roiCobbItem = new javax.swing.JCheckBoxMenuItem(
                LanguageManager.get("menu_roi_display_cobb", "Display Cobb angle"));
        roiCobbItem.addActionListener(e -> updateViews(3));
        roiMenu.add(roiCobbItem);

        roiMenu.addSeparator();

        // --- Thickness / Font ---
        javax.swing.JMenuItem incThickItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_increase_thickness", "Increase Thickness"));
        incThickItem.addActionListener(e -> increaseThickness(null));
        roiMenu.add(incThickItem);

        javax.swing.JMenuItem decThickItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_decrease_thickness", "Decrease Thickness"));
        decThickItem.addActionListener(e -> decreaseThickness(null));
        roiMenu.add(decThickItem);

        javax.swing.JMenuItem incFontItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_increase_font", "Increase Font Size"));
        incFontItem.addActionListener(e -> increaseFontSize(null));
        roiMenu.add(incFontItem);

        javax.swing.JMenuItem decFontItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_decrease_font", "Decrease Font Size"));
        decFontItem.addActionListener(e -> decreaseFontSize(null));
        roiMenu.add(decFontItem);

        roiMenu.addSeparator();

        // --- Flip ---
        javax.swing.JMenuItem flipHItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_flip_horizontal", "Flip Horizontally"));
        flipHItem.addActionListener(e -> flipROIHorizontally(null));
        roiMenu.add(flipHItem);

        javax.swing.JMenuItem flipVItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_flip_vertical", "Flip Vertically"));
        flipVItem.addActionListener(e -> flipROIVertically(null));
        roiMenu.add(flipVItem);

        roiMenu.addSeparator();

        // --- Statistics / Layer / Volume ---
        javax.swing.JMenuItem roiHistItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_histogram", "Histogram of Selected ROI..."));
        roiHistItem.addActionListener(e -> roiHistogram(null));
        roiMenu.add(roiHistItem);

        javax.swing.JMenuItem roiLayerItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_create_layer", "Create Layer from Selected ROI"));
        roiLayerItem.addActionListener(e -> createLayerROIFromSelectedROI(null));
        roiMenu.add(roiLayerItem);

        javax.swing.JMenuItem roiVolumeItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_volume", "ROI Volume"));
        roiVolumeItem.addActionListener(e -> roiVolume(null));
        roiMenu.add(roiVolumeItem);

        roiMenu.addSeparator();

        // --- Generate / Delete Generated ---
        javax.swing.JMenuItem roiGenItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_generate_missing", "Generate Missing ROIs"));
        roiGenItem.addActionListener(e -> roiVolume(1));
        roiMenu.add(roiGenItem);

        javax.swing.JMenuItem roiDelGenItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_delete_generated", "Delete Generated ROIs"));
        roiDelGenItem.addActionListener(e -> roiDeleteGeneratedROIs(null));
        roiMenu.add(roiDelGenItem);

        roiMenu.addSeparator();

        // --- Erase / Restore Content ---
        javax.swing.JMenuItem roiEraseItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_erase_content", "Erase Content"));
        roiEraseItem.addActionListener(e -> roiVolumeEraseRestore(1));
        roiMenu.add(roiEraseItem);

        javax.swing.JMenuItem roiRestoreItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_restore_content", "Restore Content"));
        roiRestoreItem.addActionListener(e -> roiVolumeEraseRestore(2));
        roiMenu.add(roiRestoreItem);

        roiMenu.addSeparator();

        // --- Propagate ---
        javax.swing.JMenuItem roiPropItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_propagate", "Propagate Selected ROI..."));
        roiPropItem.addActionListener(e -> roiPropagateSetup(null));
        roiMenu.add(roiPropItem);

        javax.swing.JMenuItem roiPropSlabItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_propagate_slab", "Propagate Selected ROI to Current Thick Slab"));
        roiPropSlabItem.addActionListener(e -> roiPropagateSlab(null));
        roiMenu.add(roiPropSlabItem);

        roiMenu.addSeparator();

        // --- Group / UNGROUP ---
        javax.swing.JMenuItem roiGroupItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_group", "Group Selected ROIs"));
        roiGroupItem.addActionListener(e -> groupSelectedROIs(null));
        roiMenu.add(roiGroupItem);

        javax.swing.JMenuItem roiUngroupItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_ungroup", "Ungroup Selected ROIs"));
        roiUngroupItem.addActionListener(e -> ungroupSelectedROIs(null));
        roiMenu.add(roiUngroupItem);

        roiMenu.addSeparator();

        // --- Lock / Unlock ---
        javax.swing.JMenuItem roiLockItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_lock", "Lock Selected ROIs"));
        roiLockItem.addActionListener(e -> lockSelectedROIs(null));
        roiMenu.add(roiLockItem);

        javax.swing.JMenuItem roiUnlockItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_unlock", "Unlock Selected ROIs"));
        roiUnlockItem.addActionListener(e -> unlockSelectedROIs(null));
        roiMenu.add(roiUnlockItem);

        roiMenu.addSeparator();

        // --- Make unselectable / Selectable ---
        javax.swing.JMenuItem roiUnselItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_unselectable", "Make Selected ROIs Unselectable"));
        roiUnselItem.addActionListener(e -> makeSelectedROIsUnselectable(null));
        roiMenu.add(roiUnselItem);

        javax.swing.JMenuItem roiSelItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_make_selectable", "Make All ROIs Selectable"));
        roiSelItem.addActionListener(e -> makeAllROIsSelectable(null));
        roiMenu.add(roiSelItem);

        roiMenu.addSeparator();

        // --- Set Pixel Values / Grow Region / Brush ROIs ---
        javax.swing.JMenuItem roiSetPixItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_set_pixel_values", "Set Pixel Values to..."));
        roiSetPixItem.addActionListener(e -> roiSetPixelsSetup(null));
        roiMenu.add(roiSetPixItem);

        javax.swing.JMenuItem roiGrowItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_grow_region", "Grow Region (2D/3D Segmentation)..."));
        roiGrowItem.addActionListener(e -> segmentationTest(null));
        roiMenu.add(roiGrowItem);

        javax.swing.JMenuItem roiBrushItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_brush", "Brush ROIs"));
        roiBrushItem.addActionListener(e -> applyConvolutionOnSource(0));
        roiMenu.add(roiBrushItem);

        // --- Morphological brush ops (HOROS: morphoSelectedBrushROI:) ---
        javax.swing.JMenuItem roiDilateItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_morpho_dilate", "Dilatation"));
        roiDilateItem.addActionListener(e -> morphoSelectedBrushROI(1));
        roiMenu.add(roiDilateItem);

        javax.swing.JMenuItem roiCloseItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_morpho_close", "Closing"));
        roiCloseItem.addActionListener(e -> morphoSelectedBrushROI(2));
        roiMenu.add(roiCloseItem);

        javax.swing.JMenuItem roiOpenItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_morpho_open", "Opening"));
        roiOpenItem.addActionListener(e -> morphoSelectedBrushROI(3));
        roiMenu.add(roiOpenItem);

        roiMenu.addSeparator();

        // --- Convert / Merge brush ---
        javax.swing.JMenuItem roiConvertItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_convert_brush_polygon", "Convert Between Polygon / Brush ROIs"));
        roiConvertItem.addActionListener(e -> convertBrushPolygon(null));
        roiMenu.add(roiConvertItem);

        javax.swing.JMenuItem roiMergeItem = new javax.swing.JMenuItem(
                LanguageManager.get("menu_roi_merge_brush", "Merge Selected Brush ROIs"));
        roiMergeItem.addActionListener(e -> mergeBrushROI(null));
        roiMenu.add(roiMergeItem);

        menuBar.add(roiMenu);

        // ========== Pluginsメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib 1853行目
        javax.swing.JMenu pluginsMenu = new javax.swing.JMenu("Plugins");

        // Image Filters サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 1856行目
        javax.swing.JMenu imageFiltersMenu = new javax.swing.JMenu("Image Filters");
        pluginsMenu.add(imageFiltersMenu);

        // ROI Tools サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 1859行目
        javax.swing.JMenu roiToolsMenu = new javax.swing.JMenu("ROI Tools");
        pluginsMenu.add(roiToolsMenu);

        // Others サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 1862行目
        javax.swing.JMenu othersMenu = new javax.swing.JMenu("Others");
        pluginsMenu.add(othersMenu);

        // Database サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 1865行目
        javax.swing.JMenu databasePluginsMenu = new javax.swing.JMenu("Database");
        pluginsMenu.add(databasePluginsMenu);

        pluginsMenu.addSeparator();

        // Plugins Manager...
        // HOROS-20240407準拠: MainMenu.xib 1871行目
        javax.swing.JMenuItem pluginsManagerItem = new javax.swing.JMenuItem("Plugins Manager...");
        pluginsManagerItem.addActionListener(e -> showWindow(null));
        pluginsMenu.add(pluginsManagerItem);

        menuBar.add(pluginsMenu);

        // ========== Recent Studiesメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib 1879行目
        javax.swing.JMenu recentStudiesMenu = new javax.swing.JMenu(MenuResourceLoader.getString("recent_studies"));
        // 注: Recent Studiesメニューは動的に更新されるため、初期状態では空
        javax.swing.JMenuItem emptyItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("recent_empty"));
        emptyItem.setEnabled(false);
        recentStudiesMenu.add(emptyItem);

        menuBar.add(recentStudiesMenu);

        // ========== Windowメニュー ==========
        // HOROS-20240407準拠: MainMenu.xib 1889行目
        javax.swing.JMenu windowMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_window"));

        // Minimize
        // HOROS-20240407準拠: MainMenu.xib 1892行目
        javax.swing.JMenuItem minimizeItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_window_minimize"));
        minimizeItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, 0));
        minimizeItem.addActionListener(e -> minimizeMenuOwnerWindow(e));
        windowMenu.add(minimizeItem);

        // Resize Window サブメニュー
        // HOROS-20240407準拠: MainMenu.xib 1897行目
        // HOROS: DCMView.m:13321 - resizeWindow: tag 0..5 → resizeWindowToScale:
        // 25%/50%/100%/200%/300%: 画像ズーム倍率 / iPod Video: 320pxを基準に画像をフィット
        javax.swing.JMenu resizeWindowMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_window_resize"));
        String[] resizeLabels = { "25%", "50%", "100%", "200%", "300%", "iPod Video" };
        for (int i = 0; i < resizeLabels.length; i++) {
            javax.swing.JMenuItem resizeItem = new javax.swing.JMenuItem(resizeLabels[i]);
            final int tag = i;
            resizeItem.addActionListener(e -> resizeWindowToScale(tag));
            resizeWindowMenu.add(resizeItem);
        }
        windowMenu.add(resizeWindowMenu);

        // Tile 2D Viewer Windows
        // HOROS-20240407準拠: MainMenu.xib 1933行目
        javax.swing.JMenuItem tile2DItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_window_tile_2d"));
        tile2DItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, 0));
        tile2DItem.addActionListener(e -> {
            ViewerController.tileWindows();
        });
        windowMenu.add(tile2DItem);

        // Tile 3D Viewer Windows
        // HOROS-20240407準拠: MainMenu.xib 1938行目
        javax.swing.JMenuItem tile3DItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_window_tile_3d"));
        tile3DItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        tile3DItem.addActionListener(e -> {
            ViewerController.tile3DWindows();
        });
        windowMenu.add(tile3DItem);

        // Close All Viewers
        // HOROS-20240407準拠: MainMenu.xib 1943行目
        javax.swing.JMenuItem closeAllViewersItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_window_close_all_viewers"));
        closeAllViewersItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        closeAllViewersItem.addActionListener(e -> {
            closeAllDisplayedViewers();
        });
        windowMenu.add(closeAllViewersItem);

        windowMenu.addSeparator();

        // Bring All to Front (HOROS: MainMenu.xib 1949 - arrangeInFront:)
        javax.swing.JMenuItem bringToFrontItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_window_bring_all_to_front"));
        bringToFrontItem.addActionListener(e -> bringAllToFront());
        windowMenu.add(bringToFrontItem);

        menuBar.add(windowMenu);

        // ========== Helpメニュー ==========
        javax.swing.JMenu helpMenu = new javax.swing.JMenu(MenuResourceLoader.getString("menu_help"));

        javax.swing.JMenuItem shortcutsItem = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("menu_help_shortcuts"));
        shortcutsItem.addActionListener(e -> showShortcutsDialog());
        helpMenu.add(shortcutsItem);

        helpMenu.addSeparator();

        javax.swing.JMenuItem aboutItem = new javax.swing.JMenuItem(MenuResourceLoader.getString("file_about"));
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "OculusJJ - Advanced DICOM Viewer\nVersion 1.0\nOpen-source medical imaging for veterinary practice."));
        helpMenu.add(aboutItem);

        menuBar.add(helpMenu);

        // HOROS-20240407準拠: 各メニュー・サブメニュー・メニュー項目にアクション識別子を付与
        // （MainMenu.xib autoenablesItems="YES" + Responder Chain + validateMenuItem: 相当）
        // File menu
        tagMenu(fileMenu, "fileMenu");
        tagMenu(showDatabaseItem, "showDatabase");
        tagMenu(drawerToggleItem, "drawerToggle");
        tagMenu(comparativeToggleItem, "comparativeToggle");
        tagMenu(prefMenuItem, "preferences");
        tagMenu(newDatabaseFolderItem, "newDatabaseFolder");
        tagMenu(openDatabaseFolderItem, "openDatabaseFolder");
        tagMenu(albumsMenu, "albumsMenu");
        tagMenu(addAlbumItem, "addAlbum");
        tagMenu(addSmartAlbumItem, "addSmartAlbum");
        tagMenu(addSelectedStudiesItem, "addSelectedStudiesToAlbum");
        tagMenu(deleteAlbumItem, "deleteAlbum");
        tagMenu(saveAlbumsItem, "saveAlbums");
        tagMenu(importAlbumsItem, "importAlbums");
        tagMenu(defaultAlbumsItem, "defaultAlbums");
        tagMenu(reportMenu, "reportMenu");
        tagMenu(openReportItem, "openReport");
        tagMenu(deleteReportItem, "deleteReport");
        tagMenu(convertToPDFItem, "convertToPDF");
        tagMenu(convertToDICOMSRItem, "convertToDICOMSR");
        tagMenu(importMenu, "importMenu");
        tagMenu(importFilesItem, "importFiles");
        tagMenu(importURLItem, "importURL");
        tagMenu(importRawDataItem, "importRawData");
        tagMenu(exportMenu, "exportMenu");
        tagMenu(export2PACSItem, "export2PACS");
        tagMenu(exportMovieItem, "exportMovie");
        tagMenu(exportJPEGItem, "exportJPEG");
        tagMenu(exportRAWItem, "exportRAW");
        tagMenu(exportTIFFItem, "exportTIFF");
        tagMenu(exportDICOMFileItem, "exportDICOMFile");
        tagMenu(exportEmailItem, "exportEmail");
        tagMenu(exportPhotosItem, "exportPhotos");
        tagMenu(saveDBListItemItem, "saveDBListAs");
        tagMenu(burnItem, "burn");
        tagMenu(anonymizeItem, "anonymize");
        tagMenu(copyToDBFolderItem, "copyToDBFolder");
        tagMenu(searchItem, "search");
        tagMenu(mergeStudiesItem, "mergeStudies");
        tagMenu(deleteExamItem, "deleteExam");
        tagMenu(metaDataItem, "metaData");
        tagMenu(compressFilesItem, "compressFiles");
        tagMenu(decompressFilesItem, "decompressFiles");
        tagMenu(rebuildDatabaseItem, "rebuildDatabase");
        tagMenu(rebuildSQLItem, "rebuildSQL");
        tagMenu(rebuildThumbnailsItem, "rebuildThumbnails");
        tagMenu(closeWindowItem, "closeWindow");
        tagMenu(pageSetupItem, "pageSetup");
        tagMenu(printItem, "print");
        tagMenu(dicomPrintItem, "dicomPrint");

        // Network menu
        tagMenu(networkMenu, "networkMenu");
        tagMenu(networkExport2PACSItem, "networkExport2PACS");
        tagMenu(queryDICOMItem, "queryDICOM");
        tagMenu(querySelectedStudyItem, "querySelectedStudy");
        tagMenu(autoQueryItem, "autoQuery");
        tagMenu(autoQueryRefreshItem, "autoQueryRefresh");
        tagMenu(retrievePODItem, "retrievePOD");
        tagMenu(refreshPODItem, "refreshPOD");
        tagMenu(abortStoreSCUItem, "abortStoreSCU");
        tagMenu(networkLogsItem, "networkLogs");
        tagMenu(preferencesItem, "preferences");
        tagMenu(pacsQuerySettingsItem, "pacsQuerySettings");

        // Edit menu
        tagMenu(editMenu, "editMenu");
        tagMenu(undoItem, "undo");
        tagMenu(redoItem, "redo");
        tagMenu(cutItem, "cut");
        tagMenu(copyItem, "copy");
        tagMenu(pasteItem, "paste");
        tagMenu(selectAllItem, "selectAll");

        // Format menu
        tagMenu(formatMenu, "formatMenu");
        tagMenu(customizeToolbarItem, "customizeToolbar");
        tagMenu(fullscreenItem, "fullscreen");
        tagMenu(fontMenu, "fontMenu");
        tagMenu(showFontsItem, "showFonts");
        tagMenu(boldItem, "bold");
        tagMenu(italicItem, "italic");
        tagMenu(biggerItem, "bigger");
        tagMenu(smallerItem, "smaller");
        tagMenu(showColorsItem, "showColors");

        // 2D Viewer menu
        tagMenu(viewer2DMenu, "viewer2DMenu");
        tagMenu(nextSeriesItem, "nextSeries");
        tagMenu(previousSeriesItem, "previousSeries");
        tagMenu(nextPatientItem, "nextPatient");
        tagMenu(previousPatientItem, "previousPatient");
        tagMenu(seriesListItem, "seriesList");
        tagMenu(convertBWRGBMenu, "convertBWRGBMenu");
        tagChildMenuItems(convertBWRGBMenu, "convertBWRGB");
        tagMenu(resetImageViewItem, "resetImageView");
        tagMenu(revertSeriesItem, "revertSeries");
        tagMenu(scaleMenu, "scaleMenu");
        tagChildMenuItems(scaleMenu, "scale");
        tagMenu(sortByMenu, "sortByMenu");
        tagChildMenuItems(sortByMenu, "sortBy");
        tagMenu(orientationMenu, "orientationMenu");
        tagChildMenuItems(orientationMenu, "orientation");
        tagMenu(calibrateItem, "calibrate");
        tagMenu(threeDPanelItem, "threeDPanel");
        tagMenu(navigatorItem, "navigator");
        tagMenu(flipSeriesItem, "flipSeries");
        tagMenu(useVOILUTItem, "useVOILUT");
        tagMenu(displayOverlaysItem, "displayOverlays");
        tagMenu(dicomMetaDataItem, "dicomMetaData");
        tagMenu(convertSUVItem, "convertSUV");
        tagMenu(fuseItem, "fuse");
        tagMenu(flattenFusedItem, "flattenFused");
        tagMenu(propagateSettingsItem, "propagateSettings");
        tagMenu(dontPropagateWLWWItem, "dontPropagateWLWW");
        tagMenu(propagateInSeriesItem, "propagateInSeries");
        tagMenu(zoomParallelItem, "zoomParallel");
        tagMenu(syncSeriesMenu, "syncSeriesMenu");
        tagChildMenuItems(syncSeriesMenu, "syncSeries");
        tagMenu(syncDifferentItem, "syncDifferent");
        tagMenu(keyImageItem, "keyImage");
        tagMenu(markAllKeyItem, "markAllKey");
        tagMenu(unmarkAllKeyItem, "unmarkAllKey");
        tagMenu(markROIsKeyItem, "markROIsKey");
        tagMenu(saveDICOMSCItem, "saveDICOMSC");
        tagMenu(exportKeySCItem, "exportKeySC");
        tagMenu(findNextKeyItem, "findNextKey");
        tagMenu(findPrevKeyItem, "findPrevKey");
        tagMenu(subtractionMenu, "subtractionMenu");
        tagChildMenuItems(subtractionMenu, "subCtrlParam");
        tagMenu(subCtrlOnOffItem, "subCtrlOnOff");
        tagMenu(newMaskItem, "subCtrlNewMask");
        tagMenu(subSharpenItem, "subSharpen");
        tagMenu(annotationsMenu, "annotationsMenu");
        tagMenu(openAnnotationsItem, "annotationsOpen");
        tagMenu(windowWidthLevelMenu, "windowWidthLevelMenu");
        tagMenu(applyWindowProtocolItem, "applyWindowProtocol");
        tagMenu(resetWLWWItem, "resetWLWW");
        tagMenu(clutMenu, "clutMenu");
        tagChildMenuItems(clutMenu, "applyCLUT");
        tagMenu(clutBarMenu, "clutBarMenu");
        tagMenu(clutBarUnavailableItem, "clutBarUnavailable");
        tagMenu(opacityMenu, "opacityMenu");
        tagChildMenuItems(opacityMenu, "applyOpacity");
        tagMenu(convolutionMenu, "convolutionMenu");
        tagChildMenuItems(convolutionMenu, "applyConvolution");
        tagMenu(resampleItem, "resample");
        tagMenu(item12Bit, "item12Bit");
        tagMenu(workspaceMenu, "workspaceMenu");
        tagMenu(saveWorkspaceItem, "saveWorkspace");
        tagMenu(loadWorkspaceItem, "loadWorkspace");
        tagMenu(resetWorkspaceItem, "resetWorkspace");

        // 3D Viewer menu
        tagMenu(viewer3DMenu, "viewer3DMenu");
        tagMenu(mprViewerItem, "mprViewer");
        tagMenu(cprViewerItem, "cprViewer");
        tagMenu(orthogonalMPRItem, "orthogonalMPR");
        tagMenu(mipViewerItem, "mipViewer");
        tagMenu(vrViewerItem, "vrViewer");
        tagMenu(surfaceRenderingItem, "surfaceRendering");
        tagMenu(endoscopyItem, "endoscopy");
        tagMenu(reset3DItem, "reset3D");
        tagMenu(revert3DItem, "revert3D");
        tagMenu(coronalItem, "coronal");
        tagMenu(leftSagittalItem, "leftSagittal");
        tagMenu(rightSagittalItem, "rightSagittal");
        tagMenu(axialItem, "axial");
        tagMenu(flyThruAddItem, "flyThruAdd");
        tagMenu(flyThruRemoveItem, "flyThruRemove");
        tagMenu(flyThruResetItem, "flyThruReset");
        tagMenu(curvedPathMenu, "curvedPathMenu");
        tagMenu(curvedPathLoad, "curvedPathLoad");
        tagMenu(curvedPathSave, "curvedPathSave");
        tagMenu(scissorMenu, "scissorMenu");
        tagMenu(scissorSave, "scissorSave");
        tagMenu(scissorLoad, "scissorLoad");
        tagMenu(scissorDelete, "scissorDelete");
        tagMenu(presetSelectItem, "presetSelect");
        tagMenu(presetSaveItem, "presetSave");

        // ROI menu
        tagMenu(roiMenu, "roiMenu");
        tagMenu(importROIItem, "importROI");
        tagMenu(saveSelectedROIItem, "saveSelectedROI");
        tagMenu(saveSeriesROIItem, "saveSeriesROI");
        tagMenu(deleteAllROIItem, "deleteAllROI");
        tagMenu(deleteSameNameROIItem, "deleteSameNameROI");
        tagMenu(selectAllROIItem, "selectAllROI");
        tagMenu(deselectAllROIItem, "deselectAllROI");
        tagMenu(roiManagerItem, "roiManager");
        tagMenu(roiInfoItem, "roiInfo");
        tagMenu(roiRenameItem, "roiRename");
        tagMenu(roiDefaultsItem, "roiDefaults");
        tagMenu(roiInfoOnlyItem, "roiInfoOnly");
        tagMenu(roiMouseOverItem, "roiMouseOver");
        tagMenu(roiNameOnlyItem, "roiNameOnly");
        tagMenu(roiCobbItem, "roiCobb");
        tagMenu(incThickItem, "incThick");
        tagMenu(decThickItem, "decThick");
        tagMenu(incFontItem, "incFont");
        tagMenu(decFontItem, "decFont");
        tagMenu(flipHItem, "flipH");
        tagMenu(flipVItem, "flipV");
        tagMenu(roiHistItem, "roiHist");
        tagMenu(roiLayerItem, "roiLayer");
        tagMenu(roiVolumeItem, "roiVolume");
        tagMenu(roiGenItem, "roiGen");
        tagMenu(roiDelGenItem, "roiDelGen");
        tagMenu(roiEraseItem, "roiErase");
        tagMenu(roiRestoreItem, "roiRestore");
        tagMenu(roiPropItem, "roiProp");
        tagMenu(roiPropSlabItem, "roiPropSlab");
        tagMenu(roiGroupItem, "roiGroup");
        tagMenu(roiUngroupItem, "roiUngroup");
        tagMenu(roiLockItem, "roiLock");
        tagMenu(roiUnlockItem, "roiUnlock");
        tagMenu(roiUnselItem, "roiUnsel");
        tagMenu(roiSelItem, "roiSel");
        tagMenu(roiSetPixItem, "roiSetPix");
        tagMenu(roiGrowItem, "roiGrow");
        tagMenu(roiBrushItem, "roiBrush");
        tagMenu(roiDilateItem, "roiDilate");
        tagMenu(roiCloseItem, "roiClose");
        tagMenu(roiOpenItem, "roiOpen");
        tagMenu(roiConvertItem, "roiConvert");
        tagMenu(roiMergeItem, "roiMerge");

        // Plugins menu
        tagMenu(pluginsMenu, "pluginsMenu");
        tagMenu(imageFiltersMenu, "imageFiltersMenu");
        tagMenu(roiToolsMenu, "roiToolsMenu");
        tagMenu(othersMenu, "othersMenu");
        tagMenu(databasePluginsMenu, "databasePluginsMenu");
        tagMenu(pluginsManagerItem, "pluginsManager");

        // Recent Studies menu
        tagMenu(recentStudiesMenu, "recentStudiesMenu");
        tagMenu(emptyItem, "recentEmpty");

        // Window menu
        tagMenu(windowMenu, "windowMenu");
        tagMenu(minimizeItem, "minimize");
        tagMenu(resizeWindowMenu, "resizeWindowMenu");
        tagChildMenuItems(resizeWindowMenu, "resizeWindowItem");
        tagMenu(tile2DItem, "tile2D");
        tagMenu(tile3DItem, "tile3D");
        tagMenu(closeAllViewersItem, "closeAllViewers");
        tagMenu(bringToFrontItem, "bringAllToFront");

        // Help menu
        tagMenu(helpMenu, "helpMenu");
        tagMenu(shortcutsItem, "shortcuts");
        tagMenu(aboutItem, "about");

        attachMenuValidation(menuBar, this);
        validateMenuBarForWindow(menuBar, this);
        setJMenuBar(menuBar);
    }

    /**
     * HOROS-20240407準拠: メニュー項目にアクション識別子 (horosAction) を設定する
     */
    public static void tagMenu(javax.swing.JComponent item, String action) {
        if (item != null && action != null) {
            item.putClientProperty("horosAction", action);
        }
    }

    /**
     * HOROS-20240407準拠: サブメニュー内の全子JMenuItemに同一アクション識別子を設定する
     */
    private static void tagChildMenuItems(javax.swing.JMenu menu, String action) {
        if (menu == null || action == null) {
            return;
        }
        for (int i = 0; i < menu.getMenuComponentCount(); i++) {
            java.awt.Component comp = menu.getMenuComponent(i);
            if (comp instanceof javax.swing.JMenuItem child) {
                child.putClientProperty("horosAction", action);
            }
        }
    }

    /**
     * メニューイベントの発生元コンポーネントまたはフォーカス状態から対象ウィンドウを特定する
     */
    private java.awt.Window resolveMenuOwnerWindow(java.awt.event.ActionEvent e) {
        if (e != null && e.getSource() instanceof javax.swing.JComponent comp) {
            Object prop = comp.getClientProperty("horosOwnerWindow");
            if (prop instanceof java.awt.Window w) {
                return w;
            }
            java.awt.Window ancestor = resolveWindowFromMenuComponent(comp);
            if (ancestor != null) {
                return ancestor;
            }
        }
        java.awt.Window active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return active != null ? active : this;
    }

    private static java.awt.Window resolveWindowFromMenuComponent(java.awt.Component comp) {
        java.awt.Component cur = comp;
        while (cur != null) {
            if (cur instanceof javax.swing.JComponent jc) {
                Object prop = jc.getClientProperty("horosOwnerWindow");
                if (prop instanceof java.awt.Window w) {
                    return w;
                }
            }
            if (cur instanceof java.awt.Window w) {
                return w;
            }
            if (cur instanceof javax.swing.JPopupMenu popup) {
                cur = popup.getInvoker();
            } else {
                cur = cur.getParent();
            }
        }
        return null;
    }

    private javax.swing.JFrame resolveMenuOwnerFrame(java.awt.event.ActionEvent e) {
        java.awt.Window w = resolveMenuOwnerWindow(e);
        if (w instanceof ToolbarPanel tp && tp.getViewer() != null) {
            return tp.getViewer();
        }
        if (w instanceof javax.swing.JFrame f) {
            return f;
        }
        return this;
    }

    private void closeMenuOwnerWindow(java.awt.event.ActionEvent e) {
        javax.swing.JFrame target = resolveMenuOwnerFrame(e);
        if (target != null) {
            target.dispose();
        }
    }

    private void minimizeMenuOwnerWindow(java.awt.event.ActionEvent e) {
        javax.swing.JFrame target = resolveMenuOwnerFrame(e);
        if (target != null) {
            target.setState(java.awt.Frame.ICONIFIED);
        }
    }

    private void handleShowDatabaseWindow(java.awt.event.ActionEvent e) {
        java.awt.Window w = resolveMenuOwnerWindow(e);
        if (w == this) {
            toggleDatabaseOutlineVisibility();
        } else {
            setVisible(true);
            if (getExtendedState() == java.awt.Frame.ICONIFIED) {
                setExtendedState(java.awt.Frame.NORMAL);
            }
            toFront();
            requestFocus();
        }
    }

    /**
     * HOROS-20240407準拠: ウインドウ種別（Cocoa First Responder Chain のコントローラ種別相当）
     */
    public enum WindowRole {
        BROWSER,    // BrowserController.m
        VIEWER_2D,  // ViewerController.m / DCMView.m
        VIEWER_3D,  // VRController.m / SRController.m / EndoscopyViewer.m
        MPR,        // MPRController.m / OrthogonalMPRController.m
        CPR         // CPRController.m
    }

    /**
     * HOROS-20240407準拠: メニューバーの各トップレベルJMenuおよび対象Windowに
     * 動的メニュー検証リスナー（NSMenu autoenablesItems + validateMenuItem: 相当）を登録する
     */
    public static void attachMenuValidation(javax.swing.JMenuBar menuBar, java.awt.Window ownerWindow) {
        if (menuBar == null) {
            return;
        }
        if (ownerWindow != null) {
            menuBar.putClientProperty("horosOwnerWindow", ownerWindow);
            propagateOwnerWindow(menuBar, ownerWindow);
        }
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            javax.swing.JMenu menu = menuBar.getMenu(i);
            if (menu == null) {
                continue;
            }
            if (Boolean.TRUE.equals(menu.getClientProperty("horosValidationAttached"))) {
                continue;
            }
            menu.putClientProperty("horosValidationAttached", Boolean.TRUE);
            menu.addMenuListener(new javax.swing.event.MenuListener() {
                @Override
                public void menuSelected(javax.swing.event.MenuEvent e) {
                    java.awt.Window target = (java.awt.Window) menuBar.getClientProperty("horosOwnerWindow");
                    if (target == null) {
                        target = resolveWindowFromMenuComponent(menuBar);
                    }
                    validateMenuBar(menuBar, target);
                }

                @Override
                public void menuDeselected(javax.swing.event.MenuEvent e) {
                }

                @Override
                public void menuCanceled(javax.swing.event.MenuEvent e) {
                }
            });
        }
        if (ownerWindow != null) {
            ownerWindow.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowGainedFocus(java.awt.event.WindowEvent e) {
                    validateMenuBar(menuBar, ownerWindow);
                }
            });
        }
    }

    public static void propagateOwnerWindow(javax.swing.JComponent root, java.awt.Window ownerWindow) {
        if (root == null || ownerWindow == null) {
            return;
        }
        root.putClientProperty("horosOwnerWindow", ownerWindow);
        if (root instanceof javax.swing.JMenuBar mb) {
            for (int i = 0; i < mb.getMenuCount(); i++) {
                javax.swing.JMenu m = mb.getMenu(i);
                if (m != null) {
                    propagateOwnerWindow(m, ownerWindow);
                }
            }
        } else if (root instanceof javax.swing.JMenu menu) {
            for (int i = 0; i < menu.getMenuComponentCount(); i++) {
                java.awt.Component c = menu.getMenuComponent(i);
                if (c instanceof javax.swing.JComponent jc) {
                    propagateOwnerWindow(jc, ownerWindow);
                }
            }
        }
    }

    /**
     * HOROS-20240407準拠: 指定されたメニューバーを対象ウィンドウのロール・状態に応じて検証・更新する
     */
    public static void validateMenuBar(javax.swing.JMenuBar menuBar, java.awt.Window ownerWindow) {
        BrowserController browser = currentBrowser();
        if (browser != null) {
            browser.validateMenuBarForWindow(menuBar, ownerWindow);
        }
    }

    /**
     * HOROS-20240407準拠: 各ウインドウ（BrowserController / ViewerController / Viewer3DController /
     * MPRController / CPRController）の Responder Chain および validateMenuItem: に準拠して
     * メニュー項目の有効/無効（グレーアウト）およびチェック状態を動的に更新する。
     */
    public void validateMenuBarForWindow(javax.swing.JMenuBar menuBar, java.awt.Window ownerWindow) {
        if (menuBar == null) {
            return;
        }
        java.awt.Window resolvedWindow = ownerWindow;
        if (resolvedWindow == null) {
            Object prop = menuBar.getClientProperty("horosOwnerWindow");
            if (prop instanceof java.awt.Window w) {
                resolvedWindow = w;
            } else {
                resolvedWindow = resolveWindowFromMenuComponent(menuBar);
            }
        }
        if (resolvedWindow == null) {
            resolvedWindow = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        }
        if (resolvedWindow == null) {
            resolvedWindow = this;
        }

        WindowRole role = determineWindowRole(resolvedWindow);
        ViewerController viewer2D = resolveViewer2D(resolvedWindow);

        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            javax.swing.JMenu menu = menuBar.getMenu(i);
            if (menu != null) {
                // トップレベルJMenu自体は常に開けるように有効化し、内部項目をグレーアウト制御する（HOROS NSMenu準拠）
                menu.setEnabled(true);
                validateMenuChildren(menu, role, resolvedWindow, viewer2D);
            }
        }
    }

    private WindowRole determineWindowRole(java.awt.Window w) {
        if (w instanceof BrowserController) {
            return WindowRole.BROWSER;
        }
        if (w instanceof ViewerController || w instanceof ToolbarPanel) {
            return WindowRole.VIEWER_2D;
        }
        if (w instanceof Viewer3DController) {
            return WindowRole.VIEWER_3D;
        }
        if (w instanceof MPRController) {
            return WindowRole.MPR;
        }
        if (w instanceof CPRController) {
            return WindowRole.CPR;
        }
        return WindowRole.BROWSER;
    }

    private ViewerController resolveViewer2D(java.awt.Window w) {
        if (w instanceof ViewerController vc) {
            return vc;
        }
        if (w instanceof ToolbarPanel tp && tp.getViewer() != null) {
            return tp.getViewer();
        }
        List<ViewerController> displayed = ViewerController.getDisplayed2DViewers();
        if (displayed != null && !displayed.isEmpty()) {
            return displayed.get(displayed.size() - 1);
        }
        return null;
    }

    private boolean validateMenuChildren(javax.swing.JMenu menu, WindowRole role,
            java.awt.Window ownerWindow, ViewerController viewer2D) {
        boolean anyChildEnabled = false;
        for (int i = 0; i < menu.getMenuComponentCount(); i++) {
            java.awt.Component comp = menu.getMenuComponent(i);
            if (comp instanceof javax.swing.JMenu subMenu) {
                String subAction = (String) subMenu.getClientProperty("horosAction");
                // プラグインの空サブメニューや明示的に許可されたサブメニューの判定
                boolean childrenEnabled = validateMenuChildren(subMenu, role, ownerWindow, viewer2D);
                boolean subEnabled = evaluateSubMenuEnabled(subAction, childrenEnabled, role, ownerWindow, viewer2D);
                subMenu.setEnabled(subEnabled);
                if (subEnabled) {
                    anyChildEnabled = true;
                }
            } else if (comp instanceof javax.swing.JMenuItem item) {
                String action = (String) item.getClientProperty("horosAction");
                boolean enabled = validateMenuItemForWindow(action, item, role, ownerWindow, viewer2D);
                item.setEnabled(enabled);
                syncCheckBoxMenuItemState(action, item, viewer2D);
                if (enabled) {
                    anyChildEnabled = true;
                }
            }
        }
        return anyChildEnabled;
    }

    private boolean evaluateSubMenuEnabled(String subAction, boolean childrenEnabled,
            WindowRole role, java.awt.Window ownerWindow, ViewerController viewer2D) {
        if (subAction == null) {
            return childrenEnabled;
        }
        switch (subAction) {
            case "clutBarMenu":
                return false;
            case "albumsMenu":
            case "importMenu":
                return role == WindowRole.BROWSER;
            case "reportMenu":
                return role == WindowRole.BROWSER && hasDatabaseSelection();
            case "resizeWindowMenu":
                return role == WindowRole.VIEWER_2D;
            case "curvedPathMenu":
                if (role == WindowRole.CPR) {
                    return true;
                }
                if (role == WindowRole.MPR && ownerWindow instanceof MPRController mpr) {
                    return mpr.getLayoutType() == MPRController.LayoutType.CURVED_MPR;
                }
                return false;
            case "scissorMenu":
                return role == WindowRole.VIEWER_3D;
            case "imageFiltersMenu":
            case "roiToolsMenu":
            case "othersMenu":
                return role == WindowRole.VIEWER_2D;
            case "databasePluginsMenu":
                return role == WindowRole.BROWSER;
            default:
                return childrenEnabled;
        }
    }

    private void syncCheckBoxMenuItemState(String action, javax.swing.JMenuItem item, ViewerController viewer2D) {
        if (!(item instanceof javax.swing.JCheckBoxMenuItem cb) || action == null) {
            return;
        }
        DCMView view = viewer2D != null ? viewer2D.getImageView() : null;
        switch (action) {
            case "useVOILUT":
                cb.setSelected(com.oculus.jj.config.AppPreferences.isUseVOILUT());
                break;
            case "propagateSettings":
                cb.setSelected(DCMView.propagateSettings);
                break;
            case "subCtrlOnOff":
                if (subCtrlOnOffItem != null) {
                    cb.setSelected(subCtrlOnOffItem.isSelected());
                }
                break;
            case "subSharpen":
                if (subSharpenItem != null) {
                    cb.setSelected(subSharpenItem.isSelected());
                }
                break;
            case "item12Bit":
                if (view != null) {
                    cb.setSelected(view.isLUT12Bit());
                }
                break;
            case "roiInfoOnly":
                cb.setSelected(com.oculus.jj.config.AppPreferences.isRoiInfoOnlyWhenSelected());
                break;
            default:
                break;
        }
    }

    private boolean hasDatabaseSelection() {
        return databaseOutline != null
                && databaseOutline.getSelectedItems() != null
                && !databaseOutline.getSelectedItems().isEmpty();
    }

    private int getSelectedStudyCountInBrowser() {
        if (databaseOutline == null || databaseOutline.getSelectedItems() == null) {
            return 0;
        }
        java.util.Set<com.oculus.jj.model.DicomStudy> studies = new java.util.HashSet<>();
        for (Object obj : databaseOutline.getSelectedItems()) {
            if (obj instanceof com.oculus.jj.model.DicomStudy s) {
                studies.add(s);
            } else if (obj instanceof com.oculus.jj.model.DicomSeries ser && ser.getStudy() != null) {
                studies.add(ser.getStudy());
            }
        }
        return studies.size();
    }

    private boolean hasReportInSelectedBrowserStudy() {
        if (databaseOutline == null) {
            return false;
        }
        Object sel = databaseOutline.getSelectedItem();
        com.oculus.jj.model.DicomStudy study = null;
        if (sel instanceof com.oculus.jj.model.DicomStudy s) {
            study = s;
        } else if (sel instanceof com.oculus.jj.model.DicomSeries ser) {
            study = ser.getStudy();
        }
        if (study == null) {
            return false;
        }
        String url = study.getReportURL();
        return url != null && !url.trim().isEmpty();
    }

    private boolean canDeleteSelectedAlbumInBrowser() {
        if (albumTable == null) {
            return false;
        }
        int row = albumTable.getSelectedRow();
        List<com.oculus.jj.model.DicomAlbum> albums = getAlbumArray();
        if (row <= 0 || albums == null || row >= albums.size()) {
            return false;
        }
        com.oculus.jj.model.DicomAlbum album = albums.get(row);
        return album != null && !"Database".equals(album.getName());
    }

    private java.util.List<com.oculus.jj.model.ROI> getSeriesROIsForView(DCMView view) {
        java.util.List<com.oculus.jj.model.ROI> out = new java.util.ArrayList<>();
        if (view == null || view.getDcmPixList() == null) {
            return out;
        }
        java.util.Set<com.oculus.jj.model.ROI> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (DicomPix pix : view.getDcmPixList()) {
            if (pix == null || pix.getROIs() == null) {
                continue;
            }
            for (com.oculus.jj.model.ROI roi : pix.getROIs()) {
                if (roi != null && seen.add(roi)) {
                    out.add(roi);
                }
            }
        }
        return out;
    }

    private java.util.List<com.oculus.jj.model.ROI> getSelectedROIsForView(DCMView view) {
        java.util.List<com.oculus.jj.model.ROI> out = new java.util.ArrayList<>();
        if (view == null) {
            return out;
        }
        if (view.getSelectedRoi() != null) {
            out.add(view.getSelectedRoi());
        }
        for (com.oculus.jj.model.ROI roi : getSeriesROIsForView(view)) {
            if (roi != view.getSelectedRoi() && roi.isSelected()) {
                out.add(roi);
            }
        }
        return out;
    }

    /**
     * HOROS-20240407準拠: `- (BOOL)validateMenuItem:(NSMenuItem *)menuItem` 相当
     * 各ウィンドウコントローラ (BrowserController / ViewerController / VRController /
     * MPRController / CPRController / AppController) が First Responder Chain で
     * 応答・検証するルールを忠実に再現する。
     */
    private boolean validateMenuItemForWindow(String action, javax.swing.JMenuItem item,
            WindowRole role, java.awt.Window ownerWindow, ViewerController viewer2D) {
        if (action == null) {
            // 動的追加アイテム（3D Mode メニューや Recent Studies の履歴項目など）はデフォルト有効
            return true;
        }

        // 常に無効なプレースホルダ項目
        if ("recentEmpty".equals(action)
                || "clutBarUnavailable".equals(action)
                || "zoomParallel".equals(action)) {
            return false;
        }

        // 全ウインドウ共通で有効なグローバル項目 (AppController.m / NSWindow 応答)
        switch (action) {
            case "showDatabase":
            case "preferences":
            case "closeWindow":
            case "pageSetup":
            case "queryDICOM":
            case "autoQuery":
            case "autoQueryRefresh":
            case "abortStoreSCU":
            case "networkLogs":
            case "pacsQuerySettings":
            case "customizeToolbar":
            case "fullscreen":
            case "pluginsManager":
            case "minimize":
            case "bringAllToFront":
            case "shortcuts":
            case "about":
                return true;
            default:
                break;
        }

        // Window メニューの動的項目 (AppController.m validateMenuItem:)
        boolean has2DViewers = !ViewerController.getDisplayed2DViewers().isEmpty();
        boolean has3DViewers = Viewer3DController.getActiveInstance() != null;
        switch (action) {
            case "tile2D":
                return has2DViewers;
            case "tile3D":
                return has3DViewers;
            case "closeAllViewers":
                return has2DViewers || has3DViewers;
            case "resizeWindowItem":
                // DCMView.m resizeWindow: は 2D Viewer のみ応答
                return role == WindowRole.VIEWER_2D;
            default:
                break;
        }

        // ウインドウ種別ごとの Responder Chain + validateMenuItem: 判定
        switch (role) {
            case BROWSER: {
                boolean hasSel = hasDatabaseSelection();
                switch (action) {
                    // File メニュー (BrowserController.m)
                    case "drawerToggle":
                    case "comparativeToggle":
                    case "newDatabaseFolder":
                    case "openDatabaseFolder":
                    case "addAlbum":
                    case "addSmartAlbum":
                    case "saveAlbums":
                    case "importAlbums":
                    case "defaultAlbums":
                    case "importFiles":
                    case "importURL":
                    case "importRawData":
                    case "saveDBListAs":
                    case "search":
                    case "rebuildDatabase":
                    case "rebuildSQL":
                        return true;
                    case "deleteAlbum":
                        return canDeleteSelectedAlbumInBrowser();
                    case "addSelectedStudiesToAlbum":
                    case "openReport":
                    case "export2PACS":
                    case "exportMovie":
                    case "exportJPEG":
                    case "exportTIFF":
                    case "exportDICOMFile":
                    case "exportEmail":
                    case "exportPhotos":
                    case "burn":
                    case "anonymize":
                    case "copyToDBFolder":
                    case "deleteExam":
                    case "metaData":
                    case "compressFiles":
                    case "decompressFiles":
                    case "rebuildThumbnails":
                    case "print":
                    case "dicomPrint":
                    case "networkExport2PACS":
                    case "querySelectedStudy":
                    case "retrievePOD":
                        return hasSel;
                    case "deleteReport":
                    case "convertToPDF":
                    case "convertToDICOMSR":
                        return hasReportInSelectedBrowserStudy();
                    case "mergeStudies":
                        return getSelectedStudyCountInBrowser() >= 2;
                    case "exportRAW":
                        // Export to Raw は ViewerController 専用
                        return false;
                    // Network メニュー
                    case "refreshPOD":
                        return true;
                    // Edit メニュー
                    case "copy":
                        return hasSel;
                    case "undo":
                    case "redo":
                    case "cut":
                    case "paste":
                    case "selectAll":
                        return false;
                    // Format > Font メニュー
                    case "showFonts":
                    case "showColors":
                        return true;
                    case "bold":
                    case "italic":
                    case "bigger":
                    case "smaller":
                        return false;
                    // BrowserController は 2D Viewer / 3D Viewer / ROI メニューのセレクタに応答しないため全てグレーアウト
                    default:
                        return false;
                }
            }

            case VIEWER_2D: {
                DCMView view = viewer2D != null ? viewer2D.getImageView() : getActiveDCMViewForEdit();
                boolean hasImage = view != null && view.getCurDCM() != null;
                int sliceCount = (view != null && view.getDcmPixList() != null) ? view.getDcmPixList().size() : 0;
                boolean isVolume = sliceCount >= 2;
                boolean hasAnyROI = !getSeriesROIsForView(view).isEmpty();
                boolean hasSelectedROI = !getSelectedROIsForView(view).isEmpty();
                boolean multi2DViewers = ViewerController.getDisplayed2DViewers().size() >= 2;
                boolean subActive = isVolume && subCtrlOnOffItem != null && subCtrlOnOffItem.isSelected();

                switch (action) {
                    // File メニュー: ViewerController で応答する項目のみ有効、Database専用項目はグレーアウト
                    case "export2PACS":
                    case "exportMovie":
                    case "exportJPEG":
                    case "exportRAW":
                    case "exportTIFF":
                    case "exportDICOMFile":
                    case "exportEmail":
                    case "exportPhotos":
                    case "anonymize":
                    case "metaData":
                    case "print":
                    case "dicomPrint":
                    case "networkExport2PACS":
                        return hasImage;
                    case "drawerToggle":
                    case "comparativeToggle":
                    case "newDatabaseFolder":
                    case "openDatabaseFolder":
                    case "addAlbum":
                    case "addSmartAlbum":
                    case "addSelectedStudiesToAlbum":
                    case "deleteAlbum":
                    case "saveAlbums":
                    case "importAlbums":
                    case "defaultAlbums":
                    case "openReport":
                    case "deleteReport":
                    case "convertToPDF":
                    case "convertToDICOMSR":
                    case "importFiles":
                    case "importURL":
                    case "importRawData":
                    case "saveDBListAs":
                    case "burn":
                    case "copyToDBFolder":
                    case "search":
                    case "mergeStudies":
                    case "deleteExam":
                    case "compressFiles":
                    case "decompressFiles":
                    case "rebuildDatabase":
                    case "rebuildSQL":
                    case "rebuildThumbnails":
                    case "querySelectedStudy":
                    case "retrievePOD":
                    case "refreshPOD":
                        return false;

                    // Edit メニュー (ViewerController / DCMView)
                    case "undo":
                    case "copy":
                    case "paste":
                        return hasImage;
                    case "redo":
                        return hasImage && annotationUndoManager != null && annotationUndoManager.canRedo();
                    case "cut":
                        return hasSelectedROI;
                    case "selectAll":
                        return hasAnyROI;

                    // Format > Font メニュー
                    case "showFonts":
                    case "bold":
                    case "italic":
                    case "bigger":
                    case "smaller":
                    case "showColors":
                        return hasImage;

                    // 2D Viewer メニュー (ViewerController.m validateMenuItem:)
                    case "nextSeries":
                    case "previousSeries":
                    case "nextPatient":
                    case "previousPatient":
                    case "seriesList":
                    case "convertBWRGB":
                    case "resetImageView":
                    case "revertSeries":
                    case "scale":
                    case "orientation":
                    case "calibrate":
                    case "useVOILUT":
                    case "displayOverlays":
                    case "dicomMetaData":
                    case "convertSUV":
                    case "propagateSettings":
                    case "dontPropagateWLWW":
                    case "propagateInSeries":
                    case "syncSeries":
                    case "syncDifferent":
                    case "keyImage":
                    case "markAllKey":
                    case "unmarkAllKey":
                    case "saveDICOMSC":
                    case "exportKeySC":
                    case "findNextKey":
                    case "findPrevKey":
                    case "annotationsOpen":
                    case "applyWindowProtocol":
                    case "resetWLWW":
                    case "applyCLUT":
                    case "applyOpacity":
                    case "applyConvolution":
                    case "item12Bit":
                    case "saveWorkspace":
                    case "loadWorkspace":
                    case "resetWorkspace":
                        return hasImage;
                    case "markROIsKey":
                        return hasAnyROI;
                    case "sortBy":
                    case "threeDPanel":
                    case "navigator":
                    case "flipSeries":
                    case "resample":
                    case "subCtrlOnOff":
                    case "subCtrlNewMask":
                        return isVolume;
                    case "subSharpen":
                    case "subCtrlParam":
                        return subActive;
                    case "fuse":
                    case "flattenFused":
                        return multi2DViewers;

                    // 3D Viewer メニュー:
                    // HOROS ViewerController.m は MPR/CPR/3D 起動アクション (Group 1 & 2) のみ実装し、
                    // 2スライス以上のボリュームデータのときのみ有効。
                    // Group 3〜8 (Reset 3D, Coronal/Sagittal/Axial, FlyThru, Curved Path, Scissor, 3D Preset) は
                    // VRController / CPRController のセレクタであり 2D Viewer ではグレーアウトされる。
                    case "mprViewer":
                    case "cprViewer":
                    case "orthogonalMPR":
                    case "mipViewer":
                    case "vrViewer":
                    case "surfaceRendering":
                    case "endoscopy":
                        return isVolume;
                    case "reset3D":
                    case "revert3D":
                    case "coronal":
                    case "leftSagittal":
                    case "rightSagittal":
                    case "axial":
                    case "flyThruAdd":
                    case "flyThruRemove":
                    case "flyThruReset":
                    case "curvedPathLoad":
                    case "curvedPathSave":
                    case "scissorSave":
                    case "scissorLoad":
                    case "scissorDelete":
                    case "presetSelect":
                    case "presetSave":
                        return false;

                    // ROI メニュー (ViewerController.m validateMenuItem:)
                    case "importROI":
                    case "roiDefaults":
                    case "roiInfoOnly":
                    case "roiMouseOver":
                    case "roiNameOnly":
                    case "roiCobb":
                    case "roiGrow":
                        return hasImage;
                    case "saveSeriesROI":
                    case "deleteAllROI":
                    case "selectAllROI":
                    case "deselectAllROI":
                    case "roiManager":
                    case "roiVolume":
                    case "roiGen":
                    case "roiDelGen":
                    case "roiSel":
                    case "roiBrush":
                        return hasAnyROI;
                    case "saveSelectedROI":
                    case "deleteSameNameROI":
                    case "roiInfo":
                    case "roiRename":
                    case "incThick":
                    case "decThick":
                    case "incFont":
                    case "decFont":
                    case "flipH":
                    case "flipV":
                    case "roiHist":
                    case "roiLayer":
                    case "roiErase":
                    case "roiRestore":
                    case "roiProp":
                    case "roiPropSlab":
                    case "roiGroup":
                    case "roiUngroup":
                    case "roiLock":
                    case "roiUnlock":
                    case "roiUnsel":
                    case "roiSetPix":
                    case "roiDilate":
                    case "roiClose":
                    case "roiOpen":
                    case "roiConvert":
                    case "roiMerge":
                        return hasSelectedROI;
                    default:
                        return false;
                }
            }

            case VIEWER_3D: {
                // VRController.m / SRController.m / EndoscopyViewer.m
                switch (action) {
                    // File メニュー (3D Viewer で対応するエクスポート・印刷のみ有効)
                    case "exportMovie":
                    case "exportJPEG":
                    case "exportTIFF":
                    case "exportDICOMFile":
                    case "exportEmail":
                    case "exportPhotos":
                    case "print":
                    case "dicomPrint":
                    case "copy":
                        return true;
                    // 2D Viewer メニューのうち WL/WW・CLUT・Opacity は 3D Viewer でも利用可能
                    case "applyWindowProtocol":
                    case "resetWLWW":
                    case "applyCLUT":
                    case "applyOpacity":
                        return true;
                    // 3D Viewer メニュー:
                    // Group 1 & 2 (3D/MPR 起動項目) と Group 6 (Curved Path) は VRController ではグレーアウト。
                    // Group 3, 4, 5, 7, 8 (3D操作・方向ビュー・FlyThru・Scissor・3Dプリセット) が有効。
                    case "reset3D":
                    case "revert3D":
                    case "coronal":
                    case "leftSagittal":
                    case "rightSagittal":
                    case "axial":
                    case "flyThruAdd":
                    case "flyThruRemove":
                    case "flyThruReset":
                    case "scissorSave":
                    case "scissorLoad":
                    case "scissorDelete":
                    case "presetSelect":
                    case "presetSave":
                        return true;
                    default:
                        return false;
                }
            }

            case MPR:
            case CPR: {
                // MPRController.m / OrthogonalMPRController.m / CPRController.m
                boolean isCurved = (role == WindowRole.CPR)
                        || (ownerWindow instanceof MPRController mpr
                                && mpr.getLayoutType() == MPRController.LayoutType.CURVED_MPR);
                boolean is3DMPR = (ownerWindow instanceof MPRController mpr
                        && mpr.getLayoutType() == MPRController.LayoutType.THREE_D_MPR);
                switch (action) {
                    case "exportMovie":
                    case "exportJPEG":
                    case "exportTIFF":
                    case "exportDICOMFile":
                    case "exportEmail":
                    case "exportPhotos":
                    case "print":
                    case "dicomPrint":
                    case "copy":
                    case "resetImageView":
                    case "applyWindowProtocol":
                    case "resetWLWW":
                    case "applyCLUT":
                    case "applyOpacity":
                    case "reset3D":
                    case "revert3D":
                        return true;
                    case "coronal":
                    case "leftSagittal":
                    case "rightSagittal":
                    case "axial":
                        return is3DMPR;
                    case "curvedPathLoad":
                    case "curvedPathSave":
                        return isCurved;
                    default:
                        return false;
                }
            }

            default:
                return false;
        }
    }

    private void showShortcutsDialog() {
        String msg = "<html>" +
                "<h3>Mouse Controls</h3>" +
                "<b>[2D Viewer]</b><br>" +
                "- Left Drag: Window Level / Width (WL/WW)<br>" +
                "- Right Drag / Ctrl+Drag: Zoom<br>" +
                "- Middle Drag / Shift+Drag: Pan<br>" +
                "- Scroll Wheel: Change Image/Slice<br><br>" +
                "<b>[3D Viewer]</b><br>" +
                "- Left Drag: Rotate (when rotate tool is active)<br>" +
                "- Right Drag: <b>Adjust WL/WW (Brightness/Contrast)</b><br>" +
                "- Middle Drag (Wheel Click): <b>Pan (Parallel Move)</b><br>" +
                "- Shift + Left Drag / Scroll Wheel: <b>Zoom (Magnify/Minify)</b><br><br>" +
                "<h3>Keyboard Shortcuts</h3>" +
                "- [R]: Reset to Initial View<br>" +
                "- [Shift+R]: Revert Series<br>" +
                "- [Delete]: Delete selected ROI<br>" +
                "- [Arrow Keys]: Navigate Studies/Images<br>" +
                "</html>";
        JOptionPane.showMessageDialog(this, msg, "Shortcuts & Mouse Controls", JOptionPane.INFORMATION_MESSAGE);
    }

    // ========== メニューアクションメソッド（HOROS-20240407準拠） ==========

    /**
     * ドロワーをトグル
     * HOROS-20240407準拠: - (IBAction)drawerToggle: (id) sender (10611行目)
     */
    public void drawerToggle(Object sender) {
    }

    /**
     * 比較スタディドロワーをトグル
     * HOROS-20240407準拠: - (IBAction)comparativeToggle:(id)sender (10591行目)
     */
    public void comparativeToggle(Object sender) {
    }

    /**
     * データベースフォルダを作成
     * HOROS-20240407準拠: - (IBAction) createDatabaseFolder:(id) sender (1860行目)
     */
    /**
     * データベースフォルダを作成
     * HOROS-20240407準拠: - (IBAction) createDatabaseFolder:(id) sender (1860行目)
     */
    public void createDatabaseFolder(Object sender) {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        fc.setDialogTitle(LanguageManager.get("db.new_folder.title", "New Database Folder"));
        if (fc.showDialog(this, LanguageManager.get("db.new_folder.select", "Choose a location")) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File base = fc.getSelectedFile();
        String name = JOptionPane.showInputDialog(this,
                LanguageManager.get("db.new_folder.name", "Database folder name"), "My Database");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        java.io.File dir = new java.io.File(base, name.trim());
        if (dir.exists()) {
            int ans = JOptionPane.showConfirmDialog(this,
                    LanguageManager.get("db.new_folder.exists", "Folder already exists. Overwrite?"),
                    LanguageManager.get("db.new_folder.title", "New Database Folder"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ans != JOptionPane.YES_OPTION) return;
        }
        if (dir.mkdirs()) {
            new java.io.File(dir, DicomDatabase.OSIRIX_DATA_DIR_NAME).mkdirs();
            infoFmt(dir.getAbsolutePath(), LanguageManager.get("db.new_folder.done", "Database folder created"));
        } else {
            infoFmt(LanguageManager.get("db.new_folder.fail", "Failed to create folder"),
                    LanguageManager.get("db.new_folder.title", "New Database Folder"));
        }
    }

    /**
     * データベースフォルダを開く
     * HOROS-20240407準拠: -(IBAction)openDatabase:(id)sender (1843行目)
     */
    public void openDatabase(Object sender) {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        fc.setDialogTitle(LanguageManager.get("db.open.title", "Open Database Folder"));
        if (fc.showDialog(this, LanguageManager.get("db.open.select", "Open")) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        DicomDatabase db = DicomDatabase.databaseAtPath(fc.getSelectedFile().getAbsolutePath());
        if (db != null) {
            DicomDatabase.setActiveLocalDatabase(db);
            infoFmt(db.getName() != null ? db.getName() : "(opened)",
                    LanguageManager.get("db.open.done", "Database opened"));
        } else {
            infoFmt(LanguageManager.get("db.open.fail", "No valid database found at the selected folder"),
                    LanguageManager.get("db.open.title", "Open Database Folder"));
        }
    }

    /**
     * アルバムを保存
     * HOROS-20240407準拠: - (IBAction) saveAlbums:(id) sender (10811行目)
     */
    public void saveAlbums(Object sender) {
        DicomDatabase db = DicomDatabase.activeLocalDatabase();
        if (db == null) {
            infoFmt(LanguageManager.get("db.album.no_db", "No active database."),
                    LanguageManager.get("db.album.save.title", "Save Albums"));
            return;
        }
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle(LanguageManager.get("db.album.save.title", "Save Albums"));
        fc.setSelectedFile(new java.io.File("DatabaseAlbums.albums"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("OculusJJ albums (*.albums)", "albums"));
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        java.io.File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".albums")) {
            file = new java.io.File(file.getAbsolutePath() + ".albums");
        }
        try {
            db.saveAlbumsToPath(file.getAbsolutePath());
            infoFmt(file.getAbsolutePath(), LanguageManager.get("db.album.save.ok", "Albums saved."));
        } catch (Exception ex) {
            infoFmt(String.valueOf(ex.getMessage()), LanguageManager.get("db.album.save.fail", "Save failed."));
        }
    }

    /**
     * アルバムをインポート
     * HOROS-20240407準拠: - (IBAction) addAlbums:(id) sender (10834行目)
     * 簡易形式: 1行1アルバム名、条件付きは「名前|predicate」
     */
    public void addAlbums(Object sender) {
        DicomDatabase db = DicomDatabase.activeLocalDatabase();
        if (db == null) {
            infoFmt(LanguageManager.get("db.album.no_db", "No active database."),
                    LanguageManager.get("db.album.import.title", "Import Albums"));
            return;
        }
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle(LanguageManager.get("db.album.import.title", "Import Albums"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("OculusJJ albums (*.albums)", "albums"));
        if (fc.showDialog(this, LanguageManager.get("db.album.import.select", "Open")) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File f = fc.getSelectedFile();
        try {
            int before = db.getAlbums().size();
            db.loadAlbumsFromPath(f.getAbsolutePath());
            int added = Math.max(0, db.getAlbums().size() - before);
            refreshAlbums();
            infoFmt(String.format(LanguageManager.get("db.album.import.done", "Imported {0} album(s)."), added),
                    LanguageManager.get("db.album.import.title", "Import Albums"));
        } catch (Exception ex) {
            infoFmt(String.valueOf(ex.getMessage()), LanguageManager.get("db.album.import.fail", "Import failed"));
        }
    }

    /**
     * レポートを生成
     * HOROS-20240407準拠: - (IBAction) generateReport: (id)sender (18655行目)
     * 
     * /**
     * ローカルデータベースにリセット
     * HOROS-20240407準拠: - (void)resetToLocalDatabase (1654行目)
     */
    public void resetToLocalDatabase() {
        // HOROS-20240407準拠: [self setDatabase:[DicomDatabase activeLocalDatabase]];
        if (database != null) {
            // DicomDatabase localDatabase = DicomDatabase.getActiveLocalDatabase();
            // setDatabase(localDatabase);
        }
    }

    /**
     * データベースパスを開く
     * HOROS-20240407準拠: - (void)openDatabasePath: (NSString*)path (20201行目)
     * 
     * @param path データベースのパス
     */
    public void openDatabasePath(String path) {
        // HOROS-20240407準拠: BrowserController.m 20201-20223行目
        // NSThread* thread = [NSThread currentThread];
        // [thread setName:NSLocalizedString(@"Opening database...", nil)];
        // ThreadModalForWindowController* tmc = [thread
        // startModalForWindow:self.window];
        //
        // @try
        // {
        // DicomDatabase* db = [DicomDatabase databaseAtPath:path];
        // if( db)
        // [self setDatabase:db];
        // else
        // [NSException raise:NSGenericException format: @"DicomDatabase == nil"];
        // }
        // @catch (NSException* e)
        // {
        // N2LogExceptionWithStackTrace(e);
        // NSRunAlertPanel(NSLocalizedString(@"Horos Database", nil), NSLocalizedString(
        // @"Horos cannot read/create this file/folder. Permissions error?", nil), nil,
        // nil, nil);
        // [self resetToLocalDatabase];
        // }
        //
        // [tmc invalidate];

        Thread thread = new Thread(() -> {
            try {
                Thread.currentThread().setName("Opening database...");
                // DicomDatabase db = DicomDatabase.databaseAtPath(path);
                // if (db != null) {
                // SwingUtilities.invokeLater(() -> {
                // setDatabase(db);
                // });
                // } else {
                // throw new Exception("DicomDatabase == null");
                // }

                // 現在はローカルデータベースのみサポート
                SwingUtilities.invokeLater(() -> {
                    resetToLocalDatabase();
                });
            } catch (Exception e) {
                // HOROS-20240407準拠: エラーが発生した場合はローカルデータベースにリセット
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    resetToLocalDatabase();
                });
            }
        });
        thread.setName("Opening database...");
        ThreadsManager.defaultManager().addThreadAndStart(thread);
    }

    /**
     * デフォルトデータベースに切り替え（必要に応じて）
     * HOROS-20240407準拠: - (void) switchToDefaultDBIfNeeded (20193行目) //
     * __deprecated
     */
    public void switchToDefaultDBIfNeeded() {
        // HOROS-20240407準拠: BrowserController.m 20193-20199行目
        // NSString *defaultPath = [self documentsDirectoryFor: [[NSUserDefaults
        // standardUserDefaults] integerForKey: @"DEFAULT_DATABASELOCATION"] url:
        // [[NSUserDefaults standardUserDefaults] stringForKey:
        // @"DEFAULT_DATABASELOCATIONURL"]];
        //
        // if( [[self documentsDirectory] isEqualToString: defaultPath] == NO)
        // [self resetToLocalDatabase];
        // String defaultPath = getDocumentsDirectoryFor(...);
        // String currentPath = getDocumentsDirectory();
        // if (!defaultPath.equals(currentPath)) {
        // resetToLocalDatabase();
        // }
    }

    /**
     * ソース識別子を取得（行インデックスから）
     * HOROS-20240407準拠: - (DataNodeIdentifier*)sourceIdentifierAtRow: (int)row
     * 
     * @param row 行インデックス
     * @return ソース識別子（DataNodeIdentifier相当）、存在しない場合はnull
     */
    public Object sourceIdentifierAtRow(int row) {
        // HOROS-20240407準拠: BrowserController.m 2945行目
        // DataNodeIdentifier* bs = [self sourceIdentifierAtRow: [_sourcesTableView
        // selectedRow]];
        //
        // if( bs)
        // description = [description stringByAppendingFormat:NSLocalizedString(@"%@: %@
        // / ", nil), [_database isLocal] ? NSLocalizedString( @"Local Database", nil) :
        // NSLocalizedString( @"Remote Database", nil), [bs description]];
        // if (bonjourBrowser != null && row > 0) {
        // // BonjourBrowserからサービスを取得
        // return bonjourBrowser.getServiceAtRow(row - 1);
        // }

        // 現在はローカルデータベースのみサポート
        return null;
    }

    /**
     * サーバーを選択（DICOM送信用）
     * HOROS-20240407準拠: - (void) selectServer: (NSArray*)objects (18098行目)
     * 
     * @param objects 送信するオブジェクトのリスト
     */
    public void selectServer(java.util.List<Object> objects) {
        // HOROS-20240407準拠: BrowserController.m 18098-18102行目
        // if( [objects count] > 0) [SendController sendFiles: objects];
        // else NSRunCriticalAlertPanel(NSLocalizedString(@"DICOM
        // Send",nil),NSLocalizedString( @"No files are
        // selected...",nil),NSLocalizedString( @"OK",nil), nil, nil);

        if (objects != null && !objects.isEmpty()) {
            // SendController.sendFiles(objects);
        } else {
            // HOROS-20240407準拠: ファイルが選択されていない場合はアラートを表示
            javax.swing.JOptionPane.showMessageDialog(
                    this,
                    "No files are selected...",
                    "DICOM Send",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * パスをアンマウント
     * HOROS-20240407準拠: - (void)unmountPath: (NSString*)path (18054行目)
     * 
     * @param path アンマウントするパス
     */
    public void unmountPath(String path) {
        // HOROS-20240407準拠: BrowserController.m 18054-18080行目
        // [_sourcesTableView display];
        //
        // int attempts = 0;
        // BOOL success = NO;
        // while( success == NO)
        // {
        // success = [[NSWorkspace sharedWorkspace] unmountAndEjectDeviceAtPath: path];
        // if( success == NO)
        // {
        // attempts++;
        // if( attempts < 5)
        // {
        // [NSThread sleepForTimeInterval: 1.0];
        // }
        // else success = YES;
        // }
        // }
        //
        // [_sourcesTableView display];
        // [_sourcesTableView setNeedsDisplay];
        //
        // if( attempts == 5)
        // {
        // NSRunCriticalAlertPanel(NSLocalizedString(@"Failed", nil),
        // NSLocalizedString(@"Unable to unmount this disk. This disk is probably in
        // used by another application.", nil), NSLocalizedString(@"OK",nil),nil, nil);
        // }
        // Javaでは、java.nio.file.FileSystemsを使用してアンマウント処理を実装する必要がある
        // 現在は未実装
    }

    /**
     * 代替ボタンが押された
     * HOROS-20240407準拠: - (void)alternateButtonPressed: (NSNotification*)n
     * (18082行目)
     * 
     * @param notification 通知オブジェクト
     */
    public void alternateButtonPressed(Object notification) {
        // HOROS-20240407準拠: BrowserController.m 18082-18092行目
        // int i = [_sourcesTableView selectedRow];
        // if( i > 0)
        // {
        // NSString *path = [[[bonjourBrowser services] objectAtIndex: i-1]
        // valueForKey:@"Path"];
        //
        // [self resetToLocalDatabase];
        // [self unmountPath: path];
        // }
        if (sourcesTableView != null) {
            int selectedRow = sourcesTableView.getSelectedRow();
            if (selectedRow > 0) {
                // if (bonjourBrowser != null) {
                // Object service = bonjourBrowser.getServiceAtRow(selectedRow - 1);
                // if (service != null) {
                // String path = getPathFromService(service);
                // resetToLocalDatabase();
                // unmountPath(path);
                // }
                // }
            }
        }
    }

    /**
     * ソース選択が変更された
     * HOROS-20240407準拠: Sourcesテーブルの選択変更時に呼ばれる
     */
    private void sourceSelectionChanged() {
        // HOROS-20240407準拠: 選択されたソースに基づいてデータベースを切り替え
        if (sourcesTableView != null) {
            int selectedRow = sourcesTableView.getSelectedRow();
            if (selectedRow == 0) {
                // HOROS-20240407準拠: 最初の行（"Documents DB"）が選択された場合はローカルデータベースにリセット
                resetToLocalDatabase();
            } else if (selectedRow > 0) {
                // Object sourceIdentifier = sourceIdentifierAtRow(selectedRow);
                // if (sourceIdentifier != null) {
                // String path = getPathFromSourceIdentifier(sourceIdentifier);
                // openDatabasePath(path);
                // }
            }
        }
    }

    /**
     * レポートを削除
     * HOROS-20240407準拠: - (IBAction)deleteReport: (id)sender (18583行目)
     */
    /**
     * 現在アクティブなStudyを取得（ビューアから or データベース選択から）
     * HOROS: [self databaseSelection] → study / [study valueForKey:@"study"]
     */
    private com.oculus.jj.model.DicomStudy activeStudy() {
        DCMView view = getActiveDCMViewForEdit();
        if (view != null && view.getCurDCM() != null) {
            DicomPix pix = view.getCurDCM();
            if (pix.getImageObj() != null && pix.getImageObj().getSeries() != null
                    && pix.getImageObj().getSeries().getStudy() != null) {
                return pix.getImageObj().getSeries().getStudy();
            }
        }
        if (databaseOutline != null) {
            Object sel = databaseOutline.getSelectedItem();
            if (sel instanceof com.oculus.jj.model.DicomStudy) {
                return (com.oculus.jj.model.DicomStudy) sel;
            }
            if (sel instanceof com.oculus.jj.model.DicomSeries) {
                com.oculus.jj.model.DicomSeries s = (com.oculus.jj.model.DicomSeries) sel;
                if (s.getStudy() != null) return s.getStudy();
            }
        }
        return null;
    }

    public void deleteReport(Object sender) {
        com.oculus.jj.model.DicomStudy study = activeStudy();
        if (study == null) {
            infoFmt(LanguageManager.get("report.delete.no_study", "No study selected."),
                    LanguageManager.get("report.title", "Report"));
            return;
        }
        String url = study.getReportURL();
        if (url == null || url.isEmpty()) {
            infoFmt(LanguageManager.get("report.delete.none", "No report associated with this study."),
                    LanguageManager.get("report.title", "Report"));
            return;
        }
        int ans = JOptionPane.showConfirmDialog(this,
                LanguageManager.get("report.delete.confirm", "Are you sure you want to delete the selected report?"),
                LanguageManager.get("report.delete.title", "Delete Report"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ans != JOptionPane.YES_OPTION) return;
        java.io.File f = new java.io.File(url);
        boolean removed = f.delete();
        study.setReportURL(null);
        try {
            DicomDatabase db = DicomDatabase.activeLocalDatabase();
            if (db != null) db.save();
        } catch (Exception ex) { /* ignore */ }
        infoFmt(f.getAbsolutePath() + (removed ? " " + LanguageManager.get("report.delete.ok", "(deleted)") : " " + LanguageManager.get("report.delete.not_found", "(file not found / cleared)")),
                LanguageManager.get("report.delete.title", "Delete Report"));
    }

    /**
     * レポートをPDFに変換
     * HOROS-20240407準拠: - (IBAction) convertReportToPDF: (id)sender (18542行目)
     * [study saveReportAsPdfAtPath:] — Java版: 現在StudyのreportURL HTMLを
     * JEditorPane の print() サポートでレンダリングし、PDFプリンタ（Windows の
     * 「Microsoft Print to PDF」など）経由で保存する。
     */
    public void convertReportToPDF(Object sender) {
        com.oculus.jj.model.DicomStudy study = activeStudy();
        if (study == null) {
            infoFmt(LanguageManager.get("report.pdf.no_study", "No study selected."),
                    LanguageManager.get("report.title", "Report"));
            return;
        }
        String url = study.getReportURL();
        if (url == null || url.isEmpty()) {
            infoFmt(LanguageManager.get("report.pdf.none",
                    "No report associated with this study. Generate one first (File > Report > Open report)."),
                    LanguageManager.get("report.title", "Report"));
            return;
        }
        java.io.File html = new java.io.File(url);
        if (!html.exists()) {
            infoFmt(html.getAbsolutePath(),
                    LanguageManager.get("report.pdf.missing", "Report file not found."));
            return;
        }
        String base = study.getName() != null ? study.getName() : "Study";
        int ans = JOptionPane.showConfirmDialog(this,
                LanguageManager.get("report.pdf.hint",
                        "This opens a print dialog. Choose a PDF printer (e.g. \"Microsoft Print to PDF\"), then save as \"" + base + "-Report.pdf\"."),
                LanguageManager.get("report.pdf.title", "Convert to PDF"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ans != JOptionPane.OK_OPTION) return;
        final java.io.File htmlFile = html;
        new Thread(() -> {
            final Exception[] err = new Exception[1];
            final boolean[] printed = { false };
            try {
                String htmlContent = new String(java.nio.file.Files.readAllBytes(htmlFile.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
                javax.swing.SwingUtilities.invokeAndWait(() -> {
                    try {
                        javax.swing.JEditorPane pane = new javax.swing.JEditorPane("text/html", htmlContent);
                        pane.setOpaque(true);
                        pane.setForeground(java.awt.Color.BLACK);
                        java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
                        job.setJobName("OculusJJ Report - " + base);
                        job.setPrintable((java.awt.print.Printable) pane, job.defaultPage());
                        job.print();
                        printed[0] = true;
                    } catch (Exception ex) {
                        err[0] = ex;
                    }
                });
            } catch (Exception ex) {
                err[0] = ex;
            }
            final boolean done = printed[0];
            final Exception e = err[0];
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (e != null) {
                    infoFmt(String.valueOf(e.getMessage()),
                            LanguageManager.get("report.pdf.fail", "PDF export failed"));
                } else if (done) {
                    infoFmt(LanguageManager.get("report.pdf.sent", "Sent to printer. Choose \"Save as PDF\" to save as a PDF file."),
                            LanguageManager.get("report.pdf.title", "Convert to PDF"));
                }
            });
        }, "report-pdf").start();
    }

    /**
     * レポートをDICOM SR（STUDY REPORT）に変換
     * HOROS-20240407準拠: - (IBAction) convertReportToDICOMSR: (id)sender (18494行目)
     * [study saveReportAsDicomAtPath:] — Java版: dcm4che-core で
     * STUDY REPORT (1.2.840.10008.5.1.4.1.1.88.33) 形式のDICOMオブジェクトを生成し、
     * study.databaseに追加・保存する。
     */
    public void convertReportToDICOMSR(Object sender) {
        com.oculus.jj.model.DicomStudy study = activeStudy();
        if (study == null) {
            infoFmt(LanguageManager.get("report.dcm.no_study", "No study selected."),
                    LanguageManager.get("report.title", "Report"));
            return;
        }
        String url = study.getReportURL();
        if (url == null || url.isEmpty()) {
            infoFmt(LanguageManager.get("report.dcm.none", "No report associated with this study. Generate one first."),
                    LanguageManager.get("report.title", "Report"));
            return;
        }
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        String base = study.getName() != null ? study.getName() : "Study";
        fc.setSelectedFile(new java.io.File(base + "-Report.dcm"));
        fc.setDialogTitle(LanguageManager.get("report.dcm.save.title", "Save Report as DICOM SR"));
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File out = fc.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".dcm")) {
            out = new java.io.File(out.getAbsolutePath() + ".dcm");
        }
        String htmlContent;
        try {
            htmlContent = new String(java.nio.file.Files.readAllBytes(new java.io.File(url).toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            infoFmt(String.valueOf(ex.getMessage()), LanguageManager.get("report.dcm.fail", "DICOM SR export failed"));
            return;
        }
        // HTML→プレーンテキストへ簡易変換（<p>/<br>は改行、タグ除去）
        String text = htmlContent
                .replaceAll("(?i)<\\s*(p|div|tr|h[1-6]|li|table)[^>]*>", "\n")
                .replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n")
                .replaceAll("(?i)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n");

        java.text.SimpleDateFormat dtF = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
        String nowDT = dtF.format(new java.util.Date());
        String uidRoot = "1.2.826.0.1.3680043.8.498"; // OculusJJ private UID root
        String sopUID = uidRoot + "." + System.currentTimeMillis() + "." + (System.nanoTime() % 100000L);
        String studyUID = study.getStudyInstanceUID();
        if (studyUID == null || studyUID.isEmpty())
            studyUID = uidRoot + ".study." + System.currentTimeMillis();
        String patientID = study.getPatientID() != null && !study.getPatientID().isEmpty()
                ? study.getPatientID() : "UNKNOWN";

        org.dcm4che3.data.Attributes attrs = new org.dcm4che3.data.Attributes();
        attrs.setString(0x00080005, org.dcm4che3.data.VR.UI, "ISO_IR 100"); // SpecificCharacterSet
        attrs.setString(0x00080016, org.dcm4che3.data.VR.UI, "1.2.840.10008.5.1.4.1.1.88.33"); // SOPClassUID (STUDY REPORT)
        attrs.setString(0x00080018, org.dcm4che3.data.VR.UI, sopUID); // SOPInstanceUID
        attrs.setString(0x00080020, org.dcm4che3.data.VR.DA, nowDT.substring(0, 8)); // StudyDate
        attrs.setString(0x00080060, org.dcm4che3.data.VR.CS, "SR"); // Modality
        attrs.setString(0x00081030, org.dcm4che3.data.VR.LO,
                study.getName() != null ? study.getName() : "Study"); // StudyDescription
        attrs.setString(0x00100020, org.dcm4che3.data.VR.SH, patientID); // PatientID
        attrs.setString(0x0020000D, org.dcm4che3.data.VR.UI, studyUID); // StudyInstanceUID
        attrs.setString(0x00400244, org.dcm4che3.data.VR.CS, "PRELIMINARY"); // ReportStatusID
        attrs.setString(0x00400250, org.dcm4che3.data.VR.UI, "1.3.6.1.4.1.11456.9000.1.2.1.3.1"); // ReportClassID
        attrs.setString(0x00400260, org.dcm4che3.data.VR.DT, nowDT); // ReportDateTime
        attrs.setString(0x00404010, org.dcm4che3.data.VR.UT, text); // ReportDetailedDescription

        java.io.File parent = out.getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();
        try {
            org.dcm4che3.io.DicomOutputStream dos = new org.dcm4che3.io.DicomOutputStream(out);
            org.dcm4che3.data.Attributes fmi = new org.dcm4che3.data.Attributes();
            fmi.setString(0x00020000, org.dcm4che3.data.VR.UI, "1.2.840.10008.5.1.4.1.1.88.33"); // MediaStorageSOPClassUID
            fmi.setString(0x00020001, org.dcm4che3.data.VR.UI, sopUID); // MediaStorageSOPInstanceUID
            fmi.setString(0x00020002, org.dcm4che3.data.VR.UI, "1.2.840.10008.1.2"); // TransferSyntaxUID (Explicit VR LE)
            dos.writeFileMetaInformation(fmi);
            dos.writeDataset(attrs, null);
            dos.finish();
            dos.close();
            infoFmt(out.getAbsolutePath(), LanguageManager.get("report.dcm.done", "Report saved as DICOM SR"));
        } catch (Exception ex) {
            infoFmt(String.valueOf(ex.getMessage()), LanguageManager.get("report.dcm.fail", "DICOM SR export failed"));
        }
    }

    /**
     * Rawデータをインポート
     * HOROS-20240407準拠: - (IBAction)importRawData:(id)sender (18186行目)
     */
    public void importRawData(Object sender) {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Raw images", "raw", "dat"));
        if (fc.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            importFiles(java.util.Collections.singletonList(fc.getSelectedFile().getAbsolutePath()));
        }
    }

    /**
     * メールを送信
     * HOROS-20240407準拠: -(IBAction)sendMail:(id)sender (16940行目)
     * 現行: 現行フレームをJPEGにエクスポートし、mailto URIでメーラを起動
     */
    public void sendMail(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null || view.curDCM == null) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("export.no.view", "No active viewer to export"),
                    "Export to Email", JOptionPane.WARNING_MESSAGE);
            return;
        }
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Export JPEG (for Email)");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JPEG images", "jpg", "jpeg"));
        fc.setApproveButtonText("Save");
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".jpg") && !file.getName().toLowerCase().endsWith(".jpeg")) {
            file = new java.io.File(file.getAbsolutePath() + ".jpg");
        }
        exportImageSingle(view.curDCM, file, "JPEG", "Export to Email");
        if (file.exists()) {
            try {
                String dcm = (view.curDCM.getSeriesDescription() != null
                        ? view.curDCM.getSeriesDescription() : "OculusJJ");
                String subject = java.net.URLEncoder.encode(
                        (view.curDCM.getPatientID() != null ? view.curDCM.getPatientID() + " - " : "") + dcm,
                        java.nio.charset.StandardCharsets.UTF_8.name());
                String body = java.net.URLEncoder.encode(
                        LanguageManager.get("mail.body", "Please find the attached DICOM image export.") + "\n",
                        java.nio.charset.StandardCharsets.UTF_8.name());
                String uri = "mailto:?subject=" + subject + "&body=" + body;
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isDesktopSupported()) {
                    desktop.browse(java.net.URI.create(uri));
                } else {
                    JOptionPane.showMessageDialog(this,
                            LanguageManager.get("mail.attach.hint",
                                    "Attach the file below to your email:") + "\n" + file.getAbsolutePath(),
                            "Export to Email", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        LanguageManager.get("mail.attach.hint", "Attach the file below to your email:") + "\n"
                                + file.getAbsolutePath() + "\n(" + ex.getMessage() + ")",
                        "Export to Email", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * データベースリストを保存
     * HOROS-20240407準拠: - (IBAction) saveDBListAs:(id) sender (8825行目)
     * 現行: DatabaseOutlineView のツリー構造をCSVまたはテキストでエクスポート
     */
    public void saveDBListAs(Object sender) {
        if (databaseOutline == null) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("dblist.no.data", "No database list to export"),
                    "Export Database List", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int choice = JOptionPane.showOptionDialog(this,
                LanguageManager.get("dblist.format.choose", "Choose export format:"),
                "Export Database List",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new String[] {
                        LanguageManager.get("dblist.format.csv", "CSV"),
                        LanguageManager.get("dblist.format.txt", "Plain Text"),
                        LanguageManager.get("common.cancel", "Cancel")
                },
                LanguageManager.get("dblist.format.csv", "CSV"));
        if (choice < 0) {
            return;
        }
        boolean csv = (choice == 0);
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Export Database List");
        fc.setSelectedFile(new java.io.File(csv ? "DatabaseList.csv" : "DatabaseList.txt"));
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File out = fc.getSelectedFile();
        if (csv && !out.getName().toLowerCase().endsWith(".csv")) {
            out = new java.io.File(out.getAbsolutePath() + ".csv");
        }
        try (java.io.BufferedWriter w = new java.io.BufferedWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(out),
                        java.nio.charset.StandardCharsets.UTF_8))) {
            w.write("\uFEFF"); // BOM (Excel互換)
            databaseOutline.exportAsText(csv, w);
            w.flush();
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("export.saved", "Exported to:") + "\n" + out.getAbsolutePath(),
                    "Export Database List", JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("export.failed", "Export failed:") + " " + ex.getMessage(),
                    "Export Database List", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * DICOMを匿名化
     * HOROS-20240407準拠: - (IBAction)anonymizeDICOM:(id)sender (17978行目)
     */
    public void anonymizeDICOM(Object sender) {
    }

    /**
     * データベースフォルダにコピー
     * HOROS-20240407準拠: - (IBAction) copyToDBFolder: (id) sender (2110行目)
     */
    public void copyToDBFolder(Object sender) {
    }

    /**
     * 検索フィールド
     * HOROS-20240407準拠: - (IBAction) searchField: (id)sender (13752行目)
     */
    public void searchField(Object sender) {
        if (searchField != null) {
            searchField.requestFocus();
        }
    }

    /**
     * スタディをマージ
     * HOROS-20240407準拠: - (IBAction) mergeStudies:(id) sender (5501行目)
     */
    public void mergeStudies(Object sender) {
        if (database == null || databaseOutline == null) {
            infoFmt(LanguageManager.get("db.merge.no_db", "No active database."),
                    LanguageManager.get("db.merge.title", "Merge Studies"));
            return;
        }
        List<Object> selected = databaseOutline.getSelectedItems();
        List<com.oculus.jj.model.DicomStudy> studies = new ArrayList<>();
        for (Object item : selected) {
            if (item instanceof com.oculus.jj.model.DicomStudy) {
                com.oculus.jj.model.DicomStudy study = (com.oculus.jj.model.DicomStudy) item;
                if (!studies.contains(study)) studies.add(study);
            }
        }
        if (studies.size() < 2) {
            infoFmt(LanguageManager.get("db.merge.no_selection", "Select at least two studies to merge."),
                    LanguageManager.get("db.merge.title", "Merge Studies"));
            return;
        }
        com.oculus.jj.model.DicomStudy destination = studies.get(0);
        String warning = String.format(LanguageManager.get("db.merge.files_warning",
                "Merge %d studies into '%s' in the database only? Source DICOM files are not changed; their original Study Instance UIDs remain, so reimport may recreate the old studies."),
                studies.size(), destination.getName());
        int answer = JOptionPane.showConfirmDialog(this, warning,
                LanguageManager.get("db.merge.title", "Merge Studies"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        if (!database.mergeStudiesDatabaseOnly(destination, studies)) {
            infoFmt(LanguageManager.get("db.merge.failed", "The database-only merge failed."),
                    LanguageManager.get("db.merge.title", "Merge Studies"));
            return;
        }
        refreshDatabase(null);
        refreshAlbums();
        if (databaseOutline != null) databaseOutline.selectStudy(destination);
        infoFmt(String.format(LanguageManager.get("db.merge.done", "Merged %d source study/studies into the destination."),
            studies.size() - 1), LanguageManager.get("db.merge.title", "Merge Studies"));
    }

    /**
     * アイテムを削除
     * HOROS-20240407準拠: - (IBAction)delItem: (id)sender (5999行目)
     */
    public void delItem(Object sender) {
        if (database == null || databaseOutline == null) {
            infoFmt(LanguageManager.get("db.delete.no_db", "No active database."),
                    LanguageManager.get("db.delete.title", "Delete Items"));
            return;
        }
        List<Object> selected = databaseOutline.getSelectedItems();
        if (selected.isEmpty()) {
            infoFmt(LanguageManager.get("db.delete.no_selection", "Select one or more studies or series first."),
                    LanguageManager.get("db.delete.title", "Delete Items"));
            return;
        }

        List<com.oculus.jj.model.DicomStudy> studies = new ArrayList<>();
        List<com.oculus.jj.model.DicomSeries> seriesList = new ArrayList<>();
        for (Object item : selected) {
            if (item instanceof com.oculus.jj.model.DicomStudy) {
                studies.add((com.oculus.jj.model.DicomStudy) item);
            } else if (item instanceof com.oculus.jj.model.DicomSeries) {
                seriesList.add((com.oculus.jj.model.DicomSeries) item);
            }
        }
        if (studies.isEmpty() && seriesList.isEmpty()) {
            infoFmt(LanguageManager.get("db.delete.unsupported", "The selected rows cannot be deleted."),
                    LanguageManager.get("db.delete.title", "Delete Items"));
            return;
        }

        List<com.oculus.jj.model.DicomAlbum> albumArray = getAlbumArray();
        int albumRow = albumTable != null ? albumTable.getSelectedRow() : -1;
        com.oculus.jj.model.DicomAlbum currentAlbum = albumRow >= 0 && albumRow < albumArray.size()
                ? albumArray.get(albumRow) : null;
        boolean canRemoveFromAlbum = studies.size() > 0 && seriesList.isEmpty() && currentAlbum != null
                && !"Database".equals(currentAlbum.getName())
                && !Boolean.TRUE.equals(currentAlbum.isSmartAlbum());

        String deleteFromDatabase = LanguageManager.get("db.delete.database_only", "Delete database records (keep DICOM files)");
        String removeFromAlbum = LanguageManager.get("db.delete.remove_album", "Remove from current album");
        String cancel = LanguageManager.get("common.cancel", "Cancel");
        Object[] options = canRemoveFromAlbum
                ? new Object[] { removeFromAlbum, deleteFromDatabase, cancel }
                : new Object[] { deleteFromDatabase, cancel };
        String detail = String.format(LanguageManager.get("db.delete.confirm",
                "Delete %d study/study record(s)? Original DICOM files will remain on disk."), selected.size());
        int choice = JOptionPane.showOptionDialog(this, detail,
                LanguageManager.get("db.delete.title", "Delete Items"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[options.length - 1]);
        if (choice < 0 || options[choice].equals(cancel)) return;

        if (canRemoveFromAlbum && options[choice].equals(removeFromAlbum)) {
            for (com.oculus.jj.model.DicomStudy study : studies) currentAlbum.removeStudiesObject(study);
            if (!database.save()) {
                infoFmt(LanguageManager.get("db.delete.failed", "Failed to update the album."),
                        LanguageManager.get("db.delete.title", "Delete Items"));
                return;
            }
        } else {
            boolean deleted = true;
            for (com.oculus.jj.model.DicomStudy study : studies) deleted &= database.deleteStudy(study);
            for (com.oculus.jj.model.DicomSeries series : seriesList) deleted &= database.deleteSeries(series);
            if (!deleted) {
                outlineViewRefresh();
                refreshAlbums();
                infoFmt(LanguageManager.get("db.delete.failed", "Failed to update the database."),
                        LanguageManager.get("db.delete.title", "Delete Items"));
                return;
            }
            if (!database.save()) {
                infoFmt(LanguageManager.get("db.delete.failed", "Failed to update the database."),
                        LanguageManager.get("db.delete.title", "Delete Items"));
                return;
            }
        }
        outlineViewRefresh();
        refreshAlbums();
        infoFmt(String.format(LanguageManager.get("db.delete.done", "Removed %d selected row(s)."), selected.size()),
            LanguageManager.get("db.delete.title", "Delete Items"));
    }

    /**
     * XMLを表示
     * HOROS-20240407準拠: - (IBAction) viewXML:(id) sender (18391行目)
     */
    public void viewXML(Object sender) {
        String filePath = null;
        DCMView activeView = getActiveDCMViewForEdit();
        if (activeView != null && activeView.getCurDCM() != null && activeView.getCurDCM().getPath() != null) {
            filePath = activeView.getCurDCM().getPath().toString();
        }
        if (filePath == null) {
            Object selected = getSelectedItem();
            if (selected instanceof com.oculus.jj.model.DicomImage image) {
                filePath = image.completePath();
            } else if (selected instanceof com.oculus.jj.model.DicomSeries series) {
                List<com.oculus.jj.model.DicomImage> images = series.sortedImages();
                if (!images.isEmpty()) filePath = images.get(0).completePath();
            }
        }
        if (filePath == null || filePath.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("meta.nofile", "No DICOM file selected."),
                    LanguageManager.get("meta.title", "DICOM Meta-Data"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DicomMetadataDialog dialog = new DicomMetadataDialog(this, filePath);
        dialog.setVisible(true);
    }

    /**
     * ファイルを圧縮
     * HOROS-20240407準拠: - (IBAction) compressSelectedFiles: (id)sender (15952行目)
     */
    public void compressSelectedFiles(Object sender) {
    }

    /**
     * ファイルを展開
     * HOROS-20240407準拠: - (IBAction)decompressSelectedFiles: (id)sender (15982行目)
     */
    public void decompressSelectedFiles(Object sender) {
    }

    /**
     * データベースを再構築
     * HOROS-20240407準拠: - (IBAction) ReBuildDatabaseSheet: (id)sender (2459行目)
     */
    public void ReBuildDatabaseSheet(Object sender) {
    }

    /**
     * デフォルトアルバムの作成
     * HOROS-20240407準拠: BrowserController.m:1379 defaultAlbums: → [self.database addDefaultAlbums]
     * 簡易実装: 「Favorites」/「Recently Used」などのプレフィックス付きアルバムを作成し、
     * database（DicomDatabase）に追加してUIをリフレッシュする。
     */
    public void createDefaultAlbums() {
        DicomDatabase db = DicomDatabase.activeLocalDatabase();
        if (db == null) {
            infoFmt(LanguageManager.get("db.default_albums.no_db", "No active database."),
                    LanguageManager.get("db.default_albums.title", "Create Default Albums"));
            return;
        }
        String[] defaults = { "Favorites", "Recently Used", "Screen Captures" };
        int added = 0;
        for (String name : defaults) {
            // 重複防止: 同じ名前のアルバムが存在するか確認
            boolean exists = false;
            try {
                if (db.getAlbums() != null) {
                    for (com.oculus.jj.model.DicomAlbum a : db.getAlbums()) {
                        if (a != null && name.equals(a.getName())) {
                            exists = true;
                            break;
                        }
                    }
                }
            } catch (Exception ex) {
                // ignore
            }
            if (!exists) {
                try {
                    db.addAlbum(new com.oculus.jj.model.DicomAlbum(name));
                    added++;
                } catch (Exception ex) {
                    // 単一アルバムの失敗はスキップ
                }
            }
        }
        if (added > 0) {
            db.save();
            refreshAlbums();
        }
        infoFmt(String.format(LanguageManager.get("db.default_albums.done", "Created {0} default album(s)."), added),
                LanguageManager.get("db.default_albums.title", "Create Default Albums"));
    }

    /**
     * SQLインデックスの再構築
     * HOROS-20240407準拠: BrowserController.m:2530 rebuildSQLFile:
     * 確認ダイアログ → DicomDatabase.save() でデータベースを保存（=SQLインデックス再構築）
     */
    public void rebuildSQLIndexFile() {
        DicomDatabase db = DicomDatabase.activeLocalDatabase();
        if (db == null) {
            infoFmt(LanguageManager.get("db.rebuild_sql.no_db", "No active database."),
                    LanguageManager.get("db.rebuild_sql.title", "Rebuild SQL Index File"));
            return;
        }
        int ans = JOptionPane.showConfirmDialog(this,
                LanguageManager.get("db.rebuild_sql.confirm",
                        "Are you sure you want to rebuild this database's SQL index? This operation can take several minutes."),
                LanguageManager.get("db.rebuild_sql.title", "Rebuild SQL Index File"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ans != JOptionPane.YES_OPTION)
            return;
        boolean ok = db.save();
        infoFmt(LanguageManager.get(ok ? "db.rebuild_sql.ok" : "db.rebuild_sql.fail",
                ok ? "SQL index rebuilt successfully." : "SQL index rebuild failed."),
                LanguageManager.get("db.rebuild_sql.title", "Rebuild SQL Index File"));
    }

    /**
     * ページセットアップ
     * HOROS-20240407準拠: MainMenu.xib 381行目 (Java Print Service API)
     * javax.print.PageFormat + PrinterJob.printDialog を使用する。
     */
    public void showPageSetup() {
        try {
            java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
            job.printDialog();
        } catch (Exception ex) {
            infoFmt(String.format(LanguageManager.get("db.page_setup.error", "Page setup error: {0}"), ex.getMessage()),
                    LanguageManager.get("db.page_setup.title", "Page Setup"));
        }
    }

    /**
     * サムネイルを再構築
     * HOROS-20240407準拠: - (IBAction)rebuildThumbnails:(id)sender (9985行目)
     * 全スライスのサムネイルを再生成する（DicomPix.thumbnailImage = null にリセット）。
     */
    public void rebuildThumbnails(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) {
            infoFmt(LanguageManager.get("db.thumbnails.no_view", "No active viewer."),
                    LanguageManager.get("db.thumbnails.title", "Rebuild Thumbnails"));
            return;
        }
        int count = 0;
        for (DicomPix pix : view.getDcmPixList()) {
            if (pix != null) {
                pix.invalidateThumbnail();
                count++;
            }
        }
        view.repaint();
        infoFmt(String.format(LanguageManager.get("db.thumbnails.done", "Rebuilt {0} thumbnail(s)."), count),
                LanguageManager.get("db.thumbnails.title", "Rebuild Thumbnails"));
    }

    /**
     * 選択スタディをクエリ
     * HOROS-20240407準拠: - (IBAction)querySelectedStudy: (id)sender (18120行目)
     */
    public void querySelectedStudy(Object sender) {
        Object selected = databaseOutline != null ? databaseOutline.getSelectedItem() : null;
        com.oculus.jj.model.DicomStudy study = null;
        if (selected instanceof com.oculus.jj.model.DicomStudy) {
            study = (com.oculus.jj.model.DicomStudy) selected;
        } else if (selected instanceof com.oculus.jj.model.DicomSeries) {
            study = ((com.oculus.jj.model.DicomSeries) selected).getStudy();
        }
        if (study == null) {
            infoFmt(LanguageManager.get("pacs.query.no_study", "Select a study or series first."),
                    LanguageManager.get("net_title", "Network"));
            return;
        }
        String patientID = study.getPatientID();
        if (patientID == null || patientID.trim().isEmpty()) {
            infoFmt(LanguageManager.get("pacs.query.no_patient_id", "The selected study has no Patient ID."),
                    LanguageManager.get("net_title", "Network"));
            return;
        }
        QueryController.getInstance().setSearchTextAndQuery(patientID.trim(), 1);
    }

    /**
     * PACS On-Demandスタディを取得
     * HOROS-20240407準拠: -(IBAction)retrieveSelectedPODStudies:(id) sender (9955行目)
     */
    public void retrieveSelectedPODStudies(Object sender) {
        QueryController query = QueryController.getInstance();
        if (query.retrieveSelectedResults(BrowserController.this)) return;

        List<com.oculus.jj.model.DicomStudy> remoteStudies = new ArrayList<>();
        if (databaseOutline != null) {
            for (Object item : databaseOutline.getSelectedItems()) {
                com.oculus.jj.model.DicomStudy study = null;
                if (item instanceof com.oculus.jj.model.DicomStudy) study = (com.oculus.jj.model.DicomStudy) item;
                else if (item instanceof com.oculus.jj.model.DicomSeries) study = ((com.oculus.jj.model.DicomSeries) item).getStudy();
                if (study != null && study.isDistant()) remoteStudies.add(study);
            }
        }
        if (remoteStudies.isEmpty()) {
            infoFmt(LanguageManager.get("pacs.retrieve.no_selection",
                    "Select results in Query/Retrieve or a stored remote study first."),
                    LanguageManager.get("net_title", "Network"));
            return;
        }

        List<com.oculus.jj.model.DicomNode> nodes = com.oculus.jj.model.DicomNodeManager.getInstance().getNodes();
        new javax.swing.SwingWorker<Integer, Void>() {
            private final List<String> failures = new ArrayList<>();

            @Override
            protected Integer doInBackground() {
                int retrieved = 0;
                for (com.oculus.jj.model.DicomStudy study : remoteStudies) {
                    com.oculus.jj.model.DicomNode node = null;
                    for (com.oculus.jj.model.DicomNode candidate : nodes) {
                        if (candidate.getName() != null && candidate.getName().equals(study.getSourceNodeName())) {
                            node = candidate;
                            break;
                        }
                    }
                    if (node == null) {
                        failures.add(study.getName() + ": " + LanguageManager.get("pacs.retrieve.node_missing", "source node not found"));
                        continue;
                    }
                    try {
                        com.oculus.jj.model.DicomService.getInstance().move(node, study.getStudyInstanceUID());
                        retrieved++;
                    } catch (Exception ex) {
                        failures.add(study.getName() + ": " + ex.getMessage());
                    }
                }
                return retrieved;
            }

            @Override
            protected void done() {
                try {
                    int retrieved = get();
                    if (databaseOutline != null) databaseOutline.reloadData();
                    refreshDatabase(null);
                    String message = String.format(LanguageManager.get("pacs.retrieve.done", "Retrieved %d study/studies."), retrieved);
                    if (!failures.isEmpty()) message += "\n" + String.join("\n", failures);
                    infoFmt(message, LanguageManager.get("net_title", "Network"));
                } catch (Exception ex) {
                    infoFmt(String.valueOf(ex.getMessage()), LanguageManager.get("pacs.retrieve.failed", "Retrieve failed"));
                }
            }
        }.execute();
    }

    /**
     * PACS On-Demand結果を更新
     * HOROS-20240407準拠: - (IBAction) refreshPACSOnDemandResults:(id) sender
     * (4721行目)
     */
    public void refreshPACSOnDemandResults(Object sender) {
        QueryController.getInstance().refreshOnDemandResults(BrowserController.this);
    }

    /**
     * ログウィンドウを表示
     * HOROS-20240407準拠: - (IBAction)showLogWindow: (id)sender (20372行目)
     */
    public void showLogWindow(Object sender) {
        LogWindow.showLogWindow(BrowserController.this);
    }

    /**
     * 現在アクティブなDCMViewを取得（編集系アクション用）
     */

    /**
     * DICOMをプリント (HOROS: printDICOM:).
     * HOROS の print:（ページ設定ダイアログ）と同様に、選択モードと From/To の
     * スライダーとテキストを双方向同期させ、選択スライスをページ単位で出力する。
     */
    public void printDICOM(Object sender) {
        showPrintDialog();
    }

    /**
     * HOROS print: に準拠したプリントページ設定ダイアログ。
     * - tag 0 = 現在スライスのみ: from=curImage, to=from+1
     * - tag 1 = 全スライス: from=0, to=count
     * - tag 2 = From-To 範囲（反転対応）: HOROS ViewerController.m:18029-18045
     * - スライダーとテキストの双方向同期: HOROS printSlider: (18469-18484) / setPagesToPrint:
     * 出力は Swing PrinterJob（プリンタまたはOSの「PDFとして保存」）でページ単位の画像をレンダリングする。
     */
    public void showPrintDialog() {
        DCMView targetView = getActiveDCMViewForEdit();
        if (targetView == null) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("browser.print.noActiveView", "No active image view for printing"),
                    "Print", JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.util.List<com.oculus.jj.model.DicomPix> pixList = targetView.getDcmPixList();
        int sliceCount = pixList.size();
        if (sliceCount == 0) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("browser.print.noImage", "No image to print"),
                    "Print", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int curImage = targetView.getCurImage();

        java.awt.Frame owner = (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this);
        javax.swing.JDialog dialog = new javax.swing.JDialog(owner,
                LanguageManager.get("browser.print.title", "Print"), true);

        // 選択モード（HOROS printSelection tag 0/1/2）
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        javax.swing.JRadioButton currentRB = new javax.swing.JRadioButton(
                LanguageManager.get("browser.print.current", "Current Image"));
        javax.swing.JRadioButton allRB = new javax.swing.JRadioButton(
                LanguageManager.get("browser.print.all", "All Slices"));
        javax.swing.JRadioButton rangeRB = new javax.swing.JRadioButton(
                LanguageManager.get("browser.print.range", "From - To Range"));
        currentRB.setSelected(true);
        group.add(currentRB); group.add(allRB); group.add(rangeRB);

        final int lo = 1, hi = sliceCount;
        // From/To スライダーと数値フィールド（双方向同期）
        final javax.swing.JSlider fromSlider = new javax.swing.JSlider(lo, hi, Math.max(lo, curImage + 1));
        final javax.swing.JSlider toSlider = new javax.swing.JSlider(lo, hi, hi);
        fromSlider.setPaintTicks(sliceCount <= 100);
        toSlider.setPaintTicks(sliceCount <= 100);
        final javax.swing.JTextField fromText = new javax.swing.JTextField(Integer.toString(fromSlider.getValue()), 5);
        final javax.swing.JTextField toText = new javax.swing.JTextField(Integer.toString(toSlider.getValue()), 5);
        final javax.swing.JSpinner intervalSpinner =
                new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(1, 1, 9999, 1));

        // スライダー -> テキスト
        final boolean[] syncing = { false };
        javax.swing.event.ChangeListener sliderToText = (javax.swing.event.ChangeEvent ev) -> {
            if (syncing[0]) return;
            syncing[0] = true;
            fromText.setText(Integer.toString(fromSlider.getValue()));
            toText.setText(Integer.toString(toSlider.getValue()));
            syncing[0] = false;
        };
        fromSlider.addChangeListener(sliderToText);
        toSlider.addChangeListener(sliderToText);
        // テキスト -> スライダー
        javax.swing.event.DocumentListener textToSlider = new javax.swing.event.DocumentListener() {
            private void apply(javax.swing.JTextField tf, javax.swing.JSlider sl) {
                if (syncing[0]) return;
                String s = tf.getText() == null ? "" : tf.getText().trim();
                if (s.isEmpty()) return;
                int v;
                try { v = Integer.parseInt(s); } catch (NumberFormatException ex) { return; }
                if (v < lo) v = lo;
                if (v > hi) v = hi;
                syncing[0] = true;
                sl.setValue(v);
                tf.setText(Integer.toString(v));
                syncing[0] = false;
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { apply(fromText, fromSlider); apply(toText, toSlider); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { apply(fromText, fromSlider); apply(toText, toSlider); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { apply(fromText, fromSlider); apply(toText, toSlider); }
        };
        fromText.getDocument().addDocumentListener(textToSlider);
        toText.getDocument().addDocumentListener(textToSlider);

        // 範囲パネル（From / To / Interval）
        javax.swing.JPanel rangePanel = new javax.swing.JPanel(new java.awt.GridLayout(0, 2, 8, 4));
        rangePanel.add(new javax.swing.JLabel(LanguageManager.get("browser.print.from", "From:")));
        javax.swing.JPanel fromRow = new javax.swing.JPanel(new java.awt.BorderLayout(4, 0));
        fromRow.add(fromSlider, java.awt.BorderLayout.CENTER);
        fromRow.add(fromText, java.awt.BorderLayout.EAST);
        rangePanel.add(fromRow);
        rangePanel.add(new javax.swing.JLabel(LanguageManager.get("browser.print.to", "To:")));
        javax.swing.JPanel toRow = new javax.swing.JPanel(new java.awt.BorderLayout(4, 0));
        toRow.add(toSlider, java.awt.BorderLayout.CENTER);
        toRow.add(toText, java.awt.BorderLayout.EAST);
        rangePanel.add(toRow);
        rangePanel.add(new javax.swing.JLabel(LanguageManager.get("browser.print.interval", "Interval:")));
        rangePanel.add(intervalSpinner);

        // モード切替で From/To/Interval を有効/無効
        java.awt.event.ActionListener modeListener = e -> {
            boolean range = rangeRB.isSelected();
            fromSlider.setEnabled(range); toSlider.setEnabled(range);
            fromText.setEnabled(range); toText.setEnabled(range);
            intervalSpinner.setEnabled(range);
        };
        currentRB.addActionListener(modeListener);
        allRB.addActionListener(modeListener);
        rangeRB.addActionListener(modeListener);
        modeListener.actionPerformed(null);

        javax.swing.JPanel radios = new javax.swing.JPanel();
        radios.add(currentRB); radios.add(allRB); radios.add(rangeRB);

        javax.swing.JPanel content = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(radios, java.awt.BorderLayout.NORTH);
        content.add(rangePanel, java.awt.BorderLayout.CENTER);
        content.setPreferredSize(new java.awt.Dimension(440, 190));

        javax.swing.JButton printBtn = new javax.swing.JButton(LanguageManager.get("browser.print.print", "Print..."));
        javax.swing.JButton cancelBtn = new javax.swing.JButton(LanguageManager.get("browser.print.cancel", "Cancel"));
        printBtn.addActionListener(e -> {
            // 選択スライスの0-basedインデックス列を計算（HOROS setPagesToPrint: 準拠）
            int from0, to0, interval = 1;
            if (currentRB.isSelected()) {
                from0 = Math.min(curImage, sliceCount - 1); to0 = from0 + 1;
            } else if (allRB.isSelected()) {
                from0 = 0; to0 = sliceCount;
            } else {
                int f, t, iv;
                try { f = Integer.parseInt(fromText.getText().trim()); } catch (Exception ex) { f = lo; }
                try { t = Integer.parseInt(toText.getText().trim()); } catch (Exception ex) { t = hi; }
                try { iv = Integer.parseInt(((javax.swing.SpinnerNumberModel) intervalSpinner.getValue()).toString()); } catch (Exception ex) { iv = 1; }
                if (f < lo) f = lo; if (f > hi) f = hi;
                if (t < lo) t = lo; if (t > hi) t = hi;
                if (iv < 1) iv = 1;
                interval = iv;
                if (f <= t) { from0 = f - 1; to0 = t; }
                else { from0 = t - 1; to0 = f; }
                if (to0 == from0) to0 = from0 + 1;
            }
            java.util.ArrayList<Integer> indices = new java.util.ArrayList<>();
            for (int i = from0; i < to0; i += interval) {
                if (i >= 0 && i < sliceCount) indices.add(i);
            }
            if (indices.isEmpty()) indices.add(Math.min(curImage, sliceCount - 1));

            // 各スライスを描画（DicomPix#getBufferedImage は必要に応じて読込）
            final java.awt.image.BufferedImage[] pages = new java.awt.image.BufferedImage[indices.size()];
            boolean okAll = true;
            for (int k = 0; k < indices.size(); k++) {
                try { pages[k] = pixList.get(indices.get(k)).getBufferedImage(); }
                catch (Exception ex) { pages[k] = null; okAll = false; }
            }
            int valid = 0;
            for (java.awt.image.BufferedImage b : pages) if (b != null) valid++;
            if (valid == 0) {
                JOptionPane.showMessageDialog(dialog,
                        LanguageManager.get("browser.print.renderFailed", "Failed to render the selected slices"),
                        "Print", JOptionPane.ERROR_MESSAGE);
                return;
            }

            java.awt.print.PrinterJob pj = java.awt.print.PrinterJob.getPrinterJob();
            pj.setJobName("OculusJJ");
            pj.setPrintable((java.awt.Graphics g, java.awt.print.PageFormat format, int pageIndex) -> {
                if (pageIndex < 0 || pageIndex >= pages.length) return java.awt.print.Printable.NO_SUCH_PAGE;
                java.awt.image.BufferedImage img = pages[pageIndex];
                if (img == null) return java.awt.print.Printable.NO_SUCH_PAGE;
                final double pageW = format.getImageableWidth();
                final double pageH = format.getImageableHeight();
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                double scale = Math.min(pageW / img.getWidth(),
                        pageH / img.getHeight());
                if (scale <= 0 || Double.isInfinite(scale)) scale = 1.0;
                int w = Math.max(1, (int) (img.getWidth() * scale));
                int h = Math.max(1, (int) (img.getHeight() * scale));
                int x = (int) (pageW - w) / 2;
                int y = (int) (pageH - h) / 2;
                g2.drawImage(img, x, y, w, h, null);
                return java.awt.print.Printable.PAGE_EXISTS;
            });
            boolean showDialog = pj.printDialog();
            if (showDialog) {
                try { pj.print(); }
                catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(dialog,
                            LanguageManager.get("browser.print.jobFailed", "Print job failed: ") + ex.getMessage(),
                            "Print", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());

        javax.swing.JPanel south = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 8));
        south.add(printBtn); south.add(cancelBtn);

        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout());
        root.add(content, java.awt.BorderLayout.CENTER);
        root.add(south, java.awt.BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(printBtn);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    /**
     * 選択中の画像/シリーズをエクスポート (HOROS: exportSelected:)
     */
    public void exportSelected() {
        exportToDICOMFile();
    }

    /**
     * PACS照会ウィンドウを表示 (HOROS: showPACSQuery:)
     */
    public void showPACSQuery() {
        QueryController.getInstance().showQueryDialog(this);
    }

    /**
     * 選択項目をPACSに送信 (HOROS: Send2PACS)
     */
    public void sendToPACS() {
        exportToPACS();
    }

    /**
     * 算術減算 (HOROS: SubtractionToolbarItemIdentifier / DCMView.m -subtract: 6321行目)
     * アクティブなビュー（基準/Reference）から、他のビューのピクセル値を差し引く。
     * absolute=true の場合 |a-b| を適用する。
     */
    public void showSubtractionDialog() {
        DCMView targetView = getActiveDCMViewForEdit();
        if (targetView == null) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("browser.subtraction.noActiveView",
                            "No active image view for subtraction"),
                    "Subtraction", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        if (viewers.size() < 2) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("browser.subtraction.needTwoSeries",
                            "Please select at least 2 series for subtraction"),
                    "Subtraction", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 候補リストを構築（ソース候補 = アクティブビュー以外のビュー）
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<DCMView> sources = new java.util.ArrayList<>();
        for (ViewerController vc : viewers) {
            DCMView v = vc.getImageView();
            if (v == null || v == targetView)
                continue;
            String lbl = "";
            DicomPix p = v.getCurDCM();
            if (p != null && p.getSeriesDescription() != null)
                lbl = p.getSeriesDescription();
            labels.add((lbl == null || lbl.isEmpty()) ? ("View " + (sources.size() + 1)) : lbl);
            sources.add(v);
        }
        if (sources.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("browser.subtraction.needTwoSeries",
                            "Please select at least 2 series for subtraction"),
                    "Subtraction", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final DCMView tView = targetView;
        final java.util.List<DCMView> fSources = sources;
        javax.swing.JComboBox<String> combo = new javax.swing.JComboBox<>(labels.toArray(new String[0]));
        javax.swing.JCheckBox absCheck = new javax.swing.JCheckBox(
                LanguageManager.get("browser.subtraction.absolute", "Absolute value"));
        absCheck.setSelected(false);
        // パネル構築: NORTH=referenceラベル, CENTER=sourceコンボ, SOUTH=absoluteチェック
        String refName = "";
        DicomPix tp = tView.getCurDCM();
        if (tp != null && tp.getSeriesDescription() != null)
            refName = tp.getSeriesDescription();
        javax.swing.JPanel centerPanel = new javax.swing.JPanel();
        centerPanel.setLayout(new javax.swing.BoxLayout(centerPanel,
                javax.swing.BoxLayout.LINE_AXIS));
        centerPanel.add(new javax.swing.JLabel(
                LanguageManager.get("browser.subtraction.source", "Source to subtract:") + "  "));
        centerPanel.add(combo);
        javax.swing.JPanel southPanel = new javax.swing.JPanel();
        southPanel.add(absCheck);
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));
        panel.add(new javax.swing.JLabel(
                String.format(LanguageManager.get("browser.subtraction.reference",
                        "Reference (target): %s"), refName == null ? "" : refName)),
                java.awt.BorderLayout.NORTH);
        panel.add(centerPanel, java.awt.BorderLayout.CENTER);
        panel.add(southPanel, java.awt.BorderLayout.SOUTH);
        panel.setPreferredSize(new java.awt.Dimension(420, 120));
        int result = JOptionPane.showConfirmDialog(this, panel,
                LanguageManager.get("browser.subtraction.title", "Arithmetic Subtraction"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            int idx = combo.getSelectedIndex();
            if (idx >= 0 && idx < fSources.size()) {
                boolean absolute = absCheck.isSelected();
                tView.subtract(fSources.get(idx), absolute);
            }
        }
    }

    // ========================================================================
    // HOROS-20240407 Subtraction (XA造影CT向け)
    // 参照: ViewerController.m:
    //   subCtrlOnOff: 9827  - トグル。ON時 各フレーム = フレーム - マスク
    //   subCtrlNewMask: 9875 - 現在フレームをマスクに設定し引算を有効化
    //   subCtrlOffset: 9910 - 8方向1pxシフトでマスクをずらして再引算
    //   subCtrlSliders: 10136 - Contrast/Brightness (WL/WW 調整)
    //   subSumSlider: 10173 - Sum 調整 (fusion stack 対応)
    //   subSharpen: 10211 - "Sharpen 5x5" カーネル適用
    // ========================================================================

    /**
     * Subtract トグル (HOROS: subCtrlOnOff: tag=0)
     * ON : 現在フレームをマスクから引算 → WL=128/WW=256 で表示
     * OFF: バックアップから元に戻す → WL/WW リセット
     */
    public void subCtrlOnOff(int tag) {
        DCMView view = activeDCMView();
        if (view == null || view.getCurDCM() == null) {
            info("roi.msg.no_rois", "No active image view.");
            return;
        }
        List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || pixList.isEmpty())
            return;

        boolean turningOn = !subCtrlOnOffItem.isSelected();
        subCtrlOnOffItem.setSelected(turningOn);

        if (turningOn) {
            if (subCtrlMaskIndex < 0) {
                // マスク未設定: 最初のスライスをマスクに自動設定
                subCtrlNewMask(null);
                return;
            }
            int cur = view.getCurImage();
            DicomPix maskPix = pixList.get(subCtrlMaskIndex);
            if (maskPix == null)
                return;
            for (DicomPix pix : pixList) {
                if (pix == null)
                    continue;
                pix.getOriginalShortData(); // バックアップ取得
                pix.setSubtractionEnabled(true);
                pix.subtractWithMask(maskPix, subCtrlOffsetX, subCtrlOffsetY);
            }
            // HOROS: [imageView setWLWW:128 :256]
            DicomPix curPix = pixList.get(Math.min(cur, pixList.size() - 1));
            if (curPix != null)
                curPix.setWindowLevelWidth(128, 256);
        } else {
            for (DicomPix pix : pixList) {
                if (pix == null)
                    continue;
                pix.setSubtractionEnabled(false);
                pix.restoreOriginalShortData();
            }
            // HOROS: [imageView setWLWW:0 :0] (自動再計算)
            int cur = view.getCurImage();
            DicomPix curPix = pixList.get(Math.min(cur, pixList.size() - 1));
            if (curPix != null)
                curPix.setWindowLevelWidth(0, 0);
        }
        view.repaint();
    }

    /**
     * New Mask (HOROS: subCtrlNewMask:)
     * 現在フレームをマスクとして設定し、引算を有効化する。
     */
    public void subCtrlNewMask(Object sender) {
        DCMView view = activeDCMView();
        if (view == null)
            return;
        List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || pixList.isEmpty())
            return;
        subCtrlMaskIndex = view.getCurImage(); // 0-based
        // 引算を有効化
        if (!subCtrlOnOffItem.isSelected()) {
            subCtrlOnOff(0);
        }
    }

    /**
     * 8方向1pxシフト (HOROS: subCtrlOffset:)
     * tag: 1=SW 2=S 3=SE 4=W 5=None 6=E 7=NE 8=N 9=NW
     */
    public void subCtrlOffset(int tag) {
        DCMView view = activeDCMView();
        if (view == null || !subCtrlOnOffItem.isSelected())
            return;
        switch (tag) {
            case 1: subCtrlOffsetX--; subCtrlOffsetY--; break; // SW
            case 2: subCtrlOffsetY--; break; // S
            case 3: subCtrlOffsetX++; subCtrlOffsetY--; break; // SE
            case 4: subCtrlOffsetX--; break; // W
            case 5: subCtrlOffsetX = 0; subCtrlOffsetY = 0; break; // None
            case 6: subCtrlOffsetX++; break; // E
            case 7: subCtrlOffsetX++; subCtrlOffsetY++; break; // NE
            case 8: subCtrlOffsetY++; break; // N
            case 9: subCtrlOffsetX--; subCtrlOffsetY++; break; // NW
        }
        // HOROS: 範囲 -30..30 にクランプ
        subCtrlOffsetX = Math.max(-30, Math.min(30, subCtrlOffsetX));
        subCtrlOffsetY = Math.max(-30, Math.min(30, subCtrlOffsetY));

        List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || pixList.isEmpty())
            return;
        if (subCtrlMaskIndex < 0)
            return;
        DicomPix maskPix = pixList.get(Math.min(subCtrlMaskIndex, pixList.size() - 1));
        if (maskPix == null)
            return;
        // バックアップから再引算（offset分を反映）
        for (DicomPix pix : pixList) {
            if (pix == null)
                continue;
            pix.restoreOriginalShortData();
            pix.subtractWithMask(maskPix, subCtrlOffsetX, subCtrlOffsetY);
        }
        view.repaint();
    }

    /**
     * Contrast / Brightness 調整 (HOROS: subCtrlSliders:)
     * tag: 37=Contrast Down 38=Contrast Default 39=Contrast Up
     *      34=Brightness Down 35=Brightness Default 36=Brightness Up
     * HOROS: Contrast=WL, Brightness=WW (Gamma/Zero スライダーは未対応)
     */
    public void subCtrlSliders(int tag) {
        DCMView view = activeDCMView();
        if (view == null || view.getCurDCM() == null || !subCtrlOnOffItem.isSelected())
            return;
        DicomPix pix = view.getCurDCM();
        float cwl = pix.getWindowLevel();
        float cww = pix.getWindowWidth();
        switch (tag) {
            case 37: cwl = cwl - 5; break;
            case 38: cwl = 128; break;
            case 39: cwl = cwl + 5; break;
            case 34: cww = cww - 5; break;
            case 35: cww = 256; break;
            case 36: cww = cww + 5; break;
        }
        pix.setWindowLevelWidth(cwl, cww);
        view.repaint();
    }

    /**
     * Sum 調整 (HOROS: subSumSlider:)
     * tag: 31=Sum Less 32=No Sum 33=Sum More
     * HOROS: fusion stack 用。ここでは1..10を保持し、将来setFusionに配線。
     */
    public void subSumSlider(int tag) {
        switch (tag) {
            case 31: subCtrlSum = Math.max(1, subCtrlSum - 1); break;
            case 32: subCtrlSum = 1; break;
            case 33: subCtrlSum = Math.min(10, subCtrlSum + 1); break;
        }
        // 現状: subCtrlSum は状態保持のみ。fusion stack 実装時に使用。
    }

    /**
     * Sharpen トグル (HOROS: subSharpen: tag=30)
     * ON : "Sharpen 5x5" カーネルを全スライスの引算結果に適用
     * OFF: バックアップから元に戻す
     */
    public void subSharpen(int tag) {
        DCMView view = activeDCMView();
        if (view == null)
            return;
        subCtrlSharpenOn = !subCtrlSharpenOn;
        if (subSharpenItem != null)
            subSharpenItem.setSelected(subCtrlSharpenOn);
        List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || pixList.isEmpty())
            return;
        if (subCtrlSharpenOn) {
            for (DicomPix pix : pixList) {
                if (pix != null)
                    pix.applySharpen5x5();
            }
        } else {
            // OFF: 引算OFF時のバックアップに復元（引算がONなら引算も再適用）
            for (DicomPix pix : pixList) {
                if (pix == null)
                    continue;
                pix.restoreOriginalShortData();
                if (subCtrlOnOffItem.isSelected() && subCtrlMaskIndex >= 0) {
                    DicomPix maskPix = pixList.get(Math.min(subCtrlMaskIndex, pixList.size() - 1));
                    if (maskPix != null)
                        pix.subtractWithMask(maskPix, subCtrlOffsetX, subCtrlOffsetY);
                }
            }
        }
        view.repaint();
    }

    // --- Resample (HOROS: ViewerController.m resampleDataFromPixArray, blending sheet tag 11) ---

    /**
     * リサンプリング (HOROS準拠: ViewerController.m 9560-9920)。
     *
     * <p>HOROSのdrag-blend操作でのデフォルトfactorは2.0。明示的なX/Y/Z factorを指定し、
     * NEW = OLD / factor、pixelSpacing ×= factor（空間解像度UP方向）。
     * 双線形補間で各スライスをリサイズし、新pixリストを生成して置換。
     * Z factorはスライス数に反映（等間隔サンプリング）。
     */
    void showResampleDialog() {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("resample.error.noView",
                            "No active 2D viewer with image data."));
            return;
        }

        java.awt.FlowLayout layout = new java.awt.FlowLayout(java.awt.FlowLayout.LEFT);
        javax.swing.JPanel panel = new javax.swing.JPanel(layout);

        javax.swing.JLabel label = new javax.swing.JLabel(
                LanguageManager.get("resample.help",
                        "Resample by factor (NEW = OLD / factor).\n"
                        + "Default 2.0: pixels halved, spacing doubled.\n"
                        + "Higher factor = coarser pixels, larger field-of-view per pixel."));
        panel.add(label);

        javax.swing.JTextField xFactor = new javax.swing.JTextField("2.0", 5);
        javax.swing.JTextField yFactor = new javax.swing.JTextField("2.0", 5);
        javax.swing.JTextField zFactor = new javax.swing.JTextField("1.0", 5);
        panel.add(new javax.swing.JLabel(
                LanguageManager.get("resample.factor", "X / Y / Z factor:")));
        panel.add(xFactor);
        panel.add(yFactor);
        panel.add(zFactor);

        int result = JOptionPane.showConfirmDialog(this, panel,
                LanguageManager.get("resample.title", "Resample"),
                JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        double fx, fy, fz;
        try {
            fx = Double.parseDouble(xFactor.getText().trim());
            fy = Double.parseDouble(yFactor.getText().trim());
            fz = Double.parseDouble(zFactor.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("resample.error.badFactor",
                            "Invalid factor (must be a positive number)."));
            return;
        }
        if (fx <= 0 || fy <= 0 || fz <= 0) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("resample.error.badFactor",
                            "Factor must be > 0."));
            return;
        }

        List<DicomPix> pixList = view.getDcmPixList();
        List<Object> files = view.getDcmFilesList();
        if (pixList == null || pixList.isEmpty() || files == null) return;

        int nOld = pixList.size();
        int nNew = Math.max(1, (int) Math.round(nOld / fz));

        List<DicomPix> newPixList = new java.util.ArrayList<>();
        List<Object> newFileList = new java.util.ArrayList<>();

        try {
            for (int nz = 0; nz < nNew; nz++) {
                int srcIdx;
                if (nNew == 1) srcIdx = 0;
                else srcIdx = Math.min(nOld - 1, (int) Math.round((double) nz * (nOld - 1) / (nNew - 1)));
                DicomPix src = pixList.get(srcIdx);
                short[] srcData = src.getShortData();
                if (srcData == null) continue;

                int sw = src.getWidth();
                int sh = src.getHeight();
                int nw = Math.max(1, (int) Math.round(sw / fx));
                int nh = Math.max(1, (int) Math.round(sh / fy));

                short[] newData = bilinearResize(srcData, sw, sh, nw, nh);

                DicomPix np = new DicomPix(src.getImageObj());
                np.setShortImageData(newData);
                np.setWidth(nw);
                np.setHeight(nh);
                np.setPixelSpacingX(src.getPixelSpacingX() * (float) fx);
                np.setPixelSpacingY(src.getPixelSpacingY() * (float) fy);
                double[] orient = src.getOrientation();
                if (orient != null && orient.length >= 9) np.setOrientation(orient);
                np.setOrigin(src.getOriginX(), src.getOriginY(), src.getOriginZ());
                newPixList.add(np);
                newFileList.add(files.get(srcIdx));
            }
        } catch (RuntimeException ex) {
            java.util.logging.Logger.getLogger(getClass().getName()).warning("showResampleDialog: " + ex);
            return;
        }

        if (newPixList.isEmpty()) return;

        char listType = view.listType;
        view.setPixels(newPixList, newFileList, view.getCurRoiList(),
                (short) 0, listType, true);
        view.repaint();
    }

    /**
     * 双線形補間で2D short[]画像をリサイズ。
     */
    static short[] bilinearResize(short[] src, int sw, int sh, int nw, int nh) {
        short[] out = new short[nw * nh];
        for (int ny = 0; ny < nh; ny++) {
            double sy = (ny + 0.5) * (double) sh / nh - 0.5;
            double y0d = Math.floor(sy);
            double fy2 = sy - y0d;
            int y0 = (int) y0d;
            int y1 = y0 + 1;
            for (int nx = 0; nx < nw; nx++) {
                double sx = (nx + 0.5) * (double) sw / nw - 0.5;
                double x0d = Math.floor(sx);
                double fx2 = sx - x0d;
                int x0 = (int) x0d;
                int x1 = x0 + 1;
                x0 = Math.max(0, Math.min(sw - 1, x0));
                x1 = Math.max(0, Math.min(sw - 1, x1));
                y0 = Math.max(0, Math.min(sh - 1, y0));
                y1 = Math.max(0, Math.min(sh - 1, y1));
                double v = (1 - fy2) * ((1 - fx2) * src[y0 * sw + x0] + fx2 * src[y0 * sw + x1])
                         + fy2 * ((1 - fx2) * src[y1 * sw + x0] + fx2 * src[y1 * sw + x1]);
                out[ny * nw + nx] = (short) Math.round(v);
            }
        }
        return out;
    }

    /**
     * Patient/Series/KeyImagesステータス表示 (HOROS: StatusToolbarItemIdentifier)
     * 現在の選択項目の詳細ステータスをモーダルで表示する。
     */
    public void showStatusDisplay() {
        java.util.List<Object> selected = getSelectedDatabaseObjects();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("browser.status.noSelection",
                            "No items selected for status display"),
                    "Status Display", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(LanguageManager.get("browser.status.selectedItems", "Selected Items:")).append("\n");
        for (int i = 0; i < Math.min(selected.size(), 10); i++) {
            sb.append("  ").append(i + 1).append(". ").append(selected.get(i)).append("\n");
        }
        if (selected.size() > 10) {
            sb.append("  ... ").append(selected.size() - 10).append(" more\n");
        }
        JOptionPane.showMessageDialog(this, sb.toString(),
                "Status Display", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 選択項目をXML形式でエクスポート
     */
    public void exportToXML() {
        JOptionPane.showMessageDialog(this,
                LanguageManager.get("browser.toolbar.xmlNotImpl", "XML export - not yet implemented"),
                "XML Export", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 選択項目をゴミ箱に移動 (HOROS: TrashToolbarItemIdentifier)
     */
    public void moveToTrash() {
        java.util.List<Object> selected = getSelectedDatabaseObjects();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No items selected", "Trash", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        int result = JOptionPane.showConfirmDialog(this,
                "Move " + selected.size() + " item(s) to trash?",
                "Move to Trash", javax.swing.JOptionPane.YES_NO_OPTION);
        if (result == javax.swing.JOptionPane.YES_OPTION) {
            for (Object obj : selected) {
                if (obj instanceof String path) {
                    File f = new File(path);
                    if (f.exists())
                        f.delete();
                }
            }
            outlineViewRefresh();
        }
    }

    private java.util.List<Object> getSelectedDatabaseObjects() {
        java.util.List<Object> result = new java.util.ArrayList<>();
        for (javax.swing.JButton cell : getMatrix().selectedCells()) {
            Object prop = cell.getClientProperty("path");
            if (prop instanceof String p)
                result.add(p);
        }
        return result;
    }

    DCMView getActiveDCMViewForEdit() {
        BrowserController bc = BrowserController.currentBrowser();
        if (bc != null) {
            javax.swing.JButton activeCell = bc.getMatrix().selectedCell();
            if (activeCell != null && activeCell.getClientProperty("viewer") instanceof ViewerController vc) {
                return vc.getImageView();
            }
        }
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        return (!viewers.isEmpty()) ? viewers.get(viewers.size() - 1).getImageView() : null;
    }

    /**
     * コピー
     * HOROS-20240407準拠: - (IBAction) copy: (id)sender (8809行目)
     */
    public void copy(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view != null) {
            java.awt.image.BufferedImage img = view.getBufferedImageNoLoad();
            if (img != null) {
                java.awt.datatransfer.Transferable transferable = new java.awt.datatransfer.Transferable() {
                    @Override
                    public DataFlavor[] getTransferDataFlavors() {
                        return new DataFlavor[] { DataFlavor.imageFlavor };
                    }

                    @Override
                    public Object getTransferData(DataFlavor df) throws java.io.IOException {
                        return img;
                    }

                    @Override
                    public boolean isDataFlavorSupported(DataFlavor df) {
                        return df == DataFlavor.imageFlavor;
                    }
                };
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
            }
        }
    }

    /**
     * ペースト
     * HOROS-20240407準拠: - (IBAction) paste: (id)sender (8802行目)
     */
    public void paste(Object sender) {
        java.awt.datatransfer.Transferable t = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .getContents(null);
        if (t != null && t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            try {
                java.awt.Image img = (java.awt.Image) t.getTransferData(DataFlavor.imageFlavor);
                JOptionPane.showMessageDialog(BrowserController.this, "Paste: image received from clipboard", "Paste",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(BrowserController.this, "Paste failed: " + ex.getMessage(), "Paste",
                        JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(BrowserController.this, "No image data on clipboard", "Paste",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    // ========== 2D Viewerメニューアクションメソッド ==========

    /**
     * シリーズを読み込み
     * HOROS-20240407準拠: - (IBAction) loadSerie: (id) sender
     */
    public void loadSerie(Object sender) {
    }

    /**
     * 患者を読み込み
     * HOROS-20240407準拠: - (IBAction) loadPatient: (id) sender
     */
    public void loadPatient(Object sender) {
    }

    /**
     * BW変換メニュー
     * HOROS-20240407準拠: - (IBAction) ConvertToBW:mode
     * @param sender mode: 0=RED, 1=GREEN, 2=BLUE, 3=RGB平均
     */
    public void ConvertToBWMenu(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        DicomPix pix = view.getCurDCM();
        if (pix == null) return;
        int mode = (sender instanceof Integer) ? (Integer) sender : 3;
        pix.convertToBW(mode);
        view.repaint();
    }

    /**
     * RGB変換メニュー
     * HOROS-20240407準拠: - (IBAction) ConvertToRGB:mode
     * @param sender mode: 0=RED, 1=GREEN, 2=BLUE, 3=RGB全チャンネル
     */
    public void ConvertToRGBMenu(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        DicomPix pix = view.getCurDCM();
        if (pix == null) return;
        int mode = (sender instanceof Integer) ? (Integer) sender : 3;
        pix.convertToRGB(mode);
        view.repaint();
    }

    /**
     * CLUTを適用
     * HOROS-20240407準拠: - (IBAction) applyCLUT (2D Viewer)
     * @param sender プリセット名（String）、null で解除
     */
    public void applyCLUT(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        DicomPix pix = view.getCurDCM();
        if (pix == null) return;
        String preset = (sender instanceof String) ? (String) sender : null;
        pix.applyCLUT(preset);
        view.repaint();
    }

    /**
     * Opacity（不透明度）を適用
     * HOROS-20240407準拠: - (IBAction) applyOpacity (2D Viewer)
     * @param sender 不透明度パーセント（Integer、25/50/75/100）
     */
    public void applyOpacity(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        DicomPix pix = view.getCurDCM();
        if (pix == null) return;
        int percent = (sender instanceof Integer) ? (Integer) sender : 100;
        pix.applyOpacity(percent / 100.0f);
        view.repaint();
    }

    // ============================================================
    // Convolution Filters
    // HOROS-20240407準拠: AppController.m (IBAction) ApplyConv: /
    //   AddConv: / deleteConv: / UpdateConvolutionMenu:
    //   ViewerController.m - (void) setConv:m:s:norm: / computeSum:
    //   通常クリック=適用, Shift+クリック=削除, Option+クリック=編集
    // ============================================================

    private static java.io.File convolutionFilterFile() {
        return new java.io.File(System.getProperty("user.home"),
                ".oculusjj/convolution_filters.json");
    }

    /** 保存済みの畳み込みフィルタを読み込む (name -> {size, normalization, matrix}) */
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, java.util.Map<String, Object>>
            loadConvolutionFilters() {
        final java.util.Map<String, java.util.Map<String, Object>> out =
                new java.util.LinkedHashMap<>();
        java.io.File f = convolutionFilterFile();
        if (!f.exists()) return out;
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(in);
            java.util.Iterator<java.util.Map.Entry<String,
                    com.fasterxml.jackson.databind.JsonNode>> it = root.fields();
            while (it.hasNext()) {
                java.util.Map.Entry<String,
                        com.fasterxml.jackson.databind.JsonNode> e = it.next();
                com.fasterxml.jackson.databind.JsonNode v = e.getValue();
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("size", v.path("size").asInt(5));
                m.put("normalization", v.path("normalization").asInt(0));
                java.util.List<Integer> matrix = new java.util.ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode x : v.path("matrix")) {
                    matrix.add(x.asInt(0));
                }
                m.put("matrix", matrix);
                out.put(e.getKey(), m);
            }
        } catch (Exception ex) {
            // 破損ファイルは空として扱う
        }
        return out;
    }

    /** 畳み込みフィルタを保存 (HOROS: NSUserDefaults "Convolution" 相当) */
    private static void saveConvolutionFilters(
            java.util.Map<String, java.util.Map<String, Object>> filters) {
        java.io.File f = convolutionFilterFile();
        try {
            if (f.getParentFile() != null && !f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode root =
                    mapper.createObjectNode();
            for (java.util.Map.Entry<String, java.util.Map<String, Object>> e
                    : filters.entrySet()) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> m = e.getValue();
                com.fasterxml.jackson.databind.node.ObjectNode n =
                        mapper.createObjectNode();
                n.put("size", (Integer) m.get("size"));
                n.put("normalization", (Integer) m.get("normalization"));
                com.fasterxml.jackson.databind.node.ArrayNode arr =
                        mapper.createArrayNode();
                for (Integer v : (java.util.List<Integer>) m.get("matrix")) {
                    arr.add(v);
                }
                n.set("matrix", arr);
                root.set(e.getKey(), n);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(f, root);
        } catch (Exception ex) {
            // 保存失敗はダイアログで通知する
            javax.swing.JOptionPane.showMessageDialog(null,
                    LanguageManager.get("convolution.error.save",
                            "Could not save convolution filters: ") + ex.getMessage(),
                    LanguageManager.get("convolution.error.title", "Error"),
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Convolution Filtersメニューを再構築
     * HOROS-20240407準拠: - (void) UpdateConvolutionMenu:
     * "No Filter" → 保存済みフィルタ(名順) → "Add a Filter..."
     */
    void rebuildConvolutionMenu() {
        if (convolutionMenu == null) return;
        convolutionMenu.removeAll();

        javax.swing.JMenuItem noFilter = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("convolution.none"));
        noFilter.addActionListener(e -> applyConvolution(null));
        convolutionMenu.add(noFilter);

        java.util.Map<String, java.util.Map<String, Object>> filters =
                loadConvolutionFilters();
        java.util.List<String> names = new java.util.ArrayList<>(filters.keySet());
        java.util.Collections.sort(names);
        for (String name : names) {
            final String n = name;
            javax.swing.JMenuItem it = new javax.swing.JMenuItem(name);
            it.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    int mods = e.getModifiersEx();
                    if ((mods & java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0) {
                        deleteConvolutionFilter(n);
                    } else if ((mods
                            & (java.awt.event.InputEvent.ALT_DOWN_MASK
                               | java.awt.event.InputEvent.META_DOWN_MASK)) != 0) {
                        showConvolutionDialog(n);
                    } else {
                        applyConvolution(n);
                    }
                }
            });
            convolutionMenu.add(it);
        }

        if (!names.isEmpty()) {
            convolutionMenu.addSeparator();
        }
        javax.swing.JMenuItem add = new javax.swing.JMenuItem(
                MenuResourceLoader.getString("convolution.add"));
        add.addActionListener(e -> showConvolutionDialog());
        convolutionMenu.add(add);
        tagChildMenuItems(convolutionMenu, "applyConvolution");
    }

    /**
     * 畳み込みフィルタをアクティブビューに適用
     * HOROS-20240407準拠: - (void) setConv:m:s:norm:
     * （全フレームに適用してからリフレッシュ）
     */
    public void applyConvolution(String name) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        List<DicomPix> list = view.getDcmPixList();
        if (list == null || list.isEmpty()) return;
        if (name == null) {
            for (DicomPix pix : list) {
                pix.setConvolutionKernel(null, 0, 0f);
            }
        } else {
            java.util.Map<String, java.util.Map<String, Object>> filters =
                    loadConvolutionFilters();
            java.util.Map<String, Object> f = filters.get(name);
            if (f == null) return;
            int size = (Integer) f.get("size");
            int norm = (Integer) f.get("normalization");
            @SuppressWarnings("unchecked")
            java.util.List<Integer> matrix =
                    (java.util.List<Integer>) f.get("matrix");
            float[] kernel = new float[size * size];
            for (int i = 0; i < kernel.length && i < matrix.size(); i++) {
                kernel[i] = matrix.get(i);
            }
            for (DicomPix pix : list) {
                pix.setConvolutionKernel(kernel, size, norm);
            }
        }
        view.repaint();
    }

    /** 畳み込みフィルタを削除 (Shift+クリック) */
    private void deleteConvolutionFilter(String name) {
        int confirm = javax.swing.JOptionPane.showConfirmDialog(this,
                LanguageManager.get("convolution.delete.message",
                        "Delete convolution filter") + " \"" + name + "\"?",
                LanguageManager.get("convolution.delete.title",
                        "Delete Convolution Filter"),
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE);
        if (confirm != javax.swing.JOptionPane.OK_OPTION) return;
        java.util.Map<String, java.util.Map<String, Object>> filters =
                loadConvolutionFilters();
        filters.remove(name);
        saveConvolutionFilters(filters);
        rebuildConvolutionMenu();
    }

    /** 新規フィルタ編集ダイアログ (HOROS: AddConv:) */
    public void showConvolutionDialog() {
        showConvolutionDialog(null);
    }

    /**
     * 畳み込みフィルタ編集ダイアログ
     * HOROS-20240407準拠: AppController.m (IBAction) AddConv: /
     *   ViewerController.m - (void) changeMatrixSize: / computeSum:
     * 5x5グリッド; 3x3選択時外周を無効化; 正規化は合計を自動計算
     */
    public void showConvolutionDialog(final String existingName) {
        final String[] nameHolder = { existingName };
        final JTextField nameField = new JTextField(
                existingName != null ? existingName : "", 20);
        final JTextField[][] cells = new JTextField[5][5];
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                cells[y][x] = new JTextField("0", 4);
            }
        }

        // サイズ選択 (HOROS: changeMatrixSize: tag 3/5)
        final JRadioButton size5 = new JRadioButton("5x5");
        final JRadioButton size3 = new JRadioButton("3x3");
        final ButtonGroup sizeGroup = new ButtonGroup();
        sizeGroup.add(size5);
        sizeGroup.add(size3);
        size5.setSelected(true);

        @SuppressWarnings("unused")
        final Runnable refreshSize = () -> {
            boolean three = size3.isSelected();
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    boolean inner = (x >= 1 && x <= 3 && y >= 1 && y <= 3);
                    cells[y][x].setEnabled(!three || inner);
                    if (three && !inner) {
                        cells[y][x].setText("");
                    }
                }
            }
        };
        size5.addActionListener(e -> refreshSize.run());
        size3.addActionListener(e -> refreshSize.run());

        final JTextField normField = new JTextField("0", 8);
        JButton sumButton = new JButton(
                LanguageManager.get("convolution.sum", "Sum"));
        sumButton.addActionListener(e -> {
            int sum = 0;
            boolean three = size3.isSelected();
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    if (three && (x < 1 || x > 3 || y < 1 || y > 3)) continue;
                    try {
                        sum += Integer.parseInt(cells[y][x].getText().trim());
                    } catch (NumberFormatException ex) {
                        // 無効なセルは0扱い
                    }
                }
            }
            normField.setText(String.valueOf(sum));
        });

        // グリッドパネル (HOROS: 5x5 convMatrix)
        javax.swing.JPanel gridPanel = new javax.swing.JPanel(
                new java.awt.GridLayout(5, 1, 2, 2));
        for (int y = 4; y >= 0; y--) {
            javax.swing.JPanel row = new javax.swing.JPanel(
                    new java.awt.GridLayout(1, 5, 2, 2));
            for (int x = 0; x < 5; x++) {
                row.add(cells[y][x]);
            }
            gridPanel.add(row);
        }

        javax.swing.JPanel mainPanel = new javax.swing.JPanel(new javax.swing.BoxLayout(
                new javax.swing.JPanel(), javax.swing.BoxLayout.Y_AXIS));
        mainPanel.setBorder(
                javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));

        javax.swing.JPanel nameRow = new javax.swing.JPanel();
        nameRow.add(new javax.swing.JLabel(
                LanguageManager.get("convolution.name", "Name:")));
        nameRow.add(nameField);

        javax.swing.JPanel sizeRow = new javax.swing.JPanel();
        sizeRow.add(new javax.swing.JLabel(
                LanguageManager.get("convolution.size", "Size:")));
        sizeRow.add(size5);
        sizeRow.add(size3);

        javax.swing.JPanel normRow = new javax.swing.JPanel();
        normRow.add(new javax.swing.JLabel(
                LanguageManager.get("convolution.normalization", "Normalization:")));
        normRow.add(normField);
        normRow.add(sumButton);

        mainPanel.add(nameRow);
        mainPanel.add(sizeRow);
        mainPanel.add(new javax.swing.JSeparator());
        mainPanel.add(gridPanel);
        mainPanel.add(new javax.swing.JSeparator());
        mainPanel.add(normRow);

        final javax.swing.JDialog dialog = new javax.swing.JDialog(this,
                LanguageManager.get("convolution.title", "Convolution Filter"),
                true);
        dialog.setLayout(new java.awt.BorderLayout(0, 6));
        dialog.add(mainPanel, java.awt.BorderLayout.CENTER);

        javax.swing.JButton saveButton = new JButton(
                LanguageManager.get("convolution.save", "Save"));
        javax.swing.JButton cancelButton = new JButton(
                LanguageManager.get("convolution.cancel", "Cancel"));
        javax.swing.JPanel buttonPanel = new javax.swing.JPanel();
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        dialog.add(buttonPanel, java.awt.BorderLayout.SOUTH);
        saveButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                javax.swing.JOptionPane.showMessageDialog(this,
                        LanguageManager.get("convolution.error.name",
                                "Please enter a filter name."),
                        LanguageManager.get("convolution.error.title", "Error"),
                        javax.swing.JOptionPane.ERROR_MESSAGE);
                return;
            }
            int size = size3.isSelected() ? 3 : 5;
            int count = size * size;
            java.util.List<Integer> matrix = new java.util.ArrayList<>();
            for (int y = 0; y < 5 && matrix.size() < count; y++) {
                for (int x = 0; x < 5 && matrix.size() < count; x++) {
                    if (size == 3 && (x < 1 || x > 3 || y < 1 || y > 3)) continue;
                    try {
                        matrix.add(Integer.parseInt(cells[y][x].getText().trim()));
                    } catch (NumberFormatException ex) {
                        javax.swing.JOptionPane.showMessageDialog(this,
                                LanguageManager.get("convolution.error.value",
                                        "Matrix values must be integers."),
                                LanguageManager.get("convolution.error.title", "Error"),
                                javax.swing.JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
            }
            int norm = 0;
            try {
                norm = Integer.parseInt(normField.getText().trim());
            } catch (NumberFormatException ex) {
                norm = 0;
            }

            java.util.Map<String, java.util.Map<String, Object>> filters =
                    loadConvolutionFilters();
            // 編集でない場合は同名を削除 (HOROS: 上書き保存)
            if (existingName == null) {
                filters.remove(name);
            }
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("size", size);
            m.put("normalization", norm);
            m.put("matrix", matrix);
            filters.put(name, m);
            saveConvolutionFilters(filters);
            rebuildConvolutionMenu();
            // 適用 (ApplyConv: 相当 — 新規作成後に即適用)
            applyConvolution(name);
            dialog.dispose();
        });
        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);

        // 既存フィルタの内容を反映 (Option+クリックで編集)
        if (existingName != null) {
            java.util.Map<String, java.util.Map<String, Object>> filters =
                    loadConvolutionFilters();
            java.util.Map<String, Object> f = filters.get(existingName);
            if (f != null) {
                int size = (Integer) f.get("size");
                normField.setText(String.valueOf((Integer) f.get("normalization")));
                if (size == 3) {
                    size3.setSelected(true);
                }
                refreshSize.run();
                @SuppressWarnings("unchecked")
                java.util.List<Integer> matrix =
                        (java.util.List<Integer>) f.get("matrix");
                int idx = 0;
                for (int y = 0; y < 5 && idx < matrix.size(); y++) {
                    for (int x = 0; x < 5 && idx < matrix.size(); x++) {
                        if (size == 3 && (x < 1 || x > 3 || y < 1 || y > 3)) continue;
                        cells[y][x].setText(String.valueOf(matrix.get(idx++)));
                    }
                }
            }
        }

        dialog.setVisible(true);
    }

    /**
     * ウィンドウプロトコルを適用
     * HOROS-20240407準拠: - (IBAction) applyWindowProtocol: (id) sender
     * モダリティに応じた既定のWL/WWを適用する
     */
    public void applyWindowProtocol(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        List<DicomPix> list = view.getDcmPixList();
        if (list == null || list.isEmpty()) return;
        for (DicomPix pix : list) {
            String modality = (pix.getModality() != null) ? pix.getModality().toUpperCase() : "";
            float wl = 0, ww = 0;
            boolean applied = false;
            if (modality.equals("CT")) {
                wl = 40; ww = 400; applied = true; // 骨窓
            } else if (modality.equals("MR")) {
                pix.computeBestFit();
                applied = true;
            } else if (modality.equals("US") || modality.equals("DR")
                    || modality.equals("MG") || modality.equals("CR")
                    || modality.equals("DX") || modality.equals("XA")) {
                pix.computeBestFit();
                applied = true;
            }
            if (!applied) {
                pix.computeBestFit();
            }
        }
        view.repaint();
        JOptionPane.showMessageDialog(this,
                LanguageManager.get("window.protocol.applied", "Window protocol applied"),
                "Window Protocol", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * ワークスペース状態を保存
     * HOROS-20240407準拠: - (IBAction) saveWindowState
     * 開いている2Dビューアの位置・サイズ・インデックス・スケール・WL/WWをJSONで保存
     */
    public void saveWorkspaceState(Object sender) {
        List<ViewerController> viewers = ViewerController.getDisplayed2DViewers();
        if (viewers == null || viewers.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("workspace.no.viewers", "No 2D viewers to save"),
                    "Workspace State", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ArrayNode root = mapper.createArrayNode();
            for (ViewerController vc : viewers) {
                com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
                java.awt.Rectangle b = vc.getBounds();
                node.put("x", b.x);
                node.put("y", b.y);
                node.put("w", b.width);
                node.put("h", b.height);
                DCMView view = vc.getImageView();
                if (view != null) {
                    node.put("index", view.getCurImage());
                    node.put("scale", view.getScaleValue());
                    DicomPix pix = view.getCurDCM();
                    if (pix != null) {
                        node.put("wl", pix.getWindowLevel());
                        node.put("ww", pix.getWindowWidth());
                        node.put("seriesUid", pix.getSeriesInstanceUID());
                    }
                }
                root.add(node);
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON", "json"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            java.io.File out = chooser.getSelectedFile();
            if (!out.getName().toLowerCase().endsWith(".json")) out = new java.io.File(out.getParent(), out.getName() + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(out, root);
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("workspace.saved", "Workspace state saved: ") + out.getAbsolutePath(),
                    "Workspace State", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("workspace.save.failed", "Save failed: ") + ex.getMessage(),
                    "Workspace State", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * ワークスペース状態を読み込み
     * HOROS-20240407準拠: - (IBAction) loadWindowState
     */
    public void loadWorkspaceState(Object sender) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON", "json"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            java.io.File in = chooser.getSelectedFile();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(in);
            if (!root.isArray()) {
                JOptionPane.showMessageDialog(this,
                        LanguageManager.get("workspace.invalid.file", "Invalid workspace file"),
                        "Workspace State", JOptionPane.WARNING_MESSAGE);
                return;
            }
            List<ViewerController> viewers = ViewerController.getDisplayed2DViewers();
            int n = Math.min(root.size(), viewers.size());
            for (int i = 0; i < n; i++) {
                com.fasterxml.jackson.databind.JsonNode node = root.get(i);
                ViewerController vc = viewers.get(i);
                vc.setBounds(node.path("x").asInt(0), node.path("y").asInt(100),
                        node.path("w").asInt(800), node.path("h").asInt(600));
                DCMView view = vc.getImageView();
                if (view != null) {
                    List<DicomPix> list = view.getDcmPixList();
                    int idx = node.path("index").asInt(0);
                    if (list != null && idx >= 0 && idx < list.size()) {
                        view.setIndex(idx);
                    }
                    float scale = (float) node.path("scale").asDouble(1.0);
                    if (scale > 0) view.setScaleValue(scale);
                    DicomPix pix = view.getCurDCM();
                    if (pix != null && node.has("wl") && node.has("ww")) {
                        pix.setWindowLevelWidth((float) node.path("wl").asDouble(0.0),
                                (float) node.path("ww").asDouble(256.0));
                    }
                    view.repaint();
                }
            }
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("workspace.loaded", "Workspace state loaded"),
                    "Workspace State", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("workspace.load.failed", "Load failed: ") + ex.getMessage(),
                    "Workspace State", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * ワークスペース状態をリセット
     * HOROS-20240407準拠: - (IBAction) resetWindowState
     */
    public void resetWorkspaceState(Object sender) {
        List<ViewerController> viewers = ViewerController.getDisplayed2DViewers();
        if (viewers == null || viewers.isEmpty()) return;
        for (ViewerController vc : viewers) {
            DCMView view = vc.getImageView();
            if (view == null) continue;
            view.setScaleValue(1.0f);
            DicomPix pix = view.getCurDCM();
            if (pix != null) {
                pix.resetWLWW();
            }
            view.repaint();
        }
        JOptionPane.showMessageDialog(this,
                LanguageManager.get("workspace.reset", "Workspace state reset"),
                "Workspace State", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 画像をリセット
     * HOROS-20240407準拠: - (IBAction) resetImage: (id) sender
     */
    public void resetImage(Object sender) {
        // HOROS-20240407準拠: - (IBAction) resetImage: (ViewerController.m:10231)
        // 各viewの origin=0 / scaleToFit / rotation=0 / savedWL&WW を再適用
        DCMView view = getActiveDCMViewForEdit();
        if (view == null || view.getCurDCM() == null) return;
        view.setRotation(0);
        view.scaleToFit();
        view.getCurDCM().setWindowLevelWidth(view.getCurDCM().getSavedWL(), view.getCurDCM().getSavedWW());
        view.repaint();
    }

    /**
     * シリーズを元に戻す
     * HOROS-20240407準拠: - (IBAction) revertSeries: (ViewerController.m:21950)
     * executeRevert相当: pixをリセットし、表示状態(origin/scale/rotation)とsavedWL/WWを復元
     */
    public void revertSeries(Object sender) {
        // HOROS-20240407準拠: postprocessed(再スライス/MPR)データはrevert不可
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        java.util.List<DicomPix> pixList = view.getDcmPixList();
        if (pixList != null) {
            for (DicomPix pix : pixList) {
                // HOROS: [pix revert] — WL/WW等をデフォルト(保存値)にリセット
                pix.resetWLWW();
            }
        }
        // HOROS: [imageView updatePresentationStateFromSeries]
        // origin=0 / scaleToFit / rotation=0 / savedWL&WW を再適用
        view.setRotation(0);
        view.scaleToFit();
        DicomPix cur = view.getCurDCM();
        if (cur != null) {
            cur.setWindowLevelWidth(cur.getSavedWL(), cur.getSavedWW());
        }
        view.repaint();
    }

    /**
     * 実際のサイズ
     * HOROS-20240407準拠: - (IBAction) actualSize: (id) sender
     */
    public void actualSize(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        view.setScaleValue(1.0f);
        view.repaint();
    }

    /**
     * 実サイズ
     * HOROS-20240407準拠: - (IBAction) realSize: (id) sender
     * pixelSpacing で物理mm→画面px換算
     */
    public void realSize(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        DicomPix pix = view.getCurDCM();
        if (pix == null) return;
        double px = pix.getPixelSpacingX();
        double py = pix.getPixelSpacingY();
        if (px <= 0 || py <= 0) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("realsize.not.calibrated", "Image is not pixel-calibrated (no PixelSpacing)."),
                    LanguageManager.get("menu_scale_actual_size", "Real Size"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        // 画面の物理密度 (px/mm) を 1.0/0.0264 で換算 (96dpi ≒ 3.7795 px/mm)
        float screenPxPerMm = 3.7795f;
        float scale = (float) (px * screenPxPerMm);
        view.setScaleValue(scale);
        view.repaint();
    }

    /**
     * フィットにスケール
     * HOROS-20240407準拠: - (IBAction) scaleToFit: (id) sender
     */
    public void scaleToFit(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        DicomPix pix = view.getCurDCM();
        if (pix == null) return;
        float w = view.getWidth();
        float h = view.getHeight();
        float pw = pix.getWidth();
        float ph = pix.getHeight();
        double ratio = pix.getPixelRatio();
        if (pw <= 0 || ph <= 0) return;
        float sx = w / pw;
        float sy = (float) (h / ph / ratio);
        view.setScaleValue(Math.min(sx, sy));
        view.repaint();
    }

    /**
     * シリーズをソート
     * HOROS-20240407準拠: - (IBAction) sortSeriesByValue: (id) sender
     */
    public void sortSeriesByValue(Object sender) {
    }

    /**
     * 水平反転
     * HOROS-20240407準拠: - (IBAction) flipHorizontal: (id) sender
     */
    public void flipHorizontal(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        view.toggleXFlipped();
        view.repaint();
    }

    /**
     * 垂直反転
     * HOROS-20240407準拠: - (IBAction) flipVertical: (id) sender
     */
    public void flipVertical(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        view.toggleYFlipped();
        view.repaint();
    }

    /**
     * 0度回転
     * HOROS-20240407準拠: - (IBAction) rotate0: (id) sender
     */
    public void rotate0(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        view.setRotation(0);
        view.repaint();
    }

    /**
     * 90度回転
     * HOROS-20240407準拠: - (IBAction) rotate90: (id) sender
     */
    public void rotate90(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        view.setRotation(1);
        view.repaint();
    }

    /**
     * 180度回転
     * HOROS-20240407準拠: - (IBAction) rotate180: (id) sender
     */
    public void rotate180(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        view.setRotation(2);
        view.repaint();
    }

    /**
     * キャリブレート
     * HOROS-20240407準拠: - (IBAction) calibrate: (ViewerController.m:9343)
     * 警告ダイアログ → 間隔/方位/原点を入力するシート (SetThicknessInterval) を表示。
     * 入力値は pixList の全ピクセルへ反映され、表示が再レンダリングされる。
     */
    public void calibrate(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null || view.getCurDCM() == null) return;

        int choice = JOptionPane.showConfirmDialog(this,
                LanguageManager.get("calibrate.warning",
                        "Modifying these parameters will:\n\n"
                        + "- Change the measurement results (length, surface, volume, ...)\n"
                        + "- Change the orientation of the slices and of the 3D objects (Left, Right, ...)\n"
                        + "- Change the aspect of the 3D images. It can introduce distortions.\n\n"
                        + "ONLY change these parameters if you know WHAT and WHY you are doing it."),
                LanguageManager.get("calibrate.title", "Warning !"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return; // "I agree" vs "Cancel"

        java.util.List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || pixList.isEmpty()) return;
        DicomPix p0 = pixList.get(0);

        showCalibrateDialog(view, pixList, p0);
    }

    /**
     * ホロス-20240407準拠: SetThicknessInterval (ViewerController.m:11068)
     * sliceInterval / pixelSpacing / orientation / origin を編集可能に表示し、
     * OK 押下時は全ピクセルへ反映して再レンダリングする。
     */
    private void showCalibrateDialog(final DCMView view, final java.util.List<DicomPix> pixList, final DicomPix p0) {
        final javax.swing.JDialog dlg = new javax.swing.JDialog(this,
                LanguageManager.get("calibrate.window.title", "Calibrate"), true);

        javax.swing.JTextField intervalField = new javax.swing.JTextField(
                String.valueOf(p0.getSliceThickness()), 12);
        javax.swing.JTextField xSpacingField = new javax.swing.JTextField(
                String.valueOf(p0.getPixelSpacingX()), 12);
        javax.swing.JTextField ySpacingField = new javax.swing.JTextField(
                String.valueOf(p0.getPixelSpacingY()), 12);
        javax.swing.JTextField originX = new javax.swing.JTextField(
                String.valueOf(p0.getOriginX()), 10);
        javax.swing.JTextField originY = new javax.swing.JTextField(
                String.valueOf(p0.getOriginY()), 10);
        javax.swing.JTextField originZ = new javax.swing.JTextField(
                String.valueOf(p0.getOriginZ()), 10);

        javax.swing.JPanel form = new javax.swing.JPanel();
        form.setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.insets = new java.awt.Insets(5, 8, 5, 8);
        c.anchor = java.awt.GridBagConstraints.WEST;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0;
        form.add(new javax.swing.JLabel(LanguageManager.get("calibrate.sliceThickness", "Slice Thickness (mm):")), c);
        c.gridx = 1; c.gridy = 0;
        form.add(intervalField, c);

        c.gridx = 0; c.gridy = 1;
        form.add(new javax.swing.JLabel(LanguageManager.get("calibrate.xSpacing", "X Spacing (mm):")), c);
        c.gridx = 1; c.gridy = 1;
        form.add(xSpacingField, c);

        c.gridx = 0; c.gridy = 2;
        form.add(new javax.swing.JLabel(LanguageManager.get("calibrate.ySpacing", "Y Spacing (mm):")), c);
        c.gridx = 1; c.gridy = 2;
        form.add(ySpacingField, c);

        c.gridx = 0; c.gridy = 3;
        form.add(new javax.swing.JLabel(LanguageManager.get("calibrate.origin", "Origin (X, Y, Z mm):")), c);
        javax.swing.JPanel originRow = new javax.swing.JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints oc = new java.awt.GridBagConstraints();
        oc.insets = new java.awt.Insets(0, 2, 0, 2);
        originRow.add(originX, oc); oc.gridx = 1;
        originRow.add(originY, oc); oc.gridx = 2;
        originRow.add(originZ, oc);
        c.gridx = 1; c.gridy = 3;
        form.add(originRow, c);

        final javax.swing.JLabel statusLabel = new javax.swing.JLabel(" ");
        statusLabel.setForeground(java.awt.Color.BLUE);
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2;
        form.add(statusLabel, c);

        final java.util.concurrent.atomic.AtomicBoolean applied = new java.util.concurrent.atomic.AtomicBoolean(false);
        javax.swing.JButton okBtn = new javax.swing.JButton(LanguageManager.get("common.ok", "OK"));
        okBtn.addActionListener(e -> {
            try {
                double interval = Double.parseDouble(intervalField.getText().trim());
                double xs = Double.parseDouble(xSpacingField.getText().trim());
                double ys = Double.parseDouble(ySpacingField.getText().trim());
                double ox = Double.parseDouble(originX.getText().trim());
                double oy = Double.parseDouble(originY.getText().trim());
                double oz = Double.parseDouble(originZ.getText().trim());
                if (interval <= 0) throw new NumberFormatException("interval");
                if (xs <= 0) throw new NumberFormatException("xs");
                if (ys <= 0) throw new NumberFormatException("ys");
                // HOROS: 全ピクセルへスライスマップを再計算して適用
                for (DicomPix p : pixList) {
                    p.setSliceThickness(interval);
                    p.setPixelSpacingX(xs);
                    p.setPixelSpacingY(ys);
                    p.setOrigin(ox, oy, oz);
                }
                DicomPix cur = view.getCurDCM();
                if (cur != null) {
                    cur.setWindowLevelWidth(cur.getSavedWL(), cur.getSavedWW());
                }
                view.repaint();
                applied.set(true);
                dlg.dispose();
            } catch (NumberFormatException ex) {
                statusLabel.setText(LanguageManager.get("calibrate.invalid_number", "Please enter valid positive numbers."));
            }
        });
        javax.swing.JButton cancelBtn = new javax.swing.JButton(LanguageManager.get("common.cancel", "Cancel"));
        cancelBtn.addActionListener(e -> dlg.dispose());

        javax.swing.JPanel buttons = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        buttons.add(okBtn);
        buttons.add(cancelBtn);

        dlg.getContentPane().setLayout(new java.awt.BorderLayout());
        dlg.getContentPane().add(form, java.awt.BorderLayout.CENTER);
        dlg.getContentPane().add(buttons, java.awt.BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    /** 3D Position ウィンドウ（シングルトン相当） */
    private static ThreeDPositionWindow threeDPositionWindow = null;

    /**
     * 3Dパネル（3D Position 表示）
     * HOROS-20240407準拠: - (IBAction) threeDPanel: (id) sender
     * (ViewerController.m:22812) ThreeDPositionController（シングルトン）を表示する。
     */
    public void threeDPanel(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("position3d.no_viewer", "No active viewer."),
                    LanguageManager.get("position3d.title", "3D Position"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (threeDPositionWindow == null || threeDPositionWindow.isDisposed()) {
            threeDPositionWindow = new ThreeDPositionWindow();
        }
        threeDPositionWindow.showForView(view);
    }

    /** ナビゲータウィンドウ（シングルトン相当） */
    private static NavigatorWindow navigatorWindow = null;

    /**
     * ナビゲータ
     * HOROS-20240407準拠: - (IBAction) navigator: (id) sender
     * (ViewerController.m:22787) RGBデータと非3Dシリーズをガードし、
     * NavigatorWindowController（シングルトン）でサムネイルストリップを表示する。
     */
    public void navigator(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("navigator.no_viewer", "No active viewer to navigate."),
                    LanguageManager.get("navigator.title", "Navigator"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        java.util.List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || pixList.size() < 2) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("navigator.not3d_error",
                            "This tool works only with 3D data series with identical matrix sizes."),
                    LanguageManager.get("navigator.title", "Navigator"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        // HOROS: if (curDCM isRGB) alert "This tool currently does not work with RGB data series."
        if (view.getCurDCM() != null && view.getCurDCM().isRGB()) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("navigator.rgb_error",
                            "This tool currently does not work with RGB data series."),
                    LanguageManager.get("navigator.title", "Navigator"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        // HOROS: 3D判定 - 同一マトリクスサイズの3Dデータシリーズのみ
        int w0 = pixList.get(0).getWidth(), h0 = pixList.get(0).getHeight();
        for (DicomPix p : pixList) {
            if (p.getWidth() != w0 || p.getHeight() != h0) {
                JOptionPane.showMessageDialog(this,
                        LanguageManager.get("navigator.not3d_error",
                                "This tool works only with 3D data series with identical matrix sizes."),
                        LanguageManager.get("navigator.title", "Navigator"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        if (navigatorWindow == null || navigatorWindow.isDisposed()) {
            navigatorWindow = new NavigatorWindow();
        }
        navigatorWindow.showForView(view);
    }

    /**
     * データシリーズを反転
     * HOROS-20240407準拠: - (IBAction) flipDataSeries: (id) sender
     * フラグを反転し、現在インデックスを count-1-curImage に再マッピングする
     */
    private static final java.util.Set<com.oculus.jj.ui.DCMView> flippedDataViews =
            java.util.Collections.synchronizedSet(new java.util.HashSet<com.oculus.jj.ui.DCMView>());

    public void flipDataSeries(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null || view.getDcmPixList() == null || view.getDcmPixList().size() < 2) {
            return;
        }
        boolean nowFlipped = !flippedDataViews.contains(view);
        if (nowFlipped) {
            flippedDataViews.add(view);
        } else {
            flippedDataViews.remove(view);
        }
        int count = view.getDcmPixList().size();
        int cur = view.getCurImage();
        int newIndex = count - 1 - cur;
        view.setIndex(newIndex);
        view.repaint();
    }

    /**
     * VOI LUTを使用
     * HOROS-20240407準拠: - (IBAction) useVOILUT: (ViewerController.m:16444)
     * "UseVOILUT"プリファレンスをトグルし、applyLUT相当(savedWL/WWを再適用)を実行
     */
    private javax.swing.JCheckBoxMenuItem useVOILUTItem = null;
    public void useVOILUT(Object sender) {
        // HOROS: [[NSUserDefaults standardUserDefaults] setBool:!boolForKey:@"UseVOILUT" forKey:@"UseVOILUT"]
        boolean nowUse = !com.oculus.jj.config.AppPreferences.isUseVOILUT();
        com.oculus.jj.config.AppPreferences.setUseVOILUT(nowUse);
        // チェックボックス状態を同期
        if (useVOILUTItem != null) {
            useVOILUTItem.setSelected(nowUse);
        }
        // HOROS: applyLUT: — savedWL/savedWW を再適用して再レンダリング
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        DicomPix pix = view.getCurDCM();
        if (pix != null) {
            pix.setWindowLevelWidth(pix.getSavedWL(), pix.getSavedWW());
        }
        view.repaint();
    }

    /**
     * SUVを表示
     * HOROS-20240407準拠: - (IBAction) displaySUV: (ViewerController.m:16312)
     * curDCM が hasSUV なら SUV 変換ウィンドウ (patientsWeight /
     * radionuclideTotalDose / halflife) を表示、できない場合は
     * "Cannot compute SUV on these data." を警告する。
     */
    public void displaySUV(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("suv.no_viewer", "No active viewer to compute SUV."),
                    LanguageManager.get("menu_viewer_2d_suv", "Convert from/to SUV"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DicomPix pix = view.getCurDCM();
        if (pix == null) return;
        // HOROS: if ([[imageView curDCM] hasSUV] == NO)
        //         NSRunAlertPanel(@"SUV Error", @"Cannot compute SUV on these data.", ...)
        if (!pix.hasSUV()) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("suv.error", "Cannot compute SUV on these data."),
                    LanguageManager.get("suv.error.title", "SUV Error"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        // HOROS: else beginSheet: displaySUVWindow — 3パラメータを表示/編集
        showSUVDialog(view, pix);
    }

    /**
     * SUV 変換ダイアログ
     * HOROS-20240407準拠: DisplaySUVWindowController (displaySUVWindow.nib)
     * patientsWeight / radionuclideTotalDose / radionuclideHalfLife を
     * 編集可能に表示し、変更を AppPreferences に保存して再レンダリングする。
     */
    private void showSUVDialog(final DCMView view, final DicomPix pix) {
        final javax.swing.JDialog dlg = new javax.swing.JDialog(
                this,
                LanguageManager.get("suv.window.title", "SUV Conversion"), true);

        final javax.swing.JTextField weightField = new javax.swing.JTextField(
            String.valueOf(pix.getPatientsWeight() > 0 ? pix.getPatientsWeight() :
                com.oculus.jj.config.AppPreferences.getSUVPatientsWeight()), 12);
        final javax.swing.JTextField doseField = new javax.swing.JTextField(
            String.valueOf(pix.getRadionuclideTotalDose() > 0 ? pix.getRadionuclideTotalDose() / 1_000_000.0f :
                com.oculus.jj.config.AppPreferences.getSUVRadionuclideTotalDose()), 12);
        final javax.swing.JTextField halflifeField = new javax.swing.JTextField(
            String.valueOf(pix.getHalfLife() > 0 ? pix.getHalfLife() / 60.0f :
                com.oculus.jj.config.AppPreferences.getSUVRadionuclideHalfLife()), 12);

        javax.swing.JPanel form = new javax.swing.JPanel();
        form.setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.insets = new java.awt.Insets(6, 8, 6, 8);
        c.anchor = java.awt.GridBagConstraints.WEST;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0;
        form.add(new javax.swing.JLabel(LanguageManager.get("suv.patientsWeight", "Patients Weight (kg):")), c);
        c.gridx = 1; c.gridy = 0;
        form.add(weightField, c);

        c.gridx = 0; c.gridy = 1;
        form.add(new javax.swing.JLabel(LanguageManager.get("suv.totalDose", "Radionuclide Total Dose (MBq):")), c);
        c.gridx = 1; c.gridy = 1;
        form.add(doseField, c);

        c.gridx = 0; c.gridy = 2;
        form.add(new javax.swing.JLabel(LanguageManager.get("suv.halflife", "Radionuclide Half-Life (min):")), c);
        c.gridx = 1; c.gridy = 2;
        form.add(halflifeField, c);

        final javax.swing.JLabel statusLabel = new javax.swing.JLabel(" ");
        statusLabel.setForeground(java.awt.Color.BLUE);
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        form.add(statusLabel, c);

        final java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        final java.util.List<float[]> values = new java.util.ArrayList<>();
        javax.swing.JButton okBtn = new javax.swing.JButton(LanguageManager.get("common.ok", "OK"));
        okBtn.addActionListener(e -> {
            try {
                float w = Float.parseFloat(weightField.getText().trim());
                float d = Float.parseFloat(doseField.getText().trim());
                float h = Float.parseFloat(halflifeField.getText().trim());
                if (w <= 0) throw new NumberFormatException("weight");
                if (d <= 0) throw new NumberFormatException("dose");
                if (h <= 0) throw new NumberFormatException("halflife");
                values.add(new float[]{w, d, h});
                dlg.dispose();
            } catch (NumberFormatException ex) {
                errors.add(LanguageManager.get("suv.invalid_number", "Please enter valid positive numbers."));
                statusLabel.setText(errors.get(errors.size() - 1));
            }
        });
        javax.swing.JButton cancelBtn = new javax.swing.JButton(LanguageManager.get("common.cancel", "Cancel"));
        cancelBtn.addActionListener(e -> dlg.dispose());

        javax.swing.JPanel buttons = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        buttons.add(okBtn);
        buttons.add(cancelBtn);

        dlg.getContentPane().setLayout(new java.awt.BorderLayout());
        dlg.getContentPane().add(form, java.awt.BorderLayout.CENTER);
        dlg.getContentPane().add(buttons, java.awt.BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);

        if (!values.isEmpty()) {
            float[] v = values.get(0);
            // HOROS: SUV 変換には these 3 values を用いる (patientsWeight /
            // radionuclideTotalDose / radionuclideHalfLife)。ここで保存値を反映して再レンダリング。
            com.oculus.jj.config.AppPreferences.setSUVPatientsWeight(v[0]);
            com.oculus.jj.config.AppPreferences.setSUVRadionuclideTotalDose(v[1]);
            com.oculus.jj.config.AppPreferences.setSUVRadionuclideHalfLife(v[2]);
            DicomPix cur = view.getCurDCM();
            if (cur != null) {
                cur.setSUVConversionParameters(v[0], v[1] * 1_000_000.0f, v[2] * 60.0f);
                boolean converted = cur.setSUVConverted(!cur.isSUVConverted());
                if (!converted) {
                    JOptionPane.showMessageDialog(this,
                            LanguageManager.get("suv.error.time", "SUV conversion requires BQML units and valid acquisition/injection times."),
                            LanguageManager.get("suv.error.title", "SUV Error"), JOptionPane.ERROR_MESSAGE);
                }
            }
            view.repaint();
        }
    }

    /**
     * ウィンドウをブレンド (Fuse/De-Fuse PET/SPECT - CT)
     * HOROS-20240407準拠: - (IBAction) blendWindows: (id) sender
     *   - 表示中の2Dビューアから同一Study・平行平面の CT と PET/SPECT のペアを探す
     *   - 見つかれば FusionImageProcessor を通して融合オーバーレイを CT 側に設定
     *   - もう一度実行すると融合を解除 (toggle)
     */
    public void blendWindows(Object sender) {
        List<ViewerController> viewers = ViewerController.getDisplayed2DViewers();
        if (viewers.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("fuse.no_viewer", "No active 2D viewer to fuse."),
                    LanguageManager.get("fuse.error.title", "Fuse Error"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 現在どこかが融合状態か確認 (toggle)
        boolean anyFusing = false;
        for (ViewerController vc : viewers) {
            DCMView view = vc.getImageView();
            if (view == null) continue;
            List<DicomPix> list = view.getDcmPixList();
            if (list == null) continue;
            for (DicomPix p : list) {
                if (p != null && p.hasFusion()) { anyFusing = true; break; }
            }
            if (anyFusing) break;
        }

        com.oculus.jj.model.FusionImageProcessor.FusionMode mode =
                com.oculus.jj.config.AppPreferences.getFusionMode();
        double weight = com.oculus.jj.config.AppPreferences.getFusionWeight();

        // De-Fuse: 既存融合を全ビューアからクリア
        if (anyFusing) {
            for (ViewerController vc : viewers) {
                DCMView view = vc.getImageView();
                if (view == null) continue;
                List<DicomPix> list = view.getDcmPixList();
                if (list == null) continue;
                for (DicomPix p : list) {
                    if (p != null && p.hasFusion()) {
                        p.clearFusion();
                        view.repaint();
                    }
                }
            }
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("fuse.disabled", "PET/SPECT + CT fusion disabled."),
                    LanguageManager.get("fuse.error.title", "Fuse Error"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Fuse: CT 側と PET/SPECT 側のペアを探す
        boolean found = false;
        int matchedCT = 0;
        for (ViewerController vc : viewers) {
            DCMView view = vc.getImageView();
            if (view == null) continue;
            List<DicomPix> list = view.getDcmPixList();
            if (list == null || list.isEmpty()) continue;

            DicomPix activeCT = null;
            for (DicomPix p : list) {
                if (p != null && isCTModality(p.getModality())) { activeCT = p; break; }
            }
            if (activeCT == null) continue;

            // 同一 Study の PET/SPECT を別のビューアから探す
            DicomPix petPix = findPetOrSpectFor(activeCT, viewers);
            if (petPix == null) continue;

            activeCT.setFusionOverlay(petPix, weight, mode);
            found = true;
            matchedCT++;
            view.repaint();
        }

        if (found) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("fuse.enabled", "PET/SPECT + CT fusion enabled."),
                    LanguageManager.get("fuse.error.title", "Fuse Error"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } else if (sender != null) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("fuse.no_pair",
                            "No compatible CT + PET/SPECT pair found in the displayed viewers (same study, parallel planes)."),
                    LanguageManager.get("fuse.error.title", "Fuse Error"),
                    javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * CT と融合可能な PET/SPECT (PT/NM) 画像を探す
     * HOROS-20240407準拠: blendWindows: 中の PET/SPECT シリーズ特定
     */
    private DicomPix findPetOrSpectFor(DicomPix ctPix, List<ViewerController> viewers) {
        String ctStudy = ctPix.getStudyInstanceUID();
        if (ctStudy == null) ctStudy = ctPix.getFrameOfReferenceUID();
        if (ctStudy == null) return null;

        int ctSlice = ctPix.getSliceIndex();
        DicomPix best = null;
        int bestDist = Integer.MAX_VALUE;

        for (ViewerController vc : viewers) {
            DCMView view = vc.getImageView();
            if (view == null) continue;
            List<DicomPix> list = view.getDcmPixList();
            if (list == null) continue;
            for (DicomPix p : list) {
                if (p == null || p == ctPix) continue;
                if (!isPetOrSpectModality(p.getModality())) continue;
                // 同一Study (FrameOfReferenceUID で代用可)
                String study = p.getStudyInstanceUID();
                if (study == null) study = p.getFrameOfReferenceUID();
                if (study == null) continue;
                if (!study.equalsIgnoreCase(ctStudy)) continue;
                // 平行平面 (sliceIndex 近接 — 許容 0.5スライス)
                int dist = Math.abs(p.getSliceIndex() - ctSlice);
                if (dist > bestDist) continue;
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }

    private static boolean isCTModality(String m) {
        if (m == null) return false;
        String u = m.trim().toUpperCase();
        return u.equals("CT") || u.equals("XA") || u.equals("XA7");
    }

    private static boolean isPetOrSpectModality(String m) {
        if (m == null) return false;
        String u = m.trim().toUpperCase();
        return u.equals("PT") || u.equals("NM") || u.equals("PET");
    }

    /**
     * 融合画像をマージ (Flatten Fused Image)
     * HOROS-20240407準拠: - (IBAction) mergeFusedImages: (id) sender
     *   - 現在のCT側の融合オーバーレイ結果をバッファに焼き込み、融合状態をクリア
     */
    public void mergeFusedImages(Object sender) {
        DCMView active = getActiveDCMViewForEdit();
        if (active == null || active.getCurDCM() == null) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("flatten.no_fusion", "No fused image to flatten."),
                    LanguageManager.get("flatten.error.title", "Flatten Error"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<DicomPix> list = active.getDcmPixList();
        if (list == null || list.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("flatten.no_fusion", "No fused image to flatten."),
                    LanguageManager.get("flatten.error.title", "Flatten Error"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean merged = false;
        for (DicomPix p : list) {
            if (p != null && p.hasFusion()) {
                java.awt.image.BufferedImage fused = p.getFusedOverlay();
                if (fused != null) {
                    java.awt.image.BufferedImage copy = new java.awt.image.BufferedImage(
                            fused.getWidth(), fused.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                    copy.getGraphics().drawImage(fused, 0, 0, null);
                    p.setBufferedImage(copy);
                }
                p.clearFusion();
                merged = true;
            }
        }

        active.repaint();
        if (merged) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("flatten.applied", "Fused image flattened."),
                    LanguageManager.get("flatten.error.title", "Flatten Error"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } else if (sender != null) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("flatten.no_fusion", "No fused image to flatten."),
                    LanguageManager.get("flatten.error.title", "Flatten Error"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * 設定を他にコピー (Propagate Settings)
     * HOROS-20240407準拠: - (IBAction) copySettingsToOthers: → propagateSettings → propagateSettingsToViewer:
     *   - アクティブビューアの WL/WW・スケール・回転 を他2Dビューアに伝播
     *   - 同一Study かつ同一Modality でのみ WL/WW を伝播 (HOROS propagateSettingsToViewer: 準拠)
     *   - 平行平面 (Orientationベクトルがほぼ同一) でのみ スケール・回転 を伝播
     */
    public void copySettingsToOthers(Object sender) {
        if (!DCMView.propagateSettings) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("propagate.disabled",
                            "Propagation is disabled. Enable \"Propagate Settings Between Series\" in the 2D menu."),
                    LanguageManager.get("propagate.title", "Propagate Settings"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<ViewerController> viewers = ViewerController.getDisplayed2DViewers();
        if (viewers.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("propagate.no_viewer", "No active 2D viewer to propagate from."),
                    LanguageManager.get("propagate.title", "Propagate Settings"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // アクティブ(最後にフォーカスしたKeyView)のビューアをソースとする
        ViewerController source = null;
        for (ViewerController vc : viewers) {
            if (vc.getImageView() != null && vc.getImageView().isKeyView) {
                source = vc;
                break;
            }
        }
        if (source == null) {
            source = viewers.get(0);
        }

        DCMView srcView = source.getImageView();
        if (srcView == null || srcView.getCurDCM() == null) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("propagate.no_viewer", "No active 2D viewer to propagate from."),
                    LanguageManager.get("propagate.title", "Propagate Settings"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        DicomPix srcPix = srcView.getCurDCM();
        float srcWL = srcPix.getWindowLevel();
        float srcWW = srcPix.getWindowWidth();
        float srcScale = srcView.getScaleValue();
        int srcRotation = srcView.getRotation();
        boolean srcXFlipped = srcView.getXFlipped();
        boolean srcYFlipped = srcView.getYFlipped();
        String srcModality = srcPix.getModality();
        String srcStudy = srcPix.getStudyInstanceUID();
        if (srcStudy == null) srcStudy = srcPix.getFrameOfReferenceUID();
        double[] srcNormal = orientationNormal(srcPix);

        int propagated = 0;
        for (ViewerController vc : viewers) {
            if (vc == source) continue;
            DCMView tgtView = vc.getImageView();
            if (tgtView == null || tgtView.getCurDCM() == null) continue;
            DicomPix tgtPix = tgtView.getCurDCM();

            // WL/WW: 同一Studyかつ同一Modalityでのみ伝播 (HOROS準拠)
            boolean propagateWLWW = true;
            if (srcModality != null && tgtPix.getModality() != null
                    && !srcModality.equalsIgnoreCase(tgtPix.getModality())) {
                propagateWLWW = false;
            }
            String tgtStudy = tgtPix.getStudyInstanceUID();
            if (tgtStudy == null) tgtStudy = tgtPix.getFrameOfReferenceUID();
            if (srcStudy != null && tgtStudy != null && !srcStudy.equalsIgnoreCase(tgtStudy)) {
                propagateWLWW = false;
            }
            if (propagateWLWW
                    && (tgtPix.getWindowLevel() != srcWL || tgtPix.getWindowWidth() != srcWW)) {
                tgtPix.setWindowLevelWidth(srcWL, srcWW);
            }

            // スケール・回転・flip: 平行平面でのみ伝播
            double[] tgtNormal = orientationNormal(tgtPix);
            if (normalsParallel(srcNormal, tgtNormal)) {
                double srcSpacing = srcPix.getPixelSpacingX();
                double tgtSpacing = tgtPix.getPixelSpacingX();
                float newScale;
                if (srcSpacing > 0 && tgtSpacing > 0 && srcScale > 0) {
                    newScale = (float) ((srcScale / srcSpacing) * tgtSpacing);
                } else {
                    newScale = srcScale;
                }
                if (Math.abs(tgtView.getScaleValue() - newScale) > 1e-4f)
                    tgtView.setScaleValue(newScale);
                if (tgtView.getRotation() != srcRotation)
                    tgtView.setRotation(srcRotation);
                if (tgtView.getXFlipped() != srcXFlipped)
                    tgtView.setXFlipped(srcXFlipped);
                if (tgtView.getYFlipped() != srcYFlipped)
                    tgtView.setYFlipped(srcYFlipped);
            }

            tgtView.repaint();
            propagated++;
        }

        if (propagated > 0) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("propagate.applied",
                            "Settings propagated to %d viewer(s).").replace("%d", String.valueOf(propagated)),
                    LanguageManager.get("propagate.title", "Propagate Settings"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } else if (sender != null) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    LanguageManager.get("propagate.none", "No other viewer to propagate to."),
                    LanguageManager.get("propagate.title", "Propagate Settings"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Orientationベクトルから法線成分 (ベクトル[6..8]) を取得 */
    private static double[] orientationNormal(DicomPix pix) {
        double[] o = pix.getOrientation();
        if (o != null && o.length >= 9) {
            return new double[] { o[6], o[7], o[8] };
        }
        return new double[] { 0, 0, 1 };
    }

    /** 2つの法線ベクトルが平行 (または反平行) かどうか */
    private static boolean normalsParallel(double[] a, double[] b) {
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        return Math.abs(Math.abs(dot) - 1.0) < 0.001;
    }

    /**
     * シリーズ内で設定を切り替え (Propagate Settings in Current Series)
     * HOROS-20240407準拠: - (IBAction) switchCopySettingsInSeries: → DCMView.m switchCopySettingsInSeries:
     *   - COPYSETTINGSINSERIES をトグル
     *   - 有効時: スライス切替後もWL/WW・スケール・回転・flipがビューレベルで継続適用される
     */
    public void switchCopySettingsInSeries(Object sender) {
        DCMView.copySettingsInSeries = !DCMView.copySettingsInSeries;

        // 表示中の2Dビューアを再描画
        for (ViewerController vc : ViewerController.getDisplayed2DViewers()) {
            DCMView v = vc.getImageView();
            if (v != null) v.repaint();
        }

        javax.swing.JOptionPane.showMessageDialog(this,
                DCMView.copySettingsInSeries
                        ? LanguageManager.get("propagate.in_series.enabled",
                                "Propagate Settings in Current Series: ON (WL/WW & view settings persist across slices).")
                        : LanguageManager.get("propagate.in_series.disabled",
                                "Propagate Settings in Current Series: OFF."),
                LanguageManager.get("propagate.title", "Propagate Settings"),
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 同期
     * HOROS-20240407準拠: - (IBAction) syncronize: (id) sender
     */
    public void syncronize(Object sender) {
        // 同期モードを切り替える
        if (DCMView.syncro == DCMView.syncroOFF) {
            DCMView.syncro = DCMView.syncroLOC;
        } else {
            DCMView.syncro = DCMView.syncroOFF;
        }

        // 全てのビューを再描画
        NotificationCenter.defaultCenter().postNotification(NotificationCenter.OsirixUpdateViewNotification, null);
    }

    /**
     * 常に同期メニュー
     * HOROS-20240407準拠: - (IBAction) alwaysSyncMenu: (id) sender
     */
    public void alwaysSyncMenu(Object sender) {
        // SYNCSERIESを切り替える (Horosの仕様に合わせて調整が必要)
        DCMView.SYNCSERIES = !DCMView.SYNCSERIES;

    }

    /**
     * シリーズを同期
     * HOROS-20240407準拠: - (IBAction) SyncSeries: (id) sender
     */
    public void SyncSeries(Object sender) {
        DCMView.SYNCSERIES = !DCMView.SYNCSERIES;

    }

    /**
     * キー画像を設定
     * HOROS-20240407準拠: - (IBAction) setKeyImage: (id) sender
     */
    public void setKeyImage(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        DicomPix pix = view.getCurDCM();
        if (pix == null) return;
        boolean newVal = !pix.getKeyImage();
        pix.setKeyImage(newVal);
        view.repaint();
        JOptionPane.showMessageDialog(this,
                LanguageManager.get("key.image.marked", newVal ? "Key image set" : "Key image unset"),
                "Key Image", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 全ての画像をキー画像に設定
     * HOROS-20240407準拠: - (IBAction) setAllKeyImages: (id) sender
     */
    public void setAllKeyImages(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        List<DicomPix> list = view.getDcmPixList();
        if (list == null) return;
        for (DicomPix pix : list) {
            pix.setKeyImage(true);
        }
        view.repaint();
        JOptionPane.showMessageDialog(this,
                LanguageManager.get("key.image.all.marked", "All images marked as key"),
                "Key Images", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 全ての画像を非キー画像に設定
     * HOROS-20240407準拠: - (IBAction) setAllNonKeyImages: (id) sender
     */
    public void setAllNonKeyImages(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        List<DicomPix> list = view.getDcmPixList();
        if (list == null) return;
        for (DicomPix pix : list) {
            pix.setKeyImage(false);
        }
        view.repaint();
        JOptionPane.showMessageDialog(this,
                LanguageManager.get("key.image.all.unmarked", "All key images unmarked"),
                "Key Images", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * ROI画像をキー画像に設定
     * HOROS-20240407準拠: - (IBAction) setROIsImagesKeyImages: (id) sender
     */
    public void setROIsImagesKeyImages(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        List<DicomPix> list = view.getDcmPixList();
        if (list == null) return;
        int count = 0;
        for (DicomPix pix : list) {
            if (pix.getROIs() != null && !pix.getROIs().isEmpty()) {
                pix.setKeyImage(true);
                count++;
            }
        }
        view.repaint();
        JOptionPane.showMessageDialog(this,
                LanguageManager.get("key.image.roi.marked", count + " ROI images marked as key"),
                "Key Images", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * キャプチャしてキー画像に設定
     * HOROS-20240407準拠: - (IBAction) captureAndSetKeyImage: (id) sender
     */
    public void captureAndSetKeyImage(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null || view.curDCM == null) {
            JOptionPane.showMessageDialog(BrowserController.this,
                    LanguageManager.get("key.image.no.viewer", "No active viewer to capture key image"),
                    LanguageManager.get("key.image.title", "Key Image"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        DicomPix pix = view.curDCM;
        try {
            java.awt.image.BufferedImage img = renderViewToBufferedImage(view, pix);
            if (img == null) {
                JOptionPane.showMessageDialog(BrowserController.this,
                        LanguageManager.get("export.no.image", "No image data available"),
                        LanguageManager.get("key.image.title", "Key Image"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            java.io.File dir = new java.io.File(new java.io.File(System.getProperty("user.dir"), "OculusJJ Data"),
                    "KeyImages");
            if (!dir.exists())
                dir.mkdirs();
            String base = (pix.getSeriesDescription() != null && !pix.getSeriesDescription().isEmpty())
                    ? pix.getSeriesDescription().replaceAll("[^A-Za-z0-9._-]", "_")
                    : "KeyImage";
            String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            int frame = pix.getFrameNumber();
            java.io.File outFile = new java.io.File(dir, String.format("%s_%s_%02d.dcm", base, ts, frame));
            if (writeSCImage(outFile, img, pix, 1,
                    LanguageManager.get("export.sc.series.key", "Key Image"))) {
                pix.setKeyImage(true);
                view.repaint();
                JOptionPane.showMessageDialog(BrowserController.this,
                        LanguageManager.get("key.image.saved", "Key image saved:") + "\n" + outFile.getAbsolutePath(),
                        LanguageManager.get("key.image.title", "Key Image"), JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(BrowserController.this,
                    LanguageManager.get("key.image.capture.failed", "Key image capture failed:") + " " + ex.getMessage(),
                    LanguageManager.get("key.image.title", "Key Image"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * キー画像をすべて DICOM SC (Secondary Capture) シリーズとしてエクスポート
     * HOROS-20240407準拠: BrowserController.m:17815 exportROIAndKeyImagesAsDICOMSeries:
     * (各キー画像を DicomImage.m:1266 imageAsDICOMScreenCapture: 相当の描画キャプチャで
     *  8bit RGB の SC オブジェクトとして出力、SeriesDescription="ROIs and Key Images")
     */
    public void exportKeyImagesAsDICOM(Object sender) {
        java.util.Map<DicomPix, DCMView> targets = new java.util.LinkedHashMap<>();
        for (ViewerController vc : ViewerController.getDisplayed2DViewers()) {
            DCMView view = vc.getImageView();
            if (view == null || view.dcmPixList == null)
                continue;
            for (DicomPix pix : view.dcmPixList) {
                if (pix.getKeyImage())
                    targets.put(pix, view);
            }
        }
        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("key.image.export.none",
                            "No key images marked. Mark images as Key Image first (K)."),
                    LanguageManager.get("export.sc.title", "Export Key Images as DICOM SC"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle(LanguageManager.get("export.sc.title", "Export Key Images as DICOM SC"));
        fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        fc.setApproveButtonText("Export");
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File dir = fc.getSelectedFile();
        if (!dir.exists())
            dir.mkdirs();
        int saved = 0;
        java.util.List<DCMView> usedViews = new java.util.ArrayList<>();
        for (java.util.Map.Entry<DicomPix, DCMView> entry : targets.entrySet()) {
            DicomPix pix = entry.getKey();
            DCMView view = entry.getValue();
            int idx = view.dcmPixList.indexOf(pix);
            int prev = view.getCurImage();
            if (!usedViews.contains(view))
                usedViews.add(view);
            try {
                if (idx >= 0 && idx != prev)
                    view.setIndex(idx);
                java.awt.image.BufferedImage img = renderViewToBufferedImage(view, pix);
                if (img == null)
                    continue;
                saved++;
                java.io.File outFile = new java.io.File(dir, String.format("KeyImage_%02d.dcm", saved));
                writeSCImage(outFile, img, pix, saved,
                        LanguageManager.get("export.sc.series.description", "ROIs and Key Images"));
            } catch (Exception ex) {
                System.err.println("[KeyImageSC] export failed: " + ex.getMessage());
            } finally {
                if (idx >= 0 && idx != prev)
                    view.setIndex(prev);
            }
        }
        for (DCMView v : usedViews)
            v.repaint();
        JOptionPane.showMessageDialog(this,
                String.format(LanguageManager.get("export.sc.saved", "Exported %d key image(s) as DICOM SC to:"))
                        + "\n" + dir.getAbsolutePath(),
                LanguageManager.get("export.sc.title", "Export Key Images as DICOM SC"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * DCMView（ROI/アノテーションを含む描画状態）を元画像サイズの 8bit RGB BufferedImage にレンダリング
     * HOROS-20240407準拠: DCMView.m:11115 exportDCMCurrentImage:
     * (getRawPixelsViewWidth screenCapture: force8bits: 相当)
     */
    private java.awt.image.BufferedImage renderViewToBufferedImage(DCMView view, DicomPix pix) {
        int w = pix.getWidth();
        int h = pix.getHeight();
        if (w <= 0 || h <= 0)
            return null;
        try {
            java.awt.image.BufferedImage canvas = new java.awt.image.BufferedImage(w, h,
                    java.awt.image.BufferedImage.TYPE_3BYTE_BGR);
            java.awt.Graphics2D g = canvas.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.translate(-view.getInsets().left, -view.getInsets().top);
            view.paint(g);
            g.dispose();
            return canvas;
        } catch (Exception ex) {
            System.err.println("[KeyImageSC] render failed: " + ex.getMessage());
            return null;
        }
    }

    /**
     * 8bit RGB BufferedImage を DICOM Secondary Capture Image Storage (SC) ファイルとして書き出す
     * HOROS-20240407準拠: DICOMExport.mm (SOP 1.2.840.10008.5.1.4.1.1.7, Modality="SC")
     */
    private boolean writeSCImage(java.io.File outFile, java.awt.image.BufferedImage img, DicomPix pix,
            int seriesNumber, String seriesDescription) throws java.io.IOException {
        int w = img.getWidth();
        int h = img.getHeight();
        // 8bit RGB バイト列 (BGR 3バイト/ピクセル)
        java.awt.image.BufferedImage rgb = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_3BYTE_BGR);
        rgb.getGraphics().drawImage(img, 0, 0, null);
        byte[] pixelData = ((java.awt.image.DataBufferByte) rgb.getRaster().getDataBuffer()).getData();

        String now = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
        String uidRoot = "1.2.826.0.1.3680043.8.498"; // OculusJJ private UID root
        String seriesUID = uidRoot + "." + System.currentTimeMillis();
        String sopUID = seriesUID + "." + (System.nanoTime() % 100000L);

        org.dcm4che3.data.Attributes attrs = new org.dcm4che3.data.Attributes();
        attrs.setString(0x00080016, org.dcm4che3.data.VR.UI, "1.2.840.10008.5.1.4.1.1.7"); // SOPClassUID
        attrs.setString(0x00080018, org.dcm4che3.data.VR.UI, sopUID); // SOPInstanceUID
        attrs.setString(0x00080020, org.dcm4che3.data.VR.DA, now.substring(0, 8)); // StudyDate
        attrs.setString(0x00080030, org.dcm4che3.data.VR.DA, now.substring(0, 8)); // SeriesDate
        attrs.setString(0x00080033, org.dcm4che3.data.VR.TM, now.substring(8)); // StudyTime
        attrs.setString(0x00080060, org.dcm4che3.data.VR.CS, "SC"); // Modality
        attrs.setString(0x00080070, org.dcm4che3.data.VR.LO, "OculusJJ"); // Manufacturer
        attrs.setString(0x00081010, org.dcm4che3.data.VR.SH, "OculusJJ"); // InstitutionName
        attrs.setString(0x00081030, org.dcm4che3.data.VR.SH, "OculusJJ"); // StationName
        if (pix.getStudyDescription() != null && !pix.getStudyDescription().isEmpty())
            attrs.setString(0x00081030, org.dcm4che3.data.VR.LO, pix.getStudyDescription()); // StudyDescription
        attrs.setString(0x0008103E, org.dcm4che3.data.VR.LO, seriesDescription); // SeriesDescription
        if (pix.getAccessionNumber() != null && !pix.getAccessionNumber().isEmpty())
            attrs.setString(0x00080050, org.dcm4che3.data.VR.SH, pix.getAccessionNumber()); // AccessionNumber
        if (pix.getPatientID() != null && !pix.getPatientID().isEmpty())
            attrs.setString(0x00100020, org.dcm4che3.data.VR.SH, pix.getPatientID()); // PatientID
        com.oculus.jj.model.DicomImage imageObj = pix.getImageObj();
        if (imageObj != null) {
            if (imageObj.getPatientName() != null && !imageObj.getPatientName().isEmpty())
                attrs.setString(0x00100010, org.dcm4che3.data.VR.PN, imageObj.getPatientName()); // PatientName
            if (imageObj.getStudyDate() != null)
                attrs.setString(0x00080020, org.dcm4che3.data.VR.DA,
                        imageObj.getStudyDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")));
            if (imageObj.getAcquisitionDate() != null)
                attrs.setString(0x00080033, org.dcm4che3.data.VR.TM,
                        imageObj.getAcquisitionDate().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss")));
        }
        String studyUID = pix.getStudyInstanceUID();
        if (studyUID == null || studyUID.isEmpty())
            studyUID = uidRoot + ".study." + System.currentTimeMillis();
        attrs.setString(0x0020000D, org.dcm4che3.data.VR.UI, studyUID); // StudyInstanceUID
        attrs.setString(0x0020000E, org.dcm4che3.data.VR.UI, seriesUID); // SeriesInstanceUID
        attrs.setString(0x00200010, org.dcm4che3.data.VR.SH, "OCULUSJJ"); // StudyID
        attrs.setString(0x00201001, org.dcm4che3.data.VR.IS, String.valueOf(seriesNumber)); // SeriesNumber
        attrs.setString(0x00201002, org.dcm4che3.data.VR.IS, "1"); // InstanceNumber
        attrs.setString(0x00280002, org.dcm4che3.data.VR.CS,
                new String[] { "ORIGINAL", "PRIMARY", "SC" }); // ImageType
        attrs.setString(0x00280004, org.dcm4che3.data.VR.CS, "RGB"); // PhotometricInterpretation
        attrs.setInt(0x00280006, org.dcm4che3.data.VR.US, 3); // SamplesPerPixel
        attrs.setInt(0x00280010, org.dcm4che3.data.VR.US, h); // Rows
        attrs.setInt(0x00280011, org.dcm4che3.data.VR.US, w); // Columns
        attrs.setInt(0x00280101, org.dcm4che3.data.VR.US, 8); // BitsAllocated
        attrs.setInt(0x00280102, org.dcm4che3.data.VR.US, 8); // BitsStored
        attrs.setInt(0x00280103, org.dcm4che3.data.VR.US, 7); // HighBit
        attrs.setInt(0x00280104, org.dcm4che3.data.VR.US, 0); // PixelRepresentation
        double psx = pix.getPixelSpacingX();
        double psy = pix.getPixelSpacingY();
        if (psx > 0 && psy > 0)
            attrs.setString(0x00280030, org.dcm4che3.data.VR.DS,
                    new String[] { Double.toString(psy), Double.toString(psx) }); // PixelSpacing
        if (pix.getSrcFile() != null) {
            String src = pix.getSrcFile();
            if (src.length() > 64)
                src = src.substring(src.length() - 64);
            attrs.setString(0x00280008, org.dcm4che3.data.VR.LO, src); // ImageComments
        }
        attrs.setBytes(0x7FE00010, org.dcm4che3.data.VR.OB, pixelData); // PixelData

        java.io.File parent = outFile.getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();
        org.dcm4che3.io.DicomOutputStream dos = new org.dcm4che3.io.DicomOutputStream(outFile);
        org.dcm4che3.data.Attributes fmi = new org.dcm4che3.data.Attributes();
        fmi.setString(0x00020000, org.dcm4che3.data.VR.UI, "1.2.840.10008.5.1.4.1.1.7"); // MediaStorageSOPClassUID
        fmi.setString(0x00020001, org.dcm4che3.data.VR.UI, sopUID); // MediaStorageSOPInstanceUID
        fmi.setString(0x00020002, org.dcm4che3.data.VR.UI, "1.2.840.10008.1.2"); // TransferSyntaxUID (Explicit VR LE)
        dos.writeFileMetaInformation(fmi);
        dos.writeDataset(attrs, null);
        dos.finish();
        dos.close();
        return true;
    }

    /**
     * 次の/前のキー画像を検索
     * HOROS-20240407準拠: - (IBAction) findNextPreviousKeyImage: (id) sender
     */
    public void findNextPreviousKeyImage(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) return;
        List<DicomPix> list = view.getDcmPixList();
        if (list == null || list.isEmpty()) return;
        int cur = view.getCurImage();
        int dir = (sender instanceof Integer) ? (Integer) sender : 1;
        int count = list.size();
        // 次の/前のキー画像を検索（ラップアラウンド）
        for (int i = 1; i < count; i++) {
            int idx = (cur + dir * i + count * 100) % count;
            if (list.get(idx).getKeyImage()) {
                view.setIndex(idx);
                view.repaint();
                return;
            }
        }
        JOptionPane.showMessageDialog(this,
                LanguageManager.get("key.image.not.found", "No key image found"),
                "Key Image", JOptionPane.INFORMATION_MESSAGE);
    }

    // ========== 3D Viewerメニューアクションメソッド ==========

    /**
     * MPRビューア
     * HOROS-20240407準拠: - (IBAction) mprViewer: (id) sender
     */
    public void mprViewer(Object sender) {
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        if (!viewers.isEmpty()) {
            ViewerController active = viewers.get(viewers.size() - 1);
            MPRController.openMPR(active);
            // HOROS-20240407準拠: ビュータイプを更新してメニューを制御
            updateForViewType(MainMenuBar.ViewType.MPR_ORTHOGONAL);
        } else {
            JOptionPane.showMessageDialog(this, "Please open a 2D Viewer first to launch 3D MPR.");
        }
    }

    /**
     * CPRビューア
     * HOROS-20240407準拠: - (IBAction) cprViewer: (id) sender
     */
    public void cprViewer(Object sender) {
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        if (viewers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please open a 2D Viewer first.");
            return;
        }

        ViewerController active = viewers.get(viewers.size() - 1);
        DCMView dcmView = active.getImageView();

        // 選ばれているROIまたはパスを探す
        com.oculus.jj.model.ROI curvedRoi = null;
        for (com.oculus.jj.model.DicomPix pix : dcmView.getDcmPixList()) {
            for (com.oculus.jj.model.ROI roi : pix.getROIs()) {
                if (roi.getType() == DCMView.ToolMode.tCurvedROI) {
                    curvedRoi = roi;
                    break;
                }
            }
            if (curvedRoi != null)
                break;
        }

        if (curvedRoi != null) {
            final com.oculus.jj.model.ROI finalRoi = curvedRoi;
            SwingUtilities.invokeLater(() -> {
                // 統合された MPRController を使用するように修正 (古い CPRController は無視)
                MPRController mpr = new MPRController(active, MPRController.LayoutType.CURVED_MPR);
                // HOROS-20240407準拠: ビュータイプを更新してメニューを制御
                updateForViewType(MainMenuBar.ViewType.MPR_CURVED);
                List<double[]> pts = new java.util.ArrayList<>();
                for (com.oculus.jj.model.ROI.Point3D p : finalRoi.getPoints3D()) {
                    pts.add(new double[] { p.x, p.y, p.z });
                }
                mpr.setCurvePoints(pts);
                mpr.setVisible(true);
            });
        } else {
            // 見つからない場合はツールを切り替えて案内 (MPR側でも描画可能にする)
            DCMView.currentTool = DCMView.ToolMode.tCurvedROI;
            JOptionPane.showMessageDialog(this,
                    "Curved MPR requires a Curved ROI path.\nThe 'Curved' tool is now selected. Please draw a path on the image, then try again.");
        }
    }

    /**
     * 直交MPRビューア
     * HOROS-20240407準拠: - (IBAction) orthogonalMPRViewer: (id) sender
     */
    public void orthogonalMPRViewer(Object sender) {
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        if (!viewers.isEmpty()) {
            // 最前面のビューワーを使用（簡易的）
            ViewerController active = viewers.get(viewers.size() - 1);
            MPRController.openOrthogonal(active);
        } else {
            // ビューワーが開いていない場合は、選択中のシリーズから開くなどの処理が必要
            JOptionPane.showMessageDialog(this, "Please open a 2D Viewer first.");
        }
    }

    /**
     * VRビューア
     * HOROS-20240407準拠: - (IBAction) VRViewer: (id) sender
     */
    public void VRViewer(Object sender) {
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        if (!viewers.isEmpty()) {
            ViewerController active = viewers.get(viewers.size() - 1);
            Viewer3DController.openVR(active);
            // HOROS-20240407準拠: ビュータイプを更新してメニューを制御
            updateForViewType(MainMenuBar.ViewType.VIEWER_3D);
        } else {
            JOptionPane.showMessageDialog(this, "Please open a 2D Viewer first.");
        }
    }

    /**
     * MIPビューア (追加)
     */
    public void MIPViewer(Object sender) {
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        if (!viewers.isEmpty()) {
            ViewerController active = viewers.get(viewers.size() - 1);
            Viewer3DController.openMIP(active);
            // HOROS-20240407準拠: ビュータイプを更新してメニューを制御
            updateForViewType(MainMenuBar.ViewType.VIEWER_3D);
        } else {
            JOptionPane.showMessageDialog(this, "Please open a 2D Viewer first.");
        }
    }

    /**
     * SRビューア
     * HOROS-20240407準拠: - (IBAction) SRViewer: (id) sender
     */
    public void SRViewer(Object sender) {
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        if (!viewers.isEmpty()) {
            ViewerController active = viewers.get(viewers.size() - 1);
            Viewer3DController.openISO(active);
            // HOROS-20240407準拠: ビュータイプを更新してメニューを制御
            updateForViewType(MainMenuBar.ViewType.VIEWER_3D);
        } else {
            JOptionPane.showMessageDialog(this, "Please open a 2D Viewer first.");
        }
    }

        /**
     * 内視鏡ビューア
     * HOROS-20240407準拠: - (IBAction) endoscopyViewer: (id) sender
     */
    public void endoscopyViewer(Object sender) {
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        if (!viewers.isEmpty()) {
            ViewerController active = viewers.get(viewers.size() - 1);
            Viewer3DController.openEndoscopy(active);
            updateForViewType(MainMenuBar.ViewType.VIEWER_3D);
        } else {
            JOptionPane.showMessageDialog(this, LanguageManager.get("msg_3d_need_viewer",
                    "Please open a 2D Viewer first."));
        }
    }

    // ================= 3D Viewer メニュー (HOROS-20240407準拠: 追加項目) =================
    // 上記メニューに追加した 方向ビュー / FlyThru / Curved Path / Scissor Editing / 3Dプリセット
    // のハンドラ。未実装機能は「プレースホルダ」として整えて後から実装できる形にする。

    /** 方向ビューの適用 (HOROS: coView:/saView:/saViewOpposite:/axView: のJava相当) */
    private void openOrientationView(Viewer3DController.OrientationView v) {
        Viewer3DController active = Viewer3DController.getActiveInstance();
        if (active == null) {
            JOptionPane.showMessageDialog(this, LanguageManager.get("msg_3d_need_viewer",
                    "Please open a 3D Viewer first."));
            return;
        }
        active.setOrientationView(v);
        active.toFront();
        active.requestFocus();
    }

    /**
     * FlyThruポイントの追加 / 削除 / パスリセット (HOROS: flyThruButtonMenu:)
     * 現状はプレースホルダ: 実装後にこの分岐を伸ばす。
     */
    private void flyThruButtonMenu(int op) {
        Viewer3DController active = Viewer3DController.getActiveInstance();
        if (active == null) {
            JOptionPane.showMessageDialog(this, LanguageManager.get("msg_3d_need_viewer",
                    "Please open a 3D Viewer first."));
            return;
        }
        JOptionPane.showMessageDialog(this, LanguageManager.get("msg_3d_not_implemented",
                "Not yet implemented in this port."),
                LanguageManager.get("menu_viewer_3d_flythru_add", "Add FlyThru Point"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** ビジェーパスのファイル読み込み (HOROS: loadBezierPath:) — プレースホルダ */
    private void loadBezierPath(Object sender) {
        JOptionPane.showMessageDialog(this, LanguageManager.get("msg_3d_not_implemented",
                "Not yet implemented in this port."),
                LanguageManager.get("menu_viewer_3d_curved_path_load", "Load from file..."),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** ビジェーパスのファイル保存 (HOROS: saveBezierPath:) — プレースホルダ */
    private void saveBezierPath(Object sender) {
        JOptionPane.showMessageDialog(this, LanguageManager.get("msg_3d_not_implemented",
                "Not yet implemented in this port."),
                LanguageManager.get("menu_viewer_3d_curved_path_save", "Save to file..."),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** シザー状態の保存 / 読み込み / 削除 (HOROS: scissorStateButtons:) — プレースホルダ */
    private void scissorStateButtons(int op) {
        String title = (op == 0)
                ? LanguageManager.get("menu_viewer_3d_scissor_save", "Save")
                : (op == 1)
                        ? LanguageManager.get("menu_viewer_3d_scissor_load", "Load")
                        : LanguageManager.get("menu_viewer_3d_scissor_delete", "Delete");
        JOptionPane.showMessageDialog(this, LanguageManager.get("msg_3d_not_implemented",
                "Not yet implemented in this port."), title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 3Dプリセットの保存 (HOROS: save3DSettings:)。
     * 現在アクティブな3D Viewer の状態を JSON として ~/.oculusjj/3d_presets/ に保存する。
     */
    private void save3DSettings(Object sender) {
        Viewer3DController active = Viewer3DController.getActiveInstance();
        if (active == null) {
            JOptionPane.showMessageDialog(this, LanguageManager.get("msg_3d_need_viewer",
                    "Please open a 3D Viewer first."));
            return;
        }
        String name = JOptionPane.showInputDialog(this,
                LanguageManager.get("menu_viewer_3d_preset_save", "Save Current State as 3D Preset..."),
                "My 3D Preset");
        if (name == null || name.trim().isEmpty())
            return;
        try {
            java.io.File dir = new java.io.File(
                    System.getProperty("user.home"), ".oculusjj/3d_presets");
            dir.mkdirs();
            String safe = name.replaceAll("[^A-Za-z0-9_\\-\\u3040-\\u30ff\\u4e00-\\u9fff]", "_");
            java.io.File out = new java.io.File(dir, safe + ".json");
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            m.writerWithDefaultPrettyPrinter().writeValue(out, active.captureState());
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("msg_3d_preset_saved_to", "Saved 3D preset to:") + "\n" + out,
                    LanguageManager.get("menu_viewer_3d_preset_save", "Save Current State as 3D Preset..."),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("msg_3d_not_implemented", "Not yet implemented in this port.")
                            + "\n" + ex.getMessage(),
                    LanguageManager.get("menu_viewer_3d_preset_save", "Save Current State as 3D Preset..."),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 3Dプリセットの読み込み (HOROS: load3DSettings:)。
     * ~/.oculusjj/3d_presets/ 配下のJSON一覧を表示し、選択されたプリセットを適用する。
     */
    private void load3DSettings(Object sender) {
        Viewer3DController active = Viewer3DController.getActiveInstance();
        if (active == null) {
            JOptionPane.showMessageDialog(this, LanguageManager.get("msg_3d_need_viewer",
                    "Please open a 3D Viewer first."));
            return;
        }
        java.io.File dir = new java.io.File(System.getProperty("user.home"), ".oculusjj/3d_presets");
        java.io.File[] files = (dir.isDirectory()) ? dir.listFiles((d, n) -> n.toLowerCase().endsWith(".json")) : null;
        if (files == null || files.length == 0) {
            JOptionPane.showMessageDialog(this, LanguageManager.get("msg_3d_no_presets",
                    "No saved 3D presets were found."),
                    LanguageManager.get("menu_viewer_3d_preset_select", "Select 3D Preset..."),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(java.io.File::getName));
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++)
            names[i] = files[i].getName();
        int idx = JOptionPane.showOptionDialog(this,
                LanguageManager.get("menu_viewer_3d_preset_select", "Select 3D Preset..."),
                LanguageManager.get("menu_viewer_3d_preset_select", "Select 3D Preset..."),
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
        if (idx < 0 || idx >= files.length)
            return;
        try {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = m.readTree(files[idx]);
            if (node.has("windowLevel") && node.has("windowWidth")) {
                float wl = (float) node.get("windowLevel").asDouble();
                float ww = (float) node.get("windowWidth").asDouble();
                active.updateWindow(wl, ww);
            }
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("msg_3d_preset_loaded_from", "Loaded 3D preset from:") + "\n" + files[idx],
                    LanguageManager.get("menu_viewer_3d_preset_select", "Select 3D Preset..."),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("msg_3d_not_implemented", "Not yet implemented in this port.")
                            + "\n" + ex.getMessage(),
                    LanguageManager.get("menu_viewer_3d_preset_select", "Select 3D Preset..."),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ========== ROIメニューアクションメソッド ==========

    /** アクティブな2Dビュー (null=なし) */
    private DCMView activeDCMView() {
        return getActiveDCMViewForEdit();
    }

    /** アクティブシリーズの全ROI（DicomPix単位で集約） */
    private java.util.List<com.oculus.jj.model.ROI> seriesROIs() {
        java.util.List<com.oculus.jj.model.ROI> out = new java.util.ArrayList<>();
        DCMView view = activeDCMView();
        if (view == null)
            return out;
        java.util.Set<com.oculus.jj.model.ROI> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (DicomPix pix : view.getDcmPixList()) {
            if (pix == null)
                continue;
            for (com.oculus.jj.model.ROI roi : pix.getROIs()) {
                if (roi != null && seen.add(roi))
                    out.add(roi);
            }
        }
        return out;
    }

    /** 現在選択中/複数選択されたROI */
    private java.util.List<com.oculus.jj.model.ROI> selectedROIs() {
        java.util.List<com.oculus.jj.model.ROI> out = new java.util.ArrayList<>();
        DCMView view = activeDCMView();
        if (view == null)
            return out;
        if (view.getSelectedRoi() != null)
            out.add(view.getSelectedRoi());
        for (com.oculus.jj.model.ROI roi : seriesROIs()) {
            if (roi != view.getSelectedRoi() && roi.isSelected())
                out.add(roi);
        }
        return out;
    }

    /** 選択状態を有するROI（複数選択）の数を返す */
    private int multiSelectedCount() {
        int n = 0;
        for (com.oculus.jj.model.ROI roi : seriesROIs())
            if (roi.isSelected())
                n++;
        DCMView v = activeDCMView();
        if (v != null && v.getSelectedRoi() != null && !v.getSelectedRoi().isSelected())
            n++;
        return n;
    }

    private void info(String key, String def) {
        JOptionPane.showMessageDialog(this, LanguageManager.get(key, def));
    }

    private void infoFmt(String text, String title) {
        JOptionPane.showMessageDialog(this, text, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * ROIをファイルから読み込み
     * HOROS-20240407準拠: - (IBAction) roiLoadFromFiles: (id) sender
     */
    public void roiLoadFromFiles(Object sender) {
        DCMView view = activeDCMView();
        if (view == null || view.getCurDCM() == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        java.io.File[] files = fc.getSelectedFiles();
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        int loaded = 0;
        for (java.io.File f : files) {
            try {
                com.fasterxml.jackson.databind.JsonNode root = m.readTree(f);
                com.fasterxml.jackson.databind.JsonNode rois = root.get("rois");
                if (rois == null)
                    continue;
                for (com.fasterxml.jackson.databind.JsonNode r : rois) {
                    com.oculus.jj.model.ROI roi = buildROIFromJson(r, view.getCurDCM(), view);
                    if (roi != null) {
                        view.getCurDCM().getROIs().add(roi);
                        loaded++;
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.selected", "Selected ROIs: {0}"), loaded + " (loaded)"),
                LanguageManager.get("menu_roi_import", "Import ROI(s)..."));
    }

    private com.oculus.jj.model.ROI buildROIFromJson(com.fasterxml.jackson.databind.JsonNode r, DicomPix pix, DCMView view) {
        DCMView.ToolMode type = DCMView.ToolMode.tCPolygon;
        String t = r.path("type").asText("");
        for (DCMView.ToolMode tm : DCMView.ToolMode.values()) {
            if (tm.name().equals(t)) {
                type = tm;
                break;
            }
        }
        com.oculus.jj.model.ROI roi = new com.oculus.jj.model.ROI(type,
                (float) pix.getPixelSpacingX(), (float) pix.getPixelSpacingY(),
                new java.awt.geom.Point2D.Float(0f, 0f));
        roi.setName(r.path("name").asText(com.oculus.jj.config.AppPreferences.getRoiDefaultName()));
        roi.setComments(r.path("comments").asText(""));
        roi.setColor(java.awt.Color.decode(r.path("color").asText("#FFAA00")));
        roi.setLocked(r.path("locked").asBoolean(false));
        roi.setSelectable(r.path("selectable").asBoolean(true));
        roi.setThickness((float) r.path("thickness").asDouble(1.0));
        com.fasterxml.jackson.databind.JsonNode pts = r.path("points");
        java.util.List<java.awt.geom.Point2D.Float> list = new java.util.ArrayList<>();
        if (pts.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode p : pts) {
                list.add(new java.awt.geom.Point2D.Float((float) p.path("x").asDouble(), (float) p.path("y").asDouble()));
            }
        }
        roi.setPoints(list);
        roi.setSourcePix(pix);
        roi.setParentView(view);
        roi.setSOPInstanceUID(pix.getSOPInstanceUID());
        return roi;
    }

    /**
     * 選択されたROIを保存
     * HOROS-20240407準拠: - (IBAction) roiSaveSelected: (id) sender
     */
    public void roiSaveSelected(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        saveROIsToFile(rois);
    }

    /**
     * シリーズのROIを保存
     * HOROS-20240407準拠: - (IBAction) roiSaveSeries: (id) sender
     */
    public void roiSaveSeries(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = seriesROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        saveROIsToFile(rois);
    }

    private void saveROIsToFile(java.util.List<com.oculus.jj.model.ROI> rois) {
        DCMView view = activeDCMView();
        JFileChooser fc = new JFileChooser();
        if (view != null && view.getCurDCM() != null) {
            fc.setSelectedFile(new java.io.File("ROIs_" + view.getCurDCM().getSOPInstanceUID() + ".json"));
        }
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        java.io.File out = fc.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".json"))
            out = new java.io.File(out.getAbsolutePath() + ".json");
        try {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ArrayNode arr = m.createArrayNode();
            for (com.oculus.jj.model.ROI roi : rois) {
                com.fasterxml.jackson.databind.node.ObjectNode o = m.createObjectNode();
                o.put("type", roi.getType() != null ? roi.getType().name() : "tCPolygon");
                o.put("name", roi.getName() != null ? roi.getName() : "");
                o.put("comments", roi.getComments() != null ? roi.getComments() : "");
                o.put("color", "#" + Integer.toHexString(roi.getColor().getRGB()));
                o.put("locked", roi.isLocked());
                o.put("selectable", roi.isSelectable());
                o.put("thickness", roi.getThickness());
                o.put("sopInstanceUID", roi.getSOPInstanceUID() != null ? roi.getSOPInstanceUID() : "");
                com.fasterxml.jackson.databind.node.ArrayNode pts = m.createArrayNode();
                for (java.awt.geom.Point2D.Float p : roi.getPoints()) {
                    com.fasterxml.jackson.databind.node.ObjectNode p2 = m.createObjectNode();
                    p2.put("x", p.x);
                    p2.put("y", p.y);
                    pts.add(p2);
                }
                o.set("points", pts);
                arr.add(o);
            }
            com.fasterxml.jackson.databind.node.ObjectNode root = m.createObjectNode();
            root.put("sopInstanceUID", view != null && view.getCurDCM() != null ? view.getCurDCM().getSOPInstanceUID() : "");
            root.set("rois", arr);
            m.writerWithDefaultPrettyPrinter().writeValue(out, root);
            infoFmt(LanguageManager.get("roi.msg.saved", "Saved ROI(s) to:") + "\n" + out,
                    LanguageManager.get("menu_roi_save_selected", "Save Selected ROI(s)..."));
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    LanguageManager.get("menu_roi_save_selected", "Save Selected ROI(s)..."),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 全てのROIを削除
     * HOROS-20240407準拠: - (IBAction) roiDeleteAll: (id) sender
     */
    public void roiDeleteAll(Object sender) {
        DCMView view = activeDCMView();
        if (view == null)
            return;
        int total = 0;
        for (DicomPix pix : view.getDcmPixList()) {
            if (pix != null) {
                total += pix.getROIs().size();
                pix.getROIs().clear();
            }
        }
        view.selectedRoi = null;
        view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.rois_removed", "Removed ROIs: {0}"), total),
                LanguageManager.get("menu_roi_delete_all", "Delete All ROIs in this Series"));
    }

    /**
     * 同じ名前のROIを削除
     * HOROS-20240407準拠: - (IBAction) roiDeleteAllROIsWithSameName: (id) sender
     */
    public void roiDeleteAllROIsWithSameName(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> sel = selectedROIs();
        if (sel.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        String name = sel.get(0).getName();
        if (name == null || name.isEmpty()) {
            info("roi.msg.no_selection", "Selected ROI has no name.");
            return;
        }
        DCMView view = activeDCMView();
        int removed = 0;
        for (DicomPix pix : view.getDcmPixList()) {
            if (pix == null)
                continue;
            java.util.List<com.oculus.jj.model.ROI> rois = pix.getROIs();
            java.util.Iterator<com.oculus.jj.model.ROI> it = rois.iterator();
            while (it.hasNext()) {
                com.oculus.jj.model.ROI roi = it.next();
                if (name.equals(roi.getName())) {
                    it.remove();
                    removed++;
                }
            }
        }
        if (view.getSelectedRoi() != null && name.equals(view.getSelectedRoi().getName()))
            view.selectedRoi = null;
        view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.rois_removed", "Removed ROIs: {0}"), removed),
                "Delete All ROIs with Same Name");
    }

    /**
     * ROIを選択/選択解除
     * HOROS-20240407準拠: - (IBAction) roiSelectDeselectAll: (id) sender
     * sender == "deselect" 時に全解除
     */
    public void roiSelectDeselectAll(Object sender) {
        boolean select = !"deselect".equals(sender);
        for (com.oculus.jj.model.ROI roi : seriesROIs())
            roi.setSelected(select);
        DCMView view = activeDCMView();
        if (view != null)
            view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.selected", "Selected ROIs: {0}"), multiSelectedCount()),
                select ? LanguageManager.get("menu_roi_select_all", "Select All ROIs")
                        : LanguageManager.get("menu_roi_deselect_all", "Deselect All ROIs"));
    }

    /**
     * ROIマネージャーを取得
     * HOROS-20240407準拠: - (IBAction) roiGetManager: (id) sender
     */
    public void roiGetManager(Object sender) {
        DCMView view = activeDCMView();
        if (view == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        AnnotationsPanel.toggle(this, view);
    }

    /**
     * ROI情報を取得
     * HOROS-20240407準拠: - (IBAction) roiGetInfo: (id) sender
     */
    public void roiGetInfo(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty())
            rois = seriesROIs();
        if (rois.isEmpty()) {
            info("roi.msg.history_empty", "No statistics available for the selected ROI(s).");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (com.oculus.jj.model.ROI roi : rois) {
            sb.append("• ").append(roi.getName() != null ? roi.getName() : "").append("\n");
            sb.append("  ").append(roi.getType()).append("  ")
                    .append(String.format("Area=%.2f  Mean=%.2f  SD=%.2f  Min=%.1f  Max=%.1f",
                            roi.getArea() / 100.0, roi.getMean(), roi.getStDev(), roi.getMin(), roi.getMax()))
                    .append("\n");
        }
        infoFmt(sb.toString(), LanguageManager.get("roi.msg.history", "ROI Statistics"));
    }

    /**
     * ROIをリネーム
     * HOROS-20240407準拠: - (IBAction) roiRename: (id) sender
     */
    public void roiRename(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        String name = JOptionPane.showInputDialog(this,
                LanguageManager.get("menu_roi_rename", "ROI Rename..."), rois.get(0).getName());
        if (name == null)
            return;
        for (com.oculus.jj.model.ROI roi : rois)
            roi.setName(name);
        DCMView v = activeDCMView();
        if (v != null)
            v.repaint();
    }

    /**
     * ROIデフォルト
     * HOROS-20240407準拠: - (IBAction) roiDefaults: (id) sender
     */
    public void roiDefaults(Object sender) {
        String name = JOptionPane.showInputDialog(this,
                LanguageManager.get("menu_roi_defaults", "Set Default ROI Name..."),
                com.oculus.jj.config.AppPreferences.getRoiDefaultName());
        if (name == null || name.isEmpty())
            return;
        com.oculus.jj.config.AppPreferences.setRoiDefaultName(name);
    }

    // ========== ROIメニューアクションメソッド（HOROS追加項目） ==========

    /** 表示オプション切替 (HOROS: updateViews:)。
     *  op=0: 選択中ROIのみ情報表示  op=1: マウスオーバー  op=2: 名前のみ  op=3: コッブ角
     */
    private void updateViews(int op) {
        boolean sel = !com.oculus.jj.config.AppPreferences.isRoiInfoOnlyWhenSelected();
        com.oculus.jj.config.AppPreferences.setRoiInfoOnlyWhenSelected(sel);
        DCMView v2 = activeDCMView();
        if (v2 != null)
            v2.repaint();
    }

    // ===== Thickness / Font (HOROS: increaseThickness / decreaseThickness / increaseFontSize / decreaseFontSize) =====

    /** 選択/系列のROI線太を太く (HOROS: increaseThickness:) */
    public void increaseThickness(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty())
            rois = seriesROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        float t = rois.get(0).getThickness() + 1.0f;
        for (com.oculus.jj.model.ROI r : rois)
            r.setThickness(Math.max(1.0f, t));
        DCMView v = activeDCMView();
        if (v != null)
            v.repaint();
    }

    /** 選択/系列のROI線太を細く (HOROS: decreaseThickness:) */
    public void decreaseThickness(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty())
            rois = seriesROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        float t = rois.get(0).getThickness() - 1.0f;
        for (com.oculus.jj.model.ROI r : rois)
            r.setThickness(Math.max(1.0f, t));
        DCMView v = activeDCMView();
        if (v != null)
            v.repaint();
    }

    /** ROIフォントを大きく (HOROS: increaseFontSize:) */
    public void increaseFontSize(Object sender) {
        double s = com.oculus.jj.config.AppPreferences.getRoiFontScale() + 0.25;
        com.oculus.jj.config.AppPreferences.setRoiFontScale(Math.min(3.0, s));
        DCMView v = activeDCMView();
        if (v != null)
            v.repaint();
    }

    /** ROIフォントを小さく (HOROS: decreaseFontSize:) */
    public void decreaseFontSize(Object sender) {
        double s = com.oculus.jj.config.AppPreferences.getRoiFontScale() - 0.25;
        com.oculus.jj.config.AppPreferences.setRoiFontScale(Math.max(0.5, s));
        DCMView v = activeDCMView();
        if (v != null)
            v.repaint();
    }

    // ===== Flip (HOROS: flipROIHorizontally / flipROIVertically) =====

    /** 選択ROIを水平反転 (HOROS: flipROIHorizontally:) */
    public void flipROIHorizontally(Object sender) {
        flipROI(true);
    }

    /** 選択ROIを垂直反転 (HOROS: flipROIVertically:) */
    public void flipROIVertically(Object sender) {
        flipROI(false);
    }

    private void flipROI(boolean horizontal) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        DCMView view = activeDCMView();
        int w = view != null && view.getCurDCM() != null ? view.getCurDCM().getWidth() : 0;
        int h = view != null && view.getCurDCM() != null ? view.getCurDCM().getHeight() : 0;
        for (com.oculus.jj.model.ROI roi : rois) {
            java.util.List<java.awt.geom.Point2D.Float> pts = roi.getPoints();
            for (int i = 0; i < pts.size(); i++) {
                java.awt.geom.Point2D.Float p = pts.get(i);
                if (horizontal && w > 0)
                    pts.set(i, new java.awt.geom.Point2D.Float(w - p.x, p.y));
                else if (!horizontal && h > 0)
                    pts.set(i, new java.awt.geom.Point2D.Float(p.x, h - p.y));
            }
            roi.setPoints(new java.util.ArrayList<>(pts));
        }
        if (view != null)
            view.repaint();
    }

    // ===== Statistics (HOROS: roiHistogram) =====

    /** 選択ROIのヒストグラム表示 (HOROS: roiHistogram:) */
    public void roiHistogram(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        com.oculus.jj.model.ROI roi = rois.get(0);
        int[] bins = new int[64];
        DCMView view = activeDCMView();
        DicomPix pix = view != null ? view.getCurDCM() : null;
        if (pix == null)
            return;
        int w = pix.getWidth();
        int h = pix.getHeight();
        for (java.awt.geom.Point2D.Float p : roi.getPoints()) {
            if (p.x < 0 || p.x >= w || p.y < 0 || p.y >= h)
                continue;
            int v = pix.getShortData()[(int) p.y * w + (int) p.x] & 0xFFFF;
            int bin = Math.min(63, v / 64);
            bins[bin]++;
        }
        int max = 1;
        for (int b : bins)
            max = Math.max(max, b);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bins.length; i++) {
            sb.append(String.format("%5d-%5d ", i * 64, i * 64 + 63));
            int bar = (int) (Math.round((double) bins[i] / max * 40));
            for (int k = 0; k < bar; k++)
                sb.append('#');
            sb.append("  ").append(bins[i]).append("\n");
        }
        javax.swing.JTextArea ta = new javax.swing.JTextArea(sb.toString(), 30, 80);
        ta.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        ta.setEditable(false);
        javax.swing.JScrollPane sp = new javax.swing.JScrollPane(ta);
        sp.setPreferredSize(new java.awt.Dimension(420, 500));
        JOptionPane.showMessageDialog(this, sp,
                LanguageManager.get("roi.msg.history", "ROI Statistics") + " - "
                        + (roi.getName() != null ? roi.getName() : ""),
                JOptionPane.INFORMATION_MESSAGE);
    }

    // ===== Layer / Volume / Propagate (HOROS) =====

    /** 選択ROIからレイヤーを作成 (HOROS: createLayerROIFromSelectedROI:) - プレースホルダ */
    public void createLayerROIFromSelectedROI(Object sender) {
        info("roi.msg.not_implemented", "Not yet implemented in this port.");
    }

    /** ROI体積 / Generate Missing ROIs (HOROS: roiVolume: tag=0/1) */
    public void roiVolume(Object sender) {
        int op = (sender instanceof Integer) ? (Integer) sender : 0;
        if (op == 1) {
            generateMissingROIs();
            return;
        }
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        DCMView view = activeDCMView();
        DicomPix pix = view != null ? view.getCurDCM() : null;
        double thickness = pix != null ? pix.getSliceThickness() : 1.0;
        if (thickness <= 0)
            thickness = 1.0;
        double volume = 0;
        for (com.oculus.jj.model.ROI roi : rois)
            volume += roi.getArea() / 100.0 * thickness;
        infoFmt(String.format("%.2f mm^3", volume), LanguageManager.get("roi.msg.volume", "ROI Volume (approx.)"));
    }

    /**
     * HOROS-20240407準拠: computeVolume ... generateMissingROIs:YES相当。
     * 選択したROIの名前を共有するスライス間にある「空のスライス」に、
     * 隣接する2つの同名ROIを線形補間（点同士の比率補間）した生成ROIを挿入する。
     * comments="morphing generated" で識別し、 roiDeleteGeneratedROIs で削除可能。
     * 近似: HOROSのITK等値線/isoContourではなく、点列の線形補間を使う（点列長が一致しない場合は一致部分のみ使用）。
     */
    private void generateMissingROIs() {
        DCMView view = activeDCMView();
        if (view == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        com.oculus.jj.model.ROI src = rois.get(0);
        String name = src.getName();
        List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || pixList.size() < 2) {
            info("roi.msg.single_image", "There is only one image in this series. Nothing to propagate!");
            return;
        }

        // (1) まず、既に生成済みの同名ROIを削除（HOROS: roiDeleteGeneratedROIsForName: [selectedRoi name]）
        int removed = 0;
        for (DicomPix pix : pixList) {
            if (pix == null)
                continue;
            java.util.List<com.oculus.jj.model.ROI> rr = pix.getROIs();
            for (int i = rr.size() - 1; i >= 0; i--) {
                com.oculus.jj.model.ROI r = rr.get(i);
                if (r != null && "morphing generated".equals(r.getComments())
                        && (name == null || name.equals(r.getName()))) {
                    rr.remove(i);
                    removed++;
                }
            }
        }

        // (2) 各スライス上の同名ROIをスライス順に収集
        int n = pixList.size();
        @SuppressWarnings("unchecked")
        com.oculus.jj.model.ROI[] roiBySlice = new com.oculus.jj.model.ROI[n];
        for (int i = 0; i < n; i++) {
            DicomPix pix = pixList.get(i);
            if (pix == null)
                continue;
            for (com.oculus.jj.model.ROI r : pix.getROIs()) {
                if (r != null && name != null && name.equals(r.getName())
                        && !"morphing generated".equals(r.getComments())) {
                    roiBySlice[i] = r;
                    break;
                }
            }
        }

        // (3) 隣接する2つの同名ROIの間の空きスライスに補間ROIを生成
        int generated = 0;
        com.oculus.jj.model.ROI last = null;
        int lastIdx = -1;
        for (int i = 0; i < n; i++) {
            if (roiBySlice[i] != null) {
                if (last != null && (lastIdx + 1) < i) {
                    for (int y = lastIdx + 1; y < i; y++) {
                        float ratio = (float) (y - lastIdx) / (float) (i - lastIdx);
                        com.oculus.jj.model.ROI c = morphBetween(last, roiBySlice[i], ratio);
                        if (c != null) {
                            DicomPix targetPix = pixList.get(y);
                            if (targetPix != null) {
                                targetPix.addROI(c);
                                generated++;
                            }
                        }
                    }
                }
                last = roiBySlice[i];
                lastIdx = i;
            }
        }
        view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.generated", "Removed {0} previous, generated {1} new ROI(s)."), removed, generated),
                LanguageManager.get("roi.msg.generated_title", "Generate Missing ROIs"));
    }

    /**
     * 2つのROIの点列を線形補間（HOROS: roiMorphingBetween: の近似）。
     * 点列長が異なる場合は短い方の長さを基準に補間する。
     * 生成ROI: type=tCPolygon, name=親名, comments="morphing generated"。
     */
    private com.oculus.jj.model.ROI morphBetween(com.oculus.jj.model.ROI a,
            com.oculus.jj.model.ROI b, float ratio) {
        if (a == null || b == null)
            return null;
        List<java.awt.geom.Point2D.Float> aPts = a.getPoints();
        List<java.awt.geom.Point2D.Float> bPts = b.getPoints();
        if (aPts == null || bPts == null || aPts.size() < 2 || bPts.size() < 2)
            return null;
        int m = Math.min(aPts.size(), bPts.size());
        if (m < 2)
            return null;
        List<java.awt.geom.Point2D.Float> out = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            java.awt.geom.Point2D.Float p0 = aPts.get(i);
            java.awt.geom.Point2D.Float p1 = bPts.get(i);
            float x = (float) (p0.x + (p1.x - p0.x) * ratio);
            float y = (float) (p0.y + (p1.y - p0.y) * ratio);
            out.add(new java.awt.geom.Point2D.Float(x, y));
        }
        com.oculus.jj.model.ROI c = new com.oculus.jj.model.ROI(DCMView.ToolMode.tCPolygon,
                (float) a.getPixelSpacingX(), (float) a.getPixelSpacingY(), null);
        c.setPoints(out);
        c.setName(a.getName());
        c.setColor(a.getColor());
        c.setOpacity(a.getOpacity());
        c.setThickness(a.getThickness());
        c.setComments("morphing generated");
        c.setSelected(false);
        return c;
    }

    /** 生成ROIを削除 (HOROS: roiDeleteGeneratedROIs:)
     *  HOROS: comments == "morphing generated" のROIを削除する。
     */
    public void roiDeleteGeneratedROIs(Object sender) {
        DCMView view = activeDCMView();
        if (view == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        int removed = 0;
        for (DicomPix pix : view.getDcmPixList()) {
            if (pix == null)
                continue;
            java.util.List<com.oculus.jj.model.ROI> rois = pix.getROIs();
            for (int i = rois.size() - 1; i >= 0; i--) {
                com.oculus.jj.model.ROI r = rois.get(i);
                if (r != null && "morphing generated".equals(r.getComments())) {
                    rois.remove(i);
                    removed++;
                }
            }
        }
        view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.generated_deleted", "Removed {0} generated ROI(s)."), removed),
                LanguageManager.get("roi.msg.generated_deleted_title", "Remove Generated ROIs"));
    }

    /** ROI内容消去/復元 (HOROS: roiVolumeEraseRestore:) */
    public void roiVolumeEraseRestore(Object sender) {
        int op = (sender instanceof Integer) ? (Integer) sender : 0;
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        DCMView view = activeDCMView();
        if (view == null || view.getCurDCM() == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        for (com.oculus.jj.model.ROI roi : rois)
            roi.setEraseMode(op == 1);
        view.repaint();
    }

    /** ROI伝播 (HOROS: roiPropagateSetup:)
     *  HOROSではダイアログで伝播範囲を指定する。
     *  Java版では簡易ダイアログで「全フレームへ伝播」を実行。
     */
    public void roiPropagateSetup(Object sender) {
        DCMView view = activeDCMView();
        if (view == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || pixList.size() < 2) {
            info("roi.msg.single_image", "There is only one image in this series. Nothing to propagate!");
            return;
        }
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI(s) selected to propagate on the series!");
            return;
        }
        int from = 0, to = pixList.size() - 1;
        int cur = view.getCurImage();
        int confirm = JOptionPane.showConfirmDialog(this,
                LanguageManager.get("roi.msg.propagate_all",
                        "Propagate selected ROI(s) to all frames of the series?"),
                LanguageManager.get("roi.msg.propagate_title", "Propagate ROI"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION)
            return;
        int added = 0;
        for (int i = from; i <= to; i++) {
            if (i == cur)
                continue;
            DicomPix pix = pixList.get(i);
            if (pix == null)
                continue;
            for (com.oculus.jj.model.ROI roi : rois) {
                pix.addROI(roi.copy());
                added++;
            }
        }
        view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.propagated", "Propagated {0} ROI(s) to other frames."), added),
                LanguageManager.get("roi.msg.propagated_title", "ROI Propagation"));
    }

    /** ROI伝播スラブ (HOROS: roiPropagateSlab:)
     *  HOROS: スラブ(stack)内の近傍スライスに選択ROIをコピーする。
     *  Java版: 現在スライスの前後 N 枚の範囲にコピー（N はスライス厚/ピクセル間隔から推定、最小1）。
     */
    public void roiPropagateSlab(Object sender) {
        DCMView view = activeDCMView();
        if (view == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        List<DicomPix> pixList = view.getDcmPixList();
        if (pixList == null || pixList.size() < 2) {
            info("roi.msg.single_image", "There is only one image in this series. Nothing to propagate!");
            return;
        }
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI(s) selected to propagate on the series!");
            return;
        }
        // スラブの厚さを推定（スライス厚/ピクセル間隔から。1以上の整数）
        int slab = 1;
        DicomPix curPix = pixList.get(Math.min(view.getCurImage(), pixList.size() - 1));
        if (curPix != null) {
            double ps = curPix.getPixelSpacingY();
            double th = curPix.getSliceThickness();
            if (ps > 0 && th > 0)
                slab = Math.max(1, (int) Math.round(th / ps));
        }
        int cur = view.getCurImage();
        int start = Math.max(0, cur - slab);
        int end = Math.min(pixList.size() - 1, cur + slab);
        int added = 0;
        for (int i = start; i <= end; i++) {
            if (i == cur)
                continue;
            DicomPix pix = pixList.get(i);
            if (pix == null)
                continue;
            for (com.oculus.jj.model.ROI roi : rois) {
                pix.addROI(roi.copy());
                added++;
            }
        }
        view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.propagated_slab", "Propagated {0} ROI(s) to ±{1} slab frame(s)."), added, slab),
                LanguageManager.get("roi.msg.propagated_slab_title", "ROI Propagate to Slab"));
    }

    // ===== Group / Lock / Selectable (HOROS) =====

    /** 選択ROIをグループ化 (HOROS: groupSelectedROIs:) */
    public void groupSelectedROIs(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        // HOROS: newGroupID = [NSDate timeIntervalSinceReferenceDate] (一意な時刻ベースID)
        long gid = System.currentTimeMillis();
        for (com.oculus.jj.model.ROI r : rois)
            r.setGroupID((int) gid);
        DCMView v = activeDCMView();
        if (v != null)
            v.repaint();
        info("roi.msg.grouped", "ROI group updated.");
    }

    /** 選択ROIのグループ解除 (HOROS: ungroupSelectedROIs:) */
    public void ungroupSelectedROIs(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        // HOROS: newGroupID = 0 (非グループ)
        for (com.oculus.jj.model.ROI r : rois)
            r.setGroupID(-1);
        DCMView v = activeDCMView();
        if (v != null)
            v.repaint();
        info("roi.msg.ungrouped", "ROI group cleared.");
    }

    /** 選択ROIをロック (HOROS: lockSelectedROIs:) */
    public void lockSelectedROIs(Object sender) {
        setROIsLock(true);
    }

    /** 選択ROIのロック解除 (HOROS: unlockSelectedROIs:) */
    public void unlockSelectedROIs(Object sender) {
        setROIsLock(false);
    }

    private void setROIsLock(boolean lock) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty())
            rois = seriesROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        for (com.oculus.jj.model.ROI r : rois)
            r.setLocked(lock);
        DCMView v = activeDCMView();
        if (v != null)
            v.repaint();
    }

    /** 選択ROIを選択不能にする (HOROS: makeSelectedROIsUnselectable:) */
    public void makeSelectedROIsUnselectable(Object sender) {
        setROIsSelectable(false, false);
    }

    /** 全ROIを選択可能にする (HOROS: makeAllROIsSelectable:) */
    public void makeAllROIsSelectable(Object sender) {
        setROIsSelectable(true, true);
    }

    private void setROIsSelectable(boolean selectable, boolean all) {
        java.util.List<com.oculus.jj.model.ROI> rois = all ? seriesROIs() : selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        for (com.oculus.jj.model.ROI r : rois)
            r.setSelectable(selectable);
        DCMView v = activeDCMView();
        if (v != null)
            v.repaint();
    }

    // ===== Set Pixel Values / Grow Region (HOROS) =====

    /** ROI内のピクセル値を設定 (HOROS: roiSetPixelsSetup:) */
    public void roiSetPixelsSetup(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        String s = JOptionPane.showInputDialog(this,
                LanguageManager.get("menu_roi_set_pixel_values", "Set Pixel Values to..."), "0");
        if (s == null)
            return;
        int val;
        try {
            val = (int) Short.parseShort(s.trim());
        } catch (Exception ex) {
            val = 0;
        }
        DCMView view = activeDCMView();
        if (view == null || view.getCurDCM() == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        DicomPix pix = view.getCurDCM();
        short[] data = pix.getShortData();
        if (data == null)
            return;
        int w = pix.getWidth();
        int h = pix.getHeight();
        int count = 0;
        for (com.oculus.jj.model.ROI roi : rois) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (roi.isPointInside(new java.awt.geom.Point2D.Float(x, y))) {
                        data[y * w + x] = (short) val;
                        count++;
                    }
                }
            }
        }
        pix.setShortImageData(data);
        view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.pixels_set", "Pixel values set inside ROI: {0}"), count),
                LanguageManager.get("menu_roi_set_pixel_values", "Set Pixel Values to..."));
    }

    /** 領域成長 (HOROS: segmentationTest:) - 既存のViewer3DController実装にフォールバック */
    public void segmentationTest(Object sender) {
        DCMView view = activeDCMView();
        if (view == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        // 領域成長は3D Viewer内の実装(Viewer3DController)に委譲するのがHOROS準拠。
        // 2Dメニューからは案内表示にとどめる
        infoFmt(LanguageManager.get("roi.msg.not_implemented", "Not yet implemented in this port."),
                LanguageManager.get("menu_roi_grow_region", "Grow Region (2D/3D Segmentation)..."));
    }

    // ===== Brush ROIs (HOROS) =====

    /** ブラッシュROI (HOROS: applyConvolutionOnSource: tag=0) - プレースホルダ */
    public void applyConvolutionOnSource(Object sender) {
        DCMView view = activeDCMView();
        if (view == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        view.currentTool = DCMView.ToolMode.tBrush;
        view.repaint();
    }

    /** ブラッシュ形態学操作 (HOROS: morphoSelectedBrushROI:) */
    public void morphoSelectedBrushROI(Object sender) {
        int op = (sender instanceof Integer) ? (Integer) sender : 0;
        DCMView view = activeDCMView();
        if (view == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        boolean ok;
        switch (op) {
            case 1: // Dilatation
                ok = view.applyBrushMorph(true, 2);
                break;
            case 2: // Closing (Dilate + Erode)
                view.applyBrushMorph(true, 2);
                ok = view.applyBrushMorph(false, 2);
                break;
            case 3: // Opening (Erode + Dilate)
                view.applyBrushMorph(false, 2);
                ok = view.applyBrushMorph(true, 2);
                break;
            default:
                ok = false;
        }
        if (!ok)
            info("roi.msg.no_selection", "No ROI is selected.");
    }

    // ===== Convert / Merge (HOROS) =====

    /** ブラッシュ/ポリゴン変換 (HOROS: convertBrushPolygon:)
     *  HOROS: textureベース（brush→ITKコンター抽出 / polygon→texture化）。
     *  Java版: 近似で
     *   - ブラッシュ(点集合) → ポリゴン: 点の凸包(convex hull)をポリゴンとして作成
     *   - ポリゴン(点集合) → ブラッシュ: 辺を密化した点をブラッシュとして作成
     */
    public void convertBrushPolygon(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        if (rois.isEmpty()) {
            info("roi.msg.no_selection", "No ROI is selected.");
            return;
        }
        DCMView view = activeDCMView();
        if (view == null) {
            info("roi.msg.no_rois", "No ROIs in the current series.");
            return;
        }
        DicomPix pix = view.getCurDCM();
        if (pix == null)
            return;
        int converted = 0;
        for (com.oculus.jj.model.ROI roi : rois) {
            List<java.awt.geom.Point2D.Float> pts = roi.getPoints();
            if (pts == null || pts.size() < 2)
                continue;
            DCMView.ToolMode newType;
            List<java.awt.geom.Point2D.Float> newPts;
            if (roi.getType() == DCMView.ToolMode.tPencil || roi.isEraseMode()
                    || roi.getThickness() > 1) {
                // ブラッシュ → ポリゴン（凸包）
                newType = DCMView.ToolMode.tCPolygon;
                newPts = convexHull(pts);
            } else if (roi.getType() == DCMView.ToolMode.tCPolygon
                    || roi.getType() == DCMView.ToolMode.tOPolygon
                    || roi.getType() == DCMView.ToolMode.tPencil) {
                // ポリゴン → ブラッシュ（辺の密化）
                newType = DCMView.ToolMode.tPencil;
                newPts = densifyPolygon(pts, 8);
            } else {
                continue;
            }
            if (newPts == null || newPts.size() < 2)
                continue;
            com.oculus.jj.model.ROI nroi = new com.oculus.jj.model.ROI(newType,
                    (float) pix.getPixelSpacingX(), (float) pix.getPixelSpacingY(), null);
            nroi.setPoints(newPts);
            nroi.setName(roi.getName());
            nroi.setComments(roi.getComments());
            nroi.setColor(roi.getColor());
            nroi.setOpacity(roi.getOpacity());
            nroi.setSelected(true);
            pix.addROI(nroi);
            pix.removeROI(roi);
            if (newType == DCMView.ToolMode.tPencil)
                nroi.setThickness(3);
            converted++;
        }
        view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.converted", "Converted {0} ROI(s)."), converted),
                LanguageManager.get("roi.msg.converted_title", "Convert Brush/Polygon"));
    }

    /** ブラッシュROI結合 (HOROS: mergeBrushROI:)
     *  HOROS: textureの統合。Java版: 点集合の統合（最初のROIに他の点を追加し、他を削除）。
     */
    public void mergeBrushROI(Object sender) {
        java.util.List<com.oculus.jj.model.ROI> rois = selectedROIs();
        // 同じタイプかつ点集合を持つものだけを扱う
        java.util.List<com.oculus.jj.model.ROI> brush = new java.util.ArrayList<>();
        for (com.oculus.jj.model.ROI r : rois) {
            if (r != null && r.getPoints() != null && !r.getPoints().isEmpty()
                    && (r.getType() == DCMView.ToolMode.tPencil || r.getThickness() > 1))
                brush.add(r);
        }
        if (brush.size() < 2) {
            info("roi.msg.merge_need_two", "Select at least two brush ROIs to merge.");
            return;
        }
        DCMView view = activeDCMView();
        if (view == null)
            return;
        DicomPix pix = view.getCurDCM();
        if (pix == null)
            return;
        com.oculus.jj.model.ROI target = brush.get(0);
        List<java.awt.geom.Point2D.Float> all = new ArrayList<>(target.getPoints());
        for (int i = 1; i < brush.size(); i++) {
            all.addAll(brush.get(i).getPoints());
            pix.removeROI(brush.get(i));
        }
        target.setPoints(all);
        view.repaint();
        infoFmt(String.format(LanguageManager.get("roi.msg.merged", "Merged {0} brush ROIs."), brush.size()),
                LanguageManager.get("roi.msg.merged_title", "Merge Brush ROIs"));
    }

    /** Andrew's monotone chain convex hull（ブラシ→ポリゴンの近似） */
    private static List<java.awt.geom.Point2D.Float> convexHull(List<java.awt.geom.Point2D.Float> pts) {
        List<java.awt.geom.Point2D.Float> p = new ArrayList<>(pts);
        p.sort((a, b) -> a.x != b.x ? Float.compare(a.x, b.x) : Float.compare(a.y, b.y));
        int n = p.size();
        if (n <= 2)
            return p;
        java.util.List<java.awt.geom.Point2D.Float> hull = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p.get(i)) <= 0)
                hull.remove(hull.size() - 1);
            hull.add(p.get(i));
        }
        int lower = hull.size() + 1;
        for (int i = n - 2; i >= 0; i--) {
            while (hull.size() > lower && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p.get(i)) <= 0)
                hull.remove(hull.size() - 1);
            hull.add(p.get(i));
        }
        hull.remove(hull.size() - 1);
        return hull;
    }

    private static int cross(java.awt.geom.Point2D.Float o, java.awt.geom.Point2D.Float a, java.awt.geom.Point2D.Float b) {
        long v = Math.round((a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x));
        if (v > 0) return 1;
        if (v < 0) return -1;
        return 0;
    }

    /** ポリゴンの辺を密化（ポリゴン→ブラシの近似） */
    private static List<java.awt.geom.Point2D.Float> densifyPolygon(List<java.awt.geom.Point2D.Float> pts, int perEdge) {
        List<java.awt.geom.Point2D.Float> out = new ArrayList<>();
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            java.awt.geom.Point2D.Float a = pts.get(i);
            java.awt.geom.Point2D.Float b = pts.get((i + 1) % n);
            for (int s = 0; s < perEdge; s++) {
                double t = (double) s / perEdge;
                out.add(new java.awt.geom.Point2D.Float(
                        (float) (a.x + (b.x - a.x) * t),
                        (float) (a.y + (b.y - a.y) * t)));
            }
        }
        return out;
    }

    /**
     * ウィンドウを表示
     * HOROS-20240407準拠: - (IBAction) showWindow: (id) sender
     */
    public void showWindow(Object sender) {
    }

    // HOROS-20240407準拠: 列ヘッダーのクリック処理はNSOutlineViewが自動的に処理
    // Java Swingでは、JXTreeTableが自動的にヘッダークリックを処理するため、
    // カスタムのMouseListenerは不要（HOROS-20240407準拠）
    // setupColumnHeaderListener()とhandleColumnHeaderClick()は削除

    /**
     * 列メニューを構築
     * HOROS-20240407準拠: BrowserController.m 6183-6204行目 - (void)buildColumnsMenu
     */
    private void buildColumnsMenu() {
        if (databaseOutline == null) {
            return;
        }

        // HOROS-20240407準拠: columnsMenuを初期化
        columnsMenu = new javax.swing.JPopupMenu();

        // HOROS-20240407準拠: すべての列を取得して、ヘッダー名でソート
        javax.swing.table.TableColumnModel columnModel = databaseOutline.getColumnModel();
        List<javax.swing.table.TableColumn> columns = new ArrayList<>();
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            columns.add(columnModel.getColumn(i));
        }

        // HOROS-20240407準拠: ヘッダー名でソート
        columns.sort((a, b) -> {
            String nameA = a.getHeaderValue() != null ? a.getHeaderValue().toString() : "";
            String nameB = b.getHeaderValue() != null ? b.getHeaderValue().toString() : "";
            return nameA.compareTo(nameB);
        });

        // HOROS-20240407準拠: 各列に対してメニューアイテムを追加
        for (javax.swing.table.TableColumn column : columns) {
            Object identifier = column.getIdentifier();
            String headerValue = column.getHeaderValue() != null ? column.getHeaderValue().toString() : "";

            if (identifier != null && !headerValue.isEmpty()) {
                javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(headerValue);
                item.putClientProperty("columnIdentifier", identifier.toString());
                // HOROS-20240407準拠: メニューアイテムを有効化
                item.setEnabled(true);
                // HOROS-20240407準拠: メニューアイテムがクリックされたときにcolumnsMenuActionを呼ぶ
                item.addActionListener(e -> {
                    // HOROS-20240407準拠: ActionListenerが呼ばれた時点で既にチェック状態が変更されている
                    columnsMenuAction(item);
                });
                columnsMenu.add(item);
            }
        }

        // HOROS-20240407準拠: ヘッダーに右クリックリスナーを追加
        // 【重要】列の移動とリサイズを妨害しないよう、右クリックのみを処理する
        // 左クリックの標準動作（列の移動、リサイズ、ソート）を妨害しない
        // 【修正】既存のリスナーを削除しない（JTableHeaderの標準リスナーを保持する）
        javax.swing.table.JTableHeader header = databaseOutline.getTableHeader();
        if (header != null) {
            // 【重要】既存のMouseListenerを削除しない
            // JTableHeaderの標準リスナー（列の移動、リサイズ、ソート）を保持する
            // 右クリックのみを処理するリスナーを追加（左クリックの標準動作を妨害しない）
            header.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    // 右クリックのみを処理（左クリックの標準動作を妨害しない）
                    if (e.isPopupTrigger() || e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                        showColumnsMenu(e);
                        // 右クリックのイベントを消費しない（標準動作を妨害しない）
                        // e.consume()は呼ばない
                    }
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    // 右クリックのみを処理（左クリックの標準動作を妨害しない）
                    if (e.isPopupTrigger() || e.getButton() == java.awt.event.MouseEvent.BUTTON3) {
                        showColumnsMenu(e);
                        // 右クリックのイベントを消費しない（標準動作を妨害しない）
                        // e.consume()は呼ばない
                    }
                }
            });
        }
    }

    /**
     * 列メニューを表示
     * HOROS-20240407準拠: BrowserController.m 13985-13990行目 - menuWillOpen:
     */
    private void showColumnsMenu(java.awt.event.MouseEvent e) {
        if (columnsMenu == null || databaseOutline == null) {
            return;
        }

        // HOROS-20240407準拠: columnsMenuWillOpen相当の処理
        columnsMenuWillOpen();

        // HOROS-20240407準拠: メニューを表示
        columnsMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * 列メニューが開かれる前に呼ばれる
     * HOROS-20240407準拠: BrowserController.m 6206-6226行目 - (void)columnsMenuWillOpen
     */
    private void columnsMenuWillOpen() {
        if (columnsMenu == null || databaseOutline == null) {
            return;
        }

        // HOROS-20240407準拠: 各メニューアイテムのチェック状態を更新
        for (int i = 0; i < columnsMenu.getComponentCount(); i++) {
            java.awt.Component comp = columnsMenu.getComponent(i);
            if (comp instanceof javax.swing.JCheckBoxMenuItem) {
                javax.swing.JCheckBoxMenuItem item = (javax.swing.JCheckBoxMenuItem) comp;
                Object identifierObj = item.getClientProperty("columnIdentifier");
                if (identifierObj instanceof String) {
                    String identifier = (String) identifierObj;

                    // HOROS-20240407準拠: name列の場合はHIDEPATIENTNAME設定を確認
                    if ("name".equals(identifier)) {
                        item.setSelected(true);
                    } else {
                        // HOROS-20240407準拠: その他の列はisColumnWithIdentifierVisibleで確認
                        boolean visible = databaseOutline.isColumnWithIdentifierVisible(identifier);
                        item.setSelected(visible);
                    }
                }
            }
        }
    }

    /**
     * 列メニューアイテムがクリックされたとき
     * HOROS-20240407準拠: BrowserController.m 6228-6250行目 -
     * (void)columnsMenuAction:(id)sender
     * HOROS-20240407準拠: [sender setState: ![sender state]] - チェック状態を切り替え
     * Java Swingでは、ActionListenerが呼ばれた時点で既にチェック状態が変更されているため、
     * 現在の状態（既に変更された後）をそのまま使用する
     */
    private void columnsMenuAction(javax.swing.JCheckBoxMenuItem sender) {
        if (databaseOutline == null) {
            return;
        }

        // HOROS-20240407準拠: チェック状態を切り替え
        // Java Swingでは、ActionListenerが呼ばれた時点で既にチェック状態が変更されているため、
        // 現在の状態（既に変更された後）をそのまま使用する
        // boolean newState = sender.isSelected();

        Object identifierObj = sender.getClientProperty("columnIdentifier");
        if (!(identifierObj instanceof String)) {
            return;
        }

        String identifier = (String) identifierObj;

        // HOROS-20240407準拠: name列の場合はHIDEPATIENTNAMEを設定
        if ("name".equals(identifier)) {
            // Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);
            // prefs.putBoolean("HIDEPATIENTNAME", !newState);
        } else {
            // HOROS-20240407準拠: その他の列はCOLUMNSDATABASEに保存してrefreshColumnsを呼ぶ
            saveColumnsDatabaseState();
            refreshColumns();
        }
    }

    /**
     * 列の表示/非表示状態を保存
     * HOROS-20240407準拠: BrowserController.m 6228-6246行目 - columnsMenuAction内の保存処理
     */
    private void saveColumnsDatabaseState() {
        if (columnsMenu == null || databaseOutline == null) {
            return;
        }

        try {
            // HOROS-20240407準拠: COLUMNSDATABASEに保存
            Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);
            java.util.Map<String, Integer> columnsDatabase = new java.util.HashMap<>();

            for (int i = 0; i < columnsMenu.getComponentCount(); i++) {
                java.awt.Component comp = columnsMenu.getComponent(i);
                if (comp instanceof javax.swing.JCheckBoxMenuItem) {
                    javax.swing.JCheckBoxMenuItem item = (javax.swing.JCheckBoxMenuItem) comp;
                    String title = item.getText();
                    if (title != null && !title.isEmpty()) {
                        // HOROS-20240407準拠: チェック状態を保存（1=表示、0=非表示）
                        columnsDatabase.put(title, item.isSelected() ? 1 : 0);
                    }
                }
            }

            // HOROS-20240407準拠: Mapを文字列にシリアライズ（簡易実装）
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> entry : columnsDatabase.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(entry.getKey()).append(":").append(entry.getValue());
            }
            prefs.put("COLUMNSDATABASE", sb.toString());
            prefs.flush();
        } catch (Exception e) {
            // エラーが発生した場合は保存をスキップ
        }
    }

    /**
     * 列の表示/非表示を更新
     * HOROS-20240407準拠: BrowserController.m 6252-6294行目 - (void)refreshColumns
     */
    private void refreshColumns() {
        if (databaseOutline == null) {
            return;
        }

        try {
            // HOROS-20240407準拠: COLUMNSDATABASEから設定を読み込む
            Preferences prefs = Preferences.userNodeForPackage(BrowserController.class);
            String columnsDatabaseStr = prefs.get("COLUMNSDATABASE", null);

            if (columnsDatabaseStr == null || columnsDatabaseStr.isEmpty()) {
                return;
            }

            // HOROS-20240407準拠: 文字列をMapにパース（簡易実装）
            java.util.Map<String, Integer> columnsDatabase = new java.util.HashMap<>();
            String[] parts = columnsDatabaseStr.split(",");
            for (String part : parts) {
                String[] keyValue = part.split(":", 2);
                if (keyValue.length == 2) {
                    try {
                        columnsDatabase.put(keyValue[0], Integer.parseInt(keyValue[1]));
                    } catch (NumberFormatException e) {
                        // 無効な数値はスキップ
                    }
                }
            }

            // HOROS-20240407準拠: 各列の表示/非表示を設定
            javax.swing.table.TableColumnModel columnModel = databaseOutline.getColumnModel();
            for (int i = 0; i < columnModel.getColumnCount(); i++) {
                javax.swing.table.TableColumn column = columnModel.getColumn(i);
                String headerValue = column.getHeaderValue() != null ? column.getHeaderValue().toString() : "";
                Object identifier = column.getIdentifier();

                if (headerValue != null && !headerValue.isEmpty() && identifier != null) {
                    Integer state = columnsDatabase.get(headerValue);
                    if (state != null) {
                        boolean visible = state.intValue() != 0;

                        // HOROS-20240407準拠: name列は常に表示（非表示にできない）
                        if ("name".equals(identifier.toString())) {
                            // name列は常に表示
                            continue;
                        }

                        // HOROS-20240407準拠: 列の表示/非表示を設定
                        boolean currentVisible = databaseOutline.isColumnWithIdentifierVisible(identifier.toString());
                        if (currentVisible != visible) {
                            databaseOutline.setColumnWithIdentifierVisible(identifier.toString(), visible);

                            // HOROS-20240407準拠: 選択されている列が非表示になる場合は最初の列を選択
                            if (!visible && databaseOutline.getSelectedColumn() == i) {
                                if (databaseOutline.getRowCount() > 0) {
                                    databaseOutline.setRowSelectionInterval(0, 0);
                                }
                            }
                        }
                    }
                }
            }

            // HOROS-20240407準拠: 列メニューを再構築
            buildColumnsMenu();
        } catch (Exception e) {
            // エラーが発生した場合はスキップ
        }
    }

    /** Tile all 3D viewer windows */
    public static void tile3DWindows() {
        java.util.List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        int count = viewers.size();
        if (count == 0)
            return;
        // Compute layout: prefer square-ish grid
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

    /** Close all displayed viewers */
    private void closeAllDisplayedViewers() {
        java.util.List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        for (ViewerController vc : viewers)
            vc.dispose();
    }

    /** Resize image zoom level (HOROS: DCMView.m resizeWindow: tag 0..5 → resizeWindowToScale:)
     *  0: 25% / 1: 50% / 2: 100% / 3: 200% / 4: 300% / 5: iPod Video (320px基準) */
    private void resizeWindowToScale(int tag) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null)
            return;
        float resizeScale;
        switch (tag) {
            case 0:
                resizeScale = 0.25f;
                break;
            case 1:
                resizeScale = 0.5f;
                break;
            case 2:
                resizeScale = 1.0f;
                break;
            case 3:
                resizeScale = 2.0f;
                break;
            case 4:
                resizeScale = 3.0f;
                break;
            case 5: { // iPod Video: 320pxを基準に画像をフィット
                DicomPix pix = view.getCurDCM();
                if (pix == null)
                    return;
                int w = pix.getWidth();
                int h = pix.getHeight();
                if (w <= 0 || h <= 0)
                    return;
                float widthRatio = 320.0f / w;
                float heightRatio = 320.0f / h;
                resizeScale = Math.min(widthRatio, heightRatio);
                break;
            }
            default:
                return;
        }
        view.setScaleValue(resizeScale);
        view.repaint();
    }

    /** Bring all viewer windows to front (HOROS: arrangeInFront:) */
    private void bringAllToFront() {
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (w.isVisible() && w != this)
                w.toFront();
        }
    }

    /** Export to DICOM Network Node (PACS) */
    private void exportToPACS() {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null || view.curDCM == null) {
            infoFmt(LanguageManager.get("pacs.export.no_view", "No active viewer to export."),
                    LanguageManager.get("net_title", "Network"));
            return;
        }

        List<com.oculus.jj.model.DicomNode> nodes = com.oculus.jj.model.DicomNodeManager.getInstance().getNodes();
        if (nodes.isEmpty()) {
            infoFmt(LanguageManager.get("pacs.export.no_nodes", "No DICOM destination nodes are configured."),
                    LanguageManager.get("net_title", "Network"));
            return;
        }
        Object destination = JOptionPane.showInputDialog(this,
                LanguageManager.get("pacs.export.select_node", "Select a destination node:"),
                LanguageManager.get("pacs.export.title", "Export to DICOM Network Node"),
                JOptionPane.QUESTION_MESSAGE, null, nodes.toArray(), nodes.get(0));
        if (!(destination instanceof com.oculus.jj.model.DicomNode)) return;
        com.oculus.jj.model.DicomNode node = (com.oculus.jj.model.DicomNode) destination;

        java.util.LinkedHashMap<String, java.io.File> filesByPath = new java.util.LinkedHashMap<>();
        List<DicomPix> pixList = view.getDcmPixList();
        if (pixList != null) {
            for (DicomPix pix : pixList) {
                if (pix == null) continue;
                String path = pix.getSrcFile();
                if ((path == null || path.isEmpty()) && pix.getImageObj() != null) {
                    path = pix.getImageObj().completePath();
                }
                if (path == null || path.isEmpty()) continue;
                java.io.File file = new java.io.File(path);
                if (file.isFile()) filesByPath.putIfAbsent(file.getAbsolutePath(), file);
            }
        }
        if (filesByPath.isEmpty()) {
            infoFmt(LanguageManager.get("pacs.export.no_files", "No local DICOM files are available in this viewer."),
                    LanguageManager.get("pacs.export.title", "Export to DICOM Network Node"));
            return;
        }
        List<java.io.File> files = new ArrayList<>(filesByPath.values());
        int answer = JOptionPane.showConfirmDialog(this,
                String.format(LanguageManager.get("pacs.export.confirm", "Send %d DICOM file(s) to %s?"),
                        files.size(), node.getName()),
                LanguageManager.get("pacs.export.title", "Export to DICOM Network Node"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;

        javax.swing.ProgressMonitor progress = new javax.swing.ProgressMonitor(this,
                LanguageManager.get("pacs.export.progress", "Sending DICOM files..."), "", 0, files.size());
        progress.setMillisToDecideToPopup(0);
        new javax.swing.SwingWorker<Integer, Integer>() {
            private final List<String> failures = new ArrayList<>();

            @Override
            protected Integer doInBackground() {
                int sent = 0;
                for (int i = 0; i < files.size() && !progress.isCanceled(); i++) {
                    java.io.File file = files.get(i);
                    publish(i);
                    try {
                        com.oculus.jj.model.DicomService.getInstance().store(node, file);
                        sent++;
                    } catch (Exception ex) {
                        failures.add(file.getName() + ": " + ex.getMessage());
                    }
                }
                return sent;
            }

            @Override
            protected void process(List<Integer> indexes) {
                if (indexes.isEmpty()) return;
                int index = indexes.get(indexes.size() - 1);
                progress.setNote(files.get(index).getName());
                progress.setProgress(index + 1);
            }

            @Override
            protected void done() {
                progress.close();
                try {
                    String message = String.format(LanguageManager.get("pacs.export.done", "Sent %d of %d DICOM file(s)."),
                            get(), files.size());
                    if (!failures.isEmpty()) message += "\n" + String.join("\n", failures);
                    infoFmt(message, LanguageManager.get("pacs.export.title", "Export to DICOM Network Node"));
                } catch (Exception ex) {
                    infoFmt(String.valueOf(ex.getMessage()), LanguageManager.get("pacs.export.fail", "DICOM send failed"));
                }
            }
        }.execute();
    }

    /** Export to Movie dialog */
    /** HOROS-20240407準拠: movie export dialog. BrowserToolbar calls this */
    void showMovieExport() {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null) {
            JOptionPane.showMessageDialog(BrowserController.this, "No active viewer to export as movie", "Movie Export",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        MoviePlayer.createAndShow(BrowserController.this, view);
    }

    /** Export to JPEG dialog */
    private void exportToJPEG() {
        exportImageBatch("JPEG", new String[] { "jpg", "jpeg" },
                LanguageManager.get("export.title.jpeg", "Export JPEG"));
    }

    /** 画像エクスポート共通処理（JPEG/TIFF）。複数フレーム時はフォルダ一括出力に対応 */
    private void exportImageBatch(String format, String[] exts, String title) {
        DCMView view = getActiveDCMViewForEdit();
        if (view == null || view.curDCM == null) {
            JOptionPane.showMessageDialog(BrowserController.this,
                    LanguageManager.get("export.no.view", "No active viewer to export"),
                    title, JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.util.List<DicomPix> list = (view.dcmPixList != null && !view.dcmPixList.isEmpty())
                ? view.dcmPixList
                : java.util.Collections.singletonList(view.curDCM);
        String ext = exts[0];

        if (list.size() > 1) {
            int choice = JOptionPane.showConfirmDialog(BrowserController.this,
                    String.format(LanguageManager.get("export.all.frames",
                            "This series has %d frames. Export all frames to a folder?"), list.size()),
                    title, JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                exportImageBatchAll(list, format, ext, title);
                return;
            }
        }

        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(format + " images", exts));
        fc.setApproveButtonText("Export");
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + ext)) {
            file = new java.io.File(file.getAbsolutePath() + "." + ext);
        }
        exportImageSingle(view.curDCM, file, format, title);
    }

    private void exportImageSingle(DicomPix pix, java.io.File file, String format, String title) {
        try {
            java.awt.image.BufferedImage img = pix.getBufferedImage();
            if (img == null) {
                JOptionPane.showMessageDialog(BrowserController.this,
                        LanguageManager.get("export.no.image", "No image data available"),
                        title, JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (img.getType() == java.awt.image.BufferedImage.TYPE_BYTE_GRAY && format.equals("JPEG")) {
                // JPEGはグレースケール非対応のためRGBに変換
                java.awt.image.BufferedImage rgb = new java.awt.image.BufferedImage(img.getWidth(), img.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_RGB);
                rgb.getGraphics().drawImage(img, 0, 0, null);
                img = rgb;
            }
            javax.imageio.ImageIO.write(img, format, file);
            JOptionPane.showMessageDialog(BrowserController.this,
                    LanguageManager.get("export.saved", "Exported to:") + "\n" + file.getAbsolutePath(),
                    title, JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(BrowserController.this,
                    LanguageManager.get("export.failed", "Export failed:") + " " + ex.getMessage(),
                    title, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportImageBatchAll(java.util.List<DicomPix> list, String format, String ext, String title) {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        fc.setApproveButtonText("Export");
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File dir = fc.getSelectedFile();
        String base = (list.get(0).getSeriesDescription() != null
                ? list.get(0).getSeriesDescription()
                : "series").replaceAll("[^A-Za-z0-9._-]", "_");
        int ok = 0;
        for (int i = 0; i < list.size(); i++) {
            java.io.File out = new java.io.File(dir, String.format("%s_%03d.%s", base, i + 1, ext));
            exportImageSingle(list.get(i), out, format, title + " (batch)");
            ok++;
        }
        JOptionPane.showMessageDialog(BrowserController.this,
                String.format(LanguageManager.get("export.frames.exported", "Exported %d image(s) to:"))
                        + "\n" + dir.getAbsolutePath(),
                title, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Export Raw Data dialog */
    private void showExportRawDialog() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Export Raw DICOM");
        if (fc.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File file = fc.getSelectedFile();
            DCMView view = getActiveDCMViewForEdit();
            exportToDICOMFileInternal(file, (view != null) ? view.curDCM : null);
        }
    }

    /** Export to TIFF dialog */
    private void exportToTIFF() {
        exportImageBatch("TIFF", new String[] { "tif", "tiff" },
                LanguageManager.get("export.title.tiff", "Export TIFF"));
    }

    /** Export to DICOM File(s) dialog */
    private void exportToDICOMFile() {
        DCMView view = getActiveDCMViewForEdit();
        boolean multi = view != null && view.dcmPixList != null && view.dcmPixList.size() > 1;
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Export DICOM");
        if (multi) {
            int choice = JOptionPane.showConfirmDialog(this,
                    String.format(LanguageManager.get("export.all.frames",
                            "This series has %d frames. Export all frames to a folder?"),
                            view.dcmPixList.size()),
                    "Export DICOM", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
            }
        }
        if (fc.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File file = fc.getSelectedFile();
            if (file.isDirectory()) {
                // フォルダ一括: 全フレームを出力
                int ok = 0;
                String base = (view.curDCM.getSeriesDescription() != null
                        ? view.curDCM.getSeriesDescription()
                        : "series").replaceAll("[^A-Za-z0-9._-]", "_");
                for (int i = 0; i < view.dcmPixList.size(); i++) {
                    DicomPix pix = view.dcmPixList.get(i);
                    java.io.File out = new java.io.File(file, String.format("%s_%03d.dcm", base, i + 1));
                    if (exportToDICOMFileInternal(out, pix, false))
                        ok++;
                }
                JOptionPane.showMessageDialog(this,
                        String.format(LanguageManager.get("export.frames.exported", "Exported %d image(s) to:"))
                                + "\n" + file.getAbsolutePath(),
                        "Export DICOM", JOptionPane.INFORMATION_MESSAGE);
            } else {
                exportToDICOMFileInternal(file, view != null ? view.curDCM : null);
            }
        }
    }

    private boolean exportToDICOMFileInternal(java.io.File outputFile, DicomPix pix) {
        return exportToDICOMFileInternal(outputFile, pix, true);
    }

    private boolean exportToDICOMFileInternal(java.io.File outputFile, DicomPix pix, boolean showMessages) {
        if (pix == null) {
            if (showMessages)
                JOptionPane.showMessageDialog(BrowserController.this, "No active viewer to export", "DICOM Export",
                        JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (pix.getPath() == null || !pix.getPath().toFile().exists()) {
            if (showMessages)
                JOptionPane.showMessageDialog(BrowserController.this, "Source file not available", "DICOM Export",
                        JOptionPane.WARNING_MESSAGE);
            return false;
        }
        try {
            java.io.File srcFile = pix.getPath().toFile();
            org.dcm4che3.io.DicomInputStream dis = new org.dcm4che3.io.DicomInputStream(srcFile);
            org.dcm4che3.data.Attributes attrs;
            try {
                dis.readFileMetaInformation();
                org.dcm4che3.data.Attributes fmi = dis.getFileMetaInformation();
                if (fmi != null) {
                    String tsuid = fmi.getString(0x00080010); // TransferSyntaxUID
                }
                attrs = dis.readDataset();
                dis.close();
            } catch (Exception e) {
                try (org.dcm4che3.io.DicomInputStream dis2 = new org.dcm4che3.io.DicomInputStream(srcFile)) {
                    attrs = dis2.readDataset();
                }
            }
            if (attrs == null) {
                if (showMessages)
                    JOptionPane.showMessageDialog(BrowserController.this, "Failed to read source DICOM", "DICOM Export",
                            JOptionPane.ERROR_MESSAGE);
                return false;
            }
            String newSopInstanceUID = java.util.UUID.randomUUID().toString();
            attrs.setString(0x00080016, org.dcm4che3.data.VR.UI, "1.2.840.10008.5.1.4.1.1.7"); // SOPClassUID
            attrs.setString(0x00080018, org.dcm4che3.data.VR.UI, newSopInstanceUID); // SOPInstanceUID
            attrs.setString(0x00080060, org.dcm4che3.data.VR.CS, "SC"); // Modality
            java.io.File outDir = outputFile.getParentFile();
            if (outDir != null && !outDir.exists())
                outDir.mkdirs();
            String fn = outputFile.getName().toLowerCase();
            if (!fn.endsWith(".dcm") && !fn.endsWith(".dicom")) {
                outputFile = new java.io.File(outputFile.getAbsolutePath() + ".dcm");
            }
            byte[] srcBytes = java.nio.file.Files.readAllBytes(srcFile.toPath());
            int fmiStart = -1;
            for (int i = 272; i >= 128 && i <= 300; i--) {
                if ((char) srcBytes[i] == 'D' && (char) srcBytes[i + 1] == 'I' && (char) srcBytes[i + 2] == 'C'
                        && (char) srcBytes[i + 3] == 'M') {
                    fmiStart = i;
                    break;
                }
            }
            byte[] pixelData = (fmiStart > 0) ? java.util.Arrays.copyOfRange(srcBytes, fmiStart + 132, srcBytes.length)
                    : srcBytes;
            java.io.File tmpFmi = java.io.File.createTempFile("fmi", "tmp");
            org.dcm4che3.io.DicomOutputStream dos = new org.dcm4che3.io.DicomOutputStream(tmpFmi);
            org.dcm4che3.data.Attributes fmo = new org.dcm4che3.data.Attributes();
            fmo.setString(0x00080016, org.dcm4che3.data.VR.UI,
                    "1.2.840.10008.5.1.4.1.1.7"); // MediaStorageSOPClassUID
            fmo.setString(0x00080018, org.dcm4che3.data.VR.UI, newSopInstanceUID); // MediaStorageSOPInstanceUID
            fmo.setString(0x00080010, org.dcm4che3.data.VR.UI, "1.2.840.10008.1.2"); // TransferSyntaxUID
            dos.writeFileMetaInformation(fmo);
            dos.close();
            byte[] fmiBytes = java.nio.file.Files.readAllBytes(tmpFmi.toPath());
            tmpFmi.delete();
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                byte[] pad = new byte[128];
                fos.write(pad);
                fos.write("DICM".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                fos.write(fmiBytes);
                fos.write(pixelData);
            }
            if (showMessages)
                JOptionPane.showMessageDialog(BrowserController.this,
                        "Exported DICOM to: " + outputFile.getAbsolutePath(),
                        "DICOM Export", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (java.io.IOException ex) {
            if (showMessages)
                JOptionPane.showMessageDialog(BrowserController.this,
                        "DICOM export failed: " + ex.getMessage(),
                        "DICOM Export", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /** DICOM Print (PACSSettings popup) */
    public void generateReport(Object sender) {
        DCMView view = getActiveDCMViewForEdit();
        DicomPix dcm = (view != null) ? view.getCurDCM() : null;
        if (dcm == null) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("report.no.image", "No active image to generate a report for."),
                    LanguageManager.get("report.title", "Report"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        String content = buildReportContent(view, dcm);

        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle(LanguageManager.get("report.save.title", "Save Report"));
        fc.setSelectedFile(new java.io.File("OculusJJ_Report.html"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                LanguageManager.get("report.filter.html", "HTML file"), "html"));
        fc.setAcceptAllFileFilterUsed(false);
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File out = fc.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".html")) {
            out = new java.io.File(out.getAbsolutePath() + ".html");
        }
        try {
            java.nio.file.Files.write(out.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // HOROS-20240407準拠: レポートはStudyに紐づく（reportURL）。
            // 以降 convertReportToPDF / convertReportToDICOMSR / deleteReport が利用可能になる。
            com.oculus.jj.model.DicomStudy study = activeStudy();
            if (study != null) {
                study.setReportURL(out.getAbsolutePath());
                DicomDatabase db = DicomDatabase.activeLocalDatabase();
                if (db != null)
                    db.save();
            }
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("report.saved", "Report saved to:") + "\n" + out.getAbsolutePath(),
                    LanguageManager.get("report.title", "Report"), JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this,
                    LanguageManager.get("report.save.failed", "Failed to save report:") + " " + ex.getMessage(),
                    LanguageManager.get("report.title", "Report"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** レポート用 HTML コンテンツを生成 */
    private String buildReportContent(DCMView view, DicomPix dcm) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\">\n");
        sb.append("<title>OculusJJ Report</title>\n");
        sb.append("<style>body{font-family:sans-serif;margin:24px;}"
                + "table{border-collapse:collapse;margin-top:12px;}"
                + "th,td{border:1px solid #ccc;padding:6px 10px;text-align:left;}"
                + "th{background:#f0f0f0;}</style>\n</head>\n<body>\n");
        sb.append("<h1>OculusJJ ").append(esc(LanguageManager.get("report.title", "Report"))).append("</h1>\n");
        sb.append("<p>").append(esc(LanguageManager.get("report.generated", "Generated:")))
                .append(" ").append(esc(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())))
                .append("</p>\n");
        sb.append("<table>\n");
        row(sb, LanguageManager.get("report.patient.id", "Patient ID"), dcm.getPatientID());
        row(sb, LanguageManager.get("report.study.desc", "Study Description"), dcm.getStudyDescription());
        row(sb, LanguageManager.get("report.series.desc", "Series Description"), dcm.getSeriesDescription());
        row(sb, LanguageManager.get("report.accession", "Accession Number"), dcm.getAccessionNumber());
        row(sb, LanguageManager.get("report.modality", "Modality"), dcm.getModality());
        row(sb, "Study UID", dcm.getStudyInstanceUID());
        row(sb, "Series UID", dcm.getSeriesInstanceUID());
        row(sb, "SOP Instance UID", dcm.getSOPInstanceUID());
        row(sb, "Transfer Syntax", dcm.getTransferSyntaxUID());
        row(sb, LanguageManager.get("report.dimensions", "Dimensions"), dcm.getWidth() + " x " + dcm.getHeight());
        row(sb, LanguageManager.get("report.frame", "Current Frame"), String.valueOf(view.getCurImage()));
        row(sb, "Source File", dcm.getSrcFile());
        sb.append("</table>\n");
        sb.append("</body></html>\n");
        return sb.toString();
    }

    private void row(StringBuilder sb, String label, String value) {
        sb.append("<tr><th>").append(esc(label)).append("</th><td>")
                .append(esc(value == null ? "" : value)).append("</td></tr>\n");
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Import image from file (via JFileChooser) */
    public void importImageFromURL() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
        if (fc.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            String url = fc.getSelectedFile().getAbsolutePath();
            importFiles(java.util.Collections.singletonList(url));
        }
    }

    /** Toggle database outline drawer visibility */
    public void toggleDatabaseOutlineVisibility() {
        if (databaseOutline != null) {
            boolean vis = !databaseOutline.isVisible();
            databaseOutline.setVisible(vis);
        }
    }

    /** Export to JPEG (Photos format) */
    public void exportToPhotos() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Export to Photos");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JPEG images", "jpg", "jpeg"));
        if (fc.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.awt.image.BufferedImage img = getActiveDCMViewForEdit() != null
                    ? getActiveDCMViewForEdit().getBufferedImageNoLoad()
                    : null;
            if (img != null) {
                try {
                    javax.imageio.ImageIO.write(img, "JPEG", fc.getSelectedFile());
                    JOptionPane.showMessageDialog(BrowserController.this, "Image exported successfully",
                            "Export to Photos", JOptionPane.INFORMATION_MESSAGE);
                } catch (java.io.IOException ex) {
                    JOptionPane.showMessageDialog(BrowserController.this, "Export failed: " + ex.getMessage(),
                            "Export to Photos", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(BrowserController.this, "No active viewer to export", "Export to Photos",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    /** HOROS-20240407準拠: ShutterToolbarItemIdentifier. Pop-up menu selection for shutter display mode. */
    public void setShutterMode(String mode) {
        // HOROS-20240407準拠: - (void)setShutter:(NSString*)mode inDCMView:(DCMView*)view
        System.out.println("[Shutter] Setting shutter mode: " + mode);
    }
}
