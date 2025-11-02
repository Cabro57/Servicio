package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.events.TableActionEvent;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 *
 * @author RAVEN
 */
public class PanelAction extends javax.swing.JPanel {

    /**
     * Creates new form PanelAction
     */
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

    private void initComponents() {
        setLayout(new MigLayout("insets 0, fill, align center", "[]0[]0[]", "[]"));

        cmdEdit = new ActionButton(new FlatSVGIcon("icons/edit.svg", 0.4f));
        cmdDelete = new ActionButton(new FlatSVGIcon("icons/delete.svg", 0.4f));
        cmdView = new ActionButton(new FlatSVGIcon("icons/view.svg", 0.4f));

        add(cmdEdit, "split 3");
        add(cmdDelete);
        add(cmdView);

    }

    private ActionButton cmdDelete;
    private ActionButton cmdEdit;
    private ActionButton cmdView;
}
