package budgethelper;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;

public final class BudgetSummaryPanel extends JPanel {
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BudgetDataStore dataStore;
    private final JSpinner startDateSpinner;
    private final JSpinner endDateSpinner;
    private final JTextArea reportArea;

    public BudgetSummaryPanel(BudgetDataStore dataStore) {
        super(new BorderLayout(12, 12));
        this.dataStore = dataStore;
        startDateSpinner = UiSupport.createDateSpinner();
        endDateSpinner = UiSupport.createDateSpinner();
        reportArea = new JTextArea();
        reportArea.setEditable(false);
        reportArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(buildControls(), BorderLayout.NORTH);
        add(new JScrollPane(reportArea), BorderLayout.CENTER);
        dataStore.addChangeListener(this::refreshReport);
        refreshReport();
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Start"));
        controls.add(startDateSpinner);
        controls.add(new JLabel("End"));
        controls.add(endDateSpinner);
        JButton refreshButton = new JButton("Refresh Summary");
        refreshButton.addActionListener(event -> refreshReport());
        controls.add(refreshButton);
        JButton exportPdfButton = new JButton("Export PDF");
        exportPdfButton.addActionListener(event -> exportPdf());
        controls.add(exportPdfButton);
        JButton exportCsvButton = new JButton("Export CSV");
        exportCsvButton.addActionListener(event -> exportCsv());
        controls.add(exportCsvButton);
        return controls;
    }

    private void refreshReport() {
        try {
            reportArea.setText(String.join(System.lineSeparator(), buildReportLines()));
            reportArea.setCaretPosition(0);
        } catch (RuntimeException exception) {
            reportArea.setText(exception.getMessage());
        }
    }

