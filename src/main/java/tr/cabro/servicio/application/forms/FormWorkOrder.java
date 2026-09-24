package tr.cabro.servicio.application.forms;

import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.panels.QuickIntakePanel;
import tr.cabro.servicio.application.panels.workorder.WorkOrderDiagnosisPanel;
import tr.cabro.servicio.application.panels.workorder.WorkOrderInfoPanel;
import tr.cabro.servicio.application.panels.workorder.WorkOrderItemsPanel;
import tr.cabro.servicio.application.panels.workorder.WorkOrderPaymentsPanel;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.DocumentExportModal;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.NewCustomerModal;
import tr.cabro.servicio.application.component.Badge;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.application.component.detail.DetailHeader;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.documents.ServiceFormType;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.model.enums.TemplateType;
import tr.cabro.servicio.service.*;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.TemplateEngine;

import javax.swing.*;
import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * İş emri detay ekranı — orkestratör. Düzen "tezgâh": solda geniş sütun işin sırasını izler
 * (1 arıza ve tespit, 2 parça ve işçilik, 3 ödemeler; her adımın numarası adım bitince onaya
 * döner), sağda gövdeyle kaymayan ince ray tutarı ve dosyayı (müşteri, cihaz, zaman) hep
 * gösterir. Paneller {@code application.panels.workorder} paketinde; burada kimlik şeridi,
 * PDF belge üretimi, WhatsApp mesajı ve panellerin kuruluşu/bağlanması kalıyor.
 */
public class FormWorkOrder extends Form {

    private WorkOrder workOrder;
    private final WorkOrderService workOrderService;

    // --- Kimlik şeridi ---
    private DetailHeader header;
    private StatusButton statusButton;
    private Badge urgentBadge;
    private JButton btnWhatsApp;

    // --- Gövde: solda adımlar (arıza/tespit, kalemler, ödemeler), sağda sabit ray (tutar, dosya, zaman) ---
    private WorkOrderInfoPanel infoPanel;
    private JPanel workColumn;
    private WorkOrderDiagnosisPanel diagnosisPanel;
    private WorkOrderItemsPanel itemsPanel;
    private WorkOrderPaymentsPanel paymentsPanel;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public FormWorkOrder(WorkOrder workOrder) {
        this.workOrderService = ServiceManager.getWorkOrderService();
        initComponent(workOrder);
        setService(workOrder);
    }

    public void setService(@NonNull WorkOrder workOrder) {
        this.workOrder = workOrder;
        hydrateHeader();
        infoPanel.refresh(workOrder);
        buildWorkArea();
    }

