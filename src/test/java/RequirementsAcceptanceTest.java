import org.io.Representation;
import org.io.RepresentationSource;
import org.io.json.JsonFormat;
import org.io.json.JsonSource;
import org.lab.*;
import org.metrics.*;
import org.model.*;
import org.model.Vector;
import org.projection.CustomProjectionService;
import org.projection.ProjectionScore;
import org.view.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RequirementsAcceptanceTest
 *
 * Goal:
 *  Verify (as much as possible without GUI automation) that the project supports
 *  the FEATURES and REQUIREMENTS described in the assignment:
 *
 *  A) Core / Space management
 *    1) Load vectors (Python output) and build object model
 *    2) Axis selection for 2D (PCA dims), and basic 3D support
 *    3) Semantic distance (Cosine + Euclidean) on the FULL/original space
 *    4) Nearest neighbor probe (top-K neighbors)
 *    5) Custom projection axis (A->B semantic scale)
 *
 *  B) Deep research extensions
 *    6) Vector arithmetic lab (V1 - V2 + V3) + nearest-to-result
 *    7) Subspace grouping: centroid of selected words + top-K nearest to centroid
 *
 *  C) 3D
 *    8) 3D view mode + 3D points (as a model primitive)
 *
 *  D) OOP design highlights (indirectly)
 *    9) Strategy-based distance metrics (injectable, no hard-coded instanceof)
 *   10) Failure handling for missing words / corrupted data
 *
 * Notes:
 * - UI behaviors like "click a point" or "center camera on a word" are hard to test
 *   reliably without GUI test frameworks. Instead, we test the underlying capabilities
 *   your UI MUST rely on.
 * - Where your API might differ, we use reflection in small places (only where needed),
 *   so the test still guides you instead of breaking immediately for a naming mismatch.
 */
public class RequirementsAcceptanceTest {

    // ---------------------------------------------
    // A) Load + Build model from Python-like output
    // ---------------------------------------------

    @Nested
    class LoadingAndModelBuild {

        @TempDir
        Path tempDir;

        @Test
        void loadsPythonJsonOutput_andBuildsStorage_withTwoRepresentations() throws IOException {
            // Simulate Python output: a JSON array of objects: [{"id": "...", "vector": [..]}, ...]
            // We'll create TWO files:
            //  - FULL (original space, e.g., D=4 here for testing)
            //  - PCA  (compressed space, e.g., D=3 here for testing)
            //
            // In your real system, FULL might be 100/300, PCA might be 50 (then view chooses 2 or 3 dims).

            Path fullFile = tempDir.resolve("full.json");
            Path pcaFile = tempDir.resolve("pca.json");

            writeJson(fullFile, List.of(
                    row("king",  1, 0, 0, 0),
                    row("queen", 0.9, 0.1, 0, 0),
                    row("man",   0.8, 0.2, 0, 0),
                    row("woman", 0.7, 0.3, 0, 0)
            ));

            writeJson(pcaFile, List.of(
                    row("king",  10,  0,  0),
                    row("queen",  9,  1,  0),
                    row("man",    8,  2,  0),
                    row("woman",  7,  3,  0)
            ));

            Representation FULL = Representation.of("FULL");
            Representation PCA  = Representation.of("PCA");

// JsonFormat: use constructor (no .of in your codebase)
            JsonFormat fmt = new JsonFormat("id", "vector");

// JsonSource: 4 args (rep, inputStream supplier, format, parser/mapper)
            RepresentationSource<String> fullSrc =
                    new JsonSource<>(FULL, () -> Files.newInputStream(fullFile), fmt, s -> s);

            RepresentationSource<String> pcaSrc =
                    new JsonSource<>(PCA, () -> Files.newInputStream(pcaFile), fmt, s -> s);

            EmbeddingsAssembler<String> assembler = new EmbeddingsAssembler<>();
            EmbeddingStorage<String> storage = assembler.assemble(Set.of(fullSrc, pcaSrc));

            assertEquals(Set.of("king", "queen", "man", "woman"), storage.ids());
            assertEquals(Set.of(FULL, PCA), storage.availableRepresentations());

            // sanity: requires vectors exist in both reps
            assertNotNull(storage.require("king", FULL));
            assertNotNull(storage.require("king", PCA));
        }

