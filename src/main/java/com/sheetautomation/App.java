package com.sheetautomation;

import com.sheetautomation.config.AppConfig;
import com.sheetautomation.model.Resident;
import com.sheetautomation.service.BillingPreviewService;
import com.sheetautomation.service.ColumnDeletionService;
import com.sheetautomation.service.GoogleSheetsService;
import com.sheetautomation.service.ResidentLoader;
import com.sheetautomation.service.SheetCreationService;
import com.sheetautomation.service.RowHidingService;
import com.sheetautomation.util.RowAnalyzer;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.Sheet;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entry point for the Google Sheet Automation CLI.
 *
 * Usage:
 *   java -jar google-sheet-automation.jar <command> [--dry-run]
 *
 * Commands:
 *   test-connection            Verify auth and print spreadsheet title
 *   create-tabs                Create one tab per resident in residents.csv
 *   hide-empty-rows            Hide rows with no useful data on all resident sheets
 *   delete-unbilled-columns    Delete date columns with no billed (A) rows from resident tabs
 *   preview-billing-month      Show auto-detected month, date range, and column mapping (read-only)
 *   run                        Auto-detect: create tabs or hide rows as appropriate
 *
 * Add --dry-run to any command to preview without making changes.
 */
public class App {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];
        boolean dryRun = Arrays.asList(args).contains("--dry-run");

        if (dryRun) {
            System.out.println("[DRY-RUN MODE] No changes will be made to the spreadsheet.");
        }

        switch (command) {
            case "test-analyzer" -> {
                // No config or credentials needed — runs entirely offline
                RowAnalyzer.runSelfTest();
                return;
            }
            default -> {} // fall through to load config for all other commands
        }

        AppConfig config = new AppConfig();
        ResidentLoader loader = new ResidentLoader();

        switch (command) {
            case "test-connection" -> {
                GoogleSheetsService sheets = new GoogleSheetsService(config.getCredentialsPath());
                Spreadsheet spreadsheet = sheets.getSpreadsheet(config.getSpreadsheetId());
                System.out.println("Connected successfully!");
                System.out.println("Spreadsheet title : " + spreadsheet.getProperties().getTitle());
                System.out.println("Spreadsheet ID    : " + config.getSpreadsheetId());
                System.out.println("Tabs found        : " + spreadsheet.getSheets().size());
                for (Sheet sheet : spreadsheet.getSheets()) {
                    System.out.println("  - " + sheet.getProperties().getTitle());
                }
            }
            case "create-tabs" -> {
                List<Resident> residents = loader.load(config.getResidentsFile());
                GoogleSheetsService sheets = new GoogleSheetsService(config.getCredentialsPath());
                new SheetCreationService().createTabs(config, sheets, residents, dryRun);
            }
            case "hide-empty-rows" -> {
                GoogleSheetsService sheets = new GoogleSheetsService(config.getCredentialsPath());
                new RowHidingService().hideEmptyRows(config, sheets, dryRun);
            }
            case "delete-unbilled-columns" -> {
                GoogleSheetsService sheets = new GoogleSheetsService(config.getCredentialsPath());
                new ColumnDeletionService().deleteUnbilledColumns(config, sheets, dryRun);
            }
            case "preview-billing-month" -> {
                // Read-only — no Google Sheets API calls
                new BillingPreviewService().previewBillingMonth(config);
            }
            case "run" -> {
                List<Resident> residents = loader.load(config.getResidentsFile());
                GoogleSheetsService sheets = new GoogleSheetsService(config.getCredentialsPath());
                runAutoDetect(config, sheets, residents, loader, dryRun);
            }
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
                System.exit(1);
            }
        }
    }

    /**
     * Smart dispatcher for the "run" command.
     *
     * Decision logic:
     *  1. Find the template sheet — stop if missing.
     *  2. Check how many resident tabs (from residents.csv) already exist.
     *  3. If zero resident tabs exist → run create-tabs.
     *  4. If at least one resident tab exists → run hide-empty-rows.
     *  5. If the spreadsheet has extra sheets but none match residents.csv → warn and stop.
     */
    private static void runAutoDetect(AppConfig config, GoogleSheetsService sheets,
                                      List<Resident> residents, ResidentLoader loader,
                                      boolean dryRun) {

        Spreadsheet spreadsheet = sheets.getSpreadsheet(config.getSpreadsheetId());
        String templateName     = config.getTemplateSheetName();

        // Collect all current tab titles
        Set<String> existingTitles = spreadsheet.getSheets().stream()
                .map(s -> s.getProperties().getTitle())
                .collect(Collectors.toSet());

        // 1. Confirm the template sheet exists
        if (!existingTitles.contains(templateName)) {
            System.err.println("ERROR: Template sheet \"" + templateName + "\" not found.");
            System.err.println("Available sheets:");
            existingTitles.forEach(t -> System.err.println("  - " + t));
            System.err.println("Update template.sheet.name in config/application.properties.");
            System.exit(1);
        }

        // 2. Count how many resident tabs already exist
        Set<String> expectedTabNames = residents.stream()
                .map(Resident::getTabName)
                .collect(Collectors.toSet());

        long matchingTabCount = existingTitles.stream()
                .filter(expectedTabNames::contains)
                .count();

        int nonTemplateTabs = existingTitles.size() - 1; // subtract the template

        System.out.println("Spreadsheet     : " + spreadsheet.getProperties().getTitle());
        System.out.println("Total sheets    : " + existingTitles.size());
        System.out.println("Resident tabs   : " + matchingTabCount + " of " + residents.size() + " expected");
        System.out.println();

        // 3. Only the template exists — create tabs for all residents
        if (matchingTabCount == 0 && nonTemplateTabs == 0) {
            System.out.println("No resident tabs found. Running create-tabs...");
            System.out.println();
            new SheetCreationService().createTabs(config, sheets, residents, dryRun);
            return;
        }

        // 4. At least one resident tab exists — hide empty rows
        if (matchingTabCount > 0) {
            System.out.println("Resident tabs found. Running hide-empty-rows...");
            System.out.println();
            new RowHidingService().hideEmptyRows(config, sheets, dryRun);
            return;
        }

        // 5. Extra sheets exist but none match residents.csv — something looks wrong
        System.err.println("WARNING: The spreadsheet has " + nonTemplateTabs
                + " non-template sheet(s), but none match any name in residents.csv.");
        System.err.println("This may mean you are pointing at the wrong spreadsheet,");
        System.err.println("or residents.csv does not match the existing tab names.");
        System.err.println("Stopping. Use create-tabs or hide-empty-rows directly if you are sure.");
        System.exit(1);
    }

    private static void printUsage() {
        System.out.println("""
                Google Sheet Automation — Usage:

                  java -jar google-sheet-automation.jar <command> [--dry-run]

                Commands:
                  test-analyzer              Run built-in tests for the row detection logic
                  test-connection            Verify auth and print the spreadsheet title
                  create-tabs                Create one tab per resident
                  hide-empty-rows            Hide rows with no useful data
                  delete-unbilled-columns    Delete unbilled date columns from resident tabs
                  preview-billing-month      Show detected month, dates, and column mapping (read-only)
                  run                        Auto-detect and run the right command

                Options:
                  --dry-run            Preview changes without modifying the spreadsheet
                """);
    }
}
