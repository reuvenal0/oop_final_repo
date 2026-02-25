package org.model;

import org.io.Representation;
import org.io.RepresentationSource;

import java.util.*;

/**
 * Factory/Builder that merges multiple representation sources into
 * a single EmbeddingStorage<T>.
 *
 * Strict mode:
 * - all sources must contain the exact same set of IDs.
 */
public final class EmbeddingsAssembler<T> {

    public EmbeddingStorage<T> assemble(Set<RepresentationSource<T>> sources) {
        Objects.requireNonNull(sources, "sources must not be null");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("At least one RepresentationSource is required");
        }

        Map<Representation, Map<T, Vector>> loaded = new LinkedHashMap<>();

        for (RepresentationSource<T> src : sources) {
            Representation rep = src.representation();
            if (loaded.containsKey(rep)) {
                throw new IllegalArgumentException("Duplicate representation source: " + rep);
            }
            Map<T, Vector> map = src.load();
            if (map.isEmpty()) {
                throw new IllegalArgumentException("Empty source for representation: " + rep);
            }
            loaded.put(rep, map);
        }

        Iterator<Map<T, Vector>> it = loaded.values().iterator();
        Set<T> baseIds = new LinkedHashSet<>(it.next().keySet());

        while (it.hasNext()) {
            Set<T> ids = it.next().keySet();
            if (!baseIds.equals(ids)) {
                throw new IllegalArgumentException(
                        "All representations must contain the same ID set"
                );
            }
        }

        Map<T, EmbeddingItem<T>> byId = new HashMap<>();

        for (T id : baseIds) {
            Map<Representation, Vector> reps = new HashMap<>();
            for (var entry : loaded.entrySet()) {
                Vector v = entry.getValue().get(id);
                if (v == null) {
                    throw new IllegalStateException(
                            "Missing vector for id=" + id + " in representation=" + entry.getKey()
                    );
                }
                reps.put(entry.getKey(), v);
            }
            byId.put(id, new EmbeddingItem<>(id, reps));
        }

        return new EmbeddingStorage<>(byId);
    }
}