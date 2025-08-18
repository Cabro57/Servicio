package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;
import lombok.Setter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class FieldPopupEditor {

    private final JTextField targetField;
    private final JPopupMenu popupMenu;
    private final JTextArea textArea;

    @Getter
    private final JButton triggerButton;
    @Getter @Setter
    private String lineSeparator = "; ";

    public FieldPopupEditor(JTextField targetField) {
        this.targetField = targetField;
        FlatSVGIcon icon = new FlatSVGIcon("icon/resize-full.svg", 16, 16);
        Color newcolor = UIManager.getColor("MenuItem.foreground");
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> newcolor));
        this.triggerButton = new JButton(icon);
        this.popupMenu = new JPopupMenu();
        this.textArea = new JTextArea(5, 20);

        init();
    }

    public FieldPopupEditor(JTextField targetField, String lineSeparator) {
        this(targetField);
        this.lineSeparator = lineSeparator;
    }

    private void init() {
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(textArea);
        popupMenu.setLayout(new BorderLayout());
        popupMenu.add(scrollPane);

        triggerButton.addActionListener(e -> showPopup());

        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyAndClose();
            }
        });

        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    applyAndClose();
                }
            }
        });
    }

    private void showPopup() {
        String existingText = targetField.getText();
        // Field'daki ; işaretlerini textarea'ya geri çevir (isteğe bağlı)
        textArea.setText(existingText.replace(lineSeparator, "\n"));
        textArea.setCaretPosition(0);
        popupMenu.show(targetField, 0, 0);
        textArea.requestFocusInWindow();
    }

    private void applyAndClose() {
        String text = textArea.getText().trim().replace("\n", lineSeparator);
        targetField.setText(text);
        targetField.setToolTipText("<html>" + text.replace(lineSeparator, "<br>") + "</html>");
        popupMenu.setVisible(false);
    }

}
