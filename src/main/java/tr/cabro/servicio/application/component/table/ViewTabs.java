package tr.cabro.servicio.application.component.table;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Liste sayfasının sayılı görünüm sekmeleri: "Tümü 69 · Tamirde 15 · Hazır 10". Her sekme
 * önceden tanımlı bir süzgeçtir; sayı, o süzgeçle (ve o anki aramayla) eşleşen kayıt adedidir.
 * Seçili sekme vurgu dolgusu alır — ekrandaki tek ikinci vurgu dolgusu budur (bkz. DESIGN.md).
 */
public class ViewTabs extends JPanel {

    private final Map<String, JToggleButton> tabs = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final ButtonGroup group = new ButtonGroup();
    private Consumer<String> onChange;
    private String selected;
    private boolean silent;

    public ViewTabs() {
        super(new MigLayout("insets 0, gap 2", "", "[center]"));
        setOpaque(false);
    }

    public void addView(String key, String label) {
        JToggleButton b = new JToggleButton(label);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 4,10,4,10; focusWidth: 0;"
                + " toolbar.selectedBackground: $Component.accentColor;"
                + " toolbar.selectedForeground: $Servicio.onAccentForeground");
        b.addItemListener(e -> {
            if (b.isSelected()) {
                selected = key;
                if (!silent && onChange != null) onChange.accept(key);
            }
        });
        group.add(b);
        tabs.put(key, b);
        labels.put(key, label);
        add(b);
        if (tabs.size() == 1) {
            silent = true;
            b.setSelected(true);
            silent = false;
        }
    }

    public boolean isEmpty() {
        return tabs.isEmpty();
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    public String getSelected() {
        return selected;
    }

    /** Sekmeyi seçer; {@code notify} false ise dinleyici tetiklenmez (programatik eşitleme). */
    public void select(String key, boolean notify) {
        JToggleButton b = tabs.get(key);
        if (b == null || b.isSelected()) return;
        silent = !notify;
        b.setSelected(true);
        silent = false;
    }

    /** Sekmenin yanındaki sayıyı günceller; {@code null} sayıyı gizler. */
    public void setCount(String key, Long count) {
        JToggleButton b = tabs.get(key);
        if (b == null) return;
        String label = labels.get(key);
        // Sayı etikete HTML ile eklenir: seçili sekmede vurgu zemininde okunabilir kalsın diye renk
        // verilmez, yalnızca inceltilir.
        b.setText(count == null ? label : "<html>" + escape(label) + "&nbsp;&nbsp;<span style='font-weight:normal'>"
                + count + "</span></html>");
        b.getAccessibleContext().setAccessibleName(count == null ? label : label + ", " + count + " kayıt");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
