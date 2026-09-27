package tr.cabro.servicio.reports;

import tr.cabro.servicio.application.component.chart.ColumnChart;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.util.Format;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Bir raporun ekrana, PDF'e ve CSV'ye aynı şekilde basılan içeriği: öne çıkan rakamlar,
 * zaman grafiği ve döküm tabloları. Rapor arka planda {@link ReportKind#build} ile bir kez
 * kurulur; üç çıktı da bu nesneden üretilir, böylece ekranda görülen ile dışa aktarılan aynıdır.
 */
public record Report(ReportKind kind, LocalDate from, LocalDate to, Bucket bucket,
                     List<Figure> figures, Chart chart, List<Table> tables) {

    /** Rakamın anlamı; ekranda renge, PDF'te kalınlığa dönüşür. */
    public enum Tone { PLAIN, SUCCESS, DANGER, WARNING, MUTED }

    /** Öne çıkan tek rakam: "Net kâr  12.450,00 ₺", altında soluk açıklama. */
    public record Figure(String label, String value, Tone tone, String hint) {}

    /** Kova başına seriler; {@code money} ise eksen ve ipucu para biçiminde yazılır. */
    public record Chart(String title, List<String> keys, List<ColumnChart.Series> bars,
                        ColumnChart.Series line, boolean stacked, boolean money) {}

    /** Sütun türü: hizalama, biçim ve renk kuralını belirler. */
    public enum Kind {
        TEXT, COUNT, MONEY,
        /** İşaretli tutar: artı başarı, eksi tehlike (net kâr). */
        MONEY_SIGN,
        /** Zaten sayılmış paradan düşen: yalnızca eksi tehlike (iade). */
        MONEY_NEGATIVE,
        /** Tahsil edilecek: artı uyarı (bakiye). */
        MONEY_OWED,
        PERCENT, DAYS, DATE;

        public boolean numeric() {
            return this != TEXT && this != DATE;
        }

        /** Ekran ve PDF metni. */
        public String format(Object value) {
            if (value == null) return "—";
            switch (this) {
                case TEXT:
                    return value.toString();
                case COUNT: {
                    double d = ((Number) value).doubleValue();
                    NumberFormat nf = NumberFormat.getIntegerInstance(AppLocale.uiLocale());
                    return nf.format(Math.round(d));
                }
                case MONEY:
                case MONEY_SIGN:
                case MONEY_NEGATIVE:
                case MONEY_OWED:
                    return Format.formatPrice(BigDecimal.valueOf(((Number) value).doubleValue()));
                case PERCENT:
                    return percent(((Number) value).doubleValue());
                case DAYS:
                    return days(((Number) value).doubleValue());
                case DATE: {
                    LocalDate d = value instanceof LocalDate ld ? ld : LocalDate.parse(value.toString().substring(0, 10));
                    return d.format(DateTimeFormatter.ofPattern("d MMM yyyy", AppLocale.uiLocale()));
                }
                default:
                    return value.toString();
            }
        }
    }

    public record Col(String name, Kind kind) {}

    /**
     * Döküm tablosu. {@code totals} null değilse en altta toplam satırı olarak basılır.
     * {@code customerIds} doluysa satır tıklanınca o müşterinin sayfası açılır.
     */
    public record Table(String title, String note, List<Col> cols, List<Object[]> rows, Object[] totals,
                        String emptyText, long[] customerIds) {
        public Table(String title, String note, List<Col> cols, List<Object[]> rows, Object[] totals, String emptyText) {
            this(title, note, cols, rows, totals, emptyText, null);
        }
    }

    // ── Biçim yardımcıları ──────────────────────────────────────────────────

    /** "%12,5", eksi ise "-%6,4"; hesaplanamıyorsa "—". */
    public static String percent(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) return "—";
        NumberFormat nf = NumberFormat.getNumberInstance(AppLocale.uiLocale());
        nf.setMaximumFractionDigits(Math.abs(ratio) < 0.1 ? 1 : 0);
        return (ratio < 0 ? "-%" : "%") + nf.format(Math.abs(ratio) * 100);
    }

    /** "2,4 gün", "5 saat"; veri yoksa "—". */
    public static String days(double d) {
        if (Double.isNaN(d)) return "—";
        if (d < 1) return Math.max(1, Math.round(d * 24)) + " saat";
        NumberFormat nf = NumberFormat.getNumberInstance(AppLocale.uiLocale());
        nf.setMaximumFractionDigits(d < 10 ? 1 : 0);
        return nf.format(d) + " gün";
    }

    public static String money(double v) {
        return Format.formatPrice(BigDecimal.valueOf(v));
    }

    /** Dönemin okunur adı: "1 Eyl – 27 Eyl 2026" ya da tek gün. */
    public String periodText() {
        DateTimeFormatter withYear = DateTimeFormatter.ofPattern("d MMM yyyy", AppLocale.uiLocale());
        DateTimeFormatter noYear = DateTimeFormatter.ofPattern("d MMM", AppLocale.uiLocale());
        if (from.equals(to)) return from.format(withYear);
        return (from.getYear() == to.getYear() ? from.format(noYear) : from.format(withYear)) + " – " + to.format(withYear);
    }
}
