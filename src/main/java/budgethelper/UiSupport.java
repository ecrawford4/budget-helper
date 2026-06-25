package budgethelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;

public final class UiSupport {
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance();

    private UiSupport() {
    }

    public static JSpinner createDateSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "yyyy-MM-dd");
        spinner.setEditor(editor);
        JFormattedTextField textField = editor.getTextField();
        textField.setColumns(10);
        return spinner;
    }

    public static LocalDate toLocalDate(JSpinner spinner) {
        Date date = (Date) spinner.getValue();
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static void setLocalDate(JSpinner spinner, LocalDate localDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(localDate.getYear(), localDate.getMonthValue() - 1, localDate.getDayOfMonth());
        spinner.setValue(calendar.getTime());
    }

    public static BigDecimal parseAmount(String text) {
        String normalized = text == null ? "" : text.trim().replace("$", "").replace(",", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Amount is required.");
        }
        return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
    }

    public static String formatCurrency(BigDecimal amount) {
        return CURRENCY_FORMAT.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    public static List<String> wrapText(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R")) {
            if (paragraph.length() <= maxLength) {
                lines.add(paragraph);
                continue;
            }
            StringBuilder currentLine = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (currentLine.isEmpty()) {
                    currentLine.append(word);
                } else if (currentLine.length() + word.length() + 1 > maxLength) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    currentLine.append(' ').append(word);
                }
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
            }
        }
        return lines;
    }
}
