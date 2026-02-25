package org.metrics;

import org.model.Vector;

/**
 * Cosine distance: 1 - cosineSimilarity(a, b),
 * where cosineSimilarity = (a · b) / (||a|| * ||b||).
 *
 * This produces a distance where smaller = closer.
 * Range is typically [0, 2] (numerical rounding can push slightly outside).
 */
public final class CosineDistance implements DistanceMetric {

    @Override
    public double distance(Vector a, Vector b) {
        requireNonNull(a, "a");
        requireNonNull(b, "b");
        if (a.dim() != b.dim()) {
            throw new IllegalArgumentException("Dimension mismatch: " + a.dim() + " vs " + b.dim());
        }

        double normA = a.norm();
        double normB = b.norm();
        if (normA == 0.0 || normB == 0.0) {
            throw new IllegalArgumentException("Cosine distance is undefined for zero vectors");
        }

        double cosineSimilarity = a.dot(b) / (normA * normB);
        return 1.0 - cosineSimilarity;
    }

    @Override
    public String name() {
        return "cosine";
    }

    private static void requireNonNull(Object x, String paramName) {
        if (x == null) {
            throw new IllegalArgumentException(paramName + " must not be null");
        }
    }

    @Override
    public String toString() {
        return name();
    }
}