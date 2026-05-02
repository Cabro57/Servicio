package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.ReportRepository;
import tr.cabro.servicio.model.dto.ChartDataDto;
import tr.cabro.servicio.model.dto.SummaryCardDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReportManager {

    private final ReportRepository repository;

    public ReportManager(ReportRepository repository) {
        this.repository = repository;
    }

    /**
     * Dashboard üstündeki 4 adet özet bilgi kartını doldurur.
     * @param startDate "YYYY-MM-DD"
     * @param endDate "YYYY-MM-DD"
     */
    public CompletableFuture<SummaryCardDto> getDashboardSummaryCards(String startDate, String endDate) {
        return CompletableFuture.supplyAsync(() -> repository.getSummaryCards(startDate, endDate));
    }

    /** Cihaz Türlerine göre pasta grafik verisi (Örn: %40 Telefon, %60 Bilgisayar) */
    public CompletableFuture<List<ChartDataDto>> getDeviceTypePieChart(String startDate, String endDate) {
        return CompletableFuture.supplyAsync(() -> repository.getDeviceTypeDistribution(startDate, endDate));
    }

    /** Markalara göre pasta grafik verisi (Örn: Apple 15, Samsung 10) */
    public CompletableFuture<List<ChartDataDto>> getBrandPieChart(String startDate, String endDate) {
        return CompletableFuture.supplyAsync(() -> repository.getBrandDistribution(startDate, endDate));
    }

    public CompletableFuture<List<ChartDataDto>> getRevenueTrend(String sqlFormat, String startDate, String endDate, boolean useEffectiveRange) {
        return CompletableFuture.supplyAsync(() -> {
            List<ChartDataDto> data = repository.getRevenueTrend(sqlFormat, startDate, endDate);
            if (data == null || data.isEmpty()) return data;

            String effectiveStart = useEffectiveRange ? data.get(0).getLabel().split("T")[0] : startDate;
            String effectiveEnd   = useEffectiveRange ? data.get(data.size() - 1).getLabel().split("T")[0] : endDate;

            return fillMissingPeriods(data, sqlFormat, effectiveStart, effectiveEnd);
        });
    }

    public CompletableFuture<List<ChartDataDto>> getProfitTrend(String sqlFormat, String startDate, String endDate, boolean useEffectiveRange) {
        return CompletableFuture.supplyAsync(() -> {
            List<ChartDataDto> data = repository.getProfitTrend(sqlFormat, startDate, endDate);

            if (data == null || data.isEmpty()) return data;

            String effectiveStart = useEffectiveRange ? data.get(0).getLabel().split("T")[0] : startDate;
            String effectiveEnd   = useEffectiveRange ? data.get(data.size() - 1).getLabel().split("T")[0] : endDate;

            return fillMissingPeriods(data, sqlFormat, effectiveStart, effectiveEnd);
        });
    }

    private List<ChartDataDto> fillMissingPeriods(List<ChartDataDto> data, String sqlFormat, String startDate, String endDate) {
        if (data == null || data.isEmpty()) {
            // Veri hiç yoksa bile boş periyotları doldur
            data = new ArrayList<>();
        }

        Map<String, ChartDataDto> dataMap = new LinkedHashMap<>();
        for (ChartDataDto dto : data) {
            dataMap.put(dto.getLabel(), dto);
        }

        List<String> allPeriods = generateAllPeriods(sqlFormat, startDate, endDate);

        List<ChartDataDto> result = new ArrayList<>();
        for (String period : allPeriods) {
            if (dataMap.containsKey(period)) {
                result.add(dataMap.get(period));
            } else {
                ChartDataDto empty = new ChartDataDto();
                empty.setLabel(period);
                empty.setValue(BigDecimal.ZERO);
                result.add(empty);
            }
        }
        return result;
    }

    private List<String> generateAllPeriods(String sqlFormat, String startDate, String endDate) {
        List<String> periods = new ArrayList<>();

        if ("%Y-%m-%dT%H".equals(sqlFormat)) {
            // startDate: "2026-04-29", endDate: "2026-05-02"
            // Başlangıç saati = şu anki saat (borsa gibi simetrik aralık)
            int currentHour = java.time.LocalTime.now().getHour();

            LocalDate startDay = LocalDate.parse(startDate);
            LocalDate endDay = LocalDate.parse(endDate);

            LocalDate day = startDay;
            while (!day.isAfter(endDay)) {
                int fromHour = day.equals(startDay) ? currentHour : 0;
                int toHour   = day.equals(endDay)   ? currentHour : 23;
                for (int hour = fromHour; hour <= toHour; hour++) {
                    periods.add(String.format("%sT%02d", day.toString(), hour));
                }
                day = day.plusDays(1);
            }

        } else if ("%Y-%m-%d".equals(sqlFormat)) {
            LocalDate day = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            while (!day.isAfter(end)) {
                periods.add(day.toString());
                day = day.plusDays(1);
            }

        } else if ("%Y-%W".equals(sqlFormat)) {
            String[] startParts = startDate.split("-");
            String[] endParts = endDate.split("-");
            LocalDate day = LocalDate.of(Integer.parseInt(startParts[0]), 1, 1)
                    .with(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear(), Integer.parseInt(startParts[1].length() == 2 ? startParts[1] : "0" + startParts[1]))
                    .with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1);
            LocalDate endDay = LocalDate.of(Integer.parseInt(endParts[0]), 1, 1)
                    .with(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear(), Integer.parseInt(endParts[1]))
                    .with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1);
            while (!day.isAfter(endDay)) {
                int week = day.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
                periods.add(String.format("%d-%02d", day.getYear(), week));
                day = day.plusWeeks(1);
            }

        } else {
            // Aylık — ALL_TIME dahil, startDate ilk verinin ayı olarak geliyor
            String[] startParts = startDate.split("-");
            String[] endParts = endDate.split("-");
            LocalDate month = LocalDate.of(Integer.parseInt(startParts[0]), Integer.parseInt(startParts[1]), 1);
            LocalDate endMonth = LocalDate.of(Integer.parseInt(endParts[0]), Integer.parseInt(endParts[1]), 1);
            while (!month.isAfter(endMonth)) {
                periods.add(String.format("%d-%02d", month.getYear(), month.getMonthValue()));
                month = month.plusMonths(1);
            }
        }

        return periods;
    }
}