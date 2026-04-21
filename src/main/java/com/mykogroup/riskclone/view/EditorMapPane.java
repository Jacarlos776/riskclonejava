package com.mykogroup.riskclone.view;

import com.mykogroup.riskclone.engine.AdjacencyEditor;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

import java.util.*;
import java.util.function.Consumer;

/**
 * A lightweight map pane for the Map Editor — pan/zoom only, no GameState dependency.
 * Color priority: selected (blue) > neighbor (green) > region highlight (orange) > neutral (gray).
 */
public class EditorMapPane extends Pane {

    private static final String COLOR_SELECTED  = "#3b82f6";
    private static final String COLOR_NEIGHBOR  = "#10b981";
    private static final String COLOR_REGION    = "#f59e0b";
    private static final String COLOR_NEUTRAL   = "#6b7280";

    private final Map<String, SVGPath> provinceNodes;
    private final AdjacencyEditor editor;

    private String selectedId = null;
    private final Set<String> regionHighlightIds = new HashSet<>();

    private Consumer<String> onProvinceClicked = id -> {};

    // Pan state
    private double lastMouseX, lastMouseY;

    public EditorMapPane(Map<String, SVGPath> provinceNodes, AdjacencyEditor editor) {
        this.provinceNodes = provinceNodes;
        this.editor = editor;

        // Add all province nodes to this pane
        this.getChildren().addAll(provinceNodes.values());

        // Install click handlers on every province
        provinceNodes.forEach((id, node) ->
                node.setOnMouseClicked(e -> onProvinceClicked.accept(id)));

        // Pan / zoom
        this.setOnMousePressed(this::handleMousePressed);
        this.setOnMouseDragged(this::handleMouseDragged);
        this.setOnScroll(this::handleScroll);
    }

    // --- Public API for MapEditorScene ---

    public void setOnProvinceClicked(Consumer<String> callback) {
        this.onProvinceClicked = callback;
    }

    /** Select a province: highlight it blue and its neighbors green. */
    public void selectProvince(String id) {
        selectedId = id;
        refreshColors();
    }

    /** Clear province selection (but keep region highlight). */
    public void clearSelection() {
        selectedId = null;
        refreshColors();
    }

    /** Highlight a set of provinces orange (region view). Replaces previous region highlight. */
    public void highlightRegion(List<String> ids) {
        regionHighlightIds.clear();
        regionHighlightIds.addAll(ids);
        refreshColors();
    }

    public void clearRegionHighlight() {
        regionHighlightIds.clear();
        refreshColors();
    }

    /**
     * Repaints every province according to current state.
     * Call this after any adjacency toggle.
     */
    public void refreshColors() {
        Set<String> neighbors = selectedId != null ? editor.getNeighbors(selectedId) : Collections.emptySet();

        for (Map.Entry<String, SVGPath> entry : provinceNodes.entrySet()) {
            String id = entry.getKey();
            SVGPath node = entry.getValue();

            String color;
            if (id.equals(selectedId)) {
                color = COLOR_SELECTED;
            } else if (neighbors.contains(id)) {
                color = COLOR_NEIGHBOR;
            } else if (regionHighlightIds.contains(id)) {
                color = COLOR_REGION;
            } else {
                color = COLOR_NEUTRAL;
            }

            node.getProperties().put("baseColor", color);
            node.setStyle(SvgMapLoader.generateStyle(color, false, false));
        }
    }

    /** Flash a province a given color briefly, then restore the normal color. */
    public void flashProvince(String id, String flashColor, int durationMs) {
        SVGPath node = provinceNodes.get(id);
        if (node == null) return;

        node.setStyle(SvgMapLoader.generateStyle(flashColor, false, false));

        javafx.animation.PauseTransition pause =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(durationMs));
        pause.setOnFinished(e -> refreshColors());
        pause.play();
    }

    // --- Pan / Zoom (copied from InteractiveMapPane) ---

    private void handleMousePressed(MouseEvent event) {
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();
        event.consume();
    }

    private void handleMouseDragged(MouseEvent event) {
        double deltaX = event.getSceneX() - lastMouseX;
        double deltaY = event.getSceneY() - lastMouseY;
        this.setTranslateX(this.getTranslateX() + deltaX);
        this.setTranslateY(this.getTranslateY() + deltaY);
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();
        event.consume();
    }

    private void handleScroll(ScrollEvent event) {
        double zoomFactor = (event.getDeltaY() < 0) ? 0.9 : 1.1;
        double oldScale = this.getScaleX();
        double newScale = Math.max(0.4, Math.min(10.0, oldScale * zoomFactor));
        double actualFactor = newScale / oldScale;

        this.setScaleX(newScale);
        this.setScaleY(newScale);

        double pivotX = event.getX() - (this.getBoundsInLocal().getWidth() / 2);
        double pivotY = event.getY() - (this.getBoundsInLocal().getHeight() / 2);
        this.setTranslateX(this.getTranslateX() - pivotX * (actualFactor - 1));
        this.setTranslateY(this.getTranslateY() - pivotY * (actualFactor - 1));

        event.consume();
    }
}
