package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.system.Form;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.application.panels.setting.*;

import javax.swing.*;

@SystemForm(name = "Ayarlar", description = "Uygulama ile ilgili tüm ayarlar")
public class FormSettings extends Form {

    public FormSettings() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill", "[fill]", "[fill]"));

        tabbedPane = new JTabbedPane();
        tabbedPane.putClientProperty(FlatClientProperties.STYLE, "tabType:card");

        addPanel("Genel", new SettingsMainPanel());
        addPanel("Cihazlar", new SettingsDevicePanel());
        addPanel("Tamirler", new SettingsRepairPanel());
        addPanel("Yedeklemeler", new SettingsDatabasePanel());
        addPanel("Görünüm", new AppearancePanel());

        add(tabbedPane);

    }

    private void addPanel(String title, JPanel panel) {
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.putClientProperty(FlatClientProperties.STYLE, "border: 0, 0, 0, 0");
        tabbedPane.add(title, scrollPane);


    }

//    private JPanel createThemes() {
//        JPanel panel = new JPanel(new MigLayout("wrap,fill,insets 0", "[fill]", "[grow 0,fill]0[fill]"));
//        final PanelThemes panelThemes = new PanelThemes();
//        JPanel panelHeader = new JPanel(new MigLayout("fillx,insets 3", "[grow 0]push[]"));
//        panelHeader.add(new JLabel("Themes"));
//        JComboBox combo = new JComboBox(new Object[]{"All", "Light", "Dark"});
//        combo.addActionListener(e -> {
//            panelThemes.updateThemesList(combo.getSelectedIndex());
//        });
//        panelHeader.add(combo);
//        panel.add(panelHeader);
//        panel.add(panelThemes);
//        return panel;
//    }

    private JTabbedPane tabbedPane;

}
