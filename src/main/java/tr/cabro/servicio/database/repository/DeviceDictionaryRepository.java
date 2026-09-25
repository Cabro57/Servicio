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

/**
 * Cihaz türü / marka sözlüğü. Marka tek bir global kayıttır; türlere çoka-çok bağlanır
 * (device_type_brand). Cihaz sayıları silinmiş cihazları da içerir: silme/birleştirme kararı
 * yabancı anahtara (RESTRICT) göre verildiği için ekranda görünen sayı onunla aynı olmalı.
 */
@RegisterBeanMapper(DeviceType.class)
@RegisterBeanMapper(DeviceBrand.class)
public interface DeviceDictionaryRepository {

    // --- TÜRLER (Types) ---

    // Sayılar alt sorgularla: JOIN'ler birbirini çoğaltmasın.
    @SqlQuery("SELECT dt.id, dt.name, " +
            "(SELECT COUNT(*) FROM device_type_brand dtb WHERE dtb.type_id = dt.id) AS brand_count, " +
            "(SELECT COUNT(*) FROM devices d WHERE d.device_type_id = dt.id) AS device_count, " +
            "(SELECT COUNT(*) FROM labors l WHERE l.device_type_id = dt.id AND l.is_deleted = 0) AS labor_count " +
            "FROM device_types dt ORDER BY dt.name COLLATE NOCASE ASC")
    List<DeviceType> findAllTypes();

    @SqlUpdate("INSERT INTO device_types (name) VALUES (:name)")
    @GetGeneratedKeys
    int insertType(@Bind("name") String name);

    @SqlUpdate("UPDATE device_types SET name = :name WHERE id = :id")
    void renameType(@Bind("id") Long id, @Bind("name") String name);

    @SqlUpdate("DELETE FROM device_types WHERE id = :id")
    void deleteType(@Bind("id") Long id);

    @SqlQuery("SELECT * FROM device_types WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    Optional<DeviceType> findTypeByName(@Bind("name") String name);

    // --- MARKALAR (Brands) ---

    /** Türün markaları; cihaz sayısı yalnızca bu türdeki cihazlar, typeNames markanın DİĞER türleri. */
    @SqlQuery("SELECT db.id, db.name, " +
            "(SELECT COUNT(*) FROM devices d WHERE d.brand_id = db.id AND d.device_type_id = :typeId) AS device_count, " +
            "(SELECT GROUP_CONCAT(t.name, ', ') FROM device_type_brand x JOIN device_types t ON t.id = x.type_id " +
            "  WHERE x.brand_id = db.id AND x.type_id <> :typeId) AS type_names " +
            "FROM device_brands db JOIN device_type_brand dtb ON db.id = dtb.brand_id " +
            "WHERE dtb.type_id = :typeId ORDER BY db.name COLLATE NOCASE ASC")
    List<DeviceBrand> findBrandsByTypeId(@Bind("typeId") Long typeId);

    /** Tüm markalar; cihaz sayısı toplam, typeNames bağlı olduğu tüm türler. */
    @SqlQuery("SELECT db.id, db.name, " +
            "(SELECT COUNT(*) FROM devices d WHERE d.brand_id = db.id) AS device_count, " +
            "(SELECT GROUP_CONCAT(t.name, ', ') FROM device_type_brand x JOIN device_types t ON t.id = x.type_id " +
            "  WHERE x.brand_id = db.id) AS type_names " +
            "FROM device_brands db ORDER BY db.name COLLATE NOCASE ASC")
    List<DeviceBrand> findAllBrands();

    @SqlQuery("SELECT type_id FROM device_type_brand WHERE brand_id = :brandId")
    List<Long> findTypeIdsByBrand(@Bind("brandId") Long brandId);

    @SqlQuery("SELECT COUNT(*) FROM devices WHERE device_type_id = :typeId AND brand_id = :brandId")
    int countDevices(@Bind("typeId") Long typeId, @Bind("brandId") Long brandId);

    @SqlQuery("SELECT COUNT(*) FROM devices WHERE brand_id = :brandId")
    int countDevicesByBrand(@Bind("brandId") Long brandId);

    @SqlUpdate("INSERT INTO device_brands (name) VALUES (:name)")
    @GetGeneratedKeys
    int insertBrand(@Bind("name") String name);

    @SqlUpdate("UPDATE device_brands SET name = :name WHERE id = :id")
    void renameBrand(@Bind("id") Long id, @Bind("name") String name);

    @SqlUpdate("DELETE FROM device_brands WHERE id = :id")
    void deleteBrand(@Bind("id") Long id);

    @SqlQuery("SELECT * FROM device_brands WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    Optional<DeviceBrand> findBrandByName(@Bind("name") String name);

    // --- ÇOKA-ÇOK İLİŞKİ YÖNETİMİ ---

    @SqlUpdate("INSERT OR IGNORE INTO device_type_brand (type_id, brand_id) VALUES (:typeId, :brandId)")
    void linkTypeAndBrand(@Bind("typeId") Long typeId, @Bind("brandId") Long brandId);

    @SqlUpdate("DELETE FROM device_type_brand WHERE type_id = :typeId AND brand_id = :brandId")
    void unlinkTypeAndBrand(@Bind("typeId") Long typeId, @Bind("brandId") Long brandId);

    // --- TAŞIMA / BİRLEŞTİRME (yalnızca transaction içinde, handle.attach ile) ---

    @SqlUpdate("UPDATE devices SET device_type_id = :targetId, updated_at = CURRENT_TIMESTAMP WHERE device_type_id = :sourceId")
    void moveDevicesToType(@Bind("sourceId") Long sourceId, @Bind("targetId") Long targetId);

    @SqlUpdate("UPDATE labors SET device_type_id = :targetId, updated_at = CURRENT_TIMESTAMP WHERE device_type_id = :sourceId")
    void moveLaborsToType(@Bind("sourceId") Long sourceId, @Bind("targetId") Long targetId);

    @SqlUpdate("INSERT OR IGNORE INTO device_type_brand (type_id, brand_id) " +
            "SELECT :targetId, brand_id FROM device_type_brand WHERE type_id = :sourceId")
    void copyBrandLinksToType(@Bind("sourceId") Long sourceId, @Bind("targetId") Long targetId);

    @SqlUpdate("UPDATE devices SET brand_id = :targetId, updated_at = CURRENT_TIMESTAMP WHERE brand_id = :sourceId")
    void moveDevicesToBrand(@Bind("sourceId") Long sourceId, @Bind("targetId") Long targetId);

    @SqlUpdate("INSERT OR IGNORE INTO device_type_brand (type_id, brand_id) " +
            "SELECT type_id, :targetId FROM device_type_brand WHERE brand_id = :sourceId")
    void copyTypeLinksToBrand(@Bind("sourceId") Long sourceId, @Bind("targetId") Long targetId);

    /** Hiçbir türe bağlı olmayan ve hiçbir cihazda kullanılmayan markayı siler (yetim temizliği). */
    @SqlUpdate("DELETE FROM device_brands WHERE id = :id " +
            "AND id NOT IN (SELECT brand_id FROM device_type_brand) AND id NOT IN (SELECT brand_id FROM devices)")
    int deleteBrandIfOrphan(@Bind("id") Long id);
}
