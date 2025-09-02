package tr.cabro.servicio.application.tablemodal;

import lombok.Getter;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ServiceListTableModel extends AbstractTableModel {

    private final String[] columnNames = { "#", "Müşteri", "Cihaz Türü", "Marka", "Model", "Seri No./IMEI", "Ücret", "Kayıt Tarih", "Teslim Tarihi", "Durum" };

    @Getter
    private final List<Service> services;

    public ServiceListTableModel(List<Service> services) {
        this.services = (services != null) ? services : new ArrayList<>();
    }

    @Override
    public int getRowCount() {
        return services.size();
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
        Service service = services.get(rowIndex);

        Customer customer = ServiceManager.getCustomerService().get(service.getCustomer_id()).orElse(null);


        switch (columnIndex) {
            case 0: return service.getId();
            case 1: return customer;
            case 2: return service.getDevice_type();
            case 3: return service.getDevice_brand();
            case 4: return service.getDevice_model();
            case 5: return service.getDevice_serial();
            case 6: return Format.formatPrice(calculateRemainingAmount(service));
            case 7: return service.getCreated_at();
            case 8: return service.getDelivery_at();
            case 9: return service.getService_status();
            default: return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0: return Integer.class;
            case 1: return Customer.class;
            case 7:
            case 8: return LocalDateTime.class;
            case 9: return ServiceStatus.class;
            default: return String.class;
        }
    }

    public void removeServiceById(int serviceId) {
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).getId() == serviceId) {
                services.remove(i);
                fireTableRowsDeleted(i, i);
                break;
            }
        }
    }

    public void updateService(Service updatedService) {
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).getId() == updatedService.getId()) {
                services.set(i, updatedService);
                fireTableRowsUpdated(i, i);
                break;
            }
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
        double parts = ServiceManager.getRepairService().getTotalPartsCostForService(service.getId());
        double paid = service.getPaid();
        return (labor + parts) - paid;
    }
}
