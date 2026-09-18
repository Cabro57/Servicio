package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
public class SaleItem {

    private Long id;

    @ColumnName("sale_id")
    private Long saleId;

    @ColumnName("product_id")
    private Long productId; // NULL = katalogda olmayan manuel kalem

    @ColumnName("item_name")
    private String itemName; // Snapshot (Product.name kopyası veya elle yazılan)

    private Integer quantity = 1;

    // Finansal snapshot (WorkOrderItem'daki desenin aynısı)
    @ColumnName("purchase_price")
    private BigDecimal purchasePrice = BigDecimal.ZERO; // Satış anındaki maliyet (kâr raporu için)

    @ColumnName("unit_price")
    private BigDecimal unitPrice = BigDecimal.ZERO; // TL kanonik birim satış fiyatı

    @ColumnName("sale_currency")
    private String saleCurrency = "TRY";

    @ColumnName("unit_price_original")
    private BigDecimal unitPriceOriginal;

    @ColumnName("line_discount_type")
    private DiscountType lineDiscountType;

    @ColumnName("line_discount_value")
    private BigDecimal lineDiscountValue = BigDecimal.ZERO;

    @ColumnName("line_total")
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    // İade kalemi ise orijinal satış kalemine referans — "aynı kalem iki kez iade edilemez"
    // kontrolü bunun üzerinden yapılır (bkz. SaleService.recordReturn). Normal satış kalemlerinde NULL.
    @ColumnName("source_sale_item_id")
    private Long sourceSaleItemId;

    // DB kolonu değil; SaleService.hydrate() ile doldurulur (ProductService.hydrateProducts ile aynı desen)
    private Product product;
}
