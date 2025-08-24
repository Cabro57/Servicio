package tr.cabro.servicio.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class FormatUtils {

    private static final Locale TURKISH_LOCALE = new Locale("tr", "TR");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private FormatUtils() {
        // static utility class, prevent instantiation
    }

    public static String formatPrice(double price) {
        return String.format(TURKISH_LOCALE, "%,.2f ₺", price);
    }

    public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DATE_FORMATTER);
    }

    public static String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DATE_FORMATTER);
    }

    public static String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 10) return phoneNumber;
        return String.format("%s %s %s %s",
                phoneNumber.substring(0, 3),
                phoneNumber.substring(3, 6),
                phoneNumber.substring(6, 8),
                phoneNumber.substring(8, 10));
    }
}
