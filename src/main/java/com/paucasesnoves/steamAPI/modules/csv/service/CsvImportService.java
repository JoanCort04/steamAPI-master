package com.paucasesnoves.steamAPI.modules.csv.service;

import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportResultDto;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportStatisticsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    @Autowired private GameCsvImporter gameImporter;
    @Autowired private TagCsvImporter tagImporter;
    @Autowired private DescriptionCsvImporter descriptionImporter;
    @Autowired private MediaCsvImporter mediaImporter;
    @Autowired private RequirementsCsvImporter requirementsImporter;
    @Autowired private SupportCsvImporter supportImporter;

    // Rutas de los archivos CSV en classpath
    private static final String[] CSV_FILES = {
            "data/steam.csv",
            "data/steamspy_tag_data.csv",
            "data/steam_description_data.csv",
            "data/steam_media_data.csv",
            "data/steam_requirements_data.csv",
            "data/steam_support_info.csv"
    };

    public CsvImportResultDto importAllCsv() {
        long globalStartTime = System.currentTimeMillis();
        CsvImportResultDto result = new CsvImportResultDto();

        // Acumuladores
        int totalSkipped = 0;
        int totalGames = 0;
        int totalDevelopers = 0;
        int totalPublishers = 0;
        int totalGenres = 0;
        int totalTags = 0;
        boolean hasErrors = false;

        log.info("=== 🚀 INICIANDO IMPORTACIÓN COMPLETA DE DATOS STEAM ===");

        // 1. Importar juegos (steam.csv)
        try {
            log.info("📁 [1/6] Importando juegos...");
            CsvImportStatisticsDto stats = executeImport(gameImporter::importCsv, CSV_FILES[0]);
            totalGames += stats.getCreated();
            totalDevelopers += stats.getDevelopersCreated();
            totalPublishers += stats.getPublishersCreated();
            totalGenres += stats.getGenresCreated();
            totalSkipped += stats.getSkipped();
            log.info("✅ Juegos: {} creados ({} saltados)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallo en importación de juegos: {}", e.getMessage(), e);
        }

        // 2. Importar tags (steamspy_tag_data.csv)
        try {
            log.info("📁 [2/6] Importando tags...");
            CsvImportStatisticsDto stats = executeImport(tagImporter::importCsv, CSV_FILES[1]);
            totalTags += stats.getCreated();
            totalSkipped += stats.getSkipped();
            log.info("✅ Tags: {} creados ({} saltados)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallo en importación de tags: {}", e.getMessage(), e);
        }

        // 3. Importar descripciones (steam_description_data.csv)
        try {
            log.info("📁 [3/6] Importando descripciones...");
            CsvImportStatisticsDto stats = executeImport(descriptionImporter::importCsv, CSV_FILES[2]);
            totalSkipped += stats.getSkipped();
            log.info("✅ Descripciones: {} creadas ({} saltados)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallo en importación de descripciones: {}", e.getMessage(), e);
        }

        // 4. Importar media (steam_media_data.csv)
        try {
            log.info("📁 [4/6] Importando media...");
            CsvImportStatisticsDto stats = executeImport(mediaImporter::importCsv, CSV_FILES[3]);
            totalSkipped += stats.getSkipped();
            log.info("✅ Media: {} registros creados ({} saltados)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallo en importación de media: {}", e.getMessage(), e);
        }

        // 5. Importar requisitos (steam_requirements_data.csv)
        try {
            log.info("📁 [5/6] Importando requisitos...");
            CsvImportStatisticsDto stats = executeImport(requirementsImporter::importCsv, CSV_FILES[4]);
            totalSkipped += stats.getSkipped();
            log.info("✅ Requisitos: {} registros creados ({} saltados)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallo en importación de requisitos: {}", e.getMessage(), e);
        }

        // 6. Importar soporte (steam_support_info.csv)
        try {
            log.info("📁 [6/6] Importando soporte...");
            CsvImportStatisticsDto stats = executeImport(supportImporter::importCsv, CSV_FILES[5]);
            totalSkipped += stats.getSkipped();
            log.info("✅ Soporte: {} registros creados ({} saltados)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallo en importación de soporte: {}", e.getMessage(), e);
        }

        // ===== Construir resultado =====
        double totalSeconds = (System.currentTimeMillis() - globalStartTime) / 1000.0;
        result.setStatus(hasErrors ? "PARTIAL" : "OK");
        result.setImportedGames(totalGames);
        result.setDevelopers(totalDevelopers);
        result.setPublishers(totalPublishers);
        result.setGenres(totalGenres);
        result.setTags(totalTags);
        result.setSkippedLines(totalSkipped);
        result.setDurationSeconds(totalSeconds);

        log.info("\n" + "=".repeat(70));
        log.info("📊 RESUMEN GLOBAL DE IMPORTACIÓN");
        log.info("=".repeat(70));
        log.info("Estado:               {}", result.getStatus());
        log.info("Juegos importados:    {}", String.format("%,d", result.getImportedGames()));
        log.info("Desarrolladores:      {}", String.format("%,d", result.getDevelopers()));
        log.info("Editores:             {}", String.format("%,d", result.getPublishers()));
        log.info("Géneros:              {}", String.format("%,d", result.getGenres()));
        log.info("Tags:                 {}", String.format("%,d", result.getTags()));
        log.info("Líneas saltadas:      {}", String.format("%,d", result.getSkippedLines()));
        log.info("Tiempo total:         {} segundos", String.format("%,.2f", result.getDurationSeconds()));
        log.info("=".repeat(70));

        return result;
    }

    /**
     * Ejecuta un importador con manejo de recursos y excepciones.
     */
    private CsvImportStatisticsDto executeImport(ThrowingFunction<InputStream, CsvImportStatisticsDto> importer,
                                                 String resourcePath) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:" + resourcePath);
        if (!resource.exists()) {
            throw new IOException("Archivo no encontrado: " + resourcePath);
        }
        try (InputStream is = resource.getInputStream()) {
            return importer.apply(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }

    // =================================================================
    // MÉTODOS PARA IMPORTACIÓN INDIVIDUAL (delegación)
    // =================================================================
    public CsvImportStatisticsDto importGamesOnly() throws Exception {
        return executeImport(gameImporter::importCsv, CSV_FILES[0]);
    }

    public CsvImportStatisticsDto importTagsOnly() throws Exception {
        return executeImport(tagImporter::importCsv, CSV_FILES[1]);
    }

    public CsvImportStatisticsDto importDescriptionsOnly() throws Exception {
        return executeImport(descriptionImporter::importCsv, CSV_FILES[2]);
    }

    public CsvImportStatisticsDto importMediaOnly() throws Exception {
        return executeImport(mediaImporter::importCsv, CSV_FILES[3]);
    }

    public CsvImportStatisticsDto importRequirementsOnly() throws Exception {
        return executeImport(requirementsImporter::importCsv, CSV_FILES[4]);
    }

    public CsvImportStatisticsDto importSupportOnly() throws Exception {
        return executeImport(supportImporter::importCsv, CSV_FILES[5]);
    }

    public String checkFiles() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        StringBuilder sb = new StringBuilder("=== ARCHIVOS ENCONTRADOS EN /data ===\n");
        for (String path : CSV_FILES) {
            boolean exists = resolver.getResource("classpath:" + path).exists();
            sb.append(exists ? "✓ " : "✗ ").append(path).append("\n");
        }
        return sb.toString();
    }
}