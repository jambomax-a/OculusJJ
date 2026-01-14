package com.jj.dicomviewer.ui;

import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.List;

import com.jj.dicomviewer.model.DicomPix;

/**
 * PreviewView - プレビュー画像表示ビュー
 * 
 * HOROS-20240407/Horos/Sources/PreviewView.h
 * HOROS-20240407/Horos/Sources/PreviewView.m
 * の写経
 * 
 * DCMViewを継承し、BrowserController用のプレビュー表示を提供
 */
public class PreviewView extends DCMView {

    // HOROS: BrowserControllerへの参照
    private BrowserController browserController;

    /**
     * HOROS: - (id) initWithFrame:(NSRect)frame
     */
    public PreviewView() {
        super();

        // HOROS: マウスホイールリスナー
        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                scrollWheel(e);
            }
        });

        // HOROS-20240407準拠: DCMView.m 4319-4322行目
        // if( clickCount == 2 && [self window] == [[BrowserController currentBrowser]
        // window])
        // {
        // [[BrowserController currentBrowser] matrixDoublePressed:nil];
        // }
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && browserController != null) {
                    // HOROS-20240407準拠: プレビュー画面（BrowserControllerのwindow）でのダブルクリック時に
                    // matrixDoublePressedを呼び出す
                    browserController.matrixDoublePressed(null);
                }
            }
        });
    }

    /**
     * HOROS: BrowserControllerを設定
     * HOROS-20240407準拠: PreviewView.mにはsetBrowserControllerメソッドは存在しない
     * しかし、scrollWheelで使用するため、browserControllerフィールドは保持
     */
    public void setBrowserController(BrowserController controller) {
        this.browserController = controller;
        // HOROS-20240407準拠: DCMViewはBrowserController.currentBrowser()を使用するため、
        // super.setBrowserController()の呼び出しは不要
    }

    /**
     * HOROS-20240407準拠: - (void) setTheMatrix:(NSMatrix *)matrix
     * BrowserController.m 行14250: [imageView setTheMatrix:oMatrix];
     * マトリックスを設定（DCMViewには存在しないため、ここで実装）
     */
    private Object theMatrix;

    public void setTheMatrix(Object matrix) {
        this.theMatrix = matrix;
    }

    public Object getTheMatrix() {
        return theMatrix;
    }

    /**
     * HOROS: - (BOOL)is2DViewer
     * PreviewView.m 行72
     */
    public boolean is2DViewer() {
        return false;
    }

    /**
     * インデックスを設定
     * HOROS-20240407準拠: - (void)setIndex:(int)index (DCMView.m)
     * プレビュー画面では常にFIT処理を行うためにオーバーライド
     */
    @Override
    public void setIndex(int index) {
        // HOROS-20240407準拠: DCMViewのsetIndexを呼び出す
        super.setIndex(index);
        // プレビュー画面では、画像が切り替わるたびにFIT処理を確実に行う
        scaleToFit();
    }

    /**
     * HOROS: - (void) scrollWheel:(NSEvent *)theEvent
     * BrowserControllerに委譲
     * HOROS-20240407準拠: PreviewView.m のscrollWheel実装
     */
    @Override
    protected void scrollWheel(java.awt.event.MouseWheelEvent e) {
        if (browserController != null) {
            // HOROS-20240407準拠: BrowserControllerのscrollWheelメソッドに委譲
            // wheelRotationは通常、上にスクロールで負の値、下にスクロールで正の値
            // HOROSのdeltaYに相当
            browserController.previewScrollWheel(e.getWheelRotation());
        }
    }

    /**
     * setPixels - ピクセルデータを設定（簡易版）
     * HOROS-20240407準拠: BrowserController.m 9619行目
     */
    public void setPixels(List<DicomPix> pixels) {
        setPixels(pixels, null, null, (short) 0, 'i', true);
    }

    /**
     * setPixels - ピクセルデータを設定（完全版）
     * HOROS-20240407準拠: DCMView.m 2176行目
     */
    @Override
    public void setPixels(List<DicomPix> pixels, List<Object> files, List<Object> rois, short firstImage, char level,
            boolean reset) {
        // HOROS-20240407準拠: DCMViewのsetPixelsを呼び出す
        super.setPixels(pixels, files, rois, firstImage, level, reset);
    }
}
