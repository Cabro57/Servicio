package tr.cabro.servicio.application.renderer;

import javax.swing.table.DefaultTableCellRenderer;
import java.text.NumberFormat;
import java.util.Locale;

public class CurrencyTableCellRenderer extends DefaultTableCellRenderer {

    private final NumberFormat currencyFormat;

    public CurrencyTableCellRenderer() {
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("tr", "TR"));
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Number) {
            String formatted = currencyFormat.format(((Number) value).doubleValue());
            // " TL" ya da "TL" varsa onun yerine "₺" koy
            formatted = formatted.replace("TL", "₺").replace("Tl", "₺");
            setText(formatted);
        } else {
            setText("");
        }
    }
}
