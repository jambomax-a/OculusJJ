package com.oculus.jj.model;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.concurrent.*;
import java.util.*;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TileLoader - 画像データの非同期読み込みとキャッシュ更新を管理するクラス。
 * HOROS-202407準拠: BrowserController内でのバックグラウンドスレッドを用いた画像取得処理を統合。
 */
public class TileLoader {
    private static final Logger logger = LoggerFactory.getLogger(TileLoader.class);

    // 画像読み込み用の固定スレッドプール
    private static final ExecutorService loadExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2);

    // --- HOROS-202407準拠: 画像キャッシュ (LRU) ---
    private static final int CACHE_SIZE = 50; // 最大50枚まで
    private static final LinkedHashMap<String, BufferedImage> imageCache = new LinkedHashMap<String, BufferedImage>(
            CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, BufferedImage> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /**
     * 指定された画像のデータを非同期でロードし、コールバックを呼び出す。
     * 
     * @param imageId    イメージの識別子
     * @param path       ファイルパス
     * @param onComplete ロード完了時のUI更新処理
     */
    public static void loadTileAsync(String imageId, String path,
            java.util.function.Consumer<BufferedImage> onComplete) {
        // キャッシュヒットチェック
        synchronized (imageCache) {
            if (imageCache.containsKey(imageId)) {
                BufferedImage cached = imageCache.get(imageId);
                onComplete.accept(cached);
                return;
            }
        }

        loadExecutor.submit(() -> {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    onComplete.accept(null);
                    return;
                }

                // HOROS-202407準拠: 標準ImageIOではなくできるだけDICOMとして読み込む
                // (DICOMファイルならDicomPix経由で、そうでければImageIOで読み込む)
                BufferedImage img = null;
                if (path.toLowerCase().endsWith(".dcm")) {
                    // DicomPixを使用してヘッダーとピクセルを読み込む
                    DicomPix dcm = new DicomPix(path, 0, 1, 0, "", false, null);
                    dcm.readHeader();
                    // ピクセルデータ取得
                    img = dcm.getBufferedImage();
                } else {
                    img = ImageIO.read(file);
                }

                if (img != null) {
                    final BufferedImage finalImg = img;
                    synchronized (imageCache) {
                        imageCache.put(imageId, finalImg);
                    }
                    // UIを更新するためSwingUtilities.invokeLaterで実行
                    javax.swing.SwingUtilities.invokeLater(() -> onComplete.accept(finalImg));
                } else {
                    onComplete.accept(null);
                }
            } catch (Exception e) {
                logger.error("Failed to load tile: {}", imageId, e);
                onComplete.accept(null);
            }
        });
    }

    /**
     * キャッシュをクリアする
     */
    public static void clearCache() {
        synchronized (imageCache) {
            imageCache.clear();
        }
    }

    /**
     * スレッドプールのシャットダウン処理。
     */
    public static void shutdown() {
        loadExecutor.shutdown();
    }
}
