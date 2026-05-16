package com.mykogroup.riskclone.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykogroup.riskclone.Main;
import com.mykogroup.riskclone.network.GameClient;
import com.mykogroup.riskclone.network.GameClientListener;
import com.mykogroup.riskclone.model.LobbyPlayer;
import com.mykogroup.riskclone.network.MessageType;
import com.mykogroup.riskclone.network.NetworkMessage;
import com.mykogroup.riskclone.network.LobbyCodeConverter;
import com.mykogroup.riskclone.network.payload.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;

public class NetworkLobbyPane extends StackPane implements GameClientListener {

    private GameClient client;
    private final boolean isHost;
    private final String serverIp;
    private final int port;
    private String localPlayerId;
    private final Runnable onGameStart;
    private final Runnable onLeave;
    private final ObjectMapper mapper = new ObjectMapper();

    private final GridPane playerGrid = new GridPane();
    private final Label lobbyCodeLabel = new Label("CODE: ------");
    private final Button startGameBtn = new Button("");
    private final Button addAiBtn = new Button("");

    private boolean detailsPrompted = false;

    public NetworkLobbyPane(boolean isHost, String serverIp, int port,
            Runnable onGameStart, Runnable onLeave) {
        this.isHost = isHost;
        this.serverIp = serverIp;
        this.port = port;
        this.onGameStart = onGameStart;
        this.onLeave = onLeave;

        // Background
        try {
            ImageView bgView = new ImageView(
                    new Image(getClass().getResourceAsStream("/com/mykogroup/riskclone/assets/socket-lobby-bg.png")));
            bgView.setFitWidth(1280);
            bgView.setFitHeight(720);
            getChildren().add(bgView);
        } catch (Exception e) {
            setStyle("-fx-background-color: #3d2b1f;");
        }

        VBox content = new VBox(40);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(120, 0, 0, 0));

        // Player Grid
        playerGrid.setHgap(30);
        playerGrid.setVgap(20);
        playerGrid.setAlignment(Pos.CENTER);

        content.getChildren().addAll(playerGrid);
        getChildren().add(content);

        // Lobby Code (Bottom section)
        String code = LobbyCodeConverter.encode(serverIp, port);
        lobbyCodeLabel.setText(code);
        lobbyCodeLabel.setTextFill(Color.WHITE);
        if (Main.BODY_FONT != null)
            lobbyCodeLabel.setFont(Font.font(Main.BODY_FONT.getFamily(), FontWeight.BLACK, 34));
        else
            lobbyCodeLabel.setFont(Font.font("System", FontWeight.BOLD, 34));

