package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.model.enums.BadgeColor;

import javax.swing.*;
import java.awt.*;

public class Badge extends JLabel {

    public Badge(String text, BadgeColor badgeColor) {
        this(text, Color.decode(badgeColor.getBackgroundHex()), Color.decode(badgeColor.getForegroundHex()));
    }

    public Badge(String text, Color bgColor, Color fgColor) {
        super(text);

        putClientProperty(FlatClientProperties.STYLE, "arc: 999; border: 2,10,2,10");
        setBackground(bgColor);
        setForeground(fgColor);
    }
}