    @Override
    public void formRefresh() {
        workOrderService.get(workOrder.getId()).thenAccept(woOpt -> {
            if (!woOpt.isPresent()) {
                SwingUtilities.invokeLater(() ->
                        Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.workorder.refreshFailedNotFound")));
                return;
            }
            workOrder = woOpt.get();
            SwingUtilities.invokeLater(() -> {
                hydrateHeader();
                infoPanel.refresh(workOrder);
                buildWorkArea();
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Servis kaydı yenilenemedi", ex));
    }

    // =========================================================================
    // INIT COMPONENT
    // =========================================================================

    private void initComponent(WorkOrder initialWorkOrder) {
        setLayout(new MigLayout("fill, insets 20, gap 16", "[grow, fill]", "[pref][grow, fill]"));
        createHeaderPanel();

        workColumn = new JPanel(new MigLayout("insets 0, wrap, fillx, gapy 16", "[grow, fill]", ""));
        workColumn.setOpaque(false);

        // Ray gövdeyle kaymaz: tutar ve dosya, iş aşağıda sürerken de görünür kalır.
        infoPanel = new WorkOrderInfoPanel(initialWorkOrder, () -> {
            if (paymentsPanel != null) paymentsPanel.focusAmount();
        });

        JPanel content = new JPanel(new MigLayout("insets 0, fill, gap 16", "[grow, fill][320!, fill]", "[grow, fill]"));
        content.setOpaque(false);
        content.add(DetailKit.scroll(workColumn), "wmin 0, hmin 0");
        content.add(DetailKit.scroll(infoPanel), "hmin 0");
        add(content, "grow, hmin 0");
    }

    // =========================================================================
    // KİMLİK ŞERİDİ
    // =========================================================================

    private void createHeaderPanel() {
        header = new DetailHeader(() -> FormManager.showForm(AllForms.getForm(FormWorkOrders.class)));

        statusButton = new StatusButton(this::changeStatus);
        urgentBadge = new Badge(new tr.cabro.servicio.model.contract.Visualizable() {
            @Override public String getDisplayName() { return "Acil"; }
            @Override public String getIconPath() { return "icons/triangle-alert.svg"; }
            @Override public tr.cabro.servicio.model.enums.BadgeColor getBadgeColor() { return tr.cabro.servicio.model.enums.BadgeColor.RED; }
        }).setShowIcon(true);
        header.addBadge(statusButton);
        header.addBadge(urgentBadge);

        JButton btnGenerateDoc = header.addAction("Belge", "icons/file-text.svg", null);
        btnGenerateDoc.setToolTipText("Servis formu, teklif, fiş…");
        btnGenerateDoc.addActionListener(e -> showDocumentMenu(btnGenerateDoc));
        btnWhatsApp = header.addAction("WhatsApp", "icons/message-circle.svg", this::openWhatsAppModal);
        header.addAction("Düzenle", "icons/pencil.svg", this::openEditModal);
        header.addActionComponent(buildDeleteButton());
        header.setPrimary("Parça / İşçilik Ekle", "icons/plus.svg", () -> {
            if (itemsPanel != null) itemsPanel.openItemAddModal();
        });

        add(header, "growx, wmin 0, wrap");
    }

    /** Yalnızca ikon: silme sık bir işlem değil, adı ipucunda; ikon tehlike renginde. */
    private JButton buildDeleteButton() {
        JButton b = new JButton(new Ikon("icons/trash-2.svg", 16, "Servicio.dangerColor"));
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 7,10,7,10");
        b.setToolTipText("Servis kaydını sil");
        b.getAccessibleContext().setAccessibleName("Servis kaydını sil");
        b.addActionListener(e -> confirmDelete());
        return b;
    }

    /** Listedeki "Düzenle" ile aynı kayıt formu ({@link QuickIntakePanel}); kaydedince sayfa yenilenir. */
    private void openEditModal() {
        final String modalId = "service_edit_modal";
        QuickIntakePanel[] ref = new QuickIntakePanel[1];
        ref[0] = new QuickIntakePanel(workOrder, () -> NewCustomerModal.push(modalId, c -> ref[0].appendNewCustomer(c)));
        QuickIntakePanel panel = ref[0];
        panel.setPrimaryModalAction(SimpleModalBorder.YES_OPTION);
        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Değişiklikleri Kaydet", SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };
        AppModal.showModal(this, new SimpleModalBorder(panel, "Kayıt Düzenle (SRV-" + workOrder.getId() + ")", options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) {
                panel.requestInitialFocus();
                return;
            }
            if (action != SimpleModalBorder.YES_OPTION) return;
            WorkOrder formData = panel.getData();
            if (formData == null) { controller.consume(); return; }
            workOrderService.save(formData, true).thenCompose(saved ->
                    ServiceManager.getDeviceAccessCredentialService()
                            .save(saved.getId(), panel.getDeviceAccessType(), panel.getDeviceAccessSecret())
                            .thenApply(v -> saved)
            ).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.workorder.updated"));
                formRefresh();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(this, "Servis kaydı kaydedilemedi", ex);
            });
        }), modalId);
    }

    private void confirmDelete() {
        DialogHelper.confirmDelete(this, "confirm.delete.workorder", () ->
                workOrderService.delete(workOrder.getId())
                        .thenAccept(v -> SwingUtilities.invokeLater(() -> {
                            Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.record.deleted"));
                            FormManager.showForm(AllForms.getForm(FormWorkOrders.class));
                        }))
                        .exceptionally(ex -> ErrorHandler.handle(this, "Servis kaydı silinemedi", ex)),
                workOrder.getId());
    }

    /** Durum menüsünden seçilen yeni durumu kaydeder; hata olursa rozet eski durumda kalır. */
    private void changeStatus(ServiceStatus newStatus) {
        if (newStatus == null || workOrder == null || workOrder.getServiceStatus() == newStatus) return;
        workOrderService.updateStatus(workOrder.getId(), newStatus)
                .thenAccept(unused -> SwingUtilities.invokeLater(() -> {
                    workOrder.setServiceStatus(newStatus);
                    workOrder.setStatusChangedAt(java.time.LocalDateTime.now());
                    statusButton.setStatus(newStatus);
                    infoPanel.refresh(workOrder);
                    tr.cabro.servicio.util.SoundPlayer.statusChanged();
                }))
                .exceptionally(ex -> ErrorHandler.handle(this, "Servis durumu güncellenemedi", ex));
    }

    private void hydrateHeader() {
        header.setTitle("SRV-" + workOrder.getId());

        ServiceStatus currentStatus = workOrder.getServiceStatus() != null
                ? workOrder.getServiceStatus() : ServiceStatus.UNDER_REPAIR;
        statusButton.setStatus(currentStatus);
        urgentBadge.setVisible("URGENT".equalsIgnoreCase(workOrder.getUrgencyStatus()));

        Customer c = workOrder.getCustomer();
        Device d = workOrder.getDevice();
        String since = null;
        if (workOrder.getCreatedAt() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(workOrder.getCreatedAt().toLocalDate(),
                    workOrder.getDeliveryDate() != null ? workOrder.getDeliveryDate().toLocalDate() : java.time.LocalDate.now());
            // Tarihlerin tamamı raydaki zaman kartında; şeritte kısa özet kalır ki tek satıra sığsın.
            since = workOrder.getDeliveryDate() != null
                    ? "Teslim " + workOrder.getDeliveryDate().format(DateFormats.shortDate())
                    : (days <= 0 ? "Bugün geldi" : days + " gündür serviste");
        }
        header.setMeta(c != null ? c.getFullName() : "Müşterisiz kayıt",
                d != null ? d.getBrand() + " " + d.getModel() : null,
                since);

        boolean hasPhone = c != null && c.getPhoneNumber1() != null && !c.getPhoneNumber1().isBlank();
        btnWhatsApp.setEnabled(hasPhone);
        btnWhatsApp.setToolTipText(hasPhone ? "Müşteriye şablonlu WhatsApp mesajı gönder" : "Müşterinin telefonu kayıtlı değil");
    }

    /** Kalem ya da ödeme değişince: raydaki tutarlar ve adım işaretleri yeniden okunur. */
    private void hydrateMoney() {
        infoPanel.refreshMoney();
        refreshSteps();
    }

    /** Adım işaretleri: tespit yazıldıysa 1, kalem varsa 2, ücret tamamen tahsil edildiyse 3 biter. */
    private void refreshSteps() {
        if (diagnosisPanel != null) {
            String detected = workOrder.getDetectedFault();
            diagnosisPanel.getStepMarker().setDone(detected != null && !detected.isBlank());
        }
        if (itemsPanel != null) {
            itemsPanel.getStepMarker().setDone(workOrder.getItems() != null && !workOrder.getItems().isEmpty());
        }
        if (paymentsPanel != null) {
            paymentsPanel.getStepMarker().setDone(workOrder.getTotalServiceAmount().signum() > 0
                    && workOrder.getRemainingAmount().signum() <= 0);
        }
    }

    // =========================================================================
    // BELGE (PDF) OLUŞTURMA
    // =========================================================================

    private void showDocumentMenu(JButton anchor) {
        JPopupMenu popup = new JPopupMenu();
        boolean thermalGroupStarted = false;
        for (ServiceFormType type : ServiceFormType.values()) {
            if (type.isThermal() && !thermalGroupStarted) {
                // A4 belgeler üstte, termal fişler ayraçtan sonra kendi başlığı altında.
                popup.addSeparator();
                JLabel groupLabel = new JLabel("Termal Fiş (" + AppSettings.get().getPrinting().getReceiptPaperWidth() + ")");
                groupLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
                groupLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 2, 10));
                popup.add(groupLabel);
                thermalGroupStarted = true;
            }
            JMenuItem item = new JMenuItem(type.getDisplayName());
            item.addActionListener(e -> openDocumentModal(type));
            popup.add(item);
        }
        popup.show(anchor, 0, anchor.getHeight());
    }

    /** İmza isimleri, garanti ve metinler düzenlenip belge açılır ya da farklı kaydedilir. */
    private void openDocumentModal(ServiceFormType type) {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> SwingUtilities.invokeLater(() -> {
            User shop = shopOpt.orElse(null);
            DocumentExportModal.Spec spec = new DocumentExportModal.Spec(
                    type.getDisplayName(), type.getFileSlug() + "-SRV" + workOrder.getId(),
                    type.getLeftSignerLabel(), defaultSignerName(type.getLeftSignerLabel(), shop),
                    type.getRightSignerLabel(), defaultSignerName(type.getRightSignerLabel(), shop),
                    type.hasWarrantyDays(), type.getEditableTexts(), type.getSupportedFormats());
            DocumentExportModal.show(this, spec,
                    (request, format, outFile) -> type.generate(workOrder, shop, request, format, outFile));
        })).exceptionally(ex -> ErrorHandler.handle(this, "Belge penceresi açılamadı", ex));
    }

    /**
     * İmza etiketi "İşletme" içeriyorsa işletme adını, "Müşteri" içeriyorsa müşteri adını
     * varsayılan olarak doldurur — kullanıcı isterse üzerine yazabilir.
     */
    private String defaultSignerName(String signerLabel, User shop) {
        if (signerLabel == null) return "";
        if (signerLabel.contains("İşletme") && shop != null && shop.getBusinessName() != null) {
            return shop.getBusinessName();
        }
        if (signerLabel.contains("Müşteri") && workOrder.getCustomer() != null) {
            return workOrder.getCustomer().getFullName();
        }
        return "";
    }

    // =========================================================================
    // WHATSAPP MESAJ GÖNDERME
    // =========================================================================

    private void openWhatsAppModal() {
        if (workOrder.getCustomer() == null || workOrder.getCustomer().getPhoneNumber1() == null) return;

        CompletableFuture<List<DocumentTemplate>> templatesFuture =
                ServiceManager.getDocumentTemplateService().getByType(TemplateType.WHATSAPP_MESSAGE);
        CompletableFuture<Optional<User>> shopFuture = ServiceManager.getUserService().get(1L);

        CompletableFuture.allOf(templatesFuture, shopFuture).thenAccept(v -> {
            List<DocumentTemplate> templates = templatesFuture.join();
            User shop = shopFuture.join().orElse(null);
            Map<String, String> tokens = TemplateTokenBuilder.fromWorkOrder(workOrder, shop);

            SwingUtilities.invokeLater(() -> {
                if (templates.isEmpty()) {
                    Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.whatsapp.noTemplate"));
                    return;
                }

                JPanel panel = new JPanel(new MigLayout("fillx, wrap, insets 10, width 400", "[fill,grow]", "[][][]"));
                panel.add(new JLabel("Mesaj şablonu:"));
                JComboBox<DocumentTemplate> combo = new JComboBox<>(templates.toArray(new DocumentTemplate[0]));
                panel.add(combo, "growx");

                panel.add(new JLabel("Mesaj (göndermeden önce düzenleyebilirsiniz):"));
                JTextArea txtMessage = new JTextArea(6, 0);
                txtMessage.setLineWrap(true);
                txtMessage.setWrapStyleWord(true);
                DocumentTemplate initial = (DocumentTemplate) combo.getSelectedItem();
                if (initial != null) txtMessage.setText(TemplateEngine.render(initial.getBody(), tokens));
                panel.add(new JScrollPane(txtMessage), "growx");

                combo.addActionListener(e -> {
                    DocumentTemplate selected = (DocumentTemplate) combo.getSelectedItem();
                    if (selected != null) txtMessage.setText(TemplateEngine.render(selected.getBody(), tokens));
                });

                SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                        new SimpleModalBorder.Option("Gönder", SimpleModalBorder.YES_OPTION),
                        new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
                };

                AppModal.showModal(this, new SimpleModalBorder(panel, "WhatsApp Mesaj Gönder", options, (controller, action) -> {
                    if (action != SimpleModalBorder.YES_OPTION) return;

                    String digits = PhoneHelper.toWhatsAppDigits(workOrder.getCustomer().getPhoneNumber1());
                    if (digits == null) {
                        controller.consume();
                        Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.whatsapp.noPhone"));
                        return;
                    }

                    String url = "https://wa.me/" + digits + "?text=" + URLEncoder.encode(txtMessage.getText(), StandardCharsets.UTF_8);
                    if (!DesktopHelper.browseUrl(url)) {
                        Toasts.show(this, Toast.Type.ERROR, Messages.get("toast.whatsapp.openFailed"));
                    }
                }), "whatsapp_message_modal");
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "WhatsApp şablonları yüklenemedi", ex));
    }

    // =========================================================================
    // ÇALIŞMA ALANI (1 arıza/tespit, 2 kalemler, 3 ödemeler)
    // =========================================================================

    private void buildWorkArea() {
        workColumn.removeAll();

        diagnosisPanel = new WorkOrderDiagnosisPanel(workOrder, this::refreshSteps);
        paymentsPanel = new WorkOrderPaymentsPanel(workOrder);
        paymentsPanel.setOnChanged(this::hydrateMoney);
        itemsPanel = new WorkOrderItemsPanel(workOrder, paymentsPanel::refresh);

        workColumn.add(diagnosisPanel, "growx");
        workColumn.add(itemsPanel, "growx");
        workColumn.add(paymentsPanel, "growx");
        refreshSteps();
        workColumn.revalidate();
        workColumn.repaint();
    }

    // =========================================================================
    // DURUM ROZETİ (tıklanınca durum menüsü)
    // =========================================================================

    /**
     * Kimlik şeridindeki durum: rozet görünümünde düğme (durum ikonu + ad + aşağı ok). Tıklanınca
     * durum listesini açar; eskiden ayrı bir açılır kutu şeridi genişletip işlemleri ikinci satıra
     * itiyordu. Renkler {@link BadgePalette}'ten; tema değişince {@link #updateUI()} yeniden okur.
     */
    private static final class StatusButton extends JButton {
        private static final int CHEVRON = 12;
        private ServiceStatus status = ServiceStatus.UNDER_REPAIR;

        StatusButton(java.util.function.Consumer<ServiceStatus> onSelect) {
            setToolTipText("Servis durumunu değiştir");
            getAccessibleContext().setAccessibleName("Servis durumu");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusPainted(false);
            addActionListener(e -> {
                JPopupMenu menu = new JPopupMenu();
                for (ServiceStatus st : ServiceStatus.values()) {
                    JMenuItem item = new JMenuItem(st.getDisplayName(), badgeIcon(st, 16));
                    if (st == status) item.putClientProperty(FlatClientProperties.STYLE, "font: bold");
                    item.addActionListener(ev -> onSelect.accept(st));
                    menu.add(item);
                }
                menu.show(this, 0, getHeight() + 4);
            });
            applyStyle();
        }

        void setStatus(ServiceStatus status) {
            this.status = status;
            applyStyle();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (status != null) applyStyle();
        }

        private void applyStyle() {
            String bg = BadgePalette.backgroundHex(status.getBadgeColor());
            setText(status.getDisplayName());
            setIcon(badgeIcon(status, 14));
            setIconTextGap(6);
            // Yandaki "Acil" rozetiyle aynı boy ve yuvarlaklık; sağda ok için yer bırakılır.
            putClientProperty(FlatClientProperties.STYLE, BadgePalette.style(status.getBadgeColor(),
                    "arc: 999; border: 2,10,2," + (CHEVRON + 16) + "; font: bold +0;"
                            + " hoverBackground: darken(" + bg + ",5%); pressedBackground: darken(" + bg + ",10%)"));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (status == null) return;
            Ikon chevron = new Ikon("icons/chevron-down.svg", CHEVRON);
            chevron.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(
                    col -> BadgePalette.foreground(status.getBadgeColor())));
            chevron.paintIcon(this, g, getWidth() - CHEVRON - 10, (getHeight() - CHEVRON) / 2);
        }

        private static Icon badgeIcon(ServiceStatus st, int size) {
            Ikon icon = new Ikon(st.getIconPath(), size);
            icon.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(
                    col -> BadgePalette.foreground(st.getBadgeColor())));
            return icon;
        }
    }
}
