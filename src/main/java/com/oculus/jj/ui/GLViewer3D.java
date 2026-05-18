package com.oculus.jj.ui;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;
import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.nio.*;
import org.lwjgl.system.MemoryUtil;
import java.util.function.Consumer;

import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

/**
 * GLViewer3D - HOROS-20240407準拠 GPU Volume Renderer
 * Phase 1: Pre-computed Gradient Volume (事前勾配計算)
 * Phase 2: Phong照明 (Ambient + Diffuse + Specular)
 * Phase 3: Transfer Function 1Dテクスチャ
 */
public class GLViewer3D extends AWTGLCanvas {

    public static final int ENGINE_CPU = 0;
    public static final int ENGINE_GPU = 1;

    private int shaderProgram;
    private int vao, vbo, ebo;
    private int volumeTexture;
    private int gradientTexture; // Phase 1: 事前計算勾配テクスチャ
    private int tfTexture; // Phase 3: Transfer Function 1Dテクスチャ
    private int cpuQuadProgram;
    private int cpuQuadVao, cpuQuadVbo, cpuQuadEbo;
    private int cpuFrameTexture;
    private int cpuFrameWidth = -1, cpuFrameHeight = -1;

    // ---- Orientation Cube (方位ボックス) リソース ----
    private int cubeShaderProgram;
    private int cubeVao, cubeVbo, cubeEbo;
    private int[] orientationTextures = new int[6]; // A, P, S, I, R, L
    private final String[] labelStrings = { "A", "P", "S", "I", "R", "L" };

    private short[] volume;
    private byte[] gradientData;
    private int dimX, dimY, dimZ;
    private boolean dataReady = false;

    private float rotateX = -1.5707963f;
    private float rotateY = 1.5707963f;
    private float zoom = 1.0f;
    private float offsetX = 0, offsetY = 0, offsetZ = 0;
    private static final float PAN_LIMIT = 0.35f;
    private int renderMode = 1; // 0=MIP, 1=VR, 2=ISO

    // Phase 2: Phong ライト方向
    private float lightX = -0.577f, lightY = -0.577f, lightZ = 0.577f;

    // WW/WL (HOROS準拠: 3D用の初期表示は1:1マッピング)
    private float windowLevel = 1024.0f;
    private float windowWidth = 4096.0f;

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
    private int gpuFrameCount = 0; // GPU描画フレーム数 (診断用)
    private float spacingX = 1.0f, spacingY = 1.0f, spacingZ = 1.0f;
    private int engineMode = ENGINE_CPU;
    private CPURaycastRenderer cpuRenderer;
    private float stepSize = 0.002f;
    private int useShading = 1;
    private boolean isPerspective = true;
    private volatile BufferedImage cpuImage;
    private final Object cpuRenderLock = new Object();
    private boolean cpuRenderInFlight = false;
    private boolean cpuRenderRequested = false;
    private Consumer<Graphics2D> overlayPainter;
    private volatile boolean gpuContextFailed = false;
    private Runnable gpuContextFailureHandler;
    private int overlayTexture = 0;
    private int overlayWidth = -1;
    private int overlayHeight = -1;

    private BufferedImage reusableOverlayImage;
    private java.nio.ByteBuffer reusableOverlayBuffer;
    private boolean overlayDirty = true;

    public void markOverlayDirty() {
        this.overlayDirty = true;
    }

