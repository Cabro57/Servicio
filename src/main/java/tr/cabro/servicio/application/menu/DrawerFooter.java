package tr.cabro.servicio.application.menu;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import raven.modal.drawer.menu.AbstractMenuElement;
import raven.modal.drawer.menu.MenuOption;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.ScaledImageIcon;

import javax.swing.*;
import java.awt.*;

/**
 * Menünün alt kısmı: ince bir çizginin altında solda uygulama kimliği (logo, ad ve yanında
 * sürüm; tık: Hakkında), sağda Ayarlar düğmesi. Ayarlar ve Hakkında menü öğesi olmaktan
 * çıkıp buraya taşındı; tema seçimi Ayarlar > Görünüm'de. Dar (ikon) modda logo ve
 * Ayarlar düğmesi alt alta kalır.
 */
public class DrawerFooter extends AbstractMenuElement {

    private static final int LOGO_SIZE = 26;

    /** Menü öğeleriyle aynı yuvarlaklık ve üzerine gelme tonu (bkz. MyDrawerBuilder.ITEM_STYLE). */
    private static final String BUTTON_STYLE = "arc:12;"
            + "toolbar.hoverBackground:fade($Label.foreground,5%);"
            + "toolbar.pressedBackground:fade($Label.foreground,9%)";

    private final MigLayout layout;
    private final MigLayout identityLayout;
    private final JButton identity;
    private final JButton settings;
    private final JLabel name;
    private final JLabel version;

    public DrawerFooter() {
        layout = new MigLayout("insets 0 12 12 12,gap 4 2", "[grow,fill][]", "[][]");
        setLayout(layout);
        putClientProperty(FlatClientProperties.STYLE, "background:null");

        JSeparator separator = new JSeparator();
        separator.putClientProperty(FlatClientProperties.STYLE, "height:17;stripeIndent:8");
        add(separator, "span,growx,gapx 8 8,wrap");

        String appVersion = Servicio.getInstance().getAppVersion();
        name = new JLabel("Servicio");
        name.putClientProperty(FlatClientProperties.STYLE, "font:bold");
        version = new JLabel("v" + appVersion);
        version.putClientProperty(FlatClientProperties.STYLE, "font:-2;foreground:$Label.disabledForeground");

        identityLayout = new MigLayout("hidemode 3,insets 6 8 6 8,gap 8 0", "[][][grow]", "[center]");
        identity = toolbarButton();
        identity.setLayout(identityLayout);
        identity.setToolTipText("Hakkında — sürüm ve destek bilgileri");
        identity.getAccessibleContext().setAccessibleName("Hakkında, Servicio sürüm " + appVersion);
        identity.addActionListener(e -> FormManager.showAbout());
        identity.add(new JLabel(loadLogo()));
        identity.add(name, "wmin 0");
        // Taban çizgisine hizalı küçük yazı gözde aşağıda kalıyor; dikeyde ortalanır.
        identity.add(version);
        add(identity);

        settings = toolbarButton();
        // 20 + 2×9 = 38: soldaki kimlik düğmesiyle (26 logo + 2×6 iç boşluk) aynı boyda kare.
        settings.setIcon(new Ikon("icons/settings.svg", 20, "Label.disabledForeground"));
        settings.setRolloverIcon(new Ikon("icons/settings.svg", 20, "Label.foreground"));
        settings.putClientProperty(FlatClientProperties.STYLE, BUTTON_STYLE + ";margin:9,9,9,9");
        settings.setToolTipText("Ayarlar");
        settings.getAccessibleContext().setAccessibleName("Ayarlar");
        settings.addActionListener(e -> FormManager.showSettings());
        add(settings, "growy");
    }

    private static JButton toolbarButton() {
        JButton b = new JButton();
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE, BUTTON_STYLE);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusable(false);
        return b;
    }

    private static Icon loadLogo() {
        java.net.URL url = DrawerFooter.class.getResource("/logo.png");
        if (url == null) return null;
        int size = UIScale.scale(LOGO_SIZE);
        return new ScaledImageIcon(new ImageIcon(url).getImage(), size, size);
    }

    @Override
    protected void layoutOptionChanged(MenuOption.MenuOpenMode menuOpenMode) {
        boolean full = menuOpenMode == MenuOption.MenuOpenMode.FULL;
        name.setVisible(full);
        version.setVisible(full);
        identityLayout.setLayoutConstraints(full ? "hidemode 3,insets 6 8 6 8,gap 8 0" : "hidemode 3,insets 6,gap 0");
        identityLayout.setColumnConstraints(full ? "[][][grow]" : "[center]");
        // Dar modda iki düğme tek sütunda alt alta, ortalı.
        layout.setColumnConstraints(full ? "[grow,fill][]" : "[grow,center]");
        layout.setComponentConstraints(identity, full ? "" : "wrap");
        layout.setComponentConstraints(settings, full ? "growy" : "");
        revalidate();
    }
}
