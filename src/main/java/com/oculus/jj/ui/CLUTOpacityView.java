package com.oculus.jj.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CLUTOpacityView - 16-bit CLUT curve editor Swing panel.
 * HOROS-20240407準拠: CLUTOpacityView equivalent for Java/Swing VR toolbar.
 * 
 * Left: histogram + curve/color canvas  
 * Right sidebar: add/remove buttons, opacity slider, save/close
 */
public class CLUTOpacityView extends JPanel {

    public static final int CLUT_SIZE = 4096; // HOROS 16-bit equivalent

    /** Curve control points for opacity mapping */
    public static class Curve {
        public float[] controlPointsX;
        public float[] controlPointsY;
        public Color color = Color.WHITE;

        public Curve(float[] x, float[] y) {
            this.controlPointsX = x.clone();
            this.controlPointsY = y.clone();
        }
    }

    /** Per-HU RGB value entry */
    public static class ColorPoint {
        public float huValue;
        public int[] rgb = new int[3];

        public ColorPoint(float hu, int r, int g, int b) {
            this.huValue = hu;
            this.rgb[0] = Math.max(0, Math.min(65535, r));
            this.rgb[1] = Math.max(0, Math.min(65535, g));
            this.rgb[2] = Math.max(0, Math.min(65535, b));
        }

        public Color toSwingColor() { return new Color(rgb[0], rgb[1], rgb[2]); }
    }

    private List<Curve> curves = new ArrayList<>();
    private List<ColorPoint> colorPoints = new ArrayList<>();
    private int selectedCurveIndex = 0;

    private CurveEditorCanvas curveCanvas;
    private ColorEditorCanvas colorCanvas;
    private JSlider opacitySlider;
    private JLabel opacityLabel;

