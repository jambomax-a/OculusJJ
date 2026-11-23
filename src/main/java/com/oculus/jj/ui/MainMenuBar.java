package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import com.oculus.jj.utils.LanguageManager;

/**
 * MainMenuBar - HOROS-20240407準拠のメインメニューバー。
 * BrowserController の initializeMenuBar() と併用され、
 * updateForViewType によるビュー種別ごとの表示切替を担当する。
 */
public class MainMenuBar extends JMenuBar {

    private static final long serialVersionUID = 1L;

    /** Recent Studies 履歴の優先領域キー */
    private static final String PREF_RECENT_STUDIES = "recentStudies";

    public enum ViewType {
        STANDARD_2D, MPR_ORTHOGONAL, MPR_CURVED, VIEWER_3D
    }

    private ViewType currentViewType = ViewType.STANDARD_2D;

    private JMenu advancedMenu, toolsMenu, analysisMenu, viewMenu;
    private JMenuItem mip3dItem, vr3dItem, cprItem;
    private JMenuItem lengthItem, angleItem, roiOvalItem;

    public MainMenuBar() { super(); initMenus(); }

    private void initMenus() {
        String fileLabel = LanguageManager.get("menu_file", "File");
        String viewLabel = LanguageManager.get("menu_view", "View");
        String toolsLabel = LanguageManager.get("menu_tools", "Tools");
        String settingsLabel = LanguageManager.get("menu_settings", "Settings");

        JMenu fileMenu = new JMenu(fileLabel);
        fileMenu.add(makeItem(LanguageManager.get("menu_open_study", "Open Study..."), e -> openStudy()));
        fileMenu.addSeparator();

        JMenuItem prefItem = makeItem(LanguageManager.get("menu_file_preferences", "Preferences..."), e -> showPreferences());
        prefItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_COMMA, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        fileMenu.add(prefItem);

        JMenu importMenu = new JMenu(LanguageManager.get("file_import", "Import"));
        importMenu.add(makeItem(LanguageManager.get("menu_import_files", "From Files..."), e -> openStudy()));
        importMenu.add(makeItem(LanguageManager.get("menu_import_cd", "From CD/DVD..."), e -> showBurnerDialog()));
        JMenuItem pacsItem = makeItem(LanguageManager.get("menu_import_pacs", "From PACS (C-MOVE)..."), e -> queryPACS());
        importMenu.add(pacsItem);
        fileMenu.add(importMenu);

        JMenu exportMenu = new JMenu(LanguageManager.get("file_export", "Export"));
        exportMenu.add(makeItem(LanguageManager.get("menu_export_dicom", "To DICOM File..."), e -> exportStudy()));
        exportMenu.add(makeItem(LanguageManager.get("menu_export_image", "JPEG / TIFF..."), e -> exportToImage()));
        exportMenu.add(makeItem(LanguageManager.get("menu_export_roi", "ROI Data..."), e -> exportROIData()));
        fileMenu.add(exportMenu);

        fileMenu.addSeparator();
        fileMenu.add(makeItem(LanguageManager.get("menu_burn", "Burn to CD/DVD..."), e -> showBurnerDialog()));
        JMenuItem anonItem = makeItem(LanguageManager.get("menu_anonymize", "Anonymize DICOM..."), e -> { Frame o = (Frame)SwingUtilities.getWindowAncestor(MainMenuBar.this); if(o==null)o=BrowserController.currentBrowser(); if(o!=null) AnonymizationDialog.showAnonymization(o); }); fileMenu.add(anonItem);
        fileMenu.addSeparator();

        JMenu printSub = new JMenu(LanguageManager.get("menu_print", "Print"));
        printSub.add(makeItem(LanguageManager.get("menu_file_dicom_print", "DICOM Print..."), e -> dicomPrint()));
        printSub.add(makeItem(LanguageManager.get("menu_file_page_setup", "Page Setup..."), e -> pageSetup()));
        fileMenu.add(printSub);
        fileMenu.addSeparator();

        JMenu recentStudiesMenu = makeRecentStudiesMenu();
        fileMenu.add(recentStudiesMenu);
        fileMenu.addSeparator();

        fileMenu.add(makeItem(LanguageManager.get("menu_window_close_all_viewers", "Close All Viewers"), e -> closeAllViewers()));
        fileMenu.add(makeItem(LanguageManager.get("menu_exit", "Exit"), e -> System.exit(0)));
        add(fileMenu);

        viewMenu = new JMenu(viewLabel);
        viewMenu.add(makeItem(LanguageManager.get("menu_window_zoom", "Zoom"), e -> setTool(DCMView.ToolMode.tZoom)));
        viewMenu.addSeparator();
        viewMenu.add(makeItem(LanguageManager.get("menu_shutter", "Shutter"), e -> setTool(DCMView.ToolMode.tShutter)));
        viewMenu.addSeparator();
        viewMenu.add(makeItem(LanguageManager.get("menu_tools_window_level", "Window Level/Width"), e -> setTool(DCMView.ToolMode.tWLWW)));

        JMenu displaySub = new JMenu(LanguageManager.get("menu_display", "Display"));
        displaySub.add(makeItem(LanguageManager.get("menu_flip_horizontal", "Flip Horizontal"), e -> flipX()));
        displaySub.add(makeItem(LanguageManager.get("menu_flip_vertical", "Flip Vertical"), e -> flipY()));
        viewMenu.add(displaySub);
        viewMenu.addSeparator();

        JMenu tilingMenu = new JMenu(LanguageManager.get("menu_tiling", "Image Tiling"));
        String[] layouts = {"1x1","2x1","1x2","2x2","3x1","1x3","3x3","4x2","2x4","5x1","1x5"};
        for (String l : layouts) { final String layout = l; tilingMenu.add(makeItem(layout, ev -> tileImages(layout))); }
        tilingMenu.addSeparator();
        // HOROS-20240407準拠: setFixedTilingRows: / setFixedTilingColumns: (AppController.m:4760/4765)
        tilingMenu.add(makeItem(LanguageManager.get("menu_fixed_rows", "Fixed Rows..."), ev -> fixedTilingRows()));
        tilingMenu.add(makeItem(LanguageManager.get("menu_fixed_cols", "Fixed Columns..."), ev -> fixedTilingColumns()));
        viewMenu.add(tilingMenu);

        JMenu orientationMenu = new JMenu(LanguageManager.get("menu_orientation", "Orientation"));
        orientationMenu.add(makeItem(LanguageManager.get("view_axial", "Axial"), e -> setOrientation(LanguageManager.get("view_axial", "Axial"))));
        orientationMenu.add(makeItem(LanguageManager.get("view_coronal", "Coronal"), e -> setOrientation(LanguageManager.get("view_coronal", "Coronal"))));
        orientationMenu.add(makeItem(LanguageManager.get("view_sagittal", "Sagittal"), e -> setOrientation(LanguageManager.get("view_sagittal", "Sagittal"))));
        viewMenu.add(orientationMenu);
        // Orthogonal MPR - 3D volume直交断面表示
        JMenuItem mprItem = makeItem(LanguageManager.get("menu_mpr_orthogonal", "Orthogonal MPR"), e -> openOrthogonalMPR());
        viewMenu.add(mprItem);

        advancedMenu = new JMenu(LanguageManager.get("menu_advanced", "Advanced"));
        mip3dItem = makeItem(LanguageManager.get("menu_3d_mip", "3D MIP"), e -> open3DMIP());
        vr3dItem = makeItem(LanguageManager.get("menu_3d_vr", "3D Volume Rendering"), e -> open3DVR());
        cprItem = makeItem(LanguageManager.get("menu_cpr", "Curved MPR"), e -> openCPR());
        advancedMenu.add(mip3dItem);
        advancedMenu.add(vr3dItem);
        advancedMenu.add(cprItem);
        // HOROS-20240407準拠: Endoscopy / SR Viewer menu items
        advancedMenu.add(makeItem(LanguageManager.get("menu_endoscopy", "Endoscopy Viewer"), e -> openEndoscopyImpl()));
        advancedMenu.add(makeItem(LanguageManager.get("menu_sr", "Structured Report"), e -> openSRViewerImpl()));

        toolsMenu = new JMenu(toolsLabel);
        JMenu roiTools = new JMenu(LanguageManager.get("menu_roi_tools", "ROI Tools"));
        roiOvalItem = makeItem(LanguageManager.get("menu_oval_roi", "Oval ROI"), e -> setTool(DCMView.ToolMode.tOval));
        roiTools.add(roiOvalItem);
        JMenu measureTools = new JMenu(LanguageManager.get("menu_tools_measure", "Measure"));
        lengthItem = makeItem(LanguageManager.get("menu_length", "Length"), e -> setTool(DCMView.ToolMode.tMesure));
        angleItem = makeItem(LanguageManager.get("menu_angle", "Angle"), e -> setTool(DCMView.ToolMode.tAngle));
        measureTools.add(lengthItem);
        measureTools.add(angleItem);
        measureTools.addSeparator();
        measureTools.add(makeItem(LanguageManager.get("menu_vhs", "VHS (Vertebral Heart Size)"), e -> setTool(DCMView.ToolMode.tVHS)));
        measureTools.add(makeItem(LanguageManager.get("menu_vlas", "VLAS (Vertebral Left Atrial Size)"), e -> setTool(DCMView.ToolMode.tVLAS)));
        measureTools.add(makeItem(LanguageManager.get("menu_mvlas", "M-VLAS (Modified VLAS)"), e -> setTool(DCMView.ToolMode.tMVLAS)));
        JMenu annotTools = new JMenu(LanguageManager.get("menu_annotations", "Annotations"));
        annotTools.add(makeItem(LanguageManager.get("menu_text", "Text"), e -> setTool(DCMView.ToolMode.tText)));
        annotTools.add(makeItem(LanguageManager.get("menu_arrow", "Arrow"), e -> setTool(DCMView.ToolMode.tArrow)));
        annotTools.add(makeItem(LanguageManager.get("menu_pencil", "Pencil / Freehand ROI"), e -> setTool(DCMView.ToolMode.tPencil)));
        toolsMenu.add(roiTools);
        toolsMenu.add(measureTools);
        toolsMenu.add(annotTools);

        analysisMenu = new JMenu(LanguageManager.get("menu_analysis", "Analysis"));
        analysisMenu.add(makeItem(LanguageManager.get("menu_toolbar_bone_removal", "Bone Removal"), e -> analysisPending(LanguageManager.get("menu_toolbar_bone_removal", "Bone Removal"))));
        analysisMenu.add(makeItem(LanguageManager.get("menu_repulsor", "Repulsor / ROI Sculpting"), e -> setTool(DCMView.ToolMode.tRepulsor)));

        JMenu settingsMenu = new JMenu(settingsLabel);
        settingsMenu.add(makeItem(LanguageManager.get("menu_file_preferences", "Preferences..."), e -> showPreferences()));
        add(settingsMenu);

        viewMenu.addSeparator();
        viewMenu.add(advancedMenu);
        viewMenu.add(toolsMenu);
        viewMenu.add(analysisMenu);

        advancedMenu.setVisible(false);
        toolsMenu.setVisible(true);
        analysisMenu.setVisible(false);

        add(viewMenu);
        updateForViewType(currentViewType);
    }

