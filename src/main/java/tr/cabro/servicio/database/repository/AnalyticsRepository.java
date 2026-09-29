package tr.cabro.servicio.database.repository;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.reports.Bucket;

import java.time.LocalDate;
import java.util.List;

/**
 * Ana sayfa grafikleri ve Raporlar sayfasının sorguları. Sonuç şekilleri çok çeşitli olduğu için
 * SqlObject arayüzü yerine düz Jdbi sorgularıyla yazıldı; her sorgu küçük bir kayıt döndürür.
 * <p>
 * Tarih kuralları uygulamanın geri kalanıyla aynı: servis cirosu açılış tarihine
 * ({@code work_orders.created_at}), satış {@code sale_date}'e, tahsilat {@code payment_date}'e
 * yazılır. Aralık {@code [from, to]} iki uç dahil gün olarak verilir. Silinmiş kayıtlar sayılmaz.
 * İade fişlerinde tutar ve kalem adedi zaten negatif olduğu için toplamlar kendiliğinden netleşir.
 */
public class AnalyticsRepository {

    private static final String IN_RANGE = " >= :from AND %s < date(:to, '+1 day')";

    private final Jdbi jdbi;

    public AnalyticsRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    private static String range(String column) {
        return column + String.format(IN_RANGE, column);
    }

    private static final String CUSTOMER_NAME =
            "COALESCE(NULLIF(TRIM(c.business_name), ''), TRIM(c.first_name || ' ' || c.last_name))";

    // =========================================================================
    // Kayıtlar
    // =========================================================================

    /** Bir kovadaki gelir ve maliyet. {@code ret}/{@code retCost} iade fişlerinden gelir, negatiftir. */
    public record FinancePoint(String key, double labor, double partRevenue, double partCost,
                               double sale, double saleCost, double ret, double retCost) {
        public double serviceRevenue() { return labor + partRevenue; }
        public double salesNet() { return sale + ret; }
        public double revenue() { return serviceRevenue() + salesNet(); }
        public double cost() { return partCost + saleCost + retCost; }
        public double profit() { return revenue() - cost(); }
    }

    public record FlowPoint(String key, long opened, long delivered) {}

    public record ServiceStats(long opened, long delivered, long returned, double avgDays,
                               double avgTicket, long openNow) {}

    /** Bir gruptaki servis sayısı, cirosu ve teslim edilenlerin ortalama süresi (gün; teslim yoksa NaN). */
    public record Breakdown(String name, long count, double revenue, double avgDays) {}

    public record ItemRow(String name, double quantity, double revenue, double cost) {}

    public record SalesStats(long saleCount, double saleRevenue, long returnCount, double returnAmount) {}

    public record StockRow(String kind, String name, long stock, long minStock) {}

    public record StockValue(double parts, double products, long partUnits, long productUnits) {}

    public record PaymentPoint(String key, PaymentType type, double amount) {}

    public record PaymentTotal(PaymentType type, long count, double amount) {}

    public record CustomerRow(long id, String name, double service, double sale, double paid) {
        public double total() { return service + sale; }
    }

    public record DebtorRow(long id, String name, String phone, double balance, String oldestOpen) {}

    public record Receivables(double total, long debtors) {}

    // =========================================================================
    // Genel
    // =========================================================================

    /** İlk hareketin günü (servis, satış ya da ödeme); hiç kayıt yoksa bugün. "Tümü" aralığı buradan başlar. */
    public LocalDate firstActivity() {
        String day = jdbi.withHandle(h -> h.createQuery(
                        "SELECT MIN(d) FROM (" +
                                " SELECT MIN(date(created_at)) AS d FROM work_orders WHERE is_deleted = 0" +
                                " UNION ALL SELECT MIN(date(sale_date)) FROM sales WHERE is_deleted = 0" +
                                " UNION ALL SELECT MIN(date(COALESCE(payment_date, created_at))) FROM payments)")
                .mapTo(String.class).findOne().orElse(null));
        return day != null ? LocalDate.parse(day) : LocalDate.now();
    }

    // =========================================================================
    // Finans
    // =========================================================================

