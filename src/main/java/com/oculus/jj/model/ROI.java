package com.oculus.jj.model;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.FontMetrics;
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.Composite;
import java.awt.AlphaComposite;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.oculus.jj.ui.DCMView;

/**
 * ROI - Region of Interest
 * 
 * HOROS-20240407のROIをJava Swingに移植
 */
public class ROI {

    // 統計計算用のロック（重い計算が重ならないようにする）
    private final Object statsLock = new Object();
    private boolean isStatsCalculating = false;

    public Object getStatsLock() {
        return statsLock;
    }

    public boolean isStatsCalculating() {
        return isStatsCalculating;
    }

    public void setStatsCalculating(boolean calculating) {
        this.isStatsCalculating = calculating;
    }

    // HOROS: NSRecursiveLock *roiLock
    private ReentrantLock roiLock;

    // HOROS: NSMutableArray *points
    private List<Point2D.Float> points;

    // HOROS: NSRect rect
    private Rectangle rect;
    // HOROS-20240407準拠: 独自のID（データベース管理用）
    private int id = -1;
    // HOROS: ToolMode type
    private DCMView.ToolMode type;

    // HOROS: long mode
    private int mode;

    // HOROS: float thickness
    private float thickness = 1.0f;

    // HOROS: BOOL fill
    private boolean fill = false;

    // HOROS: float opacity
    private float opacity = 1.0f;

    // HOROS: RGBColor color
    private Color color = Color.YELLOW;

    // HOROS-20240407準拠: 選択時の色設定 (0.5, 0.5, 1.0)
    private static final Color SELECTION_COLOR = new Color(128, 128, 255);

    // HOROS: globalColorRotationState, colorRotationTable
    private static int globalColorRotationState = 0;
    private static final Color[] colorRotationTable = {
            new Color(191, 127, 255), // pastel purple bf7fff
            new Color(0, 255, 0), // default green
            new Color(249, 190, 0), // topaz
            new Color(234, 110, 53), // carrot orange
            new Color(0, 96, 142), // madonna blue
            new Color(86, 37, 86), // grape
            new Color(127, 255, 127), // pastel green 7fff7f
            new Color(255, 255, 127), // pastel yellow ffff7f
            new Color(255, 127, 127), // pastel orange ff7f7f
            new Color(127, 191, 255) // pastel blue 7fbfff
    };

    // ソース画像の情報（USのマルチリージョン対応用、およびROIアイソレーション用）
    private DicomPix sourcePix;
    private String sopInstanceUID;

    // HOROS: BOOL closed
    private boolean closed = false;

    public boolean isClosed() {
        return closed;
    }

    public static class Point3D {
        public double x, y, z;

