package com.sheetautomation.model;

/**
 * Represents one resident loaded from residents.csv.
 * Holds the person's name and ID, and builds the Google Sheet tab name.
 */
public class Resident {

    private final String name;
    private final String id;

    public Resident(String name, String id) {
        this.name = name.trim();
        this.id   = id.trim();
    }

    public String getName() { return name; }
    public String getId()   { return id; }

    /** Returns the tab name format: "Abellera, Noel (011335)" */
    public String getTabName() {
        return name + " (" + id + ")";
    }

    @Override
    public String toString() {
        return getTabName();
    }
}
