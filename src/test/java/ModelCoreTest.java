import org.io.Representation;
import org.io.RepresentationSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.model.*;
import org.model.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for:
 * - Vector
 * - EmbeddingItem
 * - EmbeddingSingle (default methods)
 * - EmbeddingStorage (EmbeddingGroup impl)
 * - EmbeddingsAssembler
 *
 * Notes:
 * 1) Representation is not part of the uploaded files, so we create Representation instances
 *    using reflection in a robust way.
 * 2) RepresentationSource is used by EmbeddingsAssembler; we provide a fake implementation here.
 */
public class ModelCoreTest {

    // ----------------------------
    // Helpers
    // ----------------------------

    private static Vector v(double... xs) {
        return new Vector(xs);
    }

    /**
     * Create a Representation instance in a "best effort" way.
     * Supports common cases:
     * - Representation is an enum: takes the first enum constant (or by name if exists).
     * - Representation has a (String) constructor.
     * - Representation has a no-arg constructor.
     * - Representation has a static factory method like of(String) / from(String) / valueOf(String).
     *
     * If none works, tests will fail with a clear message.
     */
    private static Representation rep(String desiredName) {
        try {
            Class<?> cls = Representation.class;

            // Case 1: enum
            if (cls.isEnum()) {
                Object[] constants = cls.getEnumConstants();
                if (constants != null && constants.length > 0) {
                    // If user name matches an enum constant, try that; otherwise use the first constant.
                    try {
                        @SuppressWarnings("unchecked")
                        Class<? extends Enum> ecls = (Class<? extends Enum>) cls;
                        return Representation.of(desiredName);
                    } catch (IllegalArgumentException ignored) {
                        return (Representation) constants[0];
                    }
                }
            }

            // Case 2: static factory methods
            for (String methodName : List.of("of", "from", "valueOf")) {
                try {
                    Method m = cls.getMethod(methodName, String.class);
                    Object out = m.invoke(null, desiredName);
                    if (out instanceof Representation r) return r;
                } catch (NoSuchMethodException ignored) {
                    // keep trying
                }
            }

            // Case 3: (String) ctor
            try {
                Constructor<?> c = cls.getConstructor(String.class);
                Object out = c.newInstance(desiredName);
                return (Representation) out;
            } catch (NoSuchMethodException ignored) {
                // keep trying
            }

            // Case 4: no-arg ctor
            try {
                Constructor<?> c = cls.getConstructor();
                Object out = c.newInstance();
                return (Representation) out;
            } catch (NoSuchMethodException ignored) {
                // keep trying
            }

            fail("Could not construct org.io.Representation. " +
                    "Please ensure Representation is an enum or has a (String) or () constructor " +
                    "or a static factory (of/from/valueOf).");
            return null;

        } catch (Exception e) {
            throw new RuntimeException("Failed creating Representation via reflection", e);
        }
    }

    private static Map<Representation, Vector> repsMap(Representation r1, Vector v1) {
        Map<Representation, Vector> m = new HashMap<>();
        m.put(r1, v1);
        return m;
    }

    // Fake RepresentationSource for EmbeddingsAssembler tests
    private static final class FakeSource<T> implements RepresentationSource<T> {
        private final Representation rep;
        private final Map<T, Vector> data;
        private final OptionalInt dim;

        FakeSource(Representation rep, Map<T, Vector> data) {
            this.rep = Objects.requireNonNull(rep, "rep must not be null");
            this.data = Objects.requireNonNull(data, "data must not be null");
            this.dim = inferDimension(data);
        }

        private static OptionalInt inferDimension(Map<?, Vector> data) {
            if (data.isEmpty()) return OptionalInt.empty();
            Vector any = data.values().iterator().next();
            return OptionalInt.of(any.dim());
        }

        @Override
        public Representation representation() {
            return rep;
        }

        @Override
        public Map<T, Vector> load() {
            return data;
        }

        @Override
        public OptionalInt dimension() {
            return dim;
        }
    }

    // Minimal EmbeddingSingle implementation for default-method tests
    private static final class FakeSingle<T> implements EmbeddingSingle<T> {
        private final T id;
        private final Map<Representation, Vector> map;

        FakeSingle(T id, Map<Representation, Vector> map) {
            this.id = id;
            this.map = map;
        }

