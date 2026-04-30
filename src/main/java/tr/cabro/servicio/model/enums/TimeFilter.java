package tr.cabro.servicio.model.enums;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public enum TimeFilter {
    DAY_1("1G",  1, 0, 0, "hour", "%Y-%m-%dT%H"),
    DAY_3("3G",  3, 0, 0, "hour", "%Y-%m-%dT%H"),
    WEEK_1("1H", 7, 0, 0, "day",   "%Y-%m-%d"),
    MONTH_1("1A",0, 1, 0, "week",  "%Y-%W"),
    MONTH_3("3A",0, 3, 0, "month", "%Y-%m"),
    MONTH_6("6A",0, 6, 0, "month", "%Y-%m"),
    YEAR_1("1Y", 0, 0, 1, "month", "%Y-%m"),
    ALL_TIME("Tümü", 0, 0, 0, "month", "%Y-%m") {
        @Override
        public LocalDate[] getRanges() {
            LocalDate start = LocalDate.of(2000, 1, 1);
            return new LocalDate[]{start, LocalDate.now(), start, start};
        }
    };

    private final String label;
    private final int days, months, years;
    private final String granularity;   // "day" | "week" | "month"
    private final String sqlFormat;     // SQLite strftime formatı

    TimeFilter(String label, int days, int months, int years, String granularity, String sqlFormat) {
        this.label = label;
        this.days = days;
        this.months = months;
        this.years = years;
        this.granularity = granularity;
        this.sqlFormat = sqlFormat;
    }

    public LocalDate[] getRanges() {
        LocalDate now = LocalDate.now();
        LocalDate currentStart = now.minusDays(days).minusMonths(months).minusYears(years);
        LocalDate prevEnd = currentStart;
        LocalDate prevStart = prevEnd.minusDays(days).minusMonths(months).minusYears(years);
        // Dönen dizi: [BuDönemBaşı, Bugün, GeçenDönemBaşı, GeçenDönemSonu]
        return new LocalDate[]{currentStart, now, prevStart, prevEnd};
    }

    @Override
    public String toString() {
        return label; // ToolBarSelection'da buton üzerinde yazacak metin
    }
}