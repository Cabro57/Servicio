package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;

public class StatusInfoPanel extends JPanel {
    private JPanel main_panel;
    private JLabel title;

    private JRadioButton under_repair_radio;
    private JRadioButton ready_radio;
    private JRadioButton another_service_radio;
    private JRadioButton delivered_radio;
    private JRadioButton return_radio;
    private JRadioButton wait_part_radio;

    private final ButtonGroup status_group = new ButtonGroup();

    public StatusInfoPanel() {
        init();

        add(main_panel);
    }

    private void init() {
        this.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");
        main_panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        status_group.add(another_service_radio);
        status_group.add(ready_radio);
        status_group.add(return_radio);
        status_group.add(delivered_radio);
        status_group.add(under_repair_radio);
        status_group.add(wait_part_radio);

    }

    private JRadioButton getSelectedRadioButton() {
        for (AbstractButton button : java.util.Collections.list(status_group.getElements())) {
            if (button.isSelected()) {
                return (JRadioButton) button;
            }
        }
        return null; // Hiçbiri seçili değilse
    }

    public String getSelected() {
        JRadioButton selected = getSelectedRadioButton();
        return (selected != null) ? selected.getText() : null;
    }

    public void setSelected(String text) {
        if (text == null) {
            clearSelection();
            return;
        }

        for (AbstractButton button : java.util.Collections.list(status_group.getElements())) {
            if (text.equalsIgnoreCase(button.getText())) {
                button.setSelected(true);
                return;
            }
        }
        // Eğer eşleşen bulunmazsa seçimi temizle
        clearSelection();
    }

    public void clearSelection() {
        status_group.clearSelection();
    }



}
