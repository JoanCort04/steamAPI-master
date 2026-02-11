package com.paucasesnoves.steamAPI.modules.csv.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportStatisticsDto;
import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.GameRequirements;
import com.paucasesnoves.steamAPI.modules.games.repository.GameRepository;
import com.paucasesnoves.steamAPI.modules.games.repository.GameRequirementsRepository;
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
public class RequirementsCsvImporter {

    private static final Logger log = LoggerFactory.getLogger(RequirementsCsvImporter.class);
    private static final int BATCH_SIZE = 1000;

    // Cabecera flexible: acepta "appid" o "steam_appid"
    private static final String[][] EXPECTED_HEADER_ALTERNATIVES = {
            {"appid", "steam_appid"},
            {"pc_requirements"},
            {"mac_requirements"},
            {"linux_requirements"},
            {"minimum"},
            {"recommended"}
    };

    @Autowired
    private GameRepository gameRepo;
    @Autowired
    private GameRequirementsRepository requirementsRepo;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public CsvImportStatisticsDto importCsv(InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        CsvImportStatisticsDto stats = new CsvImportStatisticsDto();

        // 1. Precargar juegos y requisitos existentes
        Map<Long, Game> gameCache = CsvUtils.buildEntityCache(gameRepo.findAll(), Game::getAppId);
        log.info("📦 Juegos precargados: {} entidades", gameCache.size());

        Set<Long> existingRequirementsAppIds = CsvUtils.buildExistenceCache(
                requirementsRepo.findAll(), req -> req.getGame().getAppId());
        log.info("📋 Requisitos existentes: {} juegos", existingRequirementsAppIds.size());

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

            List<GameRequirements> batch = new ArrayList<>(BATCH_SIZE);
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

                    // ---- Ya tiene requisitos? ----
                    if (existingRequirementsAppIds.contains(appId)) {
                        stats.incrementSkipped();
                        continue;
                    }

                    // ---- Crear GameRequirements ----
                    GameRequirements requirements = new GameRequirements();
                    requirements.setGame(game);
                    requirements.setPcRequirements(cleanRequirementField(line[1]));
                    requirements.setMacRequirements(cleanRequirementField(line[2]));
                    requirements.setLinuxRequirements(cleanRequirementField(line[3]));
                    requirements.setMinimum(cleanTextField(line[4]));
                    requirements.setRecommended(cleanTextField(line[5]));

                    batch.add(requirements);
                    stats.incrementCreated();

                    if (batch.size() >= BATCH_SIZE) {
                        CsvUtils.saveBatchAndClear(batch, requirementsRepo::saveAll, stats, entityManager);
                        batch.clear();
                    }

                    if (stats.getCreated() % 5000 == 0) {
                        log.info("✅ {} requisitos importados...", String.format("%,d", stats.getCreated()));
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
                CsvUtils.saveBatchAndClear(batch, requirementsRepo::saveAll, stats, entityManager);
            }

            // ---- Estadísticas finales ----
            CsvUtils.logFinalStatistics(stats, startTime, "Requisitos");

        } catch (Exception e) {
            log.error("❌ Error crítico en importación de requisitos", e);
            throw new RuntimeException("Falló importación de requisitos", e);
        }

        return stats;
    }

    // =========================================================================
    // UTILIDADES DE LIMPIEZA DE CAMPOS
    // =========================================================================

    /**
     * Limpia los campos de requisitos (pc, mac, linux) que vienen como
     * strings con formato {'minimum': '...', 'recommended': '...'}
     * Si el valor es null, vacío o "None", devuelve null.
     */
    private String cleanRequirementField(String value) {
        if (value == null || value.isBlank() || "None".equalsIgnoreCase(value.trim())) {
            return null;
        }
        // Eliminar posibles llaves externas y espacios
        String trimmed = value.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            // Devuelve el contenido sin las llaves (puede seguir siendo JSON-like)
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * Limpia campos de texto plano (minimum, recommended).
     */
    private String cleanTextField(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}