        @Override
        public T id() {
            return id;
        }

        @Override
        public Set<Representation> representations() {
            return map.keySet();
        }

        @Override
        public Optional<Vector> get(Representation rep) {
            return Optional.ofNullable(map.get(rep));
        }
    }

    // ----------------------------
    // Vector tests
    // ----------------------------
    @Nested
    class VectorTests {

        @Test
        void ctorRejectsNullAndEmpty() {
            assertThrows(IllegalArgumentException.class, () -> new Vector(null));
            assertThrows(IllegalArgumentException.class, () -> new Vector(new double[]{}));
        }

        @Test
        void isImmutableAgainstInputArrayMutation() {
            double[] raw = {1.0, 2.0, 3.0};
            Vector vec = new Vector(raw);

            raw[0] = 999.0; // mutate after construction
            assertEquals(1.0, vec.get(0), "Vector should defensively copy input array");
        }

        @Test
        void toArrayCopyIsDefensive() {
            Vector vec = v(1.0, 2.0, 3.0);
            double[] copy = vec.toArrayCopy();
            copy[1] = 777.0;
            assertEquals(2.0, vec.get(1), "toArrayCopy() must return a defensive copy");
        }

        @Test
        void getRejectsOutOfBounds() {
            Vector vec = v(1.0, 2.0);
            assertThrows(IndexOutOfBoundsException.class, () -> vec.get(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> vec.get(2));
        }

        @Test
        void dotRejectsNullAndDimMismatch() {
            Vector a = v(1, 2, 3);
            assertThrows(IllegalArgumentException.class, () -> a.dot(null));
            assertThrows(IllegalArgumentException.class, () -> a.dot(v(1, 2)));
        }

        @Test
        void dotAndNormWork() {
            Vector a = v(1, 2, 3);
            Vector b = v(4, 5, 6);

            // dot = 1*4 + 2*5 + 3*6 = 4 + 10 + 18 = 32
            assertEquals(32.0, a.dot(b), 1e-12);

            // norm^2 = 1+4+9=14 => norm = sqrt(14)
            assertEquals(Math.sqrt(14.0), a.norm(), 1e-12);
            assertEquals(14.0, a.normSquared(), 1e-12);
        }

        @Test
        void normalizedRejectsZeroVector() {
            Vector z = v(0.0, 0.0);
            assertThrows(IllegalStateException.class, z::normalized);
        }

        @Test
        void normalizedHasUnitLength() {
            Vector a = v(3.0, 4.0);
            Vector n = a.normalized();
            assertEquals(1.0, n.norm(), 1e-12);
        }

        @Test
        void addSubtractScaleWork() {
            Vector a = v(1, 2, 3);
            Vector b = v(10, 20, 30);

            Vector sum = a.add(b);       // [11,22,33]
            Vector diff = b.subtract(a); // [9,18,27]
            Vector scaled = a.scale(2);  // [2,4,6]

            assertArrayEquals(new double[]{11, 22, 33}, sum.toArrayCopy(), 1e-12);
            assertArrayEquals(new double[]{9, 18, 27}, diff.toArrayCopy(), 1e-12);
            assertArrayEquals(new double[]{2, 4, 6}, scaled.toArrayCopy(), 1e-12);
        }

        @Test
        void averageRejectsNullEmptyAndDimMismatch() {
            assertThrows(IllegalArgumentException.class, () -> Vector.average(null));
            assertThrows(IllegalArgumentException.class, () -> Vector.average(List.of()));

            Vector a = v(1, 2);
            Vector b = v(3, 4, 5);
            assertThrows(IllegalArgumentException.class, () -> Vector.average(List.of(a, b)));
        }

        @Test
        void averageComputesMean() {
            Vector a = v(1, 2);
            Vector b = v(3, 4);
            Vector c = v(5, 6);

            Vector avg = Vector.average(List.of(a, b, c)); // [(1+3+5)/3, (2+4+6)/3] = [3,4]
            assertArrayEquals(new double[]{3.0, 4.0}, avg.toArrayCopy(), 1e-12);
        }

        @Test
        void equalsAndHashCodeAreConsistent() {
            Vector a1 = v(1, 2, 3);
            Vector a2 = v(1, 2, 3);
            Vector b = v(1, 2, 4);

            assertEquals(a1, a2);
            assertEquals(a1.hashCode(), a2.hashCode());
            assertNotEquals(a1, b);
        }
    }

