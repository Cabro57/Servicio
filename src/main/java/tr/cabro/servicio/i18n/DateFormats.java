package tr.cabro.servicio.i18n;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Aktif {@link AppLocale#formatLocale()}'a göre tarih/saat pattern'i seçen merkezi sınıf.
 * <p>
 * {@code tr-TR} girdisi projede daha önce her yerde hardcode edilen pattern'lerle
 * birebir aynıdır — bölge ayarı değiştirilmediği sürece görünümde bir değişiklik olmaz.
 */
public final class DateFormats {

    private static final Map<String, String[]> PATTERNS = Map.of(
            "tr-TR", new String[]{"dd MMM yyyy", "dd MMM yyyy HH:mm"},
            "en-US", new String[]{"MMM dd, yyyy", "MMM dd, yyyy hh:mm a"},
            "en-GB", new String[]{"dd MMM yyyy", "dd MMM yyyy HH:mm"},
            "de-DE", new String[]{"dd.MM.yyyy", "dd.MM.yyyy HH:mm"}
    );

    private static final String[] DEFAULT_PATTERNS = PATTERNS.get("tr-TR");

    private DateFormats() {}

    /** Gün/ay/yıl gösterimi için formatlayıcı, ör. "13 Ağu 2026". */
    public static DateTimeFormatter shortDate() {
        return DateTimeFormatter.ofPattern(patterns()[0], AppLocale.formatLocale());
    }

    /** Gün/ay/yıl + saat gösterimi için formatlayıcı, ör. "13 Ağu 2026 14:30". */
    public static DateTimeFormatter dateTime() {
        return DateTimeFormatter.ofPattern(patterns()[1], AppLocale.formatLocale());
    }

    private static String[] patterns() {
        return PATTERNS.getOrDefault(AppLocale.formatLocale().toLanguageTag(), DEFAULT_PATTERNS);
    }
}
