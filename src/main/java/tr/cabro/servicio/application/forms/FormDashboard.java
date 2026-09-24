package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.QuickActionButton;
import tr.cabro.servicio.application.panels.dashboard.ActiveServicesPanel;
import tr.cabro.servicio.application.panels.dashboard.AttentionPanel;
import tr.cabro.servicio.application.panels.dashboard.CashTodayPanel;
import tr.cabro.servicio.application.panels.dashboard.DistributionPanel;
import tr.cabro.servicio.application.panels.dashboard.MovementsPanel;
import tr.cabro.servicio.application.panels.dashboard.PeriodPanel;
import tr.cabro.servicio.application.panels.dashboard.PipelinePanel;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.dto.ChartDataDto;
import tr.cabro.servicio.model.dto.SummaryCardDto;
import tr.cabro.servicio.model.enums.TimeFilter;
import tr.cabro.servicio.service.ReportManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ana sayfa: tezgâhın operasyon panosu. Sol 2/3 bugünün işi (servis hattı, dikkat bekleyenler,
 * aktif servisler, cihaz dağılımı), sağ 1/3 para (bugünkü kasa, bugünkü hareketler, dönem kazancı).
 * Üstte sık işlemlerin kısayol şeridi; aynı işlemler Alt+harf ve Ctrl+K komut paletinden de çalışır.
 */
@SystemForm(name = "Ana Sayfa", description = "Bugünkü kasa, atölye durumu, bekleyen işler ve dönem kazancı",
        tags = {"dashboard", "gösterge", "kasa", "kazanç", "özet"})
public class FormDashboard extends Form {

    private static final int MOVEMENT_ROWS = 8;

    private TimeFilter selectedTimeFilter = TimeFilter.MONTH_1;
    private boolean loadedOnce;

    private JLabel lblDate;
    private PipelinePanel pipelinePanel;
    private AttentionPanel attentionPanel;
    private ActiveServicesPanel activeServiceTable;
    private DistributionPanel distributionPanel;
    private CashTodayPanel cashTodayPanel;
    private MovementsPanel movementsPanel;
    private PeriodPanel periodPanel;

