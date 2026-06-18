package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import com.oculus.jj.utils.UIStyle;
import java.awt.event.*;
import com.oculus.jj.utils.LanguageManager;

/**
 * ToolbarPanel - ツールバーウィンドウ
 * HOROS-20240407準拠: ToolbarPanel.h, ToolbarPanel.m
 *
 * 画面上部に固定表示されるツールバーウィンドウ
 */
public class ToolbarPanel extends JFrame { // i18n_ready

    // HOROS-20240407準拠: ToolbarPanel.m 50行目
    // メニューバーを統合しつつ、他ウィンドウを圧迫しないよう高さを詰める (140 -> 100)
    private static final int FIXED_HEIGHT = 100;

    // HOROS-20240407準拠: ToolbarPanel.m 61-62行目
    private static final int HIDDEN_HEIGHT = 15;

    // HOROS-20240407準拠: ToolbarPanel.h 45行目
    private ViewerController viewer;

    // HOROS-20240407準拠: ToolbarPanel.h 44行目
    private JToolBar toolbar;

    /**
     * コンストラクタ
     * HOROS-20240407準拠: ToolbarPanel.m 101行目 - initForViewer:withToolbar:
     */
    public ToolbarPanel(ViewerController viewer) {
        super("Toolbar");

        this.viewer = viewer;

        // HOROS-20240407準拠: ToolbarPanel.m 108行目
        // [[self window] setAnimationBehavior: NSWindowAnimationBehaviorNone];
        // Javaでは特に設定不要

        // HOROS-20240407準拠: ToolbarPanel.m 110行目
        // [[self window] setLevel: NSNormalWindowLevel];
        setAlwaysOnTop(false);

        // UI初期化
        initializeUI();

        // HOROS-20240407準拠: ToolbarPanel.m 116行目
        // [self applicationDidChangeScreenParameters: nil];
        applicationDidChangeScreenParameters();

        // HOROS-20240407準拠: ToolbarPanel.m 122-123行目
        // [[AppController hasMacOSXSnowLeopard])
        // [[self window] setCollectionBehavior: 1 << 6];
        // //NSWindowCollectionBehaviorIgnoresCycle
        setFocusableWindowState(false);

        // HOROS-20240407準拠: ToolbarPanel.m 128行目
        // [self.window safelySetMovable:NO];
        setResizable(false);

        // HOROS-20240407準拠: ToolbarPanel.m 129行目
        // [self.window setShowsToolbarButton:NO];
        // Javaでは特に設定不要

        // HOROS-20240407準拠: ToolbarPanel.m 125-126行目
        // ウィンドウがキーまたはメインになったときの処理
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                windowDidBecomeMain();
            }
        });
    }

    /**
     * UIを初期化
     */
    private void initializeUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(UIStyle.BACKGROUND_COLOR);
        setUndecorated(true);
        setLayout(new BorderLayout());

        // HOROS-20240407準拠: ツールバーウィンドウのメニューバー
        // メニューバー本体の色も調整
        BrowserController browser = BrowserController.currentBrowser();
        if (browser != null && browser.getJMenuBar() != null) {
            JMenuBar menuBar = ViewerController.replicateMenuBar(browser.getJMenuBar());
            // Windowsでメニュー項目が見えにくい問題を修正するため、不透明度と色を設定
            menuBar.setOpaque(true);
            menuBar.setBackground(new Color(240, 240, 240));
            menuBar.setForeground(Color.BLACK);
            // 下側に境界線を入れてメニューの終わりを明確にする
            menuBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                    BorderFactory.createEmptyBorder(2, 0, 2, 0)));

            // 重要: WindowsではsetJMenuBarを使用するとウィンドウタイトルバーがない場合に
            // 配置が不安定になることがあるため、直接ContainerのNORTHに配置を試みる
            JPanel menuWrapper = new JPanel(new BorderLayout());
            menuWrapper.add(menuBar, BorderLayout.CENTER);
            add(menuWrapper, BorderLayout.NORTH);
        }

        // ツールバーを作成
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(240, 240, 240));
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        // --- ナビゲーショングループ ---
        JButton selectorButton = new JButton(LanguageManager.get("btn_select"));
        selectorButton.setFont(UIStyle.MAIN_FONT);
        selectorButton.setToolTipText(LanguageManager.get("btn_select") + " / Edit (S)");
        selectorButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tPlain;
        });
        toolbar.add(selectorButton);

        JButton curvedButtonTop = new JButton(LanguageManager.get("btn_curved"));
        curvedButtonTop.setFont(UIStyle.MAIN_FONT);
        curvedButtonTop
                .setToolTipText(LanguageManager.get("btn_curved") + " ROI (Click nodes, double-click to finish)");
        curvedButtonTop.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tCurvedROI;
        });
        toolbar.add(curvedButtonTop);

        toolbar.addSeparator();

        JButton wlwwButton = new JButton(LanguageManager.get("btn_wlww"));
        wlwwButton.setFont(UIStyle.MAIN_FONT);
        wlwwButton.setToolTipText(LanguageManager.get("btn_wlww") + " (W)");
        wlwwButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tWLWW;
        });
        toolbar.add(wlwwButton);

        JButton panButton = new JButton(LanguageManager.get("btn_pan"));
        panButton.setFont(UIStyle.MAIN_FONT);
        panButton.setToolTipText(LanguageManager.get("btn_pan") + " (P)");
        panButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tTranslate;
        });
        toolbar.add(panButton);

        JButton zoomButton = new JButton(LanguageManager.get("btn_zoom"));
        zoomButton.setFont(UIStyle.MAIN_FONT);
        zoomButton.setToolTipText(LanguageManager.get("btn_zoom") + " (Z)");
        zoomButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tZoom;
        });
        toolbar.add(zoomButton);

        toolbar.addSeparator();

        // --- ROI ツール ---

        // 長さ計測 ツール
        JButton measureButton = new JButton(LanguageManager.get("tools_roi.length"));
        measureButton.setFont(UIStyle.MAIN_FONT);
        measureButton.setToolTipText(LanguageManager.get("tools_roi.length") + " (M)");
        measureButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tMesure;
        });
        toolbar.add(measureButton);

        // 矩形 ツール
        JButton rectButton = new JButton(LanguageManager.get("tools_roi.rect"));
        rectButton.setFont(UIStyle.MAIN_FONT);
        // Wait, I missed some keys in my last update of properties file. Let me fix
        // that first.

        // 楕円 ツール
        JButton ovalButton = new JButton(LanguageManager.get("tools_roi.oval"));
        ovalButton.setFont(UIStyle.MAIN_FONT);
        ovalButton.setToolTipText(LanguageManager.get("tools_roi.oval") + " (O)");
        ovalButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tOval;
        });
        toolbar.add(ovalButton);

        toolbar.addSeparator();

        // 多角形 ツール (閉)
        JButton cPolyButton = new JButton(LanguageManager.get("tools_roi.poly_closed"));
        cPolyButton.setFont(UIStyle.MAIN_FONT);
        cPolyButton.setToolTipText(LanguageManager.get("tools_roi.poly_closed"));
        cPolyButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tCPolygon;
        });
        toolbar.add(cPolyButton);

        JButton oPolyButton = new JButton(LanguageManager.get("tools_roi.poly_open"));
        oPolyButton.setFont(UIStyle.MAIN_FONT);
        oPolyButton.setToolTipText(LanguageManager.get("tools_roi.poly_open"));
        oPolyButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tOPolygon;
        });
        toolbar.add(oPolyButton);

        // 角度 ツール
        JButton angleButton = new JButton(LanguageManager.get("tools_roi.angle"));
        angleButton.setFont(UIStyle.MAIN_FONT);
        angleButton.setToolTipText(LanguageManager.get("tools_roi.angle") + " (A)");
        angleButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tAngle;
        });
        toolbar.add(angleButton);

        // 矢印 ツール
        JButton arrowButton = new JButton(LanguageManager.get("annotation_arrow"));
        arrowButton.setFont(UIStyle.MAIN_FONT);
        arrowButton.setToolTipText(LanguageManager.get("annotation_arrow"));
        arrowButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tArrow;
        });
        toolbar.add(arrowButton);

        // 鉛筆 ツール
        JButton pencilButton = new JButton(LanguageManager.get("annotation_pencil"));
        pencilButton.setFont(UIStyle.MAIN_FONT);
        pencilButton.setToolTipText(LanguageManager.get("annotation_pencil"));
        pencilButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tPencil;
        });
        toolbar.add(pencilButton);

        toolbar.addSeparator();

        // ポイント ツール
        JButton pointButton = new JButton(LanguageManager.get("btn_point"));
        pointButton.setToolTipText(LanguageManager.get("btn_point"));
        pointButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.t2DPoint;
        });
        toolbar.add(pointButton);

        // テキスト ツール
        JButton textButton = new JButton(LanguageManager.get("annotation_text"));
        textButton.setToolTipText(LanguageManager.get("annotation_text"));
        textButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tText;
        });
        toolbar.add(textButton);

        // Axis ツール
        JButton axisButton = new JButton(LanguageManager.get("tools_roi.axis"));
        axisButton.setToolTipText(LanguageManager.get("tools_roi.axis"));
        axisButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tAxis;
        });
        toolbar.add(axisButton);

        // Dynamic Angle ツール
        JButton dAngleButton = new JButton(LanguageManager.get("tools_roi.dangle"));
        dAngleButton.setToolTipText(LanguageManager.get("tools_roi.dangle"));
        dAngleButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tDynamicAngle;
        });
        toolbar.add(dAngleButton);

        // Perpendicular ツール
        JButton perpButton = new JButton(LanguageManager.get("tools_roi.perp"));
        perpButton.setToolTipText(LanguageManager.get("tools_roi.perp"));
        perpButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tPerpendicular;
        });
        toolbar.add(perpButton);

        // --- Curved MPR ツール (HOROS-20240407)
        JButton cprButton = new JButton(LanguageManager.get("tools_roi.curved_3d"));
        cprButton.setToolTipText(LanguageManager.get("tt_curved_3d"));
        cprButton.addActionListener(e -> {
            BrowserController current = BrowserController.currentBrowser();
            if (current != null) {
                current.cprViewer(null);
            }
        });
        toolbar.add(cprButton);

        // Brush ツール
        JButton brushButton = new JButton(LanguageManager.get("tools_roi.brush"));
        brushButton.setToolTipText("Brush ROI / Pixel Mask");
        brushButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tBrush;
            Window owner = SwingUtilities.getWindowAncestor(this);
            BrushPaletteDialog.showPalette(owner instanceof Frame ? (Frame) owner : null);
        });
        toolbar.add(brushButton);

        // Repulsor ツール
        JButton repulsorButton = new JButton(LanguageManager.get("btn_repulse"));
        repulsorButton.setToolTipText(LanguageManager.get("tt_repulse"));
        repulsorButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tRepulsor;
        });
        toolbar.add(repulsorButton);

        toolbar.addSeparator();

        // --- WLWW プリセット (HOROS-20240407準拠) ---
        String[] presets = { "Standard", "Bone", "Lung", "Brain", "Soft Tissue" };
        JComboBox<String> presetCombo = new JComboBox<>(presets);
        presetCombo.setMaximumSize(new Dimension(100, 25));
        presetCombo.addActionListener(e -> {
            String selected = (String) presetCombo.getSelectedItem();
            DCMView current = viewer != null ? viewer.getImageView() : null;
            if (current != null) {
                current.applyWLPreset(selected);
            }
        });
        toolbar.add(new JLabel(" WL: "));
        toolbar.add(presetCombo);

        // --- 同期トグル ---
        JCheckBox syncCheck = new JCheckBox("Sync", DCMView.syncro != DCMView.syncroOFF);
        syncCheck.setOpaque(false);
        syncCheck.setForeground(Color.WHITE);
        syncCheck.addActionListener(e -> {
            DCMView.syncro = syncCheck.isSelected() ? DCMView.syncroABS : DCMView.syncroOFF;
        });
        toolbar.add(syncCheck);

        toolbar.addSeparator();

        // --- 画面編集ツール (Flip/Rotate) ---
        // HOROS-20240407準拠: 2D MPR (3D MPR)
        JButton mprButton = new JButton(LanguageManager.get("tools_roi.mpr"));
        mprButton.setToolTipText(LanguageManager.get("tt_mpr"));
        mprButton.addActionListener(e -> {
            if (viewer != null) {
                MPRController.openMPR(viewer);
            }
        });
        toolbar.add(mprButton);

        // HOROS-20240407準拠: 3D MIP
        JButton mipButton = new JButton(LanguageManager.get("advanced.mip3d"));
        mipButton.setToolTipText(LanguageManager.get("tt_mip3d"));
        mipButton.addActionListener(e -> {
            if (viewer != null) {
                Viewer3DController.openMIP(viewer);
            }
        });
        toolbar.add(mipButton);

        // HOROS-20240407準拠: 3D VR (Volume Rendering)
        JButton vrButton = new JButton(LanguageManager.get("tools_roi.vr"));
        vrButton.setToolTipText(LanguageManager.get("tt_vr3d"));
        vrButton.addActionListener(e -> {
            if (viewer != null) {
                Viewer3DController.openVR(viewer);
            }
        });
        toolbar.add(vrButton);

        toolbar.addSeparator();

        // --- 画像編集ツール (Flip/Rotate) ---
        JButton flipXButton = new JButton("Flip H");
        flipXButton.setToolTipText("Flip Horizontal");
        flipXButton.addActionListener(e -> {
            DCMView current = viewer != null ? viewer.getImageView() : null;
            if (current != null)
                current.toggleXFlipped();
        });
        toolbar.add(flipXButton);

        JButton flipYButton = new JButton("Flip V");
        flipYButton.setToolTipText("Flip Vertical");
        flipYButton.addActionListener(e -> {
            DCMView current = viewer != null ? viewer.getImageView() : null;
            if (current != null)
                current.toggleYFlipped();
        });
        toolbar.add(flipYButton);

        JButton rotRButton = new JButton("Rot 90 R");
        rotRButton.setToolTipText("Rotate 90 Right");
        rotRButton.addActionListener(e -> {
            DCMView current = viewer != null ? viewer.getImageView() : null;
            if (current != null)
                current.rotateRight();
        });
        toolbar.add(rotRButton);

        JButton rotLButton = new JButton("Rot 90 L");
        rotLButton.setToolTipText("Rotate 90 Left");
        rotLButton.addActionListener(e -> {
            DCMView current = viewer != null ? viewer.getImageView() : null;
            if (current != null)
                current.rotateLeft();
        });
        toolbar.add(rotLButton);

        // ツールバーを中央（メニューの下）に配置
        add(toolbar, BorderLayout.CENTER);

        getContentPane().setBackground(UIStyle.BACKGROUND_COLOR);
    }

    /**
     * 固定高さを取得
     * HOROS-20240407準拠: ToolbarPanel.m 56-59行目 - fixedHeight
     */
    public int getFixedHeight() {
        return FIXED_HEIGHT;
    }

    /**
     * 隠れている高さを取得
     * HOROS-20240407準拠: ToolbarPanel.m 61-63行目 - hiddenHeight
     */
    public static int getHiddenHeight() {
        return HIDDEN_HEIGHT;
    }

    /**
     * 表示されている高さを取得
     * HOROS-20240407準拠: ToolbarPanel.m 65-67行目 - exposedHeight
     */
    public int getExposedHeight() {
        // Windows環境ではHIDDEN_HEIGHT（画面外への隠し領域）を使用しないため
        // FIXED_HEIGHTをそのまま返す
        return FIXED_HEIGHT;
    }

    /**
     * 表示されている高さを取得（静的メソッド）
     * HOROS-20240407準拠: ToolbarPanel.m 69-71行目 - exposedHeight
     */
    public static int exposedHeight() {
        return FIXED_HEIGHT;
    }

    /**
     * 画面パラメータ変更時の処理
     * HOROS-20240407準拠: ToolbarPanel.m 87-99行目 -
     * applicationDidChangeScreenParameters:
     */
    public void applicationDidChangeScreenParameters() {
        if (viewer == null || viewer.getGraphicsConfiguration() == null) {
            return;
        }

        // HOROS-20240407準拠: ToolbarPanel.m 89行目
        // NSRect screenRect = [viewer.window.screen visibleFrame];
        GraphicsConfiguration gc = viewer.getGraphicsConfiguration();
        Rectangle screenBounds = gc.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

        // HOROS-20240407準拠: ToolbarPanel.m 91-95行目
        // dstframe.size.height = [self fixedHeight];
        // dstframe.size.width = screenRect.size.width;
        // dstframe.origin.x = screenRect.origin.x;
        // dstframe.origin.y = screenRect.origin.y + screenRect.size.height -
        // dstframe.size.height + [ToolbarPanelController hiddenHeight];
        int x = screenBounds.x + screenInsets.left;
        int width = screenBounds.width - screenInsets.left - screenInsets.right;
        int height = FIXED_HEIGHT;
        // Windows環境でのメニュー切れを修正: HIDDEN_HEIGHTによるマイナスオフセットを削除し、
        // 画面上端にぴったり配置する
        int y = screenBounds.y + screenInsets.top;

        setBounds(x, y, width, height);
    }

    /**
     * ウィンドウがメインになったときの処理
     * HOROS-20240407準拠: ToolbarPanel.m 170-185行目 - windowDidBecomeMain:
     */
    private void windowDidBecomeMain() {
        // HOROS-20240407準拠: ToolbarPanel.m 174行目
        // if( [[viewer window] isVisible])
        if (viewer != null && viewer.isVisible()) {
            // HOROS-20240407準拠: ToolbarPanel.m 178行目
            // viewerを前面にだすが、ツールバーが常に一番上にあるようにする
            viewer.toFront();
            this.toFront();
            // フォーカスはビューワーに渡す
            viewer.requestFocus();
        } else {
            // HOROS-20240407準拠: ToolbarPanel.m 183行目
            // [self.window orderOut: self];
            setVisible(false);
        }
    }

    /**
     * ViewerControllerを取得
     * HOROS-20240407準拠: ToolbarPanel.h 49行目 - @property (readonly) ViewerController
     * *viewer;
     */
    public ViewerController getViewer() {
        return viewer;
    }

    /**
     * ツールバーを取得
     * HOROS-20240407準拠: ToolbarPanel.m 187-190行目 - toolbar
     */
    public JToolBar getToolbar() {
        return toolbar;
    }
}
