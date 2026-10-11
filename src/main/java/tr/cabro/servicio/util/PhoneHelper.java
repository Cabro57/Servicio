package tr.cabro.servicio.util;

import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.Getter;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.service.exception.ValidationException;

import java.text.Collator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telefon numaralarının tek kaynağı: çözümleme, kayıt biçimi (E.164) ve ekranda/belgede gösterim.
 * <p>
 * Gösterim kuralı her yerde aynıdır: işletmenin bölgesindeki (Ayarlar &gt; Dil ve Bölge, yoksa TR)
 * numaralar ulusal biçimde ("0532 111 22 33", "(0212) 345 67 89"), başka ülkelerin numaraları
 * uluslararası biçimde ("+49 30 1234567") yazılır. Kayıtlı değer hangi biçimde olursa olsun
 * ({@code +905321112233}, eski kayıtlardaki {@code 905321112233} ya da {@code 05321112233})
 * önce {@link #parseLenient} ile çözülür; gösterim ham metne asla tahminle "+" eklemez.
 */
public class PhoneHelper {

    private static final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
    private static final String FALLBACK_REGION = "TR";

    /**
     * UI'dan gelen (ülke kodu seçili + ham numara) veriyi E.164 formatına (+90532...) çevirir.
     */
    public static String normalize(String regionCode, String phoneNumber) throws ValidationException {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) return null;
        try {
            Phonenumber.PhoneNumber number = phoneUtil.parse(phoneNumber, regionCode);
            if (!phoneUtil.isValidNumber(number)) {
                throw new ValidationException("Bu numara seçilen ülke (" + regionCode + ") için geçerli değil.");
            }
            return phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw new ValidationException("Telefon numarası formatı hatalı: " + e.getMessage());
        }
    }

    /** İşletmenin bölgesi: biçim bölgesinin ülkesi libphonenumber'da varsa o, yoksa TR. */
    public static String homeRegion() {
        String country = AppLocale.formatLocale().getCountry();
        return country != null && phoneUtil.getSupportedRegions().contains(country) ? country : FALLBACK_REGION;
    }

    /**
     * Kayıtlı ya da yazılmış bir numarayı çözer; çözülemezse null. Sırasıyla: "+" ile başlıyorsa
     * uluslararası, "00" ile başlıyorsa uluslararası, sonra işletme bölgesine göre ulusal; o geçersizse
     * başında "+" unutulmuş E.164 (eski kayıtlar) denenir.
     */
    public static Phonenumber.PhoneNumber parseLenient(String raw) {
        if (raw == null) return null;
        String s = raw.strip();
        if (s.isEmpty()) return null;
        String digits = s.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        try {
            if (s.startsWith("+")) return phoneUtil.parse("+" + digits, null);
            if (digits.startsWith("00")) return phoneUtil.parse("+" + digits.substring(2), null);
            Phonenumber.PhoneNumber national = phoneUtil.parse(digits, homeRegion());
            if (phoneUtil.isValidNumber(national)) return national;
            try {
                Phonenumber.PhoneNumber international = phoneUtil.parse("+" + digits, null);
                if (phoneUtil.isValidNumber(international)) return international;
            } catch (NumberParseException ignored) {
                // ulusal sonuç döner
            }
            return national;
        } catch (NumberParseException e) {
            return null;
        }
    }

    /** Kayıt biçimi; numara çözülemez ya da geçersizse null. */
    public static String toE164(String raw) {
        Phonenumber.PhoneNumber n = parseLenient(raw);
        return n != null && phoneUtil.isValidNumber(n) ? phoneUtil.format(n, PhoneNumberUtil.PhoneNumberFormat.E164) : null;
    }

    /** Numaranın ait olduğu ülke (ör. "DE"); bilinmiyorsa null. */
    public static String regionOf(Phonenumber.PhoneNumber number) {
        if (number == null) return null;
        String region = phoneUtil.getRegionCodeForNumber(number);
        return region == null || "ZZ".equals(region) ? null : region;
    }

    /** Ülke kodunu atlayan ulusal anlamlı numara (ör. "5321112233"). */
    public static String nationalSignificant(Phonenumber.PhoneNumber number) {
        return phoneUtil.getNationalSignificantNumber(number);
    }

    public static boolean isValid(Phonenumber.PhoneNumber number) {
        return number != null && phoneUtil.isValidNumber(number);
    }

    /**
     * libphonenumber ayrıştırması liste/tablo renderer'larında her çizimde tekrarlanıyordu; aynı
     * numara hep aynı sonucu verdiği için sonuç önbelleğe alınır. Sınırsız büyümesin diye taşınca temizlenir.
     * Anahtar bölgeyi de içerir: bölge değişince eski biçimler geri dönmez.
     */
    private static final Map<String, String> DISPLAY_CACHE = new ConcurrentHashMap<>();
    private static final int DISPLAY_CACHE_LIMIT = 5000;

    /**
     * Ekranda, belgede ve şablonda gösterilecek biçim (bkz. sınıf açıklaması). Boşsa "", çözülemeyen
     * bir değerse olduğu gibi (kırpılmış) döner.
     */
    public static String formatForDisplay(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String home = homeRegion();
        String key = home + '|' + raw;
        String cached = DISPLAY_CACHE.get(key);
        if (cached != null) return cached;
        if (DISPLAY_CACHE.size() >= DISPLAY_CACHE_LIMIT) DISPLAY_CACHE.clear();
        String formatted = formatUncached(raw, home);
        DISPLAY_CACHE.put(key, formatted);
        return formatted;
    }

    private static String formatUncached(String raw, String home) {
        Phonenumber.PhoneNumber n = parseLenient(raw);
        if (n == null || !phoneUtil.isValidNumber(n)) return raw.strip();
        boolean domestic = home.equals(phoneUtil.getRegionCodeForNumber(n));
        return phoneUtil.format(n, domestic ? PhoneNumberUtil.PhoneNumberFormat.NATIONAL
                : PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
    }

    /** wa.me deep link'i için "+" olmadan E.164 rakam dizisi üretir (ör. 905321234567); geçersizse null. */
    public static String toWhatsAppDigits(String raw) {
        String e164 = toE164(raw);
        return e164 != null ? e164.substring(1) : null;
    }

    /**
     * Seçilen ülke için "Yazarken Formatlama" nesnesi döndürür.
     */
    public static AsYouTypeFormatter getAsYouTypeFormatter(String regionCode) {
        return phoneUtil.getAsYouTypeFormatter(regionCode);
    }

    /**
     * Alanın içindeki olağan görünüm: ülke kodu solda ayrıca durduğu için uluslararası biçimin
     * "+kod " kısmı atılır ("+49 30 1234567" → "30 1234567", "+90 532 111 22 33" → "532 111 22 33").
     * Ulusal öneki olan ülkelerde (DE, GB…) önek olmadan da doğru gruplanır.
     */
    public static String formatInField(Phonenumber.PhoneNumber number) {
        String international = phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        String prefix = "+" + number.getCountryCode();
        return international.startsWith(prefix) ? international.substring(prefix.length()).strip() : nationalSignificant(number);
    }

    /** Rakamları verilen ülkenin yazım biçimiyle gruplar (yazarken alanın içindeki görünüm). */
    public static String formatAsYouType(String regionCode, String digits) {
        AsYouTypeFormatter f = phoneUtil.getAsYouTypeFormatter(regionCode);
        String out = "";
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c == '+' || Character.isDigit(c)) out = f.inputDigit(c);
        }
        return out;
    }

    /**
     * Alanın ipucu metni: o ülkenin örnek cep numarası, alanda yazıldığı gibi (ülke kodu solda
     * durduğu için başsız; TR için "501 234 56 78").
     */
    public static String placeholderFor(String regionCode) {
        Phonenumber.PhoneNumber example = phoneUtil.getExampleNumberForType(regionCode, PhoneNumberUtil.PhoneNumberType.MOBILE);
        if (example == null) example = phoneUtil.getExampleNumber(regionCode);
        return example != null ? formatInField(example) : "";
    }

    /** Ülkenin en uzun anlamlı numarasına göre alanın kabul edeceği rakam sınırı (baştaki 0 payı dahil). */
    public static int maxDigitsFor(String regionCode) {
        Phonenumber.PhoneNumber example = phoneUtil.getExampleNumber(regionCode);
        int base = example != null ? nationalSignificant(example).length() : 13;
        return Math.min(17, base + 3);
    }

    /**
     * Seçilen ülke için örnek bir telefon numarası döndürür.
     * Placeholder (İpucu) metni için kullanılır.
     * Örn: TR -> "532 123 45 67"
     */
    public static String getExampleNumber(String regionCode) {
        Phonenumber.PhoneNumber example = phoneUtil.getExampleNumber(regionCode);
        if (example != null) {
            // Ulusal formatı al (International değil, çünkü +90 zaten başta var)
            return phoneUtil.format(example, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
        }
        return "5XX XXX XX XX"; // Varsayılan
    }

    /**
     * ~250 ülke için Locale çözümü pahalı bir işlemdir. Bu liste uygulama çalışırken değişmediği için
     * bir kez hesaplanıp önbelleğe alınır — aksi halde her telefon alanı (dolayısıyla her müşteri/cihaz
     * formu) açılışında EDT üzerinde tekrar tekrar hesaplanması arayüzü anlık donduruyordu.
     * İşletmenin bölgesi başta, kalanlar arayüz dilinin alfabe sırasıyla.
     */
    private static List<CountryCode> supportedCountriesCache;

    public static synchronized List<CountryCode> getSupportedCountries() {
        if (supportedCountriesCache != null) return supportedCountriesCache;

        Locale ui = AppLocale.uiLocale();
        List<CountryCode> countries = new ArrayList<>();
        for (String region : phoneUtil.getSupportedRegions()) {
            int code = phoneUtil.getCountryCodeForRegion(region);
            String name = new Locale("", region).getDisplayCountry(ui);
            countries.add(new CountryCode(region, code, name));
        }
        Collator collator = Collator.getInstance(ui);
        countries.sort((a, b) -> collator.compare(a.getName(), b.getName()));

        String home = homeRegion();
        countries.stream().filter(c -> c.getRegionCode().equals(home)).findFirst().ifPresent(h -> {
            countries.remove(h);
            countries.add(0, h);
        });

        supportedCountriesCache = Collections.unmodifiableList(countries);
        return supportedCountriesCache;
    }

    public static CountryCode country(String regionCode) {
        for (CountryCode c : getSupportedCountries()) {
            if (c.getRegionCode().equals(regionCode)) return c;
        }
        return null;
    }

    @Getter
    public static class CountryCode {
        private final String regionCode;
        private final int phoneCode;
        private final String name;
        /** Arama anahtarı: küçük harf, Türkçe harfler katlanmış ("Türkiye" → "turkiye"). */
        private final String searchKey;

        public CountryCode(String regionCode, int phoneCode, String name) {
            this.regionCode = regionCode;
            this.phoneCode = phoneCode;
            this.name = name;
            this.searchKey = fold(name) + " " + regionCode.toLowerCase(Locale.ROOT);
        }

        public String getFlagPath() {
            return "icons/flags/" + regionCode.toLowerCase(Locale.ROOT) + ".svg";
        }

        /** Ad, ISO kodu ya da alan koduyla eşleşir ("alm", "de", "49", "+49"). */
        public boolean matches(String query) {
            String q = fold(query.strip());
            if (q.isEmpty()) return true;
            String digits = q.startsWith("+") ? q.substring(1) : q;
            if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit)) {
                return String.valueOf(phoneCode).startsWith(digits);
            }
            return searchKey.contains(q);
        }

        @Override
        public String toString() {
            return "+" + phoneCode + " " + name;
        }
    }

    /** Arama için harf katlama: büyük/küçük ve Türkçe harf farkı yok sayılır. */
    public static String fold(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toLowerCase(new Locale("tr", "TR")).toCharArray()) {
            sb.append(switch (c) {
                case 'ı' -> 'i';
                case 'ş' -> 's';
                case 'ç' -> 'c';
                case 'ğ' -> 'g';
                case 'ö' -> 'o';
                case 'ü' -> 'u';
                case 'â' -> 'a';
                case 'î' -> 'i';
                case 'û' -> 'u';
                default -> c;
            });
        }
        return sb.toString();
    }
}
