package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.dictionary.PartCategory;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
public class Part {
    private Long id;

    private String barcode;

    private String name;

    @ColumnName("category_id")
    private Long categoryId;

    // DB kolonu değil; PartService.hydrateParts() ile doldurulur (supplier ile aynı desen)
    private PartCategory category;

    @ColumnName("model_compatibility")
    private String modelCompatibility;

    @ColumnName("supplier_id")
    private Long supplierId;

    private Supplier supplier;

    @ColumnName("warehouse_id")
    private Long warehouseId;

    @ColumnName("purchase_price")
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @ColumnName("sale_price")
    private BigDecimal salePrice = BigDecimal.ZERO;

    @ColumnName("stock_quantity")
    private Integer stockQuantity;

    @ColumnName("min_stock_level")
    private Integer minStockLevel;

    private String description;

    @ColumnName("is_deleted")
    private boolean isDeleted;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    public BigDecimal getTotalStockValue() {
        if (purchasePrice == null) return BigDecimal.ZERO;
        return purchasePrice.multiply(BigDecimal.valueOf(stockQuantity));
    }

    @Override
    public String toString() {
        return "Part{" +
                "id=" + id +
                ", barcode='" + barcode + '\'' +
                ", name='" + name + '\'' +
                ", categoryId=" + categoryId +
                ", modelCompatibility='" + modelCompatibility + '\'' +
                ", supplierId=" + supplierId +
                ", purchasePrice=" + purchasePrice +
                ", salePrice=" + salePrice +
                ", stockQuantity=" + stockQuantity +
                ", minStockLevel=" + minStockLevel +
                ", description='" + description + '\'' +
                ", isDeleted=" + isDeleted +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}