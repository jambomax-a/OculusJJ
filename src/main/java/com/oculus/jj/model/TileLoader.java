package com.oculus.jj.model;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TileLoader - 画像データの非同期読み込みとキャッシュ更新を管理するクラス。
 * HOROS-202407準拠: BrowserController内でのバックグラウンドスレッドを用いた画像取得処理を統合。
 */
public class TileLoader {
    private static final Logger logger = LoggerFactory.getLogger(TileLoader.class);
    
    // 画像読み込み用の固定スレッドプール（CPUコア数に基づき調整）
    private static final ExecutorService loadExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() * 2
    );

    /**
     * 指定された画像のデータを非同期でロードし、コールバックを呼び出す。
     * @param imageId イメージの識別子
     * @param onComplete ロード完了時のUI更新処理（EDTで実行されることを期待）
     */
    public static void loadTileAsync(String imageId, String path, java.util.function.Consumer<BufferedImage> onComplete) {
        loadExecutor.submit(() -> {
            try {
                File file = new File(path);
                if (!file.exists()) {
                    onComplete.accept(null); // ファイルがない場合はnullを返す
                    return;
                }

                // HOROS-202407準拠: 画像の読み込み（DicomImageから取得したパスを使用）
                BufferedImage img = ImageIO.read(file);
                
                if (img != null) {
                    // UIを更新するためSwingUtilities.invokeLaterで実行
                    javax.swing.SwingUtilities.invokeLater(() -> onComplete.accept(img));
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
     * スレッドプールのシャットダウン処理。
     */
    public static void shutdown() {
        loadExecutor.shutdown();
    }
}
