package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * OTP tarzı kod girişi: her hane için ayrı bir kutu.
 * Rakam girilince otomatik sonraki kutuya geçer, backspace ile geri döner,
 * yapıştırılan (Ctrl+V) çok haneli metin kutulara dağıtılır.
 * Tüm haneler dolunca {@code onComplete} tetiklenir.
 */
public class OtpField extends JPanel {

    private final JPasswordField[] cells;

    /** Programatik güncellemede (dağıtma/temizleme) filtrenin devreye girmemesi için bayrak. */
    private boolean updating;

    private Runnable onComplete;
    private Runnable onSubmit;

    public OtpField(int length) {
        this(length, true);
    }

    /**
     * @param length hane sayısı
     * @param masked {@code true} ise girilen rakam nokta ile gizlenir
     */
    public OtpField(int length, boolean masked) {
        // "al center center": kutu ızgarası, panel genişletildiğinde bile ortada kalır
        super(new MigLayout("insets 0, gap 6, al center center", "", "[]"));
        putClientProperty(FlatClientProperties.STYLE, "background:null;");

        cells = new JPasswordField[length];
        for (int i = 0; i < length; i++) {
            cells[i] = createCell(i, masked);
            add(cells[i], "w 42!, h 48!");
        }
    }

    private JPasswordField createCell(int index, boolean masked) {
        JPasswordField cell = new JPasswordField(1);
        cell.setHorizontalAlignment(SwingConstants.CENTER);
        if (!masked) {
            cell.setEchoChar((char) 0);
        }
        cell.putClientProperty(FlatClientProperties.STYLE, "" +
                "arc:12;" +
                "margin:0,0,0,0;" +
                "font:bold +6;" +
                "showRevealButton:false;" +
                "showCapsLock:false;");

        ((AbstractDocument) cell.getDocument()).setDocumentFilter(new DigitFilter(index));

        // Enter: dışarıya gönderme sinyali
        cell.addActionListener(e -> {
            if (onSubmit != null) {
                onSubmit.run();
            }
        });

        cell.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(cell::selectAll);
            }
        });

        cell.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_BACK_SPACE -> {
                        // Kutu boşsa bir öncekine dön ve onu temizle
                        if (cell.getPassword().length == 0 && index > 0) {
                            setText(cells[index - 1], "");
                            focus(index - 1);
                            e.consume();
                        }
                    }
                    case KeyEvent.VK_LEFT -> {
                        if (index > 0) {
                            focus(index - 1);
                            e.consume();
                        }
                    }
                    case KeyEvent.VK_RIGHT -> {
                        if (index < cells.length - 1) {
                            focus(index + 1);
                            e.consume();
                        }
                    }
                    default -> {
                    }
                }
            }
        });
        return cell;
    }

    // --- GİRİŞ İŞLEME ---

    /** Girilen/yapıştırılan metni {@code startIndex}'ten itibaren kutulara dağıtır. */
    private void distribute(int startIndex, String digits) {
        int i = startIndex;
        updating = true;
        try {
            for (int c = 0; c < digits.length() && i < cells.length; c++, i++) {
                setText(cells[i], String.valueOf(digits.charAt(c)));
            }
        } finally {
            updating = false;
        }

        focus(Math.min(i, cells.length - 1));

        if (isComplete() && onComplete != null) {
            onComplete.run();
        }
    }

    private void setText(JPasswordField cell, String text) {
        boolean previous = updating;
        updating = true;
        try {
            cell.setText(text);
        } finally {
            updating = previous;
        }
    }

    private void focus(int index) {
        cells[index].requestFocusInWindow();
        cells[index].selectAll();
    }

    // --- DIŞ API ---

    public String getValue() {
        StringBuilder sb = new StringBuilder();
        for (JPasswordField cell : cells) {
            sb.append(new String(cell.getPassword()));
        }
        return sb.toString();
    }

    public boolean isComplete() {
        for (JPasswordField cell : cells) {
            if (cell.getPassword().length == 0) {
                return false;
            }
        }
        return true;
    }

    public int getLength() {
        return cells.length;
    }

    /** Tüm haneleri temizler ve ilk kutuya odaklanır. */
    public void clear() {
        updating = true;
        try {
            for (JPasswordField cell : cells) {
                cell.setText("");
            }
        } finally {
            updating = false;
        }
        focus(0);
    }

    /** Tüm haneler dolduğunda çalışacak eylem. */
    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    /** Enter'a basıldığında çalışacak eylem. */
    public void setOnSubmit(Runnable onSubmit) {
        this.onSubmit = onSubmit;
    }

    @Override
    public boolean requestFocusInWindow() {
        return cells[0].requestFocusInWindow();
    }

    @Override
    public void requestFocus() {
        cells[0].requestFocus();
    }

    /**
     * Sadece rakam kabul eder; çok haneli girişi (yapıştırma) kutulara dağıtır.
     * Doküman bildirimi sırasında başka dokümana yazmamak için dağıtım
     * {@link SwingUtilities#invokeLater(Runnable)} ile ertelenir.
     */
    private class DigitFilter extends DocumentFilter {

        private final int index;

        private DigitFilter(int index) {
            this.index = index;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (updating) {
                super.insertString(fb, offset, string, attr);
                return;
            }
            handle(string);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (updating) {
                super.replace(fb, offset, length, text, attrs);
                return;
            }
            // Boş metin = silme/temizleme işlemi, olduğu gibi geçir
            if (text == null || text.isEmpty()) {
                super.replace(fb, offset, length, text, attrs);
                return;
            }
            handle(text);
        }

        private void handle(String text) {
            if (text == null) return;
            String digits = text.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return;
            SwingUtilities.invokeLater(() -> distribute(index, digits));
        }
    }
}
