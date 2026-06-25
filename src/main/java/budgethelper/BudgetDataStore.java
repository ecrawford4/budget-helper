package budgethelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class BudgetDataStore {
    private static final List<String> DEFAULT_INCOME_CATEGORIES = List.of("Salary", "Freelance", "Bonus", "Interest",
            "Other");
    private static final List<String> DEFAULT_EXPENSE_CATEGORIES = List.of("Housing", "Utilities", "Food",
            "Transportation", "Healthcare", "Debt", "Savings", "Entertainment", "Other");

    private final List<BudgetEntry> entries = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private BudgetProfileInfo profileInfo = BudgetProfileInfo.empty();

    public void addEntry(BudgetEntry entry) {
        entries.add(entry);
        sortAndNotify();
    }

    public void updateEntry(BudgetEntry existingEntry, BudgetEntry updatedEntry) {
        int entryIndex = entries.indexOf(existingEntry);
        if (entryIndex < 0) {
            throw new IllegalArgumentException("The selected entry no longer exists.");
        }
        entries.set(entryIndex, updatedEntry);
        sortAndNotify();
    }

    public void deleteEntry(BudgetEntry entry) {
        if (!entries.remove(entry)) {
            throw new IllegalArgumentException("The selected entry no longer exists.");
        }
        notifyListeners();
    }

    public void replaceProfile(BudgetProfile profile) {
        profileInfo = profile.profileInfo();
        entries.clear();
        entries.addAll(profile.entries());
        sortAndNotify();
    }

    public boolean hasIncomeEntries() {
        return entries.stream().anyMatch(entry -> entry.getType() == EntryType.INCOME);
    }

    public List<BudgetEntry> getEntries(EntryType type) {
        return entries.stream().filter(entry -> entry.getType() == type).toList();
    }

    public List<BudgetEntry> getEntries(EntryType type, LocalDate startDate, LocalDate endDate) {
        return entries.stream()
                .filter(entry -> entry.getType() == type)
                .filter(entry -> !entry.getEntryDate().isBefore(startDate) && !entry.getEntryDate().isAfter(endDate))
                .toList();
    }

    public Map<String, BigDecimal> totalsByCategory(EntryType type, LocalDate startDate, LocalDate endDate) {
        return getEntries(type, startDate, endDate).stream().collect(Collectors.toMap(
                BudgetEntry::getCategory,
                BudgetEntry::getAmount,
                BigDecimal::add,
                TreeMap::new));
    }

    public List<String> getIncomeCategories() {
        return mergeCategories(DEFAULT_INCOME_CATEGORIES, getEntries(EntryType.INCOME));
    }

    public List<String> getExpenseCategories() {
        List<BudgetEntry> expenseEntries = new ArrayList<>();
        expenseEntries.addAll(getEntries(EntryType.BUDGETED_EXPENSE));
        expenseEntries.addAll(getEntries(EntryType.ACTUAL_EXPENSE));
        return mergeCategories(DEFAULT_EXPENSE_CATEGORIES, expenseEntries);
    }

    public BudgetProfileInfo getProfileInfo() {
        return profileInfo;
    }

    public void updateProfileInfo(BudgetProfileInfo updatedProfileInfo) {
        profileInfo = updatedProfileInfo == null ? BudgetProfileInfo.empty() : updatedProfileInfo;
        notifyListeners();
    }

    public BudgetProfile exportProfile() {
        return new BudgetProfile(profileInfo, entries);
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public List<BudgetEntry> getAllEntries() {
        return Collections.unmodifiableList(entries);
    }

    public static BigDecimal sumEntries(Collection<BudgetEntry> budgetEntries) {
        return budgetEntries.stream().map(BudgetEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<String> mergeCategories(List<String> defaults, List<BudgetEntry> dynamicEntries) {
        LinkedHashSet<String> categories = new LinkedHashSet<>(defaults);
        dynamicEntries.stream().map(BudgetEntry::getCategory).filter(category -> !category.isBlank())
                .forEach(categories::add);
        return List.copyOf(categories);
    }

    private void sortAndNotify() {
        entries.sort(Comparator.comparing(BudgetEntry::getEntryDate)
                .thenComparing(BudgetEntry::getType)
                .thenComparing(BudgetEntry::getCategory)
                .thenComparing(BudgetEntry::getDescription));
        notifyListeners();
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}