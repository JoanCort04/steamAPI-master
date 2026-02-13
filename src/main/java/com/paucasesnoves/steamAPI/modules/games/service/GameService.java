package com.paucasesnoves.steamAPI.modules.games.service;

import com.paucasesnoves.steamAPI.exception.ResourceNotFoundException;
import com.paucasesnoves.steamAPI.modules.games.domain.*;
import com.paucasesnoves.steamAPI.modules.games.dto.*;
import com.paucasesnoves.steamAPI.modules.games.mapper.GameMapper;
import com.paucasesnoves.steamAPI.modules.games.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameService {

    // Repositoris principals
    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private DeveloperRepository developerRepo;

    @Autowired
    private PublisherRepository publisherRepo;

    @Autowired
    private GenreRepository genreRepo;

    @Autowired
    private CategoryRepository categoryRepo;

    // Repositoris de detall
    @Autowired
    private GameDescriptionRepository descriptionRepo;

    @Autowired
    private GameRequirementsRepository requirementsRepo;

    @Autowired
    private GameMediaRepository mediaRepo;

    @Autowired
    private GameSupportInfoRepository supportRepo;

    // Mapper
    @Autowired
    private GameMapper gameMapper;

    // =========================================================================
    // 1. LLISTAT AMB FILTRES I PAGINACIÓ (ARA AMB FILTRE PER NOM)
    // =========================================================================
    @Transactional(readOnly = true)
    public Page<GameSummaryDTO> getGamesSummary(
            String name,                // <-- NOU PARÀMETRE
            String genre,
            String developer,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        Specification<Game> spec = buildSpecification(name, genre, developer, minPrice, maxPrice);
        Page<Game> gamesPage = gameRepository.findAll(spec, pageable);
        return gamesPage.map(gameMapper::toSummaryDTO);
    }

    private Specification<Game> buildSpecification(
            String name,
            String genre,
            String developer,
            BigDecimal minPrice,
            BigDecimal maxPrice) {

        Specification<Game> spec = Specification.allOf();

        if (StringUtils.hasText(name)) {
            spec = spec.and(GameSpecification.hasName(name));   // <-- NOU
        }
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

    // =========================================================================
    // 2. DETALL COMPLET D'UN JOC
    // =========================================================================
    @Transactional(readOnly = true)
    public GameDetailDTO getGameDetail(Long appId) {
        Specification<Game> spec = (root, query, cb) -> cb.equal(root.get("appId"), appId);
        Game game = gameRepository.findOne(spec)
                .orElseThrow(() -> new ResourceNotFoundException("Juego no encontrado con appId: " + appId));

        GameDetailDTO dto = new GameDetailDTO();

        // Dades bàsiques
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

        // Categoria
        if (game.getCategory() != null) {
            dto.setCategory(new CategoryDTO(game.getCategory().getId(), game.getCategory().getName()));
        }

        // Col·leccions
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

        // Descripció
        descriptionRepo.findByGame(game).ifPresent(desc -> {
            GameDescriptionDTO descDto = new GameDescriptionDTO();
            descDto.setDetailedDescription(desc.getDetailedDescription());
            descDto.setAboutTheGame(desc.getAboutTheGame());
            descDto.setShortDescription(desc.getShortDescription());
            dto.setDescription(descDto);
        });

        // Requisits
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
            mediaDto.setScreenshots(new ArrayList<>(media.getScreenshots()));
            mediaDto.setMovies(new ArrayList<>(media.getMovies()));
            dto.setMedia(mediaDto);
        });

        // Suport
        supportRepo.findByGame(game).ifPresent(support -> {
            GameSupportInfoDTO supportDto = new GameSupportInfoDTO();
            supportDto.setWebsite(support.getWebsite());
            supportDto.setSupportUrl(support.getSupportUrl());
            supportDto.setSupportEmail(support.getSupportEmail());
            dto.setSupportInfo(supportDto);
        });

        return dto;
    }

    // =========================================================================
    // 3. OBTENIR TOTS ELS GÈNERES (PER AL FORMULARI DE CERCA)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<String> getAllGenreNames() {
        return genreRepo.findAll().stream()
                .map(Genre::getName)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 4. MÈTODE ADDICIONAL: TOTS ELS JOCS EN RESUM (SI CAL)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<GameSummaryDTO> getAllGamesSummary() {
        return gameRepository.findAll().stream()
                .map(gameMapper::toSummaryDTO)
                .collect(Collectors.toList());
    }
}