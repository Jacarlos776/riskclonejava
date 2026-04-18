package com.mykogroup.riskclone;

import com.mykogroup.riskclone.engine.AdjacencyService;
import com.mykogroup.riskclone.engine.RegionLoader;
import com.mykogroup.riskclone.engine.ResolutionEngine;
import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Player;
import com.mykogroup.riskclone.model.Province;
import com.mykogroup.riskclone.model.Region;
import com.mykogroup.riskclone.view.InteractiveMapPane;
import com.mykogroup.riskclone.view.SvgMapLoader;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
    private Label draftCountLabel; // NEW: Shows "Armies left: 5"
    private Button endTurnBtn;

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

        // Player 1 Input
        HBox p1Box = new HBox(10);
        p1Box.setAlignment(Pos.CENTER);
        Label p1Label = new Label("Player 1 Name:");
        p1Label.setFont(Font.font(18));
        p1Label.setTextFill(Color.web("#ef4444"));
        TextField p1Input = new TextField("Joshua");
        p1Input.setFont(Font.font(16));
        p1Box.getChildren().addAll(p1Label, p1Input);

        // Player 2 Input
        HBox p2Box = new HBox(10);
        p2Box.setAlignment(Pos.CENTER);
        Label p2Label = new Label("Player 2 Name:");
        p2Label.setFont(Font.font(18));
        p2Label.setTextFill(Color.web("#3b82f6"));
        TextField p2Input = new TextField("Enemy AI");
        p2Input.setFont(Font.font(16));
        p2Box.getChildren().addAll(p2Label, p2Input);

        // Start Button
        Button startBtn = new Button("Start Game");
        startBtn.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 10 30;");

        startBtn.setOnAction(e -> {
            // 1. Save the customized players into the GameState
            masterState.getPlayers().clear();
            masterState.getPlayers().add(new Player("player1", p1Input.getText()));
            masterState.getPlayers().add(new Player("player2", p2Input.getText()));

            // 2. Transition to the Game Board
            launchGameView(masterState, gameBoard);
        });

        menuRoot.getChildren().addAll(titleLabel, p1Box, p2Box, startBtn);
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
}
