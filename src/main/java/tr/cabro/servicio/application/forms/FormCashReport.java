package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.model.dto.DailyCashReportDto;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.service.SaleService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Günlük kasa raporu — {@code payments} tablosundan yöntem bazında net toplam (servis tahsilatı +
 * POS satışı + iade hepsi burada birleşir, iade tutarları negatif olduğu için netleşir).
 */
@SystemForm(name = "Kasa Raporu", description = "Günlük nakit/kart/havale kırılımı")
public class FormCashReport extends Form {

    private final SaleService saleService;
    private DatePicker datePicker;
    private JFormattedTextField dateField;
    private GenericTableModel<Map.Entry<PaymentType, BigDecimal>> tableModel;
    private JLabel lblTotal, lblSaleCount, lblReturnCount;

    public FormCashReport() {
        this.saleService = ServiceManager.getSaleService();
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 15", "[grow]", "[pref][pref][grow][pref!]"));

        JLabel title = new JLabel("Kasa Raporu");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold $h1.font");
        add(title, "wrap");

        add(buildDateRow(), "growx, wrap");
        add(buildTable(), "grow, wrap");
        add(buildSummaryBox(), "growx");

        refresh();
    }

    private JPanel buildDateRow() {
        JPanel panel = new JPanel(new MigLayout("insets 0, fillx", "[][100!][][][grow]", "[]"));

        panel.add(new JLabel("Tarih:"));
        dateField = new JFormattedTextField();
        datePicker = new DatePicker();
        datePicker.setDateFormat("dd/MM/yyyy");
        datePicker.setEditor(dateField);
        datePicker.setSelectedDate(LocalDate.now());
        panel.add(dateField, "growx");

        JButton btnToday = new JButton("Bugün");
        btnToday.addActionListener(e -> { datePicker.setSelectedDate(LocalDate.now()); refresh(); });
        panel.add(btnToday);

        JButton btnRefresh = new JButton(new Ikon("icons/refresh-cw.svg", 1f));
        btnRefresh.setToolTipText("Yenile");
        btnRefresh.addActionListener(e -> refresh());
        panel.add(btnRefresh);

        return panel;
    }

    private JScrollPane buildTable() {
        List<ColumnDef<Map.Entry<PaymentType, BigDecimal>>> columns = Arrays.asList(
                new ColumnDef<>("Yöntem", String.class, e -> e.getKey() != null ? e.getKey().getDisplayName() : "-"),
                new ColumnDef<>("Tutar", BigDecimal.class, Map.Entry::getValue)
        );
        tableModel = new GenericTableModel<>(columns);
        JTable table = new JTable(tableModel);
        table.getColumnModel().getColumn(1).setCellRenderer(new tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.putClientProperty(FlatClientProperties.STYLE, "arc: 12;");
        return scrollPane;
    }

    private JPanel buildSummaryBox() {
        JPanel box = new JPanel(new MigLayout("fillx, insets 15", "[grow][pref!]", "[]5[]"));
        box.putClientProperty(FlatClientProperties.STYLE, "background: darken($Panel.background, 2%); arc: 15;");

        box.add(new JLabel("Satış / İade Adedi:"));
        JPanel counts = new JPanel(new MigLayout("insets 0, gap 10", "[][]", "[]"));
        counts.setOpaque(false);
        lblSaleCount = new JLabel("0");
        lblReturnCount = new JLabel("0");
        counts.add(lblSaleCount);
        counts.add(new JLabel("/"));
        counts.add(lblReturnCount);
        box.add(counts, "align right, wrap");

        JLabel totalLabel = new JLabel("Net Kasa Toplamı:");
        totalLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        box.add(totalLabel);
        lblTotal = new JLabel("0,00 ₺");
        lblTotal.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        box.add(lblTotal, "align right");

        return box;
    }

    private void refresh() {
        LocalDate date = datePicker.getSelectedDate() != null ? datePicker.getSelectedDate() : LocalDate.now();

        saleService.getDailyCashReport(date).thenAccept(report -> SwingUtilities.invokeLater(() -> {
            List<Map.Entry<PaymentType, BigDecimal>> rows = new ArrayList<>(report.getBreakdown().entrySet());
            tableModel.setData(rows);
            lblTotal.setText(Format.formatPrice(report.getTotal()));
            lblSaleCount.setText(String.valueOf(report.getSaleCount()));
            lblReturnCount.setText(String.valueOf(report.getReturnCount()));
        })).exceptionally(ex -> ErrorHandler.handle(this, "Kasa raporu yüklenemedi", ex));
    }

    @Override
    public void formOpen() {
        refresh();
    }
}
