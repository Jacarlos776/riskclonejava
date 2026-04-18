package com.mykogroup.riskclone;

import com.mykogroup.riskclone.engine.AdjacencyService;
import com.mykogroup.riskclone.engine.RegionLoader;
import com.mykogroup.riskclone.engine.ResolutionEngine;
import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Player;
import com.mykogroup.riskclone.model.Province;
import com.mykogroup.riskclone.model.Region;
import com.mykogroup.riskclone.view.ColorManager;
import com.mykogroup.riskclone.view.InteractiveMapPane;
import com.mykogroup.riskclone.view.SvgMapLoader;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

public class Main extends Application {
    // --- Class Variables for Game Loop ---
    private Scene mainScene; // Tracks the main window scene
    private Label timerLabel;
    private Label playerTurnLabel;
    private Label draftCountLabel; // Shows "Armies left: 5"
    private Button endTurnBtn;
    private VBox playerRowsContainer;
    private final String[] DEFAULT_COLORS = {
            "#ef4444", "#3b82f6", "#10b981", "#f59e0b",
            "#8b5cf6", "#ec4899", "#14b8a6", "#f97316"
    };

    private Timeline phaseTimer;
    private int timeRemaining;
    private int currentPlayerIndex = 0; // Tracks whose turn it is locally

    @Override
    public void start(Stage stage) {

        // Initialize the Engine and Board
        AdjacencyService adjacencyService = new AdjacencyService("/com/mykogroup/riskclone/province.json");
        GameState masterState = new GameState();

        // --- Load and inject the regions ---
        List<Region> loadedRegions = RegionLoader.loadRegions("/com/mykogroup/riskclone/region.json");
        masterState.setRegions(loadedRegions);

        // 1. Create the board first so we can pass its click-handler to the loader
        InteractiveMapPane gameBoard = new InteractiveMapPane(adjacencyService, masterState);

        // 2. Load the map nodes, passing in gameBoard::handleProvinceClick, then add al SVG nodes to the Pane
        Map<String, SVGPath> mapNodes = SvgMapLoader.loadMap("/com/mykogroup/riskclone/map.svg", gameBoard::handleProvinceClick);
        gameBoard.addProvinces(mapNodes.values());

        // Pass gameState to InteractiveMapPane
        gameBoard.setGameState(masterState);

        // Add all 81 provinces as neutral first
        for (String id : mapNodes.keySet()) {
            masterState.getProvinces().add(new Province(id, null, 0));
        }

        // Bind the UI to the State
        gameBoard.renderState(masterState);

        // 4. Create static root layout for ocean background
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: #add8e6;");

        // 5. Add map to root
        root.getChildren().add(gameBoard);

        // 6. Setup and show the Scene
        mainScene = new Scene(new Pane(), 1280, 720); // Placeholder root

        stage.setTitle("Title here");
        stage.setScene(mainScene);

        // Show lobby menu
        showSetupMenu(masterState, gameBoard);

        stage.show();
    }

