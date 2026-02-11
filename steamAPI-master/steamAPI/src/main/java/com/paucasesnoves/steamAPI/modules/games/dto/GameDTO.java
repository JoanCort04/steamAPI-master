package com.paucasesnoves.steamAPI.modules.games.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public class GameDTO {
    private Long id;
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

    // Relaciones como objetos
    private CategoryDTO category;
    private Set<DeveloperDTO> developers;  // Cambiado de DeveloperDTO
    private Set<PublisherDTO> publishers;   // También para consistencia
    private Set<GenreDTO> genres;
    private Set<PlatformDTO> platforms;
    private Set<TagDTO> tags;

    // Constructores
    public GameDTO() {}

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public CategoryDTO getCategory() {
        return category;
    }

    public void setCategory(CategoryDTO category) {
        this.category = category;
    }

    public Set<DeveloperDTO> getDevelopers() {
        return developers;
    }

    public void setDevelopers(Set<DeveloperDTO> developers) {
        this.developers = developers;
    }

    public Set<PublisherDTO> getPublishers() {
        return publishers;
    }

    public void setPublishers(Set<PublisherDTO> publishers) {
        this.publishers = publishers;
    }

    public Set<GenreDTO> getGenres() {
        return genres;
    }

    public void setGenres(Set<GenreDTO> genres) {
        this.genres = genres;
    }

    public Set<PlatformDTO> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(Set<PlatformDTO> platforms) {
        this.platforms = platforms;
    }

    public Set<TagDTO> getTags() {
        return tags;
    }

    public void setTags(Set<TagDTO> tags) {
        this.tags = tags;
    }
}