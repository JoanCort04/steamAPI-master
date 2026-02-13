package com.paucasesnoves.steamAPI.modules.csv.dto;

public class CsvImportResultDto {

    private String status;
    private int importedGames;
    private int developers;
    private int publishers;
    private int genres;
    private int tags;
    private int skippedLines;
    private double durationSeconds;

    public CsvImportResultDto() {}

    public CsvImportResultDto(String status, int importedGames, int developers,
                              int publishers, int genres, int tags,
                              int skippedLines, double durationSeconds) {
        this.status = status;
        this.importedGames = importedGames;
        this.developers = developers;
        this.publishers = publishers;
        this.genres = genres;
        this.tags = tags;
        this.skippedLines = skippedLines;
        this.durationSeconds = durationSeconds;
    }

    public int getDevelopers() {
        return developers;
    }

    public void setDevelopers(int developers) {
        this.developers = developers;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getGenres() {
        return genres;
    }

    public void setGenres(int genres) {
        this.genres = genres;
    }

    public int getImportedGames() {
        return importedGames;
    }

    public void setImportedGames(int importedGames) {
        this.importedGames = importedGames;
    }

    public int getPublishers() {
        return publishers;
    }

    public void setPublishers(int publishers) {
        this.publishers = publishers;
    }

    public int getSkippedLines() {
        return skippedLines;
    }

    public void setSkippedLines(int skippedLines) {
        this.skippedLines = skippedLines;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTags() {
        return tags;
    }

    public void setTags(int tags) {
        this.tags = tags;
    }
}

