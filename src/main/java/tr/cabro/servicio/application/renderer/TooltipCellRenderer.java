package tr.cabro.servicio.application.renderer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

// Dinamik ve tekrar kullanılabilir bir TableCellRenderer sınıfı
public class TooltipCellRenderer extends DefaultTableCellRenderer {

    public TooltipCellRenderer() {
        super();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (c instanceof JComponent) {
            // Hücre değeri null değilse tooltip olarak göster
            ((JComponent) c).setToolTipText(value != null ? value.toString() : null);
        }

        return c;
    }

    // Kolay kullanım için statik method ile sütuna tooltip ekleme
    public static void applyToColumn(JTable table, int columnIndex) {
        table.getColumnModel().getColumn(columnIndex).setCellRenderer(new TooltipCellRenderer());
    }
}

