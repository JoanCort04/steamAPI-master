package com.paucasesnoves.steamAPI.utils;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportStatisticsDto;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Utilidades centralizadas para importación de archivos CSV en el ecosistema Steam.
 * Incluye parseo, validación, cacheo de entidades, manejo de lotes y estadísticas.
 */
public final class CsvUtils {

    private static final Logger log = LoggerFactory.getLogger(CsvUtils.class);

    // =========================================================================
    // CONFIGURACIÓN DEL PARSER CSV (OpenCSV)
    // =========================================================================

    public static CSVParser createDefaultParser() {
        return new CSVParserBuilder()
                .withSeparator(',')
                .withQuoteChar('"')
                .withEscapeChar('\\')
                .withStrictQuotes(false)
                .withIgnoreLeadingWhiteSpace(true)
                .build();
    }

    public static CSVParser createParser(char separator, char quoteChar, char escapeChar) {
        return new CSVParserBuilder()
                .withSeparator(separator)
                .withQuoteChar(quoteChar)
                .withEscapeChar(escapeChar)
                .withStrictQuotes(false)
                .withIgnoreLeadingWhiteSpace(true)
                .build();
    }

    // =========================================================================
    // VALIDACIÓN DE CABECERAS
    // =========================================================================

    public static boolean isHeaderValid(String[] header, String[] expectedColumns) {
        if (header == null || header.length < expectedColumns.length) {
            return false;
        }
        for (int i = 0; i < expectedColumns.length; i++) {
            if (!expectedColumns[i].equalsIgnoreCase(header[i].trim())) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // PARSEO SEGURO DE TIPOS BÁSICOS
    // =========================================================================

    public static Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static Optional<Integer> parseInt(String value) {
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static Optional<Double> parseDouble(String value) {
        try {
            return Optional.of(Double.parseDouble(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // =========================================================================
    // PARSEO DE CAMPOS ESPECIALES (STEAM)
    // =========================================================================

    /**
     * Parsea el rango de propietarios (ej: "20000-50000" o "100000").
     */
    public static OwnersRange parseOwners(String ownersStr) {
        OwnersRange range = new OwnersRange(0, 0, 0);
        if (ownersStr == null || ownersStr.trim().isEmpty()) {
            return range;
        }
        try {
            if (ownersStr.contains("-")) {
                String[] parts = ownersStr.split("-");
                if (parts.length == 2) {
                    int lower = Integer.parseInt(parts[0].trim());
                    int upper = Integer.parseInt(parts[1].trim());
                    int mid = (lower + upper) / 2;
                    return new OwnersRange(lower, upper, mid);
                }
            } else {
                int val = Integer.parseInt(ownersStr.trim());
                return new OwnersRange(val, val, val);
            }
        } catch (NumberFormatException e) {
            log.warn("Formato de owners inválido: '{}'", ownersStr);
        }
        return range;
    }

    /**
     * Resultado inmutable del parseo de owners.
     */
    public record OwnersRange(int lower, int upper, int mid) {}

    // =========================================================================
    // PATRÓN "FIND-OR-CREATE" PARA STRINGS SEPARADOS POR ';'
    // =========================================================================

    /**
     * Parsea un string con valores separados por ';' y devuelve un Set de entidades.
     * Cada elemento se busca con finder; si no existe, se crea con creator.
     */
    public static <T> Set<T> parseAndFindOrCreate(
            String value,
            Function<String, Optional<T>> finder,
            Function<String, T> creator) {

        Set<T> results = new HashSet<>();
        if (value == null || value.trim().isEmpty()) {
            return results;
        }

        for (String item : value.split(";")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                finder.apply(trimmed).ifPresentOrElse(
                        results::add,
                        () -> results.add(creator.apply(trimmed))
                );
            }
        }
        return results;
    }

    /**
     * Versión simple: busca una entidad por nombre o la crea.
     */
    public static <T> T findOrCreate(
            String name,
            Function<String, Optional<T>> finder,
            Function<String, T> creator) {

        return finder.apply(name).orElseGet(() -> creator.apply(name));
    }

    // =========================================================================
    // CACHÉ DE ENTIDADES (OPTIMIZACIÓN)
    // =========================================================================

    /**
     * Construye un mapa para acceso O(1) a entidades por su ID.
     */
    public static <T, ID> Map<ID, T> buildEntityCache(List<T> entities, Function<T, ID> idMapper) {
        return entities.stream()
                .collect(Collectors.toMap(idMapper, Function.identity(), (e1, e2) -> e1));
    }

    /**
     * Construye un Set con los IDs de entidades que ya existen.
     * Útil para evitar consultas EXISTS en cada línea.
     */
    public static <T, ID> Set<ID> buildExistenceCache(List<T> entities, Function<T, ID> idMapper) {
        return entities.stream()
                .map(idMapper)
                .collect(Collectors.toSet());
    }

    // =========================================================================
    // MANEJO DE LOTES Y LIMPIEZA DEL CONTEXTO DE PERSISTENCIA
    // =========================================================================

    /**
     * Guarda un lote, hace flush/clear y actualiza estadísticas.
     */
    public static <T> void saveBatchAndClear(List<T> batch,
                                             Function<List<T>, Iterable<T>> saveFunction,
                                             CsvImportStatisticsDto stats,
                                             EntityManager entityManager) {
        if (batch.isEmpty()) return;
        try {
            saveFunction.apply(batch);
            entityManager.flush();
            entityManager.clear();
            log.debug("✅ Lote guardado: {} entidades | Total acumulado: {}",
                    batch.size(), stats.getCreated());
        } catch (Exception e) {
            log.error("❌ Error guardando lote de {} entidades: {}",
                    batch.size(), e.getMessage(), e);
            stats.setCreated(stats.getCreated() - batch.size());
            stats.setSkipped(stats.getSkipped() + batch.size());
        }
    }

    /**
     * Versión sin EntityManager (cuando no se necesita flush/clear explícito).
     */
    public static <T> void saveBatch(List<T> batch,
                                     Function<List<T>, Iterable<T>> saveFunction,
                                     CsvImportStatisticsDto stats) {
        if (batch.isEmpty()) return;
        try {
            saveFunction.apply(batch);
            log.debug("✅ Lote guardado: {} entidades | Total: {}",
                    batch.size(), stats.getCreated());
        } catch (Exception e) {
            log.error("❌ Error guardando lote: {}", e.getMessage(), e);
            stats.setCreated(stats.getCreated() - batch.size());
            stats.setSkipped(stats.getSkipped() + batch.size());
        }
    }

    // =========================================================================
    // ESTADÍSTICAS Y LOGGING
    // =========================================================================

    public static void logFinalStatistics(CsvImportStatisticsDto stats,
                                          long startTime,
                                          String entityName) {
        long elapsed = System.currentTimeMillis() - startTime;
        double seconds = elapsed / 1000.0;

        log.info("\n" + "=".repeat(70));
        log.info("📊 ESTADÍSTICAS FINALES - {}", entityName.toUpperCase());
        log.info("=".repeat(70));
        log.info("Líneas procesadas:      {}", String.format("%,d", stats.getProcessed()));
        log.info("Entidades creadas:      {}", String.format("%,d", stats.getCreated()));
        log.info("Registros saltados:     {}", String.format("%,d", stats.getSkipped()));
        log.info("Tiempo total:           {,.2f} segundos", seconds);  // ✅ CORREGIDO
        if (seconds > 0) {
            log.info("Velocidad:              {,.1f} ent/segundo", stats.getCreated() / seconds);
        }
        log.info("=".repeat(70));
    }

    public static InputStream getResourceInputStream(String path) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:" + path);
        if (!resource.exists()) {
            throw new IOException("Recurso no encontrado en classpath: " + path);
        }
        return resource.getInputStream();
    }

    public static boolean resourceExists(String path) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        return resolver.getResource("classpath:" + path).exists();
    }

    // =========================================================================
    // UTILIDADES DE TEXTO
    // =========================================================================

    public static String truncate(String text, int maxLength) {
        if (text == null) return "null";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    // =========================================================================
    // CONSTRUCTOR PRIVADO
    // =========================================================================

    /**
     * Valida la cabecera permitiendo múltiples nombres alternativos por columna.
     * @param header Array con las columnas leídas del CSV
     * @param expectedAlternatives Array de arrays, cada subarray contiene los nombres aceptados para esa columna
     * @return true si la cabecera es válida
     */
    public static boolean isHeaderFlexibleValid(String[] header, String[][] expectedAlternatives) {
        if (header == null || header.length < expectedAlternatives.length) {
            return false;
        }
        for (int i = 0; i < expectedAlternatives.length; i++) {
            String actual = header[i].trim();
            boolean match = Arrays.stream(expectedAlternatives[i])
                    .anyMatch(alt -> alt.equalsIgnoreCase(actual));
            if (!match) {
                return false;
            }
        }
        return true;
    }



    private CsvUtils() {
        // No instanciable
    }
}