package tr.cabro.servicio.application.tablemodal;

import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.table.AbstractTableModel;
import java.util.List;

public class CustomerServiceRecordTableModel extends AbstractTableModel {

    private final String[] columnsNames = { "#", "Ürün", "Ücret", "Durum", "Kayıt Tarihi" };
    private final List<Service> services;

    private final RepairService repairService = ServiceManager.getRepairService();

    public CustomerServiceRecordTableModel(List<Service> services) {
        this.services = services;
    }

    @Override
    public int getRowCount() {
        return services.size();
    }

    @Override
    public int getColumnCount() {
        return columnsNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Service service = services.get(rowIndex);

        switch (columnIndex) {
            case 0: return service.getId();
            case 1: return service.getDevice_brand() + " " + service.getDevice_model();
            case 2: return Format.formatPrice(calculateRemainingAmount(service));
            case 3: return service.getService_status();
            case 4: return Format.formatDate(service.getCreated_at());
            default: return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
    }

    @Override
    public String getColumnName(int column) {
        return columnsNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0: return Integer.class;
            default: return String.class;
        }
    }

    public Service getService(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < services.size()) {
            return services.get(rowIndex);
        } else {
            throw new IndexOutOfBoundsException("Geçersiz satır indeksi: " + rowIndex);
        }
    }

    private double calculateRemainingAmount(Service service) {
        double labor = service.getLabor_cost();
        double parts = repairService.getTotalPartsCostForService(service.getId());
        double paid = service.getPaid();
        return (labor + parts) - paid;
    }
}
