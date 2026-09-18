package tr.cabro.servicio.application.renderer;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.model.contract.Visualizable;
import tr.cabro.servicio.application.ui.IconManager;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.model.enums.BadgeColor;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class UniversalVisualizableRenderer extends DefaultTableCellRenderer {

    private final int iconSize;
//    private final int horizontalAlignment;
    private final JLabel badgeLabel;
    private final JPanel content;

    public UniversalVisualizableRenderer(int horizontalAlignment, int iconSize) {
        this.iconSize = iconSize;

        String alignX = horizontalAlignment == SwingConstants.CENTER ? "center" :
                horizontalAlignment == SwingConstants.RIGHT ? "right" : "left";

        content = new JPanel();
        content.setLayout(new MigLayout("insets 2 4 2 4, fill", "[" + alignX + ", grow]", "[center]"));
        setOpaque(true);

        badgeLabel = new JLabel();
        badgeLabel.setOpaque(true);

        badgeLabel.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc: 999;"
                + "border: 2,10,2,10;"
                + "font: bold +0");

        content.add(badgeLabel);

    }

    public UniversalVisualizableRenderer(int horizontalAlignment) {
        this(horizontalAlignment, 16);
    }

    public UniversalVisualizableRenderer() {
        this(SwingConstants.LEFT, 16); // Varsayılan değerler
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);


        // Zebra satır rengini koru: seçili değilse tek satırlarda alternateRowColor gelir
        content.setBackground(TableRendererSupport.rowBackground(table, row, isSelected));

        if (value instanceof Visualizable) {
            Visualizable item = (Visualizable) value;
            badgeLabel.setText(item.getDisplayName());

            BadgeColor colorObj = item.getBadgeColor();
            if (colorObj != null) {
                // Tema-duyarlı çözüm: açık temada pastel, koyu temada derin zemin.
                Color bgColor = BadgePalette.background(colorObj);
                Color fgColor = BadgePalette.foreground(colorObj);

                badgeLabel.setBackground(bgColor);
                badgeLabel.setForeground(fgColor);

                // Dinamik İkon Renklendirme (İkonu Foreground rengine boyar).
                // setColorFilter nesneyi değiştirip kendisini döndürür, kopya üretmez —
                // bu yüzden ikon bu renderer'a özel olmalı. IconManager artık her çağrıda
                // yeni örnek veriyor; paylaşılan örnek döndürseydi buradaki filtre, aynı
                // ikonu kullanan diğer bileşenlerin rengini de kalıcı olarak değiştirirdi.
                FlatSVGIcon icon = IconManager.getIcon(item.getIconPath(), iconSize);
                if (icon != null) {
                    icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> fgColor));
                }
                badgeLabel.setIcon(icon);
            } else {
                badgeLabel.setIcon(null);
            }

            badgeLabel.setIconTextGap(6);
            badgeLabel.setVisible(true);
        } else {
            badgeLabel.setVisible(false);
        }

        return content;
    }
}