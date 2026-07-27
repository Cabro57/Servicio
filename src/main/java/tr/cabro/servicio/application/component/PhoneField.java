package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.text.ParseException;


// Ülke seçici kaldırıldı — şimdilik sadece Türkiye numaraları kabul ediliyor.
// PhoneHelper.getSupportedCountries()/CountryCode kodu ileride tekrar kullanılabilmesi
// için dokunulmadan bırakıldı, sadece bu sınıf artık onu çağırmıyor.
public class PhoneField extends JFormattedTextField {

    private static final String REGION_CODE = "TR";
    private int maxDigitLength = 15; // Varsayılan güvenlik sınırı

    public PhoneField() {
        super();
        init();
    }

    private void init() {
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

            // normalize metodu numara geçersizse hata fırlatır
            PhoneHelper.normalize(REGION_CODE, digits);

            // Hata fırlatmadıysa geçerlidir, kırmızılığı kaldır
            putClientProperty(FlatClientProperties.OUTLINE, null);
        } catch (Exception e) {
            // Hata varsa çerçeveyi kırmızı yap
            putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
        }
    }

    /**
     * Formatter'ı Türkiye numara formatına göre ayarlar.
     */
    private void updateFormatter() {
        // 1. Placeholder ve Max Length Hesapla
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber example = util.getExampleNumber(REGION_CODE);
        if (example != null) {
            String formattedExample = util.format(example, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
            maxDigitLength = String.valueOf(example.getNationalNumber()).length();
            putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, formattedExample);
        } else {
            maxDigitLength = 15;
            putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Numara giriniz");
        }

        // 2. Formatter'ı Değiştir
        Object currentValue = getValue();
        setFormatterFactory(new DefaultFormatterFactory(new PhoneFormatter(REGION_CODE, maxDigitLength)));
        if (currentValue != null) {
            setValue(currentValue);
        }

        checkValidity();
    }

    // --- Helper Methods ---

    /**
     * Formdan veri çekerken hata fırlatmamak için güncellendi.
     * Geçersizse veya boşsa null döner.
     */
    public String getNormalizedNumber() {
        String text = getText();
        if (text == null || text.trim().isEmpty()) return null;

        try {
            // getValue() yerine anlık text üzerinden gidiyoruz
            String digits = text.replaceAll("[^\\d]", "");
            return PhoneHelper.normalize(REGION_CODE, digits);
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
            long nationalNumber = number.getNationalNumber();
            setValue(String.valueOf(nationalNumber));
        } catch (Exception e) {
            setValue(e164Number);
        }
        // Numarayı set ettikten sonra da validasyonu kontrol et
        checkValidity();
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
