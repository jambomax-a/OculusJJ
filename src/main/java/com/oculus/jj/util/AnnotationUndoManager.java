package com.oculus.jj.util;
import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.awt.geom.Point2D;
import com.oculus.jj.model.ROI;
import com.oculus.jj.ui.DCMView;
/**
 * AnnotationUndoManager - ROI/AnnotationのUndo/Redo管理
 */
public class AnnotationUndoManager {
    private static class UndoRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        
        final int roiId;
        final boolean wasDeleted;
        final DCMView.ToolMode type;
        final float thickness;
        final boolean fill;
        final float opacity;
        final int colorR, colorG, colorB;
        final List<Point2D.Float> points;
        
        UndoRecord(int roiId, boolean wasDeleted, DCMView.ToolMode type,
                   float thickness, boolean fill, float opacity,
                   Color color, List<Point2D.Float> pts) {
            this.roiId = roiId;
            this.wasDeleted = wasDeleted;
            this.type = type;
            this.thickness = thickness;
            this.fill = fill;
            this.opacity = opacity;
            if (color != null) {
                colorR = color.getRed();
                colorG = color.getGreen();
                colorB = color.getBlue();
            } else {
                colorR = 255; colorG = 255; colorB = 127;
            }
            this.points = (pts != null) ? new ArrayList<>(pts) : new ArrayList<>();
        }
        Color getColor() {
            return new Color(colorR, colorG, colorB);
        }
    }
    private final List<UndoRecord> undoStack = new ArrayList<>();
    private int currentIndex = -1;
    
    private DCMView targetView;
    public AnnotationUndoManager() {}
    
    public AnnotationUndoManager(DCMView view) {
        this.targetView = view;
    }
    public void pushSnapshot(List<ROI> roiList, List<ROI> deletedRois) {
        while (undoStack.size() > currentIndex + 1) {
            undoStack.remove(undoStack.size() - 1);
        }
        for (ROI roi : roiList) {
            if (roi.getPoints() != null && !roi.getPoints().isEmpty()) {
                UndoRecord record = new UndoRecord(
                    roi.getId(), false,
                    roi.getType(), roi.getThickness(), roi.isFill(), roi.getOpacity(),
                    roi.getColor(), roi.getPoints());
                undoStack.add(record);
            }
        }
        for (ROI roi : deletedRois) {
            if (roi.getPoints() != null && !roi.getPoints().isEmpty()) {
                UndoRecord record = new UndoRecord(
                    roi.getId(), true,
                    roi.getType(), roi.getThickness(), roi.isFill(), roi.getOpacity(),
                    roi.getColor(), roi.getPoints());
                undoStack.add(record);
            }
        }
        currentIndex = undoStack.size() - 1;
    }
    public ROI undo() {
        if (currentIndex < 0 || currentIndex >= undoStack.size()) return null;
        UndoRecord record = undoStack.get(currentIndex);
        currentIndex--;
        return restoreFrom(record);
    }
    public ROI redo() {
        if (currentIndex < 0 || currentIndex >= undoStack.size()) return null;
        currentIndex++;
        UndoRecord record = undoStack.get(currentIndex);
        return restoreFrom(record);
    }
    public boolean canUndo() {
        return currentIndex >= 0 && currentIndex < undoStack.size();
    }
    public boolean canRedo() {
        return currentIndex + 1 < undoStack.size();
    }
    public void clear() {
        undoStack.clear();
        currentIndex = -1;
    }
    public void setTarget(DCMView view) {
        this.targetView = view;
    }
    
    public DCMView getTarget() {
        return targetView;
    }
    private ROI restoreFrom(UndoRecord record) {
        if (targetView == null) return null;
        ROI roi = new ROI();
        roi.setType(record.type);
        roi.setThickness(record.thickness);
        roi.setFill(record.fill);
        roi.setOpacity(record.opacity);
        roi.setColor(record.getColor());
        if (record.points != null) {
            roi.setPoints(new ArrayList<>(record.points));
        }
        if (record.wasDeleted && targetView.curRoiList != null) {
            targetView.curRoiList.add(roi);
        }
        return roi;
    }
}
