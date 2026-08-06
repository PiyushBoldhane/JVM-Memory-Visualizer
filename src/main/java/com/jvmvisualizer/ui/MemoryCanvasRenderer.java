package com.jvmvisualizer.ui;

import com.jvmvisualizer.model.HeapObject;
import com.jvmvisualizer.model.HeapRef;
import com.jvmvisualizer.model.MemoryState;
import com.jvmvisualizer.model.StackFrame;
import com.jvmvisualizer.model.StringPoolRef;
import com.jvmvisualizer.model.Variable;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws one {@link MemoryState} snapshot: Stack (FR-3), Heap (FR-4),
 * reference arrows between them (FR-5), the Static Area (FR-9) and the
 * String Pool (FR-8). Pure JavaFX Canvas - no external diagramming tool -
 * so every step can be redrawn instantly as the Timeline Control (FR-11)
 * moves back and forth.
 */
public class MemoryCanvasRenderer {

    private static final double STACK_X = 20;
    private static final double STACK_W = 230;
    private static final double HEAP_X = 300;
    private static final double HEAP_W = 260;
    private static final double ROW_H = 20;
    private static final double HEADER_H = 26;
    private static final double TOP_Y = 40;
    private static final double PAD = 8;

    private static final Color BG = Color.web("#1e1e2e");
    private static final Color TITLE = Color.web("#cdd6f4");
    private static final Color STACK_BORDER = Color.web("#89b4fa");
    private static final Color STACK_FILL = Color.web("#1e2233");
    private static final Color HEAP_BORDER = Color.web("#a6e3a1");
    private static final Color HEAP_FILL = Color.web("#1e2b1f");
    private static final Color GARBAGE_BORDER = Color.web("#f38ba8");
    private static final Color GARBAGE_FILL = Color.web("#3a1f26");
    private static final Color STATIC_BORDER = Color.web("#fab387");
    private static final Color STATIC_FILL = Color.web("#2b241d");
    private static final Color POOL_BORDER = Color.web("#94e2d5");
    private static final Color POOL_FILL = Color.web("#1c2b2a");
    private static final Color TEXT = Color.web("#e6e6f0");
    private static final Color ARROW = Color.web("#f9e2af");

    /** Recomputes the required canvas size, resizes it, then draws. Call this on every step change. */
    public void render(Canvas canvas, MemoryState state) {
        Map<String, Point2D> varAnchors = new LinkedHashMap<>();
        Map<Integer, Box> heapBoxes = new LinkedHashMap<>();
        Map<String, Point2D> poolAnchors = new LinkedHashMap<>();

        double stackBottom = layoutStack(state, varAnchors);
        double heapBottom = layoutHeap(state, heapBoxes);
        double columnsBottom = Math.max(stackBottom, heapBottom);

        double staticTop = columnsBottom + 30;
        double staticBottom = state.getStaticArea().isEmpty() ? staticTop : staticTop + HEADER_H + PAD * 2;

        double poolTop = staticBottom + 20;
        double poolBottom = state.getStringPool().isEmpty() ? poolTop : poolTop + HEADER_H + PAD * 2;

        double width = STACK_X + STACK_W + 40 + HEAP_X + HEAP_W;
        double height = Math.max(poolBottom + 30, 300);
        canvas.setWidth(width);
        canvas.setHeight(height);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(BG);
        gc.fillRect(0, 0, width, height);

        drawStack(gc, state, varAnchors);
        drawHeap(gc, state, heapBoxes);
        drawStaticArea(gc, state, staticTop);
        drawStringPool(gc, state, poolTop, poolAnchors);
        drawArrows(gc, state, varAnchors, heapBoxes, poolAnchors);
    }

    private record Box(double x, double y, double w, double h) {
        Point2D leftAnchor() {
            return new Point2D(x, y + h / 2);
        }
    }

    // -- layout (size only, no drawing) --------------------------------

    private double layoutStack(MemoryState state, Map<String, Point2D> varAnchors) {
        double y = TOP_Y;
        int frameIndex = 0;
        for (StackFrame frame : state.getStack()) {
            double h = HEADER_H + Math.max(1, frame.getLocals().size()) * ROW_H + PAD;
            double rowY = y + HEADER_H + PAD / 2.0;
            for (Variable v : frame.getLocals()) {
                varAnchors.put(frameIndex + ":" + v.getName(), new Point2D(STACK_X + STACK_W, rowY + ROW_H / 2.0));
                rowY += ROW_H;
            }
            y += h + 10;
            frameIndex++;
        }
        return y;
    }

