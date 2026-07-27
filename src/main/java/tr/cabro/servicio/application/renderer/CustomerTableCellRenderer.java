package tr.cabro.servicio.application.renderer;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.application.ui.IconManager;
import tr.cabro.servicio.application.utils.Ikon;
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
            label.setText(ct.getFullName());
            label.setToolTipText(null);

            if (ct.isProblematic()) {
                label.setIcon(new FlatSVGIcon("icons/triangle-alert.svg", 16, 16)
                        .setColorFilter(new FlatSVGIcon.ColorFilter(c -> new Color(231, 76, 60))));
                label.setToolTipText("Sorunlu Müşteri");
            } else if (ct.getType() != null) {
                CustomerType type = CustomerType.valueOf(ct.getType().name());
                IconManager.getIcon(type.getIconPath(), 16);
                label.setIcon(new Ikon(ct.getType().getIconPath()));
            } else {
                // Veri hatası varsa varsayılan olarak BIREYSEL kabul et
                label.setIcon(new Ikon(CustomerType.BIREYSEL.getIconPath()));
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