package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.ui.IconManager;

import javax.swing.*;
import java.awt.*;

public class StatCard extends JPanel {

    public StatCard(String title, String value, String iconPath, Color valueColor) {
        // Layout: Başlık ve İkon üstte, Değer altta
        setLayout(new MigLayout("fill, insets 15", "[grow][pref]", "[][grow, bottom]"));

        // FlatLaf Kutu Tasarımı (Yuvarlak köşeli, panel arka planından bir ton farklı)
        putClientProperty(FlatClientProperties.STYLE, ""
                + "arc: 16;"
                + "background: lighten($Panel.background, 5%);");

        // Başlık
        JLabel titleLabel = new JLabel(title);
        titleLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: 13");

        // İkon (IconManager mimarimizi kullanıyoruz)
        JLabel iconLabel = new JLabel();
        if (iconPath != null) {
            iconLabel.setIcon(IconManager.getIcon(iconPath, 16));
        }

        // Değer (Örn: "5", "₺4.650,00")
        JLabel valueLabel = new JLabel(value);
        valueLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +12");
        if (valueColor != null) {
            valueLabel.setForeground(valueColor);
        }

        // Bileşenleri yerleştir
        add(titleLabel, "cell 0 0");
        add(iconLabel, "cell 1 0, alignx right");
        add(valueLabel, "cell 0 1 2 1"); // 2 sütunu kapla
    }
}