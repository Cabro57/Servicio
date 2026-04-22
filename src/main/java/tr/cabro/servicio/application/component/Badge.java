package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.model.contract.Visualizable;
import tr.cabro.servicio.model.enums.BadgeColor;

import javax.swing.*;
import java.awt.*;

public class Badge extends JLabel {

    private boolean showIcon = false;
    private boolean showBorder = false;

    private Visualizable visualizable;
    private Color customBgColor;
    private Color customFgColor;
    private String customIconPath;

    // --- CONSTRUCTORS ---
    public Badge(Visualizable visualizable) {
        this.visualizable = visualizable;
        updateStyle();
    }

    public Badge(String text, Color bgColor, Color fgColor) {
        super(text);
        this.customBgColor = bgColor;
        this.customFgColor = fgColor;
        updateStyle();
    }

    // --- ZİNCİRLEME (BUILDER) AYAR METOTLARI ---

    public Badge setShowIcon(boolean showIcon) {
        this.showIcon = showIcon;
        updateStyle();
        return this;
    }

    public Badge setShowBorder(boolean showBorder) {
        this.showBorder = showBorder;
        updateStyle();
        return this;
    }

    // --- VERİ GÜNCELLEME METOTLARI ---

    public void setVisualizable(Visualizable visualizable) {
        this.visualizable = visualizable;
        this.setText(visualizable.getDisplayName());
        updateStyle();
    }

    public void setCustom(String text, Color bgColor, Color fgColor, String iconPath) {
        this.visualizable = null;
        this.setText(text);
        this.customBgColor = bgColor;
        this.customFgColor = fgColor;
        this.customIconPath = iconPath;
        updateStyle();
    }

    // --- MOTOR (Görünümü Uygulayan Metot) ---
    private void updateStyle() {
        Color bg = visualizable != null ? Color.decode(visualizable.getBadgeColor().getBackgroundHex()) : customBgColor;
        Color fg = visualizable != null ? Color.decode(visualizable.getBadgeColor().getForegroundHex()) : customFgColor;
        String iconPath = visualizable != null ? visualizable.getIconPath() : customIconPath;

        // 1. İkon Ayarı (Renk filtresi ile birlikte)
        if (showIcon && iconPath != null && !iconPath.isEmpty()) {
            FlatSVGIcon icon = new FlatSVGIcon(iconPath, 0.75f);
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> fg)); // İkonu yazı rengine boya
            setIcon(icon);
            setIconTextGap(6);
        } else {
            setIcon(null);
        }

        // 2. Renkleri Hex formatına çevir
        String hexBg = String.format("#%02x%02x%02x", bg.getRed(), bg.getGreen(), bg.getBlue());
        String hexFg = String.format("#%02x%02x%02x", fg.getRed(), fg.getGreen(), fg.getBlue());

//        // 3. FlatLaf Stil Tanımı
//        String style = "arc: 999; insetes: 3,10,3,10; font: bold -1; ";
//
//        if (showBorder) {
//            // Çizgili (Border) Mod: İçi boş, sadece kenarlık ve yazı renkli
//            style += "border: 1,1,1,1," + hexFg + ";";
//        } else {
//            // Dolgulu Mod (Varsayılan)
//            style += "border: null; background: " + hexBg + ";";
//        }

        setForeground(fg);
        setBackground(bg);
        putClientProperty(FlatClientProperties.STYLE, ""
                + "arc: 999;"
                + "border: 2,10,2,10;"
                + "font: bold +0");
    }
}