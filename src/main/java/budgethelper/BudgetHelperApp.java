package budgethelper;

import javax.swing.SwingUtilities;

public final class BudgetHelperApp {
    private BudgetHelperApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BudgetAppFrame().setVisible(true));
    }
}
