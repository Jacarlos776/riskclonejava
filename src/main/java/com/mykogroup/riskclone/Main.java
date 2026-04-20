package com.mykogroup.riskclone;

import com.mykogroup.riskclone.engine.AdjacencyService;
import com.mykogroup.riskclone.engine.AiController;
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
import javafx.animation.PauseTransition;
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
import java.util.Random;

public class Main extends Application {
    // --- Class Variables for Game Loop ---
    private AiController aiController;
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
    private final String[] AI_NAMES = {
            "Jose Rizal", "Andres Bonifacio", "Magellan", "Lapu-lapu", "Antonio Luna",
            "Gabriela Silang", "Apolinario Mabini", "Emilio Jacinto", "Melchora Aquino",
            "Sultan Kudarat", "Ferdinand Magellan", "Juan Luna", "Emilio Aguinaldo"
    };

    private Timeline phaseTimer;
    private int timeRemaining;
    private int currentPlayerIndex = 0; // Tracks whose turn it is locally

    @Override
    public void start(Stage stage) {
        mainScene = new Scene(new Pane(), 1280, 720);
        stage.setTitle("RISK: Philippines");
        stage.setScene(mainScene);

        resetGameToMenu(); // Boots the game cleanly!

        stage.show();
    }

    // Completely builds a fresh board and state, destroying the old one
    private void resetGameToMenu() {
        if (phaseTimer != null) {
            phaseTimer.stop();
        }

        // Initialize the Engine and Board
        AdjacencyService adjacencyService = new AdjacencyService("/com/mykogroup/riskclone/province.json");
        aiController = new AiController(adjacencyService);
        GameState masterState = new GameState();
        InteractiveMapPane gameBoard = new InteractiveMapPane(adjacencyService, masterState);

        Map<String, SVGPath> mapNodes = SvgMapLoader.loadMap("/com/mykogroup/riskclone/map.svg", gameBoard::handleProvinceClick);
        gameBoard.addProvinces(mapNodes.values());

        masterState.setRegions(RegionLoader.loadRegions("/com/mykogroup/riskclone/region.json"));

        // Setup provinces as neutral first
        for (String id : mapNodes.keySet()) {
            masterState.getProvinces().add(new Province(id, null, 0));
        }

        // Bind the UI to the State
        gameBoard.renderState(masterState);

        // Send user to mode select
        showModeSelect(masterState, gameBoard);
    }

    // --- MODE SELECT ---
    private void showModeSelect(GameState masterState, InteractiveMapPane gameBoard) {
        VBox root = new VBox(24);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #2c3e50;");

        Label title = new Label("RISK: Philippines");
        title.setFont(Font.font("System", FontWeight.BOLD, 56));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Select a Game Mode");
        subtitle.setFont(Font.font("System", 20));
        subtitle.setTextFill(Color.LIGHTGRAY);

        Button hotseatBtn = new Button("Hotseat / Single Player");
        hotseatBtn.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 14 40; -fx-background-radius: 8;");
        hotseatBtn.setOnAction(e -> showSetupMenu(masterState, gameBoard));

        Button lanBtn = new Button("LAN Multiplayer  (Coming Soon)");
        lanBtn.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-background-color: #4a5568; -fx-text-fill: #888888; -fx-padding: 14 40; -fx-background-radius: 8;");
        lanBtn.setDisable(true);

        root.getChildren().addAll(title, subtitle, hotseatBtn, lanBtn);
        mainScene.setRoot(root);
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
                    Button aiToggle = (Button) row.getChildren().get(2); // Get the toggle button
                    ColorPicker colorPicker = (ColorPicker) row.getChildren().get(3);

                    // Check if the button says "AI"osh
                    boolean isAi = aiToggle.getText().contains("AI");

                    String pId = "player" + index;
                    masterState.getPlayers().add(new Player(pId, nameInput.getText(), isAi));
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

        gameBoard.setInteractionLocked(false);
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
        gameBoard.setInteractionLocked(false);
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
        gameBoard.setInteractionLocked(true);

        System.out.println("Processing Combat...");
        new ResolutionEngine().processTurn(masterState);

        gameBoard.clearArrows();
        gameBoard.renderState(masterState);

        List<Player> survivors = masterState.getAlivePlayers();

        if (survivors.size() == 1) {
            // WINNEr
            showGameOverScreen(survivors.getFirst());
        } else if (survivors.isEmpty()) {
            // Extremely rare edge case: Mutual destruction of the last two players
            showGameOverScreen(null);
        } else {
            // The war continues... loop back to Drafting Phase.
            new Timeline(new KeyFrame(Duration.seconds(3), ev -> {
                startDraftingPhase(masterState, gameBoard);
            })).play();
        }
    }

