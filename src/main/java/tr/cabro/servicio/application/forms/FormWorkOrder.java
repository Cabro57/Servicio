package tr.cabro.servicio.application.forms;

import tr.cabro.servicio.application.panels.workorder.WorkOrderDeleteDialog;
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
import tr.cabro.servicio.application.panels.workorder.WorkOrderStatusDialogs;
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
    private Badge statusBadge;
    private Badge urgentBadge;
    private JButton btnWhatsApp;
    private JButton btnStatus;
    private JButton btnEdit;
    private JButton btnPrimary;

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
        }, this::formRefresh);

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

        statusBadge = new Badge(ServiceStatus.ACCEPTED).setShowIcon(true);
        urgentBadge = new Badge(new tr.cabro.servicio.model.contract.Visualizable() {
            @Override public String getDisplayName() { return "Acil"; }
            @Override public String getIconPath() { return "icons/triangle-alert.svg"; }
            @Override public tr.cabro.servicio.model.enums.BadgeColor getBadgeColor() { return tr.cabro.servicio.model.enums.BadgeColor.RED; }
        }).setShowIcon(true);
        header.addBadge(statusBadge);
        header.addBadge(urgentBadge);

        // Açık kayıtta durum penceresini açar; teslim/iade edilmiş kayıtta aynı yerde "geri al" olur.
        btnStatus = header.addAction("Durum", "icons/arrow-right.svg", this::onStatusAction);
        JButton btnGenerateDoc = header.addAction("Belge", "icons/file-text.svg", null);
        btnGenerateDoc.setToolTipText("Servis formu, teklif, fiş…");
        btnGenerateDoc.addActionListener(e -> showDocumentMenu(btnGenerateDoc));
        btnWhatsApp = header.addAction("WhatsApp", "icons/message-circle.svg", this::openWhatsAppModal);
        btnEdit = header.addAction("Düzenle", "icons/pencil.svg", this::openEditModal);
        header.addActionComponent(buildDeleteButton());
        btnPrimary = header.setPrimary("Parça / İşçilik Ekle", "icons/plus.svg", () -> {
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
                Toasts.saved(this, Messages.get("toast.workorder.updated"));
                formRefresh();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(this, "Servis kaydı kaydedilemedi", ex);
            });
        }), modalId);
    }

    private void confirmDelete() {
        WorkOrderDeleteDialog.confirm(this, workOrder,
                () -> FormManager.showForm(AllForms.getForm(FormWorkOrders.class)));
    }

    /** Kimlik şeridindeki durum düğmesi: açık kayıtta durum penceresi, kapalı kayıtta geri alma. */
    private void onStatusAction() {
        ServiceStatus current = workOrder.getServiceStatus() != null ? workOrder.getServiceStatus() : ServiceStatus.ACCEPTED;
        if (current.isClosed()) reopen();
        else changeStatus(current);
    }

    /**
     * Yeni durum ve geçiş tarihi tek pencerede seçilir; teslim ve iade ayrıca kaydın kilitleneceğini
     * söyler. Kaydedilince sayfa yeniden okunur (kilit, tarihler).
     */
    private void changeStatus(ServiceStatus previous) {
        WorkOrderStatusDialogs.changeStatus(this, workOrder.getId(), previous, workOrder.getStatusChangedAt(), newStatus -> {
            tr.cabro.servicio.util.SoundPlayer.statusChanged();
            notifyStockMove(newStatus == ServiceStatus.RETURN, previous == ServiceStatus.RETURN);
            formRefresh();
        });
    }

    /** Teslim ya da iade geri alınır; kayıt bir önceki durumuna döner ve kilidi açılır. */
    private void reopen() {
        ServiceStatus closed = workOrder.getServiceStatus();
        if (closed == null || !closed.isClosed()) return;
        WorkOrderStatusDialogs.reopen(this, workOrder.getId(), closed, previous -> {
            tr.cabro.servicio.util.SoundPlayer.statusChanged();
            notifyStockMove(false, closed == ServiceStatus.RETURN);
            Toasts.show(this, Toast.Type.SUCCESS, "Kayıt yeniden açıldı: " + previous.getDisplayName());
            formRefresh();
        });
    }

    /** "İade" parçaları stoğa döndürür, geri açmak yeniden düşer (WorkOrderService.syncStock). */
    private void notifyStockMove(boolean returnedToStock, boolean takenFromStock) {
        boolean hasParts = workOrder.getItems() != null && workOrder.getItems().stream()
                .anyMatch(i -> i.getItemType() == tr.cabro.servicio.model.enums.ItemType.PART && i.getPartId() != null);
        if (!hasParts || returnedToStock == takenFromStock) return;
        Toasts.show(this, Toast.Type.INFO, returnedToStock
                ? "Servisteki parçalar stoğa geri döndü." : "Servisteki parçalar yeniden stoktan düşüldü.");
    }

    private void hydrateHeader() {
        header.setTitle("SRV-" + workOrder.getId());

        ServiceStatus currentStatus = workOrder.getServiceStatus() != null
                ? workOrder.getServiceStatus() : ServiceStatus.ACCEPTED;
        boolean closed = currentStatus.isClosed();
        statusBadge.setVisualizable(currentStatus);
        urgentBadge.setVisible("URGENT".equalsIgnoreCase(workOrder.getUrgencyStatus()));

        Customer c = workOrder.getCustomer();
        Device d = workOrder.getDevice();
        String since = null;
        java.time.LocalDateTime received = workOrder.getReceivedAt() != null ? workOrder.getReceivedAt() : workOrder.getCreatedAt();
        if (received != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(received.toLocalDate(),
                    workOrder.getDeliveryDate() != null ? workOrder.getDeliveryDate().toLocalDate() : java.time.LocalDate.now());
            // Tarihlerin tamamı raydaki durum geçmişinde; şeritte kısa özet kalır ki tek satıra sığsın.
            String closedWord = currentStatus == ServiceStatus.RETURN ? "İade " : "Teslim ";
            since = workOrder.getDeliveryDate() != null
                    ? closedWord + workOrder.getDeliveryDate().format(DateFormats.shortDate())
                    : (days <= 0 ? "Bugün geldi" : days + " gündür serviste");
        }
        header.setMeta(c != null ? c.getFullName() : "Müşterisiz kayıt",
                d != null ? d.getBrand() + " " + d.getModel() : null,
                since);

        applyLock(currentStatus);

        boolean hasPhone = c != null && c.getPhoneNumber1() != null && !c.getPhoneNumber1().isBlank();
        btnWhatsApp.setEnabled(hasPhone);
        btnWhatsApp.setToolTipText(hasPhone ? "Müşteriye şablonlu WhatsApp mesajı gönder" : "Müşterinin telefonu kayıtlı değil");
    }

    /**
     * Teslim/iade edilmiş kayıt kilitlidir: kalem ekleme (birincil) ve düzenleme kapanır, şeridin
     * altında kilidin nedeni yazar ve "geri al" belirir. Ödeme alma ve belge açıktır.
     */
    private void applyLock(ServiceStatus status) {
        boolean closed = status.isClosed();
        boolean returned = status == ServiceStatus.RETURN;
        btnPrimary.setVisible(!closed);
        btnEdit.setEnabled(!closed);
        btnEdit.setToolTipText(closed ? "Kayıt kilitli; düzenlemek için önce " + (returned ? "iadeyi" : "teslimi") + " geri alın" : null);
        btnStatus.setText(closed ? (returned ? "İadeyi geri al" : "Teslimi geri al") : "Durum");
        btnStatus.setIcon(new Ikon(closed ? "icons/undo-2.svg" : "icons/arrow-right.svg", 16, "Label.foreground"));
        btnStatus.setToolTipText(closed ? "Kaydı bir önceki durumuna döndürür ve kilidi açar" : "Servis durumunu değiştir");
        if (closed) {
            String when = workOrder.getDeliveryDate() != null
                    ? " " + workOrder.getDeliveryDate().format(DateFormats.dateTime()) : "";
            header.setNotice((returned ? "İade edildi" : "Teslim edildi") + when
                            + "  ·  Kayıt kilitli: parça, işçilik, not ve tespit değiştirilemez. Ödeme alınabilir.",
                    null, "icons/lock.svg", "Label.disabledForeground", false);
        } else {
            header.setNotice(null, null, "icons/lock.svg", "Label.disabledForeground", false);
        }
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
        ServiceManager.getBusinessService().get().thenAccept(shopOpt -> SwingUtilities.invokeLater(() -> {
            Business shop = shopOpt.orElse(null);
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
    private String defaultSignerName(String signerLabel, Business shop) {
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
        CompletableFuture<Optional<Business>> shopFuture = ServiceManager.getBusinessService().get();

        CompletableFuture.allOf(templatesFuture, shopFuture).thenAccept(v -> {
            List<DocumentTemplate> templates = templatesFuture.join();
            Business shop = shopFuture.join().orElse(null);
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

        boolean locked = workOrder.getServiceStatus() != null && workOrder.getServiceStatus().isClosed();
        diagnosisPanel = new WorkOrderDiagnosisPanel(workOrder, locked, this::refreshSteps);
        paymentsPanel = new WorkOrderPaymentsPanel(workOrder);
        paymentsPanel.setOnChanged(this::hydrateMoney);
        itemsPanel = new WorkOrderItemsPanel(workOrder, locked, paymentsPanel::refresh);

        workColumn.add(diagnosisPanel, "growx");
        workColumn.add(itemsPanel, "growx");
        workColumn.add(paymentsPanel, "growx");
        refreshSteps();
        workColumn.revalidate();
        workColumn.repaint();
    }
}
