import org.io.*;
import org.io.json.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.model.Vector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A single test file that covers:
 * - JsonFormat
 * - Representation (flyweight/interning + normalization)
 * - JsonSource (parsing, validation, caching, immutability, dimension)
 * - PythonScriptRunner (constructor validations + optional integration run)
 *
 * Notes:
 * - Tests are written to be robust even if Vector doesn't expose its internal array.
 * - The Python runner integration tests are conditional: skipped if Python is not available.
 */
public class IoTest {
    // ----------------------------
    // Representation tests
    // ----------------------------
    @Nested
    class RepresentationTests {

        @Test
        void of_normalizesAndInterns_sameReference() {
            Representation a = Representation.of(" FULL ");
            Representation b = Representation.of("full");
            Representation c = Representation.of(" f u l l "); // internal spaces are removed

            // Flyweight behavior: same canonical name => same object reference
            assertSame(a, b);
            assertSame(b, c);

            assertEquals("full", a.name());
            assertEquals("full", a.toString());
        }

        @Test
        void of_null_throws() {
            assertThrows(IllegalArgumentException.class, () -> Representation.of(null));
        }

        @Test
        void of_blank_throws() {
            assertThrows(IllegalArgumentException.class, () -> Representation.of("   "));
        }

        @Test
        void equalsHashCode_basedOnCanonicalName() {
            Representation x = Representation.of("PCA");
            Representation y = Representation.of(" p c a ");
            assertEquals(x, y);
            assertEquals(x.hashCode(), y.hashCode());
        }
    }

    @Nested
    class RepresentationSourceContractTests {

        private RepresentationSource<String> createSource() {
            String json = """
                [
                  { "word": "the", "vector": [1.0, 2.0] },
                  { "word": "cat", "vector": [3.0, 4.0] }
                ]
                """;

            JsonSource.InputStreamSupplier supplier =
                    () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

            return new JsonSource<>(
                    Representation.of("FULL"),
                    supplier,
                    new JsonFormat("word", "vector"),
                    raw -> raw
            );
        }

        @Test
        void representation_notNull() {
            RepresentationSource<String> src = createSource();

            assertNotNull(src.representation());
            assertNotNull(src.representation().name());
            assertFalse(src.representation().name().isBlank());
        }

        @Test
        void load_returnsNonNullMap() {
            RepresentationSource<String> src = createSource();

            Map<String, Vector> map = src.load();

            assertNotNull(map);
        }

        @Test
        void load_isStableAcrossCalls() {
            RepresentationSource<String> src = createSource();

            Map<String, Vector> m1 = src.load();
            Map<String, Vector> m2 = src.load();

            assertEquals(m1, m2);
        }

        @Test
        void dimension_isStableAfterLoad() {
            RepresentationSource<String> src = createSource();

            assertTrue(src.dimension().isEmpty());

            src.load();

            assertTrue(src.dimension().isPresent());
            int dim = src.dimension().orElseThrow();

            assertTrue(dim > 0);
            assertEquals(dim, src.dimension().orElseThrow());
        }

        @Test
        void returnedMap_isImmutableOrDefensiveCopy() {
            RepresentationSource<String> src = createSource();

            Map<String, Vector> m1 = src.load();

            boolean mutationSucceeded = false;
            try {
                m1.put("__new__", new Vector(new double[]{9.0, 9.0}));
                mutationSucceeded = true;
            } catch (UnsupportedOperationException expected) {
                // good — immutable map
            }

            if (mutationSucceeded) {
                Map<String, Vector> m2 = src.load();
                assertFalse(m2.containsKey("__new__"));
            }
        }
    }




    // ----------------------------
    // JsonFormat tests
    // ----------------------------
    @Nested
    class JsonFormatTests {

        @Test
        void ctor_validFields_ok() {
            JsonFormat fmt = new JsonFormat("word", "vector");
            assertEquals("word", fmt.idField());
            assertEquals("vector", fmt.vectorField());
        }

        @Test
        void ctor_nullIdField_throws() {
            assertThrows(IllegalArgumentException.class, () -> new JsonFormat(null, "vector"));
        }

        @Test
        void ctor_blankIdField_throws() {
            assertThrows(IllegalArgumentException.class, () -> new JsonFormat("   ", "vector"));
        }

