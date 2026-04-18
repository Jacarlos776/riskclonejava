package com.mykogroup.riskclone;

import com.mykogroup.riskclone.engine.AdjacencyService;
import com.mykogroup.riskclone.engine.ResolutionEngine;
import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Player;
import com.mykogroup.riskclone.model.Province;
import com.mykogroup.riskclone.view.InteractiveMapPane;
import com.mykogroup.riskclone.view.SvgMapLoader;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Map;

public class Main extends Application {
    // --- Class Variables for Game Loop ---
    private Label timerLabel;
    private Label playerTurnLabel;
    private Label draftCountLabel; // NEW: Shows "Armies left: 5"
    private Button endTurnBtn;

    private Timeline phaseTimer;
    private int timeRemaining;
    private int currentPlayerIndex = 0; // Tracks whose turn it is locally

    @Override
    public void start(Stage stage) {

        AdjacencyService adjacencyService = new AdjacencyService("/com/mykogroup/riskclone/province.json");
        GameState masterState = new GameState();

        // 1. Create the board first so we can pass its click-handler to the loader
        InteractiveMapPane gameBoard = new InteractiveMapPane(adjacencyService, masterState);

        // 2. Load the map nodes, passing in gameBoard::handleProvinceClick, then add al SVG nodes to the Pane
        Map<String, SVGPath> mapNodes = SvgMapLoader.loadMap("/com/mykogroup/riskclone/map.svg", gameBoard::handleProvinceClick);
        gameBoard.addProvinces(mapNodes.values());

        // Pass gameState to InteractiveMapPane
        gameBoard.setGameState(masterState);

        // 3. Add players to GameState
        masterState.getPlayers().add(new Player("player1", "Joshua"));
        masterState.getPlayers().add(new Player("player2", "Enemy AI"));

        // Add all 81 provinces as neutral first
        for (String id : mapNodes.keySet()) {
            masterState.getProvinces().add(new Province(id, null, 0));
        }

        // Claim a few specific provinces for testing
        masterState.getProvince("PH-BTN").ifPresent(p -> { p.setOwnerId("player1"); p.setArmyCount(10); });
        masterState.getProvince("PH-CAG").ifPresent(p -> { p.setOwnerId("player1"); p.setArmyCount(5); });

        masterState.getProvince("PH-ILN").ifPresent(p -> { p.setOwnerId("player2"); p.setArmyCount(20); });

        // Bind the UI to the State
        gameBoard.renderState(masterState);

        // 4. Create static root layout for ocean background
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: #add8e6;");

        // 5. Add map to root
        root.getChildren().add(gameBoard);

        // Build the persistent UI
        initUI(root, masterState, gameBoard);

        // Kick off the infinite game loop starting with Drafting!
        startDraftingPhase(masterState, gameBoard);

        // 6. Setup and show the Scene
        Scene scene = new Scene(root, 1280, 720);

        stage.setTitle("Title here");
        stage.setScene(scene);
        stage.show();
    }

    // --- 1. BUILD THE UI ONCE ---
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

        // Set the callback so clicking the map updates the Draft Label
        gameBoard.setOnDraftAction(() -> {
            String pId = masterState.getPlayers().get(currentPlayerIndex).getId();
            draftCountLabel.setText("Armies left: " + masterState.getDraftArmies(pId));
        });
    }

    // --- THE DRAFTING PHASE ---
    private void startDraftingPhase(GameState masterState, InteractiveMapPane gameBoard) {
        masterState.setCurrentPhase(GameState.GamePhase.DRAFTING);
        masterState.initDraftPools(5); // Give everyone 5 troops
        masterState.resetReadyStates();

        currentPlayerIndex = 0;
        timeRemaining = 20; // 20 Second Draft
        gameBoard.setDisable(false);
        endTurnBtn.setDisable(false);
        draftCountLabel.setVisible(true);

        updatePlayerTurnUI(masterState, gameBoard);

        // Setup End Turn button for Drafting
        endTurnBtn.setOnAction(e -> handleHotseatPass(masterState, gameBoard, () -> startPlanningPhase(masterState, gameBoard)));

        startTimer("Drafting", () -> startPlanningPhase(masterState, gameBoard));
    }

    // --- THE PLANNING PHASE ---
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

    // --- THE RESOLUTION PHASE ---
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
        draftCountLabel.setText("Armies left: " + masterState.getDraftArmies(p.getId()));
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
