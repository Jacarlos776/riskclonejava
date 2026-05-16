package com.mykogroup.riskclone.view;

import com.mykogroup.riskclone.Main;
import com.mykogroup.riskclone.model.LobbyPlayer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class LocalLobbyPane extends StackPane {
    private final List<LobbyPlayer> players = new ArrayList<>();
    private final GridPane playerGrid = new GridPane();
    private final Runnable onStart;
    private final Runnable onBack;

    private static final String[] DEFAULT_COLORS = {
            "#ef4444", "#3b82f6", "#10b981", "#f59e0b",
            "#8b5cf6", "#ec4899", "#14b8a6", "#f97316"
    };

    private static final String[] AI_NAMES = {
            "Jose Rizal", "Andres Bonifacio", "Magellan", "Lapu-lapu", "Antonio Luna",
            "Gabriela Silang", "Apolinario Mabini", "Emilio Jacinto", "Melchora Aquino",
            "Sultan Kudarat", "Ferdinand Magellan", "Juan Luna", "Emilio Aguinaldo"
    };

    public LocalLobbyPane(Runnable onStart, Runnable onBack) {
        this.onStart = onStart;
        this.onBack = onBack;

        // Background
        try {
            ImageView bgView = new ImageView(
                    new Image(getClass().getResourceAsStream("/com/mykogroup/riskclone/assets/local-lobby-bg.png")));
            bgView.setFitWidth(1280);
            bgView.setFitHeight(720);
            getChildren().add(bgView);
        } catch (Exception e) {
            setStyle("-fx-background-color: #3d2b1f;");
        }

        VBox content = new VBox(30);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(120, 0, 0, 0)); // Lowered to avoid background header overlap

        // Grid
        playerGrid.setHgap(30);
        playerGrid.setVgap(20);
        playerGrid.setAlignment(Pos.CENTER);
        refreshGrid();

        // Bottom Buttons (Add Player, Add Bot)
        HBox addButtons = new HBox(30);
        addButtons.setAlignment(Pos.CENTER);

        Button addPlayerBtn = createIconButton("/com/mykogroup/riskclone/assets/add-player-btn.png", 220, 60);
        addPlayerBtn.setOnAction(e -> showAddPlayerModal(false));
        Main.addHoverEffect(addPlayerBtn);

        Button addBotBtn = createIconButton("/com/mykogroup/riskclone/assets/add-bot-btn.png", 220, 60);
        addBotBtn.setOnAction(e -> addBot());
        Main.addHoverEffect(addBotBtn);

        addButtons.getChildren().addAll(addPlayerBtn, addBotBtn);

        content.getChildren().addAll(playerGrid, addButtons);

        // Play Button (Right side)
        Button playBtn = createIconButton("/com/mykogroup/riskclone/assets/play-btn.png", 100, 150);
        StackPane.setAlignment(playBtn, Pos.CENTER_RIGHT);
        playBtn.setTranslateX(0); // Move against the edge
        playBtn.setOnAction(e -> {
            if (players.size() >= 4) {
                if (onStart != null)
                    onStart.run();
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Minimum of 4 players required to start!");
                alert.show();
            }
        });
        Main.addHoverEffect(playBtn);

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

        backBtn.setOnAction(e -> onBack.run());
        StackPane.setAlignment(backBtn, Pos.TOP_LEFT);
        StackPane.setMargin(backBtn, new Insets(45, 0, 0, 45));
        Main.addHoverEffect(backBtn);

        getChildren().addAll(content, playBtn, backBtn);
    }

    public List<LobbyPlayer> getPlayers() {
        return players;
    }

    private void addBot() {
        if (players.size() >= 8)
            return;

        Random rand = new Random();
        String name = AI_NAMES[rand.nextInt(AI_NAMES.length)];
        String avatar = "/com/mykogroup/riskclone/assets/Avatar" + (rand.nextInt(6) + 1) + ".png";

        // Find first available color
        List<String> takenColors = players.stream().map(p -> p.color).collect(Collectors.toList());
        String color = DEFAULT_COLORS[0];
        for (String c : DEFAULT_COLORS) {
            if (!takenColors.contains(c)) {
                color = c;
                break;
            }
        }

        players.add(new LobbyPlayer("ai-" + System.currentTimeMillis() + "-" + rand.nextInt(1000), name, color, true, avatar));
        refreshGrid();
    }

    private void refreshGrid() {
        playerGrid.getChildren().clear();
        for (int i = 0; i < 8; i++) {
            int row = i / 2;
            int col = i % 2;

            LobbyPlayer p = (i < players.size()) ? players.get(i) : null;
            final int index = i;
            PlayerCard card = new PlayerCard(p, false, () -> {
                players.remove(index);
                refreshGrid();
            });
            playerGrid.add(card, col, row);
        }
    }

    private void showAddPlayerModal(boolean isAi) {
        if (players.size() >= 8)
            return;

        final Main.Overlay[] overlayRef = new Main.Overlay[1];

        VBox root = new VBox(25);
        root.setPadding(new Insets(35));
        root.setStyle("-fx-background-color: #3d2b1f; " +
                "-fx-border-color: #d4af37; " +
                "-fx-border-width: 4; " +
                "-fx-background-radius: 20; " +
                "-fx-border-radius: 20; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 20, 0, 0, 0);");
        root.setAlignment(Pos.CENTER);
        root.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);

        Label title = new Label("MAGDAGDAG NG MANLALARO");
        if (Main.HEADER_FONT != null)
            title.setFont(Main.HEADER_FONT);
        else
            title.setFont(Font.font("System", FontWeight.BOLD, 28));
        title.setTextFill(Color.web("#d4af37"));

        TextField nameField = new TextField();
        nameField.setPromptText("Ilagay ang Pangalan");
        nameField.setStyle(
                "-fx-font-size: 18px; -fx-background-color: #2c1e14; -fx-text-fill: white; -fx-border-color: #5d4037;");
        if (Main.BODY_FONT != null)
            nameField.setFont(Main.BODY_FONT);

        // Avatar Picker
        Label avatarLabel = new Label("Pumili ng Avatar:");
        avatarLabel.setTextFill(Color.WHITE);
        HBox avatarBox = new HBox(10);
        avatarBox.setAlignment(Pos.CENTER);
        String selectedAvatar[] = { "/com/mykogroup/riskclone/assets/Avatar1.png" };

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
                avatarBox.getChildren().forEach(n -> n.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 2;"));
                aBtn.setStyle("-fx-background-color: rgba(212, 175, 55, 0.3); -fx-border-color: #d4af37; -fx-border-width: 2;");
            });
            
            if (i == 1) {
                aBtn.setStyle("-fx-background-color: rgba(212, 175, 55, 0.3); -fx-border-color: #d4af37; -fx-border-width: 2;");
            }
            
            avatarBox.getChildren().add(aBtn);
            Main.addHoverEffect(aBtn);
        }

        // Color Picker
        Label colorLabel = new Label("Pumili ng Kulay:");
        colorLabel.setTextFill(Color.WHITE);
        if (Main.BODY_FONT != null) colorLabel.setFont(Main.BODY_FONT);

        HBox colorBox = new HBox(10);
        colorBox.setAlignment(Pos.CENTER);
        
        List<String> takenColors = players.stream().map(p -> p.color).collect(Collectors.toList());
        String selectedColor[] = {null};

        for (String hex : DEFAULT_COLORS) {
            Button cBtn = new Button();
            cBtn.setPrefSize(35, 35);
            cBtn.setStyle("-fx-background-color: " + hex + "; -fx-border-color: transparent; -fx-border-width: 2; -fx-background-radius: 50%; -fx-border-radius: 50%;");
            
            if (takenColors.contains(hex)) {
                cBtn.setDisable(true);
                cBtn.setOpacity(0.3);
            } else {
                if (selectedColor[0] == null) {
                    selectedColor[0] = hex;
                    cBtn.setStyle(cBtn.getStyle().replace("-fx-border-color: transparent;", "-fx-border-color: #d4af37;"));
                }
                cBtn.setOnAction(e -> {
                    selectedColor[0] = hex;
                    colorBox.getChildren().forEach(n -> n.setStyle(n.getStyle().replace("-fx-border-color: #d4af37;", "-fx-border-color: transparent;")));
                    cBtn.setStyle(cBtn.getStyle().replace("-fx-border-color: transparent;", "-fx-border-color: #d4af37;"));
                });
                Main.addHoverEffect(cBtn);
            }
            colorBox.getChildren().add(cBtn);
        }

        Label errorLabel = new Label("");
        errorLabel.setTextFill(Color.web("#ef4444"));
        errorLabel.setFont(Font.font(14));

        Button addBtn = new Button("DAGDAGAN");
        addBtn.setStyle(Main.primaryBtnStyle(200, 55));
        addBtn.setFont(Main.headerFont(22));
        addBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                errorLabel.setText("Ilagay ang pangalan!");
                nameField.setStyle(
                        "-fx-font-size: 18px; -fx-background-color: #2c1e14; -fx-text-fill: white; -fx-border-color: #ef4444; -fx-border-width: 2;");
                return;
            }
            players.add(new LobbyPlayer("player-" + System.currentTimeMillis(), name, selectedColor[0], isAi, selectedAvatar[0]));
            overlayRef[0].close();
            refreshGrid();
        });
        Main.addHoverEffect(addBtn);

        Button cancelBtn = new Button("KANSELAHIN");
        cancelBtn.setStyle(Main.primaryBtnStyle(200, 55));
        cancelBtn.setFont(Main.headerFont(22));
        cancelBtn.setOnAction(e -> overlayRef[0].close());
        Main.addHoverEffect(cancelBtn);

        HBox actions = new HBox(20, cancelBtn, addBtn);
        actions.setAlignment(Pos.CENTER);

        root.getChildren().addAll(title, nameField, errorLabel, avatarLabel, avatarBox, colorLabel, colorBox, actions);

        overlayRef[0] = Main.showOverlay(root);
    }

    private Button createIconButton(String path, double w, double h) {
        Button btn = new Button();
        try {
            ImageView iv = new ImageView(new Image(getClass().getResourceAsStream(path)));
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            btn.setGraphic(iv);
            btn.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            btn.setCursor(javafx.scene.Cursor.HAND);
        } catch (Exception e) {
            btn.setText(path.substring(path.lastIndexOf('/') + 1));
        }
        return btn;
    }
}
