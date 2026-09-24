package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.application.component.table.ListTable;

import tr.cabro.servicio.application.component.PanelAction;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class ActionButtonRenderer extends DefaultTableCellRenderer {

    private PanelAction panel;
    private final JPanel blank = new JPanel();

    public ActionButtonRenderer() {
        this.panel = new PanelAction();
    }



    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component com = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setVerticalAlignment(SwingConstants.CENTER);
        setFocusable(false);
        panel.setBackground(com.getBackground());
        // Liste sayfalarında işlemler yalnızca fare üstündeki ya da seçili satırda görünür.
        if (table instanceof ListTable && !((ListTable) table).isRowActive(row)) {
            blank.setBackground(com.getBackground());
            return blank;
        }
        return panel;
    }
}