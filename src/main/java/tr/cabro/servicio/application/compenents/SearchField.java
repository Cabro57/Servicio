package tr.cabro.servicio.application.compenents;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;

public class SearchField extends JTextField {

    public SearchField() {
        init();
    }

    private void init() {
        putClientProperty("JTextField.placeholderText", "Müşteri Ara... (Enter tuşuna basın)");
        putClientProperty("JTextField.padding", new Insets(5, 5, 5, 5));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "searchField");



    }
}
