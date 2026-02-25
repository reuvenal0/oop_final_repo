package org.metrics;

import org.io.Representation;
import org.model.EmbeddingGroup;
import org.model.Vector;

import java.util.*;

/**
 * Finds nearest neighbors by scanning all IDs in an EmbeddingGroup
 * and keeping the top-K closest results using a bounded max-heap.
 *
 * Design:
 * - Depends on abstractions (EmbeddingGroup, DistanceMetric) => DIP
 * - Supports new metrics without modification => OCP (Strategy)
 */
public final class NearestNeighbors<T> {

    private final EmbeddingGroup<T> group;
    private final Representation representation;
    private final DistanceMetric metric;

    /**
     * @param group embedding group (non-null)
     * @param representation which representation to compare (e.g., FULL) (non-null)
     * @param metric distance strategy (non-null)
     */
    public NearestNeighbors(EmbeddingGroup<T> group, Representation representation, DistanceMetric metric) {
        if (group == null) throw new IllegalArgumentException("group must not be null");
        if (representation == null) throw new IllegalArgumentException("representation must not be null");
        if (metric == null) throw new IllegalArgumentException("metric must not be null");
        this.group = group;
        this.representation = representation;
        this.metric = metric;
    }

    /**
     * Returns the K nearest neighbors of queryId (excluding queryId itself).
     *
     * @param queryId the query ID (must exist in the group)
     * @param k number of neighbors to return (must be >= 1)
     * @return a list sorted by ascending distance (closest first)
     */
    public List<Neighbor<T>> topK(T queryId, int k) {
        if (queryId == null) throw new IllegalArgumentException("queryId must not be null");
        if (k <= 0) throw new IllegalArgumentException("k must be >= 1");

        // Fail-fast if query does not exist (your group likely already throws a nice message)
        Vector queryVec = group.require(queryId, representation);

        // Max-heap by distance: the "worst" (largest distance) is on top and gets evicted first.
        PriorityQueue<Neighbor<T>> heap = new PriorityQueue<>(
                Comparator.<Neighbor<T>>comparingDouble(Neighbor::distance).reversed()
        );

        for (T candidateId : group.ids()) {
            if (candidateId == null) continue; // defensive; should not happen in a clean dataset
            if (candidateId.equals(queryId)) continue;

            Vector candidateVec = group.require(candidateId, representation);
            double d = metric.distance(queryVec, candidateVec);

            Neighbor<T> neighbor = new Neighbor<>(candidateId, d);

            if (heap.size() < k) {
                heap.add(neighbor);
            } else if (d < heap.peek().distance()) {
                heap.poll();
                heap.add(neighbor);
            }
        }

        // Convert heap -> sorted list (closest first)
        List<Neighbor<T>> result = new ArrayList<>(heap);
        result.sort(Comparator.comparingDouble(Neighbor::distance));
        return result;
    }

    /**
     * Top-K neighbors for an arbitrary query vector.
     *
     * @param queryVector query vector (must match representation dimension)
     * @param k number of neighbors to return (k >= 1)
     * @param exclude optional IDs to exclude (may be null)
     *
     * Complexity:
     * - Time: O(N log K) where N = vocabulary size
     * - Space: O(K)
     */
    public List<Neighbor<T>> topK(Vector queryVector, int k, Set<T> exclude) {
        if (queryVector == null) throw new IllegalArgumentException("queryVector must not be null");
        if (k <= 0) throw new IllegalArgumentException("k must be >= 1");

        // Max-heap by distance: keep the worst (largest distance) on top so we can pop it when we find a better one.
        PriorityQueue<Neighbor<T>> heap = new PriorityQueue<>(
                Comparator.comparingDouble(Neighbor<T>::distance).reversed()
        );

        for (T id : group.ids()) {
            if (exclude != null && exclude.contains(id)) {
                continue;
            }

            Vector v = group.require(id, representation);

            // Vector should fail-fast if dimensions mismatch (FULL vs PCA accidental mix).
            double d = metric.distance(queryVector, v);

            Neighbor<T> candidate = new Neighbor<>(id, d);

            if (heap.size() < k) {
                heap.add(candidate);
            } else if (d < heap.peek().distance()) {
                heap.poll();
                heap.add(candidate);
            }
        }

        // Convert heap to sorted list (ascending distance).
        List<Neighbor<T>> result = new ArrayList<>(heap);
        result.sort(Comparator.comparingDouble(Neighbor<T>::distance));
        return result;
    }

    public DistanceMetric metric() {
        return metric;
    }

    public Representation representation() {
        return representation;
    }
}