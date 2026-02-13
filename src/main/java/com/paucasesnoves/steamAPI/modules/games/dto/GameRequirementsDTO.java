package com.paucasesnoves.steamAPI.modules.games.dto;

public class GameRequirementsDTO {
    private String pcRequirements;
    private String macRequirements;
    private String linuxRequirements;
    private String minimum;
    private String recommended;

    public String getPcRequirements() {
        return pcRequirements;
    }

    public void setPcRequirements(String pcRequirements) {
        this.pcRequirements = pcRequirements;
    }

    public String getMacRequirements() {
        return macRequirements;
    }

    public void setMacRequirements(String macRequirements) {
        this.macRequirements = macRequirements;
    }

    public String getLinuxRequirements() {
        return linuxRequirements;
    }

    public void setLinuxRequirements(String linuxRequirements) {
        this.linuxRequirements = linuxRequirements;
    }

    public String getMinimum() {
        return minimum;
    }

    public void setMinimum(String minimum) {
        this.minimum = minimum;
    }

    public String getRecommended() {
        return recommended;
    }

    public void setRecommended(String recommended) {
        this.recommended = recommended;
    }
}