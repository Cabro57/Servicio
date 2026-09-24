package tr.cabro.servicio.application.renderer;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Sağa yaslı para hücresi: üstte tutar (anlamına göre renkli, kalın), altında soluk açıklama
 * ("₺1.250 kalan") ve ödeme çipi (Ödendi / Kısmi / Ödenmedi …).
 *
 * @param <T> satırın kaydı
 */
public class AmountChipCellRenderer<T> extends JPanel implements TableCellRenderer {

    private Function<T, BigDecimal> amount = t -> null;
    private RowParts.Money mode = RowParts.Money.NEUTRAL;
    private String zeroText = "—";
    private Function<T, String> caption = t -> null;
    private Function<T, String> captionColor = t -> null;
    private Function<T, RowParts.Badge> chip = t -> null;

    private final JLabel amountL = new JLabel();
    private final JLabel captionL = new JLabel();
    private final RowParts.BadgeChip chipView = new RowParts.BadgeChip(20, 2f);

    public AmountChipCellRenderer() {
        setOpaque(true);
        setLayout(new MigLayout("insets 0 8 0 14, gap 6 3, hidemode 3", "[grow, right]", "push[]3[]push"));
        amountL.setHorizontalAlignment(SwingConstants.TRAILING);
        JPanel bottom = new JPanel(new MigLayout("insets 0, gap 6, hidemode 3", "[][]", "[center]"));
        bottom.setOpaque(false);
        bottom.add(captionL);
        bottom.add(chipView);
        add(amountL, "growx, wrap");
        add(bottom, "align right");
    }

    public AmountChipCellRenderer<T> amount(Function<T, BigDecimal> f, RowParts.Money mode) {
        this.amount = f;
        this.mode = mode;
        return this;
    }

    public AmountChipCellRenderer<T> zeroText(String text) { this.zeroText = text; return this; }
    public AmountChipCellRenderer<T> caption(Function<T, String> f) { this.caption = f; return this; }
    public AmountChipCellRenderer<T> captionColor(Function<T, String> f) { this.captionColor = f; return this; }
    public AmountChipCellRenderer<T> chip(Function<T, RowParts.Badge> f) { this.chip = f; return this; }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        setBackground(TableRendererSupport.rowBackground(table, row, isSelected));
        Color fg = table.getForeground();
        Color muted = UIManager.getColor("Label.disabledForeground");
        Font base = UIManager.getFont("Label.font");
        captionL.setFont(base.deriveFont(Font.PLAIN, base.getSize2D() - 1f));

        @SuppressWarnings("unchecked")
        T item = (T) value;
        if (item == null) {
            amountL.setText("");
            captionL.setText("");
            chipView.set(null);
            return this;
        }

        BigDecimal v = amount.apply(item);
        if (v == null) {
            amountL.setText("");
        } else {
            Color color = fg;
            String text = Format.formatPrice(v);
            boolean quiet = false;
            switch (mode) {
                case OWED:
                    if (v.signum() > 0) color = UIManager.getColor("Servicio.warningColor");
                    else { text = zeroText; color = muted; quiet = true; }
                    break;
                case BALANCE:
                    if (v.signum() > 0) color = UIManager.getColor("Servicio.warningColor");
                    else if (v.signum() < 0) color = UIManager.getColor("Servicio.dangerColor");
                    else { text = zeroText; color = muted; quiet = true; }
                    break;
                case NEGATIVE:
                    if (v.signum() < 0) color = UIManager.getColor("Servicio.dangerColor");
                    break;
                default:
                    break;
            }
            amountL.setText(text);
            amountL.setForeground(color);
            amountL.setFont(quiet ? base.deriveFont(Font.PLAIN, base.getSize2D() - 1f) : base.deriveFont(Font.BOLD));
        }

        String cap = caption.apply(item);
        captionL.setText(cap != null ? cap : "");
        String key = captionColor.apply(item);
        Color capColor = key != null ? UIManager.getColor(key) : null;
        captionL.setForeground(capColor != null ? capColor : muted);
        chipView.set(chip.apply(item));
        return this;
    }
}
