package tr.cabro.servicio.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Format {

    private static final Locale TURKISH_LOCALE = new Locale("tr", "TR");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

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
        return PhoneHelper.formatForDisplay(phoneNumber);
    }
}
