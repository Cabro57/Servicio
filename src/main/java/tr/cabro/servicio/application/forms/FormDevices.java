package tr.cabro.servicio.application.forms;

import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.model.dto.PageResult;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.component.table.TableHeaderFilterSupport;
import tr.cabro.servicio.application.component.table.TableActionColumnSupport;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.edit.DeviceEditPanel;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.service.DeviceService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@SystemForm(name = "Cihazlar", description = "Sistemde kayıtlı tüm cihazları listeler")
public class FormDevices extends AbstractTableForm {

    private final DeviceService deviceService;
    private GenericTableModel<Device> tableModel;
    private TableHeaderFilterSupport<Device> headerFilters;

    // --- SAYFALAMA (DB-tabanlı) ---
    private static final Integer[] PAGE_SIZE_OPTIONS = {10, 25, 50, 100};
    private int pageSize = AppSettings.get().getTables().getDevicePageSize();
    private int currentPage = 1;
    private String currentSearchTerm = "";
    private PaginationBar paginationBar;

    public FormDevices() {
        this.deviceService = ServiceManager.getDeviceService();
    }

    @Override
    protected String getNewButtonText() {
        return "Yeni Cihaz";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/tablet-smartphone.svg";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Marka, model veya seri no ara…";
    }

    // --- Görünüm sekmeleri: atölyedekiler + cihaz türleri (türler veritabanından) ---

    private static final String VIEW_ALL = "all";
    private static final String VIEW_IN_SHOP = "inshop";
    private static final String TYPE_PREFIX = "type:";

    /** Teslim/iade edilmemiş bir servis kaydı olan cihazlar. */
    private static final String IN_SHOP_CONDITION = "d.id IN (SELECT device_id FROM work_orders WHERE is_deleted = 0 "
            + "AND service_status NOT IN ('DELIVERED', 'RETURN'))";

    @Override
    protected void initViews() {
        addView(VIEW_ALL, "Tümü");
        addView(VIEW_IN_SHOP, "Atölyede");
        ServiceManager.getDeviceDictionaryManager().getAllTypes().thenAccept(types -> SwingUtilities.invokeLater(() -> {
            for (DeviceType type : types) addView(TYPE_PREFIX + type.getId(), type.getName());
            refreshViewCounts();
        })).exceptionally(ex -> null);
    }

    @Override
    protected Map<String, ColumnFilterValue> viewFilters(String key) {
        if (VIEW_IN_SHOP.equals(key)) return Map.of("view:inshop", ColumnFilterValue.condition(IN_SHOP_CONDITION));
        if (key != null && key.startsWith(TYPE_PREFIX)) {
            // Kimlik veritabanından gelen sayı; koşula sayı olarak girer (kullanıcı girdisi değil).
            long typeId = Long.parseLong(key.substring(TYPE_PREFIX.length()));
            return Map.of("view:type", ColumnFilterValue.condition("d.device_type_id = " + typeId));
        }
        return Collections.emptyMap();
    }

    @Override
    protected CompletableFuture<Long> countMatching(Map<String, ColumnFilterValue> filters) {
        return deviceService.searchFilteredPaged(currentSearchTerm, filters, 1, 1).thenApply(PageResult::getTotalItems);
    }

    @Override
    protected void onViewChanged(String key) {
        currentPage = 1;
        super.onViewChanged(key);
    }

    @Override
    protected void refreshStats() {
        CompletableFuture<Long> total = deviceService.searchFilteredPaged(null, Collections.emptyMap(), 1, 1)
                .thenApply(PageResult::getTotalItems);
        CompletableFuture<Long> inShop = deviceService.searchFilteredPaged(null, viewFilters(VIEW_IN_SHOP), 1, 1)
                .thenApply(PageResult::getTotalItems);
        CompletableFuture.allOf(total, inShop).thenRun(() -> SwingUtilities.invokeLater(() -> summary.set(
                ListSummary.Part.strong(total.join() + " kayıtlı cihaz"),
                inShop.join() > 0 ? ListSummary.Part.meaning(inShop.join() + " cihaz şu an atölyede", "Servicio.infoColor") : null
        ))).exceptionally(ex -> ErrorHandler.handle(this, "Cihaz özeti yüklenemedi", ex));
    }

