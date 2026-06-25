package budgethelper;

import java.util.List;

public record BudgetProfile(BudgetProfileInfo profileInfo, List<BudgetEntry> entries) {
    public BudgetProfile {
        profileInfo = profileInfo == null ? BudgetProfileInfo.empty() : profileInfo;
        entries = List.copyOf(entries == null ? List.of() : entries);
    }
}
