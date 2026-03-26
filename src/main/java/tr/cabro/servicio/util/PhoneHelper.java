package tr.cabro.servicio.util;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.google.i18n.phonenumbers.AsYouTypeFormatter; // YENİ
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.Getter;
import tr.cabro.servicio.service.exception.ValidationException;

import java.util.*;

public class PhoneHelper {

    private static final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    // ... (normalize ve formatForDisplay metodları aynen kalıyor) ...
    // ... (normalize metodu buraya gelecek)
    // ... (formatForDisplay metodu buraya gelecek)

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

    public static String formatForDisplay(String e164Number) {
        if (e164Number == null || e164Number.isEmpty()) return "";
        try {
            if (!e164Number.startsWith("+")) e164Number = "+" + e164Number;
            Phonenumber.PhoneNumber number = phoneUtil.parse(e164Number, null);
            return phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        } catch (NumberParseException e) {
            return e164Number;
        }
    }

    // --- YENİ EKLENEN METODLAR ---

    /**
     * Seçilen ülke için "Yazarken Formatlama" nesnesi döndürür.
     */
    public static AsYouTypeFormatter getAsYouTypeFormatter(String regionCode) {
        return phoneUtil.getAsYouTypeFormatter(regionCode);
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

    public static List<CountryCode> getSupportedCountries() {
        List<CountryCode> countries = new ArrayList<>();
        for (String region : phoneUtil.getSupportedRegions()) {
            int code = phoneUtil.getCountryCodeForRegion(region);
            String name = new Locale("", region).getDisplayCountry(new Locale("tr", "TR"));
            FlatSVGIcon flag = getFlagIcon(region);
            countries.add(new CountryCode(region, code, name, flag));
        }
        countries.sort(Comparator.comparing(CountryCode::getName));

        // Türkiye'yi başa al
        countries.stream().filter(c -> c.getRegionCode().equals("TR")).findFirst().ifPresent(tr -> {
            countries.remove(tr);
            countries.add(0, tr);
        });
        return countries;
    }

    private static FlatSVGIcon getFlagIcon(String countryCode) {
        return new FlatSVGIcon("icons/flags/" + countryCode.toLowerCase() + ".svg", 0.02f);
    }

    @Getter
    public static class CountryCode {
        private final String regionCode;
        private final int phoneCode;
        private final String name;
        private final FlatSVGIcon flag;

        public CountryCode(String regionCode, int phoneCode, String name, FlatSVGIcon flag) {
            this.regionCode = regionCode;
            this.phoneCode = phoneCode;
            this.name = name;
            this.flag = flag;
        }

        @Override
        public String toString() {
            return flag + " +" + phoneCode + " " + name;
        }
    }
}