    public FormDashboard() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("wrap, fill, insets 0, gap 0", "[fill]", "[grow 0][fill, grow]"));
        add(createHeader());
        add(createContent());
    }

    @Override
    public void formInit() {
        loadData();
    }

    @Override
    public void formOpen() {
        // İlk gösterimde formInit zaten yükleyecek; sonraki dönüşlerde (ör. tahsilattan sonra) tazele.
        if (loadedOnce) loadData();
    }

    @Override
    public void formRefresh() {
        loadData();
    }

    // ── Yerleşim ────────────────────────────────────────────────────────────

    private JComponent createHeader() {
        JPanel panel = new JPanel(new MigLayout("insets 14 18 10 18, fillx, gap 8", "[]push[][][][][]", "[center]"));

        JPanel titleBox = new JPanel(new MigLayout("insets 0, wrap, gap 0", "[]", "[][]"));
        titleBox.setOpaque(false);
        JLabel title = new JLabel("Ana Sayfa");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +5");
        lblDate = new JLabel();
        lblDate.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        titleBox.add(title);
        titleBox.add(lblDate);
        panel.add(titleBox);

        QuickActionButton[] buttons = {
                new QuickActionButton(QuickAction.NEW_SERVICE, true),
                new QuickActionButton(QuickAction.QUICK_SALE, false),
                new QuickActionButton(QuickAction.COLLECT, false),
                new QuickActionButton(QuickAction.NEW_CUSTOMER, false),
                new QuickActionButton(QuickAction.NEW_PART, false)
        };
        for (QuickActionButton b : buttons) panel.add(b);

        // Şerit sığmıyorsa (1366 px ekran + açık menü) önce kısayol etiketleri gizlenir.
        panel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                boolean compact = panel.getWidth() < UIScale.scale(1180);
                for (QuickActionButton b : buttons) b.setCompact(compact);
            }
        });
        return panel;
    }

    private JComponent createContent() {
        ContentPanel content = new ContentPanel(new MigLayout("insets 4 18 18 18, fillx, gap 14",
                "[fill, grow][fill, 330:32%:440]", "[top]"));

        JPanel left = column();
        pipelinePanel = new PipelinePanel();
        attentionPanel = new AttentionPanel(() -> {
            loadData();
            FormManager.refreshStatusBar();
        });
        activeServiceTable = new ActiveServicesPanel(ServiceManager.getWorkOrderService());
        distributionPanel = new DistributionPanel();
        left.add(pipelinePanel);
        left.add(attentionPanel);
        left.add(activeServiceTable);
        left.add(distributionPanel);

        JPanel right = column();
        cashTodayPanel = new CashTodayPanel();
        movementsPanel = new MovementsPanel();
        periodPanel = new PeriodPanel(selectedTimeFilter, filter -> {
            selectedTimeFilter = filter;
            loadPeriod();
        });
        right.add(cashTodayPanel);
        right.add(movementsPanel);
        right.add(periodPanel);

        content.add(left, "wmin 0");
        content.add(right);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE,
                "width: 5; trackArc: $ScrollBar.thumbArc; trackInsets: 0,0,0,0; thumbInsets: 0,0,0,0");
        return scroll;
    }

    private static JPanel column() {
        JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 14", "[fill, grow]", ""));
        panel.setOpaque(false);
        return panel;
    }

    // ── Veri ────────────────────────────────────────────────────────────────

    private void loadData() {
        // ActiveServicesPanel ilk sayfasını kurucuda kendisi yükler; tekrar yükleme yalnızca sonraki yenilemelerde.
        if (loadedOnce) activeServiceTable.loadPage(1);
        loadedOnce = true;
        lblDate.setText(LocalDate.now().format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(AppLocale.uiLocale())));
        loadToday();
        attentionPanel.load();
        loadPeriod();
    }

    /** Seçili dönemden bağımsız, her zaman bugünü gösteren bölümler. */
    private void loadToday() {
        LocalDate today = LocalDate.now();

        ServiceManager.getWorkOrderService().getOpenStatusCounts()
                .thenAccept(counts -> SwingUtilities.invokeLater(() -> pipelinePanel.setCounts(counts)))
                .exceptionally(ex -> ErrorHandler.handle(this, "Servis hattı yüklenemedi", ex));
        ServiceManager.getWorkOrderService().countCreatedOn(today)
                .thenAccept(n -> SwingUtilities.invokeLater(() -> pipelinePanel.setTodayCount(n)))
                .exceptionally(ex -> ErrorHandler.handle(this, "Bugün açılan servisler yüklenemedi", ex));

        ServiceManager.getSaleService().getDailyCashReport(today)
                .thenAccept(report -> SwingUtilities.invokeLater(() -> cashTodayPanel.setReport(report)))
                .exceptionally(ex -> ErrorHandler.handle(this, "Günlük kasa özeti yüklenemedi", ex));
        ServiceManager.getPaymentService().getTotalReceivables()
                .thenAccept(total -> SwingUtilities.invokeLater(() -> cashTodayPanel.setReceivables(total)))
                .exceptionally(ex -> ErrorHandler.handle(this, "Açık alacak yüklenemedi", ex));

        CompletableFuture<List<Payment>> paymentsF = ServiceManager.getPaymentService().getPaymentsOn(today, MOVEMENT_ROWS);
        paymentsF.thenCompose(payments -> {
            List<Long> ids = payments.stream().map(Payment::getCustomerId).filter(Objects::nonNull)
                    .distinct().collect(Collectors.toList());
            CompletableFuture<Map<Long, Customer>> customersF = ids.isEmpty()
                    ? CompletableFuture.completedFuture(Collections.emptyMap())
                    : ServiceManager.getCustomerService().getAll(ids).thenApply(cs -> cs.stream()
                    .collect(Collectors.toMap(Customer::getId, Function.identity(), (a, b) -> a)));
            return customersF.thenAccept(customers ->
                    SwingUtilities.invokeLater(() -> movementsPanel.setPayments(payments, customers)));
        }).exceptionally(ex -> ErrorHandler.handle(this, "Bugünkü hareketler yüklenemedi", ex));
    }

    /** Dönem filtresine bağlı bölümler: kazanç özeti, trend grafiği, cihaz dağılımı. */
    private void loadPeriod() {
        ReportManager reportManager = ServiceManager.getReportManager();
        TimeFilter filter = selectedTimeFilter;
        LocalDate[] dates = filter.getRanges();
        String start = dates[0].toString();
        String end = dates[1].toString();

        CompletableFuture<SummaryCardDto> currentF = reportManager.getDashboardSummaryCards(start, end);
        CompletableFuture<SummaryCardDto> prevF = reportManager.getDashboardSummaryCards(dates[2].toString(), dates[3].toString());
        // Dönemler arasında hızlı geçişte geç gelen eski sonuçlar atlanır.
        CompletableFuture.allOf(currentF, prevF).thenRun(() -> SwingUtilities.invokeLater(() -> {
                    if (filter != selectedTimeFilter) return;
                    periodPanel.setSummary(currentF.join(), prevF.join(), filter != TimeFilter.ALL_TIME);
                }))
                .exceptionally(ex -> ErrorHandler.handle(this, "Dönem özeti yüklenemedi", ex));

        boolean useEffective = filter == TimeFilter.ALL_TIME;
        CompletableFuture<List<ChartDataDto>> revF = reportManager.getRevenueTrend(filter.getSqlFormat(), start, end, useEffective);
        CompletableFuture<List<ChartDataDto>> profF = reportManager.getProfitTrend(filter.getSqlFormat(), start, end, useEffective);
        CompletableFuture.allOf(revF, profF).thenRun(() -> SwingUtilities.invokeLater(() -> {
                    if (filter != selectedTimeFilter) return;
                    periodPanel.setTrend(revF.join(), profF.join(), filter.getGranularity());
                }))
                .exceptionally(ex -> ErrorHandler.handle(this, "Kazanç grafiği yüklenemedi", ex));

        reportManager.getDeviceTypePieChart(start, end)
                .thenAccept(data -> SwingUtilities.invokeLater(() -> {
                    if (filter == selectedTimeFilter) distributionPanel.setDeviceTypes(data);
                }))
                .exceptionally(ex -> ErrorHandler.handle(this, "Cihaz türü grafiği yüklenemedi", ex));
        reportManager.getBrandPieChart(start, end)
                .thenAccept(data -> SwingUtilities.invokeLater(() -> {
                    if (filter == selectedTimeFilter) distributionPanel.setBrands(data);
                }))
                .exceptionally(ex -> ErrorHandler.handle(this, "Marka grafiği yüklenemedi", ex));
    }

    /** Kaydırma alanında genişliği görünüm alanına eşitleyen içerik paneli (yatay kaydırma olmasın). */
    private static class ContentPanel extends JPanel implements Scrollable {
        ContentPanel(LayoutManager layout) {
            super(layout);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 32; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
