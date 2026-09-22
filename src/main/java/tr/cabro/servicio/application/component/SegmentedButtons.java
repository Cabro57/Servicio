package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Birbirini dışlayan birkaç seçenek için yan yana düğme grubu (segmented control).
 * <p>
 * Az sayıda seçeneği açılır liste yerine tek bakışta gösterir; seçili düğme vurgu rengiyle
 * tonlanır. Müşteri tipi (Bireysel/Kurumsal) ve cihaz kilit türü gibi 2-4 seçenekli alanlarda
 * kullanılır.
 */
public class SegmentedButtons<T> extends JPanel {

    private final Map<T, JToggleButton> buttons = new LinkedHashMap<>();
    private final ButtonGroup group = new ButtonGroup();
    private Consumer<T> onChange;
    private T selected;

    public SegmentedButtons() {
        super(new MigLayout("insets 3, gap 2", "", "[]"));
        putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: fade($Label.foreground, 7%)");
    }

    public SegmentedButtons<T> add(T value, String text, String iconPath) {
        JToggleButton b = new JToggleButton(text, iconPath != null ? new Ikon(iconPath, 0.85f) : null);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; margin: 5,14,5,14; iconTextGap: 6; "
                        + "toolbar.selectedBackground: fade($Component.accentColor, 22%); "
                        + "toolbar.hoverBackground: fade($Label.foreground, 6%)");
        b.addActionListener(e -> {
            if (value == selected) return;
            selected = value;
            if (onChange != null) onChange.accept(value);
        });
        group.add(b);
        buttons.put(value, b);
        super.add(b);
        if (selected == null) setSelected(value);
        return this;
    }

    /** Programatik seçim; {@code onChange} tetiklenmez (çağıran zaten durumu biliyor). */
    public void setSelected(T value) {
        JToggleButton b = buttons.get(value);
        if (b == null) return;
        b.setSelected(true);
        selected = value;
    }

    public T getSelected() {
        return selected;
    }

    /** Kullanıcı seçimi değiştirdiğinde çağrılır. */
    public void setOnChange(Consumer<T> onChange) {
        this.onChange = onChange;
    }

    /** İlk düğmeye odak verir (klavyeyle forma girişte). */
    public void focusSelected() {
        JToggleButton b = buttons.get(selected);
        if (b != null) b.requestFocusInWindow();
    }
}
