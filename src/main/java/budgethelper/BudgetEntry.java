package budgethelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class BudgetEntry {
    private final EntryType type;
    private final String category;
    private final String description;
    private final BigDecimal amount;
    private final LocalDate entryDate;

    public BudgetEntry(EntryType type, String category, String description, BigDecimal amount, LocalDate entryDate) {
        this.type = Objects.requireNonNull(type, "type");
        this.category = sanitize(category, "category");
        this.description = description == null ? "" : description.trim();
        this.amount = Objects.requireNonNull(amount, "amount");
        this.entryDate = Objects.requireNonNull(entryDate, "entryDate");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
    }

    public EntryType getType() {
        return type;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    private static String sanitize(String value, String fieldName) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return trimmed;
    }
}
