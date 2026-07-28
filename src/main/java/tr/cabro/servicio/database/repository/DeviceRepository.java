package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.filter.SqlWhereBuilder;
import tr.cabro.servicio.database.mapper.DeviceRowMapper;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.dto.PageResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RegisterRowMapper(DeviceRowMapper.class)
public interface DeviceRepository extends SqlObject {

    @SqlUpdate("INSERT INTO devices (device_type_id, brand_id, model, serial_no, accessory, created_at) " +
            "VALUES (:deviceType.id, :brand.id, :model, :serialNo, :accessory, :createdAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Device device);

    @SqlUpdate("UPDATE devices SET device_type_id=:deviceType.id, brand_id=:brand.id, model=:model, " +
            "serial_no=:serialNo, accessory=:accessory WHERE id=:id")
    void update(@BindBean Device device);

    @SqlUpdate("DELETE FROM devices WHERE id = :id")
    void delete(@Bind("id") Long id);

    // ÖNEMLİ: Alias isimleri unique olmalı
    String BASE_SELECT = "SELECT " +
            "d.id, d.model, d.serial_no, d.accessory, " +
            "d.created_at, d.is_deleted, d.updated_at, " +
            "dt.id AS device_type_id, " +
            "dt.name AS device_type_name, " +
            "db.id AS device_brand_id, " +
            "db.name AS device_brand_name " +
            "FROM devices d " +
            "JOIN device_types dt ON d.device_type_id = dt.id " +
            "JOIN device_brands db ON d.brand_id = db.id ";

    @SqlQuery(BASE_SELECT + "WHERE d.id = :id")
    Optional<Device> findById(@Bind("id") Long id);

    @SqlQuery(BASE_SELECT + "WHERE d.id IN (<ids>) AND d.is_deleted = 0")
    List<Device> findByIds(@BindList("ids") List<Long> ids);

    @SqlQuery(BASE_SELECT + "WHERE d.serial_no = :serialNo")
    Optional<Device> findBySerialNo(@Bind("serialNo") String serialNo);

    @SqlQuery(BASE_SELECT + "ORDER BY d.created_at DESC")
    List<Device> findAll();

    @SqlQuery(BASE_SELECT + "WHERE db.name LIKE :search OR d.model LIKE :search OR d.serial_no LIKE :search " +
            "ORDER BY d.created_at DESC")
    List<Device> search(@Bind("search") String searchTerm);

    // Bir müşterinin daha önce servise getirdiği cihazlar — devices tablosunda customer_id yok,
    // ilişki work_orders üzerinden kuruluyor (bkz. V7 migration notu).
    @SqlQuery(BASE_SELECT + "JOIN work_orders wo ON wo.device_id = d.id " +
            "WHERE wo.customer_id = :customerId AND d.is_deleted = 0 " +
            "GROUP BY d.id ORDER BY MAX(d.created_at) DESC")
    List<Device> findByCustomerId(@Bind("customerId") Long customerId);

    // =========================================================================
    // GEÇİCİ: devices.password -> device_access_credentials taşıma desteği.
    // V15 migration'da devices.password kolonu düşürüldüğünde bu metot, LegacyPassword
    // sınıfı ve clearPassword de kaldırılacak (bkz. DeviceAccessCredentialService.migrateLegacyDevicePasswords).
    // Device modelinde artık password alanı olmadığı için ayrı bir hafif DTO kullanılıyor.
    // =========================================================================

    @RegisterBeanMapper(LegacyPassword.class)
    @SqlQuery("SELECT id, password FROM devices WHERE password IS NOT NULL AND password <> ''")
    List<LegacyPassword> findLegacyPasswords();

    @SqlUpdate("UPDATE devices SET password = NULL WHERE id = :id")
    void clearPassword(@Bind("id") Long id);

    class LegacyPassword {
        private Long id;
        private String password;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // =========================================================================
    // TABLO BAŞLIĞI FİLTRESİ + SAYFALAMA — dinamik WHERE (SqlWhereBuilder), JDBI SqlObject default metot.
    // =========================================================================

    default PageResult<Device> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                    int page, int pageSize) {
        SqlWhereBuilder.Result where = SqlWhereBuilder.build(filters);
        String likeTerm = (searchTerm != null && !searchTerm.isBlank()) ? "%" + searchTerm.trim() + "%" : null;

        StringBuilder whereClause = new StringBuilder("WHERE 1=1").append(where.getWhereFragment());
        if (likeTerm != null) {
            whereClause.append(" AND (db.name LIKE :search OR d.model LIKE :search OR d.serial_no LIKE :search)");
        }

        Map<String, Object> countParams = new HashMap<>(where.getParams());
        if (likeTerm != null) countParams.put("search", likeTerm);

        Map<String, Object> params = new HashMap<>(countParams);
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);

        String listSql = BASE_SELECT + whereClause + " ORDER BY d.created_at DESC LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) FROM devices d " +
                "JOIN device_types dt ON d.device_type_id = dt.id " +
                "JOIN device_brands db ON d.brand_id = db.id " + whereClause;

        List<Device> items = getHandle().createQuery(listSql).bindMap(params).map(new DeviceRowMapper()).list();
        long total = getHandle().createQuery(countSql).bindMap(countParams).mapTo(Long.class).one();
        return new PageResult<>(items, page, pageSize, total);
    }
}