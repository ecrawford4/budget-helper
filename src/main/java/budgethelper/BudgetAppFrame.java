package budgethelper;

import java.awt.Dimension;
import java.awt.Image;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

public final class BudgetAppFrame extends JFrame {
    private final BudgetDataStore dataStore;
    private final LocalProfileStore profileStore;
    private final AppUpdateService updateService;
    private final JTabbedPane tabs;
    private AppPreferences preferences;
    private boolean suppressAutoSave;
    private String currentProfileKey;

    public BudgetAppFrame() {
        super("Budget Helper");
        dataStore = new BudgetDataStore();
        profileStore = new LocalProfileStore();
        updateService = new AppUpdateService();
        tabs = new JTabbedPane();
        preferences = AppPreferences.defaults();
        tabs.addTab("Profile", new ProfileTabPanel(dataStore, new ProfileActions()));
        tabs.addTab("Budget Entry", new BudgetEntryPanel(dataStore));
        tabs.addTab("Reporting", new BudgetReportingPanel(dataStore));
        tabs.addTab("Summary", new BudgetSummaryPanel(dataStore));
        setContentPane(tabs);
        setJMenuBar(buildMenuBar());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1180, 760));
        loadAppIcon();
        loadInitialProfile();
        dataStore.addChangeListener(this::saveProfileSilently);
        applyInitialTabSelection();
        pack();
        setLocationRelativeTo(null);
    }

    private void loadAppIcon() {
        try (java.io.InputStream stream = getClass().getResourceAsStream("/budgethelper/icon.png")) {
            if (stream != null) {
                Image icon = ImageIO.read(stream);
                if (icon != null) {
                    setIconImage(icon);
                }
            }
        } catch (IOException ignored) {
            // icon is optional – missing file does not prevent startup
        }
    }

    private void loadInitialProfile() {
        try {
            preferences = profileStore.loadPreferences();
            currentProfileKey = profileStore.getCurrentProfileKey();
            suppressAutoSave = true;
            dataStore.replaceProfile(profileStore.loadProfile(currentProfileKey));
            suppressAutoSave = false;
        } catch (IOException | RuntimeException exception) {
            suppressAutoSave = false;
            JOptionPane.showMessageDialog(this,
                    "The local budget profile could not be loaded.\n\n" + exception.getMessage(),
                    "Profile Load Failed",
                    JOptionPane.ERROR_MESSAGE);
            preferences = AppPreferences.defaults();
            currentProfileKey = profileStore.toProfileKey(dataStore.getProfileInfo().displayBudgetName());
        }
    }

    private void saveProfileSilently() {
        if (suppressAutoSave) {
            return;
        }
        try {
            if (currentProfileKey == null || currentProfileKey.isBlank()) {
                currentProfileKey = profileStore.toProfileKey(dataStore.getProfileInfo().displayBudgetName());
            }
            profileStore.saveProfile(currentProfileKey, dataStore.exportProfile());
            profileStore.saveCurrentProfileKey(currentProfileKey);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this,
                    "The local budget profile could not be saved to\n" + profileStore.getProfilePath(currentProfileKey)
                            + "\n\n" + exception.getMessage(),
                    "Profile Save Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveProfileInfo(BudgetProfileInfo updatedInfo) {
        dataStore.updateProfileInfo(updatedInfo);
        JOptionPane.showMessageDialog(this, "Profile details saved for " + updatedInfo.displayBudgetName() + ".",
                "Profile Updated", JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveAsProfile(BudgetProfileInfo updatedInfo) {
        try {
            currentProfileKey = profileStore.toProfileKey(updatedInfo.displayBudgetName());
            suppressAutoSave = true;
            dataStore.updateProfileInfo(updatedInfo);
            suppressAutoSave = false;
            profileStore.saveProfile(currentProfileKey, dataStore.exportProfile());
            profileStore.saveCurrentProfileKey(currentProfileKey);
            JOptionPane.showMessageDialog(this, "Profile saved as " + updatedInfo.displayBudgetName() + ".",
                    "Profile Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            suppressAutoSave = false;
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Profile Save Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void duplicateCurrentProfile(BudgetProfileInfo duplicateInfo) {
        String duplicateKey = profileStore.toProfileKey(duplicateInfo.displayBudgetName());
        if (duplicateKey.equals(currentProfileKey)) {
            JOptionPane.showMessageDialog(this, "Choose a new budget name before duplicating this profile.",
                    "Duplicate Profile", JOptionPane.WARNING_MESSAGE);
            return;
        }
        saveAsProfile(duplicateInfo);
    }

    private void promptAndLoadProfile() {
        try {
            List<LocalProfileStore.SavedProfile> profiles = profileStore.listProfiles();
            if (profiles.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No saved profiles were found yet.", "No Profiles",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            LocalProfileStore.SavedProfile selectedProfile = (LocalProfileStore.SavedProfile) JOptionPane
                    .showInputDialog(
                            this,
                            "Select a budget profile to load.",
                            "Load Profile",
                            JOptionPane.PLAIN_MESSAGE,
                            null,
                            profiles.toArray(),
                            profiles.stream().filter(profile -> profile.profileKey().equals(currentProfileKey))
                                    .findFirst().orElse(profiles.get(0)));
            if (selectedProfile != null) {
                loadProfile(selectedProfile.profileKey());
            }
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Profile Load Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renameCurrentProfile(BudgetProfileInfo updatedInfo) {
        String newProfileKey = profileStore.toProfileKey(updatedInfo.displayBudgetName());
        try {
            String oldProfileKey = currentProfileKey;
            suppressAutoSave = true;
            dataStore.updateProfileInfo(updatedInfo);
            suppressAutoSave = false;
            profileStore.saveProfile(oldProfileKey, dataStore.exportProfile());
            profileStore.renameProfile(oldProfileKey, newProfileKey);
            currentProfileKey = newProfileKey;
            profileStore.saveCurrentProfileKey(currentProfileKey);
            JOptionPane.showMessageDialog(this, "Profile renamed to " + updatedInfo.displayBudgetName() + ".",
                    "Profile Renamed", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            suppressAutoSave = false;
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Profile Rename Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteCurrentProfile() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete the current profile " + dataStore.getProfileInfo().displayBudgetName() + "?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            profileStore.deleteProfile(currentProfileKey);
            List<LocalProfileStore.SavedProfile> profiles = profileStore.listProfiles();
            if (profiles.isEmpty()) {
                BudgetProfileInfo defaultInfo = BudgetProfileInfo.empty();
                currentProfileKey = profileStore.toProfileKey(defaultInfo.displayBudgetName());
                suppressAutoSave = true;
                dataStore.replaceProfile(new BudgetProfile(defaultInfo, List.of()));
                suppressAutoSave = false;
                profileStore.saveProfile(currentProfileKey, dataStore.exportProfile());
                profileStore.saveCurrentProfileKey(currentProfileKey);
            } else {
                loadProfile(profiles.get(0).profileKey());
            }
            JOptionPane.showMessageDialog(this, "Profile deleted.", "Profile Deleted", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            suppressAutoSave = false;
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Profile Delete Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadProfile(String profileKey) throws IOException {
        currentProfileKey = profileKey;
        suppressAutoSave = true;
        dataStore.replaceProfile(profileStore.loadProfile(profileKey));
        suppressAutoSave = false;
        profileStore.saveCurrentProfileKey(currentProfileKey);
    }

    private void updatePreferences(AppPreferences updatedPreferences) {
        try {
            preferences = updatedPreferences;
            profileStore.savePreferences(preferences);
            JOptionPane.showMessageDialog(
                    this, "Preferences saved. The next launch will open on "
                            + readableTabName(preferences.initialTabId()) + ".",
                    "Preferences Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Preferences Save Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyInitialTabSelection() {
        tabs.setSelectedIndex(switch (preferences.initialTabId()) {
            case AppPreferences.PROFILE_TAB_ID -> 0;
            case AppPreferences.REPORTING_TAB_ID -> 2;
            case AppPreferences.SUMMARY_TAB_ID -> 3;
            default -> 1;
        });
    }

    private String readableTabName(String tabId) {
        return switch (tabId) {
            case AppPreferences.PROFILE_TAB_ID -> "Profile";
            case AppPreferences.SUMMARY_TAB_ID -> "Summary";
            case AppPreferences.REPORTING_TAB_ID -> "Reporting";
            default -> "Budget Entry";
        };
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu helpMenu = new JMenu("Help");
        JMenuItem checkUpdatesItem = new JMenuItem("Check for Updates");
        checkUpdatesItem.addActionListener(event -> checkForUpdates(checkUpdatesItem));
        helpMenu.add(checkUpdatesItem);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private void checkForUpdates(JMenuItem checkUpdatesItem) {
        checkUpdatesItem.setEnabled(false);
        new SwingWorker<java.util.Optional<AppUpdateService.UpdateInfo>, Void>() {
            @Override
            protected java.util.Optional<AppUpdateService.UpdateInfo> doInBackground() throws Exception {
                return updateService.findUpdate(AppVersion.currentVersion());
            }

            @Override
            protected void done() {
                checkUpdatesItem.setEnabled(true);
                try {
                    java.util.Optional<AppUpdateService.UpdateInfo> update = get();
                    if (update.isEmpty()) {
                        JOptionPane.showMessageDialog(BudgetAppFrame.this,
                                "You already have the latest version installed.",
                                "No Updates Available",
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    AppUpdateService.UpdateInfo updateInfo = update.get();
                    int choice = JOptionPane.showConfirmDialog(BudgetAppFrame.this,
                            "A new version " + updateInfo.latestTag() + " is available.\n\n"
                                    + "Do you want to download and install it now?",
                            "Update Available",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION) {
                        downloadAndInstallUpdate(updateInfo);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    JOptionPane.showMessageDialog(BudgetAppFrame.this,
                            exception.getMessage() == null ? exception.toString() : exception.getMessage(),
                            "Update Check Failed",
                            JOptionPane.ERROR_MESSAGE);
                } catch (ExecutionException exception) {
                    Throwable cause = exception;
                    if (exception.getCause() != null) {
                        cause = exception.getCause();
                    }
                    JOptionPane.showMessageDialog(BudgetAppFrame.this,
                            cause.getMessage() == null ? cause.toString() : cause.getMessage(),
                            "Update Check Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void downloadAndInstallUpdate(AppUpdateService.UpdateInfo updateInfo) {
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return updateService.downloadInstaller(updateInfo);
            }

            @Override
            protected void done() {
                try {
                    Path installerPath = get();
                    int launchChoice = JOptionPane.showConfirmDialog(BudgetAppFrame.this,
                            "Update installer downloaded to:\n" + installerPath + "\n\n"
                                    + "Launch installer now? The app will close first.",
                            "Ready to Install Update",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE);
                    if (launchChoice != JOptionPane.YES_OPTION) {
                        return;
                    }
                    updateService.launchInstaller(installerPath);
                    dispose();
                    System.exit(0);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    JOptionPane.showMessageDialog(BudgetAppFrame.this,
                            exception.getMessage() == null ? exception.toString() : exception.getMessage(),
                            "Update Install Failed",
                            JOptionPane.ERROR_MESSAGE);
                } catch (ExecutionException exception) {
                    Throwable cause = exception;
                    if (exception.getCause() != null) {
                        cause = exception.getCause();
                    }
                    JOptionPane.showMessageDialog(BudgetAppFrame.this,
                            cause.getMessage() == null ? cause.toString() : cause.getMessage(),
                            "Update Install Failed",
                            JOptionPane.ERROR_MESSAGE);
                } catch (IOException exception) {
                    JOptionPane.showMessageDialog(BudgetAppFrame.this,
                            exception.getMessage() == null ? exception.toString() : exception.getMessage(),
                            "Update Install Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private final class ProfileActions implements ProfileTabPanel.Actions {
        @Override
        public void saveProfileInfo(BudgetProfileInfo profileInfo) {
            BudgetAppFrame.this.saveProfileInfo(profileInfo);
        }

        @Override
        public void saveAsProfile(BudgetProfileInfo profileInfo) {
            BudgetAppFrame.this.saveAsProfile(profileInfo);
        }

        @Override
        public void duplicateCurrentProfile(BudgetProfileInfo profileInfo) {
            BudgetAppFrame.this.duplicateCurrentProfile(profileInfo);
        }

        @Override
        public void loadProfile() {
            BudgetAppFrame.this.promptAndLoadProfile();
        }

        @Override
        public void renameCurrentProfile(BudgetProfileInfo profileInfo) {
            BudgetAppFrame.this.renameCurrentProfile(profileInfo);
        }

        @Override
        public void deleteCurrentProfile() {
            BudgetAppFrame.this.deleteCurrentProfile();
        }

        @Override
        public AppPreferences getPreferences() {
            return preferences;
        }

        @Override
        public void updatePreferences(AppPreferences preferences) {
            BudgetAppFrame.this.updatePreferences(preferences);
        }

        @Override
        public String getCurrentProfileName() {
            return dataStore.getProfileInfo().displayBudgetName();
        }
    }
}