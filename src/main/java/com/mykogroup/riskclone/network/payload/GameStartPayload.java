package com.mykogroup.riskclone.network.payload;

import com.mykogroup.riskclone.model.GameState;
import java.util.Map;

public class GameStartPayload {
    public GameState gameState;
    public Map<String, String> colors; // playerId → hex
    public GameStartPayload() {}
    public GameStartPayload(GameState gameState, Map<String, String> colors) {
        this.gameState = gameState;
        this.colors = colors;
    }
}