    public GLViewer3D(GLData data, short[] volume, int dx, int dy, int dz) {
        super(data);
        this.volume = volume;
        this.dimX = dx;
        this.dimY = dy;
        this.dimZ = dz;

        java.awt.event.MouseAdapter overlayMouseListener = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                markOverlayDirty();
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                markOverlayDirty();
            }
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                markOverlayDirty();
            }
            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                markOverlayDirty();
            }
        };
        this.addMouseListener(overlayMouseListener);
        this.addMouseMotionListener(overlayMouseListener);
        this.addMouseWheelListener(overlayMouseListener);
    }

    public void setVolumeData(short[] vol, byte[] grad, int w, int h, int d) {
        markOverlayDirty();
        this.volume = vol;
        this.gradientData = grad;
        this.dimX = w;
        this.dimY = h;
        this.dimZ = d;
        this.dataReady = true;
        // grad != null は GPU 初期化パス専用。CPU レンダラーは既にボリュームを持っているため
        // synchronized な setVolumeData() を呼ばない（render() ロック競合によるブロックを防ぐ）。
        if (cpuRenderer != null && grad == null) {
            cpuRenderer.setVolumeData(vol, w, h, d);
            requestCpuRender();
        }
    }

    public void setCpuRenderer(CPURaycastRenderer renderer) {
        markOverlayDirty();
        this.cpuRenderer = renderer;
        if (renderer != null) {
            renderer.setVolumeData(volume, dimX, dimY, dimZ);
            renderer.setRenderMode(renderMode);
            renderer.setRotation(rotateX, rotateY);
            renderer.setZoom(zoom);
            renderer.setOffset(offsetX, offsetY, offsetZ);
            renderer.setClipping(clipMinX, clipMaxX, clipMinY, clipMaxY, clipMinZ, clipMaxZ);
            renderer.setUseShading(this.useShading == 1);
            renderer.setPerspective(this.isPerspective);
            if (transferFunction != null) {
                renderer.setTransferFunction(transferFunction);
            }
            renderer.setWindowLevel(windowLevel, windowWidth);
        }
        requestCpuRender();
    }

    public void setOverlayPainter(Consumer<Graphics2D> overlayPainter) {
        this.overlayPainter = overlayPainter;
        repaint();
    }

    public void setGpuContextFailureHandler(Runnable handler) {
        this.gpuContextFailureHandler = handler;
    }

    /**
     * HOROS互換のエンジン切替API。
     * 0=CPU, 1=GPU
     */
    public void setEngine(int mode) {
        this.engineMode = mode;
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        } else {
            repaint();
        }
    }

    public int getEngine() {
        return engineMode;
    }

    public BufferedImage getCpuImage() {
        return cpuImage;
    }

    private float lodFactor = 0.25f;

    public void setLodFactor(float factor) {
        this.lodFactor = factor;
        if (cpuRenderer != null) {
            cpuRenderer.setStepSizeFactor(factor);
        }
        this.stepSize = 0.001f + factor * (0.015f - 0.001f);
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        } else {
            repaint();
        }
    }

    public void requestCpuRender() {
        if (engineMode != ENGINE_CPU || cpuRenderer == null) {
            repaint();
            return;
        }

        int width = getWidth();
        int height = getHeight();
        if (cpuImage == null && width > 0 && height > 0) {
            java.awt.EventQueue.invokeLater(this::repaint);
        }

        synchronized (cpuRenderLock) {
            cpuRenderRequested = true;
            if (cpuRenderInFlight) {
                return;
            }
            cpuRenderInFlight = true;
        }

        Thread worker = new Thread(() -> {
            while (true) {
                synchronized (cpuRenderLock) {
                    cpuRenderRequested = false;
                }

                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    int maxW = (int) (720 - lodFactor * (720 - 180));
                    int renderW = Math.min(w, maxW);
                    int renderH = (int) (h * ((float) renderW / w));
                    cpuImage = cpuRenderer.render(renderW, renderH);
                }
                java.awt.EventQueue.invokeLater(this::repaint);

                synchronized (cpuRenderLock) {
                    if (!cpuRenderRequested) {
                        cpuRenderInFlight = false;
                        break;
                    }
                }
            }
        }, "GLViewer3D-CPU");
        worker.setDaemon(true);
        worker.start();
    }

    public void setRenderMode(int mode) {
        markOverlayDirty();
        this.renderMode = mode;
        if (cpuRenderer != null) {
            cpuRenderer.setRenderMode(mode);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        } else {
            repaint(); // GPUモード用に描画を強制更新する
        }
    }

    public void setRotation(float x, float y) {
        markOverlayDirty();
        this.rotateX = x;
        this.rotateY = y;
        if (cpuRenderer != null) {
            cpuRenderer.setRotation(x, y);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        }
    }

    public void setZoom(float z) {
        markOverlayDirty();
        this.zoom = z;
        if (cpuRenderer != null) {
            cpuRenderer.setZoom(z);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        }
    }

    public void setOffset(float x, float y, float z) {
        markOverlayDirty();
        offsetX = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, x));
        offsetY = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, y));
        offsetZ = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, z));
        if (cpuRenderer != null) {
            cpuRenderer.setOffset(offsetX, offsetY, offsetZ);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        }
    }

    public void addOffset(float dx, float dy, float dz) {
        markOverlayDirty();
        offsetX = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, offsetX + dx));
        offsetY = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, offsetY + dy));
        offsetZ = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, offsetZ + dz));
        if (cpuRenderer != null) {
            cpuRenderer.setOffset(offsetX, offsetY, offsetZ);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        }
    }

    public void setClipping(float x0, float x1, float y0, float y1, float z0, float z1) {
        markOverlayDirty();
        clipMinX = x0;
        clipMaxX = x1;
        clipMinY = y0;
        clipMaxY = y1;
        clipMinZ = z0;
        clipMaxZ = z1;
        if (cpuRenderer != null) {
            cpuRenderer.setClipping(x0, x1, y0, y1, z0, z1);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        }
    }

    public void setLightDirection(float x, float y, float z) {
        markOverlayDirty();
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l > 0) {
            lightX = x / l;
            lightY = y / l;
            lightZ = z / l;
        }
        if (cpuRenderer != null) {
            cpuRenderer.setLightDirection(x, y, z);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        }
    }

    public void setTransferFunction(TransferFunction tf) {
        markOverlayDirty();
        this.transferFunction = tf;
        this.tfDirty = true;
        if (cpuRenderer != null) {
            cpuRenderer.setTransferFunction(tf);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        }
    }

    public void setIsoThreshold(float t) {
        markOverlayDirty();
        this.isoThreshold = t;
        if (cpuRenderer != null) {
            cpuRenderer.setIsoThreshold(t);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        }
    }

    /** HOROS準拠: WW/WL でTFの参照範囲をシフト・スケールする */
    public void setWindowLevel(float wl, float ww) {
        markOverlayDirty();
        this.windowLevel = wl;
        this.windowWidth = Math.max(1, ww);
        this.tfDirty = true;
        if (cpuRenderer != null) {
            cpuRenderer.setWindowLevel(this.windowLevel, this.windowWidth);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        } else {
            repaint(); // GPUモード用の描画更新を強制する
        }
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
        markOverlayDirty();
        // 各軸の物理總幅 = ボクセル数 x スペーシング
        float physX = dimX * sx;
        float physY = dimY * sy;
        float physZ = dimZ * sz;
        // 最大軸を 1.0 に正規化
        float maxPhys = Math.max(physX, Math.max(physY, physZ));
        voxScaleX = (maxPhys > 0) ? physX / maxPhys : 1.0f;
        voxScaleY = (maxPhys > 0) ? physY / maxPhys : 1.0f;
        voxScaleZ = (maxPhys > 0) ? physZ / maxPhys : 1.0f;
        if (cpuRenderer != null) {
            cpuRenderer.setVoxelSpacing(sx, sy, sz);
        }
    }

    public void setStepSize(float s) {
        this.stepSize = s;
        if (engineMode != ENGINE_CPU) {
            repaint();
        }
    }

    public void setUseShading(boolean shading) {
        this.useShading = shading ? 1 : 0;
        if (cpuRenderer != null) {
            cpuRenderer.setUseShading(shading);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        } else {
            repaint();
        }
    }

    public void setPerspective(boolean p) {
        this.isPerspective = p;
        if (cpuRenderer != null) {
            cpuRenderer.setPerspective(p);
        }
        if (engineMode == ENGINE_CPU) {
            requestCpuRender();
        } else {
            repaint();
        }
    }

    private float[] orthographic(float left, float right, float bottom, float top, float near, float far) {
        float x_ortho = 2.0f / (right - left);
        float y_ortho = 2.0f / (top - bottom);
        float z_ortho = -2.0f / (far - near);
        float tx = -(right + left) / (right - left);
        float ty = -(top + bottom) / (top - bottom);
        float tz = -(far + near) / (far - near);
        return new float[] {
            x_ortho, 0, 0, 0,
            0, y_ortho, 0, 0,
            0, 0, z_ortho, 0,
            tx, ty, tz, 1
        };
    }

    // --- シェーダー ---
    private static final String VERT = "#version 330 core\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "out vec3 vTex;\n" +
            "out vec3 vCam;\n" +
            "uniform mat4 model,view,projection;\n" +
            "uniform vec3 uCamPos;\n" +
            "void main(){\n" +
            "  mat4 mv=view*model;\n" +
            "  gl_Position=projection*mv*vec4(aPos,1);\n" +
            "  vTex=aPos+vec3(0.5);\n" +
            "  vCam=uCamPos;\n" +
            "}\n";

    private static final String FRAG = "#version 330 core\n" +
            "in vec3 vTex, vCam;\n" +
            "out vec4 FragColor;\n" +
            "uniform sampler3D volumeTex;\n" +
            "uniform sampler3D gradTex;\n" +
            "uniform sampler1D tfTex;\n" +
            "uniform float stepSize;\n" +
            "uniform int useShading;\n" +
            "uniform int renderMode;\n" +
            "uniform float isoThreshold;\n" +
            "uniform float windowLevel;\n" +
            "uniform float windowWidth;\n" +
            "uniform vec3 uOffset;\n" +
            "uniform vec3 lightDir;\n" +
            "uniform vec3 clipMin, clipMax;\n" +
            "void main(){\n" +
            // isoThreshold < -9999.0 はデバッグテスト: ジオメトリが描画されているか確認 (黄色出力)
            "  if(isoThreshold < -9999.0){ FragColor=vec4(1.0,1.0,0.0,1.0); return; }\n" +
            "  vec3 ray=normalize(vTex-vCam);\n" +
            "  vec3 pos=vTex+uOffset;\n" +
            "  vec4 acc=vec4(0);\n" +
            "  float maxV=0;\n" +
            // jitterを小さく抑える（0.0~0.5）
            "  float jit=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453)*0.5;\n" +
            "  pos+=ray*stepSize*jit;\n" +
            "  for(int i=0;i<512;i++){\n" +
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
            "          vec3 col;\n" +
            "          if(useShading==1){\n" +
            "            vec3 N=normalize(-(texture(gradTex,pos).xyz*2.0-1.0));\n" +
            "            vec3 L=normalize(lightDir);\n" +
            "            vec3 V=normalize(-ray);\n" +
            "            vec3 H=normalize(L+V);\n" + // Blinn-Phong
            "            float ambient=0.20;\n" +
            "            float diffuse=max(dot(N,L),0.0)*0.90;\n" +
            "            float spec=pow(max(dot(N,H),0.0),15.0)*0.30;\n" +
            "            col=tf.rgb*(ambient+diffuse)+vec3(spec);\n" +
            "          } else {\n" +
            "            col=tf.rgb;\n" +
            "          }\n" +
            "          acc.rgb+=(1.0-acc.a)*col*tf.a;\n" +
            "          acc.a+=(1.0-acc.a)*tf.a;\n" +
            "          if(acc.a>=0.95) break;\n" +
            "        }\n" +
            "      } else {\n" +
            "        if(raw>isoThreshold){\n" +
            // 外向き法線 (HU勾配を否定)
            "          vec3 N=normalize(-(texture(gradTex,pos).xyz*2.0-1.0));\n" +
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
            // MIP: 常にopaque (0.0=黒でもalphaを1にして背景と区別可能にする)
            "  if(renderMode==0) FragColor=vec4(vec3(max(maxV,0.0)),1.0);\n" +
            "  else FragColor=acc;\n" +
            "}\n";

    private static final String CUBE_VS = "#version 330 core\n" +
            "layout (location = 0) in vec3 aPos;\n" +
            "layout (location = 1) in vec2 aTexCoord;\n" +
            "out vec2 TexCoord;\n" +
            "uniform mat4 model, view, projection;\n" +
            "void main() {\n" +
            "    gl_Position = projection * view * model * vec4(aPos, 1.0);\n" +
            "    TexCoord = aTexCoord;\n" +
            "}\n";

    private static final String CUBE_FS = "#version 330 core\n" +
            "out vec4 FragColor;\n" +
            "in vec2 TexCoord;\n" +
            "uniform sampler2D ourTexture;\n" +
            "void main() {\n" +
            "    vec4 texColor = texture(ourTexture, TexCoord);\n" +
            "    if (texColor.a < 0.1) discard;\n" +
            "    FragColor = texColor;\n" +
            "}\n";

    private static final String CPU_QUAD_VS = "#version 330 core\n" +
            "layout (location = 0) in vec2 aPos;\n" +
            "layout (location = 1) in vec2 aUv;\n" +
            "out vec2 vUv;\n" +
            "void main(){\n" +
            "  vUv = aUv;\n" +
            "  gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "}\n";

    private static final String CPU_QUAD_FS = "#version 330 core\n" +
            "in vec2 vUv;\n" +
            "out vec4 FragColor;\n" +
            "uniform sampler2D uTex;\n" +
            "void main(){\n" +
            "  FragColor = texture(uTex, vUv);\n" +
            "}\n";

    @Override
    public void initGL() {
        GL.createCapabilities(); // LWJGLのOpenGLコンテキストを有効化 (重要)

        System.out.println("OpenGL Version: " + GL33.glGetString(GL33.GL_VERSION));
        System.out.println("GL Renderer: " + GL33.glGetString(GL33.GL_RENDERER));
        GL33.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GL33.glEnable(GL33.GL_BLEND);
        GL33.glBlendFunc(GL33.GL_SRC_ALPHA, GL33.GL_ONE_MINUS_SRC_ALPHA);
        GL33.glDisable(GL33.GL_CULL_FACE);
        GL33.glDisable(GL33.GL_DEPTH_TEST);

        // GPU ボリュームシェーダー (複雑、失敗しても CPU モードを維持)
        try {
            shaderProgram = createProgram(VERT, FRAG);
            System.out.println("DEBUG: GPU shader compiled OK. program=" + shaderProgram);
        } catch (Throwable e) {
            System.err.println("DEBUG: GPU shader compile FAILED (CPU fallback remains): " + e);
            e.printStackTrace();
        }

        // CPU フレーム表示クワッドシェーダー (必須: CPU モードに使用)
        try {
            cpuQuadProgram = createProgram(CPU_QUAD_VS, CPU_QUAD_FS);
            initCpuQuad();
            System.out.println("DEBUG: CPU quad shader+VAO ready. program=" + cpuQuadProgram + " vao=" + cpuQuadVao);
        } catch (Throwable e) {
            System.err.println("DEBUG: CPU quad shader FAILED: " + e);
            e.printStackTrace();
        }

        // 方位キューブシェーダー (任意)
        try {
            cubeShaderProgram = createProgram(CUBE_VS, CUBE_FS);
            initCube();
            createOrientationTextures();
            System.out.println("DEBUG: Orientation cube shader ready. program=" + cubeShaderProgram);
        } catch (Throwable e) {
            System.err.println("DEBUG: Orientation cube shader FAILED (non-critical): " + e);
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
        // NOTE: uploadVolume() は paintGL() の lazy-upload ブロックで行う。
        // ここで呼ぶと EDT ブロック + 例外がレンダーループ停止に繋がるため除外。
    }

    // ---- Phase 1: Pre-computed Gradient Volume ----
    private void uploadVolume() {
        int zStep = (dimZ > 512) ? 2 : 1;
        if (gradientData != null && dimX > 0 && dimY > 0) {
            int calculatedTz = gradientData.length / (dimX * dimY * 3);
            if (calculatedTz > 0) {
                zStep = Math.max(1, dimZ / calculatedTz);
            }
        }
        int tz = dimZ / zStep;

        // 3D Texture for Volume (R16 = 16bit Normalized)
        volumeTexture = GL33.glGenTextures();
        GL33.glBindTexture(GL33.GL_TEXTURE_3D, volumeTexture);
        set3DParams();

        // 空のテクスチャ枠を確保
        GL33.glTexImage3D(GL33.GL_TEXTURE_3D, 0, GL33.GL_R16, dimX, dimY, tz, 0, GL33.GL_RED, GL33.GL_UNSIGNED_SHORT,
                (ShortBuffer) null);

        // 分割転送 (1回あたり 64 スライス) でドライバハングを防止
        int chunkSize = 64;
        for (int startZ = 0; startZ < tz; startZ += chunkSize) {
            int currentChunk = Math.min(chunkSize, tz - startZ);
            long sz = (long) dimX * dimY * currentChunk;
            ShortBuffer sb = MemoryUtil.memAllocShort((int) sz);
            for (int zi = 0; zi < currentChunk; zi++) {
                int zOffset = (startZ + zi) * zStep * dimY * dimX;
                for (int y = 0; y < dimY; y++) {
                    int yOffset = y * dimX;
                    for (int x = 0; x < dimX; x++) {
                        sb.put((short) (volume[zOffset + yOffset + x] ^ 0x8000));
                    }
                }
            }
            sb.flip();
            GL33.glPixelStorei(GL33.GL_UNPACK_ALIGNMENT, 1);
            GL33.glTexSubImage3D(GL33.GL_TEXTURE_3D, 0, 0, 0, startZ, dimX, dimY, currentChunk, GL33.GL_RED,
                    GL33.GL_UNSIGNED_SHORT, sb);
            MemoryUtil.memFree(sb);
            System.out.println("DEBUG: GPU Volume Uploaded: " + (startZ + currentChunk) + "/" + tz);
        }

        // Gradient texture (RGB8 = 事前計算法線)
        if (gradientData != null) {
            gradientTexture = GL33.glGenTextures();
            GL33.glBindTexture(GL33.GL_TEXTURE_3D, gradientTexture);
            set3DParams();
            ByteBuffer bb = MemoryUtil.memAlloc(gradientData.length);
            bb.put(gradientData).flip();
            GL33.glTexImage3D(GL33.GL_TEXTURE_3D, 0, GL33.GL_RGB8, dimX, dimY, tz, 0, GL33.GL_RGB,
                    GL33.GL_UNSIGNED_BYTE,
                    bb);
            MemoryUtil.memFree(bb);
        }
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
        tfTexture = transferFunction.buildAndUploadGL();
        tfDirty = false;
    }

    private int frameCount = 0;

    @Override
    public void paintGL() {
        frameCount++;
        if (engineMode == ENGINE_GPU)
            gpuFrameCount++;
        if (frameCount % 60 == 0) {
            System.out.println(
                    "DEBUG: paintGL() executed. engineMode=" + engineMode + ", size=" + getWidth() + "x" + getHeight());
        }
        int width = getWidth();
        int height = getHeight();
        // 最初の3GPUフレームは明るい緑色の背景でジオメトリ描画を確認
        if (gpuFrameCount > 0 && gpuFrameCount <= 3) {
            GL33.glClearColor(0.0f, 0.7f, 0.0f, 1.0f);
        } else {
            GL33.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        }
        GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);

        if (engineMode == ENGINE_CPU) {
            drawCpuFrame(width, height);
            drawOrientationCube(width, height);
            drawOverlayTexture(width, height);
            swapBuffers();
            return;
        }

        if (!dataReady || volume == null)
            return;

        // データがあるのにテクスチャが未作成ならアップロード
        if (volumeTexture == 0) {
            try {
                uploadVolume();
            } catch (Throwable t) {
                System.err.println("DEBUG: Critical error during uploadVolume:");
                t.printStackTrace();
                volumeTexture = -1; // 再試行防止
                return;
            }
            if (shaderProgram == 0)
                return; // initGLがまだなら中断
        }

        if (shaderProgram == 0)
            return;

        // 深度テスト有効化 (CPU quad が glDisable するため毎フレーム再設定が必要)
        GL33.glEnable(GL33.GL_DEPTH_TEST);
        GL33.glDepthFunc(GL33.GL_LESS);
        // カリング無効化 (winding orderトラブル防止)
        GL33.glDisable(GL33.GL_CULL_FACE);

        float w = width <= 0 ? 1f : (float) width, h = height <= 0 ? 1f : (float) height;
        GL33.glViewport(0, 0, (int) w, (int) h);
        GL33.glUseProgram(shaderProgram);
        int err = GL33.glGetError();
        if (err != GL33.GL_NO_ERROR) {
            System.err.println("GL Error after glUseProgram: " + err);
        }

        if (tfDirty)
            uploadTransferFunction();

        float[] proj;
        if (isPerspective) {
            proj = perspective(45f, w / h, 0.1f, 100f);
        } else {
            float size = 0.5f / zoom;
            proj = orthographic(-size * (w / h), size * (w / h), -size, size, 0.1f, 100f);
        }
        float[] view = translate(0, 0, -1.2f / zoom);
        // yaw(Y) -> pitch(X) の順にすることで、初期姿勢でも横回転が効きやすい
        float[] mdl = multiply(multiply(rotate(rotateY, 0, 1, 0), rotate(rotateX, 1, 0, 0)),
                scale(voxScaleX, voxScaleY, voxScaleZ));

        setMat4("projection", proj);
        setMat4("view", view);
        setMat4("model", mdl);

        // CPU側でモデル/テクスチャ空間 of カメラ位置を逆算してユニフォーム送信 (inverseマトリクス依存排除)
        float camZVal = 1.2f / zoom;
        float[] rot = multiply(rotate(rotateY, 0, 1, 0), rotate(rotateX, 1, 0, 0));
        float rx = rot[2] * camZVal;
        float ry = rot[6] * camZVal;
        float rz = rot[10] * camZVal;
        float cx = rx / voxScaleX;
        float cy = ry / voxScaleY;
        float cz = rz / voxScaleZ;
        float vCamX = cx + 0.5f;
        float vCamY = cy + 0.5f;
        float vCamZ = cz + 0.5f;
        GL33.glUniform3f(GL33.glGetUniformLocation(shaderProgram, "uCamPos"), vCamX, vCamY, vCamZ);

        u1f("stepSize", stepSize);
        u1i("useShading", useShading);
        // 最初の3GPUフレームで診断情報をログ出力
        if (gpuFrameCount <= 3) {
            System.out.println("DEBUG GPU[" + gpuFrameCount + "]: renderMode=" + renderMode
                    + " wl=" + windowLevel + " ww=" + windowWidth
                    + " shader=" + shaderProgram + " vao=" + vao + " volTex=" + volumeTexture
                    + " gradTex=" + gradientTexture + " tfTex=" + tfTexture
                    + " voxScale=(" + voxScaleX + "," + voxScaleY + "," + voxScaleZ + ")"
                    + " uCamPos=(" + vCamX + "," + vCamY + "," + vCamZ + ")"
                    + " dataReady=" + dataReady + " size=" + width + "x" + height);
        }
        u1i("renderMode", renderMode);
        // 最初の3GPUフレームはisoThreshold=-10000でシェーダーテスト(黄色出力でジオメトリ確認)
        u1f("isoThreshold", gpuFrameCount <= 3 ? -10000.0f : isoThreshold);
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
        int drawErr = GL33.glGetError();
        if (drawErr != GL33.GL_NO_ERROR) {
            System.err.println("DEBUG: GL error after volume draw: " + drawErr);
        }

        // 方位ボックスはカリング不要 — 状態を戻してから描画
        GL33.glDisable(GL33.GL_CULL_FACE);

        // ---- 方位ボックスの描画 (右上) ----
        drawOrientationCube(width, height);

        // ---- Transparent Java2D overlays rendered as an OpenGL texture ----
        drawOverlayTexture(width, height);

        swapBuffers();
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
        if (GL33.glGetShaderi(v, GL33.GL_COMPILE_STATUS) == GL33.GL_FALSE) {
            String log = GL33.glGetShaderInfoLog(v);
            GL33.glDeleteShader(v);
            throw new Exception("Vertex shader compile error: " + log);
        }
        String vLog = GL33.glGetShaderInfoLog(v);
        if (!vLog.isBlank())
            System.err.println("VS: " + vLog);

        int f = GL33.glCreateShader(GL33.GL_FRAGMENT_SHADER);
        GL33.glShaderSource(f, fs);
        GL33.glCompileShader(f);
        if (GL33.glGetShaderi(f, GL33.GL_COMPILE_STATUS) == GL33.GL_FALSE) {
            String log = GL33.glGetShaderInfoLog(f);
            GL33.glDeleteShader(v);
            GL33.glDeleteShader(f);
            throw new Exception("Fragment shader compile error: " + log);
        }
        String fLog = GL33.glGetShaderInfoLog(f);
        if (!fLog.isBlank())
            System.err.println("FS: " + fLog);

        int p = GL33.glCreateProgram();
        GL33.glAttachShader(p, v);
        GL33.glAttachShader(p, f);
        GL33.glLinkProgram(p);
        GL33.glDeleteShader(v);
        GL33.glDeleteShader(f);
        if (GL33.glGetProgrami(p, GL33.GL_LINK_STATUS) == GL33.GL_FALSE) {
            String log = GL33.glGetProgramInfoLog(p);
            GL33.glDeleteProgram(p);
            throw new Exception("Shader link error: " + log);
        }
        return p;
    }

    private void initCpuQuad() {
        float[] verts = {
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                1f, 1f, 1f, 1f,
                -1f, 1f, 0f, 1f
        };
        int[] idx = { 0, 1, 2, 2, 3, 0 };

        cpuQuadVao = GL33.glGenVertexArrays();
        cpuQuadVbo = GL33.glGenBuffers();
        cpuQuadEbo = GL33.glGenBuffers();

        GL33.glBindVertexArray(cpuQuadVao);
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, cpuQuadVbo);
        FloatBuffer vb = MemoryUtil.memAllocFloat(verts.length);
        vb.put(verts).flip();
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, vb, GL33.GL_STATIC_DRAW);
        MemoryUtil.memFree(vb);

        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, cpuQuadEbo);
        IntBuffer ib = MemoryUtil.memAllocInt(idx.length);
        ib.put(idx).flip();
        GL33.glBufferData(GL33.GL_ELEMENT_ARRAY_BUFFER, ib, GL33.GL_STATIC_DRAW);
        MemoryUtil.memFree(ib);

        GL33.glVertexAttribPointer(0, 2, GL33.GL_FLOAT, false, 4 * 4, 0);
        GL33.glEnableVertexAttribArray(0);
        GL33.glVertexAttribPointer(1, 2, GL33.GL_FLOAT, false, 4 * 4, 2 * 4);
        GL33.glEnableVertexAttribArray(1);
        GL33.glBindVertexArray(0);
    }

    private boolean cpuFirstFrameLogged = false;

    private void drawCpuFrame(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (cpuQuadProgram == 0) {
            if (!cpuFirstFrameLogged) {
                System.err.println("DEBUG: drawCpuFrame() — cpuQuadProgram=0, GL 初期化未完了またはシェーダーコンパイル失敗。画像は描画されません。");
                cpuFirstFrameLogged = true;
            }
            return;
        }

        if (cpuImage == null && !cpuRenderInFlight && cpuRenderer != null) {
            requestCpuRender();
        }

        BufferedImage img = cpuImage;
        if (img == null) {
            return;
        }

        if (!cpuFirstFrameLogged) {
            System.out.println("DEBUG: drawCpuFrame() — cpuImage ready " + img.getWidth() + "x" + img.getHeight()
                    + ", cpuQuadProgram=" + cpuQuadProgram + ", cpuQuadVao=" + cpuQuadVao);
            cpuFirstFrameLogged = true;
        }

        int iw = img.getWidth();
        int ih = img.getHeight();
        if (iw <= 0 || ih <= 0) {
            return;
        }

        int[] argb = new int[iw * ih];
        img.getRGB(0, 0, iw, ih, argb, 0, iw);
        ByteBuffer bb = MemoryUtil.memAlloc(iw * ih * 4);
        // OpenGL は行の先頭を下端として解釈するため、上下反転してから転送する
        for (int row = ih - 1; row >= 0; row--) {
            int srcBase = row * iw;
            for (int col = 0; col < iw; col++) {
                int p = argb[srcBase + col];
                bb.put((byte) ((p >> 16) & 0xFF));
                bb.put((byte) ((p >> 8) & 0xFF));
                bb.put((byte) (p & 0xFF));
                bb.put((byte) ((p >> 24) & 0xFF));
            }
        }
        bb.flip();

        if (cpuFrameTexture == 0) {
            cpuFrameTexture = GL33.glGenTextures();
        }
        GL33.glBindTexture(GL33.GL_TEXTURE_2D, cpuFrameTexture);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_T, GL33.GL_CLAMP_TO_EDGE);

        if (cpuFrameWidth != iw || cpuFrameHeight != ih) {
            GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA8, iw, ih, 0, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE,
                    bb);
            cpuFrameWidth = iw;
            cpuFrameHeight = ih;
        } else {
            GL33.glTexSubImage2D(GL33.GL_TEXTURE_2D, 0, 0, 0, iw, ih, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, bb);
        }
        MemoryUtil.memFree(bb);

        GL33.glDisable(GL33.GL_DEPTH_TEST);
        GL33.glViewport(0, 0, width, height);
        GL33.glUseProgram(cpuQuadProgram);
        GL33.glActiveTexture(GL33.GL_TEXTURE0);
        GL33.glBindTexture(GL33.GL_TEXTURE_2D, cpuFrameTexture);
        GL33.glUniform1i(GL33.glGetUniformLocation(cpuQuadProgram, "uTex"), 0);
        GL33.glBindVertexArray(cpuQuadVao);
        GL33.glDrawElements(GL33.GL_TRIANGLES, 6, GL33.GL_UNSIGNED_INT, 0);
        GL33.glBindVertexArray(0);
    }

    private void drawOverlayTexture(int width, int height) {
        if (overlayPainter == null || width <= 0 || height <= 0) {
            return;
        }

        boolean sizeChanged = (overlayWidth != width || overlayHeight != height);

        if (sizeChanged) {
            if (reusableOverlayImage != null) {
                reusableOverlayImage.flush();
            }
            reusableOverlayImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            if (reusableOverlayBuffer != null) {
                MemoryUtil.memFree(reusableOverlayBuffer);
            }
            reusableOverlayBuffer = MemoryUtil.memAlloc(width * height * 4);
        }

        if (overlayDirty || sizeChanged) {
            overlayDirty = false;

            Graphics2D g2d = reusableOverlayImage.createGraphics();
            try {
                g2d.setComposite(AlphaComposite.Clear);
                g2d.fillRect(0, 0, width, height);
                g2d.setComposite(AlphaComposite.SrcOver);
                overlayPainter.accept(g2d);
            } finally {
                g2d.dispose();
            }

            int[] argb = new int[width * height];
            reusableOverlayImage.getRGB(0, 0, width, height, argb, 0, width);

            reusableOverlayBuffer.clear();
            for (int row = height - 1; row >= 0; row--) {
                int srcBase = row * width;
                for (int col = 0; col < width; col++) {
                    int p = argb[srcBase + col];
                    reusableOverlayBuffer.put((byte) ((p >> 16) & 0xFF)); // R
                    reusableOverlayBuffer.put((byte) ((p >> 8) & 0xFF));  // G
                    reusableOverlayBuffer.put((byte) (p & 0xFF));         // B
                    reusableOverlayBuffer.put((byte) ((p >> 24) & 0xFF)); // A
                }
            }
            reusableOverlayBuffer.flip();

            if (overlayTexture == 0) {
                overlayTexture = GL33.glGenTextures();
            }
            GL33.glBindTexture(GL33.GL_TEXTURE_2D, overlayTexture);
            GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
            GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_LINEAR);
            GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
            GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_T, GL33.GL_CLAMP_TO_EDGE);

            if (sizeChanged) {
                GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA8, width, height, 0, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, reusableOverlayBuffer);
                overlayWidth = width;
                overlayHeight = height;
            } else {
                GL33.glTexSubImage2D(GL33.GL_TEXTURE_2D, 0, 0, 0, width, height, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, reusableOverlayBuffer);
            }
        }

        GL33.glDisable(GL33.GL_DEPTH_TEST);
        GL33.glEnable(GL33.GL_BLEND);
        GL33.glBlendFunc(GL33.GL_SRC_ALPHA, GL33.GL_ONE_MINUS_SRC_ALPHA);

        GL33.glViewport(0, 0, width, height);
        GL33.glUseProgram(cpuQuadProgram);
        GL33.glActiveTexture(GL33.GL_TEXTURE0);
        GL33.glBindTexture(GL33.GL_TEXTURE_2D, overlayTexture);
        GL33.glUniform1i(GL33.glGetUniformLocation(cpuQuadProgram, "uTex"), 0);
        GL33.glBindVertexArray(cpuQuadVao);
        GL33.glDrawElements(GL33.GL_TRIANGLES, 6, GL33.GL_UNSIGNED_INT, 0);
        GL33.glBindVertexArray(0);

        GL33.glDisable(GL33.GL_BLEND);
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

    // ---- Orientation Cube 初期化 ----
    private void initCube() {
        // 各面 4頂点 * 6面 = 24頂点 (pos.x, y, z, tex.u, v)
        float[] vertices = {
                // Anterior (+Y) - index 0
                -0.5f, 0.5f, 0.5f, 1, 0, 0.5f, 0.5f, 0.5f, 0, 0, 0.5f, 0.5f, -0.5f, 0, 1, -0.5f, 0.5f, -0.5f, 1, 1,
                // Posterior (-Y) - index 1
                0.5f, -0.5f, 0.5f, 1, 0, -0.5f, -0.5f, 0.5f, 0, 0, -0.5f, -0.5f, -0.5f, 0, 1, 0.5f, -0.5f, -0.5f, 1, 1,
                // Superior (+Z) - index 2
                -0.5f, -0.5f, 0.5f, 1, 0, 0.5f, -0.5f, 0.5f, 0, 0, 0.5f, 0.5f, 0.5f, 0, 1, -0.5f, 0.5f, 0.5f, 1, 1,
                // Inferior (-Z) - index 3
                -0.5f, 0.5f, -0.5f, 1, 0, 0.5f, 0.5f, -0.5f, 0, 0, 0.5f, -0.5f, -0.5f, 0, 1, -0.5f, -0.5f, -0.5f, 1, 1,
                // Right (+X) - index 4
                0.5f, 0.5f, 0.5f, 1, 0, 0.5f, -0.5f, 0.5f, 0, 0, 0.5f, -0.5f, -0.5f, 0, 1, 0.5f, 0.5f, -0.5f, 1, 1,
                // Left (-X) - index 5
                -0.5f, -0.5f, 0.5f, 1, 0, -0.5f, 0.5f, 0.5f, 0, 0, -0.5f, 0.5f, -0.5f, 0, 1, -0.5f, -0.5f, -0.5f, 1, 1
        };
        int[] indices = new int[36];
        for (int i = 0; i < 6; i++) {
            int offset = i * 4;
            int idx = i * 6;
            indices[idx] = offset;
            indices[idx + 1] = offset + 1;
            indices[idx + 2] = offset + 2;
            indices[idx + 3] = offset + 2;
            indices[idx + 4] = offset + 3;
            indices[idx + 5] = offset;
        }

        cubeVao = GL33.glGenVertexArrays();
        cubeVbo = GL33.glGenBuffers();
        cubeEbo = GL33.glGenBuffers();

        GL33.glBindVertexArray(cubeVao);
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, cubeVbo);
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, vertices, GL33.GL_STATIC_DRAW);
        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, cubeEbo);
        GL33.glBufferData(GL33.GL_ELEMENT_ARRAY_BUFFER, indices, GL33.GL_STATIC_DRAW);

        GL33.glVertexAttribPointer(0, 3, GL33.GL_FLOAT, false, 5 * 4, 0);
        GL33.glEnableVertexAttribArray(0);
        GL33.glVertexAttribPointer(1, 2, GL33.GL_FLOAT, false, 5 * 4, 3 * 4);
        GL33.glEnableVertexAttribArray(1);
        GL33.glBindVertexArray(0);
    }

    private void createOrientationTextures() {
        for (int i = 0; i < 6; i++) {
            int size = 64;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            // 背景（半透明の濃い青/黒）
            g.setColor(new java.awt.Color(20, 20, 40, 180));
            g.fillRect(0, 0, size, size);

            // 枠線
            g.setColor(java.awt.Color.WHITE);
            g.setStroke(new java.awt.BasicStroke(2));
            g.drawRect(1, 1, size - 2, size - 2);

            // 文字色をHOROS準拠に変更
            if (labelStrings[i].equals("S") || labelStrings[i].equals("I")) {
                g.setColor(java.awt.Color.YELLOW);
            } else if (labelStrings[i].equals("A") || labelStrings[i].equals("P")) {
                g.setColor(java.awt.Color.GREEN);
            } else if (labelStrings[i].equals("L") || labelStrings[i].equals("R")) {
                g.setColor(new java.awt.Color(100, 150, 255)); // 明るい青
            } else {
                g.setColor(java.awt.Color.WHITE);
            }

            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 40));
            java.awt.FontMetrics fm = g.getFontMetrics();
            int tx = (size - fm.stringWidth(labelStrings[i])) / 2;
            int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(labelStrings[i], tx, ty);
            g.dispose();

            int[] pixels = new int[size * size];
            img.getRGB(0, 0, size, size, pixels, 0, size);
            java.nio.ByteBuffer bb = MemoryUtil.memAlloc(size * size * 4);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int p = pixels[y * size + x];
                    bb.put((byte) ((p >> 16) & 0xFF)); // R
                    bb.put((byte) ((p >> 8) & 0xFF)); // G
                    bb.put((byte) (p & 0xFF)); // B
                    bb.put((byte) ((p >> 24) & 0xFF)); // A
                }
            }
            bb.flip();

            orientationTextures[i] = GL33.glGenTextures();
            GL33.glBindTexture(GL33.GL_TEXTURE_2D, orientationTextures[i]);
            GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA, size, size, 0, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE,
                    bb);
            GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
            GL33.glTexParameteri(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_LINEAR);
            MemoryUtil.memFree(bb);
        }
    }

    private void drawOrientationCube(int width, int height) {
        if (cubeShaderProgram == 0)
            return;

        // デプスバッファをクリアして最前面に表示
        GL33.glClear(GL33.GL_DEPTH_BUFFER_BIT);
        GL33.glEnable(GL33.GL_DEPTH_TEST);

        // ビューポートを右上に制限 (100x100ピクセル程度)
        int cubeSize = 100;
        GL33.glViewport(width - cubeSize - 20, height - cubeSize - 20, cubeSize, cubeSize);

        GL33.glUseProgram(cubeShaderProgram);

        // メインと同じ回転を適用 (スケーリングなし)
        float[] proj = perspective(45f, 1.0f, 0.1f, 10f);
        float[] view = translate(0, 0, -2.5f);
        float[] mdl = multiply(rotate(rotateY, 0, 1, 0), rotate(rotateX, 1, 0, 0));

        setMat4Cube("projection", proj);
        setMat4Cube("view", view);
        setMat4Cube("model", mdl);

        GL33.glBindVertexArray(cubeVao);
        for (int i = 0; i < 6; i++) {
            GL33.glActiveTexture(GL33.GL_TEXTURE0);
            GL33.glBindTexture(GL33.GL_TEXTURE_2D, orientationTextures[i]);
            u1iCube("ourTexture", 0);
            GL33.glDrawElements(GL33.GL_TRIANGLES, 6, GL33.GL_UNSIGNED_INT, i * 6 * 4);
        }
        GL33.glBindVertexArray(0);

        GL33.glDisable(GL33.GL_DEPTH_TEST);
    }

    private void u1iCube(String n, int v) {
        GL33.glUniform1i(GL33.glGetUniformLocation(cubeShaderProgram, n), v);
    }

    private void setMat4Cube(String n, float[] m) {
        int loc = GL33.glGetUniformLocation(cubeShaderProgram, n);
        if (loc < 0)
            return;
        FloatBuffer b = MemoryUtil.memAllocFloat(16);
        b.put(m).flip();
        GL33.glUniformMatrix4fv(loc, false, b);
        MemoryUtil.memFree(b);
    }

    @Override
    public void paint(java.awt.Graphics g) {
        // CardLayout で非表示カードにいる場合は描画しない。
        // hidden heavyweight コンポーネントに paint() が来ると GL 初期化や
        // GDI overlay が不正に実行され、DWM redirect surface にテキストが残留する。
        if (!isShowing()) {
            return;
        }
        try {
            render(); // AWTの再描画要求（サイズ変更や露出時）に応じて即座にOpenGL描画を行う
        } catch (Throwable t) {
            if (isPixelFormatError(t) || engineMode == ENGINE_CPU) {
                if (engineMode == ENGINE_GPU && !gpuContextFailed) {
                    gpuContextFailed = true;
                    System.err.println("GLViewer3D: OpenGL context creation failed. Switching to CPU renderer.");
                    t.printStackTrace();
                    if (gpuContextFailureHandler != null) {
                        try {
                            gpuContextFailureHandler.run();
                        } catch (Throwable callbackError) {
                            callbackError.printStackTrace();
                        }
                    }
                }
                setEngine(ENGINE_CPU);
                paintCpu(g);
                return;
            }
            throw t;
        }
    }

    @Override
    public void update(java.awt.Graphics g) {
        // 背景クリアを抑制しつつ、repaint() から常に描画パスへ入れる。
        paint(g);
    }

    private void paintCpu(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, Math.max(0, width), Math.max(0, height));

        BufferedImage image = cpuImage;
        if (image != null) {
            g.drawImage(image, 0, 0, null);
        } else {
            g.setColor(Color.WHITE);
            int cx = Math.max(20, width / 2 - 150);
            int cy = Math.max(40, height / 2 - 10);
            String msg = (cpuRenderer == null) ? "CPU renderer is not initialized" : "CPU rendering in progress...";
            g.drawString(msg, cx, cy);
            g.drawString("If this persists, retry GPU then switch back to CPU.", cx, cy + 20);
        }

        paintOverlay(g);

        if (!cpuRenderInFlight && cpuRenderer != null && width > 0 && height > 0) {
            requestCpuRender();
        }
    }

    private void paintOverlay(Graphics g) {
        if (overlayPainter == null || !(g instanceof Graphics2D)) {
            return;
        }
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            overlayPainter.accept(g2d);
        } finally {
            g2d.dispose();
        }
    }

    private boolean isPixelFormatError(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains("Failed to set pixel format")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
