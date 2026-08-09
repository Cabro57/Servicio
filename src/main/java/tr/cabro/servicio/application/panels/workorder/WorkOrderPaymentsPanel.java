package tr.cabro.servicio.application.panels.workorder;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.table.DynamicActionColumnSupport;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.documents.PaymentReceiptFormGenerator;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.WorkOrderPayment;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * `FormWorkOrder`'ın sağ kolonundaki "Ödemeler (Ön Muhasebe)" kartı — eskiden
 * {@code FormWorkOrder.buildPaymentsCard()}/{@code populatePaymentsTable()}/
 * {@code buildPaymentInputRow()}/{@code confirmDeletePayment()}/{@code buildPaymentSummaryBox()}/
 * {@code updatePaymentSummary()}/{@code refreshPaymentBadge()} olarak tek sınıfta duruyordu.
 * {@link #refresh()} hem kendi ödeme akışlarından hem de {@link WorkOrderItemsPanel}'in
 * kalem ekleme/düzenleme/silme sonrası callback'inden çağrılır (kalan bakiye kalem toplamına bağlı).
 */
public class WorkOrderPaymentsPanel extends JPanel {

    private final WorkOrder workOrder;
    private final WorkOrderService workOrderService;

    private GenericTableModel<WorkOrderPayment> paymentsTableModel;
    private JPanel paymentsTableContainer;
    private JPanel paymentsEmptyLabel;

    private JLabel lblTotalService;
    private JLabel lblTotalPaid;
    private JLabel lblRemainVal;
    private JLabel lblPaymentBadge;

    public WorkOrderPaymentsPanel(WorkOrder workOrder) {
        this.workOrder = workOrder;
        this.workOrderService = ServiceManager.getWorkOrderService();
        build();
    }

    private void build() {
        putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); arc: 15;");
        setLayout(new MigLayout("insets 20, fillx", "[grow]", "[]15[][]20[]"));

        JLabel title = new JLabel("Ödemeler (Ön Muhasebe)");
        title.setIcon(new Ikon("icons/credit-card.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        add(title, "wrap");

        List<ColumnDef<WorkOrderPayment>> columnDefs = Arrays.asList(
                new ColumnDef<>("Tarih", LocalDateTime.class, WorkOrderPayment::getPaymentDate),
                new ColumnDef<>("Yöntem", PaymentType.class, WorkOrderPayment::getPaymentType),
                new ColumnDef<>("Tutar", BigDecimal.class, WorkOrderPayment::getAmount),
                new ColumnDef<>("İşlem", WorkOrderPayment.class, workOrderPayment -> "Detay")
        );

        paymentsTableModel = new GenericTableModel<>(columnDefs);

        JTable paymentsTable = new JTable(paymentsTableModel);
        WorkOrderPanelSupport.styleTable(paymentsTable);
        paymentsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        paymentsTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        paymentsTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        paymentsTable.getColumnModel().getColumn(3).setMaxWidth(50);
        paymentsTable.getColumnModel().getColumn(3).setMinWidth(50);
        paymentsTable.getColumnModel().getColumn(0).setCellRenderer(new DateTimeCellRenderer());
        paymentsTable.getColumnModel().getColumn(2).setCellRenderer(new GreenAmountRenderer());

        DynamicActionColumnSupport.install(paymentsTable, 3, paymentsTableModel, List.of(
                DynamicActionColumnSupport.button("icons/file-text.svg", new Color(13, 110, 253), "Tahsilat fişi yazdır",
                        this::printPaymentReceipt),
                DynamicActionColumnSupport.button("icons/trash-2.svg", new Color(220, 53, 69), "Ödemeyi sil",
                        this::confirmDeletePayment)

        ));

        // JScrollPane KALDILIRDI.
        paymentsTableContainer = new JPanel(new MigLayout("insets 0, gap 0", "[grow, fill]", "[]0[]"));
        paymentsTableContainer.setOpaque(false);
        paymentsTableContainer.add(paymentsTable.getTableHeader(), "wrap");
        paymentsTableContainer.add(paymentsTable);

        paymentsEmptyLabel = WorkOrderPanelSupport.createEmptyStatePanel("Henüz ödeme alınmadı.");

        populatePaymentsTable();

        add(paymentsEmptyLabel, "growx, wrap");
        add(paymentsTableContainer, "growx, wrap");
        add(buildPaymentInputRow(), "growx, wrap");
        add(buildPaymentSummaryBox(), "align right, w 350!");
    }

    private void populatePaymentsTable() {
        List<WorkOrderPayment> payments = workOrder.getPayments();
        if (payments == null || payments.isEmpty()) {
            paymentsEmptyLabel.setVisible(true);
            paymentsTableContainer.setVisible(false);
            return;
        }

        paymentsEmptyLabel.setVisible(false);
        paymentsTableContainer.setVisible(true);

        paymentsTableModel.setData(payments);

        paymentsTableContainer.revalidate();
        paymentsTableContainer.repaint();
    }

    private JPanel buildPaymentInputRow() {
        JPanel inputRow = new JPanel(new MigLayout("insets 10, fillx", "[150!][grow][]", "[]5[]"));
        inputRow.putClientProperty(FlatClientProperties.STYLE,
                "background: lighten($Panel.background, 2%); arc: 10; border: 1,1,1,1,$Component.borderColor");

        inputRow.add(WorkOrderPanelSupport.createMutedLabel("Ödeme Yöntemi"));
        inputRow.add(WorkOrderPanelSupport.createMutedLabel("Tutar (TL)"), "wrap");

        JComboBox<PaymentType> cmbMethod = new JComboBox<>(PaymentType.values());
        cmbMethod.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PaymentType) setText(((PaymentType) value).getDisplayName());
                return this;
            }
        });

        JFormattedTextField txtAmount = new CurrencyField();
        txtAmount.setValue(workOrder.getRemainingAmount());

        JButton btnAddPayment = new JButton("+ Tahsilat Ekle");
        btnAddPayment.putClientProperty(FlatClientProperties.STYLE,
                "background: #0b4a3a; foreground: #2ecc71; arc: 8; font: bold; borderWidth: 0");

        btnAddPayment.addActionListener(e -> {
            BigDecimal amt = new BigDecimal(txtAmount.getValue().toString());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                Toast.show(this, Toast.Type.WARNING, "Geçerli bir tutar girin.");
                return;
            }

            WorkOrderPayment sp = new WorkOrderPayment();
            sp.setServiceId(workOrder.getId());
            sp.setAmount(amt);
            sp.setPaymentType((PaymentType) cmbMethod.getSelectedItem());
            sp.setPaymentDate(LocalDateTime.now());

            workOrderService.addPayment(sp).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                workOrder.getPayments().add(saved);

                populatePaymentsTable();

                txtAmount.setValue(workOrder.getRemainingAmount());

                refresh();
                Toast.show(this, Toast.Type.SUCCESS, "Tahsilat eklendi.");
            })).exceptionally(ex -> ErrorHandler.handle(this, "Tahsilat eklenemedi", ex));
        });

        inputRow.add(cmbMethod, "growx");
        inputRow.add(txtAmount, "growx");
        inputRow.add(btnAddPayment);
        return inputRow;
    }

    private void confirmDeletePayment(WorkOrderPayment payment) {
        if (payment == null) return;
        int confirm = JOptionPane.showConfirmDialog(
                this,
                Format.formatPrice(payment.getAmount()) + " tutarlı ödeme silinecek. Emin misiniz?",
                "Silme Onayı",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        workOrderService.deletePayment(payment.getId()).thenRun(() -> SwingUtilities.invokeLater(() -> {
            workOrder.getPayments().remove(payment);

            populatePaymentsTable();

            refresh();
            Toast.show(this, Toast.Type.SUCCESS, "Ödeme silindi.");
        })).exceptionally(ex -> ErrorHandler.handle(this, "Ödeme silinemedi", ex));
    }

    private void printPaymentReceipt(WorkOrderPayment payment) {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> {
            User shop = shopOpt.orElse(null);
            try {
                File pdf = new PaymentReceiptFormGenerator().generate(workOrder, payment, shop);
                SwingUtilities.invokeLater(() -> {
                    if (DesktopHelper.openFile(pdf)) {
                        Toast.show(this, Toast.Type.SUCCESS, "Fiş oluşturuldu.");
                    } else {
                        Toast.show(this, Toast.Type.WARNING, "Fiş oluşturuldu ama açılamadı: " + pdf.getAbsolutePath());
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Fiş oluşturulamadı: " + ex.getMessage()));
                Servicio.getLogger().error("Tahsilat fişi oluşturma hatası", ex);
            }
        }).exceptionally(ex -> ErrorHandler.handle(this, "Tahsilat fişi oluşturulamadı", ex));
    }

    private JPanel buildPaymentSummaryBox() {
        JPanel summaryBox = new JPanel(new MigLayout("insets 15, fillx", "[grow][pref!]", "[]10[]15[]10[]"));
        summaryBox.putClientProperty(FlatClientProperties.STYLE, "background: darken($Panel.background, 2%); arc: 15");

        summaryBox.add(WorkOrderPanelSupport.createMutedLabel("Hizmet & Parça Toplamı:"));
        lblTotalService = new JLabel(Format.formatPrice(workOrder.getTotalServiceAmount()));
        summaryBox.add(lblTotalService, "align right, wrap");

        summaryBox.add(WorkOrderPanelSupport.createMutedLabel("Alınan Ödeme:"));
        lblTotalPaid = new JLabel("- " + Format.formatPrice(workOrder.getTotalPaid()));
        lblTotalPaid.putClientProperty(FlatClientProperties.STYLE, "foreground: #2ecc71");
        summaryBox.add(lblTotalPaid, "align right, wrap");

        summaryBox.add(new JSeparator(), "span 2, growx, wrap");

        JLabel lblRemainText = new JLabel("Kalan Bakiye:");
        lblRemainText.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        summaryBox.add(lblRemainText);

        BigDecimal remain = workOrder.getRemainingAmount();
        lblRemainVal = new JLabel(Format.formatPrice(remain));
        String remainColor = remain.compareTo(BigDecimal.ZERO) > 0 ? "#e74c3c" : "#2ecc71";
        lblRemainVal.putClientProperty(FlatClientProperties.STYLE, "font: bold +4; foreground: " + remainColor);
        summaryBox.add(lblRemainVal, "align right, wrap");

        lblPaymentBadge = new JLabel();
        refreshPaymentBadge(lblPaymentBadge);
        summaryBox.add(lblPaymentBadge, "span 2, align right");

        return summaryBox;
    }

    /** Kalem/ödeme değişikliği sonrası özet kutusunu ve rozeti günceller. */
    public void refresh() {
        lblTotalService.setText(Format.formatPrice(workOrder.getTotalServiceAmount()));
        lblTotalPaid.setText("- " + Format.formatPrice(workOrder.getTotalPaid()));

        BigDecimal remain = workOrder.getRemainingAmount();
        lblRemainVal.setText(Format.formatPrice(remain));
        String remainColor = remain.compareTo(BigDecimal.ZERO) > 0 ? "#e74c3c" : "#2ecc71";
        lblRemainVal.putClientProperty(FlatClientProperties.STYLE, "font: bold +4; foreground: " + remainColor);

        refreshPaymentBadge(lblPaymentBadge);

        lblRemainVal.repaint();
        lblPaymentBadge.repaint();
    }

    private void refreshPaymentBadge(JLabel badge) {
        BigDecimal totalCost = workOrder.getTotalServiceAmount();
        BigDecimal totalPaid = workOrder.getTotalPaid();
        BigDecimal remain = workOrder.getRemainingAmount();

        if (totalCost.compareTo(BigDecimal.ZERO) == 0) {
            badge.setText("Ücretsiz İşlem");
            badge.putClientProperty(FlatClientProperties.STYLE,
                    "background: #1e3a8a; foreground: #3498db; arc: 15; border: 4,10,4,10; font: bold -1");
        } else if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            badge.setText("Ödenmedi");
            badge.putClientProperty(FlatClientProperties.STYLE,
                    "background: #4a1919; foreground: #e74c3c; arc: 15; border: 4,10,4,10; font: bold -1");
        } else if (remain.compareTo(BigDecimal.ZERO) > 0) {
            badge.setText("Kısmi Ödeme");
            badge.putClientProperty(FlatClientProperties.STYLE,
                    "background: #7a5c13; foreground: #f1c40f; arc: 15; border: 4,10,4,10; font: bold -1");
        } else {
            badge.setText("Ödendi");
            badge.putClientProperty(FlatClientProperties.STYLE,
                    "background: #0b4a3a; foreground: #2ecc71; arc: 15; border: 4,10,4,10; font: bold -1");
        }
        badge.setOpaque(true);
    }

    private static class GreenAmountRenderer extends DefaultTableCellRenderer {
        GreenAmountRenderer() { setHorizontalAlignment(SwingConstants.TRAILING); }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.putClientProperty(FlatClientProperties.STYLE, "foreground: #2ecc71; font: bold");
            return label;
        }
    }

    /** "Tarih" kolonu ham {@code LocalDateTime.toString()} (ISO-8601) yerine okunur formatta gösterir. */
    private static class DateTimeCellRenderer extends DefaultTableCellRenderer {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            String text = value instanceof LocalDateTime ? ((LocalDateTime) value).format(FORMATTER) : "-";
            return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);
        }
    }
}
