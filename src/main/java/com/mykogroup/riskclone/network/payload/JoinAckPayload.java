package com.mykogroup.riskclone.network.payload;

public class JoinAckPayload {
    public String assignedPlayerId;
    public JoinAckPayload() {}
    public JoinAckPayload(String assignedPlayerId) {
        this.assignedPlayerId = assignedPlayerId;
    }
}
