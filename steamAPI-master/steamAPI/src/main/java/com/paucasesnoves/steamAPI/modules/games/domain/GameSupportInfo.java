package com.paucasesnoves.steamAPI.modules.games.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "game_support_info")
public class GameSupportInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "game_id", nullable = false, unique = true) // 🔥 Aseguramos 1:1 real
    private Game game;

    @Column(length = 1024)  // URLs largas de Steam (hasta 1024 caracteres)
    private String website;

    @Column(length = 1024)  // support_url también larga
    private String supportUrl;

    @Column(length = 512)   // emails no suelen pasar de 512
    private String supportEmail;

    // Constructores, getters y setters...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getSupportUrl() { return supportUrl; }
    public void setSupportUrl(String supportUrl) { this.supportUrl = supportUrl; }

    public String getSupportEmail() { return supportEmail; }
    public void setSupportEmail(String supportEmail) { this.supportEmail = supportEmail; }
}