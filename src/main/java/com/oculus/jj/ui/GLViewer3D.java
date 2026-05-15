package com.oculus.jj.ui;

import org.lwjgl.opengl.GL33;
import java.nio.*;
import org.lwjgl.system.MemoryUtil;
import java.util.List;
import com.oculus.jj.model.DicomPix;

/**
 * GLViewer3D - HOROS-20240407準拠 GPU Volume Renderer
 * Phase 1: Pre-computed Gradient Volume (事前勾配計算)
 * Phase 2: Phong照明 (Ambient + Diffuse + Specular)
 * Phase 3: Transfer Function 1Dテクスチャ
 */
public class GLViewer3D {

    private int shaderProgram;
    private int vao, vbo, ebo;
    private int volumeTexture;
    private int gradientTexture; // Phase 1: 事前計算勾配テクスチャ
    private int tfTexture; // Phase 3: Transfer Function 1Dテクスチャ

    private short[][][] volume;
    private int dimX, dimY, dimZ;
    private List<DicomPix> pixList;
    private boolean dataReady = false;

    private float rotateX = -1.57f;
    private float rotateY = 0.0f;
    private float zoom = 1.0f;
    private float offsetX = 0, offsetY = 0, offsetZ = 0;
    private static final float PAN_LIMIT = 0.35f;
    private int renderMode = 1; // 0=MIP, 1=VR, 2=ISO

    // Phase 2: Phong ライト方向
    private float lightX = -0.577f, lightY = -0.577f, lightZ = 0.577f;

    // WW/WL (HOROS準拠: TFのルックアップ範囲を制御)
    private float windowLevel = 150.0f;
    private float windowWidth = 400.0f;

    // Clipping
    private float clipMinX = 0, clipMaxX = 1;
    private float clipMinY = 0, clipMaxY = 1;
    private float clipMinZ = 0, clipMaxZ = 1;

    // Phase 3: Transfer Function
    private TransferFunction transferFunction;
    private boolean tfDirty = true;

    // ISO
    private float isoThreshold = 300.0f;

    // Fix 2: 物理ボクセルサイズ [mm] → アスペクト比正規化
    private float voxScaleX = 1.0f, voxScaleY = 1.0f, voxScaleZ = 1.0f;

    public GLViewer3D() {
        this.transferFunction = TransferFunction.createVRMusclesBones();
    }

    public void setVolumeData(short[][][] vol, int w, int h, int d, List<DicomPix> pix) {
        this.volume = vol;
        this.dimX = w;
        this.dimY = h;
        this.dimZ = d;
        this.pixList = pix;
        this.dataReady = true;
    }

    public void setRenderMode(int mode) {
        this.renderMode = mode;
    }

    public void setRotation(float x, float y) {
        this.rotateX = x;
        this.rotateY = y;
    }

    public void setZoom(float z) {
        this.zoom = z;
    }

