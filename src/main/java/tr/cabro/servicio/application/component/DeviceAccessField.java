package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.model.enums.DeviceAccessType;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Cihaz ekran kilidi erişim bilgisini (PIN / Şifre / Desen) toplayan bileşen.
 * <p>
 * Tür seçimine göre giriş alanı değişir:
 * <ul>
 *   <li>{@link DeviceAccessType#PIN} — sadece rakam kabul eden şifre alanı</li>
 *   <li>{@link DeviceAccessType#PASSWORD} — serbest metin şifre alanı</li>
 *   <li>{@link DeviceAccessType#PATTERN} — 3x3 desen ızgarası, tıklama sırasına göre dizi üretir</li>
 *   <li>{@link DeviceAccessType#NONE} — giriş alanı gizlenir</li>
 * </ul>
 * Bu bileşen hiçbir şeyi şifrelemez/saklamaz — sadece düz metin değeri toplar;
 * şifreleme {@code DeviceAccessCredentialService} tarafından yapılır.
 */
public class DeviceAccessField extends JPanel {

    private final JComboBox<DeviceAccessType> typeCombo;

    private final JPasswordField pinField;
    private final JPasswordField passwordField;
    private final PatternGridPanel patternGrid;

    public DeviceAccessField() {
        // hidemode 3: setVisible(false) yapılan bileşen düzende hiç yer kaplamaz —
        // CardLayout kullanmıyoruz çünkü o, gösterilmeyen kartlar için bile en büyük
        // kartın (desen ızgarası) boyutunu ayırıp altında boşluk bırakıyordu.
        setLayout(new MigLayout("insets 0, fillx, wrap 1, hidemode 3", "[fill,grow]"));

        typeCombo = new JComboBox<>(DeviceAccessType.values());
        add(typeCombo, "growx");

        pinField = new JPasswordField();
        pinField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton:true;");
        pinField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ekran PIN'i");
        applyDigitsOnlyFilter(pinField);
        add(pinField, "growx");

        passwordField = new JPasswordField();
        passwordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton:true;");
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ekran şifresi");
        add(passwordField, "growx");

        patternGrid = new PatternGridPanel();
        add(patternGrid, "growx");

        typeCombo.addActionListener(e -> showFieldFor((DeviceAccessType) typeCombo.getSelectedItem()));
        showFieldFor(DeviceAccessType.NONE);
    }

    private void showFieldFor(DeviceAccessType type) {
        if (type == null) type = DeviceAccessType.NONE;
        pinField.setVisible(type == DeviceAccessType.PIN);
        passwordField.setVisible(type == DeviceAccessType.PASSWORD);
        patternGrid.setVisible(type == DeviceAccessType.PATTERN);

        // Bu bileşen genelde bir modal içinde kullanılıyor; modalın kendisi de yeni
        // yüksekliğe göre yeniden boyutlansın diye üst hiyerarşiyi de geçersiz kılıyoruz.
        revalidate();
        repaint();
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private void applyDigitsOnlyFilter(JPasswordField field) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string != null && string.matches("\\d+")) super.insertString(fb, offset, string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text == null || text.isEmpty() || text.matches("\\d+")) super.replace(fb, offset, length, text, attrs);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Dışa açık API
    // -------------------------------------------------------------------------

    public DeviceAccessType getAccessType() {
        return (DeviceAccessType) typeCombo.getSelectedItem();
    }

    public void setAccessType(DeviceAccessType type) {
        typeCombo.setSelectedItem(type != null ? type : DeviceAccessType.NONE);
    }

    /** Seçili türe göre girilen düz metin değeri döner (PATTERN için "1-5-9" gibi bir dizi). */
    public String getValue() {
        DeviceAccessType type = getAccessType();
        if (type == null) return null;
        return switch (type) {
            case PIN -> new String(pinField.getPassword());
            case PASSWORD -> new String(passwordField.getPassword());
            case PATTERN -> patternGrid.getSequence();
            default -> null;
        };
    }

    public void setValue(DeviceAccessType type, String value) {
        setAccessType(type);
        if (value == null) value = "";
        if (type == DeviceAccessType.PIN) {
            pinField.setText(value);
        } else if (type == DeviceAccessType.PASSWORD) {
            passwordField.setText(value);
        } else if (type == DeviceAccessType.PATTERN) {
            patternGrid.setSequence(value);
        }
    }

    public void clear() {
        pinField.setText("");
        passwordField.setText("");
        patternGrid.setSequence("");
        setAccessType(DeviceAccessType.NONE);
    }

    /**
     * 3x3 desen kilidi ızgarası. Kullanıcı noktalara tıklama sırasına göre bir dizi
     * ("1-2-3-6-9" gibi 1-9 arası düğüm numaraları) oluşturur.
     */
    private static class PatternGridPanel extends JPanel {

        private final JToggleButton[] dots = new JToggleButton[9];
        private final List<Integer> sequence = new ArrayList<>();
        private final JLabel sequenceLabel = new JLabel(" ");

        PatternGridPanel() {
            setLayout(new MigLayout("insets 5, wrap 3", "[40!][40!][40!]", "[40!][40!][40!][]"));
            for (int i = 0; i < 9; i++) {
                int node = i + 1;
                JToggleButton dot = new JToggleButton(String.valueOf(node));
                dot.addActionListener(e -> onDotClicked(node));
                dots[i] = dot;
                add(dot, "grow");
            }
            JButton reset = new JButton("Deseni Temizle");
            reset.addActionListener(e -> setSequence(""));
            add(sequenceLabel, "span 3, wrap");
            add(reset, "span 3");
        }

        private void onDotClicked(int node) {
            if (sequence.contains(node)) {
                dots[node - 1].setSelected(true); // zaten seçili düğüme tekrar tıklamayı yok say
                return;
            }
            sequence.add(node);
            updateLabel();
        }

        String getSequence() {
            StringBuilder sb = new StringBuilder();
            for (int node : sequence) {
                if (sb.length() > 0) sb.append("-");
                sb.append(node);
            }
            return sb.toString();
        }

        void setSequence(String value) {
            sequence.clear();
            for (JToggleButton dot : dots) dot.setSelected(false);
            if (value != null && !value.isBlank()) {
                for (String part : value.split("-")) {
                    try {
                        int node = Integer.parseInt(part.trim());
                        if (node >= 1 && node <= 9) {
                            sequence.add(node);
                            dots[node - 1].setSelected(true);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            updateLabel();
        }

        private void updateLabel() {
            sequenceLabel.setText(sequence.isEmpty() ? "Deseni sırayla tıklayın" : "Desen: " + getSequence());
        }
    }
}
