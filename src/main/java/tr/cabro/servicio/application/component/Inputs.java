package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;

/**
 * Tüm ekranların ortak giriş ve düğme yapı taşları.
 * <p>
 * Boy ve köşe bileşene yazılmaz; {@code themes/FlatLaf.properties} "Kontroller" bölümünden gelir
 * (alan, combo ve düğme aynı yükseklik, 10px köşe). Burada yalnızca anlam taşıyan farklar var:
 * arama alanının ikonu ve temizle düğmesi, birincil/tehlike düğmesinin dolgusu.
 */
public final class Inputs {

    private Inputs() {}

    /**
     * Arama alanı: solda soluk büyüteç, sağda temizle (x); ipucu neyin arandığını söyler.
     * Liste sayfası, ayar listeleri, modallar ve açılır menüler (ülke listesi, sütun filtresi) aynı alanı kullanır.
     */
    public static JTextField search(String placeholder) {
        return search(new JTextField(), placeholder);
    }

    /** Var olan bir alanı arama alanına çevirir (alt sınıflı alanlar için). */
    public static <T extends JTextField> T search(T field, String placeholder) {
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, searchIcon());
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        return field;
    }

    /** Alan içi ikonların ortak ölçüsü: 16px, soluk. */
    public static Icon fieldIcon(String path) {
        return new Ikon(path, 16, "Label.disabledForeground");
    }

    private static Icon searchIcon() {
        return fieldIcon("icons/search.svg");
    }

    /** Birincil eylem düğmesi (ekranda tek): vurgu dolgusu, kalın yazı, 16px ikon. */
    public static JButton primary(String text, String iconPath) {
        JButton b = iconPath != null
                ? new JButton(text, new Ikon(iconPath, 16, "Servicio.onAccentForeground"))
                : new JButton(text);
        return primary(b);
    }

    public static <T extends AbstractButton> T primary(T button) {
        button.putClientProperty(FlatClientProperties.STYLE_CLASS, "primary");
        return button;
    }

    /** Geri alınamaz eylem düğmesi (Sil, Taşı ve sil): birincil anatomisi, tehlike dolgusu. */
    public static JButton danger(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE_CLASS, "danger");
        return b;
    }

    /** İkincil eylem düğmesi: varsayılan düğme, 16px ikon. */
    public static JButton secondary(String text, String iconPath) {
        return iconPath != null ? new JButton(text, new Ikon(iconPath, 16, "Label.foreground")) : new JButton(text);
    }
}
