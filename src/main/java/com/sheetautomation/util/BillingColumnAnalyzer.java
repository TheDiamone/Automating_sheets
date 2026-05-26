package com.sheetautomation.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure logic utilities for billing column operations.
 * No I/O, no API calls — safe to unit test independently.
 */
public class BillingColumnAnalyzer {

    // ── ISO date detection ────────────────────────────────────────────────────

    /** Returns true if the header string looks like an ISO date: YYYY-MM-DD */
    public static boolean isIsoDateHeader(String header) {
        return header != null && header.trim().matches("\\d{4}-\\d{2}-\\d{2}");
    }

    /**
     * Scans a CSV header row and returns all ISO date strings in their
     * original column order. Non-date headers (Resident Name, Room, etc.) are skipped.
     */
    public static List<String> extractBillingDatesFromCsvHeaders(String[] headers) {
        List<String> dates = new ArrayList<>();
        for (String h : headers) {
            if (isIsoDateHeader(h)) {
                dates.add(h.trim());
            }
        }
        return dates;
    }

    // ── Month detection and validation ────────────────────────────────────────

    /**
     * Returns the "YYYY-MM" month string detected from the date list,
     * e.g. ["2026-05-01", ...] → "2026-05".
     */
    public static String detectMonthFromDates(List<String> dates) {
        if (dates.isEmpty()) {
            System.err.println("ERROR: No ISO date columns detected in billing.csv.");
            System.exit(1);
        }
        return dates.get(0).substring(0, 7);
    }

    /**
     * Stops the program if the dates span more than one calendar month.
     * All dates must share the same "YYYY-MM" prefix.
     */
    public static void validateAllDatesSameMonth(List<String> dates) {
        if (dates.isEmpty()) return;
        String expectedMonth = dates.get(0).substring(0, 7);
        for (String date : dates) {
            if (!date.startsWith(expectedMonth)) {
                System.err.println("ERROR: billing.csv contains dates from multiple months.");
                System.err.println("  Expected month : " + expectedMonth);
                System.err.println("  Found date     : " + date + "  (month: " + date.substring(0, 7) + ")");
                System.err.println("Export billing.csv for a single month only.");
                System.exit(1);
            }
        }
    }

    /**
     * Stops the program if dates are not consecutive calendar days.
     * Catches both gaps (missing day) and duplicates.
     */
    public static void validateConsecutiveDates(List<String> dates) {
        if (dates.size() < 2) return;
        for (int i = 1; i < dates.size(); i++) {
            LocalDate prev = LocalDate.parse(dates.get(i - 1));
            LocalDate curr = LocalDate.parse(dates.get(i));
            if (curr.equals(prev)) {
                System.err.println("ERROR: Duplicate date in billing.csv headers: " + curr);
                System.exit(1);
            }
            if (!curr.equals(prev.plusDays(1))) {
                System.err.println("ERROR: Non-consecutive dates in billing.csv headers.");
                System.err.println("  " + prev + " is followed by " + curr + " (expected " + prev.plusDays(1) + ")");
                System.exit(1);
            }
        }
    }

    // ── Column letter math ────────────────────────────────────────────────────

    /**
     * Converts a spreadsheet column letter to a 0-based column index.
     *
     * A=0, B=1, C=2, Z=25, AA=26, AG=32
     *
     * Single letter:  index = ch - 'A'
     * Double letter:  index = (first - 'A' + 1) * 26 + (second - 'A')
     */
    public static int letterToIndex(String colLetter) {
        String upper = colLetter.trim().toUpperCase();
        if (upper.length() == 1) {
            return upper.charAt(0) - 'A';
        } else if (upper.length() == 2) {
            int first  = upper.charAt(0) - 'A' + 1;
            int second = upper.charAt(1) - 'A';
            return first * 26 + second;
        }
        throw new IllegalArgumentException("Unsupported column letter: " + colLetter);
    }

    /**
     * Converts a 0-based column index to a spreadsheet column letter.
     * Inverse of letterToIndex().
     *
     * 0→"A", 1→"B", 25→"Z", 26→"AA", 31→"AF", 32→"AG"
     */
    public static String indexToColumnLetter(int index) {
        if (index < 26) {
            return String.valueOf((char) ('A' + index));
        }
        // Two-letter: AA=26, AB=27, ..., AF=31, AG=32, ..., AZ=51, BA=52, ...
        // first char: (index/26) - 1 positions after 'A'   (AA→A, BA→B, etc.)
        // second char: index % 26 positions after 'A'
        int first  = (index / 26) - 1 + 'A';
        int second = (index % 26) + 'A';
        return String.valueOf((char) first) + (char) second;
    }

    /**
     * Returns the 0-based sheet column index for the date at position {@code offset}
     * within the ordered date list, where offset 0 = billing.sheet.first.day.column.
     */
    public static int datePositionToSheetColumnIndex(String sheetFirstDayCol, int offset) {
        return letterToIndex(sheetFirstDayCol) + offset;
    }

    /**
     * Calculates the last sheet column letter given the first column and number of days.
     *
     * calculateLastColumn("B", 31) = "AF"
     * calculateLastColumn("B", 30) = "AE"
     * calculateLastColumn("B", 29) = "AD"
     * calculateLastColumn("B", 28) = "AC"
     */
    public static String calculateLastColumn(String firstCol, int dayCount) {
        int firstIdx = letterToIndex(firstCol);
        int lastIdx  = firstIdx + dayCount - 1;
        return indexToColumnLetter(lastIdx);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validates that the number of CSV dates exactly matches the sheet column range.
     * Stops the program immediately if there is a mismatch — no destructive API calls
     * should have been made before this check.
     */
    public static void validateColumnCounts(List<String> csvDates,
                                            String sheetFirstCol,
                                            String sheetLastCol) {
        int csvCount   = csvDates.size();
        int sheetCount = letterToIndex(sheetLastCol) - letterToIndex(sheetFirstCol) + 1;

        if (csvCount != sheetCount) {
            System.err.println("ERROR: Column count mismatch — cannot proceed safely.");
            System.err.println("  CSV date columns   : " + csvCount
                    + "  (" + csvDates.get(0) + " through " + csvDates.get(csvDates.size() - 1) + ")");
            System.err.println("  Sheet column range : " + sheetCount
                    + "  (" + sheetFirstCol + " through " + sheetLastCol + ")");
            System.err.println("Fix billing.sheet.first.day.column in application.properties.");
            System.exit(1);
        }
    }

    /**
     * Returns the date strings (in original CSV order) that have no "A" row —
     * i.e., the columns that should be deleted.
     *
     * A date is considered unbilled if it is absent from billedByDate or mapped to false.
     */
    public static List<String> getUnbilledDates(Map<String, Boolean> billedByDate,
                                                List<String> orderedDates) {
        List<String> unbilled = new ArrayList<>();
        for (String date : orderedDates) {
            if (!Boolean.TRUE.equals(billedByDate.get(date))) {
                unbilled.add(date);
            }
        }
        return unbilled;
    }
}
