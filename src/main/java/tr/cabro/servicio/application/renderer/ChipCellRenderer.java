package tr.cabro.servicio.application.renderer;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.model.enums.BadgeColor;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.function.Function;

/**
 * Tek çipli hücre: durum/stok/ödeme gibi kısa bir etiketi renkli bir çip olarak gösterir (yalnızca
 * çip, altında yazı yok). Çip metni ve rengi kayıttan üretilir; boş dönerse hücre boş kalır.
 *
 * @param <T> satırın kaydı
 */
public class ChipCellRenderer<T> extends JPanel implements TableCellRenderer {

    private final Function<T, RowParts.Badge> chip;
    private final RowParts.BadgeChip view = new RowParts.BadgeChip(24, 1f);

    public ChipCellRenderer(Function<T, RowParts.Badge> chip) {
        this.chip = chip;
        setOpaque(true);
        setLayout(new MigLayout("insets 0 8 0 8, gap 0", "[]", "push[]push"));
        add(view);
    }

    /** Kısayol: metin + renk çifti. */
    public static RowParts.Badge badge(String text, BadgeColor color) {
        return new RowParts.Badge(text, color);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        setBackground(TableRendererSupport.rowBackground(table, row, isSelected));
        RowParts.Badge b = null;
        if (value != null) {
            try {
                @SuppressWarnings("unchecked")
                T item = (T) value;
                b = chip.apply(item);
            } catch (ClassCastException ignored) {
                // farklı tipte değer: çip gösterilmez
            }
        }
        view.set(b);
        return this;
    }
}
