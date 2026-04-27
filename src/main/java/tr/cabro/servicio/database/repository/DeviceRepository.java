package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Device;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Device.class)
public interface DeviceRepository {

    // --- INSERT ---
    @SqlUpdate("INSERT INTO devices (device_type, brand, model, serial_no, password, accessory, created_at) " +
            "VALUES (:deviceType, :brand, :model, :serialNo, :password, :accessory, :createdAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Device device);

    // --- UPDATE ---
    @SqlUpdate("UPDATE devices SET device_type=:deviceType, brand=:brand, model=:model, " +
            "serial_no=:serialNo, password=:password, accessory=:accessory WHERE id=:id")
    void update(@BindBean Device device);

    // --- DELETE ---
    @SqlUpdate("DELETE FROM devices WHERE id = :id")
    void delete(@Bind("id") Long id);

    // --- SELECT ---
    @SqlQuery("SELECT id, device_type, brand, model, serial_no, password, accessory, created_at " +
            "FROM devices WHERE id = :id")
    Optional<Device> findById(@Bind("id") Long id);

    @SqlQuery("SELECT id, device_type, brand, model, serial_no, password, accessory, created_at " +
            "FROM devices WHERE id IN (<ids>) AND is_deleted = 0")
    List<Device> findByIds(@Bind("ids") List<Long> ids);

    @SqlQuery("SELECT id, device_type, brand, model, serial_no, password, accessory, created_at " +
            "FROM devices WHERE serial_no = :serialNo")
    Optional<Device> findBySerialNo(@Bind("serialNo") String serialNo);

    @SqlQuery("SELECT id, device_type, brand, model, serial_no, password, accessory, created_at " +
            "FROM devices ORDER BY created_at DESC")
    List<Device> findAll();

    @SqlQuery("SELECT id, device_type, brand, model, serial_no, password, accessory, created_at " +
            "FROM devices WHERE brand LIKE :search OR model LIKE :search OR serial_no LIKE :search " +
            "ORDER BY created_at DESC")
    List<Device> search(@Bind("search") String searchTerm);
}