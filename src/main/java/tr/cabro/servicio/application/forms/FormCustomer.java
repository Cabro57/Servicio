package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import tr.cabro.servicio.application.renderer.*;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.panels.QuickIntakePanel;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FormCustomer extends Form {

    private Customer customer;
    private final WorkOrderService workOrderService;
    private final CustomerService customerService;

    private GenericTableModel<WorkOrder> tableModel;
    private JTable table;

    private JLabel lblNameBadge;
    private JLabel valTotalDevices, valActiveServices, valCompletedServices, valTotalSpent;

    public FormCustomer(Customer customer) {
        this.customer = customer;
        this.workOrderService = ServiceManager.getWorkOrderService();
        this.customerService = ServiceManager.getCustomerService();
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 20", "[::320][grow]", "[pref][grow]"));
        createHeader();
        createLeftColumn();
        createRightColumn();
        refreshData();
    }

    @Override
    public void formRefresh() {
        customerService.get(customer.getId()).thenAccept(updated ->
                updated.ifPresent(c -> {
                    this.customer = c;
                    SwingUtilities.invokeLater(this::refreshData);
                })
        );
    }

    // -------------------------------------------------------------------------
    // Servis modalları
    // -------------------------------------------------------------------------

    /**
     * Müşterisi önceden dolu olan yeni servis kaydı modalını açar.
     */
    private void openQuickIntakeModal() {
        WorkOrder preFilledOrder = new WorkOrder();
        preFilledOrder.setCustomer(this.customer);
        preFilledOrder.setCustomerId(this.customer.getId());

        showIntakeModal(preFilledOrder, "Yeni Servis Kaydı", false);
    }

    /**
     * Mevcut bir servis kaydını düzenlemek için modal açar.
     */
    private void openEditModal(WorkOrder workOrder) {
        if (workOrder == null || workOrder.getId() <= 0) return;
        showIntakeModal(workOrder, "Kayıt Düzenle (SRV-" + workOrder.getId() + ")", true);
    }

    /**
     * Yeni ve düzenleme modallarını tek yerden yönetir.
     * <p>
     * {@link QuickIntakePanel} artık sabit eylem kodu yerine {@code Runnable} callback alıyor;
     * yeni müşteri ekleme akışı bu metod içindeki closure'da kalıyor, panel bilmiyor.
     *
     * @param data   servis kaydı (yeni veya mevcut)
     * @param title  modal başlığı
     * @param isEdit true ise güncelleme, false ise yeni kayıt modu
     */
    private void showIntakeModal(WorkOrder data, String title, boolean isEdit) {
        final String MODAL_ID = isEdit ? "customer_service_edit_modal" : "customer_intake_modal";

        // Panel referansını lambda içinden güncelleyebilmek için tek elemanlı dizi kullanıyoruz.
        QuickIntakePanel[] panelRef = new QuickIntakePanel[1];
        panelRef[0] = new QuickIntakePanel(data, () -> {
            CustomerEditPanel newCustomerPanel = new CustomerEditPanel(new Customer());
            ModalDialog.pushModal(
                    new SimpleModalBorder(newCustomerPanel, "Yeni Müşteri", SimpleModalBorder.YES_NO_OPTION, (c1, a1) -> {
                        if (a1 != SimpleModalBorder.YES_OPTION) return;
                        Customer newCustomer = newCustomerPanel.getData();
                        if (newCustomer == null) { c1.consume(); return; }
                        c1.consume();
                        newCustomer.setCreatedAt(LocalDateTime.now());
                        customerService.save(newCustomer, false).thenAccept(saved ->
                                SwingUtilities.invokeLater(() -> {
                                    panelRef[0].appendNewCustomer(saved);
                                    ModalDialog.popModal(MODAL_ID);
                                })
                        );
                    }),
                    MODAL_ID
            );
        });
        QuickIntakePanel panel = panelRef[0];

        SimpleModalBorder.Option[] options = isEdit
                ? new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Değişiklikleri Kaydet", SimpleModalBorder.YES_OPTION),
                new SimpleModalBorder.Option("İptal",                  SimpleModalBorder.CANCEL_OPTION)
        }
                : new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Servisi Kaydet",  SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("Servisi Başlat",  SimpleModalBorder.NO_OPTION),
                new SimpleModalBorder.Option("İptal",           SimpleModalBorder.CANCEL_OPTION)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(panel, title, options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) {
                panel.requestInitialFocus();
                return;
            }
            if (action == SimpleModalBorder.CANCEL_OPTION) return;

            boolean openDetail = (action == SimpleModalBorder.NO_OPTION);
            WorkOrder formData = panel.getData();
            if (formData == null) { controller.consume(); return; }

            workOrderService.save(formData, isEdit).thenAccept(saved ->
                    SwingUtilities.invokeLater(() -> {
                        String msg = isEdit ? "Servis bilgileri güncellendi." : "Servis başarıyla kaydedildi.";
                        Toast.show(this, Toast.Type.SUCCESS, msg);
                        refreshData();
                        if (openDetail) FormManager.showForm(new FormWorkOrder(saved));
                    })
            ).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    controller.consume();
                    String cause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    Toast.show(this, Toast.Type.ERROR, "Hata: " + cause);
                });
                Servicio.getLogger().error("Servis kayıt hatası", ex);
                return null;
            });
        }), MODAL_ID);
    }

    // -------------------------------------------------------------------------
    // UI oluşturma
    // -------------------------------------------------------------------------

    private void createHeader() {
        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx, gap 15", "[][grow]", "[]"));
        headerPanel.setOpaque(false);

        JButton btnBack = new JButton(new Ikon("icons/arrow-left.svg", 1.2f));
        btnBack.putClientProperty(FlatClientProperties.STYLE, "arc: 15; background: lighten($Panel.background, 5%); borderWidth: 0; margin: 8,10,8,10;");
        btnBack.addActionListener(e -> FormManager.undo());
        headerPanel.add(btnBack, "cell 0 0, aligny center");

        JPanel titleBox = new JPanel(new MigLayout("insets 0, gap 0", "[grow]", "[][]"));
        titleBox.setOpaque(false);

        lblNameBadge = new JLabel();
        lblNameBadge.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");
        updateNameAndBadge();

        JLabel lblSubtitle = new JLabel("Müşteri Profili ve Servis Geçmişi");
        lblSubtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");

        titleBox.add(lblNameBadge, "wrap");
        titleBox.add(lblSubtitle);
        headerPanel.add(titleBox, "cell 1 0");
        add(headerPanel, "span 2, growx, wrap");
    }

    private void updateNameAndBadge() {
        if (customer == null) return;
        String isim = (customer.getType() == CustomerType.SMALL_BUSINESS || customer.getType() == CustomerType.DEALER)
                ? customer.getBusinessName()
                : customer.getFullName();
        String badge = customer.getType() == CustomerType.NORMAL ? "Bireysel" : "Kurumsal";
        lblNameBadge.setText("<html><span>" + isim + "</span>&nbsp;&nbsp;"
                + "<span style='background-color:#2a2d36; color:#a0a0a0; font-size:11px; "
                + "padding:3px 8px; border-radius:6px; font-weight:normal;'> " + badge + " </span></html>");
    }

    private void createLeftColumn() {
        JPanel leftPanel = new JPanel(new MigLayout("insets 0, gapy 20, fillx", "[grow]", "[pref][pref]"));
        leftPanel.setOpaque(false);
        leftPanel.add(createContactCard(), "growx, wrap");
        leftPanel.add(createSummaryCard(), "growx");
        add(leftPanel, "cell 0 1, aligny top");
    }

    private JPanel createContactCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new MigLayout("insets 20, gapy 15, fillx", "[25!][grow]", "[]15[][][][]"));

        JLabel title = new JLabel("İletişim Bilgileri");
        title.setIcon(new Ikon("icons/user.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2; iconTextGap: 10");
        card.add(title, "span 2, wrap");

        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));
        addContactRow(card, "icons/phone.svg",    "Telefon",      customer.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(customer.getPhoneNumber1()) : "-");
        addContactRow(card, "icons/mail.svg",     "E-posta",      customer.getEmail()        != null ? customer.getEmail()        : "-");
        addContactRow(card, "icons/map-pin.svg",  "Adres",        customer.getAddress()      != null ? customer.getAddress()      : "-");
        addContactRow(card, "icons/calendar.svg", "Kayıt Tarihi", customer.getCreatedAt()    != null ? customer.getCreatedAt().format(df) : "-");
        return card;
    }

    private void addContactRow(JPanel parent, String iconPath, String label, String value) {
        JLabel icon = new JLabel(new Ikon(iconPath, 0.75f));
        icon.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        JLabel lblLabel = new JLabel(label);
        lblLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");

        JLabel lblValue = new JLabel(value);
        lblValue.putClientProperty(FlatClientProperties.STYLE, "font: bold");

        parent.add(icon,     "aligny top, span 1 2");
        parent.add(lblLabel, "wrap");
        parent.add(lblValue, "gapbottom 10, wrap");
    }

    private JPanel createSummaryCard() {
        JPanel card = createRoundedCard();
        card.setLayout(new MigLayout("insets 20, gapy 15, fillx", "[grow]", "[]10[grow]"));

        JLabel title = new JLabel("Müşteri Özeti");
        title.setIcon(new Ikon("icons/wrench.svg", 1f));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2; iconTextGap: 10");
        card.add(title, "wrap");

        JPanel grid = new JPanel(new MigLayout("insets 0, gap 10, fill", "[grow][grow]", "[grow][grow]"));
        grid.setOpaque(false);

        valTotalDevices = createStatValueLabel();
        valActiveServices = createStatValueLabel();
        valActiveServices.putClientProperty(FlatClientProperties.STYLE, "font: bold +10; foreground: $Component.accentColor");
        valCompletedServices = createStatValueLabel();
        valCompletedServices.putClientProperty(FlatClientProperties.STYLE, "font: bold +10; foreground: #2ecc71");
        valTotalSpent = createStatValueLabel();

        grid.add(createMiniStatBox(valTotalDevices,      "Toplam Cihaz"), "grow");
        grid.add(createMiniStatBox(valActiveServices,    "Aktif İşlem"),  "grow, wrap");
        grid.add(createMiniStatBox(valCompletedServices, "Tamamlanan"),   "grow");
        grid.add(createMiniStatBox(valTotalSpent,        "Harcama"),      "grow");

        card.add(grid, "grow");
        return card;
    }

    private JLabel createStatValueLabel() {
        JLabel lbl = new JLabel("0");
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        lbl.putClientProperty(FlatClientProperties.STYLE, "font: bold +10");
        return lbl;
    }

    private JPanel createMiniStatBox(JLabel valueLabel, String title) {
        JPanel box = new JPanel(new MigLayout("insets 15, fill", "[grow]", "[grow][pref]"));
        box.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: lighten($Panel.background, 3%);");

        JLabel lblTitle = new JLabel(title);
        lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
        lblTitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");

        box.add(valueLabel, "grow, center, wrap");
        box.add(lblTitle,   "grow, center");
        return box;
    }

    private void createRightColumn() {
        JPanel rightPanel = createRoundedCard();
        rightPanel.setLayout(new MigLayout("insets 20, fill", "[grow]", "[pref]15[grow]"));

        JPanel toolbar = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[]"));
        toolbar.setOpaque(false);

        JLabel title = new JLabel("Servis Geçmişi");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");

        JButton btnNewService = new JButton("Yeni Servis Kaydı");
        btnNewService.setIcon(new Ikon("icons/plus.svg", 1f));
        btnNewService.putClientProperty(FlatClientProperties.STYLE, "background: $Component.accentColor; foreground: #ffffff; arc: 10; font: bold");
        btnNewService.addActionListener(e -> openQuickIntakeModal());

        toolbar.add(title);
        toolbar.add(btnNewService);
        rightPanel.add(toolbar, "wrap, growx");

        setupTable();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        rightPanel.add(scroll, "grow, push");

        add(rightPanel, "cell 1 1, grow");
    }

    // -------------------------------------------------------------------------
    // Tablo
    // -------------------------------------------------------------------------

    private void setupTable() {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        List<ColumnDef<WorkOrder>> columns = Arrays.asList(
                new ColumnDef<>("Kayıt No", String.class,        s -> "SRV-" + s.getId()),
                new ColumnDef<>("Cihaz",    Device.class,        WorkOrder::getDevice),
                new ColumnDef<>("Tarih",    String.class,        s -> s.getCreatedAt() != null ? s.getCreatedAt().format(df) : "-"),
                new ColumnDef<>("Durum",    ServiceStatus.class, WorkOrder::getServiceStatus),
                new ColumnDef<>("Ücret",    BigDecimal.class, WorkOrder::getTotalServiceAmount),
                new ColumnDef<>("İşlem",    String.class,        s -> "Detay")
        );

        tableModel = new GenericTableModel<>(columns);
        table = new JTable(tableModel);
        configureTable();
    }

    private void configureTable() {
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:40; separatorColor:$TableHeader.background; font:bold +1;");
        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:50; showHorizontalLines:true; intercellSpacing:0,1; selectionBackground:$TableHeader.hoverBackground;");

        Integer[] alignments = {
                SwingConstants.CENTER, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.CENTER, SwingConstants.TRAILING, SwingConstants.CENTER
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, alignments));

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
                ((JLabel) c).putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Label.disabledForeground");
                return c;
            }
        });

        table.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer<Device>(
                d -> d != null ? d.getBrand() + " " + d.getModel() : "Belirtilmedi",
                d -> d != null && d.getSerialNo() != null ? "SN: " + d.getSerialNo() : "Bilinmiyor",
                d -> null,
                d -> new Color(130, 130, 130)
        ));

        table.getColumnModel().getColumn(3).setCellRenderer(new UniversalVisualizableRenderer());

        table.getColumnModel().getColumn(4).setCellRenderer(new CurrencyTableCellRenderer());

        table.getColumnModel().getColumn(5).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(5).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onView(int row) {
                int modelRow = table.convertRowIndexToModel(row);
                WorkOrder s = tableModel.getItemAt(modelRow);
                if (s != null) FormManager.showForm(new FormWorkOrder(s));
            }

            @Override
            public void onEdit(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                WorkOrder s = tableModel.getItemAt(modelRow);
                if (s != null) openEditModal(s);
            }

            @Override
            public void onDelete(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                WorkOrder s = tableModel.getItemAt(modelRow);
                if (s == null) return;

                int confirm = JOptionPane.showConfirmDialog(
                        FormCustomer.this,
                        "SRV-" + s.getId() + " numaralı servis kaydını silmek istediğinize emin misiniz?\nBu işlem geri alınamaz.",
                        "Silme Onayı",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
                );
                if (confirm != JOptionPane.YES_OPTION) return;

                workOrderService.delete(s.getId())
                        .thenAccept(v -> SwingUtilities.invokeLater(() -> {
                            Toast.show(FormCustomer.this, Toast.Type.SUCCESS, "Kayıt başarıyla silindi.");
                            refreshData();
                        }))
                        .exceptionally(ex -> {
                            SwingUtilities.invokeLater(() ->
                                    Toast.show(FormCustomer.this, Toast.Type.ERROR, "Silme başarısız: " + ex.getCause().getMessage()));
                            return null;
                        });
            }
        }));

        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setMaxWidth(180);
        table.getColumnModel().getColumn(5).setMinWidth(120);
    }

    // -------------------------------------------------------------------------
    // Veri yükleme
    // -------------------------------------------------------------------------

    private void refreshData() {
        updateNameAndBadge();
        workOrderService.getAll(customer.getId()).thenAccept(services ->
                SwingUtilities.invokeLater(() -> {
                    tableModel.setData(services);
                    calculateStats(services);
                })
        );
    }

    private void calculateStats(List<WorkOrder> workOrders) {
        int active = 0, completed = 0;
        BigDecimal totalSpent = BigDecimal.ZERO;

        for (WorkOrder s : workOrders) {
            if (s.getPayments() != null) {
                for (WorkOrderPayment payment : s.getPayments()) {
                    totalSpent = totalSpent.add(payment.getAmount());
                }
            }
            if (s.getServiceStatus() == ServiceStatus.DELIVERED || s.getServiceStatus() == ServiceStatus.RETURN) {
                completed++;
            } else {
                active++;
            }
        }

        valTotalDevices.setText(String.valueOf(workOrders.size()));
        valActiveServices.setText(String.valueOf(active));
        valCompletedServices.setText(String.valueOf(completed));
        valTotalSpent.setText(Format.formatPrice(totalSpent));
    }

    // -------------------------------------------------------------------------
    // Yardımcı
    // -------------------------------------------------------------------------

    private JPanel createRoundedCard() {
        JPanel p = new JPanel();
        p.putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");
        return p;
    }
}