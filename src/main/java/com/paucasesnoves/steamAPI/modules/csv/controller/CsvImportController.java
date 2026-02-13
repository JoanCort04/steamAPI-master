package com.paucasesnoves.steamAPI.modules.csv.controller;

import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportResultDto;
import com.paucasesnoves.steamAPI.exception.DatabaseNotEmptyException;
import com.paucasesnoves.steamAPI.modules.csv.service.CsvImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsvImportController {

    private final CsvImportService csvImportService;

    @Autowired
    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping("/api/import")
    public ResponseEntity<CsvImportResultDto> importCsv() {
        try {
            // Intenta realizar la importación (solo tendrá éxito si la BD está vacía)
            CsvImportResultDto result = csvImportService.importAllCsv();
            return ResponseEntity.ok(result);
        } catch (DatabaseNotEmptyException e) {
            // Caso 1: La base de datos ya contiene juegos → se rechaza la importación
            CsvImportResultDto errorResult = new CsvImportResultDto();
            errorResult.setStatus("REJECTED: " + e.getMessage());
            errorResult.setImportedGames(0);
            errorResult.setDevelopers(0);
            errorResult.setPublishers(0);
            errorResult.setGenres(0);
            errorResult.setTags(0);
            errorResult.setSkippedLines(0);
            errorResult.setDurationSeconds(0);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResult); // 409 Conflict
        } catch (Exception e) {

            e.printStackTrace();
            CsvImportResultDto errorResult = new CsvImportResultDto();
            errorResult.setStatus("ERROR: " + e.getMessage());
            errorResult.setImportedGames(0);
            errorResult.setDevelopers(0);
            errorResult.setPublishers(0);
            errorResult.setGenres(0);
            errorResult.setTags(0);
            errorResult.setSkippedLines(0);
            errorResult.setDurationSeconds(0);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
}