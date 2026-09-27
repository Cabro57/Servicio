package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;

/** Stoğun fiziksel olarak durduğu yer (depo, raf, vitrin, araç...). Bkz. V25 migration. */
@Getter @Setter
public class Warehouse {
    private Long id;
    private String name;
    private String description;

    /** Konum belirtilmeyen tüm stok işlemleri bu depoya yazılır; her zaman tam bir tane vardır. */
    private boolean defaultWarehouse;

    /** Pasif depo yeni işlemlerde seçilemez; geçmişi korunur. */
    private boolean active = true;

    @ColumnName("sort_order")
    private int sortOrder;

    // --- Yalnızca istatistikli liste sorgusunda dolar (DB kolonu değil) ---

    /** Stoğu sıfır olmayan kalem sayısı (parça + ürün). */
    @ColumnName("item_count")
    private int itemCount;

    @ColumnName("total_quantity")
    private int totalQuantity;

    /** Alış fiyatıyla stok değeri (TL). */
    @ColumnName("stock_value")
    private BigDecimal stockValue = BigDecimal.ZERO;

    /** Depoya yazılmış hareket sayısı — sıfırsa depo silinebilir. */
    @ColumnName("movement_count")
    private int movementCount;

    @Override
    public String toString() {
        return name;
    }
}
