package com.oculus.jj.model;

import java.awt.image.BufferedImage;

/**
 * FusionImageProcessor — PET/CT 融合ブレンドエンジン。
 * 
 * HOROS-style CLUT (カラールックアップテーブル) オーバーレイとアルファブレンディングに対応。
 */
public class FusionImageProcessor {

    /** 融合モード */
    public enum FusionMode {
        COLOR,   // CLUT（カラーマップ）重ね合わせ — PETをカラーでCT上に表示
        ALPHA,   // アルファ（透明度）ブレンディング — LUT画像をアルファ合成  
        GRAY     // グレースケール加算 — 単純な加重和
    }

    // ── PET用ヒートカラーマップ (256段 [R,G,B]) ──

    private static final int[] HEAT_CLUT = buildHeatCLUT();

    public static int[] getHeatCLUT() { return HEAT_CLUT.clone(); }

    private static int[] buildHeatCLUT() {
        int[] lut = new int[256 * 3];
        for (int i = 0; i < 256; i++) {
            float t = i / 255.0f;
            int r, g, b;
            if (t < 0.125f) {
                float f = t / 0.125f;
                r = 0; g = (int)(f * 80); b = (int)(f * 255);
            } else if (t < 0.375f) {
                float f = (t - 0.125f) / 0.25f;
                r = 0; g = (int)(80 + f * 175); b = (int)(255 - f * 55);
            } else if (t < 0.625f) {
                float f = (t - 0.375f) / 0.25f;
                r = (int)(f * 255); g = 255; b = (int)(200 - f * 150);
            } else if (t < 0.875f) {
                float f = (t - 0.625f) / 0.25f;
                r = 255; g = (int)(255 - f * 128); b = (int)(50 + f * 50);
            } else {
                float f = (t - 0.875f) / 0.125f;
                r = 255; g = (int)(127 + f * 128); b = (int)(100 + f * 155);
            }
            int base = i * 3;
            lut[base]     = Math.min(255, Math.max(0, r));
            lut[base + 1] = Math.min(255, Math.max(0, g));
            lut[base + 2] = Math.min(255, Math.max(0, b));
        }
        return lut;
    }

    // ── 融合コア ──

    /**
     * 2枚のDicomPixを指定した重みで融合。
     * 
     * @param base     背景画像（CTなど） — bufferedImage と shortData を持つこと
     * @param overlay  前景画像（PET/SPECTなど） — rawShortData を持つこと
     * @param weight   0.0 = baseのみ, 1.0 = overlayのみ, 0.5 = 等比
     * @param mode     融合モード
     * @return         融合結果の BufferedImage (TYPE_INT_RGB)
     */
    public static BufferedImage fuse(DicomPix base, DicomPix overlay, double weight, FusionMode mode) {
        if (base == null || overlay == null) return null;
        int w = base.getWidth();
        int h = base.getHeight();
        if (w <= 0 || h <= 0) return null;

        short[] overRaw = overlay.getShortData();
        if (overRaw == null) return null;

        weight = Math.max(0.0, Math.min(1.0, weight));
        float wBase = 1f - (float)weight;
        float wOver = (float)weight;

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] outPixels = ((java.awt.image.DataBufferInt) result.getRaster().getDataBuffer()).getData();

        switch (mode) {
            case GRAY:  blendGray(base, overRaw, outPixels, w, h, wBase, wOver); break;
            case COLOR: blendColorOverlay(overlay, outPixels, w, h, wOver);       break;
            case ALPHA: blendAlpha(base, overlay, outPixels, w, h, wBase, wOver); break;
        }

