package org.metrics;

import org.model.Vector;

/**
 * Euclidean (L2) distance: sqrt(sum_i (a_i - b_i)^2).
 */
public final class EuclideanDistance implements DistanceMetric {

    @Override
    public double distance(Vector a, Vector b) {
        requireNonNull(a, "a");
        requireNonNull(b, "b");
        if (a.dim() != b.dim()) {
            throw new IllegalArgumentException("Dimension mismatch: " + a.dim() + " vs " + b.dim());
        }

        double sumSq = 0.0;
        for (int i = 0; i < a.dim(); i++) {
            double d = a.get(i) - b.get(i);
            sumSq += d * d;
        }
        return Math.sqrt(sumSq);
    }

    @Override
    public String name() {
        return "euclidean";
    }
    @Override
    public String toString() {return name();}

        private static void requireNonNull(Object x, String paramName) {
        if (x == null) {
            throw new IllegalArgumentException(paramName + " must not be null");
        }
    }
}