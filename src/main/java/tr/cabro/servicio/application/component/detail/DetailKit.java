package tr.cabro.servicio.application.component.detail;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.awt.*;

/**
 * Detay sayfalarının ortak yapı taşları: kart, kart başlığı, bilgi satırı (soluk etiket + değer),
 * birincil/ikincil düğme. Kartlar liste sayfalarıyla aynı beyaz zemini kullanır ({@code listCard});
 * kart içinde kart yoktur.
 */
public final class DetailKit {

    private DetailKit() {}

    /** Başlıklı kart. Başlık satırının sağına {@code headerRight} (bağlantı, düğme) konabilir. */
    public static JPanel card(String title, JComponent headerRight) {
        JPanel card = new JPanel(new MigLayout("insets 14 16 14 16, fillx, wrap, hidemode 3", "[grow, fill]", "[]10[]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        JPanel header = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[grow][]", "[center]"));
        header.setOpaque(false);
        header.add(title(title), "wmin 0");
        if (headerRight != null) header.add(headerRight);
        card.add(header);
        return card;
    }

    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        return l;
    }

    public static JLabel small(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        return l;
    }

    /** Bilgi listesi paneli: {@link #fact} satırları eklenir (etiket solda soluk, değer sağda). */
    public static JPanel facts() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, gap 12 8, hidemode 3", "[shrink 0][grow, right]", ""));
        p.setOpaque(false);
        return p;
    }

    /** Bilgi satırı ekler ve değer etiketini döner (sonradan güncellemek için). */
    public static JLabel fact(JPanel facts, String caption) {
        JLabel value = new JLabel("—");
        value.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        facts.add(muted(caption));
        facts.add(value, "wmin 0, wrap");
        return value;
    }

    public static void setFact(JLabel value, String text) {
        value.setText(text == null || text.isBlank() ? "—" : text);
        value.setToolTipText(text == null || text.isBlank() ? null : text);
    }

    public static JButton secondaryButton(String text, String iconPath, Runnable action) {
        JButton b = iconPath != null ? new JButton(text, new Ikon(iconPath, 16, "Label.foreground")) : new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 7,12,7,12; iconTextGap: 6");
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    public static JButton primaryButton(String text, String iconPath, Runnable action) {
        JButton b = iconPath != null ? new JButton(text, new Ikon(iconPath, 16, "Servicio.onAccentForeground")) : new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 7,14,7,14; iconTextGap: 6; font: bold;"
                + " borderWidth: 0; focusWidth: 0; innerFocusWidth: 1;"
                + " background: $Component.accentColor; foreground: $Servicio.onAccentForeground;"
                + " hoverBackground: darken($Component.accentColor,6%); pressedBackground: darken($Component.accentColor,12%)");
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    /**
     * Genel kısayolu olan birincil düğme: ikon + kalın ad + kutulu tuş ipucu (ör. "Alt+T").
     * Liste sayfalarının "Yeni …" düğmesiyle aynı anatomi (bkz. AbstractTableForm).
     */
    public static JButton primaryButton(String text, String iconPath, Runnable action,
                                        tr.cabro.servicio.application.system.QuickAction shortcut) {
        JButton b = new JButton() {
            @Override public Dimension getPreferredSize() { return getLayout().preferredLayoutSize(this); }
            @Override public Dimension getMinimumSize() { return getLayout().minimumLayoutSize(this); }
        };
        b.setLayout(new MigLayout("insets 0, gap 8", "[][]", "[center]"));
        JLabel label = new JLabel(text, iconPath != null ? new Ikon(iconPath, 16, "Servicio.onAccentForeground") : null, SwingConstants.LEADING);
        label.setIconTextGap(6);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Servicio.onAccentForeground");
        b.add(label);
        if (shortcut != null) {
            JLabel key = new JLabel(shortcut.getShortcutText());
            key.putClientProperty(FlatClientProperties.STYLE, "font: -2; foreground: fade($Servicio.onAccentForeground,85%);"
                    + " background: fade($Servicio.onAccentForeground,12%);"
                    + " border: 1,5,1,5,fade($Servicio.onAccentForeground,40%),1,6");
            b.add(key);
            b.setToolTipText(text + " (" + shortcut.getShortcutText() + ")");
        }
        b.getAccessibleContext().setAccessibleName(text);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 7,14,7,12; borderWidth: 0; focusWidth: 0; innerFocusWidth: 1;"
                + " background: $Component.accentColor; hoverBackground: darken($Component.accentColor,6%);"
                + " pressedBackground: darken($Component.accentColor,12%)");
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    /** Başlık satırındaki metin bağlantısı ("Müşteriye git"). */
    public static JButton link(String text, Runnable action) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        // Metin olarak vurgu rengi koyu temada kart zemininde okunmuyordu; bağlantı rengi iki temada da okunur.
        b.putClientProperty(FlatClientProperties.STYLE, "foreground: $Component.linkColor; margin: 2,6,2,6");
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Dikey kaydırmalı gövde (ince çubuk, hızlı adım); içerik genişliği pencereye uyar. */
    public static JScrollPane scroll(JComponent content) {
        JPanel tracker = new WidthTrackingPanel();
        tracker.add(content);
        JScrollPane sp = new JScrollPane(tracker);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, "width: 8");
        return sp;
    }

    /** Görünüm alanının genişliğini izleyen kap: yatay kaydırma olmaz, içerik pencereyle daralır. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel() {
            super(new MigLayout("insets 0, fill", "[grow, fill]", "[grow, fill]"));
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            // İçerik kısa ise görünüm yüksekliğini doldur (kartlar alta uzayabilsin).
            return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
        }
    }
}
