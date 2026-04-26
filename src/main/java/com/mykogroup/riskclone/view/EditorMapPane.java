package com.mykogroup.riskclone.view;

import com.mykogroup.riskclone.engine.AdjacencyEditor;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
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

    private static final String[][] SEA_ROUTES = {
        {"PH-BTN", "PH-CAG"},
        {"PH-PLW", "PH-MDC"},
        {"PH-PLW", "PH-ANT"},
        {"PH-PLW", "PH-TAW"},
        {"PH-TAW", "PH-SLU"},
        {"PH-SLU", "PH-BAS"},
        {"PH-BAS", "PH-ZSI"},
        {"PH-ROM", "PH-AKL"},
        {"PH-ROM", "PH-MAD"},
        {"PH-MDR", "PH-ROM"},
        {"PH-MAD", "PH-MDR"},
        {"PH-MAD", "PH-QUE"},
        {"PH-CAT", "PH-ALB"},
        {"PH-CAT", "PH-CAS"},
        {"PH-MAS", "PH-SOR"},
        {"PH-MAS", "PH-CAP"},
        {"PH-MAS", "PH-NSA"},
        {"PH-SOR", "PH-NSA"},
        {"PH-BIL", "PH-LEY"},
        {"PH-CEB", "PH-LEY"},
        {"PH-CEB", "PH-NER"},
        {"PH-CEB", "PH-BOH"},
        {"PH-NER", "PH-ZAN"},
        {"PH-NEC", "PH-GUI"},
        {"PH-ILI", "PH-GUI"},
        {"PH-SLE", "PH-DIN"},
        {"PH-CAM", "PH-BOH"},
        {"PH-CAM", "PH-MSR"},
    };

    private final Map<String, SVGPath> provinceNodes;
    private final AdjacencyEditor editor;
    private final Group seaRouteLayer = new Group();
    private boolean seaRoutesDrawn = false;

    private String selectedId = null;
    private final Set<String> regionHighlightIds = new HashSet<>();

    private Consumer<String> onProvinceClicked = id -> {};

    // Pan state
    private double lastMouseX, lastMouseY;

    public EditorMapPane(Map<String, SVGPath> provinceNodes, AdjacencyEditor editor) {
        this.provinceNodes = provinceNodes;
        this.editor = editor;

        this.getChildren().add(seaRouteLayer);
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
    private void drawSeaRoutes() {
        for (String[] route : SEA_ROUTES) {
            SVGPath p1 = provinceNodes.get(route[0]);
            SVGPath p2 = provinceNodes.get(route[1]);
            if (p1 == null || p2 == null) continue;

            Bounds b1 = p1.getBoundsInParent();
            Bounds b2 = p2.getBoundsInParent();

            Line line = new Line(b1.getCenterX(), b1.getCenterY(), b2.getCenterX(), b2.getCenterY());
            line.setStrokeWidth(1.5);
            line.setStroke(Color.web("#0284c7"));
            line.getStrokeDashArray().addAll(6d, 4d);
            line.setMouseTransparent(true);
            line.setOpacity(0.85);

            seaRouteLayer.getChildren().add(line);
        }
    }

    public void refreshColors() {
        if (!seaRoutesDrawn) {
            drawSeaRoutes();
            seaRoutesDrawn = true;
        }
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
