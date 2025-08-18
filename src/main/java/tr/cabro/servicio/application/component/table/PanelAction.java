package tr.cabro.servicio.application.component.table;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PanelAction extends JPanel {

    private JPanel main_panel;
    private ActionButton remove_button;
    private ActionButton add_button;
    private JLabel amount_label;
    private ActionButton new_button;

    private int amount = 0;

    public PanelAction() {
        add(main_panel);
        updateAmountLabel();
    }

    public void initEvent(TableActionEvent event, int row) {
        add_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                incrementAmount();
                event.onAdd(row);
            }
        });

        remove_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                decrementAmount();
                event.onRemove(row);
            }
        });
    }

    public void setAmount(int value) {
        this.amount = Math.max(0, value);
        updateAmountLabel();
    }

    public void incrementAmount() {
        this.amount++;
        updateAmountLabel();
    }

    public void decrementAmount() {
        if (this.amount > 0) {
            this.amount--;
            updateAmountLabel();
        }
    }

    private void updateAmountLabel() {
        amount_label.setText(String.valueOf(amount));
    }
}
