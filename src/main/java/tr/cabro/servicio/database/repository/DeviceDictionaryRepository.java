package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(DeviceType.class)
@RegisterBeanMapper(DeviceBrand.class)
public interface DeviceDictionaryRepository {

    // --- TÜRLER (Types) ---

    // brandCount'u da tek sorguda çeker — ayrı sorgu gerekmez
    @SqlQuery("SELECT dt.*, COUNT(dtb.brand_id) AS brand_count " +
            "FROM device_types dt " +
            "LEFT JOIN device_type_brand dtb ON dt.id = dtb.type_id " +
            "GROUP BY dt.id " +
            "ORDER BY dt.name ASC")
    List<DeviceType> findAllTypes();

    @SqlUpdate("INSERT INTO device_types (name) VALUES (:name)")
    @GetGeneratedKeys
    int insertType(@Bind("name") String name);

    @SqlUpdate("DELETE FROM device_types WHERE id = :id")
    void deleteType(@Bind("id") Long id);

    @SqlQuery("SELECT * FROM device_types WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    Optional<DeviceType> findTypeByName(@Bind("name") String name);

    // --- MARKALAR (Brands) ---

    @SqlQuery("SELECT db.* FROM device_brands db " +
            "JOIN device_type_brand dtb ON db.id = dtb.brand_id " +
            "WHERE dtb.type_id = :typeId ORDER BY db.name ASC")
    List<DeviceBrand> findBrandsByTypeId(@Bind("typeId") Long typeId);

    @SqlUpdate("INSERT INTO device_brands (name) VALUES (:name)")
    @GetGeneratedKeys
    int insertBrand(@Bind("name") String name);

    @SqlUpdate("DELETE FROM device_brands WHERE id = :id")
    void deleteBrand(@Bind("id") Long id);

    @SqlQuery("SELECT * FROM device_brands WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    Optional<DeviceBrand> findBrandByName(@Bind("name") String name);

    // --- ÇOKA-ÇOK İLİŞKİ YÖNETİMİ ---

    @SqlUpdate("INSERT OR IGNORE INTO device_type_brand (type_id, brand_id) VALUES (:typeId, :brandId)")
    void linkTypeAndBrand(@Bind("typeId") Long typeId, @Bind("brandId") Long brandId);

    @SqlUpdate("DELETE FROM device_type_brand WHERE type_id = :typeId AND brand_id = :brandId")
    void unlinkTypeAndBrand(@Bind("typeId") Long typeId, @Bind("brandId") Long brandId);
}