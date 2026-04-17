package com.mykogroup.riskclone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Move(
        @JsonProperty("playerId") String playerId,
        @JsonProperty("fromId") String fromId,
        @JsonProperty("toId") String toId,
        @JsonProperty("armies") int armies
) {
    // A record automatically generates a constructor, getters, equals(), hashCode(), and toString().
    // We only need to define custom logic if we want strict validation right at creation.
    public Move {
        if (armies <= 0) {
            throw new IllegalArgumentException("Must move at least 1 army.");
        }
    }
}