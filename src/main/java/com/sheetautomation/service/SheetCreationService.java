package com.sheetautomation.service;

import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DuplicateSheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.sheetautomation.config.AppConfig;
import com.sheetautomation.model.Resident;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates one Google Sheet tab per resident by duplicating the template sheet.
 *
 * Dry-run mode prints exactly what would happen without calling the API.
 * Run with --dry-run first, then without it to apply changes.
 */
public class SheetCreationService {

    /**
     * Main entry point for the create-tabs command.
     *
     * @param config        loaded application.properties
     * @param sheetsService authenticated API client
     * @param residents     list loaded from residents.csv
     * @param dryRun        true = print only, false = call the API
     */
    public void createTabs(AppConfig config, GoogleSheetsService sheetsService,
                           List<Resident> residents, boolean dryRun) {

        String spreadsheetId   = config.getSpreadsheetId();
        String templateName    = config.getTemplateSheetName();

        // Fetch current spreadsheet state
        Spreadsheet spreadsheet = sheetsService.getSpreadsheet(spreadsheetId);
        List<Sheet> sheets      = spreadsheet.getSheets();

        // Find the template sheet by name (not by index — avoids the Python bug)
        Sheet templateSheet = findSheetByName(sheets, templateName);
        if (templateSheet == null) {
            System.err.println("ERROR: Template sheet \"" + templateName + "\" not found.");
            System.err.println("Available sheets:");
            sheets.forEach(s -> System.err.println("  - " + s.getProperties().getTitle()));
            System.err.println("Update template.sheet.name in config/application.properties.");
            System.exit(1);
        }

        int templateSheetId = templateSheet.getProperties().getSheetId();

        // Build a set of existing tab titles for fast duplicate detection
        Set<String> existingTitles = sheets.stream()
                .map(s -> s.getProperties().getTitle())
                .collect(Collectors.toSet());

        int created = 0;
        int skipped = 0;
        int errors  = 0;

        System.out.println("Template sheet  : " + templateName);
        System.out.println("Residents loaded: " + residents.size());
        System.out.println("Dry-run mode    : " + (dryRun ? "YES — no changes will be made" : "NO — changes will be applied"));
        System.out.println();

        for (Resident resident : residents) {
            String tabName = resident.getTabName();

            if (existingTitles.contains(tabName)) {
                System.out.println("[SKIP]    " + tabName);
                skipped++;
                continue;
            }

            if (dryRun) {
                System.out.println("[DRY-RUN] Would create: " + tabName);
                created++;
                continue;
            }

            // Live mode: duplicate the template and name the new tab in one request
            try {
                duplicateSheet(sheetsService, spreadsheetId, templateSheetId, tabName, sheets.size());
                System.out.println("[CREATE]  " + tabName);
                created++;

                // Track the new title so subsequent residents don't think it's missing
                existingTitles.add(tabName);

            } catch (Exception e) {
                System.err.println("[ERROR]   " + tabName + " — " + e.getMessage());
                errors++;
            }
        }

        // Summary
        System.out.println();
        if (dryRun) {
            System.out.println("Dry-run complete. No changes were made.");
            System.out.println("Would create: " + created + " | Would skip: " + skipped);
        } else {
            System.out.println("Done.");
            System.out.println("Created: " + created + " | Skipped: " + skipped + " | Errors: " + errors);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Finds a sheet in the list by its title. Returns null if not found.
     */
    private Sheet findSheetByName(List<Sheet> sheets, String name) {
        return sheets.stream()
                .filter(s -> s.getProperties().getTitle().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Duplicates the template sheet and gives the new tab the resident's name.
     * DuplicateSheetRequest supports setting newSheetName directly, so one
     * batchUpdate call is enough — no separate rename needed.
     *
     * @param insertIndex position to insert the new sheet (appends at end)
     */
    private void duplicateSheet(GoogleSheetsService sheetsService, String spreadsheetId,
                                int templateSheetId, String newSheetName, int insertIndex) {

        DuplicateSheetRequest duplicateRequest = new DuplicateSheetRequest()
                .setSourceSheetId(templateSheetId)
                .setInsertSheetIndex(insertIndex)
                .setNewSheetName(newSheetName);

        List<Request> requests = new ArrayList<>();
        requests.add(new Request().setDuplicateSheet(duplicateRequest));

        BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                .setRequests(requests);

        sheetsService.batchUpdate(spreadsheetId, body);
    }
}
