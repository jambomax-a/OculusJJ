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
 * 全ボタンラベルは英語ベースでLanguageManager経由のi18n対応。
 */
public class ToolbarPanel extends JFrame { // i18n_ready

    private static final int FIXED_HEIGHT = 100;
    private static final int HIDDEN_HEIGHT = 15;
    private ViewerController viewer;
    private JToolBar toolbar;

    public ToolbarPanel(ViewerController viewer) {
        super("Toolbar");
        this.viewer = viewer;
        setAlwaysOnTop(false);
        initializeUI();
        applicationDidChangeScreenParameters();
        setFocusableWindowState(false);
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override public void windowActivated(WindowEvent e) { windowDidBecomeMain(); }
        });
    }

    private void initializeUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(UIStyle.BACKGROUND_COLOR);
        setUndecorated(true);
        setLayout(new BorderLayout());

        BrowserController browser = BrowserController.currentBrowser();
        if (browser != null && browser.getJMenuBar() != null) {
            JMenuBar menuBar = ViewerController.replicateMenuBar(browser.getJMenuBar());
            menuBar.setOpaque(true);
            menuBar.setBackground(new Color(240, 240, 240));
            menuBar.setForeground(Color.BLACK);
            menuBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                    BorderFactory.createEmptyBorder(2, 0, 2, 0)));
            JPanel menuWrapper = new JPanel(new BorderLayout());
            menuWrapper.add(menuBar, BorderLayout.CENTER);
            add(menuWrapper, BorderLayout.NORTH);
        }

        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(240, 240, 240));
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        // Navigation tools (HOROS-20240407準拠)
        addToolButton(toolbar, LanguageManager.get("btn_select"), "Select / Edit", () -> DCMView.currentTool = DCMView.ToolMode.tPlain);
        addToolButton(toolbar, LanguageManager.get("btn_curved"), "Curved MPR ROI", () -> DCMView.currentTool = DCMView.ToolMode.tCurvedROI);
        addToolButton(toolbar, LanguageManager.get("btn_wlww"), "Window Level/Width", () -> DCMView.currentTool = DCMView.ToolMode.tWLWW);
                addToolButton(toolbar, LanguageManager.get("btn_zoom"), "Zoom", () -> DCMView.currentTool = DCMView.ToolMode.tZoom);

        toolbar.addSeparator();

        // Measurement & ROI tools (HOROS-20240407準拠)
        addToolButton(toolbar, LanguageManager.get("tools_roi.length"), "Measure Length", () -> DCMView.currentTool = DCMView.ToolMode.tMesure);
        addToolButton(toolbar, LanguageManager.get("tools_roi.rect"), "Rectangle ROI", () -> DCMView.currentTool = DCMView.ToolMode.tOval);
        addToolButton(toolbar, LanguageManager.get("tools_roi.oval"), "Oval ROI", () -> DCMView.currentTool = DCMView.ToolMode.tOval);
        addToolButton(toolbar, LanguageManager.get("tools_roi.poly_closed"), "Closed Polygon ROI", () -> DCMView.currentTool = DCMView.ToolMode.tCPolygon);
        addToolButton(toolbar, LanguageManager.get("tools_roi.poly_open"), "Open Polygon ROI", () -> DCMView.currentTool = DCMView.ToolMode.tOPolygon);

        toolbar.addSeparator();

        // Annotation tools (HOROS-20240407準拠)
        addToolButton(toolbar, LanguageManager.get("tools_roi.angle"), "Angle Measurement", () -> DCMView.currentTool = DCMView.ToolMode.tAngle);
        addToolButton(toolbar, LanguageManager.get("annotation.arrow"), "Arrow Annotation", () -> DCMView.currentTool = DCMView.ToolMode.tArrow);
        addToolButton(toolbar, LanguageManager.get("annotation.pencil"), "Pencil / Freehand ROI", () -> DCMView.currentTool = DCMView.ToolMode.tPencil);

        toolbar.addSeparator();

        // Point / Text (HOROS-20240407準拠)
        addToolButton(toolbar, LanguageManager.get("btn_point"), "2D Point", () -> DCMView.currentTool = DCMView.ToolMode.t2DPoint);
        addToolButton(toolbar, LanguageManager.get("btn_text"), "Text Annotation", () -> DCMView.currentTool = DCMView.ToolMode.tText);

        // Special tools
        addToolButton(toolbar, LanguageManager.get("btn_axis"), "Axis / Tumor", () -> DCMView.currentTool = DCMView.ToolMode.tAxis);
        addToolButton(toolbar, LanguageManager.get("btn_dangle"), "Dynamic Angle", () -> DCMView.currentTool = DCMView.ToolMode.tDynamicAngle);
        addToolButton(toolbar, LanguageManager.get("btn_perp"), "Perpendicular", () -> DCMView.currentTool = DCMView.ToolMode.tPerpendicular);

        toolbar.addSeparator();

        // Curved MPR 3D (HOROS-20240407準拠)
        addToolButton(toolbar, LanguageManager.get("btn_cpr"), "Curved MPR 3D", () -> {
            BrowserController current = BrowserController.currentBrowser();
            if (current != null) current.cprViewer(null);
        });

        // Brush / Repulsor
        addToolButton(toolbar, LanguageManager.get("btn_brush"), "Brush", () -> {
            DCMView.currentTool = DCMView.ToolMode.tBrush;
            Window owner = SwingUtilities.getWindowAncestor(ToolbarPanel.this);
            BrushPaletteDialog.showPalette(owner instanceof Frame ? (Frame) owner : null);
        });
        addToolButton(toolbar, LanguageManager.get("btn_repulse"), "Repulsor", () -> DCMView.currentTool = DCMView.ToolMode.tRepulsor);

        toolbar.addSeparator();

        // WLWW presets (HOROS-20240407準拠)
        String[] presets = { "Standard", "Bone", "Lung", "Brain", "Soft Tissue" };
        JComboBox<String> presetCombo = new JComboBox<>(presets);
        presetCombo.setMaximumSize(new Dimension(100, 25));
        presetCombo.addActionListener(e -> {
            DCMView current = viewer != null ? viewer.getImageView() : null;
            if (current != null) current.applyWLPreset((String) presetCombo.getSelectedItem());
        });
        toolbar.add(new JLabel(" WL: "));
        toolbar.add(presetCombo);

        // Sync toggle
        JCheckBox syncCheck = new JCheckBox(LanguageManager.get("btn_sync"), DCMView.syncro != DCMView.syncroOFF);
        syncCheck.setOpaque(false);
        syncCheck.setForeground(Color.WHITE);
        syncCheck.addActionListener(e -> {
            DCMView.syncro = syncCheck.isSelected() ? DCMView.syncroABS : DCMView.syncroOFF;
        });
        toolbar.add(syncCheck);

        toolbar.addSeparator();

        // 3D viewer buttons (HOROS-20240407準拠)
        addToolButton(toolbar, LanguageManager.get("btn_mpr"), "3D MPR Viewer", () -> {
            if (viewer != null) MPRController.openMPR(viewer);
        });
        addToolButton(toolbar, LanguageManager.get("btn_mip3d"), "3D MIP", () -> {
            if (viewer != null) Viewer3DController.openMIP(viewer);
        });
        addToolButton(toolbar, LanguageManager.get("btn_vr3d"), "3D VR", () -> {
            if (viewer != null) Viewer3DController.openVR(viewer);
        });

        toolbar.addSeparator();

        // Image edit - English labels via i18n (HOROS-20240407準拠)
        addToolButton(toolbar, LanguageManager.get("btn_flip_h"), "Flip H", () -> {
            DCMView current = viewer != null ? viewer.getImageView() : null;
            if (current != null) current.toggleXFlipped();
        });
        addToolButton(toolbar, LanguageManager.get("btn_flip_v"), "Flip V", () -> {
            DCMView current = viewer != null ? viewer.getImageView() : null;
            if (current != null) current.toggleYFlipped();
        });
        addToolButton(toolbar, LanguageManager.get("btn_rot_r"), "Rot 90 R", () -> {
            DCMView current = viewer != null ? viewer.getImageView() : null;
            if (current != null) current.rotateRight();
        });
        addToolButton(toolbar, LanguageManager.get("btn_rot_l"), "Rot 90 L", () -> {
            DCMView current = viewer != null ? viewer.getImageView() : null;
            if (current != null) current.rotateLeft();
        });

        add(toolbar, BorderLayout.CENTER);
    }

    private void addToolButton(JToolBar bar, String text, String tooltip, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(UIStyle.MAIN_FONT);
        if (tooltip != null) btn.setToolTipText(tooltip);
        btn.addActionListener(e -> action.run());
        bar.add(btn);
    }

    public int getFixedHeight() { return FIXED_HEIGHT; }
    public static int getHiddenHeight() { return HIDDEN_HEIGHT; }
    public int getExposedHeight() { return FIXED_HEIGHT; }
    public static int exposedHeight() { return FIXED_HEIGHT; }

    public void applicationDidChangeScreenParameters() {
        if (viewer == null || viewer.getGraphicsConfiguration() == null) return;
        GraphicsConfiguration gc = viewer.getGraphicsConfiguration();
        Rectangle screenBounds = gc.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int x = screenBounds.x + screenInsets.left;
        int width = screenBounds.width - screenInsets.left - screenInsets.right;
        int height = FIXED_HEIGHT;
        int y = screenBounds.y + screenInsets.top;
        setBounds(x, y, width, height);
    }

    private void windowDidBecomeMain() {
        if (viewer != null && viewer.isVisible()) {
            viewer.toFront();
            this.toFront();
            viewer.requestFocus();
        } else { setVisible(false); }
    }

    public ViewerController getViewer() { return viewer; }
    public JToolBar getToolbar() { return toolbar; }
}