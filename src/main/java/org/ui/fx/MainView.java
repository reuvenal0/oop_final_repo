package org.ui.fx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.io.Representation;
import org.app.api.ExplorerUseCases;
import org.app.api.dto.MetricOption;
import org.app.api.dto.NeighborView;
import org.app.api.dto.ProjectionScoreView;
import org.app.api.dto.AppliedViewConfig;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Modern Main UI:
 * - Left: Representations list + view controls (2D/3D, axes, metric, K) + search/center
 * - Center: 2D/3D canvas
 * - Right: Tabs (Neighbors, Projection)
 * - Bottom: Status bar
 *
 * No file picking here. Data should be loaded by the ExplorerUseCases.
 */
public final class MainView extends BorderPane {

    private final ExplorerUseCases UseCases;

    private final Scatter2DView view2d;
    private final Cloud3DView view3d;

    // View controls
    private final ToggleGroup dimToggle = new ToggleGroup();
    private final ComboBox<Integer> xAxis = new ComboBox<>();
    private final ComboBox<Integer> yAxis = new ComboBox<>();
    private final ComboBox<Integer> zAxis = new ComboBox<>();

    // Representations list
    private final ListView<Representation> repList = new ListView<>();
    private final Label displayedRepLabel = new Label("Displayed: (none)");

    // Search / selection
    private final TextField searchField = new TextField();
    private final Button centerBtn = new Button("Center");

    // Metric + Neighbors
    private final ComboBox<MetricOption> metricCombo = new ComboBox<>();
    private final Slider kSlider = new Slider(1, 30, 10);
    private final ListView<NeighborView<String>> neighborsList = new ListView<>();

    private final Label selectedLabel = new Label("Selected: (none)");
    private final Label statusBar = new Label("Ready.");

    private String selectedId = null;

    // Center zoom (when user hits Center)
    private static final double CENTER_ZOOM_2D = 2.7;

    // Initial zoom on open (requested: x4 inward)
    private static final double INITIAL_ZOOM_2D = 4.0;

    // =======================
    // Projection UI
    // =======================
    private final TextField projAField = new TextField();
    private final TextField projBField = new TextField();
    private final Button projRunBtn = new Button("Project");
    private final Spinner<Integer> projTopN = new Spinner<>(10, 2000, 200, 50);
    private final CheckBox projIncludeAnchors = new CheckBox("Include anchors");
    private final CheckBox projUsePurity = new CheckBox("Purity filter (recommended)");
    private final CheckBox projCentered = new CheckBox("Centered axis (A negative, B positive)");

    private final TableView<ProjectionScoreView<String>> projTable = new TableView<>();
    private final ObservableList<ProjectionScoreView<String>> projItems = FXCollections.observableArrayList();

    // Picking anchors from the graph
    private enum AnchorPickMode { NONE, A, B }
    private AnchorPickMode pickMode = AnchorPickMode.NONE;

    private final ToggleButton pickABtn = new ToggleButton("Pick A");
    private final ToggleButton pickBBtn = new ToggleButton("Pick B");
    private final ToggleGroup pickToggle = new ToggleGroup();

    private final Button useSelectedAsABtn = new Button("Use selected as A");
    private final Button useSelectedAsBBtn = new Button("Use selected as B");

    private final LabTabController labTab;
    private final GroupTabController groupTab;

    public MainView(ExplorerUseCases UseCases) {
        this.UseCases = Objects.requireNonNull(UseCases);

        this.view2d = new Scatter2DView(UseCases);
        this.view3d = new Cloud3DView(UseCases);
        this.labTab = new LabTabController(UseCases, () -> selectedId, id -> { onSelect(id); tryCenter(id); }, this::setStatus);
        this.groupTab = new GroupTabController(UseCases, () -> selectedId, id -> { onSelect(id); tryCenter(id); }, this::setStatus);

        setPadding(new Insets(14));

        setLeft(buildLeftPanel());
        setCenter(buildCenter());
        setRight(buildRightPanel());
        setBottom(buildStatusBar());

        // Default mode (validated by app layer)
        applyRequestedViewConfig(false);

        // Hook selection from views
        view2d.setOnSelection(this::onSelect);
        view2d.setNeighborK((int) Math.round(kSlider.getValue()));
        view3d.setOnSelection(this::onSelect);

        // Fill representations if already loaded (or show empty state)
        refreshRepresentations();

        // Initial zoom (requested)
        view2d.resetCamera(INITIAL_ZOOM_2D);

        refreshNow();
    }

