package com.paucasesnoves.steamAPI.modules.csv.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportStatisticsDto;
import com.paucasesnoves.steamAPI.modules.csv.dto.GameCsvRecord;
import com.paucasesnoves.steamAPI.modules.games.domain.*;
import com.paucasesnoves.steamAPI.modules.games.repository.*;
import com.paucasesnoves.steamAPI.utils.CsvUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameCsvImporter {

    private static final Logger log = LoggerFactory.getLogger(GameCsvImporter.class);
    private static final int BATCH_SIZE = 1000;

    @Autowired private GameRepository gameRepo;
    @Autowired private DeveloperRepository developerRepo;
    @Autowired private PublisherRepository publisherRepo;
    @Autowired private PlatformRepository platformRepo;
    @Autowired private CategoryRepository categoryRepo;
    @Autowired private GenreRepository genreRepo;
    @Autowired private TransactionTemplate transactionTemplate;
    @PersistenceContext private EntityManager entityManager;

    public CsvImportStatisticsDto importCsv(InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        CsvImportStatisticsDto stats = new CsvImportStatisticsDto();

        // =================================================================
        // 1. PRECARGAR TODAS LAS ENTIDADES EXISTENTES (CACHÉS INMUTABLES DURANTE LA IMPORTACIÓN)
        // =================================================================
        Set<Long> existingGameAppIds = CsvUtils.buildExistenceCache(gameRepo.findAll(), Game::getAppId);
        log.info("📦 Juegos existentes: {} IDs", existingGameAppIds.size());

        Map<String, Developer> developerCache = CsvUtils.buildEntityCache(developerRepo.findAll(), Developer::getName);
        Map<String, Publisher> publisherCache = CsvUtils.buildEntityCache(publisherRepo.findAll(), Publisher::getName);
        Map<String, Platform> platformCache = CsvUtils.buildEntityCache(platformRepo.findAll(), Platform::getName);
        Map<String, Category> categoryCache = CsvUtils.buildEntityCache(categoryRepo.findAll(), Category::getName);
        Map<String, Genre> genreCache = CsvUtils.buildEntityCache(genreRepo.findAll(), Genre::getName);

        // Convertir los mapas a ConcurrentHashMap para permitir modificaciones seguras
        Map<String, Developer> developerCacheConcurrent = new ConcurrentHashMap<>(developerCache);
        Map<String, Publisher> publisherCacheConcurrent = new ConcurrentHashMap<>(publisherCache);
        Map<String, Platform> platformCacheConcurrent = new ConcurrentHashMap<>(platformCache);
        Map<String, Category> categoryCacheConcurrent = new ConcurrentHashMap<>(categoryCache);
        Map<String, Genre> genreCacheConcurrent = new ConcurrentHashMap<>(genreCache);

        log.info("📦 Cachés cargadas: Devs={}, Publishers={}, Platforms={}, Cats={}, Genres={}",
                developerCacheConcurrent.size(), publisherCacheConcurrent.size(),
                platformCacheConcurrent.size(), categoryCacheConcurrent.size(),
                genreCacheConcurrent.size());

        // =================================================================
        // 2. LEER CSV
        // =================================================================
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

            HeaderColumnNameMappingStrategy<GameCsvRecord> strategy = new HeaderColumnNameMappingStrategy<>();
            strategy.setType(GameCsvRecord.class);

            CsvToBean<GameCsvRecord> csvToBean = new CsvToBeanBuilder<GameCsvRecord>(reader)
                    .withMappingStrategy(strategy)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withIgnoreEmptyLine(true)
                    .withThrowExceptions(false)
                    .build();

            List<GameCsvRecord> records = csvToBean.parse();
            stats.setProcessed(records.size());
            log.info("📄 Registros leídos: {}", String.format("%,d", records.size()));

            if (records.isEmpty()) {
                log.warn("⚠️ CSV vacío");
                return stats;
            }

            // Debug: primer registro
            GameCsvRecord first = records.get(0);
            log.debug("Ejemplo: appId={}, title={}", first.getAppId(), first.getTitle());

            List<Game> gamesBatch = new ArrayList<>(BATCH_SIZE);

            for (int i = 0; i < records.size(); i++) {
                GameCsvRecord record = records.get(i);
                int lineNumber = i + 2;

                try {
                    // ---- AppId ----
                    Long appId = CsvUtils.parseLong(record.getAppId()).orElse(null);
                    if (appId == null) {
                        log.warn("⚠️ Línea {}: appId inválido '{}'", lineNumber, record.getAppId());
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- Juego ya existe? ----
                    if (existingGameAppIds.contains(appId)) {
                        if (stats.getSkipped() % 1000 == 0) {
                            log.debug("⏭️ Juego {} ya existe, saltando", appId);
                        }
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- Crear Game ----
                    Game game = createGameFromRecord(record, appId);

                    // ---- Procesar relaciones con cachés CONCURRENTES ----
                    processGameRelations(game, record, stats,
                            developerCacheConcurrent, publisherCacheConcurrent,
                            platformCacheConcurrent, categoryCacheConcurrent,
                            genreCacheConcurrent);

                    gamesBatch.add(game);
                    stats.incrementCreated();

                    if (gamesBatch.size() >= BATCH_SIZE) {
                        saveBatchInTransaction(gamesBatch, stats);
                        gamesBatch.clear();
                    }

                    if (stats.getCreated() % 5000 == 0) {
                        log.info("✅ {} juegos creados...", String.format("%,d", stats.getCreated()));
                    }

                } catch (Exception e) {
                    log.warn("❌ Error línea {}: {}", lineNumber, e.getMessage());
                    if (i < 5) log.debug("Detalle", e);
                    stats.incrementSkipped();
                }
            }

            // ---- Guardar último lote ----
            if (!gamesBatch.isEmpty()) {
                saveBatchInTransaction(gamesBatch, stats);
            }

            // ---- Estadísticas finales ----
            CsvUtils.logFinalStatistics(stats, startTime, "Juegos");

        } catch (Exception e) {
            log.error("❌ Error crítico en importación de juegos", e);
            throw new RuntimeException("Falló importación de juegos", e);
        }

        return stats;
    }

    private Game createGameFromRecord(GameCsvRecord record, Long appId) {
        Game game = new Game();
        game.setAppId(appId);
        game.setTitle(Optional.ofNullable(record.getTitle())
                .filter(t -> !t.isBlank())
                .map(String::trim)
                .orElse("Unknown Title - " + appId));
        game.setReleaseDate(Optional.ofNullable(record.getReleaseDate())
                .orElse(LocalDate.of(2000, 1, 1)));
        game.setEnglish("1".equals(record.getEnglish()));
        game.setMinAge(CsvUtils.parseInt(record.getRequiredAge()).orElse(0));
        game.setAchievements(CsvUtils.parseInt(record.getAchievements()).orElse(0));
        game.setPositiveRatings(CsvUtils.parseInt(record.getPositiveRatings()).orElse(0));
        game.setNegativeRatings(CsvUtils.parseInt(record.getNegativeRatings()).orElse(0));
        game.setAvgPlaytime(CsvUtils.parseDouble(record.getAveragePlaytime()).orElse(0.0));
        game.setMedianPlaytime(CsvUtils.parseDouble(record.getMedianPlaytime()).orElse(0.0));

        CsvUtils.OwnersRange owners = CsvUtils.parseOwners(record.getOwners());
        game.setOwnersLower(owners.lower());
        game.setOwnersUpper(owners.upper());
        game.setOwnersMid(owners.mid());

        game.setPrice(parseBigDecimal(record.getPrice()));
        return game;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private void processGameRelations(Game game,
                                      GameCsvRecord record,
                                      CsvImportStatisticsDto stats,
                                      Map<String, Developer> developerCache,
                                      Map<String, Publisher> publisherCache,
                                      Map<String, Platform> platformCache,
                                      Map<String, Category> categoryCache,
                                      Map<String, Genre> genreCache) {

        // Desarrolladores
        if (record.getDevelopers() != null && !record.getDevelopers().isBlank()) {
            processMultiValue(record.getDevelopers(), ";", name -> {
                Developer dev = getOrCreateDeveloper(name, developerCache, stats);
                if (!game.getDevelopers().contains(dev)) game.getDevelopers().add(dev);
            });
        }

        // Editores
        if (record.getPublishers() != null && !record.getPublishers().isBlank()) {
            processMultiValue(record.getPublishers(), ";", name -> {
                Publisher pub = getOrCreatePublisher(name, publisherCache, stats);
                if (!game.getPublishers().contains(pub)) game.getPublishers().add(pub);
            });
        }

        // Plataformas
        if (record.getPlatforms() != null && !record.getPlatforms().isBlank()) {
            processMultiValue(record.getPlatforms(), ";", name -> {
                Platform plat = getOrCreatePlatform(name, platformCache, stats);
                if (!game.getPlatforms().contains(plat)) game.getPlatforms().add(plat);
            });
        }

        // Categoría (primera)
        if (record.getCategories() != null && !record.getCategories().isBlank()) {
            String[] cats = record.getCategories().split(";");
            if (cats.length > 0 && !cats[0].trim().isEmpty()) {
                Category cat = getOrCreateCategory(cats[0].trim(), categoryCache, stats);
                game.setCategory(cat);
            }
        }

        // Géneros
        if (record.getGenres() != null && !record.getGenres().isBlank()) {
            processMultiValue(record.getGenres(), ";", name -> {
                Genre gen = getOrCreateGenre(name, genreCache, stats);
                if (!game.getGenres().contains(gen)) game.getGenres().add(gen);
            });
        }
    }

    private void processMultiValue(String field, String delimiter, java.util.function.Consumer<String> consumer) {
        for (String item : field.split(delimiter)) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) consumer.accept(trimmed);
        }
    }

    // =================================================================
    // MÉTODOS getOrCreate ESPECÍFICOS (con caché ConcurrentHashMap)
    // =================================================================
    private Developer getOrCreateDeveloper(String name, Map<String, Developer> cache, CsvImportStatisticsDto stats) {
        return cache.computeIfAbsent(name, n -> {
            // Buscar en BD por si acaso (aunque ya debería estar en caché si existía)
            Optional<Developer> existing = developerRepo.findByName(n);
            if (existing.isPresent()) {
                return existing.get();
            }
            Developer dev = new Developer(n);
            try {
                Developer saved = developerRepo.save(dev);
                stats.incrementDevelopersCreated();
                return saved;
            } catch (DataIntegrityViolationException e) {
                // Si ocurre duplicado (otro hilo insertó justo antes), recuperamos el existente
                return developerRepo.findByName(n)
                        .orElseThrow(() -> new RuntimeException("Error al crear desarrollador: " + n, e));
            }
        });
    }

    private Publisher getOrCreatePublisher(String name, Map<String, Publisher> cache, CsvImportStatisticsDto stats) {
        return cache.computeIfAbsent(name, n -> {
            Optional<Publisher> existing = publisherRepo.findByName(n);
            if (existing.isPresent()) return existing.get();
            Publisher pub = new Publisher(n);
            try {
                Publisher saved = publisherRepo.save(pub);
                stats.incrementPublishersCreated();
                return saved;
            } catch (DataIntegrityViolationException e) {
                return publisherRepo.findByName(n)
                        .orElseThrow(() -> new RuntimeException("Error al crear editor: " + n, e));
            }
        });
    }

    private Platform getOrCreatePlatform(String name, Map<String, Platform> cache, CsvImportStatisticsDto stats) {
        return cache.computeIfAbsent(name, n -> {
            Optional<Platform> existing = platformRepo.findByName(n);
            if (existing.isPresent()) return existing.get();
            Platform plat = new Platform(n);
            try {
                Platform saved = platformRepo.save(plat);
                stats.incrementPlatformsCreated();
                return saved;
            } catch (DataIntegrityViolationException e) {
                return platformRepo.findByName(n)
                        .orElseThrow(() -> new RuntimeException("Error al crear plataforma: " + n, e));
            }
        });
    }

    private Category getOrCreateCategory(String name, Map<String, Category> cache, CsvImportStatisticsDto stats) {
        return cache.computeIfAbsent(name, n -> {
            Optional<Category> existing = categoryRepo.findByName(n);
            if (existing.isPresent()) return existing.get();
            Category cat = new Category(n);
            try {
                Category saved = categoryRepo.save(cat);
                stats.incrementCategoriesCreated();
                return saved;
            } catch (DataIntegrityViolationException e) {
                return categoryRepo.findByName(n)
                        .orElseThrow(() -> new RuntimeException("Error al crear categoría: " + n, e));
            }
        });
    }

    private Genre getOrCreateGenre(String name, Map<String, Genre> cache, CsvImportStatisticsDto stats) {
        return cache.computeIfAbsent(name, n -> {
            Optional<Genre> existing = genreRepo.findByName(n);
            if (existing.isPresent()) return existing.get();
            Genre genre = new Genre(n);
            try {
                Genre saved = genreRepo.save(genre);
                stats.incrementGenresCreated();
                return saved;
            } catch (DataIntegrityViolationException e) {
                return genreRepo.findByName(n)
                        .orElseThrow(() -> new RuntimeException("Error al crear género: " + n, e));
            }
        });
    }

    // =================================================================
    // GUARDADO POR LOTES CON TRANSACCIÓN INDEPENDIENTE
    // =================================================================
    private void saveBatchInTransaction(List<Game> batch, CsvImportStatisticsDto stats) {
        if (batch.isEmpty()) return;
        try {
            transactionTemplate.execute(status -> {
                gameRepo.saveAll(batch);
                entityManager.flush();
                entityManager.clear();
                return null;
            });
            log.debug("✅ Lote guardado: {} juegos", batch.size());
        } catch (Exception e) {
            log.error("❌ Error guardando lote de {} juegos: {}", batch.size(), e.getMessage(), e);
            stats.setCreated(stats.getCreated() - batch.size());
            stats.setSkipped(stats.getSkipped() + batch.size());
        }
    }
}