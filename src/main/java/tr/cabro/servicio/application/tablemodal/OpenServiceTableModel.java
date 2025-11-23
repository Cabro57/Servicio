package tr.cabro.servicio.application.tablemodal;

import lombok.*;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class OpenServiceTableModel extends AbstractTableModel {

    private final String[] columnNames = { "Müşteri", "Cihaz", "Ücret", "Durum", "Kayıt Tarih" };

    @Getter
    private List<Service> data;

    public OpenServiceTableModel() {
        this.data = new ArrayList<>();
    }

    @Override
    public int getRowCount() {
        return data.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Service service = data.get(rowIndex);
        Customer customer = ServiceManager.getCustomerService().get(service.getCustomerId()).orElse(null);

        switch (columnIndex) {
            case 0: return customer;
            case 1: return service.getDevice();
            case 2: return calculateRemainingAmount(service);
            case 3: return service.getServiceStatus();
            case 4: return service.getCreatedAt();
            default: return null;
        }
    }

    public Service getService(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < data.size()) {
            return data.get(rowIndex);
        } else {
            throw new IndexOutOfBoundsException("Geçersiz satır indeksi: " + rowIndex);
        }
    }

    private double calculateRemainingAmount(Service service) {
        double labor = service.getLaborCost();
        double parts = ServiceManager.getRepairService().getTotalPartsCostForService(service.getId());
        double paid = service.getPaid();
        return (labor + parts) - paid;
    }

    public void setData(List<Service> data) {
        this.data = data;
        fireTableDataChanged();
    }
}
