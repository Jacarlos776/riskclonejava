package com.mykogroup.riskclone.model;

import java.util.List;

public class Region {
    private String name;
    private int bonusArmies;
    private List<String> provinces;

    // Default constructor for Jackson
    public Region() {}

    public Region(String name, int bonusArmies, List<String> provinces) {
        this.name = name;
        this.bonusArmies = bonusArmies;
        this.provinces = provinces;
    }

    public String getName() { return name; }
    public int getBonusArmies() { return bonusArmies; }
    public List<String> getProvinces() { return provinces; }
}