    // ----------------------------
    // EmbeddingSingle default-method tests
    // ----------------------------
    @Nested
    class EmbeddingSingleDefaultMethodTests {

        @Test
        void hasAndRequireBehaveCorrectly() {
            Representation rA = rep("A");
            Representation rB = rep("B");

            Map<Representation, Vector> m = new HashMap<>();
            m.put(rA, v(1, 2, 3));

            EmbeddingSingle<String> s = new FakeSingle<>("word", m);

            assertTrue(s.has(rA));
            assertFalse(s.has(rB));

            assertEquals(v(1, 2, 3), s.require(rA));
            assertThrows(IllegalArgumentException.class, () -> s.require(rB));
        }

        @Test
        void hasRejectsNullRep() {
            Representation rA = rep("A");
            EmbeddingSingle<String> s = new FakeSingle<>("x", repsMap(rA, v(1, 1)));

            assertThrows(NullPointerException.class, () -> s.has(null));
        }

        @Test
        void requireRejectsNullRep() {
            Representation rA = rep("A");
            EmbeddingSingle<String> s = new FakeSingle<>("x", repsMap(rA, v(1, 1)));

            assertThrows(NullPointerException.class, () -> s.require(null));
        }
    }

    // ----------------------------
    // EmbeddingItem tests
    // ----------------------------
    @Nested
    class EmbeddingItemTests {

        @Test
        void ctorRejectsNullsAndEmptyMap() {
            Representation r = rep("A");

            assertThrows(NullPointerException.class, () -> new EmbeddingItem<>(null, repsMap(r, v(1))));
            assertThrows(NullPointerException.class, () -> new EmbeddingItem<>("id", null));
            assertThrows(IllegalArgumentException.class, () -> new EmbeddingItem<>("id", Map.of()));
        }

        @Test
        void exposesIdAndRepresentationsAndGet() {
            Representation rA = rep("A");
            Representation rB = rep("B");

            Map<Representation, Vector> m = new HashMap<>();
            m.put(rA, v(1, 2));
            m.put(rB, v(3, 4));

            EmbeddingItem<String> item = new EmbeddingItem<>("hello", m);

            assertEquals("hello", item.id());
            assertEquals(Set.of(rA, rB), item.representations());

            assertTrue(item.get(rA).isPresent());
            assertEquals(v(1, 2), item.get(rA).get());

            assertTrue(item.get(rB).isPresent());
            assertEquals(v(3, 4), item.get(rB).get());
        }

        @Test
        void getRejectsNullRepresentation() {
            Representation rA = rep("A");
            EmbeddingItem<String> item = new EmbeddingItem<>("x", repsMap(rA, v(1)));

            assertThrows(NullPointerException.class, () -> item.get(null));
        }

        @Test
        void repsViewIsUnmodifiable() {
            Representation rA = rep("A");
            EmbeddingItem<String> item = new EmbeddingItem<>("x", repsMap(rA, v(1)));

            Map<Representation, Vector> view = item.repsView();
            assertThrows(UnsupportedOperationException.class, () -> view.put(rep("B"), v(2)));
        }
    }

    // ----------------------------
    // EmbeddingStorage tests
    // ----------------------------
    @Nested
    class EmbeddingStorageTests {

        @Test
        void ctorRejectsNullAndEmpty() {
            assertThrows(NullPointerException.class, () -> new EmbeddingStorage<>(null));
            assertThrows(IllegalArgumentException.class, () -> new EmbeddingStorage<>(Map.of()));
        }

        @Test
        void ctorEnforcesStrictRepresentationSet() {
            Representation rA = rep("A");
            Representation rB = rep("B");

            EmbeddingSingle<String> s1 = new EmbeddingItem<>("id1", Map.of(rA, v(1, 1), rB, v(2, 2)));
            EmbeddingSingle<String> s2 = new EmbeddingItem<>("id2", Map.of(rA, v(3, 3))); // missing rB

            Map<String, EmbeddingSingle<String>> byId = new HashMap<>();
            byId.put("id1", s1);
            byId.put("id2", s2);

            assertThrows(IllegalArgumentException.class, () -> new EmbeddingStorage<>(byId),
                    "Storage should reject items with inconsistent representation sets");
        }

