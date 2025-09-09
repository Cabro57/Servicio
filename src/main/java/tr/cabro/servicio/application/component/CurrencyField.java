package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.DecimalFormat;

public class CurrencyField extends JFormattedTextField {

    public CurrencyField() {
        super(createFormatter());
        this.setValue(0.0);

//        FlatSVGIcon icon = new FlatSVGIcon("icon/lira-symbol.svg", 12, 12);
//        Color newcolor = UIManager.getColor("MenuItem.foreground");
//        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> newcolor));
        JLabel label = new JLabel("₺");
        label.putClientProperty(FlatClientProperties.STYLE, "border:0,5,0,5;");
        putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, label);

        // Focus geldiğinde tüm yazıyı seç
        this.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(() -> selectAll());
            }
        });
    }

    private static NumberFormatter createFormatter() {
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        NumberFormatter formatter = new NumberFormatter(decimalFormat);
        formatter.setValueClass(Double.class);
        formatter.setAllowsInvalid(false);
        formatter.setMinimum(0.0);  // Negatif para engelle
        return formatter;
    }

    public double getDoubleValue() {
        Object val = this.getValue();
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return 0.0;
    }
}
