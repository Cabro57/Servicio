package tr.cabro.servicio.application.renderer;

import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

public class TableHeaderAlignment implements TableCellRenderer {

    private final TableCellRenderer oldHeaderRenderer;
    private final TableCellRenderer oldCellRenderer;
    private final Integer[] alignments;

    public TableHeaderAlignment(JTable table, Integer[] alignments) {
        this.oldHeaderRenderer = table.getTableHeader().getDefaultRenderer();
        this.oldCellRenderer = table.getDefaultRenderer(Object.class);
        this.alignments = alignments;

        table.setDefaultRenderer(Object.class, (jtable, o, isSelected, hasFocus, row, column) -> {
            JLabel label = (JLabel) oldCellRenderer.getTableCellRendererComponent(jtable, o, isSelected, hasFocus, row, column);
            label.setHorizontalAlignment(getAlignment(column));
            return label;
        });
    }

    @Override
    public Component getTableCellRendererComponent(JTable jtable, Object o, boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel label = (JLabel) oldHeaderRenderer.getTableCellRendererComponent(jtable, o, isSelected, hasFocus, row, column);
        label.setHorizontalAlignment(getAlignment(column));
        return label;
    }

    protected int getAlignment(int column) {
        if (alignments != null && column < alignments.length && alignments[column] != null) {
            return alignments[column];
        }
        return SwingConstants.LEADING; // Varsayılan
    }
}
