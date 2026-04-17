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

    private int timeRemaining = 60;
    private Timeline phaseTimer;
    private int currentPlayerIndex = 0; // Tracks whose turn it is locally

    private void setupTimerUI(StackPane root, GameState masterState, InteractiveMapPane gameBoard, ResolutionEngine resolutionEngine) {
        // --- Timer Label ---
        Label timerLabel = new Label("Planning Phase: 60s");
        timerLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        timerLabel.setTextFill(Color.WHITE);
        timerLabel.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-padding: 10px; -fx-background-radius: 5px;");
        StackPane.setAlignment(timerLabel, Pos.TOP_CENTER); // 2. Align it to the Top Center of the screen
        timerLabel.setTranslateY(20); // Push it down slightly so it's not flush with the window edge

        // --- The Current Player Indicator (Remove this later for Socket/Multiplayer) ---
        Label playerTurnLabel = new Label("Current Player: " + masterState.getPlayers().get(0).getDisplayName());
        playerTurnLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        playerTurnLabel.setTextFill(Color.WHITE);
        playerTurnLabel.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-padding: 5px; -fx-background-radius: 5px;");
        StackPane.setAlignment(playerTurnLabel, Pos.TOP_LEFT);
        playerTurnLabel.setTranslateY(20);
        playerTurnLabel.setTranslateX(20);

        // --- The End Turn Button ---
        Button endTurnBtn = new Button("End Turn");
        endTurnBtn.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #f59e0b; -fx-text-fill: white;");
        StackPane.setAlignment(endTurnBtn, Pos.TOP_RIGHT);
        endTurnBtn.setTranslateY(20);
        endTurnBtn.setTranslateX(-20);

        // Add them all to root
        root.getChildren().addAll(timerLabel, playerTurnLabel, endTurnBtn);

        // --- Define the Resolution Trigger ---
        // We pull this into a Runnable so we can call it from the timer OR the button
        Runnable triggerResolution = () -> {
            phaseTimer.stop(); // Stop the clock!
            timerLabel.setText("RESOLUTION PHASE");
            timerLabel.setTextFill(Color.GOLD);
            endTurnBtn.setDisable(true);
            gameBoard.setDisable(true); // Lock the board

            System.out.println("Executing moves for all players...");

            // 1. Process the math!
            resolutionEngine.processTurn(masterState);

            // 2. Erase the visual arrows from the previous phase
            gameBoard.clearArrows();

            // 3. Re-render the map with the new ownership and troop counts
            gameBoard.renderState(masterState);

            // 4. Reset for the next turn
            // We use a small Timeline delay just so players can "see" the resolution phase
            // for a few seconds before the next planning phase starts.
            Timeline resetDelay = new Timeline(new KeyFrame(Duration.seconds(3), ev -> {
                currentPlayerIndex = 0;
                Player firstPlayer = masterState.getPlayers().get(currentPlayerIndex);
                gameBoard.setCurrentLocalPlayerId(firstPlayer.getId());

                playerTurnLabel.setText("Current Player: " + firstPlayer.getDisplayName());
                timerLabel.setText("Planning Phase: 60s");
                timerLabel.setTextFill(Color.WHITE);

                timeRemaining = 60;
                endTurnBtn.setDisable(false);
                gameBoard.setDisable(false);
                phaseTimer.playFromStart();
            }));
            resetDelay.play();
        };

        // --- Button Logic (Hotseat Passing) ---
        endTurnBtn.setOnAction(e -> {
            Player currentPlayer = masterState.getPlayers().get(currentPlayerIndex);
            masterState.setPlayerReady(currentPlayer.getId());

            System.out.println(currentPlayer.getDisplayName() + " is ready.");

            if (masterState.areAllPlayersReady()) {
                // Everyone is done! Skip the rest of the timer.
                triggerResolution.run();
            } else {
                // Pass to the next player
                currentPlayerIndex++;
                Player nextPlayer = masterState.getPlayers().get(currentPlayerIndex);

                // Update the map to queue moves for the new player
                gameBoard.setCurrentLocalPlayerId(nextPlayer.getId());

                // Update the UI
                playerTurnLabel.setText("Current Player: " + nextPlayer.getDisplayName());
            }
        });

        // --- Timeline Logic ---
        phaseTimer = new Timeline(
                new KeyFrame(Duration.seconds(1), event -> {
                    timeRemaining--;
                    timerLabel.setText("Planning Phase: " + timeRemaining + "s");

                    // Optional: Turn red when time is running out
                    if (timeRemaining <= 10) {
                        timerLabel.setTextFill(Color.RED);
                    }
                })
        );

        // Tell it to run exactly 60 times (for 60 seconds)
        phaseTimer.setCycleCount(60);
        phaseTimer.setOnFinished(event -> triggerResolution.run());
        phaseTimer.play();

        // Start the clock!
        phaseTimer.play();
    }

    @Override
    public void start(Stage stage) {

        AdjacencyService adjacencyService = new AdjacencyService("/com/mykogroup/riskclone/province.json");
        ResolutionEngine resolutionEngine = new ResolutionEngine();
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

        // --- Initialize the fixed timer HUD ---
        setupTimerUI(root, masterState, gameBoard, resolutionEngine);

        // 6. Setup and show the Scene
        Scene scene = new Scene(root, 1280, 720);

        stage.setTitle("Title here");
        stage.setScene(scene);
        stage.show();
    }
}
