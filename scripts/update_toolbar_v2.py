import re

tp_path = r'C:\Users\jam11\OculusJJ\src\main\java\com\oculus\jj\ui\ToolbarPanel.java'
with open(tp_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Helper to replace block using line-number range (startLine, endLine inclusive)
def replace_block(start_line, end_line, replacement):
    lines = content.split('\n')
    # start/end are 1-based
    before = '\n'.join(lines[:start_line-1])
    after = '\n'.join(lines[end_line:])
    return before + replacement + '\n' + after

# toggleAnnotationsPanel: line 261-263 (old)
content = replace_block(261, 263, '''    // HOROS-20240407準拠: AnnotationsToolbarItemIdentifier (ViewerController.m 6691行目附近)
    private void toggleAnnotationsPanel() {
        ViewerController vc = viewer;
        if (vc == null || vc.getImageView() == null) return;
        int annotationType = vc.getAnnotationType();
        String status;
        switch (annotationType) {
            case DCMView.annotGraphics: status = "Annotation layer: graphics"; break;
            case DCMView.annotBase:     status = "Annotation layer: base"; break;
            case 0:                     status = "Annotations: hidden"; break;
            default:                    status = "Annotation type: " + annotationType; break;
        }
        JPanel pnl = new JPanel(new GridLayout(4, 1, 6, 6));
        pnl.setOpaque(false);
        JLabel lblStatus = new JLabel(status, SwingConstants.CENTER);
        lblStatus.setFont(UIStyle.MAIN_FONT.deriveFont(12f));
        JCheckBox cbToggle = new JCheckBox("Show annotations on image");
        cbToggle.setSelected(annotationType != DCMView.annotNone);
        cbToggle.addActionListener(e -> {
            int next = cbToggle.isSelected() ? (annotationType == 0 ? DCMView.annotBase : annotationType) : DCMView.annotNone;
            vc.getImageView().annotMenu(next);
        });
        JDialog dlg = new JDialog(this, "Annotations", ModalityType.MODELESS);
        pnl.add(lblStatus); pnl.add(cbToggle);
        dlg.setContentPane(pnl);
        dlg.pack();
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        dlg.setLocation(d.width - 300, 100);
        dlg.setVisible(true);
    }''')

# toggleOrientationWidget: line 265-267 (old)
content = replace_block(265, 267, '''    // HOROS-20240407準拠: OrientationToolbarItemIdentifier (ViewerController.m 6691行目附近) -- 方向パネル表示/非表示切替
    private boolean orientationVisible = false;
    private void toggleOrientationWidget() {
        orientationVisible = !orientationVisible;
        if (!orientationVisible) return;
        JPanel pnl = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        pnl.setOpaque(false);
        String[] axes = {"Axial", "Sagittal", "Coronal"};
        JCheckBox[] boxes = new JCheckBox[3];
        for (int i = 0; i < axes.length; i++) {
            final int idx = i;
            boxes[i] = new JCheckBox(axes[i], false);
            boxes[i].addActionListener(e -> JOptionPane.showMessageDialog(this, axes[idx] + " orientation - feature in development", "Orientation", JOptionPane.INFORMATION_MESSAGE));
            pnl.add(boxes[i]);
        }
        JDialog dlg = new JDialog(this, "Orientation", ModalityType.MODELESS);
        dlg.setContentPane(pnl);
        dlg.pack();
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        dlg.setLocation(d.width - 200, 300);
        dlg.setVisible(true);
    }''')

# toggleNavigatorPanel: line 269-271 (old)
content = replace_block(269, 271, '''    // HOROS-20240407準拠: NavigatorToolbarItemIdentifier (ViewerController.m 6691行目附近) -- NavigatorPanel表示/非表示切替
    private NavigatorPanel navigatorPanel;
    private void toggleNavigatorPanel() {
        if (navigatorPanel == null || !navigatorPanel.isDisplaying()) {
            DCMView view = (viewer != null && viewer.getImageView() != null) ? viewer.getImageView() : null;
            if (view == null) { JOptionPane.showMessageDialog(this, "No viewer open.", "Navigator", JOptionPane.INFORMATION_MESSAGE); return; }
            navigatorPanel = new NavigatorPanel();
            navigatorPanel.setThumbnail(view.getThumbnailImage(), new java.awt.Rectangle(0, 0, 150, 120));
            JFrame frame = new JFrame("Navigator");
            frame.setUndecorated(true);
            frame.setAlwaysOnTop(true);
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            Dimension sz = navigatorPanel.getPreferredSize();
            frame.setBounds(d.width - sz.width - 50, d.height - sz.height - 350, sz.width + 20, sz.height + 20);
            frame.setContentPane(navigatorPanel);
            navigatorPanel.showNavigator = true;
            navigatorPanel.setVisible(true);
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent e) {
                    navigatorPanel.setVisible(false);
                    navigatorPanel.showNavigator = false;
                }
            });
            frame.setVisible(true);
        } else {
            if (navigatorPanel != null) { navigatorPanel.setVisible(false); navigatorPanel.showNavigator = false; }
        }
    }''')

# toggleFusionMode: line 273-275 (old)
content = replace_block(273, 275, '''    // HOROS-20240407準拠: FusionToolbarItemIdentifier (ViewerController.m 6691行目) -- パネル表示/非表示切替
    private JDialog fusionDialog;
    private void toggleFusionMode() {
        if (fusionDialog == null || !fusionDialog.isVisible()) {
            FusionControlsPanel fcp = new FusionControlsPanel();
            fusionDialog = new JDialog(this, "Fusion Controls", ModalityType.MODELESS);
            fusionDialog.setContentPane(fcp);
            fusionDialog.pack();
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            fusionDialog.setLocation(d.width - 350, (d.height - fusionDialog.getHeight()) / 2);
            fusionDialog.setVisible(true);
        } else {
            fusionDialog.dispose();
            fusionDialog = null;
        }
    }''')

# Add Sync Series toolbar button near panel toggles section (line ~197 area)
sync_code = '        addToolButton(toolbar, "toolbar.sync_series", "tt.toolbar.sync_series", () -> { if (viewer != null && viewer.getImageView() != null) DCMView.SYNCSERIES = !DCMView.SYNCSERIES; });\n\n'
content = content.replace(
    '        // Panel toggles + Email / Print',
    sync_code + '        // Sync Series + Panel toggles + Email / Print'
)

# Add import for Dialog (ModalityType) if not present
if 'import java.awt.Dialog;' not in content:
    content = content.replace('import javax.swing.*;', 'import javax.swing.*;\nimport java.awt.Dialog;')

with open(tp_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("OK")
