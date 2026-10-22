package com.oculus.jj.model;

/**
 * SegmentationEngine — HOROS-20240407 ITKSegmentation3D の純 Java 実装。
 *
 * HOROS 側の対応:
 * <ul>
 * <li>{@code ITKSegmentation3D.mm::fastGrowingRegionWithVolume / regionGrowing3D}</li>
 * <li>{@code ITKSegmentation3DController.m} のアルゴリズム群:
 *   <ul>
 *   <li>Threshold (interval)  → itkConnectedThresholdImageFilter</li>
 *   <li>Neighborhood          → itkNeighborhoodConnectedImageFilter</li>
 *   <li>Confidence            → itkConfidenceConnectedImageFilter</li>
 *   </ul></li>
 * <li>{@code itkCurvatureFlowImageFilter} 相当の平滑化（近似）</li>
 * <li>{@code roiSetPixelsSetup:} 相当のピクセル値上書き</li>
 * </ul>
 *
 * ボリュームは {@code [z*dimY*dimX + y*dimX + x]} 配置の HU 値 short[] を前提とする。
 * 依存は JDK のみ（ITK C++ 不要）。
 *
 * 実装方針: シード起点の 6 近傍連結領域を、毎イテレーション 1 ボクセルずつ成長させる
 * （反復 dilate + 条件フィルタ）。HOROS の NeighborhoodConnectedImageFilter と同じ
 * 「近傍半径 = イテレーション回数」のモデルである。
 */
public final class SegmentationEngine {

    /** アルゴリズム識別子（HOROS ITKSegmentation3DController 相当）。 */
    public static final int ALGO_INTERVAL = 0;       // itkConnectedThresholdImageFilter
    public static final int ALGO_NEIGHBORHOOD = 1;   // itkNeighborhoodConnectedImageFilter
    public static final int ALGO_CONFIDENCE = 2;     // itkConfidenceConnectedImageFilter

    /** 成長結果（マスク + 領域ピクセル数）。 */
    public static final class Result {
        public final boolean[] mask;
        public final int count;

        Result(boolean[] mask, int count) {
            this.mask = mask;
            this.count = count;
        }
    }

    private SegmentationEngine() {
    }

    /**
     * シードからの 3D 領域成長（6 近傍・反復 dilate）。
     *
     * @param vol        ボリューム（HU、short[]、[z*dimY*dimX + y*dimX + x]）
     * @param w, h, d    各次元サイズ
     * @param sx, sy, sz シード座標（範囲外はクランプ）
     * @param algo       ALGO_INTERVAL / ALGO_NEIGHBORHOOD / ALGO_CONFIDENCE
     * @param lower      下限 HU（ALGO_INTERVAL で有効）
     * @param upper      上限 HU
     * @param radius     成長半径（ボクセル、最大 512 にクランプ）
     * @param multiplier ALGO_CONFIDENCE の confidence 倍率
     * @return           成長マスク（長さ w*h*d の boolean[]）
     */
    public static Result grow(short[] vol, int w, int h, int d,
            int sx, int sy, int sz,
            int algo, double lower, double upper, int radius, double multiplier) {
        if (vol == null || w <= 0 || h <= 0 || d <= 0) {
            return new Result(new boolean[0], 0);
        }
        int n = vol.length;
        int plane = w * h;

        sx = clamp(sx, 0, w - 1);
        sy = clamp(sy, 0, h - 1);
        sz = clamp(sz, 0, d - 1);
        int seed = sz * plane + sy * w + sx;
        if (seed >= n) {
            return new Result(new boolean[n], 0);
        }

        boolean[] region = new boolean[n];
        boolean[] fresh = new boolean[n]; // 前イテレーションで追加されたボクセル
        int count = 0;

        region[seed] = true;
        fresh[seed] = true;
        count = 1;

        int maxVal = vol[seed];
        int iters = Math.max(1, Math.min(radius, 512));
        boolean confidence = (algo == ALGO_CONFIDENCE);
        double tol = Math.max(0.001, upper * multiplier);

        for (int iter = 0; iter < iters; iter++) {
            int added = 0;
            int[] freshList = new int[Math.min(n, 1 << 20)];
            int flSize = 0;
            for (int i = 0; i < n; i++) {
                if (!fresh[i]) {
                    continue;
                }
                int v = vol[i];
                if (confidence) {
                    if (v > maxVal) {
                        maxVal = v;
                        tol = Math.max(0.001, upper * multiplier);
                    }
                    if (v < maxVal - tol) {
                        continue;
                    }
                } else if (algo == ALGO_INTERVAL) {
                    if (v < lower || v > upper) {
                        continue;
                    }
                } else {
                    if (v > upper) {
                        continue;
                    }
                }

                int x = i % w;
                int rem = i / w;
                int y = rem % h;

                if (x + 1 < w && !region[i + 1] && passes(vol[i + 1], algo, lower, upper,
                        confidence, maxVal, tol)) {
                    region[i + 1] = true;
                    fresh[i + 1] = true;
                    if (flSize == freshList.length)
                        freshList = java.util.Arrays.copyOf(freshList, freshList.length * 2);
                    freshList[flSize++] = i + 1;
                    added++;
                }
                if (x - 1 >= 0 && !region[i - 1] && passes(vol[i - 1], algo, lower, upper,
                        confidence, maxVal, tol)) {
                    region[i - 1] = true;
                    fresh[i - 1] = true;
                    if (flSize == freshList.length)
                        freshList = java.util.Arrays.copyOf(freshList, freshList.length * 2);
                    freshList[flSize++] = i - 1;
                    added++;
                }
                if (y + 1 < h && !region[i + w] && passes(vol[i + w], algo, lower, upper,
                        confidence, maxVal, tol)) {
                    region[i + w] = true;
                    fresh[i + w] = true;
                    if (flSize == freshList.length)
                        freshList = java.util.Arrays.copyOf(freshList, freshList.length * 2);
                    freshList[flSize++] = i + w;
                    added++;
                }
                if (y - 1 >= 0 && !region[i - w] && passes(vol[i - w], algo, lower, upper,
                        confidence, maxVal, tol)) {
                    region[i - w] = true;
                    fresh[i - w] = true;
                    if (flSize == freshList.length)
                        freshList = java.util.Arrays.copyOf(freshList, freshList.length * 2);
                    freshList[flSize++] = i - w;
                    added++;
                }
                if (i + plane < n && !region[i + plane]
                        && passes(vol[i + plane], algo, lower, upper, confidence, maxVal, tol)) {
                    region[i + plane] = true;
                    fresh[i + plane] = true;
                    if (flSize == freshList.length)
                        freshList = java.util.Arrays.copyOf(freshList, freshList.length * 2);
                    freshList[flSize++] = i + plane;
                    added++;
                }
                if (i - plane >= 0 && !region[i - plane]
                        && passes(vol[i - plane], algo, lower, upper, confidence, maxVal, tol)) {
                    region[i - plane] = true;
                    fresh[i - plane] = true;
                    if (flSize == freshList.length)
                        freshList = java.util.Arrays.copyOf(freshList, freshList.length * 2);
                    freshList[flSize++] = i - plane;
                    added++;
                }
            }

            count += added;
            // fresh を次イテレーション用に更新
            java.util.Arrays.fill(fresh, false);
            for (int i = 0; i < flSize; i++) {
                fresh[freshList[i]] = true;
            }
            if (added == 0) {
                break; // 収束
            }
        }
        return new Result(region, count);
    }

