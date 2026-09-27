package tr.cabro.servicio.application.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.chart.ColumnChart;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.application.forms.FormWorkOrders;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.database.repository.AnalyticsRepository.Breakdown;
import tr.cabro.servicio.database.repository.AnalyticsRepository.FlowPoint;
import tr.cabro.servicio.database.repository.AnalyticsRepository.ServiceStats;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.reports.Bucket;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servis akışı: seçili dönemde atölyeye giren ve teslim edilen cihazlar. Solda kova başına
 * "Açılan / Teslim edilen" sütunları (bir sütun tıklanınca o günlerde açılan servisler listelenir),
 * üstünde dönemin özeti (açılan, teslim, ortalama teslim süresi). Sağda en çok gelen cihaz türleri
 * ya da markalar; satır tıklanınca servis listesi o ada ve döneme süzülür.
 * Eski iki pasta grafiğin yerini alır: pastalar çok dilimde okunmuyor, tıklanmıyordu.
 */
public class FlowPanel extends JPanel {

    private static final int RANK_ROWS = 6;
    private static final String VIEW_TYPES = "types";
    private static final String VIEW_BRANDS = "brands";

    private final JLabel lblPeriod = DashboardUi.small(" ");
    private final JPanel summary = new JPanel(new MigLayout("insets 0, gap 0", "", "[center]"));
    private final ColumnChart chart = new ColumnChart();
    private final ViewTabs rankTabs = new ViewTabs();
    private final JPanel rankRows = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0 2", "[fill, grow]", ""));

    private Bucket bucket = Bucket.DAY;
    private List<String> keys = Collections.emptyList();
    private LocalDate from = LocalDate.now();
    private LocalDate to = LocalDate.now();
    private List<Breakdown> types = Collections.emptyList();
    private List<Breakdown> brands = Collections.emptyList();

    public FlowPanel() {
        setLayout(new MigLayout("insets 0, fillx, wrap", "[fill]", "[]"));
        setOpaque(false);

        // Tek kart, iki bölge; aradaki ince çizgi bölgeleri ayırır (kart içinde kart yok).
        JPanel card = DashboardUi.card("insets 14 16 12 16, fillx, hidemode 3",
                "[fill, grow, 0:pref]18[1!]16[fill, 210:34%:300]", "[top]");

        JPanel left = new JPanel(new MigLayout("insets 0, fillx, wrap", "[fill, grow]", "[]4[]10[]"));
        left.setOpaque(false);
        JPanel header = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[][]push", "[baseline]"));
        header.setOpaque(false);
        header.add(DashboardUi.title("Servis akışı"));
        header.add(lblPeriod);
        left.add(header);
        summary.setOpaque(false);
        left.add(summary, "wmin 0");

        chart.setValueFormat(v -> String.valueOf(Math.round(v)));
        chart.setAxisFormat(v -> v == Math.rint(v) ? String.valueOf((long) v) : "");
        chart.setEmptyText("Bu dönemde servis hareketi yok");
        chart.setOnClick(this::openBucket, "Tıkla: bu aralıkta açılan servisler");
        left.add(chart, "h 190:210:240, wmin 0");
        card.add(left, "wmin 0");

        JSeparator divider = new JSeparator(SwingConstants.VERTICAL);
        card.add(divider, "growy");

        JPanel right = new JPanel(new MigLayout("insets 0, fillx, wrap", "[fill, grow]", "[]8[]"));
        right.setOpaque(false);
        JPanel rankHeader = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[]push[]", "[center]"));
        rankHeader.setOpaque(false);
        rankHeader.add(DashboardUi.title("En çok gelen"));
        rankTabs.addView(VIEW_TYPES, "Tür");
        rankTabs.addView(VIEW_BRANDS, "Marka");
        rankTabs.setOnChange(key -> renderRanks());
        rankHeader.add(rankTabs);
        right.add(rankHeader);
        rankRows.setOpaque(false);
        right.add(rankRows);
        card.add(right, "wmin 0");

        add(card);
        setLoading();
    }

    /** Dönem değişince eski sayılar kalmasın. */
    public void setLoading() {
        chart.setLoading();
        summary.removeAll();
        summary.add(DashboardUi.small("Yükleniyor…"));
        summary.revalidate();
    }

    /**
     * @param periodText kartın başlığındaki soluk dönem adı ("son 1 ay")
     */
    public void setFlow(String periodText, Bucket bucket, List<String> keys, LocalDate from, LocalDate to,
                        List<FlowPoint> points, ServiceStats stats) {
        this.bucket = bucket;
        this.keys = keys;
        this.from = from;
        this.to = to;
        lblPeriod.setText(periodText);

        Map<String, FlowPoint> byKey = new HashMap<>();
        for (FlowPoint p : points) byKey.put(p.key(), p);
        int n = keys.size();
        double[] opened = new double[n];
        double[] delivered = new double[n];
        List<String> axis = new ArrayList<>(n);
        List<String> tips = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            FlowPoint p = byKey.get(keys.get(i));
            if (p != null) {
                opened[i] = p.opened();
                delivered[i] = p.delivered();
            }
            axis.add(bucket.shortLabel(keys.get(i), AppLocale.uiLocale()));
            tips.add(bucket.longLabel(keys.get(i), AppLocale.uiLocale()));
        }
        chart.setData(axis, tips, List.of(
                new ColumnChart.Series("Açılan", opened, ColumnChart.Ink.neutral(0.45f)),
                new ColumnChart.Series("Teslim edilen", delivered, ColumnChart.Ink.key("Servicio.successColor"))), null, false);

