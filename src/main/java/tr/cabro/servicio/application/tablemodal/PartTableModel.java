package tr.cabro.servicio.application.tablemodal;

import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.util.Format;

import javax.swing.table.AbstractTableModel;
import java.util.Arrays;
import java.util.List;

public class PartTableModel extends AbstractTableModel {

    private final String[] columnsNames = { "SELECT", "Barkod", "Marka", "Ürün Adı", "Cihaz Türü", "Uyumlu Model", "Stok", "Alış Fiyatı", "Satış Fiyatı", "Alış Tarihi" };
    private final List<Part> parts;
    private final Boolean[] selectedRows;

    public PartTableModel(List<Part> parts) {
        this.parts = parts;
        this.selectedRows = new Boolean[parts.size()];
        Arrays.fill(selectedRows, false);
    }

    @Override
    public int getRowCount() {
        return parts.size();
    }

    @Override
    public int getColumnCount() {
        return columnsNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Part device_part = parts.get(rowIndex);

        switch (columnIndex) {
            case 0: return selectedRows[rowIndex];
            case 1: return device_part.getBarcode();
            case 2: return device_part.getBrand();
            case 3: return device_part.getName();
            case 4: return device_part.getDeviceType();
            case 5: return device_part.getModel();
            case 6: return device_part.getStock();
            case 7: return Format.formatPrice(device_part.getPurchasePrice());
            case 8: return Format.formatPrice(device_part.getSalePrice());
            case 9: return Format.formatDate(device_part.getPurchaseDate());
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
        if (columnIndex == 0) return Boolean.class;
        if (columnIndex == 6) return Integer.class;
        //if (columnIndex == 7) return Double.class;
        return String.class;
    }

    public String getProductBarcode(int rowIndex) {
        return getProduct(rowIndex).getBarcode();
    }

    public Part getProduct(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < parts.size()) {
            return parts.get(rowIndex); // Belirtilen satırı döndür
        } else {
            throw new IndexOutOfBoundsException("Geçersiz satır indeksi" + rowIndex);
        }
    }

    public List<Part> getSelectedProducts() {
        List<Part> selected = new java.util.ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            if (selectedRows[i]) {
                selected.add(parts.get(i));
            }
        }
        return selected;
    }

}
