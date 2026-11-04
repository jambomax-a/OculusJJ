package com.oculus.jj;

import com.oculus.jj.model.DicomDatabase;
import com.oculus.jj.ui.BrowserController;
import com.oculus.jj.config.AppPreferences;
import com.oculus.jj.utils.LanguageManager;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.file.*;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.logging.Logger;

/**
 * CD/DVD ディスク挿入検出リスナー
 * HOROS-20240407準拠: BrowserController.m 1860-1892行目
 * Disc insertion detection via FileSystemListener (java.nio.file.spi.FileSystemProvider)
 * <p>
 * Note: Full filesystem watcher support requires JDK 7+ and may not work on all platforms
 * (e.g., Windows autoplay, macOS disk images). This is a best-effort implementation.
 */
public final class DiscWatcher {

    private static final Logger logger = Logger.getLogger(DiscWatcher.class.getName());

    // モード: 0=コピーする, 1=ソースとして表示, 2=無視する
    // HOROS準拠: cddvd.insertMode (0: Copy all, 1: Show as separate source, 2: Ignore)
    private static final int MODE_COPY = 0;
    private static final int MODE_SHOW = 1;
    private static final int MODE_IGNORE = 2;

    // デフォルトは cddvd.insertMode の既定値（2: Ignore）に合わせる
    private static volatile int currentMode = MODE_IGNORE;

    // ディレクトリ監視用の WatchService（利用可能な場合）
    private static WatchService watchService;
    private static Path rootPath;
    private static boolean initialized = false;

    /**
     * WatchService を初期化する。
     * HOROS と同様に動作するが、ポーリングフォールバック付き。
     */
    public static void initialize() {
        if (initialized) return;
        initialized = true;

        Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(DiscWatcher.class);
        currentMode = prefs.getInt("insertMode", MODE_IGNORE);
        rootPath = Path.of(System.getProperty("user.dir"));

        try {
            watchService = FileSystems.getDefault().newWatchService();
            logger.fine("DiscWatcher WatchService initialized on " + rootPath);
        } catch (Exception e) {
            // WatchService がサポートされていない場合は無視（ポーリングフォールバック）
            logger.fine("DiscWatcher WatchService not available, will use polling fallback: " + e.getMessage());
            watchService = null;
        }

        // 初回チェック
        checkForDiscs();

        // 定期チェック用タイマーを開始（1秒間隔）
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> checkForDiscs());
        timer.start();
    }

    /**
     * ディスクの有無をチェックする。
     * HOROS-20240407準拠: BrowserController.m 1860-1892行目相当の動作
     */
    private static void checkForDiscs() {
        try {
            if (!Files.isDirectory(rootPath)) return;

            // ルートディレクトリ直下のボリューム/ドライブをスキャン
            // (CD/DVDドライブは通常、user.dir の親やシステム領域にマウントされる)
            Path parent = rootPath.getParent();
            if (parent != null) {
                scanDirectory(parent);
            }
            // user.dir 自体もチェック（USBドライブ等）
            scanDirectory(rootPath);
        } catch (Exception e) {
            logger.fine("Error checking discs: " + e.getMessage());
        }
    }

    /** ディレクトリ内の DICOM ファイルを検出 */
    private static void scanDirectory(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try {
            FileSystem fs = FileSystems.getDefault();
            // このファイルシステムのスキャン対象フラグを確認
            if (!shouldScanDirectory(dir)) return;

            List<Path> dicomFiles = new ArrayList<>();
            try {
                Files.walk(dir)
                        .filter(p -> Files.isRegularFile(p))
                        .forEach(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            // DICOMファイルの拡張子チェック
                            if (name.endsWith(".dcm") || name.endsWith(".dicom") ||
                                    name.endsWith(".ima") || name.endsWith(".img") ||
                                    (!name.contains(".") && p.getNameCount() < 5)) {
                                dicomFiles.add(p);
                            }
                        });
            } catch (Exception ignored) {
            }

            if (!dicomFiles.isEmpty()) {
                // HOROS準拠: 「すべての内容をデータベースにコピー」 または 「ソースとして表示」
                int mode = AppPreferences.getInt("cddvd.insertMode", MODE_IGNORE);
                if (mode == MODE_COPY || mode == MODE_SHOW) {
                    String discLabel = getDiscLabel(dir);
                    String msg = discLabel != null
                            ? LanguageManager.get("cddvd.disc_inserted_with_label",
                                    "Disc inserted: {0}").formatted(discLabel)
                            : LanguageManager.get("cddvd.disc_inserted",
                                    "Disc inserted");

                    // UI スレッドで表示
                    SwingUtilities.invokeLater(() -> {
                        int choice = JOptionPane.showConfirmDialog(
                                BrowserController.currentBrowser(),
                                msg,
                                LanguageManager.get("cddvd.disc_title", "Disc Inserted"),
                                JOptionPane.YES_NO_CANCEL_OPTION);

                        if (choice == JOptionPane.YES_OPTION && mode == MODE_COPY) {
                            // 「すべての内容をデータベースにコピー」
                            // addFilesAndFolderToDatabase と同様の処理
                            importDiscContents(dir);
                        } else if (choice == JOptionPane.NO_OPTION && mode == MODE_SHOW) {
                            // 「ソースとして表示」: ブラウザの一覧に追加
                            // ディレクトリパスを List<String> に変換して addFilesAndFolderToDatabase を呼び出し
                            BrowserController.currentBrowser().addFilesAndFolderToDatabase(
                                    java.util.Collections.singletonList(dir.toString()));
                        }
                    });
                }
            }
        } catch (Exception e) {
            logger.fine("Error scanning directory " + dir + ": " + e.getMessage());
        }
    }

    private static String getDiscLabel(Path dir) {
        try {
            // ディレクトリ名をラベルとして使用、または volume name を取得試行
            String name = dir.getFileName().toString();
            if (!name.isEmpty()) return name;
            // volume serial number 等を取得試行（プラットフォーム依存）
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void importDiscContents(Path dir) {
        try {
            List<String> filePaths = new ArrayList<>();
            Files.walk(dir)
                    .filter(p -> Files.isRegularFile(p))
                    .forEach(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        if (name.endsWith(".dcm") || name.endsWith(".dicom") ||
                                name.endsWith(".ima") || name.endsWith(".img")) {
                            filePaths.add(p.toString());
                        }
                    });

            if (!filePaths.isEmpty()) {
                BrowserController.currentBrowser().addFilesAndFolderToDatabase(filePaths);
            }
        } catch (Exception e) {
            logger.fine("Error importing disc contents: " + e.getMessage());
        }
    }

    /**
     * ディレクトリがスキャン対象か確認する（CD/DVD ドライブの検出簡易版）
     */
    private static boolean shouldScanDirectory(Path dir) {
        try {
            String name = dir.getFileName().toString().toLowerCase();
            // CD/DVDドライブの可能性がある名前（Mac: /Volumes/xxx, Windows: D:, E: 等）
            // ここでは単に存在するすべてのディレクトリをスキャンする簡易実装
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * アクティブなビューアー（存在する場合）を取得
     */
    private static BrowserController getActiveBrowser() {
        // BrowserController には getActiveViewer() がないため、
        // 代わりに currentBrowser() (single instance) を使用
        return BrowserController.currentBrowser();
    }
}
