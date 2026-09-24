package tr.cabro.servicio.application.renderer;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.model.enums.BadgeColor;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.function.Function;

/**
 * Satırın kimliğini gösteren ilk hücre: solda durumun rozet renginde küçük bir nokta, yanında
 * kayıt numarası (üst) ve soluk ikinci satır (alt). Durum rengi taramayı kolaylaştırır; renk
 * anlamı {@link BadgeColor} ile aynıdır, böylece sağdaki durum rozetiyle çelişmez.
 *
 * @param <T> hücreye gelen kayıt tipi
 */
public class StatusDotCellRenderer<T> extends JPanel implements TableCellRenderer {

    private final Function<T, String> top;
    private final Function<T, String> bottom;
    private final Function<T, BadgeColor> dotColor;
    private final JLabel topLabel = new JLabel();
    private final JLabel bottomLabel = new JLabel();
    private Color dot = Color.GRAY;

    public StatusDotCellRenderer(Function<T, String> top, Function<T, String> bottom, Function<T, BadgeColor> dotColor) {
        this.top = top;
        this.bottom = bottom;
        this.dotColor = dotColor;
        setOpaque(true);
        setLayout(new GridBagLayout());
        topLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        bottomLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 4);
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.SOUTH;
        add(topLabel, gbc);
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.NORTH;
        add(bottomLabel, gbc);
    }

    /** Noktanın çapı + iki yanındaki boşluk kadar sol dolgu. */
    private int leftPad() {
        return UIScale.scale(14 + 8 + 10);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        setBackground(TableRendererSupport.rowBackground(table, row, isSelected));
        setBorder(BorderFactory.createEmptyBorder(0, leftPad(), 0, 0));
        if (value == null) {
            topLabel.setText("");
            bottomLabel.setText("");
            dot = null;
            return this;
        }
        try {
            @SuppressWarnings("unchecked")
            T item = (T) value;
            topLabel.setText(nz(top.apply(item)));
            bottomLabel.setText(nz(bottom.apply(item)));
            BadgeColor color = dotColor.apply(item);
            dot = color != null ? BadgePalette.foreground(color) : null;
        } catch (ClassCastException e) {
            topLabel.setText(String.valueOf(value));
            bottomLabel.setText("");
        }
        topLabel.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        bottomLabel.setForeground(isSelected ? table.getSelectionForeground()
                : UIManager.getColor("Label.disabledForeground"));
        return this;
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (dot == null) { g2.dispose(); return; }
        if (dot == null) { g2.dispose(); return; }
        int d = UIScale.scale(8);
        g2.setColor(dot);
        g2.fillOval(UIScale.scale(14), (getHeight() - d) / 2, d, d);
        g2.dispose();
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
