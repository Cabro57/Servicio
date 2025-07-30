package tr.cabro.servicio.application.tablemodal;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.CustomerType;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SearchCustomerTableModel extends AbstractTableModel {

    private final String[] columnsNames = { "Tip", "Ad Soyad", "Firma İsmi", "Telefon", "TC Kimlik No" };
    private final List<Customer> customers;
    private final Boolean[] selectedRows;

    public SearchCustomerTableModel(List<Customer> customers) {
        this.customers = customers;
        this.selectedRows = new Boolean[customers.size()];
        Arrays.fill(selectedRows, false);
    }

    @Override
    public int getRowCount() {
        return customers.size();
    }

    @Override
    public int getColumnCount() {
        return columnsNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Customer customer = customers.get(rowIndex);

        switch (columnIndex) {
            case 0: return CustomerType.of(customer.getStatus());
            case 1: return customer.getName() + " " + customer.getSurname();
            case 2: return customer.getBusiness_name();
            case 3: return formatPhoneNumber(customer.getPhone_number_1());
            case 4: return customer.getId_no();
            default: return null;
        }
    }

    @Override
    public String getColumnName(int column) {
        return columnsNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0: return String.class;
            case 1: return String.class;
            default: return String.class;
        }
    }

    public Customer getCustomer(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < customers.size()) {
            return customers.get(rowIndex);
        } else {
            throw new IndexOutOfBoundsException("Geçersiz satır indeksi: " + rowIndex);
        }
    }

    public int getCustomerID(int rowIndex) {
        return getCustomer(rowIndex).getID();
    }

    private static String formatPhoneNumber(String phoneNumber) {
        return String.format("%s %s %s %s",
                phoneNumber.substring(0, 3),
                phoneNumber.substring(3, 6),
                phoneNumber.substring(6, 8),
                phoneNumber.substring(8, 10));
    }

    private static String formatPrice(double price) {
        Locale turkishLocale = new Locale("tr", "TR");
        return String.format(turkishLocale, "%,.2f ₺", price);
    }

    private static String formatDate(LocalDateTime date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return date.format(formatter);
    }

    public List<Customer> getSelectedCustomers() {
        List<Customer> selected = new java.util.ArrayList<>();
        for (int i = 0; i < customers.size(); i++) {
            if (selectedRows[i]) {
                selected.add(customers.get(i));
            }
        }
        return selected;
    }
}
