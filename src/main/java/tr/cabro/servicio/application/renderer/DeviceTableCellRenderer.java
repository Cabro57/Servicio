package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.model.Device;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class DeviceTableCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        JPanel panel = new JPanel(new MigLayout("wrap 1, insets 5, aligny 50%", "[left]", "[]0[]"));
        panel.setOpaque(true);

        if (isSelected) {
            panel.setBackground(table.getSelectionBackground());
            panel.setForeground(table.getSelectionForeground());
        } else {
            panel.setBackground(table.getBackground());
            panel.setForeground(table.getForeground());
        }

        if (value instanceof Device) {
            Device device = (Device) value;

            JLabel typeLabel = new JLabel(device.getType());
            //typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD));

            JLabel brandModelLabel = new JLabel(device.getBrand() + " " + device.getModel());

            panel.add(typeLabel, "growx");
            panel.add(brandModelLabel, "growx");
            setHorizontalAlignment(CENTER);
        }

        return panel;
    }
}
