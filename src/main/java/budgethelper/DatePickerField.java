package budgethelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public final class DatePickerField extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEKDAY_LABELS = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
    private static final Color SELECTED_DAY_COLOR = new Color(0xDDEBFF);

    private final JTextField displayField;
    private final JButton calendarButton;
    private final JPopupMenu popupMenu;
    private final List<Runnable> listeners;
    private LocalDate selectedDate;

    public DatePickerField() {
        this(LocalDate.now());
    }

    public DatePickerField(LocalDate initialDate) {
        super(new BorderLayout(6, 0));
        listeners = new ArrayList<>();
        displayField = new JTextField(10);
        displayField.setEditable(false);
        displayField.setFocusable(false);
        displayField.setHorizontalAlignment(JTextField.CENTER);
        calendarButton = new JButton("...");
        calendarButton.setMargin(new Insets(2, 8, 2, 8));
        calendarButton.setFocusable(false);
        calendarButton.setToolTipText("Choose a date");
        popupMenu = new JPopupMenu();
        popupMenu.setBorder(BorderFactory.createLineBorder(new Color(0xB8C2CC)));
        add(displayField, BorderLayout.CENTER);
        add(calendarButton, BorderLayout.EAST);
        setDate(initialDate == null ? LocalDate.now() : initialDate);
        calendarButton.addActionListener(event -> showCalendarPopup());
        displayField.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                showCalendarPopup();
            }
        });
    }

    public LocalDate getDate() {
        return selectedDate;
    }

    public void setDate(LocalDate date) {
        selectedDate = date == null ? LocalDate.now() : date;
        displayField.setText(DATE_FORMAT.format(selectedDate));
        fireDateChanged();
    }

    public void addDateChangeListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private void showCalendarPopup() {
        popupMenu.removeAll();
        popupMenu.add(buildCalendarPanel());
        popupMenu.show(calendarButton, 0, calendarButton.getHeight());
    }

    private JPanel buildCalendarPanel() {
        YearMonth visibleMonth = YearMonth.from(selectedDate);
        JPanel outerPanel = new JPanel(new BorderLayout(0, 8));
        outerPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel headerPanel = new JPanel(new BorderLayout(6, 0));
        JButton previousMonthButton = new JButton("<");
        JButton nextMonthButton = new JButton(">");
        JLabel monthLabel = new JLabel(visibleMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                SwingConstants.CENTER);
        previousMonthButton.setMargin(new Insets(2, 6, 2, 6));
        nextMonthButton.setMargin(new Insets(2, 6, 2, 6));
        previousMonthButton.addActionListener(event -> {
            selectedDate = selectedDate.minusMonths(1);
            popupMenu.setVisible(false);
            showCalendarPopup();
        });
        nextMonthButton.addActionListener(event -> {
            selectedDate = selectedDate.plusMonths(1);
            popupMenu.setVisible(false);
            showCalendarPopup();
        });
        headerPanel.add(previousMonthButton, BorderLayout.WEST);
        headerPanel.add(monthLabel, BorderLayout.CENTER);
        headerPanel.add(nextMonthButton, BorderLayout.EAST);

        JPanel calendarPanel = new JPanel(new GridLayout(0, 7, 4, 4));
        for (String label : WEEKDAY_LABELS) {
            JLabel weekdayLabel = new JLabel(label, SwingConstants.CENTER);
            weekdayLabel.setFont(weekdayLabel.getFont().deriveFont(java.awt.Font.BOLD));
            calendarPanel.add(weekdayLabel);
        }

        LocalDate firstOfMonth = visibleMonth.atDay(1);
        LocalDate firstVisibleDate = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        List<LocalDate> dates = new ArrayList<>();
        for (int index = 0; index < 42; index++) {
            dates.add(firstVisibleDate.plusDays(index));
        }
        for (LocalDate date : dates) {
            boolean inMonth = YearMonth.from(date).equals(visibleMonth);
            JButton dayButton = new JButton(Integer.toString(date.getDayOfMonth()));
            dayButton.setMargin(new Insets(2, 0, 2, 0));
            dayButton.setFocusPainted(false);
            dayButton.setOpaque(true);
            dayButton.setContentAreaFilled(true);
            dayButton.setBorder(BorderFactory.createLineBorder(new Color(0xD0D7DE)));
            dayButton.setEnabled(inMonth);
            dayButton.setBackground(date.equals(selectedDate) ? SELECTED_DAY_COLOR : Color.WHITE);
            dayButton.addActionListener(event -> {
                setDate(date);
                popupMenu.setVisible(false);
            });
            calendarPanel.add(dayButton);
        }

        outerPanel.add(headerPanel, BorderLayout.NORTH);
        outerPanel.add(calendarPanel, BorderLayout.CENTER);
        popupMenu.setPreferredSize(new Dimension(280, 260));
        return outerPanel;
    }

    private void fireDateChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}