package com.paucasesnoves.steamAPI.modules.games.dto;

public class DeveloperDTO {
    private Long id;
    private String name;

    public DeveloperDTO() {}

    public DeveloperDTO(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}