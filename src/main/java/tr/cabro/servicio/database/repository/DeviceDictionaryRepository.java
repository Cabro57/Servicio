package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;

import java.util.List;

@RegisterBeanMapper(DeviceType.class)
@RegisterBeanMapper(DeviceBrand.class)
public interface DeviceDictionaryRepository {

    // --- TÜRLER (Types) ---
    @SqlQuery("SELECT * FROM device_types ORDER BY name ASC")
    List<DeviceType> findAllTypes();

    @SqlUpdate("INSERT INTO device_types (name) VALUES (:name)")
    @GetGeneratedKeys
    int insertType(@Bind("name") String name);

    @SqlUpdate("DELETE FROM device_types WHERE id = :id")
    void deleteType(@Bind("id") int id);

    // --- MARKALAR (Brands) ---
    // Sadece belirli bir Türe (Örn: Telefon) ait markaları getirir (Çoka-Çok JOIN ile)
    @SqlQuery("SELECT db.* FROM device_brands db " +
            "JOIN device_type_brand dtb ON db.id = dtb.brand_id " +
            "WHERE dtb.type_id = :typeId ORDER BY db.name ASC")
    List<DeviceBrand> findBrandsByTypeId(@Bind("typeId") int typeId);

    @SqlUpdate("INSERT INTO device_brands (name) VALUES (:name)")
    @GetGeneratedKeys
    int insertBrand(@Bind("name") String name);

    @SqlUpdate("DELETE FROM device_brands WHERE id = :id")
    void deleteBrand(@Bind("id") int id);

    // --- ÇOKA-ÇOK İLİŞKİ YÖNETİMİ ---
    @SqlUpdate("INSERT OR IGNORE INTO device_type_brand (type_id, brand_id) VALUES (:typeId, :brandId)")
    void linkTypeAndBrand(@Bind("typeId") int typeId, @Bind("brandId") int brandId);

    @SqlUpdate("DELETE FROM device_type_brand WHERE type_id = :typeId AND brand_id = :brandId")
    void unlinkTypeAndBrand(@Bind("typeId") int typeId, @Bind("brandId") int brandId);
}