    public List<FinancePoint> financeTrend(Bucket bucket, LocalDate from, LocalDate to) {
        String sql = "SELECT k, SUM(labor) labor, SUM(part_rev) part_rev, SUM(part_cost) part_cost," +
                " SUM(sale) sale, SUM(sale_cost) sale_cost, SUM(ret) ret, SUM(ret_cost) ret_cost FROM (" +
                "  SELECT " + bucket.sql("wo.created_at") + " AS k," +
                "   COALESCE(SUM(CASE WHEN i.item_type = 'LABOR' THEN i.unit_price * i.quantity END), 0) AS labor," +
                "   COALESCE(SUM(CASE WHEN i.item_type = 'PART' THEN i.unit_price * i.quantity END), 0) AS part_rev," +
                "   COALESCE(SUM(CASE WHEN i.item_type = 'PART' THEN i.purchase_price * i.quantity END), 0) AS part_cost," +
                "   0 AS sale, 0 AS sale_cost, 0 AS ret, 0 AS ret_cost" +
                "  FROM work_orders wo LEFT JOIN work_order_items i ON i.service_id = wo.id" +
                "  WHERE wo.is_deleted = 0 AND " + range("wo.created_at") + " GROUP BY k" +
                "  UNION ALL" +
                "  SELECT " + bucket.sql("sa.sale_date") + " AS k, 0, 0, 0," +
                "   COALESCE(SUM(CASE WHEN sa.type = 'SALE' THEN sa.total_amount END), 0)," +
                "   COALESCE(SUM(CASE WHEN sa.type = 'SALE' THEN (SELECT SUM(x.purchase_price * x.quantity) FROM sale_items x WHERE x.sale_id = sa.id) END), 0)," +
                "   COALESCE(SUM(CASE WHEN sa.type = 'RETURN' THEN sa.total_amount END), 0)," +
                "   COALESCE(SUM(CASE WHEN sa.type = 'RETURN' THEN (SELECT SUM(x.purchase_price * x.quantity) FROM sale_items x WHERE x.sale_id = sa.id) END), 0)" +
                "  FROM sales sa WHERE sa.is_deleted = 0 AND " + range("sa.sale_date") + " GROUP BY k" +
                ") GROUP BY k ORDER BY k";
        return list(sql, from, to, (rs, ctx) -> new FinancePoint(rs.getString("k"),
                rs.getDouble("labor"), rs.getDouble("part_rev"), rs.getDouble("part_cost"),
                rs.getDouble("sale"), rs.getDouble("sale_cost"), rs.getDouble("ret"), rs.getDouble("ret_cost")));
    }

    // =========================================================================
    // Servis
    // =========================================================================

    /** Açılan servisler açılış gününe, teslim edilenler teslim gününe yazılır. */
    public List<FlowPoint> serviceFlow(Bucket bucket, LocalDate from, LocalDate to) {
        String sql = "SELECT k, SUM(o) opened, SUM(d) delivered FROM (" +
                " SELECT " + bucket.sql("created_at") + " AS k, COUNT(*) AS o, 0 AS d FROM work_orders" +
                "  WHERE is_deleted = 0 AND " + range("created_at") + " GROUP BY k" +
                " UNION ALL" +
                " SELECT " + bucket.sql("delivery_date") + " AS k, 0, COUNT(*) FROM v_work_orders" +
                "  WHERE is_deleted = 0 AND service_status = 'DELIVERED' AND " + range("delivery_date") + " GROUP BY k" +
                ") GROUP BY k ORDER BY k";
        return list(sql, from, to, (rs, ctx) -> new FlowPoint(rs.getString("k"), rs.getLong("opened"), rs.getLong("delivered")));
    }

