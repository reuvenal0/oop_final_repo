package org.projection;

import org.model.Vector;

/**
 * Represents a 1D semantic axis in the FULL vector space.
 *
 * The axis is defined by:
 * - origin: a reference point (we use vector(A))
 * - direction: a unit vector (normalize(B - A))
 *
 * For any vector V:
 *   coordinate t = (V - origin) · direction
 *   orthogonal distance = ||(V - origin) - direction * t||
 */
public final class ProjectionAxis {

    private final Vector origin;
    private final Vector direction; // must be unit length

    private ProjectionAxis(Vector origin, Vector directionUnit) {
        if (origin == null) throw new IllegalArgumentException("origin must not be null");
        if (directionUnit == null) throw new IllegalArgumentException("direction must not be null");
        if (origin.dim() != directionUnit.dim()) {
            throw new IllegalArgumentException("Dimension mismatch: origin vs direction");
        }
        this.origin = origin;
        this.direction = directionUnit;
    }

    /**
     * Builds an axis from two anchor vectors A and B.
     * Origin is A, direction is normalized (B - A).
     */
    public static ProjectionAxis between(Vector a, Vector b) {
        if (a == null) throw new IllegalArgumentException("a must not be null");
        if (b == null) throw new IllegalArgumentException("b must not be null");
        if (a.dim() != b.dim()) {
            throw new IllegalArgumentException("Dimension mismatch: " + a.dim() + " vs " + b.dim());
        }

        Vector delta = b.subtract(a);
        if (delta.norm() == 0.0) {
            throw new IllegalArgumentException("Cannot build axis: anchors are identical (B - A is zero)");
        }

        return new ProjectionAxis(a, delta.normalized());
    }

    public Vector origin() {
        return origin;
    }

    public Vector direction() {
        return direction;
    }

    /**
     * Scalar coordinate of V along the axis.
     */
    public double coordinateOf(Vector v) {
        if (v == null) throw new IllegalArgumentException("v must not be null");
        if (v.dim() != origin.dim()) {
            throw new IllegalArgumentException("Dimension mismatch: " + v.dim() + " vs " + origin.dim());
        }
        return v.subtract(origin).dot(direction);
    }

    /**
     * Orthogonal distance of V from the axis line (in FULL space).
     * Useful to measure "how much V really belongs to this semantic scale".
     */
    public double orthogonalDistanceOf(Vector v) {
        double t = coordinateOf(v);
        Vector along = direction.scale(t);
        Vector residual = v.subtract(origin).subtract(along);
        return residual.norm();
    }

    /**
     * Builds an axis from two anchor vectors A and B, but with origin at the midpoint (A+B)/2.
     * This makes coordinates symmetric: A is negative, B is positive.
     */
    public static ProjectionAxis centeredBetween(Vector a, Vector b) {
        if (a == null) throw new IllegalArgumentException("a must not be null");
        if (b == null) throw new IllegalArgumentException("b must not be null");
        if (a.dim() != b.dim()) {
            throw new IllegalArgumentException("Dimension mismatch: " + a.dim() + " vs " + b.dim());
        }

        Vector delta = b.subtract(a);
        if (delta.norm() == 0.0) {
            throw new IllegalArgumentException("Cannot build axis: anchors are identical");
        }

        Vector originMid = a.add(b).scale(0.5);
        Vector direction = delta.normalized();
        return new ProjectionAxis(originMid, direction);
    }

}