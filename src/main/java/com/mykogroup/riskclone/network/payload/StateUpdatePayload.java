package com.mykogroup.riskclone.network.payload;

import com.mykogroup.riskclone.model.GameState;

public class StateUpdatePayload {
    public GameState gameState;
    public String phase;
    public int timeRemaining;
    public StateUpdatePayload() {}
    public StateUpdatePayload(GameState gameState, String phase, int timeRemaining) {
        this.gameState = gameState;
        this.phase = phase;
        this.timeRemaining = timeRemaining;
    }
}
