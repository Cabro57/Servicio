package tr.cabro.servicio.application.compenents;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.application.ui.AboutUI;

import javax.swing.*;
import java.awt.*;

public class AboutButton extends JButton {

    public AboutButton() {
        init();

    }

    private void init() {
        FlatSVGIcon icon = new FlatSVGIcon("icon/about.svg", 18, 18);
        Color newcolor = UIManager.getColor( "MenuItem.foreground" );
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> newcolor));
        setIcon(icon);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(true);
        putClientProperty(FlatClientProperties.STYLE, "" +
                "background:$Panel.background;" +
                "hoverBackground:tint($Panel.background, 20%);" +
                "margin:null");

        addActionListener(e -> {
            AboutUI aboutUI = new AboutUI();
            aboutUI.setModal(true);
            aboutUI.setVisible(true);
        });
    }
}
