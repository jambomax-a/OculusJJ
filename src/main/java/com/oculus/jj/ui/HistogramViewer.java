package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * HistogramViewer - 画像ヒストグラム表示コンポーネント
 * HOROS-20240407準拠: DCMHistogramView 相当
 * 
 * DICOMピクセル値（HU値など）の分布をリアルタイムで可視化し、
 * ウィンドウレベル（WL/WW）の設定を支援する機能を提供します。
 */
public class HistogramViewer extends JPanel {

    private MPRController controller;
    private int[] histogram; // ヒストグラムデータ（256階調）
    private double dataMin, dataMax; // データの最小値・最大値
    private boolean hasData = false;

    // ウィンドウレベル表示用
    private float windowLevel;
    private float windowWidth;

    // 色設定
    private static final Color BG_COLOR = new Color(30, 30, 30);
    private static final Color HISTOGRAM_COLOR = Color.WHITE;
    private static final Color WL_LINE_COLOR = Color.YELLOW;
    private static final Color WW_RANGE_COLOR = new Color(255, 255, 0, 80);
    private static final Color TEXT_COLOR = Color.LIGHT_GRAY;

    public HistogramViewer(MPRController controller) {
        this.controller = controller;
        this.histogram = new int[256];
        this.windowLevel = controller.getWindowLevel();
        this.windowWidth = controller.getWindowWidth();
        setPreferredSize(new Dimension(300, 150));
        setBackground(BG_COLOR);
    }

    /**
     * ヒストグラムデータを計算して更新する
     */
    public void updateHistogram() {
        if (controller == null) return;

        // MPRビューからピクセル値を取得してヒストグラムを計算
        int[] tempHist = new int[256];
        double minVal = Double.MAX_VALUE;
        double maxVal = -Double.MAX_VALUE;

        // 各MPRビューの現在表示されているスライスからサンプリング
        List<MPRView> views = controller.getAllViews();
        for (MPRView view : views) {
            BufferedImage img = view.getCurrentImage();
            if (img == null) continue;

            int w = img.getWidth(), h = img.getHeight();
            for (int y = 0; y < h; y += 4) { // パフォーマンスのため4ピクセルごと
                for (int x = 0; x < w; x += 4) {
                    int rgb = img.getRGB(x, y);
                    int gray = (rgb >> 16) & 0xFF;
                    
                    if (gray >= 0 && gray < 256) {
                        tempHist[gray]++;
                    }

                    // データ範囲を更新（簡易的にグレースケール値を使用）
                    double val = controller.getInterpolatedVoxel(
                        controller.getCurX(), controller.getCurY(), controller.getCurZ());
                    if (val < minVal) minVal = val;
                    if (val > maxVal) maxVal = val;
                }
            }
        }

        this.histogram = tempHist;
        this.dataMin = minVal;
        this.dataMax = maxVal;
        this.hasData = true;
        
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // 背景
        g2.setColor(BG_COLOR);
        g2.fillRect(0, 0, getWidth(), getHeight());

        if (!hasData || histogram == null) {
            g2.setColor(TEXT_COLOR);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            String noDataMsg = com.oculus.jj.utils.LanguageManager.get("histogram.no_data", "No data");
            int x = getWidth() / 2 - g2.getFontMetrics().stringWidth(noDataMsg) / 2;
            int y = getHeight() / 2 + 5;
            g2.drawString(noDataMsg, x, y);
            return;
        }

        // ヒストグラム描画領域を計算
        int paddingX = 40;
        int paddingY = 20;
        int histWidth = getWidth() - paddingX * 2;
        int histHeight = getHeight() - paddingY * 2;

        if (histWidth <= 0 || histHeight <= 0) return;

        // ヒストグラムを描画
        g2.setColor(HISTOGRAM_COLOR);
        g2.setStroke(new BasicStroke(1.0f));

        int maxCount = 0;
        for (int count : histogram) {
            if (count > maxCount) maxCount = count;
        }

        // ヒストグラムバーを描画
        Path2D path = new Path2D.Double();
        path.moveTo(paddingX, paddingY + histHeight);

        for (int i = 0; i < 256; i++) {
            int barWidth = Math.max(1, histWidth / 256);
            double barHeight = maxCount > 0 ? (histogram[i] / (double) maxCount) * histHeight : 0;
            int x = paddingX + i * barWidth;
            
            if (i == 0) {
                path.lineTo(x, paddingY + histHeight);
            }
            path.lineTo(x, paddingY + histHeight - (int) barHeight);
        }

        path.lineTo(paddingX + histWidth, paddingY + histHeight);
        path.closePath();
        g2.fill(path);

        // WL/WW 表示
        drawWLWWOverlay(g2, paddingX, paddingY, histWidth, histHeight);

        // ラベル
        g2.setColor(TEXT_COLOR);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        
        // X軸ラベル
        String xAxisLabel = com.oculus.jj.utils.LanguageManager.get("histogram.x_axis", "Pixel Value");
        int labelX = getWidth() / 2 - g2.getFontMetrics().stringWidth(xAxisLabel) / 2;
        g2.drawString(xAxisLabel, labelX, getHeight() - 5);

        // Y軸ラベル（回転）
        String yAxisLabel = com.oculus.jj.utils.LanguageManager.get("histogram.y_axis", "Count");
        Graphics2D g2Rotated = (Graphics2D) g2.create();
        g2Rotated.translate(12, getHeight() / 2);
        g2Rotated.rotate(-Math.PI / 2);
        g2Rotated.drawString(yAxisLabel, 0, 0);
        g2Rotated.dispose();

        // ウィンドウ情報（右上）
        String wlwwInfo = String.format("WL: %.0f WW: %.0f", windowLevel, windowWidth);
        Font infoFont = new Font("Monospaced", Font.BOLD, 11);
        g2.setFont(infoFont);
        g2.setColor(WL_LINE_COLOR);
        
        int infoX = getWidth() - paddingX - g2.getFontMetrics().stringWidth(wlwwInfo) - 5;
        int infoY = paddingY + 15;
        g2.drawString(wlwwInfo, infoX, infoY);
    }

