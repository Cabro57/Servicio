package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.Badge;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.editors.AddButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.WorkOrderItemEditPanel;
import tr.cabro.servicio.application.panels.WorkOrderItemAddPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.AddButtonRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.documents.ServiceFormType;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.model.enums.SourceType;
import tr.cabro.servicio.model.enums.TemplateType;
import tr.cabro.servicio.service.*;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.TemplateEngine;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class FormWorkOrder extends Form {

    private WorkOrder workOrder;
    private final WorkOrderService workOrderService;

    // --- Header ---
    private JLabel lblHeaderTitle;
    private JLabel lblHeaderSubtitle;
    private Badge lblHeaderBadge;
    private JComboBox<ServiceStatus> statusComboBox;

    // --- Layout ---
    private JPanel leftColumn;
    private JPanel rightColumn;

    // --- Left Column Labels ---
    private JLabel lblCustomerName, lblCustomerPhone, lblCustomerEmail;
    private JButton btnWhatsapp;
    private JLabel lblDeviceType, lblDeviceBrand, lblDeviceModel, lblDeviceSerial;
    private JLabel lblDeviceAccess;
    private JButton btnRevealAccess;
    private String currentAccessTypeLabel;
    private String currentAccessSecret; // düz metin — sadece "göster"e basılınca gösterilir
    private boolean accessRevealed;
    private JTextArea txtReportedFault;
    private JLabel lblDateArrival, lblDateEstimated;

    // --- Items Table ---
    private GenericTableModel<WorkOrderItem> itemsTableModel;
    private JPanel itemsTableContainer;
    private JPanel itemsEmptyLabel;

    // --- Payments Table ---
    private GenericTableModel<WorkOrderPayment> paymentsTableModel;
    private JPanel paymentsTableContainer; // JScrollPane yerine JPanel kullanıyoruz
    private JPanel paymentsEmptyLabel;

    // --- Payment Summary Labels (inline güncelleme için referans) ---
    private JLabel lblTotalService;
    private JLabel lblTotalPaid;
    private JLabel lblRemainVal;
    private JLabel lblPaymentBadge;

    // --- Notes Panel ---
    private JPanel notesListPanel;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public FormWorkOrder(WorkOrder workOrder) {
        this.workOrderService = ServiceManager.getWorkOrderService();
        initComponent();
        setService(workOrder);
    }

    public void setService(@NonNull WorkOrder workOrder) {
        this.workOrder = workOrder;
        hydrateLeftUI();
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
                hydrateLeftUI();
                buildRightColumn();
            });
        }).exceptionally(throwable -> {
            SwingUtilities.invokeLater(() ->
                    Toast.show(this, Toast.Type.ERROR, throwable.getMessage()));
            Servicio.getLogger().error("HATA:", throwable);
            return null;
        });
    }

    // =========================================================================
    // INIT COMPONENT
    // =========================================================================

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 20", "[grow]", "[pref!]20[grow, fill]"));
        createHeaderPanel();

        JPanel contentPanel = new JPanel(new MigLayout("insets 0, gapx 20", "[330!, fill][grow, fill]", "[grow, fill]"));
        contentPanel.setOpaque(false);

        leftColumn = new JPanel(new MigLayout("insets 0, gapy 20", "[fill, grow]", "[pref!][pref!][pref!]"));
        leftColumn.setOpaque(false);
        leftColumn.add(createCustomerCard(), "wrap");
        leftColumn.add(createDeviceCard(), "wrap");
        leftColumn.add(createTimelineCard(), "wrap");

        rightColumn = new JPanel(new MigLayout("insets 0, gapy 20", "[fill, grow]", "[]"));
        rightColumn.setOpaque(false);

        contentPanel.add(leftColumn, "grow");
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
                            Toast.show(this, Toast.Type.ERROR, "Durum güncellenemedi.");
                        });
                        return null;
                    });
        });

        statusPanel.add(new JLabel("Durum Güncelle:"));
        statusPanel.add(statusComboBox);

        JButton btnGenerateDoc = new JButton("Belge Oluştur", new Ikon("icons/file-text.svg", 1f));
        btnGenerateDoc.putClientProperty(FlatClientProperties.STYLE,
                "background: lighten($Panel.background, 5%); arc: 10; font: bold");
        btnGenerateDoc.addActionListener(e -> openGenerateDocumentModal());

        headerPanel.add(btnBack, "w 40!, h 40!, aligny top");
        headerPanel.add(titlePanel, "gapleft 15, aligny top");
        headerPanel.add(lblHeaderBadge, "gapleft 10, aligny top, gaptop 5");
        headerPanel.add(new JLabel(""), "growx, pushx");
        headerPanel.add(statusPanel, "align right, aligny top");
        headerPanel.add(btnGenerateDoc, "gapleft 10, aligny top");

        add(headerPanel, "wrap, growx");
    }

    // =========================================================================
    // BELGE (PDF) OLUŞTURMA
    // =========================================================================

    private void openGenerateDocumentModal() {
        JPanel panel = new JPanel(new MigLayout("fillx, wrap, insets 10, width 350", "[fill,grow]", "[][]"));
        panel.add(new JLabel("Oluşturulacak belge:"));
        JComboBox<ServiceFormType> combo = new JComboBox<>(ServiceFormType.values());
        panel.add(combo, "growx");

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Oluştur", SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option("Çık", SimpleModalBorder.CANCEL_OPTION)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Belge Oluştur", options, (controller, action) -> {
            if (action != SimpleModalBorder.YES_OPTION) return;
            ServiceFormType selected = (ServiceFormType) combo.getSelectedItem();
            if (selected == null) {
                controller.consume();
                return;
            }
            generateAndOpenDocument(selected);
        }), "generate_document_modal");
    }

    private void generateAndOpenDocument(ServiceFormType formType) {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> {
            User shop = shopOpt.orElse(null);
            try {
                File pdf = formType.generate(workOrder, shop);
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
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, ex.getMessage()));
            return null;
        });
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
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Şablonlar yüklenemedi: " + ex.getMessage()));
            return null;
        });
    }

    // =========================================================================
    // LEFT COLUMN HYDRATION
    // =========================================================================

    private void hydrateLeftUI() {
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
        lblDateArrival.setText(dateStr);
        lblDateEstimated.setText(workOrder.getCreatedAt() != null
                ? workOrder.getCreatedAt().plusDays(3).format(formatter) : "-");
    }

    // =========================================================================
    // RIGHT COLUMN
    // =========================================================================

    private void buildRightColumn() {
        rightColumn.removeAll();
        rightColumn.add(buildPartsCard(), "wrap, growx");
        rightColumn.add(buildPaymentsCard(), "wrap, growx");
        rightColumn.add(buildNotesCard(), "wrap, growx");
        rightColumn.revalidate();
        rightColumn.repaint();
    }

    // =========================================================================
    // PARTS / ITEMS CARD
    // =========================================================================

    private JPanel buildPartsCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[grow][]", "[]15[]10[]"));

        JLabel title = new JLabel("Kullanılan Parçalar ve Ücretlendirme");
        title.setIcon(new Ikon("icons/wrench.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        card.add(title, "span 2, wrap");

        JLabel subtitle = new JLabel("İşlemler ve Parçalar");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "font: bold");

        JButton btnAddPart = new JButton("+ Parça / İşlem Ekle");
        btnAddPart.putClientProperty(FlatClientProperties.STYLE,
                "background: $Component.accentColor; foreground: #ffffff; arc: 10; font: bold");
        btnAddPart.addActionListener(e -> openItemAddModal());
        card.add(subtitle, "aligny center");
        card.add(btnAddPart, "align right, wrap");

        // --- Tablo ---

        List<ColumnDef<WorkOrderItem>> columnDefs = Arrays.asList(
                new ColumnDef<>("Tür", ItemType.class, WorkOrderItem::getItemType),
                new ColumnDef<>("İşçilik / Parça Adı", String.class, WorkOrderItem::getItemName),
                new ColumnDef<>("Seri No", String.class, WorkOrderItem::getUsedSerialNo),
                new ColumnDef<>("Fiyat", BigDecimal.class, WorkOrderItem::getTotalPrice),
                new ColumnDef<>("İşlem", WorkOrderItem.class, workOrderItem -> "Detay")
        );

        itemsTableModel = new GenericTableModel<>(columnDefs);

        JTable itemsTable = new JTable(itemsTableModel);
        styleTable(itemsTable);
        itemsTable.getColumnModel().getColumn(0).setMaxWidth(85);
        itemsTable.getColumnModel().getColumn(0).setMinWidth(85);
        itemsTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        itemsTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        itemsTable.getColumnModel().getColumn(4).setMaxWidth(180);
        itemsTable.getColumnModel().getColumn(4).setMinWidth(120);

        DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer();
        rightAlign.setHorizontalAlignment(SwingConstants.TRAILING);
        itemsTable.getColumnModel().getColumn(3).setCellRenderer(new CurrencyTableCellRenderer());
        itemsTable.getColumnModel().getColumn(0).setCellRenderer(new ItemTypeBadgeRenderer());

        itemsTable.getColumnModel().getColumn(4).setCellRenderer(new ActionButtonRenderer());
        itemsTable.getColumnModel().getColumn(4).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                if (itemsTable.isEditing()) itemsTable.getCellEditor().cancelCellEditing();
                int modelRow = itemsTable.convertRowIndexToModel(row);
                WorkOrderItem selectedWoItem = itemsTableModel.getItemAt(modelRow);
                openItemEditModal(selectedWoItem);
            }

            @Override
            public void onDelete(int row) {
                if (itemsTable.isEditing()) itemsTable.getCellEditor().cancelCellEditing();
                int modelRow = itemsTable.convertRowIndexToModel(row);
                WorkOrderItem selectedWoItem = itemsTableModel.getItemAt(modelRow);
                confirmDeleteItem(selectedWoItem);
            }

            @Override
            public void onView(int row) {

            }
        }));

        // JScrollPane KALDILIRDI. Yerine normal JPanel kullanıyoruz.
        itemsTableContainer = new JPanel(new MigLayout("insets 0, gap 0", "[grow, fill]", "[]0[]"));
        itemsTableContainer.setOpaque(false);
        // JTable JPanel'de kullanıldığında Header'ı manuel eklememiz gerekir.
        itemsTableContainer.add(itemsTable.getTableHeader(), "wrap");
        itemsTableContainer.add(itemsTable);

        itemsEmptyLabel = createEmptyStatePanel("Henüz işlem veya parça eklenmedi.");

        populateItemsTable();

        card.add(itemsEmptyLabel, "span 2, growx, wrap");
        card.add(itemsTableContainer, "span 2, growx, wrap");
        return card;
    }

    private void populateItemsTable() {
        List<WorkOrderItem> items = workOrder.getItems();
        if (items == null || items.isEmpty()) {
            itemsEmptyLabel.setVisible(true);
            itemsTableContainer.setVisible(false);
            return;
        }
        itemsEmptyLabel.setVisible(false);
        itemsTableContainer.setVisible(true);

        itemsTableModel.setData(items);

        itemsTableContainer.revalidate();
        itemsTableContainer.repaint();
    }

    // ---- Item CRUD ----

    private void openItemAddModal() {
        WorkOrderItemAddPanel addPanel = new WorkOrderItemAddPanel(workOrder);
        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Ekle", SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };
        AppModal.showModal(this, new SimpleModalBorder(addPanel, "Parça veya İşlem Ekle", null, (controller, action) -> {

            if (action == WorkOrderItemAddPanel.CREAT_ITEM) {
                WorkOrderItem newItem = addPanel.getItem();
                if (newItem == null) {
                    Toast.show(this, Toast.Type.WARNING, "Lütfen geçerli bilgiler girin.");
                    controller.consume();
                    return;
                }

                workOrderService.addItem(newItem).thenAccept(saved -> {
                    workOrder.getItems().add(saved);

                    SwingUtilities.invokeLater(() -> {
                        populateItemsTable();

                        updatePaymentSummary();
                        Toast.show(this, Toast.Type.SUCCESS, "Kalem eklendi.");
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.ERROR, ex.getMessage());
                    });
                    Servicio.getLogger().error("ERROR", ex);
                    return null;
                });

            }
            else if (action == WorkOrderItemAddPanel.SELECTED_ITEM) {
                WorkOrderItem newItem = addPanel.getItem();
                if (newItem == null) {
                    Toast.show(this, Toast.Type.WARNING, "Lütfen geçerli bilgiler girin.");
                    controller.consume();
                    return;
                }

                controller.consume();

                AppModal.pushModalDeferred(() -> {
                    WorkOrderItemEditPanel editPanel = new WorkOrderItemEditPanel(newItem);
                    return new SimpleModalBorder(editPanel, "Kalem Ekle", options, (controller1, action1) -> {
                        if (action1 != SimpleModalBorder.YES_OPTION) return;

                        WorkOrderItem updated = editPanel.getUpdatedItem();

                        workOrderService.addItem(updated).thenAccept(saved -> {
                            workOrder.getItems().add(saved);

                            SwingUtilities.invokeLater(() -> {
                                populateItemsTable();

                                updatePaymentSummary();
                                Toast.show(this, Toast.Type.SUCCESS, "Kalem eklendi.");
                            });
                        }).exceptionally(ex -> {
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(this, Toast.Type.ERROR, ex.getMessage());
                            });
                            Servicio.getLogger().error("ERROR", ex);
                            return null;
                        });
                    });
                }, "itemAddModal");
            }
        }), "itemAddModal");
    }

    private void openItemEditModal(WorkOrderItem item) {
        if (item == null) return;
        WorkOrderItemEditPanel editPanel = new WorkOrderItemEditPanel(item);
        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Değişiklikleri Kaydet", SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };
        AppModal.showModal(FormManager.getFrame(), new SimpleModalBorder(editPanel, "Kalemi Düzenle", options, (controller, action) -> {
            if (action != SimpleModalBorder.YES_OPTION) return;

            WorkOrderItem updated = editPanel.getUpdatedItem();
            if (updated == null) {
                Toast.show(this, Toast.Type.WARNING, "Lütfen geçerli bir isim girin.");
                controller.consume();
                return;
            }

            workOrderService.updateItem(updated).thenRun(() -> SwingUtilities.invokeLater(() -> {
                List<WorkOrderItem> currentItems = workOrder.getItems();

                int index = currentItems.indexOf(item);
                if (index != -1) {
                    currentItems.set(index, updated);

                    workOrder.setItems(currentItems);
                }

                populateItemsTable();

                updatePaymentSummary();
                Toast.show(this, Toast.Type.SUCCESS, "Kalem güncellendi.");
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(() ->
                        Toast.show(this, Toast.Type.ERROR, "Güncelleme başarısız: " + ex.getMessage()));
                return null;
            });
        }), "itemEditModal");
    }

    private void confirmDeleteItem(WorkOrderItem item) {
        if (item == null) return;

        JPanel panel = new JPanel(new BorderLayout(0, 10));

        JLabel messageLabel = new JLabel("\"" + item.getItemName() + "\" kalemi silinecek. Emin misiniz?");
        JCheckBox stockCheckBox = new JCheckBox("Silinen parça stoğa eklensin mi?");

        stockCheckBox.setSelected(false);

        // Bileşenleri panele ekliyoruz
        panel.add(messageLabel, BorderLayout.CENTER);
        if (item.getSourceType() == SourceType.PRESET) {
            stockCheckBox.setSelected(true);
            panel.add(stockCheckBox, BorderLayout.SOUTH);
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Silme Onayı",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        boolean updateStock = stockCheckBox.isSelected();

        workOrderService.deleteItem(item.getId(), updateStock).thenRun(() -> SwingUtilities.invokeLater(() -> {
            workOrder.getItems().remove(item);

            populateItemsTable();
            updatePaymentSummary();

            Toast.show(this, Toast.Type.SUCCESS, "Kalem silindi.");
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    Toast.show(this, Toast.Type.ERROR, ex.getMessage()));

            Servicio.getLogger().error("HATA", ex);
            return null;
        });
    }

    // =========================================================================
    // PAYMENTS CARD
    // =========================================================================

    private JPanel buildPaymentsCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[grow]", "[]15[][]20[]"));

        JLabel title = new JLabel("Ödemeler (Ön Muhasebe)");
        title.setIcon(new Ikon("icons/credit-card.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        card.add(title, "wrap");

        List<ColumnDef<WorkOrderPayment>> columnDefs = Arrays.asList(
                new ColumnDef<>("Tarih", LocalDateTime.class, WorkOrderPayment::getPaymentDate),
                new ColumnDef<>("Yöntem", PaymentType.class, WorkOrderPayment::getPaymentType),
                new ColumnDef<>("Tutar", BigDecimal.class, WorkOrderPayment::getAmount),
                new ColumnDef<>("İşlem", WorkOrderPayment.class, workOrderPayment -> "Detay")
        );

        paymentsTableModel = new GenericTableModel<>(columnDefs);

        JTable paymentsTable = new JTable(paymentsTableModel);
        styleTable(paymentsTable);
        paymentsTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        paymentsTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        paymentsTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        paymentsTable.getColumnModel().getColumn(3).setMaxWidth(50);
        paymentsTable.getColumnModel().getColumn(3).setMinWidth(50);
        paymentsTable.getColumnModel().getColumn(2).setCellRenderer(new GreenAmountRenderer());
        paymentsTable.getColumnModel().getColumn(3).setCellRenderer(new AddButtonRenderer());
        paymentsTable.getColumnModel().getColumn(3).setCellEditor(new AddButtonEditor(row -> {
            if (paymentsTable.isEditing()) paymentsTable.getCellEditor().cancelCellEditing();
            int modelRow = paymentsTable.convertRowIndexToModel(row);
            WorkOrderPayment selectedWoPayment = paymentsTableModel.getItemAt(modelRow);
            confirmDeletePayment(selectedWoPayment, row);
        }));

        // JScrollPane KALDILIRDI.
        paymentsTableContainer = new JPanel(new MigLayout("insets 0, gap 0", "[grow, fill]", "[]0[]"));
        paymentsTableContainer.setOpaque(false);
        paymentsTableContainer.add(paymentsTable.getTableHeader(), "wrap");
        paymentsTableContainer.add(paymentsTable);

        paymentsEmptyLabel = createEmptyStatePanel("Henüz ödeme alınmadı.");

        populatePaymentsTable();

        card.add(paymentsEmptyLabel, "growx, wrap");
        card.add(paymentsTableContainer, "growx, wrap");
        card.add(buildPaymentInputRow(), "growx, wrap");
        card.add(buildPaymentSummaryBox(), "align right, w 350!");
        return card;
    }

    private void populatePaymentsTable() {
        List<WorkOrderPayment> payments = workOrder.getPayments();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));
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

        inputRow.add(createMutedLabel("Ödeme Yöntemi"));
        inputRow.add(createMutedLabel("Tutar (TL)"), "wrap");

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

                updatePaymentSummary();
                Toast.show(this, Toast.Type.SUCCESS, "Tahsilat eklendi.");
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(() ->
                        Toast.show(this, Toast.Type.ERROR, "Tahsilat eklenemedi: " + ex.getMessage()));
                return null;
            });
        });

        inputRow.add(cmbMethod, "growx");
        inputRow.add(txtAmount, "growx");
        inputRow.add(btnAddPayment);
        return inputRow;
    }

    private void confirmDeletePayment(WorkOrderPayment payment, int row) {
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

            updatePaymentSummary();
            Toast.show(this, Toast.Type.SUCCESS, "Ödeme silindi.");
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    Toast.show(this, Toast.Type.ERROR, "Silinemedi: " + ex.getMessage()));
            return null;
        });
    }

    // =========================================================================
    // PAYMENT SUMMARY
    // =========================================================================

    private JPanel buildPaymentSummaryBox() {
        JPanel summaryBox = new JPanel(new MigLayout("insets 15, fillx", "[grow][pref!]", "[]10[]15[]10[]"));
        summaryBox.putClientProperty(FlatClientProperties.STYLE, "background: darken($Panel.background, 2%); arc: 15");

        summaryBox.add(createMutedLabel("Hizmet & Parça Toplamı:"));
        lblTotalService = new JLabel(Format.formatPrice(workOrder.getTotalServiceAmount()));
        summaryBox.add(lblTotalService, "align right, wrap");

        summaryBox.add(createMutedLabel("Alınan Ödeme:"));
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

    private void updatePaymentSummary() {
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

    // =========================================================================
    // NOTES CARD
    // =========================================================================

    private JPanel buildNotesCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[grow]", "[]15[]15[]"));

        JLabel title = new JLabel("Teknisyen Notları");
        title.setIcon(new Ikon("icons/file-text.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        card.add(title, "wrap");

        notesListPanel = new JPanel(new MigLayout("insets 0, fillx", "[grow]", "[]"));
        notesListPanel.setOpaque(false);
        populateNotesList();
        card.add(notesListPanel, "growx, wrap");

        JTextArea txtNewNote = new JTextArea(3, 20);
        txtNewNote.setLineWrap(true);
        txtNewNote.setWrapStyleWord(true);
        txtNewNote.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Servis süreciyle ilgili notlarınızı buraya yazın...");
        txtNewNote.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); border: 10,10,10,10;");

        JScrollPane scrollNote = new JScrollPane(txtNewNote);
        card.add(scrollNote, "wrap, growx, h 80!");

        JButton btnAddNote = new JButton("+ Not Ekle");
        btnAddNote.putClientProperty(FlatClientProperties.STYLE,
                "background: #1e3a8a; foreground: #3498db; arc: 10; font: bold; borderWidth: 0");
        btnAddNote.addActionListener(e -> {
            String text = txtNewNote.getText().trim();
            if (text.isEmpty()) {
                Toast.show(this, Toast.Type.WARNING, "Not boş olamaz.");
                return;
            }
            WorkOrderNote n = new WorkOrderNote();
            n.setServiceId(workOrder.getId());
            n.setNote(text);
            n.setCreatedAt(LocalDateTime.now());

            workOrderService.addNote(n).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                workOrder.getTechnicianNotes().add(saved);
                txtNewNote.setText("");
                appendNoteRow(saved);
                Toast.show(this, Toast.Type.SUCCESS, "Not eklendi.");
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(() ->
                        Toast.show(this, Toast.Type.ERROR, "Not eklenemedi: " + ex.getMessage()));
                return null;
            });
        });

        card.add(btnAddNote, "align right");
        return card;
    }

    private void populateNotesList() {
        notesListPanel.removeAll();
        List<WorkOrderNote> notes = workOrder.getTechnicianNotes();
        if (notes == null || notes.isEmpty()) {
            notesListPanel.add(createMutedLabel("Henüz teknisyen notu eklenmedi."), "wrap");
        } else {
            for (WorkOrderNote n : notes) {
                addNoteRowToPanel(n);
            }
        }
        notesListPanel.revalidate();
        notesListPanel.repaint();
    }

    private void appendNoteRow(WorkOrderNote note) {
        if (notesListPanel.getComponentCount() == 1
                && notesListPanel.getComponent(0) instanceof JLabel) {
            notesListPanel.removeAll();
        }
        addNoteRowToPanel(note);
        notesListPanel.revalidate();
        notesListPanel.repaint();
    }

    private void addNoteRowToPanel(WorkOrderNote note) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        JPanel noteRow = new JPanel(new MigLayout("insets 10 12 10 12, fillx", "[grow][]", "[]6[]"));
        noteRow.putClientProperty(FlatClientProperties.STYLE,
                "background: lighten($Panel.background, 4%); arc: 12; border: 1,1,1,1,$Component.borderColor");

        JTextArea lblNote = new JTextArea(note.getNote());
        lblNote.setEditable(false);
        lblNote.setOpaque(false);
        lblNote.setLineWrap(true);
        lblNote.setWrapStyleWord(true);
        lblNote.setFocusable(false);
        lblNote.putClientProperty(FlatClientProperties.STYLE, "font: +1; border: 0,0,0,0");

        JButton btnDeleteNote = new JButton(new Ikon("icons/x.svg", 0.75f));
        btnDeleteNote.putClientProperty(FlatClientProperties.BUTTON_TYPE, "toolBarButton");
        btnDeleteNote.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDeleteNote.setToolTipText("Notu Sil");
        btnDeleteNote.addActionListener(e -> confirmDeleteNote(note, noteRow));

        JLabel lblAuthor = createMutedLabel(note.getTechnicianId() == null ? "Sistem" : "Teknisyen");
        JLabel lblDate = createMutedLabel(note.getCreatedAt() != null ? note.getCreatedAt().format(df) : "-");

        noteRow.add(lblNote, "growx, wmin 0, aligny top");
        noteRow.add(btnDeleteNote, "top, right, w 28!, h 28!, wrap");
        noteRow.add(lblAuthor, "aligny bottom");
        noteRow.add(lblDate, "align right, aligny bottom");

        notesListPanel.add(noteRow, "wrap, growx, gapy 0 8");
    }

    private void confirmDeleteNote(WorkOrderNote note, JPanel noteRow) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bu not silinecek. Emin misiniz?",
                "Silme Onayı",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        workOrderService.deleteNote(note.getId()).thenRun(() -> SwingUtilities.invokeLater(() -> {
            workOrder.getTechnicianNotes().remove(note);
            notesListPanel.remove(noteRow);
            if (workOrder.getTechnicianNotes().isEmpty()) {
                notesListPanel.removeAll();
                notesListPanel.add(createMutedLabel("Henüz teknisyen notu eklenmedi."), "wrap");
            }
            notesListPanel.revalidate();
            notesListPanel.repaint();
            Toast.show(this, Toast.Type.SUCCESS, "Not silindi.");
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() ->
                    Toast.show(this, Toast.Type.ERROR, "Not silinemedi: " + ex.getMessage()));
            return null;
        });
    }

    // =========================================================================
    // LEFT COLUMN CARDS
    // =========================================================================

    private JPanel createCustomerCard() {
        JPanel card = createCardPanel();
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
        btnWhatsapp.addActionListener(e -> openWhatsAppModal());
        btnWhatsapp.setVisible(false);

        card.add(title, "span 2");
        card.add(lblCustomerName, "span 2");
        card.add(lblCustomerPhone, "span 2");
        card.add(lblCustomerEmail, "span 2");
        card.add(btnWhatsapp, "span 2, growx");
        return card;
    }

    private JPanel createDeviceCard() {
        JPanel card = createCardPanel();
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
        card.add(createMutedLabel("Tür:")); card.add(lblDeviceType, "wrap");
        card.add(createMutedLabel("Marka:")); card.add(lblDeviceBrand, "wrap");
        card.add(createMutedLabel("Model:")); card.add(lblDeviceModel, "wrap");
        card.add(createMutedLabel("Seri No:")); card.add(lblDeviceSerial, "wrap");

        lblDeviceAccess = new JLabel("-");
        lblDeviceAccess.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        btnRevealAccess = new JButton(new Ikon("icons/eye.svg", 0.7f));
        btnRevealAccess.setToolTipText("Erişim kodunu göster/gizle");
        btnRevealAccess.setVisible(false);
        btnRevealAccess.addActionListener(e -> toggleAccessReveal());
        JPanel accessRow = new JPanel(new MigLayout("insets 0, fillx", "[grow][]"));
        accessRow.add(lblDeviceAccess, "growx");
        accessRow.add(btnRevealAccess);
        card.add(createMutedLabel("Erişim Kodu:")); card.add(accessRow, "wrap, growx");

        card.add(createMutedLabel("Müşteri Şikayeti:"), "span 2, wrap");
        txtReportedFault = new JTextArea();
        txtReportedFault.setEditable(false);
        txtReportedFault.setLineWrap(true);
        txtReportedFault.setWrapStyleWord(true);
        txtReportedFault.putClientProperty(FlatClientProperties.STYLE,
                "background: lighten($Panel.background, 3%); border: 10,10,10,10;");
        card.add(txtReportedFault, "span 2, growx, h 60!");
        return card;
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
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[100!][grow, right]", "[]15[][]"));
        JLabel title = new JLabel("Zaman Çizelgesi");
        title.setIcon(new Ikon("icons/clock.svg"));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        lblDateArrival = new JLabel("-");
        lblDateArrival.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDateEstimated = new JLabel("-");
        lblDateEstimated.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        card.add(title, "span 2, wrap");
        card.add(createMutedLabel("Geliş:")); card.add(lblDateArrival, "wrap");
        card.add(createMutedLabel("Tahmini Bitiş:")); card.add(lblDateEstimated, "wrap");
        return card;
    }

    // =========================================================================
    // INNER RENDERERS & EDITORS
    // =========================================================================

    private static class ItemTypeBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof ItemType) {
                if ((ItemType) value == ItemType.LABOR) {
                    label.setText("İşçilik");
                    label.putClientProperty(FlatClientProperties.STYLE,
                            "border: 1,8,1,8,#9b59b6; foreground: #9b59b6; arc: 15; font: -1");
                } else {
                    label.setText("Parça");
                    label.putClientProperty(FlatClientProperties.STYLE,
                            "border: 1,8,1,8,#3498db; foreground: #3498db; arc: 15; font: -1");
                }
            }
            label.setHorizontalAlignment(SwingConstants.CENTER);
            return label;
        }
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



    // =========================================================================
    // HELPERS
    // =========================================================================

    private void styleTable(JTable table) {
        table.setRowHeight(38);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setReorderingAllowed(false);
        table.putClientProperty(FlatClientProperties.STYLE,
                "background: lighten($Panel.background, 2%); selectionBackground: lighten($Panel.background, 5%)");
    }

    private JPanel createEmptyStatePanel(String message) {
        JPanel p = new JPanel(new MigLayout("insets 10, fillx", "[grow]", "[]"));
        p.setOpaque(false);
        p.add(createMutedLabel(message), "align center");
        return p;
    }

    private JLabel createMutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        return label;
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); arc: 15;");
        return panel;
    }
}