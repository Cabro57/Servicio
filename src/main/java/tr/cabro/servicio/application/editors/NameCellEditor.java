package tr.cabro.servicio.application.editors;

import tr.cabro.servicio.application.events.EventCellInputChange;
import tr.cabro.servicio.application.tablemodal.ServicePartTableModel;
import tr.cabro.servicio.model.AddedPart;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.text.DefaultFormatter;
import java.awt.*;

public class NameCellEditor extends DefaultCellEditor {

    private JTextField input;

    private JTable table;
    private int row;
    private AddedPart item;

    public NameCellEditor() {
        super(new JCheckBox());
        input = new JTextField();
        input.addPropertyChangeListener(evt -> inputChange());


    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        super.getTableCellEditorComponent(table, value, isSelected, row, column);
        this.table = table;
        this.row = row;
        ServicePartTableModel tableModel = (ServicePartTableModel) table.getModel();
        this.item = tableModel.getAddedParts().get(row);
        String qty = value.toString();
        input.setText(qty);
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
        return input.getText();
    }

    private void inputChange() {
        String qty = input.getText();
        if (qty.equals(item.getName())) {
            item.setName(qty);
        }
    }
}
