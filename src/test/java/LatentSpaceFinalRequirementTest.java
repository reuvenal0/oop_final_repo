import org.io.Representation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lab.*;
import org.metrics.*;
import org.model.*;
import org.model.Vector;
import org.projection.*;
import org.view.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final Assignment Verification Test Suite.
 * This suite verifies the core functional requirements of the LatentSpace Explorer project:
 * 1. Semantic Distance (Cosine/Euclidean)
 * 2. Nearest Neighbors
 * 3. Custom Projections (Semantic Scales)
 * 4. Vector Arithmetic (Analogies)
 * 5. Subspace Grouping (Centroids)
 * 6. 2D/3D View Abstractions
 */
public class LatentSpaceFinalRequirementTest {

    // --- Helper for creating test data ---
    private EmbeddingStorage<String> createTestStorage() {
        Representation full = Representation.of("full");
        // PCA representations (simulating the 50 dimensions from Python)
        Representation pca = Representation.of("pca");

        // Manually creating a small semantic space:
        // "king" (10, 10), "queen" (10, 9), "man" (2, 10), "woman" (2, 9)
        Map<String, EmbeddingSingle<String>> data = new HashMap<>();

        data.put("king", new EmbeddingItem<>("king", Map.of(
                full, new Vector(new double[]{10.0, 10.0, 0.5}),
                pca, new Vector(new double[]{1.0, 1.0, 0.1})
        )));
        data.put("queen", new EmbeddingItem<>("queen", Map.of(
                full, new Vector(new double[]{10.0, 9.0, 0.5}),
                pca, new Vector(new double[]{1.0, 0.9, 0.1})
        )));
        data.put("man", new EmbeddingItem<>("man", Map.of(
                full, new Vector(new double[]{2.0, 10.0, 0.5}),
                pca, new Vector(new double[]{0.2, 1.0, 0.1})
        )));
        data.put("woman", new EmbeddingItem<>("woman", Map.of(
                full, new Vector(new double[]{2.0, 9.0, 0.5}),
                pca, new Vector(new double[]{0.2, 0.9, 0.1})
        )));

        return new EmbeddingStorage<>(data);
    }

    // -------------------------------------------------------------------------
    // Phase A: Core Logic & Semantic Distance
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Phase A: Core Logic & Distance Metrics")
    class PhaseATests {

        @Test
        @DisplayName("Requirement: Support both Cosine and Euclidean distances")
        void distanceMetricsRequirement() {
            Vector v1 = new Vector(new double[]{1, 0});
            Vector v2 = new Vector(new double[]{0, 1});

            DistanceMetric cosine = new CosineDistance();
            DistanceMetric euclidean = new EuclideanDistance();

            // Orthogonal vectors: Cosine distance should be 1.0
            assertEquals(1.0, cosine.distance(v1, v2), 1e-9);
            // Euclidean distance of (1,0) and (0,1) is sqrt(2)
            assertEquals(Math.sqrt(2), euclidean.distance(v1, v2), 1e-9);
        }

        @Test
        @DisplayName("Requirement: Nearest Neighbor Probe (Find K neighbors)")
        void nearestNeighborRequirement() {
            EmbeddingStorage<String> storage = createTestStorage();
            NearestNeighbors<String> nn = new NearestNeighbors<>(
                    storage, Representation.of("full"), new EuclideanDistance());

            // Nearest to king (10,10) should be queen (10,9)
            List<Neighbor<String>> neighbors = nn.topK("king", 1);

            assertFalse(neighbors.isEmpty());
            assertEquals("queen", neighbors.get(0).id());
        }

        @Test
        @DisplayName("Requirement: Custom Projections (Semantic Scale A -> B)")
        void customProjectionRequirement() {
            EmbeddingStorage<String> storage = createTestStorage();
            Representation full = Representation.of("full");
            CustomProjectionService<String> service = new CustomProjectionService<>(storage, full);

            // Projecting onto axis "man" -> "king" (the gender/royalty scale)
            // 'woman' should be closer to 'queen' on this projection
            List<ProjectionScore<String>> scale = service.semanticScale("man", "king", true);

            assertNotNull(scale);
            assertTrue(scale.size() >= 4);

            // Verify sorting (Requirement: scale must be sorted by coordinate)
            for (int i = 0; i < scale.size() - 1; i++) {
                assertTrue(scale.get(i).coordinate() <= scale.get(i+1).coordinate(),
                        "Scale must be sorted by its position on the axis");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase B: Advanced Research (Arithmetic & Grouping)
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Phase B: Vector Arithmetic & Subspace Grouping")
    class PhaseBTests {

        @Test
        @DisplayName("Requirement: Vector Arithmetic Lab (Analogies: King - Man + Woman = Queen)")
        void vectorArithmeticRequirement() {
            EmbeddingStorage<String> storage = createTestStorage();
            Representation full = Representation.of("full");

            // Build expression: king - man + woman
            VectorExpression<String> expression = VectorExpression.<String>builder()
                    .plus("king")
                    .minus("man")
                    .plus("woman")
                    .build();

            VectorExpressionEvaluator<String> evaluator = new VectorExpressionEvaluator<>(storage, full);
            Vector resultVector = evaluator.evaluate(expression);

            // Calculate expected manually: (10,10) - (2,10) + (2,9) = (10,9)
            assertArrayEquals(new double[]{10.0, 9.0, 0.5}, resultVector.toArrayCopy(), 1e-9);

            // Requirement: Find word closest to result vector
            NearestNeighbors<String> nn = new NearestNeighbors<>(storage, full, new EuclideanDistance());
            List<Neighbor<String>> neighbors = nn.topK(resultVector, 1, Set.of());

            assertEquals("queen", neighbors.get(0).id(), "Analogy result should be 'queen'");
        }

        @Test
        @DisplayName("Requirement: Subspace Grouping (Centroid Calculation)")
        void centroidGroupingRequirement() {
            // Group: king (10,10) and man (2,10)
            Vector v1 = new Vector(new double[]{10.0, 10.0});
            Vector v2 = new Vector(new double[]{2.0, 10.0});

            // Centroid = (10+2)/2, (10+10)/2 = (6, 10)
            Vector centroid = Vector.average(List.of(v1, v2));

            assertEquals(6.0, centroid.get(0), 1e-9);
            assertEquals(10.0, centroid.get(1), 1e-9);
        }
    }

    // -------------------------------------------------------------------------
    // Phase C: 3D Visualization Logic
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Phase C: Visualization Abstractions")
    class PhaseCTests {

        @Test
        @DisplayName("Requirement: Support switching between 2D and 3D ViewModes")
        void viewModeAbstractionRequirement() {
            // Test that our view logic can handle different dimensions
            ViewMode mode2D = new ViewMode2D(0, 1); // Use PCA1 and PCA2
            ViewMode mode3D = new ViewMode3D(0, 1, 2); // Use PCA1, PCA2, and PCA3

            assertEquals(2, mode2D.viewDim());
            assertEquals(3, mode3D.viewDim());

            // Requirement: Logic should be decoupled from specific PCA components
            assertEquals(0, mode2D.axisIndex(0));
            assertEquals(2, mode3D.axisIndex(2));
        }
    }
}