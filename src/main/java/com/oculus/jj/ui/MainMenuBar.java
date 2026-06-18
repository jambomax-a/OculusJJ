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
        fileMenu.add(makeItem("Open Study...", e -> openStudy()));
        fileMenu.addSeparator();

        JMenuItem prefItem = makeItem("Preferences...", e -> showPreferences());
        prefItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_COMMA, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        fileMenu.add(prefItem);

        JMenu importMenu = new JMenu("Import");
        importMenu.add(makeItem("From Files...", e -> openStudy()));
        importMenu.add(makeItem("From CD/DVD...", e -> showBurnerDialog()));
        JMenuItem pacsItem = makeItem("From PACS (C-MOVE)...", e -> queryPACS());
        importMenu.add(pacsItem);
        fileMenu.add(importMenu);

        JMenu exportMenu = new JMenu("Export");
        exportMenu.add(makeItem("To DICOM File...", e -> exportStudy()));
        exportMenu.add(makeItem("JPEG / TIFF...", e -> exportToImage()));
        exportMenu.add(makeItem("ROI Data...", e -> exportROIData()));
        fileMenu.add(exportMenu);

        fileMenu.addSeparator();
        fileMenu.add(makeItem("Burn to CD/DVD...", e -> showBurnerDialog()));
        JMenuItem anonItem = makeItem("Anonymize DICOM...", e -> { Frame o = (Frame)SwingUtilities.getWindowAncestor(MainMenuBar.this); if(o==null)o=BrowserController.currentBrowser(); if(o!=null) AnonymizationDialog.showAnonymization(o); }); fileMenu.add(anonItem);
        fileMenu.addSeparator();

        JMenu printSub = new JMenu("Print");
        printSub.add(makeItem("DICOM Print...", e -> dicomPrint()));
        printSub.add(makeItem("Page Setup...", e -> pageSetup()));
        fileMenu.add(printSub);
        fileMenu.addSeparator();

        JMenu recentStudiesMenu = makeRecentStudiesMenu();
        fileMenu.add(recentStudiesMenu);
        fileMenu.addSeparator();

        fileMenu.add(makeItem("Close All Viewers", e -> closeAllViewers()));
        fileMenu.add(makeItem("Exit", e -> System.exit(0)));
        add(fileMenu);

        viewMenu = new JMenu(viewLabel);
        viewMenu.add(makeItem("Zoom", e -> setTool(DCMView.ToolMode.tZoom)));
        viewMenu.add(makeItem("Pan", e -> setTool(DCMView.ToolMode.tTranslate)));
        viewMenu.addSeparator();
        viewMenu.add(makeItem("Window Level/Width", e -> setTool(DCMView.ToolMode.tWLWW)));

        JMenu displaySub = new JMenu("Display");
        displaySub.add(makeItem("Flip Horizontal", e -> flipX()));
        displaySub.add(makeItem("Flip Vertical", e -> flipY()));
        viewMenu.add(displaySub);
        viewMenu.addSeparator();

        JMenu tilingMenu = new JMenu("Image Tiling");
        String[] layouts = {"1x1","2x1","1x2","2x2","3x1","1x3","3x3","4x2","2x4","5x1","1x5"};
        for (String l : layouts) { final String layout = l; tilingMenu.add(makeItem(layout, ev -> tileImages(layout))); }
        viewMenu.add(tilingMenu);

        JMenu orientationMenu = new JMenu("Orientation");
        orientationMenu.add(makeItem("Axial", e -> setOrientation("Axial")));
        orientationMenu.add(makeItem("Coronal", e -> setOrientation("Coronal")));
        orientationMenu.add(makeItem("Sagittal", e -> setOrientation("Sagittal")));
        viewMenu.add(orientationMenu);

        advancedMenu = new JMenu("Advanced");
        mip3dItem = makeItem("3D MIP", e -> open3DMIP());
        vr3dItem = makeItem("3D Volume Rendering", e -> open3DVR());
        cprItem = makeItem("Curved MPR", e -> openCPR());
        advancedMenu.add(mip3dItem);
        advancedMenu.add(vr3dItem);
        advancedMenu.add(cprItem);

        toolsMenu = new JMenu(toolsLabel);
        JMenu roiTools = new JMenu("ROI Tools");
        roiOvalItem = makeItem("Oval ROI", e -> setTool(DCMView.ToolMode.tOval));
        roiTools.add(roiOvalItem);
        JMenu measureTools = new JMenu("Measure");
        lengthItem = makeItem("Length", e -> setTool(DCMView.ToolMode.tMesure));
        angleItem = makeItem("Angle", e -> setTool(DCMView.ToolMode.tAngle));
        measureTools.add(lengthItem);
        measureTools.add(angleItem);
        JMenu annotTools = new JMenu("Annotations");
        annotTools.add(makeItem("Text", e -> setTool(DCMView.ToolMode.tText)));
        annotTools.add(makeItem("Arrow", e -> setTool(DCMView.ToolMode.tArrow)));
        annotTools.add(makeItem("Pencil / Freehand ROI", e -> setTool(DCMView.ToolMode.tPencil)));
        toolsMenu.add(roiTools);
        toolsMenu.add(measureTools);
        toolsMenu.add(annotTools);

        analysisMenu = new JMenu("Analysis");
        analysisMenu.add(makeItem("Bone Removal", e -> analysisPending("Bone Removal")));
        analysisMenu.add(makeItem("Repulsor / ROI Sculpting", e -> setTool(DCMView.ToolMode.tRepulsor)));

        JMenu settingsMenu = new JMenu(settingsLabel);
        settingsMenu.add(makeItem("Preferences...", e -> showPreferences()));
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

    private void exportStudy() { JOptionPane.showMessageDialog(this, "Export to DICOM File (pending integration)", "Export", JOptionPane.INFORMATION_MESSAGE); }
    private void exportToImage() { JOptionPane.showMessageDialog(this, "JPEG / TIFF export dialog pending", "Export", JOptionPane.INFORMATION_MESSAGE); }
    private void exportROIData() { JOptionPane.showMessageDialog(this, "ROI data export dialog pending", "Export", JOptionPane.INFORMATION_MESSAGE); }

    private void dicomPrint() { BrowserController bc = BrowserController.currentBrowser(); if (bc != null) bc.generateReport(null); }
    private void pageSetup() { JOptionPane.showMessageDialog(this, "Page Setup dialog pending", "Page Setup", JOptionPane.INFORMATION_MESSAGE); }

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
    private void openCPR()  { DCMView v = getActiveDCMView(); if (v != null && v.getController() != null) CPRController.openCurved(v.getController()); }

    public void tileImages(String layout) { String[] parts = layout.split("x");
        if (parts.length == 2) { int rows = Integer.parseInt(parts[0]); int cols = Integer.parseInt(parts[1]); BrowserController bc = BrowserController.currentBrowser(); if (bc != null) bc.getMatrix().renewRows(rows, cols); } }

    private void setOrientation(String orientation) { DCMView view = getActiveDCMView();
        if (view != null) { BrowserController bc = BrowserController.currentBrowser();
            if (bc != null && bc.getCurrentViewType() == ViewType.STANDARD_2D) JOptionPane.showMessageDialog(this, "Orientation: " + orientation + " (requires MPR viewer active)", "Orientation", JOptionPane.INFORMATION_MESSAGE); } }

    private void analysisPending(String name) { BrowserController bc = BrowserController.currentBrowser();
        if (bc != null) JOptionPane.showMessageDialog(bc, "\"" + name + "\" - implementation pending", "Analysis", JOptionPane.INFORMATION_MESSAGE); }

    private JMenu makeRecentStudiesMenu() { JMenu menu = new JMenu("Recent Studies"); addRecentItems(menu);
        if (menu.getItemCount() == 0) { JMenuItem empty = new JMenuItem("(empty)"); empty.setEnabled(false); menu.add(empty); } return menu; }

    private void addRecentItems(JMenu menu) { Preferences prefs = Preferences.userNodeForPackage(MainMenuBar.class);
        int count = prefs.getInt(PREF_RECENT_STUDIES + ".count", 0);
        for (int i = Math.min(count, 9) - 1; i >= 0 && menu.getItemCount() < 9; i--) { String path = prefs.get(PREF_RECENT_STUDIES + "." + i, "");
            if (!path.isEmpty()) { JMenuItem item = new JMenuItem(path); final String p = path; item.addActionListener(e -> openStudyFromRecent(p)); menu.add(item); } } }

    private void openStudyFromRecent(String path) { File dir = new File(path);
        if (dir.isDirectory()) { BrowserController bc = BrowserController.currentBrowser(); if (bc != null) { List<String> files = new ArrayList<>(); collectDicomFiles(dir, files); if (!files.isEmpty()) bc.importFiles(files); }
        } else JOptionPane.showMessageDialog(this, "Path not found: " + path, "Open Study", JOptionPane.ERROR_MESSAGE); }

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




