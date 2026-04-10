package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.application.ui.IconManager;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.CustomerType;

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
                CustomerType type = CustomerType.valueOf(ct.getType().name());
                IconManager.getIcon(type.getIconPath(), 16);
                label.setIcon(new Ikon(ct.getType().getIconPath()));
            } else {
                label.setIcon(new Ikon(CustomerType.NORMAL.getIconPath()));
                // Veri hatası varsa varsayılan olarak NORMAL kabul et
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