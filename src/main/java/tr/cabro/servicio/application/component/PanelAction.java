package tr.cabro.servicio.application.component;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.util.Ikon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PanelAction extends JPanel {


    public PanelAction() {
        initComponents();
    }

    public void initEvent(TableActionEvent event, int row) {
        cmdEdit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                event.onEdit(row);
            }
        });
        cmdDelete.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                event.onDelete(row);
            }
        });
        cmdView.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                event.onView(row);
            }
        });
    }

    @Override
    public void setBackground(Color bg) {
        super.setBackground(bg);

        if (cmdEdit != null) {
            cmdEdit.setBackground(bg);
        }
        if (cmdDelete != null) {
            cmdDelete.setBackground(bg);
        }
        if (cmdView != null) {
            cmdView.setBackground(bg);
        }
    }

    private void initComponents() {
        setLayout(new MigLayout("insets 0, fill", "[grow, center][grow, center][grow, center]", "[center]"));

        Color editColor = new Color(253, 126, 20);   // Şık bir Turuncu
        Color deleteColor = new Color(220, 53, 69); // Şık bir Kırmızı
        Color viewColor = new Color(13, 110, 253);  // Şık bir Mavi

        cmdEdit = new ActionButton(new Ikon("icons/pencil.svg", 0.7f), editColor);
        cmdDelete = new ActionButton(new Ikon("icons/trash-2.svg", 0.7f), deleteColor);
        cmdView = new ActionButton(new Ikon("icons/eye.svg", 0.7f), viewColor);

        add(cmdView);
        add(cmdEdit);
        add(cmdDelete);
    }

    private ActionButton cmdDelete;
    private ActionButton cmdEdit;
    private ActionButton cmdView;
}
