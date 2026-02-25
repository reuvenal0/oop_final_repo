package org.ui.fx;

import javafx.animation.PauseTransition;
import javafx.scene.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import org.app.api.ExplorerUseCases;
import org.app.api.dto.NeighborView;
import org.view.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * Lightweight 3D point-cloud renderer for JavaFX.
 *
 * Performance decisions:
 * - Reuses Sphere nodes through a tiny pool (no full scene rebuild per refresh).
 * - Coalesces rapid refresh requests using a short throttle.
 * - Rebuilds geometry only when cloud/center data actually changes.
 */
public final class Cloud3DView extends SubScene {

    private static final double BASE_RADIUS = 2.7;
    private static final double HIGHLIGHT_SCALE = 1.8;
    private static final double NORMAL_SCALE = 1.0;
    private static final double WORLD_TARGET_RADIUS = 320.0;

    private static final double INITIAL_CAMERA_DISTANCE = 900.0;
    private static final double MIN_CAMERA_DISTANCE = 120.0;
    private static final double MAX_CAMERA_DISTANCE = 8000.0;
    private static final double ZOOM_SPEED = 0.0015;

    private static final double ROTATE_SPEED = 0.22;
    private static final double PAN_SPEED = 0.60;
    private static final double MIN_PITCH = -85.0;
    private static final double MAX_PITCH = 85.0;

    private static final long REFRESH_THROTTLE_MS = 35;

    private static final double OVERLAY_RADIUS = 6.0;

    private static final PhongMaterial MAT_NORMAL = new PhongMaterial(Color.web("#8ea0ff"));
    private static final PhongMaterial MAT_SELECTED = new PhongMaterial(Color.web("#ff8a00"));
    private static final PhongMaterial MAT_NEIGHBOR = new PhongMaterial(Color.web("#4dd0e1"));
    private static final PhongMaterial MAT_OVERLAY = new PhongMaterial(Color.web("#ff8a00"));

    private final ExplorerUseCases vm;

    private final Group sceneRoot = new Group();
    private final Group panGroup = new Group();
    private final Group worldGroup = new Group();
    private final Group pointsGroup = new Group();
    private final Group overlayGroup = new Group();
    private final Group labelGroup = new Group();

    private final Rotate yawRotate = new Rotate(-25, Rotate.Y_AXIS);
    private final Rotate pitchRotate = new Rotate(15, Rotate.X_AXIS);

    private final PerspectiveCamera camera = new PerspectiveCamera(true);

    private final List<Sphere> pointPool = new ArrayList<>();
    private final List<Sphere> overlayPool = new ArrayList<>();
    private final Map<String, Sphere> idToSphere = new HashMap<>();
    private final Map<Sphere, String> sphereToId = new HashMap<>();

    private final PauseTransition refreshThrottle = new PauseTransition(Duration.millis(REFRESH_THROTTLE_MS));

    private Consumer<String> onSelection = id -> {};

    private int neighborK = 10;
    private String selectedId;

    private double anchorX;
    private double anchorY;

    private double yaw = -25;
    private double pitch = 15;
    private double panX = 0;
    private double panY = 0;
    private double cameraDistance = INITIAL_CAMERA_DISTANCE;

    private long lastCloudSignature = Long.MIN_VALUE;
    private long lastOverlaySignature = Long.MIN_VALUE;
    private String lastCenterKey = "";
    private Point_3D lastCenter = new Point_3D(0, 0, 0);
    private double lastScale = 1.0;

