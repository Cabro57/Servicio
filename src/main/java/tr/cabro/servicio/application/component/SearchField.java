package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import tr.cabro.servicio.application.util.SVGIconUIColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SearchField extends JTextField {

    private final JButton enter;

    public SearchField() {
        this.enter = new JButton();
        init();
    }

    private void init() {
        putClientProperty("JTextField.placeholderText", "Müşteri Ara... (Enter tuşuna basın)");
        putClientProperty("JTextField.padding", new Insets(5, 5, 5, 5));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "searchField");
        putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        enter.setIcon(new SVGIconUIColor("icons/enter.svg", 0.04f, "TextField.foreground"));
        enter.setFocusable(false);

        JToolBar toolBar = new JToolBar();
        toolBar.setMargin(new Insets(5, 0, 5, 5));
        toolBar.add(enter);

        putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, toolBar);
    }

    @Override
    public void addActionListener(ActionListener l) {
        super.addActionListener(l);

        enter.addActionListener(e ->
                l.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getText()))
        );
    }
}