    private void exportPdf() {
        try {
            LocalDate startDate = UiSupport.toLocalDate(startDateSpinner);
            LocalDate endDate = UiSupport.toLocalDate(endDateSpinner);
            validateRange(startDate, endDate);
            JFileChooser fileChooser = new JFileChooser();
            String budgetName = dataStore.getProfileInfo().displayBudgetName().replaceAll("[^A-Za-z0-9-]+", "-");
            fileChooser.setSelectedFile(new File(budgetName + "-summary-" + endDate.format(FILE_DATE_FORMAT) + ".pdf"));
            if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            PdfReportExporter.export(buildReportLines(), fileChooser.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(this, "PDF exported successfully.", "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Export Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportCsv() {
        try {
            LocalDate startDate = UiSupport.toLocalDate(startDateSpinner);
            LocalDate endDate = UiSupport.toLocalDate(endDateSpinner);
            validateRange(startDate, endDate);
            JFileChooser fileChooser = new JFileChooser();
            String budgetName = dataStore.getProfileInfo().displayBudgetName().replaceAll("[^A-Za-z0-9-]+", "-");
            fileChooser.setSelectedFile(new File(budgetName + "-summary-" + endDate.format(FILE_DATE_FORMAT) + ".csv"));
            if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            CsvExportSupport.exportRows(buildSummaryCsvRows(), fileChooser.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(this, "CSV exported successfully.", "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Export Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<String> buildReportLines() {
        if (!dataStore.hasIncomeEntries()) {
            return List.of("Budget Invoice Summary", "", "Add at least one income entry before generating a summary.");
        }
        LocalDate startDate = UiSupport.toLocalDate(startDateSpinner);
        LocalDate endDate = UiSupport.toLocalDate(endDateSpinner);
        validateRange(startDate, endDate);
        List<BudgetEntry> incomeEntries = dataStore.getEntries(EntryType.INCOME, startDate, endDate);
        List<BudgetEntry> expenseEntries = dataStore.getEntries(EntryType.BUDGETED_EXPENSE, startDate, endDate);
        BigDecimal totalIncome = BudgetDataStore.sumEntries(incomeEntries);
        BigDecimal totalExpenses = BudgetDataStore.sumEntries(expenseEntries);
        BigDecimal balance = totalIncome.subtract(totalExpenses);
        BudgetProfileInfo profileInfo = dataStore.getProfileInfo();
        List<String> lines = new ArrayList<>();
        lines.add("Budget Invoice Summary");
        lines.add("Budget Name: " + profileInfo.displayBudgetName());
        if (!profileInfo.yourName().isBlank())
            lines.add("Prepared By: " + profileInfo.yourName());
        if (!profileInfo.companyName().isBlank())
            lines.add("Company: " + profileInfo.companyName());
        if (!profileInfo.email().isBlank())
            lines.add("Email: " + profileInfo.email());
        if (!profileInfo.phone().isBlank())
            lines.add("Phone: " + profileInfo.phone());
        if (!profileInfo.addressLines().isEmpty()) {
            lines.add("Address:");
            for (String addressLine : profileInfo.addressLines())
                lines.add("  " + addressLine);
        }
        if (!profileInfo.billingAddressLines().isEmpty()) {
            lines.add("Billing Address:");
            for (String addressLine : profileInfo.billingAddressLines())
                lines.add("  " + addressLine);
        }
        lines.add("Date Range: " + startDate + " to " + endDate);
        lines.add(repeat('-', 78));
        lines.add("");
        lines.add("Income Items");
        lines.addAll(formatEntries(incomeEntries, false));
        lines.add("Total Income: " + UiSupport.formatCurrency(totalIncome));
        lines.add("");
        lines.add("Expense Items");
        lines.addAll(formatEntries(expenseEntries, true));
        lines.add("Total Expenses: " + UiSupport.formatCurrency(totalExpenses));
        lines.add("");
        lines.add(balance.signum() >= 0 ? "Surplus: " + UiSupport.formatCurrency(balance)
                : "Deficit: " + UiSupport.formatCurrency(balance.abs()));
        if (incomeEntries.isEmpty() && expenseEntries.isEmpty()) {
            lines.add("");
            lines.add("No budget entries exist in this date range.");
        }
        return lines;
    }

    private List<List<String>> buildSummaryCsvRows() {
        LocalDate startDate = UiSupport.toLocalDate(startDateSpinner);
        LocalDate endDate = UiSupport.toLocalDate(endDateSpinner);
        validateRange(startDate, endDate);
        List<BudgetEntry> incomeEntries = dataStore.getEntries(EntryType.INCOME, startDate, endDate);
        List<BudgetEntry> expenseEntries = dataStore.getEntries(EntryType.BUDGETED_EXPENSE, startDate, endDate);
        BigDecimal totalIncome = BudgetDataStore.sumEntries(incomeEntries);
        BigDecimal totalExpenses = BudgetDataStore.sumEntries(expenseEntries);
        BigDecimal balance = totalIncome.subtract(totalExpenses);
        BudgetProfileInfo profileInfo = dataStore.getProfileInfo();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Budget Name", profileInfo.displayBudgetName()));
        rows.add(List.of("Prepared By", profileInfo.yourName()));
        rows.add(List.of("Company", profileInfo.companyName()));
        rows.add(List.of("Email", profileInfo.email()));
        rows.add(List.of("Phone", profileInfo.phone()));
        rows.add(List.of("Address", String.join(" | ", profileInfo.addressLines())));
        rows.add(List.of("Billing Address", String.join(" | ", profileInfo.billingAddressLines())));
        rows.add(List.of("Start Date", startDate.toString(), "End Date", endDate.toString()));
        rows.add(List.of());
        rows.add(List.of("Income Date", "Category", "Description", "Amount"));
        for (BudgetEntry entry : incomeEntries) {
            rows.add(List.of(entry.getEntryDate().toString(), entry.getCategory(), entry.getDescription(),
                    entry.getAmount().toPlainString()));
        }
        rows.add(List.of("Total Income", "", "", totalIncome.toPlainString()));
        rows.add(List.of());
        rows.add(List.of("Expense Date", "Category", "Description", "Amount"));
        for (BudgetEntry entry : expenseEntries) {
            rows.add(List.of(entry.getEntryDate().toString(), entry.getCategory(), entry.getDescription(),
                    entry.getAmount().toPlainString()));
        }
        rows.add(List.of("Total Expenses", "", "", totalExpenses.toPlainString()));
        rows.add(List.of(balance.signum() >= 0 ? "Surplus" : "Deficit", "", "", balance.abs().toPlainString()));
        return rows;
    }

    private List<String> formatEntries(List<BudgetEntry> entries, boolean expense) {
        if (entries.isEmpty()) {
            return List.of("  None");
        }
        List<String> lines = new ArrayList<>();
        for (BudgetEntry entry : entries) {
            String amount = UiSupport.formatCurrency(entry.getAmount());
            String baseLine = String.format("  %-10s  %-18s  %10s  %s", entry.getEntryDate(),
                    truncate(entry.getCategory(), 18), expense ? "-" + amount : amount,
                    entry.getDescription().isBlank() ? "-" : entry.getDescription());
            lines.addAll(UiSupport.wrapText(baseLine, 95));
        }
        return lines;
    }

    private static void validateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after the start date.");
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private static String repeat(char character, int count) {
        return String.valueOf(character).repeat(Math.max(0, count));
    }
}
