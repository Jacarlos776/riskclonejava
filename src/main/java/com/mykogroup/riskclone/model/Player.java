package com.mykogroup.riskclone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Player {

    private String id;
    private String displayName;
    private boolean isAi;

    // Jackson REQUIRES a default, no-argument constructor to deserialize JSON into objects.
    public Player() {}

    public Player(String id, String displayName, boolean isAi) {
        this.id = id;
        this.displayName = displayName;
        this.isAi = isAi;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isAi() { return isAi; }
    public void setAi(boolean ai) { isAi = ai; }
}