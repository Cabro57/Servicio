package tr.cabro.servicio.util;

import java.util.regex.Pattern;

public class Validator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");

    /** Boş mu? */
    public static boolean isEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }

    /** E-posta geçerli mi? */
    public static boolean isValidEmail(String email) {
        return !isEmpty(email) && EMAIL_PATTERN.matcher(email).matches();
    }

    /** Sadece rakamlardan oluşuyor mu? */
    public static boolean isNumeric(String text) {
        return !isEmpty(text) && DIGITS_PATTERN.matcher(text).matches();
    }

    /** Belirtilen uzunlukta mı? */
    public static boolean hasLength(String text, int length) {
        return !isEmpty(text) && text.trim().length() == length;
    }

    /** Minimum uzunluk */
    public static boolean hasMinLength(String text, int minLength) {
        return !isEmpty(text) && text.trim().length() >= minLength;
    }
}
