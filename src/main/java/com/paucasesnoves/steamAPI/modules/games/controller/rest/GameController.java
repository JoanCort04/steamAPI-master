package com.paucasesnoves.steamAPI.modules.games.controller.rest;

import com.paucasesnoves.steamAPI.modules.games.dto.GameDetailDTO;
import com.paucasesnoves.steamAPI.modules.games.dto.GameSummaryDTO;
import com.paucasesnoves.steamAPI.modules.games.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/games")
public class GameController {

    @Autowired
    private GameService gameService;

    @GetMapping
    public Page<GameSummaryDTO> listGames(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String developer,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20, sort = "appId", direction = Sort.Direction.ASC) Pageable pageable) {

        return gameService.getGamesSummary(name, genre, developer, minPrice, maxPrice, pageable);
    }

    @GetMapping("/{appId}")
    public GameDetailDTO getGameDetail(@PathVariable Long appId) {
        return gameService.getGameDetail(appId);
    }
}