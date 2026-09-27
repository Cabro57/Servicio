package tr.cabro.servicio.application.component.detail;

import tr.cabro.servicio.application.component.table.ViewTabs;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Detay sayfasının geniş sütununda aynı yeri paylaşan liste bölümleri (ör. "Stok hareketleri" /
 * "Kullanıldığı servisler"). Her bölümün başlığı altında aynı sekme çubuğu durur; sekme değişince
 * bölüm değişir, tüm çubuklar eşitlenir. Bölümler farklı kolonlara sahip olabildiği için tek
 * tabloda süzmek yerine kart değiştirilir.
 */
public final class SectionSwitcher extends JPanel {

    private final CardLayout cards = new CardLayout();
    private final List<ViewTabs> tabBars = new ArrayList<>();

    /** {@code keys[i]} sekmesi {@code sections[i]} bölümünü gösterir; ilk bölüm açık başlar. */
    public SectionSwitcher(String[] keys, String[] labels, DetailListSection<?>... sections) {
        setLayout(cards);
        setOpaque(false);
        for (int i = 0; i < sections.length; i++) {
            ViewTabs tabs = new ViewTabs();
            for (int j = 0; j < keys.length; j++) tabs.addView(keys[j], labels[j]);
            tabs.setOnChange(this::show);
            sections[i].setTabs(tabs);
            tabBars.add(tabs);
            add(sections[i], keys[i]);
        }
    }

    public void show(String key) {
        cards.show(this, key);
        tabBars.forEach(t -> t.select(key, false));
    }

    public void setCount(String key, long count) {
        tabBars.forEach(t -> t.setCount(key, count));
    }
}
