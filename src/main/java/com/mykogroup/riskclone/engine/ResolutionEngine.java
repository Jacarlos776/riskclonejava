package com.mykogroup.riskclone.engine;

import com.mykogroup.riskclone.model.GameState;
import com.mykogroup.riskclone.model.Move;
import com.mykogroup.riskclone.model.Province;

import java.util.*;

public class ResolutionEngine {

    // A mutable wrapper for processing combat without breaking the pure state records
    private static class MarchingArmy {
        String playerId;
        String fromId;
        String toId;
        int armies;

        MarchingArmy(Move move) {
            this.playerId = move.playerId();
            this.fromId = move.fromId();
            this.toId = move.toId();
            this.armies = move.armies();
        }
    }

    public void processTurn(GameState state) {
        List<MarchingArmy> activeArmies = new ArrayList<>();
        for (Move m : state.getQueuedMoves()) {
            activeArmies.add(new MarchingArmy(m));
        }

        // ---------------------------------------------------------
        // PHASE 1: DEPARTURES
        // ---------------------------------------------------------
        for (MarchingArmy army : activeArmies) {
            state.getProvince(army.fromId).ifPresent(p -> {
                p.setArmyCount(p.getArmyCount() - army.armies);
            });
        }

        // ---------------------------------------------------------
        // PHASE 2: CROSSFIRES (Head-to-Head on the road)
        // ---------------------------------------------------------
        for (int i = 0; i < activeArmies.size(); i++) {
            for (int j = i + 1; j < activeArmies.size(); j++) {
                MarchingArmy a1 = activeArmies.get(i);
                MarchingArmy a2 = activeArmies.get(j);

                // If they are crossing paths and belong to different players
                if (a1.fromId.equals(a2.toId) && a1.toId.equals(a2.fromId) && !a1.playerId.equals(a2.playerId)) {

                    // TODO: Future Variance Hook
                    // int a1Effective = a1.armies + (int)(a1.armies * randomVariance);
                    // int a2Effective = a2.armies + (int)(a2.armies * randomVariance);

                    if (a1.armies > a2.armies) {
                        a1.armies -= a2.armies;
                        a2.armies = 0;
                    } else if (a2.armies > a1.armies) {
                        a2.armies -= a1.armies;
                        a1.armies = 0;
                    } else { // Tie
                        a1.armies = 0;
                        a2.armies = 0;
                    }
                }
            }
        }
        // Remove wiped-out armies
        activeArmies.removeIf(a -> a.armies <= 0);

        // ---------------------------------------------------------
        // PHASE 3: CONVERGENCE & CLASHES
        // ---------------------------------------------------------
        // Group remaining marching armies by their destination
        Map<String, List<MarchingArmy>> arrivals = new HashMap<>();
        for (MarchingArmy a : activeArmies) {
            arrivals.computeIfAbsent(a.toId, k -> new ArrayList<>()).add(a);
        }

        for (Province p : state.getProvinces()) {
            List<MarchingArmy> arrivingHere = arrivals.getOrDefault(p.getId(), Collections.emptyList());
            if (arrivingHere.isEmpty()) continue; // Nobody is moving here

            // Pool all forces present in the province by Player ID
            Map<String, Integer> forcesByPlayer = new HashMap<>();

            // Add the defending garrison (if any)
            if (p.getOwnerId() != null && p.getArmyCount() > 0) {
                forcesByPlayer.put(p.getOwnerId(), p.getArmyCount());
            }

            // Add all arriving attackers/reinforcements
            for (MarchingArmy a : arrivingHere) {
                forcesByPlayer.put(a.playerId, forcesByPlayer.getOrDefault(a.playerId, 0) + a.armies);
            }

            // If only one player has troops here (Peaceful transfer/reinforcement)
            if (forcesByPlayer.size() == 1) {
                String singlePlayer = forcesByPlayer.keySet().iterator().next();
                p.setOwnerId(singlePlayer);
                p.setArmyCount(forcesByPlayer.get(singlePlayer));
                continue;
            }

            // ---------------------------------------------------------
            // PHASE 4: TIE-BREAKERS & CASUALTIES
            // ---------------------------------------------------------
            // Sort players by army size (Descending)
            List<Map.Entry<String, Integer>> sortedForces = new ArrayList<>(forcesByPlayer.entrySet());
            sortedForces.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

            var firstPlace = sortedForces.get(0);
            var secondPlace = sortedForces.get(1);

            if (firstPlace.getValue().equals(secondPlace.getValue())) {
                // It's a Tie!
                String originalOwner = p.getOwnerId();

                if (forcesByPlayer.containsKey(originalOwner) && forcesByPlayer.get(originalOwner).equals(firstPlace.getValue())) {
                    // Defender tied for first place. Defender's Advantage wins with 1 survivor.
                    p.setArmyCount(1);
                    // Owner remains the same
                } else {
                    // Mutual destruction between attackers, or nobody owned it to begin with.
                    p.setOwnerId(null);
                    p.setArmyCount(0);
                }
            } else {
                // Clear Winner
                p.setOwnerId(firstPlace.getKey());
                // Winner loses troops equal to the second-largest army
                p.setArmyCount(firstPlace.getValue() - secondPlace.getValue());
            }
        }

        // ---------------------------------------------------------
        // CLEANUP
        // ---------------------------------------------------------
        state.clearQueuedMoves();
        state.resetReadyStates();
    }
}