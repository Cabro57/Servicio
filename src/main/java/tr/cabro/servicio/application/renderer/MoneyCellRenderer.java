package tr.cabro.servicio.application.renderer;

import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;

/**
 * Liste tablolarındaki para hücresi: sağa yaslı, kalın, rengi anlamından gelir (DESIGN.md "Sign Rule").
 * <ul>
 *   <li>{@link Mode#NEUTRAL} — tutar kendi başına bir anlam taşımaz (fiyat, toplam): normal renk.</li>
 *   <li>{@link Mode#OWED} — dükkâna ödenecek tutar (kalan ücret, bakiye): &gt;0 uyarı rengi, 0 soluk "—".</li>
 *   <li>{@link Mode#SIGN} — işaretli tutar (net kâr): artı başarı, eksi tehlike, sıfır normal.</li>
 *   <li>{@link Mode#BALANCE} — müşteri bakiyesi: artı (bize borçlu) uyarı, eksi (biz borçluyuz) tehlike, sıfır soluk.</li>
 *   <li>{@link Mode#NEGATIVE} — zaten sayılmış para (fiş toplamı): artı nötr, yalnızca eksi (iade) tehlike.</li>
 * </ul>
 */
public class MoneyCellRenderer extends DefaultTableCellRenderer {

    public enum Mode { NEUTRAL, OWED, SIGN, NEGATIVE, BALANCE }

    private final Mode mode;
    private final String zeroText;

    public MoneyCellRenderer(Mode mode) {
        this(mode, "—");
    }

    /** {@code zeroText}: OWED modunda sıfır tutar yerine yazılacak metin (ör. "Ödendi"). */
    public MoneyCellRenderer(Mode mode, String zeroText) {
        this.mode = mode;
        this.zeroText = zeroText;
        setHorizontalAlignment(SwingConstants.TRAILING);
        setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 12));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
        setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 12));
        // Renk ve yazı her çağrıda doğrudan atanır: renderer satırdan satıra yeniden kullanıldığı için
        // FlatLaf stil dizgisi (yalnızca değişince uygulanır) burada güvenilir değil.
        Font base = table.getFont();
        BigDecimal amount = toBigDecimal(value);
        if (amount == null) {
            setText("");
            return this;
        }
        Color color = table.getForeground();
        Font font = base.deriveFont(Font.BOLD);
        String text = Format.formatPrice(amount);
        switch (mode) {
            case OWED:
                if (amount.signum() > 0) color = UIManager.getColor("Servicio.warningColor");
                else {
                    text = zeroText;
                    color = UIManager.getColor("Label.disabledForeground");
                    font = base.deriveFont(base.getSize2D() - 1f);
                }
                break;
            case SIGN:
                if (amount.signum() > 0) color = UIManager.getColor("Servicio.successColor");
                else if (amount.signum() < 0) color = UIManager.getColor("Servicio.dangerColor");
                break;
            case BALANCE:
                if (amount.signum() > 0) color = UIManager.getColor("Servicio.warningColor");
                else if (amount.signum() < 0) color = UIManager.getColor("Servicio.dangerColor");
                else color = UIManager.getColor("Label.disabledForeground");
                break;
            case NEGATIVE:
                if (amount.signum() < 0) color = UIManager.getColor("Servicio.dangerColor");
                break;
            default:
                break;
        }
        setText(text);
        setForeground(color);
        setFont(font);
        return this;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return null;
    }
}
