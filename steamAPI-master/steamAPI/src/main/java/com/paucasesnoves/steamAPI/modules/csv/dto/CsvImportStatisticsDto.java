package com.paucasesnoves.steamAPI.modules.csv.dto;

public class CsvImportStatisticsDto {
    private int processed;
    private int created;
    private int skipped;
    private int developersCreated;
    private int publishersCreated;
    private int genresCreated;
    private int platformsCreated;
    private int categoriesCreated;
    private int tagsCreated;

    // Constructor vacío
    public CsvImportStatisticsDto() {}

    // Getters y Setters
    public int getProcessed() { return processed; }
    public void setProcessed(int processed) { this.processed = processed; }
    public void incrementProcessed() { this.processed++; }

    public int getCreated() { return created; }
    public void setCreated(int created) { this.created = created; }
    public void incrementCreated() { this.created++; }

    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public void incrementSkipped() { this.skipped++; }

    public int getDevelopersCreated() { return developersCreated; }
    public void setDevelopersCreated(int developersCreated) { this.developersCreated = developersCreated; }
    public void incrementDevelopersCreated() { this.developersCreated++; }

    public int getPublishersCreated() { return publishersCreated; }
    public void setPublishersCreated(int publishersCreated) { this.publishersCreated = publishersCreated; }
    public void incrementPublishersCreated() { this.publishersCreated++; }

    public int getGenresCreated() { return genresCreated; }
    public void setGenresCreated(int genresCreated) { this.genresCreated = genresCreated; }
    public void incrementGenresCreated() { this.genresCreated++; }

    public int getPlatformsCreated() { return platformsCreated; }
    public void setPlatformsCreated(int platformsCreated) { this.platformsCreated = platformsCreated; }
    public void incrementPlatformsCreated() { this.platformsCreated++; }

    public int getCategoriesCreated() { return categoriesCreated; }
    public void setCategoriesCreated(int categoriesCreated) { this.categoriesCreated = categoriesCreated; }
    public void incrementCategoriesCreated() { this.categoriesCreated++; }

    public int getTagsCreated() { return tagsCreated; }
    public void setTagsCreated(int tagsCreated) { this.tagsCreated = tagsCreated; }
    public void incrementTagsCreated() { this.tagsCreated++; }
}