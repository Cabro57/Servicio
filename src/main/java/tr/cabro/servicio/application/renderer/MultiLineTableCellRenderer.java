package tr.cabro.servicio.application.renderer;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.function.Function;

/**
 * @param <T> Hücreye gelen verinin tipi (Örn: Customer, Service vb.)
 */
public class MultiLineTableCellRenderer<T> extends JPanel implements TableCellRenderer {

    private final JLabel topLabel;
    private final JLabel bottomLabel;
    private final Function<T, String> topTextExtractor;
    private final Function<T, String> bottomTextExtractor;

    public MultiLineTableCellRenderer(Function<T, String> topTextExtractor, Function<T, String> bottomTextExtractor) {
        this.topTextExtractor = topTextExtractor;
        this.bottomTextExtractor = bottomTextExtractor;

        // ÇÖZÜM BURADA:
        // "gapy 0" ve "[]0[]" komutları iki satır arasındaki tüm varsayılan boşlukları ezer ve 0 yapar.
        // "insets 0 5 0 5" alt ve üstteki iç boşlukları sıfırlar.
        // "aligny center" ile bu yapışık iki satır hücrenin dikey ekseninde tam ortaya hizalanır.
        setLayout(new MigLayout("insets 0 5 0 5, gapy 0, fill, aligny center", "[grow]", "[]0[]"));
        setOpaque(true);

        topLabel = new JLabel();
        topLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +1"); // Görseldeki gibi biraz daha belirgin olması için +1 yapabilirsiniz

        bottomLabel = new JLabel();
        bottomLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1");

        // "wrap" komutu bir alt satıra geçmesini sağlar, cell kullanmaktan daha temizdir.
        add(topLabel, "growx, wrap");
        add(bottomLabel, "growx");
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

        // Satır seçimi koruması
        if (isSelected) {
            setBackground(table.getSelectionBackground());
            topLabel.setForeground(table.getSelectionForeground());
            bottomLabel.setForeground(table.getSelectionForeground()); // Seçiliyken alt metin de beyaz olsun
        } else {
            setBackground(table.getBackground());
            topLabel.setForeground(table.getForeground());
            // Seçili değilken alt metin FlatLaf'ın pasif (gri) rengini alsın
            bottomLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        }

        if (value != null) {
            try {
                @SuppressWarnings("unchecked")
                T item = (T) value;

                // Java 8 Function'ları ile nesneden ilgili metinleri çek
                String topText = topTextExtractor.apply(item);
                String bottomText = bottomTextExtractor.apply(item);

                topLabel.setText(topText != null ? topText : "");
                bottomLabel.setText(bottomText != null ? bottomText : "");
            } catch (ClassCastException e) {
                // Tip uyuşmazlığı olursa sistemi çökertme, düz metin bas
                topLabel.setText(value.toString());
                bottomLabel.setText("");
            }
        } else {
            topLabel.setText("");
            bottomLabel.setText("");
        }

        return this;
    }
}