package tr.cabro.servicio.application.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Month;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeTableXYDataset;
import org.jfree.data.time.Week;
import tr.cabro.servicio.application.component.chart.TimeSeriesChart;
import tr.cabro.servicio.model.dto.ChartDataDto;
import tr.cabro.servicio.model.dto.SummaryCardDto;
import tr.cabro.servicio.model.enums.TimeFilter;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dönem finansı: seçilen zaman aralığında ciro, parça gideri, net kâr ve servis sayısı;
 * her biri önceki eşdeğer döneme göre değişimiyle. Altında gelir/kâr trend grafiği.
 */
public class PeriodPanel extends JPanel {

    private final JLabel lblRevenue = value();
    private final JLabel lblExpense = value();
    private final JLabel lblProfit = value();
    private final JLabel lblRecords = value();
    private final JLabel chRevenue = badge();
    private final JLabel chExpense = badge();
    private final JLabel chProfit = badge();
    private final JLabel chRecords = badge();
    private final TimeSeriesChart chart = new TimeSeriesChart();

    public PeriodPanel(TimeFilter initial, Consumer<TimeFilter> onChange) {
        setLayout(new MigLayout("insets 0, fillx, wrap", "[fill]", "[]"));
        setOpaque(false);

        JPanel card = DashboardUi.card("insets 14 16 12 16, fillx, wrap", "[fill]", "[]10[]12[]");

        JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[]push[]", "[center]"));
        header.setOpaque(false);
        header.add(DashboardUi.title("Kazanç"));
        header.add(filterBar(initial, onChange));
        card.add(header);

        JPanel figures = new JPanel(new MigLayout("insets 0, fillx, wrap 3, gapy 6", "[grow][right][right,56!]", ""));
        figures.setOpaque(false);
        addFigure(figures, "Ciro", lblRevenue, chRevenue);
        addFigure(figures, "Parça gideri", lblExpense, chExpense);
        addFigure(figures, "Net kâr", lblProfit, chProfit);
        addFigure(figures, "Servis sayısı", lblRecords, chRecords);
        card.add(figures);

        // Grafik kendi kart zeminini taşıyor; bu kartın içinde ikinci bir kart olmasın.
        chart.putClientProperty(FlatClientProperties.STYLE_CLASS, null);
        chart.putClientProperty(FlatClientProperties.STYLE, "background: null");
        card.add(chart, "h 200:220:260");

        add(card);
    }

    private JComponent filterBar(TimeFilter initial, Consumer<TimeFilter> onChange) {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.putClientProperty(FlatClientProperties.STYLE, "background: null");
        ButtonGroup group = new ButtonGroup();
        for (TimeFilter f : new TimeFilter[]{TimeFilter.DAY_1, TimeFilter.WEEK_1, TimeFilter.MONTH_1,
                TimeFilter.MONTH_3, TimeFilter.YEAR_1, TimeFilter.ALL_TIME}) {
            JToggleButton b = new JToggleButton(f.getLabel());
            b.putClientProperty(FlatClientProperties.STYLE, "toolbar.margin: 2,6,2,6; arc: 8;"
                    + " toolbar.selectedBackground: $Component.accentColor; toolbar.selectedForeground: $Servicio.onAccentForeground");
            b.setSelected(f == initial);
            b.addActionListener(e -> onChange.accept(f));
            group.add(b);
            bar.add(b);
        }
        return bar;
    }

    private static JLabel value() {
        JLabel l = new JLabel("—");
        l.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        return l;
    }

    private static JLabel badge() {
        JLabel l = new JLabel(" ");
        l.putClientProperty(FlatClientProperties.STYLE_CLASS, "greenBadge small");
        return l;
    }

    private static void addFigure(JPanel panel, String caption, JLabel value, JLabel change) {
        panel.add(DashboardUi.muted(caption));
        panel.add(value);
        panel.add(change);
    }

