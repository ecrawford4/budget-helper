package budgethelper;

public record AppPreferences(String initialTabId) {
    public static final String PROFILE_TAB_ID = "profile";
    public static final String BUDGET_ENTRY_TAB_ID = "budget-entry";
    public static final String SUMMARY_TAB_ID = "summary";
    public static final String REPORTING_TAB_ID = "reporting";

    public AppPreferences {
        initialTabId = normalize(initialTabId);
    }

    public static AppPreferences defaults() {
        return new AppPreferences(BUDGET_ENTRY_TAB_ID);
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return switch (normalized) {
            case PROFILE_TAB_ID, BUDGET_ENTRY_TAB_ID, SUMMARY_TAB_ID, REPORTING_TAB_ID -> normalized;
            default -> BUDGET_ENTRY_TAB_ID;
        };
    }
}
