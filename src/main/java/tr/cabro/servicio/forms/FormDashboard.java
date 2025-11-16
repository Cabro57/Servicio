package tr.cabro.servicio.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import org.jfree.data.time.Month;
import org.jfree.data.time.TimeTableXYDataset;
import raven.modal.component.ToolBarSelection;
import raven.modal.component.chart.*;
import raven.modal.component.chart.themes.ColorThemes;
import raven.modal.component.chart.themes.DefaultChartTheme;
import raven.modal.component.chart.utils.ToolBarTimeSeriesChartRenderer;
import raven.modal.component.dashboard.CardBox;
import raven.modal.system.Form;
import raven.modal.utils.SystemForm;
import raven.swingpack.JPagination;
import tr.cabro.servicio.application.tablemodal.ServiceListTableModel;
import tr.cabro.servicio.component.PaginationTable;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.reports.ServiceFinanceRecord;
import tr.cabro.servicio.reports.ServiceFinanceReport;
import tr.cabro.servicio.service.RepairService;

import javax.swing.*;
import java.awt.*;

@SystemForm(name = "Ana Sayfa", description = "gösterge paneli formu bazı ayrıntıları görüntüler")
public class FormDashboard extends Form {

    private final int LIMIT = 10;

    public FormDashboard() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("wrap,fill", "[fill]", "[grow 0][fill]"));
        createTitle();
        createPanelLayout();
        createCard();
        createChart();
        createServiceTable();
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
        // load data card
        RepairService repairService = new RepairService();
        ServiceFinanceReport report = repairService.getDashboardStats();

        // Son ay
        ServiceFinanceRecord current = report.getLatestMonth();
        if (current == null) return;

        cardBox.setValueAt(0,
                String.format("%,d", report.getTotalServiceCount()),
                String.format("Bu ay %,d servis açıldı", current.getServiceCount()),
                String.format("%.1f%%", current.getServiceChangeRate()),
                current.getServiceChangeRate() >= 0);

        cardBox.setValueAt(1,
                String.format("₺%,.2f", report.getTotalRevenue()),
                String.format("Bu ay ₺%,.2f tahsil edildi", current.getTotalRevenue()),
                String.format("%.1f%%", current.getRevenueChangeRate()),
                current.getRevenueChangeRate() >= 0);

        cardBox.setValueAt(2,
                String.format("₺%,.2f", report.getTotalExpense()),
                String.format("Bu ay ₺%,.2f harcandı", current.getTotalExpense()),
                String.format("%.1f%%", current.getExpenseChangeRate()),
                current.getExpenseChangeRate() <= 0); // gider artışı olumsuzdur

        cardBox.setValueAt(3,
                String.format("₺%,.2f", report.getTotalProfit()),
                String.format("Bu ay ₺%,.2f kâr elde edildi", current.getTotalProfit()),
                String.format("%.1f%%", current.getProfitChangeRate()),
                current.getProfitChangeRate() >= 0);

        // load data chart
        // Grafik (gelir-gider serisi)
        TimeTableXYDataset dataset = new TimeTableXYDataset();
        String incomeSeries = "Gelir";
        String expenseSeries = "Gider";

        for (ServiceFinanceRecord rec : report.getMonthlyRows()) {
            if (rec.getMonth() == null || rec.getTotalRevenue() == null) continue;

            String[] parts = rec.getMonth().split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);

            dataset.add(new Month(month, year), rec.getTotalRevenue(), incomeSeries);
            dataset.add(new Month(month, year), rec.getTotalExpense(), expenseSeries);
        }

        timeSeriesChart.setDataset(dataset);

        paginationTable.setData(repairService.getAll("OPEN"));
        paginationTable.showData();
    }

    private void createTitle() {
        JPanel panel = new JPanel(new MigLayout("fillx", "[]push[][]"));
        JLabel title = new JLabel("Ana Sayfa");

        title.putClientProperty(FlatClientProperties.STYLE, "" +
                "font:bold +3");

        ToolBarSelection<ColorThemes> toolBarSelection = new ToolBarSelection<>(ColorThemes.values(), colorThemes -> {
            if (DefaultChartTheme.setChartColors(colorThemes)) {
                DefaultChartTheme.applyTheme(timeSeriesChart.getFreeChart());
                cardBox.setCardIconColor(0, DefaultChartTheme.getColor(0));
                cardBox.setCardIconColor(1, DefaultChartTheme.getColor(1));
                cardBox.setCardIconColor(2, DefaultChartTheme.getColor(2));
                cardBox.setCardIconColor(3, DefaultChartTheme.getColor(3));
            }
        });
        panel.add(title);
        panel.add(toolBarSelection);
        add(panel);
    }

    private void createPanelLayout() {
        panelLayout = new JPanel(new DashboardLayout());
        JScrollPane scrollPane = new JScrollPane(panelLayout);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(10);
        scrollPane.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "" +
                "width:5;" +
                "trackArc:$ScrollBar.thumbArc;" +
                "trackInsets:0,0,0,0;" +
                "thumbInsets:0,0,0,0;");
        add(scrollPane);
    }

    private void createCard() {
        JPanel panel = new JPanel(new MigLayout("fillx", "[fill]"));
        cardBox = new CardBox();
        cardBox.addCardItem(createIcon("icons/dashboard/customer.svg", DefaultChartTheme.getColor(0)), "Toplam Servis");
        cardBox.addCardItem(createIcon("icons/dashboard/income.svg", DefaultChartTheme.getColor(1)), "Toplam Gelir");
        cardBox.addCardItem(createIcon("icons/dashboard/expense.svg", DefaultChartTheme.getColor(2)), "Toplam Gider");
        cardBox.addCardItem(createIcon("icons/dashboard/profit.svg", DefaultChartTheme.getColor(3)), "Son Kar");
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

    private void createServiceTable() {
        JPanel panel = new JPanel(new MigLayout("gap 14,wrap,fillx", "[fill]"));

        paginationTable = new PaginationTable<>();

        panel.add(paginationTable);
        panelLayout.add(panel);
    }

    private Icon createIcon(String icon, Color color) {
        return new FlatSVGIcon(icon, 0.4f).setColorFilter(new FlatSVGIcon.ColorFilter(color1 -> color));
    }


    private JPanel panelLayout;
    private CardBox cardBox;

    private TimeSeriesChart timeSeriesChart;

    private PaginationTable<Service> paginationTable;

    private class DashboardLayout implements LayoutManager {

        private int gap = 0;

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int width = (insets.left + insets.right);
                int height = insets.top + insets.bottom;
                int g = UIScale.scale(gap);
                int count = parent.getComponentCount();
                for (int i = 0; i < count; i++) {
                    Component com = parent.getComponent(i);
                    Dimension size = com.getPreferredSize();
                    height += size.height;
                }
                if (count > 1) {
                    height += (count - 1) * g;
                }
                return new Dimension(width, height);
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                return new Dimension(10, 10);
            }
        }

        @Override
        public void layoutContainer(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int x = insets.left;
                int y = insets.top;
                int width = parent.getWidth() - (insets.left + insets.right);
                int g = UIScale.scale(gap);
                int count = parent.getComponentCount();
                for (int i = 0; i < count; i++) {
                    Component com = parent.getComponent(i);
                    Dimension size = com.getPreferredSize();
                    com.setBounds(x, y, width, size.height);
                    y += size.height + g;
                }
            }
        }
    }
}