    /**
     * @param comparable önceki dönemle karşılaştırma anlamlı mı ("Tümü" seçiliyken değil)
     */
    public void setSummary(SummaryCardDto current, SummaryCardDto previous, boolean comparable) {
        lblRevenue.setText(Format.formatPrice(current.getTotalRevenue()));
        lblExpense.setText(Format.formatPrice(current.getTotalExpense()));
        lblProfit.setText(Format.formatPrice(current.getTotalProfit()));
        DashboardUi.styleMoney(lblProfit, current.getTotalProfit(), "font: bold +3");
        lblRecords.setText(String.valueOf(current.getTotalRecords()));

        setChange(chRevenue, comparable, current.getTotalRevenue(), previous.getTotalRevenue(), true);
        setChange(chExpense, comparable, current.getTotalExpense(), previous.getTotalExpense(), false);
        setChange(chProfit, comparable, current.getTotalProfit(), previous.getTotalProfit(), true);
        setChange(chRecords, comparable, BigDecimal.valueOf(current.getTotalRecords()),
                BigDecimal.valueOf(previous.getTotalRecords()), true);
    }

    /** Değişim rozeti; gider için artış kötüdür, bu yüzden renk yönü {@code higherIsBetter} ile belirlenir. */
    private static void setChange(JLabel label, boolean comparable, BigDecimal cur, BigDecimal prev, boolean higherIsBetter) {
        if (!comparable || cur == null || prev == null || prev.signum() == 0) {
            label.setVisible(false);
            return;
        }
        double change = (cur.doubleValue() - prev.doubleValue()) / Math.abs(prev.doubleValue()) * 100.0;
        boolean good = higherIsBetter ? change >= 0 : change <= 0;
        label.setText(String.format("%s%.0f%%", change >= 0 ? "+" : "", change));
        label.putClientProperty(FlatClientProperties.STYLE_CLASS, (good ? "greenBadge" : "redBadge") + " small");
        label.setToolTipText("Önceki eşdeğer döneme göre");
        label.setVisible(true);
    }

    public void setTrend(List<ChartDataDto> revenue, List<ChartDataDto> profit, String granularity) {
        TimeTableXYDataset dataset = new TimeTableXYDataset();
        addSeries(dataset, revenue, granularity, "Ciro");
        addSeries(dataset, profit, granularity, "Net kâr");
        chart.setDataset(dataset);
    }

    /** Aynı periyoda düşen etiketler (ör. Ocak'ta SQLite hafta 00 ve 01) üst üste yazılmaz, toplanır. */
    private static void addSeries(TimeTableXYDataset dataset, List<ChartDataDto> data, String granularity, String series) {
        java.util.Map<RegularTimePeriod, Double> sums = new java.util.LinkedHashMap<>();
        for (ChartDataDto dto : data) {
            if (dto.getLabel() == null || dto.getValue() == null) continue;
            sums.merge(parsePeriod(dto.getLabel(), granularity), dto.getValue().doubleValue(), Double::sum);
        }
        sums.forEach((period, value) -> dataset.add(period, value, series));
    }

    private static RegularTimePeriod parsePeriod(String label, String granularity) {
        if ("hour".equals(granularity)) {
            String[] parts = label.split("T");
            LocalDate date = LocalDate.parse(parts[0]);
            return new Hour(Integer.parseInt(parts[1]), new Day(date.getDayOfMonth(), date.getMonthValue(), date.getYear()));
        } else if ("day".equals(granularity)) {
            LocalDate d = LocalDate.parse(label);
            return new Day(d.getDayOfMonth(), d.getMonthValue(), d.getYear());
        } else if ("week".equals(granularity)) {
            // SQLite %W yılın ilk pazartesisinden önceki günlere 00 verir; JFreeChart Week 1..53 bekler.
            String[] p = label.split("-");
            return new Week(Math.max(1, Integer.parseInt(p[1])), Integer.parseInt(p[0]));
        } else {
            String[] p = label.split("-");
            return new Month(Integer.parseInt(p[1]), Integer.parseInt(p[0]));
        }
    }
}
