package tr.cabro.servicio.application.ui;

import tr.cabro.servicio.util.SVGIconUIColor;

import javax.swing.*;
import java.awt.*;

public class SettingsUI extends JDialog {
    private JPanel main_panel;
    private JTabbedPane tabbed_pane;
    private JPanel device_setting;
    private JPanel repair_setting;
    private JPanel database_setting;
    private JPanel main_setting;

    public SettingsUI() {
        super((Frame) null, "Ayarlar", true);
        Setup();
        setContentPane(main_panel);
    }

    private void Setup() {
        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.6);
        int height = (int) (screen_size.height * 0.6);

        setSize(width, height);
        setLocationRelativeTo(null);

        setIconImage(new SVGIconUIColor("icon/settings.svg", 0.025f, "MenuItem.foreground").getImage());
    }
}
