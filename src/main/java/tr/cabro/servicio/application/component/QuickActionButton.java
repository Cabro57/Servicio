package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;

/**
 * Bir {@link QuickAction}'ı ikon ve etiketle gösteren düğme. Klavye kısayolu düğmede yazmaz
 * (ana sayfa sade kalsın diye kaldırıldı); tooltip'te ve Ayarlar &gt; Klavye kısayolları'nda görünür.
 * Birincil varyant accent zemindedir; ekranda yalnızca bir tane olmalı (ana sayfada "Yeni Servis").
 */
public class QuickActionButton extends JButton {

    public QuickActionButton(QuickAction action, boolean primary) {
        setLayout(new MigLayout("insets 0, fill, gap 8", "[]", "[center]"));
        setToolTipText(action.getDescription() + " (" + action.getShortcutText() + ")");
        getAccessibleContext().setAccessibleName(action.getLabel());
        addActionListener(e -> action.run());

        String fg = primary ? "Servicio.onAccentForeground" : "Label.foreground";
        JLabel label = new JLabel(action.getLabel(), new Ikon(action.getIconPath(), 16, fg), SwingConstants.LEADING);
        label.setIconTextGap(8);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $" + fg);

        add(label);

        putClientProperty(FlatClientProperties.STYLE, primary
                ? "arc: 12; margin: 9,14,9,12; borderWidth: 0; focusWidth: 0; innerFocusWidth: 1;"
                  + " background: $Component.accentColor; hoverBackground: darken($Component.accentColor,6%);"
                  + " pressedBackground: darken($Component.accentColor,12%)"
                : "arc: 12; margin: 9,14,9,12; focusWidth: 0; innerFocusWidth: 1");
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
