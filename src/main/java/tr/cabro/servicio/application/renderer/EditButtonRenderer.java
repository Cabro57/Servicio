package tr.cabro.servicio.application.renderer;


import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;

public class EditButtonRenderer extends JButton implements TableCellRenderer {
    public EditButtonRenderer() {
        setOpaque(true);
    }
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        setText("Düzenle");
        return this;
    }
}