    public CLUTOpacityView() {
        setLayout(new BorderLayout(6, 6));
        setBackground(Color.DARK_GRAY);

        // Left side: two canvases stacked vertically
        JPanel leftPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        curveCanvas = new CurveEditorCanvas();
        colorCanvas = new ColorEditorCanvas();
        leftPanel.add(curveCanvas);
        leftPanel.add(colorCanvas);

        // Right sidebar
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        rightPanel.setBackground(Color.LIGHT_GRAY);

        Component sep1 = Box.createVerticalStrut(6);
        Component sep2 = Box.createVerticalStrut(10);

        JButton addBtn = new JButton("Add Curve");
        addBtn.setAlignmentX(CENTER_ALIGNMENT);
        addBtn.addActionListener(e -> addNewCurve());

        JButton rmBtn = new JButton("Remove Curve");  
        rmBtn.setAlignmentX(CENTER_ALIGNMENT);
        rmBtn.addActionListener(e -> removeSelectedCurve());

        opacitySlider = new JSlider(0, 100, 50);
        opacityLabel = new JLabel("Opacity: 50%");
        opacitySlider.addChangeListener(ev -> {
            int v = opacitySlider.getValue();
            opacityLabel.setText("Opacity: " + v + "%");
            repaint();
        });

        JButton saveBtn = new JButton("Save CLUT");  
        saveBtn.setAlignmentX(CENTER_ALIGNMENT);
        saveBtn.addActionListener(e -> JOptionPane.showMessageDialog(this, "CLUT saved (placeholder).", "Save", JOptionPane.INFORMATION_MESSAGE));

        JButton closeBtn = new JButton("Close");
        closeBtn.setAlignmentX(CENTER_ALIGNMENT);
        closeBtn.addActionListener(e -> { Window anc = SwingUtilities.getWindowAncestor(CLUTOpacityView.this); if (anc != null) anc.dispose(); });

        rightPanel.add(addBtn);
        rightPanel.add(sep1);
        rightPanel.add(rmBtn);  
        rightPanel.add(sep2);
        rightPanel.add(opacitySlider);
        rightPanel.add(opacityLabel);
        rightPanel.add(sep2);
        rightPanel.add(saveBtn);
        rightPanel.add(closeBtn);

        add(leftPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // Initialize with default curve (as HOROS does)
        addNewCurve();  
    }

    public void addNewCurve() {
        float[] xPts = { 12f / CLUT_SIZE, 202f / CLUT_SIZE, 404f / CLUT_SIZE, 549f / CLUT_SIZE };  
        float[] yPts = { 0.0f, (float) Math.sqrt(0.027), (float) Math.sqrt(0.133), (float) Math.sqrt(0.682) };
        curves.add(new Curve(xPts, yPts));
        selectedCurveIndex = curves.size() - 1;
        curveCanvas.repaint();  
    }

    public void removeSelectedCurve() {
        if (curves.size() > 1 && selectedCurveIndex >= 0 && selectedCurveIndex < curves.size()) {  
            curves.remove(selectedCurveIndex);
            if (selectedCurveIndex >= curves.size()) selectedCurveIndex = curves.size() - 1;
            curveCanvas.repaint();
        }
    }

    // HOROS準拠: convertCurvesForPlist equivalent  
    public List<float[]> getCurvesAsArrays() {
        List<float[]> result = new ArrayList<>();
        for (Curve c : curves) {
            int n = Math.min(c.controlPointsX.length, c.controlPointsY.length);
            float[] xa = new float[n];
            float[] ya = new float[n];
            System.arraycopy(c.controlPointsX, 0, xa, 0, n);
            System.arraycopy(c.controlPointsY, 0, ya, 0, n);  
            result.add(xa);
            result.add(ya);
        }
        return result;
    }

    // HOROS準拠: convertPointColorsForPlist equivalent - int[CLUT_SIZE][3] RGB (16bit values)
    public int[][] getPointColorsAsTable() {  
        if (colorPoints.isEmpty()) return new int[CLUT_SIZE][3];
        int[][] table = new int[CLUT_SIZE][3];
        for (int i = 0; i < CLUT_SIZE; i++) {
            float t = (float) i / (CLUT_SIZE - 1);
            ColorPoint cp = interpolateColor(t);
            if (cp != null) table[i] = new int[]{cp.rgb[0], cp.rgb[1], cp.rgb[2]};  
        }
        return table;
    }

    private ColorPoint interpolateColor(float t) {
        if (colorPoints.isEmpty()) return colorPoints.get(0);
        int n = colorPoints.size();
        float hu = -1.0f + t * 2.0f;
        ColorPoint prev = null, next = null;
        for (int i = 0; i < n - 1; i++) {  
            if (hu >= colorPoints.get(i).huValue && hu <= colorPoints.get((i + 1) % n).huValue) {
                prev = colorPoints.get(i); next = colorPoints.get(i + 1); break;  
            }
        }
        return interpolateColorPoint(prev, next, (hu - prev.huValue) / (next.huValue - prev.huValue));
    }

    private ColorPoint interpolateColorPoint(ColorPoint a, ColorPoint b, float factor) {
        if (a == null || b == null) return new ColorPoint(0f, 32768, 32768, 32768);  
        int r = Math.round(a.rgb[0] + (b.rgb[0] - a.rgb[0]) * factor);
        int g = Math.round(a.rgb[1] + (b.rgb[1] - a.rgb[1]) * factor);
        int bl = Math.round(a.rgb[2] + (b.rgb[2] - a.rgb[2]) * factor);
        return new ColorPoint(0f, r, g, bl);
    }

    // ---- Inner Canvas Classes matching HOROS architecture ---  

    class CurveEditorCanvas extends JPanel {  
        private static final int PAD = 10;

        public CurveEditorCanvas() {  
            setPreferredSize(new Dimension(549, 243));
            setBackground(Color.BLACK);
            
            addMouseListener(new MouseAdapter() {  
                @Override
                public void mouseClicked(MouseEvent e) {  
                    if (SwingUtilities.isLeftMouseButton(e) && curves.size() > 0) {
                        Curve cur = curves.get(selectedCurveIndex);  
                        int n = Math.min(cur.controlPointsX.length, cur.controlPointsY.length);
                        float t = normalizeXToT(e.getX());
                        
                        // Add new control point at mouse position
                        int insertIdx = n;
                        for (int i = 0; i < n; i++) { if (t >= cur.controlPointsX[i]) insertIdx = i + 1; }

                        float[] newX = new float[n + 1];
                        float[] newY = new float[n + 1];
                        
                        System.arraycopy(cur.controlPointsX, 0, newX, 0, insertIdx);  
                        System.arraycopy(cur.controlPointsY, 0, newY, 0, insertIdx);
                        
                        float insertedY = (float) Math.pow(t, 1.5);  
                        newX[insertIdx] = t;
                        newY[insertIdx] = insertedY;
                        
                        System.arraycopy(cur.controlPointsX, insertIdx, newX, insertIdx + 1, n - insertIdx);
                        System.arraycopy(cur.controlPointsY, insertIdx, newY, insertIdx + 1, n - insertIdx);

                        cur.controlPointsX = newX;
                        cur.controlPointsY = newY;
                        repaint();  
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) { /* track drag start */ }
            });

            addMouseMotionListener(new MouseMotionAdapter() {  
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (curves.isEmpty()) return;
                    Curve cur = curves.get(selectedCurveIndex);  
                    int n = Math.min(cur.controlPointsX.length, cur.controlPointsY.length);
                    float t = normalizeXToT(e.getX());

                    // Find nearest control point and drag its Y  
                    double bestDist = Double.MAX_VALUE;
                    int bestIdx = -1;
                    for (int i = 0; i < n; i++) {
                        double dx = Math.abs(cur.controlPointsX[i] - t);
                        if (dx < bestDist) { bestDist = dx; bestIdx = i; }  
                    }

                    if (bestIdx >= 0 && bestDist < 0.1f /* within snap radius */) {  
                        cur.controlPointsY[bestIdx] = Math.max(0, Math.min(1, normalizeY(e.getY())));
                        repaint();
                    }
                }
            });
        }

        private float normalizeXToT(int x) { return (x - PAD) / (float)(getWidth() - 2 * PAD); }  
        private float normalizeY(int y) { return (getHeight() - PAD - y) / (float)(getHeight() - 2 * PAD); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);  
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (curves.isEmpty()) return;

            for (int ci = 0; ci < curves.size(); ci++) {  
                Curve cur = curves.get(ci);
                int n = Math.min(cur.controlPointsX.length, cur.controlPointsY.length);
                
                g2.setColor(cur.color != null ? cur.color : Color.WHITE);  
                if (ci == selectedCurveIndex) g2.setStroke(new BasicStroke(3));  
                else g2.setStroke(new BasicStroke(1.5f));

                for (int i = 0; i < n - 1; i++) {
                    float x1 = PAD + cur.controlPointsX[i] * (getWidth() - 2 * PAD);  
                    float y1 = getHeight() - PAD - cur.controlPointsY[i] * (getHeight() - 2 * PAD);
                    float x2 = PAD + cur.controlPointsX[i + 1] * (getWidth() - 2 * PAD);  
                    float y2 = getHeight() - PAD - cur.controlPointsY[i + 1] * (getHeight() - 2 * PAD);
                    g2.drawLine((int) x1, (int) y1, (int) x2, (int) y2);  
                }

                for (int i = 0; i < n; i++) {  
                    float cx = PAD + cur.controlPointsX[i] * (getWidth() - 2 * PAD);
                    float cy = getHeight() - PAD - cur.controlPointsY[i] * (getHeight() - 2 * PAD);  
                    int rad = 4;
                    g2.setColor(Color.WHITE);
                    g2.fillOval((int) cx - rad, (int) cy - rad, 2 * rad, 2 * rad);
                    if (ci == selectedCurveIndex && i < n) {  
                        g2.setColor(Color.YELLOW);
                        g2.drawOval((int) cx - rad, (int) cy - rad, 2 * rad, 2 * rad);
                    }
                }

                // Selected curve highlight border  
                if (ci == selectedCurveIndex) {  
                    g2.setColor(new Color(0, 150, 255));
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);  
                }
            }

            // Histogram area at bottom  
            if (histogram != null && histogram.length > 0) {  
                int hSize = histogram.length;
                float barW = (float) getWidth() / hSize;
                for (int i = 0; i < hSize; i++) {  
                    int h = Math.min(getHeight() - PAD, (int)(histogram[i] * (getHeight() - PAD)));
                    g2.setColor(new Color(80, 160, 80, 200));  
                    g2.fillRect((int) (i * barW), getHeight() - h, (int) barW + 1, h);
                }
            }

            // Axis labels
            g2.setColor(Color.GRAY);
            Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 10);  
            g2.setFont(small);
            g2.drawString("HU", 5, getHeight() - PAD - 30);  
            g2.drawString("Opacity", 180, 15);
        }

        private int[] histogram;
        public void setHistogram(int[] hist) { this.histogram = hist; repaint(); }
    }

    class ColorEditorCanvas extends JPanel {  
        @Override
        protected void paintComponent(Graphics g) {  
            super.paintComponent(g);

            if (colorPoints.isEmpty()) return;

            int n = Math.min(colorPoints.size(), 128);
            for (int i = 0; i < n; i++) {
                ColorPoint cp = colorPoints.get(i);
                int x = getWidth() * i / Math.max(1, n);  
                int bw = getWidth() / Math.max(2, n);

                // Top: R channel bar  
                g.setColor(cp.toSwingColor().brighter());
                g.fillRect(x, 0, bw, getHeight() / 3);

                // Middle: G (simplified representation)
                g.setColor(new Color(0, cp.rgb[1] >> 8 | cp.rgb[0], 0));
                g.fillRect(x, getHeight() / 3, bw, getHeight() / 3);

                // Bottom: B (simplified)  
                g.setColor(new Color(0, 0, cp.rgb[2] >> 8 | cp.rgb[1]));  
                g.fillRect(x, getHeight() * 2/3, bw, getHeight() / 3);
            }

            Graphics2D g2 = (Graphics2D) g;  
            g2.setColor(new Color(100, 100, 100));
            g2.drawLine(0, getHeight()/3 - 1, getWidth(), getHeight()/3 - 1);
            g2.drawLine(0, getHeight()*2/3 - 1, getWidth(), getHeight()*2/3 - 1);

            Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 10);  
            g2.setFont(small);
            g2.setColor(Color.WHITE);
            g2.drawString("R", 5, getHeight() / 6 + 4);
            g2.drawString("G", getWidth()/2 - 5, getHeight()*2/3 + 4);  
            g2.drawString("B", getWidth() - 10, getHeight()*5/6 + 4);  
        }

        @Override
        public Dimension getPreferredSize() { return new Dimension(549, 80); }
    }
}
