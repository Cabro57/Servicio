package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.filter.SqlWhereBuilder;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.mapper.CustomerTableMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RegisterBeanMapper(Customer.class)
public interface CustomerRepository extends SqlObject {

    @SqlUpdate("INSERT INTO customers (customer_type, business_name, first_name, last_name, identity_no, tax_number, tax_office, phone_number_1, phone_number_2, email, address, note, is_problematic, created_at, updated_at) " +
            "VALUES (:type, :businessName, :firstName, :lastName, :identityNo, :taxNumber, :taxOffice, :phoneNumber1, :phoneNumber2, :email, :address, :note, :problematic, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Customer customer);

    @SqlUpdate("UPDATE customers SET customer_type=:type, business_name=:businessName, first_name=:firstName, last_name=:lastName, " +
            "identity_no=:identityNo, tax_number=:taxNumber, tax_office=:taxOffice, phone_number_1=:phoneNumber1, phone_number_2=:phoneNumber2, " +
            "email=:email, address=:address, note=:note, is_problematic=:problematic, updated_at=:updatedAt WHERE id=:id")
    void update(@BindBean Customer customer);

    // --- SOFT DELETE İŞLEMLERİ ---
    @SqlUpdate("UPDATE customers SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlUpdate("UPDATE customers SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id IN (<ids>)")
    void deleteByIds(@BindList("ids") List<Long> ids);

    // --- SEÇME (SELECT) İŞLEMLERİ ---
    // DÜZELTME: customer_type kolonu Java'daki "type" değişkeniyle eşleşmesi için "AS type" olarak seçildi
    @SqlQuery("SELECT id, customer_type AS type, business_name, first_name, last_name, identity_no, tax_number, tax_office, phone_number_1, phone_number_2, email, address, note, is_problematic, created_at, updated_at FROM customers WHERE id = :id AND is_deleted = 0")
    Optional<Customer> findById(@Bind("id") Long id);

    @SqlQuery("SELECT id, customer_type AS type, business_name, first_name, last_name, identity_no, tax_number, tax_office, phone_number_1, phone_number_2, email, address, note, is_problematic, created_at, updated_at FROM customers WHERE id IN (<ids>) AND is_deleted = 0")
    List<Customer> findByIds(@BindList("ids") List<Long> ids);

    @SqlQuery("SELECT id, customer_type AS type, business_name, first_name, last_name, identity_no, tax_number, tax_office, phone_number_1, phone_number_2, email, address, note, is_problematic, created_at, updated_at FROM customers WHERE is_deleted = 0 ORDER BY created_at DESC")
    List<Customer> findAll();

    @SqlQuery("SELECT " +
            " c.id, c.customer_type AS type, " +
            "c.business_name, c.first_name, c.last_name, " +
            "c.identity_no, c.tax_number, c.tax_office, " +
            "c.phone_number_1, c.phone_number_2, c.email, " +
            "c.address, c.note, c.is_problematic, c.created_at, c.updated_at, " +
            "COUNT(DISTINCT wo.device_id) AS device_count, " +
            "COALESCE(SUM(woi.unit_price * woi.quantity), 0) AS spent " +
            "FROM customers c " +
            "LEFT JOIN work_orders wo ON wo.customer_id = c.id " +
            "LEFT JOIN work_order_items woi ON woi.service_id = wo.id " +
            "WHERE c.is_deleted = 0 GROUP BY " +
            "c.id, c.customer_type, c.business_name, c.first_name, c.last_name, " +
            "c.identity_no, c.tax_number, c.tax_office, c.phone_number_1, c.phone_number_2, " +
            "c.email, c.address, c.note, c.is_problematic, c.created_at, c.updated_at " +
            "ORDER BY c.created_at DESC")
    @UseRowMapper(CustomerTableMapper.class)
    List<Customer> findAllTable();

    @SqlQuery("SELECT id, customer_type AS type, business_name, first_name, last_name, identity_no, tax_number, tax_office, phone_number_1, phone_number_2, email, address, note, is_problematic, created_at, updated_at FROM customers WHERE is_deleted = 0 AND " +
            "(first_name LIKE :search OR last_name LIKE :search OR business_name LIKE :search OR " +
            "phone_number_1 LIKE :search OR identity_no LIKE :search) " +
            "ORDER BY created_at DESC")
    List<Customer> search(@Bind("search") String searchTerm);

    // =========================================================================
    // TABLO BAŞLIĞI FİLTRESİ + SAYFALAMA — dinamik WHERE (SqlWhereBuilder), JDBI SqlObject default metot.
    // Aggregate (device_count/spent) yapısı findAllTable() ile aynı — GROUP BY c.id sayesinde
    // JOIN'ler satır sayısını etkilemiyor, bu yüzden COUNT sorgusu JOIN'süz, sade customers üzerinden.
    // =========================================================================

    default PageResult<Customer> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                      int page, int pageSize) {
        SqlWhereBuilder.Result where = SqlWhereBuilder.build(filters);
        String likeTerm = (searchTerm != null && !searchTerm.isBlank()) ? "%" + searchTerm.trim() + "%" : null;

        StringBuilder whereClause = new StringBuilder("WHERE c.is_deleted = 0").append(where.getWhereFragment());
        if (likeTerm != null) {
            whereClause.append(" AND (c.first_name LIKE :search OR c.last_name LIKE :search OR c.business_name LIKE :search " +
                    "OR c.phone_number_1 LIKE :search OR c.identity_no LIKE :search)");
        }

        Map<String, Object> countParams = new HashMap<>(where.getParams());
        if (likeTerm != null) countParams.put("search", likeTerm);

        Map<String, Object> params = new HashMap<>(countParams);
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);

        String listSql = "SELECT c.id, c.customer_type AS type, c.business_name, c.first_name, c.last_name, " +
                "c.identity_no, c.tax_number, c.tax_office, c.phone_number_1, c.phone_number_2, c.email, " +
                "c.address, c.note, c.is_problematic, c.created_at, c.updated_at, " +
                "COUNT(DISTINCT wo.device_id) AS device_count, COALESCE(SUM(woi.unit_price * woi.quantity), 0) AS spent " +
                "FROM customers c " +
                "LEFT JOIN work_orders wo ON wo.customer_id = c.id " +
                "LEFT JOIN work_order_items woi ON woi.service_id = wo.id " +
                whereClause +
                " GROUP BY c.id, c.customer_type, c.business_name, c.first_name, c.last_name, c.identity_no, " +
                "c.tax_number, c.tax_office, c.phone_number_1, c.phone_number_2, c.email, c.address, c.note, " +
                "c.is_problematic, c.created_at, c.updated_at " +
                "ORDER BY c.created_at DESC LIMIT :limit OFFSET :offset";

        String countSql = "SELECT COUNT(*) FROM customers c " + whereClause;

        List<Customer> items = getHandle().createQuery(listSql).bindMap(params).map(new CustomerTableMapper()).list();
        long total = getHandle().createQuery(countSql).bindMap(countParams).mapTo(Long.class).one();
        return new PageResult<>(items, page, pageSize, total);
    }
}