    // --- Victory Screen ---
    private void showGameOverScreen(Player winner) {
        VBox gameOverOverlay = new VBox(20);
        gameOverOverlay.setAlignment(Pos.CENTER);
        gameOverOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.85);"); // Dark fade over the map

        Label title = new Label("GAME OVER");
        title.setFont(Font.font("System", FontWeight.BOLD, 64));
        title.setTextFill(Color.WHITE);

        Label subTitle = new Label();
        subTitle.setFont(Font.font("System", FontWeight.BOLD, 32));
        if (winner != null) {
            subTitle.setText(winner.getDisplayName() + " has conquered the map!");
            subTitle.setTextFill(Color.web(ColorManager.getColorForPlayer(winner.getId())));
        } else {
            subTitle.setText("Total Annihilation. No victors remain.");
            subTitle.setTextFill(Color.GRAY);
        }

        Button playAgainBtn = new Button("Play Again");
        playAgainBtn.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 15 40;");

        // Triggers the factory reset!
        playAgainBtn.setOnAction(e -> resetGameToMenu());

        gameOverOverlay.getChildren().addAll(title, subTitle, playAgainBtn);

        // Assuming mainScene.getRoot() is currently your gameRoot (StackPane)
        if (mainScene.getRoot() instanceof StackPane root) {
            root.getChildren().add(gameOverOverlay);
        }
    }

    // --- HELPER METHODS ---
    private void handleHotseatPass(GameState masterState, InteractiveMapPane gameBoard, Runnable onAllReady) {
        Player currentPlayer = masterState.getPlayers().get(currentPlayerIndex);
        masterState.setPlayerReady(currentPlayer.getId());

        if (masterState.areAllPlayersReady()) {
            onAllReady.run();
        } else {
            // Loop forward until we find the next ALIVE player
            do {
                currentPlayerIndex++;
            } while (currentPlayerIndex < masterState.getPlayers().size() &&
                    !masterState.isPlayerAlive(masterState.getPlayers().get(currentPlayerIndex).getId()));

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

        // --- Handle AI Turns ---
        if (p.isAi()) {
            // Lock out human interaction
            gameBoard.setInteractionLocked(true);
            endTurnBtn.setDisable(true);

            // Wait 1.5 seconds to simulate "thinking"
            PauseTransition thinkPause = new PauseTransition(Duration.seconds(1.5));
            thinkPause.setOnFinished(e -> {
                GameState.GamePhase currentPhase = masterState.getCurrentPhase();

                if (currentPhase == GameState.GamePhase.CLAIMING) {
                    aiController.takeClaimingTurn(masterState, p.getId());
                    gameBoard.renderState(masterState); // Instantly show their choice

                    // Wait another 0.5s so the player can actually see the province change color, then end turn
                    PauseTransition endPause = new PauseTransition(Duration.seconds(0.5));
                    endPause.setOnFinished(ev -> {
                        endTurnBtn.setDisable(false);
                        endTurnBtn.fire(); // Simulate clicking the button
                    });
                    endPause.play();
                } else if (currentPhase == GameState.GamePhase.DRAFTING) {
                    playAiDraftAnimation(masterState, gameBoard, p);
                } else if (currentPhase == GameState.GamePhase.PLANNING) {
                    // --- Trigger the Planning Logic ---
                    aiController.takePlanningTurn(masterState, p.getId());

                    // Instantly render the map so the UI can draw the arrows for the AI's queued moves!
                    gameBoard.renderState(masterState);

                    // Add a small pause so players can briefly see the AI's arrows before the turn passes
                    PauseTransition endPause = new PauseTransition(Duration.seconds(1.0));
                    endPause.setOnFinished(ev -> {
                        endTurnBtn.setDisable(false);
                        endTurnBtn.fire();
                    });
                }
            });
            thinkPause.play();

        } else {
            // Ensure the board is unlocked for Humans
            gameBoard.setInteractionLocked(false);
            endTurnBtn.setDisable(false);
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
        Random rand = new Random();
        int index = playerRowsContainer.getChildren().size();

        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER);

        Label pLabel = new Label(); // Text set by refreshPlayerRows()
        pLabel.setFont(Font.font(18));
        pLabel.setTextFill(Color.WHITE);

        TextField nameInput = new TextField(); // Text set by refreshPlayerRows()
        nameInput.setFont(Font.font(16));

        Button aiToggleBtn = new Button("👤 Human");
        aiToggleBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-pref-width: 100px;");

        aiToggleBtn.setOnAction(e -> {
            if (aiToggleBtn.getText().contains("Human")) {
                // Switch TO AI
                aiToggleBtn.setText("🤖 AI");
                aiToggleBtn.setStyle("-fx-background-color: #8b5cf6; -fx-text-fill: white; -fx-font-weight: bold; -fx-pref-width: 100px;");
                nameInput.setDisable(true); // Lock the text field
                nameInput.setText(AI_NAMES[rand.nextInt(AI_NAMES.length)]); // Assign historical name
            } else {
                // Switch TO Human
                aiToggleBtn.setText("👤 Human");
                aiToggleBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-pref-width: 100px;");
                nameInput.setDisable(false); // Unlock the text field
                nameInput.setText(""); // Clear it so refreshPlayerRows can reset it
                refreshPlayerRows();
            }
        });

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

        row.getChildren().addAll(pLabel, nameInput, aiToggleBtn, colorPicker, removeBtn);
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
                Button removeBtn = (Button) row.getChildren().get(4);

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

    // --- AI Incremental Drafting ---
    private void playAiDraftAnimation(GameState masterState, InteractiveMapPane gameBoard, Player p) {
        int totalDraft = masterState.getDraftArmies(p.getId());

        if (totalDraft <= 0) {
            endTurnBtn.setDisable(false);
            endTurnBtn.fire();
            return;
        }

        // Calculate dynamic batch size (Drops troops faster in late-game)
        int batchSize = Math.max(1, totalDraft / 15);

        Timeline draftTimeline = new Timeline();
        draftTimeline.setCycleCount(Timeline.INDEFINITE); // Loop until out of troops

        // Fire every 300 milliseconds (0.3 seconds)
        KeyFrame frame = new KeyFrame(Duration.millis(300), e -> {
            int remaining = masterState.getDraftArmies(p.getId());

            if (remaining <= 0) {
                draftTimeline.stop();
                // Pause slightly after finishing before clicking End Turn
                PauseTransition endPause = new PauseTransition(Duration.seconds(0.5));
                endPause.setOnFinished(ev -> {
                    endTurnBtn.setDisable(false);
                    endTurnBtn.fire();
                });
                endPause.play();
                return;
            }

            // Figure out how many to place this tick
            int toPlace = Math.min(batchSize, remaining);

            // Re-evaluate the best target every tick! (Because threat levels change as we add troops)
            String targetId = aiController.getBestDraftTarget(masterState, p.getId());

            if (targetId != null) {
                for (int i = 0; i < toPlace; i++) {
                    masterState.placeDraftArmy(p.getId(), targetId);
                }

                // Update visuals
                gameBoard.renderState(masterState);
                draftCountLabel.setText("Armies left: " + masterState.getDraftArmies(p.getId()));
                System.out.println("AI drafted " + toPlace + " troops to " + targetId);
            }
        });

        draftTimeline.getKeyFrames().add(frame);
        draftTimeline.play();
    }

    // Convert JavaFX Color to Web Hex String
    private String toHexString(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

}
