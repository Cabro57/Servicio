package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.dto.PartStatsDto;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Part.class)
public interface PartRepository {

    // --- INSERT ---
    @SqlUpdate("INSERT INTO parts (barcode, name, category_id, model_compatibility, supplier_id, warehouse_id, " +
            "purchase_price, sale_price, purchase_currency, purchase_price_original, sale_currency, sale_price_original, " +
            "stock_quantity, min_stock_level, description, created_at, updated_at) " +
            "VALUES (:barcode, :name, :categoryId, :modelCompatibility, :supplierId, :warehouseId, " +
            ":purchasePrice, :salePrice, :purchaseCurrency, :purchasePriceOriginal, :saleCurrency, :salePriceOriginal, " +
            ":stockQuantity, :minStockLevel, :description, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Part part);

    // --- UPDATE ---
    @SqlUpdate("UPDATE parts SET barcode=:barcode, name=:name, category_id=:categoryId, model_compatibility=:modelCompatibility, " +
            "supplier_id=:supplierId, warehouse_id=:warehouseId, purchase_price=:purchasePrice, sale_price=:salePrice, " +
            "purchase_currency=:purchaseCurrency, purchase_price_original=:purchasePriceOriginal, " +
            "sale_currency=:saleCurrency, sale_price_original=:salePriceOriginal, " +
            "stock_quantity=:stockQuantity, min_stock_level=:minStockLevel, " +
            "description=:description, updated_at=:updatedAt WHERE id=:id")
    void update(@BindBean Part part);

    // --- SOFT DELETE ---
    @SqlUpdate("UPDATE parts SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlUpdate("UPDATE parts SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id IN (<ids>)")
    void deleteByIds(@BindList("ids") List<Long> ids);

    @SqlUpdate("UPDATE parts SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE barcode = :barcode")
    void deleteByBarcode(@Bind("barcode") String barcode);

    @SqlUpdate("UPDATE parts SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE barcode IN (<barcodes>)")
    void deleteByBarcodes(@BindList("barcodes") List<String> barcodes);

    // --- STOK GÜNCELLEME ---
    @SqlUpdate("UPDATE parts SET stock_quantity = stock_quantity + :amount, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void adjustStock(@Bind("id") Long id, @Bind("amount") Integer amount);

    @SqlUpdate("UPDATE parts SET stock_quantity = stock_quantity + :amount, updated_at = CURRENT_TIMESTAMP " +
            "WHERE barcode = :barcode AND is_deleted = 0")
    void increaseStockAtomically(@Bind("barcode") String barcode, @Bind("amount") Integer amount);

    @SqlUpdate("UPDATE parts SET stock_quantity = stock_quantity - :amount, updated_at = CURRENT_TIMESTAMP " +
            "WHERE barcode = :barcode AND is_deleted = 0 AND stock_quantity >= :amount")
    int decreaseStockAtomically(@Bind("barcode") String barcode, @Bind("amount") Integer amount);

    @SqlQuery("SELECT COUNT(*) > 0 FROM parts WHERE barcode = :barcode AND is_deleted = 0")
    boolean existsByBarcode(@Bind("barcode") String barcode);

    // --- SELECT ---
    @SqlQuery("SELECT id, barcode, name, category_id, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, purchase_currency, purchase_price_original, sale_currency, sale_price_original, " +
            "stock_quantity, min_stock_level, description, " +
            "is_deleted, created_at, updated_at FROM parts WHERE id = :id AND is_deleted = 0")
    Optional<Part> findById(@Bind("id") Long id);

    @SqlQuery("SELECT id, barcode, name, category_id, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, purchase_currency, purchase_price_original, sale_currency, sale_price_original, " +
            "stock_quantity, min_stock_level, description, " +
            "is_deleted, created_at, updated_at FROM parts WHERE barcode = :barcode AND is_deleted = 0")
    Optional<Part> findByBarcode(@Bind("barcode") String barcode);

    @SqlQuery("SELECT id, barcode, name, category_id, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, purchase_currency, purchase_price_original, sale_currency, sale_price_original, " +
            "stock_quantity, min_stock_level, description, " +
            "is_deleted, created_at, updated_at FROM parts WHERE is_deleted = 0 ORDER BY created_at DESC")
    List<Part> findAll();

    @SqlQuery("SELECT id, barcode, name, category_id, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, purchase_currency, purchase_price_original, sale_currency, sale_price_original, " +
            "stock_quantity, min_stock_level, description, " +
            "is_deleted, created_at, updated_at FROM parts WHERE is_deleted = 0 AND stock_quantity <= min_stock_level " +
            "ORDER BY stock_quantity ASC")
    List<Part> findLowStockParts();

    default List<Part> findBelowMinStock() {
        return findLowStockParts();
    }

    // Arama alanı tedarikçi ve kategori adını da kapsar; bu yüzden aşağıdaki sorgular
    // parts'ı suppliers ve part_categories ile JOIN eder.
    String SEARCH_SELECT = "SELECT p.id, p.barcode, p.name, p.category_id, p.model_compatibility, p.supplier_id, " +
            "p.purchase_price, p.sale_price, p.purchase_currency, p.purchase_price_original, p.sale_currency, p.sale_price_original, " +
            "p.stock_quantity, p.min_stock_level, p.description, " +
            "p.is_deleted, p.created_at, p.updated_at ";
    String SEARCH_FROM = "FROM parts p " +
            "LEFT JOIN suppliers sup ON sup.id = p.supplier_id " +
            "LEFT JOIN part_categories pc ON pc.id = p.category_id ";
    String SEARCH_WHERE = "WHERE p.is_deleted = 0 AND " +
            "(p.name LIKE :search OR p.barcode LIKE :search OR p.model_compatibility LIKE :search " +
            "OR pc.name LIKE :search OR sup.name LIKE :search OR sup.business_name LIKE :search) ";

    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + SEARCH_WHERE + "ORDER BY p.name")
    List<Part> search(@Bind("search") String searchTerm);

    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + "WHERE p.supplier_id = :supplierId AND p.is_deleted = 0 ORDER BY p.name")
    List<Part> findBySupplierId(@Bind("supplierId") Long supplierId);

    // =========================================================================
    // SAYFALAMA (LIMIT/OFFSET) — DB-tabanlı liste ekranı için
    // =========================================================================

    @SqlQuery("SELECT id, barcode, name, category_id, model_compatibility, supplier_id, " +
            "purchase_price, sale_price, purchase_currency, purchase_price_original, sale_currency, sale_price_original, " +
            "stock_quantity, min_stock_level, description, " +
            "is_deleted, created_at, updated_at FROM parts WHERE is_deleted = 0 " +
            "ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<Part> findAllPaged(@Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM parts WHERE is_deleted = 0")
    long countAll();

    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + SEARCH_WHERE + "ORDER BY p.name LIMIT :limit OFFSET :offset")
    List<Part> searchPaged(@Bind("search") String searchTerm, @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) " + SEARCH_FROM + SEARCH_WHERE)
    long countSearch(@Bind("search") String searchTerm);

    // İstatistik kartları için tek sorguda agregat — FormParts.refreshStats()'daki tam-tablo
    // client-side hesaplamanın yerine geçer.
    @RegisterBeanMapper(PartStatsDto.class)
    @SqlQuery("SELECT " +
            "  COUNT(*) AS part_variety_count, " +
            "  COALESCE(SUM(stock_quantity), 0) AS total_stock, " +
            "  COUNT(CASE WHEN stock_quantity < min_stock_level THEN 1 END) AS critical_stock_count, " +
            "  COALESCE(SUM(purchase_price * stock_quantity), 0.0) AS total_inventory_value " +
            "FROM parts WHERE is_deleted = 0")
    PartStatsDto getStats();
}
