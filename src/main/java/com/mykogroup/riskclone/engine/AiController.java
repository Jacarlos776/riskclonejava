package com.mykogroup.riskclone.engine;

import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Province;

import java.util.*;

public class AiController {

    private final AdjacencyService adjacencyService;
    private final Random random = new Random();

    public AiController(AdjacencyService adjacencyService) {
        this.adjacencyService = adjacencyService;
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
