package tr.cabro.servicio.application.tablemodal;

import lombok.Getter;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Part;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ServicePartTableModel extends AbstractTableModel {

    private final String[] columnNames = { "Barkod", "Parça Adı", "Adet", "Satış Fiyatı" };

    @Getter
    private final List<AddedPart> addedParts;

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
        Part p = ServiceManager.getPartService().getPartByBarcode(part.getBarcode());
        switch (columnIndex) {
            case 0: return part.getBarcode();
            case 1: return p != null ? p.getName() : "[Silinmiş Parça]";
            case 2: return part.getAmount();
            case 3: return part.getSellingPrice();
            default: return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false; // Tüm hücreler salt okunur
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 2) return Integer.class;
        if (columnIndex == 3) return Double.class;
        return String.class;
    }

    public double getTotalPrice() {
        return addedParts.stream()
                .mapToDouble(AddedPart::getTotal)
                .sum();
    }

    /**
     * Aynı barkod varsa yeni satır eklemek yerine adetini artırır.
     */
    public void addAddedPart(AddedPart addedPart) {
        for (int i = 0; i < addedParts.size(); i++) {
            AddedPart existing = addedParts.get(i);
            if (existing.getBarcode().equals(addedPart.getBarcode())) {
                existing.setAmount(existing.getAmount() + addedPart.getAmount());
                fireTableRowsUpdated(i, i);
                return;
            }
        }
        // Yoksa yeni satır ekle
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
}