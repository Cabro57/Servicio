package tr.cabro.servicio.application.editors;

import tr.cabro.servicio.application.events.EventCellInputChange;
import tr.cabro.servicio.application.tablemodal.ServicePartTableModel;
import tr.cabro.servicio.model.AddedPart;

import java.awt.Component;
import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.text.DefaultFormatter;

public class AmountCellEditor extends DefaultCellEditor {

    private EventCellInputChange event;
    private JSpinner input;

    private JTable table;
    private int row;
    private AddedPart item;

    public AmountCellEditor(EventCellInputChange event) {
        super(new JCheckBox());
        this.event = event;
        input = new JSpinner();
        SpinnerNumberModel numberModel = (SpinnerNumberModel) input.getModel();
        numberModel.setMinimum(1);
        JSpinner.NumberEditor editor = (JSpinner.NumberEditor) input.getEditor();
        DefaultFormatter formatter = (DefaultFormatter) editor.getTextField().getFormatter();
        formatter.setCommitsOnValidEdit(true);
        editor.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
        input.addChangeListener((ChangeEvent e) -> {
            inputChange();
        });
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        super.getTableCellEditorComponent(table, value, isSelected, row, column);
        this.table = table;
        this.row = row;
        ServicePartTableModel tableModel = (ServicePartTableModel) table.getModel();
        this.item = tableModel.getAddedParts().get(row);
        int qty = Integer.parseInt(value.toString());
        input.setValue(qty);
        input.setEnabled(false);
        enable();
        return input;
    }

    private void enable() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    input.setEnabled(true);
                } catch (Exception e) {

                }
            }
        }).start();
    }

    @Override
    public Object getCellEditorValue() {
        return input.getValue();
    }

    private void inputChange() {
        int qty = Integer.parseInt(input.getValue().toString());
        if (qty != item.getAmount()) {
            item.setAmount(qty);
            event.inputChanged();
        }
    }
}
