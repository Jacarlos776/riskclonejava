package com.mykogroup.riskclone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameState {

    public enum GamePhase { DRAFTING, PLANNING, RESOLUTION }

    private GamePhase currentPhase = GamePhase.DRAFTING;
    private final Map<String, Integer> draftPools = new HashMap<>();

    private List<Player> players = new ArrayList<>();
    private List<Province> provinces = new ArrayList<>();
    private List<Move> queuedMoves = new ArrayList<>();
    private final Set<String> readyPlayers = new HashSet<>();

    // Default constructor for Jackson
    public GameState() {}

    // --- Core State Manipulations ---

    // Call this at the start of every draft phase
    public void initDraftPools(int armiesPerPlayer) {
        for (Player p : players) {
            draftPools.put(p.getId(), armiesPerPlayer);
        }
    }

    // Attempts to place an army. Returns true if successful.
    public boolean placeDraftArmy(String playerId, String provinceId) {
        int available = getDraftArmies(playerId);
        if (available > 0) {
            Optional<Province> p = getProvince(provinceId);
            // Verify they actually own the province
            if (p.isPresent() && playerId.equals(p.get().getOwnerId())) {
                p.get().setArmyCount(p.get().getArmyCount() + 1);
                draftPools.put(playerId, available - 1);
                return true;
            }
        }
        return false;
    }

    public void clearQueuedMoves() {
        queuedMoves.clear();
    }

    // Helper method to quickly find a province by its ID
    public Optional<Province> getProvince(String provinceId) {
        return provinces.stream()
                .filter(p -> p.getId().equals(provinceId))
                .findFirst();
    }

    // Find if a move already exists between these two provinces
    public Optional<Move> getExistingMove(String fromId, String toId) {
        return queuedMoves.stream()
                .filter(m -> m.fromId().equals(fromId) && m.toId().equals(toId))
                .findFirst();
    }

    // Calculate how many troops the player can send
    public int getAvailableTroopsForMove(String provinceId, String targetId) {
        Province p = getProvince(provinceId).orElse(null);
        if (p == null) return 0;

        int totalArmies = p.getArmyCount();

        // Sum up all troops already committed to OTHER moves from this province
        int committedArmies = queuedMoves.stream()
                .filter(m -> m.fromId().equals(provinceId) && !m.toId().equals(targetId))
                .mapToInt(Move::armies)
                .sum();

        return totalArmies - committedArmies;
    }

    // Update or remove a move safely
    public void setMove(Move newMove) {
        // Remove any existing move for this exact path
        queuedMoves.removeIf(m -> m.fromId().equals(newMove.fromId()) && m.toId().equals(newMove.toId()));

        // If the slider wasn't dragged to zero, add the new move
        if (newMove.armies() > 0) {
            queuedMoves.add(newMove);
        }
    }

    public boolean areAllPlayersReady() {
        return !players.isEmpty() && readyPlayers.size() >= players.size();
    }

    public void resetReadyStates() {
        readyPlayers.clear();
    }

    // --- Getters and Setters for Jackson ---

    public List<Player> getPlayers() { return players; }
    public void setPlayers(List<Player> players) { this.players = players; }

    public List<Province> getProvinces() { return provinces; }
    public void setProvinces(List<Province> provinces) { this.provinces = provinces; }

    public List<Move> getQueuedMoves() { return queuedMoves; }
    public void setQueuedMoves(List<Move> queuedMoves) { this.queuedMoves = queuedMoves; }

    public void setPlayerReady(String playerId) {
        readyPlayers.add(playerId);
    }

    public GamePhase getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(GamePhase currentPhase) { this.currentPhase = currentPhase; }

    public int getDraftArmies(String playerId) {
        return draftPools.getOrDefault(playerId, 0);
    }
}