    private JMenuItem makeItem(String text, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        if (action != null) item.addActionListener(action);
        return item;
    }

    private void setTool(DCMView.ToolMode tool) { DCMView.currentTool = tool; }

    private void openStudy() { BrowserController bc = BrowserController.currentBrowser(); if (bc != null) bc.importFilesFromDialog(); }

    private void closeAllViewers() {
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        for (ViewerController vc : viewers) vc.dispose();
    }

    private void showBurnerDialog() { Frame owner = (Frame)SwingUtilities.getWindowAncestor(MainMenuBar.this); if(owner==null)owner=BrowserController.currentBrowser(); if(owner!=null) BurnerDialog.showBurnerDialog(owner); }

    private void showPreferences() {
        BrowserController bc = BrowserController.currentBrowser();
        Frame owner = bc != null ? bc : (Frame) SwingUtilities.getWindowAncestor(MainMenuBar.this);
        PreferencesController.showPreferences(owner);
    }

    private void queryPACS() { Frame owner = (Frame)SwingUtilities.getWindowAncestor(MainMenuBar.this); if(owner==null)owner=BrowserController.currentBrowser(); if(owner!=null) QueryController.getInstance().showQueryDialog(owner); }

    private void exportStudy() { JOptionPane.showMessageDialog(this, LanguageManager.get("menu_export_dicom_pending", "Export to DICOM File (pending integration)"), LanguageManager.get("file_export", "Export"), JOptionPane.INFORMATION_MESSAGE); }
    private void exportToImage() { JOptionPane.showMessageDialog(this, LanguageManager.get("menu_export_image_pending", "JPEG / TIFF export dialog pending"), LanguageManager.get("file_export", "Export"), JOptionPane.INFORMATION_MESSAGE); }
    private void exportROIData() { JOptionPane.showMessageDialog(this, LanguageManager.get("menu_export_roi_pending", "ROI data export dialog pending"), LanguageManager.get("file_export", "Export"), JOptionPane.INFORMATION_MESSAGE); }

