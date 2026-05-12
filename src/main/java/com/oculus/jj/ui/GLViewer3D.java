package com.oculus.jj.ui;

import org.lwjgl.opengl.GL33;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import org.lwjgl.system.MemoryUtil;
import java.util.List;
import com.oculus.jj.model.DicomPix;

public class GLViewer3D {

    private int shaderProgram;
    private int vao, vbo, ebo;
    private int volumeTexture;
    
    private short[][][] volume;
    private int dimX, dimY, dimZ;
    private List<DicomPix> pixList;
    private boolean dataReady = false;

    private float rotateX = -1.57f;
    private float rotateY = 0.0f;
    private float zoom = 1.0f;
    private float windowLevel = 40.0f;
    private float windowWidth = 400.0f;
    private int renderMode = 1; // 0=MIP, 1=VR, 2=ISO

    public GLViewer3D() {}

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

    public void setZoom(float zoom) {
        this.zoom = zoom;
    }

    public void setWindowLevel(float wl, float ww) {
        this.windowLevel = wl;
        this.windowWidth = ww;
    }

    public void initGL() {
        System.out.println("LWJGL OpenGL Vendor: " + GL33.glGetString(GL33.GL_VENDOR));
        System.out.println("LWJGL OpenGL Renderer: " + GL33.glGetString(GL33.GL_RENDERER));
        System.out.println("LWJGL OpenGL Version: " + GL33.glGetString(GL33.GL_VERSION));

        GL33.glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
        GL33.glEnable(GL33.GL_BLEND);
        GL33.glBlendFunc(GL33.GL_SRC_ALPHA, GL33.GL_ONE_MINUS_SRC_ALPHA);
        GL33.glEnable(GL33.GL_CULL_FACE);
        GL33.glCullFace(GL33.GL_BACK);
        GL33.glDisable(GL33.GL_DEPTH_TEST);
        
        try {
            String vShaderCode = 
                "#version 330 core\n" +
                "layout (location = 0) in vec3 aPos;\n" +
                "out vec3 vTexCoord;\n" +
                "out vec3 vCameraPosLocal;\n" +
                "uniform mat4 model;\n" +
                "uniform mat4 view;\n" +
                "uniform mat4 projection;\n" +
                "void main() {\n" +
                "    mat4 modelView = view * model;\n" +
                "    gl_Position = projection * modelView * vec4(aPos, 1.0);\n" +
                "    vTexCoord = aPos + vec3(0.5);\n" +
                "    mat4 invModelView = inverse(modelView);\n" +
                "    vCameraPosLocal = (invModelView * vec4(0.0, 0.0, 0.0, 1.0)).xyz + vec3(0.5);\n" +
                "}\n";

            String fShaderCode = 
                "#version 330 core\n" +
                "in vec3 vTexCoord;\n" +
                "in vec3 vCameraPosLocal;\n" +
                "out vec4 FragColor;\n" +
                "uniform sampler3D volumeTex;\n" +
                "uniform float stepSize;\n" +
                "uniform float windowLevel;\n" +
                "uniform float windowWidth;\n" +
                "uniform int renderMode;\n" +
                "void main() {\n" +
                "    vec3 rayDir = normalize(vTexCoord - vCameraPosLocal);\n" +
                "    vec3 currPos = vTexCoord;\n" +
                "    vec4 accColor = vec4(0.0);\n" +
                "    float low = windowLevel - windowWidth/2.0;\n" +
                "    float maxVal = 0.0;\n" +
                "    // Stochastic Raycasting (ジッター) で同心円状の境界リングを完全に溶かし、連続的で滑らかな質感にする\n" +
                "    float jitter = fract(sin(dot(vTexCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);\n" +
                "    currPos += rayDir * stepSize * jitter;\n" +
                "    for(int i = 0; i < 400; i++) {\n" +
                "        float rawVal = texture(volumeTex, currPos).r * 65535.0 - 32768.0;\n" +
                "        float normVal = clamp((rawVal - low) / windowWidth, 0.0, 1.0);\n" +
                "        if (renderMode == 0) {\n" + // MIP
                "            maxVal = max(maxVal, normVal);\n" +
                "        } else if (renderMode == 1) {\n" + // VR
                "            // S字カーブ (smoothstep) を使って低濃度のモヤを優雅に消し、お肉の密度を滑らかに立ち上げる\n" +
                "            float smoothNorm = smoothstep(0.02, 0.8, normVal);\n" +
                "            if(smoothNorm > 0.01) {\n" +
                "                float alpha = pow(smoothNorm, 1.2) * 0.35;\n" +
                "                vec3 color;\n" +
                "                if (rawVal > windowLevel + 100.0) color = vec3(1.0, 0.95, 0.85); \n" +      // Bone
                "                else if (rawVal > windowLevel - 50.0) color = vec3(0.92, 0.58, 0.40); \n" + // Muscle (温かみのある色)
                "                else if (rawVal > windowLevel - 150.0) color = vec3(0.88, 0.50, 0.40); \n" + // Soft Tissue (極上の柔らかい肉色)
                "                else if (rawVal > windowLevel - 300.0) color = vec3(0.55, 0.35, 0.25); \n" + // Fat
                "                else color = vec3(0.1, 0.1, 0.15); \n" +                                    // Air
                "                vec3 offset = vec3(1.0/512.0, 1.0/512.0, 1.0/512.0);\n" +
                "                float dx = texture(volumeTex, currPos + vec3(offset.x, 0, 0)).r - texture(volumeTex, currPos - vec3(offset.x, 0, 0)).r;\n" +
                "                float dy = texture(volumeTex, currPos + vec3(0, offset.y, 0)).r - texture(volumeTex, currPos - vec3(0, offset.y, 0)).r;\n" +
                "                float dz = texture(volumeTex, currPos + vec3(0, 0, offset.z)).r - texture(volumeTex, currPos - vec3(0, 0, offset.z)).r;\n" +
                "                vec3 normal = normalize(vec3(dx, dy, dz) + 0.0001);\n" +
                "                vec3 lightDir = normalize(vec3(-1.0, -1.0, 1.0));\n" +
                "                // ラップライティング (Subsurface Scatteringの模倣) で影の境界に人肌の温かい透過光をまとわせる\n" +
                "                float sss = (dot(normal, lightDir) + 1.0) * 0.5;\n" +
                "                float diff = mix(0.5, 1.0, sss);\n" +
                "                color = color * diff;\n" +
                "                accColor.rgb += (1.0 - accColor.a) * color * alpha;\n" +
                "                accColor.a += (1.0 - accColor.a) * alpha;\n" +
                "            }\n" +
                "            if(accColor.a >= 0.98) break;\n" +
                "        } else if (renderMode == 2) {\n" + // ISO
                "            if(rawVal > 300.0) {\n" + // Fixed threshold for bone/surface
                "                vec3 offset = vec3(1.0/512.0, 1.0/512.0, 1.0/512.0);\n" +
                "                float dx = texture(volumeTex, currPos + vec3(offset.x, 0, 0)).r - texture(volumeTex, currPos - vec3(offset.x, 0, 0)).r;\n" +
                "                float dy = texture(volumeTex, currPos + vec3(0, offset.y, 0)).r - texture(volumeTex, currPos - vec3(0, offset.y, 0)).r;\n" +
                "                float dz = texture(volumeTex, currPos + vec3(0, 0, offset.z)).r - texture(volumeTex, currPos - vec3(0, 0, offset.z)).r;\n" +
                "                vec3 normal = normalize(vec3(dx, dy, dz) + 0.0001);\n" +
                "                vec3 lightDir = normalize(vec3(-1.0, -1.0, 1.0));\n" +
                "                float diff = max(dot(normal, lightDir), 0.2);\n" +
                "                accColor = vec4(vec3(0.9, 0.85, 0.75) * diff, 1.0);\n" +
                "                break;\n" +
                "            }\n" +
                "        }\n" +
                "        currPos += rayDir * stepSize;\n" +
                "        if(currPos.x < 0.0 || currPos.x > 1.0 || currPos.y < 0.0 || currPos.y > 1.0 || currPos.z < 0.0 || currPos.z > 1.0) break;\n" +
                "    }\n" +
                "    if (renderMode == 0) {\n" +
                "        if (maxVal > 0.01) FragColor = vec4(maxVal, maxVal, maxVal, 1.0);\n" +
                "        else FragColor = vec4(0.0);\n" +
                "    } else {\n" +
                "        if(accColor.a < 0.01) FragColor = vec4(0.0);\n" +
                "        else FragColor = accColor;\n" +
                "    }\n" +
                "}\n";

            shaderProgram = createProgram(vShaderCode, fShaderCode);
        } catch(Exception e) {
            e.printStackTrace();
        }

        float[] vertices = {
            -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
            -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f
        };
        int[] indices = {
            0, 2, 1, 0, 3, 2,  4, 5, 6, 4, 6, 7,  0, 1, 5, 0, 5, 4,
            1, 2, 6, 1, 6, 5,  2, 3, 7, 2, 7, 6,  3, 0, 4, 3, 4, 7
        };

        vao = GL33.glGenVertexArrays();
        GL33.glBindVertexArray(vao);

        vbo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo);
        FloatBuffer vBuffer = MemoryUtil.memAllocFloat(vertices.length);
        vBuffer.put(vertices).flip();
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, vBuffer, GL33.GL_STATIC_DRAW);
        MemoryUtil.memFree(vBuffer);

        GL33.glVertexAttribPointer(0, 3, GL33.GL_FLOAT, false, 3 * 4, 0);
        GL33.glEnableVertexAttribArray(0);

        ebo = GL33.glGenBuffers();
        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, ebo);
        IntBuffer iBuffer = MemoryUtil.memAllocInt(indices.length);
        iBuffer.put(indices).flip();
        GL33.glBufferData(GL33.GL_ELEMENT_ARRAY_BUFFER, iBuffer, GL33.GL_STATIC_DRAW);
        MemoryUtil.memFree(iBuffer);

        GL33.glBindVertexArray(0);

        if (volume != null) {
            uploadVolume();
        }
    }

    private void uploadVolume() {
        volumeTexture = GL33.glGenTextures();

        GL33.glBindTexture(GL33.GL_TEXTURE_3D, volumeTexture);
        GL33.glTexParameteri(GL33.GL_TEXTURE_3D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_3D, GL33.GL_TEXTURE_WRAP_T, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_3D, GL33.GL_TEXTURE_WRAP_R, GL33.GL_CLAMP_TO_EDGE);
        GL33.glTexParameteri(GL33.GL_TEXTURE_3D, GL33.GL_TEXTURE_MIN_FILTER, GL33.GL_LINEAR);
        GL33.glTexParameteri(GL33.GL_TEXTURE_3D, GL33.GL_TEXTURE_MAG_FILTER, GL33.GL_LINEAR);

        int zStep = (dimZ > 512) ? 2 : 1;
        int targetDimZ = dimZ / zStep;
        long totalSize = (long)dimX * dimY * targetDimZ;

        ShortBuffer buffer = MemoryUtil.memAllocShort((int)totalSize);
        for(int z=0; z<dimZ; z += zStep) {
            if (z / zStep >= targetDimZ) break;
            for(int y=0; y<dimY; y++) {
                for(int x=0; x<dimX; x++) {
                    short val = volume[z][y][x];
                    buffer.put((short)(val ^ 0x8000));
                }
            }
        }
        buffer.flip();

        GL33.glPixelStorei(GL33.GL_UNPACK_ALIGNMENT, 1);
        GL33.glTexImage3D(GL33.GL_TEXTURE_3D, 0, GL33.GL_R16, dimX, dimY, targetDimZ, 0, GL33.GL_RED, GL33.GL_UNSIGNED_SHORT, buffer);
        MemoryUtil.memFree(buffer);
    }

    public void paintGL(int width, int height) {
        GL33.glClear(GL33.GL_COLOR_BUFFER_BIT | GL33.GL_DEPTH_BUFFER_BIT);

        if (!dataReady || shaderProgram == 0) return;

        float w = width <= 0 ? 1.0f : (float)width;
        float h = height <= 0 ? 1.0f : (float)height;
        GL33.glViewport(0, 0, (int)w, (int)h);

        GL33.glUseProgram(shaderProgram);

        float aspect = w / h;
        float[] projection = perspective(45.0f, aspect, 0.1f, 100.0f);
        float[] view = translate(0, 0, -1.2f / zoom);
        float[] model = rotate(rotateX, 1, 0, 0);
        model = multiply(model, rotate(rotateY, 0, 1, 0));

        setUniformMatrix4("projection", projection);
        setUniformMatrix4("view", view);
        setUniformMatrix4("model", model);

        GL33.glUniform1f(GL33.glGetUniformLocation(shaderProgram, "windowLevel"), windowLevel);
        GL33.glUniform1f(GL33.glGetUniformLocation(shaderProgram, "windowWidth"), windowWidth);
        GL33.glUniform1f(GL33.glGetUniformLocation(shaderProgram, "stepSize"), 0.005f);
        GL33.glUniform1i(GL33.glGetUniformLocation(shaderProgram, "renderMode"), renderMode);

        GL33.glActiveTexture(GL33.GL_TEXTURE0);
        GL33.glBindTexture(GL33.GL_TEXTURE_3D, volumeTexture);
        GL33.glUniform1i(GL33.glGetUniformLocation(shaderProgram, "volumeTex"), 0);

        GL33.glBindVertexArray(vao);
        GL33.glDrawElements(GL33.GL_TRIANGLES, 36, GL33.GL_UNSIGNED_INT, 0);
        GL33.glBindVertexArray(0);
    }

    private void setUniformMatrix4(String name, float[] mat) {
        int loc = GL33.glGetUniformLocation(shaderProgram, name);
        if(loc != -1) {
            FloatBuffer buf = MemoryUtil.memAllocFloat(16);
            buf.put(mat).flip();
            GL33.glUniformMatrix4fv(loc, false, buf);
            MemoryUtil.memFree(buf);
        }
    }

    private int createProgram(String vSrc, String fSrc) throws Exception {
        int vs = GL33.glCreateShader(GL33.GL_VERTEX_SHADER);
        GL33.glShaderSource(vs, vSrc);
        GL33.glCompileShader(vs);
        
        int fs = GL33.glCreateShader(GL33.GL_FRAGMENT_SHADER);
        GL33.glShaderSource(fs, fSrc);
        GL33.glCompileShader(fs);

        int prog = GL33.glCreateProgram();
        GL33.glAttachShader(prog, vs);
        GL33.glAttachShader(prog, fs);
        GL33.glLinkProgram(prog);
        return prog;
    }

    private float[] perspective(float fov, float aspect, float near, float far) {
        float f = (float)(1.0 / Math.tan(Math.toRadians(fov) / 2.0));
        return new float[]{
            f/aspect, 0, 0, 0,  0, f, 0, 0,  0, 0, (far+near)/(near-far), -1,  0, 0, (2*far*near)/(near-far), 0
        };
    }

    private float[] translate(float x, float y, float z) {
        return new float[]{
            1, 0, 0, 0,  0, 1, 0, 0,  0, 0, 1, 0,  x, y, z, 1
        };
    }

    private float[] rotate(float angle, float x, float y, float z) {
        float c = (float)Math.cos(angle);
        float s = (float)Math.sin(angle);
        return new float[]{
            x*x*(1-c)+c,   y*x*(1-c)+z*s, x*z*(1-c)-y*s, 0,
            x*y*(1-c)-z*s, y*y*(1-c)+c,   y*z*(1-c)+x*s, 0,
            x*z*(1-c)+y*s, y*z*(1-c)-x*s, z*z*(1-c)+c,   0,
            0,             0,             0,             1
        };
    }

    private float[] multiply(float[] a, float[] b) {
        float[] res = new float[16];
        for(int c=0; c<4; c++) {
            for(int r=0; r<4; r++) {
                res[c*4+r] = a[0*4+r]*b[c*4+0] + a[1*4+r]*b[c*4+1] + a[2*4+r]*b[c*4+2] + a[3*4+r]*b[c*4+3];
            }
        }
        return res;
    }
}
