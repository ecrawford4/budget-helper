package budgethelper;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

public final class BudgetEntryPanel extends JPanel {
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BudgetDataStore dataStore;
    private final JLabel incomeRequirementLabel;
    private final EntrySection incomeSection;
    private final EntrySection expenseSection;

    public BudgetEntryPanel(BudgetDataStore dataStore) {
        super(new BorderLayout(12, 12));
        this.dataStore = dataStore;
        incomeRequirementLabel = new JLabel();
        incomeRequirementLabel.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        add(incomeRequirementLabel, BorderLayout.NORTH);
        incomeSection = new EntrySection("Income Entries", EntryType.INCOME);
        expenseSection = new EntrySection("Budgeted Expenses", EntryType.BUDGETED_EXPENSE);
        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        contentPanel.add(incomeSection);
        contentPanel.add(expenseSection);
        add(contentPanel, BorderLayout.CENTER);
        dataStore.addChangeListener(this::refreshView);
        refreshView();
    }

    private void refreshView() {
        boolean hasIncome = dataStore.hasIncomeEntries();
        incomeRequirementLabel
                .setText(hasIncome ? "Income requirement satisfied. You can add or adjust expense entries."
                        : "Add at least one income entry before planning expenses.");
        incomeSection.refresh(dataStore.getEntries(EntryType.INCOME), dataStore.getIncomeCategories());
        expenseSection.refresh(dataStore.getEntries(EntryType.BUDGETED_EXPENSE), dataStore.getExpenseCategories());
    }

    private final class EntrySection extends JPanel {
        private final EntryType entryType;
        private final JComboBox<String> categoryBox;
        private final JTextField descriptionField;
        private final JTextField amountField;
        private final DatePickerField dateField;
        private final JButton addButton;
        private final JButton updateButton;
        private final JButton deleteButton;
        private final JButton clearButton;
        private final JButton exportCsvButton;
        private final EntryTableModel tableModel;
        private final JTable table;
        private BudgetEntry selectedEntry;

