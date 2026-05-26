package com.sheetautomation.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One aggregated resident record built from all billing.csv rows
 * sharing the same Resident Number.
 *
 * billedByDate is an OR-aggregate: once a date is set to true (has "A"),
 * it never reverts to false regardless of other rows for the same resident.
 */
public class BillingRecord {

    private final String residentId;
    private final String residentName;
    private final Map<String, Boolean> billedByDate = new LinkedHashMap<>();
    private int sourceRowCount = 0;

    public BillingRecord(String residentId, String residentName) {
        this.residentId   = residentId;
        this.residentName = residentName;
    }

    /**
     * Records one cell from the billing CSV for this resident.
     * Sets the date to true only if the status matches the configured keep value ("A").
     * Never downgrades a date that is already true.
     */
    public void recordDate(String date, String status, String keepValue) {
        boolean currentlyBilled = billedByDate.getOrDefault(date, false);
        if (!currentlyBilled) {
            billedByDate.put(date, keepValue.equals(status));
        }
        // if already true, leave it — OR semantics across rows
    }

    public void incrementSourceRowCount() {
        sourceRowCount++;
    }

    public String getResidentId() {
        return residentId;
    }

    public String getResidentName() {
        return residentName;
    }

    public Map<String, Boolean> getBilledByDate() {
        return billedByDate;
    }

    public int getSourceRowCount() {
        return sourceRowCount;
    }
}
