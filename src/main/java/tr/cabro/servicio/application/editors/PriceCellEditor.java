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

    private final EventCellInputChange event;
    private final JFormattedTextField input;

    private JTable table;
    private int row;
    private AddedPart item;
    private boolean isPurchase; // true ise purchasePrice, false ise sellingPrice

    public PriceCellEditor(EventCellInputChange event, boolean isPurchase) {
        super(new JCheckBox());
        this.event = event;
        this.isPurchase = isPurchase;
        this.input = new JFormattedTextField();

        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);

        NumberFormatter formatter = new NumberFormatter(format);
        formatter.setAllowsInvalid(false);
        formatter.setMinimum(0.0);

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

        double val = Double.parseDouble(value.toString());
        input.setValue(val);
        input.setEnabled(false);
        enable();
        return input;
    }

    private void enable() {
        new Thread(() -> {
            try {
                Thread.sleep(100);
                input.setEnabled(true);
            } catch (Exception ignored) {}
        }).start();
    }

    @Override
    public Object getCellEditorValue() {
        return input.getValue();
    }

    private void inputChange() {
        double val = Double.parseDouble(input.getValue().toString());
        if (isPurchase) {
            if (val != item.getPurchasePrice()) {
                item.setPurchasePrice(val);
                event.inputChanged();
            }
        } else {
            if (val != item.getSellingPrice()) {
                item.setSellingPrice(val);
                event.inputChanged();
            }
        }
    }
}
