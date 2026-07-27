package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.dictionary.PartCategory;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(PartCategory.class)
public interface PartCategoryRepository {

    // partCount'u da tek sorguda çeker — ayrı sorgu gerekmez
    @SqlQuery("SELECT pc.*, COUNT(p.id) AS part_count " +
            "FROM part_categories pc " +
            "LEFT JOIN parts p ON p.category_id = pc.id AND p.is_deleted = 0 " +
            "GROUP BY pc.id " +
            "ORDER BY pc.name ASC")
    List<PartCategory> findAll();

    @SqlQuery("SELECT * FROM part_categories WHERE id IN (<ids>)")
    List<PartCategory> findByIds(@BindList("ids") List<Long> ids);

    @SqlQuery("SELECT * FROM part_categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    Optional<PartCategory> findByName(@Bind("name") String name);

    @SqlUpdate("INSERT INTO part_categories (name) VALUES (:name)")
    @GetGeneratedKeys
    int insert(@Bind("name") String name);

    @SqlUpdate("UPDATE part_categories SET name = :name WHERE id = :id")
    void rename(@Bind("id") Long id, @Bind("name") String name);

    @SqlUpdate("DELETE FROM part_categories WHERE id = :id")
    void delete(@Bind("id") Long id);
}
