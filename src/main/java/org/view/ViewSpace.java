package org.view;

import org.io.Representation;
import org.model.EmbeddingGroup;
import org.model.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Visualization facade:
 * - Locked to a display representation (typically PCA).
 * - Holds UI-controlled view mode (2D/3D + axis indices).
 * - Produces ready-to-render points with labels.
 * - Supports centering/focus on a specific ID (requirement).
 */
public final class ViewSpace<T> {

    private final EmbeddingGroup<T> group;
    private final Representation displayRep;
    private ViewMode mode;

    // Current center in view coordinates (2D or 3D)
    private ViewPoint center;

    public ViewSpace(EmbeddingGroup<T> group, Representation displayRep, ViewMode initialMode) {
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.displayRep = Objects.requireNonNull(displayRep, "displayRep must not be null");
        this.mode = Objects.requireNonNull(initialMode, "initialMode must not be null");

        // Optional: initialize center to the first point if exists, otherwise null
        this.center = null;
    }

    public ViewMode mode() {
        return mode;
    }

    public void setMode(ViewMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
    }

    /**
     * Returns the current view center (may be null if not set yet).
     */
    public ViewPoint center() {
        return center;
    }

    /**
     * Requirement: search & center. Return a single point for the given ID.
     */
    public ViewPoint pointOf(T id) {
        Objects.requireNonNull(id, "id must not be null");
        Vector v = group.require(id, displayRep);
        return mapVectorToPoint(v, mode);
    }

    /**
     * Useful for Stage B: project an arbitrary vector (no ID) into the current view.
     */
    public ViewPoint project(Vector v) {
        Objects.requireNonNull(v, "vector must not be null");
        return mapVectorToPoint(v, mode);
    }

    /**
     * Requirement: space management. Return all labeled points for rendering.
     */
    public PointCloud<T> allPoints() {
        List<LabeledPoint<T>> out = new ArrayList<>(group.ids().size());
        for (T id : group.ids()) {
            Vector v = group.require(id, displayRep);
            out.add(new LabeledPoint<>(id, mapVectorToPoint(v, mode)));
        }
        return new PointCloud<>(out, mode);
    }

    /**
     * Requirement: search a word -> the UI centers around the point representing that word.
     * This moves the center to the projected coordinates of the given ID.
     */
    public void centerOn(T id) {
        Objects.requireNonNull(id, "id must not be null");
        this.center = pointOf(id); // throws if the ID does not exist (good: explicit error handling)
    }

    private static ViewPoint mapVectorToPoint(Vector v, ViewMode mode) {
        int dim = v.dim();

        // Validate chosen axes against the actual vector dimension.
        for (int axis = 0; axis < mode.viewDim(); axis++) {
            int idx = mode.axisIndex(axis);
            if (idx < 0 || idx >= dim) {
                throw new IllegalArgumentException("Axis index out of bounds: " + idx + " for dim=" + dim);
            }
        }

        double[] a = v.toArrayCopy();

        if (mode.viewDim() == 2) {
            int x = mode.axisIndex(0);
            int y = mode.axisIndex(1);
            return new Point_2D(a[x], a[y]);
        } else if (mode.viewDim() == 3) {
            int x = mode.axisIndex(0);
            int y = mode.axisIndex(1);
            int z = mode.axisIndex(2);
            return new Point_3D(a[x], a[y], a[z]);
        } else {
            throw new IllegalStateException("Unsupported viewDim: " + mode.viewDim());
        }
    }

    public Representation representation() {return displayRep;}
}