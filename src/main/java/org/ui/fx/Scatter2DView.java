package org.ui.fx;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.view.LabeledPoint;
import org.view.PointCloud;
import org.view.Point_2D;
import org.view.Point_3D;
import org.view.ViewPoint;
import org.app.api.dto.NeighborView;
import org.app.api.ExplorerUseCases;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 2D scatter rendering with:
 * - Smooth pan (drag)
 * - Zoom around mouse pointer (scroll)
 * - Tooltip on hover
 * - Click selection with highlight
 * - Correct centering: subtract ViewSpace center when rendering
 */
public final class Scatter2DView extends Pane {

    private final ExplorerUseCases vm;
    private final Canvas canvas = new Canvas(800, 800);

    // Modern camera feel
    private double zoom = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;

    private double lastX;
    private double lastY;

    private Consumer<String> onSelection = id -> {};
    private String selectedId;

    // How many neighbors to visualize around the selected point (UI-only)
    private int neighborK = 10;

    // Cached neighbor edges from the last refresh (screen-space)
    private final List<NeighborEdge<String>> neighborEdges = new ArrayList<>();

    // Render cache for hit testing
    private final List<RenderedPoint<String>> rendered = new ArrayList<>();

    private final Tooltip tip = new Tooltip();

    // Stores the last scale used in rendering so zoom math can be correct
    private double currentScale = 1.0;

    // Highlight styling
    private static final Color HIGHLIGHT_COLOR = Color.web("#1e66ff"); // modern blue
    private static final double HIGHLIGHT_DOT_RADIUS = 5.0;
    private static final double HIGHLIGHT_RING_RADIUS = 12.0;
    private static final Font LABEL_FONT = Font.font("System", FontWeight.BOLD, 14);
    private static final Font NEIGHBOR_LABEL_FONT = Font.font("System", FontWeight.BOLD, 11);

