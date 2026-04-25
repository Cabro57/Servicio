package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.system.AllForms;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import tr.cabro.servicio.application.component.Badge;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.panels.service.ServiceItemAddPanel;
import tr.cabro.servicio.application.panels.service.ServiceItemEditPanel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.*;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FormWorkOrder extends Form {

    private WorkOrder workOrder;
    private List<WorkOrderNote> workOrderNotes;

    private final WorkOrderService workOrderService;

    // --- UI Bileşenleri ---
    private JLabel lblHeaderTitle;
    private JLabel lblHeaderSubtitle;
    private Badge lblHeaderBadge;
    private JComboBox<ServiceStatus> statusComboBox;

    private JPanel leftColumn;
    private JPanel rightColumn;

    private JLabel lblCustomerName, lblCustomerPhone, lblCustomerEmail;
    private JLabel lblDeviceType, lblDeviceBrand, lblDeviceModel, lblDeviceSerial;
    private JTextArea txtReportedFault;
    private JLabel lblDateArrival, lblDateEstimated;

    public FormWorkOrder(WorkOrder workOrder) {
        this.workOrderService = ServiceManager.getWorkOrderService();

        initComponent();
        setService(workOrder);
    }

    public void setService(@NonNull WorkOrder workOrder) {
        this.workOrder = workOrder;
        reloadServiceData();
    }

    private void reloadServiceData() {
        if (workOrder == null || workOrder.getId() <= 0) return;

        workOrderService.get(workOrder.getId()).thenAccept(opt -> {
            opt.ifPresent(s -> {
                this.workOrder = s;
                workOrderService.getNotes(s.getId()).thenAccept(notes -> {
                    this.workOrderNotes = notes;
                    SwingUtilities.invokeLater(() -> {
                        hydrateLeftUI();
                        buildRightColumn();
                    });
                });
            });
        });
    }

    private void hydrateLeftUI() {
        lblHeaderTitle.setText("SRV-" + workOrder.getId());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new java.util.Locale("tr", "TR"));
        String dateStr = workOrder.getCreatedAt() != null ? workOrder.getCreatedAt().format(formatter) : "-";
        lblHeaderSubtitle.setText("Kayıt Tarihi: " + dateStr);

        ServiceStatus currentStatus = workOrder.getServiceStatus() != null ? workOrder.getServiceStatus() : ServiceStatus.UNDER_REPAIR;
        lblHeaderBadge.setVisualizable(currentStatus);

        ActionListener[] listeners = statusComboBox.getActionListeners();
        for (ActionListener l : listeners) statusComboBox.removeActionListener(l);
        statusComboBox.setSelectedItem(currentStatus);
        for (ActionListener l : listeners) statusComboBox.addActionListener(l);

        if (workOrder.getCustomer() != null) {
            lblCustomerName.setText(workOrder.getCustomer().getFullName());
            lblCustomerPhone.setText(workOrder.getCustomer().getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(workOrder.getCustomer().getPhoneNumber1()) : "-");
            lblCustomerEmail.setText(workOrder.getCustomer().getEmail() != null ? workOrder.getCustomer().getEmail() : "-");
        }

        Device device = workOrder.getDevice();
        if (device != null) {
            lblDeviceType.setText(device.getDeviceType() != null ? device.getDeviceType() : "-");
            lblDeviceBrand.setText(device.getBrand() != null ? device.getBrand() : "-");
            lblDeviceModel.setText(device.getModel() != null ? device.getModel() : "-");
            lblDeviceSerial.setText(device.getSerialNo() != null ? device.getSerialNo() : "-");
        }

        txtReportedFault.setText(workOrder.getReportedFault() != null ? workOrder.getReportedFault() : "Belirtilmemiş.");
        lblDateArrival.setText(dateStr);
        lblDateEstimated.setText(workOrder.getCreatedAt() != null ? workOrder.getCreatedAt().plusDays(3).format(formatter) : "-");
    }

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

    private void buildRightColumn() {
        rightColumn.removeAll();
        rightColumn.add(buildPartsCard(), "wrap, growx");
        rightColumn.add(buildPaymentsCard(), "wrap, growx");
        rightColumn.add(buildNotesCard(), "wrap, growx");
        rightColumn.revalidate();
        rightColumn.repaint();
    }

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
        btnAddPart.putClientProperty(FlatClientProperties.STYLE, "background: $Component.accentColor; foreground: #ffffff; arc: 10; font: bold");
        btnAddPart.addActionListener(e -> onActionTaken());

        card.add(subtitle, "aligny center");
        card.add(btnAddPart, "align right, wrap");

        JPanel listHeader = new JPanel(new MigLayout("insets 5 10 5 10, fillx", "[80!][grow][150!][100!][40!]", ""));
        listHeader.putClientProperty(FlatClientProperties.STYLE, "border: 0,0,1,0,$Component.borderColor");
        listHeader.setOpaque(false);
        listHeader.add(createMutedLabel("Tür"));
        listHeader.add(createMutedLabel("İşlem / Parça Adı"));
        listHeader.add(createMutedLabel("Seri No"));
        listHeader.add(createMutedLabel("Fiyat"), "align right");
        listHeader.add(new JLabel(""), "wrap");
        card.add(listHeader, "span 2, growx, wrap");

        if (workOrder.getItems() == null || workOrder.getItems().isEmpty()) {
            JLabel empty = createMutedLabel("Henüz işlem veya parça eklenmedi.");
            card.add(empty, "span 2, gapy 10");
        } else {
            for (WorkOrderItem item : workOrder.getItems()) {
                JPanel row = new JPanel(new MigLayout("insets 10, fillx", "[80!][grow][150!][100!][70!]", ""));
                row.putClientProperty(FlatClientProperties.STYLE, "border: 0,0,1,0,$Component.borderColor");
                row.setOpaque(false);

                JLabel lblType = new JLabel(item.getItemType() == ItemType.LABOR ? "İşçilik" : "Parça");
                String badgeStyle = item.getItemType() == ItemType.LABOR ?
                        "border: 1,8,1,8,#9b59b6; foreground: #9b59b6; arc: 15; font: -1" :
                        "border: 1,8,1,8,#3498db; foreground: #3498db; arc: 15; font: -1";
                lblType.putClientProperty(FlatClientProperties.STYLE, badgeStyle);

                JLabel lblName = new JLabel(item.getItemName());
                JLabel lblSerial = createMutedLabel(item.getUsedSerialNo() != null ? item.getUsedSerialNo() : "-");

                JLabel lblPrice = new JLabel(Format.formatPrice(item.getUnitPrice()));
                lblPrice.putClientProperty(FlatClientProperties.STYLE, "font: bold");

                // --- YENİ DÜZENLE BUTONU ---
                JButton btnEdit = new JButton(new Ikon("icons/pencil.svg", 0.7f));
                btnEdit.putClientProperty(FlatClientProperties.STYLE, "background: null; borderWidth: 0; foreground: $Component.accentColor");
                btnEdit.setCursor(new Cursor(Cursor.HAND_CURSOR));
                btnEdit.addActionListener(e -> openItemEditModal(item));

                // --- ÇÖP KUTUSU ---
                JButton btnDelete = new JButton(new Ikon("icons/trash-2.svg", 0.8f));
                btnDelete.putClientProperty(FlatClientProperties.STYLE, "background: null; borderWidth: 0; foreground: #e74c3c");
                btnDelete.setCursor(new Cursor(Cursor.HAND_CURSOR));
                btnDelete.addActionListener(e -> {
                    workOrderService.deleteItem(item.getId()).thenAccept(v -> reloadServiceData());
                });

                JPanel actions = new JPanel(new MigLayout("insets 0, gap 5", "[][]", ""));
                actions.setOpaque(false);
                actions.add(btnEdit);
                actions.add(btnDelete);

                row.add(lblType);
                row.add(lblName);
                row.add(lblSerial);
                row.add(lblPrice, "align right");
                row.add(actions, "align center");
                card.add(row, "span 2, growx, wrap");
            }
        }
        return card;
    }

    private JPanel buildPaymentsCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[grow]", "[]15[][]20[]"));

        JLabel title = new JLabel("Ödemeler (Ön Muhasebe)");
        title.setIcon(new Ikon("icons/credit-card.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        card.add(title, "wrap");

        if (workOrder.getPayments() == null || workOrder.getPayments().isEmpty()) {
            card.add(createMutedLabel("Henüz ödeme alınmadı."), "wrap");
        } else {
            for (WorkOrderPayment p : workOrder.getPayments()) {
                JPanel row = new JPanel(new MigLayout("insets 5, fillx", "[grow][100!][40!]", ""));
                row.setOpaque(false);
                row.putClientProperty(FlatClientProperties.STYLE, "border: 0,0,1,0,$Component.borderColor");

                PaymentType method = p.getPaymentType();

                row.add(new JLabel("Tahsilat (" + method.getDisplayName() + ")"), "growx");
                JLabel lblAmt = new JLabel(Format.formatPrice(p.getAmount()));
                lblAmt.putClientProperty(FlatClientProperties.STYLE, "foreground: #2ecc71; font: bold");
                row.add(lblAmt, "align right");

                JButton btnDel = new JButton(new Ikon("icons/x.svg", 0.7f));
                btnDel.putClientProperty(FlatClientProperties.STYLE, "background: null; borderWidth: 0; foreground: #e74c3c");
                btnDel.addActionListener(e -> {
                    workOrderService.deletePayment(p.getId()).thenAccept(v -> reloadServiceData());
                });
                row.add(btnDel, "align center");
                card.add(row, "wrap, growx");
            }
        }

        JPanel inputRow = new JPanel(new MigLayout("insets 10, fillx", "[150!][grow][]", "[]5[]"));
        inputRow.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); arc: 10; border: 1,1,1,1,$Component.borderColor");

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
        btnAddPayment.putClientProperty(FlatClientProperties.STYLE, "background: #0b4a3a; foreground: #2ecc71; arc: 8; font: bold; borderWidth: 0");

        btnAddPayment.addActionListener(e -> {
            BigDecimal amt = new BigDecimal(txtAmount.getValue().toString());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) return;

            WorkOrderPayment sp = new WorkOrderPayment();
            sp.setServiceId(workOrder.getId());
            sp.setAmount(amt);
            sp.setPaymentType((PaymentType) cmbMethod.getSelectedItem());
            sp.setPaymentDate(LocalDateTime.now());

            workOrderService.addPayment(sp).thenAccept(v -> reloadServiceData());
        });

        inputRow.add(cmbMethod, "growx");
        inputRow.add(txtAmount, "growx");
        inputRow.add(btnAddPayment);
        card.add(inputRow, "wrap, growx");

        // ÖZET KUTUSU VE ROZET MANTIĞI
        JPanel summaryBox = new JPanel(new MigLayout("insets 15, fillx", "[grow][pref!]", "[]10[]15[]"));
        summaryBox.putClientProperty(FlatClientProperties.STYLE, "background: darken($Panel.background, 2%); arc: 15");

        summaryBox.add(createMutedLabel("Hizmet & Parça Toplamı:"));
        summaryBox.add(new JLabel(Format.formatPrice(workOrder.getTotalServiceAmount())), "align right, wrap");

        summaryBox.add(createMutedLabel("Alınan Ödeme:"));
        JLabel lblPaid = new JLabel("- " + Format.formatPrice(workOrder.getTotalPaid()));
        lblPaid.putClientProperty(FlatClientProperties.STYLE, "foreground: #2ecc71");
        summaryBox.add(lblPaid, "align right, wrap");

        summaryBox.add(new JSeparator(), "span 2, growx, wrap");

        JLabel lblRemainText = new JLabel("Kalan Bakiye:");
        lblRemainText.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        summaryBox.add(lblRemainText);

        BigDecimal remain = workOrder.getRemainingAmount();
        BigDecimal totalPaid = workOrder.getTotalPaid();
        BigDecimal totalCost = workOrder.getTotalServiceAmount();

        JLabel lblRemainVal = new JLabel(Format.formatPrice(remain));
        String remainColor = remain.compareTo(BigDecimal.ZERO) > 0 ? "#e74c3c" : "#2ecc71";
        lblRemainVal.putClientProperty(FlatClientProperties.STYLE, "font: bold +4; foreground: " + remainColor);
        summaryBox.add(lblRemainVal, "align right, wrap");

        // UX DÜZELTMESİ: Kısmi Ödeme / Ödendi / Ödenmedi / Ücretsiz
        JLabel lblBadge = new JLabel();
        lblBadge.setOpaque(true);

        if (totalCost.compareTo(BigDecimal.ZERO) == 0) {
            lblBadge.setText("Ücretsiz İşlem");
            lblBadge.putClientProperty(FlatClientProperties.STYLE, "background: #1e3a8a; foreground: #3498db; arc: 15; border: 4,10,4,10; font: bold -1");
        } else if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            lblBadge.setText("Ödenmedi");
            lblBadge.putClientProperty(FlatClientProperties.STYLE, "background: #4a1919; foreground: #e74c3c; arc: 15; border: 4,10,4,10; font: bold -1");
        } else if (remain.compareTo(BigDecimal.ZERO) > 0) {
            lblBadge.setText("Kısmi Ödeme");
            lblBadge.putClientProperty(FlatClientProperties.STYLE, "background: #7a5c13; foreground: #f1c40f; arc: 15; border: 4,10,4,10; font: bold -1");
        } else {
            lblBadge.setText("Ödendi");
            lblBadge.putClientProperty(FlatClientProperties.STYLE, "background: #0b4a3a; foreground: #2ecc71; arc: 15; border: 4,10,4,10; font: bold -1");
        }

        summaryBox.add(lblBadge, "span 2, align right");
        card.add(summaryBox, "align right, w 350!");
        return card;
    }

    private JPanel buildNotesCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[grow]", "[]15[]15[]"));

        JLabel title = new JLabel("Teknisyen Notları");
        title.setIcon(new Ikon("icons/file-text.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        card.add(title, "wrap");

        if (workOrderNotes != null && !workOrderNotes.isEmpty()) {
            DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new java.util.Locale("tr", "TR"));
            for (WorkOrderNote n : workOrderNotes) {
                JPanel noteRow = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[]5[]"));
                noteRow.setOpaque(false);

                JLabel lblNote = new JLabel("<html>" + n.getNote().replace("\n", "<br>") + "</html>");
                lblNote.putClientProperty(FlatClientProperties.STYLE, "font: +1");

                JLabel lblAuthor = createMutedLabel(n.getTechnicianId() == null ? "Sistem" : "Teknisyen");
                JLabel lblDate = createMutedLabel(n.getCreatedAt() != null ? n.getCreatedAt().format(df) : "-");

                noteRow.add(lblNote, "span 2, wrap");
                noteRow.add(lblAuthor);
                noteRow.add(lblDate, "align right");

                card.add(noteRow, "wrap, growx");
                card.add(new JSeparator(), "wrap, growx");
            }
        }

        JTextArea txtNewNote = new JTextArea(3, 20);
        txtNewNote.setLineWrap(true);
        txtNewNote.setWrapStyleWord(true);
        txtNewNote.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Servis süreciyle ilgili notlarınızı buraya yazın...");
        txtNewNote.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); border: 10,10,10,10;");

        JScrollPane scrollNote = new JScrollPane(txtNewNote);
        card.add(scrollNote, "wrap, growx, h 80!");

        JButton btnAddNote = new JButton("+ Not Ekle");
        btnAddNote.putClientProperty(FlatClientProperties.STYLE, "background: #1e3a8a; foreground: #3498db; arc: 10; font: bold; borderWidth: 0");
        btnAddNote.addActionListener(e -> {
            if (txtNewNote.getText().trim().isEmpty()) return;
            WorkOrderNote n = new WorkOrderNote();
            n.setServiceId(workOrder.getId());
            n.setNote(txtNewNote.getText().trim());
            workOrderService.addNote(n).thenAccept(v -> reloadServiceData());
        });

        card.add(btnAddNote, "align right");
        return card;
    }

    private void createHeaderPanel() {
        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx", "[][][][grow][][]", "[]"));
        headerPanel.setOpaque(false);

        JButton btnBack = new JButton(new Ikon("icons/arrow-left.svg", 0.5f));
        btnBack.putClientProperty(FlatClientProperties.STYLE, "arc: 999; background: lighten($Panel.background, 5%);");
        btnBack.addActionListener(e -> FormManager.showForm(AllForms.getForm(FormWorkOrders.class)));

        JPanel titlePanel = new JPanel(new MigLayout("insets 0, gapy 2", "[fill]", "[][]"));
        titlePanel.setOpaque(false);
        lblHeaderTitle = new JLabel("SRV-YENI");
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
            if (newStatus != null && workOrder != null && workOrder.getServiceStatus() != newStatus) {
                workOrder.setServiceStatus(newStatus);
                workOrderService.save(workOrder, true).thenAccept(s -> reloadServiceData());
            }
        });

        statusPanel.add(new JLabel("Durum Güncelle:"));
        statusPanel.add(statusComboBox);

        headerPanel.add(btnBack, "w 40!, h 40!, aligny top");
        headerPanel.add(titlePanel, "gapleft 15, aligny top");
        headerPanel.add(lblHeaderBadge, "gapleft 10, aligny top, gaptop 5");
        headerPanel.add(new JLabel(""), "growx, pushx");
        headerPanel.add(statusPanel, "align right, aligny top");

        add(headerPanel, "wrap, growx");
    }

    private void onActionTaken() {
        ServiceItemAddPanel addPanel = new ServiceItemAddPanel(workOrder, this::reloadServiceData);

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Pencereyi Kapat", SimpleModalBorder.CANCEL_OPTION)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(addPanel, "Parça veya İşlem Ekle", options, (controller, action) -> {
            // Ekleme işlemleri panel içinde hallediliyor, kapatınca sayfayı tazele
            if (action == SimpleModalBorder.CLOSE_OPTION) {
                reloadServiceData();
            }
        }), "itemAddModal");
    }

    private void openItemEditModal(WorkOrderItem item) {
        ServiceItemEditPanel editPanel = new ServiceItemEditPanel(item);

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Değişiklikleri Kaydet", SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(editPanel, "Kalemi Düzenle", options, (controller, action) -> {
            if (action == SimpleModalBorder.YES_OPTION) {
                WorkOrderItem updated = editPanel.getUpdatedItem();
                if (updated == null) {
                    Toast.show(this, Toast.Type.WARNING, "Lütfen geçerli bir isim girin.");
                    controller.consume();
                    return;
                }

                workOrderService.updateItem(updated).thenAccept(v -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, "Kalem başarıyla güncellendi.");
                        reloadServiceData();
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.ERROR, "Güncelleme başarısız: " + ex.getMessage());
                    });
                    return null;
                });
            }
        }), "itemEditModal");
    }

    private JPanel createCustomerCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx, wrap 2", "[grow][]", "[]15[][][]"));
        JLabel title = new JLabel("Müşteri Bilgileri"); title.setIcon(new Ikon("icons/user.svg"));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        lblCustomerName = new JLabel("-"); lblCustomerName.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        lblCustomerPhone = new JLabel("-"); lblCustomerPhone.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        lblCustomerEmail = new JLabel("-"); lblCustomerEmail.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        card.add(title, "span 2"); card.add(lblCustomerName, "span 2"); card.add(lblCustomerPhone, "span 2"); card.add(lblCustomerEmail, "span 2");
        return card;
    }

    private JPanel createDeviceCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[100!][grow]", "[]15[][][][]15[]5[]"));
        JLabel title = new JLabel("Cihaz Bilgileri"); title.setIcon(new Ikon("icons/tablet-smartphone.svg")); title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        lblDeviceType = new JLabel("-"); lblDeviceType.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceBrand = new JLabel("-"); lblDeviceBrand.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceModel = new JLabel("-"); lblDeviceModel.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceSerial = new JLabel("-"); lblDeviceSerial.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        card.add(title, "span 2, wrap");
        card.add(createMutedLabel("Tür:")); card.add(lblDeviceType, "wrap");
        card.add(createMutedLabel("Marka:")); card.add(lblDeviceBrand, "wrap");
        card.add(createMutedLabel("Model:")); card.add(lblDeviceModel, "wrap");
        card.add(createMutedLabel("Seri No:")); card.add(lblDeviceSerial, "wrap");
        card.add(createMutedLabel("Müşteri Şikayeti:"), "span 2, wrap");
        txtReportedFault = new JTextArea();
        txtReportedFault.setEditable(false);
        txtReportedFault.setLineWrap(true);
        txtReportedFault.setWrapStyleWord(true);
        txtReportedFault.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 3%); border: 10,10,10,10;");
        card.add(txtReportedFault, "span 2, growx, h 60!");
        return card;
    }

    private JPanel createTimelineCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[100!][grow, right]", "[]15[][]"));
        JLabel title = new JLabel("Zaman Çizelgesi"); title.setIcon(new Ikon("icons/clock.svg")); title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        lblDateArrival = new JLabel("-"); lblDateArrival.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDateEstimated = new JLabel("-"); lblDateEstimated.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        card.add(title, "span 2, wrap");
        card.add(createMutedLabel("Geliş:")); card.add(lblDateArrival, "wrap");
        card.add(createMutedLabel("Tahmini Bitiş:")); card.add(lblDateEstimated, "wrap");
        return card;
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