        @Test
        void failsFastOnMissingWord_requireSingle_shouldThrow() {
            Representation FULL = Representation.of("FULL");

            EmbeddingSingle<String> s1 = new EmbeddingItem<>("a", Map.of(FULL, new Vector(new double[]{1, 0})));
            EmbeddingStorage<String> storage = new EmbeddingStorage<>(Map.of("a", s1));

            assertThrows(IllegalArgumentException.class, () -> storage.requireSingle("missing"),
                    "Requirement: handling 'word not found' should be explicit and safe.");
        }
    }

    // ---------------------------------------------
    // B) Axis selection + 2D / 3D view primitives
    // ---------------------------------------------

    @Nested
    class AxisSelectionAndViewPrimitives {

        @Test
        void axisSelection_2D_allowsChoosingAnyTwoPcaComponents() {
            // Requirement: user can choose which PCA components are shown on X and Y.
            // That means your view mode should store chosen axis indices.
            ViewMode2D mode = new ViewMode2D(3, 1);
            assertEquals(2, mode.viewDim());
            assertEquals(3, mode.axisIndex(0));
            assertEquals(1, mode.axisIndex(1));
        }

        @Test
        void axisSelection_3D_existsAndRejectsDuplicates() {
            // Requirement: 3D view (PCa dims for x,y,z).
            assertThrows(IllegalArgumentException.class, () -> new ViewMode3D(0, 0, 1));
            ViewMode3D mode = new ViewMode3D(0, 1, 2);
            assertEquals(3, mode.viewDim());
        }

        @Test
        void pointCloud_canRepresentLabeledPoints_in2D_and3D() {
            LabeledPoint<String> p2 = new LabeledPoint<>("king", new Point_2D(1.0, 2.0));
            PointCloud<String> cloud2 = new PointCloud<>(List.of(p2), new ViewMode2D(0, 1));
            assertEquals("king", cloud2.points().get(0).id());

            LabeledPoint<String> p3 = new LabeledPoint<>("queen", new Point_3D(1.0, 2.0, 3.0));
            PointCloud<String> cloud3 = new PointCloud<>(List.of(p3), new ViewMode3D(0, 1, 2));
            assertEquals("queen", cloud3.points().get(0).id());
        }

        @Test
        void optional_viewSpace_centerOnWord_api_exists_ifImplemented() {
            // Requirement says:
            // "search a word in space so UI centers around that point"
            //
            // This is UI-ish, but you might have a ViewSpace model/controller class
            // that exposes something like centerOn(id) / focusOn(id).
            //
            // If you implemented org.view.ViewSpace, we try to detect an API.
            // If you didn't implement it yet, this test will guide you (it will fail with a clear message).
            try {
                Class<?> cls = Class.forName("org.view.ViewSpace");

                boolean hasCentering = hasAnyMethod(cls,
                        "centerOn", "focusOn", "setCenter", "moveCenterTo", "panTo", "lookAt");

                assertTrue(hasCentering,
                        "ViewSpace exists but no obvious centering API found. " +
                                "Implement a method like centerOn(id) / focusOn(id) to support the requirement.");

            } catch (ClassNotFoundException e) {
                fail("Missing org.view.ViewSpace. If your design uses a different class for camera/centering logic, " +
                        "either add ViewSpace or update this acceptance test to match your chosen API.");
            }
        }
    }

    // ---------------------------------------------
    // C) Distance metrics + Nearest Neighbors
    // ---------------------------------------------

    @Nested
    class DistanceAndNeighbors {

        @Test
        void supportsTwoDistanceMetrics_cosineAndEuclidean() {
            DistanceMetric cosine = new CosineDistance();
            DistanceMetric euclid = new EuclideanDistance();

            Vector a = new Vector(new double[]{1, 0});
            Vector b = new Vector(new double[]{0, 1});

            double c = cosine.distance(a, b);
            double e = euclid.distance(a, b);

            // We don’t care about exact values here as much as:
            // - both metrics exist
            // - both compute something valid
            assertTrue(Double.isFinite(c));
            assertTrue(Double.isFinite(e));
            assertNotEquals(c, e, "Cosine and Euclidean should behave differently in general.");
        }

