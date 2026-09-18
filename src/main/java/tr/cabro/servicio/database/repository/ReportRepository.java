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
    // total_records/active_records BİLİNÇLİ OLARAK servis-only kalır ("Toplam Servis" kartı) —
    // ciro/gider/kâr ise POS satışlarını da (sales.total_amount, RETURN'lerde zaten negatif)
    // skaler alt sorgularla dahil eder, aksi halde POS cirosu dashboard'da hiç görünmezdi.
    @RegisterBeanMapper(SummaryCardDto.class)
    @SqlQuery("SELECT " +
            "  COUNT(DISTINCT s.id) AS total_records, " +
            "  COUNT(DISTINCT CASE WHEN s.service_status NOT IN ('DELIVERED', 'RETURN') THEN s.id END) AS active_records, " +
            "  COALESCE(SUM(si.unit_price * si.quantity), 0.0) + COALESCE((" +
            "      SELECT SUM(sa.total_amount) FROM sales sa WHERE sa.is_deleted = 0 " +
            "      AND sa.sale_date >= :startDate AND sa.sale_date < date(:endDate, '+1 day')" +
            "  ), 0.0) AS total_revenue, " +
            "  COALESCE(SUM(CASE WHEN si.item_type = 'PART' THEN si.purchase_price * si.quantity ELSE 0 END), 0.0) + COALESCE((" +
            "      SELECT SUM(sai.purchase_price * sai.quantity) FROM sale_items sai JOIN sales sa2 ON sa2.id = sai.sale_id " +
            "      WHERE sa2.is_deleted = 0 AND sa2.sale_date >= :startDate AND sa2.sale_date < date(:endDate, '+1 day')" +
            "  ), 0.0) AS total_expense, " +
            "  (COALESCE(SUM(si.unit_price * si.quantity), 0.0) + COALESCE((" +
            "      SELECT SUM(sa.total_amount) FROM sales sa WHERE sa.is_deleted = 0 " +
            "      AND sa.sale_date >= :startDate AND sa.sale_date < date(:endDate, '+1 day')" +
            "  ), 0.0)) - (COALESCE(SUM(CASE WHEN si.item_type = 'PART' THEN si.purchase_price * si.quantity ELSE 0 END), 0.0) + COALESCE((" +
            "      SELECT SUM(sai.purchase_price * sai.quantity) FROM sale_items sai JOIN sales sa2 ON sa2.id = sai.sale_id " +
            "      WHERE sa2.is_deleted = 0 AND sa2.sale_date >= :startDate AND sa2.sale_date < date(:endDate, '+1 day')" +
            "  ), 0.0)) AS total_profit " +
            "FROM work_orders s " +
            "LEFT JOIN work_order_items si ON s.id = si.service_id " +
            "WHERE s.created_at >= :startDate AND s.created_at < date(:endDate, '+1 day')")
    SummaryCardDto getSummaryCards(@Bind("startDate") String startDate, @Bind("endDate") String endDate);


    // =========================================================================
    // 2. PASTA GRAFİKLERİ (Cihazlar devices tablosundan JOIN ile geliyor)
    // =========================================================================
    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT dt.name AS label, COUNT(s.id) AS value " + // d.device_type_id -> dt.name
            "FROM work_orders s " +
            "JOIN devices d ON s.device_id = d.id " +
            "JOIN device_types dt ON d.device_type_id = dt.id " +
            "WHERE s.created_at >= :startDate AND s.created_at < date(:endDate, '+1 day') " +
            "GROUP BY dt.name ORDER BY value DESC") // GROUP BY dt.name
    List<ChartDataDto> getDeviceTypeDistribution(@Bind("startDate") String startDate, @Bind("endDate") String endDate);

    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT db.name AS label, COUNT(s.id) AS value " + // d.brand_id -> db.name
            "FROM work_orders s " +
            "JOIN devices d ON s.device_id = d.id " +
            "JOIN device_brands db ON d.brand_id = db.id " +
            "WHERE s.created_at >= :startDate AND s.created_at < date(:endDate, '+1 day') " +
            "GROUP BY db.name ORDER BY value DESC") // GROUP BY db.name
    List<ChartDataDto> getBrandDistribution(@Bind("startDate") String startDate, @Bind("endDate") String endDate);


    // =========================================================================
    // 3. ZAMAN SERİSİ (TREND) GRAFİKLERİ
    // =========================================================================

    // Servis geliri + POS satış geliri (RETURN'lerde total_amount negatif olduğu için netleşir)
    // aynı periyoda (label) UNION ALL ile toplanır — ReportManager.fillMissingPeriods()'un
    // beklediği (label, value) şeklini bozmadan iki kaynağı birleştirir.
    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT label, SUM(value) AS value FROM (" +
            "  SELECT strftime(:sqlFormat, s.created_at) AS label, COALESCE(SUM(si.unit_price * si.quantity), 0.0) AS value " +
            "  FROM work_orders s LEFT JOIN work_order_items si ON s.id = si.service_id " +
            "  WHERE s.created_at >= :startDate AND s.created_at < date(:endDate, '+1 day') GROUP BY label " +
            "  UNION ALL " +
            "  SELECT strftime(:sqlFormat, sa.sale_date) AS label, COALESCE(SUM(sa.total_amount), 0.0) AS value " +
            "  FROM sales sa WHERE sa.is_deleted = 0 " +
            "  AND sa.sale_date >= :startDate AND sa.sale_date < date(:endDate, '+1 day') GROUP BY label " +
            ") combined GROUP BY label ORDER BY label ASC")
    List<ChartDataDto> getRevenueTrend(@Bind("sqlFormat") String sqlFormat,
                                       @Bind("startDate") String startDate,
                                       @Bind("endDate") String endDate);

    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT label, SUM(value) AS value FROM (" +
            "  SELECT strftime(:sqlFormat, s.created_at) AS label, " +
            "    COALESCE(SUM(si.unit_price * si.quantity), 0.0) - " +
            "    COALESCE(SUM(CASE WHEN si.item_type = 'PART' THEN si.purchase_price * si.quantity ELSE 0 END), 0.0) AS value " +
            "  FROM work_orders s LEFT JOIN work_order_items si ON s.id = si.service_id " +
            "  WHERE s.created_at >= :startDate AND s.created_at < date(:endDate, '+1 day') GROUP BY label " +
            "  UNION ALL " +
            "  SELECT strftime(:sqlFormat, sa.sale_date) AS label, " +
            "    COALESCE(SUM(sa.total_amount), 0.0) - COALESCE(SUM(" +
            "      (SELECT COALESCE(SUM(sai.purchase_price * sai.quantity), 0.0) FROM sale_items sai WHERE sai.sale_id = sa.id)" +
            "    ), 0.0) AS value " +
            "  FROM sales sa WHERE sa.is_deleted = 0 " +
            "  AND sa.sale_date >= :startDate AND sa.sale_date < date(:endDate, '+1 day') GROUP BY label " +
            ") combined GROUP BY label ORDER BY label ASC")
    List<ChartDataDto> getProfitTrend(@Bind("sqlFormat") String sqlFormat,
                                      @Bind("startDate") String startDate,
                                      @Bind("endDate") String endDate);
}