package tr.cabro.servicio.application.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.renderer.RowParts;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.model.enums.BadgeColor;

import javax.swing.*;
import java.awt.*;

/**
 * Ana sayfadaki liste kartlarının ortak satırı: solda durum noktası, ortada kalın ad ve soluk alt satır,
 * sağda tutar/çip. Satırın tamamı tıklanır (üstüne gelince zemini belirir).
 */
final class RowKit {

    private RowKit() {}

    /** Durum rengiyle küçük dolu nokta. */
    private static final class Dot extends JComponent {
        private final BadgeColor color;

        Dot(BadgeColor color) {
            this.color = color;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UIScale.scale(14), UIScale.scale(14));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (color == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = UIScale.scale(8);
            g2.setColor(BadgePalette.foreground(color));
            g2.fillOval(0, (getHeight() - d) / 2, d, d);
            g2.dispose();
        }
    }

    /**
     * Satır düğmesi. {@code subColorKey} alt satırın anlam rengi (tema anahtarı), null ise soluk.
     * {@code right} sağ bölge (tutar, çip); null olabilir.
     */
    static JButton row(BadgeColor dot, String title, String sub, String subColorKey, JComponent right) {
        JButton row = DashboardUi.rowButton();
        // Satırlar arası nefes: dikey iç boşluk rowButton'dakinden büyük (ana sayfadaki tüm RowKit satırları için).
        row.putClientProperty(FlatClientProperties.STYLE,
                "background: null; arc: 10; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 8,10,8,10");
        row.setLayout(new MigLayout("insets 0, fillx, gap 0 1, hidemode 3", "[16!][grow,fill]12[right]", "[][]"));

        row.add(new Dot(dot), "cell 0 0, aligny center");
        JLabel name = new JLabel(title);
        name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        row.add(name, "cell 1 0, wmin 0");

        JLabel subLabel = DashboardUi.small(sub);
        if (subColorKey != null) {
            subLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $" + subColorKey);
        }
        row.add(subLabel, "cell 1 1, wmin 0");

        if (right != null) row.add(right, "cell 2 0, spany 2, aligny center");
        return row;
    }

    /** Üstte kalın tutar, altında soluk açıklama ve ödeme çipi; sağa yaslı. */
    static JPanel money(String amountText, String amountColorKey, String caption, RowParts.Badge chip) {
        JPanel p = new JPanel(new MigLayout("insets 0, wrap, gap 0 3, hidemode 3", "[right]", "[]"));
        p.setOpaque(false);
        if (amountText != null) {
            JLabel amount = new JLabel(amountText);
            amount.putClientProperty(FlatClientProperties.STYLE,
                    "font: bold +1" + (amountColorKey != null ? "; foreground: $" + amountColorKey : ""));
            p.add(amount);
        }
        JPanel line = new JPanel(new MigLayout("insets 0, gap 6, hidemode 3", "[][]", "[center]"));
        line.setOpaque(false);
        if (caption != null) line.add(DashboardUi.small(caption));
        if (chip != null) {
            RowParts.BadgeChip c = new RowParts.BadgeChip(20, 2f);
            c.set(chip);
            line.add(c);
        }
        p.add(line);
        return p;
    }

    /** Tek çip. */
    static JComponent chip(RowParts.Badge badge, int height) {
        RowParts.BadgeChip c = new RowParts.BadgeChip(height, height >= 24 ? 1f : 2f);
        c.set(badge);
        return c;
    }
}
