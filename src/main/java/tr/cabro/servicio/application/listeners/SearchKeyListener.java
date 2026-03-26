package tr.cabro.servicio.application.listeners;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

public class SearchKeyListener extends KeyAdapter {

    private final Consumer<String> searchAction;

    public SearchKeyListener(Consumer<String> searchAction) {
        this.searchAction = searchAction;
    }


    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            Object source = e.getSource();

            if (source instanceof JTextField) {
                JTextField textField = (JTextField) source;
                String searchText = textField.getText().trim();

                searchAction.accept(searchText);
            }
        }


    }
}
