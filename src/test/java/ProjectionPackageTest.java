import org.io.Representation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.model.EmbeddingGroup;
import org.model.EmbeddingSingle;
import org.model.Vector;
import org.projection.CustomProjectionService;
import org.projection.ProjectionAxis;
import org.projection.ProjectionScore;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for:
 *  - ProjectionAxis
 *  - CustomProjectionService
 *
 * Notes:
 * 1) This file is intentionally "robust" against different Vector factory/constructor styles.
 *    The helper vec(...) tries a few common patterns via reflection.
 *
 * 2) Representation is also obtained safely: we pick the first enum constant, so you don't have to hardcode FULL.
 *
 * 3) We provide a small FakeEmbeddingGroup that implements the minimal behavior needed by CustomProjectionService.
 */
public class ProjectionPackageTest {

    // -------------------------------------------------------------------------
    // ProjectionAxis tests
    // -------------------------------------------------------------------------

    @Nested
    class ProjectionAxisTests {

        @Test
        @DisplayName("between(A,B): direction is along (B-A); coordinate/orthogonal distance behave correctly")
        void betweenAxis_basicGeometry() {
            Vector a = vec(0.0, 0.0);
            Vector b = vec(2.0, 0.0);

            ProjectionAxis axis = ProjectionAxis.between(a, b);

            // Coordinate of A should be 0 (because origin=A)
            assertEquals(0.0, axis.coordinateOf(a), 1e-9);

            // Coordinate of B should be ||B-A|| = 2
            assertEquals(2.0, axis.coordinateOf(b), 1e-9);

            // A point 1 unit above the axis should have orthogonal distance 1
            Vector p = vec(0.0, 1.0);
            assertEquals(1.0, axis.orthogonalDistanceOf(p), 1e-9);
        }

        @Test
        @DisplayName("centeredBetween(A,B): origin is midpoint; A becomes negative and B positive")
        void centeredAxis_isSymmetric() {
            Vector a = vec(0.0, 0.0);
            Vector b = vec(2.0, 0.0);

            ProjectionAxis axis = ProjectionAxis.centeredBetween(a, b);

            // Midpoint origin means A is at -1 and B is at +1 along the axis direction
            assertEquals(-1.0, axis.coordinateOf(a), 1e-9);
            assertEquals(1.0, axis.coordinateOf(b), 1e-9);
        }

        @Test
        @DisplayName("between(A,A) should throw (zero direction)")
        void betweenAxis_sameAnchorsThrows() {
            Vector a = vec(1.0, 2.0);
            assertThrows(IllegalArgumentException.class, () -> ProjectionAxis.between(a, a));
        }
    }

    // -------------------------------------------------------------------------
    // CustomProjectionService tests
    // -------------------------------------------------------------------------

    @Nested
    class CustomProjectionServiceTests {

        @Test
        @DisplayName("semanticScale(A,B,includeAnchors=true) sorts by coordinate (A-like -> B-like)")
        void semanticScale_sortsByCoordinate() {
            Representation rep = anyRepresentation();

            // A=(0,0), C=(1,0), B=(2,0) all on-axis.
            FakeEmbeddingGroup<String> group = new FakeEmbeddingGroup<>();
            group.put("A", vec(0.0, 0.0));
            group.put("C", vec(1.0, 0.0));
            group.put("B", vec(2.0, 0.0));

            CustomProjectionService<String> svc = new CustomProjectionService<>(group, rep);

            List<ProjectionScore<String>> scale = svc.semanticScale("A", "B", true);

            assertEquals(List.of("A", "C", "B"), idsOf(scale));
            // sanity: monotonic coordinates
            assertTrue(scale.get(0).coordinate() <= scale.get(1).coordinate());
            assertTrue(scale.get(1).coordinate() <= scale.get(2).coordinate());
        }

        @Test
        @DisplayName("semanticScale(A,B,includeAnchors=false) excludes A and B")
        void semanticScale_excludesAnchors() {
            Representation rep = anyRepresentation();

            FakeEmbeddingGroup<String> group = new FakeEmbeddingGroup<>();
            group.put("A", vec(0.0, 0.0));
            group.put("C", vec(1.0, 0.0));
            group.put("B", vec(2.0, 0.0));

            CustomProjectionService<String> svc = new CustomProjectionService<>(group, rep);

            List<ProjectionScore<String>> scale = svc.semanticScale("A", "B", false);

            assertEquals(List.of("C"), idsOf(scale));
        }

