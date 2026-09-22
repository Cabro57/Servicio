package tr.cabro.servicio.application.component.table;

import tr.cabro.servicio.application.component.PanelAction;

import javax.swing.*;
import java.awt.*;

public class ActionButtonEditor extends DefaultCellEditor {

    private final TableActionEvent event;

    public ActionButtonEditor(TableActionEvent event) {
        super(new JCheckBox());
        this.event = event;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        Component com = super.getTableCellEditorComponent(table, value, isSelected, row, column);

        PanelAction action = new PanelAction();
        action.initEvent(event, row);

        action.setBackground(com.getBackground());

        return action;
    }
}