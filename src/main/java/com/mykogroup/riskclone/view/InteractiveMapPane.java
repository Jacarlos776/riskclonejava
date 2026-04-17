package com.mykogroup.riskclone.view;

import com.mykogroup.riskclone.engine.AdjacencyService;
import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Move;
import com.mykogroup.riskclone.model.Province;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.SVGPath;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InteractiveMapPane extends Pane {

    // Variables to track mouse position for panning
    private double lastMouseX;
    private double lastMouseY;

    // --- GAME UI STATE ---
    private SVGPath sourceProvince = null;

    // --- GAME LOGIC STATE ---
    private final AdjacencyService adjacencyService;
    private GameState gameState;


    // --- Layers --- // Reason why we had to make two layers is because of the .toFront of provinces in SvgMapLoader. this means when you hover over the province it hides the arrows, so we make a second layer where the arrow is always on top.
    private final Group provinceLayer = new Group();
    private final Group arrowLayer = new Group(); // Note: Once we implement the planning and resolution phase, we should clear the arrow layer to remove all arrows.
    private final Group uiLayer = new Group();
    // ADD MORE LAYERS AS NEEDED. for example: uiLayer for the UI, etc.

    // Track arrows so we can delete them
    private final Map<String, Line> activeArrows = new HashMap<>();

    public InteractiveMapPane(AdjacencyService adjacencyService, GameState gameState) {
        this.adjacencyService = adjacencyService;
        this.gameState = gameState;
        // Attach event listeners upon instantiation
        this.setOnMousePressed(this::handleMousePressed);
        this.setOnMouseDragged(this::handleMouseDragged);
        this.setOnScroll(this::handleScroll);

        // Add layers to the Pane.
        // Order matters: arrowLayer is added second, so it always renders on top.
        this.getChildren().addAll(provinceLayer, arrowLayer, uiLayer);
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
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
                // 1. Calculate how many troops the player can send
                int maxAvailable = gameState.getAvailableTroopsForMove(sourceId, targetId);

                // 2. Check if they already have a move queued here
                Optional<Move> existingMove = gameState.getExistingMove(sourceId, targetId);
                int currentArmies = existingMove.map(Move::armies).orElse(maxAvailable); // Default to max if new

                // 3. Show the UI if they have troops to send (or an existing move to edit)
                if (maxAvailable > 0 || existingMove.isPresent()) {
                    showArmyPopup(sourceProvince, clickedNode, currentArmies, maxAvailable);
                } else {
                    System.out.println("Not enough troops to move from " + sourceId);
                }

                // Clear selection visually
                SvgMapLoader.setNodeSelected(sourceProvince, false);
                sourceProvince = null;

            } else {
                // Illegal move. Just select the province and unselect the previous one
                System.out.println("Invalid move: " + targetId + " does not border " + sourceId);

                // Unselect the old source
                SvgMapLoader.setNodeSelected(sourceProvince, false);

                // Make clicked province the new source province and update UI
                sourceProvince = clickedNode;
                SvgMapLoader.setNodeSelected(sourceProvince, true);
            }
        }
    }

    private void drawArrow(SVGPath source, SVGPath target, String pathKey) {
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

        // Add to map AND layer
        activeArrows.put(pathKey, arrow);
        arrowLayer.getChildren().add(arrow);
        arrowLayer.toFront();
        uiLayer.toFront(); // Ensure UI always stays above the new arrow
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

    // --- Sync View to State ---
    public void renderState(GameState state) {
        // Loop through all SVG paths currently in the province layer
        for (var node : provinceLayer.getChildren()) {
            if (node instanceof SVGPath svgPath) {
                String provinceId = svgPath.getId();

                // Find the matching province in the master state
                Optional<Province> provinceData = state.getProvince(provinceId);

                if (provinceData.isPresent()) {
                    String ownerId = provinceData.get().getOwnerId();
                    String newColor = ColorManager.getColorForPlayer(ownerId);

                    // Update the node's memory of its base color
                    svgPath.getProperties().put("baseColor", newColor);

                    // Reapply the style (maintaining current selection status)
                    boolean isSelected = (boolean) svgPath.getProperties().get("selected");
                    svgPath.setStyle(SvgMapLoader.generateStyle(newColor, isSelected, false));
                }
            }
        }
    }

    private void showArmyPopup(SVGPath source, SVGPath target, int currentArmies, int maxArmies) {
        // Clear any existing popups first
        uiLayer.getChildren().clear();

        // Build the UI Container
        VBox popup = new VBox(5);
        popup.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8); -fx-padding: 10; -fx-background-radius: 5;");
        popup.setAlignment(Pos.CENTER);

        // Labels
        Label titleLabel = new Label("Send Troops");
        titleLabel.setTextFill(Color.WHITE);

        Label countLabel = new Label(currentArmies + " / " + maxArmies);
        countLabel.setTextFill(Color.GOLD);

        // The Slider
        Slider slider = new Slider(0, maxArmies, currentArmies);
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setPrefWidth(120);

        // Update label when slider moves
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            countLabel.setText(newVal.intValue() + " / " + maxArmies);
        });

        // Confirm Button
        Button confirmBtn = new Button("Confirm");
        confirmBtn.setOnAction(e -> {
            int finalArmies = (int) slider.getValue();
            String pathKey = source.getId() + "-" + target.getId();

            if (finalArmies == 0) {
                // Cancel Move
                gameState.setMove(new Move("localPlayer1", source.getId(), target.getId(), 0));
                Line arrow = activeArrows.remove(pathKey);
                if (arrow != null) arrowLayer.getChildren().remove(arrow);
                System.out.println("Move cancelled.");
            } else {
                // Save Move
                gameState.setMove(new Move("localPlayer1", source.getId(), target.getId(), finalArmies));
                if (!activeArrows.containsKey(pathKey)) {
                    drawArrow(source, target, pathKey);
                }
                System.out.println("Move updated: " + finalArmies + " troops.");
            }

            // Close popup
            uiLayer.getChildren().clear();
        });

        popup.getChildren().addAll(titleLabel, slider, countLabel, confirmBtn);

        // Position the popup based on destination province's location
        Bounds targetBounds = target.getBoundsInParent();
        popup.setLayoutX(targetBounds.getCenterX() - 150);
        popup.setLayoutY(targetBounds.getCenterY());

        uiLayer.getChildren().add(popup);
    }
}
