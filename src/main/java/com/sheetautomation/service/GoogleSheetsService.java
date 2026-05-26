package com.sheetautomation.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

/**
 * Handles service account authentication and provides a configured Sheets client.
 * All other services call getSheets() to make API requests.
 */
public class GoogleSheetsService {

    private static final String APPLICATION_NAME = "Google Sheet Automation";

    private final Sheets sheetsClient;

    /**
     * Authenticates using the service account JSON file at the given path.
     * The path is resolved relative to the current working directory.
     *
     * @param credentialsPath path to service-account.json, e.g. "credentials/service-account.json"
     */
    public GoogleSheetsService(String credentialsPath) {
        try {
            // Load the service account credentials from the JSON key file
            GoogleCredentials credentials;
            try (FileInputStream credStream = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials
                        .fromStream(credStream)
                        .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
            } catch (IOException e) {
                System.err.println("ERROR: Could not read credentials file: " + credentialsPath);
                System.err.println("Make sure the file exists and is a valid service account JSON key.");
                System.err.println("Also confirm the spreadsheet is shared with the client_email in that file.");
                System.exit(1);
                throw new RuntimeException(e); // unreachable, satisfies the compiler
            }

            // Build the Sheets API client
            sheetsClient = new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

        } catch (GeneralSecurityException | IOException e) {
            System.err.println("ERROR: Failed to initialise the Google Sheets client.");
            System.err.println(e.getMessage());
            System.exit(1);
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the configured Sheets client for direct API calls.
     * Use the higher-level helpers below where possible.
     */
    public Sheets getSheets() {
        return sheetsClient;
    }

    /**
     * Fetches the full spreadsheet metadata (sheet titles, IDs, etc.).
     * Does NOT fetch cell data — use getSheetValues() for that.
     *
     * @param spreadsheetId the ID from the spreadsheet URL
     */
    public Spreadsheet getSpreadsheet(String spreadsheetId) {
        try {
            return sheetsClient.spreadsheets()
                    .get(spreadsheetId)
                    .execute();
        } catch (IOException e) {
            System.err.println("ERROR: Could not open spreadsheet: " + spreadsheetId);
            System.err.println(e.getMessage());
            System.err.println("Make sure the spreadsheet ID is correct and it is shared with the service account.");
            System.exit(1);
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads a range of cells from a sheet and returns the values as a 2D list.
     * Each inner list is one row; each String is one cell value.
     *
     * @param spreadsheetId the spreadsheet to read from
     * @param range         A1 notation range, e.g. "Sheet1!A1:Z160"
     */
    public List<List<Object>> getSheetValues(String spreadsheetId, String range) {
        try {
            var response = sheetsClient.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            List<List<Object>> values = response.getValues();
            // The API returns null when the range is completely empty
            return (values != null) ? values : Collections.emptyList();
        } catch (IOException e) {
            System.err.println("ERROR: Could not read range " + range + " from spreadsheet " + spreadsheetId);
            System.err.println(e.getMessage());
            System.exit(1);
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends a batchUpdate request to the spreadsheet.
     * Used for structural changes: duplicate sheet, hide rows, rename sheet, etc.
     *
     * @param spreadsheetId the spreadsheet to update
     * @param request       the fully-built BatchUpdateSpreadsheetRequest
     */
    public BatchUpdateSpreadsheetResponse batchUpdate(String spreadsheetId,
                                                       BatchUpdateSpreadsheetRequest request) {
        try {
            return sheetsClient.spreadsheets()
                    .batchUpdate(spreadsheetId, request)
                    .execute();
        } catch (IOException e) {
            System.err.println("ERROR: batchUpdate failed on spreadsheet " + spreadsheetId);
            System.err.println(e.getMessage());
            System.exit(1);
            throw new RuntimeException(e);
        }
    }
}
