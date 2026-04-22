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

    @SqlUpdate("INSERT INTO labors (name, description, category, default_price, created_at, updated_at) " +
            "VALUES (:name, :description, :category, :defaultPrice, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    int insert(@BindBean Labor labor);

    @SqlUpdate("UPDATE labors SET name=:name, description=:description, category=:category, " +
            "default_price=:defaultPrice, updated_at=:updatedAt WHERE id=:id")
    void update(@BindBean Labor labor);

    // Soft Delete: İşçilik kalemini silmeyip gizliyoruz
    @SqlUpdate("UPDATE labors SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void delete(@Bind("id") int id);

    @SqlQuery("SELECT * FROM labors WHERE id = :id AND is_deleted = 0")
    Optional<Labor> findById(@Bind("id") int id);

    @SqlQuery("SELECT * FROM labors WHERE is_deleted = 0 ORDER BY category, name")
    List<Labor> findAll();

    @SqlQuery("SELECT * FROM labors WHERE is_deleted = 0 AND " +
            "(name LIKE :search OR category LIKE :search) ORDER BY name")
    List<Labor> search(@Bind("search") String searchStr);
}