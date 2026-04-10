package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.model.enums.ServiceStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class StatusInfoPanel extends ServicePanel {

    private final ButtonGroup status_group = new ButtonGroup();
    private boolean isInitializing = false;

    public StatusInfoPanel() {
        init();
    }

    private void init() {
        initComponent();

        status_group.add(another_service_radio);
        status_group.add(ready_radio);
        status_group.add(return_radio);
        status_group.add(delivered_radio);
        status_group.add(under_repair_radio);
        status_group.add(wait_part_radio);

        // Her bir radyo butonuna dinleyici ekle
        ActionListener radioListener = e -> {
            if (!isInitializing && getListener() != null) {
                getListener().onDataChanged();
            }
        };

        for (AbstractButton button : java.util.Collections.list(status_group.getElements())) {
            button.addActionListener(radioListener);
        }
    }

    @Override
    protected void onServiceSet() {
        if (service == null) return;

        isInitializing = true;
        try {
            // Eğer servis teslim edilmişse, durumu Form üzerinden değiştirilemez.
            // "Teslim Et" butonu kullanılmalıdır. (Kurumsal Kural)
            boolean isDelivered = service.getServiceStatus() == ServiceStatus.DELIVERED;

            for (AbstractButton button : java.util.Collections.list(status_group.getElements())) {
                button.setEnabled(!isDelivered);
            }

            if (service.getServiceStatus() != null) {
                setSelected(service.getServiceStatus().getDisplayName());
            } else {
                under_repair_radio.setSelected(true);
            }
        } finally {
            isInitializing = false;
        }
    }

    private JRadioButton getSelectedRadioButton() {
        for (AbstractButton button : java.util.Collections.list(status_group.getElements())) {
            if (button.isSelected()) {
                return (JRadioButton) button;
            }
        }
        return null;
    }

    public ServiceStatus getSelected() {
        JRadioButton selected = getSelectedRadioButton();
        return (selected != null) ? ServiceStatus.of(selected.getText()) : ServiceStatus.UNDER_REPAIR;
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
        clearSelection();
    }

    public void clearSelection() {
        status_group.clearSelection();
    }

    private void initComponent() {
        setLayout(new MigLayout("wrap 6, insets 10", "[grow][grow][grow][grow][grow][grow]", ""));

        putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        title = new JLabel("Durum");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        under_repair_radio = new JRadioButton("Tamirde");
        ready_radio = new JRadioButton("Hazır");
        another_service_radio = new JRadioButton("Başka Serviste");
        delivered_radio = new JRadioButton("Teslim Edildi");
        return_radio = new JRadioButton("İade");
        wait_part_radio = new JRadioButton("Parça Bekliyor");

        under_repair_radio.setSelected(true);

        add(title, "span 6, wrap, gapbottom 10");
        add(under_repair_radio);
        add(ready_radio);
        add(another_service_radio);
        add(delivered_radio);
        add(return_radio);
        add(wait_part_radio);
    }

    private JLabel title;
    private JRadioButton under_repair_radio;
    private JRadioButton ready_radio;
    private JRadioButton another_service_radio;
    private JRadioButton delivered_radio;
    private JRadioButton return_radio;
    private JRadioButton wait_part_radio;
}