        @Test
        void nearestNeighborProbe_topK_neighborsWorks() {
            // Requirement: "click a point -> highlight K nearest neighbors"
            // Under the hood, you need a KNN query.
            Representation FULL = Representation.of("FULL");

            EmbeddingGroup<String> group = groupFromMap(FULL, new LinkedHashMap<>(Map.of(
                    "q", new Vector(new double[]{0, 0}),
                    "a", new Vector(new double[]{1, 0}),
                    "b", new Vector(new double[]{2, 0}),
                    "c", new Vector(new double[]{10, 0})
            )));

            NearestNeighbors<String> nn = new NearestNeighbors<>(group, FULL, new EuclideanDistance());
            List<Neighbor<String>> out = nn.topK("q", 2);

            assertEquals(List.of("a", "b"), out.stream().map(Neighbor::id).toList());
            assertTrue(out.get(0).distance() <= out.get(1).distance());
        }
    }

    // ---------------------------------------------
    // D) Custom Projections (Semantic Axis)
    // ---------------------------------------------

    @Nested
    class CustomProjectionAxis {

        @Test
        void customProjection_semanticScale_sortsAlongAxis() {
            // Requirement: choose two words (A,B), project all words onto axis A->B, view a semantic scale.
            Representation FULL = Representation.of("FULL");

            EmbeddingGroup<String> group = groupFromMap(FULL, new LinkedHashMap<>(Map.of(
                    "poor",  new Vector(new double[]{0, 0}),
                    "mid",   new Vector(new double[]{1, 0}),
                    "rich",  new Vector(new double[]{2, 0}),
                    "off",   new Vector(new double[]{1, 5})  // off-axis point
            )));

            CustomProjectionService<String> svc = new CustomProjectionService<>(group, FULL);

            List<ProjectionScore<String>> scale = svc.semanticScale("poor", "rich", true);

            // We expect monotonic order on the axis coordinate:
            assertEquals("poor", scale.get(0).id());
            assertEquals("rich", scale.get(scale.size() - 1).id());

            // and "mid" should be between them
            int midIndex = indexOfId(scale, "mid");
            assertTrue(midIndex > 0 && midIndex < scale.size() - 1);
        }
    }

    // ---------------------------------------------
    // E) Vector Arithmetic Lab (Analogies)
    // ---------------------------------------------

    @Nested
    class VectorArithmeticLabAcceptance {

        @Test
        void vectorArithmeticLab_solvesExpression_andReturnsNearestWord() {
            // Requirement: V1 - V2 + V3 -> result vector + nearest word in embedding space
            Representation FULL = Representation.of("FULL");

            // We'll craft a tiny space where:
            //   king - man + woman ≈ queen
            //
            // We'll set:
            //   king  = (10,  1)
            //   man   = ( 2,  0)
            //   woman = ( 2,  1)
            //   queen = (10,  2)  -> king - man + woman = (10,2)
            //
            // Exact match so nearest should be "queen".
            EmbeddingGroup<String> group = groupFromMap(FULL, new LinkedHashMap<>(Map.of(
                    "king",  new Vector(new double[]{10, 1}),
                    "man",   new Vector(new double[]{ 2, 0}),
                    "woman", new Vector(new double[]{ 2, 1}),
                    "queen", new Vector(new double[]{10, 2}),
                    "other", new Vector(new double[]{-5, -5})
            )));

            VectorArithmeticLab<String> lab = new VectorArithmeticLab<>(group, FULL, new EuclideanDistance());

            VectorExpression<String> expr = VectorExpression.<String>builder()
                    .plus("king")
                    .minus("man")
                    .plus("woman")
                    .build();

            Object result = lab.solve(expr, 3);
            assertNotNull(result, "LabResult must not be null");

            Vector outVec = extractVectorFromLabResult(result);
            assertNotNull(outVec, "LabResult should expose a result Vector (via method or field)");

            // Verify the computed vector is exactly (10,2)
            assertArrayEquals(new double[]{10.0, 2.0}, outVec.toArrayCopy(), 1e-9);

            List<?> neighbors = extractNeighborsFromLabResult(result);
            assertNotNull(neighbors, "LabResult should expose neighbors list (via method or field)");
            assertFalse(neighbors.isEmpty(), "Neighbors list should not be empty");

            // We expect "queen" to appear as the closest neighbor
            String bestId = extractNeighborId(neighbors.get(0));
            assertEquals("queen", bestId, "The closest neighbor to the result should be 'queen' in this crafted space.");
        }
    }

