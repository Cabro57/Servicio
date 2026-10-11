package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import com.google.i18n.phonenumbers.Phonenumber;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.PhoneHelper.CountryCode;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Telefon girişi: tek bir alan, solunda ülke seçici (bayrak, "+90", aşağı ok; ince bir çizgiyle
 * ayrılır), sağında o ülkenin yazım biçimiyle kendiliğinden gruplanan numara ("532 111 22 33").
 * <ul>
 *   <li>Ülke seçiciye tıklamak ya da alandayken Alt+↓ / F4 aranabilir ülke listesini açar
 *       ({@link PhoneCountryPicker}). Ülke değişince yazılmış rakamlar yeni ülkenin biçimiyle dizilir.</li>
 *   <li>"+49 30 …" ya da "0049 30 …" yazmak/yapıştırmak ülkeyi kendisi bulur ve numarayı o ülkenin
 *       ulusal biçimine çevirir. Başına 0 koyarak yazmak ("0532 …") da geçerlidir; alandan çıkınca
 *       numara olağan biçimine ("532 …") getirilir.</li>
 *   <li>Geçersiz numarada çerçeve kırmızıdır (FlatLaf hata çizgisi); boşken nötrdür.</li>
 *   <li>Silerken araya girmiş boşluk atlanır: boşluğu silmek, ondan önceki rakamı siler.</li>
 * </ul>
 * Kayıt biçimi E.164'tür ({@link #getNormalizedNumber()}); gösterim {@link PhoneHelper#formatForDisplay}.
 */
public class PhoneField extends JTextField {

    private String region;
    private int maxDigits;
    private String autoPlaceholder;
    private boolean reformatting;
    private final CountrySelector selector = new CountrySelector();