        @Test
        void basicQueriesWork() {
            Representation rA = rep("A");
            Representation rB = rep("B");

            EmbeddingSingle<String> s1 = new EmbeddingItem<>("id1", Map.of(rA, v(1, 1), rB, v(2, 2)));
            EmbeddingSingle<String> s2 = new EmbeddingItem<>("id2", Map.of(rA, v(3, 3), rB, v(4, 4)));

            EmbeddingStorage<String> store = new EmbeddingStorage<>(Map.of(
                    "id1", s1,
                    "id2", s2
            ));

            assertTrue(store.contains("id1"));
            assertFalse(store.contains("missing"));

            assertTrue(store.find("id2").isPresent());
            assertFalse(store.find("missing").isPresent());

            assertEquals(s1, store.requireSingle("id1"));
            assertThrows(IllegalArgumentException.class, () -> store.requireSingle("missing"));

            assertEquals(v(4, 4), store.require("id2", rB));
            assertThrows(NullPointerException.class, () -> store.require("id2", null));

            assertEquals(Set.of("id1", "id2"), store.ids());
            assertEquals(Set.of(rA, rB), store.availableRepresentations());
        }
    }

    // ----------------------------
    // EmbeddingsAssembler tests
    // ----------------------------
    @Nested
    class EmbeddingsAssemblerTests {

        @Test
        void assembleRejectsNullAndEmptySources() {
            EmbeddingsAssembler<String> asm = new EmbeddingsAssembler<>();
            assertThrows(NullPointerException.class, () -> asm.assemble(null));
            assertThrows(IllegalArgumentException.class, () -> asm.assemble(Set.of()));
        }

        @Test
        void assembleRejectsDuplicateRepresentations() {
            EmbeddingsAssembler<String> asm = new EmbeddingsAssembler<>();
            Representation rA = rep("A");

            Map<String, Vector> m1 = Map.of("x", v(1, 1));
            Map<String, Vector> m2 = Map.of("x", v(2, 2));

            FakeSource<String> s1 = new FakeSource<>(rA, m1);
            FakeSource<String> s2 = new FakeSource<>(rA, m2); // same rep -> duplicate

            assertThrows(IllegalArgumentException.class, () -> asm.assemble(Set.of(s1, s2)));
        }

        @Test
        void assembleRejectsEmptySourceMap() {
            EmbeddingsAssembler<String> asm = new EmbeddingsAssembler<>();
            Representation rA = rep("A");

            FakeSource<String> s1 = new FakeSource<>(rA, Map.of());
            assertThrows(IllegalArgumentException.class, () -> asm.assemble(Set.of(s1)));
        }

        @Test
        void assembleRejectsMismatchedIdSets() {
            EmbeddingsAssembler<String> asm = new EmbeddingsAssembler<>();

            Representation rA = rep("A");
            Representation rB = rep("B");

            FakeSource<String> s1 = new FakeSource<>(rA, Map.of(
                    "x", v(1, 1),
                    "y", v(2, 2)
            ));

            FakeSource<String> s2 = new FakeSource<>(rB, Map.of(
                    "x", v(10, 10) // missing "y"
            ));

            assertThrows(IllegalArgumentException.class, () -> asm.assemble(Set.of(s1, s2)));
        }

        @Test
        void assembleBuildsStorageWithMergedRepresentations() {
            EmbeddingsAssembler<String> asm = new EmbeddingsAssembler<>();

            Representation rA = rep("A");
            Representation rB = rep("B");

            FakeSource<String> s1 = new FakeSource<>(rA, new LinkedHashMap<>(Map.of(
                    "x", v(1, 1),
                    "y", v(2, 2)
            )));

            FakeSource<String> s2 = new FakeSource<>(rB, new LinkedHashMap<>(Map.of(
                    "x", v(10, 10),
                    "y", v(20, 20)
            )));

            EmbeddingStorage<String> store = asm.assemble(Set.of(s1, s2));

            assertEquals(Set.of("x", "y"), store.ids());
            assertEquals(Set.of(rA, rB), store.availableRepresentations());

            assertEquals(v(1, 1), store.require("x", rA));
            assertEquals(v(10, 10), store.require("x", rB));

            assertEquals(v(2, 2), store.require("y", rA));
            assertEquals(v(20, 20), store.require("y", rB));
        }
    }