        public Point3D(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class Segment {
        public Point3D p1, p2;

        public Segment(Point3D p1, Point3D p2) {
            this.p1 = p1;
            this.p2 = p2;
        }
    }

    public List<Segment> getSegments() {
        List<Segment> segments = new ArrayList<>();
        if (points == null || points.size() < 2)
            return segments;

        // ボリューム空間でのスライス位置 (インデックス) を取得
        // DicomPix が sliceIndex を持っていると仮定
        double z = (sourcePix != null) ? sourcePix.getSliceIndex() : 0;

        for (int i = 0; i < points.size() - 1; i++) {
            Point2D.Float p1 = points.get(i);
            Point2D.Float p2 = points.get(i + 1);
            segments.add(new Segment(new Point3D(p1.x, p1.y, z), new Point3D(p2.x, p2.y, z)));
        }
        if (closed && points.size() > 2) {
            Point2D.Float p1 = points.get(points.size() - 1);
            Point2D.Float p2 = points.get(0);
            segments.add(new Segment(new Point3D(p1.x, p1.y, z), new Point3D(p2.x, p2.y, z)));
        }
        return segments;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    // HOROS: NSString *name
    private String name = "";

    // HOROS: double pixelSpacingX, pixelSpacingY
    private double pixelSpacingX = 1.0;
    private double pixelSpacingY = 1.0;

    // HOROS: BOOL locked, selectable, hidden
    private boolean locked = false;
    private boolean isSpline = false;
    private boolean selectable = true;
    private boolean hidden = false;
    private DCMView parentView = null;

    private boolean isEraseMode = false;

    // HOROS-20240407: 3D coordinates for Curved MPR nodes
    private List<Point3D> points3D = new ArrayList<>();

    public void addPoint3D(Point3D p) {
        points3D.add(p);
    }

    public void setEraseMode(boolean erase) {
        this.isEraseMode = erase;
    }

    public boolean isEraseMode() {
        return isEraseMode;
    }

    private String comments = "";

    // キャッシュ用フィールド
    private transient List<Point2D.Float> cachedSplinePoints = null;
    private transient Path2D.Float cachedPath = null;
    private transient Path2D.Float cachedClosedPath = null;
    private transient Shape cachedStrokedShape = null;
    private transient boolean needsUpdate = true;

    public void setParentView(DCMView v) {
        this.parentView = v;
    }

    public DCMView getParentView() {
        return parentView;
    }

    // HOROS: float rmean, rmax, rmin, rdev, rtotal
    private float rmean = 0, rmax = 0, rmin = 0, rdev = 0, rtotal = 0;
    private String pixelUnit = "";
    private boolean hasStatistics = false;

    // HOROS: NSString *textualBoxLine1...6
    private String textualBoxLine1 = null;
    private String textualBoxLine2 = null;
    private String textualBoxLine3 = null;
    private String textualBoxLine4 = null;
    private String textualBoxLine5 = null;
    private String textualBoxLine6 = null;

    private void clearTextualData() {
        textualBoxLine1 = null;
        textualBoxLine2 = null;
        textualBoxLine3 = null;
        textualBoxLine4 = null;
        textualBoxLine5 = null;
        textualBoxLine6 = null;
    }

    public void setStatistics(float mean, float max, float min, float dev, float total) {
        setStatistics(mean, max, min, dev, total, "");
    }

    public void setStatistics(float mean, float max, float min, float dev, float total, String unit) {
        this.rmean = mean;
        this.rmax = max;
        this.rmin = min;
        this.rdev = dev;
        this.rtotal = total;
        this.pixelUnit = unit;
        this.hasStatistics = true;
    }

    // HOROS: float offsetTextBox_x, offsetTextBox_y
    private float textBoxOffsetX = 0;
    private float textBoxOffsetY = 0;
    private boolean clickInTextBox = false;
    private int activeHandleIndex = -1; // -1: なし, 0以上: ハンドルのインデックス
    private transient java.awt.geom.Rectangle2D.Float textBoxRect = null;

    /**
     * Constructor
     */
    public ROI() {
        roiLock = new ReentrantLock();
        points = new ArrayList<>();
        rect = new Rectangle();
    }

    /**
     * HOROS: - (id)initWithType:(ToolMode)itype :(float)ipixelSpacing
     * :(NSPoint)iimageOrigin
     */
    public ROI(DCMView.ToolMode type, float pixelSpacingX, float pixelSpacingY, Point2D.Float imageOrigin) {
        this();
        this.type = type;
        this.pixelSpacingX = pixelSpacingX;
        this.pixelSpacingY = pixelSpacingY;

        // HOROS-20240407: ROI color rotation
        // 実際には設定を確認するが、ここでは常に回転させる
        this.color = colorRotationTable[globalColorRotationState % colorRotationTable.length];
        globalColorRotationState++;

        if (type == DCMView.ToolMode.tText) {
            this.name = "Double-Click to edit";
        }
    }

    /**
     * HOROS: - (void)addPoint:(NSPoint)pt
     */
    public void addPoint(Point2D.Float pt) {
        roiLock.lock();
        try {
            points.add(pt);
            updateRect();
            needsUpdate = true;
        } finally {
            roiLock.unlock();
        }
    }

    /**
     * HOROS: - (void)setPoints:(NSMutableArray*)pts
     */
    public void setPoints(List<Point2D.Float> pts) {
        roiLock.lock();
        try {
            points.clear();
            points.addAll(pts);
            updateRect();
            needsUpdate = true;
        } finally {
            roiLock.unlock();
        }
    }

    /**
     * Update point at index
     */
    public void updatePoint(int index, Point2D.Float pt) {
        roiLock.lock();
        try {
            if (index >= 0 && index < points.size()) {
                points.set(index, pt);
                if (type == DCMView.ToolMode.tPerpendicular) {
                    recalculatePerpendicular();
                }
                updateRect();
                needsUpdate = true;
            }
        } finally {
            roiLock.unlock();
        }
    }

    public void updatePoint3D(int index, Point3D pt) {
        roiLock.lock();
        try {
            if (index >= 0 && index < points3D.size()) {
                points3D.set(index, pt);
                needsUpdate = true;
            }
        } finally {
            roiLock.unlock();
        }
    }

    public List<Point3D> getPoints3D() {
        roiLock.lock();
        try {
            return new ArrayList<>(points3D);
        } finally {
            roiLock.unlock();
        }
    }

    /**
     * Get points (returns a copy)
     */
    public List<Point2D.Float> getPoints() {
        roiLock.lock();
        try {
            return new ArrayList<>(points);
        } finally {
            roiLock.unlock();
        }
    }

    /**
     * Update bounding rectangle
     */
    private void updateRect() {
        if (points.isEmpty()) {
            rect = new Rectangle();
            return;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (Point2D.Float pt : points) {
            if (pt.x < minX)
                minX = pt.x;
            if (pt.y < minY)
                minY = pt.y;
            if (pt.x > maxX)
                maxX = pt.x;
            if (pt.y > maxY)
                maxY = pt.y;
        }

        // マージンを持たせて、サイズ0のROIでも統計が表示されるようにする
        rect = new Rectangle((int) minX, (int) minY, (int) Math.max(1, maxX - minX), (int) Math.max(1, maxY - minY));
    }

    /**
     * HOROS-20240407準拠: RepulsorツールによるROIの変形と再サンプリング
     * DCMView.m 5815-5884行目のロジックを移植
     * 
     * @return 実際に変形が行われた場合にtrue
     */
    public boolean repulse(Point2D.Float mousePt, double radius, double scale, float pixSpacingRatio) {
        if (locked || !selectable)
            return false;

        // 除外されるタイプ (Horos準拠: tAxis, tAngle, tArrow, tDynAngle 等)
        if (type == DCMView.ToolMode.tAxis || type == DCMView.ToolMode.tAngle ||
                type == DCMView.ToolMode.tArrow || type == DCMView.ToolMode.tDynamicAngle ||
                type == DCMView.ToolMode.tText || type == DCMView.ToolMode.tPlain) {
            return false;
        }

        roiLock.lock();
        try {
            boolean changed = false;
            double minD = 10.0 / scale;
            double maxD = 50.0 / scale;
            double maxN = 10.0 * scale;
            int nAdded = 0;

            Rectangle2D repulsorRect = new Rectangle2D.Double(
                    mousePt.x - radius, mousePt.y - radius, radius * 2.0, radius * 2.0);

            // 各点をチェック
            for (int i = 0; i < points.size(); i++) {
                Point2D.Float pt = points.get(i);

                // バウンディングボックスチェックで高速化
                if (repulsorRect.contains(pt)) {
                    double dx = pt.x - mousePt.x;
                    double dy = (pt.y - mousePt.y) * pixSpacingRatio;
                    double d = Math.sqrt(dx * dx + dy * dy);

                    if (d < radius && d > 0) {
                        // 1. 点を押し出す (境界線まで)
                        float pushX = (float) (dx / d * radius - dx);
                        float pushY = (float) (dy / d * radius - dy);

                        // 2D Pointの場合は矩形ごと移動
                        if (type == DCMView.ToolMode.t2DPoint) {
                            if (rect != null) {
                                rect.x += (int) pushX;
                                rect.y += (int) (pushY / pixSpacingRatio);
                                // 2D Pointの場合、points[0]も更新
                                if (!points.isEmpty()) {
                                    Point2D.Float first = points.get(0);
                                    first.x = rect.x;
                                    first.y = rect.y;
                                }
                            }
                        } else {
                            pt.x += pushX;
                            pt.y += pushY / pixSpacingRatio;
                            points.set(i, pt);
                        }
                        changed = true;

                        // 2. 近隣点の再サンプリング (Subdivide/Simplify) - Horos 5845行目
                        for (int delta = -1; delta <= 1; delta += 2) {
                            int k = i + delta;

                            // 閉じている場合のインデックス処理
                            if (closed && points.size() > 2) {
                                if (k == -1)
                                    k = points.size() - 1;
                                else if (k == points.size())
                                    k = 0;
                            }

                            if (k != i && k >= 0 && k < points.size()) {
                                Point2D.Float pt2 = points.get(k);
                                double dx2 = pt2.x - pt.x;
                                double dy2 = (pt2.y - pt.y) * pixSpacingRatio;
                                double d2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);

                                if (d2 <= minD && d2 < radius && points.size() > 2) {
                                    // Simplify: 点を削除
                                    points.remove(k);
                                    if (delta == -1) {
                                        i--; // 前の点を消した場合はインデックスを戻す
                                    }
                                } else if ((d2 >= maxD || d2 >= radius) && nAdded < maxN) {
                                    // Subdivide: 点を追加
                                    Point2D.Float mid = new Point2D.Float((pt2.x + pt.x) / 2.0f, (pt2.y + pt.y) / 2.0f);
                                    int index = (delta == -1) ? i : i + 1;
                                    points.add(index, mid);
                                    nAdded++;
                                    if (delta == -1) {
                                        i++; // 手前に追加した場合はカレントインデックスを進める
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (changed) {
                if (type == DCMView.ToolMode.tMesure) {
                    type = DCMView.ToolMode.tOPolygon;
                }
                updateRect();
                needsUpdate = true;
            }
            return changed;
        } finally {
            roiLock.unlock();
        }
    }

    /**
     * HOROS: - (void)drawROI:(CGContextRef)ctx
     */
    public void draw(Graphics2D g2d, boolean selected) {
        draw(g2d, selected, false);
    }

    public void draw(Graphics2D g2d, boolean selected, boolean suppressLabels) {
        if (hidden || points.isEmpty()) {
            return;
        }

        // スケールに応じて線の太さとフォントサイズを調整（画面上で一定の見え方にするため）
        java.awt.geom.AffineTransform at = g2d.getTransform();
        double currentScale = Math.abs(at.getScaleX());
        if (currentScale == 0)
            currentScale = 1.0;

        java.awt.Font originalFont = g2d.getFont();
        java.awt.Stroke originalStroke = g2d.getStroke();
        Composite originalComposite = g2d.getComposite();

        // HOROS-20240407: Set Opacity
        if (opacity < 1.0f) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        }

        // HOROS-20240407: Use ROI thickness (LineWidth)
        float strokeWidth = (float) (this.thickness / currentScale);
        if (selected) {
            strokeWidth *= 2.0f; // 選択中は少し太くする
        }
        float outlineWidth = strokeWidth * 2.5f;

        // フォントサイズを調整 (Horizontal: thickness slider usually affects font size in some
        // tools)
        float fontSize = (float) (12.0 / currentScale);
        if (type == DCMView.ToolMode.tText) {
            // Text ROI: Horos uses 12.0 + thickness * 2.0 (but adjusted for Java coordinate
            // system)
            fontSize = (float) ((12.0f + thickness * 2.0f) / currentScale);
        }
        g2d.setFont(originalFont.deriveFont(fontSize));

        // 選択時の色設定 (Horos準拠: 0.5, 0.5, 1.0)
        Color currentColor = selected ? SELECTION_COLOR : color;

        // 初期色を設定
        g2d.setColor(currentColor);

        switch (type) {
            case tMesure:
                // Draw line measurement
                if (points.size() >= 2) {
                    Point2D.Float p1 = points.get(0);
                    Point2D.Float p2 = points.get(1);

                    // 黒の縁取りを描画して視認性を高める
                    g2d.setColor(Color.BLACK);
                    g2d.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(new java.awt.geom.Line2D.Float(p1.x, p1.y, p2.x, p2.y));

                    // 本体を描画
                    g2d.setColor(currentColor);
                    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(new java.awt.geom.Line2D.Float(p1.x, p1.y, p2.x, p2.y));

                    // 選択中のハンドル（端点）: Horos準拠で薄い青色の丸い点
                    if (selected) {
                        drawHandle(g2d, p1.x, p1.y, currentScale, 0);
                        drawHandle(g2d, p2.x, p2.y, currentScale, 1);
                    }

                    // Draw measurement text with shadow
                    double lengthInMm = calculateLength();
                    String text = (type == DCMView.ToolMode.tMesure ? "Length: " : "")
                            + formattedLength(lengthInMm / 10.0); // Convert mm to cm for formatter

                    // アンカーポイント（初期位置は線分の中央）
                    float anchorX = (p1.x + p2.x) / 2.0f;
                    float anchorY = (p1.y + p2.y) / 2.0f;

                    if (!suppressLabels)
                        drawTextBox(g2d, text, anchorX, anchorY, currentScale, selected, currentColor);
                }
                break;

            case tCurvedROI:
                if (points.size() >= 2) {
                    List<Point2D.Float> drawPoints = (isSpline && points.size() >= 3) ? cachedSplinePoints : points;
                    if (drawPoints == null)
                        drawPoints = points;

                    Path2D.Float path = new Path2D.Float();
                    path.moveTo(drawPoints.get(0).x, drawPoints.get(0).y);
                    for (int i = 1; i < drawPoints.size(); i++) {
                        path.lineTo(drawPoints.get(i).x, drawPoints.get(i).y);
                    }

                    // 黒の縁取り
                    g2d.setColor(Color.BLACK);
                    g2d.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(path);

                    // 本体を描画 (CurvedROIは目立つように少し太めにするか、特定の色にするのがHoros流)
                    g2d.setColor(currentColor);
                    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    // HOROS-20240407 準拠: Z方向の近さによって透明度を変える
                    if (parentView != null && parentView.getCurDCM() != null && !points3D.isEmpty()) {
                        double curZ = parentView.getCurDCM().getSliceLocation();
                        float alpha = 1.0f;
                        // 簡単のため、最初の点と現在のスライスの距離で判定
                        double distZ = Math.abs(points3D.get(0).z - curZ);
                        // スライス厚の3倍以上離れていたら半透明
                        double thickness = parentView.getCurDCM().getSliceThickness();
                        if (distZ > thickness * 3.0)
                            alpha = 0.3f;
                        else if (distZ > thickness)
                            alpha = 0.6f;

                        if (alpha < 1.0f) {
                            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * opacity));
                        }
                    }

                    g2d.draw(path);
                    g2d.setComposite(originalComposite); // 戻す

                    // 補助情報
                    if (!suppressLabels) {
                        double totalLen = calculateLength();
                        String text = String.format("Curved MPR: %.1f mm", totalLen);
                        Point2D.Float lastPt = points.get(points.size() - 2); // 確定済みの最後の点
                        if (points.size() < 2)
                            lastPt = points.get(0);
                        drawTextBox(g2d, text, lastPt.x, lastPt.y, currentScale, selected, currentColor);
                    }

                    // ハンドル描画
                    if (selected) {
                        for (int i = 0; i < points.size() - 1; i++) {
                            drawHandle(g2d, points.get(i).x, points.get(i).y, currentScale, i);
                        }
                    }
                }
                break;

            case tOval:
                // Draw oval
                if (rect != null) {
                    java.awt.geom.Ellipse2D.Float oval = new java.awt.geom.Ellipse2D.Float(rect.x, rect.y, rect.width,
                            rect.height);

                    // 黒の縁取り
                    g2d.setColor(Color.BLACK);
                    g2d.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(oval);

                    // 本体を描画
                    g2d.setColor(currentColor);
                    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(oval);

                    if (fill) {
                        g2d.fill(oval);
                    }

                    // 選択中はハンドルを表示 (Horos準拠: 中心 + 四隅の5点)
                    if (selected) {
                        float centerX = rect.x + rect.width / 2.0f;
                        float centerY = rect.y + rect.height / 2.0f;
                        drawHandle(g2d, centerX, centerY, currentScale, 0); // Center

                        drawHandle(g2d, rect.x, rect.y, currentScale, 1); // Top-Left
                        drawHandle(g2d, rect.x + rect.width, rect.y, currentScale, 2); // Top-Right
                        drawHandle(g2d, rect.x, rect.y + rect.height, currentScale, 3); // Bottom-Left
                        drawHandle(g2d, rect.x + rect.width, rect.y + rect.height, currentScale, 4); // Bottom-Right
                    }

                    if (!suppressLabels)
                        drawStatistics(g2d, currentScale, selected);
                }
                break;

            case tROI:
                // Draw rectangle ROI
                if (rect != null) {
                    java.awt.geom.Rectangle2D.Float r = new java.awt.geom.Rectangle2D.Float(rect.x, rect.y, rect.width,
                            rect.height);

                    // 黒の縁取り
                    g2d.setColor(Color.BLACK);
                    g2d.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(r);

                    // 本体を描画
                    g2d.setColor(currentColor);
                    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(r);

                    if (fill) {
                        g2d.fill(r);
                    }

                    // 選択中はハンドルを表示 (Horos準拠: Center + 四隅)
                    if (selected) {
                        float centerX = rect.x + rect.width / 2.0f;
                        float centerY = rect.y + rect.height / 2.0f;
                        drawHandle(g2d, centerX, centerY, currentScale, 0); // Center

                        drawHandle(g2d, rect.x, rect.y, currentScale, 1); // Top-Left
                        drawHandle(g2d, rect.x + rect.width, rect.y, currentScale, 2); // Top-Right
                        drawHandle(g2d, rect.x, rect.y + rect.height, currentScale, 3); // Bottom-Left
                        drawHandle(g2d, rect.x + rect.width, rect.y + rect.height, currentScale, 4); // Bottom-Right
                    }

                    if (!suppressLabels)
                        drawStatistics(g2d, currentScale, selected);
                }
                break;

            case tCPolygon:
            case tOPolygon:
            case tPencil:
            case tBrush:
                // Draw polygon / pencil / brush
                if (points.size() >= 2) {
                    boolean isCreating = (parentView != null && parentView.getNewRoi() == this);

                    // キャッシュの更新
                    validateCache();

                    List<Point2D.Float> drawPoints = (isSpline && points.size() >= 3) ? cachedSplinePoints : points;
                    if (drawPoints == null)
                        drawPoints = points;

                    Path2D.Float drawPath = new Path2D.Float();
                    drawPath.moveTo(drawPoints.get(0).x, drawPoints.get(0).y);
                    for (int i = 1; i < drawPoints.size(); i++) {
                        drawPath.lineTo(drawPoints.get(i).x, drawPoints.get(i).y);
                    }

                    if (!isCreating
                            && (closed || type == DCMView.ToolMode.tCPolygon || type == DCMView.ToolMode.tPencil)) {
                        drawPath.closePath();
                    }

                    // 黒の縁取り (Brushツール以外)
                    if (type != DCMView.ToolMode.tBrush) {
                        g2d.setColor(Color.BLACK);
                        g2d.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2d.draw(drawPath);
                    }

                    // 本体を描画
                    g2d.setColor(currentColor);
                    if (type == DCMView.ToolMode.tBrush) {
                        // Brush specific: thickness is RADIUS (1-17), so diameter is 2*thickness
                        float brushWidth = this.thickness * 2.0f;
                        if (isEraseMode) {
                            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
                        }
                        g2d.setStroke(new BasicStroke(brushWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    } else {
                        g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    }
                    g2d.draw(drawPath);

                    // Filled ROI (Polygon/Oval): Fill with opacity
                    if (!isCreating && (type != DCMView.ToolMode.tBrush && fill)) {
                        g2d.fill(drawPath);
                    }

                    // Compositeを元に戻す
                    g2d.setComposite(originalComposite);

                    // Opened Polygon の "Closed Enough" 判定と補助線の描画 (Horos準拠)
                    if (type == DCMView.ToolMode.tOPolygon && !isCreating && drawPoints.size() >= 3) {
                        double totalLen = 0;
                        for (int i = 0; i < drawPoints.size() - 1; i++) {
                            totalLen += drawPoints.get(i).distance(drawPoints.get(i + 1));
                        }
                        double firstLastDist = drawPoints.get(0).distance(drawPoints.get(drawPoints.size() - 1));

                        if (firstLastDist < (totalLen / 3.0)) {
                            // ほぼ閉じている場合は、薄い色で始点と終点を結ぶ線を描画
                            g2d.setColor(new Color(currentColor.getRed(), currentColor.getGreen(),
                                    currentColor.getBlue(), 64));
                            Point2D.Float pStart = drawPoints.get(0);
                            Point2D.Float pEnd = drawPoints.get(drawPoints.size() - 1);
                            g2d.draw(new java.awt.geom.Line2D.Float(pStart.x, pStart.y, pEnd.x, pEnd.y));
                        }
                    }

                    if (!isCreating && fill
                            && (closed || type == DCMView.ToolMode.tCPolygon || type == DCMView.ToolMode.tPencil)) {
                        g2d.fill(drawPath);
                    }

                    // 作成中または選択中はハンドルを表示 (Horos準拠)
                    if (selected || isCreating) {
                        for (int i = 0; i < points.size(); i++) {
                            Point2D.Float pt = points.get(i);
                            drawHandle(g2d, pt.x, pt.y, currentScale, i);
                        }
                    }

                    // 統計を表示（確定後のみ）
                    if (!isCreating && (type == DCMView.ToolMode.tCPolygon || type == DCMView.ToolMode.tPencil
                            || type == DCMView.ToolMode.tOPolygon || type == DCMView.ToolMode.tBrush)) {
                        if (!suppressLabels)
                            drawStatistics(g2d, currentScale, selected);
                    }
                }
                break;

            case tAngle:
                // Draw angle
                if (points.size() >= 2) {
                    Point2D.Float p1 = points.get(0);
                    Point2D.Float p2 = points.get(1);

                    // HOROS-20240407準拠: リアルタイムに角度を表示するために描画時にタイトルデータを準備
                    prepareTextualData();

                    Path2D.Float path = new Path2D.Float();
                    path.moveTo(p1.x, p1.y);
                    path.lineTo(p2.x, p2.y);

                    if (points.size() >= 3) {
                        Point2D.Float p3 = points.get(2);
                        path.lineTo(p3.x, p3.y);
                    }

                    // 黒の縁取り
                    g2d.setColor(Color.BLACK);
                    g2d.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(path);

                    // 本体
                    g2d.setColor(currentColor);
                    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(path);

                    // 選択中または新規作成中はハンドルを表示
                    if (selected || (parentView != null && parentView.getNewRoi() == this)) {
                        for (int i = 0; i < points.size(); i++) {
                            Point2D.Float pt = points.get(i);
                            drawHandle(g2d, pt.x, pt.y, currentScale, i);
                        }
                    }

                    if (!suppressLabels)
                        drawStatistics(g2d, currentScale, selected);
                }
                break;

            case tText:
                // Draw text
                if (!points.isEmpty()) {
                    Point2D.Float p = points.get(0);

                    // Update textBoxRect for selection (image coordinates)
                    FontMetrics fm = g2d.getFontMetrics();
                    Rectangle2D bounds = fm.getStringBounds(name, g2d);
                    textBoxRect = new java.awt.geom.Rectangle2D.Float(
                            p.x,
                            p.y - (float) bounds.getHeight() + fm.getDescent(), // Baseline adjustment
                            (float) bounds.getWidth(),
                            (float) bounds.getHeight());

                    // 影付きで描画
                    if (!suppressLabels) {
                        g2d.setColor(Color.BLACK);
                        g2d.drawString(name, p.x + (float) (1.0 / currentScale), p.y + (float) (1.0 / currentScale));
                        g2d.setColor(currentColor);
                        g2d.drawString(name, p.x, p.y);
                    }

                    // 選択中はハンドルを表示 (Horos準拠: 4隅 + 起点)
                    if (selected) {
                        drawHandle(g2d, p.x, p.y, currentScale, 0); // Origin

                        if (textBoxRect != null) {
                            drawHandle(g2d, textBoxRect.x, textBoxRect.y, currentScale, 1); // Top-Left
                            drawHandle(g2d, textBoxRect.x + textBoxRect.width, textBoxRect.y, currentScale, 2); // Top-Right
                            drawHandle(g2d, textBoxRect.x, textBoxRect.y + textBoxRect.height, currentScale, 3); // Bottom-Left
                            drawHandle(g2d, textBoxRect.x + textBoxRect.width, textBoxRect.y + textBoxRect.height,
                                    currentScale, 4); // Bottom-Right
                        }
                    }
                }
                break;

            case tArrow:
                // Draw arrow
                if (points.size() >= 2) {
                    // Horos spec: points.get(0) is the arrow head
                    Point2D.Float head = points.get(0);
                    Point2D.Float tail = points.get(1);

                    // 黒の縁取り
                    g2d.setColor(Color.BLACK);
                    g2d.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(new java.awt.geom.Line2D.Float(head.x, head.y, tail.x, tail.y));
                    drawArrowHead(g2d, head, tail, currentScale);

                    // 本体を描画
                    g2d.setColor(currentColor);
                    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(new java.awt.geom.Line2D.Float(head.x, head.y, tail.x, tail.y));
                    drawArrowHead(g2d, head, tail, currentScale);

                    // 選択中のハンドル（端点）: Horos準拠
                    if (selected) {
                        drawHandle(g2d, head.x, head.y, currentScale, 0);
                        drawHandle(g2d, tail.x, tail.y, currentScale, 1);
                    }
                }
                break;

            case t2DPoint:
                // HOROS-20240407準拠: 二重円（塗りつぶし円 + 外枠円）を描画
                if (!points.isEmpty()) {
                    Point2D.Float p = points.get(0);
                    float innerRadius = (float) (2.5 / currentScale);
                    float outerRadius = (float) (6.0 / currentScale);

                    // 黒の縁取り
                    g2d.setColor(Color.BLACK);
                    g2d.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(new java.awt.geom.Ellipse2D.Float(p.x - outerRadius, p.y - outerRadius, outerRadius * 2,
                            outerRadius * 2));

                    // 本体（外枠）
                    g2d.setColor(currentColor);
                    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(new java.awt.geom.Ellipse2D.Float(p.x - outerRadius, p.y - outerRadius, outerRadius * 2,
                            outerRadius * 2));

                    // 内側の塗りつぶし円
                    g2d.fill(new java.awt.geom.Ellipse2D.Float(p.x - innerRadius, p.y - innerRadius, innerRadius * 2,
                            innerRadius * 2));

                    // 統計（Point No, 解析値, 2D Pos, 3D Pos）を表示
                    if (!suppressLabels)
                        drawStatistics(g2d, currentScale, selected);
                }
                break;

            case tAxis:
                if (points.size() >= 2) {
                    // HOROS-20240407準拠: 常に点間を結んで「外枠」を描画
                    Path2D.Float boxPath = new Path2D.Float();
                    boxPath.moveTo(points.get(0).x, points.get(0).y);
                    for (int i = 1; i < points.size(); i++) {
                        boxPath.lineTo(points.get(i).x, points.get(i).y);
                    }
                    if (points.size() >= 3) {
                        boxPath.closePath();
                    }

                    // ボックスの塗りつぶし (Opacity 25%)
                    if (points.size() >= 3) {
                        Composite oldComp = g2d.getComposite();
                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f * opacity));
                        g2d.setColor(currentColor);
                        g2d.fill(boxPath);
                        g2d.setComposite(oldComp);
                    }

                    // 外枠の線
                    g2d.setColor(currentColor);
                    g2d.setStroke(new BasicStroke(strokeWidth));
                    g2d.draw(boxPath);

                    // HOROS-20240407準拠: 4点揃っており、かつ作成中でない場合のみ交差線を描画
                    boolean isNewRoi = (parentView != null && parentView.getNewRoi() == this);
                    if (points.size() >= 4 && !isNewRoi) {
                        Point2D.Float p0 = points.get(0);
                        Point2D.Float p1 = points.get(1);
                        Point2D.Float p2 = points.get(2);
                        Point2D.Float p3 = points.get(3);

                        // 各辺の中点を算出
                        Point2D.Float m01 = new Point2D.Float((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f);
                        Point2D.Float m23 = new Point2D.Float((p2.x + p3.x) / 2f, (p2.y + p3.y) / 2f);
                        Point2D.Float m03 = new Point2D.Float((p0.x + p3.x) / 2f, (p0.y + p3.y) / 2f);
                        Point2D.Float m12 = new Point2D.Float((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f);

                        // 延長距離 (125ピクセル相当をイメージ座標に換算)
                        float extension = (float) (125.0 / currentScale);

                        // --- Axis 1 (Blue) ---
                        g2d.setColor(Color.BLUE);
                        drawExtendedLine(g2d, m01, m23, extension, true); // Y方向ベースで延長

                        // --- Axis 2 (Red) ---
                        g2d.setColor(Color.RED);
                        drawExtendedLine(g2d, m03, m12, extension, false); // X方向ベースで延長
                    }

                    if (selected || (parentView != null && parentView.getNewRoi() == this)) {
                        for (int i = 0; i < points.size(); i++) {
                            drawHandle(g2d, points.get(i).x, points.get(i).y, currentScale, i);
                        }
                    }
                    if (!suppressLabels)
                        drawStatistics(g2d, currentScale, selected);
                }
                break;

            case tDynamicAngle:
                if (points.size() >= 2) {
                    Point2D.Float p0 = points.get(0);
                    Point2D.Float p1 = points.get(1);
                    g2d.draw(new java.awt.geom.Line2D.Float(p0.x, p0.y, p1.x, p1.y));

                    // HOROS-20240407準拠: リアルタイムに角度を表示するために描画時にタイトルデータを準備
                    prepareTextualData();

                    if (points.size() >= 3) {
                        Point2D.Float p2 = points.get(2);
                        // HOROS-20240407準拠: ポイント1とポイント2の間を非常に薄い連続線で結ぶ（ガイド線）
                        Stroke oldStroke = g2d.getStroke();
                        Color oldColor = g2d.getColor();
                        // Horos: 0.1 opacity
                        g2d.setColor(new Color(oldColor.getRed(), oldColor.getGreen(), oldColor.getBlue(), 30));
                        g2d.draw(new java.awt.geom.Line2D.Float(p1.x, p1.y, p2.x, p2.y));
                        g2d.setStroke(oldStroke);
                        g2d.setColor(oldColor);

                        if (points.size() >= 4) {
                            Point2D.Float p3 = points.get(3);
                            g2d.draw(new java.awt.geom.Line2D.Float(p2.x, p2.y, p3.x, p3.y));
                        }
                    }
                    if (selected || (parentView != null && parentView.getNewRoi() == this)) {
                        for (int i = 0; i < points.size(); i++) {
                            drawHandle(g2d, points.get(i).x, points.get(i).y, currentScale, i);
                        }
                    }
                    if (!suppressLabels)
                        drawStatistics(g2d, currentScale, selected);
                }
                break;

            case tPerpendicular:
                if (points.size() >= 6) {
                    Point2D.Float p0 = points.get(0);
                    Point2D.Float p1 = points.get(1);
                    Point2D.Float p2 = points.get(2);
                    Point2D.Float p3 = points.get(3);
                    Point2D.Float p4 = points.get(4);
                    Point2D.Float p5 = points.get(5);

                    // HOROS-20240407準拠: リアルタイムに値を更新
                    prepareTextualData();

                    // 黒の縁取り
                    g2d.setColor(Color.BLACK);
                    g2d.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(new java.awt.geom.Line2D.Float(p4.x, p4.y, p5.x, p5.y));
                    g2d.draw(new java.awt.geom.Line2D.Float(p0.x, p0.y, p1.x, p1.y));
                    g2d.draw(new java.awt.geom.Line2D.Float(p2.x, p2.y, p3.x, p3.y));

                    // 本体
                    g2d.setColor(currentColor);
                    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.draw(new java.awt.geom.Line2D.Float(p4.x, p4.y, p5.x, p5.y));
                    g2d.draw(new java.awt.geom.Line2D.Float(p0.x, p0.y, p1.x, p1.y));
                    g2d.draw(new java.awt.geom.Line2D.Float(p2.x, p2.y, p3.x, p3.y));

                    // ラベル "A", "B", "C" を描画
                    float offset = (float) (8.0 / currentScale);
                    g2d.drawString("A", p5.x + offset, p5.y);
                    g2d.drawString("B", p1.x + offset, p1.y);
                    g2d.drawString("C", p3.x + offset, p3.y);

                    // 選択中または新規作成中はハンドルを表示
                    if (selected || (parentView != null && parentView.getNewRoi() == this)) {
                        // 可動ハンドル: 1, 3, 4, 5 (0 と 2 はベースライン上の拘束点)
                        drawHandle(g2d, p1.x, p1.y, currentScale, 1);
                        drawHandle(g2d, p3.x, p3.y, currentScale, 3);
                        drawHandle(g2d, p4.x, p4.y, currentScale, 4);
                        drawHandle(g2d, p5.x, p5.y, currentScale, 5);
                    }

                    if (!suppressLabels)
                        drawStatistics(g2d, currentScale, selected);
                }
                break;

            default:
                // Draw points
                float dotSize = (float) (4.0 / currentScale);
                for (Point2D.Float pt : points) {
                    g2d.fill(new java.awt.geom.Ellipse2D.Float(pt.x - dotSize / 2, pt.y - dotSize / 2, dotSize,
                            dotSize));
                }
                break;
        }

        g2d.setFont(originalFont); // 元に戻す
        g2d.setStroke(originalStroke);
        g2d.setComposite(originalComposite);
    }

    /**
     * Draw arrow head
     */
    private void drawArrowHead(Graphics2D g2d, Point2D.Float headPos, Point2D.Float tailPos, double currentScale) {
        // Horos: head is at headPos (points.get(0))
        double angle = Math.atan2(headPos.y - tailPos.y, headPos.x - tailPos.x);
        float arrowSize = (float) (12.0 / currentScale);

        Path2D.Float head = new Path2D.Float();
        head.moveTo(headPos.x, headPos.y);
        head.lineTo(headPos.x - arrowSize * Math.cos(angle - Math.PI / 6),
                headPos.y - arrowSize * Math.sin(angle - Math.PI / 6));
        head.moveTo(headPos.x, headPos.y);
        head.lineTo(headPos.x - arrowSize * Math.cos(angle + Math.PI / 6),
                headPos.y - arrowSize * Math.sin(angle + Math.PI / 6));

        g2d.draw(head);
    }

    /**
     * Get source DicomPix
     */
    public DicomPix getSourcePix() {
        return sourcePix;
    }

    private void validateCache() {
        if (!needsUpdate && (cachedPath != null || (type == DCMView.ToolMode.tText))) {
            return;
        }

        roiLock.lock();
        try {
            if (type == DCMView.ToolMode.tCPolygon || type == DCMView.ToolMode.tOPolygon
                    || type == DCMView.ToolMode.tPencil || type == DCMView.ToolMode.tBrush) {
                List<Point2D.Float> checkPoints = points;
                if (isSpline && points.size() >= 3) {
                    checkPoints = getSplinePoints(2.0);
                    cachedSplinePoints = checkPoints;
                } else {
                    cachedSplinePoints = null;
                }

                if (checkPoints.size() >= 1) {
                    cachedPath = new Path2D.Float();
                    cachedPath.moveTo(checkPoints.get(0).x, checkPoints.get(0).y);
                    for (int i = 1; i < checkPoints.size(); i++) {
                        cachedPath.lineTo(checkPoints.get(i).x, checkPoints.get(i).y);
                    }
                    // ヒットテストや統計計算用には（閉じることができるツールなら）閉じたパスを作る
                    if (type == DCMView.ToolMode.tCPolygon || type == DCMView.ToolMode.tPencil) {
                        cachedPath.closePath();
                        cachedClosedPath = cachedPath;
                    } else if (type == DCMView.ToolMode.tOPolygon) {
                        cachedClosedPath = new Path2D.Float(cachedPath);
                        cachedClosedPath.closePath();
                    } else {
                        cachedClosedPath = null;
                    }

                    if (type == DCMView.ToolMode.tBrush) {
                        // ブラシの場合は厚みを持った形状を作成
                        BasicStroke stroke = new BasicStroke(this.thickness * 2.0f, BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND);
                        cachedStrokedShape = stroke.createStrokedShape(cachedPath);
                    } else {
                        cachedStrokedShape = null;
                    }
                } else {
                    cachedPath = null;
                    cachedStrokedShape = null;
                }
            }
            needsUpdate = false;
        } finally {
            roiLock.unlock();
        }
    }

    /**
     * 点がROIの内部に含まれるか判定（統計計算用、副作用なし）
     */
    public boolean isPointInside(Point2D.Float pt) {
        if (points.isEmpty())
            return false;

        switch (type) {
            case tOval:
                if (rect != null) {
                    float xc = rect.x + rect.width / 2.0f;
                    float yc = rect.y + rect.height / 2.0f;
                    float a = rect.width / 2.0f;
                    float b = rect.height / 2.0f;
                    if (a > 0 && b > 0) {
                        double val = Math.pow(pt.x - xc, 2) / Math.pow(a, 2) + Math.pow(pt.y - yc, 2) / Math.pow(b, 2);
                        return val <= 1.0;
                    }
                }
                break;
            case tROI:
                if (rect != null) {
                    return pt.x >= rect.x && pt.x <= rect.x + rect.width &&
                            pt.y >= rect.y && pt.y <= rect.y + rect.height;
                }
                break;
            case tCPolygon:
            case tOPolygon:
            case tPencil:
                validateCache();
                if (cachedClosedPath != null) {
                    return cachedClosedPath.contains(pt);
                }
                if (cachedPath != null) {
                    return cachedPath.contains(pt);
                }
                break;
            case tBrush:
                // HOROS-20240407: Brushはビットマップ的だが、ベクター実装では
                // ストローク形状を生成して判定する
                validateCache();
                if (cachedStrokedShape != null) {
                    return cachedStrokedShape.contains(pt);
                }
                return false;
            case t2DPoint:
            case tAxis:
            case tDynamicAngle:
            case tPerpendicular:
                // 統計計算用: これらのツールは面積を持たないか、線分ベースなので
                // 基本的に中身判定は行わない
                return false;
            default:
                break;
        }
        return false;
    }

    /**
     * Hit test - 点がROIの近くにあるか判定
     */
    public boolean hitTest(Point2D.Float pt, double tolerance) {
        if (hidden || !selectable || points.isEmpty()) {
            return false;
        }

        // 1. ハンドルのヒットテスト (最優先)
        // 許容範囲を少し広めに取る (Horosスタイル: 7px相当)
        for (int i = 0; i < points.size(); i++) {
            // HOROS-20240407準拠: Perpendicular Lines の 0 と 2 は可動ハンドルではない
            if (type == DCMView.ToolMode.tPerpendicular && (i == 0 || i == 2))
                continue;

            Point2D.Float p = points.get(i);
            if (p.distance(pt) < tolerance) {
                activeHandleIndex = i;
                clickInTextBox = false;
                return true;
            }
        }
        activeHandleIndex = -1;

        // 2. テキストボックスの判定
        if (textBoxRect != null && textBoxRect.contains(pt)) {
            clickInTextBox = true;
            return true;
        }
        clickInTextBox = false;

        switch (type) {
            case tMesure:
            case tArrow:
                if (points.size() >= 2) {
                    Point2D.Float p1 = points.get(0);
                    Point2D.Float p2 = points.get(1);
                    java.awt.geom.Line2D.Float line = new java.awt.geom.Line2D.Float(p1.x, p1.y, p2.x, p2.y);
                    return line.ptSegDist(pt) < tolerance;
                }
                break;
            case tAxis:
                if (points.size() >= 2) {
                    Point2D.Float p0 = points.get(0);
                    Point2D.Float p1 = points.get(1);
                    if (new java.awt.geom.Line2D.Float(p0.x, p0.y, p1.x, p1.y).ptSegDist(pt) < tolerance) {
                        return true;
                    }
                    if (points.size() >= 4) {
                        Point2D.Float p2 = points.get(2);
                        Point2D.Float p3 = points.get(3);
                        if (new java.awt.geom.Line2D.Float(p2.x, p2.y, p3.x, p3.y).ptSegDist(pt) < tolerance) {
                            return true;
                        }
                    }
                }
                break;

            case tAngle:
                if (points.size() >= 3) {
                    if (new java.awt.geom.Line2D.Float(points.get(0).x, points.get(0).y, points.get(1).x,
                            points.get(1).y)
                            .ptSegDist(pt) < tolerance)
                        return true;
                    if (new java.awt.geom.Line2D.Float(points.get(1).x, points.get(1).y, points.get(2).x,
                            points.get(2).y)
                            .ptSegDist(pt) < tolerance)
                        return true;
                }
                break;

            case tDynamicAngle:
                if (points.size() >= 4) {
                    if (new java.awt.geom.Line2D.Float(points.get(0).x, points.get(0).y, points.get(1).x,
                            points.get(1).y)
                            .ptSegDist(pt) < tolerance)
                        return true;
                    if (new java.awt.geom.Line2D.Float(points.get(2).x, points.get(2).y, points.get(3).x,
                            points.get(3).y)
                            .ptSegDist(pt) < tolerance)
                        return true;
                }
                break;

            case tPerpendicular:
                if (points.size() >= 6) {
                    if (new java.awt.geom.Line2D.Float(points.get(0).x, points.get(0).y, points.get(1).x,
                            points.get(1).y)
                            .ptSegDist(pt) < tolerance)
                        return true;
                    if (new java.awt.geom.Line2D.Float(points.get(2).x, points.get(2).y, points.get(3).x,
                            points.get(3).y)
                            .ptSegDist(pt) < tolerance)
                        return true;
                    if (new java.awt.geom.Line2D.Float(points.get(4).x, points.get(4).y, points.get(5).x,
                            points.get(5).y)
                            .ptSegDist(pt) < tolerance)
                        return true;
                }
                break;

            case tOval:
                // 楕円自体の判定（中が塗られていなくても境界線付近で反応させる）
                if (rect != null) {
                    // 1. ハンドルのヒットテスト (Resizable handles)
                    // Horos準拠: 中心 + 四隅の5点
                    float[][] handlePts = {
                            { rect.x + rect.width / 2.0f, rect.y + rect.height / 2.0f }, // Center (Index 0)
                            { rect.x, rect.y }, // Top-Left (Index 1)
                            { rect.x + rect.width, rect.y }, // Top-Right (Index 2)
                            { rect.x, rect.y + rect.height }, // Bottom-Left (Index 3)
                            { rect.x + rect.width, rect.y + rect.height } // Bottom-Right (Index 4)
                    };
                    for (int i = 0; i < handlePts.length; i++) {
                        if (new Point2D.Float(handlePts[i][0], handlePts[i][1]).distance(pt) < tolerance) {
                            activeHandleIndex = i;
                            return true;
                        }
                    }

                    // 2. 楕円の境界線/内部判定
                    // 楕円の方程式 (x-xc)^2/a^2 + (y-yc)^2/b^2 <= 1
                    float xc = rect.x + rect.width / 2.0f;
                    float yc = rect.y + rect.height / 2.0f;
                    float a = rect.width / 2.0f;
                    float b = rect.height / 2.0f;
                    if (a > 0 && b > 0) {
                        double val = Math.pow(pt.x - xc, 2) / Math.pow(a, 2) + Math.pow(pt.y - yc, 2) / Math.pow(b, 2);
                        // 内部または境界線付近 (toleranceを考慮)
                        if (val <= 1.0 || Math.abs(val - 1.0) < (tolerance / Math.min(a, b))) {
                            activeHandleIndex = -1;
                            return true;
                        }
                    }
                }
                break;
            case tROI:
                if (rect != null) {
                    // 1. ハンドルのヒットテスト
                    // Horos準拠: 中心 + 四隅の5点
                    float[][] handlePts = {
                            { rect.x + rect.width / 2.0f, rect.y + rect.height / 2.0f }, // Center (Index 0)
                            { rect.x, rect.y }, // Top-Left (Index 1)
                            { rect.x + rect.width, rect.y }, // Top-Right (Index 2)
                            { rect.x, rect.y + rect.height }, // Bottom-Left (Index 3)
                            { rect.x + rect.width, rect.y + rect.height } // Bottom-Right (Index 4)
                    };
                    for (int i = 0; i < handlePts.length; i++) {
                        if (new Point2D.Float(handlePts[i][0], handlePts[i][1]).distance(pt) < tolerance) {
                            activeHandleIndex = i;
                            return true;
                        }
                    }

                    // 2. 境界線/内部判定
                    if (pt.x >= rect.x - (float) tolerance && pt.x <= rect.x + rect.width + (float) tolerance &&
                            pt.y >= rect.y - (float) tolerance && pt.y <= rect.y + rect.height + (float) tolerance) {
                        activeHandleIndex = -1;
                        return true;
                    }
                }
                break;
            case tCPolygon:
            case tOPolygon:
            case tPencil:
            case tBrush:
                validateCache();
                if (cachedPath != null && (type == DCMView.ToolMode.tCPolygon || type == DCMView.ToolMode.tPencil
                        || type == DCMView.ToolMode.tBrush)) {
                    if (cachedPath.contains(pt)) {
                        return true;
                    }
                }

                // 境界線へのヒットテスト（開いている場合や、閉じていても中を塗っていない場合など）
                List<Point2D.Float> checkPoints = (isSpline && cachedSplinePoints != null) ? cachedSplinePoints
                        : points;

                double currentTolerance = tolerance;
                if (type == DCMView.ToolMode.tBrush) {
                    // ブラシの場合は厚み（半径）も含めてヒット判定
                    currentTolerance = Math.max(tolerance, (double) this.thickness);
                }

                for (int i = 0; i < checkPoints.size() - 1; i++) {
                    Point2D.Float p1 = checkPoints.get(i);
                    Point2D.Float p2 = checkPoints.get(i + 1);
                    if (new java.awt.geom.Line2D.Float(p1.x, p1.y, p2.x, p2.y).ptSegDist(pt) < currentTolerance) {
                        return true;
                    }
                }
                // 閉じたポリゴンの最後の線分
                if (type == DCMView.ToolMode.tCPolygon || type == DCMView.ToolMode.tPencil
                        || type == DCMView.ToolMode.tBrush) {
                    Point2D.Float p1 = checkPoints.get(checkPoints.size() - 1);
                    Point2D.Float p2 = checkPoints.get(0);
                    if (new java.awt.geom.Line2D.Float(p1.x, p1.y, p2.x, p2.y).ptSegDist(pt) < currentTolerance) {
                        return true;
                    }
                }
                break;
            default:
                break;
        }
        return false;
    }

    /**
     * Set source DicomPix for region-specific measurements
     */
    public void setSourcePix(DicomPix pix) {
        this.sourcePix = pix;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /**
     * Calculate length (for measurement ROI)
     */
    public double calculateLength() {
        if (type == DCMView.ToolMode.tCurvedROI && !points3D.isEmpty()) {
            double total = 0;
            for (int i = 0; i < points3D.size() - 1; i++) {
                Point3D p1 = points3D.get(i);
                Point3D p2 = points3D.get(i + 1);
                double dx = p1.x - p2.x;
                double dy = p1.y - p2.y;
                double dz = p1.z - p2.z;
                total += Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            return total;
        }

        if (points.size() < 2) {
            return 0;
        }

        Point2D.Float p1 = points.get(0);
        Point2D.Float p2 = points.get(1);

        double spX = pixelSpacingX;
        double spY = pixelSpacingY;

        if (sourcePix != null) {
            // リージョン内のサンプリング点として中点を使用（マルチリージョンUS対応）
            double centerX = (p1.x + p2.x) / 2.0;
            double centerY = (p1.y + p2.y) / 2.0;
            spX = sourcePix.getPixelSpacingXAt(centerX, centerY);
            spY = sourcePix.getPixelSpacingYAt(centerX, centerY);
        }

        double dx = (p2.x - p1.x) * spX;
        double dy = (p2.y - p1.y) * spY;

        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Calculate angle (for angle ROI)
     * HOROS-20240407準拠: ROI.m の Angle:p2 :p1 :p3 ロジック
     */
    public double calculateAngle() {
        if (points.size() < 3) {
            return 0;
        }

        Point2D.Float p2 = points.get(0);
        Point2D.Float p1 = points.get(1); // 頂点
        Point2D.Float p3 = points.get(2);

        double px = pixelSpacingX != 0 ? pixelSpacingX : 1.0;
        double py = pixelSpacingY != 0 ? pixelSpacingY : 1.0;

        double ax = p2.x * px - p1.x * px;
        double ay = p2.y * py - p1.y * py;
        double bx = p3.x * px - p1.x * px;
        double by = p3.y * py - p1.y * py;

        if (ax == 0 && ay == 0)
            return 0;
        if (bx == 0 && by == 0)
            return 0;

        double val = ((ax * bx) + (ay * by)) / (Math.sqrt(ax * ax + ay * ay) * Math.sqrt(bx * bx + by * by));
        // 浮動小数点の誤差を考慮して範囲を制限
        if (val > 1.0)
            val = 1.0;
        if (val < -1.0)
            val = -1.0;

        return Math.toDegrees(Math.acos(val));
    }

    /**
     * Calculate angle for 4-point dynamic angle (Cobb angle)
     * HOROS-20240407準拠: ROI.m の 4-point angle 計算ロジック
     */
    public double calculateDynamicAngle() {
        if (points.size() < 4) {
            return 0;
        }

        Point2D.Float p0 = points.get(0);
        Point2D.Float p1 = points.get(1);
        Point2D.Float p2 = points.get(2);
        Point2D.Float p3 = points.get(3);

        // 各線分の角度を算出 (物理空間座標に基づいて計算)
        double angle1 = Math.toDegrees(Math.atan2((p1.y - p0.y) * pixelSpacingY, (p1.x - p0.x) * pixelSpacingX));
        double angle2 = Math.toDegrees(Math.atan2((p3.y - p2.y) * pixelSpacingY, (p3.x - p2.x) * pixelSpacingX));

        double angle = angle1 - angle2;

        // HOROS-20240407準拠の正規化ロジック (ROI.m 6168-6176行目)
        if (angle < -180)
            angle = -180 - angle;
        else if (angle < -90)
            angle += 180;
        else if (angle < 0)
            angle *= -1;

        if (angle > 270)
            angle = 360 - angle;
        else if (angle > 180)
            angle -= 180;
        else if (angle > 90)
            angle = 180 - angle;

        return angle;
    }

    /**
     * HOROS-20240407準拠: Perpendicular Lines の拘束条件（垂直射影）を適用する
     * Point 4, 5: Baseline (Line A)
     * Point 0, 1: Line B (0 is on A, 1 is end)
     * Point 2, 3: Line C (2 is on A, 3 is end)
     */
    public void recalculatePerpendicular() {
        if (type != DCMView.ToolMode.tPerpendicular || points.size() < 6)
            return;

        Point2D.Float p4 = points.get(4);
        Point2D.Float p5 = points.get(5);

        // Point 0 は Point 1 を Line 4-5 に投影した点
        points.set(0, projectPointOnLine(points.get(1), p4, p5));

        // Point 2 は Point 3 を Line 4-5 に投影した点
        points.set(2, projectPointOnLine(points.get(3), p4, p5));
    }

    private Point2D.Float projectPointOnLine(Point2D.Float p, Point2D.Float a, Point2D.Float b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float denominator = dx * dx + dy * dy;
        if (denominator < 1e-10)
            return new Point2D.Float(a.x, a.y);

        float u = ((p.x - a.x) * dx + (p.y - a.y) * dy) / denominator;
        // Horos は Perpendicular Lines において u を [0, 1] にクランプしない（延長線上も許容）
        return new Point2D.Float(a.x + u * dx, a.y + u * dy);
    }

    private double calculateLength(Point2D.Float p1, Point2D.Float p2) {
        return calculateDistanceBetween(p1, p2);
    }

    // Getters and Setters

    public DCMView.ToolMode getType() {
        return type;
    }

    public void setType(DCMView.ToolMode type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = opacity;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public boolean isFill() {
        return fill;
    }

    public void setFill(boolean fill) {
        this.fill = fill;
    }

    public boolean isSpline() {
        return isSpline;
    }

    public void setIsSpline(boolean isSpline) {
        if (this.isSpline != isSpline) {
            this.isSpline = isSpline;
            this.needsUpdate = true;
        }
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }

    public Rectangle getRect() {
        return rect;
    }

    public void setPixelSpacing(double x, double y) {
        this.pixelSpacingX = x;
        this.pixelSpacingY = y;
    }

    public void setSOPInstanceUID(String sopUID) {
        this.sopInstanceUID = sopUID;
    }

    public String getSOPInstanceUID() {
        return sopInstanceUID;
    }

    public float getThickness() {
        return thickness;
    }

    public void setThickness(float thickness) {
        this.thickness = thickness;
    }

    public double getPixelSpacingX() {
        return pixelSpacingX;
    }

    public void setPixelSpacingX(double pixelSpacingX) {
        this.pixelSpacingX = pixelSpacingX;
    }

    public double getPixelSpacingY() {
        return pixelSpacingY;
    }

    public void setPixelSpacingY(double pixelSpacingY) {
        this.pixelSpacingY = pixelSpacingY;
    }

    /**
     * ROIが有効かどうかを判定（Horos-20240407準拠）
     * サイズが極端に小さい場合や、点数が不足している場合は無効(false)を返す。
     */
    public boolean isValid() {
        if (points == null || points.isEmpty()) {
            return false;
        }

        switch (type) {
            case tOval:
            case tROI:
                if (rect == null)
                    return false;
                // Horos: width < 0.2 || height < 0.2 は無効
                return Math.abs(rect.width) >= 0.2 && Math.abs(rect.height) >= 0.2;

            case tMesure:
            case tArrow:
                if (points.size() < 2)
                    return false;
                Point2D.Float p1 = points.get(0);
                Point2D.Float p2 = points.get(1);
                // Horos: 距離が0.2未満は無効
                return p1.distance(p2) >= 0.2;

            case tCPolygon:
            case tPencil:
            case tBrush:
                return points.size() >= 3;

            case tOPolygon:
                return points.size() >= 2;

            case tAngle:
                return points.size() >= 3;

            case tText:
                return points.size() >= 1;

            case tAxis:
            case tDynamicAngle:
                return points.size() >= 4;

            case tPerpendicular:
                return points.size() >= 6;

            default:
                return true;
        }
    }

    public void setTextBoxOffset(float x, float y) {
        this.textBoxOffsetX = x;
        this.textBoxOffsetY = y;
    }

    public float getTextBoxOffsetX() {
        return textBoxOffsetX;
    }

    public float getTextBoxOffsetY() {
        return textBoxOffsetY;
    }

    public boolean isClickInTextBox() {
        return clickInTextBox;
    }

    public int getActiveHandleIndex() {
        return activeHandleIndex;
    }

    public void setActiveHandleIndex(int index) {
        this.activeHandleIndex = index;
    }

    /**
     * Horos-compliant distance formatter.
     * 
     * @param lengthInCm length in centimeters
     * @return formatted string with unit (cm, mm, or µm)
     */
    private String formattedLength(double lengthInCm) {
        if (lengthInCm <= 0) {
            return "0.00 cm";
        }

        if (lengthInCm < 0.01) { // < 0.1 mm
            return String.format("%.2f \u00B5m", lengthInCm * 10000.0);
        } else if (lengthInCm < 1.0) { // < 1 cm
            return String.format("%.2f mm", lengthInCm * 10.0);
        } else {
            return String.format("%.2f cm", lengthInCm);
        }
    }

    /**
     * ROIの選択ハンドルを描画する (Horos準拠)
     */
    private void drawHandle(Graphics2D g2d, float x, float y, double scale, int index) {
        float handleSize = (float) (7.0 / scale);
        java.awt.geom.Ellipse2D.Float h = new java.awt.geom.Ellipse2D.Float(
                x - handleSize / 2, y - handleSize / 2, handleSize, handleSize);

        g2d.setColor(SELECTION_COLOR);
        g2d.fill(h);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke((float) (1.0 / scale)));
        g2d.draw(h);
    }

    /**
     * テキストボックスを描画（マルチライン対応）
     */
    public double getMean() {
        return rmean;
    }

    public double getMax() {
        return rmax;
    }

    public double getMin() {
        return rmin;
    }

    public double getStDev() {
        return rdev;
    }

    public double getArea() {
        if (type == DCMView.ToolMode.tAxis && points.size() >= 4) {
            double len1 = calculateDistanceBetween(points.get(0), points.get(1));
            double len2 = calculateDistanceBetween(points.get(2), points.get(3));
            // 楕円近似面積を計算 (PI * a/2 * b/2)
            return (Math.PI * len1 * len2) / 4.0;
        }
        return rtotal;
    }

    public double getPerimeter() {
        double perimeter = 0;
        if (points.size() < 2)
            return 0;

        for (int i = 0; i < points.size() - 1; i++) {
            perimeter += calculateDistanceBetween(points.get(i), points.get(i + 1));
        }
        if (closed && points.size() > 2) {
            perimeter += calculateDistanceBetween(points.get(points.size() - 1), points.get(0));
        }
        return perimeter;
    }

    private double calculateDistanceBetween(Point2D.Float p1, Point2D.Float p2) {
        double spX = pixelSpacingX;
        double spY = pixelSpacingY;

        if (sourcePix != null) {
            // 線分の中点でのサンプリング（USマルチリージョン対応）
            double midX = (p1.x + p2.x) / 2.0;
            double midY = (p1.y + p2.y) / 2.0;
            spX = sourcePix.getPixelSpacingXAt(midX, midY);
            spY = sourcePix.getPixelSpacingYAt(midX, midY);
        }

        double dx = (p2.x - p1.x) * spX;
        double dy = (p2.y - p1.y) * spY;

        return Math.sqrt(dx * dx + dy * dy);
    }

    public void updateStatistics() {
        // 本来はピクセルデータから計算するが、ここではプレースホルダ
        // DCMView側で計算してsetStatisticsが呼ばれることを前提とする
    }

    private String formatLength(double lengthInCm) {
        if (pixelUnit != null && !pixelUnit.isEmpty() && !pixelUnit.equals("mm")) {
            return String.format("%.2f %s", lengthInCm * 10.0, pixelUnit);
        }

        if (lengthInCm < 0.001) { // < 0.01 mm
            return String.format("%.2f \u00B5m", lengthInCm * 10000.0);
        } else if (lengthInCm < 1.0) { // < 10.0 mm (Horos usually stays mm under 1cm)
            return String.format("%.2f mm", lengthInCm * 10.0);
        } else {
            return String.format("%.2f cm", lengthInCm);
        }
    }

    private String formatArea(double areaInCm2) {
        if (pixelUnit != null && !pixelUnit.isEmpty() && !pixelUnit.equals("mm")) {
            // ピクセル単位がある場合（USなど）は、その単位系で面積を表示
            return String.format("%.2f %s\u00B2", areaInCm2 * 100.0, pixelUnit);
        }
        return String.format("%.2f cm\u00B2", areaInCm2);
    }

    private void prepareTextualData() {
        if (isStatsCalculating) {
            textualBoxLine1 = (name == null || name.isEmpty() || name.equalsIgnoreCase("Unnamed")) ? null : name;
            textualBoxLine2 = "Calculating...";
            textualBoxLine3 = null;
            textualBoxLine4 = null;
            textualBoxLine5 = null;
            textualBoxLine6 = null;
            return;
        }

        textualBoxLine1 = (name == null || name.isEmpty() || name.equalsIgnoreCase("Unnamed")) ? null : name;
        textualBoxLine2 = null;
        textualBoxLine3 = null;
        textualBoxLine4 = null;
        textualBoxLine5 = null;
        textualBoxLine6 = null;

        switch (type) {
            case tMesure:
                if (points.size() >= 2) {
                    double lengthInMm = calculateLength();
                    textualBoxLine2 = String.format("L: %s", formatLength(lengthInMm / 10.0));
                }
                break;

            case t2DPoint:
                textualBoxLine1 = String.format("Point %d", id);
                if (points.size() > 0) {
                    textualBoxLine2 = String.format("Value: %.1f", rmean);
                    Point2D.Float p = points.get(0);
                    textualBoxLine3 = String.format("X: %.1f", p.x);
                    textualBoxLine4 = String.format("Y: %.1f", p.y);
                }
                break;

            case tAxis:
                if (points.size() >= 2) {
                    Point2D.Float p0 = points.get(0);
                    Point2D.Float p1 = points.get(1);
                    double len1 = calculateDistanceBetween(p0, p1) / 10.0;
                    textualBoxLine2 = String.format("L: %s", formatLength(len1));

                    if (points.size() >= 4) {
                        Point2D.Float p2 = points.get(2);
                        Point2D.Float p3 = points.get(3);
                        double len2 = calculateDistanceBetween(p2, p3) / 10.0;
                        textualBoxLine3 = String.format("S: %s", formatLength(len2));

                        // 面積表示を追加（楕円近似など）
                        textualBoxLine4 = String.format("Area: %s", formatArea(getArea() / 100.0));
                    }
                }
                break;

            case tOPolygon: {
                updateStatistics();
                double perimeter = getPerimeter();
                if (points.size() >= 3) {
                    double gap = calculateDistanceBetween(points.get(0), points.get(points.size() - 1));
                    if (gap < perimeter / 3.0) { // RATIO_FOROPOLYGONAREA = 3.0
                        textualBoxLine2 = String.format("Area: %s", formatArea(getArea() / 100.0));
                        textualBoxLine3 = String.format("Mean: %.2f SD: %.2f", getMean(), getStDev());
                        textualBoxLine4 = String.format("Min: %.1f Max: %.1f", getMin(), getMax());
                        textualBoxLine5 = String.format("Length: %s", formatLength(perimeter / 10.0));
                        break;
                    }
                }
                textualBoxLine2 = String.format("Length: %s", formatLength(perimeter / 10.0));
                break;
            }

            case tCPolygon:
            case tOval:
            case tPencil:
            case tBrush:
                if (textualBoxLine1 == null) {
                    textualBoxLine1 = String.format("Region %d", id);
                }
                updateStatistics();
                textualBoxLine2 = String.format("Area: %s", formatArea(getArea() / 100.0));
                // 面積が 0 より大きい場合のみ統計（平均値・標準偏差等）を表示 (Horos準拠)
                if (getArea() > 0) {
                    textualBoxLine3 = String.format("Mean: %.2f SD: %.2f", getMean(), getStDev());
                    textualBoxLine4 = String.format("Min: %.1f Max: %.1f", getMin(), getMax());
                    textualBoxLine5 = String.format("Perim: %s", formatLength(getPerimeter() / 10.0));
                } else {
                    textualBoxLine3 = String.format("Perim: %s", formatLength(getPerimeter() / 10.0));
                }
                break;

            case tROI:
                if (textualBoxLine1 == null) {
                    textualBoxLine1 = String.format("Region %d", id);
                }
                updateStatistics();
                textualBoxLine2 = String.format("Area: %s", formatArea(getArea() / 100.0));
                textualBoxLine3 = String.format("Mean: %.2f SD: %.2f", getMean(), getStDev());
                textualBoxLine4 = String.format("Min: %.1f Max: %.1f", getMin(), getMax());
                // HOROS-20240407: Rectangle ROI displays W x L instead of Perimeter
                double width = rect.width * pixelSpacingX / 10.0;
                double height = rect.height * pixelSpacingY / 10.0;
                textualBoxLine5 = String.format("Size: %s x %s", formatLength(width), formatLength(height));
                break;

            case tAngle:
                if (points.size() >= 3) {
                    double angle = calculateAngle();
                    // HOROS-20240407準拠: 2段で表示
                    textualBoxLine2 = String.format("Angle: %.3f°", angle);
                    textualBoxLine3 = String.format("Angle 2: %.3f°", 360.0 - angle);
                }
                break;

            case tDynamicAngle:
                if (points.size() >= 4) {
                    double angle = calculateDynamicAngle();
                    // HOROS-20240407準拠: 2段で表示 (Angle と Angle 2)
                    textualBoxLine2 = String.format("Angle: %.2f°", angle);
                    textualBoxLine3 = String.format("Angle 2: %.2f°", 360.0 - angle);
                }
                break;

            case tPerpendicular:
                if (points.size() >= 6) {
                    // HOROS-20240407準拠: A, B, C の長さと B-C の距離
                    double lenA = calculateLength(points.get(4), points.get(5)) / 10.0;
                    double lenB = calculateLength(points.get(0), points.get(1)) / 10.0;
                    double lenC = calculateLength(points.get(2), points.get(3)) / 10.0;
                    double lenBC = calculateLength(points.get(0), points.get(2)) / 10.0;

                    textualBoxLine2 = String.format("A: %s", formatLength(lenA));
                    textualBoxLine3 = String.format("B: %s", formatLength(lenB));
                    textualBoxLine4 = String.format("C: %s", formatLength(lenC));
                    textualBoxLine5 = String.format("B-C: %s", formatLength(lenBC));
                }
                break;

            default:
                break;
        }
    }

    private void drawStatistics(Graphics2D g2d, double currentScale, boolean selected) {
        if (rect == null && type != DCMView.ToolMode.t2DPoint)
            return;

        boolean isCreating = (parentView != null && parentView.getNewRoi() == this);
        if (isCreating && (type == DCMView.ToolMode.tCPolygon || type == DCMView.ToolMode.tPencil)) {
            return;
        }

        prepareTextualData();

        StringBuilder sb = new StringBuilder();
        if (textualBoxLine1 != null)
            sb.append(textualBoxLine1).append("\n");
        if (textualBoxLine2 != null)
            sb.append(textualBoxLine2).append("\n");
        if (textualBoxLine3 != null)
            sb.append(textualBoxLine3).append("\n");
        if (textualBoxLine4 != null)
            sb.append(textualBoxLine4).append("\n");
        if (textualBoxLine5 != null)
            sb.append(textualBoxLine5).append("\n");
        if (textualBoxLine6 != null)
            sb.append(textualBoxLine6).append("\n");

        String resultText = sb.toString().trim();
        if (resultText.isEmpty())
            return;

        // HOROS-20240407準拠: ROIのアンカーポイントを決定する
        float anchorX = 0;
        float anchorY = Float.MAX_VALUE;

        if (type == DCMView.ToolMode.t2DPoint && !points.isEmpty()) {
            anchorX = points.get(0).x;
            anchorY = points.get(0).y;
        } else if (type == DCMView.ToolMode.tAngle && points.size() >= 2) {
            // Angleツールは頂点（point index 1）をアンカーにするのが最も直感的
            anchorX = points.get(1).x;
            anchorY = points.get(1).y;
        } else if (type == DCMView.ToolMode.tDynamicAngle && points.size() >= 2) {
            // Dynamic Angleは最初の線分の中点をとりあえずのアンカーにする
            anchorX = (points.get(0).x + points.get(1).x) / 2.0f;
            anchorY = (points.get(0).y + points.get(1).y) / 2.0f;
        } else if (!points.isEmpty()) {
            for (Point2D.Float pt : points) {
                if (pt.y < anchorY) { // Pixel coordinates: smaller Y is higher
                    anchorY = pt.y;
                    anchorX = pt.x;
                }
            }
        } else {
            anchorX = rect.x + rect.width / 2.0f;
            anchorY = rect.y;
        }

        Color textColor = selected ? SELECTION_COLOR : color;
        drawTextBox(g2d, resultText, anchorX, anchorY, currentScale, selected, textColor);
    }

    /**
     * HOROS-20240407準拠: 指定された方向に延長された直線を描画する
     */
    private void drawExtendedLine(Graphics2D g2d, Point2D.Float pStart, Point2D.Float pEnd, float extension,
            boolean yBased) {
        float dx = pEnd.x - pStart.x;
        float dy = pEnd.y - pStart.y;

        if (Math.abs(dx) < 0.0001 && Math.abs(dy) < 0.0001)
            return;

        float x1, y1, x2, y2;
        if (yBased) {
            // Y方向ベースの延長 (Axis 1 / Blue)
            float a = dy / dx;
            float b = pStart.y - a * pStart.x;

            y1 = Math.min(pStart.y, pEnd.y) - extension;
            y2 = Math.max(pStart.y, pEnd.y) + extension;

            if (Math.abs(dx) < 0.0001) { // 垂直
                x1 = pStart.x;
                x2 = pStart.x;
            } else {
                x1 = (y1 - b) / a;
                x2 = (y2 - b) / a;
            }
        } else {
            // X方向ベースの延長 (Axis 2 / Red)
            float a = dy / dx;
            float b = pStart.y - a * pStart.x;

            x1 = Math.min(pStart.x, pEnd.x) - extension;
            x2 = Math.max(pStart.x, pEnd.x) + extension;

            y1 = a * x1 + b;
            y2 = a * x2 + b;
        }

        g2d.draw(new java.awt.geom.Line2D.Float(x1, y1, x2, y2));
    }

    private void drawTextBox(Graphics2D g2d, String text, float anchorX, float anchorY, double currentScale,
            boolean selected, Color textColor) {
        String[] lines = text.split("\n");
        FontMetrics fm = g2d.getFontMetrics();
        float maxWidth = 0;
        float ascent = (float) fm.getAscent();
        float descent = (float) fm.getDescent();
        float lineHeight = ascent + descent;

        for (String line : lines) {
            maxWidth = Math.max(maxWidth, (float) fm.stringWidth(line));
        }

        float hPad = (float) (2.0 / currentScale);
        float rectW = maxWidth + hPad * 2;
        float rectH = lineHeight * lines.length;

        // デフォルトのYオフセットを、描画部と重ならないように調整 (-20px相当)
        float defaultYOffset = (float) (-20.0 / currentScale);

        float boxCenterX = anchorX + textBoxOffsetX;
        float boxCenterY = anchorY + defaultYOffset + textBoxOffsetY;

        float boxX = boxCenterX - rectW / 2.0f;
        float boxY = boxCenterY - rectH / 2.0f;

        textBoxRect = new java.awt.geom.Rectangle2D.Float(boxX, boxY, rectW, rectH);

        if (selected && (Math.abs(textBoxOffsetX) > 0.01 || Math.abs(textBoxOffsetY) > 0.01)) {
            g2d.setStroke(new BasicStroke((float) (1.0 / currentScale)));
            g2d.setColor(new Color(128, 128, 255, 128));
            g2d.draw(new java.awt.geom.Line2D.Float(anchorX, anchorY, boxCenterX, boxCenterY));
        }

        // HOROS: 視認性のために常に背景を描画
        if (selected) {
            g2d.setColor(new Color(128, 128, 255, 128));
        } else {
            g2d.setColor(new Color(0, 0, 0, 80)); // 30% black
        }
        g2d.fill(textBoxRect);

        float shadowOffset = (float) (0.5 / currentScale);
        for (int i = 0; i < lines.length; i++) {
            float lineY = boxY + ascent + i * lineHeight;
            float lineX = boxX + hPad;

            // Black Shadow
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.drawString(lines[i], lineX + shadowOffset, lineY + shadowOffset);

            // Text本体
            g2d.setColor(textColor);
            g2d.drawString(lines[i], lineX, lineY);
        }
    }

    /**
     * Spline points calculation matching Horos ROI.m spline()
     */
    private List<Point2D.Float> getSplinePoints(double scale) {
        if (points.size() < 3) {
            return new ArrayList<>(points);
        }

        boolean isCreating = (parentView != null && parentView.getNewRoi() == this);
        boolean closed = !isCreating && (type == DCMView.ToolMode.tCPolygon || type == DCMView.ToolMode.tPencil);

        int n = points.size();
        if (closed) {
            n++;
        }

        int nb = n + 2;
        double[] px = new double[nb];
        double[] py = new double[nb];

        for (int i = 0; i < points.size(); i++) {
            px[i + 1] = points.get(i).x;
            py[i + 1] = points.get(i).y;
        }

        if (closed) {
            px[n] = points.get(0).x;
            py[n] = points.get(0).y;

            px[0] = px[n - 1];
            py[0] = py[n - 1];
            px[nb - 1] = px[2];
            py[nb - 1] = py[2];
        } else {
            // Open spline: Phantom points are duplicates of first and last points
            px[n] = points.get(points.size() - 1).x;
            py[n] = points.get(points.size() - 1).y;

            px[0] = px[1];
            py[0] = py[1];
            px[nb - 1] = px[n];
            py[nb - 1] = py[n];
        }

        double[] h = new double[nb];
        double[] d = new double[nb];
        d[0] = 0; // Ensure start at 0
        for (int i = 0; i < nb - 1; i++) {
            double dx = px[i + 1] - px[i];
            double dy = py[i + 1] - py[i];
            h[i] = Math.sqrt(dx * dx + dy * dy);
            if (h[i] < 0.1)
                h[i] = 0.1;
            d[i + 1] = d[i] + h[i];
        }

        double[] a = new double[nb];
        double[] c = new double[nb];
        for (int i = 2; i < nb - 1; i++)
            a[i] = 2.0 * h[i - 1] / (h[i] + h[i - 1]);
        for (int i = 1; i < nb - 2; i++)
            c[i] = 2.0 * h[i] / (h[i] + h[i - 1]);

        double[] gx = new double[nb];
        for (int i = 1; i < nb - 1; i++) {
            double den1 = (d[i] - d[i - 1]);
            double den2 = (d[i + 1] - d[i]);
            double den3 = (d[i + 1] - d[i - 1]);
            if (Math.abs(den1) < 0.0001)
                den1 = 0.0001;
            if (Math.abs(den2) < 0.0001)
                den2 = 0.0001;
            if (Math.abs(den3) < 0.0001)
                den3 = 0.0001;

            gx[i] = 6.0 * (((px[i - 1] - px[i]) / den1) - ((px[i] - px[i + 1]) / den2)) / den3;
        }

        double[] gy = new double[nb];
        for (int i = 1; i < nb - 1; i++) {
            double den1 = (d[i] - d[i - 1]);
            double den2 = (d[i + 1] - d[i]);
            double den3 = (d[i + 1] - d[i - 1]);
            if (Math.abs(den1) < 0.0001)
                den1 = 0.0001;
            if (Math.abs(den2) < 0.0001)
                den2 = 0.0001;
            if (Math.abs(den3) < 0.0001)
                den3 = 0.0001;

            gy[i] = 6.0 * (((py[i - 1] - py[i]) / den1) - ((py[i] - py[i + 1]) / den2)) / den3;
        }

        double[] cxx = solveTridiagonal(a, gx, c, nb);
        double[] cyy = solveTridiagonal(a, gy, c, nb);

        List<Point2D.Float> result = new ArrayList<>();
        int end = n;
        for (int i = 1; i < end; i++) {
            double ccx = cxx[i];
            double aax = px[i];
            double ddx = (cxx[i + 1] - cxx[i]) / (3.0 * h[i]);
            double bbx = ((px[i + 1] - px[i]) / h[i]) - (h[i] / 3.0) * (cxx[i + 1] + 2.0 * cxx[i]);

            double ccy = cyy[i];
            double aay = py[i];
            double ddy = (cyy[i + 1] - cyy[i]) / (3.0 * h[i]);
            double bby = ((py[i + 1] - py[i]) / h[i]) - (h[i] / 3.0) * (cyy[i + 1] + 2.0 * cyy[i]);

            int steps = (int) (h[i] * scale);
            if (steps < 1)
                steps = 1;

            for (int t_step = 0; t_step < steps; t_step++) {
                double t = (double) t_step * h[i] / steps;
                double x = aax + bbx * t + ccx * t * t + ddx * t * t * t;
                double y = aay + bby * t + ccy * t * t + ddy * t * t * t;
                result.add(new Point2D.Float((float) x, (float) y));
            }
        }
        result.add(new Point2D.Float((float) px[end], (float) py[end]));

        return result;
    }

    private double[] solveTridiagonal(double[] a, double[] g, double[] c, int nb) {
        double[] u = new double[nb];
        double[] gam = new double[nb];
        double b = 4.0;
        double bet = 4.0;
        u[1] = g[1] / b;
        for (int j = 2; j < nb - 1; j++) {
            gam[j] = c[j - 1] / bet;
            bet = b - a[j] * gam[j];
            u[j] = (g[j] - a[j] * u[j - 1]) / bet;
        }
        for (int j = nb - 2; j >= 1; j--) {
            if (j < nb - 2)
                u[j] -= gam[j + 1] * u[j + 1];
        }
        return u;
    }
}
