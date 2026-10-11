package tr.cabro.servicio.application.utils;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import raven.datetime.DatePicker;

import javax.swing.*;
import java.awt.*;

/**
 * Açılır menülerin ortak davranışı. Kabuk görünümü (tablo zemini, ince çizgi, 12px köşe, nötr seçim
 * tonu) temada tanımlıdır ({@code themes/FlatLaf.properties}, "Acilir menuler"); burada yalnızca
 * temanın tek başına çözemediği üç şey var:
 * <ul>
 *   <li>{@link #showBelow}: düğmeden açılan her menü aynı 4px boşlukla, düğmenin sol kenarına hizalı açılır.</li>
 *   <li>{@link #blend}: içine panel konan menülerde (filtre, tarih aralığı, müşteri listesi) panellerin
 *       {@code Panel.background} zemini menünün tablo zemininde çerçeve gibi görünmesin.</li>
 *   <li>{@link #groupLabel}: menü içindeki grup başlıkları tek biçimde.</li>
 * </ul>
 */
public final class Popups {

    private static final int GAP = 4;

    private Popups() {
    }

    /** Menüyü düğmenin altında, sol kenarına hizalı ve 4px boşlukla açar. */
    public static void showBelow(JPopupMenu menu, Component anchor) {
        menu.show(anchor, 0, anchor.getHeight() + UIScale.scale(GAP));
    }

    /**
     * Menüye konan içeriği menü zeminine katar: paneller, kaydırma alanları ve görünüm pencereleri
     * saydamlaşır. Tarih seçici kendi çizimini yaptığı için içine girilmez, yalnızca zemini eşitlenir.
     */
    public static void blend(Component c) {
        if (c instanceof DatePicker picker) {
            picker.setBackground(UIManager.getColor("Table.background"));
            return;
        }
        if (c instanceof JPanel || c instanceof JScrollPane || c instanceof JViewport) {
            ((JComponent) c).setOpaque(false);
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) blend(child);
        }
    }

    /** Menü içindeki grup başlığı: soluk, bir kademe küçük; öğe metniyle aynı sol hizada. */
    public static JLabel groupLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        label.setBorder(BorderFactory.createEmptyBorder(6, 12, 3, 12));
        return label;
    }
}
