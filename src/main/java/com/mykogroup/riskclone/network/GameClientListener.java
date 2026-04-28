package com.mykogroup.riskclone.network;

import com.mykogroup.riskclone.network.payload.*;

public interface GameClientListener {
    void onJoinAck(String assignedPlayerId);
    void onLobbyUpdate(LobbyUpdatePayload payload);
    void onGameStart(GameStartPayload payload);
    void onStateUpdate(StateUpdatePayload payload);
    void onGameOver(GameOverPayload payload);
    void onPlayerDisconnected(String playerId);
    void onError(String message);
    void onDisconnected();          // socket closed / server unreachable
}
