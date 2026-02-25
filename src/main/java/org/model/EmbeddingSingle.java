package org.model;

import org.io.Representation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a single embedding entity (word, image, ID, etc.)
 * together with its vector representations.
 *
 * Storage-agnostic: does NOT force Map/Collection internally.
 */
public interface EmbeddingSingle<T> {

    T id();

    /**
     * @return all available representations for this item.
     */
    Set<Representation> representations();

    default boolean has(Representation rep) {
        Objects.requireNonNull(rep, "rep must not be null");
        return representations().contains(rep);
    }

    /**
     * Returns the vector for the given representation if present.
     */
    Optional<Vector> get(Representation rep);

    /**
     * Returns the vector for the given representation or throws if missing.
     */
    default Vector require(Representation rep) {
        Objects.requireNonNull(rep, "rep must not be null");
        return get(rep).orElseThrow(() ->
                new IllegalArgumentException("Missing representation: " + rep + " for id: " + id())
        );
    }
}
