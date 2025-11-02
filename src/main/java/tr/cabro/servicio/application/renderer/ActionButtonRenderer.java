package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.application.component.PanelAction;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class ActionButtonRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component com = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        PanelAction action = new PanelAction();
        if (isSelected) {
            action.setBackground(table.getSelectionBackground());
        } else {
            action.setBackground(table.getBackground());
        }
        return action;
    }
}