        @Test
        @DisplayName("cleanSemanticScale keeps N closest-to-axis by orthogonal distance, then sorts by coordinate")
        void cleanSemanticScale_keepsClosestToAxis() {
            Representation rep = anyRepresentation();

            // Axis A->B is horizontal:
            // A=(0,0), B=(2,0)
            // C=(1,0) on-axis (orth=0)
            // D=(1,10) far off-axis (orth~10)
            // E=(1,0.5) near-axis (orth~0.5)
            FakeEmbeddingGroup<String> group = new FakeEmbeddingGroup<>();
            group.put("A", vec(0.0, 0.0));
            group.put("B", vec(2.0, 0.0));
            group.put("C", vec(1.0, 0.0));
            group.put("D", vec(1.0, 10.0));
            group.put("E", vec(1.0, 0.5));

            CustomProjectionService<String> svc = new CustomProjectionService<>(group, rep);

            // keepClosest=4 will likely drop only the worst one if needed
            // But note: the method projects ALL words first (including anchors),
            // then takes the top N by orth distance.
            List<ProjectionScore<String>> cleaned = svc.cleanSemanticScale("A", "B", 4, true);

            List<String> ids = idsOf(cleaned);

            // We expect D to be the first candidate to drop because it's far from the axis.
            assertFalse(ids.contains("D"), "D should be dropped as far off-axis when keepClosest is limited");

            // Remaining should still be sorted by coordinate
            assertTrue(isSortedByCoordinate(cleaned));
        }

