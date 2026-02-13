package com.paucasesnoves.steamAPI.modules.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class SteamGameSearchFormDTO {

    @Size(max = 80, message = "El nom no pot superar els 80 caràcters")
    private String name;

    @DecimalMin(value = "0.0", inclusive = true, message = "El preu màxim ha de ser un número positiu")
    private BigDecimal maxPrice;

    private String genre;

    // Getters i setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
}