package com.paucasesnoves.steamAPI.modules.csv.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportStatisticsDto;
import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.GameMedia;
import com.paucasesnoves.steamAPI.modules.games.repository.GameMediaRepository;
import com.paucasesnoves.steamAPI.modules.games.repository.GameRepository;
import com.paucasesnoves.steamAPI.utils.CsvUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class MediaCsvImporter {

    private static final Logger log = LoggerFactory.getLogger(MediaCsvImporter.class);
    private static final int BATCH_SIZE = 1000;

    // Cabecera flexible: acepta "appid" o "steam_appid"
    private static final String[][] EXPECTED_HEADER_ALTERNATIVES = {
            {"appid", "steam_appid"},
            {"header_image"},
            {"screenshots"},
            {"background"},
            {"movies"}
    };

    @Autowired
    private GameRepository gameRepo;
    @Autowired
    private GameMediaRepository mediaRepo;
    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public CsvImportStatisticsDto importCsv(InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        CsvImportStatisticsDto stats = new CsvImportStatisticsDto();

        // 1. Precargar juegos y media existente
        Map<Long, Game> gameCache = CsvUtils.buildEntityCache(gameRepo.findAll(), Game::getAppId);
        log.info("📦 Juegos precargados: {} entidades", gameCache.size());

        Set<Long> existingMediaAppIds = CsvUtils.buildExistenceCache(
                mediaRepo.findAll(), media -> media.getGame().getAppId());
        log.info("🖼️ Media existente: {} juegos", existingMediaAppIds.size());

        // 2. Configurar parser CSV y leer cabecera
        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .withCSVParser(CsvUtils.createDefaultParser())
                .build()) {

            String[] header = reader.readNext();
            if (!CsvUtils.isHeaderFlexibleValid(header, EXPECTED_HEADER_ALTERNATIVES)) {
                log.error("❌ Cabecera inválida. Encontrada: {}", Arrays.toString(header));
                return stats;
            }
            log.info("📋 Cabecera válida: {}", String.join(" | ", header));

            List<GameMedia> batch = new ArrayList<>(BATCH_SIZE);
            String[] line;
            int lineNumber = 1;

            while ((line = reader.readNext()) != null) {
                lineNumber++;
                stats.incrementProcessed();

                try {
                    if (line.length < EXPECTED_HEADER_ALTERNATIVES.length) {
                        log.warn("⚠️ Línea {}: solo {} campos (se requieren {})",
                                lineNumber, line.length, EXPECTED_HEADER_ALTERNATIVES.length);
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- AppId ----
                    Long appId = CsvUtils.parseLong(line[0].trim()).orElse(null);
                    if (appId == null) {
                        log.warn("⚠️ Línea {}: appId inválido '{}'", lineNumber, line[0]);
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- Juego existe? ----
                    Game game = gameCache.get(appId);
                    if (game == null) {
                        if (stats.getSkipped() % 1000 == 0) {
                            log.debug("⏭️ Juego {} no encontrado", appId);
                        }
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- Ya tiene media? ----
                    if (existingMediaAppIds.contains(appId)) {
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- Crear GameMedia ----
                    GameMedia media = new GameMedia();
                    media.setGame(game);
                    media.setHeaderImage(parseHeaderImage(line[1]));
                    media.setBackground(parseBackground(line[3]));

                    // ---- Parsear screenshots (JSON) ----
                    List<String> screenshots = parseScreenshots(line[2]);
                    media.getScreenshots().addAll(screenshots);

                    // ---- Parsear movies (JSON) ----
                    List<String> movies = parseMoviesRobust(line[4]);
                    media.getMovies().addAll(movies);

                    batch.add(media);
                    stats.incrementCreated();

                    if (batch.size() >= BATCH_SIZE) {
                        CsvUtils.saveBatchAndClear(batch, mediaRepo::saveAll, stats, entityManager);
                        batch.clear();
                    }

                    if (stats.getCreated() % 5000 == 0) {
                        log.info("✅ {} media importados...", String.format("%,d", stats.getCreated()));
                    }

                } catch (Exception e) {
                    log.warn("❌ Error línea {}: {}", lineNumber, e.getMessage());
                    if (lineNumber <= 10) {
                        log.debug("Contenido: {}", CsvUtils.truncate(String.join(" | ", line), 200));
                    }
                    stats.incrementSkipped();
                }
            }

            // ---- Guardar último lote ----
            if (!batch.isEmpty()) {
                CsvUtils.saveBatchAndClear(batch, mediaRepo::saveAll, stats, entityManager);
            }

            // ---- Estadísticas finales ----
            CsvUtils.logFinalStatistics(stats, startTime, "Media");

        } catch (Exception e) {
            log.error("❌ Error crítico en importación de media", e);
            throw new RuntimeException("Falló importación de media", e);
        }

        return stats;
    }

    // =========================================================================
    // PARSEO DE CAMPOS ESPECÍFICOS
    // =========================================================================

    private String parseHeaderImage(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String parseBackground(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Parsea el JSON de screenshots y extrae la URL 'path_full' de cada objeto.
     * Ejemplo: [{'id':0,'path_thumbnail':'...','path_full':'...'}, ...]
     */
    private List<String> parseScreenshots(String json) {
        if (isEmptyJson(json)) {
            return Collections.emptyList();
        }
        try {
            // Reemplazar comillas simples por dobles para JSON válido
            String validJson = json.replace("'", "\"");
            List<Map<String, Object>> list = objectMapper.readValue(validJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            return list.stream()
                    .map(map -> (String) map.get("path_full"))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("⚠️ Error parseando screenshots JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Parsea el campo 'movies' de forma robusta.
     * El campo puede ser:
     * - Un array JSON vacío: []
     * - Un array con objetos (formato antiguo o nuevo)
     * - Un booleano: True / False (en algunos registros antiguos)
     * - Una cadena vacía
     * - Cualquier otro valor malformado
     */
    private List<String> parseMoviesRobust(String json) {
        if (isEmptyJson(json)) {
            return Collections.emptyList();
        }

        // Si el valor es "True" o "False" (booleano en mayúscula), no hay videos
        if (json.trim().equalsIgnoreCase("true") || json.trim().equalsIgnoreCase("false")) {
            return Collections.emptyList();
        }

        // Intentar parsear como array JSON
        try {
            // Reemplazar comillas simples por dobles
            String validJson = json.replace("'", "\"");
            List<Map<String, Object>> list = objectMapper.readValue(validJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            return extractMovieUrls(list);
        } catch (Exception e) {
            log.debug("No se pudo parsear movies como array JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extrae las URLs de los objetos de películas.
     * Prefiere mp4 sobre webm, y la calidad 'max' si existe.
     */
    private List<String> extractMovieUrls(List<Map<String, Object>> moviesList) {
        List<String> urls = new ArrayList<>();
        for (Map<String, Object> movie : moviesList) {
            // Intentar obtener mp4.max
            Object mp4 = movie.get("mp4");
            if (mp4 instanceof Map) {
                Object max = ((Map<?, ?>) mp4).get("max");
                if (max instanceof String) {
                    urls.add((String) max);
                    continue;
                }
            } else if (mp4 instanceof String) {
                urls.add((String) mp4);
                continue;
            }

            // Si no hay mp4, probar webm.max
            Object webm = movie.get("webm");
            if (webm instanceof Map) {
                Object max = ((Map<?, ?>) webm).get("max");
                if (max instanceof String) {
                    urls.add((String) max);
                }
            } else if (webm instanceof String) {
                urls.add((String) webm);
            }
        }
        return urls;
    }

    /**
     * Determina si un campo JSON está vacío o es nulo.
     */
    private boolean isEmptyJson(String json) {
        return json == null || json.isBlank() || "[]".equals(json.trim()) || "{}".equals(json.trim());
    }
}