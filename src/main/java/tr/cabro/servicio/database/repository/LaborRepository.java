package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Labor;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Labor.class)
public interface LaborRepository {

    @SqlUpdate("INSERT INTO labors (name, description, category, default_price, device_type_id, created_at, updated_at) " +
            "VALUES (:name, :description, :category, :defaultPrice, :deviceTypeId, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Labor labor);

    @SqlUpdate("UPDATE labors SET name=:name, description=:description, category=:category, " +
            "default_price=:defaultPrice, device_type_id=:deviceTypeId, updated_at=:updatedAt WHERE id=:id")
    void update(@BindBean Labor labor);

    // Soft Delete: İşçilik kalemini silmeyip gizliyoruz
    @SqlUpdate("UPDATE labors SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlQuery("SELECT l.*, dt.name AS device_type_name FROM labors l " +
            "LEFT JOIN device_types dt ON dt.id = l.device_type_id " +
            "WHERE l.id = :id AND l.is_deleted = 0")
    Optional<Labor> findById(@Bind("id") Long id);

    @SqlQuery("SELECT l.*, dt.name AS device_type_name FROM labors l " +
            "LEFT JOIN device_types dt ON dt.id = l.device_type_id " +
            "WHERE l.is_deleted = 0 ORDER BY dt.name, l.category, l.name")
    List<Labor> findAll();

    // Belirli bir cihaz türüne özel işçilikler + türden bağımsız (Genel) işçilikler
    @SqlQuery("SELECT l.*, dt.name AS device_type_name FROM labors l " +
            "LEFT JOIN device_types dt ON dt.id = l.device_type_id " +
            "WHERE l.is_deleted = 0 AND (l.device_type_id = :typeId OR l.device_type_id IS NULL) " +
            "ORDER BY l.device_type_id IS NULL, l.category, l.name")
    List<Labor> findByTypeId(@Bind("typeId") Long typeId);

    @SqlQuery("SELECT l.*, dt.name AS device_type_name FROM labors l " +
            "LEFT JOIN device_types dt ON dt.id = l.device_type_id " +
            "WHERE l.is_deleted = 0 AND " +
            "(l.name LIKE :search OR l.category LIKE :search) ORDER BY l.name")
    List<Labor> search(@Bind("search") String searchStr);
}