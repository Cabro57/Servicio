package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.time.Month;
import org.jfree.data.time.TimeTableXYDataset;
import raven.modal.Toast;
import raven.modal.component.ToolBarSelection;
import raven.modal.component.chart.PieChart;
import raven.modal.component.chart.TimeSeriesChart;
import raven.modal.component.chart.themes.DefaultChartTheme;
import raven.modal.component.chart.utils.ToolBarTimeSeriesChartRenderer;
import raven.modal.component.dashboard.CardBox;
import raven.modal.system.Form;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.ActiveServiceTable;
import tr.cabro.servicio.application.panels.PendingPaymentsTable;
import tr.cabro.servicio.model.dto.ChartDataDto;
import tr.cabro.servicio.model.dto.SummaryCardDto;
import tr.cabro.servicio.model.enums.TimeFilter;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.service.ReportManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SystemForm(name = "Ana Sayfa", description = "Gösterge paneli finansal ayrıntıları ve dağılımları görüntüler")
public class FormDashboard extends Form {

    // --- 1. ZAMAN FİLTRESİ İÇİN ENUM ---
    // Tarih hesaplama mantığı (switch-case yerine) doğrudan enum içinde yapıldı


    private JPanel panelLayout;
    private CardBox cardBox;
    private TimeSeriesChart timeSeriesChart;
    private PieChart deviceTypePieChart;
    private PieChart brandPieChart;
    private ActiveServiceTable activeServiceTable;
    private PendingPaymentsTable pendingPaymentsTable;

    // Varsayılan zaman filtresi (1 Ay)
    private TimeFilter selectedTimeFilter = TimeFilter.ALL_TIME;

