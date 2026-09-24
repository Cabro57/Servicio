package tr.cabro.servicio.application.panels.workorder;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.table.DynamicActionColumnSupport;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.documents.PaymentReceiptFormGenerator;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.DialogHelper;
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

    private GenericTableModel<Payment> paymentsTableModel;
    private JPanel paymentsTableContainer;
    private JPanel paymentsEmptyLabel;

    private JLabel lblTotalService;
    private JLabel lblTotalPaid;
    private JLabel lblRemainVal;
    private JLabel lblPaymentBadge;

    /** Ödeme eklenip silinince (ve {@link #refresh()} ile) çağrılır; kimlik şeridi tutarları tazeler. */
    private Runnable onChanged;

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public WorkOrderPaymentsPanel(WorkOrder workOrder) {
        this.workOrder = workOrder;
        this.workOrderService = ServiceManager.getWorkOrderService();
        build();
    }

    private void build() {
        putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        setLayout(new MigLayout("insets 16 18 14 18, fillx, hidemode 3", "[grow][]", "[]10[][]12[]"));

        lblPaymentBadge = new JLabel();
        add(WorkOrderPanelSupport.createTitle("Ödemeler"), "aligny center");
        add(lblPaymentBadge, "align right, wrap");

        List<ColumnDef<Payment>> columnDefs = Arrays.asList(
                new ColumnDef<Payment>("Tarih", LocalDateTime.class, Payment::getPaymentDate).alignment(SwingConstants.LEADING),
                new ColumnDef<Payment>("Yöntem", PaymentType.class, Payment::getPaymentType).alignment(SwingConstants.LEADING),
                new ColumnDef<Payment>("Tutar", BigDecimal.class, Payment::getAmount).alignment(SwingConstants.TRAILING),
                new ColumnDef<Payment>("", Payment.class, payment -> "Detay").editable(true)
        );

        paymentsTableModel = new GenericTableModel<>(columnDefs);

        // Fiş/sil düğmeleri yalnızca fare üstündeki satırda (ListTable) görünür.
        tr.cabro.servicio.application.component.table.ListTable paymentsTable = new tr.cabro.servicio.application.component.table.ListTable();
        paymentsTable.setModel(paymentsTableModel);
        WorkOrderPanelSupport.styleTable(paymentsTable);
        tr.cabro.servicio.application.component.table.TableColumnConfigurator.applyColumnRenderers(paymentsTable, columnDefs);
        paymentsTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        paymentsTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        paymentsTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        paymentsTable.getColumnModel().getColumn(3).setMaxWidth(80);
        paymentsTable.getColumnModel().getColumn(3).setMinWidth(80);
        paymentsTable.getColumnModel().getColumn(0).setCellRenderer(new DateTimeCellRenderer());
        paymentsTable.getColumnModel().getColumn(1).setCellRenderer(
                new tr.cabro.servicio.application.renderer.UniversalVisualizableRenderer(SwingConstants.LEADING));
        paymentsTable.getColumnModel().getColumn(2).setCellRenderer(
                new tr.cabro.servicio.application.renderer.MoneyCellRenderer(tr.cabro.servicio.application.renderer.MoneyCellRenderer.Mode.NEGATIVE));

        DynamicActionColumnSupport.install(paymentsTable, 3, paymentsTableModel, List.of(
                DynamicActionColumnSupport.button("icons/file-text.svg", SemanticColor.info(), "Tahsilat fişi yazdır",
                        this::printPaymentReceipt),
                DynamicActionColumnSupport.button("icons/trash-2.svg", SemanticColor.danger(), "Ödemeyi sil",
                        this::confirmDeletePayment)

        ));

        // JScrollPane KALDILIRDI.
        paymentsTableContainer = new JPanel(new MigLayout("insets 0, gap 0", "[grow, fill]", "[]0[]"));
        paymentsTableContainer.setOpaque(false);
        paymentsTableContainer.add(paymentsTable.getTableHeader(), "wrap");
        paymentsTableContainer.add(paymentsTable);

        paymentsEmptyLabel = WorkOrderPanelSupport.createEmptyStatePanel("Henüz ödeme alınmadı.");

        populatePaymentsTable();

        add(paymentsEmptyLabel, "span 2, growx, wrap");
        add(paymentsTableContainer, "span 2, growx, wrap");
        add(buildPaymentInputRow(), "span 2, growx, wrap");
        buildPaymentSummaryBox();
        refreshPaymentBadge(lblPaymentBadge);
    }

    private void populatePaymentsTable() {
        List<Payment> payments = workOrder.getPayments();
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
        // Tahsilat satırı: yöntem + tutar (kalan tutarla dolu gelir) + ekle — tek satırda, kart içinde kart yok.
        JPanel inputRow = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[][170!][grow, fill][]", "[center]"));
        inputRow.setOpaque(false);
        inputRow.add(WorkOrderPanelSupport.createMutedLabel("Tahsilat"));

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

        JButton btnAddPayment = new JButton("Tahsilat Ekle", new Ikon("icons/hand-coins.svg", 16, "Label.foreground"));
        btnAddPayment.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 6,12,6,12; iconTextGap: 6");

        btnAddPayment.addActionListener(e -> {
            BigDecimal amt = new BigDecimal(txtAmount.getValue().toString());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                Toast.show(this, Toast.Type.WARNING, Messages.get("toast.payment.invalidAmount"));
                return;
            }

            Long customerId = workOrder.getCustomer() != null ? workOrder.getCustomer().getId() : null;
            workOrderService.addPayment(workOrder.getId(), customerId, amt,
                    (PaymentType) cmbMethod.getSelectedItem(), null, LocalDateTime.now())
                    .thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                workOrder.getPayments().add(saved);

                populatePaymentsTable();

                txtAmount.setValue(workOrder.getRemainingAmount());

                refresh();
                Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.payment.added"));
            })).exceptionally(ex -> ErrorHandler.handle(this, "Tahsilat eklenemedi", ex));
        });

        inputRow.add(cmbMethod, "growx");
        inputRow.add(txtAmount, "growx");
        inputRow.add(btnAddPayment);
        return inputRow;
    }

    private void confirmDeletePayment(Payment payment) {
        if (payment == null) return;
        DialogHelper.confirmDelete(this, "confirm.delete.payment", () ->
                workOrderService.deletePayment(payment.getId()).thenRun(() -> SwingUtilities.invokeLater(() -> {
                    workOrder.getPayments().remove(payment);

                    populatePaymentsTable();

                    refresh();
                    Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.payment.deleted"));
                })).exceptionally(ex -> ErrorHandler.handle(this, "Ödeme silinemedi", ex)),
                Format.formatPrice(payment.getAmount()));
    }

    private void printPaymentReceipt(Payment payment) {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> {
            User shop = shopOpt.orElse(null);
            try {
                File pdf = new PaymentReceiptFormGenerator().generate(workOrder, payment, shop);
                SwingUtilities.invokeLater(() -> {
                    if (DesktopHelper.openFile(pdf)) {
                        Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.receipt.created"));
                    } else {
                        Toast.show(this, Toast.Type.WARNING, Messages.get("toast.receipt.created.openFailed", pdf.getAbsolutePath()));
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, Messages.get("toast.receipt.failed", ex.getMessage())));
                Servicio.getLogger().error("Tahsilat fişi oluşturma hatası", ex);
            }
        }).exceptionally(ex -> ErrorHandler.handle(this, "Tahsilat fişi oluşturulamadı", ex));
    }

    /** Eski özet kutusu; tutarlar artık kimlik şeridinde. Etiketler refresh() için tutulur, eklenmez. */
    private JPanel buildPaymentSummaryBox() {
        JPanel summaryBox = new JPanel(new MigLayout("insets 15, fillx", "[grow][pref!]", "[]10[]15[]10[]"));
        summaryBox.putClientProperty(FlatClientProperties.STYLE, "background: darken($Panel.background, 2%); arc: 15");

        summaryBox.add(WorkOrderPanelSupport.createMutedLabel("Hizmet & Parça Toplamı:"));
        lblTotalService = new JLabel(Format.formatPrice(workOrder.getTotalServiceAmount()));
        summaryBox.add(lblTotalService, "align right, wrap");

        summaryBox.add(WorkOrderPanelSupport.createMutedLabel("Alınan Ödeme:"));
        lblTotalPaid = new JLabel("- " + Format.formatPrice(workOrder.getTotalPaid()));
        lblTotalPaid.putClientProperty(FlatClientProperties.STYLE,
                "foreground: " + SemanticColor.hex(SemanticColor.success()));
        summaryBox.add(lblTotalPaid, "align right, wrap");

        summaryBox.add(new JSeparator(), "span 2, growx, wrap");

        JLabel lblRemainText = new JLabel("Kalan Bakiye:");
        lblRemainText.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        summaryBox.add(lblRemainText);

        BigDecimal remain = workOrder.getRemainingAmount();
        lblRemainVal = new JLabel(Format.formatPrice(remain));
        String remainColor = SemanticColor.hex(remain.compareTo(BigDecimal.ZERO) > 0
                ? SemanticColor.danger() : SemanticColor.success());
        lblRemainVal.putClientProperty(FlatClientProperties.STYLE, "font: bold +4; foreground: " + remainColor);
        summaryBox.add(lblRemainVal, "align right, wrap");

        return summaryBox;
    }

    /** Kalem/ödeme değişikliği sonrası özet kutusunu ve rozeti günceller. */
    public void refresh() {
        lblTotalService.setText(Format.formatPrice(workOrder.getTotalServiceAmount()));
        lblTotalPaid.setText("- " + Format.formatPrice(workOrder.getTotalPaid()));

        BigDecimal remain = workOrder.getRemainingAmount();
        lblRemainVal.setText(Format.formatPrice(remain));
        String remainColor = SemanticColor.hex(remain.compareTo(BigDecimal.ZERO) > 0
                ? SemanticColor.danger() : SemanticColor.success());
        lblRemainVal.putClientProperty(FlatClientProperties.STYLE, "font: bold +4; foreground: " + remainColor);

        refreshPaymentBadge(lblPaymentBadge);

        lblRemainVal.repaint();
        lblPaymentBadge.repaint();
        if (onChanged != null) onChanged.run();
    }

    /**
     * Ödeme rozeti. Renkler {@link BadgePalette} üzerinden çözülür — eskiden sabit koyu
     * zemin/parlak yazı hex'leri yazılıydı ve açık temada kara blok gibi duruyordu.
     */
    private void refreshPaymentBadge(JLabel badge) {
        BigDecimal totalCost = workOrder.getTotalServiceAmount();
        BigDecimal totalPaid = workOrder.getTotalPaid();
        BigDecimal remain = workOrder.getRemainingAmount();

        if (totalCost.compareTo(BigDecimal.ZERO) == 0) {
            applyBadge(badge, "Ücretsiz İşlem", BadgeColor.BLUE);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            applyBadge(badge, "Ödenmedi", BadgeColor.RED);
        } else if (remain.compareTo(BigDecimal.ZERO) > 0) {
            applyBadge(badge, "Kısmi Ödeme", BadgeColor.YELLOW);
        } else {
            applyBadge(badge, "Ödendi", BadgeColor.GREEN);
        }
    }

    private void applyBadge(JLabel badge, String text, BadgeColor color) {
        badge.setText(text);
        badge.putClientProperty(FlatClientProperties.STYLE, BadgePalette.style(color, null));
        badge.setOpaque(true);
    }

    private static class GreenAmountRenderer extends DefaultTableCellRenderer {
        GreenAmountRenderer() { setHorizontalAlignment(SwingConstants.TRAILING); }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.putClientProperty(FlatClientProperties.STYLE,
                    "foreground: " + SemanticColor.hex(SemanticColor.success()) + "; font: bold");
            return label;
        }
    }

    /** "Tarih" kolonu ham {@code LocalDateTime.toString()} (ISO-8601) yerine okunur formatta gösterir. */
    private static class DateTimeCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            String text = value instanceof LocalDateTime ? ((LocalDateTime) value).format(DateFormats.dateTime()) : "-";
            return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column);
        }
    }
}
