package com.jj.dicomviewer.ui;

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
    private static final int FIXED_HEIGHT = 92;

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
        // [[self window] setCollectionBehavior: 1 << 6]; //NSWindowCollectionBehaviorIgnoresCycle
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
        setUndecorated(true);
        setLayout(new BorderLayout());

        // ツールバーを作成
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
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
        return FIXED_HEIGHT - HIDDEN_HEIGHT;
    }

    /**
     * 表示されている高さを取得（静的メソッド）
     * HOROS-20240407準拠: ToolbarPanel.m 69-71行目 - exposedHeight
     */
    public static int exposedHeight() {
        return FIXED_HEIGHT - HIDDEN_HEIGHT;
    }

    /**
     * 画面パラメータ変更時の処理
     * HOROS-20240407準拠: ToolbarPanel.m 87-99行目 - applicationDidChangeScreenParameters:
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
        // dstframe.origin.y = screenRect.origin.y + screenRect.size.height - dstframe.size.height + [ToolbarPanelController hiddenHeight];
        int x = screenBounds.x + screenInsets.left;
        int width = screenBounds.width - screenInsets.left - screenInsets.right;
        int height = FIXED_HEIGHT;
        // ツールバーの上部をHIDDEN_HEIGHT分画面外に配置（HOROS準拠）
        int y = screenBounds.y + screenInsets.top - HIDDEN_HEIGHT;

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
            // [[viewer window] makeKeyAndOrderFront: self];
            viewer.toFront();
            viewer.requestFocus();

            // HOROS-20240407準拠: ToolbarPanel.m 179行目
            // [self.window orderBack: self];
            toBack();
        } else {
            // HOROS-20240407準拠: ToolbarPanel.m 183行目
            // [self.window orderOut: self];
            setVisible(false);
        }
    }

    /**
     * ViewerControllerを取得
     * HOROS-20240407準拠: ToolbarPanel.h 49行目 - @property (readonly) ViewerController *viewer;
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
