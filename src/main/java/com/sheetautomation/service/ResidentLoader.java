package com.sheetautomation.service;

import com.opencsv.CSVReader;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import com.sheetautomation.model.Resident;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads residents.csv and returns a list of Resident objects.
 *
 * Uses OpenCSV's RFC4180 parser so quoted names with commas are handled
 * correctly. For example:
 *   "Abellera, Noel",011335  →  name="Abellera, Noel"  id="011335"
 *
 * IDs are kept as plain Strings so leading zeros (e.g. 011335) are preserved.
 */
public class ResidentLoader {

    /**
     * Loads residents from the given CSV file path.
     * The first row is expected to be a header (name,id) and is skipped.
     *
     * @param csvFilePath path to residents.csv, relative to the working directory
     * @return list of Resident objects, never null
     */
    public List<Resident> load(String csvFilePath) {
        List<Resident> residents = new ArrayList<>();

        RFC4180Parser parser = new RFC4180ParserBuilder().build();

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(csvFilePath))
                .withCSVParser(parser)
                .withSkipLines(1)   // skip the "name,id" header row
                .build()) {

            String[] row;
            int lineNumber = 2; // start at 2 because we skipped line 1

            while ((row = reader.readNext()) != null) {
                if (row.length < 2) {
                    System.err.println("WARNING: Skipping malformed line " + lineNumber
                            + " in " + csvFilePath + " (expected 2 columns, got " + row.length + ")");
                    lineNumber++;
                    continue;
                }

                String name = row[0].trim();
                String id   = row[1].trim();

                if (name.isEmpty() || id.isEmpty()) {
                    System.err.println("WARNING: Skipping line " + lineNumber
                            + " — name or id is blank.");
                    lineNumber++;
                    continue;
                }

                residents.add(new Resident(name, id));
                lineNumber++;
            }

        } catch (IOException e) {
            System.err.println("ERROR: Could not read residents file: " + csvFilePath);
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (CsvValidationException e) {
            System.err.println("ERROR: Invalid CSV format in: " + csvFilePath);
            System.err.println(e.getMessage());
            System.exit(1);
        }

        if (residents.isEmpty()) {
            System.err.println("ERROR: No residents found in " + csvFilePath);
            System.err.println("Make sure the file has at least one data row after the header.");
            System.exit(1);
        }

        return residents;
    }
}
