package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.model.enums.PaymentType;

import javax.swing.*;
import java.awt.*;

public class PaymentTypeRenderer extends JLabel implements ListCellRenderer<PaymentType> {

    public PaymentTypeRenderer() {
        setOpaque(true);
        setHorizontalAlignment(LEFT);
        setVerticalAlignment(CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends PaymentType> list,
                                                  PaymentType value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
        if (value != null) {
            setText(value.getDisplayName());
        }

        return this;
    }
}
