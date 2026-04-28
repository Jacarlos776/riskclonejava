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
        if (finishedTurnBtn != null) {
            finishedTurnBtn.setDisable(true);
            finishedTurnBtn.setText("Waiting for others...");
        }
        send(MessageType.END_TURN, new Object());
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

            // Re-enable the button so the player can act on the new phase
            if (finishedTurnBtn != null) {
                finishedTurnBtn.setDisable(false);
                finishedTurnBtn.setText("Finished Turn");
            }

            if (timerLabel != null) {
                timerLabel.setText(payload.phase);
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

    private void send(String type, Object payload) {
        if (client == null) return;
        client.send(new NetworkMessage(type, localPlayerId, mapper.valueToTree(payload)));
    }
}