        @Test
        void ctor_nullVectorField_throws() {
            assertThrows(IllegalArgumentException.class, () -> new JsonFormat("word", null));
        }

        @Test
        void ctor_blankVectorField_throws() {
            assertThrows(IllegalArgumentException.class, () -> new JsonFormat("word", "  "));
        }
    }

    // ----------------------------
    // JsonSource tests
    // ----------------------------
    @Nested
    class JsonSourceTests {

        private JsonSource<String> sourceFromJson(
                String json,
                AtomicInteger openCounter,
                JsonFormat fmt
        ) {
            JsonSource.InputStreamSupplier supplier = () -> {
                openCounter.incrementAndGet();
                return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            };

            return new JsonSource<>(
                    Representation.of("FULL"),
                    supplier,
                    fmt,
                    raw -> raw // idParser: keep as String
            );
        }

        private static JsonFormat defaultFormat() {
            return new JsonFormat("word", "vector");
        }

        @Test
        void load_happyPath_parsesTwoItems_dimensionCached_andSkipsUnknownFields() {
            String json = """
                    [
                      { "word": "the", "vector": [1.0, 2.0], "extra": "ignored" },
                      { "word": "cat", "vector": [3.5, 4.5] }
                    ]
                    """;

            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            Map<String, org.model.Vector> map = src.load();

            assertEquals(2, map.size());
            assertTrue(map.containsKey("the"));
            assertTrue(map.containsKey("cat"));
            assertNotNull(map.get("the"));
            assertNotNull(map.get("cat"));

            // Dimension should be present after load
            OptionalInt dim = src.dimension();
            assertTrue(dim.isPresent());
            assertEquals(2, dim.getAsInt());

            // Supplier should have been opened exactly once (cached)
            assertEquals(1, opens.get());

            // Second load should return same cached map instance (unmodifiable)
            Map<String, org.model.Vector> map2 = src.load();
            assertSame(map, map2);
            assertEquals(1, opens.get());
        }

