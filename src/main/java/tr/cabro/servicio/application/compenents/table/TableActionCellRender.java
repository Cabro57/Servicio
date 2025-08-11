package tr.cabro.servicio.application.compenents.table;

import java.awt.Color;
import java.awt.Component;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

public class TableActionCellRender extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object o, boolean isSelected, boolean bln1, int row, int column) {
        Component com = super.getTableCellRendererComponent(table, o, isSelected, bln1, row, column);
        PanelAction action = new PanelAction();
        action.setAmount((Integer) o);
        if (isSelected) {
            action.setBackground(table.getSelectionBackground());
        } else if (row % 2 == 0) {
            Color evenColor = UIManager.getColor("Table.alternateRowColor");
            if (evenColor == null) {
                evenColor = table.getBackground(); // fallback
            }
            action.setBackground(evenColor);
        } else {
            action.setBackground(table.getBackground());
        }
        return action;
    }
}
