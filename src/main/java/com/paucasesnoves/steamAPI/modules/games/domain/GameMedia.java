package com.paucasesnoves.steamAPI.modules.games.domain;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "game_media")
public class GameMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    private String headerImage;

    @Lob
    private String background;

    @ElementCollection
    @CollectionTable(name = "game_screenshots", joinColumns = @JoinColumn(name = "game_media_id"))
    @Column(name = "screenshot")
    private List<String> screenshots = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "game_movies", joinColumns = @JoinColumn(name = "game_media_id"))
    @Column(name = "movie")
    private List<String> movies = new ArrayList<>();

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

    public String getHeaderImage() {
        return headerImage;
    }

    public void setHeaderImage(String headerImage) {
        this.headerImage = headerImage;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public List<String> getScreenshots() {
        return screenshots;
    }

    public void setScreenshots(List<String> screenshots) {
        this.screenshots = screenshots;
    }

    public List<String> getMovies() {
        return movies;
    }

    public void setMovies(List<String> movies) {
        this.movies = movies;
    }
}