    public ServiceStats serviceStats(LocalDate from, LocalDate to) {
        String sql = "SELECT" +
                " (SELECT COUNT(*) FROM work_orders WHERE is_deleted = 0 AND " + range("created_at") + ") AS opened," +
                " (SELECT COUNT(*) FROM v_work_orders WHERE is_deleted = 0 AND service_status = 'DELIVERED' AND " + range("delivery_date") + ") AS delivered," +
                " (SELECT COUNT(*) FROM v_work_orders WHERE is_deleted = 0 AND service_status = 'RETURN'" +
                "   AND " + range("delivery_date") + ") AS returned," +
                // Serviste kalma süresi teslim almadan teslime kadar (kayıt açılış anı değil).
                " (SELECT AVG(julianday(delivery_date) - julianday(received_at)) FROM v_work_orders" +
                "   WHERE is_deleted = 0 AND service_status = 'DELIVERED' AND " + range("delivery_date") + ") AS avg_days," +
                " (SELECT AVG(t) FROM (SELECT SUM(i.unit_price * i.quantity) AS t FROM work_orders wo" +
                "   JOIN work_order_items i ON i.service_id = wo.id WHERE wo.is_deleted = 0 AND " + range("wo.created_at") +
                "   GROUP BY wo.id HAVING t > 0)) AS avg_ticket," +
                " (SELECT COUNT(*) FROM work_orders WHERE is_deleted = 0 AND service_status NOT IN ('DELIVERED', 'RETURN')) AS open_now";
        return jdbi.withHandle(h -> h.createQuery(sql).bind("from", from.toString()).bind("to", to.toString())
                .map((rs, ctx) -> new ServiceStats(rs.getLong("opened"), rs.getLong("delivered"), rs.getLong("returned"),
                        orNaN(rs.getObject("avg_days")), rs.getDouble("avg_ticket"), rs.getLong("open_now")))
                .one());
    }

    public List<Breakdown> byDeviceType(LocalDate from, LocalDate to) {
        return breakdown("device_types", "d.device_type_id", from, to);
    }

    public List<Breakdown> byBrand(LocalDate from, LocalDate to) {
        return breakdown("device_brands", "d.brand_id", from, to);
    }

    private List<Breakdown> breakdown(String table, String fk, LocalDate from, LocalDate to) {
        String sql = "SELECT g.name AS name, COUNT(*) AS cnt," +
                " COALESCE(SUM((SELECT SUM(i.unit_price * i.quantity) FROM work_order_items i WHERE i.service_id = wo.id)), 0) AS revenue," +
                " AVG(CASE WHEN wo.service_status = 'DELIVERED' THEN julianday(wo.delivery_date) - julianday(wo.received_at) END) AS avg_days" +
                " FROM v_work_orders wo JOIN devices d ON d.id = wo.device_id JOIN " + table + " g ON g.id = " + fk +
                " WHERE wo.is_deleted = 0 AND " + range("wo.created_at") +
                " GROUP BY g.id ORDER BY cnt DESC, revenue DESC";
        return list(sql, from, to, (rs, ctx) -> new Breakdown(rs.getString("name"), rs.getLong("cnt"),
                rs.getDouble("revenue"), orNaN(rs.getObject("avg_days"))));
    }

    /** Servislerde kullanılan işçilik ya da parça kalemleri, ada göre toplanmış. */
    public List<ItemRow> serviceItems(String itemType, LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT i.item_name AS name, SUM(i.quantity) AS qty, SUM(i.unit_price * i.quantity) AS revenue," +
                " SUM(CASE WHEN i.item_type = 'PART' THEN i.purchase_price * i.quantity ELSE 0 END) AS cost" +
                " FROM work_order_items i JOIN work_orders wo ON wo.id = i.service_id" +
                " WHERE wo.is_deleted = 0 AND i.item_type = :type AND " + range("wo.created_at") +
                " GROUP BY i.item_name ORDER BY revenue DESC, qty DESC LIMIT :limit";
        return jdbi.withHandle(h -> h.createQuery(sql).bind("from", from.toString()).bind("to", to.toString())
                .bind("type", itemType).bind("limit", limit).map(ITEM_ROW).list());
    }

    private static final RowMapper<ItemRow> ITEM_ROW = (rs, ctx) -> new ItemRow(rs.getString("name"),
            rs.getDouble("qty"), rs.getDouble("revenue"), rs.getDouble("cost"));

    // =========================================================================
    // Satış ve stok
    // =========================================================================

