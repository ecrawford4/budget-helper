# budget-helper

A simple Java desktop budgeting app built with Swing and Maven.

## Features

- Budget entry for income and planned expenses with predefined or custom categories
- Edit and delete support for budgeted income and expense entries
- Date-stamped entries for every income and expense item
- Summary view that renders an invoice-style budget report for a selected date range
- PDF export for the summary report without external dependencies
- Reporting view for actual expenses versus budgeted expenses by category, including edit and delete support
- Automatic local profile save and load for budget data
- Multiple named local profiles with budget metadata such as budget name, your name, company name, address, billing address, email, and phone
- A dedicated Profile tab for budget metadata, profile management, and preferences
- CSV export for the Summary and Reporting views
- In-app update check that downloads and launches the latest installer from GitHub Releases

## Run

From the workspace root:

```powershell
.\mvnw.cmd -q -DskipTests package
java -jar target/budget-helper-1.0.0.jar
```

## Installer Build

Create a Windows installer with bundled runtime (no separate Java install required):

```powershell
.\mvnw.cmd -q -DskipTests package
jpackage --type exe --name BudgetHelper --input target --main-jar budget-helper-1.0.0.jar --main-class budgethelper.BudgetHelperApp --app-version 1.0.0 --vendor ecrawford4 --win-dir-chooser --win-shortcut --dest target\installer
```

Installer output is written under target\installer.

## Release Automation

- Tag pushes matching v* trigger the release workflow.
- The workflow uploads both the JAR and Windows installer executable to the GitHub Release.

## Notes

- Add at least one income entry before adding expenses.
- Profiles are saved locally under ~/.budget-helper/profiles/ and the last used profile is tracked automatically.
- App preferences, including the initial tab on launch, are stored locally under ~/.budget-helper/preferences.properties.
