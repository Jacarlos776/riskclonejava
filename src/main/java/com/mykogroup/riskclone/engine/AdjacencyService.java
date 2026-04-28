package com.mykogroup.riskclone.engine;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class AdjacencyService {

    private Map<String, List<String>> adjacencyMap;

    public AdjacencyService(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalArgumentException("Missing JSON: " + resourcePath);

            ObjectMapper mapper = new ObjectMapper();
            // Parses the JSON object into a Java Map
            adjacencyMap = mapper.readValue(is, new TypeReference<Map<String, List<String>>>() {});

        } catch (Exception e) {
            e.printStackTrace();
            adjacencyMap = Collections.emptyMap();
        }
    }

    // O(1) lookup
    public boolean areAdjacent(String provinceA, String provinceB) {
        List<String> neighbors = adjacencyMap.get(provinceA);
        return neighbors != null && neighbors.contains(provinceB);
    }

    // --- Expose neighbors for AI Pathfinding ---
    public List<String> getNeighbors(String provinceId) {
        return adjacencyMap.getOrDefault(provinceId, Collections.emptyList());
    }

    // Returns all province IDs mentioned in the adjacency file —
    // both as keys and as values in neighbour lists.
    // Used by GameServer to initialise provinces without needing JavaFX.
    public Set<String> getAllProvinceIds() {
        Set<String> ids = new HashSet<>(adjacencyMap.keySet());
        adjacencyMap.values().forEach(ids::addAll);
        return ids;
    }
}