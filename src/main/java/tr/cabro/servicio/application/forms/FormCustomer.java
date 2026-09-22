package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.table.ActionButtonEditor;
import tr.cabro.servicio.application.component.table.DynamicActionColumnSupport;
import tr.cabro.servicio.application.component.table.TableActionEvent;
import tr.cabro.servicio.application.panels.CollectionPanel;
import tr.cabro.servicio.application.panels.QuickIntakePanel;
import tr.cabro.servicio.application.panels.customer.CustomerActivity;
import tr.cabro.servicio.application.panels.customer.CustomerHeaderPanel;
import tr.cabro.servicio.application.panels.customer.CustomerListSection;
import tr.cabro.servicio.application.panels.customer.CustomerOverviewPanel;
import tr.cabro.servicio.application.panels.customer.CustomerSectionNav;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.dto.OpenDocumentDto;
import tr.cabro.servicio.model.enums.AllocationTargetType;
import tr.cabro.servicio.model.enums.DeviceTransactionType;
import tr.cabro.servicio.model.enums.PaymentStatus;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.model.enums.SaleType;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.*;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Müşteri detayı: üstte kimlik şeridi (bakiye + sık işlemler), solda bölüm menüsü, sağda
 * seçili bölüm tam genişlikte. Eski düzende üç kart 320px'lik bir kolona üst üste diziliyor,
 * sağdaki tek tablo sıkışıyor ve satış/ödeme/2.el geçmişi hiç görünmüyordu.
 */
public class FormCustomer extends Form {

    private static final String SECTION_OVERVIEW = "overview";
    private static final String SECTION_SERVICES = "services";
    private static final String SECTION_SALES = "sales";
    private static final String SECTION_PAYMENTS = "payments";
    private static final String SECTION_DEVICES = "devices";
    private static final String SECTION_SECONDHAND = "secondhand";

    /** Son hareketler listesinde gösterilen en fazla satır; tamamı ilgili bölümde. */
    private static final int RECENT_ACTIVITY_LIMIT = 15;

    private Customer customer;
    private final WorkOrderService workOrderService;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final SaleService saleService;
    private final DeviceService deviceService;
    private final DeviceTransactionService deviceTransactionService;

    private CustomerHeaderPanel header;
    private CustomerSectionNav nav;
    private final JPanel sections = new JPanel(new CardLayout());

    private CustomerOverviewPanel overview;
    private CustomerListSection<WorkOrder> servicesSection;
    private CustomerListSection<Sale> salesSection;
    private CustomerListSection<Payment> paymentsSection;
    private CustomerListSection<Device> devicesSection;
    private CustomerListSection<DeviceTransaction> secondHandSection;

    private List<WorkOrder> workOrders = List.of();

