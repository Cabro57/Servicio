package tr.cabro.servicio.application.editors;

import tr.cabro.servicio.application.component.ActionButton;
import tr.cabro.servicio.application.events.TableAddEvent;
import tr.cabro.servicio.application.util.Ikon;

import javax.swing.*;
import java.awt.*;

public class AddButtonEditor extends DefaultCellEditor {

    private final TableAddEvent event;

    public AddButtonEditor(TableAddEvent event) {
        super(new JCheckBox());
        this.event = event;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        Component com = super.getTableCellEditorComponent(table, value, isSelected, row, column);

        ActionButton add = new ActionButton(new Ikon("icons/plus.svg"),  Color.GREEN);
        add.addActionListener(e -> event.onAdd(row));

        add.setBackground(com.getBackground());

        return add;
    }
}
