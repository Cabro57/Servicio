package tr.cabro.servicio.application.forms;

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
        return service.searchFilteredPaged(currentSearchTerm, filters, 1, 1).thenApply(PageResult::getTotalItems);
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
                new ColumnDef<WorkOrder>("No",       Long.class,          WorkOrder::getId).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Müşteri",  Customer.class,      WorkOrder::getCustomer).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Cihaz",    Device.class,        WorkOrder::getDevice).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Şikâyet",  String.class,        WorkOrder::getReportedFault).alignment(SwingConstants.LEADING),
                new ColumnDef<WorkOrder>("Geliş",    WorkOrder.class,     s -> s).alignment(SwingConstants.LEADING).dateRangeFilter("s.created_at"),
                ColumnDef.<WorkOrder>currency("Kalan", WorkOrder::getRemainingAmount),
                ColumnDef.<WorkOrder>badge("Durum", ServiceStatus.class, WorkOrder::getServiceStatus).enumFilter("s.service_status", ServiceStatus.class),
                ColumnDef.<WorkOrder>actionColumn("")
        );
        tableModal = new GenericTableModel<>(columns);
        setTableModel(tableModal);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureTableColumns();
        headerFilters = installHeaderFilters(columns);
    }

    @Override
    protected void initTableFilter(TableModel model) {
        sorter = new TableRowSorter<>(tableModal);

        sorter.setComparator(1, Comparator.comparing(c -> {
            if (c == null) return "";
            Customer cust = (Customer) c;
            return cust.getFullName() + " " + cust.getPhoneNumber1();
        }));
        sorter.setComparator(2, Comparator.comparing(s -> {
            if (s == null) return "";
            Device d = (Device) s;
            return d.getBrand() + " " + d.getModel();
        }));
        sorter.setComparator(4, Comparator.comparing(s -> {
            if (s == null) return LocalDateTime.MIN;
            LocalDateTime date = ((WorkOrder) s).getCreatedAt();
            return date != null ? date : LocalDateTime.MIN;
        }));

        sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(4, SortOrder.DESCENDING)));
    }

    /** Tarih kolonunun indeksi (setupTable'daki kolon sırası). */
    private static final int DATE_COLUMN = 4;

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

        service.searchFilteredPaged(currentSearchTerm, effectiveFilters(), currentPage, pageSize)
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
        // Hizalama ColumnDef.alignment(...) üzerinden; burada yalnızca bu forma özel renderer'lar var.
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, false, row, col);
                label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 4));
                label.setForeground(UIManager.getColor("Label.disabledForeground"));
                if (value instanceof Long) label.setText("SRV-" + value);
                return label;
            }
        });

        table.getColumnModel().getColumn(1).setCellRenderer(
                new MultiLineTableCellRenderer<Customer>(
                        c -> c != null ? c.getFullName() : "Müşterisiz kayıt",
                        c -> c != null ? PhoneHelper.formatForDisplay(c.getPhoneNumber1()) : ""
                )
        );
        table.getColumnModel().getColumn(2).setCellRenderer(
                new MultiLineTableCellRenderer<Device>(
                        d -> d != null ? d.getBrand() + " " + d.getModel() : "Bilinmeyen cihaz",
                        d -> d != null && d.getSerialNo() != null && !d.getSerialNo().isBlank() ? "SN " + d.getSerialNo() : "Seri no yok"
                )
        );
        table.getColumnModel().getColumn(3).setCellRenderer(new TooltipCellRenderer());

        // Geliş tarihi + ne zamandır serviste / ne zaman teslim edildi.
        table.getColumnModel().getColumn(4).setCellRenderer(
                new MultiLineTableCellRenderer<WorkOrder>(
                        s -> s.getCreatedAt() != null ? s.getCreatedAt().format(DateFormats.dateTime()) : "Tarih yok",
                        s -> {
                            String deliv = s.getDeliveryDate() != null ? Format.formatDate(s.getDeliveryDate()) : "-";
                            if (s.getServiceStatus() == ServiceStatus.RETURN) return "İade: " + deliv;
                            if (s.getServiceStatus() == ServiceStatus.DELIVERED) return "Teslim: " + deliv;
                            if (s.getCreatedAt() == null) return "";
                            long days = java.time.temporal.ChronoUnit.DAYS.between(s.getCreatedAt().toLocalDate(), java.time.LocalDate.now());
                            return days <= 0 ? "Bugün geldi" : days + " gündür serviste";
                        },
                        s -> null,
                        s -> s.getServiceStatus() == ServiceStatus.RETURN ? SemanticColor.danger() : null
                )
        );

        // Kalan ücret: dükkâna ödenecek tutar uyarı renginde, ödenmişse soluk "Ödendi".
        table.getColumnModel().getColumn(5).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.OWED, "Ödendi"));

        TableActionColumnSupport.install(table, 7, tableModal, new TableActionColumnSupport.Handlers<WorkOrder>() {
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

        table.getColumnModel().getColumn(0).setMaxWidth(90);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(190);
        table.getColumnModel().getColumn(2).setPreferredWidth(190);
        table.getColumnModel().getColumn(2).setMinWidth(170);
        table.getColumnModel().getColumn(3).setMinWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(230);
        table.getColumnModel().getColumn(4).setPreferredWidth(150);
        table.getColumnModel().getColumn(4).setMinWidth(135);
        table.getColumnModel().getColumn(5).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setMinWidth(95);
        table.getColumnModel().getColumn(6).setPreferredWidth(150);
        table.getColumnModel().getColumn(6).setMinWidth(145);
        table.getColumnModel().getColumn(7).setMinWidth(96);
        table.getColumnModel().getColumn(7).setMaxWidth(96);
    }
}