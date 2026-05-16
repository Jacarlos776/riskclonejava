package com.mykogroup.riskclone.network.payload;

import com.mykogroup.riskclone.engine.ResolutionResult;
import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Move;

import java.util.List;
import java.util.Map;

public class ResolutionPlaybackPayload {
    public GameState finalState;
    public List<ResolutionResult> results;
    public Map<String, String> preOwners;
    public Map<String, Integer> preArmies;
    public List<Move> queuedMoves;
    public int animationDurationMs;

    public ResolutionPlaybackPayload() {}

    public ResolutionPlaybackPayload(GameState finalState,
                                     List<ResolutionResult> results,
                                     Map<String, String> preOwners,
                                     Map<String, Integer> preArmies,
                                     List<Move> queuedMoves,
                                     int animationDurationMs) {
        this.finalState = finalState;
        this.results = results;
        this.preOwners = preOwners;
        this.preArmies = preArmies;
        this.queuedMoves = queuedMoves;
        this.animationDurationMs = animationDurationMs;
    }
}
