package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import tr.cabro.servicio.model.dto.SummaryCardDto;

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
}
