package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;

/**
 * Bir {@link QuickAction}'ı ikon, etiket ve klavye kısayoluyla birlikte gösteren düğme.
 * Birincil varyant accent zemindedir; ekranda yalnızca bir tane olmalı (ana sayfada "Yeni Servis").
 */
public class QuickActionButton extends JButton {

    private final JLabel key;

    public QuickActionButton(QuickAction action, boolean primary) {
        setLayout(new MigLayout("insets 0, fill, gap 8, hidemode 3", "[]push[]", "[center]"));
        setToolTipText(action.getDescription() + " (" + action.getShortcutText() + ")");
        getAccessibleContext().setAccessibleName(action.getLabel());
        addActionListener(e -> action.run());

        String fg = primary ? "Servicio.onAccentForeground" : "Label.foreground";
        JLabel label = new JLabel(action.getLabel(), new Ikon(action.getIconPath(), 16, fg), SwingConstants.LEADING);
        label.setIconTextGap(8);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $" + fg);

        key = new JLabel(action.getShortcutText());
        key.putClientProperty(FlatClientProperties.STYLE, primary
                ? "font: -2; foreground: fade($Servicio.onAccentForeground,85%); background: fade($Servicio.onAccentForeground,12%);"
                  + " border: 1,5,1,5,fade($Servicio.onAccentForeground,40%),1,6"
                : "font: -2; foreground: $Label.disabledForeground; border: 1,5,1,5,$Component.borderColor,1,6");

        add(label);
        add(key);

        putClientProperty(FlatClientProperties.STYLE, primary
                ? "arc: 12; margin: 9,14,9,12; borderWidth: 0; focusWidth: 0; innerFocusWidth: 1;"
                  + " background: $Component.accentColor; hoverBackground: darken($Component.accentColor,6%);"
                  + " pressedBackground: darken($Component.accentColor,12%)"
                : "arc: 12; margin: 9,14,9,12; focusWidth: 0; innerFocusWidth: 1");
    }

    /** Dar ekranda (1366 px) şerit sığsın diye kısayol etiketi gizlenir; kısayol ipucu tooltip'te kalır. */
    public void setCompact(boolean compact) {
        if (key.isVisible() == !compact) return;
        key.setVisible(!compact);
        revalidate();
    }

    // BasicButtonUI tercih edilen boyutu metin/ikon'dan hesaplar; bu düğmenin içeriği alt etiketlerde
    // olduğu için boyut yerleşimden (MigLayout, kenar boşlukları dahil) alınır.
    @Override
    public java.awt.Dimension getPreferredSize() {
        return getLayout().preferredLayoutSize(this);
    }

    @Override
    public java.awt.Dimension getMinimumSize() {
        return getLayout().minimumLayoutSize(this);
    }
}
