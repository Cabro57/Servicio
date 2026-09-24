package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.filter.SqlWhereBuilder;
import tr.cabro.servicio.model.dto.PageResult;
import java.util.HashMap;
import java.util.Map;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.model.dto.PartStatsDto;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Product.class)
public interface ProductRepository extends SqlObject {

    // --- INSERT / UPDATE ---
    @SqlUpdate("INSERT INTO products (barcode, name, brand, category_id, purchase_price, sale_price, " +
            "purchase_currency, purchase_price_original, sale_currency, sale_price_original, " +
            "stock_quantity, min_stock_level, description, created_at, updated_at) " +
            "VALUES (:barcode, :name, :brand, :categoryId, :purchasePrice, :salePrice, " +
            ":purchaseCurrency, :purchasePriceOriginal, :saleCurrency, :salePriceOriginal, " +
            ":stockQuantity, :minStockLevel, :description, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Product product);

    @SqlUpdate("UPDATE products SET barcode=:barcode, name=:name, brand=:brand, category_id=:categoryId, " +
            "purchase_price=:purchasePrice, sale_price=:salePrice, purchase_currency=:purchaseCurrency, " +
            "purchase_price_original=:purchasePriceOriginal, sale_currency=:saleCurrency, sale_price_original=:salePriceOriginal, " +
            "stock_quantity=:stockQuantity, min_stock_level=:minStockLevel, description=:description, " +
            "updated_at=:updatedAt WHERE id=:id")
    void update(@BindBean Product product);

    // --- SOFT DELETE ---
    @SqlUpdate("UPDATE products SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlUpdate("UPDATE products SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id IN (<ids>)")
    void deleteByIds(@BindList("ids") List<Long> ids);

    @SqlQuery("SELECT COUNT(*) > 0 FROM products WHERE barcode = :barcode AND is_deleted = 0")
    boolean existsByBarcode(@Bind("barcode") String barcode);

    // --- SELECT ---
    @SqlQuery("SELECT id, barcode, name, brand, category_id, purchase_price, sale_price, purchase_currency, " +
            "purchase_price_original, sale_currency, sale_price_original, stock_quantity, min_stock_level, " +
            "description, is_deleted, created_at, updated_at FROM products WHERE id = :id AND is_deleted = 0")
    Optional<Product> findById(@Bind("id") Long id);

    @SqlQuery("SELECT id, barcode, name, brand, category_id, purchase_price, sale_price, purchase_currency, " +
            "purchase_price_original, sale_currency, sale_price_original, stock_quantity, min_stock_level, " +
            "description, is_deleted, created_at, updated_at FROM products WHERE barcode = :barcode AND is_deleted = 0")
    Optional<Product> findByBarcode(@Bind("barcode") String barcode);

    @SqlQuery("SELECT id, barcode, name, brand, category_id, purchase_price, sale_price, purchase_currency, " +
            "purchase_price_original, sale_currency, sale_price_original, stock_quantity, min_stock_level, " +
            "description, is_deleted, created_at, updated_at FROM products WHERE is_deleted = 0 ORDER BY name")
    List<Product> findAll();

    // Arama alanı kategori ve marka adını da kapsar.
    String SEARCH_SELECT = "SELECT p.id, p.barcode, p.name, p.brand, p.category_id, p.purchase_price, p.sale_price, " +
            "p.purchase_currency, p.purchase_price_original, p.sale_currency, p.sale_price_original, " +
            "p.stock_quantity, p.min_stock_level, p.description, p.is_deleted, p.created_at, p.updated_at ";
    String SEARCH_FROM = "FROM products p LEFT JOIN part_categories pc ON pc.id = p.category_id ";
    String SEARCH_WHERE = "WHERE p.is_deleted = 0 AND " +
            "(p.name LIKE :search OR p.barcode LIKE :search OR p.brand LIKE :search OR pc.name LIKE :search) ";

    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + SEARCH_WHERE + "ORDER BY p.name")
    List<Product> search(@Bind("search") String searchTerm);

    // =========================================================================
    // SAYFALAMA (LIMIT/OFFSET)
    // =========================================================================

    @SqlQuery("SELECT id, barcode, name, brand, category_id, purchase_price, sale_price, purchase_currency, " +
            "purchase_price_original, sale_currency, sale_price_original, stock_quantity, min_stock_level, " +
            "description, is_deleted, created_at, updated_at FROM products WHERE is_deleted = 0 " +
            "ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<Product> findAllPaged(@Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM products WHERE is_deleted = 0")
    long countAll();

    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + SEARCH_WHERE + "ORDER BY p.name LIMIT :limit OFFSET :offset")
    List<Product> searchPaged(@Bind("search") String searchTerm, @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) " + SEARCH_FROM + SEARCH_WHERE)
    long countSearch(@Bind("search") String searchTerm);

