package tr.cabro.servicio.application.tablemodal;

import lombok.Getter;
import tr.cabro.servicio.application.listeners.PriceChangeListener;
import tr.cabro.servicio.model.AddedPart;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ServicePartTableModel extends AbstractTableModel {

    private final String[] columnNames = { "Seri No.", "Parça Adı", "Adet", "Alış Fiyatı", "Satış Fiyatı", "Kaldır" };

    @Getter
    private final List<AddedPart> addedParts;

    private final List<PriceChangeListener> priceChangeListeners = new ArrayList<>();

    public ServicePartTableModel(List<AddedPart> addedParts) {
        this.addedParts = (addedParts != null) ? addedParts : new ArrayList<>();
    }

    @Override
    public int getRowCount() {
        return addedParts.size();
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
        AddedPart part = addedParts.get(rowIndex);
        switch (columnIndex) {
            case 0: return part.getSerial_no();
            case 1: return part.getName();
            case 2: return part.getAmount();
            case 3: return part.getPurchasePrice();
            case 4: return part.getSellingPrice();
            default: return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 2) return Integer.class;
        if (columnIndex == 3) return Double.class;
        if (columnIndex == 4) return Double.class;
        return String.class;
    }

    public double getTotalPrice() {
        return addedParts.stream()
                .mapToDouble(AddedPart::getTotal)
                .sum();
    }

    public void addAddedPart(AddedPart addedPart) {
        // Eğer barcode boş değilse, aynı barkodlu parçaları birleştir
        if (addedPart.getBarcode() != null && !addedPart.getBarcode().isEmpty()) {
            for (int i = 0; i < addedParts.size(); i++) {
                AddedPart existing = addedParts.get(i);
                if (addedPart.getBarcode().equals(existing.getBarcode())) {
                    existing.setAmount(existing.getAmount() + addedPart.getAmount());
                    fireTableRowsUpdated(i, i);
                    return;
                }
            }
        }

        // Eğer barcode boş ise (manuel parça) veya hiç eşleşme bulunamadıysa yeni satır ekle
        addedParts.add(addedPart);
        fireTableRowsInserted(addedParts.size() - 1, addedParts.size() - 1);
    }

    public void removeAddedPart(AddedPart addedPart) {
        int index = addedParts.indexOf(addedPart);
        if (index != -1) {
            addedParts.remove(index);
            fireTableRowsDeleted(index, index);
        }
    }

    public void addPriceChangeListener(PriceChangeListener listener) {
        priceChangeListeners.add(listener);
    }

    private void notifyPriceChange() {
        for (PriceChangeListener listener : priceChangeListeners) {
            listener.onPriceOrAmountChanged();
        }
    }

    public void clearParts() {
        addedParts.clear();
        fireTableDataChanged();
    }

    public void setParts(List<AddedPart> parts) {
        addedParts.clear();
        if (parts != null) {
            addedParts.addAll(parts);
        }
        fireTableDataChanged();
    }
}