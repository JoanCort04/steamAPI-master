package com.paucasesnoves.steamAPI.modules.csv.controller;

import com.paucasesnoves.steamAPI.modules.csv.dto.CsvImportResultDto;
import com.paucasesnoves.steamAPI.modules.csv.service.CsvImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class CsvImportController {
    private CsvImportService csvImportService;


    @Autowired
    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @GetMapping("/import")
    public CsvImportResultDto importCsv() {
        try {
            return csvImportService.importAllCsv();
        } catch (Exception e) {
            e.printStackTrace();
            CsvImportResultDto result = new CsvImportResultDto();
            result.setStatus("ERROR: " + e.getMessage());
            result.setImportedGames(0);
            result.setDevelopers(0);
            result.setPublishers(0);
            result.setGenres(0);
            result.setTags(0);
            result.setSkippedLines(0);
            result.setDurationSeconds(0);
            return result;
        }
    }
}