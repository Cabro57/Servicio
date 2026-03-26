package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;

public class SettingsMainPanel extends JPanel {

    public SettingsMainPanel() {
        init();
    }

    private void init() {
        initComponent();
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx,insets 5,gapy 10", "[grow]", "[][][][grow]"));

        JPanel barcode_panel = new JPanel(new MigLayout("fill,insets 5", "[fill][fill][fill]", "[][][]"));
        barcode_panel.setBorder(BorderFactory.createTitledBorder("Barkod Ayarları"));

        JTextField prefix = new JTextField();

        barcode_panel.add(prefix, "grow, push");

        add(barcode_panel, "growx, wrap");

        JPanel extra_panel = new JPanel(new MigLayout("fill,insets 5"));
        add(extra_panel, "growx, wrap");

        add(new JLabel(), "pushy, growy");
    }
}
