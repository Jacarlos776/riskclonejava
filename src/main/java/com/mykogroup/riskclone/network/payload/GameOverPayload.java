package com.mykogroup.riskclone.network.payload;

public class GameOverPayload {
    public String winnerId; // null = mutual destruction
    public GameOverPayload() {}
    public GameOverPayload(String winnerId) { this.winnerId = winnerId; }
}
