package com.oculus.jj.math;

/**
 * Quaternion for 3D rotations.
 */
public class Quaternion {
    public double w, x, y, z;

    public Quaternion(double w, double x, double y, double z) {
        this.w = w;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Quaternion fromEuler(double roll, double pitch, double yaw) {
        double cr = Math.cos(roll * 0.5);
        double sr = Math.sin(roll * 0.5);
        double cp = Math.cos(pitch * 0.5);
        double sp = Math.sin(pitch * 0.5);
        double cy = Math.cos(yaw * 0.5);
        double sy = Math.sin(yaw * 0.5);

        return new Quaternion(
            cr * cp * cy + sr * sp * sy,
            sr * cp * cy - cr * sp * sy,
            cr * sp * cy + sr * cp * sy,
            cr * cp * sy - sr * sp * cy
        );
    }

    public Quaternion multiply(Quaternion q) {
        return new Quaternion(
            w * q.w - x * q.x - y * q.y - z * q.z,
            w * q.x + x * q.w + y * q.z - z * q.y,
            w * q.y - x * q.z + y * q.w + z * q.x,
            w * q.z + x * q.y - y * q.x + z * q.w
        );
    }

    public Matrix4x4 toMatrix() {
        Matrix4x4 m = new Matrix4x4();
        double[] d = m.getData();
        double xx = x * x, yy = y * y, zz = z * z;
        double xy = x * y, xz = x * z, yz = y * z;
        double wx = w * x, wy = w * y, wz = w * z;

        m.data[0] = 1 - 2 * (yy + zz);
        m.data[1] = 2 * (xy + wz);
        m.data[2] = 2 * (xz - wy);
        m.data[3] = 0;

        m.data[4] = 2 * (xy - wz);
        m.data[5] = 1 - 2 * (xx + zz);
        m.data[6] = 2 * (yz + wx);
        m.data[7] = 0;

        m.data[8] = 2 * (xz + wy);
        m.data[9] = 2 * (yz - wx);
        m.data[10] = 1 - 2 * (xx + yy);
        m.data[11] = 0;

        m.data[12] = 2 * (wx - yz);
        m.data[13] = 2 * (wy + xz);
        m.data[14] = 2 * (wz + xy);
        m.data[15] = 1;

        return m;
    }

    @Override
    public String toString() {
        return String.format("Quaternion(w=%.3f, x=%.3f, y=%.3f, z=%.3f)", w, x, y, z);
    }
}