    // -----------------------------------------------------------------------------
// 1) RepresentationSource<T> contract tests (BASE - NO @Nested HERE)
// -----------------------------------------------------------------------------
    abstract class RepresentationSourceContractTestsBase {

        protected abstract RepresentationSource<String> createSource();

        @Test
        void representationIsNotNull() {
            RepresentationSource<String> src = createSource();
            assertNotNull(src.representation(), "representation() must not return null");
        }

        @Test
        void loadIsNotNull() {
            RepresentationSource<String> src = createSource();
            assertNotNull(src.load(), "load() must not return null");
        }

        @Test
        void loadIsNotEmpty() {
            RepresentationSource<String> src = createSource();
            assertFalse(src.load().isEmpty(), "load() should not be empty (remove this test if empty is allowed)");
        }

        @Test
        void dimensionMatchesVectorsWhenPresent() {
            RepresentationSource<String> src = createSource();
            Map<String, Vector> data = src.load();

            OptionalInt d = src.dimension();
            if (d.isPresent()) {
                int expected = d.getAsInt();
                for (Map.Entry<String, Vector> e : data.entrySet()) {
                    assertEquals(expected, e.getValue().dim(),
                            "All vectors must match dimension() when it is present. Key=" + e.getKey());
                }
            }
        }

        @Test
        void loadIsStableAcrossCalls() {
            RepresentationSource<String> src = createSource();
            Map<String, Vector> first = src.load();
            Map<String, Vector> second = src.load();

            assertEquals(first.keySet(), second.keySet(), "load() keys should be stable across calls");
            for (String k : first.keySet()) {
                assertEquals(first.get(k), second.get(k), "Vector for key '" + k + "' should be stable across calls");
            }
        }
    }

    // Runner (THIS is @Nested and concrete, so JUnit can instantiate it)
    @Nested
    class FakeSourceContract extends RepresentationSourceContractTestsBase {

        @Override
        protected RepresentationSource<String> createSource() {
            Representation r = Representation.of("TEST_REP");
            Map<String, Vector> data = new LinkedHashMap<>();
            data.put("x", new Vector(new double[]{1, 1, 1}));
            data.put("y", new Vector(new double[]{2, 2, 2}));

            return new FakeSource<>(r, data); // assumes you already have FakeSource in the test file
        }
    }


    // -----------------------------------------------------------------------------
// 2) EmbeddingSingle<T> contract tests (BASE - NO @Nested HERE)
// -----------------------------------------------------------------------------
    abstract class EmbeddingSingleContractTestsBase {

        protected abstract EmbeddingSingle<String> createWithRep(Representation rep);
        protected abstract EmbeddingSingle<String> createWithoutRep(Representation missingRep);

        @Test
        void idIsNotNull() {
            EmbeddingSingle<String> item = createWithRep(Representation.of("full"));
            assertNotNull(item.id(), "id() must not return null");
        }

        @Test
        void representationsIsNotNullAndNotEmpty() {
            EmbeddingSingle<String> item = createWithRep(Representation.of("full"));
            assertNotNull(item.representations(), "representations() must not return null");
            assertFalse(item.representations().isEmpty(), "representations() must not be empty");
        }

        @Test
        void getRejectsNullRepresentation() {
            EmbeddingSingle<String> item = createWithRep(Representation.of("full"));
            assertThrows(NullPointerException.class, () -> item.get(null));
        }

        @Test
        void hasRejectsNullRepresentation() {
            EmbeddingSingle<String> item = createWithRep(Representation.of("full"));
            assertThrows(NullPointerException.class, () -> item.has(null));
        }

        @Test
        void requireRejectsNullRepresentation() {
            EmbeddingSingle<String> item = createWithRep(Representation.of("full"));
            assertThrows(NullPointerException.class, () -> item.require(null));
        }

        @Test
        void hasIsConsistentWithRepresentationsSet() {
            Representation rep = Representation.of("full");
            EmbeddingSingle<String> item = createWithRep(rep);

            assertTrue(item.representations().contains(rep));
            assertTrue(item.has(rep));
        }

        @Test
        void getAndRequireBehaveWhenPresent() {
            Representation rep = Representation.of("full");
            EmbeddingSingle<String> item = createWithRep(rep);

            assertTrue(item.get(rep).isPresent());
            assertEquals(item.get(rep).orElseThrow(), item.require(rep));
        }

