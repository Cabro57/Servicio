package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Part;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Part.class)
public interface PartRepository {

    // --- INSERT ---
    @SqlUpdate("INSERT INTO parts (barcode, name, category, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, stock_quantity, min_stock_level, description, created_at, updated_at) " +
            "VALUES (:barcode, :name, :category, :modelCompatibility, :supplierId, " +
            ":purchasePrice, :salePrice, :stockQuantity, :minStockLevel, :description, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    int insert(@BindBean Part part);

    // --- UPDATE ---
    @SqlUpdate("UPDATE parts SET barcode=:barcode, name=:name, category=:category, model_compatibility=:modelCompatibility, " +
            "supplier_id=:supplierId, purchase_price=:purchasePrice, sale_price=:salePrice, " +
            "stock_quantity=:stockQuantity, min_stock_level=:minStockLevel, " +
            "description=:description, updated_at=:updatedAt WHERE id=:id")
    void update(@BindBean Part part);

    // --- SOFT DELETE ---
    @SqlUpdate("UPDATE parts SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void delete(@Bind("id") int id);

    @SqlUpdate("UPDATE parts SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id IN (<ids>)")
    void deleteByIds(@BindList("ids") List<Integer> ids);

    @SqlUpdate("UPDATE parts SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE barcode = :barcode")
    void deleteByBarcode(@Bind("barcode") String barcode);

    @SqlUpdate("UPDATE parts SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE barcode IN (<barcodes>)")
    void deleteByBarcodes(@BindList("barcodes") List<String> barcodes);

    // --- STOK GÜNCELLEME ---
    @SqlUpdate("UPDATE parts SET stock_quantity = stock_quantity + :amount, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void adjustStock(@Bind("id") int id, @Bind("amount") int amount);

    @SqlUpdate("UPDATE parts SET stock_quantity = stock_quantity + :amount, updated_at = CURRENT_TIMESTAMP " +
            "WHERE barcode = :barcode AND is_deleted = 0")
    void increaseStockAtomically(@Bind("barcode") String barcode, @Bind("amount") int amount);

    @SqlUpdate("UPDATE parts SET stock_quantity = stock_quantity - :amount, updated_at = CURRENT_TIMESTAMP " +
            "WHERE barcode = :barcode AND is_deleted = 0 AND stock_quantity >= :amount")
    int decreaseStockAtomically(@Bind("barcode") String barcode, @Bind("amount") int amount);

    @SqlQuery("SELECT COUNT(*) > 0 FROM parts WHERE barcode = :barcode AND is_deleted = 0")
    boolean existsByBarcode(@Bind("barcode") String barcode);

    // --- SELECT ---
    @SqlQuery("SELECT id, barcode, name, category, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, stock_quantity, min_stock_level, description, " +
            "is_deleted, created_at, updated_at FROM parts WHERE id = :id AND is_deleted = 0")
    Optional<Part> findById(@Bind("id") int id);

    @SqlQuery("SELECT id, barcode, name, category, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, stock_quantity, min_stock_level, description, " +
            "is_deleted, created_at, updated_at FROM parts WHERE barcode = :barcode AND is_deleted = 0")
    Optional<Part> findByBarcode(@Bind("barcode") String barcode);

    @SqlQuery("SELECT id, barcode, name, category, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, stock_quantity, min_stock_level, description, " +
            "is_deleted, created_at, updated_at FROM parts WHERE is_deleted = 0 ORDER BY name")
    List<Part> findAll();

    @SqlQuery("SELECT id, barcode, name, category, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, stock_quantity, min_stock_level, description, " +
            "is_deleted, created_at, updated_at FROM parts WHERE is_deleted = 0 AND stock_quantity <= min_stock_level " +
            "ORDER BY stock_quantity ASC")
    List<Part> findLowStockParts();

    default List<Part> findBelowMinStock() {
        return findLowStockParts();
    }

    @SqlQuery("SELECT id, barcode, name, category, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, stock_quantity, min_stock_level, description, " +
            "is_deleted, created_at, updated_at FROM parts WHERE is_deleted = 0 AND " +
            "(name LIKE :search OR barcode LIKE :search OR category LIKE :search OR model_compatibility LIKE :search) " +
            "ORDER BY name")
    List<Part> search(@Bind("search") String searchTerm);
}