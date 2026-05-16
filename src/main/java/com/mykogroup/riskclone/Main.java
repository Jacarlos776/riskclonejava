package com.mykogroup.riskclone;

import com.mykogroup.riskclone.engine.AdjacencyEditor;
import com.mykogroup.riskclone.engine.AdjacencyService;
import com.mykogroup.riskclone.engine.AiController;
import com.mykogroup.riskclone.engine.RegionLoader;
import com.mykogroup.riskclone.engine.ResolutionEngine;
import com.mykogroup.riskclone.engine.ResolutionResult;
import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Move;
import com.mykogroup.riskclone.model.Player;
import com.mykogroup.riskclone.model.Province;
import com.mykogroup.riskclone.model.Region;
import com.mykogroup.riskclone.model.LobbyPlayer;
import com.fasterxml.jackson.databind.JsonNode;
import com.mykogroup.riskclone.view.ColorManager;
import com.mykogroup.riskclone.view.InteractiveMapPane;
import com.mykogroup.riskclone.view.LocalLobbyPane;
import com.mykogroup.riskclone.view.MapEditorScene;
import com.mykogroup.riskclone.view.SvgMapLoader;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.geometry.Insets;
import javafx.util.Duration;

import java.util.*;
import java.util.Map;
import java.util.Random;

public class Main extends Application {
    // --- Design Tokens ---
    public static Font HEADER_FONT;
    public static Font BODY_FONT;
    public static String PRIMARY_BTN_STYLE;
    private static String BTN_IMG_PATH;
    private static javafx.scene.image.Image MENU_BG_GIF; // cached so we don't re-decode on every visit

    /** Same precolonial image button as PRIMARY_BTN_STYLE, but at a custom size. */
    public static String primaryBtnStyle(double width, double height) {
        return "-fx-background-image: url('" + BTN_IMG_PATH + "'); " +
                "-fx-background-size: 100% 100%; " +
                "-fx-background-color: transparent; " +
                "-fx-background-repeat: no-repeat; " +
                "-fx-min-width: " + width + "px; -fx-min-height: " + height + "px; " +
                "-fx-max-width: " + width + "px; -fx-max-height: " + height + "px; " +
                "-fx-text-fill: white; " +
                "-fx-alignment: center; " +
                "-fx-padding: 0 0 6 0; " +
                "-fx-cursor: hand;";
    }

    /** Tribo header font at a custom size (matches HEADER_FONT family). */
    public static Font headerFont(double size) {
        if (HEADER_FONT == null) return Font.font("System", FontWeight.BOLD, size);
        return Font.font(HEADER_FONT.getFamily(), size);
    }

    /** Handle to a live in-scene overlay. Call {@link #close()} to dismiss. */
    public static final class Overlay {
        private final StackPane host;
        private final Pane scrim;
        private final Node content;
        private boolean closed = false;
        Overlay(StackPane host, Pane scrim, Node content) {
            this.host = host; this.scrim = scrim; this.content = content;
        }
        public void close() {
            if (closed) return;
            closed = true;
            host.getChildren().removeAll(scrim, content);
        }
    }

    /**
     * Show a node centered over the current scene root, behind a click-blocking scrim.
     * Lighter than spinning up a transparent modal Stage. If the current root isn't a
     * StackPane, it gets wrapped in one (one-time op).
     */
    public static Overlay showOverlay(Node content) {
        Parent root = mainSceneStatic.getRoot();
        StackPane host;
        if (root instanceof StackPane sp) {
            host = sp;
        } else {
            host = new StackPane(root);
            mainSceneStatic.setRoot(host);
        }
        Pane scrim = new Pane();
        scrim.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        scrim.setOnMouseClicked(javafx.event.Event::consume);
        StackPane.setAlignment(content, Pos.CENTER);
        host.getChildren().addAll(scrim, content);
        return new Overlay(host, scrim, content);
    }

    // --- Class Variables for Game Loop ---
    private AiController aiController;
    private Scene mainScene; // Tracks the main window scene
    private static Scene mainSceneStatic; // Static handle so overlay helpers can reach the scene
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
    private int phaseTurnDuration;
    private String currentPhaseName;
    private int currentPlayerIndex = 0; // Tracks whose turn it is locally

    // --- Core Game State ---
    private GameState masterState;
    private InteractiveMapPane gameBoard;

    // --- Network ---
    private com.mykogroup.riskclone.network.GameServer gameServer; // non-null when this instance is hosting
    private com.mykogroup.riskclone.network.GameClient gameClient; // non-null in any network session
    private com.mykogroup.riskclone.engine.NetworkGameController networkController;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public void start(Stage stage) {
        loadDesignTokens();

        mainScene = new Scene(new Pane(), 1280, 720);
        mainSceneStatic = mainScene;
        stage.setTitle("RISK: Philippines");
        stage.setScene(mainScene);

        initializeGame();
        showLoadingScreen(this::showMainMenu);

        stage.show();
    }

    private void showMainMenu() {
        showModeSelect();
    }

    // Helper for themed hover effects
    public static void addHoverEffect(javafx.scene.control.ButtonBase btn) {
        final String KEY = "hoverAnim";
        btn.setOnMouseEntered(e -> playHoverScale(btn, KEY, 1.1, 0.9));
        btn.setOnMouseExited(e -> playHoverScale(btn, KEY, 1.0, 1.0));
    }

