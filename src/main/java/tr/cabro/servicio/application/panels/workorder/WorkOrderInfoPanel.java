package tr.cabro.servicio.application.panels.workorder;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
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

        setLayout(new MigLayout("insets 0, gapy 20", "[fill, grow]", "[pref!][pref!][pref!]"));
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
                    ? PhoneHelper.formatForDisplay(workOrder.getCustomer().getPhoneNumber1()) : "-");
            lblCustomerEmail.setText(workOrder.getCustomer().getEmail() != null
                    ? workOrder.getCustomer().getEmail() : "-");
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
    }

    private JPanel createCustomerCard() {
        JPanel card = WorkOrderPanelSupport.createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx, wrap 2", "[grow][]", "[]15[][][]10[]"));
        JLabel title = new JLabel("Müşteri Bilgileri");
        title.setIcon(new Ikon("icons/user.svg"));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        lblCustomerName = new JLabel("-");
        lblCustomerName.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        lblCustomerPhone = new JLabel("-");
        lblCustomerPhone.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        lblCustomerEmail = new JLabel("-");
        lblCustomerEmail.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        btnWhatsapp = new JButton("WhatsApp Mesaj Gönder", new Ikon("icons/message-circle.svg", 1f));
        btnWhatsapp.putClientProperty(FlatClientProperties.STYLE,
                "background: #0b4a3a; foreground: #2ecc71; arc: 10; font: bold; borderWidth: 0");
        btnWhatsapp.addActionListener(e -> onWhatsAppRequested.run());
        btnWhatsapp.setVisible(false);

        card.add(title, "span 2");
        card.add(lblCustomerName, "span 2");
        card.add(lblCustomerPhone, "span 2");
        card.add(lblCustomerEmail, "span 2");
        card.add(btnWhatsapp, "span 2, growx");
        return card;
    }

    private JPanel createDeviceCard() {
        JPanel card = WorkOrderPanelSupport.createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[100!][grow]", "[]15[][][][][]15[]5[]"));
        JLabel title = new JLabel("Cihaz Bilgileri");
        title.setIcon(new Ikon("icons/tablet-smartphone.svg"));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        lblDeviceType = new JLabel("-");
        lblDeviceType.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceBrand = new JLabel("-");
        lblDeviceBrand.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceModel = new JLabel("-");
        lblDeviceModel.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceSerial = new JLabel("-");
        lblDeviceSerial.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        card.add(title, "span 2, wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Tür:")); card.add(lblDeviceType, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Marka:")); card.add(lblDeviceBrand, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Model:")); card.add(lblDeviceModel, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Seri No:")); card.add(lblDeviceSerial, "wrap");

        lblDeviceAccess = new JLabel("-");
        lblDeviceAccess.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        btnRevealAccess = new JButton(new Ikon("icons/eye.svg", 0.7f));
        btnRevealAccess.setToolTipText("Erişim kodunu göster/gizle");
        btnRevealAccess.setVisible(false);
        btnRevealAccess.addActionListener(e -> toggleAccessReveal());
        JPanel accessRow = new JPanel(new MigLayout("insets 0, fillx", "[grow][]"));
        accessRow.add(lblDeviceAccess, "growx");
        accessRow.add(btnRevealAccess);
        card.add(WorkOrderPanelSupport.createMutedLabel("Erişim Kodu:")); card.add(accessRow, "wrap, growx");

        card.add(WorkOrderPanelSupport.createMutedLabel("Müşteri Şikayeti:"), "span 2, wrap");
        txtReportedFault = new JTextArea();
        txtReportedFault.setEditable(false);
        txtReportedFault.setLineWrap(true);
        txtReportedFault.setWrapStyleWord(true);
        txtReportedFault.putClientProperty(FlatClientProperties.STYLE,
                "background: lighten($Panel.background, 3%); border: 10,10,10,10;");
        card.add(txtReportedFault, "span 2, growx, h 60!, wrap");

        card.add(WorkOrderPanelSupport.createMutedLabel("Teknisyen Arıza Tespiti:"), "span 2, gaptop 10, wrap");
        txtDetectedFault = new JTextArea();
        txtDetectedFault.setLineWrap(true);
        txtDetectedFault.setWrapStyleWord(true);
        txtDetectedFault.putClientProperty(FlatClientProperties.STYLE,
                "background: lighten($Panel.background, 3%); border: 10,10,10,10;");
        card.add(txtDetectedFault, "span 2, growx, h 60!, wrap");

        JButton btnSaveDetectedFault = new JButton("Kaydet");
        btnSaveDetectedFault.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
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
        lblDeviceAccess.setText("-");
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

    private JPanel createTimelineCard() {
        JPanel card = WorkOrderPanelSupport.createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[100!][grow, right]", "[]15[][]"));
        JLabel title = new JLabel("Zaman Çizelgesi");
        title.setIcon(new Ikon("icons/clock.svg"));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        lblDateArrival = new JLabel("-");
        lblDateArrival.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDateEstimated = new JLabel("-");
        lblDateEstimated.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        card.add(title, "span 2, wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Geliş:")); card.add(lblDateArrival, "wrap");
        card.add(WorkOrderPanelSupport.createMutedLabel("Tahmini Bitiş:")); card.add(lblDateEstimated, "wrap");
        return card;
    }
}
