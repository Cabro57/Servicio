package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.system.QuickAction;

import javax.swing.*;

/**
 * Ayarlar &gt; Uygulama &gt; Klavye kısayolları: salt bilgi sayfası. Kısayollar eskiden alt çubukta
 * ipucu olarak duruyordu; tam liste burada, kapsamına göre gruplu (her yerde, POS, müşteri kartı).
 * Genel kısayollar {@link QuickAction}'dan okunur, böylece liste kodla birlikte güncel kalır.
 */
public class SettingsShortcutsPanel extends JPanel {

    public SettingsShortcutsPanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());
    }

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        JPanel everywhere = table();
        row(everywhere, "Ctrl+K", "Müşteri, servis, parça ara ya da bir işlem çalıştır");
        row(everywhere, "Ctrl+F", "Liste sayfasında aramaya git (başka yerde Ctrl+K gibi çalışır)");
        for (QuickAction action : QuickAction.values()) {
            row(everywhere, action.getShortcutText(), action.getLabel() + " — " + action.getDescription());
        }
        SettingsKit.section(page, "Her yerde", "Hangi ekranda olursanız olun çalışır; açık bir pencere varken Alt kısayolları beklemeye alınır.", everywhere);

        JPanel pos = table();
        row(pos, "F1", "Nakit ile satışı tamamla");
        row(pos, "F2", "Kartla (POS) satışı tamamla");
        row(pos, "F3", "Müşterinin açık hesabına yaz");
        row(pos, "F4", "Parçalı ödeme (nakit, kart, açık hesap)");
        row(pos, "F5", "Barkod alanına git");
        row(pos, "F6", "Ürün ara");
        row(pos, "F7", "Ödenen tutar alanına git");
        row(pos, "F8", "Sepete geç");
        row(pos, "Ctrl+T", "Yeni sepet aç");
        row(pos, "Ctrl+W", "Açık sepeti kapat");
        row(pos, "+  /  −", "Sepette seçili ürünün adedini artır / azalt");
        row(pos, "Delete", "Sepetten seçili ürünü çıkar");
        row(pos, "Ctrl+I", "Seçili ürüne iskonto uygula");
        SettingsKit.section(page, "Satış (POS)", "Yalnızca POS ekranı açıkken.", pos);

        JPanel customer = table();
        row(customer, "Ctrl+1 … 6", "Müşteri kartında bölümler arasında geç");
        SettingsKit.section(page, "Müşteri kartı", "Bir müşterinin kartı açıkken.", customer);

        JPanel settings = table();
        row(settings, "Ctrl+F", "Ayar ara");
        row(settings, "↑  /  ↓", "Ayar sayfaları arasında gez");
        SettingsKit.section(page, "Ayarlar", "Bu pencere açıkken.", settings);

        return page;
    }

    /** Solda tuş(lar), sağda ne yaptığı. Tuş sütunu sabit genişlikte: açıklamalar tek hizada başlar. */
    private static JPanel table() {
        JPanel p = new JPanel(new MigLayout("wrap 2, insets 0, fillx, gapx 16, gapy 8", "[110!][grow, fill]", "[center]"));
        p.setOpaque(false);
        return p;
    }

    private static void row(JPanel table, String keys, String description) {
        JLabel key = new JLabel(keys);
        key.putClientProperty(FlatClientProperties.STYLE,
                "font: -1; foreground: $Label.foreground; border: 1,6,1,6,$Component.borderColor,1,6");
        table.add(key, "growx 0");
        JLabel text = new JLabel(description);
        table.add(text, "wmin 0");
    }
}
