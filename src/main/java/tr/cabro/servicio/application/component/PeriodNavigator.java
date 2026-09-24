package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.AppLocale;

import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.function.BiConsumer;

/**
 * Dönem gezgini: tek çerçeveli "‹ [takvim Bugün · 24 Eyl 2026] ›" şeridi; dönem tek gün ya da
 * tarih aralığı olabilir. Ortadaki düğme açılır paneli açar: hazır dönemler (Bugün, Dün, Bu hafta,
 * Bu ay, Geçen ay) ve serbest aralık için takvim. Oklar dönemi kendi uzunluğu kadar kaydırır
 * (tam ay ise ay ay); gelecek güne geçilmez. Dönem bugün değilse yanında "Bugün" kısayolu belirir.
 */
public class PeriodNavigator extends JPanel {

    private final BiConsumer<LocalDate, LocalDate> onChange;
    private final JButton next;
    private final JButton dateButton;
    private final JButton todayButton;
    private final DatePicker picker = new DatePicker();
    private final JPopupMenu popup = new JPopupMenu();
    private LocalDate from = LocalDate.now();
    private LocalDate to = LocalDate.now();

    public PeriodNavigator(BiConsumer<LocalDate, LocalDate> onChange) {
        super(new MigLayout("insets 0, gap 8", "", "[center]"));
        this.onChange = onChange;
        setOpaque(false);

        JButton prev = segment("icons/chevron-left.svg", "Önceki dönem (Ctrl+←)", () -> shift(-1));
        next = segment("icons/chevron-right.svg", "Sonraki dönem (Ctrl+→)", () -> shift(1));

        dateButton = new JButton();
        dateButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        dateButton.putClientProperty(FlatClientProperties.STYLE,
                "arc: 8; margin: 6,12,6,12; focusWidth: 0; iconTextGap: 8; font: bold");
        dateButton.setIcon(new Ikon("icons/calendar.svg", 16, "Label.foreground"));
        dateButton.setToolTipText("Gün ya da tarih aralığı seç");
        dateButton.addActionListener(e -> showPopup());

        JPanel group = new JPanel(new MigLayout("insets 2, gap 2", "", "[center]")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color line = UIManager.getColor("Component.borderColor");
                float arc = UIScale.scale(10f);
                g2.setColor(line != null ? line : Color.GRAY);
                g2.setStroke(new BasicStroke(UIScale.scale(1f)));
                g2.draw(new java.awt.geom.RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, arc, arc));
                g2.dispose();
            }
        };
        group.setOpaque(false);
        group.add(prev, "growy");
        group.add(dateButton, "growy");
        group.add(next, "growy");
        add(group, "growy");

        todayButton = new JButton("Bugün");
        todayButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        todayButton.putClientProperty(FlatClientProperties.STYLE,
                "arc: 8; margin: 6,10,6,10; focusWidth: 0; foreground: $Component.accentColor");
        todayButton.setToolTipText("Bugüne dön");
        todayButton.addActionListener(e -> set(LocalDate.now(), LocalDate.now()));
        add(todayButton, "growy, hidemode 3");

        buildPopup();
        render();
    }

    private static JButton segment(String icon, String tip, Runnable action) {
        JButton b = new JButton(new Ikon(icon, 16, "Label.foreground"));
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 6,8,6,8; focusWidth: 0");
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void buildPopup() {
        JPanel content = new JPanel(new MigLayout("insets 10, wrap 1, gapy 8", "[fill,grow]"));

        JPanel presets = new JPanel(new MigLayout("insets 0, gap 6, wrap 3", "[grow,fill][grow,fill][grow,fill]"));
        addPreset(presets, "Bugün", () -> { LocalDate t = LocalDate.now(); return new LocalDate[]{t, t}; });
        addPreset(presets, "Dün", () -> { LocalDate y = LocalDate.now().minusDays(1); return new LocalDate[]{y, y}; });
        addPreset(presets, "Bu hafta", () -> {
            LocalDate t = LocalDate.now();
            return new LocalDate[]{t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), t};
        });
        addPreset(presets, "Son 7 gün", () -> { LocalDate t = LocalDate.now(); return new LocalDate[]{t.minusDays(6), t}; });
        addPreset(presets, "Bu ay", () -> { LocalDate t = LocalDate.now(); return new LocalDate[]{t.withDayOfMonth(1), t}; });
        addPreset(presets, "Geçen ay", () -> {
            LocalDate first = LocalDate.now().minusMonths(1).withDayOfMonth(1);
            return new LocalDate[]{first, first.with(TemporalAdjusters.lastDayOfMonth())};
        });
        content.add(presets);

        JLabel hint = new JLabel("Ya da takvimden başlangıç ve bitiş günlerini seçin");
        hint.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        content.add(hint);

        picker.setDateSelectionMode(DatePicker.DateSelectionMode.BETWEEN_DATE_SELECTED);
        content.add(picker);

        JButton apply = new JButton("Aralığı uygula");
        apply.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 6,12,6,12; font: bold");
        apply.addActionListener(e -> {
            LocalDate[] range = picker.getSelectedDateRange();
            if (range == null) {
                LocalDate one = picker.getSelectedDate();
                if (one == null) return;
                range = new LocalDate[]{one, one};
            }
            popup.setVisible(false);
            set(range[0], range[1]);
        });
        content.add(apply);
        popup.add(content);
    }

    private void addPreset(JPanel panel, String text, java.util.function.Supplier<LocalDate[]> range) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,8,5,8");
        b.addActionListener(e -> {
            popup.setVisible(false);
            LocalDate[] r = range.get();
            set(r[0], r[1]);
        });
        panel.add(b);
    }

    private void showPopup() {
        if (from.equals(to)) picker.setSelectedDate(from);
        else picker.setSelectedDateRange(from, to);
        popup.show(dateButton, 0, dateButton.getHeight() + UIScale.scale(4));
    }

    /** Dönemi değiştirir ve dinleyiciyi çağırır; gelecek gün bugüne çekilir, aynı dönem yok sayılır. */
    public void set(LocalDate newFrom, LocalDate newTo) {
        LocalDate today = LocalDate.now();
        LocalDate f = newFrom.isAfter(today) ? today : newFrom;
        LocalDate t = newTo.isAfter(today) ? today : newTo;
        if (t.isBefore(f)) { LocalDate tmp = f; f = t; t = tmp; }
        if (f.equals(from) && t.equals(to)) return;
        from = f;
        to = t;
        render();
        onChange.accept(from, to);
    }

    /** Dönemi kendi uzunluğu kadar kaydırır; tam takvim ayıysa ay ay gider. */
    public void shift(int direction) {
        if (isFullMonth()) {
            LocalDate f = from.plusMonths(direction);
            set(f, f.with(TemporalAdjusters.lastDayOfMonth()));
            return;
        }
        long span = ChronoUnit.DAYS.between(from, to) + 1;
        set(from.plusDays(direction * span), to.plusDays(direction * span));
    }

    private boolean isFullMonth() {
        return from.getDayOfMonth() == 1 && to.equals(from.with(TemporalAdjusters.lastDayOfMonth()));
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }

    public boolean isSingleDay() {
        return from.equals(to);
    }

    /** Görünümü seçili döneme göre tazeler (form yeniden açılınca gece yarısı geçilmiş olabilir). */
    public void render() {
        LocalDate today = LocalDate.now();
        java.util.Locale loc = AppLocale.uiLocale();
        DateTimeFormatter withYear = DateTimeFormatter.ofPattern("d MMM yyyy", loc);
        DateTimeFormatter noYear = DateTimeFormatter.ofPattern("d MMM", loc);
        String text;
        if (isSingleDay()) {
            if (from.equals(today)) text = "Bugün  ·  " + from.format(withYear);
            else if (from.equals(today.minusDays(1))) text = "Dün  ·  " + from.format(withYear);
            else text = from.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", loc));
        } else {
            String name = presetName(today);
            String range = from.getYear() == to.getYear()
                    ? from.format(noYear) + " – " + to.format(withYear)
                    : from.format(withYear) + " – " + to.format(withYear);
            text = name != null ? name + "  ·  " + range : range;
        }
        dateButton.setText(text);
        next.setEnabled(to.isBefore(today));
        todayButton.setVisible(!(isSingleDay() && from.equals(today)));
        revalidate();
        repaint();
    }

    private String presetName(LocalDate today) {
        if (to.equals(today) && from.equals(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))) return "Bu hafta";
        if (to.equals(today) && from.equals(today.minusDays(6))) return "Son 7 gün";
        if (to.equals(today) && from.equals(today.withDayOfMonth(1))) return "Bu ay";
        LocalDate lastMonth = today.minusMonths(1).withDayOfMonth(1);
        if (from.equals(lastMonth) && to.equals(lastMonth.with(TemporalAdjusters.lastDayOfMonth()))) return "Geçen ay";
        return null;
    }
}
