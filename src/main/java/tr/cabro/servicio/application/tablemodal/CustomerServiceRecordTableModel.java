package tr.cabro.servicio.application.tablemodal;

import tr.cabro.servicio.model.Service;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class CustomerServiceRecordTableModel extends AbstractTableModel {

    private final String[] columnsNames = { "#", "Ürün", "Ücret", "Durum", "Kayıt Tarihi" };
    private final List<Service> services;

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
            case 2: return formatPrice(service.getLabor_cost());
            case 3: return service.getService_status();
            case 4: return formatDate(service.getCreated_at());
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

    private static String formatPrice(double price) {
        Locale turkishLocale = new Locale("tr", "TR");
        return String.format(turkishLocale, "%,.2f ₺", price);
    }

    private static String formatDate(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if (date == null) {
            return "";
        }
        return date.format(formatter);
    }
}
