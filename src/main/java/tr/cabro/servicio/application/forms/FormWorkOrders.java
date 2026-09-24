package tr.cabro.servicio.application.forms;

import java.util.ArrayList;
import tr.cabro.servicio.application.component.table.Lookups;
import tr.cabro.servicio.application.renderer.TooltipCellRenderer;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.renderer.AmountChipCellRenderer;
import tr.cabro.servicio.application.renderer.ChipCellRenderer;
import tr.cabro.servicio.application.renderer.StatusDotCellRenderer;
import tr.cabro.servicio.application.renderer.RowParts;
import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.NewCustomerModal;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.QuickIntakePanel;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.model.dto.PageResult;
import java.util.concurrent.CompletableFuture;
import tr.cabro.servicio.application.component.table.TableActionColumnSupport;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.component.table.TableHeaderFilterSupport;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.database.repository.WorkOrderRepository;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.service.ReportManager;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@SystemForm(name = "Servis Kayıtları", description = "Tüm servis kayıtlarını oluşturmak için kullanılabilir")
public class FormWorkOrders extends AbstractTableForm {

    private final WorkOrderService service;
    private final ReportManager reportManager;
    private GenericTableModel<WorkOrder> tableModal;
    private TableHeaderFilterSupport<WorkOrder> headerFilters;

    // --- SAYFALAMA (DB-tabanlı) ---
    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = AppSettings.get().getTables().getWorkOrderPageSize();
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private PaginationBar paginationBar;

    public FormWorkOrders() {
        this.service = ServiceManager.getWorkOrderService();
        this.reportManager = ServiceManager.getReportManager();
    }

    // -------------------------------------------------------------------------
    // AbstractTableForm implementasyonu
    // -------------------------------------------------------------------------

    @Override
    protected String getNewButtonText()      { return "Yeni Servis"; }

    @Override
    protected String getNewButtonIconPath()  { return "icons/wrench.svg"; }

    @Override
    protected QuickAction getNewQuickAction() { return QuickAction.NEW_SERVICE; }

    @Override
    protected String getSearchPlaceholder()  { return "Müşteri, cihaz, seri no veya SRV no ara…"; }

    @Override
    protected JComponent createPaginationComponent() {
        paginationBar = new PaginationBar(5, PAGE_SIZE_OPTIONS, pageSize,
                page -> { currentPage = page; refreshTable(); },
                newSize -> {
                    pageSize = newSize;
                    currentPage = 1;
                    AppSettings.get().getTables().setWorkOrderPageSize(pageSize);
                    AppSettings.save();
                    refreshTable();
                });
        return paginationBar;
    }

    // --- Görünüm sekmeleri: durum ön ayarları ---

    private static final String VIEW_ALL = "all";
    private static final String VIEW_OPEN = "open";
    private static final String STATUS_KEY = "s.service_status";
    private static final ServiceStatus[] OPEN_STATUSES = {ServiceStatus.UNDER_REPAIR, ServiceStatus.WAITING_FOR_PART,
            ServiceStatus.ANOTHER_SERVICE, ServiceStatus.READY};

    @Override
    protected void initViews() {
        addView(VIEW_ALL, "Tümü");
        addView(VIEW_OPEN, "Atölyede");
        addView(ServiceStatus.UNDER_REPAIR.name(), "Tamirde");
        addView(ServiceStatus.WAITING_FOR_PART.name(), "Parça bekliyor");
        addView(ServiceStatus.READY.name(), "Teslime hazır");
        addView(ServiceStatus.DELIVERED.name(), "Teslim edildi");
    }

    @Override
    protected Map<String, ColumnFilterValue> viewFilters(String key) {
        if (key == null || VIEW_ALL.equals(key)) return Collections.emptyMap();
        if (VIEW_OPEN.equals(key)) {
            String[] names = Arrays.stream(OPEN_STATUSES).map(Enum::name).toArray(String[]::new);
            return Map.of(STATUS_KEY, ColumnFilterValue.enumOf(names));
        }
        return Map.of(STATUS_KEY, ColumnFilterValue.enumOf(key));
    }