    private static void playHoverScale(javafx.scene.control.ButtonBase btn,
                                       String key, double targetScale, double targetOpacity) {
        Object prev = btn.getProperties().get(key);
        if (prev instanceof javafx.animation.Animation a) a.stop();

        javafx.animation.Timeline tl = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(Duration.millis(150),
                new javafx.animation.KeyValue(btn.scaleXProperty(), targetScale,
                    javafx.animation.Interpolator.EASE_OUT),
                new javafx.animation.KeyValue(btn.scaleYProperty(), targetScale,
                    javafx.animation.Interpolator.EASE_OUT),
                new javafx.animation.KeyValue(btn.opacityProperty(), targetOpacity,
                    javafx.animation.Interpolator.EASE_OUT)
            )
        );
        btn.getProperties().put(key, tl);
        tl.play();
    }

    private void loadDesignTokens() {
        try {
            HEADER_FONT = Font
                    .loadFont(getClass().getResourceAsStream("/com/mykogroup/riskclone/assets/Tribo Regular.ttf"), 42);
            BODY_FONT = Font.loadFont(
                    getClass().getResourceAsStream("/com/mykogroup/riskclone/assets/Nunito-VariableFont_wght.ttf"), 18);

            BTN_IMG_PATH = getClass().getResource("/com/mykogroup/riskclone/assets/main-menu-btn.png")
                    .toExternalForm();
            PRIMARY_BTN_STYLE = "-fx-background-image: url('" + BTN_IMG_PATH + "'); " +
                    "-fx-background-size: 100% 100%; " +
                    "-fx-background-color: transparent; " +
                    "-fx-background-repeat: no-repeat; " +
                    "-fx-min-width: 320px; -fx-min-height: 70px; " +
                    "-fx-max-width: 320px; -fx-max-height: 70px; " +
                    "-fx-text-fill: white; " +
                    "-fx-alignment: center; " +
                    "-fx-padding: 0 0 6 0; " + // Slight bottom nudge to compensate for font baseline
                    "-fx-cursor: hand;";

            MENU_BG_GIF = new javafx.scene.image.Image(
                    getClass().getResourceAsStream("/com/mykogroup/riskclone/assets/main-menu-bg.gif"));
        } catch (Exception e) {
            System.err.println("Error loading design tokens: " + e.getMessage());
            // Fallback
            HEADER_FONT = Font.font("System", FontWeight.BOLD, 64);
            BODY_FONT = Font.font("System", 18);
            PRIMARY_BTN_STYLE = "-fx-background-color: #3d2b1f; -fx-text-fill: white;";
        }
    }

    private void showLoadingScreen(Runnable onFinish) {
        StackPane loadingRoot = new StackPane();

        // Background Image
        try {
            javafx.scene.image.Image bgImg = new javafx.scene.image.Image(
                    getClass().getResourceAsStream("/com/mykogroup/riskclone/assets/loading-screen.png"));
            javafx.scene.image.ImageView bgView = new javafx.scene.image.ImageView(bgImg);
            bgView.setFitWidth(1280);
            bgView.setFitHeight(720);
            loadingRoot.getChildren().add(bgView);
        } catch (Exception e) {
            loadingRoot.setStyle("-fx-background-color: #1a1a1a;");
            System.err.println("Could not load loading screen image: " + e.getMessage());
        }

        // Loading Bar Container
        VBox loaderBox = new VBox(15);
        loaderBox.setAlignment(Pos.BOTTOM_CENTER);
        loaderBox.setPadding(new javafx.geometry.Insets(0, 0, 100, 0));

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(600);
        progressBar.setPrefHeight(20);
        progressBar.setStyle(
                "-fx-accent: #d4af37; -fx-control-inner-background: #3d2b1f; -fx-background-color: transparent;");

        Label loadingLabel = new Label("Naghahanda para sa Digmaan...");
        if (BODY_FONT != null)
            loadingLabel.setFont(BODY_FONT);
        else
            loadingLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        loadingLabel.setTextFill(Color.web("#d4af37"));

        loaderBox.getChildren().addAll(loadingLabel, progressBar);
        loadingRoot.getChildren().add(loaderBox);

        mainScene.setRoot(loadingRoot);

        // Simulated Loading Logic
        Timeline loadingTimeline = new Timeline(
                new KeyFrame(Duration.millis(10), e -> {
                    progressBar.setProgress(progressBar.getProgress() + 0.01);
                    if (progressBar.getProgress() >= 0.4 && progressBar.getProgress() < 0.41) {
                        loadingLabel.setText("Tinitipon ang mga datu...");
                    } else if (progressBar.getProgress() >= 0.7 && progressBar.getProgress() < 0.71) {
                        loadingLabel.setText("Sumasangguni kay Babaylan...");
                    }
                }));
        loadingTimeline.setCycleCount(100);
        loadingTimeline.setOnFinished(e -> onFinish.run());
        loadingTimeline.play();
    }

    // --- Network Cleanup ---
    private void tearDownNetwork() {
        // Capture refs and null them immediately so no other code reuses them
        var client = gameClient;
        var server = gameServer;
        gameClient = null;
        gameServer = null;
        networkController = null;

        // Close sockets on a background thread — both disconnect() and stop() block briefly
        if (client != null || server != null) {
            new Thread(() -> {
                if (client != null) client.disconnect();
                if (server != null) server.stop();
            }, "network-teardown").start();
        }
    }

    // Lightweight: network teardown + back to menu, no map rebuild (use when game never started)
    private void returnToMainMenu() {
        tearDownNetwork();
        if (phaseTimer != null) phaseTimer.stop();
        showMainMenu();
    }

    // Heavy: full rebuild after a played game (masterState is dirty)
    private void resetGameToMenu() {
        tearDownNetwork();
        if (phaseTimer != null) phaseTimer.stop();
        initializeGame();
        showMainMenu();
    }

    private void initializeGame() {
        AdjacencyService adjacencyService = new AdjacencyService("/com/mykogroup/riskclone/province.json");
        aiController = new AiController(adjacencyService);
        masterState = new GameState();
        gameBoard = new InteractiveMapPane(adjacencyService, masterState);

        Map<String, String> displayNames = new HashMap<>();
        Map<String, SVGPath> mapNodes = SvgMapLoader.loadMap("/com/mykogroup/riskclone/map.svg",
                gameBoard::handleProvinceClick, displayNames);
        gameBoard.addProvinces(mapNodes.values());

        masterState.setRegions(RegionLoader.loadRegions("/com/mykogroup/riskclone/region.json"));

        // Setup provinces as neutral first
        for (String id : mapNodes.keySet()) {
            masterState.getProvinces().add(new Province(id, null, 0));
        }

        // Bind the UI to the State
        gameBoard.renderState(masterState);
    }

    // --- MODE SELECT ---
    private void showModeSelect() {
        StackPane root = new StackPane();

        // GIF Background
        try {
            javafx.scene.image.ImageView bgView = new javafx.scene.image.ImageView(MENU_BG_GIF);
            bgView.setFitWidth(1280);
            bgView.setFitHeight(720);
            root.getChildren().add(bgView);
        } catch (Exception e) {
            root.setStyle("-fx-background-color: #2c3e50;");
            System.err.println("Could not load menu GIF: " + e.getMessage());
        }

        VBox menuContent = new VBox(15);
        menuContent.setAlignment(Pos.CENTER);
        menuContent.setTranslateY(120); // Move buttons down to match image layout

        Button hotseatBtn = new Button("LOKAL PLAY");
        hotseatBtn.setStyle(PRIMARY_BTN_STYLE);
        if (HEADER_FONT != null)
            hotseatBtn.setFont(HEADER_FONT);
        hotseatBtn.setOnAction(e -> showSetupMenu());
        addHoverEffect(hotseatBtn);

        Button hostBtn = new Button("HOST GAME");
        hostBtn.setStyle(PRIMARY_BTN_STYLE);
        if (HEADER_FONT != null)
            hostBtn.setFont(HEADER_FONT);
        hostBtn.setOnAction(e -> startHostSession());
        addHoverEffect(hostBtn);

        Button joinBtn = new Button("JOIN GAME");
        joinBtn.setStyle(PRIMARY_BTN_STYLE);
        if (HEADER_FONT != null)
            joinBtn.setFont(HEADER_FONT);
        joinBtn.setOnAction(e -> showJoinDialog());
        addHoverEffect(joinBtn);

        menuContent.getChildren().addAll(hotseatBtn, hostBtn, joinBtn);
        root.getChildren().add(menuContent);

        mainScene.setRoot(root);
    }

    // --- MAP EDITOR ---
    private void showMapEditor() {
        Map<String, String> displayNames = new HashMap<>();
        Map<String, javafx.scene.shape.SVGPath> svgNodes = SvgMapLoader.loadMap(
                "/com/mykogroup/riskclone/map.svg",
                node -> {
                }, // EditorMapPane installs its own handlers
                displayNames);

        AdjacencyService svc = new AdjacencyService("/com/mykogroup/riskclone/province.json");
        List<Region> regions = RegionLoader.loadRegions("/com/mykogroup/riskclone/region.json");
        AdjacencyEditor editor = new AdjacencyEditor(new HashMap<>(svc.getAdjacencyMap()), regions);

        MapEditorScene editorScene = new MapEditorScene(svgNodes, displayNames, editor, this::returnToMainMenu);
        mainScene.setRoot(editorScene.getRoot());
    }

    // --- PRE-GAME MENU ---
    private void showSetupMenu() {
        LocalLobbyPane lobbyPane = new LocalLobbyPane(
                () -> {
                    // Start Game Logic
                    System.out.println("Lobby: Starting game...");
                    masterState.getPlayers().clear();
                    List<LobbyPlayer> lobbyPlayers = ((LocalLobbyPane) mainScene.getRoot()).getPlayers();
                    System.out.println("Lobby: Found " + lobbyPlayers.size() + " players.");
                    int index = 1;
                    for (LobbyPlayer lp : lobbyPlayers) {
                        String pId = "player" + index;
                        // Use public fields from LobbyPlayer
                        masterState.getPlayers().add(new Player(pId, lp.displayName, lp.isAi, lp.avatarPath));
                        ColorManager.setPlayerColor(pId, lp.color);
                        index++;
                    }
                    showLoadingScreen(this::launchGameView);
                },
                this::returnToMainMenu
        );

        mainScene.setRoot(lobbyPane);
    }

    // --- GAME LAUNCHER ---
    private void launchGameView() {
        System.out.println("Launcher: Building game view...");
        StackPane gameRoot = new StackPane();
        gameRoot.setStyle("-fx-background-color: #add8e6;"); // Old Ocean background
        gameRoot.getChildren().add(gameBoard);

        initUI(gameRoot);
        mainScene.setRoot(gameRoot);

        // Crucial: Sync the board with the now-populated player list
        gameBoard.renderState(masterState);
        System.out.println("Launcher: Game state rendered. Total players: " + masterState.getPlayers().size());

        startClaimingPhase();
    }

    // --- BUILDS THE UI ONCE ---
    private void initUI(StackPane root) {
        timerLabel = new Label();
        if (BODY_FONT != null)
            timerLabel.setFont(Font.font(BODY_FONT.getFamily(), FontWeight.BOLD, 24));
        else
            timerLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        timerLabel.setTextFill(Color.WHITE);
        timerLabel.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-padding: 10px; -fx-background-radius: 5px;");
        StackPane.setAlignment(timerLabel, Pos.TOP_CENTER);
        timerLabel.setTranslateY(20);

        playerTurnLabel = new Label();
        if (BODY_FONT != null)
            playerTurnLabel.setFont(Font.font(BODY_FONT.getFamily(), FontWeight.BOLD, 18));
        else
            playerTurnLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        playerTurnLabel.setTextFill(Color.WHITE);
        playerTurnLabel
                .setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-padding: 5px; -fx-background-radius: 5px;");
        StackPane.setAlignment(playerTurnLabel, Pos.TOP_LEFT);
        playerTurnLabel.setTranslateY(20);
        playerTurnLabel.setTranslateX(20);

        draftCountLabel = new Label();
        if (BODY_FONT != null)
            draftCountLabel.setFont(Font.font(BODY_FONT.getFamily(), FontWeight.BOLD, 18));
        else
            draftCountLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        draftCountLabel.setTextFill(Color.GOLD);
        draftCountLabel
                .setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-padding: 5px; -fx-background-radius: 5px;");
        StackPane.setAlignment(draftCountLabel, Pos.TOP_LEFT);
        draftCountLabel.setTranslateY(60); // Place below player name
        draftCountLabel.setTranslateX(20);

        endTurnBtn = new Button("End Turn");
        endTurnBtn.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #f59e0b; -fx-text-fill: white;");
        if (BODY_FONT != null)
            endTurnBtn.setFont(Font.font(BODY_FONT.getFamily(), FontWeight.BOLD, 16));
        StackPane.setAlignment(endTurnBtn, Pos.TOP_RIGHT);
        endTurnBtn.setTranslateY(20);
        endTurnBtn.setTranslateX(-20);

        root.getChildren().addAll(timerLabel, playerTurnLabel, draftCountLabel, endTurnBtn);

        // Set the callback so clicking the map updates the UI safely
        gameBoard.setOnDraftAction(this::updatePlayerTurnUI);
    }

    // === GAME PHASES ===
    // CLAIMING PHASE
    private void startClaimingPhase() {
        currentPhaseName = "Claiming";
        System.out.println("--- Starting Claiming Phase ---");
        masterState.setCurrentPhase(GameState.GamePhase.CLAIMING);
        masterState.resetReadyStates();
        currentPlayerIndex = 0;

        // Stop the clock (Claiming has no time limit)
        if (phaseTimer != null)
            phaseTimer.stop();
        timerLabel.setText("Claiming Phase");
        timerLabel.setTextFill(Color.GOLD);

        // Repurpose the draft label to give instructions
        draftCountLabel.setText("Select 1 starting province");
        draftCountLabel.setVisible(true);

        gameBoard.setInteractionLocked(false);
        endTurnBtn.setDisable(false);

        updatePlayerTurnUI();

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
            advanceTurn(() -> startDraftingPhase());
        });
    }

    // DRAFTING PHASE
    private void startDraftingPhase() {
        masterState.setCurrentPhase(GameState.GamePhase.DRAFTING);
        masterState.initDraftPools(5); // Give everyone 5 troops
        masterState.resetReadyStates();

        currentPlayerIndex = 0;
        currentPhaseName = "Drafting";
        phaseTurnDuration = 20;
        timeRemaining = 20;
        gameBoard.setInteractionLocked(false);
        endTurnBtn.setDisable(false);

        draftCountLabel.setVisible(true);
        draftCountLabel.setTextFill(Color.GOLD);

        updatePlayerTurnUI();

        // Setup End Turn button for Drafting
        endTurnBtn.setOnAction(
                e -> advanceTurn(() -> startPlanningPhase()));

        triggerSimultaneousAI();
        
        // Find first human to set turn focus
        currentPlayerIndex = -1;
        advanceTurn(() -> startPlanningPhase()); // This will skip AIs and land on first human
        
        startTimer("Drafting", () -> startPlanningPhase());
    }

    // PLANNING PHASE
    private void startPlanningPhase() {
        masterState.setCurrentPhase(GameState.GamePhase.PLANNING);
        masterState.resetReadyStates();

        currentPlayerIndex = 0;
        currentPhaseName = "Planning";
        phaseTurnDuration = 60;
        timeRemaining = 60;
        draftCountLabel.setVisible(false); // Hide the draft tracker

        updatePlayerTurnUI();

        // Setup End Turn button for Planning
        endTurnBtn.setOnAction(
                e -> advanceTurn(() -> triggerResolutionPhase()));

        triggerSimultaneousAI();
        
        // Find first human to set turn focus
        currentPlayerIndex = -1;
        advanceTurn(() -> triggerResolutionPhase());

        startTimer("Planning", () -> triggerResolutionPhase());
    }

    // RESOLUTION PHASE
    private void triggerResolutionPhase() {
        if (phaseTimer != null)
            phaseTimer.stop();

        masterState.setCurrentPhase(GameState.GamePhase.RESOLUTION);
        timerLabel.setText("RESOLUTION PHASE");
        timerLabel.setTextFill(Color.GOLD);
        endTurnBtn.setDisable(true);
        gameBoard.setInteractionLocked(true);

        System.out.println("Processing Combat...");

        // Snapshot pre-resolution state so we can animate forward from it.
        Map<String, String> preOwners = new HashMap<>();
        Map<String, Integer> preArmies = new HashMap<>();
        for (Province p : masterState.getProvinces()) {
            preOwners.put(p.getId(), p.getOwnerId());
            preArmies.put(p.getId(), p.getArmyCount());
        }
        List<Move> preMoves = new ArrayList<>(masterState.getQueuedMoves());

        // Save the user's current camera so we can restore it after resolution.
        double preCamScale = gameBoard.getScaleX();
        double preCamTX = gameBoard.getTranslateX();
        double preCamTY = gameBoard.getTranslateY();

        List<ResolutionResult> results = new ResolutionEngine().processTurn(masterState);

        // Roll the board back to pre-resolution visuals. Planning arrows stay on screen.
        gameBoard.renderSnapshotState(preOwners, preArmies, masterState);

        // Animate all source-province departures first so the arrows look like they actually marched off.
        Map<String, Integer> totalDepartures = new HashMap<>();
        for (Move m : preMoves) {
            totalDepartures.merge(m.fromId(), m.armies(), Integer::sum);
        }
        for (var entry : totalDepartures.entrySet()) {
            int oldC = preArmies.get(entry.getKey());
            int newC = oldC - entry.getValue();
            preArmies.put(entry.getKey(), newC);
            gameBoard.tweenArmyCount(entry.getKey(), oldC, newC, Duration.millis(400));
        }

        PauseTransition departurePause = new PauseTransition(Duration.millis(500));
        departurePause.setOnFinished(e -> animateResolution(results, preOwners, preArmies, preMoves, () -> {
            gameBoard.clearArrows();
            gameBoard.animateCameraTo(preCamScale, preCamTX, preCamTY, Duration.millis(500));
            PauseTransition restorePause = new PauseTransition(Duration.millis(520));
            restorePause.setOnFinished(ev -> {
                gameBoard.renderState(masterState);
                checkVictory();
            });
            restorePause.play();
        }));
        departurePause.play();
    }

    private void checkVictory() {
        List<Player> survivors = masterState.getAlivePlayers();
        if (survivors.size() == 1) {
            showGameOverScreen(survivors.getFirst());
        } else if (survivors.isEmpty()) {
            showGameOverScreen(null);
        } else {
            new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
                startDraftingPhase();
            })).play();
        }
    }

    private void animateResolution(List<ResolutionResult> results,
                                   Map<String, String> preOwners,
                                   Map<String, Integer> preArmies,
                                   List<Move> preMoves,
                                   Runnable onComplete) {
        if (results.isEmpty()) {
            onComplete.run();
            return;
        }

        // Sort results by player ID of the first involved player (to satisfy "ordered by player")
        results.sort(Comparator.comparing(r -> r.involvedPlayerIds().isEmpty() ? "" : r.involvedPlayerIds().get(0)));

        ResolutionResult res = results.remove(0);
        String destId = res.provinceId();

        List<String> inboundSources = new ArrayList<>();
        for (Move m : preMoves) {
            if (m.toId().equals(destId)) inboundSources.add(m.fromId());
        }

        gameBoard.zoomToProvince(destId, 2.5, Duration.millis(350));
        gameBoard.pulseArrows(inboundSources, destId, Duration.millis(400));
        gameBoard.flashProvince(destId, Color.YELLOW, Duration.millis(500));

        String oldOwner = preOwners.get(destId);
        String newOwner = res.ownerId();
        if (!Objects.equals(oldOwner, newOwner)) {
            gameBoard.setProvinceOwnerColor(destId, newOwner);
            preOwners.put(destId, newOwner);
        }

        int oldCount = preArmies.getOrDefault(destId, 0);
        preArmies.put(destId, res.armyCount());
        gameBoard.tweenArmyCount(destId, oldCount, res.armyCount(), Duration.millis(500));

        draftCountLabel.setVisible(true);
        draftCountLabel.setText(res.description());
        draftCountLabel.setTextFill(Color.CYAN);

        PauseTransition pause = new PauseTransition(Duration.seconds(1.0));
        pause.setOnFinished(e -> animateResolution(results, preOwners, preArmies, preMoves, onComplete));
        pause.play();
    }

    private void triggerSimultaneousAI() {
        for (Player p : masterState.getPlayers()) {
            if (p.isAi() && masterState.isPlayerAlive(p.getId())) {
                if (masterState.getCurrentPhase() == GameState.GamePhase.DRAFTING) {
                    // Start AI drafting in background
                    playAiDraftAnimation(masterState, gameBoard, p);
                } else if (masterState.getCurrentPhase() == GameState.GamePhase.PLANNING) {
                    // AI planning is instant
                    aiController.takePlanningTurn(masterState, p.getId());
                    gameBoard.renderState(masterState);
                }
            }
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
        playAgainBtn.setStyle(
                "-fx-font-size: 24px; -fx-font-weight: bold; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 15 40;");

        // Triggers the factory reset!
        playAgainBtn.setOnAction(e -> resetGameToMenu());

        gameOverOverlay.getChildren().addAll(title, subTitle, playAgainBtn);

        // Assuming mainScene.getRoot() is currently your gameRoot (StackPane)
        if (mainScene.getRoot() instanceof StackPane root) {
            root.getChildren().add(gameOverOverlay);
        }
    }

    // --- HELPER METHODS ---
    private void advanceTurn(Runnable onPhaseEnd) {
        currentPlayerIndex++;
        
        // Skip dead players OR AI players (in simultaneous phases)
        boolean skipAI = (masterState.getCurrentPhase() == GameState.GamePhase.DRAFTING || 
                          masterState.getCurrentPhase() == GameState.GamePhase.PLANNING);

        while (currentPlayerIndex < masterState.getPlayers().size()) {
            Player p = masterState.getPlayers().get(currentPlayerIndex);
            boolean isDead = (masterState.getCurrentPhase() != GameState.GamePhase.CLAIMING && !masterState.isPlayerAlive(p.getId()));
            boolean isAiToSkip = (skipAI && p.isAi());
            
            if (isDead || isAiToSkip) {
                currentPlayerIndex++;
            } else {
                break;
            }
        }

        if (currentPlayerIndex >= masterState.getPlayers().size()) {
            System.out.println("Phase End reached. Advancing phase...");
            onPhaseEnd.run();
        } else {
            updatePlayerTurnUI();
            
            // Restart timer for Drafting/Planning if applicable
            if (currentPhaseName != null && !currentPhaseName.equals("Claiming")) {
                timeRemaining = phaseTurnDuration;
                startTimer(currentPhaseName, onPhaseEnd);
            }
        }
    }

    private void updatePlayerTurnUI() {
        if (masterState.getPlayers().isEmpty())
            return;
        Player p = masterState.getPlayers().get(currentPlayerIndex);
        playerTurnLabel.setText("Turn: " + p.getDisplayName());
        playerTurnLabel.setTextFill(javafx.scene.paint.Color.web(ColorManager.getColorForPlayer(p.getId())));
        gameBoard.setCurrentLocalPlayerId(p.getId());

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

                    // Wait another 0.5s so the player can actually see the province change color,
                    // then end turn
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

                    // Instantly render the map so the UI can draw the arrows for the AI's queued
                    // moves!
                    gameBoard.renderState(masterState);

                    // Add a small pause so players can briefly see the AI's arrows before the turn
                    // passes
                    PauseTransition endPause = new PauseTransition(Duration.seconds(1.0));
                    endPause.setOnFinished(ev -> {
                        endTurnBtn.setDisable(false);
                        endTurnBtn.fire();
                    });
                    endPause.play();
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
        if (phaseTimer != null)
            phaseTimer.stop();
        timerLabel.setTextFill(Color.WHITE);

        phaseTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            timeRemaining--;
            timerLabel.setText(phaseName + " Phase: " + timeRemaining + "s");
            if (timeRemaining <= 5)
                timerLabel.setTextFill(Color.RED);
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
        if (BODY_FONT != null)
            pLabel.setFont(Font.font(BODY_FONT.getFamily(), 18));
        else
            pLabel.setFont(Font.font(18));
        pLabel.setTextFill(Color.WHITE);

        TextField nameInput = new TextField(); // Text set by refreshPlayerRows()
        if (BODY_FONT != null)
            nameInput.setFont(Font.font(BODY_FONT.getFamily(), 16));
        else
            nameInput.setFont(Font.font(16));

        Button aiToggleBtn = new Button("👤 Human");
        aiToggleBtn.setStyle(
                "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-pref-width: 100px;");
        if (BODY_FONT != null)
            aiToggleBtn.setFont(Font.font(BODY_FONT.getFamily(), FontWeight.BOLD, 14));

        aiToggleBtn.setOnAction(e -> {
            if (aiToggleBtn.getText().contains("Human")) {
                // Switch TO AI
                aiToggleBtn.setText("🤖 AI");
                aiToggleBtn.setStyle(
                        "-fx-background-color: #8b5cf6; -fx-text-fill: white; -fx-font-weight: bold; -fx-pref-width: 100px;");
                nameInput.setDisable(true); // Lock the text field
                nameInput.setText(AI_NAMES[rand.nextInt(AI_NAMES.length)]); // Assign historical name
            } else {
                // Switch TO Human
                aiToggleBtn.setText("👤 Human");
                aiToggleBtn.setStyle(
                        "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-pref-width: 100px;");
                nameInput.setDisable(false); // Unlock the text field
                nameInput.setText(""); // Clear it so refreshPlayerRows can reset it
                refreshPlayerRows();
            }
        });

        // Grab a default color based on their index, looping back to the start if we
        // exceed 8
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
                return;
            }

            // Figure out how many to place this tick
            int toPlace = Math.min(batchSize, remaining);

            // Re-evaluate the best target every tick! (Because threat levels change as we
            // add troops)
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

    // --- NETWORK METHODS ---

    private void startHostSession() {
        if (gameClient != null)
            return; // already connecting

        // Server startup + DNS lookup both can block — keep off FX thread
        new Thread(() -> {
            try {
                com.mykogroup.riskclone.network.GameServer server =
                        new com.mykogroup.riskclone.network.GameServer(5050);
                server.start();
                int port = server.getPort();

                String ip;
                try {
                    ip = java.net.InetAddress.getLocalHost().getHostAddress();
                } catch (Exception ex) {
                    ip = "127.0.0.1";
                }
                final String finalIp = ip;
                final int finalPort = port;

                javafx.application.Platform.runLater(() -> {
                    gameServer = server;
                    networkController = new com.mykogroup.riskclone.engine.NetworkGameController();
                    com.mykogroup.riskclone.view.NetworkLobbyPane lobbyPane =
                            new com.mykogroup.riskclone.view.NetworkLobbyPane(
                                    true, finalIp, finalPort,
                                    () -> showLoadingScreen(this::launchNetworkGameView),
                                    this::returnToMainMenu);

                    gameClient = new com.mykogroup.riskclone.network.GameClient(
                            new CompositeListener(lobbyPane, networkController));
                    lobbyPane.setClient(gameClient);
                    networkController.setClient(gameClient);
                    mainScene.setRoot(lobbyPane);

                    // Connect host client on a background thread
                    long startTime = System.currentTimeMillis();
                    new Thread(() -> {
                        try {
                            gameClient.connect("localhost", finalPort);
                            gameClient.send(new com.mykogroup.riskclone.network.NetworkMessage(
                                    com.mykogroup.riskclone.network.MessageType.JOIN, null,
                                    mapper.valueToTree(
                                            new com.mykogroup.riskclone.network.payload.JoinPayload(
                                                    "Host", DEFAULT_COLORS[0]))));
                        } catch (Exception ex) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            if (elapsed < 5000) {
                                try { Thread.sleep(5000 - elapsed); } catch (InterruptedException ignored) {}
                            }
                            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                            javafx.application.Platform.runLater(this::returnToMainMenu);
                            ex.printStackTrace();
                        }
                    }, "host-connect").start();
                });

            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    tearDownNetwork();
                    showMainMenu();
                });
                ex.printStackTrace();
            }
        }, "host-setup").start();
    }

    private void showJoinDialog() {
        VBox card = new VBox(25);
        card.setPadding(new Insets(40));
        card.setStyle("-fx-background-color: #3d2b1f; -fx-border-color: #d4af37; -fx-border-width: 4; -fx-background-radius: 20; -fx-border-radius: 20;");
        card.setAlignment(Pos.CENTER);
        card.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);

        Label title = new Label("SALI SA LARO");
        if (HEADER_FONT != null) title.setFont(HEADER_FONT);
        else title.setFont(Font.font("System", FontWeight.BOLD, 32));
        title.setTextFill(Color.web("#d4af37"));

        TextField codeField = new TextField();
        codeField.setPromptText("ILAGAY ANG KODA");
        codeField.setStyle("-fx-font-size: 20px; -fx-background-color: #2c1e14; -fx-text-fill: white; -fx-border-color: #5d4037; -fx-padding: 10;");
        if (BODY_FONT != null) codeField.setFont(BODY_FONT);

        Label errorLabel = new Label("");
        errorLabel.setTextFill(Color.web("#ef4444"));
        if (BODY_FONT != null) errorLabel.setFont(BODY_FONT);

        Button joinBtn = new Button("SALI");
        joinBtn.setStyle(primaryBtnStyle(200, 55));
        joinBtn.setFont(headerFont(22));

        Button cancelBtn = new Button("KANSELAHIN");
        cancelBtn.setStyle(primaryBtnStyle(200, 55));
        cancelBtn.setFont(headerFont(22));

        HBox actions = new HBox(20, cancelBtn, joinBtn);
        actions.setAlignment(Pos.CENTER);

        card.getChildren().addAll(title, codeField, errorLabel, actions);

        Overlay overlay = showOverlay(card);

        joinBtn.setOnAction(e -> {
            String code = codeField.getText().trim().toUpperCase();
            if (code.length() != 6) {
                errorLabel.setText("Maling koda! Dapat ay 6 na karakter.");
                return;
            }
            try {
                com.mykogroup.riskclone.network.LobbyCodeConverter.Address addr = com.mykogroup.riskclone.network.LobbyCodeConverter.decode(code);
                errorLabel.setText("Kumokonekta...");
                startJoinSession(addr.ip, addr.port, "Player", errorLabel, overlay::close);
            } catch (Exception ex) {
                errorLabel.setText("Maling code!");
            }
        });
        addHoverEffect(joinBtn);

        cancelBtn.setOnAction(e -> overlay.close());
        addHoverEffect(cancelBtn);
    }

    private void startJoinSession(String host, int port, String playerName,
            Label statusLabel, Runnable onSuccess) {
        if (gameClient != null)
            return; // already connecting

        networkController = new com.mykogroup.riskclone.engine.NetworkGameController();
        com.mykogroup.riskclone.view.NetworkLobbyPane lobbyPane = new com.mykogroup.riskclone.view.NetworkLobbyPane(
                false, host, port,
                () -> showLoadingScreen(this::launchNetworkGameView),
                this::showJoinDialog); // Return to code entry

        gameClient = new com.mykogroup.riskclone.network.GameClient(
                new CompositeListener(lobbyPane, networkController));
        lobbyPane.setClient(gameClient);
        networkController.setClient(gameClient);

        // Connect and send JOIN on a background thread to avoid blocking the FX thread
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                gameClient.connect(host, port);
                gameClient.send(new com.mykogroup.riskclone.network.NetworkMessage(
                        com.mykogroup.riskclone.network.MessageType.JOIN, null,
                        mapper.valueToTree(
                                new com.mykogroup.riskclone.network.payload.JoinPayload(
                                        playerName, "#3b82f6"))));
                
                javafx.application.Platform.runLater(() -> {
                    mainScene.setRoot(lobbyPane);
                    if (onSuccess != null) onSuccess.run();
                });
            } catch (Exception ex) {
                // Ensure at least 5 seconds have passed before showing error (as requested)
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < 5000) {
                    try { Thread.sleep(5000 - elapsed); } catch (InterruptedException ignored) {}
                }
                
                javafx.application.Platform.runLater(() -> {
                    if (statusLabel != null) statusLabel.setText("Hindi maka-connect!");
                });

                // Show the error for 1 second before closing
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

                javafx.application.Platform.runLater(() -> {
                    if (statusLabel != null && statusLabel.getScene() != null && statusLabel.getScene().getWindow() != null) {
                        ((javafx.stage.Stage)statusLabel.getScene().getWindow()).close();
                    }
                    returnToMainMenu(); // Go back to main menu (tearDownNetwork is inside returnToMainMenu)
                    System.err.println("Connection failed after 5s feedback + 1s error, returning to menu.");
                });
            }
        }, "join-connect").start();
    }

    // --- CompositeListener: fans out GameClientListener callbacks to both lobby
    // and controller ---
    private static class CompositeListener
            implements com.mykogroup.riskclone.network.GameClientListener {

        private final com.mykogroup.riskclone.network.GameClientListener lobby;
        private final com.mykogroup.riskclone.network.GameClientListener controller;

        CompositeListener(com.mykogroup.riskclone.network.GameClientListener lobby,
                com.mykogroup.riskclone.network.GameClientListener controller) {
            this.lobby = lobby;
            this.controller = controller;
        }

        @Override
        public void onJoinAck(String pid) {
            lobby.onJoinAck(pid);
            controller.onJoinAck(pid);
        }

        @Override
        public void onLobbyUpdate(com.mykogroup.riskclone.network.payload.LobbyUpdatePayload p) {
            lobby.onLobbyUpdate(p);
            controller.onLobbyUpdate(p);
        }

        @Override
        public void onGameStart(com.mykogroup.riskclone.network.payload.GameStartPayload p) {
            lobby.onGameStart(p);
            controller.onGameStart(p);
        }

        @Override
        public void onStateUpdate(com.mykogroup.riskclone.network.payload.StateUpdatePayload p) {
            lobby.onStateUpdate(p);
            controller.onStateUpdate(p);
        }

        @Override
        public void onGameOver(com.mykogroup.riskclone.network.payload.GameOverPayload p) {
            lobby.onGameOver(p);
            controller.onGameOver(p);
        }

        @Override
        public void onPlayerDisconnected(String pid) {
            lobby.onPlayerDisconnected(pid);
            controller.onPlayerDisconnected(pid);
        }

        @Override
        public void onError(String m) {
            lobby.onError(m);
            controller.onError(m);
        }

        @Override
        public void onDisconnected() {
            lobby.onDisconnected();
            controller.onDisconnected();
        }

        @Override
        public void onTimerUpdate(String phase, int secondsRemaining) {
            lobby.onTimerUpdate(phase, secondsRemaining);
            controller.onTimerUpdate(phase, secondsRemaining);
        }
    }

    // Transitions to the final game view
    private void launchNetworkGameView() {
        if (networkController == null)
            return; // session torn down before game started
        StackPane gameRoot = new StackPane();
        gameRoot.setStyle("-fx-background-color: #0a1628;");
        // gameBoard is accessible from parent scope context
        gameRoot.getChildren().add(gameBoard);

        // Build HUD labels + button
        Label timerLbl = new Label("Waiting for game start…");
        timerLbl.setFont(Font.font("System", FontWeight.BOLD, 20));
        timerLbl.setTextFill(Color.WHITE);
        timerLbl.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-padding: 8px; -fx-background-radius: 5px;");
        StackPane.setAlignment(timerLbl, Pos.TOP_CENTER);
        timerLbl.setTranslateY(20);

        Label playerLbl = new Label();
        playerLbl.setFont(Font.font("System", FontWeight.BOLD, 16));
        playerLbl.setTextFill(Color.WHITE);
        playerLbl.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-padding: 5px; -fx-background-radius: 5px;");
        StackPane.setAlignment(playerLbl, Pos.TOP_LEFT);
        playerLbl.setTranslateY(20);
        playerLbl.setTranslateX(20);

        Label draftLbl = new Label();
        draftLbl.setFont(Font.font("System", FontWeight.BOLD, 16));
        draftLbl.setTextFill(Color.GOLD);
        draftLbl.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-padding: 5px; -fx-background-radius: 5px;");
        draftLbl.setVisible(false);
        StackPane.setAlignment(draftLbl, Pos.TOP_LEFT);
        draftLbl.setTranslateY(55);
        draftLbl.setTranslateX(20);

        Button finishedBtn = new Button("Finished Turn");
        finishedBtn.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #f59e0b; -fx-text-fill: white;");
        StackPane.setAlignment(finishedBtn, Pos.TOP_RIGHT);
        finishedBtn.setTranslateY(20);
        finishedBtn.setTranslateX(-20);

        gameRoot.getChildren().addAll(timerLbl, playerLbl, draftLbl, finishedBtn);
        mainScene.setRoot(gameRoot);

        // Wire controller to UI
        networkController.attachUI(gameBoard, timerLbl, playerLbl, draftLbl, finishedBtn);
        networkController.setOnGameOverCallback(winnerId -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Game Over");
            alert.setHeaderText(winnerId == null ? "Total Annihilation! No survivors."
                    : "Player " + winnerId + " wins!");
            alert.setOnHidden(e -> resetGameToMenu());
            alert.show();
        });
    }

}