    private double layoutHeap(MemoryState state, Map<Integer, Box> heapBoxes) {
        double y = TOP_Y;
        for (HeapObject obj : state.getHeap().values()) {
            double h = HEADER_H + Math.max(1, obj.getFields().size()) * ROW_H + PAD;
            heapBoxes.put(obj.getId(), new Box(HEAP_X, y, HEAP_W, h));
            y += h + 10;
        }
        return y;
    }

    // -- drawing ---------------------------------------------------------

    private void drawStack(GraphicsContext gc, MemoryState state, Map<String, Point2D> varAnchors) {
        gc.setFill(TITLE);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        gc.fillText("STACK", STACK_X, 24);

        double y = TOP_Y;
        int frameIndex = 0;
        for (StackFrame frame : state.getStack()) {
            double h = HEADER_H + Math.max(1, frame.getLocals().size()) * ROW_H + PAD;
            boolean top = frameIndex == 0;
            gc.setFill(STACK_FILL);
            gc.fillRoundRect(STACK_X, y, STACK_W, h, 8, 8);
            gc.setStroke(STACK_BORDER);
            gc.setLineWidth(top ? 2.5 : 1.2);
            gc.strokeRoundRect(STACK_X, y, STACK_W, h, 8, 8);

            gc.setFill(STACK_BORDER);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
            gc.fillText(frame.getMethodName() + "()" + (top ? "  ◀ top of stack" : ""), STACK_X + PAD, y + 17);

            double rowY = y + HEADER_H + PAD / 2.0;
            gc.setFont(Font.font("Consolas", 12));
            for (Variable v : frame.getLocals()) {
                gc.setFill(TEXT);
                String valueText = valueLabel(v.getValue());
                gc.fillText(v.getName() + " : " + v.getType() + " = " + valueText, STACK_X + PAD, rowY + ROW_H - 6);
                rowY += ROW_H;
            }
            y += h + 10;
            frameIndex++;
        }
    }

    private void drawHeap(GraphicsContext gc, MemoryState state, Map<Integer, Box> heapBoxes) {
        gc.setFill(TITLE);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        gc.fillText("HEAP", HEAP_X, 24);

        for (HeapObject obj : state.getHeap().values()) {
            Box box = heapBoxes.get(obj.getId());
            boolean garbage = obj.isMarkedForCollection();
            gc.setFill(garbage ? GARBAGE_FILL : HEAP_FILL);
            gc.fillRoundRect(box.x(), box.y(), box.w(), box.h(), 8, 8);
            gc.setStroke(garbage ? GARBAGE_BORDER : HEAP_BORDER);
            gc.setLineWidth(1.5);
            if (garbage) {
                gc.setLineDashes(6, 4);
            }
            gc.strokeRoundRect(box.x(), box.y(), box.w(), box.h(), 8, 8);
            gc.setLineDashes(0);

            gc.setFill(garbage ? GARBAGE_BORDER : HEAP_BORDER);
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
            String header = obj.getClassName() + " @ " + obj.simulatedAddress() + (garbage ? "  ✕ GARBAGE" : "");
            gc.fillText(header, box.x() + PAD, box.y() + 17);

            gc.setFont(Font.font("Consolas", 12));
            double rowY = box.y() + HEADER_H + PAD / 2.0;
            for (Map.Entry<String, Object> field : obj.getFields().entrySet()) {
                gc.setFill(TEXT);
                gc.fillText(field.getKey() + " = " + valueLabel(field.getValue()), box.x() + PAD, rowY + ROW_H - 6);
                rowY += ROW_H;
            }
            if (obj.getFields().isEmpty()) {
                gc.setFill(Color.web("#8a8aa3"));
                gc.fillText("(no fields set)", box.x() + PAD, rowY + ROW_H - 6);
            }
        }
    }

