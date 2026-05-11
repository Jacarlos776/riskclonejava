package com.mykogroup.riskclone.network.payload;

public class KickPlayerPayload {
    public String targetPlayerId;
    public KickPlayerPayload() {}
    public KickPlayerPayload(String targetPlayerId) { this.targetPlayerId = targetPlayerId; }
}
