package com.oculus.jj.ui;

import org.lwjgl.opengl.GL33;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * TransferFunction - HU値 → (R,G,B,Alpha) の多段階マッピング
 * HOROS-20240407準拠: ViewerController+VTK.m の CLUT実装に相当
 *
 * 1Dテクスチャ(256要素)をGPUに転送し、シェーダーで
 * texture(tfTex, normHU) の1サンプルで色と不透明度を取得する。
 */
public class TransferFunction {

    // HU値の有効範囲 (CT標準)
    public static final float HU_MIN = -1024.0f;
    public static final float HU_MAX = 3072.0f;
    public static final int TF_SIZE = 256;

    /** 色の制御点 */
    public static class ColorPoint {
        public float huValue; // HU値
        public float r, g, b;

        public ColorPoint(float hu, float r, float g, float b) {
            this.huValue = hu;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    /** 不透明度の制御点 */
    public static class OpacityPoint {
        public float huValue;
        public float alpha;

        public OpacityPoint(float hu, float alpha) {
            this.huValue = hu;
            this.alpha = alpha;
        }
    }

    private final List<ColorPoint> colorPoints = new ArrayList<>();
    private final List<OpacityPoint> opacityPoints = new ArrayList<>();
    private int glTextureId = 0;
    private String name;
    private int[] tableRed;
    private int[] tableGreen;
    private int[] tableBlue;
    private float[] tableAlpha;

    public TransferFunction(String name) {
        this.name = name;
    }

    public void addColor(float hu, float r, float g, float b) {
        colorPoints.add(new ColorPoint(hu, r, g, b));
        colorPoints.sort(Comparator.comparingDouble(p -> p.huValue));
    }

    public void addOpacity(float hu, float alpha) {
        opacityPoints.add(new OpacityPoint(hu, alpha));
        opacityPoints.sort(Comparator.comparingDouble(p -> p.huValue));
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * 制御点リストをOpenGL 1Dテクスチャに焼き付けてGPUへ転送する。
     * GL_TEXTURE1 スロットを使用する。
     * 
     * @return テクスチャID
     */
    public int buildAndUploadGL() {
        float[] data = new float[TF_SIZE * 4]; // RGBA

        for (int i = 0; i < TF_SIZE; i++) {
            float t = (float) i / (TF_SIZE - 1);
            float hu = HU_MIN + t * (HU_MAX - HU_MIN);

            float[] rgb = interpolateColor(hu);
            float alpha = interpolateOpacity(hu);

            data[i * 4 + 0] = rgb[0];
            data[i * 4 + 1] = rgb[1];
            data[i * 4 + 2] = rgb[2];
            data[i * 4 + 3] = alpha;
        }

        if (glTextureId != 0) {
            GL33.glDeleteTextures(glTextureId);
        }
        glTextureId = GL33.glGenTextures();
        GL33.glActiveTexture(GL33.GL_TEXTURE1);
        GL33.glBindTexture(GL33.GL_TEXTURE_1D, glTextureId);
        GL33.glTexParameteri(GL33.GL_TEXTURE_1D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_1D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_1D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_LINEAR);

        org.lwjgl.system.MemoryUtil.memAllocFloat(data.length);
        java.nio.FloatBuffer buf = org.lwjgl.system.MemoryUtil.memAllocFloat(data.length);
        buf.put(data).flip();
        GL33.glTexImage1D(GL33.GL_TEXTURE_1D, 0, GL33.GL_RGBA16F, TF_SIZE, 0, GL33.GL_RGBA, GL33.GL_FLOAT, buf);
        org.lwjgl.system.MemoryUtil.memFree(buf);

        return glTextureId;
    }

    /**
     * HOROS互換: WL/WW で定義される窓範囲を 0..1 に再サンプリングして 1D TF を生成する。
     */
    public int buildAndUploadGL(float wl, float ww) {
        float[] data = new float[TF_SIZE * 4]; // RGBA
        float width = Math.max(1.0f, ww);
        float huStart = wl - width * 0.5f;
        float huEnd = wl + width * 0.5f;

        if (tableRed != null && tableGreen != null && tableBlue != null && tableAlpha != null) {
            int[] red = normalizeTable(tableRed);
            int[] green = normalizeTable(tableGreen);
            int[] blue = normalizeTable(tableBlue);
            float[] alpha = normalizeTable(tableAlpha);
            for (int i = 0; i < TF_SIZE; i++) {
                data[i * 4 + 0] = red[i] / 255.0f;
                data[i * 4 + 1] = green[i] / 255.0f;
                data[i * 4 + 2] = blue[i] / 255.0f;
                data[i * 4 + 3] = alpha[i];
            }
        } else {
            for (int i = 0; i < TF_SIZE; i++) {
                float t = (float) i / (TF_SIZE - 1);
                float hu = huStart + t * (huEnd - huStart);

                float[] rgb = interpolateColor(hu);
                float alpha = interpolateOpacity(hu);

                data[i * 4 + 0] = rgb[0];
                data[i * 4 + 1] = rgb[1];
                data[i * 4 + 2] = rgb[2];
                data[i * 4 + 3] = alpha;
            }
        }

        if (glTextureId != 0) {
            GL33.glDeleteTextures(glTextureId);
        }
        glTextureId = GL33.glGenTextures();
        GL33.glActiveTexture(GL33.GL_TEXTURE1);
        GL33.glBindTexture(GL33.GL_TEXTURE_1D, glTextureId);
        GL33.glTexParameteri(GL33.GL_TEXTURE_1D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_1D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_1D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_LINEAR);

        java.nio.FloatBuffer buf = org.lwjgl.system.MemoryUtil.memAllocFloat(data.length);
        buf.put(data).flip();
        GL33.glTexImage1D(GL33.GL_TEXTURE_1D, 0, GL33.GL_RGBA16F, TF_SIZE, 0, GL33.GL_RGBA, GL33.GL_FLOAT, buf);
        org.lwjgl.system.MemoryUtil.memFree(buf);

        return glTextureId;
    }

    public int getGLTextureId() {
        return glTextureId;
    }

    // ---- 補間ロジック ----

    private float[] interpolateColor(float hu) {
        if (colorPoints.isEmpty())
            return new float[] { 0.5f, 0.5f, 0.5f };
        if (colorPoints.size() == 1 || hu <= colorPoints.get(0).huValue) {
            ColorPoint p = colorPoints.get(0);
            return new float[] { p.r, p.g, p.b };
        }
        if (hu >= colorPoints.get(colorPoints.size() - 1).huValue) {
            ColorPoint p = colorPoints.get(colorPoints.size() - 1);
            return new float[] { p.r, p.g, p.b };
        }
        for (int i = 0; i < colorPoints.size() - 1; i++) {
            ColorPoint c0 = colorPoints.get(i);
            ColorPoint c1 = colorPoints.get(i + 1);
            if (hu >= c0.huValue && hu <= c1.huValue) {
                float t = (hu - c0.huValue) / (c1.huValue - c0.huValue);
                return new float[] {
                        lerp(c0.r, c1.r, t),
                        lerp(c0.g, c1.g, t),
                        lerp(c0.b, c1.b, t)
                };
            }
        }
        return new float[] { 0.5f, 0.5f, 0.5f };
    }

    private float interpolateOpacity(float hu) {
        if (opacityPoints.isEmpty())
            return 0.0f;
        if (opacityPoints.size() == 1 || hu <= opacityPoints.get(0).huValue)
            return opacityPoints.get(0).alpha;
        if (hu >= opacityPoints.get(opacityPoints.size() - 1).huValue)
            return opacityPoints.get(opacityPoints.size() - 1).alpha;
        for (int i = 0; i < opacityPoints.size() - 1; i++) {
            OpacityPoint o0 = opacityPoints.get(i);
            OpacityPoint o1 = opacityPoints.get(i + 1);
            if (hu >= o0.huValue && hu <= o1.huValue) {
                float t = (hu - o0.huValue) / (o1.huValue - o0.huValue);
                return lerp(o0.alpha, o1.alpha, t);
            }
        }
        return 0.0f;
    }

    /**
     * 0..1 の正規化座標 t から色と不透明度を取得する。
     * テーブル方式の場合は直接参照、制御点方式の場合はウィンドウ範囲に基づいたHU値で評価する。
     */
    public float[] evaluateT(float t, float wl, float ww) {
        if (tableRed != null && tableGreen != null && tableBlue != null && tableAlpha != null) {
            int idx = (int) (t * (TF_SIZE - 1));
            idx = Math.max(0, Math.min(TF_SIZE - 1, idx));
            return new float[] {
                tableRed[idx] / 255.0f,
                tableGreen[idx] / 255.0f,
                tableBlue[idx] / 255.0f,
                tableAlpha[idx]
            };
        } else {
            float huStart = wl - ww * 0.5f;
            float huEnd = wl + ww * 0.5f;
            float hu = huStart + t * (huEnd - huStart);
            return evaluate(hu);
        }
    }

    public float[] evaluate(float hu) {
        float[] rgb = interpolateColor(hu);
        float alpha = interpolateOpacity(hu);
        return new float[] { rgb[0], rgb[1], rgb[2], alpha };
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private int[] normalizeTable(int[] source) {
        int[] normalized = new int[TF_SIZE];
        int usable = Math.min(source.length, TF_SIZE);
        if (usable > 0) {
            System.arraycopy(source, 0, normalized, 0, usable);
            int fill = source[usable - 1];
            for (int i = usable; i < TF_SIZE; i++) {
                normalized[i] = fill;
            }
        }
        return normalized;
    }

    private float[] normalizeTable(float[] source) {
        float[] normalized = new float[TF_SIZE];
        int usable = Math.min(source.length, TF_SIZE);
        if (usable > 0) {
            System.arraycopy(source, 0, normalized, 0, usable);
            float fill = source[usable - 1];
            for (int i = usable; i < TF_SIZE; i++) {
                normalized[i] = fill;
            }
        }
        return normalized;
    }

    // ---- プリセット定義 (HOROS-20240407準拠のCLUT相当) ----

    /** 骨・骨格系のVR表示 */
    public static TransferFunction createBone() {
        TransferFunction tf = new TransferFunction("Bone");
        // 色: 空気=黒, 軟部=暗赤, 筋肉=肉色, 骨皮質=明るいベージュ
        tf.addColor(-1000, 0.0f, 0.0f, 0.0f);
        tf.addColor(-500, 0.1f, 0.0f, 0.0f);
        tf.addColor(-20, 0.6f, 0.3f, 0.2f);
        tf.addColor(200, 0.9f, 0.7f, 0.5f);
        tf.addColor(400, 1.0f, 0.95f, 0.85f);
        tf.addColor(1000, 1.0f, 1.0f, 0.95f);
        // 不透明度: 骨だけを強調表示
        tf.addOpacity(-1000, 0.00f);
        tf.addOpacity(-100, 0.00f);
        tf.addOpacity(150, 0.00f);
        tf.addOpacity(200, 0.40f);
        tf.addOpacity(500, 0.85f);
        tf.addOpacity(1000, 0.90f);
        return tf;
    }

    /** 軟部組織・臓器のVR表示 */
    public static TransferFunction createSoftTissue() {
        TransferFunction tf = new TransferFunction("Soft Tissue");
        // 色: 空気=透明, 脂肪=黄色, 軟部=肉色, 骨=ベージュ
        tf.addColor(-1000, 0.0f, 0.0f, 0.0f);
        tf.addColor(-200, 0.4f, 0.2f, 0.1f);
        tf.addColor(-100, 0.7f, 0.6f, 0.3f); // 脂肪: 黄色がかった色
        tf.addColor(40, 0.88f, 0.50f, 0.40f); // 軟部組織: 肉色
        tf.addColor(200, 0.92f, 0.58f, 0.42f); // 筋肉: やや濃い肉色
        tf.addColor(400, 1.0f, 0.95f, 0.85f); // 骨
        // 不透明度: 軟部組織を透けさせ、骨は半透明
        tf.addOpacity(-1000, 0.00f);
        tf.addOpacity(-300, 0.00f);
        tf.addOpacity(-200, 0.05f); // 脂肪: わずかに表示
        tf.addOpacity(-50, 0.20f); // 軟部組織の境界
        tf.addOpacity(40, 0.45f); // 軟部組織メイン
        tf.addOpacity(200, 0.50f);
        tf.addOpacity(400, 0.30f); // 骨は半透明にして中の組織も見えるように
        tf.addOpacity(1000, 0.30f);
        return tf;
    }

    /** 肺野のVR表示 */
    public static TransferFunction createLung() {
        TransferFunction tf = new TransferFunction("Lung");
        // 色: 肺実質=薄い青、気管支壁=ピンク、骨=ベージュ
        tf.addColor(-1000, 0.0f, 0.0f, 0.0f);
        tf.addColor(-900, 0.05f, 0.12f, 0.2f); // 肺気腔: 暗い青
        tf.addColor(-600, 0.2f, 0.4f, 0.6f); // 肺実質: 青
        tf.addColor(-400, 0.4f, 0.6f, 0.8f);
        tf.addColor(-50, 0.85f, 0.5f, 0.4f); // 気管支壁・軟部
        tf.addColor(200, 1.0f, 0.95f, 0.85f); // 骨
        // 不透明度: 肺実質を強調
        tf.addOpacity(-1000, 0.00f);
        tf.addOpacity(-950, 0.00f);
        tf.addOpacity(-900, 0.15f);
        tf.addOpacity(-600, 0.30f);
        tf.addOpacity(-500, 0.25f);
        tf.addOpacity(-100, 0.00f); // 軟部組織の手前は透明に
        tf.addOpacity(50, 0.00f);
        tf.addOpacity(200, 0.20f);
        tf.addOpacity(400, 0.60f);
        return tf;
    }

    /** 造影CT血管のVR表示 */
    public static TransferFunction createVascular() {
        TransferFunction tf = new TransferFunction("Vascular (CE)");
        // 色: 造影血管=鮮やかな赤～ピンク、骨=半透明ベージュ
        tf.addColor(-1000, 0.0f, 0.0f, 0.0f);
        tf.addColor(100, 0.5f, 0.0f, 0.0f); // 軟部の始まり
        tf.addColor(200, 0.9f, 0.1f, 0.1f); // 造影血管（主相）: 鮮紅色
        tf.addColor(300, 1.0f, 0.3f, 0.2f); // 高濃度造影
        tf.addColor(400, 1.0f, 0.9f, 0.8f); // 骨
        tf.addColor(1000, 1.0f, 1.0f, 0.95f);
        // 不透明度: 200HU以上を強調（造影剤で明るくなった血管）
        tf.addOpacity(-1000, 0.00f);
        tf.addOpacity(100, 0.00f);
        tf.addOpacity(150, 0.00f);
        tf.addOpacity(200, 0.70f); // 造影血管: 不透明に
        tf.addOpacity(350, 0.80f);
        tf.addOpacity(500, 0.30f); // 骨: やや透かして血管を見やすく
        tf.addOpacity(1000, 0.30f);
        return tf;
    }

    /**
     * HOROS Standard に近い VR Muscles-Bones 近似プリセット。
     * 色は筋肉〜骨の暖色グラデーション、不透明度は Logarithmic Inverse 近似。
     */
    public static TransferFunction createVRMusclesBones() {
        TransferFunction tf = new TransferFunction("VR Muscles-Bones");

        tf.tableBlue = new int[] { 0, 0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 7, 7, 7, 7,
                8, 8, 8, 9, 9, 9, 10, 10, 10, 10, 11, 11, 11, 12, 12, 12, 12, 13, 13, 13, 14, 14, 14, 15, 15, 15, 15,
                16, 16, 16, 17, 17, 17, 17, 18, 18, 18, 19, 19, 19, 20, 20, 20, 20, 21, 21, 21, 22, 22, 22, 22, 23, 23,
                23, 24, 24, 24, 25, 25, 25, 25, 26, 26, 26, 27, 27, 27, 27, 27, 27, 26, 26, 26, 25, 25, 25, 24, 24, 24,
                23, 23, 22, 22, 22, 21, 21, 21, 20, 20, 20, 19, 19, 19, 18, 18, 18, 17, 17, 16, 16, 16, 15, 15, 15, 14,
                14, 14, 13, 13, 13, 12, 12, 11, 11, 11, 10, 10, 10, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 15, 15,
                16, 16, 17, 17, 18, 18, 19, 19, 20, 21, 21, 22, 22, 23, 23, 24, 24, 25, 26, 26, 27, 27, 28, 28, 29, 29,
                30, 31, 31, 32, 32, 33, 33, 34, 34, 35, 36, 36, 37, 37, 38, 38, 39, 43, 47, 51, 55, 58, 62, 66, 70, 74,
                78, 82, 86, 90, 94, 98, 102, 105, 109, 113, 117, 121, 125, 129, 133, 137, 141, 145, 149, 153, 156, 160,
                164, 168, 172, 176, 180, 184, 188, 192, 196, 200, 204, 207, 211, 215, 219, 223, 227, 231, 235, 239, 243,
                247, 251 };
        tf.tableGreen = new int[] { 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6,
                6, 6, 6, 7, 7, 7, 7, 8, 8, 8, 8, 9, 9, 9, 9, 9, 10, 10, 10, 10, 11, 11, 11, 11, 12, 12, 12, 12, 12, 13,
                13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15, 15, 16, 16, 16, 16, 17, 17, 17, 17, 18, 18, 18, 18, 18, 19,
                19, 19, 19, 20, 20, 20, 20, 21, 21, 21, 21, 21, 24, 27, 30, 33, 36, 39, 42, 45, 48, 51, 54, 57, 60, 63,
                66, 69, 72, 75, 78, 81, 84, 87, 90, 93, 96, 99, 102, 105, 108, 111, 114, 117, 120, 123, 126, 129, 131,
                134, 137, 140, 143, 146, 149, 152, 155, 158, 161, 164, 167, 170, 173, 176, 177, 179, 180, 181, 182, 183,
                185, 186, 187, 188, 189, 191, 192, 193, 194, 195, 197, 198, 199, 200, 201, 203, 204, 205, 206, 207, 209,
                210, 211, 212, 213, 215, 216, 217, 218, 220, 221, 222, 223, 224, 226, 227, 228, 229, 230, 232, 233, 234,
                235, 236, 238, 239, 240, 241, 241, 242, 242, 242, 242, 243, 243, 243, 243, 244, 244, 244, 244, 245, 245,
                245, 245, 246, 246, 246, 246, 247, 247, 247, 247, 248, 248, 248, 248, 248, 249, 249, 249, 249, 250, 250,
                250, 250, 251, 251, 251, 251, 252, 252, 252, 252, 253, 253, 253, 253, 254, 254, 254, 254 };
        tf.tableRed = new int[] { 0, 2, 5, 8, 10, 13, 16, 18, 21, 24, 26, 29, 32, 34, 37, 40, 42, 45, 48, 51, 53, 56,
                59, 61, 64, 67, 69, 72, 75, 77, 80, 83, 85, 88, 91, 93, 96, 99, 102, 104, 107, 110, 112, 115, 118, 120,
                123, 126, 128, 131, 134, 136, 139, 142, 144, 147, 150, 153, 155, 158, 161, 163, 166, 169, 171, 174, 177,
                179, 182, 185, 187, 190, 193, 195, 198, 201, 204, 206, 209, 212, 214, 217, 220, 222, 225, 228, 230, 233,
                236, 238, 241, 244, 246, 249, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255 };
        tf.tableAlpha = new float[TF_SIZE];
        for (int i = 0; i < TF_SIZE; i++) {
            // 軟部組織をよりはっきりと表示させるため、不透明度の立ち上がりを調整 (HOROSの挙動に近似)
            float t = i / 255.0f;
            tf.tableAlpha[i] = (float) (0.08 + 0.62 * Math.pow(t, 0.9));
        }

        return tf;
    }
}
