package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;

public class CurrencyTableCellRenderer extends DefaultTableCellRenderer {

    public CurrencyTableCellRenderer() {
        // Para değerleri genellikle sağa yaslanır
        setHorizontalAlignment(SwingConstants.RIGHT);
    }

    @Override
    protected void setValue(Object value) {
        if (value instanceof BigDecimal) {
            setText(Format.formatPrice((BigDecimal) value));
        } else if (value instanceof Number) {
            setText(Format.formatPrice(BigDecimal.valueOf(((Number) value).doubleValue())));
        } else {
            // Boş veya geçersiz değer durumunda
            setText("");
        }
    }
}