package tr.cabro.servicio.application.ui;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.panels.setting.SettingsDatabasePanel;
import tr.cabro.servicio.application.panels.setting.SettingsDevicePanel;
import tr.cabro.servicio.application.panels.setting.SettingsRepairPanel;
import tr.cabro.servicio.application.panels.setting.SettingsMainPanel;
import tr.cabro.servicio.application.util.SVGIconUIColor;

import javax.swing.*;
import java.awt.*;

public class SettingsUI extends JDialog {

    public SettingsUI() {
        super((Frame) null, "Ayarlar", true);

        init();
    }

    private void init() {
        initComponent();

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.6);
        int height = (int) (screen_size.height * 0.6);

        setSize(width, height);
        setLocationRelativeTo(null);
    }

    private void initComponent() {
        setIconImage(new SVGIconUIColor("icon/settings.svg", 0.025f, "MenuItem.foreground").getImage());

        setLayout(new MigLayout("fill, insets 10", "[grow]", "[grow]"));

        JTabbedPane tabbed_pane = new JTabbedPane();

        // Genel Ayarlar
        JPanel main_setting = new SettingsMainPanel();
        tabbed_pane.addTab("Genel Ayarlar", main_setting);

        // Cihaz Ayarları
        JPanel device_setting = new SettingsDevicePanel();
        tabbed_pane.addTab("Cihaz Ayarları", device_setting);

        // Tamir Ayarları
        JPanel repair_setting = new SettingsRepairPanel();
        tabbed_pane.addTab("Tamir Ayarları", repair_setting);

        // Yedekleme Ayarları
        JPanel database_setting = new SettingsDatabasePanel();
        tabbed_pane.addTab("Yedekleme Ayarları", database_setting);

        add(tabbed_pane, "grow");
    }
}
