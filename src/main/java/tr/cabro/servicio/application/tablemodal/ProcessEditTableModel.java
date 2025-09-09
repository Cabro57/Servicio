package tr.cabro.servicio.application.tablemodal;

import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.util.Format;

import javax.swing.table.AbstractTableModel;
import java.util.Arrays;
import java.util.List;

public class ProcessEditTableModel extends AbstractTableModel {
    private final String[] columnsNames = { "SELECT", "İşlem Adı", "Açıklama", "Fiyat" };
    private final List<Process> processes;
    private Boolean[] selectedRows;

    public ProcessEditTableModel(List<Process> processes) {
        this.processes = processes;
        this.selectedRows = new Boolean[processes.size()];
        Arrays.fill(selectedRows, false);
    }

    @Override
    public int getRowCount() {
        return processes.size();
    }

    @Override
    public int getColumnCount() {
        return columnsNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Process process = processes.get(rowIndex);

        switch (columnIndex) {
            case 0: return selectedRows[rowIndex];
            case 1: return process.getName();
            case 2: return process.getComment();
            case 3: return Format.formatPrice(process.getPrice());
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
        if (columnIndex == 0) {
            return Boolean.class;
        }
        return String.class;
    }

    public void setProcesses(List<Process> processes) {
        this.processes.clear();
        this.processes.addAll(processes);
        this.selectedRows = new Boolean[processes.size()];
        Arrays.fill(this.selectedRows, false);
        fireTableDataChanged();
    }


    public Process getProcess(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < processes.size()) {
            return processes.get(rowIndex);
        } else {
            throw new IndexOutOfBoundsException("Geçersiz satır indeksi: " + rowIndex);
        }
    }

    public void updateProcess(Process oldProcess, Process newProcess) {
        int index = processes.indexOf(oldProcess);
        if (index >= 0) {
            processes.set(index, newProcess);

            // ilgili satırı güncelledik diye Swing'e haber veriyoruz
            fireTableRowsUpdated(index, index);
        }
    }

    public void removeProcess(Process process) {
        int index = processes.indexOf(process);
        if (index >= 0) {
            processes.remove(index);

            // selectedRows dizisini de küçült
            Boolean[] newSelectedRows = new Boolean[processes.size()];
            for (int i = 0, j = 0; i < selectedRows.length; i++) {
                if (i != index) {
                    newSelectedRows[j++] = selectedRows[i];
                }
            }
            Arrays.fill(newSelectedRows, false); // güvenlik için seçimleri sıfırla
            System.arraycopy(newSelectedRows, 0, selectedRows, 0, newSelectedRows.length);

            fireTableRowsDeleted(index, index);
        }
    }

    public void removeProcesses(List<Process> toRemove) {
        for (Process process : toRemove) {
            removeProcess(process);
        }
        fireTableDataChanged();
    }

    public List<Process> getSelectedProcess() {
        List<Process> selected = new java.util.ArrayList<>();
        for (int i = 0; i < processes.size(); i++) {
            if (selectedRows[i]) {
                selected.add(processes.get(i));
            }
        }
        return selected;
    }
}
