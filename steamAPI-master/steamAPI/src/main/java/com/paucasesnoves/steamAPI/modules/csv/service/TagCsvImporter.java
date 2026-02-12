package com.paucasesnoves.steamAPI.modules.csv.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportStatisticsDto;
import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.Tag;
import com.paucasesnoves.steamAPI.modules.games.repository.GameRepository;
import com.paucasesnoves.steamAPI.modules.games.repository.TagRepository;
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
public class TagCsvImporter {

    private static final Logger log = LoggerFactory.getLogger(TagCsvImporter.class);
    private static final int BATCH_SIZE = 1000;

    private static final String[][] EXPECTED_HEADER_ALTERNATIVES = {
            {"appid", "steam_appid"}
    };

    @Autowired
    private GameRepository gameRepo;
    @Autowired
    private TagRepository tagRepo;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public CsvImportStatisticsDto importCsv(InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        CsvImportStatisticsDto stats = new CsvImportStatisticsDto();

        // 1. Precargar caches
        Map<Long, Game> gameCache = CsvUtils.buildEntityCache(gameRepo.findAll(), Game::getAppId);
        log.info("📦 Jocs precarregats: {} entitats", gameCache.size());

        Map<String, Tag> tagCache = new HashMap<>();
        for (Tag tag : tagRepo.findAll()) {
            tagCache.put(tag.getName(), tag);
        }
        log.info("🏷️ Tags existents: {} noms", tagCache.size());

        // 2. Estructures per al processament
        List<Tag> newTagsBatch = new ArrayList<>(BATCH_SIZE);
        Map<Long, Set<String>> gameTagsToAdd = new HashMap<>();

        // 3. Lectura del CSV
        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .withCSVParser(CsvUtils.createDefaultParser())
                .build()) {

            String[] header = reader.readNext();
            if (!CsvUtils.isHeaderFlexibleValid(header, EXPECTED_HEADER_ALTERNATIVES)) {
                log.error("❌ Capçalera invàlida. Primera columna ha de ser 'appid' o 'steam_appid'. Trobat: {}",
                        header.length > 0 ? header[0] : "buit");
                return stats;
            }
            log.info("📋 Capçalera: {} columnes (la primera és appid)", header.length);

            String[] line;
            int lineNumber = 1;

            while ((line = reader.readNext()) != null) {
                lineNumber++;
                stats.incrementProcessed();

                try {
                    if (line.length < 2) {
                        log.warn("⚠️ Línia {}: menys de 2 columnes, s'ignora", lineNumber);
                        stats.incrementSkipped();
                        continue;
                    }

                    Long appId = CsvUtils.parseLong(line[0].trim()).orElse(null);
                    if (appId == null) {
                        log.warn("⚠️ Línia {}: appId invàlid '{}'", lineNumber, line[0]);
                        stats.incrementSkipped();
                        continue;
                    }

                    Game game = gameCache.get(appId);
                    if (game == null) {
                        if (stats.getSkipped() % 1000 == 0) {
                            log.debug("⏭️ Joc {} no trobat", appId);
                        }
                        stats.incrementSkipped();
                        continue;
                    }

                    for (int i = 1; i < line.length; i++) {
                        String tagName = line[i].trim();
                        if (tagName.isEmpty()) continue;

                        Tag tag = tagCache.get(tagName);
                        if (tag == null) {
                            tag = new Tag(tagName);
                            tagCache.put(tagName, tag);
                            newTagsBatch.add(tag);
                            stats.incrementTagsCreated();
                        }

                        gameTagsToAdd.computeIfAbsent(appId, k -> new HashSet<>()).add(tagName);
                    }

                    // Guardar lot de tags nous
                    if (newTagsBatch.size() >= BATCH_SIZE) {
                        saveNewTagsBatch(newTagsBatch, tagCache, stats);
                        newTagsBatch.clear();
                    }

                } catch (Exception e) {
                    log.warn("❌ Error línia {}: {}", lineNumber, e.getMessage());
                    if (lineNumber <= 10) {
                        log.debug("Contingut: {}", CsvUtils.truncate(String.join(" | ", line), 200));
                    }
                    stats.incrementSkipped();
                }
            }

            // Guardar últim lot de tags nous
            if (!newTagsBatch.isEmpty()) {
                saveNewTagsBatch(newTagsBatch, tagCache, stats);
            }

            // 4. ASSOCIAR TAGS ALS JOCS (dins de la mateixa transacció)
            int relationsInserted = associateTagsToGames(gameTagsToAdd, tagCache);
            stats.setCreated(relationsInserted);

            // 5. Estadístiques finals
            long elapsed = System.currentTimeMillis() - startTime;
            double seconds = elapsed / 1000.0;
            log.info("\n" + "=".repeat(70));
            log.info("🏷️ IMPORTACIÓ DE TAGS FINALITZADA");
            log.info("=".repeat(70));
            log.info("Línies processades:      {}", String.format("%,d", stats.getProcessed()));
            log.info("Tags creats:             {}", String.format("%,d", stats.getTagsCreated()));
            log.info("Relacions game-tag:      {}", String.format("%,d", stats.getCreated()));
            log.info("Línies saltades:         {}", String.format("%,d", stats.getSkipped()));
            log.info("Temps total:             {:.2f} segons", seconds);
            log.info("=".repeat(70));

        } catch (Exception e) {
            log.error("❌ Error crític en importació de tags", e);
            throw new RuntimeException("Fallada en importació de tags", e);
        }

