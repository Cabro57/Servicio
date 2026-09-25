package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Kilit ve kurulum ekranlarının zemini: sabit, temadan türeyen üç yumuşak ışık lekesi. Renkleri
 * vurgu renginden türetilir (vurgunun kendisi ve tonu iki yana biraz kaydırılmış iki kardeşi). Hareket yok. Zemin boyut,
 * tema ya da vurgu değişince bir kez yeniden çizilir; diğer boyamalarda hazır resim kullanılır.
 */
public final class AmbientLights {
    // Her ışık: merkez x/y (ekran oranı), çap (uzun kenar oranı), ton kayması (derece).
    private static final double[][] LIGHTS = {
            {0.18, 0.22, 0.70, 0},
            {0.86, 0.80, 0.66, 28},
            {0.80, 0.12, 0.44, -26},
    };

    private BufferedImage cache;
    private Color cachedAccent;
    private boolean cachedDark;

    public void paint(Graphics2D g, int w, int h) {
        if (w <= 0 || h <= 0) return;
        Color accent = UIManager.getColor("Component.accentColor");
        boolean dark = FlatLaf.isLafDark();
        if (cache == null || cache.getWidth() != w || cache.getHeight() != h
                || !accent.equals(cachedAccent) || dark != cachedDark) {
            cache = render(w, h, accent, dark);
            cachedAccent = accent;
            cachedDark = dark;
        }
        g.drawImage(cache, 0, 0, null);
    }

    private static BufferedImage render(int w, int h, Color accent, boolean dark) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Koyu temada ışık daha belirgin olabilir; açık temada yazıyı bastırmasın diye daha hafif.
        float alpha = dark ? 0.32f : 0.20f;
        int longSide = Math.max(w, h);
        for (double[] l : LIGHTS) {
            Color c = shiftHue(accent, (float) l[3]);
            float r = (float) (longSide * l[2] / 2);
            float cx = (float) (l[0] * w), cy = (float) (l[1] * h);
            g2.setPaint(new RadialGradientPaint(cx, cy, r, new float[]{0f, 0.45f, 1f},
                    new Color[]{withAlpha(c, alpha), withAlpha(c, alpha * 0.4f), withAlpha(c, 0f)}));
            g2.fillRect((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r));
        }
        g2.dispose();
        return img;
    }

    private static Color shiftHue(Color c, float degrees) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float hue = (hsb[0] + degrees / 360f + 1f) % 1f;
        return Color.getHSBColor(hue, hsb[1], hsb[2]);
    }

    private static Color withAlpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(a * 255));
    }
}