    private void dicomPrint() { BrowserController bc = BrowserController.currentBrowser(); if (bc != null) bc.generateReport(null); }
    private void pageSetup() { JOptionPane.showMessageDialog(this, LanguageManager.get("menu_pagesetup_pending", "Page Setup dialog pending"), LanguageManager.get("menu_file_page_setup", "Page Setup..."), JOptionPane.INFORMATION_MESSAGE); }

    private void flipX() { DCMView view = getActiveDCMView(); if (view != null) view.toggleXFlipped(); }
    private void flipY() { DCMView view = getActiveDCMView(); if (view != null) view.toggleYFlipped(); }

    private DCMView getActiveDCMView() {
        BrowserController bc = BrowserController.currentBrowser();
        if (bc != null) { JButton activeCell = bc.getMatrix().selectedCell();
            if (activeCell != null && activeCell.getClientProperty("viewer") instanceof ViewerController vc) return vc.getImageView(); }
        List<ViewerController> viewers = ViewerController.getDisplayedViewers();
        return (!viewers.isEmpty()) ? viewers.get(viewers.size() - 1).getImageView() : null;
    }

    private void open3DMIP() { DCMView v = getActiveDCMView(); if (v != null && v.getController() != null) Viewer3DController.openMIP(v.getController()); }
    private void open3DVR() { DCMView v = getActiveDCMView(); if (v != null && v.getController() != null) Viewer3DController.openVR(v.getController()); }
    private void openEndoscopyImpl() {
        DCMView v = getActiveDCMView();
        if (v != null && v.getController() != null) Viewer3DController.openEndoscopy(v.getController());
    }

