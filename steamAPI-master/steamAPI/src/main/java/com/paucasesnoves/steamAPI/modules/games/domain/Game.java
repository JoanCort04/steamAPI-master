package com.paucasesnoves.steamAPI.modules.games.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "games")
public class Game {

    @Id
    @Column(name = "app_id", unique = true, nullable = false)
    private Long appId;  // ← ¡SIN @GeneratedValue!

    @Column(nullable = false)
    private String title;

    private LocalDate releaseDate;
    private boolean english;
    private Integer minAge;
    private Integer achievements;
    private Integer positiveRatings;
    private Integer negativeRatings;
    private Double avgPlaytime;
    private Double medianPlaytime;
    private Integer ownersLower;
    private Integer ownersUpper;
    private Integer ownersMid;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    // ========== RELACIONES MANY-TO-MANY ==========

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "game_genre",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> genres = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "game_tag",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "game_platform",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "platform_id")
    )
    private Set<Platform> platforms = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "game_developer",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "developer_id")
    )
    private Set<Developer> developers = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "game_publisher",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "publisher_id")
    )
    private Set<Publisher> publishers = new HashSet<>();

    // ========== RELACIÓN MANY-TO-ONE ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // ========== CONSTRUCTORES ==========

    public Game() {
        // Constructor vacío requerido por JPA
    }

    public Game(Long appId, String title) {
        this.appId = appId;
        this.title = title;
    }

    // ========== GETTERS Y SETTERS ==========

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public boolean isEnglish() { return english; }
    public void setEnglish(boolean english) { this.english = english; }

    public Integer getMinAge() { return minAge; }
    public void setMinAge(Integer minAge) { this.minAge = minAge; }

    public Integer getAchievements() { return achievements; }
    public void setAchievements(Integer achievements) { this.achievements = achievements; }

    public Integer getPositiveRatings() { return positiveRatings; }
    public void setPositiveRatings(Integer positiveRatings) { this.positiveRatings = positiveRatings; }

    public Integer getNegativeRatings() { return negativeRatings; }
    public void setNegativeRatings(Integer negativeRatings) { this.negativeRatings = negativeRatings; }

    public Double getAvgPlaytime() { return avgPlaytime; }
    public void setAvgPlaytime(Double avgPlaytime) { this.avgPlaytime = avgPlaytime; }

    public Double getMedianPlaytime() { return medianPlaytime; }
    public void setMedianPlaytime(Double medianPlaytime) { this.medianPlaytime = medianPlaytime; }

    public Integer getOwnersLower() { return ownersLower; }
    public void setOwnersLower(Integer ownersLower) { this.ownersLower = ownersLower; }

    public Integer getOwnersUpper() { return ownersUpper; }
    public void setOwnersUpper(Integer ownersUpper) { this.ownersUpper = ownersUpper; }

    public Integer getOwnersMid() { return ownersMid; }
    public void setOwnersMid(Integer ownersMid) { this.ownersMid = ownersMid; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Set<Genre> getGenres() { return genres; }
    public void setGenres(Set<Genre> genres) { this.genres = genres; }

    public Set<Tag> getTags() { return tags; }
    public void setTags(Set<Tag> tags) { this.tags = tags; }

    public Set<Platform> getPlatforms() { return platforms; }
    public void setPlatforms(Set<Platform> platforms) { this.platforms = platforms; }

    public Set<Developer> getDevelopers() { return developers; }
    public void setDevelopers(Set<Developer> developers) { this.developers = developers; }

    public Set<Publisher> getPublishers() { return publishers; }
    public void setPublishers(Set<Publisher> publishers) { this.publishers = publishers; }

    // ========== MÉTODOS UTILITARIOS ==========

    public void addGenre(Genre genre) {
        this.genres.add(genre);
    }

    public void addTag(Tag tag) {
        this.tags.add(tag);
    }

    public void addPlatform(Platform platform) {
        this.platforms.add(platform);
    }

    public void addDeveloper(Developer developer) {
        this.developers.add(developer);
    }

    public void addPublisher(Publisher publisher) {
        this.publishers.add(publisher);
    }

    @Override
    public String toString() {
        return "Game{" +
                "appId=" + appId +
                ", title='" + title + '\'' +
                ", releaseDate=" + releaseDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Game game = (Game) o;
        return appId != null ? appId.equals(game.appId) : game.appId == null;
    }

    @Override
    public int hashCode() {
        return appId != null ? appId.hashCode() : 0;
    }
}