    // --- PRE-GAME MENU ---
    private void showSetupMenu(GameState masterState, InteractiveMapPane gameBoard) {
        VBox menuRoot = new VBox(20);
        menuRoot.setAlignment(Pos.CENTER);
        menuRoot.setStyle("-fx-background-color: #2c3e50;");

        Label titleLabel = new Label("Game Setup");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 48));
        titleLabel.setTextFill(Color.WHITE);

        playerRowsContainer = new VBox(15);
        playerRowsContainer.setAlignment(Pos.CENTER);

        // Generate the minimum 4 players
        for (int i = 0; i < 4; i++) {
            addPlayerRow();
        }

        // Add Player Button
        Button addPlayerBtn = new Button("+ Add Player");
        addPlayerBtn.setStyle("-fx-font-size: 14px; -fx-background-color: #4a5568; -fx-text-fill: white; -fx-padding: 5 15;");
        addPlayerBtn.setOnAction(e -> addPlayerRow());

        // Start Button
        Button startBtn = new Button("Start Game");
        startBtn.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 10 30;");

        startBtn.setOnAction(e -> {
            masterState.getPlayers().clear();

            // Loop through our dynamic container to save the customized players
            int index = 1;
            for (Node node : playerRowsContainer.getChildren()) {
                if (node instanceof HBox row) {
                    // Extract the values from the UI elements in the row
                    TextField nameInput = (TextField) row.getChildren().get(1);
                    ColorPicker colorPicker = (ColorPicker) row.getChildren().get(2);

                    String pId = "player" + index;
                    masterState.getPlayers().add(new Player(pId, nameInput.getText()));
                    ColorManager.setPlayerColor(pId, toHexString(colorPicker.getValue()));

                    index++;
                }
            }

            launchGameView(masterState, gameBoard);
        });

        menuRoot.getChildren().addAll(titleLabel, playerRowsContainer, addPlayerBtn, startBtn);
        mainScene.setRoot(menuRoot); // Attach menu to the window
    }

    // --- GAME LAUNCHER ---
    private void launchGameView(GameState masterState, InteractiveMapPane gameBoard) {
        // 1. Build the game container
        StackPane gameRoot = new StackPane();
        gameRoot.setStyle("-fx-background-color: #add8e6;"); // Ocean background
        gameRoot.getChildren().add(gameBoard);

        // 2. Apply initial state to the map
        gameBoard.renderState(masterState);

        // 3. Initialize the fixed HUD (Timers, Buttons)
        initUI(gameRoot, masterState, gameBoard);

        // 4. Swap the window out to show the game
        mainScene.setRoot(gameRoot);

        // 5. Start the Game Loop!
        startClaimingPhase(masterState, gameBoard);
    }

    // --- BUILDS THE UI ONCE ---
    private void initUI(StackPane root, GameState masterState, InteractiveMapPane gameBoard) {
        timerLabel = new Label();
        timerLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        timerLabel.setTextFill(Color.WHITE);
        timerLabel.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-padding: 10px; -fx-background-radius: 5px;");
        StackPane.setAlignment(timerLabel, Pos.TOP_CENTER);
        timerLabel.setTranslateY(20);

        playerTurnLabel = new Label();
        playerTurnLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        playerTurnLabel.setTextFill(Color.WHITE);
        playerTurnLabel.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-padding: 5px; -fx-background-radius: 5px;");
        StackPane.setAlignment(playerTurnLabel, Pos.TOP_LEFT);
        playerTurnLabel.setTranslateY(20);
        playerTurnLabel.setTranslateX(20);

        draftCountLabel = new Label();
        draftCountLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        draftCountLabel.setTextFill(Color.GOLD);
        draftCountLabel.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-padding: 5px; -fx-background-radius: 5px;");
        StackPane.setAlignment(draftCountLabel, Pos.TOP_LEFT);
        draftCountLabel.setTranslateY(60); // Place below player name
        draftCountLabel.setTranslateX(20);

        endTurnBtn = new Button("End Turn");
        endTurnBtn.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #f59e0b; -fx-text-fill: white;");
        StackPane.setAlignment(endTurnBtn, Pos.TOP_RIGHT);
        endTurnBtn.setTranslateY(20);
        endTurnBtn.setTranslateX(-20);

        root.getChildren().addAll(timerLabel, playerTurnLabel, draftCountLabel, endTurnBtn);

        // Set the callback so clicking the map updates the UI safely
        gameBoard.setOnDraftAction(() -> {
            updatePlayerTurnUI(masterState, gameBoard);
        });
    }

    // === GAME PHASES ===
    // CLAIMING PHASE
    private void startClaimingPhase(GameState masterState, InteractiveMapPane gameBoard) {
        masterState.setCurrentPhase(GameState.GamePhase.CLAIMING);
        masterState.resetReadyStates();
        currentPlayerIndex = 0;

        // Stop the clock (Claiming has no time limit)
        if (phaseTimer != null) phaseTimer.stop();
        timerLabel.setText("Claiming Phase");
        timerLabel.setTextFill(Color.GOLD);

        // Repurpose the draft label to give instructions
        draftCountLabel.setText("Select 1 starting province");
        draftCountLabel.setVisible(true);

        gameBoard.setDisable(false);
        endTurnBtn.setDisable(false);

        updatePlayerTurnUI(masterState, gameBoard);

        // Set up the End Turn Button for Claiming
        endTurnBtn.setOnAction(e -> {
            Player p = masterState.getPlayers().get(currentPlayerIndex);

            // Validation Check: Make sure they actually clicked a province!
            long ownedCount = masterState.getProvinces().stream()
                    .filter(prov -> p.getId().equals(prov.getOwnerId()))
                    .count();

            if (ownedCount == 0) {
                // If they haven't claimed one, don't let them pass the turn
                System.out.println("You must claim a province first!");
                draftCountLabel.setText("PLEASE SELECT A PROVINCE!");
                draftCountLabel.setTextFill(Color.RED);
                return;
            }

            // Pass the turn. Once everyone claims, move to Drafting!
            handleHotseatPass(masterState, gameBoard, () -> startDraftingPhase(masterState, gameBoard));
        });
    }

    // DRAFTING PHASE
    private void startDraftingPhase(GameState masterState, InteractiveMapPane gameBoard) {
        masterState.setCurrentPhase(GameState.GamePhase.DRAFTING);
        masterState.initDraftPools(5); // Give everyone 5 troops
        masterState.resetReadyStates();

        currentPlayerIndex = 0;
        timeRemaining = 20; // 20 Second Draft
        gameBoard.setDisable(false);
        endTurnBtn.setDisable(false);

        draftCountLabel.setVisible(true);
        draftCountLabel.setTextFill(Color.GOLD);

        updatePlayerTurnUI(masterState, gameBoard);

        // Setup End Turn button for Drafting
        endTurnBtn.setOnAction(e -> handleHotseatPass(masterState, gameBoard, () -> startPlanningPhase(masterState, gameBoard)));

        startTimer("Drafting", () -> startPlanningPhase(masterState, gameBoard));
    }

    // PLANNING PHASE
    private void startPlanningPhase(GameState masterState, InteractiveMapPane gameBoard) {
        masterState.setCurrentPhase(GameState.GamePhase.PLANNING);
        masterState.resetReadyStates();

        currentPlayerIndex = 0;
        timeRemaining = 60; // 60 Second Planning
        draftCountLabel.setVisible(false); // Hide the draft tracker

        updatePlayerTurnUI(masterState, gameBoard);

        // Setup End Turn button for Planning
        endTurnBtn.setOnAction(e -> handleHotseatPass(masterState, gameBoard, () -> triggerResolutionPhase(masterState, gameBoard)));

        startTimer("Planning", () -> triggerResolutionPhase(masterState, gameBoard));
    }

    // RESOLUTION PHASE
    private void triggerResolutionPhase(GameState masterState, InteractiveMapPane gameBoard) {
        if (phaseTimer != null) phaseTimer.stop();

        masterState.setCurrentPhase(GameState.GamePhase.RESOLUTION);
        timerLabel.setText("RESOLUTION PHASE");
        timerLabel.setTextFill(Color.GOLD);
        endTurnBtn.setDisable(true);
        gameBoard.setDisable(true);

        System.out.println("Processing Combat...");
        new ResolutionEngine().processTurn(masterState);

        gameBoard.clearArrows();
        gameBoard.renderState(masterState);

        // Wait 3 seconds to show results, then loop back to Drafting
        new Timeline(new KeyFrame(Duration.seconds(3), ev -> {
            startDraftingPhase(masterState, gameBoard);
        })).play();
    }

    // --- HELPER METHODS ---
    private void handleHotseatPass(GameState masterState, InteractiveMapPane gameBoard, Runnable onAllReady) {
        Player currentPlayer = masterState.getPlayers().get(currentPlayerIndex);
        masterState.setPlayerReady(currentPlayer.getId());

        if (masterState.areAllPlayersReady()) {
            onAllReady.run(); // Move to the next phase
        } else {
            currentPlayerIndex++;
            updatePlayerTurnUI(masterState, gameBoard);
        }
    }

    private void updatePlayerTurnUI(GameState masterState, InteractiveMapPane gameBoard) {
        Player p = masterState.getPlayers().get(currentPlayerIndex);
        gameBoard.setCurrentLocalPlayerId(p.getId());
        playerTurnLabel.setText("Current Player: " + p.getDisplayName());
        if (masterState.getCurrentPhase() == GameState.GamePhase.CLAIMING) {
            draftCountLabel.setText("Select 1 starting province");
            draftCountLabel.setTextFill(Color.GOLD);
        } else if (masterState.getCurrentPhase() == GameState.GamePhase.DRAFTING) {
            draftCountLabel.setText("Armies left: " + masterState.getDraftArmies(p.getId()));
            draftCountLabel.setTextFill(Color.GOLD);
        }
    }

    private void startTimer(String phaseName, Runnable onComplete) {
        if (phaseTimer != null) phaseTimer.stop();
        timerLabel.setTextFill(Color.WHITE);

        phaseTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            timeRemaining--;
            timerLabel.setText(phaseName + " Phase: " + timeRemaining + "s");
            if (timeRemaining <= 5) timerLabel.setTextFill(Color.RED);
        }));
        phaseTimer.setCycleCount(timeRemaining);
        phaseTimer.setOnFinished(event -> onComplete.run());
        phaseTimer.playFromStart();
    }

    private void addPlayerRow() {
        int index = playerRowsContainer.getChildren().size();

        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER);

        Label pLabel = new Label(); // Text set by refreshPlayerRows()
        pLabel.setFont(Font.font(18));
        pLabel.setTextFill(Color.WHITE);

        TextField nameInput = new TextField(); // Text set by refreshPlayerRows()
        nameInput.setFont(Font.font(16));

        // Grab a default color based on their index, looping back to the start if we exceed 8
        String defaultHex = DEFAULT_COLORS[index % DEFAULT_COLORS.length];
        ColorPicker colorPicker = new ColorPicker(Color.web(defaultHex));
        colorPicker.setStyle("-fx-font-size: 14px;");

        Button removeBtn = new Button("X");
        removeBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold;");
        removeBtn.setOnAction(e -> {
            playerRowsContainer.getChildren().remove(row);
            refreshPlayerRows(); // Recalculate labels and button states
        });

        row.getChildren().addAll(pLabel, nameInput, colorPicker, removeBtn);
        playerRowsContainer.getChildren().add(row);

        refreshPlayerRows(); // Update UI states
    }

    private void refreshPlayerRows() {
        int totalPlayers = playerRowsContainer.getChildren().size();
        int currentIndex = 1;

        for (Node node : playerRowsContainer.getChildren()) {
            if (node instanceof HBox row) {
                Label pLabel = (Label) row.getChildren().get(0);
                TextField nameInput = (TextField) row.getChildren().get(1);
                Button removeBtn = (Button) row.getChildren().get(3);

                // Update the visual numbering
                pLabel.setText("Player " + currentIndex + " Name:");

                // If they haven't typed a custom name yet, update the placeholder
                if (nameInput.getText().isEmpty() || nameInput.getText().startsWith("Player ")) {
                    nameInput.setText("Player " + currentIndex);
                }

                // Disable the remove button if we are at the minimum of 4 players
                removeBtn.setDisable(totalPlayers <= 4);

                currentIndex++;
            }
        }
    }

    // Convert JavaFX Color to Web Hex String
    private String toHexString(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

}