    private double drawStaticArea(GraphicsContext gc, MemoryState state, double y) {
        if (state.getStaticArea().isEmpty()) {
            return y;
        }
        double width = STACK_X + STACK_W + 40 + HEAP_X + HEAP_W - STACK_X;
        double h = HEADER_H + PAD * 2;
        gc.setFill(STATIC_FILL);
        gc.fillRoundRect(STACK_X, y, width, h, 8, 8);
        gc.setStroke(STATIC_BORDER);
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(STACK_X, y, width, h, 8, 8);

        gc.setFill(STATIC_BORDER);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        gc.fillText("STATIC AREA", STACK_X + PAD, y + 16);

        gc.setFont(Font.font("Consolas", 12));
        gc.setFill(TEXT);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : state.getStaticArea().entrySet()) {
            if (sb.length() > 0) sb.append("      ");
            sb.append(e.getKey()).append(" = ").append(valueLabel(e.getValue()));
        }
        gc.fillText(sb.toString(), STACK_X + PAD, y + HEADER_H + PAD);
        return y + h;
    }

    private void drawStringPool(GraphicsContext gc, MemoryState state, double y, Map<String, Point2D> poolAnchors) {
        if (state.getStringPool().isEmpty()) {
            return;
        }
        double width = STACK_X + STACK_W + 40 + HEAP_X + HEAP_W - STACK_X;
        double h = HEADER_H + PAD * 2;
        gc.setFill(POOL_FILL);
        gc.fillRoundRect(STACK_X, y, width, h, 8, 8);
        gc.setStroke(POOL_BORDER);
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(STACK_X, y, width, h, 8, 8);

        gc.setFill(POOL_BORDER);
        gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        gc.fillText("STRING POOL", STACK_X + PAD, y + 16);

        gc.setFont(Font.font("Consolas", 12));
        double x = STACK_X + 110;
        for (String literal : state.getStringPool()) {
            String chip = "\"" + literal + "\"";
            double chipW = 14 + chip.length() * 7.0;
            gc.setFill(Color.web("#12201f"));
            gc.fillRoundRect(x, y + PAD, chipW, ROW_H, 6, 6);
            gc.setStroke(POOL_BORDER);
            gc.setLineWidth(1);
            gc.strokeRoundRect(x, y + PAD, chipW, ROW_H, 6, 6);
            gc.setFill(TEXT);
            gc.fillText(chip, x + 7, y + PAD + ROW_H - 6);
            poolAnchors.put(literal, new Point2D(x + chipW / 2.0, y + PAD));
            x += chipW + 12;
        }
    }

    private void drawArrows(GraphicsContext gc, MemoryState state, Map<String, Point2D> varAnchors,
                             Map<Integer, Box> heapBoxes, Map<String, Point2D> poolAnchors) {
        gc.setStroke(ARROW);
        gc.setFill(ARROW);
        gc.setLineWidth(1.6);
        gc.setLineDashes(0);

        int frameIndex = 0;
        for (StackFrame frame : state.getStack()) {
            for (Variable v : frame.getLocals()) {
                Point2D from = varAnchors.get(frameIndex + ":" + v.getName());
                drawReferenceArrow(gc, from, v.getValue(), heapBoxes, poolAnchors);
            }
            frameIndex++;
        }

        // Heap object fields that reference other heap objects.
        for (HeapObject obj : state.getHeap().values()) {
            Box fromBox = heapBoxes.get(obj.getId());
            double rowY = fromBox.y() + HEADER_H + PAD / 2.0;
            for (Object fieldValue : obj.getFields().values()) {
                Point2D from = new Point2D(fromBox.x() + fromBox.w(), rowY + ROW_H / 2.0);
                drawReferenceArrow(gc, from, fieldValue, heapBoxes, poolAnchors);
                rowY += ROW_H;
            }
        }
    }

    private void drawReferenceArrow(GraphicsContext gc, Point2D from, Object value,
                                     Map<Integer, Box> heapBoxes, Map<String, Point2D> poolAnchors) {
        if (from == null) {
            return;
        }
        Point2D to = null;
        if (value instanceof HeapRef) {
            Box box = heapBoxes.get(((HeapRef) value).id());
            if (box != null) {
                to = box.leftAnchor();
            }
        } else if (value instanceof StringPoolRef) {
            to = poolAnchors.get(((StringPoolRef) value).value());
        }
        if (to == null) {
            return;
        }
        gc.strokeLine(from.getX(), from.getY(), to.getX(), to.getY());
        drawArrowHead(gc, from, to);
    }

    private void drawArrowHead(GraphicsContext gc, Point2D from, Point2D to) {
        double angle = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
        double size = 7;
        double x1 = to.getX() - size * Math.cos(angle - Math.PI / 6);
        double y1 = to.getY() - size * Math.sin(angle - Math.PI / 6);
        double x2 = to.getX() - size * Math.cos(angle + Math.PI / 6);
        double y2 = to.getY() - size * Math.sin(angle + Math.PI / 6);
        gc.fillPolygon(new double[]{to.getX(), x1, x2}, new double[]{to.getY(), y1, y2}, 3);
    }

    private String valueLabel(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof HeapRef) {
            return ((HeapRef) value).simulatedAddress();
        }
        if (value instanceof StringPoolRef) {
            return "\"" + ((StringPoolRef) value).value() + "\"";
        }
        return String.valueOf(value);
    }
}
