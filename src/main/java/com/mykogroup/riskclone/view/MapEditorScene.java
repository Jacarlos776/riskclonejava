package com.mykogroup.riskclone.view;

import com.mykogroup.riskclone.engine.AdjacencyEditor;
import com.mykogroup.riskclone.model.Region;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.Map;

/**
 * The full Map Editor UI. Displays the Philippines map and lets the developer
 * inspect and toggle province adjacencies, then save back to province.json.
 */
public class MapEditorScene {

    private static final String DARK_BG   = "#1a1a2e";
    private static final String PANEL_BG  = "#16213e";
    private static final String BTN_GREEN = "#27ae60";
    private static final String BTN_GRAY  = "#4a5568";

    private final BorderPane root;
    private final AdjacencyEditor editor;
    private final EditorMapPane mapPane;
    private final Map<String, String> displayNames;

    // Left panel
    private Label provinceIdLabel;
    private Label displayNameLabel;
    private Label regionLabel;
    private ListView<String> neighborList;
    private Label warningLabel;

    // Right panel
    private Button saveBtn;
    private Label saveStatusLabel;

    // State
    private String selectedId = null;

    public MapEditorScene(Map<String, SVGPath> svgNodes,
                          Map<String, String> displayNames,
                          AdjacencyEditor editor,
                          Runnable onBack) {
        this.editor = editor;
        this.displayNames = displayNames;
        this.mapPane = new EditorMapPane(svgNodes, editor);
        this.root = new BorderPane();

        buildLayout(onBack);

        mapPane.setOnProvinceClicked(this::handleProvinceClicked);
        mapPane.refreshColors();

        // On open, report asymmetric adjacencies
        List<String> issues = editor.getAsymmetricAdjacencies();
        if (issues.isEmpty()) {
            setStatus("No asymmetric adjacencies found.", false);
        } else {
            setStatus("Found " + issues.size() + " asymmetric adjacency issue(s). Check warnings.", true);
        }
    }

    public BorderPane getRoot() { return root; }

    // --- Layout ---

    private void buildLayout(Runnable onBack) {
        root.setStyle("-fx-background-color: " + DARK_BG + ";");

        root.setLeft(buildLeftPanel());
        root.setCenter(buildCenterPanel());
        root.setRight(buildRightPanel(onBack));
    }

    private VBox buildLeftPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(260);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: " + PANEL_BG + ";");

        Label title = boldLabel("Province Inspector", 16);

        provinceIdLabel  = infoLabel("—");
        displayNameLabel = infoLabel("—");
        regionLabel      = infoLabel("—");

        Label neighborsTitle = boldLabel("Neighbors (click to toggle):", 13);

