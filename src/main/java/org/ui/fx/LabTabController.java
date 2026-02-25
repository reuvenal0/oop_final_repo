package org.ui.fx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.app.api.ExplorerUseCases;
import org.app.api.dto.LabResultView;
import org.app.api.dto.NeighborView;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class LabTabController {

    private final ExplorerUseCases vm;
    private final Supplier<String> selectedIdSupplier;
    private final Consumer<String> selectAndCenter;
    private final Consumer<String> setStatus;

    private final ObservableList<org.lab.Term<String>> labTerms = FXCollections.observableArrayList();
    private final ListView<org.lab.Term<String>> labTermsList = new ListView<>(labTerms);

    private final ToggleGroup labPickToggle = new ToggleGroup();
    private final ToggleButton labPickPlusBtn = new ToggleButton("Pick +");
    private final ToggleButton labPickMinusBtn = new ToggleButton("Pick -");

    private enum LabPickMode { NONE, PLUS, MINUS }
    private LabPickMode labPickMode = LabPickMode.NONE;

    private final Button labAddPlusBtn = new Button("+ Add selected");
    private final Button labAddMinusBtn = new Button("- Add selected");
    private final Button labClearBtn = new Button("Clear");
    private final Button labSolveBtn = new Button("Solve");

    private final Spinner<Integer> labK = new Spinner<>(1, 50, 10, 1);
    private final Label labBestMatchLabel = new Label("Best match: (none)");
    private final ListView<NeighborView<String>> labNeighborsList = new ListView<>();

    LabTabController(
            ExplorerUseCases vm,
            Supplier<String> selectedIdSupplier,
            Consumer<String> selectAndCenter,
            Consumer<String> setStatus
    ) {
        this.vm = vm;
        this.selectedIdSupplier = selectedIdSupplier;
        this.selectAndCenter = selectAndCenter;
        this.setStatus = setStatus;
    }

    Node build() {
        Label title = new Label("Vector Arithmetic Lab");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        labPickPlusBtn.setToggleGroup(labPickToggle);
        labPickMinusBtn.setToggleGroup(labPickToggle);
        labPickPlusBtn.setMaxWidth(Double.MAX_VALUE);
        labPickMinusBtn.setMaxWidth(Double.MAX_VALUE);

        labPickToggle.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == labPickPlusBtn) {
                labPickMode = LabPickMode.PLUS;
                setStatus.accept("Lab: click a point to add it as +term");
            } else if (now == labPickMinusBtn) {
                labPickMode = LabPickMode.MINUS;
                setStatus.accept("Lab: click a point to add it as -term");
            } else {
                labPickMode = LabPickMode.NONE;
            }
        });

        HBox pickRow = new HBox(10, labPickPlusBtn, labPickMinusBtn);
        pickRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(labPickPlusBtn, Priority.ALWAYS);
        HBox.setHgrow(labPickMinusBtn, Priority.ALWAYS);

        labAddPlusBtn.setMaxWidth(Double.MAX_VALUE);
        labAddMinusBtn.setMaxWidth(Double.MAX_VALUE);

        labAddPlusBtn.setOnAction(e -> {
            String selectedId = selectedIdSupplier.get();
            if (selectedId == null) {
                setStatus.accept("Lab: no selected point.");
                return;
            }
            addLabTerm(selectedId, +1);
        });

        labAddMinusBtn.setOnAction(e -> {
            String selectedId = selectedIdSupplier.get();
            if (selectedId == null) {
                setStatus.accept("Lab: no selected point.");
                return;
            }
            addLabTerm(selectedId, -1);
        });

        HBox addSelectedRow = new HBox(10, labAddPlusBtn, labAddMinusBtn);
        addSelectedRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(labAddPlusBtn, Priority.ALWAYS);
        HBox.setHgrow(labAddMinusBtn, Priority.ALWAYS);

        labTermsList.setPrefHeight(240);
        labTermsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(org.lab.Term<String> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                String sign = item.sign() == 1 ? "+" : "-";
                Label left = new Label(sign);
                left.setStyle(item.sign() == 1
                        ? "-fx-font-weight: 800; -fx-text-fill: #1e66ff;"
                        : "-fx-font-weight: 800; -fx-text-fill: #d14b4b;");

                Label id = new Label(item.id());
                id.setStyle("-fx-font-weight: 700;");

                Button remove = new Button("✕");
                remove.setFocusTraversable(false);
                remove.setOnAction(e -> {
                    labTerms.remove(item);
                    refreshOverlayPathFromTerms();
                });

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox row = new HBox(10, left, id, spacer, remove);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 8, 6, 8));
                setGraphic(row);
            }
        });

        labTermsList.setOnMouseClicked(e -> {
            org.lab.Term<String> t = labTermsList.getSelectionModel().getSelectedItem();
            if (t != null) {
                selectAndCenter.accept(t.id());
            }
        });

        labK.setEditable(true);
        labSolveBtn.setMaxWidth(Double.MAX_VALUE);
        labClearBtn.setMaxWidth(Double.MAX_VALUE);

        labClearBtn.setOnAction(e -> resetForNewDataset());
        labSolveBtn.setOnAction(e -> runLab());

        labNeighborsList.setCellFactory(list -> new ListCell<>() {
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

        labNeighborsList.setOnMouseClicked(e -> {
            NeighborView<String> n = labNeighborsList.getSelectionModel().getSelectedItem();
            if (n != null) {
                selectAndCenter.accept(n.id());
            }
        });

        VBox box = new VBox(10,
                title,
                new Label("Build expression by picking points"),
                pickRow,
                addSelectedRow,
                new Separator(),
                new Label("Terms"),
                labTermsList,
                new Separator(),
                labeledRow("K", labK),
                labBestMatchLabel,
                labSolveBtn,
                labClearBtn,
                new Separator(),
                new Label("Nearest neighbors"),
                labNeighborsList
        );

        box.setPadding(new Insets(10));
        return box;
    }

    void stopPicking() {
        labPickMode = LabPickMode.NONE;
        labPickToggle.selectToggle(null);
        labPickPlusBtn.setSelected(false);
        labPickMinusBtn.setSelected(false);
    }

    void onExternalSelection(String id) {
        if (id == null) return;
        if (labPickMode == LabPickMode.PLUS) {
            addLabTerm(id, +1);
            stopPicking();
        } else if (labPickMode == LabPickMode.MINUS) {
            addLabTerm(id, -1);
            stopPicking();
        }
    }

    void refreshOverlayPathFromTerms() {
        if (labTerms.isEmpty()) {
            vm.setOverlayPath(List.of());
            return;
        }

        try {
            List<String> ids = labTerms.stream().map(org.lab.Term::id).toList();
            vm.setOverlayPath(LabOverlayPathBuilder.routeForTerms(vm.getPointCloud(), ids));
        } catch (Exception ex) {
            vm.setOverlayPath(List.of());
        }
    }

    void resetForNewDataset() {
        labTerms.clear();
        labNeighborsList.getItems().clear();
        labBestMatchLabel.setText("Best match: (none)");
        vm.setOverlayPath(List.of());
        setStatus.accept("Lab cleared.");
    }

    private void addLabTerm(String id, int sign) {
        if (!vm.containsId(id)) {
            setStatus.accept("Lab: unknown id: " + id);
            return;
        }
        labTerms.add(new org.lab.Term<>(id, sign));
        refreshOverlayPathFromTerms();
        setStatus.accept("Lab: added " + (sign == 1 ? "+" : "-") + " " + id);
    }

    private void runLab() {
        if (labTerms.isEmpty()) {
            setStatus.accept("Lab: add at least one term.");
            return;
        }

        org.lab.VectorExpression<String> expr = new org.lab.VectorExpression<>(List.copyOf(labTerms));
        int k = labK.getValue();

        try {
            LabResultView<String> res = vm.solveVectorArithmetic(expr, k);

            List<NeighborView<String>> nn = res.neighbors();
            labNeighborsList.getItems().setAll(nn);

            String best = nn.isEmpty() ? "(none)" : nn.get(0).id();
            labBestMatchLabel.setText("Best match: " + best);

            List<String> termIds = labTerms.stream().map(org.lab.Term::id).toList();
            vm.setOverlayPath(LabOverlayPathBuilder.routeForSolution(vm.getPointCloud(), termIds, nn));

            if (!"(none)".equals(best) && vm.containsId(best)) {
                selectAndCenter.accept(best);
            }

            setStatus.accept("Lab solved. Best match: " + best);
        } catch (Exception ex) {
            setStatus.accept("Lab failed: " + ex.getMessage());
        }
    }

    private static Node labeledRow(String label, Node field) {
        Label l = new Label(label);
        l.setMinWidth(36);
        HBox row = new HBox(8, l, field);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }
}
