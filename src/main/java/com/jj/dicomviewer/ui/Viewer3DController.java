package com.jj.dicomviewer.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;
import com.jj.dicomviewer.model.DicomPix;

/**
 * Viewer3DController - 3Dビューワー (MIP/VR)
 * HOROS-20240407準拠: VRController.h, VRController.m 相当
 */
public class Viewer3DController extends JFrame {

    private ViewerController viewer2D;
    private List<DicomPix> pixList;
    private short[][][] volume; // [z][y][x]
    private int dimX, dimY, dimZ;
    private double spacingX = 1.0, spacingY = 1.0, spacingZ = 1.0;

    private RenderingPanel renderingPanel;
    private String mode = "MIP"; // "MIP", "VR"

    // 視点回転パラメータ
    private double rotateX = 0;
    private double rotateY = 0;

    public Viewer3DController(ViewerController viewer2D, String mode) {
        super("3D Viewer - " + mode);
        this.viewer2D = viewer2D;
        this.mode = mode;

        initializeData();
        initializeUI();
    }

    private void initializeData() {
        // ViewerControllerからピクセルリストを取得
        // 簡易的に最初のシリーズを使用
        List<List<DicomPix>> allPix = viewer2D.getPixList();
        if (allPix != null && !allPix.isEmpty()) {
            this.pixList = new ArrayList<>(allPix.get(0));
        }

        if (pixList == null || pixList.isEmpty())
            return;

        // ボリューム構築
        DicomPix first = pixList.get(0);
        dimX = first.getWidth();
        dimY = first.getHeight();
        dimZ = pixList.size();

        spacingX = first.getPixelSpacingX();
        spacingY = first.getPixelSpacingY();
        spacingZ = first.getSliceThickness(); // 簡易的にSliceThicknessを使用

        volume = new short[dimZ][dimY][dimX];
        for (int z = 0; z < dimZ; z++) {
            DicomPix pix = pixList.get(z);
            short[] src = pix.getShortData();
            if (src != null) {
                for (int y = 0; y < dimY; y++) {
                    System.arraycopy(src, y * dimX, volume[z][y], 0, dimX);
                }
            }
        }
    }

    private void initializeUI() {
        // HOROS-20240407準拠: 3D Viewerも大画面または全画面で表示
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // HOROS-20240407準拠: 3D Viewerも独立したメニューバーを持つ（Windows環境）
        BrowserController browser = BrowserController.currentBrowser();
        if (browser != null && browser.getJMenuBar() != null) {
            setJMenuBar(ViewerController.replicateMenuBar(browser.getJMenuBar()));
        }

        setLayout(new BorderLayout());

        // HOROS-20240407準拠: VR.xib 相当のツールバー
        JToolBar vrToolbar = new JToolBar();
        vrToolbar.setFloatable(false);
        vrToolbar.setBackground(new Color(240, 240, 240));

        vrToolbar.add(new JButton("Reset"));
        vrToolbar.add(new JButton("Shading"));
        vrToolbar.add(new JButton("Projection"));
        vrToolbar.add(new JButton("Movie"));
        add(vrToolbar, BorderLayout.NORTH);

        renderingPanel = new RenderingPanel();
        add(renderingPanel, BorderLayout.CENTER);

        // キーボードショートカット (Horos準拠: ESCで閉じるなど)
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                }
            }
        });
    }

    /**
     * 3Dレンダリング用パネル
     */
    private class RenderingPanel extends JPanel {
        private Point lastMousePos;

        public RenderingPanel() {
            setBackground(Color.BLACK);

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMousePos = e.getPoint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (lastMousePos != null) {
                        int dx = e.getX() - lastMousePos.x;
                        int dy = e.getY() - lastMousePos.y;

                        rotateY += dx * 0.01;
                        rotateX += dy * 0.01;

                        lastMousePos = e.getPoint();
                        repaint();
                    }
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (volume == null)
                return;

            render3D((Graphics2D) g);
        }

        private void render3D(Graphics2D g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0)
                return;

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

            // 簡易的なレイキャスティング (MIP)
            // 数学的な最適化は省き、コンセプトを示す
            double sinX = Math.sin(rotateX);
            double cosX = Math.cos(rotateX);
            double sinY = Math.sin(rotateY);
            double cosY = Math.cos(rotateY);

            // ボリューム中心
            double cx = dimX / 2.0;
            double cy = dimY / 2.0;
            double cz = dimZ / 2.0;

            // スケーリング
            double scale = Math.min(w / (double) dimX, h / (double) dimY) * 0.8;

            for (int sy = 0; sy < h; sy += 2) { // パフォーマンスのためステップを荒く
                for (int sx = 0; sx < w; sx += 2) {

                    double vx = (sx - w / 2.0) / scale;
                    double vy = (sy - h / 2.0) / scale;

                    int maxVal = 0;

                    // レイのサンプリング
                    for (double vz = -cz; vz < cz; vz += 1.0) {
                        // 回転適用
                        // Y軸回転
                        double tx = vx * cosY + vz * sinY;
                        double tz = -vx * sinY + vz * cosY;

                        // X軸回転
                        double ty = vy * cosX - tz * sinX;
                        double finalZ = vy * sinX + tz * cosX;

                        int ix = (int) (tx + cx);
                        int iy = (int) (ty + cy);
                        int iz = (int) (finalZ + cz);

                        if (ix >= 0 && ix < dimX && iy >= 0 && iy < dimY && iz >= 0 && iz < dimZ) {
                            int val = volume[iz][iy][ix] & 0xFFFF;
                            if (val > maxVal)
                                maxVal = val;
                        }
                    }

                    if (maxVal > 0) {
                        // 簡易的な階調変換 (TODO: WLWW適用)
                        int c = Math.min(255, maxVal / 10);
                        int rgb = (c << 16) | (c << 8) | c;
                        img.setRGB(sx, sy, rgb);
                        img.setRGB(sx + 1, sy, rgb);
                        img.setRGB(sx, sy + 1, rgb);
                        img.setRGB(sx + 1, sy + 1, rgb);
                    }
                }
            }

            g.drawImage(img, 0, 0, null);
        }
    }

    public static void openMIP(ViewerController viewer) {
        SwingUtilities.invokeLater(() -> {
            Viewer3DController v3d = new Viewer3DController(viewer, "MIP");
            v3d.setVisible(true);
        });
    }

    public static void openVR(ViewerController viewer) {
        SwingUtilities.invokeLater(() -> {
            Viewer3DController v3d = new Viewer3DController(viewer, "VR");
            v3d.setVisible(true);
        });
    }
}
