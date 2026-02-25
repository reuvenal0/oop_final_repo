package org.io.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.io.Representation;
import org.io.RepresentationSource;
import org.model.Vector;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;

/**
 * JSON implementation of RepresentationSource.
 *
 * Expected JSON shape: array of objects
 * [
 *   { "word": "the", "vector": [ ... ] },
 *   { "word": "cat", "vector": [ ... ] }
 * ]
 */
public final class JsonSource<T> implements RepresentationSource<T> {

    private final Representation representation;
    private final InputStreamSupplier streamSupplier;
    private final JsonFormat format;
    private final Function<String, T> idParser;

    // Cached after first load
    private volatile Map<T, Vector> cached;
    private volatile Integer cachedDim;

    private final Object lock = new Object();

    public JsonSource(Representation representation,
                      InputStreamSupplier streamSupplier,
                      JsonFormat format,
                      Function<String, T> idParser) {

        this.representation = Objects.requireNonNull(representation, "representation must not be null");
        this.streamSupplier = Objects.requireNonNull(streamSupplier, "streamSupplier must not be null");
        this.format = Objects.requireNonNull(format, "format must not be null");
        this.idParser = Objects.requireNonNull(idParser, "idParser must not be null");
    }

    @Override
    public Representation representation() {
        return representation;
    }

    @Override
    public Map<T, Vector> load() {
        Map<T, Vector> local = cached;
        if (local != null) {
            return local;
        }

        synchronized (lock) {
            if (cached != null) {
                return cached;
            }
            Loaded<T> loaded = loadOnce();
            this.cachedDim = loaded.dimension;
            this.cached = Collections.unmodifiableMap(loaded.map);
            return this.cached;
        }
    }

    @Override
    public OptionalInt dimension() {
        Integer d = cachedDim;
        return (d == null) ? OptionalInt.empty() : OptionalInt.of(d);
    }

    private Loaded<T> loadOnce() {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = mapper.getFactory();

        Map<T, Vector> result = new HashMap<>();
        Integer dim = null;

        try (InputStream in = streamSupplier.open();
             JsonParser p = factory.createParser(in)) {

            // Expect start array
            if (p.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalArgumentException("JSON must start with an array of objects");
            }

            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (p.currentToken() != JsonToken.START_OBJECT) {
                    throw new IllegalArgumentException("Expected an object inside the array");
                }

                String rawId = null;
                double[] vectorArr = null;

                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String field = p.getCurrentName();
                    p.nextToken(); // move to value

                    if (format.idField().equals(field)) {
                        rawId = p.getValueAsString(null);
                    } else if (format.vectorField().equals(field)) {
                        vectorArr = readDoubleArray(p);
                    } else {
                        // Skip unknown fields cleanly
                        p.skipChildren();
                    }
                }

                // Validate id
                if (rawId == null || rawId.isBlank()) {
                    throw new IllegalArgumentException("Missing/blank id field '" + format.idField() + "'");
                }
                T id = idParser.apply(rawId);
                if (id == null) {
                    throw new IllegalArgumentException("Parsed id is null for raw id: " + rawId);
                }

                // Validate vector
                if (vectorArr == null) {
                    throw new IllegalArgumentException("Missing vector field '" + format.vectorField() + "' for id: " + rawId);
                }
                if (vectorArr.length == 0) {
                    throw new IllegalArgumentException("Vector must not be empty for id: " + rawId);
                }

                if (dim == null) {
                    dim = vectorArr.length;
                } else if (vectorArr.length != dim) {
                    throw new IllegalArgumentException(
                            "Inconsistent vector dimension in representation '" + representation +
                                    "': expected " + dim + " but got " + vectorArr.length + " for id: " + rawId
                    );
                }

                Vector v = new Vector(vectorArr);

                // No duplicate IDs
                if (result.putIfAbsent(id, v) != null) {
                    throw new IllegalArgumentException("Duplicate id in representation '" + representation + "': " + rawId);
                }
            }

            if (result.isEmpty()) {
                throw new IllegalArgumentException("JSON array is empty for representation '" + representation + "'");
            }

            return new Loaded<>(result, dim);

        } catch (IOException e) {
            // Wrap IO as unchecked for simplicity at this layer
            throw new java.io.UncheckedIOException(
                    "Failed to read JSON for representation '" + representation + "'", e
            );

        }
    }

    private static double[] readDoubleArray(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_ARRAY) {
            throw new IllegalArgumentException(
                    "Vector field must be a JSON array of numbers"
            );
        }

        // Start with a reasonable initial capacity
        int capacity = 5000;
        double[] buffer = new double[capacity];
        int size = 0;

        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (!p.currentToken().isNumeric()) {
                throw new IllegalArgumentException(
                        "Vector array must contain numbers only"
                );
            }

            // Grow array if needed
            if (size == capacity) {
                capacity *= 2;
                buffer = java.util.Arrays.copyOf(buffer, capacity);
            }

            buffer[size++] = p.getDoubleValue();
        }

        // Trim to exact size
        return java.util.Arrays.copyOf(buffer, size);
    }

    private record Loaded<T>(Map<T, Vector> map, int dimension) { }

    /**
     * Simple functional interface so callers can provide:
     * - a file stream
     * - a classpath resource stream
     * - a stream from S3/DB/etc. later
     */
    @FunctionalInterface
    public interface InputStreamSupplier {
        InputStream open() throws IOException;
    }
}
