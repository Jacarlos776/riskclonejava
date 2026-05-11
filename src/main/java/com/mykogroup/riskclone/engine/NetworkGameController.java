package com.mykogroup.riskclone.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Move;
import com.mykogroup.riskclone.network.GameClient;
import com.mykogroup.riskclone.network.GameClientListener;
import com.mykogroup.riskclone.network.MessageType;
import com.mykogroup.riskclone.network.NetworkMessage;
import com.mykogroup.riskclone.network.payload.*;
import com.mykogroup.riskclone.view.ColorManager;
import com.mykogroup.riskclone.view.InteractiveMapPane;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.util.Map;
import java.util.function.Consumer;

// Bridges the GameClient (network) and the JavaFX game view.
// All listener callbacks arrive on the client read thread —
// anything touching JavaFX nodes is wrapped in Platform.runLater().
public class NetworkGameController implements GameClientListener {

    // --- State ---
    private GameClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private String localPlayerId;

    // JavaFX UI references (set once in attachUI)
    private InteractiveMapPane gameBoard;
    private Label timerLabel;
    private Label playerTurnLabel;
    private Label draftCountLabel;
    private Button finishedTurnBtn;

    // Callback so Main can show the game-over screen (winnerId = null means draw)
    private Consumer<String> onGameOverCallback;

    // Toggle state for the Finished Turn button
    private boolean isReady = false;
    private String lastPhase = null;

    private static final String FINISHED_STYLE =
            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #f59e0b; -fx-text-fill: white;";
    private static final String CANCEL_STYLE =
            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: #ef4444; -fx-text-fill: white;";

    public NetworkGameController() {}

    // Set after construction to break the circular dependency with GameClient
    public void setClient(GameClient client) { this.client = client; }

    // Called by Main once the game view is constructed
    public void attachUI(InteractiveMapPane gameBoard,
                         Label timerLabel,
                         Label playerTurnLabel,
                         Label draftCountLabel,
                         Button finishedTurnBtn) {
        this.gameBoard       = gameBoard;
        this.timerLabel      = timerLabel;
        this.playerTurnLabel = playerTurnLabel;
        this.draftCountLabel = draftCountLabel;
        this.finishedTurnBtn = finishedTurnBtn;

        // Forward map clicks to the server instead of mutating local state
        gameBoard.enableNetworkMode(
                this::submitClaim,
                this::submitDraft,
                this::submitMove
        );

        finishedTurnBtn.setText("Finished Turn");
        finishedTurnBtn.setStyle(FINISHED_STYLE);
        finishedTurnBtn.setOnAction(e -> submitFinishedTurn());
    }

    public void setLocalPlayerId(String pid)               { this.localPlayerId = pid; }
    public String getLocalPlayerId()                       { return localPlayerId; }
    public void setOnGameOverCallback(Consumer<String> cb) { this.onGameOverCallback = cb; }

    // --- Intent Senders ---

    public void submitClaim(String provinceId) {
        send(MessageType.CLAIM_REQUEST, new ClaimRequestPayload(provinceId));
    }

    public void submitDraft(String provinceId) {
        send(MessageType.DRAFT_REQUEST, new DraftRequestPayload(provinceId));
    }

    public void submitMove(Move move) {
        send(MessageType.MOVE_REQUEST, new MoveRequestPayload(move));
    }

    public void submitFinishedTurn() {
        isReady = !isReady;
        if (finishedTurnBtn != null) {
            if (isReady) {
                finishedTurnBtn.setText("Cancel");
                finishedTurnBtn.setStyle(CANCEL_STYLE);
            } else {
                finishedTurnBtn.setText("Finished Turn");
                finishedTurnBtn.setStyle(FINISHED_STYLE);
            }
        }
        send(MessageType.END_TURN, null);
    }

    // --- GameClientListener ---

    @Override
    public void onJoinAck(String assignedPlayerId) {
        this.localPlayerId = assignedPlayerId;
    }

    @Override
    public void onLobbyUpdate(LobbyUpdatePayload payload) {
        // Handled by NetworkLobbyPane, not this controller
    }

