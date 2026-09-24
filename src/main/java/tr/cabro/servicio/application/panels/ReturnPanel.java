package tr.cabro.servicio.application.panels;

import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.table.DynamicActionColumnSupport;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Sale;
import tr.cabro.servicio.model.SaleItem;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.service.SaleService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * İade diyaloğu — orijinal satışın kalemleri listelenir, her satırın +/- ile iade adedi
 * seçilir (üst sınır: satılan miktar - daha önce iade edilmiş miktar). Kaydedildiğinde
 * {@link SaleService#recordReturn} çağrılır; ayrı bir "iade" tablosu yoktur, negatif tutarlı
 * yeni bir satış oluşur.
 */
public class ReturnPanel extends JPanel {

    private static class Row {
        final SaleItem item;
        final SaleService.ReturnQuote quote;
        final int maxReturnable;
        int quantity = 0;

        Row(SaleService.ReturnQuote quote) {
            this.item = quote.item;
            this.quote = quote;
            this.maxReturnable = quote.returnable;
        }

        BigDecimal amount() {
            return quote.amountFor(quantity);
        }
    }

    private final Sale sale;
    private final String modalId;
    private final Runnable onSuccess;
    private final SaleService saleService;

    private final List<Row> rows = new ArrayList<>();
    private GenericTableModel<Row> tableModel;

    private JLabel lblReturnTotal;
    private CurrencyField refundAmountField;
    private JComboBox<PaymentType> refundTypeCombo;
    private JButton btnSave;

    public static void open(Component parent, Sale sale, Runnable onSuccess) {
        String modalId = "Return-" + sale.getId();
        ReturnPanel panel = new ReturnPanel(sale, modalId, onSuccess);
        SimpleModalBorder.Option[] options = {new SimpleModalBorder.Option("Kapat", SimpleModalBorder.CANCEL_OPTION)};
        AppModal.showModal(parent, new SimpleModalBorder(panel, "İade — SAT-" + sale.getId(), options, (controller, action) -> {}), modalId);
    }

    public ReturnPanel(Sale sale, String modalId, Runnable onSuccess) {
        this.sale = sale;
        this.modalId = modalId;
        this.onSuccess = onSuccess;
        this.saleService = ServiceManager.getSaleService();
        init();
        loadItems();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 15, gap 10", "[grow]", "[grow][][pref!]"));
        setPreferredSize(new Dimension(560, 460));

        setupTable();
        JTable table = buildTable();
        add(new JScrollPane(table), "grow, wrap");

        JPanel refundRow = new JPanel(new MigLayout("insets 0, fillx", "[][grow][][grow]", "[]"));
        refundRow.add(new JLabel("İade Tutarı"));
        refundAmountField = new CurrencyField();
        refundAmountField.setAvailableCurrencies(List.of("TRY"));
        refundRow.add(refundAmountField, "growx");

        refundRow.add(new JLabel("Yöntem"));
        refundTypeCombo = new JComboBox<>(PaymentType.values());
        refundTypeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PaymentType) setText(((PaymentType) value).getDisplayName());
                return this;
            }
        });
        refundRow.add(refundTypeCombo, "growx");
        add(refundRow, "growx, wrap");

        JPanel footer = new JPanel(new MigLayout("insets 0, fillx", "[grow][pref!]", "[]"));
        lblReturnTotal = new JLabel("İade edilecek tutar: " + Format.formatPrice(BigDecimal.ZERO));
        footer.add(lblReturnTotal);

        btnSave = new JButton("İadeyi Kaydet");
        btnSave.putClientProperty(FlatClientProperties.STYLE,
                "background: $Component.accentColor; foreground: #ffffff; arc: 10; margin: 8,14,8,14; font: bold");
        btnSave.addActionListener(e -> save());
        footer.add(btnSave);

        add(footer, "growx");
    }

    private void setupTable() {
        List<ColumnDef<Row>> columns = Arrays.asList(
                new ColumnDef<>("Kalem", String.class, r -> r.item.getItemName()),
                new ColumnDef<>("Satılan", Integer.class, r -> r.item.getQuantity()),
                new ColumnDef<>("İade Edilebilir", Integer.class, r -> r.maxReturnable),
                new ColumnDef<>("İade Adedi", Integer.class, r -> r.quantity),
                new ColumnDef<>("İade Tutarı", String.class, r -> r.quantity > 0 ? Format.formatPrice(r.amount()) : ""),
                new ColumnDef<Row>("", String.class, r -> "").editable(true)
        );
        tableModel = new GenericTableModel<>(columns);
    }

    private JTable buildTable() {
        JTable table = new JTable(tableModel);
        table.setRowHeight(30);
        DynamicActionColumnSupport.install(table, 5, tableModel, List.of(
                DynamicActionColumnSupport.button("icons/plus.svg", new Color(46, 204, 113), "Artır",
                        r -> { if (r.quantity < r.maxReturnable) { r.quantity++; refresh(); } }),
                DynamicActionColumnSupport.button("icons/minus.svg", new Color(230, 126, 34), "Azalt",
                        r -> { if (r.quantity > 0) { r.quantity--; refresh(); } })
        ));
        table.getColumnModel().getColumn(5).setMaxWidth(90);
        return table;
    }

    private void loadItems() {
        saleService.getReturnQuotes(sale.getId()).thenAccept(quotes -> SwingUtilities.invokeLater(() -> {
            rows.clear();
            for (SaleService.ReturnQuote q : quotes) {
                if (q.returnable > 0) rows.add(new Row(q));
            }
            refresh();
        })).exceptionally(ex -> ErrorHandler.handle(this, "İade edilebilir miktarlar yüklenemedi", ex));
    }

    private void refresh() {
        tableModel.setData(new ArrayList<>(rows));

        BigDecimal total = BigDecimal.ZERO;
        for (Row row : rows) {
            total = total.add(row.amount());
        }
        lblReturnTotal.setText("İade edilecek tutar: " + Format.formatPrice(total));
        refundAmountField.setValue(total.doubleValue());
    }

    private void save() {
        List<SaleService.ReturnLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Row row : rows) {
            if (row.quantity <= 0) continue;
            lines.add(new SaleService.ReturnLine(row.item.getId(), row.quantity));
            total = total.add(row.amount());
        }
        if (lines.isEmpty()) {
            Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.return.noItemsSelected"));
            return;
        }

        BigDecimal refundAmount = toBigDecimal(refundAmountField.getValue());
        if (refundAmount.compareTo(total) > 0) {
            Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.return.refundExceedsTotal"));
            return;
        }

        btnSave.setEnabled(false);
        saleService.recordReturn(sale.getId(), lines, (PaymentType) refundTypeCombo.getSelectedItem(), refundAmount)
                .thenAccept(returned -> SwingUtilities.invokeLater(() -> {
                    Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.return.completed"));
                    AppModal.closeModal(modalId);
                    if (onSuccess != null) onSuccess.run();
                })).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> btnSave.setEnabled(true));
                    return ErrorHandler.handle(this, "İade kaydedilemedi", ex);
                });
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return BigDecimal.ZERO;
    }
}
