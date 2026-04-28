package com.mykogroup.riskclone.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykogroup.riskclone.network.payload.JoinPayload;
import com.mykogroup.riskclone.network.payload.LobbyUpdatePayload;
import com.mykogroup.riskclone.network.payload.StateUpdatePayload;
import com.mykogroup.riskclone.network.LobbyPlayer;
import com.mykogroup.riskclone.model.GameState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class NetworkMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void networkMessage_withJoinPayload_roundTrips() throws Exception {
        JoinPayload join = new JoinPayload("Player 1", "#ef4444");
        NetworkMessage msg = new NetworkMessage(
                MessageType.JOIN, null, mapper.valueToTree(join));

        String json = mapper.writeValueAsString(msg);
        NetworkMessage parsed = mapper.readValue(json, NetworkMessage.class);

        assertEquals(MessageType.JOIN, parsed.type);
        assertNull(parsed.senderId);
        JoinPayload back = mapper.treeToValue(parsed.payload, JoinPayload.class);
        assertEquals("Player 1", back.displayName);
        assertEquals("#ef4444", back.preferredColor);
    }

    @Test
    void networkMessage_withStateUpdatePayload_roundTrips() throws Exception {
        GameState state = new GameState();
        state.getProvinces().add(new com.mykogroup.riskclone.model.Province("PH-BTN", "p1", 3));
        StateUpdatePayload sup = new StateUpdatePayload(state, "PLANNING", 45);
        NetworkMessage msg = new NetworkMessage(MessageType.STATE_UPDATE, null, mapper.valueToTree(sup));

        String json = mapper.writeValueAsString(msg);
        NetworkMessage parsed = mapper.readValue(json, NetworkMessage.class);
        StateUpdatePayload back = mapper.treeToValue(parsed.payload, StateUpdatePayload.class);

        assertEquals("PLANNING", back.phase);
        assertEquals(45, back.timeRemaining);
        assertEquals(1, back.gameState.getProvinces().size());
        assertEquals("p1", back.gameState.getProvince("PH-BTN").get().getOwnerId());
    }

    @Test
    void networkMessage_withLobbyUpdatePayload_roundTrips() throws Exception {
        LobbyPlayer lp = new LobbyPlayer("player1", "Alice", "#ef4444", false);
        LobbyUpdatePayload lobby = new LobbyUpdatePayload(java.util.List.of(lp));
        NetworkMessage msg = new NetworkMessage(
                MessageType.LOBBY_UPDATE, null, mapper.valueToTree(lobby));

        String json = mapper.writeValueAsString(msg);
        NetworkMessage parsed = mapper.readValue(json, NetworkMessage.class);
        LobbyUpdatePayload back = mapper.treeToValue(parsed.payload, LobbyUpdatePayload.class);

        assertEquals(1, back.players.size());
        assertEquals("Alice", back.players.get(0).displayName);
        assertEquals(false, back.players.get(0).isAi);
    }
}
