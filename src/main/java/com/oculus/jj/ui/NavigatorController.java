package com.oculus.jj.ui;

import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import com.oculus.jj.model.DicomPix;

import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

/**
 * NavigatorController - HOROS NavigatorWindowController 相当。
 * NavigatorPanel を含む浮遊パネル (JDialog) の lifecycle と配置を管理する。
 */
public class NavigatorController {

    // HOROS-20240407準拠: static NavigatorWindowController *nav = nil;
    private static NavigatorController navInstance = null;

    /** DCMView からのサムネイル更新通知リスナー */
    public interface ThumbnailRefreshListener {
        void onThumbnailRefresh(java.awt.image.BufferedImage[] allPics, int activeIndex);
    }

    /** ホスト画面 (BrowserController) との紐付け */
    private JDialog dialog;
    private NavigatorPanel navigatorPanel;
    private JScrollPane scrollPane;

    private NavigatorController() {
        buildUI();
    }

    /** static singleton access (HOROS準拠) */
    public static NavigatorController navigatorWindowController() {
        if (navInstance == null) {
            navInstance = new NavigatorController();
        }
        return navInstance;
    }

    // HOROS-20240407準拠: - (void)initView
    private void buildUI() {
        navigatorPanel = new NavigatorPanel();
        scrollPane = new JScrollPane(navigatorPanel);
        scrollPane.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        dialog = new JDialog((java.awt.Frame) null, "Navigator");
        dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        dialog.setUndecorated(false);
        dialog.setAlwaysOnTop(true);
        dialog.add(scrollPane);
        dialog.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // リサイズ時に NavigatorPanel を更新
            }
        });
    }

    /** ダイアログを表示 (HOROS準拠: - showWindow:) */
    public void showWindow(java.awt.Frame owner) {
        if (!dialog.isVisible()) {
            if (owner != null) {
                dialog.setLocationRelativeTo(owner);
            } else {
                positionAtBottomOfScreen();
            }
            updateNavigator(null);
            dialog.setVisible(true);
        }
    }

    /** ダイアログを閉じる */
    public boolean isShowing() {
        return dialog != null && dialog.isVisible();
    }

    public void closeWindow() {
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
        }
    }

    // HOROS-20240407準拠: - (void)setViewer: / [self updateNavigator]
    /**
     * NavigatorPanel に全スライスとアクティブ位置を伝える。
     */
    public void updateNavigator(DCMView dcmView) {
        if (navigatorPanel == null) return;

        if (dcmView != null && dcmView.getDcmPixList() != null && !dcmView.getDcmPixList().isEmpty()) {
            List<DicomPix> pixList = dcmView.getDcmPixList();
            int size = pixList.size();
            BufferedImage[] pics = new BufferedImage[size];
            for (int i = 0; i < size; i++) {
                DicomPix p = pixList.get(i);
                if (p != null) {
                    pics[i] = p.getBufferedImageNoLoad();
                } else {
                    pics[i] = null;
                }
            }
            navigatorPanel.setThumbnails(pics, dcmView.curImage);
        }

        positionAtBottomOfScreen();
    }

    private void positionAtBottomOfScreen() {
        if (dialog == null) return;
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle bounds = gd.getDefaultConfiguration().getBounds();
        Toolkit tk = Toolkit.getDefaultToolkit();
        java.awt.Insets insets = tk.getScreenInsets(gd.getDefaultConfiguration());

        int width = bounds.width - insets.left - insets.right;
        int height = Math.min(bounds.height, 150);
        dialog.setBounds(bounds.x + insets.left,
                         bounds.y + insets.top + bounds.height - height - insets.bottom,
                         width, height);
    }

    /** Dialog の幅を画面可視領域に合わせて調整 */
    public void updateWindowSize(java.awt.Frame owner) {
        if (dialog == null || owner == null) return;
        Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getBounds();
        java.awt.Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(owner.getGraphicsConfiguration());

        int width = screenBounds.width - insets.left - insets.right;
        dialog.setSize(width, dialog.getHeight());
    }
}