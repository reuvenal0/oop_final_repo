import org.io.Representation;
import org.io.json.JsonFormat;
import org.io.json.JsonSource;
import org.metrics.CosineDistance;
import org.metrics.DistanceMetric;
import org.model.EmbeddingStorage;
import org.model.EmbeddingsAssembler;
import org.model.Vector;
import org.view.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Demo that shows the entire VIEW layer usage:
 * - PCA 2D axis selection (PC1 vs PC2, PC6 vs PC7, etc.)
 * - Fetching a single point for "search & center"
 * - Fetching the whole point cloud (labels + points) for rendering
 * - Projecting arbitrary vectors (Stage B readiness):
 *      * analogy: king - man + woman
 *      * centroid: average of a group of words
 *
 * Notes:
 * - This demo is backend-only: it prints results to stdout.
 * - It does NOT use JavaFX or any UI library.
 */
public final class ViewDemo {

    public static void main(String[] args) throws Exception {

        // 1) Build paths to the JSON files inside the "data" folder (relative to project root).
        Path fullPath = Path.of("data", "full_vectors.json");
        Path pcaPath  = Path.of("data", "pca_vectors.json");

        // 2) Validate that files exist early (fail-fast with a clear message).
        Demo.requireFileExists(fullPath);
        Demo.requireFileExists(pcaPath);

        // 3) Define the representation keys (Flyweight).
        Representation full = Representation.of("full");
        Representation pca  = Representation.of("pca");

        // 4) Define the JSON schema: which field is the ID and which field is the vector array.
        // Change these if your JSON uses different field names.
        JsonFormat format = new JsonFormat("word", "vector");

        // 5) Create sources (each one loads a Map<ID, Vector> for a single representation).
        JsonSource<String> fullSource = new JsonSource<>(
                full,
                () -> Files.newInputStream(fullPath),
                format,
                s -> s // ID parser: raw string -> String
        );

        JsonSource<String> pcaSource = new JsonSource<>(
                pca,
                () -> Files.newInputStream(pcaPath),
                format,
                s -> s
        );

        // 6) Assemble into one storage where each ID has ALL representations.
        EmbeddingStorage<String> storage = new EmbeddingsAssembler<String>()
                .assemble(Set.of(fullSource, pcaSource));

        System.out.println("=== Data loaded ===");
        System.out.println("IDs count: " + storage.ids().size());
        System.out.println("Available reps: " + storage.availableRepresentations());
        System.out.println("FULL dim: " + fullSource.dimension().orElse(-1));
        System.out.println("PCA  dim: " + pcaSource.dimension().orElse(-1));
        System.out.println();

        // -----------------------------
        // 2) Build VIEW: ViewSpace locked to PCA for visualization
        // -----------------------------
        ViewSpace<String> viewSpace = new ViewSpace<>(
                storage,
                pca,
                new ViewMode2D(0, 1) // default axes: PC1 vs PC2 (0-based indices)
        );

        // -----------------------------
        // 3) Space Management: get all points (labels + points) for rendering
        // -----------------------------
        PointCloud<String> cloud01 = viewSpace.allPoints();

        System.out.println("=== VIEW: PCA cloud (PC1 vs PC2) ===");
        System.out.println("Mode: " + cloud01.mode());
        System.out.println("Points count: " + cloud01.points().size());

        // Print first 5 as sanity
        cloud01.points().stream().limit(5).forEach(lp -> {
            Point_2D p2 = (Point_2D) lp.point(); // safe: mode is 2D
            System.out.printf(Locale.ROOT, "%-12s x=%+.6f y=%+.6f%n", lp.id(), p2.x(), p2.y());
        });
        System.out.println();

        // -----------------------------
        // 4) Search & Center: get a single point for one word
        // -----------------------------
        String query = "the";
        Point_2D q01 = (Point_2D) viewSpace.pointOf(query);

        System.out.println("=== VIEW: Search & center ===");
        System.out.printf(Locale.ROOT, "Query '%s' under PC1/PC2 -> x=%+.6f y=%+.6f%n",
                query, q01.x(), q01.y());
        System.out.println();

        // -----------------------------
        // 5) Axis Selection: user chooses different PCA coordinates
        // Example: (5,6) by 0-based indices -> "PC6 vs PC7"
        // -----------------------------
        viewSpace.setMode(new ViewMode2D(5, 6));

        Point_2D q56 = (Point_2D) viewSpace.pointOf(query);

        System.out.println("=== VIEW: Axis selection change ===");
        System.out.println("Mode: " + viewSpace.mode());
        System.out.printf(Locale.ROOT, "Query '%s' under axes (5,6) -> x=%+.6f y=%+.6f%n",
                query, q56.x(), q56.y());
        System.out.println();

        // -----------------------------
        // 6) The "small smart addition": project(Vector v)
        // This is Stage B readiness: projecting vectors that have no ID.
        // -----------------------------
        // You add this method into ViewSpace (no new logic, reuses mapVectorToPoint):
        //
        // public ViewPoint project(Vector v) {
        //     Objects.requireNonNull(v, "vector must not be null");
        //     return mapVectorToPoint(v, mode);
        // }
        //
        // We'll demonstrate two Stage-B-style use cases.

        // 6a) Vector arithmetic (analogy-like):
        // result = king - man + woman
        //
        // IMPORTANT:
        // We use FULL vectors for the arithmetic (semantic space),
        // then we project the result into PCA view.
        //
        // That means: we need the PCA view mapping to accept a Vector that is already in PCA space.
        // So we should first convert the FULL result into PCA space if we want to show it in the PCA plane.
        //
        // In your current dataset, PCA vectors exist only per-word, not for arbitrary vectors.
        // So, for the demo we show the FULL-space result's *nearest neighbor word*,
        // and then project that neighbor's PCA vector.
        //
        // This is exactly how many embedding demos work: compute in FULL, then display nearest known word.

        System.out.println("=== Stage B demo: Vector arithmetic -> nearest word -> project ===");

        String king = "king";
        String man = "man";
        String woman = "woman";

        if (storage.contains(king) && storage.contains(man) && storage.contains(woman)) {
            Vector vKing = storage.require(king, full);
            Vector vMan = storage.require(man, full);
            Vector vWoman = storage.require(woman, full);

            Vector resultFull = vKing.subtract(vMan).add(vWoman);

            // Find nearest neighbor in FULL space (simple brute force with cosine distance)
            String nn = nearestNeighborByCosine(storage, full, resultFull);

            // Project the nearest known word into PCA view
            Point_2D nnPoint = (Point_2D) viewSpace.pointOf(nn);

            System.out.printf(Locale.ROOT, "Analogy vector: %s - %s + %s%n", king, man, woman);
            System.out.println("Nearest known word in FULL space: " + nn);
            System.out.printf(Locale.ROOT, "Projected (PCA axes %s): %s -> x=%+.6f y=%+.6f%n",
                    viewSpace.mode(), nn, nnPoint.x(), nnPoint.y());
        } else {
            System.out.println("Skipping analogy demo: dataset does not contain king/man/woman");
        }
        System.out.println();

        // 6b) Centroid of a group:
        // centroid = average(FULL vectors of selected words)
        // Then again: nearest neighbor word -> project its PCA point
        System.out.println("=== Stage B demo: Centroid -> nearest word -> project ===");

        List<String> group = List.of("doctor", "nurse", "hospital", "patients");
        List<String> present = group.stream().filter(storage::contains).toList();

        if (present.size() >= 2) {
            List<Vector> vectors = new ArrayList<>();
            for (String w : present) {
                vectors.add(storage.require(w, full));
            }

            Vector centroidFull = average(vectors);

            String nnCentroid = nearestNeighborByCosine(storage, full, centroidFull);
            Point_2D nnCentroidPoint = (Point_2D) viewSpace.pointOf(nnCentroid);

            System.out.println("Group: " + present);
            System.out.println("Nearest known word to centroid (FULL): " + nnCentroid);
            System.out.printf(Locale.ROOT, "Projected (PCA axes %s): %s -> x=%+.6f y=%+.6f%n",
                    viewSpace.mode(), nnCentroid, nnCentroidPoint.x(), nnCentroidPoint.y());
        } else {
            System.out.println("Skipping centroid demo: not enough group words exist in dataset");
        }

        System.out.println("\nDone.");
    }

