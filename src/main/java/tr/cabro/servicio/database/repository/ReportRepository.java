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
            "  COALESCE(SUM(si.unit_price * si.quantity) - " +
            "           SUM(CASE WHEN si.item_type = 'PART' THEN si.purchase_price * si.quantity ELSE 0 END), 0.0) AS total_profit " +
            "FROM work_orders s " +
            "LEFT JOIN work_order_items si ON s.id = si.service_id " +
            "WHERE date(s.created_at) BETWEEN date(:startDate) AND date(:endDate)")
    SummaryCardDto getSummaryCards(@Bind("startDate") String startDate, @Bind("endDate") String endDate);


    // =========================================================================
    // 2. PASTA GRAFİKLERİ (Cihazlar devices tablosundan JOIN ile geliyor)
    // =========================================================================
    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT dt.name AS label, COUNT(s.id) AS value " + // d.device_type_id -> dt.name
            "FROM work_orders s " +
            "JOIN devices d ON s.device_id = d.id " +
            "JOIN device_types dt ON d.device_type_id = dt.id " +
            "WHERE date(s.created_at) BETWEEN date(:startDate) AND date(:endDate) " +
            "GROUP BY dt.name ORDER BY value DESC") // GROUP BY dt.name
    List<ChartDataDto> getDeviceTypeDistribution(@Bind("startDate") String startDate, @Bind("endDate") String endDate);

    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT db.name AS label, COUNT(s.id) AS value " + // d.brand_id -> db.name
            "FROM work_orders s " +
            "JOIN devices d ON s.device_id = d.id " +
            "JOIN device_brands db ON d.brand_id = db.id " +
            "WHERE date(s.created_at) BETWEEN date(:startDate) AND date(:endDate) " +
            "GROUP BY db.name ORDER BY value DESC") // GROUP BY db.name
    List<ChartDataDto> getBrandDistribution(@Bind("startDate") String startDate, @Bind("endDate") String endDate);


    // =========================================================================
    // 3. ZAMAN SERİSİ (TREND) GRAFİKLERİ
    // =========================================================================

    // Aylık Ciro (Gelir) Trendi
    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT strftime('%Y-%m', s.created_at) AS label, " +
            "  COALESCE(SUM(si.unit_price * si.quantity), 0.0) AS value " +
            "FROM work_orders s " +
            "LEFT JOIN work_order_items si ON s.id = si.service_id " +
            "WHERE date(s.created_at) BETWEEN date(:startDate) AND date(:endDate) " +
            "GROUP BY label " +
            "ORDER BY label ASC")
    List<ChartDataDto> getMonthlyRevenueTrend(@Bind("startDate") String startDate, @Bind("endDate") String endDate);

    // Aylık Kâr Trendi
    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT strftime('%Y-%m', s.created_at) AS label, " +
            "  COALESCE(SUM(si.unit_price * si.quantity) - " +
            "           SUM(CASE WHEN si.item_type = 'PART' THEN si.purchase_price * si.quantity ELSE 0 END), 0.0) AS value " +
            "FROM work_orders s " +
            "LEFT JOIN work_order_items si ON s.id = si.service_id " +
            "WHERE date(s.created_at) BETWEEN date(:startDate) AND date(:endDate) " +
            "GROUP BY label " +
            "ORDER BY label ASC")
    List<ChartDataDto> getMonthlyProfitTrend(@Bind("startDate") String startDate, @Bind("endDate") String endDate);
}