package com.mykogroup.riskclone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameState {

    public enum GamePhase { DRAFTING, PLANNING, RESOLUTION, CLAIMING }

    private GamePhase currentPhase = GamePhase.DRAFTING;
    private final Map<String, Integer> draftPools = new HashMap<>();

    private List<Player> players = new ArrayList<>();
    private List<Province> provinces = new ArrayList<>();
    private List<Move> queuedMoves = new ArrayList<>();
    private final Set<String> readyPlayers = new HashSet<>();
    private List<Region> regions = new ArrayList<>();

    // Default constructor for Jackson
    public GameState() {}

    // --- Core State Manipulations ---

    public boolean claimStartingProvince(String playerId, String provinceId) {
        Optional<Province> target = getProvince(provinceId);

        // Rule 1: Cannot claim a province that an enemy has already locked in
        if (target.isEmpty() || (target.get().getOwnerId() != null && !target.get().getOwnerId().equals(playerId))) {
            return false;
        }

        // Rule 2: Unclaim any previously clicked province by this player
        provinces.stream()
                .filter(p -> playerId.equals(p.getOwnerId()))
                .forEach(p -> {
                    p.setOwnerId(null);
                    p.setArmyCount(0);
                });

        // Rule 3: Claim the new province and give it a starting garrison (e.g., 5 troops)
        target.get().setOwnerId(playerId);
        target.get().setArmyCount(5);

        return true;
    }

    // --- Calculate Draft Phase Incomes ---
    public int calculateDraftIncome(String playerId) {
        int baseIncome = 5; // Everyone gets a base of 5 troops
        int regionBonus = 0;

        // Loop through all regions on the map
        for (Region region : regions) {
            boolean ownsEntireRegion = true;

            // Check if the player owns every province in this specific region
            for (String provinceId : region.getProvinces()) {
                Optional<Province> p = getProvince(provinceId);

                // If the province is unowned, or owned by someone else, they fail the check
                if (p.isEmpty() || !playerId.equals(p.get().getOwnerId())) {
                    ownsEntireRegion = false;
                }
            }

            // If they survived the loop, they own it! Award the bonus.
            if (ownsEntireRegion) {
                regionBonus += region.getBonusArmies();
                System.out.println(playerId + " controls " + region.getName() + "! (+ " + region.getBonusArmies() + " armies)");
            }
        }

        // Province Count Bonus
        long provinceCount = provinces.stream()
                .filter(p -> playerId.equals(p.getOwnerId()))
                .count();

        int sprawlBonus = (int) (provinceCount / 2); // Each player gets a bonus 0.5 army per province they own rounded down

        int totalIncome = baseIncome + regionBonus + sprawlBonus;

        System.out.println(playerId + " Draft Income: Base(" + baseIncome + ") + Sprawl(" + sprawlBonus + ") + Regions(" + regionBonus + ") = " + totalIncome);
        return totalIncome;
    }

    // Call this at the start of every draft phase
    public void initDraftPools(int armiesPerPlayer) {
        for (Player p : players) {
            int earnedArmies = calculateDraftIncome(p.getId());
            draftPools.put(p.getId(), earnedArmies);
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
        List<Player> alivePlayers = getAlivePlayers();
        if (alivePlayers.isEmpty()) return false;

        // Check if every ALIVE player has clicked "End Turn"
        return alivePlayers.stream().allMatch(p -> readyPlayers.contains(p.getId()));
    }

    public void resetReadyStates() {
        readyPlayers.clear();
    }

    // --- Survival Checks ---
    public boolean isPlayerAlive(String playerId) {
        // Nobody is dead during the claiming phase
        if (currentPhase == GamePhase.CLAIMING) return true;

        // Otherwise, they must own at least 1 province to stay in the game
        return provinces.stream().anyMatch(p -> playerId.equals(p.getOwnerId()));
    }

    public List<Player> getAlivePlayers() {
        return players.stream().filter(p -> isPlayerAlive(p.getId())).toList();
    }

    // --- Getters and Setters for Jackson ---

    public List<Player> getPlayers() { return players; }
    public void setPlayers(List<Player> players) { this.players = players; }

    public List<Province> getProvinces() { return provinces; }
    public void setProvinces(List<Province> provinces) { this.provinces = provinces; }

    public List<Move> getQueuedMoves() { return queuedMoves; }
    public void setQueuedMoves(List<Move> queuedMoves) { this.queuedMoves = queuedMoves; }

    public void setRegions(List<Region> regions) { this.regions = regions; }

    public void setPlayerReady(String playerId) {
        readyPlayers.add(playerId);
    }

    public GamePhase getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(GamePhase currentPhase) { this.currentPhase = currentPhase; }

    public int getDraftArmies(String playerId) {
        return draftPools.getOrDefault(playerId, 0);
    }

    // --- AI Helpers ---
    public List<Province> getClaimedProvinces() {
        return provinces.stream().filter(p -> p.getOwnerId() != null).toList();
    }

    public List<Province> getUnclaimedProvinces() {
        return provinces.stream().filter(p -> p.getOwnerId() == null).toList();
    }
}