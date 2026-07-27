package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.service.DeviceService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

@SystemForm(name = "Cihazlar", description = "Sistemde kayıtlı tüm cihazları listeler")
public class FormDevices extends AbstractTableForm {

    private final DeviceService deviceService;
    private GenericTableModel<Device> tableModel;

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
    protected void setupTable() {
        List<ColumnDef<Device>> columns = Arrays.asList(
                new ColumnDef<>("Tür / Marka", String.class, d ->
                        (d.getDeviceType() != null ? d.getDeviceType().getName() : "-") + " / " +
                        (d.getBrand() != null ? d.getBrand().getName() : "-")),
                new ColumnDef<>("Model", String.class, Device::getModel),
                new ColumnDef<>("Seri No", String.class, d -> d.getSerialNo() != null ? d.getSerialNo() : "-"),
                new ColumnDef<>("Kayıt Tarihi", String.class, d -> Format.formatDate(d.getCreatedAt())),
                new ColumnDef<>("İşlem", String.class, d -> "Detay")
        );
        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);
        configureTableColumns();
    }

    private void configureTableColumns() {
        Integer[] columnAlignments = {
                SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.LEADING, SwingConstants.CENTER
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JLabel) c).putClientProperty(FlatClientProperties.STYLE, "font: bold");
                return c;
            }
        });

        table.getColumnModel().getColumn(4).setCellRenderer(new ActionButtonRenderer());
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
        deviceService.getAll().thenAccept(devices -> SwingUtilities.invokeLater(() -> tableModel.setData(devices)))
                .exceptionally(ex -> {
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
