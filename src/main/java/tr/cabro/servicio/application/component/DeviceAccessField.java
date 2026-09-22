package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.enums.DeviceAccessType;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;

/**
 * Cihaz ekran kilidi erişim bilgisini (PIN / Şifre / Desen) toplayan bileşen.
 * <p>
 * Tür, açılır liste yerine yan yana düğmelerle seçilir; seçime göre giriş alanı değişir:
 * <ul>
 *   <li>{@link DeviceAccessType#PIN} — sadece rakam kabul eden şifre alanı</li>
 *   <li>{@link DeviceAccessType#PASSWORD} — serbest metin şifre alanı</li>
 *   <li>{@link DeviceAccessType#PATTERN} — {@link PatternLockPad} üzerinde çizilen desen</li>
 *   <li>{@link DeviceAccessType#NONE} — giriş alanı gizlenir</li>
 * </ul>
 * Bu bileşen hiçbir şeyi şifrelemez/saklamaz — sadece düz metin değeri toplar;
 * şifreleme {@code DeviceAccessCredentialService} tarafından yapılır.
 */
public class DeviceAccessField extends JPanel {

    private final SegmentedButtons<DeviceAccessType> typeButtons = new SegmentedButtons<>();

    private final JPasswordField pinField;
    private final JPasswordField passwordField;
    private final JPanel patternPanel;
    private final PatternLockPad patternPad;
    private final JLabel patternSummary;
    private final JLabel noneHint;

    public DeviceAccessField() {
        // hidemode 3: setVisible(false) yapılan bileşen düzende hiç yer kaplamaz —
        // CardLayout kullanmıyoruz çünkü o, gösterilmeyen kartlar için bile en büyük
        // kartın (desen alanı) boyutunu ayırıp altında boşluk bırakıyordu.
        setLayout(new MigLayout("insets 0, fillx, wrap 1, hidemode 3", "[fill,grow]", "[]8[]"));
        setOpaque(false);

        typeButtons.add(DeviceAccessType.NONE, "Kilit Yok", null)
                .add(DeviceAccessType.PIN, "PIN", null)
                .add(DeviceAccessType.PASSWORD, "Şifre", null)
                .add(DeviceAccessType.PATTERN, "Desen", null);
        typeButtons.setOnChange(type -> {
            showFieldFor(type);
            focusInput(type);
        });
        add(typeButtons, "growx 0");

        noneHint = new JLabel("Cihazda ekran kilidi yok ya da müşteri paylaşmadı.");
        noneHint.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        add(noneHint);

        pinField = new JPasswordField();
        pinField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        pinField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ekran PIN'i (yalnızca rakam)");
        applyDigitsOnlyFilter(pinField);
        add(pinField);

        passwordField = new JPasswordField();
        passwordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ekran şifresi");
        add(passwordField);

        // Desen: solda çizim alanı, sağda sıra özeti ve düzeltme düğmeleri.
        patternPad = new PatternLockPad();
        patternSummary = new JLabel();
        patternSummary.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        JLabel patternHelp = new JLabel("<html>Sürükleyerek çizin ya da noktalara sırayla tıklayın. "
                + "Noktadaki sayı seçilme sırasıdır.</html>");
        patternHelp.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");

        JButton undo = smallButton("Geri Al", "icons/undo-2.svg");
        undo.addActionListener(e -> patternPad.removeLast());
        JButton reset = smallButton("Temizle", "icons/x.svg");
        reset.addActionListener(e -> patternPad.setSequence(""));

        JPanel side = new JPanel(new MigLayout("insets 0, wrap, gap 0", "[grow, fill]", "[]6[]12[]"));
        side.setOpaque(false);
        side.add(patternSummary);
        side.add(patternHelp, "wmin 0, w 10:160:");
        JPanel buttons = new JPanel(new MigLayout("insets 0, gap 6", "[][]", "[]"));
        buttons.setOpaque(false);
        buttons.add(undo);
        buttons.add(reset);
        side.add(buttons);

        patternPanel = new JPanel(new MigLayout("insets 8 8 8 14, gapx 16", "[][grow, fill]", "[center]"));
        patternPanel.putClientProperty(FlatClientProperties.STYLE, "arc: 14; background: fade($Label.foreground, 4%)");
        patternPanel.add(patternPad);
        patternPanel.add(side, "aligny center, wmin 0");
        add(patternPanel);

        patternPad.setOnChange(this::updatePatternSummary);
        updatePatternSummary();
        showFieldFor(DeviceAccessType.NONE);
    }

    private void updatePatternSummary() {
        String seq = patternPad.getSequence();
        patternSummary.setText(seq.isEmpty() ? "Desen çizilmedi" : "Desen: " + seq.replace("-", " → "));
    }

    private static JButton smallButton(String text, String icon) {
        JButton b = new JButton(text, new Ikon(icon, 0.75f));
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 3,8,3,8; iconTextGap: 4; font: -1");
        return b;
    }

    private void showFieldFor(DeviceAccessType type) {
        if (type == null) type = DeviceAccessType.NONE;
        noneHint.setVisible(type == DeviceAccessType.NONE);
        pinField.setVisible(type == DeviceAccessType.PIN);
        passwordField.setVisible(type == DeviceAccessType.PASSWORD);
        patternPanel.setVisible(type == DeviceAccessType.PATTERN);

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

    private void focusInput(DeviceAccessType type) {
        SwingUtilities.invokeLater(() -> {
            switch (type) {
                case PIN -> pinField.requestFocusInWindow();
                case PASSWORD -> passwordField.requestFocusInWindow();
                case PATTERN -> patternPad.requestFocusInWindow();
                default -> { }
            }
        });
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
        return typeButtons.getSelected();
    }

    public void setAccessType(DeviceAccessType type) {
        DeviceAccessType t = type != null ? type : DeviceAccessType.NONE;
        typeButtons.setSelected(t);
        showFieldFor(t);
    }

    /** Seçili türe göre girilen düz metin değeri döner (PATTERN için "1-5-9" gibi bir dizi). */
    public String getValue() {
        DeviceAccessType type = getAccessType();
        if (type == null) return null;
        return switch (type) {
            case PIN -> new String(pinField.getPassword());
            case PASSWORD -> new String(passwordField.getPassword());
            case PATTERN -> patternPad.getSequence();
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
            patternPad.setSequence(value);
        }
    }

    public void clear() {
        pinField.setText("");
        passwordField.setText("");
        patternPad.setSequence("");
        setAccessType(DeviceAccessType.NONE);
    }
}
