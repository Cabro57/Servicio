package tr.cabro.servicio.application.tablemodal;

import lombok.Getter;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ServiceListTableModel extends AbstractTableModel {

    private final String[] columnNames = { "#", "Müşteri", "Cihaz Türü", "Marka", "Model", "Ücret", "Kayıt Tarih", "Teslim Tarihi", "Durum" };

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
            case 5: return formatPrice(calculateRemainingAmount(service));
            case 6: return formatDate(service.getCreated_at());
            case 7: return formatDate(service.getDelivery_at());
            case 8: return service.getService_status();
            default: return null;
        }
    }

    private double calculateRemainingAmount(Service service) {
        double labor = service.getLabor_cost();
        double parts = ServiceManager.getPartService().getTotalPartsCostForService(service.getId());
        double paid = service.getPaid();
        return (labor + parts) - paid;
    }

    private static String formatPrice(double price) {
        Locale turkishLocale = new Locale("tr", "TR");
        return String.format(turkishLocale, "%,.2f ₺", price);
    }

    private static String formatDate(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return date.format(formatter);
    }
}
