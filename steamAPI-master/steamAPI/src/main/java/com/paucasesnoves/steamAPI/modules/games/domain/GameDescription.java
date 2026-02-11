package com.paucasesnoves.steamAPI.modules.games.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "game_descriptions")
public class GameDescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Lob
    private String detailedDescription;

    @Lob
    private String aboutTheGame;

    @Lob
    private String shortDescription;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
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
