package com.oculus.jj.model;

/**
 * Measurement3D - 3D空間での計測ユーティリティ
 * DICOMの物理的なピクセル間隔とスライス間隔を考慮して距離を算出します。
 */
public class Measurement3D {

    /**
     * 2つのボクセル座標間の物理距離（mm）を計算します。
     * 
     * @param x1, y1, z1 点1のボクセル座標
     * @param x2, y2, z2 点2のボクセル座標
     * @param spacingX X方向のピクセル間隔 (mm)
     * @param spacingY Y方向のピクセル間隔 (mm)
     * @param spacingZ Z方向の間隔 (mm)
     * @return 物理距離 (mm)
     */
    public static double calculateDistance(double x1, double y1, double z1, 
                                           double x2, double y2, double z2,
                                           double spacingX, double spacingY, double spacingZ) {
        
        double dx = (x1 - x2) * spacingX;
        double dy = (y1 - y2) * spacingY;
        double dz = (z1 - z2) * spacingZ;
        
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 3D空間内の点を表すシンプルな内部クラス
     */
    public static class Point3D {
        public double x, y, z; // ボクセル座標

        public Point3D(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
