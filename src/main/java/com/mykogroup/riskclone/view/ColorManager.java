package com.mykogroup.riskclone.view;

import java.util.HashMap;
import java.util.Map;

public class ColorManager {

    // Maps playerId to a Hex Color String
    private static final Map<String, String> PLAYER_COLORS = new HashMap<>();

    static {
        PLAYER_COLORS.put("player1", "#ef4444"); // Red
        PLAYER_COLORS.put("player2", "#3b82f6"); // Blue
        PLAYER_COLORS.put("player3", "#10b981"); // Green
        PLAYER_COLORS.put("player4", "#f59e0b"); // Yellow
    }

    // Default neutral color if ownerId is null or unknown
    public static final String NEUTRAL_COLOR = "#d1d5db"; // Light Gray

    public static String getColorForPlayer(String playerId) {
        if (playerId == null) return NEUTRAL_COLOR;
        return PLAYER_COLORS.getOrDefault(playerId, NEUTRAL_COLOR);
    }
}