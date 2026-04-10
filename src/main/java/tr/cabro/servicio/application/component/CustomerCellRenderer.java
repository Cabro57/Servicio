package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.model.Customer;

import javax.swing.*;
import java.awt.*;

public class CustomerCellRenderer extends DefaultListCellRenderer {

    JPanel content;
    JLabel topLabel;
    JLabel bottomLabel;

    public CustomerCellRenderer() {
        content = new JPanel();
        content.setLayout(new MigLayout("insets 2 5 2 5, fill, aligny center", "[grow]", "[]1[]"));
        content.putClientProperty(FlatClientProperties.STYLE, "arc:20; background:$List.background;");

        setOpaque(true);

        topLabel = new JLabel();
        topLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +0");

        bottomLabel = new JLabel();
        bottomLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1");

        content.add(topLabel, "cell 0 0, growx");
        content.add(bottomLabel, "cell 0 1, growx");
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        // Varsayılan hücre çizimini al
        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof Customer) {
            Customer customer = (Customer) value;

            String name = customer.getName() != null ? customer.toString() : "İsimsiz";
            String phone = customer.getPhoneNumber1() != null ? customer.getPhoneNumber1() : "Telefon Yok";

            // Seçili olma durumuna göre metin rengini ayarla
            String textColor = isSelected ? "white" : "#666666";

            topLabel.setText(name);
            bottomLabel.setText(phone);

            // Hücrelere biraz boşluk verelim
//            label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

            return content;
        } else {
            topLabel.setText("Hata");
        }

        return label;
    }
}