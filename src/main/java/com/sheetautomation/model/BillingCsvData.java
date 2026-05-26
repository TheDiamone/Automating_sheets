package com.sheetautomation.model;

import java.util.List;

/**
 * Result object returned by BillingCsvLoader.load().
 *
 * Holds both the aggregated resident records AND the ordered date list built
 * directly from the CSV header. Keeping orderedDates here avoids the fragile
 * pattern of extracting them from the first record's billedByDate map keys.
 */
public class BillingCsvData {

    private final List<BillingRecord> records;
    private final List<String> orderedDates;

    public BillingCsvData(List<BillingRecord> records, List<String> orderedDates) {
        this.records      = records;
        this.orderedDates = orderedDates;
    }

    /** Aggregated resident records, one per unique Resident Number. */
    public List<BillingRecord> getRecords() {
        return records;
    }

    /**
     * ISO date strings in their original CSV header order.
     * Example: ["2026-05-01", "2026-05-02", ..., "2026-05-31"]
     */
    public List<String> getOrderedDates() {
        return orderedDates;
    }
}
