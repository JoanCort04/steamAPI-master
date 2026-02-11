package com.paucasesnoves.steamAPI.modules.csv.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportStatisticsDto;
import com.paucasesnoves.steamAPI.utils.CsvUtils;
import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.GameDescription;
import com.paucasesnoves.steamAPI.modules.games.repository.GameDescriptionRepository;
import com.paucasesnoves.steamAPI.modules.games.repository.GameRepository;
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
public class DescriptionCsvImporter {

    private static final Logger log = LoggerFactory.getLogger(DescriptionCsvImporter.class);
    private static final int BATCH_SIZE = 1000;
    private static final String[] EXPECTED_HEADER = {
            "steam_appid", "detailed_description", "about_the_game", "short_description"
    };

    @Autowired
    private GameRepository gameRepo;
    @Autowired
    private GameDescriptionRepository descriptionRepo;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public CsvImportStatisticsDto importCsv(InputStream inputStream) {
        CsvImportStatisticsDto stats = new CsvImportStatisticsDto();
        long startTime = System.currentTimeMillis();

        // 1. Cache de juegos
        Map<Long, Game> gameCache = CsvUtils.buildEntityCache(
                gameRepo.findAll(), Game::getAppId);
        log.info("📦 Cache de juegos cargado: {} entidades", gameCache.size());

        // 2. Conjunto de juegos que YA TIENEN descripción (para evitar existsByGame)
        Set<Long> existingDescriptions = descriptionRepo.findAll().stream()
                .map(desc -> desc.getGame().getAppId())
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        log.info("🔍 Descripciones existentes: {} juegos", existingDescriptions.size());

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .withCSVParser(CsvUtils.createDefaultParser())
                .build()) {

            String[] header = reader.readNext();
            if (!CsvUtils.isHeaderValid(header, EXPECTED_HEADER)) {
                log.error("❌ Cabecera inválida");
                return stats;
            }

            List<GameDescription> batch = new ArrayList<>(BATCH_SIZE);
            String[] line;
            int lineNumber = 1;

            while ((line = reader.readNext()) != null) {
                lineNumber++;
                stats.incrementProcessed();

                try {
                    if (line.length < 4) {
                        stats.incrementSkipped();
                        continue;
                    }

                    Long appId = CsvUtils.parseLong(line[0].trim()).orElse(null);
                    if (appId == null) {
                        stats.incrementSkipped();
                        continue;
                    }

                    Game game = gameCache.get(appId);
                    if (game == null || existingDescriptions.contains(appId)) {
                        stats.incrementSkipped();
                        continue;
                    }

                    GameDescription desc = new GameDescription();
                    desc.setGame(game);
                    desc.setDetailedDescription(line[1].trim());
                    desc.setAboutTheGame(line[2].trim());
                    desc.setShortDescription(line[3].trim());

                    batch.add(desc);
                    stats.incrementCreated();

                    if (batch.size() >= BATCH_SIZE) {
                        CsvUtils.saveBatchAndClear(batch, descriptionRepo::saveAll,
                                stats, entityManager);
                        batch.clear();
                    }

                    if (stats.getCreated() % 5000 == 0) {
                        log.info("✅ {} descripciones importadas...", stats.getCreated());
                    }

                } catch (Exception e) {
                    log.warn("⚠️ Error línea {}: {}", lineNumber, e.getMessage());
                    stats.incrementSkipped();
                }
            }

            if (!batch.isEmpty()) {
                CsvUtils.saveBatchAndClear(batch, descriptionRepo::saveAll,
                        stats, entityManager);
            }

            CsvUtils.logFinalStatistics(stats, startTime, "Descripciones");

        } catch (Exception e) {
            log.error("❌ Error fatal", e);
            throw new RuntimeException("Falló importación de descripciones", e);
        }

        return stats;
    }
}