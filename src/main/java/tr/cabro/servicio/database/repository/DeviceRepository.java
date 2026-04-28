package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.database.mapper.DeviceRowMapper;
import tr.cabro.servicio.model.Device;

import java.util.List;
import java.util.Optional;

@RegisterRowMapper(DeviceRowMapper.class)
public interface DeviceRepository {

    @SqlUpdate("INSERT INTO devices (device_type_id, brand_id, model, serial_no, password, accessory, created_at) " +
            "VALUES (:deviceType.id, :brand.id, :model, :serialNo, :password, :accessory, :createdAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Device device);

    @SqlUpdate("UPDATE devices SET device_type_id=:deviceType.id, brand_id=:brand.id, model=:model, " +
            "serial_no=:serialNo, password=:password, accessory=:accessory WHERE id=:id")
    void update(@BindBean Device device);

    @SqlUpdate("DELETE FROM devices WHERE id = :id")
    void delete(@Bind("id") Long id);

    // ÖNEMLİ: Alias isimleri unique olmalı
    String BASE_SELECT = "SELECT " +
            "d.id, d.model, d.serial_no, d.password, d.accessory, " +
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
}