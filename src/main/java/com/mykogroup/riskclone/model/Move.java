package com.mykogroup.riskclone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Move(
        @JsonProperty("playerId") String playerId,
        @JsonProperty("fromId") String fromId,
        @JsonProperty("toId") String toId,
        @JsonProperty("armies") int armies
) {}