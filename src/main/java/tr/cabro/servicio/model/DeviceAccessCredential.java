package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.DeviceAccessType;

import java.time.LocalDateTime;

/**
 * Bir servis kaydına (WorkOrder) ait cihaz ekran kilidi erişim bilgisi.
 * <p>
 * Bilinçli olarak {@link WorkOrder} modeline kolon olarak eklenmedi — bağımsız bir tabloda
 * tutulur (kurumsal/genişleyebilir tasarım): ileride "kod değişti, yenisi girildi" gibi
 * geçmiş kayıtlar veya farklı varlıklara (ör. tedarikçi cihazı) bağlanma ihtiyacı çıkarsa
 * bu tablo kolayca genişler.
 * <p>
 * {@code secret} bu modelde her zaman <b>düz metin</b> tutulur — şifreleme/çözme işi
 * repository/servis katmanında ({@code DeviceAccessCredentialService}) yapılır, model
 * ve UI katmanı bu detayı bilmez.
 */
@Getter @Setter
public class DeviceAccessCredential {

    private Long id;

    @ColumnName("work_order_id")
    private Long workOrderId;

    @ColumnName("access_type")
    private DeviceAccessType accessType;

    /** Düz metin erişim kodu (PIN/şifre/desen dizisi) — DB'de şifreli tutulur. */
    private String secret;

    @ColumnName("set_at")
    private LocalDateTime setAt;

    /** Otomatik silme (purge) ne zaman gerçekleşti — null ise henüz silinmedi. */
    @ColumnName("purged_at")
    private LocalDateTime purgedAt;

    @ColumnName("created_at")
    private LocalDateTime createdAt;
}
