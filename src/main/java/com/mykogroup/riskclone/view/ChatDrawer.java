package com.mykogroup.riskclone.view;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.function.Consumer;

// Collapsible in-game chat drawer pinned to the top-left of the game scene.
// Collapsed: a small 💬 button.
// Expanded:  a 300x340 panel with a header, scrollable message log, and an
//            input row (TextField + Send button). Enter also sends.
//
// Drop-in usage: create one, register a Consumer<String> for outbound chat,
// then call addMessage(...) / addSystemMessage(...) when CHAT_BROADCAST arrives.
public class ChatDrawer extends StackPane {

    // --- Style constants (match the game's dark navy theme) ---
    private static final String PANEL_BG  = "#1e2235";
    private static final String LIGHT_BG  = "#2a2d3e";
    private static final String ACCENT    = "#27ae60";
    private static final String TEXT_MUTED= "#94a3b8";
    private static final int    MAX_MSGS  = 200;
    private static final double PANEL_W   = 300;
    private static final double PANEL_H   = 340;

    // --- State ---
    private final Button iconButton = new Button("💬");
    private final VBox panel        = new VBox();
    private final VBox messageList  = new VBox(4);
    private final ScrollPane scroll = new ScrollPane(messageList);
    private final TextField input   = new TextField();
    private final Button sendBtn    = new Button("Send");

    private boolean expanded = false;
    private Consumer<String> onSend;

    public ChatDrawer() {
        buildIconButton();
        buildPanel();

        // Stack panel on top of icon; only one is visible at a time
        getChildren().addAll(iconButton, panel);
        StackPane.setAlignment(iconButton, Pos.TOP_LEFT);
        StackPane.setAlignment(panel,      Pos.TOP_LEFT);
        setPickOnBounds(false);  // clicks outside the visible child fall through
        setMaxSize(PANEL_W, PANEL_H);

        collapse(); // start collapsed
    }

    // Register where outgoing typed messages should be sent.
    public void setOnSend(Consumer<String> sender) { this.onSend = sender; }

    // Append a player message. Safe to call from any thread.
    public void addMessage(String senderName, String colorHex, String text) {
        Platform.runLater(() -> appendRow(senderName, colorHex, text, false));
    }

    // Append a system / game-event message. Safe to call from any thread.
    public void addSystemMessage(String text) {
        Platform.runLater(() -> appendRow(null, null, text, true));
    }

    // ── Build ───────────────────────────────────────────────────────────────

    private void buildIconButton() {
        iconButton.setFont(Font.font("System", FontWeight.BOLD, 18));
        iconButton.setPrefSize(44, 44);
        iconButton.setStyle(
                "-fx-background-color: " + PANEL_BG + ";" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: " + LIGHT_BG + ";" +
                "-fx-border-radius: 8;" +
                "-fx-border-width: 1;" +
                "-fx-cursor: hand;");
        iconButton.setOnAction(e -> expand());
    }

    private void buildPanel() {
        panel.setPrefSize(PANEL_W, PANEL_H);
        panel.setMaxSize(PANEL_W, PANEL_H);
        panel.setStyle(
                "-fx-background-color: " + PANEL_BG + ";" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: " + LIGHT_BG + ";" +
                "-fx-border-radius: 8;" +
                "-fx-border-width: 1;");

        // --- Header bar with title + close button ---
        Label title = new Label("Chat");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));
        title.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button closeBtn = new Button("×");
        closeBtn.setFont(Font.font("System", FontWeight.BOLD, 16));
        closeBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: " + TEXT_MUTED + ";" +
                "-fx-cursor: hand;" +
                "-fx-padding: 0 6;");
        closeBtn.setOnAction(e -> collapse());

        HBox header = new HBox(8, title, spacer, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 10, 8, 12));
        header.setStyle("-fx-background-color: " + LIGHT_BG + ";" +
                        "-fx-background-radius: 8 8 0 0;");

        // --- Scrollable message list ---
        messageList.setPadding(new Insets(8, 10, 8, 10));
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle(
                "-fx-background: " + PANEL_BG + ";" +
                "-fx-background-color: " + PANEL_BG + ";" +
                "-fx-border-color: transparent;");
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        // Auto-scroll to bottom when new messages are added
        ChangeListener<Number> autoScroll = (obs, oldV, newV) -> scroll.setVvalue(1.0);
        messageList.heightProperty().addListener(autoScroll);

        // --- Input row ---
        input.setPromptText("Type a message…");
        input.setStyle(
                "-fx-background-color: " + LIGHT_BG + ";" +
                "-fx-text-fill: white;" +
                "-fx-prompt-text-fill: " + TEXT_MUTED + ";" +
                "-fx-background-radius: 4;" +
                "-fx-padding: 6 8;");
        input.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) doSend(); });

        sendBtn.setStyle(
                "-fx-background-color: " + ACCENT + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 4;" +
                "-fx-padding: 6 12;" +
                "-fx-cursor: hand;");
        sendBtn.setOnAction(e -> doSend());

        HBox inputRow = new HBox(6, input, sendBtn);
        HBox.setHgrow(input, javafx.scene.layout.Priority.ALWAYS);
        inputRow.setPadding(new Insets(8, 10, 10, 10));
        inputRow.setStyle("-fx-background-color: " + PANEL_BG + ";" +
                          "-fx-background-radius: 0 0 8 8;");

        panel.getChildren().addAll(header, scroll, inputRow);
    }

    // ── Behaviour ───────────────────────────────────────────────────────────

    private void doSend() {
        String text = input.getText();
        if (text == null) return;
        text = text.strip();
        if (text.isEmpty()) return;
        if (onSend != null) onSend.accept(text);
        input.clear();
    }

    public void expand() {
        expanded = true;
        iconButton.setVisible(false);
        iconButton.setManaged(false);
        panel.setVisible(true);
        panel.setManaged(true);
        input.requestFocus();
    }

    public void collapse() {
        expanded = false;
        panel.setVisible(false);
        panel.setManaged(false);
        iconButton.setVisible(true);
        iconButton.setManaged(true);
    }

    private void appendRow(String senderName, String colorHex, String text, boolean system) {
        TextFlow flow = new TextFlow();
        flow.setMaxWidth(PANEL_W - 24);
        if (system) {
            Text t = new Text(text);
            t.setFill(Color.web(TEXT_MUTED));
            t.setFont(Font.font("System", javafx.scene.text.FontPosture.ITALIC, 12));
            flow.getChildren().add(t);
        } else {
            Text name = new Text(senderName + ": ");
            name.setFill(safeColor(colorHex));
            name.setFont(Font.font("System", FontWeight.BOLD, 13));
            Text body = new Text(text);
            body.setFill(Color.web("#e2e8f0"));
            body.setFont(Font.font("System", 13));
            flow.getChildren().addAll(name, body);
        }
        messageList.getChildren().add(flow);

        // Trim oldest if we exceed the cap
        while (messageList.getChildren().size() > MAX_MSGS) {
            messageList.getChildren().remove(0);
        }
    }

    private static Color safeColor(String hex) {
        try {
            return Color.web(hex == null ? "#cccccc" : hex);
        } catch (Exception e) {
            return Color.web("#cccccc");
        }
    }
}
