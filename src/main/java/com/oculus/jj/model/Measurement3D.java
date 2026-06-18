package com.oculus.jj.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Measurement3D - 3D空間での計測ユーティリティ
 * DICOMの物理的なピクセル間隔とスライス間隔を考慮して距離・角度を算出します。
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
        
        /**
         * 世界座標への変換（原点とオリエンテーションを考慮）
         */
        public Point3D toWorldCoordinates(double originX, double originY, double originZ, 
                                           double[] orientation) {
            double dx = x * orientation[0] + y * orientation[3] + z * orientation[6];
            double dy = x * orientation[1] + y * orientation[4] + z * orientation[7];
            double dz = x * orientation[2] + y * orientation[5] + z * orientation[8];
            return new Point3D(originX + dx, originY + dy, originZ + dz);
        }
    }

    /**
     * 3D角度測定結果を格納するクラス
     */
    public static class AngleResult {
        public double angleDegrees; // 角度（度）
        public Point3D vertex;      // 頂点
        public Point3D p1;          // 始点
        public Point3D p2;          // 終点
        
        public AngleResult(double angle, Point3D vertex, Point3D p1, Point3D p2) {
            this.angleDegrees = angle;
            this.vertex = vertex;
            this.p1 = p1;
            this.p2 = p2;
        }
        
        @Override
        public String toString() {
            return String.format("Angle: %.2f° (Vertex: (%.1f, %.1f, %.1f))", 
                angleDegrees, vertex.x, vertex.y, vertex.z);
        }
    }

    /**
     * 3点間の角度（度）を計算します。
     * P1-Vertex-P2の角度を計算します。
     */
    public static AngleResult calculateAngle(Point3D p1, Point3D vertex, Point3D p2,
                                             double spacingX, double spacingY, double spacingZ) {
        // ベクトルVA = P1 - Vertex
        double vx1 = (p1.x - vertex.x) * spacingX;
        double vy1 = (p1.y - vertex.y) * spacingY;
        double vz1 = (p1.z - vertex.z) * spacingZ;
        
        // ベクトルVB = P2 - Vertex
        double vx2 = (p2.x - vertex.x) * spacingX;
        double vy2 = (p2.y - vertex.y) * spacingY;
        double vz2 = (p2.z - vertex.z) * spacingZ;
        
        // 内積
        double dotProduct = vx1 * vx2 + vy1 * vy2 + vz1 * vz2;
        
        // 絶対値
        double magV1 = Math.sqrt(vx1 * vx1 + vy1 * vy1 + vz1 * vz1);
        double magV2 = Math.sqrt(vx2 * vx2 + vy2 * vy2 + vz2 * vz2);
        
        if (magV1 == 0 || magV2 == 0) {
            return new AngleResult(0.0, vertex, p1, p2);
        }
        
        // cosθ = 内積 / (|VA| * |VB|)
        double cosTheta = dotProduct / (magV1 * magV2);
        cosTheta = Math.max(-1.0, Math.min(1.0, cosTheta)); // -1〜1に制限
        
        double angleRad = Math.acos(cosTheta);
        double angleDeg = Math.toDegrees(angleRad);
        
        return new AngleResult(angleDeg, vertex, p1, p2);
    }

    /**
     * 3D距離測定結果を格納するクラス
     */
    public static class DistanceResult {
        public double distanceMM; // 距離（mm）
        public Point3D startPoint;
        public Point3D endPoint;
        
        public DistanceResult(double distance, Point3D start, Point3D end) {
            this.distanceMM = distance;
            this.startPoint = start;
            this.endPoint = end;
        }
        
        @Override
        public String toString() {
            return String.format("Distance: %.2f mm (Start: (%.1f, %.1f, %.1f) -> End: (%.1f, %.1f, %.1f))", 
                distanceMM, startPoint.x, startPoint.y, startPoint.z, 
                endPoint.x, endPoint.y, endPoint.z);
        }
    }

    /**
     * 2点間の3D距離を計算し、結果オブジェクトを返します。
     */
    public static DistanceResult calculateDistanceResult(Point3D p1, Point3D p2,
                                                         double spacingX, double spacingY, double spacingZ) {
        double distance = calculateDistance(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, 
                                            spacingX, spacingY, spacingZ);
        return new DistanceResult(distance, p1, p2);
    }

    /**
     * ボクセル座標から距離を計算する簡易メソッド（spacing=1.0想定）
     */
    public static DistanceResult measureDistance(double x1, double y1, double z1, 
                                                  double x2, double y2, double z2) {
        Point3D p1 = new Point3D(x1, y1, z1);
        Point3D p2 = new Point3D(x2, y2, z2);
        return calculateDistanceResult(p1, p2, 1.0, 1.0, 1.0);
    }

    /**
     * 3点間の角度を計算する簡易メソッド（spacing=1.0想定）
     * @param vx, vy, vz 頂点座標
     * @param x1, y1, z1 脚1の端点
     * @param x2, y2, z2 脚2の端点
     */
    public static AngleResult measureAngle(double vx, double vy, double vz, 
                                            double x1, double y1, double z1, 
                                            double x2, double y2, double z2) {
        Point3D vertex = new Point3D(vx, vy, vz);
        Point3D p1 = new Point3D(x1, y1, z1);
        Point3D p2 = new Point3D(x2, y2, z2);
        return calculateAngle(p1, vertex, p2, 1.0, 1.0, 1.0);
    }

    /**
     * 3D測定履歴を管理するクラス
     */
    public static class MeasurementHistory {
        private List<DistanceResult> distanceMeasurements = new ArrayList<>();
        private List<AngleResult> angleMeasurements = new ArrayList<>();
        
        /**
         * 距離測定結果を追加
         */
        public void addDistanceMeasurement(DistanceResult result) {
            distanceMeasurements.add(result);
        }
        
        /**
         * 角度測定結果を追加
         */
        public void addAngleMeasurement(AngleResult result) {
            angleMeasurements.add(result);
        }
        
        /**
         * 全距離測定結果を取得
         */
        public List<DistanceResult> getDistanceMeasurements() {
            return new ArrayList<>(distanceMeasurements);
        }
        
        /**
         * 全角度測定結果を取得
         */
        public List<AngleResult> getAngleMeasurements() {
            return new ArrayList<>(angleMeasurements);
        }
        
        /**
         * 測定履歴をクリア
         */
        public void clear() {
            distanceMeasurements.clear();
            angleMeasurements.clear();
        }
        
        /**
         * 距離測定の総数
         */
        public int getDistanceCount() {
            return distanceMeasurements.size();
        }
        
        /**
         * 角度測定の総数
         */
        public int getAngleCount() {
            return angleMeasurements.size();
        }
    }
}