        neighborList = new ListView<>();
        neighborList.setPrefHeight(260);
        neighborList.setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;"
                + " -fx-text-fill: white;");
        neighborList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    String name = displayNames.getOrDefault(item, item);
                    setText(item + "  (" + name + ")");
                    setStyle("-fx-text-fill: #10b981; -fx-background-color: transparent;");
                }
            }
        });

        Label instrLabel = smallLabel("• Click a province on the map\n"
                + "• Click a neighbor to remove it\n"
                + "• Click any other province to add it");

        warningLabel = new Label("");
        warningLabel.setWrapText(true);
        warningLabel.setTextFill(Color.web("#f59e0b"));
        warningLabel.setFont(Font.font("System", 12));

        panel.getChildren().addAll(
                title,
                new Separator(),
                boldLabel("ID:", 12), provinceIdLabel,
                boldLabel("Name:", 12), displayNameLabel,
                boldLabel("Region:", 12), regionLabel,
                new Separator(),
                neighborsTitle,
                neighborList,
                warningLabel,
                new Separator(),
                instrLabel
        );
        return panel;
    }

    private StackPane buildCenterPanel() {
        StackPane center = new StackPane(mapPane);
        center.setStyle("-fx-background-color: #add8e6;");
        return center;
    }

    private VBox buildRightPanel(Runnable onBack) {
        VBox panel = new VBox(12);
        panel.setPrefWidth(230);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: " + PANEL_BG + ";");

        Label regionsTitle = boldLabel("Regions", 16);
        Label regionInstr  = smallLabel("Click to highlight on map");

        ListView<String> regionListView = new ListView<>();
        for (Region r : editor.getRegions()) {
            regionListView.getItems().add(r.getName() + "  (+" + r.getBonusArmies() + ")");
        }
        regionListView.setPrefHeight(280);
        regionListView.setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");
        regionListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else { setText(item); setStyle("-fx-text-fill: #f59e0b; -fx-background-color: transparent;"); }
            }
        });
        regionListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            int idx = newIdx.intValue();
            if (idx >= 0 && idx < editor.getRegions().size()) {
                Region r = editor.getRegions().get(idx);
                mapPane.highlightRegion(r.getProvinces());
            }
        });

        Button fixAsymBtn = styledButton("Fix All Asymmetries", BTN_GRAY);
        fixAsymBtn.setOnAction(e -> {
            editor.fixAllAsymmetries();
            List<String> remaining = editor.getAsymmetricAdjacencies();
            setStatus("Fixed! " + remaining.size() + " issue(s) remaining.", remaining.size() > 0);
            saveBtn.setDisable(!editor.isDirty());
            if (selectedId != null) updateInspectorPanel(selectedId);
        });

        saveBtn = styledButton("Save Changes", BTN_GREEN);
        saveBtn.setDisable(true);
        saveBtn.setOnAction(e -> saveChanges());

        saveStatusLabel = new Label("");
        saveStatusLabel.setWrapText(true);
        saveStatusLabel.setFont(Font.font("System", 12));
        saveStatusLabel.setTextFill(Color.LIGHTGRAY);

        Button backBtn = styledButton("← Back to Menu", BTN_GRAY);
        backBtn.setOnAction(e -> onBack.run());

        VBox.setVgrow(regionListView, Priority.ALWAYS);
        panel.getChildren().addAll(
                regionsTitle, regionInstr, regionListView,
                new Separator(),
                fixAsymBtn, saveBtn, saveStatusLabel,
                new Separator(),
                backBtn
        );
        return panel;
    }

    // --- Interaction ---

    private void handleProvinceClicked(String clickedId) {
        if (selectedId == null) {
            // First click — select this province
            selectedId = clickedId;
            mapPane.selectProvince(selectedId);
            updateInspectorPanel(selectedId);
        } else if (selectedId.equals(clickedId)) {
            // Clicked same province — deselect
            selectedId = null;
            mapPane.clearSelection();
            clearInspectorPanel();
        } else {
            // Second click on a different province — toggle adjacency
            boolean added = editor.toggleAdjacency(selectedId, clickedId);
            String flashColor = added ? "#10b981" : "#ef4444";
            mapPane.selectProvince(selectedId); // refresh neighbors display
            mapPane.flashProvince(clickedId, flashColor, 400);
            updateInspectorPanel(selectedId);
            saveBtn.setDisable(false);
            setStatus(added
                    ? "Added: " + selectedId + " ↔ " + clickedId
                    : "Removed: " + selectedId + " ↔ " + clickedId, false);
        }
    }

    private void updateInspectorPanel(String id) {
        provinceIdLabel.setText(id);
        displayNameLabel.setText(displayNames.getOrDefault(id, "Unknown"));
        regionLabel.setText(editor.findRegion(id));

        neighborList.getItems().setAll(editor.getNeighbors(id));

        // Check for asymmetries involving this province
        List<String> issues = editor.getAsymmetricAdjacencies().stream()
                .filter(s -> s.startsWith(id) || s.contains("→ " + id))
                .toList();
        if (issues.isEmpty()) {
            warningLabel.setText("");
        } else {
            warningLabel.setText("⚠ " + String.join("\n⚠ ", issues));
        }
    }

    private void clearInspectorPanel() {
        provinceIdLabel.setText("—");
        displayNameLabel.setText("—");
        regionLabel.setText("—");
        neighborList.getItems().clear();
        warningLabel.setText("");
    }

    private void saveChanges() {
        try {
            editor.save(getClass(), "com/mykogroup/riskclone/province.json");
            setStatus("Saved successfully!", false);
            saveBtn.setDisable(true);
        } catch (Exception ex) {
            setStatus("Save failed: " + ex.getMessage(), true);
            ex.printStackTrace();
        }
    }

    private void setStatus(String message, boolean isError) {
        saveStatusLabel.setText(message);
        saveStatusLabel.setTextFill(isError ? Color.web("#ef4444") : Color.LIGHTGRAY);
    }

    // --- Style helpers ---

    private static Label boldLabel(String text, int size) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, size));
        l.setTextFill(Color.WHITE);
        return l;
    }

    private static Label infoLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", 13));
        l.setTextFill(Color.LIGHTGRAY);
        l.setWrapText(true);
        return l;
    }

    private static Label smallLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", 11));
        l.setTextFill(Color.web("#888888"));
        l.setWrapText(true);
        return l;
    }

    private static Button styledButton(String text, String color) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-font-weight: bold; -fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-background-radius: 5;");
        return b;
    }
}
