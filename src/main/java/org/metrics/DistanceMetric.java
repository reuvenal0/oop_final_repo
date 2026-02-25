package org.metrics;

import org.model.Vector;

/**
 * Strategy interface for measuring distance between two vectors.
 * Implementations must define a distance where "smaller = closer".
 */
public interface DistanceMetric {

    /**
     * Computes the distance between two vectors.
     *
     * @param a first vector (non-null)
     * @param b second vector (non-null)
     * @return a non-negative distance, where smaller means more similar/closer
     * @throws IllegalArgumentException if vectors are null or dimensions mismatch
     */
    double distance(Vector a, Vector b);

    /**
     * @return a human-readable name for the metric (useful for UI/logging).
     */
    String name();
    String toString();
}