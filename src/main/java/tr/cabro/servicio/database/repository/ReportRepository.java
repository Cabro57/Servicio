package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import tr.cabro.servicio.model.dto.ChartDataDto;
import tr.cabro.servicio.model.dto.SummaryCardDto;

import java.util.List;

public interface ReportRepository {

    // =========================================================================
    // 1. DÖRT ANA KART İSTATİSTİĞİ (Tarih Filtreli)
    // =========================================================================
    @RegisterBeanMapper(SummaryCardDto.class)
    @SqlQuery("SELECT " +
            "  COUNT(DISTINCT s.id) AS total_records, " +
            "  COUNT(DISTINCT CASE WHEN s.service_status NOT IN ('DELIVERED', 'RETURN') THEN s.id END) AS active_records, " +
            "  COALESCE(SUM(si.unit_price * si.quantity), 0.0) AS total_revenue, " +
            "  COALESCE(SUM(CASE WHEN si.item_type = 'PART' THEN si.purchase_price * si.quantity ELSE 0 END), 0.0) AS total_expense, " +
            "  COALESCE(SUM(si.unit_price * si.quantity) - SUM(CASE WHEN si.item_type = 'PART' THEN si.purchase_price * si.quantity ELSE 0 END), 0.0) AS total_profit " +
            "FROM services s " +
            "LEFT JOIN service_items si ON s.id = si.service_id " +
            "WHERE date(s.created_at) BETWEEN date(:startDate) AND date(:endDate)")
    SummaryCardDto getSummaryCards(@Bind("startDate") String startDate, @Bind("endDate") String endDate);


    // =========================================================================
    // 2. PASTA GRAFİKLERİ (Tarih Filtreli Cihaz Türü ve Marka Dağılımı)
    // =========================================================================
    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT d.device_type AS label, COUNT(s.id) AS value " +
            "FROM services s " +
            "JOIN devices d ON s.device_id = d.id " +
            "WHERE date(s.created_at) BETWEEN date(:startDate) AND date(:endDate) " +
            "GROUP BY d.device_type ORDER BY value DESC")
    List<ChartDataDto> getDeviceTypeDistribution(@Bind("startDate") String startDate, @Bind("endDate") String endDate);

    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT d.brand AS label, COUNT(s.id) AS value " +
            "FROM services s " +
            "JOIN devices d ON s.device_id = d.id " +
            "WHERE date(s.created_at) BETWEEN date(:startDate) AND date(:endDate) " +
            "GROUP BY d.brand ORDER BY value DESC")
    List<ChartDataDto> getBrandDistribution(@Bind("startDate") String startDate, @Bind("endDate") String endDate);


    // =========================================================================
    // 3. BORSA (ZAMAN SERİSİ) GRAFİKLERİ İÇİN (Tarih Filtreli Aylık/Günlük Kazanç)
    // =========================================================================

    // Gelen Tarih aralığındaki AYLIK Ciro (Gelir) Trendini verir.
    // X ekseni = label (Örn: 2026-01), Y ekseni = value (Örn: 15000 TL)
    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT strftime('%Y-%m', s.created_at) AS label, " +
            "  COALESCE(SUM(si.unit_price * si.quantity), 0.0) AS value " +
            "FROM services s " +
            "LEFT JOIN service_items si ON s.id = si.service_id " +
            "WHERE date(s.created_at) BETWEEN date(:startDate) AND date(:endDate) " +
            "GROUP BY strftime('%Y-%m', s.created_at) " +
            "ORDER BY label ASC")
    List<ChartDataDto> getMonthlyRevenueTrend(@Bind("startDate") String startDate, @Bind("endDate") String endDate);

    // Aynı mantıkla AYLIK Kâr (Profit) Trendi
    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT strftime('%Y-%m', s.created_at) AS label, " +
            "  COALESCE(SUM(si.unit_price * si.quantity) - SUM(CASE WHEN si.item_type = 'PART' THEN si.purchase_price * si.quantity ELSE 0 END), 0.0) AS value " +
            "FROM services s " +
            "LEFT JOIN service_items si ON s.id = si.service_id " +
            "WHERE date(s.created_at) BETWEEN date(:startDate) AND date(:endDate) " +
            "GROUP BY strftime('%Y-%m', s.created_at) " +
            "ORDER BY label ASC")
    List<ChartDataDto> getMonthlyProfitTrend(@Bind("startDate") String startDate, @Bind("endDate") String endDate);
}