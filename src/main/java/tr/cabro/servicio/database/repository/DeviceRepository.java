package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.config.ValueColumn;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Device;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Device.class)
public interface DeviceRepository {

    // --- INSERT ---
    @SqlUpdate("INSERT INTO devices (customer_id, device_type, brand, model, serial_no, password, accessory, created_at) " +
            "VALUES (:customerId, :deviceType, :brand, :model, :serialNo, :password, :accessory, :createdAt)")
    @GetGeneratedKeys
    int insertDevice(@BindBean Device device);

    // --- UPDATE ---
    @SqlUpdate("UPDATE devices SET customer_id=:customerId, device_type=:deviceType, brand=:brand, model=:model, " +
            "serial_no=:serialNo, password=:password, accessory=:accessory WHERE id=:id")
    void updateDevice(@BindBean Device device);

    // --- DELETE ---
    @SqlUpdate("DELETE FROM devices WHERE id = :id")
    void deleteDevice(@Bind("id") int id);

    // --- SELECT ---
    @SqlQuery("SELECT id, customer_id, device_type, brand, model, serial_no, password, accessory, created_at " +
            "FROM devices WHERE id = :id")
    Optional<Device> findById(@Bind("id") int id);

    @SqlQuery("SELECT id, customer_id, device_type, brand, model, serial_no, password, accessory, created_at " +
            "FROM devices WHERE serial_no = :serialNo")
    Optional<Device> findBySerialNo(@Bind("serialNo") String serialNo);

    @SqlQuery("SELECT id, customer_id, device_type, brand, model, serial_no, password, accessory, created_at " +
            "FROM devices WHERE customer_id = :customerId ORDER BY created_at DESC")
    List<Device> findByCustomerId(@Bind("customerId") int customerId);

    @SqlQuery("SELECT id, customer_id, device_type, brand, model, serial_no, password, accessory, created_at " +
            "FROM devices ORDER BY created_at DESC")
    List<Device> findAll();

    @SqlQuery("SELECT id, customer_id, device_type, brand, model, serial_no, password, accessory, created_at " +
            "FROM devices WHERE brand LIKE :search OR model LIKE :search OR serial_no LIKE :search " +
            "ORDER BY created_at DESC")
    List<Device> search(@Bind("search") String searchTerm);

    @SqlQuery("SELECT customer_id, COUNT(id) AS device_count FROM devices WHERE customer_id IN (<customerIds>) GROUP BY customer_id")
    @KeyColumn("customer_id")
    @ValueColumn("device_count")
    java.util.Map<Integer, Integer> getDeviceCountsByCustomerIds(@BindList("customerIds") java.util.List<Integer> customerIds);
}