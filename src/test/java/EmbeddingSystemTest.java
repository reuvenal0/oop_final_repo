import org.io.Representation;
import org.io.json.JsonFormat;
import org.io.json.JsonSource;
import org.io.RepresentationSource;
import org.model.EmbeddingItem;
import org.model.EmbeddingStorage;
import org.model.EmbeddingsAssembler;
import org.model.Vector;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Comprehensive tests for:
 * - Representation (Flyweight + normalization)
 * - JsonFormat validation
 * - Vector (immutability, math, equals/hashCode, exceptions)
 * - EmbeddingItem / EmbeddingStorage behavior and immutability
 * - JsonSource load/caching and fail-fast on malformed JSON
 * - EmbeddingsAssembler strict merge rules and fail-fast behavior
 * - End-to-end load from real project files under /data (optional, guarded by assumptions)
 *
 * JSON expectation:
 * [
 *   {"word":"the","vector":[...]}
 * ]
 */
public class EmbeddingSystemTest {

    // Optional strict expectations for E2E (set to >0 if you want strict checks)
    private static final int EXPECTED_IDS = 0;
    private static final int EXPECTED_FULL_DIM = 0;
    private static final int EXPECTED_PCA_DIM = 0;

    // -----------------------------
    // Representation tests
    // -----------------------------

    @Test
    void representation_of_shouldNormalizeAndIntern() {
        Representation a = Representation.of(" FULL ");
        Representation b = Representation.of("full");
        Representation c = Representation.of(" f u l l "); // internal spaces removed by normalize()

        assertSame(a, b, "Flyweight should intern identical canonical names");
        assertSame(b, c, "Normalization should remove spaces, so all variants intern to same object");
        assertEquals("full", a.name());
        assertEquals("full", a.toString());
    }

