package com.paucasesnoves.steamAPI.modules.csv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MediaCsvImporter {

    private static final Logger log = LoggerFactory.getLogger(MediaCsvImporter.class);
    private static final int BATCH_SIZE = 1000;

    // Capçalera flexible: admet "appid" o "steam_appid", i "movies" o "movie"
    private static final String[][] EXPECTED_HEADER_ALTERNATIVES = {
            {"appid", "steam_appid"},
            {"header_image"},
            {"screenshots"},
            {"background"},
            {"movies", "movie"}
    };

    @Autowired
    private GameRepository gameRepo;
    @Autowired
    private GameMediaRepository mediaRepo;
    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Per a extracció per regex com a últim recurs
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\"'\\s,]+");

    @Transactional
    public CsvImportStatisticsDto importCsv(InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        CsvImportStatisticsDto stats = new CsvImportStatisticsDto();

        // 1. Precàrrega de caches
        Map<Long, Game> gameCache = CsvUtils.buildEntityCache(gameRepo.findAll(), Game::getAppId);
        log.info("📦 Jocs precarregats: {} entitats", gameCache.size());

        Set<Long> existingMediaAppIds = CsvUtils.buildExistenceCache(
                mediaRepo.findAll(), media -> media.getGame().getAppId());
        log.info("🖼️ Media existent: {} jocs", existingMediaAppIds.size());

        // 2. Lector CSV
        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .withCSVParser(CsvUtils.createDefaultParser())
                .build()) {

            String[] header = reader.readNext();
            if (!CsvUtils.isHeaderFlexibleValid(header, EXPECTED_HEADER_ALTERNATIVES)) {
                log.error("❌ Capçalera invàlida. Trobada: {}", Arrays.toString(header));
                return stats;
            }
            log.info("📋 Capçalera vàlida: {}", String.join(" | ", header));

            List<GameMedia> batch = new ArrayList<>(BATCH_SIZE);
            String[] line;
            int lineNumber = 1;

            // Comptadors per a diagnòstic
            int totalLinesWithMovies = 0;
            int totalMoviesExtracted = 0;

            while ((line = reader.readNext()) != null) {
                lineNumber++;
                stats.incrementProcessed();

                try {
                    if (line.length < EXPECTED_HEADER_ALTERNATIVES.length) {
                        log.warn("⚠️ Línia {}: només {} camps (se'n requereixen {})",
                                lineNumber, line.length, EXPECTED_HEADER_ALTERNATIVES.length);
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- AppId ----
                    Long appId = CsvUtils.parseLong(line[0].trim()).orElse(null);
                    if (appId == null) {
                        log.warn("⚠️ Línia {}: appId invàlid '{}'", lineNumber, line[0]);
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- El joc existeix? ----
                    Game game = gameCache.get(appId);
                    if (game == null) {
                        if (stats.getSkipped() % 1000 == 0) {
                            log.debug("⏭️ Joc {} no trobat", appId);
                        }
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- Ja té media? ----
                    if (existingMediaAppIds.contains(appId)) {
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- Crear GameMedia ----
                    GameMedia media = new GameMedia();
                    media.setGame(game);
                    media.setHeaderImage(parseHeaderImage(line[1]));
                    media.setBackground(parseBackground(line[3]));

                    // ---- Screenshots ----
                    List<String> screenshots = parseScreenshots(line[2]);
                    if (!screenshots.isEmpty()) {
                        media.getScreenshots().addAll(screenshots);
                    }

                    // ---- MOVIES: parseig ultra robust ----
                    List<String> movies = parseMoviesUltraRobust(line[4], appId, lineNumber);
                    if (!movies.isEmpty()) {
                        media.getMovies().addAll(movies);
                        totalLinesWithMovies++;
                        totalMoviesExtracted += movies.size();
                    }

                    batch.add(media);
                    stats.incrementCreated();

                    // ---- Guardar lot ----
                    if (batch.size() >= BATCH_SIZE) {
                        CsvUtils.saveBatchAndClear(batch, mediaRepo::saveAll, stats, entityManager);
                        batch.clear();
                    }

                    if (stats.getCreated() % 5000 == 0) {
                        log.info("✅ {} media importats...", String.format("%,d", stats.getCreated()));
                    }

                } catch (Exception e) {
                    log.warn("❌ Error línia {}: {}", lineNumber, e.getMessage());
                    if (lineNumber <= 10) {
                        log.debug("Contingut: {}", CsvUtils.truncate(String.join(" | ", line), 200));
                    }
                    stats.incrementSkipped();
                }
            }

            // ---- Últim lot ----
            if (!batch.isEmpty()) {
                CsvUtils.saveBatchAndClear(batch, mediaRepo::saveAll, stats, entityManager);
            }

            // ---- Estadístiques finals amb detall de movies ----
            long elapsed = System.currentTimeMillis() - startTime;
            double seconds = elapsed / 1000.0;
            log.info("\n" + "=".repeat(70));
            log.info("🎬 IMPORTACIÓ DE MEDIA FINALITZADA");
            log.info("=".repeat(70));
            log.info("Línies processades:      {}", String.format("%,d", stats.getProcessed()));
            log.info("Media creats:            {}", String.format("%,d", stats.getCreated()));
            log.info("Línies amb movies:       {}", String.format("%,d", totalLinesWithMovies));
            log.info("Total URLs de movies:    {}", String.format("%,d", totalMoviesExtracted));
            log.info("Línies saltades:         {}", String.format("%,d", stats.getSkipped()));
            log.info("Temps total:             {:.2f} segons", seconds);
            log.info("=".repeat(70));

        } catch (Exception e) {
            log.error("❌ Error crític en importació de media", e);
            throw new RuntimeException("Fallada en importació de media", e);
        }

        return stats;
    }

    // =========================================================================
    // PARSEIG ESPECÍFIC
    // =========================================================================

    private String parseHeaderImage(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String parseBackground(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Parsea el camp JSON de screenshots.
     * Format: [{"id":0,"path_thumbnail":"...","path_full":"..."}, ...]
     */
    private List<String> parseScreenshots(String json) {
        if (isEmptyJson(json)) {
            return Collections.emptyList();
        }
        try {
            String validJson = json.replace("'", "\"");
            List<Map<String, Object>> list = objectMapper.readValue(validJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            return list.stream()
                    .map(map -> (String) map.get("path_full"))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("⚠️ Error parsejant screenshots JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Versió ULTRA ROBUSTA per al camp 'movies'.
     * Suporta TOTS els formats coneguts:
     * - Buit o booleà
     * - Array JSON buit o amb objectes
     * - Objecte JSON únic (sense claudàtors)
     * - Strings amb JSON escapats
     * - Cometes simples o dobles
     * - Fins i tot extracció per regex si res funciona
     */
    private List<String> parseMoviesUltraRobust(String raw, Long appId, int lineNumber) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = raw.trim();

        // Cas booleà (True/False)
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return Collections.emptyList();
        }

        // 1. Normalitzar cometes simples a dobles
        String normalized = trimmed.replace("'", "\"");

        // 2. Intentar parsejar com a llista
        try {
            List<Map<String, Object>> list = objectMapper.readValue(normalized,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<String> urls = extractMovieUrls(list);
            if (!urls.isEmpty()) {
                log.debug("🎬 Línia {} (app {}): {} movies extretes (format array)", lineNumber, appId, urls.size());
                return urls;
            }
        } catch (Exception ignored) {
            // No era un array
        }

        // 3. Intentar parsejar com a objecte únic
        try {
            Map<String, Object> single = objectMapper.readValue(normalized,
                    new TypeReference<Map<String, Object>>() {});
            List<String> urls = extractMovieUrls(Collections.singletonList(single));
            if (!urls.isEmpty()) {
                log.debug("🎬 Línia {} (app {}): {} movies extretes (format objecte)", lineNumber, appId, urls.size());
                return urls;
            }
        } catch (Exception ignored) {
            // No era un objecte
        }

        // 4. Intentar netejar caràcters d'escapament i tornar-ho a provar
        try {
            // Eliminar barres invertides davant de cometes dobles
            String unescaped = normalized.replace("\\\"", "\"");
            if (!unescaped.equals(normalized)) {
                // Tornar a provar com a llista
                List<Map<String, Object>> list = objectMapper.readValue(unescaped,
                        new TypeReference<List<Map<String, Object>>>() {});
                List<String> urls = extractMovieUrls(list);
                if (!urls.isEmpty()) {
                    log.debug("🎬 Línia {} (app {}): {} movies extretes (unescaped)", lineNumber, appId, urls.size());
                    return urls;
                }
            }
        } catch (Exception ignored) {}

        // 5. Últim recurs: extracció per regex (si hi ha alguna URL)
        List<String> regexUrls = extractUrlsWithRegex(trimmed);
        if (!regexUrls.isEmpty()) {
            log.warn("⚠️ Línia {} (app {}): s'han extret {} URLs per regex (format desconegut)",
                    lineNumber, appId, regexUrls.size());
            return regexUrls;
        }

        // Si no s'ha trobat res
        if (log.isDebugEnabled()) {
            log.debug("🎬 Línia {} (app {}): cap movie trobada (valor: {})",
                    lineNumber, appId, CsvUtils.truncate(trimmed, 100));
        }
        return Collections.emptyList();
    }

    /**
     * Extreu URLs de mp4 o webm, preferint mp4.max.
     */
    private List<String> extractMovieUrls(List<Map<String, Object>> moviesList) {
        List<String> urls = new ArrayList<>();
        for (Map<String, Object> movie : moviesList) {
            // mp4.max
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

            // webm.max
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
     * Extrau qualsevol URL que sembli un vídeo (mp4, webm) mitjançant regex.
     * Últim recurs per a formats corruptes.
     */
    private List<String> extractUrlsWithRegex(String text) {
        List<String> urls = new ArrayList<>();
        Matcher m = URL_PATTERN.matcher(text);
        while (m.find()) {
            String url = m.group();
            if (url.contains(".mp4") || url.contains(".webm") || url.contains("steam")) {
                urls.add(url);
            }
        }
        return urls;
    }

    private boolean isEmptyJson(String json) {
        return json == null || json.isBlank() || "[]".equals(json.trim()) || "{}".equals(json.trim());
    }
}