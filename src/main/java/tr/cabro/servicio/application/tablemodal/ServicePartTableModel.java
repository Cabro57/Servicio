package tr.cabro.servicio.application.tablemodal;

import lombok.Getter;
import tr.cabro.servicio.application.listeners.PriceChangeListener;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.util.Format;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ServicePartTableModel extends AbstractTableModel {

    private final String[] columnNames = { "Parça", "Adet", "Satış Fiyatı", "" };

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
            case 0: return part.getName();
            case 1: return part.getAmount();
            case 2: return Format.formatPrice(part.getSellingPrice());
            default: return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 3;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    public double getTotalPrice() {
        return addedParts.stream()
                .mapToDouble(AddedPart::getTotal)
                .sum();
    }

    public void addAddedPart(AddedPart data) {
        addedParts.add(data);
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