    // -----------------------------
    // Helpers (private) used ONLY in this demo file
    // -----------------------------

    /**
     * Computes the average vector (centroid) of a list of vectors.
     * Assumes all vectors have the same dimension.
     */
    private static Vector average(List<Vector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("vectors must not be null/empty");
        }

        int dim = vectors.get(0).dim();
        double[] sum = new double[dim];

        for (Vector v : vectors) {
            if (v.dim() != dim) {
                throw new IllegalArgumentException("All vectors must have same dim. Expected " + dim + " got " + v.dim());
            }
            double[] a = v.toArrayCopy();
            for (int i = 0; i < dim; i++) {
                sum[i] += a[i];
            }
        }

        for (int i = 0; i < dim; i++) {
            sum[i] /= vectors.size();
        }

        return new Vector(sum);
    }

    /**
     * Finds the nearest neighbor (word) to a query vector in a given representation
     * using cosine distance: distance = 1 - cosineSimilarity.
     *
     * This is a brute-force scan over all IDs, which is fine for a demo (N=5000).
     */
    private static String nearestNeighborByCosine(EmbeddingStorage<String> storage,
                                                  Representation rep,
                                                  Vector query) {

        String bestId = null;
        double bestDist = Double.POSITIVE_INFINITY;

        for (String id : storage.ids()) {
            Vector v = storage.require(id, rep);

            DistanceMetric metric = new CosineDistance();

            double dist = metric.distance(query, v);

            if (dist < bestDist) {
                bestDist = dist;
                bestId = id;
            }
        }

        return Objects.requireNonNull(bestId, "bestId should not be null");
    }
}