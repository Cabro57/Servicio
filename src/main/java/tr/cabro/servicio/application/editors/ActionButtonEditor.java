package tr.cabro.servicio.application.editors;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.application.component.ActionButton;
import tr.cabro.servicio.application.events.TableActionEvent;

import javax.swing.*;
import java.awt.*;

public class ActionButtonEditor extends DefaultCellEditor {

    private final TableActionEvent event;

    public ActionButtonEditor(TableActionEvent event) {
        super(new JCheckBox());
        this.event = event;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        ActionButton button = new ActionButton(new FlatSVGIcon("icon/delete.svg"));
        button.addActionListener(e -> event.onAction(row));

        return button;
    }
}