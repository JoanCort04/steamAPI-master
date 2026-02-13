package com.paucasesnoves.steamAPI.modules.csv.service;

import com.paucasesnoves.steamAPI.exception.DatabaseNotEmptyException;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportResultDto;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportStatisticsDto;
import com.paucasesnoves.steamAPI.modules.games.repository.GameRepository;
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

    // 🔥 Repositori per comprovar si hi ha jocs a la BD
    @Autowired private GameRepository gameRepository;

    // Rutes dels fitxers CSV al classpath
    private static final String[] CSV_FILES = {
            "data/steam.csv",
            "data/steamspy_tag_data.csv",
            "data/steam_description_data.csv",
            "data/steam_media_data.csv",
            "data/steam_requirements_data.csv",
            "data/steam_support_info.csv"
    };

    public CsvImportResultDto importAllCsv() {
        // 🔥 Comprovació: si la BD ja té jocs, llencem excepció
        if (gameRepository.count() > 0) {
            throw new DatabaseNotEmptyException("La base de dades ja conté jocs. Importació rebutjada per evitar duplicats.");
        }

        long globalStartTime = System.currentTimeMillis();
        CsvImportResultDto result = new CsvImportResultDto();

        // Acumuladors
        int totalSkipped = 0;
        int totalGames = 0;
        int totalDevelopers = 0;
        int totalPublishers = 0;
        int totalGenres = 0;
        int totalTags = 0;
        boolean hasErrors = false;

        log.info("=== 🚀 INICIANT IMPORTACIÓ COMPLETA DE DADES STEAM ===");

        // 1. Importar jocs (steam.csv)
        try {
            log.info("📁 [1/6] Important jocs...");
            CsvImportStatisticsDto stats = executeImport(gameImporter::importCsv, CSV_FILES[0]);
            totalGames += stats.getCreated();
            totalDevelopers += stats.getDevelopersCreated();
            totalPublishers += stats.getPublishersCreated();
            totalGenres += stats.getGenresCreated();
            totalSkipped += stats.getSkipped();
            log.info("✅ Jocs: {} creats ({} saltats)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallada en importació de jocs: {}", e.getMessage(), e);
        }

        // 2. Importar tags (steamspy_tag_data.csv)
        try {
            log.info("📁 [2/6] Important tags...");
            CsvImportStatisticsDto stats = executeImport(tagImporter::importCsv, CSV_FILES[1]);
            totalTags += stats.getCreated();
            totalSkipped += stats.getSkipped();
            log.info("✅ Tags: {} creats ({} saltats)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallada en importació de tags: {}", e.getMessage(), e);
        }

        // 3. Importar descripcions (steam_description_data.csv)
        try {
            log.info("📁 [3/6] Important descripcions...");
            CsvImportStatisticsDto stats = executeImport(descriptionImporter::importCsv, CSV_FILES[2]);
            totalSkipped += stats.getSkipped();
            log.info("✅ Descripcions: {} creades ({} saltades)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallada en importació de descripcions: {}", e.getMessage(), e);
        }

        // 4. Importar media (steam_media_data.csv)
        try {
            log.info("📁 [4/6] Important media...");
            CsvImportStatisticsDto stats = executeImport(mediaImporter::importCsv, CSV_FILES[3]);
            totalSkipped += stats.getSkipped();
            log.info("✅ Media: {} registres creats ({} saltats)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallada en importació de media: {}", e.getMessage(), e);
        }

        // 5. Importar requisits (steam_requirements_data.csv)
        try {
            log.info("📁 [5/6] Important requisits...");
            CsvImportStatisticsDto stats = executeImport(requirementsImporter::importCsv, CSV_FILES[4]);
            totalSkipped += stats.getSkipped();
            log.info("✅ Requisits: {} registres creats ({} saltats)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallada en importació de requisits: {}", e.getMessage(), e);
        }

        // 6. Importar suport (steam_support_info.csv)
        try {
            log.info("📁 [6/6] Important suport...");
            CsvImportStatisticsDto stats = executeImport(supportImporter::importCsv, CSV_FILES[5]);
            totalSkipped += stats.getSkipped();
            log.info("✅ Suport: {} registres creats ({} saltats)",
                    String.format("%,d", stats.getCreated()),
                    String.format("%,d", stats.getSkipped()));
        } catch (Exception e) {
            hasErrors = true;
            log.error("❌ Fallada en importació de suport: {}", e.getMessage(), e);
        }

        // ===== Construir resultat =====
        double totalSeconds = (System.currentTimeMillis() - globalStartTime) / 1000.0;
        result.setStatus(hasErrors ? "PARCIAL" : "OK");
        result.setImportedGames(totalGames);
        result.setDevelopers(totalDevelopers);
        result.setPublishers(totalPublishers);
        result.setGenres(totalGenres);
        result.setTags(totalTags);
        result.setSkippedLines(totalSkipped);
        result.setDurationSeconds(totalSeconds);

        log.info("\n" + "=".repeat(70));
        log.info("📊 RESUM GLOBAL D'IMPORTACIÓ");
        log.info("=".repeat(70));
        log.info("Estat:               {}", result.getStatus());
        log.info("Jocs importats:      {}", String.format("%,d", result.getImportedGames()));
        log.info("Desenvolupadors:     {}", String.format("%,d", result.getDevelopers()));
        log.info("Editors:             {}", String.format("%,d", result.getPublishers()));
        log.info("Gèneres:             {}", String.format("%,d", result.getGenres()));
        log.info("Tags:                {}", String.format("%,d", result.getTags()));
        log.info("Línies saltades:     {}", String.format("%,d", result.getSkippedLines()));
        log.info("Temps total:         {} segons", String.format("%,.2f", result.getDurationSeconds()));
        log.info("=".repeat(70));

        return result;
    }

    /**
     * Executa un importador amb gestió de recursos i excepcions.
     */
    private CsvImportStatisticsDto executeImport(ThrowingFunction<InputStream, CsvImportStatisticsDto> importer,
                                                 String resourcePath) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:" + resourcePath);
        if (!resource.exists()) {
            throw new IOException("Fitxer no trobat: " + resourcePath);
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
    // MÈTODES PER A IMPORTACIÓ INDIVIDUAL (delegació)
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
        StringBuilder sb = new StringBuilder("=== FITXERS TROBATS A /data ===\n");
        for (String path : CSV_FILES) {
            boolean exists = resolver.getResource("classpath:" + path).exists();
            sb.append(exists ? "✓ " : "✗ ").append(path).append("\n");
        }
        return sb.toString();
    }
}