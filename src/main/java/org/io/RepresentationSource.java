package org.io;

import org.model.Vector;

import java.util.Map;
import java.util.OptionalInt;

/**
 * A single representation input:
 * - which representation it is (canonical Representation)
 * - where its data comes from (file/stream/db/etc.)
 *
 * Implementations should:
 * - load vectors once (and optionally cache)
 * - validate vectors (dimension consistency, duplicates, bad data)
 * - return an immutable map (or defensive copy)
 */
public interface RepresentationSource<T> {

    /**
     * The representation key this source provides (e.g., FULL, PCA).
     */
    Representation representation();

    /**
     * Loads (or returns cached) vectors for this representation.
     * The returned map must be immutable or a defensive copy.
     */
    Map<T, Vector> load();

    /**
     * Optional: the dimension of vectors in this source (known after loading).
     */
    OptionalInt dimension();
}
