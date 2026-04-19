package com.mykogroup.riskclone.engine;

import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Province;

import java.util.*;

public class AiController {
    // --- AI BALANCING WEIGHTS ---
    private final double WEIGHT_BORDER_DEFENSE = 5.0;  // Flat bonus for touching an enemy
    private final double WEIGHT_THREAT_DIFF = 2.0;     // Multiplier for how badly outnumbered they are
    private final double WEIGHT_REGION_SYNERGY = 1.5;  // Bonus per province owned in the same region

    private final AdjacencyService adjacencyService;
    private final Random random = new Random();

    public AiController(AdjacencyService adjacencyService) {
        this.adjacencyService = adjacencyService;
    }

    public String getBestDraftTarget(GameState state, String aiPlayerId) {
        List<Province> myProvinces = state.getProvinces().stream()
                .filter(p -> aiPlayerId.equals(p.getOwnerId())).toList();

        if (myProvinces.isEmpty()) return null;

        String bestProvinceId = myProvinces.getFirst().getId();
        double maxScore = -1;

        for (Province p : myProvinces) {
            double score = 0;

            // 1. Analyze Borders & Threats
            boolean isBorder = false;
            int maxEnemyThreat = 0;

            for (String neighborId : adjacencyService.getNeighbors(p.getId())) {
                Optional<Province> nOpt = state.getProvince(neighborId);
                if (nOpt.isPresent()) {
                    Province neighbor = nOpt.get();
                    if (neighbor.getOwnerId() != null && !neighbor.getOwnerId().equals(aiPlayerId)) {
                        isBorder = true;
                        if (neighbor.getArmyCount() > maxEnemyThreat) {
                            maxEnemyThreat = neighbor.getArmyCount();
                        }
                    }
                }
            }

            if (isBorder) {
                score += WEIGHT_BORDER_DEFENSE;

                // If the enemy has more troops, this becomes an emergency!
                if (maxEnemyThreat > p.getArmyCount()) {
                    int deficit = maxEnemyThreat - p.getArmyCount();
                    score += (deficit * WEIGHT_THREAT_DIFF);
                }
            }

            // 2. Analyze Region Synergy
            for (com.mykogroup.riskclone.model.Region r : state.getRegions()) {
                if (r.getProvinces().contains(p.getId())) {
                    // Count how many provinces in this region the AI owns
                    long ownedInRegion = r.getProvinces().stream()
                            .map(state::getProvince)
                            .filter(opt -> opt.isPresent() && aiPlayerId.equals(opt.get().getOwnerId()))
                            .count();

                    score += (ownedInRegion * WEIGHT_REGION_SYNERGY);
                    break; // Province only belongs to one region
                }
            }

            // 3. Add a tiny random variance (0.0 to 0.5) to break exact ties
            // and make the AI slightly less predictable
            score += (random.nextDouble() * 0.5);

            if (score > maxScore) {
                maxScore = score;
                bestProvinceId = p.getId();
            }
        }

        return bestProvinceId;
    }

    public void takeClaimingTurn(GameState state, String aiPlayerId) {
        List<Province> unclaimed = state.getUnclaimedProvinces();
        List<Province> claimed = state.getClaimedProvinces();

        if (unclaimed.isEmpty()) return;

        String chosenId;

        if (claimed.isEmpty()) {
            // First player on the board? Pick completely randomly.
            chosenId = unclaimed.get(random.nextInt(unclaimed.size())).getId();
        } else {
            // --- Multi-Source BFS Pathfinding ---
            Map<String, Integer> distances = new HashMap<>();
            Queue<String> queue = new LinkedList<>();

            // Start the search from EVERY claimed province
            for (Province c : claimed) {
                queue.add(c.getId());
                distances.put(c.getId(), 0);
            }

            // Flood fill the map to find shortest distances
            while (!queue.isEmpty()) {
                String curr = queue.poll();
                int currentDist = distances.get(curr);

                for (String neighbor : adjacencyService.getNeighbors(curr)) {
                    if (!distances.containsKey(neighbor)) {
                        distances.put(neighbor, currentDist + 1);
                        queue.add(neighbor);
                    }
                }
            }

            // Score the empty provinces
            int maxDistance = 0;
            List<String> bestChoices = new ArrayList<>();

            for (Province p : unclaimed) {
                // If a province is completely isolated (no neighbors), default to a high distance
                int dist = distances.getOrDefault(p.getId(), 999);

                if (dist > maxDistance) {
                    maxDistance = dist;
                    bestChoices.clear();
                    bestChoices.add(p.getId());
                } else if (dist == maxDistance) {
                    bestChoices.add(p.getId());
                }
            }

            // Pick a random province from the furthest available tier
            chosenId = bestChoices.get(random.nextInt(bestChoices.size()));
        }

        // Lock it in!
        state.claimStartingProvince(aiPlayerId, chosenId);
        System.out.println("AI " + aiPlayerId + " claimed " + chosenId);
    }
}
