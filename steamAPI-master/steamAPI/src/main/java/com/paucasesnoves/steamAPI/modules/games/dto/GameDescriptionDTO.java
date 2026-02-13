
package com.paucasesnoves.steamAPI.modules.games.dto;

public class GameDescriptionDTO {
    private String detailedDescription;
    private String aboutTheGame;
    private String shortDescription;

    // Constructores, getters y setters
    public GameDescriptionDTO() {}
    public GameDescriptionDTO(String detailedDescription, String aboutTheGame, String shortDescription) {
        this.detailedDescription = detailedDescription;
        this.aboutTheGame = aboutTheGame;
        this.shortDescription = shortDescription;
    }

    public String getDetailedDescription() {
        return detailedDescription;
    }

    public void setDetailedDescription(String detailedDescription) {
        this.detailedDescription = detailedDescription;
    }

    public String getAboutTheGame() {
        return aboutTheGame;
    }

    public void setAboutTheGame(String aboutTheGame) {
        this.aboutTheGame = aboutTheGame;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }
}