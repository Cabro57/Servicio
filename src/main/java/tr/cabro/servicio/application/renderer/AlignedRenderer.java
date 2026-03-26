package tr.cabro.servicio.application.renderer;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class AlignedRenderer implements TableCellRenderer {
    private final TableCellRenderer delegate;
    private final int alignment;

    public AlignedRenderer(JTable table, int columnIndex, int alignment) {
        TableCellRenderer current = table.getColumnModel().getColumn(columnIndex).getCellRenderer();
        if (current == null) {
            current = table.getDefaultRenderer(Object.class);
        }
        this.delegate = current;
        this.alignment = alignment;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        Component c = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (c instanceof JLabel) {
            ((JLabel) c).setHorizontalAlignment(alignment);
        }
        return c;
    }
}
