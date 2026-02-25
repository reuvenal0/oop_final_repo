package org.model;

import org.io.Representation;

import java.util.Optional;
import java.util.Set;

/**
 * Read-only facade over a group of embedding items.
 */
public interface EmbeddingGroup<T> {

    /** @return true if the id exists in the store */
    boolean contains(T id);

    /**
     * @return the item for the id, or empty if not found.
     * Use this when you don't want exceptions in normal flow (e.g., UI).
     */
    Optional<EmbeddingSingle<T>> find(T id);

    /**
     * @return the item for the id, or throws if not found.
     * Use this when missing data is considered a bug.
     */
    EmbeddingSingle<T> requireSingle(T id);

    /** Convenience: required representation vector */
    Vector require(T id, Representation rep);

    /** All ids in the store */
    Set<T> ids();

    /**
     * What representations are available in this group.
     * In strict mode, all items share the same representation set.
     */
    Set<Representation> availableRepresentations();
}