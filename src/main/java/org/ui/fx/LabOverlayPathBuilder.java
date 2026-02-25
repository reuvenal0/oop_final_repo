package org.ui.fx;

import org.app.api.dto.NeighborView;
import org.view.LabeledPoint;
import org.view.PointCloud;
import org.view.ViewPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LabOverlayPathBuilder {

    private LabOverlayPathBuilder() {}

    static List<ViewPoint> routeForTerms(PointCloud<String> cloud, List<String> termIds) {
        return mapIdsToViewPoints(cloud, termIds);
    }

    static List<ViewPoint> routeForSolution(PointCloud<String> cloud, List<String> termIds, List<NeighborView<String>> neighbors) {
        List<String> routeIds = new ArrayList<>(termIds);
        if (neighbors != null && !neighbors.isEmpty()) {
            routeIds.add(neighbors.get(0).id());
        }
        return mapIdsToViewPoints(cloud, routeIds);
    }

    private static List<ViewPoint> mapIdsToViewPoints(PointCloud<String> cloud, List<String> idsInOrder) {
        if (cloud == null || idsInOrder == null || idsInOrder.isEmpty()) {
            return List.of();
        }

        Map<String, ViewPoint> byId = new HashMap<>(cloud.points().size());
        for (LabeledPoint<String> lp : cloud.points()) {
            byId.put(lp.id(), lp.point());
        }

        List<ViewPoint> path = new ArrayList<>();
        for (String id : idsInOrder) {
            ViewPoint point = byId.get(id);
            if (point != null) {
                path.add(point);
            }
        }
        return path;
    }
}
