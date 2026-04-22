package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
public class ServicePayment {

    private int id;

    @ColumnName("service_id")
    private int serviceId;

    // Resimdeki "Tutar"
    private BigDecimal amount;

    // Resimdeki "Ödeme Türü"
    @ColumnName("payment_type")
    private PaymentType paymentType;

    // Resimdeki "Açıklama" (Örn: "Kalan tutar", "Kapora")
    private String note;

    // Resimdeki "İşlem Tarihi" (Ödeme ne zaman yapıldı?)
    @ColumnName("payment_date")
    private LocalDateTime paymentDate;

    // Sisteme kaydın girildiği tarih (Log/Denetim için)
    @ColumnName("created_at")
    private LocalDateTime createdAt;
}