    public FormDashboard() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("wrap,fill", "[fill]", "[grow 0][fill]"));
        createTitle();
        createPanelLayout();
        createCard();
        createChart();
        createPieCharts();
        createOtherTable();
    }

    @Override
    public void formInit() {
        loadData();
    }

    @Override
    public void formRefresh() {
        loadData();
    }

    private void loadData() {
        ReportManager reportManager = ServiceManager.getReportManager();
        WorkOrderService workOrderService = ServiceManager.getWorkOrderService();

        // Tarihleri Enum'dan doğrudan alıyoruz (Switch-case'e gerek kalmadı)
        LocalDate[] dates = selectedTimeFilter.getRanges();
        String startCurrent = dates[0].toString();
        String endCurrent = dates[1].toString();
        String startPrev = dates[2].toString();
        String endPrev = dates[3].toString();

        // ÖZET KARTLARI
        CompletableFuture<SummaryCardDto> currentFuture = reportManager.getDashboardSummaryCards(startCurrent, endCurrent);
        CompletableFuture<SummaryCardDto> prevFuture = reportManager.getDashboardSummaryCards(startPrev, endPrev);

        CompletableFuture.allOf(currentFuture, prevFuture).thenAccept(v -> {
            SummaryCardDto current = currentFuture.join();
            SummaryCardDto prev = prevFuture.join();

            SwingUtilities.invokeLater(() -> {
                cardBox.setValueAt(0,
                        String.format("%,d", current.getTotalRecords()),
                        String.format("Önceki döneme göre: %,d", prev.getTotalRecords()),
                        String.format("%.1f%%", calcChange(current.getTotalRecords(), prev.getTotalRecords())),
                        calcChange(current.getTotalRecords(), prev.getTotalRecords()) >= 0);

                cardBox.setValueAt(1,
                        String.format("₺%,.2f", current.getTotalRevenue()),
                        String.format("Önceki dönem: ₺%,.2f", prev.getTotalRevenue()),
                        String.format("%.1f%%", calcChange(current.getTotalRevenue().doubleValue(), prev.getTotalRevenue().doubleValue())),
                        calcChange(current.getTotalRevenue().doubleValue(), prev.getTotalRevenue().doubleValue()) >= 0);

                cardBox.setValueAt(2,
                        String.format("₺%,.2f", current.getTotalExpense()),
                        String.format("Önceki dönem: ₺%,.2f", prev.getTotalExpense()),
                        String.format("%.1f%%", calcChange(current.getTotalExpense().doubleValue(), prev.getTotalExpense().doubleValue())),
                        calcChange(current.getTotalExpense().doubleValue(), prev.getTotalExpense().doubleValue()) <= 0);

                cardBox.setValueAt(3,
                        String.format("₺%,.2f", current.getTotalProfit()),
                        String.format("Önceki dönem: ₺%,.2f", prev.getTotalProfit()),
                        String.format("%.1f%%", calcChange(current.getTotalProfit().doubleValue(), prev.getTotalProfit().doubleValue())),
                        calcChange(current.getTotalProfit().doubleValue(), prev.getTotalProfit().doubleValue()) >= 0);
            });
        });

        // ÇİZGİ GRAFİK (BORSA)
        CompletableFuture<List<ChartDataDto>> revFuture = reportManager.getMonthlyRevenueTrend(startCurrent, endCurrent);
        CompletableFuture<List<ChartDataDto>> profFuture = reportManager.getMonthlyProfitTrend(startCurrent, endCurrent);

        CompletableFuture.allOf(revFuture, profFuture).thenAccept(v -> {
            List<ChartDataDto> revData = revFuture.join();
            List<ChartDataDto> profData = profFuture.join();

            SwingUtilities.invokeLater(() -> {
                TimeTableXYDataset dataset = new TimeTableXYDataset();
                for (ChartDataDto dto : revData) {
                    if (dto.getLabel() == null) continue;
                    String[] parts = dto.getLabel().split("-");
                    dataset.add(new Month(Integer.parseInt(parts[1]), Integer.parseInt(parts[0])), dto.getValue().doubleValue(), "Brüt Gelir");
                }
                for (ChartDataDto dto : profData) {
                    if (dto.getLabel() == null) continue;
                    String[] parts = dto.getLabel().split("-");
                    dataset.add(new Month(Integer.parseInt(parts[1]), Integer.parseInt(parts[0])), dto.getValue().doubleValue(), "Net Kâr");
                }
                timeSeriesChart.setDataset(dataset);
            });
        });

        // PASTA GRAFİKLER (Küçük dilimler %3'ün altındaysa "Diğer" grubuna alınır)
        reportManager.getDeviceTypePieChart(startCurrent, endCurrent).thenAccept(data ->
                SwingUtilities.invokeLater(() -> deviceTypePieChart.setDataset(createGroupedPieDataset(data, 3.0)))
        ).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.WARNING, ex.getMessage());
            });
            Servicio.getLogger().error("Hata", ex);
            return null;
        });

        reportManager.getBrandPieChart(startCurrent, endCurrent).thenAccept(data ->
                SwingUtilities.invokeLater(() -> brandPieChart.setDataset(createGroupedPieDataset(data, 3.0)))
        ).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.WARNING, ex.getMessage());
            });
            Servicio.getLogger().error("Hata", ex);
            return null;
        });;

        // ALT TABLOLAR
        workOrderService.getAll("OPEN")
                .thenAccept(services -> {
                    SwingUtilities.invokeLater(() -> activeServiceTable.setData(services));
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.WARNING, ex.getMessage());
                    });
                    Servicio.getLogger().error("HATA:", ex);
                    return null;
                });
        workOrderService.getServicesWithDebt().thenAccept(services -> SwingUtilities.invokeLater(() -> pendingPaymentsTable.setData(services)));
    }

    /**
     * Pasta Grafik Veri Temizleme Algoritması
     * Belirtilen yüzdeden (Örn: %3) daha küçük olan dilimleri toplayıp "Diğer" olarak tek dilim yapar.
     */
    private DefaultPieDataset createGroupedPieDataset(List<ChartDataDto> data, double thresholdPercent) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        if (data == null || data.isEmpty()) return dataset;

        double total = data.stream().mapToDouble(d -> d.getValue().doubleValue()).sum();
        if (total == 0) return dataset;

        double otherSum = 0;

        for (ChartDataDto dto : data) {
            double value = dto.getValue().doubleValue();
            double percent = (value / total) * 100.0;

            if (percent < thresholdPercent) {
                otherSum += value; // Diğer sepetine at
            } else {
                dataset.setValue(dto.getLabel() != null ? dto.getLabel() : "Bilinmeyen", value);
            }
        }

        // Eğer diğerleri sepetinde veri varsa tek bir dilim olarak ekle
        if (otherSum > 0) {
            dataset.setValue("Diğer", otherSum);
        }

        return dataset;
    }

    private double calcChange(double current, double previous) {
        if (previous == 0) return current > 0 ? 100.0 : 0.0;
        return ((current - previous) / previous) * 100.0;
    }

    private void createTitle() {
        JPanel panel = new JPanel(new MigLayout("fillx", "[]push[][]"));
        JLabel title = new JLabel("Ana Sayfa");
        title.putClientProperty(FlatClientProperties.STYLE, "font:bold +3");

        // Zaman Filtresi Başlığa Alındı
        ToolBarSelection<TimeFilter> timeFilterSelection = new ToolBarSelection<>(
                TimeFilter.values(),
                selected -> {
                    selectedTimeFilter = selected;
                    loadData(); // Filtre değişince sayfayı yenile
                }
        );

        //timeFilterSelection.setSelectedIndex(3); // Varsayılan: MONTH_1 (1A)

        panel.add(title);
        panel.add(timeFilterSelection); // Temaların yerine Zaman Filtresi geldi
        add(panel);
    }

    private void createPanelLayout() {
        panelLayout = new JPanel(new DashboardLayout());
        JScrollPane scrollPane = new JScrollPane(panelLayout);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "" +
                "width:5; trackArc:$ScrollBar.thumbArc; trackInsets:0,0,0,0; thumbInsets:0,0,0,0;");
        add(scrollPane);
    }

    private void createCard() {
        JPanel panel = new JPanel(new MigLayout("fillx", "[fill]"));
        cardBox = new CardBox();
        cardBox.addCardItem(createIcon("icons/wrench.svg", DefaultChartTheme.getColor(0)), "Toplam Servis");
        cardBox.addCardItem(createIcon("icons/banknote-arrow-up.svg", DefaultChartTheme.getColor(1)), "Toplam Gelir (Ciro)");
        cardBox.addCardItem(createIcon("icons/banknote-arrow-down.svg", DefaultChartTheme.getColor(2)), "Toplam Parça Gideri");
        cardBox.addCardItem(createIcon("icons/turkish-lira.svg", DefaultChartTheme.getColor(3)), "Toplam Net Kâr");
        panel.add(cardBox);
        panelLayout.add(panel);
    }

    private void createChart() {
        JPanel panel = new JPanel(new MigLayout("gap 14,wrap,fillx", "[fill]", "[350]"));
        timeSeriesChart = new TimeSeriesChart();
        timeSeriesChart.add(new ToolBarTimeSeriesChartRenderer(timeSeriesChart), "al trailing,grow 0", 0);
        panel.add(timeSeriesChart);
        panelLayout.add(panel);
    }

    private void createPieCharts() {
        JPanel panel = new JPanel(new MigLayout("fillx,gap 14", "[fill, 50%][fill, 50%]", "[300]"));

        deviceTypePieChart = new PieChart();
        brandPieChart = new PieChart();

        // İsteğe bağlı: Grafiğin içine renk temasını uyguluyoruz
        DefaultChartTheme.applyTheme(deviceTypePieChart.getFreeChart());
        DefaultChartTheme.applyTheme(brandPieChart.getFreeChart());

        panel.add(deviceTypePieChart);
        panel.add(brandPieChart);
        panelLayout.add(panel);
    }

    // TODO: tek bir panel yap 2 tabblonun arasına ayırıcı koy
    private void createOtherTable() {
        JPanel panel = new JPanel(new MigLayout("fillx,gap 14", "[fill, 50%][fill, 50%]", "[300]"));
        activeServiceTable = new ActiveServiceTable();
        pendingPaymentsTable = new PendingPaymentsTable();
        panel.add(activeServiceTable, "sgx 1, aligny top");
        panel.add(pendingPaymentsTable, "sgx 1, aligny top");
        panelLayout.add(panel);
    }

    private Icon createIcon(String icon, Color color) {
        return new FlatSVGIcon(icon, 1f).setColorFilter(new FlatSVGIcon.ColorFilter(color1 -> color));
    }

    private class DashboardLayout implements LayoutManager {
        private int gap = 0;
        @Override public void addLayoutComponent(String name, Component comp) {}
        @Override public void removeLayoutComponent(Component comp) {}

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int width = (insets.left + insets.right);
                int height = insets.top + insets.bottom;
                int g = UIScale.scale(gap);
                int count = parent.getComponentCount();
                for (int i = 0; i < count; i++) {
                    height += parent.getComponent(i).getPreferredSize().height;
                }
                if (count > 1) height += (count - 1) * g;
                return new Dimension(width, height);
            }
        }

        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(10, 10); }

        @Override
        public void layoutContainer(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int x = insets.left;
                int y = insets.top;
                int width = parent.getWidth() - (insets.left + insets.right);
                int g = UIScale.scale(gap);
                for (int i = 0; i < parent.getComponentCount(); i++) {
                    Component com = parent.getComponent(i);
                    Dimension size = com.getPreferredSize();
                    com.setBounds(x, y, width, size.height);
                    y += size.height + g;
                }
            }
        }
    }
}