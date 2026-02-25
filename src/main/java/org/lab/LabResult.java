package org.lab;

import org.metrics.Neighbor;
import org.model.Vector;

import java.util.List;
import java.util.Objects;

/**
 * Result of a vector arithmetic query: computed vector + nearest neighbors.
 */
public record LabResult<T>(Vector vector, List<Neighbor<T>> neighbors) {
    public LabResult {
        Objects.requireNonNull(vector, "vector must not be null");
        Objects.requireNonNull(neighbors, "neighbors must not be null");
    }
}