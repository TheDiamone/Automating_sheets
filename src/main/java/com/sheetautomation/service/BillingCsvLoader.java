package com.sheetautomation.service;

import com.opencsv.CSVReader;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import com.sheetautomation.config.AppConfig;
import com.sheetautomation.model.BillingCsvData;
import com.sheetautomation.model.BillingRecord;
import com.sheetautomation.util.BillingColumnAnalyzer;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads billing.csv and aggregates rows by Resident Number.
 *
 * Date columns are auto-detected from the CSV header by finding all cells
 * that match the ISO date format YYYY-MM-DD. No billing.first.day.column or
 * billing.last.day.column properties are needed — the CSV is the source of truth.
 *
 * Returns a BillingCsvData result object containing both the aggregated
 * resident records and the ordered date list derived from the CSV header.
 */
public class BillingCsvLoader {

    /**
     * Loads billing.csv and returns a BillingCsvData containing:
     * - one BillingRecord per unique Resident Number (aggregated across all rows)
     * - orderedDates: ISO date strings in CSV header order
     *
     * @param config loaded application.properties
     */
    public BillingCsvData load(AppConfig config) {
        String csvFile    = config.getBillingFile();
        String idHeader   = config.getBillingIdColumn();
        String nameHeader = config.getBillingNameColumn();
        String keepValue  = config.getBillingStatusKeepValue();

        RFC4180Parser parser = new RFC4180ParserBuilder().build();

        Map<String, BillingRecord> byId = new LinkedHashMap<>();
        List<String>  orderedDates  = new ArrayList<>();
        List<Integer> dateColIndexes = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(csvFile))
                .withCSVParser(parser)
                .build()) {

            // ── Header row ────────────────────────────────────────────────────
            String[] header = reader.readNext();
            if (header == null) {
                System.err.println("ERROR: billing.csv is empty: " + csvFile);
                System.exit(1);
            }

            int idCol   = findColumn(header, idHeader,   csvFile);
            int nameCol = findColumn(header, nameHeader, csvFile);

            // Auto-detect ISO date columns (YYYY-MM-DD) in their CSV header order
            for (int i = 0; i < header.length; i++) {
                if (BillingColumnAnalyzer.isIsoDateHeader(header[i])) {
                    orderedDates.add(header[i].trim());
                    dateColIndexes.add(i);
                }
            }

            if (orderedDates.isEmpty()) {
                System.err.println("ERROR: No ISO date columns (YYYY-MM-DD) found in header of " + csvFile);
                System.exit(1);
            }

            // Validate: all dates are in the same month and consecutive
            BillingColumnAnalyzer.validateAllDatesSameMonth(orderedDates);
            BillingColumnAnalyzer.validateConsecutiveDates(orderedDates);

            // ── Data rows ─────────────────────────────────────────────────────
            String[] row;
            int lineNumber = 2;

            while ((row = reader.readNext()) != null) {
                if (row.length <= idCol) {
                    System.err.println("WARNING: Skipping short row at line " + lineNumber);
                    lineNumber++;
                    continue;
                }

                String residentId   = row[idCol].trim();
                String residentName = (row.length > nameCol) ? row[nameCol].trim() : "";

                if (residentId.isEmpty()) {
                    lineNumber++;
                    continue;
                }

                BillingRecord record = byId.computeIfAbsent(
                        residentId, id -> new BillingRecord(id, residentName));

                record.incrementSourceRowCount();

                for (int i = 0; i < orderedDates.size(); i++) {
                    String date   = orderedDates.get(i);
                    int    colIdx = dateColIndexes.get(i);
                    String status = (colIdx < row.length) ? row[colIdx].trim() : "";
                    record.recordDate(date, status, keepValue);
                }

                lineNumber++;
            }

        } catch (IOException e) {
            System.err.println("ERROR: Could not read billing file: " + csvFile);
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (CsvValidationException e) {
            System.err.println("ERROR: Invalid CSV format in: " + csvFile);
            System.err.println(e.getMessage());
            System.exit(1);
        }

        if (byId.isEmpty()) {
            System.err.println("ERROR: No billing records found in " + csvFile);
            System.exit(1);
        }

        return new BillingCsvData(new ArrayList<>(byId.values()), orderedDates);
    }

    /**
     * Finds the 0-based column index for a header string.
     * Stops the program with a clear error if not found.
     */
    private int findColumn(String[] header, String name, String csvFile) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equals(name)) {
                return i;
            }
        }
        System.err.println("ERROR: Column \"" + name + "\" not found in header of " + csvFile);
        System.err.println("Available columns: " + String.join(", ", header));
        System.exit(1);
        throw new RuntimeException("unreachable");
    }
}
