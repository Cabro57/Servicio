package tr.cabro.servicio.application.themes;

import com.formdev.flatlaf.FlatLaf;
import tr.cabro.servicio.model.enums.BadgeColor;

import java.awt.*;

/**
 * {@link BadgeColor} çiftlerini aktif temaya göre çözer. Rozet rengi okuyan her yer
 * (rozet bileşeni, tablo renderer'ı, stil dizeleri) buradan geçer — böylece tema
 * anahtarı çevrildiğinde rozetler de dönüşür.
 * <p>
 * Doğrudan {@code BadgeColor.getBackgroundHex()} okuyup {@code Color.decode} etmeyin:
 * o çağrı her zaman açık tema değerini verir ve koyu temada okunmaz.
 */
public final class BadgePalette {

    private BadgePalette() {}

    public static String backgroundHex(BadgeColor color) {
        return FlatLaf.isLafDark() ? color.getDarkBackgroundHex() : color.getBackgroundHex();
    }

    public static String foregroundHex(BadgeColor color) {
        return FlatLaf.isLafDark() ? color.getDarkForegroundHex() : color.getForegroundHex();
    }

    public static Color background(BadgeColor color) {
        return Color.decode(backgroundHex(color));
    }

    public static Color foreground(BadgeColor color) {
        return Color.decode(foregroundHex(color));
    }

    /** Rozet görünümlü etiket/buton stili — zemin, yazı ve yuvarlaklık tek yerden. */
    // "border: t,l,b,r" butonun FlatLaf çerçevesini düz boşluk kenarlığıyla değiştirir; bu yüzden
    // extra'da borderWidth/borderColor gibi çerçeve stilleri KULLANILMAMALI (UnknownStyleException).
    public static String style(BadgeColor color, String extra) {
        return "background: " + backgroundHex(color)
                + "; foreground: " + foregroundHex(color)
                + "; arc: 15; border: 4,10,4,10; font: bold -1"
                + (extra == null || extra.isEmpty() ? "" : "; " + extra);
    }
}