    public SalesStats salesStats(LocalDate from, LocalDate to) {
        String sql = "SELECT" +
                " COUNT(CASE WHEN type = 'SALE' THEN 1 END) AS sale_count," +
                " COALESCE(SUM(CASE WHEN type = 'SALE' THEN total_amount END), 0) AS sale_revenue," +
                " COUNT(CASE WHEN type = 'RETURN' THEN 1 END) AS return_count," +
                " COALESCE(SUM(CASE WHEN type = 'RETURN' THEN total_amount END), 0) AS return_amount" +
                " FROM sales WHERE is_deleted = 0 AND " + range("sale_date");
        return jdbi.withHandle(h -> h.createQuery(sql).bind("from", from.toString()).bind("to", to.toString())
                .map((rs, ctx) -> new SalesStats(rs.getLong("sale_count"), rs.getDouble("sale_revenue"),
                        rs.getLong("return_count"), rs.getDouble("return_amount")))
                .one());
    }

    /** En çok satan ürünler; iadeler aynı satırdan düşer (adet ve tutar negatif). */
    public List<ItemRow> topProducts(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT si.item_name AS name, SUM(si.quantity) AS qty, SUM(si.line_total) AS revenue," +
                " SUM(si.purchase_price * si.quantity) AS cost" +
                " FROM sale_items si JOIN sales sa ON sa.id = si.sale_id" +
                " WHERE sa.is_deleted = 0 AND " + range("sa.sale_date") +
                " GROUP BY COALESCE(CAST(si.product_id AS TEXT), si.item_name)" +
                " HAVING qty <> 0 OR revenue <> 0 ORDER BY revenue DESC, qty DESC LIMIT :limit";
        return jdbi.withHandle(h -> h.createQuery(sql).bind("from", from.toString()).bind("to", to.toString())
                .bind("limit", limit).map(ITEM_ROW).list());
    }

    /** Asgari stok seviyesine inmiş ya da altına düşmüş parça ve ürünler (anlık, dönemden bağımsız). */
    public List<StockRow> lowStock() {
        String sql = "SELECT 'Parça' AS kind, name, stock_quantity, min_stock_level FROM parts" +
                " WHERE is_deleted = 0 AND min_stock_level > 0 AND stock_quantity <= min_stock_level" +
                " UNION ALL" +
                " SELECT 'Ürün', name, stock_quantity, min_stock_level FROM products" +
                " WHERE is_deleted = 0 AND min_stock_level > 0 AND stock_quantity <= min_stock_level" +
                " ORDER BY 3 ASC, 2";
        return jdbi.withHandle(h -> h.createQuery(sql).map((rs, ctx) -> new StockRow(rs.getString(1), rs.getString(2),
                rs.getLong(3), rs.getLong(4))).list());
    }

    /** Eldeki stoğun alış fiyatından değeri (anlık). */
    public StockValue stockValue() {
        String sql = "SELECT" +
                " (SELECT COALESCE(SUM(purchase_price * stock_quantity), 0) FROM parts WHERE is_deleted = 0 AND stock_quantity > 0)," +
                " (SELECT COALESCE(SUM(purchase_price * stock_quantity), 0) FROM products WHERE is_deleted = 0 AND stock_quantity > 0)," +
                " (SELECT COALESCE(SUM(stock_quantity), 0) FROM parts WHERE is_deleted = 0 AND stock_quantity > 0)," +
                " (SELECT COALESCE(SUM(stock_quantity), 0) FROM products WHERE is_deleted = 0 AND stock_quantity > 0)";
        return jdbi.withHandle(h -> h.createQuery(sql).map((rs, ctx) -> new StockValue(rs.getDouble(1), rs.getDouble(2),
                rs.getLong(3), rs.getLong(4))).one());
    }

    // =========================================================================
    // Tahsilat ve müşteriler
    // =========================================================================

    private static final String PAY_DATE = "COALESCE(payment_date, created_at)";

    /** Kovalara göre tahsilat, yönteme ayrılmış (yalnızca giriş; iade ödemeleri hariç). */
    public List<PaymentPoint> paymentTrend(Bucket bucket, LocalDate from, LocalDate to) {
        String sql = "SELECT " + bucket.sql(PAY_DATE) + " AS k, payment_type, SUM(amount) AS amount FROM payments" +
                " WHERE amount > 0 AND " + range(PAY_DATE) + " GROUP BY k, payment_type ORDER BY k";
        return list(sql, from, to, (rs, ctx) -> new PaymentPoint(rs.getString("k"),
                PaymentType.of(rs.getString("payment_type")), rs.getDouble("amount")));
    }

