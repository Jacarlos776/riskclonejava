package com.mykogroup.riskclone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Player {

    private String id;
    private String displayName;

    // Jackson REQUIRES a default, no-argument constructor to deserialize JSON into objects.
    public Player() {}

    public Player(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}