package tr.cabro.servicio.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.system.Form;
import raven.modal.themes.PanelThemes;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.application.panels.setting.SettingsDatabasePanel;
import tr.cabro.servicio.application.panels.setting.SettingsDevicePanel;
import tr.cabro.servicio.application.panels.setting.SettingsMainPanel;
import tr.cabro.servicio.application.panels.setting.SettingsRepairPanel;

import javax.swing.*;

@SystemForm(name = "Ayarlar", description = "Uygulama ile ilgili tüm ayarlar")
public class FormSettings extends Form {

    public FormSettings() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill", "[fill][fill,grow 0,250:250]", "[fill]"));

        tabbedPane = new JTabbedPane();
        tabbedPane.putClientProperty(FlatClientProperties.STYLE, "tabType:card");

        tabbedPane.add("Genel", new SettingsMainPanel());
        tabbedPane.addTab("Cihazlar", new SettingsDevicePanel());
        tabbedPane.addTab("Tamirler", new SettingsRepairPanel());
        tabbedPane.addTab("Yedeklemeler", new SettingsDatabasePanel());

        add(tabbedPane, "gapy 1 0");
        add(createThemes());

    }

    private JPanel createThemes() {
        JPanel panel = new JPanel(new MigLayout("wrap,fill,insets 0", "[fill]", "[grow 0,fill]0[fill]"));
        final PanelThemes panelThemes = new PanelThemes();
        JPanel panelHeader = new JPanel(new MigLayout("fillx,insets 3", "[grow 0]push[]"));
        panelHeader.add(new JLabel("Themes"));
        JComboBox combo = new JComboBox(new Object[]{"All", "Light", "Dark"});
        combo.addActionListener(e -> {
            panelThemes.updateThemesList(combo.getSelectedIndex());
        });
        panelHeader.add(combo);
        panel.add(panelHeader);
        panel.add(panelThemes);
        return panel;
    }

    private JTabbedPane tabbedPane;

}
