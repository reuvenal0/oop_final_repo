import org.io.Representation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.model.EmbeddingGroup;
import org.model.Vector;
import org.metrics.*;

import java.lang.reflect.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full test suite for:
 * - DistanceMetric
 * - CosineDistance
 * - EuclideanDistance
 * - Neighbor
 * - NearestNeighbors
 *
 * Adapted specifically for:
 * - Representation being a Flyweight class with Representation.of(String)
 * - No enum assumptions
 */
public class MetricsAndNeighborsTest {

    /* ============================================================
       Helpers
       ============================================================ */

    /**
     * Create a Vector instance from raw doubles.
     * Adjust here ONLY if Vector API changes.
     */
    private static Vector v(double... values) {
        try {
            Constructor<Vector> c = Vector.class.getDeclaredConstructor(double[].class);
            c.setAccessible(true);
            return c.newInstance((Object) values);
        } catch (ReflectiveOperationException e) {
            fail("Could not construct Vector(double[]): " + e.getMessage());
            return null;
        }
    }

    /**
     * Canonical representation used across tests.
     */
    private static Representation rep() {
        return Representation.of("FULL");
    }

    /**
     * Minimal EmbeddingGroup test double using Dynamic Proxy.
     * Supports ONLY what NearestNeighbors actually uses.
     */
    @SuppressWarnings("unchecked")
    private static <T> EmbeddingGroup<T> group(Map<T, Vector> data) {

        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();

            if (name.equals("ids")) {
                return data.keySet();
            }

            if (name.equals("require")) {
                T id = (T) args[0];
                if (!data.containsKey(id)) {
                    throw new IllegalArgumentException("Unknown id: " + id);
                }
                return data.get(id);
            }

            throw new UnsupportedOperationException(
                    "Method not supported in test proxy: " + method
            );
        };

        return (EmbeddingGroup<T>) Proxy.newProxyInstance(
                EmbeddingGroup.class.getClassLoader(),
                new Class<?>[]{EmbeddingGroup.class},
                handler
        );
    }

    private static void assertSorted(List<Neighbor<String>> neighbors) {
        for (int i = 1; i < neighbors.size(); i++) {
            assertTrue(
                    neighbors.get(i - 1).distance() <= neighbors.get(i).distance(),
                    "Neighbors not sorted by ascending distance"
            );
        }
    }

    /* ============================================================
       Neighbor
       ============================================================ */

    @Nested
    @DisplayName("Neighbor")
    class NeighborTests {

        @Test
        void storesIdAndDistance() {
            Neighbor<String> n = new Neighbor<>("word", 0.5);
            assertEquals("word", n.id());
            assertEquals(0.5, n.distance(), 1e-12);
        }
    }

    /* ============================================================
       EuclideanDistance
       ============================================================ */

    @Nested
    @DisplayName("EuclideanDistance")
    class EuclideanDistanceTests {

        private final DistanceMetric metric = new EuclideanDistance();

        @Test
        void nameIsEuclidean() {
            assertEquals("euclidean", metric.name());
        }

        @Test
        void identicalVectorsHaveZeroDistance() {
            Vector a = v(1, 2, 3);
            Vector b = v(1, 2, 3);
            assertEquals(0.0, metric.distance(a, b), 1e-12);
        }

        @Test
        void knownDistanceExample() {
            Vector a = v(0, 0);
            Vector b = v(3, 4);
            assertEquals(5.0, metric.distance(a, b), 1e-12);
        }

        @Test
        void throwsOnDimensionMismatch() {
            assertThrows(IllegalArgumentException.class,
                    () -> metric.distance(v(1, 2), v(1, 2, 3)));
        }
    }

    /* ============================================================
       CosineDistance
       ============================================================ */

    @Nested
    @DisplayName("CosineDistance")
    class CosineDistanceTests {

        private final DistanceMetric metric = new CosineDistance();

        @Test
        void nameIsCosine() {
            assertEquals("cosine", metric.name());
        }

        @Test
        void identicalVectorsHaveZeroDistance() {
            Vector a = v(1, 2, 3);
            Vector b = v(1, 2, 3);
            assertEquals(0.0, metric.distance(a, b), 1e-12);
        }

        @Test
        void orthogonalVectorsHaveDistanceOne() {
            Vector a = v(1, 0);
            Vector b = v(0, 5);
            assertEquals(1.0, metric.distance(a, b), 1e-12);
        }

        @Test
        void oppositeVectorsHaveDistanceTwo() {
            Vector a = v(1, 0);
            Vector b = v(-1, 0);
            assertEquals(2.0, metric.distance(a, b), 1e-12);
        }

        @Test
        void zeroVectorThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> metric.distance(v(0, 0), v(1, 0)));
        }
    }

    /* ============================================================
       NearestNeighbors
       ============================================================ */

    @Nested
    @DisplayName("NearestNeighbors")
    class NearestNeighborsTests {

        @Test
        void constructorRejectsNulls() {
            Map<String, Vector> data = Map.of("a", v(0, 0));
            EmbeddingGroup<String> g = group(data);

            assertThrows(IllegalArgumentException.class,
                    () -> new NearestNeighbors<>(null, rep(), new EuclideanDistance()));
            assertThrows(IllegalArgumentException.class,
                    () -> new NearestNeighbors<>(g, null, new EuclideanDistance()));
            assertThrows(IllegalArgumentException.class,
                    () -> new NearestNeighbors<>(g, rep(), null));
        }

        @Test
        void excludesQueryIdAndReturnsClosestFirst() {
            Map<String, Vector> data = new LinkedHashMap<>();
            data.put("q", v(0, 0));
            data.put("a", v(1, 0));
            data.put("b", v(2, 0));
            data.put("c", v(10, 0));

            NearestNeighbors<String> nn =
                    new NearestNeighbors<>(group(data), rep(), new EuclideanDistance());

            List<Neighbor<String>> out = nn.topK("q", 2);

            assertEquals(2, out.size());
            assertEquals("a", out.get(0).id());
            assertEquals("b", out.get(1).id());
            assertSorted(out);
        }

        @Test
        void kLargerThanAvailableReturnsAll() {
            Map<String, Vector> data = Map.of(
                    "q", v(0, 0),
                    "a", v(1, 0)
            );

            NearestNeighbors<String> nn =
                    new NearestNeighbors<>(group(data), rep(), new EuclideanDistance());

            List<Neighbor<String>> out = nn.topK("q", 10);
            assertEquals(1, out.size());
            assertEquals("a", out.get(0).id());
        }

        @Test
        void topKByVectorWithExcludeSet() {
            Map<String, Vector> data = Map.of(
                    "a", v(1, 0),
                    "b", v(2, 0),
                    "c", v(3, 0)
            );

            NearestNeighbors<String> nn =
                    new NearestNeighbors<>(group(data), rep(), new EuclideanDistance());

            Vector query = v(0, 0);
            Set<String> exclude = Set.of("a");

            List<Neighbor<String>> out = nn.topK(query, 2, exclude);

            assertEquals(2, out.size());
            assertEquals("b", out.get(0).id());
            assertEquals("c", out.get(1).id());
            assertSorted(out);
        }

        @Test
        void exposesMetricAndRepresentation() {
            DistanceMetric metric = new EuclideanDistance();
            Representation rep = rep();

            NearestNeighbors<String> nn =
                    new NearestNeighbors<>(group(Map.of("x", v(0, 0))), rep, metric);

            assertSame(metric, nn.metric());
            assertSame(rep, nn.representation());
        }
    }
}