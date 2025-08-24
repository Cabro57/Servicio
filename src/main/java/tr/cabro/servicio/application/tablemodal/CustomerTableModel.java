package tr.cabro.servicio.application.tablemodal;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.util.FormatUtils;

import javax.swing.table.AbstractTableModel;
import java.util.Arrays;
import java.util.List;

public class CustomerTableModel extends AbstractTableModel {

    private final String[] columnsNames = { "SELECT", "#", "Ad Soyad", "Firma İsmi", "Kimlik No.", "Adres", "Telefon 1", "Tip", "Kayıt Tarihi" };
    private final List<Customer> customers;
    private final Boolean[] selectedRows;

    public CustomerTableModel(List<Customer> customers) {
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
            case 0: return selectedRows[rowIndex];
            case 1: return customer.getID();
            case 2: return customer.getName() + " " + customer.getSurname();
            case 3: return customer.getBusiness_name();
            case 4: return customer.getId_no();
            case 5: return customer.getAddress();
            case 6: return FormatUtils.formatPhoneNumber(customer.getPhone_number_1());
            case 7: return customer.getType();
            case 8: return FormatUtils.formatDate(customer.getCreated_at());
            default: return null;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 0 && aValue instanceof Boolean) {
            selectedRows[rowIndex] = (Boolean) aValue;
            fireTableCellUpdated(rowIndex, columnIndex);
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
            case 0: return Boolean.class;
            case 1: return Integer.class;
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
