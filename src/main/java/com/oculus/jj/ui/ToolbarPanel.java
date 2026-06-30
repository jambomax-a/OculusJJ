package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import com.oculus.jj.utils.UIStyle;
import com.oculus.jj.utils.LanguageManager;
import java.awt.event.*;

/**
 * ToolbarPanel - ツールバーウィンドウ（JToolBar方式）
 * HOROS-20240407準拠: ToolbarPanel.h, ToolbarPanel.m
 * JToolBar方式で横スクロール可能。高さはFIXED_HEIGHT=40に小さくしてオーバーレイ回避。
 */
public class ToolbarPanel extends JFrame {

    private static final int FIXED_HEIGHT = 90; // ツールバー高さ（Math.maxで最小値確保）
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

    private JButton createToolButton(String labelKey, String tooltipKey, Runnable action) {
        JButton btn = new JButton(LanguageManager.get(labelKey));
        if (tooltipKey != null && !tooltipKey.isEmpty()) {
            btn.setToolTipText(LanguageManager.get(tooltipKey));
        }
        btn.setFont(UIStyle.MAIN_FONT);
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private void initializeUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(UIStyle.BACKGROUND_COLOR);
        setUndecorated(true);
        setLayout(new BorderLayout());

        // ===== Menu bar =====
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


        // ===== Tool bar (横スクロール可能JToolBar) =====
        toolbar = new JToolBar();
        JScrollPane scrollPane = new JScrollPane(toolbar);
        scrollPane.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new java.awt.Color(240, 240, 240));
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        toolbar.setBackground(java.awt.Color.GRAY);
        toolbar.setBackground(java.awt.Color.GRAY);


        toolbar.setFloatable(false);
        toolbar.setBackground(UIStyle.BACKGROUND_COLOR);
        toolbar.setBackground(new java.awt.Color(240, 240, 240));


        // Navigation tools (select/curved/wlww/zoom)
        addToolButton(toolbar, "btn_select", "tt.btn_select", () -> DCMView.currentTool = DCMView.ToolMode.tPlain);
        addToolButton(toolbar, "btn_curved", "tt.btn_curved", () -> DCMView.currentTool = DCMView.ToolMode.tCurvedROI);
        addToolButton(toolbar, "btn_wlww", "tt.btn_wlww", () -> DCMView.currentTool = DCMView.ToolMode.tWLWW);
        addToolButton(toolbar, "btn_zoom", "tt.btn_zoom", () -> DCMView.currentTool = DCMView.ToolMode.tZoom);

        toolbar.addSeparator();

        // Measurement & ROI tools (open polygon: tOpenPolygon → tOPolygon 修正)
        addToolButton(toolbar, "tools_roi.length", "tt.tools_roi.length", () -> DCMView.currentTool = DCMView.ToolMode.tMesure);
        addToolButton(toolbar, "tools_roi.rect", "tt.tools_roi.rect", () -> DCMView.currentTool = DCMView.ToolMode.tOval);
        addToolButton(toolbar, "tools_roi.oval", "tt.tools_roi.oval", () -> DCMView.currentTool = DCMView.ToolMode.tOval);
        addToolButton(toolbar, "tools_roi.poly_closed", "tt.tools_roi.poly_closed", () -> DCMView.currentTool = DCMView.ToolMode.tCPolygon);
        // typo修正: tOpenPolygon → tOPolygon
        addToolButton(toolbar, "tools_roi.poly_open", "tt.tools_roi.poly_open", () -> DCMView.currentTool = DCMView.ToolMode.tOPolygon);

        toolbar.addSeparator();

        // Annotation tools (angle/arrow/pencil)
        addToolButton(toolbar, "tools_roi.angle", "tt.tools_roi.angle", () -> DCMView.currentTool = DCMView.ToolMode.tAngle);
        addToolButton(toolbar, "annotation.arrow", "tt.annotation.arrow", () -> DCMView.currentTool = DCMView.ToolMode.tArrow);
        addToolButton(toolbar, "annotation.pencil", "tt.annotation.pencil", () -> DCMView.currentTool = DCMView.ToolMode.tPencil);

        toolbar.addSeparator();

        // Point/Text + Special (axis/dangle/perp)
        addToolButton(toolbar, "btn_point", "tt.btn_point", () -> DCMView.currentTool = DCMView.ToolMode.t2DPoint);
        addToolButton(toolbar, "btn_text", "tt.btn_text", () -> DCMView.currentTool = DCMView.ToolMode.tText);
        addToolButton(toolbar, "btn_axis", "tt.btn_axis", () -> DCMView.currentTool = DCMView.ToolMode.tAxis);
        addToolButton(toolbar, "btn_dangle", "tt.btn_dangle", () -> DCMView.currentTool = DCMView.ToolMode.tDynamicAngle);
        addToolButton(toolbar, "btn_perp", "tt.btn_perp", () -> DCMView.currentTool = DCMView.ToolMode.tPerpendicular);

        toolbar.addSeparator();

        // CPR / Brush / Repulsor / Sync / MPR / MIP3D / VR3D
        addToolButton(toolbar, "btn_cpr", null, () -> { BrowserController current = BrowserController.currentBrowser(); if (current != null) current.cprViewer(null); });
        addToolButton(toolbar, "btn_brush", null, () -> { DCMView.currentTool = DCMView.ToolMode.tBrush; Window owner = SwingUtilities.getWindowAncestor(ToolbarPanel.this); BrushPaletteDialog.showPalette(owner instanceof Frame ? (Frame) owner : null); });
        addToolButton(toolbar, "btn_repulse", null, () -> DCMView.currentTool = DCMView.ToolMode.tRepulsor);

