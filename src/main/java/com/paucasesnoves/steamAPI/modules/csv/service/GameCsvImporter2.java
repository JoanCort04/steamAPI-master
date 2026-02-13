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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class GameCsvImporter2 {

    @Autowired
    private GameRepository gameRepo;
    @Autowired
    private DeveloperRepository developerRepo;
    @Autowired
    private PublisherRepository publisherRepo;
    @Autowired
    private PlatformRepository platformRepo;
    @Autowired
    private CategoryRepository categoryRepo;
    @Autowired
    private GenreRepository genreRepo;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int BATCH_SIZE = 100;

    @Transactional
    public CsvImportStatisticsDto importCsv(InputStream inputStream) {
        CsvImportStatisticsDto stats = new CsvImportStatisticsDto();
        long startTime = System.currentTimeMillis();

        System.out.println("🚀 INICIANDO IMPORTACIÓN DE JUEGOS (Versión simplificada) 🚀");

        try (Reader reader = new InputStreamReader(inputStream, "UTF-8")) {

            // Configurar estrategia de mapeo por columnas
            HeaderColumnNameMappingStrategy<GameCsvRecord> strategy =
                    new HeaderColumnNameMappingStrategy<>();
            strategy.setType(GameCsvRecord.class);

            // Crear CsvToBean con configuración completa
            CsvToBean<GameCsvRecord> csvToBean = new CsvToBeanBuilder<GameCsvRecord>(reader)
                    .withMappingStrategy(strategy)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withIgnoreEmptyLine(true)
                    .withThrowExceptions(false)
                    .build();

            // Obtener todos los registros
            List<GameCsvRecord> records = csvToBean.parse();
            stats.setProcessed(records.size());

            System.out.printf("📄 Total de registros leídos: %,d%n", records.size());

            if (records.isEmpty()) {
                System.out.println("⚠️  No se encontraron registros en el CSV");
                return stats;
            }

            // Mostrar ejemplo del primer registro
            GameCsvRecord first = records.get(0);
            System.out.printf("📋 Ejemplo: appid=%s, name=%s%n",
                    first.getAppId(), first.getTitle());

            // Procesar en batches
            List<Game> gamesBatch = new ArrayList<>(BATCH_SIZE);

            for (GameCsvRecord record : records) {
                try {
                    // Validar appId
                    if (record.getAppId() == null || record.getAppId().trim().isEmpty()) {
                        stats.incrementSkipped();
                        continue;
                    }

                    Long appId = parseAppId(record.getAppId());
                    if (appId == null) {
                        stats.incrementSkipped();
                        continue;
                    }

                    // Verificar si ya existe
                    if (gameRepo.existsByAppId(appId)) {
                        stats.incrementSkipped();
                        continue;
                    }

                    // Crear y configurar el juego
                    Game game = createGameFromRecord(record, appId);

                    // Procesar relaciones usando CsvUtils
                    processRelationsWithUtils(game, record, stats);

                    gamesBatch.add(game);
                    stats.incrementCreated();

                    // Guardar batch
                    if (gamesBatch.size() >= BATCH_SIZE) {
                        saveBatch(gamesBatch, stats);
                        gamesBatch.clear();
                        entityManager.flush();
                        entityManager.clear();
                    }

                    // Mostrar progreso
                    if (stats.getCreated() % 1000 == 0) {
                        System.out.printf("✅ %,d juegos creados...%n", stats.getCreated());
                    }

                } catch (Exception e) {
                    stats.incrementSkipped();
                    if (stats.getSkipped() < 10) {
                        System.err.printf("❌ Error: %s%n", e.getMessage());
                    }
                }
            }

            // Guardar batch final
            if (!gamesBatch.isEmpty()) {
                saveBatch(gamesBatch, stats);
            }

            // Estadísticas finales
            printFinalStatistics(stats, startTime);

        } catch (Exception e) {
            System.err.println("❌ Error crítico: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al importar CSV: " + e.getMessage());
        }

        return stats;
    }

    private Game createGameFromRecord(GameCsvRecord record, Long appId) {
        Game game = new Game();
        game.setAppId(appId);
        game.setTitle(record.getTitle() != null ? record.getTitle().trim() : "Unknown - " + appId);
        game.setReleaseDate(record.getReleaseDate() != null ? record.getReleaseDate() : LocalDate.of(2000, 1, 1));
        game.setEnglish("1".equals(record.getEnglish()));

        // Campos numéricos con parseo seguro
        game.setMinAge(parseIntSafe(record.getRequiredAge(), 0));
        game.setAchievements(parseIntSafe(record.getAchievements(), 0));
        game.setPositiveRatings(parseIntSafe(record.getPositiveRatings(), 0));
        game.setNegativeRatings(parseIntSafe(record.getNegativeRatings(), 0));
        game.setAvgPlaytime(parseDoubleSafe(record.getAveragePlaytime(), 0.0));
        game.setMedianPlaytime(parseDoubleSafe(record.getMedianPlaytime(), 0.0));

        // Precio
        if (record.getPrice() != null && !record.getPrice().isEmpty()) {
            try {
                game.setPrice(new BigDecimal(record.getPrice().trim()));
            } catch (NumberFormatException e) {
                game.setPrice(BigDecimal.ZERO);
            }
        } else {
            game.setPrice(BigDecimal.ZERO);
        }

        // Propietarios
        parseOwners(game, record.getOwners());

        return game;
    }

    /**
     * 🎯 Versión MEJORADA usando CsvUtils.parseAndFindOrCreate
     */
    private void processRelationsWithUtils(Game game, GameCsvRecord record, CsvImportStatisticsDto stats) {

        // Desarrolladores
        game.setDevelopers(CsvUtils.parseAndFindOrCreate(
                record.getDevelopers(),
                developerRepo::findByName,
                name -> {
                    stats.incrementDevelopersCreated();
                    return developerRepo.save(new Developer(name));
                }
        ));

        // Editores
        game.setPublishers(CsvUtils.parseAndFindOrCreate(
                record.getPublishers(),
                publisherRepo::findByName,
                name -> {
                    stats.incrementPublishersCreated();
                    return publisherRepo.save(new Publisher(name));
                }
        ));

        // Plataformas
        game.setPlatforms(CsvUtils.parseAndFindOrCreate(
                record.getPlatforms(),
                platformRepo::findByName,
                name -> {
                    stats.incrementPlatformsCreated();
                    return platformRepo.save(new Platform(name));
                }
        ));

        // Categorías - IMPORTANTE: Solo tomamos la primera para category principal
        if (record.getCategories() != null && !record.getCategories().trim().isEmpty()) {
            String[] categories = record.getCategories().split(";");
            if (categories.length > 0) {
                String categoryName = categories[0].trim();
                if (!categoryName.isEmpty()) {
                    Category category = CsvUtils.findOrCreate(
                            categoryName,
                            categoryRepo::findByName,
                            name -> {
                                stats.incrementCategoriesCreated();
                                return categoryRepo.save(new Category(name));
                            }
                    );
                    game.setCategory(category);
                }
            }
        }

        // Géneros
        game.setGenres(CsvUtils.parseAndFindOrCreate(
                record.getGenres(),
                genreRepo::findByName,
                name -> {
                    stats.incrementGenresCreated();
                    return genreRepo.save(new Genre(name));
                }
        ));
    }

    // ========== MÉTODOS UTILITY ==========

    private Long parseAppId(String appIdStr) {
        if (appIdStr == null || appIdStr.trim().isEmpty()) return null;
        try {
            return Long.parseLong(appIdStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double parseDoubleSafe(String value, double defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void parseOwners(Game game, String ownersStr) {
        if (ownersStr == null || ownersStr.trim().isEmpty()) {
            game.setOwnersLower(0); game.setOwnersUpper(0); game.setOwnersMid(0);
            return;
        }
        try {
            if (ownersStr.contains("-")) {
                String[] parts = ownersStr.split("-");
                if (parts.length == 2) {
                    int low = parseIntSafe(parts[0], 0);
                    int high = parseIntSafe(parts[1], 0);
                    game.setOwnersLower(low);
                    game.setOwnersUpper(high);
                    game.setOwnersMid((low + high) / 2);
                }
            } else {
                int val = parseIntSafe(ownersStr, 0);
                game.setOwnersLower(val);
                game.setOwnersUpper(val);
                game.setOwnersMid(val);
            }
        } catch (Exception e) {
            game.setOwnersLower(0); game.setOwnersUpper(0); game.setOwnersMid(0);
        }
    }

    private void saveBatch(List<Game> gamesBatch, CsvImportStatisticsDto stats) {
        try {
            gameRepo.saveAll(gamesBatch);
            System.out.printf("✅ Batch guardado: %,d juegos | Total: %,d%n",
                    gamesBatch.size(), stats.getCreated());
        } catch (Exception e) {
            System.err.printf("❌ Error guardando batch: %s%n", e.getMessage());
            // Fallback: guardar individualmente
            int saved = 0;
            for (Game game : gamesBatch) {
                try {
                    gameRepo.save(game);
                    saved++;
                } catch (Exception ex) {
                    stats.incrementSkipped();
                }
            }
            stats.setCreated(stats.getCreated() - (gamesBatch.size() - saved));
        }
    }

    private void printFinalStatistics(CsvImportStatisticsDto stats, long startTime) {
        long endTime = System.currentTimeMillis();
        double seconds = (endTime - startTime) / 1000.0;

        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 ESTADÍSTICAS FINALES DE IMPORTACIÓN");
        System.out.println("=".repeat(70));
        System.out.printf("Registros procesados:      %,d%n", stats.getProcessed());
        System.out.printf("Juegos creados:            %,d%n", stats.getCreated());
        System.out.printf("Registros saltados:        %,d%n", stats.getSkipped());
        System.out.printf("Desarrolladores creados:   %,d%n", stats.getDevelopersCreated());
        System.out.printf("Editores creados:          %,d%n", stats.getPublishersCreated());
        System.out.printf("Géneros creados:           %,d%n", stats.getGenresCreated());
        System.out.printf("Plataformas creadas:       %,d%n", stats.getPlatformsCreated());
        System.out.printf("Categorías creadas:        %,d%n", stats.getCategoriesCreated());
        System.out.printf("Tiempo total:              %.2f segundos%n", seconds);

        if (seconds > 0) {
            System.out.printf("Velocidad:                 %.1f juegos/segundo%n",
                    stats.getCreated() / seconds);
        }

        try {
            long totalInDB = gameRepo.count();
            System.out.printf("Total juegos en BD:        %,d%n", totalInDB);
        } catch (Exception e) {
            // Ignorar
        }
        System.out.println("=".repeat(70));
    }
}