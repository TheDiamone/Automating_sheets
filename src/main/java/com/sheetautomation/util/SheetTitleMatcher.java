package com.sheetautomation.util;

import com.google.api.services.sheets.v4.model.Sheet;

import java.util.List;

/**
 * Pure logic utility for matching a Resident Number to exactly one sheet tab.
 *
 * Match condition: the tab title contains "(" + residentId + ")" — exact,
 * case-sensitive, and leading-zero-preserving (e.g. "(011361)").
 */
public class SheetTitleMatcher {

    /**
     * Finds the single sheet whose title contains {@code (residentId)}.
     *
     * @param allSheets    all sheets from the spreadsheet metadata
     * @param residentId   resident number to search for, e.g. "011361"
     * @param templateName template sheet name to always skip
     * @return the matched Sheet, or null if zero or more than one match found
     */
    public static Sheet findSheet(List<Sheet> allSheets,
                                  String residentId,
                                  String templateName) {
        String token = "(" + residentId + ")";
        List<Sheet> matches = allSheets.stream()
                .filter(s -> !s.getProperties().getTitle().equals(templateName))
                .filter(s -> s.getProperties().getTitle().contains(token))
                .toList();

        if (matches.isEmpty()) {
            System.out.println("  WARN: No tab found for resident (" + residentId + ") — skipping.");
            return null;
        }
        if (matches.size() > 1) {
            System.out.println("  WARN: Multiple tabs matched (" + residentId + ") — skipping.");
            matches.forEach(s -> System.out.println("    " + s.getProperties().getTitle()));
            return null;
        }
        return matches.get(0);
    }
}
