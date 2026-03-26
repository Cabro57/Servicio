package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.model.CustomerType;

import javax.swing.*;
import java.awt.*;

public class CustomerTypeRenderer extends JLabel implements ListCellRenderer<CustomerType> {

    public CustomerTypeRenderer() {
        setOpaque(true);
        setHorizontalAlignment(LEFT);
        setVerticalAlignment(CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends CustomerType> list,
                                                  CustomerType value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
        if (value != null) {
            setText(value.getDisplayName());
            setIcon(value.getIcon());
        }

        return this;
    }
}
