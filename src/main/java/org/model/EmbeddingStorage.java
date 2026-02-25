package org.model;

import org.io.Representation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable implementation of EmbeddingGroup backed by an in-memory map.
 */
public final class EmbeddingStorage<T> implements EmbeddingGroup<T> {

    private final Map<T, EmbeddingSingle<T>> byId;
    private final Set<Representation> availableReps;

    public EmbeddingStorage(Map<T, ? extends EmbeddingSingle<T>> byId) {
        Objects.requireNonNull(byId, "byId must not be null");
        if (byId.isEmpty()) throw new IllegalArgumentException("EmbeddingStorage cannot be empty");

        this.byId = Map.copyOf(byId);

        EmbeddingSingle<T> first = this.byId.values().iterator().next();
        Set<Representation> reps = Set.copyOf(first.representations());

        // Enforce strict-mode: all items must have the exact same representation set
        for (EmbeddingSingle<T> item : this.byId.values()) {
            if (!item.representations().equals(reps)) {
                throw new IllegalArgumentException(
                        "Inconsistent representation set for id=" + item.id() +
                                ". Expected=" + reps + ", but got=" + item.representations()
                );
            }
        }

        this.availableReps = reps;
    }

    @Override
    public boolean contains(T id) {
        return byId.containsKey(id);
    }

    @Override
    public Optional<EmbeddingSingle<T>> find(T id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public EmbeddingSingle<T> requireSingle(T id) {
        return find(id).orElseThrow(() ->
                new IllegalArgumentException("Unknown id: " + id)
        );
    }

    @Override
    public Vector require(T id, Representation rep) {
        Objects.requireNonNull(rep, "rep must not be null");
        return requireSingle(id).require(rep);
    }

    @Override
    public Set<T> ids() {
        return byId.keySet();
    }

    @Override
    public Set<Representation> availableRepresentations() {
        return availableReps;
    }
}