    // ---------------------------------------------
    // F) Subspace grouping (Centroid + nearest)
    // ---------------------------------------------

    @Nested
    class SubspaceGroupingAcceptance {

        @Test
        void subspaceGrouping_centroidComputed_andTopKNearestToCentroid() {
            // Requirement: select a set of words -> centroid -> find K nearest to centroid
            Representation FULL = Representation.of("FULL");

            // Space:
            // A=(0,0), B=(2,0), C=(0,2), D=(10,10)
            // centroid({A,B,C}) = (2/3, 2/3)
            // nearest to centroid should be A/B/C (in some order), definitely NOT D.
            EmbeddingGroup<String> group = groupFromMap(FULL, new LinkedHashMap<>(Map.of(
                    "A", new Vector(new double[]{0, 0}),
                    "B", new Vector(new double[]{2, 0}),
                    "C", new Vector(new double[]{0, 2}),
                    "D", new Vector(new double[]{10, 10})
            )));

            Set<String> selected = Set.of("A", "B", "C");
            Vector centroid = centroidOf(group, FULL, selected);

            NearestNeighbors<String> nn = new NearestNeighbors<>(group, FULL, new EuclideanDistance());

            // Exclude the selected words to simulate "show me other words close to the group meaning"
            List<Neighbor<String>> out = nn.topK(centroid, 2, selected);

            assertEquals(1, out.size(),
                    "In this tiny dataset, excluding A/B/C leaves only D available; so topK should return 1.");
            assertEquals("D", out.get(0).id(),
                    "Given exclusion, D is the only remaining candidate. This validates the pipeline.");

            // Now without exclusion: the closest should be from A/B/C and NOT D.
            List<Neighbor<String>> out2 = nn.topK(centroid, 3, Set.of());
            assertEquals(3, out2.size());
            assertFalse(out2.stream().map(Neighbor::id).toList().contains("D"),
                    "D is far away and should not be in the top-3 nearest when others exist.");
        }
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private static void writeJson(Path file, List<Map<String, Object>> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            sb.append("  {\"id\": \"").append(r.get("id")).append("\", \"vector\": ");
            @SuppressWarnings("unchecked")
            List<Number> vec = (List<Number>) r.get("vector");
            sb.append("[");
            for (int j = 0; j < vec.size(); j++) {
                sb.append(vec.get(j));
                if (j + 1 < vec.size()) sb.append(", ");
            }
            sb.append("]}");
            if (i + 1 < rows.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> row(String id, double... xs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        List<Number> v = new ArrayList<>();
        for (double x : xs) v.add(x);
        m.put("vector", v);
        return m;
    }

    /**
     * Build an EmbeddingGroup from a simple id->Vector map for a single Representation.
     * We implement EmbeddingGroup minimally, matching what your tests already expect.
     */
    private static EmbeddingGroup<String> groupFromMap(Representation rep, Map<String, Vector> data) {
        Objects.requireNonNull(rep);
        Objects.requireNonNull(data);

        return new EmbeddingGroup<String>() {
            @Override
            public boolean contains(String id) {
                return data.containsKey(id);
            }

            @Override
            public Optional<EmbeddingSingle<String>> find(String id) {
                if (!data.containsKey(id)) return Optional.empty();
                return Optional.of(requireSingle(id));
            }

            @Override
            public EmbeddingSingle<String> requireSingle(String id) {
                if (!data.containsKey(id)) {
                    throw new IllegalArgumentException("Missing id: " + id);
                }
                return new EmbeddingItem<>(id, Map.of(rep, data.get(id)));
            }

            @Override
            public Vector require(String id, Representation r) {
                if (!rep.equals(r)) {
                    throw new IllegalArgumentException("This fake group supports only rep=" + rep.name());
                }
                Vector v = data.get(id);
                if (v == null) throw new IllegalArgumentException("Missing id: " + id);
                return v;
            }

            @Override
            public Set<String> ids() {
                return data.keySet();
            }

            @Override
            public Set<Representation> availableRepresentations() {
                return Set.of(rep);
            }
        };
    }

    private static int indexOfId(List<ProjectionScore<String>> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i).id(), id)) return i;
        }
        return -1;
    }

    /**
     * Compute centroid in a very explicit way using raw arrays.
     * This matches the assignment definition exactly.
     */
    private static Vector centroidOf(EmbeddingGroup<String> group, Representation rep, Set<String> ids) {
        Objects.requireNonNull(group);
        Objects.requireNonNull(rep);
        Objects.requireNonNull(ids);
        if (ids.isEmpty()) throw new IllegalArgumentException("ids must not be empty");

        double[] sum = null;
        int count = 0;

        for (String id : ids) {
            Vector v = group.require(id, rep);
            double[] a = v.toArrayCopy();
            if (sum == null) {
                sum = new double[a.length];
            } else {
                if (sum.length != a.length) {
                    throw new IllegalStateException("dimension mismatch inside centroid");
                }
            }
            for (int i = 0; i < a.length; i++) sum[i] += a[i];
            count++;
        }

        for (int i = 0; i < sum.length; i++) sum[i] /= count;
        return new Vector(sum);
    }

    private static boolean hasAnyMethod(Class<?> cls, String... names) {
        for (String n : names) {
            for (Method m : cls.getMethods()) {
                if (m.getName().equals(n)) return true;
            }
        }
        return false;
    }

    // -----------------------------
    // LabResult extraction helpers
    // -----------------------------

    /**
     * Extract a Vector from LabResult using reflection.
     * We try common method names first; if none exist, we look for a Vector field.
     */
    private static Vector extractVectorFromLabResult(Object labResult) {
        Class<?> c = labResult.getClass();

        // Try common accessor names
        for (String methodName : List.of("vector", "resultVector", "result", "value", "outputVector")) {
            try {
                Method m = c.getMethod(methodName);
                Object out = m.invoke(labResult);
                if (out instanceof Vector v) return v;
            } catch (Exception ignored) {}
        }

        // Try fields of type Vector
        for (Field f : c.getDeclaredFields()) {
            if (Vector.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Object out = f.get(labResult);
                    if (out instanceof Vector v) return v;
                } catch (Exception ignored) {}
            }
        }

        fail("Cannot extract result Vector from LabResult. " +
                "Expose a public method like vector()/resultVector() or keep a Vector field.");
        return null;
    }

    /**
     * Extract neighbors list from LabResult using reflection.
     */
    private static List<?> extractNeighborsFromLabResult(Object labResult) {
        Class<?> c = labResult.getClass();

        for (String methodName : List.of("neighbors", "nearest", "topK", "results")) {
            try {
                Method m = c.getMethod(methodName);
                Object out = m.invoke(labResult);
                if (out instanceof List<?> list) return list;
            } catch (Exception ignored) {}
        }

        for (Field f : c.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Object out = f.get(labResult);
                    if (out instanceof List<?> list) return list;
                } catch (Exception ignored) {}
            }
        }

        return null;
    }

    /**
     * Extract neighbor id from Neighbor-like object.
     * If it's your org.metrics.Neighbor, it has id().
     * If it's something else, we try common patterns by reflection.
     */
    private static String extractNeighborId(Object neighborObj) {
        if (neighborObj == null) return null;

        // Best case: it's org.metrics.Neighbor<T>
        if (neighborObj instanceof Neighbor<?> n) {
            Object id = n.id();
            return id == null ? null : id.toString();
        }

        // Reflection fallback
        for (String methodName : List.of("id", "getId", "word", "term")) {
            try {
                Method m = neighborObj.getClass().getMethod(methodName);
                Object out = m.invoke(neighborObj);
                return out == null ? null : out.toString();
            } catch (Exception ignored) {}
        }

        return neighborObj.toString();
    }
}