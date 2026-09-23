package tr.cabro.servicio.application.panels.dashboard;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.model.enums.BadgeColor;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/**
 * Ana sayfa bölümlerinin ortak görsel dili: kart zemini, başlık, soluk yardımcı metin,
 * para rengi ve kısayol tuşu etiketi. Stil dizeleri tema anahtarlarına ({@code $...}) bağlıdır,
 * tema değişince kendiliğinden döner.
 */
final class DashboardUi {

    private DashboardUi() {}

    /** Yuvarlak köşeli ana sayfa kartı (FlatLaf.properties: Panel.dashboardBackground). */
    static JPanel card(String layoutConstraints, String columns, String rows) {
        JPanel panel = new JPanel(new MigLayout(layoutConstraints, columns, rows));
        panel.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        return panel;
    }

    static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
        return label;
    }

    static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        return label;
    }

    static JLabel small(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        return label;
    }

    /** Klavye kısayolu etiketi, ör. "Alt+N" — ince çerçeveli küçük tuş görünümü. */
    static JLabel keyHint(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE,
                "font: -2; foreground: $Label.disabledForeground; border: 1,5,1,5,$Component.borderColor,1,6");
        return label;
    }

    /** Başlık satırının sağındaki metin düğmesi ("Tümünü gör" gibi). */
    static JButton link(String text, Runnable action) {
        JButton button = new JButton(text);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.putClientProperty(FlatClientProperties.STYLE, "foreground: $Component.accentColor; margin: 2,6,2,6");
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    /**
     * Para tutarının rengini işaretine göre ayarlar: artı yeşil, eksi kırmızı, sıfır nötr.
     * Uygulama genelindeki kural: bakiye rengi ve işareti her yerde aynı anlama gelir.
     */
    static void styleMoney(JLabel label, BigDecimal amount, String baseStyle) {
        String color;
        if (amount == null || amount.signum() == 0) color = "$Label.foreground";
        else color = amount.signum() > 0 ? "$Servicio.successColor" : "$Servicio.dangerColor";
        label.putClientProperty(FlatClientProperties.STYLE, baseStyle + "; foreground: " + color);
    }

    /** Durum/ödeme türü rozet rengine boyanan ikon; renk her çizimde okunur, tema değişimini izler. */
    static Icon badgeIcon(String path, int size, BadgeColor color) {
        FlatSVGIcon icon = new FlatSVGIcon(path, size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> BadgePalette.foreground(color)));
        return icon;
    }

    /** Liste satırı olarak kullanılan, üzerine gelince zemini beliren düz düğme. */
    static JButton rowButton() {
        JButton button = new LayoutButton();
        button.setHorizontalAlignment(SwingConstants.LEADING);
        button.putClientProperty(FlatClientProperties.STYLE,
                "background: null; arc: 10; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 4,8,4,8");
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** Boş durum: ne olduğunu ve nasıl dolacağını söyleyen iki satır. */
    static JPanel emptyState(String headline, String hint) {
        JPanel panel = new JPanel(new MigLayout("insets 18 8 18 8, wrap, al center", "[center]"));
        panel.setOpaque(false);
        JLabel h = new JLabel(headline);
        h.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        panel.add(h);
        JLabel d = small(hint);
        panel.add(d);
        return panel;
    }

    /**
     * İçeriği alt bileşenlerle (etiketler) kurulan düğme. BasicButtonUI tercih edilen boyutu
     * metin/ikon'dan hesapladığı için boyut yerleşimden alınır.
     */
    static class LayoutButton extends JButton {
        @Override
        public Dimension getPreferredSize() {
            if (isPreferredSizeSet() || getComponentCount() == 0) return super.getPreferredSize();
            return getLayout().preferredLayoutSize(this);
        }

        @Override
        public Dimension getMinimumSize() {
            if (getComponentCount() == 0) return super.getMinimumSize();
            return getLayout().minimumLayoutSize(this);
        }
    }
}
