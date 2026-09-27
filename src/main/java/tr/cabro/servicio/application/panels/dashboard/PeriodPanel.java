package tr.cabro.servicio.application.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.chart.ColumnChart;
import tr.cabro.servicio.database.repository.AnalyticsRepository.FinancePoint;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.reports.Bucket;
import tr.cabro.servicio.model.dto.SummaryCardDto;
import tr.cabro.servicio.model.enums.TimeFilter;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dönem finansı: seçilen zaman aralığında ciro, parça gideri, net kâr ve servis sayısı;
 * her biri önceki eşdeğer döneme göre değişimiyle. Altında ciro sütunları (servis ve satış
 * üst üste, nötr mürekkeple) ve üstünde net kâr çizgisi.
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
    private final ColumnChart chart = new ColumnChart();

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

        chart.setValueFormat(v -> Format.formatPrice(BigDecimal.valueOf(v)));
        card.add(chart, "h 190:210:240, wmin 0");

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

    public void setLoading() {
        chart.setLoading();
    }

    /** Kova anahtarları sırasıyla; verisi olmayan kova sıfır çizilir. */
    public void setTrend(Bucket bucket, List<String> keys, List<FinancePoint> points) {
        Map<String, FinancePoint> byKey = new HashMap<>();
        for (FinancePoint p : points) byKey.put(p.key(), p);
        int n = keys.size();
        double[] service = new double[n];
        double[] sales = new double[n];
        double[] profit = new double[n];
        List<String> axis = new ArrayList<>(n);
        List<String> tips = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String key = keys.get(i);
            FinancePoint p = byKey.get(key);
            if (p != null) {
                service[i] = p.serviceRevenue();
                sales[i] = p.salesNet();
                profit[i] = p.profit();
            }
            axis.add(bucket.shortLabel(key, AppLocale.uiLocale()));
            tips.add(bucket.longLabel(key, AppLocale.uiLocale()));
        }
        chart.setData(axis, tips, List.of(
                        new ColumnChart.Series("Servis", service, ColumnChart.Ink.neutral(0.62f)),
                        new ColumnChart.Series("Satış", sales, ColumnChart.Ink.neutral(0.30f))),
                new ColumnChart.Series("Net kâr", profit, ColumnChart.Ink.key("Servicio.successColor")), true);
    }
}
