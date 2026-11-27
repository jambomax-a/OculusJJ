package com.oculus.jj.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.AWTEvent;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * アプリケーションアイコンおよびブランドロゴの読み込み・適用を一元管理するユーティリティ。
 *
 * <p>ウィンドウ・タスクバー・Dock 用のアプリアイコン（Oculus Lens）と、
 * スプラッシュ画面・About ダイアログ用のロゴ（Veterinary X-Ray）を提供する。</p>
 */
public final class AppIconManager {

    private static final int[] APP_ICON_SIZES = { 16, 32, 48, 64, 128, 256 };
    private static List<Image> cachedAppIcons;
    private static BufferedImage cachedAppIcon512;
    private static BufferedImage cachedXRayLogo512;
    private static boolean globalHookInstalled = false;

    private AppIconManager() {
    }

    /**
     * マルチ解像度のアプリアイコンリストを返す。
     */
    public static synchronized List<Image> getAppIcons() {
        if (cachedAppIcons == null) {
            List<Image> icons = new ArrayList<>();
            for (int size : APP_ICON_SIZES) {
                BufferedImage img = loadResourceImage("/icons/app_icon_" + size + ".png");
                if (img != null) {
                    icons.add(img);
                }
            }
            cachedAppIcons = Collections.unmodifiableList(icons);
        }
        return cachedAppIcons;
    }

    /**
     * 高解像度（512px）のアプリアイコン画像を返す。
     */
    public static synchronized BufferedImage getAppIconImage() {
        if (cachedAppIcon512 == null) {
            cachedAppIcon512 = loadResourceImage("/icons/app_icon_512.png");
        }
        return cachedAppIcon512;
    }

    /**
     * 高解像度（512px）の獣医療 X-Ray ロゴ画像を返す。
     */
    public static synchronized BufferedImage getXRayLogoImage() {
        if (cachedXRayLogo512 == null) {
            cachedXRayLogo512 = loadResourceImage("/icons/xray_logo_512.png");
        }
        return cachedXRayLogo512;
    }

    /**
     * 指定サイズにスケーリングしたアプリアイコン用 {@link ImageIcon} を返す。
     */
    public static ImageIcon getAppIcon(int width, int height) {
        BufferedImage img = getAppIconImage();
        if (img == null) {
            return null;
        }
        return new ImageIcon(img.getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

    /**
     * 指定サイズにスケーリングした獣医療 X-Ray ロゴ用 {@link ImageIcon} を返す。
     */
    public static ImageIcon getXRayLogoIcon(int width, int height) {
        BufferedImage img = getXRayLogoImage();
        if (img == null) {
            return null;
        }
        return new ImageIcon(img.getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

    /**
     * 指定ウィンドウ（JFrame / JDialog 等）にマルチ解像度アプリアイコンを適用する。
     */
    public static void applyAppIcon(Window window) {
        if (window == null) {
            return;
        }
        List<Image> icons = getAppIcons();
        if (!icons.isEmpty()) {
            window.setIconImages(icons);
        }
    }

    /**
     * OS のタスクバー / Dock にアプリアイコンを設定する（対応環境のみ）。
     */
    public static void applyTaskbarIcon() {
        try {
            if (!Taskbar.isTaskbarSupported()) {
                return;
            }
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                BufferedImage img = getAppIconImage();
                if (img != null) {
                    taskbar.setIconImage(img);
                }
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // 非対応環境では無視
        }
    }

    /**
     * 今後開かれるすべてのウィンドウ（JFrame / JDialog）へ自動的にアプリアイコンを適用する。
     */
    public static synchronized void installGlobalWindowIconHook() {
        if (globalHookInstalled) {
            return;
        }
        globalHookInstalled = true;
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (event instanceof WindowEvent we && we.getID() == WindowEvent.WINDOW_OPENED) {
                    Window w = we.getWindow();
                    if (w != null && (w.getIconImages() == null || w.getIconImages().isEmpty())) {
                        applyAppIcon(w);
                    }
                }
            }, AWTEvent.WINDOW_EVENT_MASK);
        } catch (SecurityException ignored) {
            // 権限不足時は個別適用にフォールバック
        }
    }

    private static BufferedImage loadResourceImage(String path) {
        try (InputStream is = AppIconManager.class.getResourceAsStream(path)) {
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (IOException ignored) {
            // リソース未配置・読込失敗時は null を返す
        }
        return null;
    }
}
