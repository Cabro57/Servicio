package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;

public class AboutUI extends JDialog {


    private JPanel main_panel;

    public AboutUI() {
        init();

        add(main_panel);
    }

    private void init() {
        setTitle("Hakkında");

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.6);
        int height = (int) (screen_size.height * 0.6);

        setSize(width, height);
        setLocationRelativeTo(null);

        FlatSVGIcon icon = new FlatSVGIcon("icon/about.svg", 18, 18);
        Color newcolor = UIManager.getColor( "MenuItem.foreground" );
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> newcolor));
        setIconImage(icon.getImage());
    }
}