        return result;
    }

    // ── GRAY (加算ブレンディング) ──

    private static void blendGray(DicomPix base, short[] overlayRaw, int[] out,
            int w, int h, float wb, float wo) {
        
        byte[] lutB = buildWLWWLUT(base);
        byte[] lutO = buildWLWWLUTFromShort(overlayRaw);

        int baseW = base.getWidth();
        short[] baseRaw = base.getShortData();
        int baseH = baseRaw != null ? (baseRaw.length / Math.max(1, baseW)) : h;
        int overW = overlayRaw.length > 0 ? 
                    (int)Math.round(Math.sqrt((double)overlayRaw.length)) : w;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int outIdx = y * w + x;
                
                // Base pixel gray value
                int baseGray;
                if (lutB != null && baseRaw != null 
                    && x < baseW && y < baseH) {
                    int rawVal = (baseRaw[y * baseW + x] & 0xFFFF);
                    baseGray = lutB[rawVal] & 0xFF;
                } else {
                    BufferedImage img = base.getBufferedImageNoLoad();
                    if (img != null && x < img.getWidth() && y < img.getHeight()) {
                        int rgb = img.getRGB(x, y);
                        // Convert RGB to luminance
                        baseGray = ((rgb & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 
                                  + ((rgb >> 16) & 0xFF) * 114) / 1000;
                    } else {
                        baseGray = 0;
                    }
                }

                // Overlay pixel gray value (estimate using histogram normalization)
                int overIdx = y * Math.max(1, overW) + x;
                int overGray;
                if (lutO != null && overIdx < overlayRaw.length 
                    && y < baseH && x < w) {
                    int rawVal = (overlayRaw[overIdx] & 0xFFFF);
                    overGray = lutO[rawVal] & 0xFF;
                } else {
                    overGray = 0;
                }

                int blended = clamp((int)(baseGray * wb + overGray * wo), 0, 255);
                out[outIdx] = (blended << 16) | (blended << 8) | blended;
            }
        }
    }

    // ── COLOR (CLUT オーバーレイ — PETのカラー画像をCTに重ねる) ──

    private static void blendColorOverlay(DicomPix overlay, int[] out, int w, int h, float wo) {
        short[] overRaw = overlay.getShortData();
        if (overRaw == null) return;

        // Compute min/max from overlay raw for dynamic CLUT normalization
        int minVal = Short.MAX_VALUE, maxVal = Short.MIN_VALUE;
        for (int i = 0; i < overRaw.length; i++) {
            int v = overRaw[i] & 0xFFFF;
            if (v < minVal) minVal = v;
            if (v > maxVal) maxVal = v;
        }
        int range = Math.max(1, maxVal - minVal);

        int overW = overlay.getWidth();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int outIdx = y * w + x;

                // CLUT index from normalized overlay value
                int clutIdx = 0;
                if (x < overW && y * overW + x < overRaw.length) {
                    int val = ((overRaw[y * overW + x] & 0xFFFF) - minVal);
                    clutIdx = clamp((val * 255) / range, 0, 255);
                }

                // Map through heat CLUT
                int cb = clutIdx * 3;
                int r = HEAT_CLUT[cb];
                int g = HEAT_CLUT[cb + 1];
                int b = HEAT_CLUT[cb + 2];

                // Blend: white background (base) with CLUT color weighted
                // base appears as white = (255,255,255) * (1-weight) + clut * weight
                int rr = clamp((int)(255f * wo + r * (1f - wo)), 0, 255);
                int gg = clamp((int)(255f * wo + g * (1f - wo)), 0, 255);
                int bb = clamp((int)(255f * wo + b * (1f - wo)), 0, 255);

                out[outIdx] = (bb << 16) | (gg << 8) | rr;
            }
        }
    }

    // ── ALPHA (透明度ブレンディング — baseのLUT画像にoverlay CLUTをα合成) ──

    private static void blendAlpha(DicomPix base, DicomPix overlay, int[] out,
            int w, int h, float wb, float wo) {
        BufferedImage baseImg = base.getBufferedImageNoLoad();
        if (baseImg == null) return;

        short[] overRaw = overlay.getShortData();
        if (overRaw == null) return;

        // Compute min/max for CLUT normalization
        int minVal = Short.MAX_VALUE, maxVal = Short.MIN_VALUE;
        for (int i = 0; i < overRaw.length; i++) {
            int v = overRaw[i] & 0xFFFF;
            if (v < minVal) minVal = v;
            if (v > maxVal) maxVal = v;
        }
        int range = Math.max(1, maxVal - minVal);

        int overW = overlay.getWidth();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int outIdx = y * w + x;

                // Base pixel RGB (already windowed)
                int baseRGB = baseImg.getRGB(x, y);
                int br = baseRGB & 0xFF;
                int bg = (baseRGB >> 8) & 0xFF;
                int bb = (baseRGB >> 16) & 0xFF;

                // Overlay pixel mapped to CLUT color + alpha from intensity
                int clutIdx = 0;
                if (x < overW && y * overW + x < overRaw.length) {
                    int val = ((overRaw[y * overW + x] & 0xFFFF) - minVal);
                    clutIdx = clamp((val * 255) / range, 0, 255);
                }

                // Normalize overlay intensity -> alpha (use weight as max alpha)
                float alpha = wo;
                
                int cr = HEAT_CLUT[clutIdx * 3];
                int cg = HEAT_CLUT[clutIdx * 3 + 1];
                int cb = HEAT_CLUT[clutIdx * 3 + 2];

                // Alpha composite: result = base * (1-a) + overlayCLUT * a
                out[outIdx] = (clamp((int)(bb * (1 - alpha) + cb * alpha), 0, 255) << 16)
                            | (clamp((int)(bg * (1 - alpha) + cg * alpha), 0, 255) << 8)
                            | clamp((int)(br * (1 - alpha) + cr * alpha), 0, 255);
            }
        }
    }

    // ── ヘルパー ──

    /** DICOM WL/WWから8bit LUTを生成 */
    private static byte[] buildWLWWLUT(DicomPix pix) {
        if (pix == null || pix.getWindowWidth() <= 0) return null;
        byte[] lut = new byte[65536];
        float low = pix.getWindowLevel() - (pix.getWindowWidth() / 2f);
        float actualWW = (pix.getWindowWidth() < 1.0f) ? 1.0f : pix.getWindowWidth();
        float factor = 255.0f / actualWW;

        for (int i = 0; i < 65536; i++) {
            float rawValue = pix.getIsSigned() ? (float)((short) i) : (float) i;
            float v = rawValue * pix.getRescaleSlope() + pix.getRescaleIntercept();
            if (pix.getIsInverse()) v = -v;
            int p = clamp((int)((v - low) * factor), 0, 255);
            lut[i] = (byte) p;
        }
        return lut;
    }

    /** short配列からWL/WWなしの単純LUTを生成 */
    private static byte[] buildWLWWLUTFromShort(short[] raw) {
        if (raw == null || raw.length == 0) return null;
        int min = Short.MAX_VALUE, max = Short.MIN_VALUE;
        for (short s : raw) {
            int v = s & 0xFFFF;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        int range = Math.max(1, max - min);
        byte[] lut = new byte[65536];
        for (int i = 0; i < 65536; i++) {
            float v = (float)(i - min) / range * 255f;
            lut[i] = (byte) clamp((int)v, 0, 255);
        }
        return lut;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}