package budgethelper;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class ProfileTabPanel extends JPanel {
    public interface Actions {
        void saveProfileInfo(BudgetProfileInfo profileInfo);

        void saveAsProfile(BudgetProfileInfo profileInfo);

        void duplicateCurrentProfile(BudgetProfileInfo profileInfo);

        void loadProfile();

        void renameCurrentProfile(BudgetProfileInfo profileInfo);

        void deleteCurrentProfile();

        AppPreferences getPreferences();

        void updatePreferences(AppPreferences preferences);

        String getCurrentProfileName();
    }

    private final BudgetDataStore dataStore;
    private final Actions actions;
    private final JLabel currentProfileLabel;
    private final JLabel dirtyStateLabel;
    private final JTextField budgetNameField;
    private final JTextField yourNameField;
    private final JTextField companyNameField;
    private final JTextArea addressArea;
    private final JTextArea billingAddressArea;
    private final JTextField emailField;
    private final JTextField phoneField;
    private final JComboBox<TabChoice> initialTabComboBox;
    private boolean refreshingForm;

    public ProfileTabPanel(BudgetDataStore dataStore, Actions actions) {
        super(new BorderLayout(12, 12));
        this.dataStore = dataStore;
        this.actions = actions;
        currentProfileLabel = new JLabel();
        dirtyStateLabel = new JLabel();
        budgetNameField = new JTextField(24);
        yourNameField = new JTextField(24);
        companyNameField = new JTextField(24);
        addressArea = createTextArea();
        billingAddressArea = createTextArea();
        emailField = new JTextField(24);
        phoneField = new JTextField(24);
        initialTabComboBox = new JComboBox<>(new TabChoice[] {
                new TabChoice("Profile", AppPreferences.PROFILE_TAB_ID),
                new TabChoice("Budget Entry", AppPreferences.BUDGET_ENTRY_TAB_ID),
                new TabChoice("Summary", AppPreferences.SUMMARY_TAB_ID),
                new TabChoice("Reporting", AppPreferences.REPORTING_TAB_ID) });

        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);
        attachDirtyListeners();
        dataStore.addChangeListener(this::refresh);
        refresh();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        header.add(currentProfileLabel);
        JButton loadButton = new JButton("Load Profile");
        loadButton.addActionListener(event -> {
            if (confirmDiscardChanges("loading another profile")) {
                actions.loadProfile();
            }
        });
        header.add(loadButton);

        JButton saveAsButton = new JButton("Save As Profile");
        saveAsButton.addActionListener(event -> actions.saveAsProfile(buildProfileInfoFromFields()));
        header.add(saveAsButton);

        JButton duplicateButton = new JButton("Duplicate Profile");
        duplicateButton.addActionListener(event -> actions.duplicateCurrentProfile(buildProfileInfoFromFields()));
        header.add(duplicateButton);

        JButton renameButton = new JButton("Rename Profile");
        renameButton.addActionListener(event -> actions.renameCurrentProfile(buildProfileInfoFromFields()));
        header.add(renameButton);

        JButton deleteButton = new JButton("Delete Profile");
        deleteButton.addActionListener(event -> actions.deleteCurrentProfile());
        header.add(deleteButton);

        header.add(dirtyStateLabel);
        return header;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.add(buildProfileDetailsPanel(), BorderLayout.CENTER);
        content.add(buildPreferencesPanel(), BorderLayout.SOUTH);
        return content;
    }

    private JPanel buildProfileDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Profile Details"));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        addFormRow(form, constraints, 0, "Budget Name", budgetNameField);
        addFormRow(form, constraints, 1, "Your Name", yourNameField);
        addFormRow(form, constraints, 2, "Company Name", companyNameField);
        addFormRow(form, constraints, 3, "Address", new JScrollPane(addressArea));
        addFormRow(form, constraints, 4, "Billing Address", new JScrollPane(billingAddressArea));
        addFormRow(form, constraints, 5, "Email", emailField);
        addFormRow(form, constraints, 6, "Phone", phoneField);
        panel.add(form, BorderLayout.CENTER);
        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveInfoButton = new JButton("Save Profile Info");
        saveInfoButton.addActionListener(event -> actions.saveProfileInfo(buildProfileInfoFromFields()));
        actionsRow.add(saveInfoButton);
        JButton resetButton = new JButton("Reset Fields");
        resetButton.addActionListener(event -> refresh());
        actionsRow.add(resetButton);
        panel.add(actionsRow, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPreferencesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Preferences"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("Initial Tab"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        panel.add(initialTabComboBox, constraints);
        JButton savePreferencesButton = new JButton("Save Preferences");
        savePreferencesButton.addActionListener(event -> savePreferences());
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 2;
        constraints.weightx = 0;
        panel.add(savePreferencesButton, constraints);
        return panel;
    }

    private void attachDirtyListeners() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateDirtyIndicator();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateDirtyIndicator();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateDirtyIndicator();
            }
        };
        budgetNameField.getDocument().addDocumentListener(listener);
        yourNameField.getDocument().addDocumentListener(listener);
        companyNameField.getDocument().addDocumentListener(listener);
        addressArea.getDocument().addDocumentListener(listener);
        billingAddressArea.getDocument().addDocumentListener(listener);
        emailField.getDocument().addDocumentListener(listener);
        phoneField.getDocument().addDocumentListener(listener);
        initialTabComboBox.addActionListener(event -> updateDirtyIndicator());
    }

    private boolean confirmDiscardChanges(String action) {
        if (!hasUnsavedChanges()) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "You have unsaved profile changes. Continue with " + action + "?",
                "Unsaved Changes",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    private boolean hasUnsavedChanges() {
        BudgetProfileInfo profileInfo = buildProfileInfoFromFields();
        TabChoice selectedChoice = (TabChoice) initialTabComboBox.getSelectedItem();
        boolean preferencesDirty = selectedChoice != null
                && !selectedChoice.tabId().equals(actions.getPreferences().initialTabId());
        return !profileInfo.equals(dataStore.getProfileInfo()) || preferencesDirty;
    }

    private void savePreferences() {
        TabChoice choice = (TabChoice) initialTabComboBox.getSelectedItem();
        if (choice == null) {
            JOptionPane.showMessageDialog(this, "Choose an initial tab first.", "Missing Preference",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        actions.updatePreferences(new AppPreferences(choice.tabId()));
    }

    private void updateDirtyIndicator() {
        if (refreshingForm) {
            return;
        }
        dirtyStateLabel.setText(hasUnsavedChanges() ? "Unsaved changes" : "");
    }

    private BudgetProfileInfo buildProfileInfoFromFields() {
        return new BudgetProfileInfo(budgetNameField.getText(), yourNameField.getText(), companyNameField.getText(),
                addressArea.getText(), billingAddressArea.getText(), emailField.getText(), phoneField.getText());
    }

    private void refresh() {
        BudgetProfileInfo profileInfo = dataStore.getProfileInfo();
        refreshingForm = true;
        currentProfileLabel.setText("Current Profile: " + actions.getCurrentProfileName());
        budgetNameField.setText(profileInfo.displayBudgetName());
        yourNameField.setText(profileInfo.yourName());
        companyNameField.setText(profileInfo.companyName());
        addressArea.setText(profileInfo.address());
        billingAddressArea.setText(profileInfo.billingAddress());
        emailField.setText(profileInfo.email());
        phoneField.setText(profileInfo.phone());
        Map<String, TabChoice> choicesById = new LinkedHashMap<>();
        for (int index = 0; index < initialTabComboBox.getItemCount(); index++) {
            TabChoice choice = initialTabComboBox.getItemAt(index);
            choicesById.put(choice.tabId(), choice);
        }
        initialTabComboBox.setSelectedItem(choicesById.get(actions.getPreferences().initialTabId()));
        refreshingForm = false;
        updateDirtyIndicator();
    }

    private static void addFormRow(JPanel form, GridBagConstraints constraints, int rowIndex, String labelText,
            java.awt.Component component) {
        constraints.gridx = 0;
        constraints.gridy = rowIndex;
        constraints.weightx = 0;
        constraints.weighty = 0;
        form.add(new JLabel(labelText), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.weighty = component instanceof JScrollPane ? 1.0 : 0;
        form.add(component, constraints);
    }

    private static JTextArea createTextArea() {
        JTextArea area = new JTextArea(3, 24);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private record TabChoice(String label, String tabId) {
        @Override
        public String toString() {
            return label;
        }
    }
}
