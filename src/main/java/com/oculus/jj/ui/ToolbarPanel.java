package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * ToolbarPanel - ツールバーウィンドウ
 * HOROS-20240407準拠: ToolbarPanel.h, ToolbarPanel.m
 *
 * 画面上部に固定表示されるツールバーウィンドウ
 */
public class ToolbarPanel extends JFrame {

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
        // 背景色をHorosのツールバーに近い色に変更し、境界線を追加して視認性を確保
        getContentPane().setBackground(new Color(240, 240, 240));
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

        // --- ツールボタンを追加 ---

        // Selector ツール
        JButton selectorButton = new JButton("Select");
        selectorButton.setToolTipText("Selector / Edit (S)");
        selectorButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tPlain;
        });
        toolbar.add(selectorButton);

        // Curved MPR ツール (HOROS-20240407)
        JButton curvedButtonTop = new JButton("Curved");
        curvedButtonTop.setToolTipText("Curved MPR ROI (Click nodes, double-click to finish)");
        curvedButtonTop.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tCurvedROI;
        });
        toolbar.add(curvedButtonTop);

        toolbar.addSeparator();

        // WLWW ツール
        JButton wlwwButton = new JButton("WL/WW");
        wlwwButton.setToolTipText("Window Level/Width (W)");
        wlwwButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tWLWW;
        });
        toolbar.add(wlwwButton);

        // パン ツール
        JButton panButton = new JButton("Pan");
        panButton.setToolTipText("Pan (P)");
        panButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tTranslate;
        });
        toolbar.add(panButton);

        // ズーム ツール
        JButton zoomButton = new JButton("Zoom");
        zoomButton.setToolTipText("Zoom (Z)");
        zoomButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tZoom;
        });
        toolbar.add(zoomButton);

        toolbar.addSeparator();

        // --- ROI ツール ---

        // 長さ計測 ツール
        JButton measureButton = new JButton("Measure");
        measureButton.setToolTipText("Length Measurement (M)");
        measureButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tMesure;
        });
        toolbar.add(measureButton);

        // 矩形 ツール
        JButton rectButton = new JButton("Rect");
        rectButton.setToolTipText("Rectangle ROI (R)");
        rectButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tROI;
        });
        toolbar.add(rectButton);

        // 楕円 ツール
        JButton ovalButton = new JButton("Oval");
        ovalButton.setToolTipText("Oval ROI (O)");
        ovalButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tOval;
        });
        toolbar.add(ovalButton);

        // 多角形 ツール (閉)
        JButton cPolyButton = new JButton("Poly");
        cPolyButton.setToolTipText("Closed Polygon ROI");
        cPolyButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tCPolygon;
        });
        toolbar.add(cPolyButton);

        // 多角形 ツール (開)
        JButton oPolyButton = new JButton("O-Poly");
        oPolyButton.setToolTipText("Open Polygon ROI");
        oPolyButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tOPolygon;
        });
        toolbar.add(oPolyButton);

        // 角度 ツール
        JButton angleButton = new JButton("Angle");
        angleButton.setToolTipText("Angle Measurement (A)");
        angleButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tAngle;
        });
        toolbar.add(angleButton);

        // 矢印 ツール
        JButton arrowButton = new JButton("Arrow");
        arrowButton.setToolTipText("Arrow Annotation");
        arrowButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tArrow;
        });
        toolbar.add(arrowButton);

        // 鉛筆 ツール
        JButton pencilButton = new JButton("Pencil");
        pencilButton.setToolTipText("Pencil / Freehand ROI");
        pencilButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tPencil;
        });
        toolbar.add(pencilButton);

        // ポイント ツール
        JButton pointButton = new JButton("Point");
        pointButton.setToolTipText("2D Point / Crosshair");
        pointButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.t2DPoint;
        });
        toolbar.add(pointButton);

        // テキスト ツール
        JButton textButton = new JButton("Text");
        textButton.setToolTipText("Text Annotation (T)");
        textButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tText;
        });
        toolbar.add(textButton);

        // Axis ツール
        JButton axisButton = new JButton("Axis");
        axisButton.setToolTipText("Axis ROI / Tumor measurement");
        axisButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tAxis;
        });
        toolbar.add(axisButton);

        // Dynamic Angle ツール
        JButton dAngleButton = new JButton("D-Angle");
        dAngleButton.setToolTipText("Dynamic Angle Measurement");
        dAngleButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tDynamicAngle;
        });
        toolbar.add(dAngleButton);

        // Perpendicular ツール
        JButton perpButton = new JButton("Perp");
        perpButton.setToolTipText("Perpendicular Lines");
        perpButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tPerpendicular;
        });
        toolbar.add(perpButton);

        // Curved MPR ツール (HOROS-20240407)
        JButton curvedButton = new JButton("Curved");
        curvedButton.setToolTipText("Curved MPR ROI (Click nodes, double-click to finish)");
        curvedButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tCurvedROI;
        });
        toolbar.add(curvedButton);

        // Brush ツール
        JButton brushButton = new JButton("Brush");
        brushButton.setToolTipText("Brush ROI / Pixel Mask");
        brushButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tBrush;
            Window owner = SwingUtilities.getWindowAncestor(this);
            BrushPaletteDialog.showPalette(owner instanceof Frame ? (Frame) owner : null);
        });
        toolbar.add(brushButton);

        // Repulsor ツール
        JButton repulsorButton = new JButton("Repulse");
        repulsorButton.setToolTipText("Repulsor / ROI Sculpting Tool (E)");
        repulsorButton.addActionListener(e -> {
            DCMView.currentTool = DCMView.ToolMode.tRepulsor;
        });
        toolbar.add(repulsorButton);

        toolbar.addSeparator();

        // --- 3D / MPR ツール ---
        // HOROS-20240407準拠: 2D MPR (3D MPR)
        JButton mprButton = new JButton("3D MPR");
        mprButton.setToolTipText("Multi-Planar Reconstruction (3D MPR)");
        mprButton.addActionListener(e -> {
            if (viewer != null) {
                MPRController.openMPR(viewer);
            }
        });
        toolbar.add(mprButton);

        // HOROS-20240407準拠: Curved MPR (3D Curved)
        JButton cprButton = new JButton("3D Curved");
        cprButton.setToolTipText("Curved Planar Reconstruction (3D Curved)");
        cprButton.addActionListener(e -> {
            BrowserController current = BrowserController.currentBrowser();
            if (current != null) {
                current.cprViewer(null);
            }
        });
        toolbar.add(cprButton);

        // HOROS-20240407準拠: 3D MIP
        JButton mipButton = new JButton("3D MIP");
        mipButton.setToolTipText("Maximum Intensity Projection (3D MIP)");
        mipButton.addActionListener(e -> {
            if (viewer != null) {
                Viewer3DController.openMIP(viewer);
            }
        });
        toolbar.add(mipButton);

        // HOROS-20240407準拠: 3D VR (Volume Rendering)
        JButton vrButton = new JButton("3D VR");
        vrButton.setToolTipText("3D Volume Rendering");
        vrButton.addActionListener(e -> {
            if (viewer != null) {
                Viewer3DController.openVR(viewer);
            }
        });
        toolbar.add(vrButton);

        // ツールバーを中央（メニューの下）に配置
        add(toolbar, BorderLayout.CENTER);

        // 背景色を設定
        getContentPane().setBackground(new Color(240, 240, 240));
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
