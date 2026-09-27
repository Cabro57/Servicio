package tr.cabro.servicio.reports;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grafik ve rapor tablolarının zaman kırılımı. Her kova SQLite'ta bir anahtar ifadesine
 * dönüşür; anahtarlar sıralanabilir metinlerdir (saat "2026-09-27T14", gün "2026-09-27",
 * hafta o haftanın pazartesisi, ay ayın ilk günü). Boş kovalar {@link #keys} ile doldurulur,
 * böylece grafikte hareketsiz günler de sıfır olarak görünür.
 */
public enum Bucket {
    HOUR("saatlik"),
    DAY("günlük"),
    WEEK("haftalık"),
    MONTH("aylık");

    private final String adjective;

    Bucket(String adjective) {
        this.adjective = adjective;
    }

    /** "günlük", "haftalık"… — özet satırında kırılımı anlatır. */
    public String adjective() {
        return adjective;
    }

    /** Aralığın uzunluğuna göre okunur sayıda sütun veren kırılım. */
    public static Bucket forRange(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 2) return HOUR;
        if (days <= 45) return DAY;
        if (days <= 190) return WEEK;
        return MONTH;
    }

    /**
     * Dar alan için: {@code maxColumns} sütunu aşmayan en ince kırılım (ana sayfa kartları).
     * Ör. son 1 ay 31 gün yerine 5 hafta çizilir.
     */
    public static Bucket forRange(LocalDate from, LocalDate to, int maxColumns) {
        for (Bucket b : values()) {
            if (b.keys(from, to).size() <= maxColumns) return b;
        }
        return MONTH;
    }

    /**
     * Zaman damgası sütununu kova anahtarına çeviren SQLite ifadesi. Sütun adı yalnızca kod
     * içinden verilir (kullanıcı girdisi değil), bu yüzden metin olarak eklenmesi güvenlidir.
     */
    public String sql(String column) {
        switch (this) {
            case HOUR:
                return "strftime('%Y-%m-%dT%H', " + column + ")";
            case DAY:
                return "date(" + column + ")";
            case WEEK:
                // 6 gün geri gidip bir sonraki pazartesiye ilerlemek = o günün haftasının pazartesisi.
                return "date(" + column + ", '-6 days', 'weekday 1')";
            default:
                return "strftime('%Y-%m-01', " + column + ")";
        }
    }

    /** Aralıktaki tüm kova anahtarları, sırayla. */
    public List<String> keys(LocalDate from, LocalDate to) {
        List<String> keys = new ArrayList<>();
        switch (this) {
            case HOUR: {
                for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                    for (int h = 0; h < 24; h++) keys.add(d + "T" + (h < 10 ? "0" + h : String.valueOf(h)));
                }
                break;
            }
            case DAY:
                for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) keys.add(d.toString());
                break;
            case WEEK:
                for (LocalDate d = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); !d.isAfter(to); d = d.plusWeeks(1)) {
                    keys.add(d.toString());
                }
                break;
            default:
                for (LocalDate d = from.withDayOfMonth(1); !d.isAfter(to); d = d.plusMonths(1)) keys.add(d.toString());
                break;
        }
        return keys;
    }

    /** Kovanın kapsadığı ilk gün. */
    public LocalDate startOf(String key) {
        return this == HOUR ? LocalDate.parse(key.substring(0, 10)) : LocalDate.parse(key);
    }

    /** Kovanın kapsadığı son gün. */
    public LocalDate endOf(String key) {
        LocalDate start = startOf(key);
        switch (this) {
            case WEEK:
                return start.plusDays(6);
            case MONTH:
                return start.with(TemporalAdjusters.lastDayOfMonth());
            default:
                return start;
        }
    }

    /** Eksen etiketi: kısa ("14", "27", "22 Eyl", "Eyl"). */
    public String shortLabel(String key, Locale locale) {
        switch (this) {
            case HOUR:
                return key.substring(11) + ":00";
            case DAY:
                return String.valueOf(LocalDate.parse(key).getDayOfMonth());
            case WEEK:
                return LocalDate.parse(key).format(DateTimeFormatter.ofPattern("d MMM", locale));
            default: {
                LocalDate d = LocalDate.parse(key);
                String month = d.format(DateTimeFormatter.ofPattern("MMM", locale));
                return d.getMonthValue() == 1 ? month + " " + (d.getYear() % 100) : month;
            }
        }
    }

    /** İpucu ve tablo etiketi: tam ("27 Eyl Cmt 14:00", "22–28 Eyl 2026", "Eylül 2026"). */
    public String longLabel(String key, Locale locale) {
        switch (this) {
            case HOUR: {
                LocalDateTime t = LocalDate.parse(key.substring(0, 10)).atTime(Integer.parseInt(key.substring(11)), 0);
                return t.format(DateTimeFormatter.ofPattern("d MMM EEE, HH:00", locale));
            }
            case DAY:
                return LocalDate.parse(key).format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", locale));
            case WEEK: {
                LocalDate s = LocalDate.parse(key);
                LocalDate e = s.plusDays(6);
                String left = s.getMonth() == e.getMonth()
                        ? String.valueOf(s.getDayOfMonth())
                        : s.format(DateTimeFormatter.ofPattern("d MMM", locale));
                return left + " – " + e.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale));
            }
            default:
                return LocalDate.parse(key).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale));
        }
    }
}
