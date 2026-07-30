package com.oculus.jj.ui;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.util.List;

/**
 * NavigatorPanel — Study/Series/Image階層ナビゲータ
 *
 * HOROS-20240407準拠: NavigatorWindowController 相当。
 * DicomDatabaseから取得したStudy/Series/Image階層をJTreeで表示。
 * 選択したImageのサムネイル＋可視領域オーバーレイ（ミニマップ）も併設。
 */
public class NavigatorPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // ===== UIコンポーネント =====
    private JTree studyTree;
    private JScrollPane treeScrollPane;
    private ImageIcon thumbnailImage;
    private Rectangle visibleRect;
    private boolean showMiniMap = false;

    // ===== データモデル =====
    private com.oculus.jj.model.NavigatorModel navigatorModel;
    private DefaultMutableTreeNode rootFolderNode;  // "All Studies" ルートノード

    // ===== コールバック =====
    /** Image選択時に呼び出されるコールバック */
    private java.util.function.Consumer<String> onImageSelected;

    public NavigatorPanel() {
        setOpaque(true);
        setBackground(new Color(30, 30, 30));
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(280, 450));

        // ----- ツリービュー（Study/Series/Image階層）-----
        rootFolderNode = new DefaultMutableTreeNode("All Studies");
        studyTree = new JTree(rootFolderNode);
        studyTree.setOpaque(false);
        studyTree.setBackground(new Color(30, 30, 30));
        studyTree.setForeground(new Color(220, 220, 220));
        studyTree.setRootVisible(true);
        studyTree.setShowsRootHandles(true);
        studyTree.setCellRenderer(new NavigatorTreeCellRenderer());

        // 選択イベント → サムネイル更新 + コールバック
        studyTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) studyTree.getLastSelectedPathComponent();
                if (node == null) return;
                Object userObj = node.getUserObject();
                if (userObj instanceof ImageNodeInfo) {
                    String sopUID = ((ImageNodeInfo) userObj).sopInstanceUID;
                    if (onImageSelected != null) {
                        onImageSelected.accept(sopUID);
                    }
                }
            }
        });

        treeScrollPane = new JScrollPane(studyTree);
        treeScrollPane.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        treeScrollPane.setBackground(new Color(30, 30, 30));
        treeScrollPane.getViewport().setBackground(new Color(30, 30, 30));

        // ----- ミニマップ（サムネイル＋可視領域）-----
        JPanel miniMapPanel = new MiniMapView();
        miniMapPanel.setOpaque(false);
        miniMapPanel.setPreferredSize(new Dimension(260, 180));
        miniMapPanel.setVisible(showMiniMap);

        // ----- レイアウト -----
        add(treeScrollPane, BorderLayout.CENTER);
        add(miniMapPanel, BorderLayout.SOUTH);
    }

    /** 画像選択時のコールバックを設定 */
    public void setOnImageSelected(java.util.function.Consumer<String> callback) {
        this.onImageSelected = callback;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(160, 160, 180, 200));
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.drawString("Navigator", 6, 14);
        g2.dispose();
    }

    /** JTreeセルレンダラ（階層表示カスタマイズ） */
    private class NavigatorTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObj = node.getUserObject();
            if (userObj instanceof ImageNodeInfo) {
                ImageNodeInfo info = (ImageNodeInfo) userObj;
                setText("Image #" + info.instanceNumber);
                setToolTipText(info.sopInstanceUID + "\n" + info.positionStr);
            }
            return this;
        }
    }

    /** ミニマップ描画パネル（旧NavigatorPanelの機能を継承） */
    private class MiniMapView extends JPanel {
        private static final long serialVersionUID = 1L;
        public MiniMapView() { setOpaque(false); setBackground(new Color(30, 30, 30)); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!showMiniMap || thumbnailImage == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(40, 40, 40));
            g2.fillRect(0, 0, w, h);

            int imgW = thumbnailImage.getIconWidth(), imgH = thumbnailImage.getIconHeight();
            float scale = (float) Math.min((w - 4) / (double) imgW, (h - 4) / (double) imgH);
            int drawW = (int)(imgW * scale), drawH = (int)(imgH * scale);
            int xOff = (w - drawW) / 2, yOff = (h - drawH) / 2;

            g2.drawImage(thumbnailImage.getImage(), xOff, yOff, drawW, drawH, this);

            if (visibleRect != null) {
                double vrScaleX = drawW / (double) imgW;
                double vrScaleY = drawH / (double) imgH;
                int vrX = xOff + (int)(visibleRect.getX() * vrScaleX);
                int vrY = yOff + (int)(visibleRect.getY() * vrScaleY);
                int vrW = (int)(visibleRect.getWidth() * vrScaleX);
                int vrH = (int)(visibleRect.getHeight() * vrScaleY);
                g2.setColor(new Color(0, 180, 255, 120));
                g2.drawRect(vrX, vrY, vrW, vrH);
                g2.setColor(Color.CYAN);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRect(vrX + 1, vrY + 1, vrW - 2, vrH - 2);
            }

            g2.setColor(new Color(200, 200, 200, 180));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g2.drawString("Mini-map", 4, h - 4);
            g2.dispose();
        }
    }

    /** ツリーノードに紐づく画像情報（内部データ構造） */
    private static class ImageNodeInfo {
        final String sopInstanceUID;
        final int instanceNumber;
        final String positionStr;
        final String filePath;
        public ImageNodeInfo(String sopUID, int instNum, String posStr, String path) {
            this.sopInstanceUID = sopUID; this.instanceNumber = instNum;
            this.positionStr = posStr; this.filePath = path;
        }
    }

    /** DCMView に統合するためのヘルパー */
    public static NavigatorPanel createForDCMView() {
        return new NavigatorPanel();
    }
}
