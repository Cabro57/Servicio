package tr.cabro.servicio.application.renderer;

import com.formdev.flatlaf.util.UIScale;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.model.enums.BadgeColor;

import javax.swing.*;
import java.awt.*;

/**
 * Liste hücrelerinin ortak parçaları: çip verisi ({@link Badge}), tutar anlamı ({@link Money}),
 * çip çizen bileşen ({@link BadgeChip}) ve kısa tarih biçimleri.
 */
public final class RowParts {

    private RowParts() {}

    /** Tutarın anlamı: rengini ve sıfırda ne yazılacağını belirler (bkz. {@link MoneyCellRenderer.Mode}). */
    public enum Money { NEUTRAL, OWED, BALANCE, NEGATIVE }

    /** Çip: metin + renk çifti. */
    public static final class Badge {
        final String text;
        final BadgeColor color;

        public Badge(String text, BadgeColor color) {
            this.text = text;
            this.color = color;
        }
    }

    /** "24 Eyl 14:30" — satır alt satırlarındaki kısa zaman. */
    public static String when(java.time.LocalDateTime t) {
        return t == null ? "" : t.format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm", tr.cabro.servicio.i18n.AppLocale.uiLocale()));
    }

    /** "24 Eyl 2026" — saatsiz kısa gün. */
    public static String day(java.time.LocalDateTime t) {
        return t == null ? "" : t.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", tr.cabro.servicio.i18n.AppLocale.uiLocale()));
    }

    /** Durum rozeti: yuvarlak dolgu + kalın küçük metin, renkleri {@link BadgePalette}'ten. */
    public static final class BadgeChip extends JComponent {
        private Badge badge;
        private final int height;
        private final float shrink;

        public BadgeChip(int height, float shrink) {
            this.height = height;
            this.shrink = shrink;
        }

        public void set(Badge b) {
            this.badge = b;
            invalidate();
        }

        @Override
        public Dimension getPreferredSize() {
            if (badge == null) return new Dimension(0, UIScale.scale(height));
            FontMetrics fm = getFontMetrics(font());
            return new Dimension(fm.stringWidth(badge.text) + UIScale.scale(height - 4), UIScale.scale(height));
        }

        private Font font() {
            Font f = UIManager.getFont("Label.font");
            return f.deriveFont(Font.BOLD, f.getSize2D() - shrink);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (badge == null || badge.text == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font());
            FontMetrics fm = g2.getFontMetrics();
            int w = Math.min(getWidth(), fm.stringWidth(badge.text) + UIScale.scale(height - 4));
            int h = UIScale.scale(height);
            int y = (getHeight() - h) / 2;
            BadgeColor c = badge.color != null ? badge.color : BadgeColor.GRAY;
            g2.setColor(BadgePalette.background(c));
            g2.fillRoundRect(0, y, w, h, h, h);
            g2.setColor(BadgePalette.foreground(c));
            g2.drawString(badge.text, UIScale.scale((height - 4) / 2), y + (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