        summary.removeAll();
        addPart(stats.opened() + " açıldı", "font: bold", stats.opened() == 0);
        addDot();
        addPart(stats.delivered() + " teslim edildi", "font: bold; foreground: $Servicio.successColor", stats.delivered() == 0);
        if (stats.delivered() > 0) {
            addDot();
            addPart("ort. " + tr.cabro.servicio.reports.Report.days(stats.avgDays()) + " içinde teslim", null, false);
        }
        if (stats.returned() > 0) {
            addDot();
            addPart(stats.returned() + " iade", "font: bold; foreground: $Servicio.dangerColor", false);
        }
        summary.revalidate();
        summary.repaint();
    }

    public void setRanks(List<Breakdown> types, List<Breakdown> brands) {
        this.types = types;
        this.brands = brands;
        renderRanks();
    }

    private void addPart(String text, String style, boolean zero) {
        JLabel l = new JLabel(text);
        String s = zero ? "foreground: $Label.disabledForeground" : style;
        if (s != null) l.putClientProperty(FlatClientProperties.STYLE, s);
        else l.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        summary.add(l);
    }

    private void addDot() {
        summary.add(DashboardUi.muted("  ·  "));
    }

    private void renderRanks() {
        boolean byType = !VIEW_BRANDS.equals(rankTabs.getSelected());
        List<Breakdown> data = byType ? types : brands;
        rankRows.removeAll();
        if (data.isEmpty()) {
            rankRows.add(DashboardUi.emptyState(byType ? "Cihaz gelmedi" : "Marka yok", "Bu dönemde servis kaydı açılmadı"));
        } else {
            long max = 1;
            for (Breakdown b : data) max = Math.max(max, b.count());
            long total = 0;
            for (Breakdown b : data) total += b.count();
            int shown = Math.min(RANK_ROWS, data.size());
            for (int i = 0; i < shown; i++) {
                Breakdown b = data.get(i);
                rankRows.add(rankRow(b, max, total));
            }
            if (data.size() > shown) {
                long rest = 0;
                for (int i = shown; i < data.size(); i++) rest += data.get(i).count();
                JLabel more = DashboardUi.small("+" + (data.size() - shown) + " diğer · " + rest + " servis");
                rankRows.add(more, "gapleft 10, gaptop 2");
            }
        }
        rankRows.revalidate();
        rankRows.repaint();
    }

    private JButton rankRow(Breakdown b, long max, long total) {
        JButton row = DashboardUi.rowButton();
        row.putClientProperty(FlatClientProperties.STYLE,
                "background: null; arc: 10; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 5,10,6,10");
        row.setLayout(new MigLayout("insets 0, fillx, gap 8 4", "[grow, fill][right]", "[][]"));
        JLabel name = new JLabel(b.name());
        name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        row.add(name, "wmin 0");
        JLabel count = new JLabel(String.valueOf(b.count()));
        count.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        row.add(count, "wrap");
        row.add(new Meter(b.count() / (double) max), "span 2, growx, h 5!");
        int pct = (int) Math.round(b.count() * 100.0 / Math.max(1, total));
        row.setToolTipText(b.name() + " — " + b.count() + " servis (%" + pct + "). Tıkla: listeyi aç");
        row.getAccessibleContext().setAccessibleName(b.name() + ", " + b.count() + " servis");
        row.addActionListener(e -> {
            FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
            FormManager.showForm(form);
            form.showCreatedBetween(b.name(), from, to);
        });
        return row;
    }

    private void openBucket(int index) {
        if (index < 0 || index >= keys.size()) return;
        String key = keys.get(index);
        LocalDate start = bucket.startOf(key);
        LocalDate end = bucket.endOf(key);
        // Kova dönem sınırını aşabilir (haftanın başı dönemden önce olabilir); aralık içinde kalsın.
        if (start.isBefore(from)) start = from;
        if (end.isAfter(to)) end = to;
        FormWorkOrders form = (FormWorkOrders) AllForms.getForm(FormWorkOrders.class);
        FormManager.showForm(form);
        form.showCreatedBetween(null, start, end);
    }

    /** İnce yatay oran çubuğu: iz çizgi renginde, dolgu nötr mürekkeple. */
    static final class Meter extends JComponent {
        private final double ratio;

        Meter(double ratio) {
            this.ratio = Math.max(0, Math.min(1, ratio));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UIScale.scale(60), UIScale.scale(5));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            float arc = h;
            Color track = UIManager.getColor("Component.borderColor");
            g2.setColor(track != null ? track : Color.LIGHT_GRAY);
            g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), h, arc, arc));
            g2.setColor(ColumnChart.Ink.neutral(0.55f).get());
            float w = (float) Math.max(h, getWidth() * ratio);
            g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            g2.dispose();
        }
    }
}