    public FormCustomer(Customer customer) {
        this.customer = customer;
        this.workOrderService = ServiceManager.getWorkOrderService();
        this.customerService = ServiceManager.getCustomerService();
        this.paymentService = ServiceManager.getPaymentService();
        this.saleService = ServiceManager.getSaleService();
        this.deviceService = ServiceManager.getDeviceService();
        this.deviceTransactionService = ServiceManager.getDeviceTransactionService();
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 16", "[200!][grow, fill]", "[pref][grow, fill]"));

        header = new CustomerHeaderPanel(FormManager::undo, this::openWhatsApp, this::openEditModal,
                this::openCollection, this::openQuickIntakeModal);
        add(header, "span 2, growx, wmin 0, wrap");

        nav = new CustomerSectionNav(this::showSection);
        nav.addSection(SECTION_OVERVIEW, "Genel Bakış", "icons/layout-dashboard.svg", "Ctrl+1");
        nav.addSection(SECTION_SERVICES, "Servisler", "icons/wrench.svg", "Ctrl+2");
        nav.addSection(SECTION_SALES, "Satışlar", "icons/shopping-bag.svg", "Ctrl+3");
        nav.addSection(SECTION_PAYMENTS, "Ödemeler", "icons/hand-coins.svg", "Ctrl+4");
        nav.addSection(SECTION_DEVICES, "Cihazlar", "icons/tablet-smartphone.svg", "Ctrl+5");
        nav.addSection(SECTION_SECONDHAND, "2.El Alım-Satım", "icons/handshake.svg", "Ctrl+6");
        nav.setCount(SECTION_OVERVIEW, null);
        add(nav, "aligny top, growx");

        sections.setOpaque(false);
        overview = new CustomerOverviewPanel(this::openWorkOrder, this::openDocument, this::openActivity,
                this::openQuickIntakeModal);
        sections.add(overview, SECTION_OVERVIEW);
        sections.add(createServicesSection(), SECTION_SERVICES);
        sections.add(createSalesSection(), SECTION_SALES);
        sections.add(createPaymentsSection(), SECTION_PAYMENTS);
        sections.add(createDevicesSection(), SECTION_DEVICES);
        sections.add(createSecondHandSection(), SECTION_SECONDHAND);
        add(sections, "grow, wmin 0, hmin 0");

        installSectionShortcuts();
        nav.select(SECTION_OVERVIEW);
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

    private void showSection(String id) {
        ((CardLayout) sections.getLayout()).show(sections, id);
    }

    /** Ctrl+1..6 bölümler arasında geçer; menüdeki ipuçları aynı sırayı gösterir. */
    private void installSectionShortcuts() {
        InputMap inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        for (int i = 0; i < 6; i++) {
            String sectionId = nav.idAt(i);
            String actionKey = "customerSection" + i;
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.CTRL_DOWN_MASK), actionKey);
            getActionMap().put(actionKey, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    nav.select(sectionId);
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Bölümler
    // -------------------------------------------------------------------------

    private JComponent createServicesSection() {
        servicesSection = new CustomerListSection<>("Servis Geçmişi", Arrays.asList(
                new ColumnDef<>("Kayıt No", String.class, s -> "SRV-" + s.getId()),
                new ColumnDef<>("Cihaz", Device.class, WorkOrder::getDevice),
                new ColumnDef<>("Tarih", String.class, s -> s.getCreatedAt() != null ? s.getCreatedAt().format(DateFormats.dateTime()) : "-"),
                ColumnDef.badge("Durum", ServiceStatus.class, WorkOrder::getServiceStatus),
                ColumnDef.currency("Ücret", WorkOrder::getTotalServiceAmount),
                ColumnDef.actionColumn("İşlem")
        ), "Henüz servis kaydı yok", "Bu müşteri için açılan servis kayıtları burada listelenir.", false);
        servicesSection.setEmptyAction("Yeni servis kaydı", this::openQuickIntakeModal);
        servicesSection.setOnOpen(this::openWorkOrder);

        JTable table = servicesSection.getTable();
        table.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer<Device>(
                d -> d != null ? d.getBrand() + " " + d.getModel() : "Belirtilmedi",
                d -> d != null && d.getSerialNo() != null ? "SN: " + d.getSerialNo() : "Bilinmiyor"));
        table.getColumnModel().getColumn(5).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(5).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onView(int row) {
                WorkOrder s = workOrderAt(table, row);
                if (s != null) openWorkOrder(s);
            }

            @Override
            public void onEdit(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                WorkOrder s = workOrderAt(table, row);
                if (s != null) openEditModal(s);
            }

            @Override
            public void onDelete(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                WorkOrder s = workOrderAt(table, row);
                if (s == null) return;

                DialogHelper.confirmDelete(FormCustomer.this, "confirm.delete.workorder", () ->
                                workOrderService.delete(s.getId())
                                        .thenAccept(v -> SwingUtilities.invokeLater(() -> {
                                            Toast.show(FormCustomer.this, Toast.Type.SUCCESS, Messages.get("toast.record.deleted"));
                                            refreshData();
                                        }))
                                        .exceptionally(ex -> ErrorHandler.handle(FormCustomer.this, "Servis kaydı silinemedi", ex)),
                        s.getId());
            }
        }));

        table.getColumnModel().getColumn(0).setMaxWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setMaxWidth(180);
        table.getColumnModel().getColumn(5).setMinWidth(120);
        return servicesSection;
    }

    private WorkOrder workOrderAt(JTable table, int viewRow) {
        return servicesSection.getTableModel().getItemAt(table.convertRowIndexToModel(viewRow));
    }

    private JComponent createSalesSection() {
        salesSection = new CustomerListSection<>("Satışlar ve İadeler", Arrays.asList(
                new ColumnDef<>("Fiş No", String.class, this::saleLabel),
                new ColumnDef<>("Tarih", String.class, s -> s.getSaleDate() != null ? s.getSaleDate().format(DateFormats.dateTime()) : "-"),
                ColumnDef.currency("Toplam", Sale::getTotalAmount),
                ColumnDef.badge("Durum", PaymentStatus.class, s -> PaymentService.resolveStatus(s.getTotalAmount(), s.getTotalPaid())),
                new ColumnDef<Sale>("", String.class, s -> "").editable(true)
        ), "Satış kaydı yok", "Bu müşteriye POS ekranından yapılan satışlar burada listelenir.", false);
        salesSection.setOnOpen(this::openSale);
        DynamicActionColumnSupport.install(salesSection.getTable(), 4, salesSection.getTableModel(), List.of(
                DynamicActionColumnSupport.button("icons/eye.svg", new Color(13, 110, 253), "Detay", this::openSale)));
        salesSection.getTable().getColumnModel().getColumn(4).setMaxWidth(60);
        return salesSection;
    }

    private JComponent createPaymentsSection() {
        paymentsSection = new CustomerListSection<>("Ödeme Geçmişi", Arrays.asList(
                new ColumnDef<>("Tarih", String.class, p -> p.getPaymentDate() != null ? p.getPaymentDate().format(DateFormats.dateTime()) : "-"),
                ColumnDef.badge("Ödeme Türü", PaymentType.class, Payment::getPaymentType),
                ColumnDef.currency("Tutar", Payment::getAmount),
                new ColumnDef<>("Not", String.class, p -> p.getNote() != null ? p.getNote() : "")
        ), "Tahsilat yok", "Bu müşteriden alınan ödemeler burada listelenir.", false);
        paymentsSection.setEmptyAction("Tahsilat al", this::openCollection);
        JTable table = paymentsSection.getTable();
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(360);
        return paymentsSection;
    }

    private JComponent createDevicesSection() {
        devicesSection = new CustomerListSection<>("Servise Getirdiği Cihazlar", Arrays.asList(
                new ColumnDef<>("Cihaz", Device.class, d -> d),
                new ColumnDef<>("Tür", String.class, d -> d.getDeviceType() != null ? d.getDeviceType().toString() : "-"),
                new ColumnDef<>("Aksesuar", String.class, d -> d.getAccessory() != null ? d.getAccessory() : ""),
                new ColumnDef<Device>("", String.class, d -> "").editable(true)
        ), "Kayıtlı cihaz yok", "Servis kaydı açılan cihazlar burada listelenir.", false);
        devicesSection.setOnOpen(this::openDevice);
        JTable table = devicesSection.getTable();
        table.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Device>(
                d -> d != null ? d.getBrand() + " " + d.getModel() : "Belirtilmedi",
                d -> d != null && d.getSerialNo() != null ? "SN: " + d.getSerialNo() : "Seri no yok"));
        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        DynamicActionColumnSupport.install(table, 3, devicesSection.getTableModel(), List.of(
                DynamicActionColumnSupport.button("icons/eye.svg", new Color(13, 110, 253), "Cihaz detayı", this::openDevice)));
        table.getColumnModel().getColumn(3).setMaxWidth(60);
        return devicesSection;
    }

    private JComponent createSecondHandSection() {
        secondHandSection = new CustomerListSection<>("2.El Alım-Satım", Arrays.asList(
                new ColumnDef<>("Tarih", String.class, t -> t.getTransactionDate() != null ? t.getTransactionDate().format(DateFormats.dateTime()) : "-"),
                new ColumnDef<>("İşlem", String.class, t -> t.getType() == DeviceTransactionType.PURCHASE ? "Müşteriden alım" : "Müşteriye satış"),
                new ColumnDef<>("Cihaz", Device.class, DeviceTransaction::getDevice),
                ColumnDef.currency("Fiyat", DeviceTransaction::getPrice),
                new ColumnDef<DeviceTransaction>("", String.class, t -> "").editable(true)
        ), "2.el işlem yok", "Bu müşteriden alınan ya da ona satılan 2.el cihazlar burada listelenir.", false);
        secondHandSection.setOnOpen(this::openTransactionDevice);
        JTable table = secondHandSection.getTable();
        table.getColumnModel().getColumn(2).setCellRenderer(new MultiLineTableCellRenderer<Device>(
                d -> d != null ? d.getBrand() + " " + d.getModel() : "Belirtilmedi",
                d -> d != null && d.getSerialNo() != null ? "SN: " + d.getSerialNo() : ""));
        table.getColumnModel().getColumn(2).setPreferredWidth(280);
        DynamicActionColumnSupport.install(table, 4, secondHandSection.getTableModel(), List.of(
                DynamicActionColumnSupport.button("icons/eye.svg", new Color(13, 110, 253), "Cihaz detayı", this::openTransactionDevice)));
        table.getColumnModel().getColumn(4).setMaxWidth(60);
        return secondHandSection;
    }

    private String saleLabel(Sale s) {
        return s.getType() == SaleType.RETURN
                ? "İADE-" + s.getId() + " (SAT-" + s.getParentSaleId() + ")"
                : "SAT-" + s.getId();
    }

    // -------------------------------------------------------------------------
    // Veri yükleme
    // -------------------------------------------------------------------------

    private void refreshData() {
        header.setCustomer(customer);
        Long id = customer.getId();

        CompletableFuture<List<WorkOrder>> workOrdersF = workOrderService.getAll(id);
        CompletableFuture<List<Sale>> salesF = saleService.getByCustomer(id);
        CompletableFuture<List<Payment>> paymentsF = paymentService.getByCustomer(id);
        CompletableFuture<List<Device>> devicesF = deviceService.getAllByCustomerId(id);
        CompletableFuture<List<DeviceTransaction>> tradesF = deviceTransactionService.getByCustomerId(id);

        workOrdersF.thenAccept(list -> SwingUtilities.invokeLater(() -> {
            workOrders = list;
            servicesSection.setData(list);
            overview.setWorkOrders(list);
            nav.setCount(SECTION_SERVICES, list.size());
        })).exceptionally(ex -> sectionError(servicesSection, "Servis kayıtları yüklenemedi", ex));

        salesF.thenAccept(list -> SwingUtilities.invokeLater(() -> {
            salesSection.setData(list);
            nav.setCount(SECTION_SALES, list.size());
        })).exceptionally(ex -> sectionError(salesSection, "Satışlar yüklenemedi", ex));

        paymentsF.thenAccept(list -> SwingUtilities.invokeLater(() -> {
            paymentsSection.setData(list);
            nav.setCount(SECTION_PAYMENTS, list.size());
            overview.setTotalCollected(list.stream().map(Payment::getAmount)
                    .filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        })).exceptionally(ex -> sectionError(paymentsSection, "Ödemeler yüklenemedi", ex));

        devicesF.thenAccept(list -> SwingUtilities.invokeLater(() -> {
            devicesSection.setData(list);
            nav.setCount(SECTION_DEVICES, list.size());
        })).exceptionally(ex -> sectionError(devicesSection, "Cihazlar yüklenemedi", ex));

        tradesF.thenAccept(list -> SwingUtilities.invokeLater(() -> {
            secondHandSection.setData(list);
            nav.setCount(SECTION_SECONDHAND, list.size());
        })).exceptionally(ex -> sectionError(secondHandSection, "2.el işlemleri yüklenemedi", ex));

        CompletableFuture.allOf(workOrdersF, salesF, paymentsF, tradesF)
                .thenAccept(v -> {
                    List<CustomerActivity> activity = buildActivity(workOrdersF.join(), salesF.join(), paymentsF.join(), tradesF.join());
                    SwingUtilities.invokeLater(() -> overview.setRecentActivity(activity));
                })
                .exceptionally(ex -> {
                    Servicio.getLogger().error("Müşteri hareketleri yüklenemedi", ex);
                    return null;
                });

        refreshAccount();
    }

    private void refreshAccount() {
        paymentService.getCustomerBalance(customer.getId()).thenAccept(opt -> SwingUtilities.invokeLater(() ->
                header.setBalance(opt.map(b -> b.getBalance()).orElse(BigDecimal.ZERO))
        )).exceptionally(ex -> ErrorHandler.handle(this, "Cari bakiye yüklenemedi", ex));

        paymentService.getOpenDocuments(customer.getId()).thenAccept(docs -> SwingUtilities.invokeLater(() ->
                overview.setOpenDocuments(docs)
        )).exceptionally(ex -> ErrorHandler.handle(this, "Açık belgeler yüklenemedi", ex));
    }

    private Void sectionError(CustomerListSection<?> section, String message, Throwable ex) {
        Servicio.getLogger().error(message, ex);
        SwingUtilities.invokeLater(() -> section.showError(message));
        return null;
    }

    /** Servis, satış, tahsilat ve 2.el işlemlerini tek tarih sırasına dizer; en yeni önce. */
    private List<CustomerActivity> buildActivity(List<WorkOrder> orders, List<Sale> sales,
                                                 List<Payment> payments, List<DeviceTransaction> trades) {
        List<CustomerActivity> all = new ArrayList<>();
        for (WorkOrder w : orders) {
            String device = w.getDevice() != null ? w.getDevice().getBrand() + " " + w.getDevice().getModel() : "Cihaz belirtilmedi";
            String status = w.getServiceStatus() != null ? " — " + w.getServiceStatus().getDisplayName() : "";
            all.add(new CustomerActivity(CustomerActivity.Kind.SERVICE, w.getCreatedAt(),
                    "SRV-" + w.getId() + " · " + device + status, w.getTotalServiceAmount(), w));
        }
        for (Sale s : sales) {
            CustomerActivity.Kind kind = s.getType() == SaleType.RETURN ? CustomerActivity.Kind.RETURN : CustomerActivity.Kind.SALE;
            all.add(new CustomerActivity(kind, s.getSaleDate(), saleLabel(s), s.getTotalAmount(), s));
        }
        for (Payment p : payments) {
            String type = p.getPaymentType() != null ? p.getPaymentType().getDisplayName() : "Ödeme";
            String note = p.getNote() != null && !p.getNote().isBlank() ? " · " + p.getNote() : "";
            all.add(new CustomerActivity(CustomerActivity.Kind.PAYMENT, p.getPaymentDate(), type + note, p.getAmount(), p));
        }
        for (DeviceTransaction t : trades) {
            CustomerActivity.Kind kind = t.getType() == DeviceTransactionType.PURCHASE
                    ? CustomerActivity.Kind.DEVICE_PURCHASE : CustomerActivity.Kind.DEVICE_SALE;
            String device = t.getDevice() != null ? t.getDevice().getBrand() + " " + t.getDevice().getModel() : "Cihaz";
            all.add(new CustomerActivity(kind, t.getTransactionDate(), device, t.getPrice(), t));
        }
        all.sort(Comparator.comparing(CustomerActivity::getDate, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())));
        return all.size() > RECENT_ACTIVITY_LIMIT ? new ArrayList<>(all.subList(0, RECENT_ACTIVITY_LIMIT)) : all;
    }

    // -------------------------------------------------------------------------
    // Kayıt açma
    // -------------------------------------------------------------------------

    private void openWorkOrder(WorkOrder workOrder) {
        FormManager.showForm(new FormWorkOrder(workOrder));
    }

    private void openSale(Sale sale) {
        FormManager.showForm(new FormSale(sale));
    }

    private void openDevice(Device device) {
        FormManager.showForm(new FormDevice(device));
    }

    /** 2.el sorgusu cihazın yalnızca özet alanlarını taşıyor; detay formu için cihaz tam haliyle yüklenir. */
    private void openTransactionDevice(DeviceTransaction transaction) {
        deviceService.get(transaction.getDeviceId()).thenAccept(opt -> SwingUtilities.invokeLater(() ->
                opt.ifPresent(this::openDevice)
        )).exceptionally(ex -> ErrorHandler.handle(this, "Cihaz yüklenemedi", ex));
    }

    private void openDocument(OpenDocumentDto document) {
        if (document.getDocumentType() == AllocationTargetType.WORK_ORDER) {
            workOrders.stream().filter(w -> w.getId().equals(document.getDocumentId())).findFirst()
                    .ifPresent(this::openWorkOrder);
        } else {
            saleService.getById(document.getDocumentId()).thenAccept(opt -> SwingUtilities.invokeLater(() ->
                    opt.ifPresent(this::openSale)
            )).exceptionally(ex -> ErrorHandler.handle(this, "Satış yüklenemedi", ex));
        }
    }

    private void openActivity(CustomerActivity activity) {
        Object source = activity.getSource();
        if (source instanceof WorkOrder w) {
            openWorkOrder(w);
        } else if (source instanceof Sale s) {
            openSale(s);
        } else if (source instanceof DeviceTransaction t) {
            openTransactionDevice(t);
        } else if (source instanceof Payment) {
            nav.select(SECTION_PAYMENTS);
        }
    }

    // -------------------------------------------------------------------------
    // Kimlik şeridi işlemleri
    // -------------------------------------------------------------------------

    private void openWhatsApp() {
        String digits = PhoneHelper.toWhatsAppDigits(customer.getPhoneNumber1());
        if (digits == null) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.whatsapp.noPhone"));
            return;
        }
        if (!DesktopHelper.browseUrl("https://wa.me/" + digits)) {
            Toast.show(this, Toast.Type.ERROR, Messages.get("toast.generic.error", "WhatsApp açılamadı"));
        }
    }

    private void openCollection() {
        CollectionPanel.open(this, customer, this::refreshData);
    }

    private void openEditModal() {
        final String modalId = "customer_detail_edit";
        CustomerEditPanel panel = new CustomerEditPanel(customer);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };

        AppModal.showModal(this, new SimpleModalBorder(panel, "Müşteri Düzenle", options, (controller, action) -> {
            if (action != SimpleModalBorder.OK_OPTION) return;
            Customer updated = panel.getData();
            if (updated == null) {
                controller.consume();
                return;
            }
            updated.setId(customer.getId());
            updated.setCreatedAt(customer.getCreatedAt());

            customerService.save(updated, true).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.entity.updated", updated.getFullName()));
                formRefresh();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(this, "Müşteri güncellenemedi", ex);
            });
        }), modalId);
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
        panelRef[0] = new QuickIntakePanel(data, () -> AppModal.pushModalDeferred(() -> {
            CustomerEditPanel newCustomerPanel = new CustomerEditPanel(new Customer());
            return new SimpleModalBorder(newCustomerPanel, "Yeni Müşteri", SimpleModalBorder.YES_NO_OPTION, (c1, a1) -> {
                if (a1 != SimpleModalBorder.YES_OPTION) return;
                Customer newCustomer = newCustomerPanel.getData();
                if (newCustomer == null) { c1.consume(); return; }
                c1.consume();
                newCustomer.setCreatedAt(LocalDateTime.now());
                customerService.save(newCustomer, false).thenAccept(saved ->
                        SwingUtilities.invokeLater(() -> {
                            panelRef[0].appendNewCustomer(saved);
                            AppModal.popModal(MODAL_ID);
                        })
                );
            });
        }, MODAL_ID));
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

        // Ctrl+Enter'ın karşılığı moda göre değişir; bildirilmezse kısayol ölü kalır.
        panel.setPrimaryModalAction(isEdit ? SimpleModalBorder.YES_OPTION : SimpleModalBorder.OK_OPTION);

        AppModal.showModal(this, new SimpleModalBorder(panel, title, options, (controller, action) -> {
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
                        String msg = isEdit ? Messages.get("toast.workorder.updated") : Messages.get("toast.workorder.created");
                        Toast.show(this, Toast.Type.SUCCESS, msg);
                        refreshData();
                        if (openDetail) FormManager.showForm(new FormWorkOrder(saved));
                    })
            ).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    controller.consume();
                    String cause = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    Toast.show(this, Toast.Type.ERROR, Messages.get("toast.generic.error", cause));
                });
                Servicio.getLogger().error("Servis kayıt hatası", ex);
                return null;
            });
        }), MODAL_ID);
    }
}