    @Override
    protected JComponent createPaginationComponent() {
        paginationBar = new PaginationBar(5, PAGE_SIZE_OPTIONS, pageSize,
                page -> { currentPage = page; refreshTable(); },
                newSize -> {
                    pageSize = newSize;
                    currentPage = 1;
                    AppSettings.get().getTables().setDevicePageSize(pageSize);
                    AppSettings.save();
                    refreshTable();
                });
        return paginationBar;
    }

    @Override
    protected void applyFilter() {
        currentSearchTerm = searchField.getText().trim();
        currentPage = 1;
        refreshTable();
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<Device>> columns = Arrays.asList(
                new ColumnDef<Device>("Cihaz", Device.class, d -> d).alignment(SwingConstants.LEADING),
                new ColumnDef<Device>("Seri No / IMEI", String.class, d -> d.getSerialNo() != null && !d.getSerialNo().isBlank() ? d.getSerialNo() : "—").alignment(SwingConstants.LEADING),
                new ColumnDef<Device>("Aksesuar", String.class, d -> d.getAccessory() != null && !d.getAccessory().isBlank() ? d.getAccessory() : "—").alignment(SwingConstants.LEADING),
                new ColumnDef<Device>("Kayıt", String.class, d -> Format.formatDate(d.getCreatedAt()))
                        .alignment(SwingConstants.LEADING).dateRangeFilter("d.created_at"),
                ColumnDef.<Device>actionColumn("")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureTableColumns();
        headerFilters = installHeaderFilters(columns);
    }

    private void configureTableColumns() {
        table.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<Device>(
                d -> (d.getBrand() != null ? d.getBrand().getName() + " " : "") + (d.getModel() != null ? d.getModel() : ""),
                d -> d.getDeviceType() != null ? d.getDeviceType().getName() : "Türü belirsiz"));
        table.getColumnModel().getColumn(1).setCellRenderer(StyledLabelCellRenderer.of(SwingConstants.LEADING, null, 8));
        table.getColumnModel().getColumn(2).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground", 8));
        table.getColumnModel().getColumn(3).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground; font: -1", 8));

        TableActionColumnSupport.install(table, 4, tableModel, new TableActionColumnSupport.Handlers<Device>() {
            @Override
            public void onView(Device d) {
                if (d != null) FormManager.showForm(new FormDevice(d));
            }

            @Override
            public void onEdit(Device d) {
                if (d != null) DeviceEditPanel.open(FormDevices.this, d, FormDevices.this::refreshTable);
            }

            @Override
            public void onDelete(Device d) {}
        });

        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(200);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setMaxWidth(96);
        table.getColumnModel().getColumn(4).setMinWidth(110);
    }

    @Override
    protected String getEmptyStateTitle() { return "Henüz cihaz yok"; }

    @Override
    protected String getEmptyStateDescription() { return "Servise gelen cihazlar kaydedildikçe burada toplanır."; }

    @Override
    protected void loadTableData() {
        deviceService.searchFilteredPaged(currentSearchTerm, effectiveFilters(), currentPage, pageSize).thenAccept(result ->
                SwingUtilities.invokeLater(() -> {
                    tableModel.setData(result.getItems());
                    if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                    setResultCount(result.getTotalItems());
                    refreshLayout();
                })
        ).exceptionally(ex -> ErrorHandler.handle(this, "Cihaz listesi yenilenemedi", ex));
    }

    @Override
    protected void onNew() {
        DeviceEditPanel.open(this, new Device(), this::refreshTable);
    }
}
