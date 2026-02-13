package com.paucasesnoves.steamAPI.modules.games.service;

import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.dto.GameSummaryDTO;
import com.paucasesnoves.steamAPI.modules.games.mapper.GameMapper;
import com.paucasesnoves.steamAPI.modules.games.repository.GameRepository;

import com.paucasesnoves.steamAPI.modules.games.repository.GameSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
public class GameService {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameMapper gameMapper;

    public Page<GameSummaryDTO> getGamesSummary(
            String genre,
            String developer,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        Specification<Game> spec = buildSpecification(genre, developer, minPrice, maxPrice);

        Page<Game> gamesPage = gameRepository.findAll(spec, pageable);
        return gamesPage.map(gameMapper::toSummaryDTO);
    }

    private Specification<Game> buildSpecification(
            String genre,
            String developer,
            BigDecimal minPrice,
            BigDecimal maxPrice) {

        // ✅ SOLUCIÓN: Usar Specification.allOf() para especificación vacía
        Specification<Game> spec = Specification.allOf();

        // Alternativa si prefieres where con tipo explícito:
        // Specification<Game> spec = Specification.<Game>where(null);

        if (StringUtils.hasText(genre)) {
            spec = spec.and(GameSpecification.hasGenre(genre));
        }

        if (StringUtils.hasText(developer)) {
            spec = spec.and(GameSpecification.hasDeveloper(developer));
        }

        if (minPrice != null || maxPrice != null) {
            spec = spec.and(GameSpecification.priceBetween(minPrice, maxPrice));
        }

        return spec;
    }
}