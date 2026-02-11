package com.paucasesnoves.steamAPI.modules.csv.service;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportStatisticsDto;
import com.paucasesnoves.steamAPI.modules.csv.dto.GameCsvRecord;
import com.paucasesnoves.steamAPI.modules.games.domain.*;
import com.paucasesnoves.steamAPI.modules.games.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameCsvImporter {

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

    private static final int BATCH_SIZE = 1000;
    private static final int CACHE_CLEAN_INTERVAL = 5000;

    // Caches
    private final Map<String, Developer> developerCache = new ConcurrentHashMap<>();
    private final Map<String, Publisher> publisherCache = new ConcurrentHashMap<>();
    private final Map<String, Platform> platformCache = new ConcurrentHashMap<>();
    private final Map<String, Category> categoryCache = new ConcurrentHashMap<>();
    private final Map<String, Genre> genreCache = new ConcurrentHashMap<>();

    @Transactional
    public CsvImportStatisticsDto importCsv(InputStream inputStream) throws Exception {
        long startTime = System.currentTimeMillis();
        CsvImportStatisticsDto stats = new CsvImportStatisticsDto();
        clearAllCaches();

        System.out.println("🚀 INICIANDO IMPORTACIÓN DE JUEGOS (Mapeo por columnas) 🚀");

        try (InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8")) {

            HeaderColumnNameMappingStrategy<GameCsvRecord> strategy =
                    new HeaderColumnNameMappingStrategy<>();
            strategy.setType(GameCsvRecord.class);

            CsvToBean<GameCsvRecord> csvToBean = new CsvToBeanBuilder<GameCsvRecord>(reader)
                    .withMappingStrategy(strategy)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withIgnoreEmptyLine(true)
                    .withThrowExceptions(false)
                    .build();

            List<GameCsvRecord> records = csvToBean.parse();
            stats.setProcessed(records.size());

            System.out.printf("📄 Total de registros leídos: %,d%n", records.size());

            // Verificar que se leyeron registros
            if (records.isEmpty()) {
                System.out.println("⚠️  No se encontraron registros en el CSV");
                return stats;
            }

            // Mostrar información de las columnas del primer registro
            if (!records.isEmpty()) {
                System.out.println("📋 Ejemplo de primer registro leído:");
                GameCsvRecord firstRecord = records.get(0);
                System.out.printf("  appid: %s, name: %s%n",
                        firstRecord.getAppId(),
                        firstRecord.getTitle());
            }

            List<Game> gamesBatch = new ArrayList<>(BATCH_SIZE);

            for (int i = 0; i < records.size(); i++) {
                GameCsvRecord record = records.get(i);

                try {
                    // Validar registro mínimo
                    if (record.getAppId() == null || record.getAppId().trim().isEmpty()) {
                        stats.incrementSkipped();
                        continue;
                    }

                    // Parsear appId
                    Long appId = parseAppId(record.getAppId());
                    if (appId == null) {
                        System.err.printf("⚠️  Línea %d: appId inválido: %s%n",
                                i + 2, record.getAppId()); // +2 para contar header
                        stats.incrementSkipped();
                        continue;
                    }

                    // Verificar si ya existe
                    if (gameRepo.existsByAppId(appId)) {
                        if (stats.getSkipped() % 1000 == 0) {
                            System.out.printf("⏭️  Juego %d ya existe, saltando...%n", appId);
                        }
                        stats.incrementSkipped();
                        continue;
                    }

                    // Crear juego desde el record
                    Game game = createGameFromRecord(record, appId);

                    // Procesar relaciones
                    processGameRelations(game, record, stats);

                    gamesBatch.add(game);
                    stats.incrementCreated();

                    // Mostrar progreso
                    if (stats.getCreated() % 1000 == 0) {
                        System.out.printf("✅ %,d juegos creados...%n", stats.getCreated());
                    }

                    // Guardar batch
                    if (gamesBatch.size() >= BATCH_SIZE) {
                        saveBatch(gamesBatch, stats);
                        gamesBatch.clear();

                        // Limpiar entity manager
                        entityManager.flush();
                        entityManager.clear();

                        // Limpiar caches periódicamente
                        if (stats.getCreated() % CACHE_CLEAN_INTERVAL == 0) {
                            clearCaches();
                            System.out.println("🧹 Caches limpiados");
                        }
                    }

                } catch (Exception e) {
                    stats.incrementSkipped();
                    System.err.printf("❌ Error procesando línea %d: %s%n",
                            i + 2, e.getMessage());
                    // Log detallado solo para los primeros errores
                    if (i < 5) {
                        e.printStackTrace();
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
            System.err.println("❌ Error crítico durante la importación: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            clearAllCaches();
            entityManager.clear();
        }

        return stats;
    }

    private Long parseAppId(String appIdStr) {
        if (appIdStr == null || appIdStr.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(appIdStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Game createGameFromRecord(GameCsvRecord record, Long appId) {
        Game game = new Game();
        game.setAppId(appId);

        // Título
        if (record.getTitle() != null && !record.getTitle().trim().isEmpty()) {
            game.setTitle(record.getTitle().trim());
        } else {
            game.setTitle("Unknown Title - " + appId);
        }

        // Fecha de lanzamiento
        if (record.getReleaseDate() != null) {
            game.setReleaseDate(record.getReleaseDate());
        } else {
            game.setReleaseDate(LocalDate.of(2000, 1, 1));
        }

        // Inglés
        game.setEnglish("1".equals(record.getEnglish()));

        // Edad mínima
        game.setMinAge(parseIntSafe(record.getRequiredAge(), 0));

        // Logros
        game.setAchievements(parseIntSafe(record.getAchievements(), 0));

        // Calificaciones
        game.setPositiveRatings(parseIntSafe(record.getPositiveRatings(), 0));
        game.setNegativeRatings(parseIntSafe(record.getNegativeRatings(), 0));

        // Tiempo de juego
        game.setAvgPlaytime(parseDoubleSafe(record.getAveragePlaytime(), 0.0));
        game.setMedianPlaytime(parseDoubleSafe(record.getMedianPlaytime(), 0.0));

        // Propietarios
        if (record.getOwners() != null && !record.getOwners().trim().isEmpty()) {
            parseOwners(game, record.getOwners().trim());
        } else {
            game.setOwnersLower(0);
            game.setOwnersUpper(0);
            game.setOwnersMid(0);
        }

        // Precio
        game.setPrice(parseBigDecimalSafe(record.getPrice(), BigDecimal.ZERO));

        return game;
    }

    private void parseOwners(Game game, String ownersStr) {
        try {
            if (ownersStr.contains("-")) {
                String[] parts = ownersStr.split("-");
                if (parts.length == 2) {
                    int low = parseIntSafe(parts[0].trim(), 0);
                    int high = parseIntSafe(parts[1].trim(), 0);
                    game.setOwnersLower(low);
                    game.setOwnersUpper(high);
                    game.setOwnersMid((low + high) / 2);
                } else {
                    setDefaultOwners(game);
                }
            } else {
                int val = parseIntSafe(ownersStr.trim(), 0);
                game.setOwnersLower(val);
                game.setOwnersUpper(val);
                game.setOwnersMid(val);
            }
        } catch (Exception e) {
            setDefaultOwners(game);
        }
    }

    private void setDefaultOwners(Game game) {
        game.setOwnersLower(0);
        game.setOwnersUpper(0);
        game.setOwnersMid(0);
    }

    private void processGameRelations(Game game, GameCsvRecord record, CsvImportStatisticsDto stats) {
        // Desarrolladores
        if (record.getDevelopers() != null && !record.getDevelopers().trim().isEmpty()) {
            processMultiValueField(record.getDevelopers(), ";", name -> {
                Developer dev = getOrCreateDeveloper(name, stats);
                if (!game.getDevelopers().contains(dev)) {
                    game.getDevelopers().add(dev);
                }
            });
        }

        // Editores
        if (record.getPublishers() != null && !record.getPublishers().trim().isEmpty()) {
            processMultiValueField(record.getPublishers(), ";", name -> {
                Publisher pub = getOrCreatePublisher(name, stats);
                if (!game.getPublishers().contains(pub)) {
                    game.getPublishers().add(pub);
                }
            });
        }

        // Plataformas
        if (record.getPlatforms() != null && !record.getPlatforms().trim().isEmpty()) {
            processMultiValueField(record.getPlatforms(), ";", name -> {
                Platform platform = getOrCreatePlatform(name, stats);
                if (!game.getPlatforms().contains(platform)) {
                    game.getPlatforms().add(platform);
                }
            });
        }

        // Categoría (tomamos la primera)
        if (record.getCategories() != null && !record.getCategories().trim().isEmpty()) {
            String[] categories = record.getCategories().split(";");
            if (categories.length > 0) {
                String categoryName = categories[0].trim();
                if (!categoryName.isEmpty()) {
                    Category category = getOrCreateCategory(categoryName, stats);
                    game.setCategory(category);
                }
            }
        }

        // Géneros
        if (record.getGenres() != null && !record.getGenres().trim().isEmpty()) {
            processMultiValueField(record.getGenres(), ";", name -> {
                Genre genre = getOrCreateGenre(name, stats);
                if (!game.getGenres().contains(genre)) {
                    game.getGenres().add(genre);
                }
            });
        }
    }

    private void processMultiValueField(String fieldValue, String delimiter,
                                        java.util.function.Consumer<String> processor) {
        String[] values = fieldValue.split(delimiter);
        for (String value : values) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                processor.accept(trimmed);
            }
        }
    }

    // Métodos helper para parsing seguro
    private int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double parseDoubleSafe(String value, double defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private BigDecimal parseBigDecimalSafe(String value, BigDecimal defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Métodos getOrCreate (igual que antes, pero los incluyo por completitud)
    private Developer getOrCreateDeveloper(String name, CsvImportStatisticsDto stats) {
        return developerCache.computeIfAbsent(name, n -> {
            Optional<Developer> existing = developerRepo.findByName(n);
            if (existing.isPresent()) {
                return existing.get();
            } else {
                stats.incrementDevelopersCreated();
                return developerRepo.save(new Developer(n));
            }
        });
    }

    private Publisher getOrCreatePublisher(String name, CsvImportStatisticsDto stats) {
        return publisherCache.computeIfAbsent(name, n -> {
            Optional<Publisher> existing = publisherRepo.findByName(n);
            if (existing.isPresent()) {
                return existing.get();
            } else {
                stats.incrementPublishersCreated();
                return publisherRepo.save(new Publisher(n));
            }
        });
    }

    private Platform getOrCreatePlatform(String name, CsvImportStatisticsDto stats) {
        return platformCache.computeIfAbsent(name, n -> {
            Optional<Platform> existing = platformRepo.findByName(n);
            if (existing.isPresent()) {
                return existing.get();
            } else {
                stats.incrementPlatformsCreated();
                return platformRepo.save(new Platform(n));
            }
        });
    }

    private Category getOrCreateCategory(String name, CsvImportStatisticsDto stats) {
        return categoryCache.computeIfAbsent(name, n -> {
            Optional<Category> existing = categoryRepo.findByName(n);
            if (existing.isPresent()) {
                return existing.get();
            } else {
                stats.incrementCategoriesCreated();
                return categoryRepo.save(new Category(n));
            }
        });
    }

    private Genre getOrCreateGenre(String name, CsvImportStatisticsDto stats) {
        return genreCache.computeIfAbsent(name, n -> {
            Optional<Genre> existing = genreRepo.findByName(n);
            if (existing.isPresent()) {
                return existing.get();
            } else {
                stats.incrementGenresCreated();
                return genreRepo.save(new Genre(n));
            }
        });
    }

    private void saveBatch(List<Game> gamesBatch, CsvImportStatisticsDto stats) {
        if (gamesBatch.isEmpty()) {
            return;
        }

        long batchStartTime = System.currentTimeMillis();

        try {
            for (Game game : gamesBatch) {
                try {
                    gameRepo.save(game);
                } catch (Exception e) {
                    System.err.printf("❌ Error guardando juego %d (%s): %s%n",
                            game.getAppId(), game.getTitle(), e.getMessage());
                    stats.setCreated(stats.getCreated() - 1);
                    stats.incrementSkipped();
                }
            }

            entityManager.flush();

            long batchEndTime = System.currentTimeMillis();
            double batchSeconds = (batchEndTime - batchStartTime) / 1000.0;

            System.out.printf("✅ Batch guardado: %,d juegos | Tiempo: %.2fs | Total creados: %,d%n",
                    gamesBatch.size(), batchSeconds, stats.getCreated());

        } catch (Exception e) {
            System.err.printf("❌ Error crítico en batch: %s%n", e.getMessage());

            // Fallback: guardar individualmente
            int successfullySaved = 0;
            for (Game game : gamesBatch) {
                try {
                    gameRepo.save(game);
                    successfullySaved++;
                } catch (Exception ex) {
                    System.err.printf("   ✗ Error con juego %d: %s%n",
                            game.getAppId(), ex.getMessage());
                }
            }

            // Ajustar estadísticas
            int failed = gamesBatch.size() - successfullySaved;
            stats.setCreated(stats.getCreated() - failed);
            stats.setSkipped(stats.getSkipped() + failed);
        }
    }

    private void printFinalStatistics(CsvImportStatisticsDto stats, long startTime) {
        long endTime = System.currentTimeMillis();
        double totalSeconds = (endTime - startTime) / 1000.0;

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
        System.out.printf("Tiempo total:              %.2f segundos%n", totalSeconds);

        if (totalSeconds > 0) {
            double gamesPerSecond = stats.getCreated() / totalSeconds;
            System.out.printf("Velocidad:                 %.1f juegos/segundo%n", gamesPerSecond);
        }

        try {
            long totalInDB = gameRepo.count();
            System.out.printf("Total juegos en BD:        %,d%n", totalInDB);

            if (stats.getProcessed() > 0) {
                double successRate = (double) stats.getCreated() / stats.getProcessed() * 100;
                System.out.printf("Tasa de éxito:            %.1f%%%n", successRate);
            }
        } catch (Exception e) {
            System.err.println("⚠️  Error al contar juegos en BD: " + e.getMessage());
        }

        System.out.println("=".repeat(70));
    }

    private void clearCaches() {
        developerCache.clear();
        publisherCache.clear();
        platformCache.clear();
        categoryCache.clear();
        genreCache.clear();
    }

    private void clearAllCaches() {
        clearCaches();
        entityManager.clear();
    }
}