        @Test
        void load_returnsUnmodifiableMap() {
            String json = """
                    [
                      { "word": "a", "vector": [1.0] }
                    ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            Map<String, org.model.Vector> map = src.load();
            assertThrows(UnsupportedOperationException.class, () -> map.put("b", new org.model.Vector(new double[]{2.0})));
        }

        @Test
        void dimension_emptyBeforeLoad_thenPresentAfterLoad() {
            String json = """
                    [
                      { "word": "a", "vector": [1.0, 2.0, 3.0] }
                    ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            assertTrue(src.dimension().isEmpty());

            src.load();
            assertEquals(3, src.dimension().orElseThrow());
        }

        @Test
        void load_jsonMustStartWithArray_throws() {
            String json = """
                    { "word": "a", "vector": [1.0] }
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().contains("array"));
        }

        @Test
        void load_expectedObjectInsideArray_throws() {
            String json = """
                    [ 123 ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().toLowerCase().contains("expected an object"));
        }

        @Test
        void load_missingId_throws() {
            String json = """
                    [
                      { "vector": [1.0, 2.0] }
                    ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().toLowerCase().contains("missing/blank id"));
        }

        @Test
        void load_blankId_throws() {
            String json = """
                    [
                      { "word": "   ", "vector": [1.0, 2.0] }
                    ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            assertThrows(IllegalArgumentException.class, src::load);
        }

        @Test
        void load_idParserReturnsNull_throws() {
            String json = """
                    [
                      { "word": "the", "vector": [1.0] }
                    ]
                    """;

            AtomicInteger opens = new AtomicInteger(0);
            JsonSource.InputStreamSupplier supplier = () -> {
                opens.incrementAndGet();
                return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            };

            JsonSource<String> src = new JsonSource<>(
                    Representation.of("FULL"),
                    supplier,
                    defaultFormat(),
                    raw -> null // bad parser
            );

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().toLowerCase().contains("parsed id is null"));
        }

        @Test
        void load_missingVector_throws() {
            String json = """
                    [
                      { "word": "the" }
                    ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().toLowerCase().contains("missing vector"));
        }

        @Test
        void load_vectorMustBeArray_throws() {
            String json = """
                    [
                      { "word": "the", "vector": 7 }
                    ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().toLowerCase().contains("must be a json array"));
        }

        @Test
        void load_vectorArrayMustContainNumbersOnly_throws() {
            String json = """
                    [
                      { "word": "the", "vector": [1.0, "nope"] }
                    ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().toLowerCase().contains("numbers only"));
        }

        @Test
        void load_vectorMustNotBeEmpty_throws() {
            String json = """
                    [
                      { "word": "the", "vector": [] }
                    ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().toLowerCase().contains("must not be empty"));
        }

        @Test
        void load_inconsistentVectorDimensions_throws() {
            String json = """
                    [
                      { "word": "a", "vector": [1.0, 2.0] },
                      { "word": "b", "vector": [3.0] }
                    ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().toLowerCase().contains("inconsistent vector dimension"));
        }

        @Test
        void load_duplicateIds_throws() {
            String json = """
                    [
                      { "word": "dup", "vector": [1.0] },
                      { "word": "dup", "vector": [2.0] }
                    ]
                    """;
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().toLowerCase().contains("duplicate id"));
        }

        @Test
        void load_emptyArray_throws() {
            String json = "[]";
            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, defaultFormat());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, src::load);
            assertTrue(ex.getMessage().toLowerCase().contains("array is empty"));
        }

        @Test
        void load_usesCustomFieldNamesViaJsonFormat() {
            String json = """
                    [
                      { "id": "x", "vec": [9.0, 8.0] }
                    ]
                    """;

            AtomicInteger opens = new AtomicInteger(0);
            JsonSource<String> src = sourceFromJson(json, opens, new JsonFormat("id", "vec"));

            Map<String, org.model.Vector> map = src.load();
            assertEquals(1, map.size());
            assertTrue(map.containsKey("x"));
            assertEquals(2, src.dimension().orElseThrow());
        }
    }

    // ----------------------------
    // PythonScriptRunner tests
    // ----------------------------
    @Nested
    class PythonScriptRunnerTests {

        @TempDir
        Path tempDir;

        @Test
        void ctor_blankPythonExecutable_throws() {
            Path fakeScript = tempDir.resolve("script.py");
            assertThrows(IllegalArgumentException.class, () -> new PythonScriptRunner("   ", fakeScript));
        }

        @Test
        void ctor_nullScriptPath_throws() {
            assertThrows(IllegalArgumentException.class, () -> new PythonScriptRunner("python", null));
        }

        @Test
        void ctor_scriptDoesNotExist_throws() {
            Path missing = tempDir.resolve("nope.py");
            assertThrows(IllegalArgumentException.class, () -> new PythonScriptRunner("python", missing));
        }

        @Test
        void ctor_setsAbsoluteScriptPathAndWorkingDir() throws IOException {
            Path script = tempDir.resolve("ok.py");
            Files.writeString(script, "print('hi')\n", StandardCharsets.UTF_8);

            PythonScriptRunner runner = new PythonScriptRunner("python", script);

            assertTrue(runner.getScriptPath().isAbsolute());
            assertEquals(runner.getScriptPath().getParent(), runner.getWorkingDirectory());
            assertEquals("python", runner.getPythonExecutable());
        }

        @Test
        void run_successExitCode0_whenPythonAvailable() throws Exception {
            Assumptions.assumeTrue(isPythonAvailable(), "Python is not available on PATH; skipping integration test.");

            Path script = tempDir.resolve("success.py");
            Files.writeString(script, "print('ok')\n", StandardCharsets.UTF_8);

            PythonScriptRunner runner = new PythonScriptRunner("python", script);

            // Timeout guard so tests never hang forever
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                int code = runner.run();
                assertEquals(0, code);
            });
        }

        @Test
        void run_throwsIOExceptionOnNonZeroExit_whenPythonAvailable() throws Exception {
            Assumptions.assumeTrue(isPythonAvailable(), "Python is not available on PATH; skipping integration test.");

            Path script = tempDir.resolve("fail.py");
            Files.writeString(script, "import sys\nsys.exit(3)\n", StandardCharsets.UTF_8);

            PythonScriptRunner runner = new PythonScriptRunner("python", script);

            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                IOException ex = assertThrows(IOException.class, runner::run);
                assertTrue(ex.getMessage().contains("exit code=3"));
            });
        }

        private boolean isPythonAvailable() {
            try {
                Process p = new ProcessBuilder("python", "--version")
                        .redirectErrorStream(true)
                        .start();
                int code = p.waitFor();
                return code == 0;
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}
