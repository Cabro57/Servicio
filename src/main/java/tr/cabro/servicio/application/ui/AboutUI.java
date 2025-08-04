package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.icons.SVGIconUIColor;

import javax.swing.*;
import java.awt.*;

public class AboutUI extends JDialog {


    private JPanel main_panel;
    private JEditorPane desc;

    public AboutUI() {
        init();

        add(main_panel);
    }

    private void init() {
        setTitle("Hakkında");

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.3);
        int height = (int) (screen_size.height * 0.3);

        setSize(width, height);
        setLocationRelativeTo(null);

        setIconImage(new SVGIconUIColor("icon/about.svg", 0.025f, "MenuItem.foreground").getImage());
    }
}
