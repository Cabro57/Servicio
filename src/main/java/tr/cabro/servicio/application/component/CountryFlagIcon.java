package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.ColorFunctions;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ülke bayrağı: 4:3 oranında, köşeleri 2px yuvarlatılmış, çevresinde ince bir çizgi. Çizgi beyaz ya da
 * açık zeminli bayrakların (Japonya, Finlandiya…) kart zemininde kaybolmamasını sağlar; rengi temadan
 * okunur. Bayrağı bulunmayan bölgelerde yalnızca boş çerçeve çizilir, satır hizası bozulmaz.
 * <p>
 * Örnekler paylaşılır (renk süzgeci yok, değiştirilemez): her çizimde yeni {@link FlatSVGIcon} kurmak
 * classpath'te kaynak araması yapıyordu.
 */
final class CountryFlagIcon implements Icon {

    private static final Map<String, CountryFlagIcon> CACHE = new ConcurrentHashMap<>();

    private final FlatSVGIcon svg;
    private final int width;
    private final int height;

    private CountryFlagIcon(String path, int width, int height) {
        FlatSVGIcon icon = new FlatSVGIcon(path, width, height);
        this.svg = icon.hasFound() ? icon : null;
        this.width = width;
        this.height = height;
    }

    /** {@code width} ölçeklenmemiş piksel; yükseklik 4:3 oranından gelir. */
    static CountryFlagIcon of(String path, int width) {
        int height = Math.round(width * 3f / 4f);
        return CACHE.computeIfAbsent(path + '@' + width, k -> new CountryFlagIcon(path, width, height));
    }

    /** Devre dışı alanlar için soluk hâli (Swing özel ikonlardan kendisi üretemiyor). */
    Icon disabled() {
        CountryFlagIcon self = this;
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.SrcOver.derive(0.4f));
                self.paintIcon(c, g2, x, y);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return self.getIconWidth();
            }

            @Override
            public int getIconHeight() {
                return self.getIconHeight();
            }
        };
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float w = getIconWidth(), h = getIconHeight(), arc = UIScale.scale(4f);
            RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, arc, arc);
            if (svg != null) {
                Graphics2D clip = (Graphics2D) g2.create();
                clip.clip(shape);
                svg.paintIcon(c, clip, x, y);
                clip.dispose();
            }
            Color ink = UIManager.getColor("Label.foreground");
            g2.setColor(ColorFunctions.fade(ink, 0.16f));
            g2.setStroke(new BasicStroke(UIScale.scale(1f)));
            float half = UIScale.scale(0.5f);
            g2.draw(new RoundRectangle2D.Float(x + half, y + half, w - 2 * half, h - 2 * half, arc, arc));
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return UIScale.scale(width);
    }

    @Override
    public int getIconHeight() {
        return UIScale.scale(height);
    }
}
