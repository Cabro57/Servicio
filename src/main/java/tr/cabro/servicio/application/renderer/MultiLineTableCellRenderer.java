package tr.cabro.servicio.application.renderer;

import com.formdev.flatlaf.FlatClientProperties;

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

    // Yeni eklenen renk belirleyici fonksiyonlar
    private Function<T, Color> topColorExtractor = null;
    private Function<T, Color> bottomColorExtractor = null;

    // Mevcut yapıyı bozmamak için eski constructor (Renkler varsayılan kalır)
    public MultiLineTableCellRenderer(Function<T, String> topTextExtractor, Function<T, String> bottomTextExtractor) {
        this(topTextExtractor, bottomTextExtractor, null, null);
    }

    // Yeni ve gelişmiş constructor (Özel renk koşulları eklenebilir)
    public MultiLineTableCellRenderer(Function<T, String> topTextExtractor,
                                      Function<T, String> bottomTextExtractor,
                                      Function<T, Color> topColorExtractor,
                                      Function<T, Color> bottomColorExtractor) {
        this.topTextExtractor = topTextExtractor;
        this.bottomTextExtractor = bottomTextExtractor;
        this.topColorExtractor = topColorExtractor;
        this.bottomColorExtractor = bottomColorExtractor;

        setLayout(new GridBagLayout());
        setOpaque(true);
        setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        topLabel = new JLabel();
        topLabel.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
        topLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        bottomLabel = new JLabel();
        bottomLabel.putClientProperty(FlatClientProperties.STYLE, "font: $medium.font");
        bottomLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 5, 0, 5);

        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.SOUTH;
        add(topLabel, gbc);

        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.NORTH;
        add(bottomLabel, gbc);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value != null) {
            try {
                @SuppressWarnings("unchecked")
                T item = (T) value;

                // Metinleri ayarla
                String topText = topTextExtractor.apply(item);
                String bottomText = bottomTextExtractor.apply(item);
                topLabel.setText(topText != null ? topText : "");
                bottomLabel.setText(bottomText != null ? bottomText : "");

                // Özel renkleri kontrol et
                Color topCustomColor = topColorExtractor != null ? topColorExtractor.apply(item) : null;
                Color bottomCustomColor = bottomColorExtractor != null ? bottomColorExtractor.apply(item) : null;

                // Renk atamaları ve Seçili olma durumu
                if (isSelected) {
                    setBackground(table.getSelectionBackground());
                    // Seçiliyken eğer özel renk varsa onu kullan, yoksa tablonun varsayılan seçili rengini (genelde beyaz) kullan
                    topLabel.setForeground(topCustomColor != null ? topCustomColor : table.getSelectionForeground());
                    bottomLabel.setForeground(bottomCustomColor != null ? bottomCustomColor : table.getSelectionForeground());
                } else {
                    setBackground(table.getBackground());
                    topLabel.setForeground(topCustomColor != null ? topCustomColor : table.getForeground());
                    // Özel renk yoksa varsayılan pasif griyi kullan
                    bottomLabel.setForeground(bottomCustomColor != null ? bottomCustomColor : UIManager.getColor("Label.disabledForeground"));
                }

            } catch (ClassCastException e) {
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