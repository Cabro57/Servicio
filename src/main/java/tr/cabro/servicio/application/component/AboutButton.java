package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.ui.AboutUI;
import tr.cabro.servicio.icons.SVGIconUIColor;

import javax.swing.*;

public class AboutButton extends JButton {

    public AboutButton() {
        init();

    }

    private void init() {
        setIcon(new SVGIconUIColor("icon/about.svg", 0.025f, "MenuItem.foreground"));
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
