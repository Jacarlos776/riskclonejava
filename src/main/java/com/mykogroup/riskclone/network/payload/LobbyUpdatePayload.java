package com.mykogroup.riskclone.network.payload;

import com.mykogroup.riskclone.network.LobbyPlayer;
import java.util.List;

public class LobbyUpdatePayload {
    public List<LobbyPlayer> players;
    public LobbyUpdatePayload() {}
    public LobbyUpdatePayload(List<LobbyPlayer> players) {
        this.players = players;
    }
}
