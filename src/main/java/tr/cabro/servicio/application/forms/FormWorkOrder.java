package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.panels.workorder.WorkOrderInfoPanel;
import tr.cabro.servicio.application.panels.workorder.WorkOrderItemsPanel;
import tr.cabro.servicio.application.panels.workorder.WorkOrderNotesPanel;
import tr.cabro.servicio.application.panels.workorder.WorkOrderPaymentsPanel;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.DocumentExportModal;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.component.Badge;
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
import java.awt.event.ActionListener;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * İş emri detay ekranı — orkestratör. Eskiden 1254 satırlık tek bir sınıftı; parça/ödeme/not/
 * müşteri-cihaz-zaman-çizelgesi kartları artık {@code application.panels.workorder} paketindeki
 * ayrı panellere taşındı (bkz. {@link WorkOrderItemsPanel}, {@link WorkOrderPaymentsPanel},
 * {@link WorkOrderNotesPanel}, {@link WorkOrderInfoPanel}). Burada sadece başlık/durum çubuğu,
 * PDF belge üretimi ve WhatsApp mesaj gönderimi (form-seviyesi aksiyonlar) ile panellerin
 * kuruluşu/bağlanması kalıyor.
 */
public class FormWorkOrder extends Form {

    private WorkOrder workOrder;
    private final WorkOrderService workOrderService;

    // --- Kimlik şeridi ---
    private DetailHeader header;
    private Badge statusBadge;
    private Badge urgentBadge;
    private JComboBox<ServiceStatus> statusComboBox;
    private JButton btnWhatsApp;