    public Scatter2DView(ExplorerUseCases vm) {
        this.vm = vm;
        getChildren().add(canvas);

        widthProperty().addListener((o, a, b) -> canvas.setWidth(b.doubleValue()));
        heightProperty().addListener((o, a, b) -> canvas.setHeight(b.doubleValue()));

        Tooltip.install(canvas, tip);

        setOnMousePressed(e -> {
            lastX = e.getX();
            lastY = e.getY();
        });

        setOnMouseDragged(e -> {
            // Smooth pan with primary drag
            double dx = e.getX() - lastX;
            double dy = e.getY() - lastY;
            offsetX += dx;
            offsetY += dy;
            lastX = e.getX();
            lastY = e.getY();
            refresh();
        });

        setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.10 : 0.90;

            double oldZoom = zoom;
            double newZoom = clamp(zoom * factor, 0.15, 30.0);

            // Mouse position in screen coordinates (inside the canvas)
            double mx = e.getX();
            double my = e.getY();

            // Screen center (without offsets)
            double cx0 = canvas.getWidth() / 2.0;
            double cy0 = canvas.getHeight() / 2.0;

            // Use the last rendering scale
            double s = currentScale;

            // Convert mouse screen position -> world coords (relative to ViewSpace center)
            double worldX = (mx - (cx0 + offsetX)) / (s * oldZoom);
            double worldY = - (my - (cy0 + offsetY)) / (s * oldZoom); // minus due to inverted Y

            // Apply zoom
            zoom = newZoom;

            // Adjust offsets so the same world point stays under the mouse
            offsetX = mx - cx0 - (worldX * s * zoom);
            offsetY = my - cy0 + (worldY * s * zoom);

            refresh();
        });

        setOnMouseMoved(e -> {
            String hit = hitTest(e.getX(), e.getY());
            if (hit != null) {
                tip.setText(String.valueOf(hit));
                if (!tip.isShowing()) {
                    tip.show(canvas, e.getScreenX() + 12, e.getScreenY() + 12);
                } else {
                    tip.setAnchorX(e.getScreenX() + 12);
                    tip.setAnchorY(e.getScreenY() + 12);
                }
            } else {
                tip.hide();
            }
        });

        setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            String hit = hitTest(e.getX(), e.getY());
            if (hit != null) {
                selectedId = hit;
                onSelection.accept(hit);
                refresh();
            }
        });
    }

    public void setOnSelection(Consumer<String> onSelection) {
        this.onSelection = (onSelection == null) ? (id -> {}) : onSelection;
    }

    public void setSelectedId(String id) {
        this.selectedId = id;
        refresh();
    }

    public void refresh() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        g.setFill(Color.BLACK);
        g.setStroke(Color.BLACK);
        g.setLineWidth(1.0);

        PointCloud<String> cloud;
        try {
            cloud = vm.getPointCloud();
        } catch (Exception ex) {
            // Nothing to draw
            rendered.clear();
            return;
        }

        if (cloud.mode().viewDim() != 2) return;

        // Screen center + pan offsets
        double cx = canvas.getWidth() / 2.0 + offsetX;
        double cy = canvas.getHeight() / 2.0 + offsetY;

        // Center point from ViewSpace (may be absent early)
        Point_2D center = vm.getCenter()
                .filter(p -> p.dim() == 2)
                .map(p -> (Point_2D) p)
                .orElse(new Point_2D(0, 0));

        // Compute maxAbs AFTER subtracting center (important!)
        double maxAbs = 1e-9;
        for (LabeledPoint<String> p : cloud.points()) {
            Point_2D pt = (Point_2D) p.point();
            double vx = pt.x() - center.x();
            double vy = pt.y() - center.y();
            maxAbs = Math.max(maxAbs, Math.max(Math.abs(vx), Math.abs(vy)));
        }

        double scale = (Math.min(canvas.getWidth(), canvas.getHeight()) * 0.45) / maxAbs;
        currentScale = scale;

        rendered.clear();

        for (LabeledPoint<String> p : cloud.points()) {
            Point_2D pt = (Point_2D) p.point();

            // Critical: subtract center
            double vx = pt.x() - center.x();
            double vy = pt.y() - center.y();

            double sx = cx + (vx * scale * zoom);
            double sy = cy - (vy * scale * zoom); // invert Y for screen coords

            double r = 3.0;

            boolean isSelected = selectedId != null && selectedId.equals(p.id());
            if (isSelected) {
                // A simple "ring" highlight
                g.strokeOval(sx - 8, sy - 8, 16, 16);
            }

            g.fillOval(sx - r, sy - r, r * 2, r * 2);

            rendered.add(new RenderedPoint<>(p.id(), sx, sy, r));
        }

        // Recompute and draw neighbor lines (under the selection ring, over the points)
        recomputeNeighborCache(center, scale, cx, cy);
        drawNeighborLines(g);
        drawNeighborLabels(g);

        // Draw vector-arithmetic overlay path (if present)
        drawOverlayPath(g, center, scale, cx, cy);

        // Draw selected highlight LAST so it stays on top
        if (selectedId != null) {
            RenderedPoint<String> sel = findRendered(selectedId);
            if (sel != null) {
                drawSelectionOverlay(g, sel);
            }
        }
    }

    private String hitTest(double x, double y) {
        RenderedPoint<String> best = null;
        double bestD2 = Double.POSITIVE_INFINITY;

        for (RenderedPoint<String> rp : rendered) {
            double dx = x - rp.sx;
            double dy = y - rp.sy;
            double d2 = dx * dx + dy * dy;
            double hitR = rp.radius + 5.0;
            if (d2 <= hitR * hitR && d2 < bestD2) {
                best = rp;
                bestD2 = d2;
            }
        }
        return best == null ? null : best.id;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static final class RenderedPoint<T> {
        final T id;
        final double sx;
        final double sy;
        final double radius;

        RenderedPoint(T id, double sx, double sy, double radius) {
            this.id = id;
            this.sx = sx;
            this.sy = sy;
            this.radius = radius;
        }
    }

    /**
     * Sets how many nearest neighbors (K) to visualize.
     */
    public void setNeighborK(int k) {
        this.neighborK = Math.max(0, k);
        refresh();
    }

    /**
     * Resets pan and sets a preferred zoom so centering feels "closer".
     */
    public void resetCamera(double preferredZoom) {
        zoom = clamp(preferredZoom, 0.15, 30.0);
        offsetX = 0.0;
        offsetY = 0.0;
        refresh();
    }

    private RenderedPoint<String> findRendered(String id) {
        for (RenderedPoint<String> rp : rendered) {
            if (rp.id.equals(id)) return rp;
        }
        return null;
    }

    private void drawSelectionOverlay(GraphicsContext g, RenderedPoint<String> rp) {
        double sx = rp.sx;
        double sy = rp.sy;

        // Blue ring
        g.setStroke(HIGHLIGHT_COLOR);
        g.setLineWidth(2.5);
        g.strokeOval(
                sx - HIGHLIGHT_RING_RADIUS,
                sy - HIGHLIGHT_RING_RADIUS,
                HIGHLIGHT_RING_RADIUS * 2,
                HIGHLIGHT_RING_RADIUS * 2
        );

        // Blue filled dot
        g.setFill(HIGHLIGHT_COLOR);
        g.fillOval(
                sx - HIGHLIGHT_DOT_RADIUS,
                sy - HIGHLIGHT_DOT_RADIUS,
                HIGHLIGHT_DOT_RADIUS * 2,
                HIGHLIGHT_DOT_RADIUS * 2
        );

        // Label with white background "pill"
        String text = String.valueOf(rp.id);

        g.setFont(LABEL_FONT);

        // Measure text size accurately (no com.sun.* hacks)
        double paddingX = 8;
        double paddingY = 5;
        double textWidth = measureTextWidth(text, LABEL_FONT);
        double textHeight = measureTextHeight(text, LABEL_FONT);

        double boxX = sx + 14;
        double boxY = sy - (textHeight / 2);

        // White background
        g.setFill(Color.rgb(255, 255, 255, 0.90));
        g.fillRoundRect(boxX, boxY, textWidth + paddingX * 2, textHeight + paddingY * 2, 10, 10);

        // Blue border
        g.setStroke(Color.rgb(30, 102, 255, 0.35));
        g.setLineWidth(1.0);
        g.strokeRoundRect(boxX, boxY, textWidth + paddingX * 2, textHeight + paddingY * 2, 10, 10);

        // Text
        g.setFill(HIGHLIGHT_COLOR);
        g.fillText(text, boxX + paddingX, boxY + paddingY + 14);
    }

/**
 * Rebuilds a small cache of neighbor edges (screen-space) for the currently selected ID.
 * This is UI-only: it uses ExplorerUseCases already-available nearestNeighbors/distance APIs.
 */
private void recomputeNeighborCache(Point_2D center, double scale, double cx, double cy) {
    neighborEdges.clear();
    if (selectedId == null || neighborK <= 0) return;

    java.util.List<NeighborView<String>> neighbors;
    try {
        neighbors = vm.nearestNeighborsDetailed(selectedId, neighborK);
    } catch (Exception ex) {
        return;
    }
    if (neighbors == null || neighbors.isEmpty()) return;

    RenderedPoint<String> sel = findRendered(selectedId);
    if (sel == null) return;

    // Compute distances once so we can normalize thickness.
    double minD = Double.POSITIVE_INFINITY;
    double maxD = Double.NEGATIVE_INFINITY;
    List<Double> dists = new ArrayList<>(neighbors.size());

    for (NeighborView<String> nb : neighbors) {
        double d = nb.distance();
        dists.add(d);
        if (Double.isFinite(d)) {
            minD = Math.min(minD, d);
            maxD = Math.max(maxD, d);
        }
    }

    // If distances are weird/identical, fall back to a safe range.
    if (!Double.isFinite(minD) || !Double.isFinite(maxD) || Math.abs(maxD - minD) < 1e-12) {
        minD = 0.0;
        maxD = 1.0;
    }

    for (int i = 0; i < neighbors.size(); i++) {
        String nb = neighbors.get(i).id();
        RenderedPoint<String> rp = findRendered(nb);
        if (rp == null) continue;

        double d = dists.get(i);
        // Convert distance -> closeness in [0,1]. Closer means thicker.
        double t = 1.0 - clamp((d - minD) / (maxD - minD), 0.0, 1.0);

        neighborEdges.add(new NeighborEdge<>(sel, rp, t));
    }
}

/**
 * Draws lines from the selected point to its K nearest neighbors.
 * Thickness encodes closeness (already normalized into [0,1]).
 */
private void drawNeighborLines(GraphicsContext g) {
    if (neighborEdges.isEmpty()) return;

    // Draw a soft "glow" pass first (makes it readable on dense clouds).
    g.setLineDashes(null);
    for (NeighborEdge<String> e : neighborEdges) {
        double w = 2.0 + 6.0 * e.closeness;  // 2..8
        g.setStroke(Color.rgb(30, 102, 255, 0.18));
        g.setLineWidth(w + 4.0);
        g.strokeLine(e.a.sx, e.a.sy, e.b.sx, e.b.sy);
    }

    // Then the crisp pass.
    for (NeighborEdge<String> e : neighborEdges) {
        double w = 1.5 + 5.0 * e.closeness; // 1.5..6.5
        g.setStroke(Color.rgb(30, 102, 255, 0.55));
        g.setLineWidth(w);
        g.strokeLine(e.a.sx, e.a.sy, e.b.sx, e.b.sy);

        // Subtle ring on neighbor
        g.setStroke(Color.rgb(30, 102, 255, 0.45));
        g.setLineWidth(1.25);
        g.strokeOval(e.b.sx - 10, e.b.sy - 10, 20, 20);
    }

    g.setLineDashes(null);
}

/**
 * Draws the vector arithmetic "overlay path" (A -> B -> C -> ...).
 * This is a pure render function: it uses the same pan/zoom/scale transform as points.
 */
private void drawOverlayPath(GraphicsContext g, Point_2D center, double scale, double cx, double cy) {
    List<ViewPoint> path;
    try {
        path = vm.getOverlayPath();
    } catch (Exception ex) {
        return;
    }
    if (path == null || path.size() < 2) return;

    // Build screen-space polyline, skipping non-2D points.
    List<Double> xs = new ArrayList<>();
    List<Double> ys = new ArrayList<>();

    for (ViewPoint vp : path) {
        if (vp == null) continue;

        // Support both 2D and 3D overlay points.
        // If a 3D path is provided while in 2D mode (or vice-versa), we still draw using x/y.
        final double px;
        final double py;
        if (vp.dim() == 2) {
            Point_2D pt = (Point_2D) vp;
            px = pt.x();
            py = pt.y();
        } else if (vp.dim() == 3) {
            Point_3D pt = (Point_3D) vp;
            px = pt.x();
            py = pt.y();
        } else {
            continue;
        }

        double vx = px - center.x();
        double vy = py - center.y();

        double sx = cx + (vx * scale * zoom);
        double sy = cy - (vy * scale * zoom);

        xs.add(sx);
        ys.add(sy);
    }

    if (xs.size() < 2) return;

    // 1) Glow underlay
    g.setLineDashes(null);
    g.setStroke(Color.rgb(255, 138, 0, 0.22));
    g.setLineWidth(10.0);
    for (int i = 0; i < xs.size() - 1; i++) {
        g.strokeLine(xs.get(i), ys.get(i), xs.get(i + 1), ys.get(i + 1));
    }

    // 2) Crisp dashed line on top
    g.setStroke(Color.web("#ff8a00"));
    g.setLineWidth(3.5);
    g.setLineDashes(12.0, 9.0);
    for (int i = 0; i < xs.size() - 1; i++) {
        g.strokeLine(xs.get(i), ys.get(i), xs.get(i + 1), ys.get(i + 1));
    }
    g.setLineDashes(null);

    // 3) Waypoint dots
    g.setFill(Color.web("#ff8a00"));
    for (int i = 0; i < xs.size(); i++) {
        double r = 4.5;
        g.fillOval(xs.get(i) - r, ys.get(i) - r, r * 2, r * 2);
    }
}

private static double measureTextWidth(String text, Font font) {
    Text t = new Text(text == null ? "" : text);
    t.setFont(font);
    return t.getLayoutBounds().getWidth();
}

private static double measureTextHeight(String text, Font font) {
    Text t = new Text(text == null ? "" : text);
    t.setFont(font);
    return t.getLayoutBounds().getHeight();
}

private static final class NeighborEdge<T> {
    final RenderedPoint<T> a;
    final RenderedPoint<T> b;
    final double closeness; // 0..1

    NeighborEdge(RenderedPoint<T> a, RenderedPoint<T> b, double closeness) {
        this.a = a;
        this.b = b;
        this.closeness = closeness;
    }
}



    /**
     * Draws small "pill" labels for the K nearest neighbors (UI-only).
     * Kept intentionally smaller than the selected label to reduce clutter.
     */
    private void drawNeighborLabels(GraphicsContext g) {
        if (neighborEdges.isEmpty()) return;

        // Deduplicate neighbors (defensive)
        java.util.HashSet<String> seen = new java.util.HashSet<>();

        g.setFont(NEIGHBOR_LABEL_FONT);

        for (NeighborEdge<String> e : neighborEdges) {
            String id = e.b.id;
            if (id == null) continue;
            if (!seen.add(id)) continue;

            // Place the label slightly above/right of the point.
            double sx = e.b.sx + 8;
            double sy = e.b.sy - 8;

            String text = String.valueOf(id);

            double textWidth = measureTextWidth(text, NEIGHBOR_LABEL_FONT);
            double textHeight = measureTextHeight(text, NEIGHBOR_LABEL_FONT);

            double paddingX = 6;
            double paddingY = 3;

            double boxX = sx;
            double boxY = sy - (textHeight / 2);

            // White-ish background
            g.setFill(Color.rgb(255, 255, 255, 0.85));
            g.fillRoundRect(boxX, boxY, textWidth + paddingX * 2, textHeight + paddingY * 2, 8, 8);

            // Subtle blue border
            g.setStroke(Color.rgb(30, 102, 255, 0.25));
            g.setLineWidth(1.0);
            g.strokeRoundRect(boxX, boxY, textWidth + paddingX * 2, textHeight + paddingY * 2, 8, 8);

            // Text
            g.setFill(Color.rgb(30, 102, 255, 0.85));
            g.fillText(text, boxX + paddingX, boxY + paddingY + 10);
        }
    }

}