    public Cloud3DView(ExplorerUseCases vm) {
        super(new Group(), 800, 800, true, SceneAntialiasing.BALANCED);
        this.vm = Objects.requireNonNull(vm, "vm must not be null");

        ((Group) getRoot()).getChildren().add(sceneRoot);

        worldGroup.getTransforms().addAll(yawRotate, pitchRotate);
        worldGroup.getChildren().addAll(pointsGroup, overlayGroup, labelGroup);
        panGroup.getChildren().add(worldGroup);
        sceneRoot.getChildren().add(panGroup);

        camera.setNearClip(0.1);
        camera.setFarClip(100_000);
        applyCameraDistance();
        setCamera(camera);

        setOnMousePressed(e -> {
            anchorX = e.getSceneX();
            anchorY = e.getSceneY();
        });

        setOnMouseDragged(e -> {
            double dx = e.getSceneX() - anchorX;
            double dy = e.getSceneY() - anchorY;
            anchorX = e.getSceneX();
            anchorY = e.getSceneY();

            if (!e.isPrimaryButtonDown()) return;

            if (e.isShiftDown()) {
                panX += dx * PAN_SPEED;
                panY += dy * PAN_SPEED;
                applyPan();
                return;
            }

            yaw += dx * ROTATE_SPEED;
            pitch = clamp(pitch - dy * ROTATE_SPEED, MIN_PITCH, MAX_PITCH);
            applyRotation();
        });

        setOnScroll(this::handleScrollZoom);

        setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            Node picked = e.getPickResult().getIntersectedNode();
            if (picked instanceof Sphere s) {
                String id = sphereToId.get(s);
                if (id != null) {
                    selectedId = id;
                    onSelection.accept(id);
                    updateHighlightsAndLabels();
                }
            }
        });

        refreshThrottle.setOnFinished(e -> refreshNow());

        applyRotation();
        applyPan();
        refresh();
    }

    public void setOnSelection(Consumer<String> onSelection) {
        this.onSelection = (onSelection == null) ? (id -> {}) : onSelection;
    }

    public void setSelectedId(String id) {
        selectedId = id;
        refresh();
    }

    public void setNeighborK(int k) {
        neighborK = Math.max(0, k);
        refresh();
    }

    public void refresh() {
        refreshThrottle.playFromStart();
    }

    public void resetCamera() {
        cameraDistance = INITIAL_CAMERA_DISTANCE;
        yaw = -25;
        pitch = 15;
        panX = 0;
        panY = 0;
        applyCameraDistance();
        applyRotation();
        applyPan();
        refresh();
    }

    private void refreshNow() {
        PointCloud<String> cloud;
        try {
            cloud = vm.getPointCloud();
        } catch (Exception ex) {
            clearAll();
            return;
        }

        if (cloud.mode().viewDim() != 3) {
            clearAll();
            return;
        }

        Point_3D center = vm.getCenter()
                .filter(p -> p.dim() == 3)
                .map(p -> (Point_3D) p)
                .orElse(new Point_3D(0, 0, 0));

        String centerKey = center.x() + ":" + center.y() + ":" + center.z();
        long cloudSignature = signature(cloud);

        boolean rebuildPoints = (cloudSignature != lastCloudSignature) || !centerKey.equals(lastCenterKey);
        if (rebuildPoints) {
            rebuildPointGeometry(cloud, center);
            lastCloudSignature = cloudSignature;
            lastCenterKey = centerKey;
            lastCenter = center;
        }

        long overlaySignature = signature(vm.getOverlayPath());
        if (rebuildPoints || overlaySignature != lastOverlaySignature) {
            rebuildOverlay(vm.getOverlayPath(), lastCenter, lastScale);
            lastOverlaySignature = overlaySignature;
        }

        updateHighlightsAndLabels();
    }

    private void rebuildPointGeometry(PointCloud<String> cloud, Point_3D center) {
        List<LabeledPoint<String>> points = cloud.points();
        double maxAbs = 1e-9;
        for (LabeledPoint<String> lp : points) {
            Point_3D p = (Point_3D) lp.point();
            maxAbs = Math.max(maxAbs, Math.abs(p.x() - center.x()));
            maxAbs = Math.max(maxAbs, Math.abs(p.y() - center.y()));
            maxAbs = Math.max(maxAbs, Math.abs(p.z() - center.z()));
        }
        lastScale = WORLD_TARGET_RADIUS / maxAbs;

        idToSphere.clear();
        sphereToId.clear();

        ensurePointPool(points.size());

        int idx = 0;
        for (LabeledPoint<String> lp : points) {
            Point_3D p = (Point_3D) lp.point();
            Sphere s = pointPool.get(idx++);
            String id = lp.id();

            s.setVisible(true);
            s.setTranslateX((p.x() - center.x()) * lastScale);
            s.setTranslateY(-(p.y() - center.y()) * lastScale);
            s.setTranslateZ((p.z() - center.z()) * lastScale);
            s.setScaleX(NORMAL_SCALE);
            s.setScaleY(NORMAL_SCALE);
            s.setScaleZ(NORMAL_SCALE);
            s.setMaterial(MAT_NORMAL);

            idToSphere.put(id, s);
            sphereToId.put(s, id);
        }

        for (int i = idx; i < pointPool.size(); i++) {
            pointPool.get(i).setVisible(false);
        }
    }

    private void rebuildOverlay(List<ViewPoint> path, Point_3D center, double scale) {
        int needed = (path == null) ? 0 : path.size();
        ensureOverlayPool(needed);

        int idx = 0;
        if (path != null) {
            for (ViewPoint vp : path) {
                if (vp == null) continue;

                double x;
                double y;
                double z;
                if (vp.dim() == 3) {
                    Point_3D p = (Point_3D) vp;
                    x = p.x();
                    y = p.y();
                    z = p.z();
                } else if (vp.dim() == 2) {
                    Point_2D p = (Point_2D) vp;
                    x = p.x();
                    y = p.y();
                    z = 0.0;
                } else {
                    continue;
                }

                Sphere s = overlayPool.get(idx++);
                s.setVisible(true);
                s.setTranslateX((x - center.x()) * scale);
                s.setTranslateY(-(y - center.y()) * scale);
                s.setTranslateZ((z - center.z()) * scale);
            }
        }

        for (int i = idx; i < overlayPool.size(); i++) {
            overlayPool.get(i).setVisible(false);
        }
    }

    private void updateHighlightsAndLabels() {
        Set<String> highlighted = new HashSet<>();
        if (selectedId != null) {
            highlighted.add(selectedId);
            if (neighborK > 0) {
                try {
                    for (NeighborView<String> n : vm.nearestNeighborsDetailed(selectedId, neighborK)) {
                        highlighted.add(n.id());
                    }
                } catch (Exception ignored) {
                    // UI-only decoration should not fail the 3D render.
                }
            }
        }

        for (Map.Entry<String, Sphere> e : idToSphere.entrySet()) {
            String id = e.getKey();
            Sphere s = e.getValue();

            if (selectedId != null && selectedId.equals(id)) {
                s.setMaterial(MAT_SELECTED);
                s.setScaleX(HIGHLIGHT_SCALE);
                s.setScaleY(HIGHLIGHT_SCALE);
                s.setScaleZ(HIGHLIGHT_SCALE);
            } else if (highlighted.contains(id)) {
                s.setMaterial(MAT_NEIGHBOR);
                s.setScaleX(1.35);
                s.setScaleY(1.35);
                s.setScaleZ(1.35);
            } else {
                s.setMaterial(MAT_NORMAL);
                s.setScaleX(NORMAL_SCALE);
                s.setScaleY(NORMAL_SCALE);
                s.setScaleZ(NORMAL_SCALE);
            }
        }

        labelGroup.getChildren().clear();
        if (selectedId == null) return;

        List<String> labelIds = new ArrayList<>();
        labelIds.add(selectedId);
        if (neighborK > 0) {
            try {
                for (NeighborView<String> n : vm.nearestNeighborsDetailed(selectedId, neighborK)) {
                    labelIds.add(n.id());
                }
            } catch (Exception ignored) {
                // UI-only labels.
            }
        }

        for (int i = 0; i < labelIds.size(); i++) {
            String id = labelIds.get(i);
            Sphere s = idToSphere.get(id);
            if (s == null || !s.isVisible()) continue;

            Text t = new Text(id);
            boolean isSelected = (i == 0);
            t.setFill(isSelected ? Color.web("#ff8a00") : Color.WHITE);
            t.setStroke(Color.rgb(0, 0, 0, 140));
            t.setStrokeWidth(isSelected ? 1.2 : 0.9);
            t.setStyle(isSelected
                    ? "-fx-font-size: 18px; -fx-font-weight: 800;"
                    : "-fx-font-size: 14px; -fx-font-weight: 700;");
            t.setMouseTransparent(true);
            t.setDepthTest(DepthTest.ENABLE);

            t.setTranslateX(s.getTranslateX() + 8);
            t.setTranslateY(s.getTranslateY() - 8);
            t.setTranslateZ(s.getTranslateZ());

            labelGroup.getChildren().add(t);
        }
    }

    private void ensurePointPool(int count) {
        while (pointPool.size() < count) {
            Sphere s = new Sphere(BASE_RADIUS);
            pointsGroup.getChildren().add(s);
            pointPool.add(s);
        }
    }

    private void ensureOverlayPool(int count) {
        while (overlayPool.size() < count) {
            Sphere s = new Sphere(OVERLAY_RADIUS);
            s.setMaterial(MAT_OVERLAY);
            s.setMouseTransparent(true);
            overlayGroup.getChildren().add(s);
            overlayPool.add(s);
        }
    }

    private void handleScrollZoom(ScrollEvent e) {
        double factor = Math.exp(-e.getDeltaY() * ZOOM_SPEED);
        cameraDistance = clamp(cameraDistance * factor, MIN_CAMERA_DISTANCE, MAX_CAMERA_DISTANCE);
        applyCameraDistance();
        e.consume();
    }

    private void applyCameraDistance() {
        camera.setTranslateZ(-cameraDistance);
    }

    private void applyRotation() {
        yawRotate.setAngle(yaw);
        pitchRotate.setAngle(pitch);
    }

    private void applyPan() {
        panGroup.setTranslateX(panX);
        panGroup.setTranslateY(panY);
    }

    private void clearAll() {
        idToSphere.clear();
        sphereToId.clear();
        for (Sphere s : pointPool) s.setVisible(false);
        for (Sphere s : overlayPool) s.setVisible(false);
        labelGroup.getChildren().clear();
        lastCloudSignature = Long.MIN_VALUE;
        lastOverlaySignature = Long.MIN_VALUE;
        lastCenterKey = "";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long signature(PointCloud<String> cloud) {
        long h = 17;
        h = 31 * h + cloud.mode().viewDim();
        List<LabeledPoint<String>> points = cloud.points();
        h = 31 * h + points.size();
        if (!points.isEmpty()) {
            LabeledPoint<String> first = points.get(0);
            LabeledPoint<String> last = points.get(points.size() - 1);
            h = 31 * h + Objects.hashCode(first.id());
            h = 31 * h + Objects.hashCode(last.id());
        }
        return h;
    }

    private static long signature(List<ViewPoint> path) {
        if (path == null || path.isEmpty()) return 0L;
        long h = 17;
        h = 31 * h + path.size();
        ViewPoint first = path.get(0);
        ViewPoint last = path.get(path.size() - 1);
        h = 31 * h + ((first == null) ? 0 : first.dim());
        h = 31 * h + ((last == null) ? 0 : last.dim());
        return h;
    }
}