        @Test
        void getAndRequireBehaveWhenMissing() {
            Representation missing = Representation.of("missing_rep");
            EmbeddingSingle<String> item = createWithoutRep(missing);

            assertFalse(item.representations().contains(missing));
            assertTrue(item.get(missing).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> item.require(missing));
        }
    }

    // Runner for your current implementation (EmbeddingItem)
    @Nested
    class EmbeddingItemContract extends EmbeddingSingleContractTestsBase {

        @Override
        protected EmbeddingSingle<String> createWithRep(Representation rep) {
            return new EmbeddingItem<>("id1", Map.of(rep, new Vector(new double[]{1, 2, 3})));
        }

        @Override
        protected EmbeddingSingle<String> createWithoutRep(Representation missingRep) {
            Representation other = Representation.of("other");
            return new EmbeddingItem<>("id2", Map.of(other, new Vector(new double[]{9, 9, 9})));
        }
    }


    // -----------------------------------------------------------------------------
// 3) EmbeddingGroup<T> contract tests (BASE - NO @Nested HERE)
// -----------------------------------------------------------------------------
    abstract class EmbeddingGroupContractTestsBase {

        protected abstract EmbeddingGroup<String> createGroup();

        @Test
        void idsIsNotNullAndNotEmpty() {
            EmbeddingGroup<String> g = createGroup();
            assertNotNull(g.ids(), "ids() must not return null");
            assertFalse(g.ids().isEmpty(), "ids() must not be empty (remove if empty groups are allowed)");
        }

        @Test
        void availableRepresentationsIsNotNullAndNotEmpty() {
            EmbeddingGroup<String> g = createGroup();
            assertNotNull(g.availableRepresentations(), "availableRepresentations() must not return null");
            assertFalse(g.availableRepresentations().isEmpty(), "availableRepresentations() must not be empty");
        }

        @Test
        void containsAndFindAreConsistent() {
            EmbeddingGroup<String> g = createGroup();
            String existing = g.ids().iterator().next();
            String missing = existing + "__MISSING__";

            assertTrue(g.contains(existing));
            assertTrue(g.find(existing).isPresent());

            assertFalse(g.contains(missing));
            assertTrue(g.find(missing).isEmpty());
        }

        @Test
        void requireSingleMatchesFindForExistingId() {
            EmbeddingGroup<String> g = createGroup();
            String existing = g.ids().iterator().next();

            assertEquals(g.find(existing).orElseThrow(), g.requireSingle(existing));
        }

        @Test
        void requireSingleThrowsForMissingId() {
            EmbeddingGroup<String> g = createGroup();
            String existing = g.ids().iterator().next();
            String missing = existing + "__MISSING__";

            assertThrows(IllegalArgumentException.class, () -> g.requireSingle(missing));
        }

        @Test
        void requireDelegatesToSingleRequire() {
            EmbeddingGroup<String> g = createGroup();
            String existing = g.ids().iterator().next();
            Representation rep = g.availableRepresentations().iterator().next();

            assertEquals(g.requireSingle(existing).require(rep), g.require(existing, rep));
        }

        @Test
        void requireRejectsNullRepresentation() {
            EmbeddingGroup<String> g = createGroup();
            String existing = g.ids().iterator().next();

            assertThrows(NullPointerException.class, () -> g.require(existing, null));
        }
    }

    // Runner for your current implementation (EmbeddingStorage)
    @Nested
    class EmbeddingStorageContract extends EmbeddingGroupContractTestsBase {

        @Override
        protected EmbeddingGroup<String> createGroup() {
            Representation rA = Representation.of("A");
            Representation rB = Representation.of("B");

            EmbeddingSingle<String> s1 = new EmbeddingItem<>("id1", Map.of(
                    rA, new Vector(new double[]{1, 1}),
                    rB, new Vector(new double[]{2, 2})
            ));

            EmbeddingSingle<String> s2 = new EmbeddingItem<>("id2", Map.of(
                    rA, new Vector(new double[]{3, 3}),
                    rB, new Vector(new double[]{4, 4})
            ));

            return new EmbeddingStorage<>(Map.of(
                    "id1", s1,
                    "id2", s2
            ));
        }
    }
}