package org.ui.fx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.app.api.ExplorerUseCases;
import org.app.api.dto.LabResultView;
import org.app.api.dto.NeighborView;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class GroupTabController {

    private final ExplorerUseCases vm;
    private final Supplier<String> selectedIdSupplier;
    private final Consumer<String> selectAndCenter;
    private final Consumer<String> statusSink;

    private final ObservableList<String> groupIds = FXCollections.observableArrayList();
    private final ListView<String> groupList = new ListView<>(groupIds);
    private final ListView<NeighborView<String>> resultsList = new ListView<>();

    private final Spinner<Integer> kSpinner = new Spinner<>(1, 50, 10);
    private final CheckBox excludeSelected = new CheckBox("Exclude selected from results");

    private final TextField addFromTextField = new TextField();
    private final Button addFromTextBtn = new Button("Add from text");

    private final Button addSelectedBtn = new Button("Add selected");
    private final Button removeBtn = new Button("Remove");
    private final Button clearBtn = new Button("Clear");
    private final Button computeBtn = new Button("Compute centroid neighbors");

    public GroupTabController(
            ExplorerUseCases vm,
            Supplier<String> selectedIdSupplier,
            Consumer<String> selectAndCenter,
            Consumer<String> statusSink
    ) {
        this.vm = Objects.requireNonNull(vm, "vm must not be null");
        this.selectedIdSupplier = Objects.requireNonNull(selectedIdSupplier, "selectedIdSupplier must not be null");
        this.selectAndCenter = Objects.requireNonNull(selectAndCenter, "selectAndCenter must not be null");
        this.statusSink = Objects.requireNonNull(statusSink, "statusSink must not be null");
    }

    public Node build() {
        Label title = new Label("Subspace Grouping");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        groupList.setPrefHeight(180);
        groupList.setPlaceholder(new Label("No IDs in group."));

        resultsList.setPrefHeight(280);
        resultsList.setPlaceholder(new Label("No results yet."));

        addFromTextField.setPromptText("Type an ID");

        kSpinner.setEditable(true);
        excludeSelected.setSelected(true);

        addSelectedBtn.setMaxWidth(Double.MAX_VALUE);
        removeBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        addFromTextBtn.setMaxWidth(Double.MAX_VALUE);
        computeBtn.setMaxWidth(Double.MAX_VALUE);

        addSelectedBtn.setOnAction(e -> addSelected());
        removeBtn.setOnAction(e -> removeSelected());
        clearBtn.setOnAction(e -> clear());
        addFromTextBtn.setOnAction(e -> addFromText());
        addFromTextField.setOnAction(e -> addFromText());
        computeBtn.setOnAction(e -> compute());

        groupList.setOnMouseClicked(e -> {
            String id = groupList.getSelectionModel().getSelectedItem();
            if (id != null && e.getClickCount() == 2) {
                selectAndCenter.accept(id);
            }
        });

        resultsList.setCellFactory(list -> new ListCell<>() {
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

        resultsList.setOnMouseClicked(e -> {
            NeighborView<String> n = resultsList.getSelectionModel().getSelectedItem();
            if (n != null) {
                selectAndCenter.accept(n.id());
            }
        });

        HBox groupButtons = new HBox(8, addSelectedBtn, removeBtn, clearBtn);
        groupButtons.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(addSelectedBtn, Priority.ALWAYS);
        HBox.setHgrow(removeBtn, Priority.ALWAYS);
        HBox.setHgrow(clearBtn, Priority.ALWAYS);

        HBox addTextRow = new HBox(8, addFromTextField, addFromTextBtn);
        addTextRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(addFromTextField, Priority.ALWAYS);
        HBox.setHgrow(addFromTextBtn, Priority.NEVER);

        VBox box = new VBox(10,
                title,
                new Label("Group IDs"),
                groupList,
                groupButtons,
                addTextRow,
                new Separator(),
                labeledRow("K", kSpinner),
                excludeSelected,
                computeBtn,
                new Separator(),
                new Label("Centroid neighbors"),
                resultsList
        );
        box.setPadding(new Insets(10));
        return box;
    }

    private void addSelected() {
        String selectedId = selectedIdSupplier.get();
        if (selectedId == null || selectedId.isBlank()) {
            statusSink.accept("Group: no selected point to add.");
            return;
        }
        addIdIfValid(selectedId.trim());
    }

    private void addFromText() {
        String typed = addFromTextField.getText();
        if (typed == null || typed.isBlank()) {
            statusSink.accept("Group: type an ID first.");
            return;
        }
        addIdIfValid(typed.trim());
        addFromTextField.clear();
    }

    private void addIdIfValid(String id) {
        if (!vm.containsId(id)) {
            statusSink.accept("Group: unknown ID: " + id);
            return;
        }
        if (groupIds.contains(id)) {
            statusSink.accept("Group: already added: " + id);
            return;
        }
        groupIds.add(id);
        statusSink.accept("Group: added " + id + ".");
    }

    private void removeSelected() {
        String id = groupList.getSelectionModel().getSelectedItem();
        if (id == null) {
            statusSink.accept("Group: choose an item to remove.");
            return;
        }
        groupIds.remove(id);
        statusSink.accept("Group: removed " + id + ".");
    }

    private void clear() {
        groupIds.clear();
        resultsList.getItems().clear();
        statusSink.accept("Group: selection cleared.");
    }

    private void compute() {
        if (groupIds.isEmpty()) {
            statusSink.accept("Group: add at least one ID.");
            return;
        }

        int k = kSpinner.getValue();
        Set<String> ids = new LinkedHashSet<>(groupIds);

        try {
            LabResultView<String> result = vm.subspaceGrouping(ids, k, excludeSelected.isSelected());
            List<NeighborView<String>> neighbors = result.neighbors();
            resultsList.getItems().setAll(neighbors);
            statusSink.accept("Group: computed " + neighbors.size() + " centroid neighbors.");
        } catch (Exception ex) {
            statusSink.accept("Group failed: " + ex.getMessage());
        }
    }

    private static Node labeledRow(String label, Control control) {
        Label l = new Label(label);
        l.setMinWidth(36);
        HBox row = new HBox(8, l, control);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }
}
