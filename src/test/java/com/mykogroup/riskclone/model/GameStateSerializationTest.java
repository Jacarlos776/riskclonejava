package com.mykogroup.riskclone.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GameStateSerializationTest {

    @Test
    void draftPools_surviveJsonRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GameState state = new GameState();
        state.getPlayers().add(new Player("p1", "Alice", false));
        state.initDraftPools(5);

        String json = mapper.writeValueAsString(state);
        GameState restored = mapper.readValue(json, GameState.class);

        assertEquals(state.getDraftArmies("p1"), restored.getDraftArmies("p1"),
                "Draft pool for p1 must survive serialization");
    }

    @Test
    void provinceList_survivesJsonRoundTrip() {
        ObjectMapper mapper = new ObjectMapper();
        GameState state = new GameState();
        state.getProvinces().add(new Province("PH-BTN", null, 0));
        state.getProvinces().add(new Province("PH-CAG", "p1", 5));

        assertDoesNotThrow(() -> {
            String json = mapper.writeValueAsString(state);
            GameState restored = mapper.readValue(json, GameState.class);
            assertEquals(2, restored.getProvinces().size());
            assertEquals("p1", restored.getProvince("PH-CAG").get().getOwnerId());
        });
    }
}
