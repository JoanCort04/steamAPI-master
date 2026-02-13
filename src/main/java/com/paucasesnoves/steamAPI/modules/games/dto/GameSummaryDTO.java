package com.paucasesnoves.steamAPI.modules.games.dto;

import java.math.BigDecimal;
import java.util.List;

public class GameSummaryDTO {
    private Long appId;
    private String title;
    private BigDecimal price;
    private List<String> developers;  // Ahora es List<String>
    private List<String> genres;       // Ahora es List<String>

    // Constructor por defecto
    public GameSummaryDTO() {}

    // Constructor con parámetros (opcional)
    public GameSummaryDTO(Long appId, String title, BigDecimal price,
                          List<String> developers, List<String> genres) {
        this.appId = appId;
        this.title = title;
        this.price = price;
        this.developers = developers;
        this.genres = genres;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public List<String> getDevelopers() {
        return developers;
    }

    public void setDevelopers(List<String> developers) {
        this.developers = developers;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }
}