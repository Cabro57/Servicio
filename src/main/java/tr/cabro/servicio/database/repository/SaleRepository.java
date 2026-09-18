package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Sale;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Sale.class)
public interface SaleRepository {

    String COLUMNS = "id, customer_id, type, parent_sale_id, sale_date, subtotal, discount_type, discount_value, " +
            "tax_rate, total_amount, note, is_deleted, created_at, updated_at ";

    @SqlUpdate("INSERT INTO sales (customer_id, type, parent_sale_id, sale_date, subtotal, discount_type, discount_value, " +
            "tax_rate, total_amount, note, created_at, updated_at) " +
            "VALUES (:customerId, :type, :parentSaleId, :saleDate, :subtotal, :discountType, :discountValue, " +
            ":taxRate, :totalAmount, :note, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Sale sale);

    @SqlUpdate("UPDATE sales SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlQuery("SELECT " + COLUMNS + "FROM sales WHERE id = :id AND is_deleted = 0")
    Optional<Sale> findById(@Bind("id") Long id);

    @SqlQuery("SELECT " + COLUMNS + "FROM sales WHERE is_deleted = 0 ORDER BY sale_date DESC")
    List<Sale> findAll();

    @SqlQuery("SELECT " + COLUMNS + "FROM sales WHERE is_deleted = 0 ORDER BY sale_date DESC LIMIT :limit OFFSET :offset")
    List<Sale> findAllPaged(@Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM sales WHERE is_deleted = 0")
    long countAll();

    @SqlQuery("SELECT " + COLUMNS + "FROM sales WHERE customer_id = :customerId AND is_deleted = 0 ORDER BY sale_date DESC")
    List<Sale> findByCustomerId(@Bind("customerId") Long customerId);

    @SqlQuery("SELECT " + COLUMNS + "FROM sales WHERE parent_sale_id = :parentSaleId AND is_deleted = 0")
    List<Sale> findByParentSaleId(@Bind("parentSaleId") Long parentSaleId);

    // Arama alanı müşteri adını da kapsar; bu yüzden customers ile JOIN edilir.
    String SEARCH_SELECT = "SELECT s.id, s.customer_id, s.type, s.parent_sale_id, s.sale_date, s.subtotal, " +
            "s.discount_type, s.discount_value, s.tax_rate, s.total_amount, s.note, s.is_deleted, s.created_at, s.updated_at ";
    String SEARCH_FROM = "FROM sales s LEFT JOIN customers c ON c.id = s.customer_id ";
    String SEARCH_WHERE = "WHERE s.is_deleted = 0 AND (" +
            "CAST(s.id AS TEXT) LIKE :search OR c.first_name LIKE :search OR c.last_name LIKE :search " +
            "OR c.business_name LIKE :search OR c.phone_number_1 LIKE :search) ";

    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + SEARCH_WHERE + "ORDER BY s.sale_date DESC LIMIT :limit OFFSET :offset")
    List<Sale> searchPaged(@Bind("search") String searchTerm, @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) " + SEARCH_FROM + SEARCH_WHERE)
    long countSearch(@Bind("search") String searchTerm);

    // Günlük kasa raporu — belirli bir gün içindeki satış/iade adedi.
    @SqlQuery("SELECT COUNT(*) FROM sales WHERE is_deleted = 0 AND type = :type " +
            "AND sale_date >= :start AND sale_date < :end")
    long countByTypeForDateRange(@Bind("type") tr.cabro.servicio.model.enums.SaleType type,
                                  @Bind("start") java.time.LocalDateTime start, @Bind("end") java.time.LocalDateTime end);
}
