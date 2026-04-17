package com.mykogroup.riskclone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameState {

    private List<Player> players = new ArrayList<>();
    private List<Province> provinces = new ArrayList<>();
    private List<Move> queuedMoves = new ArrayList<>();

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

    // --- Getters and Setters for Jackson ---

    public List<Player> getPlayers() { return players; }
    public void setPlayers(List<Player> players) { this.players = players; }

    public List<Province> getProvinces() { return provinces; }
    public void setProvinces(List<Province> provinces) { this.provinces = provinces; }

    public List<Move> getQueuedMoves() { return queuedMoves; }
    public void setQueuedMoves(List<Move> queuedMoves) { this.queuedMoves = queuedMoves; }
}