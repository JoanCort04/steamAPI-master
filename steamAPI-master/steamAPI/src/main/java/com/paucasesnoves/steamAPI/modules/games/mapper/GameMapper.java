package com.paucasesnoves.steamAPI.modules.games.mapper;

import com.paucasesnoves.steamAPI.modules.games.domain.Developer;
import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.Genre;
import com.paucasesnoves.steamAPI.modules.games.dto.GameSummaryDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameMapper {

    public GameSummaryDTO toSummaryDTO(Game game) {
        List<String> developers = game.getDevelopers().stream()
                .map(Developer::getName)
                .toList();

        List<String> genres = game.getGenres().stream()
                .map(Genre::getName)
                .toList();

        return new GameSummaryDTO(
                game.getAppId(),
                game.getTitle(),
                game.getPrice(),
                developers,
                genres
        );
    }
}