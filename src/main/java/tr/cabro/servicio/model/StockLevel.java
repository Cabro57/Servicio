package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

/**
 * Bir kalemin bir depodaki bakiyesi. Kalem detayında (hangi depoda kaç adet) ve depo içeriğinde
 * (bu depoda hangi kalemden kaç adet) kullanılır; sorguya göre bazı alanlar boş kalır.
 */
@Getter @Setter
public class StockLevel {

    @ColumnName("item_id")
    private Long itemId;

    /** Depo içeriği sorgusunda "PART" / "PRODUCT". */
    @ColumnName("item_kind")
    private String itemKind;

    @ColumnName("item_name")
    private String itemName;

    private String barcode;

    @ColumnName("warehouse_id")
    private Long warehouseId;

    @ColumnName("warehouse_name")
    private String warehouseName;

    private boolean defaultWarehouse;

    private boolean activeWarehouse = true;

    private int quantity;
}
