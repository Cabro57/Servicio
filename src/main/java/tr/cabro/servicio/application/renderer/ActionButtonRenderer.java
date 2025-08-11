package tr.cabro.servicio.application.renderer;


import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.application.compenents.ActionButton;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class ActionButtonRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        ActionButton action = new ActionButton(new FlatSVGIcon("icon/delete.svg"));
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