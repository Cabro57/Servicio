package tr.cabro.servicio.application.themes;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.ui.FlatComboBoxUI;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;

/**
 * Tüm açılır kutuların ortak arayüzü: açılan liste, en uzun öğe tam görünecek kadar genişler.
 * <p>
 * FlatLaf listeyi kutunun "görüntü boyutuna" göre genişletir; o da prototip değer verilmişse prototipten
 * ölçülür. Dar tutulan (prototipli ya da yerleşimde daralan) kutularda liste kutu genişliğinde açılıp
 * metni kesiyordu. Genişlik {@code computePopupBounds} içinde verildiği için iç kaydırma alanı da aynı
 * genişliğe ayarlanır ve ekrandan taşma durumunda liste FlatLaf'ın kendi kuralıyla sola kaydırılır.
 * Kutunun kendi genişliği değişmez.
 */
public class FitComboBoxUI extends FlatComboBoxUI {

    /** Çok uzun listelerde (ör. binlerce model) açılış gecikmesin diye ölçülen öğe sayısı sınırı. */
    private static final int MAX_MEASURED = 500;

    public static ComponentUI createUI(JComponent c) {
        return new FitComboBoxUI();
    }

    /** Arayüzü yalnızca FlatLaf temalarında kaydeder; başka bir görünüme geçilirse kaydı kaldırır. */
    public static void install() {
        UIManager.put("ComboBoxUI", UIManager.getLookAndFeel() instanceof FlatLaf ? FitComboBoxUI.class.getName() : null);
    }

    @Override
    protected ComboPopup createPopup() {
        return new FlatComboPopup(comboBox) {
            @Override
            protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
                return super.computePopupBounds(px, py, Math.max(pw, contentWidth(list)), ph);
            }
        };
    }

    /** En uzun öğenin çizilmiş genişliği + kaydırma çubuğu ve kenar payı; ekran genişliğini aşmaz. */
    private int contentWidth(JList<Object> list) {
        @SuppressWarnings("unchecked")
        ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) comboBox.getRenderer();
        if (renderer == null) return 0;
        int max = 0;
        int count = Math.min(comboBox.getItemCount(), MAX_MEASURED);
        for (int i = 0; i < count; i++) {
            Component c = renderer.getListCellRendererComponent(list, comboBox.getItemAt(i), i, false, false);
            max = Math.max(max, c.getPreferredSize().width);
        }
        if (max == 0) return 0;
        int width = max + UIScale.scale(24);
        GraphicsConfiguration gc = comboBox.getGraphicsConfiguration();
        return gc != null ? Math.min(width, gc.getBounds().width - UIScale.scale(40)) : width;
    }
}
