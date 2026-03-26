package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.CustomerType;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class CustomerTableCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value instanceof Customer) {
            Customer ct = (Customer) value;
            label.setText(ct.toString());

            // GÜVENLİK ÖNLEMİ: Type null ise varsayılan ikonu (NORMAL) kullan veya boş geç
            if (ct.getType() != null) {
                label.setIcon(ct.getType().getIcon());
            } else {
                // Veri hatası varsa varsayılan olarak NORMAL kabul et
                label.setIcon(CustomerType.NORMAL.getIcon());
            }

            label.setHorizontalTextPosition(SwingConstants.RIGHT);
            label.setIconTextGap(8);
        } else {
            label.setText("");
            label.setIcon(null);
        }

        return label;
    }
}