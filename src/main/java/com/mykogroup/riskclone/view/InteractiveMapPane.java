package com.mykogroup.riskclone.view;

import com.mykogroup.riskclone.engine.AdjacencyService;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.SVGPath;

import java.util.Collection;

public class InteractiveMapPane extends Pane {

    // Variables to track mouse position for panning
    private double lastMouseX;
    private double lastMouseY;

    // --- GAME UI STATE ---
    private SVGPath sourceProvince = null;

    // --- GAME LOGIC STATE ---
    private final AdjacencyService adjacencyService;


    // --- Layers --- // Reason why we had to make two layers is because of the .toFront of provinces in SvgMapLoader. this means when you hover over the province it hides the arrows, so we make a second layer where the arrow is always on top.
    private final Group provinceLayer = new Group();
    private final Group arrowLayer = new Group(); // Note: Once we implement the planning and resolution phase, we should clear the arrow layer to remove all arrows.
    // ADD MORE LAYERS AS NEEDED. for example: uiLayer for the UI, etc.

    public InteractiveMapPane(AdjacencyService adjacencyService) {
        this.adjacencyService = adjacencyService;
        // Attach event listeners upon instantiation
        this.setOnMousePressed(this::handleMousePressed);
        this.setOnMouseDragged(this::handleMouseDragged);
        this.setOnScroll(this::handleScroll);

        // Add layers to the Pane.
        // Order matters: arrowLayer is added second, so it always renders on top.
        this.getChildren().addAll(provinceLayer, arrowLayer);
    }

    // --- Helper to add provinces to the correct layer ---
    public void addProvinces(Collection<SVGPath> provinces) {
        provinceLayer.getChildren().addAll(provinces);
    }

    // --- Handle the Source -> Destination flow ---
    public void handleProvinceClick(SVGPath clickedNode) {
        if (sourceProvince == null) {
            sourceProvince = clickedNode;
            SvgMapLoader.setNodeSelected(sourceProvince, true);

            System.out.println("Selected Province ID: " + sourceProvince.getId());
        } else if (sourceProvince == clickedNode) {
            SvgMapLoader.setNodeSelected(sourceProvince, false);
            sourceProvince = null;

            System.out.println("Selection cancelled.");
        } else {
            String sourceId = sourceProvince.getId();
            String targetId = clickedNode.getId();

            if (adjacencyService.areAdjacent(sourceId, targetId)) {
                // It's a valid neighbor! Draw the arrow.
                drawArrow(sourceProvince, clickedNode);

                // Clear sourceProvince UI
                SvgMapLoader.setNodeSelected(sourceProvince, false);
                sourceProvince = null;

                // TODO: Create the Move object and add it to GameState
                // Move move = new Move("localPlayer", sourceId, targetId, 5);
                // gameState.addMove(move);

                System.out.println("Valid move queued: " + sourceId + " -> " + targetId);

            } else {
                // Illegal move. Just select the province and unselect the previous one
                System.out.println("Invalid move: " + targetId + " does not border " + sourceId);

                // Unselect the old source
                SvgMapLoader.setNodeSelected(sourceProvince, false);

                // Make clicked province the new source province and update UI
                sourceProvince = clickedNode;
                SvgMapLoader.setNodeSelected(sourceProvince, true);

                System.out.println("Selected Province ID: " + sourceProvince.getId());
            }
        }
    }

    private void drawArrow(SVGPath source, SVGPath target) {
        Bounds sourceBounds = source.getBoundsInParent();
        Bounds targetBounds = target.getBoundsInParent();

        Line arrow = new Line(
                sourceBounds.getCenterX(), sourceBounds.getCenterY(),
                targetBounds.getCenterX(), targetBounds.getCenterY()
        );

        arrow.setStrokeWidth(4);
        arrow.setStroke(Color.DARKRED);
        arrow.getStrokeDashArray().addAll(10d, 5d);
        arrow.setMouseTransparent(true);

        // Add the arrow to the top layer (arrow)
        arrowLayer.getChildren().add(arrow);
    }

    private void handleMousePressed(MouseEvent event) {
        // Record the starting coordinates when the user clicks down
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();
        event.consume();
    }

    private void handleMouseDragged(MouseEvent event) {
        // Calculate how far the mouse has moved since the last frame
        double deltaX = event.getSceneX() - lastMouseX;
        double deltaY = event.getSceneY() - lastMouseY;

        // Apply that movement to the Pane's translation (Panning)
        this.setTranslateX(this.getTranslateX() + deltaX);
        this.setTranslateY(this.getTranslateY() + deltaY);

        // Update the tracked coordinates for the next drag event
        lastMouseX = event.getSceneX();
        lastMouseY = event.getSceneY();

        event.consume();
    }

    private void handleScroll(ScrollEvent event) {
        double zoomFactor = (event.getDeltaY() < 0) ? 0.9 : 1.1;

        double oldScale = this.getScaleX();
        double newScale = oldScale * zoomFactor;

        // Clamp limits
        if (newScale < 0.4) newScale = 0.4;
        if (newScale > 10.0) newScale = 10.0;

        // Recalculate actual zoom factor based on clamped scale
        double actualFactor = newScale / oldScale;

        // 1. Get mouse position relative to the pane
        double mouseX = event.getX();
        double mouseY = event.getY();

        // 2. Apply the Scale
        this.setScaleX(newScale);
        this.setScaleY(newScale);

        // 3. Adjust Translation to keep the mouse point fixed
        // The logic: (pivot - center) * (1 - factor)
        // JavaFX pivot is the center of the node's layout bounds by default.
        double pivotX = mouseX - (this.getBoundsInLocal().getWidth() / 2);
        double pivotY = mouseY - (this.getBoundsInLocal().getHeight() / 2);

        this.setTranslateX(this.getTranslateX() - pivotX * (actualFactor - 1));
        this.setTranslateY(this.getTranslateY() - pivotY * (actualFactor - 1));

        event.consume();
    }
}
