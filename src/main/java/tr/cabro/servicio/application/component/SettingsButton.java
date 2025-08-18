package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.ui.SettingsUI;
import tr.cabro.servicio.icons.SVGIconUIColor;

import javax.swing.*;

public class SettingsButton extends JButton {

    public SettingsButton() {
        init();

    }

    private void init() {
        setIcon(new SVGIconUIColor("icon/settings.svg", 0.025f, "MenuItem.foreground"));
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(true);
        putClientProperty(FlatClientProperties.STYLE,
                "background:$Panel.background;" +
                "hoverBackground:tint($Panel.background, 20%);" +
                "margin:null");

        addActionListener(e -> {
            SettingsUI settingsUI = new SettingsUI();
            settingsUI.setVisible(true);
        });
    }
}
