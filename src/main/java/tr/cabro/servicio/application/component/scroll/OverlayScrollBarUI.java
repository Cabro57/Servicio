package tr.cabro.servicio.application.component.scroll;

import com.formdev.flatlaf.ui.FlatScrollBarUI;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * İçeriğin üstüne binen kaydırma çubuğu (temada {@code ScrollBarUI} olarak kayıtlı). İz (track) yoktur;
 * yalnızca yazı renginden türeyen yarı saydam, tam yuvarlak bir tutamaç çizilir, böylece açık, koyu ve
 * OLED temada her içerik üstünde okunur. Dinlenmede ince (4px) ve kenara yaslıdır; fare üstüne gelince
 * ya da sürüklenirken kalınlaşır ve altında soluk bir iz belirir, tutması kolaylaşır.
 * <p>
 * Saydamlık kaydırma alanının {@link OverlayFader}'ından gelir. Gizliyken çubuk fareyi yakalamaz
 * ({@link #contains}): tablonun sağ kenarındaki tıklamalar alttaki satıra gider.
 */
public class OverlayScrollBarUI extends FlatScrollBarUI {

    private static final int THIN = 4;
    private static final int EDGE = 2;

    public static ComponentUI createUI(JComponent c) {
        return new OverlayScrollBarUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        scrollbar.setOpaque(false);
    }

    boolean isHovered() {
        return hoverTrack || hoverThumb || isDragging;
    }

    /** Kaydırma alanında değilse (tek başına kullanılan çubuk) her zaman görünür. */
    private float alpha() {
        Container parent = scrollbar.getParent();
        if (!(parent instanceof JScrollPane)) return 1f;
        OverlayFader fader = OverlayScrollPaneUI.fader(parent);
        return fader != null ? fader.alpha() : 1f;
    }

    @Override
    public boolean contains(JComponent c, int x, int y) {
        return alpha() > 0.05f && super.contains(c, x, y);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        float a = alpha();
        if (a <= 0f) return;
        Rectangle thumb = getThumbBounds();
        if (thumb.isEmpty() || !scrollbar.isEnabled()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            FlatUIUtils.setRenderingHints(g2);
            g2.setComposite(AlphaComposite.SrcOver.derive(a));

            boolean vertical = scrollbar.getOrientation() == SwingConstants.VERTICAL;
            int across = vertical ? c.getWidth() : c.getHeight();
            int edge = UIScale.scale(EDGE);
            int thin = Math.min(UIScale.scale(THIN), Math.max(1, across - edge));
            int size = isHovered() ? Math.max(thin, across - 2 * edge) : thin;
            // Kenara yaslı: dikey çubuk sağ kenara, yatay çubuk alt kenara.
            int offset = across - edge - size;

            Color ink = UIManager.getColor("Label.foreground");
            if (isHovered()) {
                g2.setColor(withAlpha(ink, 0.07f));
                fill(g2, vertical, offset, 0, size, vertical ? c.getHeight() : c.getWidth());
            }
            float strength = isDragging ? 0.58f : isHovered() ? 0.48f : 0.36f;
            g2.setColor(withAlpha(ink, strength));
            int start = vertical ? thumb.y : thumb.x;
            int length = vertical ? thumb.height : thumb.width;
            fill(g2, vertical, offset, start + edge, size, length - 2 * edge);
        } finally {
            g2.dispose();
        }
    }

    private static void fill(Graphics2D g2, boolean vertical, int offset, int start, int size, int length) {
        if (length <= 0) return;
        if (vertical) g2.fillRoundRect(offset, start, size, length, size, size);
        else g2.fillRoundRect(start, offset, length, size, size, size);
    }

    private static Color withAlpha(Color c, float alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(alpha * 255));
    }
}
