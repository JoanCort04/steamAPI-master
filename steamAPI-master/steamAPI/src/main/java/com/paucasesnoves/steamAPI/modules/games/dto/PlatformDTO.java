package com.paucasesnoves.steamAPI.modules.games.dto;

public class PlatformDTO {
    private Long id;
    private String name;

    public PlatformDTO() {}

    public PlatformDTO(Long id, String name) {
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