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

/**
 * Parça ve ürün kategorileri (tek tablo, {@code scope} ile hangi katalogda seçilebildiği).
 * Parça ve ürün sayıları ayrı alt sorgularla sayılır (JOIN'ler birbirini çoğaltmasın).
 */
@RegisterBeanMapper(PartCategory.class)
public interface PartCategoryRepository {

    @SqlQuery("SELECT pc.id, pc.name, pc.scope, " +
            "(SELECT COUNT(*) FROM parts p WHERE p.category_id = pc.id AND p.is_deleted = 0) AS part_count, " +
            "(SELECT COUNT(*) FROM products r WHERE r.category_id = pc.id AND r.is_deleted = 0) AS product_count " +
            "FROM part_categories pc ORDER BY pc.name COLLATE NOCASE ASC")
    List<PartCategory> findAll();

    @SqlQuery("SELECT * FROM part_categories WHERE id IN (<ids>)")
    List<PartCategory> findByIds(@BindList("ids") List<Long> ids);

    @SqlQuery("SELECT * FROM part_categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    Optional<PartCategory> findByName(@Bind("name") String name);

    @SqlUpdate("INSERT INTO part_categories (name, scope) VALUES (:name, :scope)")
    @GetGeneratedKeys
    int insert(@Bind("name") String name, @Bind("scope") String scope);

    @SqlUpdate("UPDATE part_categories SET name = :name, scope = :scope WHERE id = :id")
    void update(@Bind("id") Long id, @Bind("name") String name, @Bind("scope") String scope);

    @SqlUpdate("UPDATE parts SET category_id = :targetId WHERE category_id = :sourceId")
    void moveParts(@Bind("sourceId") Long sourceId, @Bind("targetId") Long targetId);

    @SqlUpdate("UPDATE products SET category_id = :targetId WHERE category_id = :sourceId")
    void moveProducts(@Bind("sourceId") Long sourceId, @Bind("targetId") Long targetId);

    // Parça/ürün category_id'leri ON DELETE SET NULL ile kategorisiz kalır.
    @SqlUpdate("DELETE FROM part_categories WHERE id = :id")
    void delete(@Bind("id") Long id);
}
