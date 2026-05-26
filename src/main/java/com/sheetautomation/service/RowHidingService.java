package com.sheetautomation.service;

import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DimensionProperties;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateDimensionPropertiesRequest;
import com.sheetautomation.config.AppConfig;
import com.sheetautomation.model.BillingCsvData;
import com.sheetautomation.util.BillingColumnAnalyzer;
import com.sheetautomation.util.RowAnalyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads each resident sheet and hides rows that contain no charge value
 * in the billing day columns.
 *
 * Rule: a row is kept visible only if at least one day-column cell contains
 * a whole integer from 1 to 10 (inclusive). All other values — blanks, text
 * like A/D/HN, decimals, numbers outside 1–10 — do not count.
 *
 * The day column range is determined from billing.csv automatically:
 * the program detects the billing month and computes the last sheet column
 * from billing.sheet.first.day.column + (date count - 1).
 *
 * Dry-run mode prints which rows would be hidden without touching the API.
 */
public class RowHidingService {

    /**
     * Main entry point for the hide-empty-rows command.
     *
     * @param config        loaded application.properties
     * @param sheetsService authenticated API client
     * @param dryRun        true = print only, false = call the API
     */
    public void hideEmptyRows(AppConfig config, GoogleSheetsService sheetsService, boolean dryRun) {

        String spreadsheetId = config.getSpreadsheetId();
        String templateName  = config.getTemplateSheetName();
        int    startRow      = config.getHideStartRow();
        int    endRow        = config.getHideEndRow();
        long   sleepMs       = config.getSleepBetweenSheets();

        // ── Detect day column range from billing.csv ──────────────────────────
        BillingCsvData data = new BillingCsvLoader().load(config);
        List<String> orderedDates = data.getOrderedDates();
        String sheetFirstCol  = config.getBillingSheetFirstDayColumn();
        String sheetLastCol   = BillingColumnAnalyzer.calculateLastColumn(sheetFirstCol, orderedDates.size());
        String detectedMonth  = BillingColumnAnalyzer.detectMonthFromDates(orderedDates);

        Spreadsheet spreadsheet = sheetsService.getSpreadsheet(spreadsheetId);
        List<Sheet> sheets      = spreadsheet.getSheets();

        System.out.println("Dry-run mode  : " + (dryRun ? "YES — no changes will be made" : "NO — changes will be applied"));
        System.out.println("Billing month : " + detectedMonth);
        System.out.println("Day columns   : " + sheetFirstCol + " through " + sheetLastCol
                + "  (" + orderedDates.size() + " columns)");
        System.out.println("Row range     : " + startRow + " – " + endRow);
        System.out.println("Skipping      : " + templateName + " (template)");
        System.out.println("Keep rule     : any day cell with a whole number 1–10");
        System.out.println();

        int totalHidden = 0;

        for (Sheet sheet : sheets) {
            String sheetName = sheet.getProperties().getTitle();

            if (sheetName.equals(templateName)) {
                continue;
            }

            System.out.println("Processing: " + sheetName);

            // Read only the day columns for each row (not the entire row A:Z)
            String range = "'" + sheetName + "'!" + sheetFirstCol + startRow + ":" + sheetLastCol + endRow;
            List<List<Object>> rawValues = sheetsService.getSheetValues(spreadsheetId, range);

            List<Integer> rowsToHide = new ArrayList<>();

            for (int i = 0; i < (endRow - startRow + 1); i++) {
                List<String> dayCells = new ArrayList<>();
                if (i < rawValues.size()) {
                    for (Object obj : rawValues.get(i)) {
                        dayCells.add(obj != null ? obj.toString().trim() : "");
                    }
                }
                // Convert loop offset to 0-based sheet row index
                int sheetRowIndex = (startRow - 1) + i;

                if (!RowAnalyzer.isRowWithCharge(dayCells)) {
                    rowsToHide.add(sheetRowIndex);
                }
            }

            if (rowsToHide.isEmpty()) {
                System.out.println("  No rows to hide.");
                sleepBetweenSheets(sleepMs);
                continue;
            }

            if (dryRun) {
                for (int rowIndex : rowsToHide) {
                    System.out.println("  [DRY-RUN] Would hide row " + (rowIndex + 1));
                }
            } else {
                int sheetId = sheet.getProperties().getSheetId();
                List<Request> requests = buildHideRequests(sheetId, rowsToHide, true);

                BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                        .setRequests(requests);

                sheetsService.batchUpdate(spreadsheetId, body);
                System.out.println("  Hidden " + rowsToHide.size() + " rows.");
            }

            totalHidden += rowsToHide.size();

            sleepBetweenSheets(sleepMs);
        }

        System.out.println();
        if (dryRun) {
            System.out.println("Dry-run complete. No changes were made.");
            System.out.println("Would hide: " + totalHidden + " rows across all sheets.");
        } else {
            System.out.println("Done.");
            System.out.println("Total rows hidden: " + totalHidden);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Request> buildHideRequests(int sheetId, List<Integer> rowIndexes, boolean hide) {
        List<Request> requests = new ArrayList<>();
        for (int rowIndex : rowIndexes) {
            DimensionRange range = new DimensionRange()
                    .setSheetId(sheetId)
                    .setDimension("ROWS")
                    .setStartIndex(rowIndex)
                    .setEndIndex(rowIndex + 1);

            DimensionProperties props = new DimensionProperties()
                    .setHiddenByUser(hide);

            UpdateDimensionPropertiesRequest updateRequest = new UpdateDimensionPropertiesRequest()
                    .setRange(range)
                    .setProperties(props)
                    .setFields("hiddenByUser");

            requests.add(new Request().setUpdateDimensionProperties(updateRequest));
        }
        return requests;
    }

    private void sleepBetweenSheets(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
