package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.Define;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.StockLevel;
import tr.cabro.servicio.model.StockMovement;
import tr.cabro.servicio.model.enums.StockItemKind;

import java.util.List;
import java.util.Optional;

/**
 * Parça ve ürün stok defterleri için ortak repository. Tablo/sütun adları {@link StockItemKind}'dan
 * {@code @Define} ile gelir; dışarıya yalnızca kind alan default metotlar açılır.
 * <p>
 * Bakiye tabloları ({@code *_stock_levels}) ve {@code stock_quantity} toplamı yalnızca defter
 * tetikleyicisiyle değişir (V25) — bu repository onlara hiç yazmaz.
 */
@RegisterBeanMapper(StockMovement.class)
@RegisterBeanMapper(StockLevel.class)
public interface StockLedgerRepository {

    // --- YAZMA ---

    @SqlUpdate("INSERT INTO <movements> (<itemCol>, warehouse_id, quantity, type, reference_type, reference_id, " +
            "unit_cost, note, created_at) VALUES (:itemId, :warehouseId, :quantity, :type, :referenceType, " +
            ":referenceId, :unitCost, :note, :createdAt)")
    @GetGeneratedKeys
    Long insertRaw(@Define("movements") String movements, @Define("itemCol") String itemCol,
                   @BindBean StockMovement movement);

    default Long insert(StockItemKind kind, StockMovement movement) {
        return insertRaw(kind.movementTable(), kind.itemColumn(), movement);
    }

    // --- BAKİYE ---

    @SqlQuery("SELECT COALESCE((SELECT quantity FROM <levels> WHERE <itemCol> = :itemId AND warehouse_id = :warehouseId), 0)")
    int levelRaw(@Define("levels") String levels, @Define("itemCol") String itemCol,
                 @Bind("itemId") Long itemId, @Bind("warehouseId") Long warehouseId);

    default int level(StockItemKind kind, Long itemId, Long warehouseId) {
        return levelRaw(kind.levelTable(), kind.itemColumn(), itemId, warehouseId);
    }

    /** Kalemin depo bazında bakiyeleri: tüm aktif depolar (sıfır dahil) + stoğu kalmış pasif depolar. */
    @SqlQuery("SELECT :itemId AS item_id, w.id AS warehouse_id, w.name AS warehouse_name, w.is_default AS default_warehouse, w.is_active AS active_warehouse, " +
            "COALESCE(l.quantity, 0) AS quantity " +
            "FROM warehouses w LEFT JOIN <levels> l ON l.warehouse_id = w.id AND l.<itemCol> = :itemId " +
            "WHERE w.is_active = 1 OR COALESCE(l.quantity, 0) != 0 " +
            "ORDER BY w.is_default DESC, w.sort_order, w.name COLLATE NOCASE")
    List<StockLevel> levelsRaw(@Define("levels") String levels, @Define("itemCol") String itemCol,
                               @Bind("itemId") Long itemId);

    default List<StockLevel> levels(StockItemKind kind, Long itemId) {
        return levelsRaw(kind.levelTable(), kind.itemColumn(), itemId);
    }

    /** Depo içeriği: depoda stoğu sıfır olmayan parça ve ürünler. */
    @SqlQuery("SELECT 'PART' AS item_kind, p.id AS item_id, p.name AS item_name, p.barcode, " +
            "l.warehouse_id, l.quantity FROM part_stock_levels l JOIN parts p ON p.id = l.part_id " +
            "WHERE l.warehouse_id = :warehouseId AND l.quantity != 0 " +
            "UNION ALL " +
            "SELECT 'PRODUCT', p.id, p.name, p.barcode, l.warehouse_id, l.quantity " +
            "FROM product_stock_levels l JOIN products p ON p.id = l.product_id " +
            "WHERE l.warehouse_id = :warehouseId AND l.quantity != 0 " +
            "ORDER BY item_name COLLATE NOCASE")
    List<StockLevel> findWarehouseContents(@Bind("warehouseId") Long warehouseId);

    // --- GEÇMİŞ ---

    /**
     * Kalemin hareket geçmişi, en yeni önce. Bakiye sütunları pencere fonksiyonuyla LIMIT'ten önce
     * tüm geçmiş üzerinden hesaplanır; sayfalı okumada da doğru kalır.
     */
    @SqlQuery("SELECT * FROM (" +
            "  SELECT m.id, m.<itemCol> AS item_id, m.warehouse_id, w.name AS warehouse_name, m.quantity, m.type, " +
            "  m.reference_type, m.reference_id, m.unit_cost, m.note, m.created_at, " +
            "  SUM(m.quantity) OVER (ORDER BY m.id) AS balance_after, " +
            "  SUM(m.quantity) OVER (PARTITION BY m.warehouse_id ORDER BY m.id) AS warehouse_balance_after " +
            "  FROM <movements> m JOIN warehouses w ON w.id = m.warehouse_id " +
            "  WHERE m.<itemCol> = :itemId" +
            ") ORDER BY id DESC LIMIT :limit OFFSET :offset")
    List<StockMovement> historyRaw(@Define("movements") String movements, @Define("itemCol") String itemCol,
                                   @Bind("itemId") Long itemId, @Bind("limit") int limit, @Bind("offset") int offset);

    default List<StockMovement> history(StockItemKind kind, Long itemId, int limit, int offset) {
        return historyRaw(kind.movementTable(), kind.itemColumn(), itemId, limit, offset);
    }

    @SqlQuery("SELECT COUNT(*) FROM <movements> WHERE <itemCol> = :itemId")
    long countHistoryRaw(@Define("movements") String movements, @Define("itemCol") String itemCol,
                         @Bind("itemId") Long itemId);

    default long countHistory(StockItemKind kind, Long itemId) {
        return countHistoryRaw(kind.movementTable(), kind.itemColumn(), itemId);
    }

    // --- SERVİS ---

    /**
     * Servisin defterdeki net parça tüketimi, parça + depo bazında (pozitif = servise çıkmış adet).
     * Servis kalemleriyle eşitleme bunun üzerinden yapılır (WorkOrderService).
     */
    @SqlQuery("SELECT part_id AS item_id, warehouse_id, -SUM(quantity) AS quantity FROM stock_movements " +
            "WHERE reference_type IN ('WORK_ORDER', 'WORK_ORDER_CANCEL') AND reference_id = :workOrderId " +
            "GROUP BY part_id, warehouse_id HAVING SUM(quantity) != 0")
    List<StockLevel> findWorkOrderConsumption(@Bind("workOrderId") Long workOrderId);

    // --- YARDIMCI ---

    @SqlQuery("SELECT name FROM <items> WHERE id = :id")
    Optional<String> itemNameRaw(@Define("items") String items, @Bind("id") Long id);

    default String itemName(StockItemKind kind, Long itemId) {
        return itemNameRaw(kind.itemTable(), itemId).orElse("#" + itemId);
    }
}