        @Test
        @DisplayName("cleanScaleByPurity keeps topN by purity and then sorts by coordinate")
        void cleanScaleByPurity_keepsBestAligned() {
            Representation rep = anyRepresentation();

            FakeEmbeddingGroup<String> group = new FakeEmbeddingGroup<>();
            group.put("A", vec(0.0, 0.0));
            group.put("B", vec(2.0, 0.0));

            // Strongly on-axis and far along: high |t|, low orth -> high purity
            group.put("X", vec(10.0, 0.0));

            // Off-axis: similar t but high orth -> lower purity
            group.put("Y", vec(10.0, 5.0));

            CustomProjectionService<String> svc = new CustomProjectionService<>(group, rep);

            List<ProjectionScore<String>> best = svc.cleanScaleByPurity("A", "B", 2, true);

            List<String> ids = idsOf(best);
            assertTrue(ids.contains("X"), "X should survive topN purity filtering");
            assertFalse(ids.contains("Y"), "Y should likely be filtered out due to lower purity (if topN is small)");

            assertTrue(isSortedByCoordinate(best));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Tries to build a Vector from doubles using reflection to match your project's Vector API.
     *
     * Supported common patterns (in this order):
     *  - Vector.of(double...)
     *  - Vector.from(double...)
     *  - new Vector(double...)
     *  - new Vector(double[])
     *
     * If none exists, the test will fail with a helpful message.
     */
    private static Vector vec(double... xs) {
        Objects.requireNonNull(xs, "xs");

        // 1) static Vector of(double...)
        Vector v = tryStaticVarargsFactory("of", xs);
        if (v != null) return v;

        // 2) static Vector from(double...)
        v = tryStaticVarargsFactory("from", xs);
        if (v != null) return v;

        // 3) constructor Vector(double...)
        v = tryVarargsConstructor(xs);
        if (v != null) return v;

        // 4) constructor Vector(double[])
        v = tryArrayConstructor(xs);
        if (v != null) return v;

        fail("""
             Could not construct org.model.Vector.
             Please add one of these APIs (any one is enough), then re-run tests:
               - static Vector of(double...)
               - static Vector from(double...)
               - public Vector(double...)
               - public Vector(double[])
             """);
        return null; // unreachable
    }

    private static Vector tryStaticVarargsFactory(String methodName, double... xs) {
        try {
            // method(double...) compiles as method(double[])
            Method m = Vector.class.getMethod(methodName, double[].class);
            Object out = m.invoke(null, (Object) xs);
            return (Vector) out;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Exception e) {
            fail("Vector." + methodName + "(double...) exists but failed at runtime: " + e);
            return null;
        }
    }

    private static Vector tryVarargsConstructor(double... xs) {
        try {
            Constructor<Vector> c = Vector.class.getConstructor(double[].class);
            // If they wrote public Vector(double...) it is also (double[]) at runtime.
            return c.newInstance((Object) xs);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Exception e) {
            fail("Vector constructor (double...) / (double[]) exists but failed at runtime: " + e);
            return null;
        }
    }

    private static Vector tryArrayConstructor(double... xs) {
        // This is the same signature as above at runtime, but we keep it separate for clarity.
        return tryVarargsConstructor(xs);
    }

    /**
     * Avoid hardcoding Representation.FULL.
     * We pick the first enum constant (whatever your project uses).
     */
    private static Representation anyRepresentation() {
        // Representation is a Flyweight class (not an enum).
        // Use a stable canonical name that will always be accepted.
        return Representation.of("full");
    }

    private static <T> List<T> idsOf(List<ProjectionScore<T>> scores) {
        List<T> out = new ArrayList<>(scores.size());
        for (ProjectionScore<T> s : scores) out.add(s.id());
        return out;
    }

    private static <T> boolean isSortedByCoordinate(List<ProjectionScore<T>> scores) {
        for (int i = 1; i < scores.size(); i++) {
            if (scores.get(i - 1).coordinate() > scores.get(i).coordinate()) return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // FakeEmbeddingGroup: a minimal in-memory group for testing the service
    // -------------------------------------------------------------------------

    /**
     * A tiny in-memory EmbeddingGroup implementation for tests.
     * It stores vectors by ID.
     *
     * IMPORTANT:
     * Your EmbeddingGroup interface might have more methods.
     * If compilation complains, implement the missing methods here in the simplest correct way.
     */
    private static final class FakeEmbeddingGroup<T> implements EmbeddingGroup<T> {

        private final Map<T, Vector> vectorsById = new LinkedHashMap<>();
        private final Set<Representation> availableReps;

        /**
         * By default, we allow any representation name (because Representation is NOT an enum).
         * That means tests won't fail just because a rep wasn't pre-registered.
         */
        FakeEmbeddingGroup() {
            // Empty set means: "no restriction" (accept any rep).
            this.availableReps = Collections.emptySet();
        }

        /**
         * If you want strict tests, pass a set of allowed representations.
         * Example:
         *   new FakeEmbeddingGroup<>(Set.of(Representation.of("full")))
         */
        FakeEmbeddingGroup(Set<Representation> availableReps) {
            Objects.requireNonNull(availableReps, "availableReps must not be null");
            this.availableReps = Collections.unmodifiableSet(new LinkedHashSet<>(availableReps));
        }

        void put(T id, Vector v) {
            vectorsById.put(Objects.requireNonNull(id, "id must not be null"),
                    Objects.requireNonNull(v, "vector must not be null"));
        }

        @Override
        public boolean contains(T id) {
            return vectorsById.containsKey(id);
        }

        @Override
        public Optional<EmbeddingSingle<T>> find(T id) {
            if (!contains(id)) return Optional.empty();
            return Optional.of(fakeSingle(id));
        }

        @Override
        public EmbeddingSingle<T> requireSingle(T id) {
            if (!contains(id)) throw new NoSuchElementException("Unknown id: " + id);
            return fakeSingle(id);
        }

        @Override
        public Vector require(T id, Representation rep) {
            Objects.requireNonNull(rep, "rep must not be null");

            // If availableReps is non-empty, enforce it; otherwise accept any rep.
            if (!availableReps.isEmpty() && !availableReps.contains(rep)) {
                throw new IllegalArgumentException("Representation not available in this group: " + rep);
            }

            Vector v = vectorsById.get(id);
            if (v == null) throw new NoSuchElementException("Unknown id: " + id);
            return v;
        }

        @Override
        public Set<T> ids() {
            return Collections.unmodifiableSet(vectorsById.keySet());
        }

        @Override
        public Set<Representation> availableRepresentations() {
            // If empty => we mean "any representation is accepted".
            return availableReps;
        }

        // -------------------------------------------------------------------------
        // Dynamic fake EmbeddingSingle<T> (so we don't need to know its full API)
        // -------------------------------------------------------------------------

        @SuppressWarnings("unchecked")
        private EmbeddingSingle<T> fakeSingle(T id) {
            return (EmbeddingSingle<T>) Proxy.newProxyInstance(
                    EmbeddingSingle.class.getClassLoader(),
                    new Class<?>[]{EmbeddingSingle.class},
                    (Object proxy, Method method, Object[] args) -> {
                        String name = method.getName();

                        if (name.equals("toString")) return "FakeEmbeddingSingle(" + id + ")";
                        if (name.equals("hashCode")) return Objects.hashCode(id);
                        if (name.equals("equals")) return proxy == args[0];

                        // Common "id" method patterns
                        if ((name.equals("id") || name.equals("getId")) && method.getParameterCount() == 0) {
                            return id;
                        }

                        // Common "vector by rep" patterns
                        if (method.getParameterCount() == 1
                                && method.getParameterTypes()[0] == Representation.class
                                && method.getReturnType() == Vector.class) {
                            return require(id, (Representation) args[0]);
                        }

                        // Safe defaults for primitives
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == byte.class) return (byte) 0;
                        if (rt == short.class) return (short) 0;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        if (rt == float.class) return 0f;
                        if (rt == double.class) return 0d;
                        if (rt == char.class) return '\0';

                        return null;
                    }
            );
        }
    }
}