    private void openSRViewerImpl() {
        DCMView v = getActiveDCMView();
        if (v != null && v.getController() != null) Viewer3DController.openSR(v.getController());
    }

private void openCPR()  { DCMView v = getActiveDCMView(); if (v != null && v.getController() != null) CPRController.openCurved(v.getController()); }

    private void openOrthogonalMPR() { DCMView v = getActiveDCMView(); if (v != null && v.getController() != null) MPRController.openOrthogonal(v.getController()); }

    public void tileImages(String layout) { String[] parts = layout.split("x");
        if (parts.length == 2) { int rows = Integer.parseInt(parts[0]); int cols = Integer.parseInt(parts[1]); BrowserController bc = BrowserController.currentBrowser(); if (bc != null) bc.getMatrix().renewRows(rows, cols); } }

    /** HOROS-20240407準拠: setFixedTilingRows: (AppController.m:4760) — 行数のみ固定、列数は現状維持 */
    private void fixedTilingRows() {
        BrowserController bc = BrowserController.currentBrowser();
        if (bc == null) return;
        int curRows = bc.getMatrix().getRows();
        if (curRows <= 0) curRows = 1;
        Object input = JOptionPane.showInputDialog(this,
                LanguageManager.get("menu_fixed_rows_prompt", "Fixed tiling rows:"), String.valueOf(curRows), JOptionPane.QUESTION_MESSAGE);
        if (input == null) return;
        try { int rows = Integer.parseInt(input.toString().trim()); if (rows > 0) bc.getMatrix().setFixedTilingRows(rows); }
        catch (NumberFormatException ignored) {}
    }

    /** HOROS-20240407準拠: setFixedTilingColumns: (AppController.m:4765) — 列数のみ固定、行数は現状維持 */
    private void fixedTilingColumns() {
        BrowserController bc = BrowserController.currentBrowser();
        if (bc == null) return;
        int curCols = bc.getMatrix().getColumns();
        if (curCols <= 0) curCols = 1;
        Object input = JOptionPane.showInputDialog(this,
                LanguageManager.get("menu_fixed_cols_prompt", "Fixed tiling columns:"), String.valueOf(curCols), JOptionPane.QUESTION_MESSAGE);
        if (input == null) return;
        try { int cols = Integer.parseInt(input.toString().trim()); if (cols > 0) bc.getMatrix().setFixedTilingColumns(cols); }
        catch (NumberFormatException ignored) {}
    }

    private void setOrientation(String orientation) { DCMView view = getActiveDCMView();
        if (view != null) { BrowserController bc = BrowserController.currentBrowser();
            if (bc != null && bc.getCurrentViewType() == ViewType.STANDARD_2D) JOptionPane.showMessageDialog(this, LanguageManager.get("menu_orient_req_mpr", "Orientation: ") + orientation + LanguageManager.get("menu_orient_req_mpr_suffix", " (requires MPR viewer active)"), LanguageManager.get("menu_orientation", "Orientation"), JOptionPane.INFORMATION_MESSAGE); } }