        StackPane.setAlignment(lobbyCodeLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(lobbyCodeLabel, new Insets(0, 0, 33, 140)); // Move slightly lower and right
        getChildren().add(lobbyCodeLabel);

        // Buttons
        if (isHost) {
            // Add AI Button (Bottom Left)
            try {
                ImageView aiIv = new ImageView(
                        new Image(getClass().getResourceAsStream("/com/mykogroup/riskclone/assets/add-bot-btn.png")));
                aiIv.setFitWidth(180);
                aiIv.setFitHeight(60);
                addAiBtn.setGraphic(aiIv);
                addAiBtn.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            } catch (Exception e) {
                addAiBtn.setStyle(
                        "-fx-background-color: #4a5568; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30;");
            }
            addAiBtn.setOnAction(e -> sendAsync(build(MessageType.ADD_AI, null)));
            Main.addHoverEffect(addAiBtn);
            StackPane.setAlignment(addAiBtn, Pos.BOTTOM_LEFT);
            StackPane.setMargin(addAiBtn, new Insets(0, 0, 40, 50));
            getChildren().add(addAiBtn);

            // Play Button (Center Right)
            try {
                ImageView playIv = new ImageView(
                        new Image(getClass().getResourceAsStream("/com/mykogroup/riskclone/assets/play-btn.png")));
                playIv.setFitWidth(100);
                playIv.setFitHeight(150);
                startGameBtn.setGraphic(playIv);
                startGameBtn.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            } catch (Exception e) {
                startGameBtn.setStyle(
                        "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30;");
            }
            startGameBtn.setDisable(true);
            startGameBtn.setOnAction(e -> sendAsync(build(MessageType.START_GAME, null)));
            Main.addHoverEffect(startGameBtn);
            StackPane.setAlignment(startGameBtn, Pos.CENTER_RIGHT);
            StackPane.setMargin(startGameBtn, new Insets(0, 0, 0, 0));
            getChildren().add(startGameBtn);
        }

        // Back Button
        Button backBtn = new Button("");
        try {
            ImageView iv = new ImageView(
                    new Image(getClass().getResourceAsStream("/com/mykogroup/riskclone/assets/main-menu-btn.png")));
            iv.setFitWidth(160);
            iv.setFitHeight(45);

            StackPane btnContent = new StackPane();
            Label lbl = new Label("BUMALIK");
            lbl.setTextFill(Color.WHITE);
            if (Main.HEADER_FONT != null)
                lbl.setFont(Font.font(Main.HEADER_FONT.getFamily(), 20));
            else
                lbl.setFont(Font.font("System", FontWeight.BOLD, 20));

            btnContent.getChildren().addAll(iv, lbl);
            backBtn.setGraphic(btnContent);
            backBtn.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        } catch (Exception e) {
            backBtn.setStyle(
                    "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        }
        backBtn.setOnAction(e -> {
            if (client != null)
                client.disconnect();
            onLeave.run();
        });
        StackPane.setAlignment(backBtn, Pos.TOP_LEFT);
        StackPane.setMargin(backBtn, new Insets(45, 0, 0, 45));
        Main.addHoverEffect(backBtn);
        getChildren().add(backBtn);
    }

    public void setClient(GameClient client) {
        this.client = client;
    }

    public void setLocalPlayerId(String pid) {
        this.localPlayerId = pid;
    }

    private void showDetailsModal() {
        detailsPrompted = true;
        final Main.Overlay[] overlayRef = new Main.Overlay[1];

        VBox root = new VBox(25);
        root.setPadding(new Insets(35));
        root.setStyle(
                "-fx-background-color: #3d2b1f; -fx-border-color: #d4af37; -fx-border-width: 4; -fx-background-radius: 20; -fx-border-radius: 20;");
        root.setAlignment(Pos.CENTER);
        root.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);

        Label title = new Label("SINO KA?");
        if (Main.HEADER_FONT != null)
            title.setFont(Main.HEADER_FONT);
        else
            title.setFont(Font.font("System", FontWeight.BOLD, 28));
        title.setTextFill(Color.web("#d4af37"));

        TextField nameField = new TextField("Datu " + (int) (Math.random() * 100));
        nameField.setPromptText("Ilagay ang Pangalan");
        nameField.setStyle(
                "-fx-font-size: 18px; -fx-background-color: #2c1e14; -fx-text-fill: white; -fx-border-color: #5d4037;");
        if (Main.BODY_FONT != null)
            nameField.setFont(Main.BODY_FONT);

        // Avatar Picker
        Label avatarLabel = new Label("Pumili ng Avatar:");
        avatarLabel.setTextFill(Color.WHITE);
        if (Main.BODY_FONT != null)
            avatarLabel.setFont(Main.BODY_FONT);

        HBox avatarBox = new HBox(10);
        avatarBox.setAlignment(Pos.CENTER);
        String[] selectedAvatar = { "/com/mykogroup/riskclone/assets/Avatar1.png" };
        for (int i = 1; i <= 6; i++) {
            String path = "/com/mykogroup/riskclone/assets/Avatar" + i + ".png";
            ImageView iv = new ImageView(new Image(getClass().getResourceAsStream(path)));
            iv.setFitWidth(50);
            iv.setFitHeight(50);

            Button aBtn = new Button();
            aBtn.setGraphic(iv);
            aBtn.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 2;");
            aBtn.setOnAction(e -> {
                selectedAvatar[0] = path;
                avatarBox.getChildren().forEach(n -> n.setStyle(
                        "-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 2;"));
                aBtn.setStyle(
                        "-fx-background-color: rgba(212, 175, 55, 0.3); -fx-border-color: #d4af37; -fx-border-width: 2;");
            });
            if (i == 1)
                aBtn.setStyle(
                        "-fx-background-color: rgba(212, 175, 55, 0.3); -fx-border-color: #d4af37; -fx-border-width: 2;");
            avatarBox.getChildren().add(aBtn);
            Main.addHoverEffect(aBtn);
        }

        // Color Picker
        Label colorLabel = new Label("Pumili ng Kulay:");
        colorLabel.setTextFill(Color.WHITE);
        if (Main.BODY_FONT != null)
            colorLabel.setFont(Main.BODY_FONT);

        HBox colorBox = new HBox(10);
        colorBox.setAlignment(Pos.CENTER);
        String[] colors = { "#ef4444", "#3b82f6", "#10b981", "#f59e0b", "#8b5cf6", "#ec4899" };
        String[] selectedColor = { colors[0] };
        for (String hex : colors) {
            Button cBtn = new Button();
            cBtn.setPrefSize(35, 35);
            cBtn.setStyle("-fx-background-color: " + hex
                    + "; -fx-border-color: transparent; -fx-border-width: 2; -fx-background-radius: 50%; -fx-border-radius: 50%;");
            cBtn.setOnAction(e -> {
                selectedColor[0] = hex;
                colorBox.getChildren().forEach(n -> n.setStyle(
                        n.getStyle().replace("-fx-border-color: #d4af37;", "-fx-border-color: transparent;")));
                cBtn.setStyle(cBtn.getStyle().replace("-fx-border-color: transparent;", "-fx-border-color: #d4af37;"));
            });
            if (hex.equals(colors[0]))
                cBtn.setStyle(cBtn.getStyle().replace("-fx-border-color: transparent;", "-fx-border-color: #d4af37;"));
            colorBox.getChildren().add(cBtn);
            Main.addHoverEffect(cBtn);
        }

        Label errorLabel = new Label("");
        errorLabel.setTextFill(Color.web("#ef4444"));
        errorLabel.setFont(Font.font(14));

        Button okBtn = new Button("HANDA NA");
        okBtn.setStyle(Main.primaryBtnStyle(200, 55));
        okBtn.setFont(Main.headerFont(22));
        okBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                errorLabel.setText("Ilagay ang pangalan!");
                nameField.setStyle(
                        "-fx-font-size: 18px; -fx-background-color: #2c1e14; -fx-text-fill: white; -fx-border-color: #ef4444; -fx-border-width: 2;");
                return;
            }

            sendAsync(build(MessageType.UPDATE_NAME, new UpdateNamePayload(name)));
            sendAsync(build(MessageType.UPDATE_COLOR, new UpdateColorPayload(selectedColor[0])));
            sendAsync(build(MessageType.UPDATE_AVATAR, new UpdateAvatarPayload(selectedAvatar[0])));

            overlayRef[0].close();
        });
        Main.addHoverEffect(okBtn);

        Button cancelBtn = new Button("KANSELAHIN");
        cancelBtn.setStyle(Main.primaryBtnStyle(200, 55));
        cancelBtn.setFont(Main.headerFont(22));
        cancelBtn.setOnAction(e -> {
            overlayRef[0].close();
            if (client != null)
                client.disconnect();
            onLeave.run();
        });
        Main.addHoverEffect(cancelBtn);

        HBox actions = new HBox(20, cancelBtn, okBtn);
        actions.setAlignment(Pos.CENTER);

        root.getChildren().addAll(title, nameField, errorLabel, avatarLabel, avatarBox, colorLabel, colorBox, actions);

        overlayRef[0] = Main.showOverlay(root);
    }

    @Override
    public void onJoinAck(String assignedPlayerId) {
        this.localPlayerId = assignedPlayerId;
        if (!detailsPrompted) {
            Platform.runLater(this::showDetailsModal);
        }
    }

    @Override
    public void onLobbyUpdate(LobbyUpdatePayload payload) {
        Platform.runLater(() -> refreshPlayerList(payload.players));
    }

    @Override
    public void onGameStart(GameStartPayload payload) {
        Platform.runLater(onGameStart);
    }

    @Override
    public void onStateUpdate(StateUpdatePayload p) {
    }

    @Override
    public void onGameOver(GameOverPayload p) {
    }

    @Override
    public void onPlayerDisconnected(String pid) {
    }

    @Override
    public void onError(String message) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, message);
            a.show();
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(onLeave);
    }

    private void refreshPlayerList(List<LobbyPlayer> players) {
        playerGrid.getChildren().clear();
        for (int i = 0; i < 8; i++) {
            LobbyPlayer lp = (i < players.size()) ? players.get(i) : null;
            final int index = i;

            com.mykogroup.riskclone.model.LobbyPlayer modelPlayer = null;
            boolean isLpHost = false;
            if (lp != null) {
                modelPlayer = new com.mykogroup.riskclone.model.LobbyPlayer(
                        lp.playerId,
                        lp.displayName,
                        lp.color,
                        lp.isAi,
                        lp.avatarPath != null ? lp.avatarPath : "/com/mykogroup/riskclone/assets/Avatar1.png");
                // First player in list is host (server logic ensures this)
                if (index == 0)
                    isLpHost = true;
            }

            // X button only for AI players in network lobby
            Runnable kickAction = null;
            if (isHost && lp != null && lp.isAi) {
                kickAction = () -> sendAsync(build(MessageType.KICK_PLAYER, new KickPlayerPayload(lp.playerId)));
            }

            PlayerCard card = new PlayerCard(modelPlayer, isLpHost, kickAction);

            playerGrid.add(card, i % 2, i / 2);
        }
        if (isHost)
            startGameBtn.setDisable(players.size() < 4);
    }

    private void sendAsync(NetworkMessage msg) {
        if (client == null)
            return;
        new Thread(() -> client.send(msg)).start();
    }

    private NetworkMessage build(String type, Object payload) {
        return new NetworkMessage(type, localPlayerId, mapper.valueToTree(payload));
    }
}
