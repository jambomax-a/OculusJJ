package com.oculus.jj.ui;

import java.awt.image.BufferedImage;
import java.util.concurrent.*;

/**
 * CPURaycastRenderer - CPU側ソフトウェアレイキャスティング
 * HOROS-20240407準拠: GPU不可時のフォールバック
 * マルチスレッド（ForkJoinPool）で画面を水平タイル分割して並列処理。
 */
public class CPURaycastRenderer {

    private short[][][] volume;
    private int dimX, dimY, dimZ;
    private float[] gradientX, gradientY, gradientZ;
    private boolean gradientsReady = false;

    private int renderMode = 1; // 0=MIP, 1=VR, 2=ISO
    private TransferFunction transferFunction;

    private float rotateX = -(float) Math.PI / 2;
    private float rotateY = 0;
    private float zoom    = 1.0f;
    private float[] lightDir = new float[]{-0.577f, -0.577f, 0.577f};
    private float isoThreshold = 300.0f;

    private float clipMinX = 0, clipMaxX = 1;
    private float clipMinY = 0, clipMaxY = 1;
    private float clipMinZ = 0, clipMaxZ = 1;

    private static final int   STEP_COUNT = 300;
    private static final float STEP_SIZE  = 1.0f / STEP_COUNT;

    public CPURaycastRenderer() {
        this.transferFunction = TransferFunction.createSoftTissue();
    }

    public void setVolumeData(short[][][] vol, int w, int h, int d) {
        this.volume = vol; this.dimX = w; this.dimY = h; this.dimZ = d;
        this.gradientsReady = false;
    }

    public void setTransferFunction(TransferFunction tf) { this.transferFunction = tf; }
    public void setRenderMode(int mode)  { this.renderMode = mode; }
    public void setRotation(float rx, float ry) { this.rotateX = rx; this.rotateY = ry; }
    public void setZoom(float z) { this.zoom = z; }
    public void setIsoThreshold(float t) { this.isoThreshold = t; }
    public void setLightDirection(float x, float y, float z) {
        float len = (float) Math.sqrt(x*x + y*y + z*z);
        if (len > 0) { lightDir[0] = x/len; lightDir[1] = y/len; lightDir[2] = z/len; }
    }
    public void setClipping(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        clipMinX = minX; clipMaxX = maxX;
        clipMinY = minY; clipMaxY = maxY;
        clipMinZ = minZ; clipMaxZ = maxZ;
    }

    /** Pre-computed gradient volume をCPU上で計算する（重い処理。非同期で呼ぶこと）。 */
    public void computeGradients() {
        if (volume == null) return;
        int total = dimZ * dimY * dimX;
        gradientX = new float[total];
        gradientY = new float[total];
        gradientZ = new float[total];
        for (int z = 0; z < dimZ; z++) {
            for (int y = 0; y < dimY; y++) {
                for (int x = 0; x < dimX; x++) {
                    int idx = z * dimY * dimX + y * dimX + x;
                    gradientX[idx] = (sampleVolume(x+1,y,z) - sampleVolume(x-1,y,z)) * 0.5f;
                    gradientY[idx] = (sampleVolume(x,y+1,z) - sampleVolume(x,y-1,z)) * 0.5f;
                    gradientZ[idx] = (sampleVolume(x,y,z+1) - sampleVolume(x,y,z-1)) * 0.5f;
                }
            }
        }
        gradientsReady = true;
    }

