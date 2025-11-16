package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DateTimeTableCellRenderer extends DefaultTableCellRenderer {

    @Override
    protected void setValue(Object value) {
        if (value instanceof LocalDateTime) {
            setText(Format.formatDate((LocalDateTime) value));
            setHorizontalAlignment(SwingConstants.CENTER);
        } else if (value instanceof LocalDate) {
            setText(Format.formatDate((LocalDate) value));
            setHorizontalAlignment(SwingConstants.CENTER);
        } else {
            setText("");
        }
    }
}
