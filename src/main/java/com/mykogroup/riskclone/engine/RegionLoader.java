package com.mykogroup.riskclone.engine;

import com.mykogroup.riskclone.model.Region;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class RegionLoader {

    public static List<Region> loadRegions(String resourcePath) {
        try (InputStream is = RegionLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalArgumentException("Cannot find JSON: " + resourcePath);

            ObjectMapper mapper = new ObjectMapper();

            // Tells Jackson to parse the JSON array into a List of Region objects
            return mapper.readValue(is, new TypeReference<List<Region>>() {});

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}