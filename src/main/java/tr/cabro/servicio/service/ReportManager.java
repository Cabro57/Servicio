package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.ReportRepository;
import tr.cabro.servicio.model.dto.ChartDataDto;
import tr.cabro.servicio.model.dto.SummaryCardDto;

import java.util.List;
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

    /** Borsa/Çizgi Grafiği için Aylık Ciro (Gelir) Trendi */
    public CompletableFuture<List<ChartDataDto>> getMonthlyRevenueTrend(String startDate, String endDate) {
        return CompletableFuture.supplyAsync(() -> repository.getMonthlyRevenueTrend(startDate, endDate));
    }

    /** Borsa/Çizgi Grafiği için Aylık Net Kâr Trendi */
    public CompletableFuture<List<ChartDataDto>> getMonthlyProfitTrend(String startDate, String endDate) {
        return CompletableFuture.supplyAsync(() -> repository.getMonthlyProfitTrend(startDate, endDate));
    }
}