package com.paucasesnoves.steamAPI.modules.games.domain;
import jakarta.persistence.*;


@Entity
@Table(name = "game_requirements")
public class GameRequirements {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Lob
    private String pcRequirements;

    @Lob
    private String macRequirements;

    @Lob
    private String linuxRequirements;

    @Lob
    private String minimum;

    @Lob
    private String recommended;

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
