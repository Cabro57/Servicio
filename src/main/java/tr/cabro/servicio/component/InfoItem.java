package tr.cabro.servicio.component;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.Color;

public class InfoItem extends JPanel {

    private final JLabel valueLabel;

    // Key'i tutmak zorunlu değil ama isterseniz ekleyebilirsiniz.
    // private final String key;

    public InfoItem(String labelText, String initialValue) {
        // this.key = key; // Eğer key'i saklamak isterseniz
        setLayout(new MigLayout("wrap 1, insets 0 0 15 0"));
        setOpaque(false);

        // Başlık (Label)
        JLabel labelLabel = new JLabel(labelText);
        labelLabel.setForeground(Color.gray);
        labelLabel.putClientProperty("FlatLaf.style", "font: -2");
        add(labelLabel);

        // Değer (Value)
        valueLabel = new JLabel(initialValue);
        valueLabel.putClientProperty("FlatLaf.style", "font: +1");
        add(valueLabel, "gapy 2");
    }

    /**
     * Paneldeki değeri (valueLabel'ın metnini) günceller.
     * @param value Yeni değer metni
     */
    public void setValue(String value) {
        valueLabel.setText(value);
    }

    /**
     * Paneldeki değeri (valueLabel'ın metnini) döndürür.
     * @return Mevcut değer metni
     */
    public String getValue() {
        return valueLabel.getText();
    }
}
