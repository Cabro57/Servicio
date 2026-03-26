package tr.cabro.servicio.application.renderer;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Process;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class ProcessTableRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JPanel panel = new JPanel(new MigLayout("insets 4", "[]", "[]2[]"));
        panel.setBackground(null);

        JLabel nameLabel = new JLabel();
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));

        JLabel commentLabel = new JLabel();
        commentLabel.setForeground(Color.GRAY);

        panel.add(nameLabel, "wrap");
        panel.add(commentLabel, "growx");

        if (value instanceof Process) {
            Process ct = (Process) value;
            nameLabel.setText(ct.getName());
            commentLabel.setText(ct.getComment());
            panel.setToolTipText(ct.getComment());
        } else {
            nameLabel.setText("");
            commentLabel.setIcon(null);
        }

        if (isSelected) {
            panel.setBackground(table.getSelectionBackground());
            nameLabel.setForeground(table.getSelectionForeground());
            commentLabel.setForeground(table.getSelectionForeground());
        } else {
            panel.setBackground(table.getBackground());
            nameLabel.setForeground(table.getForeground());
            commentLabel.setForeground(Color.GRAY);

        }

        return panel;
    }
}
