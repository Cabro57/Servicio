package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.application.component.table.PaginationBar;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.component.table.TableHeaderFilterSupport;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
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
        return "Yeni Cihaz Ekle";
    }

    @Override
    protected String getTableTitleText() {
        return "Tüm Cihazlar";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Marka, model veya seri no ara...";
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
                new ColumnDef<Device>("Tür / Marka", String.class, d ->
                        (d.getDeviceType() != null ? d.getDeviceType().getName() : "-") + " / " +
                        (d.getBrand() != null ? d.getBrand().getName() : "-")).alignment(SwingConstants.LEADING),
                new ColumnDef<Device>("Model", String.class, Device::getModel).alignment(SwingConstants.LEADING),
                new ColumnDef<Device>("Seri No", String.class, d -> d.getSerialNo() != null ? d.getSerialNo() : "-").alignment(SwingConstants.LEADING),
                new ColumnDef<Device>("Kayıt Tarihi", String.class, d -> Format.formatDate(d.getCreatedAt()))
                        .alignment(SwingConstants.LEADING).dateRangeFilter("d.created_at"),
                ColumnDef.<Device>actionColumn("İşlem")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        configureTableColumns();
        headerFilters = installHeaderFilters(columns);
    }

    private void configureTableColumns() {
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JLabel) c).putClientProperty(FlatClientProperties.STYLE, "font: bold");
                return c;
            }
        });

        table.getColumnModel().getColumn(4).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onView(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                Device d = tableModel.getItemAt(modelRow);
                if (d != null) FormManager.showForm(new FormDevice(d));
            }

            @Override
            public void onEdit(int row) {}

            @Override
            public void onDelete(int row) {}
        }));

        table.getColumnModel().getColumn(4).setMaxWidth(120);
        table.getColumnModel().getColumn(4).setMinWidth(90);
    }

    @Override
    protected void refreshTable() {
        Map<String, ColumnFilterValue> filters = headerFilters != null ? headerFilters.getActiveFilters() : java.util.Collections.emptyMap();

        deviceService.searchFilteredPaged(currentSearchTerm, filters, currentPage, pageSize).thenAccept(result ->
                SwingUtilities.invokeLater(() -> {
                    tableModel.setData(result.getItems());
                    if (paginationBar != null) paginationBar.setPageRange(result.getPage(), result.getTotalPages());
                    refreshLayout();
                })
        ).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Cihazlar yüklenemedi: " + ex.getMessage()));
            Servicio.getLogger().error("Cihaz listesi yenileme hatası", ex);
            return null;
        });
    }

    @Override
    protected void onNew() {
        Toast.show(this, Toast.Type.INFO, "Cihazlar servis kaydı üzerinden eklenir.");
    }
}
