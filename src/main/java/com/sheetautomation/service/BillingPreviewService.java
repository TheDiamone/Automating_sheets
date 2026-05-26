package com.sheetautomation.service;

import com.sheetautomation.config.AppConfig;
import com.sheetautomation.model.BillingCsvData;
import com.sheetautomation.util.BillingColumnAnalyzer;

import java.util.List;

/**
 * Implements the preview-billing-month command.
 *
 * Reads billing.csv, auto-detects the billing month and date columns,
 * calculates the sheet column range, and prints the full date-to-column mapping.
 * Makes no API calls to Google Sheets — safe to run without network access.
 */
public class BillingPreviewService {

    public void previewBillingMonth(AppConfig config) {
        BillingCsvData data = new BillingCsvLoader().load(config);
        List<String> orderedDates = data.getOrderedDates();

        String sheetFirstCol = config.getBillingSheetFirstDayColumn();
        String sheetLastCol  = BillingColumnAnalyzer.calculateLastColumn(sheetFirstCol, orderedDates.size());
        String detectedMonth = BillingColumnAnalyzer.detectMonthFromDates(orderedDates);

        int firstIdx = BillingColumnAnalyzer.letterToIndex(sheetFirstCol);
        int lastIdx  = BillingColumnAnalyzer.letterToIndex(sheetLastCol);

        System.out.println("Billing CSV       : " + config.getBillingFile());
        System.out.println("Detected month    : " + detectedMonth);
        System.out.printf("CSV date columns  : %d%n", orderedDates.size());
        System.out.printf("CSV range         : %s  through  %s%n",
                orderedDates.get(0), orderedDates.get(orderedDates.size() - 1));
        System.out.printf("Sheet first col   : %s  (index %d)%n", sheetFirstCol, firstIdx);
        System.out.printf("Calculated last   : %s  (index %d)%n", sheetLastCol, lastIdx);
        System.out.printf("Sheet mapping     : %s  through  %s%n", sheetFirstCol, sheetLastCol);
        System.out.println();
        System.out.println("Full date-to-column mapping:");

        for (int i = 0; i < orderedDates.size(); i++) {
            String date   = orderedDates.get(i);
            int    colIdx = BillingColumnAnalyzer.datePositionToSheetColumnIndex(sheetFirstCol, i);
            String letter = BillingColumnAnalyzer.indexToColumnLetter(colIdx);
            System.out.printf("  %s -> %s/index %d%n", date, letter, colIdx);
        }

        System.out.println();
        System.out.println("Residents in billing.csv: " + data.getRecords().size());
        System.out.println("No changes were made. This command is read-only.");
    }
}