    /**
     * WL/WWオーバーレイを描画する
     */
    private void drawWLWWOverlay(Graphics2D g2, int px, int py, int w, int h) {
        if (dataMin == Double.MAX_VALUE || dataMax == -Double.MAX_VALUE) return;

        double range = dataMax - dataMin;
        if (range <= 0) range = 1.0;

        // WL（中央値）の位置を計算
        double wlPos = ((windowLevel - dataMin) / range) * w + px;
        
        // WW（幅）の範囲を計算
        double wwHalf = windowWidth / 2.0;
        double wwStart = ((windowLevel - wwHalf - dataMin) / range) * w + px;
        double wwEnd = ((windowLevel + wwHalf - dataMin) / range) * w + px;

        // WW範囲を半透明で塗りつぶし
        g2.setColor(WW_RANGE_COLOR);
        g2.fillRect((int)Math.max(px, wwStart), py, (int)(wwEnd - wwStart), h);

        // WL線を黄色で描画
        g2.setColor(WL_LINE_COLOR);
        g2.setStroke(new BasicStroke(2.0f));
        if (wlPos >= px && wlPos <= px + w) {
            g2.drawLine((int)wlPos, py, (int)wlPos, py + h);
        }

        // WW端線を描画（薄い色）
        g2.setColor(new Color(255, 255, 0, 100));
        g2.setStroke(new BasicStroke(1.0f));
        if (wwStart >= px && wwStart <= px + w) {
            g2.drawLine((int)wwStart, py, (int)wwStart, py + h);
        }
        if (wwEnd >= px && wwEnd <= px + w) {
            g2.drawLine((int)wwEnd, py, (int)wwEnd, py + h);
        }
    }

    /**
     * 現在のウィンドウレベルを取得する
     */
    public float getWindowLevel() {
        return windowLevel;
    }

    /**
     * 現在のウィンドウ幅を取得する
     */
    public float getWindowWidth() {
        return windowWidth;
    }

    /**
     * ウィンドウレベルを設定して更新
     */
    public void setWindowLevel(float level) {
        this.windowLevel = level;
        if (controller != null) {
            controller.setWindowLevel(level);
        }
        repaint();
    }

    /**
     * ウィンドウ幅を設定して更新
     */
    public void setWindowWidth(float width) {
        this.windowWidth = width;
        if (controller != null) {
            controller.setWindowWidth(width);
        }
        repaint();
    }

    /**
     * ヒストグラムデータを取得する
     */
    public int[] getHistogram() {
        return histogram;
    }

    /**
     * データの最小値を取得する
     */
    public double getDataMin() {
        return dataMin;
    }

    /**
     * データの最大値を取得する
     */
    public double getDataMax() {
        return dataMax;
    }

    /**
     * 統計情報を計算して返す（HOROS準拠）
     */
    public ROIStatistics calculateStatistics() {
        if (!hasData || histogram == null) {
            return new ROIStatistics(0, 0, 0, 0, 0);
        }

        double sum = 0;
        double sumSq = 0;
        int totalCount = 0;
        float minVal = Float.MAX_VALUE;
        float maxVal = -Float.MAX_VALUE;

        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] > 0) {
                double midPoint = (i - 127.5); // グレースケールをHU値近似に変換
                sum += midPoint * histogram[i];
                sumSq += midPoint * midPoint * histogram[i];
                totalCount += histogram[i];

                if (i < minVal) minVal = i;
                if (i > maxVal) maxVal = i;
            }
        }

        double mean = totalCount > 0 ? sum / totalCount : 0;
        double variance = totalCount > 1 ? (sumSq - sum * sum / totalCount) / (totalCount - 1) : 0;
        double stdDev = Math.sqrt(Math.max(0, variance));

        return new ROIStatistics((float)mean, maxVal, minVal, (float)stdDev, totalCount);
    }

    /**
     * ROI統計情報クラス（HOROS準拠）
     */
    public static class ROIStatistics {
        private float mean;
        private float stdDev;
        private float min;
        private float max;
        private int count;

        public ROIStatistics(float mean, float max, float min, float stdDev, int count) {
            this.mean = mean;
            this.stdDev = stdDev;
            this.min = min;
            this.max = max;
            this.count = count;
        }

        public float getMean() { return mean; }
        public float getStdDev() { return stdDev; }
        public float getMin() { return min; }
        public float getMax() { return max; }
        public int getCount() { return count; }

        @Override
        public String toString() {
            return String.format("Mean: %.2f, SD: %.2f, Min: %.1f, Max: %.1f (n=%d)",
                mean, stdDev, min, max, count);
        }
    }
}
