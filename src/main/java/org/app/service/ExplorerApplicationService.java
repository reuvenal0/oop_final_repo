package org.app.service;

import org.app.api.ExplorerUseCases;
import org.app.api.dto.AppliedViewConfig;
import org.app.api.dto.LabResultView;
import org.app.api.dto.MetricOption;
import org.app.api.dto.NeighborView;
import org.app.api.dto.ProjectionScoreView;
import org.io.Representation;
import org.io.RepresentationSource;
import org.io.json.JsonFormat;
import org.io.json.JsonSource;
import org.io.json.PythonScriptRunner;
import org.lab.LabResult;
import org.lab.Term;
import org.lab.VectorArithmeticLab;
import org.lab.VectorExpression;
import org.metrics.CosineDistance;
import org.metrics.DistanceMetric;
import org.metrics.EuclideanDistance;
import org.metrics.NearestNeighbors;
import org.metrics.Neighbor;
import org.model.EmbeddingGroup;
import org.model.EmbeddingStorage;
import org.model.EmbeddingsAssembler;
import org.model.Vector;
import org.projection.CustomProjectionService;
import org.projection.ProjectionScore;
import org.view.PointCloud;
import org.view.ViewMode;
import org.view.ViewMode2D;
import org.view.ViewMode3D;
import org.view.ViewPoint;
import org.view.ViewSpace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;

/** Default application service used by JavaFX UI through ExplorerUseCases. */
public final class ExplorerApplicationService implements ExplorerUseCases {

    private static final Path DEFAULT_FULL = Path.of("data", "full_vectors.json");
    private static final Path DEFAULT_PCA = Path.of("data", "pca_vectors.json");
    private static final String EMBEDDER_RESOURCE = "embedder.py";

    private static final Representation FULL = Representation.of("full");
    private static final Representation PCA = Representation.of("pca");

    private static final JsonFormat JSON_FORMAT = new JsonFormat("word", "vector");
    private static final Function<String, String> ID_PARSER = s -> s;

    private final Map<String, DistanceMetric> metricsById;

    private EmbeddingGroup<String> group;
    private ViewSpace<String> viewSpace;
    private ViewMode mode = new ViewMode2D(0, 1);
    private DistanceMetric metric;
    private String metricId = "cosine";
    private NearestNeighbors<String> knn;
    private Representation displayRep = PCA;
    private CustomProjectionService<String> projectionService;
    private VectorArithmeticLab<String> vectorLab;
    private List<ViewPoint> overlayPath = List.of();
    private String centeredId;

    public ExplorerApplicationService() {
        Map<String, DistanceMetric> metrics = new LinkedHashMap<>();
        metrics.put("cosine", new CosineDistance());
        metrics.put("euclidean", new EuclideanDistance());
        this.metricsById = Map.copyOf(metrics);
        this.metric = metricsById.get("cosine");
    }

    @Override
    public void load(Path anyPath) throws Exception {
        Path dir = anyPath.toAbsolutePath().getParent();
        Path full = dir.resolve("full_vectors.json");
        Path pca = dir.resolve("pca_vectors.json");

        if (Files.exists(full) && Files.exists(pca)) {
            loadPair(full, pca);
            return;
        }

        if (anyPath.getFileName().toString().toLowerCase(Locale.ROOT).contains("pca") && Files.exists(DEFAULT_FULL)) {
            loadPair(DEFAULT_FULL, anyPath);
            return;
        }

        throw new IllegalArgumentException("Could not infer full_vectors.json + pca_vectors.json in the same folder.");
    }

    @Override
    public void loadDefaultIfPresent() throws Exception {
        if (Files.exists(DEFAULT_FULL) && Files.exists(DEFAULT_PCA)) {
            loadPair(DEFAULT_FULL, DEFAULT_PCA);
        } else {
            throw new IllegalStateException("Expected " + DEFAULT_FULL + " and " + DEFAULT_PCA);
        }
    }

