package org.app.api;

import org.app.api.dto.AppliedViewConfig;
import org.app.api.dto.LabResultView;
import org.app.api.dto.MetricOption;
import org.app.api.dto.NeighborView;
import org.app.api.dto.ProjectionScoreView;
import org.io.Representation;
import org.lab.VectorExpression;
import org.view.PointCloud;
import org.view.ViewMode;
import org.view.ViewPoint;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Application boundary consumed by JavaFX UI.
 * Keeps UI independent from metrics/projection concrete packages.
 */
public interface ExplorerUseCases {

    void load(Path anyPath) throws Exception;

    void loadDefaultIfPresent() throws Exception;

    void generateDefaultDataset() throws Exception;

    PointCloud<String> getPointCloud();

    void setViewMode(ViewMode mode);

    ViewMode getViewMode();

    /**
     * Applies requested view mode + axes and returns the validated/coerced configuration.
     */
    AppliedViewConfig applyViewConfiguration(boolean prefer3D, int requestedX, int requestedY, Integer requestedZ);

    Optional<ViewPoint> getCenter();

    void centerOn(String id);

    boolean containsId(String id);

    List<Representation> availableRepresentations();

    Representation currentDisplayRepresentation();

    void setDisplayRepresentation(Representation representation);

    int representationDimension(Representation representation);

    List<MetricOption> availableMetrics();

    String currentMetricId();

    void setMetric(String metricId);

    List<NeighborView<String>> nearestNeighborsDetailed(String id, int k);

    List<ProjectionScoreView<String>> customProjectionScale(
            String aId,
            String bId,
            int topN,
            boolean centeredAxis,
            boolean includeAnchors,
            boolean usePurityFilter
    );

    LabResultView<String> solveVectorArithmetic(VectorExpression<String> expr, int k);

    LabResultView<String> subspaceGrouping(Set<String> selectedIds, int k, boolean excludeSelected);

    List<ViewPoint> vectorArithmeticPathInView(VectorExpression<String> expr);

    default List<ViewPoint> getOverlayPath() { return List.of(); }

    default void setOverlayPath(List<ViewPoint> path) { }

    default void clearOverlayPath() { }

    default void clearSelection() { }
}
