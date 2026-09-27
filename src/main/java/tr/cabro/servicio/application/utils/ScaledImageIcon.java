package tr.cabro.servicio.application.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Büyük bir raster görseli (logo vb.) keskin çizen ikon. {@code getScaledInstance} ile önceden
 * mantıksal boyuta küçültülmüş görsel, Windows ekran ölçeklemesinde (%125, %150...) Java2D
 * tarafından yeniden BÜYÜTÜLÜYOR ve bulanık çıkıyordu. Bu ikon, çizim anındaki gerçek piksel
 * boyutunu grafik dönüşümünden okur, görseli o boyuta kademeli olarak küçültür (tek adımda
 * küçültme ayrıntıyı kaybediyor) ve piksel piksel çizer. Sonuç ölçek başına önbelleklenir.
 */
public class ScaledImageIcon implements Icon {

    private final Image source;
    private final int width;
    private final int height;
    private BufferedImage cached;

    /** {@code width}/{@code height} mantıksal boyuttur (gerekirse {@code UIScale.scale} ile verilir). */
    public ScaledImageIcon(Image source, int width, int height) {
        this.source = source;
        this.width = width;
        this.height = height;
    }

    /** Görseli oranını koruyarak verilen mantıksal yüksekliğe sığdırır; okunamazsa null. */
    public static ScaledImageIcon ofHeight(Image source, int height) {
        int iw = source.getWidth(null), ih = source.getHeight(null);
        if (iw <= 0 || ih <= 0) return null;
        return new ScaledImageIcon(source, Math.max(1, iw * height / ih), height);
    }

    /** Görseli oranını koruyarak verilen kutuya sığdırır; okunamazsa null. */
    public static ScaledImageIcon fit(Image source, int boxWidth, int boxHeight) {
        int iw = source.getWidth(null), ih = source.getHeight(null);
        if (iw <= 0 || ih <= 0) return null;
        double scale = Math.min((double) boxWidth / iw, (double) boxHeight / ih);
        return new ScaledImageIcon(source, Math.max(1, (int) (iw * scale)), Math.max(1, (int) (ih * scale)));
    }

    /** Görev çubuğu/pencere ikonu için standart boyutlarda keskin kopyalar. */
    public static List<Image> windowIcons(Image source) {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 20, 24, 32, 40, 48, 64, 128, 256}) {
            icons.add(downscale(source, size, size));
        }
        return icons;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g;
        AffineTransform t = g2.getTransform();
        int dw = Math.max(1, (int) Math.round(width * t.getScaleX()));
        int dh = Math.max(1, (int) Math.round(height * t.getScaleY()));
        if (cached == null || cached.getWidth() != dw || cached.getHeight() != dh) {
            cached = downscale(source, dw, dh);
        }
        g2.drawImage(cached, x, y, width, height, null);
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }

    /** Her adımda en fazla yarıya küçülterek hedef boyuta iner (bilineer, yüksek kalite). */
    private static BufferedImage downscale(Image source, int targetW, int targetH) {
        int w = source.getWidth(null), h = source.getHeight(null);
        BufferedImage current = toBuffered(source, w, h);
        while (w != targetW || h != targetH) {
            w = w / 2 >= targetW ? w / 2 : targetW;
            h = h / 2 >= targetH ? h / 2 : targetH;
            BufferedImage next = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(current, 0, 0, w, h, null);
            g.dispose();
            current = next;
        }
        return current;
    }

    private static BufferedImage toBuffered(Image source, int w, int h) {
        if (source instanceof BufferedImage b && b.getType() == BufferedImage.TYPE_INT_ARGB) return b;
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return b;
    }
}
