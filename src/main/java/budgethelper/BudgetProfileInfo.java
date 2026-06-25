package budgethelper;

import java.util.List;

public record BudgetProfileInfo(
        String budgetName,
        String yourName,
        String companyName,
        String address,
        String billingAddress,
        String email,
        String phone) {

    public BudgetProfileInfo {
        budgetName = sanitize(budgetName);
        yourName = sanitize(yourName);
        companyName = sanitize(companyName);
        address = sanitize(address);
        billingAddress = sanitize(billingAddress);
        email = sanitize(email);
        phone = sanitize(phone);
    }

    public static BudgetProfileInfo empty() {
        return new BudgetProfileInfo("default-budget", "", "", "", "", "", "");
    }

    public String displayBudgetName() {
        return budgetName.isBlank() ? "default-budget" : budgetName;
    }

    public List<String> addressLines() {
        return splitLines(address);
    }

    public List<String> billingAddressLines() {
        return splitLines(billingAddress);
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> splitLines(String value) {
        return value.isBlank() ? List.of() : value.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }
}
