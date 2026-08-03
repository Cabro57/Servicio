package tr.cabro.servicio.application.renderer;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Liste formlarındaki (FormCustomers, FormSuppliers, FormDevices, FormParts, FormWorkOrders)
 * neredeyse birebir kopyalanmış anonim {@link DefaultTableCellRenderer} alt sınıflarının (hizalama +
 * FlatLaf stil dizgisi + opsiyonel yatay padding) yerini alan tek noktalı, yapılandırılabilir sürüm.
 */
public class StyledLabelCellRenderer extends DefaultTableCellRenderer {

    private final int alignment;
    private final String flatStyle;
    private final int horizontalPadding;

    private StyledLabelCellRenderer(int alignment, String flatStyle, int horizontalPadding) {
        this.alignment = alignment;
        this.flatStyle = flatStyle;
        this.horizontalPadding = horizontalPadding;
    }

    /** Sadece hizalama + FlatLaf stil dizgisi uygular (padding yok). */
    public static StyledLabelCellRenderer of(int alignment, String flatStyle) {
        return new StyledLabelCellRenderer(alignment, flatStyle, 0);
    }

    /** Hizalama + FlatLaf stil dizgisi + yatay padding (sol/sağ eşit) uygular. */
    public static StyledLabelCellRenderer of(int alignment, String flatStyle, int horizontalPadding) {
        return new StyledLabelCellRenderer(alignment, flatStyle, horizontalPadding);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                     boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        JLabel label = (JLabel) c;
        label.setHorizontalAlignment(alignment);
        if (flatStyle != null) {
            label.putClientProperty(FlatClientProperties.STYLE, flatStyle);
        }
        if (horizontalPadding > 0) {
            label.setBorder(BorderFactory.createEmptyBorder(0, horizontalPadding, 0, horizontalPadding));
        }
        return c;
    }
}