    public PhoneField() {
        super();
        putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, selector);
        ((AbstractDocument) getDocument()).setDocumentFilter(new Filter());
        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { checkValidity(); }
            @Override public void removeUpdate(DocumentEvent e) { checkValidity(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!e.isTemporary()) canonicalize();
            }
        });
        Action open = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openPicker();
            }
        };
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), "phone.country");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), "phone.country");
        getActionMap().put("phone.country", open);

        applyRegion(PhoneHelper.homeRegion(), false);
    }

    // ------------------------------------------------------------------ dışa açık API

    /** E.164 ("+905321112233"); boş ya da geçersizse null (geçersizi boştan ayırmak için {@link #getText()}). */
    public String getNormalizedNumber() {
        String text = getText();
        if (text == null || text.isBlank()) return null;
        try {
            return PhoneHelper.normalize(region, text);
        } catch (Exception e) {
            return null;
        }
    }

    /** Kayıtlı bir numarayı gösterir; ülkesi numaradan bulunur. Eski biçimli kayıtlar da çözülür. */
    public void setNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            writeText("");
            return;
        }
        Phonenumber.PhoneNumber n = PhoneHelper.parseLenient(raw);
        String r = PhoneHelper.regionOf(n);
        if (n == null || r == null) {
            setText(raw.strip());
            return;
        }
        if (!r.equals(region)) applyRegion(r, false);
        writeText(PhoneHelper.formatInField(n));
    }

    /** Seçili ülke (ISO kodu, ör. "TR"). */
    public String getRegion() {
        return region;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (selector != null) selector.setEnabled(enabled);
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        if (selector != null) selector.setEnabled(editable && isEnabled());
    }

    // ------------------------------------------------------------------ ülke

    private void openPicker() {
        if (!isEnabled() || !isEditable()) return;
        selector.setOpen(true);
        PhoneCountryPicker.show(this, region,
                country -> applyRegion(country.getRegionCode(), true),
                () -> selector.setOpen(false));
    }

    /**
     * Ülkeyi değiştirir: seçici, ipucu metni ve rakam sınırı güncellenir. {@code reformat} ise alandaki
     * rakamlar yeni ülkenin biçimiyle yeniden dizilir.
     */
    private void applyRegion(String newRegion, boolean reformat) {
        String old = region;
        region = newRegion;
        maxDigits = PhoneHelper.maxDigitsFor(newRegion);
        selector.show(PhoneHelper.country(newRegion));

        String placeholder = PhoneHelper.placeholderFor(newRegion);
        Object currentPlaceholder = getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT);
        // Formun kendi ipucunu ("İsteğe bağlı") ezme; yalnızca ülkeden gelen ipucu güncellenir.
        if (currentPlaceholder == null || currentPlaceholder.equals(autoPlaceholder)) {
            putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        }
        autoPlaceholder = placeholder;

        if (reformat) {
            String digits = getText().replaceAll("\\D", "");
            writeText(PhoneHelper.formatAsYouType(region, digits));
        }
        if (old != null && !old.equals(newRegion)) firePropertyChange("region", old, newRegion);
        checkValidity();
    }

    /** Geçerli numarayı olağan biçimine getirir (baştaki 0 düşer, uluslararası yazım ülkeye dönüşür). */
    private void canonicalize() {
        String e164 = getNormalizedNumber();
        if (e164 == null) return;
        String before = getText();
        Phonenumber.PhoneNumber n = PhoneHelper.parseLenient(e164);
        String r = PhoneHelper.regionOf(n);
        if (r == null) return;
        if (!r.equals(region)) applyRegion(r, false);
        String canonical = PhoneHelper.formatInField(n);
        if (!canonical.equals(before)) writeText(canonical);
    }

    /** "+…" ile yazılmış numara geçerli hâle gelince ülkeye geçer ve ulusal yazıma döner. */
    private void adoptInternational(String text) {
        Phonenumber.PhoneNumber n = PhoneHelper.parseLenient(text);
        String r = PhoneHelper.regionOf(n);
        if (!PhoneHelper.isValid(n) || r == null) return;
        if (!getText().equals(text)) return; // bu arada yazmaya devam edildi
        applyRegion(r, false);
        writeText(PhoneHelper.formatInField(n));
        setCaretPosition(getDocument().getLength());
    }

    // ------------------------------------------------------------------ doğrulama ve yazım

    private void checkValidity() {
        String text = getText();
        boolean invalid = text != null && !text.isBlank() && getNormalizedNumber() == null;
        putClientProperty(FlatClientProperties.OUTLINE, invalid ? FlatClientProperties.OUTLINE_ERROR : null);
    }

    /** Biçimlemeyi atlayarak metni olduğu gibi yazar (zaten biçimlenmiş metin için). */
    private void writeText(String text) {
        reformatting = true;
        try {
            setText(text);
        } finally {
            reformatting = false;
        }
        checkValidity();
    }

    private static boolean significant(char c) {
        return Character.isDigit(c) || c == '+';
    }

    /**
     * Her değişiklikte metnin tamamını ülkenin yazım biçimiyle yeniden dizer; imleç, solundaki rakam
     * sayısı korunarak yeniden yerleştirilir (araya giren boşluklar imleci kaydırmaz).
     */
    private final class Filter extends DocumentFilter {

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            if (!reformatting && length > 0) {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                String removed = current.substring(offset, offset + length);
                if (removed.chars().noneMatch(c -> significant((char) c))) {
                    // Yalnızca boşluk silinmek istendi: Backspace ise soldaki, Delete ise sağdaki rakamı sil.
                    boolean backspace = getCaretPosition() == offset + length;
                    int i = backspace ? offset - 1 : offset + length;
                    while (i >= 0 && i < current.length() && !significant(current.charAt(i))) i += backspace ? -1 : 1;
                    if (i < 0 || i >= current.length()) return;
                    offset = i;
                    length = 1;
                }
            }
            replace(fb, offset, length, "", null);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (reformatting) {
                super.replace(fb, offset, length, text, attrs);
                return;
            }
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String inserted = text == null ? "" : text;
            String future = current.substring(0, offset) + inserted + current.substring(offset + length);

            int caretSignificant = 0;
            int caretEnd = offset + inserted.length();
            for (int i = 0; i < caretEnd && i < future.length(); i++) {
                if (significant(future.charAt(i))) caretSignificant++;
            }

            String stripped = future.strip();
            boolean international = stripped.startsWith("+");
            String digits = future.replaceAll("\\D", "");
            if (!international && digits.startsWith("00") && digits.length() > 2) {
                international = true;
                digits = digits.substring(2);
                caretSignificant = Math.max(0, caretSignificant - 1); // "00" → "+": bir karakter azaldı
            }
            if (digits.length() > (international ? 15 : maxDigits)) {
                UIManager.getLookAndFeel().provideErrorFeedback(PhoneField.this);
                return;
            }

            String formatted = digits.isEmpty() && !international ? ""
                    : PhoneHelper.formatAsYouType(region, (international ? "+" : "") + digits);
            super.replace(fb, 0, fb.getDocument().getLength(), formatted, attrs);

            int caret = formatted.length();
            int seen = 0;
            for (int i = 0; i < formatted.length(); i++) {
                if (seen == caretSignificant) {
                    caret = i;
                    break;
                }
                if (significant(formatted.charAt(i))) seen++;
            }
            int finalCaret = Math.min(caret, formatted.length());
            setCaretPosition(finalCaret);

            if (international) {
                String snapshot = formatted;
                SwingUtilities.invokeLater(() -> adoptInternational(snapshot));
            }
        }
    }

    /**
     * Alanın solundaki ülke düğmesi: bayrak, "+kod", soluk aşağı ok; sağ kenarında yarım boy ince
     * çizgi. Üzerine gelince araç düğmesi gibi zemin alır; liste açıkken basılı görünür. Odak almaz,
     * odak numarada kalır.
     */
    private final class CountrySelector extends JPanel {
        private final JLabel flag = new JLabel();
        private final JLabel code = new JLabel();
        private final JLabel chevron = new JLabel(new Ikon("icons/chevron-down.svg", 12, "Label.disabledForeground"));
        private boolean hover;
        private boolean open;
        private long closedAt;

        /** Ayraç çizgisinden numaraya kadar boşluk. */
        private static final int TEXT_GAP = 8;

        CountrySelector() {
            setLayout(new MigLayout("insets 0 8 0 " + (9 + TEXT_GAP) + ", gap 0, aligny center", "[]6[]4[]", "[grow, center]"));
            setOpaque(false);
            add(flag);
            add(code);
            add(chevron);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    // Açık listeyi kapatan aynı tıklama listeyi yeniden açmasın.
                    boolean justClosed = System.currentTimeMillis() - closedAt < 250;
                    if (!SwingUtilities.isLeftMouseButton(e) || !isEnabled() || open || justClosed) return;
                    PhoneField.this.requestFocusInWindow();
                    openPicker();
                }
            });
        }

        void show(CountryCode country) {
            if (country == null) return;
            CountryFlagIcon icon = CountryFlagIcon.of(country.getFlagPath(), 18);
            flag.setIcon(icon);
            flag.setDisabledIcon(icon.disabled());
            code.setText("+" + country.getPhoneCode());
            setToolTipText(country.getName() + " (+" + country.getPhoneCode() + ")  ·  Ülkeyi değiştir: Alt+↓");
            getAccessibleContext().setAccessibleName("Ülke kodu: " + country.getName());
            revalidate();
            repaint();
        }

        void setOpen(boolean open) {
            if (this.open && !open) closedAt = System.currentTimeMillis();
            this.open = open;
            repaint();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            flag.setEnabled(enabled);
            code.setEnabled(enabled);
            chevron.setVisible(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }

        @Override
        public boolean isFocusable() {
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int line = UIScale.scale(1);
                int gutter = UIScale.scale(3);
                int separatorX = getWidth() - UIScale.scale(TEXT_GAP) - line;
                if (isEnabled() && (hover || open)) {
                    float arc = UIScale.scale(8f);
                    g2.setColor(UIManager.getColor(open ? "Button.toolbar.pressedBackground" : "Button.toolbar.hoverBackground"));
                    g2.fill(new RoundRectangle2D.Float(gutter, gutter, separatorX - 2 * gutter, getHeight() - 2 * gutter, arc, arc));
                }
                g2.setColor(UIManager.getColor("Component.borderColor"));
                int h = getHeight() / 2;
                g2.fillRect(separatorX, (getHeight() - h) / 2, line, h);
            } finally {
                g2.dispose();
            }
        }
    }
}
