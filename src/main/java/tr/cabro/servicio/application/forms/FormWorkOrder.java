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
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.Badge;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.documents.ServiceFormType;
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
import java.io.File;
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

    // --- Header ---
    private JLabel lblHeaderTitle;
    private JLabel lblHeaderSubtitle;
    private Badge lblHeaderBadge;
    private JComboBox<ServiceStatus> statusComboBox;

    // --- Layout ---
    private WorkOrderInfoPanel infoPanel;
    private JPanel rightColumn;

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
        buildRightColumn();
    }

    @Override
    public void formRefresh() {
        workOrderService.get(workOrder.getId()).thenAccept(woOpt -> {
            if (!woOpt.isPresent()) {
                SwingUtilities.invokeLater(() ->
                        Toast.show(this, Toast.Type.WARNING, "Servis bulunamadığı için yenilenemedi."));
                return;
            }
            workOrder = woOpt.get();
            SwingUtilities.invokeLater(() -> {
                hydrateHeader();
                infoPanel.refresh(workOrder);
                buildRightColumn();
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Servis kaydı yenilenemedi", ex));
    }

    // =========================================================================
    // INIT COMPONENT
    // =========================================================================

    private void initComponent(WorkOrder initialWorkOrder) {
        setLayout(new MigLayout("fill, insets 20", "[grow]", "[pref!]20[grow, fill]"));
        createHeaderPanel();

        JPanel contentPanel = new JPanel(new MigLayout("insets 0, gapx 20", "[330!, fill][grow, fill]", "[grow, fill]"));
        contentPanel.setOpaque(false);

        infoPanel = new WorkOrderInfoPanel(initialWorkOrder, this::openWhatsAppModal);

        rightColumn = new JPanel(new MigLayout("insets 0, gapy 20", "[fill, grow]", "[]"));
        rightColumn.setOpaque(false);

        contentPanel.add(infoPanel, "grow");
        contentPanel.add(rightColumn, "grow");

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, "grow");
    }

    // =========================================================================
    // HEADER
    // =========================================================================

    private void createHeaderPanel() {
        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx", "[][][][grow][][]", "[]"));
        headerPanel.setOpaque(false);

        JButton btnBack = new JButton(new Ikon("icons/arrow-left.svg", 0.5f));
        btnBack.putClientProperty(FlatClientProperties.STYLE, "arc: 999; background: lighten($Panel.background, 5%);");
        btnBack.addActionListener(e -> FormManager.showForm(AllForms.getForm(FormWorkOrders.class)));

        JPanel titlePanel = new JPanel(new MigLayout("insets 0, gapy 2", "[fill]", "[][]"));
        titlePanel.setOpaque(false);
        lblHeaderTitle = new JLabel("SRV-YENİ");
        lblHeaderTitle.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");
        lblHeaderSubtitle = new JLabel("Kayıt Tarihi: -");
        lblHeaderSubtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        titlePanel.add(lblHeaderTitle, "wrap");
        titlePanel.add(lblHeaderSubtitle);

        lblHeaderBadge = new Badge(ServiceStatus.UNDER_REPAIR);
        lblHeaderBadge.setShowIcon(true);
        lblHeaderBadge.setShowBorder(true);
        lblHeaderBadge.setOpaque(true);

        JPanel statusPanel = new JPanel(new MigLayout("insets 0", "[][]", "[]"));
        statusPanel.setOpaque(false);
        statusComboBox = new JComboBox<>(ServiceStatus.values());
        statusComboBox.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
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
                        SwingUtilities.invokeLater(() -> lblHeaderBadge.setVisualizable(newStatus));
                        workOrder.setServiceStatus(newStatus);
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

        statusPanel.add(new JLabel("Durum Güncelle:"));
        statusPanel.add(statusComboBox);

        JButton btnGenerateDoc = new JButton("Form", new Ikon("icons/file-text.svg", 1f));
        btnGenerateDoc.putClientProperty(FlatClientProperties.STYLE,
                "background: lighten($Panel.background, 5%); arc: 10; font: bold");
        btnGenerateDoc.addActionListener(e -> showDocumentMenu(btnGenerateDoc));

        headerPanel.add(btnBack, "w 40!, h 40!, aligny top");
        headerPanel.add(titlePanel, "gapleft 15, aligny top");
        headerPanel.add(lblHeaderBadge, "gapleft 10, aligny top, gaptop 5");
        headerPanel.add(new JLabel(""), "growx, pushx");
        headerPanel.add(statusPanel, "align right, aligny top");
        headerPanel.add(btnGenerateDoc, "gapleft 10, aligny top");

        add(headerPanel, "wrap, growx");
    }

    private void hydrateHeader() {
        lblHeaderTitle.setText("SRV-" + workOrder.getId());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));
        String dateStr = workOrder.getCreatedAt() != null ? workOrder.getCreatedAt().format(formatter) : "-";
        lblHeaderSubtitle.setText("Kayıt Tarihi: " + dateStr);

        ServiceStatus currentStatus = workOrder.getServiceStatus() != null
                ? workOrder.getServiceStatus() : ServiceStatus.UNDER_REPAIR;
        lblHeaderBadge.setVisualizable(currentStatus);

        ActionListener[] listeners = statusComboBox.getActionListeners();
        for (ActionListener l : listeners) statusComboBox.removeActionListener(l);
        statusComboBox.setSelectedItem(currentStatus);
        for (ActionListener l : listeners) statusComboBox.addActionListener(l);
    }

    // =========================================================================
    // BELGE (PDF) OLUŞTURMA
    // =========================================================================

    private void showDocumentMenu(JButton anchor) {
        JPopupMenu popup = new JPopupMenu();
        for (ServiceFormType type : ServiceFormType.values()) {
            JMenuItem item = new JMenuItem(type.getDisplayName());
            item.addActionListener(e -> {
                if (type.requiresSigners()) {
                    openSignerNamesModal(type);
                } else {
                    generateAndOpenDocument(type, null, null);
                }
            });
            popup.add(item);
        }
        popup.show(anchor, 0, anchor.getHeight());
    }

    private void openSignerNamesModal(ServiceFormType type) {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> SwingUtilities.invokeLater(() -> {
            User shop = shopOpt.orElse(null);

            JPanel panel = new JPanel(new MigLayout("fillx, wrap, insets 10, width 350", "[fill,grow]", "[][][][]"));
            panel.add(new JLabel(type.getLeftSignerLabel() + ":"));
            JTextField txtLeft = new JTextField(defaultSignerName(type.getLeftSignerLabel(), shop));
            panel.add(txtLeft, "growx");
            panel.add(new JLabel(type.getRightSignerLabel() + ":"));
            JTextField txtRight = new JTextField(defaultSignerName(type.getRightSignerLabel(), shop));
            panel.add(txtRight, "growx");

            SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                    new SimpleModalBorder.Option("Oluştur", SimpleModalBorder.YES_OPTION),
                    new SimpleModalBorder.Option("Çık", SimpleModalBorder.CANCEL_OPTION)
            };

            AppModal.showModal(this, new SimpleModalBorder(panel, type.getDisplayName(), options, (controller, action) -> {
                if (action != SimpleModalBorder.YES_OPTION) return;
                generateAndOpenDocument(type, txtLeft.getText(), txtRight.getText());
            }), "generate_document_modal");
        })).exceptionally(ex -> ErrorHandler.handle(this, "İmza modalı açılamadı", ex));
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

    private void generateAndOpenDocument(ServiceFormType formType, String leftSignerName, String rightSignerName) {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> {
            User shop = shopOpt.orElse(null);
            try {
                File pdf = formType.generate(workOrder, shop, leftSignerName, rightSignerName);
                SwingUtilities.invokeLater(() -> {
                    if (DesktopHelper.openFile(pdf)) {
                        Toast.show(this, Toast.Type.SUCCESS, "Belge oluşturuldu.");
                    } else {
                        Toast.show(this, Toast.Type.WARNING, "Belge oluşturuldu ama açılamadı: " + pdf.getAbsolutePath());
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Belge oluşturulamadı: " + ex.getMessage()));
                Servicio.getLogger().error("PDF belge oluşturma hatası", ex);
            }
        }).exceptionally(ex -> ErrorHandler.handle(this, "Belge oluşturulamadı", ex));
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
                    Toast.show(this, Toast.Type.WARNING, "Önce Ayarlar > WhatsApp Şablonları'ndan bir mesaj şablonu ekleyin.");
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
                        new SimpleModalBorder.Option("Çık", SimpleModalBorder.CANCEL_OPTION)
                };

                AppModal.showModal(this, new SimpleModalBorder(panel, "WhatsApp Mesaj Gönder", options, (controller, action) -> {
                    if (action != SimpleModalBorder.YES_OPTION) return;

                    String digits = PhoneHelper.toWhatsAppDigits(workOrder.getCustomer().getPhoneNumber1());
                    if (digits == null) {
                        controller.consume();
                        Toast.show(this, Toast.Type.WARNING, "Müşterinin telefon numarası yok.");
                        return;
                    }

                    String url = "https://wa.me/" + digits + "?text=" + URLEncoder.encode(txtMessage.getText(), StandardCharsets.UTF_8);
                    if (!DesktopHelper.browseUrl(url)) {
                        Toast.show(this, Toast.Type.ERROR, "WhatsApp açılamadı.");
                    }
                }), "whatsapp_message_modal");
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "WhatsApp şablonları yüklenemedi", ex));
    }

    // =========================================================================
    // RIGHT COLUMN (Parça/Ödeme/Not panelleri)
    // =========================================================================

    private void buildRightColumn() {
        rightColumn.removeAll();

        WorkOrderPaymentsPanel paymentsPanel = new WorkOrderPaymentsPanel(workOrder);
        WorkOrderItemsPanel itemsPanel = new WorkOrderItemsPanel(workOrder, paymentsPanel::refresh);
        WorkOrderNotesPanel notesPanel = new WorkOrderNotesPanel(workOrder);

        rightColumn.add(itemsPanel, "wrap, growx");
        rightColumn.add(paymentsPanel, "wrap, growx");
        rightColumn.add(notesPanel, "wrap, growx");
        rightColumn.revalidate();
        rightColumn.repaint();
    }
}