    @Override
    public void generateDefaultDataset() throws Exception {
        Files.createDirectories(Path.of("data"));

        Path scriptPath = extractScriptFromResources(EMBEDDER_RESOURCE);
        PythonScriptRunner runner = new PythonScriptRunner(scriptPath);
        runner.run();

        Path generatedFull = runner.getWorkingDirectory().resolve("full_vectors.json");
        Path generatedPca = runner.getWorkingDirectory().resolve("pca_vectors.json");

        if (!Files.exists(generatedFull) || !Files.exists(generatedPca)) {
            throw new IllegalStateException(
                    "Python embedder finished but did not produce expected files: full_vectors.json and pca_vectors.json"
            );
        }

        Files.copy(generatedFull, DEFAULT_FULL, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(generatedPca, DEFAULT_PCA, StandardCopyOption.REPLACE_EXISTING);

        loadDefaultIfPresent();
    }

    Path extractScriptFromResources(String resourcePath) throws IOException {
        InputStream scriptStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (scriptStream == null) {
            throw new IllegalStateException("Missing Python resource: " + resourcePath);
        }

        Path tempScript = Files.createTempFile("embedder-", ".py");
        tempScript.toFile().deleteOnExit();
        try (InputStream in = scriptStream) {
            Files.copy(in, tempScript, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempScript;
    }

    private void loadPair(Path fullPath, Path pcaPath) {
        RepresentationSource<String> fullSrc = new JsonSource<>(FULL, () -> Files.newInputStream(fullPath), JSON_FORMAT, ID_PARSER);
        RepresentationSource<String> pcaSrc = new JsonSource<>(PCA, () -> Files.newInputStream(pcaPath), JSON_FORMAT, ID_PARSER);

        EmbeddingStorage<String> storage = new EmbeddingsAssembler<String>().assemble(Set.of(fullSrc, pcaSrc));

        this.group = storage;
        this.projectionService = new CustomProjectionService<>(group, FULL);
        this.viewSpace = new ViewSpace<>(group, displayRep, mode);
        this.knn = new NearestNeighbors<>(group, FULL, metric);
        this.vectorLab = new VectorArithmeticLab<>(group, FULL, metric);

        String anyId = group.ids().iterator().next();
        viewSpace.centerOn(anyId);
        centeredId = anyId;
    }

    @Override
    public PointCloud<String> getPointCloud() { ensureLoaded(); return viewSpace.allPoints(); }

    @Override
    public void setViewMode(ViewMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        if (viewSpace != null) {
            viewSpace.setMode(this.mode);
            recenterIfPossible();
        }
    }

    @Override
    public ViewMode getViewMode() { return mode; }

    @Override
    public Optional<ViewPoint> getCenter() { return viewSpace == null ? Optional.empty() : Optional.ofNullable(viewSpace.center()); }

    @Override
    public void centerOn(String id) { ensureLoaded(); viewSpace.centerOn(id); centeredId = id; }

    @Override
    public boolean containsId(String id) { return group != null && group.contains(id); }

    @Override
    public List<Representation> availableRepresentations() {
        if (group == null) return List.of();
        return group.availableRepresentations().stream().toList();
    }

    @Override
    public Representation currentDisplayRepresentation() { return viewSpace == null ? null : viewSpace.representation(); }

    @Override
    public void setDisplayRepresentation(Representation representation) {
        ensureLoaded();
        this.displayRep = Objects.requireNonNull(representation, "representation must not be null");
        this.viewSpace = new ViewSpace<>(group, displayRep, mode);
        recenterIfPossible();
    }

    @Override
    public int representationDimension(Representation representation) {
        ensureLoaded();
        String anyId = group.ids().iterator().next();
        return group.require(anyId, representation).dim();
    }

    @Override
    public List<MetricOption> availableMetrics() {
        return metricsById.entrySet().stream().map(e -> new MetricOption(e.getKey(), e.getValue().name())).toList();
    }

    @Override
    public String currentMetricId() {
        return metricId;
    }

    @Override
    public void setMetric(String metricId) {
        DistanceMetric chosen = metricsById.get(metricId);
        if (chosen == null) throw new IllegalArgumentException("Unknown metric: " + metricId);
        this.metric = chosen;
        this.metricId = metricId;
        if (group != null) {
            this.knn = new NearestNeighbors<>(group, FULL, this.metric);
            this.vectorLab = new VectorArithmeticLab<>(group, FULL, this.metric);
        }
    }

    @Override
    public List<NeighborView<String>> nearestNeighborsDetailed(String id, int k) {
        ensureLoaded();
        List<Neighbor<String>> neighbors = knn.topK(id, k);
        String metricName = currentMetricId();
        return neighbors.stream().map(n -> {
            double distance = n.distance();
            String label = "cosine".equalsIgnoreCase(metricName)
                    ? String.format("%s   |   dist=%.5f   sim=%.5f", n.id(), distance, 1.0 - distance)
                    : String.format("%s   |   dist=%.5f", n.id(), distance);
            return new NeighborView<>(n.id(), distance, label);
        }).toList();
    }

    @Override
    public List<ProjectionScoreView<String>> customProjectionScale(String aId, String bId, int topN, boolean centeredAxis, boolean includeAnchors, boolean usePurityFilter) {
        ensureLoaded();

        List<ProjectionScore<String>> result;
        if (usePurityFilter) {
            result = projectionService.cleanScaleByPurity(aId, bId, topN, includeAnchors);
        } else {
            result = projectionService.cleanSemanticScale(aId, bId, topN, includeAnchors);
        }

        return result.stream()
                .map(s -> new ProjectionScoreView<>(s.id(), s.coordinate(), s.orthogonalDistance(), s.purity()))
                .toList();
    }

    @Override
    public LabResultView<String> solveVectorArithmetic(VectorExpression<String> expr, int k) {
        ensureLoaded();
        LabResult<String> result = vectorLab.solve(expr, k);
        List<NeighborView<String>> neighbors = result.neighbors().stream()
                .map(n -> new NeighborView<>(n.id(), n.distance(), String.format("%s   |   dist=%.5f", n.id(), n.distance())))
                .toList();
        return new LabResultView<>(neighbors);
    }

    @Override
    public LabResultView<String> subspaceGrouping(Set<String> selectedIds, int k, boolean excludeSelected) {
        ensureLoaded();
        if (selectedIds == null || selectedIds.isEmpty()) {
            throw new IllegalArgumentException("selectedIds must not be null or empty");
        }
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        for (String id : selectedIds) {
            if (!group.contains(id)) {
                throw new IllegalArgumentException("Unknown id: " + id);
            }
        }

        Vector centroid = centroidOfSelected(selectedIds);
        Set<String> excluded = excludeSelected ? selectedIds : Set.of();
        List<NeighborView<String>> neighbors = knn.topK(centroid, k, excluded).stream()
                .map(n -> new NeighborView<>(n.id(), n.distance(), String.format("%s   |   dist=%.5f", n.id(), n.distance())))
                .toList();
        return new LabResultView<>(neighbors);
    }

    @Override
    public List<ViewPoint> vectorArithmeticPathInView(VectorExpression<String> expr) {
        ensureLoaded();
        Objects.requireNonNull(expr, "expr must not be null");
        if (expr.terms().isEmpty()) return List.of();

        List<ViewPoint> path = new ArrayList<>();
        Term<String> first = expr.terms().get(0);
        Vector acc = group.require(first.id(), displayRep);
        if (first.sign() == -1) acc = acc.scale(-1.0);
        path.add(viewSpace.project(acc));

        for (int i = 1; i < expr.terms().size(); i++) {
            Term<String> term = expr.terms().get(i);
            Vector v = group.require(term.id(), displayRep);
            acc = (term.sign() == 1) ? acc.add(v) : acc.subtract(v);
            path.add(viewSpace.project(acc));
        }

        return path;
    }

    @Override
    public void setOverlayPath(List<ViewPoint> path) { this.overlayPath = (path == null) ? List.of() : List.copyOf(path); }

    @Override
    public void clearOverlayPath() { this.overlayPath = List.of(); }

    @Override
    public List<ViewPoint> getOverlayPath() { return overlayPath; }


    @Override
    public AppliedViewConfig applyViewConfiguration(boolean prefer3D, int requestedX, int requestedY, Integer requestedZ) {
        ensureLoaded();

        int dim = representationDimension(currentDisplayRepresentation());
        if (dim < 2) {
            throw new IllegalStateException("Representation has dimension " + dim + "; cannot build a 2D view.");
        }

        int x = clampAxis(requestedX, dim);
        int y = clampAxis(requestedY, dim);
        if (x == y) {
            y = firstAvailableAxis(dim, x);
        }

        boolean threeD = prefer3D && dim >= 3;

        int z = requestedZ == null ? 2 : requestedZ;
        z = clampAxis(z, dim);
        if (threeD) {
            if (z == x || z == y) {
                z = firstAvailableAxis(dim, x, y);
            }
            this.mode = new ViewMode3D(x, y, z);
        } else {
            this.mode = new ViewMode2D(x, y);
        }

        viewSpace.setMode(this.mode);
        recenterIfPossible();

        return new AppliedViewConfig(threeD, x, y, z);
    }

    private Vector centroidOfSelected(Set<String> selectedIds) {
        Vector sum = null;
        int count = 0;

        for (String id : selectedIds) {
            Vector v = group.require(id, FULL);
            sum = (sum == null) ? v : sum.add(v);
            count++;
        }

        return sum.scale(1.0 / count);
    }

    private static int clampAxis(int axis, int dim) {
        if (dim <= 0) return 0;
        if (axis < 0) return 0;
        if (axis >= dim) return dim - 1;
        return axis;
    }

    private static int firstAvailableAxis(int dim, int... forbidden) {
        java.util.Set<Integer> blocked = new java.util.HashSet<>();
        for (int v : forbidden) blocked.add(v);
        for (int i = 0; i < dim; i++) {
            if (!blocked.contains(i)) return i;
        }
        return 0;
    }

    private void ensureLoaded() {
        if (group == null || viewSpace == null) throw new IllegalStateException("No dataset loaded.");
    }

    private void recenterIfPossible() {
        if (group == null || viewSpace == null || group.ids().isEmpty()) return;
        String target = centeredId;
        if (target == null || !group.contains(target)) target = group.ids().iterator().next();
        viewSpace.centerOn(target);
        centeredId = target;
    }
}
