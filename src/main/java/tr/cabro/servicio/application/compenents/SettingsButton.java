package tr.cabro.servicio.application.compenents;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.application.ui.SettingsUI;

import javax.swing.*;
import java.awt.*;

public class SettingsButton extends JButton {

    public SettingsButton() {
        init();

    }

    private void init() {
        FlatSVGIcon icon = new FlatSVGIcon("icon/settings.svg", 18, 18);
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
            SettingsUI settingsUI = new SettingsUI();
            settingsUI.setModal(true);
            settingsUI.setVisible(true);
        });
    }
}