    @Override
    protected CompletableFuture<Long> countMatching(Map<String, ColumnFilterValue> filters) {
        return service.searchFilteredPaged(currentSearchTerm, filters, 1, 1,
                WorkOrderRepository.Sort.NEWEST, payFilter).thenApply(PageResult::getTotalItems);
    }

    // --- Ödeme durumu süzgeci (sekme çubuğundaki "Ödeme" menüsü) ---

    private WorkOrderRepository.PayFilter payFilter = WorkOrderRepository.PayFilter.ALL;
    private JButton payButton;

    @Override
    protected JComponent createExtraToolbarComponent() {
        payButton = new JButton();
        payButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        payButton.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            ButtonGroup group = new ButtonGroup();
            for (WorkOrderRepository.PayFilter f : WorkOrderRepository.PayFilter.values()) {
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(payLabel(f), f == payFilter);
                item.addActionListener(a -> {
                    if (f == payFilter) return;
                    payFilter = f;
                    updatePayButton();
                    currentPage = 1;
                    refreshTable();
                });
                group.add(item);
                menu.add(item);
            }
            menu.show(payButton, 0, payButton.getHeight() + 4);
        });
        updatePayButton();
        return payButton;
    }

    private static String payLabel(WorkOrderRepository.PayFilter f) {
        switch (f) {
            case PAID: return "Ödendi";
            case PARTIAL: return "Kısmi ödendi";
            case UNPAID: return "Ödenmedi";
            case FREE: return "Ücretsiz / ücret yok";
            default: return "Tümü";
        }
    }

    private void updatePayButton() {
        boolean active = payFilter != WorkOrderRepository.PayFilter.ALL;
        payButton.setText(active ? "Ödeme · " + payLabel(payFilter) : "Ödeme");
        payButton.setIcon(new Ikon("icons/banknote.svg", 14, active ? "Component.accentColor" : "Label.disabledForeground"));
        payButton.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 4,10,4,10; iconTextGap: 6; focusWidth: 0;"
                + (active ? " foreground: $Component.accentColor; font: bold;"
                + " background: fade($Component.accentColor,12%); toolbar.hoverBackground: fade($Component.accentColor,18%)" : ""));
    }

    @Override
    protected boolean isFilterActive() {
        return super.isFilterActive() || payFilter != WorkOrderRepository.PayFilter.ALL;
    }

    @Override
    protected void clearFilters() {
        payFilter = WorkOrderRepository.PayFilter.ALL;
        updatePayButton();
        super.clearFilters();
    }

    @Override
    protected void onViewChanged(String key) {
        currentPage = 1;
        super.onViewChanged(key);
    }

    @Override
    protected void refreshStats() {
        service.getOpenStatusCounts().thenAccept(counts -> SwingUtilities.invokeLater(() -> {
            long open = counts.values().stream().mapToLong(Long::longValue).sum();
            long ready = counts.getOrDefault(ServiceStatus.READY, 0L);
            long waiting = counts.getOrDefault(ServiceStatus.WAITING_FOR_PART, 0L);
            summary.set(
                    ListSummary.Part.strong(open + " cihaz atölyede"),
                    ready > 0 ? ListSummary.Part.meaning(ready + " teslime hazır", "Servicio.actionColor") : null,
                    waiting > 0 ? ListSummary.Part.meaning(waiting + " parça bekliyor", "Servicio.warningColor") : null);
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Servis özeti yüklenirken hata oluştu", ex);
            return null;
        });
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<WorkOrder>> columns = Arrays.asList(
                new ColumnDef<WorkOrder>("Servis", WorkOrder.class, s -> s).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Müşteri", Customer.class, WorkOrder::getCustomer).alignment(SwingConstants.LEADING)
                        .lookupFilter("s.customer_id", Lookups.customers()),
                new ColumnDef<WorkOrder>("Cihaz ve şikâyet", WorkOrder.class, s -> s).alignment(SwingConstants.LEADING)
                        .lookupFilter("d.device_type_id", Lookups.deviceTypes()),
                new ColumnDef<WorkOrder>("Geliş", WorkOrder.class, s -> s).alignment(SwingConstants.LEADING).dateRangeFilter("s.created_at"),
                new ColumnDef<WorkOrder>("Ücret", WorkOrder.class, s -> s).alignment(SwingConstants.TRAILING),
                ColumnDef.<WorkOrder>badge("Durum", ServiceStatus.class, WorkOrder::getServiceStatus).enumFilter("s.service_status", ServiceStatus.class),
                ColumnDef.<WorkOrder>actionColumn("")
        );
        tableModal = new GenericTableModel<>(columns);
        setTableModel(tableModal);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureTableColumns();
        headerFilters = installHeaderFilters(columns);

        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(0).setMinWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setMinWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(320);
        table.getColumnModel().getColumn(2).setMinWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setMinWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(170);
        table.getColumnModel().getColumn(4).setMinWidth(150);
        table.getColumnModel().getColumn(5).setPreferredWidth(150);
        table.getColumnModel().getColumn(5).setMinWidth(145);
        table.getColumnModel().getColumn(6).setMinWidth(96);
        table.getColumnModel().getColumn(6).setMaxWidth(96);

        addSort(WorkOrderRepository.Sort.NEWEST.name(), "En yeni");
        addSort(WorkOrderRepository.Sort.OLDEST.name(), "En eski");
        addSort(WorkOrderRepository.Sort.LONGEST_IN_STATUS.name(), "En uzun bekleyen");
        addSort(WorkOrderRepository.Sort.RECENT_ACTIVITY.name(), "Son hareket");
        addSort(WorkOrderRepository.Sort.REMAINING_DESC.name(), "Kalan tutar");
        addSort(WorkOrderRepository.Sort.CUSTOMER.name(), "Müşteri adı");
    }

    @Override
    protected void onSortChanged(String key) {
        currentPage = 1;
        super.onSortChanged(key);
    }

    @Override
    protected void initTableFilter(TableModel model) {
        sorter = new TableRowSorter<>(tableModal);
        sorter.setComparator(0, Comparator.comparing(s -> s == null || ((WorkOrder) s).getId() == null ? 0L : ((WorkOrder) s).getId()));
        sorter.setComparator(1, Comparator.comparing(c -> {
            if (c == null) return "";
            Customer cust = (Customer) c;
            return cust.getFullName() + " " + cust.getPhoneNumber1();
        }));
        sorter.setComparator(2, Comparator.comparing(s -> {
            Device d = s == null ? null : ((WorkOrder) s).getDevice();
            return d == null ? "" : d.getBrand() + " " + d.getModel();
        }));
        sorter.setComparator(3, Comparator.comparing(s -> {
            LocalDateTime date = s == null ? null : ((WorkOrder) s).getCreatedAt();
            return date != null ? date : LocalDateTime.MIN;
        }));
        sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(3, SortOrder.DESCENDING)));
    }

    /** Tarih kolonunun indeksi (setupTable'daki kolon sırası). */
    private static final int DATE_COLUMN = 3;

    /**
     * Listeyi durum(lar)a süzerek gösterir (ana sayfadaki servis hattı, dikkat kuyruğu ve alt
     * çubuk buradan açılır). Eşleşen görünüm sekmesi seçilir; argümansız çağrı tümünü gösterir.
     * Form ilk kez açılıyorsa formInit() EDT kuyruğunda olduğu için çağrı ertelenir.
     */
    public void showStatus(ServiceStatus... statuses) {
        SwingUtilities.invokeLater(() -> {
            if (headerFilters == null) return;
            if (searchField != null && !searchField.getText().isEmpty()) searchField.setText("");
            headerFilters.clearAll();
            Set<ServiceStatus> set = new HashSet<>();
            for (ServiceStatus status : statuses) if (status != null) set.add(status);
            String view = VIEW_ALL;
            if (set.size() == 1) view = set.iterator().next().name();
            else if (set.equals(new HashSet<>(Arrays.asList(OPEN_STATUSES)))) view = VIEW_OPEN;
            selectView(view);
        });
    }

    /** Belirli bir günde açılan servisleri gösterir (ana sayfadaki "Bugün alınan"). */
    public void showCreatedOn(java.time.LocalDate date) {
        SwingUtilities.invokeLater(() -> {
            if (headerFilters == null) return;
            if (searchField != null && !searchField.getText().isEmpty()) searchField.setText("");
            currentPage = 1;
            views.select(VIEW_ALL, false);
            ColumnFilterValue value = new ColumnFilterValue();
            value.setDateFrom(date);
            value.setDateTo(date);
            headerFilters.applyOnly(DATE_COLUMN, value);
        });
    }

    /** Atölyede olan (teslim/iade edilmemiş) servisleri gösterir. */
    public void showOpen() {
        showStatus(OPEN_STATUSES);
    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz servis kaydı yok"; }

    @Override
    protected String getEmptyStateDescription() { return "Cihaz kabul ettiğinizde kayıtlar burada listelenir."; }

    @Override
    protected void loadTableData() {
        if (tableModal == null) return;

        service.searchFilteredPaged(currentSearchTerm, effectiveFilters(), currentPage, pageSize,
                        WorkOrderRepository.Sort.valueOf(getSortKey()), payFilter)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    tableModal.setData(result.getItems());
                    if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                    setResultCount(result.getTotalItems());
                    refreshLayout();
                }))
                .exceptionally(ex -> ErrorHandler.handle(this, "Servis tablosu yenilenemedi", ex));
    }

    @Override
    protected void applyFilter() {
        currentSearchTerm = searchField.getText().trim();
        currentPage = 1;
        refreshTable();
    }

    // -------------------------------------------------------------------------
    // Yeni kayıt modalı
    // -------------------------------------------------------------------------

    @Override
    protected void onNew() {
        showIntakeModal(new WorkOrder(), "Servis Kaydı", false);
    }

    /**
     * Yeni servis kaydını müşteri ve/veya cihaz önceden seçili açar (cihaz ya da müşteri detayından).
     * Form ilk kez açılıyorsa formInit() beklensin diye çağrı ertelenir.
     */
    public void startNewFor(Customer customer, Device device) {
        SwingUtilities.invokeLater(() -> {
            WorkOrder wo = new WorkOrder();
            wo.setCustomer(customer);
            if (customer != null) wo.setCustomerId(customer.getId());
            wo.setDevice(device);
            if (device != null) wo.setDeviceId(device.getId());
            showIntakeModal(wo, "Servis Kaydı", false);
        });
    }

    private void openEditModal(WorkOrder workOrder) {
        showIntakeModal(workOrder, "Kayıt Düzenle (SRV-" + workOrder.getId() + ")", true);
    }

    /**
     * Yeni ve düzenleme modalını tek bir yerde yönetir.
     * {@link QuickIntakePanel} artık sabit bir eylem kodu yerine callback alıyor;
     * bu metod callback'in ne yapacağını biliyor, panel bilmiyor.
     *
     * @param data     servis kaydı (yeni veya mevcut)
     * @param title    modal başlığı
     * @param isEdit   true ise güncelleme, false ise yeni kayıt modu
     */
    private void showIntakeModal(WorkOrder data, String title, boolean isEdit) {
        final String MODAL_ID = isEdit ? "service_edit_modal" : "quick_intake_modal";

        // Panel yeni müşteri eklemek istediğinde bu Runnable tetiklenir.
        // Panel'in kendisi modalın nasıl açıldığını bilmez; bu sorumluluğu bu form üstlenir.
        //
        // Lambda içinde 'panel' referansına ihtiyaç duyduğumuz için önce bir dizi wrapper kullanıyoruz.
        // Java'da lambda içindeki değişken effectively-final olmalı; tek elemanlı dizi bu kısıtlamayı aşar.
        QuickIntakePanel[] panelRef = new QuickIntakePanel[1];
        panelRef[0] = new QuickIntakePanel(data, () -> NewCustomerModal.push(MODAL_ID, c -> panelRef[0].appendNewCustomer(c)));
        QuickIntakePanel panel = panelRef[0];

        // Düzenleme modunda sadece "Kaydet" ve "İptal" butonu gösterilir.
        // Yeni kayıt modunda üçüncü seçenek "Servisi Başlat" da eklenir.
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

        // Ctrl+Enter'ın hangi butona karşılık geldiği moda göre değişir; panele bildirilmezse
        // yeni kayıt modunda seçeneklerde olmayan YES_OPTION gönderilir ve kısayol ölü kalır.
        panel.setPrimaryModalAction(isEdit ? SimpleModalBorder.YES_OPTION : SimpleModalBorder.OK_OPTION);

        AppModal.showModal(this, new SimpleModalBorder(panel, title, options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) {
                panel.requestInitialFocus();
                return;
            }
            if (action == SimpleModalBorder.CANCEL_OPTION) return;
            if (action == SimpleModalBorder.CLOSE_OPTION) return;

            // Kaydet veya Başlat seçenekleri → formu topla ve kaydet
            boolean openDetail = (action == SimpleModalBorder.NO_OPTION);
            WorkOrder formData = panel.getData();
            if (formData == null) { controller.consume(); return; }

            service.save(formData, isEdit).thenCompose(saved ->
                    ServiceManager.getDeviceAccessCredentialService()
                            .save(saved.getId(), panel.getDeviceAccessType(), panel.getDeviceAccessSecret())
                            .thenApply(v -> saved)
            ).thenAccept(saved ->
                    SwingUtilities.invokeLater(() -> {
                        String msg = isEdit ? Messages.get("toast.workorder.updated") : Messages.get("toast.workorder.created");
                        Toast.show(this, Toast.Type.SUCCESS, msg);
                        refreshTable();
                        if (openDetail) FormManager.showForm(new FormWorkOrder(saved));
                    })
            ).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(this, "Servis kaydı kaydedilemedi", ex);
            });
        }), MODAL_ID);
    }

    // -------------------------------------------------------------------------
    // Tablo sütun yapılandırması
    // -------------------------------------------------------------------------

    private void configureTableColumns() {
        // Servis: durum rengiyle nokta, SRV no; altında geliş saati.
        table.getColumnModel().getColumn(0).setCellRenderer(new StatusDotCellRenderer<WorkOrder>(
                s -> "SRV-" + s.getId(),
                s -> s.getCreatedAt() != null ? RowParts.when(s.getCreatedAt()) : "",
                s -> s.getServiceStatus() != null ? s.getServiceStatus().getBadgeColor() : null));

        table.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer<Customer>(
                c -> c != null ? c.getFullName() : "Müşterisiz kayıt",
                c -> c != null ? PhoneHelper.formatForDisplay(c.getPhoneNumber1()) : ""));

        // Cihaz kalın, altında şikâyet (yoksa seri no).
        table.getColumnModel().getColumn(2).setCellRenderer(new MultiLineTableCellRenderer<WorkOrder>(
                s -> s.getDevice() != null ? s.getDevice().getBrand() + " " + s.getDevice().getModel() : "Bilinmeyen cihaz",
                s -> {
                    String fault = s.getReportedFault();
                    if (fault != null && !fault.isBlank()) return fault.replaceAll("\\s+", " ").trim();
                    Device d = s.getDevice();
                    return d != null && d.getSerialNo() != null && !d.getSerialNo().isBlank()
                            ? "SN " + d.getSerialNo() : "Şikâyet girilmemiş";
                }));

        // Geliş günü + ne zamandır serviste / teslim / iade; açık iş 7 günü aşınca uyarı rengi; teslimde garanti.
        table.getColumnModel().getColumn(3).setCellRenderer(new MultiLineTableCellRenderer<WorkOrder>(
                s -> s.getCreatedAt() != null ? RowParts.day(s.getCreatedAt()) : "Tarih yok",
                s -> {
                    String deliv = s.getDeliveryDate() != null ? Format.formatDate(s.getDeliveryDate()) : "-";
                    if (s.getServiceStatus() == ServiceStatus.RETURN) return "İade: " + deliv;
                    if (s.getServiceStatus() == ServiceStatus.DELIVERED) return "Teslim: " + deliv + warranty(s);
                    if (s.getCreatedAt() == null) return "";
                    long days = openDays(s);
                    return days <= 0 ? "Bugün geldi" : days + " gündür serviste";
                },
                s -> null,
                s -> s.getServiceStatus() == ServiceStatus.RETURN ? SemanticColor.danger()
                        : isLingering(s) ? SemanticColor.warning() : null));

        // Ücret: toplam kalın; altında ödeme çipi, kısmi/fazla ödemede fark.
        table.getColumnModel().getColumn(4).setCellRenderer(new AmountChipCellRenderer<WorkOrder>()
                .amount(s -> s.getTotalServiceAmount().signum() == 0 ? null : s.getTotalServiceAmount(), RowParts.Money.NEUTRAL)
                .chip(FormWorkOrders::paymentChip)
                .caption(s -> {
                    BigDecimal total = s.getTotalServiceAmount(), paid = s.getTotalPaid();
                    if (paid.signum() > 0 && paid.compareTo(total) < 0) return Format.formatPrice(total.subtract(paid)) + " kalan";
                    if (paid.compareTo(total) > 0 && total.signum() > 0) return Format.formatPrice(paid.subtract(total)) + " fazla";
                    return null;
                })
                .captionColor(s -> s.getTotalPaid().compareTo(s.getTotalServiceAmount()) < 0 ? "Servicio.warningColor" : "Servicio.infoColor"));

        TableActionColumnSupport.install(table, 6, tableModal, new TableActionColumnSupport.Handlers<WorkOrder>() {
            @Override
            public void onEdit(WorkOrder wo) {
                if (wo != null) openEditModal(wo);
            }

            @Override
            public void onDelete(WorkOrder wo) {
                if (wo == null) return;

                DialogHelper.confirmDelete(FormWorkOrders.this, "confirm.delete.workorder", () ->
                        service.delete(wo.getId())
                                .thenAccept(v -> SwingUtilities.invokeLater(() -> {
                                    Toast.show(FormWorkOrders.this, Toast.Type.SUCCESS, Messages.get("toast.record.deleted"));
                                    refreshTable();
                                }))
                                .exceptionally(ex -> ErrorHandler.handle(FormWorkOrders.this, "Servis kaydı silinemedi", ex)),
                        wo.getId());
            }

            @Override
            public void onView(WorkOrder wo) {
                if (wo == null) {
                    Toast.show(FormWorkOrders.this, Toast.Type.WARNING, Messages.get("toast.workorder.notFound"));
                    return;
                }
                service.get(wo.getId()).thenAccept(opt ->
                        SwingUtilities.invokeLater(() -> {
                            if (opt.isPresent()) FormManager.showForm(new FormWorkOrder(opt.get()));
                            else Toast.show(FormWorkOrders.this, Toast.Type.WARNING, Messages.get("toast.workorder.notFound"));
                        })
                ).exceptionally(ex -> ErrorHandler.handle(FormWorkOrders.this, "Servis detayı açılamadı", ex));
            }
        });

    }

    private static RowParts.Badge paymentChip(WorkOrder s) {
        return tr.cabro.servicio.application.utils.ServiceBadges.payment(s);
    }

    /** Teslim edilmiş işin garanti bilgisi (alt satıra eklenir). */
    private static String warranty(WorkOrder s) {
        if (s.getWarrantyEndDate() == null) return "";
        java.time.LocalDate end = s.getWarrantyEndDate().toLocalDate();
        if (end.isBefore(java.time.LocalDate.now())) return "  ·  garanti bitti";
        return "  ·  garanti bitişi " + Format.formatDate(end);
    }

    /** Kayıttan bu yana geçen gün (teslim edilmişse de bugüne göre; yalnızca açık işlerde anlamlıdır). */
    private static long openDays(WorkOrder s) {
        return java.time.temporal.ChronoUnit.DAYS.between(s.getCreatedAt().toLocalDate(), java.time.LocalDate.now());
    }

    /** Atölyede 7 günü aşmış açık iş: satır soluk kalmasın, süre uyarı rengine dönsün. */
    private static boolean isLingering(WorkOrder s) {
        if (s.getCreatedAt() == null) return false;
        ServiceStatus st = s.getServiceStatus();
        boolean open = Arrays.asList(OPEN_STATUSES).contains(st);
        return open && openDays(s) >= 7;
    }
}