        private EntrySection(String title, EntryType entryType) {
            super(new BorderLayout(0, 12));
            this.entryType = entryType;
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)));
            categoryBox = new JComboBox<>();
            categoryBox.setEditable(true);
            descriptionField = new JTextField(18);
            amountField = new JTextField(12);
            dateField = UiSupport.createDatePickerField();
            tableModel = new EntryTableModel();
            table = new JTable(tableModel);
            addButton = new JButton(entryType == EntryType.INCOME ? "Add Income" : "Add Expense");
            updateButton = new JButton("Update Selected");
            deleteButton = new JButton("Delete Selected");
            clearButton = new JButton("Clear Selection");
            exportCsvButton = new JButton("Export CSV");
            add(buildForm(), BorderLayout.NORTH);
            add(buildTable(), BorderLayout.CENTER);
            resetSelection();
        }

        private JPanel buildForm() {
            JPanel formPanel = new JPanel(new GridBagLayout());
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
            formPanel.add(dateField, constraints);
            addButton.addActionListener(event -> addEntry());
            updateButton.addActionListener(event -> updateSelectedEntry());
            deleteButton.addActionListener(event -> deleteSelectedEntry());
            clearButton.addActionListener(event -> resetSelection());
            exportCsvButton.addActionListener(event -> exportCsv());
            constraints.gridx = 0;
            constraints.gridy = 4;
            constraints.gridwidth = 2;
            formPanel.add(addButton, constraints);
            JPanel buttonRow = new JPanel(new GridLayout(1, 3, 8, 0));
            buttonRow.add(updateButton);
            buttonRow.add(deleteButton);
            buttonRow.add(clearButton);
            constraints.gridy = 5;
            formPanel.add(buttonRow, constraints);
            constraints.gridy = 6;
            formPanel.add(exportCsvButton, constraints);
            JPanel wrapper = new JPanel();
            wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
            wrapper.add(formPanel);
            wrapper.add(Box.createVerticalStrut(8));
            return wrapper;
        }

        private JScrollPane buildTable() {
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setFillsViewportHeight(true);
            table.getSelectionModel().addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    populateSelectionFromTable();
                }
            });
            return new JScrollPane(table);
        }

        private void addEntry() {
            if (entryType != EntryType.INCOME && !dataStore.hasIncomeEntries()) {
                JOptionPane.showMessageDialog(BudgetEntryPanel.this,
                        "Create at least one income entry before adding expenses.", "Income Required",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                Object selectedCategory = categoryBox.getEditor().getItem();
                String category = selectedCategory == null ? "" : selectedCategory.toString();
                BigDecimal amount = UiSupport.parseAmount(amountField.getText());
                LocalDate entryDate = UiSupport.toLocalDate(dateField);
                dataStore.addEntry(new BudgetEntry(entryType, category, descriptionField.getText(), amount, entryDate));
                clearFormFields();
            } catch (RuntimeException exception) {
                JOptionPane.showMessageDialog(BudgetEntryPanel.this, exception.getMessage(), "Invalid Entry",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        private void updateSelectedEntry() {
            if (selectedEntry == null) {
                JOptionPane.showMessageDialog(BudgetEntryPanel.this, "Select an entry to edit first.", "No Selection",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                dataStore.updateEntry(selectedEntry, buildEntryFromForm());
                resetSelection();
            } catch (RuntimeException exception) {
                JOptionPane.showMessageDialog(BudgetEntryPanel.this, exception.getMessage(), "Invalid Entry",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        private void deleteSelectedEntry() {
            if (selectedEntry == null) {
                JOptionPane.showMessageDialog(BudgetEntryPanel.this, "Select an entry to delete first.", "No Selection",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            int choice = JOptionPane.showConfirmDialog(BudgetEntryPanel.this,
                    "Delete the selected " + (entryType == EntryType.INCOME ? "income" : "expense") + " entry?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                dataStore.deleteEntry(selectedEntry);
                resetSelection();
            } catch (RuntimeException exception) {
                JOptionPane.showMessageDialog(BudgetEntryPanel.this, exception.getMessage(), "Delete Failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        private BudgetEntry buildEntryFromForm() {
            Object selectedCategory = categoryBox.getEditor().getItem();
            String category = selectedCategory == null ? "" : selectedCategory.toString();
            BigDecimal amount = UiSupport.parseAmount(amountField.getText());
            LocalDate entryDate = UiSupport.toLocalDate(dateField);
            return new BudgetEntry(entryType, category, descriptionField.getText(), amount, entryDate);
        }

        private void populateSelectionFromTable() {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                if (selectedEntry != null) {
                    resetSelection();
                }
                return;
            }
            selectedEntry = tableModel.getEntryAt(viewRow);
            categoryBox.setSelectedItem(selectedEntry.getCategory());
            descriptionField.setText(selectedEntry.getDescription());
            amountField.setText(selectedEntry.getAmount().toPlainString());
            UiSupport.setLocalDate(dateField, selectedEntry.getEntryDate());
            addButton.setEnabled(false);
            updateButton.setEnabled(true);
            deleteButton.setEnabled(true);
        }

        private void resetSelection() {
            selectedEntry = null;
            table.clearSelection();
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
            UiSupport.setLocalDate(dateField, LocalDate.now());
        }

        private void refresh(List<BudgetEntry> entries, List<String> categories) {
            Object currentSelection = categoryBox.isEditable() ? categoryBox.getEditor().getItem()
                    : categoryBox.getSelectedItem();
            categoryBox.setModel(new DefaultComboBoxModel<>(categories.toArray(String[]::new)));
            categoryBox.setSelectedItem(currentSelection);
            tableModel.setEntries(entries);
            if (selectedEntry != null) {
                BudgetEntry refreshedSelection = tableModel.findMatchingEntry(selectedEntry);
                if (refreshedSelection == null) {
                    resetSelection();
                } else {
                    selectedEntry = refreshedSelection;
                    categoryBox.setSelectedItem(selectedEntry.getCategory());
                    descriptionField.setText(selectedEntry.getDescription());
                    amountField.setText(selectedEntry.getAmount().toPlainString());
                    UiSupport.setLocalDate(dateField, selectedEntry.getEntryDate());
                    updateButton.setEnabled(true);
                    deleteButton.setEnabled(true);
                    addButton.setEnabled(false);
                }
            }
        }

        private void exportCsv() {
            try {
                JFileChooser fileChooser = new JFileChooser();
                String budgetName = dataStore.getProfileInfo().displayBudgetName().replaceAll("[^A-Za-z0-9-]+", "-");
                String entryLabel = entryType == EntryType.INCOME ? "income-entries" : "budgeted-expense-entries";
                fileChooser.setSelectedFile(new File(
                        budgetName + "-" + entryLabel + "-" + LocalDate.now().format(FILE_DATE_FORMAT) + ".csv"));
                int result = fileChooser.showSaveDialog(BudgetEntryPanel.this);
                if (result != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                List<List<String>> rows = new ArrayList<>();
                rows.add(List.of("Budget Name", dataStore.getProfileInfo().displayBudgetName()));
                rows.add(List.of("Entry Type", entryType == EntryType.INCOME ? "Income" : "Budgeted Expense"));
                rows.add(List.of());
                rows.add(List.of("Date", "Category", "Description", "Amount"));
                for (BudgetEntry entry : dataStore.getEntries(entryType)) {
                    rows.add(List.of(entry.getEntryDate().toString(), entry.getCategory(), entry.getDescription(),
                            entry.getAmount().toPlainString()));
                }
                CsvExportSupport.exportRows(rows, fileChooser.getSelectedFile().toPath());
                JOptionPane.showMessageDialog(BudgetEntryPanel.this, "CSV exported successfully.", "Export Complete",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException exception) {
                JOptionPane.showMessageDialog(BudgetEntryPanel.this, exception.getMessage(), "Export Failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static final class EntryTableModel extends AbstractTableModel {
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
}