    // =======================
    // Public helpers (FxApp)
    // =======================

    public void refreshNow() {
        view2d.refresh();
        view3d.refresh();
    }

    public void setStatus(String msg) {
        statusBar.setText(msg);
    }

    /** Call this after dataset loads to populate the representations list + displayed chip. */
    public void refreshRepresentations() {
        refreshRepresentationsUI();
    }

    // =======================
    // Layout
    // =======================

    private Node buildLeftPanel() {
        // --- Representations card ---
        Label repsTitle = new Label("Representations");
        repsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        displayedRepLabel.setStyle("""
                -fx-padding: 4 10;
                -fx-background-color: #eef4ff;
                -fx-text-fill: #1e66ff;
                -fx-font-weight: 700;
                -fx-background-radius: 999;
                -fx-border-radius: 999;
                """);

        Button refreshBtn = new Button("↻");
        refreshBtn.setFocusTraversable(false);
        refreshBtn.setStyle("-fx-background-radius: 10; -fx-padding: 4 10;");
        refreshBtn.setOnAction(e -> refreshRepresentationsUI());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button loadBtn = new Button("📂");
        loadBtn.setFocusTraversable(false);
        loadBtn.setStyle("-fx-background-radius: 10; -fx-padding: 4 10;");
        loadBtn.setOnAction(e -> chooseAndLoadFile());

        Button generateBtn = new Button("⚙ Generate");
        generateBtn.setFocusTraversable(false);
        generateBtn.setStyle("-fx-background-radius: 10; -fx-padding: 4 10;");
        generateBtn.setTooltip(new Tooltip("Run Python embedder and generate default dataset"));
        generateBtn.setOnAction(e -> generateDatasetAsync(generateBtn));

        HBox repsHeader = new HBox(10, repsTitle, spacer, displayedRepLabel, loadBtn, generateBtn, refreshBtn);
        repsHeader.setAlignment(Pos.CENTER_LEFT);

        repList.setPrefHeight(220);
        repList.setFocusTraversable(false);
        repList.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #e8e8e8;
                -fx-border-radius: 10;
                -fx-background-radius: 10;
                """);

        repList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Representation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setGraphic(null);
                    return;
                }
                setText(item.name());
                setStyle("-fx-padding: 10 12; -fx-font-size: 13px;");
            }
        });

        repList.getSelectionModel().selectedItemProperty().addListener((obs, old, rep) -> {
            if (rep == null) return;
            try {
                UseCases.setDisplayRepresentation(rep);
                displayedRepLabel.setText("Displayed: " + rep.name());
                setStatus("Display representation: " + rep.name());

                // IMPORTANT: update axis range to match representation dimension
                initAxesForRepresentation(rep);
                labTab.refreshOverlayPathFromTerms();

                refreshNow();
            } catch (Exception ex) {
                setStatus("Failed to switch representation: " + ex.getMessage());
            }
        });

        VBox repsCard = card(repsHeader, repList);

        // --- View controls card ---
        Label viewTitle = new Label("View");
        viewTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        RadioButton rb2d = new RadioButton("2D");
        rb2d.setToggleGroup(dimToggle);
        rb2d.setSelected(true);

        RadioButton rb3d = new RadioButton("3D");
        rb3d.setToggleGroup(dimToggle);

        HBox dimRow = new HBox(10, rb2d, rb3d);
        dimRow.setAlignment(Pos.CENTER_LEFT);

        dimToggle.selectedToggleProperty().addListener((obs, old, now) -> {
            applyRequestedViewConfig(is3D());
            labTab.refreshOverlayPathFromTerms();
            refreshNow();
        });

        xAxis.setOnAction(e -> { applyRequestedViewConfig(is3D()); labTab.refreshOverlayPathFromTerms(); refreshNow(); });
        yAxis.setOnAction(e -> { applyRequestedViewConfig(is3D()); labTab.refreshOverlayPathFromTerms(); refreshNow(); });
        zAxis.setOnAction(e -> { applyRequestedViewConfig(is3D()); labTab.refreshOverlayPathFromTerms(); refreshNow(); });

        VBox axesBox = new VBox(10,
                labeledRow("X", xAxis),
                labeledRow("Y", yAxis),
                labeledRow("Z", zAxis)
        );

        Label metricTitle = new Label("Metric");
        metricTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        metricCombo.getItems().setAll(UseCases.availableMetrics());
        metricCombo.getSelectionModel().select(
                UseCases.availableMetrics().stream()
                        .filter(m -> m.id().equalsIgnoreCase(UseCases.currentMetricId()))
                        .findFirst()
                        .orElse(null)
        );
        metricCombo.setOnAction(e -> {
            MetricOption m = metricCombo.getValue();
            if (m != null) {
                UseCases.setMetric(m.id());
                setStatus("Metric: " + m.label());
                refreshNeighbors();
                // Metric affects neighbor lines in the views as well
                view2d.refresh();
                view3d.refresh();
            }
        });

        Label knnTitle = new Label("Neighbors (K)");
        knnTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        kSlider.setMajorTickUnit(5);
        kSlider.setMinorTickCount(4);
        kSlider.setShowTickLabels(true);
        kSlider.setShowTickMarks(true);
        kSlider.valueProperty().addListener((obs, old, now) -> {
            refreshNeighbors();
            int k = (int) Math.round(kSlider.getValue());
            view2d.setNeighborK(k);
            view3d.setNeighborK(k);
        });

        VBox viewCard = card(
                viewTitle,
                new Label("Dimension"),
                dimRow,
                new Separator(),
                new Label("Axes"),
                axesBox
        );

        // --- Search card ---
        Label searchTitle = new Label("Search");
        searchTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        searchField.setPromptText("Type a word id...");
        centerBtn.setMaxWidth(Double.MAX_VALUE);
        centerBtn.setOnAction(e -> onCenter());

        VBox searchCard = card(searchTitle, searchField, centerBtn);

        VBox left = new VBox(14, repsCard, viewCard, searchCard);
        left.setPrefWidth(330);
        left.setPadding(new Insets(6, 10, 6, 6));
        return left;
    }

    private Node buildCenter() {
        StackPane stack = new StackPane(view2d, view3d);

        view3d.setVisible(false);
        view3d.managedProperty().bind(view3d.visibleProperty());
        view2d.managedProperty().bind(view2d.visibleProperty());

        stack.setStyle("-fx-background-color: white; -fx-border-color: #e8e8e8; -fx-border-radius: 14; -fx-background-radius: 14;");
        stack.setPadding(new Insets(10));

        return stack;
    }

    private Node buildRightPanel() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab neighborsTab = new Tab("Neighbors", buildNeighborsTab());
        Tab projectionTab = new Tab("Projection", buildProjectionTab());
        Tab groupTabView = new Tab("Group", groupTab.build());
        Tab labTabView = new Tab("Lab", labTab.build());

        // Leaving Projection tab stops pick mode (prevents confusing UX)
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> {
            if (now == null || !"Projection".equals(now.getText())) {
                stopAnchorPicking(); // existing
            }
            if (now == null || !"Lab".equals(now.getText())) {
                labTab.stopPicking();
            }
        });

        tabs.getTabs().setAll(neighborsTab, projectionTab, groupTabView, labTabView);

        VBox right = new VBox(tabs);
        right.setPrefWidth(360);
        right.setPadding(new Insets(6, 6, 6, 10));
        return right;
    }

    private Node buildNeighborsTab() {
        Label title = new Label("Selection");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        neighborsList.setPrefHeight(520);
        neighborsList.setFocusTraversable(false);

        // Render: word + distance (+ similarity when metric is cosine)
        neighborsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(NeighborView<String> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(NeighborDisplayFormatter.format(item));
            }
        });


        neighborsList.setOnMouseClicked(e -> {
            NeighborView<String> n = neighborsList.getSelectionModel().getSelectedItem();
            if (n != null) {
                onSelect(n.id());
                tryCenter(n.id());
            }
        });

        VBox box = new VBox(10,
                title,
                selectedLabel,
                new Separator(),
                new Label("Metric"),
                metricCombo,
                new Label("Neighbors (K)"),
                kSlider,
                new Separator(),
                new Label("Nearest neighbors"),
                neighborsList
        );
        box.setPadding(new Insets(10));
        return box;
    }

    private Node buildProjectionTab() {
        Label title = new Label("Custom Projection");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        projAField.setPromptText("Anchor A (pick from graph)");
        projBField.setPromptText("Anchor B (pick from graph)");

        projUsePurity.setSelected(true);
        projCentered.setSelected(true);
        projIncludeAnchors.setSelected(false);

        projTopN.setEditable(true);
        projTopN.setMaxWidth(Double.MAX_VALUE);

        // Pick buttons
        pickABtn.setToggleGroup(pickToggle);
        pickBBtn.setToggleGroup(pickToggle);
        pickABtn.setMaxWidth(Double.MAX_VALUE);
        pickBBtn.setMaxWidth(Double.MAX_VALUE);

        pickToggle.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == pickABtn) {
                pickMode = AnchorPickMode.A;
                setStatus("Pick A: click a point in the graph");
            } else if (now == pickBBtn) {
                pickMode = AnchorPickMode.B;
                setStatus("Pick B: click a point in the graph");
            } else {
                pickMode = AnchorPickMode.NONE;
            }
        });

        HBox pickRow = new HBox(10, pickABtn, pickBBtn);
        pickRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(pickABtn, Priority.ALWAYS);
        HBox.setHgrow(pickBBtn, Priority.ALWAYS);

        // Use selected buttons
        useSelectedAsABtn.setMaxWidth(Double.MAX_VALUE);
        useSelectedAsBBtn.setMaxWidth(Double.MAX_VALUE);

        useSelectedAsABtn.setOnAction(e -> {
            if (selectedId == null) {
                setStatus("No selected point to use as anchor A.");
                return;
            }
            projAField.setText(selectedId);
            setStatus("Projection anchor A set to selected: " + selectedId);
        });

        useSelectedAsBBtn.setOnAction(e -> {
            if (selectedId == null) {
                setStatus("No selected point to use as anchor B.");
                return;
            }
            projBField.setText(selectedId);
            setStatus("Projection anchor B set to selected: " + selectedId);
        });

        HBox useSelectedRow = new HBox(10, useSelectedAsABtn, useSelectedAsBBtn);
        useSelectedRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(useSelectedAsABtn, Priority.ALWAYS);
        HBox.setHgrow(useSelectedAsBBtn, Priority.ALWAYS);

        // Table
        setupProjectionTable();

        projRunBtn.setMaxWidth(Double.MAX_VALUE);
        projRunBtn.setOnAction(e -> runProjection());

        VBox box = new VBox(10,
                title,
                projAField,
                projBField,
                new Label("Pick anchors from the graph"),
                pickRow,
                useSelectedRow,
                new Separator(),
                labeledRow("Top N", projTopN),
                projCentered,
                projUsePurity,
                projIncludeAnchors,
                projRunBtn,
                new Separator(),
                projTable
        );
        box.setPadding(new Insets(10));
        return box;
    }

    private void setupProjectionTable() {
        if (!projTable.getColumns().isEmpty()) return;

        TableColumn<ProjectionScoreView<String>, String> idCol = new TableColumn<>("Word");
        idCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().id()));
        idCol.setPrefWidth(160);

        TableColumn<ProjectionScoreView<String>, Number> tCol = new TableColumn<>("t");
        tCol.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().coordinate()));
        tCol.setPrefWidth(80);

        TableColumn<ProjectionScoreView<String>, Number> orthCol = new TableColumn<>("orth");
        orthCol.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().orthogonalDistance()));
        orthCol.setPrefWidth(90);

        TableColumn<ProjectionScoreView<String>, Number> purityCol = new TableColumn<>("purity");
        purityCol.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().purity()));
        purityCol.setPrefWidth(90);

        projTable.getColumns().setAll(idCol, tCol, orthCol, purityCol);
        projTable.setItems(projItems);
        projTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Click row => select + center
        projTable.setOnMouseClicked(e -> {
            ProjectionScoreView<String> s = projTable.getSelectionModel().getSelectedItem();
            if (s != null) {
                onSelect(s.id());
                tryCenter(s.id());
            }
        });
    }

    private Node buildStatusBar() {
        HBox bar = new HBox(statusBar);
        bar.setPadding(new Insets(10));
        bar.setStyle("-fx-background-color: #f7f7f7; -fx-border-color: #e3e3e3; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    private VBox card(Node... content) {
        VBox box = new VBox(10, content);
        box.setPadding(new Insets(12));
        box.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #e8e8e8;
                -fx-border-radius: 14;
                -fx-background-radius: 14;
                """);
        return box;
    }

    private HBox labeledRow(String label, Control control) {
        Label l = new Label(label);
        l.setMinWidth(18);
        l.setStyle("-fx-font-weight: 700; -fx-text-fill: #333;");

        HBox row = new HBox(10, l, control);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        control.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    // =======================
    // Representations
    // =======================

    private void refreshRepresentationsUI() {
        try {
            List<Representation> reps = UseCases.availableRepresentations();
            repList.getItems().setAll(reps);

            Representation current = UseCases.currentDisplayRepresentation();
            displayedRepLabel.setText("Displayed: " + (current == null ? "(none)" : current.name()));

            if (current != null) {
                repList.getSelectionModel().select(current);
            } else if (!reps.isEmpty()) {
                repList.getSelectionModel().select(0);
            }

            Representation selected = repList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                // Ensure axis range matches representation dimension
                initAxesForRepresentation(selected);
            }

            if (reps.isEmpty()) {
                setStatus("No representations available (dataset not loaded?).");
            }
        } catch (Exception ex) {
            repList.getItems().clear();
            displayedRepLabel.setText("Displayed: (none)");
            setStatus("Representations unavailable: " + ex.getMessage());
        }
    }

    // =======================
    // Axes: UI delegates validation/coercion policy to app layer
    // =======================

    private void initAxesForRepresentation(Representation rep) {
        int dim = UseCases.representationDimension(rep);
        if (dim <= 0) {
            setStatus("Representation " + rep.name() + " has invalid dimension: " + dim);
            return;
        }

        List<Integer> axes = java.util.stream.IntStream.range(0, dim).boxed().toList();
        xAxis.getItems().setAll(axes);
        yAxis.getItems().setAll(axes);
        zAxis.getItems().setAll(axes);

        xAxis.getSelectionModel().select(Integer.valueOf(0));
        yAxis.getSelectionModel().select(Integer.valueOf(Math.min(1, dim - 1)));
        zAxis.getSelectionModel().select(Integer.valueOf(Math.min(2, dim - 1)));

        applyRequestedViewConfig(is3D());
    }

    // =======================
    // View mode + visibility
    // =======================

    private boolean is3D() {
        Toggle t = dimToggle.getSelectedToggle();
        return t != null && ((RadioButton) t).getText().equals("3D");
    }

    private void updateVisibleView(boolean is3d) {
        view3d.setVisible(is3d);
        view2d.setVisible(!is3d);
        zAxis.setDisable(!is3d);
        zAxis.setOpacity(is3d ? 1.0 : 0.55);
    }

    private void applyRequestedViewConfig(boolean prefer3D) {
        Integer x = xAxis.getValue();
        Integer y = yAxis.getValue();
        Integer z = zAxis.getValue();
        if (x == null || y == null) return;

        try {
            AppliedViewConfig applied = UseCases.applyViewConfiguration(prefer3D, x, y, z);
            xAxis.getSelectionModel().select(Integer.valueOf(applied.xAxis()));
            yAxis.getSelectionModel().select(Integer.valueOf(applied.yAxis()));
            zAxis.getSelectionModel().select(Integer.valueOf(applied.zAxis()));
            set3DToggleSelected(applied.threeD());
            updateVisibleView(applied.threeD());
        } catch (Exception ex) {
            setStatus("View config failed: " + ex.getMessage());
            updateVisibleView(false);
            set3DToggleSelected(false);
        }
    }

    private void set3DToggleSelected(boolean use3d) {
        Toggle current = dimToggle.getSelectedToggle();
        for (Toggle t : dimToggle.getToggles()) {
            RadioButton rb = (RadioButton) t;
            if (use3d && rb.getText().equals("3D")) {
                if (current != t) dimToggle.selectToggle(t);
                return;
            }
            if (!use3d && rb.getText().equals("2D")) {
                if (current != t) dimToggle.selectToggle(t);
                return;
            }
        }
    }

    // =======================
    // Selection / Center / Neighbors
    // =======================

    private void onCenter() {
        String id = searchField.getText();
        if (id == null || id.isBlank()) return;
        tryCenter(id.trim());
    }

    private void tryCenter(String id) {
        if (!UseCases.containsId(id)) {
            setStatus("Unknown id: " + id);
            return;
        }
        try {
            UseCases.centerOn(id);
            onSelect(id);

            view2d.resetCamera(CENTER_ZOOM_2D);
            view3d.resetCamera();

            setStatus("Centered on: " + id);
            refreshNow();
        } catch (Exception ex) {
            setStatus("Center failed: " + ex.getMessage());
        }
    }

    private void onSelect(String id) {
        selectedId = id;
        selectedLabel.setText("Selected: " + id);
        searchField.setText(id);

        view2d.setSelectedId(id);
        view3d.setSelectedId(id);

        refreshNeighbors();

        // Apply "pick anchor from graph" behavior (ONLY here; no duplicates elsewhere)
        applyAnchorPickingToSelection(id);
        labTab.onExternalSelection(id);

    }

    private void refreshNeighbors() {
        if (selectedId == null) {
            neighborsList.getItems().clear();
            return;
        }

        int k = (int) Math.round(kSlider.getValue());
        try {
            neighborsList.getItems().setAll(UseCases.nearestNeighborsDetailed(selectedId, k));
        } catch (Exception ex) {
            setStatus("Neighbors failed: " + ex.getMessage());
        }
    }

    // =======================
    // Projection logic
    // =======================

    private void runProjection() {
        String a = projAField.getText();
        String b = projBField.getText();

        if (a == null || a.isBlank() || b == null || b.isBlank()) {
            setStatus("Please enter both anchors (A and B).");
            return;
        }

        a = a.trim();
        b = b.trim();

        if (!UseCases.containsId(a)) {
            setStatus("Unknown anchor A: " + a);
            return;
        }
        if (!UseCases.containsId(b)) {
            setStatus("Unknown anchor B: " + b);
            return;
        }

        int topN = projTopN.getValue();

        try {
            List<ProjectionScoreView<String>> res = UseCases.customProjectionScale(
                    a, b,
                    topN,
                    projCentered.isSelected(),
                    projIncludeAnchors.isSelected(),
                    projUsePurity.isSelected()
            );

            projItems.setAll(res);
            setStatus("Projection: " + a + " → " + b + " (showing " + res.size() + ")");
        } catch (Exception ex) {
            setStatus("Projection failed: " + ex.getMessage());
        }
    }

    private void stopAnchorPicking() {
        pickMode = AnchorPickMode.NONE;
        pickToggle.selectToggle(null);
        pickABtn.setSelected(false);
        pickBBtn.setSelected(false);
    }

    private void applyAnchorPickingToSelection(String id) {
        if (id == null) return;

        if (pickMode == AnchorPickMode.A) {
            projAField.setText(id);
            setStatus("Projection anchor A set to: " + id);
            stopAnchorPicking();
        } else if (pickMode == AnchorPickMode.B) {
            projBField.setText(id);
            setStatus("Projection anchor B set to: " + id);
            stopAnchorPicking();
        }
    }

    private void generateDatasetAsync(Button generateBtn) {
        generateBtn.setDisable(true);
        setStatus("Running Python embedder...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                UseCases.generateDefaultDataset();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            refreshRepresentations();
            refreshNow();
            setStatus("Generated and loaded dataset into /data");
            generateBtn.setDisable(false);
        });

        task.setOnFailed(e -> {
            Throwable err = task.getException();
            setStatus("Generate failed: " + (err == null ? "unknown error" : err.getMessage()));
            generateBtn.setDisable(false);
        });

        Thread worker = new Thread(task, "embedder-generator");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Opens a FileChooser dialog and loads a selected JSON file
     * via the ExplorerUseCases. This method contains UI logic only.
     */
    private void chooseAndLoadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Embedding JSON");

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON files", "*.json")
        );

        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return; // user canceled – totally fine
        }

        try {
            UseCases.load(file.toPath());

            // Clear transient UI state after loading new data
            UseCases.clearSelection();
            UseCases.clearOverlayPath();
            labTab.resetForNewDataset();

            refreshNow();
            setStatus("Loaded: " + file.getName());

        } catch (Exception ex) {
            setStatus("Failed to load file");
            ex.printStackTrace(); // dev-visible, not user-facing
        }
    }
}
