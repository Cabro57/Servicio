package tr.cabro.servicio.util;

import java.util.regex.Pattern;

public class Validator {

    // Temel Regex Pattern'leri
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern DIGITS_PATTERN =
            Pattern.compile("\\d+");

    /* --- STRING KONTROLLERİ --- */

    /** Boş mu? (null veya sadece boşluklar) */
    public static boolean isEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }

    /** Minimum uzunluk sağlanmış mı? */
    public static boolean hasMinLength(String text, int minLength) {
        return !isEmpty(text) && text.trim().length() >= minLength;
    }

    /** Belirli uzunlukta mı? */
    public static boolean hasLength(String text, int length) {
        return !isEmpty(text) && text.trim().length() == length;
    }

    /** Sadece rakamlardan mı oluşuyor? */
    public static boolean isNumeric(String text) {
        return !isEmpty(text) && DIGITS_PATTERN.matcher(text).matches();
    }

    /** E-posta formatı geçerli mi? */
    public static boolean isValidEmail(String email) {
        return !isEmpty(email) && EMAIL_PATTERN.matcher(email).matches();
    }

    /** Maksimum uzunluk aşıldı mı? */
    public static boolean exceedsMaxLength(String text, int maxLength) {
        return !isEmpty(text) && text.trim().length() > maxLength;
    }


    /* --- SAYI KONTROLLERİ --- */

    /** Negatif mi? */
    public static boolean isNegative(Number number) {
        return number != null && number.doubleValue() < 0;
    }

    /** Pozitif mi? */
    public static boolean isPositive(Number number) {
        return number != null && number.doubleValue() > 0;
    }

    /** Sıfır mı? */
    public static boolean isZero(Number number) {
        return number != null && number.doubleValue() == 0;
    }

    /** Belirtilen aralıkta mı? (dahil) */
    public static boolean isInRange(Number number, double min, double max) {
        if (number == null) return false;
        double value = number.doubleValue();
        return value >= min && value <= max;
    }


    /* --- ÖZEL KONTROLLER --- */

    /** Null mu? */
    public static boolean isNull(Object obj) {
        return obj == null;
    }

    /** Null değil mi? */
    public static boolean isNotNull(Object obj) {
        return obj != null;
    }
}
