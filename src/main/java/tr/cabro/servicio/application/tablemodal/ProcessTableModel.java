package tr.cabro.servicio.application.tablemodal;

import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.util.FormatUtils;

import javax.swing.table.AbstractTableModel;
import java.util.List;

public class ProcessTableModel extends AbstractTableModel {

    private final String[] columnNames = { "İşlem", "Fiyat" };
    private final List<Process> processes;

    public ProcessTableModel(List<Process> initialData) {
        this.processes = initialData;
    }

    @Override
    public int getRowCount() {
        return processes.size();
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
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 1) {
            return Double.class;
        }
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Process row = processes.get(rowIndex);
        switch (columnIndex) {
            case 0: return row.getName();
            case 1: return FormatUtils.formatPrice(row.getPrice());
            default: return null;
        }
    }

    public Process getProcess(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < processes.size()) {
            return processes.get(rowIndex);
        } else {
            throw new IndexOutOfBoundsException("Geçersiz satır indeksi: " + rowIndex);
        }
    }
}