    public void setOffset(float x, float y, float z) {
        offsetX = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, x));
        offsetY = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, y));
        offsetZ = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, z));
    }

    public void addOffset(float dx, float dy, float dz) {
        offsetX = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, offsetX + dx));
        offsetY = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, offsetY + dy));
        offsetZ = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, offsetZ + dz));
    }

    public void setClipping(float x0, float x1, float y0, float y1, float z0, float z1) {
        clipMinX = x0;
        clipMaxX = x1;
        clipMinY = y0;
        clipMaxY = y1;
        clipMinZ = z0;
        clipMaxZ = z1;
    }

    public void setLightDirection(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l > 0) {
            lightX = x / l;
            lightY = y / l;
            lightZ = z / l;
        }
    }

    public void setTransferFunction(TransferFunction tf) {
        this.transferFunction = tf;
        this.tfDirty = true;
    }

    public void setIsoThreshold(float t) {
        this.isoThreshold = t;
    }

    /** HOROS準拠: WW/WL でTFの参照範囲をシフト・スケールする */
    public void setWindowLevel(float wl, float ww) {
        this.windowLevel = wl;
        this.windowWidth = Math.max(1, ww);
        this.tfDirty = true;
    }

    /**
     * 物理ボクセルサイズを設定しモデル行列のスケールを正規化する。
     * HOROSは実際のmm単位でボリュームをスケールするためズんぐりにならない。
     * 
     * @param sx X方向 mm/pixel
     * @param sy Y方向 mm/pixel
     * @param sz Z方向 mm/slice
     */
    public void setVoxelSpacing(float sx, float sy, float sz) {
        // 各軸の物理總幅 = ボクセル数 x スペーシング
        float physX = dimX * sx;
        float physY = dimY * sy;
        float physZ = dimZ * sz;
        // 最大軸を 1.0 に正規化
        float maxPhys = Math.max(physX, Math.max(physY, physZ));
        voxScaleX = (maxPhys > 0) ? physX / maxPhys : 1.0f;
        voxScaleY = (maxPhys > 0) ? physY / maxPhys : 1.0f;
        voxScaleZ = (maxPhys > 0) ? physZ / maxPhys : 1.0f;
    }

    // --- シェーダー ---
    private static final String VERT = "#version 330 core\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "out vec3 vTex;\n" +
            "out vec3 vCam;\n" +
            "uniform mat4 model,view,projection;\n" +
            "void main(){\n" +
            "  mat4 mv=view*model;\n" +
            "  gl_Position=projection*mv*vec4(aPos,1);\n" +
            "  vTex=aPos+vec3(0.5);\n" +
            "  vCam=(inverse(mv)*vec4(0,0,0,1)).xyz+vec3(0.5);\n" +
            "}\n";

    private static final String FRAG = "#version 330 core\n" +
            "in vec3 vTex, vCam;\n" +
            "out vec4 FragColor;\n" +
            "uniform sampler3D volumeTex;\n" +
            "uniform sampler3D gradTex;\n" +
            "uniform sampler1D tfTex;\n" +
            "uniform float stepSize;\n" +
            "uniform int renderMode;\n" +
            "uniform float isoThreshold;\n" +
            "uniform float windowLevel;\n" +
            "uniform float windowWidth;\n" +
            "uniform vec3 uOffset;\n" +
            "uniform vec3 lightDir;\n" +
            "uniform vec3 clipMin, clipMax;\n" +
            "void main(){\n" +
            "  vec3 ray=normalize(vTex-vCam);\n" +
            "  vec3 pos=vTex+uOffset;\n" +
            "  vec4 acc=vec4(0);\n" +
            "  float maxV=0;\n" +
            // jitterを小さく抑える（0.0~0.5）
            "  float jit=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453)*0.5;\n" +
            "  pos+=ray*stepSize*jit;\n" +
            "  for(int i=0;i<800;i++){\n" +
            "    if(pos.x<0.0||pos.x>1.0||pos.y<0.0||pos.y>1.0||pos.z<0.0||pos.z>1.0) break;\n" +
            "    if(pos.x>=clipMin.x&&pos.x<=clipMax.x&&\n" +
            "       pos.y>=clipMin.y&&pos.y<=clipMax.y&&\n" +
            "       pos.z>=clipMin.z&&pos.z<=clipMax.z){\n" +
            "      float raw=texture(volumeTex,pos).r*65535.0-32768.0;\n" +
            // VR/MIPともWL/WW窓でスケールしたTF座標を使う
            "      float tfT=clamp((raw-(windowLevel-windowWidth*0.5))/windowWidth, 0.0, 1.0);\n" +
            // MIP/MinIP用座標: WL/WW窓でスケール
            "      float winT=clamp((raw-(windowLevel-windowWidth*0.5))/windowWidth, 0.0, 1.0);\n" +
            "      if(renderMode==0){\n" +
            "        maxV=max(maxV,winT);\n" +
            "      } else if(renderMode==1){\n" +
            "        float winLow=windowLevel-windowWidth*0.5;\n" +
            "        float winHigh=windowLevel+windowWidth*0.5;\n" +
            "        if(raw<winLow||raw>winHigh){ pos+=ray*stepSize; continue; }\n" +
            "        vec4 tf=texture(tfTex,tfT);\n" +
            "        if(tf.a>0.005){\n" +
            "          vec3 N=normalize(texture(gradTex,pos).xyz*2.0-1.0);\n" +
            "          vec3 L=normalize(lightDir);\n" +
            "          vec3 V=normalize(-ray);\n" +
            "          vec3 H=normalize(L+V);\n" + // Blinn-Phong (HOROSはこちら)
            "          float ambient=0.20;\n" +
            "          float diffuse=max(dot(N,L),0.0)*0.90;\n" +
            "          float spec=pow(max(dot(N,H),0.0),15.0)*0.30;\n" +
            "          vec3 col=tf.rgb*(ambient+diffuse)+vec3(spec);\n" +
            "          acc.rgb+=(1.0-acc.a)*col*tf.a;\n" +
            "          acc.a+=(1.0-acc.a)*tf.a;\n" +
            "          if(acc.a>=0.95) break;\n" +
            "        }\n" +
            "      } else {\n" +
            "        if(raw>isoThreshold){\n" +
            "          vec3 N=normalize(texture(gradTex,pos).xyz*2.0-1.0);\n" +
            "          vec3 L=normalize(lightDir);\n" +
            "          float d=max(dot(N,L),0.15);\n" +
            "          float s=pow(max(dot(normalize(-ray+L),N),0.0),16.0)*0.15;\n" +
            "          acc=vec4(vec3(0.92,0.88,0.78)*(d+0.2)+vec3(s),1.0);\n" +
            "          break;\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "    pos+=ray*stepSize;\n" +
            "  }\n" +
            "  if(renderMode==0) FragColor=(maxV>0.001)?vec4(vec3(maxV),1.0):vec4(0.0);\n" +
            "  else FragColor=acc;\n" +
            "}\n";

    public void initGL() {
        System.out.println("GL Vendor: " + GL33.glGetString(GL33.GL_VENDOR));
        System.out.println("GL Renderer: " + GL33.glGetString(GL33.GL_RENDERER));
        GL33.glClearColor(0.05f, 0.05f, 0.1f, 1);
        GL33.glEnable(GL33.GL_BLEND);
        GL33.glBlendFunc(GL33.GL_SRC_ALPHA, GL33.GL_ONE_MINUS_SRC_ALPHA);
        GL33.glEnable(GL33.GL_CULL_FACE);
        GL33.glCullFace(GL33.GL_BACK);
        GL33.glDisable(GL33.GL_DEPTH_TEST);

        try {
            shaderProgram = createProgram(VERT, FRAG);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Unit cube
        float[] verts = { -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
                -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f };
        int[] idx = { 0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 1, 2, 6, 1, 6, 5, 2, 3, 7, 2, 7, 6, 3, 0, 4,
                3, 4, 7 };

        vao = GL33.glGenVertexArrays();
        GL33.glBindVertexArray(vao);
        vbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        FloatBuffer vb = MemoryUtil.memAllocFloat(verts.length);
        vb.put(verts).flip();
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, vb, GL33.GL_STATIC_DRAW);
        MemoryUtil.memFree(vb);
        GL33.glVertexAttribPointer(0, 3, GL33.GL_FLOAT, false, 12, 0);
        GL33.glEnableVertexAttribArray(0);
        ebo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer ib = MemoryUtil.memAllocInt(idx.length);
        ib.put(idx).flip();
        GL33.glBufferData(GL33.GL_ELEMENT_ARRAY_BUFFER, ib, GL33.GL_STATIC_DRAW);
        MemoryUtil.memFree(ib);
        GL33.glBindVertexArray(0);

        if (volume != null)
            uploadVolume();
    }

    // ---- Phase 1: Pre-computed Gradient Volume ----
    private void uploadVolume() {
        int zStep = (dimZ > 512) ? 2 : 1;
        int tz = dimZ / zStep;
        long sz = (long) dimX * dimY * tz;

        // Volume texture (R16)
        volumeTexture = GL33.glGenTextures();
        GL33.glBindTexture(GL33.GL_TEXTURE_3D, volumeTexture);
        set3DParams();
        ShortBuffer sb = MemoryUtil.memAllocShort((int) sz);
        for (int z = 0; z < dimZ; z += zStep) {
            if (z / zStep >= tz)
                break;
            for (int y = 0; y < dimY; y++)
                for (int x = 0; x < dimX; x++)
                    sb.put((short) (volume[z][y][x] ^ 0x8000));
        }
        sb.flip();
        GL33.glPixelStorei(GL33.GL_UNPACK_ALIGNMENT, 1);
        GL33.glTexImage3D(GL33.GL_TEXTURE_3D, 0, GL33.GL_R16, dimX, dimY, tz, 0, GL33.GL_RED, GL33.GL_UNSIGNED_SHORT,
                sb);
        MemoryUtil.memFree(sb);

        // Gradient texture (RGB8 = 事前計算法線)
        gradientTexture = GL33.glGenTextures();
        GL33.glBindTexture(GL33.GL_TEXTURE_3D, gradientTexture);
        set3DParams();
        byte[] grad = computeGradients(zStep, tz);
        ByteBuffer bb = MemoryUtil.memAlloc(grad.length);
        bb.put(grad).flip();
        GL33.glTexImage3D(GL33.GL_TEXTURE_3D, 0, GL33.GL_RGB8, dimX, dimY, tz, 0, GL33.GL_RGB, GL33.GL_UNSIGNED_BYTE,
                bb);
        MemoryUtil.memFree(bb);
    }

    private byte[] computeGradients(int zStep, int tz) {
        byte[] g = new byte[dimX * dimY * tz * 3];
        int idx = 0;
        for (int z = 0; z < dimZ; z += zStep) {
            if (z / zStep >= tz)
                break;
            for (int y = 0; y < dimY; y++) {
                for (int x = 0; x < dimX; x++) {
                    float dx = (sv(x + 1, y, z) - sv(x - 1, y, z)) * 0.5f;
                    float dy = (sv(x, y + 1, z) - sv(x, y - 1, z)) * 0.5f;
                    float dz = (sv(x, y, z + 1) - sv(x, y, z - 1)) * 0.5f;
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len < 1e-6f) {
                        len = 1;
                    }
                    // -1..1 → 0..255
                    g[idx++] = (byte) ((dx / len * 0.5f + 0.5f) * 255);
                    g[idx++] = (byte) ((dy / len * 0.5f + 0.5f) * 255);
                    g[idx++] = (byte) ((dz / len * 0.5f + 0.5f) * 255);
                }
            }
        }
        return g;
    }

    private float sv(int x, int y, int z) {
        x = Math.max(0, Math.min(dimX - 1, x));
        y = Math.max(0, Math.min(dimY - 1, y));
        z = Math.max(0, Math.min(dimZ - 1, z));
        return volume[z][y][x];
    }

    private void set3DParams() {
        GL33.glTexParameteri(GL33.GL_TEXTURE_3D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_3D, GL33.GL_TEXTURE_WRAP_T, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_3D, GL33.GL_TEXTURE_WRAP_R, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_3D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_3D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_LINEAR);
    }

    // ---- Phase 3: TF 1Dテクスチャのアップロード ----
    private void uploadTransferFunction() {
        if (transferFunction == null)
            return;
        if (tfTexture != 0)
            GL33.glDeleteTextures(tfTexture);
        tfTexture = transferFunction.buildAndUploadGL(windowLevel, windowWidth);
        tfDirty = false;
    }

    public void paintGL(int width, int height) {
        GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);
        if (!dataReady || shaderProgram == 0)
            return;

        float w = width <= 0 ? 1f : (float) width, h = height <= 0 ? 1f : (float) height;
        GL33.glViewport(0, 0, (int) w, (int) h);
        GL33.glUseProgram(shaderProgram);

        if (tfDirty)
            uploadTransferFunction();

        float[] proj = perspective(45f, w / h, 0.1f, 100f);
        float[] view = translate(0, 0, -1.2f / zoom);
        // yaw(Y) -> pitch(X) の順にすることで、初期姿勢でも横回転が効きやすい
        float[] mdl = multiply(multiply(rotate(rotateY, 0, 1, 0), rotate(rotateX, 1, 0, 0)),
                scale(voxScaleX, voxScaleY, voxScaleZ));

        setMat4("projection", proj);
        setMat4("view", view);
        setMat4("model", mdl);
        u1f("stepSize", 0.002f);
        u1i("renderMode", renderMode);
        u1f("isoThreshold", isoThreshold);
        u1f("windowLevel", windowLevel);
        u1f("windowWidth", windowWidth);
        u3f("uOffset", offsetX, offsetY, offsetZ);
        u3f("lightDir", lightX, lightY, lightZ);
        u3f("clipMin", clipMinX, clipMinY, clipMinZ);
        u3f("clipMax", clipMaxX, clipMaxY, clipMaxZ);

        // Texture unit 0 = volume
        GL33.glActiveTexture(GL33.GL_TEXTURE0);
        GL33.glBindTexture(GL33.GL_TEXTURE_3D, volumeTexture);
        u1i("volumeTex", 0);

        // Texture unit 1 = gradient
        GL33.glActiveTexture(GL33.GL_TEXTURE1);
        GL33.glBindTexture(GL33.GL_TEXTURE_3D, gradientTexture);
        u1i("gradTex", 1);

        // Texture unit 2 = TF
        if (tfTexture != 0) {
            GL33.glActiveTexture(GL33.GL_TEXTURE2);
            GL33.glBindTexture(GL33.GL_TEXTURE_1D, tfTexture);
            u1i("tfTex", 2);
        }

        GL33.glBindVertexArray(vao);
        GL33.glDrawElements(GL33.GL_TRIANGLES, 36, GL33.GL_UNSIGNED_INT, 0);
        GL33.glBindVertexArray(0);
    }

    // ---- Uniform helpers ----
    private void u1f(String n, float v) {
        GL33.glUniform1f(GL33.glGetUniformLocation(shaderProgram, n), v);
    }

    private void u1i(String n, int v) {
        GL33.glUniform1i(GL33.glGetUniformLocation(shaderProgram, n), v);
    }

    private void u3f(String n, float x, float y, float z) {
        GL33.glUniform3f(GL33.glGetUniformLocation(shaderProgram, n), x, y, z);
    }

    private void setMat4(String n, float[] m) {
        int loc = GL33.glGetUniformLocation(shaderProgram, n);
        if (loc < 0)
            return;
        FloatBuffer b = MemoryUtil.memAllocFloat(16);
        b.put(m).flip();
        GL33.glUniformMatrix4fv(loc, false, b);
        MemoryUtil.memFree(b);
    }

    // ---- Shader compile ----
    private int createProgram(String vs, String fs) throws Exception {
        int v = GL33.glCreateShader(GL33.GL_VERTEX_SHADER);
        GL33.glShaderSource(v, vs);
        GL33.glCompileShader(v);
        int f = GL33.glCreateShader(GL33.GL_FRAGMENT_SHADER);
        GL33.glShaderSource(f, fs);
        GL33.glCompileShader(f);
        String vLog = GL33.glGetShaderInfoLog(v);
        if (!vLog.isBlank())
            System.err.println("VS: " + vLog);
        String fLog = GL33.glGetShaderInfoLog(f);
        if (!fLog.isBlank())
            System.err.println("FS: " + fLog);
        int p = GL33.glCreateProgram();
        GL33.glAttachShader(p, v);
        GL33.glAttachShader(p, f);
        GL33.glLinkProgram(p);
        return p;
    }

    // ---- Math ----
    private float[] perspective(float fov, float asp, float n, float f) {
        float t = (float) (1.0 / Math.tan(Math.toRadians(fov) / 2));
        return new float[] { t / asp, 0, 0, 0, 0, t, 0, 0, 0, 0, (f + n) / (n - f), -1, 0, 0, (2 * f * n) / (n - f),
                0 };
    }

    private float[] translate(float x, float y, float z) {
        return new float[] { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1 };
    }

    private float[] scale(float x, float y, float z) {
        return new float[] { x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1 };
    }

    private float[] rotate(float a, float x, float y, float z) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] { x * x * (1 - c) + c, y * x * (1 - c) + z * s, x * z * (1 - c) - y * s, 0,
                x * y * (1 - c) - z * s, y * y * (1 - c) + c, y * z * (1 - c) + x * s, 0,
                x * z * (1 - c) + y * s, y * z * (1 - c) - x * s, z * z * (1 - c) + c, 0, 0, 0, 0, 1 };
    }

    private float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int row = 0; row < 4; row++)
                r[c * 4 + row] = a[row] * b[c * 4] + a[4 + row] * b[c * 4 + 1] + a[8 + row] * b[c * 4 + 2]
                        + a[12 + row] * b[c * 4 + 3];
        return r;
    }
}
