package tr.cabro.servicio.application.editors;

import javax.swing.*;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.text.NumberFormat;

public class DoubleEditor extends DefaultCellEditor {
    public DoubleEditor() {
        super(new JFormattedTextField());
        JFormattedTextField ftf = (JFormattedTextField) getComponent();

        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);

        NumberFormatter formatter = new NumberFormatter(format);
        formatter.setAllowsInvalid(false); // Geçersiz giriş engelle
        formatter.setMinimum(0.0); // Negatif sayı olmasın

        ftf.setFormatterFactory(new DefaultFormatterFactory(formatter));
        ftf.setHorizontalAlignment(JTextField.RIGHT);
    }

    @Override
    public Object getCellEditorValue() {
        JFormattedTextField ftf = (JFormattedTextField) getComponent();
        Object value = ftf.getValue();
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(ftf.getText());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
