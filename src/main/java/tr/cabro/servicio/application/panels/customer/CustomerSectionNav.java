package tr.cabro.servicio.application.panels.customer;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Müşteri detayının sol bölüm menüsü. Her öğe ikon + ad + kayıt sayısı taşır; sayı,
 * bölüme girmeden "bu müşterinin satışı var mı?" sorusunu cevaplar.
 */
public class CustomerSectionNav extends JPanel {

    private final ButtonGroup group = new ButtonGroup();
    private final Map<String, Item> items = new LinkedHashMap<>();
    private final Consumer<String> onSelect;

    public CustomerSectionNav(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        setLayout(new MigLayout("insets 8, wrap, gapy 2, fillx", "[grow, fill]", ""));
        putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");
    }

    /**
     * @param shortcut menüde ipucu olarak gösterilen kısayol (ör. "Ctrl+1"); kısayolun kendisi formda bağlanır.
     */
    public void addSection(String id, String title, String iconPath, String shortcut) {
        Item item = new Item(id, title, iconPath);
        item.setToolTipText(title + "  (" + shortcut + ")");
        group.add(item);
        items.put(id, item);
        add(item, "h 38!");
    }

    /** Sayı {@code null} ise rozet gizlenir (Genel Bakış gibi sayılmayan bölümler). */
    public void setCount(String id, Integer count) {
        Item item = items.get(id);
        if (item == null) return;
        item.countLabel.setText(count == null ? "" : String.valueOf(count));
        item.countLabel.setVisible(count != null);
    }

    public void select(String id) {
        Item item = items.get(id);
        if (item != null && !item.isSelected()) item.setSelected(true);
    }

    public String idAt(int index) {
        return index >= 0 && index < items.size() ? items.keySet().toArray(new String[0])[index] : null;
    }

    private class Item extends JToggleButton {
        private final JLabel iconLabel;
        private final JLabel titleLabel;
        private final JLabel countLabel = new JLabel();
        private final String iconPath;

        Item(String id, String title, String iconPath) {
            this.iconPath = iconPath;
            setLayout(new MigLayout("insets 0 10 0 10, gap 10, filly", "[][grow][]", "[center]"));
            putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            putClientProperty(FlatClientProperties.STYLE,
                    "arc: 10; focusWidth: 0; toolbar.selectedBackground: $TableHeader.hoverBackground;");
            setFocusable(false);

            iconLabel = new JLabel();
            titleLabel = new JLabel(title);
            countLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");

            add(iconLabel);
            add(titleLabel, "wmin 0");
            add(countLabel);

            addItemListener(e -> {
                applySelectionStyle();
                if (isSelected()) onSelect.accept(id);
            });
            applySelectionStyle();
        }

        /**
         * Seçili bölümde ikon vurgu renginde, yazı kalın. Yazının kendisi vurgu rengine boyanmaz:
         * koyu temada vurgu mavisi seçili arka planın üstünde okunmuyordu.
         */
        private void applySelectionStyle() {
            boolean selected = isSelected();
            iconLabel.setIcon(selected
                    ? new Ikon(iconPath, 0.85f, "Component.accentColor")
                    : new Ikon(iconPath, 0.85f, "Label.disabledForeground"));
            titleLabel.putClientProperty(FlatClientProperties.STYLE, selected ? "font: bold" : "font: plain");
        }
    }
}
