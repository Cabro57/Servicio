package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.model.enums.CustomerType;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class CustomerTypeTableRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value instanceof CustomerType) {
            CustomerType ct = (CustomerType) value;
            label.setText(ct.getDisplayName());
            label.setHorizontalTextPosition(SwingConstants.RIGHT);
            label.setIconTextGap(8);
        } else {
            label.setText("");
            label.setIcon(null);
        }

        return label;
    }
}
