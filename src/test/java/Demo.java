import com.sun.tools.javac.Main;
import org.io.Representation;
import org.io.json.JsonFormat;
import org.io.json.JsonSource;
import org.lab.*;
import org.metrics.*;
import org.model.EmbeddingStorage;
import org.model.EmbeddingsAssembler;
import org.model.Vector;
import org.io.json.PythonScriptRunner;
import org.metrics.NearestNeighbors;
import org.projection.CustomProjectionService;
import org.view.Point_2D;
import org.view.ViewMode2D;
import org.view.ViewSpace;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/**
 * Demo:
 * Loads FULL and PCA embeddings from the project's /data folder.
 *
 * Expected project structure:
 *  - data/full_vectors.json
 *  - data/pca_vectors.json
 *
 * Run it from the project root so relative paths work.
 */
public final class Demo {

    public static void run_python_script_4_data() {
        try {
            /*
             * The Python script is stored under:
             *   src/main/resources/Data/embedder.py
             *
             * At runtime (especially from a packaged JAR), resources are not guaranteed
             * to exist as regular filesystem files. Therefore, we extract the script
             * to a temporary file and run it from there.
             */

            // Load script from classpath resources
            InputStream scriptStream =
                    Main.class.getClassLoader()
                            .getResourceAsStream("embedder.py");

            if (scriptStream == null) {
                throw new IllegalStateException("Resource not found: Data/embedder.py");
            }

            // Create a temporary file that Python can execute
            Path tempScript = Files.createTempFile("embedder-", ".py");

            // Copy resource stream into temp file
            Files.copy(scriptStream, tempScript, StandardCopyOption.REPLACE_EXISTING);

            // Ensure temp file is deleted when JVM exits
            tempScript.toFile().deleteOnExit();

            // Create the runner (uses "python" by default)
            PythonScriptRunner runner = new PythonScriptRunner(tempScript);

            // Run the script (blocks until finished)
            runner.run();

            System.out.println("Python embedder finished successfully.");
            System.out.println("Expected output files are in: " + runner.getWorkingDirectory());

            Path outDir = Path.of("data");
            Files.createDirectories(outDir);

            Path tmpDir = runner.getWorkingDirectory(); // אם זה מחזיר Path (עדיף)
            Files.copy(tmpDir.resolve("full_vectors.json"), outDir.resolve("full_vectors.json"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(tmpDir.resolve("pca_vectors.json"), outDir.resolve("pca_vectors.json"), StandardCopyOption.REPLACE_EXISTING);


        } catch (Exception e) {
            // Print the stack trace so you immediately see what failed.
            System.err.println("Failed to run Python embedder.");
            e.printStackTrace();

            // Exit with non-zero code so failures are visible to CI / scripts
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Exception {
        run_python_script_4_data();

        // 1) Build paths to the JSON files inside the "data" folder (relative to project root).
        Path fullPath = Path.of("data", "full_vectors.json");
        Path pcaPath  = Path.of("data", "pca_vectors.json");

        // 2) Validate that files exist early (fail-fast with a clear message).
        requireFileExists(fullPath);
        requireFileExists(pcaPath);

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


        VectorArithmeticLab<String> lab = new VectorArithmeticLab<>(storage, full, new CosineDistance());
        System.out.println("king - man + woman");

        VectorExpression<String> expr = VectorExpression.<String>builder()
                .plus("king")
                .minus("man")
                .plus("woman")
                .build();

        LabResult<String> res = lab.solve(expr, 10);

        System.out.println("=== Vector Arithmetic Lab ===");
//        System.out.println("Expression: king - man + woman");
        for (var n : res.neighbors()) {
            System.out.printf(Locale.ROOT, "%-15s dist=%.6f%n", n.id(), n.distance());
        }

        ViewSpace<String> viewSpace = new ViewSpace<>(storage, pca, new ViewMode2D(0, 1));

        String best = res.neighbors().get(0).id(); // "queen"
        Point_2D p = (Point_2D) viewSpace.pointOf(best);

        System.out.printf(Locale.ROOT, "Best word '%s' projected to PCA: x=%+.6f y=%+.6f%n",
                best, p.x(), p.y());



        Representation FULL = Representation.of("full");

        CustomProjectionService<String> cps = new CustomProjectionService<>(storage, FULL);

// Example axis: "poor" -> "rich" (replace with words that exist in your dataset)
        String a = "poor";
        String b = "rich";

        var scores = cps.cleanScaleByPurity(a, b, 300,true);

        System.out.println("=== Custom Projection: " + a + " -> " + b + " (FULL, purity) ===");

        System.out.println("-- Most A-like (lowest coordinate) --");
        for (int i = 0; i < Math.min(10, scores.size()); i++) {
            var s = scores.get(i);
            System.out.printf(Locale.ROOT, "%-20s t=%+.4f orth=%.4f purity=%.4f%n",
                    s.id(), s.coordinate(), s.orthogonalDistance(), s.purity());
        }

        System.out.println("-- Most B-like (highest coordinate) --");
        for (int i = Math.max(0, scores.size() - 10); i < scores.size(); i++) {
            var s = scores.get(i);
            System.out.printf(Locale.ROOT, "%-20s t=%+.4f orth=%.4f purity=%.4f%n",
                    s.id(), s.coordinate(), s.orthogonalDistance(), s.purity());
        }

        // 7) Print a quick sanity report.
        System.out.println("=== Loaded embeddings ===");
        System.out.println("IDs count: " + storage.ids().size());
        System.out.println("Available representations: " + storage.availableRepresentations());

        System.out.println("FULL dimension: " + fullSource.dimension().orElse(-1));
        System.out.println("PCA dimension:  " + pcaSource.dimension().orElse(-1));

        // 8) Try fetching a sample word (pick any word you know exists in your data).
        String sample = pickAnyId(storage);
        System.out.println("Sample ID: " + sample);

        Vector fullVec = storage.require(sample, full);
        Vector pcaVec  = storage.require(sample, pca);

        System.out.println("Sample FULL vector: " + fullVec);
        System.out.println("Sample PCA vector:  " + pcaVec);
    }

    public static void requireFileExists(Path p) {
        if (!Files.exists(p)) {
            throw new IllegalStateException("File not found: " + p.toAbsolutePath()
                    + "\nTip: Run Demo from the project root so 'data/...' resolves correctly.");
        }
        if (!Files.isRegularFile(p)) {
            throw new IllegalStateException("Not a regular file: " + p.toAbsolutePath());
        }
    }

    private static String pickAnyId(EmbeddingStorage<String> storage) {
        return storage.ids().iterator().next();
    }

    public static void demomogas(String[] args) throws Exception {

        // -----------------------------
        // 0) Configure what to demo
        // -----------------------------
        String queryWord = "the";  // must exist in the dataset
        int k = 10;

        // Choose metric here (Strategy):
        DistanceMetric metric = new EuclideanDistance();   // or: new EuclideanDistance() or CosineDistance()

        // Optional: distance demo between two words
        String wordA = "the";
        String wordB = "of";

        // -----------------------------
        // 1) Locate data files
        // -----------------------------
        Path fullPath = Path.of("data", "full_vectors.json");
        Path pcaPath  = Path.of("data", "pca_vectors.json");

        if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath)) {
            throw new IllegalStateException("Missing data file: " + fullPath.toAbsolutePath());
        }
        if (!Files.exists(pcaPath) || !Files.isRegularFile(pcaPath)) {
            throw new IllegalStateException("Missing data file: " + pcaPath.toAbsolutePath());
        }

        // -----------------------------
        // 2) Build representations + JSON schema
        // -----------------------------
        Representation FULL = Representation.of("full");
        Representation PCA  = Representation.of("pca");

        // Your JSON entries look like:
        // {"word": "...", "vector": [...]}
        JsonFormat format = new JsonFormat("word", "vector");

        // -----------------------------
        // 3) Create sources (lazy, cached inside JsonSource)
        // -----------------------------
        JsonSource<String> fullSource = new JsonSource<>(
                FULL,
                () -> Files.newInputStream(fullPath),
                format,
                s -> s // ID parser: raw -> String
        );

        JsonSource<String> pcaSource = new JsonSource<>(
                PCA,
                () -> Files.newInputStream(pcaPath),
                format,
                s -> s
        );

        // -----------------------------
        // 4) Assemble merged storage (strict same-ID-set merge)
        // -----------------------------
        EmbeddingStorage<String> storage = new EmbeddingsAssembler<String>()
                .assemble(Set.of(fullSource, pcaSource));

        // -----------------------------
        // 5) Sanity prints
        // -----------------------------
        System.out.println("=== Loaded embeddings ===");
        System.out.println("IDs count: " + storage.ids().size());
        System.out.println("Available representations: " + storage.availableRepresentations());
        System.out.println("FULL dim: " + fullSource.dimension().orElse(-1));
        System.out.println("PCA  dim: " + pcaSource.dimension().orElse(-1));
        System.out.println();

        // -----------------------------
        // 6) Distance demo (FULL)
        // -----------------------------
        double dist = metric.distance(
                storage.require(wordA, FULL),
                storage.require(wordB, FULL)
        );

        System.out.println("=== Distance demo (FULL) ===");
        System.out.println("Metric: " + metric.name());
        System.out.println("A: " + wordA);
        System.out.println("B: " + wordB);
        System.out.printf(Locale.ROOT, "Distance: %.6f%n", dist);
        System.out.println();

        // -----------------------------
        // 7) Nearest neighbors demo (FULL)
        // -----------------------------
        NearestNeighbors<String> nn = new NearestNeighbors<>(storage, FULL, metric);

        System.out.println("=== Nearest neighbors (FULL) ===");
        System.out.println("Query: " + queryWord);
        System.out.println("Metric: " + metric.name());
        System.out.println("Top K: " + k);
        System.out.println();

        for (Neighbor<String> n : nn.topK(queryWord, k)) {
            System.out.printf(Locale.ROOT, "%-20s dist=%.6f%n", n.id(), n.distance());
        }

        System.out.println();

        // -----------------------------
        // 8) Tiny PCA demo: print first two coords of query (optional)
        // -----------------------------
        var pcaVec = storage.require(queryWord, PCA);
        System.out.println("=== PCA quick view ===");
        System.out.println("Query: " + queryWord);
        System.out.printf(Locale.ROOT, "PCA[0]=%.6f, PCA[1]=%.6f%n", pcaVec.get(0), pcaVec.get(1));
    }



}
