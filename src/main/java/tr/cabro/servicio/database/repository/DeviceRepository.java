package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;

import java.util.List;
import java.util.Optional;

// Not: JDBI'nin joinlenmiş tabloları Device içindeki DeviceType ve DeviceBrand'e otomatik
// mapleyebilmesi için Device sınıfınızda bu property'lerin üzerinde @Nested anotasyonu olmalıdır.
@RegisterBeanMapper(Device.class)
@RegisterBeanMapper(DeviceType.class)
@RegisterBeanMapper(DeviceBrand.class)
public interface DeviceRepository {

    // --- INSERT ---
    // String isimler yerine objelerin içindeki ID'leri alıyoruz.
    @SqlUpdate("INSERT INTO devices (device_type_id, brand_id, model, serial_no, password, accessory, created_at) " +
            "VALUES (:deviceType.id, :brand.id, :model, :serialNo, :password, :accessory, :createdAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Device device);

    // --- UPDATE ---
    @SqlUpdate("UPDATE devices SET device_type_id=:deviceType.id, brand_id=:brand.id, model=:model, " +
            "serial_no=:serialNo, password=:password, accessory=:accessory WHERE id=:id")
    void update(@BindBean Device device);

    // --- DELETE ---
    @SqlUpdate("DELETE FROM devices WHERE id = :id")
    void delete(@Bind("id") Long id);

    // --- ORTAK SELECT SORGUSU (Sabit olarak tanımlanıp tekrar önlenebilir) ---
    String BASE_SELECT = "SELECT d.id, d.device_type_id, d.brand_id, d.model, d.serial_no, d.password, d.accessory, d.created_at, " +
            "dt.id AS \"deviceType.id\", dt.name AS \"deviceType.name\", " +
            "db.id AS \"brand.id\", db.name AS \"brand.name\" " +
            "FROM devices d " +
            "JOIN device_types dt ON d.device_type_id = dt.id " +
            "JOIN device_brands db ON d.brand_id = db.id ";

    // --- SELECT ---
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