    @Override
    public void onGameStart(GameStartPayload payload) {
        Platform.runLater(() -> {
            for (Map.Entry<String, String> e : payload.colors.entrySet()) {
                ColorManager.setPlayerColor(e.getKey(), e.getValue());
            }
            if (gameBoard != null) {
                gameBoard.setCurrentLocalPlayerId(localPlayerId);
                gameBoard.setGameState(payload.gameState);
                gameBoard.renderState(payload.gameState);
            }
        });
    }

    @Override
    public void onStateUpdate(StateUpdatePayload payload) {
        Platform.runLater(() -> {
            GameState state = payload.gameState;

            if (gameBoard != null) {
                gameBoard.setCurrentLocalPlayerId(localPlayerId);
                gameBoard.setGameState(state);
                gameBoard.renderState(state);
            }

            // Reset the toggle when the phase actually changes; intra-phase
            // STATE_UPDATEs (e.g. someone else's claim) preserve our ready state.
            boolean phaseChanged = payload.phase != null && !payload.phase.equals(lastPhase);
            lastPhase = payload.phase;
            if (phaseChanged && finishedTurnBtn != null) {
                isReady = false;
                finishedTurnBtn.setDisable(false);
                finishedTurnBtn.setText("Finished Turn");
                finishedTurnBtn.setStyle(FINISHED_STYLE);
            }

            if (timerLabel != null && phaseChanged) {
                // Render the phase name immediately; subsequent TIMER_UPDATEs overwrite this.
                timerLabel.setText(payload.phase);
                timerLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            }

            if (playerTurnLabel != null && localPlayerId != null) {
                state.getPlayers().stream()
                        .filter(p -> p.getId().equals(localPlayerId))
                        .findFirst()
                        .ifPresent(p -> playerTurnLabel.setText("You: " + p.getDisplayName()));
            }

            if (draftCountLabel != null) {
                int draftsLeft = state.getDraftArmies(localPlayerId);
                if (state.getCurrentPhase() == GameState.GamePhase.DRAFTING && draftsLeft > 0) {
                    draftCountLabel.setText("Armies left: " + draftsLeft);
                    draftCountLabel.setVisible(true);
                } else {
                    draftCountLabel.setVisible(false);
                }
            }
        });
    }

    @Override
    public void onGameOver(GameOverPayload payload) {
        Platform.runLater(() -> {
            if (onGameOverCallback != null) onGameOverCallback.accept(payload.winnerId);
        });
    }

    @Override
    public void onPlayerDisconnected(String playerId) {
        Platform.runLater(() -> {
            if (timerLabel != null)
                timerLabel.setText("Player " + playerId + " disconnected — taken over by AI");
        });
    }

    @Override
    public void onTimerUpdate(String phase, int secondsRemaining) {
        Platform.runLater(() -> {
            if (timerLabel == null) return;
            timerLabel.setText(phase + " Phase: " + secondsRemaining + "s");
            timerLabel.setTextFill(secondsRemaining <= 5
                    ? javafx.scene.paint.Color.RED
                    : javafx.scene.paint.Color.WHITE);
        });
    }

    @Override
    public void onError(String message) {
        Platform.runLater(() -> {
            if (timerLabel != null) timerLabel.setText("Error: " + message);
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            if (timerLabel != null) timerLabel.setText("Disconnected from server");
            if (finishedTurnBtn != null) finishedTurnBtn.setDisable(true);
        });
    }

    // Build the message on the calling thread, send on a daemon thread
    // so the FX thread is never blocked by socket I/O.
    private void send(String type, Object payload) {
        if (client == null) return;
        com.fasterxml.jackson.databind.JsonNode node = (payload == null)
                ? mapper.createObjectNode()
                : mapper.valueToTree(payload);
        NetworkMessage msg = new NetworkMessage(type, localPlayerId, node);
        Thread t = new Thread(() -> client.send(msg), "ctrl-send");
        t.setDaemon(true);
        t.start();
    }
}
