package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.AnalyticsRepository;
import tr.cabro.servicio.database.repository.ReportRepository;
import tr.cabro.servicio.model.dto.SummaryCardDto;

import java.util.concurrent.CompletableFuture;

public class ReportManager {

    private final ReportRepository repository;
    private final AnalyticsRepository analytics;

    public ReportManager(ReportRepository repository, AnalyticsRepository analytics) {
        this.repository = repository;
        this.analytics = analytics;
    }

    /**
     * Ana sayfa grafikleri ve Raporlar sayfası için analitik sorguyu arka planda çalıştırır.
     * Birden çok sorgu tek görevde birleştirilebilir, böylece sonuçlar aynı anda gelir.
     */
    public <T> CompletableFuture<T> analytics(java.util.function.Function<AnalyticsRepository, T> query) {
        return CompletableFuture.supplyAsync(() -> query.apply(analytics));
    }

    /**
     * Dashboard üstündeki 4 adet özet bilgi kartını doldurur.
     * @param startDate "YYYY-MM-DD"
     * @param endDate "YYYY-MM-DD"
     */
    public CompletableFuture<SummaryCardDto> getDashboardSummaryCards(String startDate, String endDate) {
        return CompletableFuture.supplyAsync(() -> repository.getSummaryCards(startDate, endDate));
    }
}
