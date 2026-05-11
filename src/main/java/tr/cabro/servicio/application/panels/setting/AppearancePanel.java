package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import raven.modal.themes.PanelThemes;

import javax.swing.*;

public class AppearancePanel extends JPanel {

    public AppearancePanel() {
        setLayout(new MigLayout("wrap,fill,insets 0", "[fill]", "[grow 0,fill]0[fill]"));
        final PanelThemes panelThemes = new PanelThemes();
        JPanel panelHeader = new JPanel(new MigLayout("fillx,insets 3", "[grow 0]push[]"));
        panelHeader.add(new JLabel("Themes"));
        JComboBox combo = new JComboBox(new Object[]{"All", "Light", "Dark"});
        combo.addActionListener(e -> {
            panelThemes.updateThemesList(combo.getSelectedIndex());
        });
        panelHeader.add(combo);
        add(panelHeader);
        add(panelThemes);
    }
}
