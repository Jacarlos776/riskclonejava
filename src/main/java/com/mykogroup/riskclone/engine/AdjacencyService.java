package com.mykogroup.riskclone.engine;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
}