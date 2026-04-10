package tr.cabro.servicio.application.tablemodal;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Tüm sınıflar için ortak olarak kullanılabilen Jenerik Tablo Modeli.
 * @param <T> Tabloda listelenecek nesnenin tipi
 */
public class GenericTableModel<T> extends AbstractTableModel {

    private List<T> dataList;
    private final List<ColumnDef<T>> columns;

    public GenericTableModel(List<ColumnDef<T>> columns) {
        this.columns = columns;
        this.dataList = new ArrayList<>();
    }

    public GenericTableModel(List<T> dataList, List<ColumnDef<T>> columns) {
        this.dataList = dataList != null ? dataList : new ArrayList<>();
        this.columns = columns;
    }

    // --- Tabloyu Yenilemek İçin Altın Vuruş ---
    // Tabloyu sürekli new'lemek yerine sadece veriyi güncelleriz.
    public void setData(List<T> newData) {
        this.dataList = newData != null ? newData : new ArrayList<>();
        fireTableDataChanged(); // Arayüze "Veriler değişti, tabloyu çiz" mesajı gönderir
    }

    // Seçilen satırdaki nesneyi geri döndürür (Düzenleme/Silme işlemleri için)
    public T getItemAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < dataList.size()) {
            return dataList.get(rowIndex);
        }
        return null;
    }

    public List<T> getSelectedItems(int[] selectedRows) {
        List<T> selectedItems = new ArrayList<>();
        for (int row : selectedRows) {
            selectedItems.add(getItemAt(row));
        }
        return selectedItems;
    }

    @Override
    public int getRowCount() {
        return dataList.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int columnIndex) {
        return columns.get(columnIndex).getName();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columns.get(columnIndex).getType();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        T item = dataList.get(rowIndex);
        ColumnDef<T> column = columns.get(columnIndex);
        return column.getValue(item); // ColumnDef içindeki Function'ı çalıştırır
    }

    // TODO: generic tablo için tekraradn yazılacak
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        // Sütun başlığı "İşlem" ise tıklanmasına izin ver
        return getColumnName(columnIndex).equals("İşlem");
    }
}