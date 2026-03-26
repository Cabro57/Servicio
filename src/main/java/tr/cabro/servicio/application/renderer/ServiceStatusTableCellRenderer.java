package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.model.ServiceStatus;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ServiceStatusTableCellRenderer implements TableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {

        JPanel panel = new JPanel(new MigLayout("insets 0, fill, alignx center, aligny center"));
        panel.setOpaque(true);

        if (isSelected) {
            panel.setBackground(table.getSelectionBackground());
        } else {
            panel.setBackground(table.getBackground());
        }

        if (value instanceof ServiceStatus) {
            ServiceStatus status = (ServiceStatus) value;

            JLabel label = new JLabel(status.getDisplayName());
            label.setIcon(status.getIcon());
            label.setHorizontalTextPosition(SwingConstants.RIGHT);
            label.setIconTextGap(8);

            panel.add(label, "center");   // asıl hizalama burada
        }

        return panel;
    }
}
