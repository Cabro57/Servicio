package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @Deprecated
public class AddedPart {

    private Long id;

    @ColumnName("service_id")
    private Long serviceId;

    @ColumnName("part_barcode")
    private String partBarcode;

    @ColumnName("is_stock_tracked")
    private boolean stockTracked;

    private transient boolean returnToStockOnDelete;

    @ColumnName("series_no")
    private String seriesNo;

    private String brand;
    private String name;

    @ColumnName("supplier_id")
    private Long supplierId;

    @ColumnName("device_type")
    private String deviceType;

    private String model;
    private Integer amount;

    @ColumnName("purchase_price")
    private BigDecimal purchasePrice;

    @ColumnName("sale_price")
    private BigDecimal sellingPrice;

    @ColumnName("warranty_period")
    private Integer warrantyPeriod;

    @ColumnName("purchase_date")
    private LocalDateTime purchaseDate;

    private String description;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    public AddedPart() {
        this.createdAt = LocalDateTime.now();
    }

    public AddedPart(Part data, Integer amount) {
        this(data);
        this.amount = amount;
    }

    public AddedPart(Part data) {
        this();
        this.partBarcode = data.getBarcode();
        this.stockTracked = true;
        this.name = data.getName();
        this.supplierId = data.getSupplierId();
        this.model = data.getModelCompatibility();  // FIX: eski `model` → `modelCompatibility`
        this.amount = 1;
        // FIX: BigDecimal → double (.doubleValue())
        this.purchasePrice = BigDecimal.valueOf(data.getPurchasePrice() != null ? data.getPurchasePrice().doubleValue() : 0.0);
        this.sellingPrice = data.getSalePrice();
        // FIX: Part.purchaseDate kaldırıldı — boş bırakılıyor
        this.purchaseDate = null;
        this.description = data.getDescription();
    }
}
