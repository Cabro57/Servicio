package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.awt.*;

/**
 * Ayarlar sayfalarının ortak yapı taşları.
 * <p>
 * Her sayfa aynı dili konuşur (uygulamadaki kayıt formlarıyla aynı, bkz. {@link FormKit}):
 * alt alta bölümler, aralarında ince çizgi; her bölümün solunda dar bir rayda başlık ve kısa
 * açıklama, sağında kontroller. Sayfa başlığı ve "Kaydedildi" göstergesi {@link SettingsModal}
 * kabuğundadır, sayfalar kendi başlığını çizmez.
 * <pre>{@code
 * JPanel page = SettingsKit.page();
 * JPanel rows = SettingsKit.rows();
 * rows.add(SettingsKit.label("Dil"));
 * rows.add(languageCombo, SettingsKit.FIELD);
 * SettingsKit.section(page, "Dil ve bölge", "Tarih ve para biçimi.", rows);
 * }</pre>
 */
final class SettingsKit {

    /** Satırdaki kontrol genişliği: formu doldursun ama geniş ekranda uzamasın. */
    static final String FIELD = "growx, wmax 380";

    private SettingsKit() {}

    /** Bölümleri alt alta dizen sayfa gövdesi. Modal kabuğu bunu kaydırılabilir alana koyar. */
    static JPanel page() {
        JPanel page = new JPanel(new MigLayout("wrap, insets 0 24 16 24, fillx, gap 0, hidemode 3", "[grow, fill]"));
        page.setOpaque(false);
        return page;
    }

    /**
     * Sayfaya bir bölüm ekler; ilk bölüm değilse üstüne ince çizgi koyar.
     * {@code content} bölümün sağ kolonunu kaplar.
     */
    static JPanel section(JPanel page, String title, String hint, JComponent content) {
        JPanel section = new JPanel(new MigLayout("insets 18 0 18 0, fillx, hidemode 3",
                "[" + FormKit.RAIL_WIDTH + "!]28[grow, fill]", "[top]"));
        section.setOpaque(false);
        section.add(FormKit.rail(title, hint), "top");
        section.add(content, "wmin 0");
        if (page.getComponentCount() > 0) page.add(new JSeparator(), "growx");
        page.add(section, "growx");
        return section;
    }

    /** Sol kolonda etiket, sağda kontrol; iki kolonlu satır ızgarası. */
    static JPanel rows() {
        JPanel p = new JPanel(new MigLayout("wrap 2, insets 0, fillx, gapx 16, gapy 10, hidemode 3",
                "[right, pref!][grow, fill]", "[center]"));
        p.setOpaque(false);
        return p;
    }

    /** Alt alta dizilen kontroller (anahtarlar, notlar, düğmeler). */
    static JPanel stack() {
        JPanel p = new JPanel(new MigLayout("wrap, insets 0, fillx, gapy 8, hidemode 3", "[grow, fill]"));
        p.setOpaque(false);
        return p;
    }

    static JLabel label(String text) {
        return new JLabel(text);
    }

    /** Tek satırlık soluk açıklama. */
    static JLabel note(String text) {
        return FormKit.note(text);
    }

    /** Kelime sınırından kırılan çok satırlı soluk açıklama. */
    static JTextArea wrappingNote(String text) {
        return FormKit.hint(text);
    }

    /** Başlığı ve altında açıklaması olan onay kutusu; açıklama kutunun metniyle hizalanır. */
    static JPanel check(JCheckBox box, String description) {
        JPanel p = new JPanel(new MigLayout("wrap, insets 0, gap 0, fillx", "[grow, fill]", "[]2[]"));
        p.setOpaque(false);
        p.add(box);
        if (description != null) {
            JTextArea hint = FormKit.hint(description);
            // Onay kutusu simgesi + boşluk kadar içeri: açıklama etiket metninin altında başlasın.
            int indent = UIManager.getIcon("CheckBox.icon") != null
                    ? UIManager.getIcon("CheckBox.icon").getIconWidth() + box.getIconTextGap() : 22;
            p.add(hint, "gapleft " + indent + ", wmin 0");
        }
        return p;
    }

    /**
     * Sözlük listelerinin kaydırma alanı. Tercih edilen yükseklik bilerek küçük: liste sayfayı
     * uzatmasın, sayfa pencereyi doldursun ve uzun liste kendi içinde kaysın.
     */
    static JScrollPane listScroll(JList<?> list) {
        JScrollPane scroll = new JScrollPane(list);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(200, 200));
        return scroll;
    }

    /** Sayfa başlığındaki eylem düğmesi (Ekle, Şimdi yedekle…). */
    static JButton headerButton(String text, String iconPath) {
        JButton b = new JButton(text, iconPath != null ? new Ikon(iconPath, 16) : null);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,12,5,12; iconTextGap: 6");
        return b;
    }

    /** Kabuğun başlığında kısa "Kaydedildi" göstergesini yakar; anında kaydedilen her değişiklikten sonra çağrılır. */
    static void saved(Component source) {
        SettingsModal modal = (SettingsModal) SwingUtilities.getAncestorOfClass(SettingsModal.class, source);
        if (modal != null) modal.flashSaved();
    }
}
