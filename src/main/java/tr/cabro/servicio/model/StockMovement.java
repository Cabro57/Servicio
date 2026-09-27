package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.ReferenceType;
import tr.cabro.servicio.model.enums.StockType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stok defterinin bir satırı — parça ve ürün defteri için ortak model (bkz. {@link tr.cabro.servicio.model.enums.StockItemKind}).
 * Miktar işaretlidir: giriş +, çıkış −. Hareketler değiştirilemez/silinemez (V25 tetikleyicileri).
 */
@Getter @Setter
public class StockMovement {
    private Long id;

    /** parts.id ya da products.id — sorgularda {@code item_id} takma adıyla gelir. */
    @ColumnName("item_id")
    private Long itemId;

    @ColumnName("warehouse_id")
    private Long warehouseId;

    private Integer quantity;
    private StockType type;

    @ColumnName("reference_type")
    private ReferenceType referenceType;

    @ColumnName("reference_id")
    private Long referenceId;

    @ColumnName("unit_cost")
    private BigDecimal unitCost;

    private String note;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    // --- Yalnızca geçmiş sorgusunda dolar (DB kolonu değil) ---

    @ColumnName("warehouse_name")
    private String warehouseName;

    /** Bu hareketten sonra kalemin tüm depolardaki toplam stoğu. */
    @ColumnName("balance_after")
    private Integer balanceAfter;

    /** Bu hareketten sonra kalemin hareketin deposundaki stoğu. */
    @ColumnName("warehouse_balance_after")
    private Integer warehouseBalanceAfter;

    @Override
    public String toString() {
        return "StockMovement{" +
                "id=" + id +
                ", itemId=" + itemId +
                ", warehouseId=" + warehouseId +
                ", quantity=" + quantity +
                ", type=" + type +
                ", referenceType=" + referenceType +
                ", referenceId=" + referenceId +
                ", createdAt=" + createdAt +
                '}';
    }
}
