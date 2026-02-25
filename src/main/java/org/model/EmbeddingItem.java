package org.model;

import org.io.Representation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable implementation of EmbeddingSingle backed by a Map internally.
 */
public final class EmbeddingItem<T> implements EmbeddingSingle<T> {

    private final T id;
    private final Map<Representation, Vector> reps;

    public EmbeddingItem(T id, Map<Representation, Vector> reps) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(reps, "reps must not be null");
        if (reps.isEmpty()) {
            throw new IllegalArgumentException("reps must not be empty");
        }

        // Map.copyOf is already unmodifiable (and defensive-copies the entries).
        this.reps = Map.copyOf(reps);
    }

    @Override
    public T id() {
        return id;
    }

    @Override
    public Set<Representation> representations() {
        // This is an unmodifiable view because the backing map is unmodifiable.
        return reps.keySet();
    }

    @Override
    public Optional<Vector> get(Representation rep) {
        Objects.requireNonNull(rep, "rep must not be null");
        return Optional.ofNullable(reps.get(rep));
    }

    /**
     * Optional: keep this if you really need an immutable Map view for debugging/serialization.
     * Not part of the EmbeddingSingle contract (so callers won't rely on Map).
     */
    public Map<Representation, Vector> repsView() {
        return reps;
    }
}