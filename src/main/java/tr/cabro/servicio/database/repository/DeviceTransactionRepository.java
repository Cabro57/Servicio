package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.filter.SqlWhereBuilder;
import tr.cabro.servicio.database.mapper.DeviceTransactionRowMapper;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.dto.PageResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 2.el alım-satım modülü: bir cihaza ait alım/satım olayları.
 * "Şu an stokta mı" durumu ayrı bir kolon değil — bir cihazın en son (tarihe göre)
 * transaction'ı PURCHASE ise stokta, SALE ise satılmış kabul edilir.
 */
@RegisterRowMapper(DeviceTransactionRowMapper.class)
public interface DeviceTransactionRepository extends SqlObject {

    String BASE_SELECT = "SELECT tr.id, tr.device_id, tr.type, tr.customer_id, tr.transaction_date, tr.price, " +
            "tr.expertise_notes, tr.warranty_months, tr.note, " +
            "tr.is_deleted AS tr_is_deleted, tr.created_at AS tr_created_at, tr.updated_at AS tr_updated_at, " +
            "d.model AS d_model, d.serial_no AS d_serial_no, d.accessory AS d_accessory, " +
            "dt.id AS dt_id, dt.name AS dt_name, " +
            "db.id AS db_id, db.name AS db_name, " +
            "c.customer_type AS c_type, c.business_name AS c_business_name, " +
            "c.first_name AS c_first_name, c.last_name AS c_last_name, c.phone_number_1 AS c_phone_number_1 " +
            "FROM device_transactions tr " +
            "JOIN devices d ON tr.device_id = d.id " +
            "JOIN device_types dt ON d.device_type_id = dt.id " +
            "JOIN device_brands db ON d.brand_id = db.id " +
            "JOIN customers c ON tr.customer_id = c.id ";

    // Bir cihaz için en son (tarih, sonra id) sıralı transaction — "şu an stokta mı" bunun
    // type'ına bakılarak türetilir.
    String LATEST_PER_DEVICE = "tr.id = (SELECT t2.id FROM device_transactions t2 " +
            "WHERE t2.device_id = tr.device_id AND t2.is_deleted = 0 " +
            "ORDER BY t2.transaction_date DESC, t2.id DESC LIMIT 1) ";

    @SqlUpdate("INSERT INTO device_transactions (device_id, type, customer_id, transaction_date, price, " +
            "expertise_notes, warranty_months, note, created_at, updated_at) " +
            "VALUES (:deviceId, :type, :customerId, :transactionDate, :price, " +
            ":expertiseNotes, :warrantyMonths, :note, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean DeviceTransaction transaction);

    @SqlUpdate("UPDATE device_transactions SET price=:price, transaction_date=:transactionDate, " +
            "expertise_notes=:expertiseNotes, warranty_months=:warrantyMonths, note=:note, updated_at=:updatedAt " +
            "WHERE id=:id")
    void update(@BindBean DeviceTransaction transaction);

    @SqlUpdate("UPDATE device_transactions SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlQuery(BASE_SELECT + "WHERE tr.id = :id AND tr.is_deleted = 0")
    Optional<DeviceTransaction> findById(@Bind("id") Long id);

    // Bir cihazın tüm alım/satım geçmişi (Cihaz Geçmişi paneli için) — tarihe göre en yeni önce.
    @SqlQuery(BASE_SELECT + "WHERE tr.device_id = :deviceId AND tr.is_deleted = 0 " +
            "ORDER BY tr.transaction_date DESC, tr.id DESC")
    List<DeviceTransaction> findByDeviceId(@Bind("deviceId") Long deviceId);

    // Şu an stokta olan cihazlar: en son transaction'ı PURCHASE olanlar.
    @SqlQuery(BASE_SELECT + "WHERE tr.is_deleted = 0 AND tr.type = 'PURCHASE' AND " + LATEST_PER_DEVICE +
            "ORDER BY tr.transaction_date DESC")
    List<DeviceTransaction> findCurrentStockDevices();

    // Bir cihazın en son transaction'ı — SALE eklenmeden önce "zaten stokta mı" kontrolü için.
    @SqlQuery(BASE_SELECT + "WHERE tr.device_id = :deviceId AND tr.is_deleted = 0 " +
            "ORDER BY tr.transaction_date DESC, tr.id DESC LIMIT 1")
    Optional<DeviceTransaction> findLatestByDeviceId(@Bind("deviceId") Long deviceId);

    // =========================================================================
    // TABLO BAŞLIĞI FİLTRESİ + SAYFALAMA — dinamik WHERE (SqlWhereBuilder), JDBI SqlObject default metot.
    // =========================================================================

    default PageResult<DeviceTransaction> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                                boolean onlyInStock, int page, int pageSize) {
        SqlWhereBuilder.Result where = SqlWhereBuilder.build(filters);
        String likeTerm = (searchTerm != null && !searchTerm.isBlank()) ? "%" + searchTerm.trim() + "%" : null;

        StringBuilder whereClause = new StringBuilder("WHERE tr.is_deleted = 0").append(where.getWhereFragment());
        if (onlyInStock) {
            whereClause.append(" AND tr.type = 'PURCHASE' AND ").append(LATEST_PER_DEVICE);
        }
        if (likeTerm != null) {
            whereClause.append(" AND (d.model LIKE :search OR d.serial_no LIKE :search OR db.name LIKE :search " +
                    "OR c.first_name LIKE :search OR c.last_name LIKE :search OR c.business_name LIKE :search)");
        }

        Map<String, Object> countParams = new HashMap<>(where.getParams());
        if (likeTerm != null) countParams.put("search", likeTerm);

        Map<String, Object> params = new HashMap<>(countParams);
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);

        String listSql = BASE_SELECT + whereClause + " ORDER BY tr.transaction_date DESC, tr.id DESC LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) FROM device_transactions tr " +
                "JOIN devices d ON tr.device_id = d.id " +
                "JOIN device_types dt ON d.device_type_id = dt.id " +
                "JOIN device_brands db ON d.brand_id = db.id " +
                "JOIN customers c ON tr.customer_id = c.id " + whereClause;

        List<DeviceTransaction> items = getHandle().createQuery(listSql).bindMap(params)
                .map(new DeviceTransactionRowMapper()).list();
        long total = getHandle().createQuery(countSql).bindMap(countParams).mapTo(Long.class).one();
        return new PageResult<>(items, page, pageSize, total);
    }
}