    /** 指定解像度でBufferedImageをレンダリングする（マルチスレッド）。 */
    public BufferedImage render(int width, int height) {
        int[] pixels = new int[width * height];
        float[] matRotX = rotateMatrix(rotateX, 1, 0, 0);
        float[] matRotY = rotateMatrix(rotateY, 0, 1, 0);
        float[] mvMat   = multiplyMatrix(matRotY, matRotX);
        float aspect    = (float) width / height;
        float fovFactor = (float) Math.tan(Math.toRadians(22.5));

        int cpus  = Runtime.getRuntime().availableProcessors();
        int tileH = Math.max(1, height / cpus);
        int tiles = (int) Math.ceil((double) height / tileH);
        CountDownLatch latch = new CountDownLatch(tiles);
        ForkJoinPool pool = ForkJoinPool.commonPool();

        for (int startY = 0; startY < height; startY += tileH) {
            final int sy = startY;
            final int ey = Math.min(startY + tileH, height);
            pool.execute(() -> {
                try { renderTile(pixels, width, height, sy, ey, mvMat, aspect, fovFactor); }
                finally { latch.countDown(); }
            });
        }
        try { latch.await(); } catch (InterruptedException ignored) {}

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, pixels, 0, width);
        return img;
    }

    private void renderTile(int[] pixels, int width, int height,
                            int startY, int endY,
                            float[] mvMat, float aspect, float fovFactor) {
        for (int py = startY; py < endY; py++) {
            for (int px = 0; px < width; px++) {
                float ndcX = (2.0f * px / width  - 1.0f) * aspect * fovFactor / zoom;
                float ndcY = (1.0f - 2.0f * py / height) * fovFactor / zoom;
                float[] rayDir = transformDir(mvMat, ndcX, ndcY, -1.0f);
                float len = (float) Math.sqrt(rayDir[0]*rayDir[0]+rayDir[1]*rayDir[1]+rayDir[2]*rayDir[2]);
                if (len > 0) { rayDir[0]/=len; rayDir[1]/=len; rayDir[2]/=len; }
                float[] cam = transformDir(mvMat, 0, 0, 2.0f / zoom);
                pixels[py * width + px] = castRay(cam[0]+0.5f, cam[1]+0.5f, cam[2]+0.5f, rayDir);
            }
        }
    }

    private int castRay(float ox, float oy, float oz, float[] dir) {
        float accR=0, accG=0, accB=0, accA=0, maxVal=0;
        float cx=ox, cy=oy, cz=oz;
        for (int i = 0; i < STEP_COUNT; i++) {
            if (cx<0||cx>1||cy<0||cy>1||cz<0||cz>1) {
                cx+=dir[0]*STEP_SIZE; cy+=dir[1]*STEP_SIZE; cz+=dir[2]*STEP_SIZE; continue;
            }
            if (cx>=clipMinX && cx<=clipMaxX && cy>=clipMinY && cy<=clipMaxY && cz>=clipMinZ && cz<=clipMaxZ) {
                float hu = sampleHU(cx, cy, cz);
                if (renderMode == 0) {
                    float norm = Math.max(0, Math.min(1, (hu - TransferFunction.HU_MIN) / (TransferFunction.HU_MAX - TransferFunction.HU_MIN)));
                    maxVal = Math.max(maxVal, norm);
                } else if (renderMode == 1) {
                    float[] rgba = sampleTF(hu);
                    float alpha = rgba[3];
                    if (alpha > 0.001f) {
                        float[] c = phongShade(cx, cy, cz, rgba);
                        accR += (1-accA)*c[0]*alpha; accG += (1-accA)*c[1]*alpha;
                        accB += (1-accA)*c[2]*alpha; accA += (1-accA)*alpha;
                        if (accA >= 0.98f) break;
                    }
                } else if (renderMode == 2) {
                    if (hu > isoThreshold) {
                        float[] c = phongShade(cx, cy, cz, new float[]{0.9f, 0.85f, 0.75f, 1.0f});
                        accR=c[0]; accG=c[1]; accB=c[2]; accA=1.0f; break;
                    }
                }
            }
            cx+=dir[0]*STEP_SIZE; cy+=dir[1]*STEP_SIZE; cz+=dir[2]*STEP_SIZE;
        }
        if (renderMode == 0) { int v=(int)(maxVal*255); return (255<<24)|(v<<16)|(v<<8)|v; }
        if (accA < 0.01f) return 0;
        int r=Math.min(255,(int)(accR*255)), g=Math.min(255,(int)(accG*255)),
            b=Math.min(255,(int)(accB*255)), a=Math.min(255,(int)(accA*255));
        return (a<<24)|(r<<16)|(g<<8)|b;
    }

    private float[] phongShade(float cx, float cy, float cz, float[] rgba) {
        float nx=0, ny=0, nz=0;
        if (gradientsReady) {
            int xi=Math.max(0,Math.min(dimX-1,Math.round(cx*(dimX-1))));
            int yi=Math.max(0,Math.min(dimY-1,Math.round(cy*(dimY-1))));
            int zi=Math.max(0,Math.min(dimZ-1,Math.round(cz*(dimZ-1))));
            int idx=zi*dimY*dimX+yi*dimX+xi;
            nx=gradientX[idx]; ny=gradientY[idx]; nz=gradientZ[idx];
            float gl=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(gl>0.0001f){nx/=gl;ny/=gl;nz/=gl;}
        }
        float d = Math.max(0, nx*lightDir[0]+ny*lightDir[1]+nz*lightDir[2]);
        float sp = (float)Math.pow(Math.max(0,d),32)*0.1f;
        float sh = 0.2f + d*0.7f + sp;
        return new float[]{Math.min(1,rgba[0]*sh+sp), Math.min(1,rgba[1]*sh+sp), Math.min(1,rgba[2]*sh+sp)};
    }

    /** TransferFunction の evaluate() を呼んでRGBAサンプリング */
    private float[] sampleTF(float hu) {
        if (transferFunction == null) return new float[]{0.5f,0.5f,0.5f,0.1f};
        return transferFunction.evaluate(hu);
    }

    private float sampleHU(float nx, float ny, float nz) {
        int xi=Math.max(0,Math.min(dimX-1,(int)(nx*(dimX-1))));
        int yi=Math.max(0,Math.min(dimY-1,(int)(ny*(dimY-1))));
        int zi=Math.max(0,Math.min(dimZ-1,(int)(nz*(dimZ-1))));
        return volume[zi][yi][xi];
    }
    private float sampleVolume(int x,int y,int z) {
        return volume[Math.max(0,Math.min(dimZ-1,z))][Math.max(0,Math.min(dimY-1,y))][Math.max(0,Math.min(dimX-1,x))];
    }
    private float[] transformDir(float[] m, float x, float y, float z) {
        return new float[]{m[0]*x+m[4]*y+m[8]*z, m[1]*x+m[5]*y+m[9]*z, m[2]*x+m[6]*y+m[10]*z};
    }
    private float[] rotateMatrix(float a, float ax, float ay, float az) {
        float c=(float)Math.cos(a), s=(float)Math.sin(a);
        return new float[]{ax*ax*(1-c)+c,ay*ax*(1-c)+az*s,ax*az*(1-c)-ay*s,0,
                           ax*ay*(1-c)-az*s,ay*ay*(1-c)+c,ay*az*(1-c)+ax*s,0,
                           ax*az*(1-c)+ay*s,ay*az*(1-c)-ax*s,az*az*(1-c)+c,0,0,0,0,1};
    }
    private float[] multiplyMatrix(float[] a, float[] b) {
        float[] r=new float[16];
        for(int c=0;c<4;c++) for(int row=0;row<4;row++)
            r[c*4+row]=a[0*4+row]*b[c*4+0]+a[1*4+row]*b[c*4+1]+a[2*4+row]*b[c*4+2]+a[3*4+row]*b[c*4+3];
        return r;
    }
}