    private static boolean passes(int v, int algo, double lower, double upper,
            boolean confidence, int maxVal, double tol) {
        if (confidence) {
            return v <= maxVal && (maxVal - v) <= tol;
        }
        if (algo == ALGO_INTERVAL) {
            return v >= lower && v <= upper;
        }
        return v <= upper;
    }

    /** 成長マスク内のピクセルを value に上書き（HOROS roiSetPixels inside 相当）。 */
    public static int setInside(short[] vol, boolean[] mask, short value) {
        if (vol == null || mask == null)
            return 0;
        int changed = 0;
        int len = Math.min(vol.length, mask.length);
        for (int i = 0; i < len; i++) {
            if (mask[i] && vol[i] != value) {
                vol[i] = value;
                changed++;
            }
        }
        return changed;
    }

    /** 成長マスク外のピクセルを value に上書き（HOROS roiSetPixels outside 相当）。 */
    public static int setOutside(short[] vol, boolean[] mask, short value) {
        if (vol == null || mask == null)
            return 0;
        int changed = 0;
        int len = Math.min(vol.length, mask.length);
        for (int i = 0; i < len; i++) {
            if (!mask[i] && vol[i] != value) {
                vol[i] = value;
                changed++;
            }
        }
        return changed;
    }

    /**
     * 簡易平滑化（HOROS itkCurvatureFlowImageFilter の近似）。
     * 中心 6 / 6 近傍 1 の重み平均を N 回適用。
     */
    public static void smooth(short[] vol, int w, int h, int d, int iterations) {
        if (vol == null || w <= 0 || h <= 0 || d <= 0)
            return;
        int n = vol.length;
        short[] tmp = new short[n];
        short[] src = vol;
        short[] dst = tmp;
        int its = Math.max(1, Math.min(iterations, 100));
        for (int it = 0; it < its; it++) {
            for (int z = 0; z < d; z++) {
                int zOff = z * w * h;
                for (int y = 0; y < h; y++) {
                    int base = zOff + y * w;
                    for (int x = 0; x < w; x++) {
                        int i = base + x;
                        int sum = 6 * src[i];
                        sum += src[x + 1 < w ? i + 1 : i];
                        sum += src[x - 1 >= 0 ? i - 1 : i];
                        sum += src[y + 1 < h ? i + w : i];
                        sum += src[y - 1 >= 0 ? i - w : i];
                        sum += (z + 1 < d) ? src[i + w * h] : src[i];
                        sum += (z - 1 >= 0) ? src[i - w * h] : src[i];
                        dst[i] = (short) (sum / 7);
                    }
                }
            }
            short[] s = src;
            src = dst;
            dst = s;
        }
        if (src != vol) {
            System.arraycopy(src, 0, vol, 0, n);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