    // İstatistik kartları için tek sorguda agregat (PartStatsDto — parça/ürün için aynı şekil, yeniden kullanılıyor).
    @RegisterBeanMapper(PartStatsDto.class)
    @SqlQuery("SELECT " +
            "  COUNT(*) AS part_variety_count, " +
            "  COALESCE(SUM(stock_quantity), 0) AS total_stock, " +
            "  COUNT(CASE WHEN stock_quantity < min_stock_level THEN 1 END) AS critical_stock_count, " +
            "  COALESCE(SUM(purchase_price * stock_quantity), 0.0) AS total_inventory_value " +
            "FROM products WHERE is_deleted = 0")
    PartStatsDto getStats();

    // =========================================================================
    // LİSTE SAYFASI: arama + görünüm sekmesi/başlık filtresi + sayfalama (dinamik WHERE, SqlWhereBuilder)
    // =========================================================================

    default PageResult<Product> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                int page, int pageSize) {
        return searchFilteredPaged(searchTerm, filters, page, pageSize, null);
    }

    /** Sıralama anahtarları sabit bir beyaz listedir; SQL parçası kullanıcıdan gelmez. */
    java.util.Map<String, String> SORTS = java.util.Map.of("NEWEST", "p.created_at DESC", "NAME", "p.name COLLATE NOCASE ASC", "STOCK", "p.stock_quantity ASC", "PRICE", "p.sale_price DESC");

    default PageResult<Product> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                int page, int pageSize, String sortKey) {
        SqlWhereBuilder.Result where = SqlWhereBuilder.build(filters);
        boolean searching = searchTerm != null && !searchTerm.isBlank();

        StringBuilder whereClause = new StringBuilder("WHERE p.is_deleted = 0").append(where.getWhereFragment());
        if (searching) whereClause.append(" AND (p.name LIKE :search OR p.barcode LIKE :search OR p.brand LIKE :search OR pc.name LIKE :search)");

        Map<String, Object> countParams = new HashMap<>(where.getParams());
        if (searching) countParams.put("search", "%" + searchTerm.trim() + "%");
        Map<String, Object> params = new HashMap<>(countParams);
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);

        // Aramada ada göre, aksi halde en yeni önce (eski findAllPaged/searchPaged sırası korunur).
        // Sıralama seçilmediyse: aramada ada göre, aksi halde en yeni önce (eski davranış).
        String order = sortKey != null && SORTS.containsKey(sortKey) ? " ORDER BY " + SORTS.get(sortKey)
                : searching ? " ORDER BY p.name" : " ORDER BY p.created_at DESC";
        String listSql = SEARCH_SELECT + SEARCH_FROM + whereClause + order + " LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) " + SEARCH_FROM + whereClause;

        java.util.List<Product> items = getHandle().createQuery(listSql).bindMap(params).mapToBean(Product.class).list();
        long total = getHandle().createQuery(countSql).bindMap(countParams).mapTo(Long.class).one();
        return new PageResult<>(items, page, pageSize, total);
    }
}
