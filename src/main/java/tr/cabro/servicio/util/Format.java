package tr.cabro.servicio.util;

import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.i18n.DateFormats;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

public class Format {

    /** Serbest metin tarih ayrıştırması her zaman bu sabit biçimle yapılır — gösterim bölgesinden bağımsız. */
    private static final Locale PARSE_LOCALE = new Locale("tr", "TR");

    public static String formatPrice(BigDecimal price) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(AppLocale.formatLocale()));
        return df.format(price) + " " + AppLocale.currencySymbol();
    }

    public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DateFormats.shortDate());
    }

    public static String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DateFormats.shortDate());
    }

    public static LocalDate formatDate(String date) {
        if (date == null) return LocalDate.now();
        return LocalDate.parse(date, java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", PARSE_LOCALE));
    }

    public static String formatPhoneNumber(String phoneNumber) {
        return PhoneHelper.formatForDisplay(phoneNumber);
    }
}
