package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.dictionary.PartCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POS satış kataloğu — bilinçli olarak {@link Part} (servis parçası) ile AYNI TABLOYU
 * PAYLAŞMAZ (bkz. V21 migration). Alan kümesi gerçekten farklı: ürünlerde marka var,
 * uyumlu model/tedarikçi yok; parçalarda tedarikçi/uyumlu model var, marka yok. Stok da
 * ayrı bir kaynakta ({@code product_stock_movements}) tutulur.
 */
@Getter @Setter
public class Product {

    private Long id;

    private String barcode;

    private String name;

    private String brand;

    @ColumnName("category_id")
    private Long categoryId;

    // DB kolonu değil; ProductService.hydrateProducts() ile doldurulur (PartService ile aynı desen)
    private PartCategory category;

    @ColumnName("purchase_price")
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @ColumnName("sale_price")
    private BigDecimal salePrice = BigDecimal.ZERO;

    @ColumnName("purchase_currency")
    private String purchaseCurrency = "TRY";

    @ColumnName("purchase_price_original")
    private BigDecimal purchasePriceOriginal;

    @ColumnName("sale_currency")
    private String saleCurrency = "TRY";

    @ColumnName("sale_price_original")
    private BigDecimal salePriceOriginal;

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
        if (purchasePrice == null || stockQuantity == null) return BigDecimal.ZERO;
        return purchasePrice.multiply(BigDecimal.valueOf(stockQuantity));
    }
}
