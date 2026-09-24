package tr.cabro.servicio.application.panels.workorder;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * `FormWorkOrder`'ın sol kolonu — eskiden {@code FormWorkOrder.leftColumn} içindeki
 * {@code createCustomerCard()}/{@code createDeviceCard()}/{@code createTimelineCard()} ve
 * {@code hydrateLeftUI()}'in müşteri/cihaz/zaman-çizelgesi kısmı, {@code loadDeviceAccessCredential()}/
 * {@code toggleAccessReveal()}/{@code updateAccessLabel()} olarak tek sınıfta duruyordu.
 * WhatsApp mesajı gönderme, form-seviyesi bir aksiyon olduğu için burada değil orkestratörde
 * ({@code FormWorkOrder.openWhatsAppModal()}) kalıyor — {@link #onWhatsAppRequested} callback'i
 * ile tetiklenir.
 */
public class WorkOrderInfoPanel extends JPanel {

    private WorkOrder workOrder;
    private final WorkOrderService workOrderService;
    private final Runnable onWhatsAppRequested;

    private JLabel lblCustomerName, lblCustomerPhone, lblCustomerEmail;
    private JButton btnWhatsapp;
    private JLabel lblDeviceType, lblDeviceBrand, lblDeviceModel, lblDeviceSerial;
    private JLabel lblDeviceAccess;
    private JButton btnRevealAccess;
    private String currentAccessTypeLabel;
    private String currentAccessSecret; // düz metin — sadece "göster"e basılınca gösterilir
    private boolean accessRevealed;
    private JTextArea txtReportedFault;
    private JTextArea txtDetectedFault;
    private JLabel lblDateArrival, lblDateEstimated;

    public WorkOrderInfoPanel(WorkOrder workOrder, Runnable onWhatsAppRequested) {
        this.workOrder = workOrder;
        this.workOrderService = ServiceManager.getWorkOrderService();
        this.onWhatsAppRequested = onWhatsAppRequested;

        setLayout(new MigLayout("insets 0, gapy 16", "[fill, grow]", "[pref!][pref!][pref!]"));
        setOpaque(false);
        add(createCustomerCard(), "wrap");
        add(createDeviceCard(), "wrap");
        add(createTimelineCard(), "wrap");
    }

    /** Servis kaydı yeniden yüklendiğinde ({@code setService}/{@code formRefresh}) çağrılır. */
    public void refresh(WorkOrder updatedWorkOrder) {
        this.workOrder = updatedWorkOrder;

        DateTimeFormatter formatter = DateFormats.dateTime();
        String dateStr = workOrder.getCreatedAt() != null ? workOrder.getCreatedAt().format(formatter) : "-";

        if (workOrder.getCustomer() != null) {
            lblCustomerName.setText(workOrder.getCustomer().getFullName());
            lblCustomerPhone.setText(workOrder.getCustomer().getPhoneNumber1() != null
                    ? PhoneHelper.formatForDisplay(workOrder.getCustomer().getPhoneNumber1()) : "Telefon yok");
            String email = workOrder.getCustomer().getEmail();
            lblCustomerEmail.setText(email != null && !email.isBlank() ? email : " ");
            btnWhatsapp.setVisible(workOrder.getCustomer().getPhoneNumber1() != null
                    && !workOrder.getCustomer().getPhoneNumber1().isBlank());
        } else {
            btnWhatsapp.setVisible(false);
        }

        Device device = workOrder.getDevice();
        if (device != null) {
            lblDeviceType.setText(device.getDeviceType() != null ? device.getDeviceType().getName() : "-");
            lblDeviceBrand.setText(device.getBrand() != null ? device.getBrand().getName() : "-");
            lblDeviceModel.setText(device.getModel() != null ? device.getModel() : "-");
            lblDeviceSerial.setText(device.getSerialNo() != null ? device.getSerialNo() : "-");
        }
        loadDeviceAccessCredential();

        txtReportedFault.setText(workOrder.getReportedFault() != null ? workOrder.getReportedFault() : "Belirtilmemiş.");
        txtDetectedFault.setText(workOrder.getDetectedFault() != null ? workOrder.getDetectedFault() : "");
        lblDateArrival.setText(dateStr);
        lblDateEstimated.setText(workOrder.getCreatedAt() != null
                ? workOrder.getCreatedAt().plusDays(3).format(formatter) : "-");
        lblDateStatus.setText(workOrder.getStatusChangedAt() != null ? workOrder.getStatusChangedAt().format(formatter) : "Bilinmiyor");
        lblDateDelivery.setText(workOrder.getDeliveryDate() != null ? workOrder.getDeliveryDate().format(formatter) : "Teslim edilmedi");
        if (workOrder.getCreatedAt() != null) {
            java.time.LocalDate end = workOrder.getDeliveryDate() != null ? workOrder.getDeliveryDate().toLocalDate() : java.time.LocalDate.now();
            long days = java.time.temporal.ChronoUnit.DAYS.between(workOrder.getCreatedAt().toLocalDate(), end);
            lblDaysIn.setText(days <= 0 ? "Aynı gün" : days + " gün");
        } else {
            lblDaysIn.setText("-");
        }
    }

    private JPanel createCustomerCard() {
        JPanel card = WorkOrderPanelSupport.createCardPanel();
        card.setLayout(new MigLayout("insets 16 18 16 18, fillx, wrap, hidemode 3", "[grow, fill]", "[]10[]2[]2[]"));
        JButton link = new JButton("Müşteriye git");
        link.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        link.putClientProperty(FlatClientProperties.STYLE, "foreground: $Component.accentColor; margin: 2,6,2,6");
        link.addActionListener(e -> {
            if (workOrder.getCustomer() != null) {
                tr.cabro.servicio.application.system.FormManager.showForm(
                        new tr.cabro.servicio.application.forms.FormCustomer(workOrder.getCustomer()));
            }
        });
        lblCustomerName = new JLabel("-");
        lblCustomerName.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        lblCustomerPhone = new JLabel("-");
        lblCustomerEmail = new JLabel("-");
        lblCustomerEmail.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        // Görünmez tutulur: WhatsApp artık kimlik şeridinde (onWhatsAppRequested oradan çağrılır).
        btnWhatsapp = new JButton();
        btnWhatsapp.setVisible(false);

        JPanel head = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[center]"));
        head.setOpaque(false);
        head.add(WorkOrderPanelSupport.createTitle("Müşteri"));
        head.add(link);
        card.add(head);
        card.add(lblCustomerName, "wmin 0");
        card.add(lblCustomerPhone, "wmin 0");
        card.add(lblCustomerEmail, "wmin 0");
        return card;
    }

    private JPanel createDeviceCard() {
        JPanel card = WorkOrderPanelSupport.createCardPanel();
        card.setLayout(new MigLayout("insets 16 18 16 18, fillx", "[110!][grow, right]", "[]10[][][][][]14[]6[]"));
        JLabel title = WorkOrderPanelSupport.createTitle("Cihaz ve arıza");
        lblDeviceType = new JLabel("-");
        lblDeviceType.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceBrand = new JLabel("-");
        lblDeviceBrand.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceModel = new JLabel("-");
        lblDeviceModel.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceSerial = new JLabel("-");
        lblDeviceSerial.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        card.add(title, "span 2, wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Tür")); card.add(lblDeviceType, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Marka")); card.add(lblDeviceBrand, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Model")); card.add(lblDeviceModel, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Seri no")); card.add(lblDeviceSerial, "wrap");

        lblDeviceAccess = new JLabel("-");
        lblDeviceAccess.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        btnRevealAccess = new JButton(new Ikon("icons/eye.svg", 0.7f));
        btnRevealAccess.setToolTipText("Erişim kodunu göster/gizle");
        btnRevealAccess.setVisible(false);
        btnRevealAccess.addActionListener(e -> toggleAccessReveal());
        JPanel accessRow = new JPanel(new MigLayout("insets 0, fillx, gap 4, hidemode 3", "push[][]", "[center]"));
        accessRow.add(lblDeviceAccess);
        accessRow.add(btnRevealAccess);
        accessRow.setOpaque(false);
        card.add(WorkOrderPanelSupport.createMutedLabel("Erişim kodu")); card.add(accessRow, "wrap, growx");

        card.add(WorkOrderPanelSupport.createMutedLabel("Müşteri şikâyeti"), "span 2, wrap");
        txtReportedFault = new JTextArea();
        txtReportedFault.setEditable(false);
        txtReportedFault.setLineWrap(true);
        txtReportedFault.setWrapStyleWord(true);
        txtReportedFault.putClientProperty(FlatClientProperties.STYLE, "font: bold; border: 0,0,0,0");
        txtReportedFault.setOpaque(false);
        card.add(txtReportedFault, "span 2, growx, wmin 0, wrap");

        card.add(WorkOrderPanelSupport.createMutedLabel("Teknisyen arıza tespiti"), "span 2, gaptop 10, wrap");
        txtDetectedFault = new JTextArea();
        txtDetectedFault.setLineWrap(true);
        txtDetectedFault.setWrapStyleWord(true);
        txtDetectedFault.putClientProperty(FlatClientProperties.STYLE, "border: 8,10,8,10");
        txtDetectedFault.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Tespit edilen arızayı yazın…");
        JScrollPane detectedScroll = new JScrollPane(txtDetectedFault);
        detectedScroll.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        card.add(detectedScroll, "span 2, growx, h 70!, wrap");

        JButton btnSaveDetectedFault = new JButton("Tespiti kaydet");
        btnSaveDetectedFault.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10");
        btnSaveDetectedFault.addActionListener(e -> saveDetectedFault());
        card.add(btnSaveDetectedFault, "span 2, align right, gaptop 5");
        return card;
    }

    private void saveDetectedFault() {
        String text = txtDetectedFault.getText().trim();
        workOrderService.updateDetectedFault(workOrder.getId(), text).thenRun(() ->
                SwingUtilities.invokeLater(() -> {
                    workOrder.setDetectedFault(text);
                    Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.detectedFault.saved"));
                })
        ).exceptionally(ex -> ErrorHandler.handle(this, "Arıza tespiti kaydedilemedi", ex));
    }

    /** Bu servis kaydına ait cihaz erişim kodunu (varsa) yükler; süresi dolup silindiyse "-" gösterir. */
    private void loadDeviceAccessCredential() {
        lblDeviceAccess.setText("—");
        btnRevealAccess.setVisible(false);
        currentAccessSecret = null;
        currentAccessTypeLabel = null;
        accessRevealed = false;

        if (workOrder.getId() == null) return;

        ServiceManager.getDeviceAccessCredentialService().getActive(workOrder.getId())
                .thenAccept(credentialOpt -> SwingUtilities.invokeLater(() -> credentialOpt.ifPresent(c -> {
                    if (c.getSecret() == null) {
                        lblDeviceAccess.setText("Süresi doldu, silindi.");
                        return;
                    }
                    currentAccessTypeLabel = c.getAccessType().getDisplayName();
                    currentAccessSecret = c.getSecret();
                    btnRevealAccess.setVisible(true);
                    updateAccessLabel();
                })))
                .exceptionally(ex -> {
                    Servicio.getLogger().error("Cihaz erişim kodu yüklenemedi", ex);
                    return null;
                });
    }

    private void toggleAccessReveal() {
        accessRevealed = !accessRevealed;
        updateAccessLabel();
    }

    private void updateAccessLabel() {
        if (currentAccessSecret == null) return;
        lblDeviceAccess.setText(currentAccessTypeLabel + ": " + (accessRevealed ? currentAccessSecret : "••••••"));
    }

    private JLabel lblDateStatus, lblDateDelivery, lblDaysIn;

    private JPanel createTimelineCard() {
        JPanel card = WorkOrderPanelSupport.createCardPanel();
        card.setLayout(new MigLayout("insets 16 18 16 18, fillx, hidemode 3", "[][grow, right]", "[]10[][][][][]"));
        lblDateArrival = boldLabel();
        lblDateEstimated = boldLabel();
        lblDateStatus = boldLabel();
        lblDateDelivery = boldLabel();
        lblDaysIn = boldLabel();
        card.add(WorkOrderPanelSupport.createTitle("Zaman çizelgesi"), "span 2, wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Geliş")); card.add(lblDateArrival, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Tahmini bitiş")); card.add(lblDateEstimated, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Son durum değişimi")); card.add(lblDateStatus, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Teslim")); card.add(lblDateDelivery, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Serviste geçen")); card.add(lblDaysIn, "wrap");
        return card;
    }

    private static JLabel boldLabel() {
        JLabel l = new JLabel("-");
        l.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        return l;
    }
}
