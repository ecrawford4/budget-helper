package budgethelper;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

public final class BudgetReportingPanel extends JPanel {
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final BudgetDataStore dataStore;
    private final JComboBox<String> categoryBox;
    private final JTextField descriptionField;
    private final JTextField amountField;
    private final JSpinner actualDateSpinner;
    private final JSpinner startDateSpinner;
    private final JSpinner endDateSpinner;
    private final JLabel summaryLabel;
    private final JButton addButton;
    private final JButton updateButton;
    private final JButton deleteButton;
    private final JButton clearButton;
    private final ActualExpenseTableModel actualExpenseTableModel;
    private final JTable actualExpenseTable;
    private final VarianceTableModel tableModel;
    private BudgetEntry selectedActualEntry;

    public BudgetReportingPanel(BudgetDataStore dataStore) {
        super(new BorderLayout(12, 12));
        this.dataStore = dataStore;
        categoryBox = new JComboBox<>();
        categoryBox.setEditable(true);
        descriptionField = new JTextField(16);
        amountField = new JTextField(10);
        actualDateSpinner = UiSupport.createDateSpinner();
        startDateSpinner = UiSupport.createDateSpinner();
        endDateSpinner = UiSupport.createDateSpinner();
        summaryLabel = new JLabel();
        addButton = new JButton("Add Actual Expense");
        updateButton = new JButton("Update Selected");
        deleteButton = new JButton("Delete Selected");
        clearButton = new JButton("Clear Selection");
        actualExpenseTableModel = new ActualExpenseTableModel();
        actualExpenseTable = new JTable(actualExpenseTableModel);
        tableModel = new VarianceTableModel();
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(summaryLabel, BorderLayout.SOUTH);
        dataStore.addChangeListener(this::refreshView);
        resetSelection();
        refreshView();
    }

