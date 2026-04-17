package com.mykogroup.riskclone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameState {

    private List<Player> players = new ArrayList<>();
    private List<Province> provinces = new ArrayList<>();
    private List<Move> queuedMoves = new ArrayList<>();
    private Set<String> readyPlayers = new HashSet<>();

    // Default constructor for Jackson
    public GameState() {}

    // --- Core State Manipulations ---

    public void queueMove(Move move) {
        // Optional: Add basic sanity checks before allowing it into the queue.
        // E.g., Does the player actually own the 'fromId' province?
        // Do they have enough armies?
        // (Adjacency is already checked by the View layer, but the Engine should double-check it during resolution).
        queuedMoves.add(move);
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
}