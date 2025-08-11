package tr.cabro.servicio.application.editors;

import tr.cabro.servicio.application.events.EventCellInputChange;
import tr.cabro.servicio.application.tablemodal.ServicePartTableModel;
import tr.cabro.servicio.model.AddedPart;

import javax.swing.*;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.text.NumberFormat;

public class PriceCellEditor extends DefaultCellEditor {

    private EventCellInputChange event;
    private JFormattedTextField input;

    private JTable table;
    private int row;
    private AddedPart item;

    public PriceCellEditor(EventCellInputChange event) {
        super(new JCheckBox());
        this.event = event;
        this.input = new JFormattedTextField();

        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);

        NumberFormatter formatter = new NumberFormatter(format);
        formatter.setAllowsInvalid(false); // Geçersiz giriş engelle
        formatter.setMinimum(0.0); // Negatif sayı olmasın

        this.input.setFormatterFactory(new DefaultFormatterFactory(formatter));
        this.input.setHorizontalAlignment(JTextField.RIGHT);

        this.input.addPropertyChangeListener("value", evt -> inputChange());
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        super.getTableCellEditorComponent(table, value, isSelected, row, column);

        this.table = table;
        this.row = row;
        ServicePartTableModel tableModel = (ServicePartTableModel) table.getModel();
        this.item = tableModel.getAddedParts().get(row);
        double qty = Double.parseDouble(value.toString());
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
        double qty = Double.parseDouble(input.getValue().toString());
        if (qty != item.getSellingPrice()) {
            item.setSellingPrice(qty);
            event.inputChanged();
        }
    }
}
