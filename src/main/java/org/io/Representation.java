package org.io;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Flyweight Representation:
 * - Instances are interned (cached) by canonical name.
 * - Representation.of(" FULL ") and Representation.of("full") return the SAME object reference.
 *
 * Why this matters:
 * - Prevents duplicate objects for the same conceptual representation.
 * - Lets you use Representation safely as a Map key.
 */
public final class Representation {

    private static final ConcurrentMap<String, Representation> POOL = new ConcurrentHashMap<>();

    private final String name; // canonical, normalized name

    private Representation(String canonicalName) {
        this.name = canonicalName;
    }

    /**
     * Factory method (the only way to obtain a Representation).
     * Performs normalization + interning.
     */
    public static Representation of(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("representation name must be non-empty");
        }
        String canonical = normalize(rawName);
        return POOL.computeIfAbsent(canonical, Representation::new);
    }

    public String name() {
        return name;
    }

    /**
     * Normalization rule:
     * - trim leading/trailing spaces
     * - lowercase
     * - remove internal spaces
     *
     * Note: removing internal spaces is aggressive; keep only if your rep names are simple keys (full/pca/etc).
     */
    private static String normalize(String s) {
        return s.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Representation other)) return false;
        return Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}