    /** Yönteme göre tahsilat; {@code type == null} satırı iade ödemelerinin (negatif) toplamıdır. */
    public List<PaymentTotal> paymentTotals(LocalDate from, LocalDate to) {
        String sql = "SELECT CASE WHEN amount > 0 THEN payment_type END AS t, COUNT(*) AS cnt, SUM(amount) AS amount" +
                " FROM payments WHERE amount <> 0 AND " + range(PAY_DATE) + " GROUP BY t ORDER BY amount DESC";
        return list(sql, from, to, (rs, ctx) -> {
            String t = rs.getString("t");
            return new PaymentTotal(t == null ? null : PaymentType.of(t), rs.getLong("cnt"), rs.getDouble("amount"));
        });
    }

    public Receivables receivables() {
        String sql = "SELECT COALESCE(SUM(balance), 0), COUNT(*) FROM v_customer_balances WHERE balance > 0.005";
        return jdbi.withHandle(h -> h.createQuery(sql).map((rs, ctx) -> new Receivables(rs.getDouble(1), rs.getLong(2))).one());
    }

    /** Dönemde en çok iş getiren müşteriler: servis ve satış tutarı, aynı dönemde ödedikleri. */
    public List<CustomerRow> topCustomers(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT * FROM (SELECT c.id AS id, " + CUSTOMER_NAME + " AS name," +
                " (SELECT COALESCE(SUM(total_amount), 0) FROM v_document_totals t WHERE t.customer_id = c.id" +
                "   AND t.document_type = 'WORK_ORDER' AND " + range("t.document_date") + ") AS service," +
                " (SELECT COALESCE(SUM(total_amount), 0) FROM v_document_totals t WHERE t.customer_id = c.id" +
                "   AND t.document_type = 'SALE' AND " + range("t.document_date") + ") AS sale," +
                " (SELECT COALESCE(SUM(amount), 0) FROM payments p WHERE p.customer_id = c.id AND " + range("COALESCE(p.payment_date, p.created_at)") + ") AS paid" +
                " FROM customers c WHERE c.is_deleted = 0)" +
                " WHERE service + sale <> 0 ORDER BY service + sale DESC LIMIT :limit";
        return jdbi.withHandle(h -> h.createQuery(sql).bind("from", from.toString()).bind("to", to.toString())
                .bind("limit", limit)
                .map((rs, ctx) -> new CustomerRow(rs.getLong("id"), rs.getString("name"),
                        rs.getDouble("service"), rs.getDouble("sale"), rs.getDouble("paid")))
                .list());
    }

    /** Bize borcu olan müşteriler (anlık) ve en eski açık belgelerinin tarihi. */
    public List<DebtorRow> debtors(int limit) {
        String sql = "SELECT c.id, " + CUSTOMER_NAME + " AS name, c.phone_number_1, b.balance," +
                " (SELECT MIN(date(db.document_date)) FROM v_document_balances db WHERE db.customer_id = c.id" +
                "   AND db.remaining_amount > 0.005) AS oldest" +
                " FROM v_customer_balances b JOIN customers c ON c.id = b.customer_id" +
                " WHERE b.balance > 0.005 ORDER BY b.balance DESC LIMIT :limit";
        return jdbi.withHandle(h -> h.createQuery(sql).bind("limit", limit)
                .map((rs, ctx) -> new DebtorRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getDouble(4), rs.getString(5)))
                .list());
    }

    // =========================================================================

    /** Ortalaması alınacak kayıt yoksa SQL NULL döner; sıfır gün sanılmasın diye NaN. */
    private static double orNaN(Object value) {
        return value instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    private <T> List<T> list(String sql, LocalDate from, LocalDate to, RowMapper<T> mapper) {
        return jdbi.withHandle(h -> h.createQuery(sql).bind("from", from.toString()).bind("to", to.toString())
                .map(mapper).list());
    }
}
