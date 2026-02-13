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

        // 1. Precargar juegos existentes
        Map<Long, Game> gameCache = CsvUtils.buildEntityCache(gameRepo.findAll(), Game::getAppId);
        log.info("📦 Juegos precargados: {} entidades", gameCache.size());

        // 2. Cache de tags existentes (por nombre)
        Map<String, Tag> tagCache = new HashMap<>();
        tagRepo.findAll().forEach(tag -> tagCache.put(tag.getName(), tag));
        log.info("🏷️ Tags existentes: {} nombres", tagCache.size());

        // 3. Leer el CSV
        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .withCSVParser(CsvUtils.createDefaultParser())
                .build()) {

            String[] header = reader.readNext(); // Primera línea: cabecera
            if (header == null || header.length < 2) {
                log.error("❌ Cabecera inválida o vacía");
                return stats;
            }

            // Los nombres de los tags están en las columnas a partir de la segunda
            String[] tagNames = Arrays.copyOfRange(header, 1, header.length);
            log.info("📋 Se encontraron {} columnas de tags: {}", tagNames.length, String.join(", ", tagNames));

            // 4. Preparar estructuras para nuevos tags y relaciones
            List<Tag> newTagsBatch = new ArrayList<>(BATCH_SIZE);
            Map<Long, Set<String>> gameTagsToAdd = new HashMap<>(); // appId -> set de nombres de tags

            String[] line;
            int lineNumber = 1;

            while ((line = reader.readNext()) != null) {
                lineNumber++;
                stats.incrementProcessed();

                try {
                    if (line.length < 2) {
                        log.warn("⚠️ Línea {}: menos de 2 columnas, se ignora", lineNumber);
                        stats.incrementSkipped();
                        continue;
                    }

                    Long appId = CsvUtils.parseLong(line[0].trim()).orElse(null);
                    if (appId == null) {
                        log.warn("⚠️ Línea {}: appId inválido '{}'", lineNumber, line[0]);
                        stats.incrementSkipped();
                        continue;
                    }

                    Game game = gameCache.get(appId);
                    if (game == null) {
                        if (stats.getSkipped() % 1000 == 0) {
                            log.debug("⏭️ Juego {} no encontrado", appId);
                        }
                        stats.incrementSkipped();
                        continue;
                    }

                    // Procesar cada columna de tag
                    for (int i = 1; i < line.length && i - 1 < tagNames.length; i++) {
                        String tagName = tagNames[i - 1]; // Nombre del tag desde la cabecera
                        String valueStr = line[i].trim();

                        // Si el valor es numérico y positivo, consideramos que el tag aplica
                        if (valueStr.isEmpty() || "0".equals(valueStr)) {
                            continue;
                        }

                        // Intentar parsear como número; si es positivo, agregar tag
                        try {
                            int value = Integer.parseInt(valueStr);
                            if (value <= 0) continue;
                        } catch (NumberFormatException e) {
                            // Si no es número, podría ser un valor directo (raro), pero lo ignoramos
                            continue;
                        }

                        // Buscar o crear el tag
                        Tag tag = tagCache.get(tagName);
                        if (tag == null) {
                            tag = new Tag(tagName);
                            tagCache.put(tagName, tag);
                            newTagsBatch.add(tag);
                            stats.incrementTagsCreated();
                        }

                        // Programar relación game-tag
                        gameTagsToAdd.computeIfAbsent(appId, k -> new HashSet<>()).add(tagName);
                    }

                    // Guardar lote de tags nuevos si es necesario
                    if (newTagsBatch.size() >= BATCH_SIZE) {
                        saveNewTagsBatch(newTagsBatch, tagCache, stats);
                        newTagsBatch.clear();
                    }

                } catch (Exception e) {
                    log.warn("❌ Error línea {}: {}", lineNumber, e.getMessage());
                    if (lineNumber <= 10) {
                        log.debug("Contenido: {}", CsvUtils.truncate(String.join(" | ", line), 200));
                    }
                    stats.incrementSkipped();
                }
            }

            // Guardar último lote de tags nuevos
            if (!newTagsBatch.isEmpty()) {
                saveNewTagsBatch(newTagsBatch, tagCache, stats);
            }

            // 5. Asociar tags a los juegos
            int relationsInserted = associateTagsToGames(gameTagsToAdd, tagCache);
            stats.setCreated(relationsInserted); // usamos created para contar relaciones

            // 6. Estadísticas finales
            long elapsed = System.currentTimeMillis() - startTime;
            double seconds = elapsed / 1000.0;
            log.info("\n" + "=".repeat(70));
            log.info("🏷️ IMPORTACIÓN DE TAGS FINALIZADA");
            log.info("=".repeat(70));
            log.info("Líneas procesadas:      {}", String.format("%,d", stats.getProcessed()));
            log.info("Tags nuevos creados:    {}", String.format("%,d", stats.getTagsCreated()));
            log.info("Relaciones game-tag:    {}", String.format("%,d", stats.getCreated()));
            log.info("Líneas saltadas:        {}", String.format("%,d", stats.getSkipped()));
            log.info("Tiempo total:           {:.2f} segundos", seconds);
            log.info("=".repeat(70));

        } catch (Exception e) {
            log.error("❌ Error crítico en importación de tags", e);
            throw new RuntimeException("Falló importación de tags", e);
        }

        return stats;
    }

    /**
     * Guarda un lote de tags nuevos y actualiza la caché.
     */
    private void saveNewTagsBatch(List<Tag> batch, Map<String, Tag> tagCache, CsvImportStatisticsDto stats) {
        if (batch.isEmpty()) return;
        try {
            Iterable<Tag> saved = tagRepo.saveAll(batch);
            for (Tag savedTag : saved) {
                tagCache.put(savedTag.getName(), savedTag);
            }
            log.debug("✅ Lote de {} tags guardado", batch.size());
        } catch (Exception e) {
            log.error("❌ Error guardando lote de {} tags: {}", batch.size(), e.getMessage(), e);
            stats.setTagsCreated(stats.getTagsCreated() - batch.size());
            stats.setSkipped(stats.getSkipped() + batch.size());
        }
    }

    /**
     * Asocia los tags a los juegos (añade a la colección y guarda).
     */
    private int associateTagsToGames(Map<Long, Set<String>> gameTagsToAdd,
                                     Map<String, Tag> tagCache) {
        if (gameTagsToAdd.isEmpty()) return 0;

        log.info("🔄 Asociando tags a {} juegos...", gameTagsToAdd.size());
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
            entityManager.flush();
            // No hacemos clear para no perder el contexto de la transacción principal
        }

        log.info("✅ {} relaciones game-tag insertadas", totalRelations);
        return totalRelations;
    }
}