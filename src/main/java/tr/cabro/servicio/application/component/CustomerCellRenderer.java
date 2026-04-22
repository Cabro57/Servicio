package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.model.Customer;

import javax.swing.*;
import java.awt.*;

public class CustomerCellRenderer extends DefaultListCellRenderer {

    private JPanel content;
    private JLabel topLabel;
    private JLabel bottomLabel;

    public CustomerCellRenderer() {
        content = new JPanel();
        // insets ile iç boşlukları (padding) artırdık, gapy ile isim ve telefon arasını hafif açtık
        content.setLayout(new MigLayout("insets 8 12 8 12, fill, aligny center", "[grow]", "[]2[]"));

        // Kenarları hafif yuvarlak yapıyoruz
        content.putClientProperty(FlatClientProperties.STYLE, "arc: 10;");

        topLabel = new JLabel();
        topLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +1"); // İsmi biraz daha büyüttük

        bottomLabel = new JLabel();
        bottomLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1");

        content.add(topLabel, "cell 0 0, growx");
        content.add(bottomLabel, "cell 0 1, growx");
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value instanceof Customer) {
            Customer customer = (Customer) value;

            String name = customer.getFirstName() != null ? customer.getFullName() : "İsimsiz";
            String phone = customer.getPhoneNumber1() != null ? customer.getPhoneNumber1() : "Telefon Yok";

            topLabel.setText(name);
            bottomLabel.setText(phone);

            // SEÇİLİ OLMA DURUMUNA GÖRE RENKLENDİRME (FlatLaf tema renklerini kullanır)
            if (isSelected) {
                content.setBackground(UIManager.getColor("List.selectionBackground"));
                topLabel.setForeground(UIManager.getColor("List.selectionForeground"));
                bottomLabel.setForeground(UIManager.getColor("List.selectionForeground"));
            } else {
                content.setBackground(UIManager.getColor("List.background"));
                topLabel.setForeground(UIManager.getColor("List.foreground"));
                // Alt metni (telefonu) normal durumda biraz daha soluk (gri) gösteririz
                bottomLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            }

            // Elemanların arasına ince bir ayırıcı çizgi (Border) çekmek için:
            // Sadece alt tarafa 1px'lik tema renginde bir çizgi ekler
            content.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));

            return content;
        }

        return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }
}