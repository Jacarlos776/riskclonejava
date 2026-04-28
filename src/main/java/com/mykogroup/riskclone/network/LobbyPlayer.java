package com.mykogroup.riskclone.network;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LobbyPlayer {
    public String playerId;
    public String displayName;
    public String color;
    @JsonProperty("isAi")
    public boolean isAi;

    public LobbyPlayer() {}

    public LobbyPlayer(String playerId, String displayName, String color, boolean isAi) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.color = color;
        this.isAi = isAi;
    }
}
