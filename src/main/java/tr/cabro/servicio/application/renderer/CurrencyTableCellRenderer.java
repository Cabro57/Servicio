package tr.cabro.servicio.application.renderer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class CurrencyTableCellRenderer extends DefaultTableCellRenderer {

    // Para formatı için standart Java nesnesi (Örn: 1.250,50 TL veya $1,250.50)
    // Locale.GERMANY kuruş için virgül, binlik için nokta kullanır (TR ile benzerdir)
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("tr", "TR"));

    public CurrencyTableCellRenderer() {
        // Para değerleri genellikle sağa yaslanır
        setHorizontalAlignment(SwingConstants.RIGHT);
    }

    @Override
    protected void setValue(Object value) {
        if (value instanceof BigDecimal) {
            // BigDecimal'i doğrudan formatla (Hassasiyet kaybı yaşanmaz)
            setText(CURRENCY_FORMAT.format(value));
        } else if (value instanceof Number) {
            // Eğer değer başka bir sayı tipindeyse
            setText(CURRENCY_FORMAT.format(((Number) value).doubleValue()));
        } else {
            // Boş veya geçersiz değer durumunda
            setText("");
        }
    }
}