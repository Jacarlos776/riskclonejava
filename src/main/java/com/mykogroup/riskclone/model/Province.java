package com.mykogroup.riskclone.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Province {

    private String id;
    private String ownerId; // If null, the province is neutral/unoccupied
    private int armyCount;

    // Default constructor for Jackson
    public Province() {}

    public Province(String id, String ownerId, int armyCount) {
        this.id = id;
        this.ownerId = ownerId;
        this.armyCount = armyCount;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public int getArmyCount() { return armyCount; }
    public void setArmyCount(int armyCount) { this.armyCount = armyCount; }
}