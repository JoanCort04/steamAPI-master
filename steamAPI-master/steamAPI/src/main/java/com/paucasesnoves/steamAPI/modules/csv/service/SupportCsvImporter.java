package com.paucasesnoves.steamAPI.modules.csv.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportStatisticsDto;
import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.GameSupportInfo;
import com.paucasesnoves.steamAPI.modules.games.repository.GameRepository;
import com.paucasesnoves.steamAPI.modules.games.repository.GameSupportInfoRepository;
import com.paucasesnoves.steamAPI.utils.CsvUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class SupportCsvImporter {

    private static final Logger log = LoggerFactory.getLogger(SupportCsvImporter.class);
    private static final int BATCH_SIZE = 1000;

    // Cabecera flexible
    private static final String[][] EXPECTED_HEADER_ALTERNATIVES = {
            {"appid", "steam_appid"},
            {"website"},
            {"support_url", "supporturl"},
            {"support_email", "supportemail"}
    };

    @Autowired private GameRepository gameRepo;
    @Autowired private GameSupportInfoRepository supportRepo;
    @PersistenceContext private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;  // Inyectar TransactionTemplate

    public CsvImportStatisticsDto importCsv(InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        CsvImportStatisticsDto stats = new CsvImportStatisticsDto();

        // 1. Precargar juegos y soporte existente
        Map<Long, Game> gameCache = CsvUtils.buildEntityCache(gameRepo.findAll(), Game::getAppId);
        log.info("📦 Juegos precargados: {} entidades", gameCache.size());

        Set<Long> existingSupportAppIds = CsvUtils.buildExistenceCache(
                supportRepo.findAll(), support -> support.getGame().getAppId());
        log.info("🛟 Soporte existente: {} juegos", existingSupportAppIds.size());

        // 2. Configurar parser CSV
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

            List<GameSupportInfo> batch = new ArrayList<>(BATCH_SIZE);
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

                    if (existingSupportAppIds.contains(appId)) {
                        stats.incrementSkipped();
                        continue;
                    }

                    GameSupportInfo info = new GameSupportInfo();
                    info.setGame(game);
                    info.setWebsite(cleanField(line[1]));
                    info.setSupportUrl(cleanField(line[2]));
                    info.setSupportEmail(cleanField(line[3]));

                    batch.add(info);
                    stats.incrementCreated();

                    if (batch.size() >= BATCH_SIZE) {
                        saveBatchInTransaction(batch, stats);
                        batch.clear();
                    }

                    if (stats.getCreated() % 5000 == 0) {
                        log.info("✅ {} soportes importados...", String.format("%,d", stats.getCreated()));
                    }

                } catch (Exception e) {
                    log.warn("❌ Error línea {}: {}", lineNumber, e.getMessage());
                    if (lineNumber <= 10) {
                        log.debug("Contenido: {}", CsvUtils.truncate(String.join(" | ", line), 200));
                    }
                    stats.incrementSkipped();
                }
            }

            // Guardar último lote
            if (!batch.isEmpty()) {
                saveBatchInTransaction(batch, stats);
            }

            // Estadísticas finales (corregido el formato)
            CsvUtils.logFinalStatistics(stats, startTime, "Soporte");

        } catch (Exception e) {
            log.error("❌ Error crítico en importación de soporte", e);
            throw new RuntimeException("Falló importación de soporte", e);
        }

        return stats;
    }

    /**
     * Guarda un lote en una transacción independiente.
     * Si falla, no afecta a otros lotes.
     */
    private void saveBatchInTransaction(List<GameSupportInfo> batch, CsvImportStatisticsDto stats) {
        try {
            transactionTemplate.execute(status -> {
                supportRepo.saveAll(batch);
                entityManager.flush();
                entityManager.clear();
                return null;
            });
            log.debug("✅ Lote guardado: {} soportes", batch.size());
        } catch (Exception e) {
            log.error("❌ Error guardando lote de {} soportes: {}", batch.size(), e.getMessage());
            // Revertir estadísticas
            stats.setCreated(stats.getCreated() - batch.size());
            stats.setSkipped(stats.getSkipped() + batch.size());
        }
    }

    private String cleanField(String value) {
        if (value == null || value.isBlank() || "None".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }
}