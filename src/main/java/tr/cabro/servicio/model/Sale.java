package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.DiscountType;
import tr.cabro.servicio.model.enums.SaleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class Sale {

    private Long id;

    @ColumnName("customer_id")
    private Long customerId; // NULL = perakende/hızlı satış

    private SaleType type = SaleType.SALE;

    @ColumnName("parent_sale_id")
    private Long parentSaleId; // RETURN ise orijinal satışa referans

    @ColumnName("sale_date")
    private LocalDateTime saleDate;

    private BigDecimal subtotal = BigDecimal.ZERO;

    @ColumnName("discount_type")
    private DiscountType discountType;

    @ColumnName("discount_value")
    private BigDecimal discountValue = BigDecimal.ZERO;

    @ColumnName("tax_rate")
    private BigDecimal taxRate = BigDecimal.ZERO;

    @ColumnName("total_amount")
    private BigDecimal totalAmount = BigDecimal.ZERO;

    private String note;

    @ColumnName("is_deleted")
    private boolean isDeleted;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    // --- İLİŞKİSEL VERİLER (DB'ye yazılmaz) ---
    private Customer customer;
    private List<SaleItem> items = new ArrayList<>();

    // DB kolonu değil; PaymentService.hydrate ile doldurulur (WorkOrder.payments deseninin karşılığı).
    // Hydrate edilmeden okunursa null kalır — getRemainingAmount() bunu 0 kabul eder.
    private BigDecimal totalPaid;

    public BigDecimal getRemainingAmount() {
        BigDecimal paid = totalPaid == null ? BigDecimal.ZERO : totalPaid;
        return totalAmount.subtract(paid);
    }
}
