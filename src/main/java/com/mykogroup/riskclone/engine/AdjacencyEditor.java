package com.mykogroup.riskclone.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mykogroup.riskclone.model.Region;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Mutable wrapper around adjacency data used by the Map Editor.
 * All toggles are kept symmetric — if A→B is added, B→A is added too.
 */
public class AdjacencyEditor {

    private final Map<String, List<String>> adjacencyMap;
    private final List<Region> regions;
    private boolean dirty = false;

    public AdjacencyEditor(Map<String, List<String>> adjacencyMap, List<Region> regions) {
        // Ensure all values are mutable lists
        this.adjacencyMap = new LinkedHashMap<>();
        adjacencyMap.forEach((k, v) -> this.adjacencyMap.put(k, new ArrayList<>(v)));
        this.regions = regions;
    }

    // --- Queries ---

    public Set<String> getNeighbors(String id) {
        List<String> neighbors = adjacencyMap.get(id);
        return neighbors == null ? Collections.emptySet() : new LinkedHashSet<>(neighbors);
    }

    public boolean areAdjacent(String a, String b) {
        List<String> neighbors = adjacencyMap.get(a);
        return neighbors != null && neighbors.contains(b);
    }

    /** Returns the name of the region containing this province, or "None". */
    public String findRegion(String provinceId) {
        for (Region r : regions) {
            if (r.getProvinces().contains(provinceId)) return r.getName();
        }
        return "None";
    }

    public List<Region> getRegions() {
        return Collections.unmodifiableList(regions);
    }

    public Set<String> getAllProvinceIds() {
        return Collections.unmodifiableSet(adjacencyMap.keySet());
    }

    /**
     * Scans for asymmetric adjacencies: A lists B but B does not list A.
     * Returns a list of strings describing each issue, e.g. "PH-CEB → PH-BOH (but not reverse)".
     */
    public List<String> getAsymmetricAdjacencies() {
        List<String> issues = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : adjacencyMap.entrySet()) {
            String a = entry.getKey();
            for (String b : entry.getValue()) {
                List<String> bNeighbors = adjacencyMap.get(b);
                if (bNeighbors == null || !bNeighbors.contains(a)) {
                    issues.add(a + " → " + b + " (not reciprocated)");
                }
            }
        }
        return issues;
    }

    // --- Mutations ---

    /**
     * Toggles the adjacency between a and b symmetrically.
     * @return true if the adjacency was ADDED, false if it was REMOVED.
     */
    public boolean toggleAdjacency(String a, String b) {
        if (areAdjacent(a, b)) {
            removeAdjacency(a, b);
            removeAdjacency(b, a);
            dirty = true;
            return false;
        } else {
            addAdjacency(a, b);
            addAdjacency(b, a);
            dirty = true;
            return true;
        }
    }

    /** Fix all asymmetric adjacencies by adding the missing reverse direction. */
    public void fixAllAsymmetries() {
        List<String[]> toAdd = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : adjacencyMap.entrySet()) {
            String a = entry.getKey();
            for (String b : entry.getValue()) {
                List<String> bNeighbors = adjacencyMap.get(b);
                if (bNeighbors == null || !bNeighbors.contains(a)) {
                    toAdd.add(new String[]{b, a});
                }
            }
        }
        for (String[] pair : toAdd) {
            addAdjacency(pair[0], pair[1]);
        }
        if (!toAdd.isEmpty()) dirty = true;
    }

    // --- Persistence ---

    public boolean isDirty() { return dirty; }

    /**
     * Saves the adjacency map back to the source file in src/main/resources.
     * Resolves the path by walking up from target/classes to the project root.
     */
    public void save(Class<?> loaderClass, String resourcePath) throws IOException {
        Path srcFile = resolveSourcePath(loaderClass, resourcePath);
        if (srcFile == null || !Files.exists(srcFile)) {
            throw new IOException("Cannot locate source file for: " + resourcePath
                    + "\nResolved to: " + srcFile
                    + "\nAre you running from source via mvnw javafx:run?");
        }

        // Sort keys for readable output
        Map<String, List<String>> sorted = new TreeMap<>(adjacencyMap);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(srcFile.toFile(), sorted);

        dirty = false;
    }

    // --- Private helpers ---

    private void addAdjacency(String from, String to) {
        adjacencyMap.computeIfAbsent(from, k -> new ArrayList<>());
        if (!adjacencyMap.get(from).contains(to)) {
            adjacencyMap.get(from).add(to);
        }
    }

    private void removeAdjacency(String from, String to) {
        List<String> neighbors = adjacencyMap.get(from);
        if (neighbors != null) neighbors.remove(to);
    }

    private Path resolveSourcePath(Class<?> clazz, String resourcePath) {
        try {
            URL loc = clazz.getProtectionDomain().getCodeSource().getLocation();
            // target/classes → target → project root
            Path projectRoot = Paths.get(loc.toURI()).getParent().getParent();
            String relative = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            return projectRoot.resolve("src/main/resources").resolve(relative);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
