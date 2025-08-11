package tr.cabro.servicio.application.tablemodal;

import lombok.Getter;
import tr.cabro.servicio.application.listeners.PriceChangeListener;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Part;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ServicePartTableModel extends AbstractTableModel {

    private final String[] columnNames = { "Seri No.", "Parça Adı", "Adet", "Satış Fiyatı" };

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
        Part p = ServiceManager.getPartService().getPartByBarcode(part.getBarcode());
        switch (columnIndex) {
            case 0: return part.getSerial_no();
            case 1: return p != null ? p.getName() : "[Silinmiş Parça]";
            case 2: return part.getAmount();
            case 3: return part.getSellingPrice();
            default: return null;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        AddedPart part = addedParts.get(rowIndex);
        boolean changed = false;

        switch (columnIndex) {
            case 0: // Seri No
                if (aValue != null) {
                    String serial = aValue.toString().trim();
                    part.setSerial_no(serial);
                    changed = true;
                    fireTableCellUpdated(rowIndex, columnIndex);
                }
                break;

            case 2: // Adet
                if (aValue instanceof Number) {
                    int newAmount = ((Number) aValue).intValue();
                    if (newAmount > 0) {
                        part.setAmount(newAmount);
                        changed = true;
                        fireTableCellUpdated(rowIndex, columnIndex);
                        fireTableCellUpdated(rowIndex, 3); // Toplam fiyat vs. değiştiyse
                    } else {
                        JOptionPane.showMessageDialog(null,
                                "Adet 0'dan büyük olmalıdır.",
                                "Hatalı Değer",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    try {
                        int newAmount = Integer.parseInt(aValue.toString());
                        if (newAmount > 0) {
                            part.setAmount(newAmount);
                            changed = true;
                            fireTableCellUpdated(rowIndex, columnIndex);
                            fireTableCellUpdated(rowIndex, 3);
                        } else {
                            JOptionPane.showMessageDialog(null,
                                    "Adet 0'dan büyük olmalıdır.",
                                    "Hatalı Değer",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(null,
                                "Geçerli bir sayı giriniz.",
                                "Hatalı Değer",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
                break;

            case 3: // Satış Fiyatı
                if (aValue instanceof Number) {
                    double newPrice = ((Number) aValue).doubleValue();
                    part.setSellingPrice(newPrice);
                    changed = true;
                    fireTableCellUpdated(rowIndex, columnIndex);
                } else {
                    try {
                        double newPrice = Double.parseDouble(aValue.toString());
                        part.setSellingPrice(newPrice);
                        changed = true;
                        fireTableCellUpdated(rowIndex, columnIndex);
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(null,
                                "Geçerli bir fiyat giriniz.",
                                "Hatalı Değer",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
                break;
        }

        if (changed) {
            notifyPriceChange();
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        // Seri No, Adet ve Satış Fiyatı düzenlenebilir
        return columnIndex == 0 || columnIndex == 2 || columnIndex == 3;
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

    public void addPriceChangeListener(PriceChangeListener listener) {
        priceChangeListeners.add(listener);
    }

    private void notifyPriceChange() {
        for (PriceChangeListener listener : priceChangeListeners) {
            listener.onPriceOrAmountChanged();
        }
    }
}