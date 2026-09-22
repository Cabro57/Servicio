package tr.cabro.servicio.application.component;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.table.TableActionEvent;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.application.utils.Ikon;

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
        setFocusable(false);

        cmdEdit = new ActionButton(new Ikon("icons/pencil.svg", 0.8f), SemanticColor.warning());
        cmdDelete = new ActionButton(new Ikon("icons/trash-2.svg", 0.8f), SemanticColor.danger());
        cmdView = new ActionButton(new Ikon("icons/eye.svg", 0.8f), SemanticColor.info());

        // İkonlar tek başına ne yaptıklarını söylemiyor: tooltip görene, erişilebilir ad
        // ekran okuyucuya anlatır. Satırı açmanın klavye yolu da burada duyurulur.
        describe(cmdView, "Detayı aç", "Detayı aç (satırda Enter veya çift tık)");
        describe(cmdEdit, "Düzenle", "Düzenle");
        describe(cmdDelete, "Sil", "Sil");

        add(cmdView);
        add(cmdEdit);
        add(cmdDelete);
    }

    private void describe(ActionButton button, String accessibleName, String tooltip) {
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(accessibleName);
        button.getAccessibleContext().setAccessibleDescription(tooltip);
    }

    private ActionButton cmdDelete;
    private ActionButton cmdEdit;
    private ActionButton cmdView;
}