    // --- Gövde: solda çalışma alanı (kalemler + ödemeler), sağda dosya (müşteri, cihaz, notlar) ---
    private WorkOrderInfoPanel infoPanel;
    private JPanel workColumn;
    private JPanel notesHolder;
    private WorkOrderItemsPanel itemsPanel;

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
                        Toast.show(this, Toast.Type.WARNING, Messages.get("toast.workorder.refreshFailedNotFound")));
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

        infoPanel = new WorkOrderInfoPanel(initialWorkOrder, this::openWhatsAppModal);
        notesHolder = new JPanel(new MigLayout("insets 0, fill", "[grow, fill]", "[]"));
        notesHolder.setOpaque(false);
        JPanel side = new JPanel(new MigLayout("insets 0, wrap, fillx, gapy 16", "[grow, fill]", ""));
        side.setOpaque(false);
        side.add(infoPanel);
        side.add(notesHolder);

        JPanel content = new JPanel(new MigLayout("insets 0, fillx, gap 16", "[grow, fill][360!, fill]", "[top]"));
        content.setOpaque(false);
        content.add(workColumn, "wmin 0");
        content.add(side);
        add(DetailKit.scroll(content), "grow, hmin 0");
    }

    // =========================================================================
    // KİMLİK ŞERİDİ
    // =========================================================================

    private void createHeaderPanel() {
        header = new DetailHeader(() -> FormManager.showForm(AllForms.getForm(FormWorkOrders.class)));

        statusBadge = new Badge(ServiceStatus.UNDER_REPAIR);
        statusBadge.setShowIcon(true);
        urgentBadge = new Badge(new tr.cabro.servicio.model.contract.Visualizable() {
            @Override public String getDisplayName() { return "Acil"; }
            @Override public String getIconPath() { return "icons/triangle-alert.svg"; }
            @Override public tr.cabro.servicio.model.enums.BadgeColor getBadgeColor() { return tr.cabro.servicio.model.enums.BadgeColor.RED; }
        }).setShowIcon(true);
        header.addBadge(statusBadge);
        header.addBadge(urgentBadge);

        header.addStat("total", "Toplam");
        header.addStat("paid", "Ödenen");
        header.addStat("remaining", "Kalan");

        statusComboBox = new JComboBox<>(ServiceStatus.values());
        statusComboBox.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        statusComboBox.setToolTipText("Servis durumunu değiştir");
        statusComboBox.getAccessibleContext().setAccessibleName("Servis durumu");
        statusComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ServiceStatus) setText(((ServiceStatus) value).getDisplayName());
                return this;
            }
        });
        statusComboBox.addActionListener(e -> {
            ServiceStatus newStatus = (ServiceStatus) statusComboBox.getSelectedItem();
            if (newStatus == null || workOrder == null || workOrder.getServiceStatus() == newStatus) return;

            ServiceStatus oldStatus = workOrder.getServiceStatus();

            workOrderService.updateStatus(workOrder.getId(), newStatus)
                    .thenAccept(unused -> {
                        SwingUtilities.invokeLater(() -> statusBadge.setVisualizable(newStatus));
                        workOrder.setServiceStatus(newStatus);
                        workOrder.setStatusChangedAt(java.time.LocalDateTime.now());
                        SwingUtilities.invokeLater(() -> infoPanel.refresh(workOrder));
                    }).exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> {
                            ActionListener[] ls = statusComboBox.getActionListeners();
                            for (ActionListener l : ls) statusComboBox.removeActionListener(l);
                            statusComboBox.setSelectedItem(oldStatus);
                            for (ActionListener l : ls) statusComboBox.addActionListener(l);
                        });
                        return ErrorHandler.handle(this, "Servis durumu güncellenemedi", ex);
                    });
        });
        header.addActionComponent(statusComboBox);

        JButton btnGenerateDoc = header.addAction("Belge", "icons/file-text.svg", null);
        btnGenerateDoc.setToolTipText("Servis formu, teklif, fiş…");
        btnGenerateDoc.addActionListener(e -> showDocumentMenu(btnGenerateDoc));
        btnWhatsApp = header.addAction("WhatsApp", "icons/message-circle.svg", this::openWhatsAppModal);
        header.setPrimary("Parça / İşçilik Ekle", "icons/plus.svg", () -> {
            if (itemsPanel != null) itemsPanel.openItemAddModal();
        });

        add(header, "growx, wmin 0, wrap");
    }

    private void hydrateHeader() {
        header.setTitle("SRV-" + workOrder.getId());

        ServiceStatus currentStatus = workOrder.getServiceStatus() != null
                ? workOrder.getServiceStatus() : ServiceStatus.UNDER_REPAIR;
        statusBadge.setVisualizable(currentStatus);
        urgentBadge.setVisible("URGENT".equalsIgnoreCase(workOrder.getUrgencyStatus()));

        Customer c = workOrder.getCustomer();
        Device d = workOrder.getDevice();
        String since = null;
        if (workOrder.getCreatedAt() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(workOrder.getCreatedAt().toLocalDate(),
                    workOrder.getDeliveryDate() != null ? workOrder.getDeliveryDate().toLocalDate() : java.time.LocalDate.now());
            since = "Geliş " + workOrder.getCreatedAt().format(DateFormats.shortDate())
                    + (workOrder.getDeliveryDate() != null ? ", teslim " + workOrder.getDeliveryDate().format(DateFormats.shortDate())
                    : (days <= 0 ? ", bugün geldi" : ", " + days + " gündür serviste"));
        }
        header.setMeta(c != null ? c.getFullName() : "Müşterisiz kayıt",
                d != null ? d.getBrand() + " " + d.getModel() : null,
                since);

        boolean hasPhone = c != null && c.getPhoneNumber1() != null && !c.getPhoneNumber1().isBlank();
        btnWhatsApp.setEnabled(hasPhone);
        btnWhatsApp.setToolTipText(hasPhone ? "Müşteriye şablonlu WhatsApp mesajı gönder" : "Müşterinin telefonu kayıtlı değil");

        ActionListener[] listeners = statusComboBox.getActionListeners();
        for (ActionListener l : listeners) statusComboBox.removeActionListener(l);
        statusComboBox.setSelectedItem(currentStatus);
        for (ActionListener l : listeners) statusComboBox.addActionListener(l);

        hydrateMoney();
    }

    /** Toplam / ödenen / kalan: kalem ya da ödeme değişince yeniden okunur. */
    private void hydrateMoney() {
        java.math.BigDecimal total = workOrder.getTotalServiceAmount();
        java.math.BigDecimal paid = workOrder.getTotalPaid();
        java.math.BigDecimal remaining = workOrder.getRemainingAmount();
        header.setStat("total", tr.cabro.servicio.util.Format.formatPrice(total), null);
        header.setStat("paid", tr.cabro.servicio.util.Format.formatPrice(paid), paid.signum() > 0 ? "Servicio.successColor" : null);
        header.setStat("remaining", remaining.signum() > 0 ? tr.cabro.servicio.util.Format.formatPrice(remaining)
                        : (total.signum() > 0 ? "Ödendi" : "—"),
                remaining.signum() > 0 ? "Servicio.warningColor" : "Label.disabledForeground");
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
                    Toast.show(this, Toast.Type.WARNING, Messages.get("toast.whatsapp.noTemplate"));
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
                        Toast.show(this, Toast.Type.WARNING, Messages.get("toast.whatsapp.noPhone"));
                        return;
                    }

                    String url = "https://wa.me/" + digits + "?text=" + URLEncoder.encode(txtMessage.getText(), StandardCharsets.UTF_8);
                    if (!DesktopHelper.browseUrl(url)) {
                        Toast.show(this, Toast.Type.ERROR, Messages.get("toast.whatsapp.openFailed"));
                    }
                }), "whatsapp_message_modal");
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "WhatsApp şablonları yüklenemedi", ex));
    }

    // =========================================================================
    // ÇALIŞMA ALANI (Parça/Ödeme/Not panelleri)
    // =========================================================================

    private void buildWorkArea() {
        workColumn.removeAll();
        notesHolder.removeAll();

        WorkOrderPaymentsPanel paymentsPanel = new WorkOrderPaymentsPanel(workOrder);
        paymentsPanel.setOnChanged(this::hydrateMoney);
        itemsPanel = new WorkOrderItemsPanel(workOrder, paymentsPanel::refresh);
        WorkOrderNotesPanel notesPanel = new WorkOrderNotesPanel(workOrder);

        workColumn.add(itemsPanel, "growx");
        workColumn.add(paymentsPanel, "growx");
        notesHolder.add(notesPanel, "growx");
        workColumn.revalidate();
        workColumn.repaint();
        notesHolder.revalidate();
    }
}
