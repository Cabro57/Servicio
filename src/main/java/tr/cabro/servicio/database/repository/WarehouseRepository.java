package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Warehouse;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Warehouse.class)
public interface WarehouseRepository {

    // Boolean sütunlar bean özellik adına takma adla eşlenir: BeanMapper alan üstündeki
    // @ColumnName'i "isX" boolean alanlarda uygulamıyor, is_default → defaultWarehouse eşleşmiyordu.
    String COLUMNS = "w.id, w.name, w.description, w.is_default AS default_warehouse, w.is_active AS active, w.sort_order ";

    String ORDER = "ORDER BY w.is_default DESC, w.is_active DESC, w.sort_order, w.name COLLATE NOCASE";

    @SqlUpdate("INSERT INTO warehouses (name, description, is_default, is_active, sort_order) " +
            "VALUES (:name, :description, 0, 1, :sortOrder)")
    @GetGeneratedKeys
    Long insert(@BindBean Warehouse warehouse);

    @SqlUpdate("UPDATE warehouses SET name = :name, description = :description, sort_order = :sortOrder WHERE id = :id")
    void update(@BindBean Warehouse warehouse);

    @SqlUpdate("UPDATE warehouses SET is_active = :active WHERE id = :id")
    void setActive(@Bind("id") Long id, @Bind("active") boolean active);

    /** Varsayılanı kaldırır — tek-varsayılan index'i nedeniyle {@link #markDefault}'tan önce çağrılmalı. */
    @SqlUpdate("UPDATE warehouses SET is_default = 0 WHERE is_default = 1")
    void clearDefault();

    @SqlUpdate("UPDATE warehouses SET is_default = 1, is_active = 1 WHERE id = :id")
    void markDefault(@Bind("id") Long id);

    @SqlUpdate("DELETE FROM warehouses WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlQuery("SELECT " + COLUMNS + "FROM warehouses w WHERE w.id = :id")
    Optional<Warehouse> findById(@Bind("id") Long id);

    @SqlQuery("SELECT id FROM warehouses WHERE is_default = 1")
    Optional<Long> findDefaultId();

    @SqlQuery("SELECT " + COLUMNS + "FROM warehouses w " + ORDER)
    List<Warehouse> findAll();

    @SqlQuery("SELECT " + COLUMNS + "FROM warehouses w WHERE w.is_active = 1 " + ORDER)
    List<Warehouse> findActive();

    /** Ayarlar > Depolar listesi: her deponun kalem sayısı, toplam adedi, stok değeri ve hareket sayısı. */
    @SqlQuery("SELECT " + COLUMNS + ", " +
            "(SELECT COUNT(*) FROM part_stock_levels l WHERE l.warehouse_id = w.id AND l.quantity != 0) + " +
            "(SELECT COUNT(*) FROM product_stock_levels l WHERE l.warehouse_id = w.id AND l.quantity != 0) AS item_count, " +
            "(SELECT COALESCE(SUM(l.quantity), 0) FROM part_stock_levels l WHERE l.warehouse_id = w.id) + " +
            "(SELECT COALESCE(SUM(l.quantity), 0) FROM product_stock_levels l WHERE l.warehouse_id = w.id) AS total_quantity, " +
            "(SELECT COALESCE(SUM(l.quantity * p.purchase_price), 0) FROM part_stock_levels l JOIN parts p ON p.id = l.part_id " +
            "   WHERE l.warehouse_id = w.id AND l.quantity > 0) + " +
            "(SELECT COALESCE(SUM(l.quantity * p.purchase_price), 0) FROM product_stock_levels l JOIN products p ON p.id = l.product_id " +
            "   WHERE l.warehouse_id = w.id AND l.quantity > 0) AS stock_value, " +
            "(SELECT COUNT(*) FROM stock_movements m WHERE m.warehouse_id = w.id) + " +
            "(SELECT COUNT(*) FROM product_stock_movements m WHERE m.warehouse_id = w.id) AS movement_count " +
            "FROM warehouses w " + ORDER)
    List<Warehouse> findAllWithStats();

    @SqlQuery("SELECT COUNT(*) > 0 FROM warehouses WHERE name = :name COLLATE NOCASE AND (:excludeId IS NULL OR id != :excludeId)")
    boolean existsByName(@Bind("name") String name, @Bind("excludeId") Long excludeId);

    /** Depoda stoğu sıfır olmayan kalem var mı (pasife alma/silme öncesi kontrol). */
    @SqlQuery("SELECT EXISTS (SELECT 1 FROM part_stock_levels WHERE warehouse_id = :id AND quantity != 0) " +
            "OR EXISTS (SELECT 1 FROM product_stock_levels WHERE warehouse_id = :id AND quantity != 0)")
    boolean hasStock(@Bind("id") Long id);

    @SqlQuery("SELECT EXISTS (SELECT 1 FROM stock_movements WHERE warehouse_id = :id) " +
            "OR EXISTS (SELECT 1 FROM product_stock_movements WHERE warehouse_id = :id)")
    boolean hasMovements(@Bind("id") Long id);

    @SqlQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM warehouses")
    int nextSortOrder();
}