    @Test
    void representation_of_shouldRejectNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> Representation.of(null));
        assertThrows(IllegalArgumentException.class, () -> Representation.of(""));
        assertThrows(IllegalArgumentException.class, () -> Representation.of("   "));
    }

    // -----------------------------
    // JsonFormat tests
    // -----------------------------

    @Test
    void jsonFormat_shouldRejectBlankFields() {
        assertThrows(IllegalArgumentException.class, () -> new JsonFormat(null, "vector"));
        assertThrows(IllegalArgumentException.class, () -> new JsonFormat("word", null));
        assertThrows(IllegalArgumentException.class, () -> new JsonFormat("   ", "vector"));
        assertThrows(IllegalArgumentException.class, () -> new JsonFormat("word", "   "));
    }

    // -----------------------------
    // Vector tests
    // -----------------------------

    @Test
    void vector_shouldBeImmutable_byCopyingInputArray() {
        double[] raw = {1.0, 2.0, 3.0};
        Vector v = new Vector(raw);

        raw[0] = 999.0; // attempt to mutate original array
        assertEquals(1.0, v.get(0), 0.0, "Vector must copy input array to remain immutable");
    }

    @Test
    void vector_toArrayCopy_shouldReturnDefensiveCopy() {
        Vector v = new Vector(new double[]{1.0, 2.0});
        double[] copy = v.toArrayCopy();
        copy[0] = 999.0;

        assertEquals(1.0, v.get(0), 0.0, "toArrayCopy must protect internal state");
    }

    @Test
    void vector_constructor_shouldRejectNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new Vector(null));
        assertThrows(IllegalArgumentException.class, () -> new Vector(new double[]{}));
    }

    @Test
    void vector_math_sanity() {
        Vector a = new Vector(new double[]{3, 4});
        assertEquals(5.0, a.norm(), 1e-12, "norm(3,4) should be 5");
        assertEquals(25.0, a.normSquared(), 1e-12, "normSquared should match");
        assertEquals(a.normSquared(), a.dot(a), 1e-12, "dot(v,v) == normSquared(v)");
    }

    @Test
    void vector_normalized_shouldHaveNormOne() {
        Vector a = new Vector(new double[]{3, 4});
        Vector u = a.normalized();
        assertEquals(1.0, u.norm(), 1e-12, "normalized vector should have norm ~= 1");
    }

    @Test
    void vector_normalized_shouldFailOnZeroVector() {
        Vector z = new Vector(new double[]{0, 0, 0});
        assertThrows(IllegalStateException.class, z::normalized);
    }

    @Test
    void vector_ops_shouldEnforceSameDimension() {
        Vector a = new Vector(new double[]{1, 2, 3});
        Vector b = new Vector(new double[]{1, 2});

        assertThrows(IllegalArgumentException.class, () -> a.dot(b));
        assertThrows(IllegalArgumentException.class, () -> a.add(b));
        assertThrows(IllegalArgumentException.class, () -> a.subtract(b));
    }

    @Test
    void vector_equalsAndHashCode_shouldWork() {
        Vector a = new Vector(new double[]{1, 2, 3});
        Vector b = new Vector(new double[]{1, 2, 3});
        Vector c = new Vector(new double[]{1, 2, 4});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    // -----------------------------
    // EmbeddingItem tests
    // -----------------------------

    @Test
    void embeddingItem_shouldRejectNullIdOrEmptyReps() {
        Representation full = Representation.of("full");

        assertThrows(NullPointerException.class,
                () -> new EmbeddingItem<>(null, Map.of(full, new Vector(new double[]{1})))
        );
        assertThrows(NullPointerException.class,
                () -> new EmbeddingItem<>("x", null)
        );
        assertThrows(IllegalArgumentException.class,
                () -> new EmbeddingItem<>("x", Map.of())
        );
    }

    @Test
    void embeddingItem_hasAndRequire_shouldWork_andFailFast() {
        Representation full = Representation.of("full");
        Representation pca = Representation.of("pca");

        EmbeddingItem<String> item = new EmbeddingItem<>(
                "the",
                Map.of(full, new Vector(new double[]{1, 2, 3}))
        );

        assertTrue(item.has(full));
        assertFalse(item.has(pca));

        assertNotNull(item.require(full));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> item.require(pca));
        assertTrue(ex.getMessage().toLowerCase().contains("missing representation"));
    }

    @Test
    void embeddingItem_repsView_shouldBeImmutable() {
        Representation full = Representation.of("full");
        EmbeddingItem<String> item = new EmbeddingItem<>("x", Map.of(full, new Vector(new double[]{1})));

        Map<Representation, Vector> view = item.repsView();
        assertThrows(UnsupportedOperationException.class,
                () -> view.put(Representation.of("pca"), new Vector(new double[]{2}))
        );
    }

    // -----------------------------
    // EmbeddingStorage tests
    // -----------------------------

    @Test
    void embeddingStorage_shouldRejectEmptyMap() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingStorage<>(Map.of()));
    }

    @Test
    void embeddingStorage_containsFindRequireSingleRequire_shouldBehaveCorrectly() {
        Representation full = Representation.of("full");

        EmbeddingItem<String> item = new EmbeddingItem<>("id1", Map.of(full, new Vector(new double[]{1, 2})));
        EmbeddingStorage<String> s = new EmbeddingStorage<>(Map.of("id1", item));

        assertTrue(s.contains("id1"));
        assertFalse(s.contains("missing"));

        assertTrue(s.find("id1").isPresent());
        assertTrue(s.find("missing").isEmpty());

        assertEquals("id1", s.requireSingle("id1").id());
        assertThrows(IllegalArgumentException.class, () -> s.requireSingle("missing"));

        Vector v = s.require("id1", full);
        assertEquals(2, v.dim());
    }

    @Test
    void embeddingStorage_availableRepresentations_shouldExposeKeys() {
        Representation full = Representation.of("full");
        Representation pca = Representation.of("pca");

        EmbeddingItem<String> item = new EmbeddingItem<>("id1", Map.of(
                full, new Vector(new double[]{1}),
                pca, new Vector(new double[]{2})
        ));

        EmbeddingStorage<String> s = new EmbeddingStorage<>(Map.of("id1", item));
        Set<Representation> reps = s.availableRepresentations();

        assertTrue(reps.contains(full));
        assertTrue(reps.contains(pca));
    }

    // -----------------------------
    // JsonSource tests (happy path + caching + error cases)
    // -----------------------------

    @Test
    void jsonSource_load_shouldCacheAndReturnImmutableMap() throws IOException {
        Representation rep = Representation.of("full");
        JsonFormat format = new JsonFormat("word", "vector");

        AtomicInteger openCount = new AtomicInteger(0);

        JsonSource.InputStreamSupplier supplier = () -> {
            openCount.incrementAndGet();
            Path p = writeTempJson("""
                    [
                      {"word":"the","vector":[1.0,2.0,3.0]},
                      {"word":"of","vector":[4.0,5.0,6.0]}
                    ]
                    """);
            return Files.newInputStream(p);
        };

        JsonSource<String> src = new JsonSource<>(rep, supplier, format, s -> s);

        Map<String, Vector> m1 = src.load();
        Map<String, Vector> m2 = src.load();

        assertSame(m1, m2, "load() should return cached map instance after first load");
        assertEquals(1, openCount.get(), "InputStream should be opened only once due to caching");

        assertEquals(2, m1.size());
        assertTrue(src.dimension().isPresent());
        assertEquals(3, src.dimension().orElseThrow());

        assertThrows(UnsupportedOperationException.class,
                () -> m1.put("x", new Vector(new double[]{1})),
                "Returned map should be immutable"
        );
    }

    @Test
    void jsonSource_shouldFailIfJsonDoesNotStartWithArray() throws IOException {
        Representation rep = Representation.of("full");
        JsonFormat format = new JsonFormat("word", "vector");
        Path p = writeTempJson("""
                {"word":"the","vector":[1,2,3]}
                """);

        JsonSource<String> src = new JsonSource<>(rep, () -> Files.newInputStream(p), format, s -> s);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
        assertTrue(ex.getMessage().toLowerCase().contains("must start with an array"));
    }

    @Test
    void jsonSource_shouldFailIfArrayContainsNonObject() throws IOException {
        Representation rep = Representation.of("full");
        JsonFormat format = new JsonFormat("word", "vector");
        Path p = writeTempJson("""
                [ 123 ]
                """);

        JsonSource<String> src = new JsonSource<>(rep, () -> Files.newInputStream(p), format, s -> s);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
        assertTrue(ex.getMessage().toLowerCase().contains("expected an object"));
    }

    @Test
    void jsonSource_shouldFailOnMissingOrBlankId() throws IOException {
        Representation rep = Representation.of("full");
        JsonFormat format = new JsonFormat("word", "vector");

        Path missingId = writeTempJson("""
                [
                  {"vector":[1,2,3]}
                ]
                """);

        JsonSource<String> src1 = new JsonSource<>(rep, () -> Files.newInputStream(missingId), format, s -> s);
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, src1::load);
        assertTrue(ex1.getMessage().toLowerCase().contains("missing/blank id"));

        Path blankId = writeTempJson("""
                [
                  {"word":"   ","vector":[1,2,3]}
                ]
                """);

        JsonSource<String> src2 = new JsonSource<>(rep, () -> Files.newInputStream(blankId), format, s -> s);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, src2::load);
        assertTrue(ex2.getMessage().toLowerCase().contains("missing/blank id"));
    }

    @Test
    void jsonSource_shouldFailOnNullParsedId() throws IOException {
        Representation rep = Representation.of("full");
        JsonFormat format = new JsonFormat("word", "vector");

        Path p = writeTempJson("""
                [
                  {"word":"the","vector":[1,2,3]}
                ]
                """);

        Function<String, String> badParser = s -> null;

        JsonSource<String> src = new JsonSource<>(rep, () -> Files.newInputStream(p), format, badParser);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
        assertTrue(ex.getMessage().toLowerCase().contains("parsed id is null"));
    }

    @Test
    void jsonSource_shouldFailOnMissingVectorOrEmptyVector() throws IOException {
        Representation rep = Representation.of("full");
        JsonFormat format = new JsonFormat("word", "vector");

        Path missingVector = writeTempJson("""
                [
                  {"word":"the"}
                ]
                """);

        JsonSource<String> src1 = new JsonSource<>(rep, () -> Files.newInputStream(missingVector), format, s -> s);
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, src1::load);
        assertTrue(ex1.getMessage().toLowerCase().contains("missing vector field"));

        Path emptyVector = writeTempJson("""
                [
                  {"word":"the","vector":[]}
                ]
                """);

        JsonSource<String> src2 = new JsonSource<>(rep, () -> Files.newInputStream(emptyVector), format, s -> s);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, src2::load);
        assertTrue(ex2.getMessage().toLowerCase().contains("must not be empty"));
    }

    @Test
    void jsonSource_shouldFailOnNonNumericVector() throws IOException {
        Representation rep = Representation.of("full");
        JsonFormat format = new JsonFormat("word", "vector");

        Path p = writeTempJson("""
                [
                  {"word":"the","vector":[1, "oops", 3]}
                ]
                """);

        JsonSource<String> src = new JsonSource<>(rep, () -> Files.newInputStream(p), format, s -> s);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
        assertTrue(ex.getMessage().toLowerCase().contains("numbers only"));
    }

    @Test
    void jsonSource_shouldFailOnDuplicateIds() throws IOException {
        Representation rep = Representation.of("full");
        JsonFormat format = new JsonFormat("word", "vector");

        Path p = writeTempJson("""
                [
                  {"word":"the","vector":[1,2,3]},
                  {"word":"the","vector":[4,5,6]}
                ]
                """);

        JsonSource<String> src = new JsonSource<>(rep, () -> Files.newInputStream(p), format, s -> s);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
        assertTrue(ex.getMessage().toLowerCase().contains("duplicate id"));
    }

    @Test
    void jsonSource_shouldFailOnDimensionMismatchInsideFile() throws IOException {
        Representation rep = Representation.of("full");
        JsonFormat format = new JsonFormat("word", "vector");

        Path p = writeTempJson("""
                [
                  {"word":"the","vector":[1,2,3]},
                  {"word":"of","vector":[4,5]}
                ]
                """);

        JsonSource<String> src = new JsonSource<>(rep, () -> Files.newInputStream(p), format, s -> s);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
        assertTrue(ex.getMessage().toLowerCase().contains("inconsistent vector dimension"));
    }

    @Test
    void jsonSource_shouldFailOnEmptyArray() throws IOException {
        Representation rep = Representation.of("full");
        JsonFormat format = new JsonFormat("word", "vector");

        Path p = writeTempJson("[]");

        JsonSource<String> src = new JsonSource<>(rep, () -> Files.newInputStream(p), format, s -> s);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
        assertTrue(ex.getMessage().toLowerCase().contains("array is empty"));
    }

    // -----------------------------
    // EmbeddingsAssembler tests
    // -----------------------------

    @Test
    void assembler_shouldRejectNullOrEmptySources() {
        EmbeddingsAssembler<String> assembler = new EmbeddingsAssembler<>();

        assertThrows(NullPointerException.class, () -> assembler.assemble(null));
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(Set.of()));
    }

    @Test
    void assembler_shouldFailOnDuplicateRepresentation() {
        Representation rep = Representation.of("full");

        RepresentationSource<String> s1 = new InMemorySource<>(rep, Map.of("a", new Vector(new double[]{1})));
        RepresentationSource<String> s2 = new InMemorySource<>(rep, Map.of("a", new Vector(new double[]{2})));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddingsAssembler<String>().assemble(Set.of(s1, s2))
        );

        assertTrue(ex.getMessage().toLowerCase().contains("duplicate representation source"));
    }

    @Test
    void assembler_shouldFailOnEmptySourceMap() {
        Representation rep = Representation.of("full");
        RepresentationSource<String> empty = new InMemorySource<>(rep, Map.of());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddingsAssembler<String>().assemble(Set.of(empty))
        );

        assertTrue(ex.getMessage().toLowerCase().contains("empty source"));
    }

    @Test
    void assembler_shouldFailOnIdSetMismatch() {
        Representation full = Representation.of("full");
        Representation pca = Representation.of("pca");

        RepresentationSource<String> s1 = new InMemorySource<>(full, Map.of(
                "the", new Vector(new double[]{1, 2, 3})
        ));

        RepresentationSource<String> s2 = new InMemorySource<>(pca, Map.of(
                "of", new Vector(new double[]{0.1, 0.2})
        ));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddingsAssembler<String>().assemble(Set.of(s1, s2))
        );

        assertTrue(ex.getMessage().toLowerCase().contains("same id set"));
    }

    @Test
    void assembler_shouldBuildStorageWithMergedRepresentations() {
        Representation full = Representation.of("full");
        Representation pca = Representation.of("pca");

        RepresentationSource<String> s1 = new InMemorySource<>(full, Map.of(
                "the", new Vector(new double[]{1, 2, 3}),
                "of", new Vector(new double[]{4, 5, 6})
        ));

        RepresentationSource<String> s2 = new InMemorySource<>(pca, Map.of(
                "the", new Vector(new double[]{0.1, 0.2}),
                "of", new Vector(new double[]{0.3, 0.4})
        ));

        EmbeddingStorage<String> storage = new EmbeddingsAssembler<String>().assemble(Set.of(s1, s2));

        assertEquals(Set.of("the", "of"), storage.ids());
        assertTrue(storage.availableRepresentations().contains(full));
        assertTrue(storage.availableRepresentations().contains(pca));

        assertEquals(3, storage.require("the", full).dim());
        assertEquals(2, storage.require("the", pca).dim());
    }

    // -----------------------------
    // Optional End-to-End tests (real files under /data)
    // -----------------------------

    @Test
    void endToEnd_shouldLoadFromProjectDataFolder_ifFilesExist() throws IOException {
        Path fullPath = Paths.get("data", "full_vectors.json");
        Path pcaPath = Paths.get("data", "pca_vectors.json");

        assumeTrue(Files.exists(fullPath) && Files.isRegularFile(fullPath),
                "Skipping E2E: data/full_vectors.json not found");
        assumeTrue(Files.exists(pcaPath) && Files.isRegularFile(pcaPath),
                "Skipping E2E: data/pca_vectors.json not found");

        Representation full = Representation.of("full");
        Representation pca = Representation.of("pca");
        JsonFormat format = new JsonFormat("word", "vector");

        JsonSource<String> fullSrc = new JsonSource<>(full, () -> Files.newInputStream(fullPath), format, s -> s);
        JsonSource<String> pcaSrc = new JsonSource<>(pca, () -> Files.newInputStream(pcaPath), format, s -> s);

        EmbeddingStorage<String> storage = new EmbeddingsAssembler<String>().assemble(Set.of(fullSrc, pcaSrc));

        assertNotNull(storage);
        assertFalse(storage.ids().isEmpty());

        // Basic sanity: sample id can fetch both reps
        String sample = storage.ids().iterator().next();
        assertNotNull(storage.require(sample, full));
        assertNotNull(storage.require(sample, pca));

        // Sources must know dimensions after load
        assertTrue(fullSrc.dimension().isPresent());
        assertTrue(pcaSrc.dimension().isPresent());

        // Optional strict expectations
        if (EXPECTED_IDS > 0) assertEquals(EXPECTED_IDS, storage.ids().size());
        if (EXPECTED_FULL_DIM > 0) assertEquals(EXPECTED_FULL_DIM, fullSrc.dimension().orElseThrow());
        if (EXPECTED_PCA_DIM > 0) assertEquals(EXPECTED_PCA_DIM, pcaSrc.dimension().orElseThrow());
    }

    // -----------------------------
    // Test utilities
    // -----------------------------

    /**
     * In-memory RepresentationSource used for assembler tests (no IO).
     */
    private static final class InMemorySource<T> implements RepresentationSource<T> {

        private final Representation rep;
        private final Map<T, Vector> map;
        private final OptionalInt dim;

        InMemorySource(Representation rep, Map<T, Vector> map) {
            this.rep = Objects.requireNonNull(rep, "rep must not be null");
            this.map = Objects.requireNonNull(map, "map must not be null");
            this.dim = computeDim(map);
        }

        @Override
        public Representation representation() {
            return rep;
        }

        @Override
        public Map<T, Vector> load() {
            return map;
        }

        @Override
        public OptionalInt dimension() {
            return dim;
        }

        private static OptionalInt computeDim(Map<?, Vector> m) {
            if (m.isEmpty()) return OptionalInt.empty();
            Vector v = m.values().iterator().next();
            return OptionalInt.of(v.dim());
        }
    }

    private static Path writeTempJson(String json) throws IOException {
        Path tmp = Files.createTempFile("embeddings-test-", ".json");
        Files.writeString(tmp, json, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        tmp.toFile().deleteOnExit();
        return tmp;
    }
}
