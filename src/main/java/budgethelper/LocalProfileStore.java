package budgethelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public final class LocalProfileStore {
    private static final String PROFILE_DIRECTORY = ".budget-helper";
    private static final String LEGACY_PROFILE_DIRECTORY = ".budget-repo";
    private static final String PROFILES_DIRECTORY = "profiles";
    private static final String CURRENT_PROFILE_FILE = "current-profile.txt";
    private static final String PREFERENCES_FILE = "preferences.properties";
    private static final String PROFILE_EXTENSION = ".budget";
    private static final String LEGACY_PROFILE_FILE = "budget-profile.csv";
    private static final String DEFAULT_PROFILE_KEY = "default-budget";

    public record SavedProfile(String profileKey, String budgetName) {
        @Override
        public String toString() {
            return budgetName;
        }
    }

    public Path getProfilesDirectoryPath() {
        return Path.of(System.getProperty("user.home"), PROFILE_DIRECTORY, PROFILES_DIRECTORY);
    }

    private Path getLegacyProfilesDirectoryPath() {
        return Path.of(System.getProperty("user.home"), LEGACY_PROFILE_DIRECTORY, PROFILES_DIRECTORY);
    }

    private Path getCurrentProfilePointerPath() {
        return Path.of(System.getProperty("user.home"), PROFILE_DIRECTORY, CURRENT_PROFILE_FILE);
    }

    private Path getLegacyCurrentProfilePointerPath() {
        return Path.of(System.getProperty("user.home"), LEGACY_PROFILE_DIRECTORY, CURRENT_PROFILE_FILE);
    }

    private Path getLegacyProfilePath() {
        return Path.of(System.getProperty("user.home"), LEGACY_PROFILE_DIRECTORY, LEGACY_PROFILE_FILE);
    }

    private Path getLegacyNamedProfilePath(String profileKey) {
        return getLegacyProfilesDirectoryPath().resolve(profileKey + PROFILE_EXTENSION);
    }

    private Path getPreferencesPath() {
        return Path.of(System.getProperty("user.home"), PROFILE_DIRECTORY, PREFERENCES_FILE);
    }

    private Path getLegacyPreferencesPath() {
        return Path.of(System.getProperty("user.home"), LEGACY_PROFILE_DIRECTORY, PREFERENCES_FILE);
    }

    public Path getProfilePath(String profileKey) {
        return getProfilesDirectoryPath().resolve(profileKey + PROFILE_EXTENSION);
    }

    public String getCurrentProfileKey() throws IOException {
        Path pointerPath = getCurrentProfilePointerPath();
        if (Files.exists(pointerPath)) {
            String savedKey = Files.readString(pointerPath, StandardCharsets.UTF_8).trim();
            return savedKey.isBlank() ? DEFAULT_PROFILE_KEY : savedKey;
        }
        Path legacyPointerPath = getLegacyCurrentProfilePointerPath();
        if (Files.exists(legacyPointerPath)) {
            String savedKey = Files.readString(legacyPointerPath, StandardCharsets.UTF_8).trim();
            return savedKey.isBlank() ? DEFAULT_PROFILE_KEY : savedKey;
        }
        return DEFAULT_PROFILE_KEY;
    }

    public void saveCurrentProfileKey(String profileKey) throws IOException {
        Path pointerPath = getCurrentProfilePointerPath();
        Files.createDirectories(pointerPath.getParent());
        Files.writeString(pointerPath, profileKey, StandardCharsets.UTF_8);
    }

    public AppPreferences loadPreferences() throws IOException {
        Path preferencesPath = getPreferencesPath();
        if (Files.exists(preferencesPath)) {
            Properties properties = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(preferencesPath, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return new AppPreferences(properties.getProperty("initialTabId"));
        }
        Path legacyPreferencesPath = getLegacyPreferencesPath();
        if (Files.exists(legacyPreferencesPath)) {
            Properties properties = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(legacyPreferencesPath, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return new AppPreferences(properties.getProperty("initialTabId"));
        }
        return AppPreferences.defaults();
    }

    public void savePreferences(AppPreferences preferences) throws IOException {
        Path preferencesPath = getPreferencesPath();
        Files.createDirectories(preferencesPath.getParent());
        Properties properties = new Properties();
        properties.setProperty("initialTabId", preferences.initialTabId());
        try (java.io.Writer writer = Files.newBufferedWriter(preferencesPath, StandardCharsets.UTF_8)) {
            properties.store(writer, "budget-helper preferences");
        }
    }

    public BudgetProfile loadProfile(String profileKey) throws IOException {
        Path profilePath = resolveReadableProfilePath(profileKey);
        if (!Files.exists(profilePath)) {
            if (DEFAULT_PROFILE_KEY.equals(profileKey) && Files.exists(getLegacyProfilePath())) {
                return new BudgetProfile(BudgetProfileInfo.empty(), loadLegacyEntries());
            }
            return new BudgetProfile(new BudgetProfileInfo(profileKey, "", "", "", "", "", ""), List.of());
        }

        BudgetProfileInfo profileInfo = BudgetProfileInfo.empty();
        List<BudgetEntry> loadedEntries = new ArrayList<>();
        for (String line : Files.readAllLines(profilePath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] columns = parseCsvLine(line);
            if (columns.length < 2) {
                throw new IOException("Invalid profile format.");
            }
            switch (columns[0]) {
                case "VERSION" -> {
                }
                case "INFO" -> profileInfo = applyProfileInfoValue(profileInfo, columns);
                case "ENTRY" -> loadedEntries.add(parseEntry(columns));
                default -> throw new IOException("Unknown profile record.");
            }
        }
        if (profileInfo.displayBudgetName().equals("default-budget") && !profileKey.equals(DEFAULT_PROFILE_KEY)) {
            profileInfo = new BudgetProfileInfo(profileKey, profileInfo.yourName(), profileInfo.companyName(),
                    profileInfo.address(), profileInfo.billingAddress(), profileInfo.email(), profileInfo.phone());
        }
        return new BudgetProfile(profileInfo, loadedEntries);
    }

    public void saveProfile(String profileKey, BudgetProfile profile) throws IOException {
        Path profilePath = getProfilePath(profileKey);
        Files.createDirectories(profilePath.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", "VERSION", escape("1")));
        lines.add(String.join(",", "INFO", escape("budgetName"), escape(profile.profileInfo().displayBudgetName())));
        lines.add(String.join(",", "INFO", escape("yourName"), escape(profile.profileInfo().yourName())));
        lines.add(String.join(",", "INFO", escape("companyName"), escape(profile.profileInfo().companyName())));
        lines.add(String.join(",", "INFO", escape("address"), escape(profile.profileInfo().address())));
        lines.add(String.join(",", "INFO", escape("billingAddress"), escape(profile.profileInfo().billingAddress())));
        lines.add(String.join(",", "INFO", escape("email"), escape(profile.profileInfo().email())));
        lines.add(String.join(",", "INFO", escape("phone"), escape(profile.profileInfo().phone())));
        for (BudgetEntry entry : profile.entries()) {
            lines.add(String.join(",", "ENTRY", escape(entry.getType().name()), escape(entry.getCategory()),
                    escape(entry.getDescription()), escape(entry.getAmount().toPlainString()),
                    escape(entry.getEntryDate().toString())));
        }
        Path tempFile = profilePath.resolveSibling(profilePath.getFileName() + ".tmp");
        Files.write(tempFile, lines, StandardCharsets.UTF_8);
        Files.move(tempFile, profilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public List<SavedProfile> listProfiles() throws IOException {
        List<SavedProfile> profiles = new ArrayList<>();
        collectProfiles(profiles, getProfilesDirectoryPath());
        collectProfiles(profiles, getLegacyProfilesDirectoryPath());
        if (profiles.stream().noneMatch(profile -> profile.profileKey().equals(DEFAULT_PROFILE_KEY))
                && Files.exists(getLegacyProfilePath())) {
            profiles.add(new SavedProfile(DEFAULT_PROFILE_KEY, "default-budget"));
        }
        profiles.sort(Comparator.comparing(SavedProfile::budgetName, String.CASE_INSENSITIVE_ORDER));
        return profiles;
    }

    public String toProfileKey(String budgetName) {
        String normalized = budgetName == null ? "" : budgetName.trim().toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? DEFAULT_PROFILE_KEY : normalized;
    }

    public void renameProfile(String oldProfileKey, String newProfileKey) throws IOException {
        if (oldProfileKey.equals(newProfileKey)) {
            return;
        }
        Path oldPath = getProfilePath(oldProfileKey);
        if (!Files.exists(oldPath)) {
            oldPath = getLegacyNamedProfilePath(oldProfileKey);
        }
        Path newPath = getProfilePath(newProfileKey);
        Files.createDirectories(newPath.getParent());
        if (Files.exists(newPath)) {
            throw new IOException("A profile with that name already exists.");
        }
        if (!Files.exists(oldPath)) {
            throw new IOException("The current profile file could not be found.");
        }
        Files.move(oldPath, newPath);
    }

    public void deleteProfile(String profileKey) throws IOException {
        Path profilePath = getProfilePath(profileKey);
        if (Files.exists(profilePath)) {
            Files.delete(profilePath);
        }
        Path legacyPath = getLegacyNamedProfilePath(profileKey);
        if (Files.exists(legacyPath)) {
            Files.delete(legacyPath);
        }
    }

    private Path resolveReadableProfilePath(String profileKey) {
        Path currentPath = getProfilePath(profileKey);
        if (Files.exists(currentPath)) {
            return currentPath;
        }
        Path legacyPath = getLegacyNamedProfilePath(profileKey);
        if (Files.exists(legacyPath)) {
            return legacyPath;
        }
        return currentPath;
    }

    private void collectProfiles(List<SavedProfile> profiles, Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + PROFILE_EXTENSION)) {
            for (Path profilePath : stream) {
                String fileName = profilePath.getFileName().toString();
                String profileKey = fileName.substring(0, fileName.length() - PROFILE_EXTENSION.length());
                if (profiles.stream().anyMatch(profile -> profile.profileKey().equals(profileKey))) {
                    continue;
                }
                profiles.add(new SavedProfile(profileKey, loadProfile(profileKey).profileInfo().displayBudgetName()));
            }
        }
    }

    private List<BudgetEntry> loadLegacyEntries() throws IOException {
        List<BudgetEntry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(getLegacyProfilePath(), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.equals("type,category,description,amount,date")) {
                continue;
            }
            String[] columns = parseCsvLine(line);
            entries.add(new BudgetEntry(EntryType.valueOf(columns[0]), columns[1], columns[2],
                    new java.math.BigDecimal(columns[3]), java.time.LocalDate.parse(columns[4])));
        }
        return entries;
    }

    private static BudgetProfileInfo applyProfileInfoValue(BudgetProfileInfo existing, String[] columns)
            throws IOException {
        if (columns.length != 3) {
            throw new IOException("Invalid profile info record.");
        }
        return switch (columns[1]) {
            case "budgetName" -> new BudgetProfileInfo(columns[2], existing.yourName(), existing.companyName(),
                    existing.address(), existing.billingAddress(), existing.email(), existing.phone());
            case "yourName" -> new BudgetProfileInfo(existing.budgetName(), columns[2], existing.companyName(),
                    existing.address(), existing.billingAddress(), existing.email(), existing.phone());
            case "companyName" -> new BudgetProfileInfo(existing.budgetName(), existing.yourName(), columns[2],
                    existing.address(), existing.billingAddress(), existing.email(), existing.phone());
            case "address" -> new BudgetProfileInfo(existing.budgetName(), existing.yourName(), existing.companyName(),
                    columns[2], existing.billingAddress(), existing.email(), existing.phone());
            case "billingAddress" -> new BudgetProfileInfo(existing.budgetName(), existing.yourName(),
                    existing.companyName(), existing.address(), columns[2], existing.email(), existing.phone());
            case "email" -> new BudgetProfileInfo(existing.budgetName(), existing.yourName(), existing.companyName(),
                    existing.address(), existing.billingAddress(), columns[2], existing.phone());
            case "phone" -> new BudgetProfileInfo(existing.budgetName(), existing.yourName(), existing.companyName(),
                    existing.address(), existing.billingAddress(), existing.email(), columns[2]);
            default -> throw new IOException("Unknown profile field.");
        };
    }

    private static BudgetEntry parseEntry(String[] columns) throws IOException {
        if (columns.length != 6) {
            throw new IOException("Invalid entry record.");
        }
        return new BudgetEntry(EntryType.valueOf(columns[1]), columns[2], columns[3],
                new java.math.BigDecimal(columns[4]), java.time.LocalDate.parse(columns[5]));
    }

    private static String escape(String value) {
        String normalized = value == null ? "" : value;
        return '"' + normalized.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"") + '"';
    }

    private static String[] parseCsvLine(String line) throws IOException {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (currentChar == ',' && !quoted) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(currentChar);
            }
        }
        if (quoted) {
            throw new IOException("Malformed quoted value.");
        }
        columns.add(current.toString());
        return columns.toArray(String[]::new);
    }
}
