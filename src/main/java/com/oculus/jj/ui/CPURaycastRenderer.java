package com.oculus.jj.ui;

import java.awt.image.BufferedImage;
import java.util.concurrent.*;

/**
 * CPURaycastRenderer - CPU側ソフトウェアレイキャスティング
 * HOROS-20240407準拠: GPU不可時のフォールバック
 * マルチスレッド（ForkJoinPool）で画面を水平タイル分割して並列処理。
 */
public class CPURaycastRenderer {

    private short[] volume;
    private int dimX, dimY, dimZ;
    private float[] gradientX, gradientY, gradientZ;
    private boolean gradientsReady = false;

    private int renderMode = 1; // 0=MIP, 1=VR, 2=ISO
    private TransferFunction transferFunction;

    private float rotateX = -(float) Math.PI / 2;
    private float rotateY = (float) (Math.PI / 2);
    private float zoom = 1.0f;
    private float[] lightDir = new float[] { -0.577f, -0.577f, 0.577f };
    private float isoThreshold = 300.0f;
    private float windowLevel = 40.0f;
    private float windowWidth = 400.0f;

    private float clipMinX = 0, clipMaxX = 1;
    private float clipMinY = 0, clipMaxY = 1;
    private float clipMinZ = 0, clipMaxZ = 1;

    private float voxScaleX = 1.0f;
    private float voxScaleY = 1.0f;
    private float voxScaleZ = 1.0f;

    private float offsetX = 0.0f;
    private float offsetY = 0.0f;
    private float offsetZ = 0.0f;
    private static final float PAN_LIMIT = 0.35f;

    private static final int STEP_COUNT = 256;
    private int debugPrintCount = 0;

    public CPURaycastRenderer() {
        this.transferFunction = TransferFunction.createSoftTissue();
    }

    public void setVoxelSpacing(float sx, float sy, float sz) {
        float physX = dimX * sx;
        float physY = dimY * sy;
        float physZ = dimZ * sz;
        float maxPhys = Math.max(physX, Math.max(physY, physZ));
        voxScaleX = (maxPhys > 0) ? physX / maxPhys : 1.0f;
        voxScaleY = (maxPhys > 0) ? physY / maxPhys : 1.0f;
        voxScaleZ = (maxPhys > 0) ? physZ / maxPhys : 1.0f;
    }

    public synchronized void setVolumeData(short[] vol, int w, int h, int d) {
        if (this.volume == vol && this.dimX == w && this.dimY == h && this.dimZ == d) {
            return;
        }
        this.volume = vol;
        this.dimX = w;
        this.dimY = h;
        this.dimZ = d;
        this.gradientsReady = false;
    }

    public void setTransferFunction(TransferFunction tf) {
        this.transferFunction = tf;
    }

    public void setRenderMode(int mode) {
        this.renderMode = mode;
    }

    public void setRotation(float rx, float ry) {
        this.rotateX = rx;
        this.rotateY = ry;
    }

    public void setZoom(float z) {
        this.zoom = z;
    }

    public void setIsoThreshold(float t) {
        this.isoThreshold = t;
    }

    public void setWindowLevel(float wl, float ww) {
        this.windowLevel = wl;
        this.windowWidth = Math.max(1.0f, ww);
    }

