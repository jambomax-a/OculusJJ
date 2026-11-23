package com.oculus.jj.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oculus.jj.ui.DCMView;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class VeterinaryCardiacROITest {

    @Test
    void vhsRequiresSixPointsAndComputesOrthogonalShortAxis() {
        ROI roi = new ROI(DCMView.ToolMode.tVHS, 0.5f, 0.5f, null);
        roi.addPoint(new Point2D.Float(100, 50)); // T4 front
        roi.addPoint(new Point2D.Float(250, 50)); // Spine axis
        roi.addPoint(new Point2D.Float(120, 100)); // Carina
        roi.addPoint(new Point2D.Float(120, 200)); // Apex (vertical long axis)
        roi.addPoint(new Point2D.Float(90, 150)); // Short axis start
        assertFalse(roi.isValid());

        roi.addPoint(new Point2D.Float(170, 180)); // Diagonal click -> projected horizontally
        assertTrue(roi.isValid());

        Point2D.Float sEnd = roi.computeVhsShortAxisEnd();
        assertNotNull(sEnd);
        assertEquals(170.0f, sEnd.x, 1e-3f);
        assertEquals(150.0f, sEnd.y, 1e-3f);
        assertTrue(roi.hitTest(new Point2D.Float(130, 150), 5.0));

        BufferedImage img = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        roi.draw(g2d, false, false);
        g2d.dispose();
    }

    @Test
    void vlasRequiresFourPoints() {
        ROI roi = new ROI(DCMView.ToolMode.tVLAS, 0.5f, 0.5f, null);
        roi.addPoint(new Point2D.Float(100, 50));
        roi.addPoint(new Point2D.Float(220, 50));
        roi.addPoint(new Point2D.Float(120, 100));
        assertFalse(roi.isValid());

        roi.addPoint(new Point2D.Float(160, 130));
        assertTrue(roi.isValid());
        assertTrue(roi.hitTest(new Point2D.Float(140, 115), 5.0));
    }

    @Test
    void mvlasRequiresFivePointsAndProjectsPerpendicularToVlasBaseline() {
        ROI roi = new ROI(DCMView.ToolMode.tMVLAS, 0.5f, 0.5f, null);
        roi.addPoint(new Point2D.Float(100, 50));
        roi.addPoint(new Point2D.Float(220, 50));
        roi.addPoint(new Point2D.Float(100, 100)); // Carina
        roi.addPoint(new Point2D.Float(200, 100)); // VLAS end (horizontal baseline)
        assertFalse(roi.isValid());

        roi.addPoint(new Point2D.Float(150, 60)); // LA dorsal dome top
        assertTrue(roi.isValid());

        Point2D.Float foot = roi.projectPointOnLine(
                roi.getPoints().get(4), roi.getPoints().get(2), roi.getPoints().get(3));
        assertEquals(150.0f, foot.x, 1e-3f);
        assertEquals(100.0f, foot.y, 1e-3f);
        assertTrue(roi.hitTest(new Point2D.Float(150, 80), 5.0));
    }
}
