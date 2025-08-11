package tr.cabro.servicio.application.renderer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.text.DecimalFormat;

public class CurrencyTableCellRenderer extends DefaultTableCellRenderer {


    public CurrencyTableCellRenderer() {
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Number) {
            DecimalFormat df = new DecimalFormat("#,##0.00 ₺");
            setText(df.format(value));
            setHorizontalAlignment(SwingConstants.CENTER);
        } else {
            setText("");
        }
    }
}
