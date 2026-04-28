package com.mykogroup.riskclone.network.payload;

public class PlayerDisconnectedPayload {
    public String playerId;
    public PlayerDisconnectedPayload() {}
    public PlayerDisconnectedPayload(String playerId) { this.playerId = playerId; }
}
