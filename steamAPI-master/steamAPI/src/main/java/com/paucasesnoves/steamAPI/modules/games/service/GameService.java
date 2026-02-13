package com.paucasesnoves.steamAPI.modules.games.service;


import com.paucasesnoves.steamAPI.modules.games.domain.*;
import com.paucasesnoves.steamAPI.modules.games.dto.*;
import com.paucasesnoves.steamAPI.modules.games.mapper.GameMapper;
import com.paucasesnoves.steamAPI.modules.games.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
public class GameService {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameDescriptionRepository descriptionRepo;

    @Autowired
    private GameRequirementsRepository requirementsRepo;

    @Autowired
    private GameMediaRepository mediaRepo;

    @Autowired
    private GameSupportInfoRepository supportRepo;

    @Autowired
    private GameMapper gameMapper;

    // ========== MÉTODO PARA LISTADO CON FILTROS ==========
    @Transactional(readOnly = true)
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

        Specification<Game> spec = Specification.allOf();

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

    // ========== MÉTODO PARA DETALLE DE UN JUEGO ==========
    @Transactional(readOnly = true)
    public GameDetailDTO getGameDetail(Long appId) {
        Specification<Game> spec = (root, query, cb) -> cb.equal(root.get("appId"), appId);
        Game game = gameRepository.findOne(spec)
                .orElseThrow(() -> new ResourceNotFoundException("Juego no encontrado con appId: " + appId));

        GameDetailDTO dto = new GameDetailDTO();

        // Datos básicos
        dto.setAppId(game.getAppId());
        dto.setTitle(game.getTitle());
        dto.setReleaseDate(game.getReleaseDate());
        dto.setEnglish(game.isEnglish());
        dto.setMinAge(game.getMinAge());
        dto.setAchievements(game.getAchievements());
        dto.setPositiveRatings(game.getPositiveRatings());
        dto.setNegativeRatings(game.getNegativeRatings());
        dto.setAvgPlaytime(game.getAvgPlaytime());
        dto.setMedianPlaytime(game.getMedianPlaytime());
        dto.setOwnersLower(game.getOwnersLower());
        dto.setOwnersUpper(game.getOwnersUpper());
        dto.setOwnersMid(game.getOwnersMid());
        dto.setPrice(game.getPrice());

        // Categoría
        if (game.getCategory() != null) {
            dto.setCategory(new CategoryDTO(game.getCategory().getId(), game.getCategory().getName()));
        }

        // Colecciones
        dto.setDevelopers(game.getDevelopers().stream()
                .map(d -> new DeveloperDTO(d.getId(), d.getName()))
                .collect(Collectors.toSet()));

        dto.setPublishers(game.getPublishers().stream()
                .map(p -> new PublisherDTO(p.getId(), p.getName()))
                .collect(Collectors.toSet()));

        dto.setGenres(game.getGenres().stream()
                .map(g -> new GenreDTO(g.getId(), g.getName()))
                .collect(Collectors.toSet()));

        dto.setTags(game.getTags().stream()
                .map(t -> new TagDTO(t.getId(), t.getName()))
                .collect(Collectors.toSet()));

        dto.setPlatforms(game.getPlatforms().stream()
                .map(p -> new PlatformDTO(p.getId(), p.getName()))
                .collect(Collectors.toSet()));

        // Descripción
        descriptionRepo.findByGame(game).ifPresent(desc -> {
            GameDescriptionDTO descDto = new GameDescriptionDTO();
            descDto.setDetailedDescription(desc.getDetailedDescription());
            descDto.setAboutTheGame(desc.getAboutTheGame());
            descDto.setShortDescription(desc.getShortDescription());
            dto.setDescription(descDto);
        });

        // Requisitos
        requirementsRepo.findByGame(game).ifPresent(req -> {
            GameRequirementsDTO reqDto = new GameRequirementsDTO();
            reqDto.setPcRequirements(req.getPcRequirements());
            reqDto.setMacRequirements(req.getMacRequirements());
            reqDto.setLinuxRequirements(req.getLinuxRequirements());
            reqDto.setMinimum(req.getMinimum());
            reqDto.setRecommended(req.getRecommended());
            dto.setRequirements(reqDto);
        });

        // Media
        mediaRepo.findByGame(game).ifPresent(media -> {
            GameMediaDTO mediaDto = new GameMediaDTO();
            mediaDto.setHeaderImage(media.getHeaderImage());
            mediaDto.setBackground(media.getBackground());
            mediaDto.setScreenshots(media.getScreenshots());
            mediaDto.setMovies(media.getMovies());
            dto.setMedia(mediaDto);
        });

        // Soporte
        supportRepo.findByGame(game).ifPresent(support -> {
            GameSupportInfoDTO supportDto = new GameSupportInfoDTO();
            supportDto.setWebsite(support.getWebsite());
            supportDto.setSupportUrl(support.getSupportUrl());
            supportDto.setSupportEmail(support.getSupportEmail());
            dto.setSupportInfo(supportDto);
        });

        return dto;
    }
}