        JCheckBox syncCheck = new JCheckBox(LanguageManager.get("btn_sync"));
        syncCheck.setOpaque(false);
        syncCheck.setForeground(Color.WHITE);
        syncCheck.addActionListener(e -> { DCMView.syncro = syncCheck.isSelected() ? DCMView.syncroABS : DCMView.syncroOFF; });

        addToolButton(toolbar, "btn_mpr", null, () -> { if (viewer != null) MPRController.openMPR(viewer); });
        addToolButton(toolbar, "btn_mip3d", null, () -> { if (viewer != null) Viewer3DController.openMIP(viewer); });
        addToolButton(toolbar, "btn_vr3d", null, () -> { if (viewer != null) Viewer3DController.openVR(viewer); });

        toolbar.addSeparator();


        // Flip / Rotate
        addToolButton(toolbar, "btn_flip_h", null, () -> { DCMView v = viewer != null ? viewer.getImageView() : null; if (v != null) v.toggleXFlipped(); });
        addToolButton(toolbar, "btn_flip_v", null, () -> { DCMView v = viewer != null ? viewer.getImageView() : null; if (v != null) v.toggleYFlipped(); });
        addToolButton(toolbar, "btn_rot_r", null, () -> { DCMView v = viewer != null ? viewer.getImageView() : null; if (v != null) v.rotateRight(); });
        addToolButton(toolbar, "btn_rot_l", null, () -> { DCMView v = viewer != null ? viewer.getImageView() : null; if (v != null) v.rotateLeft(); });

        toolbar.addSeparator();

        // Panel toggles + Email / Print
        addToolButton(toolbar, "toolbar.annotations", "tt.toolbar.annotations", () -> toggleAnnotationsPanel());
        addToolButton(toolbar, "toolbar.orientation", "tt.toolbar.orientation", () -> toggleOrientationWidget());
        addToolButton(toolbar, "toolbar.navigator", "tt.toolbar.navigator", () -> toggleNavigatorPanel());
        addToolButton(toolbar, "toolbar.fusion", "tt.toolbar.fusion", () -> toggleFusionMode());

        JButton emailBtn = new JButton(LanguageManager.get("toolbar.email"));
        emailBtn.setFont(UIStyle.MAIN_FONT);
        if (!LanguageManager.get("toolbar.email").equals("toolbar.email")) { emailBtn.setToolTipText(LanguageManager.get("toolbar.email")); } else { emailBtn.setToolTipText("Email"); }
        emailBtn.addActionListener(e -> sendMailAction());
        toolbar.add(emailBtn);

        JButton printBtn = new JButton(LanguageManager.get("toolbar.print"));
        printBtn.setFont(UIStyle.MAIN_FONT);
        if (!LanguageManager.get("toolbar.print").equals("toolbar.print")) { printBtn.setToolTipText(LanguageManager.get("toolbar.print")); } else { printBtn.setToolTipText("Print"); }
        printBtn.addActionListener(e -> printDICOMAction());
        toolbar.add(printBtn);

        add(scrollPane, BorderLayout.CENTER);
    } // close initializeUI()

    private void addToolButton(JToolBar bar, String labelKey, String tooltipKey, Runnable action) {
        JButton btn = createToolButton(labelKey, tooltipKey, action);
        bar.add(btn);
    }

    public int getFixedHeight() { return FIXED_HEIGHT; }
    public static int getHiddenHeight() { return HIDDEN_HEIGHT; }
    public int getExposedHeight() { return FIXED_HEIGHT; }
    public static int exposedHeight() { return FIXED_HEIGHT; }

    @Deprecated
    public JToolBar getToolbar() { return toolbar; } // 後方互換性: nullを返す（旧API）

    public void applicationDidChangeScreenParameters() {
        if (viewer == null || viewer.getGraphicsConfiguration() == null) return;
        GraphicsConfiguration gc = viewer.getGraphicsConfiguration();
        Rectangle screenBounds = gc.getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int x = screenBounds.x + screenInsets.left;
        int width = screenBounds.width - screenInsets.left - screenInsets.right;
        // FIXED_HEIGHT=40 (JToolBar高さ、横スクロール可能)
        int height = Math.max(FIXED_HEIGHT, toolbar.getPreferredSize().height);
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

    // --- Action handlers (existing logic) ---

    private void toggleAnnotationsPanel() {
        JOptionPane.showMessageDialog(this, LanguageManager.get("toolbar.panel.annotations", "Annotations panel - feature in development"), "Annotations", JOptionPane.INFORMATION_MESSAGE);
    }

    private void toggleOrientationWidget() {
        JOptionPane.showMessageDialog(this, LanguageManager.get("toolbar.panel.position3d", "3D Position panel - feature in development"), "3D Position", JOptionPane.INFORMATION_MESSAGE);
    }

    private void toggleNavigatorPanel() {
        JOptionPane.showMessageDialog(this, LanguageManager.get("toolbar.panel.navigator", "Navigator panel - feature in development"), "Navigator", JOptionPane.INFORMATION_MESSAGE);
    }

    private void toggleFusionMode() {
        JOptionPane.showMessageDialog(this, LanguageManager.get("toolbar.panel.fusion", "Fusion/Blending - feature in development"), "Fusion", JOptionPane.INFORMATION_MESSAGE);
    }

    private void sendMailAction() {
        ViewerController vc = viewer;
        if (vc != null && vc.getImageView() != null) {
            try { java.awt.Desktop.getDesktop().mail(); } catch (java.io.IOException e) {}
        } else if (BrowserController.currentBrowser() != null) {
            BrowserController.currentBrowser().sendMail(null);
        }
    }

    private void printDICOMAction() {
        ViewerController vc = viewer;
        if (vc != null && BrowserController.currentBrowser() != null) {
            BrowserController.currentBrowser().printDICOM(null);
        }
    }

} // close ToolbarPanel class