        return stats;
    }

    /**
     * Guarda un lot de tags nous i actualitza la cache.
     * S'executa dins de la transacció principal.
     */
    private void saveNewTagsBatch(List<Tag> batch, Map<String, Tag> tagCache, CsvImportStatisticsDto stats) {
        if (batch.isEmpty()) return;
        try {
            Iterable<Tag> saved = tagRepo.saveAll(batch);
            // Com que estem en una transacció, no fem flush/clear encara per no perdre el context
            for (Tag savedTag : saved) {
                tagCache.put(savedTag.getName(), savedTag);
            }
            log.debug("✅ Lote de {} tags guardado", batch.size());
        } catch (Exception e) {
            log.error("❌ Error guardant lot de {} tags: {}", batch.size(), e.getMessage(), e);
            stats.setTagsCreated(stats.getTagsCreated() - batch.size());
            stats.setSkipped(stats.getSkipped() + batch.size());
        }
    }

    /**
     * ASSOCIACIÓ DE TAGS ALS JOCS. Es crida dins de la mateixa transacció.
     */
    private int associateTagsToGames(Map<Long, Set<String>> gameTagsToAdd,
                                     Map<String, Tag> tagCache) {
        if (gameTagsToAdd.isEmpty()) return 0;

        log.info("🔄 Associant tags a {} jocs...", gameTagsToAdd.size());

        List<Game> gamesToUpdate = gameRepo.findAllById(gameTagsToAdd.keySet());
        int totalRelations = 0;

        for (Game game : gamesToUpdate) {
            Set<String> tagNames = gameTagsToAdd.get(game.getAppId());
            if (tagNames == null) continue;

            for (String tagName : tagNames) {
                Tag tag = tagCache.get(tagName);
                if (tag != null && tag.getId() != null) {
                    if (game.getTags().add(tag)) {
                        totalRelations++;
                    }
                }
            }
        }

        if (!gamesToUpdate.isEmpty()) {
            gameRepo.saveAll(gamesToUpdate);
            // Podem fer flush aquí si volem alliberar memòria, però el clear trencaria la tx
            entityManager.flush();
            // entityManager.clear(); // NO fer clear encara, perquè després es puguin usar els jocs
        }

        log.info("✅ {} relacions game-tag inserides via JPA", totalRelations);
        return totalRelations;
    }
}