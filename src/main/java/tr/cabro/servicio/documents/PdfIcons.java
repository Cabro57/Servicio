package tr.cabro.servicio.documents;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.lowagie.text.Image;
import tr.cabro.servicio.Servicio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Belgelerde kullanılan küçük çizgi ikonlar (telefon, e-posta, konum…). Uygulamanın kendi SVG
 * ikonları yüksek çözünürlükte bir görüntüye çizilir ve PDF'e gömülür; renk verilen tondadır
 * (belgeler siyah-beyaz basılır). Aynı ikon+renk bir kez çizilir, sonra önbellekten gelir.
 */
final class PdfIcons {

    /** Çizim çözünürlüğü (piksel); PDF'te birkaç punto boyuna küçültülür, baskıda keskin kalır. */
    private static final int RASTER = 96;

    private static final Map<String, java.awt.Image> CACHE = new ConcurrentHashMap<>();

    private PdfIcons() {}

    /**
     * @param path  "icons/phone.svg"
     * @param color çizgi rengi
     * @param size  PDF'teki boy (pt)
     * @return ikon; çizilemezse null (belge ikonsuz devam eder)
     */
    static Image get(String path, Color color, float size) {
        try {
            java.awt.Image raster = CACHE.computeIfAbsent(path + "#" + color.getRGB(), k -> render(path, color));
            if (raster == null) return null;
            Image image = Image.getInstance(raster, null);
            image.scaleAbsolute(size, size);
            return image;
        } catch (Exception e) {
            Servicio.getLogger().warn("Belge ikonu çizilemedi: " + path, e);
            return null;
        }
    }

    private static java.awt.Image render(String path, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon(path, 24, 24);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
        BufferedImage img = new BufferedImage(RASTER, RASTER, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            // İkonun ekran ölçeği (UIScale) ne olursa olsun görüntüyü tam kaplasın.
            double scale = (double) RASTER / Math.max(1, icon.getIconWidth());
            g.scale(scale, scale);
            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        return img;
    }
}
