package com.paucasesnoves.steamAPI.modules.games.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public class GameCreateDTO {
    private String title;
    private LocalDate releaseDate;
    private Boolean english;
    private Integer minAge;
    private Integer achievements;
    private Integer positiveRatings;
    private Integer negativeRatings;
    private Double avgPlaytime;
    private Double medianPlaytime;
    private Integer ownersLower;
    private Integer ownersUpper;
    private Integer ownersMid;
    private BigDecimal price;

    // Relaciones
    private Long categoryId;
    private Set<Long> developerIds;  // Cambiado de developerId
    private Set<Long> publisherIds;   // También para consistencia
    private Set<Long> genreIds;
    private Set<Long> platformIds;
    private Set<Long> tagIds;

    // Constructores
    public GameCreateDTO() {}

    // Getters y Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Boolean getEnglish() {
        return english;
    }

    public void setEnglish(Boolean english) {
        this.english = english;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public void setMinAge(Integer minAge) {
        this.minAge = minAge;
    }

    public Integer getAchievements() {
        return achievements;
    }

    public void setAchievements(Integer achievements) {
        this.achievements = achievements;
    }

    public Integer getPositiveRatings() {
        return positiveRatings;
    }

    public void setPositiveRatings(Integer positiveRatings) {
        this.positiveRatings = positiveRatings;
    }

    public Integer getNegativeRatings() {
        return negativeRatings;
    }

    public void setNegativeRatings(Integer negativeRatings) {
        this.negativeRatings = negativeRatings;
    }

    public Double getAvgPlaytime() {
        return avgPlaytime;
    }

    public void setAvgPlaytime(Double avgPlaytime) {
        this.avgPlaytime = avgPlaytime;
    }

    public Double getMedianPlaytime() {
        return medianPlaytime;
    }

    public void setMedianPlaytime(Double medianPlaytime) {
        this.medianPlaytime = medianPlaytime;
    }

    public Integer getOwnersLower() {
        return ownersLower;
    }

    public void setOwnersLower(Integer ownersLower) {
        this.ownersLower = ownersLower;
    }

    public Integer getOwnersUpper() {
        return ownersUpper;
    }

    public void setOwnersUpper(Integer ownersUpper) {
        this.ownersUpper = ownersUpper;
    }

    public Integer getOwnersMid() {
        return ownersMid;
    }

    public void setOwnersMid(Integer ownersMid) {
        this.ownersMid = ownersMid;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Set<Long> getDeveloperIds() {
        return developerIds;
    }

    public void setDeveloperIds(Set<Long> developerIds) {
        this.developerIds = developerIds;
    }

    public Set<Long> getPublisherIds() {
        return publisherIds;
    }

    public void setPublisherIds(Set<Long> publisherIds) {
        this.publisherIds = publisherIds;
    }

    public Set<Long> getGenreIds() {
        return genreIds;
    }

    public void setGenreIds(Set<Long> genreIds) {
        this.genreIds = genreIds;
    }

    public Set<Long> getPlatformIds() {
        return platformIds;
    }

    public void setPlatformIds(Set<Long> platformIds) {
        this.platformIds = platformIds;
    }

    public Set<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(Set<Long> tagIds) {
        this.tagIds = tagIds;
    }
}