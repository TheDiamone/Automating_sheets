package com.sheetautomation.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Loads and validates settings from config/application.properties.
 * All other classes read configuration through this class.
 *
 * The config file is resolved relative to the current working directory,
 * so you must run the program from the project root (where config/ lives).
 */
public class AppConfig {

    private static final String CONFIG_FILE = "config/application.properties";

    private final Properties props;

    public AppConfig() {
        props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("ERROR: Could not read config file: " + CONFIG_FILE);
            System.err.println("Make sure you run the program from the project root folder.");
            System.exit(1);
        }
        validate();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getSpreadsheetId() {
        return require("spreadsheet.id");
    }

    public String getTemplateSheetName() {
        return require("template.sheet.name");
    }

    public String getCredentialsPath() {
        return require("credentials.path");
    }

    public String getResidentsFile() {
        return require("residents.file");
    }

    public int getHideStartRow() {
        return Integer.parseInt(require("hide.start.row"));
    }

    public int getHideEndRow() {
        return Integer.parseInt(require("hide.end.row"));
    }

    public long getSleepBetweenSheets() {
        return Long.parseLong(require("sleep.between.sheets.ms"));
    }

    // ── Billing / delete-unbilled-columns getters ─────────────────────────────

    public String getBillingFile() {
        return require("billing.file");
    }

    public String getBillingNameColumn() {
        return require("billing.name.column");
    }

    public String getBillingIdColumn() {
        return require("billing.id.column");
    }

    /** Sheet column letter where the first billing date lives, e.g. "B" */
    public String getBillingSheetFirstDayColumn() {
        return require("billing.sheet.first.day.column");
    }

    /** The status value that means billed, e.g. "A" */
    public String getBillingStatusKeepValue() {
        return require("billing.status.keep.value");
    }

    public long getBillingSleepBetweenSheets() {
        return Long.parseLong(require("billing.sleep.between.sheets.ms"));
    }

    /**
     * Returns true only when billing.allow.delete.columns=true.
     * Missing or any other value is treated as false (safe default).
     */
    public boolean isBillingAllowDeleteColumns() {
        String value = props.getProperty("billing.allow.delete.columns", "false");
        return "true".equalsIgnoreCase(value.trim());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Returns the value for a key, or exits with a clear error if missing. */
    private String require(String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            System.err.println("ERROR: Missing required config key: " + key);
            System.err.println("Edit config/application.properties and set a value for: " + key);
            System.exit(1);
        }
        return value.trim();
    }

    /** Checks for placeholder values that the user forgot to replace. */
    private void validate() {
        String spreadsheetId = props.getProperty("spreadsheet.id", "");
        if (spreadsheetId.equals("YOUR_SPREADSHEET_ID_HERE")) {
            System.err.println("ERROR: spreadsheet.id is still set to the placeholder value.");
            System.err.println("Edit config/application.properties and replace YOUR_SPREADSHEET_ID_HERE");
            System.err.println("with your actual Google Spreadsheet ID.");
            System.exit(1);
        }
    }
}
