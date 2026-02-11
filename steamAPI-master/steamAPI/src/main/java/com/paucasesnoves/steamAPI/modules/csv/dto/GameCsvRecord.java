package com.paucasesnoves.steamAPI.modules.csv.dto;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;

import java.math.BigDecimal;
import java.time.LocalDate;

public class GameCsvRecord {

    @CsvBindByName(column = "appid", required = true)
    private String appId;

    @CsvBindByName(column = "name")
    private String title;

    @CsvBindByName(column = "release_date")
    @CsvDate("yyyy-MM-dd")
    private LocalDate releaseDate;

    @CsvBindByName(column = "english")
    private String english;

    @CsvBindByName(column = "developer")
    private String developers;

    @CsvBindByName(column = "publisher")
    private String publishers;

    @CsvBindByName(column = "platforms")
    private String platforms;

    @CsvBindByName(column = "required_age")
    private String requiredAge;

    @CsvBindByName(column = "categories")
    private String categories;

    @CsvBindByName(column = "genres")
    private String genres;

    @CsvBindByName(column = "steamspy_tags")
    private String steamspyTags;

    @CsvBindByName(column = "achievements")
    private String achievements;

    @CsvBindByName(column = "positive_ratings")
    private String positiveRatings;

    @CsvBindByName(column = "negative_ratings")
    private String negativeRatings;

    @CsvBindByName(column = "average_playtime")
    private String averagePlaytime;

    @CsvBindByName(column = "median_playtime")
    private String medianPlaytime;

    @CsvBindByName(column = "owners")
    private String owners;

    @CsvBindByName(column = "price")
    private String price;

    public String getAchievements() {
        return achievements;
    }

    public void setAchievements(String achievements) {
        this.achievements = achievements;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAveragePlaytime() {
        return averagePlaytime;
    }

    public void setAveragePlaytime(String averagePlaytime) {
        this.averagePlaytime = averagePlaytime;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public String getDevelopers() {
        return developers;
    }

    public void setDevelopers(String developers) {
        this.developers = developers;
    }

    public String getEnglish() {
        return english;
    }

    public void setEnglish(String english) {
        this.english = english;
    }

    public String getGenres() {
        return genres;
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public String getMedianPlaytime() {
        return medianPlaytime;
    }

    public void setMedianPlaytime(String medianPlaytime) {
        this.medianPlaytime = medianPlaytime;
    }

    public String getNegativeRatings() {
        return negativeRatings;
    }

    public void setNegativeRatings(String negativeRatings) {
        this.negativeRatings = negativeRatings;
    }

    public String getOwners() {
        return owners;
    }

    public void setOwners(String owners) {
        this.owners = owners;
    }

    public String getPlatforms() {
        return platforms;
    }

    public void setPlatforms(String platforms) {
        this.platforms = platforms;
    }

    public String getPositiveRatings() {
        return positiveRatings;
    }

    public void setPositiveRatings(String positiveRatings) {
        this.positiveRatings = positiveRatings;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getPublishers() {
        return publishers;
    }

    public void setPublishers(String publishers) {
        this.publishers = publishers;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getRequiredAge() {
        return requiredAge;
    }

    public void setRequiredAge(String requiredAge) {
        this.requiredAge = requiredAge;
    }

    public String getSteamspyTags() {
        return steamspyTags;
    }

    public void setSteamspyTags(String steamspyTags) {
        this.steamspyTags = steamspyTags;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}