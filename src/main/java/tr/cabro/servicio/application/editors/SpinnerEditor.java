package tr.cabro.servicio.application.editors;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;

public class SpinnerEditor extends AbstractCellEditor implements TableCellEditor {
    private final JSpinner spinner;

    public SpinnerEditor(int min, int max, int step) {
        spinner = new JSpinner(new SpinnerNumberModel(min, min, max, step));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setHorizontalAlignment(JTextField.RIGHT);
    }

    @Override
    public Object getCellEditorValue() {
        return spinner.getValue();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                 boolean isSelected, int row, int column) {
        spinner.setValue(value != null ? value : 0);
        return spinner;
    }
}
