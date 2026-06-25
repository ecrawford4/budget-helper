package budgethelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {
    private static final String FALLBACK_VERSION = "0.0.0-dev";

    private AppVersion() {
    }

    public static String currentVersion() {
        Package appPackage = BudgetHelperApp.class.getPackage();
        if (appPackage != null && appPackage.getImplementationVersion() != null
                && !appPackage.getImplementationVersion().isBlank()) {
            return appPackage.getImplementationVersion().trim();
        }
        try (InputStream inputStream = AppVersion.class
                .getResourceAsStream("/META-INF/maven/com.example/budget-helper/pom.properties")) {
            if (inputStream == null) {
                return FALLBACK_VERSION;
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            String version = properties.getProperty("version", FALLBACK_VERSION).trim();
            return version.isBlank() ? FALLBACK_VERSION : version;
        } catch (IOException exception) {
            return FALLBACK_VERSION;
        }
    }
}
