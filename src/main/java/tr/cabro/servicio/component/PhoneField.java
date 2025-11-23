package tr.cabro.servicio.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.text.ParseException;
import java.util.List;

public class PhoneField extends JFormattedTextField {

    private JComboBox<PhoneHelper.CountryCode> countryCombo;
    private int maxDigitLength = 15; // Varsayılan güvenlik sınırı

    public PhoneField() {
        super();
        init();
    }

    private void init() {
        // 1. Ülke Listesini Hazırla
        List<PhoneHelper.CountryCode> countries = PhoneHelper.getSupportedCountries();
        countryCombo = new JComboBox<>(countries.toArray(new PhoneHelper.CountryCode[0]));
        countryCombo.setLightWeightPopupEnabled(false);

        // 2. Varsayılan Seçim (TR)
        for (int i = 0; i < countryCombo.getItemCount(); i++) {
            if ("TR".equals(countryCombo.getItemAt(i).getRegionCode())) {
                countryCombo.setSelectedIndex(i);
                break;
            }
        }

        // --- GÖRÜNÜM (Renderer vb.) ---
        setupRenderer();

        // UI Ayarları
        countryCombo.putClientProperty("JComboBox.showArrowButton", false);
        countryCombo.putClientProperty(FlatClientProperties.STYLE, "border: null; background: null;");
        countryCombo.setFocusable(false);

        // TextField Entegrasyonu
        putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, countryCombo);

        // Event Listener: Ülke değiştiğinde Formatter'ı güncelle
        countryCombo.addActionListener(e -> updateFormatter());

