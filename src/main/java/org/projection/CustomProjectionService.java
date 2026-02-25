package org.projection;

import org.io.Representation;
import org.model.EmbeddingGroup;
import org.model.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Service that creates semantic axes from two IDs and projects the whole vocabulary onto the axis.
 *
 * This is backend-only: no UI assumptions.
 */
public final class CustomProjectionService<T> {

    private final EmbeddingGroup<T> group;
    private final Representation representation; // usually FULL

    public CustomProjectionService(EmbeddingGroup<T> group, Representation representation) {
        this.group = Objects.requireNonNull(group, "group must not be null");
        this.representation = Objects.requireNonNull(representation, "representation must not be null");
    }

    /**
     * Builds a semantic axis from two anchor IDs (A -> B), using origin=A.
     */
    public ProjectionAxis axisBetween(T aId, T bId) {
        requireNonNullId(aId, "aId");
        requireNonNullId(bId, "bId");

        Vector a = group.require(aId, representation);
        Vector b = group.require(bId, representation);

        return ProjectionAxis.between(a, b);
    }

    /**
     * Builds a semantic axis from two anchor IDs (A -> B), using origin=(A+B)/2 (midpoint).
     * Coordinates become symmetric: A negative, B positive.
     */
    public ProjectionAxis centeredAxisBetween(T aId, T bId) {
        requireNonNullId(aId, "aId");
        requireNonNullId(bId, "bId");

        Vector a = group.require(aId, representation);
        Vector b = group.require(bId, representation);

        return ProjectionAxis.centeredBetween(a, b);
    }

    /**
     * Projects all IDs in the group onto the given axis.
     *
     * @param axis the axis (non-null)
     * @param includeAnchors whether to keep the anchors in the returned list
     * @param aId anchor A (needed only for filtering when includeAnchors=false)
     * @param bId anchor B (needed only for filtering when includeAnchors=false)
     * @return list of ProjectionScore sorted by coordinate ascending (from "more A-like" to "more B-like")
     */
    public List<ProjectionScore<T>> projectAll(ProjectionAxis axis, boolean includeAnchors, T aId, T bId) {
        Objects.requireNonNull(axis, "axis must not be null");
        if (!includeAnchors) {
            requireNonNullId(aId, "aId");
            requireNonNullId(bId, "bId");
        }

        List<ProjectionScore<T>> out = new ArrayList<>(group.ids().size());

        for (T id : group.ids()) {
            if (!includeAnchors && (id.equals(aId) || id.equals(bId))) {
                continue;
            }

            Vector v = group.require(id, representation);
            double t = axis.coordinateOf(v);
            double orth = axis.orthogonalDistanceOf(v);

            // Purity is optional; if your record doesn't have it, just remove the 4th argument.
            double purity = purity(t, orth);

            out.add(new ProjectionScore<>(id, t, orth, purity));
        }

        out.sort(Comparator.comparingDouble(ProjectionScore::coordinate));
        return out;
    }

    /**
     * Convenience: build axis between aId and bId (origin=A) and project all vocabulary.
     */
    public List<ProjectionScore<T>> semanticScale(T aId, T bId, boolean includeAnchors) {
        ProjectionAxis axis = axisBetween(aId, bId);
        return projectAll(axis, includeAnchors, aId, bId);
    }

    /**
     * Returns a "clean" semantic scale:
     * 1) Project all words onto the axis (origin=A)
     * 2) Keep only the N words closest to the axis (smallest orthogonal distance)
     * 3) Sort the remaining words by coordinate (A-like -> B-like)
     *
     * Note:
     * This produces a cleaner line, but can still include "noise words"
     * if the axis direction is not strongly represented in the dataset.
     */
    public List<ProjectionScore<T>> cleanSemanticScale(T aId, T bId, int keepClosest, boolean includeAnchors) {
        requireNonNullId(aId, "aId");
        requireNonNullId(bId, "bId");
        if (keepClosest <= 0) throw new IllegalArgumentException("keepClosest must be >= 1");

        ProjectionAxis axis = axisBetween(aId, bId);

        // Include anchors in the projection list so filtering is simple and consistent
        List<ProjectionScore<T>> all = projectAll(axis, true, aId, bId);

        // 1) Sort by orthogonal distance (closest to axis first)
        all.sort(Comparator.comparingDouble(ProjectionScore::orthogonalDistance));

        // 2) Keep top N closest-to-axis
        int n = Math.min(keepClosest, all.size());
        List<ProjectionScore<T>> closest = new ArrayList<>(all.subList(0, n));

        // 3) Optionally remove anchors
        if (!includeAnchors) {
            closest.removeIf(s -> s.id().equals(aId) || s.id().equals(bId));
        }

        // 4) Sort by coordinate (semantic scale order)
        closest.sort(Comparator.comparingDouble(ProjectionScore::coordinate));
        return closest;
    }

    /**
     * Produces a "clean" semantic scale using a purity score:
     * purity = |t| / (orth + eps)
     *
     * Steps:
     * 1) Build a centered axis between A and B (midpoint origin)
     * 2) Compute (t, orth, purity) for every word
     * 3) Keep topN by purity (best-aligned to the axis)
     * 4) Sort the kept words by coordinate (scale order)
     */
    public List<ProjectionScore<T>> cleanScaleByPurity(T aId, T bId, int topN, boolean includeAnchors) {
        requireNonNullId(aId, "aId");
        requireNonNullId(bId, "bId");
        if (topN <= 0) throw new IllegalArgumentException("topN must be >= 1");

        ProjectionAxis axis = centeredAxisBetween(aId, bId);

        List<ProjectionScore<T>> all = new ArrayList<>(group.ids().size());

        for (T id : group.ids()) {
            if (!includeAnchors && (id.equals(aId) || id.equals(bId))) {
                continue;
            }

            Vector v = group.require(id, representation);
            double t = axis.coordinateOf(v);
            double orth = axis.orthogonalDistanceOf(v);
            double purity = purity(t, orth);

            all.add(new ProjectionScore<>(id, t, orth, purity));
        }

        // Keep topN by purity (largest first)
        all.sort(Comparator.comparingDouble(ProjectionScore<T>::purity).reversed());
        int n = Math.min(topN, all.size());
        List<ProjectionScore<T>> best = new ArrayList<>(all.subList(0, n));

        // Now sort by coordinate to get a real scale A->B
        best.sort(Comparator.comparingDouble(ProjectionScore::coordinate));
        return best;
    }

    // -------------------------------------------------------------------------
    // Private helpers (small, local, and predictable)
    // -------------------------------------------------------------------------

    private static void requireNonNullId(Object id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    /**
     * A simple "how much on-axis" score.
     * Bigger means the point progresses along the axis with less sideways deviation.
     */
    private static double purity(double t, double orth) {
        final double eps = 1e-9;
        return Math.abs(t) / (orth + eps);
    }
}
