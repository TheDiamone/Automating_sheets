package com.sheetautomation.service;

import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.sheetautomation.config.AppConfig;
import com.sheetautomation.model.BillingCsvData;
import com.sheetautomation.model.BillingRecord;
import com.sheetautomation.util.BillingColumnAnalyzer;
import com.sheetautomation.util.SheetTitleMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the delete-unbilled-columns command.
 *
 * Dry-run: reads spreadsheet metadata for tab matching but sends zero batchUpdate requests.
 * Live:    requires billing.allow.delete.columns=true and sends one batchUpdate per resident.
 *
 * The sheet last column is calculated automatically from the number of ISO date columns
 * detected in billing.csv — no billing.sheet.last.day.column config property is needed.
 *
 * Column deletion order is always right-to-left (descending index) so that earlier
 * deletions do not shift the indexes of later columns within the same batch.
 */
public class ColumnDeletionService {

    public void deleteUnbilledColumns(AppConfig config,
                                      GoogleSheetsService sheetsService,
                                      boolean dryRun) {

        // ── Safety guard for live mode ─────────────────────────────────────────
        if (!dryRun && !config.isBillingAllowDeleteColumns()) {
            System.err.println("ERROR: Live column deletion is disabled.");
            System.err.println("Set  billing.allow.delete.columns=true  in application.properties");
            System.err.println("to enable it. Use --dry-run to preview safely without changing anything.");
            System.exit(1);
        }

        // ── Load and aggregate billing.csv ────────────────────────────────────
        BillingCsvData data = new BillingCsvLoader().load(config);
        List<BillingRecord> records = data.getRecords();
        List<String> orderedDates   = data.getOrderedDates();

        // ── Auto-calculate sheet column range ─────────────────────────────────
        String sheetFirstCol = config.getBillingSheetFirstDayColumn();
        String sheetLastCol  = BillingColumnAnalyzer.calculateLastColumn(sheetFirstCol, orderedDates.size());
        String detectedMonth = BillingColumnAnalyzer.detectMonthFromDates(orderedDates);

        // ── Validate column counts before any destructive call ────────────────
        BillingColumnAnalyzer.validateColumnCounts(orderedDates, sheetFirstCol, sheetLastCol);

        // ── Fetch spreadsheet metadata (read-only) ────────────────────────────
        String spreadsheetId = config.getSpreadsheetId();
        Spreadsheet spreadsheet = sheetsService.getSpreadsheet(spreadsheetId);
        List<Sheet> allSheets = spreadsheet.getSheets();
        String templateName   = config.getTemplateSheetName();
        long sleepMs          = config.getBillingSleepBetweenSheets();

        // ── Mode header ───────────────────────────────────────────────────────
        if (dryRun) {
            System.out.println("[DRY-RUN MODE] No destructive changes will be made.");
            System.out.println("  (getSpreadsheet is called for tab matching — no batchUpdate requests sent.)");
        } else {
            System.out.println("WARNING: This will permanently delete columns from Google Sheets.");
            System.out.println("Spreadsheet: " + spreadsheet.getProperties().getTitle());
        }
        System.out.println();
        printBillingMonthHeader(detectedMonth, orderedDates, sheetFirstCol, sheetLastCol);

        int totalResidents = 0;
        int totalColumns   = 0;

        // ── Process each resident ─────────────────────────────────────────────
        for (BillingRecord record : records) {

            String residentId   = record.getResidentId();
            String residentName = record.getResidentName();

            Sheet matched = SheetTitleMatcher.findSheet(allSheets, residentId, templateName);
            if (matched == null) {
                continue; // warning already printed by SheetTitleMatcher
            }

            String tabTitle = matched.getProperties().getTitle();
            int sheetId     = matched.getProperties().getSheetId();

            List<String> unbilledDates = BillingColumnAnalyzer.getUnbilledDates(
                    record.getBilledByDate(), orderedDates);

            // Convert unbilled dates to 0-based sheet column indexes
            List<Integer> colIndexes = new ArrayList<>();
            for (String date : unbilledDates) {
                int offset = orderedDates.indexOf(date);
                int colIdx = BillingColumnAnalyzer.datePositionToSheetColumnIndex(sheetFirstCol, offset);
                colIndexes.add(colIdx);
            }

            // Sort DESCENDING — critical so left-to-right deletions don't shift later indexes
            colIndexes.sort(Comparator.reverseOrder());

            totalResidents++;
            totalColumns += colIndexes.size();

            if (dryRun) {
                printDryRun(record, tabTitle, orderedDates, unbilledDates, colIndexes, sheetFirstCol);
            } else {
                if (colIndexes.isEmpty()) {
                    System.out.println("Patient: " + residentName + " (" + residentId + ")");
                    System.out.println("  Matched tab: \"" + tabTitle + "\"");
                    System.out.println("  No unbilled columns — nothing to delete.");
                    System.out.println();
                } else {
                    List<Request> requests = buildDeleteRequests(sheetId, colIndexes);
                    BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
                            .setRequests(requests);
                    sheetsService.batchUpdate(spreadsheetId, body);
                    System.out.println("Deleted " + colIndexes.size()
                            + " columns from \"" + tabTitle + "\".");
                }
            }

            sleepBetweenSheets(sleepMs);
        }

        // ── Summary ───────────────────────────────────────────────────────────
        System.out.println();
        if (dryRun) {
            System.out.println("Dry-run complete.");
            System.out.println("  Residents matched  : " + totalResidents);
            System.out.println("  Columns to delete  : " + totalColumns);
            System.out.println("  batchUpdate calls  : 0  (dry-run)");
        } else {
            System.out.println("Done.");
            System.out.println("  Residents processed: " + totalResidents);
            System.out.println("  Columns deleted    : " + totalColumns);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void printBillingMonthHeader(String detectedMonth,
                                         List<String> orderedDates,
                                         String sheetFirstCol,
                                         String sheetLastCol) {
        int firstIdx = BillingColumnAnalyzer.letterToIndex(sheetFirstCol);
        int lastIdx  = BillingColumnAnalyzer.letterToIndex(sheetLastCol);
        System.out.println("Billing month detection:");
        System.out.printf("  Detected month    : %s%n", detectedMonth);
        System.out.printf("  CSV date columns  : %d%n", orderedDates.size());
        System.out.printf("  CSV range         : %s through %s%n",
                orderedDates.get(0), orderedDates.get(orderedDates.size() - 1));
        System.out.printf("  Sheet first col   : %s  (index %d)%n", sheetFirstCol, firstIdx);
        System.out.printf("  Calculated last   : %s  (index %d)%n", sheetLastCol, lastIdx);
        System.out.println();
    }

    private void printDryRun(BillingRecord record,
                             String tabTitle,
                             List<String> orderedDates,
                             List<String> unbilledDates,
                             List<Integer> sortedDescColIndexes,
                             String sheetFirstCol) {

        String residentId   = record.getResidentId();
        String residentName = record.getResidentName();

        System.out.println("[DRY-RUN] Patient: " + residentName + " (" + residentId + ")");
        System.out.println("  Matched tab: \"" + tabTitle + "\"");

        // Full date→column mapping
        System.out.println("  Mapping:");
        for (int i = 0; i < orderedDates.size(); i++) {
            String date   = orderedDates.get(i);
            int    colIdx = BillingColumnAnalyzer.datePositionToSheetColumnIndex(sheetFirstCol, i);
            String letter = BillingColumnAnalyzer.indexToColumnLetter(colIdx);
            System.out.printf("    %s -> %s/index %d%n", date, letter, colIdx);
        }

        // Kept columns
        System.out.println("  Keep:");
        boolean anyKept = false;
        for (String date : orderedDates) {
            if (!unbilledDates.contains(date)) {
                int offset = orderedDates.indexOf(date);
                int colIdx = BillingColumnAnalyzer.datePositionToSheetColumnIndex(sheetFirstCol, offset);
                System.out.printf("    %s -> %s/index %d  (has A)%n",
                        date, BillingColumnAnalyzer.indexToColumnLetter(colIdx), colIdx);
                anyKept = true;
            }
        }
        if (!anyKept) System.out.println("    (none)");

        // Columns to delete (in original date order for readability)
        System.out.println("  Delete:");
        if (unbilledDates.isEmpty()) {
            System.out.println("    (none)");
        } else {
            for (String date : unbilledDates) {
                int offset = orderedDates.indexOf(date);
                int colIdx = BillingColumnAnalyzer.datePositionToSheetColumnIndex(sheetFirstCol, offset);
                System.out.printf("    %s -> %s/index %d%n",
                        date, BillingColumnAnalyzer.indexToColumnLetter(colIdx), colIdx);
            }
            System.out.println("  Would delete " + unbilledDates.size() + " columns.");

            // Delete order (right to left — sortedDescColIndexes is already descending)
            System.out.println("  Delete order (right to left):");
            for (int colIdx : sortedDescColIndexes) {
                System.out.printf("    %s/index %d%n", BillingColumnAnalyzer.indexToColumnLetter(colIdx), colIdx);
            }
        }
        System.out.println();
    }

    /**
     * Builds one DeleteDimensionRequest per column index.
     * colIndexes must already be sorted descending by the caller.
     */
    private List<Request> buildDeleteRequests(int sheetId, List<Integer> colIndexes) {
        List<Request> requests = new ArrayList<>();
        for (int colIdx : colIndexes) {
            DimensionRange range = new DimensionRange()
                    .setSheetId(sheetId)
                    .setDimension("COLUMNS")
                    .setStartIndex(colIdx)       // 0-based, inclusive
                    .setEndIndex(colIdx + 1);    // exclusive

            requests.add(new Request().setDeleteDimension(
                    new DeleteDimensionRequest().setRange(range)));
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
