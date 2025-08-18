package tr.cabro.servicio.application.component.table;

import tr.cabro.servicio.model.Customer;

import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

public class ProfileTableRenderer implements TableCellRenderer {

    private final TableCellRenderer oldCellRenderer;

    public ProfileTableRenderer(JTable table) {
        oldCellRenderer = table.getDefaultRenderer(Object.class);
    }

    @Override
    public Component getTableCellRendererComponent(JTable jtable, Object o, boolean bln, boolean bln1, int i, int i1) {
        Component com = oldCellRenderer.getTableCellRendererComponent(jtable, o, bln, bln1, i, i1);

        if (o instanceof Customer) {
            TableCellProfile cell = new TableCellProfile((Customer) o, com.getFont());
            cell.setBackground(com.getBackground());
            return cell;
        } else {
            // Customer değilse eski renderer'ı kullan
            return com;
        }
    }

}
