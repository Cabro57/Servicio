package tr.cabro.servicio.application.tablemodal;

import tr.cabro.servicio.model.Supplier;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SupplierTableModel extends AbstractTableModel {

    private final String[] columnsNames = { "SELECT", "Firma İsmi", "Ad Soyad", "Telefon", "Adress", "Kayıt Tarihi" };
    private final List<Supplier> suppliers;
    private final Boolean[] selectedRows;

    public SupplierTableModel(List<Supplier> suppliers) {
        this.suppliers = suppliers;
        this.selectedRows = new Boolean[suppliers.size()];
        Arrays.fill(selectedRows, false);
    }

    @Override
    public int getRowCount() {
        return suppliers.size();
    }

    @Override
    public int getColumnCount() {
        return columnsNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Supplier supplier = suppliers.get(rowIndex);

        switch (columnIndex) {
            case 0: return selectedRows[rowIndex];
            case 1: return supplier.getBusiness_name();
            case 2: return supplier.getName();
            case 3: return formatPhoneNumber(supplier.getPhone());
            case 4: return supplier.getAddress();
            case 5: return formatDate(supplier.getCreated_at());
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
        return columnIndex == 0 ? Boolean.class : String.class;
    }

    public Supplier getSupplier(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < suppliers.size()) {
            return suppliers.get(rowIndex);
        } else {
            throw new IndexOutOfBoundsException("Geçersiz satır indeksi: " + rowIndex);
        }
    }

    public int getSupplierID(int rowIndex) {
        return getSupplier(rowIndex).getId();
    }

    private static String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 10) return phoneNumber;
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

    public List<Supplier> getSelectedSuppliers() {
        List<Supplier> selected = new java.util.ArrayList<>();
        for (int i = 0; i < suppliers.size(); i++) {
            if (selectedRows[i]) {
                selected.add(suppliers.get(i));
            }
        }
        return selected;
    }
}
