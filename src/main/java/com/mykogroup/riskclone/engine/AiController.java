package com.mykogroup.riskclone.engine;

import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Province;

import java.util.*;

public class AiController {
    // --- AI DRAFTING BALANCING WEIGHTS ---
    private final double WEIGHT_BORDER_DEFENSE = 5.0;  // Flat bonus for touching an enemy
    private final double WEIGHT_THREAT_DIFF = 2.0;     // Multiplier for how badly outnumbered they are
    private final double WEIGHT_REGION_SYNERGY = 1.5;  // Bonus per province owned in the same region

    // --- AI PLANNING BALANCING WEIGHTS ---
    private final double WEIGHT_INTERIOR_PUSH = 20.0;    // Pushing troops to the front line
    private final double WEIGHT_OPPORTUNISM = 15.0;      // Attacking empty/weak provinces
    private final double WEIGHT_OVERWHELMING = 5.0;      // Bonus for having a massive numbers advantage
    private final double WEIGHT_SUICIDE_PENALTY = -50.0; // Penalty for attacking larger armies

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

    public void takePlanningTurn(GameState state, String aiPlayerId) {
        List<Province> myProvinces = state.getProvinces().stream()
                .filter(p -> aiPlayerId.equals(p.getOwnerId())).toList();

        for (Province source : myProvinces) {
            int currentArmies = source.getArmyCount();
            if (currentArmies <= 1) continue; // Cannot move if you only have 1 troop

            // --- 1. SMART GARRISONING ---
            // Calculate how many enemies are bordering this exact province
            int maxEnemyThreat = 0;
            boolean isBorder = false;

            for (String neighborId : adjacencyService.getNeighbors(source.getId())) {
                Optional<Province> nOpt = state.getProvince(neighborId);
                if (nOpt.isPresent() && nOpt.get().getOwnerId() != null && !nOpt.get().getOwnerId().equals(aiPlayerId)) {
                    isBorder = true;
                    if (nOpt.get().getArmyCount() > maxEnemyThreat) {
                        maxEnemyThreat = nOpt.get().getArmyCount();
                    }
                }
            }

            // The AI leaves enough troops to survive the biggest adjacent threat, plus 1 for safety.
            // If it's an interior province (no enemies), safeGarrison is just 1.
            int safeGarrison = isBorder ? (maxEnemyThreat + 1) : 1;

            // How many troops are actually allowed to march?
            int availableToMove = currentArmies - safeGarrison;

            // If we don't have a surplus, hunker down and don't move.
            if (availableToMove <= 0) continue;

            // --- TARGET SCORING ---
            String bestTargetId = null;
            double bestScore = -999;

            for (String neighborId : adjacencyService.getNeighbors(source.getId())) {
                Optional<Province> targetOpt = state.getProvince(neighborId);
                if (targetOpt.isEmpty()) continue;
                Province target = targetOpt.get();

                double score = 0;
                boolean isEnemy = target.getOwnerId() == null || !target.getOwnerId().equals(aiPlayerId);

                if (!isEnemy) {
                    // --- 2. INTERIOR CONSOLIDATION ---
                    // Moving to a friendly province. Is the target closer to the front lines?
                    boolean targetIsBorder = adjacencyService.getNeighbors(target.getId()).stream()
                            .map(state::getProvince)
                            .anyMatch(opt -> opt.isPresent() && opt.get().getOwnerId() != null && !opt.get().getOwnerId().equals(aiPlayerId));

                    if (!isBorder && targetIsBorder) {
                        // Massive bonus for moving from a safe interior to a dangerous border
                        score += WEIGHT_INTERIOR_PUSH;
                    } else if (!isBorder && !targetIsBorder) {
                        // Moving from interior to interior. Just shuffling troops randomly.
                        score += 1.0;
                    } else {
                        // Moving from border to border. Usually a bad idea, deduct points.
                        score -= 5.0;
                    }
                } else {
                    // --- ATTACKING ENEMIES ---
                    int enemyArmies = target.getArmyCount();

                    // 3. OPPORTUNISM (The Easy Snipe)
                    if (enemyArmies <= 2) {
                        score += WEIGHT_OPPORTUNISM;
                    }

                    // 4. OVERWHELMING FORCE vs COWARDICE
                    double forceRatio = (double) availableToMove / Math.max(1, enemyArmies);

                    if (forceRatio >= 1.5) {
                        // We outnumber them significantly!
                        score += (forceRatio * WEIGHT_OVERWHELMING);
                    } else if (forceRatio < 1.0) {
                        // They outnumber us. Do not attack unless absolutely desperate.
                        score += WEIGHT_SUICIDE_PENALTY;
                    }
                }

                // Add slight randomness to prevent repetitive loops
                score += (random.nextDouble() * 2.0);

                if (score > bestScore) {
                    bestScore = score;
                    bestTargetId = target.getId();
                }
            }

            // --- EXECUTE THE MOVE ---
            // If we found a target that is actually worth it (score > 0)
            if (bestTargetId != null && bestScore > 0) {
                // Queue the move in the master state!
                // Assuming Move constructor is: Move(playerId, fromId, toId, armyCount)
                state.setMove(new com.mykogroup.riskclone.model.Move(aiPlayerId, source.getId(), bestTargetId, availableToMove));
                System.out.println("AI " + aiPlayerId + " queuing move: " + availableToMove + " troops from " + source.getId() + " to " + bestTargetId);
            }
        }
    }
}
