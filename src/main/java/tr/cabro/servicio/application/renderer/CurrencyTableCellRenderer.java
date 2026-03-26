package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.text.DecimalFormat;

public class CurrencyTableCellRenderer extends DefaultTableCellRenderer {


    public CurrencyTableCellRenderer() {
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Number) {
            setText(Format.formatPrice(((Number) value).doubleValue()));
            setHorizontalAlignment(SwingConstants.CENTER);
        } else {
            setText("");
        }
    }
}
