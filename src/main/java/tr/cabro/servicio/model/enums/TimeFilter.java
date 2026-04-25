package tr.cabro.servicio.model.enums;

import java.time.LocalDate;

public enum  TimeFilter {
    DAY_1("1G", 1, 0, 0),
    DAY_3("3G", 3, 0, 0),
    WEEK_1("1H", 7, 0, 0),
    MONTH_1("1A", 0, 1, 0),
    MONTH_3("3A", 0, 3, 0),
    MONTH_6("6A", 0, 6, 0),
    YEAR_1("1Y", 0, 0, 1),
    ALL_TIME("Tümü", 0, 0, 0) {
        @Override
        public LocalDate[] getRanges() {
            LocalDate start = LocalDate.of(2000, 1, 1);
            return new LocalDate[]{start, LocalDate.now(), start, start};
        }
    };

    private final String label;
    private final int days;
    private final int months;
    private final int years;

    TimeFilter(String label, int days, int months, int years) {
        this.label = label;
        this.days = days;
        this.months = months;
        this.years = years;
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