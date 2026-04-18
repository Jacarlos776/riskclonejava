package com.mykogroup.riskclone.view;

import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

public class ColorManager {

    // Maps playerId to a Hex Color String
    private static final Map<String, String> PLAYER_COLORS = new HashMap<>();

    // Default neutral color if ownerId is null or unknown
    public static final String NEUTRAL_COLOR = "#d1d5db"; // Light Gray

    public static void setPlayerColor(String playerId, String hexColor) {
        PLAYER_COLORS.put(playerId, hexColor);
    }

    public static String getColorForPlayer(String playerId) {
        if (playerId == null) return NEUTRAL_COLOR;
        return PLAYER_COLORS.getOrDefault(playerId, NEUTRAL_COLOR);
    }
}