    private JPanel buildTopPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 12));
        wrapper.add(buildActualExpenseForm(), BorderLayout.NORTH);
        wrapper.add(buildFilterPanel(), BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildActualExpenseForm() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Record Actual Expenses"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        formPanel.add(new JLabel("Category"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        formPanel.add(categoryBox, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        formPanel.add(new JLabel("Description"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        formPanel.add(descriptionField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 0;
        formPanel.add(new JLabel("Amount"), constraints);
        constraints.gridx = 1;
        formPanel.add(amountField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 3;
        formPanel.add(new JLabel("Date"), constraints);
        constraints.gridx = 1;
        formPanel.add(actualDateSpinner, constraints);
        addButton.addActionListener(event -> addActualExpense());
        updateButton.addActionListener(event -> updateActualExpense());
        deleteButton.addActionListener(event -> deleteActualExpense());
        clearButton.addActionListener(event -> resetSelection());
        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        formPanel.add(addButton, constraints);
        JPanel buttonRow = new JPanel(new java.awt.GridLayout(1, 3, 8, 0));
        buttonRow.add(updateButton);
        buttonRow.add(deleteButton);
        buttonRow.add(clearButton);
        constraints.gridy = 5;
        formPanel.add(buttonRow, constraints);
        return formPanel;
    }

    private JPanel buildFilterPanel() {
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.add(new JLabel("Start"));
        filterPanel.add(startDateSpinner);
        filterPanel.add(new JLabel("End"));
        filterPanel.add(endDateSpinner);
        JButton refreshButton = new JButton("Refresh Reporting");
        refreshButton.addActionListener(event -> refreshView());
        filterPanel.add(refreshButton);
        JButton exportCsvButton = new JButton("Export CSV");
        exportCsvButton.addActionListener(event -> exportCsv());
        filterPanel.add(exportCsvButton);
        return filterPanel;
    }

    private JSplitPane buildCenterPanel() {
        actualExpenseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        actualExpenseTable.setFillsViewportHeight(true);
        actualExpenseTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                populateSelectionFromTable();
            }
        });
        JPanel actualEntriesPanel = new JPanel(new BorderLayout());
        actualEntriesPanel.setBorder(BorderFactory.createTitledBorder("Actual Expense Entries"));
        actualEntriesPanel.add(new JScrollPane(actualExpenseTable), BorderLayout.CENTER);
        JTable varianceTable = new JTable(tableModel);
        varianceTable.setFillsViewportHeight(true);
        JPanel variancePanel = new JPanel(new BorderLayout());
        variancePanel.setBorder(BorderFactory.createTitledBorder("Category Variance"));
        variancePanel.add(new JScrollPane(varianceTable), BorderLayout.CENTER);
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, actualEntriesPanel, variancePanel);
        splitPane.setResizeWeight(0.55);
        return splitPane;
    }

    private void addActualExpense() {
        if (!dataStore.hasIncomeEntries()) {
            JOptionPane.showMessageDialog(this, "Create at least one income entry before recording actual expenses.",
                    "Income Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Object selectedCategory = categoryBox.getEditor().getItem();
            String category = selectedCategory == null ? "" : selectedCategory.toString();
            BigDecimal amount = UiSupport.parseAmount(amountField.getText());
            LocalDate entryDate = UiSupport.toLocalDate(actualDateSpinner);
            dataStore.addEntry(
                    new BudgetEntry(EntryType.ACTUAL_EXPENSE, category, descriptionField.getText(), amount, entryDate));
            clearFormFields();
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Invalid Entry", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateActualExpense() {
        if (selectedActualEntry == null) {
            JOptionPane.showMessageDialog(this, "Select an actual expense entry to edit first.", "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            dataStore.updateEntry(selectedActualEntry, buildActualExpenseFromForm());
            resetSelection();
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Invalid Entry", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteActualExpense() {
        if (selectedActualEntry == null) {
            JOptionPane.showMessageDialog(this, "Select an actual expense entry to delete first.", "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this, "Delete the selected actual expense entry?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            dataStore.deleteEntry(selectedActualEntry);
            resetSelection();
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Delete Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BudgetEntry buildActualExpenseFromForm() {
        Object selectedCategory = categoryBox.getEditor().getItem();
        String category = selectedCategory == null ? "" : selectedCategory.toString();
        BigDecimal amount = UiSupport.parseAmount(amountField.getText());
        LocalDate entryDate = UiSupport.toLocalDate(actualDateSpinner);
        return new BudgetEntry(EntryType.ACTUAL_EXPENSE, category, descriptionField.getText(), amount, entryDate);
    }

    private void populateSelectionFromTable() {
        int rowIndex = actualExpenseTable.getSelectedRow();
        if (rowIndex < 0) {
            if (selectedActualEntry != null) {
                resetSelection();
            }
            return;
        }
        selectedActualEntry = actualExpenseTableModel.getEntryAt(rowIndex);
        categoryBox.setSelectedItem(selectedActualEntry.getCategory());
        descriptionField.setText(selectedActualEntry.getDescription());
        amountField.setText(selectedActualEntry.getAmount().toPlainString());
        UiSupport.setLocalDate(actualDateSpinner, selectedActualEntry.getEntryDate());
        addButton.setEnabled(false);
        updateButton.setEnabled(true);
        deleteButton.setEnabled(true);
    }

    private void resetSelection() {
        selectedActualEntry = null;
        actualExpenseTable.clearSelection();
        clearFormFields();
        addButton.setEnabled(true);
        updateButton.setEnabled(false);
        deleteButton.setEnabled(false);
    }

    private void clearFormFields() {
        descriptionField.setText("");
        amountField.setText("");
        if (categoryBox.getItemCount() > 0 && categoryBox.getSelectedItem() == null) {
            categoryBox.setSelectedIndex(0);
        }
        UiSupport.setLocalDate(actualDateSpinner, LocalDate.now());
    }

    private void refreshView() {
        Object selectedCategory = categoryBox.isEditable() ? categoryBox.getEditor().getItem()
                : categoryBox.getSelectedItem();
        categoryBox.setModel(new DefaultComboBoxModel<>(dataStore.getExpenseCategories().toArray(String[]::new)));
        categoryBox.setSelectedItem(selectedCategory);
        LocalDate startDate = UiSupport.toLocalDate(startDateSpinner);
        LocalDate endDate = UiSupport.toLocalDate(endDateSpinner);
        if (endDate.isBefore(startDate)) {
            summaryLabel.setText("End date must be on or after the start date.");
            actualExpenseTableModel.setEntries(List.of());
            tableModel.setRows(List.of());
            return;
        }
        List<BudgetEntry> actualEntries = dataStore.getEntries(EntryType.ACTUAL_EXPENSE, startDate, endDate);
        actualExpenseTableModel.setEntries(actualEntries);
        Map<String, BigDecimal> budgeted = dataStore.totalsByCategory(EntryType.BUDGETED_EXPENSE, startDate, endDate);
        Map<String, BigDecimal> actual = dataStore.totalsByCategory(EntryType.ACTUAL_EXPENSE, startDate, endDate);
        Set<String> categories = new TreeSet<>();
        categories.addAll(budgeted.keySet());
        categories.addAll(actual.keySet());
        List<VarianceRow> rows = new ArrayList<>();
        BigDecimal totalBudgeted = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        for (String category : categories) {
            BigDecimal budgetedAmount = budgeted.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal actualAmount = actual.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal variance = budgetedAmount.subtract(actualAmount);
            rows.add(new VarianceRow(category, budgetedAmount, actualAmount, variance));
            totalBudgeted = totalBudgeted.add(budgetedAmount);
            totalActual = totalActual.add(actualAmount);
        }
        tableModel.setRows(rows);
        if (selectedActualEntry != null) {
            BudgetEntry refreshedSelection = actualExpenseTableModel.findMatchingEntry(selectedActualEntry);
            if (refreshedSelection == null) {
                resetSelection();
            } else {
                selectedActualEntry = refreshedSelection;
                categoryBox.setSelectedItem(selectedActualEntry.getCategory());
                descriptionField.setText(selectedActualEntry.getDescription());
                amountField.setText(selectedActualEntry.getAmount().toPlainString());
                UiSupport.setLocalDate(actualDateSpinner, selectedActualEntry.getEntryDate());
                addButton.setEnabled(false);
                updateButton.setEnabled(true);
                deleteButton.setEnabled(true);
            }
        }
        BigDecimal totalVariance = totalBudgeted.subtract(totalActual);
        summaryLabel.setText("Budgeted: " + UiSupport.formatCurrency(totalBudgeted) + "    Actual: "
                + UiSupport.formatCurrency(totalActual) + "    "
                + (totalVariance.signum() >= 0 ? "Surplus: " : "Deficit: ")
                + UiSupport.formatCurrency(totalVariance.abs()));
    }

    private void exportCsv() {
        try {
            LocalDate startDate = UiSupport.toLocalDate(startDateSpinner);
            LocalDate endDate = UiSupport.toLocalDate(endDateSpinner);
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("End date must be on or after the start date.");
            }
            JFileChooser fileChooser = new JFileChooser();
            String budgetName = dataStore.getProfileInfo().displayBudgetName().replaceAll("[^A-Za-z0-9-]+", "-");
            fileChooser
                    .setSelectedFile(new File(budgetName + "-reporting-" + endDate.format(FILE_DATE_FORMAT) + ".csv"));
            if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            CsvExportSupport.exportRows(buildReportingCsvRows(), fileChooser.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(this, "CSV exported successfully.", "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Export Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<List<String>> buildReportingCsvRows() {
        LocalDate startDate = UiSupport.toLocalDate(startDateSpinner);
        LocalDate endDate = UiSupport.toLocalDate(endDateSpinner);
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after the start date.");
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Budget Name", dataStore.getProfileInfo().displayBudgetName()));
        rows.add(List.of("Start Date", startDate.toString(), "End Date", endDate.toString()));
        rows.add(List.of());
        rows.add(List.of("Actual Expense Date", "Category", "Description", "Amount"));
        for (BudgetEntry entry : dataStore.getEntries(EntryType.ACTUAL_EXPENSE, startDate, endDate)) {
            rows.add(List.of(entry.getEntryDate().toString(), entry.getCategory(), entry.getDescription(),
                    entry.getAmount().toPlainString()));
        }
        rows.add(List.of());
        rows.add(List.of("Category", "Budgeted", "Actual", "Variance"));
        Map<String, BigDecimal> budgeted = dataStore.totalsByCategory(EntryType.BUDGETED_EXPENSE, startDate, endDate);
        Map<String, BigDecimal> actual = dataStore.totalsByCategory(EntryType.ACTUAL_EXPENSE, startDate, endDate);
        Set<String> categories = new TreeSet<>();
        categories.addAll(budgeted.keySet());
        categories.addAll(actual.keySet());
        for (String category : categories) {
            BigDecimal budgetedAmount = budgeted.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal actualAmount = actual.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal variance = budgetedAmount.subtract(actualAmount);
            rows.add(List.of(category, budgetedAmount.toPlainString(), actualAmount.toPlainString(),
                    variance.toPlainString()));
        }
        return rows;
    }

    private static final class ActualExpenseTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Date", "Category", "Description", "Amount" };
        private final List<BudgetEntry> entries = new ArrayList<>();

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            BudgetEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.getEntryDate();
                case 1 -> entry.getCategory();
                case 2 -> entry.getDescription();
                case 3 -> UiSupport.formatCurrency(entry.getAmount());
                default -> "";
            };
        }

        private BudgetEntry getEntryAt(int rowIndex) {
            return entries.get(rowIndex);
        }

        private BudgetEntry findMatchingEntry(BudgetEntry targetEntry) {
            return entries.stream().filter(entry -> entry == targetEntry).findFirst().orElse(null);
        }

        private void setEntries(List<BudgetEntry> updatedEntries) {
            entries.clear();
            entries.addAll(updatedEntries);
            fireTableDataChanged();
        }
    }

    private record VarianceRow(String category, BigDecimal budgeted, BigDecimal actual, BigDecimal variance) {
    }

    private static final class VarianceTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = { "Category", "Budgeted", "Actual", "Surplus / Deficit" };
        private final List<VarianceRow> rows = new ArrayList<>();

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            VarianceRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.category();
                case 1 -> UiSupport.formatCurrency(row.budgeted());
                case 2 -> UiSupport.formatCurrency(row.actual());
                case 3 -> row.variance().signum() >= 0 ? "Surplus " + UiSupport.formatCurrency(row.variance())
                        : "Deficit " + UiSupport.formatCurrency(row.variance().abs());
                default -> "";
            };
        }

        private void setRows(List<VarianceRow> updatedRows) {
            rows.clear();
            rows.addAll(updatedRows);
            fireTableDataChanged();
        }
    }
}
