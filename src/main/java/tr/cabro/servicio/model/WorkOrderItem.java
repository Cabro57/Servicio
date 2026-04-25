package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.SourceType;

import java.math.BigDecimal;

@Getter @Setter
public class WorkOrderItem {

    private Long id;

    @ColumnName("service_id")
    private Long serviceId;

    // --- Tür Bilgileri ---
    @ColumnName("item_type")
    private ItemType itemType;

    @ColumnName("source_type")
    private SourceType sourceType;

    // --- Referanslar (Sadece PRESET ise dolu olur, MANUAL ise NULL kalır) ---
    @ColumnName("part_id")
    private Long partId; // Veritabanındaki parts tablosuna referans

    @ColumnName("labor_id")
    private Long laborId; // Gelecekte eklenebilecek Hazır İşçilikler (labor_definitions) tablosu için

    // --- SNAPSHOT (Anlık Görüntü) ALANLARI ---
    @ColumnName("item_name")
    private String itemName; // Rapordaki 'label' (Örn: "Ekran Değişimi" veya "Batarya")

    @ColumnName("used_serial_no")
    private String usedSerialNo; // Sadece PART ise zorunlu olabilir (Garanti takibi)

    // TODO: bu primitive değeri araştır.
    private Integer quantity = 1;

    // Finansal Snapshot
    @ColumnName("purchase_price")
    private BigDecimal purchasePrice = BigDecimal.ZERO; // Kalemin maliyeti (İşçilikse 0 kalabilir)

    @ColumnName("unit_price")
    private BigDecimal unitPrice = BigDecimal.ZERO; // Müşteriye yansıyan birim fiyat (Satış veya İşçilik ücreti)

    @ColumnName("tax_rate")
    private BigDecimal taxRate = BigDecimal.ZERO; // KDV vb. (İsteğe bağlı)

    // Toplam tutarı hesaplayan yardımcı metot
    public BigDecimal getTotalPrice() {
        if (unitPrice == null) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}