package com.mykogroup.riskclone.view;

import com.mykogroup.riskclone.engine.AdjacencyService;
import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Move;
import com.mykogroup.riskclone.model.Province;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InteractiveMapPane extends Pane {

    // Variables to track mouse position for panning
    private double lastMouseX;
    private double lastMouseY;

    // --- GAME UI STATE ---
    private SVGPath sourceProvince = null;
    private Runnable onDraftAction; // Callback to update UI

    // --- GAME LOGIC STATE ---
    private final AdjacencyService adjacencyService;
    private GameState gameState;
    private String currentLocalPlayerId = "player1"; // Default
    private boolean interactionLocked = false;

    private static final String[][] SEA_ROUTES = {
        {"PH-BTN", "PH-CAG"}, {"PH-PLW", "PH-MDC"}, {"PH-PLW", "PH-ANT"}, {"PH-PLW", "PH-TAW"}, {"PH-TAW", "PH-SLU"},
        {"PH-SLU", "PH-BAS"}, {"PH-BAS", "PH-ZSI"}, {"PH-ROM", "PH-AKL"}, {"PH-ROM", "PH-MAD"}, {"PH-MDR", "PH-ROM"},
        {"PH-MAD", "PH-MDR"}, {"PH-MAD", "PH-QUE"}, {"PH-CAT", "PH-ALB"}, {"PH-CAT", "PH-CAS"}, {"PH-MAS", "PH-SOR"},
        {"PH-MAS", "PH-CAP"}, {"PH-MAS", "PH-NSA"}, {"PH-SOR", "PH-NSA"}, {"PH-BIL", "PH-LEY"}, {"PH-CEB", "PH-LEY"},
        {"PH-CEB", "PH-NER"}, {"PH-CEB", "PH-BOH"}, {"PH-NER", "PH-ZAN"}, {"PH-NEC", "PH-GUI"}, {"PH-ILI", "PH-GUI"},
        {"PH-SLE", "PH-DIN"}, {"PH-CAM", "PH-BOH"}, {"PH-CAM", "PH-MSR"},
    };

    // --- NETWORK MODE ---
    // When non-null, clicks send intents instead of mutating state directly.
    private java.util.function.Consumer<String> networkClaimHandler = null;
    private java.util.function.Consumer<String> networkDraftHandler = null;
    private java.util.function.Consumer<com.mykogroup.riskclone.model.Move> networkMoveHandler = null;

    // --- Layers ---
    private final Group seaRouteLayer = new Group();
    private final Group provinceLayer = new Group();
    private final Group labelLayer = new Group();
    private final Group arrowLayer = new Group();
    private final Group uiLayer = new Group();

    // Track arrows so we can delete them
    private final Map<String, ArrowNode> activeArrows = new HashMap<>();

    private static final double ARROW_END_PAD = 10;
    private static final double ARROW_HEAD_LEN = 12;
    private static final double ARROW_HEAD_W   = 9;
    private static final double ARROW_SHAFT_WIDTH = 3.0;
    private static final double ARROW_HEAD_FORWARD = 5.0; // push head tip past shaft endpoint
    private static final double ARROW_PULSE_BOOST = 3.5;  // extra strokeWidth at pulse peak

    // Composite arrow: filled shaft + arrowhead, grouped so animations apply uniformly.
    private static class ArrowNode extends Group {
        final Line shaft;
        final Polygon head;
        ArrowNode(Line shaft, Polygon head) {
            super(shaft, head);
            this.shaft = shaft;
            this.head = head;
            setMouseTransparent(true);
        }
    }
    private final Map<String, Text> armyLabels = new HashMap<>(); // Track text
    private final Map<String, SVGPath> provinceNodeMap = new HashMap<>();
    private boolean seaRoutesDrawn = false;

    public InteractiveMapPane(AdjacencyService adjacencyService, GameState gameState) {
        this.adjacencyService = adjacencyService;
        this.gameState = gameState;
        // Attach event listeners upon instantiation
        this.setOnMousePressed(this::handleMousePressed);
        this.setOnMouseDragged(this::handleMouseDragged);
        this.setOnScroll(this::handleScroll);

        this.getChildren().addAll(seaRouteLayer, provinceLayer, labelLayer, arrowLayer, uiLayer);
    }

    public void setOnDraftAction(Runnable onDraftAction) {
        this.onDraftAction = onDraftAction;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public void setCurrentLocalPlayerId(String playerId) {
        this.currentLocalPlayerId = playerId;
    }

    // --- Helper to add provinces to the correct layer ---
    public void addProvinces(Collection<SVGPath> provinces) {
        provinceLayer.getChildren().addAll(provinces);

        for (SVGPath province : provinces) {
            provinceNodeMap.put(province.getId(), province);
            Text label = new Text(""); // Start empty

            // Prevent the text from blocking clicks on the province
            label.setMouseTransparent(true);

            // High visibility styling (White text with a black outline)
            label.setFont(Font.font("System", FontWeight.BOLD, 18));
            label.setFill(Color.WHITE);
            label.setStroke(Color.BLACK);
            label.setStrokeType(StrokeType.OUTSIDE);
            label.setStrokeWidth(1);

            // Rough centering (We refine this dynamically in renderState)
            Bounds b = province.getBoundsInParent();
            label.setX(b.getCenterX());
            label.setY(b.getCenterY());

            // Save the label in our map and add it to the layer
            armyLabels.put(province.getId(), label);
            labelLayer.getChildren().add(label);
        }
    }

    // --- Handle the Source -> Destination flow ---
    public void handleProvinceClick(SVGPath clickedNode) {
        if (gameState == null || interactionLocked) {
            System.err.println("GameState not attached to board");
            return;
        }

        String clickedId = clickedNode.getId();

        // --- CLAIMING PHASE ---
        if (gameState.getCurrentPhase() == GameState.GamePhase.CLAIMING) {
            if (networkClaimHandler != null) {
                networkClaimHandler.accept(clickedId);
            } else {
                boolean claimed = gameState.claimStartingProvince(currentLocalPlayerId, clickedId);
                if (claimed) {
                    // Instantly sync the visuals
                    renderState(gameState);
                } else {
                    System.out.println("Cannot claim this province. It belongs to an enemy!");
                }
            }
            return; // Exit method
        }

        // --- DRAFTING PHASE ---
        if (gameState.getCurrentPhase() == GameState.GamePhase.DRAFTING) {
            Optional<Province> pData = gameState.getProvince(clickedId);
            if (pData.isPresent() && currentLocalPlayerId.equals(pData.get().getOwnerId())) {
                showDraftPopup(clickedNode);
            } else {
                System.out.println("Cannot draft here - province not owned!");
            }
            return;
        }

        if (sourceProvince == null) { // State 1: Nothing is selected yet. Set this as the Source.
            Optional<Province> pData = gameState.getProvince(clickedId);

            if (pData.isPresent() && currentLocalPlayerId.equals(pData.get().getOwnerId())) {
                sourceProvince = clickedNode;
                SvgMapLoader.setNodeSelected(sourceProvince, true);
                System.out.println("Selected Province ID: " + sourceProvince.getId());
            } else {
                System.out.println("Cannot select " + clickedId + " - you do not own it.");
            }

        } else if (sourceProvince == clickedNode) { // State 2: User clicked the same province again. Cancel selection.
            SvgMapLoader.setNodeSelected(sourceProvince, false);
            sourceProvince = null;
            System.out.println("Selection cancelled.");

        } else { // State 3: A source is selected, and they clicked a different province. Destination!
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

                Optional<Province> pData = gameState.getProvince(clickedId);
                if (pData.isPresent() && currentLocalPlayerId.equals(pData.get().getOwnerId())) {
                    // They own the new province, so safely pivot the selection
                    SvgMapLoader.setNodeSelected(sourceProvince, false);
                    sourceProvince = clickedNode;
                    SvgMapLoader.setNodeSelected(sourceProvince, true);
                    System.out.println("Pivoted selection to: " + sourceProvince.getId());
                } else {
                    // They clicked an invalid enemy province. Just cancel the original selection.
                    SvgMapLoader.setNodeSelected(sourceProvince, false);
                    sourceProvince = null;
                    System.out.println("Selection cleared.");
                }
            }
        }
    }

    public void clearArrows() {
        activeArrows.clear();
        arrowLayer.getChildren().clear();
    }

    private void drawArrow(SVGPath source, SVGPath target, String pathKey, String ownerId) {
        Bounds sourceBounds = source.getBoundsInParent();
        Bounds targetBounds = target.getBoundsInParent();

        double sx = sourceBounds.getCenterX();
        double sy = sourceBounds.getCenterY();
        double tx = targetBounds.getCenterX();
        double ty = targetBounds.getCenterY();

        double dx = tx - sx;
        double dy = ty - sy;
        double len = Math.hypot(dx, dy);
        if (len < 1) return; // degenerate; same province
        double ux = dx / len;
        double uy = dy / len;

        // Scale arrow geometry down for short-distance moves so close-neighbour
        // arrows don't collapse to "just an arrowhead".
        double scale = Math.min(1.0, len / 60.0);
        double pad = Math.min(ARROW_END_PAD * scale, len / 3);
        double headLen = ARROW_HEAD_LEN * scale;
        double headW = ARROW_HEAD_W * scale;
        double shaftWidth = Math.max(1.0, ARROW_SHAFT_WIDTH * scale);

        double sx2 = sx + ux * pad;
        double sy2 = sy + uy * pad;
        double tx2 = tx - ux * pad;
        double ty2 = ty - uy * pad;

        Color fill = Color.web(ColorManager.getColorForPlayer(ownerId));
        Color outline = fill.darker().darker();

        Line shaft = new Line(sx2, sy2, tx2, ty2);
        shaft.setStrokeWidth(shaftWidth);
        shaft.setStroke(fill);

        // Local-space arrowhead with its tip at the local origin (0,0).
        // Rotate around (0,0) — the tip — and then translate the tip onto (tx2, ty2).
        Polygon head = new Polygon(
                0, 0,
                -headLen, headW / 2,
                -headLen, -headW / 2
        );
        head.setFill(fill);
        head.setStroke(outline);
        head.setStrokeWidth(0.8);
        head.getTransforms().add(new javafx.scene.transform.Rotate(
                Math.toDegrees(Math.atan2(dy, dx)), 0, 0));
        // Push the head tip forward along the shaft direction so the line endpoint
        // is hidden behind the arrowhead instead of poking past it.
        double forward = ARROW_HEAD_FORWARD * scale;
        head.setTranslateX(tx2 + ux * forward);
        head.setTranslateY(ty2 + uy * forward);

        ArrowNode arrow = new ArrowNode(shaft, head);
        activeArrows.put(pathKey, arrow);
        arrowLayer.getChildren().add(arrow);
        arrowLayer.toFront();
        uiLayer.toFront();
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

    public void setInteractionLocked(boolean locked) {
        this.interactionLocked = locked;
    }

    private void drawSeaRoutes() {
        for (String[] route : SEA_ROUTES) {
            SVGPath p1 = provinceNodeMap.get(route[0]);
            SVGPath p2 = provinceNodeMap.get(route[1]);
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

    // Switches the pane to network mode. Clicks no longer mutate the local
    // GameState — instead the supplied consumers are called so the
    // NetworkGameController can send them to the server.
    public void enableNetworkMode(
            java.util.function.Consumer<String> claimHandler,
            java.util.function.Consumer<String> draftHandler,
            java.util.function.Consumer<com.mykogroup.riskclone.model.Move> moveHandler) {
        this.networkClaimHandler = claimHandler;
        this.networkDraftHandler = draftHandler;
        this.networkMoveHandler = moveHandler;
    }

    // --- Sync View to State ---
    public void renderState(GameState state) {
        if (!seaRoutesDrawn) {
            drawSeaRoutes();
            seaRoutesDrawn = true;
        }
        // Loop through all SVG paths currently in the province layer
        for (var node : provinceLayer.getChildren()) {
            if (node instanceof SVGPath svgPath) {
                String provinceId = svgPath.getId();

                // Find the matching province in the master state
                Optional<Province> provinceData = state.getProvince(provinceId);

                if (provinceData.isPresent()) {
                    Province p = provinceData.get();

                    // --- Handle Color/Owner ---
                    String ownerId = p.getOwnerId();
                    String newColor = ColorManager.getColorForPlayer(ownerId);
                    svgPath.getProperties().put("baseColor", newColor); // Update the node's memory of its base color

                    // Reapply the style (maintaining current selection status)
                    boolean isSelected = (boolean) svgPath.getProperties().get("selected");
                    svgPath.setStyle(SvgMapLoader.generateStyle(newColor, isSelected, false));

                    // --- Handle Text Labels ---
                    Text label = armyLabels.get(provinceId);
                    if (label != null) {
                        int count = p.getArmyCount();

                        // Only show numbers if there is an army
                        if (count > 0) {
                            label.setText(String.valueOf(count));

                            // Fine-tune centering based on text width (so "1" and "100" are both perfectly centered)
                            Bounds textBounds = label.getLayoutBounds();
                            Bounds pathBounds = svgPath.getBoundsInParent();

                            label.setX(pathBounds.getCenterX() - (textBounds.getWidth() / 2));
                            label.setY(pathBounds.getCenterY() + (textBounds.getHeight() / 4));
                        } else {
                            label.setText(""); // Hide if neutral/empty
                        }
                    }
                }
            }
        }

        // Reconcile arrows with the current queued moves. In network mode only
        // the local player's own moves are revealed — opponents stay hidden until
        // resolution. In hotseat everyone sees everyone's plans.
        arrowLayer.getChildren().clear();
        activeArrows.clear();
        boolean isNetwork = (networkClaimHandler != null);
        for (Move move : state.getQueuedMoves()) {
            if (isNetwork && !move.playerId().equals(currentLocalPlayerId)) continue;
            SVGPath src = findProvinceNode(move.fromId());
            SVGPath tgt = findProvinceNode(move.toId());
            if (src != null && tgt != null) {
                drawArrow(src, tgt, move.fromId() + "-" + move.toId(), move.playerId());
            }
        }
    }

    private SVGPath findProvinceNode(String provinceId) {
        for (var node : provinceLayer.getChildren()) {
            if (node instanceof SVGPath svgPath && provinceId.equals(svgPath.getId())) {
                return svgPath;
            }
        }
        return null;
    }

    private void showDraftPopup(SVGPath province) {
        uiLayer.getChildren().clear();

        int maxArmies = gameState.getDraftArmies(currentLocalPlayerId);
        if (maxArmies <= 0) return;

        VBox popup = new VBox(8);
        popup.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-padding: 12; -fx-background-radius: 5;");
        popup.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Add Troops to " + province.getId());
        titleLabel.setTextFill(Color.WHITE);

        Label countLabel = new Label("1 / " + maxArmies);
        countLabel.setTextFill(Color.GOLD);
        countLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        Slider slider = new Slider(1, maxArmies, 1);
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setPrefWidth(120);

        slider.valueProperty().addListener((obs, oldVal, newVal) ->
                countLabel.setText(newVal.intValue() + " / " + maxArmies));

        Button minusBtn = new Button("-");
        minusBtn.setStyle(STEPPER_STYLE);
        minusBtn.setOnAction(e -> { if (slider.getValue() > 1) slider.setValue(slider.getValue() - 1); });

        Button plusBtn = new Button("+");
        plusBtn.setStyle(STEPPER_STYLE);
        plusBtn.setOnAction(e -> { if (slider.getValue() < maxArmies) slider.setValue(slider.getValue() + 1); });

        HBox sliderRow = new HBox(6, minusBtn, slider, plusBtn);
        sliderRow.setAlignment(Pos.CENTER);

        Button confirmBtn = new Button("Confirm");
        confirmBtn.setStyle(CONFIRM_STYLE);
        confirmBtn.setOnAction(e -> {
            int count = (int) slider.getValue();
            if (networkDraftHandler != null) {
                for (int i = 0; i < count; i++) {
                    networkDraftHandler.accept(province.getId());
                }
            } else {
                for (int i = 0; i < count; i++) {
                    gameState.placeDraftArmy(currentLocalPlayerId, province.getId());
                }
                renderState(gameState);
                if (onDraftAction != null) onDraftAction.run();
            }
            uiLayer.getChildren().clear();
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(CANCEL_STYLE);
        cancelBtn.setOnAction(e -> uiLayer.getChildren().clear());

        HBox btnRow = new HBox(8, confirmBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER);

        popup.getChildren().addAll(titleLabel, sliderRow, countLabel, btnRow);

        Bounds b = province.getBoundsInParent();
        popup.setLayoutX(b.getCenterX() - 150);
        popup.setLayoutY(b.getCenterY());
        uiLayer.getChildren().add(popup);
    }

    private void showArmyPopup(SVGPath source, SVGPath target, int currentArmies, int maxArmies) {
        uiLayer.getChildren().clear();

        VBox popup = new VBox(8);
        popup.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-padding: 12; -fx-background-radius: 5;");
        popup.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Send Troops");
        titleLabel.setTextFill(Color.WHITE);

        Label countLabel = new Label(currentArmies + " / " + maxArmies);
        countLabel.setTextFill(Color.GOLD);
        countLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        Slider slider = new Slider(0, maxArmies, currentArmies);
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setPrefWidth(120);

        slider.valueProperty().addListener((obs, oldVal, newVal) ->
                countLabel.setText(newVal.intValue() + " / " + maxArmies));

        Button minusBtn = new Button("-");
        minusBtn.setStyle(STEPPER_STYLE);
        minusBtn.setOnAction(e -> { if (slider.getValue() > 0) slider.setValue(slider.getValue() - 1); });

        Button plusBtn = new Button("+");
        plusBtn.setStyle(STEPPER_STYLE);
        plusBtn.setOnAction(e -> { if (slider.getValue() < maxArmies) slider.setValue(slider.getValue() + 1); });

        HBox sliderRow = new HBox(6, minusBtn, slider, plusBtn);
        sliderRow.setAlignment(Pos.CENTER);

        Button confirmBtn = new Button("Confirm");
        confirmBtn.setStyle(CONFIRM_STYLE);
        confirmBtn.setOnAction(e -> {
            int finalArmies = (int) slider.getValue();
            String pathKey = source.getId() + "-" + target.getId();

            Move newMove = new Move(currentLocalPlayerId, source.getId(), target.getId(), finalArmies);
            if (networkMoveHandler != null) {
                networkMoveHandler.accept(newMove);
            } else if (finalArmies == 0) {
                gameState.setMove(newMove);
                ArrowNode arrow = activeArrows.remove(pathKey);
                if (arrow != null) arrowLayer.getChildren().remove(arrow);
            } else {
                gameState.setMove(newMove);
                if (!activeArrows.containsKey(pathKey)) drawArrow(source, target, pathKey, currentLocalPlayerId);
            }
            uiLayer.getChildren().clear();
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(CANCEL_STYLE);
        cancelBtn.setOnAction(e -> uiLayer.getChildren().clear());

        HBox btnRow = new HBox(8, confirmBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER);

        popup.getChildren().addAll(titleLabel, sliderRow, countLabel, btnRow);

        Bounds targetBounds = target.getBoundsInParent();
        popup.setLayoutX(targetBounds.getCenterX() - 150);
        popup.setLayoutY(targetBounds.getCenterY());
        uiLayer.getChildren().add(popup);
    }

    // ---------------------------------------------------------
    // RESOLUTION-PHASE ANIMATION HELPERS
    // ---------------------------------------------------------

    public void renderSnapshotState(Map<String, String> snapshotOwners,
                                    Map<String, Integer> snapshotArmies,
                                    GameState state) {
        if (!seaRoutesDrawn) {
            drawSeaRoutes();
            seaRoutesDrawn = true;
        }
        for (var node : provinceLayer.getChildren()) {
            if (!(node instanceof SVGPath svgPath)) continue;
            String provinceId = svgPath.getId();

            String ownerId = snapshotOwners.get(provinceId);
            int count = snapshotArmies.getOrDefault(provinceId, 0);

            String newColor = ColorManager.getColorForPlayer(ownerId);
            svgPath.getProperties().put("baseColor", newColor);
            Object selObj = svgPath.getProperties().get("selected");
            boolean isSelected = selObj instanceof Boolean b && b;
            svgPath.setStyle(SvgMapLoader.generateStyle(newColor, isSelected, false));

            Text label = armyLabels.get(provinceId);
            if (label != null) {
                if (count > 0) {
                    label.setText(String.valueOf(count));
                    Bounds textBounds = label.getLayoutBounds();
                    Bounds pathBounds = svgPath.getBoundsInParent();
                    label.setX(pathBounds.getCenterX() - (textBounds.getWidth() / 2));
                    label.setY(pathBounds.getCenterY() + (textBounds.getHeight() / 4));
                } else {
                    label.setText("");
                }
            }
        }
    }

    public void flashProvince(String provinceId, Color flashColor, Duration duration) {
        SVGPath path = provinceNodeMap.get(provinceId);
        if (path == null) return;

        DropShadow glow = new DropShadow();
        glow.setColor(flashColor);
        glow.setSpread(0.55);
        glow.setRadius(0);
        path.setEffect(glow);

        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 0)),
            new KeyFrame(duration.divide(2), new KeyValue(glow.radiusProperty(), 35)),
            new KeyFrame(duration, new KeyValue(glow.radiusProperty(), 0))
        );
        tl.setOnFinished(e -> path.setEffect(null));
        tl.play();
    }

    public void tweenArmyCount(String provinceId, int from, int to, Duration duration) {
        Text label = armyLabels.get(provinceId);
        SVGPath svgPath = provinceNodeMap.get(provinceId);
        if (label == null) return;

        IntegerProperty value = new SimpleIntegerProperty(from);
        value.addListener((obs, oldV, newV) -> {
            int n = newV.intValue();
            if (n > 0) {
                label.setText(String.valueOf(n));
                if (svgPath != null) {
                    Bounds textBounds = label.getLayoutBounds();
                    Bounds pathBounds = svgPath.getBoundsInParent();
                    label.setX(pathBounds.getCenterX() - (textBounds.getWidth() / 2));
                    label.setY(pathBounds.getCenterY() + (textBounds.getHeight() / 4));
                }
            } else {
                label.setText("");
            }
        });

        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(value, from)),
            new KeyFrame(duration, new KeyValue(value, to))
        );
        tl.play();
    }

    public void setProvinceOwnerColor(String provinceId, String ownerId) {
        SVGPath path = provinceNodeMap.get(provinceId);
        if (path == null) return;
        String newColor = ColorManager.getColorForPlayer(ownerId);
        path.getProperties().put("baseColor", newColor);
        Object selObj = path.getProperties().get("selected");
        boolean isSelected = selObj instanceof Boolean b && b;
        path.setStyle(SvgMapLoader.generateStyle(newColor, isSelected, false));
    }

    public void drawArrowsForMoves(List<Move> moves) {
        arrowLayer.getChildren().clear();
        activeArrows.clear();
        for (Move move : moves) {
            SVGPath src = findProvinceNode(move.fromId());
            SVGPath tgt = findProvinceNode(move.toId());
            if (src != null && tgt != null) {
                drawArrow(src, tgt, move.fromId() + "-" + move.toId(), move.playerId());
            }
        }
    }

    public void zoomToProvince(String provinceId, double targetScale, Duration duration) {
        SVGPath path = provinceNodeMap.get(provinceId);
        if (path == null || getScene() == null) return;

        Bounds pBounds = path.getBoundsInParent();
        double px = pBounds.getCenterX();
        double py = pBounds.getCenterY();

        double cx = getLayoutBounds().getWidth() / 2;
        double cy = getLayoutBounds().getHeight() / 2;

        Point2D paneOrigin = (getParent() != null)
            ? getParent().localToScene(getLayoutX(), getLayoutY())
            : new Point2D(0, 0);

        double sceneCenterX = getScene().getWidth() / 2;
        double sceneCenterY = getScene().getHeight() / 2;

        // After scale (pivot = layout-bounds center) and translate,
        // a local point (px, py) maps to scene coord:
        //   paneOrigin + cx + (px - cx) * scale + translate
        // Solve for translate so the point lands at the scene center.
        double newTX = sceneCenterX - paneOrigin.getX() - cx - (px - cx) * targetScale;
        double newTY = sceneCenterY - paneOrigin.getY() - cy - (py - cy) * targetScale;

        Timeline tl = new Timeline(new KeyFrame(duration,
            new KeyValue(scaleXProperty(), targetScale),
            new KeyValue(scaleYProperty(), targetScale),
            new KeyValue(translateXProperty(), newTX),
            new KeyValue(translateYProperty(), newTY)
        ));
        tl.play();
    }

    public void animateCameraTo(double scale, double translateX, double translateY, Duration duration) {
        Timeline tl = new Timeline(new KeyFrame(duration,
            new KeyValue(scaleXProperty(), scale),
            new KeyValue(scaleYProperty(), scale),
            new KeyValue(translateXProperty(), translateX),
            new KeyValue(translateYProperty(), translateY)
        ));
        tl.play();
    }

    public void pulseArrows(List<String> sourceIds, String destId, Duration duration) {
        for (String sourceId : sourceIds) {
            String key = sourceId + "-" + destId;
            ArrowNode arrow = activeArrows.get(key);
            if (arrow == null) continue;
            arrow.shaft.setStroke(Color.YELLOW);
            arrow.head.setFill(Color.YELLOW);
            // Boost scales with the arrow's current width so short, scaled-down
            // arrows don't pulse to a comically thick line.
            double baseWidth = arrow.shaft.getStrokeWidth();
            double boost = ARROW_PULSE_BOOST * (baseWidth / ARROW_SHAFT_WIDTH);
            Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(arrow.shaft.strokeWidthProperty(), baseWidth),
                    new KeyValue(arrow.opacityProperty(), 1.0)),
                new KeyFrame(duration.divide(2),
                    new KeyValue(arrow.shaft.strokeWidthProperty(), baseWidth + boost)),
                new KeyFrame(duration,
                    new KeyValue(arrow.shaft.strokeWidthProperty(), baseWidth),
                    new KeyValue(arrow.opacityProperty(), 0.0))
            );
            ArrowNode finalArrow = arrow;
            tl.setOnFinished(e -> {
                arrowLayer.getChildren().remove(finalArrow);
                activeArrows.remove(key);
            });
            tl.play();
        }
    }

    private static final String STEPPER_STYLE =
            "-fx-font-weight: bold; -fx-background-color: #4a5568; -fx-text-fill: white; -fx-pref-width: 28;";
    private static final String CONFIRM_STYLE =
            "-fx-font-weight: bold; -fx-background-color: #27ae60; -fx-text-fill: white;";
    private static final String CANCEL_STYLE =
            "-fx-font-weight: bold; -fx-background-color: #4a5568; -fx-text-fill: white;";
}
