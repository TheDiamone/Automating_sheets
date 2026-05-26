# Google Sheet Automation

A Java CLI tool that automates Google Sheets tasks using the official Sheets API v4.
Replaces the original Python/gspread scripts with a cleaner, safer, and more portable implementation.

---

## Prerequisites

- Java 17 or later
- Maven 3.6 or later
- A Google Cloud service account with a JSON key file
- The target spreadsheet shared with the service account (see below)

---

## Setup

### 1. Place your service account key

Copy your service account JSON file into the `credentials/` folder and name it `service-account.json`:

```
credentials/service-account.json
```

> **This file is gitignored and will never be committed.**
> The `client_email` field inside the JSON is the address you must share your spreadsheet with.

### 2. Share the spreadsheet

Open your Google Spreadsheet → Share → paste the `client_email` value from your JSON file → give it **Editor** access.

Without this step, the program will receive a 403 Forbidden error.

### 3. Edit config/application.properties

Fill in your spreadsheet ID and review all settings:

```properties
spreadsheet.id=YOUR_SPREADSHEET_ID_HERE
template.sheet.name=Template
```

Find the spreadsheet ID in the URL:
```
https://docs.google.com/spreadsheets/d/SPREADSHEET_ID_IS_HERE/edit
```

### 4. Edit config/residents.csv

Add your residents using **quoted names** (because names contain commas):

```csv
name,id
"Smith, John",012345
"Doe, Jane",067890
```

---

## Build

```bash
mvn clean package
```

This produces: `target/google-sheet-automation.jar`

---

## Commands

```bash
# Test that authentication and config are working
java -jar target/google-sheet-automation.jar test-connection

# Create tabs for all residents in residents.csv
java -jar target/google-sheet-automation.jar create-tabs
java -jar target/google-sheet-automation.jar create-tabs --dry-run

# Hide empty rows on all resident sheets
java -jar target/google-sheet-automation.jar hide-empty-rows
java -jar target/google-sheet-automation.jar hide-empty-rows --dry-run

# Auto-detect: create tabs if none exist, otherwise hide empty rows
java -jar target/google-sheet-automation.jar run
java -jar target/google-sheet-automation.jar run --dry-run
```

**Always run with `--dry-run` first** to preview changes before applying them.

---

## Security

- `credentials/` is gitignored — your service account key will not be committed.
- The spreadsheet ID is in `config/application.properties` — safe to commit (it is not a secret).
- Never paste your service account JSON content into source code.
