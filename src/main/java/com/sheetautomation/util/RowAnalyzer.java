package com.sheetautomation.util;

import java.util.List;

/**
 * Decides whether a spreadsheet row contains useful data worth keeping visible.
 *
 * Two rules are available:
 *
 * isUsefulRow  — legacy broad check: any digit anywhere in the row.
 *                Used by the test-analyzer self-test.
 *
 * isRowWithCharge — targeted check for the billing workflow: any day cell
 *                   contains a whole integer from 1 to 10 (inclusive).
 *                   Used by hide-empty-rows.
 */
public class RowAnalyzer {

    /**
     * Returns true if this row should stay visible (legacy broad rule).
     * A row is useful if ANY cell contains at least one digit character.
     *
     * @param cells list of cell values from one spreadsheet row
     */
    public static boolean isUsefulRow(List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            if (cell != null && !cell.isBlank() && cell.matches(".*\\d.*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if any cell in dayCells is a whole integer from 1 to 10 inclusive.
     *
     * Only exact integers qualify:
     *   "4"    → true     "10"   → true     "1"    → true
     *   "11"   → false    "0"    → false    "31-A" → false
     *   "10.5" → false    "A"    → false    ""     → false
     *
     * Call this with cells from the day columns only (e.g. B through AF),
     * not with the full row including resident name or room number.
     *
     * @param dayCells cells from the billing day columns for one row
     */
    public static boolean isRowWithCharge(List<String> dayCells) {
        if (dayCells == null) return false;
        for (String cell : dayCells) {
            if (cell == null || cell.isBlank()) continue;
            try {
                int value = Integer.parseInt(cell.trim());
                if (value >= 1 && value <= 10) return true;
            } catch (NumberFormatException e) {
                // not a plain integer — not a charge value
            }
        }
        return false;
    }

    /**
     * Quick self-test — prints pass/fail for known values.
     * Run from App.java with the "test-analyzer" command.
     */
    public static void runSelfTest() {
        record Case(String label, boolean expected, boolean got) {}

        System.out.println("RowAnalyzer self-test");
        System.out.println();

        // ── isUsefulRow tests ─────────────────────────────────────────────────
        System.out.println("isUsefulRow:");
        record UsefulCase(String input, boolean expected) {}
        List<UsefulCase> usefulCases = List.of(
            new UsefulCase("31",        true),
            new UsefulCase("31-A",      true),
            new UsefulCase("12.5",      true),
            new UsefulCase("$50",       true),
            new UsefulCase("011335-A",  true),
            new UsefulCase("308-B",     true),
            new UsefulCase("Room 4",    true),
            new UsefulCase("",          false),
            new UsefulCase("   ",       false),
            new UsefulCase("N/A",       false),
            new UsefulCase("Name",      false),
            new UsefulCase(null,        false)
        );

        int usefulPassed = 0;
        for (UsefulCase c : usefulCases) {
            List<String> row = List.of(c.input() != null ? c.input() : "");
            boolean result = isUsefulRow(row);
            boolean ok     = result == c.expected();
            System.out.printf("  [%s]  %-14s  expected=%-5s  got=%s%n",
                    ok ? "PASS" : "FAIL", "\"" + c.input() + "\"", c.expected(), result);
            if (ok) usefulPassed++;
        }

        // ── isRowWithCharge tests ─────────────────────────────────────────────
        System.out.println();
        System.out.println("isRowWithCharge (single-cell rows):");
        record ChargeCase(String input, boolean expected) {}
        List<ChargeCase> chargeCases = List.of(
            new ChargeCase("1",    true),
            new ChargeCase("4",    true),
            new ChargeCase("10",   true),
            new ChargeCase("11",   false),
            new ChargeCase("0",    false),
            new ChargeCase("31",   false),
            new ChargeCase("31-A", false),
            new ChargeCase("10.5", false),
            new ChargeCase("A",    false),
            new ChargeCase("D",    false),
            new ChargeCase("HN",   false),
            new ChargeCase("",     false),
            new ChargeCase(null,   false)
        );

        int chargePassed = 0;
        for (ChargeCase c : chargeCases) {
            List<String> row = List.of(c.input() != null ? c.input() : "");
            boolean result = isRowWithCharge(row);
            boolean ok     = result == c.expected();
            System.out.printf("  [%s]  %-14s  expected=%-5s  got=%s%n",
                    ok ? "PASS" : "FAIL", "\"" + c.input() + "\"", c.expected(), result);
            if (ok) chargePassed++;
        }

        // Multi-cell row: charge found among mixed cells
        System.out.println();
        System.out.println("isRowWithCharge (multi-cell rows):");
        boolean mixedResult = isRowWithCharge(List.of("", "7", ""));
        System.out.printf("  [%s]  [\"\", \"7\", \"\"]  expected=true   got=%s%n",
                mixedResult ? "PASS" : "FAIL", mixedResult);
        boolean allBlank = isRowWithCharge(List.of("", "", ""));
        System.out.printf("  [%s]  [\"\", \"\", \"\"]  expected=false  got=%s%n",
                !allBlank ? "PASS" : "FAIL", allBlank);
        int multiPassed = (mixedResult ? 1 : 0) + (!allBlank ? 1 : 0);

        // ── Summary ───────────────────────────────────────────────────────────
        int total  = usefulCases.size() + chargeCases.size() + 2;
        int passed = usefulPassed + chargePassed + multiPassed;
        System.out.println();
        System.out.println("Result: " + passed + " / " + total + " passed.");
    }
}
