package com.mykogroup.riskclone.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykogroup.riskclone.network.GameClient;
import com.mykogroup.riskclone.network.GameClientListener;
import com.mykogroup.riskclone.network.LobbyPlayer;
import com.mykogroup.riskclone.network.MessageType;
import com.mykogroup.riskclone.network.NetworkMessage;
import com.mykogroup.riskclone.network.payload.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

// JavaFX lobby pane used for both hosting and joining.
// Layout: Title | connection info | scrollable player list | bottom bar (Add AI, Start, Leave)
// Implements GameClientListener so it can react to server events via Platform.runLater.
public class NetworkLobbyPane extends VBox implements GameClientListener {

    // --- State ---
    private GameClient client;   // set after construction via setClient()
    private final boolean isHost;
    private String localPlayerId;
    private final Runnable onGameStart;  // called when GAME_START arrives
    private final Runnable onLeave;      // called when Leave is clicked

    private final Label connectionInfoLabel = new Label("Connecting…");
    private final VBox playerListBox        = new VBox(10);
    private final Button startGameBtn       = new Button("Start Game");
    private final Button addAiBtn           = new Button("+ Add AI");
    private final ObjectMapper mapper       = new ObjectMapper();

    // isHost: true if this process is also running GameServer
    // serverIp: LAN IP to display (host shows it; client shows the address they joined)
    public NetworkLobbyPane(boolean isHost, String serverIp, int port,
                             Runnable onGameStart, Runnable onLeave) {
        this.isHost      = isHost;
        this.onGameStart = onGameStart;
        this.onLeave     = onLeave;

        setSpacing(20);
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: #1e2235; -fx-padding: 40;");

        // Title
        Label title = new Label("LAN Multiplayer");
        title.setFont(Font.font("System", FontWeight.BOLD, 36));
        title.setTextFill(Color.WHITE);

        // Connection info
        if (isHost) {
            connectionInfoLabel.setText("Your IP: " + serverIp + "   Port: " + port
                    + "   (Share this with other players)");
        } else {
            connectionInfoLabel.setText("Connected to " + serverIp + ":" + port);
        }
        connectionInfoLabel.setTextFill(Color.web("#94a3b8"));
        connectionInfoLabel.setFont(Font.font(14));

        // Player list scroll area
        ScrollPane scroll = new ScrollPane(playerListBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(280);
        scroll.setStyle("-fx-background: #13161f; -fx-background-color: #13161f;");
        playerListBox.setStyle("-fx-padding: 10;");

        // Bottom bar
        addAiBtn.setStyle("-fx-font-size: 13px; -fx-background-color: #4a5568; -fx-text-fill: white; -fx-padding: 6 16;");
        addAiBtn.setVisible(isHost);
        addAiBtn.setManaged(isHost);
        addAiBtn.setOnAction(e -> sendAsync(build(MessageType.ADD_AI, null)));

        startGameBtn.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 8 24;");
        startGameBtn.setVisible(isHost);
        startGameBtn.setManaged(isHost);
        startGameBtn.setDisable(true); // enabled once >= 4 players
        startGameBtn.setOnAction(e -> {
            startGameBtn.setDisable(true); // prevent double-click while server responds
            sendAsync(build(MessageType.START_GAME, null));
        });

        Button leaveBtn = new Button("Leave");
        leaveBtn.setStyle("-fx-font-size: 13px; -fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 6 16;");
        leaveBtn.setOnAction(e -> { if (client != null) client.disconnect(); if (onLeave != null) onLeave.run(); });

        HBox bottomBar = new HBox(12, addAiBtn, startGameBtn, leaveBtn);
        bottomBar.setAlignment(Pos.CENTER);

        getChildren().addAll(title, connectionInfoLabel, scroll, bottomBar);
    }

    // Set after construction to break the circular dependency with GameClient
    public void setClient(GameClient client) { this.client = client; }

    public void setLocalPlayerId(String pid) { this.localPlayerId = pid; }

    // --- GameClientListener ---

    @Override public void onJoinAck(String assignedPlayerId) {
        this.localPlayerId = assignedPlayerId;
    }

    @Override public void onLobbyUpdate(LobbyUpdatePayload payload) {
        System.out.println("[lobby] onLobbyUpdate: " + (payload.players == null ? "null" : payload.players.size()) + " players");
        Platform.runLater(() -> {
            try {
                refreshPlayerList(payload.players);
            } catch (Exception e) {
                e.printStackTrace();
                connectionInfoLabel.setText("UI error: " + e.getMessage());
            }
        });
    }

    @Override public void onGameStart(GameStartPayload payload) {
        System.out.println("[lobby] onGameStart received");
        Platform.runLater(() -> {
            try {
                if (onGameStart != null) onGameStart.run();
            } catch (Exception e) {
                e.printStackTrace();
                connectionInfoLabel.setText("Start error: " + e.getMessage());
            }
        });
    }

    @Override public void onStateUpdate(StateUpdatePayload p) {}
    @Override public void onGameOver(GameOverPayload p) {}
    @Override public void onPlayerDisconnected(String pid) {}
    @Override public void onError(String message) {
        Platform.runLater(() -> connectionInfoLabel.setText("Error: " + message));
    }
    @Override public void onDisconnected() {
        Platform.runLater(() -> connectionInfoLabel.setText("Disconnected from server"));
    }

    // --- private ---

    private void refreshPlayerList(List<LobbyPlayer> players) {
        playerListBox.getChildren().clear();
        for (LobbyPlayer lp : players) {
            playerListBox.getChildren().add(buildPlayerRow(lp));
        }
        // Enable Start only when >= 4 players (host only; server enforces this too)
        startGameBtn.setDisable(players.size() < 4);
    }

    private HBox buildPlayerRow(LobbyPlayer lp) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #2a2d3e; -fx-padding: 8 12; -fx-background-radius: 6;");

        // Colour dot
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(7);
        dot.setFill(Color.web(lp.color != null ? lp.color : "#888888"));

        // Own row gets editable name + colour picker; others are read-only
        boolean isOwnRow = lp.playerId.equals(localPlayerId);
        if (isOwnRow && !lp.isAi) {
            TextField nameField = new TextField(lp.displayName);
            nameField.setStyle("-fx-font-size: 14px;");
            nameField.setOnAction(e -> sendAsync(
                    build(MessageType.UPDATE_NAME, new UpdateNamePayload(nameField.getText()))));
            nameField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused)
                    sendAsync(build(MessageType.UPDATE_NAME, new UpdateNamePayload(nameField.getText())));
            });

            ColorPicker cp = new ColorPicker(Color.web(lp.color != null ? lp.color : "#ef4444"));
            cp.setStyle("-fx-font-size: 12px;");
            cp.setOnAction(e -> {
                String hex = toHex(cp.getValue());
                dot.setFill(Color.web(hex));
                sendAsync(build(MessageType.UPDATE_COLOR, new UpdateColorPayload(hex)));
            });

            row.getChildren().addAll(dot, nameField, cp);
        } else {
            Label nameLabel = new Label(lp.displayName + (lp.isAi ? "  🤖" : ""));
            nameLabel.setTextFill(Color.web(isOwnRow ? "#fbbf24" : "#e2e8f0"));
            nameLabel.setFont(Font.font(14));
            row.getChildren().addAll(dot, nameLabel);
        }
        return row;
    }

    // Build a message on the calling thread, then send it on a daemon thread
    // so the FX thread is never blocked by socket I/O.
    private void sendAsync(NetworkMessage msg) {
        if (client == null) return;
        Thread t = new Thread(() -> client.send(msg), "lobby-send");
        t.setDaemon(true);
        t.start();
    }

    private NetworkMessage build(String type, Object payload) {
        com.fasterxml.jackson.databind.node.ObjectNode node = (payload == null)
                ? mapper.createObjectNode()
                : mapper.valueToTree(payload);
        return new NetworkMessage(type, localPlayerId, node);
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }
}
