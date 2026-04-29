package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.application.component.ActionButton;
import tr.cabro.servicio.application.util.Ikon;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class AddButtonRenderer extends DefaultTableCellRenderer {

    private ActionButton button;

    public AddButtonRenderer() {
        this.button = new ActionButton(new Ikon("icons/plus.svg", 0.7f), Color.GREEN);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component com = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setVerticalAlignment(SwingConstants.CENTER);
        setFocusable(false);
        button.setBackground(com.getBackground());

        return button;
    }
}
