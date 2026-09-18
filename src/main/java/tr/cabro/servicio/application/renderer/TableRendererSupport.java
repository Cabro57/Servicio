package tr.cabro.servicio.application.renderer;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * Özel hücre renderer'ları için ortak arka plan hesabı.
 * <p>
 * Zebra (bir açık bir koyu) satır rengini JDK'nin {@code DefaultTableCellRenderer}'ı
 * uygular: tablonun arka planı temadan geliyorsa (yani {@link UIResource} ise) tek
 * numaralı satırlarda {@code Table.alternateRowColor} kullanılır. Kendi renderer'ımız
 * arka planı doğrudan {@code table.getBackground()} ile ayarlarsa bu zebra deseni o
 * sütunda bozulur; bu yardımcı aynı kuralı tek yerde tekrarlar.
 */
public final class TableRendererSupport {

    private TableRendererSupport() {}

    /** Satırın (ve seçim durumunun) gerçek arka plan rengi. */
    public static Color rowBackground(JTable table, int row, boolean isSelected) {
        if (isSelected) {
            return table.getSelectionBackground();
        }

        Color background = table.getBackground();
        // Arka plan elle değiştirilmişse (UIResource değilse) zebra rengi uygulanmaz
        if (background == null || background instanceof UIResource) {
            Color alternate = UIManager.getColor("Table.alternateRowColor");
            if (alternate != null && row % 2 != 0) {
                return alternate;
            }
        }
        return background;
    }
}