        // --- SÜREKLİ KONTROL (Validasyon) ---
        // Kullanıcı her yazı yazdığında veya sildiğinde kontrol et
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { checkValidity(); }
            @Override
            public void removeUpdate(DocumentEvent e) { checkValidity(); }
            @Override
            public void changedUpdate(DocumentEvent e) { checkValidity(); }
        });

        // İlk Ayarlar
        updateFormatter();
    }

    /**
     * Anlık olarak metni kontrol eder ve geçerli değilse
     * TextField çerçevesini kırmızı yapar (FlatLaf Error Outline).
     */
    private void checkValidity() {
        try {
            String text = getText();
            // Boşsa nötr durumda kalsın (veya zorunlu ise hata verebilirsiniz)
            if (text == null || text.trim().isEmpty()) {
                putClientProperty(FlatClientProperties.OUTLINE, null);
                return;
            }

            // Sadece rakamları al
            String digits = text.replaceAll("[^\\d]", "");

            // Seçili ülkeyi al
            PhoneHelper.CountryCode selected = (PhoneHelper.CountryCode) countryCombo.getSelectedItem();

            if (selected != null) {
                // normalize metodu numara geçersizse hata fırlatır
                PhoneHelper.normalize(selected.getRegionCode(), digits);

                // Hata fırlatmadıysa geçerlidir, kırmızılığı kaldır
                putClientProperty(FlatClientProperties.OUTLINE, null);
            }
        } catch (Exception e) {
            // Hata varsa çerçeveyi kırmızı yap
            putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
        }
    }

    /**
     * Seçili ülkeye göre JFormattedTextField'in FormatterFactory'sini günceller.
     */
    private void updateFormatter() {
        PhoneHelper.CountryCode selected = (PhoneHelper.CountryCode) countryCombo.getSelectedItem();
        if (selected != null) {
            String regionCode = selected.getRegionCode();

            // 1. Placeholder ve Max Length Hesapla
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber example = util.getExampleNumber(regionCode);
            if (example != null) {
                String formattedExample = util.format(example, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
                maxDigitLength = String.valueOf(example.getNationalNumber()).length();
                putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, formattedExample);
            } else {
                maxDigitLength = 15;
                putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Numara giriniz");
            }

            setToolTipText("+" + selected.getPhoneCode() + " " + selected.getName());

            // 2. Formatter'ı Değiştir
            Object currentValue = getValue();
            setFormatterFactory(new DefaultFormatterFactory(new PhoneFormatter(regionCode, maxDigitLength)));
            if (currentValue != null) {
                setValue(currentValue);
            }

            // Ülke değişince mevcut numaranın o ülke için geçerliliğini tekrar kontrol et
            checkValidity();
        }
    }

    // --- Helper Methods ---

    /**
     * Formdan veri çekerken hata fırlatmamak için güncellendi.
     * Geçersizse veya boşsa null döner.
     */
    public String getNormalizedNumber() {
        String text = getText();
        if (text == null || text.trim().isEmpty()) return null;

        PhoneHelper.CountryCode selected = (PhoneHelper.CountryCode) countryCombo.getSelectedItem();
        if (selected == null) return null;

        try {
            // getValue() yerine anlık text üzerinden gidiyoruz
            String digits = text.replaceAll("[^\\d]", "");
            return PhoneHelper.normalize(selected.getRegionCode(), digits);
        } catch (Exception e) {
            // Geçersiz numara girildiyse uygulama çökmesin, null dönsün.
            // Zaten ekranda kırmızı yandığı için kullanıcı farkındadır.
            return null;
        }
    }

    public void setNumber(String e164Number) {
        if (e164Number == null || e164Number.isEmpty()) {
            setValue(null);
            return;
        }
        try {
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            if (!e164Number.startsWith("+")) e164Number = "+" + e164Number;

            Phonenumber.PhoneNumber number = util.parse(e164Number, null);
            String regionCode = util.getRegionCodeForNumber(number);
            long nationalNumber = number.getNationalNumber();

            for (int i = 0; i < countryCombo.getItemCount(); i++) {
                if (countryCombo.getItemAt(i).getRegionCode().equals(regionCode)) {
                    countryCombo.setSelectedIndex(i);
                    break;
                }
            }
            setValue(String.valueOf(nationalNumber));
        } catch (Exception e) {
            setValue(e164Number);
        }
        // Numarayı set ettikten sonra da validasyonu kontrol et
        checkValidity();
    }

    private void setupRenderer() {
        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 14);
        if (emojiFont.getFamily().equals("Dialog")) {
            emojiFont = countryCombo.getFont().deriveFont(14f);
        }
        countryCombo.setFont(emojiFont);
        final Font finalFont = emojiFont;

        countryCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setFont(finalFont);
                if (value instanceof PhoneHelper.CountryCode) {
                    PhoneHelper.CountryCode code = (PhoneHelper.CountryCode) value;
                    if (index == -1) label.setText("+" + code.getPhoneCode());
                    else label.setText(code.getFlag() + "  +" + code.getPhoneCode() + "  " + code.getName());
                }
                return label;
            }
        });
    }

    // =================================================================================
    //  CUSTOM FORMATTER CLASS
    // =================================================================================

    private static class PhoneFormatter extends JFormattedTextField.AbstractFormatter {
        private final String regionCode;
        private final int maxDigits;
        private final AsYouTypeFormatter asYouTypeFormatter;

        public PhoneFormatter(String regionCode, int maxDigits) {
            this.regionCode = regionCode;
            this.maxDigits = maxDigits;
            this.asYouTypeFormatter = PhoneHelper.getAsYouTypeFormatter(regionCode);
        }

        @Override
        public Object stringToValue(String text) throws ParseException {
            if (text == null || text.trim().isEmpty()) return null;
            return text.replaceAll("[^\\d]", "");
        }

        @Override
        public String valueToString(Object value) throws ParseException {
            if (value == null) return "";

            String digits = value.toString().replaceAll("[^\\d]", "");
            asYouTypeFormatter.clear();
            String formatted = "";
            for (char c : digits.toCharArray()) {
                formatted = asYouTypeFormatter.inputDigit(c);
            }
            return formatted;
        }

        @Override
        protected DocumentFilter getDocumentFilter() {
            return new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                    replace(fb, offset, 0, string, attr);
                }

                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                    String currentText = fb.getDocument().getText(0, fb.getDocument().getLength());
                    String futureText = new StringBuilder(currentText).replace(offset, offset + length, text).toString();
                    String digits = futureText.replaceAll("[^\\d]", "");

                    if (digits.length() > maxDigits) {
                        return;
                    }

                    asYouTypeFormatter.clear();
                    String formatted = "";
                    for (char c : digits.toCharArray()) {
                        formatted = asYouTypeFormatter.inputDigit(c);
                    }
                    super.replace(fb, 0, fb.getDocument().getLength(), formatted, attrs);
                }

                @Override
                public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                    replace(fb, offset, length, "", null);
                }
            };
        }
    }
}