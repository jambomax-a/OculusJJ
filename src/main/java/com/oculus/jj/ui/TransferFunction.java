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
    public static final float HU_MAX =  3072.0f;
    public static final int   TF_SIZE = 256;

    /** 色の制御点 */
    public static class ColorPoint {
        public float huValue; // HU値
        public float r, g, b;
        public ColorPoint(float hu, float r, float g, float b) {
            this.huValue = hu; this.r = r; this.g = g; this.b = b;
        }
    }

    /** 不透明度の制御点 */
    public static class OpacityPoint {
        public float huValue;
        public float alpha;
        public OpacityPoint(float hu, float alpha) {
            this.huValue = hu; this.alpha = alpha;
        }
    }

    private final List<ColorPoint>   colorPoints   = new ArrayList<>();
    private final List<OpacityPoint> opacityPoints = new ArrayList<>();
    private int glTextureId = 0;
    private String name;

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
    public String toString() { return name; }

    /**
     * 制御点リストをOpenGL 1Dテクスチャに焼き付けてGPUへ転送する。
     * GL_TEXTURE1 スロットを使用する。
     * @return テクスチャID
     */
    public int buildAndUploadGL() {
        float[] data = new float[TF_SIZE * 4]; // RGBA

        for (int i = 0; i < TF_SIZE; i++) {
            float t = (float) i / (TF_SIZE - 1);
            float hu = HU_MIN + t * (HU_MAX - HU_MIN);

            float[] rgb   = interpolateColor(hu);
            float   alpha = interpolateOpacity(hu);

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
        GL33.glTexParameteri(GL33.GL_TEXTURE_1D, GL33.GL_TEXTURE_WRAP_S,     GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_1D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_1D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_LINEAR);

        org.lwjgl.system.MemoryUtil.memAllocFloat(data.length);
        java.nio.FloatBuffer buf = org.lwjgl.system.MemoryUtil.memAllocFloat(data.length);
        buf.put(data).flip();
        GL33.glTexImage1D(GL33.GL_TEXTURE_1D, 0, GL33.GL_RGBA16F, TF_SIZE, 0, GL33.GL_RGBA, GL33.GL_FLOAT, buf);
        org.lwjgl.system.MemoryUtil.memFree(buf);

        return glTextureId;
    }

    public int getGLTextureId() { return glTextureId; }

    // ---- 補間ロジック ----

    private float[] interpolateColor(float hu) {
        if (colorPoints.isEmpty()) return new float[]{0.5f, 0.5f, 0.5f};
        if (colorPoints.size() == 1 || hu <= colorPoints.get(0).huValue) {
            ColorPoint p = colorPoints.get(0);
            return new float[]{p.r, p.g, p.b};
        }
        if (hu >= colorPoints.get(colorPoints.size() - 1).huValue) {
            ColorPoint p = colorPoints.get(colorPoints.size() - 1);
            return new float[]{p.r, p.g, p.b};
        }
        for (int i = 0; i < colorPoints.size() - 1; i++) {
            ColorPoint c0 = colorPoints.get(i);
            ColorPoint c1 = colorPoints.get(i + 1);
            if (hu >= c0.huValue && hu <= c1.huValue) {
                float t = (hu - c0.huValue) / (c1.huValue - c0.huValue);
                return new float[]{
                    lerp(c0.r, c1.r, t),
                    lerp(c0.g, c1.g, t),
                    lerp(c0.b, c1.b, t)
                };
            }
        }
        return new float[]{0.5f, 0.5f, 0.5f};
    }

    private float interpolateOpacity(float hu) {
        if (opacityPoints.isEmpty()) return 0.0f;
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
     * CPURaycastRenderer と共有: HU値 → float[4] {R,G,B,Alpha} を返す。
     */
    public float[] evaluate(float hu) {
        float[] rgb   = interpolateColor(hu);
        float   alpha = interpolateOpacity(hu);
        return new float[]{rgb[0], rgb[1], rgb[2], alpha};
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }

    // ---- プリセット定義 (HOROS-20240407準拠のCLUT相当) ----

    /** 骨・骨格系のVR表示 */
    public static TransferFunction createBone() {
        TransferFunction tf = new TransferFunction("Bone");
        // 色: 空気=黒, 軟部=暗赤, 筋肉=肉色, 骨皮質=明るいベージュ
        tf.addColor(-1000,  0.0f, 0.0f, 0.0f);
        tf.addColor( -500,  0.1f, 0.0f, 0.0f);
        tf.addColor(  -20,  0.6f, 0.3f, 0.2f);
        tf.addColor(  200,  0.9f, 0.7f, 0.5f);
        tf.addColor(  400,  1.0f, 0.95f,0.85f);
        tf.addColor( 1000,  1.0f, 1.0f, 0.95f);
        // 不透明度: 骨だけを強調表示
        tf.addOpacity(-1000, 0.00f);
        tf.addOpacity( -100, 0.00f);
        tf.addOpacity(  150, 0.00f);
        tf.addOpacity(  200, 0.40f);
        tf.addOpacity(  500, 0.85f);
        tf.addOpacity( 1000, 0.90f);
        return tf;
    }

    /** 軟部組織・臓器のVR表示 */
    public static TransferFunction createSoftTissue() {
        TransferFunction tf = new TransferFunction("Soft Tissue");
        // 色: 空気=透明, 脂肪=黄色, 軟部=肉色, 骨=ベージュ
        tf.addColor(-1000,  0.0f, 0.0f, 0.0f);
        tf.addColor( -200,  0.4f, 0.2f, 0.1f);
        tf.addColor( -100,  0.7f, 0.6f, 0.3f); // 脂肪: 黄色がかった色
        tf.addColor(   40,  0.88f,0.50f,0.40f); // 軟部組織: 肉色
        tf.addColor(  200,  0.92f,0.58f,0.42f); // 筋肉: やや濃い肉色
        tf.addColor(  400,  1.0f, 0.95f,0.85f); // 骨
        // 不透明度: 軟部組織を透けさせ、骨は半透明
        tf.addOpacity(-1000, 0.00f);
        tf.addOpacity( -300, 0.00f);
        tf.addOpacity( -200, 0.05f); // 脂肪: わずかに表示
        tf.addOpacity(  -50, 0.20f); // 軟部組織の境界
        tf.addOpacity(   40, 0.45f); // 軟部組織メイン
        tf.addOpacity(  200, 0.50f);
        tf.addOpacity(  400, 0.30f); // 骨は半透明にして中の組織も見えるように
        tf.addOpacity( 1000, 0.30f);
        return tf;
    }

    /** 肺野のVR表示 */
    public static TransferFunction createLung() {
        TransferFunction tf = new TransferFunction("Lung");
        // 色: 肺実質=薄い青、気管支壁=ピンク、骨=ベージュ
        tf.addColor(-1000, 0.0f, 0.0f, 0.0f);
        tf.addColor( -900, 0.05f,0.12f,0.2f);   // 肺気腔: 暗い青
        tf.addColor( -600, 0.2f, 0.4f, 0.6f);   // 肺実質: 青
        tf.addColor( -400, 0.4f, 0.6f, 0.8f);
        tf.addColor(  -50, 0.85f,0.5f, 0.4f);   // 気管支壁・軟部
        tf.addColor(  200, 1.0f, 0.95f,0.85f);  // 骨
        // 不透明度: 肺実質を強調
        tf.addOpacity(-1000, 0.00f);
        tf.addOpacity( -950, 0.00f);
        tf.addOpacity( -900, 0.15f);
        tf.addOpacity( -600, 0.30f);
        tf.addOpacity( -500, 0.25f);
        tf.addOpacity( -100, 0.00f); // 軟部組織の手前は透明に
        tf.addOpacity(   50, 0.00f);
        tf.addOpacity(  200, 0.20f);
        tf.addOpacity(  400, 0.60f);
        return tf;
    }

    /** 造影CT血管のVR表示 */
    public static TransferFunction createVascular() {
        TransferFunction tf = new TransferFunction("Vascular (CE)");
        // 色: 造影血管=鮮やかな赤～ピンク、骨=半透明ベージュ
        tf.addColor(-1000, 0.0f, 0.0f, 0.0f);
        tf.addColor(  100, 0.5f, 0.0f, 0.0f);   // 軟部の始まり
        tf.addColor(  200, 0.9f, 0.1f, 0.1f);   // 造影血管（主相）: 鮮紅色
        tf.addColor(  300, 1.0f, 0.3f, 0.2f);   // 高濃度造影
        tf.addColor(  400, 1.0f, 0.9f, 0.8f);   // 骨
        tf.addColor( 1000, 1.0f, 1.0f, 0.95f);
        // 不透明度: 200HU以上を強調（造影剤で明るくなった血管）
        tf.addOpacity(-1000, 0.00f);
        tf.addOpacity(  100, 0.00f);
        tf.addOpacity(  150, 0.00f);
        tf.addOpacity(  200, 0.70f); // 造影血管: 不透明に
        tf.addOpacity(  350, 0.80f);
        tf.addOpacity(  500, 0.30f); // 骨: やや透かして血管を見やすく
        tf.addOpacity( 1000, 0.30f);
        return tf;
    }
}
