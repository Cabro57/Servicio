package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.application.tablemodal.PartTableModel;
import tr.cabro.servicio.model.Part;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class EditButtonEditor extends DefaultCellEditor {
    private final JButton button;
    private boolean clicked;
    private int row;

    public EditButtonEditor(JCheckBox checkBox, JTable table) {
        super(checkBox);
        button = new JButton("Düzenle");
        button.setOpaque(true);

        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireEditingStopped();
                PartTableModel model = (PartTableModel) table.getModel();
                Part p = model.getProduct(row);
                // Örneğin: Edit diyaloğunu aç
                JOptionPane.showMessageDialog(table, "Düzenlenecek ürün: " + p.getName());
                // Burada kendi edit dialogunu çağırabilirsin
            }
        });
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                 boolean isSelected, int row, int column) {
        this.row = row;
        clicked = true;
        return button;
    }

    @Override
    public Object getCellEditorValue() {
        return "Düzenle";
    }

    @Override
    public boolean stopCellEditing() {
        clicked = false;
        return super.stopCellEditing();
    }

    @Override
    protected void fireEditingStopped() {
        super.fireEditingStopped();
    }
}