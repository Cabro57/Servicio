package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.PaymentAllocation;
import tr.cabro.servicio.model.dto.OpenDocumentDto;
import tr.cabro.servicio.model.enums.AllocationTargetType;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.service.PaymentService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tahsilat diyaloğu — müşterinin açık belgeleri (iş emri/satış) en eski tarihten başlayarak
 * otomatik dağıtılır; bir satıra çift tıklayıp tutarı elle değiştirmek dağıtımı o satırda
 * SABİTLER, kalan tutar diğer (elle sabitlenmemiş) satırlara yine FIFO ile dağıtılmaya devam eder.
 * Dağıtılmayan fark {@link PaymentService#recordCollection} tarafından otomatik olarak müşterinin
 * avans bakiyesi olarak bırakılır (allocation yazılmaz, sadece payment'ın kendisi kaydedilir).
 */
public class CollectionPanel extends JPanel {

    private static class Row {
        final OpenDocumentDto doc;
        BigDecimal allocated = BigDecimal.ZERO;
        boolean manual = false;

        Row(OpenDocumentDto doc) {
            this.doc = doc;
        }
    }

    private final Customer customer;
    private final String modalId;
    private final Runnable onSuccess;
    private final PaymentService paymentService;

    private final List<Row> rows = new ArrayList<>();
    private GenericTableModel<Row> tableModel;

    private CurrencyField amountField;
    private JComboBox<PaymentType> paymentTypeCombo;
    private JLabel lblUnallocated;
    private JButton btnSave;

    /** Müşteri cari sayfasından ve iş emri ekranından çağrılan ortak açılış noktası. */
    public static void open(Component parent, Customer customer, Runnable onSuccess) {
        String modalId = "Collection-" + customer.getId();
        CollectionPanel panel = new CollectionPanel(customer, modalId, onSuccess);
        SimpleModalBorder.Option[] options = {new SimpleModalBorder.Option("Kapat", SimpleModalBorder.CANCEL_OPTION)};
        AppModal.showModal(parent, new SimpleModalBorder(panel, "Tahsilat Al", options, (controller, action) -> {}), modalId);
    }

    public CollectionPanel(Customer customer, String modalId, Runnable onSuccess) {
        this.customer = customer;
        this.modalId = modalId;
        this.onSuccess = onSuccess;
        this.paymentService = ServiceManager.getPaymentService();
        init();
        loadOpenDocuments();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 15, gap 10", "[grow]", "[][][grow][pref!]"));
        setPreferredSize(new Dimension(560, 480));

        JLabel title = new JLabel("Tahsilat — " + customer.getFullName());
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        add(title, "wrap");

        JPanel headerRow = new JPanel(new MigLayout("insets 0, fillx", "[][grow][][grow][]", "[]"));
        headerRow.add(new JLabel("Tutar"));
        amountField = new CurrencyField();
        amountField.setAvailableCurrencies(List.of("TRY"));
        amountField.addPropertyChangeListener("value", e -> redistribute());
        headerRow.add(amountField, "growx");

        headerRow.add(new JLabel("Yöntem"));
        paymentTypeCombo = new JComboBox<>(PaymentType.values());
        paymentTypeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PaymentType) setText(((PaymentType) value).getDisplayName());
                return this;
            }
        });
        headerRow.add(paymentTypeCombo, "growx");

        JButton btnAutoDistribute = new JButton("FIFO'ya Döndür");
        btnAutoDistribute.setToolTipText("Elle yapılan tüm değişiklikleri sıfırlar, en eski belgeden başlayarak yeniden dağıtır.");
        btnAutoDistribute.addActionListener(e -> {
            for (Row row : rows) row.manual = false;
            redistribute();
        });
        headerRow.add(btnAutoDistribute);

        add(headerRow, "growx, wrap");

        setupTable();
        JTable table = buildTable();
        add(new JScrollPane(table), "grow, wrap");

        JPanel footer = new JPanel(new MigLayout("insets 0, fillx", "[grow][pref!]", "[]"));
        lblUnallocated = new JLabel("Dağıtılmayan (avans): " + Format.formatPrice(BigDecimal.ZERO));
        footer.add(lblUnallocated);

        btnSave = new JButton("Tahsilatı Kaydet");
        btnSave.putClientProperty(FlatClientProperties.STYLE,
                "background: $Component.accentColor; foreground: #ffffff; arc: 10; margin: 8,14,8,14; font: bold");
        btnSave.addActionListener(e -> save());
        footer.add(btnSave);

        add(footer, "growx");
    }

    private void setupTable() {
        List<ColumnDef<Row>> columns = Arrays.asList(
                new ColumnDef<>("Belge", String.class, r -> r.doc.getDocumentLabel()),
                new ColumnDef<>("Tarih", String.class, r -> r.doc.getDocumentDate() != null ? r.doc.getDocumentDate().format(DateFormats.dateTime()) : "-"),
                new ColumnDef<>("Kalan", BigDecimal.class, r -> r.doc.getRemainingAmount()),
                new ColumnDef<>("Tahsis Edilecek", BigDecimal.class, r -> r.allocated)
        );
        tableModel = new GenericTableModel<>(columns);
    }

    private JTable buildTable() {
        JTable table = new JTable(tableModel);
        table.setRowHeight(30);
        table.getColumnModel().getColumn(2).setCellRenderer(new tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.rowAtPoint(e.getPoint());
                    if (viewRow < 0) return;
                    Row row = tableModel.getItemAt(table.convertRowIndexToModel(viewRow));
                    if (row != null) editRow(row);
                }
            }
        });
        return table;
    }

    private void editRow(Row row) {
        DialogHelper.prompt(this, "prompt.collection.override.title", "prompt.collection.override.label",
                row.allocated.toPlainString(), text -> {
                    BigDecimal value;
                    try {
                        value = new BigDecimal(text.trim().replace(",", "."));
                    } catch (Exception ex) {
                        Toast.show(this, Toast.Type.WARNING, Messages.get("toast.collection.invalidAmount"));
                        return;
                    }
                    if (value.signum() < 0) value = BigDecimal.ZERO;
                    if (value.compareTo(row.doc.getRemainingAmount()) > 0) value = row.doc.getRemainingAmount();

                    row.allocated = value;
                    row.manual = true;
                    redistribute();
                }, row.doc.getDocumentLabel(), Format.formatPrice(row.doc.getRemainingAmount()));
    }

    private void loadOpenDocuments() {
        paymentService.getOpenDocuments(customer.getId()).thenAccept(docs -> SwingUtilities.invokeLater(() -> {
            rows.clear();
            for (OpenDocumentDto doc : docs) rows.add(new Row(doc));
            redistribute();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Açık belgeler yüklenemedi", ex));
    }

    private void redistribute() {
        BigDecimal amount = toBigDecimal(amountField.getValue());
        BigDecimal remaining = amount;

        for (Row row : rows) {
            if (row.manual) {
                remaining = remaining.subtract(row.allocated);
                continue;
            }
            BigDecimal available = remaining.max(BigDecimal.ZERO);
            BigDecimal alloc = row.doc.getRemainingAmount().min(available);
            row.allocated = alloc.max(BigDecimal.ZERO);
            remaining = remaining.subtract(row.allocated);
        }

        tableModel.setData(new ArrayList<>(rows));
        lblUnallocated.setText("Dağıtılmayan (avans): " + Format.formatPrice(remaining.max(BigDecimal.ZERO)));
    }

    private void save() {
        BigDecimal amount = toBigDecimal(amountField.getValue());
        if (amount.signum() <= 0) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.collection.invalidAmount"));
            return;
        }

        List<PaymentAllocation> allocations = new ArrayList<>();
        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (Row row : rows) {
            if (row.allocated.signum() <= 0) continue;
            allocatedTotal = allocatedTotal.add(row.allocated);
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setTargetType(AllocationTargetType.valueOf(row.doc.getDocumentType().name()));
            allocation.setTargetId(row.doc.getDocumentId());
            allocation.setAmount(row.allocated);
            allocations.add(allocation);
        }
        if (allocatedTotal.compareTo(amount) > 0) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.collection.overAllocated"));
            return;
        }

        btnSave.setEnabled(false);
        try {
            paymentService.recordCollection(customer.getId(), amount, (PaymentType) paymentTypeCombo.getSelectedItem(),
                            null, LocalDateTime.now(), allocations)
                    .thenAccept(payment -> SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.collection.completed"));
                        AppModal.closeModal(modalId);
                        if (onSuccess != null) onSuccess.run();
                    })).exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> btnSave.setEnabled(true));
                        return ErrorHandler.handle(this, "Tahsilat kaydedilemedi", ex);
                    });
        } catch (Exception ex) {
            btnSave.setEnabled(true);
            ErrorHandler.handle(this, "Tahsilat kaydedilemedi", ex);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return BigDecimal.ZERO;
    }
}
