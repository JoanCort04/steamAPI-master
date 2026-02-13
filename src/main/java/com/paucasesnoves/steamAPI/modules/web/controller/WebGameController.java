package com.paucasesnoves.steamAPI.modules.web.controller;

import com.paucasesnoves.steamAPI.exception.ResourceNotFoundException;
import com.paucasesnoves.steamAPI.modules.games.dto.GameDetailDTO;
import com.paucasesnoves.steamAPI.modules.games.dto.GameSummaryDTO;
import com.paucasesnoves.steamAPI.modules.games.service.GameService;
import com.paucasesnoves.steamAPI.modules.web.dto.SteamGameSearchFormDTO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/web/games")
public class WebGameController {

    @Autowired
    private GameService gameService;

    // LISTADO CON FILTROS Y PAGINACIÓN
    @GetMapping
    public String listGames(
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String developer,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20, sort = "appId", direction = Sort.Direction.ASC) Pageable pageable,
            Model model) {

        Page<GameSummaryDTO> gamesPage = gameService.getGamesSummary(
                genre, developer, minPrice, maxPrice, pageable);

        model.addAttribute("gamesPage", gamesPage);
        model.addAttribute("genre", genre);
        model.addAttribute("developer", developer);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        return "games/list";
    }

    // MOSTRAR FORMULARI DE CERCA AVANÇADA
    @GetMapping("/search")
    public String showSearchForm(Model model) {
        model.addAttribute("searchForm", new SteamGameSearchFormDTO());
        model.addAttribute("allGenres", gameService.getAllGenreNames()); // Afegim la llista de gèneres
        return "games/search-form";
    }

    // PROCESSAR LA CERCA
    @PostMapping("/search")
    public String processSearch(@Valid @ModelAttribute("searchForm") SteamGameSearchFormDTO form,
                                BindingResult result,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            // Tornar a posar la llista de gèneres al model per al select
            model.addAttribute("allGenres", gameService.getAllGenreNames());
            return "games/search-form";
        }

        // Afegir els paràmetres de cerca a la redirecció
        redirectAttributes.addAttribute("name", form.getName());
        redirectAttributes.addAttribute("maxPrice", form.getMaxPrice());
        redirectAttributes.addAttribute("genre", form.getGenre());

        return "redirect:/web/games";
    }

    // DETALL D'UN JOC
    @GetMapping("/{appId}")
    public String showGameDetail(@PathVariable Long appId, Model model) {
        GameDetailDTO game = gameService.getGameDetail(appId);
        model.addAttribute("game", game);
        return "games/detail";
    }

    // MANEJADOR D'ERRORS 404 PER A LA PART WEB
    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "error/404";
    }
}