    public void setLightDirection(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len > 0) {
            lightDir[0] = x / len;
            lightDir[1] = y / len;
            lightDir[2] = z / len;
        }
    }

    public void setClipping(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        clipMinX = minX;
        clipMaxX = maxX;
        clipMinY = minY;
        clipMaxY = maxY;
        clipMinZ = minZ;
        clipMaxZ = maxZ;
    }

    public void setOffset(float x, float y, float z) {
        this.offsetX = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, x));
        this.offsetY = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, y));
        this.offsetZ = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, z));
    }

    public void addOffset(float dx, float dy, float dz) {
        this.offsetX = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, this.offsetX + dx));
        this.offsetY = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, this.offsetY + dy));
        this.offsetZ = Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, this.offsetZ + dz));
    }

    /** Pre-computed gradient volume をCPU上で計算する（重い処理。非同期で呼ぶこと）。 */
    public void computeGradients() {
        if (volume == null)
            return;
        int total = dimZ * dimY * dimX;
        gradientX = new float[total];
        gradientY = new float[total];
        gradientZ = new float[total];
        java.util.stream.IntStream.range(0, dimZ).parallel().forEach(z -> {
            for (int y = 0; y < dimY; y++) {
                int yOffset = y * dimX;
                int zOffset = z * dimY * dimX;
                for (int x = 0; x < dimX; x++) {
                    int idx = zOffset + yOffset + x;
                    gradientX[idx] = (sampleVolume(x + 1, y, z) - sampleVolume(x - 1, y, z)) * 0.5f;
                    gradientY[idx] = (sampleVolume(x, y + 1, z) - sampleVolume(x, y - 1, z)) * 0.5f;
                    gradientZ[idx] = (sampleVolume(x, y, z + 1) - sampleVolume(x, y, z - 1)) * 0.5f;
                }
            }
        });
        gradientsReady = true;
    }

    /** 指定解像度でBufferedImageをレンダリングする（マルチスレッド）。 */
    public synchronized BufferedImage render(int width, int height) {
        if (volume == null || dimX <= 0 || dimY <= 0 || dimZ <= 0 || width <= 0 || height <= 0) {
            return new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        }

        int[] pixels = new int[width * height];
        float aspect = (float) width / height;
        float fovFactor = (float) Math.tan(Math.toRadians(22.5));

        float camZVal = 1.2f / zoom;
        float[] rot = multiplyMatrix(rotateMatrix(rotateY, 0, 1, 0), rotateMatrix(rotateX, 1, 0, 0));
        float rx = rot[2] * camZVal;
        float ry = rot[6] * camZVal;
        float rz = rot[10] * camZVal;
        float camX = rx / voxScaleX + 0.5f;
        float camY = ry / voxScaleY + 0.5f;
        float camZ = rz / voxScaleZ + 0.5f;

        java.util.stream.IntStream.range(0, height).parallel().forEach(py -> {
            for (int px = 0; px < width; px++) {
                float ndcX = (2.0f * px / width - 1.0f) * aspect * fovFactor / zoom;
                float ndcY = (1.0f - 2.0f * py / height) * fovFactor / zoom;
                float[] vModel = transformDirTranspose(rot, ndcX, ndcY, -1.0f);
                float[] rayDir = new float[] {
                    vModel[0] / voxScaleX,
                    vModel[1] / voxScaleY,
                    vModel[2] / voxScaleZ
                };
                float len = (float) Math.sqrt(rayDir[0] * rayDir[0] + rayDir[1] * rayDir[1] + rayDir[2] * rayDir[2]);
                if (len > 0) {
                    rayDir[0] /= len;
                    rayDir[1] /= len;
                    rayDir[2] /= len;
                }
                pixels[py * width + px] = castRay(camX, camY, camZ, rayDir);
            }
        });

        int nonZero = 0;
        for (int p : pixels) {
            if ((p & 0x00FFFFFF) != 0) {
                nonZero++;
            }
        }
        if (debugPrintCount < 5) {
            debugPrintCount++;
            System.out.println("DEBUG CPURenderer: renderW=" + width + ", renderH=" + height 
                + ", nonZero=" + nonZero + " (out of " + pixels.length + ")"
                + ", cam=(" + camX + "," + camY + "," + camZ + ")"
                + ", voxScale=(" + voxScaleX + "," + voxScaleY + "," + voxScaleZ + ")");
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, pixels, 0, width);
        return img;
    }

    private int castRay(float ox, float oy, float oz, float[] dir) {
        float[] hit = intersectUnitBox(ox, oy, oz, dir);
        if (hit == null) {
            return 0;
        }

        float t = Math.max(0.0f, hit[0]);
        float tEnd = hit[1];
        if (tEnd <= t) {
            return 0;
        }

        float dt = 0.002f;
        float accR = 0, accG = 0, accB = 0, accA = 0, maxVal = 0;
        float winLow = windowLevel - windowWidth * 0.5f;
        float winHigh = windowLevel + windowWidth * 0.5f;

        int step = 0;
        for (float curT = t; curT <= tEnd && step < 512; curT += dt, step++) {
            float cx = ox + dir[0] * curT + offsetX;
            float cy = oy + dir[1] * curT + offsetY;
            float cz = oz + dir[2] * curT + offsetZ;
            if (cx >= clipMinX && cx <= clipMaxX && cy >= clipMinY && cy <= clipMaxY && cz >= clipMinZ
                    && cz <= clipMaxZ) {
                float hu = sampleHU(cx, cy, cz);
                float norm = Math.max(0, Math.min(1,
                        (hu - TransferFunction.HU_MIN) / (TransferFunction.HU_MAX - TransferFunction.HU_MIN)));
                maxVal = Math.max(maxVal, norm);
                if (renderMode == 0) {
                } else if (renderMode == 1) {
                    if (hu < winLow || hu > winHigh) {
                        continue;
                    }
                    float[] rgba = sampleTF(hu);
                    float alpha = rgba[3];
                    if (alpha > 0.005f) {
                        float[] c = phongShade(cx, cy, cz, rgba, dir);
                        accR += (1 - accA) * c[0] * alpha;
                        accG += (1 - accA) * c[1] * alpha;
                        accB += (1 - accA) * c[2] * alpha;
                        accA += (1 - accA) * alpha;
                        if (accA >= 0.95f) // GPU matches 0.95 break
                            break;
                    }
                } else if (renderMode == 2) {
                    if (hu > isoThreshold) {
                        float[] c = phongShade(cx, cy, cz, new float[] { 0.92f, 0.88f, 0.78f, 1.0f }, dir);
                        accR = c[0];
                        accG = c[1];
                        accB = c[2];
                        accA = 1.0f;
                        break;
                    }
                }
            }
        }
        if (renderMode == 0) {
            int v = (int) (maxVal * 255);
            return (255 << 24) | (v << 16) | (v << 8) | v;
        }
        if (accA < 0.01f) {
            return 0; // Completely transparent background matching GPU!
        }
        int r = Math.min(255, (int) (accR * 255)), g = Math.min(255, (int) (accG * 255)),
                b = Math.min(255, (int) (accB * 255)), a = Math.min(255, (int) (accA * 255));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private float[] intersectUnitBox(float ox, float oy, float oz, float[] dir) {
        float tMin = -Float.MAX_VALUE;
        float tMax = Float.MAX_VALUE;

        float[] o = { ox, oy, oz };
        for (int axis = 0; axis < 3; axis++) {
            float d = dir[axis];
            float origin = o[axis];
            if (Math.abs(d) < 1e-7f) {
                if (origin < 0.0f || origin > 1.0f) {
                    return null;
                }
                continue;
            }

            float invD = 1.0f / d;
            float t1 = (0.0f - origin) * invD;
            float t2 = (1.0f - origin) * invD;
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
            }

            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMax < tMin) {
                return null;
            }
        }

        return new float[] { tMin, tMax };
    }

    private float interpolateField(float[] field, int x0, int x1, int y0, int y1, int z0, int z1, float tx, float ty, float tz) {
        float c000 = field[z0 * dimY * dimX + y0 * dimX + x0];
        float c100 = field[z0 * dimY * dimX + y0 * dimX + x1];
        float c010 = field[z0 * dimY * dimX + y1 * dimX + x0];
        float c110 = field[z0 * dimY * dimX + y1 * dimX + x1];

        float c001 = field[z1 * dimY * dimX + y0 * dimX + x0];
        float c101 = field[z1 * dimY * dimX + y0 * dimX + x1];
        float c011 = field[z1 * dimY * dimX + y1 * dimX + x0];
        float c111 = field[z1 * dimY * dimX + y1 * dimX + x1];

        float c00 = c000 * (1.0f - tx) + c100 * tx;
        float c10 = c010 * (1.0f - tx) + c110 * tx;
        float c01 = c001 * (1.0f - tx) + c101 * tx;
        float c11 = c011 * (1.0f - tx) + c111 * tx;

        float c0 = c00 * (1.0f - ty) + c10 * ty;
        float c1 = c01 * (1.0f - ty) + c11 * ty;

        return c0 * (1.0f - tz) + c1 * tz;
    }

    private float[] phongShade(float cx, float cy, float cz, float[] rgba, float[] dir) {
        float nx = 0, ny = 0, nz = 0;
        if (gradientsReady) {
            float px = cx * (dimX - 1);
            float py = cy * (dimY - 1);
            float pz = cz * (dimZ - 1);

            int x0 = (int) Math.floor(px);
            int y0 = (int) Math.floor(py);
            int z0 = (int) Math.floor(pz);

            int x0_c = Math.max(0, Math.min(dimX - 1, x0));
            int x1_c = Math.max(0, Math.min(dimX - 1, x0 + 1));
            int y0_c = Math.max(0, Math.min(dimY - 1, y0));
            int y1_c = Math.max(0, Math.min(dimY - 1, y0 + 1));
            int z0_c = Math.max(0, Math.min(dimZ - 1, z0));
            int z1_c = Math.max(0, Math.min(dimZ - 1, z0 + 1));

            float tx = px - x0;
            float ty = py - y0;
            float tz = pz - z0;

            nx = interpolateField(gradientX, x0_c, x1_c, y0_c, y1_c, z0_c, z1_c, tx, ty, tz);
            ny = interpolateField(gradientY, x0_c, x1_c, y0_c, y1_c, z0_c, z1_c, tx, ty, tz);
            nz = interpolateField(gradientZ, x0_c, x1_c, y0_c, y1_c, z0_c, z1_c, tx, ty, tz);

            // Invert normal to point outwards (HU gradient points towards increasing density)
            nx = -nx;
            ny = -ny;
            nz = -nz;

            float gl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (gl > 0.0001f) {
                nx /= gl;
                ny /= gl;
                nz /= gl;
            }
        }

        // Blinn-Phong Reflection Model matching GPU 1:1
        float[] L = lightDir;
        float[] V = new float[] { -dir[0], -dir[1], -dir[2] };
        float hx = L[0] + V[0];
        float hy = L[1] + V[1];
        float hz = L[2] + V[2];
        float hlen = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (hlen > 0.0001f) {
            hx /= hlen;
            hy /= hlen;
            hz /= hlen;
        }

        float ambient = 0.20f;
        float diffuse = Math.max(0.0f, nx * L[0] + ny * L[1] + nz * L[2]) * 0.90f;
        float spec = (float) Math.pow(Math.max(0.0f, nx * hx + ny * hy + nz * hz), 15.0f) * 0.30f;

        return new float[] {
            Math.min(1.0f, rgba[0] * (ambient + diffuse) + spec),
            Math.min(1.0f, rgba[1] * (ambient + diffuse) + spec),
            Math.min(1.0f, rgba[2] * (ambient + diffuse) + spec)
        };
    }

    /** TransferFunction の evaluate() を呼んでRGBAサンプリング */
    private float[] sampleTF(float hu) {
        if (transferFunction == null)
            return new float[] { 0.5f, 0.5f, 0.5f, 0.1f };
        float low = windowLevel - windowWidth * 0.5f;
        float winT = Math.max(0.0f, Math.min(1.0f, (hu - low) / windowWidth));
        return transferFunction.evaluateT(winT, windowLevel, windowWidth);
    }

    private float sampleHU(float nx, float ny, float nz) {
        float px = nx * (dimX - 1);
        float py = ny * (dimY - 1);
        float pz = nz * (dimZ - 1);

        int x0 = (int) Math.floor(px);
        int y0 = (int) Math.floor(py);
        int z0 = (int) Math.floor(pz);

        int x0_c = Math.max(0, Math.min(dimX - 1, x0));
        int x1_c = Math.max(0, Math.min(dimX - 1, x0 + 1));
        int y0_c = Math.max(0, Math.min(dimY - 1, y0));
        int y1_c = Math.max(0, Math.min(dimY - 1, y0 + 1));
        int z0_c = Math.max(0, Math.min(dimZ - 1, z0));
        int z1_c = Math.max(0, Math.min(dimZ - 1, z0 + 1));

        float tx = px - x0;
        float ty = py - y0;
        float tz = pz - z0;

        float c000 = volume[z0_c * dimY * dimX + y0_c * dimX + x0_c];
        float c100 = volume[z0_c * dimY * dimX + y0_c * dimX + x1_c];
        float c010 = volume[z0_c * dimY * dimX + y1_c * dimX + x0_c];
        float c110 = volume[z0_c * dimY * dimX + y1_c * dimX + x1_c];

        float c001 = volume[z1_c * dimY * dimX + y0_c * dimX + x0_c];
        float c101 = volume[z1_c * dimY * dimX + y0_c * dimX + x1_c];
        float c011 = volume[z1_c * dimY * dimX + y1_c * dimX + x0_c];
        float c111 = volume[z1_c * dimY * dimX + y1_c * dimX + x1_c];

        float c00 = c000 * (1.0f - tx) + c100 * tx;
        float c10 = c010 * (1.0f - tx) + c110 * tx;
        float c01 = c001 * (1.0f - tx) + c101 * tx;
        float c11 = c011 * (1.0f - tx) + c111 * tx;

        float c0 = c00 * (1.0f - ty) + c10 * ty;
        float c1 = c01 * (1.0f - ty) + c11 * ty;

        return c0 * (1.0f - tz) + c1 * tz;
    }

    private float sampleVolume(int x, int y, int z) {
        int zi = Math.max(0, Math.min(dimZ - 1, z));
        int yi = Math.max(0, Math.min(dimY - 1, y));
        int xi = Math.max(0, Math.min(dimX - 1, x));
        return volume[zi * dimY * dimX + yi * dimX + xi];
    }

    private float[] transformDir(float[] m, float x, float y, float z) {
        return new float[] { m[0] * x + m[4] * y + m[8] * z, m[1] * x + m[5] * y + m[9] * z,
                m[2] * x + m[6] * y + m[10] * z };
    }

    private float[] transformDirTranspose(float[] m, float x, float y, float z) {
        return new float[] {
            m[0] * x + m[1] * y + m[2] * z,
            m[4] * x + m[5] * y + m[6] * z,
            m[8] * x + m[9] * y + m[10] * z
        };
    }

    private float[] rotateMatrix(float a, float ax, float ay, float az) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[] { ax * ax * (1 - c) + c, ay * ax * (1 - c) + az * s, ax * az * (1 - c) - ay * s, 0,
                ax * ay * (1 - c) - az * s, ay * ay * (1 - c) + c, ay * az * (1 - c) + ax * s, 0,
                ax * az * (1 - c) + ay * s, ay * az * (1 - c) - ax * s, az * az * (1 - c) + c, 0, 0, 0, 0, 1 };
    }

    private float[] multiplyMatrix(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int row = 0; row < 4; row++)
                r[c * 4 + row] = a[0 * 4 + row] * b[c * 4 + 0] + a[1 * 4 + row] * b[c * 4 + 1]
                        + a[2 * 4 + row] * b[c * 4 + 2] + a[3 * 4 + row] * b[c * 4 + 3];
        return r;
    }
}