    private void analysisPending(String name) { BrowserController bc = BrowserController.currentBrowser();
        if (bc != null) JOptionPane.showMessageDialog(bc, "\"" + name + "\" - " + LanguageManager.get("menu_impl_pending", "implementation pending"), LanguageManager.get("menu_analysis", "Analysis"), JOptionPane.INFORMATION_MESSAGE); }

    private JMenu makeRecentStudiesMenu() { JMenu menu = new JMenu(LanguageManager.get("recent_studies", "Recent Studies")); addRecentItems(menu);
        if (menu.getItemCount() == 0) { JMenuItem empty = new JMenuItem(LanguageManager.get("menu_empty", "(empty)")); empty.setEnabled(false); menu.add(empty); } return menu; }

    private void addRecentItems(JMenu menu) { Preferences prefs = Preferences.userNodeForPackage(MainMenuBar.class);
        int count = prefs.getInt(PREF_RECENT_STUDIES + ".count", 0);
        for (int i = Math.min(count, 9) - 1; i >= 0 && menu.getItemCount() < 9; i--) { String path = prefs.get(PREF_RECENT_STUDIES + "." + i, "");
            if (!path.isEmpty()) { JMenuItem item = new JMenuItem(path); final String p = path; item.addActionListener(e -> openStudyFromRecent(p)); menu.add(item); } } }

    private void openStudyFromRecent(String path) { File dir = new File(path);
        if (dir.isDirectory()) { BrowserController bc = BrowserController.currentBrowser(); if (bc != null) { List<String> files = new ArrayList<>(); collectDicomFiles(dir, files); if (!files.isEmpty()) bc.importFiles(files); }
        } else JOptionPane.showMessageDialog(this, LanguageManager.get("menu_path_notfound", "Path not found: ") + path, LanguageManager.get("menu_open_study", "Open Study..."), JOptionPane.ERROR_MESSAGE); }

    private void collectDicomFiles(File dir, List<String> out) { File[] entries = dir.listFiles();
        if (entries == null) return; for (File f : entries) { if (f.isDirectory()) collectDicomFiles(f, out); else if (!isNonDicomExt(f.getName())) out.add(f.getAbsolutePath()); } }

    private boolean isNonDicomExt(String name) { int dot = name.lastIndexOf('.'); if (dot <= 0) return false; String ext = name.substring(dot).toLowerCase();
        return ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png") || ext.equals(".bmp") || ext.equals(".zip") || ext.equals(".rar") || ext.equals(".pdf") || ext.equals(".txt") || ext.equals(".xml"); }

    public static void addRecentStudy(String path) { Preferences prefs = Preferences.userNodeForPackage(MainMenuBar.class);
        int count = prefs.getInt(PREF_RECENT_STUDIES + ".count", 0); List<String> list = new ArrayList<>();
        for (int i = 0; i < count && i < 10; i++) { String p = prefs.get(PREF_RECENT_STUDIES + "." + i, ""); if (!p.equals(path)) list.add(p); }
        list.add(0, path); while (list.size() > 10) list.remove(list.size() - 1);
        prefs.putInt(PREF_RECENT_STUDIES + ".count", list.size()); for (int i = 0; i < list.size(); i++) prefs.put(PREF_RECENT_STUDIES + "." + i, list.get(i)); }

    public void updateForViewType(ViewType viewType) { this.currentViewType = viewType; switch (viewType) {
        case STANDARD_2D: advancedMenu.setVisible(true); advancedMenu.setEnabled(true); toolsMenu.setEnabled(true); analysisMenu.setEnabled(true); lengthItem.setEnabled(true); roiOvalItem.setEnabled(true); break;
        case MPR_ORTHOGONAL: case MPR_CURVED: advancedMenu.setVisible(true); advancedMenu.setEnabled(true); toolsMenu.setEnabled(true); analysisMenu.setVisible(false); lengthItem.setEnabled(true); roiOvalItem.setEnabled(true); break;
        case VIEWER_3D: advancedMenu.setVisible(true); advancedMenu.setEnabled(true); toolsMenu.setEnabled(false); analysisMenu.setVisible(false); lengthItem.setEnabled(false); roiOvalItem.setEnabled(false); break; } }

    public ViewType getCurrentViewType() { return currentViewType; }
}
