package com.oculus.jj.math;

/**
 * 4x4 Matrix used for 3D transformations (translation, rotation, scaling).
 * Implementation is Column-Major to align with common graphics standards (like OpenGL/VTK).
 */
public class Matrix4x4 {
    public final double[] data = new double[16];

    public Matrix4x4() {
        setIdentity();
    }

    public static Matrix4x4 identity() {
        Matrix4x4 m = new Matrix4x4();
        m.setIdentity();
        return m;
    }

    private void setIdentity() {
        for (int i = 0; i < 16; i++) data[i] = 0;
        data[0] = 1; data[5] = 1; data[10] = 1; data[15] = 1;
    }

    public double get(int row, int col) {
        return data[col * 4 + row];
    }

    public void set(int row, int col, double value) {
        data[col * 4 + row] = value;
    }

    public Matrix4x4 multiply(Matrix4x4 other) {
        Matrix4x4 result = new Matrix4x4();
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                double sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += this.get(r, k) * other.get(k, c);
                }
                result.set(r, c, sum);
            }
        }
        return result;
    }

    public Vector3 multiply(Vector3 v) {
        double x = data[0] * v.x + data[4] * v.y + data[8] * v.z + data[12];
        double y = data[1] * v.x + data[5] * v.y + data[9] * v.z + data[13];
        double z = data[2] * v.x + data[6] * v.y + data[10] * v.z + data[14];
        double w = data[3] * v.x + data[7] * v.y + data[11] * v.z + data[15];
        if (w != 0 && w != 1.0) {
            return new Vector3(x / w, y / w, z / w);
        }
        return new Vector3(x, y, z);
    }

    public Matrix4x4 translation(double x, double y, double z) {
        Matrix4x4 m = identity();
        m.set(0, 3, x);
        m.set(1, 3, y);
        m.set(2, 3, z);
        return m;
    }

    public Matrix4x4 rotationX(double angleRad) {
        Matrix4x4 m = identity();
        double c = Math.cos(angleRad);
        double s = Math.sin(angleRad);
        m.data[5] = c;   // row 1, col 1
        m.data[6] = s;    // row 2, col 1
        m.data[9] = -s;   // row 1, col 2
        m.data[10] = c;   // row 2, col 2
        return m;
    }

    public Matrix4x4 rotationY(double angleRad) {
        Matrix4x4 m = identity();
        double c = Math.cos(angleRad);
        double s = Math.sin(angleRad);
        m.data[0] = c;    // row 0, col 0
        m.data[2] = -s;   // row 2, col 0
        m.data[8] = s;    // row 0, col 2
        m.data[10] = c;   // row 2, col 2
        return m;
    }

    public Matrix4x4 rotationZ(double angleRad) {
        Matrix4x4 m = identity();
        double c = Math.cos(angleRad);
        double s = Math.sin(angleRad);
        m.data[0] = c;    // row 0, col 0
        m.data[1] = -s;   // row 1, col 0
        m.data[4] = s;    // row 0, col 1
        m.data[5] = c;    // row 1, col 1
        return m;
    }

    public double[] getData() {
        return data;
    }
}
